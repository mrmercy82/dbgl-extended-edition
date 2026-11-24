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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.service.SettingsService;


/**
 * Alpha 53: what the output of DOSBox told at the last start of a profile, read while it runs: things that cannot be seen in
 * the configuration (the network card without WinPcap, a folder DOSBox may not create, a value DOSBox does not take). Kept
 * per profile in the settings until the next start, and shown by "Check profiles" as notes. Only messages whose wording is
 * known from the output of DOSBox are recognised.
 */
public final class RunIssues {

	/** one thing the output told: the key of its note and what it names */
	public record Issue(String key, List<String> args) {
	}

	static final String SECTION = "runissues";
	private static final int MAX_ISSUES = 10;

	/** DOSBox-X: "WinPcap has to be installed for the NE2000 to work." */
	private static final Pattern PCAP = Pattern.compile("(?i)(WinPcap|Npcap|libpcap) has to be installed for the NE2000");
	/** DOSBox-X: "SETUP: mkdir_recursive failed: Permission denied" */
	private static final Pattern MKDIR = Pattern.compile("(?i)mkdir_recursive failed: (Permission denied|Access is denied)");
	/** DOSBox-X: "\"\" is not a valid value for variable: enable supermegazeux tweakmode." */
	/**
	 * DOSBox Staging: "MT32: Failed to find ROMs for model auto in:", then the folders ("MT32:  |- D:\\...\\mt32-roms", "MT32:
	 * `- C:\\mt32-rom-data\\"); DOSBox-X: "MT32: failed to locate ROMs."
	 */
	private static final Pattern MT32 = Pattern.compile("(?i)MT32: (Failed to find ROMs|failed to locate ROMs)");
	private static final Pattern MT32_DIR = Pattern.compile("MT32:\\s+(?:\\|-|`-)\\s*(.+)$");
	private static final Pattern BAD_VALUE = Pattern.compile("\"([^\"]*)\" is not a valid value for variable: ([^.]+)\\.?");

	private RunIssues() {
	}

	/** the issues in these lines of output, each once */
	public static List<Issue> scan(List<String> lines) {
		Set<Issue> found = new LinkedHashSet<>();
		List<String> mt32Dirs = null; // the folders DOSBox Staging looked in, read after its line
		for (String raw: lines) {
			String line = StringUtils.defaultString(raw);
			if (mt32Dirs != null) {
				Matcher d = MT32_DIR.matcher(line);
				if (d.find()) {
					mt32Dirs.add(d.group(1).strip());
					continue;
				}
				found.add(new Issue("runmt32", List.of(String.join(", ", mt32Dirs))));
				mt32Dirs = null;
			}
			if (MT32.matcher(line).find())
				mt32Dirs = new ArrayList<>();
			else if (PCAP.matcher(line).find())
				found.add(new Issue("runpcap", List.of()));
			else if (MKDIR.matcher(line).find())
				found.add(new Issue("runmkdir", List.of()));
			else {
				Matcher m = BAD_VALUE.matcher(line);
				if (m.find())
					found.add(new Issue("runbadvalue", List.of(m.group(1), m.group(2).strip())));
			}
			if (found.size() >= MAX_ISSUES)
				break;
		}
		if (mt32Dirs != null)
			found.add(new Issue("runmt32", List.of(String.join(", ", mt32Dirs))));
		return new ArrayList<>(found);
	}

	/**
	 * DOSBox-X started with "-savedir" (the folder for the save states of the profile, made by DBGL beforehand) writes
	 * "mkdir_recursive failed: Permission denied" although the folder is there (TEST21: only with -savedir, not without): then
	 * no note of it
	 */
	public static List<Issue> withoutSaveDirNoise(List<Issue> issues, List<String> commandLine) {
		int at = commandLine.indexOf("-savedir");
		if ((at < 0) || (at + 1 >= commandLine.size()) || !new java.io.File(commandLine.get(at + 1)).isDirectory())
			return issues;
		return issues.stream().filter(i -> !i.key().equals("runmkdir")).toList();
	}

	/** keeps what the last start of a profile told (nothing: its entry goes) */
	public static void save(int profileId, List<Issue> issues) {
		if (profileId < 0)
			return;
		SettingsService.getInstance().setValue(SECTION, String.valueOf(profileId), encode(issues));
	}

	/** what the last start of a profile told */
	public static List<Issue> of(int profileId) {
		if (profileId < 0)
			return List.of();
		try {
			return decode(SettingsService.getInstance().getValue(SECTION, String.valueOf(profileId)));
		} catch (RuntimeException e) {
			return List.of();
		}
	}

	static String encode(List<Issue> issues) {
		List<String> parts = new ArrayList<>();
		for (Issue i: issues) {
			StringBuilder sb = new StringBuilder(i.key());
			for (String a: i.args())
				sb.append(',').append(URLEncoder.encode(a, StandardCharsets.UTF_8));
			parts.add(sb.toString());
		}
		return String.join(" ", parts);
	}

	static List<Issue> decode(String value) {
		List<Issue> result = new ArrayList<>();
		if (StringUtils.isBlank(value))
			return result;
		for (String part: value.strip().split(" +")) {
			String[] p = part.split(",", -1);
			if (!p[0].matches("run[a-z0-9]+"))
				continue;
			List<String> args = new ArrayList<>();
			for (int k = 1; k < p.length; k++)
				args.add(URLDecoder.decode(p[k], StandardCharsets.UTF_8));
			result.add(new Issue(p[0], args));
		}
		return result;
	}
}
