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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.service.FileLocationService;


/**
 * Alpha 49: how much room the game folder of a profile takes on the disk (the folder of drive C:, else the first folder
 * mounted), for the column "Disk space" and the statistics. Walking a folder takes time, so the sizes are found in the
 * background, kept in disksizes.properties in the data folder of DBGL, and found again only after a profile was changed
 * or run. Profiles that share a folder show the same size. The folder of another profile inside the folder is not counted,
 * and of the folder all games are in (dosroot) only the files that lie directly in it.
 */
public final class DiskUsage {

	/** the size of a game folder as last found; bytes -1: the profile has no game folder */
	public record Entry(long bytes, String path, long when) {
	}

	private static final String FILE = "disksizes.properties", FORMAT = "2";
	/**
	 * alpha 49: the folders the games are in ("games folders": a folder with the game folders of two or more profiles directly
	 * in it, e.g. D:\\SPIELE), each with its whole size; kept in the file as "root|<path>"
	 */
	private static final Map<String, Entry> ROOTS = new ConcurrentHashMap<>();
	private static final String ROOT_KEY = "root|";

	/** a games folder: its whole size, and how much of it is in no game folder of a profile */
	public record Root(String path, long bytes, long outside) {
	}
	private static final Map<Integer, Entry> CACHE = new ConcurrentHashMap<>();
	private static boolean loaded_;

	private DiskUsage() {
	}

	/** alpha 49: in the folder "cache" of the data folder (it is found again when missing), not among the data itself */
	private static File file() {
		return file(FileLocationService.getInstance().getDataDir());
	}

	/** the file in the folder "cache"; one written by an earlier version into the data folder itself is moved there first */
	static File file(File dataDir) {
		File now = new File(new File(dataDir, "cache"), FILE), before = new File(dataDir, FILE);
		try {
			if (before.isFile()) {
				Files.createDirectories(now.getParentFile().toPath());
				if (now.isFile())
					Files.delete(before.toPath()); // both there: the one in "cache" is the newer one
				else
					Files.move(before.toPath(), now.toPath());
			}
		} catch (IOException | RuntimeException e) {
			// stays where it is; the sizes are found again
		}
		return now;
	}

