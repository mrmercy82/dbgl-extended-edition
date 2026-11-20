/*
 *  Copyright (C) 2026  mrmercy82 (DBGL Extended Edition, written with the help of AI)
 *  Part of a modified version of DBGL, Copyright (C) 2006-2025  Ronald Blankendaal
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package org.dbgl.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.dbgl.model.aggregate.DosboxVersion;


/**
 * alpha 52: a whole drive (or any folder) searched for what DOSBox can use for music: SoundFonts (.sf2, .sf3), the ROMs of an
 * MT-32/CM-32L (known by their SHA-1 sums), the ROMs of a Sound Canvas (in a folder per model) and the patches of the Gravis
 * Ultrasound (a folder ULTRASND with MIDI\*.pat). Nothing is changed while searching; links are not followed, hidden folders
 * and the folders of the system are left out. What is found can be copied to where DOSBox looks for it.
 */
public final class SoundScan {

	public enum Kind {
		SOUNDFONT, MT32, SOUNDCANVAS, ULTRASND
	}

	/** a find: what, where, how many files, what is in it ("CM-32L v1.02", "SC-55-v1.20", "2.1" for the patches) */
	public record Find(Kind kind, File dir, int files, List<String> details) {
	}

	static final int MAX_DEPTH = 16;
	static final int MAX_DIRS = 400_000;
	/** folders not looked into, by name (all systems) */
	private static final Set<String> SKIPPED = Set.of("$recycle.bin", "system volume information", "windowsapps", "winsxs", "node_modules", "lost+found");
	/** folders of the system, left out at the top of a drive only (a game may have a folder "windows") */
	private static final Set<String> SKIPPED_TOP = Set.of("windows", "proc", "sys", "dev", "run", "snap", "boot", "tmp", "var");

	private SoundScan() {
	}

	/** the folders to start from when the whole computer is meant: the drives (Windows) or the home folder and /media, /mnt */
	public static List<File> wholeComputer() {
		List<File> result = new ArrayList<>();
		if (SystemUtils.IS_WINDOWS) {
			for (File root: File.listRoots())
				if (root.isDirectory())
					result.add(root);
		} else {
			result.add(new File(System.getProperty("user.home")));
			for (String d: new String[] {"/media", "/mnt", "/Volumes", "/usr/share", "/usr/local/share", "/opt"})
				if (new File(d).isDirectory())
					result.add(new File(d));
		}
		return result;
	}

