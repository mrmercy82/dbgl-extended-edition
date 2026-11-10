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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.factory.DosboxVersionFactory;
import org.dbgl.model.factory.ProfileFactory;


/**
 * Finds the problems the main window marks with a warning sign in the list of profiles: values the DOSBox version of the
 * profile does not accept, and folders or files of the profile that are missing. Only a copy of the profile is loaded, so this
 * can run in the background. The result is kept per profile until its configuration file or that of its DOSBox version
 * changes.
 */
public final class ProfileWarnings {

	/** hints: what "Check profiles" finds that does not keep the game from starting (see {@link #STOPS_START}) */
	private record Cached(String stamp, List<String> problems, List<String> hints) {
	}

	private static final Map<Integer, Cached> CACHE = new ConcurrentHashMap<>();
	/**
	 * the result of the last check of each profile, also when it changed since or was forgotten: the list keeps showing it
	 * until the profile was checked again (before, the warning sign was gone from the moment the profile changed or "Check
	 * profiles" closed, and stayed gone when the check again did not get through)
	 */
	private static final Map<Integer, Cached> LAST = new ConcurrentHashMap<>();

	private ProfileWarnings() {
	}

	private static String stampOf(File file) {
		return file == null ? "-": file.getPath() + ':' + file.length() + ':' + file.lastModified();
	}

	/** what the result depends on: both configuration files, the DOSBox version, and the mounts and commands of the profile */
	static String stampOf(Profile profile) {
		DosboxVersion dbversion = profile.getDosboxVersion();
		return stampOf(profile.getConfigurationCanonicalFile()) + '|' + (dbversion == null ? "-": dbversion.getId() + "|" + dbversion.getFamily() + "|" + dbversion.getVersion() + "|" + stampOf(dbversion.getConfigurationCanonicalFile()))
			+ '|' + ProfileFindings.dismissedStamp(); // hidden for good in "Check profiles": not told here either
	}

	/** @return the problems found the last time, or null if the profile was not checked since it last changed */
	public static List<String> known(Profile profile) {
		Cached cached = CACHE.get(profile.getId());
		return (cached != null) && cached.stamp().equals(stampOf(profile)) ? cached.problems(): null;
	}

	/** @return the hints found the last time (see {@link Cached}), or null if the profile was not checked since it last changed */
	public static List<String> knownHints(Profile profile) {
		Cached cached = CACHE.get(profile.getId());
		return (cached != null) && cached.stamp().equals(stampOf(profile)) ? cached.hints(): null;
	}

	/** @return the problems of the last check, also if the profile changed since (see {@link #LAST}); null if never checked */
	public static List<String> lastKnown(Profile profile) {
		Cached cached = CACHE.get(profile.getId());
		if ((cached == null) || !cached.stamp().equals(stampOf(profile)))
			cached = LAST.get(profile.getId());
		return cached != null ? cached.problems(): null;
	}

	/** @return the hints of the last check, also if the profile changed since; null if never checked */
	public static List<String> lastKnownHints(Profile profile) {
		Cached cached = CACHE.get(profile.getId());
		if ((cached == null) || !cached.stamp().equals(stampOf(profile)))
			cached = LAST.get(profile.getId());
		return cached != null ? cached.hints(): null;
	}

	/** the profile is in the database, its configuration file is not there: the red warning sign */
	public static boolean confMissing(Profile profile) {
		File conf = profile.getConfigurationCanonicalFile();
		return (conf != null) && !conf.exists();
	}

	private static final Object LOCK = new Object();

