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
 * ScreenScraper (https://www.screenscraper.fr), through its API v2: GET requests to https://api.screenscraper.fr/api2/ with
 * the developer access of the program (devid, devpassword, softname) and the account of the user (ssid, sspassword).
 * ScreenScraper gives the developer access to the author of a program on request; until DBGL has one, this source is not
 * offered (see available()). One search (jeuRecherche.php) already brings the whole game with its media, so details and
 * images need no further request: the daily quota of the user is spent sparingly.
 */
public class ScreenScraperSearchEngine extends WebSearchEngine {

	static final String API = "https://api.screenscraper.fr/api2/";
	static final String SITE = "https://www.screenscraper.fr/gameinfos.php?gameid=";
	static final String SOFTNAME = "DBGL-Extended-Edition";
	/** the developer access of DBGL, from ScreenScraper; empty until it is given (then this source is not offered) */
	static final String DEV_ID = "", DEV_PASSWORD = "";
	public static final String USER = "ssid", PASSWORD = "sspassword";
	/** ScreenScraper's system ids: PC Dos 135, PC Win3.xx 136, PC Windows 138 */
	static final int SYSTEM_DOS = 135;

	/** a game found, with all ScreenScraper gave for it */
	public static final class Entry extends WebProfile {
		JSONObject game_;
	}

	private ScreenScraperSearchEngine() {
	}

	private static class SearchEngineHolder {
		private static WebSearchEngine instance_ = new ScreenScraperSearchEngine();
	}

	public static WebSearchEngine getInstance() {
		return SearchEngineHolder.instance_;
	}

	@Override
	public String getIcon() {
		return ImageService.IMG_SCREENSCRAPER;
	}

	@Override
	public String getName() {
		return "ScreenScraper";
	}

	@Override
	public String getSimpleName() {
		return "screenscraper";
	}

	/** the developer access, which may also be given in the settings (devid, devpassword) */
	static String dev(String key, String builtIn) {
		String v = StringUtils.trimToEmpty(SettingsService.getInstance().getValue("screenscraper", key));
		return v.isEmpty() ? builtIn: v;
	}

	@Override
	public boolean available() {
		return !dev("devid", DEV_ID).isEmpty() && !dev("devpassword", DEV_PASSWORD).isEmpty();
	}

	JSONObject get(String endpoint, String query) throws IOException {
		SettingsService settings = SettingsService.getInstance();
		String user = StringUtils.trimToEmpty(settings.getValue(getSimpleName(), USER)), password = StringUtils.trimToEmpty(settings.getValue(getSimpleName(), PASSWORD));
		String url = API + endpoint + "?devid=" + enc(dev("devid", DEV_ID)) + "&devpassword=" + enc(dev("devpassword", DEV_PASSWORD)) + "&softname=" + enc(SOFTNAME) + "&output=json"
				+ (user.isEmpty() ? "": "&ssid=" + enc(user) + "&sspassword=" + enc(password)) + "&" + query;
		String answer;
		try {
			answer = request(getName(), url, Map.of(), null);
		} catch (SearchEngineException e) {
			Integer status = statusIn(e.getMessage());
			if (status != null)
				throw new SearchEngineException(org.dbgl.service.TextService.getInstance().get("searchengine.error.screenscraper", new Object[] {String.valueOf(status)}), e);
			throw e;
		}
		try {
			return new JSONObject(answer).optJSONObject("response") == null ? new JSONObject(): new JSONObject(answer).getJSONObject("response");
		} catch (RuntimeException e) {
			// ScreenScraper answers some problems as text, e.g. "Erreur de login : Vérifier vos identifiants développeur !"
			if (answer.toLowerCase().contains("identifiant") || answer.toLowerCase().contains("login"))
				throw SearchEngineException.key(getName());
			throw SearchEngineException.changed(getName(), e);
		}
	}

	/** the HTTP status a message of SearchEngineException.http names, for the codes ScreenScraper uses */
	public static Integer statusIn(String message) {
		if (message == null)
			return null;
		for (int s: new int[] {401, 403, 423, 426, 429, 430, 431})
			if (message.contains(String.valueOf(s)))
				return s;
		return null;
	}

	static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	@Override
	public List<WebProfile> getEntries(String title, String[] platforms) throws IOException {
		List<Integer> systems = systemIds(platforms);
		List<WebProfile> result = new ArrayList<>();
		for (int system: systems)
			result.addAll(parseEntries(get("jeuRecherche.php", "systemeid=" + system + "&recherche=" + enc(prepareSearchTerm(title)))));
		return result;
	}

	/** the system ids in the platform filter (numbers, or the names DOS, Windows 3.x, Windows); PC Dos if none */
	public static List<Integer> systemIds(String[] platforms) {
		List<Integer> ids = new ArrayList<>();
		if (platforms != null)
			for (String p: platforms) {
				String s = StringUtils.trimToEmpty(p).toLowerCase();
				Integer id = s.matches("\\d+") ? Integer.valueOf(s): switch (s) {
					case "dos", "pc dos", "ms-dos" -> SYSTEM_DOS;
					case "windows 3.x", "pc win3.xx" -> 136;
					case "windows", "pc windows" -> 138;
					default -> null;
				};
				if ((id != null) && !ids.contains(id))
					ids.add(id);
			}
		if (ids.isEmpty())
			ids.add(SYSTEM_DOS);
		return ids;
	}

	/** @param response the "response" of jeuRecherche.php, split off to be tested with saved answers */
	public static List<WebProfile> parseEntries(JSONObject response) {
		List<WebProfile> result = new ArrayList<>();
		JSONArray games = response.optJSONArray("jeux");
		if (games == null)
			return result;
		for (int i = 0; i < games.length(); i++) {
			JSONObject g = games.optJSONObject(i);
			if ((g == null) || !g.has("id")) // no game found: ScreenScraper answers [{}]
				continue;
			Entry e = new Entry();
			e.game_ = g;
			e.setGameId(g.optInt("id"));
			e.setTitle(byRegion(g.optJSONArray("noms")));
			JSONObject system = g.optJSONObject("systeme");
			e.setPlatform(system == null ? "": system.optString("text", ""));
			String date = byRegion(g.optJSONArray("dates"));
			e.setYear(date.length() >= 4 ? date.substring(0, 4): "");
			e.setUrl(SITE + e.getGameId());
			e.setDeveloperName("");
			e.setPublisherName("");
			e.setGenre("");
			e.setNotes("");
			result.add(e);
		}
		return result;
	}

	@Override
	public WebProfile getEntryDetailedInformation(WebProfile entry) throws IOException {
		if (entry instanceof Entry e)
			parseDetails(e, language());
		return entry;
	}

	static String language() {
		String l = StringUtils.defaultString(SettingsService.getInstance().getValue("locale", "language"), "en").toLowerCase();
		return l.length() > 2 ? l.substring(0, 2): l;
	}

	/** developer, publisher, genres (in the language of DBGL, else English), description and rating (0-20 at ScreenScraper) */
	public static void parseDetails(Entry e, String language) {
		JSONObject g = e.game_;
		JSONObject dev = g.optJSONObject("developpeur"), pub = g.optJSONObject("editeur"), note = g.optJSONObject("note");
		e.setDeveloperName(dev == null ? "": dev.optString("text", ""));
		e.setPublisherName(pub == null ? "": pub.optString("text", ""));
		List<String> genres = new ArrayList<>();
		JSONArray gs = g.optJSONArray("genres");
		if (gs != null)
			for (int i = 0; i < gs.length(); i++) {
				String n = byLanguage(gs.getJSONObject(i).optJSONArray("noms"), language);
				if (!n.isEmpty() && !genres.contains(n))
					genres.add(n);
			}
		e.setGenre(String.join(", ", genres));
		e.setNotes(byLanguage(g.optJSONArray("synopsis"), language));
		double rating = 0;
		try {
			rating = note == null ? 0: Double.parseDouble(note.optString("text", "0"));
		} catch (NumberFormatException ex) {
			rating = 0;
		}
		e.setRank((int)Math.round(rating * 5));
	}

	@Override
	public SearchEngineImageInformation[] getEntryImages(WebProfile entry, int coverArtMax, int screenshotsMax, boolean forceAllRegionsCoverArt) throws IOException {
		List<SearchEngineImageInformation> result = entry instanceof Entry e ? parseImages(e, coverArtMax, screenshotsMax): new ArrayList<>();
		entry.setWebImages(result.toArray(new SearchEngineImageInformation[0]));
		return entry.getWebImages();
	}

	/** covers (box-2D, the regions in order of REGIONS) and screenshots (title screen first, then in-game) */
	public static List<SearchEngineImageInformation> parseImages(Entry e, int coverArtMax, int screenshotsMax) {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		JSONArray medias = e.game_.optJSONArray("medias");
		if (medias == null)
			return result;
		List<JSONObject> boxes = new ArrayList<>(), shots = new ArrayList<>();
		for (int i = 0; i < medias.length(); i++) {
			JSONObject m = medias.getJSONObject(i);
			String type = m.optString("type", "");
			if (m.optString("url", "").isEmpty())
				continue;
			if (type.equals("box-2D"))
				boxes.add(m);
			else if (type.equals("sstitle") || type.equals("ss"))
				shots.add(m);
		}
		boxes.sort((a, b) -> Integer.compare(regionRank(a.optString("region", "")), regionRank(b.optString("region", ""))));
		shots.sort((a, b) -> Integer.compare(a.optString("type").equals("sstitle") ? 0: 1, b.optString("type").equals("sstitle") ? 0: 1));
		for (int i = 0; (i < boxes.size()) && (i < coverArtMax); i++)
			result.add(new SearchEngineImageInformation(SearchEngineImageType.COVER_ART, boxes.get(i).getString("url"), "Box (" + boxes.get(i).optString("region", "") + ")"));
		for (int i = 0; (i < shots.size()) && (i < screenshotsMax); i++)
			result.add(new SearchEngineImageInformation(SearchEngineImageType.SCREENSHOT, shots.get(i).getString("url"), shots.get(i).optString("type").equals("sstitle") ? "Title": "Screenshot"));
		return result;
	}

	/** regions in the order DBGL prefers them: the whole world, then the English and European versions */
	static final List<String> REGIONS = List.of("wor", "us", "eu", "uk", "de", "fr", "ss", "jp");

	static int regionRank(String region) {
		int i = REGIONS.indexOf(region);
		return i < 0 ? REGIONS.size(): i;
	}

	/** the text for the best region (see REGIONS) */
	static String byRegion(JSONArray texts) {
		if ((texts == null) || texts.isEmpty())
			return "";
		JSONObject best = null;
		for (int i = 0; i < texts.length(); i++) {
			JSONObject t = texts.getJSONObject(i);
			if ((best == null) || (regionRank(t.optString("region", "")) < regionRank(best.optString("region", ""))))
				best = t;
		}
		return best.optString("text", "");
	}

	/** the text in this language, else in English, else the first */
	static String byLanguage(JSONArray texts, String language) {
		if ((texts == null) || texts.isEmpty())
			return "";
		String english = null;
		for (int i = 0; i < texts.length(); i++) {
			JSONObject t = texts.getJSONObject(i);
			if (t.optString("langue", "").equalsIgnoreCase(language))
				return t.optString("text", "");
			if (t.optString("langue", "").equalsIgnoreCase("en"))
				english = t.optString("text", "");
		}
		return english != null ? english: texts.getJSONObject(0).optString("text", "");
	}
}
