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
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.ImageMount;
import org.dbgl.model.conf.mount.Mount;


/**
 * alpha 52: the DOS games in a folder that no profile has yet. Each folder in the chosen folder is a game (the way most keep
 * their games: D:\DOS\Doom, D:\DOS\Keen4); it counts when it holds a DOS program (an .EXE that is not a Windows program, a
 * .COM) and no profile starts a program in it, mounts it or something in it. A profile that mounts the chosen folder itself
 * (or one above it) as drive C: does not count for the folders in it. Games of GOG or Steam (with their own DOSBox) are left to
 * their own import. Nothing is changed; the profiles are made by the import window, with a start file guessed.
 */
public final class UnlinkedGames {

	/** a folder of a game: the DOS programs in it (the most likely start file first), the setup program if there is one */
	public record Game(File dir, List<File> programs, File main, File setup) {
	}

	/** what was found, and how many folders were left out: had a profile, only Windows programs, a store game's DOSBox, nothing */
	public record Result(List<Game> games, int linked, int windows, int store, int empty) {
	}

	static final int MAX_DEPTH = 4;
	static final int MAX_FILES = 20_000;

	/** programs that are not the game: installers, helpers, DOS extenders */
	static final Pattern NOT_MAIN = Pattern.compile("(?i)(setup|install|instal|config|setsound|sndsetup|soundset|sound|uninst|unins\\d*|unzip|pkunzip|lha|arj|dos4gw|dos32a|pmode\\w*|cwsdpmi|rtm|dpmi\\w*|"
			+ "readme|read|catalog|order|patch|update|chkdsk|mem|debug|edit|cmd|command|keyb|mouse|vesa\\w*|univbe|uvconfig|drvinst|ipxsetup|nullmod|serial|modem)\\.(exe|com|bat)");
	static final Pattern SETUP = Pattern.compile("(?i)(setup|install|config|setsound|sndsetup|soundset)\\w*\\.(exe|com|bat)");

	private UnlinkedGames() {
	}

	/** the folders the profiles use: where their program is, what they mount (folders, images) */
	public static List<File> usedBy(List<Profile> profiles) {
		List<File> result = new ArrayList<>();
		for (Profile p: profiles) {
			try {
				File g = p.getCombinedConfiguration().getAutoexec().getCanonicalGameDir();
				if (g != null)
					result.add(g);
				for (Mount m: p.getConfiguration().getAutoexec().getMountingpoints()) {
					if (m instanceof DirMount dm && (dm.getCanonicalPath() != null))
						result.add(dm.getCanonicalPath());
					else if (m instanceof ImageMount im && (im.getCanonicalImgPaths() != null))
						for (File img: im.getCanonicalImgPaths())
							result.add(img);
				}
			} catch (RuntimeException e) {
				// a profile without its configuration: nothing of it
			}
		}
		return result;
	}

	/** whether a profile uses this game folder: something in it, or it in a folder a profile mounts that is not the chosen one or above it */
	static boolean linked(File game, File root, List<File> used) {
		java.nio.file.Path g = game.toPath(), r = root.toPath();
		for (File u: used) {
			java.nio.file.Path p = u.toPath();
			if (p.startsWith(g))
				return true;
			if (g.startsWith(p) && !r.startsWith(p))
				return true;
		}
		return false;
	}

