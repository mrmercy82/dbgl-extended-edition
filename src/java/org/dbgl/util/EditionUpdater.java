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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


/**
 * Updating the Extended Edition with one click: the update zip of a release on GitHub is downloaded, checked against the
 * SHA-256 sums of the release, and unpacked into a folder of its own. A backup is made (the database, profiles, templates and
 * settings, and the program files that will be replaced). As a running dbgl.jar cannot be replaced (on Windows), a copy of
 * it then waits in a process of its own until DBGL has ended, copies the new files over and starts DBGL again.
 */
public final class EditionUpdater {

	/** what is ready to be installed: the unpacked files, where they go, and the backups made */
	/** alpha 48: the downloaded update is not the file of the release (damaged or changed); said in words to the user */
	public static final class ChecksumMismatch extends IOException {
		private static final long serialVersionUID = 1L;

		public ChecksumMismatch(String message) {
			super(message);
		}
	}

	public record Prepared(File pending, File installDir, File dataBackup, File programBackup) {
	}

	private EditionUpdater() {
	}

	/** the dbgl.jar that runs, or null (e.g. when started from the classes of a build) */
	public static File runningJar() {
		try {
			File f = new File(EditionUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			return f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar") ? f: null;
		} catch (Exception e) {
			return null;
		}
	}

	/** whether this installation can update itself: from a dbgl.jar in a folder that can be written to */
	public static boolean canUpdate() {
		File jar = runningJar();
		return (jar != null) && jar.getParentFile().canWrite() && jar.canWrite();
	}

	/** the folder for the downloads and the files to install */
	public static File workDir() {
		return new File(org.dbgl.service.FileLocationService.getInstance().getDataDir(), "update");
	}

	/** the expected SHA-256 of a file, from a SHA256SUMS.txt ("hash  name" per line), or null */
	public static String sumOf(String sums, String fileName) {
		for (String line: sums.split("\\R")) {
			String[] parts = line.trim().split("\\s+\\*?", 2);
			if ((parts.length == 2) && parts[1].trim().equals(fileName) && parts[0].matches("(?i)[0-9a-f]{64}"))
				return parts[0].toLowerCase(Locale.ROOT);
		}
		return null;
	}

	public static String sha256(File file) throws IOException {
		try (InputStream in = Files.newInputStream(file.toPath())) {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0)
				md.update(buf, 0, n);
			return HexFormat.of().formatHex(md.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	/** unpack a zip into a folder; entries outside of it (../) are refused. @return the files unpacked */
	public static List<String> unzip(File zip, File dir) throws IOException {
		List<String> names = new ArrayList<>();
		Path base = dir.toPath().toAbsolutePath().normalize();
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip.toPath()))) {
			ZipEntry e;
			while ((e = in.getNextEntry()) != null) {
				Path target = base.resolve(e.getName()).normalize();
				if (!target.startsWith(base))
					throw new IOException("Invalid entry in update: " + e.getName());
				if (e.isDirectory()) {
					Files.createDirectories(target);
				} else {
					Files.createDirectories(target.getParent());
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
					names.add(base.relativize(target).toString().replace('\\', '/'));
				}
			}
		}
		return names;
	}

