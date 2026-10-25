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
package org.dbgl.model.conf.mount;

import java.io.File;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.FileLocation;


public class Mount {

	protected char drive_;
	protected String mountAs_, label_;
	protected boolean unmounted_;

	public Mount() {
		drive_ = '\0';
		mountAs_ = StringUtils.EMPTY;
		label_ = StringUtils.EMPTY;
		unmounted_ = false;
	}

	public char getDrive() {
		return drive_;
	}

	public String getDriveAsString() {
		return String.valueOf(drive_);
	}

	public void setDrive(char drive) {
		drive_ = Character.toUpperCase(drive);
	}

	public String getMountAs() {
		return mountAs_;
	}

	public void setMountAs(String mountAs) {
		mountAs_ = mountAs;
	}

	public String getLabel() {
		return label_;
	}

	public void setLabel(String label) {
		label_ = label;
	}

	public boolean isUnmounted() {
		return unmounted_;
	}

	public void setUnmounted(boolean unmounted) {
		unmounted_ = unmounted;
	}

	public boolean matchesDrive(char driveletter) {
		return drive_ == Character.toUpperCase(driveletter);
	}

	public File canBeUsedFor(FileLocation hostFile) {
		return null;
	}

	public void setBaseDir(File baseDir) {
		// baseDir is not relevant for unmounts
	}

	public void migrate(FileLocation from, FileLocation to) {
		// migration is not relevant for unmounts
	}

	public String getPathString() {
		return null;
	}

	protected String extString(StringBuilder start, boolean forUI) {
		if (unmounted_ && !forUI) {
			start.setLength(0);
			start.append("mount -u ").append(drive_);
		} else {
			if (StringUtils.isNotBlank(label_)) {
				start.append(" -label ").append(label_);
			}
			if (StringUtils.isNotBlank(mountAs_)) {
				start.append(" -t ").append(mountAs_);
			}
			if (unmounted_) {
				start.append(" (UNMOUNTED)");
			}
		}
		return start.toString();
	}

	public String toString(boolean forUI) {
		return extString(new StringBuilder(), forUI);
	}

	@Override
	public String toString() {
		return toString(false);
	}
}
