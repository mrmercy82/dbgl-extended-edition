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
		if ((profiles == null) || profiles.size() < 2)
			return; // a single profile has its own history
		File dir = dir();
		try {
			clear();
			Files.createDirectories(dir.toPath());
			StringBuilder meta = new StringBuilder(label.replaceAll("[\\r\\n]", " ")).append('\n').append(new SimpleDateFormat(STAMP).format(new Date())).append('\n');
			for (Profile p: profiles) {
				byte[] content = ProfileHistory.read(p);
				if (content == null)
					continue;
				Files.write(new File(dir, p.getId() + ".conf").toPath(), content);
				meta.append(p.getId()).append('=').append(p.getDosboxVersion() == null ? -1: p.getDosboxVersion().getId()).append('\n');
			}
			Files.writeString(new File(dir, META).toPath(), meta.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
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
		Map<Integer, Integer> entries = new LinkedHashMap<>();
		if (read(entries) == null)
			return List.of();
		ProfileRepository repo = new ProfileRepository();
		String ids = entries.keySet().stream().map(String::valueOf).collect(Collectors.joining(","));
		List<Integer> restored = new ArrayList<>();
		for (Profile p: repo.list("", "GAM.ID IN (" + ids + ")", dbversions)) {
			File saved = new File(dir(), p.getId() + ".conf");
			if (!saved.isFile())
				continue;
			DosboxVersion dbv = BaseRepository.findById(dbversions, entries.get(p.getId()));
			if (dbv != null)
				p.setDosboxVersion(dbv);
			Files.copy(saved.toPath(), p.getConfigurationCanonicalFile().toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			p.resetAndLoadConfiguration();
			repo.update(p);
			ProfileWarnings.forget(p.getId());
			restored.add(p.getId());
		}
		clear();
		return restored;
	}

	public static void clear() {
		File[] files = dir().listFiles();
		if (files != null)
			for (File f: files)
				f.delete();
	}
}
