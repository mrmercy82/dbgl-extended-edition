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
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.conf.Configuration;
import org.dbgl.service.FileLocationService;


/**
 * Which ROMs of a Roland MT-32/CM-32L and Sound Canvas the DOSBox version of a profile finds, recognised as DOSBox does it: by
 * their checksums, not their names.
 * <ul>
 * <li>MT-32/CM-32L, DOSBox Staging: any file in the folder of the setting "romdir" (default mt32-roms, relative to where DBGL
 * starts DOSBox) or of the folders DOSBox Staging looks in by itself, not in subfolders; a model needs its control ROM and its
 * PCM ROM, each whole or in two halves (the SHA-1 checksums of Munt, the MT-32 emulation of DOSBox).</li>
 * <li>MT-32/CM-32L, DOSBox-X: the files CM32L_CONTROL.ROM and CM32L_PCM.ROM or MT32_CONTROL.ROM and MT32_PCM.ROM, by these
 * names.</li>
 * <li>Sound Canvas, DOSBox Staging 0.83 (with the CLAP plugin Nuked-SC55): a folder per model (SC-55-v1.20 ...) in those
 * folders, its files with the SHA-256 checksums of the plugin.</li>
 * </ul>
 */
public final class MidiRoms {

	/**
	 * what was found: the folder (the first with ROMs; null if there are none), the models with their versions ("CM-32L
	 * v1.02"), the number of ROM files; incomplete: ROMs there, but none of a model whole (or for DOSBox-X: by other names)
	 */
	public record Found(File dir, List<String> models, int files, boolean incomplete, boolean otherNames) {
	}

	private MidiRoms() {
	}

	// ------------------------------------------------------------------ MT-32 / CM-32L (Munt, mt32emu/src/ROMInfo.cpp)

	private enum Part {
		CTRL, CTRL_A, CTRL_B, PCM, PCM_L, PCM_H
	}

	/** one ROM file: its model (the control ROM: "MT-32 v1.07"; the PCM ROM: "MT-32", "CM-32L"), what part of it */
	private record Rom(String model, Part part) {
	}

	private static final Map<String, Rom> MT32_ROMS = new HashMap<>();

	static {
		String[][] roms = {
			// control ROMs, whole and in halves
			{"9cd4858014c4e8a9dff96053f784bfaac1092a2e", "MT-32 v1.04", "CTRL_A"}, {"fe8db469b5bfeb37edb269fd47e3ce6d91014652", "MT-32 v1.04", "CTRL_B"},
			{"5a5cb5a77d7d55ee69657c2f870416daed52dea7", "MT-32 v1.04", "CTRL"}, {"57a09d80d2f7ca5b9734edbe9645e6e700f83701", "MT-32 v1.05", "CTRL_A"},
			{"52e3c6666db9ef962591a8ee99be0cde17f3a6b6", "MT-32 v1.05", "CTRL_B"}, {"e17a3a6d265bf1fa150312061134293d2b58288c", "MT-32 v1.05", "CTRL"},
			{"cc83bf23cee533097fb4c7e2c116e43b50ebacc8", "MT-32 v1.06", "CTRL_A"}, {"bf4f15666bc46679579498386704893b630c1171", "MT-32 v1.06", "CTRL_B"},
			{"a553481f4e2794c10cfe597fef154eef0d8257de", "MT-32 v1.06", "CTRL"}, {"13f06b38f0d9e0fc050b6503ab777bb938603260", "MT-32 v1.07", "CTRL_A"},
			{"c55e165487d71fa88bd8c5e9c083bc456c1a89aa", "MT-32 v1.07", "CTRL_B"}, {"b083518fffb7f66b03c23b7eb4f868e62dc5a987", "MT-32 v1.07", "CTRL"},
			{"11a6ae5d8b6ee328b371af7f1e40b82125aa6b4d", "MT-32 BlueRidge", "CTRL_A"}, {"e0934320d7cbb5edfaa29e0d01ae835ef620085b", "MT-32 BlueRidge", "CTRL_B"},
			{"7b8c2a5ddb42fd0732e2f22b3340dcf5360edf92", "MT-32 BlueRidge", "CTRL"}, {"5837064c9df4741a55f7c4d8787ac158dff2d3ce", "MT-32 v2.03", "CTRL"},
			{"2c16432b6c73dd2a3947cba950a0f4c19d6180eb", "MT-32 v2.04", "CTRL"}, {"2869cf4c235d671668cfcb62415e2ce8323ad4ed", "MT-32 v2.06", "CTRL"},
			{"47b52adefedaec475c925e54340e37673c11707c", "MT-32 v2.07", "CTRL"}, {"73683d585cd6948cc19547942ca0e14a0319456d", "CM-32L v1.00", "CTRL"},
			{"a439fbb390da38cada95a7cbb1d6ca199cd66ef8", "CM-32L v1.02", "CTRL"}, {"dc1c5b1b90a4646d00f7daf3679733c7badc7077", "CM-32LN v1.00", "CTRL"},
			// PCM ROMs: the one of the MT-32 is also the lower half of the one of the CM-32L
			{"3a1e19b0cd4036623fd1d1d11f5f25995585962b", "MT-32", "PCM_L"}, {"2cadb99d21a6a4a6f5b61b6218d16e9b43f61d01", "MT-32", "PCM_H"},
			{"f6b1eebc4b2d200ec6d3d21d51325d5b48c60252", "MT-32", "PCM"}, {"3ad889fde5db5b6437cbc2eb6e305312fec3df93", "CM-32L", "PCM_H"},
			{"289cc298ad532b702461bfc738009d9ebe8025ea", "CM-32L", "PCM"}};
		for (String[] r: roms)
			MT32_ROMS.put(r[0], new Rom(r[1], Part.valueOf(r[2])));
	}

