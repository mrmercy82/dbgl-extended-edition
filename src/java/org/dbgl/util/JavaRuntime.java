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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * alpha 52: a Java of DBGL's own, in the folder "jre" of DBGL (launch.exe looks there first, the start script for Linux too).
 * With the update of DBGL it can come along: Eclipse Temurin (Adoptium), the runtime only (JRE), checked by its SHA-256,
 * unpacked into the work folder and tried (java -version) before the installer puts it in place once DBGL has ended (the Java
 * that runs cannot be replaced while it runs). The Java of the system is not touched. Windows and Linux; not macOS, where
 * DBGL.app has its own way of finding Java.
 */
public final class JavaRuntime {

	public static final int FEATURE = 21;
	public static final String FOLDER = "jre";
	static final String API = "https://api.adoptium.net/v3/assets/latest/" + FEATURE + "/hotspot?image_type=jre&vendor=eclipse&os=%s&architecture=%s";

	/** what Adoptium offers: the version ("21.0.9+10-LTS"), the file, its SHA-256 and size */
	public record Offer(String version, String url, String name, String sha256, long size) {
	}

	private JavaRuntime() {
	}

	/** whether a Java can come with the update here: Windows or Linux, on x64 or ARM, in a DBGL that can update itself */
	public static boolean possible() {
		return (SystemUtils.IS_WINDOWS || SystemUtils.IS_LINUX) && (arch() != null) && EditionUpdater.canUpdate();
	}

	static String os() {
		return SystemUtils.IS_WINDOWS ? "windows": SystemUtils.IS_LINUX ? "linux": null;
	}

	static String arch() {
		String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		if (a.equals("amd64") || a.equals("x86_64"))
			return "x64";
		if (a.equals("aarch64") || a.equals("arm64"))
			return "aarch64";
		return null;
	}

	/** the folder of DBGL's own Java: beside dbgl.jar */
	public static File folder() {
		File jar = EditionUpdater.runningJar();
		return new File(jar == null ? new File("."): jar.getParentFile(), FOLDER);
	}

	/** the version of a Java in a folder, from its file "release" (JAVA_RUNTIME_VERSION="21.0.5+11-LTS"); null if there is none */
	public static String versionIn(File jre) {
		File release = new File(jre, "release");
		if (!release.isFile() || (release.length() > 100_000))
			return null;
		try {
			String version = null;
			for (String line: Files.readAllLines(release.toPath(), StandardCharsets.UTF_8)) {
				if (line.startsWith("JAVA_RUNTIME_VERSION="))
					return line.substring(line.indexOf('=') + 1).replace("\"", "").trim();
				if (line.startsWith("JAVA_VERSION="))
					version = line.substring(line.indexOf('=') + 1).replace("\"", "").trim();
			}
			return version;
		} catch (IOException e) {
			return null;
		}
	}

	/** whether version a is newer than b ("21.0.9+10-LTS" against "21.0.5+11-LTS"); a newer one if b is not known */
	public static boolean newer(String a, String b) {
		if (b == null)
			return a != null;
		try {
			return Runtime.Version.parse(a).compareToIgnoreOptional(Runtime.Version.parse(b)) > 0;
		} catch (RuntimeException e) {
			return !a.equals(b);
		}
	}

	/** the newest Java of the feature version for this system at Adoptium */
	public static Offer latest() throws IOException, InterruptedException {
		return parse(EditionUpdater.getText(String.format(API, os(), arch())));
	}

	public static Offer parse(String json) throws IOException {
		JSONArray list = new JSONArray(json);
		for (int i = 0; i < list.length(); i++) {
			JSONObject o = list.getJSONObject(i);
			// assets/latest has "binary" and "version", the other lists of the API "binaries" and "version_data": both are taken
			JSONObject binary = o.optJSONObject("binary");
			if ((binary == null) && (o.optJSONArray("binaries") != null) && !o.getJSONArray("binaries").isEmpty())
				binary = o.getJSONArray("binaries").optJSONObject(0);
			JSONObject pack = binary == null ? null: binary.optJSONObject("package");
			if (pack == null)
				continue;
			String name = pack.optString("name", ""), link = pack.optString("link", ""), sum = pack.optString("checksum", "").toLowerCase(Locale.ROOT);
			String lower = name.toLowerCase(Locale.ROOT);
			if (!(lower.endsWith(".zip") || lower.endsWith(".tar.gz")) || !lower.contains("jre") || !link.startsWith("https://") || !sum.matches("[0-9a-f]{64}"))
				continue;
			JSONObject v = o.optJSONObject("version") != null ? o.optJSONObject("version"): o.optJSONObject("version_data");
			String version = v == null ? o.optString("release_name", ""): v.optString("openjdk_version", v.optString("semver", ""));
			return new Offer(version, link, name.replaceAll("[\\\\/]", "_"), sum, pack.optLong("size", -1));
		}
		throw new IOException("No Java " + FEATURE + " for " + os() + "/" + arch() + " at Adoptium");
	}

