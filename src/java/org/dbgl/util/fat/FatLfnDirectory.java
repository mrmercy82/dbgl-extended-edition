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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


/**
 * The {@link FsDirectory} implementation for FAT file systems. This implementation aims to fully comply to the FAT specification, including the quite complex naming system regarding the long file
 * names (LFNs) and their corresponding 8+3 short file names. This also means that an {@code FatLfnDirectory} is case-preserving but <em>not</em> case-sensitive.
 *
 * @author gbin
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 * @since 0.6
 */
public final class FatLfnDirectory {

	/**
	 * This set is used to check if a file name is already in use in this directory. The FAT specification says that file names must be unique ignoring the case, so this set contains all names
	 * converted to lower-case, and all checks must be performed using lower-case strings.
	 */
	private final Fat fat_;
	private final AbstractDirectory dir_;
	private final Set<String> usedNames_;
	private final Map<ShortName, FatLfnDirectoryEntry> shortNameIndex_;
	private final Map<String, FatLfnDirectoryEntry> longNameIndex_;
	private final Map<FatDirectoryEntry, FatLfnDirectory> entryToDirectory_;

	FatLfnDirectory(AbstractDirectory dir, Fat fat) throws IOException {
		if ((dir == null) || (fat == null))
			throw new NullPointerException();

		fat_ = fat;
		dir_ = dir;

		usedNames_ = new HashSet<>();
		shortNameIndex_ = new LinkedHashMap<>();
		longNameIndex_ = new LinkedHashMap<>();
		entryToDirectory_ = new LinkedHashMap<>();

		int i = 0;
		final int size = dir_.getEntryCount();

		while (i < size) {
			// jump over empty entries
			while (i < size && dir_.getEntry(i) == null) {
				i++;
			}

			if (i >= size) {
				break;
			}

			int offset = i; // beginning of the entry
			// check when we reach a real entry
			while (dir_.getEntry(i).isLfnEntry()) {
				i++;
				if (i >= size) {
					// This is a cutted entry, forgive it
					break;
				}
			}

			if (i >= size) {
				// This is a cutted entry, forgive it
				break;
			}

			FatLfnDirectoryEntry current = FatLfnDirectoryEntry.extract(this, offset, ++i - offset);

			if (!current.isDeleted()) {
				String name = current.getName();
				if (!usedNames_.add(name.toLowerCase()))
					throw new IOException("an entry named " + name + " already exists");

				shortNameIndex_.put(current.getShortName(), current);
				longNameIndex_.put(current.getName().toLowerCase(), current);
			}
		}
	}

	FatLfnDirectory getDirectory(FatDirectoryEntry entry) throws IOException {
		FatLfnDirectory result = entryToDirectory_.get(entry);

		if (result == null) {
			if (!entry.isDirectory())
				throw new IllegalArgumentException(entry + " is no directory");

			ClusterChainDirectory ccd = new ClusterChainDirectory(new ClusterChain(fat_, entry.getStartCluster()), false);
			ccd.read();
			result = new FatLfnDirectory(ccd, fat_);
			entryToDirectory_.put(entry, result);
		}

		return result;
	}

	public FatDirectoryEntry getEntry(int idx) {
		return dir_.getEntry(idx);
	}

	public Iterator<FatLfnDirectoryEntry> iterator() {
		return new Iterator<FatLfnDirectoryEntry>() {

			final Iterator<FatLfnDirectoryEntry> it = shortNameIndex_.values().iterator();

			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public FatLfnDirectoryEntry next() {
				return it.next();
			}

			/**
			 * @see java.util.Iterator#remove()
			 */
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [size=" + shortNameIndex_.size() + ", dir=" + dir_ + "]"; // NOI18N
	}
}