	/** sizes of the MT-32 ROM files, the only files whose checksums are worth computing */
	private static final Set<Long> MT32_SIZES = Set.of(32768L, 65536L, 131072L, 262144L, 524288L, 1048576L);

	/**
	 * The ROMs of an MT-32/CM-32L: setting "romdir" in [mt32] (DOSBox Staging) or "mt32.romdir" in [midi] (DOSBox-X); what the
	 * DOSBox version can use, as it looks for them.
	 */
	public static Found mt32(DosboxVersion db, Configuration combined, boolean dosboxX) {
		if (dosboxX)
			return mt32DosboxX(db, combined.getValue("midi", "mt32.romdir"));
		String set = combined.getValue("mt32", "romdir");
		List<File> dirs = stagingDirs(db, StringUtils.isBlank(set) ? "mt32-roms": set, "mt32-roms", "mt32-rom-data", "C:\\mt32-rom-data");
		if (SystemUtils.IS_OSX)
			dirs.add(new File(System.getProperty("user.home"), "Library/Audio/Sounds/MT32-Roms"));
		File first = null, unknownDir = null;
		Set<String> models = new TreeSet<>();
		int files = 0, unknown = 0;
		for (File d: dirs) {
			File[] all = d.listFiles(File::isFile);
			if (all == null)
				continue;
			List<Rom> roms = new ArrayList<>();
			for (File f: all)
				if (MT32_SIZES.contains(f.length())) {
					Rom rom = MT32_ROMS.get(checksum(f, "SHA-1"));
					if (rom != null) {
						roms.add(rom);
					} else if (unknownDir == null || unknownDir.equals(d)) {
						unknownDir = d; // of the size of a ROM, but none DOSBox knows (a bad copy, another device)
						unknown++;
					}
				}
			if (roms.isEmpty())
				continue;
			files += roms.size();
			if (first == null)
				first = d;
			models.addAll(mt32Models(roms));
		}
		if ((files == 0) && (unknown > 0))
			return new Found(unknownDir, List.of(), unknown, true, false);
		return new Found(first, new ArrayList<>(models), files, (files > 0) && models.isEmpty(), false);
	}

