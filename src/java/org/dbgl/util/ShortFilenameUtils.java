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
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.model.ShortFile;


public class ShortFilenameUtils {

	private static final Pattern VALID_DOS_FILENAME_REGEXP = Pattern.compile(
		"^[_$#@()!%{}`~_\\-&+^\\u00F7\\u00A0\\u00E5\\u00BDa-zA-Z0-9]{1,8}(\\.[_$#@()!%{}`~_\\-&+^\\u00F7\\u00A0\\u00E5\\u00BDa-zA-Z0-9]{1,3}){0,1}$");

	private ShortFilenameUtils() {
	}

	public static Set<String> convertToShortFileSet(Set<String> fileList) {
		Set<ShortFile> result = new TreeSet<>();
		for (String file: fileList.stream().sorted().toList())
			result.add(createShortFile(file, result));
		return result.stream().map(x -> new File(x.getFile().getParentFile(), x.getName()).getPath()).collect(Collectors.toCollection(HashSet::new));
	}

	private static ShortFile createShortFile(String path, Set<ShortFile> curDir) {
		String filename = FilenameUtils.getName(path);
		boolean createShort = false;
		if (StringUtils.contains(filename, ' ')) {
			filename = StringUtils.remove(filename, ' ');
			createShort = true;
		}
		int len = 0;
		int idx = filename.indexOf('.');
		if (idx != -1) {
			if (filename.length() - idx - 1 > 3) {
				filename = StringUtils.stripStart(filename, ".");
				createShort = true;
			}
			idx = filename.indexOf('.');
			len = (idx != -1) ? idx: filename.length();
		} else {
			len = filename.length();
		}
		createShort |= len > 8;

		ShortFile shortFile = null;
		if (!createShort) {
			shortFile = new ShortFile(new File(path), Strings.CS.removeEnd(filename, "."));
		} else {
			int i = 1;
			do {
				String nr = String.valueOf(i++);
				StringBuilder sb = new StringBuilder(StringUtils.left(filename, Math.min(8 - nr.length() - 1, len)));
				sb.append('~').append(nr);
				idx = filename.lastIndexOf('.');
				if (idx != -1)
					sb.append(StringUtils.left(filename.substring(idx), 4));
				shortFile = new ShortFile(new File(path), Strings.CS.removeEnd(sb.toString(), "."));
			} while (shortFile.isContainedIn(curDir));
		}
		return shortFile;
	}

	public static boolean valid(String dosFilename) {
		return (dosFilename != null) && (VALID_DOS_FILENAME_REGEXP.matcher(dosFilename).matches());
	}
}
