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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.interfaces.PreProgressNotifyable;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.entity.Filter;
import org.dbgl.model.repository.FilterRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.DatabaseService;
import org.dbgl.service.FileLocationService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.XmlUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


public class Import {

	private static final String IMPORTER_VERSION = "0.99";

	private static boolean listOnly_ = false;
	private static boolean analyzeOnly_ = false;
    private static boolean gameDataOnly_ = false;
	private static boolean skipZips_ = false;
	private static boolean defaultDosboxOnly_ = false;
	private static boolean verboseOutput_ = false;
	private static boolean mediapack_ = false;

	private static void displaySyntax() {
		System.out.println("Use: Import <inputexodosdir> [-l] [-a] [-d] [-v] [-z] [-f] [game-1] [game-2] [game-N]");
		System.out.println("-l\t\tList game titles and abbreviations, don't import");
		System.out.println("-a\t\tAnalyze only, don't import");
        System.out.println("-g\t\tGame data only, don't import extra media");
		System.out.println("-d\t\tUse only the default DOSBox version, do not import the ones used in eXoDOS");
		System.out.println("-v\t\tVerbose output");
		System.out.println("-z\t\tDon't import zips (debugging)");
		System.out.println("-f\t\tFavorite games only");
		System.out.println("Optional: game(s) to import based on title or abbreviation");
		System.exit(1);
	}