	private static HttpClient client() {
		return HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	private static String getText(String url) throws IOException, InterruptedException {
		HttpResponse<String> r = client().send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("User-Agent", "DBGL").GET().build(),
			HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (r.statusCode() != 200)
			throw new IOException("HTTP " + r.statusCode() + ": " + url);
		return r.body();
	}

	/** download to a file; progress counts up to 1000 */
	private static void download(String url, File to, AtomicInteger progress) throws IOException, InterruptedException {
		HttpResponse<InputStream> r = client().send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).header("User-Agent", "DBGL").GET().build(),
			HttpResponse.BodyHandlers.ofInputStream());
		if (r.statusCode() != 200)
			throw new IOException("HTTP " + r.statusCode() + ": " + url);
		long length = r.headers().firstValueAsLong("Content-Length").orElse(-1);
		try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(to.toPath())) {
			byte[] buf = new byte[65536];
			long done = 0;
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
				done += n;
				if ((length > 0) && (progress != null))
					progress.set((int)(900 * done / length)); // the last part: checking, backup, unpacking
				if (Thread.currentThread().isInterrupted())
					throw new InterruptedException();
			}
		}
	}

	/**
	 * Download, check and unpack the update, and make the backups. Not on the user interface thread.
	 *
	 * @param fromVersion the version running now (for the name of the backups)
	 */
	public static Prepared prepare(String zipUrl, String sumsUrl, String fromVersion, String toVersion, AtomicInteger progress) throws IOException, InterruptedException,
			java.sql.SQLException {
		File jar = runningJar();
		if (jar == null)
			throw new IOException("dbgl.jar not found");
		File work = workDir();
		org.apache.commons.io.FileUtils.deleteQuietly(work);
		Files.createDirectories(work.toPath());
		try {
			return prepare(jar, work, zipUrl, sumsUrl, fromVersion, progress);
		} catch (IOException | InterruptedException | java.sql.SQLException | RuntimeException e) {
			// alpha 48: nothing of a failed or cancelled update stays behind (before, the download stayed until the next try)
			org.apache.commons.io.FileUtils.deleteQuietly(work);
			throw e;
		}
	}

	private static Prepared prepare(File jar, File work, String zipUrl, String sumsUrl, String fromVersion, AtomicInteger progress) throws IOException, InterruptedException,
			java.sql.SQLException {
		String zipName = zipUrl.substring(zipUrl.lastIndexOf('/') + 1);
		String expected = sumOf(getText(sumsUrl), zipName);
		if (expected == null)
			throw new IOException("No SHA-256 sum for " + zipName);
		File zip = new File(work, zipName);
		download(zipUrl, zip, progress);
		String actual = sha256(zip);
		if (!actual.equals(expected))
			throw new ChecksumMismatch("SHA-256 of " + zipName + " is " + actual + ", expected " + expected);
		progress.set(920);

		File pending = new File(work, "files");
		List<String> files = unzip(zip, pending);
		if (!files.contains("dbgl.jar"))
			throw new IOException(zipName + ": no dbgl.jar");
		progress.set(940);

		// backups: what DBGL keeps about the games, and the program files that will be replaced (to go back)
		File backupDir = org.dbgl.service.BackupService.defaultAutoBackupDir();
		Files.createDirectories(backupDir.toPath());
		String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
		String from = fromVersion.replaceAll("[^0-9A-Za-z.]+", "");
		File dataBackup = new File(backupDir, "dbgl-backup-before-update-" + from + "-" + stamp + ".zip");
		org.dbgl.service.BackupService.backup(dataBackup);
		progress.set(980);
		File installDir = jar.getParentFile();
		File programBackup = new File(backupDir, "dbgl-program-" + from + "-" + stamp + ".zip");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(programBackup.toPath()))) {
			for (String name: files) {
				File existing = new File(installDir, name);
				if (existing.isFile()) {
					out.putNextEntry(new ZipEntry(name));
					Files.copy(existing.toPath(), out);
					out.closeEntry();
				}
			}
		}
		progress.set(1000);
		return new Prepared(pending, installDir, dataBackup, programBackup);
	}

	/**
	 * Start the process that installs the update once DBGL has ended (then DBGL must end), and starts DBGL again.
	 *
	 * @return false if it could not be started
	 */
	public static boolean launchInstaller(Prepared p) {
		try {
			File jar = runningJar();
			File copy = new File(workDir(), "dbgl-updater.jar"); // the running dbgl.jar is replaced, so the installer runs from a copy
			Files.copy(jar.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
			String java = new File(new File(System.getProperty("java.home"), "bin"), SystemUtils.IS_WINDOWS ? "javaw.exe": "java").getPath();
			List<String> cmd = new ArrayList<>(List.of(java, "-cp", copy.getPath(), EditionUpdater.class.getName(), String.valueOf(ProcessHandle.current().pid()),
				p.pending().getPath(), p.installDir().getPath()));
			cmd.addAll(restartCommand());
			new ProcessBuilder(cmd).directory(p.installDir()).redirectErrorStream(true).redirectOutput(new File(workDir(), "updater-output.txt")).start();
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * How this DBGL was started (java with its options and -jar dbgl.jar), to start it again. Windows does not tell the
	 * arguments of a process: then the same java with the options of this one (if known) and -jar dbgl.jar.
	 */
	public static List<String> restartCommand() {
		ProcessHandle.Info info = ProcessHandle.current().info();
		List<String> cmd = new ArrayList<>();
		if (info.command().isPresent() && info.arguments().isPresent() && (info.arguments().get().length > 0)) {
			cmd.add(info.command().get());
			cmd.addAll(List.of(info.arguments().get()));
			return cmd;
		}
		File jar = runningJar();
		if (jar == null)
			return cmd;
		cmd.add(new File(new File(System.getProperty("java.home"), "bin"), SystemUtils.IS_WINDOWS ? "javaw.exe": "java").getPath());
		try {
			// java.management may be missing in a small Java runtime
			Class<?> factory = Class.forName("java.lang.management.ManagementFactory");
			Object bean = factory.getMethod("getRuntimeMXBean").invoke(null);
			@SuppressWarnings("unchecked")
			List<String> jvmArgs = (List<String>)Class.forName("java.lang.management.RuntimeMXBean").getMethod("getInputArguments").invoke(bean);
			jvmArgs.stream().filter(x -> !x.startsWith("-agentlib") && !x.startsWith("-javaagent")).forEach(cmd::add);
		} catch (Exception | LinkageError e) {
			cmd.add("-Djdk.util.zip.disableZip64ExtraFieldValidation=true");
		}
		cmd.add("-jar");
		cmd.add(jar.getName());
		return cmd;
	}

	/** copy a file, trying again for a while if it is still in use */
	private static void copyRetrying(Path from, Path to) throws IOException, InterruptedException {
		for (int attempt = 0;; attempt++) {
			try {
				Files.createDirectories(to.getParent());
				Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (IOException e) {
				if (attempt >= 30)
					throw e;
				Thread.sleep(1000);
			}
		}
	}

	/**
	 * After an update installed with one click (its log ends with "Done"): the archive downloaded for it, still in the work
	 * folder; null if there is none (nothing installed, already cleaned up, or the update failed: then everything stays there
	 * to see what went wrong).
	 */
	public static File installedArchive() {
		File work = workDir();
		File log = new File(work, "update-log.txt");
		try {
			if (!log.isFile() || Files.readAllLines(log.toPath(), StandardCharsets.UTF_8).stream().noneMatch(l -> l.trim().equals("Done")))
				return null;
		} catch (IOException e) {
			return null;
		}
		File[] zips = work.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".zip"));
		return (zips != null) && (zips.length > 0) ? zips[0]: null;
	}

	/**
	 * Clean up after an installed update (alpha 47): the unpacked files and the copy of the installer are deleted, the logs
	 * stay; the downloaded archive goes into the backup folder if wanted (to install it again without GitHub), else it is deleted.
	 *
	 * @return where the archive is kept, or null
	 */
	public static File cleanUp(File archive, boolean keepArchive) throws IOException {
		File work = workDir();
		File kept = null;
		if ((archive != null) && archive.isFile()) {
			if (keepArchive) {
				File backupDir = org.dbgl.service.BackupService.defaultAutoBackupDir();
				Files.createDirectories(backupDir.toPath());
				kept = new File(backupDir, archive.getName());
				Files.move(archive.toPath(), kept.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.delete(archive.toPath());
			}
		}
		org.apache.commons.io.FileUtils.deleteQuietly(new File(work, "files"));
		org.apache.commons.io.FileUtils.deleteQuietly(new File(work, "dbgl-updater.jar"));
		return kept;
	}

	/** the installer: args pid, folder with the new files, installation folder, then the command to start DBGL again */
	public static void main(String[] args) throws Exception {
		File log = new File(new File(args[1]).getParentFile(), "update-log.txt");
		try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(log.toPath(), StandardCharsets.UTF_8))) {
			long pid = Long.parseLong(args[0]);
			Path pending = new File(args[1]).toPath(), install = new File(args[2]).toPath();
			out.println("Waiting for DBGL (" + pid + ") to end");
			out.flush();
			java.util.Optional<ProcessHandle> dbgl = ProcessHandle.of(pid);
			if (dbgl.isPresent())
				dbgl.get().onExit().get(10, TimeUnit.MINUTES);
			Thread.sleep(1500);
			List<Path> files;
			try (java.util.stream.Stream<Path> walk = Files.walk(pending)) {
				files = walk.filter(Files::isRegularFile).toList();
			}
			for (Path f: files) {
				Path rel = pending.relativize(f);
				copyRetrying(f, install.resolve(rel));
				out.println("Installed " + rel);
			}
			out.println("Done");
			out.flush();
			if (args.length > 3) {
				List<String> cmd = new ArrayList<>(List.of(args).subList(3, args.length));
				new ProcessBuilder(cmd).directory(install.toFile()).start();
				out.println("Started " + String.join(" ", cmd));
			}
		} catch (Exception e) {
			try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(log.toPath(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND))) {
				e.printStackTrace(out);
			}
			throw e;
		}
	}
}
