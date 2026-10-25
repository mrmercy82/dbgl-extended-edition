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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.interfaces.PreProgressNotifyable;
import org.dbgl.gui.interfaces.ProgressNotifyable;
import org.dbgl.model.GamePack;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.entity.GamePackEntry;
import org.dbgl.service.DatabaseService;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ImportExportProfilesService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.XmlUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


public class Convert {

	private static final String CONVERTER_TITLE = "eXoDOS converter";
	private static final String CONVERTER_VERSION = "0.99";
	private static final String GPA_TITLE = "eXoDOS conversion";
	private static final String GPA_NOTES = StringUtils.EMPTY;
	private static final String GPA_AUTHOR = StringUtils.EMPTY;
	private static final long BYTES_IN_MB = 1024L * 1024L;
	private static final long MAX_PART_SIZE_DEFAULT_IN_MB = 1024L * 16L;

	private static boolean analyzeOnly_ = false;
	private static boolean gameDataOnly_ = false;
	private static boolean skipZips_ = false;
	private static boolean defaultDosboxOnly_ = false;
	private static boolean verboseOutput_ = false;
	private static boolean mediapack_ = false;
	private static long maxPartSizeInMB_ = MAX_PART_SIZE_DEFAULT_IN_MB;
	private static int nrOfThreads_ = Math.min(Runtime.getRuntime().availableProcessors(), 6);

	private static void displaySyntax() {
		System.out.println("Use: Convert <inputexodosdir> <dstdir> [-a] [-d] [-v] [-s:size] [-t:threads] [-z] [-f] [game-1] [game-2] [game-N]");
		System.out.println("-a\t\tAnalyze only, don't generate GamePackArchives");
		System.out.println("-g\t\tGame data only, don't convert extra media");
		System.out.println("-d\t\tUse only the default DOSBox version, do not reference the ones used in eXoDOS");
		System.out.println("-v\t\tVerbose output");
		System.out.println("-s:size\t\tTarget size of the GamePackArchives in MB, " + MAX_PART_SIZE_DEFAULT_IN_MB + " is the default (= " + MAX_PART_SIZE_DEFAULT_IN_MB / 1024L + " GB packages)");
		System.out.println("-t:nrOfThreads\tAmount of CPU cores to use when generating GamePackArchives (instead of the default " + nrOfThreads_ + " threads)");
		System.out.println("-z\t\tDon't convert zips (debugging)");
		System.out.println("-f\t\tFavorite games only");
		System.out.println("Optional: game(s) to export based on title or abbreviation");
		System.exit(1);
	}

