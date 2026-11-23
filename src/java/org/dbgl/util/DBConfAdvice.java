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
 * and DOSBox-X their values would often be wrong. Only for exactly the title of the game, and only for configurations that set
 * something of their own (about half of them only have the defaults of DOSBox).
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

	/**
	 * The configuration of DBConf for exactly this title, if there is one that sets something of its own; null if there is none,
	 * or several that do not agree.
	 */
	public static SharedConf configurationFor(List<SharedConf> confs, String title) {
		List<SharedConf> own = org.dbgl.service.DBConfDatabaseService.exact(confs, title).stream().filter(c -> !settings(c.getIncrConf()).isEmpty()).toList();
		if (own.isEmpty())
			return null;
		List<String[]> first = settings(own.get(0).getIncrConf());
		for (SharedConf c: own)
			if (!same(first, settings(c.getIncrConf())))
				return null;
		return own.get(0);
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

	private static boolean same(List<String[]> a, List<String[]> b) {
		if (a.size() != b.size())
			return false;
		for (int i = 0; i < a.size(); i++)
			if (!java.util.Arrays.equals(a.get(i), b.get(i)))
				return false;
		return true;
	}

	/** the settings of DBConf that the profile has otherwise (a setting this DOSBox version does not know is left out) */
	public static List<Difference> differences(SharedConf dbconf, Configuration applies) {
		List<Difference> result = new ArrayList<>();
		for (String[] s: settings(dbconf.getIncrConf())) {
			if (!applies.hasValue(s[0], s[1]))
				continue;
			String current = StringUtils.trimToEmpty(applies.getValue(s[0], s[1]));
			if (!current.equalsIgnoreCase(s[2]))
				result.add(new Difference(s[0], s[1], s[2], current));
		}
		return result;
	}

	/** "[cpu] cycles = 750, [dosbox] machine = cga" */
	public static String shown(List<Difference> diffs, boolean dbconf) {
		return String.join(", ", diffs.stream().map(d -> "[" + d.section() + "] " + d.item() + " = " + (dbconf ? d.value(): d.current())).toList());
	}
}