	/** the MT-32/CM-32L models whole in a folder with files of these SHA-1 checksums */
	public static Set<String> mt32ModelsOfSums(java.util.Collection<String> sha1) {
		List<Rom> roms = new ArrayList<>();
		for (String sum: sha1)
			if (MT32_ROMS.containsKey(sum))
				roms.add(MT32_ROMS.get(sum));
		return mt32Models(roms);
	}

	/** the models whole in one folder: a control ROM (whole or both halves) and the PCM ROM of its kind (whole or both halves) */
	static Set<String> mt32Models(List<Rom> roms) {
		Set<String> controls = new TreeSet<>(), halfA = new TreeSet<>(), halfB = new TreeSet<>();
		boolean mt32Pcm = false, mt32L = false, mt32H = false, cm32lPcm = false, cm32lH = false;
		for (Rom r: roms) {
			switch (r.part()) {
				case CTRL -> controls.add(r.model());
				case CTRL_A -> halfA.add(r.model());
				case CTRL_B -> halfB.add(r.model());
				case PCM -> {
					if (r.model().equals("MT-32"))
						mt32Pcm = true;
					else
						cm32lPcm = true;
				}
				case PCM_L -> mt32L = true;
				case PCM_H -> {
					if (r.model().equals("MT-32"))
						mt32H = true;
					else
						cm32lH = true;
				}
			}
		}
		halfA.retainAll(halfB);
		controls.addAll(halfA);
		boolean forMt32 = mt32Pcm || (mt32L && mt32H);
		boolean forCm32l = cm32lPcm || (mt32Pcm && cm32lH); // the lower half of the CM-32L is the whole PCM ROM of the MT-32
		Set<String> result = new TreeSet<>();
		for (String c: controls)
			if (c.startsWith("CM-32L") ? forCm32l: forMt32)
				result.add(c);
		return result;
	}

	/** DOSBox-X: by the names of the files, in the folder set (else where DOSBox-X is started from), then beside its program */
	private static Found mt32DosboxX(DosboxVersion db, String set) {
		List<File> dirs = new ArrayList<>();
		dirs.add(StringUtils.isBlank(set) ? FileLocationService.getInstance().getDosroot(): resolve(set.trim()));
		File exe = db == null ? null: db.getCanonicalExecutable();
		if ((exe != null) && (exe.getParentFile() != null))
			dirs.add(exe.getParentFile());
		File first = null, unknownDir = null;
		Set<String> models = new TreeSet<>();
		int files = 0, unknown = 0;
		boolean otherNames = false;
		for (File d: dirs) {
			File[] all = d.listFiles(File::isFile);
			if (all == null)
				continue;
			List<Rom> named = new ArrayList<>();
			int known = 0;
			for (File f: all) {
				if (!MT32_SIZES.contains(f.length()))
					continue;
				Rom rom = MT32_ROMS.get(checksum(f, "SHA-1"));
				if (rom == null) {
					// under a name DOSBox-X reads, but not a ROM it can use
					String n = SystemUtils.IS_WINDOWS ? f.getName().toUpperCase(Locale.ROOT): f.getName();
					if (List.of("CM32L_CONTROL.ROM", "CM32L_PCM.ROM", "MT32_CONTROL.ROM", "MT32_PCM.ROM").contains(n) && ((unknownDir == null) || unknownDir.equals(d))) {
						unknownDir = d;
						unknown++;
					}
					continue;
				}
				known++;
				String n = SystemUtils.IS_WINDOWS ? f.getName().toUpperCase(Locale.ROOT): f.getName();
				if (List.of("CM32L_CONTROL.ROM", "CM32L_PCM.ROM", "MT32_CONTROL.ROM", "MT32_PCM.ROM").contains(n))
					named.add(rom);
			}
			if (known == 0)
				continue;
			files += known;
			if (first == null)
				first = d;
			Set<String> here = mt32Models(named);
			models.addAll(here);
			// ROMs under other names ("mt32_ctrl_1_07.rom" as for DOSBox Staging), not just too few of them
			otherNames |= here.isEmpty() && (named.size() < known);
		}
		if ((files == 0) && (unknown > 0))
			return new Found(unknownDir, List.of(), unknown, true, false);
		return new Found(first, new ArrayList<>(models), files, (files > 0) && models.isEmpty() && !otherNames, models.isEmpty() && otherNames);
	}

