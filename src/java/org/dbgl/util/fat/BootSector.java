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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.apache.commons.lang3.StringUtils;
import java.nio.Buffer;


/**
 * The boot sector.
 *
 * @author Ewout Prangsma &lt;epr at jnode.org&gt;
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 */
public abstract class BootSector extends Sector {

	/**
	 * Offset to the byte specifying the number of FATs.
	 *
	 * @see #getNrFats()
	 * @see #setNrFats(int)
	 */
	public static final int FAT_COUNT_OFFSET = 16;
	public static final int RESERVED_SECTORS_OFFSET = 14;
	public static final int TOTAL_SECTORS_16_OFFSET = 19;
	public static final int TOTAL_SECTORS_32_OFFSET = 32;

	/**
	 * The length of the file system type string.
	 *
	 * @see #getFileSystemType()
	 */
	public static final int FILE_SYSTEM_TYPE_LENGTH = 8;

	/**
	 * The offset to the sectors per cluster value stored in a boot sector.
	 *
	 * @see #getSectorsPerCluster()
	 * @see #setSectorsPerCluster(int)
	 */
	public static final int SECTORS_PER_CLUSTER_OFFSET = 0x0d;

	/**
	 * The size of a boot sector in bytes.
	 */
	public static final int SIZE = 512;

	protected BootSector(BlockDevice device, long offset) {
		super(device, offset, SIZE);
	}

	public static BootSector read(BlockDevice device) throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(512);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		long offset = 0;

		if (device.getSize() > (2880 * 1024)) {
			offset = 63L * SIZE;
			device.read(0, bb);
			if ((bb.get(510) & 0xff) != 0x55 || (bb.get(511) & 0xff) != 0xaa)
				System.err.println("missing boot sector signature");
			for (int i = 0; i < 4; i++) {
				int partSize = bb.getInt(446 + ((i * 16) + 12));
				if (partSize > 0) {
					offset = bb.getInt(446 + ((i * 16) + 8)) * (long)SIZE;
					break;
				}
			}
		}

		((Buffer)bb).clear();
		device.read(offset, bb);

		if ((bb.get(510) & 0xff) != 0x55 || (bb.get(511) & 0xff) != 0xaa)
			System.err.println("missing boot sector signature");

		byte sectorsPerCluster = bb.get(SECTORS_PER_CLUSTER_OFFSET);

		if (sectorsPerCluster <= 0)
			throw new IOException("suspicious sectors per cluster count " + sectorsPerCluster);

		int rootDirEntries = bb.getShort(Fat16BootSector.ROOT_DIR_ENTRIES_OFFSET);
		int rootDirSectors = ((rootDirEntries * 32) + (device.getSectorSize() - 1)) / device.getSectorSize();

		int total16 = bb.getShort(TOTAL_SECTORS_16_OFFSET) & 0xffff;
		long total32 = bb.getInt(TOTAL_SECTORS_32_OFFSET) & 0xffffffffl;

		long totalSectors = total16 == 0 ? total32: total16;

		int fatSz16 = bb.getShort(Fat16BootSector.SECTORS_PER_FAT_OFFSET) & 0xffff;
		long fatSz32 = bb.getInt(Fat32BootSector.SECTORS_PER_FAT_OFFSET) & 0xffffffffl;

		long fatSz = fatSz16 == 0 ? fatSz32: fatSz16;
		long dataSectors = totalSectors - (bb.getShort(RESERVED_SECTORS_OFFSET) + (bb.get(FAT_COUNT_OFFSET) * fatSz) + rootDirSectors);

		long clusterCount = dataSectors / sectorsPerCluster;

		BootSector result = (clusterCount > Fat16BootSector.MAX_FAT16_CLUSTERS) ? new Fat32BootSector(device, offset): new Fat16BootSector(device, offset);

