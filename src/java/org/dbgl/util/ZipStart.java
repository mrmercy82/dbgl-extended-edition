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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.model.conf.mount.OverlayMount;
import org.dbgl.model.conf.mount.PhysFsMount;
import org.dbgl.model.entity.TemplateProfileBase;
import org.dbgl.model.factory.MountFactory;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.SettingsService;


/**
 * Alpha 54: games in a ZIP archive, for every DOSBox version. A profile mounts the archive as DBGL always could ("mount C
 * "GAME.ZIP:\"", the mount dialog's "Mount ZIP", optionally with a folder for what the game writes). That works in DOSBox
 * Daum as it is. For a start only (in the copy of the configuration DOSBox gets, never in the profile itself):
 * <ul>
 * <li>DOSBox-X 0.84.1 and newer mounts the archive itself, read-only; what the game writes goes to an overlay ("mount -t
 * overlay C folder"), DOSBox-X keeps it in folder\C_DRIVE.</li>
 * <li>every other DOSBox (Staging, 0.74, SVN, ECE, an older DOSBox-X): DBGL unpacks the ZIP into a work folder, puts the
 * files written before (folder\C_DRIVE, the same layout) over it and mounts the work folder. When DOSBox has ended, what is
 * new or changed there is copied to folder\C_DRIVE and the work folder deleted. A 7z archive cannot be unpacked by DBGL.</li>
 * </ul>
 * The folder for what the game writes is the one of the mount, or else the profile's own one in DBGL's data folder
 * (zipstart\changes\&lt;id&gt;), kept when the profile is deleted like its saved games.
 */
public final class ZipStart {

	public static final String DIR = "zipstart";

	private ZipStart() {
	}

	/** a ZIP or 7z mounted as a drive: DBGL's PhysFS mount, or DOSBox-X's own "mount C GAME.ZIP" */
	public static boolean isArchiveMount(Mount m) {
		if ((m == null) || m.isUnmounted() || (m instanceof OverlayMount))
			return false;
		if (m instanceof PhysFsMount)
			return true;
		return (m instanceof DirMount dm) && isArchive(dm.getPath().getName()) && dm.getCanonicalPath().isFile();
	}

	static boolean isArchive(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return n.endsWith(".zip") || n.endsWith(".7z");
	}

	/** the archive file of such a mount */
	public static File archiveOf(Mount m) {
		return ((DirMount)m).getCanonicalPath();
	}

	/** a ZIP made by DBGL's export (with profiles.xml), not a game */
	public static boolean isDbglExport(File zip) {
		try (ZipFile z = new ZipFile(zip)) {
			return z.getEntry(org.dbgl.service.ImportExportProfilesService.PROFILES_XML) != null;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * The most likely start file of a game in a ZIP, as for a folder (see {@link UnlinkedGames}): not an installer or helper,
	 * named like the archive, the fewest folders deep, .EXE before .COM and .BAT, the bigger one.
	 *
	 * @return its path in the archive with the separators of the system, null if there is none
	 */
	public static String mainIn(File zip) throws IOException {
		String name = StringUtils.substringBeforeLast(zip.getName(), ".").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		List<ZipEntry> programs = new ArrayList<>();
		try (ZipFile z = new ZipFile(zip)) {
			for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements();) {
				ZipEntry entry = e.nextElement();
				if (!entry.isDirectory() && FilesUtils.isExecutable(entry.getName()) && !entry.getName().contains(".."))
					programs.add(entry);
			}
		}
		java.util.function.Function<ZipEntry, String> file = e -> StringUtils.substringAfterLast("/" + e.getName().replace('\\', '/'), "/");
		return programs.stream().sorted(java.util.Comparator.<ZipEntry, Integer> comparing(e -> UnlinkedGames.NOT_MAIN.matcher(file.apply(e)).matches() ? 1: 0)
				.thenComparing(e -> UnlinkedGames.like(name, StringUtils.substringBeforeLast(file.apply(e), ".").toLowerCase(Locale.ROOT)) ? 0: 1)
				.thenComparing(e -> StringUtils.countMatches(e.getName().replace('\\', '/'), '/'))
				.thenComparing(e -> file.apply(e).toLowerCase(Locale.ROOT).endsWith(".exe") ? 0: 1)
				.thenComparing(java.util.Comparator.comparingLong(ZipEntry::getSize).reversed())
				.thenComparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER))
				.map(e -> org.apache.commons.io.FilenameUtils.separatorsToSystem(e.getName())).findFirst().orElse(null);
	}

