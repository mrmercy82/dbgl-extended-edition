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
package org.dbgl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.dbgl.model.WebProfile;
import org.dbgl.util.searchengine.PouetSearchEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;


/** Pouët with saved pages (test/data/online): fails when the parsing is wrong, needs no internet */
public class PouetOfflineTest {

	private static final String DATA_DIR = System.getProperty("dbgl.testdata", "src/java/org/dbgl/test/data");

	private static Document page(String name) throws IOException {
		return Jsoup.parse(new File(DATA_DIR, "online/" + name), "UTF-8", "https://www.pouet.net/search.php");
	}

	@Test
	public void testSearchPage() throws IOException {
		Document doc = page("pouet-search.html");
		assertEquals(1, PouetSearchEngine.pageCount(doc));
		List<WebProfile> entries = PouetSearchEngine.parseResultPage(doc, 1);
		// one entry per platform: Purple Motion on 2 of them
		assertEquals(4, entries.size());

		WebProfile first = entries.get(0);
		assertEquals("Purple Motion", first.getTitle());
		assertEquals("https://www.pouet.net/prod.php?which=462", first.getUrl());
		assertEquals("Future Crew", first.getDeveloperName());
		assertEquals("", first.getPublisherName());
		assertEquals("1993", first.getYear());
		assertEquals("demo", first.getGenre());
		assertEquals(81, first.getRank());
		assertEquals("MS-Dos", first.getPlatform());
		assertEquals("MS-Dos/gus", entries.get(1).getPlatform());
		assertEquals("Purple Motion", entries.get(1).getTitle());

		WebProfile haze = entries.get(2);
		assertEquals("Purple Haze", haze.getTitle());
		assertEquals("Amiga OCS/ECS", haze.getPlatform());
		assertEquals("1991", haze.getYear());
		assertEquals(0, haze.getRank()); // no rating: n/a

		assertEquals(40, entries.get(3).getRank());
		assertEquals("4k", entries.get(3).getGenre());
	}

	@Test
	public void testProdPage() throws IOException {
		Document doc = page("pouet-prod.html");
		assertEquals(0, PouetSearchEngine.pageCount(doc));
		List<WebProfile> entries = PouetSearchEngine.parseResultPage(doc, 0);
		assertEquals(2, entries.size());
		WebProfile t = entries.get(0);
		assertEquals("Transgression 2", t.getTitle());
		assertEquals("mfx", t.getDeveloperName());
		assertEquals("64k", t.getGenre());
		assertEquals("1996", t.getYear());
		assertEquals(94, t.getRank());
		assertEquals("https://www.pouet.net/prod.php?which=462", t.getUrl());
		// each platform an entry of its own (before, both had the last platform)
		assertEquals("MS-Dos", t.getPlatform());
		assertEquals("MS-Dos/gus", entries.get(1).getPlatform());
		assertEquals("https://content.pouet.net/files/screenshots/00000/00000462.png", PouetSearchEngine.screenshotUrl(doc));
	}

	@Test
	public void testNothingFound() throws IOException {
		Document doc = page("pouet-none.html");
		assertEquals(0, PouetSearchEngine.pageCount(doc));
		assertEquals(0, PouetSearchEngine.parseResultPage(doc, 0).size());
		assertNull(PouetSearchEngine.screenshotUrl(doc));
	}
}
