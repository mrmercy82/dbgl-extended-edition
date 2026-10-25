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

import org.dbgl.util.iso.Util;


/**
 * Enumerates the different entry sizes of 12, 16 and 32 bits for the different FAT flavours.
 *
 * @author Ewout Prangsma &lt;epr at jnode.org&gt;
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 */
public enum FatType {

	/**
	 * Represents a 12-bit file allocation table.
	 */
	FAT12(0xFFFL, 1.5f) { // NOI18N

		@Override
		public long readEntry(byte[] data, int index) {
			int v = Util.getUInt16(data, (int)(index * 1.5));
			return ((index % 2) == 0) ? v & 0xFFF: v >> 4;
		}
	},

	/**
	 * Represents a 16-bit file allocation table.
	 */
	FAT16(0xFFFFL, 2.0f) { // NOI18N

		@Override
		public long readEntry(byte[] data, int index) {
			return Util.getUInt16(data, index * 2);
		}
	},

	/**
	 * Represents a 32-bit file allocation table.
	 */
	FAT32(0xFFFFFFFFL, 4.0f) { // NOI18N

		@Override
		public long readEntry(byte[] data, int index) {
			return Util.getUInt32(data, index * 4);
		}
	};

	private final long eofCluster_;
	private final float entrySize_;

	private FatType(long bitMask, float entrySize) {
		eofCluster_ = (0xFFFFFF8L & bitMask);
		entrySize_ = entrySize;
	}

	public boolean isEofCluster(long entry) {
		return (entry >= eofCluster_);
	}

	public float getEntrySize() {
		return entrySize_;
	}

	public abstract long readEntry(byte[] data, int index);
}
