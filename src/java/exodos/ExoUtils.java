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
package exodos;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.gui.interfaces.PreProgressNotifyable;
import org.dbgl.model.FileLocation;
import org.dbgl.model.Link;
import org.dbgl.model.SearchResult;
import org.dbgl.model.SearchResult.ResultType;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.ImageMount;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;
import org.dbgl.model.factory.DosboxVersionFactory;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.TextService;
import org.dbgl.util.ExecuteUtils;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.ShortFilenameUtils;
import org.dbgl.util.SystemUtils;
import org.dbgl.util.XmlUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


class ExoUtils {

	static final Charset CP437 = Charset.forName("CP437");
	
	private static final List<String> EXTRAFILES = Arrays.asList("rtf", "pdf", "doc", "xls", "htm", "html", "txt", "jpg", "png", "bmp", "gif", "flac", "wav", "ogg", "mp2", "mp3", "mp4", "mod", "s3m", "amf",
		"dsm", "mo3", "psm", "sfx", "voc", "xm");
	private static final Pattern IMG_FILENAME_PTRN = Pattern.compile("^(.*?)(\\.........\\-....\\-....\\-....\\-............)?\\-[0-9][0-9]( \\(.\\))?\\.(.*)$");
	private static final String[] DOS_EXECUTABLES = {".BAT", ".COM", ".EXE"};
	private static final List<String> IMG_EXTENSIONS = Arrays.asList("ISO", "CUE", "BIN", "IMG", "GOG", "IMA", "INST", "DSK", "VFD");
	private static final List<String> DOS_EXTENSIONS = Arrays.asList("BAT", "COM", "EXE");
	private static final List<String> BOOTER_EXTENSIONS = Arrays.asList("IMG", "JRC", "TC", "IMA");


	private ExoUtils() {
	}
	
	static List<ZipEntry> listEntries(File zipFile, boolean ignoreDirectories) {
		return listEntries(zipFile, x -> true, ignoreDirectories);
	}
	
	static List<ZipEntry> listEntries(File zipFile, Predicate<String> p, boolean ignoreDirectories) {
		try (ZipFile zipfile = new ZipFile(zipFile, CP437)) {
			return listEntries(zipfile, p, ignoreDirectories);
		} catch (IOException e) {
			e.printStackTrace();
			return List.of();
		}
	}

	static List<ZipEntry> listEntries(ZipFile zipFile, boolean ignoreDirectories) {
		return listEntries(zipFile, x -> true, ignoreDirectories);
	}
	
	private static List<ZipEntry> listEntries(ZipFile zipFile, Predicate<String> p, boolean ignoreDirectories) {
		List<ZipEntry> entries = new ArrayList<>();
		for (Enumeration<? extends ZipEntry> e = zipFile.entries(); e.hasMoreElements();) {
			try {
				ZipEntry entry = e.nextElement();
				if ((!ignoreDirectories || !entry.isDirectory()) && p.test(entry.getName()))
					entries.add(entry);
			} catch (IllegalArgumentException iae) {
				System.out.println("WARNING - Zip file [" + zipFile
						+ "] contains an entry with problematic characters in its filename");
			}
		}
		return entries;
	}
	
	static PreProgressNotifyable prog(String title, Collection<ZipEntry> zipEntries) {
		return new AsciiProgressBar(title, zipEntries.stream().mapToLong(x -> x.getSize()).sum());
	}
	
	static NodeList getGameNodes(final List<ZipEntry> xodosZipEntries, ExoContext ctx)
			throws SAXException, IOException, ParserConfigurationException, XPathExpressionException {
		ZipEntry msdosXmlEntry = xodosZipEntries.parallelStream().filter(x -> x.getName().equals(ctx.xoMetadataGamesXml())).findAny().orElse(null);
		
		try (ZipFile zipfile = new ZipFile(ctx.xoMetadataZipFile(), ExoUtils.CP437)) {
			InputStream is = ctx.launchboxGamesXmlIsComplete()
					? zipfile.getInputStream(msdosXmlEntry)
					: new SequenceInputStream(Collections.enumeration(Arrays.asList(
							new ByteArrayInputStream("<?xml version=\"1.0\" standalone=\"yes\"?>".getBytes(StandardCharsets.UTF_8)),
							new ByteArrayInputStream("<LaunchBox>".getBytes(StandardCharsets.UTF_8)),
							zipfile.getInputStream(msdosXmlEntry),
							new ByteArrayInputStream("</LaunchBox>".getBytes(StandardCharsets.UTF_8))
					  ))); 
			return (NodeList)XPathFactory.newInstance().newXPath()
				.evaluate("/LaunchBox/Game[ApplicationPath!='Setup eXoDOS.bat' and ApplicationPath!='Setup eXoDemoScene.bat' and ApplicationPath!='']",
					XmlUtils.getDocumentBuilder().parse(is), XPathConstants.NODESET);
		}
	}

	static List<String> determineTitles(List<String> titles, boolean favorites, NodeList gameNodes) {
		if (titles.isEmpty() && !favorites)
			return titles;
		
		List<String> result = new ArrayList<>();
		for (int i = 0; i < gameNodes.getLength(); i++) {
			String applicationPath = XmlUtils.getTextValue((Element)gameNodes.item(i), "ApplicationPath");
			String title = FilenameUtils.getBaseName(applicationPath).toLowerCase();
			File path = new File(FilenameUtils.separatorsToSystem(applicationPath)).getParentFile();
			String dir = path != null ? path.getName().toLowerCase(): StringUtils.EMPTY;
			boolean isFavorite = BooleanUtils.toBoolean(XmlUtils.getTextValue((Element)gameNodes.item(i), "Favorite"));
			
			if (StringUtils.isNotBlank(title) && ((favorites && isFavorite) || titles.contains(title) || titles.contains(dir)))
				result.add(title);
		}
		Collections.sort(result);
		return result;
	}
	
	static List<Integer> determineGameIndices(NodeList gameNodes) {
		Map<String, Integer> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (int i = 0; i < gameNodes.getLength(); i++) {
			String applicationPath = XmlUtils.getTextValue((Element)gameNodes.item(i), "ApplicationPath");
			String fullGameTitle = FilenameUtils.getBaseName(applicationPath);
			
			result.put(fullGameTitle, i);
		}
		return new ArrayList<>(result.values());
	}
	
