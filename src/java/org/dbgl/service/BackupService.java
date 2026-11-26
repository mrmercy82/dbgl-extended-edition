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
package org.dbgl.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.List;
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
	/** alpha 52: on request the screenshots (captures/ without the savegames folders) and the backups of the saved games */
	private static final String CAPTURES_DIR = "captures/";
	private static final String SAVEGAMES_PART = "/" + org.dbgl.util.SavegameBackup.DIR + "/";
	private static final String SAVEGAMES_DIR = org.dbgl.util.SavegameBackup.DIR + "/";

	/** alpha 52: the folder of the backups of saved games (savegames, in the data folder) */
	static File savegamesDir() {
		return new File(FileLocationService.getInstance().getDataDir(), org.dbgl.util.SavegameBackup.DIR);
	}
	/** alpha 49: how the comment of the zip file begins; the version of DBGL that made the backup follows */
	public static final String COMMENT = "DBGL Extended Edition ";

	private BackupService() {
	}

	/** @return the number of files in the backup */
	public static int backup(File zipFile) throws IOException, SQLException {
		return backup(zipFile, false, false);
	}

	/**
	 * alpha 52: with the screenshots and/or the backups of the saved games (both in the folder captures)
	 *
	 * @return the number of files in the backup
	 */
	public static int backup(File zipFile, boolean captures, boolean savegames) throws IOException, SQLException {
		File tmp = Files.createTempDirectory("dbgl-backup").toFile();
		try {
			DatabaseService.getInstance().backupTo(new File(tmp, "db"));
			int count = 0;
			try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
				zip.setComment(COMMENT + org.dbgl.constants.Constants.PROGRAM_VERSION); // alpha 49: the window of the backups shows it
				count += addDir(zip, new File(tmp, "db"), DB_DIR);
				count += addDir(zip, FileLocationService.getInstance().getProfilesDir(), PROFILES_DIR);
				count += addDir(zip, FileLocationService.getInstance().getTemplatesDir(), TEMPLATES_DIR);
				File settings = SettingsService.getInstance().getSettingsFile();
				if ((settings != null) && settings.isFile()) {
					SettingsService.getInstance().save();
					addFile(zip, settings, SETTINGS);
					count++;
				}
				if (captures || savegames)
					count += addDir(zip, FileLocationService.getInstance().getCapturesDir(), CAPTURES_DIR, rel -> isSavegamePart(rel) ? savegames: captures);
				if (savegames) // alpha 52: their own folder now
					count += addDir(zip, savegamesDir(), SAVEGAMES_DIR);
			}
			return count;
		} finally {
			FileUtils.deleteQuietly(tmp);
		}
	}

	/** the folder automatic backups go to, unless another one is set */
	public static File defaultAutoBackupDir() {
		return new File(FileLocationService.getInstance().getProfilesDir().getParentFile(), "backups");
	}

	/**
	 * alpha 54 (TEST3): the folder of DBGL's backups (automatic ones, before an update, of saved games packed, of the way back
	 * from a stick, made in the window of the backups): the one chosen in the settings ([backup] dir), else "backups" in the
	 * data folder
	 */
	public static File backupDir() {
		String dir = SettingsService.getInstance().getValue("backup", "dir");
		return (dir == null) || dir.isBlank() ? defaultAutoBackupDir(): new File(dir.trim());
	}

	/** the backups DBGL made in a folder (to move them along when another folder is chosen): those the window of the backups shows, and which are locked */
	public static List<File> ownBackupsIn(File dir) {
		File[] files = dir.listFiles(f -> (org.dbgl.util.BackupCatalog.kindOf(f.getName()) != null) || f.getName().equals(org.dbgl.util.BackupCatalog.LOCKED));
		return files == null ? List.of(): java.util.Arrays.stream(files).sorted().toList();
	}

	/**
	 * Move DBGL's backups from one folder to another (across drives: copied, then deleted); a file of the same name there
	 * stays and the one here too.
	 *
	 * @return how many were moved
	 */
	public static int moveBackups(File from, File to) throws IOException {
		Files.createDirectories(to.toPath());
		int moved = 0;
		for (File f: ownBackupsIn(from)) {
			File target = new File(to, f.getName());
			if (target.exists())
				continue;
			try {
				Files.move(f.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) { // another drive
				if (f.isDirectory())
					FileUtils.copyDirectory(f, target, true);
				else
					FileUtils.copyFile(f, target, true);
				FileUtils.forceDelete(f);
			}
			moved++;
		}
		return moved;
	}

	/**
	 * @param mode never, daily or weekly
	 * @param last the date of the last automatic backup (yyyy-MM-dd), or empty
	 */
	public static boolean isAutoBackupDue(String mode, String last, java.time.LocalDate today) {
		if (!"daily".equals(mode) && !"weekly".equals(mode))
			return false;
		if ((last == null) || last.isBlank())
			return true;
		try {
			java.time.LocalDate lastDate = java.time.LocalDate.parse(last.strip());
			return !today.isBefore(lastDate.plusDays("daily".equals(mode) ? 1: 7));
		} catch (java.time.format.DateTimeParseException e) {
			return true;
		}
	}

	/** make a backup in the folder and keep only the newest ones */
	public static File autoBackup(File dir, int keep) throws IOException, SQLException {
		return autoBackup(dir, keep, false, false);
	}

	/** alpha 52: with the screenshots and/or the backups of the saved games; copied to the second place too, if one is set */
	public static File autoBackup(File dir, int keep, boolean captures, boolean savegames) throws IOException, SQLException {
		dir.mkdirs();
		File zip = new File(dir, "dbgl-backup-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip");
		// alpha 54: written under another name first, then renamed: OneDrive and the like do not take a half written file
		File part = new File(dir, zip.getName() + ".part");
		try {
			backup(part, captures, savegames);
			Files.move(part.toPath(), zip.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(part.toPath());
		}
		File[] all = dir.listFiles((d, n) -> n.startsWith("dbgl-backup-") && n.endsWith(".zip"));
		if (all != null) {
			java.util.Arrays.sort(all, java.util.Comparator.comparing(File::getName).reversed());
			// alpha 49: locked ones stay, and are not among those counted
			int kept = 0;
			for (File f: all) {
				if (org.dbgl.util.BackupCatalog.isLocked(f))
					continue;
				if (++kept > Math.max(1, keep))
					Files.deleteIfExists(f.toPath());
			}
		}
		org.dbgl.util.SecondBackupPlace.autoBackupMade(zip, keep);
		return zip;
	}

	/** alpha 52: a path in captures (with / and without the leading folder) that is in the backups of the saved games */
	public static boolean isSavegamePart(String rel) {
		return ("/" + rel).contains(SAVEGAMES_PART);
	}

	/** alpha 52: the size of the screenshots and of the backups of the saved games, in bytes: {screenshots, saved games} */
	public static long[] capturesSizes() {
		long[] result = new long[2];
		Path base = FileLocationService.getInstance().getCapturesDir().toPath();
		if (!Files.isDirectory(base))
			return result;
		try (Stream<Path> files = Files.walk(base)) {
			files.filter(Files::isRegularFile).forEach(p -> {
				try {
					result[isSavegamePart(base.relativize(p).toString().replace(File.separatorChar, '/')) ? 1: 0] += Files.size(p);
				} catch (IOException e) {
					// not counted
				}
			});
		} catch (IOException | RuntimeException e) {
			// as far as counted
		}
		Path own = savegamesDir().toPath(); // alpha 52: their own folder
		if (Files.isDirectory(own)) {
			try (Stream<Path> files = Files.walk(own)) {
				files.filter(Files::isRegularFile).forEach(p -> {
					try {
						result[1] += Files.size(p);
					} catch (IOException e) {
						// not counted
					}
				});
			} catch (IOException | RuntimeException e) {
				// as far as counted
			}
		}
		return result;
	}

	private static int addDir(ZipOutputStream zip, File dir, String prefix) throws IOException {
		return addDir(zip, dir, prefix, rel -> true);
	}

	private static int addDir(ZipOutputStream zip, File dir, String prefix, java.util.function.Predicate<String> wanted) throws IOException {
		if (!dir.isDirectory())
			return 0;
		int count = 0;
		Path base = dir.toPath();
		try (Stream<Path> files = Files.walk(base)) {
			for (Path p: (Iterable<Path>)files.filter(Files::isRegularFile)::iterator) {
				if (!wanted.test(base.relativize(p).toString().replace(File.separatorChar, '/')))
					continue;
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
			// alpha 52: screenshots and backups of saved games go back next to those there now; none is removed
			File capturesBackup = new File(tmp, "captures");
			if (capturesBackup.isDirectory())
				FileUtils.copyDirectory(capturesBackup, FileLocationService.getInstance().getCapturesDir());
			File savegamesBackup = new File(tmp, org.dbgl.util.SavegameBackup.DIR);
			if (savegamesBackup.isDirectory())
				FileUtils.copyDirectory(savegamesBackup, savegamesDir());
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
