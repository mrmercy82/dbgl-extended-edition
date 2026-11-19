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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import org.apache.commons.lang3.StringUtils;


/**
 * alpha 52: the MS-DOS games of an own LaunchBox collection (not eXoDOS, which has its own import), as LaunchBox keeps them:
 * <ul>
 * <li>{@code Data\Platforms\MS-DOS.xml}: a Game element per game, with Title, ApplicationPath (the start file), UseDosBox,
 * DosBoxConfigurationPath, RootFolder (the folder LaunchBox mounts as C:, else the one of the start file), CommandLine,
 * Emulator (the ID of an emulator in {@code Data\Emulators.xml}, when DOSBox is set up as emulator), Developer, ...</li>
 * <li>{@code Images\MS-DOS\Box - Front\<title>-01.jpg} and the other kinds of pictures</li>
 * </ul>
 * Paths are relative to the LaunchBox folder or absolute. From each game a configuration for DBGL is made: the settings of
 * its own DOSBox configuration if it has one, and an [autoexec] that mounts its folder and starts it, the way LaunchBox does;
 * a configuration that mounts something itself is taken as it is. Everything is only read; the data is untrusted (the XML
 * without DTD and external entities).
 */
public final class LaunchBoxImport {

	/** what was found: the games, those of eXoDOS in it, those without DOSBox or without their start file, the folder */
	public record Result(List<StoreGameImport.Found> games, int exo, int skipped, File root) {
	}

	static final String STORE = "LaunchBox";

	private LaunchBoxImport() {
	}

	/** the LaunchBox folder: the one chosen or one above it, with Data\Platforms in it */
	static File rootOf(File chosen) {
		int up = 0;
		for (File d = chosen; (d != null) && (up < 3); d = d.getParentFile(), up++)
			if (new File(d, "Data" + File.separator + "Platforms").isDirectory())
				return d;
		return null;
	}

