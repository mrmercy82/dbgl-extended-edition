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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.FileLocation;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;


/**
 * Finds manuals, walkthroughs, maps and soundtracks in the folder of a game and links them in free link fields of its profile.
 */
public final class ExtrasFinder {

	public static final String SECTION = "profiles", ON_ADD = "findextras";

	public enum Kind {
		MANUAL, WALKTHROUGH, MAP, REFERENCE, COPYPROTECTION, SOUNDTRACK, OTHER
	}

	/** one file or folder found, with the link title it gets */
	public record Extra(Kind kind, File file) {
		public String title() {
			return TextService.getInstance().get("dialog.extras.kind." + kind.name().toLowerCase(Locale.ROOT));
		}
	}

	/**
	 * @return the icon of a link (ImageService): web page, or the kind of extra, by its title ("Handbuch (x.pdf)", as linked by
	 *         DBGL) or else by its file name
	 */
	public static String iconOf(String title, String destination) {
		String d = StringUtils.trimToEmpty(destination).toLowerCase(Locale.ROOT);
		if (d.startsWith("http://") || d.startsWith("https://") || d.startsWith("www."))
			return org.dbgl.service.ImageService.IMG_EXTRA_WEB;
		Kind kind = null;
		String t = StringUtils.trimToEmpty(title);
		for (Kind k: Kind.values())
			if (t.startsWith(new Extra(k, new File("")).title()))
				kind = k;
		if (kind == null) {
			try {
				kind = kindOf(new File(d).toPath());
				if ((kind == null) && new File(d).isDirectory())
					kind = Kind.SOUNDTRACK.equals(kindOf(new File(d, "x.mp3").toPath())) ? Kind.SOUNDTRACK: null;
			} catch (RuntimeException e) {
				kind = null;
			}
		}
		return org.dbgl.service.ImageService.IMG_EXTRA_PREFIX + (kind == null ? Kind.OTHER: kind).name().toLowerCase(Locale.ROOT) + ".png";
	}

	static final int DEPTH = 5;
	static final Set<String> DOCUMENTS = Set.of("pdf", "txt", "doc", "docx", "rtf", "htm", "html", "md", "odt", "chm");
	static final Set<String> PICTURES = Set.of("jpg", "jpeg", "png", "gif");
	static final Set<String> MUSIC = Set.of("mp3", "ogg", "flac", "opus", "m4a"); // no .wav and .voc: games keep their sounds in those
	static final String[] MANUAL_WORDS = {"manual", "handbuch", "anleitung", "handleiding", "manuel", "manuale", "guide", "reference", "refcard", "instructions",
			"docs"};
	static final String[] WALKTHROUGH_WORDS = {"walkthrough", "solution", "loesung", "lösung", "losung", "komplettl", "hint", "cheat", "faq", "spoiler"};
	static final String[] MAP_WORDS = {"map", "karte", "maps", "karten"};
	static final String[] REFERENCE_WORDS = {"refcard", "reference card", "referencecard", "referenzkarte", "quick reference", "quickref", "kurzreferenz", "keyboard", "tastatur",
			"command card", "befehlskarte"};
	static final String[] COPYPROTECTION_WORDS = {"kopierschutz", "copyprot", "copy prot", "copy-prot", "codewheel", "code wheel", "code-wheel", "coderad", "code-rad", "codetabelle",
			"codeblatt", "lenslok", "dial-a-pirate", "protection"};
	static final String[] SOUNDTRACK_WORDS = {"soundtrack", "ost", "music", "musik", "audio"};

	private ExtrasFinder() {
	}

	public static boolean onAdd() {
		return SettingsService.getInstance().getBooleanValue(SECTION, ON_ADD);
	}

	/** @return the folder of the game: the folder of its main program; null when there is none or it is the DOS root itself */
	public static File gameFolder(Profile profile) {
		String main = profile.getConfiguration().getAutoexec().getMain();
		if (StringUtils.isBlank(main))
			return null;
		File file = FilesUtils.determineMainFile(new FileLocation(main, FileLocationService.getInstance().dosrootRelative()).getCanonicalFile());
		File dir = file.getAbsoluteFile().getParentFile();
		if ((dir == null) || !dir.isDirectory())
			return null;
		File dosroot = FileLocationService.getInstance().getDosroot();
		if ((dosroot != null) && dosroot.getAbsoluteFile().equals(dir.getAbsoluteFile()))
			return null; // the folder of all games: their extras are not this game's
		return dir;
	}

	static boolean has(String name, String[] words) {
		String n = name.toLowerCase(Locale.ROOT);
		for (String w: words)
			if ((w.length() <= 3) ? n.matches("(^|.*[^a-z])" + w + "([^a-z].*|$)"): n.contains(w))
				return true;
		return false;
	}

