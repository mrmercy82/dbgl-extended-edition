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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.constants.Constants;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.util.ProfileFindings.Finding;


/**
 * Alpha 53: the results of "Check profiles" with the configuration files of the profiles, as one text to send to the
 * developer of DBGL, so that the checks can be improved with real collections. Left out: notes, links, own fields, play
 * times. Replaced before the text is shown: the folders of DBGL, of the games (dosroot) and of the user, the user name, the
 * name of the computer and e-mail addresses. The user sees the whole text before saving it.
 */
public final class CheckExport {

	private static final int MAX_CONF = 64 * 1024;
	private static final Pattern USER_DIR = Pattern.compile("(?i)(?:[A-Z]:[\\\\/](?:Users|Documents and Settings)|/home|/Users)[\\\\/](?!<)[^\\\\/\"<>|\\r\\n]+");
	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private CheckExport() {
	}

	/** the text, already anonymous */
	public static String text(List<Profile> profiles, ProfileFindings.Result result, List<DosboxVersion> versions) {
		StringBuilder sb = new StringBuilder();
		sb.append("# DBGL check export (anonymous)\n");
		sb.append("# ").append(Constants.PROGRAM_NAME_SHORT).append(' ').append(Constants.PROGRAM_VERSION).append(", ").append(System.getProperty("os.name")).append(", Java ")
				.append(System.getProperty("java.version")).append('\n');
		sb.append("# profiles: ").append(profiles.size()).append("\n\n");
		sb.append("== DOSBox versions\n");
		for (DosboxVersion v: versions)
			sb.append("- ").append(v.getTitle()).append(" | ").append(v.getFamily()).append(' ').append(v.getVersion()).append(" | ").append(v.getConfigurationCanonicalFile()).append('\n');
		if (result != null)
			for (Finding f: result.versions())
				sb.append("  ! ").append(f.kind()).append(" | ").append(oneLine(f.text())).append('\n');
		List<Profile> sorted = new ArrayList<>(profiles);
		sorted.sort(Comparator.comparingInt(Profile::getId));
		for (Profile p: sorted) {
			sb.append("\n== ").append(p.getId()).append(" | ").append(p.getTitle()).append(" | ").append(p.getDosboxVersion() == null ? "-": p.getDosboxVersion().getTitle()).append('\n');
			if (p.hasSetup())
				sb.append("setup: ").append(p.getSetupString()).append('\n');
			for (String alt: p.getAltExeStrings())
				if (StringUtils.isNotBlank(alt))
					sb.append("further program: ").append(alt).append('\n');
			List<Finding> list = result == null ? null: result.profiles().get(p);
			if (list != null)
				for (Finding f: list)
					sb.append("! ").append(f.kind()).append(" | ").append(oneLine(f.text())).append(f.solution() == null ? "": " | fix: " + f.solution()).append('\n');
			File conf = p.getConfigurationCanonicalFile();
			try {
				if ((conf != null) && conf.isFile()) {
					String text = new String(Files.readAllBytes(conf.toPath()), StandardCharsets.ISO_8859_1).replace("\r\n", "\n");
					if (text.length() > MAX_CONF)
						text = text.substring(0, MAX_CONF) + "\n...";
					sb.append("--- ").append(conf.getName()).append('\n').append(text.strip()).append("\n---\n");
				} else {
					sb.append("--- configuration file missing\n");
				}
			} catch (IOException e) {
				sb.append("--- configuration file not readable\n");
			}
		}
		return anonymous(sb.toString(), replacements());
	}

	private static String oneLine(String s) {
		return StringUtils.defaultString(s).replaceAll("\\R", " / ");
	}

	/** what is replaced by what: the folders of this computer and the names that tell who uses it */
	static Map<String, String> replacements() {
		Map<String, String> r = new LinkedHashMap<>();
		org.dbgl.service.FileLocationService fl = org.dbgl.service.FileLocationService.getInstance();
		r.put(path(fl.getDosroot()), "<DOSROOT>");
		r.put(path(fl.getDataDir()), "<DBGL>");
		r.put(System.getProperty("user.home"), "<HOME>");
		r.put(System.getProperty("user.name"), "<USER>");
		r.put(System.getenv("COMPUTERNAME"), "<PC>");
		r.put(System.getenv("HOSTNAME"), "<PC>");
		return r;
	}

	private static String path(File f) {
		try {
			return f == null ? null: f.getCanonicalPath();
		} catch (IOException e) {
			return f.getAbsolutePath();
		}
	}

	/**
	 * the text with e-mail addresses and these replaced, the longest first: paths with \ and / and in any case (Windows), names
	 * only as whole words
	 */
	static String anonymous(String text, Map<String, String> replacements) {
		List<Map.Entry<String, String>> list = new ArrayList<>();
		for (Map.Entry<String, String> e: replacements.entrySet())
			if ((e.getKey() != null) && (e.getKey().strip().length() >= 3))
				list.add(Map.entry(e.getKey().strip(), e.getValue()));
		list.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
		String result = EMAIL.matcher(text).replaceAll("<EMAIL>"); // first: a name in it would be replaced alone
		for (Map.Entry<String, String> e: list) {
			String key = e.getKey();
			boolean isPath = key.contains("/") || key.contains("\\");
			if (isPath) {
				// the separators either way, and the case as Windows ignores it
				StringBuilder rx = new StringBuilder("(?i)");
				for (char c: key.toCharArray())
					rx.append(((c == '/') || (c == '\\')) ? "[\\\\/]": Pattern.quote(String.valueOf(c)));
				result = result.replaceAll(rx.toString(), Matcher.quoteReplacement(e.getValue()));
			} else {
				result = result.replaceAll("(?i)(?<![\\p{L}\\p{N}])" + Pattern.quote(key) + "(?![\\p{L}\\p{N}])", Matcher.quoteReplacement(e.getValue()));
			}
		}
		// the folder of another user of this or another computer (an old path in a configuration): its name as well
		return USER_DIR.matcher(result).replaceAll("<HOME>");
	}

	/** the name suggested for the file */
	public static String fileName() {
		return "dbgl-check-export-" + java.time.LocalDate.now().toString().toLowerCase(Locale.ROOT) + ".txt";
	}
}
