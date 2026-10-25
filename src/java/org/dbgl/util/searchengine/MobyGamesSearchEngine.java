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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class MobyGamesSearchEngine extends WebSearchEngine {

	static final String MOBY_GAMES_HOST_NAME = "www.mobygames.com";
	static final String USER_AGENT = "Mozilla";

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
			
			Document doc = Jsoup.connect(url).userAgent(USER_AGENT).get();
			JSONObject json = new JSONObject(doc.selectFirst("game-browser").attr(":initial-values"));
			
			if (page == 0) {
				int perPage = json.getInt("perPage");
				pages = (json.getInt("total") + perPage - 1) / perPage;
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
		Document doc = Jsoup.connect(entry.getUrl()).userAgent(USER_AGENT).get();
		Element infoBlock = doc.selectFirst("div#infoBlock");
		entry.setDeveloperName(StringUtils.join(infoBlock.select("ul#developerLinks > li > a").eachText(), ", "));
		entry.setPublisherName(StringUtils.join(infoBlock.select("ul#publisherLinks > li > a").eachText(), ", "));
		entry.setGenre(StringUtils.join(infoBlock.select("div.info-genres > dl.metadata > dt:containsWholeOwnText(Genre) + dd > a").eachText(), ", "));
		Element descrElmt = doc.selectFirst("section#gameDescription > div#description-text");
		entry.setNotes(unescapeHtml(removeAllTags(descrElmt.html())));
		entry.setRank(extractRank(infoBlock));
		entry.setCoreGameCoverUrl(StringUtils.stripToNull(doc.select("meta[property=og:image]").attr("content")));
		if (StringUtils.isEmpty(entry.getDeveloperName()))
			entry.setDeveloperName(StringUtils.join(doc.select("section#gameCredits table.table-credits tr > td:last-child").eachText(), ", "));
		return entry;
	}

	@Override
	public SearchEngineImageInformation[] getEntryImages(WebProfile entry, int coverArtMax, int screenshotsMax, boolean forceAllRegionsCoverArt) throws IOException {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		if (coverArtMax > 0) {
			result.addAll(getEntryCoverArtInformation(entry, coverArtMax));
		}
		if (screenshotsMax > 0) {
			result.addAll(getEntryScreenshotInformation(entry, screenshotsMax));
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
		Document doc = Jsoup.connect(entry.getScreenshotsUrl()).userAgent(USER_AGENT).get();
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
			Document doc = Jsoup.connect(entry.getCoverArtUrl()).userAgent(USER_AGENT).get();
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
}