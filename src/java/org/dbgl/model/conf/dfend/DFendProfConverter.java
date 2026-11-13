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
package org.dbgl.model.conf.dfend;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.conf.Settings;


/**
 * A DOSBox 0.74 configuration made from a D-Fend Reloaded profile (.prof) itself, for profiles without a .conf exported from
 * D-Fend. The .prof holds the DOSBox settings under D-Fend's own names (e.g. [cpu] type, [midi] device, values 0/1), the drives
 * as "path;type;letter;..." in [Extra] 0..9, and the program to start in [Extra] Exe. Paths starting with .\ are relative to the
 * D-Fend folder. What cannot be converted is listed in the notes.
 */
public final class DFendProfConverter {

	/** the result: the configuration text and what could not be converted */
	public record Result(String conf, List<String> notes) {
	}

	/** D-Fend name -> DOSBox 0.74 name, per section; '?' in front: a 0/1 value that becomes false/true */
	private static final String[][] SETTINGS = {
		{"sdl", "fullscreen", "?fullscreen"}, {"sdl", "fulldouble", "?fulldouble"}, {"sdl", "fullresolution", "fullresolution"}, {"sdl", "windowresolution", "windowresolution"},
		{"sdl", "output", "output"}, {"sdl", "autolock", "?autolock"}, {"sdl", "sensitivity", "sensitivity"}, {"sdl", "priority", "priority"}, {"sdl", "usescancodes", "?usescancodes"},
		{"sdl", "usecancodes", "?usescancodes"}, // so written by D-Fend Reloaded in its example profiles
		{"dosbox", "machine", "machine"}, {"dosbox", "memsize", "memsize"},
		{"render", "frameskip", "frameskip"}, {"render", "aspect", "?aspect"}, {"render", "scaler", "scaler"},
		{"cpu", "core", "core"}, {"cpu", "type", "cputype"}, {"cpu", "cycles", "cycles"}, {"cpu", "cyclesup", "cycleup"}, {"cpu", "cyclesdown", "cycledown"},
		{"mixer", "nosound", "?nosound"}, {"mixer", "rate", "rate"}, {"mixer", "blocksize", "blocksize"}, {"mixer", "prebuffer", "prebuffer"},
		{"midi", "mpu401", "mpu401"}, {"midi", "device", "mididevice"}, {"midi", "config", "midiconfig"},
		{"sblaster", "sbtype", "sbtype"}, {"sblaster", "sbbase", "sbbase"}, {"sblaster", "irq", "irq"}, {"sblaster", "dma", "dma"}, {"sblaster", "hdma", "hdma"},
		{"sblaster", "mixer", "?sbmixer"}, {"sblaster", "oplmode", "oplmode"}, {"sblaster", "oplemu", "oplemu"}, {"sblaster", "oplrate", "oplrate"},
		{"gus", "gus", "?gus"}, {"gus", "gusrate", "gusrate"}, {"gus", "gusbase", "gusbase"}, {"gus", "irq1", "gusirq"}, {"gus", "dma1", "gusdma"}, {"gus", "ultradir", "ultradir"},
		{"speaker", "pcspeaker", "?pcspeaker"}, {"speaker", "pcrate", "pcrate"}, {"speaker", "tandy", "tandy"}, {"speaker", "tandyrate", "tandyrate"}, {"speaker", "disney", "?disney"},
		{"joystick", "joysticktype", "joysticktype"}, {"joystick", "timed", "?timed"}, {"joystick", "autofire", "?autofire"}, {"joystick", "swap34", "?swap34"},
		{"joystick", "buttonwrap", "?buttonwrap"},
		{"serial", "serial1", "serial1"}, {"serial", "serial2", "serial2"}, {"serial", "serial3", "serial3"}, {"serial", "serial4", "serial4"},
		{"dos", "xms", "?xms"}, {"dos", "ems", "?ems"}, {"dos", "umb", "?umb"}, {"dos", "keyboardlayout", "keyboardlayout"},
		{"ipx", "ipx", "?ipx"}};

	private DFendProfConverter() {
	}

	private static String value(Settings prof, String section, String item) {
		return StringUtils.trimToEmpty(prof.getValue(section, item));
	}

	/** D-Fend codes line breaks as [13] (and [10]), '=' as [61] */
	static List<String> lines(String coded) {
		List<String> result = new ArrayList<>();
		for (String l: StringUtils.defaultString(coded).replace("[13][10]", "[13]").replace("[10]", "[13]").replace("[61]", "=").split("\\[13\\]", -1))
			if (StringUtils.isNotBlank(l))
				result.add(l.trim());
		return result;
	}