	/** @return null if this is no LaunchBox folder */
	public static Result find(File chosen, File work) {
		File root = rootOf(chosen);
		if (root == null)
			return null;
		Map<String, Boolean> emulators = readEmulators(root);
		File[] xmls = new File(root, "Data" + File.separator + "Platforms").listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".xml"));
		List<StoreGameImport.Found> games = new ArrayList<>();
		int[] exo = {0}, skipped = {0};
		Set<String> seen = new HashSet<>();
		if (xmls != null)
			for (File xml: xmls) {
				String platform = xml.getName().substring(0, xml.getName().length() - 4);
				ExoImport.CoverIndex covers = new ExoImport.CoverIndex(new File(root, "Images" + File.separator + platform + File.separator + "Box - Front"));
				List<ExoImport.CoverIndex> more = new ArrayList<>();
				for (String kind: ExoImport.PICTURE_KINDS)
					more.add(new ExoImport.CoverIndex(new File(root, "Images" + File.separator + platform + File.separator + kind)));
				try (InputStream in = new FileInputStream(xml)) {
					ExoImport.readGames(in, fields -> {
						if (!isDos(fields.get("Platform"), platform))
							return;
						String app = StringUtils.defaultString(fields.get("ApplicationPath")).trim();
						if (app.replace('\\', '/').toLowerCase(Locale.ROOT).startsWith("exo/")) {
							exo[0]++;
							return;
						}
						StoreGameImport.Found f = gameOf(root, fields, emulators, covers, more, work);
						if (f == null)
							skipped[0]++;
						else if (seen.add(f.title().toLowerCase(Locale.ROOT) + "|" + f.gameDir().getPath().toLowerCase(Locale.ROOT)))
							games.add(f);
					});
				} catch (IOException | javax.xml.stream.XMLStreamException | RuntimeException e) {
					// an unreadable platform file: the others still count
				}
			}
		games.sort(Comparator.comparing(StoreGameImport.Found::title, String.CASE_INSENSITIVE_ORDER));
		return new Result(games, exo[0], skipped[0], root);
	}

	/** a game of the platform MS-DOS (by its field, else by the name of the file) */
	static boolean isDos(String platformField, String fileName) {
		String p = StringUtils.isNotBlank(platformField) ? platformField.trim(): fileName;
		return p.equalsIgnoreCase("MS-DOS") || p.equalsIgnoreCase("DOS");
	}

	/** the emulators of LaunchBox, by their ID: whether it is a DOSBox (by the name of its program) */
	static Map<String, Boolean> readEmulators(File root) {
		Map<String, Boolean> result = new HashMap<>();
		File xml = new File(root, "Data" + File.separator + "Emulators.xml");
		if (!xml.isFile())
			return result;
		try (InputStream in = new FileInputStream(xml)) {
			readElements(in, "Emulator", fields -> {
				String id = StringUtils.defaultString(fields.get("ID")).trim().toLowerCase(Locale.ROOT);
				String app = StringUtils.defaultString(fields.get("ApplicationPath")) + " " + StringUtils.defaultString(fields.get("Title"));
				if (!id.isEmpty())
					result.put(id, app.toLowerCase(Locale.ROOT).contains("dosbox"));
			});
		} catch (IOException | javax.xml.stream.XMLStreamException | RuntimeException e) {
			// no emulators known then
		}
		return result;
	}

	/** like ExoImport.readGames, for any element below the root */
	static void readElements(InputStream in, String element, java.util.function.Consumer<Map<String, String>> each) throws javax.xml.stream.XMLStreamException {
		javax.xml.stream.XMLInputFactory f = javax.xml.stream.XMLInputFactory.newFactory();
		f.setProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD, false);
		f.setProperty(javax.xml.stream.XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		javax.xml.stream.XMLStreamReader r = f.createXMLStreamReader(in);
		Map<String, String> fields = null;
		String field = null;
		StringBuilder text = new StringBuilder();
		int depth = 0;
		while (r.hasNext()) {
			int ev = r.next();
			if (ev == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
				depth++;
				if ((depth == 2) && r.getLocalName().equals(element))
					fields = new HashMap<>();
				else if ((depth == 3) && (fields != null)) {
					field = r.getLocalName();
					text.setLength(0);
				}
			} else if (((ev == javax.xml.stream.XMLStreamConstants.CHARACTERS) || (ev == javax.xml.stream.XMLStreamConstants.CDATA)) && (field != null)) {
				if (text.length() < 20000)
					text.append(r.getText());
			} else if (ev == javax.xml.stream.XMLStreamConstants.END_ELEMENT) {
				if ((depth == 3) && (fields != null) && (field != null)) {
					fields.put(field, text.toString());
					field = null;
				} else if ((depth == 2) && (fields != null)) {
					each.accept(fields);
					fields = null;
				}
				depth--;
			}
		}
		r.close();
	}

	/** a path of the LaunchBox data as an existing file or folder: absolute, or relative to the LaunchBox folder; else null */
	static File resolve(File root, String path) {
		if (StringUtils.isBlank(path) || path.contains("\0"))
			return null;
		String p = path.trim().replace('\\', File.separatorChar).replace('/', File.separatorChar);
		File f = new File(p);
		if (!f.isAbsolute())
			f = new File(root, p);
		try {
			f = f.getCanonicalFile();
		} catch (IOException e) {
			return null;
		}
		return f.exists() ? f: null;
	}

	/** the game as DBGL imports it; null if it does not start with DOSBox or its start file is not there */
	static StoreGameImport.Found gameOf(File root, Map<String, String> fields, Map<String, Boolean> emulators, ExoImport.CoverIndex covers, List<ExoImport.CoverIndex> more,
			File work) {
		String title = StringUtils.defaultString(fields.get("Title")).trim();
		boolean useDosbox = "true".equalsIgnoreCase(StringUtils.trim(fields.get("UseDosBox")));
		String emulator = StringUtils.defaultString(fields.get("Emulator")).trim().toLowerCase(Locale.ROOT);
		boolean dosboxEmulator = !emulator.isEmpty() && Boolean.TRUE.equals(emulators.get(emulator));
		if (!useDosbox && !dosboxEmulator)
			return null; // a Windows game, ScummVM, ...: not for DOSBox
		File app = resolve(root, fields.get("ApplicationPath"));
		String commandLine = StringUtils.defaultString(fields.get("CommandLine")).trim();
		File conf = resolve(root, fields.get("DosBoxConfigurationPath"));
		// DOSBox as emulator: the start file may be a configuration, or the command line names one ("-conf game.conf")
		if ((conf == null) && (app != null) && app.isFile() && app.getName().toLowerCase(Locale.ROOT).endsWith(".conf"))
			conf = app;
		if (conf == null) {
			Matcher m = StoreGameImport.CONF_ARGUMENT.matcher(commandLine);
			if (m.find())
				conf = resolve(root, m.group(1) != null ? m.group(1): m.group(2));
		}
		if ((conf != null) && !conf.isFile())
			conf = null;
		File folder = resolve(root, fields.get("RootFolder"));
		if ((folder != null) && !folder.isDirectory())
			folder = null;
		if (StringUtils.isBlank(title))
			title = app != null ? app.getName().replaceFirst("\\.[^.]*$", ""): "";
		StoreGameImport.Meta meta = metaOf(root, fields, title, covers, more);
		// a configuration that mounts and starts the game itself: as it is
		if ((conf != null) && StoreGameImport.mountsSomething(conf)) {
			File dir = folder != null ? folder: (app != null && app.isFile() && app != conf ? app.getParentFile(): conf.getParentFile());
			return new StoreGameImport.Found(title, dir, List.of(conf), conf.getParentFile(), STORE, meta);
		}
		if ((app == null) || !app.isFile() || app.getName().toLowerCase(Locale.ROOT).endsWith(".conf"))
			return null;
		File dir = folder != null ? folder: app.getParentFile();
		if (!app.toPath().startsWith(dir.toPath()))
			dir = app.getParentFile(); // the start file must be on the mounted drive
		try {
			File made = new File(work, String.format(Locale.ROOT, "lb%05d.conf", work.list() == null ? 0: work.list().length));
			Files.write(made.toPath(), confFor(conf == null ? null: StoreGameImport.read(conf), dir, app, argumentsOf(commandLine)).getBytes(StandardCharsets.ISO_8859_1));
			return new StoreGameImport.Found(title, dir, List.of(made), dir, STORE, meta);
		} catch (IOException e) {
			return null;
		}
	}

	/** what the command line of the game gives the start file itself (not the "-conf ..." for DOSBox) */
	static String argumentsOf(String commandLine) {
		return StoreGameImport.CONF_ARGUMENT.matcher(commandLine).replaceAll("").replaceAll("(?i)-(noconsole|exit|fullscreen)\\b", "").trim();
	}

	/**
	 * the configuration for DBGL: the settings of the game's own configuration (without its [autoexec]), then an [autoexec]
	 * that mounts the folder as C: and starts the file in it, like LaunchBox does
	 */
	static String confFor(String ownConf, File dir, File app, String arguments) {
		StringBuilder b = new StringBuilder();
		if (ownConf != null) {
			boolean inAutoexec = false;
			for (String l: ownConf.split("\\r?\\n")) {
				String t = l.trim().toLowerCase(Locale.ROOT);
				if (t.startsWith("[") && t.endsWith("]"))
					inAutoexec = t.equals("[autoexec]");
				if (!inAutoexec)
					b.append(l).append('\n');
			}
		}
		b.append("[autoexec]\n");
		b.append("mount c \"").append(dir.getPath()).append("\"\n");
		b.append("c:\n");
		String rel = dir.toPath().relativize(app.getParentFile().toPath()).toString().replace(File.separatorChar, '\\');
		if (!rel.isEmpty())
			b.append("cd \\").append(rel).append('\n');
		b.append(app.getName()).append(arguments.isEmpty() ? "": " " + arguments).append('\n');
		return b.toString();
	}

	static StoreGameImport.Meta metaOf(File root, Map<String, String> fields, String title, ExoImport.CoverIndex covers, List<ExoImport.CoverIndex> more) {
		String date = StringUtils.defaultString(fields.get("ReleaseDate"));
		List<ExtrasFinder.Extra> extras = new ArrayList<>();
		File manual = resolve(root, fields.get("ManualPath"));
		if ((manual != null) && manual.isFile())
			extras.add(new ExtrasFinder.Extra(ExtrasFinder.Kind.MANUAL, manual));
		File music = resolve(root, fields.get("MusicPath"));
		if ((music != null) && music.isFile())
			extras.add(new ExtrasFinder.Extra(ExtrasFinder.Kind.SOUNDTRACK, music));
		return new StoreGameImport.Meta(fields.get("Developer"), fields.get("Publisher"), date.matches("\\d{4}.*") ? date.substring(0, 4): "", fields.get("Genre"),
				fields.get("Notes"), () -> covers.of(title), () -> {
					List<File> pictures = new ArrayList<>();
					for (ExoImport.CoverIndex index: more) {
						File f = index.of(title);
						if (f != null)
							pictures.add(f);
					}
					return pictures;
				}, extras);
	}
}
