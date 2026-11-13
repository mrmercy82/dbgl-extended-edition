/*
 *  Copyright (C) 2026  mrmercy82 (DBGL Extended Edition, written with the help of AI)
 *  Part of a modified version of DBGL, Copyright (C) 2006-2025  Ronald Blankendaal
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
package org.dbgl.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import org.dbgl.model.ImgSizeCommand;


/**
 * Alpha 48: the geometry of a hard disk image, for "-size bytes,sectors,heads,cylinders" of DOSBox 0.74 and other versions that
 * do not find it out themselves. Read from what the image says (the file is only read, it is untrusted):
 * <ul>
 * <li>a VHD (fixed): its footer, the last 512 bytes, "conectix", geometry at 0x38 (cylinders 2 bytes, heads, sectors; big
 * endian)</li>
 * <li>a raw image with a partition table (MBR, 0x55AA at 510): the heads and sectors of the end of its partitions; the
 * cylinders from the size of the file</li>
 * <li>a raw image without one, a FAT file system from its first sector: sectors per track and heads of its boot sector</li>
 * </ul>
 * PC-98 images (.HDI, .NHD) are left out: only DOSBox-X mounts them, and it reads their header itself.
 */
public final class DiskGeometry {

	/** the geometry, and where it came from: "vhd", "mbr" or "bpb" */
	public record Result(ImgSizeCommand size, String source) {
	}

	private static final int SECTOR = 512;
	/** smaller ones are floppies, DOSBox knows them by their size */
	private static final long MIN_SIZE = 4L * 1024 * 1024;

	private DiskGeometry() {
	}

	/** @return null if the image says nothing usable */
	public static Result of(File image) {
		if ((image == null) || !image.isFile() || (image.length() < MIN_SIZE) || FilesUtils.isPc98Image(image.getName()))
			return null;
		try (RandomAccessFile f = new RandomAccessFile(image, "r")) {
			long size = f.length();
			byte[] footer = new byte[SECTOR];
			f.seek(size - SECTOR);
			f.readFully(footer);
			Result vhd = vhd(footer);
			if (vhd != null)
				return vhd;
			byte[] first = new byte[SECTOR];
			f.seek(0);
			f.readFully(first);
			return raw(first, size);
		} catch (IOException e) {
			return null;
		}
	}

	static Result vhd(byte[] footer) {
		if (!new String(footer, 0, 8, java.nio.charset.StandardCharsets.ISO_8859_1).equals("conectix"))
			return null;
		int type = ((footer[0x3c] & 0xff) << 24) | ((footer[0x3d] & 0xff) << 16) | ((footer[0x3e] & 0xff) << 8) | (footer[0x3f] & 0xff);
		if (type != 2)
			return null; // dynamic or differencing: no plain sector dump, old DOSBox versions cannot use it anyway
		int cylinders = ((footer[0x38] & 0xff) << 8) | (footer[0x39] & 0xff);
		int heads = footer[0x3a] & 0xff, sectors = footer[0x3b] & 0xff;
		return plausible(sectors, heads, cylinders) ? new Result(new ImgSizeCommand(SECTOR, sectors, heads, cylinders), "vhd"): null;
	}

	static Result raw(byte[] first, long size) {
		if (((first[510] & 0xff) != 0x55) || ((first[511] & 0xff) != 0xaa))
			return null;
		// a FAT boot sector at the start (no partition table): its BPB
		boolean jump = ((first[0] & 0xff) == 0xeb) || ((first[0] & 0xff) == 0xe9);
		int bytes = (first[0x0b] & 0xff) | ((first[0x0c] & 0xff) << 8);
		if (jump && (bytes == SECTOR)) {
			int sectors = (first[0x18] & 0xff) | ((first[0x19] & 0xff) << 8), heads = (first[0x1a] & 0xff) | ((first[0x1b] & 0xff) << 8);
			if (plausible(sectors, heads, 1))
				return result(sectors, heads, size, "bpb");
		}
		int heads = 0, sectors = 0;
		for (int i = 0; i < 4; i++) {
			int o = 446 + (i * 16);
			int status = first[o] & 0xff, type = first[o + 4] & 0xff;
			if ((type == 0) || ((status != 0) && (status != 0x80)))
				continue;
			heads = Math.max(heads, (first[o + 5] & 0xff) + 1);
			sectors = Math.max(sectors, first[o + 6] & 0x3f);
		}
		return plausible(sectors, heads, 1) ? result(sectors, heads, size, "mbr"): null;
	}

	private static Result result(int sectors, int heads, long size, String source) {
		long cylinders = size / ((long)SECTOR * sectors * heads);
		return plausible(sectors, heads, cylinders) ? new Result(new ImgSizeCommand(SECTOR, sectors, heads, (int)cylinders), source): null;
	}

	private static boolean plausible(int sectors, int heads, long cylinders) {
		return (sectors >= 1) && (sectors <= 63) && (heads >= 1) && (heads <= 255) && (cylinders >= 1) && (cylinders <= 65535);
	}
}
