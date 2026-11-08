/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-01. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.SearchEngineImageInformation;
import org.dbgl.model.SearchEngineImageInformation.SearchEngineImageType;
import org.dbgl.model.WebProfile;
import org.dbgl.service.ImageService;
import org.eclipse.swt.graphics.ImageData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class PouetSearchEngine extends WebSearchEngine {

	private static final String HOST_NAME = "www.pouet.net";

	private PouetSearchEngine() {
	}

	private static class SearchEngineHolder {
		private static WebSearchEngine instance_ = new PouetSearchEngine();
	}

	public static WebSearchEngine getInstance() {
		return SearchEngineHolder.instance_;
	}

	@Override
	public String getIcon() {
		return ImageService.IMG_POUET;
	}

	@Override
	public String getName() {
		return "Pou\u00EBt.net";
	}

	@Override
	public String getSimpleName() {
		return "pouet";
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public List<WebProfile> getEntries(String title, String[] platforms) throws IOException {
		int pageIdx = 0;
		int pages = 1;
		List<WebProfile> allEntries = new ArrayList<>();

		while (pageIdx < pages) {
			String url = HTTPS_PROTOCOL + HOST_NAME + "/search.php?type=prod&what=" 
					+ URLEncoder.encode(prepareSearchTerm(title), StandardCharsets.UTF_8) + "&page=" + (pageIdx + 1);
			Document doc = Jsoup.connect(url).get();
			
			if (pageIdx == 0) {
				pages = pageCount(doc);
			}
			allEntries.addAll(parseResultPage(doc, pages));
			pageIdx++;
		}

		return filterEntries(platforms, allEntries);
	}

	/** how many pages of results the search has; 0: none, or Pouët went straight to the one prod found */
	public static int pageCount(Document doc) {
		try {
			return Integer.valueOf(doc.select("select[name=page] > option:last-child").text());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** the prods on one page of results, or the one prod Pouët showed instead (tested with saved pages, without internet) */
	public static List<WebProfile> parseResultPage(Document doc, int pages) {
		if (pages > 0)
			return extractEntries(doc);
		if (doc.selectFirst("div#prodpagecontainer") != null && doc.selectFirst("table#pouetbox_prodmain") != null)
			return extractSingleEntry(doc);
		return new ArrayList<>();
	}

	/** the address of the screenshot on the page of a prod, or null */
	public static String screenshotUrl(Document doc) {
		Element img = doc.selectFirst("table#pouetbox_prodmain td#screenshot > img");
		return img == null ? null: img.attr("abs:src").isEmpty() ? img.attr("src"): img.attr("abs:src");
	}

	private static List<WebProfile> extractEntries(Document doc) {
		List<WebProfile> allEntries = new ArrayList<>();
		Elements entries = doc.select("table#pouetbox_searchprod tr:gt(0):not(:last-child)");
		for (Element entry: entries) {
			List<String> platforms = entry.select("td:eq(0) > span.platformiconlist").eachText();
			platforms.forEach(x -> {
				WebProfile gameEntry = new WebProfile();
				gameEntry.setTitle(entry.select("td:eq(0) > span.prod > a").text());
				gameEntry.setUrl(entry.select("td:eq(0) > span.prod > a").attr("abs:href"));
				gameEntry.setPublisherName("");
				gameEntry.setDeveloperName(entry.select("td:eq(1) > a").text());
				gameEntry.setYear(StringUtils.right(entry.select("td:eq(3)").text(), 4));
				gameEntry.setGenre(StringUtils.join(entry.select("td:eq(0) > span.typeiconlist").eachText(), ", "));
				try {
					gameEntry.setRank((int)((Double.parseDouble(entry.select("td:eq(4)").text()) + 1.0) * 50.0));
				} catch (NumberFormatException e) {}
				gameEntry.setNotes("");
				gameEntry.setPlatform(x);
				allEntries.add(gameEntry);
			});
		}
		return allEntries;
	}

	private static List<WebProfile> extractSingleEntry(Document doc) {
		Element prod = doc.selectFirst("table#pouetbox_prodmain");
		List<WebProfile> allEntries = new ArrayList<>();
		Element canonical = doc.selectFirst("head > link[rel=canonical]");
		List<String> platforms = prod.select("table#stattable tr:eq(0) td:last-child ul li").eachText();
		// one entry per platform (before, all of them were the same object and had the last platform)
		platforms.forEach(x -> {
			WebProfile gameEntry = new WebProfile();
			gameEntry.setTitle(prod.select("tr#prodheader span#prod-title").text());
			gameEntry.setUrl(canonical != null ? canonical.attr("href"): "");
			gameEntry.setPublisherName("");
			gameEntry.setDeveloperName(prod.select("tr#prodheader span#title > a:eq(1)").text());
			gameEntry.setYear(StringUtils.right(prod.select("table#stattable tr:eq(2) td:last-child").text(), 4));
			gameEntry.setGenre(StringUtils.join(prod.select("table#stattable tr:eq(1) td:last-child a span").eachText(), ", "));
			try {
				gameEntry.setRank((int)((Double.parseDouble(prod.select("ul#avgstats li:first-child").text()) + 1.0) * 50.0));
			} catch (NumberFormatException e) {}
			gameEntry.setNotes("");
			gameEntry.setPlatform(x);
			allEntries.add(gameEntry);
		});
		return allEntries;
	}

	@Override
	public WebProfile getEntryDetailedInformation(WebProfile entry) throws IOException {
		return entry;
	}

	@Override
	public SearchEngineImageInformation[] getEntryImages(WebProfile entry, int coverArtMax, int screenshotsMax, boolean forceAllRegionsCoverArt) throws IOException {
		Document doc = Jsoup.connect(entry.getUrl()).get();
		String imgUrl = screenshotUrl(doc);
		if (imgUrl == null) {
			entry.setWebImages(new SearchEngineImageInformation[0]);
		} else {
			String imgDescription = getSimpleName();
			if (imgUrl.toLowerCase().endsWith(".gif")) {
				try (InputStream is = getHttpURLConnection(imgUrl).getInputStream()) {
					ImageData[] images = ImageService.getAnimatedImageData(is);
					int nrOfImages = Math.min(images.length, Math.max(screenshotsMax, 0));
					SearchEngineImageInformation[] result = new SearchEngineImageInformation[nrOfImages];
					for (int i = 0; i < nrOfImages; i++) {
						result[i] = new SearchEngineImageInformation(SearchEngineImageType.SCREENSHOT, imgUrl, imgDescription);
						result[i].setData(images[i]);
					}
					entry.setWebImages(result);
				}
			} else {
				entry.setWebImages(new SearchEngineImageInformation[] {new SearchEngineImageInformation(SearchEngineImageType.SCREENSHOT, imgUrl, imgDescription)});
			}
		}
		return entry.getWebImages();
	}
}