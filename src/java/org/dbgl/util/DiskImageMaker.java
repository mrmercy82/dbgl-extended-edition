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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.ImgSizeCommand;


/**
 * Alpha 48: an empty, formatted disk image for DOSBox (like MAKEIMG of DOSBox Staging 0.83; DOSBox has no FORMAT, an image
 * that is not formatted would be of no use): a floppy with FAT12, or a hard disk with a partition table and one FAT16
 * partition (geometry 16 heads, 63 sectors, as DOSBox expects without -size). No boot code: DOSBox mounts it, it does not boot.
 */
public final class DiskImageMaker {

	private static final int SECTOR = 512;
	public static final int MIN_HD_MB = 32, MAX_HD_MB = 2047;

	/** the usual PC floppies: size in KB, sectors per cluster, root entries, media byte, sectors per FAT, per track, heads */
	public enum Floppy {
		K360(360, 2, 112, 0xfd, 2, 9, 2), K720(720, 2, 112, 0xf9, 3, 9, 2), M12(1200, 1, 224, 0xf9, 7, 15, 2), M144(1440, 1, 224, 0xf0, 9, 18, 2), M288(2880, 2, 240, 0xf0, 9, 36, 2);

		final int kb, spc, rootEntries, media, fatSectors, spt, heads;

		Floppy(int kb, int spc, int rootEntries, int media, int fatSectors, int spt, int heads) {
			this.kb = kb;
			this.spc = spc;
			this.rootEntries = rootEntries;
			this.media = media;
			this.fatSectors = fatSectors;
			this.spt = spt;
			this.heads = heads;
		}

		public int kb() {
			return kb;
		}
	}

	private DiskImageMaker() {
	}

	/** a volume label as DOS writes it: upper case, 11 characters, padded with spaces (empty: "NO NAME") */
	static byte[] label(String label) {
		String l = StringUtils.isBlank(label) ? "NO NAME": label.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 _\\-!#$%&'()@^`{}~]", "_");
		return String.format("%-11s", l.length() > 11 ? l.substring(0, 11): l).getBytes(StandardCharsets.US_ASCII);
	}

	public static void floppy(File file, Floppy t, String label) throws IOException {
		int total = t.kb * 2;
		try (RandomAccessFile f = create(file, (long)total * SECTOR)) {
			ByteBuffer bs = bootSector(t.spc, t.rootEntries, total, 0, t.media, t.fatSectors, t.spt, t.heads, 0, 0x00, label, "FAT12   ");
			write(f, 0, bs);
			for (int i = 0; i < 2; i++)
				write(f, (1L + ((long)i * t.fatSectors)) * SECTOR, ByteBuffer.wrap(new byte[] {(byte)t.media, (byte)0xff, (byte)0xff}));
			writeLabelEntry(f, (1L + (2L * t.fatSectors)) * SECTOR, label);
		}
	}

	/** the cylinders of a hard disk image of this size, with 16 heads and 63 sectors */
	static int cylinders(int megabytes) {
		return (int)(((long)megabytes * 1024 * 1024) / (SECTOR * 16L * 63));
	}

	/** for "-size" of DOSBox 0.74 and others: bytes per sector, sectors, heads, cylinders */
	public static ImgSizeCommand geometry(int megabytes) {
		return new ImgSizeCommand(SECTOR, 63, 16, cylinders(megabytes));
	}

