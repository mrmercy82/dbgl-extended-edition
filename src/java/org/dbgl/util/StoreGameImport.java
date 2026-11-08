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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.service.ITextService;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * DOS games bought at GOG or Steam come with DOSBox and their own DOSBox configuration: GOG writes goggame-&lt;id&gt;.info
 * (name and how the game is started: dosbox.exe -conf "..\dosboxGAME.conf" -conf "..\dosboxGAME_single.conf", working
 * folder DOSBOX), Steam versions mostly a dosbox*.conf next to their DOSBox. From these, profiles for a DOSBox version of
 * DBGL are made: the [autoexec] (mounts, start) with the paths of the game folder, and those settings that the DOSBox
 * version knows; the screen settings ([sdl], [render]) are left to DBGL and its DOSBox version.
 */
public final class StoreGameImport {

	/** a game found: its name, folder, the configuration files in the order DOSBox reads them, and its working folder */
	public record Found(String title, File gameDir, List<File> confs, File workingDir, String store) {
	}

	static final int DEPTH = 4;
	static final Pattern CONF_ARGUMENT = Pattern.compile("-conf\\s+\"([^\"]+)\"|-conf\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
	/** sections of a GOG or Steam configuration that are about the screen of that DOSBox: left to DBGL and its DOSBox */
	static final List<String> SKIPPED_SECTIONS = List.of("sdl", "render", "mixer", "autoexec");
	/** settings about this computer rather than the game (language of DOSBox, keyboard, where captures go): left to DBGL */
	static final List<String> SKIPPED_ITEMS = List.of("language", "keyboardlayout", "keyboard_layout", "captures", "capture_dir", "mapperfile");

	private StoreGameImport() {
	}

	/** @return the GOG and Steam DOS games below this folder (up to 4 levels deep), each only once */
	public static List<Found> find(File root) {
		Map<File, Found> result = new LinkedHashMap<>();
		List<Path> infos = new ArrayList<>(), confs = new ArrayList<>();
		try (Stream<Path> files = Files.walk(root.toPath(), DEPTH)) {
			files.filter(Files::isRegularFile).forEach(p -> {
				String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
				if (n.startsWith("goggame-") && n.endsWith(".info"))
					infos.add(p);
				else if (n.startsWith("dosbox") && n.endsWith(".conf"))
					confs.add(p);
			});
		} catch (IOException | RuntimeException e) {
			// an unreadable folder: what was found so far
		}
		for (Path info: infos) {
			Found f = fromGogInfo(info.toFile());
			if ((f != null) && !result.containsKey(f.gameDir()))
				result.put(f.gameDir(), f);
		}
		// without an info file (older GOG versions, Steam): a dosbox*.conf with an [autoexec] that mounts something
		for (Path conf: confs) {
			File dir = conf.getParent().toFile();
			File gameDir = dir.getName().equalsIgnoreCase("DOSBOX") ? dir.getParentFile(): dir;
			if (result.containsKey(gameDir) || result.values().stream().anyMatch(f -> f.confs().contains(conf.toFile())))
				continue;
			if (conf.getFileName().toString().toLowerCase(Locale.ROOT).contains("_single") || !mountsSomething(conf.toFile()))
				continue;
			List<File> list = new ArrayList<>();
			// GOG: dosboxGAME.conf with the settings, dosboxGAME_single.conf with the [autoexec]
			String name = conf.getFileName().toString();
			File single = new File(dir, name.substring(0, name.length() - 5) + "_single.conf");
			list.add(conf.toFile());
			if (single.isFile())
				list.add(single);
			File working = workingDirFor(gameDir, dir);
			result.put(gameDir, new Found(titleOf(gameDir.getName()), gameDir, list, working, single.isFile() ? "GOG": storeOf(gameDir)));
		}
		// the single conf may hold the mounts while the first one does not: look again at GOG pairs
		for (Path conf: confs) {
			String name = conf.getFileName().toString().toLowerCase(Locale.ROOT);
			if (!name.endsWith("_single.conf"))
				continue;
			File dir = conf.getParent().toFile();
			File gameDir = dir.getName().equalsIgnoreCase("DOSBOX") ? dir.getParentFile(): dir;
			if (result.containsKey(gameDir))
				continue;
			File settings = new File(dir, conf.getFileName().toString().replaceFirst("(?i)_single\\.conf$", ".conf"));
			List<File> list = new ArrayList<>();
			if (settings.isFile())
				list.add(settings);
			list.add(conf.toFile());
			File working = workingDirFor(gameDir, dir);
			result.put(gameDir, new Found(titleOf(gameDir.getName()), gameDir, list, working, "GOG"));
		}
		return new ArrayList<>(result.values());
	}

	/**
	 * The folder DOSBox is started in, which relative paths in the configuration start from: the one with dosbox.exe (Steam's
	 * X-COM has it next to its dosbox.conf, a "dosbox" folder there only holds the documentation; GOG has it in DOSBOX).
	 */
	static File workingDirFor(File gameDir, File confDir) {
		for (File d: new File[] {confDir, new File(gameDir, "DOSBOX"), new File(gameDir, "dosbox"), gameDir}) {
			File[] exe = d.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).matches("dosbox(\\.exe)?"));
			if ((exe != null) && (exe.length > 0))
				return d;
		}
		return confDir;
	}

	/** Steam for a game in steamapps, else just "DOSBox" (a game packed with its own DOSBox, e.g. a DOSBox SVN-Daum package) */
	static String storeOf(File gameDir) {
		return gameDir.getAbsolutePath().toLowerCase(Locale.ROOT).replace('\\', '/').contains("/steamapps/") ? "Steam": "DOSBox";
	}

	/** "Duke_Nukem_3D" or "duke-nukem-3d" -> "Duke Nukem 3D", "earthsiege ii" -> "Earthsiege II" */
	static String titleOf(String folder) {
		String t = folder.replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").trim();
		StringBuilder b = new StringBuilder();
		for (String w: t.split(" "))
			if (!w.isEmpty())
				b.append(b.length() == 0 ? "": " ").append(w.toLowerCase(Locale.ROOT).matches("(i{1,3}|iv|v|vi{1,3}|ix|x)") ? w.toUpperCase(Locale.ROOT)
						: w.equals(w.toLowerCase(Locale.ROOT)) ? Character.toUpperCase(w.charAt(0)) + w.substring(1): w);
		return b.toString();
	}

	static boolean mountsSomething(File conf) {
		try {
			return autoexecOf(read(conf)).stream().anyMatch(l -> l.trim().toLowerCase(Locale.ROOT).matches("(img)?mount\\s+.*"));
		} catch (IOException e) {
			return false;
		}
	}

	/** GOG: the name and the configuration files from goggame-&lt;id&gt;.info (JSON), or null if it does not start DOSBox */
	static Found fromGogInfo(File info) {
		try {
			JSONObject json = new JSONObject(read(info));
			JSONArray tasks = json.optJSONArray("playTasks");
			if (tasks == null)
				return null;
			JSONObject task = null;
			for (int i = 0; i < tasks.length(); i++) {
				JSONObject t = tasks.getJSONObject(i);
				if (t.optString("arguments", "").toLowerCase(Locale.ROOT).contains("-conf") && ((task == null) || t.optBoolean("isPrimary")))
					task = t;
			}
			if (task == null)
				return null;
			File gameDir = info.getParentFile();
			File working = new File(gameDir, slashes(task.optString("workingDir", "")));
			List<File> confs = new ArrayList<>();
			Matcher m = CONF_ARGUMENT.matcher(task.optString("arguments", ""));
			while (m.find()) {
				File c = new File(working, slashes(m.group(1) != null ? m.group(1): m.group(2)));
				if (c.isFile())
					confs.add(c.getCanonicalFile());
			}
			if (confs.isEmpty())
				return null;
			String name = json.optString("name", "");
			return new Found(name.isEmpty() ? titleOf(gameDir.getName()): name, gameDir, confs, working.getCanonicalFile(), "GOG");
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	static String slashes(String path) {
		return path.replace('\\', File.separatorChar).replace('/', File.separatorChar);
	}

	static String read(File f) throws IOException {
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1);
	}

	/**
	 * @return why the game needs DOSBox-X ("ide": a CD on the IDE bus, "boot": boots a hard disk image, e.g. Windows 95), or null
	 */
	public static String needsDosboxX(Found f) {
		try {
			return dosboxXReason(prepare(f).conf());
		} catch (IOException e) {
			// no advice
		}
		return null;
	}

	/**
	 * Why a configuration (its [autoexec]) wants DOSBox-X, or null: "ide" (a CD on the IDE bus, e.g. for Windows 95), "pc98" (a
	 * PC-98 image) or "boot" (boots a hard disk image). Also for the profiles (since alpha 45).
	 */
	public static String dosboxXReason(String conf) {
		List<String> lines = conf.lines().dropWhile(l -> !l.trim().equalsIgnoreCase("[autoexec]")).map(l -> l.trim().toLowerCase(Locale.ROOT)).toList();
		if (lines.stream().anyMatch(l -> l.matches("(img)?mount\\s.*\\s-ide\\s.*") || l.matches("(img)?mount\\s.*\\s-ide$")))
			return "ide";
		if (lines.stream().anyMatch(l -> l.matches("((img)?mount|boot)\\s.*\\.(hdi|nhd|fdi|d88)\\b.*")))
			return "pc98"; // PC-98 images: only DOSBox-X
		boolean hdd = lines.stream().anyMatch(l -> l.matches("(img)?mount\\s+[0-9]\\s.*") || l.matches("(img)?mount\\s.*-t\\s+hdd.*"));
		if (hdd && lines.stream().anyMatch(l -> l.startsWith("boot")))
			return "boot";
		return null;
	}

	/** the DOSBox the configuration was written by: "# This is the configurationfile for DOSBox 0.72." (0.74 if not said) */
	static org.dbgl.model.entity.GenerationAwareConfigurable.Generation generationOf(Found f) {
		for (File c: f.confs()) {
			try {
				Matcher m = Pattern.compile("(?i)dosbox\\s*(?:version\\s*)?0\\.(\\d+)").matcher(read(c).lines().limit(5).reduce("", (a, b) -> a + "\n" + b));
				if (m.find()) {
					int minor = Integer.parseInt(m.group(1));
					if (minor < 70)
						return org.dbgl.model.entity.GenerationAwareConfigurable.Generation.official_065;
					if (minor < 73)
						return org.dbgl.model.entity.GenerationAwareConfigurable.Generation.official_070;
					return org.dbgl.model.entity.GenerationAwareConfigurable.Generation.official_073;
				}
			} catch (IOException | RuntimeException e) {
				// look at the next one
			}
		}
		return org.dbgl.model.entity.GenerationAwareConfigurable.Generation.official_073;
	}

	/** mount C "folder" or mount C folder (not an image) */
	static final Pattern MOUNT_DIR = Pattern.compile("(?i)mount\\s+([a-z]):?\\s+(?:\"([^\"]+)\"|(\\S+))(?:\\s+.*)?");

	/** the folder below base for a DOS path like "GAMES\\XCOM", whatever the case of its names; null if it is not there */
	static File existing(File base, String dosPath) {
		File cur = base;
		for (String part: dosPath.split("\\\\")) {
			if (part.isEmpty() || part.equals("."))
				continue;
			File[] match = cur.listFiles(x -> x.isDirectory() && x.getName().equalsIgnoreCase(part));
			if ((match == null) || (match.length == 0))
				return null;
			cur = match[0];
		}
		return cur;
	}

	/** the lines of the [autoexec] section */
	static List<String> autoexecOf(String conf) {
		List<String> lines = new ArrayList<>();
		boolean in = false;
		for (String l: conf.split("\\r?\\n")) {
			String t = l.trim();
			if (t.startsWith("[") && t.endsWith("]"))
				in = t.equalsIgnoreCase("[autoexec]");
			else if (in)
				lines.add(l);
		}
		return lines;
	}

	/**
	 * One configuration as DOSBox sees it after reading all the files: the settings of all (later ones win) without the screen
	 * sections, and the [autoexec] of the last that has one. Lines DBGL does without are left out: echo/cls, the closing
	 * "exit" (DBGL adds its own), and GOG's overlay mount for cloud saves.
	 */
	public static String combined(Found f) throws IOException {
		return prepare(f).conf();
	}

	/** the configuration for DBGL, and the setup program if the game came with a menu that offers one (else null) */
	public record Prepared(String conf, File setup) {
	}

	/** a program a menu of the [autoexec] starts: under which label, on which drive and in which folder */
	record Program(String label, char drive, String dir, String line) {
	}

	/** DOS commands and the like: not the program of the game */
	static final java.util.Set<String> BUILTINS = java.util.Set.of("cls", "echo", "choice", "if", "goto", "mount", "imgmount", "cd", "chdir", "set", "rem", "pause", "exit",
		"call", "path", "prompt", "keyb", "loadfix", "lh", "loadhigh", "mixer", "config", "intro", "boot", "ver", "type", "del", "copy", "md", "mkdir");
	static final Pattern SETUP_NAMES = Pattern.compile("(?i).*(setup|install|config|setsound|sndsetup|soundset).*");

	public static Prepared prepare(Found f) throws IOException {
		Map<String, Map<String, String>> sections = new LinkedHashMap<>();
		List<String> autoexec = List.of();
		for (File c: f.confs()) {
			String text = read(c);
			String section = null;
			for (String l: text.split("\\r?\\n")) {
				String t = l.trim();
				if (t.startsWith("[") && t.endsWith("]")) {
					section = t.substring(1, t.length() - 1).trim().toLowerCase(Locale.ROOT);
					continue;
				}
				if ((section == null) || SKIPPED_SECTIONS.contains(section) || t.isEmpty() || t.startsWith("#"))
					continue;
				int eq = t.indexOf('=');
				if ((eq > 0) && !SKIPPED_ITEMS.contains(t.substring(0, eq).trim().toLowerCase(Locale.ROOT)))
					sections.computeIfAbsent(section, s -> new LinkedHashMap<>()).put(t.substring(0, eq).trim().toLowerCase(Locale.ROOT), t.substring(eq + 1).trim());
			}
			List<String> a = autoexecOf(text).stream().filter(l -> !l.isBlank()).toList();
			if (!a.isEmpty())
				autoexec = a;
		}
		StringBuilder b = new StringBuilder();
		sections.forEach((s, items) -> {
			b.append('[').append(s).append(']').append('\n');
			items.forEach((k, v) -> b.append(k).append('=').append(v).append('\n'));
			b.append('\n');
		});
		b.append("[autoexec]\n");
		// which folder each drive is and where "cd" went, to leave out a "cd" into a folder that is not there: DOSBox just stays
		// where it is then (Steam's X-COM: mount c XCOM, c:, cd XCOM, call ufocd.bat starts C:\UFOCD.BAT), DBGL would look
		// for the program in that folder
		Map<Character, File> drives = new LinkedHashMap<>();
		Map<Character, String> dirs = new LinkedHashMap<>();
		char drive = 'z';
		// GOG often starts a menu of its own ("1) World of Xeen 2) Game DOS Settings 3) exit" with choice and goto): that does
		// not work in DBGL's [autoexec]. Then the game becomes the program of the profile and the settings program its setup.
		boolean menu = autoexec.stream().map(l -> l.trim().toLowerCase(Locale.ROOT)).anyMatch(l -> l.matches(":[a-z_].*") || l.startsWith("goto ") || l.startsWith("choice"));
		String label = "";
		List<Program> programs = new ArrayList<>();
		for (String l: autoexec) {
			String t = l.trim().replaceFirst("^@", "");
			String low = t.toLowerCase(Locale.ROOT);
			if (low.startsWith("echo") || low.equals("cls") || low.equals("exit") || low.startsWith("#") || (low.startsWith("mount") && low.contains("-t overlay")))
				continue;
			if (menu) {
				if (low.matches(":[a-z_].*")) {
					label = low.substring(1).trim();
					continue;
				}
				String first = low.replaceFirst("^call\\s+", "").split("\\s+")[0];
				if (low.startsWith("goto ") || low.startsWith("choice") || low.startsWith("if ") || low.startsWith("pause") || low.startsWith("rem"))
					continue;
				if (!first.matches("[a-z]:") && !BUILTINS.contains(first) && first.matches("[a-z0-9_~\\-]+(\\.(exe|com|bat))?") && !label.isEmpty()) {
					programs.add(new Program(label, drive, dirs.getOrDefault(drive, ""), t.replaceFirst("(?i)^call\\s+", "")));
					continue;
				}
				if (!label.isEmpty() && !(low.matches("[a-z]:") || low.startsWith("cd") || MOUNT_DIR.matcher(t).matches() || low.matches("(img)?mount\\s.*")))
					continue; // other lines of the menu
			}
			Matcher mount = MOUNT_DIR.matcher(t);
			if (mount.matches()) {
				File dir = new File(slashes(mount.group(2) != null ? mount.group(2): mount.group(3)));
				if (!dir.isAbsolute())
					dir = new File(f.workingDir(), dir.getPath());
				if (dir.isDirectory()) {
					drives.put(Character.toLowerCase(mount.group(1).charAt(0)), dir);
					dirs.put(Character.toLowerCase(mount.group(1).charAt(0)), "");
				}
			} else if (low.matches("[a-z]:")) {
				drive = low.charAt(0);
			} else if (low.matches("cd\\s+.+") && drives.containsKey(drive)) {
				String target = t.substring(2).trim().replace('/', '\\');
				String path = target.startsWith("\\") ? target.substring(1): (dirs.get(drive).isEmpty() ? target: dirs.get(drive) + "\\" + target);
				if (!target.equals("..") && (existing(drives.get(drive), path) == null))
					continue; // DOSBox: "Unable to change to: ..." and it stays in the folder it is in
				if (!target.equals(".."))
					dirs.put(drive, path.replaceAll("\\\\+$", ""));
			}
			if (low.matches("(img)?mount\\s.*")) {
				t = t.replaceAll("(?i)\\s+-fs\\s+iso\\b", ""); // iso is what CD images are anyway; DOSBox Staging 0.83's MOUNT does not take it
				t = t.replaceFirst("(?i)^((?:img)?mount\\s+[a-z0-9]):(\\s)", "$1$2"); // "imgmount d: x.cue": DBGL takes the drive without colon
			}
			b.append(t).append('\n');
		}
		File setup = null;
		if (menu && !programs.isEmpty()) {
			Program game = programs.stream().filter(p -> !SETUP_NAMES.matcher(p.label() + " " + p.line()).matches()).findFirst().orElse(programs.get(0));
			b.append(Character.toUpperCase(game.drive())).append(":\n").append("cd \\").append(game.dir()).append('\n').append(game.line()).append('\n');
			Program s = programs.stream().filter(p -> p != game && SETUP_NAMES.matcher(p.label() + " " + p.line()).matches()).findFirst().orElse(null);
			if ((s != null) && drives.containsKey(s.drive())) {
				File dir = s.dir().isEmpty() ? drives.get(s.drive()): existing(drives.get(s.drive()), s.dir());
				String name = s.line().split("\\s+")[0];
				File[] match = dir == null ? null: dir.listFiles(x -> x.isFile() && (x.getName().equalsIgnoreCase(name) || x.getName().toLowerCase(Locale.ROOT).matches(Pattern.quote(name.toLowerCase(Locale.ROOT)) + "\\.(exe|com|bat)")));
				if ((match != null) && (match.length > 0))
					setup = match[0].getCanonicalFile();
			}
		}
		return new Prepared(b.toString(), setup);
	}

	/**
	 * @return a new profile (not saved yet) for this game and DOSBox version: settings the DOSBox version does not know are left
	 *         out, so that its own values count
	 */
	public static Profile toProfile(Found f, DosboxVersion version, ITextService text) throws IOException {
		Profile p = ProfileFactory.create(version, null);
		p.setTitle(f.title());
		p.setDeveloper("");
		p.setPublisher("");
		p.setGenre("");
		p.setYear("");
		p.setStatus("");
		p.setNotes(f.store() + ": " + f.gameDir().getPath());
		p.setSetupFileLocation("");
		p.setSetupParams("");
		p.setAltExeFileLocations(new String[] {"", ""});
		p.setAltExeParams(new String[] {"", ""});
		Prepared prepared = prepare(f);
		p.loadConfigurationData(text, prepared.conf(), f.confs().get(f.confs().size() - 1), f.workingDir());
		if (prepared.setup() != null)
			p.setSetupFileLocation(prepared.setup().getPath());
		// the settings are those of the DOSBox the game came with (mostly 0.74, Steam's X-COM 0.72): converted like when
		// switching a profile (e.g. cycles -> cpu_cycles for Staging), settings the DOSBox version does not have are left out
		p.alterToDosboxVersionGeneration(generationOf(f), version);
		// "-ide 2m" puts a CD on the IDE bus, for Windows 95 booted from a hard disk image; only DOSBox-X has that
		if (!org.dbgl.service.SettingsService.DOSBOX_X_DOSBOX_FAM.title_.equals(version.getFamily()))
			for (org.dbgl.model.conf.mount.Mount m: p.getConfiguration().getAutoexec().getMountingpoints())
				if (m instanceof org.dbgl.model.conf.mount.ImageMount im)
					im.setIde("");
		for (String[] invalid: p.listInvalidValues())
			p.getConfiguration().removeValue(invalid[0], invalid[1]);
		if (StringUtils.isBlank(p.getConfiguration().getAutoexec().getMain()) && p.getConfiguration().getAutoexec().getMountingpoints().isEmpty())
			throw new IOException(text.get("dialog.storeimport.nostart", new Object[] {f.title()}));
		return p;
	}
}
