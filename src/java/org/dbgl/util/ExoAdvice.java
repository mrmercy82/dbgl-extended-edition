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
package org.dbgl.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.service.SettingsService;
import org.dbgl.util.DBConfAdvice.Difference;
import org.dbgl.util.searchengine.WebSearchEngine;


/**
 * Alpha 53: the DOSBox configurations of an eXo collection (eXoDOS) on this computer as a comparison for "Check profiles",
 * like DBConf: for a profile with exactly the title of a game of the collection, the settings about the game (memory,
 * machine, sound card, ...) that eXo sets otherwise. Only read from the folder the user chose (or imported from); nothing of
 * it is copied into DBGL or sent anywhere. The configurations of eXo are made for DOSBox 0.74 and its descendants: for other
 * DOSBox versions the speed (core, cycles) is left out, and DOSBox Staging gets the values under its names.
 */
public final class ExoAdvice {

	public static final String SECTION = "exodos", DIR = "dir";
	private static final int MAX_CONF = 256 * 1024;

	/** a game of the collection: its title and its configuration */
	public record Game(String title, File conf) {
	}

	private static File cachedRoot_;
	private static Map<String, Game> cachedIndex_;

	private ExoAdvice() {
	}

	/** the collection to compare with, or null if none was chosen or it is not there (any more) */
	public static File root() {
		try {
			String dir = SettingsService.getInstance().getValue(SECTION, DIR);
			return StringUtils.isBlank(dir) ? null: ExoImport.rootOf(new File(dir));
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** remembers the collection (from the import of eXo, or chosen in "Check profiles"); null forgets it */
	public static synchronized void setRoot(File root) {
		SettingsService.getInstance().setValue(SECTION, DIR, root == null ? "": root.getPath());
		cachedRoot_ = null;
		cachedIndex_ = null;
	}

	/** the games of the collection by their comparable title (installed or not: only their configuration is needed) */
	static synchronized Map<String, Game> index(File root) {
		if ((cachedIndex_ != null) && root.equals(cachedRoot_))
			return cachedIndex_;
		Map<String, ExoImport.Entry> meta = ExoImport.readPlatforms(root);
		Map<String, Game> result = new HashMap<>();
		for (File project: ExoImport.projects(new File(root, "eXo")))
			for (File bang: ExoImport.bangs(project)) {
				File[] games = bang.listFiles(File::isDirectory);
				if (games == null)
					continue;
				for (File g: games) {
					File conf = new File(g, "dosbox.conf");
					if (!conf.isFile())
						continue;
					ExoImport.Entry e = meta.get((project.getName() + "/" + bang.getName() + "/" + g.getName()).toLowerCase(java.util.Locale.ROOT));
					String title = e != null ? e.title(): ExoImport.titleOfStart(g);
					result.putIfAbsent(WebSearchEngine.comparable(title), new Game(title, conf));
				}
			}
		cachedRoot_ = root;
		cachedIndex_ = result;
		return result;
	}

	/** the game of the collection with exactly this title, or null */
	public static Game gameFor(String title) {
		File root = root();
		if ((root == null) || StringUtils.isBlank(title))
			return null;
		return index(root).get(WebSearchEngine.comparable(title));
	}

	/** the settings about the game in a configuration of eXo, as DBConf has them ("section/item" to value) */
	static Map<String, String> aboutTheGame(String text) {
		Map<String, String> result = new LinkedHashMap<>();
		for (String[] s: DBConfAdvice.settings(text))
			if (DBConfAdvice.ABOUT_THE_GAME.contains(s[0] + "/" + s[1]))
				result.put(s[0] + "/" + s[1], s[2]);
		return result;
	}

	/** the settings of eXo the profile has otherwise, for its DOSBox version (see above) */
	public static List<Difference> differences(Game game, DosboxVersion db, Configuration applies) {
		String text;
		try {
			byte[] b = Files.readAllBytes(game.conf().toPath());
			text = new String(b, 0, Math.min(b.length, MAX_CONF), StandardCharsets.ISO_8859_1);
		} catch (IOException e) {
			return List.of();
		}
		return differences(aboutTheGame(text), db, applies);
	}

	static List<Difference> differences(Map<String, String> exo, DosboxVersion db, Configuration applies) {
		boolean classic = DBConfAdvice.forVersion(db);
		GenerationAwareConfigurable.Generation gen = db == null ? null: GenerationAwareConfigurable.determineGen(db);
		boolean staging = (gen != null) && (GenerationAwareConfigurable.familyIdx(gen) == GenerationAwareConfigurable.familyIdx(GenerationAwareConfigurable.Generation.staging_0830));
		List<Difference> result = new ArrayList<>();
		for (Map.Entry<String, String> e: exo.entrySet()) {
			String section = e.getKey().substring(0, e.getKey().indexOf('/')), item = e.getKey().substring(e.getKey().indexOf('/') + 1);
			if (!classic && (section.equals("cpu") && (item.equals("core") || item.equals("cycles"))))
				continue; // the speed of 0.74 means something else elsewhere
			String value = e.getValue();
			if (staging) {
				String other = ConfTakeover.stagingValueFor(section, item, value);
				if (other != null)
					value = other;
			}
			if (!applies.hasValue(section, item))
				continue;
			String current = StringUtils.trimToEmpty(applies.getValue(section, item));
			if (!current.equalsIgnoreCase(value))
				result.add(new Difference(section, item, value, current));
		}
		return result;
	}
}
