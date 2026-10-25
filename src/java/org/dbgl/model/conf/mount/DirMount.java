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
import org.dbgl.service.FileLocationService;
import org.dbgl.util.FilesUtils;


public class DirMount extends Mount {

	private FileLocation path_;
	private String lowlevelCD_, useCD_, freesize_;

	public DirMount() {
		path_ = null;
		lowlevelCD_ = StringUtils.EMPTY;
		useCD_ = StringUtils.EMPTY;
		freesize_ = StringUtils.EMPTY;
	}

	public File getPath() {
		return path_.getFile();
	}

	public File getCanonicalPath() {
		return path_.getCanonicalFile();
	}

	public void setPath(String location) {
		path_ = new FileLocation(location, FileLocationService.getInstance().dosrootRelative());
	}

	public String getLowlevelCD() {
		return lowlevelCD_;
	}

	public void setLowlevelCD(String lowlevelCD) {
		lowlevelCD_ = lowlevelCD;
	}

	public String getUseCD() {
		return useCD_;
	}

	public void setUseCD(String useCD) {
		useCD_ = useCD;
	}

	public String getFreesize() {
		return freesize_;
	}

	public void setFreesize(String freesize) {
		freesize_ = freesize;
	}

	@Override
	public File canBeUsedFor(FileLocation hostFile) {
		if (!FilesUtils.areRelated(getCanonicalPath(), hostFile.getCanonicalFile()))
			return null;
		return FilesUtils.makeRelativeTo(getCanonicalPath(), hostFile.getCanonicalFile());
	}

	@Override
	public void setBaseDir(File baseDir) {
		setPath(FilesUtils.concat(baseDir, getPath()));
	}

	@Override
	public void migrate(FileLocation from, FileLocation to) {
		path_ = FilesUtils.migrate(path_, from, to);
	}

	@Override
	public String getPathString() {
		return getPath().getPath();
	}

	@Override
	public String toString(boolean forUI) {
		StringBuilder result = new StringBuilder();
		result.append("mount ").append(drive_).append(" \"").append(path_.getFile()).append('"');
		if (StringUtils.isNotBlank(lowlevelCD_)) {
			result.append(" -").append(lowlevelCD_);
		}
		if (StringUtils.isNotBlank(useCD_)) {
			result.append(" -usecd ").append(useCD_);
		}
		if (StringUtils.isNotBlank(freesize_)) {
			result.append(" -freesize ").append(freesize_);
		}
		return extString(result, forUI);
	}
}
