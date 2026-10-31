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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/**
 * PC-98 hard disk images in the .HDI format (Anex86, used by Neko Project II and DOSBox-X).
 *
 * Layout, as DOSBox-X reads it (bios_disk.cpp, drive_fat.cpp):
 * <ul>
 * <li>a header, usually 4096 bytes, with header size, disk size, sector size and C/H/S geometry (little endian, 32-bit):
 * +0x08 header size, +0x0C disk size incl. header, +0x10 bytes per sector, +0x14 sectors, +0x18 heads, +0x1C cylinders</li>
 * <li>then a plain sector dump of the disk. Sector 0 holds the IPL with "IPL1" at offset 4, sector 1 the PC-98 partition
 * table: up to 16 entries of 32 bytes, with start and end as C/H/S, sectors counted from 0</li>
 * <li>each partition is a FAT file system; its boot sector often says 1024 bytes per sector while the disk has 256 or 512</li>
 * </ul>
 * Images without the "IPL1" signature are read like other hard disk images (IBM PC partition table, or none).
 */
public final class Pc98HdiImage {

	public static final int HEADER_SIZE = 0x20;
	private static final int ENTRY_SIZE = 32;

	/** an entry of the PC-98 partition table */
	public record Partition(int mid, int sid, int sector, int head, int cylinder, int endCylinder, String name, long byteOffset) {
	}

	/** what the header and the partition table say */
	public record Info(long base, int sectorSize, int sectors, int heads, int cylinders, boolean ipl1, List<Partition> partitions) {
	}

	private Pc98HdiImage() {
	}

	/** a part of another device, e.g. one partition */
	static final class PartitionDisk implements BlockDevice {
		private final BlockDevice device_;
		private final long offset_;
		private final long size_;
		private final boolean partition_;

		PartitionDisk(BlockDevice device, long offset, boolean partition) throws IOException {
			device_ = device;
			offset_ = offset;
			size_ = device.getSize() - offset;
			partition_ = partition;
		}

		@Override
		public long getSize() {
			return size_;
		}

		@Override
		public void read(long devOffset, ByteBuffer dest) throws IOException {
			device_.read(offset_ + devOffset, dest);
		}

		@Override
		public int getSectorSize() throws IOException {
			return device_.getSectorSize();
		}

		@Override
		public boolean isPartition() {
			return partition_;
		}

		@Override
		public void close() throws IOException {
			device_.close();
		}

		@Override
		public boolean isClosed() {
			return device_.isClosed();
		}
	}

	private static ByteBuffer read(BlockDevice device, long offset, int length) throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(length);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		device.read(offset, bb);
		bb.flip();
		return bb;
	}

	/** reads header and partition table */
	public static Info readInfo(BlockDevice device) throws IOException {
		long size = device.getSize();
		long base = 0;
		int sectorSize = 512, sectors = 0, heads = 0, cylinders = 0;
		if (size >= HEADER_SIZE) {
			ByteBuffer h = read(device, 0, HEADER_SIZE);
			long headerSize = h.getInt(0x08) & 0xffffffffL;
			long hddSize = h.getInt(0x0c) & 0xffffffffL;
			int ss = h.getInt(0x10);
			// the same plausibility checks as DOSBox-X
			if ((ss >= 256) && (ss <= 1024) && ((ss & (ss - 1)) == 0) && (headerSize != 0) && ((headerSize % ss) == 0) && ((headerSize % 1024) == 0) && (hddSize >= ss)
					&& ((hddSize / 1024) <= ((size / 1024) + 4)) && (headerSize < size)) {
				base = headerSize;
				sectorSize = ss;
				sectors = h.getInt(0x14);
				heads = h.getInt(0x18);
				cylinders = h.getInt(0x1c);
			}
		}

		List<Partition> partitions = new ArrayList<>();
		boolean ipl1 = false;
		if (size >= base + 2L * sectorSize) {
			ByteBuffer ipl = read(device, base, sectorSize);
			ipl1 = (ipl.get(4) == 'I') && (ipl.get(5) == 'P') && (ipl.get(6) == 'L') && (ipl.get(7) == '1');
			if (ipl1) {
				ByteBuffer table = read(device, base + sectorSize, sectorSize);
				int entries = Math.min(16, sectorSize / ENTRY_SIZE);
				for (int i = 0; i < entries; i++) {
					int o = i * ENTRY_SIZE;
					int mid = table.get(o) & 0xff;
					int sid = table.get(o + 1) & 0xff;
					int sector = table.get(o + 8) & 0xff;
					int head = table.get(o + 9) & 0xff;
					int cyl = table.getShort(o + 10) & 0xffff;
					int endCyl = table.getShort(o + 14) & 0xffff;
					boolean unused = true;
					for (int j = 0; j < 16; j++)
						unused &= table.get(o + j) == 0;
					if (unused)
						continue;
					byte[] name = new byte[16];
					table.get(o + 16, name);
					long start = ((long)cyl * sectors * heads) + ((long)head * sectors) + sector;
					partitions.add(new Partition(mid, sid, sector, head, cyl, endCyl, new String(name, StandardCharsets.ISO_8859_1).trim(), base + start * sectorSize));
				}
			}
		}
		return new Info(base, sectorSize, sectors, heads, cylinders, ipl1, partitions);
	}

	/** the first MS-DOS partition, chosen like DOSBox-X does, or null */
	public static Partition chooseFatPartition(Info info) {
		for (Partition p: info.partitions()) {
			String name = p.name().toUpperCase();
			boolean dosType = (p.mid() == 0x20) || ((p.mid() >= 0xa1) && (p.mid() <= 0xaf));
			boolean active = (p.sid() == 0x81) || (p.sid() == 0x91) || (p.sid() == 0xa1) || (p.sid() == 0xe1);
			if (name.startsWith("MS-DOS") || name.startsWith("MSDOS") || name.startsWith("WINDOWS") || (dosType && active && ((info.cylinders() == 0) || (p.endCylinder() <= info.cylinders()))))
				return p;
		}
		return null;
	}

	private static boolean looksLikeFatBootSector(BlockDevice device, long offset) throws IOException {
		if (device.getSize() < offset + 512)
			return false;
		ByteBuffer bs = read(device, offset, 512);
		int bytesPerSector = bs.getShort(0x0b) & 0xffff;
		int sectorsPerCluster = bs.get(0x0d) & 0xff;
		int fats = bs.get(0x10) & 0xff;
		return (bytesPerSector >= 256) && (bytesPerSector <= 4096) && ((bytesPerSector & (bytesPerSector - 1)) == 0) && (sectorsPerCluster > 0)
				&& ((sectorsPerCluster & (sectorsPerCluster - 1)) == 0) && (fats >= 1) && (fats <= 2);
	}

	/** @return a device for the FAT file system in the image (the first MS-DOS partition of a PC-98 disk) */
	public static BlockDevice open(File file) throws IOException {
		FileDisk disk = new FileDisk(file);
		try {
			Info info = readInfo(disk);
			if (info.ipl1()) {
				Partition p = chooseFatPartition(info);
				if (p == null)
					throw new IOException("no MS-DOS partition found in " + file);
				return new PartitionDisk(disk, p.byteOffset(), true);
			}
			// no PC-98 partition table: a FAT boot sector right at the start, or an IBM PC partition table
			return new PartitionDisk(disk, info.base(), looksLikeFatBootSector(disk, info.base()));
		} catch (IOException | RuntimeException e) {
			disk.close();
			throw e;
		}
	}
}
