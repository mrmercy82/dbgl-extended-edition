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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;


/**
 * Alpha 50, the way back from a USB stick: what was played on a stick made by the assistant "Portable (USB stick)" comes
 * into the installation the stick was made from. For that the assistant puts a file on the stick ({@link #MANIFEST}): which
 * profiles went along with how often and how long they had been played, and which folder of this computer each folder of
 * the stick is. DBGL on the stick writes after each game how often and how long its profiles were played
 * ({@link #STATE}). Coming back are: the play time and the number of starts (what was added on the stick), files that are
 * new on the stick (saved games, screenshots) and files changed on the stick. A file changed on both sides since the
 * stick was made is not touched and told. Nothing is deleted here, and a file that is replaced is kept in a folder of
 * backups first.
 */
public final class StickReturn {

	public static final String MANIFEST = "stick-manifest.properties", STATE = "stick-state.properties";
	private static final String RETURNS = "stick-returns.properties";
	private static final long SLACK = 2000; // FAT keeps the time of a file to two seconds

	/** how often and how long a profile was played, and when last (0: never) */
	public record Stats(int runs, long seconds, long lastRun) {
		String line() {
			return runs + "|" + seconds + "|" + lastRun;
		}

		public static Stats of(String line) {
			try {
				String[] p = StringUtils.splitPreserveAllTokens(StringUtils.defaultString(line), "|", 4);
				return p.length < 3 ? null: new Stats(Integer.parseInt(p[0].trim()), Long.parseLong(p[1].trim()), Long.parseLong(p[2].trim()));
			} catch (NumberFormatException e) {
				return null;
			}
		}
	}

	/** what was played on the stick and is not here yet: starts and seconds to add, and when it was last played there */
	public record StatChange(int id, String title, int runs, long seconds, long lastRun) {
	}

	/** a file of the stick to copy here; replaces: there is another one here that is kept first */
	public record FileAction(File from, File to, boolean replaces) {
	}

	/**
	 * @param conflicts files changed on both sides (or that differ without either being changed since): not touched
	 * @param gone titles of profiles of the stick that are not here any more
	 * @param seen what the stick says now, kept after the return so the same is not added twice
	 */
	public record Plan(String stickId, long created, List<StatChange> stats, List<FileAction> files, List<File> conflicts, List<String> gone, Map<Integer, Stats> seen, boolean stateThere) {
		public boolean isEmpty() {
			return stats.isEmpty() && files.isEmpty() && conflicts.isEmpty();
		}
	}

	private StickReturn() {
	}

