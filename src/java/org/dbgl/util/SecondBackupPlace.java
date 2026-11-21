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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.SettingsService;


/**
 * alpha 52: a second place for backups, e.g. a folder synchronised by OneDrive or Dropbox: the backups of the saved games
 * and the automatic backup are copied there as well (in a folder "DBGL" in it), so that they survive the loss of the
 * computer. Only copies: the backups in DBGL's own folders stay as they are; there the same number is kept as here.
 */
public final class SecondBackupPlace {

	public static final String SECTION = "backup";
	/** the folder chosen (empty: no second place) */
	public static final String PLACE = "secondplace";
	/** whether the backups of the saved games are copied there, and the automatic backups */
	public static final String SAVEGAMES = "secondsavegames", AUTO = "secondauto";
	/** the folder in the second place that holds everything of DBGL */
	public static final String ROOT = "DBGL";

	private SecondBackupPlace() {
	}

	/** @return the folder DBGL writes into, or null when there is no second place or it is not there (e.g. a drive not connected) */
	public static File root() {
		String place = SettingsService.getInstance().getValue(SECTION, PLACE);
		if ((place == null) || place.isBlank())
			return null;
		File dir = new File(place.trim());
		return dir.isDirectory() ? new File(dir, ROOT): null;
	}

	/** the folder of the backups of the saved games of a profile there: by its number and its title, readable for people */
	public static File savegamesDir(File root, Profile p) {
		return new File(new File(root, "savegames"), p.getId() + "-" + safe(p.getTitle()));
	}

	/** the folder of the automatic backups there */
	public static File autoDir(File root) {
		return new File(root, "backups");
	}

	/** a name that every file system takes: letters, digits, space, - and _ only, at most 60 characters */
	public static String safe(String title) {
		String s = title == null ? "": title.replaceAll("[^\\p{L}\\p{N} _-]", "").replaceAll("\\s+", " ").trim();
		if (s.length() > 60)
			s = s.substring(0, 60).trim();
		return s.isEmpty() ? "profile": s;
	}

	/** a backup of the saved games, just made, copied there (if set); the older ones there beyond keep are removed */
	public static void savegameBackupMade(Profile p, File zip, int keep) {
		if (!SettingsService.getInstance().getBooleanValue(SECTION, SAVEGAMES))
			return;
		File root = root();
		if (root != null)
			copyAndPrune(zip, savegamesDir(root, p), keep, f -> f.getName().toLowerCase(Locale.ROOT).endsWith(".zip"));
	}

	/** an automatic backup, just made, copied there (if set) */
	public static void autoBackupMade(File zip, int keep) {
		if (!SettingsService.getInstance().getBooleanValue(SECTION, AUTO))
			return;
		File root = root();
		if (root != null)
			copyAndPrune(zip, autoDir(root), keep, f -> f.getName().startsWith("dbgl-backup-") && f.getName().endsWith(".zip"));
	}

	/** a problem there (full, offline) never stops the game or DBGL: it is only written into the log */
	public static void copyAndPrune(File zip, File dir, int keep, Predicate<File> ours) {
		try {
			Files.createDirectories(dir.toPath());
			File part = new File(dir, zip.getName() + ".part");
			Files.copy(zip.toPath(), part.toPath(), StandardCopyOption.REPLACE_EXISTING);
			Files.move(part.toPath(), new File(dir, zip.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
			File[] all = dir.listFiles(f -> f.isFile() && ours.test(f));
			if (all != null) {
				Arrays.sort(all, Comparator.comparing(File::getName).reversed());
				for (int i = Math.max(1, keep); i < all.length; i++)
					Files.deleteIfExists(all[i].toPath());
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("Second backup place " + dir + ": " + e);
		}
	}

	/** the backups of the saved games of a profile in the second place, newest first (e.g. to restore one on a new computer) */
	public static List<File> savegameBackups(Profile p) {
		File root = root();
		if (root == null)
			return List.of();
		File[] files = savegamesDir(root, p).listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".zip"));
		if (files == null)
			return List.of();
		List<File> list = new ArrayList<>(Arrays.asList(files));
		list.sort(Comparator.comparing(File::getName).reversed());
		return list;
	}

	/**
	 * the folders of OneDrive and Dropbox on this computer, by name ("OneDrive", "OneDrive - Firma", "Dropbox"), as their
	 * programs set them up; none if there are none
	 */
	public static Map<String, File> cloudFolders() {
		return cloudFolders(System.getenv(), new File(System.getProperty("user.home")));
	}

	public static Map<String, File> cloudFolders(Map<String, String> env, File home) {
		Map<String, File> result = new LinkedHashMap<>();
		for (String var: new String[] {"OneDrive", "OneDriveConsumer", "OneDriveCommercial"}) {
			String path = env.get(var);
			if ((path != null) && !path.isBlank())
				addIfThere(result, new File(path.trim()));
		}
		for (String appData: new String[] {env.get("LOCALAPPDATA"), env.get("APPDATA")}) // Dropbox writes where it syncs into info.json
			if (appData != null)
				dropboxInfo(result, new File(new File(appData, "Dropbox"), "info.json"));
		dropboxInfo(result, new File(new File(home, ".dropbox"), "info.json"));
		File[] inHome = home.listFiles(f -> f.isDirectory() && (f.getName().equals("Dropbox") || f.getName().startsWith("Dropbox (") || f.getName().equals("OneDrive")
				|| f.getName().startsWith("OneDrive - ")));
		if (inHome != null) {
			Arrays.sort(inHome);
			for (File f: inHome)
				addIfThere(result, f);
		}
		return result;
	}

	private static void dropboxInfo(Map<String, File> result, File info) {
		if (!info.isFile() || (info.length() > 64 * 1024))
			return;
		try {
			org.json.JSONObject json = new org.json.JSONObject(Files.readString(info.toPath()));
			for (String kind: new String[] {"personal", "business"}) {
				org.json.JSONObject o = json.optJSONObject(kind);
				if ((o != null) && !o.optString("path").isBlank())
					addIfThere(result, new File(o.optString("path")));
			}
		} catch (IOException | RuntimeException e) {
			// not readable: only the folders in the home folder then
		}
	}

	private static void addIfThere(Map<String, File> result, File dir) {
		if (!dir.isDirectory())
			return;
		File key = dir.getAbsoluteFile();
		if (result.values().stream().anyMatch(f -> f.getAbsoluteFile().equals(key)))
			return;
		String name = dir.getName();
		for (int i = 2; result.containsKey(name); i++)
			name = dir.getName() + " (" + i + ")";
		result.put(name, key);
	}
}
