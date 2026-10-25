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
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.service.FileLocationService;


public class FileLocation {

	protected final String location_;
	protected final ICanonicalize canonicalizer_;

	protected File file_;
	protected File canonicalFile_;

	public FileLocation(String location, ICanonicalize canonicalizer) {
		location_ = StringUtils.trim(location);
		canonicalizer_ = canonicalizer;
		if (location_ != null)
			file_ = canonicalizer_.initialize(location_);
	}
	
	public FileLocation(String location) {
		this(location, FileLocationService.standard());
	}

	public File getFile() {
		return file_;
	}

	public String getFileAsDosString() {
		if (file_ == null)
			return null;
		return FilenameUtils.separatorsToWindows(file_.getPath());
	}

	public File getCanonicalFile() {
		if (canonicalFile_ == null && file_ != null)
			canonicalFile_ = canonicalizer_.canonicalize(file_);
		return canonicalFile_;
	}

	public ICanonicalize getCanonicalizer() {
		return canonicalizer_;
	}

	@Override
	public String toString() {
		return "location:[" + location_ + "], file:[" + file_ + "], canonical:[" + getCanonicalFile() + "]";
	}
}
