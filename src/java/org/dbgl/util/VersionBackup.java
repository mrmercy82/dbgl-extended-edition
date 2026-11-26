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
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.dbgl.constants.Constants;
import org.dbgl.model.FileLocation;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.SettingsService;


/**
 * Before a version of DBGL runs for the first time (after an update, also from DBGL 0.100 or an older Extended Edition), the
 * data of the user is kept in a zip of its own: the database (before it is upgraded), the configuration files of the profiles
 * and templates with their earlier versions, and the settings. Not the captures and the games (too large, and DBGL does not
 * change them). The newest {@link #KEEP} and the oldest one are kept, in the folder "backups" of the data folder.
 */
public final class VersionBackup {

	public static final String SECTION = "edition", ITEM = "lastversion", DIR = "backups";
	static final int KEEP = 3;

	private static boolean done_;

	private VersionBackup() {
	}

	/** call before the database is opened; does nothing if this version ran before or nothing is there yet (a new installation) */
	public static synchronized void beforeFirstStart() {
		if (done_)
			return;
		done_ = true;
		SettingsService settings = SettingsService.getInstance();
		String last = settings.getValue(SECTION, ITEM);
		if (Constants.PROGRAM_VERSION.equals(last))
			return;
		try {
			File dbDir = databaseDir();
			boolean existing = (dbDir != null) && dbDir.isDirectory() && (databaseFiles(dbDir).length > 0);
			if (existing) {
				File zip = backup(dbDir, last);
				System.out.println("Before the first start of " + Constants.PROGRAM_VERSION + " (last run: " + (last == null || last.isEmpty() ? "unknown": last) + "), data kept in " + zip);
			}
			settings.setValue(SECTION, ITEM, Constants.PROGRAM_VERSION);
			settings.save();
		} catch (IOException | RuntimeException e) {
			// tried again on the next start; DBGL starts anyway
			System.err.println("Keeping the data before the first start of " + Constants.PROGRAM_VERSION + " failed: " + e);
			e.printStackTrace();
		}
	}

	/**
	 * alpha 51: the database could not be opened (e.g. too old a format): the backup before the first start is made again next
	 * time, after the database was converted
	 */
	public static synchronized void forget() {
		try {
			SettingsService settings = SettingsService.getInstance();
			if (Constants.PROGRAM_VERSION.equals(settings.getValue(SECTION, ITEM))) {
				settings.setValue(SECTION, ITEM, "");
				settings.save();
			}
		} catch (IOException | RuntimeException e) {
			e.printStackTrace();
		}
	}

	/** the folder of the database of DBGL (by default "db" in the data folder), null if it is not a file database */
	static File databaseDir() {
		String conn = SettingsService.getInstance().getValue("database", "connectionstring");
		File file = (conn == null) ? null: FileLocationService.getFileFromConnectionString(conn);
		if (file == null)
			return null;
		File canonical = new FileLocation(file.getPath(), FileLocationService.getInstance().dataRelative()).getCanonicalFile();
		return canonical.getParentFile();
	}

	/** the files of the database itself (e.g. database.script, database.properties), not those of the MobyGames cache */
	private static File[] databaseFiles(File dbDir) {
		String conn = SettingsService.getInstance().getValue("database", "connectionstring");
		String name = FileLocationService.getFileFromConnectionString(conn).getName() + ".";
		File[] files = dbDir.listFiles(f -> f.isFile() && f.getName().startsWith(name) && !f.getName().endsWith(".lck"));
		return files == null ? new File[0]: files;
	}