	public static Result find(File chosen, List<File> used, Consumer<File> progress, BooleanSupplier cancelled) {
		File root;
		try {
			root = chosen.getCanonicalFile();
		} catch (IOException e) {
			root = chosen.getAbsoluteFile();
		}
		List<File> canonicalUsed = new ArrayList<>();
		for (File u: used)
			try {
				canonicalUsed.add(u.getCanonicalFile());
			} catch (IOException e) {
				canonicalUsed.add(u.getAbsoluteFile());
			}
		List<Game> games = new ArrayList<>();
		int linked = 0, windows = 0, store = 0, empty = 0;
		File[] dirs = root.listFiles(f -> f.isDirectory() && !f.getName().startsWith(".") && !Files.isSymbolicLink(f.toPath()));
		if (dirs == null)
			return new Result(games, 0, 0, 0, 0);
		java.util.Arrays.sort(dirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
		for (File dir: dirs) {
			if ((cancelled != null) && cancelled.getAsBoolean())
				break;
			if (progress != null)
				progress.accept(dir);
			if (linked(dir, root, canonicalUsed)) {
				linked++;
				continue;
			}
			List<File> dos = new ArrayList<>();
			int[] count = {0, 0}; // files looked at, Windows programs
			boolean[] dosbox = {false};
			collect(dir, 0, dos, count, dosbox);
			if (dosbox[0]) {
				store++;
				continue;
			}
			if (dos.isEmpty()) {
				if (count[1] > 0)
					windows++;
				else
					empty++;
				continue;
			}
			dos.sort(order(dir));
			File setup = dos.stream().filter(f -> SETUP.matcher(f.getName()).matches()).findFirst().orElse(null);
			games.add(new Game(dir, dos, dos.get(0), setup == dos.get(0) ? null: setup));
		}
		return new Result(games, linked, windows, store, empty);
	}

	private static void collect(File dir, int depth, List<File> dos, int[] count, boolean[] dosbox) {
		File[] list = dir.listFiles();
		if ((list == null) || dosbox[0])
			return;
		for (File f: list) {
			if (count[0]++ > MAX_FILES)
				return;
			String n = f.getName().toLowerCase(Locale.ROOT);
			if (f.isFile()) {
				if (n.equals("dosbox.exe") || n.startsWith("goggame-") && n.endsWith(".info")) {
					dosbox[0] = true; // GOG, Steam: their own import knows how the game starts
					return;
				}
				if (n.endsWith(".com") && (f.length() > 0) && (f.length() <= 65280))
					dos.add(f);
				else if (n.endsWith(".exe") && isMz(f)) {
					if (ProfileChecks.programKind(f) == null)
						dos.add(f);
					else
						count[1]++;
				}
			}
		}
		if (depth >= MAX_DEPTH)
			return;
		for (File f: list)
			if (f.isDirectory() && !f.getName().startsWith(".") && !Files.isSymbolicLink(f.toPath()))
				collect(f, depth + 1, dos, count, dosbox);
	}

	static boolean isMz(File f) {
		try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
			int b0 = r.read(), b1 = r.read();
			return ((b0 == 'M') && (b1 == 'Z')) || ((b0 == 'Z') && (b1 == 'M'));
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * the most likely start file first: not an installer or helper, named like the folder ("DOOM" in Doom), at the top of the
	 * folder, then the bigger one
	 */
	static Comparator<File> order(File dir) {
		String folder = dir.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		return Comparator.<File, Integer> comparing(f -> NOT_MAIN.matcher(f.getName()).matches() ? 1: 0)
				.thenComparing(f -> like(folder, StringUtils.substringBeforeLast(f.getName(), ".").toLowerCase(Locale.ROOT)) ? 0: 1)
				.thenComparing(f -> dir.toPath().relativize(f.toPath()).getNameCount())
				.thenComparing(f -> f.getName().toLowerCase(Locale.ROOT).endsWith(".exe") ? 0: 1)
				.thenComparing(Comparator.comparingLong(File::length).reversed())
				.thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER);
	}

	/** "doom" for Doom, "keen4e" for Keen4, "wolf3d" for "Wolfenstein 3D": the first letters fit */
	static boolean like(String folder, String program) {
		String p = program.replaceAll("[^a-z0-9]", "");
		if (p.isEmpty() || folder.isEmpty())
			return false;
		int n = Math.min(4, Math.min(p.length(), folder.length()));
		return folder.startsWith(p.substring(0, n)) || p.startsWith(folder.substring(0, Math.min(folder.length(), p.length())));
	}

	/** the title from the folder's name: "monkey_island_2" -> "Monkey Island 2", "KEEN4" stays as it is */
	public static String titleOf(File dir) {
		String n = dir.getName().replace('_', ' ').replaceAll("\\s+", " ").trim();
		if (n.equals(n.toLowerCase(Locale.ROOT)))
			n = org.apache.commons.text.WordUtils.capitalize(n);
		return n;
	}

	/** the import entry for a game: its own dosbox.conf (if it has one) without its [autoexec], the folder as C:, the start file */
	public static StoreGameImport.Found toFound(Game g, File work, String label) throws IOException {
		File own = new File(g.dir(), "dosbox.conf");
		String conf = LaunchBoxImport.confFor(own.isFile() && (own.length() < 1_000_000) ? StoreGameImport.read(own): null, g.dir(), g.main(), "");
		File made = File.createTempFile("dbgl-folder-", ".conf", work);
		Files.write(made.toPath(), conf.getBytes(StandardCharsets.ISO_8859_1));
		made.deleteOnExit();
		StoreGameImport.Meta meta = new StoreGameImport.Meta("", "", "", "", "", () -> null, List::of, List.of(), null, g.setup());
		return new StoreGameImport.Found(titleOf(g.dir()), g.dir(), List.of(made), g.dir(), label, meta);
	}
}
