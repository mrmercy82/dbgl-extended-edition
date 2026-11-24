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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.conf.mount.ImageMount;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.util.ProfileChecks.Problem;
import org.dbgl.util.fat.BlockDevice;
import org.dbgl.util.fat.FatFileSystem;
import org.dbgl.util.fat.FatLfnDirectory;
import org.dbgl.util.fat.FatLfnDirectoryEntry;
import org.dbgl.util.fat.FileDisk;


/**
 * Alpha 53: Windows 95/98 in the hard disk image a profile boots ("imgmount 2 ...img", "boot -l C"), read through the FAT
 * reader of DBGL without changing anything: the shortcuts (.LNK) of its Start menu, those of its startup folder, and "run=" and
 * "load=" of its WIN.INI. A shortcut that points to a missing program on drive C: is told as well.
 */
public final class Win9x {

	/** a shortcut: its name (the file name without .LNK), the folder it is in, and the program it starts ("C:\...\X.EXE") */
	public record Shortcut(String name, String folder, String target, boolean startup) {
	}

	/** what was found in the image */
	public record Report(File image, List<Shortcut> shortcuts, List<String> run) {
	}

	/** the names of the startup folder in the languages of Windows */
	static final Set<String> STARTUP = Set.of("autostart", "startup", "démarrage", "inicio", "esecuzione automatica", "opstarten", "uppstart", "käynnistys", "autostart-gruppe",
		"autorun", "автозагрузка");
	/** folders of Windows with shortcuts that are no programs (recent documents, the network, "Send To") or that are big */
	static final Set<String> SKIP = Set.of("system", "system32", "fonts", "inf", "help", "temp", "command", "sysbckup", "cursors", "media", "recent", "zuletzt verwendet", "sendto",
		"senden an", "nethood", "netzwerkumgebung", "favorites", "favoriten", "history", "verlauf", "cookies", "temporary internet files", "pif", "options", "applog", "catroot",
		"java", "driver cache", "spool", "msapps", "twain_32", "web", "shellnew");
	/** parts of a target that are programs of Windows itself */
	static final String[] WINDOWS_OWN = {"\\zubeh", "\\accessories", "\\internet explorer", "\\windows messaging", "\\microsoft exchange", "\\online services", "\\online-dienste",
		"\\outlook express", "\\netmeeting", "\\windows media player", "\\plus!"};

	private Win9x() {
	}

	/** the hard disk images of a profile that boots (the files of "imgmount ... -t hdd", or of drive 2, 3 or C) */
	public static List<File> hddImages(List<Mount> mounts) {
		List<File> result = new ArrayList<>();
		for (Mount m: mounts) {
			if (!(m instanceof ImageMount im) || m.isUnmounted())
				continue;
			String as = StringUtils.defaultString(m.getMountAs()).toLowerCase(Locale.ROOT);
			char d = m.getDrive();
			if (as.equals("hdd") || (d == '2') || (d == '3') || (Character.toUpperCase(d) == 'C'))
				for (File f: im.getCanonicalImgPaths())
					if (f.isFile() && FilesUtils.isFatImage(f.getName()))
						result.add(f);
		}
		return result;
	}

	/** Windows 95/98 in this image: its shortcuts and WIN.INI; null if the image has no Windows 9x (no WINDOWS\SYSTEM\VMM32.VXD) */
	public static Report read(File image) {
		try (Closer device = new Closer(new FileDisk(image))) {
			FatLfnDirectory root = new FatFileSystem(device.device_).getRoot();
			String winDir = windowsDir(root);
			FatLfnDirectoryEntry win = find(root, winDir);
			if ((win == null) || !win.isDirectory())
				return null;
			FatLfnDirectoryEntry system = find(win.getDirectory(), "SYSTEM");
			if ((system == null) || !system.isDirectory() || (find(system.getDirectory(), "VMM32.VXD") == null))
				return null;
			List<Shortcut> shortcuts = new ArrayList<>();
			collect(win.getDirectory(), "", 0, false, shortcuts);
			List<String> run = new ArrayList<>();
			FatLfnDirectoryEntry winIni = find(win.getDirectory(), "WIN.INI");
			if ((winIni != null) && winIni.isFile()) {
				String text = new String(winIni.read(256 * 1024), Charset.forName("windows-1252"));
				String section = "";
				for (String raw: text.split("\\R")) {
					String l = raw.strip();
					if (l.startsWith("[") && l.endsWith("]"))
						section = l.substring(1, l.length() - 1).strip().toLowerCase(Locale.ROOT);
					else if (section.equals("windows") && (l.toLowerCase(Locale.ROOT).startsWith("run=") || l.toLowerCase(Locale.ROOT).startsWith("load=")))
						for (String s: l.substring(l.indexOf('=') + 1).split("[\\s,]+"))
							if (!s.isBlank())
								run.add(s);
				}
			}
			return new Report(image, shortcuts, run);
		} catch (IOException | RuntimeException e) {
			return null; // no FAT, damaged, or a partition DBGL cannot read: nothing to tell
		}
	}

