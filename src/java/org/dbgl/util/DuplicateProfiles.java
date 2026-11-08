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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.Link;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.service.TextService;


/**
 * Profiles that start the same game: the same main program with the same parameters (e.g. a game added twice, once by hand
 * and once by an import). Two profiles for one folder with different programs (game and setup, two parts) are no duplicates.
 */
public final class DuplicateProfiles {

	/** profiles that start the same program; the first is the one to keep (see keepFirst) */
	public record Group(String program, List<Profile> profiles) {
	}

	/** the main program of each profile (by its configuration file and when it was changed), so that it is read only once */
	private static final Map<String, String> KEYS = new ConcurrentHashMap<>();

	private DuplicateProfiles() {
	}

	/** @return "C:\Games\Duke3D\DUKE3D.EXE|params", or null without a main program */
	public static String keyOf(Profile p) {
		File conf;
		try {
			conf = p.getConfigurationCanonicalFile();
		} catch (RuntimeException e) {
			return keyOfLoaded(p);
		}
		String cacheKey = conf.getPath() + '|' + conf.lastModified() + '|' + conf.length();
		String key = KEYS.computeIfAbsent(cacheKey, k -> {
			try {
				Profile copy = ProfileFactory.create(p); // with its configuration file, the list's profile stays as it is
				copy.resetAndLoadConfiguration();
				String result = keyOfLoaded(copy);
				return result == null ? "": result;
			} catch (Exception e) {
				return "";
			}
		});
		return key.isEmpty() ? null: key;
	}

	/** the key of a profile whose configuration is read already, e.g. one about to be added */
	public static String keyOfLoadedPublic(Profile p) {
		return keyOfLoaded(p);
	}

	static String keyOfLoaded(Profile p) {
		String main = p.getConfiguration().getAutoexec().getMain();
		if (StringUtils.isBlank(main))
			return null;
		File f = new File(main);
		if (!f.isAbsolute())
			f = new File(org.dbgl.service.FileLocationService.getInstance().getDosroot(), main);
		String params = StringUtils.defaultString(p.getConfiguration().getAutoexec().getParameters()).trim();
		return f.getAbsoluteFile().toPath().normalize() + "|" + params; // compared in lower case (DOS names), shown as written
	}

	/** @param progress told about every profile looked at (may be null) */
	public static List<Group> find(List<Profile> all, Consumer<Profile> progress) {
		return find(all, DuplicateProfiles::keyOf, progress);
	}

	/** for profiles whose configuration is read already (e.g. in tests) */
	public static List<Group> findLoaded(List<Profile> all) {
		return find(all, DuplicateProfiles::keyOfLoaded, null);
	}

	static List<Group> find(List<Profile> all, Function<Profile, String> key, Consumer<Profile> progress) {
		Map<String, List<Profile>> byKey = new LinkedHashMap<>();
		Map<String, String> shown = new LinkedHashMap<>();
		for (Profile p: all) {
			String k = key.apply(p);
			if (progress != null)
				progress.accept(p);
			if (k != null) {
				shown.putIfAbsent(k.toLowerCase(Locale.ROOT), k.substring(0, k.indexOf('|')));
				byKey.computeIfAbsent(k.toLowerCase(Locale.ROOT), x -> new ArrayList<>()).add(p);
			}
		}
		List<Group> result = new ArrayList<>();
		byKey.forEach((k, list) -> {
			if (list.size() > 1)
				result.add(new Group(shown.get(k), keepFirst(list)));
		});
		return result;
	}

	/** the profile with the most information first (filled fields, links, how often played), then the oldest */
	static List<Profile> keepFirst(List<Profile> list) {
		List<Profile> sorted = new ArrayList<>(list);
		sorted.sort((a, b) -> {
			int c = Integer.compare(richness(b), richness(a));
			return c != 0 ? c: Integer.compare(a.getId(), b.getId());
		});
		return sorted;
	}

	static int richness(Profile p) {
		int r = 0;
		for (String s: new String[] {p.getDeveloper(), p.getPublisher(), p.getGenre(), p.getYear(), p.getStatus(), p.getNotes()})
			if (StringUtils.isNotBlank(s))
				r += 10;
		if (p.getLinks() != null)
			for (Link l: p.getLinks())
				if ((l != null) && StringUtils.isNotBlank(l.getDestination()))
					r += 5;
		if (p.getStats() != null)
			r += Math.min(p.getStats().getRuns(), 9);
		return r;
	}

	/**
	 * Take over into the profile kept what only the others have: empty fields (developer, publisher, genre, year, status, own
	 * fields), notes (added below), favourite, and links it does not have yet (as far as there are free fields).
	 */
	public static void merge(Profile keep, List<Profile> others) {
		for (Profile o: others) {
			if (StringUtils.isBlank(keep.getDeveloper()))
				keep.setDeveloper(o.getDeveloper());
			if (StringUtils.isBlank(keep.getPublisher()))
				keep.setPublisher(o.getPublisher());
			if (StringUtils.isBlank(keep.getGenre()))
				keep.setGenre(o.getGenre());
			if (StringUtils.isBlank(keep.getYear()))
				keep.setYear(o.getYear());
			if (StringUtils.isBlank(keep.getStatus()))
				keep.setStatus(o.getStatus());
			if (StringUtils.isNotBlank(o.getNotes()) && !StringUtils.defaultString(keep.getNotes()).contains(o.getNotes().trim()))
				keep.setNotes(StringUtils.isBlank(keep.getNotes()) ? o.getNotes(): keep.getNotes().trim() + "\n\n" + o.getNotes().trim());
			if (Boolean.parseBoolean(o.getFavorite()))
				keep.setFavorite(true);
			String[] own = keep.getCustomStrings(), their = o.getCustomStrings();
			if ((own != null) && (their != null))
				for (int i = 0; (i < own.length) && (i < their.length); i++)
					if (StringUtils.isBlank(own[i]) && StringUtils.isNotBlank(their[i]))
						keep.setCustomString(i, their[i]);
			if ((o.getLinks() != null) && (keep.getLinks() != null))
				for (Link l: o.getLinks()) {
					if ((l == null) || StringUtils.isBlank(l.getDestination()))
						continue;
					boolean known = false;
					int free = -1;
					for (int i = 0; i < keep.getLinks().length; i++) {
						Link k = keep.getLinks()[i];
						if ((k != null) && l.getDestination().equalsIgnoreCase(StringUtils.defaultString(k.getDestination())))
							known = true;
						if ((free == -1) && ((k == null) || StringUtils.isBlank(k.getDestination())))
							free = i;
					}
					if (!known && (free != -1)) {
						keep.setLinkDestination(free, l.getDestination());
						keep.setLinkTitle(free, l.getTitle());
					}
				}
		}
	}

	/** a line for the list: "Title (DOSBox version, played 3 times)" */
	public static String describe(Profile p) {
		int runs = p.getStats() == null ? 0: p.getStats().getRuns();
		return TextService.getInstance().get("dialog.duplicates.profile", new Object[] {p.getTitle(), p.getDosboxVersion() == null ? "-": p.getDosboxVersion().getTitle(), runs});
	}
}
