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
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


/**
 * This is the abstract base class for all directory implementations.
 *
 * @author Ewout Prangsma &lt;epr at jnode.org&gt;
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 */
public abstract class AbstractDirectory {

	private final List<FatDirectoryEntry> entries_;
	private final int capacity_;
	private final boolean isRoot_;

	/**
	 * Creates a new instance of {@code AbstractDirectory}.
	 *
	 * @param capacity the initial capacity of the new instance
	 * @param readOnly if the instance should be read-only
	 * @param isRoot   if the new {@code AbstractDirectory} represents a root directory
	 */
	protected AbstractDirectory(int capacity, boolean isRoot) {
		entries_ = new ArrayList<>();
		capacity_ = capacity;
		isRoot_ = isRoot;
	}

	/**
	 * Gets called when the {@code AbstractDirectory} must read it's content off the backing storage. This method must always fill the buffer's remaining space with the bytes making up this directory,
	 * beginning with the first byte.
	 *
	 * @param data the {@code ByteBuffer} to fill
	 * @throws IOException on read error
	 */
	protected abstract void read(ByteBuffer data) throws IOException;

	public final FatDirectoryEntry getEntry(int idx) {
		return entries_.get(idx);
	}

	/**
	 * The number of entries that are currently stored in this {@code AbstractDirectory}.
	 *
	 * @return the current number of directory entries
	 */
	public final int getEntryCount() {
		return entries_.size();
	}

	public final boolean isRoot() {
		return isRoot_;
	}

	public final void read() throws IOException {
		ByteBuffer data = ByteBuffer.allocate(capacity_ * FatDirectoryEntry.SIZE);

		read(data);
		((Buffer)data).flip();

		for (int i = 0; i < capacity_; i++) {
			FatDirectoryEntry e = FatDirectoryEntry.read(data);

			if (e == null)
				break;

			if (e.isVolumeLabel()) {
				if (!isRoot_)
					throw new IOException("volume label in non-root directory");
			} else {
				entries_.add(e);
			}
		}
	}
}