	/** closes the device of an image */
	private static final class Closer implements AutoCloseable {
		private final BlockDevice device_;

		Closer(BlockDevice device) {
			device_ = device;
		}

		@Override
		public void close() throws IOException {
			device_.close();
		}
	}

	/** the folder of Windows: "WinDir=" in [Paths] of MSDOS.SYS (C:\WINDOWS when it is not set) */
	static String windowsDir(FatLfnDirectory root) throws IOException {
		FatLfnDirectoryEntry msdos = find(root, "MSDOS.SYS");
		if ((msdos != null) && msdos.isFile() && (msdos.getLength() < 64 * 1024)) {
			for (String raw: new String(msdos.read(64 * 1024), Charset.forName("windows-1252")).split("\\R")) {
				String l = raw.strip();
				if (l.toLowerCase(Locale.ROOT).startsWith("windir=")) {
					String dir = l.substring(7).strip();
					if ((dir.length() > 3) && (dir.charAt(1) == ':'))
						return dir.substring(3);
				}
			}
		}
		return "WINDOWS";
	}

	/** an entry of a directory, its long or short name in any case */
	static FatLfnDirectoryEntry find(FatLfnDirectory dir, String name) {
		for (Iterator<FatLfnDirectoryEntry> it = dir.iterator(); it.hasNext();) {
			FatLfnDirectoryEntry e = it.next();
			if (e.isDeleted())
				continue;
			if (e.getName().equalsIgnoreCase(name) || shortName(e).equalsIgnoreCase(name))
				return e;
		}
		return null;
	}

	static String shortName(FatLfnDirectoryEntry e) {
		String n = e.getShortNameAsString();
		return n.length() > 8 ? (n.substring(0, 8).trim() + '.' + n.substring(8).trim()): n.trim();
	}

	/** the shortcuts below a folder of Windows (at most 6 folders deep), the startup folder marked */
	private static void collect(FatLfnDirectory dir, String path, int depth, boolean startup, List<Shortcut> result) throws IOException {
		if ((depth > 6) || (result.size() > 2000))
			return;
		for (Iterator<FatLfnDirectoryEntry> it = dir.iterator(); it.hasNext();) {
			FatLfnDirectoryEntry e = it.next();
			if (e.isDeleted())
				continue;
			String name = e.getName();
			if (e.isDirectory()) {
				String lower = name.toLowerCase(Locale.ROOT);
				if (name.equals(".") || name.equals("..") || ((depth == 0) && SKIP.contains(lower)) || SKIP.contains(lower) && (depth > 0) && !lower.equals("system"))
					continue;
				collect(e.getDirectory(), path.isEmpty() ? name: path + "\\" + name, depth + 1, startup || STARTUP.contains(lower), result);
			} else if (e.isFile() && name.toLowerCase(Locale.ROOT).endsWith(".lnk") && (e.getLength() < 64 * 1024)) {
				String target = linkTarget(e.read(64 * 1024));
				if (target != null)
					result.add(new Shortcut(name.substring(0, name.length() - 4), path, target, startup));
			}
		}
	}

