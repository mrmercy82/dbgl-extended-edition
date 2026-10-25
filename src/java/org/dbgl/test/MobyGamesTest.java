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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.dbgl.model.SearchEngineImageInformation;
import org.dbgl.model.WebProfile;
import org.dbgl.util.searchengine.MobyGamesSearchEngine;
import org.dbgl.util.searchengine.MobyGamesWebProfile;
import org.eclipse.swt.graphics.ImageData;
import org.junit.Test;


public class MobyGamesTest {

	@Test
	public void testGetEntries() {
		try {
			List<WebProfile> entries1 = MobyGamesSearchEngine.getInstance().getEntries("doom", new String[] {"dos", "pc booter"});
			assertTrue(entries1.size() > 30);

			List<WebProfile> entries2 = MobyGamesSearchEngine.getInstance().getEntries("mario", new String[] {"dos", "pc booter"});
			assertTrue(entries2.size() > 8);

			List<WebProfile> entries3 = MobyGamesSearchEngine.getInstance().getEntries("noresultsplease", new String[] {"dos", "pc booter"});
			assertEquals(0, entries3.size());
			
			List<WebProfile> entries4 = MobyGamesSearchEngine.getInstance().getEntries("Big Blue Disk #1", new String[] {"dos", "pc booter"});
			assertEquals(9, entries4.size());
			
		} catch (IOException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testGetEntryDetailsDoom() {
		try {
			WebProfile doom = new WebProfile();
			doom.setTitle("DOOM");
			doom.setGameId(1068);
			doom.setPlatformId(2);
			doom.setPlatform("DOS");
			doom.setYear("1993");
			doom.setUrl("https://www.mobygames.com/game/1068/doom/");
			doom = MobyGamesSearchEngine.getInstance().getEntryDetailedInformation(doom);
			assertEquals("id Software, Inc.", doom.getDeveloperName());
			assertEquals("id Software, Inc., Atari Corporation, SoftKey Multimedia Inc., Micro Star Software, SEGA of America, Inc., SEGA Enterprises Ltd., Tec Toy Indústria de Brinquedos S.A., Imagineer Co., Ltd., Art Data Interactive, Inc., Williams Entertainment, Inc., Ocean Software Ltd., Basho House, Activision Publishing, Inc., Simon & Schuster Interactive", doom.getPublisherName());
			assertEquals("Action", doom.getGenre());
			assertEquals("1993", doom.getYear());
			assertEquals(77, doom.getRank());
			assertEquals("https://cdn.mobygames.com/covers/3947919-doom-dos-front-cover.jpg", doom.getCoreGameCoverUrl());
			assertEquals(
				"The Union Aerospace Corporation has been experimenting with teleportation technology on Mars' moons Phobos and Deimos. After early successes, something goes wrong. It seems the scientists have opened a gateway straight to Hell. Phobos base is overrun with demonic creatures, and the whole of Deimos simply vanishes. A squad of marines is sent to Phobos, but all except one are quickly slaughtered. It falls to the surviving marine to grab some guns and strike back at the demons.\n\n"
						+ "id Software's follow-up to their genre-defining Wolfenstein 3D, Doom is another first-person 3D shooter: full-on action as seen from the space marine's perspective. Like Wolfenstein, the game consists of distinct episodes, playable in any order. The first episode, Knee-Deep in the Dead, takes place in the Phobos base and is freely available as shareware. The full game continues on Deimos in The Shores of Hell and culminates in Inferno, the final episode which takes place in Hell itself (the Sega 32x version lacks this episode).\n\n"
						+ "The basic objective in each level is simply to reach the exit. Since dozens of enemies stand in the way, the only way to get there is by killing them. Switches and buttons must be pressed to advance at certain points and often color-coded locked doors will block the way - matching keycards or skull keys must be found to pass.\n\n"
						+ "The game's engine technology is more advanced than Wolfenstein's, and thus the levels are more varied and complex. The engine simulates different heights (stairs and lifts appear frequently) and different lighting conditions (some rooms are pitch black, others only barely illuminated). There are outdoor areas, pools of radioactive waste that hurt the player, ceilings that come down and crush him, and unlike Wolfenstein's orthogonally aligned corridors, the walls in Doom can be in any angle to each other. An automap helps in navigating the levels.\n\n"
						+ "Stylistically, the levels begin with a futuristic theme in the military base on Phobos and gradually change to a hellish environment, complete with satanic symbols (pentagrams, upside-down-crosses, and portraits of horned demons), hung-up mutilated corpses, and the distorted faces of the damned.\n\n"
						+ "Doom features a large weapon arsenal, with most weapons having both advantages and drawbacks. The starting weapons are the fists and a simple pistol. Also available are a shotgun (high damage, slow reload, not good at distances), a chaingun (high firing rate, but slightly inaccurate in longer bursts), and a plasma rifle (combining a high firing rate and large damage). The rocket launcher also deals out lots of damage, but the explosion causes blast damage and must be used with care in confined areas or it might prove deadly to the player as well as the enemies. Two further weapons in the game are the chainsaw for close-quarter carnage, and the BFG9000 energy gun, which while taking some practice to fire correctly, can destroy most enemies in a single burst. The different weapons use four different ammunition types (bullets, shells, rockets, and energy cells), so collecting the right type for a certain gun is important.\n\n"
						+ "The game drops some of Wolfenstein's arcade-inspired aspects, so there are no extra lives or treasures to be collected for points, but many other power-ups are still available. Medpacks heal damage while armor protects from receiving it in the first place. Backpacks allow more ammunition to be carried, a computer map reveals the whole layout of the level on the automap (including any secret areas), light amplification visors illuminate dark areas and radiation suits allow travel over waste without taking damage. Also available are berserk packs (which radically increase the damage inflicted by the fists) as well as short-time invisibility and invulnerability power-ups.\n\n"
						+ "The enemies to be destroyed include former humans corrupted during the invasion, plus demons in all shapes and sizes: fireball-throwing imps, floating skulls, pink-skinned demons with powerful bite attacks, and large one-eyed flying monstrosities called Cacodemons. Each episode ends with a boss battle against one or two, particularly powerful creatures.\n\n"
						+ "Doom popularized multiplayer in the genre with two different modes: Cooperative allows players to move through the single-player game together, while Deathmatch is a competitive game type where players blast at each other to collect 'frag' points for a kill and re-spawn in a random location after being killed.\n\n"
						+ "The 3DO and Sega32x ports lack any multiplayer modes, though the other ports retain the DOS versions multiplayer to varying degrees. The various console ports all feature simplified levels and omit some levels, enemies, and features from the original DOS release. The SNES and Gameboy Advance versions of the game actually use different engines and hence feature numerous small gameplay differences.",
				doom.getNotes());

			SearchEngineImageInformation[] images = MobyGamesSearchEngine.getInstance().getEntryImages(doom, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
			assertTrue(images.length > 80);

			assertEquals("Front Cover", images[0].getDescription());
			assertEquals("Back Cover", images[1].getDescription());
			assertEquals("Front Cover", images[2].getDescription());
			assertEquals("Front Cover Doom version 1.9", images[3].getDescription());

			images = MobyGamesSearchEngine.getInstance().getEntryImages(doom, 2, 2, true);
			assertEquals(4, images.length);

		} catch (IOException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testGetEntryDetailsKingdomOfKroz() {
		try {
			WebProfile kingdomofkroz = new WebProfile();
			kingdomofkroz.setTitle("Kingdom of Kroz");
			kingdomofkroz.setGameId(728);
			kingdomofkroz.setPlatformId(2);
			kingdomofkroz.setPlatform("DOS");
			kingdomofkroz.setYear("1988");
			kingdomofkroz.setUrl("https://www.mobygames.com/game/728/kingdom-of-kroz/");
			kingdomofkroz = MobyGamesSearchEngine.getInstance().getEntryDetailedInformation(kingdomofkroz);
			assertEquals("Scott Miller", kingdomofkroz.getDeveloperName());
			assertEquals("Softdisk Publishing", kingdomofkroz.getPublisherName());
			assertEquals("Action", kingdomofkroz.getGenre());
			assertEquals("1988", kingdomofkroz.getYear());
			assertEquals(0, kingdomofkroz.getRank());
			assertEquals("https://cdn.mobygames.com/screenshots/1602608-kingdom-of-kroz-dos-level-1.png", kingdomofkroz.getCoreGameCoverUrl());
			assertEquals(
				"A text-mode action/puzzle game. You have to get to the exit on each level, which is made more difficult by obstacles such as trees (which can be destroyed) and monsters. Various puzzles (in the form of one-off objects) are also used.",
				kingdomofkroz.getNotes());

			SearchEngineImageInformation[] images = MobyGamesSearchEngine.getInstance().getEntryImages(kingdomofkroz, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
			assertEquals(3, images.length);

			assertEquals("Level 1", images[0].getDescription());
			assertEquals("Instructions", images[1].getDescription());
			assertEquals("High score table", images[2].getDescription());

		} catch (IOException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testGetEntryDetailsXargon() {
		try {
			WebProfile xargon = new WebProfile();
			xargon.setTitle("Xargon: The Mystery of the Blue Builders - Beyond Reality");
			xargon.setGameId(59387);
			xargon.setPlatformId(2);
			xargon.setPlatform("DOS");
			xargon.setYear("1994");
			xargon.setUrl("https://www.mobygames.com/game/59387/xargon-the-mystery-of-the-blue-builders-beyond-reality/");
			xargon = MobyGamesSearchEngine.getInstance().getEntryDetailedInformation(xargon);
			assertEquals("Epic MegaGames, Inc.", xargon.getDeveloperName());
			assertEquals("Wiz Technology, Inc., Monkey Business, Inc.", xargon.getPublisherName());
			assertEquals("Action", xargon.getGenre());
			assertEquals("1994", xargon.getYear());
			assertEquals(0, xargon.getRank());
			assertEquals("https://cdn.mobygames.com/covers/6737093-xargon-the-mystery-of-the-blue-builders-beyond-reality-dos-front.jpg", xargon.getCoreGameCoverUrl());
			assertEquals(
				"Beyond Reality is the first episode of three that make up the side-scrolling platformer Xargon. This first episode was freely distributed as shareware with the option to register the other two, but also sold separately. The company B&N also released each episode separately and commercially under their Monkey Business label. The B&N version can be run directly from the floppy disk by just typing GO.",
				xargon.getNotes());

			SearchEngineImageInformation[] images = MobyGamesSearchEngine.getInstance().getEntryImages(xargon, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
			assertEquals(7, images.length);

			images = MobyGamesSearchEngine.getInstance().getEntryImages(xargon, 1, 1, true);
			assertEquals(1, images.length);

		} catch (IOException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testGetEntryDetailsBongBong() {
		try {
			WebProfile bongbong = new WebProfile();
			bongbong.setTitle("Bong Bong");
			bongbong.setGameId(60970);
			bongbong.setPlatformId(2);
			bongbong.setPlatform("DOS");
			bongbong.setYear("1989");
			bongbong.setUrl("https://www.mobygames.com/game/60970/bong-bong/");
			bongbong = MobyGamesSearchEngine.getInstance().getEntryDetailedInformation(bongbong);
			assertEquals("Lee T.K.", bongbong.getDeveloperName());
			assertEquals("", bongbong.getPublisherName());
			assertEquals("Action", bongbong.getGenre());
			assertEquals("1989", bongbong.getYear());
			assertEquals(0, bongbong.getRank());
			assertEquals("https://cdn.mobygames.com/screenshots/16352125-bong-bong-dos-arcade-style-loading-sequence-showing-off-some-spr.png", bongbong.getCoreGameCoverUrl());
			assertEquals(
				"Bong Bong is a simple arcade platformer, featuring a little bunny rabbit with an unquenchable passion for sugary foodstuffs. Climb ladders, jump over traps and hop across platforms to grab every food item in sight: get them all, and the next level will present an entirely new type of delicacy to quell your appetite. Beware the scorpions, and watch out for mystery jars - most of them will get you a score bonus, but some may contain a snake.",
				bongbong.getNotes());

			SearchEngineImageInformation[] images = MobyGamesSearchEngine.getInstance().getEntryImages(bongbong, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
			assertEquals(7, images.length);

			images = MobyGamesSearchEngine.getInstance().getEntryImages(bongbong, 1, 1, true);
			assertEquals(1, images.length);

		} catch (IOException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testGetEntryDetailsFinalFury() {
		try {
			WebProfile finalfury = new WebProfile();
			finalfury.setTitle("The Complete Great Naval Battles: The Final Fury");
			finalfury.setGameId(55427);
			finalfury.setPlatformId(2);
			finalfury.setPlatform("DOS");
			finalfury.setYear("1996");
			finalfury.setUrl("https://www.mobygames.com/game/55427/the-complete-great-naval-battles-the-final-fury/");
			finalfury = MobyGamesSearchEngine.getInstance().getEntryDetailedInformation(finalfury);
			assertEquals("IO Design Group Inc., Divide By Zero Software Inc.", finalfury.getDeveloperName());
			assertEquals("Mindscape, Inc., Strategic Simulations, Inc., SNEG Ltd.", finalfury.getPublisherName());
			assertEquals("Compilation, Simulation, Strategy / tactics", finalfury.getGenre());
			assertEquals("1996", finalfury.getYear());
			assertEquals(0, finalfury.getRank());
			assertEquals("https://cdn.mobygames.com/covers/467461-the-complete-great-naval-battles-the-final-fury-dos-front-cover.jpg", finalfury.getCoreGameCoverUrl());
			assertEquals("The Final Fury is a compilation of all previous titles from the Great Naval Battles series:\n"
					+ "\n"
					+ "Great Naval Battles: North Atlantic 1939-43 with its three add-ons:\n"
					+ "\n"
					+ "Great Naval Battles: North Atlantic 1939-1943 - Super Ships of the Atlantic\n\n"
					+ "Great Naval Battles: North Atlantic 1939-1943 - America in the Atlantic\n\n"
					+ "Great Naval Battles: North Atlantic 1939-1943 - Scenario Builder\n"
					+ "\n"
					+ "Great Naval Battles Vol. II: Guadalcanal 1942-43\n\n"
					+ "Great Naval Battles Vol. III: Fury in the Pacific, 1941-1944\n\n"
					+ "Great Naval Battles Vol. IV: Burning Steel, 1939-1942\n"
					+ "\n"
					+ "It also exclusively includes the ultimate part of the series, Great Naval Battles Vol. V: Demise of the Dreadnoughts, 1914-18, which is set during World War I. Its scenarios include famous battles between the German Hochseeflotte and the British Royal Navy (battle of Jutland, battle of Coronel, battles of Dogger Bank, battle of the Falkland Islands). A mission editor is also available.",
				finalfury.getNotes());

			SearchEngineImageInformation[] images = MobyGamesSearchEngine.getInstance().getEntryImages(finalfury, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
			assertEquals(14, images.length);

			images = MobyGamesSearchEngine.getInstance().getEntryImages(finalfury, 1, 1, true);
			assertEquals(1, images.length);

		} catch (IOException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}
	
	@Test
	public void testGetEntryDetailsCivilWar() {
		try {
			WebProfile finalfury = new WebProfile();
			finalfury.setTitle("The Civil War: Master Players Edition");
			finalfury.setGameId(73648);
			finalfury.setPlatformId(2);
			finalfury.setPlatform("DOS");
			finalfury.setYear("1996");
			finalfury.setUrl("https://www.mobygames.com/game/73648/the-civil-war-master-players-edition/");
			finalfury = MobyGamesSearchEngine.getInstance().getEntryDetailedInformation(finalfury);
			assertEquals("Dagger Interactive Technologies, Ltd.", finalfury.getDeveloperName());
			assertEquals("Empire Interactive, Inc.", finalfury.getPublisherName());
			assertEquals("Strategy / tactics", finalfury.getGenre());
			assertEquals("1996", finalfury.getYear());
			assertEquals(0, finalfury.getRank());
			assertEquals(null, finalfury.getCoreGameCoverUrl());
			assertEquals("The Civil War: Master Players Edition is an enhanced version of The Civil War from the same developer & publisher (which was released a year before).\n\n"
					+ "First select the side to play, then choose one of the eight historical battles or the entire campaign (1861-1864). "
					+ "This sequel has enhanced AI, better graphics and efforts have been made to improve historical accuracy. "
					+ "Also new with this release is a printed strategy guide.",
				finalfury.getNotes());

			SearchEngineImageInformation[] images = MobyGamesSearchEngine.getInstance().getEntryImages(finalfury, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
			assertEquals(0, images.length);

			images = MobyGamesSearchEngine.getInstance().getEntryImages(finalfury, 1, 1, true);
			assertEquals(0, images.length);

		} catch (IOException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}
	
	@Test
	public void testGetEntryDetailsTransylvania() {
		try {
			WebProfile transylvania = new MobyGamesWebProfile();
			transylvania.setTitle("Transylvania");
			transylvania.setGameId(133318);
			transylvania.setPlatformId(2);
			transylvania.setPlatform("DOS");
			transylvania.setYear("1986");
			transylvania.setUrl("https://www.mobygames.com/game/133318/transylvania/");
			transylvania = MobyGamesSearchEngine.getInstance().getEntryDetailedInformation(transylvania);
			assertEquals("Polarware, RetroVenture LLC", transylvania.getDeveloperName());
			assertEquals("Polarware, RetroVenture LLC", transylvania.getPublisherName());
			assertEquals("Adventure", transylvania.getGenre());
			assertEquals("1986", transylvania.getYear());
			assertEquals(66, transylvania.getRank());
			assertEquals("https://cdn.mobygames.com/covers/9124810-transylvania-commodore-64-front-cover.jpg", transylvania.getCoreGameCoverUrl());
			assertEquals(
				"This release is a new edition of Transylvania, featuring redrawn high-resolution graphics, new locations and puzzles, and new packaging with the Polarware label.",
				transylvania.getNotes());

			SearchEngineImageInformation[] images = MobyGamesSearchEngine.getInstance().getEntryImages(transylvania, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
			assertEquals(28, images.length);
			
			for (int i = 19; i < images.length; i++) {
				try {
					ImageData imgData = transylvania.getWebImage(i);
					assertNotNull(imgData);
				} catch (IOException e) {
					System.out.println("Image " + i + ": " + images[i].getUrl());
					e.printStackTrace();
				}
			}

			images = MobyGamesSearchEngine.getInstance().getEntryImages(transylvania, 1, 1, true);
			assertEquals(2, images.length);

		} catch (IOException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}
	
	@Test
	public void testGetEntryDetailsBigBlueDisk1() {
		try {
			WebProfile bigbluedisk = new WebProfile();
			bigbluedisk.setTitle("Big Blue Disk #1");
			bigbluedisk.setGameId(97125);
			bigbluedisk.setPlatformId(2);
			bigbluedisk.setPlatform("DOS");
			bigbluedisk.setYear("1986");
			bigbluedisk.setUrl("https://www.mobygames.com/game/97125/big-blue-disk-1/");
			bigbluedisk = MobyGamesSearchEngine.getInstance().getEntryDetailedInformation(bigbluedisk);
			assertEquals("Softdisk Publishing", bigbluedisk.getDeveloperName());
			assertEquals("Softdisk Publishing", bigbluedisk.getPublisherName());
			assertEquals("Compilation", bigbluedisk.getGenre());
			assertEquals("1986", bigbluedisk.getYear());
			assertEquals(0, bigbluedisk.getRank());
			assertEquals("https://cdn.mobygames.com/covers/11042949-big-blue-disk-1-dos-front-cover.jpg", bigbluedisk.getCoreGameCoverUrl());
			assertEquals(
				"This issue of the monthly software subscription contains two games: Hop-a-long Hangman and Planet of the Robots. These programs, along with a variety of non-game applications, utilities and text articles (including a review of VersaForm XL (version 4.0)), can be accessed from a text-based menu system.",
				bigbluedisk.getNotes());

			SearchEngineImageInformation[] images = MobyGamesSearchEngine.getInstance().getEntryImages(bigbluedisk, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
			assertEquals(7, images.length);

		} catch (IOException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}
}