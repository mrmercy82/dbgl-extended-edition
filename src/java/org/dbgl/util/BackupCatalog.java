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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.dbgl.service.BackupService;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.SettingsService;


/**
 * Alpha 49: the backups DBGL made, for the window that lists them: of which kind each is (told by its name), the version of
 * DBGL its data is from, and whether it is locked. A locked backup is deleted neither in that window nor when DBGL tidies up
 * after a new automatic backup; the names of the locked ones are kept in locked.txt in the folder of the backups.
 */
public final class BackupCatalog {

	public enum Kind {
		/** at the start of DBGL, as set in the settings */
		AUTO(true),
		/** File > Create backup, saved into a folder of the backups */
		MANUAL(true),
		/** the first start of another version of DBGL */
		VERSION(true),
		/** before an update was installed: the data */
		UPDATE_DATA(true),
		/** before an update was installed: the program files it replaced */
		UPDATE_PROGRAM(false),
		/** before an update was installed: everything that was chosen */
		FULL(false),
		/** the update itself, kept to install it again */
		INSTALLER(false),
		/** alpha 50: a folder with the files of this installation that were replaced when a USB stick was brought back */
		STICK_RETURN(false);

		private final boolean restorable_;

		Kind(boolean restorable) {
			restorable_ = restorable;
		}

		/** true: File > Restore backup can read it */
		public boolean restorable() {
			return restorable_;
		}
	}

	public record Backup(File file, Kind kind, String version, long when, long size, boolean locked) {
	}

	public static final String LOCKED = "locked.txt";

	private static final Pattern AUTO_NAME = Pattern.compile("dbgl-backup-\\d{8}-\\d{6}\\.zip"), INSTALLER_NAME = Pattern.compile("dbgl-(.*)-update\\.zip"),
			STAMP = Pattern.compile("-(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}|\\d{8}-\\d{4,6})$"), VERSION_NAME = Pattern.compile("before-.*-from-(.+)");

	/** the folder "Profiles > Bring back from the USB stick" keeps the replaced files in: only a folder of exactly this name is one */
	public static final Pattern STICK_RETURN_NAME = Pattern.compile("stick-return-\\d{8}-\\d{6}");

	private BackupCatalog() {
	}

	private static long sizeOf(File dir) {
		long sum = 0;
		File[] files = dir.listFiles();
		if (files != null)
			for (File f: files)
				sum += f.isDirectory() ? sizeOf(f): f.length();
		return sum;
	}

	/** @return the kind of backup a file of this name is, null if it is none of DBGL */
	public static Kind kindOf(String fileName) {
		String name = fileName.toLowerCase(Locale.ROOT);
		if (STICK_RETURN_NAME.matcher(name).matches())
			return Kind.STICK_RETURN;
		if (!name.endsWith(".zip"))
			return null;
		if (name.startsWith(FullBackup.PREFIX))
			return Kind.FULL;
		if (name.startsWith("dbgl-backup-before-update-"))
			return Kind.UPDATE_DATA;
		if (name.startsWith("dbgl-program-"))
			return Kind.UPDATE_PROGRAM;
		if (AUTO_NAME.matcher(name).matches())
			return Kind.AUTO;
		if (name.startsWith("dbgl-backup-"))
			return Kind.MANUAL;
		if (name.startsWith("before-"))
			return Kind.VERSION;
		if (INSTALLER_NAME.matcher(name).matches())
			return Kind.INSTALLER;
		return null;
	}

	/** @return the version of DBGL in the name of the backup (as written there, e.g. "0.101alpha48"), "" if it has none */
	public static String versionInName(String fileName, Kind kind) {
		if (kind == Kind.STICK_RETURN)
			return "";
		String name = fileName.substring(0, fileName.length() - ".zip".length());
		if (kind == Kind.INSTALLER) {
			Matcher m = INSTALLER_NAME.matcher(fileName.toLowerCase(Locale.ROOT));
			return m.matches() ? fileName.substring(m.start(1), m.end(1)): "";
		}
		name = STAMP.matcher(name).replaceFirst("");
		switch (kind) {
			case FULL:
				return name.substring(FullBackup.PREFIX.length());
			case UPDATE_DATA:
				return name.substring("dbgl-backup-before-update-".length());
			case UPDATE_PROGRAM:
				return name.substring("dbgl-program-".length());
			case VERSION:
				Matcher m = VERSION_NAME.matcher(name);
				return m.matches() ? m.group(1): "";
			default:
				return "";
		}
	}

	/** a version as it is in a file name, written to be read: "ee-0.101alpha48" becomes "0.101 alpha 48" */
	public static String pretty(String version) {
		return version.replaceFirst("(?i)^ee-", "").replaceAll("(?i)(\\d)\\s*(alpha|beta|rc)\\s*(\\d)", "$1 $2 $3").replaceAll("(\\d)-(0\\.\\d)", "$1 \u2013 $2").replaceFirst("^pre(\\d)", "< $1").replaceFirst("(?i)(\\d)(alpha|beta)$", "$1 $2").strip(); // alpha 51: "0.93-0.98" a range, "pre0.92" older than 0.92, "0.101alpha" an early alpha
	}

	/** the version DBGL wrote into the backup itself (since alpha 49), "" if there is none */
	static String versionInside(File zip) {
		try (ZipFile z = new ZipFile(zip)) {
			String comment = z.getComment();
			return (comment != null) && comment.startsWith(BackupService.COMMENT) ? comment.substring(BackupService.COMMENT.length()).strip(): "";
		} catch (IOException | RuntimeException e) {
			return "";
		}
	}

