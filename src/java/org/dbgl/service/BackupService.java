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
package org.dbgl.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;


/**
 * A backup of everything DBGL keeps about the games: the database, the profile and template configurations and the settings.
 * Game files, captures and DOSBox itself are not part of it. In the zip file: db/ (the database files, written by HSQLDB
 * while the database is open, so they are consistent), profiles/, templates/ and settings.conf.
 */
public final class BackupService {

	private static final String DB_DIR = "db/";
	private static final String PROFILES_DIR = "profiles/";
	private static final String TEMPLATES_DIR = "templates/";
	private static final String SETTINGS = "settings.conf";

	private BackupService() {
	}

	/** @return the number of files in the backup */
	public static int backup(File zipFile) throws IOException, SQLException {
		File tmp = Files.createTempDirectory("dbgl-backup").toFile();
		try {
			DatabaseService.getInstance().backupTo(new File(tmp, "db"));
			int count = 0;
			try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
				count += addDir(zip, new File(tmp, "db"), DB_DIR);
				count += addDir(zip, FileLocationService.getInstance().getProfilesDir(), PROFILES_DIR);
				count += addDir(zip, FileLocationService.getInstance().getTemplatesDir(), TEMPLATES_DIR);
				File settings = SettingsService.getInstance().getSettingsFile();
				if ((settings != null) && settings.isFile()) {
					SettingsService.getInstance().save();
					addFile(zip, settings, SETTINGS);
					count++;
				}
			}
			return count;
		} finally {
			FileUtils.deleteQuietly(tmp);
		}
	}

	private static int addDir(ZipOutputStream zip, File dir, String prefix) throws IOException {
		if (!dir.isDirectory())
			return 0;
		int count = 0;
		Path base = dir.toPath();
		try (Stream<Path> files = Files.walk(base)) {
			for (Path p: (Iterable<Path>)files.filter(Files::isRegularFile)::iterator) {
				addFile(zip, p.toFile(), prefix + base.relativize(p).toString().replace(File.separatorChar, '/'));
				count++;
			}
		}
		return count;
	}

	private static void addFile(ZipOutputStream zip, File file, String name) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(file.lastModified());
		zip.putNextEntry(entry);
		Files.copy(file.toPath(), zip);
		zip.closeEntry();
	}

	/** @return true if the file is a backup made by DBGL */
	public static boolean isBackup(File zipFile) {
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
			for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry())
				if (e.getName().startsWith(DB_DIR) && e.getName().endsWith(".script"))
					return true;
		} catch (IOException e) {
			// not a zip file
		}
		return false;
	}

	/**
	 * Put the backup back. The database is shut down for this, so DBGL must end afterwards. The current files are kept with
	 * the extension .before-restore.
	 */
	public static void restore(File zipFile) throws IOException, SQLException {
		File dbBase = DatabaseService.getInstance().getDatabaseFileBase(); // e.g. .../db/database
		File dbDir = dbBase.getParentFile();
		File profilesDir = FileLocationService.getInstance().getProfilesDir();
		File templatesDir = FileLocationService.getInstance().getTemplatesDir();
		File settings = SettingsService.getInstance().getSettingsFile();

		File tmp = Files.createTempDirectory("dbgl-restore").toFile();
		try {
			try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
				for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
					if (e.isDirectory())
						continue;
					File target = new File(tmp, e.getName()).getCanonicalFile();
					if (!target.toPath().startsWith(tmp.getCanonicalFile().toPath()))
						throw new IOException("invalid entry in backup: " + e.getName());
					target.getParentFile().mkdirs();
					Files.copy(zip, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			File[] dbFiles = new File(tmp, "db").listFiles((d, n) -> n.endsWith(".script") || n.endsWith(".properties") || n.endsWith(".data") || n.endsWith(".lobs") || n.endsWith(".backup"));
			if ((dbFiles == null) || Stream.of(dbFiles).noneMatch(f -> f.getName().endsWith(".script")))
				throw new IOException("no database in backup: " + zipFile);

			DatabaseService.getInstance().shutdown();

			// database: the files are named like the database (database.script, ...), whatever they were called in the backup
			File[] current = dbDir.listFiles((d, n) -> n.startsWith(dbBase.getName() + "."));
			if (current != null)
				for (File f: current)
					Files.move(f.toPath(), new File(f.getPath() + ".before-restore").toPath(), StandardCopyOption.REPLACE_EXISTING);
			for (File f: dbFiles) {
				String ext = f.getName().substring(f.getName().lastIndexOf('.'));
				Files.copy(f.toPath(), new File(dbDir, dbBase.getName() + ext).toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			restoreDir(new File(tmp, "profiles"), profilesDir);
			restoreDir(new File(tmp, "templates"), templatesDir);
			File settingsBackup = new File(tmp, SETTINGS);
			if (settingsBackup.isFile() && (settings != null)) {
				if (settings.isFile())
					Files.copy(settings.toPath(), new File(settings.getPath() + ".before-restore").toPath(), StandardCopyOption.REPLACE_EXISTING);
				Files.copy(settingsBackup.toPath(), settings.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			FileUtils.deleteQuietly(tmp);
		}
	}

	private static void restoreDir(File from, File to) throws IOException {
		if (!from.isDirectory())
			return;
		if (to.isDirectory()) {
			File old = new File(to.getPath() + ".before-restore");
			FileUtils.deleteDirectory(old);
			FileUtils.moveDirectory(to, old);
		}
		FileUtils.copyDirectory(from, to);
	}
}
