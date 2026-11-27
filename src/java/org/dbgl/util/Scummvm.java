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
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;


/**
 * alpha 52: ScummVM next to DOSBox. ScummVM is entered on the tab "DOSBox versions" as a version of the family "ScummVM"
 * (its configuration there is a file of DBGL with no settings, DBGL never writes the one of ScummVM). A profile of such a
 * version is a game of ScummVM: its configuration has a section [scummvm] with the target, the name ScummVM knows the game
 * by in its scummvm.ini, and that file; it starts as {@code scummvm --config=<file> <target>}. The games are imported from
 * scummvm.ini, which is only read.
 */
public final class Scummvm {

	public static final String FAMILY = "ScummVM";
	public static final String SECTION = "scummvm";
	/** the configuration of the ScummVM version, in the data folder of DBGL: no settings, only a comment */
	public static final String VERSION_CONF = "scummvm-dbgl.conf";
	/** [gui] scummvm: the import of ScummVM games in the menu and the search for new ScummVM versions (its games start anyway) */
	public static final String SETTING = "scummvm";

	/** a game in scummvm.ini: its target (the name of its section), what ScummVM shows, its folder, ... */
	public record Game(String target, String title, String description, String gameId, File path, String platform, String language) {
	}

	private Scummvm() {
	}

	public static boolean is(DosboxVersion v) {
		return (v != null) && FAMILY.equalsIgnoreCase(StringUtils.trimToEmpty(v.getFamily()));
	}

	public static boolean isExecutable(File f) {
		return (f != null) && f.getName().toLowerCase(Locale.ROOT).startsWith("scummvm");
	}

	/** where ScummVM keeps its scummvm.ini when it is installed (not portable) */
	public static File defaultIni() {
		String home = System.getProperty("user.home");
		if (SystemUtils.IS_WINDOWS) {
			String appData = System.getenv("APPDATA");
			return appData == null ? null: new File(appData, "ScummVM" + File.separator + "scummvm.ini");
		}
		if (SystemUtils.IS_OSX)
			return new File(home, "Library/Preferences/ScummVM Preferences");
		String xdg = System.getenv("XDG_CONFIG_HOME");
		File modern = new File(StringUtils.isNotBlank(xdg) ? new File(xdg): new File(home, ".config"), "scummvm/scummvm.ini");
		File old = new File(home, ".scummvmrc");
		return (!modern.isFile() && old.isFile()) ? old: modern;
	}

	/** the places ScummVM is usually installed to */
	public static List<File> usualExecutables() {
		List<File> result = new ArrayList<>();
		if (SystemUtils.IS_WINDOWS) {
			for (String env: new String[] {"ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"}) {
				String dir = System.getenv(env);
				if (dir != null)
					result.add(new File(dir, "ScummVM" + File.separator + "scummvm.exe"));
			}
		} else if (SystemUtils.IS_OSX) {
			result.add(new File("/Applications/ScummVM.app/Contents/MacOS/scummvm"));
			result.add(new File(System.getProperty("user.home"), "Applications/ScummVM.app/Contents/MacOS/scummvm"));
		} else {
			for (String dir: new String[] {"/usr/bin", "/usr/games", "/usr/local/bin", "/usr/local/games", "/snap/bin"})
				result.add(new File(dir, "scummvm"));
		}
		return result;
	}

	public static File findExecutable() {
		for (File f: usualExecutables())
			if (f.isFile())
				return f;
		return null;
	}

