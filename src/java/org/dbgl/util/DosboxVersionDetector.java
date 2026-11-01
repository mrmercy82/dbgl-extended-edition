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
package org.dbgl.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.dbgl.service.SettingsService;


/**
 * Determines family and version of a DOSBox executable by running it with -version.
 */
public class DosboxVersionDetector {

	public static class Result {
		public final String family_;
		public final String version_;

		Result(String family, String version) {
			family_ = family;
			version_ = version;
		}

		@Override
		public String toString() {
			return family_ + " " + version_;
		}
	}

	private static final Pattern STAGING_PATRN = Pattern.compile("dosbox[- ]staging,?\\s+version\\s+(\\d+\\.\\d+\\.\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern X_PATRN = Pattern.compile("DOSBox-X\\s+version\\s+(\\d{4}\\.\\d{2}\\.\\d{2})", Pattern.CASE_INSENSITIVE);
	// ECE, Daum and SVN builds don't report a precise version in a uniform way ("DOSBox version SVN", "SVN-Daum", "ECE r4482"),
	// so these are recognized by name and mapped to the version DBGL supports for that family
	private static final Pattern ECE_PATRN = Pattern.compile("^DOSBox\\b.*\\bECE\\b", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
	private static final Pattern DAUM_PATRN = Pattern.compile("^DOSBox\\b.*\\bDaum\\b", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
	private static final Pattern SVN_PATRN = Pattern.compile("^DOSBox\\s+version\\s+(?:\\S*-)?SVN\\b", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
	private static final Pattern REVISION_PATRN = Pattern.compile("\\b(r\\d{4,})\\b");
	private static final Pattern OFFICIAL_PATRN = Pattern.compile("^DOSBox\\s+version\\s+(0\\.\\d+(?:-\\d+)?)\\b", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

	private static final int TIMEOUT_SECONDS = 10;

	private static final Map<String, Optional<Result>> CACHE = new ConcurrentHashMap<>();

	private DosboxVersionDetector() {
	}

	/**
	 * @return the detected family and version, or null if the executable didn't report a version DBGL recognizes
	 */
	public static Result detect(File executable, File cwd) {
		return detect(executable, cwd, true);
	}

	/**
	 * @param mayRun if false, the version is only read from the executable file. Starting DOSBox opens a window on Windows
	 *            (a console, or DOSBox-X its own), which must not happen in the background or when a dialog opens.
	 */
	public static Result detect(File executable, File cwd, boolean mayRun) {
		if ((executable == null) || !executable.isFile())
			return null;
		// DOSBox-X and DOSBox Staging carry their version as text in the executable; reading it opens no window
		// Staging first: its executable also contains the text "DOSBox-X" (a CD-ROM name), DOSBox-X never "DOSBox Staging"
		String text = readAsText(executable); // read once, the executable of DOSBox Staging has about 30 MB
		Result scanned = scanStagingText(text);
		if (scanned == null)
			scanned = scanDosboxXText(text);
		if (scanned == null)
			scanned = scanClassicText(text);
		if ((scanned != null) || !mayRun)
			return scanned;
		try {
			ProcessBuilder pb = new ProcessBuilder(executable.getPath(), "-version");
			if ((cwd != null) && cwd.isDirectory())
				pb.directory(cwd);
			pb.redirectErrorStream(true);
			Process proc = pb.start();
			proc.getOutputStream().close();
			StringBuilder output = new StringBuilder();
			Thread reader = new Thread(() -> {
				try (InputStream is = proc.getInputStream()) {
					output.append(IOUtils.toString(is, Charset.defaultCharset()));
				} catch (IOException e) {
					// ignore, use what we have
				}
			});
			reader.setDaemon(true);
			reader.start();
			if (!proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS))
				proc.destroyForcibly();
			reader.join(2000);
			return parse(output.toString());
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private static final Pattern STAGING_MODEM_PATRN = Pattern.compile("Modem compiled for DOSBox version (\\d+\\.\\d+\\.\\d+)");
	private static final Pattern STAGING_HASH_PATRN = Pattern.compile("\\b(0\\.\\d{2}\\.\\d+) \\([0-9a-f]{5,12}\\)");

	/** The version of a DOSBox Staging executable, without running it: e.g. "0.83.0 (7b400)" is stored in the program as text. */
	public static Result scanStagingExecutable(File executable) {
		return scanStagingText(readAsText(executable));
	}

	static Result scanStagingText(String text) {
		if ((text == null) || !(text.contains("DOSBox Staging") || text.contains("dosbox-staging")))
			return null;
		Matcher m = STAGING_HASH_PATRN.matcher(text);
		if (m.find())
			return new Result(SettingsService.STAGING_DOSBOX_FAM.title_, m.group(1));
		m = STAGING_MODEM_PATRN.matcher(text);
		return m.find() ? new Result(SettingsService.STAGING_DOSBOX_FAM.title_, m.group(1)): null;
	}

	private static final Pattern X_DATE_PATRN = Pattern.compile("\\b(20\\d{2}\\.[01]\\d\\.[0-3]\\d)\\b");

	private static String readAsText(File file) {
		try {
			return new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.ISO_8859_1);
		} catch (IOException | OutOfMemoryError e) {
			return null;
		}
	}

	/**
	 * The version of a DOSBox-X executable, without running it: the version (e.g. "2026.10.01") is stored in the program as
	 * text, and it is the latest date of this form in there.
	 */
	public static Result scanDosboxXExecutable(File executable) {
		return scanDosboxXText(readAsText(executable));
	}

	static Result scanDosboxXText(String text) {
		if ((text == null) || !text.contains("DOSBox-X"))
			return null;
		Matcher m = X_DATE_PATRN.matcher(text);
		String latest = null;
		while (m.find())
			if ((latest == null) || (m.group(1).compareTo(latest) > 0))
				latest = m.group(1);
		return latest == null ? null: new Result(SettingsService.DOSBOX_X_DOSBOX_FAM.title_, latest);
	}

	private static final Pattern MAPPER_PATRN = Pattern.compile("\0mapper-([A-Za-z0-9.\\-]{2,24})\\.map\0");
	private static final Pattern ECE_REVISION_PATRN = Pattern.compile("ECE[ ,_-]*(r\\d{4,})", Pattern.CASE_INSENSITIVE);

	/**
	 * The family and version of DOSBox 0.74, SVN, ECE and Daum builds, without running them: the name of the default mapper
	 * file is "mapper-" followed by the version, and stored in the program as text (e.g. "mapper-0.74-3.map",
	 * "mapper-SVN.map", "mapper-ECE.map", "mapper-SVN-Daum.map").
	 */
	public static Result scanClassicExecutable(File executable) {
		return scanClassicText(readAsText(executable));
	}

	static Result scanClassicText(String text) {
		if ((text == null) || !text.contains("DOSBox"))
			return null;
		Matcher m = MAPPER_PATRN.matcher(text);
		while (m.find()) {
			String version = m.group(1);
			if (version.equalsIgnoreCase("ECE")) {
				Matcher r = ECE_REVISION_PATRN.matcher(text);
				String rev = r.find() ? r.group(1).toLowerCase(): null;
				List<String> versions = SettingsService.ECE_DOSBOX_FAM.versions_;
				return new Result(SettingsService.ECE_DOSBOX_FAM.title_, versions.contains(rev) ? rev: versions.get(versions.size() - 1));
			}
			if (version.toLowerCase().contains("daum")) {
				List<String> versions = SettingsService.DAUM_DOSBOX_FAM.versions_;
				return new Result(SettingsService.DAUM_DOSBOX_FAM.title_, versions.get(versions.size() - 1));
			}
			if (version.equalsIgnoreCase("SVN")) {
				List<String> versions = SettingsService.SVN_DOSBOX_FAM.versions_;
				return new Result(SettingsService.SVN_DOSBOX_FAM.title_, versions.get(versions.size() - 1));
			}
			if (version.matches("0\\.\\d+(-\\d+)?"))
				return new Result(SettingsService.OFFICIAL_DOSBOX_FAM.title_, version);
		}
		return null;
	}

	/** special DOSBox-X builds, as far as the executable tells */
	public enum XBuild {
		OSFREE, LOWEND
	}

	public static java.util.Set<XBuild> dosboxXBuild(File executable) {
		java.util.Set<XBuild> result = java.util.EnumSet.noneOf(XBuild.class);
		String text = executable == null ? null: readAsText(executable);
		if (text == null)
			return result;
		if (text.contains("OS-Free build"))
			result.add(XBuild.OSFREE);
		if (text.contains("Low-end") || text.contains("low-end") || text.contains("LOWEND"))
			result.add(XBuild.LOWEND);
		return result;
	}

	private static final Map<String, List<String>> SHADER_CACHE = new ConcurrentHashMap<>();

	/**
	 * The shaders a DOSBox Staging executable finds in its resource folders, by running it with --list-shaders (0.83+) or
	 * --list-glshaders (0.81/0.82). Every executable file is asked only once.
	 */
	public static List<String> listShadersCached(File executable, File cwd, boolean staging083) {
		if ((executable == null) || !executable.isFile())
			return List.of();
		String key = executable.getAbsolutePath() + '|' + executable.length() + '|' + executable.lastModified() + '|' + staging083;
		return SHADER_CACHE.computeIfAbsent(key, k -> listShaders(executable, cwd, staging083));
	}

	/**
	 * The shaders DOSBox Staging finds, by looking into the same folders it does (see get_resource_parent_paths in Staging),
	 * instead of running it with --list-shaders: that opened a window on Windows and took a moment.
	 */
	static List<String> listShaders(File executable, File cwd, boolean staging083) {
		String sub = staging083 ? "shaders": "glshaders";
		java.util.Set<File> parents = new java.util.LinkedHashSet<>();
		File exeDir = executable.getAbsoluteFile().getParentFile();
		if (cwd != null) {
			parents.add(cwd);
			parents.add(new File(cwd, "resources"));
		}
		if (exeDir != null) {
			parents.add(new File(exeDir, "resources"));
			parents.add(new File(exeDir.getParentFile(), "resources"));
			parents.add(new File(exeDir.getParentFile(), "Resources")); // macOS app bundle
			parents.add(new File(exeDir.getParentFile(), "share/dosbox-staging"));
		}
		String home = System.getProperty("user.home");
		if (SystemUtils.IS_WINDOWS) {
			String local = System.getenv("LOCALAPPDATA");
			if (local != null)
				parents.add(new File(local, "DOSBox"));
		} else if (SystemUtils.IS_OSX) {
			parents.add(new File(home, "Library/Preferences/DOSBox"));
		} else {
			String xdgData = System.getenv("XDG_DATA_HOME");
			parents.add(new File(xdgData != null ? xdgData: home + "/.local/share", "dosbox-staging"));
			parents.add(new File("/usr/share/dosbox-staging"));
			parents.add(new File("/usr/local/share/dosbox-staging"));
			String xdgConfig = System.getenv("XDG_CONFIG_HOME");
			parents.add(new File(xdgConfig != null ? xdgConfig: home + "/.config", "dosbox"));
		}
		java.util.Set<String> result = new java.util.TreeSet<>();
		for (File parent: parents) {
			File dir = new File(parent, sub);
			if (!dir.isDirectory())
				continue;
			try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(dir.toPath(), 4)) {
				files.filter(p -> java.nio.file.Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".glsl")).forEach(p -> {
					String name = dir.toPath().relativize(p).toString().replace(File.separatorChar, '/');
					name = name.substring(0, name.length() - 5);
					if (!name.startsWith("_"))
						result.add(name);
				});
			} catch (IOException e) {
				// skip this folder
			}
		}
		return new java.util.ArrayList<>(result);
	}

	private static final Map<String, List<String>> X_SHADER_CACHE = new ConcurrentHashMap<>();

	/**
	 * The GLSL shader files DOSBox-X finds in its 'glshaders' folders: next to the program, in the working directory and in its
	 * configuration folder. DOSBox-X takes them as file name in that folder, e.g. "crt/crt-easymode.glsl". The folders are
	 * looked into once per executable and DBGL session.
	 */
	public static List<String> listDosboxXShadersCached(File executable, File cwd) {
		if ((executable == null) || !executable.isFile())
			return List.of();
		String key = executable.getAbsolutePath() + '|' + (cwd == null ? "": cwd.getAbsolutePath());
		return X_SHADER_CACHE.computeIfAbsent(key, k -> listDosboxXShaders(executable, cwd));
	}

	static List<String> listDosboxXShaders(File executable, File cwd) {
		java.util.Set<File> dirs = new java.util.LinkedHashSet<>();
		File exeDir = executable.getAbsoluteFile().getParentFile();
		if (cwd != null)
			dirs.add(new File(cwd, "glshaders"));
		if (exeDir != null) {
			dirs.add(new File(exeDir, "glshaders"));
			dirs.add(new File(exeDir.getParentFile(), "share/dosbox-x/glshaders"));
			dirs.add(new File(exeDir.getParentFile(), "Resources/glshaders")); // macOS app bundle
		}
		String home = System.getProperty("user.home");
		if (SystemUtils.IS_WINDOWS) {
			String local = System.getenv("LOCALAPPDATA");
			if (local != null)
				dirs.add(new File(local, "DOSBox-X/glshaders"));
		} else if (SystemUtils.IS_OSX) {
			dirs.add(new File(home, "Library/Preferences/DOSBox-X/glshaders"));
		} else {
			String xdgConfig = System.getenv("XDG_CONFIG_HOME");
			dirs.add(new File(xdgConfig != null ? xdgConfig: home + "/.config", "dosbox-x/glshaders"));
		}
		java.util.Set<String> result = new java.util.TreeSet<>();
		for (File dir: dirs) {
			if (!dir.isDirectory())
				continue;
			try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(dir.toPath(), 4)) {
				files.filter(p -> java.nio.file.Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".glsl"))
						.forEach(p -> result.add(dir.toPath().relativize(p).toString().replace(File.separatorChar, '/')));
			} catch (IOException e) {
				// skip this folder
			}
		}
		return new java.util.ArrayList<>(result);
	}

	/** like detect, but runs every executable file (path, size and date) only once */
	public static Result detectCached(File executable, File cwd) {
		if ((executable == null) || !executable.isFile())
			return null;
		String key = executable.getAbsolutePath() + '|' + executable.length() + '|' + executable.lastModified();
		return CACHE.computeIfAbsent(key, k -> Optional.ofNullable(detect(executable, cwd, false))).orElse(null);
	}

	/** @return true for DOSBox Staging version strings 0.83.0 and newer */
	public static boolean isStaging083OrNewer(String version) {
		Matcher m = Pattern.compile("^(\\d+)\\.(\\d+)").matcher(version == null ? "": version);
		if (!m.find())
			return false;
		int major = Integer.parseInt(m.group(1));
		int minor = Integer.parseInt(m.group(2));
		return (major > 0) || (minor >= 83);
	}

	public static Result parse(String output) {
		if (output == null)
			return null;
		Matcher m = STAGING_PATRN.matcher(output);
		if (m.find())
			return new Result(SettingsService.STAGING_DOSBOX_FAM.title_, m.group(1));
		m = X_PATRN.matcher(output);
		if (m.find())
			return new Result(SettingsService.DOSBOX_X_DOSBOX_FAM.title_, m.group(1));
		if (ECE_PATRN.matcher(output).find())
			return new Result(SettingsService.ECE_DOSBOX_FAM.title_, revisionOrDefault(output, SettingsService.ECE_DOSBOX_FAM.versions_));
		if (DAUM_PATRN.matcher(output).find())
			return new Result(SettingsService.DAUM_DOSBOX_FAM.title_, SettingsService.DAUM_DOSBOX_FAM.versions_.get(SettingsService.DAUM_DOSBOX_FAM.versions_.size() - 1));
		if (SVN_PATRN.matcher(output).find())
			return new Result(SettingsService.SVN_DOSBOX_FAM.title_, revisionOrDefault(output, SettingsService.SVN_DOSBOX_FAM.versions_));
		m = OFFICIAL_PATRN.matcher(output);
		if (m.find())
			return new Result(SettingsService.OFFICIAL_DOSBOX_FAM.title_, m.group(1));
		return null;
	}

	private static String revisionOrDefault(String output, java.util.List<String> versions) {
		Matcher m = REVISION_PATRN.matcher(output);
		if (m.find() && versions.contains(m.group(1).toLowerCase()))
			return m.group(1).toLowerCase();
		return versions.get(versions.size() - 1);
	}
}
