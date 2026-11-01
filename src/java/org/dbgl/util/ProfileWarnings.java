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
package org.dbgl.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.factory.ProfileFactory;


/**
 * Finds the problems the main window marks with a warning sign in the list of profiles: values the DOSBox version of the
 * profile does not accept, and folders or files of the profile that are missing. Only a copy of the profile is loaded, so this
 * can run in the background. The result is kept per profile until its configuration file or that of its DOSBox version
 * changes.
 */
public final class ProfileWarnings {

	private record Cached(String stamp, List<String> problems) {
	}

	private static final Map<Integer, Cached> CACHE = new ConcurrentHashMap<>();

	private ProfileWarnings() {
	}

	private static String stampOf(File file) {
		return file == null ? "-": file.getPath() + ':' + file.length() + ':' + file.lastModified();
	}

	/** what the result depends on: both configuration files, the DOSBox version, and the mounts and commands of the profile */
	static String stampOf(Profile profile) {
		DosboxVersion dbversion = profile.getDosboxVersion();
		return stampOf(profile.getConfigurationCanonicalFile()) + '|' + (dbversion == null ? "-": dbversion.getId() + "|" + stampOf(dbversion.getConfigurationCanonicalFile()));
	}

	/** @return the problems found the last time, or null if the profile was not checked since it last changed */
	public static List<String> known(Profile profile) {
		Cached cached = CACHE.get(profile.getId());
		return (cached != null) && cached.stamp().equals(stampOf(profile)) ? cached.problems(): null;
	}

	/** @return a short description of every problem, empty if there are none */
	public static List<String> check(Profile profile) {
		List<String> known = known(profile);
		if (known != null)
			return known;
		String stamp = stampOf(profile);
		List<String> problems = new ArrayList<>();
		try {
			Profile copy = ProfileFactory.create(profile); // with the location of its configuration file, read again below
			copy.resetAndLoadConfiguration();
			for (String[] v: copy.listInvalidValues())
				problems.add('[' + v[0] + "] " + v[1] + '=' + v[2]);
			for (String path: copy.listMissingPaths())
				problems.add(path);
		} catch (Exception e) {
			problems.add(String.valueOf(e.getMessage()));
		}
		List<String> result = List.copyOf(problems);
		CACHE.put(profile.getId(), new Cached(stamp, result));
		return result;
	}

	public static void forget(int profileId) {
		CACHE.remove(profileId);
	}
}