	/** the folders DBGL puts backups into: of the automatic ones, of the data folder, and the one chosen for full backups */
	public static List<File> folders() {
		Set<File> result = new LinkedHashSet<>();
		List<File> candidates = new ArrayList<>();
		candidates.add(BackupService.defaultAutoBackupDir());
		candidates.add(new File(FileLocationService.getInstance().getDataDir(), VersionBackup.DIR));
		String full = SettingsService.getInstance().getValue(FullBackup.SECTION, "backupdir");
		if ((full != null) && !full.isBlank())
			candidates.add(new File(full));
		for (File f: candidates) {
			try {
				result.add(f.getCanonicalFile());
			} catch (IOException e) {
				result.add(f.getAbsoluteFile());
			}
		}
		return new ArrayList<>(result);
	}

	/** the backups in these folders, the newest first */
	public static List<Backup> list(List<File> folders) {
		List<Backup> result = new ArrayList<>();
		for (File dir: folders) {
			File[] files = dir.listFiles();
			if (files == null)
				continue;
			Set<String> locked = locked(dir);
			Set<Kind> always = autoLocked();
			for (File f: files) {
				Kind kind = kindOf(f.getName());
				if ((kind == null) || ((kind == Kind.STICK_RETURN) != f.isDirectory()))
					continue;
				if (kind == Kind.STICK_RETURN) {
					result.add(new Backup(f, kind, "", f.lastModified(), sizeOf(f), locked.contains(f.getName()) || always.contains(kind)));
					continue;
				}
				String version = versionInName(f.getName(), kind);
				if (version.isEmpty() && kind.restorable())
					version = versionInside(f);
				// alpha 51: which DBGL it was, by its database: for "older" and for what test versions of alpha 51 wrote into the name (e.g.
				// "0.75" for DBGL 0.79); only a version of the Extended Edition (0.101 on) was taken from the settings and stays
				if ((kind == Kind.VERSION) && !version.isEmpty() && !version.matches("(?i)(ee-)?0\\.1(0[1-9]|[1-9]\\d).*")) {
					String dbgl = VersionBackup.dbglInBackup(f);
					if (dbgl != null)
						version = dbgl;
				}
				result.add(new Backup(f, kind, version, f.lastModified(), f.length(), locked.contains(f.getName()) || always.contains(kind)));
			}
		}
		result.sort(Comparator.comparingLong(Backup::when).reversed().thenComparing(b -> b.file().getName()));
		return result;
	}

	/** the names of the locked backups of a folder */
	public static Set<String> locked(File dir) {
		Set<String> result = new TreeSet<>();
		File list = new File(dir, LOCKED);
		if (list.isFile()) {
			try {
				for (String line: Files.readAllLines(list.toPath(), StandardCharsets.UTF_8))
					if (!line.isBlank())
						result.add(line.strip());
			} catch (IOException | RuntimeException e) {
				// none locked then
			}
		}
		return result;
	}

	public static boolean isLocked(File backup) {
		File dir = backup.getParentFile();
		Kind kind = kindOf(backup.getName());
		return ((kind != null) && autoLocked().contains(kind)) || ((dir != null) && locked(dir).contains(backup.getName()));
	}

	/** the kinds of backups that are always locked (chosen in the options of the window of the backups) */
	public static Set<Kind> autoLocked() {
		Set<Kind> result = java.util.EnumSet.noneOf(Kind.class);
		String stored;
		try {
			stored = SettingsService.getInstance().getValue("gui", "backupsautolock");
		} catch (RuntimeException e) {
			stored = null;
		}
		if (stored != null) {
			for (String name: stored.split("[ ,]+")) {
				try {
					if (!name.isBlank())
						result.add(Kind.valueOf(name.strip()));
				} catch (IllegalArgumentException e) {
					// a kind this version does not know
				}
			}
		}
		return result;
	}

	public static void setAutoLocked(Set<Kind> kinds) {
		StringBuilder sb = new StringBuilder();
		for (Kind k: kinds)
			sb.append(sb.length() == 0 ? "": " ").append(k.name());
		SettingsService.getInstance().setValue("gui", "backupsautolock", sb.toString());
	}

	/** lock or unlock a backup; names of backups that are gone leave the list */
	public static void setLocked(File backup, boolean lock) throws IOException {
		File dir = backup.getParentFile();
		Set<String> names = locked(dir);
		names.removeIf(n -> !new File(dir, n).exists());
		if (lock)
			names.add(backup.getName());
		else
			names.remove(backup.getName());
		File list = new File(dir, LOCKED);
		if (names.isEmpty())
			Files.deleteIfExists(list.toPath());
		else
			Files.write(list.toPath(), names, StandardCharsets.UTF_8);
	}

	/** @return false if the backup is locked (it stays) */
	public static boolean delete(File backup) throws IOException {
		if (isLocked(backup))
			return false;
		if (backup.isDirectory()) {
			// a folder only if it is one of the stick: by its exact name, nothing else a user may have put there
			if (!STICK_RETURN_NAME.matcher(backup.getName()).matches())
				return false;
			org.apache.commons.io.FileUtils.deleteDirectory(backup);
			return true;
		}
		Files.deleteIfExists(backup.toPath());
		return true;
	}
}
