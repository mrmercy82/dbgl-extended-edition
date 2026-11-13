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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FileUtils;
import org.dbgl.model.FileLocation;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.ImageMount;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.service.DatabaseService;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.SettingsService;


/**
 * Alpha 48: a ZIP file with a DBGL for a USB stick, with only the chosen profiles: the program, its settings and templates, a
 * copy of the database without the other profiles, their configurations, screenshots and games, the DOSBox versions they use,
 * and the file "portable" (all data in the folder of DBGL). Games outside the DOSroot go into the DOSroot of the stick (its
 * folder name), DOSBox versions outside the folder of DBGL into "dosbox"; their paths are changed in the copy, so a drive
 * letter does not matter there. Optionally with a Java runtime (Eclipse Temurin, from Adoptium) in "jre", which dbgl.cmd uses.
 * Nothing of this DBGL itself is changed.
 */
public final class PortableExport {

	/**
	 * what goes into the ZIP file
	 *
	 * @param all all profiles: the games of the others are left out of the program folder (e.g. a folder "Spiele" next to DBGL)
	 * @param excluded parts of DBGL (from {@link #parts()}) that are not wanted, e.g. a folder of backups
	 */
	public record Options(List<Profile> profiles, List<Profile> all, File zip, boolean java, Set<File> excluded) {
		public Options(List<Profile> profiles, List<Profile> all, File zip, boolean java) {
			this(profiles, all, zip, java, Set.of());
		}
	}

	/** a profile in the ZIP file, with the size of its games and screenshots */
	public record Transferred(String title, int id, long bytes) {
	}

	/** what was done; notes: what could not be taken (e.g. a DOSBox version in a folder DBGL cannot place) */
	public record Result(int profiles, int files, long bytes, List<String> notes, List<Transferred> transferred) {
	}

	/**
	 * a part of DBGL at the top of its folder (or of its data folder): a folder, or all files there together (file null).
	 *
	 * @param required DBGL does not run without it
	 * @param suggested ticked at first (not: backups, ZIP files)
	 */
	public record Part(File file, String name, long bytes, boolean required, boolean suggested) {
	}

	/** what a profile needs of the disk: a folder (whole) or single files (images with their tracks) */
	private record Need(File root, File path, boolean dir, Set<File> files) {
	}

	/** progress: done of total (bytes) and what is being done */
	public interface Progress {
		void update(long done, long total, String what);
	}

	static final String ROOT = "DBGL/";
	static final Set<String> DATA_DIRS_CHOSEN = Set.of("db", "profiles", "captures", "dosroot", "export");

	private final Options options_;
	private final Progress progress_;
	private final BooleanSupplier cancelled_;
	private final File program_, data_, dosroot_;
	private final Map<String, File> entries_ = new LinkedHashMap<>(); // path in the ZIP file -> file here
	private final Map<String, byte[]> generated_ = new LinkedHashMap<>(); // path in the ZIP file -> content
	private final List<String> notes_ = new ArrayList<>();
	private long done_, total_;

	public PortableExport(Options options, Progress progress, BooleanSupplier cancelled) throws IOException {
		options_ = options;
		progress_ = progress;
		cancelled_ = cancelled;
		program_ = new File(".").getCanonicalFile();
		data_ = FileLocationService.getInstance().getDataDir().getCanonicalFile();
		dosroot_ = FileLocationService.getInstance().getDosroot().getCanonicalFile();
	}