	public static void main(String[] args) {
		System.out.println("Imports eXoDOS games into DBGL (v" + IMPORTER_VERSION + ")");
		System.out.println();

		if (args.length < 1)
			displaySyntax();

		File srcDir = new File(args[0]);
		List<String> impTitles = new ArrayList<>();
		boolean favorites = false;

		if (args.length > 1) {
			for (int i = 1; i < args.length; i++) {
				if (args[i].equalsIgnoreCase("-l"))
					listOnly_ = true;
				else if (args[i].equalsIgnoreCase("-a"))
					analyzeOnly_ = true;
                else if (args[i].equalsIgnoreCase("-g"))
                    gameDataOnly_ = true;
				else if (args[i].equalsIgnoreCase("-d"))
					defaultDosboxOnly_ = true;
				else if (args[i].equalsIgnoreCase("-v"))
					verboseOutput_ = true;
				else if (args[i].equalsIgnoreCase("-z"))
					skipZips_ = true;
				else if (args[i].equalsIgnoreCase("-f"))
					favorites = true;
				else
					impTitles.add(args[i].toLowerCase());
			}
		}

		if (listOnly_)
			System.out.println("* List only");
		if (analyzeOnly_)
			System.out.println("* Analyze only");
        if (gameDataOnly_)
            System.out.println("* Convert game data only");
		if (defaultDosboxOnly_)
			System.out.println("* Default DOSBox version only");
		if (verboseOutput_)
			System.out.println("* Verbose output");
		if (skipZips_)
			System.out.println("* Don't import zips");
		if (!impTitles.isEmpty() || favorites) {
			if (!impTitles.isEmpty())
				System.out.println("* Processing: " + StringUtils.join(impTitles, ", "));
			if (favorites)
				System.out.println("* Processing favorite games");
		} else {
			System.out.println("* Processing all games");
		}

		ExoContext ctx = new ExoContext(srcDir);
		if (ctx.version() == ExoDosVersion.UNKNOWN) {
			System.out.println("The eXoDOS version could not be determined.");
			System.exit(1);
		}
		
		System.out.println("* eXoDOS " + ctx.version() + " found");
			
		mediapack_ = ctx.isMediapackAvailable();
		if (mediapack_)
			System.out.println("* eXoDOS Media Add On Pack found");

		try {
			final List<ZipEntry> xodosZipEntries = ExoUtils.listEntries(ctx.xoMetadataZipFile(), true);
			final NodeList gameNodes = ExoUtils.getGameNodes(xodosZipEntries, ctx);
			
			impTitles = ExoUtils.determineTitles(impTitles, favorites, gameNodes);
			if (!impTitles.isEmpty())
				System.out.println("* Games: " + StringUtils.join(impTitles, ", "));
			
			new Import().importData(xodosZipEntries, gameNodes, impTitles, ctx);

			DatabaseService.getInstance().shutdown();
		} catch (SQLException e) {
			// nothing we can do
		} catch (XPathExpressionException | SAXException | ParserConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void importData(final List<ZipEntry> xodosZipEntries, final NodeList gameNodes, final List<String> impTitles, ExoContext ctx) {
		
		final List<ZipEntry> dosZipEntries = ExoUtils.listEntries(ctx.metadataZipFile(), true);
		final List<ZipEntry> dosboxConfEntries = dosZipEntries.parallelStream().filter(x -> x.getName().toLowerCase().endsWith(FileLocationService.DOSBOX_CONF_STRING)).toList();
		final List<ZipEntry> imageEntries = xodosZipEntries.parallelStream().filter(x -> x.getName().startsWith(ctx.xoMetadataImages())).toList();

		final List<Integer> gameIndices = ExoUtils.determineGameIndices(gameNodes);
		
		try (ZipFile xodosZipfile = new ZipFile(ctx.xoMetadataZipFile(), ExoUtils.CP437);
				ZipFile dosZipfile = new ZipFile(ctx.metadataZipFile(), ExoUtils.CP437)) {
			
			final Map<String, DosboxVersion> gameDosboxversionMap = !ctx.hasDosboxVersions() || listOnly_ || analyzeOnly_ || defaultDosboxOnly_ 
					? null 
					: ExoUtils.ensureGameDosboxversions(null, impTitles, ctx);
			final DosboxVersion defaultDosboxVersion = !listOnly_ && gameDosboxversionMap == null 
					? ExoUtils.findDefaultDosboxVersion(verboseOutput_): null;

			if (ctx.hasRoms() && !listOnly_ && !analyzeOnly_ && !skipZips_)
				ensureRoms(ctx);
			
			if (ctx.hasDemoVideos() && !listOnly_ && !analyzeOnly_ && !skipZips_ && !gameDataOnly_) {
				System.out.println("Importing videos ...");
				ExoUtils.extractMedia(ctx.demoVideosZipFile(), ctx.demoVideoEntries(), ExoContext.MEDIAPACK_VIDEOS_DIR, ExoContext.IMPORT_VIDEOS_DIR, ExoContext.VIDEOS_DIR);
			}
			
			if (mediapack_ && !listOnly_ && !analyzeOnly_ && impTitles.isEmpty() && !skipZips_ && !gameDataOnly_) {
				System.out.println("Importing Media Add On Pack ...");
				ExoUtils.extractInnerMedia(ctx.soundtracksZipFile(), ctx.soundtrackEntries(), ExoContext.MEDIAPACK_SOUNDTRACKS_DIR, ExoContext.IMPORT_SOUNDTRACKS_DIR, ExoContext.SOUNDTRACKS_DIR);
				ExoUtils.extractMedia(ctx.booksZipFile(), ctx.bookEntries(), ExoContext.MEDIAPACK_BOOKS_DIR, ExoContext.IMPORT_BOOKS_DIR, ExoContext.BOOKS_DIR);
				ExoUtils.extractMedia(ctx.catalogsZipFile(), ctx.catalogEntries(), ExoContext.MEDIAPACK_CATALOGS_DIR, ExoContext.IMPORT_CATALOGS_DIR, ExoContext.CATALOGS_DIR);
				ExoUtils.extractMedia(ctx.magazinesZipFile(), ctx.magazineEntries(), ExoContext.MEDIAPACK_MAGAZINES_DIR, ExoContext.IMPORT_MAGAZINES_DIR, ExoContext.MAGAZINES_DIR);
				
				if (FilesUtils.isExistingFile(ctx.videosZipFile()))
					ExoUtils.extractMedia(ctx.videosZipFile(), ctx.videoEntries(), ExoContext.MEDIAPACK_VIDEOS_DIR, ExoContext.IMPORT_VIDEOS_DIR, ExoContext.VIDEOS_DIR);
			}
			
			Map<String, Set<Integer>> filters = new TreeMap<>();

			int processed = 0;

			for (int i = 0; i < gameNodes.getLength(); i++) {
				Element gameNode = (Element)gameNodes.item(gameIndices.get(i));

				String gameApplicationPath = XmlUtils.getTextValue(gameNode, "ApplicationPath");
				String fullGameTitle = FilenameUtils.getBaseName(gameApplicationPath);
				
				if (StringUtils.isBlank(fullGameTitle) || (!impTitles.isEmpty() && !impTitles.contains(fullGameTitle.toLowerCase())))
					continue;

				String gameTitle = StringUtils.defaultString(XmlUtils.getTextValue(gameNode, "Title"));
				String cleanedUpGameTitle = ExoUtils.cleanupGameTitle(gameTitle);
				File gamePath = ExoUtils.fixupGamePath(gameTitle, new File(FilenameUtils.separatorsToSystem(gameApplicationPath)).getParentFile(), ctx, verboseOutput_);
				
				String gameDirName = gamePath != null ? gamePath.getName(): StringUtils.EMPTY;

				if (listOnly_) {
					System.out.println(String.format("%4d %-50s %-50s %-10s", i + 1, gameTitle, fullGameTitle, gameDirName));
					continue;
				}

				String gameZipFilename = fullGameTitle + ".zip";
				File gameSrcZipfile = ctx.gameZipFile(gameZipFilename);
				if (!FilesUtils.isExistingFile(gameSrcZipfile)) {
					System.err.println(fullGameTitle + ": Zip file " + gameSrcZipfile + " is missing, skipping");
					continue;
				}

				String confPathAndFile = FilenameUtils.separatorsToUnix(new File(gamePath, FileLocationService.DOSBOX_CONF_STRING).getPath());
				ZipEntry confEntry = dosboxConfEntries.parallelStream().filter(x -> x.getName().equalsIgnoreCase(confPathAndFile)).findAny().orElse(null);
				if (confEntry == null) {
					System.err.println(fullGameTitle + ": Zip file " + dosZipfile.getName() + " does not contain " + confPathAndFile + ", skipping");
					continue;
				}
				
				Set<String> playlists = new HashSet<>();

				if (ctx.hasPlaylists()) {
					String series = XmlUtils.getTextValue(gameNode, "Series");
					String[] seriesValues = StringUtils.split(series, ';');
					for (String serieValue: seriesValues) {
						String[] kv = StringUtils.split(serieValue, ':');
						if ("Playlist".equals(StringUtils.trim(kv[0]))) {
							playlists.add(StringUtils.trim(kv[1]));
						}
					}
				}

				final Collection<ZipEntry> gameImageEntries = gameDataOnly_ ? new ArrayList<>(): ExoUtils.getUniqueImages(imageEntries, cleanedUpGameTitle);
				if (gameImageEntries.isEmpty() && verboseOutput_ && !gameDataOnly_)
					System.out.println(fullGameTitle + ": No images found");
					
				final List<ZipReference> gameCombinedExtraEntries = gameDataOnly_ ? new ArrayList<>(): (ctx.extrasInXoDosMetadata()
						? ExoUtils.getGameCombinedExtrasV5(xodosZipfile, xodosZipEntries,
							dosZipfile, dosZipEntries, cleanedUpGameTitle, fullGameTitle, gameDirName, ctx)
						: ExoUtils.getGameCombinedExtrasV6(gameZipFilename, gameDirName, ctx));
				
				try (ZipFile gameZipfile = new ZipFile(gameSrcZipfile, ExoUtils.CP437)) {
					final List<ZipEntry> gameZipEntries = ExoUtils.listEntries(gameZipfile, false);
					final DosboxVersion db = gameDosboxversionMap == null 
							? defaultDosboxVersion
							: (gameDosboxversionMap.containsKey(fullGameTitle) 
								? gameDosboxversionMap.get(fullGameTitle)
								: gameDosboxversionMap.entrySet().stream().filter(x -> x.getKey().equalsIgnoreCase(fullGameTitle)).map(x -> x.getValue()).findAny().orElse(null));

					Profile profile = ExoUtils.createProfile(gameNode, fullGameTitle, gameTitle, gamePath, gameDirName, db,
							confEntry, dosZipfile, gameSrcZipfile.getPath(), gameZipEntries, gameCombinedExtraEntries, ctx, verboseOutput_);
				
					if (profile.getConfiguration().getAutoexec().isExit() && profile.isIncomplete()) {
						System.out.println(fullGameTitle + ": WARNING - autoexec is incomplete");
					}
					
					if (!analyzeOnly_) {
						profile = new ProfileRepository().add(profile);

						for (String playlist: playlists) {
							Set<Integer> profileIds = filters.getOrDefault(playlist, new HashSet<>());
							profileIds.add(profile.getId());
							filters.put(playlist, profileIds);
						}
				
						PreProgressNotifyable prog = new AsciiProgressBar(fullGameTitle, Stream.of(gameImageEntries, gameZipEntries).flatMap(Collection::stream).mapToLong(ZipEntry::getSize).sum()
								+ gameCombinedExtraEntries.stream().mapToLong(x1 -> x1.zipEntry_.getSize()).sum());
				
						File canonicalGamePath = new File(FileLocationService.getInstance().getDosroot(), gameDirName);

						if (!gameImageEntries.isEmpty() && !skipZips_) {
							ExoUtils.unzip(xodosZipfile, gameImageEntries, profile.getCanonicalCaptures(), false, true, prog, verboseOutput_);
						}
						
						if (!gameCombinedExtraEntries.isEmpty() && !skipZips_) {
							if (ctx.extrasInXoDosMetadata()) {
								ExoUtils.unzip(gameCombinedExtraEntries, new File(canonicalGamePath, ExoContext.EXTRAS_DIR), prog, verboseOutput_);
							} else {
								try (ZipFile extraGameDataZipfile = new ZipFile(gameCombinedExtraEntries.get(0).zipFile_.getName(), ExoUtils.CP437)) {
									List<ZipReference> map = gameCombinedExtraEntries.stream().map(x -> new ZipReference(extraGameDataZipfile, x.zipEntry_, x.name_)).toList();
									ExoUtils.unzip(map, new File(canonicalGamePath, ExoContext.EXTRAS_DIR), prog, verboseOutput_);
								}
							}
						}
						
						if (!skipZips_)
							ExoUtils.unzip(gameZipfile, gameZipEntries, canonicalGamePath, true, false, prog, verboseOutput_);
				
						double p = (impTitles != null) && !impTitles.isEmpty() ? (double)++processed / (double)impTitles.size() * 100d: (double)(i + 1) / (double)gameNodes.getLength() * 100d;
						System.out.println(String.format("\r%s Imported. Overall progress: %5.1f%%", StringUtils.rightPad(StringUtils.abbreviate(fullGameTitle, 68), 70, '.'), p));
					}
				}
			}

			if (impTitles.isEmpty()) {
				// Create playlists / filters
				FilterRepository repo = new FilterRepository();
				for (Entry<String, Set<Integer>> entry: filters.entrySet()) {
					System.out.print("Creating filter " + entry.getKey() + " ... ");
					repo.add(new Filter(entry.getKey(), "GAM.ID IN (" + StringUtils.join(entry.getValue(), ',') + ")"));
					System.out.println("done");
				}
			}
			
			System.out.println(StringUtils.LF + StringUtils.LF + "Finished.");

		} catch (SQLException | IOException e) {
			e.printStackTrace();
		}
	}
	
	private void ensureRoms(ExoContext ctx) {
		System.out.print("Importing Roland MT-32 roms and SC-55 soundfont ...");
		try (ZipFile utilZipFile = new ZipFile(ctx.utilZipFile());
			 ZipInputStream zin = new ZipInputStream(utilZipFile.getInputStream(
					 utilZipFile.getEntry(ctx.utilExtZip())), ExoUtils.CP437)) {
			ZipEntry ze = null;
			while ((ze = zin.getNextEntry()) != null) {
				if (!ze.isDirectory()) {
					if (ze.getName().startsWith(ExoContext.UTIL_EXTDOS_ROMS_DIR)) {
				 		File dstFile = FileLocationService.getInstance().dosrootRelative().canonicalize(new File(ze.getName()));
				 		dstFile.getParentFile().mkdirs();
						try (FileOutputStream out = new FileOutputStream(dstFile)) {
							zin.transferTo(out);
							System.out.print(".");
						}
				 	}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("There was a problem extracting " + ctx.utilZipFile());
		}
		System.out.println(" done");
	}
}