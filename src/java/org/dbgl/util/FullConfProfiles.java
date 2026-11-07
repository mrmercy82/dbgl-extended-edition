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

import java.util.List;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.SettingsService;


/**
 * Optional, per profile: a profile keeps all settings of its DOSBox version in its own configuration file, as D-Fend Reloaded
 * does, instead of only those that differ from the DOSBox configuration (the DBGL way). The profile says so with a marker line
 * (Configuration.ALL_SETTINGS_MARKER); the setting here is only the default for new profiles. Off by default, because such a
 * profile no longer follows changes to the DOSBox configuration or new defaults of a newer DOSBox version.
 */
public final class FullConfProfiles {

	public static final String SECTION = "profiles", KEY = "allsettings";

	/** settings DBGL writes for each profile itself */
	static final java.util.Set<String> SKIPPED = java.util.Set.of("dosbox/captures", "capture/capture_dir");

	private FullConfProfiles() {
	}

	public static boolean enabled() {
		return SettingsService.getInstance().getBooleanValue(SECTION, KEY);
	}

	public static boolean skipped(String section, String item) {
		return SKIPPED.contains(section.toLowerCase() + '/' + item.toLowerCase());
	}

	/** number of "item=value" lines in a configuration file, without [autoexec] (used by the tests) */
	static int countInFile(java.io.File file) {
		int n = 0;
		try {
			for (String line: java.nio.file.Files.readAllLines(file.toPath())) {
				String l = line.trim();
				if (l.equalsIgnoreCase("[autoexec]"))
					break;
				if (!l.startsWith("#") && !l.startsWith("[") && l.contains("="))
					n++;
			}
		} catch (Exception e) {
			// unreadable: nothing to compare
		}
		return n;
	}

	/** number of settings, without [autoexec] */
	public static int count(org.dbgl.model.conf.Settings conf) {
		int n = 0;
		for (String section: conf.getSectionNames())
			n += conf.getItemNames(section).length;
		return n;
	}

	/**
	 * Write all settings into all profiles (complete = true), or take out again those equal to their DOSBox configuration
	 * (complete = false).
	 * 
	 * @return {profiles changed, profiles that failed}
	 */
	public static int[] applyToAll(boolean complete) throws java.sql.SQLException {
		List<DosboxVersion> versions = new DosboxVersionRepository().listAll();
		int changed = 0, failed = 0;
		for (Profile p: new ProfileRepository().list("", "", versions)) {
			try {
				p.resetAndLoadConfiguration();
				if (p.keepsAllSettings() == complete)
					continue; // already as wanted
				p.setKeepsAllSettings(complete);
				p.saveConfiguration();
				changed++;
			} catch (Exception e) {
				e.printStackTrace();
				failed++;
			}
		}
		return new int[] {changed, failed};
	}
}