	public Result run() throws IOException, SQLException, InterruptedException {
		List<Profile> profiles = options_.profiles();
		Set<Integer> ids = new LinkedHashSet<>();
		profiles.forEach(p -> ids.add(p.getId()));

		// the program, and the data that is the same for all profiles, without the parts not wanted; without the games of any
		// profile (e.g. a folder "Spiele" next to DBGL): only those of the chosen ones come along, below
		java.util.function.Predicate<File> common = commonFiles();
		java.util.function.Predicate<File> wanted = f -> options_.excluded().stream().noneMatch(x -> f.equals(x) || inside(x, f));
		addTree(program_, ROOT, f -> common.test(f) && wanted.test(f));
		if (!program_.equals(data_))
			addTree(data_, ROOT, f -> common.test(f) && wanted.test(f));
		generated_.put(ROOT + "portable", "DBGL Extended Edition: all data next to the program (portable)\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		// the games: in the DOSroot as they are; outside of it into the DOSroot of the stick, a folder each for the topmost folders
		// (a folder mounted as C: and one in it as D: are copied once, as C:)
		Map<Integer, List<Need>> needs = new LinkedHashMap<>();
		for (Profile p: profiles)
			needs.put(p.getId(), needs(p));
		Map<File, String> roots = outsideRoots(needs.values());
		Set<File> copiedWhole = new LinkedHashSet<>();
		for (List<Need> list: needs.values())
			for (Need n: list)
				if (n.dir() && n.path().equals(n.root()))
					copiedWhole.add(n.root());
		for (List<Need> list: needs.values()) {
			for (Need n: list) {
				String prefix;
				File base;
				if (n.root() == null) {
					prefix = ROOT + "dosroot/";
					base = dosroot_;
				} else {
					File top = topRoot(roots, n.root());
					prefix = ROOT + "dosroot/" + roots.get(top) + "/";
					base = top;
					if (copiedWhole.contains(top) && !n.path().equals(top))
						continue; // in it anyway
				}
				if (n.dir())
					addPath(n.path(), n.path().equals(base) ? prefix: prefix + relative(base, n.path()));
				else
					for (File f: n.files())
						addPath(f, prefix + relative(base, f));
			}
		}

		// the profiles: their files (with the changed paths), screenshots
		Map<Integer, Profile> migrated = new LinkedHashMap<>();
		List<Transferred> transferred = new ArrayList<>();
		for (Profile p: profiles) {
			for (Need n: needs.get(p.getId()))
				if (!n.dir() && n.files().isEmpty())
					notes_.add(p.getTitle() + ": " + n.path().getPath());
			// the paths may be written in another case than the folder really has (D:\SPIELE for D:\Spiele, Windows does not
			// mind): each spelling found is moved (test 13: "mount d D:\SPIELE\...\congo.iso" stayed as it was)
			for (Map.Entry<File, String> moved: roots.entrySet())
				for (String spelling: spellings(p, moved.getKey()))
					p.migrate(new FileLocation(spelling, FileLocationService.standard()), new FileLocation(moved.getValue(), FileLocationService.getInstance().dosrootRelative()));
			relativeMounts(p);
			transferred.add(new Transferred(p.getTitle(), p.getId(), sizeOf(needs.get(p.getId())) + sizeOfQuietly(p.getCanonicalCaptures())));
			File conf = p.getConfigurationCanonicalFile();
			generated_.put(ROOT + zipPathInData(conf, "profiles"), p.configurationBytesToSave());
			File captures = p.getCanonicalCaptures();
			if (captures.isDirectory())
				addPath(captures, ROOT + zipPathInData(captures, "captures"));
			File mapper = p.getCustomMapperFile();
			if (mapper != null && mapper.isFile())
				addPath(mapper, ROOT + zipPathInData(mapper, "profiles"));
			migrated.put(p.getId(), p);
			check();
		}

		// the DOSBox versions of these profiles: outside the folders of DBGL into "dosbox"
		Map<Integer, String[]> versionPaths = new LinkedHashMap<>();
		Set<Integer> versionIds = new LinkedHashSet<>();
		for (Profile p: profiles)
			if (versionIds.add(p.getDosboxVersion().getId()))
				placeDosbox(p.getDosboxVersion(), versionPaths);

		// the database: a copy without the other profiles, with the changed paths
		File tmp = Files.createTempDirectory("dbgl-portable").toFile();
		try {
			copyDatabase(tmp, ids, migrated, versionPaths);
			File[] dbFiles = tmp.listFiles();
			if (dbFiles != null)
				for (File f: dbFiles)
					if (f.isFile() && !f.getName().endsWith(".lck"))
						entries_.put(ROOT + "db/" + f.getName(), f);

			// the Java runtime, if wanted
			File jre = null;
			if (options_.java()) {
				progress_.update(0, 1, "java");
				jre = downloadJava(tmp);
			}

			total_ = 0;
			for (File f: entries_.values())
				total_ += f.length();
			if (jre != null)
				total_ += jre.length();
			writeZip(jre);
			return new Result(profiles.size(), entries_.size() + generated_.size(), total_, notes_, transferred);
		} finally {
			FileUtils.deleteQuietly(tmp);
		}
	}

	/** absolute mount paths into the DOSroot become relative, so the stick works with any drive letter */
	private void relativeMounts(Profile p) {
		for (Mount m: p.getConfiguration().getAutoexec().getMountingpoints()) {
			// the line as read is written again while the mount is unchanged; a path read absolute stays so even if DBGL
			// already sees it as relative, hence written anew
			if (m instanceof DirMount dm) {
				String rel = relativeToDosroot(dm.getCanonicalPath());
				if (rel != null) {
					dm.setPath(rel);
					m.rememberOriginal(null);
				}
			} else if (m instanceof ImageMount im) {
				File[] imgs = im.getCanonicalImgPaths();
				String[] locs = im.getImgPathStrings();
				boolean changed = false;
				for (int i = 0; i < imgs.length; i++) {
					String rel = relativeToDosroot(imgs[i]);
					if (rel != null) {
						locs[i] = rel;
						changed = true;
					}
				}
				if (changed) {
					im.setImgPaths(locs);
					m.rememberOriginal(null);
				}
			}
		}
	}

	private String relativeToDosroot(File f) {
		if (f == null || !f.isAbsolute())
			return null;
		try {
			File c = f.getCanonicalFile();
			if (!inside(dosroot_, c))
				return null;
			return c.equals(dosroot_) ? ".": dosroot_.toPath().relativize(c.toPath()).toString();
		} catch (IOException e) {
			return null;
		}
	}

	/** the ways a folder is written in the paths of a profile (mounts, programs, links), the folder itself first */
	private static Set<String> spellings(Profile p, File root) {
		Set<String> result = new LinkedHashSet<>();
		result.add(root.getPath());
		List<String> paths = new ArrayList<>();
		for (Mount m: p.getNettoMountingPoints()) {
			if (m instanceof ImageMount im)
				paths.addAll(List.of(im.getImgPathStrings()));
			else if (m.getPathString() != null)
				paths.add(m.getPathString());
		}
		paths.add(p.getSetupString());
		paths.addAll(List.of(p.getAltExeStrings()));
		for (int i = 0; i < 8; i++)
			paths.add(p.getLinkDestination(i));
		try {
			paths.add(p.getConfiguration().getAutoexec().getMain());
		} catch (RuntimeException e) {
			// no main program
		}
		for (String s: paths) {
			if (org.apache.commons.lang3.StringUtils.isBlank(s))
				continue;
			try {
				File raw = new File(s);
				if (!raw.isAbsolute())
					continue;
				File c = raw.getCanonicalFile();
				if (!c.equals(root) && !inside(root, c))
					continue;
				String abs = raw.getAbsolutePath(), rel = c.equals(root) ? "": root.toPath().relativize(c.toPath()).toString();
				String prefix = rel.isEmpty() ? abs: abs.length() > rel.length() ? abs.substring(0, abs.length() - rel.length() - 1): "";
				if (prefix.equalsIgnoreCase(root.getPath()))
					result.add(prefix);
			} catch (IOException | RuntimeException e) {
				// that one not
			}
		}
		return result;
	}

	/** what a profile mounts: folders, and image files with what belongs to them (tracks of a CUE file, same name) */
	private List<Need> needs(Profile p) throws IOException {
		p.resetAndLoadConfiguration();
		List<Need> result = new ArrayList<>();
		for (Mount m: p.getNettoMountingPoints()) {
			if (m instanceof DirMount dm) {
				File f = dm.getCanonicalPath().getCanonicalFile();
				if (f.isFile()) // "mount d game.iso -t cdrom": DOSBox Staging mounts an image this way too
					result.add(new Need(inside(dosroot_, f) ? null: f.getParentFile(), f, false, imageFiles(f)));
				else if (!f.isDirectory())
					result.add(new Need(null, f, false, Set.of()));
				else
					result.add(new Need(inside(dosroot_, f) ? null: f, f, true, Set.of()));
			} else if (m instanceof ImageMount im) {
				for (File img: im.getCanonicalImgPaths()) {
					File f = img.getCanonicalFile();
					result.add(new Need(inside(dosroot_, f) ? null: f.getParentFile(), f, false, imageFiles(f)));
				}
			}
		}
		return result;
	}

	/** an image with the files that belong to it: those a CUE file names, and those with the same name (e.g. .bin, .sub, .mdf) */
	public static Set<File> imageFiles(File img) {
		Set<File> files = new LinkedHashSet<>();
		if (!img.isFile())
			return files;
		files.add(img);
		File dir = img.getParentFile();
		String name = img.getName(), base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')): name;
		File[] same = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).startsWith(base.toLowerCase(Locale.ROOT) + "."));
		if (same != null)
			files.addAll(List.of(same));
		if (name.toLowerCase(Locale.ROOT).endsWith(".cue")) {
			try {
				java.util.regex.Pattern line = java.util.regex.Pattern.compile("^\\s*FILE\\s+\"?([^\"]+?)\"?\\s+\\w+\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE);
				for (String l: Files.readAllLines(img.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1)) {
					java.util.regex.Matcher m = line.matcher(l);
					if (m.matches()) {
						File track = new File(dir, m.group(1).replace('\\', File.separatorChar)).getCanonicalFile();
						if (track.isFile() && inside(dir, track))
							files.add(track);
					}
				}
			} catch (IOException | RuntimeException e) {
				// the image itself then
			}
		}
		return files;
	}

