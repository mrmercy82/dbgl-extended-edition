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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.FileLocationService;


/**
 * Earlier states of the configuration file of each profile. Whenever DBGL saves a profile and its configuration changed, the
 * previous file is kept in profiles/history/&lt;profile id&gt;/, the newest {@value #KEEP} of them. Besides, the state before
 * DBGL changed the file the first time is kept as {@value #ORIGINAL} and never pushed out (alpha 46).
 */
public final class ProfileHistory {

	public static final int KEEP = 10;
	public static final String ORIGINAL = "original.conf";
	private static final String STAMP = "yyyyMMdd-HHmmss-SSS";

	private ProfileHistory() {
	}

	public static File dirOf(int profileId) {
		return new File(new File(FileLocationService.getInstance().getProfilesDir(), "history"), String.valueOf(profileId));
	}

	/** @return the current content of the configuration file, or null if there is none yet */
	public static byte[] read(Profile profile) {
		try {
			File file = profile.getConfigurationCanonicalFile();
			return (file != null) && file.isFile() ? Files.readAllBytes(file.toPath()): null;
		} catch (IOException | RuntimeException e) {
			return null; // e.g. a copy of a profile without a configuration file of its own
		}
	}

	/** keep the previous content if the file changed when it was saved (the DOSBox version stayed the same) */
	public static void recordIfChanged(Profile profile, byte[] before) {
		recordIfChanged(profile, before, idAndTitle(profile.getDosboxVersion()));
	}

	/**
	 * alpha 53: keep the previous content if the file changed when it was saved, and the DOSBox version the profile had with it,
	 * so that restoring it brings back that version too (if it is still there)
	 *
	 * @param versionBefore the id and title of the DOSBox version before saving, null if not known
	 */
	public static void recordIfChanged(Profile profile, byte[] before, String[] versionBefore) {
		if ((before == null) || (profile.getId() < 0))
			return;
		byte[] after = read(profile);
		if (Arrays.equals(before, after))
			return;
		try {
			File dir = dirOf(profile.getId());
			dir.mkdirs();
			keepOriginal(profile.getId(), before, versionBefore);
			File now = new File(dir, new SimpleDateFormat(STAMP).format(new Date()) + ".conf");
			// a version with exactly this content is kept already (e.g. after applying and undoing a change): it is moved to
			// now instead of keeping it twice, so that trying something out does not push the older versions out
			File same = null;
			for (File f: list(profile.getId()))
				if ((f.length() == before.length) && Arrays.equals(Files.readAllBytes(f.toPath()), before)) {
					same = f;
					break;
				}
			if (same != null) {
				Files.move(same.toPath(), now.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.deleteIfExists(versionFile(same).toPath());
			} else {
				Files.write(now.toPath(), before);
			}
			writeVersion(now, versionBefore);
			List<File> versions = list(profile.getId());
			for (int i = KEEP; i < versions.size(); i++) {
				Files.deleteIfExists(versions.get(i).toPath());
				Files.deleteIfExists(versionFile(versions.get(i)).toPath());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * the first state DBGL changed: the oldest kept version if there are some already (kept before alpha 46), else the state
	 * before this change
	 */
	private static void keepOriginal(int profileId, byte[] before, String[] versionBefore) throws IOException {
		File original = original(profileId);
		if (original != null)
			return;
		List<File> versions = list(profileId);
		byte[] oldest = versions.isEmpty() ? before: Files.readAllBytes(versions.get(versions.size() - 1).toPath());
		File file = new File(dirOf(profileId), ORIGINAL);
		Files.write(file.toPath(), oldest);
		if (versions.isEmpty()) // the state before this change: with the DOSBox version of then
			writeVersion(file, versionBefore);
		if (!versions.isEmpty())
			file.setLastModified(dateOf(versions.get(versions.size() - 1)).getTime());
	}

	/** @return the state before DBGL changed the file the first time, or null */
	public static File original(int profileId) {
		File file = new File(dirOf(profileId), ORIGINAL);
		return file.isFile() ? file: null;
	}

	/** @return the kept versions, newest first */
	public static List<File> list(int profileId) {
		// only the versions (named by their date): not e.g. switched.conf in the same folder (see SwitchMemory)
		File[] files = dirOf(profileId).listFiles((d, n) -> n.endsWith(".conf") && n.matches("\\d{8}-\\d{6}-\\d{3}\\.conf"));
		List<File> result = new ArrayList<>(files == null ? List.of(): Arrays.asList(files));
		result.sort(Comparator.comparing(File::getName).reversed());
		return result;
	}

	/** @return when the version was replaced, from its file name */
	public static Date dateOf(File version) {
		try {
			return new SimpleDateFormat(STAMP).parse(version.getName().substring(0, STAMP.length()));
		} catch (Exception e) {
			return new Date(version.lastModified());
		}
	}

	/** alpha 53: the DOSBox version a kept state went with: next to it, "&lt;name&gt;.dosbox" with its id and title */
	static File versionFile(File version) {
		return new File(version.getParentFile(), version.getName().replaceFirst("\\.conf$", "") + ".dosbox");
	}

	/** the id and title of a DOSBox version, null for none */
	public static String[] idAndTitle(org.dbgl.model.aggregate.DosboxVersion db) {
		return db == null ? null: new String[] {String.valueOf(db.getId()), db.getTitle()};
	}

	private static void writeVersion(File version, String[] db) {
		try {
			if (db == null)
				Files.deleteIfExists(versionFile(version).toPath());
			else
				Files.writeString(versionFile(version).toPath(), db[0] + "\n" + db[1] + "\n", java.nio.charset.StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** alpha 53: the DOSBox version a kept state went with (id and title), null if not known (kept before alpha 53) */
	public static String[] versionOf(File version) {
		try {
			File f = versionFile(version);
			if (!f.isFile())
				return null;
			String[] lines = Files.readString(f.toPath(), java.nio.charset.StandardCharsets.UTF_8).split("\\R");
			return (lines.length >= 2) && lines[0].strip().matches("\\d+") ? new String[] {lines[0].strip(), lines[1].strip()}: null;
		} catch (IOException e) {
			return null;
		}
	}

	public static void deleteAll(int profileId) {
		File[] files = dirOf(profileId).listFiles();
		if (files != null)
			for (File f: files)
				f.delete();
		dirOf(profileId).delete();
	}
}