	private static synchronized void load() {
		if (loaded_)
			return;
		loaded_ = true;
		File f = file();
		if (!f.isFile())
			return;
		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(f.toPath())) {
			props.load(in);
		} catch (IOException | RuntimeException e) {
			return; // found again
		}
		if (!FORMAT.equals(props.getProperty("format")))
			return; // sizes found by test 11: a folder that held other games counted them too
		for (String key: props.stringPropertyNames()) {
			try {
				if (key.startsWith(ROOT_KEY)) {
					String[] parts = props.getProperty(key).split("\\|", 2);
					ROOTS.put(key.substring(ROOT_KEY.length()), new Entry(Long.parseLong(parts[0]), key.substring(ROOT_KEY.length()), Long.parseLong(parts[1])));
					continue;
				}
				String[] parts = props.getProperty(key).split("\\|", 3);
				CACHE.put(Integer.parseInt(key), new Entry(Long.parseLong(parts[0]), parts.length > 2 ? parts[2]: "", Long.parseLong(parts[1])));
			} catch (RuntimeException e) {
				// a line that is not ours
			}
		}
	}

	/** write what is known; called after sizes were found */
	public static synchronized void save() {
		load();
		Properties props = new Properties();
		props.setProperty("format", FORMAT);
		CACHE.forEach((id, e) -> props.setProperty(String.valueOf(id), e.bytes() + "|" + e.when() + "|" + e.path()));
		ROOTS.forEach((path, e) -> props.setProperty(ROOT_KEY + path, e.bytes() + "|" + e.when()));
		File target = file();
		target.getParentFile().mkdirs();
		try (OutputStream out = Files.newOutputStream(target.toPath())) {
			props.store(out, "DBGL Extended Edition: size of the game folder of each profile (bytes|found at|folder)");
		} catch (IOException | RuntimeException e) {
			// next time
		}
	}

	/** @return the size of the game folder of this profile as last found, null if not found yet (or it has no game folder) */
	public static Long known(Profile profile) {
		load();
		Entry e = profile == null ? null: CACHE.get(profile.getId());
		return (e == null) || (e.bytes() < 0) ? null: e.bytes();
	}

	/** the language DBGL is shown in (for the decimal sign) */
	public static Locale uiLocale() {
		org.dbgl.service.SettingsService settings = org.dbgl.service.SettingsService.getInstance();
		return Locale.of(settings.getValue("locale", "language"), settings.getValue("locale", "country"));
	}

	/** "Disk space: 1.2 GB" for the info of a profile, "" while the size is not known */
	public static String line(Profile profile, org.dbgl.service.ITextService text) {
		Long size = known(profile);
		return size == null ? "": text.get("dialog.main.diskspace", new Object[] {format(size, uiLocale())});
	}

	/** @return true if the folder of this profile was looked at (also when it has none) */
	public static boolean looked(Profile profile) {
		load();
		return (profile != null) && CACHE.containsKey(profile.getId());
	}

	/** the size is found again the next time: the profile was changed or run */
	public static void forget(int profileId) {
		load();
		CACHE.remove(profileId);
		ROOTS.clear(); // measured again: something in a games folder changed
	}

	/**
	 * alpha 49: the games folders: the folders that hold the game folders of two or more profiles directly (not one inside
	 * another one of them), e.g. D:\\SPIELE with a folder per game
	 */
	public static List<String> rootPaths(java.util.Collection<String> gameFolders) {
		Map<String, Set<String>> children = new HashMap<>();
		for (String path: gameFolders) {
			File parent = path.isEmpty() ? null: new File(path).getParentFile();
			if (parent != null)
				children.computeIfAbsent(parent.getPath(), k -> new HashSet<>()).add(path);
		}
		List<String> roots = new java.util.ArrayList<>();
		children.forEach((parent, kids) -> {
			if (kids.size() >= 2)
				roots.add(parent);
		});
		List<String> result = new java.util.ArrayList<>();
		for (String r: roots)
			if (roots.stream().noneMatch(o -> !o.equals(r) && r.startsWith(o.endsWith(File.separator) ? o: o + File.separator)))
				result.add(r);
		java.util.Collections.sort(result);
		return result;
	}

	private static Set<String> cachedGameFolders() {
		Set<String> result = new HashSet<>();
		CACHE.values().forEach(e -> {
			if (e.bytes() >= 0)
				result.add(e.path());
		});
		return result;
	}

	/** @return true if the size of every games folder is known (also when there is none) */
	public static boolean rootsKnown() {
		load();
		return rootPaths(cachedGameFolders()).stream().allMatch(ROOTS::containsKey);
	}

	/**
	 * alpha 49: the games folders with their whole size, and how much of each belongs to no profile: games without a profile,
	 * installers, what was left behind; the largest first. Empty while they are not measured.
	 */
	public static List<Root> roots() {
		load();
		List<Root> result = new java.util.ArrayList<>();
		Set<String> folders = cachedGameFolders();
		for (String path: rootPaths(folders)) {
			Entry root = ROOTS.get(path);
			if (root == null)
				return new java.util.ArrayList<>();
			String prefix = path.endsWith(File.separator) ? path: path + File.separator;
			long inside = 0;
			Set<String> counted = new HashSet<>();
			for (Entry e: CACHE.values())
				if ((e.bytes() >= 0) && (e.path().equals(path) || e.path().startsWith(prefix)) && counted.add(e.path()))
					inside += e.bytes();
			result.add(new Root(path, root.bytes(), Math.max(0, root.bytes() - inside)));
		}
		result.sort(java.util.Comparator.comparingLong(Root::bytes).reversed());
		return result;
	}

	/**
	 * find the size of the game folder of every profile that is not known yet; folders several profiles share are walked once
	 *
	 * @param cancelled asked between files; true ends it
	 * @return the number of profiles whose size was found now
	 */
	public static int find(List<Profile> profiles, BooleanSupplier cancelled) {
		load();
		// first the folder of each profile: a folder with the folders of other games in it does not count those
		String dosroot;
		try {
			dosroot = FileLocationService.getInstance().getDosroot().getCanonicalPath();
		} catch (IOException | RuntimeException e) {
			dosroot = null;
		}
		Map<Integer, String[]> mounted = new LinkedHashMap<>(); // the folder mounted, and the folder in it the game is started from
		for (Profile profile: profiles) {
			if (cancelled.getAsBoolean())
				break;
			Entry cached = CACHE.get(profile.getId());
			// alpha 54 (TEST15): a size not known (no folder found, e.g. a game of ScummVM before alpha 52) is looked for again a day later
			if ((cached != null) && ((cached.bytes() >= 0) || (System.currentTimeMillis() - cached.when() < 86_400_000L)))
				continue;
			String[] where = {"", ""};
			try {
				Profile copy = ProfileFactory.create(profile); // read apart from the profile the windows use
				copy.resetAndLoadConfiguration();
				File dir = SavegameBackup.gameDir(copy);
				File exeDir = copy.getConfiguration().getAutoexec().getCanonicalGameDir();
				if (Scummvm.is(copy.getDosboxVersion())) { // alpha 52: a game of ScummVM: its folder from scummvm.ini
					dir = Scummvm.gameDirOf(copy);
					exeDir = null;
				}
				if (dir != null) {
					where[0] = dir.getCanonicalPath();
					if ((exeDir != null) && exeDir.isDirectory())
						where[1] = top(where[0], exeDir.getCanonicalPath());
				} else if ((exeDir != null) && exeDir.isDirectory()) {
					where[0] = exeDir.getCanonicalPath();
				}
			} catch (IOException | RuntimeException e) {
				// no configuration to read: no size
			}
			mounted.put(profile.getId(), where);
		}
		Set<String> known = new HashSet<>();
		CACHE.values().forEach(e -> known.add(e.path()));
		Map<Integer, String> dirs = gameFolders(mounted, known, dosroot);
		Set<String> others = new HashSet<>(dirs.values());
		others.addAll(known);
		others.remove("");
		Map<String, Long> folders = new HashMap<>();
		int found = 0;
		for (Map.Entry<Integer, String> d: dirs.entrySet()) {
			String path = d.getValue();
			if (path.isEmpty()) {
				CACHE.put(d.getKey(), new Entry(-1, "", System.currentTimeMillis()));
			} else {
				Long size = folders.get(path);
				if (size == null) {
					size = folderSize(new File(path), path.equals(dosroot), others, cancelled);
					if (cancelled.getAsBoolean())
						break; // half a folder is no size
					folders.put(path, size);
				}
				CACHE.put(d.getKey(), new Entry(size, path, System.currentTimeMillis()));
			}
			found++;
		}
		// alpha 49: and the whole folder of the games, for the statistics (again when a size was found: something changed)
		List<String> wanted = rootPaths(cachedGameFolders());
		ROOTS.keySet().retainAll(wanted);
		for (String path: wanted) {
			if (cancelled.getAsBoolean())
				break;
			if (ROOTS.containsKey(path) && (found == 0))
				continue;
			long all = folderSize(new File(path), cancelled);
			if (!cancelled.getAsBoolean()) {
				ROOTS.put(path, new Entry(all, path, System.currentTimeMillis()));
				found++;
			}
		}
		if (found > 0)
			save();
		return found;
	}

	/** @return the name of the folder directly in dir that holds inner (inner is in dir), "" if inner is dir itself or not in it */
	public static String top(String dir, String inner) {
		String prefix = dir.endsWith(File.separator) ? dir: dir + File.separator;
		if (!inner.startsWith(prefix))
			return "";
		String rest = inner.substring(prefix.length());
		int sep = rest.indexOf(File.separatorChar);
		return sep < 0 ? rest: rest.substring(0, sep);
	}

	/**
	 * The game folder of each profile. Mostly that is the folder mounted as C:. But where one folder with many games is mounted
	 * (dosroot, or any folder several profiles mount and start their game from different folders in it), the game folder is the
	 * folder in it the game is started from: else each of these profiles would show the size of all games together.
	 *
	 * @param mounted per profile the folder mounted and the folder directly in it that holds the program ("" if none)
	 * @param known the game folders found before
	 * @return per profile the game folder, "" if it has none
	 */
	public static Map<Integer, String> gameFolders(Map<Integer, String[]> mounted, Set<String> known, String dosroot) {
		Map<String, Set<String>> tops = new HashMap<>();
		mounted.values().forEach(w -> tops.computeIfAbsent(w[0], k -> new HashSet<>()).add(w[1]));
		for (String k: known)
			for (String dir: tops.keySet())
				if (!dir.isEmpty() && !top(dir, k).isEmpty())
					tops.get(dir).add(top(dir, k));
		Map<Integer, String> result = new LinkedHashMap<>();
		mounted.forEach((id, w) -> {
			boolean shared = !w[0].isEmpty() && (w[0].equals(dosroot) || (tops.get(w[0]).size() > 1));
			result.put(id, shared && !w[1].isEmpty() ? w[0] + (w[0].endsWith(File.separator) ? "": File.separator) + w[1]: w[0]);
		});
		return result;
	}

	/** the sum of the sizes of all files in a folder and the folders in it; what cannot be read counts 0 */
	public static long folderSize(File dir, BooleanSupplier cancelled) {
		return folderSize(dir, false, Set.of(), cancelled);
	}

	/**
	 * the size of a game folder: its files and folders, without the folders in it that are the game folder of another profile
	 * (an add-on with a profile of its own, or games that lie in folders next to the files of this one)
	 *
	 * @param filesOnly the folder all games are in (dosroot): only the files that lie directly in it belong to the game
	 * @param others the game folders of all profiles (canonical paths)
	 */
	public static long folderSize(File dir, boolean filesOnly, Set<String> others, BooleanSupplier cancelled) {
		long[] sum = {0};
		Path start = dir.toPath();
		try {
			Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path sub, BasicFileAttributes attrs) {
					if (sub.equals(start))
						return FileVisitResult.CONTINUE;
					return filesOnly || others.contains(sub.toString()) ? FileVisitResult.SKIP_SUBTREE: FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (attrs.isRegularFile())
						sum[0] += attrs.size();
					return cancelled.getAsBoolean() ? FileVisitResult.TERMINATE: FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException e) {
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException | RuntimeException e) {
			// what was counted until here
		}
		return sum[0];
	}

	/** the room the game folders of these profiles take together (a shared folder once), and for how many it is known */
	public static long[] total(List<Profile> profiles) {
		load();
		Map<String, Long> folders = new HashMap<>();
		int known = 0;
		for (Profile p: profiles) {
			Entry e = CACHE.get(p.getId());
			if (e == null)
				continue;
			known++;
			if (e.bytes() >= 0)
				folders.put(e.path(), e.bytes());
		}
		return new long[] {folders.values().stream().mapToLong(Long::longValue).sum(), known};
	}

	/** "740 KB", "1.2 GB" (1 KB = 1024 bytes), with the decimal sign of the language */
	public static String format(long bytes, Locale locale) {
		String[] units = {"B", "KB", "MB", "GB", "TB"};
		double value = Math.max(0, bytes);
		int unit = 0;
		while ((value >= 1024) && (unit < units.length - 1)) {
			value /= 1024;
			unit++;
		}
		NumberFormat nf = NumberFormat.getNumberInstance(locale == null ? Locale.ROOT: locale);
		nf.setMaximumFractionDigits((unit >= 2) && (value < 100) ? 1: 0);
		nf.setMinimumFractionDigits(0);
		nf.setGroupingUsed(false);
		return nf.format(value) + " " + units[unit];
	}
}
