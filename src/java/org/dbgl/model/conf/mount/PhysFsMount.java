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


public class PhysFsMount extends DirMount {

	private FileLocation write_;

	public PhysFsMount() {
		write_ = null;
	}

	public File getWrite() {
		return write_ == null ? null: write_.getFile();
	}

	public File getCanonicalWrite() {
		return write_ == null ? null: write_.getCanonicalFile();
	}

	public void setWrite(String location) {
		write_ = StringUtils.isBlank(location) ? null: new FileLocation(location, FileLocationService.getInstance().dosrootRelative());
	}

	@Override
	public void setBaseDir(File baseDir) {
		super.setBaseDir(baseDir);
		if (write_ != null)
			setWrite(FilesUtils.concat(baseDir, getWrite()));
	}

	@Override
	public void migrate(FileLocation from, FileLocation to) {
		super.migrate(from, to);
		if (write_ != null)
			write_ = FilesUtils.migrate(write_, from, to);
	}

	@Override
	public String toString(boolean forUI) {
		StringBuilder result = new StringBuilder();
		result.append("mount ").append(drive_).append(" \"");
		if (write_ != null)
			result.append(write_.getFile()).append(':');
		result.append(getPath()).append(":\\\"");
		return extString(result, forUI);
	}
}
