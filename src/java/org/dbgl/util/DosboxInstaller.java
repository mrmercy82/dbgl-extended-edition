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
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Downloads a release of DOSBox Staging or DOSBox-X from GitHub and unpacks it into the DOSBox folder of DBGL, so it can be
 * added as a new DOSBox version. Windows and Linux builds are supported; for macOS (disk images) the download page is opened
 * instead.
 */
public final class DosboxInstaller {

	private static final List<String> EXECUTABLES = List.of("dosbox.exe", "dosbox-x.exe", "dosbox", "dosbox-x");

	private DosboxInstaller() {
	}

	/**
	 * @return the downloadable file (name, URL) that fits this computer best, or null if there is none (e.g. only a macOS disk
	 *         image, or only an installer)
	 */
	public static String[] chooseAsset(List<String[]> assets, boolean windows, boolean linux, boolean arm) {
		String[] best = null;
		int bestScore = 0;
		for (String[] asset: assets) {
			int score = score(asset[0].toLowerCase(Locale.ROOT), windows, linux, arm);
			if (score > bestScore) {
				best = asset;
				bestScore = score;
			}
		}
		return best;
	}

	static int score(String name, boolean windows, boolean linux, boolean arm) {
		boolean archive = name.endsWith(".zip") || name.endsWith(".tar.xz") || name.endsWith(".tar.gz") || name.endsWith(".tar.bz2");
		if (!archive || name.contains("source") || name.contains("src") || name.contains("debug") || name.contains("pdb") || name.contains("symbols") || name.contains("installer"))
			return 0;
		boolean forWindows = name.contains("win");
		boolean forLinux = name.contains("linux");
		if ((windows && !forWindows) || (linux && !forLinux) || (!windows && !linux))
			return 0;
		boolean nameArm = name.contains("arm64") || name.contains("aarch64");
		boolean name64 = name.contains("x86_64") || name.contains("x64") || name.contains("win64") || name.contains("amd64");
		if (arm != nameArm)
			return 0;
		int score = 10;
		if (name64 || nameArm)
			score += 5;
		if (name.contains("win32") || name.contains("x86-") || name.contains("i686"))
			score -= 6; // 32-bit builds only when there is nothing else
		if (name.contains("vsbuild")) // DOSBox-X: the Visual Studio build is the recommended one for Windows
			score += 3;
		if (name.contains("sdl1"))
			score -= 2;
		if (name.endsWith(".zip"))
			score += 1;
		return Math.max(score, 1);
	}

	/** download a file, reporting the progress in percent (or -1 when the size is unknown) */
	public static File download(String url, File target, IntConsumer progress) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.ALWAYS).build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).header("User-Agent", "DBGL").GET().build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200)
			throw new IOException("HTTP " + response.statusCode() + ": " + url);
		long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
		try (InputStream in = response.body(); var out = Files.newOutputStream(target.toPath())) {
			byte[] buffer = new byte[64 * 1024];
			long done = 0;
			int lastPercent = -2;
			for (int n; (n = in.read(buffer)) > 0;) {
				out.write(buffer, 0, n);
				done += n;
				int percent = total > 0 ? (int)(done * 100 / total): -1;
				if (percent != lastPercent) {
					progress.accept(percent);
					lastPercent = percent;
				}
			}
		}
		return target;
	}

	/** unpack a .zip (in Java) or a .tar.xz/.tar.gz (with the tar of the system) into dir */
	public static void unpack(File archive, File dir) throws IOException, InterruptedException {
		Files.createDirectories(dir.toPath());
		String name = archive.getName().toLowerCase(Locale.ROOT);
		if (name.endsWith(".zip")) {
			Path base = dir.toPath().toAbsolutePath().normalize();
			try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive.toPath()))) {
				for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
					Path target = base.resolve(entry.getName()).normalize();
					if (!target.startsWith(base))
						throw new IOException("Invalid entry in archive: " + entry.getName());
					if (entry.isDirectory()) {
						Files.createDirectories(target);
					} else {
						Files.createDirectories(target.getParent());
						Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
		} else {
			Process p = new ProcessBuilder("tar", "-xf", archive.getAbsolutePath(), "-C", dir.getAbsolutePath()).redirectErrorStream(true).start();
			String output = new String(p.getInputStream().readAllBytes());
			if (!p.waitFor(5, TimeUnit.MINUTES) || (p.exitValue() != 0))
				throw new IOException("tar failed: " + output);
		}
	}

	/** @return the DOSBox executable in the unpacked folder (searched a few levels deep), or null */
	public static File findExecutable(File dir) throws IOException {
		try (Stream<Path> files = Files.walk(dir.toPath(), 4)) {
			return files.filter(Files::isRegularFile).filter(p -> EXECUTABLES.contains(p.getFileName().toString().toLowerCase(Locale.ROOT)))
					.sorted((a, b) -> Integer.compare(a.getNameCount(), b.getNameCount())).map(Path::toFile).findFirst().map(f -> {
						if (!f.getName().endsWith(".exe"))
							f.setExecutable(true);
						return f;
					}).orElse(null);
		}
	}

	/** @return a folder name for the installation, e.g. "DOSBox Staging 0.83.0" -> "dosbox-staging-0.83.0" */
	public static String folderName(String family, String version) {
		return (family + '-' + version).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.\\-]+", "-");
	}
}
