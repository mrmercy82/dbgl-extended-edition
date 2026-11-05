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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * When DOSBox writes its configuration file again (e.g. after an update, to get the settings of the new version), the user's
 * own content must not be lost: the whole [autoexec] (e.g. "mount C ..." that all profiles rely on) and every value of a setting
 * the new version still has are taken over from the previous file. Comments and new settings come from the new file.
 */
public final class ConfMerge {

	private static final Pattern SECTION_PATRN = Pattern.compile("^\\s*\\[([^\\]]+)\\]\\s*$");
	private static final Pattern VALUE_PATRN = Pattern.compile("^(\\s*)([^#;=\\s][^=]*?)(\\s*=\\s*)(.*)$");

	private ConfMerge() {
	}

	/** take the values and the [autoexec] of the previous file into the new one, which is written again */
	public static void keepPrevious(File previous, File fresh) throws IOException {
		Charset cs = StandardCharsets.ISO_8859_1; // byte for byte, whatever the encoding
		List<String> oldLines = Files.readAllLines(previous.toPath(), cs);
		List<String> newLines = Files.readAllLines(fresh.toPath(), cs);
		Files.write(fresh.toPath(), merge(oldLines, newLines), cs);
	}

	static List<String> merge(List<String> oldLines, List<String> newLines) {
		Map<String, String> oldValues = new HashMap<>();
		List<String> oldAutoexec = null;
		String section = "";
		for (String line: oldLines) {
			Matcher s = SECTION_PATRN.matcher(line);
			if (s.matches()) {
				section = s.group(1).trim().toLowerCase();
				if (section.equals("autoexec"))
					oldAutoexec = new ArrayList<>();
				continue;
			}
			if (section.equals("autoexec")) {
				oldAutoexec.add(line);
				continue;
			}
			Matcher v = VALUE_PATRN.matcher(line);
			if (v.matches())
				oldValues.put(section + '\u0000' + v.group(2).trim().toLowerCase(), v.group(4));
		}

		List<String> result = new ArrayList<>();
		boolean autoexecWritten = false, inAutoexec = false;
		section = "";
		for (String line: newLines) {
			Matcher s = SECTION_PATRN.matcher(line);
			if (s.matches()) {
				section = s.group(1).trim().toLowerCase();
				inAutoexec = section.equals("autoexec");
				result.add(line);
				if (inAutoexec && (oldAutoexec != null)) {
					result.addAll(oldAutoexec);
					autoexecWritten = true;
				}
				continue;
			}
			if (inAutoexec && autoexecWritten)
				continue; // the previous [autoexec] replaces the new one
			Matcher v = VALUE_PATRN.matcher(line);
			if (v.matches()) {
				String old = oldValues.get(section + '\u0000' + v.group(2).trim().toLowerCase());
				if (old != null) {
					result.add(v.group(1) + v.group(2) + v.group(3) + old);
					continue;
				}
			}
			result.add(line);
		}
		if ((oldAutoexec != null) && !autoexecWritten) {
			result.add("[autoexec]");
			result.addAll(oldAutoexec);
		}
		return result;
	}
}