	// ------------------------------------------------------------------ Sound Canvas (Nuked-SC55 CLAP, standard_romsets.cpp)

	/** the models in the order of DOSBox Staging: the folder the plugin looks for, the checksums all its files must have */
	private static final Map<String, String[]> SC_MODELS = new LinkedHashMap<>();

	static {
		String w1 = "5655509a531804f97ea2d7ef05b8fec20ebf46216b389a84c44169257a4d2007", w2 = "c655b159792d999b90df9e4fa782cf56411ba1eaa0bb3ac2bdaf09e1391006b1",
				w3 = "334b2d16be3c2362210fdbec1c866ad58badeb0f84fd9bf5d0ac599baf077cc2"; // the wave ROMs of all SC-55 models
		SC_MODELS.put("SC-55-v1.00", new String[] {"b4ecf44bc0520322b0d114d397951d3bf92ca6fa51d0d27b2407df58a6be2efe", "014e2e21ea30de7a1e4f1cdea14dd9a719960535e257a9e40e98dbb1a5870226", w1, w2, w3});
		SC_MODELS.put("SC-55-v1.10", new String[] {"2fe88ec39f3ef4b1de8cdf74527419467975c47f7aacfcd07605e01d54bd89b5", "ec064d6c4fc70ec990911089d966043cb819fba0e26e6f6afdd0a05e5301b91b", w1, w2, w3});
		SC_MODELS.put("SC-55-v1.20", new String[] {"7e1bacd1d7c62ed66e465ba05597dcd60dfc13fc23de0287fdbce6cf906c6544", "22ce6ca59e6332143b335525e81fab501ea6fccce4b7e2f3bfc2cc8bf6612ff6", w1, w2, w3});
		SC_MODELS.put("SC-55-v1.21", new String[] {"7e1bacd1d7c62ed66e465ba05597dcd60dfc13fc23de0287fdbce6cf906c6544", "effc6132d68f7e300aaef915ccdd08aba93606c22d23e580daf9ea6617913af1", w1, w2, w3});
		SC_MODELS.put("SC-55-v2.00", new String[] {"24a65c97cdbaa847d6f59193523ce63c73394b4b693a6517ee79441f2fb8a3ee", "f5dac35d450ab986570a209dff3816eec75cee669e161f54b51224b467dd0bcc", w1, w2, w3});
		SC_MODELS.put("SC-55mk2-v1.01", new String[] {"8a1eb33c7599b746c0c50283e4349a1bb1773b5c0ec0e9661219bf6c067d2042", "a4c9fd821059054c7e7681d61f49ce6f42ed2fe407a7ec1ba0dfdc9722582ce0",
			"b0b5f865a403f7308b4be8d0ed3ba2ed1c22db881b8a8326769dea222f6431d8", "c6429e21b9b3a02fbd68ef0b2053668433bee0bccd537a71841bc70b8874243b",
			"5b753f6cef4cfc7fcafe1430fecbb94a739b874e55356246a46abe24097ee491"});
	}

	/** larger files are never ROMs of the plugin (it skips them too) */
	private static final long SC_MAX_SIZE = 0x800000;

