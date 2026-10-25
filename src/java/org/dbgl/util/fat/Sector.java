/*
 * Copyright (C) 2009,2010 Matthias Treydte <mt@waldheinz.de>
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
import java.nio.ByteOrder;


/**
 *
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 */
class Sector {

	private final BlockDevice device_;

	protected final long offset_;
	protected final ByteBuffer buffer_;

	protected Sector(BlockDevice device, long offset, int size) {
		device_ = device;
		offset_ = offset;
		buffer_ = ByteBuffer.allocate(size);
		buffer_.order(ByteOrder.LITTLE_ENDIAN);
	}

	/**
	 * Reads the contents of this {@code Sector} from the device into the internal buffer and resets the "dirty" state.
	 *
	 * @throws IOException on read error
	 * @see #isDirty()
	 */
	protected void read() throws IOException {
		((Buffer)buffer_).rewind();
		((Buffer)buffer_).limit(buffer_.capacity());
		device_.read(offset_, buffer_);
	}

	/**
	 * Returns the {@code BlockDevice} where this {@code Sector} is stored.
	 *
	 * @return this {@code Sector}'s device
	 */
	public BlockDevice getDevice() {
		return device_;
	}

	protected int get8(int offset) {
		return buffer_.get(offset) & 0xff;
	}

	protected int get16(int offset) {
		return buffer_.getShort(offset) & 0xffff;
	}

	protected long get32(int offset) {
		return buffer_.getInt(offset);
	}
}