	/**
	 * The program a shortcut (.LNK, the format of Windows 95 and later) starts: the local path of its link information, else
	 * its relative path; null if it is no shortcut or names no path
	 */
	public static String linkTarget(byte[] d) {
		if ((d.length < 0x4C) || (u32(d, 0) != 0x4C))
			return null;
		long flags = u32(d, 0x14);
		int pos = 0x4C;
		if ((flags & 0x01) != 0) // the item list of the shell: skipped
			pos += 2 + u16(d, pos);
		String path = null;
		if ((flags & 0x02) != 0) { // the link information
			int info = pos;
			long size = u32(d, info);
			long infoFlags = u32(d, info + 8);
			if ((infoFlags & 0x01) != 0) {
				int local = info + (int)u32(d, info + 16);
				path = cString(d, local);
			}
			pos += (int)size;
		}
		boolean unicode = (flags & 0x80) != 0;
		String relative = null;
		for (long bit: new long[] {0x04, 0x08}) { // the name (description), the relative path
			if ((flags & bit) == 0)
				continue;
			if (pos + 2 > d.length)
				break;
			int count = u16(d, pos);
			pos += 2;
			int bytes = unicode ? count * 2: count;
			if (pos + bytes > d.length)
				break;
			String s = new String(d, pos, bytes, unicode ? java.nio.charset.StandardCharsets.UTF_16LE: Charset.forName("windows-1252"));
			if (bit == 0x08)
				relative = s;
			pos += bytes;
		}
		if (StringUtils.isNotBlank(path))
			return path;
		return StringUtils.isNotBlank(relative) ? relative: null;
	}

	private static long u32(byte[] d, int o) {
		return (o + 3 < d.length) && (o >= 0) ? (d[o] & 0xffL) | ((d[o + 1] & 0xffL) << 8) | ((d[o + 2] & 0xffL) << 16) | ((d[o + 3] & 0xffL) << 24): 0;
	}

	private static int u16(byte[] d, int o) {
		return (o + 1 < d.length) && (o >= 0) ? (d[o] & 0xff) | ((d[o + 1] & 0xff) << 8): 0;
	}

	private static String cString(byte[] d, int o) {
		if ((o < 0) || (o >= d.length))
			return null;
		int e = o;
		while ((e < d.length) && (d[e] != 0))
			e++;
		return new String(d, o, e - o, Charset.forName("windows-1252"));
	}

	/** true for a program of Windows itself (in its folder, or one of its accessories) */
	static boolean windowsOwn(String target, String winDir) {
		String t = target.toLowerCase(Locale.ROOT);
		if (t.startsWith("c:\\" + winDir.toLowerCase(Locale.ROOT) + "\\"))
			return true;
		for (String w: WINDOWS_OWN)
			if (t.contains(w))
				return true;
		return false;
	}

	/** true if a path on drive C: of the image is there (a path on another drive, e.g. the CD, counts as there) */
	static boolean there(FatLfnDirectory root, String target) {
		String t = target.replace('/', '\\');
		if ((t.length() < 3) || (Character.toUpperCase(t.charAt(0)) != 'C') || (t.charAt(1) != ':'))
			return true;
		FatLfnDirectory dir = root;
		String[] parts = t.substring(3).split("\\\\");
		try {
			for (int i = 0; i < parts.length; i++) {
				if (parts[i].isEmpty())
					continue;
				FatLfnDirectoryEntry e = find(dir, parts[i]);
				if (e == null)
					return false;
				if (i == parts.length - 1)
					return true;
				if (!e.isDirectory())
					return false;
				dir = e.getDirectory();
			}
		} catch (IOException e) {
			return true;
		}
		return true;
	}

	/** the notes of "Check profiles" for a profile that boots Windows 9x from this image */
	public static void check(Report r, List<Problem> result) {
		List<String> programs = new ArrayList<>(), startup = new ArrayList<>(), gone = new ArrayList<>();
		Set<String> seen = new java.util.HashSet<>();
		try (Closer device = new Closer(new FileDisk(r.image()))) {
			FatLfnDirectory root = new FatFileSystem(device.device_).getRoot();
			String winDir = windowsDir(root);
			for (Shortcut s: r.shortcuts()) {
				String lower = s.target().toLowerCase(Locale.ROOT);
				if (!(lower.endsWith(".exe") || lower.endsWith(".com") || lower.endsWith(".bat") || lower.endsWith(".pif")))
					continue; // a document or a folder
				String shown = s.name() + " (" + s.target() + ")";
				if (s.startup())
					startup.add(shown);
				if (windowsOwn(s.target(), winDir) || !seen.add(lower))
					continue;
				(there(root, s.target()) ? programs: gone).add(shown);
			}
		} catch (IOException | RuntimeException e) {
			return;
		}
		startup.addAll(r.run());
		if (!startup.isEmpty())
			result.add(new Problem("win9xstartup", Win3x.shortList(startup)));
		if (!programs.isEmpty())
			result.add(new Problem("win9xprograms", Win3x.shortList(programs)));
		if (!gone.isEmpty())
			result.add(new Problem("win9xgone", Win3x.shortList(gone)));
	}
}
