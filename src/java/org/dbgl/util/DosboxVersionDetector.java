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
		if ((executable == null) || !executable.isFile())
			return null;
		if (executable.getName().toLowerCase().contains("dosbox-x")) {
			// DOSBox-X on Windows prints its version into a console of its own, not to us; read it from the executable instead
			Result scanned = scanDosboxXExecutable(executable);
			if (scanned != null)
				return scanned;
		}
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
		String text = readAsText(executable);
		if ((text == null) || !text.contains("DOSBox-X"))
			return null;
		Matcher m = X_DATE_PATRN.matcher(text);
		String latest = null;
		while (m.find())
			if ((latest == null) || (m.group(1).compareTo(latest) > 0))
				latest = m.group(1);
		return latest == null ? null: new Result(SettingsService.DOSBOX_X_DOSBOX_FAM.title_, latest);
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
	private static final Pattern SHADER_LINE_PATRN = Pattern.compile("^\\s*[|`]-\\s+(\\S+)\\s*$");

	/**
	 * The shaders a DOSBox Staging executable finds in its resource folders, by running it with --list-shaders (0.83+) or
	 * --list-glshaders (0.81/0.82). Every executable file is asked only once.
	 */
	public static List<String> listShadersCached(File executable, File cwd, boolean staging083) {
		if ((executable == null) || !executable.isFile())
			return List.of();
		String key = executable.getAbsolutePath() + '|' + executable.length() + '|' + executable.lastModified() + '|' + staging083;
		return SHADER_CACHE.computeIfAbsent(key, k -> {
			String output = run(executable, cwd, staging083 ? "--list-shaders": "--list-glshaders");
			List<String> result = new java.util.ArrayList<>();
			if (output != null)
				for (String line: output.split("\\R")) {
					Matcher m = SHADER_LINE_PATRN.matcher(line);
					if (m.matches())
						result.add(m.group(1));
				}
			return result;
		});
	}

	private static String run(File executable, File cwd, String argument) {
		try {
			ProcessBuilder pb = new ProcessBuilder(executable.getPath(), argument);
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
					// use what we have
				}
			});
			reader.setDaemon(true);
			reader.start();
			if (!proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS))
				proc.destroyForcibly();
			reader.join(2000);
			return output.toString();
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	/** like detect, but runs every executable file (path, size and date) only once */
	public static Result detectCached(File executable, File cwd) {
		if ((executable == null) || !executable.isFile())
			return null;
		String key = executable.getAbsolutePath() + '|' + executable.length() + '|' + executable.lastModified();
		return CACHE.computeIfAbsent(key, k -> Optional.ofNullable(detect(executable, cwd))).orElse(null);
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