	static DosboxVersion findDefaultDosboxVersion(boolean verboseOutput) {
		List<DosboxVersion> dbversionsList = null;
		try {

			DosboxVersionRepository dosboxRepo = new DosboxVersionRepository();
			dbversionsList = dosboxRepo.listAll();

			if (BaseRepository.findDefault(dbversionsList) == null) {
				SearchResult result = FileLocationService.getInstance().findDosbox();
				if (result.result_ == ResultType.COMPLETE) {
					new DosboxVersionRepository().add(result.dosbox_);
					dbversionsList = dosboxRepo.listAll();
				}
				if (BaseRepository.findDefault(dbversionsList) == null) {
					System.out.println("DOSBox installation could not be located. Please add a DOSBox Version in DBGL.");
					System.exit(1);
				}
			}

			if (verboseOutput)
				System.out.println("* Using DOSBox installation located in: [" + BaseRepository.findDefault(dbversionsList).getConfigurationCanonicalFile().getPath() + "]");
		} catch (SQLException e) {
			e.printStackTrace();
			System.exit(1);
		}

		return BaseRepository.findDefault(dbversionsList);
	}

	static Collection<ZipEntry> getUniqueImages(List<ZipEntry> imageEntries, String gameTitle) {
		return imageEntries.parallelStream().filter(x -> {
			Matcher matcher = IMG_FILENAME_PTRN.matcher(FilenameUtils.getName(x.getName()));
			return matcher.matches() && matcher.groupCount() == 4 && matcher.group(1).equalsIgnoreCase(gameTitle);
		}).collect(Collectors.toMap(ZipEntry::getCrc, Function.identity(), (entry1, entry2) -> entry1)).values();
	}
	
	static List<ZipReference> getGameCombinedExtrasV5(
			ZipFile xodosZipfile, final List<ZipEntry> xodosZipEntries,
			ZipFile dosZipfile, final List<ZipEntry> dosZipEntries,
			String gameTitle, String fullGameTitle, String gameDirName, ExoContext ctx) throws IOException {
		final Collection<ZipEntry> extras = dosZipEntries.parallelStream().filter(x ->
				x.getName().startsWith(ctx.xoMetadataExtras(gameDirName)) && EXTRAFILES.contains(FilenameUtils.getExtension(x.getName()).toLowerCase())
			).collect(Collectors.toMap(ZipEntry::getCrc, Function.identity(), (entry1, entry2) -> entry1)).values();
		final Collection<ZipEntry> manuals = xodosZipEntries.parallelStream().filter(x ->
				x.getName().startsWith(ctx.xoMetadataManuals()) && EXTRAFILES.contains(FilenameUtils.getExtension(x.getName()).toLowerCase())
				&& (FilenameUtils.getBaseName(x.getName()).equals(fullGameTitle) || FilenameUtils.getBaseName(x.getName()).equals(gameTitle))
			).collect(Collectors.toMap(ZipEntry::getCrc, Function.identity(), (entry1, entry2) -> entry1)).values();
		final Collection<ZipEntry> music = xodosZipEntries.parallelStream().filter(x -> 
				x.getName().startsWith(ctx.xoMetadataMusic()) && EXTRAFILES.contains(FilenameUtils.getExtension(x.getName()).toLowerCase())
				&& (FilenameUtils.getBaseName(x.getName()).equals(fullGameTitle) || FilenameUtils.getBaseName(x.getName()).equals(gameTitle))
			).collect(Collectors.toMap(ZipEntry::getCrc, Function.identity(), (entry1, entry2) -> entry1)).values();
		List<ZipReference> combinedEntries = new ArrayList<>();
		addToCollection(dosZipfile, extras, combinedEntries);
		addToCollection(xodosZipfile, manuals, combinedEntries);
		addToCollection(xodosZipfile, music, combinedEntries);
		return combinedEntries;
	}
	
	static List<ZipReference> getGameCombinedExtrasV6(String gameZipFilename, String gameDirName, ExoContext ctx) throws IOException {
		final File extraZipFile = ctx.gameExtrasZipFile(gameZipFilename);
		if (FilesUtils.isExistingFile(extraZipFile)) {
			try (ZipFile extraGameDataZipfile = new ZipFile(extraZipFile, CP437)) {
				final List<ZipEntry> all = listEntries(extraGameDataZipfile, true).parallelStream()
						.filter(x -> EXTRAFILES.contains(FilenameUtils.getExtension(x.getName()).toLowerCase())).toList();
				final List<ZipEntry> manuals = all.parallelStream().filter(x -> x.getName().startsWith(ctx.xoMetadataManuals())).toList();
				final List<ZipEntry> music = all.parallelStream().filter(x -> x.getName().startsWith(ctx.xoMetadataMusic())).toList();
				final List<ZipEntry> videos = all.parallelStream().filter(x -> x.getName().startsWith(ctx.xoMetadataVideos())).toList();
				final List<ZipEntry> extras = all.parallelStream().filter(x -> x.getName().startsWith(ctx.xoMetadataExtras(gameDirName))).toList();
				List<ZipReference> combinedEntries = new ArrayList<>();
				addToCollection(extraGameDataZipfile, manuals, combinedEntries);
				addToCollection(extraGameDataZipfile, music, combinedEntries);
				addToCollection(extraGameDataZipfile, videos, combinedEntries);
				addToCollection(extraGameDataZipfile, extras, combinedEntries);
				return combinedEntries;
			}
		}
		return List.of();
	}

