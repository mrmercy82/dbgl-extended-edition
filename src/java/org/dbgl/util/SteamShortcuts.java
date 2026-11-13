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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;


/**
 * Alpha 48: DBGL profiles as non-Steam games in Steam (library, Big Picture, Steam Deck). Steam keeps them per user in
 * {@code userdata/<id>/config/shortcuts.vdf}, a binary key-value file; the entries already in it are kept as they are (also the
 * fields this class does not know), an entry of the same name and program is updated instead of added twice. The pictures for the
 * library are {@code config/grid/<appid>p.<ext>} (the cover, upright). Steam must not run while the file is written: it writes
 * its own copy back when it exits.
 */
public final class SteamShortcuts {

	private static final byte MAP = 0x00, STRING = 0x01, INT = 0x02, END = 0x08;

	/** one game for Steam: its name, the program with its folder and options, and a picture (or null) */
	public record Game(String name, File exe, File startDir, String launchOptions, File cover) {
	}

	/** a user of Steam on this computer: the folder of its data and its name if known */
	public record User(File dir, String name) {
		@Override
		public String toString() {
			return (name == null || name.isBlank()) ? dir.getName(): name + " (" + dir.getName() + ")";
		}
	}

	private SteamShortcuts() {
	}

	/** @return the folder of Steam on this computer, or null */
	public static File steamDir() {
		List<File> candidates = new ArrayList<>();
		if (SystemUtils.IS_WINDOWS) {
			String reg = registrySteamPath();
			if (reg != null)
				candidates.add(new File(reg));
			candidates.add(new File(System.getenv().getOrDefault("ProgramFiles(x86)", "C:\\Program Files (x86)"), "Steam"));
			candidates.add(new File(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"), "Steam"));
		} else if (SystemUtils.IS_OSX) {
			candidates.add(new File(SystemUtils.USER_HOME, "Library/Application Support/Steam"));
		} else {
			candidates.add(new File(SystemUtils.USER_HOME, ".steam/steam"));
			candidates.add(new File(SystemUtils.USER_HOME, ".local/share/Steam"));
			candidates.add(new File(SystemUtils.USER_HOME, ".var/app/com.valvesoftware.Steam/.local/share/Steam"));
		}
		for (File dir: candidates)
			if (new File(dir, "userdata").isDirectory())
				return dir;
		return null;
	}

	private static String registrySteamPath() {
		try {
			Process p = new ProcessBuilder("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath").redirectErrorStream(true).start();
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			p.waitFor();
			for (String line: out.split("\\R")) {
				int i = line.indexOf("REG_SZ");
				if (line.contains("SteamPath") && i > 0)
					return line.substring(i + 6).strip().replace('/', '\\');
			}
		} catch (IOException e) {
			// no reg.exe: the usual folders are tried
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return null;
	}

	/** @return the users of Steam (folders in userdata with a config folder), the one who logged in last first */
	public static List<User> users(File steamDir) {
		List<User> result = new ArrayList<>();
		File[] dirs = new File(steamDir, "userdata").listFiles(f -> f.isDirectory() && f.getName().matches("\\d+") && !"0".equals(f.getName()));
		if (dirs == null)
			return result;
		java.util.Arrays.sort(dirs, (a, b) -> Long.compare(new File(b, "config").lastModified(), new File(a, "config").lastModified()));
		for (File dir: dirs)
			result.add(new User(dir, personaName(dir)));
		return result;
	}

	/** the name shown in Steam, from config/localconfig.vdf (text), or null */
	private static String personaName(File userDir) {
		File localConfig = new File(userDir, "config/localconfig.vdf");
		if (!localConfig.isFile() || localConfig.length() > 64L * 1024 * 1024)
			return null;
		try (java.io.BufferedReader r = Files.newBufferedReader(localConfig.toPath(), StandardCharsets.UTF_8)) {
			for (String line; (line = r.readLine()) != null;) {
				java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*\"PersonaName\"\\s+\"(.*)\"\\s*$").matcher(line);
				if (m.matches())
					return m.group(1);
			}
		} catch (IOException | java.io.UncheckedIOException e) {
			// no name then
		}
		return null;
	}

	/** @return whether Steam runs now (it would overwrite the file when it exits) */
	public static boolean steamRunning() {
		return ProcessHandle.allProcesses().anyMatch(p -> p.info().command().map(c -> {
			String name = new File(c).getName().toLowerCase(java.util.Locale.ROOT);
			return name.equals("steam.exe") || name.equals("steam") || name.equals("steamwebhelper.exe") || name.equals("steam_osx");
		}).orElse(false));
	}

	/** the number Steam knows a non-Steam game by (as Steam ROM Manager and others compute it), unsigned */
	public static long appId(String exeQuoted, String name) {
		CRC32 crc = new CRC32();
		crc.update((exeQuoted + name).getBytes(StandardCharsets.UTF_8));
		return (crc.getValue() | 0x80000000L) & 0xFFFFFFFFL;
	}

	/** what happened: how many games were added and updated */
	public record Result(int added, int updated) {
	}

	/** adds or updates the games for the user; a copy of the old file is kept as shortcuts.vdf.dbgl-backup */
	public static Result add(User user, List<Game> games, boolean pictures) throws IOException {
		File config = new File(user.dir(), "config");
		File vdf = new File(config, "shortcuts.vdf");
		Map<String, Object> root = vdf.isFile() ? read(Files.readAllBytes(vdf.toPath())): new LinkedHashMap<>();
		@SuppressWarnings("unchecked")
		Map<String, Object> shortcuts = (Map<String, Object>)root.computeIfAbsent(findKey(root, "shortcuts"), k -> new LinkedHashMap<String, Object>());

		int added = 0, updated = 0;
		for (Game game: games) {
			String exe = "\"" + game.exe().getPath() + "\"";
			long appId = appId(exe, game.name());
			Map<String, Object> entry = null;
			for (Object o: shortcuts.values())
				if (o instanceof Map<?, ?> m && game.name().equals(value(m, "AppName")) && (exe.equalsIgnoreCase(String.valueOf(value(m, "Exe"))) || madeByDbgl(m))) {
					@SuppressWarnings("unchecked")
					Map<String, Object> e = (Map<String, Object>)m;
					entry = e;
				}
			if (entry == null) {
				entry = new LinkedHashMap<>();
				entry.put("appid", (int)appId);
				entry.put("AppName", game.name());
				entry.put("Exe", exe);
				entry.put("StartDir", "\"" + game.startDir().getPath() + "\"");
				entry.put("icon", "");
				entry.put("ShortcutPath", "");
				entry.put("LaunchOptions", game.launchOptions());
				entry.put("IsHidden", 0);
				entry.put("AllowDesktopConfig", 1);
				entry.put("AllowOverlay", 1);
				entry.put("OpenVR", 0);
				entry.put("Devkit", 0);
				entry.put("DevkitGameID", "");
				entry.put("DevkitOverrideAppID", 0);
				entry.put("LastPlayTime", 0);
				entry.put("FlatpakAppID", "");
				Map<String, Object> tags = new LinkedHashMap<>();
				tags.put("0", "DBGL");
				entry.put("tags", tags);
				int index = 0;
				while (shortcuts.containsKey(String.valueOf(index)))
					index++;
				shortcuts.put(String.valueOf(index), entry);
				added++;
			} else {
				put(entry, "Exe", exe);
				put(entry, "StartDir", "\"" + game.startDir().getPath() + "\"");
				put(entry, "LaunchOptions", game.launchOptions());
				updated++;
			}
			if (pictures && game.cover() != null && game.cover().isFile()) {
				Object id = value(entry, "appid");
				long gridId = (id instanceof Integer i) ? (i & 0xFFFFFFFFL): appId;
				String ext = org.apache.commons.io.FilenameUtils.getExtension(game.cover().getName()).toLowerCase(java.util.Locale.ROOT);
				File grid = new File(config, "grid");
				Files.createDirectories(grid.toPath());
				Files.copy(game.cover().toPath(), new File(grid, gridId + "p." + ext).toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}

		Files.createDirectories(config.toPath());
		if (vdf.isFile())
			Files.copy(vdf.toPath(), new File(config, "shortcuts.vdf.dbgl-backup").toPath(), StandardCopyOption.REPLACE_EXISTING);
		File tmp = new File(config, "shortcuts.vdf.dbgl-new");
		Files.write(tmp.toPath(), write(root));
		Files.move(tmp.toPath(), vdf.toPath(), StandardCopyOption.REPLACE_EXISTING);
		return new Result(added, updated);
	}

	/** an entry DBGL made (its tag "DBGL"): updated also when it starts another program now (DOSBox or DBGL) */
	private static boolean madeByDbgl(Map<?, ?> entry) {
		return (value(entry, "tags") instanceof Map<?, ?> tags) && tags.values().contains("DBGL");
	}

	/** the key as it is in the map (Steam writes "shortcuts", case may differ), or the name itself */
	private static String findKey(Map<String, Object> map, String name) {
		return map.keySet().stream().filter(k -> k.equalsIgnoreCase(name)).findFirst().orElse(name);
	}

	private static Object value(Map<?, ?> map, String name) {
		for (Map.Entry<?, ?> e: map.entrySet())
			if (String.valueOf(e.getKey()).equalsIgnoreCase(name))
				return e.getValue();
		return null;
	}

	private static void put(Map<String, Object> map, String name, Object value) {
		map.put(findKey(map, name), value);
	}

	/** reads the binary format: maps of strings, 32 bit numbers and maps; other types are not in shortcuts.vdf */
	public static Map<String, Object> read(byte[] data) throws IOException {
		int[] pos = {0};
		Map<String, Object> root = readMap(data, pos, 0);
		return root;
	}

	private static Map<String, Object> readMap(byte[] d, int[] pos, int depth) throws IOException {
		if (depth > 32)
			throw new IOException("shortcuts.vdf: nested too deeply");
		Map<String, Object> map = new LinkedHashMap<>();
		while (pos[0] < d.length) {
			byte type = d[pos[0]++];
			if (type == END)
				return map;
			String key = readString(d, pos);
			switch (type) {
				case MAP -> map.put(key, readMap(d, pos, depth + 1));
				case STRING -> map.put(key, readString(d, pos));
				case INT -> {
					if (pos[0] + 4 > d.length)
						throw new IOException("shortcuts.vdf: cut off");
					int v = (d[pos[0]] & 0xFF) | ((d[pos[0] + 1] & 0xFF) << 8) | ((d[pos[0] + 2] & 0xFF) << 16) | ((d[pos[0] + 3] & 0xFF) << 24);
					pos[0] += 4;
					map.put(key, v);
				}
				default -> throw new IOException("shortcuts.vdf: unknown type " + type + " at " + (pos[0] - 1));
			}
		}
		if (depth == 0)
			return map; // the outer map may end without its END byte
		throw new IOException("shortcuts.vdf: cut off");
	}

	private static String readString(byte[] d, int[] pos) throws IOException {
		int start = pos[0];
		while (pos[0] < d.length && d[pos[0]] != 0)
			pos[0]++;
		if (pos[0] >= d.length)
			throw new IOException("shortcuts.vdf: cut off");
		String s = new String(d, start, pos[0] - start, StandardCharsets.UTF_8);
		pos[0]++;
		return s;
	}

	/** writes the binary format */
	public static byte[] write(Map<String, Object> root) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeMapBody(out, root);
		out.write(END);
		return out.toByteArray();
	}

	private static void writeMapBody(ByteArrayOutputStream out, Map<String, Object> map) {
		for (Map.Entry<String, Object> e: map.entrySet()) {
			Object v = e.getValue();
			if (v instanceof Map<?, ?> m) {
				out.write(MAP);
				writeString(out, e.getKey());
				@SuppressWarnings("unchecked")
				Map<String, Object> mm = (Map<String, Object>)m;
				writeMapBody(out, mm);
				out.write(END);
			} else if (v instanceof Integer i) {
				out.write(INT);
				writeString(out, e.getKey());
				out.write(i & 0xFF);
				out.write((i >> 8) & 0xFF);
				out.write((i >> 16) & 0xFF);
				out.write((i >> 24) & 0xFF);
			} else {
				out.write(STRING);
				writeString(out, e.getKey());
				writeString(out, String.valueOf(v));
			}
		}
	}

	private static void writeString(ByteArrayOutputStream out, String s) {
		byte[] b = s.getBytes(StandardCharsets.UTF_8);
		out.write(b, 0, b.length);
		out.write(0);
	}
}
