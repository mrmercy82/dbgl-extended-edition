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
		if (f == null || !f.isFile())
			return new ArrayList<>();
		try {
			return parse(Files.readString(f.toPath(), StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			return new ArrayList<>();
		}
	}

	/** alpha 54: the entries of a text in the format of this file (own, from someone else, or the list of the community) */
	public static List<Entry> parse(String json) {
		List<Entry> result = new ArrayList<>();
		try {
			JSONObject root = new JSONObject(json);
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
		} catch (RuntimeException e) {
			// damaged: nothing learned is used, the file stays for a look by hand (learning writes a fresh one, the old one as .bak)
			return new ArrayList<>();
		}
		return result;
	}

	/** alpha 54: the list of the community as last downloaded, next to the own file (config/known-games-community.json) */
	public static final String COMMUNITY_FILE = "known-games-community.json";
	/** alpha 54: where the list of the community is kept: in the repository of the Extended Edition, added to by its users */
	public static final String COMMUNITY_URL = org.dbgl.constants.Constants.EDITION_URL.replace("https://github.com/", "https://raw.githubusercontent.com/")
			+ "/main/community/known-games.json";
	private static final int MAX_DOWNLOAD = 20 * 1024 * 1024;

	public static File communityFile() {
		return new File(file().getParentFile(), COMMUNITY_FILE);
	}

	/**
	 * alpha 54: what the import from a folder knows: the games of the community first, then the own ones, so that an own one wins
	 * (the last one that fits is taken)
	 */
	public static List<Entry> withCommunity(List<Entry> own, List<Entry> community) {
		List<Entry> all = new ArrayList<>(community);
		all.addAll(own);
		return all;
	}

	/**
	 * alpha 54: the entries of another list taken over into this one, those whose start program (name and checksum) is not there
	 * yet; the ones there stay as they are. The number taken over
	 */
	public static int merge(List<Entry> into, List<Entry> more) {
		Set<String> keys = new java.util.HashSet<>();
		for (Entry e: into)
			keys.add(e.key());
		int added = 0;
		for (Entry e: more)
			if (into.size() < MAX_ENTRIES && keys.add(e.key())) {
				into.add(e);
				added++;
			}
		return added;
	}

	/**
	 * alpha 54: the list of the community from GitHub, checked as an own file is (at most 20 MB, 100000 games, only names and
	 * checksums), saved to the file given; the number of games in it
	 */
	public static int downloadCommunity(File target) throws IOException, InterruptedException {
		java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().proxy(java.net.ProxySelector.getDefault()).connectTimeout(java.time.Duration.ofSeconds(10))
				.followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
		java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(COMMUNITY_URL)).timeout(java.time.Duration.ofSeconds(30))
				.header("User-Agent", "DBGL").GET().build();
		java.net.http.HttpResponse<java.io.InputStream> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
		byte[] body;
		try (java.io.InputStream in = response.body()) {
			if (response.statusCode() != 200)
				throw new IOException(EditionUpdateChecker.httpProblem(response.statusCode()));
			body = in.readNBytes(MAX_DOWNLOAD + 1);
		}
		if (body.length > MAX_DOWNLOAD)
			throw new IOException("more than 20 MB");
		List<Entry> entries = parse(new String(body, StandardCharsets.UTF_8));
		if (entries.isEmpty() && !new String(body, StandardCharsets.UTF_8).contains("\"games\""))
			throw new IOException("not a list of known games");
		save(target, entries);
		return entries.size();
	}

	public static void save(File f, List<Entry> entries) throws IOException {
		if (f.getParentFile() == null)
			f = f.getAbsoluteFile();
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

	/** TEST29: all entries whose start program is among these files (name in any case, same checksum), the last learned first */
	public static List<Entry> matchAll(List<Entry> entries, Map<String, String> md5ByName) {
		Map<String, String> upper = new LinkedHashMap<>();
		md5ByName.forEach((n, m) -> upper.put(n.toUpperCase(Locale.ROOT), m));
		List<Entry> found = new ArrayList<>();
		for (Entry e: entries) {
			KnownFile m = e.main();
			if (m != null && m.md5().equals(upper.get(m.name().toUpperCase(Locale.ROOT))))
				found.add(0, e);
		}
		return found;
	}

	/**
	 * alpha 53 (TEST24): the start program there by name but with another checksum (changed by the game, e.g. its settings kept
	 * in it, or patched), and the setup program there with the same name and checksum: most likely the same game. Else null
	 */
	public static Entry matchBySetup(List<Entry> entries, Map<String, String> md5ByName) {
		Map<String, String> upper = new LinkedHashMap<>();
		md5ByName.forEach((n, m) -> upper.put(n.toUpperCase(Locale.ROOT), m));
		Entry found = null;
		for (Entry e: entries) {
			KnownFile m = e.main(), s = e.setupFile();
			if ((m != null) && (s != null) && upper.containsKey(m.name().toUpperCase(Locale.ROOT)) && s.md5().equals(upper.get(s.name().toUpperCase(Locale.ROOT))))
				found = e;
		}
		return found;
	}

	/** alpha 53 (TEST24): what became of the learned games in a search: recognized, start program there but changed, not there */
	public record Report(int learned, List<String> recognized, List<String> changed, List<String> missing) {
	}

	/**
	 * @param md5sByName all files found in the search: name (any case) to the checksums with that name
	 * @param recognizedTitles the titles of the learned games the search recognized
	 */
	public static Report report(List<Entry> entries, Map<String, Set<String>> md5sByName, Set<String> recognizedTitles) {
		Map<String, Set<String>> upper = new LinkedHashMap<>();
		md5sByName.forEach((n, m) -> upper.computeIfAbsent(n.toUpperCase(Locale.ROOT), k -> new TreeSet<>()).addAll(m));
		List<String> recognized = new ArrayList<>(), changed = new ArrayList<>(), missing = new ArrayList<>();
		Set<String> seen = new java.util.HashSet<>();
		for (Entry e: entries) {
			if (!seen.add(e.title() + "|" + e.key()))
				continue;
			KnownFile m = e.main();
			if (recognizedTitles.contains(e.title()))
				recognized.add(e.title());
			else if (upper.containsKey(m.name().toUpperCase(Locale.ROOT)) && !upper.get(m.name().toUpperCase(Locale.ROOT)).contains(m.md5()))
				changed.add(e.title() + " (" + m.name() + ")");
			else if (upper.containsKey(m.name().toUpperCase(Locale.ROOT)))
				recognized.add(e.title()); // TEST29: there with its checksum (taken under another title, or in a folder told otherwise)
			else
				missing.add(e.title() + " (" + m.name() + ")");
		}
		return new Report(entries.size(), recognized, changed, missing);
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
