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
	/** parameters DBGL has no field for (e.g. of newer DOSBox versions), written again as they are */
	protected String extraOptions_ = StringUtils.EMPTY;
	/** the line this mount was read from, and how DBGL would write it then: unchanged, the line is written again exactly */
	private String original_, parsedForm_;

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

	public String getExtraOptions() {
		return extraOptions_;
	}

	public void setExtraOptions(String extraOptions) {
		extraOptions_ = StringUtils.defaultString(extraOptions).trim();
	}

	/** remember the line this mount was read from, so that it is written again exactly as long as the mount is not changed */
	public void rememberOriginal(String line) {
		original_ = line == null ? null: line.trim();
		parsedForm_ = toString(false);
	}

	/**
	 * A mount edited in the mount dialog is a new object: it keeps the line of the mount it replaces if nothing was changed, and
	 * the command used ("mount" or "imgmount").
	 */
	public void takeOverFrom(Mount previous) {
		if (previous == null)
			return;
		if ((this instanceof ImageMount img) && (previous instanceof ImageMount prev))
			img.setCommand(prev.getCommand());
		if ((previous.original_ != null) && toString(false).equals(previous.parsedForm_)) {
			original_ = previous.original_;
			parsedForm_ = previous.parsedForm_;
		}
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
			if (StringUtils.isNotBlank(extraOptions_)) {
				start.append(' ').append(extraOptions_);
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

	/** as written to the configuration file: the line it was read from if unchanged */
	@Override
	public String toString() {
		String line = toString(false);
		return (original_ != null) && line.equals(parsedForm_) ? original_: line;
	}
}
