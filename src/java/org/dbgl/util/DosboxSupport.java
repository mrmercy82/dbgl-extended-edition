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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.dbgl.model.DosboxFamily;
import org.dbgl.service.SettingsService;


/**
 * alpha 50: a DOSBox version that is newer than the ones this DBGL knows. DBGL treats it as the newest one it knows; this
 * says so, and writes a report for the developer: what is new, gone or different in its configuration file against the list
 * of settings DBGL has for the newest version it knows.
 */
public final class DosboxSupport {

	private DosboxSupport() {
	}

	public enum Status {
		/** a version of the list */
		KNOWN,
		/** newer than every version of its family in the list */
		NEWER,
		/** not in the list, but not newer (an older or a special build), or a family DBGL does not know */
		OTHER
	}

	/** the parts of a version that are numbers ("0.83.1", "2025.02.01", "0.74-3", "r4494") */
	static List<Integer> numbers(String version) {
		List<Integer> result = new ArrayList<>();
		for (String part: String.valueOf(version).split("[^0-9]+"))
			if (!part.isEmpty() && (part.length() < 10))
				result.add(Integer.parseInt(part));
		return result;
	}

	/** compares two versions by their numbers, from the left; missing parts count as 0 */
	public static int compare(String a, String b) {
		List<Integer> x = numbers(a), y = numbers(b);
		for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
			int c = Integer.compare(i < x.size() ? x.get(i): 0, i < y.size() ? y.get(i): 0);
			if (c != 0)
				return c;
		}
		return 0;
	}

	private static DosboxFamily family(String family) {
		for (DosboxFamily f: SettingsService.SUPPORTED_DOSBOX_FAMILIES)
			if (f.title_.equalsIgnoreCase(String.valueOf(family).trim()))
				return f;
		return null;
	}

	/** the newest version of a family DBGL knows, null for a family it does not know */
	public static String newestKnown(String family) {
		DosboxFamily f = family(family);
		if ((f == null) || f.versions_.isEmpty())
			return null;
		String newest = f.versions_.get(0);
		for (String v: f.versions_)
			if (compare(v, newest) > 0)
				newest = v;
		return newest;
	}

	public static Status status(String family, String version) {
		DosboxFamily f = family(family);
		String v = String.valueOf(version).trim();
		if ((f == null) || v.isEmpty() || numbers(v).isEmpty())
			return Status.OTHER;
		if (f.versions_.contains(v))
			return Status.KNOWN;
		return compare(v, newestKnown(family)) > 0 ? Status.NEWER: Status.OTHER;
	}

	/** the list of settings DBGL has for the newest version of a family (a file in /confdefaults), null if it has none */
	public static String listOf(String family) {
		String f = String.valueOf(family).trim();
		if (f.equalsIgnoreCase(SettingsService.STAGING_DOSBOX_FAM.title_))
			return "staging-" + newestKnown(family);
		if (f.equalsIgnoreCase(SettingsService.DOSBOX_X_DOSBOX_FAM.title_))
			return "dosbox-x";
		if (f.equalsIgnoreCase(SettingsService.OFFICIAL_DOSBOX_FAM.title_))
			return "dosbox-" + newestKnown(family);
		if (f.equalsIgnoreCase(SettingsService.SVN_DOSBOX_FAM.title_))
			return "dosbox-svn";
		if (f.equalsIgnoreCase(SettingsService.ECE_DOSBOX_FAM.title_))
			return "dosbox-ece";
		if (f.equalsIgnoreCase(SettingsService.DAUM_DOSBOX_FAM.title_))
			return "dosbox-daum";
		return null;
	}

	/** "section|setting" (small letters) with its value, in the order of the file; without [autoexec] */
	public static Map<String, String> settingsOf(String confText) {
		Map<String, String> result = new LinkedHashMap<>();
		String section = null;
		for (String raw: confText.split("\\R")) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith("%") || line.startsWith(";"))
				continue;
			if (line.startsWith("[") && line.contains("]")) {
				section = line.substring(1, line.indexOf(']')).trim().toLowerCase(Locale.ROOT);
				continue;
			}
			int eq = line.indexOf('=');
			if ((section == null) || section.equals("autoexec") || (eq <= 0))
				continue;
			result.put(section + "|" + line.substring(0, eq).trim().toLowerCase(Locale.ROOT), line.substring(eq + 1).trim());
		}
		return result;
	}

	/**
	 * The report, in English (it is for the developer): the version, what DBGL treats it as, and its configuration file against
	 * the list: settings that are new, settings that are gone, settings with another value than the default DBGL has.
	 *
	 * @param known "section|setting" with the default DBGL has (alternatives split by " || ")
	 * @param conf  the settings of the configuration file of the new version
	 */
	public static String report(String dbgl, String family, String version, String treatedAs, String listName, Map<String, String> known, Map<String, String> conf) {
		List<String> out = new ArrayList<>();
		out.add("DBGL Extended Edition " + dbgl + ": report on a DOSBox version it does not know yet");
		out.add("");
		out.add("DOSBox version:   " + family + " " + version);
		out.add("Treated as:       " + (treatedAs == null ? "(no version of this family is known)": family + " " + treatedAs));
		out.add("Compared with:    " + (listName == null ? "(DBGL has no list of settings for this family)": "confdefaults/" + listName + ".txt, " + known.size() + " settings"));
		out.add("Its file has:     " + conf.size() + " settings");
		out.add("");
		if ((listName == null) || known.isEmpty() || conf.isEmpty()) {
			out.add(conf.isEmpty() ? "The configuration file of this version could not be read, or it is empty: nothing to compare."
					: "All settings of its configuration file:");
			conf.forEach((k, v) -> out.add("  " + show(k) + " = " + v));
			return String.join("\n", out) + "\n";
		}
		Set<String> knownSections = new LinkedHashSet<>(), confSections = new LinkedHashSet<>();
		known.keySet().forEach(k -> knownSections.add(k.substring(0, k.indexOf('|'))));
		conf.keySet().forEach(k -> confSections.add(k.substring(0, k.indexOf('|'))));
		List<String> added = new ArrayList<>(), gone = new ArrayList<>(), other = new ArrayList<>();
		for (Map.Entry<String, String> e: conf.entrySet()) {
			if (!known.containsKey(e.getKey())) {
				added.add("  " + show(e.getKey()) + " = " + e.getValue());
			} else {
				List<String> defaults = java.util.Arrays.stream(known.get(e.getKey()).split(" \\|\\| ", -1)).map(String::trim).toList();
				if (defaults.stream().noneMatch(d -> d.equalsIgnoreCase(e.getValue())))
					other.add("  " + show(e.getKey()) + " = " + e.getValue() + "    (default known to DBGL: " + String.join(" or ", defaults) + ")");
			}
		}
		for (String k: known.keySet())
			if (!conf.containsKey(k))
				gone.add("  " + show(k));
		List<String> newSections = confSections.stream().filter(s -> !knownSections.contains(s)).toList(), goneSections = knownSections.stream().filter(s -> !confSections.contains(s)).toList();
		out.add("New sections: " + (newSections.isEmpty() ? "none": String.join(", ", newSections)));
		out.add("Sections that are gone: " + (goneSections.isEmpty() ? "none": String.join(", ", goneSections)));
		out.add("");
		out.add("New settings (" + added.size() + "):");
		out.addAll(added);
		out.add("");
		out.add("Settings that are gone (" + gone.size() + "):");
		out.addAll(gone);
		out.add("");
		out.add("Settings with another value than the default DBGL knows (" + other.size() + "; a new default, or changed by the user):");
		out.addAll(other);
		out.add("");
		out.add("Please send this file to the developer of DBGL Extended Edition (the Vogons thread or GitHub). It holds no paths and no names of this computer.");
		return String.join("\n", out) + "\n";
	}

	private static String show(String key) {
		int bar = key.indexOf('|');
		return "[" + key.substring(0, bar) + "] " + key.substring(bar + 1);
	}

	/** the report for a DOSBox version and its configuration file (null or unreadable: said in the report) */
	public static String report(String dbgl, String family, String version, File confFile) {
		String list = listOf(family);
		Map<String, String> known = new LinkedHashMap<>();
		// the lists have their keys as "section\nsetting"
		ConfTakeover.shipped(list).forEach((k, v) -> known.put(k.replace('\n', '|'), v));
		Map<String, String> conf = new LinkedHashMap<>();
		try {
			if ((confFile != null) && confFile.isFile())
				conf = settingsOf(new String(Files.readAllBytes(confFile.toPath()), StandardCharsets.UTF_8));
		} catch (java.io.IOException | RuntimeException e) {
			// said in the report
		}
		return report(dbgl, family, version, newestKnown(family), known.isEmpty() ? null: list, known, conf);
	}
}