	static Profile createProfile(Element gameNode, String fullGameTitle, String titleString, File gamePath, String gameDirName, DosboxVersion dosboxVersion, ZipEntry confEntry, ZipFile dosZipfile,
			String gameSrcZipfile, List<ZipEntry> gameZipEntries, List<ZipReference> gameExtraEntries, ExoContext ctx, boolean verboseOutput) throws IOException {
		HashSet<String> filesInZip = gameZipEntries.stream().filter(x -> !x.isDirectory()).map(x -> FilenameUtils.separatorsToSystem(x.getName())).collect(
			Collectors.toCollection(HashSet::new));

		String developerString = StringUtils.defaultString(XmlUtils.getTextValue(gameNode, "Developer"));
		String publisherString = StringUtils.defaultString(XmlUtils.getTextValue(gameNode, "Publisher"));
		String genreString = StringUtils.defaultString(XmlUtils.getTextValue(gameNode, "Genre"));
		String yearString = StringUtils.left(StringUtils.defaultString(XmlUtils.getTextValue(gameNode, "ReleaseDate")), 4);
		String statusString = StringUtils.EMPTY;
		String notesString = StringUtils.defaultString(XmlUtils.getTextValue(gameNode, "Notes"));
		String ratingString = XmlUtils.getTextValue(gameNode, "CommunityStarRating");
		boolean favorite = BooleanUtils.toBoolean(XmlUtils.getTextValue(gameNode, "Favorite"));
		String[] links = new String[Profile.NR_OF_LINK_DESTINATIONS];
		Arrays.fill(links, StringUtils.EMPTY);
		String[] linkTitles = new String[Profile.NR_OF_LINK_TITLES];
		Arrays.fill(linkTitles, StringUtils.EMPTY);
		String confPathAndFile = new File(gameDirName, FileLocationService.DOSBOX_CONF_STRING).getPath();

		List<Link> extraLinks = gameExtraEntries.parallelStream().map(x -> new Link(x.name_, FileLocationService.DOSROOT_DIR_STRING + gameDirName + '/' + ExoContext.EXTRAS_DIR + '/' + x.name_)).limit(
			Profile.NR_OF_LINK_DESTINATIONS).toList();

		IntStream.range(0, extraLinks.size()).forEach(l -> {
			links[l] = extraLinks.get(l).getDestination();
			linkTitles[l] = extraLinks.get(l).getTitle();
		});

		Profile profile = ProfileFactory.create(titleString, developerString, publisherString, genreString, yearString, statusString, notesString, favorite, links, linkTitles, ratingString,
			dosboxVersion, confPathAndFile);

		String warnings = profile.loadConfigurationData(TextService.getInstance(), StringUtils.join(IOUtils.readLines(dosZipfile.getInputStream(confEntry), StandardCharsets.UTF_8), StringUtils.LF),
			new File(gameSrcZipfile));
		if (StringUtils.isNotBlank(warnings)) {
			System.out.println(fullGameTitle + ": " + warnings);
		}

		// minor bit of clean up of the actual dosbox configuration
		if ("64".equals(profile.getConfiguration().getValue("dosbox", "memsize")))
			profile.getConfiguration().setValue("dosbox", "memsize", "63");
		if ("overlay".equals(profile.getConfiguration().getValue("sdl", "output")))
			profile.getConfiguration().removeValue("sdl", "output");

		Autoexec autoexec = profile.getConfiguration().getAutoexec();
		autoexec.migrate(new FileLocation(ctx.gameZipsDir().getName(), FileLocationService.getInstance().dosrootRelative()), FileLocationService.getInstance().getDosrootLocation());

		if (!fixupMainFileLocation(fullGameTitle, profile, gameSrcZipfile, filesInZip, verboseOutput)) {
			String main = autoexec.getGameMain();
			if (StringUtils.isNotEmpty(main))
				System.out.println(fullGameTitle + ": WARNING - Main file [" + main + "] not found inside [" + gameSrcZipfile + "]");
		}
		
		for (Mount m: profile.getNettoMountingPoints()) {
			if (m instanceof ImageMount) {
				fixupImageMount(fullGameTitle, gameSrcZipfile, filesInZip, autoexec, (ImageMount)m, verboseOutput);
			}
		}

		// Check for multiple root entries
		if (filesInZip.parallelStream().filter(x1 -> x1.indexOf(File.separatorChar) == -1).limit(2).count() == 2) {
			autoexec.setBaseDir(gamePath);
			if (verboseOutput)
				System.out.println(fullGameTitle + ": " + gameDirName + " is moved one directory level deeper");
		}

		autoexec.migrate(FileLocationService.getInstance().getDosrootLocation(), new FileLocation(gameDirName, FileLocationService.getInstance().dosrootRelative()));
		
		return profile;
	}

	static File fixupGamePath(String title, File gamePath, ExoContext ctx, boolean verboseOutput) {
		File gd = new File(ctx.xoMetadataGameDir(gamePath.getName())).getParentFile();
		File sd = FilesUtils.removeFirstFolder(gd);
		if (gamePath.getPath().startsWith(gd.getParent()) && !gamePath.getPath().startsWith(gd.getPath())) {
			File result = new File(gd, Strings.CS.removeStart(gamePath.getPath(), gd.getParent()));
			if (verboseOutput)
				System.out.println(title + ": WARNING - Game dir " + gamePath + " is corrected to " + result);
			return result;
		}
		if (gamePath.getPath().startsWith(sd.getPath())) {
			File result = new File(gd, Strings.CS.removeStart(gamePath.getPath(), sd.getPath()));
			if (verboseOutput)
				System.out.println(title + ": WARNING - Game dir " + gamePath + " is corrected to " + result);
			return result;
		}
		return gamePath;
	}
	