	static File backup(File dbDir, String last) throws IOException {
		FileLocationService fls = FileLocationService.getInstance();
		File dir = org.dbgl.service.BackupService.backupDir(); // alpha 54: the folder chosen in the settings (else data\backups)
		Files.createDirectories(dir.toPath());
		String from = (last == null || last.isEmpty()) ? fromDatabase(dbDir): last.replaceAll("[^A-Za-z0-9.\\-]", "_");
		File zip = new File(dir, "before-" + Constants.PROGRAM_VERSION.replaceAll("[^A-Za-z0-9.\\-]", "_") + "-from-" + from + "-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".zip");
		File tmp = new File(zip.getPath() + ".part");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp.toPath()))) {
			for (File f: databaseFiles(dbDir))
				add(out, f, "db/" + f.getName());
			addDir(out, fls.getProfilesDir(), "profiles/");
			addDir(out, fls.getTemplatesDir(), "templates/");
			File settingsFile = SettingsService.getInstance().getSettingsFile();
			if ((settingsFile != null) && settingsFile.isFile())
				add(out, settingsFile, settingsFile.getName());
		}
		Files.move(tmp.toPath(), zip.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		// the newest are kept
		File[] all = dir.listFiles(f -> f.isFile() && f.getName().startsWith("before-") && f.getName().endsWith(".zip"));
		if (all != null) {
			List<File> sorted = new ArrayList<>(Arrays.asList(all));
			sorted.sort(Comparator.comparingLong(File::lastModified).reversed());
			// and the oldest one: the data as it was before the first update (before alpha 46 it went after three updates)
			sorted.removeIf(BackupCatalog::isLocked); // alpha 49: locked ones stay, and are not among those counted
			for (int i = KEEP; i < sorted.size() - 1; i++)
				Files.deleteIfExists(sorted.get(i).toPath());
		}
		return zip;
	}

	/** the version table in a database.script of HSQLDB, e.g. "INSERT INTO VERSION VALUES(0,99)" */
	private static final java.util.regex.Pattern DB_VERSION = java.util.regex.Pattern.compile("INSERT INTO (?:PUBLIC\\.)?VERSION VALUES\\((\\d+),\\s*(\\d+)\\)");

	/**
	 * alpha 51: the DBGL a database comes from when no Extended Edition ran on it before. Its version does not follow the
	 * numbers of DBGL (DBGL 0.79 has 75), only these are sure: 92 is DBGL 0.92, 99 is 0.99 up to 0.100 beta 17 (the same), 101
	 * to 103 an early alpha of the Extended Edition (before it kept its version); 93 to 98 lie between 0.92 and 0.99, anything
	 * below 92 is from before 0.92 (the old format, which 0.92 converts). Else "older".
	 */
	static String dbglOfDatabaseVersion(int major, int minor) {
		if ((major != 0) || (minor < 50) || (minor > 103) || (minor == 100))
			return "older";
		if (minor > 100)
			return "0.101alpha";
		if (minor == 99)
			return "0.99-0.100";
		if (minor == 92)
			return "0.92";
		if (minor > 92)
			return "0.93-0.98";
		return "pre0.92";
	}

	/** alpha 51: the DBGL of a database.script (its lines), "older" if it does not say */
	static String dbglOfScript(java.io.BufferedReader script) throws IOException {
		for (String line; (line = script.readLine()) != null;) {
			java.util.regex.Matcher m = DB_VERSION.matcher(line);
			if (m.find())
				return dbglOfDatabaseVersion(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
		}
		return "older";
	}

	/** alpha 51: the DBGL of the database in this folder, "older" if it cannot be told */
	private static String fromDatabase(File dbDir) {
		for (File f: databaseFiles(dbDir))
			if (f.getName().endsWith(".script"))
				try (java.io.BufferedReader r = Files.newBufferedReader(f.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
					return dbglOfScript(r);
				} catch (IOException | RuntimeException e) {
					return "older";
				}
		return "older";
	}

	/** alpha 51: for a backup named "...-from-older-..." of before: the DBGL of the database in it, null if it does not say */
	public static String dbglInBackup(File zip) {
		try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip)) {
			java.util.Enumeration<? extends ZipEntry> entries = z.entries();
			while (entries.hasMoreElements()) {
				ZipEntry e = entries.nextElement();
				if (e.getName().startsWith("db/") && e.getName().endsWith(".script"))
					try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(z.getInputStream(e), java.nio.charset.StandardCharsets.UTF_8))) {
						String v = dbglOfScript(r);
						return "older".equals(v) ? null: v;
					}
			}
		} catch (IOException | RuntimeException e) {
			// not readable: stays "older"
		}
		return null;
	}

	private static void addDir(ZipOutputStream out, File dir, String prefix) throws IOException {
		File[] files = dir.listFiles();
		if (files == null)
			return;
		Arrays.sort(files);
		for (File f: files)
			if (f.isDirectory())
				addDir(out, f, prefix + f.getName() + "/");
			else if (f.isFile())
				add(out, f, prefix + f.getName());
	}

	private static void add(ZipOutputStream out, File f, String name) throws IOException {
		ZipEntry e = new ZipEntry(name);
		e.setTime(f.lastModified());
		out.putNextEntry(e);
		try (OutputStream o = new java.io.FilterOutputStream(out) {
			@Override
			public void close() {
				// the zip stays open
			}
		}) {
			Files.copy(f.toPath(), o);
		}
		out.closeEntry();
	}
}
