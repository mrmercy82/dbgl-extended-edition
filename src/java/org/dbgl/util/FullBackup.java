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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.service.BackupService;
import org.dbgl.service.FileLocationService;


/**
 * A backup of everything before an update, if the user wants it: besides what DBGL keeps about the games (the database,
 * profiles, templates and settings, as "Backup" does it, so it can be brought back with "Restore"), the folders of the DOSBox
 * versions with their configuration files, the program folder of DBGL, the captures and the games. Folders inside others are
 * written once, under their own name; a folder that is not chosen is left out also where it is inside a chosen one (e.g.
 * the games inside the program folder of a portable installation). A DOSBox installed into a system folder (e.g. /usr/bin)
 * is not copied, only its configuration file.
 */
public final class FullBackup {

	/** DATA: always in it (the logs; the rest of DBGL's data is in {@link #DATA_ZIP}) */
	public enum Part {
		DATA, DOSBOX, PROGRAM, CAPTURES, BACKUPS, GAMES
	}

	/** a folder (or a single file) and where it goes in the zip file */
	public record Item(Part part, String name, File file) {
	}

	public static final String DATA_ZIP = "dbgl-data.zip", README = "README.txt";

	/** the name of these backups; earlier ones are never in a new one (each would hold all the ones before it) */
	public static final String PREFIX = "dbgl-full-backup-before-update-";

	private FullBackup() {
	}

	/** everything that could be backed up, folders inside others included (they are written once, see {@link #files}) */
	public static List<Item> items(List<DosboxVersion> versions, File programDir) {
		List<Item> result = new ArrayList<>();
		Set<String> names = new HashSet<>();
		for (DosboxVersion v: versions) {
			String name = "dosbox/" + unique(names, "dosbox/" + safe(v.getTitle())).substring(7);
			File dir = canonical(v.getCanonicalPath());
			if ((dir != null) && dir.isDirectory() && !isSystemDir(dir))
				add(result, new Item(Part.DOSBOX, name, appBundle(dir)));
			File conf = canonical(v.getConfigurationCanonicalFile());
			if ((conf != null) && conf.isFile())
				add(result, new Item(Part.DOSBOX, name + "/" + conf.getName(), conf)); // left out when it is in the folder written
		}
		FileLocationService f = FileLocationService.getInstance();
		// the logs: of DOSBox (see DosboxLog) and of the last update installed
		add(result, new Item(Part.DATA, "logs", canonical(DosboxLog.logsDir())));
		for (String log: new String[] {"update-log.txt", "updater-output.txt"})
			add(result, new Item(Part.DATA, "logs/update/" + log, canonical(new File(EditionUpdater.workDir(), log))));
		if (programDir != null)
			add(result, new Item(Part.PROGRAM, "dbgl", canonical(programDir)));
		add(result, new Item(Part.CAPTURES, "captures", canonical(new File(f.getDataDir(), "captures"))));
		// the backups made before: automatic ones, before a new version, before updates (not the earlier ones of these)
		add(result, new Item(Part.BACKUPS, "backups", canonical(BackupService.defaultAutoBackupDir())));
		String autoDir = org.dbgl.service.SettingsService.getInstance().getValue("backup", "dir");
		if ((autoDir != null) && !autoDir.isBlank())
			add(result, new Item(Part.BACKUPS, "backups/" + safe(new File(autoDir).getName()), canonical(new File(autoDir))));
		add(result, new Item(Part.GAMES, "dosroot", canonical(f.getDosroot())));
		result.removeIf(i -> (i.file() == null) || !i.file().exists());
		return result;
	}

	private static void add(List<Item> list, Item item) {
		if ((item.file() != null) && list.stream().noneMatch(i -> i.file().equals(item.file())))
			list.add(item);
	}

	/** never in the backup: what DBGL keeps itself (backed up apart), the backups and the update being installed */
	static Set<File> alwaysLeftOut(File target) {
		FileLocationService f = FileLocationService.getInstance();
		Set<File> result = new HashSet<>();
		for (File d: new File[] {f.getProfilesDir(), f.getTemplatesDir(), new File(f.getDataDir(), "db"), EditionUpdater.workDir(), target})
			if (d != null)
				result.add(canonical(d));
		return result;
	}

