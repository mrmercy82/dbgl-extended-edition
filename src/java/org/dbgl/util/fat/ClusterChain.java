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

import java.io.EOFException;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.FileSystemException;


/**
 * A chain of clusters as stored in a {@link Fat}.
 *
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 */
final class ClusterChain {

	private final Fat fat_;
	private final BlockDevice device_;
	private final int clusterSize_;
	private final long dataOffset_;
	private final long startCluster_;

	public ClusterChain(Fat fat, long startCluster) {
		fat_ = fat;

		if (startCluster != 0) {
			fat_.testCluster(startCluster);
			if (fat_.isFreeCluster(startCluster))
				throw new IllegalArgumentException("cluster " + startCluster + " is free");
		}

		device_ = fat.getDevice();
		dataOffset_ = fat.getBootSector().getFilesOffset();
		startCluster_ = startCluster;
		clusterSize_ = fat.getBootSector().getBytesPerCluster();
	}

	public Fat getFat() {
		return fat_;
	}

	public BlockDevice getDevice() {
		return device_;
	}

	/**
	 * Returns the first cluster of this chain.
	 *
	 * @return the chain's first cluster, which may be 0 if this chain does not contain any clusters
	 */
	public long getStartCluster() {
		return startCluster_;
	}

	/**
	 * Calculates the device offset (0-based) for the given cluster and offset within the cluster.
	 *
	 * @param cluster
	 * @param clusterOffset
	 * @return long
	 * @throws FileSystemException
	 */
	private long getDevOffset(long cluster, int clusterOffset) {
		return dataOffset_ + clusterOffset + ((cluster - Fat.FIRST_CLUSTER) * clusterSize_);
	}

	/**
	 * Returns the size this {@code ClusterChain} occupies on the device.
	 *
	 * @return the size this chain occupies on the device in bytes
	 */
	public long getLengthOnDisk() {
		if (getStartCluster() == 0)
			return 0;

		return getChainLength() * (long)clusterSize_;
	}

	/**
	 * Determines the length of this {@code ClusterChain} in clusters.
	 *
	 * @return the length of this chain
	 */
	public int getChainLength() {
		if (getStartCluster() == 0)
			return 0;

		return getFat().getChain(getStartCluster()).length;
	}

	public void readData(long offset, ByteBuffer dest) throws IOException {
		int len = dest.remaining();

		if ((startCluster_ == 0 && len > 0))
			throw new EOFException();

		long[] chain = getFat().getChain(startCluster_);
		BlockDevice dev = getDevice();

		int chainIdx = (int)(offset / clusterSize_);
		if (offset % clusterSize_ != 0) {
			int clusOfs = (int)(offset % clusterSize_);
			int size = Math.min(len, (int)(clusterSize_ - (offset % clusterSize_) - 1));
			((Buffer)dest).limit(dest.position() + size);

			dev.read(getDevOffset(chain[chainIdx], clusOfs), dest);

			len -= size;
			chainIdx++;
		}

		while (len > 0) {
			int size = Math.min(clusterSize_, len);
			((Buffer)dest).limit(dest.position() + size);

			dev.read(getDevOffset(chain[chainIdx], 0), dest);

			len -= size;
			chainIdx++;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (!(obj instanceof ClusterChain))
			return false;

		ClusterChain other = (ClusterChain)obj;

		if (fat_ != other.fat_ && (fat_ == null || !fat_.equals(other.fat_)))
			return false;

		return startCluster_ == other.startCluster_;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 79 * hash + (fat_ != null ? fat_.hashCode(): 0);
		hash = 79 * hash + (int)(startCluster_ ^ (startCluster_ >>> 32));
		return hash;
	}
}
