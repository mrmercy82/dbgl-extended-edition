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


public class ArchiveFileLocation extends FileLocation {

	private final IArchive archiver_;

	private File archiveFile_;

	public ArchiveFileLocation(String location, ICanonicalize canonicalizer, IArchive archiver) {
		super(FilenameUtils.separatorsToSystem(location), canonicalizer);
		archiver_ = archiver;
	}

	public File getArchiveFile() {
		if (archiveFile_ == null && file_ != null)
			archiveFile_ = archiver_.archive(file_);
		return archiveFile_;
	}

	public String getArchiveFileAsDosString() {
		if (getArchiveFile() == null)
			return null;
		return FilenameUtils.separatorsToWindows(getArchiveFile().getPath());
	}
}
