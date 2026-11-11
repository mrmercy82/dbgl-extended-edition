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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Alpha 47: when only the mounts of a profile changed (one added in the profile dialog, one changed or removed), only those
 * lines of its [autoexec] are changed, the rest stays as it was written. Before, DBGL wrote the whole [autoexec] again as it
 * understands it, which could turn a long start script around ("win" before "cd windows"). Whether the result says what DBGL
 * means is checked by the caller (read again, it must give the same [autoexec]).
 */
public final class MountLines {

	// "mount c path", "MOUNT D "path" -t cdrom", "imgmount d x.iso -t iso", "@mount e .", "imgmount 0 disk.img" (a drive number)
	private static final Pattern MOUNT = Pattern.compile("^\\s*@?(?:mount|imgmount)\\s+\"?([A-Za-z0-9])(?::)?\"?(?:\\s|$)", Pattern.CASE_INSENSITIVE);

	private MountLines() {
	}

	/** the drive a line mounts, upper case, or null if it is no mount line ("mount -u c", "mount" alone) */
	static String drive(String line) {
		Matcher m = MOUNT.matcher(line);
		return m.find() ? m.group(1).toUpperCase(Locale.ROOT): null;
	}

	private static List<String> lines(String text) {
		return new ArrayList<>(List.of(text.split("\\R", -1)));
	}

	private static Map<String, List<String>> mounts(List<String> lines) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		for (String l: lines) {
			String d = drive(l);
			if (d != null)
				result.computeIfAbsent(d, k -> new ArrayList<>()).add(l.trim());
		}
		return result;
	}

	private static List<String> others(List<String> lines) {
		List<String> result = new ArrayList<>();
		for (String l: lines)
			if ((drive(l) == null) && !l.isBlank())
				result.add(l.trim());
		return result;
	}

	/** whether two [autoexec] as DBGL writes them differ, and only in their mount lines */
	public static boolean onlyMountsDiffer(String before, String now) {
		if ((before == null) || (now == null) || before.equals(now))
			return false;
		List<String> b = lines(before), n = lines(now);
		return others(b).equals(others(n)) && !mounts(b).equals(mounts(n));
	}

	/**
	 * @param written the [autoexec] as written in the file
	 * @param before what DBGL writes for it as it was read
	 * @param now what DBGL writes for it now
	 * @return written, its mount lines changed as from before to now; null if more than mount lines changed, or a mount line to
	 *         change cannot be told for sure (a drive mounted more than once in the file, no mount line at all for a new one)
	 */
	public static String patch(String written, String before, String now) {
		if (!onlyMountsDiffer(before, now))
			return null;
		Map<String, List<String>> mb = mounts(lines(before)), mn = mounts(lines(now));
		List<String> out = lines(written);
		Set<String> drives = new LinkedHashSet<>(mb.keySet());
		drives.addAll(mn.keySet());
		for (String d: drives) {
			List<String> was = mb.getOrDefault(d, List.of()), is = mn.getOrDefault(d, List.of());
			if (was.equals(is))
				continue;
			List<Integer> at = new ArrayList<>();
			for (int i = 0; i < out.size(); i++)
				if (d.equals(drive(out.get(i))))
					at.add(i);
			if (was.isEmpty()) { // a new drive: after the last mount line of the file
				if (!at.isEmpty())
					return null;
				int last = -1;
				for (int i = 0; i < out.size(); i++)
					if (drive(out.get(i)) != null)
						last = i;
				if (last < 0)
					return null;
				out.addAll(last + 1, is);
			} else {
				if (at.size() != was.size())
					return null; // not the same lines as DBGL read: unsure which one is meant
				for (int i = at.size() - 1; i >= 0; i--)
					out.remove((int)at.get(i));
				out.addAll(at.get(0), is); // changed: where it was; gone (is empty): nothing in its place
			}
		}
		return String.join(org.dbgl.util.SystemUtils.EOLN, out);
	}
}
