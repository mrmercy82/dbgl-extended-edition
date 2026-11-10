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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.FileLocation;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.ImageMount;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ITextService;
import org.dbgl.service.SettingsService;


/**
 * What "Check profiles" finds, one entry per profile and problem (or per DOSBox version), each with the text shown, its kind
 * (to be hidden for all profiles), its identity (to be hidden just there) and, where DBGL can do it, its own solution. A
 * solution is applied to a profile read again, so the same one serves for the preview (on a copy) and for the profile.
 */
public final class ProfileFindings {

	/**
	 * A solution: on a profile just read (all of it is written then, as DBGL saves a profile: see rewrite), or only the lines it
	 * is about in the text of the configuration file (everything else stays as it is, also values the same as in the
	 * configuration of the DOSBox version, which saving would leave out).
	 */
	public interface Fix {
		void apply(Profile prof, List<DosboxVersion> versions);

		/** @param prof the profile, read (to know e.g. the new name of a setting) */
		void edit(Profile prof, ConfText text);
	}

	/** a solution that only changes lines of the file (DBGL does not read them, e.g. "fullscreen true"): also before a rewrite */
	private static final class TextFix implements Fix {
		private final java.util.function.BiConsumer<Profile, ConfText> inText_;

		TextFix(java.util.function.BiConsumer<Profile, ConfText> inText) {
			inText_ = inText;
		}

		@Override
		public void apply(Profile prof, List<DosboxVersion> versions) {
			// done in the file before the profile is read (see applyTextFixes)
		}

		@Override
		public void edit(Profile prof, ConfText text) {
			inText_.accept(prof, text);
		}
	}

	/** the solutions that change lines of the file only, applied to its text (raw bytes as ISO-8859-1) */
	private static String applyTextFixes(Profile prof, List<Finding> chosen, String raw) {
		if (chosen.stream().noneMatch(f -> f.fix() instanceof TextFix))
			return raw;
		ConfText text = ConfText.of(raw, fileCharset(prof));
		for (Finding f: chosen)
			if (f.fix() instanceof TextFix)
				f.fix().edit(prof, text);
		return text.text();
	}

	private static Fix fix(java.util.function.BiConsumer<Profile, List<DosboxVersion>> onProfile, java.util.function.BiConsumer<Profile, ConfText> inText) {
		return new Fix() {
			@Override
			public void apply(Profile prof, List<DosboxVersion> versions) {
				onProfile.accept(prof, versions);
			}

			@Override
			public void edit(Profile prof, ConfText text) {
				inText.accept(prof, text);
			}
		};
	}

	/**
	 * @param profile   null for one of a DOSBox version
	 * @param version   the DOSBox version (of the profile, or the one it is about)
	 * @param kind      e.g. "invalid", "missing", or the key of a problem of ProfileChecks
	 * @param args      what identifies it within its kind (the path, the setting, the line)
	 * @param text      as shown
	 * @param hint      only for information (e.g. a program the DOSBox configuration starts)
	 * @param solution  key of what the solution does (dialog.checkprofiles.fix.*), null if none
	 * @param fix       the solution, null if DBGL cannot solve it
	 * @param rewrite   solved by writing the profile again at all (short names, "cd \\", order of lines): any saving does it
	 */
	public record Finding(Profile profile, DosboxVersion version, String kind, Object[] args, String text, boolean hint, String solution, Fix fix, boolean rewrite) {

		public boolean fixable() {
			return fix != null;
		}

		/** the key under which it is hidden for good, just this one */
		public String dismissKey() {
			return ProfileFindings.dismissKey(profile, version, kind, args);
		}
	}

	/** what was found: for the DOSBox versions, per profile, and what the other buttons of the dialog work with */
	public record Result(List<Finding> versions, Map<Profile, List<Finding>> profiles, Map<Profile, List<String>> missing, Map<Profile, List<Integer>> missingLinks,
			Map<Profile, String> dosboxX) {
	}

	public static final String SETTINGS_SECTION = "checkprofiles";
	public static final String DISMISSED = "dismissed", DISMISSED_KINDS = "dismissedkinds";

	/** the texts without "Clean up" (a button no longer there): the kind stays (what is hidden for good stays hidden) */
	private static final Map<String, String> TEXTS = Map.of("cdrelative", "cdrelative2", "drivebeforemount", "drivebeforemount2", "longdosnames", "longdosnames2",
		"othersetting2", "othersetting3", "deadmount2", "deadmount3", "wrongsection", "wrongsection2", "wrongsectiondup", "wrongsectiondup2", "renamedsetting",
		"renamedsetting2", "removedsetting", "removedsetting2");

	/** the text of a kind of problem */
	public static String text(ITextService text, String kind, Object[] args) {
		return text.get("dialog.checkprofiles." + TEXTS.getOrDefault(kind, kind), args);
	}

	/** what ProfileChecks finds that is only for information */
	private static final Set<String> HINTS = Set.of("gusnopatches", "cohortoptional", "lurecm32l");

	private static final Fix REWRITE = fix((p, v) -> {
		// nothing to change: saving writes it as DOSBox needs it
	}, (p, t) -> {
		throw new UnsupportedOperationException("written again as a whole");
	});

	private ProfileFindings() {
	}

	/** the key of one finding: the profile (by its id) or the configuration of the DOSBox version, its kind and its arguments */
	public static String dismissKey(Profile profile, DosboxVersion version, String kind, Object[] args) {
		String owner = profile != null ? "p" + profile.getId(): "v" + (version == null ? "": version.getConfigurationCanonicalFile().getPath());
		String id = owner + "|" + kind + "|" + Arrays.stream(args).map(String::valueOf).collect(Collectors.joining("|"));
		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return Long.toHexString(crc.getValue()) + Integer.toHexString(id.hashCode());
	}

	public static Set<String> dismissedKeys() {
		return set(DISMISSED);
	}

	public static Set<String> dismissedKinds() {
		return set(DISMISSED_KINDS);
	}

	private static Set<String> set(String item) {
		return new LinkedHashSet<>(Arrays.asList(StringUtils.split(StringUtils.defaultString(SettingsService.getInstance().getValue(SETTINGS_SECTION, item)))));
	}

	/** for the list's warning sign: what was hidden for good is not told there either */
	public static String dismissedStamp() {
		return StringUtils.defaultString(SettingsService.getInstance().getValue(SETTINGS_SECTION, DISMISSED)) + "#"
			+ StringUtils.defaultString(SettingsService.getInstance().getValue(SETTINGS_SECTION, DISMISSED_KINDS));
	}

	public static boolean isDismissed(Profile profile, DosboxVersion version, String kind, Object[] args, Set<String> keys, Set<String> kinds) {
		return kinds.contains(kind) || keys.contains(dismissKey(profile, version, kind, args));
	}

