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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.FileLocation;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.FileLocationService;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * alpha 53: games recognized by their own files, learned from profiles: the start program and the setup program with name and
 * MD5, and the title, developer, publisher, year and genre of the profile. Kept in config/known-games.json of the data of DBGL,
 * apart from the database that comes with DBGL (which stays as it is). Used by Profiles > Import known games in folder, after
 * that database. Only names, checksums and the details of the game are kept, no paths.
 */
public final class OwnKnownGames {

	public static final String FILE = "known-games.json";
	private static final int FORMAT = 1;
	/** what is read at most: the file is the user's own, but it may be damaged or very large */
	private static final int MAX_ENTRIES = 100000;
	private static final int MAX_TEXT = 1000;

	/** a file of a game: its name as on disk, its MD5 (32 small hex digits), and whether it is the setup program */
	public record KnownFile(String name, String md5, boolean setup) {
	}

	/** a game: its details and its files (the start program first) */
	public record Entry(String title, String developer, String publisher, String year, String genre, List<KnownFile> files) {
		public KnownFile main() {
			return files.stream().filter(f -> !f.setup()).findFirst().orElse(null);
		}

		public KnownFile setupFile() {
			return files.stream().filter(KnownFile::setup).findFirst().orElse(null);
		}

		/** a game is the same when its start program is: same name (DOS: any case) and same checksum */
		String key() {
			KnownFile m = main();
			return m == null ? "": m.name().toUpperCase(Locale.ROOT) + "|" + m.md5();
		}
	}

	private OwnKnownGames() {
	}

	/** the file in the data of DBGL (folder "config", as the configuration of ScummVM) */
	public static File file() {
		return file(FileLocationService.getInstance().getDataDir());
	}

	public static File file(File dataDir) {
		return new File(new File(dataDir, Scummvm.CONF_DIR), FILE);
	}

	/** all entries; none when there is no file yet, or one that cannot be read (it is then left as it is) */
	public static List<Entry> load(File f) {
		List<Entry> result = new ArrayList<>();
		if (f == null || !f.isFile())
			return result;
		try {
			JSONObject root = new JSONObject(Files.readString(f.toPath(), StandardCharsets.UTF_8));
			JSONArray games = root.optJSONArray("games");
			if (games == null)
				return result;
			for (int i = 0; i < games.length() && result.size() < MAX_ENTRIES; i++) {
				JSONObject g = games.optJSONObject(i);
				if (g == null)
					continue;
				String title = text(g, "title");
				JSONArray files = g.optJSONArray("files");
				if (title.isEmpty() || files == null)
					continue;
				List<KnownFile> known = new ArrayList<>();
				for (int j = 0; j < files.length() && known.size() < 10; j++) {
					JSONObject kf = files.optJSONObject(j);
					if (kf == null)
						continue;
					String name = text(kf, "name");
					String md5 = text(kf, "md5").toLowerCase(Locale.ROOT);
					if (validName(name) && md5.matches("[0-9a-f]{32}"))
						known.add(new KnownFile(name, md5, kf.optBoolean("setup", false)));
				}
				Entry e = new Entry(title, text(g, "developer"), text(g, "publisher"), text(g, "year"), text(g, "genre"), known);
				if (e.main() != null)
					result.add(e);
			}
		} catch (IOException | RuntimeException e) {
			// damaged: nothing learned is used, the file stays for a look by hand (learning writes a fresh one, the old one as .bak)
			return new ArrayList<>();
		}
		return result;
	}

