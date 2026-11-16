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
	/** the name of a log: the time of the start; only files of such a name in a folder of a profile are logs of DBGL */
	public static final java.util.regex.Pattern FILE_NAME = java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.log");
	private static final java.util.regex.Pattern PROFILE_FOLDER = java.util.regex.Pattern.compile(".* \\(\\d+\\)");

	/**
	 * alpha 50: a file of the folder "logs", for the window that manages them
	 *
	 * @param profile the folder of the profile ("Title (id)"), "" for a diagnostic file of DBGL itself
	 */
	public record Entry(File file, String profile, long when, long size) {
	}

	/** how many logs of a profile are kept (the setting; {@link #KEEP} if it is not set) */
	public static int keep() {
		try {
			int n = SettingsService.getInstance().getIntValue(SECTION, "keep");
			return n > 0 ? n: KEEP;
		} catch (RuntimeException e) {
			return KEEP;
		}
	}

	public static void setKeep(int n) {
		SettingsService.getInstance().setIntValue(SECTION, "keep", Math.max(1, n));
	}

	/** the logs of all profiles and the diagnostic files of DBGL in a folder "logs", the newest first; nothing else that lies there */
	public static java.util.List<Entry> list(File logsDir) {
		java.util.List<Entry> result = new java.util.ArrayList<>();
		File[] top = logsDir.listFiles();
		if (top == null)
			return result;
		for (File f: top) {
			if (f.isFile() && OldFiles.isDiagnostic(f.getName())) {
				result.add(new Entry(f, "", f.lastModified(), f.length()));
			} else if (f.isDirectory() && PROFILE_FOLDER.matcher(f.getName()).matches()) {
				File[] logs = f.listFiles(l -> l.isFile() && FILE_NAME.matcher(l.getName()).matches());
				if (logs != null)
					for (File l: logs)
						result.add(new Entry(l, f.getName(), l.lastModified(), l.length()));
			}
		}
		result.sort(Comparator.comparingLong(Entry::when).reversed().thenComparing(e -> e.file().getPath()));
		return result;
	}

	/**
	 * Deletes a file of the folder "logs" if it is one DBGL wrote there (by its name and where it lies); the folder of a profile
	 * goes with its last log.
	 *
	 * @return false if it is not such a file (it stays)
	 */
	public static boolean delete(File logsDir, File file) throws IOException {
		File parent = file.getParentFile();
		boolean diagnostic = logsDir.equals(parent) && OldFiles.isDiagnostic(file.getName());
		boolean log = (parent != null) && logsDir.equals(parent.getParentFile()) && PROFILE_FOLDER.matcher(parent.getName()).matches() && FILE_NAME.matcher(file.getName()).matches();
		if (!diagnostic && !log)
			return false;
		Files.deleteIfExists(file.toPath());
		if (log) {
			String[] left = parent.list();
			if ((left != null) && (left.length == 0))
				Files.deleteIfExists(parent.toPath());
		}
		return true;
	}

	/** in every folder of a profile only the newest logs stay, as many as are kept: @return how many were deleted */
	public static int removeOld(File logsDir, int keep) {
		int deleted = 0;
		File[] dirs = logsDir.listFiles(f -> f.isDirectory() && PROFILE_FOLDER.matcher(f.getName()).matches());
		if (dirs == null)
			return 0;
		for (File dir: dirs) {
			File[] logs = dir.listFiles(f -> f.isFile() && FILE_NAME.matcher(f.getName()).matches());
			if ((logs == null) || (logs.length <= keep))
				continue;
			Arrays.sort(logs, Comparator.comparing(File::getName).reversed());
			for (int i = keep; i < logs.length; i++)
				if (logs[i].delete())
					deleted++;
		}
		return deleted;
	}
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
		int keep = keep();
		if ((logs == null) || (logs.length <= keep))
			return;
		Arrays.sort(logs, Comparator.comparing(File::getName).reversed()); // the names are times: newest first
		for (int i = keep; i < logs.length; i++)
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