	/** the files of an item: without the other items inside it (written under their own name) and what is always left out */
	public static List<Path> files(Item item, List<Item> all, Set<File> leftOut) throws IOException {
		List<Path> result = new ArrayList<>();
		Path root = item.file().toPath();
		if (Files.isRegularFile(root)) { // a configuration file (written once, also when it is in a folder written)
			result.add(root);
			return result;
		}
		Set<Path> skip = new HashSet<>();
		for (Item i: all)
			if ((i != item) && Files.isDirectory(i.file().toPath()))
				skip.add(i.file().toPath());
		for (File f: leftOut)
			if (f != null)
				skip.add(f.toPath());
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				return !dir.equals(root) && skip.contains(dir) ? FileVisitResult.SKIP_SUBTREE: FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (attrs.isRegularFile() && !skip.contains(file) && !file.getFileName().toString().startsWith(PREFIX))
					result.add(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException e) {
				return FileVisitResult.CONTINUE; // e.g. no permission: left out
			}
		});
		return result;
	}

	/** @return the size of the files of the items, each file once, see {@link #files} */
	public static long size(List<Item> items, List<Item> all, File target) {
		Set<Path> seen = new HashSet<>();
		Set<File> leftOut = alwaysLeftOut(target);
		long sum = 0;
		for (Item item: items) {
			try {
				for (Path p: files(item, all, leftOut))
					if (seen.add(p))
						sum += p.toFile().length();
			} catch (IOException e) {
				// not readable: not in the backup either
			}
		}
		return sum;
	}

	/**
	 * Write the backup. The items not chosen are left out (also inside chosen ones).
	 *
	 * @param progress counts up to 1000
	 * @param stop true: cancelled, the half written file is removed
	 * @return the number of files written
	 */
	public static int write(File zipFile, List<Item> chosen, List<Item> all, String fromVersion, AtomicInteger progress, java.util.function.BooleanSupplier stop)
			throws IOException, java.sql.SQLException, InterruptedException {
		Set<File> leftOut = alwaysLeftOut(zipFile);
		List<List<Path>> files = new ArrayList<>();
		Set<Path> seen = new HashSet<>();
		long total = 0;
		for (Item i: chosen) {
			List<Path> l = new ArrayList<>(files(i, all, leftOut));
			l.removeIf(p -> !seen.add(p)); // each file once (e.g. a configuration file in the folder of its DOSBox version)
			files.add(l);
			for (Path p: l)
				total += p.toFile().length();
		}
		SKIPPED.get().clear();
		File data = File.createTempFile("dbgl-data", ".zip");
		int count = 0;
		try {
			BackupService.backup(data);
			total += data.length();
			long done = 0;
			StringBuilder readme = new StringBuilder("DBGL " + fromVersion + ", " + java.time.LocalDateTime.now().withNano(0) + "\n\n" + DATA_ZIP + "\n  "
					+ org.dbgl.service.TextService.getInstance().get("dialog.updatebackup.readme.data") + "\n\n");
			try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
				zip.setLevel(Deflater.BEST_SPEED); // games and DOSBox are big: fast rather than small
				done += put(zip, data.toPath(), DATA_ZIP, progress, done, total, stop);
				count++;
				for (int n = 0; n < chosen.size(); n++) {
					Item item = chosen.get(n);
					if (files.get(n).isEmpty())
						continue;
					Path root = item.file().toPath();
					readme.append(item.name()).append('\n').append("  ").append(item.file().getPath()).append("\n\n");
					for (Path p: files.get(n)) {
						String name = Files.isRegularFile(root) ? item.name(): item.name() + "/" + root.relativize(p).toString().replace(File.separatorChar, '/');
						done += put(zip, p, name, progress, done, total, stop);
						count++;
						if (stop.getAsBoolean())
							throw new InterruptedException(); // cancelled
					}
				}
				readme.append(org.dbgl.service.TextService.getInstance().get("dialog.updatebackup.readme.folders")).append('\n');
				if (!SKIPPED.get().isEmpty()) {
					readme.append('\n').append(org.dbgl.service.TextService.getInstance().get("dialog.updatebackup.readme.skipped")).append('\n');
					SKIPPED.get().forEach(s -> readme.append("  ").append(s).append('\n'));
					System.out.println("Not in the backup (could not be read): " + SKIPPED.get());
				}
				zip.putNextEntry(new ZipEntry(README));
				zip.write(readme.toString().replace("\n", System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			} catch (IOException | InterruptedException | RuntimeException e) {
				Files.deleteIfExists(zipFile.toPath()); // no half backup that looks like a whole one
				throw e;
			}
		} finally {
			Files.deleteIfExists(data.toPath());
		}
		progress.set(1000);
		return count;
	}

	/** the files that could not be read while writing (per thread) */
	private static final ThreadLocal<List<String>> SKIPPED = ThreadLocal.withInitial(ArrayList::new);

	private static long put(ZipOutputStream zip, Path file, String name, AtomicInteger progress, long done, long total, java.util.function.BooleanSupplier stop)
			throws IOException, InterruptedException {
		InputStream opened;
		try {
			opened = Files.newInputStream(file);
		} catch (IOException e) {
			SKIPPED.get().add(file.toString()); // e.g. in use by another program: told in the README
			return file.toFile().length();
		}
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(file.toFile().lastModified());
		try {
			zip.putNextEntry(entry);
		} catch (java.util.zip.ZipException e) {
			opened.close(); // the same name twice (e.g. two chosen folders that overlap): once is enough, the backup goes on
			return file.toFile().length();
		}
		long written = 0;
		try (InputStream in = opened) {
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) {
				zip.write(buf, 0, n);
				written += n;
				if (stop.getAsBoolean())
					throw new InterruptedException(); // cancelled, also within a big file (e.g. a CD image)
				if (total > 0)
					progress.set((int)Math.min(999, 1000 * (done + written) / total));
			}
		}
		zip.closeEntry();
		return written;
	}

	/** e.g. /usr/bin: other programs are there too, it is not the folder of DOSBox */
	static boolean isSystemDir(File dir) {
		String p = dir.getPath().replace('\\', '/').toLowerCase(Locale.ROOT);
		if (dir.getParentFile() == null)
			return true; // a drive or /
		if (SystemUtils.IS_WINDOWS) {
			String win = System.getenv("SystemRoot");
			return (win != null) && p.startsWith(win.replace('\\', '/').toLowerCase(Locale.ROOT));
		}
		for (String s: new String[] {"/usr/", "/bin", "/sbin", "/lib", "/snap/", "/nix/", "/var/lib/flatpak/", "/app/", "/opt/homebrew/bin", "/opt/local/bin"})
			if (p.startsWith(s) || (p + "/").equals(s))
				return true;
		return false;
	}

	/** macOS: the whole DOSBox.app, not only its Contents/MacOS */
	private static File appBundle(File dir) {
		for (File d = dir; d != null; d = d.getParentFile())
			if (d.getName().toLowerCase(Locale.ROOT).endsWith(".app"))
				return d;
		return dir;
	}

	private static File canonical(File f) {
		if (f == null)
			return null;
		try {
			return f.getCanonicalFile();
		} catch (IOException e) {
			return f.getAbsoluteFile();
		}
	}

	private static String safe(String title) {
		String s = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
		return s.isEmpty() ? "DOSBox": s;
	}

	private static String unique(Set<String> names, String name) {
		String n = name;
		for (int i = 2; !names.add(n.toLowerCase(Locale.ROOT)); i++)
			n = name + " (" + i + ")";
		return n;
	}

	/** e.g. "3.4 GB" */
	public static String readable(long bytes) {
		if (bytes < 1024 * 1024)
			return String.format(Locale.ROOT, "%d KB", Math.max(bytes > 0 ? 1: 0, bytes / 1024));
		if (bytes < 1024L * 1024 * 1024)
			return String.format("%.0f MB", bytes / (1024.0 * 1024));
		return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
	}

	/** remembered: what was chosen last time */
	public static final String SECTION = "editionupdate";

	public static Set<Part> remembered() {
		Set<Part> result = new LinkedHashSet<>();
		for (Part p: Part.values())
			if ((p == Part.DATA) || org.dbgl.service.SettingsService.getInstance().getBooleanValue(SECTION, "backup" + p.name().toLowerCase(Locale.ROOT)))
				result.add(p);
		return result;
	}

	public static void remember(Set<Part> parts) {
		for (Part p: Part.values())
			if (p != Part.DATA)
				org.dbgl.service.SettingsService.getInstance().setBooleanValue(SECTION, "backup" + p.name().toLowerCase(Locale.ROOT), parts.contains(p));
	}
}
