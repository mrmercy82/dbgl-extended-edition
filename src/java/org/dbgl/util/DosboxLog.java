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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.SettingsService;


/**
 * The output of DOSBox (what the status window shows) saved as a text file, for the profiles where this is switched on: in
 * the folder 'logs' of DBGL, one folder per profile ("Title (id)"), one file per start. The newest {@link #KEEP} files of a
 * profile are kept.
 */
public final class DosboxLog {

	public static final String SECTION = "dosboxlog", PROFILES = "profiles";
	public static final int KEEP = 20;
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
	private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

	private final BufferedWriter writer_;

	private DosboxLog(BufferedWriter writer) {
		writer_ = writer;
	}

	/** the profiles that save the output of DOSBox */
	private static Set<String> ids() {
		return new LinkedHashSet<>(Arrays.asList(SettingsService.getInstance().getValues(SECTION, PROFILES)));
	}

	public static boolean isOn(int profileId) {
		return (profileId >= 0) && ids().contains(String.valueOf(profileId));
	}

	public static void setOn(int profileId, boolean on) {
		if (profileId < 0)
			return;
		Set<String> ids = ids();
		if (on ? ids.add(String.valueOf(profileId)): ids.remove(String.valueOf(profileId)))
			SettingsService.getInstance().setValue(SECTION, PROFILES, String.join(" ", ids));
	}

	public static File logsDir() {
		return new File(FileLocationService.getInstance().getDataDir(), "logs");
	}

	/** the folder of a profile's logs; found by the id, as the title may have changed since */
	public static File folderOf(int profileId, String title) {
		File[] dirs = logsDir().listFiles(f -> f.isDirectory() && f.getName().endsWith(" (" + profileId + ")"));
		if ((dirs != null) && (dirs.length > 0))
			return dirs[0];
		String safe = StringUtils.defaultIfBlank(StringUtils.strip(StringUtils.defaultString(title).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_"), " ."), "profile");
		// names Windows keeps for devices (CON, AUX.txt, ...) cannot be folders there
		if (safe.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(\\..*)?"))
			safe = "_" + safe;
		return new File(logsDir(), StringUtils.strip(StringUtils.left(safe, 60), " .") + " (" + profileId + ")");
	}

	/** the newest log of a profile, or null */
	public static File latest(int profileId) {
		File dir = folderOf(profileId, null);
		File[] logs = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".log"));
		if ((logs == null) || (logs.length == 0))
			return null;
		return Arrays.stream(logs).max(Comparator.comparing(File::getName)).orElse(null); // the names are times
	}

	/** the text of a log, at most the last 'max' characters (a long session can write much) */
	public static String read(File log, int max) throws IOException {
		String text = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
		return text.length() > max ? "...\n" + text.substring(text.length() - max): text;
	}

	/** a new log for this start, or null if the profile does not save its DOSBox output or the file cannot be made */
	public static DosboxLog open(int profileId, String title, String commandLine) {
		if (!isOn(profileId))
			return null;
		try {
			File dir = folderOf(profileId, title);
			Files.createDirectories(dir.toPath());
			LocalDateTime now = LocalDateTime.now();
			File file = new File(dir, now.format(FILE_TIME) + ".log");
			BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8);
			DosboxLog log = new DosboxLog(writer);
			log.line("# " + title);
			log.line("# " + commandLine);
			log.line("# started " + now.format(LINE_TIME));
			removeOld(dir);
			return log;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private static void removeOld(File dir) {
		File[] logs = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".log"));
		if ((logs == null) || (logs.length <= KEEP))
			return;
		Arrays.sort(logs, Comparator.comparing(File::getName).reversed()); // the names are times: newest first
		for (int i = KEEP; i < logs.length; i++)
			logs[i].delete();
	}

	public synchronized void line(String line) {
		try {
			writer_.write(line);
			writer_.newLine();
		} catch (IOException e) {
			// a full disk must not stop the game
		}
	}

	public synchronized void close(int exitValue) {
		line("# ended " + LocalDateTime.now().format(LINE_TIME) + ", exit code " + exitValue);
		try {
			writer_.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
