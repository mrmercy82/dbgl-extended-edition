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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;
import org.dbgl.model.entity.SharedConf;


/**
 * Alpha 53: what the configuration of DBConf for a game (DBGL 0.100 beta 18) sets differently from a profile, told by "Check
 * profiles". Only for profiles of DOSBox 0.74 (and 0.73), for which all configurations of DBConf were made: for DOSBox Staging
 * and DOSBox-X their values would often be wrong. Only for exactly the title of the game. Compared are the settings that are
 * about the game (machine, memory, CPU, EMS/XMS/UMB, sound cards, joystick), from the full configuration of DBConf with its own
 * settings over it: about half of the configurations set nothing of their own, their full configuration then says that the
 * defaults do (Doom: memsize = 16). Settings about the own computer (window, output, keyboard layout, MIDI device) are left out.
 */
public final class DBConfAdvice {

	/** one setting: section, item, the value of DBConf, the value that applies in the profile */
	public record Difference(String section, String item, String value, String current) {
	}

	private DBConfAdvice() {
	}

	/**
	 * true for a profile of DOSBox 0.74 or 0.73 (by its family and version: the generation of DOSBox 0.73 is also what DBGL
	 * assumes for a version it does not know)
	 */
	public static boolean forVersion(DosboxVersion db) {
		return (db != null) && org.dbgl.service.SettingsService.OFFICIAL_DOSBOX_FAM.title_.equalsIgnoreCase(db.getFamily())
				&& (GenerationAwareConfigurable.determineGen(db) == Generation.official_073);
	}

	/** the settings that are about the game, as "section/item" */
	public static final java.util.Set<String> ABOUT_THE_GAME = java.util.Set.of("dosbox/machine", "dosbox/memsize", "cpu/core", "cpu/cputype", "cpu/cycles", "dos/xms",
		"dos/ems", "dos/umb", "sblaster/sbtype", "sblaster/sbbase", "sblaster/irq", "sblaster/dma", "sblaster/hdma", "sblaster/oplmode", "gus/gus", "gus/gusbase",
		"gus/gusirq", "gus/gusdma", "speaker/pcspeaker", "speaker/tandy", "speaker/disney", "joystick/joysticktype", "midi/mpu401");

	/**
	 * The configuration of DBConf for exactly this title; null if there is none, or several that do not agree on the settings
	 * about the game.
	 */
	public static SharedConf configurationFor(List<SharedConf> confs, String title) {
		List<SharedConf> found = org.dbgl.service.DBConfDatabaseService.exact(confs, title).stream().filter(c -> !aboutTheGame(c).isEmpty()).toList();
		if (found.isEmpty())
			return null;
		java.util.Map<String, String> first = aboutTheGame(found.get(0));
		for (SharedConf c: found)
			if (!first.equals(aboutTheGame(c)))
				return null;
		return found.get(0);
	}

	/** the settings about the game: from the full configuration, the own settings over it ("section/item" to value, in order) */
	public static java.util.Map<String, String> aboutTheGame(SharedConf c) {
		java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
		for (String text: new String[] {c.getFullConf(), c.getIncrConf()})
			for (String[] s: settings(text))
				if (ABOUT_THE_GAME.contains(s[0] + "/" + s[1]))
					result.put(s[0] + "/" + s[1], s[2]);
		return result;
	}