	private static boolean fixupMainFileLocation(String fullGameTitle, Profile profile, String zipFile, Set<String> list, boolean verboseOutput) {
		Autoexec autoexec = profile.getConfiguration().getAutoexec();

		String gameMain = FilenameUtils.separatorsToSystem(autoexec.getGameMain());
		
		if (gameMain.contains("@")) {
			String file = FilenameUtils.getName(gameMain);
			if (file.startsWith("@")) {
				gameMain = FilenameUtils.getPath(gameMain) + file.substring(1);
			}
		}
		
		if (StringUtils.isBlank(gameMain))
			return false;

		Mount mnt = null;
		String dosDir = null;
		int minLengthMount = Integer.MAX_VALUE;
		
		for (Mount mount: profile.getNettoMountingPoints()) {
			FileLocation f = new FileLocation(gameMain, FileLocationService.getInstance().dosrootRelative());
			File dosboxDir = mount.canBeUsedFor(f);
			if (dosboxDir != null && (dosboxDir.getPath().length() < minLengthMount)) {
				mnt = mount;
				dosDir = dosboxDir.getParent() == null ? StringUtils.EMPTY: dosboxDir.getParent();
				minLengthMount = dosboxDir.getPath().length();
			}
		}

		if (mnt instanceof DirMount) {
			Set<String> fileSet = FilenameUtils.getName(gameMain).contains("~") ? ShortFilenameUtils.convertToShortFileSet(list): list;
			String mntPath = FilenameUtils.normalize(((DirMount)mnt).getPath().getPath());
			dosDir = StringUtils.isBlank(mntPath) ? Strings.CS.removeStart(dosDir, File.separator): Strings.CS.prependIfMissing(dosDir, File.separator);
			File dosPath = new File(dosDir, FilenameUtils.getName(gameMain));
			File fullPath = new File(mntPath, dosPath.getPath());
			return fixupDirMount(fullGameTitle, fileSet, profile, autoexec, mntPath, dosDir, dosPath.getPath(), fullPath.getPath(), verboseOutput);
		} else if (mnt instanceof ImageMount) {
			return fixupImageMount(fullGameTitle, zipFile, list, autoexec, (ImageMount)mnt, verboseOutput);
		} else if (autoexec.isBooter()) {
			return fixupBooter(fullGameTitle, zipFile, list, autoexec, verboseOutput);
		}
		
		return false;
	}

	private static boolean fixupBooter(String title, String zipFile, Set<String> fileSet, Autoexec autoexec, boolean verbose) {
		fileSet = fileSet.stream().filter(x -> BOOTER_EXTENSIONS.contains(FilenameUtils.getExtension(x).toUpperCase())).sorted(new FilesUtils.FilenameComparator()).collect(
			Collectors.toCollection(LinkedHashSet::new));

		boolean result = true;
		if (!fixupBooterPart(title, fileSet, autoexec, FilenameUtils.normalize(autoexec.getImg(0)), autoexec::setImg, 0, verbose))
			result = false;
		for (int i = 1; i < Autoexec.BOOTER_IMAGES; i++) {
			if (StringUtils.isNotBlank(autoexec.getImg(i))) {
				if (!fixupBooterPart(title, fileSet, autoexec, FilenameUtils.normalize(autoexec.getImg(i)), autoexec::setImg, i, verbose))
					result = false;
			}
		}
		
		if (!result && verbose)
			System.out.println(title + ": WARNING - At least one file in [" + StringUtils.join(autoexec.getImgs()) + "] not found inside [" + zipFile + "]");
		
		return result;
	}
	
	private static boolean fixupBooterPart(String title, Set<String> fileSet, Autoexec autoexec, String path, BiConsumer<Integer, String> updateAutoexec, int idx, boolean verbose) {
		if (fileSet.contains(path))
			return true;
		
		String otherBooterPath = fileSet.stream().filter(x -> Strings.CI.endsWith(x, FilenameUtils.getName(path))).findFirst().orElse(null);
		if (otherBooterPath != null) {
			updateAutoexec.accept(idx, otherBooterPath);
			if (verbose)
				System.out.println(title + ": Booter [" + path + "] has wrong path, set to [" + otherBooterPath + "]");
			return true;
		}
		
		return false;
	}
	
	private static boolean fixupImageMount(String title, String zipFile, Set<String> fileSet, Autoexec autoexec, ImageMount mnt, boolean verbose) {
		fileSet = fileSet.stream().filter(x -> IMG_EXTENSIONS.contains(FilenameUtils.getExtension(x).toUpperCase())).sorted(new FilesUtils.FilenameComparator()).collect(
			Collectors.toCollection(LinkedHashSet::new));
		
		boolean result = true;
		for (File mntPath: mnt.getImgPaths()) {
			String normalizedMntPath = FilenameUtils.normalize(mntPath.getPath());
			
			if (!fixupImagemountPart(title, zipFile, fileSet, autoexec, mnt, normalizedMntPath, verbose))
				result = false;
		}
		
		if (!result && verbose)
			System.out.println(title + ": WARNING - At least one file in [" + mnt + "] not found inside [" + zipFile + "]");
		
		return result;
	}
	
	private static boolean fixupImagemountPart(String title, String zipFile, Set<String> fileSet, Autoexec autoexec, ImageMount mnt, String path, boolean verbose) {
		if (fileSet.contains(path))
			return true;

		String mntDifferentCasing = fileSet.stream().filter(x -> x.equalsIgnoreCase(path)).findFirst().orElse(null);
		if (mntDifferentCasing != null) {
			FileLocation f = new FileLocation(path, FileLocationService.getInstance().dosrootRelative());
			FileLocation t = new FileLocation(mntDifferentCasing, FileLocationService.getInstance().dosrootRelative());
			autoexec.migrate(f, t);
			if (verbose)
				System.out.println(title + ": Mount [" + mnt + "] has wrong casing, set to [" + mntDifferentCasing + "]");
			return true;
		}

		String otherMntPath = fileSet.stream().filter(x -> Strings.CI.endsWith(x, FilenameUtils.getName(path))).findFirst().orElse(null);
		if (otherMntPath != null) {
			FileLocation f = new FileLocation(path, FileLocationService.getInstance().dosrootRelative());
			FileLocation t = new FileLocation(otherMntPath, FileLocationService.getInstance().dosrootRelative());
			autoexec.migrate(f, t);
			if (verbose)
				System.out.println(title + ": Mount [" + mnt + "] has wrong path, set to [" + otherMntPath + "]");
			return true;
		}
		
		return false;
	}
	
