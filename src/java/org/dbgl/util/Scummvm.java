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
		String config = valueOf(p, "config");
		if (StringUtils.isNotBlank(config) && new File(config.trim()).isFile())
			args.add("--config=" + config.trim());
		if (StringUtils.isNotBlank(v.getExecutableParameters()))
			for (String s: StringUtils.split(v.getExecutableParameters(), ' '))
				args.add(s);
		String target = targetOf(p);
		if (target != null)
			args.add(target);
		return args;
	}

	/** alpha 52: the folder of it in the data of DBGL (before, it was at the top, beside dbgl.jar on Windows) */
	public static final String CONF_DIR = "scummvm";

	/** the configuration file of the ScummVM version: written once, with a comment only, in the folder "scummvm" of the data */
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

	/** alpha 52: the file where it was before (at the top of the data), to move */
	public static File oldVersionConf(File dataDir) {
		return new File(dataDir, VERSION_CONF);
	}
}
