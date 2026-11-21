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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.Mount;


/**
 * Alpha 48 (wish 11): backups of the saved games of a profile. Before each start (if switched on for the profile) the files
 * that match its patterns, in the folder of its drive C: (or its first folder), go into a ZIP file in "savegames" next to its
 * screenshots; the newest ones are kept. A backup can be restored (the state then goes into a backup first). The settings of
 * the profile are in savegames.properties in that folder, so that nothing in the database or the profile file changes.
 */
public final class SavegameBackup {

	public static final String DIR = "savegames";
	private static final String SETTINGS = "savegames.properties";
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
	private static final int MAX_DEPTH = 8;
	/** alpha 52: no file larger than this is suggested */
	static final long MAX_SUGGESTED_SIZE = 4L * 1024 * 1024;

	/** the settings of a profile: on before each start, which files (patterns separated by ;), how many backups to keep */
	public record Config(boolean enabled, String patterns, int keep) {
	}

	private SavegameBackup() {
	}

	public static File dir(Profile p) {
		return new File(p.getCanonicalCaptures(), DIR);
	}

	public static Config load(Profile p) {
		Properties props = new Properties();
		File f = new File(dir(p), SETTINGS);
		if (f.isFile()) {
			try (InputStream in = Files.newInputStream(f.toPath())) {
				props.load(in);
			} catch (IOException | RuntimeException e) {
				// the defaults then
			}
		}
		int keep = 10;
		try {
			keep = Math.max(1, Integer.parseInt(props.getProperty("keep", "10").trim()));
		} catch (NumberFormatException e) {
			// 10
		}
		return new Config(Boolean.parseBoolean(props.getProperty("enabled", "false")), props.getProperty("patterns", ""), keep);
	}

	public static void save(Profile p, Config c) throws IOException {
		Properties props = properties(p); // alpha 52: the names and the kept ones stay
		if (!props.containsKey(V52)) { // a file of an earlier alpha (or none): large ones stay allowed where backups were on already
			props.setProperty("large", String.valueOf(largeAllowed(props)));
			props.setProperty(V52, "true");
		}
		props.setProperty("enabled", String.valueOf(c.enabled()));
		props.setProperty("patterns", c.patterns());
		props.setProperty("keep", String.valueOf(c.keep()));
		store(p, props);
	}

	/** alpha 52: all that is in savegames.properties (settings, names of backups, kept ones, large backups allowed) */
	private static Properties properties(Profile p) {
		Properties props = new Properties();
		File f = new File(dir(p), SETTINGS);
		if (f.isFile()) {
			try (InputStream in = Files.newInputStream(f.toPath())) {
				props.load(in);
			} catch (IOException | RuntimeException e) {
				// empty then
			}
		}
		return props;
	}

	private static void store(Profile p, Properties props) throws IOException {
		File d = dir(p);
		Files.createDirectories(d.toPath());
		try (OutputStream out = Files.newOutputStream(new File(d, SETTINGS).toPath())) {
			props.store(out, "DBGL Extended Edition: backups of the saved games of this profile");
		}
	}

	/** alpha 52: whether the state of the saved games is shown before a start, with the chance to start with another backup (on) */
	public static boolean showAtStart(Profile p) {
		return Boolean.parseBoolean(properties(p).getProperty("showatstart", "true"));
	}

	public static void setShowAtStart(Profile p, boolean show) throws IOException {
		Properties props = properties(p);
		props.setProperty("showatstart", String.valueOf(show));
		store(p, props);
	}

	/** alpha 52: from this size on (all files together) a backup is asked for first, and not made before a start unless allowed */
	public static final long LARGE = 50L * 1024 * 1024;

	/** alpha 52: the name given to a backup (empty if none) */
	public static String nameOf(Profile p, File zip) {
		return properties(p).getProperty("name." + zip.getName(), "");
	}

	public static void setName(Profile p, File zip, String name) throws IOException {
		Properties props = properties(p);
		if ((name == null) || name.isBlank())
			props.remove("name." + zip.getName());
		else
			props.setProperty("name." + zip.getName(), name.trim());
		store(p, props);
	}

