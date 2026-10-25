/*
 * Copyright (C) 2003-2009 JNode.org
 *               2009,2010 Matthias Treydte <mt@waldheinz.de>
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; If not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.dbgl.util.fat;

import java.io.IOException;


/**
 * Represents an entry in a {@link FatLfnDirectory}. Besides implementing the {@link FsDirectoryEntry} interface for FAT file systems, it allows access to the {@link #setArchiveFlag(boolean) archive},
 * {@link #setHiddenFlag(boolean) hidden}, {@link #setReadOnlyFlag(boolean) read-only} and {@link #setSystemFlag(boolean) system} flags specifed for the FAT file system.
 *
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 * @since 0.6
 */
public final class FatLfnDirectoryEntry {

	private final FatLfnDirectory parent_;
	private final FatDirectoryEntry realEntry_;
	private final String fileName_;

	FatLfnDirectoryEntry(FatLfnDirectory parent, FatDirectoryEntry realEntry, String fileName) {
		parent_ = parent;
		realEntry_ = realEntry;
		fileName_ = fileName;
	}

	static FatLfnDirectoryEntry extract(FatLfnDirectory dir, int offset, int len) {
		FatDirectoryEntry realEntry = dir.getEntry(offset + len - 1);
		String fileName;

		if (len == 1) {
			/* this is just an old plain 8.3 entry */
			fileName = realEntry.getShortName().asSimpleString();
		} else {
			/* stored in reverse order */
			StringBuilder name = new StringBuilder(13 * (len - 1));

			for (int i = len - 2; i >= 0; i--) {
				FatDirectoryEntry entry = dir.getEntry(i + offset);
				name.append(entry.getLfnPart());
			}

			fileName = name.toString().trim();
		}

		return new FatLfnDirectoryEntry(dir, realEntry, fileName);
	}

	public String getName() {
		return fileName_;
	}

	public ShortName getShortName() {
		return realEntry_.getShortName();
	}

	public String getShortNameAsString() {
		return getShortName().asSimpleString();
	}

	public FatLfnDirectory getDirectory() throws IOException {
		return parent_.getDirectory(realEntry_);
	}

	@Override
	public String toString() {
		return "LFN = " + fileName_ + " / SFN = " + realEntry_.getShortName();
	}

	public boolean isFile() {
		return realEntry_.isFile();
	}

	public boolean isDirectory() {
		return realEntry_.isDirectory();
	}

	public boolean isDeleted() {
		return realEntry_.isDeleted();
	}
}
