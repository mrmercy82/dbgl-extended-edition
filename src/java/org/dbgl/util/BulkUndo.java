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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.FileLocationService;


/**
 * The state of all profiles a change of many profiles at once touches (apply a preset, switch the DOSBox version, repair
 * paths, edit several profiles), so the last such change can be undone in one step: the configuration file and the DOSBox
 * version of each profile. Only the last change is kept, in profiles/history/bulk/.
 */
public final class BulkUndo {

	/** what the last change was, when, and for how many profiles */
	public record Info(String label, Date when, int profiles) {
	}

	private static final String META = "change.txt";
	private static final String STAMP = "yyyy-MM-dd HH:mm:ss";

	private BulkUndo() {
	}

	static File dir() {
		return new File(new File(FileLocationService.getInstance().getProfilesDir(), "history"), "bulk");
	}

	/** remember the current state of the profiles, before they are changed; replaces the previously remembered change */
	public static void remember(String label, Collection<Profile> profiles) {
		remember(label, profiles == null ? null: new java.util.ArrayList<>(profiles), null);
	}

	/**
	 * Like {@link #remember(String, Collection)}, for profiles already changed in memory but not yet saved: their files are
	 * still the old state, the DOSBox versions to go back to come from the originals (same order), when given. Never stops
	 * the change itself: a profile that cannot be read is just not remembered.
	 */
	public static void remember(String label, java.util.List<Profile> profiles, java.util.List<Profile> originals) {
		if ((profiles == null) || profiles.size() < 2)
			return; // a single profile has its own history
		File dir = dir();
		try {
			clear();
			Files.createDirectories(dir.toPath());
			StringBuilder meta = new StringBuilder(label.replaceAll("[\\r\\n]", " ")).append('\n').append(new SimpleDateFormat(STAMP).format(new Date())).append('\n');
			for (int i = 0; i < profiles.size(); i++) {
				Profile p = profiles.get(i);
				byte[] content = ProfileHistory.read(p);
				if (content == null)
					continue;
				Profile versionOf = (originals != null) && (i < originals.size()) ? originals.get(i): p;
				Files.write(new File(dir, p.getId() + ".conf").toPath(), content);
				meta.append(p.getId()).append('=').append(versionOf.getDosboxVersion() == null ? -1: versionOf.getDosboxVersion().getId()).append('\n');
			}
			Files.writeString(new File(dir, META).toPath(), meta.toString(), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			e.printStackTrace();
		}
		changed();
	}

	private static volatile Runnable onChange_;

	/** told when a change is remembered or undone (e.g. the undo button of the toolbar greyed out when there is none); from any thread */
	public static void onChange(Runnable listener) {
		onChange_ = listener;
	}

	private static void changed() {
		Runnable listener = onChange_;
		if (listener != null)
			listener.run();
	}

	/** @return the last remembered change, or null */
	public static Info last() {
		try {
			Map<Integer, Integer> entries = new LinkedHashMap<>();
			String[] meta = read(entries);
			return meta == null || entries.isEmpty() ? null: new Info(meta[0], new SimpleDateFormat(STAMP).parse(meta[1]), entries.size());
		} catch (Exception e) {
			return null;
		}
	}

	private static String[] read(Map<Integer, Integer> entries) throws IOException {
		File file = new File(dir(), META);
		if (!file.isFile())
			return null;
		List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
		if (lines.size() < 2)
			return null;
		for (String l: lines.subList(2, lines.size())) {
			int eq = l.indexOf('=');
			if (eq > 0)
				entries.put(Integer.parseInt(l.substring(0, eq).trim()), Integer.parseInt(l.substring(eq + 1).trim()));
		}
		return new String[] {lines.get(0), lines.get(1)};
	}

	/**
	 * bring the profiles back to the remembered state; profiles removed in the meantime are skipped
	 *
	 * @return the ids of the restored profiles
	 */
	public static List<Integer> undo(List<DosboxVersion> dbversions) throws IOException, java.sql.SQLException {
		return undo(dbversions, null);
	}

	/** one profile of the remembered change: its configuration file now and as it was, and its DOSBox version now and then */
	public record Change(Profile profile, String now, String then, DosboxVersion nowVersion, DosboxVersion thenVersion) {
	}

	/** the profiles of the remembered change (still there), with both states of their files, read in their own charsets */
	public static List<Change> changes(List<DosboxVersion> dbversions) {
		List<Change> result = new ArrayList<>();
		try {
			Map<Integer, Integer> entries = new LinkedHashMap<>();
			if (read(entries) == null)
				return result;
			String ids = entries.keySet().stream().map(String::valueOf).collect(Collectors.joining(","));
			for (Profile p: new ProfileRepository().list("", "GAM.ID IN (" + ids + ")", dbversions)) {
				File saved = new File(dir(), p.getId() + ".conf");
				File current = p.getConfigurationCanonicalFile();
				if (!saved.isFile())
					continue;
				String now = (current != null) && current.isFile() ? new String(Files.readAllBytes(current.toPath()), ConfCharset.of(current)): "";
				String then = new String(Files.readAllBytes(saved.toPath()), ConfCharset.of(saved));
				DosboxVersion before = BaseRepository.findById(dbversions, entries.get(p.getId()));
				result.add(new Change(p, now, then, p.getDosboxVersion(), before != null ? before: p.getDosboxVersion()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * @param only the profiles to bring back (null: all); the others stay remembered, to be brought back later
	 * @return the ids of the restored profiles
	 */
	public static List<Integer> undo(List<DosboxVersion> dbversions, java.util.Set<Integer> only) throws IOException, java.sql.SQLException {
		Map<Integer, Integer> entries = new LinkedHashMap<>();
		String[] meta = read(entries);
		if (meta == null)
			return List.of();
		Map<Integer, Integer> kept = new LinkedHashMap<>(entries);
		if (only != null)
			entries.keySet().retainAll(only);
		if (entries.isEmpty())
			return List.of();
		ProfileRepository repo = new ProfileRepository();
		String ids = entries.keySet().stream().map(String::valueOf).collect(Collectors.joining(","));
		List<Integer> restored = new ArrayList<>();
		for (Profile p: repo.list("", "GAM.ID IN (" + ids + ")", dbversions)) {
			File saved = new File(dir(), p.getId() + ".conf");
			if (!saved.isFile())
				continue;
			DosboxVersion dbv = BaseRepository.findById(dbversions, entries.get(p.getId()));
			byte[] now = ProfileHistory.read(p), then = Files.readAllBytes(saved.toPath());
			boolean sameVersion = (dbv == null) || ((p.getDosboxVersion() != null) && (p.getDosboxVersion().getId() == dbv.getId()));
			if (java.util.Arrays.equals(now, then) && sameVersion) { // not changed (or changed back): nothing to write
				restored.add(p.getId());
				continue;
			}
			if (dbv != null)
				p.setDosboxVersion(dbv);
			// byte for byte as it was (saving the profile would write it again in DBGL's own way); the state before the undo
			// is kept in the history of the profile, so the undo can be undone there
			Files.write(p.getConfigurationCanonicalFile().toPath(), then);
			p.resetAndLoadConfiguration();
			repo.updateKeepingConfigurationFile(p);
			ProfileHistory.recordIfChanged(p, now);
			ProfileWarnings.forget(p.getId());
			restored.add(p.getId());
		}
		kept.keySet().removeAll(entries.keySet());
		if (kept.isEmpty()) {
			clear();
		} else { // the others stay: their files and their lines in change.txt
			for (Integer id: entries.keySet())
				Files.deleteIfExists(new File(dir(), id + ".conf").toPath());
			StringBuilder m = new StringBuilder(meta[0]).append('\n').append(meta[1]).append('\n');
			kept.forEach((id, v) -> m.append(id).append('=').append(v).append('\n'));
			Files.writeString(new File(dir(), META).toPath(), m.toString(), StandardCharsets.UTF_8);
			changed();
		}
		return restored;
	}

	/**
	 * What undoing would change, for a look before: per profile its DOSBox version and the settings that go back, as
	 * "title: [sdl] output: opengl -> surface"; at most maxLines lines.
	 */
	public static String preview(List<DosboxVersion> dbversions, int maxLines) {
		try {
			Map<Integer, Integer> entries = new LinkedHashMap<>();
			if (read(entries) == null)
				return "";
			String ids = entries.keySet().stream().map(String::valueOf).collect(Collectors.joining(","));
			List<String> lines = new ArrayList<>();
			Map<Integer, Map<String, String>> defaults = new java.util.HashMap<>();
			int more = 0;
			for (Profile p: new ProfileRepository().list("", "GAM.ID IN (" + ids + ")", dbversions)) {
				File saved = new File(dir(), p.getId() + ".conf");
				File current = p.getConfigurationCanonicalFile();
				if (!saved.isFile() || (current == null))
					continue;
				List<String> changes = new ArrayList<>();
				DosboxVersion before = BaseRepository.findById(dbversions, entries.get(p.getId()));
				if ((before != null) && (p.getDosboxVersion() != null) && (before.getId() != p.getDosboxVersion().getId()))
					changes.add("DOSBox: " + p.getDosboxVersion().getTitle() + " -> " + before.getTitle());
				Map<String, String> now = settingsOf(current), then = settingsOf(saved);
				// a setting the profile does not have itself: the value of its DOSBox version counts (shown as such)
				Map<String, String> nowDefaults = defaultsOf(p.getDosboxVersion(), defaults);
				Map<String, String> thenDefaults = before == null ? nowDefaults: defaultsOf(before, defaults);
				java.util.Set<String> keys = new java.util.TreeSet<>(now.keySet());
				keys.addAll(then.keySet());
				for (String k: keys) {
					String a = shown(k, now, nowDefaults), b = shown(k, then, thenDefaults);
					if (!a.equals(b)) // the [autoexec] as its number of lines: "[autoexec]: 7 -> 5"
						changes.add(k + ": " + a + " -> " + b);
				}
				// like a log: the profile, below it what goes back (now -> before)
				if (!changes.isEmpty() && (lines.size() < maxLines))
					lines.add(p.getTitle());
				for (String c: changes) {
					if (lines.size() < maxLines)
						lines.add("    " + c);
					else
						more++;
				}
			}
			if (more > 0)
				lines.add("... (+" + more + ")");
			return String.join("\n", lines);
		} catch (Exception e) {
			return "";
		}
	}

	/** the settings of the configuration file of a DOSBox version, read once per preview */
	static Map<String, String> defaultsOf(DosboxVersion v, Map<Integer, Map<String, String>> cache) {
		if (v == null)
			return Map.of();
		return cache.computeIfAbsent(v.getId(), id -> {
			try {
				File f = v.getConfigurationCanonicalFile();
				return (f != null) && f.isFile() ? settingsOf(f): Map.of();
			} catch (IOException | RuntimeException e) {
				return Map.of();
			}
		});
	}

	/** the value in the profile, else that of its DOSBox version marked as such, else "-" */
	static String shown(String key, Map<String, String> own, Map<String, String> dosbox) {
		if (own.containsKey(key))
			return own.get(key).replaceAll("\\|.*", "");
		if (dosbox.containsKey(key) && !key.equals("[autoexec]"))
			return org.dbgl.service.TextService.getInstance().get("dialog.bulkundo.dosboxdefault", new Object[] {dosbox.get(key)});
		return "-";
	}

	/** "[section] item" -> value, the [autoexec] as one entry */
	static Map<String, String> settingsOf(File conf) throws IOException {
		Map<String, String> result = new LinkedHashMap<>();
		String section = "";
		StringBuilder autoexec = new StringBuilder();
		for (String line: Files.readAllLines(conf.toPath(), java.nio.charset.Charset.defaultCharset())) {
			String l = line.trim();
			if (l.startsWith("[") && l.endsWith("]")) {
				section = l.substring(1, l.length() - 1).toLowerCase();
				continue;
			}
			if (section.equals("autoexec")) {
				if (!l.isEmpty())
					autoexec.append(l).append('\n');
			} else if (!l.startsWith("#") && (l.indexOf('=') > 0)) {
				result.put('[' + section + "] " + l.substring(0, l.indexOf('=')).trim(), l.substring(l.indexOf('=') + 1).trim());
			}
		}
		if (autoexec.length() > 0)
			result.put("[autoexec]", autoexec.toString().lines().count() + "|" + autoexec.toString().hashCode());
		return result;
	}

	public static void clear() {
		File[] files = dir().listFiles();
		if (files != null)
			for (File f: files)
				f.delete();
		changed();
	}
}