	/** alpha 52: a kept backup is never removed for the number to keep (only when deleted by hand) */
	public static boolean isKept(Profile p, File zip) {
		return Boolean.parseBoolean(properties(p).getProperty("kept." + zip.getName(), "false"));
	}

	public static void setKept(Profile p, File zip, boolean kept) throws IOException {
		Properties props = properties(p);
		if (kept)
			props.setProperty("kept." + zip.getName(), "true");
		else
			props.remove("kept." + zip.getName());
		store(p, props);
	}

	/** alpha 52: whether backups from LARGE on were allowed (asked once), also before each start */
	public static boolean largeAllowed(Profile p) {
		return largeAllowed(properties(p));
	}

	/** written by alpha 52 on; a file without it is of an earlier alpha (0.101 alpha 48 to 51) */
	private static final String V52 = "v52";

	/**
	 * a file of an earlier alpha knew no limit: where backups were on there, they stay as they were (also large ones), so that
	 * none stops without notice; asked from alpha 52 on only when they are switched on again or made by hand
	 */
	private static boolean largeAllowed(Properties props) {
		if (!props.containsKey(V52))
			return Boolean.parseBoolean(props.getProperty("enabled", "false"));
		return Boolean.parseBoolean(props.getProperty("large", "false"));
	}

	public static void setLargeAllowed(Profile p, boolean allowed) throws IOException {
		Properties props = properties(p);
		props.setProperty("large", String.valueOf(allowed));
		props.setProperty(V52, "true");
		store(p, props);
	}

	/** alpha 52: the size of the files, in bytes */
	public static long size(Map<String, File> files) {
		return files.values().stream().mapToLong(File::length).sum();
	}

	/** alpha 52: the backup with the same files as in the game folder now (the state the game is in), or null */
	public static File current(Profile p) {
		try {
			Map<String, File> files = matching(gameDir(p), load(p).patterns());
			if (files.isEmpty())
				return null;
			String now = manifest(files);
			for (File zip: backups(p))
				if (now.equals(commentOf(zip)))
					return zip;
		} catch (IOException | RuntimeException e) {
			// none
		}
		return null;
	}

	/** the folder the patterns are in: that of drive C:, else the first folder mounted; null if there is none */
	public static File gameDir(Profile p) {
		DirMount first = null;
		for (Mount m: p.getNettoMountingPoints()) {
			if (m instanceof DirMount dm) {
				if (Character.toUpperCase(dm.getDrive()) == 'C')
					return existing(dm.getCanonicalPath());
				if (first == null)
					first = dm;
			}
		}
		return first == null ? null: existing(first.getCanonicalPath());
	}

	private static File existing(File f) {
		return f != null && f.isDirectory() ? f: null;
	}

	/** a pattern as a regular expression on the path relative to the folder, with "/" and in lower case: * ? and ** (any folders) */
	public static Pattern glob(String pattern) {
		String p = pattern.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
		while (p.startsWith("/"))
			p = p.substring(1);
		if (p.endsWith("/"))
			p += "**";
		StringBuilder rx = new StringBuilder();
		for (int i = 0; i < p.length(); i++) {
			char c = p.charAt(i);
			if (c == '*' && i + 1 < p.length() && p.charAt(i + 1) == '*') {
				rx.append(".*");
				i++;
				if (i + 1 < p.length() && p.charAt(i + 1) == '/')
					i++;
			} else if (c == '*') {
				rx.append("[^/]*");
			} else if (c == '?') {
				rx.append("[^/]");
			} else {
				rx.append(Pattern.quote(String.valueOf(c)));
			}
		}
		return Pattern.compile(rx.toString());
	}