	private static boolean fixupDirMount(String title, Set<String> fileSet, Profile profile, Autoexec autoexec, String mntPath, String dosDir, String dosPath, String path, boolean verbose) {
		List<String> exts = autoexec.isDos() ? DOS_EXTENSIONS: BOOTER_EXTENSIONS;
		fileSet = fileSet.stream().filter(x -> exts.contains(FilenameUtils.getExtension(x).toUpperCase())).sorted(new FilesUtils.FilenameComparator()).collect(
			Collectors.toCollection(LinkedHashSet::new));
		
		if (fileSet.contains(path))
			return true;
		
		String differentDosPathCasing = fileSet.stream().filter(x -> x.startsWith(mntPath)
				&& x.substring(mntPath.length()).equalsIgnoreCase(dosPath)).findFirst().orElse(null);
		if (differentDosPathCasing != null) {
			autoexec.setGameMain(differentDosPathCasing);
			if (verbose)
				System.out.println(title + ": Main file [" + path + "] has wrong casing for DOS path, set to [" + differentDosPathCasing + "]");
			return true;
		}
		
		String differentExtension = fileSet.stream().filter(x -> x.startsWith(mntPath)
				&& exts.stream().anyMatch(y -> x.substring(mntPath.length()).equalsIgnoreCase(
					FilenameUtils.concat(dosDir, FilenameUtils.getBaseName(dosPath) + FilenameUtils.EXTENSION_SEPARATOR + y)))).findFirst().orElse(null);
		if (differentExtension != null) {
			autoexec.setGameMain(differentExtension);
			if (verbose)
				System.out.println(title + ": Main file [" + path + "] has wrong extension, set to [" + differentExtension + "]");
			return true;
		}
		
		String differentDosPath = fileSet.stream().filter(x -> x.startsWith(mntPath)
				&& exts.stream().anyMatch(y -> Strings.CI.endsWith(x.substring(mntPath.length()), 
					FilenameUtils.getBaseName(dosPath) + FilenameUtils.EXTENSION_SEPARATOR + y))).findFirst().orElse(null);
		if (differentDosPath != null) {
			autoexec.setGameMain(differentDosPath);
			if (verbose)
				System.out.println(title + ": Main file [" + path + "] has wrong DOS path, set to [" + differentDosPath + "]");
			return true;
		}

		String differentCasing = fileSet.stream().filter(x -> Strings.CI.startsWith(x, mntPath)
				&& x.substring(mntPath.length()).equalsIgnoreCase(dosPath)).findFirst().orElse(null);
		if (differentCasing != null) {
			FileLocation f = new FileLocation(mntPath, FileLocationService.getInstance().dosrootRelative());
			FileLocation t = new FileLocation(differentCasing.substring(0, mntPath.length()), FileLocationService.getInstance().dosrootRelative());
			autoexec.migrate(f, t);
			autoexec.setGameMain(differentCasing);
			if (verbose)
				System.out.println(title + ": Main file [" + path + "] has) wrong casing, set to [" + differentCasing + "]");
			return true;
		}
		
		String differentPath = fileSet.stream().filter(x -> exts.stream().anyMatch(y -> Strings.CI.endsWith(
				x, FilenameUtils.concat(dosDir, FilenameUtils.getBaseName(dosPath) + FilenameUtils.EXTENSION_SEPARATOR + y)))).findFirst().orElse(null);
		if (differentPath != null) {
			String toDir = new File(differentPath).getParent();
			FileLocation f = new FileLocation(mntPath, FileLocationService.getInstance().dosrootRelative());
			FileLocation t = new FileLocation(toDir != null ? toDir: ".", FileLocationService.getInstance().dosrootRelative());
			autoexec.migrate(f, t);
			autoexec.setGameMain(differentPath);
			if (verbose)
				System.out.println(title + ": Main file [" + path + "] has wrong mount path and/or extension, set to [" + differentPath + "]");
			return true;
		}
		
		String[] setPaths = autoexec.getSetPathsFromCustomSections();
		if (FilenameUtils.getName(path).toUpperCase().startsWith("WIN")) {
			String mainBaseFolder = FilenameUtils.getPath(path);
			for (String setPath: setPaths) {
				char pd = setPath.toUpperCase().charAt(0);

				for (Mount m: profile.getNettoMountingPoints()) {
					if (m instanceof DirMount && m.getDrive() == pd) {
						File cp = new File(((DirMount)m).getPathString(), setPath.substring(3));
						File f1 = new File(cp, dosPath);
						String newMain = findSuitableExtension(FilenameUtils.separatorsToWindows(f1.getPath().toUpperCase()), fileSet);
						if (newMain != null) {
							autoexec.setGameMain(newMain);
							if (verbose)
								System.out.println(title + ": Main file [" + path + "] located using set path, changed to [" + newMain + "]");

							// Check and fix path to Windows parameter executable(s)
							String params = autoexec.getParameters();
							if (StringUtils.isNotEmpty(params)) {
								String[] paramArray = StringUtils.split(params);
								String[] fixedParamArray = StringUtils.split(params);
								for (int i = 0; i < paramArray.length; i++) {
									if (paramArray[i].startsWith("/") || paramArray[i].startsWith("-"))
										continue; // unlikely to be file parameter, accept in any case

									String p = fixParameterPath(fileSet, mainBaseFolder, ((DirMount)m).getPathString(), paramArray[i]);
									if (p == null) {
										if (verbose)
											System.out.println(title + ": Parameter [" + paramArray[i] + "] not found, might not be a file or folder");
									} else {
										fixedParamArray[i] = p;
									}
								}
								autoexec.setParameters(StringUtils.join(fixedParamArray, ' '));
								if (verbose)
									System.out.println(title + ": Main file parameter(s) [" + params + "] changed to [" + autoexec.getParameters() + "]");
							}
							return true;
						}
					}
				}
			}
		}
		
		return false;
	}

	private static String fixParameterPath(Set<String> fileSet, String mainBaseFolder, String mountPath, String param) {
		String newParamFile = findSuitableExtension(FilenameUtils.normalize(new File(mainBaseFolder, param).getPath()), fileSet);
		if (newParamFile != null)
			return newParamFile.substring(mountPath.length());
		newParamFile = findSuitableExtension(FilenameUtils.normalize(new File(FilenameUtils.getPath(mainBaseFolder), param).getPath()), fileSet);
		if (newParamFile != null)
			return newParamFile.substring(mountPath.length());
		newParamFile = findSuitableExtension(FilenameUtils.normalize(new File(mountPath, param).getPath()), fileSet);
		if (newParamFile != null)
			return newParamFile.substring(mountPath.length());
		return null;
	}