	/** the topmost folders outside the DOSroot that the profiles need, with their folder in the DOSroot of the stick */
	private Map<File, String> outsideRoots(java.util.Collection<List<Need>> needs) {
		Set<String> inDosroot = new java.util.HashSet<>();
		String[] names = dosroot_.list();
		if (names != null)
			for (String n: names)
				inDosroot.add(n.toLowerCase(Locale.ROOT));
		List<File> candidates = new ArrayList<>();
		for (List<Need> list: needs)
			for (Need n: list)
				if (n.root() != null && !candidates.contains(n.root()))
					candidates.add(n.root());
		candidates.sort(java.util.Comparator.comparingInt(f -> f.getPath().length()));
		Map<File, String> roots = new LinkedHashMap<>();
		for (File c: candidates)
			if (roots.keySet().stream().noneMatch(r -> inside(r, c)))
				roots.put(c, uniqueFolder(roots, inDosroot, c.getName()));
		return roots;
	}

	private static File topRoot(Map<File, String> roots, File f) {
		return roots.keySet().stream().filter(r -> inside(r, f)).findFirst().orElse(f);
	}

	private static long sizeOf(List<Need> needs) {
		long size = 0;
		Set<File> seen = new LinkedHashSet<>();
		List<File> dirs = new ArrayList<>();
		for (Need n: needs)
			if (n.dir())
				dirs.add(n.path());
		dirs.sort(java.util.Comparator.comparingInt(f -> f.getPath().length()));
		List<File> top = new ArrayList<>();
		for (File d: dirs)
			if (top.stream().noneMatch(t -> inside(t, d)))
				top.add(d);
		for (File d: top)
			size += sizeOfQuietly(d);
		for (Need n: needs)
			for (File f: n.files())
				if (top.stream().noneMatch(t -> inside(t, f)) && seen.add(f))
					size += f.length();
		return size;
	}