	/**
	 * The ROMs of a Sound Canvas (DOSBox Staging 0.83): setting "soundcanvas_rom_dir" in [soundcanvas] (default
	 * soundcanvas-roms), the folders DOSBox Staging looks in by itself, and Nuked-SC55-Resources/ROMs beside the plugin.
	 */
	public static Found soundCanvas(DosboxVersion db, Configuration combined) {
		String set = combined.getValue("soundcanvas", "soundcanvas_rom_dir");
		List<File> bases = stagingDirs(db, StringUtils.isBlank(set) ? "soundcanvas-roms": set, "soundcanvas-roms", "soundcanvas-rom-data", null);
		for (File plugins: stagingDirs(db, null, "plugins", null, null))
			bases.add(new File(new File(plugins, "Nuked-SC55-Resources"), "ROMs"));
		File first = null;
		Set<String> models = new LinkedHashSet<>();
		int files = 0, anyFiles = 0;
		boolean any = false;
		File firstAny = null;
		for (File base: bases) {
			for (Map.Entry<String, String[]> m: SC_MODELS.entrySet()) {
				File dir = child(base, m.getKey());
				File[] all = dir == null ? null: dir.listFiles(f -> f.isFile() && (f.length() <= SC_MAX_SIZE) && !f.getName().startsWith("."));
				if ((all == null) || (all.length == 0))
					continue;
				if (!any)
					firstAny = base;
				any = true;
				if (base.equals(firstAny))
					anyFiles += all.length; // told when no model is whole
				Set<String> sums = new java.util.HashSet<>();
				for (File f: all)
					sums.add(checksum(f, "SHA-256"));
				if (!sums.containsAll(List.of(m.getValue())))
					continue;
				files += m.getValue().length;
				if (first == null)
					first = base;
				models.add(m.getKey().replace("-v", " v"));
			}
		}
		return new Found(first != null ? first: firstAny, new ArrayList<>(models), models.isEmpty() ? anyFiles: files, any && models.isEmpty(), false);
	}