	/** the settings of a configuration, without [autoexec]: section, item, value (section and item in lower case) */
	public static List<String[]> settings(String text) {
		List<String[]> result = new ArrayList<>();
		String section = null;
		for (String raw: StringUtils.defaultString(text).split("\\R")) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#"))
				continue;
			if (line.startsWith("[") && line.endsWith("]")) {
				section = line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
				continue;
			}
			int eq = line.indexOf('=');
			if ((section == null) || section.equals("autoexec") || (eq <= 0))
				continue;
			result.add(new String[] {section, line.substring(0, eq).strip().toLowerCase(Locale.ROOT), line.substring(eq + 1).strip()});
		}
		return result;
	}

	/** the settings of DBConf that the profile has otherwise (a setting this DOSBox version does not know is left out) */
	public static List<Difference> differences(SharedConf dbconf, Configuration applies) {
		List<Difference> result = new ArrayList<>();
		for (java.util.Map.Entry<String, String> e: aboutTheGame(dbconf).entrySet()) {
			String[] s = {e.getKey().substring(0, e.getKey().indexOf('/')), e.getKey().substring(e.getKey().indexOf('/') + 1), e.getValue()};
			if (!applies.hasValue(s[0], s[1]))
				continue;
			String current = StringUtils.trimToEmpty(applies.getValue(s[0], s[1]));
			if (!current.equalsIgnoreCase(s[2]))
				result.add(new Difference(s[0], s[1], s[2], current));
		}
		return result;
	}

	/**
	 * What loading a configuration of DBConf puts into a profile. DOSBox 0.74: its own settings and from its full configuration
	 * the settings about the game (so that Doom with memsize = 8 gets 16 again, as the full configuration says); anything else
	 * (DOSBox Staging, DOSBox-X): its own settings only, as the other values of 0.74 often do not fit there, with the values
	 * DOSBox Staging calls otherwise taken over under their new name ("pentium_slow" -> "pentium").
	 */
	public static String toLoad(SharedConf c, DosboxVersion db) {
		String own = StringUtils.defaultString(c.getIncrConf());
		if (forVersion(db)) {
			java.util.Set<String> set = new java.util.HashSet<>();
			for (String[] s: settings(own))
				set.add(s[0] + "/" + s[1]);
			List<String[]> extra = new ArrayList<>();
			for (java.util.Map.Entry<String, String> e: aboutTheGame(c).entrySet())
				if (!set.contains(e.getKey()))
					extra.add(new String[] {e.getKey().substring(0, e.getKey().indexOf('/')), e.getKey().substring(e.getKey().indexOf('/') + 1), e.getValue()});
			return withSettings(own, extra);
		}
		if ((db != null) && org.dbgl.service.SettingsService.STAGING_DOSBOX_FAM.title_.equalsIgnoreCase(db.getFamily()))
			return forStaging(own);
		return own;
	}

	/** the text with these settings added, each in its section (a new section before [autoexec]) */
	static String withSettings(String text, List<String[]> extra) {
		List<String> lines = new ArrayList<>(java.util.Arrays.asList(StringUtils.defaultString(text).split("\\R", -1)));
		while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank())
			lines.remove(lines.size() - 1);
		for (String[] s: extra) {
			int header = -1, end = -1, autoexec = -1;
			for (int i = 0; i < lines.size(); i++) {
				String l = lines.get(i).strip();
				if (l.startsWith("[") && l.endsWith("]")) {
					String sec = l.substring(1, l.length() - 1).strip();
					if ((header >= 0) && (end < 0))
						end = i;
					if (sec.equalsIgnoreCase(s[0]))
						header = i;
					if (sec.equalsIgnoreCase("autoexec") && (autoexec < 0))
						autoexec = i;
				}
			}
			if (header >= 0) {
				int at = end < 0 ? lines.size(): end;
				while ((at > header + 1) && lines.get(at - 1).isBlank())
					at--;
				lines.add(at, s[1] + "=" + s[2]);
			} else {
				int at = autoexec < 0 ? lines.size(): autoexec;
				lines.addAll(at, List.of("[" + s[0] + "]", s[1] + "=" + s[2], ""));
			}
		}
		while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank())
			lines.remove(lines.size() - 1);
		return lines.isEmpty() ? "": String.join("\n", lines) + "\n";
	}

	/** own settings of DOSBox 0.74 with the values DOSBox Staging calls otherwise under their new name */
	static String forStaging(String text) {
		if (StringUtils.isBlank(text))
			return StringUtils.defaultString(text);
		StringBuilder sb = new StringBuilder();
		String section = null;
		for (String line: StringUtils.defaultString(text).split("\\R", -1)) {
			String l = line.strip();
			if (l.startsWith("[") && l.endsWith("]"))
				section = l.substring(1, l.length() - 1).strip();
			int eq = line.indexOf('=');
			String renamed = (section == null) || (eq <= 0) || l.startsWith("#") ? null: ConfTakeover.stagingValueFor(section, line.substring(0, eq).strip(), line.substring(eq + 1));
			sb.append(renamed == null ? line: line.substring(0, eq + 1) + renamed).append('\n');
		}
		return sb.toString().replaceAll("\\n+$", "\n");
	}

	/** "[cpu] cycles = 750, [dosbox] machine = cga" */
	public static String shown(List<Difference> diffs, boolean dbconf) {
		return String.join(", ", diffs.stream().map(d -> "[" + d.section() + "] " + d.item() + " = " + (dbconf ? d.value(): d.current())).toList());
	}
}
