/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-07. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 */
/*
 *  Copyright (C) 2006-2022  Ronald Blankendaal
 *
 *  Many thanks to Manuel J. Gallego for his work on MobyGames querying
 *  for TincoreADB. This file is based on his code.
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
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.SearchEngineImageInformation;
import org.dbgl.model.SearchEngineImageInformation.SearchEngineImageType;
import org.dbgl.model.WebProfile;
import org.dbgl.service.ImageService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class MobyGamesSearchEngine extends WebSearchEngine {

	static final String MOBY_GAMES_HOST_NAME = "www.mobygames.com";
	static final String USER_AGENT = "Mozilla";

	/**
	 * At most this many result pages are loaded (MobyGames has some 25 games per page). A short or common title found hundreds
	 * of pages, each one a request of up to half a minute, and DBGL looked as if it hung in an endless loop.
	 */
	static final int MAX_PAGES = 5;

	private MobyGamesSearchEngine() {
	}

	private static class SearchEngineHolder {
		private static WebSearchEngine instance_ = new MobyGamesSearchEngine();
	}

	public static WebSearchEngine getInstance() {
		return SearchEngineHolder.instance_;
	}

	@Override
	public String getIcon() {
		return ImageService.IMG_MOBYGAMES;
	}

	@Override
	public String getName() {
		return "MobyGames";
	}

	@Override
	public String getSimpleName() {
		return "mobygames";
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public List<WebProfile> getEntries(String title, String[] platforms) throws IOException {
		if (Api.enabled())
			return Api.getEntries(getName(), prepareSearchTerm(title), platforms);
		try {
			return getEntriesUnchecked(title, platforms);
		} catch (RuntimeException e) {
			throw SearchEngineException.changed(getName(), e); // e.g. no game list on the page: rebuilt, or a captcha page
		}
	}

	private List<WebProfile> getEntriesUnchecked(String title, String[] platforms) throws IOException {
		int page = 0;
		int pages = 1;
		List<WebProfile> allEntries = new ArrayList<>();

		while (page < pages) {
			String url = HTTPS_PROTOCOL + MOBY_GAMES_HOST_NAME + "/game/";
			for (String platform: platforms) {
				url += "platform:" + platform + "/"; 
			}
			url += "title:" + URLEncoder.encode(prepareSearchTerm(title), StandardCharsets.UTF_8)
				+ "/sort:title/page:" + (page + 1) + "/";
			
			Document doc;
			try {
				doc = fetchDocument(getName(), url, USER_AGENT);
			} catch (SearchEngineException e) {
				if ((e.getMessage() != null) && e.getMessage().contains("403"))
					throw new SearchEngineException(org.dbgl.service.TextService.getInstance().get("searchengine.error.mobygames.apikey2"), e);
				throw e;
			}
			Element browser = doc.selectFirst("game-browser");
			if (browser == null)
				throw SearchEngineException.changed(getName(), null);
			JSONObject json = new JSONObject(browser.attr(":initial-values"));
			
			if (page == 0) {
				int perPage = Math.max(1, json.getInt("perPage"));
				pages = Math.min(MAX_PAGES, (json.getInt("total") + perPage - 1) / perPage);
			}
			allEntries.addAll(extractEntries(json.getJSONArray("games"), platforms));
			page++;
		}

		return allEntries;
	}

	private static List<WebProfile> extractEntries(JSONArray games, String[] platforms) {
		List<WebProfile> allEntries = new ArrayList<>();
		for (int i = 0; i < games.length(); i++) {
			JSONObject game = games.getJSONObject(i);
			JSONArray gamePlatforms = game.getJSONArray("platforms");
			for (int j = 0; j < gamePlatforms.length(); j++) {
				JSONObject platform = gamePlatforms.getJSONObject(j);
				String name = platform.getString("name");
				if ((platforms.length == 0) || Stream.of(platforms).anyMatch(x -> x.equalsIgnoreCase(name))) {
					WebProfile gameEntry = new MobyGamesWebProfile();
					gameEntry.setTitle(game.getString("title"));
					gameEntry.setUrl(game.getString("internal_url"));
					gameEntry.setPlatform(name);
					gameEntry.setYear(platform.getString("release_date"));
					allEntries.add(gameEntry);
				}
			}
		}
		return allEntries;
	}

	@Override
	public WebProfile getEntryDetailedInformation(WebProfile entry) throws IOException {
		if (entry instanceof Api.Entry e)
			return Api.getDetails(getName(), e);
		try {
			return getEntryDetailedInformationUnchecked(entry);
		} catch (RuntimeException e) {
			throw SearchEngineException.changed(getName(), e);
		}
	}

	private WebProfile getEntryDetailedInformationUnchecked(WebProfile entry) throws IOException {
		Document doc = fetchDocument(getName(), entry.getUrl(), USER_AGENT);
		Element infoBlock = doc.selectFirst("div#infoBlock");
		if (infoBlock == null)
			throw SearchEngineException.changed(getName(), null);
		entry.setDeveloperName(StringUtils.join(infoBlock.select("ul#developerLinks > li > a").eachText(), ", "));
		entry.setPublisherName(StringUtils.join(infoBlock.select("ul#publisherLinks > li > a").eachText(), ", "));
		entry.setGenre(StringUtils.join(infoBlock.select("div.info-genres > dl.metadata > dt:containsWholeOwnText(Genre) + dd > a").eachText(), ", "));
		Element descrElmt = doc.selectFirst("section#gameDescription > div#description-text");
		entry.setNotes(descrElmt == null ? "": unescapeHtml(removeAllTags(descrElmt.html())));
		entry.setRank(extractRank(infoBlock));
		entry.setCoreGameCoverUrl(StringUtils.stripToNull(doc.select("meta[property=og:image]").attr("content")));
		if (StringUtils.isEmpty(entry.getDeveloperName()))
			entry.setDeveloperName(StringUtils.join(doc.select("section#gameCredits table.table-credits tr > td:last-child").eachText(), ", "));
		return entry;
	}

	@Override
	public SearchEngineImageInformation[] getEntryImages(WebProfile entry, int coverArtMax, int screenshotsMax, boolean forceAllRegionsCoverArt) throws IOException {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		if (entry instanceof Api.Entry e) {
			result.addAll(Api.getImages(getName(), e, coverArtMax, screenshotsMax));
			entry.setWebImages(result.toArray(new SearchEngineImageInformation[0]));
			return entry.getWebImages();
		}
		try {
			if (coverArtMax > 0) {
				result.addAll(getEntryCoverArtInformation(entry, coverArtMax));
			}
			if (screenshotsMax > 0) {
				result.addAll(getEntryScreenshotInformation(entry, screenshotsMax));
			}
		} catch (RuntimeException e) {
			throw SearchEngineException.changed(getName(), e);
		}
		entry.setWebImages(result.toArray(new SearchEngineImageInformation[0]));
		return entry.getWebImages();
	}

	private static Integer extractRank(Element infoBlock) throws IOException {
		try {
			Element score = infoBlock.selectFirst("div.mobyscore");
			return score == null ? 0: (int)(Double.valueOf(score.text()) * 10d);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static List<SearchEngineImageInformation> getEntryScreenshotInformation(WebProfile entry, int max) throws IOException {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		Document doc = fetchDocument("MobyGames", entry.getScreenshotsUrl(), USER_AGENT);
		Elements figures = doc.select("div > h2:containsWholeOwnText(DOS screenshots) ~ div.img-holder > figure");
		for (Element fig: figures) {
			result.add(new SearchEngineImageInformation(SearchEngineImageType.SCREENSHOT,
				fig.selectFirst("a").attr("href"), fig.selectFirst("figcaption").text()));
			if (result.size() >= max)
				break;
		}
		return result;
	}

	private static List<SearchEngineImageInformation> getEntryCoverArtInformation(WebProfile entry, int max) throws IOException {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		if (entry.getCoreGameCoverUrl() != null) {
			Document doc = fetchDocument("MobyGames", entry.getCoverArtUrl(), USER_AGENT);
			Elements figures = doc.select("section > h2:has(a:containsWholeOwnText(DOS)) ~ div.img-holder > figure, section > h2:has(a:containsWholeOwnText(DOS)) ~ div.sensitive-blur-wrapper>div.blurred-content>div.img-holder > figure");
			for (Element fig: figures) {
				result.add(new SearchEngineImageInformation(SearchEngineImageType.COVER_ART,
					fig.selectFirst("a").attr("href"), fig.selectFirst("figcaption").text()));
				if (result.size() >= max)
					break;
			}
		}
		return result;
	}

	/**
	 * The official MobyGames API (https://www.mobygames.com/info/api), used when an API key is entered in the settings.
	 * MobyGames blocks automated loading of its web pages (403), the API is the way it allows (the key needs a paid plan). The key allows about one
	 * request per second (and some 360 per hour), so requests are spaced out.
	 */
	public static final class Api {

		static final String BASE = "https://api.mobygames.com/v1/";
		static final String API_KEY = "api_key";
		static final long MIN_GAP_MILLIS = 1100;

		private static long lastRequest_;

		/** an entry found: the game on one platform */
		public static final class Entry extends WebProfile {
			int platformId_;
			String description_ = "", genres_ = "";
			int score_;
		}

		private Api() {
		}

		static String key() {
			return StringUtils.trimToEmpty(org.dbgl.service.SettingsService.getInstance().getValue("mobygames", API_KEY));
		}

		static boolean enabled() {
			return !key().isEmpty();
		}

		/** MobyGames platform IDs for the names in the platform filter: DOS 2, Windows 3, PC Booter 4, Windows 3.x 5 */
		public static List<Integer> platformIds(String[] platforms) {
			List<Integer> ids = new ArrayList<>();
			for (String p: platforms) {
				String s = StringUtils.trimToEmpty(p).toLowerCase();
				Integer id = s.matches("\\d+") ? Integer.valueOf(s): switch (s) {
					case "dos", "pc dos", "ms-dos" -> 2;
					case "windows" -> 3;
					case "pc booter" -> 4;
					case "windows 3.x" -> 5;
					default -> null;
				};
				if ((id != null) && !ids.contains(id))
					ids.add(id);
			}
			return ids;
		}

		static synchronized JSONObject get(String engineName, String path, String query) throws IOException {
			long wait = lastRequest_ + MIN_GAP_MILLIS - System.currentTimeMillis();
			if (wait > 0) {
				try {
					Thread.sleep(wait);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException(e);
				}
			}
			try {
				String url = BASE + path + "?" + (query.isEmpty() ? "": query + "&") + API_KEY + "=" + URLEncoder.encode(key(), StandardCharsets.UTF_8);
				return new JSONObject(request(engineName, url, java.util.Map.of(), null));
			} catch (org.json.JSONException e) {
				throw SearchEngineException.changed(engineName, e);
			} finally {
				lastRequest_ = System.currentTimeMillis();
			}
		}

		static String year(String date) {
			return (date != null) && (date.length() >= 4) ? date.substring(0, 4): "";
		}

		static List<WebProfile> getEntries(String engineName, String title, String[] platforms) throws IOException {
			List<Integer> ids = platformIds(platforms);
			StringBuilder query = new StringBuilder("title=" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&format=normal&limit=100");
			for (Integer id: ids)
				query.append("&platform=").append(id);
			return parseEntries(get(engineName, "games", query.toString()), ids);
		}

		/** @param json the answer of /games (format normal), split off to be tested with saved answers */
		public static List<WebProfile> parseEntries(JSONObject json, List<Integer> ids) {
			JSONArray games = json.optJSONArray("games");
			List<WebProfile> result = new ArrayList<>();
			if (games == null)
				return result;
			for (int i = 0; i < games.length(); i++) {
				JSONObject g = games.getJSONObject(i);
				JSONArray ps = g.optJSONArray("platforms");
				if (ps == null)
					continue;
				List<String> genres = new ArrayList<>();
				JSONArray gs = g.optJSONArray("genres");
				if (gs != null)
					for (int j = 0; j < gs.length(); j++) {
						JSONObject genre = gs.getJSONObject(j);
						if ("Basic Genres".equalsIgnoreCase(genre.optString("genre_category")) && !genres.contains(genre.optString("genre_name")))
							genres.add(genre.optString("genre_name"));
					}
				for (int j = 0; j < ps.length(); j++) {
					JSONObject p = ps.getJSONObject(j);
					if (!ids.isEmpty() && !ids.contains(p.optInt("platform_id")))
						continue;
					Entry e = new Entry();
					e.setTitle(g.optString("title", ""));
					e.setPlatform(p.optString("platform_name", ""));
					e.setYear(year(p.optString("first_release_date", null)));
					e.setUrl(g.optString("moby_url", ""));
					e.setDeveloperName("");
					e.setPublisherName("");
					e.setGenre(String.join(", ", genres));
					e.setNotes("");
					e.setGameId(g.optInt("game_id"));
					e.platformId_ = p.optInt("platform_id");
					e.description_ = g.isNull("description") ? "": g.optString("description", "");
					e.genres_ = e.getGenre();
					e.score_ = (int)Math.round(g.optDouble("moby_score", 0) * 10d);
					result.add(e);
				}
			}
			return result;
		}

		static WebProfile getDetails(String engineName, Entry e) throws IOException {
			parseDetails(e, get(engineName, "games/" + e.getGameId() + "/platforms/" + e.platformId_, ""));
			return e;
		}

		/** @param details the answer of /games/{id}/platforms/{platform} */
		public static void parseDetails(Entry e, JSONObject details) {
			e.setNotes(e.description_.isEmpty() ? "": unescapeHtml(removeAllTags(e.description_)));
			e.setGenre(e.genres_);
			e.setRank(e.score_);
			List<String> developers = new ArrayList<>(), publishers = new ArrayList<>();
			JSONArray releases = details.optJSONArray("releases");
			if (releases != null)
				for (int i = 0; i < releases.length(); i++) {
					JSONArray companies = releases.getJSONObject(i).optJSONArray("companies");
					if (companies == null)
						continue;
					for (int j = 0; j < companies.length(); j++) {
						JSONObject c = companies.getJSONObject(j);
						String name = c.optString("company_name", ""), role = c.optString("role", "").toLowerCase();
						if (name.isEmpty())
							continue;
						if (role.contains("develop") && !developers.contains(name))
							developers.add(name);
						if (role.contains("publish") && !publishers.contains(name))
							publishers.add(name);
					}
				}
			e.setDeveloperName(String.join(", ", developers));
			e.setPublisherName(String.join(", ", publishers));
			String date = details.optString("first_release_date", null);
			if (StringUtils.isNotBlank(date))
				e.setYear(year(date));
		}

		static List<SearchEngineImageInformation> getImages(String engineName, Entry e, int coverArtMax, int screenshotsMax) throws IOException {
			List<SearchEngineImageInformation> result = new ArrayList<>();
			String base = "games/" + e.getGameId() + "/platforms/" + e.platformId_ + "/";
			if (coverArtMax > 0)
				result.addAll(parseCovers(get(engineName, base + "covers", ""), coverArtMax));
			if (screenshotsMax > 0)
				result.addAll(parseScreenshots(get(engineName, base + "screenshots", ""), screenshotsMax));
			return result;
		}

		/** @param json the answer of /games/{id}/platforms/{platform}/covers */
		public static List<SearchEngineImageInformation> parseCovers(JSONObject json, int coverArtMax) {
			List<SearchEngineImageInformation> result = new ArrayList<>();
			{
				JSONArray groups = json.optJSONArray("cover_groups");
				int found = 0;
				for (int i = 0; (groups != null) && (i < groups.length()) && (found < coverArtMax); i++) {
					JSONArray covers = groups.getJSONObject(i).optJSONArray("covers");
					for (int j = 0; (covers != null) && (j < covers.length()) && (found < coverArtMax); j++) {
						JSONObject c = covers.getJSONObject(j);
						if (c.optString("image", "").isEmpty())
							continue;
						result.add(new SearchEngineImageInformation(SearchEngineImageType.COVER_ART, c.getString("image"), c.optString("scan_of", "Cover")));
						found++;
					}
				}
			}
			return result;
		}

		/** @param json the answer of /games/{id}/platforms/{platform}/screenshots */
		public static List<SearchEngineImageInformation> parseScreenshots(JSONObject json, int screenshotsMax) {
			List<SearchEngineImageInformation> result = new ArrayList<>();
			{
				JSONArray shots = json.optJSONArray("screenshots");
				for (int i = 0; (shots != null) && (i < shots.length()) && (i < screenshotsMax); i++) {
					JSONObject sh = shots.getJSONObject(i);
					result.add(new SearchEngineImageInformation(SearchEngineImageType.SCREENSHOT, sh.optString("image", ""), sh.isNull("caption") ? "Screenshot " + (i + 1): sh.optString("caption")));
				}
			}
			return result;
		}
	}
}