	public static void save(File f, List<Entry> entries) throws IOException {
		JSONArray games = new JSONArray();
		for (Entry e: entries) {
			JSONObject g = new JSONObject();
			g.put("title", e.title());
			for (String[] kv: new String[][] {{"developer", e.developer()}, {"publisher", e.publisher()}, {"year", e.year()}, {"genre", e.genre()}})
				if (StringUtils.isNotBlank(kv[1]))
					g.put(kv[0], kv[1]);
			JSONArray files = new JSONArray();
			for (KnownFile kf: e.files()) {
				JSONObject o = new JSONObject();
				o.put("name", kf.name());
				o.put("md5", kf.md5());
				if (kf.setup())
					o.put("setup", true);
				files.put(o);
			}
			g.put("files", files);
			games.put(g);
		}
		JSONObject root = new JSONObject();
		root.put("format", FORMAT);
		root.put("about", "DBGL Extended Edition: games recognized by their start and setup programs, learned from profiles");
		root.put("games", games);
		Files.createDirectories(f.getParentFile().toPath());
		File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
		Files.writeString(tmp.toPath(), root.toString(1), StandardCharsets.UTF_8);
		Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * the entry for a profile: its title and details, its start program and (if any) its setup program, with checksums; null when
	 * that is not possible (no title, a game of ScummVM, a booter, Windows started, the program not there)
	 */
	public static Entry fromProfile(Profile p) {
		if (p == null || StringUtils.isBlank(p.getTitle()) || Scummvm.is(p.getDosboxVersion()))
			return null;
		String main = p.getConfiguration().getAutoexec().getMain();
		if (StringUtils.isBlank(main))
			return null;
		File mainFile = canonical(main);
		if (mainFile == null || !mainFile.isFile() || DuplicateProfiles.startsWindows(mainFile.getName()) || !validName(mainFile.getName()))
			return null;
		List<KnownFile> files = new ArrayList<>();
		try {
			files.add(new KnownFile(mainFile.getName(), md5(mainFile), false));
			if (p.hasSetup()) {
				File setup = canonical(p.getSetupString());
				if (setup != null && setup.isFile() && !setup.equals(mainFile) && validName(setup.getName()))
					files.add(new KnownFile(setup.getName(), md5(setup), true));
			}
		} catch (IOException | java.security.NoSuchAlgorithmException e) {
			return null;
		}
		return new Entry(cut(p.getTitle()), cut(p.getDeveloper()), cut(p.getPublisher()), cut(p.getYear()), cut(p.getGenre()), files);
	}

	/**
	 * a profile counts as recognized well enough to be learned by itself (when saved in its dialog): a title and at least one
	 * detail (developer, publisher or year), as a game looked up in a database or filled in by hand has
	 */
	public static boolean worthLearning(Profile p) {
		return p != null && StringUtils.isNotBlank(p.getTitle()) && StringUtils.isNotBlank(StringUtils.defaultString(p.getDeveloper()) + StringUtils.defaultString(p.getPublisher())
				+ StringUtils.defaultString(p.getYear()));
	}

	/** learns the profiles (an entry with the same start program is replaced); the number learned */
	public static int learn(File f, List<Profile> profiles) throws IOException {
		Map<String, Entry> all = new LinkedHashMap<>();
		boolean unreadable = f.isFile() && load(f).isEmpty() && f.length() > 0;
		for (Entry e: load(f))
			all.put(e.key(), e);
		int learned = 0;
		for (Profile p: profiles) {
			Entry e = fromProfile(p);
			if (e != null) {
				all.remove(e.key()); // put again at the end: the newest last
				all.put(e.key(), e);
				learned++;
			}
		}
		if (learned > 0) {
			if (unreadable) // a damaged file is kept as .bak, not overwritten without a trace
				Files.copy(f.toPath(), new File(f.getParentFile(), f.getName() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
			save(f, new ArrayList<>(all.values()));
		}
		return learned;
	}

	/** the names of all files known, as on disk (for the search of the folder) */
	public static Set<String> filenames(List<Entry> entries) {
		Set<String> names = new TreeSet<>();
		for (Entry e: entries)
			for (KnownFile kf: e.files()) {
				names.add(kf.name());
				names.add(kf.name().toUpperCase(Locale.ROOT));
				names.add(kf.name().toLowerCase(Locale.ROOT));
			}
		return names;
	}

	/** the entry whose start program is among these files of a folder (name, any case, and MD5 in small hex digits); else null */
	public static Entry match(List<Entry> entries, Map<String, String> md5ByName) {
		Map<String, String> upper = new LinkedHashMap<>();
		md5ByName.forEach((n, m) -> upper.put(n.toUpperCase(Locale.ROOT), m));
		Entry found = null;
		for (Entry e: entries) { // the last learned wins
			KnownFile m = e.main();
			if (m != null && m.md5().equals(upper.get(m.name().toUpperCase(Locale.ROOT))))
				found = e;
		}
		return found;
	}

	public static String md5(File f) throws IOException, java.security.NoSuchAlgorithmException {
		return HexFormat.of().formatHex(FilesUtils.md5(f));
	}

	private static File canonical(String path) {
		try {
			return new FileLocation(path, FileLocationService.getInstance().dosrootRelative()).getCanonicalFile();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static boolean validName(String name) {
		return StringUtils.isNotBlank(name) && name.length() <= 255 && !name.contains("/") && !name.contains("\\") && !name.equals("..") && !name.equals(".");
	}

	private static String text(JSONObject o, String key) {
		return cut(o.optString(key, ""));
	}

	private static String cut(String s) {
		s = StringUtils.defaultString(s).trim();
		return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT): s;
	}
}
