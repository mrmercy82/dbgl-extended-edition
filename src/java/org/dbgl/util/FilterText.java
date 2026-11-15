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
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.service.ITextService;


/**
 * Alpha 50: the condition of a filter in words. A filter is a condition for the database ("UPPER(GAM.TITLE) LIKE
 * '%DOOM%'"), and the window "Edit filter" shows it as it is stored. Here the conditions that window writes itself are
 * told in the language of the user ("Title contains "DOOM""). A condition written by hand in another way is not guessed
 * at: the answer is null then.
 */
public final class FilterText {

	private static final String FIELD = "([A-Z]+\\.[A-Z_0-9]+)";
	private static final String QUOTED = "'((?:[^']|'')*)'";
	private static final char OPEN = '\u0001', CLOSE = '\u0002';

	private FilterText() {
	}

	/**
	 * @param condition the condition of the filter
	 * @param fieldName the name of a field of the database for the user ("GAM.TITLE" -> "Title"), null if not known
	 * @param dosboxTitle the title of a DOSBox version by its id, null if not known
	 * @return the condition in words, or null when it is empty or not one this class knows all parts of
	 */
	public static String readable(String condition, Function<String, String> fieldName, IntFunction<String> dosboxTitle, ITextService text) {
		if (StringUtils.isBlank(condition))
			return null;
		List<String> parts = new ArrayList<>();
		Function<String, String> name = f -> StringUtils.defaultIfBlank(fieldName.apply(f), f);
		String s = condition;
		// a day: three conditions on one field
		s = replace(s, "YEAR\\(" + FIELD + "\\)=(\\d+) AND MONTH\\(\\1\\)=(\\d+) AND DAYOFMONTH\\(\\1\\)=(\\d+)", parts,
			m -> text.get("dialog.filter.read.on", new Object[] {name.apply(m.group(1)), String.format("%04d-%02d-%02d", Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)))}));
		s = replace(s, "\\(" + FIELD + " IS NULL OR \\1=''\\)", parts, m -> text.get("dialog.filter.read.empty", new Object[] {name.apply(m.group(1))}));
		s = replace(s, "GAM\\.ID IN \\(SELECT SER\\.GAME_ID FROM SERIES SER WHERE SER\\.NAME=" + QUOTED + "\\)", parts,
			m -> text.get("dialog.filter.read.is", new Object[] {name.apply("SER.NAME"), m.group(1).replace("''", "'")}));
		s = replace(s, "GAM\\.ID IN \\(\\s*(\\d+(?:\\s*,\\s*\\d+)*)\\s*\\)", parts, m -> text.get("dialog.filter.read.selected", new Object[] {String.valueOf(m.group(1).split(",").length)}));
		s = replace(s, "UPPER\\(" + FIELD + "\\) LIKE '%((?:[^']|'')*)%'", parts, m -> text.get("dialog.filter.read.contains", new Object[] {name.apply(m.group(1)), m.group(2).replace("''", "'")}));
		s = replace(s, FIELD + "\\s*=\\s*" + QUOTED, parts, m -> m.group(2).isEmpty() ? text.get("dialog.filter.read.empty", new Object[] {name.apply(m.group(1))})
				: text.get("dialog.filter.read.is", new Object[] {name.apply(m.group(1)), m.group(2).replace("''", "'")}));
		s = replace(s, FIELD + "\\s*<>\\s*''", parts, m -> text.get("dialog.filter.read.notempty", new Object[] {name.apply(m.group(1))}));
		s = replace(s, FIELD + " IS NULL", parts, m -> text.get("dialog.filter.read.empty", new Object[] {name.apply(m.group(1))}));
		s = replace(s, FIELD + "\\s*=\\s*(?i:(true|false))", parts, m -> text.get("true".equalsIgnoreCase(m.group(2)) ? "dialog.filter.read.yes": "dialog.filter.read.no", new Object[] {name.apply(m.group(1))}));
		s = replace(s, FIELD + "\\s*=\\s*(-?\\d+)", parts, m -> {
			String value = m.group(2);
			if ("GAM.DBVERSION_ID".equals(m.group(1)))
				value = StringUtils.defaultIfBlank(dosboxTitle.apply(Integer.parseInt(value)), value);
			return text.get("dialog.filter.read.is", new Object[] {name.apply(m.group(1)), value});
		});
		// what is left may only join the parts
		if (!s.replaceAll(OPEN + "\\d+" + CLOSE, " ").replaceAll("(?i)\\b(AND|OR)\\b", " ").replaceAll("[()\\s]", "").isEmpty() || parts.isEmpty())
			return null;
		s = s.replaceAll("\\s+", " ").replaceAll("(?i)\\bAND\\b", Matcher.quoteReplacement(text.get("dialog.filter.read.and"))).replaceAll("(?i)\\bOR\\b", Matcher.quoteReplacement(text.get("dialog.filter.read.or")));
		s = s.replace("( ", "(").replace(" )", ")").strip(); // not trim(): that would take the marks away
		StringBuilder result = new StringBuilder();
		Matcher m = Pattern.compile(OPEN + "(\\d+)" + CLOSE).matcher(s);
		while (m.find())
			m.appendReplacement(result, Matcher.quoteReplacement(parts.get(Integer.parseInt(m.group(1)))));
		m.appendTail(result);
		return result.toString();
	}

	/** each match put aside as a part told in words, with a mark in its place (so no later pattern reads into it) */
	private static String replace(String s, String pattern, List<String> parts, Function<Matcher, String> words) {
		Matcher m = Pattern.compile(pattern).matcher(s);
		StringBuilder result = new StringBuilder();
		while (m.find()) {
			parts.add(words.apply(m));
			m.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(OPEN) + (parts.size() - 1) + CLOSE));
		}
		m.appendTail(result);
		return result.toString();
	}
}
