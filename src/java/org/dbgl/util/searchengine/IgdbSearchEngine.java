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
 * IGDB (igdb.com, run by Twitch): game data and cover art and screenshots, many DOS games among them. Each user needs a free
 * Twitch developer application: its Client ID and Client Secret go into the settings. See https://api-docs.igdb.com
 */
public class IgdbSearchEngine extends WebSearchEngine {

	public static final String CLIENT_ID = "client_id", CLIENT_SECRET = "client_secret";

	static final String API = "https://api.igdb.com/v4/";
	static final String TOKEN_URL = "https://id.twitch.tv/oauth2/token";
	static final String IMAGES = "https://images.igdb.com/igdb/image/upload/";
	static final int PLATFORM_DOS = 13, PLATFORM_WINDOWS = 6;

	private static String token_;
	private static long tokenValidUntil_;

	private IgdbSearchEngine() {
	}

	private static class SearchEngineHolder {
		private static WebSearchEngine instance_ = new IgdbSearchEngine();
	}

	public static WebSearchEngine getInstance() {
		return SearchEngineHolder.instance_;
	}

	@Override
	public String getIcon() {
		return ImageService.IMG_IGDB;
	}

	@Override
	public String getName() {
		return "IGDB";
	}

	@Override
	public String getSimpleName() {
		return "igdb";
	}

	@Override
	public boolean available() {
		return true;
	}

	private static String setting(String key) {
		return StringUtils.trimToEmpty(SettingsService.getInstance().getValue("igdb", key));
	}

	/** an access token for the Client ID and Secret, asked for again when it ran out (about every two months) */
	private synchronized String token() throws IOException {
		String id = setting(CLIENT_ID), secret = setting(CLIENT_SECRET);
		if (id.isEmpty() || secret.isEmpty())
			throw SearchEngineException.key(getName());
		if ((token_ == null) || (System.currentTimeMillis() > tokenValidUntil_)) {
			String answer;
			try {
				answer = request(getName(), TOKEN_URL + "?client_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8) + "&client_secret="
						+ URLEncoder.encode(secret, StandardCharsets.UTF_8) + "&grant_type=client_credentials", Map.of(), "");
			} catch (SearchEngineException e) {
				if (e.getMessage() != null && (e.getMessage().contains("400") || e.getMessage().contains("403")))
					throw SearchEngineException.key(getName()); // Twitch answers 400/403 to a wrong ID or secret
				throw e;
			}
			JSONObject json = new JSONObject(answer);
			token_ = json.getString("access_token");
			tokenValidUntil_ = System.currentTimeMillis() + (json.optLong("expires_in", 3600) - 60) * 1000L;
		}
		return token_;
	}

	JSONArray query(String endpoint, String apicalypse) throws IOException {
		String answer = request(getName(), API + endpoint, Map.of("Client-ID", setting(CLIENT_ID), "Authorization", "Bearer " + token(), "Content-Type", "text/plain"), apicalypse);
		try {
			return new JSONArray(answer);
		} catch (RuntimeException e) {
			throw SearchEngineException.changed(getName(), e);
		}
	}

	/** platform filter: IGDB IDs, or names like "PC DOS" / "DOS" (13) and "PC" / "Windows" (6) */
	static Set<Integer> platformIds(String[] platforms) {
		Set<Integer> ids = new LinkedHashSet<>();
		for (String p: platforms) {
			String s = StringUtils.trimToEmpty(p).toLowerCase();
			if (s.matches("\\d+"))
				ids.add(Integer.parseInt(s));
			else if (s.contains("dos"))
				ids.add(PLATFORM_DOS);
			else if (s.equals("pc") || s.contains("windows"))
				ids.add(PLATFORM_WINDOWS);
		}
		return ids;
	}

	static String quoted(String s) {
		return '"' + s.replace("\\", " ").replace("\"", " ") + '"';
	}

	static String year(JSONObject game) {
		long date = game.optLong("first_release_date", 0);
		return date == 0 ? "": String.valueOf(Instant.ofEpochSecond(date).atZone(ZoneOffset.UTC).getYear());
	}