		result.read();
		return result;
	}

	public abstract FatType getFatType();

	/**
	 * Gets the number of sectors per FAT.
	 *
	 * @return the sectors per FAT
	 */
	public abstract long getSectorsPerFat();

	public abstract int getRootDirEntryCount();

	public abstract long getSectorCount();

	/**
	 * Returns the number of clusters that are really needed to cover the data-caontaining portion of the file system.
	 *
	 * @return the number of clusters usable for user data
	 * @see #getDataSize()
	 */
	public final long getDataClusterCount() {
		return getDataSize() / getBytesPerCluster();
	}

	/**
	 * Returns the size of the data-containing portion of the file system.
	 *
	 * @return the number of bytes usable for storing user data
	 */
	private long getDataSize() {
		return (getSectorCount() * getBytesPerSector()) - getFilesOffset();
	}

	/**
	 * Gets the OEM name
	 *
	 * @return String
	 */
	public String getOemName() {
		StringBuilder b = new StringBuilder(8);

		for (int i = 0; i < 8; i++) {
			int v = get8(0x3 + i);
			if (v == 0)
				break;
			b.append((char)v);
		}

		return b.toString();
	}

	/**
	 * Gets the number of bytes/sector
	 *
	 * @return int
	 */
	public int getBytesPerSector() {
		return get16(0x0b);
	}

	/**
	 * Returns the number of bytes per cluster, which is calculated from the {@link #getSectorsPerCluster() sectors per cluster} and the {@link #getBytesPerSector() bytes per sector}.
	 *
	 * @return the number of bytes per cluster
	 */
	public int getBytesPerCluster() {
		return getSectorsPerCluster() * getBytesPerSector();
	}

	/**
	 * Gets the number of sectors/cluster
	 *
	 * @return int
	 */
	public int getSectorsPerCluster() {
		return get8(SECTORS_PER_CLUSTER_OFFSET);
	}

	/**
	 * Gets the number of reserved (for bootrecord) sectors
	 *
	 * @return int
	 */
	public int getNrReservedSectors() {
		return get16(RESERVED_SECTORS_OFFSET);
	}

	/**
	 * Gets the number of fats
	 *
	 * @return int
	 */
	public final int getNrFats() {
		return get8(FAT_COUNT_OFFSET);
	}

	/**
	 * Gets the number of logical sectors
	 *
	 * @return int
	 */
	protected int getNrLogicalSectors() {
		return get16(TOTAL_SECTORS_16_OFFSET);
	}

	protected long getNrTotalSectors() {
		return get32(TOTAL_SECTORS_32_OFFSET);
	}

	/**
	 * Gets the medium descriptor byte
	 *
	 * @return int
	 */
	public int getMediumDescriptor() {
		return get8(0x15);
	}

	/**
	 * Gets the number of sectors/track
	 *
	 * @return int
	 */
	public int getSectorsPerTrack() {
		return get16(0x18);
	}

	/**
	 * Gets the number of heads
	 *
	 * @return int
	 */
	public int getNrHeads() {
		return get16(0x1a);
	}

	/**
	 * Gets the number of hidden sectors
	 *
	 * @return int
	 */
	public long getNrHiddenSectors() {
		return get32(0x1c);
	}

	/**
	 * Returns the device offset to this {@code Sector}.
	 *
	 * @return the {@code Sector}'s device offset
	 */
	public long getOffset() {
		return offset_;
	}

	/**
	 * Gets the offset (in bytes) of the fat with the given index
	 *
	 * @param bs
	 * @param fatNr (0..)
	 * @return long
	 * @throws IOException
	 */
	public long getFatOffset(int fatNr) {
		return getNrReservedSectors() * getBytesPerSector() + fatNr * getSectorsPerFat() * getBytesPerSector() + getOffset();
	}

	/**
	 * Gets the offset (in bytes) of the root directory with the given index
	 *
	 * @param bs
	 * @return long
	 * @throws IOException
	 */
	public long getRootDirOffset() {
		return getNrFats() * getSectorsPerFat() * getBytesPerSector() + getFatOffset(0);
	}

	/**
	 * Gets the offset of the data (file) area
	 *
	 * @param bs
	 * @return long
	 * @throws IOException
	 */
	public long getFilesOffset() {
		return getRootDirOffset() + (getRootDirEntryCount() * 32);
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder(1024);
		res.append("Bootsector :").append(StringUtils.LF);
		res.append("oemName=");
		res.append(getOemName());
		res.append(StringUtils.LF);
		res.append("medium descriptor = ");
		res.append(getMediumDescriptor());
		res.append(StringUtils.LF);
		res.append("Nr heads = ");
		res.append(getNrHeads());
		res.append(StringUtils.LF);
		res.append("Sectors per track = ");
		res.append(getSectorsPerTrack());
		res.append(StringUtils.LF);
		res.append("Sector per cluster = ");
		res.append(getSectorsPerCluster());
		res.append(StringUtils.LF);
		res.append("byte per sector = ");
		res.append(getBytesPerSector());
		res.append(StringUtils.LF);
		res.append("Nr fats = ");
		res.append(getNrFats());
		res.append(StringUtils.LF);
		res.append("Nr hidden sectors = ");
		res.append(getNrHiddenSectors());
		res.append(StringUtils.LF);
		res.append("Nr logical sectors = ");
		res.append(getNrLogicalSectors());
		res.append(StringUtils.LF);
		res.append("Nr reserved sector = ");
		res.append(getNrReservedSectors());
		res.append(StringUtils.LF);

		return res.toString();
	}
}