	/** hide for good: just these findings (byKind false) or all of their kinds, everywhere */
	public static void dismiss(java.util.Collection<Finding> findings, boolean byKind) throws IOException {
		Set<String> s = byKind ? dismissedKinds(): dismissedKeys();
		for (Finding f: findings)
			s.add(byKind ? f.kind(): f.dismissKey());
		SettingsService.getInstance().setValue(SETTINGS_SECTION, byKind ? DISMISSED_KINDS: DISMISSED, String.join(" ", s));
		SettingsService.getInstance().save();
	}

	/** all hidden for good are shown again */
	public static void undismissAll() throws IOException {
		SettingsService.getInstance().setValue(SETTINGS_SECTION, DISMISSED, "");
		SettingsService.getInstance().setValue(SETTINGS_SECTION, DISMISSED_KINDS, "");
		SettingsService.getInstance().save();
	}

	/**
	 * Checks the profiles (each read again) and their DOSBox versions.
	 *
	 * @return all that was found, also what is hidden for good (the dialog leaves that out and counts it)
	 */
	public static Result collect(List<Profile> profiles, List<DosboxVersion> versions, ITextService text) {
		return collect(profiles, profiles, versions, text);
	}

	/**
	 * @param all all profiles (to know which share a configuration file); only those of profiles are checked, and the DOSBox
	 *            versions only if all are
	 */
	public static Result collect(List<Profile> profiles, List<Profile> all, List<DosboxVersion> versions, ITextService text) {
		return collect(profiles, all, versions, text, null);
	}

	/** @param progress told the number of profiles checked so far, after each one (null: not told) */
	public static Result collect(List<Profile> profiles, List<Profile> all, List<DosboxVersion> versions, ITextService text, java.util.function.IntConsumer progress) {
		int done = 0;
		List<Finding> forVersions = new ArrayList<>();
		Map<Profile, List<Finding>> byProfile = new LinkedHashMap<>();
		Map<Profile, List<String>> missing = new LinkedHashMap<>();
		Map<Profile, List<Integer>> missingLinks = new LinkedHashMap<>();
		Map<Profile, String> dosboxX = new LinkedHashMap<>();

		// the DOSBox versions first, once each: without the program or its configuration no profile with it starts
		Map<Integer, Long> uses = profiles.stream().filter(p -> p.getDosboxVersion() != null).collect(
			Collectors.groupingBy(p -> p.getDosboxVersion().getId(), LinkedHashMap::new, Collectors.counting()));
		for (DosboxVersion db: versions) {
			if (!uses.containsKey(db.getId()) || (profiles != all))
				continue;
			String affected = text.get("dialog.checkprofiles.dosboxprofiles", new Object[] {uses.get(db.getId())});
			for (ProfileChecks.Problem pr: ProfileChecks.checkDosboxVersion(db))
				forVersions.add(new Finding(null, db, pr.key(), pr.args(), text.get("dialog.checkprofiles." + pr.key(), pr.args()) + " " + affected, false, null, null, false));
			// a program its configuration starts before every game (wanted, e.g. a mouse driver, or not)
			for (ProfileChecks.Problem pr: ProfileChecks.dosboxVersionHints(db))
				forVersions.add(new Finding(null, db, pr.key(), new Object[] {pr.args()[2]}, text.get("dialog.checkprofiles." + pr.key(), pr.args()) + " " + affected, true, null, null, false));
		}

		Map<Profile, List<String>> shared = ProfileChecks.sharedConfigurations(all);
		for (Profile prof: profiles) {
			List<Finding> list = new ArrayList<>();
			java.util.function.BiConsumer<String, Object[]> told = (kind, args) -> list.add(plain(prof, kind, args, text.get("dialog.checkprofiles." + kind, args)));
			if (shared.containsKey(prof))
				told.accept("sharedconf", new Object[] {prof.getConfigurationCanonicalFile().getPath(), String.join(", ", shared.get(prof))});
			try {
				String warnings = prof.resetAndLoadConfiguration();
				Autoexec autoexec = prof.getConfiguration().getAutoexec();
				for (String[] v: prof.listInvalidValues()) {
					String what = (v.length > 4) && "shader".equals(v[4]) ? text.get("dialog.checkprofiles.shadermissing"): text.get("dialog.checkprofiles.invalid", new Object[] {v[3]});
					String section = v[0], item = v[1];
					String near = (v.length > 4) ? null: nearValue(v[2], v[3]);
					if (near != null) // e.g. cputype=386_slow for DOSBox Staging: 386 (what DBGL writes when switching to it), not removed
						list.add(new Finding(prof, prof.getDosboxVersion(), "invalid", new Object[] {v[0], v[1], v[2]},
							"[" + v[0] + "] " + v[1] + "=" + v[2] + ": " + what + ". " + text.get("dialog.checkprofiles.invalid.near", new Object[] {near}), false,
							"setnear", fix((p, vs) -> p.getConfiguration().setValue(section, item, near), (p, t) -> t.setRaw(section, item, near)), false));
					else
						list.add(new Finding(prof, prof.getDosboxVersion(), "invalid", new Object[] {v[0], v[1], v[2]}, "[" + v[0] + "] " + v[1] + "=" + v[2] + ": " + what, false,
							"removevalue", fix((p, vs) -> p.getConfiguration().removeValue(section, item), (p, t) -> t.remove(section, item)), false));
				}
				List<ProfileChecks.Problem> problems = new ArrayList<>(ProfileChecks.check(prof, versions));
				// settings below a heading without "]": DBGL reads them into the section before; told with the heading (repaired, they
				// are where they belong), not as being in the wrong section
				Set<String> belowBroken = problems.stream().filter(pr -> pr.key().equals("syntaxheader") && (pr.args().length > 3))
						.flatMap(pr -> Arrays.stream(String.valueOf(pr.args()[3]).split("\\|"))).collect(Collectors.toSet());
				problems.removeIf(pr -> pr.key().startsWith("wrongsection") && (pr.args().length > 0) && belowBroken.contains(String.valueOf(pr.args()[0])));
				List<String> paths = new ArrayList<>(prof.listMissingPaths());
				paths.removeAll(ProfileChecks.pathsToldOtherwise(prof)); // told with the cause (no quotes, "-pr")
				// the game on the profile's drive that DOSBox skips: told with what to do, not as missing (nothing to look for)
				if (problems.stream().anyMatch(pr -> pr.key().startsWith("drivehidden")))
					paths.remove(new FileLocation(autoexec.getMain(), FileLocationService.getInstance().dosrootRelative()).getCanonicalFile().getPath());
				// a line that fails while a later one for its drive works: nothing to look for, the line goes
				for (Mount m: autoexec.deadMounts()) {
					String line = m.toString();
					list.add(new Finding(prof, prof.getDosboxVersion(), "deadmount2", new Object[] {String.valueOf(Character.toUpperCase(m.getDrive())), line, ProfileChecks.missingOf(m)},
						text(text, "deadmount2", new Object[] {String.valueOf(Character.toUpperCase(m.getDrive())), line, ProfileChecks.missingOf(m)}), false,
						"removeline", fix((p, vs) -> removeMounts(p.getConfiguration().getAutoexec(), p.getConfiguration().getAutoexec().deadMounts(), line),
							(p, t) -> t.removeMountLines(line, false)), false));
					if (m instanceof ImageMount im)
						for (File f: im.getCanonicalImgPaths())
							paths.remove(f.getPath());
					else if (m instanceof DirMount dm)
						paths.remove(dm.getCanonicalPath().getPath());
				}
				// mounted by the configuration of the DOSBox version: to be fixed there, "Find missing folders" leaves it alone
				Set<String> byDosbox = ProfileChecks.dosboxMountPaths(prof);
				for (String path: paths)
					if (byDosbox.contains(path))
						told.accept("missingindosbox2", new Object[] {path, prof.getDosboxVersion().getTitle(), prof.getDosboxVersion().getConfigurationCanonicalFile().getName()});
				paths.removeIf(byDosbox::contains);
				if (!paths.isEmpty())
					missing.put(prof, paths);
				for (String path: paths)
					told.accept("missing", new Object[] {path});
				List<Integer> links = prof.listMissingLinks();
				if (!links.isEmpty())
					missingLinks.put(prof, links);
				for (int i: links)
					told.accept("missinglink", new Object[] {prof.getLinks()[i].getDisplayTitle(), Profile.linkFile(prof.getLinks()[i]).getPath()});
				// the same mount line more than once (e.g. left by repairing paths in alpha 45 and before)
				Map<String, Integer> times = new LinkedHashMap<>();
				autoexec.repeatedMounts().forEach(m -> times.merge(m.toString(), 1, Integer::sum));
				times.forEach((line, n) -> list.add(new Finding(prof, prof.getDosboxVersion(), "repeatedmount", new Object[] {line, n},
					text.get("dialog.checkprofiles.repeatedmount", new Object[] {line, n}), false, "removeline",
					fix((p, vs) -> removeMounts(p.getConfiguration().getAutoexec(), p.getConfiguration().getAutoexec().repeatedMounts(), line),
						(p, t) -> t.removeMountLines(line, true)), false)));
				for (Mount m: autoexec.mountsOfDrivesMountedAlready())
					told.accept("drivetwice", new Object[] {String.valueOf(m.getDrive()), m.toString()});
				// whatever else keeps it from starting or working (ProfileChecks), solved where DBGL can do it
				for (ProfileChecks.Problem pr: problems)
					list.add(ofProblem(prof, pr, text));
				// "seems incomplete" says less than what was found: left out then
				Set<String> driveCauses = Set.of("drivenotmounted", "drivebeforemount", "driveunmounted", "driveunmountedown", "startsectiondrive");
				boolean mountTold = problems.stream().anyMatch(pr -> pr.key().startsWith("mount") || driveCauses.contains(pr.key()));
				if (mountTold || problems.stream().anyMatch(pr -> pr.key().equals("nothingtostart") || pr.key().equals("notreachable")))
					warnings = warnings.replace(text.get("general.error.profileincomplete", new Object[] {prof.getConfigurationCanonicalFile().getPath()}), "");
				if (mountTold) // the same as "drive not mounted", or its cause
					warnings = warnings.replace("autoexec: invalid drive change\n", "");
				// the same as the warning symbol in the list says (ProfileWarnings): a PC-98 image needs DOSBox-X
				if (prof.needsDosboxXForPc98())
					list.add(plain(prof, "pc98", new Object[0], text.get("dialog.profile.pc98needsdosboxx.short")));
				// also a CD on the IDE bus, or booting a hard disk image (e.g. Windows 95): better with DOSBox-X
				String reason = prof.dosboxXReason();
				if (reason != null) {
					dosboxX.put(prof, reason);
					if (!"pc98".equals(reason))
						told.accept("dosboxx", new Object[] {text.get("dialog.storeimport.reason." + reason)});
				}
				// a line DOSBox cannot read: told above with its solution (ConfLines), not again as the warning of reading it
				List<String> syntaxLines = problems.stream().filter(pr -> pr.key().startsWith("syntax")).map(pr -> "\"" + pr.args()[0] + "\"").toList();
				for (String w: StringUtils.defaultString(warnings).split("\\R"))
					if (StringUtils.isNotBlank(w) && syntaxLines.stream().noneMatch(w::contains))
						list.add(plain(prof, "warning", new Object[] {w.trim()}, "! " + w.trim()));
			} catch (IOException e) {
				File conf = prof.getConfigurationCanonicalFile();
				if ((conf != null) && !conf.exists()) // only in the database: told as the warning sign in the list tells it
					list.add(plain(prof, "confmissing", new Object[] {conf.getPath()}, text.get("dialog.checkprofiles.confmissing", new Object[] {conf.getPath()})));
				else
					list.add(plain(prof, "error", new Object[] {StringUtils.defaultString(e.getMessage())}, "! " + e.getMessage()));
			}
			if (!list.isEmpty())
				byProfile.put(prof, list);
			if (progress != null)
				progress.accept(++done);
		}
		return new Result(forVersions, byProfile, missing, missingLinks, dosboxX);
	}

