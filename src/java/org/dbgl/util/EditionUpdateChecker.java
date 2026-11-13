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
	public record Release(String version, String url, boolean prerelease, String updateZip, String sums, String notes) {
		public Release(String version, String url, boolean prerelease) {
			this(version, url, prerelease, null, null, null);
		}

		public Release(String version, String url, boolean prerelease, String updateZip, String sums) {
			this(version, url, prerelease, updateZip, sums, null);
		}

		/** whether it can be installed with one click: it has an update zip and the SHA-256 sums */
		public boolean installable() {
			return (updateZip != null) && (sums != null);
		}
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
		// a test build ("0.101alpha42-test", since alpha 48 also "-test9") counts as that version, so the update check does not offer the previous one
		Matcher m = VERSION_PATRN.matcher(version.trim().toLowerCase().replaceAll("[\\s_-]", "").replaceAll("(test\\d*|dev)$", ""));
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
		return version.replaceFirst("^v", "").replaceAll("[-_]", " ").replaceAll("(?i)(alpha|beta|rc)(\\d)", "$1 $2").replaceAll("(\\d)(?i)(alpha|beta|rc)", "$1 $2")
				.replaceAll("(?i)\\btest(\\d+)$", "TEST $1").trim(); // alpha 48: a test build with its number ("0.101alpha48-test9": "0.101 alpha 48 TEST 9")
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
			if ((compare(tag, current) > 0) && ((best == null) || (compare(tag, best.version()) > 0))) {
				// the files of the release: the update zip (dbgl-0.101alphaNN-update.zip) and SHA256SUMS.txt
				String zip = null, sums = null;
				JSONArray assets = r.optJSONArray("assets");
				for (int a = 0; (assets != null) && (a < assets.length()); a++) {
					String name = assets.getJSONObject(a).optString("name", "");
					String url = assets.getJSONObject(a).optString("browser_download_url", null);
					if (name.matches("(?i)dbgl-.*-update\\.zip"))
						zip = url;
					else if (name.equalsIgnoreCase("SHA256SUMS.txt"))
						sums = url;
				}
				// alpha 48: the text of the release (its changelog), shown before updating if wanted
				best = new Release(tag, r.optString("html_url", Constants.EDITION_URL + "/releases"), pre, zip, sums, r.optString("body", ""));
			}
		}
		return best == null ? null: new Release(readable(best.version()), best.url(), best.prerelease(), best.updateZip(), best.sums(), best.notes());
	}

	/** ask GitHub; takes a few seconds at most, so not on the user interface thread */
	public static Result check(boolean prereleases) {
		try {
			HttpClient client = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
			HttpRequest request = HttpRequest.newBuilder(URI.create(API)).timeout(Duration.ofSeconds(15)).header("Accept", "application/vnd.github+json").header("User-Agent", "DBGL")
					.GET().build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200)
				return new Result(null, httpProblem(response.statusCode()));
			return new Result(newest(response.body(), Constants.PROGRAM_VERSION, prereleases), null);
		} catch (Exception e) {
			return new Result(null, describe(e));
		}
	}

	/** alpha 48: an answer of GitHub other than 200 in words; 403 and 429 are its limit of requests without an account (60 an hour) */
	public static String httpProblem(int status) {
		org.dbgl.service.ITextService text = org.dbgl.service.TextService.getInstance();
		if ((status == 403) || (status == 429))
			return text.get("dialog.main.editionupdate.problem.ratelimit");
		return text.get("dialog.main.editionupdate.problem.http", new Object[] {String.valueOf(status)});
	}

	/**
	 * alpha 48: why talking to GitHub failed, in words a user understands ("java.net.ConnectException" said nothing); the
	 * technical message only for what is not known.
	 */
	public static String describe(Throwable e) {
		org.dbgl.service.ITextService text = org.dbgl.service.TextService.getInstance();
		for (Throwable t = e; t != null; t = t.getCause() == t ? null: t.getCause()) {
			if (t instanceof EditionUpdater.ChecksumMismatch)
				return text.get("dialog.main.editionupdate.problem.checksum");
			if ((t instanceof java.net.http.HttpTimeoutException) || (t instanceof java.net.SocketTimeoutException))
				return text.get("dialog.main.editionupdate.problem.timeout");
			if (t instanceof javax.net.ssl.SSLException)
				return text.get("dialog.main.editionupdate.problem.certificate");
			if ((t instanceof java.net.ConnectException) || (t instanceof java.net.UnknownHostException) || (t instanceof java.net.NoRouteToHostException)
					|| (t instanceof java.nio.channels.UnresolvedAddressException))
				return text.get("dialog.main.editionupdate.problem.offline");
		}
		return String.valueOf(e.getMessage() == null ? e: e.getMessage());
	}

	private static final Pattern ALERT_PATRN = Pattern.compile("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\\]\\s*(.*)$", Pattern.CASE_INSENSITIVE);

	/**
	 * alpha 48: the text of a release on GitHub (Markdown) as plain text for the changelog window: headings, bold and code
	 * without their marks, links as "text (address)", GitHub's boxes ("> [!WARNING]") as "Warning:", HTML tags and entities
	 * resolved, long lines wrapped.
	 */
	public static String plainNotes(String markdown) {
		if (markdown == null)
			return "";
		org.dbgl.service.ITextService text = org.dbgl.service.TextService.getInstance();
		List<String> out = new ArrayList<>();
		boolean fence = false;
		for (String line: markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
			if (line.trim().startsWith("```")) {
				fence = !fence;
				continue;
			}
			if (fence) {
				out.add("    " + line);
				continue;
			}
			// HTML: <summary>x</summary> becomes the line x, other tags go
			line = line.replaceAll("(?i)</?(details|summary|p|div|br|sub|sup|b|i|em|strong|kbd)\\b[^>]*>", "");
			String t = line.trim();
			if (t.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
				out.add("");
				continue;
			}
			// quotes and GitHub's boxes
			while (t.startsWith(">"))
				t = t.substring(1).trim();
			boolean quoted = !t.equals(line.trim());
			Matcher alert = ALERT_PATRN.matcher(t);
			if (alert.matches()) {
				String kind = alert.group(1).toLowerCase(java.util.Locale.ROOT);
				t = (text.get("dialog.main.editionupdate.notes." + kind) + ": " + alert.group(2)).trim();
			}
			// headings: the text, an empty line before
			Matcher heading = Pattern.compile("^#{1,6}\\s+(.*?)\\s*#*$").matcher(t);
			boolean isHeading = heading.matches();
			if (isHeading) {
				t = heading.group(1);
				if (!out.isEmpty() && !out.get(out.size() - 1).isBlank())
					out.add("");
			}
			String indent = quoted || isHeading ? "": line.substring(0, line.length() - line.stripLeading().length()).replace("\t", "    ");
			t = t.replaceFirst("^[-*+]\\s+", "\u2022 ");
			t = inline(t);
			out.addAll(wrap(indent + t, 80)); // the changelog window shows about 80 characters
		}
		String result = String.join("\n", out).replaceAll("\n{3,}", "\n\n").strip();
		return result;
	}

	/** the marks within a line: images, links, bold, italic, code, entities */
	private static String inline(String s) {
		s = s.replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
		Matcher link = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)[^)]*\\)").matcher(s);
		StringBuilder sb = new StringBuilder();
		while (link.find()) {
			String label = link.group(1), url = link.group(2);
			link.appendReplacement(sb, Matcher.quoteReplacement(label.equals(url) ? url: label + " (" + url + ")"));
		}
		link.appendTail(sb);
		s = sb.toString().replaceAll("<(https?://[^>\\s]+)>", "$1");
		s = s.replaceAll("\\*\\*(.+?)\\*\\*", "$1").replaceAll("__(.+?)__", "$1");
		s = s.replaceAll("(?<![\\w*])\\*(?=\\S)(.+?)(?<=\\S)\\*(?![\\w*])", "$1").replaceAll("(?<![\\w_])_(?=\\S)(.+?)(?<=\\S)_(?![\\w_])", "$1");
		s = s.replaceAll("`([^`]*)`", "$1").replaceAll("~~(.+?)~~", "$1");
		s = s.replaceAll("\\\\([\\\\`*_{}\\[\\]()#+\\-.!<>])", "$1");
		return s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
	}

	/** a long line in lines of at most width characters, at spaces; the following lines indented like the text after a bullet */
	private static List<String> wrap(String line, int width) {
		List<String> lines = new ArrayList<>();
		if (line.length() <= width) {
			lines.add(line);
			return lines;
		}
		int textStart = line.length() - line.stripLeading().length();
		if (line.stripLeading().startsWith("\u2022 "))
			textStart += 2;
		String hanging = " ".repeat(textStart);
		String rest = line;
		while (rest.length() > width) {
			int cut = rest.lastIndexOf(' ', width);
			if (cut <= hanging.length())
				cut = rest.indexOf(' ', width);
			if (cut < 0)
				break;
			lines.add(rest.substring(0, cut).stripTrailing());
			rest = hanging + rest.substring(cut + 1).stripLeading();
		}
		lines.add(rest);
		return lines;
	}

}
