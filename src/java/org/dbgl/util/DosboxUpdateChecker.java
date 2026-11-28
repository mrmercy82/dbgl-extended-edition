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

import org.dbgl.service.SettingsService;
import org.json.JSONObject;


/**
 * Asks GitHub for the latest releases of DOSBox Staging and DOSBox-X, to tell when a newer version than the installed ones is
 * out. DosboxInstaller can download and unpack a release.
 */
public final class DosboxUpdateChecker {

	/** a release on GitHub, with its downloadable files (name, download URL, digest such as "sha256:..." or "") */
	public record Release(String family, String version, String url, List<String[]> assets) {
	}

	private static final String[][] SOURCES = {
		{SettingsService.STAGING_DOSBOX_FAM.title_, "https://api.github.com/repos/dosbox-staging/dosbox-staging/releases/latest", "https://www.dosbox-staging.org/releases/"},
		{SettingsService.DOSBOX_X_DOSBOX_FAM.title_, "https://api.github.com/repos/joncampbell123/dosbox-x/releases/latest", "https://dosbox-x.com/"}};

	private static final Pattern X_VERSION_PATRN = Pattern.compile("(20\\d\\d\\.\\d\\d\\.\\d\\d)");
	private static final Pattern STAGING_VERSION_PATRN = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");

	private DosboxUpdateChecker() {
	}

	/** @return the version in a release tag, e.g. "v0.83.0" -> "0.83.0", "dosbox-x-v2026.10.01" -> "2026.10.01", or null */
	public static String versionOfTag(String family, String tag) {
		if (tag == null)
			return null;
		Matcher m = (SettingsService.DOSBOX_X_DOSBOX_FAM.title_.equals(family) ? X_VERSION_PATRN: STAGING_VERSION_PATRN).matcher(tag);
		return m.find() ? m.group(1): null;
	}