	/**
	 * The DOSBox versions are shared with the rest of DBGL, and reading a configuration again resets that of its DOSBox
	 * version. A check doing that while the window draws the list or opens a profile made both fail now and then (the warning
	 * sign with the tooltip "null"). So checks use their own copies of the DOSBox versions.
	 */
	private static DosboxVersion privateCopy(DosboxVersion dbv) {
		if (dbv == null)
			return null;
		// one copy per DOSBox version and state of its configuration file: the documented values of a DOSBox configuration
		// (DOSBox-X: some 3000 lines) were parsed again for every profile checked
		java.io.File conf = dbv.getConfiguration().getFile();
		// with family and version: corrected at startup (0.82.2 -> 0.83.0), the copy of the old version told what the new one can
		String key = dbv.getId() + "|" + dbv.getFamily() + "|" + dbv.getVersion() + "|" + dbv.getExe().getPath() + "|" + (conf == null ? "": conf.getPath() + "|" + conf.lastModified() + "|" + conf.length());
		DosboxVersion cached = COPIES.get(key);
		if (cached != null)
			return cached;
		DosboxVersion copy = newCopy(dbv);
		COPIES.entrySet().removeIf(e -> e.getKey().startsWith(dbv.getId() + "|"));
		COPIES.put(key, copy);
		return copy;
	}

	/** only used while holding LOCK, one check after another */
	private static final Map<String, DosboxVersion> COPIES = new java.util.HashMap<>();

	private static DosboxVersion newCopy(DosboxVersion dbv) {
		DosboxVersion copy = DosboxVersionFactory.create(dbv.getId(), dbv.getTitle(), dbv.getFamily(), dbv.getVersion(), dbv.isDefault(), dbv.isMultiConfig(), dbv.isUsingCurses(),
			dbv.getDynamicOptions(), dbv.getPath().getPath(), dbv.getExe().getPath(), dbv.getExecutableParameters(), dbv.getConfiguration().getFile().getPath(), null, null, null, 0);
		return copy;
	}

