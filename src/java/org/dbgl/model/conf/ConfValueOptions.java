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
package org.dbgl.model.conf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;


/**
 * The allowed values of the settings, as documented by DOSBox itself in the comments of the configuration file it writes
 * ('config -writeconf'). This makes the option lists in the profile dialog match the DOSBox installation exactly, also for
 * DOSBox versions DBGL doesn't know in detail.
 *
 * Two formats are understood:
 * <ul>
 * <li>DOSBox (official), SVN, ECE, DOSBox-X and DOSBox Staging up to 0.82: a comment block at the start of each section,
 * "# name: help text" followed by "# Possible values: a, b, c."</li>
 * <li>DOSBox Staging 0.83+: a comment block right above each setting, with the help text containing "Possible values:"
 * followed by one "#   value:  description" line per value.</li>
 * </ul>
 */
public class ConfValueOptions {

	public static class Options {
		private final List<String> values_;
		private final boolean freeForm_;

		Options(List<String> values, boolean freeForm) {
			values_ = values;
			freeForm_ = freeForm;
		}

		public List<String> getValues() {
			return values_;
		}

		/** true if the documentation also mentions placeholders such as &lt;number&gt; or WxH, so other values are allowed */
		public boolean isFreeForm() {
			return freeForm_;
		}
	}