	/** DOSBox Staging plays a Sound Canvas with a CLAP plugin in its folder "plugins" (beside the program, or in its configuration folder) */
	public static boolean soundCanvasPlugin(DosboxVersion db) {
		for (File d: stagingDirs(db, null, "plugins", null, null)) {
			File[] files = d.listFiles((f, n) -> n.toLowerCase(Locale.ROOT).endsWith(".clap"));
			if ((files != null) && (files.length > 0))
				return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ the folders of DOSBox Staging (src/midi/mt32.cpp, soundcanvas.cpp)

	/**
	 * Where DOSBox Staging looks, in its order: the folder set (relative to where DBGL starts DOSBox), its configuration folder
	 * (beside its program if dosbox-staging.conf is there: portable), and the folders of the system.
	 *
	 * @param set the folder set, or null for none
	 * @param name the folder in the configuration folder
	 * @param shared the folder in the data folders of Linux (XDG), or null
	 * @param windows a folder of its own on Windows, or null
	 */
	private static List<File> stagingDirs(DosboxVersion db, String set, String name, String shared, String windows) {
		Set<File> result = new LinkedHashSet<>();
		if (StringUtils.isNotBlank(set))
			result.add(resolve(set.trim()));
		File exe = db == null ? null: db.getCanonicalExecutable();
		File exeDir = (exe != null) ? exe.getParentFile(): null;
		boolean portable = (exeDir != null) && new File(exeDir, "dosbox-staging.conf").isFile();
		String home = System.getProperty("user.home");
		File configDir;
		if (portable)
			configDir = exeDir;
		else if (SystemUtils.IS_WINDOWS)
			configDir = System.getenv("LOCALAPPDATA") == null ? null: new File(System.getenv("LOCALAPPDATA"), "DOSBox");
		else if (SystemUtils.IS_OSX)
			configDir = new File(home, "Library/Preferences/DOSBox");
		else
			configDir = new File(StringUtils.defaultIfBlank(System.getenv("XDG_CONFIG_HOME"), home + "/.config"), "dosbox");
		if (name.equals("plugins") && (exeDir != null))
			result.add(new File(exeDir, name)); // the plugins: in the folder of the program too
		if (SystemUtils.IS_WINDOWS || SystemUtils.IS_OSX) {
			if (configDir != null)
				result.add(new File(configDir, name));
			if (SystemUtils.IS_WINDOWS && (windows != null))
				result.add(new File(windows));
			if (SystemUtils.IS_OSX && (shared != null)) {
				result.add(new File("/usr/local/share/" + shared));
				result.add(new File("/usr/share/" + shared));
			}
		} else {
			String dataHome = StringUtils.defaultIfBlank(System.getenv("XDG_DATA_HOME"), home + "/.local/share");
			if (shared != null) {
				result.add(new File(dataHome, "dosbox/" + name));
				result.add(new File(dataHome, shared));
				for (String d: StringUtils.defaultIfBlank(System.getenv("XDG_DATA_DIRS"), "/usr/local/share:/usr/share").split(":"))
					if (!d.isBlank())
						result.add(new File(d, shared));
			}
			result.add(new File(configDir, name));
		}
		return new ArrayList<>(result);
	}

	/** a folder as DOSBox reads it: "~" for the home folder, relative to where DBGL starts DOSBox */
	private static File resolve(String path) {
		String p = path;
		if (p.equals("~") || p.startsWith("~/") || p.startsWith("~\\"))
			p = System.getProperty("user.home") + p.substring(1);
		File f = new File(p);
		return f.isAbsolute() ? f: new File(FileLocationService.getInstance().getDosroot(), p);
	}

	// ------------------------------------------------------------------ checksums

	/** computed once per file (path, size, time): the ROM frame is set anew with every change of the DOSBox version */
	private static final Map<String, String> SUMS = new ConcurrentHashMap<>();

	static String checksum(File f, String algorithm) {
		String key = algorithm + "|" + f.getAbsolutePath() + "|" + f.length() + "|" + f.lastModified();
		return SUMS.computeIfAbsent(key, k -> {
			try (InputStream in = Files.newInputStream(f.toPath())) {
				MessageDigest md = MessageDigest.getInstance(algorithm);
				byte[] buf = new byte[65536];
				for (int n; (n = in.read(buf)) > 0;)
					md.update(buf, 0, n);
				return HexFormat.of().formatHex(md.digest());
			} catch (IOException | NoSuchAlgorithmException e) {
				return "";
			}
		});
	}

	// ------------------------------------------------------------------ Gravis Ultrasound

	/**
	 * The folder ULTRASND (with the patch files of the Gravis Ultrasound in its folder MIDI) beside the program of the DOSBox
	 * version or its configuration file, whatever its case; files: the patch files (.pat) in it; models: the version of the
	 * patch library, if its ULTRASND.INI tells it.
	 */
	public static Found ultrasnd(DosboxVersion db) {
		if (db == null)
			return new Found(null, List.of(), 0, false, false);
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
			return new Found(dir, version == null ? List.of(): List.of(version), pats == null ? 0: pats.length, false, false);
		}
		return new Found(null, List.of(), 0, false, false);
	}

	private static final java.util.regex.Pattern PATCH_VERSION = java.util.regex.Pattern.compile("(?i)^#\\s*Ver:\\s*([0-9][0-9.]*)");

	/** the version of the patch library, as its ULTRASND.INI tells it in its head ("# Ver:     2.1"); null if not */
	public static String patchVersion(File ultrasnd) {
		File[] inis = ultrasnd.listFiles(f -> f.isFile() && f.getName().equalsIgnoreCase("ultrasnd.ini"));
		if ((inis == null) || (inis.length == 0) || (inis[0].length() > 1_000_000))
			return null;
		try {
			for (String line: Files.readAllLines(inis[0].toPath(), java.nio.charset.StandardCharsets.ISO_8859_1)) {
				java.util.regex.Matcher m = PATCH_VERSION.matcher(line.trim());
				if (m.find())
					return m.group(1);
				if (line.trim().startsWith("["))
					return null; // past the head
			}
		} catch (IOException e) {
			// no version then
		}
		return null;
	}

	/** the subfolder of that name, whatever its case (as on Windows) */
	private static File child(File parent, String name) {
		File[] dirs = parent.listFiles(f -> f.isDirectory() && f.getName().equalsIgnoreCase(name));
		return (dirs == null) || (dirs.length == 0) ? null: dirs[0];
	}
}
