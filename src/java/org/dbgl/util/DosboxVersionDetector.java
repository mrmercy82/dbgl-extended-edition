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
	private static final Pattern OFFICIAL_PATRN = Pattern.compile("^DOSBox\\s+version\\s+(0\\.\\d+(?:-\\d+)?)\\b", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

	private static final int TIMEOUT_SECONDS = 10;

	private DosboxVersionDetector() {
	}

	/**
	 * @return the detected family and version, or null if the executable didn't report a version DBGL recognizes
	 */
	public static Result detect(File executable, File cwd) {
		if ((executable == null) || !executable.isFile())
			return null;
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

	public static Result parse(String output) {
		if (output == null)
			return null;
		Matcher m = STAGING_PATRN.matcher(output);
		if (m.find())
			return new Result(SettingsService.STAGING_DOSBOX_FAM.title_, m.group(1));
		m = X_PATRN.matcher(output);
		if (m.find())
			return new Result(SettingsService.DOSBOX_X_DOSBOX_FAM.title_, m.group(1));
		m = OFFICIAL_PATRN.matcher(output);
		if (m.find())
			return new Result(SettingsService.OFFICIAL_DOSBOX_FAM.title_, m.group(1));
		return null;
	}
}
