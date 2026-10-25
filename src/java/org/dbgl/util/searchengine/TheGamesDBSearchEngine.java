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
package org.dbgl.util.searchengine;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.model.SearchEngineImageInformation;
import org.dbgl.model.SearchEngineImageInformation.SearchEngineImageType;
import org.dbgl.model.WebProfile;
import org.dbgl.service.ImageService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.json.JSONArray;
import org.json.JSONObject;


public class TheGamesDBSearchEngine extends WebSearchEngine {

	private static final String THE_GAMES_DB_HOST_NAME = "thegamesdb.net";
	private static final String THE_GAMES_DB_API = "api." + THE_GAMES_DB_HOST_NAME;
	private static final int MAX_PAGES = 10;
	
	private static Map<Integer, String> plaMap;
	private static Map<Integer, String> genMap;
	private static Map<Integer, String> devMap;
	private static Map<Integer, String> pubMap;

	private TheGamesDBSearchEngine() {
	}

	private static class SearchEngineHolder {
		private static WebSearchEngine instance_ = new TheGamesDBSearchEngine();
	}

	public static WebSearchEngine getInstance() {
		return SearchEngineHolder.instance_;
	}

	@Override
	public String getIcon() {
		return ImageService.IMG_THEGAMESDB;
	}

	@Override
	public String getName() {
		return "TheGamesDB.net";
	}

	@Override
	public String getSimpleName() {
		return "thegamesdb";
	}

	@Override
	public boolean available() {
		return true;
	}

	protected String addKey(String url) throws IOException {
		return url + (url.contains("?") ? '&': '?') + "apikey=" + SettingsService.getInstance().getValue(getSimpleName(), "api_key");
	}
	
	protected String getResponse(String url) throws IOException {
		HttpURLConnection conn = getHttpURLConnection(url);
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Accept", "application/json");

		if (conn.getResponseCode() == 403)
			throw new IOException(TextService.getInstance().get("dialog.profile.thegamesdb.error.ratelimit"));

		return IOUtils.toString(conn.getInputStream(), StandardCharsets.UTF_8.name());
	}
	
	public Map<Integer, String> getPlatforms() {
		if (plaMap == null) {
			plaMap = new HashMap<>();
			try {
				String response = getResponse(addKey(HTTPS_PROTOCOL + THE_GAMES_DB_API + "/v1/Platforms"));
				JSONObject json = new JSONObject(response).getJSONObject("data").getJSONObject("platforms");
				json.keys().forEachRemaining(x -> {
					JSONObject genreObj = (JSONObject) json.get(x);
					plaMap.put(genreObj.getInt("id"), genreObj.getString("name"));
				});
			} catch (IOException e) {
				// nothing we can do
			}
		}
		return plaMap;
	}
	
	public Map<Integer, String> getGenres() {
		if (genMap == null) {
			genMap = new HashMap<>();
			try {
				String response = getResponse(addKey(HTTPS_PROTOCOL + THE_GAMES_DB_API + "/v1/Genres"));
				JSONObject json = new JSONObject(response).getJSONObject("data").getJSONObject("genres");
				json.keys().forEachRemaining(x -> {
					JSONObject genreObj = (JSONObject) json.get(x);
					genMap.put(genreObj.getInt("id"), genreObj.getString("name"));
				});
			} catch (IOException e) {
				// nothing we can do
			}
		}
		return genMap;
	}
	
	public Map<Integer, String> getDevelopers() {
		if (devMap == null) {
			devMap = new HashMap<>();
			try {
				String response = getResponse(addKey(HTTPS_PROTOCOL + THE_GAMES_DB_API + "/v1/Developers"));
				JSONObject json = new JSONObject(response).getJSONObject("data").getJSONObject("developers");
				json.keys().forEachRemaining(x -> {
					JSONObject genreObj = (JSONObject) json.get(x);
					devMap.put(genreObj.getInt("id"), genreObj.getString("name"));
				});
			} catch (IOException e) {
				// nothing we can do
			}
		}
		return devMap;
	}
	
	public Map<Integer, String> getPublishers() {
		if (pubMap == null) {
			pubMap = new HashMap<>();
			try {
				String response = getResponse(addKey(HTTPS_PROTOCOL + THE_GAMES_DB_API + "/v1/Publishers"));
				JSONObject json = new JSONObject(response).getJSONObject("data").getJSONObject("publishers");
				json.keys().forEachRemaining(x -> {
					JSONObject genreObj = (JSONObject) json.get(x);
					pubMap.put(genreObj.getInt("id"), genreObj.getString("name"));
				});
			} catch (IOException e) {
				// nothing we can do
			}
		}
		return pubMap;
	}

	@Override
	public List<WebProfile> getEntries(String title, String[] platforms) throws IOException {
		List<WebProfile> allEntries = new ArrayList<>();

		int page = 0;
		String url = addKey(HTTPS_PROTOCOL + THE_GAMES_DB_API + "/v1/Games/ByGameName?name=" + URLEncoder.encode(prepareSearchTerm(title), StandardCharsets.UTF_8)
				+ "&fields=overview,publishers,genres,os");

		String platformIds = getPlatforms().entrySet().stream().filter(x -> Strings.CI.equalsAny(x.getValue(), platforms))
			.map(x -> String.valueOf(x.getKey())).collect(Collectors.joining(","));
		if (StringUtils.isNotBlank(platformIds)) {
			url += "&filter[platform]=" + platformIds;
		}

		while ((url != null) && (page < MAX_PAGES)) {
			String json = getResponse(url);
			
			Iterator<Object> gameIt = new JSONObject(json).getJSONObject("data").getJSONArray("games").iterator();
			while (gameIt.hasNext()) {
				allEntries.add(getWebProfile((JSONObject)gameIt.next()));
			}
			
			url = new JSONObject(json).getJSONObject("pages").optString("next", null);
			page++;
		}

		return allEntries;
	}
	