	/** how a DOSBox version gets an archive */
	public enum Way {
		/** as written (DOSBox Daum and other builds with PhysFS) */
		AS_IS,
		/** DOSBox-X: its own mount of the archive, writing to an overlay */
		DOSBOX_X,
		/** unpacked by DBGL into a work folder */
		UNPACK
	}

	public static Way wayFor(DosboxVersion v) {
		String family = v == null ? "": StringUtils.defaultString(v.getFamily());
		if (family.equalsIgnoreCase(SettingsService.DAUM_DOSBOX_FAM.title_))
			return Way.AS_IS;
		if (family.equalsIgnoreCase(SettingsService.DOSBOX_X_DOSBOX_FAM.title_)
				&& (DosboxUpdateChecker.compareVersions(StringUtils.defaultString(v.getVersion()), "0.84.1") >= 0))
			return Way.DOSBOX_X; // archives since 0.83.9, reading them reliably since 0.84.1 (DOSBox-X issue 3535)
		return Way.UNPACK;
	}

	/** whether a start of this profile with this DOSBox needs a copy of the configuration with other mounts */
	public static boolean needsAdapting(TemplateProfileBase p, DosboxVersion v) {
		return (wayFor(v) != Way.AS_IS) && p.getConfiguration().getAutoexec().getMountingpoints().stream().anyMatch(ZipStart::isArchiveMount);
	}

	/** the profile's own folder for what its game writes, in DBGL's data folder */
	public static File ownChangesDir(int profileId) {
		return new File(new File(new File(FileLocationService.getInstance().getDataDir(), DIR), "changes"), String.valueOf(profileId));
	}

	static File changesDir(Mount m, int profileId) {
		File write = (m instanceof PhysFsMount pm) ? pm.getCanonicalWrite(): null;
		return write != null ? write: ownChangesDir(profileId);
	}

	/** where DOSBox-X keeps what is written to drive C over an archive (and DBGL the same when it unpacks) */
	public static File driveDir(File changes, char drive) {
		return new File(changes, Character.toUpperCase(drive) + "_DRIVE");
	}

	public static File workRoot() {
		return new File(new File(FileLocationService.getInstance().getDataDir(), DIR), "work");
	}

	/** an archive unpacked for one start: its work folder and the files as they were when DOSBox started */
	public record Unpacked(char drive, File archive, File work, File changes, Map<String, long[]> before) {
	}

	/**
	 * Change the mounts of archives in the copy of the configuration for one start (see above).
	 *
	 * @param copy the copy DOSBox gets (its mounts are changed)
	 * @return the archives unpacked, for {@link #finish(List, java.io.PrintStream)} when DOSBox has ended
	 */
	public static List<Unpacked> prepare(TemplateProfileBase copy, DosboxVersion v, int profileId) throws IOException {
		Way way = wayFor(v);
		List<Unpacked> unpacked = new ArrayList<>();
		if (way == Way.AS_IS)
			return unpacked;
		if (way == Way.UNPACK)
			finishLeftOvers(profileId, System.err);
		Autoexec autoexec = copy.getConfiguration().getAutoexec();
		List<Mount> mounts = new ArrayList<>();
		for (Mount m: autoexec.getMountingpoints()) {
			if (!isArchiveMount(m)) {
				mounts.add(m);
				continue;
			}
			File archive = archiveOf(m);
			File changes = changesDir(m, profileId);
			if (way == Way.DOSBOX_X) {
				PhysFsMount read = MountFactory.createPhysFsMount(m.getDriveAsString(), archive.getPath(), null, m.getMountAs(), m.getLabel());
				mounts.add(read);
				Files.createDirectories(changes.toPath());
				mounts.add(MountFactory.createOverlayMount(m.getDriveAsString(), changes.getPath(), "overlay", null, null, null, null));
			} else {
				Unpacked u = unpack(m.getDrive(), archive, changes, profileId);
				unpacked.add(u);
				DirMount dir = MountFactory.createDirMount(m.getDriveAsString(), u.work().getPath(), m.getMountAs(), m.getLabel(), null, null, null);
				mounts.add(dir);
			}
		}
		autoexec.setMountingpoints(mounts);
		return unpacked;
	}

