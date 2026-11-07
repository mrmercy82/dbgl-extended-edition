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
 * previous file is kept in profiles/history/&lt;profile id&gt;/, the newest {@value #KEEP} of them.
 */
public final class ProfileHistory {

	public static final int KEEP = 5;
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

	/** keep the previous content if the file changed when it was saved */
	public static void recordIfChanged(Profile profile, byte[] before) {
		if ((before == null) || (profile.getId() < 0))
			return;
		byte[] after = read(profile);
		if (Arrays.equals(before, after))
			return;
		try {
			File dir = dirOf(profile.getId());
			dir.mkdirs();
			Files.write(new File(dir, new SimpleDateFormat(STAMP).format(new Date()) + ".conf").toPath(), before);
			List<File> versions = list(profile.getId());
			for (int i = KEEP; i < versions.size(); i++)
				Files.deleteIfExists(versions.get(i).toPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** @return the kept versions, newest first */
	public static List<File> list(int profileId) {
		File[] files = dirOf(profileId).listFiles((d, n) -> n.endsWith(".conf"));
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

	public static void deleteAll(int profileId) {
		File[] files = dirOf(profileId).listFiles();
		if (files != null)
			for (File f: files)
				f.delete();
		dirOf(profileId).delete();
	}
}
