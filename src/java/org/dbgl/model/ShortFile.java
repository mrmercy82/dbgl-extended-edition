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
package org.dbgl.model;

import java.io.File;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;


public class ShortFile implements Comparable<ShortFile> {

	private File file_;
	private String name_;

	public ShortFile(File file, String name) {
		file_ = file;
		name_ = name;
	}

	public File getFile() {
		return file_;
	}

	public String getName() {
		return name_;
	}

	public String getFormattedName() {
		return file_.isDirectory() ? '[' + name_ + ']': name_;
	}

	public boolean isContainedIn(Set<ShortFile> set) {
		int count = StringUtils.countMatches(file_.getPath(), "\\");
		int idx1 = name_.indexOf('~');
		int idx2 = (idx1 == -1) ? -1: name_.indexOf('.', idx1 + 2);
		if (idx2 == -1)
			idx2 = Math.min(name_.length(), 8);

		for (ShortFile shortFile: set) {
			if (count != StringUtils.countMatches(shortFile.file_.getPath(), "\\"))
				return false;
			if (idx1 != -1 && idx1 == shortFile.name_.indexOf('~')) {
				int idx3 = shortFile.name_.indexOf('.', idx1 + 2);
				if (idx3 == -1)
					idx3 = Math.min(shortFile.name_.length(), 8);
				if (idx2 == idx3 && name_.substring(0, idx2).equals(shortFile.name_.substring(0, idx2)))
					return true;
			} else if (name_.equals(shortFile.name_)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(file_, name_);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof ShortFile))
			return false;
		ShortFile that = (ShortFile)obj;
		return ((file_ == null && that.file_ == null) || (file_ != null && that.file_ != null && file_.equals(that.file_))) && Strings.CS.equals(name_, that.name_);
	}

	@Override
	public int compareTo(ShortFile o) {
		int count1 = StringUtils.countMatches(o.file_.getPath(), "\\");
		int count2 = StringUtils.countMatches(file_.getPath(), "\\");
		if (count1 != count2)
			return count1 - count2;
		int res = Boolean.valueOf(o.file_.isDirectory()).compareTo(file_.isDirectory());
		if (res != 0)
			return res;
		int idx1 = name_.indexOf('~');
		if (idx1 != -1 && idx1 == o.name_.indexOf('~')) {
			int idx2 = name_.indexOf('.', idx1 + 2);
			if (idx2 != -1 && idx2 == o.name_.indexOf('.', idx1 + 2))
				return name_.substring(0, idx2).compareTo(o.name_.substring(0, idx2));
		}
		return name_.compareTo(o.name_);
	}
}