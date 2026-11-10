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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;


/**
 * Which charset a DOSBox version reads the configuration file of a profile with, for umlauts in paths (tested on Windows 10):
 * <ul>
 * <li>DOSBox Staging takes the file as UTF-8 (a "mount" with "Ä" in Windows-1252: "Problem decoding UTF-8", the folder is not
 * found)</li>
 * <li>DOSBox 0.74 (and SVN, Daum, ECE) on Windows take the bytes as file names in the code page of Windows (a "mount" with "Ä" in
 * UTF-8: the folder is not found)</li>
 * <li>DOSBox-X: not known for sure (it tries the code page of Windows first), nothing is said</li>
 * <li>on macOS and Linux file names are UTF-8: UTF-8 for every DOSBox</li>
 * </ul>
 * Only matters for a file with characters outside ASCII in its [autoexec] (the bytes of ASCII are the same in all of them).
 */
public final class ConfCharset {

	/** a file whose charset does not fit its DOSBox version: what it is in, what it needs, a line that shows it */
	public record Mismatch(Charset file, Charset needed, String line) {
	}

	private ConfCharset() {
	}

	/** for tests: -Ddbgl.charset.windows=true acts as on Windows */
	static boolean onWindows() {
		return SystemUtils.IS_WINDOWS || Boolean.getBoolean("dbgl.charset.windows");
	}

	/** @return the charset the DOSBox version reads the configuration file with, null if not known (DOSBox-X) */
	public static Charset needed(DosboxVersion db) {
		if (db == null)
			return null;
		if (!onWindows())
			return StandardCharsets.UTF_8;
		Generation gen = GenerationAwareConfigurable.determineGen(db);
		if (gen == null)
			return null;
		int fam = GenerationAwareConfigurable.familyIdx(gen);
		if (fam == GenerationAwareConfigurable.familyIdx(Generation.staging_0830))
			return StandardCharsets.UTF_8;
		if (fam == GenerationAwareConfigurable.familyIdx(Generation.x_20241001))
			return null;
		return Configuration.windowsCodePage();
	}

	/**
	 * The kind of problem a DOSBox version has on Windows with a folder with an umlaut, whatever the charset of the file:
	 * "stagingumlaut2" (Staging 0.83 crashed right after starting, tested; 0.82 does not find it), "xumlaut2" (DOSBox-X does not
	 * find it, tested with Windows-1252 and with UTF-8), null for the others (DOSBox 0.74 finds it in the code page of Windows).
	 */
	static String umlautProblem(DosboxVersion db) {
		if (db == null)
			return null;
		boolean linux = !onWindows() && (SystemUtils.IS_LINUX || Boolean.getBoolean("dbgl.charset.linux"));
		if (!onWindows() && !linux)
			return null; // macOS: not known
		Generation gen = GenerationAwareConfigurable.determineGen(db);
		if (gen == null)
			return null;
		int fam = GenerationAwareConfigurable.familyIdx(gen);
		if (linux) // Staging on Linux: not found, tested with UTF-8 and Windows-1252 ("Image file not found"); others not known
			return fam == GenerationAwareConfigurable.familyIdx(Generation.staging_0830) ? "stagingumlautlinux2": null;
		if (fam == GenerationAwareConfigurable.familyIdx(Generation.staging_0830))
			return "stagingumlaut2";
		if (fam == GenerationAwareConfigurable.familyIdx(Generation.x_20241001))
			return "xumlaut2";
		return null;
	}

	/** the charset the configuration file is in (looking at its bytes), the one of the system if it has nothing else */
	public static Charset of(File conf) {
		try {
			Charset cs = (conf != null) && conf.isFile() ? Configuration.legacyCharsetOf(Files.readAllBytes(conf.toPath())): null;
			return cs != null ? cs: Charset.defaultCharset();
		} catch (Exception e) {
			return Charset.defaultCharset();
		}
	}

	/** the first line of the [autoexec] (not a comment) with a character outside ASCII, as written; null if there is none */
	public static String nonAsciiAutoexecLine(File conf, Charset cs) {
		for (String raw: ProfileChecks.autoexecLines(conf)) // bytes as ISO-8859-1
			if (raw.chars().anyMatch(c -> c > 127)) {
				String l = new String(raw.getBytes(StandardCharsets.ISO_8859_1), cs).trim();
				return l.length() > 100 ? l.substring(0, 100) + "…": l;
			}
		return null;
	}

	/**
	 * @param db the DOSBox version the profile uses (or will use, e.g. when switching)
	 * @return what does not fit, null if all is well (or not known, or it cannot be written in the needed charset)
	 */
	public static Mismatch mismatch(Profile prof, DosboxVersion db) {
		Charset needed = needed(db);
		if (needed == null)
			return null;
		File conf = prof.getConfigurationCanonicalFile();
		Charset file = of(conf);
		if (file.equals(needed))
			return null;
		String line = nonAsciiAutoexecLine(conf, file);
		if (line == null)
			return null;
		try {
			String text = new String(Files.readAllBytes(conf.toPath()), file);
			if (!needed.newEncoder().canEncode(text))
				return null; // e.g. a character the code page of Windows does not have: nothing to recommend
		} catch (Exception e) {
			return null;
		}
		return new Mismatch(file, needed, line);
	}

	/** a mounted folder (or the program) with a character outside ASCII in its path, for Staging and DOSBox-X on Windows; or null */
	public static String umlautFolder(Profile prof) {
		if (umlautProblem(prof.getDosboxVersion()) == null)
			return null;
		List<org.dbgl.model.conf.mount.Mount> mounts = prof.getConfiguration().getAutoexec().getMountingpoints();
		for (org.dbgl.model.conf.mount.Mount m: mounts)
			if (!m.isUnmounted() && (m instanceof org.dbgl.model.conf.mount.DirMount dm) && dm.getPath().getPath().chars().anyMatch(c -> c > 127))
				return dm.getPath().getPath();
		String main = prof.getConfiguration().getAutoexec().getMain();
		if ((main != null) && main.chars().anyMatch(c -> c > 127))
			return main;
		return null;
	}

	/** the configuration is written in the charset (null or the one of the system: as DBGL writes by default) */
	public static void writeIn(Profile prof, Charset cs) {
		prof.getConfiguration().setCharset((cs == null) || cs.equals(Charset.defaultCharset()) ? null: cs);
	}
}
