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
package org.dbgl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.List;
import org.dbgl.model.SearchEngineImageInformation;
import org.dbgl.model.WebProfile;
import org.dbgl.util.searchengine.TheGamesDBSearchEngine;
import org.junit.Test;


public class TheGamesDBTest {

	@Test
	public void testGetEntries() {
		try {
			List<WebProfile> entries1 = TheGamesDBSearchEngine.getInstance().getEntries("doom", new String[] {"pc"});
			assertEquals(59, entries1.size());

			List<WebProfile> entries2 = TheGamesDBSearchEngine.getInstance().getEntries("commander keen", new String[] {});
			assertEquals(15, entries2.size());

			List<WebProfile> entries3 = TheGamesDBSearchEngine.getInstance().getEntries("noresultsplease", new String[] {"pc"});
			assertEquals(0, entries3.size());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testGetEntryDetailsDoom() {
		try {
			WebProfile doom = new WebProfile();
			doom.setGameId(745);
			doom = TheGamesDBSearchEngine.getInstance().getEntryDetailedInformation(doom);
			assertEquals("Doom", doom.getTitle());
			assertEquals("PC (MS-DOS, Windows 95/98/ME/NT/XP, Linux, Solaris)", doom.getPlatform());
			assertEquals("1993", doom.getYear());
			assertEquals("https://thegamesdb.net/game.php?id=745", doom.getUrl());
			assertEquals("id Software", doom.getDeveloperName());
			assertEquals("id Software", doom.getPublisherName());
			assertEquals("Action, Shooter", doom.getGenre());
			assertEquals("A nameless space marine, gets punitively posted to Mars after assaulting a commanding officer, who ordered his unit to fire upon "
					+ "civilians. The Martian marine base acts as security for the Union Aerospace Corporation UAC, a multi-planetary conglomerate, which is performing "
					+ "secret experiments with teleportation by creating gateways between the two moons of Mars, Phobos and Deimos. Suddenly, one of these UAC "
					+ "experiments goes horribly wrong; computer systems on Phobos malfunction, Deimos disappears entirely and \"something fragging evil\" starts "
					+ "pouring out of the gateways, killing or possessing all UAC personnel! \r\n\r\nResponding to a frantic distress call from the overrun scientists, "
					+ "the Martian marine unit is quickly sent to Phobos to investigate, where you, the space marine, are left to guard the hangar with only a pistol "
					+ "while the rest of the group proceeds inside to discover their worst nightmare. As you advance further, terrifying screams echo through the vast "
					+ "halls, followed by a disturbing silence ... it seems, all your buddies are dead and you're all on your own now - fight back, exterminate every "
					+ "evil creature and get your ticket back home to earth!",
				doom.getNotes());

			SearchEngineImageInformation[] images = TheGamesDBSearchEngine.getInstance().getEntryImages(doom, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
			assertEquals(9, images.length);

			images = TheGamesDBSearchEngine.getInstance().getEntryImages(doom, Integer.MAX_VALUE, Integer.MAX_VALUE, false);
			assertEquals(9, images.length);

			assertEquals("boxart back 1594", images[0].getDescription());
			assertEquals("fanart 8880", images[1].getDescription());

			images = TheGamesDBSearchEngine.getInstance().getEntryImages(doom, 2, 2, true);
			assertEquals(3, images.length);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testGetEntryDetailsZelda() {
		try {
			WebProfile zelda = new WebProfile();
			zelda.setGameId(113);
			zelda = TheGamesDBSearchEngine.getInstance().getEntryDetailedInformation(zelda);
			assertEquals("The Legend of Zelda", zelda.getTitle());
			assertEquals("Nintendo Entertainment System (NES)", zelda.getPlatform());
			assertEquals("1987", zelda.getYear());
			assertEquals("https://thegamesdb.net/game.php?id=113", zelda.getUrl());
			assertEquals("Nintendo R&D4", zelda.getDeveloperName());
			assertEquals("Nintendo of America, Inc.", zelda.getPublisherName());
			assertEquals("Action, Adventure", zelda.getGenre());
			assertTrue(zelda.getNotes().startsWith("The Legend of Zelda is a 1987 NES video game developed by Nintendo R&D4 and released by Nintendo of America in the USA on August 22, 1987.\r\n"
					+ "\r\n"
					+ "Welcome to the Legend of Zelda.  Where the only sound you’ll hear is your own heart pounding as you race through forests, lakes, mountains and dungeonous mazes in an attempt to restore peace to the land of Hyrule.  Along the way you’ll be challenged by Tektites, Wizzrobes and an endless array of ruthless creatures who’ll stop at nothing to prevent you from finding the lost fragments of the Triforce of Wisdom.  But don’t despair.  With a little luck and a lot of courage, you’ll conquer your adversaries, unite the Triforce fragments and unravel the mystery of the Legend of Zelda!\r\n"
					+ "\r\n"
					+ "• Explore the vast Overworld terrain of the land of Hyrule and discover hidden treasures.\r\n"
					+ "• Explore the mystical labyrinths of the Underworld and ward-off ruthless enemies."));

			SearchEngineImageInformation[] images = TheGamesDBSearchEngine.getInstance().getEntryImages(zelda, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
			assertEquals(31, images.length);

			assertEquals("fanart 199", images[0].getDescription());
			assertEquals("fanart 236", images[1].getDescription());
			assertEquals("fanart 1344", images[2].getDescription());
			assertEquals("fanart 4164", images[3].getDescription());
			assertEquals("banner 21021", images[4].getDescription());
			assertEquals("fanart 22358", images[5].getDescription());

			images = TheGamesDBSearchEngine.getInstance().getEntryImages(zelda, 1, 2, false);
			assertEquals(3, images.length);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
