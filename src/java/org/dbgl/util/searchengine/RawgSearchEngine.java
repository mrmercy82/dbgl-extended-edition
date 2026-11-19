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
package org.dbgl.util.searchengine;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.SearchEngineImageInformation;
import org.dbgl.model.SearchEngineImageInformation.SearchEngineImageType;
import org.dbgl.model.WebProfile;
import org.dbgl.service.ImageService;
import org.dbgl.service.SettingsService;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * alpha 52: RAWG (rawg.io), with the user's own free key. Game data, description, rating, screenshots; it has no box covers,
 * its "cover" is the background picture of the game. Its terms want RAWG named as the source with a link: the link to the page
 * of the game on RAWG is set as a link of the profile ("set_link", on by default), and the settings say so.
 */
public class RawgSearchEngine extends WebSearchEngine {

	static final String API = "https://api.rawg.io/api/";
	static final String SITE = "https://rawg.io/games/";
	static final int PLATFORM_PC = 4;

	private RawgSearchEngine() {
	}

	private static class SearchEngineHolder {
		private static WebSearchEngine instance_ = new RawgSearchEngine();
	}

	public static WebSearchEngine getInstance() {
		return SearchEngineHolder.instance_;
	}

	@Override
	public String getIcon() {
		return ImageService.IMG_GLOBE;
	}

	@Override
	public String getName() {
		return "RAWG";
	}

	@Override
	public String getSimpleName() {
		return "rawg";
	}

	@Override
	public boolean available() {
		return true;
	}

	private String key() throws IOException {
		String key = StringUtils.trimToEmpty(SettingsService.getInstance().getValue(getSimpleName(), "api_key"));
		if (key.isEmpty())
			throw SearchEngineException.key(getName());
		return key;
	}

	JSONObject get(String path, String query) throws IOException {
		String url = API + path + "?key=" + URLEncoder.encode(key(), StandardCharsets.UTF_8) + (query.isEmpty() ? "": "&" + query);
		String answer = request(getName(), url, Map.of(), null);
		try {
			return new JSONObject(answer);
		} catch (RuntimeException e) {
			throw SearchEngineException.changed(getName(), e);
		}
	}

	/** platform filter: RAWG IDs, or "PC" / "DOS" / "Windows" (all PC on RAWG, 4) */
	public static Set<Integer> platformIds(String[] platforms) {
		Set<Integer> ids = new LinkedHashSet<>();
		for (String p: platforms) {
			String s = StringUtils.trimToEmpty(p).toLowerCase();
			if (s.matches("\\d+"))
				ids.add(Integer.parseInt(s));
			else if (s.contains("dos") || s.equals("pc") || s.contains("windows"))
				ids.add(PLATFORM_PC);
		}
		return ids;
	}

	@Override
	public List<WebProfile> getEntries(String title, String[] platforms) throws IOException {
		Set<Integer> ids = platformIds(platforms);
		String query = "search=" + URLEncoder.encode(prepareSearchTerm(title), StandardCharsets.UTF_8) + "&page_size=40"
				+ (ids.isEmpty() ? "": "&platforms=" + StringUtils.join(ids, ','));
		return parseEntries(get("games", query));
	}

	static String year(JSONObject g) {
		String released = g.optString("released", "");
		return released.matches("\\d{4}.*") ? released.substring(0, 4): "";
	}

	static String names(JSONArray array, String inner, String field) {
		List<String> result = new ArrayList<>();
		if (array != null)
			for (int i = 0; i < array.length(); i++) {
				JSONObject o = array.optJSONObject(i);
				if ((o != null) && (inner != null))
					o = o.optJSONObject(inner);
				String n = o == null ? "": o.optString(field, "").strip();
				if (!n.isEmpty() && !result.contains(n))
					result.add(n);
			}
		return String.join(", ", result);
	}

	/** the answers of RAWG, split off to be tested with saved answers */
	public static List<WebProfile> parseEntries(JSONObject answer) {
		List<WebProfile> result = new ArrayList<>();
		JSONArray games = answer.optJSONArray("results");
		if (games == null)
			return result;
		for (int i = 0; i < games.length(); i++) {
			JSONObject g = games.getJSONObject(i);
			WebProfile p = emptyWebProfile();
			p.setGameId(g.getInt("id"));
			p.setTitle(g.optString("name", ""));
			p.setYear(year(g));
			p.setPlatform(names(g.optJSONArray("platforms"), "platform", "name"));
			p.setUrl(SITE + g.optString("slug", String.valueOf(g.getInt("id"))));
			result.add(p);
		}
		return result;
	}

	@Override
	public WebProfile getEntryDetailedInformation(WebProfile entry) throws IOException {
		parseDetails(entry, get("games/" + entry.getGameId(), ""));
		return entry;
	}

	public static void parseDetails(WebProfile entry, JSONObject g) {
		entry.setTitle(g.optString("name", entry.getTitle()));
		entry.setYear(year(g));
		entry.setNotes(g.optString("description_raw", "").strip());
		if (g.has("slug"))
			entry.setUrl(SITE + g.getString("slug"));
		entry.setGenre(names(g.optJSONArray("genres"), null, "name"));
		entry.setDeveloperName(names(g.optJSONArray("developers"), null, "name"));
		entry.setPublisherName(names(g.optJSONArray("publishers"), null, "name"));
		entry.setRank((int)Math.round(g.optDouble("rating", 0) * 20)); // 0 to 5 -> 0 to 100
		entry.setSeries("");
		entry.setCoreGameCoverUrl(g.optString("background_image", ""));
	}

	@Override
	public SearchEngineImageInformation[] getEntryImages(WebProfile entry, int coverArtMax, int screenshotsMax, boolean forceAllRegionsCoverArt) throws IOException {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		if (coverArtMax > 0) {
			String background = StringUtils.defaultString(entry.getCoreGameCoverUrl());
			if (background.isEmpty())
				background = get("games/" + entry.getGameId(), "").optString("background_image", "");
			if (!background.isEmpty())
				result.add(new SearchEngineImageInformation(SearchEngineImageType.COVER_ART, background, "Background"));
		}
		if (screenshotsMax > 0)
			result.addAll(parseScreenshots(get("games/" + entry.getGameId() + "/screenshots", "page_size=40"), screenshotsMax));
		entry.setWebImages(result.toArray(new SearchEngineImageInformation[0]));
		return entry.getWebImages();
	}

	public static List<SearchEngineImageInformation> parseScreenshots(JSONObject answer, int max) {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		JSONArray shots = answer.optJSONArray("results");
		for (int i = 0; (shots != null) && (i < shots.length()) && (result.size() < max); i++) {
			String url = shots.getJSONObject(i).optString("image", "");
			if (!url.isEmpty())
				result.add(new SearchEngineImageInformation(SearchEngineImageType.SCREENSHOT, url, "Screenshot " + (result.size() + 1)));
		}
		return result;
	}
}