	static String ext(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "": name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	/** @return what fits as an extra, by name and type; null for anything else */
	static Kind kindOf(Path p) {
		String name = p.getFileName().toString();
		String ext = ext(name);
		String path = p.toString();
		if (DOCUMENTS.contains(ext)) {
			if (has(name, WALKTHROUGH_WORDS))
				return Kind.WALKTHROUGH;
			if (has(name, COPYPROTECTION_WORDS))
				return Kind.COPYPROTECTION;
			if (has(name, REFERENCE_WORDS))
				return Kind.REFERENCE;
			if (has(name, MAP_WORDS) && ext.equals("pdf"))
				return Kind.MAP;
			if (has(name, MANUAL_WORDS) || ext.equals("pdf") || ext.equals("chm"))
				return Kind.MANUAL; // a PDF in a game folder is nearly always its manual
			return null;
		}
		if (PICTURES.contains(ext) && has(name, COPYPROTECTION_WORDS))
			return Kind.COPYPROTECTION;
		if (PICTURES.contains(ext) && has(name, REFERENCE_WORDS))
			return Kind.REFERENCE;
		if (PICTURES.contains(ext) && has(name, MAP_WORDS))
			return Kind.MAP;
		if (MUSIC.contains(ext) && (has(name, SOUNDTRACK_WORDS) || has(path, SOUNDTRACK_WORDS)))
			return Kind.SOUNDTRACK;
		return null;
	}

	/** @return the extras in the folder of the game, documents one by one, music files of one folder as that folder */
	public static List<Extra> find(Profile profile) {
		File dir = gameFolder(profile);
		return dir == null ? List.of(): find(dir, otherGameFolders(dir, profile));
	}

	/** the game folder of each profile (by its configuration file and when it was changed), so that it is read only once */
	private static final Map<String, File> GAME_FOLDERS = new java.util.concurrent.ConcurrentHashMap<>();
	private static final File NONE = new File("");

	/**
	 * The game folders of the other profiles that lie below this folder, e.g. all games when SMARTDRV.EXE in the folder of all
	 * games is the main program. Their extras belong to those games, so their folders are left out. Subfolders of this game
	 * without a profile of their own are searched as usual (before, a folder with 5 subfolders with programs, like DSA with
	 * its parts, was only searched on its top level).
	 */
	static java.util.Set<File> otherGameFolders(File dir, Profile self) {
		java.util.Set<File> result = new java.util.HashSet<>();
		int others = 0;
		File[] subs = dir.listFiles(File::isDirectory);
		if ((subs == null) || (subs.length == 0))
			return result;
		Path base = dir.getAbsoluteFile().toPath();
		try {
			for (Profile p: allProfiles()) {
				if (p.getId() == self.getId())
					continue;
				File folder = cachedGameFolder(p);
				if ((folder != null) && (folder != NONE)) {
					Path f = folder.getAbsoluteFile().toPath();
					if (f.startsWith(base) && !f.equals(base)) {
						result.add(base.resolve(base.relativize(f).getName(0)).toFile()); // the subfolder of this folder it lies in
						others++;
					}
				}
			}
		} catch (Exception e) {
			// no database at hand (e.g. in tests): nothing left out
		}
		// a few other games below (e.g. the parts of DSA, with profiles of their own): their extras are found as well, also
		// when they are linked in those profiles already; only a folder of many games (SMARTDRV.EXE in the folder of all games)
		// leaves out the folders of the others
		if (others < COLLECTION_PROFILES)
			return new java.util.HashSet<>();
		// never leave out a folder of this game itself: one with its CD images or other mounted files in it
		List<File> own = new ArrayList<>();
		for (org.dbgl.model.conf.mount.Mount m: self.getConfiguration().getAutoexec().getMountingpoints()) {
			if (m instanceof org.dbgl.model.conf.mount.ImageMount im)
				own.addAll(List.of(im.getCanonicalImgPaths()));
			else if (m instanceof org.dbgl.model.conf.mount.DirMount dm)
				own.add(dm.getCanonicalPath());
		}
		for (File f: own)
			result.removeIf(x -> f.getAbsoluteFile().toPath().startsWith(x.getAbsoluteFile().toPath()));
		return result;
	}

	/** from this many other profiles with their game below the folder on, it is a folder of many games */
	static final int COLLECTION_PROFILES = 5;

	private static List<Profile> allProfiles_;
	private static long allProfilesRead_;

	/** all profiles, read again at most every half minute: a search through many selected profiles reads them only once */
	private static synchronized List<Profile> allProfiles() throws java.sql.SQLException {
		if ((allProfiles_ == null) || (System.currentTimeMillis() - allProfilesRead_ > 30000)) {
			allProfiles_ = new org.dbgl.model.repository.ProfileRepository().list("", "", new org.dbgl.model.repository.DosboxVersionRepository().listAll());
			allProfilesRead_ = System.currentTimeMillis();
		}
		return allProfiles_;
	}

	private static File cachedGameFolder(Profile p) {
		File conf;
		try {
			conf = p.getConfigurationCanonicalFile();
		} catch (RuntimeException e) {
			return NONE;
		}
		String key = conf.getPath() + '|' + conf.lastModified();
		return GAME_FOLDERS.computeIfAbsent(key, k -> {
			try {
				p.getConfiguration().loadWithAutoexec(org.dbgl.service.TextService.getInstance(), List.of(), null);
				File folder = gameFolder(p);
				return folder == null ? NONE: folder;
			} catch (Exception e) {
				return NONE;
			}
		});
	}

	public static List<Extra> find(File dir) {
		return find(dir, java.util.Set.of());
	}

	/** @param skip folders below dir that belong to other games */
	public static List<Extra> find(File dir, java.util.Set<File> skip) {
		List<Extra> result = new ArrayList<>();
		Map<File, Integer> musicFolders = new LinkedHashMap<>();
		java.util.Set<Path> skipPaths = new java.util.HashSet<>();
		skip.forEach(f -> skipPaths.add(f.getAbsoluteFile().toPath()));
		List<Path> found = new ArrayList<>();
		try {
			Files.walkFileTree(dir.getAbsoluteFile().toPath(), java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), DEPTH, new java.nio.file.SimpleFileVisitor<Path>() {
				@Override
				public java.nio.file.FileVisitResult preVisitDirectory(Path d, java.nio.file.attribute.BasicFileAttributes attrs) {
					return skipPaths.contains(d) ? java.nio.file.FileVisitResult.SKIP_SUBTREE: java.nio.file.FileVisitResult.CONTINUE;
				}

				@Override
				public java.nio.file.FileVisitResult visitFile(Path f, java.nio.file.attribute.BasicFileAttributes attrs) {
					if (attrs.isRegularFile())
						found.add(f);
					return java.nio.file.FileVisitResult.CONTINUE;
				}

				@Override
				public java.nio.file.FileVisitResult visitFileFailed(Path f, IOException e) {
					return java.nio.file.FileVisitResult.CONTINUE; // an unreadable file or folder: just go on
				}
			});
		} catch (IOException | RuntimeException e) {
			// an unreadable folder: just what was found so far
		}
		found.stream().sorted().forEach(p -> {
			if (MUSIC.contains(ext(p.getFileName().toString())))
				musicFolders.merge(p.getParent().toFile(), 1, Integer::sum);
			Kind k = kindOf(p);
			if ((k != null) && (k != Kind.SOUNDTRACK))
				result.add(new Extra(k, p.toFile()));
		});
		// a soundtrack: a folder with several music files (or one named so), linked as the folder
		musicFolders.forEach((folder, count) -> {
			boolean named = has(folder.getName(), SOUNDTRACK_WORDS) || has(folder.getPath(), SOUNDTRACK_WORDS);
			if ((count >= 3) || named)
				result.add(new Extra(Kind.SOUNDTRACK, folder));
		});
		return result;
	}

	/** how a link to this file is written (relative to the DBGL data folder where possible, like the browse button does) */
	public static String linkOf(File file) {
		return new FileLocation(file.getPath(), FileLocationService.getInstance().dataRelative()).getFile().getPath();
	}

	/**
	 * Put the extras into free link fields of the profile; links already there are not added twice.
	 * 
	 * @return how many were added (less than given when the 8 link fields are full)
	 */
	public static int link(Profile profile, List<Extra> extras) {
		int added = 0;
		for (Extra e: extras) {
			String link = linkOf(e.file());
			boolean known = false;
			int free = -1;
			for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
				String d = profile.getLinkDestination(i);
				if (StringUtils.isBlank(d)) {
					if (free == -1)
						free = i;
				} else if (d.equalsIgnoreCase(link) || new File(d).getAbsoluteFile().equals(e.file().getAbsoluteFile())) {
					known = true;
				}
			}
			if (known || (free == -1))
				continue;
			profile.setLinkDestination(free, link);
			profile.setLinkTitle(free, e.title() + (e.kind() == Kind.SOUNDTRACK ? "": " (" + e.file().getName() + ")"));
			added++;
		}
		return added;
	}
}
