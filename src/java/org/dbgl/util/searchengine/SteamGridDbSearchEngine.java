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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.SearchEngineImageInformation;
import org.dbgl.model.SearchEngineImageInformation.SearchEngineImageType;
import org.dbgl.model.WebProfile;
import org.dbgl.service.ImageService;
import org.dbgl.service.SettingsService;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * SteamGridDB (steamgriddb.com): cover art made by its users, also for many DOS games; title and year, no other game data and
 * no screenshots. Each user needs a free API key (steamgriddb.com, Preferences, API). See https://www.steamgriddb.com/api/v2
 */
public class SteamGridDbSearchEngine extends WebSearchEngine {

	static final String API = "https://www.steamgriddb.com/api/v2/";
	static final String SITE = "https://www.steamgriddb.com/game/";
	/** portrait covers, as a box */
	static final String DIMENSIONS = "600x900,342x482,660x930";

	private SteamGridDbSearchEngine() {
	}

	private static class SearchEngineHolder {
		private static WebSearchEngine instance_ = new SteamGridDbSearchEngine();
	}

	public static WebSearchEngine getInstance() {
		return SearchEngineHolder.instance_;
	}

	@Override
	public String getIcon() {
		return ImageService.IMG_STEAMGRIDDB;
	}

	@Override
	public String getName() {
		return "SteamGridDB";
	}

	@Override
	public String getSimpleName() {
		return "steamgriddb";
	}

	@Override
	public boolean available() {
		return true;
	}

	JSONArray get(String path) throws IOException {
		String key = StringUtils.trimToEmpty(SettingsService.getInstance().getValue(getSimpleName(), "api_key"));
		if (key.isEmpty())
			throw SearchEngineException.key(getName());
		String answer = request(getName(), API + path, Map.of("Authorization", "Bearer " + key), null);
		try {
			JSONObject json = new JSONObject(answer);
			if (!json.optBoolean("success", false))
				throw SearchEngineException.changed(getName(), null);
			return json.optJSONArray("data") == null ? new JSONArray(): json.getJSONArray("data");
		} catch (RuntimeException e) {
			throw SearchEngineException.changed(getName(), e);
		}
	}

	@Override
	public List<WebProfile> getEntries(String title, String[] platforms) throws IOException {
		JSONArray games = get("search/autocomplete/" + URLEncoder.encode(prepareSearchTerm(title), StandardCharsets.UTF_8).replace("+", "%20"));
		List<WebProfile> result = new ArrayList<>();
		for (int i = 0; i < games.length(); i++) {
			JSONObject g = games.getJSONObject(i);
			WebProfile p = emptyWebProfile();
			p.setGameId(g.getInt("id"));
			p.setTitle(g.optString("name", ""));
			long date = g.optLong("release_date", 0);
			p.setYear(date == 0 ? "": String.valueOf(Instant.ofEpochSecond(date).atZone(ZoneOffset.UTC).getYear()));
			p.setUrl(SITE + p.getGameId());
			result.add(p);
		}
		return result;
	}

	@Override
	public WebProfile getEntryDetailedInformation(WebProfile entry) throws IOException {
		return entry; // title and year come with the search already
	}

	@Override
	public SearchEngineImageInformation[] getEntryImages(WebProfile entry, int coverArtMax, int screenshotsMax, boolean forceAllRegionsCoverArt) throws IOException {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		if (coverArtMax > 0) {
			JSONArray grids = get("grids/game/" + entry.getGameId() + "?dimensions=" + DIMENSIONS);
			for (int i = 0; (i < grids.length()) && (result.size() < coverArtMax); i++) {
				JSONObject g = grids.getJSONObject(i);
				JSONObject author = g.optJSONObject("author");
				String by = author == null ? "": author.optString("name", "");
				result.add(new SearchEngineImageInformation(SearchEngineImageType.COVER_ART, g.getString("url"), "SteamGridDB" + (by.isEmpty() ? "": " (" + by + ")")));
			}
		}
		entry.setWebImages(result.toArray(new SearchEngineImageInformation[0]));
		return entry.getWebImages();
	}

	/** alpha 52: the kinds of pictures of the Steam library: wide banner, background (hero), logo, upright cover */
	public static final String BANNER = "banner", HERO = "hero", LOGO = "logo", COVER = "cover";

	/** @return true if a key for SteamGridDB is set (the pictures for Steam need one) */
	public boolean hasKey() {
		return StringUtils.isNotBlank(SettingsService.getInstance().getValue(getSimpleName(), "api_key"));
	}

	/**
	 * alpha 52: the pictures of a game for the Steam library, by kind (see the constants) the address of the best rated one;
	 * the game found by its title (the one with the same name, else the first); empty if there is none
	 */
	public Map<String, String> steamArt(String title) throws IOException {
		Map<String, String> result = new java.util.LinkedHashMap<>();
		List<WebProfile> found = getEntries(title, new String[0]);
		if (found.isEmpty())
			return result;
		WebProfile game = found.stream().filter(g -> normalized(g.getTitle()).equals(normalized(title))).findFirst().orElse(found.get(0));
		String still = "mimes=image/png,image/jpeg&types=static";
		firstUrl(result, BANNER, "grids/game/" + game.getGameId() + "?dimensions=460x215,920x430&" + still);
		firstUrl(result, HERO, "heroes/game/" + game.getGameId() + "?" + still);
		firstUrl(result, LOGO, "logos/game/" + game.getGameId() + "?mimes=image/png&types=static");
		firstUrl(result, COVER, "grids/game/" + game.getGameId() + "?dimensions=600x900&" + still);
		return result;
	}

	private void firstUrl(Map<String, String> result, String kind, String path) throws IOException {
		JSONArray found = get(path);
		for (int i = 0; i < found.length(); i++) {
			String url = found.getJSONObject(i).optString("url", "");
			if (url.startsWith("https://")) {
				result.put(kind, url);
				return;
			}
		}
	}

	public static String normalized(String title) {
		return title == null ? "": title.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
	}

	/** alpha 52: a picture saved into a file of the folder, named by its kind with the extension of the address (png or jpg) */
	public static java.io.File download(String url, java.io.File dir, String kind) throws IOException {
		String ext = url.toLowerCase(java.util.Locale.ROOT).replaceAll("[?#].*$", "").endsWith(".png") ? "png": "jpg";
		java.io.File target = new java.io.File(dir, kind + "." + ext);
		java.net.HttpURLConnection conn = getHttpURLConnection(url);
		try (java.io.InputStream in = conn.getInputStream()) {
			byte[] data = in.readNBytes(15 * 1024 * 1024 + 1);
			if (data.length > 15 * 1024 * 1024)
				throw new IOException("picture too large: " + url);
			java.nio.file.Files.write(target.toPath(), data);
		}
		return target;
	}
}
