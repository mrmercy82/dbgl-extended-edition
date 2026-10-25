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
package org.dbgl.util.iso;

import java.io.IOException;


public class ISO9660VolumeDescriptorSet {

	private static final int TYPE_PRIMARY_DESCRIPTOR = 1;
	private static final int TYPE_SUPPLEMENTARY_DESCRIPTOR = 2;
	private static final int TYPE_TERMINATOR = 255;

	private final ISO9660FileSystem fileSystem_;

	private ISO9660FileEntry rootDirectoryEntry_;
	private String encoding_ = ISO9660FileSystem.DEFAULT_ENCODING;
	private boolean hasPrimary_ = false;
	private boolean hasSupplementary_ = false;

	public ISO9660VolumeDescriptorSet(ISO9660FileSystem fileSystem) {
		fileSystem_ = fileSystem;
	}

	public ISO9660FileEntry getRootEntry() {
		return rootDirectoryEntry_;
	}

	public String getEncoding() {
		return encoding_;
	}

	/**
	 * Load a volume descriptor from the specified byte array.
	 *
	 * @param volumeDescriptor the volume descriptor to deserialize
	 * @return true if the volume descriptor is a terminator
	 * @throws IOException if there is an error deserializing the volume descriptor
	 */
	public boolean deserialize(byte[] descriptor) throws IOException {
		boolean terminator = false;
		switch (Util.getUInt8(descriptor, 0)) {
			case TYPE_TERMINATOR:
				if (!hasPrimary_) {
					throw new IOException("No primary volume descriptor found");
				}
				terminator = true;
				break;
			case TYPE_PRIMARY_DESCRIPTOR:
				deserializePrimary(descriptor);
				break;
			case TYPE_SUPPLEMENTARY_DESCRIPTOR:
				deserializeSupplementary(descriptor);
				break;
			default:
		}
		return terminator;
	}

	private void deserializePrimary(byte[] descriptor) throws IOException {
		// some ISO 9660 file systems can contain multiple identical primary volume descriptors
		if (hasPrimary_) {
			return;
		}
		if (!Util.getDChars(descriptor, 2, 5, fileSystem_.getEncoding()).equals("CD001") || Util.getUInt8(descriptor, 6) != 1) {
			throw new IOException("Invalid primary volume descriptor");
		}
		validateBlockSize(descriptor);
		if (!hasSupplementary_) {
			deserializeCommon(descriptor);
		}
		hasPrimary_ = true;
	}

	private void deserializeCommon(byte[] descriptor) {
		rootDirectoryEntry_ = new ISO9660FileEntry(fileSystem_, descriptor, 157);
	}

	private void deserializeSupplementary(byte[] descriptor) throws IOException {
		if (hasSupplementary_) {
			return;
		}
		validateBlockSize(descriptor);
		String enc = getEncoding(Util.getDChars(descriptor, 89, 32));
		if (enc != null) {
			encoding_ = enc;
			deserializeCommon(descriptor);
			hasSupplementary_ = true;
		}
	}

	private static void validateBlockSize(byte[] descriptor) throws IOException {
		int blockSize = Util.getUInt16(descriptor, 128);
		if (blockSize != ISO9660FileSystem.COOKED_SECTOR_SIZE) {
			throw new IOException("Invalid block size: " + blockSize);
		}
	}

	private static String getEncoding(String escapeSequences) {
		if (escapeSequences.equals("%/@") || escapeSequences.equals("%/C") || escapeSequences.equals("%/E")) {
			return "UTF-16BE"; // UCS-2 level 1, 2, or 3
		}
		return null;
	}
}