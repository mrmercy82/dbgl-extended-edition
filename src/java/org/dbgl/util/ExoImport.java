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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.commons.lang3.StringUtils;


/**
 * Alpha 48: the games of an eXo collection (eXoDOS, eXoWin3x), as it is laid out on disk:
 * <ul>
 * <li>{@code eXo\<project>\!<short>\<game>\dosbox.conf}: the DOSBox configuration of each game, next to its start file (.bat)</li>
 * <li>{@code eXo\<project>\<game>\}: the game itself once it is installed (unpacked from its zip by eXo's installer)</li>
 * <li>{@code Data\Platforms\<platform>.xml}: LaunchBox's data on the games (title, developer, ...), the start file as
 * ApplicationPath</li>
 * <li>{@code Images\<platform>\Box - Front\<title>-01.jpg}: the covers</li>
 * </ul>
 * The paths in the configurations are relative to the folder of the collection (eXo starts DOSBox there). Only installed games
 * are offered; the others are counted. All files are only read; they are untrusted (the XML without DTD and external entities).
 */
public final class ExoImport {

	/** what was found: the installed games, how many are not installed yet, and the folder of the collection */
	public record Result(List<StoreGameImport.Found> games, int notInstalled, File root) {
	}

	private ExoImport() {
	}

	/** the folder of the collection: the one chosen, or one above it (the user may pick eXo or eXo\eXoDOS) */
	static File rootOf(File chosen) {
		for (File d = chosen; d != null; d = d.getParentFile()) {
			File exo = new File(d, "eXo");
			if (exo.isDirectory() && (projects(exo).length > 0))
				return d;
			if ((chosen.toPath().getNameCount() - d.toPath().getNameCount()) >= 3)
				break;
		}
		return null;
	}

	/** the projects in eXo: folders with a "!..." folder of start files in them */
	private static File[] projects(File exo) {
		File[] dirs = exo.listFiles(f -> f.isDirectory() && (bangs(f).length > 0));
		return dirs == null ? new File[0]: dirs;
	}

	private static File[] bangs(File project) {
		File[] dirs = project.listFiles(f -> f.isDirectory() && f.getName().startsWith("!"));
		return dirs == null ? new File[0]: dirs;
	}

	/** @return null if this is no eXo collection */
	public static Result find(File chosen) {
		File root = rootOf(chosen);
		if (root == null)
			return null;
		Map<String, Entry> meta = readPlatforms(root);
		List<StoreGameImport.Found> games = new ArrayList<>();
		int notInstalled = 0;
		for (File project: projects(new File(root, "eXo"))) {
			for (File bang: bangs(project)) {
				File[] gameFolders = bang.listFiles(File::isDirectory);
				if (gameFolders == null)
					continue;
				for (File g: gameFolders) {
					File conf = new File(g, "dosbox.conf");
					if (!conf.isFile())
						continue;
					File gameDir = new File(project, g.getName());
					if (!gameDir.isDirectory()) {
						notInstalled++;
						continue;
					}
					Entry e = meta.get(key(project.getName(), bang.getName(), g.getName()));
					games.add(new StoreGameImport.Found(e != null ? e.title(): titleOfStart(g), gameDir, List.of(conf), root, project.getName(), e != null ? e.meta(): null));
				}
			}
		}
		games.sort(Comparator.comparing(StoreGameImport.Found::title, String.CASE_INSENSITIVE_ORDER));
		return new Result(games, notInstalled, root);
	}

	/** a game of the LaunchBox data: its title and what else it says */
	record Entry(String title, StoreGameImport.Meta meta) {
	}

	private static String key(String project, String bang, String game) {
		return (project + "/" + bang + "/" + game).toLowerCase(Locale.ROOT);
	}

