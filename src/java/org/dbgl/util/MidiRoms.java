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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.conf.Configuration;
import org.dbgl.service.FileLocationService;


/**
 * Which ROMs of a Roland MT-32/CM-32L and Sound Canvas the DOSBox version of a profile finds: in the folder the settings name
 * (relative to where DBGL starts DOSBox), else in the folders DOSBox Staging looks in by itself (beside its program and its
 * configuration file, and its configuration folder of the user). DOSBox recognises the ROMs by their checksums, not their
 * names: what the names do not tell is counted as a ROM file.
 */
public final class MidiRoms {

	/** what was found: the folder (null if none of the folders is there), the models told by the names, and all ROM files */
	public record Found(File dir, List<String> models, int files) {
	}

	private MidiRoms() {
	}

	/** the ROMs of an MT-32/CM-32L: setting "romdir" in [mt32] (DOSBox Staging) or "mt32.romdir" in [midi] (DOSBox-X) */
	public static Found mt32(DosboxVersion db, Configuration combined, boolean dosboxX) {
		String set = dosboxX ? combined.getValue("midi", "mt32.romdir"): combined.getValue("mt32", "romdir");
		return look(dirs(db, set, "mt32-roms", dosboxX ? List.of(): List.of("mt32-rom-data")), true);
	}

	/** the ROMs of a Sound Canvas (DOSBox Staging 0.83): setting "soundcanvas_rom_dir" in [soundcanvas] */
	public static Found soundCanvas(DosboxVersion db, Configuration combined) {
		return look(dirs(db, combined.getValue("soundcanvas", "soundcanvas_rom_dir"), "soundcanvas-roms", List.of()), false);
	}