	static long sizeOfQuietly(File f) {
		try {
			return f == null || !f.exists() ? 0: f.isDirectory() ? FileUtils.sizeOfDirectory(f): f.length();
		} catch (RuntimeException e) {
			return 0;
		}
	}

	/** how much the database takes (the copy in the ZIP file has only the chosen profiles: it is at most this) */
	public long databaseSize() {
		try {
			String conn = SettingsService.getInstance().getValue("database", "connectionstring");
			int start = conn.indexOf("file:") + 5, end = conn.indexOf(';', start);
			File dbFile = new File(conn.substring(start, end < 0 ? conn.length(): end));
			if (!dbFile.isAbsolute())
				dbFile = new File(data_, dbFile.getPath());
			File db = dbFile.getCanonicalFile();
			File[] files = db.getParentFile().listFiles(f -> f.isFile() && f.getName().startsWith(db.getName() + "."));
			long size = 0;
			if (files != null)
				for (File f: files)
					size += f.length();
			return size;
		} catch (IOException | RuntimeException e) {
			return 0;
		}
	}

	/** how much a profile takes on the stick: its games and screenshots (for the assistant, before the ZIP file is made) */
	public long profileSize(Profile p) {
		try {
			return sizeOf(needs(p)) + sizeOfQuietly(p.getCanonicalCaptures());
		} catch (IOException | RuntimeException e) {
			return 0;
		}
	}

	/** how much the games of the chosen profiles take together (a game several of them use counted once) */
	public long gamesSize(List<Profile> profiles) {
		List<Need> all = new ArrayList<>();
		long captures = 0;
		for (Profile p: profiles) {
			try {
				all.addAll(needs(p));
				captures += sizeOfQuietly(p.getCanonicalCaptures());
			} catch (IOException | RuntimeException e) {
				// without it
			}
		}
		return sizeOf(all) + captures;
	}

