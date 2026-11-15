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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * alpha 49: files of earlier versions of the Extended Edition that now have another place: the diagnostic files, which are
 * written to the folder "logs" now, and the text files of the package: four are in its folder "docs" now under other
 * names, the one that says how to install the update has another name. Only files DBGL knows by their exact name are found; whatever else is in these folders is none of its business.
 */
public final class OldFiles {

	/** the old names of the texts of the package and what they are called in its folder "docs" now */
	public static final Map<String, String> DOCS = docs();
	public static final String DOCS_DIR = "docs";
	/** how to install the update: under another name now, still in the main folder (where it is not overlooked) */
	public static final String OLD_README = "UPDATE-LIESMICH.txt", README = "update-readme.txt";
	private static final Pattern FREEZE = Pattern.compile("freeze-\\d{8}-\\d{6}\\.txt");
	private static final List<String> DIAGNOSTICS = List.of("menubar-check.txt", "toolbar-metrics.txt");

	private OldFiles() {
	}

	private static Map<String, String> docs() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("CHANGELOG-de.txt", "changelog-de.txt");
		m.put("CHANGELOG-en.txt", "changelog-en.txt");
		m.put("NEU-IN-DER-EXTENDED-EDITION.txt", "whats-new-de.txt");
		m.put("NEW-IN-EXTENDED-EDITION.txt", "whats-new-en.txt");
		return java.util.Collections.unmodifiableMap(m);
	}

	/**
	 * @param toLogs   diagnostic files to be moved to the folder "logs"
	 * @param toDelete old texts of the package, there again in its folder "docs"
	 */
	public record Found(List<File> toLogs, List<File> toDelete) {
		public boolean isEmpty() {
			return toLogs.isEmpty() && toDelete.isEmpty();
		}

		public List<File> all() {
			List<File> all = new ArrayList<>(toLogs);
			all.addAll(toDelete);
			return all;
		}
	}

	/**
	 * @param programDir the folder of dbgl.jar, or null when not started from it
	 * @param dataDir    the folder of the data of DBGL (the same one for a portable DBGL)
	 */
	public static Found find(File programDir, File dataDir) {
		List<File> toLogs = new ArrayList<>(), toDelete = new ArrayList<>();
		List<String> seen = new ArrayList<>();
		for (File dir: new File[] {dataDir, programDir}) {
			File[] files = (dir == null) || seen.contains(canonical(dir)) ? null: dir.listFiles(File::isFile);
			if (files == null)
				continue;
			seen.add(canonical(dir));
			java.util.Arrays.sort(files);
			for (File f: files)
				if (FREEZE.matcher(f.getName()).matches() || DIAGNOSTICS.contains(f.getName()))
					toLogs.add(f);
		}
		if (programDir != null)
			for (Map.Entry<String, String> e: DOCS.entrySet()) {
				File old = new File(programDir, e.getKey());
				// only when the new one is there: nothing is deleted that has no successor
				if (old.isFile() && new File(new File(programDir, DOCS_DIR), e.getValue()).isFile())
					toDelete.add(old);
			}
		if ((programDir != null) && new File(programDir, OLD_README).isFile() && new File(programDir, README).isFile())
			toDelete.add(new File(programDir, OLD_README));
		return new Found(toLogs, toDelete);
	}

	private static String canonical(File f) {
		try {
			return f.getCanonicalPath();
		} catch (IOException e) {
			return f.getAbsolutePath();
		}
	}

	/**
	 * The diagnostic files moved to the folder "logs" (one of the same name there is replaced), the old texts deleted.
	 *
	 * @return the files that could not be moved or deleted
	 */
	public static List<File> clean(Found found, File logsDir) {
		List<File> failed = new ArrayList<>();
		for (File f: found.toLogs()) {
			try {
				Files.createDirectories(logsDir.toPath());
				Files.move(f.toPath(), new File(logsDir, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException | RuntimeException e) {
				failed.add(f);
			}
		}
		for (File f: found.toDelete()) {
			try {
				Files.delete(f.toPath());
			} catch (IOException | RuntimeException e) {
				failed.add(f);
			}
		}
		return failed;
	}
}