	/**
	 * The folder ULTRASND (with the patch files of the Gravis Ultrasound in its folder MIDI) beside the program of the DOSBox
	 * version or its configuration file, whatever its case; files: the patch files (.pat) in it.
	 */
	public static Found ultrasnd(DosboxVersion db) {
		if (db == null)
			return new Found(null, List.of(), 0);
		Set<File> parents = new LinkedHashSet<>();
		File exe = db.getCanonicalExecutable();
		if ((exe != null) && (exe.getParentFile() != null))
			parents.add(exe.getParentFile());
		File conf = db.getConfigurationCanonicalFile();
		if ((conf != null) && (conf.getParentFile() != null))
			parents.add(conf.getParentFile());
		for (File parent: parents) {
			File dir = child(parent, "ultrasnd");
			if (dir == null)
				continue;
			File midi = child(dir, "midi");
			File[] pats = midi == null ? null: midi.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".pat"));
			String version = patchVersion(dir);
			return new Found(dir, version == null ? List.of(): List.of(version), pats == null ? 0: pats.length);
		}
		return new Found(null, List.of(), 0);
	}

	private static final java.util.regex.Pattern PATCH_VERSION = java.util.regex.Pattern.compile("(?i)^#\\s*Ver:\\s*([0-9][0-9.]*)");

	/** the version of the patch library, as its ULTRASND.INI tells it in its head ("# Ver:     2.1"); null if not */
	public static String patchVersion(File ultrasnd) {
		File[] inis = ultrasnd.listFiles(f -> f.isFile() && f.getName().equalsIgnoreCase("ultrasnd.ini"));
		if ((inis == null) || (inis.length == 0) || (inis[0].length() > 1_000_000))
			return null;
		try {
			for (String line: java.nio.file.Files.readAllLines(inis[0].toPath(), java.nio.charset.StandardCharsets.ISO_8859_1)) {
				java.util.regex.Matcher m = PATCH_VERSION.matcher(line.trim());
				if (m.find())
					return m.group(1);
				if (line.trim().startsWith("["))
					return null; // past the head
			}
		} catch (java.io.IOException e) {
			// no version then
		}
		return null;
	}

	private static File child(File parent, String name) {
		File[] dirs = parent.listFiles(f -> f.isDirectory() && f.getName().equalsIgnoreCase(name));
		return (dirs == null) || (dirs.length == 0) ? null: dirs[0];
	}

	/** DOSBox Staging plays a Sound Canvas with a CLAP plugin in its folder "plugins" (beside the program, or in its configuration folder) */
	public static boolean soundCanvasPlugin(DosboxVersion db) {
		for (File d: dirs(db, null, "plugins", List.of())) {
			File[] files = d.listFiles((f, n) -> n.toLowerCase(Locale.ROOT).endsWith(".clap"));
			if ((files != null) && (files.length > 0))
				return true;
		}
		return false;
	}

	private static List<File> dirs(DosboxVersion db, String set, String name, List<String> shared) {
		Set<File> result = new LinkedHashSet<>();
		if (StringUtils.isNotBlank(set)) { // as set: nothing else is looked at by DBGL (DOSBox Staging looks further, as below)
			File f = new File(set.trim());
			result.add(f.isAbsolute() ? f: new File(FileLocationService.getInstance().getDosroot(), set.trim()));
		}
		if (db != null) {
			File exe = db.getCanonicalExecutable();
			if ((exe != null) && (exe.getParentFile() != null))
				result.add(new File(exe.getParentFile(), name));
			File conf = db.getConfigurationCanonicalFile();
			if ((conf != null) && (conf.getParentFile() != null))
				result.add(new File(conf.getParentFile(), name));
		}
		String home = System.getProperty("user.home");
		if (SystemUtils.IS_WINDOWS) {
			String local = System.getenv("LOCALAPPDATA");
			if (local != null)
				result.add(new File(new File(local, "DOSBox"), name));
		} else if (SystemUtils.IS_OSX) {
			result.add(new File(home, "Library/Preferences/DOSBox/" + name));
		} else if (home != null) {
			result.add(new File(home, ".config/dosbox/" + name));
			for (String s: shared) {
				result.add(new File(home, ".local/share/" + s));
				result.add(new File("/usr/local/share/" + s));
				result.add(new File("/usr/share/" + s));
			}
		}
		return new ArrayList<>(result);
	}

	private static final java.util.regex.Pattern VERSION = java.util.regex.Pattern.compile("(?i)(?:^|[^0-9.])v?(\\d\\.\\d{1,2}(?:\\.\\d)?)(?:[^0-9.]|$)");

	/**
	 * The ROMs in the first of the folders that has any, also in its subfolders (DOSBox Staging looks there too, e.g.
	 * soundcanvas-roms/SC-55-v1.20): by model, with the versions the names of the files or their folders tell.
	 */
	private static Found look(List<File> dirs, boolean mt32) {
		for (File d: dirs) {
			java.util.Map<String, java.util.TreeSet<String>> models = new java.util.LinkedHashMap<>();
			int[] roms = {0};
			collect(d, 0, mt32, models, roms);
			if (roms[0] > 0) {
				List<String> told = new ArrayList<>();
				models.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(e -> told.add(e.getValue().isEmpty() ? e.getKey(): e.getKey() + ": v" + String.join(", v", e.getValue())));
				return new Found(d, told, roms[0]);
			}
		}
		return new Found(null, List.of(), 0);
	}

	/** at most this many entries looked at per folder set (a ROM folder set to a whole drive must not hold up the dialog) */
	private static final int MAX_ENTRIES = 2000;

	private static void collect(File dir, int depth, boolean mt32, java.util.Map<String, java.util.TreeSet<String>> models, int[] roms) {
		collect(dir, depth, mt32, models, roms, new int[1]);
	}

	private static void collect(File dir, int depth, boolean mt32, java.util.Map<String, java.util.TreeSet<String>> models, int[] roms, int[] seen) {
		File[] files = dir.listFiles();
		if (files == null)
			return;
		for (File f: files) {
			if (++seen[0] > MAX_ENTRIES)
				return;
			String n = f.getName().toLowerCase(Locale.ROOT);
			if (f.isDirectory()) {
				if (depth < 2)
					collect(f, depth + 1, mt32, models, roms, seen);
				continue;
			}
			if (n.startsWith(".") || !(n.endsWith(".rom") || n.endsWith(".bin")))
				continue;
			roms[0]++;
			// the model: by the name of the file, else by the name of its folder (SC-55mk2-v1.01/rom1.bin)
			String folder = depth > 0 ? f.getParentFile().getName().toLowerCase(Locale.ROOT): "";
			String model = model(n, mt32);
			if (model == null)
				model = model(folder, mt32);
			if (model == null)
				continue;
			java.util.TreeSet<String> versions = models.computeIfAbsent(model, k -> new java.util.TreeSet<>());
			java.util.regex.Matcher m = VERSION.matcher(FilenameUtils.getBaseName(n));
			if (!m.find())
				m = VERSION.matcher(folder);
			else
				m.reset();
			if (m.find())
				versions.add(m.group(1));
		}
	}

	private static String model(String name, boolean mt32) {
		if (mt32) {
			if (name.contains("cm32l") || name.contains("cm-32l") || name.contains("lapc"))
				return "CM-32L";
			if (name.contains("mt32") || name.contains("mt-32"))
				return "MT-32";
			return null;
		}
		if (name.contains("mk2") || name.contains("mkii"))
			return "SC-55mk2";
		if (name.contains("sc55") || name.contains("sc-55"))
			return "SC-55";
		return null;
	}
}