	/**
	 * the file for the stick
	 * 
	 * @param profiles id -> its numbers when the stick is made, and its title
	 * @param paths path in the folder of DBGL on the stick ("dosroot/GAMES/DOOM") -> the folder or file here
	 * @param dataDir, dosroot of this installation: what lies in them is written from there on ("@dosroot/GAMES/DOOM"), so the
	 *        stick does not carry more of the paths of this computer than it has to, and the way back still works after
	 *        this installation was moved
	 */
	public static byte[] manifest(String id, long created, Map<Integer, Map.Entry<Stats, String>> profiles, Map<String, File> paths, File dataDir, File dosroot) {
		StringBuilder sb = new StringBuilder("# DBGL Extended Edition: this stick, for \"Bring back from USB stick\" in the installation it was made from\n");
		sb.append("id=").append(id).append("\ncreated=").append(created).append('\n');
		profiles.forEach((pid, v) -> sb.append("p.").append(pid).append('=').append(escape(v.getKey().line() + "|" + v.getValue())).append('\n'));
		int n = 0;
		for (Map.Entry<String, File> e: paths.entrySet())
			sb.append("r.").append(n++).append('=').append(escape(StringUtils.stripEnd(e.getKey(), "/") + "|" + placed(e.getValue(), dataDir, dosroot))).append('\n');
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static final String IN_DOSROOT = "@dosroot/", IN_DATA = "@data/";

	private static String placed(File file, File dataDir, File dosroot) {
		String path = file.getAbsolutePath();
		for (Map.Entry<String, File> base: List.of(Map.entry(IN_DOSROOT, dosroot), Map.entry(IN_DATA, dataDir))) {
			String b = base.getValue().getAbsolutePath();
			if (path.startsWith(b + File.separator))
				return base.getKey() + path.substring(b.length() + 1).replace(File.separatorChar, '/');
		}
		return path;
	}

	private static File resolved(String placed, File dataDir, File dosroot) {
		if (placed.startsWith(IN_DOSROOT))
			return new File(dosroot, placed.substring(IN_DOSROOT.length()).replace('/', File.separatorChar));
		if (placed.startsWith(IN_DATA))
			return new File(dataDir, placed.substring(IN_DATA.length()).replace('/', File.separatorChar));
		return new File(placed);
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\n", " ").replace("\r", " ");
	}

	private static Properties load(File file) throws IOException {
		Properties result = new Properties();
		if (file.isFile())
			try (java.io.Reader in = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
				result.load(in);
			}
		return result;
	}

	private static void store(File file, Map<String, String> values, String comment) throws IOException {
		StringBuilder sb = new StringBuilder("# ").append(comment).append('\n');
		new TreeMap<>(values).forEach((k, v) -> sb.append(k).append('=').append(escape(v)).append('\n'));
		Files.createDirectories(file.getAbsoluteFile().getParentFile().toPath());
		File part = new File(file.getPath() + ".part");
		Files.write(part.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
		Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	/** is this the folder of a DBGL on a stick made for the way back */
	public static boolean isStick(File dir) {
		return (dir != null) && new File(dir, MANIFEST).isFile();
	}

	/**
	 * DBGL on the stick: how often and how long its profiles were played, written next to the manifest. Does nothing in a
	 * folder without one (an ordinary installation).
	 */
	public static void writeState(File dir, Map<Integer, Stats> profiles) {
		if (!isStick(dir))
			return;
		Map<String, String> values = new LinkedHashMap<>();
		profiles.forEach((id, s) -> values.put("p." + id, s.line()));
		try {
			store(new File(dir, STATE), values, "DBGL Extended Edition: how often and how long the profiles of this stick were played");
		} catch (IOException e) {
			// the stick cannot be written (protected, full): the numbers stay in its database, the next game tries again
		}
	}

	/**
	 * what the stick has that this installation does not
	 * 
	 * @param stick the folder of DBGL on the stick
	 * @param here the profiles of this installation: id -> title
	 * @param dataDir the data folder of this installation (it keeps what came back from which stick before)
	 * @param dosroot the DOSroot of this installation
	 */
	public static Plan plan(File stick, Map<Integer, String> here, File dataDir, File dosroot) throws IOException {
		return plan(stick, here, dataDir, dosroot, () -> false);
	}

	/** alpha 54: as above; stop says when to give up (the comparing was cancelled), with an exception then */
	public static Plan plan(File stick, Map<Integer, String> here, File dataDir, File dosroot, java.util.function.BooleanSupplier stop) throws IOException {
		Properties manifest = load(new File(stick, MANIFEST));
		String id = manifest.getProperty("id", "");
		long created = Long.parseLong(StringUtils.defaultIfBlank(manifest.getProperty("created"), "0").trim());
		File stateFile = new File(stick, STATE);
		Properties state = load(stateFile), returned = load(returnsFile(dataDir));
		List<StatChange> stats = new ArrayList<>();
		List<String> gone = new ArrayList<>();
		Map<Integer, Stats> seen = new LinkedHashMap<>();
		for (String key: new java.util.TreeSet<>(manifest.stringPropertyNames())) {
			if (!key.startsWith("p."))
				continue;
			int pid;
			try {
				pid = Integer.parseInt(key.substring(2));
			} catch (NumberFormatException e) {
				continue;
			}
			String line = manifest.getProperty(key);
			Stats base = Stats.of(line), now = Stats.of(state.getProperty(key)), before = Stats.of(returned.getProperty(id + "." + pid));
			String[] parts = StringUtils.splitPreserveAllTokens(line, "|", 4);
			String title = parts.length > 3 ? parts[3]: String.valueOf(pid);
			if ((base == null) || (now == null))
				continue;
			if (before != null)
				base = before; // what came back the last time is here already
			int runs = Math.max(0, now.runs() - base.runs());
			long seconds = Math.max(0, now.seconds() - base.seconds());
			if ((runs == 0) && (seconds == 0))
				continue;
			// the same number here may be another profile by now (the one of the stick removed, a new one made): the title
			// has to be the same
			if (!title.equals(here.get(pid))) {
				gone.add(title);
				continue;
			}
			stats.add(new StatChange(pid, title, runs, seconds, now.lastRun()));
			seen.put(pid, now);
		}
		List<FileAction> files = new ArrayList<>();
		List<File> conflicts = new ArrayList<>();
		java.util.Set<File> handled = new java.util.HashSet<>();
		for (String key: sortedRoots(manifest)) {
			String[] parts = StringUtils.splitPreserveAllTokens(manifest.getProperty(key), "|", 2);
			if (parts.length < 2)
				continue;
			File from = new File(stick, parts[0].replace('/', File.separatorChar)), to = resolved(parts[1], dataDir, dosroot);
			compare(from, to, created, files, conflicts, handled, stop);
		}
		return new Plan(id, created, stats, files, conflicts, gone, seen, stateFile.isFile());
	}

	/** the numbers of the profiles that went along on this stick */
	public static java.util.Set<Integer> profilesOf(File stick) throws IOException {
		java.util.Set<Integer> ids = new java.util.TreeSet<>();
		for (String key: load(new File(stick, MANIFEST)).stringPropertyNames()) {
			if (key.startsWith("p."))
				try {
					ids.add(Integer.parseInt(key.substring(2)));
				} catch (NumberFormatException e) {
					// not a profile
				}
		}
		return ids;
	}

	private static List<String> sortedRoots(Properties manifest) {
		List<String> keys = new ArrayList<>();
		for (String key: manifest.stringPropertyNames())
			if (key.matches("r\\.\\d+"))
				keys.add(key);
		keys.sort(java.util.Comparator.comparingInt(k -> Integer.parseInt(k.substring(2))));
		return keys;
	}

	private static void compare(File from, File to, long created, List<FileAction> files, List<File> conflicts, java.util.Set<File> handled, java.util.function.BooleanSupplier stop)
			throws IOException {
		if (stop.getAsBoolean())
			throw new java.io.InterruptedIOException("cancelled");
		if (from.isDirectory()) {
			File[] children = from.listFiles();
			if (children != null) {
				Arrays.sort(children);
				for (File child: children)
					compare(child, new File(to, child.getName()), created, files, conflicts, handled, stop);
			}
			return;
		}
		// alpha 54: a set instead of looking through all files so far for each file (slow with the thousands of files of a large game)
		if (!from.isFile() || !handled.add(to))
			return; // a folder mapped twice (one inside another)
		if (to.isDirectory()) {
			conflicts.add(to);
		} else if (!to.exists()) {
			files.add(new FileAction(from, to, false));
		} else if (!same(from, to)) {
			boolean changedThere = from.lastModified() > created + SLACK, changedHere = to.lastModified() > created + SLACK;
			if (changedThere && !changedHere)
				files.add(new FileAction(from, to, true));
			else if (changedThere || !changedHere)
				conflicts.add(to); // changed on both sides; or different without either being changed since: not guessed at
			// only changed here: this installation has the newer one
		}
	}

	/** the same content: the same size, and the same time (as far as a stick keeps it) or the same bytes */
	static boolean same(File a, File b) throws IOException {
		if (a.length() != b.length())
			return false;
		if (Math.abs(a.lastModified() - b.lastModified()) <= SLACK)
			return true;
		// alpha 54: block by block (byte by byte took long for a game of a gigabyte)
		try (InputStream x = Files.newInputStream(a.toPath()); InputStream y = Files.newInputStream(b.toPath())) {
			int size = (int)Math.min(1 << 20, a.length() + 1);
			byte[] bx = new byte[size], by = new byte[size];
			while (true) {
				int nx = x.readNBytes(bx, 0, bx.length), ny = y.readNBytes(by, 0, by.length);
				if ((nx != ny) || (Arrays.mismatch(bx, 0, nx, by, 0, ny) >= 0))
					return false;
				if (nx < bx.length)
					return true;
			}
		}
	}

	/**
	 * copies the files of the plan; a file that is replaced goes into the backup folder first, under its path from the root
	 * of its drive
	 * 
	 * @return the files that could not be copied
	 */
	public static List<File> copy(Plan plan, File backupDir) {
		List<File> failed = new ArrayList<>();
		for (FileAction a: plan.files()) {
			try {
				if (a.replaces() && a.to().isFile()) {
					File kept = new File(backupDir, backupName(a.to()));
					Files.createDirectories(kept.getParentFile().toPath());
					Files.copy(a.to().toPath(), kept.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				}
				Files.createDirectories(a.to().getAbsoluteFile().getParentFile().toPath());
				File part = new File(a.to().getPath() + ".dbglpart");
				try (InputStream in = Files.newInputStream(a.from().toPath()); OutputStream out = Files.newOutputStream(part.toPath())) {
					in.transferTo(out);
				}
				part.setLastModified(a.from().lastModified());
				Files.move(part.toPath(), a.to().toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException | RuntimeException e) {
				failed.add(a.to());
				new File(a.to().getPath() + ".dbglpart").delete();
			}
		}
		return failed;
	}

	/** "C:\Games\DOOM\SAVE.DSG" -> "C/Games/DOOM/SAVE.DSG" */
	public static String backupName(File file) {
		return StringUtils.stripStart(file.getAbsolutePath().replace(":", "").replace('\\', '/'), "/");
	}

	/** kept in this installation: what the stick said when its numbers were taken, so they are not added again */
	public static void remember(Plan plan, File dataDir, java.util.Collection<Integer> taken) throws IOException {
		File file = returnsFile(dataDir);
		Map<String, String> values = new LinkedHashMap<>();
		Properties before = load(file);
		before.stringPropertyNames().forEach(k -> values.put(k, before.getProperty(k)));
		for (Integer id: taken)
			if (plan.seen().containsKey(id))
				values.put(plan.stickId() + "." + id, plan.seen().get(id).line());
		store(file, values, "DBGL Extended Edition: what came back from which USB stick (stick.profile=starts|seconds|last run)");
	}

	private static File returnsFile(File dataDir) {
		return new File(new File(dataDir, "cache"), RETURNS);
	}
}
