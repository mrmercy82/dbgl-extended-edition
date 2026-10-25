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
package org.dbgl.util.iso;

import java.io.IOException;
import org.apache.commons.lang3.StringUtils;


public class ISO9660FileEntry {

	private static final char ID_SEPARATOR = ';';

	private final int entryLength_;
	private final long startSector_;
	private final int dataLength_;
	private final int flags_;
	private final String identifier_;

	private final ISO9660FileSystem fileSystem_;
	private final String parentPath_;

	public ISO9660FileEntry(ISO9660FileSystem fileSystem, byte[] block, int pos) {
		this(fileSystem, null, block, pos);
	}

	/**
	 * Initialize this instance.
	 *
	 * @param fileSystem the parent file system
	 * @param parentPath the path of the parent directory
	 * @param block      the bytes of the sector containing this file entry
	 * @param startPos   the starting position of this file entry
	 */
	public ISO9660FileEntry(ISO9660FileSystem fileSystem, String parentPath, byte[] block, int startPos) {
		fileSystem_ = fileSystem;
		parentPath_ = parentPath;

		int offset = startPos - 1;
		entryLength_ = Util.getUInt8(block, offset);
		startSector_ = Util.getUInt32(block, offset + 2);
		dataLength_ = (int)Util.getUInt32(block, offset + 10);
		flags_ = Util.getUInt8(block, offset + 25);
		identifier_ = getFileIdentifier(block, offset, isDirectory());
	}

	private String getFileIdentifier(byte[] block, int offset, boolean isDir) {
		int fidLength = Util.getUInt8(block, offset + 32);
		if (isDir) {
			int buff34 = Util.getUInt8(block, offset + 33);
			if ((fidLength == 1) && (buff34 == 0x00)) {
				return ".";
			} else if ((fidLength == 1) && (buff34 == 0x01)) {
				return "..";
			}
		}
		try {
			String id = Util.getDChars(block, offset + 34, fidLength, fileSystem_.getEncoding());
			int sepIdx = id.indexOf(ID_SEPARATOR);
			return sepIdx >= 0 ? id.substring(0, sepIdx): id;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

	}

	public String getPath() {
		if (".".equals(getName())) {
			return StringUtils.EMPTY;
		}
		StringBuilder buf = new StringBuilder();
		if (parentPath_ != null) {
			buf.append(parentPath_);
		}
		buf.append(getName());
		if (isDirectory()) {
			buf.append("/");
		}
		return buf.toString();
	}

	public boolean isDirectory() {
		return (flags_ & 0x02) != 0;
	}

	String getName() {
		return identifier_;
	}

	int getSize() {
		return dataLength_;
	}

	long getStartBlock() {
		return startSector_;
	}

	int getEntryLength() {
		return entryLength_;
	}
}