	/** files of DBGL that go into the ZIP file for all profiles: not the data of the profiles, no games, no ZIP files at the top */
	private java.util.function.Predicate<File> commonFiles() {
		List<File> games = new ArrayList<>();
		for (Profile p: options_.all()) {
			try {
				for (Need n: needs(p))
					if (n.root() != null && !n.root().equals(program_) && !n.root().equals(data_))
						games.add(n.dir() ? n.path(): n.root());
			} catch (IOException | RuntimeException e) {
				// that profile then just has no games to leave out
			}
		}
		return f -> !(isChosenData(data_, f)) && !(program_.equals(data_) && isChosenData(program_, f)) && !isZipAtTop(program_, f) && !isZipAtTop(data_, f)
				&& games.stream().noneMatch(g -> g.equals(f) || inside(g, f));
	}

	/**
	 * the parts of DBGL to choose from in the assistant: the folders at the top of its folder (and of the data folder), and all
	 * files there together; without the data of the profiles and games (they come with the chosen profiles)
	 */
	public List<Part> parts() {
		java.util.function.Predicate<File> common = commonFiles();
		List<Part> parts = new ArrayList<>();
		Set<String> required = Set.of("lib", "plugins", "xsl", "templates", "dosboxconf");
		// folders with a DOSBox version or its configuration: needed as well
		List<File> dosbox = new ArrayList<>();
		for (Profile p: options_.all()) {
			try {
				DosboxVersion v = p.getDosboxVersion();
				if (v != null) {
					dosbox.add(v.getCanonicalPath().getCanonicalFile());
					if (v.getConfigurationCanonicalFile() != null)
						dosbox.add(v.getConfigurationCanonicalFile().getCanonicalFile());
				}
			} catch (IOException | RuntimeException e) {
				// without it
			}
		}
		for (File base: program_.equals(data_) ? List.of(program_): List.of(program_, data_)) {
			File[] children = base.listFiles();
			if (children == null)
				continue;
			java.util.Arrays.sort(children, java.util.Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
			long files = 0;
			List<String> fileNames = new ArrayList<>();
			for (File c: children) {
				if (!common.test(c) || Files.isSymbolicLink(c.toPath()))
					continue;
				if (c.isFile()) {
					files += c.length();
					fileNames.add(c.getName());
					continue;
				}
				long size = 0;
				try (var walk = Files.walk(c.toPath())) {
					for (Path p: (Iterable<Path>)walk::iterator)
						if (Files.isRegularFile(p) && common.test(p.toFile()))
							size += p.toFile().length();
				} catch (IOException | RuntimeException e) {
					// as far as it went
				}
				if (size == 0 && !required.contains(c.getName().toLowerCase(Locale.ROOT)))
					continue; // e.g. a folder with only games of profiles
				String lower = c.getName().toLowerCase(Locale.ROOT);
				boolean needed = required.contains(lower) || dosbox.stream().anyMatch(d -> d.equals(c) || inside(c, d));
				parts.add(new Part(c, c.getName() + "/", size, needed, !lower.contains("backup")));
			}
			if (!fileNames.isEmpty())
				parts.add(0, new Part(null, String.join(", ", fileNames), files, true, true));
		}
		return parts;
	}


	private void check() throws InterruptedException {
		if (cancelled_.getAsBoolean())
			throw new InterruptedException();
	}

	/** folders of the data that are taken only for the chosen profiles (and the database is a copy) */
	private boolean isChosenData(File base, File f) {
		Path rel = base.toPath().relativize(f.toPath());
		if (rel.getNameCount() == 0)
			return false;
		String top = rel.getName(0).toString().toLowerCase(Locale.ROOT);
		return DATA_DIRS_CHOSEN.contains(top) || (options_.java() && top.equals("jre")); // a Java runtime there: replaced by the new one
	}

	/** a ZIP file at the top (e.g. an earlier one of these, or a backup) */
	private static boolean isZipAtTop(File base, File f) {
		return f.getParentFile().equals(base) && f.getName().toLowerCase(Locale.ROOT).endsWith(".zip");
	}

	static boolean inside(File dir, File f) {
		return f.toPath().startsWith(dir.toPath());
	}

	private static String relative(File base, File f) {
		return base.toPath().relativize(f.toPath()).toString().replace('\\', '/');
	}

	/** a file of the data in the ZIP file; outside the data (somewhere else on this computer) into the folder given */
	private String zipPathInData(File f, String otherwise) {
		try {
			File c = f.getCanonicalFile();
			if (inside(data_, c))
				return relative(data_, c);
		} catch (IOException e) {
			// below
		}
		return otherwise + "/" + f.getName();
	}

	/** a folder name not used yet, also not in the DOSroot already (not case sensitive, as on Windows) */
	private static String uniqueFolder(Map<?, String> used, Set<String> inDosroot, String name) {
		String base = name.isBlank() ? "games": name, folder = base;
		for (int i = 2; taken(used, inDosroot, folder); i++)
			folder = base + " (" + i + ")";
		return folder;
	}

	private static boolean taken(Map<?, String> used, Set<String> inDosroot, String folder) {
		String lower = folder.toLowerCase(Locale.ROOT);
		return inDosroot.contains(lower) || used.values().stream().anyMatch(v -> v.toLowerCase(Locale.ROOT).equals(lower));
	}

	private void addTree(File dir, String zipDir, java.util.function.Predicate<File> take) throws IOException {
		try (var walk = Files.walk(dir.toPath())) {
			for (Path p: (Iterable<Path>)walk::iterator) {
				File f = p.toFile();
				if (f.isFile() && take.test(f) && !Files.isSymbolicLink(p))
					entries_.put(zipDir + relative(dir, f), f);
			}
		}
	}

	private void addPath(File f, String zipPath) throws IOException {
		if (f.isDirectory())
			addTree(f, zipPath.endsWith("/") ? zipPath: zipPath + "/", x -> true);
		else if (f.isFile())
			entries_.put(zipPath, f);
	}

	/** a DOSBox version: where it is in the ZIP file, and its paths there (PATH, EXEFILE, CONFFILE) if they change */
	private void placeDosbox(DosboxVersion v, Map<Integer, String[]> versionPaths) throws IOException {
		File dir = v.getCanonicalPath().getCanonicalFile();
		if (inside(program_, dir) || inside(data_, dir)) {
			File conf = v.getConfigurationCanonicalFile();
			if (conf != null && conf.isFile() && !inside(program_, conf.getCanonicalFile()) && !inside(data_, conf.getCanonicalFile()))
				notes_.add(v.getTitle() + ": " + conf.getPath());
			return; // in the ZIP file with the program
		}
		File dosboxDir = FileLocationService.getInstance().getDosboxDir().getCanonicalFile();
		String dosboxDirInZip = inside(data_, dosboxDir) ? relative(data_, dosboxDir): inside(program_, dosboxDir) ? relative(program_, dosboxDir): null;
		if (dosboxDirInZip == null || !dir.isDirectory()) {
			notes_.add(v.getTitle() + ": " + dir.getPath());
			return;
		}
		String folder = "dosbox/" + dir.getName();
		addPath(dir, ROOT + folder);
		Path from = Path.of(dosboxDirInZip.isEmpty() ? ".": dosboxDirInZip);
		String newPath = from.relativize(Path.of(folder)).toString();
		String exe = v.getExe().getPath().isBlank() ? "": Path.of(newPath, relative(dir, v.getCanonicalExe().getCanonicalFile())).toString();
		File conf = v.getConfigurationCanonicalFile().getCanonicalFile();
		String newConf;
		if (inside(dir, conf)) {
			newConf = Path.of(newPath, relative(dir, conf)).toString();
		} else {
			entries_.put(ROOT + folder + "/" + conf.getName(), conf);
			newConf = Path.of(newPath, conf.getName()).toString();
		}
		versionPaths.put(v.getId(), new String[] {newPath, exe, newConf});
	}

	/** the database: written by HSQLDB to disk first (CHECKPOINT), copied, then the copy without the other profiles */
	private void copyDatabase(File tmp, Set<Integer> ids, Map<Integer, Profile> migrated, Map<Integer, String[]> versionPaths) throws IOException, SQLException {
		String conn = SettingsService.getInstance().getValue("database", "connectionstring");
		int start = conn.indexOf("file:") + 5, end = conn.indexOf(';', start);
		File dbFile = new File(conn.substring(start, end < 0 ? conn.length(): end));
		if (!dbFile.isAbsolute())
			dbFile = new File(data_, dbFile.getPath());
		final File db = dbFile.getCanonicalFile();
		try (Connection live = DatabaseService.getInstance().getConnection(); Statement st = live.createStatement()) {
			st.execute("CHECKPOINT");
		}
		File[] files = db.getParentFile().listFiles(f -> f.isFile() && f.getName().startsWith(db.getName() + ".") && !f.getName().endsWith(".lck") && !f.getName().endsWith(".tmp"));
		if (files != null)
			for (File f: files)
				Files.copy(f.toPath(), new File(tmp, f.getName()).toPath());
		// the other databases of DBGL there (e.g. of MobyGames) as they are
		File[] others = db.getParentFile().listFiles(f -> f.isFile() && !f.getName().startsWith(db.getName() + ".") && !f.getName().endsWith(".lck"));
		if (others != null)
			for (File f: others)
				entries_.put(ROOT + "db/" + f.getName(), f);

		String in = ids.isEmpty() ? "-1": String.join(",", ids.stream().map(String::valueOf).toList());
		String url = "jdbc:hsqldb:file:" + new File(tmp, db.getName()).getPath() + ";shutdown=true;hsqldb.lock_file=false";
		try (Connection con = DriverManager.getConnection(url, "sa", ""); Statement st = con.createStatement()) {
			st.executeUpdate("DELETE FROM NATIVECOMMANDS WHERE GAME_ID IS NOT NULL AND GAME_ID NOT IN (" + in + ")");
			st.executeUpdate("DELETE FROM PLAYSESSIONS WHERE GAME_ID NOT IN (" + in + ")");
			st.executeUpdate("DELETE FROM LOG WHERE ENTITY_TYPE = " + org.dbgl.model.entity.LogEntry.EntityType.PROFILE.ordinal() + " AND ENTITY_ID NOT IN (" + in + ")");
			st.executeUpdate("DELETE FROM GAMES WHERE ID NOT IN (" + in + ")");
			try (PreparedStatement ps = con.prepareStatement("UPDATE GAMES SET LINK1 = ?, LINK2 = ?, LINK3 = ?, LINK4 = ?, LINK5 = ?, LINK6 = ?, LINK7 = ?, LINK8 = ?, SETUP = ?, ALT1 = ?, ALT2 = ? WHERE ID = ?")) {
				for (Profile p: migrated.values()) {
					for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++)
						ps.setString(i + 1, p.getLinks()[i].getDestination());
					ps.setString(9, p.getSetupString());
					ps.setString(10, p.getAltExeStrings()[0]);
					ps.setString(11, p.getAltExeStrings()[1]);
					ps.setInt(12, p.getId());
					ps.executeUpdate();
				}
			}
			try (PreparedStatement ps = con.prepareStatement("UPDATE DOSBOXVERSIONS SET PATH = ?, EXEFILE = ?, CONFFILE = ? WHERE ID = ?")) {
				for (Map.Entry<Integer, String[]> e: versionPaths.entrySet()) {
					ps.setString(1, e.getValue()[0]);
					ps.setString(2, e.getValue()[1]);
					ps.setString(3, e.getValue()[2]);
					ps.setInt(4, e.getKey());
					ps.executeUpdate();
				}
			}
			st.execute("SHUTDOWN COMPACT");
		}
	}