	/** the games in a scummvm.ini (not the section [scummvm] of ScummVM itself), in their order there */
	public static List<Game> readIni(File ini) throws IOException {
		String text = decode(Files.readAllBytes(ini.toPath()));
		Map<String, Map<String, String>> sections = new LinkedHashMap<>();
		Map<String, String> current = null;
		for (String line: text.split("\\r?\\n")) {
			String t = line.trim();
			if (t.isEmpty() || t.startsWith("#") || t.startsWith(";"))
				continue;
			if (t.startsWith("[") && t.endsWith("]")) {
				String name = t.substring(1, t.length() - 1).trim();
				current = sections.computeIfAbsent(name, k -> new LinkedHashMap<>());
			} else if ((current != null) && t.contains("=")) {
				current.put(StringUtils.substringBefore(t, "=").trim().toLowerCase(Locale.ROOT), StringUtils.substringAfter(t, "=").trim());
			}
		}
		File base = ini.getAbsoluteFile().getParentFile();
		List<Game> games = new ArrayList<>();
		for (Map.Entry<String, Map<String, String>> s: sections.entrySet()) {
			Map<String, String> v = s.getValue();
			String target = s.getKey();
			if (target.equalsIgnoreCase("scummvm") || target.equalsIgnoreCase("keymapper") || target.startsWith("cloud") || !validTarget(target)
					|| (StringUtils.isBlank(v.get("gameid")) && StringUtils.isBlank(v.get("engineid"))))
				continue;
			String description = StringUtils.defaultIfBlank(v.get("description"), target);
			File path = StringUtils.isBlank(v.get("path")) ? null: new File(v.get("path"));
			if ((path != null) && !path.isAbsolute())
				path = new File(base, v.get("path"));
			games.add(new Game(target, titleOf(description), description, StringUtils.defaultIfBlank(v.get("gameid"), v.get("engineid")), path, StringUtils.defaultString(v.get("platform")),
					StringUtils.defaultString(v.get("language"))));
		}
		return games;
	}

	/**
	 * whether ScummVM goes back to its list of games when a game is left, instead of closing ("Always return to the launcher
	 * when leaving a game", in its section [scummvm]); then DBGL counts the play time until ScummVM is closed
	 */
	public static boolean returnsToLauncher(File ini) {
		try {
			boolean in = false;
			for (String line: decode(Files.readAllBytes(ini.toPath())).split("\\r?\\n")) {
				String t = line.trim();
				if (t.startsWith("[") && t.endsWith("]"))
					in = t.equalsIgnoreCase("[scummvm]");
				else if (in && t.toLowerCase(Locale.ROOT).startsWith("gui_return_to_launcher_at_exit="))
					return t.substring(t.indexOf('=') + 1).trim().equalsIgnoreCase("true");
			}
		} catch (IOException e) {
			// then it is not known: no hint
		}
		return false;
	}

	/**
	 * alpha 52: the version of ScummVM that is installed, as it writes it into its scummvm.ini at every start (versioninfo in
	 * [scummvm]); the file next to the program first (portable), then the one of the user. Null if not known. Not started for it.
	 */
	public static String installedVersion(DosboxVersion v) {
		return installedVersion(v.getCanonicalExecutable());
	}

	/** the same for the program at this place (in the window of the ScummVM version, before it is saved) */
	public static String installedVersion(File executable) {
		File[] inis = {new File(executable.getAbsoluteFile().getParentFile(), "scummvm.ini"), defaultIni()};
		for (File ini: inis) {
			if ((ini == null) || !ini.isFile())
				continue;
			try {
				boolean in = false;
				for (String line: decode(Files.readAllBytes(ini.toPath())).split("\\r?\\n")) {
					String t = line.trim();
					if (t.startsWith("[") && t.endsWith("]"))
						in = t.equalsIgnoreCase("[scummvm]");
					else if (in && t.toLowerCase(Locale.ROOT).startsWith("versioninfo=")) {
						java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(t.substring(12).trim());
						return m.find() ? m.group(1): null;
					}
				}
			} catch (IOException e) {
				// the next one
			}
		}
		return null;
	}

	/** a value of the section [scummvm] of ScummVM itself in its scummvm.ini (next to the program, else the one of the user) */
	static String globalValue(File executable, String key) {
		File[] inis = {executable == null ? null: new File(executable.getAbsoluteFile().getParentFile(), "scummvm.ini"), defaultIni()};
		for (File ini: inis) {
			if ((ini == null) || !ini.isFile())
				continue;
			try {
				String v = sectionValue(decode(Files.readAllBytes(ini.toPath())), "scummvm", key);
				return v; // the file ScummVM uses: its value, or none
			} catch (IOException e) {
				// the next one
			}
		}
		return null;
	}

	static String sectionValue(String text, String section, String key) {
		boolean in = false;
		String prefix = key.toLowerCase(Locale.ROOT) + "=";
		for (String line: text.split("\\r?\\n")) {
			String t = line.trim();
			if (t.startsWith("[") && t.endsWith("]"))
				in = t.equalsIgnoreCase("[" + section + "]");
			else if (in && t.toLowerCase(Locale.ROOT).startsWith(prefix))
				return StringUtils.trimToNull(t.substring(prefix.length()));
		}
		return null;
	}

