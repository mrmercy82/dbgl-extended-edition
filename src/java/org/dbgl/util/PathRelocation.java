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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Moving the paths of profiles whose folders or files were moved, e.g. to another drive or into a renamed folder. From one old
 * and one new location, the part of the path that changed is found; it is then changed in all other paths as well.
 */
public final class PathRelocation {

	private PathRelocation() {
	}

	private static boolean ignoreCase() {
		return File.separatorChar == '\\';
	}

	private static boolean isSeparator(char c) {
		return (c == '\\') || (c == '/');
	}

	private static String strip(String path) {
		String p = path.strip();
		while ((p.length() > 1) && isSeparator(p.charAt(p.length() - 1)) && !p.endsWith(":\\") && !p.endsWith(":/"))
			p = p.substring(0, p.length() - 1);
		return p;
	}

	private static int lastSeparator(String path) {
		for (int i = path.length() - 1; i >= 0; i--)
			if (isSeparator(path.charAt(i)))
				return i;
		return -1;
	}

	private static boolean same(String a, String b) {
		return ignoreCase() ? a.equalsIgnoreCase(b): a.equals(b);
	}

	/**
	 * @return {old prefix, new prefix}: both paths without the names they have in common at their end. For example
	 *         D:\Games\Doom and E:\Old\Games\Doom give D: and E:\Old
	 */
	public static String[] changedPrefixes(String oldPath, String newPath) {
		String o = strip(oldPath), n = strip(newPath);
		while (true) {
			int oi = lastSeparator(o), ni = lastSeparator(n);
			if ((oi <= 0) || (ni <= 0))
				break;
			if (!same(o.substring(oi + 1), n.substring(ni + 1)))
				break;
			o = o.substring(0, oi);
			n = n.substring(0, ni);
		}
		return new String[] {o, n};
	}

	/** @return the path with the old prefix replaced by the new one, or null if it does not start with the old prefix */
	public static String relocate(String path, String oldPrefix, String newPrefix) {
		String p = strip(path);
		if (same(p, oldPrefix))
			return newPrefix;
		if ((p.length() > oldPrefix.length()) && same(p.substring(0, oldPrefix.length()), oldPrefix)
				&& (isSeparator(p.charAt(oldPrefix.length())) || isSeparator(oldPrefix.charAt(oldPrefix.length() - 1))))
			return newPrefix + p.substring(oldPrefix.length());
		return null;
	}

	/** @return the path relative to the folder, or null if it is not inside */
	private static String relativeTo(String path, File dir) {
		String d = strip(dir.getPath());
		if ((path.length() > d.length() + 1) && same(path.substring(0, d.length()), d) && isSeparator(path.charAt(d.length())))
			return path.substring(d.length() + 1);
		return null;
	}

	/**
	 * Replace paths in the text of a profile configuration. Paths are written there as absolute paths or relative to the DOSBox
	 * root folder; each is replaced in the form it was written in, as far as possible.
	 *
	 * @param moves old absolute path -> new absolute path
	 */
	public static String replaceInConfiguration(String conf, Map<String, String> moves, File dosroot) {
		record Replacement(String from, String to) {
		}
		List<Replacement> replacements = new ArrayList<>();
		for (Map.Entry<String, String> e: moves.entrySet()) {
			String oldPath = strip(e.getKey()), newPath = strip(e.getValue());
			replacements.add(new Replacement(oldPath, newPath));
			String oldRelative = relativeTo(oldPath, dosroot);
			if (oldRelative != null) {
				String newRelative = relativeTo(newPath, dosroot);
				replacements.add(new Replacement(oldRelative, newRelative != null ? newRelative: newPath));
			}
		}
		replacements.sort((a, b) -> b.from().length() - a.from().length()); // longest first
		String result = conf;
		for (Replacement r: replacements) {
			Pattern p = Pattern.compile("(?<=^|[\\s\"'=])" + Pattern.quote(r.from()) + "(?=$|[\\s\"'\\\\/;])", Pattern.MULTILINE | (ignoreCase() ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE: 0));
			result = p.matcher(result).replaceAll(Matcher.quoteReplacement(r.to()));
		}
		return result;
	}
}