	/** the title from the name of the start file ("1942 - The Pacific Air War (1994).bat"), else the folder */
	static String titleOfStart(File gameFolder) {
		File[] bats = gameFolder.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".bat") && !f.getName().equalsIgnoreCase("install.bat"));
		if ((bats != null) && (bats.length > 0)) {
			String n = bats[0].getName();
			return n.substring(0, n.length() - 4).replaceFirst("\\s*\\((?:19|20)\\d\\d\\)\\s*$", "").trim();
		}
		return gameFolder.getName();
	}

	/** the LaunchBox data of all platforms, by the folder of the start file ("eXo/eXoDOS/!dos/1942" in lower case) */
	static Map<String, Entry> readPlatforms(File root) {
		Map<String, Entry> result = new HashMap<>();
		File[] xmls = new File(root, "Data" + File.separator + "Platforms").listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".xml"));
		if (xmls == null)
			return result;
		for (File xml: xmls) {
			String platform = xml.getName().substring(0, xml.getName().length() - 4);
			CoverIndex covers = new CoverIndex(new File(root, "Images" + File.separator + platform + File.separator + "Box - Front"));
			// alpha 48: more pictures of the game, the first of each kind
			List<CoverIndex> more = new ArrayList<>();
			for (String kind: PICTURE_KINDS)
				more.add(new CoverIndex(new File(root, "Images" + File.separator + platform + File.separator + kind)));
			try (InputStream in = new FileInputStream(xml)) {
				readGames(in, (fields) -> {
					String app = fields.get("ApplicationPath");
					if (StringUtils.isBlank(app))
						return;
					String[] parts = app.replace('\\', '/').split("/");
					// eXo/<project>/!<short>/<game>/<start>.bat
					if ((parts.length < 5) || !parts[0].equalsIgnoreCase("eXo"))
						return;
					String title = StringUtils.defaultIfBlank(fields.get("Title"), parts[3]).trim();
					String date = StringUtils.defaultString(fields.get("ReleaseDate"));
					List<ExtrasFinder.Extra> extras = new ArrayList<>();
					File manual = inside(root, fields.get("ManualPath"));
					if (manual != null)
						extras.add(new ExtrasFinder.Extra(ExtrasFinder.Kind.MANUAL, manual));
					File music = inside(root, fields.get("MusicPath"));
					if (music != null)
						extras.add(new ExtrasFinder.Extra(ExtrasFinder.Kind.SOUNDTRACK, music));
					StoreGameImport.Meta m = new StoreGameImport.Meta(fields.get("Developer"), fields.get("Publisher"), date.matches("\\d{4}.*") ? date.substring(0, 4): "",
						fields.get("Genre"), fields.get("Notes"), () -> covers.of(title), () -> {
							List<File> pictures = new ArrayList<>();
							for (CoverIndex index: more) {
								File f = index.of(title);
								if (f != null)
									pictures.add(f);
							}
							return pictures;
						}, extras);
					result.putIfAbsent(key(parts[1], parts[2], parts[3]), new Entry(title, m));
				});
			} catch (IOException | XMLStreamException | RuntimeException e) {
				// unreadable data: the games are found without it
			}
		}
		return result;
	}

	/** the kinds of pictures besides the cover, as LaunchBox names their folders (alpha 48) */
	static final List<String> PICTURE_KINDS = List.of("Box - Back", "Screenshot - Game Title", "Screenshot - Gameplay");

	/**
	 * alpha 48: a path of the LaunchBox data (relative to the collection, e.g. "Manuals\\MS-DOS\\Doom.pdf") as an existing file in the
	 * collection; null if empty, missing, or outside of it (the data is untrusted)
	 */
	/** for the tests */
	public static File insideForTest(File root, String path) {
		return inside(root, path);
	}

	static File inside(File root, String path) {
		if (StringUtils.isBlank(path) || path.contains("\0"))
			return null;
		String p = path.trim().replace('\\', '/');
		if (p.startsWith("/") || p.matches("^[A-Za-z]:.*"))
			return null;
		try {
			File rootCanonical = root.getCanonicalFile();
			File f = new File(rootCanonical, p).getCanonicalFile();
			return (f.toPath().startsWith(rootCanonical.toPath()) && f.isFile()) ? f: null;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** the text of the simple child elements of each Game element of a LaunchBox platform file */
	static void readGames(InputStream in, java.util.function.Consumer<Map<String, String>> game) throws XMLStreamException {
		XMLInputFactory f = XMLInputFactory.newFactory();
		f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		XMLStreamReader r = f.createXMLStreamReader(in);
		Map<String, String> fields = null;
		String field = null;
		StringBuilder text = new StringBuilder();
		int depth = 0;
		while (r.hasNext()) {
			int ev = r.next();
			if (ev == XMLStreamConstants.START_ELEMENT) {
				depth++;
				if ((depth == 2) && r.getLocalName().equals("Game"))
					fields = new HashMap<>();
				else if ((depth == 3) && (fields != null)) {
					field = r.getLocalName();
					text.setLength(0);
				}
			} else if (((ev == XMLStreamConstants.CHARACTERS) || (ev == XMLStreamConstants.CDATA)) && (field != null)) {
				if (text.length() < 20000)
					text.append(r.getText());
			} else if (ev == XMLStreamConstants.END_ELEMENT) {
				if ((depth == 3) && (fields != null) && (field != null)) {
					fields.put(field, text.toString());
					field = null;
				} else if ((depth == 2) && (fields != null)) {
					game.accept(fields);
					fields = null;
				}
				depth--;
			}
		}
		r.close();
	}

	/** the covers of a platform, by the start of their file name; the folder is read once, when the first one is needed */
	static final class CoverIndex {
		private final File dir_;
		private Map<String, File> byName_;

		CoverIndex(File dir) {
			dir_ = dir;
		}

		/** LaunchBox's file name for a title: characters Windows does not allow, and the apostrophe, as "_" */
		static String fileNameOf(String title) {
			return title.replaceAll("[\\\\/:*?\"<>|']", "_");
		}

		synchronized File of(String title) {
			if (byName_ == null) {
				byName_ = new HashMap<>();
				if (dir_.isDirectory())
					try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(dir_.toPath(), 3)) {
						files.filter(java.nio.file.Files::isRegularFile).forEach(p -> {
							String n = p.getFileName().toString();
							String low = n.toLowerCase(Locale.ROOT);
							int dash = low.lastIndexOf("-0");
							if ((dash > 0) && (low.endsWith(".jpg") || low.endsWith(".png")))
								byName_.merge(low.substring(0, dash), p.toFile(), (x, y) -> x.getName().compareToIgnoreCase(y.getName()) <= 0 ? x: y);
						});
					} catch (IOException | RuntimeException e) {
						// no covers then
					}
			}
			return byName_.get(fileNameOf(title).toLowerCase(Locale.ROOT));
		}
	}
}
