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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.model.conf.mount.PhysFsMount;
import org.dbgl.util.ProfileChecks.Problem;


/**
 * Alpha 53: a profile that starts Windows 3.x (WIN.COM): what Windows starts then (the program after WIN, "run=" and "load="
 * of WIN.INI, "shell=" of SYSTEM.INI), and whether its drivers fit the settings of DOSBox: the sound card of SYSTEM.INI
 * ([sndblst.drv]: port, interrupt, DMA) and the S3 display driver, which needs the S3 card of DOSBox. Windows itself is only
 * read, never changed.
 */
public final class Win3x {

	/** an INI file of Windows: sections and keys in lower case, the first value of a key */
	public static final class Ini {
		private final Map<String, Map<String, String>> sections_ = new LinkedHashMap<>();

		public static Ini read(File f) throws IOException {
			Ini ini = new Ini();
			Map<String, String> sec = null;
			for (String raw: Files.readString(f.toPath(), StandardCharsets.ISO_8859_1).split("\\R")) {
				String line = raw.strip();
				if (line.isEmpty() || line.startsWith(";"))
					continue;
				if (line.startsWith("[") && line.endsWith("]")) {
					sec = ini.sections_.computeIfAbsent(line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT), k -> new LinkedHashMap<>());
					continue;
				}
				int eq = line.indexOf('=');
				if ((sec != null) && (eq > 0))
					sec.putIfAbsent(line.substring(0, eq).strip().toLowerCase(Locale.ROOT), line.substring(eq + 1).strip());
			}
			return ini;
		}

		/** the keys and values of a section (keys in lower case), empty if it is not there */
		public Map<String, String> section(String section) {
			return sections_.getOrDefault(section.toLowerCase(Locale.ROOT), Map.of());
		}