	private static Finding plain(Profile prof, String kind, Object[] args, String text) {
		return new Finding(prof, prof.getDosboxVersion(), kind, args, text, false, null, null, false);
	}

	/** a problem of ProfileChecks, with the solution DBGL has for it */
	private static Finding ofProblem(Profile prof, ProfileChecks.Problem pr, ITextService text) {
		String shown = text(text, pr.key(), pr.args());
		String[] setting = pr.args().length > 0 ? setting(String.valueOf(pr.args()[0])): null;
		String solution = null;
		Fix fix = null;
		boolean rewrite = false;
		switch (pr.key()) {
			case "renamedsetting", "removedsetting" -> {
				solution = pr.key().equals("removedsetting") ? "removevalue": "rename";
				fix = fix((p, vs) -> ProfileChecks.applyStagingChange(p, setting[0], setting[1]), (p, t) -> editStagingChange(p, t, setting[0], setting[1]));
			}
			case "wrongsection", "wrongsectiondup" -> {
				solution = pr.key().equals("wrongsection") ? "move": "removevalue"; // a value there already: the wrong line goes
				fix = fix((p, vs) -> ProfileChecks.applyMisplaced(p, setting[0], setting[1]), (p, t) -> editMisplaced(p, t, setting[0], setting[1]));
			}
			case "othersetting2" -> {
				solution = "removevalue";
				fix = fix((p, vs) -> p.getConfiguration().removeValue(setting[0], setting[1]), (p, t) -> t.remove(setting[0], setting[1]));
			}
			case "boolwordstaging", "boolwordold" -> {
				solution = "convert";
				String written = String.valueOf(pr.args()[2]);
				fix = fix((p, vs) -> p.getConfiguration().setValue(setting[0], setting[1], written), (p, t) -> t.set(setting[0], setting[1], written));
			}
			case "charsetmismatch" -> {
				// the bytes in the charset the DOSBox version reads (nothing else changes)
				solution = "recode";
				java.nio.charset.Charset to = java.nio.charset.Charset.forName(String.valueOf(pr.args()[1]));
				fix = fix((p, vs) -> ConfCharset.writeIn(p, to), (p, t) -> t.recode(to));
			}
			// lines of the file itself (ConfLines): solved in its text
			case "syntaxheadertext" -> {
				String raw = String.valueOf(pr.args()[1]), repaired = String.valueOf(pr.args()[2]);
				solution = "repair";
				fix = new TextFix((p, t) -> t.replaceLine(raw, repaired));
			}
			case "syntaxnoequals", "syntaxheader" -> {
				String raw = String.valueOf(pr.args()[pr.key().equals("syntaxheader") ? 1: 3]), repaired = String.valueOf(pr.args()[pr.key().equals("syntaxheader") ? 2: 4]);
				solution = repaired.isEmpty() ? "removeline": "repair";
				fix = new TextFix((p, t) -> t.replaceLine(raw, repaired.isEmpty() ? null: repaired));
			}
			case "syntaxnosection" -> {
				String target = String.valueOf(pr.args()[1]), raw = String.valueOf(pr.args()[2]), item = String.valueOf(pr.args()[3]), value = String.valueOf(pr.args()[4]);
				solution = target.isEmpty() ? "removeline": "move";
				fix = new TextFix((p, t) -> {
					t.replaceLine(raw, null);
					if (!target.isEmpty() && (t.get(target, item) == null))
						t.setRaw(target, item, value);
				});
			}
			case "duplicatekey" -> {
				String section = String.valueOf(pr.args()[0]), item = String.valueOf(pr.args()[1]);
				solution = "removeearlier";
				fix = new TextFix((p, t) -> t.removeFirst(section, item)); // the first one: the last one counts
			}
			case "outofrange" -> {
				String section = String.valueOf(pr.args()[0]), item = String.valueOf(pr.args()[1]), to = String.valueOf(pr.args()[5]);
				solution = "clamp";
				fix = new TextFix((p, t) -> t.setRaw(section, item, to));
			}
			case "notinversion" -> {
				String raw = String.valueOf(pr.args()[3]), replacement = String.valueOf(pr.args()[4]);
				solution = replacement.isEmpty() ? "removeline": "removeoption";
				fix = new TextFix((p, t) -> t.replaceLine(raw, replacement.isEmpty() ? null: replacement));
			}
			case "cdquote" -> {
				String raw = String.valueOf(pr.args()[0]), repaired = String.valueOf(pr.args()[1]);
				solution = "removequote";
				fix = new TextFix((p, t) -> t.replaceLine(raw, repaired));
			}
			case "cdonz" -> {
				String driveLine = String.valueOf(pr.args()[1]);
				List<String> cds = List.of(String.valueOf(pr.args()[3]).split("\n"));
				solution = "movebehinddrive";
				fix = new TextFix((p, t) -> t.moveBehind(cds, driveLine));
			}
			case "longdosnames", "cdrelative", "drivebeforemount" -> {
				solution = "rewrite";
				fix = REWRITE;
				rewrite = true;
			}
			default -> {
				// told only: DBGL cannot know what is right (a missing file, a Windows program, ...)
			}
		}
		if ((fix != null) && (fix != REWRITE) && (setting == null) && !pr.key().equals("charsetmismatch") && !(fix instanceof TextFix))
			fix = null;
		boolean hint = HINTS.contains(pr.key()); // only for information: no warning sign, nothing to solve
		return new Finding(prof, prof.getDosboxVersion(), pr.key(), pr.args(), shown, hint, solution, fix, rewrite);
	}

