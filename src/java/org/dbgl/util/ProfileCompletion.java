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
import java.util.List;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.WebProfile;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.util.searchengine.WebSearchEngine;


/**
 * Alpha 53 (TEST24): "Complete profiles": the order from a game folder to a finished profile. First the title (looked up in
 * the database of MobyGames that comes with DBGL, else in the text files of the game folder), then the details of the game
 * from that database, then the covers (searched by the title, so only after it is right). Each profile gets a state like a
 * traffic light.
 */
public final class ProfileCompletion {

	/** DONE: has its details already; FOUND: the database has exactly its title; LIKELY: a title proposed; NONE: nothing found */
	public enum State {
		DONE, FOUND, LIKELY, NONE
	}

	/** what was found for a profile: its state, the game proposed (null if none) and where it came from (a file, or null) */
	public record Assessment(State state, WebProfile proposal, String source) {
	}

	private ProfileCompletion() {
	}

	/** a profile has its details when it has a developer, a publisher or a year */
	public static boolean hasDetails(Profile p) {
		return StringUtils.isNotBlank(p.getDeveloper()) || StringUtils.isNotBlank(p.getPublisher()) || StringUtils.isNotBlank(p.getYear());
	}

	/**
	 * @param lookup the games of the database for a search term, or null without the database
	 * @param gameDir the folder of the game (for its text files), or null
	 */
	public static Assessment assess(String title, boolean hasDetails, Function<String, List<WebProfile>> lookup, File gameDir) {
		if (hasDetails)
			return new Assessment(State.DONE, null, null);
		if (lookup == null)
			return new Assessment(State.NONE, null, null);
		List<WebProfile> found = StringUtils.isBlank(title) ? List.of(): safe(lookup, title);
		String key = WebSearchEngine.comparable(title);
		List<WebProfile> exact = found.stream().filter(w -> WebSearchEngine.comparable(w.getTitle()).equals(key)).toList();
		if (!exact.isEmpty() && exact.stream().map(WebProfile::getGameId).distinct().count() == 1)
			return new Assessment(State.FOUND, exact.get(0), null);
		if (!found.isEmpty()) {
			// a title that holds the one searched for ("Monkey Island 2" in "Monkey Island 2: LeChuck's Revenge"), the shortest first
			WebProfile holding = key.isEmpty() ? null: found.stream().filter(w -> WebSearchEngine.comparable(w.getTitle()).contains(key))
					.min(java.util.Comparator.comparingInt(w -> w.getTitle().length())).orElse(null);
			if (holding != null)
				return new Assessment(State.LIKELY, holding, null);
			int best = WebSearchEngine.getEntryBestMatchIndex(title, found);
			if (best >= 0)
				return new Assessment(State.LIKELY, found.get(best), null);
		}
		if ((gameDir != null) && gameDir.isDirectory()) {
			TextTitles.Found t = TextTitles.match(gameDir, s -> safe(lookup, s));
			if (t != null)
				return new Assessment(State.LIKELY, t.game(), t.source());
		}
		return new Assessment(State.NONE, null, null);
	}

	/**
	 * the title and the details of the game taken into the profile: the title always, developer, publisher, year, genre and
	 * description only where the profile has none
	 */
	public static void apply(Profile p, WebProfile game) {
		if (StringUtils.isNotBlank(game.getTitle()))
			p.setTitle(game.getTitle());
		if (StringUtils.isBlank(p.getDeveloper()) && StringUtils.isNotBlank(game.getDeveloperName()))
			p.setDeveloper(game.getDeveloperName());
		if (StringUtils.isBlank(p.getPublisher()) && StringUtils.isNotBlank(game.getPublisherName()))
			p.setPublisher(game.getPublisherName());
		if (StringUtils.isBlank(p.getYear()) && StringUtils.isNotBlank(game.getYear()))
			p.setYear(game.getYear());
		if (StringUtils.isBlank(p.getGenre()) && StringUtils.isNotBlank(game.getGenre()))
			p.setGenre(game.getGenre());
		if (StringUtils.isBlank(p.getNotes()) && StringUtils.isNotBlank(game.getNotes()))
			p.setNotes(game.getNotes());
	}

	/** the games of the database of MobyGames that comes with DBGL for a title (its platform filter), or null without it */
	public static Function<String, List<WebProfile>> databaseLookup() {
		int[] version = org.dbgl.service.MetropolisDatabaseService.getInstance().getVersion();
		if ((version.length != 2) || ((version[0] == 0) && (version[1] == 0)))
			return null;
		var engine = org.dbgl.util.searchengine.MetropolisSearchEngine.getInstance();
		String[] platforms = org.dbgl.service.SettingsService.getInstance().getValues(engine.getSimpleName(), "platform_filter");
		return title -> {
			try {
				return engine.getEntries(title, platforms);
			} catch (java.io.IOException e) {
				return List.of();
			}
		};
	}

	/** the details of a game of that database (developer, publisher, genre, description), or the game as it is */
	public static WebProfile detailed(WebProfile game) {
		try {
			return org.dbgl.util.searchengine.MetropolisSearchEngine.getInstance().getEntryDetailedInformation(game);
		} catch (java.io.IOException | RuntimeException e) {
			return game;
		}
	}

	private static List<WebProfile> safe(Function<String, List<WebProfile>> lookup, String s) {
		try {
			List<WebProfile> r = lookup.apply(s);
			return r == null ? List.of(): r;
		} catch (RuntimeException e) {
			return List.of();
		}
	}
}