	/** the files in the folder that match one of the patterns (separated by ; or new lines), by their relative path */
	public static Map<String, File> matching(File gameDir, String patterns) throws IOException {
		Map<String, File> result = new LinkedHashMap<>();
		List<Pattern> globs = new ArrayList<>();
		for (String s: patterns.split("[;\\r\\n]+"))
			if (!s.isBlank())
				globs.add(glob(s));
		if (gameDir == null || globs.isEmpty())
			return result;
		try (var walk = Files.walk(gameDir.toPath(), MAX_DEPTH)) {
			for (Path path: (Iterable<Path>)walk.sorted()::iterator) {
				if (!Files.isRegularFile(path) || Files.isSymbolicLink(path))
					continue;
				String rel = gameDir.toPath().relativize(path).toString().replace('\\', '/');
				String lower = rel.toLowerCase(Locale.ROOT);
				if (globs.stream().anyMatch(g -> g.matcher(lower).matches()))
					result.put(rel, path.toFile());
			}
		}
		return result;
	}

	/** the backups, newest first */
	public static List<File> backups(Profile p) {
		File[] files = dir(p).listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".zip"));
		if (files == null)
			return List.of();
		List<File> list = new ArrayList<>(Arrays.asList(files));
		list.sort(Comparator.comparing(File::getName).reversed());
		return list;
	}

	/** the files and their state, as written into the comment of a backup: an unchanged state needs no new backup */
	static String manifest(Map<String, File> files) {
		StringBuilder sb = new StringBuilder();
		files.forEach((rel, f) -> sb.append(rel).append('|').append(f.length()).append('|').append(f.lastModified()).append('\n'));
		return Integer.toHexString(sb.toString().hashCode()) + "-" + files.size();
	}

	/**
	 * a backup now
	 *
	 * @param suffix added to the name (e.g. before a restore), or null
	 * @return the new ZIP file, or null when there is nothing to save or nothing changed since the last backup
	 */
	public static File backup(Profile p, String suffix) throws IOException {
		return backup(p, suffix, null);
	}

	/** alpha 52: told about each file put into the backup (for a progress bar), may be null */
	public static File backup(Profile p, String suffix, java.util.function.IntConsumer progress) throws IOException {
		Config c = load(p);
		File gameDir = gameDir(p);
		Map<String, File> files = matching(gameDir, c.patterns());
		if (files.isEmpty())
			return null;
		String manifest = manifest(files);
		List<File> existing = backups(p);
		if (suffix == null && !existing.isEmpty() && manifest.equals(commentOf(existing.get(0))))
			return null; // the same as the newest backup
		File d = dir(p);
		Files.createDirectories(d.toPath());
		String name = LocalDateTime.now().format(STAMP) + (suffix == null ? "": "-" + suffix);
		File zip = new File(d, name + ".zip");
		for (int i = 2; zip.exists(); i++)
			zip = new File(d, name + "-" + i + ".zip");
		File part = new File(zip.getPath() + ".part");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(part.toPath()))) {
			out.setComment(manifest);
			for (Map.Entry<String, File> e: files.entrySet()) {
				ZipEntry entry = new ZipEntry(e.getKey());
				entry.setTime(e.getValue().lastModified());
				out.putNextEntry(entry);
				Files.copy(e.getValue().toPath(), out);
				out.closeEntry();
				if (progress != null)
					progress.accept(1);
			}
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(part.toPath());
			throw e;
		}
		Files.move(part.toPath(), zip.toPath());
		if (suffix == null) {
			prune(p, c.keep()); // not before a restore: the backup to restore could be the oldest one
			SecondBackupPlace.savegameBackupMade(p, zip, c.keep()); // alpha 52
		}
		return zip;
	}

	private static String commentOf(File zip) {
		try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip)) {
			return z.getComment();
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** only the newest ones stay; alpha 52: kept ones always stay and are not counted */
	static void prune(Profile p, int keep) {
		Properties props = properties(p);
		List<File> list = new ArrayList<>(backups(p));
		list.removeIf(f -> Boolean.parseBoolean(props.getProperty("kept." + f.getName(), "false")));
		for (int i = keep; i < list.size(); i++)
			list.get(i).delete();
	}

	/** what is in a backup: the number of files */
	public static int count(File zip) {
		try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip)) {
			return z.size();
		} catch (IOException | RuntimeException e) {
			return 0;
		}
	}

	/**
	 * a backup back into the folder of the game: the files as they are now into a backup first ("before-restore"); files of the
	 * game not in the backup stay. Only paths inside the folder are written.
	 */
	public static int restore(Profile p, File zip) throws IOException {
		File gameDir = gameDir(p);
		if (gameDir == null)
			throw new IOException(org.dbgl.service.TextService.getInstance().get("dialog.savegames.nogamedir"));
		backup(p, "before-restore");
		Path root = gameDir.toPath().toAbsolutePath().normalize();
		int n = 0;
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip.toPath()))) {
			for (ZipEntry e; (e = in.getNextEntry()) != null;) {
				if (e.isDirectory())
					continue;
				Path target = root.resolve(e.getName()).normalize();
				if (!target.startsWith(root))
					continue; // never outside the folder
				Files.createDirectories(target.getParent());
				Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				if (e.getTime() > 0)
					target.toFile().setLastModified(e.getTime());
				n++;
			}
		}
		return n;
	}

	/**
	 * patterns to start with: files with the usual names of saved games (SAVE*, *.SAV, *.GAM, ...), and files changed well after
	 * the game was installed (later than most of its files); program files are never suggested
	 */
	public static String suggest(File gameDir) throws IOException {
		if (gameDir == null)
			return "";
		List<Path> files = new ArrayList<>();
		try (var walk = Files.walk(gameDir.toPath(), MAX_DEPTH)) {
			walk.filter(Files::isRegularFile).forEach(files::add);
		}
		if (files.isEmpty())
			return "";
		long[] times = files.stream().mapToLong(f -> f.toFile().lastModified()).sorted().toArray();
		long installed = times[times.length / 4]; // most files of a game are as old as the game (saved games are few, or in one folder)
		Pattern usual = Pattern.compile("(save.*|.*\\.(sav|sv\\d|gam|sg\\d?|svg|save|ssg|dsg|[0-9]{3}))", Pattern.CASE_INSENSITIVE);
		// alpha 52: saved games of DOS games are small; big files are data of the game (Lost Eden: 375 MB were suggested)
		files.removeIf(f -> f.toFile().length() > MAX_SUGGESTED_SIZE);
		Pattern program = Pattern.compile(".*\\.(exe|com|bat|ovl|ovr|dll|drv|dig|mdi|ini|cfg|txt|doc|pdf|lnk|ico|zip)", Pattern.CASE_INSENSITIVE);
		java.util.Set<String> found = new java.util.TreeSet<>();
		Map<String, Integer> perFolder = new LinkedHashMap<>();
		for (Path f: files) {
			String name = f.getFileName().toString();
			if (program.matcher(name).matches())
				continue;
			boolean later = f.toFile().lastModified() > installed + 24L * 60 * 60 * 1000;
			if (usual.matcher(name).matches() || later) {
				String rel = gameDir.toPath().relativize(f).toString().replace('\\', '/');
				found.add(rel);
				String folder = rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')): "";
				perFolder.merge(folder, 1, Integer::sum);
			}
		}
		// many in one sub folder (e.g. SAVES): the folder instead of each file
		List<String> patterns = new ArrayList<>();
		java.util.Set<String> folders = new java.util.TreeSet<>();
		perFolder.forEach((folder, count) -> {
			if (!folder.isEmpty() && count > 3)
				folders.add(folder);
		});
		folders.forEach(f -> patterns.add(f + "/**"));
		for (String rel: found)
			if (folders.stream().noneMatch(f -> rel.startsWith(f + "/")))
				patterns.add(rel);
		return String.join("; ", patterns);
	}

	/** before a start: a backup if it is switched on for the profile; a problem never keeps the game from starting */
	public static void beforeStart(Profile p) {
		try {
			if (load(p).enabled()) {
				// alpha 52: large ones only when allowed for the profile (asked in the profile editor)
				if (!largeAllowed(p) && (size(matching(gameDir(p), load(p).patterns())) >= LARGE)) {
					System.err.println("Savegame backup of " + p.getTitle() + ": skipped, larger than " + (LARGE >> 20) + " MB (not allowed for it)");
					return;
				}
				backup(p, null);
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("Savegame backup of " + p.getTitle() + ": " + e);
		}
	}
}