	/** the Java runtime for this kind of computer from Adoptium (Eclipse Temurin 21, a JRE), checked against its SHA-256 */
	File downloadJava(File tmp) throws IOException, InterruptedException {
		String os = SystemUtils.IS_WINDOWS ? "windows": SystemUtils.IS_OSX ? "mac": "linux";
		String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT).matches("aarch64|arm64") ? "aarch64": "x64";
		HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
		String api = "https://api.adoptium.net/v3/assets/latest/21/hotspot?os=" + os + "&architecture=" + arch + "&image_type=jre&vendor=eclipse";
		HttpResponse<String> info = http.send(HttpRequest.newBuilder(URI.create(api)).header("User-Agent", "DBGL Extended Edition").timeout(Duration.ofSeconds(30)).build(),
			HttpResponse.BodyHandlers.ofString());
		if (info.statusCode() != 200)
			throw new IOException("Adoptium: " + info.statusCode());
		String[] pkg = adoptiumPackage(info.body());
		if (pkg == null)
			throw new IOException(org.dbgl.service.TextService.getInstance().get("dialog.portable.java.none", new Object[] {os + " " + arch}));
		File archive = new File(tmp, "jre." + (pkg[0].endsWith(".zip") ? "zip": "tar.gz"));
		HttpResponse<InputStream> dl = http.send(HttpRequest.newBuilder(URI.create(pkg[0])).header("User-Agent", "DBGL Extended Edition").build(), HttpResponse.BodyHandlers.ofInputStream());
		if (dl.statusCode() != 200)
			throw new IOException("Adoptium: " + dl.statusCode());
		MessageDigest sha;
		try {
			sha = MessageDigest.getInstance("SHA-256");
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
		long size = dl.headers().firstValueAsLong("content-length").orElse(50_000_000L), got = 0;
		try (InputStream in = dl.body(); OutputStream out = Files.newOutputStream(archive.toPath())) {
			byte[] buf = new byte[1 << 16];
			for (int n; (n = in.read(buf)) > 0;) {
				out.write(buf, 0, n);
				sha.update(buf, 0, n);
				got += n;
				progress_.update(got, size, "java");
				check();
			}
		}
		String hex = java.util.HexFormat.of().formatHex(sha.digest());
		if (!hex.equalsIgnoreCase(pkg[1]))
			throw new IOException(org.dbgl.service.TextService.getInstance().get("dialog.portable.java.checksum"));
		return archive;
	}