		public String get(String section, String key) {
			Map<String, String> sec = sections_.get(section.toLowerCase(Locale.ROOT));
			return sec == null ? null: sec.get(key.toLowerCase(Locale.ROOT));
		}
	}

	/** an installation of Windows 3.x: its folder, SYSTEM.INI and (if there) WIN.INI */
	public record Install(File dir, Ini system, Ini win) {
	}

	/** a program item of the Program Manager: its group, name, command line and (if set) working folder */
	public record GroupItem(String group, String name, String command, String dir) {
		/** the program of the command line ("WINSETUP.EXE /Z": WINSETUP.EXE) */
		public String program() {
			String c = StringUtils.defaultString(command).strip();
			return c.isEmpty() ? c: c.split("\\s+")[0];
		}
	}

	/** Windows started from the own lines of the autoexec ("C:", "cd \\WINDOWS", "win"): the installation and what follows WIN */
	public record Started(Install install, String params) {
	}

	private Win3x() {
	}

	/** the installation started by this program (WIN.COM and the like), or null if it is no start of Windows or has no SYSTEM.INI */
	public static Install of(File main) {
		if ((main == null) || !DuplicateProfiles.startsWindows(main.getName()))
			return null;
		return ofDir(main.getAbsoluteFile().getParentFile());
	}

	/**
	 * Windows started by the own lines of the autoexec, as many profiles do it ("C:", "cd \\WINDOWS", "win", or
	 * "C:\\WINDOWS\\WIN.COM"): the drive and folder followed through the lines; null if no line starts Windows
	 */
	public static Started fromAutoexec(List<String> lines, List<Mount> mounts, File startDir) {
		char drive = 'C';
		String dir = "\\";
		boolean moved = false; // a change of drive or folder in the lines: else they start where DBGL leaves them (startDir)
		for (String raw: lines) {
			String line = raw.strip();
			if (line.startsWith("@"))
				line = line.substring(1).strip();
			if (line.isEmpty())
				continue;
			String[] parts = line.split("\\s+", 2);
			String cmd = parts[0], rest = parts.length > 1 ? parts[1].strip(): "";
			String upper = cmd.toUpperCase(Locale.ROOT);
			if (upper.matches("[A-Z]:")) {
				drive = upper.charAt(0);
				dir = "\\";
				moved = true;
			} else if (upper.equals("CD") || upper.startsWith("CD\\") || upper.startsWith("CD.")) {
				String to = upper.equals("CD") ? rest: cmd.substring(2) + (rest.isEmpty() ? "": " " + rest);
				to = to.strip();
				if ((to.length() >= 2) && (to.charAt(1) == ':'))
					to = to.substring(2); // "cd C:\\X" does not change the drive
				if (to.isEmpty())
					continue;
				moved = true;
				if (to.startsWith("\\"))
					dir = to;
				else if (to.equals(".."))
					dir = dir.contains("\\") && (dir.lastIndexOf('\\') > 0) ? dir.substring(0, dir.lastIndexOf('\\')): "\\";
				else
					dir = (dir.endsWith("\\") ? dir: dir + "\\") + to;
			} else {
				String name = cmd.replace('/', '\\');
				String base = name.contains("\\") ? name.substring(name.lastIndexOf('\\') + 1): name;
				if (!DuplicateProfiles.startsWindows(base) && !base.equalsIgnoreCase("WIN"))
					continue;
				if (!name.contains("\\") && !moved && (startDir != null)) {
					Install in = ofDir(startDir);
					if (in != null)
						return new Started(in, rest);
					continue;
				}
				String where;
				if (name.contains("\\"))
					where = ((name.length() >= 2) && (name.charAt(1) == ':') ? "": drive + ":") + name.substring(0, name.lastIndexOf('\\'));
				else
					where = drive + ":" + dir;
				File d = resolvePath(where, null, mounts);
				Install in = d == null ? null: ofDir(d);
				if (in != null)
					return new Started(in, rest);
			}
		}
		return null;
	}

	static Install ofDir(File dir) {
		File system = file(dir, "SYSTEM.INI");
		if (system == null)
			return null;
		try {
			File win = file(dir, "WIN.INI");
			return new Install(dir, Ini.read(system), win == null ? null: Ini.read(win));
		} catch (IOException e) {
			return null;
		}
	}

	/** a file of the folder, the name in any case */
	static File file(File dir, String name) {
		String[] names = dir == null ? null: dir.list();
		if (names != null)
			for (String n: names)
				if (n.equalsIgnoreCase(name) && new File(dir, n).isFile())
					return new File(dir, n);
		return null;
	}

	/**
	 * What Windows starts: the program after WIN (its parameters), the programs of "run=" and "load=" in WIN.INI, and the
	 * shell of SYSTEM.INI if it is not the Program Manager; as written (DOS paths)
	 */
	public static List<String> started(Install in, String params) {
		List<String> result = new ArrayList<>();
		String p = StringUtils.defaultString(params).strip();
		if (!p.isEmpty()) {
			String first = p.split("\\s+")[0];
			if (!first.startsWith("/") && !first.startsWith("-") && !first.startsWith(":"))
				result.add(first);
		}
		if (in.win() != null)
			for (String key: new String[] {"run", "load"})
				for (String s: StringUtils.defaultString(in.win().get("windows", key)).split("[\\s,]+"))
					if (!s.isBlank() && !result.contains(s))
						result.add(s);
		String shell = StringUtils.defaultString(in.system().get("boot", "shell")).strip();
		if (!shell.isEmpty()) {
			String prog = shell.split("\\s+")[0];
			String name = new File(prog.replace('\\', '/')).getName().toLowerCase(Locale.ROOT);
			if (!name.equals("progman.exe") && !name.equals("progman") && !result.contains(prog))
				result.add(prog);
		}
		return result;
	}

	/**
	 * The file on this computer for a DOS path of Windows ("C:\BADMOJO\BADMOJO.EXE", or a name alone: in the folder of
	 * Windows), through the mounts of the profile; null if it is not to be found
	 */
	public static File resolve(String dosPath, File windowsDir, List<Mount> mounts) {
		File f = resolvePath(dosPath, windowsDir, mounts);
		return (f != null) && f.isFile() ? f: null;
	}

	/** as resolve, a folder as well */
	static File resolvePath(String dosPath, File windowsDir, List<Mount> mounts) {
		String p = dosPath.replace('/', '\\');
		File base;
		String rest;
		if ((p.length() >= 2) && (p.charAt(1) == ':')) {
			base = null;
			for (Mount m: mounts)
				if ((m instanceof DirMount dm) && !(m instanceof PhysFsMount) && !m.isUnmounted() && m.matchesDrive(p.charAt(0)))
					base = dm.getCanonicalPath();
			if (base == null)
				return null;
			rest = StringUtils.removeStart(p.substring(2), "\\");
		} else {
			base = windowsDir;
			rest = p;
			if (base == null)
				return null;
		}
		File f = base;
		for (String part: rest.split("\\\\")) {
			if (part.isEmpty() || part.equals("."))
				continue;
			File next = file(f, part);
			if (next == null) {
				File dir = dir(f, part);
				if (dir == null)
					return programWithoutExtension(f, part);
				next = dir;
			}
			f = next;
		}
		return f.exists() ? f: null;
	}

	private static File dir(File parent, String name) {
		String[] names = parent == null ? null: parent.list();
		if (names != null)
			for (String n: names)
				if (n.equalsIgnoreCase(name) && new File(parent, n).isDirectory())
					return new File(parent, n);
		return null;
	}

	/** "BADMOJO" for BADMOJO.EXE (as Windows takes a program without its extension) */
	private static File programWithoutExtension(File dir, String name) {
		if (name.contains("."))
			return null;
		for (String ext: new String[] {".EXE", ".COM", ".BAT", ".PIF"}) {
			File f = file(dir, name + ext);
			if (f != null)
				return f;
		}
		return null;
	}

	/** the program items of all groups of the Program Manager (PROGMAN.INI and its .GRP files); groups that cannot be read are left out */
	public static List<GroupItem> groups(Install in, List<Mount> mounts) {
		List<GroupItem> result = new ArrayList<>();
		File progman = file(in.dir(), "PROGMAN.INI");
		if (progman == null)
			return result;
		try {
			Ini ini = Ini.read(progman);
			for (Map.Entry<String, String> e: ini.section("groups").entrySet()) {
				if (!e.getKey().startsWith("group"))
					continue;
				File grp = resolve(e.getValue(), in.dir(), mounts);
				if (grp != null)
					result.addAll(readGroup(grp));
			}
		} catch (IOException e) {
			// a PROGMAN.INI that cannot be read: no groups
		}
		return result;
	}

	/** the name of the group Windows starts the programs of ([Settings] Startup=, else "StartUp" or the German "Autostart") */
	public static boolean isStartupGroup(Install in, String group) {
		File progman = file(in.dir(), "PROGMAN.INI");
		String startup = null;
		try {
			startup = progman == null ? null: Ini.read(progman).get("settings", "startup");
		} catch (IOException e) {
			// none
		}
		if (StringUtils.isNotBlank(startup))
			return startup.strip().equalsIgnoreCase(group);
		return group.equalsIgnoreCase("StartUp") || group.equalsIgnoreCase("Autostart") || group.equalsIgnoreCase("Autostart-Gruppe");
	}

	/**
	 * A group file of the Program Manager of Windows 3.1 ("PMCC"): the header with the name of the group and the offsets of the
	 * items, each item with the offsets of its name and command line, the working folders in the tags after the group
	 */
	public static List<GroupItem> readGroup(File grp) throws IOException {
		byte[] d = Files.readAllBytes(grp.toPath());
		List<GroupItem> result = new ArrayList<>();
		if ((d.length < 34) || (d[0] != 'P') || (d[1] != 'M') || (d[2] != 'C') || (d[3] != 'C'))
			return result;
		int size = word(d, 6), count = word(d, 32);
		String group = string(d, word(d, 22));
		Map<Integer, String> dirs = new java.util.HashMap<>();
		for (int o = size; (o > 0) && (o + 6 <= d.length);) {
			int id = word(d, o), item = word(d, o + 2), cb = word(d, o + 4);
			if (id == 0xFFFF)
				break;
			if (id == 0x8101)
				dirs.put(item, string(d, o + 6));
			if (cb == 0)
				break;
			o += cb;
		}
		for (int i = 0; (i < count) && (34 + 2 * i + 1 < d.length); i++) {
			int off = word(d, 34 + 2 * i);
			if ((off == 0) || (off + 24 > d.length))
				continue;
			result.add(new GroupItem(group, string(d, word(d, off + 18)), string(d, word(d, off + 20)), dirs.get(i)));
		}
		return result;
	}

	private static int word(byte[] d, int o) {
		return (o + 1 < d.length) && (o >= 0) ? (d[o] & 0xff) | ((d[o + 1] & 0xff) << 8): 0;
	}

	private static String string(byte[] d, int o) {
		if ((o <= 0) || (o >= d.length))
			return "";
		int e = o;
		while ((e < d.length) && (d[e] != 0))
			e++;
		return new String(d, o, e - o, java.nio.charset.Charset.forName("IBM850"));
	}

	/** the program items that start a program outside the folder of Windows (its own programs, as the Clock, left out) */
	public static List<GroupItem> ownPrograms(Install in, List<GroupItem> items, List<Mount> mounts) {
		List<GroupItem> result = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (GroupItem it: items) {
			String prog = it.program();
			String lower = prog.toLowerCase(Locale.ROOT);
			if (!lower.contains("\\") || !(lower.endsWith(".exe") || lower.endsWith(".com") || lower.endsWith(".bat") || lower.endsWith(".pif")))
				continue; // a name alone is one of Windows (in its folder or the PATH), a document opened by a program is no program
			File f = resolve(prog, in.dir(), mounts);
			if ((f != null) && f.getAbsoluteFile().getParentFile().equals(in.dir().getAbsoluteFile()))
				continue;
			if (seen.add(lower))
				result.add(it);
		}
		return result;
	}

	/** the settings of [sblaster] the sound driver of Windows was set up with: item, value (in the way DOSBox writes it) */
	public static Map<String, String> sound(Install in) {
		Map<String, String> result = new LinkedHashMap<>();
		String wave = StringUtils.defaultString(in.system().get("drivers", "wave")).toLowerCase(Locale.ROOT);
		if (!wave.contains("sndblst") && !wave.startsWith("sb"))
			return result;
		String port = in.system().get("sndblst.drv", "port"), irq = in.system().get("sndblst.drv", "int"), dma = in.system().get("sndblst.drv", "dmachannel"),
				hdma = in.system().get("sndblst.drv", "hdmachannel");
		if (StringUtils.isNotBlank(port))
			result.put("sbbase", port.strip().toLowerCase(Locale.ROOT));
		if (StringUtils.isNotBlank(irq))
			result.put("irq", irq.strip());
		if (StringUtils.isNotBlank(dma))
			result.put("dma", dma.strip());
		if (wave.startsWith("sb16") && StringUtils.isNotBlank(hdma))
			result.put("hdma", hdma.strip());
		return result;
	}

	/** true for a display driver of S3 (s3vsnd.drv, s3trio.drv, ...): it needs the S3 card of DOSBox */
	public static boolean s3Display(Install in) {
		String drv = StringUtils.defaultString(in.system().get("boot", "display.drv")).toLowerCase(Locale.ROOT);
		return drv.startsWith("s3");
	}

	/** true for a path on a drive of a CD or disk image ("D:\\BADMOJO.EXE" on badmojo.iso): DBGL cannot look into it */
	static boolean onImage(String dosPath, List<Mount> mounts) {
		String p = StringUtils.defaultString(dosPath);
		if ((p.length() < 2) || (p.charAt(1) != ':'))
			return false;
		return mounts.stream().anyMatch(m -> !m.isUnmounted() && m.matchesDrive(p.charAt(0)) && !((m instanceof DirMount) && !(m instanceof PhysFsMount)));
	}

	/**
	 * The installation of Windows 3.x a profile starts (WIN.COM as its program, or own lines of the autoexec), with what follows
	 * WIN; null if it starts none
	 */
	public static Started of(org.dbgl.model.aggregate.Profile prof) {
		org.dbgl.model.conf.Autoexec autoexec = prof.getConfiguration().getAutoexec();
		List<Mount> netto = prof.getNettoMountingPoints();
		File main = null;
		if (StringUtils.isNotBlank(autoexec.getMainWhereDosboxLooks()))
			main = new org.dbgl.model.FileLocation(autoexec.getMainWhereDosboxLooks(), org.dbgl.service.FileLocationService.getInstance().dosrootRelative()).getCanonicalFile();
		Install in = of(main == null ? null: ProfileChecks.programFile(main));
		if (in != null)
			return new Started(in, StringUtils.defaultString(autoexec.getParameters()));
		return fromAutoexec(ProfileChecks.ownLines(autoexec), netto, main == null ? null: main.getParentFile());
	}

	/** true if the profile starts Windows by its program WIN.COM (the program then goes into its parameters) */
	public static boolean startsByMain(org.dbgl.model.aggregate.Profile prof) {
		org.dbgl.model.conf.Autoexec autoexec = prof.getConfiguration().getAutoexec();
		String main = autoexec.getMainWhereDosboxLooks();
		return StringUtils.isNotBlank(main) && DuplicateProfiles.startsWindows(new File(main.replace('\\', '/')).getName());
	}

	/**
	 * The text of a configuration file with the line of the autoexec that starts Windows ("win", "C:\\WINDOWS\\WIN.COM /3")
	 * now starting this program in it ("win C:\\BADMOJO\\BADMOJO.EXE"); null if there is no such line
	 */
	public static String withProgram(String conf, String program) {
		String[] lines = conf.split("\\r?\\n", -1);
		String eol = conf.contains("\r\n") ? "\r\n": "\n";
		boolean autoexec = false;
		for (int i = 0; i < lines.length; i++) {
			String l = lines[i].strip();
			if (l.startsWith("[") && l.endsWith("]")) {
				autoexec = l.equalsIgnoreCase("[autoexec]");
				continue;
			}
			if (!autoexec)
				continue;
			String body = l.startsWith("@") ? l.substring(1).strip(): l;
			String cmd = body.split("\\s+", 2)[0];
			String base = cmd.replace('/', '\\');
			base = base.contains("\\") ? base.substring(base.lastIndexOf('\\') + 1): base;
			if (!DuplicateProfiles.startsWindows(base))
				continue;
			int at = lines[i].indexOf(cmd);
			lines[i] = lines[i].substring(0, at + cmd.length()) + " " + program;
			return String.join(eol, lines);
		}
		return null;
	}

	/**
	 * Profiles for these program items: each a copy of the profile, whose line that starts Windows starts the program right away
	 * ("win C:\\BADMOJO\\BADMOJO.EXE"; with WIN.COM as its program: in the parameters), the name of the item as title, the
	 * details of the game of the profile left empty
	 */
	public static List<org.dbgl.model.aggregate.Profile> createProfiles(org.dbgl.model.aggregate.Profile profile, List<GroupItem> chosen) throws java.sql.SQLException, IOException {
		boolean byMain = startsByMain(profile);
		List<org.dbgl.model.aggregate.Profile> added = new ArrayList<>();
		for (GroupItem it: chosen) {
			org.dbgl.model.aggregate.Profile copy = new org.dbgl.model.repository.ProfileRepository().duplicate(profile);
			copy.setTitle(it.name());
			copy.setDeveloper(StringUtils.EMPTY);
			copy.setPublisher(StringUtils.EMPTY);
			copy.setGenre(StringUtils.EMPTY);
			copy.setYear(StringUtils.EMPTY);
			copy.setNotes(StringUtils.EMPTY);
			if (byMain) {
				copy.setAutoexecSettings(profile.getConfiguration().getAutoexec().getMain(), it.program());
				copy.saveConfiguration();
			} else {
				File conf = copy.getConfigurationCanonicalFile();
				java.nio.charset.Charset cs = copy.getConfiguration().getCharset() == null ? StandardCharsets.ISO_8859_1: copy.getConfiguration().getCharset();
				String changed = withProgram(Files.readString(conf.toPath(), cs), it.program());
				if (changed != null) {
					Files.writeString(conf.toPath(), changed, cs);
					copy.resetAndLoadConfiguration();
				}
			}
			new org.dbgl.model.repository.ProfileRepository().update(copy);
			added.add(copy);
		}
		return added;
	}

	/** true if the program of an item is there (or on a CD or disk image, which DBGL cannot look into) */
	public static boolean isThere(String program, Install in, List<Mount> mounts) {
		return onImage(program, mounts) || (resolve(program, in.dir(), mounts) != null);
	}

	/** at most 12 entries, then "..." */
	static String shortList(List<String> list) {
		return list.size() <= 12 ? String.join(", ", list): String.join(", ", list.subList(0, 12)) + ", ...";
	}

	/** the problems of a profile that starts this installation */
	public static void check(Install in, String params, Configuration applies, List<Mount> mounts, List<Problem> result) {
		List<String> started = new ArrayList<>(started(in, params));
		List<GroupItem> items = groups(in, mounts);
		for (GroupItem it: items)
			if (isStartupGroup(in, it.group()) && !it.program().isEmpty() && !started.contains(it.program()))
				started.add(it.program());
		if (!started.isEmpty())
			result.add(new Problem("win3start", String.join(", ", started)));
		for (String s: started)
			if (!onImage(s, mounts) && (resolve(s, in.dir(), mounts) == null))
				result.add(new Problem("win3startmissing", s));
		// the programs in the groups of the Program Manager: what this Windows holds, and the items whose program is gone
		List<GroupItem> own = ownPrograms(in, items, mounts);
		List<String> there = new ArrayList<>(), gone = new ArrayList<>();
		for (GroupItem it: own)
			(onImage(it.program(), mounts) || (resolve(it.program(), in.dir(), mounts) != null) ? there: gone).add(it.name() + " (" + it.program() + ")");
		if (!there.isEmpty())
			result.add(new Problem("win3programs", shortList(there)));
		if (!gone.isEmpty())
			result.add(new Problem("win3itemsgone", shortList(gone)));
		Map<String, String> sound = sound(in);
		if (!sound.isEmpty()) {
			String sbtype = StringUtils.trimToEmpty(applies.getValue("sblaster", "sbtype")).toLowerCase(Locale.ROOT);
			String wave = StringUtils.defaultString(in.system().get("drivers", "wave")).toLowerCase(Locale.ROOT);
			if (sbtype.equals("none") || sbtype.equals("false"))
				result.add(new Problem("win3nosound", "[sblaster] sbtype", wave.startsWith("sb16") ? "sb16": "sbpro2", sbtype));
			else
				for (Map.Entry<String, String> e: sound.entrySet()) {
					if (!applies.hasValue("sblaster", e.getKey()))
						continue;
					String current = StringUtils.trimToEmpty(applies.getValue("sblaster", e.getKey()));
					if (!current.equalsIgnoreCase(e.getValue()))
						result.add(new Problem("win3sound", "[sblaster] " + e.getKey(), e.getValue(), current));
				}
		}
		if (s3Display(in)) {
			String machine = StringUtils.trimToEmpty(applies.getValue("dosbox", "machine")).toLowerCase(Locale.ROOT);
			if (!machine.isEmpty() && !machine.startsWith("svga_s3"))
				result.add(new Problem("win3display", "[dosbox] machine", "svga_s3", machine, in.system().get("boot", "display.drv")));
		}
	}
}
