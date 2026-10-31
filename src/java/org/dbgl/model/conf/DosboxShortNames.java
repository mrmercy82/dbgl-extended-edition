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
package org.dbgl.model.conf;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/**
 * The DOS (8.3) names DOSBox gives to files and directories of a mounted host directory.
 *
 * DOS without long filename support cannot use names like "M&M 3" or "MM 3.EXE": a space ends a command, so
 * "cd \M&M 3" and "MM 3.EXE" don't work in the autoexec. DOSBox (DOSBox, SVN, ECE, Staging and DOSBox-X) makes such names
 * available as short names, e.g. "M&M3~1" and "MM3~1.EXE". This class repeats that algorithm (DOS_Drive_Cache::CreateShortName
 * in drive_cache.cpp), so DBGL can write names into the autoexec that DOSBox understands, and read them back.
 *
 * The number after the '~' depends on the other names in the same directory and on the order in which the host returns
 * them. File.list() uses the same host functions as DOSBox (readdir, FindFirstFile), so the order matches.
 */
public final class DosboxShortNames {

	private static final int DOS_NAMELENGTH = 12;

	private DosboxShortNames() {
	}

	/** @return true if DOSBox gives this host name a generated short name (~n) */
	public static boolean needsShortName(String name) {
		String tmp = name.toUpperCase(Locale.ROOT);
		boolean createShort = tmp.indexOf(' ') != -1;
		tmp = tmp.replace(" ", "");
		int pos = tmp.indexOf('.');
		int len;
		if (pos != -1) {
			if (tmp.length() - pos > 4) {
				tmp = stripLeadingDots(tmp);
				createShort = true;
			}
			pos = tmp.indexOf('.');
			len = pos != -1 ? pos: tmp.length();
		} else {
			len = tmp.length();
		}
		return createShort || (len > 8);
	}

	/** @return the name DOSBox uses for the entry 'name' in host directory 'dir' */
	public static String toDosName(File dir, String name) {
		if (!needsShortName(name))
			return name;
		String result = shortNames(dir).get(name);
		return result != null ? result: name;
	}

	/**
	 * @param root     the mounted host directory
	 * @param relative path relative to root, e.g. "M &amp; M/M&amp;M 3/MM 3.EXE"
	 * @return the DOS path components, e.g. [M&amp;M~1, M&amp;M3~1, MM3~1.EXE]
	 */
	public static List<String> toDosPath(File root, File relative) {
		List<String> components = new ArrayList<>();
		for (File f = relative; f != null; f = f.getParentFile())
			if (!f.getName().isEmpty())
				components.add(0, f.getName());
		List<String> result = new ArrayList<>();
		File current = root;
		for (String c: components) {
			result.add(toDosName(current, c));
			current = new File(current, c);
		}
		return result;
	}

	/** @return the host name of the entry that DOSBox calls 'dosName' in host directory 'dir', or dosName if there is none */
	public static String toHostName(File dir, String dosName) {
		if (dosName.equals(".") || dosName.equals(".."))
			return dosName;
		String[] entries = dir.list();
		if (entries == null)
			return dosName;
		for (String e: entries)
			if (e.equals(dosName))
				return e;
		for (String e: entries)
			if (e.equalsIgnoreCase(dosName) && !needsShortName(e))
				return e;
		for (Map.Entry<String, String> e: shortNames(dir, entries).entrySet())
			if (e.getValue().equalsIgnoreCase(dosName))
				return e.getKey();
		return dosName;
	}

	/**
	 * @param root    the mounted host directory
	 * @param dosPath a DOS path relative to root, components separated by \ or /
	 * @return the host file
	 */
	public static File toHostPath(File root, String dosPath) {
		File current = root;
		for (String c: dosPath.split("[\\\\/]+")) {
			if (c.isEmpty() || c.equals("."))
				continue;
			if (c.equals("..")) {
				current = current.getParentFile() != null ? current.getParentFile(): current;
				continue;
			}
			current = new File(current, toHostName(current, c));
		}
		return current;
	}

	private static Map<String, String> shortNames(File dir) {
		String[] entries = dir.list();
		return entries == null ? Map.of(): shortNames(dir, entries);
	}