	private WebProfile getWebProfile(JSONObject gameObj) {
		WebProfile gameEntry = new WebProfile();
		gameEntry.setGameId(gameObj.getInt("id"));
		gameEntry.setTitle(gameObj.getString("game_title"));
		gameEntry.setNotes(gameObj.optString("overview"));
		gameEntry.setUrl(HTTPS_PROTOCOL + THE_GAMES_DB_HOST_NAME + "/game.php?id=" + gameEntry.getGameId());

		String date = gameObj.optString("release_date");
		gameEntry.setYear(date.length() == 10 ? date.substring(0, 4): StringUtils.EMPTY);

		gameEntry.setPlatformId(gameObj.getInt("platform"));
		String platform = Objects.toString(getPlatforms().get(gameEntry.getPlatformId()), String.valueOf(gameEntry.getPlatformId()));
		String os = gameObj.optString("os"); // should contain MS-DOS
		gameEntry.setPlatform(platform + (StringUtils.isBlank(os) ? StringUtils.EMPTY : " (" + os + ")"));

		JSONArray genres = gameObj.optJSONArray("genres");
		gameEntry.setGenre(genres == null ? StringUtils.EMPTY: StringUtils.join(genres.toList().stream().filter(x -> getGenres().containsKey(x)).map(x -> getGenres().get(x)).iterator(), ", "));

		JSONArray developers = gameObj.optJSONArray("developers");
		gameEntry.setDeveloperName(developers == null ? StringUtils.EMPTY: StringUtils.join(developers.toList().stream().filter(x -> getDevelopers().containsKey(x)).map(x -> getDevelopers().get(x)).iterator(), ", "));

		JSONArray publishers = gameObj.optJSONArray("publishers");
		gameEntry.setPublisherName(publishers == null ? StringUtils.EMPTY: StringUtils.join(publishers.toList().stream().filter(x -> getPublishers().containsKey(x)).map(x -> getPublishers().get(x)).iterator(), ", "));

		return gameEntry;
	}

	@Override
	public WebProfile getEntryDetailedInformation(WebProfile entry) throws IOException {
		if (StringUtils.isBlank(entry.getTitle()) && entry.getGameId() != 0) {
			String url = addKey(HTTPS_PROTOCOL + THE_GAMES_DB_API + "/v1/Games/ByGameID?id=" + entry.getGameId() + "&fields=overview,publishers,genres,platform,os");
			Iterator<Object> gameIt = new JSONObject(getResponse(url)).getJSONObject("data").getJSONArray("games").iterator();
			while (gameIt.hasNext()) {
				entry = getWebProfile((JSONObject)gameIt.next());
			}
		}
		return entry;
	}

	@Override
	public SearchEngineImageInformation[] getEntryImages(WebProfile entry, int coverArtMax, int screenshotsMax, boolean forceAllRegionsCoverArt) throws IOException {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		
		if ((coverArtMax > 0) || (screenshotsMax > 0)) {
			int coverArt = 0;
			int screenshots = 0;
			String base = SettingsService.getInstance().getValue(getSimpleName(), "image_base");
			String filter = SettingsService.getInstance().getValue(getSimpleName(), "image_filter");
			
			String response = getResponse(addKey(HTTPS_PROTOCOL + THE_GAMES_DB_API + "/v1/Games/Images?games_id=" + entry.getGameId() + (StringUtils.isBlank(filter) ? "": "filter[type]=" + filter)));
			JSONObject data = new JSONObject(response).getJSONObject("data");
			JSONObject games = data.getJSONObject("images");
			Iterator<String> gamesIterator = games.keys();

			while (gamesIterator.hasNext()) {
				Iterator<Object> imageIterator = ((JSONArray) games.get(gamesIterator.next())).iterator();
			
				while (imageIterator.hasNext()) {
					JSONObject imgObj = (JSONObject)imageIterator.next();
				
					SearchEngineImageType type = Strings.CS.equalsAny(imgObj.getString("type"), "screenshot", "titlescreen") ? SearchEngineImageType.SCREENSHOT: SearchEngineImageType.COVER_ART;
					if ((type == SearchEngineImageType.SCREENSHOT && screenshots < screenshotsMax) || (type == SearchEngineImageType.COVER_ART && coverArt < coverArtMax)) {
						String url = data.getJSONObject("base_url").optString(base) + imgObj.getString("filename");
						String descr = Stream.of(new String[] { imgObj.getString("type"), imgObj.optString("side"), String.valueOf(imgObj.getInt("id")) })
								.filter(x -> StringUtils.isNotBlank(x)).collect(Collectors.joining(" "));
						result.add(new SearchEngineImageInformation(type, url, descr));
						if (type == SearchEngineImageType.SCREENSHOT)
							screenshots++;
						else
							coverArt++;
					}
				};
			}
		}
		
		entry.setWebImages(result.toArray(new SearchEngineImageInformation[0]));
		return entry.getWebImages();
	}
}