	/**
	 * alpha 52: where ScummVM saves its screenshots (Alt+S): screenshotpath in its scummvm.ini, else its default: Windows
	 * Pictures\ScummVM Screenshots, macOS the desktop, Linux the XDG folder of pictures\ScummVM Screenshots
	 */
	public static File screenshotDir(File executable) {
		String set = globalValue(executable, "screenshotpath");
		if (StringUtils.isNotBlank(set))
			return new File(set);
		String home = System.getProperty("user.home");
		if (SystemUtils.IS_WINDOWS) {
			String profile = StringUtils.defaultIfBlank(System.getenv("USERPROFILE"), home);
			return new File(profile, "Pictures" + File.separator + "ScummVM Screenshots");
		}
		if (SystemUtils.IS_OSX)
			return new File(home, "Desktop");
		File pictures = new File(home, "Pictures");
		File dirs = new File(home, ".config/user-dirs.dirs");
		if (dirs.isFile()) {
			try {
				for (String line: Files.readAllLines(dirs.toPath())) {
					String t = line.trim();
					if (t.startsWith("XDG_PICTURES_DIR=")) {
						String v = t.substring(17).replace("\"", "").replace("$HOME", home).trim();
						if (!v.isEmpty())
							pictures = new File(v);
					}
				}
			} catch (IOException | RuntimeException e) {
				// ~/Pictures then
			}
		}
		return new File(pictures, "ScummVM Screenshots");
	}

	/**
	 * alpha 52: copies the screenshots of the game made while it ran into the folder of the profile (ScummVM's stay where they
	 * are): pictures in the folder of ScummVM made since the start (only this game ran then) that are named after ScummVM or the game; one of the same
	 * name already there is left alone.
	 *
	 * @return how many were copied
	 */
	/** as ScummVM names them: scummvm-<target>-00000.png */
	private static final java.util.regex.Pattern SHOT_NAME = java.util.regex.Pattern.compile("scummvm-(.+)-\\d+\\.[a-z]+");

	public static int copyScreenshots(File from, String target, long sinceMillis, File to) {
		File[] files = from == null ? null: from.listFiles();
		if (files == null)
			return 0;
		int copied = 0;
		String t = target == null ? "": target.toLowerCase(Locale.ROOT);
		for (File f: files) {
			String n = f.getName().toLowerCase(Locale.ROOT);
			if (!f.isFile() || !n.matches(".*\\.(png|bmp|jpg|jpeg)$") || (f.lastModified() < sinceMillis - 2000))
				continue;
			if (!n.startsWith("scummvm") && (t.isEmpty() || !n.contains(t)))
				continue; // not of ScummVM (on the desktop of a Mac there may be anything)
			java.util.regex.Matcher m = SHOT_NAME.matcher(n);
			if (!t.isEmpty() && m.matches() && !m.group(1).equals(t))
				continue; // of another game, started from the list of games of ScummVM while it ran
			File dest = new File(to, f.getName());
			if (dest.exists())
				continue;
			try {
				to.mkdirs();
				Files.copy(f.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
				copied++;
			} catch (IOException e) {
				// the next one
			}
		}
		return copied;
	}

	/** UTF-8 if it is that, else Latin-1 */
	static String decode(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		} catch (java.nio.charset.CharacterCodingException e) {
			return new String(bytes, StandardCharsets.ISO_8859_1);
		}
	}

	/** a target is given to ScummVM on the command line: letters, digits and a few signs only */
	static boolean validTarget(String target) {
		return target.matches("[A-Za-z0-9][A-Za-z0-9._:+-]{0,63}");
	}

	/** the title without what ScummVM adds in brackets at the end ("Monkey Island 2: LeChuck's Revenge (DOS/English)") */
	static String titleOf(String description) {
		String t = description.trim().replaceFirst("\\s*\\([^()]*/[^()]*\\)$", "").trim();
		return t.isEmpty() ? description.trim(): t;
	}

	/** the configuration of a profile for a game of ScummVM */
	public static String confOf(Game game, File ini) {
		return "[" + SECTION + "]\ntarget=" + game.target() + "\nconfig=" + ini.getAbsolutePath() + "\n";
	}