	static String names(JSONArray array, String field) {
		List<String> result = new ArrayList<>();
		if (array != null)
			for (int i = 0; i < array.length(); i++) {
				String n = array.getJSONObject(i).optString(field, "");
				if (!n.isEmpty() && !result.contains(n))
					result.add(n);
			}
		return String.join(", ", result);
	}

	@Override
	public List<WebProfile> getEntries(String title, String[] platforms) throws IOException {
		Set<Integer> ids = platformIds(platforms);
		String where = ids.isEmpty() ? "": " where platforms = (" + StringUtils.join(ids, ',') + ");";
		JSONArray games = query("games", "search " + quoted(prepareSearchTerm(title)) + "; fields name,first_release_date,platforms.name,url;" + where + " limit 50;");
		List<WebProfile> result = new ArrayList<>();
		for (int i = 0; i < games.length(); i++) {
			JSONObject g = games.getJSONObject(i);
			WebProfile p = emptyWebProfile();
			p.setGameId(g.getInt("id"));
			p.setTitle(g.optString("name", ""));
			p.setYear(year(g));
			p.setPlatform(names(g.optJSONArray("platforms"), "name"));
			p.setUrl(g.optString("url", ""));
			result.add(p);
		}
		return result;
	}

	@Override
	public WebProfile getEntryDetailedInformation(WebProfile entry) throws IOException {
		JSONArray games = query("games", "fields name,first_release_date,summary,total_rating,url,genres.name,platforms.name,involved_companies.company.name,"
				+ "involved_companies.developer,involved_companies.publisher; where id = " + entry.getGameId() + ";");
		if (games.isEmpty())
			return entry;
		JSONObject g = games.getJSONObject(0);
		entry.setTitle(g.optString("name", entry.getTitle()));
		entry.setYear(year(g));
		entry.setNotes(g.optString("summary", ""));
		entry.setUrl(g.optString("url", entry.getUrl()));
		entry.setGenre(names(g.optJSONArray("genres"), "name"));
		entry.setRank((int)Math.round(g.optDouble("total_rating", 0)));
		List<String> developers = new ArrayList<>(), publishers = new ArrayList<>();
		JSONArray companies = g.optJSONArray("involved_companies");
		if (companies != null)
			for (int i = 0; i < companies.length(); i++) {
				JSONObject c = companies.getJSONObject(i);
				JSONObject company = c.optJSONObject("company");
				String name = company == null ? "": company.optString("name", "");
				if (name.isEmpty())
					continue;
				if (c.optBoolean("developer") && !developers.contains(name))
					developers.add(name);
				if (c.optBoolean("publisher") && !publishers.contains(name))
					publishers.add(name);
			}
		entry.setDeveloperName(String.join(", ", developers));
		entry.setPublisherName(String.join(", ", publishers));
		return entry;
	}

	@Override
	public SearchEngineImageInformation[] getEntryImages(WebProfile entry, int coverArtMax, int screenshotsMax, boolean forceAllRegionsCoverArt) throws IOException {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		if ((coverArtMax > 0) || (screenshotsMax > 0)) {
			JSONArray games = query("games", "fields cover.image_id,screenshots.image_id; where id = " + entry.getGameId() + ";");
			if (!games.isEmpty()) {
				JSONObject g = games.getJSONObject(0);
				JSONObject cover = g.optJSONObject("cover");
				if ((coverArtMax > 0) && (cover != null) && cover.has("image_id"))
					result.add(new SearchEngineImageInformation(SearchEngineImageType.COVER_ART, IMAGES + "t_cover_big/" + cover.getString("image_id") + ".jpg", "Cover"));
				JSONArray shots = g.optJSONArray("screenshots");
				if (shots != null)
					for (int i = 0; (i < shots.length()) && (i < screenshotsMax); i++)
						result.add(new SearchEngineImageInformation(SearchEngineImageType.SCREENSHOT, IMAGES + "t_screenshot_big/" + shots.getJSONObject(i).getString("image_id") + ".jpg",
								"Screenshot " + (i + 1)));
			}
		}
		entry.setWebImages(result.toArray(new SearchEngineImageInformation[0]));
		return entry.getWebImages();
	}
}