	/** compares versions like 0.83.0 or 2026.10.01 number by number */
	public static int compareVersions(String a, String b) {
		String[] pa = a.split("\\D+"), pb = b.split("\\D+");
		for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
			int x = i < pa.length && !pa[i].isEmpty() ? Integer.parseInt(pa[i]): 0;
			int y = i < pb.length && !pb[i].isEmpty() ? Integer.parseInt(pb[i]): 0;
			if (x != y)
				return Integer.compare(x, y);
		}
		return 0;
	}

	/** alpha 52: the release of ScummVM in a tag like "v2.9.1" or "v2026.3.0", nothing else (no "desc/..." or "...git") */
	private static final Pattern SCUMMVM_TAG_PATRN = Pattern.compile("^v?(\\d+\\.\\d+(?:\\.\\d+)?)$");
	static final String SCUMMVM_PAGE = "https://www.scummvm.org/downloads/";

	static String scummvmVersionOfTag(String tag) {
		Matcher m = SCUMMVM_TAG_PATRN.matcher(tag == null ? "": tag.trim());
		return m.matches() ? m.group(1): null;
	}

	/**
	 * alpha 52: the latest ScummVM, from its latest release on GitHub or else the highest version among its tags; its page is
	 * the one of the downloads (ScummVM is installed with its own installer, DBGL sets nothing up). Null if not found.
	 */
	public static Release latestScummvm() {
		HttpClient client = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
		try {
			HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("https://api.github.com/repos/scummvm/scummvm/releases/latest")).timeout(Duration.ofSeconds(15))
					.header("Accept", "application/vnd.github+json").header("User-Agent", "DBGL").GET().build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				String version = scummvmVersionOfTag(new JSONObject(response.body()).optString("tag_name", null));
				if (version != null)
					return new Release(Scummvm.FAMILY, version, SCUMMVM_PAGE, List.of());
			}
			response = client.send(HttpRequest.newBuilder(URI.create("https://api.github.com/repos/scummvm/scummvm/tags?per_page=100")).timeout(Duration.ofSeconds(15))
					.header("Accept", "application/vnd.github+json").header("User-Agent", "DBGL").GET().build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200)
				return newestScummvmTag(new org.json.JSONArray(response.body()));
		} catch (Exception e) {
			System.out.println("Update check for ScummVM failed: " + e);
		}
		return null;
	}

	/**
	 * alpha 54 (TEST16): the release with the file to download on Windows, the portable ZIP of the ScummVM project
	 * (downloads.scummvm.org/frs/scummvm/&lt;version&gt;/scummvm-&lt;version&gt;-win32-x86_64.zip) with the SHA-256 it publishes next to
	 * it (.sha256); elsewhere (installers, disk images, Flatpak) as it was: its page
	 */
	public static Release withScummvmDownload(Release r) {
		if ((r == null) || !SystemUtils.IS_WINDOWS || System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT).contains("arm"))
			return r;
		String[] asset = scummvmWindowsAsset(r.version());
		try {
			HttpClient client = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
			HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(asset[1] + ".sha256")).timeout(Duration.ofSeconds(15)).header("User-Agent", "DBGL").GET().build(),
				HttpResponse.BodyHandlers.ofString());
			String sum = response.statusCode() == 200 ? sha256Of(response.body()): null;
			if (sum == null)
				return r; // no checksum to check it with: only the page
			return new Release(r.family(), r.version(), r.url(), List.<String[]>of(new String[] {asset[0], asset[1], "sha256:" + sum}));
		} catch (Exception e) {
			System.out.println("ScummVM download for Windows not found: " + e);
			return r;
		}
	}

	/** the name and address of the portable ZIP of ScummVM for 64-bit Windows */
	public static String[] scummvmWindowsAsset(String version) {
		String name = "scummvm-" + version + "-win32-x86_64.zip";
		return new String[] {name, "https://downloads.scummvm.org/frs/scummvm/" + version + "/" + name};
	}

	/** the checksum in a .sha256 file ("<64 hex digits>  <name>"), in small letters; null if there is none */
	public static String sha256Of(String text) {
		Matcher m = Pattern.compile("(?i)\\b([0-9a-f]{64})\\b").matcher(text == null ? "": text);
		return m.find() ? m.group(1).toLowerCase(java.util.Locale.ROOT): null;
	}

	public static Release newestScummvmTag(org.json.JSONArray tags) {
		String best = null;
		for (int i = 0; i < tags.length(); i++) {
			String v = scummvmVersionOfTag(tags.getJSONObject(i).optString("name", null));
			if ((v != null) && ((best == null) || (compareVersions(v, best) > 0)))
				best = v;
		}
		return best == null ? null: new Release(Scummvm.FAMILY, best, SCUMMVM_PAGE, List.of());
	}

	/** @return the latest releases that could be found; failures are left out */
	public static List<Release> latestReleases() {
		List<Release> result = new ArrayList<>();
		HttpClient client = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
		for (String[] source: SOURCES) {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(source[1])).timeout(Duration.ofSeconds(15)).header("Accept", "application/vnd.github+json")
						.header("User-Agent", "DBGL").GET().build();
				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() != 200)
					continue;
				JSONObject json = new JSONObject(response.body());
				String version = versionOfTag(source[0], json.optString("tag_name", null));
				if (version != null) {
					List<String[]> assets = new ArrayList<>();
					org.json.JSONArray list = json.optJSONArray("assets");
					for (int i = 0; (list != null) && (i < list.length()); i++) {
						JSONObject asset = list.getJSONObject(i);
						assets.add(new String[] {asset.optString("name", ""), asset.optString("browser_download_url", ""), asset.optString("digest", "")});
					}
					result.add(new Release(source[0], version, json.optString("html_url", source[2]), assets));
				}
			} catch (Exception e) {
				// no connection, rate limit, ...: try again another day
				System.out.println("DOSBox update check for " + source[0] + " failed: " + e);
			}
		}
		return result;
	}
}