	public static void hardDisk(File file, int megabytes, String label) throws IOException {
		if ((megabytes < MIN_HD_MB) || (megabytes > MAX_HD_MB))
			throw new IOException("size " + megabytes + " MB: " + MIN_HD_MB + " to " + MAX_HD_MB);
		int cyl = cylinders(megabytes);
		long disk = (long)cyl * 16 * 63;
		long part = disk - 63; // the partition from the second track on, as DOS did
		// the smallest cluster that keeps FAT16 below 65525 clusters
		int spc = 0, fatSz = 0;
		for (int s = 2; s <= 64; s *= 2) {
			int fs = 1;
			long clusters;
			while (true) {
				clusters = (part - 1 - 32 - (2L * fs)) / s;
				int need = (int)(((clusters + 2) * 2 + SECTOR - 1) / SECTOR);
				if (need <= fs)
					break;
				fs = need;
			}
			if (clusters <= 65524) {
				spc = s;
				fatSz = fs;
				break;
			}
		}
		if (spc == 0)
			throw new IOException("too big for FAT16");
		try (RandomAccessFile f = create(file, disk * SECTOR)) {
			ByteBuffer mbr = ByteBuffer.allocate(SECTOR).order(ByteOrder.LITTLE_ENDIAN);
			mbr.put(446, (byte)0x80); // active
			chs(mbr, 447, 0, 1, 1);
			mbr.put(450, (byte)(part < 65536 ? 0x04: 0x06)); // FAT16 below 32 MB, FAT16B
			chs(mbr, 451, cyl - 1, 15, 63);
			mbr.putInt(454, 63);
			mbr.putInt(458, (int)part);
			mbr.put(510, (byte)0x55);
			mbr.put(511, (byte)0xaa);
			write(f, 0, mbr);
			long base = 63L * SECTOR;
			write(f, base, bootSector(spc, 512, part < 65536 ? (int)part: 0, part < 65536 ? 0: part, 0xf8, fatSz, 63, 16, 63, 0x80, label, "FAT16   "));
			for (int i = 0; i < 2; i++)
				write(f, base + ((1L + ((long)i * fatSz)) * SECTOR), ByteBuffer.wrap(new byte[] {(byte)0xf8, (byte)0xff, (byte)0xff, (byte)0xff}));
			writeLabelEntry(f, base + ((1L + (2L * fatSz)) * SECTOR), label);
		}
	}

	private static void chs(ByteBuffer b, int at, int cyl, int head, int sector) {
		int c = Math.min(cyl, 1023);
		b.put(at, (byte)head);
		b.put(at + 1, (byte)((sector & 0x3f) | ((c >> 2) & 0xc0)));
		b.put(at + 2, (byte)(c & 0xff));
	}

	private static ByteBuffer bootSector(int spc, int rootEntries, int total16, long total32, int media, int fatSz, int spt, int heads, long hidden, int drive, String label,
			String fsType) {
		ByteBuffer b = ByteBuffer.allocate(SECTOR).order(ByteOrder.LITTLE_ENDIAN);
		b.put(0, (byte)0xeb).put(1, (byte)0x3c).put(2, (byte)0x90);
		b.put(3, "MSDOS5.0".getBytes(StandardCharsets.US_ASCII));
		b.putShort(0x0b, (short)SECTOR);
		b.put(0x0d, (byte)spc);
		b.putShort(0x0e, (short)1);
		b.put(0x10, (byte)2);
		b.putShort(0x11, (short)rootEntries);
		b.putShort(0x13, (short)total16);
		b.put(0x15, (byte)media);
		b.putShort(0x16, (short)fatSz);
		b.putShort(0x18, (short)spt);
		b.putShort(0x1a, (short)heads);
		b.putInt(0x1c, (int)hidden);
		b.putInt(0x20, (int)total32);
		b.put(0x24, (byte)drive);
		b.put(0x26, (byte)0x29);
		b.putInt(0x27, (int)(System.currentTimeMillis() & 0x7fffffff));
		b.put(0x2b, label(label));
		b.put(0x36, fsType.getBytes(StandardCharsets.US_ASCII));
		b.put(0x3e, (byte)0xcd).put(0x3f, (byte)0x18); // no system: INT 18h, as an unbootable disk does
		b.put(510, (byte)0x55);
		b.put(511, (byte)0xaa);
		return b;
	}

	private static void writeLabelEntry(RandomAccessFile f, long at, String label) throws IOException {
		if (StringUtils.isBlank(label))
			return;
		ByteBuffer e = ByteBuffer.allocate(32);
		e.put(0, label(label));
		e.put(11, (byte)0x08); // volume label
		write(f, at, e);
	}

	private static RandomAccessFile create(File file, long size) throws IOException {
		if (file.exists())
			throw new IOException(file.getPath() + ": exists already");
		java.nio.file.Files.createDirectories(file.getAbsoluteFile().getParentFile().toPath());
		RandomAccessFile f = new RandomAccessFile(file, "rw");
		f.setLength(size);
		return f;
	}

	private static void write(RandomAccessFile f, long at, ByteBuffer b) throws IOException {
		f.seek(at);
		f.write(b.array(), 0, b.limit());
	}
}