	/** alpha 52: the folder of the game of a profile of ScummVM (its path in scummvm.ini), or null if it is not known or not there */
	public static File gameDirOf(Profile p) {
		String target = targetOf(p);
		File config = configFile(p);
		if ((target == null) || (config == null))
			return null;
		try {
			for (Game g: readIni(config))
				if (target.equals(g.target()))
					return (g.path() != null) && g.path().isDirectory() ? g.path(): null;
		} catch (IOException | RuntimeException e) {
			// not known then
		}
		return null;
	}

	/**
	 * alpha 54 (TEST15): the scummvm.ini of a profile; a relative path is one in the data of DBGL (as on a USB stick, where it is
	 * next to the ScummVM that went along); null if it has none
	 */
	public static File configFile(Profile p) {
		String config = valueOf(p, "config");
		if (StringUtils.isBlank(config))
			return null;
		File f = new File(config.trim());
		return f.isAbsolute() ? f: new File(org.dbgl.service.FileLocationService.getInstance().getDataDir(), config.trim());
	}

	/** alpha 54 (TEST15): where ScummVM keeps the saved games of a target: its savepath, the one of all, else the usual folder */
	public static File saveDir(File ini, String target) {
		String own = null, all = null;
		try {
			Map<String, Map<String, String>> sections = sections(decode(Files.readAllBytes(ini.toPath())));
			own = sections.getOrDefault(target, Map.of()).get("savepath");
			all = sections.getOrDefault("scummvm", Map.of()).get("savepath");
		} catch (IOException | RuntimeException e) {
			// the usual one
		}
		if (StringUtils.isNotBlank(own))
			return new File(own.trim());
		if (StringUtils.isNotBlank(all))
			return new File(all.trim());
		String home = System.getProperty("user.home");
		if (SystemUtils.IS_WINDOWS)
			return new File(System.getenv().getOrDefault("APPDATA", home), "ScummVM" + File.separator + "Saved games");
		if (SystemUtils.IS_OSX)
			return new File(home, "Documents/ScummVM Savegames");
		String xdg = System.getenv("XDG_DATA_HOME");
		return new File(StringUtils.isNotBlank(xdg) ? xdg: home + "/.local/share", "scummvm/saves");
	}

	/** the sections of a scummvm.ini with their keys (in lower case) and values, in their order */
	static Map<String, Map<String, String>> sections(String ini) {
		Map<String, Map<String, String>> result = new java.util.LinkedHashMap<>();
		Map<String, String> current = null;
		for (String line: ini.split("\\r?\\n")) {
			String t = line.trim();
			if (t.startsWith("[") && t.endsWith("]")) {
				current = result.computeIfAbsent(t.substring(1, t.length() - 1).trim(), k -> new java.util.LinkedHashMap<>());
			} else if ((current != null) && t.contains("=") && !t.startsWith("#") && !t.startsWith(";")) {
				int eq = t.indexOf('=');
				current.put(t.substring(0, eq).trim().toLowerCase(Locale.ROOT), t.substring(eq + 1).trim());
			}
		}
		return result;
	}

	/**
	 * alpha 54 (TEST15): the scummvm.ini for a USB stick: the settings of all games ([scummvm]) without paths of this computer, the
	 * saved games in the folder "saves" next to it, and the games that go along with their folder on the stick (relative to the
	 * folder ScummVM starts in there)
	 */
	public static String stickIni(String hostIni, Map<String, String> targetPaths) {
		Map<String, Map<String, String>> sections = sections(hostIni);
		StringBuilder sb = new StringBuilder();
		sb.append("[scummvm]\r\n");
		for (Map.Entry<String, String> e: sections.getOrDefault("scummvm", Map.of()).entrySet())
			if (!e.getKey().equals("savepath") && !isHostPath(e.getValue()))
				sb.append(e.getKey()).append('=').append(e.getValue()).append("\r\n");
		sb.append("savepath=saves\r\n");
		for (Map.Entry<String, String> t: targetPaths.entrySet()) {
			sb.append("\r\n[").append(t.getKey()).append("]\r\n");
			for (Map.Entry<String, String> e: sections.getOrDefault(t.getKey(), Map.of()).entrySet())
				if (!e.getKey().equals("path") && !e.getKey().equals("savepath") && !isHostPath(e.getValue()))
					sb.append(e.getKey()).append('=').append(e.getValue()).append("\r\n");
			sb.append("path=").append(t.getValue()).append("\r\n");
		}
		return sb.toString();
	}

