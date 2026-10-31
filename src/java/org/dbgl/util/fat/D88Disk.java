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
package org.dbgl.util.fat;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.TreeMap;


/**
 * A .D88 floppy disk image (PC-88/PC-98), as DOSBox-X reads it (imagedisk_d88.cpp):
 * <ul>
 * <li>header of 0x2B0 bytes: name, write protection, disk type (+0x1B), disk size (+0x1C), then 164 track offsets from +0x20</li>
 * <li>each track is a list of sectors, each with a 16 byte header (cylinder, head, sector number, size code, number of
 * sectors in the track at +4, data size at +0xE) followed by the data</li>
 * </ul>
 * The sectors are put in order (cylinder, head, sector number) into one plain disk image in memory, so the FAT file system can
 * be read like from any other image.
 */
public final class D88Disk implements BlockDevice {

	private static final int HEADER_SIZE = 0x2b0;
	private static final int TRACK_MAX = 164;
	private static final int MAX_IMAGE_SIZE = 8 * 1024 * 1024; // floppies are 1-2 MB

	private final byte[] data_;
	private boolean closed_;

	private D88Disk(byte[] data) {
		data_ = data;
	}

	public static D88Disk open(File file) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
			long fileSize = raf.length();
			if ((fileSize < HEADER_SIZE) || (fileSize > MAX_IMAGE_SIZE))
				throw new IOException("not a D88 image: " + file);
			byte[] all = new byte[(int)fileSize];
			raf.readFully(all);
			ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
			if ((bb.getInt(0x1c) & 0xffffffffL) > fileSize)
				throw new IOException("D88 disk size larger than the file: " + file);

			// key: cylinder, head, sector number -> data
			TreeMap<Long, byte[]> sectors = new TreeMap<>();
			for (int track = 0; track < TRACK_MAX; track++) {
				long trackOffset = bb.getInt(0x20 + track * 4) & 0xffffffffL;
				if ((trackOffset == 0) || (trackOffset + 16 > fileSize))
					continue;
				int pos = (int)trackOffset;
				int count = 0;
				while (pos + 16 <= fileSize) {
					int c = all[pos] & 0xff;
					int h = all[pos + 1] & 0xff;
					int r = all[pos + 2] & 0xff;
					int sectorCount = bb.getShort(pos + 4) & 0xffff;
					int size = bb.getShort(pos + 14) & 0xffff;
					if ((sectorCount == 0) || (sectorCount > 128) || (size < 128) || (size > 16384) || (pos + 16 + size > fileSize))
						break;
					byte[] sector = new byte[size];
					System.arraycopy(all, pos + 16, sector, 0, size);
					sectors.putIfAbsent(((long)c << 24) | ((long)h << 16) | r, sector);
					pos += 16 + size;
					if (++count >= sectorCount)
						break;
				}
			}
			if (sectors.isEmpty())
				throw new IOException("no sectors in D88 image: " + file);

			int total = sectors.values().stream().mapToInt(x -> x.length).sum();
			byte[] image = new byte[total];
			int offset = 0;
			for (byte[] sector: sectors.values()) {
				System.arraycopy(sector, 0, image, offset, sector.length);
				offset += sector.length;
			}
			return new D88Disk(image);
		}
	}

	@Override
	public long getSize() {
		return data_.length;
	}

	@Override
	public void read(long devOffset, ByteBuffer dest) throws IOException {
		if (closed_)
			throw new IllegalStateException("device already closed");
		int length = dest.remaining();
		if ((devOffset < 0) || (devOffset + length > data_.length))
			throw new IOException("reading past end of device");
		dest.put(data_, (int)devOffset, length);
	}

	@Override
	public int getSectorSize() {
		return 512;
	}

	@Override
	public boolean isPartition() {
		return true; // a floppy: the boot sector is at the start
	}

	@Override
	public void close() {
		closed_ = true;
	}

	@Override
	public boolean isClosed() {
		return closed_;
	}
}
