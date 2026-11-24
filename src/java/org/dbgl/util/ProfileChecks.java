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
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.dbgl.model.FileLocation;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.ImageMount;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.model.conf.mount.OverlayMount;
import org.dbgl.model.conf.mount.PhysFsMount;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;
import org.dbgl.service.FileLocationService;


/**
 * The checks of "Check profiles" beyond invalid values, missing paths and links, repeated mounts and DOSBox-X: whatever else
 * keeps a profile from starting or working as it should.
 * Each problem is a text key below "dialog.checkprofiles." with its arguments.
 */
public final class ProfileChecks {

	public record Problem(String key, Object... args) {
	}

	/**
	 * A setting DOSBox Staging 0.83 keeps only as "deprecated" (from its source: AddX(..., Deprecated, ...)): it accepts it
	 * but does nothing with it. kind: "rename" (same value, new name), "disney" (true becomes lpt_dac = disney), "removed"
	 * (no effect, nothing instead), "manual" (replaced by a setting with other values: to be set by hand). Those that still
	 * work ("windowresolution", "glshader", "cycles") are not listed.
	 */
	public record OldSetting(String section, String item, String newSection, String newItem, String kind) {
	}

	/** a deprecated setting in a profile: what to do with it, and for a rename the value it gets (null: none) */
	public record StagingChange(OldSetting old, String value, String newValue) {
	}

	/**
	 * Alpha 49, for "Take over settings": what DOSBox Staging 0.83 made of an older setting.
	 * 
	 * @return the kind (see {@link OldSetting}), the section and the name of the setting that took its place (null: none) and
	 *         the value that one gets for the given value (null: none, or to be set by hand); null for any other setting
	 */
	public static String[] stagingSuccessor(String section, String item, String value) {
		for (OldSetting old: STAGING_083_DEPRECATED)
			if (old.section().equalsIgnoreCase(section) && old.item().equalsIgnoreCase(item))
				return new String[] {old.kind(), old.newSection(), old.newItem(), converted(old, StringUtils.defaultString(value).trim())};
		return null;
	}

	/** the value the new setting gets for the value of the old one (null: none) */
	private static String converted(OldSetting o, String value) {
		return switch (o.kind()) {
			case "rename" -> value;
			case "disney" -> Boolean.parseBoolean(value) || value.equals("1") || value.equalsIgnoreCase("on") ? "disney": null;
			// "builtin_dos_mouse_driver=true" is no value of DOSBox Staging 0.83 (found with DBConfWS, alpha 46)
			case "onoff" -> Boolean.parseBoolean(value) || value.equals("1") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("yes") ? "on": "off";
			default -> null;
		};
	}

	static final List<OldSetting> STAGING_083_DEPRECATED = List.of(
		new OldSetting("dosbox", "captures", "capture", "capture_dir", "rename"),
		new OldSetting("dosbox", "vga_render_per_scanline", null, null, "removed"),
		new OldSetting("sdl", "fullresolution", "sdl", "fullscreen_mode", "manual"),
		new OldSetting("sdl", "transparency", "sdl", "window_transparency", "rename"),
		new OldSetting("sdl", "max_resolution", "render", "viewport", "manual"),
		new OldSetting("sdl", "viewport_resolution", "render", "viewport", "rename"),
		new OldSetting("sdl", "capture_mouse", "mouse", "mouse_capture", "manual"),
		new OldSetting("sdl", "sensitivity", "mouse", "mouse_sensitivity", "rename"),
		new OldSetting("sdl", "raw_mouse_input", "mouse", "mouse_raw_input", "rename"),
		new OldSetting("sdl", "waitonerror", null, null, "removed"),
		new OldSetting("sdl", "priority", null, null, "removed"),
		new OldSetting("render", "frameskip", null, null, "removed"),
		new OldSetting("render", "scaler", null, null, "removed"),
		new OldSetting("dos", "keyboardlayout", "dos", "keyboard_layout", "rename"),
		new OldSetting("sblaster", "oplrate", null, null, "removed"),
		new OldSetting("sblaster", "oplemu", null, null, "removed"),
		new OldSetting("speaker", "disney", "speaker", "lpt_dac", "disney"),
		new OldSetting("speaker", "zero_offset", null, null, "removed"),
		new OldSetting("mouse", "dos_mouse_driver", "mouse", "builtin_dos_mouse_driver", "onoff"), // true/false before, on/no-tsr/off now
		new OldSetting("mouse", "dos_mouse_immediate", "mouse", "builtin_dos_mouse_driver_options", "manual"),
		new OldSetting("voodoo", "voodoo_multithreading", "voodoo", "voodoo_threads", "manual"));

	/** deprecated, but DOSBox Staging 0.82 and later still take them: section, setting, and where their successor is */
	static final List<String[]> STAGING_ACCEPTED = List.of(new String[] {"cpu", "cycles", "cpu", "cpu_cycles"}, new String[] {"sdl", "windowresolution", "sdl", "window_size"});

	/** the deprecated settings of a profile for DOSBox Staging 0.83 (none for other versions) */
	public static List<StagingChange> stagingChanges(Profile prof) {
		List<StagingChange> result = new ArrayList<>();
		if ((prof.getGeneration() != Generation.staging_0830) || (prof.getDosboxVersion() == null))
			return result;
		Configuration conf = prof.getConfiguration(), dbConf = prof.getDosboxVersion().getConfiguration();
		for (OldSetting o: STAGING_083_DEPRECATED) {
			if (!conf.hasValue(o.section(), o.item()) || ((dbConf != null) && dbConf.hasValue(o.section(), o.item())))
				continue; // not in the profile, or the DOSBox version still has it in its configuration
			String value = StringUtils.defaultString(conf.getValue(o.section(), o.item())).trim();
			String newValue = converted(o, value);
			// test 15: "keyboardlayout = auto" for 0.83.1: not renamed to "keyboard_layout = auto", a value that is gone there
			if ((newValue != null) && staging0831(prof.getDosboxVersion()) && GONE_0831.stream()
					.anyMatch(g -> g[0].equalsIgnoreCase(o.newSection()) && g[1].equalsIgnoreCase(o.newItem()) && g[2].equalsIgnoreCase(newValue))) {
				result.add(new StagingChange(new OldSetting(o.section(), o.item(), null, null, "autogone"), value, null));
				continue;
			}
			result.add(new StagingChange(o, value, newValue));
		}
		// alpha 49: DOSBox Staging 0.83.1 removed the value "auto" of keyboard_layout and country (it no longer guesses them from
		// the host system): such a value goes, then what the configuration of the DOSBox version says counts
		// (also "native" of locale_period: with the configuration of 0.83.1 at hand - it takes nothing from the host system now)
		if (staging0831(prof.getDosboxVersion())) {
			for (String[] gone: GONE_0831)
				if (!gone[1].equals("language") && conf.hasValue(gone[0], gone[1]) && gone[2].equalsIgnoreCase(StringUtils.trimToEmpty(conf.getValue(gone[0], gone[1]))))
					result.add(new StagingChange(new OldSetting(gone[0], gone[1], null, null, "autogone"), gone[2], null));
		}
		return result;
	}

	/** section, setting and the value DOSBox Staging 0.83.1 no longer has (language: never in a profile, only in its own file) */
	static final List<String[]> GONE_0831 = List.of(new String[] {"dos", "keyboard_layout", "auto"}, new String[] {"dos", "country", "auto"},
		new String[] {"dos", "locale_period", "native"}, new String[] {"dosbox", "language", "auto"});

	/**
	 * Test 16: a profile switched from DOSBox Staging 0.83.0 to 0.83.1 kept "keyboard_layout = auto", a value 0.83.1 no longer
	 * knows (the preview said "no changes"). For a profile of 0.83.1 or newer such values go, also under their old names; then
	 * what the configuration of the DOSBox version says counts.
	 *
	 * @return what was removed: the setting ("[dos] keyboard_layout") and its value
	 */
	public static List<String[]> removeGone0831(Profile prof) {
		List<String[]> result = new ArrayList<>();
		if (!staging0831(prof.getDosboxVersion()))
			return result;
		for (StagingChange ch: stagingChanges(prof))
			if (ch.old().kind().equals("autogone") && applyStagingChange(prof, ch.old().section(), ch.old().item()))
				result.add(new String[] {"[" + ch.old().section() + "] " + ch.old().item(), ch.value()});
		return result;
	}

	/**
	 * Test 16: "keyboard_layout = auto" written in the file of a profile of 0.83.0 is not among its settings in DBGL (the same
	 * as in the configuration of 0.83.0, and DBGL keeps only what differs), so the preview of the switch to 0.83.1 said
	 * nothing about it, though the line is gone from the file afterwards. What the file itself says, for the preview.
	 *
	 * @return the settings in the file with a value DOSBox Staging 0.83.1 no longer knows: the setting and its value; none if
	 *         the DOSBox version is an older one
	 */
	public static List<String[]> gone0831InFile(File conf, DosboxVersion target) {
		List<String[]> result = new ArrayList<>();
		if (!staging0831(target) || (conf == null) || !conf.isFile())
			return result;
		try {
			ProfileFindings.ConfText text = ProfileFindings.ConfText.of(new String(java.nio.file.Files.readAllBytes(conf.toPath()), java.nio.charset.StandardCharsets.ISO_8859_1));
			List<String[]> all = new ArrayList<>(GONE_0831);
			all.add(new String[] {"dos", "keyboardlayout", "auto"}); // the old name
			for (String[] gone: all)
				if (!gone[1].equals("language") && gone[2].equalsIgnoreCase(StringUtils.trimToEmpty(text.get(gone[0], gone[1]))))
					result.add(new String[] {"[" + gone[0] + "] " + gone[1], gone[2]});
		} catch (java.io.IOException e) {
			// not readable: nothing to tell
		}
		return result;
	}