	public static void main(String[] args) {
		System.out.println("Converts eXoDOS games into DBGL GamePackArchives (v" + CONVERTER_VERSION + ")");
		System.out.println();

		if (args.length < 2)
			displaySyntax();

		File srcDir = new File(args[0]);
		File dstDir = new File(args[1]);
		
		if (!dstDir.exists()) {
			System.out.println("The directory [" + dstDir + "] does not exist.");
			System.exit(1);
		}
		
		List<String> impTitles = new ArrayList<>();
		boolean favorites = false;

		if (args.length > 2) {
			for (int i = 2; i < args.length; i++) {
				if (args[i].equalsIgnoreCase("-a"))
					analyzeOnly_ = true;
				else if (args[i].equalsIgnoreCase("-g"))
					gameDataOnly_ = true;
				else if (args[i].equalsIgnoreCase("-d"))
					defaultDosboxOnly_ = true;
				else if (args[i].equalsIgnoreCase("-v"))
					verboseOutput_ = true;
				else if (args[i].toLowerCase().startsWith("-s:")) {
					try {
						maxPartSizeInMB_ = Long.parseLong(args[i].substring(3));
					} catch (NumberFormatException e) {
						// ignore, use the default value
					}
				} else if (args[i].toLowerCase().startsWith("-t:")) {
					try {
						nrOfThreads_ = Integer.parseInt(args[i].substring(3));
					} catch (NumberFormatException e) {
						// ignore, use the default value
					}
				} else if (args[i].equalsIgnoreCase("-z")) {
					skipZips_ = true;
				} else if (args[i].equalsIgnoreCase("-f")) {
					favorites = true;
				} else
					impTitles.add(args[i].toLowerCase());
			}
		}

		if (analyzeOnly_)
			System.out.println("* Analyze only");
		if (gameDataOnly_)
			System.out.println("* Convert game data only");
		if (defaultDosboxOnly_)
			System.out.println("* Default DOSBox version only");
		if (verboseOutput_)
			System.out.println("* Verbose output");
		if (maxPartSizeInMB_ != MAX_PART_SIZE_DEFAULT_IN_MB)
			System.out.println("* Target size of the GamePackArchives: " + maxPartSizeInMB_ + "MB");
		if (skipZips_)
			System.out.println("* Don't convert zips");
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
			
			new Convert().convertData(xodosZipEntries, gameNodes, impTitles, dstDir, ctx);

			DatabaseService.getInstance().shutdown();
		} catch (SQLException e) {
			// nothing we can do
		} catch (XPathExpressionException | SAXException | ParserConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void convertData(final List<ZipEntry> xodosZipEntries, final NodeList gameNodes, final List<String> impTitles, File dstDir, ExoContext ctx) {

		System.out.println();
		System.out.println("==========================================");
		System.out.println(" Phase 1 of 3: Analyzing meta-data");
		System.out.println("==========================================");

		GamePack gamePack = new GamePack();
		gamePack.setCreationApp(CONVERTER_TITLE);
		gamePack.setCreationAppVersion(CONVERTER_VERSION);
		gamePack.setCreationDate(new Date());
		gamePack.setTitle(GPA_TITLE);
		gamePack.setAuthor(GPA_AUTHOR);
		gamePack.setNotes(GPA_NOTES);
		gamePack.setCapturesAvailable(!gameDataOnly_);
		gamePack.setGamedataAvailable(true);
		gamePack.setMapperfilesAvailable(false);
		gamePack.setNativecommandsAvailable(false);
		gamePack.setVersion(ImportExportProfilesService.PROFILES_XML_FORMAT_VERSION);
		
		final List<ZipEntry> dosZipEntries = ExoUtils.listEntries(ctx.metadataZipFile(), true);
		final List<ZipEntry> dosboxConfEntries = dosZipEntries.parallelStream().filter(x -> x.getName().toLowerCase().endsWith(FileLocationService.DOSBOX_CONF_STRING)).toList();
		final List<ZipEntry> imageEntries = xodosZipEntries.parallelStream().filter(x -> x.getName().startsWith(ctx.xoMetadataImages())).toList();

		final List<Integer> gameIndices = ExoUtils.determineGameIndices(gameNodes);
		
		try (ZipFile xodosZipfile = new ZipFile(ctx.xoMetadataZipFile(), ExoUtils.CP437);
				ZipFile dosZipfile = new ZipFile(ctx.metadataZipFile(), ExoUtils.CP437)) {

			final Map<String, DosboxVersion> gameDosboxversionMap = !ctx.hasDosboxVersions() || analyzeOnly_ || defaultDosboxOnly_ 
					? null : ExoUtils.ensureGameDosboxversions(dstDir, impTitles, ctx);
			final DosboxVersion defaultDosboxVersion = gameDosboxversionMap == null 
					? ExoUtils.findDefaultDosboxVersion(verboseOutput_): null;
			
			ProgressNotifyable prog = new AsciiProgressBar("Analysis", gameNodes.getLength());

			for (int i = 0; i < gameNodes.getLength(); i++) {
				Element gameNode = (Element)gameNodes.item(gameIndices.get(i));

				String gameApplicationPath = XmlUtils.getTextValue(gameNode, "ApplicationPath");
				String fullGameTitle = FilenameUtils.getBaseName(gameApplicationPath);

				if (StringUtils.isBlank(fullGameTitle) || (!impTitles.isEmpty() && !impTitles.contains(fullGameTitle.toLowerCase()))) {
					prog.incrProgress(1);
					continue;
				}

				String gameTitle = StringUtils.defaultString(XmlUtils.getTextValue(gameNode, "Title"));
				String cleanedUpGameTitle = ExoUtils.cleanupGameTitle(gameTitle);
				File gamePath = ExoUtils.fixupGamePath(gameTitle, new File(FilenameUtils.separatorsToSystem(gameApplicationPath)).getParentFile(), ctx, verboseOutput_);
				
				String gameDirName = gamePath != null ? gamePath.getName(): StringUtils.EMPTY;
				String gameZipFilename = fullGameTitle + ".zip";
				File gameSrcZipfile = ctx.gameZipFile(gameZipFilename);
				if (!FilesUtils.isExistingFile(gameSrcZipfile)) {
					System.err.println(fullGameTitle + ": Zip file " + gameSrcZipfile + " is missing, skipping");
					prog.incrProgress(1);
					continue;
				}

				String confPathAndFile = FilenameUtils.separatorsToUnix(new File(gamePath, FileLocationService.DOSBOX_CONF_STRING).getPath());
				ZipEntry confEntry = dosboxConfEntries.parallelStream().filter(x -> x.getName().equalsIgnoreCase(confPathAndFile)).findAny().orElse(null);
				if (confEntry == null) {
					System.err.println(fullGameTitle + ": Zip file " + dosZipfile.getName() + " does not contain " + confPathAndFile + ", skipping");
					prog.incrProgress(1);
					continue;
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
					
					long size = Stream.of(gameImageEntries, gameZipEntries).flatMap(Collection::stream).mapToLong(ZipEntry::getCompressedSize).sum()
							+ gameCombinedExtraEntries.stream().mapToLong(x -> x.zipEntry_.getCompressedSize()).sum();

					GamePackEntry newGamePackEntry = new GamePackEntry(i, profile, gameDirName, gameImageEntries, gameCombinedExtraEntries, gameSrcZipfile, size);
					gamePack.getEntries().add(newGamePackEntry);
				}

				prog.incrProgress(1);
			}

			Collections.sort(gamePack.getEntries());

			System.out.println();
			System.out.println("Meta-data analysis done");

			if (!analyzeOnly_) {
				if (!gameDataOnly_)
					extractMedia(gamePack, xodosZipfile, dstDir, impTitles, ctx);
				generateGamePackArchives(gamePack, xodosZipfile, dstDir, ctx);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void extractMedia(GamePack gamePack, ZipFile xodosZipfile, File tmpDir, final List<String> impTitles, ExoContext ctx) {
		System.out.println();
		System.out.println("===========================================");
		System.out.println(" Phase 2 of 3: Extracting media            ");
		System.out.println("===========================================");
		
		if (ctx.hasRoms() && !skipZips_)
			ensureRoms(new File(tmpDir, ExoContext.BASE_IMPORT_DIR), ctx);
		
		if (ctx.hasDemoVideos() && !skipZips_) {
			System.out.println("Extracting videos ...");
			ExoUtils.extractMedia(ctx.demoVideosZipFile(), ctx.demoVideoEntries(), ExoContext.MEDIAPACK_VIDEOS_DIR, new File(tmpDir, ExoContext.IMPORT_VIDEOS_DIR.getPath()), ExoContext.VIDEOS_DIR);
		}
		
		if (mediapack_ && !analyzeOnly_ && impTitles.isEmpty() && !skipZips_) {
			System.out.println("Extracting Media Add On Pack ...");
			ExoUtils.extractInnerMedia(ctx.soundtracksZipFile(), ctx.soundtrackEntries(), ExoContext.MEDIAPACK_SOUNDTRACKS_DIR, new File(tmpDir, ExoContext.IMPORT_SOUNDTRACKS_DIR.getPath()), ExoContext.SOUNDTRACKS_DIR);
			ExoUtils.extractMedia(ctx.booksZipFile(), ctx.bookEntries(), ExoContext.MEDIAPACK_BOOKS_DIR, new File(tmpDir, ExoContext.IMPORT_BOOKS_DIR.getPath()), ExoContext.BOOKS_DIR);
			ExoUtils.extractMedia(ctx.catalogsZipFile(), ctx.catalogEntries(), ExoContext.MEDIAPACK_CATALOGS_DIR, new File(tmpDir, ExoContext.IMPORT_CATALOGS_DIR.getPath()), ExoContext.CATALOGS_DIR);
			ExoUtils.extractMedia(ctx.magazinesZipFile(), ctx.magazineEntries(), ExoContext.MEDIAPACK_MAGAZINES_DIR, new File(tmpDir, ExoContext.IMPORT_MAGAZINES_DIR.getPath()), ExoContext.MAGAZINES_DIR);
			
			if (FilesUtils.isExistingFile(ctx.videosZipFile()))
				ExoUtils.extractMedia(ctx.videosZipFile(), ctx.videoEntries(), ExoContext.MEDIAPACK_VIDEOS_DIR, new File(tmpDir, ExoContext.IMPORT_VIDEOS_DIR.getPath()), ExoContext.VIDEOS_DIR);
		}
	}
		
	private static void generateGamePackArchives(GamePack gamePack, ZipFile xodosZipfile, File tmpDir, ExoContext ctx) {
		System.out.println();
		System.out.println("===========================================");
		System.out.println(" Phase 3 of 3: Generating GamePackArchives");
		System.out.println("===========================================");

		List<List<GamePackEntry>> allGamePacksToCreate = new ArrayList<>();
		List<GamePackEntry> remainingGamePackEntries = new ArrayList<>(gamePack.getEntries());

		while (!remainingGamePackEntries.isEmpty()) {

			long totalSize = 0L;
			List<GamePackEntry> currentGamePackEntries = new ArrayList<>();
			boolean reachedMaxSize = false;

			while (!remainingGamePackEntries.isEmpty() && !reachedMaxSize) {
				GamePackEntry gamePackEntry = remainingGamePackEntries.get(0);
				long gameSize = gamePackEntry.getSize() + 1024 * 4; // reserved 4 KB for profiles.xml data
				if (currentGamePackEntries.isEmpty() || ((totalSize + gameSize) < (maxPartSizeInMB_ * BYTES_IN_MB))) {
					currentGamePackEntries.add(gamePackEntry);
					remainingGamePackEntries.remove(0);
					totalSize += gameSize;
				} else {
					reachedMaxSize = true;
				}
			}

			allGamePacksToCreate.add(currentGamePackEntries);
		}

		ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newFixedThreadPool(nrOfThreads_);
		CountDownLatch latch = new CountDownLatch(allGamePacksToCreate.size());

		for (List<GamePackEntry> gamePackEntries: allGamePacksToCreate) {
			executor.submit(() -> {
				File currentOutputGpa = new File(tmpDir, ctx.gpaFileName(gamePackEntries.get(0).getProfile().getTitle(),
						gamePackEntries.size() > 1 ? gamePackEntries.get(gamePackEntries.size() - 1).getProfile().getTitle(): null));

				try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(currentOutputGpa))) {
					for (GamePackEntry game: gamePackEntries) {
						try {

							Profile prof = game.getProfile();
							File relativeGameDirInZip = game.getArchiveGameDir();
							File relativeExtrasGameDirInZip = new File(relativeGameDirInZip, ExoContext.EXTRAS_DIR);

							PreProgressNotifyable prog = new DotProgress(prof.getTitle());

							if (!skipZips_)
								ExoUtils.copyZipData(xodosZipfile, game.getCaptures(), game.getArchiveCapturesDir(), zipOutputStream, prog);
							
							if (!game.getExtras().isEmpty() && !skipZips_) {
								if (ctx.extrasInXoDosMetadata()) {
									ExoUtils.copyZipData(game.getExtras(), relativeExtrasGameDirInZip, zipOutputStream, prog);
								} else {
									try (ZipFile extraGameDataZipfile = new ZipFile(game.getExtras().get(0).zipFile_.getName(), ExoUtils.CP437)) {
										List<ZipReference> map = game.getExtras().stream().map(x -> new ZipReference(extraGameDataZipfile, x.zipEntry_, x.name_)).toList();
										ExoUtils.copyZipData(map, relativeExtrasGameDirInZip, zipOutputStream, prog);
									}
								}
							}
							
							if (!skipZips_)
								ExoUtils.copyZipData(game.getGameZipFile(), relativeGameDirInZip, zipOutputStream, prog);

						} catch (IOException e2) {
							System.out.println(
								"\nWARNING: The file [" + game.getGameZipFile() + "] could not be copied (completely) properly into the [" + currentOutputGpa + "], this game may be corrupt");
							e2.printStackTrace();
						}
					}
					ImportExportProfilesService.export(gamePack, gamePackEntries, zipOutputStream);
				} catch (ParserConfigurationException | TransformerException | IOException e) {
					e.printStackTrace();
				}

				latch.countDown();
				double p = (double)(allGamePacksToCreate.size() - latch.getCount()) / (double)allGamePacksToCreate.size() * 100d;
				System.out.println(String.format(StringUtils.LF + "DBGL GamePackArchive %s successfully generated. Overall progress: %3.1f%%", currentOutputGpa.getPath(), p));
				return null;
			});
		}

		executor.shutdown();

		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
			Thread.currentThread().interrupt();
		}

		System.out.println(StringUtils.LF + StringUtils.LF + "Finished.");
	}
	
	private static void ensureRoms(File dstDir, ExoContext ctx) {
		System.out.print("Extracting Roland MT-32 roms and SC-55 soundfont ...");
		try (ZipFile utilZipFile = new ZipFile(ctx.utilZipFile());
			 ZipInputStream zin = new ZipInputStream(utilZipFile.getInputStream(
					 utilZipFile.getEntry(ctx.utilExtZip())), ExoUtils.CP437)) {
			ZipEntry ze = null;
			while ((ze = zin.getNextEntry()) != null) {
				if (!ze.isDirectory()) {
					if (ze.getName().startsWith(ExoContext.UTIL_EXTDOS_ROMS_DIR)) {
				 		File dstFile = new File(dstDir, ze.getName());
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
