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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.conf.mount.ImageMount;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.util.ProfileChecks.Problem;


/**
 * alpha 50: what "Check profiles" tells beyond single lines: settings that are valid each but do not go together, what the
 * game itself is set to (the files its setup wrote) against the profile, what lies in the folder of the game, and a short
 * list of games with a documented need.
 */
public final class GameHints {

	private GameHints() {
	}

	public static final String SB = "sb", GUS = "gus", MT32 = "mt32";

	/**
	 * what a game is set to: the file that says so, the device (SB, GUS, MT32), and for a Sound Blaster its port, IRQ and DMA
	 * channels as far as the file names them (null: not named)
	 */
	public record Sound(String file, String device, Integer port, Integer irq, Integer dma, Integer hdma, String variable, String line) {

		/** what a file of the game says */
		public Sound(String file, String device, Integer port, Integer irq, Integer dma, Integer hdma) {
			this(file, device, port, irq, dma, hdma, null, null);
		}

		/** as in the BLASTER variable: "A220 I5 D1 H5" */
		public String blaster() {
			return blasterOf(port, irq, dma, hdma);
		}
	}

	static String blasterOf(Integer port, Integer irq, Integer dma, Integer hdma) {
		List<String> parts = new ArrayList<>();
		if (port != null)
			parts.add("A" + Integer.toHexString(port).toUpperCase(Locale.ROOT));
		if (irq != null)
			parts.add("I" + irq);
		if (dma != null)
			parts.add("D" + dma);
		if (hdma != null)
			parts.add("H" + hdma);
		return String.join(" ", parts);
	}

