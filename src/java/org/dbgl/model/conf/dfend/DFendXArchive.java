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
package org.dbgl.model.conf.dfend;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;


/**
 * Alpha 48: a game archive of DFendX (a ZIP file or its unpacked folder): one or more .prof files of D-Fend Reloaded at the top,
 * next to the folders VirtualHD (the game) and Capture (screenshots), whose paths in the .prof are relative to that place.
 */
public final class DFendXArchive {

	private DFendXArchive() {
	}

	/** @return the .prof files at the top of the folder (or of its only subfolder, if a ZIP packed one), sorted */
	public static List<File> profFiles(File dir) {
		dir = dir.getAbsoluteFile(); // relative paths would be resolved against the data folder of DBGL
		List<File> result = new ArrayList<>(FileUtils.listFiles(dir, new String[] {"prof", "PROF"}, false));
		if (result.isEmpty()) {
			File[] subs = dir.listFiles(File::isDirectory);
			if ((subs != null) && (subs.length == 1))
				result.addAll(FileUtils.listFiles(subs[0], new String[] {"prof", "PROF"}, false));
		}
		result.sort(null);
		return result;
	}

	/** @return a folder below the parent, named like the archive, that does not exist yet */
	public static File freeTargetDir(File parent, File archive) {
		String base = FilenameUtils.getBaseName(archive.getName()).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
		if (base.isEmpty() || base.startsWith("."))
			base = "DFendX" + base;
		File dir = new File(parent, base);
		for (int nr = 2; dir.exists(); nr++)
			dir = new File(parent, base + " (" + nr + ")");
		return dir;
	}

	/**
	 * unpacks the ZIP file into the (new) folder. The archive is untrusted: an entry that would end up outside the folder
	 * (e.g. "../x" or an absolute path) stops the unpacking, existing files are never overwritten.
	 */
	public static void extract(File zip, File dstDir) throws IOException {
		File root = dstDir.getCanonicalFile();
		Files.createDirectories(root.toPath());
		try (ZipFile zf = new ZipFile(zip)) {
			for (Enumeration<? extends ZipEntry> en = zf.entries(); en.hasMoreElements();) {
				ZipEntry entry = en.nextElement();
				String name = entry.getName().replace('\\', '/');
				File dst = new File(root, name).getCanonicalFile();
				if (!dst.toPath().startsWith(root.toPath()) || name.startsWith("/") || name.matches("^[A-Za-z]:.*"))
					throw new IOException("Unsafe entry in " + zip.getName() + ": " + entry.getName());
				if (entry.isDirectory() || name.endsWith("/")) {
					Files.createDirectories(dst.toPath());
				} else {
					Files.createDirectories(dst.getParentFile().toPath());
					try (InputStream in = zf.getInputStream(entry)) {
						Files.copy(in, dst.toPath()); // fails if it exists
					}
					if (entry.getTime() > 0)
						dst.setLastModified(entry.getTime());
				}
			}
		}
	}
}
