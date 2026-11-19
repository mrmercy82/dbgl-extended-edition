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

	/** the configuration file of the ScummVM version: written once, with a comment only */
	public static File versionConf(File dataDir) throws IOException {
		File f = new File(dataDir, VERSION_CONF);
		if (!f.isFile())
			Files.writeString(f.toPath(), "# ScummVM: DBGL keeps no settings for it here. ScummVM keeps its own in scummvm.ini.\r\n");
		return f;
	}
}