	/**
	 * @param progress told about every folder looked into (may be null)
	 * @param cancelled asked now and then; the search stops with what was found so far
	 */
	public static List<Find> scan(List<File> roots, Consumer<File> progress, BooleanSupplier cancelled) {
		List<Find> result = new ArrayList<>();
		Set<Path> done = new HashSet<>();
		int[] dirs = {0};
		boolean[] stopped = {false};
		for (File r: roots) {
			if (stopped[0])
				break;
			Path root;
			try {
				root = r.toPath().toRealPath();
			} catch (IOException | RuntimeException e) {
				continue;
			}
			if (!done.add(root))
				continue;
			Deque<int[]> counts = new ArrayDeque<>(); // per open folder: SoundFonts
			Deque<List<String>> sums = new ArrayDeque<>(); // per open folder: the sums of files the size of an MT-32 ROM
			try {
				// the attributes come with the listing (on Windows without a further look at each file): much faster than File
				Files.walkFileTree(root, java.util.EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH + 1, new SimpleFileVisitor<>() {
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
						if (((cancelled != null) && cancelled.getAsBoolean()) || (dirs[0] >= MAX_DIRS)) {
							stopped[0] = true;
							return FileVisitResult.TERMINATE;
						}
						int depth = root.relativize(dir).getNameCount() - (dir.equals(root) ? 1: 0);
						if (!dir.equals(root)) {
							String name = dir.getFileName().toString().toLowerCase(Locale.ROOT);
							if (name.startsWith(".") || SKIPPED.contains(name) || ((depth == 1) && SKIPPED_TOP.contains(name)) || done.contains(dir))
								return FileVisitResult.SKIP_SUBTREE;
						}
						dirs[0]++;
						File d = dir.toFile();
						if (progress != null)
							progress.accept(d);
						if (d.getName().equalsIgnoreCase("ultrasnd")) {
							Find gus = ultrasnd(d);
							if (gus != null) {
								result.add(gus);
								return FileVisitResult.SKIP_SUBTREE; // its folders are its patches
							}
						}
						if ((dir.getFileName() != null) && MidiRoms.soundCanvasModelName(dir.getFileName().toString()) && (dir.getParent() != null)) {
							File base = dir.getParent().toFile();
							boolean known = result.stream().anyMatch(f -> (f.kind() == Kind.SOUNDCANVAS) && f.dir().equals(base));
							if (!known) {
								List<String> models = MidiRoms.soundCanvasModelsAt(base);
								if (!models.isEmpty())
									result.add(new Find(Kind.SOUNDCANVAS, base, models.size(), models));
							}
							return FileVisitResult.SKIP_SUBTREE; // a model: only its ROMs in it
						}
						counts.push(new int[1]);
						sums.push(new ArrayList<>());
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
						if (counts.isEmpty() || !(attrs.isRegularFile() || (attrs.isSymbolicLink() && Files.isRegularFile(file))))
							return FileVisitResult.CONTINUE; // a link to a file counts (Linux keeps its SoundFonts that way), not to a folder
						String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
						if (n.endsWith(".sf2") || n.endsWith(".sf3"))
							counts.peek()[0]++;
						else if (attrs.isRegularFile() && MidiRoms.mt32Size(attrs.size()) && !n.endsWith(".exe") && !n.endsWith(".dll")) {
							String sum = MidiRoms.sha1(file.toFile());
							if (!sum.isEmpty())
								sums.peek().add(sum);
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exc) {
						return FileVisitResult.CONTINUE; // no right to look in, gone meanwhile
					}

					@Override
					public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
						if (counts.isEmpty())
							return FileVisitResult.CONTINUE;
						int soundfonts = counts.pop()[0];
						List<String> mine = sums.pop();
						File d = dir.toFile();
						if (soundfonts > 0)
							result.add(new Find(Kind.SOUNDFONT, d, soundfonts, List.of()));
						if (!mine.isEmpty()) {
							Set<String> models = MidiRoms.mt32ModelsOfSums(mine);
							if (!models.isEmpty())
								result.add(new Find(Kind.MT32, d, mine.size(), new ArrayList<>(models)));
						}
						return FileVisitResult.CONTINUE;
					}
				});
			} catch (IOException | RuntimeException e) {
				// what was found until then is kept
			}
		}
		return result;
	}

	static Find ultrasnd(File dir) {
		File[] midi = dir.listFiles(f -> f.isDirectory() && f.getName().equalsIgnoreCase("midi"));
		File[] pats = (midi == null) || (midi.length == 0) ? null: midi[0].listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".pat"));
		if ((pats == null) || (pats.length == 0))
			return null;
		String version = MidiRoms.patchVersion(dir);
		return new Find(Kind.ULTRASND, dir, pats.length, version == null ? List.of(): List.of(version));
	}

	/** where DOSBox (Staging, by itself) or DBGL looks for this kind: the folder to copy a find to; null if there is none */
	public static File target(Kind kind, DosboxVersion db) {
		return switch (kind) {
			case SOUNDFONT -> MidiRoms.stagingTarget(db, "soundfonts", "soundfonts");
			case MT32 -> MidiRoms.stagingTarget(db, "mt32-roms", "mt32-rom-data");
			case SOUNDCANVAS -> MidiRoms.stagingTarget(db, "soundcanvas-roms", "soundcanvas-rom-data");
			case ULTRASND -> (db == null) || (db.getCanonicalExecutable() == null) ? null: new File(db.getCanonicalExecutable().getParentFile(), "ULTRASND");
		};
	}

	/**
	 * copies what was found into the folder (the same folders within it for a Sound Canvas and the Ultrasound); files there
	 * already are left as they are. Only the files of the find: SoundFonts, the ROMs known, the model folders, the patches.
	 *
	 * @return how many files were copied
	 */
	public static int copy(Find find, File to) throws IOException {
		int copied = 0;
		Files.createDirectories(to.toPath());
		switch (find.kind()) {
			case SOUNDFONT -> {
				File[] files = find.dir().listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).matches(".*\\.sf[23]"));
				if (files != null)
					for (File f: files)
						copied += copyFile(f, new File(to, f.getName()));
			}
			case MT32 -> {
				File[] files = find.dir().listFiles(f -> f.isFile() && MidiRoms.mt32Size(f.length()));
				if (files != null)
					for (File f: files)
						if (!MidiRoms.mt32ModelsOfSums(List.of(MidiRoms.sha1(f))).isEmpty() || MidiRoms.knownMt32Sum(MidiRoms.sha1(f)))
							copied += copyFile(f, new File(to, f.getName()));
			}
			case SOUNDCANVAS -> {
				for (String model: find.details()) {
					File[] dirs = find.dir().listFiles(d -> d.isDirectory() && d.getName().equalsIgnoreCase(model));
					if ((dirs != null) && (dirs.length > 0))
						copied += copyTree(dirs[0], new File(to, dirs[0].getName()), 0);
				}
			}
			case ULTRASND -> copied += copyTree(find.dir(), to, 0);
		}
		return copied;
	}

	private static int copyFile(File from, File to) throws IOException {
		if (to.exists())
			return 0;
		Files.copy(from.toPath(), to.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
		return 1;
	}

	private static int copyTree(File from, File to, int depth) throws IOException {
		int copied = 0;
		Files.createDirectories(to.toPath());
		File[] list = from.listFiles();
		if ((list == null) || (depth > 4))
			return 0;
		for (File f: list) {
			if (Files.isSymbolicLink(f.toPath()))
				continue;
			if (f.isDirectory())
				copied += copyTree(f, new File(to, f.getName()), depth + 1);
			else
				copied += copyFile(f, new File(to, f.getName()));
		}
		return copied;
	}
}