	/**
	 * download, check, unpack and try the Java. Not on the user interface thread.
	 *
	 * @return the folder of the new Java (in the work folder), to give to the installer
	 */
	public static File prepare(Offer offer, AtomicInteger progress) throws IOException, InterruptedException {
		File work = new File(EditionUpdater.workDir(), "java");
		org.apache.commons.io.FileUtils.deleteQuietly(work);
		Files.createDirectories(work.toPath());
		try {
			File archive = new File(work, offer.name());
			EditionUpdater.download(offer.url(), archive, progress);
			String actual = EditionUpdater.sha256(archive);
			if (!actual.equals(offer.sha256()))
				throw new EditionUpdater.ChecksumMismatch("SHA-256 of " + offer.name() + " is " + actual + ", expected " + offer.sha256());
			progress.set(920);
			File unpacked = new File(work, "unpacked");
			if (offer.name().toLowerCase(Locale.ROOT).endsWith(".zip"))
				EditionUpdater.unzip(archive, unpacked);
			else
				untar(archive, unpacked);
			Files.delete(archive.toPath());
			File jre = homeIn(unpacked);
			if (jre == null)
				throw new IOException(offer.name() + ": no Java in it");
			progress.set(960);
			if (!runs(jre))
				throw new IOException(offer.name() + ": java -version failed");
			progress.set(1000);
			return jre;
		} catch (IOException | InterruptedException | RuntimeException e) {
			org.apache.commons.io.FileUtils.deleteQuietly(work);
			throw e;
		}
	}

	/** the folder with bin/java in it: the archive has one folder ("jdk-21.0.9+10-jre") */
	public static File homeIn(File unpacked) {
		if (new File(unpacked, "release").isFile() && java(unpacked).isFile())
			return unpacked;
		File[] dirs = unpacked.listFiles(File::isDirectory);
		if (dirs != null)
			for (File d: dirs)
				if (new File(d, "release").isFile() && java(d).isFile())
					return d;
		return null;
	}

	static File java(File jre) {
		return new File(new File(jre, "bin"), SystemUtils.IS_WINDOWS ? "java.exe": "java");
	}

	/** whether the Java starts: "java -version" ends without an error within a minute */
	static boolean runs(File jre) throws InterruptedException {
		try {
			Process p = new ProcessBuilder(java(jre).getPath(), "-version").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
			if (!p.waitFor(1, TimeUnit.MINUTES)) {
				p.destroyForcibly();
				return false;
			}
			return p.exitValue() == 0;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * unpack a .tar.gz (the Java for Linux): files, folders and links within the folder, with their right to run. An entry
	 * outside of the folder (../, an absolute path, a link pointing out) is refused.
	 */
	public static List<String> untar(File archive, File dir) throws IOException {
		List<String> names = new ArrayList<>();
		Path base = dir.toPath().toAbsolutePath().normalize();
		Files.createDirectories(base);
		List<String[]> links = new ArrayList<>();
		try (InputStream in = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(archive.toPath())))) {
			byte[] header = new byte[512];
			String longName = null, paxPath = null;
			while (true) {
				if (in.readNBytes(header, 0, 512) < 512)
					break;
				if (allZero(header))
					break;
				String name = string(header, 0, 100);
				String prefix = string(header, 345, 155);
				if (!prefix.isEmpty() && string(header, 257, 6).startsWith("ustar"))
					name = prefix + "/" + name;
				long size = octal(header, 124, 12);
				int mode = (int)octal(header, 100, 8);
				char type = (char)header[156];
				String linkName = string(header, 157, 100);
				if ((size < 0) || (size > 1L << 32))
					throw new IOException("Invalid entry in " + archive.getName());
				if ((type == 'L') || (type == 'x') || (type == 'g')) { // GNU long name, pax header: for the next entry
					byte[] data = in.readNBytes((int)size);
					skip(in, pad(size));
					if (type == 'L')
						longName = new String(data, StandardCharsets.UTF_8).replace("\0", "");
					else if (type == 'x')
						paxPath = paxValue(new String(data, StandardCharsets.UTF_8), "path");
					continue;
				}
				if (paxPath != null)
					name = paxPath;
				else if (longName != null)
					name = longName;
				longName = null;
				paxPath = null;
				Path target = base.resolve(name).normalize();
				if (!target.startsWith(base) || name.startsWith("/"))
					throw new IOException("Invalid entry in " + archive.getName() + ": " + name);
				if (type == '5') {
					Files.createDirectories(target);
				} else if ((type == '0') || (type == '\0') || (type == '7')) {
					Files.createDirectories(target.getParent());
					try (java.io.OutputStream out = Files.newOutputStream(target)) {
						copy(in, out, size);
					}
					if ((mode & 0100) != 0)
						target.toFile().setExecutable(true, false);
					names.add(base.relativize(target).toString().replace('\\', '/'));
					skip(in, pad(size));
					continue;
				} else if (type == '2') {
					links.add(new String[] {target.toString(), linkName});
				}
				skip(in, size + pad(size)); // other kinds (hard links, devices): left out
			}
		}
		for (String[] l: links) {
			Path link = Path.of(l[0]);
			Path to = link.getParent().resolve(l[1]).normalize();
			if (!to.startsWith(base) || l[1].startsWith("/"))
				continue; // a link out of the folder: left out
			try {
				Files.createDirectories(link.getParent());
				Files.createSymbolicLink(link, Path.of(l[1]));
			} catch (IOException | UnsupportedOperationException e) {
				// without it
			}
		}
		return names;
	}

