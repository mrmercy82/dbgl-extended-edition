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
	private static final Pattern BAD_VALUE = Pattern.compile("\"([^\"]*)\" is not a valid value for variable: ([^.]+)\\.?");

	private RunIssues() {
	}

	/** the issues in these lines of output, each once */
	public static List<Issue> scan(List<String> lines) {
		Set<Issue> found = new LinkedHashSet<>();
		for (String raw: lines) {
			String line = StringUtils.defaultString(raw);
			if (PCAP.matcher(line).find())
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
		return new ArrayList<>(found);
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