	private static String findSuitableExtension(String main, Set<String> fileSet) {
		return Stream.of(DOS_EXECUTABLES).filter(x -> fileSet.contains(FilenameUtils.removeExtension(main) + x)).map(x -> FilenameUtils.removeExtension(main) + x).findAny().orElse(null);
	}

	private static void addToCollection(ZipFile zipFile, Collection<ZipEntry> entries, List<ZipReference> combinedEntries) {
		for (ZipEntry entry: entries) {
			StringBuilder suggestion = new StringBuilder(FilenameUtils.getName(cleanup(entry)));
			int c = 1;
			do {
				if (c > 1) {
					suggestion.setLength(0);
					suggestion.append(FilenameUtils.getBaseName(entry.getName()) + "(" + c + ")" + FilenameUtils.EXTENSION_SEPARATOR + FilenameUtils.getExtension(entry.getName()));
				}
				c++;
			} while (combinedEntries.parallelStream().anyMatch(x -> x.name_.equalsIgnoreCase(suggestion.toString())));

			combinedEntries.add(new ZipReference(zipFile, entry, suggestion.toString()));
		}
	}

	static void unzip(ZipFile zipFile, Collection<ZipEntry> entries, File dstDir, boolean keepDirStructure, boolean prependCounter, PreProgressNotifyable prog, boolean verboseOutput) throws IOException {
		if (keepDirStructure) {
			Set<String> dirs = new HashSet<>();
			for (ZipEntry entry: entries) {
				String fullPath = FilenameUtils.getFullPath(entry.getName());
				if (dirs.stream().noneMatch(x -> x.startsWith(fullPath))) {
					dirs.removeIf(fullPath::startsWith);
					dirs.add(fullPath);
				}
			}
			dirs.forEach(x -> new File(dstDir, x).mkdirs());
		} else {
			dstDir.mkdirs();
		}

		int counter = 1;
		for (ZipEntry entry: entries) {
			if (!entry.isDirectory())
				try {
					File dstFile = new File(dstDir, keepDirStructure ? entry.getName(): FilenameUtils.getName(entry.getName()));
					if (prependCounter)
						dstFile = new File(dstFile.getParentFile(), String.format("%02d", counter++) + "_" + dstFile.getName());
                    if (dstFile.exists()) {
                    	if (verboseOutput)
                    		System.out.println("WARNING - " + dstFile + " already exists, skipping extraction.");
                    	if (dstFile.length() != entry.getSize())
                    		System.out.println("WARNING - " + dstFile + " size=" + dstFile.length() + " != zip entry size=" + entry.getCompressedSize());
                        continue;
                    }
					try (InputStream zis = zipFile.getInputStream(entry)) {
						prog.setPreProgress(entry.getSize());
						prog.incrProgress(Files.copy(zis, dstFile.toPath()));
					}
				} catch (IllegalArgumentException e) {
					System.out.println("WARNING - Zip file [" + zipFile + "] contains an entry with problematic characters in its filename");
				}
		}
	}

	static void unzip(List<ZipReference> entries, File dstDir, PreProgressNotifyable prog, boolean verboseOutput) throws IOException {
		dstDir.mkdirs();

		for (ZipReference dst: entries) {
			try {
				File dstFile = new File(dstDir, dst.name_);
                if (dstFile.exists()) {
                	if (verboseOutput)
                		System.out.println("WARNING - " + dstFile + " already exists, skipping extraction.");
                	if (dstFile.length() != dst.zipEntry_.getSize())
                		System.out.println("WARNING - " + dstFile + " size=" + dstFile.length() + " != zip entry size=" + dst.zipEntry_.getCompressedSize());
                	continue;
                }
				try (InputStream zis = dst.zipFile_.getInputStream(dst.zipEntry_)) {
					prog.setPreProgress(dst.zipEntry_.getSize());
					prog.incrProgress(Files.copy(zis, dstFile.toPath()));
				}
			} catch (IllegalArgumentException e) {
				System.out.println("WARNING - Zip file [" + dst.zipFile_ + "] contains an entry with problematic characters in its filename");
			}
		}
	}

