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
 * <p>
 * Implements the {@code FileSystem} interface for the FAT family of file systems. This class always uses the "long file name" specification when writing directory entries.
 * </p>
 *
 * @author Ewout Prangsma &lt;epr at jnode.org&gt;
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 */
public final class FatFileSystem {

	private final Fat fat_;
	private final BootSector bs_;
	private final FatLfnDirectory rootDir_;
	private final AbstractDirectory rootDirStore_;
	private final FatType fatType_;

	public FatFileSystem(BlockDevice device) throws IOException {
		bs_ = BootSector.read(device);

		if (bs_.getNrFats() <= 0)
			throw new IOException("boot sector says there are no FATs");

		fatType_ = bs_.getFatType();
		fat_ = Fat.read(bs_, 0);

		for (int i = 1; i < bs_.getNrFats(); i++)
			if (!fat_.equals(Fat.read(bs_, i)))
				System.err.println("FAT " + i + " differs from FAT 0");

		if (fatType_ == FatType.FAT32) {
			rootDirStore_ = ClusterChainDirectory.readRoot(new ClusterChain(fat_, ((Fat32BootSector)bs_).getRootDirFirstCluster()));
		} else {
			rootDirStore_ = Fat16RootDirectory.read((Fat16BootSector)bs_);
		}

		rootDir_ = new FatLfnDirectory(rootDirStore_, fat_);
	}

	public FatLfnDirectory getRoot() {
		return rootDir_;
	}
}