	/**
	 * from the answer of the Adoptium API (a list of releases): the link of the package (ZIP or tar.gz, not the installer) and
	 * its SHA-256, both of the same package; null if there is none. Until alpha 48 test 11 the first checksum of the answer was
	 * taken: that of the installer, so the check failed.
	 */
	public static String[] adoptiumPackage(String json) {
		try {
			org.json.JSONArray releases = new org.json.JSONArray(json);
			for (int i = 0; i < releases.length(); i++) {
				org.json.JSONObject binary = releases.getJSONObject(i).optJSONObject("binary");
				org.json.JSONObject pkg = binary == null ? null: binary.optJSONObject("package");
				if (pkg == null)
					continue;
				String link = pkg.optString("link", ""), sum = pkg.optString("checksum", "");
				if (link.startsWith("https://") && (link.endsWith(".zip") || link.endsWith(".tar.gz")) && sum.matches("[0-9a-fA-F]{64}"))
					return new String[] {link, sum};
			}
		} catch (org.json.JSONException e) {
			// below
		}
		return null;
	}

	private void writeZip(File jre) throws IOException, InterruptedException {
		File part = new File(options_.zip().getPath() + ".part");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(part.toPath()))) {
			for (Map.Entry<String, byte[]> g: generated_.entrySet()) {
				zip.putNextEntry(new ZipEntry(g.getKey()));
				zip.write(g.getValue());
				zip.closeEntry();
			}
			byte[] buf = new byte[1 << 16];
			for (Map.Entry<String, File> e: entries_.entrySet()) {
				if (generated_.containsKey(e.getKey()))
					continue;
				ZipEntry entry = new ZipEntry(e.getKey());
				entry.setTime(e.getValue().lastModified());
				zip.putNextEntry(entry);
				try (InputStream in = Files.newInputStream(e.getValue().toPath())) {
					for (int n; (n = in.read(buf)) > 0;) {
						zip.write(buf, 0, n);
						done_ += n;
					}
				}
				zip.closeEntry();
				progress_.update(done_, total_, e.getKey());
				check();
			}
			if (jre != null && jre.getName().endsWith(".zip"))
				copyJre(jre, zip);
			else if (jre != null)
				notes_.add("Java: " + jre.getName()); // tar.gz (Linux, macOS): not unpacked, beside it
		} catch (IOException | InterruptedException | RuntimeException e) {
			Files.deleteIfExists(part.toPath());
			throw e;
		}
		Files.move(part.toPath(), options_.zip().toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	/** the runtime from its ZIP file into "jre" (its own top folder, e.g. jdk-21.0.5+11-jre, left out) */
	private void copyJre(File jre, ZipOutputStream zip) throws IOException {
		byte[] buf = new byte[1 << 16];
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jre.toPath()))) {
			for (ZipEntry e; (e = in.getNextEntry()) != null;) {
				String name = e.getName().replace('\\', '/');
				int slash = name.indexOf('/');
				if (slash < 0 || name.contains("../") || e.isDirectory())
					continue;
				zip.putNextEntry(new ZipEntry(ROOT + "jre/" + name.substring(slash + 1)));
				for (int n; (n = in.read(buf)) > 0;)
					zip.write(buf, 0, n);
				zip.closeEntry();
			}
		}
		done_ += jre.length();
		progress_.update(done_, total_, "jre");
	}
}