	/**
	 * @return a short description of every problem, empty if there are none. Checks run one after another (the background
	 *         checks and the list share the DOSBox versions). A technical failure while checking (e.g. a DOSBox configuration
	 *         being reloaded at that moment) is never shown as a problem of the profile: it is tried again, and if it still
	 *         fails, the profile counts as unchecked and is checked again later.
	 */
	public static List<String> check(Profile profile) {
		List<String> known = known(profile);
		if (known != null)
			return known;
		// in the database, but its configuration file is gone (deleted, or on a drive not there now): the red sign, at once
		if (confMissing(profile)) {
			List<String> result = List.of(org.dbgl.service.TextService.getInstance().get("dialog.checkprofiles.confmissing", new Object[] {profile.getConfigurationCanonicalFile().getPath()}));
			Cached done = new Cached(stampOf(profile), result, List.of());
			CACHE.put(profile.getId(), done);
			LAST.put(profile.getId(), done);
			return result;
		}
		boolean uiThread = org.eclipse.swt.widgets.Display.getCurrent() != null;
		for (int attempt = 1;; attempt++) {
			String stamp = stampOf(profile);
			try {
				List<String> problems = new ArrayList<>(), hints = new ArrayList<>();
				synchronized (LOCK) {
					Profile copy = ProfileFactory.create(profile); // with the location of its configuration file, read again below
					copy.setDosboxVersion(privateCopy(profile.getDosboxVersion()));
					copy.resetAndLoadConfiguration();
					java.util.Set<String> keys = ProfileFindings.dismissedKeys(), kinds = ProfileFindings.dismissedKinds();
					for (String[] v: copy.listInvalidValues())
						if (!ProfileFindings.isDismissed(profile, profile.getDosboxVersion(), "invalid", new Object[] {v[0], v[1], v[2]}, keys, kinds))
							problems.add('[' + v[0] + "] " + v[1] + '=' + v[2]);
					List<ProfileChecks.Problem> checks = ProfileChecks.check(copy);
					java.util.Set<String> dead = new java.util.HashSet<>(); // fails, but a later line for the drive works: no problem
					dead.addAll(ProfileChecks.pathsToldOtherwise(copy)); // told with the cause (no quotes, "-pr")
					if (checks.stream().anyMatch(pr -> pr.key().startsWith("drivehidden"))) // told below, with what to do
						dead.add(new org.dbgl.model.FileLocation(copy.getConfiguration().getAutoexec().getMain(), org.dbgl.service.FileLocationService.getInstance().dosrootRelative()).getCanonicalFile().getPath());
					for (org.dbgl.model.conf.mount.Mount m: copy.getConfiguration().getAutoexec().deadMounts())
						if (m instanceof org.dbgl.model.conf.mount.ImageMount im)
							java.util.Arrays.stream(im.getCanonicalImgPaths()).forEach(f -> dead.add(f.getPath()));
						else if (m instanceof org.dbgl.model.conf.mount.DirMount dm)
							dead.add(dm.getCanonicalPath().getPath());
					for (String path: copy.listMissingPaths())
						if (!dead.contains(path) && !ProfileFindings.isDismissed(profile, profile.getDosboxVersion(), "missing", new Object[] {path}, keys, kinds))
							problems.add(path);
					if (copy.needsDosboxXForPc98() && !ProfileFindings.isDismissed(profile, profile.getDosboxVersion(), "pc98", new Object[0], keys, kinds))
						problems.add(org.dbgl.service.TextService.getInstance().get("dialog.profile.pc98needsdosboxx.short"));
					// what "Check profiles" finds that keeps the game from starting (not settings that just have no effect)
					for (ProfileChecks.Problem pr: checks)
						if (!ProfileFindings.isDismissed(profile, profile.getDosboxVersion(), pr.key(), pr.args(), keys, kinds))
							(STOPS_START.contains(pr.key()) ? problems: hints).add(ProfileFindings.text(org.dbgl.service.TextService.getInstance(), pr.key(), pr.args()));
					// "exit" in the [autoexec] of the configuration of the DOSBox version: DOSBox quits before the game
					if (copy.getDosboxVersion() != null)
						for (ProfileChecks.Problem pr: ProfileChecks.checkDosboxVersion(copy.getDosboxVersion()))
							if (pr.key().equals("dosboxconfexit") && !ProfileFindings.isDismissed(null, profile.getDosboxVersion(), pr.key(), pr.args(), keys, kinds))
								problems.add(ProfileFindings.text(org.dbgl.service.TextService.getInstance(), pr.key(), pr.args()));
				}
				List<String> result = List.copyOf(problems);
				Cached done = new Cached(stamp, result, List.copyOf(hints));
				CACHE.put(profile.getId(), done);
				LAST.put(profile.getId(), done);
				return result;
			} catch (Exception e) {
				System.err.println("Checking profile " + profile.getId() + " failed (attempt " + attempt + "): " + e);
				if (uiThread || (attempt >= 3)) {
					if (!uiThread)
						e.printStackTrace(); // where, for a report: the profile stays unchecked, its sign as it was
					return List.of(); // not cached: checked again later
				}
				try {
					Thread.sleep(500L * attempt);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return List.of();
				}
			}
		}
	}

	/** the problems of {@link ProfileChecks} that also get the warning sign in the list: the game or its setup does not start */
	static final java.util.Set<String> STOPS_START = java.util.Set.of("setupmissing", "altexemissing", "notreachable", "notaprogram", "windowsprogram", "win16program",
		"nothingtostart", "mountfile", "mountimage", "imagefolder", "emptyimage", "archiveimage", "cuemissing", "drivenotmounted", "confreadonly", "drivehidden5", "drivehiddencd3", "mounttwocommands", "mountnoquotes", "driveunmounted", "driveunmountedown", "drivebeforemount",
		"cdrelative", "cdrelativecustom", "startsectiondrive", "casemismatchdb3", "casemismatchprof2", "charsetmismatch", "stagingumlaut2", "xumlaut2", "stagingumlautlinux2");

	public static void forget(int profileId) {
		CACHE.remove(profileId);
	}

	/** all checked again (e.g. after something was hidden for good in "Check profiles") */
	public static void forgetAll() {
		CACHE.clear();
	}
}