	/** unpack the archive into a new work folder and put what the game wrote before over it */
	public static Unpacked unpack(char drive, File archive, File changes, int profileId) throws IOException {
		if (!archive.getName().toLowerCase(Locale.ROOT).endsWith(".zip"))
			throw new IOException(org.dbgl.service.TextService.getInstance().get("general.error.zipstart7z", new Object[] {archive.getPath()}));
		File work = new File(workRoot(), profileId + "-" + Character.toUpperCase(drive) + "-" + System.currentTimeMillis());
		Path base = work.toPath().toAbsolutePath().normalize();
		Files.createDirectories(base);
		try (ZipFile zip = new ZipFile(archive)) {
			for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
				ZipEntry entry = e.nextElement();
				Path target = base.resolve(entry.getName().replace('\\', '/')).normalize();
				if (!target.startsWith(base) || target.equals(base))
					continue; // nothing outside the work folder ("../")
				if (entry.isDirectory()) {
					Files.createDirectories(target);
					continue;
				}
				Files.createDirectories(target.getParent());
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				}
				if (entry.getTime() > 0)
					target.toFile().setLastModified(entry.getTime());
			}
		} catch (IOException | RuntimeException e) {
			org.apache.commons.io.FileUtils.deleteQuietly(work);
			throw e;
		}
		File written = driveDir(changes, drive);
		if (written.isDirectory())
			org.apache.commons.io.FileUtils.copyDirectory(written, work, true);
		File marker = new File(work.getParentFile(), work.getName() + ".txt"); // what the folder is, in case DBGL ends first
		Files.writeString(marker.toPath(), changes.getPath() + "\n" + Character.toUpperCase(drive) + "\n");
		return new Unpacked(Character.toUpperCase(drive), archive, work, changes, snapshot(work));
	}

	/** every file of the folder with its size and time */
	static Map<String, long[]> snapshot(File dir) throws IOException {
		Map<String, long[]> result = new HashMap<>();
		Path base = dir.toPath();
		try (Stream<Path> walk = Files.walk(base)) {
			for (Path f: walk.filter(Files::isRegularFile).toList())
				result.put(base.relativize(f).toString().replace('\\', '/'), new long[] {Files.size(f), Files.getLastModifiedTime(f).toMillis()});
		}
		return result;
	}

	/**
	 * After DOSBox: what is new or changed in the work folders goes to the folder of what the game writes, then the work
	 * folders are deleted.
	 *
	 * @return how many files were kept
	 */
	public static int finish(List<Unpacked> unpacked, java.io.PrintStream log) {
		int kept = 0;
		for (Unpacked u: unpacked) {
			try {
				kept += keepChanges(u.work(), u.before(), driveDir(u.changes(), u.drive()));
				org.apache.commons.io.FileUtils.deleteDirectory(u.work());
				Files.deleteIfExists(new File(u.work().getParentFile(), u.work().getName() + ".txt").toPath());
			} catch (IOException e) {
				if (log != null)
					log.println("ZIP: " + u.work() + ": " + e); // the work folder stays; the next start takes over what is in it
			}
		}
		return kept;
	}

	static int keepChanges(File work, Map<String, long[]> before, File to) throws IOException {
		int kept = 0;
		Map<String, long[]> now = snapshot(work);
		for (Map.Entry<String, long[]> e: now.entrySet()) {
			long[] was = before == null ? null: before.get(e.getKey());
			if ((was != null) && (was[0] == e.getValue()[0]) && (was[1] == e.getValue()[1]))
				continue;
			File target = new File(to, e.getKey());
			Files.createDirectories(target.getParentFile().toPath());
			Files.copy(new File(work, e.getKey()).toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			kept++;
		}
		return kept;
	}

	/**
	 * Work folders of a profile left by a start whose end DBGL did not see (DBGL closed or crashed while DOSBox ran): as the
	 * file times of the unpacked files are those of the archive, every file newer than its entry in the archive is kept.
	 * Without the archive at hand (only the marker), files are kept when they are newer than the folder.
	 */
	static void finishLeftOvers(int profileId, java.io.PrintStream log) {
		File[] markers = workRoot().listFiles((d, n) -> n.startsWith(profileId + "-") && n.endsWith(".txt"));
		if (markers == null)
			return;
		for (File marker: markers) {
			File work = new File(marker.getParentFile(), StringUtils.removeEnd(marker.getName(), ".txt"));
			try {
				List<String> lines = Files.readAllLines(marker.toPath());
				if ((lines.size() >= 2) && work.isDirectory()) {
					long created = Long.parseLong(StringUtils.substringAfterLast(work.getName(), "-"));
					Map<String, long[]> now = snapshot(work), before = new HashMap<>();
					now.forEach((k, v) -> {
						if (v[1] < created)
							before.put(k, v); // older than the start: as unpacked
					});
					keepChanges(work, before, driveDir(new File(lines.get(0)), lines.get(1).charAt(0)));
				}
				org.apache.commons.io.FileUtils.deleteDirectory(work);
				Files.deleteIfExists(marker.toPath());
			} catch (IOException | RuntimeException e) {
				if (log != null)
					log.println("ZIP: " + work + ": " + e);
			}
		}
	}
}
