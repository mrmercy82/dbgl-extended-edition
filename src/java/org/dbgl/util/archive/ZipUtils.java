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
package org.dbgl.util.archive;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.interfaces.ProgressNotifyable;
import org.dbgl.service.TextService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.SystemUtils;


public class ZipUtils {

	private ZipUtils() {
	}

	public static void zipEntry(ZipOutputStream zos, File srcEntry, File dstEntry, ProgressNotifyable prog) throws IOException {
		if (!srcEntry.isFile() && !srcEntry.isDirectory())
			throw new IOException(TextService.getInstance().get("general.error.openfile", new Object[] {srcEntry}));
		ZipEntry zipEntry = new ZipEntry(FilesUtils.toArchivePath(dstEntry, srcEntry.isDirectory()));
		zipEntry.setTime(srcEntry.lastModified());
		if (srcEntry.isFile() && !srcEntry.canWrite())
			zipEntry.setExtra(new byte[] {1});
		zos.putNextEntry(zipEntry);
		if (srcEntry.isFile()) {
			Files.copy(srcEntry.toPath(), zos);
			prog.incrProgress(srcEntry.length());
		}
		zos.closeEntry();
	}

	public static void zipDir(ZipOutputStream zos, File srcDirToZip, File dstDirInZip, File srcRootDir, ProgressNotifyable prog) throws IOException {
		String[] dirList = srcDirToZip.list();
		if (dirList == null)
			throw new IOException(TextService.getInstance().get("general.error.opendir", new Object[] {srcDirToZip}));
		for (String dirEntry: dirList) {
			File srcEntry = new File(srcDirToZip, dirEntry);
			zipEntry(zos, srcEntry, new File(dstDirInZip, FilesUtils.relativize(srcRootDir, srcEntry)), prog);
			if (srcEntry.isDirectory())
				zipDir(zos, srcEntry, dstDirInZip, srcRootDir, prog);
		}
	}

	public static List<ZipEntry> readEntriesInZip(File zipFile) throws IOException {
		List<ZipEntry> result = new ArrayList<>();
		try (ZipFile zfile = new ZipFile(zipFile)) {
			for (Enumeration<? extends ZipEntry> entries = zfile.entries(); entries.hasMoreElements();)
				result.add(entries.nextElement());
		}
		return result;
	}

	public static void extractDirInZip(List<ZipEntry> entries, File archive, File dirToBeExtracted, File dstDir, boolean forceCreation, ProgressNotifyable prog) throws IOException {
		try (ZipFile zf = new ZipFile(archive)) {
			for (ZipEntry entry: entries)
				extractEntry(zf, entry, new File(dstDir, FilesUtils.relativize(dirToBeExtracted, new File(entry.getName()))), forceCreation, prog);
		}
	}

	public static void extractEntry(ZipFile zf, ZipEntry srcEntry, File dstFile, boolean forceCreation, ProgressNotifyable prog) throws IOException {
		File foundDstFile = null, temporarilyRenamedFile = null;
		if (SystemUtils.IS_WINDOWS && dstFile.getName().contains("~")) {
			foundDstFile = dstFile.getCanonicalFile();
			if (!foundDstFile.getName().equals(dstFile.getName()) && foundDstFile.exists()) {
				temporarilyRenamedFile = new File(foundDstFile.getParentFile(), UUID.randomUUID() + "__" + foundDstFile.getName());
				if (!foundDstFile.renameTo(temporarilyRenamedFile)) {
					throw new IOException(TextService.getInstance().get("general.error.savefile", new Object[] {temporarilyRenamedFile.getPath()}));
				}
			}
		}

		if (srcEntry.isDirectory()) {
			if (!dstFile.exists())
				FilesUtils.createDir(dstFile);
		} else {
			if (dstFile.exists()) {
				if (forceCreation) {
					File candidate = null;
					String ext = FilenameUtils.getExtension(dstFile.getName());
					if (StringUtils.isNotBlank(ext))
						ext = FilenameUtils.EXTENSION_SEPARATOR + ext;
					int nr = 2;
					do {
						candidate = new File(dstFile.getParentFile(), FilenameUtils.getBaseName(dstFile.getName()) + "(" + nr + ")" + ext);
						nr++;
					} while (candidate.exists());
					dstFile = candidate;
				} else {
					throw new IOException(TextService.getInstance().get("general.error.filetobeextractedexists", new Object[] {dstFile}));
				}
			}
			if (dstFile.getParentFile() != null)
				FilesUtils.createDir(dstFile.getParentFile());

			try (InputStream zis = zf.getInputStream(srcEntry)) {
				Files.copy(zis, dstFile.toPath());
			}
			byte[] extra = srcEntry.getExtra();
			if ((extra != null) && (extra.length == 1) && (extra[0] == 1))
				FilesUtils.setReadOnly(dstFile);
		}
		FilesUtils.setLastModified(dstFile, srcEntry.getTime());

		if (foundDstFile != null && temporarilyRenamedFile != null && !temporarilyRenamedFile.renameTo(foundDstFile)) {
			throw new IOException(TextService.getInstance().get("general.error.savefile", new Object[] {foundDstFile.getPath()}));
		}

		prog.incrProgress(srcEntry.getSize());
	}
}