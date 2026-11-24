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

		public String get(String section, String key) {
			Map<String, String> sec = sections_.get(section.toLowerCase(Locale.ROOT));
			return sec == null ? null: sec.get(key.toLowerCase(Locale.ROOT));
		}
	}

	/** an installation of Windows 3.x: its folder, SYSTEM.INI and (if there) WIN.INI */
	public record Install(File dir, Ini system, Ini win) {
	}

	private Win3x() {
	}

	/** the installation started by this program (WIN.COM and the like), or null if it is no start of Windows or has no SYSTEM.INI */
	public static Install of(File main) {
		if ((main == null) || !DuplicateProfiles.startsWindows(main.getName()))
			return null;
		File dir = main.getAbsoluteFile().getParentFile();
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
		return f.isFile() ? f: null;
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

	/** the problems of a profile that starts this installation */
	public static void check(Install in, String params, Configuration applies, List<Mount> mounts, List<Problem> result) {
		List<String> started = started(in, params);
		if (!started.isEmpty())
			result.add(new Problem("win3start", String.join(", ", started)));
		for (String s: started)
			if (resolve(s, in.dir(), mounts) == null)
				result.add(new Problem("win3startmissing", s));
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
