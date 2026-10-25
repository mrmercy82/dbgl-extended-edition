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
package org.dbgl.model;

import org.apache.commons.lang3.StringUtils;


public class ImgSizeCommand {

	private static final int DEFAULT_BYTES_PER_SECTOR = 512;
	private static final int DEFAULT_SECTORS_PER_TRACK = 63;
	private static final int DEFAULT_HEADS = 16;
	private static final int DEFAULT_CYLINDERS = 142;

	private final int bytesPerSector_, sectorsPerTrack_, heads_, cylinders_;

	public ImgSizeCommand(String command) {
		if (command != null) {
			String[] elements = StringUtils.split(command, ',');
			if (elements.length == 4) {
				bytesPerSector_ = tryParse(elements[0]);
				sectorsPerTrack_ = tryParse(elements[1]);
				heads_ = tryParse(elements[2]);
				cylinders_ = tryParse(elements[3]);
				return;
			}
		}

		bytesPerSector_ = DEFAULT_BYTES_PER_SECTOR;
		sectorsPerTrack_ = DEFAULT_SECTORS_PER_TRACK;
		heads_ = DEFAULT_HEADS;
		cylinders_ = DEFAULT_CYLINDERS;
	}

	public ImgSizeCommand(int bytesPerSector, int sectorsPerTrack, int heads, int cylinders) {
		bytesPerSector_ = bytesPerSector;
		sectorsPerTrack_ = sectorsPerTrack;
		heads_ = heads;
		cylinders_ = cylinders;
	}

	private static int tryParse(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException nfe) {
			return 0;
		}
	}

	public int getBytesPerSector() {
		return bytesPerSector_;
	}

	public int getSectorsPerTrack() {
		return sectorsPerTrack_;
	}

	public int getHeads() {
		return heads_;
	}

	public int getCylinders() {
		return cylinders_;
	}

	public long getTotalSize() {
		return bytesPerSector_ * sectorsPerTrack_ * heads_ * (long)cylinders_;
	}

	public long getTotalSizeInMB() {
		return getTotalSize() / (1024 * 1024);
	}

	@Override
	public String toString() {
		return bytesPerSector_ + "," + sectorsPerTrack_ + "," + heads_ + "," + cylinders_;
	}
}