	private static final Pattern SECTION_PATRN = Pattern.compile("^\\s*\\[([^\\]]+)\\]");
	private static final Pattern SETTING_PATRN = Pattern.compile("^\\s*([^#=\\s][^=]*?)\\s*=");
	private static final Pattern OLD_NAME_PATRN = Pattern.compile("^#\\s*([A-Za-z0-9_][A-Za-z0-9_ .\\-]*?):\\s");
	private static final Pattern OLD_VALUES_PATRN = Pattern.compile("^#\\s*Possible values:\\s*(.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern NEW_OPTION_PATRN = Pattern.compile("^#\\s{3,6}(\\S[^:]*?):(\\s|$)"); // values may be right aligned, e.g. "#    4:"
	private static final Pattern TOKEN_PATRN = Pattern.compile("^[A-Za-z0-9_+\\-.]+$");

	private final Map<String, Map<String, Options>> options_ = new HashMap<>();
	private final Map<String, Map<String, String>> help_ = new HashMap<>();

	private static final int MAX_HELP_LENGTH = 1500;

	private ConfValueOptions() {
	}

	public static ConfValueOptions parse(String confData) {
		ConfValueOptions result = new ConfValueOptions();
		if (confData != null)
			result.doParse(confData.split("\\R"));
		return result;
	}

	/** @return the documented values, or null if DOSBox doesn't document them for this setting */
	public Options get(String section, String item) {
		if ((section == null) || (item == null))
			return null;
		Map<String, Options> s = options_.get(section.toLowerCase());
		return s == null ? null: s.get(item.toLowerCase());
	}

	public boolean isEmpty() {
		return options_.isEmpty();
	}

	/** @return the description DOSBox gives for the setting in its configuration file, or null */
	public String getHelp(String section, String item) {
		if ((section == null) || (item == null))
			return null;
		Map<String, String> s = help_.get(section.toLowerCase());
		return s == null ? null: s.get(item.toLowerCase());
	}

	private void doParse(String[] lines) {
		String section = null;
		List<String> block = new ArrayList<>(); // comment lines since the last setting
		Map<String, String> oldStyleValues = null; // name -> raw 'Possible values' text, within current section
		Map<String, String> oldStyleHelp = new HashMap<>(); // name -> help text, within current section
		boolean firstSetting = true;

		for (String line: lines) {
			String trimmed = line.trim();
			Matcher sm = SECTION_PATRN.matcher(trimmed);
			if (sm.find()) {
				section = sm.group(1).trim().toLowerCase();
				block.clear();
				oldStyleValues = null;
				oldStyleHelp = new HashMap<>();
				firstSetting = true;
				continue;
			}
			if (section == null)
				continue;
			if (trimmed.startsWith("#")) {
				block.add(trimmed);
				continue;
			}
			Matcher setm = SETTING_PATRN.matcher(trimmed);
			if (setm.find()) {
				String item = setm.group(1).trim().toLowerCase();
				if (oldStyleValues == null)
					oldStyleValues = parseOldStyleHeader(block, oldStyleHelp); // the old format documents all settings of a section in front of the first one
				Options o = parseNewStyle(block);
				if ((o == null) && oldStyleValues.containsKey(item))
					o = parseOldStyle(oldStyleValues.get(item));
				if (o != null)
					options_.computeIfAbsent(section, k -> new HashMap<>()).put(item, o);
				String help = (firstSetting && oldStyleHelp.containsKey(item)) || block.isEmpty() ? oldStyleHelp.get(item): blockText(block);
				if (StringUtils.isNotBlank(help))
					help_.computeIfAbsent(section, k -> new HashMap<>()).put(item, StringUtils.abbreviate(help.strip(), MAX_HELP_LENGTH));
				firstSetting = false;
				block.clear();
			}
		}
	}

	/**
	 * Old format: "# name: help" lines with the names right aligned, so the colons of all setting names of a section are in the
	 * same column. Lines of the help text that happen to look alike (e.g. "#      opengl:  OpenGL backend") have their colon
	 * elsewhere and are ignored. "Possible values:" lines belong to the setting name above them.
	 */
	private static String stripComment(String line) {
		String s = line.startsWith("#") ? line.substring(1): line;
		return s.startsWith(" ") ? s.substring(1): s;
	}

	/** the comment block above a setting (new format) as plain text */
	private static String blockText(List<String> block) {
		StringBuilder sb = new StringBuilder();
		for (String l: block)
			sb.append(stripComment(l).stripTrailing()).append('\n');
		return sb.toString().strip();
	}

	private static Map<String, String> parseOldStyleHeader(List<String> block, Map<String, String> help) {
		Map<Integer, Integer> colonColumns = new HashMap<>();
		for (String l: block) {
			Matcher nm = OLD_NAME_PATRN.matcher(l);
			if (nm.find() && !OLD_VALUES_PATRN.matcher(l).matches())
				colonColumns.merge(nm.end(1), 1, Integer::sum);
		}
		Map<String, String> result = new HashMap<>();
		if (colonColumns.isEmpty())
			return result;
		int nameColumn = colonColumns.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
		String current = null;
		for (String l: block) {
			Matcher vm = OLD_VALUES_PATRN.matcher(l);
			Matcher nm = OLD_NAME_PATRN.matcher(l);
			if (!vm.matches() && nm.find() && (nm.end(1) == nameColumn)) {
				current = nm.group(1).trim().toLowerCase();
				help.put(current, l.substring(Math.min(l.length(), nm.end(1) + 1)).strip());
				continue;
			}
			if (current == null)
				continue;
			String text = l.substring(1).strip();
			if (!text.isEmpty())
				help.merge(current, text, (a, b) -> a + '\n' + b);
			if (vm.matches())
				result.merge(current, vm.group(1), (a, b) -> a + ", " + b);
		}
		return result;
	}

	/** values are lower case words; mixed case names like WxH or X are placeholders */
	private static boolean isValueToken(String s) {
		return TOKEN_PATRN.matcher(s).matches() && s.equals(s.toLowerCase());
	}

	private static Options parseOldStyle(String raw) {
		String s = StringUtils.removeEnd(raw.trim(), ".");
		Set<String> values = new LinkedHashSet<>();
		boolean freeForm = false;
		for (String v: StringUtils.split(s, ',')) {
			String t = v.trim();
			if (t.isEmpty())
				continue;
			if (isValueToken(t))
				values.add(t);
			else
				freeForm = true;
		}
		return values.isEmpty() ? null: new Options(new ArrayList<>(values), freeForm);
	}

	private static Options parseNewStyle(List<String> block) {
		// find "Possible values:", which can be wrapped over two lines ("Possible\n# values:")
		int start = -1;
		for (int i = 0; i < block.size() && start == -1; i++) {
			String l = block.get(i).toLowerCase();
			if (l.endsWith("possible values:")) // in the old format, the values follow on the same line
				start = i + 1;
			else if (l.endsWith("possible") && (i + 1 < block.size()) && block.get(i + 1).toLowerCase().startsWith("# values:"))
				start = i + 2;
		}
		if (start == -1)
			return null;
		Set<String> values = new LinkedHashSet<>();
		boolean freeForm = false;
		for (int i = start; i < block.size(); i++) {
			String l = block.get(i);
			if (l.startsWith("# Note") || l.startsWith("# Notes"))
				break;
			Matcher m = NEW_OPTION_PATRN.matcher(l);
			if (!m.find())
				continue;
			for (String name: StringUtils.split(m.group(1).trim(), '/')) { // "none/off" documents two values
				if (isValueToken(name))
					values.add(name);
				else
					freeForm = true; // e.g. "<number>", "WxH", "small, medium, large (s, m, l)", "X,Y"
			}
		}
		return values.isEmpty() ? null: new Options(new ArrayList<>(values), freeForm);
	}
}