	private static boolean allZero(byte[] b) {
		for (byte x: b)
			if (x != 0)
				return false;
		return true;
	}

	private static String string(byte[] b, int from, int length) {
		int end = from;
		while ((end < from + length) && (b[end] != 0))
			end++;
		return new String(b, from, end - from, StandardCharsets.UTF_8);
	}

	static long octal(byte[] b, int from, int length) {
		if ((b[from] & 0x80) != 0) // base-256 for big sizes
			return -1;
		String s = string(b, from, length).trim();
		if (s.isEmpty())
			return 0;
		try {
			return Long.parseLong(s, 8);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static long pad(long size) {
		return (512 - (size % 512)) % 512;
	}

	private static void skip(InputStream in, long n) throws IOException {
		long left = n;
		while (left > 0) {
			long s = in.skip(left);
			if (s <= 0) {
				if (in.read() < 0)
					return;
				s = 1;
			}
			left -= s;
		}
	}

	private static void copy(InputStream in, java.io.OutputStream out, long size) throws IOException {
		byte[] buf = new byte[65536];
		long left = size;
		while (left > 0) {
			int n = in.read(buf, 0, (int)Math.min(buf.length, left));
			if (n < 0)
				throw new IOException("Archive ends too early");
			out.write(buf, 0, n);
			left -= n;
		}
	}

	/** a value of a pax header ("27 path=jdk-21.0.9+10-jre/...") */
	static String paxValue(String records, String key) {
		for (String r: records.split("\n")) {
			int space = r.indexOf(' ');
			if ((space > 0) && r.startsWith(key + "=", space + 1))
				return r.substring(space + 2 + key.length());
		}
		return null;
	}

	/**
	 * the installer's part (after DBGL has ended): the Java of DBGL replaced by the new one. The old one is moved aside first
	 * and comes back if the new one cannot be put in place; the new one stays in the work folder until DBGL cleans up.
	 */
	public static void install(File newJre, File installDir, java.io.PrintWriter log) throws IOException {
		Path target = new File(installDir, FOLDER).toPath();
		Path old = new File(installDir, FOLDER + "-old").toPath();
		org.apache.commons.io.FileUtils.deleteQuietly(old.toFile());
		boolean hadOne = Files.exists(target);
		if (hadOne)
			Files.move(target, old);
		try {
			// copied, not moved: the installer runs with this Java
			org.apache.commons.io.FileUtils.copyDirectory(newJre, target.toFile());
			for (File f: org.apache.commons.io.FileUtils.listFiles(newJre, null, true))
				if (f.canExecute())
					target.resolve(newJre.toPath().relativize(f.toPath())).toFile().setExecutable(true, false);
			log.println("Installed Java " + versionIn(target.toFile()));
		} catch (IOException | RuntimeException e) {
			org.apache.commons.io.FileUtils.deleteQuietly(target.toFile());
			if (hadOne)
				Files.move(old, target);
			throw e;
		}
		org.apache.commons.io.FileUtils.deleteQuietly(old.toFile());
	}
}