	static void copyZipData(File srcFile, File baseDirectory, ZipOutputStream zos, PreProgressNotifyable prog) throws IOException {
		try (ZipFile zipFile = new ZipFile(srcFile, CP437)) {
			for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries.hasMoreElements();) {
				try {
					ZipEntry srcEntry = entries.nextElement();
					zipEntry(zipFile, srcEntry, FilesUtils.toArchivePath(new File(baseDirectory, cleanup(srcEntry)), srcEntry.isDirectory()), zos, prog);
				} catch (IllegalArgumentException e) {
					System.out.println("\nWARNING: Zip file [" + zipFile.getName() + "] contains an entry with problematic characters in its filename");
				}
			}
		}
	}

	static void copyZipData(ZipFile zipFile, Collection<ZipEntry> captures, File baseDirectory, ZipOutputStream zos, PreProgressNotifyable prog) throws IOException {
		int counter = 1;
		for (ZipEntry srcEntry: captures) {
			try {
				String name = String.format("%02d", counter++) + "_" + FilenameUtils.getName(cleanup(srcEntry));
				zipEntry(zipFile, srcEntry, FilenameUtils.separatorsToUnix(new File(baseDirectory, name).getPath()), zos, prog);
			} catch (IllegalArgumentException e) {
				System.out.println("\nWARNING: Zip file [" + zipFile.getName() + "] contains an entry with problematic characters in its filename");
			}
		}
	}

	static void copyZipData(List<ZipReference> extras, File baseDirectory, ZipOutputStream zos, PreProgressNotifyable prog) throws IOException {
		for (ZipReference srcReference: extras) {
			try {
				zipEntry(srcReference.zipFile_, srcReference.zipEntry_, FilenameUtils.separatorsToUnix(new File(baseDirectory, srcReference.name_).getPath()), zos, prog);
			} catch (IllegalArgumentException e) {
				System.out.println("\nWARNING: Zip file [" + srcReference.zipFile_.getName() + "] contains an entry with problematic characters in its filename");
			}
		}
	}

	private static ZipEntry zipEntry(ZipFile zipFile, ZipEntry srcEntry, String dstName, ZipOutputStream zos, PreProgressNotifyable prog) throws IOException {
		ZipEntry dstEntry = new ZipEntry(dstName);
		dstEntry.setComment(srcEntry.getComment());
		dstEntry.setTime(srcEntry.getTime());
		zos.putNextEntry(dstEntry);
		if (!srcEntry.isDirectory()) {
			prog.setPreProgress(srcEntry.getSize());
			prog.incrProgress(IOUtils.copy(zipFile.getInputStream(srcEntry), zos));
		}
		zos.closeEntry();
		return dstEntry;
	}

	static String cleanupGameTitle(String gameTitle) {
		return gameTitle.replace("/'", "_").replace("':", "_").replace("/", "_").replace("?", "_").replace(":", "_").replace("'", "_").replace("*", "_").replace("\\", "_").replace("\u2219",
			"\u00B7").replace("\u014D", "o").replace("\u0142", "l").replace("\u015B", "s").replace("\u010D", "c").replace("\u25CF", "#u25cf").replace("\u015B", "s").replace("\u0107", "c");
	}

	private static String cleanup(ZipEntry entry) {
		return entry.getName().replace((char)15, '\u263C');
	}

	static Map<String, DosboxVersion> ensureGameDosboxversions(File dstDir, List<String> titles, ExoContext ctx) throws IOException, ZipException {
		try (ZipFile utilZipFile = new ZipFile(ctx.outerZipFileContainingGameDosboxversions());
			 ZipInputStream extZipInputStream = new ZipInputStream(utilZipFile.getInputStream(utilZipFile.getEntry(ctx.innerZipFileContainingGameDosboxversions())))) {
			final List<String> dosboxEntries = IOUtils.readLines(utilZipFile.getInputStream(utilZipFile.getEntry(ctx.utilDosboxTxt())), StandardCharsets.UTF_8);
			final Map<String, String> gameDosboxExeMap = dosboxEntries.stream()
					.filter(x -> x.contains(":")).map(x -> StringUtils.split(x, ':'))
					.filter(x -> titles.isEmpty() || titles.contains(x[0].toLowerCase()))
					.collect(Collectors.toMap(x -> x[0], x -> x[1], (e1, e2) -> e1));
			return ensureDosboxVersions(extZipInputStream, dstDir, gameDosboxExeMap, ctx);
		}
	}

	private static Map<String, DosboxVersion> ensureDosboxVersions(ZipInputStream extZipInputStream, File dstDir, Map<String, String> gameToDosboxExeMap, ExoContext ctx) {
		Map<String, DosboxVersion> result = new HashMap<>();
		Map<String, DosboxVersion> added = new HashMap<>();
		
		Map<String, DosboxVersionExo> dosboxExeToExoMap = gameToDosboxExeMap.values().stream().distinct()
				.collect(Collectors.toMap(exe -> exe, exe -> ctx.dosboxMap().get(exe)));
		
		DosboxVersionRepository dosboxRepo = new DosboxVersionRepository();
		System.out.print("Importing eXoDOS DOSBox versions ...");
		
		try {
			List<DosboxVersion> dbversionsList = dosboxRepo.listAll();
			
			for (Map.Entry<String, DosboxVersionExo> entry: dosboxExeToExoMap.entrySet()) {
				String dbvTitle = entry.getValue().title_;
				
				if (dstDir == null) {
					Optional<DosboxVersion> dbv = dbversionsList.stream().filter(x -> x.getTitle().equals(dbvTitle)).findFirst();
					if (dbv.isPresent()) {
						result.put(entry.getKey(), dbv.get());
						continue;
					}
				}
				
				File dstImportDir = dstDir == null ? new File(ExoContext.DOSBOX_IMPORT_DIR): new File(dstDir, ExoContext.DOSBOX_IMPORT_DIR);
				File dbvPath = new File(dstImportDir, entry.getValue().executable_ == null ? entry.getKey() : entry.getValue().executable_);
				File path = dbvPath.getParentFile();
				File confFile = entry.getValue().conf_ == null
						? new File(path, FileLocationService.DOSBOX_CONF_STRING) : new File(path, entry.getValue().conf_);
				
				DosboxVersion newDbv = DosboxVersionFactory.create(
                        dbvTitle, entry.getValue().family_, entry.getValue().version_, false, entry.getValue().multiconf_, false, null, 
						path.getPath(), dbvPath.getPath(), StringUtils.EMPTY, confFile.getPath());
				
				if (dstDir == null) {
					dosboxRepo.add(newDbv);
					dbversionsList = dosboxRepo.listAll();
				}
				
				System.out.print(".");
				
				added.put(entry.getKey(), newDbv);
			}
			
			System.out.println(" done");
			
			if (!added.isEmpty()) {
				
				if (SystemUtils.IS_WINDOWS) {
					System.out.print("Extracting eXoDOS DOSBox versions files ...");
					
					Set<String> files = added.values().stream().distinct().map(x -> FilesUtils.toArchivePath(FilesUtils.makeRelativeTo(dstDir, x.getPath()), true))
							.collect(Collectors.toSet());
					
					try {
						ZipEntry ze = null;
						while ((ze = extZipInputStream.getNextEntry()) != null) {
							if (!ze.isDirectory()) {
							String name = ExoContext.DOSBOX_IMPORT_DIR + Strings.CS.removeStart(ze.getName(), ExoContext.UTIL_EXTDOS_EMU_DOSBOX_DIR);
							 	if (files.stream().anyMatch(x -> name.startsWith(x))) {
							 		File dstFile = dstDir == null 
							 				? FileLocationService.getInstance().dosboxRelative().canonicalize(new File(name))
							 				: new File(dstDir, name);
							 		FilesUtils.createDir(dstFile.getParentFile());
									try (FileOutputStream out = new FileOutputStream(dstFile)) {
										extZipInputStream.transferTo(out);
									}
							 	}
							}
						}
					} catch (IOException e) {
						System.err.println("There was a problem extracting " + ctx.innerZipFileContainingGameDosboxversions() + " from " + ctx.outerZipFileContainingGameDosboxversions());
					}
				
					System.out.println(" done");

				} else if (ctx.useLinuxPatch()) {

					System.out.print("Extracting eXoDOS Wine DOSBox versions files ...");
					
					Set<String> files = added.entrySet().stream().filter(x -> x.getKey().endsWith(".exe")).map(x -> x.getValue())
						.map(x -> FilesUtils.toArchivePath(FilesUtils.makeRelativeTo(dstDir, x.getPath()), true))
						.collect(Collectors.toSet());
					
					try (ZipFile utilZipFile = new ZipFile(ctx.utilZipFile());
						 ZipInputStream extDosboxZipInputStream = new ZipInputStream(utilZipFile.getInputStream(utilZipFile.getEntry(ctx.utilExtZip())))) {
						
						ZipEntry ze = null;
						while ((ze = extDosboxZipInputStream.getNextEntry()) != null) {
							if (!ze.isDirectory()) {
								String name = ExoContext.DOSBOX_IMPORT_DIR + Strings.CS.prependIfMissing(Strings.CS.removeStart(ze.getName(), ExoContext.UTIL_EXTDOS_EMU_DOSBOX_DIR), "/");
							 	if (files.stream().anyMatch(x -> name.startsWith(x))) {
							 		File dstFile = dstDir == null 
							 				? FileLocationService.getInstance().dosboxRelative().canonicalize(new File(name))
							 				: new File(dstDir, name);
							 		FilesUtils.createDir(dstFile.getParentFile());
									try (FileOutputStream out = new FileOutputStream(dstFile)) {
										extDosboxZipInputStream.transferTo(out);
									}
							 	}
							}
						}
						
					} catch (IOException e) {
						System.err.println("There was a problem extracting " + ctx.utilExtZip() + " from " + ctx.utilZipFile());
					}
					
					System.out.println(" done");
					
					System.out.print("Generating DOSBox startup scripts ...");
					try {
						for (Entry<String, DosboxVersion> entry: added.entrySet()) {
							String[] p = StringUtils.split(entry.getKey(), ' ');
							String cmd = StringUtils.joinWith(" ", p[0], p[1], p[2]);
							if (p.length > 3)
								cmd += " $(dirname \"$0\")/" + new File(p[3]).getName();
							List<String> lines = List.of("#!/bin/sh", cmd + " \"$@\"");
							FileUtils.writeLines(entry.getValue().getCanonicalExecutable(), StandardCharsets.UTF_8.name(), lines);
							entry.getValue().getCanonicalExecutable().setExecutable(true);
							
							System.out.print(".");
						}
					} catch (IOException e) {
						System.err.println("There was a problem generating the DOSBox startup scripts");
					}
					System.out.println(" done");
				}

				if (SystemUtils.IS_WINDOWS || ctx.useLinuxPatch()) {
					System.out.print("Creating dosbox.conf for each version ...");
					
					PrintStream tmp = System.out;
					
					added.values().forEach(x -> {
						try {
							if (!FilesUtils.isReadableFile(x.getConfigurationCanonicalFile())) {
								System.setOut(new PrintStream(OutputStream.nullOutputStream()));
								ExecuteUtils.doCreateDosboxConf(x);
								
								if (ctx.useLinuxPatch() && (GenerationAwareConfigurable.determineGen(x) == Generation.daum_20150125)) {
									String username = System.getProperty("user.name");
									File src = new File("/home/" + username + "/.var/app/com.retro_exo.wine/data/wine/drive_c/users/" 
											+ username + "/AppData/Local/DOSBox", x.getConfigurationFile().getName());
									FileUtils.copyFile(src, x.getConfigurationCanonicalFile());
								}
								
								x.resetAndLoadConfiguration();
							}
						} catch (IOException e) {
							System.err.println("There was a problem creating dosbox.conf for " + x.getTitle());
						} finally {
							System.setOut(tmp);
							System.out.print(".");
						}
					});
					
					System.out.println(" done");
				}
			}
			
			result.putAll(added);
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return gameToDosboxExeMap.entrySet().stream()
				.collect(Collectors.toMap(e -> e.getKey(), e -> result.get(e.getValue())));
	}
	
	static void extractMedia(File file, List<ZipEntry> zipEntries, String baseDir, File dstDir, String title) {
		PreProgressNotifyable prog = ExoUtils.prog(title, zipEntries);
		try (ZipFile zipFile = new ZipFile(file, ExoUtils.CP437)) {
			for (ZipEntry entry: zipEntries) {
				File dstFile = new File(dstDir, FilesUtils.relativize(new File(baseDir), new File(entry.getName())));
				dstFile.getParentFile().mkdirs();
				try (InputStream in = zipFile.getInputStream(entry);
						FileOutputStream out = new FileOutputStream(dstFile)) {
					prog.setPreProgress(entry.getSize());
					prog.incrProgress(in.transferTo(out));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("There was a problem extracting " + file);
		}
		System.out.println(String.format("\r%s Imported.", StringUtils.rightPad(title, 70, '.')));
	}
	
	static void extractInnerMedia(File file, List<ZipEntry> zipEntries, String baseDir, File dstDir, String title) {
		PreProgressNotifyable prog = ExoUtils.prog(title, zipEntries);
		try (ZipFile zipFile = new ZipFile(file, ExoUtils.CP437)) {
			for (ZipEntry entry: zipEntries) {
				prog.setPreProgress(entry.getSize());
				try (ZipInputStream zin = new ZipInputStream(zipFile.getInputStream(entry), ExoUtils.CP437)) {
					ZipEntry ze = null;
					while ((ze = zin.getNextEntry()) != null) {
						if (!ze.isDirectory()) {
							File dstFile = FileUtils.getFile(dstDir, FilenameUtils.getBaseName(entry.getName()), ze.getName());
							dstFile.getParentFile().mkdirs();
							try (FileOutputStream out = new FileOutputStream(dstFile)) {
								zin.transferTo(out);
							}
						}
					}
				}
				prog.incrProgress(entry.getSize());
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("There was a problem extracting " + file);
		}
		System.out.println(String.format("\r%s Imported.", StringUtils.rightPad(title, 70, '.')));
	}
}