	private static Integer number(String s, int radix) {
		if (s == null)
			return null;
		String t = s.trim().toLowerCase(Locale.ROOT);
		if (t.endsWith("h")) {
			t = t.substring(0, t.length() - 1);
			radix = 16;
		} else if (t.startsWith("0x")) {
			t = t.substring(2);
			radix = 16;
		}
		try {
			int n = Integer.parseInt(t, radix);
			return n > 0 ? n: null; // -1 and 0: not set
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * DIG.INI and MDI.INI, written by the setup of games with the Miles Sound System (AIL 3): "DRIVER SB16.DIG", "IO_ADDR
	 * 220h", "IRQ 5", "DMA_8_bit 1", "DMA_16_bit 5"; lines with ";" are comments.
	 */
	public static Sound milesIni(String file, String text) {
		Map<String, String> values = new LinkedHashMap<>();
		for (String line: text.split("\\R")) {
			String t = line.trim();
			if (t.isEmpty() || t.startsWith(";"))
				continue;
			String[] kv = t.split("\\s+", 2);
			if (kv.length == 2)
				values.putIfAbsent(kv[0].toUpperCase(Locale.ROOT), kv[1].trim());
		}
		String driver = StringUtils.defaultString(values.get("DRIVER")).toUpperCase(Locale.ROOT);
		driver = driver.substring(Math.max(driver.lastIndexOf('\\'), driver.lastIndexOf('/')) + 1);
		if (driver.startsWith("MT32"))
			return new Sound(file, MT32, null, null, null, null);
		if (driver.startsWith("ULTRA") || driver.startsWith("GUS"))
			return new Sound(file, GUS, null, null, null, null);
		if (!driver.startsWith("SB") || !driver.endsWith(".DIG"))
			return null;
		return new Sound(file, SB, number(values.get("IO_ADDR"), 16), number(values.get("IRQ"), 10), number(values.get("DMA_8_BIT"), 10), number(values.get("DMA_16_BIT"), 10));
	}

	/**
	 * DEFAULT.CFG of Doom and the games made with it (Heretic, Hexen, Strife): "snd_sfxdevice 3" is the Sound Blaster, 5 the
	 * Gravis Ultrasound. Its "snd_sbport 544", "snd_sbirq 5", "snd_sbdma 1" are not compared: the file comes with IRQ 5 from
	 * the makers of the game (idsetup), and DBGL cannot tell whether the game takes these values or looks for the card itself.
	 */
	public static Sound doomCfg(String file, String text) {
		Map<String, String> values = new LinkedHashMap<>();
		for (String line: text.split("\\R")) {
			String[] kv = line.trim().split("\\s+", 2);
			if (kv.length == 2)
				values.putIfAbsent(kv[0].toLowerCase(Locale.ROOT), kv[1].trim());
		}
		String device = values.get("snd_sfxdevice");
		if ("5".equals(device))
			return new Sound(file, GUS, null, null, null, null);
		if (!"3".equals(device))
			return null;
		return new Sound(file, SB, null, null, null, null);
	}

	private static final Pattern SCI_SOUND = Pattern.compile("(?im)^\\s*soundDrv\\s*=\\s*(\\S+)");
	private static final Set<String> SCI_MT32 = Set.of("MT32.DRV", "MTBLAST.DRV", "MTPRO.DRV");

	/** RESOURCE.CFG of the games of Sierra: "soundDrv = MT32.DRV" (MTBLAST.DRV, MTPRO.DRV: with a sound card for speech) */
	public static Sound sierraCfg(String file, String text) {
		Matcher m = SCI_SOUND.matcher(text);
		if (m.find()) {
			String driver = m.group(1).toUpperCase(Locale.ROOT);
			if (SCI_MT32.contains(driver.substring(Math.max(driver.lastIndexOf('\\'), driver.lastIndexOf('/')) + 1)))
				return new Sound(file, MT32, null, null, null, null);
		}
		return null;
	}

	private static final Pattern HMI_SECTION = Pattern.compile("(?is)^\\s*\\[SoundFX\\]\\s*$(.*?)(?=^\\s*\\[|\\z)", Pattern.MULTILINE);
	/** the numbers of the Sound Blaster and the Sound Blaster 16 (8 and 16 bit, mono and stereo) as SOUNDDRV.INI of Dynamix lists them */
	private static final Set<String> HMI_SB = Set.of("e000", "e001", "e015", "e016", "e017", "e018");

	/**
	 * The sound system of HMI, as the setup of Dynamix writes it into RESOURCE.CFG (Aces of the Deep): "[SoundFX]",
	 * "Driver#=E015", "DMA=1", "IRQ=7", "PortAddress=220".
	 */
	public static Sound hmiCfg(String file, String text) {
		Matcher m = HMI_SECTION.matcher(text);
		return m.find() ? hmiValues(file, m.group(1)): null;
	}

	private static final Pattern HMI_DIGITAL = Pattern.compile("(?is)^\\s*\\[DIGITAL\\]\\s*$(.*?)(?=^\\s*\\[|\\z)", Pattern.MULTILINE);

	private static String first(Map<String, String> values, String... keys) {
		for (String k: keys)
			if (values.containsKey(k))
				return values.get(k);
		return null;
	}

	/**
	 * The sound system of HMI as most games write it: HMISET.CFG with "[DIGITAL]", "DeviceIRQ = 7", "DeviceDMA = 1",
	 * "DevicePort = 0x220", "DeviceID = 0xe016" (Realms of Arkania 3, Millennia); with "Device_IRQ" in MAX.INI of M.A.X.; as
	 * "SoundCard=0xe016", "SoundPort=0x220", "SoundIRQ=7", "SoundDMA=1" without a section (Sensible Soccer). With a number
	 * for 16 bit (e017, e018) the DMA channel is the high one (M.A.X.: "Device_DMA=5").
	 */
	public static Sound hmiSet(String file, String text) {
		Matcher m = HMI_DIGITAL.matcher(text);
		return hmiValues(file, m.find() ? m.group(1): text);
	}

	private static Sound hmiValues(String file, String text) {
		Map<String, String> values = new LinkedHashMap<>();
		for (String line: text.split("\\R")) {
			String[] kv = line.split("=", 2);
			if (kv.length == 2)
				values.putIfAbsent(kv[0].trim().toLowerCase(Locale.ROOT).replace("_", ""), kv[1].trim());
		}
		String id = StringUtils.defaultString(first(values, "deviceid", "soundcard", "driver#")).toLowerCase(Locale.ROOT);
		if (id.startsWith("0x"))
			id = id.substring(2);
		if (!HMI_SB.contains(id))
			return null;
		Integer dma = number(first(values, "devicedma", "sounddma", "dma"), 10);
		boolean high = id.equals("e017") || id.equals("e018");
		return new Sound(file, SB, number(first(values, "deviceport", "soundport", "portaddress"), 16), number(first(values, "deviceirq", "soundirq", "irq"), 10), high ? null: dma, high ? dma: null);
	}

	private static final Pattern BLASTER_ENTRY = Pattern.compile("(?im)^\\s*BLASTER\\s*=\\s*(A[0-9a-f]{3}\\b.*)$");

	/**
	 * Other files of settings, as found with the games: "BLASTER = A220 I7 D1" (Flashback, FB.CFG); "SFXDRIVER=A32SBPDG.DLL",
	 * "SFXADDRESS=220", "SFXIRQ=7", "SFXDMA=1" (SimCity 2000; "*": found by the game); "SoundDriver=SBPRO.MSD", "Port=544",
	 * "Interrupt=7", "DMAChannel=1" (Turrican II; the port in decimal).
	 */
	public static Sound otherCfg(String file, String text) {
		Matcher b = BLASTER_ENTRY.matcher(text);
		if (b.find()) {
			Sound s = blasterLine(file, "set blaster=" + b.group(1));
			return s == null ? null: new Sound(file, SB, s.port(), s.irq(), s.dma(), s.hdma());
		}
		Map<String, String> values = new LinkedHashMap<>();
		for (String line: text.split("\\R")) {
			String[] kv = line.split("=", 2);
			if (kv.length == 2)
				values.putIfAbsent(kv[0].trim().toLowerCase(Locale.ROOT), kv[1].trim());
		}
		String sfx = StringUtils.defaultString(values.get("sfxdriver")).toUpperCase(Locale.ROOT), msd = StringUtils.defaultString(values.get("sounddriver")).toUpperCase(Locale.ROOT);
		if (sfx.startsWith("A32SB"))
			return new Sound(file, SB, number(values.get("sfxaddress"), 16), number(values.get("sfxirq"), 10), number(values.get("sfxdma"), 10), null);
		if (msd.startsWith("SB") && msd.endsWith(".MSD"))
			return new Sound(file, SB, number(values.get("port"), 10), number(values.get("interrupt"), 10), number(values.get("dmachannel"), 10), null);
		return null;
	}

	private static final Pattern ORIGIN_DS = Pattern.compile("(?m)^-ds(\\d)(1[0-2]|\\d)(\\d)\\s*$");

	/**
	 * The games of Origin with a .CFG of switches (Wing Commander Armada, Wings of Glory): "-ds271" is the Sound Blaster at
	 * 220, IRQ 7, DMA 1 ("-ds###: address(2n0) IRQ DMAchannel", the file of Wings of Glory says so itself).
	 */
	public static Sound originCfg(String file, String text) {
		Matcher m = ORIGIN_DS.matcher(text);
		return m.find() ? new Sound(file, SB, 0x200 + 16 * Integer.parseInt(m.group(1)), number(m.group(2), 10), number(m.group(3), 10), null): null;
	}

	private static final Pattern BUILD_FX = Pattern.compile("(?im)^\\s*FXDevice\\s*=\\s*(\\d+)\\s*$");

	/**
	 * The games made with Build (Duke Nukem 3D, Blood, Shadow Warrior): "[Sound Setup]", "FXDevice = 0" is the Sound Blaster.
	 * The "BlasterInterrupt" and its like there are not compared: whether the game takes them or the BLASTER variable is not
	 * known to DBGL.
	 */
	public static Sound buildCfg(String file, String text) {
		if (!text.toLowerCase(Locale.ROOT).contains("[sound setup]"))
			return null;
		Matcher m = BUILD_FX.matcher(text);
		return m.find() && m.group(1).equals("0") ? new Sound(file, SB, null, null, null, null): null;
	}

	private static final Pattern SWITCH_PORT = Pattern.compile("(?i)(?:^|\\s)-sa:([0-9a-f]{3})\\b"), SWITCH_IRQ = Pattern.compile("(?i)\\s-si:(\\d{1,2})\\b"),
			SWITCH_DMA = Pattern.compile("(?i)\\s-sd:(\\d)\\b"), SWITCH_SB = Pattern.compile("(?i)\\s-ssb\\w*");

	/**
	 * The batch file of The Elder Scrolls: Arena: "A -sa:220 -si:7 -sd:1 -ma:330 -mq:2 -md:1 -ssbdig.adv -mgenmidi.adv" -
	 * address, IRQ and DMA of the sound card, here a Sound Blaster (sbdig.adv).
	 */
	public static Sound switchesLine(String file, String line) {
		String t = line.trim();
		if (t.toLowerCase(Locale.ROOT).startsWith("rem") || t.startsWith("::") || !SWITCH_SB.matcher(t).find())
			return null;
		Matcher p = SWITCH_PORT.matcher(t), i = SWITCH_IRQ.matcher(t), d = SWITCH_DMA.matcher(t);
		if (!p.find() || !i.find() || !d.find())
			return null;
		return new Sound(file, SB, number(p.group(1), 16), number(i.group(1), 10), number(d.group(1), 10), null);
	}

	private static final Pattern BLASTER_LINE = Pattern.compile("(?i)^\\s*@?set\\s+blaster\\s*=\\s*(\\S.*)$");
	private static final Pattern ULTRASND_LINE = Pattern.compile("(?i)^\\s*@?set\\s+ultrasnd\\s*=\\s*([0-9a-f]+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*$");

	/**
	 * "SET BLASTER=A220 I5 D1 H5 T6" and "SET ULTRASND=240,3,3,5,5" (port, DMA to play, DMA to record, IRQ, IRQ for MIDI):
	 * DOSBox sets these variables itself, from its settings; a line like this replaces what it set ("You should never set
	 * this variable manually", the manual of DOSBox Staging on both).
	 */
	public static Sound blasterLine(String file, String line) {
		Matcher u = ULTRASND_LINE.matcher(line);
		if (u.find())
			return new Sound(file, GUS, number(u.group(1), 16), number(u.group(4), 10), number(u.group(2), 10), null, "ULTRASND", line.trim());
		Matcher m = BLASTER_LINE.matcher(line);
		if (!m.find())
			return null;
		Integer port = null, irq = null, dma = null, hdma = null;
		for (String part: m.group(1).trim().split("\\s+")) {
			if (part.length() < 2)
				continue;
			String value = part.substring(1);
			switch (Character.toUpperCase(part.charAt(0))) {
				case 'A' -> port = number(value, 16);
				case 'I' -> irq = number(value, 10);
				case 'D' -> dma = number(value, 10);
				case 'H' -> hdma = number(value, 10);
				default -> {
					// T (the model), P (MIDI port), E, M: not compared
				}
			}
		}
		return (port == null) && (irq == null) && (dma == null) && (hdma == null) ? null: new Sound(file, SB, port, irq, dma, hdma, "BLASTER", line.trim());
	}

	private static final Set<String> SOUND_FOLDERS = Set.of("SOUND", "SOUNDS", "AIL", "DRIVERS", "CFG", "CONFIG", "SETUP", "DATA");
	private static final Set<String> DOOM_FILES = Set.of("DEFAULT.CFG", "HERETIC.CFG", "HEXEN.CFG", "STRIFE.CFG");
	private static final long MAX_SIZE = 64 * 1024;

	private static String read(File f) {
		try {
			return f.isFile() && (f.length() <= MAX_SIZE) ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1): null;
		} catch (Exception e) {
			return null;
		}
	}

	/** what the files in the folder of the game (and in a few folders below it, e.g. SOUND) say; main: the program or batch file */
	public static List<Sound> soundOf(File main) {
		List<Sound> result = new ArrayList<>();
		File dir = main == null ? null: main.getAbsoluteFile().getParentFile();
		String[] names = dir == null ? null: dir.list();
		if (names == null)
			return result;
		Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
		List<File> folders = new ArrayList<>(List.of(dir));
		for (String n: names)
			if (SOUND_FOLDERS.contains(n.toUpperCase(Locale.ROOT)) && new File(dir, n).isDirectory())
				folders.add(new File(dir, n));
		for (File folder: folders) {
			String[] in = folder == dir ? names: folder.list();
			if (in == null)
				continue;
			Arrays.sort(in, String.CASE_INSENSITIVE_ORDER);
			for (String n: in) {
				String upper = n.toUpperCase(Locale.ROOT), shown = folder == dir ? n: folder.getName() + File.separator + n;
				boolean miles = upper.equals("DIG.INI") || upper.equals("MDI.INI"), doom = DOOM_FILES.contains(upper), sierra = upper.equals("RESOURCE.CFG"),
						hmi = upper.equals("HMISET.CFG"), other = !miles && ((folder == dir) && (upper.endsWith(".CFG") || upper.endsWith(".INI")));
				if (!miles && !doom && !sierra && !other && !hmi)
					continue;
				String text = read(new File(folder, n));
				if (text == null)
					continue;
				Sound s = miles ? milesIni(shown, text): doom ? doomCfg(shown, text): sierra ? sierraCfg(shown, text): null;
				if (s != null)
					result.add(s);
				if (sierra || other)
					for (Sound more: new Sound[] {sierra ? hmiCfg(shown, text): null, other && !doom ? originCfg(shown, text): null, other && !doom ? buildCfg(shown, text): null})
						if (more != null)
							result.add(more);
				if (hmi || (other && !doom && !sierra)) {
					Sound more = hmiSet(shown, text);
					if ((more == null) && !hmi)
						more = otherCfg(shown, text);
					if (more != null)
						result.add(more);
				}
			}
		}
		if (main.getName().toUpperCase(Locale.ROOT).endsWith(".BAT")) {
			String text = read(main);
			if (text != null)
				for (String line: text.split("\\R")) {
					Sound s = blasterLine(main.getName() + ": " + line.trim(), line);
					if (s == null)
						s = switchesLine(main.getName(), line);
					if (s != null)
						result.add(s);
				}
		}
		return result;
	}

	private static final Set<String> ON = Set.of("1", "enabled", "true", "on", "yes"), OFF = Set.of("0", "disabled", "false", "off", "no", "none");

	private static String value(Configuration conf, String section, String item) {
		return conf.hasValue(section, item) ? StringUtils.trimToEmpty(conf.getValue(section, item)).toLowerCase(Locale.ROOT): null;
	}

	/** the name this DOSBox version has for a setting: the first of the names it knows (DOSBox 0.72 and older had other ones) */
	public static String itemOf(Configuration conf, String section, String... names) {
		for (String n: names)
			if (conf.hasValue(section, n))
				return n;
		return names[0];
	}

	// DOSBox up to 0.65: "base" in [sblaster] and [gus]; up to 0.72: "irq1" and "dma1" of the Gravis Ultrasound
	private static final String[] SB_BASE = {"sbbase", "base"}, GUS_BASE = {"gusbase", "base"}, GUS_IRQ = {"gusirq", "irq1"}, GUS_DMA = {"gusdma", "dma1"};

	/** what DOSBox takes for the Sound Blaster (its documentation); a value of a game outside of these is not set by a solution */
	private static final Set<String> SB_PORTS = Set.of("220", "240", "260", "280", "2a0", "2c0", "2e0", "300"), SB_IRQS = Set.of("7", "5", "3", "9", "10", "11", "12"),
			SB_DMAS = Set.of("1", "5", "0", "3", "6", "7");

	/**
	 * Settings that do not go together: the Sound Blaster and the Gravis Ultrasound on the same IRQ, DMA channel or port (each
	 * device needs its own, or one of them fails or the sound is garbled); for DOSBox 0.74 and its kin a cputype with
	 * "prefetch" and another core than normal or auto (DOSBox quits: "prefetch queue emulation requires the normal core
	 * setting.").
	 *
	 * @param conf    the settings as they apply: those of the DOSBox version with those of the profile over them
	 * @param classic DOSBox 0.74, SVN, ECE
	 */
	public static List<Problem> conflicts(Configuration conf, boolean classic) {
		List<Problem> result = new ArrayList<>();
		String sbtype = value(conf, "sblaster", "sbtype"), gus = value(conf, "gus", "gus");
		if ((sbtype != null) && !sbtype.equals("none") && (gus != null) && ON.contains(gus)) {
			String[][] pairs = {{"irq", itemOf(conf, "gus", GUS_IRQ)}, {"dma", itemOf(conf, "gus", GUS_DMA)}, {"hdma", itemOf(conf, "gus", GUS_DMA)},
					{itemOf(conf, "sblaster", SB_BASE), itemOf(conf, "gus", GUS_BASE)}};
			for (String[] p: pairs) {
				String a = value(conf, "sblaster", p[0]), b = value(conf, "gus", p[1]);
				if ((a != null) && a.equals(b) && (number(a, 16) != null) && (!p[0].equals("hdma") || sbtype.startsWith("sb16")))
					result.add(new Problem("deviceconflict", "[sblaster] " + p[0], "[gus] " + p[1], a));
			}
		}
		String cputype = value(conf, "cpu", "cputype"), core = value(conf, "cpu", "core");
		if (classic && (cputype != null) && cputype.endsWith("_prefetch") && (core != null) && !core.equals("normal") && !core.equals("auto"))
			result.add(new Problem("prefetchcore", "[cpu] core", cputype, core));
		return result;
	}

	/**
	 * What the game is set to against the profile.
	 *
	 * @param mt32Emulated DOSBox Staging and DOSBox-X: an MT-32 comes with "mididevice = mt32"
	 */
	public static List<Problem> soundProblems(List<Sound> sounds, Configuration conf, boolean mt32Emulated) {
		List<Problem> result = new ArrayList<>();
		String sbtype = value(conf, "sblaster", "sbtype"), gus = value(conf, "gus", "gus"), midi = value(conf, "midi", "mididevice");
		Set<String> told = new java.util.HashSet<>();
		for (Sound s: sounds) {
			if (s.device().equals(SB) && (sbtype != null)) {
				if (sbtype.equals("none") || sbtype.equals("off")) {
					if ((s.variable() == null) && told.add(SB))
						result.add(new Problem("sounddeviceoff", s.file(), "Sound Blaster", "[sblaster] sbtype = " + sbtype));
					continue;
				}
				Integer port = number(value(conf, "sblaster", itemOf(conf, "sblaster", SB_BASE)), 16), irq = number(value(conf, "sblaster", "irq"), 10),
						dma = number(value(conf, "sblaster", "dma"), 10), hdma = sbtype.startsWith("sb16") ? number(value(conf, "sblaster", "hdma"), 10): null;
				// compared: what both sides name
				Integer gPort = port == null ? null: s.port(), gIrq = irq == null ? null: s.irq(), gDma = dma == null ? null: s.dma(), gHdma = hdma == null ? null: s.hdma();
				Integer pPort = gPort == null ? null: port, pIrq = gIrq == null ? null: irq, pDma = gDma == null ? null: dma, pHdma = gHdma == null ? null: hdma;
				String game = blasterOf(gPort, gIrq, gDma, gHdma), profile = blasterOf(pPort, pIrq, pDma, pHdma);
				if (game.equals(profile))
					continue;
				if (s.variable() != null) { // a line that sets the variable: the line is what is wrong (last: "profile" if it is a line of the profile)
					result.add(variableProblem(s, game, profile));
					continue;
				}
				// for the solution: the values that differ (empty: as it is)
				String fPort = (gPort == null) || gPort.equals(pPort) ? "": Integer.toHexString(gPort), fIrq = (gIrq == null) || gIrq.equals(pIrq) ? "": String.valueOf(gIrq),
						fDma = (gDma == null) || gDma.equals(pDma) ? "": String.valueOf(gDma), fHdma = (gHdma == null) || gHdma.equals(pHdma) ? "": String.valueOf(gHdma);
				// a solution only with values DOSBox takes
				boolean settable = (fPort.isEmpty() || SB_PORTS.contains(fPort)) && (fIrq.isEmpty() || SB_IRQS.contains(fIrq)) && (fDma.isEmpty() || SB_DMAS.contains(fDma))
						&& (fHdma.isEmpty() || SB_DMAS.contains(fHdma));
				if (settable)
					result.add(new Problem("soundmismatch", s.file(), game, profile, fPort, fIrq, fDma, fHdma));
				else
					result.add(new Problem("soundmismatch", s.file(), game, profile));
			} else if (s.device().equals(GUS) && (gus != null) && (s.variable() != null)) {
				if (!ON.contains(gus))
					continue;
				Integer port = number(value(conf, "gus", itemOf(conf, "gus", GUS_BASE)), 16), irq = number(value(conf, "gus", itemOf(conf, "gus", GUS_IRQ)), 10),
						dma = number(value(conf, "gus", itemOf(conf, "gus", GUS_DMA)), 10);
				Integer gPort = port == null ? null: s.port(), gIrq = irq == null ? null: s.irq(), gDma = dma == null ? null: s.dma();
				String game = blasterOf(gPort, gIrq, gDma, null), profile = blasterOf(gPort == null ? null: port, gIrq == null ? null: irq, gDma == null ? null: dma, null);
				if (!game.equals(profile))
					result.add(variableProblem(s, game, profile));
			} else if (s.device().equals(GUS) && (gus != null) && OFF.contains(gus)) {
				if (told.add(GUS))
					result.add(new Problem("sounddeviceoff", s.file(), "Gravis Ultrasound", "[gus] gus = " + gus));
			} else if (s.device().equals(MT32) && mt32Emulated && (midi != null) && !midi.equals("mt32")) {
				if (told.add(MT32))
					result.add(new Problem("gamewantsmt32", s.file(), "[midi] mididevice = " + midi));
			}
		}
		return result;
	}

	/** a line that sets BLASTER or ULTRASND to other values than DOSBox has; a line of the profile (last: "profile") can be removed */
	private static Problem variableProblem(Sound s, String game, String profile) {
		boolean own = s.file().startsWith("[autoexec]");
		return new Problem("soundvariable", own ? s.line(): s.file(), game, profile, s.variable(), own ? "profile": "");
	}

	private static final List<String> CD_IMAGES = List.of("cue", "mds", "ccd", "ins", "inst", "iso", "chd");
	private static final Pattern IMGMOUNT = Pattern.compile("(?i)\\bimgmount\\b|-t\\s+(?:cdrom|iso)\\b");

	/** the first drive letter from D: on that no mount of the list uses, null if there is none */
	public static Character freeDrive(List<Mount> mounts, List<Mount> more) {
		Set<Character> used = new java.util.HashSet<>();
		for (Mount m: mounts)
			used.add(Character.toUpperCase(m.getDrive()));
		for (Mount m: more)
			used.add(Character.toUpperCase(m.getDrive()));
		for (char c = 'D'; c <= 'Y'; c++)
			if (!used.contains(c))
				return c;
		return null;
	}

	/** a CD image in the folder of the game while the profile mounts no image and no CD at all: its name, else null */
	public static String unusedCdImage(File main, List<Mount> mounts, List<String> ownLines) {
		File dir = main == null ? null: main.getAbsoluteFile().getParentFile();
		String[] names = dir == null ? null: dir.list();
		if (names == null)
			return null;
		for (Mount m: mounts)
			if ((m instanceof ImageMount) || IMGMOUNT.matcher(m.toString()).find())
				return null;
		for (String line: ownLines)
			if (IMGMOUNT.matcher(line).find())
				return null;
		Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
		for (String ext: CD_IMAGES)
			for (String n: names)
				if (n.toLowerCase(Locale.ROOT).endsWith("." + ext) && isCdImage(new File(dir, n), ext))
					return n;
		return null;
	}

	private static final Pattern CUE_FILE = Pattern.compile("(?im)^\\s*FILE\\s+\\S"), CUE_TRACK = Pattern.compile("(?im)^\\s*TRACK\\s+\\d");

	/**
	 * Is this file a CD image by what is in it, not only by its name? Games have files of their own with these endings (an
	 * ".ins" of 25 bytes is no cue sheet): a cue sheet (.cue, and .ins and .inst as GOG names them) names a FILE and a TRACK;
	 * an ISO image has "CD001" where its first volume descriptor is; .mds, .ccd and .chd start as their makers write them.
	 */
	public static boolean isCdImage(File f, String ext) {
		if (!f.isFile())
			return false;
		try (java.io.RandomAccessFile in = new java.io.RandomAccessFile(f, "r")) {
			long size = in.length();
			switch (ext) {
				case "cue", "ins", "inst", "ccd" -> {
					if ((size < 20) || (size > MAX_SIZE))
						return false;
					byte[] all = new byte[(int)size];
					in.readFully(all);
					String text = new String(all, StandardCharsets.ISO_8859_1);
					return ext.equals("ccd") ? text.contains("[CloneCD]"): CUE_FILE.matcher(text).find() && CUE_TRACK.matcher(text).find();
				}
				case "iso" -> {
					// sector 16, with 2048 bytes a sector, or 2352 (16 or 24 bytes before the data)
					for (long at: new long[] {16L * 2048 + 1, 16L * 2352 + 16 + 1, 16L * 2352 + 24 + 1})
						if (startsWith(in, at, "CD001"))
							return true;
					return false;
				}
				case "mds" -> {
					return startsWith(in, 0, "MEDIA DESCRIPTOR");
				}
				case "chd" -> {
					return startsWith(in, 0, "MComprHD");
				}
				default -> {
					return false;
				}
			}
		} catch (Exception e) {
			return false;
		}
	}

	private static boolean startsWith(java.io.RandomAccessFile in, long at, String magic) throws java.io.IOException {
		if (in.length() < at + magic.length())
			return false;
		byte[] b = new byte[magic.length()];
		in.seek(at);
		in.readFully(b);
		return magic.equals(new String(b, StandardCharsets.ISO_8859_1));
	}

	/**
	 * a game with a documented need: by its title (compared in small letters, anything but letters and digits as a space) or
	 * the name of its program; kind: "is" (this value), "min" (at least), "cycles" (a fixed number from value to max)
	 */
	record Advice(String name, Pattern title, String program, String section, String item, String kind, String value, int max, String source) {
	}

	private static final String STAGING_MANUAL = "dosbox-staging.org, Manual", DOSBOX_WIKI = "dosbox.com, Wiki";

	// Sources: the manual of DOSBox Staging 0.83, "CPU" (self-modifying code: Contra, FIFA International Soccer, Terminator 1,
	// X-Men: Madness in The Murderworld need 386_prefetch; Alley Cat and Sopwith need around 140 to 300 cycles) and "Memory"
	// (Quake, Duke Nukem 3D, Tomb Raider, Descent II run better with memsize = 32); the wiki of DOSBox, "GAMES:Ultima VII:
	// The Black Gate" (disable ems).
	static final List<Advice> ADVICE = List.of(
		new Advice("Contra", Pattern.compile("^contra$"), null, "cpu", "cputype", "is", "386_prefetch", 0, STAGING_MANUAL),
		new Advice("FIFA International Soccer", Pattern.compile("^fifa international soccer$"), null, "cpu", "cputype", "is", "386_prefetch", 0, STAGING_MANUAL),
		new Advice("The Terminator", Pattern.compile("^(the )?terminator$"), null, "cpu", "cputype", "is", "386_prefetch", 0, STAGING_MANUAL),
		new Advice("X-Men: Madness in Murderworld", Pattern.compile("^x men madness in (the )?murderworld$"), null, "cpu", "cputype", "is", "386_prefetch", 0, STAGING_MANUAL),
		new Advice("Alley Cat", Pattern.compile("^alley cat$"), null, "cpu", "cycles", "cycles", "140", 300, STAGING_MANUAL),
		new Advice("Sopwith", Pattern.compile("^sopwith$"), null, "cpu", "cycles", "cycles", "140", 300, STAGING_MANUAL),
		new Advice("Quake", Pattern.compile("^quake$"), "QUAKE.EXE", "dosbox", "memsize", "min", "32", 0, STAGING_MANUAL),
		new Advice("Duke Nukem 3D", Pattern.compile("^duke nukem 3d( atomic edition)?$"), "DUKE3D.EXE", "dosbox", "memsize", "min", "32", 0, STAGING_MANUAL),
		new Advice("Tomb Raider", Pattern.compile("^tomb raider$"), null, "dosbox", "memsize", "min", "32", 0, STAGING_MANUAL),
		new Advice("Descent II", Pattern.compile("^descent (ii|2)( .*)?$"), null, "dosbox", "memsize", "min", "32", 0, STAGING_MANUAL),
		new Advice("Ultima VII: The Black Gate", Pattern.compile("^ultima (vii|7)( the black gate)?$"), "ULTIMA7.COM", "dos", "ems", "is", "false", 0, DOSBOX_WIKI));

	public static String titleKey(String title) {
		// without what stands in brackets ("Contra (1988)", "Quake [CD]") and with "Terminator, The" as "the terminator"
		String t = StringUtils.defaultString(title).toLowerCase(Locale.ROOT).replaceAll("\\([^)]*\\)|\\[[^\\]]*\\]", " ").replaceAll("[^a-z0-9,]+", " ").trim();
		if (t.endsWith(", the"))
			t = "the " + t.substring(0, t.length() - 5);
		return t.replace(',', ' ').replaceAll(" +", " ").trim();
	}

	private static final Pattern FIXED_CYCLES = Pattern.compile("^(?:fixed\\s+)?(\\d+)$");

	/**
	 * @param own  the settings the profile itself has
	 * @param conf the settings as they apply
	 * @param main the program (null: not known)
	 */
	public static List<Problem> advice(String title, File main, Configuration own, Configuration conf) {
		List<Problem> result = new ArrayList<>();
		String key = titleKey(title), program = main == null ? "": main.getName().toUpperCase(Locale.ROOT);
		for (Advice a: ADVICE) {
			if (!a.title().matcher(key).matches() && !program.equals(a.program()))
				continue;
			String item = a.item();
			// DOSBox Staging 0.82 and later: "cpu_cycles", unless the profile itself still says "cycles" (taken as before)
			if (a.kind().equals("cycles") && conf.hasValue("cpu", "cpu_cycles") && ((own == null) || !own.hasValue("cpu", "cycles")))
				item = "cpu_cycles";
			String current = value(conf, a.section(), item);
			if (current == null)
				continue; // not a setting of this DOSBox version
			String setting = "[" + a.section() + "] " + item, advised, fixValue = "";
			boolean met;
			switch (a.kind()) {
				case "min" -> {
					Integer n = number(current, 10);
					met = (n == null) || (n >= Integer.parseInt(a.value()));
					advised = setting + " = " + a.value();
					fixValue = a.value();
				}
				case "cycles" -> {
					Matcher m = FIXED_CYCLES.matcher(current);
					int n = m.matches() ? Integer.parseInt(m.group(1)): -1;
					met = (n >= Integer.parseInt(a.value())) && (n <= a.max());
					advised = setting + " = " + a.value() + " ... " + a.max();
				}
				default -> {
					met = current.equals(a.value()) || (OFF.contains(a.value()) && OFF.contains(current)) || (ON.contains(a.value()) && ON.contains(current));
					advised = setting + " = " + a.value();
					fixValue = a.value();
				}
			}
			if (!met)
				result.add(new Problem("gameadvice", setting, a.name(), advised, a.source(), setting + " = " + current, fixValue));
		}
		return result;
	}

	/**
	 * All of it for a profile.
	 *
	 * @param main    the program of the profile on this computer, null if DBGL cannot look into its folder (e.g. in an image)
	 * @param soFar   what was found before (not told twice)
	 * @param classic DOSBox 0.74, SVN, ECE
	 */
	public static List<Problem> check(Profile prof, File main, List<Mount> netto, List<Problem> soFar, boolean classic, boolean staging, boolean dosboxX) {
		List<Problem> result = new ArrayList<>();
		Configuration conf = prof.getCombinedConfigurationForReading();
		Autoexec autoexec = prof.getConfiguration().getAutoexec();
		result.addAll(conflicts(conf, classic));

		// the MT-32 chosen, its ROMs not there: no music (a folder that is missing is told as that)
		if ((staging || dosboxX) && "mt32".equals(value(conf, "midi", "mididevice")) && (prof.getDosboxVersion() != null)
				&& soFar.stream().noneMatch(pr -> pr.key().equals("settingfilemissing") && String.valueOf(pr.args()[0]).contains("romdir"))) {
			MidiRoms.Found roms = MidiRoms.mt32(prof.getDosboxVersion(), conf, dosboxX);
			// DOSBox Staging also takes a folder "mt32-roms" in the folder it is started in: not looked into, not told
			File beside = main == null ? null: new File(main.getAbsoluteFile().getParentFile(), "mt32-roms");
			String[] besideFiles = beside == null ? null: beside.list();
			if (((roms.dir() == null) || roms.incomplete()) && ((besideFiles == null) || (besideFiles.length == 0)))
				result.add(new Problem("mt32norom", dosboxX ? "[midi] mt32.romdir": "[mt32] romdir"));
		}

		List<String> own = ProfileChecks.ownLines(autoexec);
		List<Sound> sounds = new ArrayList<>();
		for (String line: own) {
			Sound s = blasterLine("[autoexec] " + line.trim(), line);
			if (s != null)
				sounds.add(s);
		}
		boolean lure = soFar.stream().anyMatch(pr -> pr.key().equals("lurecm32l"));
		if ((main != null) && autoexec.isDos()) {
			sounds.addAll(soundOf(main));
			String image = unusedCdImage(main, netto, own);
			if (image != null) {
				Character letter = freeDrive(netto, autoexec.getMountingpoints());
				if (letter == null)
					result.add(new Problem("cdimageunused", image));
				else { // for the solution: the line that mounts it, the path as DBGL writes paths (within dosroot: from there)
					String path = new org.dbgl.model.FileLocation(new File(main.getAbsoluteFile().getParentFile(), image).getPath(),
						org.dbgl.service.FileLocationService.getInstance().dosrootRelative()).getFile().getPath();
					result.add(new Problem("cdimageunused", image, "imgmount " + letter + " \"" + path + "\" -t iso"));
				}
			}
		}
		for (Problem pr: soundProblems(sounds, conf, staging || dosboxX))
			if (!(lure && pr.key().equals("gamewantsmt32")))
				result.add(pr);
		result.addAll(advice(prof.getTitle(), main, prof.getConfiguration(), conf));
		Problem setup = setupAdvice(prof.getTitle(), conf);
		if (setup != null)
			result.add(setup);
		return result;
	}

	private static final Pattern DUNGEON_MASTER_2 = Pattern.compile("^dungeon master (ii|2)( .*)?$");

	/**
	 * What to choose in the setup of the game itself (DBGL cannot do that; the hint can be hidden once it is done). Dungeon
	 * Master II: its setup finds a Sound Blaster 16 by itself and then plays music but no sound effects; with "Sound Blaster
	 * Pro" chosen by hand the effects are there (found by a user of DBGL with DOSBox emulating a Sound Blaster 16, IRQ 7).
	 */
	public static Problem setupAdvice(String title, Configuration conf) {
		String type = value(conf, "sblaster", "sbtype");
		if (DUNGEON_MASTER_2.matcher(titleKey(title)).matches() && (type != null) && type.startsWith("sb16"))
			return new Problem("dm2sbpro", title, "[sblaster] sbtype = " + type);
		return null;
	}
}