	/** a path of this computer (C:\\..., \\\\server\\..., /...): not taken to another one */
	static boolean isHostPath(String value) {
		String v = value == null ? "": value.trim();
		return v.matches("(?i)[a-z]:[\\\\/].*") || v.startsWith("\\\\") || v.startsWith("/") || v.startsWith("~");
	}

	/** the target of a profile of ScummVM, or null */
	public static String targetOf(Profile p) {
		String t = valueOf(p, "target");
		return StringUtils.isBlank(t) || !validTarget(t.trim()) ? null: t.trim();
	}

	/** a value of the section [scummvm] of the profile: from its configuration, or from its file when that is not loaded */
	static String valueOf(Profile p, String key) {
		String v = null;
		try {
			v = p.getConfiguration().getValue(SECTION, key);
		} catch (RuntimeException e) {
			// not loaded
		}
		if (StringUtils.isNotBlank(v))
			return v;
		try {
			File f = p.getConfigurationCanonicalFile();
			return f.isFile() ? valueInConf(decode(Files.readAllBytes(f.toPath())), key): null;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** a value of the section [scummvm] in the text of the configuration file of a profile; null if it has none */
	public static String valueInConf(String conf, String key) {
		boolean in = false;
		String prefix = key.toLowerCase(Locale.ROOT) + "=";
		for (String line: conf.split("\\r?\\n")) {
			String t = line.trim();
			if (t.startsWith("[") && t.endsWith("]"))
				in = t.equalsIgnoreCase("[" + SECTION + "]");
			else if (in && t.toLowerCase(Locale.ROOT).startsWith(prefix))
				return StringUtils.trimToNull(t.substring(prefix.length()));
		}
		return null;
	}

	/** what ScummVM is started with for the profile, after its executable: the file scummvm.ini, the parameters, the target */
	public static List<String> arguments(Profile p, DosboxVersion v) {
		List<String> args = new ArrayList<>();
		File config = configFile(p);
		if ((config != null) && config.isFile())
			args.add("--config=" + config.getAbsolutePath());
		if (StringUtils.isNotBlank(v.getExecutableParameters()))
			for (String s: StringUtils.split(v.getExecutableParameters(), ' '))
				args.add(s);
		String target = targetOf(p);
		if (target != null)
			args.add(target);
		return args;
	}

	/** alpha 54: a game ScummVM found in a folder by itself (--detect): its id (engine:game), what ScummVM calls it, its folder */
	public record Detected(String id, String description, File path) {
	}

	/** alpha 54: the scummvm.ini of a ScummVM version: next to the program for a portable one, else the one of the user */
	public static File iniOf(DosboxVersion v) {
		File ini = new File(v.getCanonicalExecutable().getParentFile(), "scummvm.ini");
		return ini.isFile() ? ini: defaultIni();
	}

	/**
	 * alpha 54: the games ScummVM finds in a folder and the folders below ({@code scummvm --detect --recursive}); nothing is
	 * added to ScummVM by that. At most two minutes; what it found until then
	 */
	public static List<Detected> detect(File executable, File root) throws IOException {
		return parseDetect(runScummvm(executable, List.of("--path=" + root.getAbsolutePath(), "--recursive", "--detect"), 120), root);
	}

	/**
	 * alpha 54: one game found by --detect added to ScummVM ({@code scummvm --config=<ini> --path=<folder> --game=<id> --add}), as
	 * ScummVM's own "Add game" does; the targets it got in that scummvm.ini (usually one)
	 */
	public static List<Game> add(File executable, File ini, Detected d) throws IOException {
		java.util.Set<String> before = new java.util.HashSet<>();
		if (ini.isFile())
			for (Game g: readIni(ini))
				before.add(g.target());
		runScummvm(executable, List.of("--config=" + ini.getAbsolutePath(), "--path=" + d.path().getAbsolutePath(), "--game=" + d.id(), "--add"), 60);
		List<Game> added = new ArrayList<>();
		if (ini.isFile())
			for (Game g: readIni(ini))
				if (!before.contains(g.target()))
					added.add(g);
		return added;
	}

	private static String runScummvm(File executable, List<String> args, int seconds) throws IOException {
		List<String> cmd = new ArrayList<>();
		cmd.add(executable.getAbsolutePath());
		cmd.addAll(args);
		ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
		pb.directory(executable.getAbsoluteFile().getParentFile());
		Process proc = pb.start();
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		Thread reader = new Thread(() -> {
			try (java.io.InputStream in = proc.getInputStream()) {
				in.transferTo(out);
			} catch (IOException e) {
				// what was read
			}
		}, "scummvm output");
		reader.setDaemon(true);
		reader.start();
		try {
			if (!proc.waitFor(seconds, java.util.concurrent.TimeUnit.SECONDS))
				proc.destroyForcibly();
			reader.join(2000);
		} catch (InterruptedException e) {
			proc.destroyForcibly();
			Thread.currentThread().interrupt();
		}
		synchronized (out) {
			return decode(out.toByteArray());
		}
	}

	/**
	 * alpha 54: the table --detect prints: a line of titles, a line of dashes that gives the columns, then one line a game. Older
	 * versions have three columns (GameID, Description, Full path), newer ones four (EngineID, GameID, Description, Full path)
	 */
	public static List<Detected> parseDetect(String output, File root) {
		// a long description pushes the path to the right: the path is found by the folder searched (all are below it)
		String rootText = root == null ? null: root.getAbsolutePath().replace('\\', '/').toLowerCase(Locale.ROOT);
		List<Detected> result = new ArrayList<>();
		String[] lines = output.split("\\r?\\n");
		int[] starts = null;
		for (String line: lines) {
			if (starts == null) {
				if (line.matches("-+( +-+){2,}\\s*")) {
					java.util.regex.Matcher m = java.util.regex.Pattern.compile("-+").matcher(line);
					List<Integer> s = new ArrayList<>();
					while (m.find())
						s.add(m.start());
					starts = s.stream().mapToInt(Integer::intValue).toArray();
				}
				continue;
			}
			if (line.isBlank() || line.length() <= starts[starts.length - 1])
				continue;
			String[] cols = new String[starts.length];
			for (int i = 0; i < starts.length; i++)
				cols[i] = line.substring(starts[i], i + 1 < starts.length ? Math.min(line.length(), starts[i + 1]): line.length()).strip();
			String id = starts.length >= 4 ? (cols[0].isEmpty() ? cols[1]: cols[0] + ":" + cols[1]): cols[0];
			String description = cols[starts.length - 2];
			String pathText = cols[starts.length - 1];
			int descriptionStart = starts[starts.length - 2];
			int at = rootText == null ? -1: line.replace('\\', '/').toLowerCase(Locale.ROOT).indexOf(rootText, descriptionStart);
			if (at > descriptionStart) {
				pathText = line.substring(at).strip();
				description = line.substring(descriptionStart, at).strip();
			}
			if (!id.isEmpty() && !id.contains(" ") && !pathText.isEmpty())
				result.add(new Detected(id, description, new File(pathText)));
		}
		return result;
	}

	/** alpha 52: the folder of it in the data of DBGL (settings.conf stays at the top: the official DBGL looks for it there) */
	public static final String CONF_DIR = "config";

	/** the configuration file of the ScummVM version: written once, with a comment only, in the folder "config" of the data */
	public static File versionConf(File dataDir) throws IOException {
		File dir = new File(dataDir, CONF_DIR);
		Files.createDirectories(dir.toPath());
		File f = new File(dir, VERSION_CONF);
		if (!f.isFile())
			Files.writeString(f.toPath(), "# ScummVM: DBGL keeps no settings for it here. ScummVM keeps its own in scummvm.ini.\r\n");
		return f;
	}

	/** alpha 52: whether the file is the one DBGL wrote (its comment only), so it can go */
	public static boolean writtenByDbgl(File f) {
		try {
			return Files.readString(f.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1).trim().equals("# ScummVM: DBGL keeps no settings for it here. ScummVM keeps its own in scummvm.ini.");
		} catch (IOException e) {
			return false;
		}
	}

	/** alpha 52: where the test versions put it before (at the top, or in the folder "scummvm"), to move into "config" */
	public static List<File> oldVersionConfs(File dataDir) {
		return List.of(new File(dataDir, VERSION_CONF), new File(new File(dataDir, "scummvm"), VERSION_CONF));
	}
}
