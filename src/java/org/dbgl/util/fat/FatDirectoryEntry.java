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

import java.nio.ByteBuffer;
import org.dbgl.util.iso.Util;


/**
 *
 *
 * @author Ewout Prangsma &lt;epr at jnode.org&gt;
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 */
public final class FatDirectoryEntry {

	/**
	 * The size in bytes of an FAT directory entry.
	 */
	public static final int SIZE = 32;

	/**
	 * The offset to the attributes byte.
	 */
	private static final int OFFSET_ATTRIBUTES = 0x0b;

	private static final int F_READONLY = 0x01;
	private static final int F_HIDDEN = 0x02;
	private static final int F_SYSTEM = 0x04;
	private static final int F_VOLUME_ID = 0x08;
	private static final int F_DIRECTORY = 0x10;

	/**
	 * The magic byte denoting that this entry was deleted and is free for reuse.
	 *
	 * @see #isDeleted()
	 */
	public static final int ENTRY_DELETED_MAGIC = 0xe5;

	private final byte[] data_;

	FatDirectoryEntry(byte[] data) {
		data_ = data;
	}

	/**
	 * Reads a {@code FatDirectoryEntry} from the specified {@code ByteBuffer}. The buffer must have at least {@link #SIZE} bytes remaining. The entry is read from the buffer's current position, and
	 * if this method returns non-null the position will have advanced by {@link #SIZE} bytes, otherwise the position will remain unchanged.
	 *
	 * @param buff the buffer to read the entry from
	 * @return the directory entry that was read from the buffer or {@code null} if there was no entry to read from the specified position (first byte was 0)
	 */
	public static FatDirectoryEntry read(ByteBuffer buff) {
		if (buff.remaining() < SIZE)
			return null;

		/* peek into the buffer to see if we're done with reading */

		if (buff.get(buff.position()) == 0)
			return null;

		/* read the directory entry */

		byte[] data = new byte[SIZE];
		buff.get(data);
		return new FatDirectoryEntry(data);
	}

	/**
	 * Decides if this entry is a "volume label" entry according to the FAT specification.
	 *
	 * @return if this is a volume label entry
	 */
	public boolean isVolumeLabel() {
		return (getFlags() & (F_READONLY | F_HIDDEN | F_SYSTEM | F_DIRECTORY | F_VOLUME_ID)) == F_VOLUME_ID;
	}

	public boolean isLfnEntry() {
		int flags = getFlags();
		return (((flags & F_READONLY) != 0) && ((flags & F_HIDDEN) != 0) && ((flags & F_SYSTEM) != 0) && ((flags & F_VOLUME_ID) != 0));
	}

	private int getFlags() {
		return Util.getUInt8(data_, OFFSET_ATTRIBUTES);
	}

	public boolean isDirectory() {
		return ((getFlags() & (F_DIRECTORY | F_VOLUME_ID)) == F_DIRECTORY);
	}

	/**
	 * Returns if this entry has been marked as deleted. A deleted entry has its first byte set to the magic {@link #ENTRY_DELETED_MAGIC} value.
	 *
	 * @return if this entry is marked as deleted
	 */
	public boolean isDeleted() {
		return (Util.getUInt8(data_, 0) == ENTRY_DELETED_MAGIC);
	}

	/**
	 * Returns the {@code ShortName} that is stored in this directory entry or {@code null} if this entry has not been initialized.
	 *
	 * @return the {@code ShortName} stored in this entry or {@code null}
	 */
	public ShortName getShortName() {
		return data_[0] == 0 ? null: new ShortName(data_);
	}

	/**
	 * Does this entry refer to a file?
	 *
	 * @return
	 * @see org.jnode.fs.FSDirectoryEntry#isFile()
	 */
	public boolean isFile() {
		return ((getFlags() & (F_DIRECTORY | F_VOLUME_ID)) == 0);
	}

	/**
	 * Returns the startCluster.
	 *
	 * @return int
	 */
	public long getStartCluster() {
		return Util.getUInt16(data_, 0x1a);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [name=" + getShortName() + "]"; // NOI18N
	}

	String getLfnPart() {
		char[] unicodechar = new char[13];
		unicodechar[0] = (char)Util.getUInt16(data_, 1);
		unicodechar[1] = (char)Util.getUInt16(data_, 3);
		unicodechar[2] = (char)Util.getUInt16(data_, 5);
		unicodechar[3] = (char)Util.getUInt16(data_, 7);
		unicodechar[4] = (char)Util.getUInt16(data_, 9);
		unicodechar[5] = (char)Util.getUInt16(data_, 14);
		unicodechar[6] = (char)Util.getUInt16(data_, 16);
		unicodechar[7] = (char)Util.getUInt16(data_, 18);
		unicodechar[8] = (char)Util.getUInt16(data_, 20);
		unicodechar[9] = (char)Util.getUInt16(data_, 22);
		unicodechar[10] = (char)Util.getUInt16(data_, 24);
		unicodechar[11] = (char)Util.getUInt16(data_, 28);
		unicodechar[12] = (char)Util.getUInt16(data_, 30);

		int end = 0;
		while ((end < 13) && (unicodechar[end] != '\0')) {
			end++;
		}

		return new String(unicodechar).substring(0, end);
	}
}
