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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.service.SettingsService;


/**
 * Alpha 53: the "primary" configuration of DOSBox Staging in the folder of the user (on Windows
 * %LOCALAPPDATA%\DOSBox\dosbox-staging.conf, made by Staging at its first start). Staging reads it before the files DBGL
 * gives it with -conf (TEST21: "CONFIG: Loaded primary config file ..."): what the configuration of the DOSBox version and the
 * profile do not set comes from there, and its [autoexec] runs too, without DBGL showing any of it. It can be left out at the
 * start (-noprimaryconf, a setting of DBGL), and "Check profiles" tells what of it counts. DOSBox-X has nothing like it: with
 * -conf it reads only the files it is given (TEST21, its log without -conf: only the file next to its program).
 */
public final class StagingPrimaryConf {

	private StagingPrimaryConf() {
	}

	public static boolean isStaging(DosboxVersion db) {
		if (db == null)
			return false;
		GenerationAwareConfigurable.Generation gen = GenerationAwareConfigurable.determineGen(db);
		return (gen != null)
				&& (GenerationAwareConfigurable.familyIdx(gen) == GenerationAwareConfigurable.familyIdx(GenerationAwareConfigurable.Generation.staging_0830));
	}

	/** DBGL starts this DOSBox version without the primary configuration (the setting, for DOSBox Staging only) */
	public static boolean skipped(DosboxVersion db) {
		return isStaging(db) && SettingsService.getInstance().getBooleanValue("dosbox", "noprimaryconf");
	}

	/** where DOSBox Staging keeps its primary configuration on this system */
	public static File file() {
		if (SystemUtils.IS_OS_WINDOWS) {
			String local = System.getenv("LOCALAPPDATA");
			return StringUtils.isBlank(local) ? null: new File(new File(local, "DOSBox"), "dosbox-staging.conf");
		}
		String home = System.getProperty("user.home");
		if (SystemUtils.IS_OS_MAC)
			return new File(home, "Library/Preferences/DOSBox/dosbox-staging.conf");
		String xdg = System.getenv("XDG_CONFIG_HOME");
		return new File(StringUtils.isBlank(xdg) ? new File(home, ".config"): new File(xdg), "dosbox/dosbox-staging.conf");
	}

	/** what of the primary configuration counts: settings the other files do not set ("[section] item = value"), lines of its [autoexec] */
	public record Counts(List<String> settings, List<String> autoexec) {
		public boolean any() {
			return !settings.isEmpty() || !autoexec.isEmpty();
		}
	}

	/**
	 * @param primary the text of the primary configuration
	 * @param others the texts of the files given with -conf (the DOSBox version, the profile): what they set wins
	 */
	public static Counts counts(String primary, List<String> others) {
		Map<String, String[]> own = ConfTakeover.settings(primary);
		java.util.Set<String> set = new java.util.HashSet<>();
		for (String o: others)
			set.addAll(ConfTakeover.settings(o).keySet());
		// the defaults of the version that wrote the file: a value equal to it changes nothing
		String first = StringUtils.defaultString(primary).lines().findFirst().orElse("");
		Map<String, String> defaults = ConfTakeover.shipped(ConfTakeover.listFor(first));
		List<String> settings = new ArrayList<>();
		for (Map.Entry<String, String[]> e: own.entrySet()) {
			if (set.contains(e.getKey()))
				continue;
			String d = defaults.get(e.getKey());
			if ((d != null) && ConfTakeover.same(d, e.getValue()[2]))
				continue;
			if (defaults.isEmpty() && StringUtils.isBlank(e.getValue()[2]))
				continue;
			settings.add("[" + e.getValue()[0] + "] " + e.getValue()[1] + " = " + e.getValue()[2]);
		}
		List<String> autoexec = new ArrayList<>();
		boolean in = false;
		for (String raw: StringUtils.defaultString(primary).split("\\R")) {
			String line = raw.strip();
			if (line.startsWith("[") && line.endsWith("]")) {
				in = line.equalsIgnoreCase("[autoexec]");
				continue;
			}
			if (in && !line.isEmpty() && !line.startsWith("#"))
				autoexec.add(line);
		}
		return new Counts(settings, autoexec);
	}

	/** the note of "Check profiles" for a DOSBox version: args title, file, what counts; null if nothing of it counts */
	public static ProfileChecks.Problem hint(DosboxVersion db) {
		if (!isStaging(db) || skipped(db))
			return null;
		File primary = file();
		if ((primary == null) || !primary.isFile())
			return null;
		try {
			File own = db.getConfigurationCanonicalFile();
			if ((own != null) && own.getCanonicalFile().equals(primary.getCanonicalFile()))
				return null; // the DOSBox version uses this very file
			String text = ConfTakeover.read(primary);
			List<String> others = new ArrayList<>();
			if ((own != null) && own.isFile())
				others.add(ConfTakeover.read(own));
			Counts c = counts(text, others);
			if (!c.any())
				return null;
			List<String> all = new ArrayList<>(c.settings());
			c.autoexec().forEach(l -> all.add("[autoexec] " + l));
			return new ProfileChecks.Problem("stagingprimaryconf", db.getTitle(), primary.getPath(),
				String.join(", ", all.subList(0, Math.min(12, all.size()))) + (all.size() > 12 ? ", ...": ""));
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}
}
