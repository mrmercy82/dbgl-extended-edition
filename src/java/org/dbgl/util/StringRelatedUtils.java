/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.text.similarity.LevenshteinDistance;


public class StringRelatedUtils {

	private StringRelatedUtils() {
	}

	public static String[] textAreaToStringArray(String contents, String del) {
		return StringUtils.splitByWholeSeparator(StringUtils.strip(contents, del), del);
	}

	public static String stringArrayToString(String[] values, String delimiter) {
		return ArrayUtils.isEmpty(values) ? StringUtils.EMPTY: StringUtils.join(values, delimiter) + delimiter;
	}

	public static int[] stringToIntArray(String input) {
		try {
			return Stream.of(input.split(" ")).filter(StringUtils::isNumeric).mapToInt(Integer::parseInt).toArray();
		} catch (NumberFormatException e) {
			return new int[0];
		}
	}

	public static boolean[] stringToBooleanArray(String input) {
		return ArrayUtils.toPrimitive(Stream.of(input.split(" ")).map(Boolean::parseBoolean).toArray(Boolean[]::new));
	}

	public static Map<String, String> stringArrayToMap(String[] list) {
		Map<String, String> result = new HashMap<>();
		for (String entry: list) {
			String[] pair = entry.split("=");
			if (pair.length == 2) {
				String key = pair[0].trim();
				String value = pair[1].trim();
				if (key.length() > 0 && value.length() > 0)
					result.put(key, value);
			}
		}
		return result;
	}

	public static int findBestMatchIndex(String search, String[] titles) {
		if (ArrayUtils.isEmpty(titles))
			return -1;
		String s = search.toLowerCase();
		int minDistance = Integer.MAX_VALUE;
		int result = 0;
		for (int i = 0; i < titles.length; i++) {
			String title = titles[i].toLowerCase();
			int distance = (i == 0) ? LevenshteinDistance.getDefaultInstance().apply(s, title): new LevenshteinDistance(minDistance - 1).apply(s, title);
			if (distance == 0)
				return i;
			if (distance != -1) {
				minDistance = distance;
				result = i;
			}
		}
		return result;
	}

	public static String toSwtGuiString(String s) {
		return Strings.CS.replace(s, "&", "&&");
	}

	public static String toString(Exception exception) {
		return exception.getMessage() != null ? exception.getMessage(): exception.toString();
	}
}
