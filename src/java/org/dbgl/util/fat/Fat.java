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
import java.util.Arrays;


/**
 *
 *
 * @author Ewout Prangsma &lt;epr at jnode.org&gt;
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 */
final class Fat {

	/**
	 * The first cluster that really holds user data in a FAT.
	 */
	public static final int FIRST_CLUSTER = 2;

	private final long[] entries_;
	private final FatType fatType_;
	private final int sectorCount_;
	private final int sectorSize_;
	private final BlockDevice device_;
	private final BootSector bs_;
	private final long offset_;
	private final int lastClusterIndex_;

	/**
	 * Reads a {@code Fat} as specified by a {@code BootSector}.
	 *
	 * @param bs    the boot sector specifying the {@code Fat} layout
	 * @param fatNr the number of the {@code Fat} to read
	 * @return the {@code Fat} that was read
	 * @throws IOException              on read error
	 * @throws IllegalArgumentException if {@code fatNr} is greater than {@link BootSector#getNrFats()}
	 */
	public static Fat read(BootSector bs, int fatNr) throws IOException, IllegalArgumentException {
		if (fatNr > bs.getNrFats()) {
			throw new IllegalArgumentException("boot sector says there are only " + bs.getNrFats() + " FATs when reading FAT #" + fatNr);
		}

		Fat result = new Fat(bs, bs.getFatOffset(fatNr));
		result.read();
		return result;
	}

	private Fat(BootSector bs, long offset) throws IOException {
		bs_ = bs;
		fatType_ = bs.getFatType();
		if (bs.getSectorsPerFat() > Integer.MAX_VALUE)
			throw new IllegalArgumentException("FAT too large");

		if (bs.getSectorsPerFat() <= 0)
			throw new IOException("boot sector says there are " + bs.getSectorsPerFat() + " sectors per FAT");

		if (bs.getBytesPerSector() <= 0)
			throw new IOException("boot sector says there are " + bs.getBytesPerSector() + " bytes per sector");

		sectorCount_ = (int)bs.getSectorsPerFat();
		sectorSize_ = bs.getBytesPerSector();
		device_ = bs.getDevice();
		offset_ = offset;

		if (bs.getDataClusterCount() > Integer.MAX_VALUE)
			throw new IOException("too many data clusters");

		if (bs.getDataClusterCount() == 0)
			throw new IOException("no data clusters");

		lastClusterIndex_ = (int)bs.getDataClusterCount() + FIRST_CLUSTER;

		entries_ = new long[(int)((sectorCount_ * sectorSize_) / fatType_.getEntrySize())];

		if (lastClusterIndex_ > entries_.length)
			throw new IOException("file system has " + lastClusterIndex_ + "clusters but only " + entries_.length + " FAT entries");
	}

	/**
	 * Returns the {@code BootSector} that specifies this {@code Fat}.
	 *
	 * @return this {@code Fat}'s {@code BootSector}
	 */
	public BootSector getBootSector() {
		return bs_;
	}

	/**
	 * Returns the {@code BlockDevice} where this {@code Fat} is stored.
	 *
	 * @return the device holding this FAT
	 */
	public BlockDevice getDevice() {
		return device_;
	}

	/**
	 * Read the contents of this FAT from the given device at the given offset.
	 *
	 * @param offset_ the byte offset where to read the FAT from the device
	 * @throws IOException on read error
	 */
	private void read() throws IOException {
		byte[] data = new byte[sectorCount_ * sectorSize_];
		device_.read(offset_, ByteBuffer.wrap(data));

		for (int i = 0; i < entries_.length; i++)
			entries_[i] = fatType_.readEntry(data, i);
	}

	/**
	 * Gets the medium descriptor byte
	 *
	 * @return int
	 */
	public int getMediumDescriptor() {
		return (int)(entries_[0] & 0xFF);
	}

	public long[] getChain(long startCluster) {
		testCluster(startCluster);
		// Count the chain first
		int count = 1;
		long cluster = startCluster;
		while (!isEofCluster(entries_[(int)cluster])) {
			count++;
			cluster = entries_[(int)cluster];
		}
		// Now create the chain
		long[] chain = new long[count];
		chain[0] = startCluster;
		cluster = startCluster;
		int i = 0;
		while (!isEofCluster(entries_[(int)cluster])) {
			cluster = entries_[(int)cluster];
			chain[++i] = cluster;
		}
		return chain;
	}

	/**
	 * Returns the number of clusters that are currently not in use by this FAT. This estimate does only account for clusters that are really available in the data portion of the file system, not for
	 * clusters that might only theoretically be stored in the {@code Fat}.
	 *
	 * @return the free cluster count
	 * @see FsInfoSector#setFreeClusterCount(long)
	 * @see FsInfoSector#getFreeClusterCount()
	 * @see BootSector#getDataClusterCount()
	 */
	public int getFreeClusterCount() {
		int result = 0;

		for (int i = FIRST_CLUSTER; i < lastClusterIndex_; i++) {
			if (isFreeCluster(i))
				result++;
		}

		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Fat))
			return false;

		Fat other = (Fat)obj;
		if (fatType_ != other.fatType_)
			return false;
		if (sectorCount_ != other.sectorCount_)
			return false;
		if (sectorSize_ != other.sectorSize_)
			return false;
		if (lastClusterIndex_ != other.lastClusterIndex_)
			return false;
		if (!Arrays.equals(entries_, other.entries_))
			return false;
		return getMediumDescriptor() == other.getMediumDescriptor();
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 23 * hash + Arrays.hashCode(entries_);
		hash = 23 * hash + fatType_.hashCode();
		hash = 23 * hash + sectorCount_;
		hash = 23 * hash + sectorSize_;
		hash = 23 * hash + lastClusterIndex_;
		return hash;
	}

	/**
	 * Is the given entry a free cluster?
	 *
	 * @param entry
	 * @return boolean
	 */
	protected boolean isFreeCluster(long entry) {
		if (entry > Integer.MAX_VALUE)
			throw new IllegalArgumentException();
		return (entries_[(int)entry] == 0);
	}

	/**
	 * Is the given entry an EOF marker
	 *
	 * @param entry
	 * @return boolean
	 */
	protected boolean isEofCluster(long entry) {
		return fatType_.isEofCluster(entry);
	}

	protected void testCluster(long cluster) throws IllegalArgumentException {
		if ((cluster < FIRST_CLUSTER) || (cluster >= entries_.length)) {
			throw new IllegalArgumentException("invalid cluster value " + cluster);
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName());
		sb.append("[type=");
		sb.append(fatType_);
		sb.append(", mediumDescriptor=0x");
		sb.append(Integer.toHexString(getMediumDescriptor()));
		sb.append(", sectorCount=");
		sb.append(sectorCount_);
		sb.append(", sectorSize=");
		sb.append(sectorSize_);
		sb.append(", freeClusters=");
		sb.append(getFreeClusterCount());
		sb.append("]");
		return sb.toString();
	}
}