	/** a path of D-Fend: .\ (or nothing in front) means its folder */
	public static File hostPath(File dfendDir, String path) {
		String p = path.trim().replace('\\', File.separatorChar).replace('/', File.separatorChar);
		File f = new File(p);
		if (f.isAbsolute() || p.matches("^[A-Za-z]:.*"))
			return f;
		if (p.startsWith("." + File.separator))
			p = p.substring(2);
		return new File(dfendDir, p);
	}

	private static String quoted(File f) {
		return "\"" + f.getPath() + "\"";
	}

	public static Result convert(Settings prof, File dfendDir) {
		List<String> notes = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		String mode = value(prof, "Extra", "profilemode");
		if (StringUtils.isNotEmpty(mode) && !mode.equalsIgnoreCase("DOSBox"))
			notes.add("ProfileMode=" + mode);

		// the settings, section by section (the first of two D-Fend names for the same setting wins)
		String section = null;
		java.util.Set<String> written = new java.util.HashSet<>();
		for (String[] s: SETTINGS) {
			String v = value(prof, s[0], s[1]);
			if (!v.isEmpty() && !written.add(s[0] + "." + s[2]))
				continue;
			if (v.isEmpty() || (s[1].equals("keyboardlayout") && v.equalsIgnoreCase("default")) || (s[1].equals("mapperfile")))
				continue;
			boolean bool = s[2].startsWith("?");
			String name = bool ? s[2].substring(1): s[2];
			if (bool)
				v = v.equals("1") || v.equalsIgnoreCase("true") ? "true": "false";
			if (!s[0].equals(section)) {
				sb.append('[').append(s[0]).append("]\n");
				section = s[0];
			}
			sb.append(name).append('=').append(v).append('\n');
		}

		// the drives, then the program; a drive letter with the host folder, to find the program's DOS path
		sb.append("[autoexec]\n");
		List<String[]> drives = new ArrayList<>(); // letter, host path
		// images on the same drive (e.g. the CDs of a game, each its own line in D-Fend) become one imgmount: to swap them in DOSBox
		java.util.Map<String, StringBuilder> imageLines = new java.util.LinkedHashMap<>();
		java.util.Set<String> imageDrives = new java.util.HashSet<>(); // letters and numbers of mounted images (to boot from)
		int count = Math.max(10, org.apache.commons.lang3.math.NumberUtils.toInt(value(prof, "Extra", "nrofmounts"), 0));
		for (int i = 0; i < count; i++) {
			String m = value(prof, "Extra", String.valueOf(i));
			if (m.isEmpty())
				continue;
			String[] parts = m.split(";", -1);
			if (parts.length < 3) {
				notes.add("mount " + i + ": " + m);
				continue;
			}
			String letter = parts[2].trim().toUpperCase();
			String type = parts[1].trim().toLowerCase();
			String label = parts.length > 4 ? parts[4].trim(): "";
			String labelOption = label.isEmpty() ? "": " -label " + label;
			if (type.equals("drive")) {
				File host = hostPath(dfendDir, parts[0]);
				sb.append("mount ").append(letter).append(' ').append(quoted(host)).append(labelOption).append('\n');
				drives.add(new String[] {letter, host.getPath()});
			} else if (type.equals("cdrom")) {
				sb.append("mount ").append(letter).append(' ').append(quoted(hostPath(dfendDir, parts[0]))).append(" -t cdrom").append(labelOption).append('\n');
			} else if (type.equals("floppy")) {
				sb.append("mount ").append(letter).append(' ').append(quoted(hostPath(dfendDir, parts[0]))).append(" -t floppy").append(labelOption).append('\n');
			} else if (type.contains("image")) {
				// several image files are separated by $; a hard disk image has its geometry in the 6th field (512,63,16,30),
				// and a drive number instead of a letter (0, 1: floppy; 2, 3: hard disk) mounts it without a file system, to boot
				StringBuilder files = new StringBuilder();
				for (String file: parts[0].split("\\$"))
					if (StringUtils.isNotBlank(file))
						files.append(' ').append(quoted(hostPath(dfendDir, file)));
				String t = type.contains("cd") ? "iso": type.contains("floppy") ? "floppy": "hdd";
				String geometry = parts.length > 5 ? parts[5].trim(): "";
				imageDrives.add(letter);
				String key = letter + " " + t;
				if (imageLines.containsKey(key)) {
					imageLines.get(key).append(files);
				} else {
					StringBuilder line = new StringBuilder("imgmount ").append(letter).append(files);
					imageLines.put(key, line);
					String options = " -t " + t + ((t.equals("hdd") && geometry.matches("\\d+,\\d+,\\d+,\\d+")) ? " -size " + geometry: "");
					sb.append("\u0000").append(key).append("\u0000").append(options).append('\n'); // filled in below, with all its files
				}
			} else {
				notes.add("mount " + letter + ": " + parts[1]);
			}
		}

		for (java.util.Map.Entry<String, StringBuilder> e: imageLines.entrySet()) {
			int at = sb.indexOf("\u0000" + e.getKey() + "\u0000");
			sb.replace(at, at + e.getKey().length() + 2, e.getValue().toString());
		}
		// devices of other DOSBox builds that D-Fend can switch on (DOSBox 0.74 does not have them)
		if (value(prof, "glide", "glide").equals("1") || value(prof, "glide", "glide").equalsIgnoreCase("true"))
			notes.add("glide");
		if (value(prof, "ne2000", "ne2000").equals("1"))
			notes.add("ne2000");
		if (value(prof, "Innova", "innova").equals("1"))
			notes.add("Innova");
		// D-Fend's own environment (e.g. PATH=Z:\;C:\NDN), unless it is just DOSBox's PATH=Z:\
		List<String> custom = new ArrayList<>();
		for (String env: lines(prof.getValue("Extra", "environment")))
			if (!env.replace(" ", "").equalsIgnoreCase("PATH=Z:\\"))
				custom.add("set " + env);
		custom.addAll(lines(prof.getValue("Extra", "autoexec")));
		// what D-Fend sets in its own way and DOSBox does not have as a setting
		if (value(prof, "dos", "4dos").equals("1"))
			notes.add("4DOS=1");
		if (value(prof, "dos", "dos32a").equals("1"))
			notes.add("DOS32A=1");
		if (!value(prof, "dos", "reporteddosversion").isEmpty() && !value(prof, "dos", "reporteddosversion").equalsIgnoreCase("default"))
			notes.add("ReportedDOSVersion=" + value(prof, "dos", "reporteddosversion"));
		if (!value(prof, "dos", "codepage").isEmpty() && !value(prof, "dos", "codepage").equalsIgnoreCase("default"))
			notes.add("codepage=" + value(prof, "dos", "codepage"));
		boolean override = value(prof, "Extra", "overridegamestart").equals("1");
		// boot from a drive: 0, 1 = A:, B:; 2, 3 = C:, D: (or a letter)
		String bootImage = value(prof, "Extra", "bootimage");
		String bootDrive = switch (bootImage.toUpperCase()) {
			case "0", "A" -> "a";
			case "1", "B" -> "b";
			case "2", "C" -> "c";
			case "3", "D" -> "d";
			default -> null;
		};
		if (!bootImage.isEmpty() && (bootDrive == null))
			notes.add("BootImage=" + bootImage);
		if ((bootDrive != null) && !imageDrives.contains(bootImage.toUpperCase())
				&& !imageDrives.contains(String.valueOf("abcd".indexOf(bootDrive))) && !imageDrives.contains(bootDrive.toUpperCase()))
			notes.add("BootImage=" + bootImage + " (no image)");
		// D-Fend's own lines run before the game: DBGL keeps them in its 'pre-launch' part of the autoexec
		if (!custom.isEmpty()) {
			sb.append("@REM PRE-LAUNCH\n");
			custom.forEach(l -> sb.append(l).append('\n'));
			sb.append("@REM /PRE-LAUNCH\n");
		}
		if (override)
			notes.add("Overridegamestart=1");
		String exe = value(prof, "Extra", "exe");
		if (!override && !exe.isEmpty()) {
			File host = hostPath(dfendDir, exe);
			// the drive whose folder holds the program (the deepest one, e.g. E: on D:\Games\AOD\ rather than C:)
			String dos = null;
			int best = -1;
			for (String[] d: drives) {
				String root = StringUtils.removeEnd(new File(d[1]).getPath(), File.separator);
				String path = host.getPath();
				if (path.toLowerCase().startsWith(root.toLowerCase() + File.separator) && (root.length() > best)) {
					String rest = path.substring(root.length() + 1).replace(File.separatorChar, '\\');
					dos = d[0] + ":\\" + rest;
					best = root.length();
				}
			}
			if (dos == null) {
				notes.add("Exe=" + exe);
			} else {
				String dir = StringUtils.substringBeforeLast(dos.substring(2), "\\");
				String file = StringUtils.substringAfterLast(dos, "\\");
				String params = value(prof, "Extra", "gameparameters");
				String prefix = value(prof, "Extra", "loadhigh").equals("1") ? "lh ": "";
				if (value(prof, "Extra", "loadfix").equals("1"))
					prefix = "loadfix -" + StringUtils.defaultIfEmpty(value(prof, "Extra", "loadfixval"), "64") + " ";
				sb.append(dos, 0, 2).append('\n').append("cd ").append(dir.isEmpty() ? "\\": dir).append('\n').append(prefix).append(file)
						.append(params.isEmpty() ? "": " " + params).append('\n');
			}
		}
		if (bootDrive != null)
			sb.append("boot -l ").append(bootDrive).append('\n');
		else if (value(prof, "Extra", "closeonexit").equals("1"))
			sb.append("exit\n");
		return new Result(sb.toString(), notes);
	}
}