	/**
	 * Test 17: a value written in the file of a profile that is the same as in the configuration of its DOSBox version is not
	 * among the settings DBGL holds for the profile (it keeps only what differs). Switched to a DOSBox version of the same
	 * kind that has another value there, the profile silently took that one ("memsize = 16" in the file, 16 in the old
	 * version, 32 in the new one: 32 from then on). Such values are put back, so the game runs with what its file said.
	 *
	 * @param file     the file of the profile as it was before the switch
	 * @param old      the DOSBox version before the switch
	 * @param prof     the profile, switched to its new DOSBox version (of the same generation: across generations the
	 *                 settings are converted, nothing is put back then)
	 * @return what was put back: the setting ("[dosbox] memsize"), its value and the value of the new DOSBox version
	 */
	public static List<String[]> keepWrittenValues(File file, DosboxVersion old, Profile prof) {
		List<String[]> result = new ArrayList<>();
		DosboxVersion now = prof.getDosboxVersion();
		if ((file == null) || !file.isFile() || (old == null) || (now == null) || (org.dbgl.model.entity.GenerationAwareConfigurable.determineGen(old) != org.dbgl.model.entity.GenerationAwareConfigurable.determineGen(now)) || prof.keepsAllSettings())
			return result;
		try {
			String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.ISO_8859_1);
			Configuration conf = prof.getConfiguration(), oldConf = old.getConfiguration(), nowConf = now.getConfiguration();
			for (String[] s: ConfTakeover.settings(text).values()) {
				if (conf.hasValue(s[0], s[1]) || !oldConf.hasValue(s[0], s[1]) || !nowConf.hasValue(s[0], s[1]))
					continue; // a setting of the profile as before, or one the DOSBox versions do not both have
				String oldValue = StringUtils.trimToEmpty(oldConf.getValue(s[0], s[1])), nowValue = StringUtils.trimToEmpty(nowConf.getValue(s[0], s[1]));
				if (!ConfTakeover.same(oldValue, s[2]) || ConfTakeover.same(nowValue, s[2]))
					continue; // not left out for being the same as in the old version, or the same in the new one too
				if (staging0831(now) && GONE_0831.stream().anyMatch(g -> g[0].equalsIgnoreCase(s[0]) && g[1].equalsIgnoreCase(s[1]) && g[2].equalsIgnoreCase(s[2])))
					continue; // "auto": no value of 0.83.1
				conf.setValue(s[0], s[1], s[2]);
				result.add(new String[] {"[" + s[0] + "] " + s[1], s[2], nowValue});
			}
		} catch (java.io.IOException e) {
			// not readable: as before
		}
		return result;
	}

	/** @return true for a value this DOSBox version no longer knows ("auto" for the keyboard layout of DOSBox Staging 0.83.1) */
	public static boolean gone0831Value(DosboxVersion db, String section, String item, String value) {
		return staging0831(db) && GONE_0831.stream().anyMatch(g -> g[0].equalsIgnoreCase(section) && g[1].equalsIgnoreCase(item) && g[2].equalsIgnoreCase(StringUtils.trimToEmpty(value)));
	}

	/** @return true for DOSBox Staging 0.83.1 and newer */
	public static boolean staging0831(DosboxVersion db) {
		if ((db == null) || !StringUtils.defaultString(db.getFamily()).toLowerCase(Locale.ROOT).contains("staging"))
			return false;
		if (atLeast0831(db.getVersion()))
			return true;
		// test 15: the version chosen in DBGL may lag behind (the DOSBox version of 0.83.0 pointed to the folder of 0.83.1):
		// then what the configuration file says it was written by counts
		File conf = db.getConfigurationCanonicalFile();
		if ((conf == null) || !conf.isFile())
			return false;
		if (CONF_0831.size() > 50)
			CONF_0831.clear();
		return CONF_0831.computeIfAbsent(conf.getPath() + '|' + conf.lastModified() + '|' + conf.length(), k -> atLeast0831(DosboxVersionDetector.versionOfConf(conf)));
	}

	private static final java.util.Map<String, Boolean> CONF_0831 = new java.util.concurrent.ConcurrentHashMap<>();

	private static boolean atLeast0831(String version) {
		java.util.regex.Matcher m = Pattern.compile("^\\d+(?:\\.\\d+)*").matcher(StringUtils.trimToEmpty(version));
		return m.find() && (DosboxUpdateChecker.compareVersions(m.group(), "0.83.1") >= 0);
	}

	/**
	 * "Clean up": renamed settings get their new name (unless the profile has that one already), removed ones go; replaced
	 * ones stay, they need another value set by hand.
	 *
	 * @return the number of settings changed
	 */
	public static int applyStagingChanges(Profile prof) {
		int count = 0;
		Configuration conf = prof.getConfiguration();
		for (StagingChange ch: stagingChanges(prof)) {
			OldSetting o = ch.old();
			if (o.kind().equals("manual"))
				continue;
			if ((ch.newValue() != null) && !ch.newValue().isEmpty() && !conf.hasValue(o.newSection(), o.newItem()))
				conf.setValue(o.newSection(), o.newItem(), ch.newValue());
			conf.removeValue(o.section(), o.item());
			count++;
		}
		return count;
	}

	/**
	 * A setting of the profile in a section where this DOSBox version does not have it, but in another one (or several)
	 * it does. alreadySet: the value the profile has in the right section already (null if none), for one target only.
	 */
	public record Misplaced(String section, String item, String value, List<String> targets, String alreadySet) {
	}

	public static List<Misplaced> misplacedSettings(Profile prof) {
		List<Misplaced> result = new ArrayList<>();
		DosboxVersion db = prof.getDosboxVersion();
		Configuration dbConf = db == null ? null: db.getConfiguration();
		if ((dbConf == null) || (dbConf.getSectionNames().length == 0))
			return result;
		Configuration conf = prof.getConfiguration();
		Set<String> deprecated = new java.util.HashSet<>();
		for (StagingChange ch: stagingChanges(prof))
			deprecated.add(ch.old().section().toLowerCase(Locale.ROOT) + "." + ch.old().item().toLowerCase(Locale.ROOT));
		for (String[] u: unknownSettingPairs(prof)) {
			if (deprecated.contains(u[0].toLowerCase(Locale.ROOT) + "." + u[1].toLowerCase(Locale.ROOT)))
				continue;
			// a section of a device of DOSBox-X ("[fdc, primary]", "[ide, secondary]"): "irq" or "dma" there is not the one of
			// [sblaster]; moving it there changed the sound card (its IRQ 7 became 0, found with DBConfWS, alpha 46)
			if (u[0].contains(","))
				continue;
			List<String> targets = new ArrayList<>();
			for (String s: dbConf.getSectionNames())
				if (!s.equalsIgnoreCase("autoexec") && dbConf.hasValue(s, u[1]))
					targets.add(s);
			if (targets.isEmpty())
				continue;
			String already = (targets.size() == 1) && conf.hasValue(targets.get(0), u[1]) ? conf.getValue(targets.get(0), u[1]): null;
			result.add(new Misplaced(u[0], u[1], conf.getValue(u[0], u[1]), targets, already));
		}
		return result;
	}

	/**
	 * "Clean up": a setting in the wrong section goes to the only section it belongs in; if the profile has it there already,
	 * that one counts already and only the misplaced line goes. With several possible sections it stays (to be chosen by hand).
	 *
	 * @return the number of settings changed
	 */
	public static int applyMisplaced(Profile prof) {
		int count = 0;
		Configuration conf = prof.getConfiguration();
		for (Misplaced mp: misplacedSettings(prof)) {
			if (mp.targets().size() != 1)
				continue;
			if (mp.alreadySet() == null)
				conf.setValue(mp.targets().get(0), mp.item(), StringUtils.defaultString(mp.value()));
			conf.removeValue(mp.section(), mp.item());
			count++;
		}
		return count;
	}

	/**
	 * The settings of the profile its DOSBox version does not know, but another DOSBox version in DBGL has in the same
	 * section, as {section, item, title of that version}. Not those in the wrong section or deprecated (told otherwise).
	 */
	public static List<String[]> otherVersionSettings(Profile prof, List<DosboxVersion> versions) {
		List<String[]> result = new ArrayList<>();
		if ((versions == null) || (prof.getDosboxVersion() == null))
			return result;
		Set<String> told = new java.util.HashSet<>();
		for (StagingChange ch: stagingChanges(prof))
			told.add(ch.old().section().toLowerCase(Locale.ROOT) + "." + ch.old().item().toLowerCase(Locale.ROOT));
		for (Misplaced mp: misplacedSettings(prof))
			told.add(mp.section().toLowerCase(Locale.ROOT) + "." + mp.item().toLowerCase(Locale.ROOT));
		for (String[] u: unknownSettingPairs(prof)) {
			if (told.contains(u[0].toLowerCase(Locale.ROOT) + "." + u[1].toLowerCase(Locale.ROOT)))
				continue;
			for (DosboxVersion v: versions) {
				if (v.getId() == prof.getDosboxVersion().getId())
					continue;
				if ((v.getConfiguration() == null) || (v.getConfiguration().getSectionNames().length == 0)) {
					try {
						v.resetAndLoadConfiguration(); // not read yet (the list of all DOSBox versions)
					} catch (IOException | RuntimeException e) {
						continue; // its configuration is not there: told for that DOSBox version
					}
				}
				if ((v.getConfiguration() != null) && v.getConfiguration().hasValue(u[0], u[1])) {
					result.add(new String[] {u[0], u[1], v.getTitle()});
					break;
				}
			}
		}
		return result;
	}

	/** "Clean up": settings of another DOSBox version go, they have no effect with this one */
	public static int applyOtherVersionSettings(Profile prof, List<DosboxVersion> versions) {
		List<String[]> others = otherVersionSettings(prof, versions);
		for (String[] o: others)
			prof.getConfiguration().removeValue(o[0], o[1]);
		return others.size();
	}

	/** one deprecated setting, as "Clean up" changes it (none if it needs another value set by hand) */
	public static boolean applyStagingChange(Profile prof, String section, String item) {
		Configuration conf = prof.getConfiguration();
		for (StagingChange ch: stagingChanges(prof)) {
			OldSetting o = ch.old();
			if (!o.section().equalsIgnoreCase(section) || !o.item().equalsIgnoreCase(item) || o.kind().equals("manual"))
				continue;
			if ((ch.newValue() != null) && !ch.newValue().isEmpty() && !conf.hasValue(o.newSection(), o.newItem()))
				conf.setValue(o.newSection(), o.newItem(), ch.newValue());
			conf.removeValue(o.section(), o.item());
			return true;
		}
		return false;
	}

	/** one setting in the wrong section, to the only section it belongs in */
	public static boolean applyMisplaced(Profile prof, String section, String item) {
		Configuration conf = prof.getConfiguration();
		for (Misplaced mp: misplacedSettings(prof)) {
			if (!mp.section().equalsIgnoreCase(section) || !mp.item().equalsIgnoreCase(item) || (mp.targets().size() != 1))
				continue;
			if (mp.alreadySet() == null)
				conf.setValue(mp.targets().get(0), mp.item(), StringUtils.defaultString(mp.value()));
			conf.removeValue(mp.section(), mp.item());
			return true;
		}
		return false;
	}

	public static int countMisplaced(Profile prof) {
		return (int)misplacedSettings(prof).stream().filter(mp -> mp.targets().size() == 1).count();
	}

	/** how many deprecated settings "Clean up" changes in this profile */
	public static int countStagingChanges(Profile prof) {
		return (int)stagingChanges(prof).stream().filter(ch -> !ch.old().kind().equals("manual")).count();
	}

	/** at most this many settings are named in one line of the report */
	static final int MAX_LISTED = 10;

	/** Windows cannot open longer paths (MAX_PATH) with the functions DOSBox uses */
	public static final int MAX_WINDOWS_PATH = 259;

	private static final Pattern CUE_FILE = Pattern.compile("^\\s*FILE\\s+(?:\"([^\"]+)\"|(\\S+))", Pattern.CASE_INSENSITIVE);
	private static final Pattern MOUNT_LINE = Pattern.compile("^\\s*@?(?:img)?mount(?:\\s|$)", Pattern.CASE_INSENSITIVE);
	private static final Pattern UNMOUNT_AND_MORE = Pattern.compile("-u\\s+([A-Za-z0-9]):?\\s+.*\\b(?:img)?mount\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern MOUNT_DRIVE = Pattern.compile("^\\s*@?(?:img)?mount\\s+([A-Za-z]):?(?:\\s|$)", Pattern.CASE_INSENSITIVE);
	private static final Pattern DRIVE_LINE = Pattern.compile("^\\s*@?(?:(?:call|cd)\\s+)?([A-Ya-y]):(?:\\s*$|\\\\)", Pattern.CASE_INSENSITIVE);

	private ProfileChecks() {
	}

	/** checked as on the system DBGL runs on */
	public static List<Problem> check(Profile prof) {
		return check(prof, SystemUtils.IS_OS_WINDOWS, fileNameCharset());
	}

	/** also telling of settings unknown to the profile's DOSBox version which of the other DOSBox versions they are from */
	public static List<Problem> check(Profile prof, List<DosboxVersion> versions) {
		return check(prof, SystemUtils.IS_OS_WINDOWS, fileNameCharset(), versions);
	}

	public static List<Problem> check(Profile prof, boolean windows, Charset systemCharset) {
		return check(prof, windows, systemCharset, null);
	}

	private static Charset fileNameCharset() {
		try {
			return Charset.forName(System.getProperty("sun.jnu.encoding", "UTF-8"));
		} catch (RuntimeException e) {
			return StandardCharsets.UTF_8;
		}
	}

	/**
	 * @param windows      check what only matters on Windows (path length, characters an old DOSBox cannot open)
	 * @param systemCharset the code page of file names for programs that do not use Unicode (Windows: the ANSI code page)
	 */
	public static List<Problem> check(Profile prof, boolean windows, Charset systemCharset, List<DosboxVersion> versions) {
		List<Problem> result = new ArrayList<>();
		if (Scummvm.is(prof.getDosboxVersion()))
			return result; // alpha 52: a game of ScummVM, none of the DOSBox checks apply
		Autoexec autoexec = prof.getConfiguration().getAutoexec();
		List<Mount> netto = prof.getNettoMountingPoints();
		Generation gen = prof.getGeneration();
		int family = gen == null ? -1: GenerationAwareConfigurable.familyIdx(gen);
		boolean dosboxX = family == GenerationAwareConfigurable.familyIdx(Generation.x_20241001);
		boolean staging083 = gen == Generation.staging_0830;
		boolean ansiDosbox = (family >= 0) && (family < GenerationAwareConfigurable.familyIdx(Generation.staging_0810)); // SDL 1: 0.74, SVN, Daum, ECE
		Set<String> paths = new LinkedHashSet<>(); // for the checks of all paths: length, characters

		// the programs: main, setup and the alternative ones
		Win3x.Install win3 = null;
		if (StringUtils.isNotBlank(autoexec.getMainWhereDosboxLooks())) {
			File main = programFile(new FileLocation(autoexec.getMainWhereDosboxLooks(), FileLocationService.getInstance().dosrootRelative()).getCanonicalFile());
			if (main != null) {
				paths.add(main.getPath());
				checkProgram(result, main, "main", null);
				// alpha 53: Windows 3.x started: what it starts then, and whether its drivers fit the settings of DOSBox
				win3 = Win3x.of(main);
				if (win3 != null)
					Win3x.check(win3, autoexec.getParameters(), prof.getCombinedConfigurationForReading(), netto, result);
				if (!autoexec.canBeReachedUsingMounts(false, autoexec.getMainWhereDosboxLooks(), netto))
					result.add(notReachable(prof, main, netto, windows));
				// a name longer than 8.3 or with spaces in the autoexec (e.g. "cd \DEATHGATE"): DOSBox does not find it, only its short
				// name (DEATHG~1); DBGL writes the short names when cleaning up, saving the profile or starting it
				// a batch file as program: the programs it starts must be in its folder (or on Z:, or found through PATH)
				if (main.getName().toUpperCase(Locale.ROOT).endsWith(".BAT"))
					for (String[] b: batMissing(main))
						result.add(new Problem(isCompanion(main, b[0]) ? "cohortoptional": "batmissing", main.getName(), b[0], b[1]));
				if (autoexec.hasLongNamesWithSpaces()) {
					String names = longDosNames(main, netto);
					if (!names.isEmpty())
						result.add(new Problem("longdosnames", names));
				}
			} else {
				hiddenByDosbox(prof, new FileLocation(autoexec.getMainWhereDosboxLooks(), FileLocationService.getInstance().dosrootRelative()).getCanonicalFile()).ifPresent(result::add);
			}
		} else if (StringUtils.isNotBlank(autoexec.getImg(0))) {
			File img = new FileLocation(autoexec.getImg(0), FileLocationService.getInstance().dosrootRelative()).getCanonicalFile();
			if (img.exists()) {
				paths.add(img.getPath());
				if (!autoexec.canBeReachedUsingMounts(true, autoexec.getImg(0), netto))
					result.add(notReachable(prof, img, netto, windows));
			}
		} else if (ownLines(autoexec).stream().allMatch(ProfileChecks::startsNothing)) {
			result.add(new Problem("nothingtostart")); // also with "@echo off" or "exit" only: nothing that starts a game
		}
		if (prof.hasSetup())
			checkOtherProgram(prof, result, paths, autoexec, netto, new FileLocation(prof.getSetupString(), FileLocationService.getInstance().dosrootRelative()).getCanonicalFile(), "setup", null, windows);
		String[] alt = prof.getAltExeStrings();
		for (int i = 0; i < alt.length; i++)
			if (StringUtils.isNotBlank(alt[i]))
				checkOtherProgram(prof, result, paths, autoexec, netto, new FileLocation(alt[i], FileLocationService.getInstance().dataRelative()).getCanonicalFile(), "altexe", i + 1, windows);
		// alpha 53: Windows 3.x started by own lines of the autoexec ("C:", "cd \\WINDOWS", "win"), as many profiles do it; the
		// lines start in the folder of the program DBGL read from them ("cd \\WINDOWS" and SMARTDRV.EXE before "win")
		if (win3 == null) {
			File startDir = StringUtils.isBlank(autoexec.getMainWhereDosboxLooks()) ? null
					: new FileLocation(autoexec.getMainWhereDosboxLooks(), FileLocationService.getInstance().dosrootRelative()).getCanonicalFile().getParentFile();
			Win3x.Started s = Win3x.fromAutoexec(ownLines(autoexec), netto, startDir);
			if (s != null) {
				win3 = s.install();
				Win3x.check(win3, s.params(), prof.getCombinedConfigurationForReading(), netto, result);
			}
		}
		// alpha 53: Windows 95/98 booted from a hard disk image ("imgmount 2 ... -t hdd", "boot -l C"): the programs of its Start
		// menu and of its startup folder, read from the image without changing it
		if ((win3 == null) && (autoexec.isBooter() || ownLines(autoexec).stream().anyMatch(l -> BOOT_LINE.matcher(l.strip()).matches())))
			for (File img: Win9x.hddImages(netto)) {
				Win9x.Report r = Win9x.read(img);
				if (r != null) {
					Win9x.check(r, result);
					break;
				}
			}
		// alpha 53: a program of Windows 3.x as setup or further program of a profile that starts Windows 3.x: it runs in that
		// Windows (told, with the way to start it there), no fault
		if (win3 != null)
			result.replaceAll(p -> p.key().equals("win16program") ? new Problem("win16inwindows", p.args()): p);

		// the mounts of the profile itself
		for (Mount m: autoexec.getMountingpoints()) {
			if (m.isUnmounted() || (m instanceof OverlayMount))
				continue;
			String type = StringUtils.defaultString(m.getMountAs()).toLowerCase(Locale.ROOT);
			if ((m instanceof DirMount dm) && !(m instanceof PhysFsMount)) {
				File dir = dm.getCanonicalPath();
				paths.add(dir.getPath());
				String fileName = dir.getName().toLowerCase(Locale.ROOT);
				if (dir.isFile() && (fileName.endsWith(".zip") || fileName.endsWith(".7z") || fileName.endsWith(".rar")))
					result.add(new Problem("archiveimage", dir.getPath()));
				else if (dir.isFile())
					result.add(new Problem("mountfile", m.toString()));
				else if (dir.isDirectory() && (dir.getParentFile() == null) && !type.equals("cdrom") && !type.equals("floppy"))
					result.add(new Problem("rootmount", String.valueOf(m.getDrive()), dir.getPath()));
			} else if (m instanceof ImageMount im) {
				// "imgmount D D:\\CD Images\\game.iso": read as two images "D:\\CD" and "Images\\game.iso", DOSBox does the same
				String quoted = im.getImgPaths().length > 1 ? withQuotes(m.toString()): null;
				if (quoted != null) {
					result.add(new Problem("mountnoquotes", m.toString(), quoted));
					continue;
				}
				// a CD image as floppy or hard disk ("-t floppy"): DOSBox looks for a FAT file system in it, finds none, mounts nothing
				String ext = FilenameUtils.getExtension(im.getImgPaths()[0].getName()).toLowerCase(Locale.ROOT);
				if ((type.equals("floppy") || type.equals("hdd")) && CD_IMAGE_EXTENSIONS.contains(ext) && im.getCanonicalImgPaths()[0].isFile())
					result.add(new Problem("cdimagewrongtype", m.toString(), "-t " + type));
				// as read (DBGL keeps the line), or as DBGL writes it for DOSBox Staging 0.83
				if (("mount".equals(im.getCommand()) || m.toString().trim().toLowerCase(Locale.ROOT).startsWith("mount ")) && !staging083)
					result.add(new Problem("mountimage", m.toString()));
				for (File img: im.getCanonicalImgPaths()) {
					paths.add(img.getPath());
					String name = img.getName().toLowerCase(Locale.ROOT);
					if (img.isDirectory() && !dosboxX)
						result.add(new Problem("imagefolder", img.getPath()));
					else if (img.isFile() && (img.length() == 0))
						result.add(new Problem("emptyimage", img.getPath()));
					else if (img.isFile() && (name.endsWith(".zip") || name.endsWith(".7z") || name.endsWith(".rar")))
						result.add(new Problem("archiveimage", img.getPath()));
					else if (img.isFile() && FilesUtils.isCueSheet(img.getName())) // also GOG's .ins
						for (String missing: missingCueFiles(img))
							result.add(new Problem("cuemissing", img.getName(), missing));
					// Alcohol 120% / Daemon Tools (alpha 48): DOSBox Staging 0.83 mounts the .mds, it reads the .mdf it names
					if (img.isFile() && (name.endsWith(".mds") || name.endsWith(".mdf"))) {
						if (!staging083 && !dosboxX)
							result.add(new Problem("mdsneedsstaging083", img.getName()));
						else if (FilesUtils.mdsOf(img) != null)
							result.add(new Problem("mdfinsteadofmds", img.getName(), FilesUtils.mdsOf(img).getName()));
					}
				}
			}
		}

		// the configuration of the profile cannot be saved: every change in DBGL fails (e.g. copied from a CD, read-only)
		File own = prof.getConfigurationCanonicalFile();
		if ((own != null) && own.isFile() && !own.canWrite())
			result.add(new Problem("confreadonly", own.getPath()));

		// a drive the configuration of the DOSBox version mounts already: DOSBox skips the profile's line (as DBGL counts it too)
		Set<Character> byDosbox = new LinkedHashSet<>();
		for (Mount m: prof.getDosboxNettoMountingPoints())
			if (!m.isUnmounted())
				byDosbox.add(Character.toUpperCase(m.getDrive()));
		boolean hidden = result.stream().anyMatch(pr -> pr.key().startsWith("drivehidden")); // tells the same, with what to do
		// the game found on the drive of the DOSBox configuration (e.g. "cd \\DGATE" fits it): the profile's line is just not needed,
		// "mount -u" would make the game not found
		File mainFile = StringUtils.isNotBlank(autoexec.getMainWhereDosboxLooks()) ? programFile(new FileLocation(autoexec.getMainWhereDosboxLooks(), FileLocationService.getInstance().dosrootRelative()).getCanonicalFile()): null;
		for (Mount m: autoexec.getMountingpoints()) {
			char drive = Character.toUpperCase(m.getDrive());
			if (m instanceof OverlayMount)
				continue;
			if (m.isUnmounted()) {
				byDosbox.remove(drive); // the profile unmounts it first: its own mount counts then
			} else if (byDosbox.remove(drive) && !hidden) { // once per drive
				File dbDir = dosboxDirOf(prof, drive);
				boolean gameThere = (mainFile != null) && (dbDir != null) && FilesUtils.areRelated(dbDir, mainFile);
				result.add(new Problem(gameThere ? "drivedosboxconfunused2": "drivedosboxconf3", String.valueOf(drive), m.toString(),
					prof.getDosboxVersion() == null ? "": prof.getDosboxVersion().getTitle(),
					prof.getDosboxVersion() == null ? "": prof.getDosboxVersion().getConfigurationCanonicalFile().getName()));
			}
		}

		// drives the own lines of the autoexec go to, that are not mounted
		Set<Character> drives = new LinkedHashSet<>();
		drives.add('Z');
		for (Mount m: netto)
			if (!m.isUnmounted())
				drives.add(Character.toUpperCase(m.getDrive()));
		if (autoexec.isBooter() && StringUtils.isNotBlank(autoexec.getImgDriveletter()))
			drives.add(Character.toUpperCase(autoexec.getImgDriveletter().charAt(0)));
		// "boot hdd.img": DOSBox 0.74 and Staging take a file given to boot as a floppy; a hard disk image does not start so
		// (DOSBox-X takes one larger than 2.88 MB as a hard disk)
		Generation bootGen = prof.getGeneration();
		boolean bootsFloppyOnly = (bootGen != null) && (GenerationAwareConfigurable.familyIdx(bootGen) != GenerationAwareConfigurable.familyIdx(Generation.x_20241001));
		if (autoexec.isBooter() && StringUtils.isBlank(autoexec.getImgDriveletter()) && bootsFloppyOnly)
			for (String img: autoexec.getImgs())
				if (StringUtils.isNotBlank(img)) {
					File f = new FileLocation(img, FileLocationService.getInstance().dosrootRelative()).getCanonicalFile();
					if (f.isFile() && (f.length() > 2881L * 1024))
						result.add(new Problem("boothdd", f.getName(), prof.getDosboxVersion() == null ? "": prof.getDosboxVersion().getTitle()));
				}

		// alpha 53: what the output of DOSBox told at the last start of the profile (RunIssues)
		for (RunIssues.Issue ri: RunIssues.of(prof.getId()))
			if (ri.key().equals("runmt32") && StringUtils.isBlank(ri.args().get(0))) // DOSBox-X does not name its folders: its setting
				result.add(new Problem(ri.key(), dosboxX ? "[midi] mt32.romdir": "[mt32] romdir"));
			else
				result.add(new Problem(ri.key(), ri.args().toArray()));
		// alpha 53: a hard disk image the system in it cannot write to (write protected, or no right to change it): Windows 95
		// and DOS then report write errors and ScanDisk runs at every start (TEST19, EarthSiege II)
		for (Mount m: netto)
			if ((m instanceof org.dbgl.model.conf.mount.ImageMount im) && !m.isUnmounted()
					&& ("hdd".equalsIgnoreCase(StringUtils.defaultString(m.getMountAs())) || (m.getDrive() == '2') || (m.getDrive() == '3')))
				for (File f: im.getCanonicalImgPaths())
					if (f.isFile() && !java.nio.file.Files.isWritable(f.toPath()))
						result.add(new Problem("imagereadonly", f.getPath()));

		// Lure of the Temptress plays its music and also its sound effects on a Roland CM-32L/MT-32: DOSBox Staging and DOSBox-X
		// emulate it (with its ROM), the profile does not use it
		if (autoexec.isDos() && new File(StringUtils.defaultString(autoexec.getMain()).replace('\\', '/')).getName().equalsIgnoreCase("LURE.EXE") && (dosboxX || ((family >= 0) && !ansiDosbox))
				&& !"mt32".equalsIgnoreCase(StringUtils.trimToEmpty(prof.getConfiguration().getValue("midi", "mididevice"))))
			result.add(new Problem("lurecm32l", prof.getTitle()));

		// the Gravis Ultrasound on, its folder (ultradir, the patch files for its music) not on the drive: a hint only
		String gusDir = gusFolderMissing(prof, netto);
		if (gusDir != null) { // with the drive and the folder that is that drive, to copy ULTRASND into
			char gusDrive = Character.toUpperCase(gusDir.charAt(0));
			String host = "";
			for (Mount m: netto)
				if (!m.isUnmounted() && (Character.toUpperCase(m.getDrive()) == gusDrive) && (m instanceof DirMount dm))
					host = dm.getCanonicalPath().getPath();
			// the folder ULTRASND beside DOSBox, if there is one: as a drive of its own (U:, else the first one free from G: on)
			File ultrasnd = MidiRoms.ultrasnd(prof.getDosboxVersion()).dir();
			Character free = null;
			if (ultrasnd != null) {
				Set<Character> used = new java.util.HashSet<>();
				for (Mount m: netto)
					used.add(Character.toUpperCase(m.getDrive()));
				for (Mount m: autoexec.getMountingpoints())
					used.add(Character.toUpperCase(m.getDrive()));
				for (char c: "UGHIJKLMNOPQRSTVWXY".toCharArray())
					if (!used.contains(c)) {
						free = c;
						break;
					}
			}
			if (free != null)
				result.add(new Problem("gusnopatches", gusDir, String.valueOf(gusDrive), host, String.valueOf(free), ultrasnd.getPath()));
			else
				result.add(new Problem("gusnopatches", gusDir, String.valueOf(gusDrive), host));
		}

		// alpha 50: settings that do not go together, what the game itself is set to (the files its setup wrote), what lies
		// in its folder, and games with a documented need
		File gameMain = StringUtils.isBlank(autoexec.getMainWhereDosboxLooks()) ? null
				: programFile(new FileLocation(autoexec.getMainWhereDosboxLooks(), FileLocationService.getInstance().dosrootRelative()).getCanonicalFile());
		boolean stagingFamily = (gen != null) && (family == GenerationAwareConfigurable.familyIdx(Generation.staging_0830));
		result.addAll(GameHints.check(prof, gameMain, netto, result, ansiDosbox, stagingFamily, dosboxX));
		// alpha 53: the ROMs of the MT-32 missing at the last start is told by the check of the settings already (with where they
		// belong), when DBGL sees it there too
		if (result.stream().anyMatch(p -> p.key().equals("mt32norom")))
			result.removeIf(p -> p.key().equals("runmt32"));

		// umlauts in the [autoexec] in another charset than the DOSBox version reads it with: the folder is not found
		ConfCharset.Mismatch cm = ConfCharset.mismatch(prof, prof.getDosboxVersion());
		if (cm != null)
			result.add(new Problem("charsetmismatch", cm.file().displayName(), cm.needed().displayName(), prof.getDosboxVersion().getTitle(), cm.line()));
		// Staging or DOSBox-X on Windows and a folder with an umlaut: Staging 0.83 crashed right after starting, DOSBox-X does not
		// find it (both tested, in either charset)
		String umlaut = ConfCharset.umlautFolder(prof);
		if (umlaut != null)
			result.add(new Problem(ConfCharset.umlautProblem(prof.getDosboxVersion()), umlaut, prof.getDosboxVersion().getTitle()));

		// "mount C SPIELE -pr": relative to the folder of the configuration file DOSBox read last, DBGL cannot check that
		for (Mount m: autoexec.getMountingpoints())
			if (relativeToConf(m))
				result.add(new Problem("mountpr", m.toString()));
		for (Mount m: prof.getDosboxNettoMountingPoints())
			if (relativeToConf(m))
				result.add(new Problem("mountprdosbox", m.toString(), prof.getDosboxVersion().getTitle(), prof.getDosboxVersion().getConfigurationCanonicalFile().getName()));

		// the lines as DOSBox runs them, after the [autoexec] of the configuration of the DOSBox version: "mount -u C" and C: not
		// mounted again, "C:" before "mount C ...", a relative "cd" after that configuration changed the folder of the drive
		for (Problem pr: autoexecOrder(prof)) {
			result.add(pr);
			if (!pr.key().equals("cdquote") && !pr.key().equals("cdonz"))
				drives.add(((String)pr.args()[0]).charAt(0)); // told, not again as "not mounted"
		}

		// the drive of a mount line DBGL cannot read: told below, with the cause
		for (String line: ownLines(autoexec)) {
			Matcher md = MOUNT_DRIVE.matcher(line);
			if (md.find())
				drives.add(Character.toUpperCase(md.group(1).charAt(0)));
		}
		for (String line: ownLines(autoexec)) {
			Matcher dl = DRIVE_LINE.matcher(line);
			if (dl.find() && !drives.contains(Character.toUpperCase(dl.group(1).charAt(0)))) {
				result.add(new Problem("drivenotmounted", dl.group(1).toUpperCase(Locale.ROOT), line.trim()));
				drives.add(Character.toUpperCase(dl.group(1).charAt(0))); // once per drive
			}
		}

		// mount lines DBGL could not read (kept as they were): two commands in one line ("mount -u C mount C ..." - DOSBox only
		// unmounts and skips the rest, the drive is then not mounted at all), or other ones DOSBox will hardly take either
		for (String line: ownLines(autoexec)) {
			if (!MOUNT_LINE.matcher(line).find())
				continue;
			Matcher two = UNMOUNT_AND_MORE.matcher(line);
			String quoted = withQuotes(line);
			if (two.find())
				result.add(new Problem("mounttwocommands", line.trim(), two.group(1).toUpperCase(Locale.ROOT)));
			else if (quoted != null) // a path with spaces, without quotes: DOSBox takes the first word only
				result.add(new Problem("mountnoquotes", line.trim(), quoted));
			else
				result.add(new Problem("mountunreadable", line.trim()));
		}

		// files settings point to: a soundfont or MT-32 ROMs missing and there is no music, a mapper file and the keys are not set
		for (String[] s: missingSettingFiles(prof)) {
			paths.add(s[1]);
			result.add(new Problem("settingfilemissing", s[0], s[1]));
		}

		// settings DOSBox Staging 0.83 lists as deprecated: renamed (e.g. disney -> lpt_dac = disney), gone or replaced
		Set<String> handled = new java.util.HashSet<>();
		for (StagingChange ch: stagingChanges(prof)) {
			handled.add("[" + ch.old().section() + "] " + ch.old().item());
			String old = "[" + ch.old().section() + "] " + ch.old().item();
			String kind = ch.old().kind().equals("disney") && (ch.newValue() == null) ? "removed": ch.old().kind(); // disney=false: nothing instead
			switch (kind) {
				case "removed" -> result.add(new Problem("removedsetting", old));
				case "autogone" -> result.add(new Problem("autogone0831", old, ch.value()));
				case "manual" -> result.add(new Problem("replacedsetting", old, "[" + ch.old().newSection() + "] " + ch.old().newItem()));
				default -> result.add(new Problem("renamedsetting", old, "[" + ch.old().newSection() + "] " + ch.old().newItem() + (ch.newValue() == null ? "" : " = " + ch.newValue())));
			}
		}

		// alpha 50: deprecated but still taken by DOSBox Staging 0.82 and later, as its log says ("The 'cycles' setting is
		// deprecated but still accepted"). They were told as settings this version does not know and ignores.
		if (((prof.getGeneration() == Generation.staging_0830) || (prof.getGeneration() == Generation.staging_0820)) && (prof.getDosboxVersion() != null)) {
			Configuration ownConf = prof.getConfiguration(), ofVersion = prof.getDosboxVersion().getConfiguration();
			for (String[] a: STAGING_ACCEPTED) {
				String old = "[" + a[0] + "] " + a[1];
				if (ownConf.hasValue(a[0], a[1]) && ((ofVersion == null) || !ofVersion.hasValue(a[0], a[1])) && handled.add(old))
					result.add(new Problem("acceptedsetting", old, "[" + a[2] + "] " + a[3]));
			}
		}

		// a setting in the wrong section (e.g. memsize in [dos] instead of [dosbox]): DOSBox looks for it in its own section only
		for (Misplaced mp: misplacedSettings(prof)) {
			String old = "[" + mp.section() + "] " + mp.item();
			if (!handled.add(old))
				continue;
			if (mp.targets().size() > 1)
				result.add(new Problem("wrongsectionmany", old, mp.targets().stream().map(t -> "[" + t + "]").collect(java.util.stream.Collectors.joining(", "))));
			else if (mp.alreadySet() != null)
				result.add(new Problem("wrongsectiondup", old, "[" + mp.targets().get(0) + "]", mp.alreadySet()));
			else
				result.add(new Problem("wrongsection", old, "[" + mp.targets().get(0) + "]"));
		}

		// a setting of another DOSBox version (e.g. gusrate of DOSBox 0.74 in a profile for DOSBox Staging): no effect here
		for (String[] o: otherVersionSettings(prof, versions)) {
			String old = "[" + o[0] + "] " + o[1];
			if (handled.add(old))
				result.add(new Problem("othersetting2", old, o[2]));
		}

		// a word for on/off this DOSBox does not understand (e.g. "1" for DOSBox Staging, "no" for DOSBox 0.74 or DOSBox-X)
		result.addAll(switchWords(prof));

		// settings this DOSBox version does not know: it ignores them (e.g. left after switching from another kind of DOSBox)
		List<String> unknown = new ArrayList<>(unknownSettings(prof));
		unknown.removeAll(handled);
		if (!unknown.isEmpty()) // a few, shortened: a broken file may hold hundreds of lines of rubbish
			result.add(new Problem("unknownsettings", unknown.stream().limit(MAX_LISTED).map(u -> StringUtils.abbreviate(u, 60)).collect(java.util.stream.Collectors.joining(", "))
				+ (unknown.size() > MAX_LISTED ? ", … (+" + (unknown.size() - MAX_LISTED) + ")": "")));

		// the lines of the file itself: what DOSBox cannot read, a setting twice, a number out of range, a command or option
		// this kind of DOSBox does not have (since alpha 46)
		result.addAll(ConfLines.check(prof));

		// paths Windows or an old DOSBox cannot open
		if (windows) {
			CharsetEncoder encoder = systemCharset.newEncoder();
			for (String p: paths) {
				if (p.length() > MAX_WINDOWS_PATH)
					result.add(new Problem("pathtoolong", p.length(), p));
				if (ansiDosbox && !encoder.canEncode(p))
					result.add(new Problem("nonansipath", p));
			}
		}
		return result;
	}

	/**
	 * "mount C D:\\Spiele DOS\\DGATE": a path with spaces, without quotes, that exists as a whole
	 *
	 * @return the line with the path in quotes, or null
	 */
	public static String withQuotes(String line) {
		if (line.indexOf('"') != -1)
			return null;
		String[] tokens = line.trim().split("\\s+");
		if ((tokens.length < 4) || !MOUNT_DRIVE.matcher(line).find())
			return null;
		int end = 2;
		while ((end < tokens.length) && !tokens[end].startsWith("-"))
			end++;
		if (end - 2 < 2)
			return null;
		String path = String.join(" ", Arrays.copyOfRange(tokens, 2, end));
		if (!new FileLocation(path, FileLocationService.getInstance().dosrootRelative()).getCanonicalFile().exists())
			return null;
		StringBuilder sb = new StringBuilder(tokens[0]).append(' ').append(tokens[1]).append(" \"").append(path).append('"');
		for (int i = end; i < tokens.length; i++)
			sb.append(' ').append(tokens[i]);
		return sb.toString();
	}

	private static final Pattern PR_OPTION = Pattern.compile("(?:^|\\s)-pr(?:\\s|$)", Pattern.CASE_INSENSITIVE);

	/** "mount C SPIELE -pr": DOSBox takes the relative path from the folder of the configuration file it read last */
	public static boolean relativeToConf(Mount m) {
		if (m.isUnmounted() || !PR_OPTION.matcher(m.toString()).find())
			return false;
		if (m instanceof ImageMount im)
			return Arrays.stream(im.getImgPaths()).anyMatch(p -> !p.isAbsolute());
		return (m instanceof DirMount dm) && !dm.getPath().isAbsolute();
	}

	/** a "-pr" mount with a relative path, in the profile or in the configuration of its DOSBox version */
	public static boolean hasPrMount(Profile prof) {
		return prof.getConfiguration().getAutoexec().getMountingpoints().stream().anyMatch(ProfileChecks::relativeToConf)
				|| prof.getDosboxNettoMountingPoints().stream().anyMatch(ProfileChecks::relativeToConf);
	}

	private static final Set<String> CD_IMAGE_EXTENSIONS = Set.of("iso", "cue", "gog", "ins", "game", "inst", "mdf", "mds", "ccd", "chd");

	/**
	 * The folder of the patch files of the Gravis Ultrasound (ultradir, C:\ULTRASND by default) if the GUS is on and the folder
	 * is not on the drive it names (a folder mounted there); null if it is there, the GUS is off, or DBGL cannot tell (e.g. an
	 * image on that drive).
	 */
	static String gusFolderMissing(Profile prof, List<Mount> netto) {
		Configuration conf = prof.getConfiguration();
		Configuration dosbox = prof.getDosboxVersion() == null ? null: prof.getDosboxVersion().getConfiguration();
		String gus = conf.hasValue("gus", "gus") ? conf.getValue("gus", "gus"): (dosbox != null) && dosbox.hasValue("gus", "gus") ? dosbox.getValue("gus", "gus"): null;
		if ((gus == null) || !SWITCH_ON.contains(gus.trim().toLowerCase(Locale.ROOT)))
			return null;
		String dir = conf.hasValue("gus", "ultradir") ? conf.getValue("gus", "ultradir"): (dosbox != null) && dosbox.hasValue("gus", "ultradir") ? dosbox.getValue("gus", "ultradir"): "C:\\ULTRASND";
		dir = StringUtils.trim(dir);
		if ((dir.length() < 3) || (dir.charAt(1) != ':'))
			return null;
		char drive = Character.toUpperCase(dir.charAt(0));
		Mount on = null;
		for (Mount m: netto)
			if (!m.isUnmounted() && (Character.toUpperCase(m.getDrive()) == drive))
				on = m;
		if (!(on instanceof DirMount dm) || (on instanceof PhysFsMount))
			return null; // not mounted (told otherwise), or an image: not looked into
		File f = dm.getCanonicalPath();
		for (String part: dir.substring(2).split("[\\\\/]+")) {
			if (part.isEmpty())
				continue;
			// as DOSBox finds it: whatever the case, or by its short name (ULTRAS~1)
			File parent = f;
			String[] names = parent.list();
			String host = org.dbgl.model.conf.DosboxShortNames.toHostName(parent, part);
			File next = names == null ? null: Arrays.stream(names).filter(n -> n.equalsIgnoreCase(part) || n.equalsIgnoreCase(host)).findFirst().map(n -> new File(parent, n)).orElse(null);
			if ((next == null) || !next.isDirectory())
				return dir;
			f = next;
		}
		return null;
	}

	private static final Set<String> SWITCH_ON = Set.of("1", "enabled", "true", "on", "yes"), SWITCH_OFF = Set.of("0", "disabled", "false", "off", "no", "none");
	/** what DOSBox Staging 0.82 and 0.83 understand (parse_bool_setting): not 1 and 0; else it takes the default value */
	private static final Set<String> STAGING_SWITCH = Set.of("enabled", "true", "on", "yes", "disabled", "false", "off", "no", "none");
	/** what DOSBox 0.74, its offspring and DOSBox-X understand (Value::set_bool): not yes, no, none; such a word counts as on */
	private static final Set<String> OLD_SWITCH = Set.of("1", "enabled", "true", "on", "0", "disabled", "false", "off");
	/** the value in the configuration of the DOSBox version that makes it a setting for on/off */
	private static final Set<String> OWN_SWITCH = Set.of("on", "off", "true", "false", "yes", "no"); // not disabled: e.g. serial3 = disabled is a device
	private static final String[][] SWITCH_PAIRS = {{"on", "off"}, {"true", "false"}, {"enabled", "disabled"}, {"yes", "no"}, {"1", "0"}};

	/**
	 * The settings for on/off (no values documented, the value in the configuration of the DOSBox version is a word for on or
	 * off) with a word this DOSBox does not understand; told with the same meaning written as that configuration writes it.
	 */
	static List<Problem> switchWords(Profile prof) {
		List<Problem> result = new ArrayList<>();
		DosboxVersion db = prof.getDosboxVersion();
		Generation gen = prof.getGeneration();
		if ((db == null) || (gen == null) || (gen == Generation.staging_0810)) // 0.81: not checked in its source
			return result;
		boolean staging = GenerationAwareConfigurable.familyIdx(gen) == GenerationAwareConfigurable.familyIdx(Generation.staging_0830);
		Set<String> understood = staging ? STAGING_SWITCH: OLD_SWITCH;
		Configuration conf = prof.getConfiguration();
		for (String section: conf.getSectionNames()) {
			for (String item: conf.getItemNames(section)) {
				String value = StringUtils.trim(conf.getValue(section, item)).toLowerCase(Locale.ROOT);
				if ((!SWITCH_ON.contains(value) && !SWITCH_OFF.contains(value)) || understood.contains(value) || !db.getConfiguration().hasValue(section, item))
					continue;
				String own = StringUtils.trim(db.getConfiguration().getValue(section, item)).toLowerCase(Locale.ROOT);
				if (!OWN_SWITCH.contains(own) || (db.getConfValueOptions().get(section, item) != null))
					continue; // not a setting for on/off (0 or 1 may be a number, none a name), or one with its values documented
				boolean on = SWITCH_ON.contains(value);
				String written = on ? "true": "false";
				for (String[] pair: SWITCH_PAIRS)
					if ((pair[0].equals(own) || pair[1].equals(own)) && understood.contains(pair[0]))
						written = on ? pair[0]: pair[1];
				result.add(new Problem(staging ? "boolwordstaging": "boolwordold", "[" + section + "] " + item, conf.getValue(section, item).trim(), written, db.getTitle()));
			}
		}
		return result;
	}

	/**
	 * The paths "Check profiles" and the list do not tell as missing, as another message tells the cause: the parts of an image path
	 * with spaces without quotes, and the paths of "-pr" mounts.
	 */
	public static Set<String> pathsToldOtherwise(Profile prof) {
		Set<String> result = new LinkedHashSet<>();
		List<Mount> mounts = new ArrayList<>(prof.getConfiguration().getAutoexec().getMountingpoints());
		mounts.addAll(prof.getDosboxNettoMountingPoints());
		for (Mount m: mounts) {
			boolean unquoted = (m instanceof ImageMount im) && (im.getImgPaths().length > 1) && (withQuotes(m.toString()) != null);
			if (!unquoted && !relativeToConf(m))
				continue;
			if (m instanceof ImageMount im)
				Arrays.stream(im.getCanonicalImgPaths()).forEach(f -> result.add(f.getPath()));
			else if (m instanceof DirMount dm) {
				result.add(dm.getCanonicalPath().getPath());
				// and the program on it
				String main = prof.getConfiguration().getAutoexec().getMainWhereDosboxLooks();
				if (StringUtils.isNotBlank(main)) {
					File f = new FileLocation(main, FileLocationService.getInstance().dosrootRelative()).getCanonicalFile();
					if (FilesUtils.areRelated(dm.getCanonicalPath(), f))
						result.add(f.getPath());
				}
			}
		}
		return result;
	}

	/** the names on the way to the program that DOS can't use, with the short names DOSBox gives them: "DEATHGATE → DEATHG~1" */
	public static String longDosNames(File main, List<Mount> netto) {
		for (Mount m: netto) {
			if (!(m instanceof DirMount dm) || (m instanceof PhysFsMount) || m.isUnmounted())
				continue;
			File root = dm.getCanonicalPath();
			if (!FilesUtils.areRelated(root, main))
				continue;
			java.nio.file.Path rel = root.toPath().relativize(main.toPath());
			List<String> dos = org.dbgl.model.conf.DosboxShortNames.toDosPath(root, rel.toFile());
			List<String> names = new ArrayList<>();
			for (int i = 0; i < rel.getNameCount() && i < dos.size(); i++)
				if (!rel.getName(i).toString().equalsIgnoreCase(dos.get(i)))
					names.add(rel.getName(i) + " → " + dos.get(i));
			return String.join(", ", names);
		}
		return "";
	}

	private static final Pattern UNMOUNT_ONLY = Pattern.compile("^(?:img)?mount\\s+(?:-u\\s+([A-Za-z]):?|([A-Za-z]):?\\s+-u)\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern MOUNT_OF = Pattern.compile("^(?:img)?mount\\s+([A-Za-z]):?\\s+\\S", Pattern.CASE_INSENSITIVE);
	private static final Pattern CUSTOM_MARKER = Pattern.compile("(?i)^REM\\s+/?(?:START|PRE-LAUNCH|POST-LAUNCH|FINISH)\\s*$");
	private static final Pattern DRIVE_CHANGE = Pattern.compile("^([A-Za-z]):\\\\?$");
	private static final Pattern CD = Pattern.compile("^(?:cd|chdir)(?:\\s+|(?=[\\\\/.]))(.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern PROGRAM_CALL = Pattern.compile("(?i)^(?:[A-Z]:)?[^\\s%]*?([A-Z0-9_~$!#&-]{1,8})(?:\\.(EXE|COM|BAT))?$");
	/** commands of the DOSBox shell and programs on its drive Z: */
	private static final Set<String> DOSBOX_COMMANDS = Set.of(
		"cd", "chdir", "cls", "copy", "date", "del", "erase", "dir", "echo", "exit", "goto", "if", "md", "mkdir", "rd", "rmdir", "ren", "rename", "rem",
		"set", "shift", "time", "type", "ver", "vol", "path", "prompt", "call", "choice", "lh", "loadhigh", "break", "attrib", "move", "label", "more",
		"mem", "mode", "ctty", "truename", "verify", "ls", "help", "for", "mount", "imgmount", "boot", "config", "mixer", "keyb", "loadfix", "loadrom",
		"rescan", "intro", "mousectl", "serial", "autotype", "imgmake", "imgswap", "cpuid", "license", "tree", "xcopy", "deltree", "find", "sys", "mscdex",
		"mcdex", "setver", "subst", "a20gate", "biostest", "capmouse", "clip", "color", "country", "debug", "dsxmenu", "flagsave", "int2fdbg", "nmitest",
		"prinfo", "shutdown", "start", "vesamoed", "vfrcrate", "where", "addkey", "dx-capture", "fcbs", "files", "lfnfor", "pause", "popd", "pushd",
		// alpha 50: IPXNET of DOSBox, and what batch files of the time call of DOS itself (DOSBox says "Illegal command" and goes on)
		"ipxnet", "smartdrv", "share", "doskey", "fastopen", "nlsfunc", "lock", "unlock", "emm386", "himem", "setlocal", "endlocal");

	/** the lines of the [autoexec] section of a configuration file as they are written (DOSBox runs them in this order) */
	public static List<String> autoexecLines(File conf) {
		List<String> result = new ArrayList<>();
		if ((conf == null) || !conf.isFile())
			return result;
		try {
			boolean in = false;
			for (String line: new String(Files.readAllBytes(conf.toPath()), StandardCharsets.ISO_8859_1).split("\\R")) {
				String t = line.replace("\u00EF\u00BB\u00BF", "").trim(); // a byte order mark, read as ISO-8859-1
				if (t.startsWith("[") && t.endsWith("]")) {
					in = t.equalsIgnoreCase("[autoexec]");
					continue;
				}
				String u = t.startsWith("@") ? t.substring(1).trim(): t;
				if (in && CUSTOM_MARKER.matcher(u).matches()) // the own sections of DBGL: where DBGL writes them again matters
					result.add(u.toUpperCase(Locale.ROOT));
				else if (in && !u.isEmpty() && !u.toLowerCase(Locale.ROOT).startsWith("rem") && !u.startsWith("::") && !u.startsWith("#"))
					result.add(u);
			}
		} catch (IOException e) {
			// not readable: told elsewhere
		}
		return result;
	}

	/** what the [autoexec] of the configuration of the DOSBox version leaves: the current drive, the folder of each drive, the programs it starts */
	record DosboxAutoexec(char drive, java.util.Map<Character, String> dirs, List<String> programs) {
	}

	/** read once per configuration file of a DOSBox version as long as it does not change (it is the same for thousands of profiles) */
	private static final java.util.Map<String, Object[]> DOSBOX_LINES = new java.util.concurrent.ConcurrentHashMap<>();

	@SuppressWarnings("unchecked")
	private static List<String> dosboxAutoexecLines(File conf) {
		if (conf == null)
			return List.of();
		String stamp = conf.lastModified() + "/" + conf.length();
		Object[] cached = DOSBOX_LINES.get(conf.getPath());
		if ((cached != null) && cached[0].equals(stamp))
			return (List<String>)cached[1];
		List<String> lines = List.copyOf(autoexecLines(conf));
		DOSBOX_LINES.put(conf.getPath(), new Object[] {stamp, lines});
		return lines;
	}

	static DosboxAutoexec runDosboxAutoexec(DosboxVersion db, boolean skipped) {
		char drive = 'Z';
		java.util.Map<Character, String> dirs = new java.util.HashMap<>();
		List<String> programs = new ArrayList<>();
		if ((db != null) && db.isMultiConfig() && !skipped) {
			for (String line: dosboxAutoexecLines(db.getConfigurationCanonicalFile())) {
				String lower = line.toLowerCase(Locale.ROOT);
				Matcher dc = DRIVE_CHANGE.matcher(line), cd = CD.matcher(line);
				if (lower.equals("exit") || lower.startsWith("exit "))
					break;
				if (dc.matches()) {
					drive = Character.toUpperCase(dc.group(1).charAt(0));
				} else if (cd.matches()) {
					String arg = cd.group(1).trim();
					char d = drive;
					if ((arg.length() >= 2) && (arg.charAt(1) == ':')) {
						d = Character.toUpperCase(arg.charAt(0));
						arg = arg.substring(2);
					}
					if (arg.startsWith("\\") || arg.startsWith("/"))
						dirs.put(d, arg.replace('/', '\\').replaceAll("\\\\+$", ""));
					else if (!arg.isEmpty())
						dirs.put(d, (dirs.getOrDefault(d, "") + "\\" + arg).replaceAll("\\\\+$", ""));
				} else if (lower.equals("pause") || isProgramCall(line)) {
					programs.add(line);
				}
			}
		}
		dirs.values().removeIf(String::isEmpty);
		return new DosboxAutoexec(drive, dirs, programs);
	}

	/** a line that starts a program (not a command of the DOSBox shell, nor one of its programs on Z:) */
	public static boolean isProgramCall(String line) {
		String t = line.trim();
		String lower = t.toLowerCase(Locale.ROOT);
		for (String prefix: new String[] {"call ", "lh ", "loadhigh "})
			if (lower.startsWith(prefix)) {
				t = t.substring(prefix.length()).trim();
				lower = t.toLowerCase(Locale.ROOT);
			}
		if (t.isEmpty() || lower.startsWith("z:") || MOUNT_LINE.matcher(t).find())
			return false;
		String token = t.split("\\s+")[0];
		Matcher m = PROGRAM_CALL.matcher(token);
		if (!m.matches() || (token.indexOf('=') != -1))
			return false;
		return (m.group(2) != null) || (token.indexOf('\\') != -1) || !DOSBOX_COMMANDS.contains(m.group(1).toLowerCase(Locale.ROOT));
	}

	/**
	 * The lines of the profile as DOSBox runs them, after those of the configuration of the DOSBox version.
	 *
	 * @return "mount -u C" and C: then used without mounting it again (driveunmounted), "C:" before the line that mounts C:
	 *         (drivebeforemount), a relative "cd" after the configuration of the DOSBox version changed the folder (cdrelative);
	 *         the drive letter first in each
	 */
	static List<Problem> autoexecOrder(Profile prof) {
		List<Problem> result = new ArrayList<>();
		DosboxVersion db = prof.getDosboxVersion();
		DosboxAutoexec before = runDosboxAutoexec(db, prof.dosboxAutoexecOverwritten());
		Set<Character> mounted = new LinkedHashSet<>(), fromDosbox = new LinkedHashSet<>(), unmounted = new LinkedHashSet<>(), told = new LinkedHashSet<>();
		mounted.add('Z');
		for (Mount m: prof.getDosboxNettoMountingPoints())
			if (!m.isUnmounted()) {
				mounted.add(Character.toUpperCase(m.getDrive()));
				fromDosbox.add(Character.toUpperCase(m.getDrive()));
			}
		java.util.Map<Character, String> dosboxDirs = new java.util.HashMap<>(before.dirs());
		dosboxDirs.keySet().retainAll(mounted);
		char drive = mounted.contains(before.drive()) ? before.drive(): 'Z';
		List<String> lines = autoexecLines(prof.getConfigurationCanonicalFile());
		String title = db == null ? "": db.getTitle(), conf = db == null ? "": db.getConfigurationCanonicalFile().getName();
		boolean inStart = false, inCustom = false; // in DBGL's own section "At the start" (written before the mount lines), in any own one
		boolean dosboxX = (prof.getGeneration() != null) && (GenerationAwareConfigurable.familyIdx(prof.getGeneration()) == GenerationAwareConfigurable.familyIdx(Generation.x_20241001));
		boolean cdOnZTold = false;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i), lower = line.toLowerCase(Locale.ROOT);
			if (CUSTOM_MARKER.matcher(line).matches()) {
				inCustom = !line.contains("/");
				inStart = inCustom && line.endsWith("START") && !line.contains("-");
				continue;
			}
			if (lower.equals("exit") || lower.startsWith("exit "))
				break;
			Matcher un = UNMOUNT_ONLY.matcher(line), mo = MOUNT_OF.matcher(line), dc = DRIVE_CHANGE.matcher(line), cd = CD.matcher(line);
			if (un.matches()) {
				char d = Character.toUpperCase((un.group(1) != null ? un.group(1): un.group(2)).charAt(0));
				if (mounted.remove(d))
					unmounted.add(d);
				dosboxDirs.remove(d);
				if (drive == d)
					drive = 'Z'; // DOSBox goes to Z: when the current drive is unmounted
			} else if (mo.find() && !UNMOUNT_AND_MORE.matcher(line).find() && !lower.contains("overlay")) {
				char d = Character.toUpperCase(mo.group(1).charAt(0));
				if (mounted.add(d)) {
					unmounted.remove(d);
					dosboxDirs.remove(d);
				}
			} else if (dc.matches()) {
				char d = Character.toUpperCase(dc.group(1).charAt(0));
				boolean mountedLater = false;
				for (int j = i + 1; (j < lines.size()) && !mountedLater; j++) {
					Matcher lm = MOUNT_OF.matcher(lines.get(j));
					mountedLater = lm.find() && (Character.toUpperCase(lm.group(1).charAt(0)) == d) && !UNMOUNT_ONLY.matcher(lines.get(j)).matches();
				}
				// "At the start" is written before the mount lines: a drive the profile mounts is not there yet then (after saving at the latest)
				if (inStart && !fromDosbox.contains(d) && (d != 'Z') && (mounted.contains(d) || mountedLater) && told.add(d))
					result.add(new Problem("startsectiondrive", String.valueOf(d), line));
				if (mounted.contains(d)) {
					drive = d;
					if (line.endsWith("\\"))
						dosboxDirs.remove(d);
				} else if (told.add(d)) {
					String later = null;
					for (int j = i + 1; (j < lines.size()) && (later == null); j++) {
						Matcher lm = MOUNT_OF.matcher(lines.get(j));
						if (lm.find() && (Character.toUpperCase(lm.group(1).charAt(0)) == d) && !UNMOUNT_ONLY.matcher(lines.get(j)).matches())
							later = lines.get(j);
					}
					if (later != null)
						result.add(new Problem("drivebeforemount", String.valueOf(d), line, later));
					else if (unmounted.contains(d))
						result.add(new Problem(fromDosbox.contains(d) ? "driveunmounted": "driveunmountedown", String.valueOf(d), line, title, conf));
				}
			} else if (cd.matches()) {
				String arg = cd.group(1).trim();
				// "cd DSA1"": the quote is part of the name for DOSBox and DOSBox Staging (not found, the folder stays), DOSBox-X drops it
				if (!dosboxX && (arg.chars().filter(c -> c == '"').count() % 2 == 1)) {
					String repaired = line.substring(0, line.length() - cd.group(1).length()) + arg.replace("\"", "");
					result.add(new Problem("cdquote", line, repaired.trim()));
				}
				// "cd \DSA" while still on Z: (before "C:"): DOSBox looks for the folder on Z:, the drive changed to later stays in its root
				if ((drive == 'Z') && !cdOnZTold && !arg.isEmpty() && !arg.equals("\\") && !arg.equals("/") && (arg.indexOf(':') == -1)) {
					List<String> cds = new ArrayList<>();
					String driveLine = null;
					for (int j = i; (j < lines.size()) && (driveLine == null); j++) {
						String lj = lines.get(j);
						Matcher dj = DRIVE_CHANGE.matcher(lj);
						if (dj.matches()) {
							if (Character.toUpperCase(dj.group(1).charAt(0)) == 'Z')
								break;
							driveLine = lj;
						} else if (CD.matcher(lj).matches()) {
							cds.add(lj);
						} else if (UNMOUNT_ONLY.matcher(lj).matches() || lj.toLowerCase(Locale.ROOT).startsWith("exit") || !(MOUNT_OF.matcher(lj).find() || lj.isEmpty()
								|| lj.toLowerCase(Locale.ROOT).startsWith("echo") || lj.toLowerCase(Locale.ROOT).startsWith("rem") || lj.equalsIgnoreCase("cls"))) {
							break; // something else runs on Z: first (a program, a batch file): left as it is
						}
					}
					if (driveLine != null) {
						cdOnZTold = true;
						result.add(new Problem("cdonz", line, driveLine, String.valueOf(Character.toUpperCase(driveLine.charAt(0))), String.join("\n", cds)));
					}
				}
				boolean absolute = arg.startsWith("\\") || arg.startsWith("/") || ((arg.length() >= 2) && (arg.charAt(1) == ':'));
				if (absolute) {
					dosboxDirs.remove((arg.length() >= 2) && (arg.charAt(1) == ':') ? Character.toUpperCase(arg.charAt(0)): drive);
				} else if (!arg.isEmpty() && !arg.startsWith(".") && dosboxDirs.containsKey(drive)) {
					String dir = dosboxDirs.remove(drive);
					result.add(new Problem(inCustom ? "cdrelativecustom": "cdrelative", String.valueOf(drive), line, dir, title, conf, drive + ":" + dir + "\\" + arg, "cd \\" + arg));
				}
			}
		}
		return result;
	}

	/**
	 * A program the batch file of a game starts that belongs to another game it can be combined with: Caesar starts Cohort II
	 * for its battles, if that is installed in the same folder (without it, Caesar fights them itself).
	 */
	public static boolean isCompanion(File bat, String program) {
		String p = FilenameUtils.getBaseName(program).toUpperCase(Locale.ROOT), b = FilenameUtils.getBaseName(bat.getName()).toUpperCase(Locale.ROOT);
		File dir = bat.getParentFile();
		boolean caesar = b.startsWith("CAESAR") || ((dir != null) && dir.getName().toUpperCase(Locale.ROOT).contains("CAESAR"));
		return caesar && p.startsWith("COHORT");
	}

	private static final Pattern BAT_GOTO = Pattern.compile("(?i)goto\\s+:?(\\S+)\\s*"), BAT_IF_EXIST_GOTO = Pattern.compile("(?i)if\\s+(not\\s+)?exist\\s+(\\S+)\\s+goto\\s+:?(\\S+)\\s*");

	/** a label as DOS compares it: its first word, its first eight characters, capitals and small letters the same */
	private static String labelOf(String label) {
		String l = label.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
		return l.length() > 8 ? l.substring(0, 8): l;
	}

	private static final Pattern BARE_NAME = Pattern.compile("[A-Za-z0-9_~$!#&-]{1,8}"), PLAIN_WORDS = Pattern.compile("\\s+[\\p{L}'.,!?\\s]+");

	// "copy a b", "ren a b", "move a b": b; "if exist a", "if not exist a": a; "echo text >a": a
	private static final Pattern BAT_MAKES = Pattern.compile("(?i)^\\s*@?(?:copy|ren|rename|move)\\s+(?:/\\S+\\s+)*\\S+\\s+([^\\s>|]+)"),
			BAT_ASKS = Pattern.compile("(?i)\\bif\\s+(?:not\\s+)?exist\\s+([^\\s>|]+)"), BAT_WRITES = Pattern.compile(">>?\\s*([^\\s>|]+)\\s*$");

	/** the programs a batch file starts (just its name, before any "cd") that are not in its folder: {program, line} */
	public static List<String[]> batMissing(File bat) {
		List<String[]> result = new ArrayList<>();
		File dir = bat.getParentFile();
		try {
			String[] lines = new String(Files.readAllBytes(bat.toPath()), StandardCharsets.ISO_8859_1).split("\\R");
			// alpha 50: not missing is what the batch file makes itself ("copy sst_full.dat sst_full.exe", then "sst_full.exe")
			// and what it asks for before ("if not exist ump.bat goto noump"): an installer and launcher in one does both
			Set<String> own = new HashSet<>(), told = new HashSet<>();
			for (String raw: lines) {
				Matcher made = BAT_MAKES.matcher(raw);
				if (made.find())
					own.add(FilenameUtils.getName(made.group(1).replace('\\', '/')).toLowerCase(Locale.ROOT));
				Matcher asked = BAT_ASKS.matcher(raw);
				while (asked.find())
					own.add(FilenameUtils.getName(asked.group(1).replace('\\', '/')).toLowerCase(Locale.ROOT));
				Matcher written = BAT_WRITES.matcher(raw);
				if (written.find())
					own.add(FilenameUtils.getName(written.group(1).replace('\\', '/')).toLowerCase(Locale.ROOT));
			}
			// the labels, to follow a "goto" that is sure: "goto x", and "if [not] exist file goto x" as the folder is now (the
			// first start of King's Quest VI unpacks with programs that are gone afterwards: "if not exist seq.exe goto continue")
			java.util.Map<String, Integer> labels = new java.util.HashMap<>();
			for (int i = 0; i < lines.length; i++) {
				String l = lines[i].trim();
				if (l.startsWith(":") && !l.startsWith("::"))
					labels.putIfAbsent(labelOf(l.substring(1)), i);
			}
			for (int at = 0; at < lines.length; at++) {
				String raw = lines[at];
				String line = raw.trim();
				if (line.startsWith("@"))
					line = line.substring(1).trim();
				String lower = line.toLowerCase(Locale.ROOT);
				Matcher jump = BAT_GOTO.matcher(line), jumpIf = BAT_IF_EXIST_GOTO.matcher(line);
				String target = null;
				if (jump.matches())
					target = jump.group(1);
				else if (jumpIf.matches()) {
					File asked = new File(dir, org.dbgl.model.conf.DosboxShortNames.toHostName(dir, jumpIf.group(2)));
					boolean there = !jumpIf.group(2).contains("*") && !jumpIf.group(2).contains("?") && !jumpIf.group(2).contains("%") && (asked.exists() || existsIgnoringCase(asked));
					if (there == (jumpIf.group(1) == null))
						target = jumpIf.group(3);
				}
				if (target != null) {
					Integer to = labels.get(labelOf(target));
					if ((to == null) || (to <= at))
						break; // to the end (":eof", no such label) or back: what was to be seen has been seen
					at = to;
					continue;
				}
				if (line.isEmpty() || lower.startsWith("rem") || line.startsWith("::") || line.startsWith(":") || lower.startsWith("echo") || lower.startsWith("if ")
						|| lower.startsWith("set ") || lower.startsWith("goto ") || lower.startsWith("for "))
					continue;
				if (CD.matcher(line).matches() || DRIVE_CHANGE.matcher(line).matches() || lower.startsWith("path"))
					break; // another folder from here on: not checked
				for (String prefix: new String[] {"call ", "lh ", "loadhigh "})
					if (lower.startsWith(prefix)) {
						line = line.substring(prefix.length()).trim();
						lower = line.toLowerCase(Locale.ROOT);
					}
				String token = line.split("\\s+")[0];
				Matcher m = PROGRAM_NAME.matcher(token);
				// alpha 50: a program is mostly called without its ending ("wrms", "u8"): looked for as .COM, .EXE and .BAT. Not
				// taken as a call is a line of plain words (text that was meant for "echo")
				boolean bare = !m.matches() && BARE_NAME.matcher(token).matches() && !PLAIN_WORDS.matcher(line.substring(token.length())).matches();
				if ((!m.matches() && !bare) || DOSBOX_COMMANDS.contains(FilenameUtils.getBaseName(token).toLowerCase(Locale.ROOT)))
					continue;
				String name = token.toLowerCase(Locale.ROOT);
				if (own.contains(name) || (!name.contains(".") && own.stream().anyMatch(o -> FilenameUtils.getBaseName(o).equals(name) && PROGRAM_NAME.matcher(o).matches())))
					continue;
				boolean there = false;
				for (String ending: bare ? new String[] {".COM", ".EXE", ".BAT"}: new String[] {""}) {
					File f = new File(dir, org.dbgl.model.conf.DosboxShortNames.toHostName(dir, token + ending));
					there |= f.exists() || existsIgnoringCase(f);
				}
				if (!there && told.add(name)) // once for each program
					result.add(new String[] {token.toUpperCase(Locale.ROOT), raw.trim()});
			}
		} catch (IOException e) {
			// not readable: nothing to tell about it
		}
		return result;
	}

	/** the programs the [autoexec] of the configuration of the DOSBox version starts before every game (e.g. a menu): {line, key to hide it} */
	/** the release number only: "0.83.0-abc123" -> "0.83.0", "2026.10.01 SDL2" -> "2026.10.01" */
	public static String releaseOf(String version) {
		java.util.regex.Matcher m = Pattern.compile("\\d+(?:\\.\\d+)*").matcher(StringUtils.defaultString(version));
		return m.find() ? m.group(): StringUtils.defaultString(version);
	}

	public static List<Problem> dosboxVersionHints(DosboxVersion db) {
		List<Problem> result = new ArrayList<>();
		if ((db == null) || !FilesUtils.isReadableFile(db.getConfigurationCanonicalFile()))
			return result;
		// alpha 50: newer than the versions DBGL knows: treated as the newest one it knows
		if (DosboxSupport.status(db.getFamily(), db.getVersion()) == DosboxSupport.Status.NEWER)
			result.add(new Problem("dosboxnewer", db.getTitle(), db.getFamily() + " " + db.getVersion(), db.getFamily() + " " + DosboxSupport.newestKnown(db.getFamily())));
		boolean skipped = "overwrite".equalsIgnoreCase(StringUtils.trim(db.getConfiguration().getValue("dosbox", "autoexec_section")));
		for (String line: runDosboxAutoexec(db, skipped).programs())
			result.add(new Problem("dosboxconfprogram", db.getTitle(), db.getConfigurationCanonicalFile().getName(), line));
		// alpha 50: the file names an older version than the entry (the program was replaced, the file kept): new settings are
		// not in it, and what it says about values and defaults is of the old version
		String written = DosboxVersionDetector.versionOfConf(db.getConfigurationCanonicalFile());
		if ((written != null) && releaseOf(written).matches("\\d+\\.\\d.*") && releaseOf(db.getVersion()).matches("\\d+\\.\\d.*") // not "SVN", "r4483"
				&& (org.dbgl.util.DosboxUpdateChecker.compareVersions(releaseOf(written), releaseOf(db.getVersion())) < 0))
			result.add(new Problem("dosboxconfolder", db.getTitle(), db.getConfigurationCanonicalFile().getName(), written, db.getVersion()));
		// alpha 49: a configuration kept from 0.83.0 for DOSBox Staging 0.83.1: "auto" is no value there any more
		if (staging0831(db))
			for (String[] gone: GONE_0831)
				if (gone[2].equalsIgnoreCase(StringUtils.trimToEmpty(db.getConfiguration().getValue(gone[0], gone[1]))))
					result.add(new Problem("dosboxconfauto0831", db.getTitle(), db.getConfigurationCanonicalFile().getName(), "[" + gone[0] + "] " + gone[1], gone[2]));
		// alpha 53: DOSBox Staging also reads its configuration in the folder of the user: what of it counts
		Problem primary = StagingPrimaryConf.hint(db);
		if (primary != null)
			result.add(primary);
		return result;
	}

	/** the lines of the autoexec DBGL does not make itself: the own sections, and lines it keeps as they were */
	/** a line that does not start a program: "@echo off", "cls", "exit", "rem ...", an empty line */
	static boolean startsNothing(String line) {
		String l = StringUtils.defaultString(line).trim().toLowerCase(Locale.ROOT);
		if (l.startsWith("@"))
			l = l.substring(1).trim();
		return l.isEmpty() || l.equals("echo off") || l.equals("echo on") || l.equals("cls") || l.equals("exit") || l.startsWith("rem ") || l.equals("rem") || l.startsWith("::")
			|| l.startsWith("echo ") || l.startsWith("echo.") || l.startsWith("mount ") || l.startsWith("imgmount ") || l.matches("[a-z]:");
	}

	static List<String> ownLines(Autoexec autoexec) {
		List<String> result = new ArrayList<>();
		for (String block: autoexec.getCustomSections())
			for (String line: StringUtils.defaultString(block).split("\\R"))
				if (StringUtils.isNotBlank(line) && !line.trim().toUpperCase(Locale.ROOT).startsWith("@REM"))
					result.add(line);
		for (Autoexec.UnknownLine line: autoexec.getPlainLines())
			result.add(line.text());
		return result;
	}

	/**
	 * "not on a mounted drive"; on Windows, where a folder is the same whatever the case of its name, the cause may be just
	 * that: DBGL compares the names exactly (e.g. "d:\spiele" typed in a DOSBox configuration, "D:\Spiele\..." chosen in DBGL)
	 */
	static Problem notReachable(Profile prof, File file, List<Mount> netto, boolean windows) {
		if (windows) {
			String p = file.getPath().toLowerCase(Locale.ROOT);
			List<Mount> fromDosbox = prof.getDosboxNettoMountingPoints();
			for (Mount m: netto) {
				if (m.isUnmounted() || !(m instanceof DirMount dm))
					continue;
				String dir = dm.getCanonicalPath().getPath();
				String d = dir.toLowerCase(Locale.ROOT);
				if (!p.startsWith(d.endsWith(File.separator) ? d: d + File.separator))
					continue;
				String drive = String.valueOf(Character.toUpperCase(m.getDrive())), real = realName(dm.getCanonicalPath());
				if (fromDosbox.stream().anyMatch(x -> x == m) && (prof.getDosboxVersion() != null))
					return new Problem("casemismatchdb3", file.getPath(), drive, dir, prof.getDosboxVersion().getConfigurationCanonicalFile().getName(), real, prof.getDosboxVersion().getTitle());
				return new Problem("casemismatchprof2", file.getPath(), drive, dir, real);
			}
		}
		return new Problem("notreachable", file.getPath());
	}

	/** how a folder is really called on the disk (Windows keeps the case of a name, but finds it whatever the case asked for) */
	static String realName(File dir) {
		try {
			return dir.toPath().toRealPath().toString();
		} catch (IOException | RuntimeException e) {
			return dir.getPath();
		}
	}

	/** what the mount line needs that is not there: its folder, or the images of it that are missing */
	public static String missingOf(Mount m) {
		if (m instanceof ImageMount im)
			return java.util.Arrays.stream(im.getCanonicalImgPaths()).filter(f -> !f.exists()).map(File::getPath).collect(java.util.stream.Collectors.joining(", "));
		if (m instanceof DirMount dm)
			return dm.getCanonicalPath().getPath();
		return "";
	}

	/**
	 * The game is not there where DOSBox looks for it, because the configuration of the DOSBox version mounts the drive
	 * already (DOSBox skips the profile's line), but it is on the profile's own drive: tell that, with what to do.
	 */
	static java.util.Optional<Problem> hiddenByDosbox(Profile prof, File main) {
		if (main.exists() || (prof.getDosboxVersion() == null))
			return java.util.Optional.empty();
		for (Mount db: prof.getDosboxNettoMountingPoints()) {
			if (db.isUnmounted() || !(db instanceof DirMount dbDir))
				continue;
			File relative = FilesUtils.makeRelativeTo(dbDir.getCanonicalPath(), main);
			if (relative.isAbsolute())
				continue; // not on this drive
			for (Mount own: prof.getConfiguration().getAutoexec().getMountingpoints()) {
				if (own.isUnmounted() && (Character.toUpperCase(own.getDrive()) == Character.toUpperCase(db.getDrive())))
					return java.util.Optional.empty(); // unmounted first: the profile's drive counts
				if ((own instanceof DirMount ownDir) && !(own instanceof OverlayMount) && (Character.toUpperCase(own.getDrive()) == Character.toUpperCase(db.getDrive()))) {
					File there = new File(ownDir.getCanonicalPath(), relative.getPath());
					if (!FilesUtils.existsAsDosProgram(there))
						continue;
					String drive = String.valueOf(Character.toUpperCase(db.getDrive()));
					String dbPath = dbDir.getCanonicalPath().getPath(), ownPath = ownDir.getCanonicalPath().getPath();
					String title = prof.getDosboxVersion().getTitle(), confFile = prof.getDosboxVersion().getConfigurationCanonicalFile().getName();
					// where DOSBox looks ("cd \"), and where the game is on that drive: the profile's folder inside the DOSBox one
					String looks = dosDir(relative.getParentFile() == null ? "": relative.getParent());
					String sub = subPathIgnoringCase(dbPath, ownPath);
					if ((sub != null) && isDosName(sub) && ((relative.getParent() == null) || isDosName(relative.getParent()))) {
						String is = dosDir(sub + (relative.getParent() == null ? "": File.separator + relative.getParent()));
						return java.util.Optional.of(new Problem("drivehiddencd3", main.getName(), drive, dbPath, ownPath, title, confFile, looks, is, own.toString()));
					}
					return java.util.Optional.of(new Problem("drivehidden5", main.getName(), drive, dbPath, ownPath, title, confFile, own.toString()));
				}
			}
		}
		return java.util.Optional.empty();
	}

	/** the folder the configuration of the DOSBox version mounts as this drive, or null */
	static File dosboxDirOf(Profile prof, char drive) {
		for (Mount m: prof.getDosboxNettoMountingPoints())
			if (!m.isUnmounted() && (m instanceof DirMount dm) && (Character.toUpperCase(m.getDrive()) == Character.toUpperCase(drive)))
				return dm.getCanonicalPath();
		return null;
	}

	/** "\" or "\GAMES\DGATE" for a folder relative to the root of a drive */
	public static String dosDir(String relative) {
		String d = StringUtils.strip(StringUtils.defaultString(relative).replace('/', '\\'), "\\");
		return "\\" + d;
	}

	/** the folder below base that path is (Windows: whatever the case), or null if it is not inside base */
	public static String subPathIgnoringCase(String base, String path) {
		String b = StringUtils.stripEnd(base, "/\\"), p = StringUtils.stripEnd(path, "/\\");
		if (p.length() <= b.length() + 1 || !p.regionMatches(!SystemUtils.IS_OS_LINUX, 0, b, 0, b.length()) || ((p.charAt(b.length()) != '\\') && (p.charAt(b.length()) != '/')))
			return null;
		return p.substring(b.length() + 1);
	}

	/** every part a DOS name (8.3, no spaces): usable in "cd" as it is */
	public static boolean isDosName(String path) {
		for (String part: path.split("[/\\\\]+"))
			if (!part.isEmpty() && !part.matches("[A-Za-z0-9_~$!#%&'(){}@^`-]{1,8}(\\.[A-Za-z0-9_~$!#%&'(){}@^`-]{1,3})?"))
				return false;
		return true;
	}

	private static void checkOtherProgram(Profile prof, List<Problem> result, Set<String> paths, Autoexec autoexec, List<Mount> netto, File file, String what, Integer nr, boolean windows) {
		File image = FilesUtils.determineMainFile(file);
		if (!image.equals(file)) { // in a CD, floppy or hard disk image or a zip ("WV1DE.CUE\WVINST.EXE"): the image must be there
			if (!image.exists())
				result.add(nr == null ? new Problem(what + "missing", image.getPath()): new Problem(what + "missing", nr, image.getPath()));
			else
				paths.add(image.getPath());
			return;
		}
		File f = programFile(file);
		if (f == null) {
			result.add(nr == null ? new Problem(what + "missing", file.getPath()): new Problem(what + "missing", nr, file.getPath()));
			return;
		}
		paths.add(f.getPath());
		checkProgram(result, f, what, nr);
		if (!autoexec.canBeReachedUsingMounts(false, f.getPath(), netto))
			result.add(notReachable(prof, f, netto, windows));
	}

	/** a Windows program: DOSBox cannot run it (a Windows 3.x one only in Windows 3.x installed in DOSBox) */
	private static void checkProgram(List<Problem> result, File f, String what, Integer nr) {
		if (f.isDirectory() || (f.isFile() && (f.length() == 0))) { // e.g. copied incompletely
			result.add(new Problem("notaprogram", f.getPath()));
			return;
		}
		String kind = programKind(f);
		if ("pe".equals(kind))
			result.add(new Problem("windowsprogram", f.getPath()));
		else if ("ne".equals(kind))
			result.add(new Problem("win16program", f.getPath()));
	}

	/** the file DOS would start for this name (also with another case, or .com/.exe/.bat), or null if there is none */
	static File programFile(File file) {
		if (file == null)
			return null;
		if (file.isFile())
			return file;
		if (!FilesUtils.existsAsDosProgram(file))
			return null;
		File dir = file.getAbsoluteFile().getParentFile();
		String name = file.getName(), base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')): name;
		String[] names = dir.list();
		if (names != null) {
			for (String n: names)
				if (n.equalsIgnoreCase(name))
					return new File(dir, n);
			for (String n: names)
				if (n.toLowerCase(Locale.ROOT).startsWith(base.toLowerCase(Locale.ROOT) + "."))
					return new File(dir, n);
		}
		return file;
	}

	/** a DOS program's name in the DOS part of a program */
	private static final Pattern PROGRAM_NAME = Pattern.compile("(?i)[A-Z0-9_~$!#&-]{1,8}\\.(?:EXE|COM|BAT)\\b");

	/** what the DOS part of a Windows-only program says (Microsoft's stubs and those of other linkers) */
	private static final Pattern WINDOWS_STUB = Pattern.compile("(?i)requires Microsoft Windows|cannot be run in DOS mode|must be run under Win|requires Windows|Windows (?:is )?required|needs Windows");

	/** names DOS extenders leave in a program: a DOS program, even with a header like a Windows program ("NE", "PE") */
	private static final String[] DOS_EXTENDERS = {"DOS/16M", "DOS16M", "Phar Lap", "PHARLAP", "RUN286", "RUN386", "TNT DOS", "RTM.EXE", "16STUB", "DPMI16BI", "DPMI32VM", "Borland Pascal",
		"WDOSX", "DPMILD32", "HDLD32", "HXLDR32", "PMODE", "DOS32A", "DOS/32A", "CauseWay", "CWSDPMI", "DOS/4G", "DOS4G", "Rational Systems", "OS/286", "OS/386", "Blinker", "EXOSPACE"};

	/**
	 * @return "pe" for a Windows program (32 or 64 bit), "ne" for a Windows 3.x program, null for a DOS program or another
	 *         file. A DOS program has only the "MZ" header, or a DOS extender's ("LE", "LX" e.g. DOS/4GW). 16 bit DOS
	 *         extenders (DOS/16M, Phar Lap 286, Borland's RTM) use the "NE" header too, but not for Windows (target OS byte
	 *         0x36: 2 or 4 is Windows), some 32 bit ones (WDOSX, HX) the "PE" header: those are told by their name in the
	 *         program, e.g. Ultima VIII.
	 */
	public static String programKind(File f) {
		if ((f == null) || !f.isFile() || (f.length() < 0x40))
			return null;
		try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
			int b0 = r.read(), b1 = r.read();
			if (!((b0 == 'M') && (b1 == 'Z')) && !((b0 == 'Z') && (b1 == 'M')))
				return null;
			r.seek(0x3C);
			long offset = Integer.toUnsignedLong(Integer.reverseBytes(r.readInt()));
			if ((offset < 0x40) || (offset + 0x40 > r.length()))
				return null;
			r.seek(offset);
			byte[] header = new byte[0x40];
			r.readFully(header);
			String kind = null;
			if ((header[0] == 'P') && (header[1] == 'E') && (header[2] == 0) && (header[3] == 0))
				kind = "pe";
			else if ((header[0] == 'N') && (header[1] == 'E') && ((header[0x36] == 2) || (header[0x36] == 4))) // for Windows
				kind = "ne";
			if (kind == null)
				return null;
			// a DOS extender's name in the program (its DOS part, or the first 256 KB): a DOS program
			r.seek(0);
			byte[] start = new byte[(int)Math.min(r.length(), 256 * 1024)];
			r.readFully(start);
			String text = new String(start, StandardCharsets.ISO_8859_1);
			for (String name: DOS_EXTENDERS)
				if (text.contains(name))
					return null;
			// "dual mode" (e.g. the setup programs of Westwood and Cryo games): the DOS part is a whole DOS program, DOS runs
			// that one. A Windows-only program has a stub of a few hundred bytes that just says it needs Windows.
			int lastPage = (start[2] & 0xFF) | ((start[3] & 0xFF) << 8), pages = (start[4] & 0xFF) | ((start[5] & 0xFF) << 8);
			int headerSize = ((start[8] & 0xFF) | ((start[9] & 0xFF) << 8)) * 16;
			long dosImage = (pages == 0 ? 0: (pages - 1) * 512L + (lastPage == 0 ? 512: lastPage)) - headerSize;
			String stub = text.substring(0, (int)Math.min(text.length(), Math.max(0, Math.min(offset, 4096))));
			boolean saysWindows = WINDOWS_STUB.matcher(stub).find();
			// a small DOS part that starts another program instead (Lost Eden's INSTALL.EXE runs INSTDOS.EXE): a DOS starter
			boolean startsProgram = PROGRAM_NAME.matcher(stub.substring(Math.min(stub.length(), headerSize))).find();
			if (saysWindows)
				return kind;
			return (dosImage <= 1024) && !startsProgram ? kind: null;
		} catch (IOException e) {
			// not readable: not checked
		}
		return null;
	}

	/** the files a .cue names (FILE lines) that are not there: next to the .cue, or as written */
	public static List<String> missingCueFiles(File cue) {
		List<String> result = new ArrayList<>();
		List<String> lines;
		try {
			if (cue.length() > 1024 * 1024)
				return result; // not a cue sheet
			lines = Files.readAllLines(cue.toPath(), StandardCharsets.ISO_8859_1);
		} catch (IOException e) {
			return result;
		}
		File dir = cue.getAbsoluteFile().getParentFile();
		for (String line: lines) {
			Matcher m = CUE_FILE.matcher(line);
			if (!m.find())
				continue;
			String name = m.group(1) != null ? m.group(1): m.group(2);
			String sys = name.replace('\\', File.separatorChar).replace('/', File.separatorChar);
			File f = new File(sys);
			if (!f.isAbsolute())
				f = new File(dir, sys);
			if (!f.exists() && !existsIgnoringCase(new File(dir, new File(sys).getName())) && !result.contains(name))
				result.add(name);
		}
		return result;
	}

	private static boolean existsIgnoringCase(File f) {
		if (f.exists())
			return true;
		String[] names = f.getAbsoluteFile().getParentFile() == null ? null: f.getAbsoluteFile().getParentFile().list();
		return (names != null) && java.util.Arrays.stream(names).anyMatch(n -> n.equalsIgnoreCase(f.getName()));
	}

	private static final Set<String> FILE_SETTINGS = Set.of("soundfont", "fluid.soundfont", "romdir", "mt32.romdir", "mapperfile", "language");

	/**
	 * The files and folders the profile's own settings name that are not there, as {"[section] item", path}. Only paths are
	 * checked: a plain name (e.g. "default.sf2") DOSBox looks for in its own folders.
	 */
	public static List<String[]> missingSettingFiles(Profile prof) {
		List<String[]> result = new ArrayList<>();
		Configuration conf = prof.getConfiguration();
		DosboxVersion db = prof.getDosboxVersion();
		for (String section: conf.getSectionNames()) {
			for (String item: conf.getItemNames(section)) {
				if (!FILE_SETTINGS.contains(item.toLowerCase(Locale.ROOT)))
					continue;
				String value = StringUtils.strip(StringUtils.defaultString(conf.getValue(section, item)), " \t\"");
				if (value.isEmpty() || (!value.contains("/") && !value.contains("\\")))
					continue;
				String withoutVolume = value.replaceFirst("\\s+\\d{1,3}$", ""); // DOSBox Staging: "soundfont = name.sf2 50"
				List<File> candidates = new ArrayList<>();
				for (String v: new String[] {value, withoutVolume}) {
					File f = new File(v.replace('\\', File.separatorChar).replace('/', File.separatorChar));
					candidates.add(f);
					if (!f.isAbsolute() && (db != null)) {
						if (db.getCwd() != null)
							candidates.add(new File(db.getCwd(), f.getPath()));
						File exe = db.getCanonicalExecutable();
						if ((exe != null) && (exe.getParentFile() != null))
							candidates.add(new File(exe.getParentFile(), f.getPath()));
					}
				}
				if (candidates.stream().noneMatch(File::exists))
					result.add(new String[] {"[" + section + "] " + item, withoutVolume});
			}
		}
		return result;
	}

	/**
	 * Profiles that use the same configuration file: a change to one changes the others (e.g. after editing the database by
	 * hand, or an import). For each such profile the titles of the others.
	 */
	public static java.util.Map<Profile, List<String>> sharedConfigurations(List<Profile> profiles) {
		java.util.Map<String, List<Profile>> byFile = new java.util.LinkedHashMap<>();
		for (Profile p: profiles) {
			File f = p.getConfigurationCanonicalFile();
			if (f != null)
				byFile.computeIfAbsent(f.getAbsolutePath().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(p);
		}
		java.util.Map<Profile, List<String>> result = new java.util.LinkedHashMap<>();
		for (List<Profile> same: byFile.values())
			if (same.size() > 1)
				for (Profile p: same)
					result.put(p, same.stream().filter(o -> o != p).map(Profile::getTitle).toList());
		return result;
	}

	/** the folders and images the configuration of the DOSBox version mounts (missing ones are to be fixed there, not in the profile) */
	public static Set<String> dosboxMountPaths(Profile prof) {
		Set<String> result = new LinkedHashSet<>();
		for (Mount m: prof.getDosboxNettoMountingPoints()) {
			if (m instanceof ImageMount im)
				for (File f: im.getCanonicalImgPaths())
					result.add(f.getPath());
			else if (m instanceof DirMount dm)
				result.add(dm.getCanonicalPath().getPath());
		}
		return result;
	}

	/** "[section] item" of the settings of the profile that its DOSBox version neither has in its configuration nor documents */
	public static List<String> unknownSettings(Profile prof) {
		return unknownSettingPairs(prof).stream().map(u -> "[" + u[0] + "] " + u[1]).toList();
	}

	/** the same as {section, item} */
	static List<String[]> unknownSettingPairs(Profile prof) {
		List<String[]> result = new ArrayList<>();
		DosboxVersion db = prof.getDosboxVersion();
		if (db == null)
			return result;
		Configuration dbConf = db.getConfiguration();
		if ((dbConf == null) || (dbConf.getSectionNames().length == 0))
			return result; // the configuration of the DOSBox version is not there: reported for the DOSBox version
		org.dbgl.model.conf.ConfValueOptions documented = db.getConfValueOptions();
		Configuration conf = prof.getConfiguration();
		for (String section: conf.getSectionNames()) {
			if (section.equalsIgnoreCase("autoexec"))
				continue;
			for (String item: conf.getItemNames(section))
				if (!dbConf.hasValue(section, item) && ((documented == null) || (documented.get(section, item) == null)))
					result.add(new String[] {section, item});
		}
		return result;
	}

	/**
	 * Checked once for each DOSBox version the profiles use: its program and its configuration file must be there.
	 *
	 * @return the problems, as for a profile
	 */
	public static List<Problem> checkDosboxVersion(DosboxVersion db) {
		List<Problem> result = new ArrayList<>();
		if ((db.getConfiguration().getSectionNames().length == 0) && FilesUtils.isReadableFile(db.getConfigurationCanonicalFile()))
			try {
				db.resetAndLoadConfiguration(); // not read yet (e.g. just listed)
			} catch (IOException e) {
				// told as for a profile then
			}
		File exe = db.getCanonicalExecutable();
		if ((exe == null) || !exe.isFile())
			result.add(new Problem("dosboxmissing2", db.getTitle(), exe == null ? "": exe.getPath()));
		File conf = db.getConfigurationCanonicalFile();
		if ((conf == null) || !conf.isFile())
			result.add(new Problem("dosboxconfmissing2", db.getTitle(), conf == null ? "": conf.getPath()));
		// "exit" in its [autoexec]: DOSBox runs it first (the sections are joined), and quits before the profile's lines; not so with
		// DOSBox Staging's "autoexec_section = overwrite", only the last [autoexec] counts then (the profile's)
		else if (db.isMultiConfig() && Boolean.TRUE.equals(db.getConfiguration().getAutoexec().isExit())
				&& !"overwrite".equalsIgnoreCase(StringUtils.trim(db.getConfiguration().getValue("dosbox", "autoexec_section"))))
			result.add(new Problem("dosboxconfexit", db.getTitle(), conf.getName()));
		return result;
	}

	/** the folder inside the profile folder that files without a profile are moved to */
	public static final String ORPHANS_DIR = "orphaned";
	private static final Pattern OWN_CONF_NAME = Pattern.compile("\\d+\\.conf", Pattern.CASE_INSENSITIVE);

	/**
	 * alpha 49: configuration files in the profile folder that no profile uses any more (e.g. left over when a profile was
	 * removed without its file, or from another database). Only files named as DBGL names them ("109.conf") and only those
	 * directly in the folder: nothing else there is DBGL's to judge.
	 *
	 * @param used the configuration files of ALL profiles of the database
	 * @return the files, by their number
	 */
	public static List<File> orphanConfs(File profilesDir, java.util.Collection<File> used) {
		List<File> result = new ArrayList<>();
		File[] files = profilesDir == null ? null: profilesDir.listFiles(f -> f.isFile() && OWN_CONF_NAME.matcher(f.getName()).matches());
		if (files == null)
			return result;
		Set<String> taken = new java.util.HashSet<>();
		for (File f: used)
			if (f != null)
				taken.add(canonicalOrAbsolute(f).getPath().toLowerCase(Locale.ROOT));
		for (File f: files)
			if (!taken.contains(canonicalOrAbsolute(f).getPath().toLowerCase(Locale.ROOT)))
				result.add(f);
		result.sort(java.util.Comparator.comparingLong((File f) -> Long.parseLong(StringUtils.substringBefore(f.getName(), "."))));
		return result;
	}

	private static File canonicalOrAbsolute(File f) {
		try {
			return f.getCanonicalFile();
		} catch (IOException e) {
			return f.getAbsoluteFile();
		}
	}

	/**
	 * alpha 49: moves files without a profile into the folder ORPHANS_DIR of the profile folder; nothing is deleted, a file
	 * of the same name already there keeps its place (the new one gets the time in its name).
	 *
	 * @return the files as they are now
	 */
	public static List<File> moveOrphans(List<File> files, File profilesDir) throws IOException {
		File dir = new File(profilesDir, ORPHANS_DIR);
		Files.createDirectories(dir.toPath());
		List<File> moved = new ArrayList<>();
		String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
		for (File f: files) {
			File target = new File(dir, f.getName());
			for (int n = 0; target.exists(); n++)
				target = new File(dir, FilenameUtils.getBaseName(f.getName()) + "." + stamp + (n == 0 ? "": "-" + n) + ".conf");
			Files.move(f.toPath(), target.toPath());
			moved.add(target);
		}
		return moved;
	}

	private static final Pattern BOOT_LINE = Pattern.compile("(?i)@?boot(\\s.*)?");

	/**
	 * alpha 49: a profile for DOSBox-X that boots another system (Windows 95 from a hard disk image, a DOS from a floppy
	 * image) while the mouse is not captured on its own: inside the booted system the mouse does not move until it is
	 * captured (Ctrl+F10), since DOSBox-X by default passes it on only then (mouse_emulation=locked) and its "autolock" is off
	 * by default. Not told when the mouse is passed on anyway (mouse_emulation always or integration).
	 */
	public static boolean bootWithoutMouseCapture(Profile prof) {
		DosboxVersion db = prof.getDosboxVersion();
		if ((db == null) || !org.dbgl.service.SettingsService.DOSBOX_X_DOSBOX_FAM.title_.equals(db.getFamily()))
			return false;
		Configuration conf = prof.getCombinedConfiguration();
		String lock = StringUtils.trimToEmpty(conf.getValue("sdl", "autolock")).toLowerCase(Locale.ROOT);
		String emulation = StringUtils.trimToEmpty(conf.getValue("sdl", "mouse_emulation")).toLowerCase(Locale.ROOT);
		if (lock.equals("true") || lock.equals("on") || emulation.equals("always") || emulation.equals("integration"))
			return false;
		return prof.getConfiguration().getAutoexec().toString().lines().anyMatch(l -> BOOT_LINE.matcher(l.trim()).matches());
	}
}