	/** host name to DOSBox name for all entries of a directory, created in host order like DOSBox does */
	private static Map<String, String> shortNames(File dir, String[] entries) {
		Set<String> fileList = new HashSet<>(); // short names given so far
		List<ShortEntry> longNameList = new ArrayList<>(); // generated short names, sorted
		Map<String, String> result = new HashMap<>();
		for (String name: entries)
			result.put(name, createShortName(name, fileList, longNameList));
		return result;
	}

	private record ShortEntry(String shortName, int nr) {
	}

	private static String createShortName(String orgName, Set<String> fileList, List<ShortEntry> longNameList) {
		String tmp = orgName.toUpperCase(Locale.ROOT);
		boolean createShort = tmp.indexOf(' ') != -1;
		tmp = tmp.replace(" ", "");

		int pos = tmp.indexOf('.');
		int len;
		if (pos != -1) {
			if (tmp.length() - pos > 4) {
				tmp = stripLeadingDots(tmp);
				createShort = true;
			}
			pos = tmp.indexOf('.');
			len = pos != -1 ? pos: tmp.length();
		} else {
			len = tmp.length();
		}

		createShort = createShort || (len > 8);
		if (!createShort)
			createShort = fileList.contains(removeTrailingDot(tmp)); // a short name like this was already given

		String shortName;
		if (createShort) {
			int nr = createShortNameId(tmp, longNameList);
			String nrStr = String.valueOf(nr);
			int toCopy = (len + nrStr.length() + 1 > 8) ? 8 - nrStr.length() - 1: len;
			StringBuilder sb = new StringBuilder(tmp.substring(0, Math.max(0, Math.min(toCopy, tmp.length()))));
			sb.append('~').append(nrStr);
			if (pos != -1) {
				String ext = tmp.substring(tmp.lastIndexOf('.'));
				sb.append(ext, 0, Math.min(4, ext.length()));
			}
			shortName = sb.length() > DOS_NAMELENGTH ? sb.substring(0, DOS_NAMELENGTH): sb.toString();

			// keep the list sorted
			int i = 0;
			while ((i < longNameList.size()) && (strcmp(shortName, longNameList.get(i).shortName()) >= 0))
				i++;
			longNameList.add(i, new ShortEntry(shortName, nr));
		} else {
			shortName = tmp;
		}
		fileList.add(removeTrailingDot(shortName));
		return removeTrailingDot(shortName);
	}

	private static int createShortNameId(String name, List<ShortEntry> longNameList) {
		if (longNameList.isEmpty())
			return 1;
		int foundNr = 0;
		int low = 0;
		int high = longNameList.size() - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			int res = compareShortname(name, longNameList.get(mid).shortName());
			if (res > 0) {
				low = mid + 1;
			} else if (res < 0) {
				high = mid - 1;
			} else {
				do {
					foundNr = longNameList.get(mid).nr();
					mid++;
				} while ((mid < longNameList.size()) && (compareShortname(name, longNameList.get(mid).shortName()) == 0));
				break;
			}
		}
		return foundNr + 1;
	}

	private static int compareShortname(String compareName, String shortName) {
		int cpos = shortName.indexOf('~');
		if (cpos == -1)
			return strcmp(compareName, shortName);
		int compareCount1 = cpos;
		int numberSize = strcspn(shortName.substring(cpos), '.');
		int compareCount2 = strcspn(compareName, '.');
		if (compareCount2 > 8)
			compareCount2 = 8;
		if (compareCount2 > compareCount1 + numberSize)
			compareCount1 = compareCount2 - numberSize;
		return strncmp(compareName, shortName, compareCount1);
	}

	private static int strcspn(String s, char c) {
		int i = s.indexOf(c);
		return i == -1 ? s.length(): i;
	}

	private static int strcmp(String a, String b) {
		return strncmp(a, b, Integer.MAX_VALUE);
	}

	private static int strncmp(String a, String b, int n) {
		for (int i = 0; i < n; i++) {
			int ca = i < a.length() ? a.charAt(i): 0;
			int cb = i < b.length() ? b.charAt(i): 0;
			if (ca != cb)
				return ca - cb;
			if (ca == 0)
				return 0;
		}
		return 0;
	}

	private static String stripLeadingDots(String s) {
		int i = 0;
		while ((i < s.length()) && (s.charAt(i) == '.'))
			i++;
		return s.substring(i);
	}

	private static String removeTrailingDot(String s) {
		if (s.length() > 1 && s.endsWith(".") && !s.equals(".."))
			return s.substring(0, s.length() - 1);
		return s;
	}
}
