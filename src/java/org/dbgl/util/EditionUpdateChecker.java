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

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dbgl.constants.Constants;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * Asks GitHub whether a newer version of the Extended Edition itself is out. It only tells; downloading is left to the user.
 * The releases are read as a list, because GitHub's "latest release" leaves out pre-releases (alpha, beta), and the
 * Extended Edition is published as pre-releases for now.
 */
public final class EditionUpdateChecker {

	/** a release of the Extended Edition: the version from its tag (e.g. "0.101 alpha 31"), its page, and whether it is a pre-release */
	public record Release(String version, String url, boolean prerelease) {
	}

	/** what a check found: the newest release that is newer than this version, or none; or an error */
	public record Result(Release newer, String error) {
	}

	public static final String SECTION = "edition", CHECK = "checkforupdates", PRERELEASES = "prereleases", CHECKED = "updatechecked", SHOWN = "updatenoticeshown";

	private static final String API = "https://api.github.com/repos/mrmercy82/dbgl-extended-edition/releases?per_page=50";

	/** e.g. "v0.101-alpha30", "0.101alpha30", "0.101 alpha 30", "0.101-beta2", "0.101-rc1", "0.101", "v0.102.1" */
	private static final Pattern VERSION_PATRN = Pattern.compile("^v?(\\d+(?:\\.\\d+)*)(?:(alpha|beta|rc|a|b)(\\d*))?$");

	private EditionUpdateChecker() {
	}

	/** @return the parts to compare: the numbers, then the stage (alpha 0, beta 1, rc 2, final 3) and its number; null if not a version */
	static long[] key(String version) {
		if (version == null)
			return null;
		// a test build ("0.101alpha42-test") counts as that version, so the update check does not offer the previous one
		Matcher m = VERSION_PATRN.matcher(version.trim().toLowerCase().replaceAll("[\\s_-]", "").replaceAll("(test|dev)$", ""));
		if (!m.matches())
			return null;
		String[] numbers = m.group(1).split("\\.");
		long[] key = new long[numbers.length + 2];
		for (int i = 0; i < numbers.length; i++)
			key[i] = Long.parseLong(numbers[i]);
		String stage = m.group(2);
		key[numbers.length] = stage == null ? 3: switch (stage) {
			case "alpha", "a" -> 0;
			case "beta", "b" -> 1;
			default -> 2;
		};
		key[numbers.length + 1] = (m.group(3) == null) || m.group(3).isEmpty() ? 0: Long.parseLong(m.group(3));
		return key;
	}

	/** @return negative, 0 or positive like a comparator: 0.101 alpha 30 < 0.101 alpha 31 < 0.101 beta 1 < 0.101 rc 1 < 0.101 < 0.101.1 < 0.102 alpha 1 */
	public static int compare(String a, String b) {
		long[] ka = key(a), kb = key(b);
		if ((ka == null) || (kb == null))
			return ka == kb ? 0: (ka == null ? -1: 1);
		int numbersA = ka.length - 2, numbersB = kb.length - 2;
		for (int i = 0; i < Math.max(numbersA, numbersB); i++) {
			long x = i < numbersA ? ka[i]: 0, y = i < numbersB ? kb[i]: 0;
			if (x != y)
				return Long.compare(x, y);
		}
		if (ka[numbersA] != kb[numbersB])
			return Long.compare(ka[numbersA], kb[numbersB]);
		return Long.compare(ka[numbersA + 1], kb[numbersB + 1]);
	}

	/** "v0.101-alpha30" -> "0.101 alpha 30" */
	public static String readable(String version) {
		return version.replaceFirst("^v", "").replaceAll("[-_]", " ").replaceAll("(?i)(alpha|beta|rc)(\\d)", "$1 $2").replaceAll("(\\d)(?i)(alpha|beta|rc)", "$1 $2").trim();
	}

	/** @return the newest release in a GitHub release list (JSON) that is newer than the version given, or null */
	public static Release newest(String json, String current, boolean prereleases) {
		Release best = null;
		JSONArray list = new JSONArray(json);
		for (int i = 0; i < list.length(); i++) {
			JSONObject r = list.getJSONObject(i);
			boolean pre = r.optBoolean("prerelease", false);
			String tag = r.optString("tag_name", "");
			if (r.optBoolean("draft", false) || (pre && !prereleases) || (key(tag) == null))
				continue;
			if ((compare(tag, current) > 0) && ((best == null) || (compare(tag, best.version()) > 0)))
				best = new Release(tag, r.optString("html_url", Constants.EDITION_URL + "/releases"), pre);
		}
		return best == null ? null: new Release(readable(best.version()), best.url(), best.prerelease());
	}

	/** ask GitHub; takes a few seconds at most, so not on the user interface thread */
	public static Result check(boolean prereleases) {
		try {
			HttpClient client = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
			HttpRequest request = HttpRequest.newBuilder(URI.create(API)).timeout(Duration.ofSeconds(15)).header("Accept", "application/vnd.github+json").header("User-Agent", "DBGL")
					.GET().build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200)
				return new Result(null, "HTTP " + response.statusCode());
			return new Result(newest(response.body(), Constants.PROGRAM_VERSION, prereleases), null);
		} catch (Exception e) {
			return new Result(null, String.valueOf(e.getMessage() == null ? e: e.getMessage()));
		}
	}

}