	/**
	 * The value of another DOSBox the version knows under a shorter name: "386_slow", "486_slow", "pentium_slow" of DOSBox 0.74
	 * are "386", "486", "pentium" for DOSBox Staging (as switching the profile to it writes them).
	 *
	 * @param allowed the values of the version, separated by ", "
	 * @return that value, or null if there is none
	 */
	public static String nearValue(String value, String allowed) {
		String v = StringUtils.defaultString(value).trim().toLowerCase(java.util.Locale.ROOT);
		List<String> values = Arrays.asList(StringUtils.defaultString(allowed).split(",\\s*"));
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([a-z0-9]+)_(slow|fast|prefetch)$").matcher(v);
		if (m.matches() && values.contains(m.group(1)))
			return m.group(1);
		return null;
	}

	/** "[sblaster] oplemu" as {sblaster, oplemu}, or null; the names of DOSBox-X have spaces ("[dos] minimum mcb free", "[ide, secondary] enable") */
	public static String[] setting(String s) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\[([^\\]]+)\\]\\s+([^=]*[^=\\s])").matcher(s);
		return m.find() ? new String[] {m.group(1), m.group(2)}: null;
	}

	/** the mounts of the list that are written as this line (e.g. all repeated ones of it) leave the profile */
	static void removeMounts(Autoexec autoexec, List<Mount> of, String line) {
		List<Mount> gone = of.stream().filter(m -> m.toString().equals(line)).toList();
		autoexec.setMountingpoints(new ArrayList<>(autoexec.getMountingpoints().stream().filter(m -> gone.stream().noneMatch(g -> g == m)).toList()));
	}

	/** solved by writing the profile again as a whole (as DBGL saves it), not just by changing the lines concerned */
	public static boolean needsRewrite(List<Finding> chosen) {
		return chosen.stream().anyMatch(f -> f.fixable() && f.rewrite());
	}

	/**
	 * The configuration file as the solutions write it: only the lines they are about changed, or (needsRewrite) a copy of the
	 * profile, read again, the solutions applied, saved to a file of its own, exactly as saving the profile writes it.
	 *
	 * @return {before, after}, the text of the configuration file
	 */
	public static String[] preview(Profile prof, List<Finding> chosen, List<DosboxVersion> versions) throws IOException {
		File own = prof.getConfigurationCanonicalFile();
		String before = own.isFile() ? java.nio.file.Files.readString(own.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1): "";
		Profile copy = org.dbgl.model.factory.ProfileFactory.create(prof); // with its id and the location of its configuration file
		copy.resetAndLoadConfiguration();
		if (!needsRewrite(chosen)) {
			ConfText text = ConfText.of(before, fileCharset(copy));
			for (Finding f: chosen)
				if (f.fixable())
					f.fix().edit(copy, text);
			return new String[] {before, text.text()};
		}
		// lines DBGL does not read, repaired in the text first (as apply does it), then read as the profile
		String edited = applyTextFixes(copy, chosen, before);
		File source = own;
		File tmp0 = null;
		if (!edited.equals(before)) {
			tmp0 = File.createTempFile("dbglpreview", ".conf", own.getParentFile()); // beside it: paths relative to its folder stay right
			java.nio.file.Files.writeString(tmp0.toPath(), edited, java.nio.charset.StandardCharsets.ISO_8859_1);
			copy.getConfiguration().setFileLocation(new FileLocation(tmp0.getPath()));
			copy.resetAndLoadConfiguration();
			copy.getConfiguration().setFileLocation(new FileLocation(own.getPath())); // captures and mapper relative to its own folder
			source = tmp0;
		}
		for (Finding f: chosen)
			if (f.fixable())
				f.fix().apply(copy, versions);
		// as updating the profile does (ProfileRepository.update): relative to the folder of its own configuration file, so before
		// the copy is written elsewhere
		copy.setCapturesInConfig();
		copy.updateMapperFileInConfigByIdentifiers();
		File tmp = File.createTempFile("dbglpreview", ".conf");
		try {
			copy.getConfiguration().setFileLocation(new FileLocation(tmp.getPath()));
			copy.getConfiguration().keepForeignLinesFrom(source); // its comments, as saving keeps them
			copy.saveConfiguration();
			return new String[] {before, java.nio.file.Files.readString(tmp.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1)};
		} finally {
			java.nio.file.Files.deleteIfExists(tmp.toPath());
			if (tmp0 != null)
				java.nio.file.Files.deleteIfExists(tmp0.toPath());
		}
	}

	/** the charset the configuration file of the profile was read with (see {@link org.dbgl.model.conf.Configuration#getCharset()}) */
	public static java.nio.charset.Charset fileCharset(Profile prof) {
		java.nio.charset.Charset cs = prof.getConfiguration().getCharset();
		return cs != null ? cs: java.nio.charset.Charset.defaultCharset();
	}

	/** a text from {@link #preview} (bytes as ISO-8859-1) readable, in the charset of the file: an umlaut shown as one */
	public static String readable(String raw, Profile prof) {
		return readable(raw, fileCharset(prof));
	}

	public static String readable(String raw, java.nio.charset.Charset cs) {
		return new String(raw.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), cs);
	}

	/** the charset the file is in once the chosen solutions are applied (another one if its charset is changed) */
	public static java.nio.charset.Charset charsetAfter(Profile prof, List<Finding> chosen) {
		for (Finding f: chosen)
			if (f.fixable() && f.kind().equals("charsetmismatch"))
				return java.nio.charset.Charset.forName(String.valueOf(f.args()[1]));
		return fileCharset(prof);
	}

	/** the solutions applied to the profile itself: its lines changed in the file, or (needsRewrite) the profile saved as a whole */
	public static void apply(Profile prof, List<Finding> chosen, List<DosboxVersion> versions) throws IOException, java.sql.SQLException {
		apply(prof, chosen, versions, false);
	}

	/** @param toUtf8 a file written as a whole is written in the charset its DOSBox version reads (see {@link ConfCharset}) */
	public static void apply(Profile prof, List<Finding> chosen, List<DosboxVersion> versions, boolean toUtf8) throws IOException, java.sql.SQLException {
		File conf = prof.getConfigurationCanonicalFile();
		if (conf.isFile() && !conf.canWrite()) // nothing changed, and said why
			throw new IOException(org.dbgl.service.TextService.getInstance().get("general.error.confreadonly", new Object[] {conf.getPath()}));
		if (!needsRewrite(chosen)) {
			String[] ba = preview(prof, chosen, versions);
			byte[] before = ProfileHistory.read(prof);
			java.nio.file.Files.writeString(prof.getConfigurationCanonicalFile().toPath(), ba[1], java.nio.charset.StandardCharsets.ISO_8859_1);
			ProfileHistory.recordIfChanged(prof, before); // as saving the profile does: the state before can be brought back
			return;
		}
		// lines DBGL does not read, repaired in a copy of the file; the profile is read from it and written as a whole (the file
		// itself unchanged until then, so that its history has the state before)
		File own = prof.getConfigurationCanonicalFile();
		File repaired = null;
		try {
			if (own.isFile() && chosen.stream().anyMatch(f -> f.fix() instanceof TextFix)) {
				String raw = java.nio.file.Files.readString(own.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1);
				repaired = File.createTempFile("dbglrepaired", ".conf", own.getParentFile()); // beside it: paths relative to its folder stay right
				java.nio.file.Files.writeString(repaired.toPath(), applyTextFixes(prof, chosen, raw), java.nio.charset.StandardCharsets.ISO_8859_1);
				prof.getConfiguration().setFileLocation(new FileLocation(repaired.getPath()));
				prof.resetAndLoadConfiguration();
				prof.getConfiguration().setFileLocation(new FileLocation(own.getPath()));
				prof.getConfiguration().keepForeignLinesFrom(repaired);
			} else {
				prof.resetAndLoadConfiguration();
			}
			applyRewrite(prof, chosen, versions, toUtf8);
		} finally {
			prof.getConfiguration().keepForeignLinesFrom(null);
			if (repaired != null)
				java.nio.file.Files.deleteIfExists(repaired.toPath());
		}
	}

	private static void applyRewrite(Profile prof, List<Finding> chosen, List<DosboxVersion> versions, boolean toUtf8) throws IOException, java.sql.SQLException {
		if (toUtf8) {
			ConfCharset.Mismatch m = ConfCharset.mismatch(prof, prof.getDosboxVersion());
			if (m != null)
				ConfCharset.writeIn(prof, m.needed());
		}
		for (Finding f: chosen)
			if (f.fixable())
				f.fix().apply(prof, versions);
		new org.dbgl.model.repository.ProfileRepository().update(prof);
	}

	/** a deprecated setting, as applyStagingChange does it, in the text */
	static void editStagingChange(Profile prof, ConfText text, String section, String item) {
		for (ProfileChecks.StagingChange ch: ProfileChecks.stagingChanges(prof)) {
			ProfileChecks.OldSetting o = ch.old();
			if (!o.section().equalsIgnoreCase(section) || !o.item().equalsIgnoreCase(item) || o.kind().equals("manual"))
				continue;
			if ((ch.newValue() != null) && !ch.newValue().isEmpty() && (text.get(o.newSection(), o.newItem()) == null))
				text.set(o.newSection(), o.newItem(), ch.newValue());
			text.remove(o.section(), o.item());
			return;
		}
	}

	/** a setting in the wrong section, as applyMisplaced does it, in the text */
	static void editMisplaced(Profile prof, ConfText text, String section, String item) {
		for (ProfileChecks.Misplaced mp: ProfileChecks.misplacedSettings(prof)) {
			if (!mp.section().equalsIgnoreCase(section) || !mp.item().equalsIgnoreCase(item) || (mp.targets().size() != 1))
				continue;
			String value = text.get(mp.section(), mp.item());
			if ((mp.alreadySet() == null) && (text.get(mp.targets().get(0), mp.item()) == null))
				if (value != null)
					text.setRaw(mp.targets().get(0), mp.item(), value); // as it is in the file
				else
					text.set(mp.targets().get(0), mp.item(), StringUtils.defaultString(mp.value()));
			text.remove(mp.section(), mp.item());
			return;
		}
	}

	/** the text of a configuration file, changed line by line (with its line ends kept) */
	public static final class ConfText {
		private final List<String> lines_;
		private final String eol_;
		private final boolean endsWithEol_;
		private java.nio.charset.Charset charset_ = java.nio.charset.StandardCharsets.ISO_8859_1;

		private ConfText(String text) {
			eol_ = text.contains("\r\n") ? "\r\n": "\n";
			endsWithEol_ = text.endsWith("\n");
			lines_ = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
			if (endsWithEol_)
				lines_.remove(lines_.size() - 1);
		}

		public static ConfText of(String text) {
			return new ConfText(text);
		}

		/**
		 * @param text    the bytes of the file, each as one character (ISO-8859-1): written again as they are
		 * @param charset the charset of the file, for values that are set (an umlaut written as the file has it)
		 */
		public static ConfText of(String text, java.nio.charset.Charset charset) {
			ConfText result = new ConfText(text);
			result.charset_ = charset;
			return result;
		}

		public String text() {
			return String.join(eol_, lines_) + (endsWithEol_ ? eol_: "");
		}

		/** as DOSBox reads it: up to the first "]" (text after it does not count), the name not trimmed ("[ dosbox ]" is no [dosbox]) */
		private static String header(String line) {
			String t = line.trim();
			int end = t.indexOf(']');
			return t.startsWith("[") && (end > 0) ? t.substring(1, end): null;
		}

		/** the line of the setting (in the first section of that name that has it), or -1 */
		/** the line of the setting that counts: the last one (as DOSBox and DBGL read it; before, the first, found by review) */
		private int find(String section, String item) {
			return find(section, item, false);
		}

		private int find(String section, String item, boolean first) {
			String current = null;
			int found = -1;
			for (int i = 0; i < lines_.size(); i++) {
				String h = header(lines_.get(i));
				if (h != null) {
					current = h;
					continue;
				}
				String t = lines_.get(i).trim();
				int eq = t.indexOf('=');
				if ((current != null) && current.equalsIgnoreCase(section) && !current.equalsIgnoreCase("autoexec") && !t.startsWith("#") && (eq > 0)
						&& t.substring(0, eq).trim().equalsIgnoreCase(item)) {
					if (first)
						return i;
					found = i;
				}
			}
			return found;
		}

		public String get(String section, String item) {
			int i = find(section, item);
			return i < 0 ? null: lines_.get(i).substring(lines_.get(i).indexOf('=') + 1).trim();
		}

		/** the line of the setting gone; a section left without any setting goes too (with its empty lines), as saving does it */
		public boolean remove(String section, String item) {
			return removeAt(find(section, item));
		}

		/** the first line of a setting that is there more than once (the last one counts) */
		public boolean removeFirst(String section, String item) {
			return removeAt(find(section, item, true));
		}

		private boolean removeAt(int i) {
			if (i < 0)
				return false;
			lines_.remove(i);
			int start = i - 1;
			while ((start >= 0) && (header(lines_.get(start)) == null))
				start--;
			int end = i;
			while ((end < lines_.size()) && (header(lines_.get(end)) == null))
				end++;
			if ((start >= 0) && !header(lines_.get(start)).equalsIgnoreCase("autoexec")
					&& lines_.subList(start + 1, end).stream().allMatch(l -> l.trim().isEmpty()))
				lines_.subList(start, end).clear();
			return true;
		}

		/** all of the text in another charset (each character as it was, in the bytes of the other one) */
		public void recode(java.nio.charset.Charset to) {
			for (int i = 0; i < lines_.size(); i++)
				lines_.set(i, new String(new String(lines_.get(i).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), charset_).getBytes(to), java.nio.charset.StandardCharsets.ISO_8859_1));
			charset_ = to;
		}

		/** the charset of the file (after {@link #recode}, the new one) */
		public java.nio.charset.Charset charset() {
			return charset_;
		}

		/** the value set: in its line, else after the last line of its section, else in a new section before [autoexec] */
		public void set(String section, String item, String value) {
			setRaw(section, item, new String(value.getBytes(charset_), java.nio.charset.StandardCharsets.ISO_8859_1));
		}

		/** like {@link #set}, the value as it is in a file of the same charset (e.g. from {@link #get}) */
		public void setRaw(String section, String item, String value) {
			int i = find(section, item);
			if (i >= 0) {
				lines_.set(i, item + "=" + value);
				return;
			}
			int last = -1, autoexec = -1;
			String current = null;
			for (int j = 0; j < lines_.size(); j++) {
				String h = header(lines_.get(j));
				if (h != null) {
					current = h;
					if (h.equalsIgnoreCase(section) && (last < 0))
						last = j;
					if (h.equalsIgnoreCase("autoexec") && (autoexec < 0))
						autoexec = j;
				} else if ((current != null) && current.equalsIgnoreCase(section) && !lines_.get(j).trim().isEmpty()) {
					last = j;
				}
			}
			if (last >= 0) {
				lines_.add(last + 1, item + "=" + value);
				return;
			}
			int at = autoexec >= 0 ? autoexec: lines_.size();
			List<String> add = new ArrayList<>(List.of("[" + section + "]", item + "=" + value, ""));
			if ((at > 0) && !lines_.get(at - 1).trim().isEmpty())
				add.add(0, "");
			lines_.addAll(at, add);
		}

		/** the first line that is this one (trimmed, as in the file) replaced, or removed (replacement null); false if not there */
		public boolean replaceLine(String raw, String replacement) {
			for (int i = 0; i < lines_.size(); i++)
				if (lines_.get(i).trim().equals(raw.trim())) {
					if (replacement == null)
						lines_.remove(i);
					else
						lines_.set(i, replacement);
					return true;
				}
			return false;
		}

		/** as the line runs: without "@" and spaces around it */
		private static String command(String line) {
			String t = line.trim();
			return t.startsWith("@") ? t.substring(1).trim(): t;
		}

		/**
		 * The lines of [autoexec] (each the first one that is it, before the anchor) moved right behind the first line after them
		 * that is the anchor, in their order; nothing moved if the anchor is not there.
		 */
		public void moveBehind(List<String> moved, String anchor) {
			int start = -1, end = lines_.size();
			for (int i = 0; i < lines_.size(); i++) {
				String h = header(lines_.get(i));
				if (h != null) {
					if (start >= 0) {
						end = i;
						break;
					}
					if (h.equalsIgnoreCase("autoexec"))
						start = i + 1;
				}
			}
			if (start < 0)
				return;
			List<Integer> found = new ArrayList<>();
			int next = 0, at = -1;
			for (int i = start; i < end; i++) {
				String c = command(lines_.get(i));
				if ((next < moved.size()) && c.replace("\"", "").equals(command(moved.get(next)).replace("\"", ""))) { // also with its quote removed already
					found.add(i);
					next++;
				} else if ((next > 0) && c.equalsIgnoreCase(command(anchor))) {
					at = i;
					break;
				}
			}
			if ((at < 0) || found.isEmpty())
				return;
			List<String> lines = new ArrayList<>();
			for (int k = found.size() - 1; k >= 0; k--)
				lines.add(0, lines_.remove((int)found.get(k)));
			lines_.addAll(at - found.size() + 1, lines);
		}

		/**
		 * The mount lines of [autoexec] (outside DBGL's own sections) that DBGL reads as this line: all of them, or (keepFirst)
		 * all but the first.
		 */
		public void removeMountLines(String mount, boolean keepFirst) {
			List<Integer> found = new ArrayList<>();
			boolean in = false, custom = false;
			for (int i = 0; i < lines_.size(); i++) {
				String h = header(lines_.get(i));
				if (h != null) {
					in = h.equalsIgnoreCase("autoexec");
					continue;
				}
				String t = lines_.get(i).trim();
				if (t.startsWith("@"))
					t = t.substring(1).trim();
				if (in && t.matches("(?i)^REM\\s+/?(?:START|PRE-LAUNCH|POST-LAUNCH|FINISH)\\s*$")) {
					custom = !t.contains("/");
					continue;
				}
				if (!in || custom || !t.toLowerCase(java.util.Locale.ROOT).matches("^(img)?mount\\s.*"))
					continue;
				try {
					if (org.dbgl.model.factory.MountFactory.create(t).toString().equals(mount))
						found.add(i);
				} catch (Exception e) {
					// not a mount DBGL reads
				}
			}
			if (keepFirst && !found.isEmpty())
				found.remove(0);
			for (int k = found.size() - 1; k >= 0; k--)
				lines_.remove((int)found.get(k));
		}
	}

	/** a line of a difference: ' ' the same (only the section it is in), '-' gone, '+' new */
	public record DiffLine(char kind, String text) {
	}

	/**
	 * What differs: the settings as "[section] item=value" (a setting moved to another section is one gone, one new), then the
	 * lines of [autoexec] that differ (compared line by line, longest common part), after a line "[autoexec]".
	 */
	public static List<DiffLine> diff(String before, String after) {
		return diff(before, after, null);
	}

	/**
	 * In the order of the file: each section, under it its lines that differ (a setting moved to another section is gone in the
	 * one, new in the other), [autoexec] last (compared line by line). A section no longer in the file is told as gone ('-'),
	 * one new in it as new ('+').
	 *
	 * @param db its configuration: a value that is gone but the same there is marked '~' (DBGL writes only what differs; the
	 *           same for DOSBox), told after the rest, also under its section
	 */
	public static List<DiffLine> diff(String before, String after, DosboxVersion db) {
		return diff(before, after, db, false);
	}

	/**
	 * @param full all of the file: also the lines that stay (' '), the values only gone as the same in the DOSBox configuration
	 *             ('~') where they are, after a note ('!') first
	 */
	public static List<DiffLine> diff(String before, String after, DosboxVersion db, boolean full) {
		Map<String, Map<String, String>> a = sections(before), b = sections(after);
		List<String> order = new ArrayList<>(a.keySet());
		b.keySet().stream().filter(k -> !order.contains(k)).forEach(order::add);
		Map<String, String> names = new LinkedHashMap<>();
		for (String text: new String[] {after, before})
			sectionNames(text).forEach(names::put);
		List<DiffLine> result = new ArrayList<>(), same = new ArrayList<>();
		// comment lines ("# ..."): writing the profile as a whole drops them, which is told too
		Map<String, List<String>> ca = comments(before), cb = comments(after);
		result.addAll(commentDiff(ca.getOrDefault("", List.of()), cb.getOrDefault("", List.of()), full)); // above the first section
		for (String sec: order) {
			Map<String, String> sa = a.getOrDefault(sec, Map.of()), sb = b.getOrDefault(sec, Map.of());
			List<DiffLine> lines = new ArrayList<>(commentDiff(ca.getOrDefault(sec, List.of()), cb.getOrDefault(sec, List.of()), full)), sameLines = new ArrayList<>();
			for (Map.Entry<String, String> e: sa.entrySet()) {
				String key = "[" + names.get(sec) + "] " + e.getKey();
				String now = valueOf(sb, e.getKey());
				if ((now == null) && (db != null) && sameAsDosbox(db, key, e.getValue()))
					(full ? lines: sameLines).add(new DiffLine('~', e.getKey() + "=" + e.getValue()));
				else if (full && e.getValue().equals(now))
					lines.add(new DiffLine(' ', e.getKey() + "=" + e.getValue()));
				else if (now == null)
					lines.add(new DiffLine('-', e.getKey() + "=" + e.getValue()));
				else if (!now.equals(e.getValue())) {
					lines.add(new DiffLine('-', e.getKey() + "=" + e.getValue()));
					lines.add(new DiffLine('+', e.getKey() + "=" + now));
				}
			}
			for (Map.Entry<String, String> e: sb.entrySet())
				if (valueOf(sa, e.getKey()) == null)
					lines.add(new DiffLine('+', e.getKey() + "=" + e.getValue()));
			// the section itself: gone (no longer in the file), new, or there before and after
			char header = !b.containsKey(sec) ? '-': !a.containsKey(sec) ? '+': ' ';
			if (!lines.isEmpty()) {
				result.add(new DiffLine(header, "[" + names.get(sec) + "]"));
				result.addAll(lines);
			}
			if (!sameLines.isEmpty()) {
				same.add(new DiffLine(lines.isEmpty() && (header == '-') ? '-': '~', "[" + names.get(sec) + "]")); // red once, where told first
				same.addAll(sameLines);
			}
		}
		List<DiffLine> lines = diffLines(autoexec(before), autoexec(after), full);
		if (!lines.isEmpty() || (full && (before.toLowerCase(java.util.Locale.ROOT).contains("[autoexec]") || after.toLowerCase(java.util.Locale.ROOT).contains("[autoexec]")))) {
			result.add(new DiffLine(' ', "[autoexec]"));
			result.addAll(lines);
		}
		if (full && result.stream().anyMatch(d -> d.kind() == '~')) // told with a note first
			result.add(0, new DiffLine('!', ""));
		if (!same.isEmpty())
			result.add(new DiffLine('!', ""));
		result.addAll(same);
		return result;
	}

	/** comment lines gone ('-'), new ('+') and (full) kept (' ') */
	private static List<DiffLine> commentDiff(List<String> a, List<String> b, boolean full) {
		List<DiffLine> result = new ArrayList<>();
		List<String> left = new ArrayList<>(b);
		for (String c: a)
			if (left.remove(c)) {
				if (full)
					result.add(new DiffLine(' ', c));
			} else {
				result.add(new DiffLine('-', c));
			}
		left.forEach(c -> result.add(new DiffLine('+', c)));
		return result;
	}

	/** the comment lines of each section but [autoexec] (by its name in lower case, "" above the first section) */
	private static Map<String, List<String>> comments(String text) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		String section = "";
		for (String raw: text.split("\\R")) {
			String line = raw.trim();
			if (line.startsWith("[") && line.endsWith("]"))
				section = line.substring(1, line.length() - 1).trim().toLowerCase(java.util.Locale.ROOT);
			else if (!section.equals("autoexec") && (line.startsWith("#") || line.startsWith(";")) && (line.length() > 1))
				result.computeIfAbsent(section, k -> new ArrayList<>()).add(line);
		}
		return result;
	}

	/** the value of an item, its name in any case */
	private static String valueOf(Map<String, String> section, String item) {
		for (Map.Entry<String, String> e: section.entrySet())
			if (e.getKey().equalsIgnoreCase(item))
				return e.getValue();
		return null;
	}

	/** the sections but [autoexec], in the order of the file (by their name in lower case), each with its items and values */
	private static Map<String, Map<String, String>> sections(String text) {
		Map<String, Map<String, String>> result = new LinkedHashMap<>();
		String section = null;
		for (String raw: text.split("\\R")) {
			String line = raw.trim();
			if (line.startsWith("[") && line.endsWith("]")) {
				section = line.substring(1, line.length() - 1).trim().toLowerCase(java.util.Locale.ROOT);
				if (!section.equals("autoexec"))
					result.computeIfAbsent(section, k -> new LinkedHashMap<>());
			} else if ((section != null) && !section.equals("autoexec") && !line.startsWith("#") && (line.indexOf('=') > 0)) {
				String item = line.substring(0, line.indexOf('=')).trim();
				Map<String, String> items = result.get(section);
				if (valueOf(items, item) == null)
					items.put(item, line.substring(line.indexOf('=') + 1).trim());
			}
		}
		return result;
	}

	/** the name of each section as written */
	private static Map<String, String> sectionNames(String text) {
		Map<String, String> result = new LinkedHashMap<>();
		for (String raw: text.split("\\R")) {
			String line = raw.trim();
			if (line.startsWith("[") && line.endsWith("]")) {
				String name = line.substring(1, line.length() - 1).trim();
				result.putIfAbsent(name.toLowerCase(java.util.Locale.ROOT), name);
			}
		}
		return result;
	}

	/** "[sdl] fullscreen" with this value in the configuration of the DOSBox version too */
	private static boolean sameAsDosbox(DosboxVersion db, String key, String value) {
		String[] sec = setting(key);
		if ((sec == null) || !db.getConfiguration().hasValue(sec[0], sec[1]))
			return false;
		return StringUtils.trim(db.getConfiguration().getValue(sec[0], sec[1])).equalsIgnoreCase(StringUtils.trim(value));
	}

	/** the lines of [autoexec], without empty ones */
	private static List<String> autoexec(String text) {
		List<String> result = new ArrayList<>();
		boolean in = false;
		for (String raw: text.split("\\R")) {
			String line = raw.trim();
			if (line.startsWith("[") && line.endsWith("]"))
				in = line.equalsIgnoreCase("[autoexec]");
			else if (in && !line.isEmpty())
				result.add(raw.replaceAll("\\s+$", ""));
		}
		return result;
	}

	/** the lines that differ ('-' gone, '+' new), in the order of the file; gone ones first where both */
	public static List<DiffLine> diffLines(List<String> a, List<String> b) {
		return diffLines(a, b, false);
	}

	/** lines that stay shown around a change of the [autoexec], so that it can be told where it is (e.g. a line moved) */
	static final int CONTEXT = 2;

	/** the line between changes far apart, of lines that stay and are not shown */
	public static final String GAP = "\u2026";

	/**
	 * @param full also the lines that stay (' '); otherwise only those up to {@link #CONTEXT} lines around a change, with
	 *             {@link #GAP} where lines are left out between them
	 */
	static List<DiffLine> diffLines(List<String> a, List<String> b, boolean full) {
		List<DiffLine> all = allDiffLines(a, b);
		if (full || all.stream().allMatch(d -> d.kind() == ' '))
			return full ? all: List.of();
		boolean[] shown = new boolean[all.size()];
		for (int k = 0; k < all.size(); k++)
			if (all.get(k).kind() != ' ')
				for (int c = Math.max(0, k - CONTEXT); c <= Math.min(all.size() - 1, k + CONTEXT); c++)
					shown[c] = true;
		List<DiffLine> result = new ArrayList<>();
		for (int k = 0; k < all.size(); k++) {
			if (shown[k])
				result.add(all.get(k));
			else if ((k > 0) && shown[k - 1] && (k < all.size() - 1) && java.util.stream.IntStream.range(k + 1, all.size()).anyMatch(x -> shown[x]))
				result.add(new DiffLine(' ', GAP));
		}
		return result;
	}

	/** every line: gone ('-'), new ('+') or staying (' ') */
	private static List<DiffLine> allDiffLines(List<String> a, List<String> b) {
		int n = a.size(), m = b.size();
		boolean full = true;
		List<DiffLine> result = new ArrayList<>();
		if ((long)n * m > 4_000_000L) { // very long: told as all gone, all new
			a.forEach(l -> result.add(new DiffLine('-', l)));
			b.forEach(l -> result.add(new DiffLine('+', l)));
			return result;
		}
		int[][] lcs = new int[n + 1][m + 1];
		for (int i = n - 1; i >= 0; i--)
			for (int j = m - 1; j >= 0; j--)
				lcs[i][j] = a.get(i).equals(b.get(j)) ? lcs[i + 1][j + 1] + 1: Math.max(lcs[i + 1][j], lcs[i][j + 1]);
		int i = 0, j = 0;
		while ((i < n) || (j < m)) {
			if ((i < n) && (j < m) && a.get(i).equals(b.get(j))) {
				if (full)
					result.add(new DiffLine(' ', a.get(i)));
				i++;
				j++;
			} else if ((i < n) && ((j == m) || (lcs[i + 1][j] >= lcs[i][j + 1]))) {
				result.add(new DiffLine('-', a.get(i++)));
			} else {
				result.add(new DiffLine('+', b.get(j++)));
			}
		}
		return result;
	}
}
