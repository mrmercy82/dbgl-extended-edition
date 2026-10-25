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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

import org.apache.commons.io.FileUtils;
import org.dbgl.service.SettingsService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.SystemUtils;

public class ExoContext {

	private static final String SLASH = "/";
	
	private static final String MSDOS = "MS-DOS";
	private static final String DEMOSCN = "eXoDemoScene";
	
	private static final String EXO_DIR = "eXo";
	private static final String UTIL_DIR = "util";

	private static final String UTIL_FILE = "util.zip";
	private static final String UTIL_FILE_LINUX = "utilDOS_linux.zip";
	private static final String UTILDSN_FILE = "utilDSN.zip";
	private static final String UTIL_EXTDOS_FILE = "EXTDOS.zip";
	private static final String UTIL_EXTDOS_LINUX_FILE = "EXTDOS_linux.zip";
	private static final String UTIL_EXTDEMOSCN_FILE = "EXTDSN.zip";
	private static final String UTIL_DOSBOX_TXT_FILE = "dosbox.txt";
	private static final String UTIL_DOSBOX_LINUX_TXT_FILE = "dosbox_linux.txt";
	private static final String UTIL_DEMOSCN_TXT_FILE = "demoscn.txt";
	static final String UTIL_EXTDOS_ROMS_DIR = "mt32";
	static final String UTIL_EXTDOS_EMU_DOSBOX_DIR = "emulators" + SLASH + "dosbox";

	private static final String EXODOS_DIR = "eXoDOS";
	private static final String DOS_METADATA_FILE = "!DOSmetadata.zip";
	private static final String XODOS_METADATA_FILE = "XODOSMetadata.zip";
	private static final String V5_METADATA_XML = "xml" + SLASH + MSDOS + ".xml";
	private static final String V6_METADATA_XML = "xml" + SLASH + "all" + SLASH + MSDOS + ".msdos";
	private static final String V6_R2_METADATA_XML = "xml" + SLASH + "all" + SLASH + MSDOS + ".xml";
	private static final String V6_R4_METADATA_PARENTS = "xml" + SLASH + "parents" + SLASH + "ms-dos.parent";
	private static final String DOS_METADATA_GAME_DIR = "!dos";

	private static final String EXODEMOSCN_DIR = "eXoDemoScn";
	private static final String DEMO_METADATA_FILE = "!DemoScenemetadata.zip";
	private static final String XODEMO_METADATA_FILE = "XODemoSceneMetadata.zip";
	private static final String DEMO_VIDEOS_FILE = "DSNVideos.zip";
	private static final String DEMO_METADATA_XML = "xml"+ SLASH + "all" + SLASH + DEMOSCN + ".xml";
	private static final String DEMO_METADATA_GAME_DIR = "!demoscn";

	private static final String MEDIAPACK_BOOKS_FILE = "DOSBooks.zip";
	private static final String MEDIAPACK_CATALOGS_FILE = "DOSCatalogs.zip";
	private static final String MEDIAPACK_MAGAZINES_FILE = "DOSMagazines.zip";
	private static final String MEDIAPACK_SOUNDTRACKS_FILE = "DOSSoundtracks.zip";
	private static final String MEDIAPACK_VIDEOS_FILE = "DOSVideos.zip";

	private static final String IMAGES_DIR = "Images";
	private static final String MANUALS_DIR = "Manuals";
	private static final String MUSIC_DIR = "Music";
	
	static final String BOOKS_DIR = "Books";
	static final String CATALOGS_DIR = "Catalogs";
	static final String MAGAZINES_DIR = "Magazines";
	static final String SOUNDTRACKS_DIR = "Soundtracks";
	static final String VIDEOS_DIR = "Videos";

	static final String MEDIAPACK_BOOKS_DIR = EXO_DIR + SLASH + BOOKS_DIR;
	static final String MEDIAPACK_CATALOGS_DIR = EXO_DIR + SLASH + CATALOGS_DIR;
	static final String MEDIAPACK_MAGAZINES_DIR = EXO_DIR + SLASH + MAGAZINES_DIR;
	static final String MEDIAPACK_SOUNDTRACKS_DIR = EXO_DIR + SLASH + SOUNDTRACKS_DIR;
	static final String MEDIAPACK_VIDEOS_DIR = EXO_DIR + SLASH + VIDEOS_DIR;

	private static final String CONTENT_DIR = "Content";
	
	static final String EXTRAS_DIR = "Extras";
	static final String BASE_IMPORT_DIR = "exo";
	static final String DOSBOX_IMPORT_DIR = BASE_IMPORT_DIR + SLASH + "DOSBox";
	static final File IMPORT_BOOKS_DIR = new File(BASE_IMPORT_DIR, ExoContext.BOOKS_DIR);
	static final File IMPORT_CATALOGS_DIR = new File(BASE_IMPORT_DIR, ExoContext.CATALOGS_DIR);
	static final File IMPORT_MAGAZINES_DIR = new File(BASE_IMPORT_DIR, ExoContext.MAGAZINES_DIR);
	static final File IMPORT_SOUNDTRACKS_DIR = new File(BASE_IMPORT_DIR, ExoContext.SOUNDTRACKS_DIR);
	static final File IMPORT_VIDEOS_DIR = new File(BASE_IMPORT_DIR, ExoContext.VIDEOS_DIR);
	
	private static final String V5_GPA_BASE_FILENAME = "eXoV5";
	private static final String V6_GPA_BASE_FILENAME = "eXoV6";
	private static final String DEMO_GPA_BASE_FILENAME = "eXoDemoScene";
	
	private static final Map<String, DosboxVersionExo> EXODOS_DOSBOX_EXE_TO_EXO = Map.ofEntries(
		Map.entry("daum\\dosbox.exe", new DosboxVersionExo("DOSBox SVN Daum Jan 15, 2015", SettingsService.OFFICIAL_DOSBOX_FAM.title_, SettingsService.OFFICIAL_DOSBOX_FAM.versions_.get(6), null, null, true)),
		Map.entry("dosbox.exe", new DosboxVersionExo("DOSBox 0.74-3", SettingsService.OFFICIAL_DOSBOX_FAM.title_, SettingsService.LATEST_SUPPORTED_DOSBOX_RELEASE, "dosbox0.73\\dosbox.exe", null, true)),
		Map.entry("ece4230\\dosbox.exe", new DosboxVersionExo("DOSBox ECE r4230", SettingsService.ECE_DOSBOX_FAM.title_, SettingsService.ECE_DOSBOX_FAM.versions_.get(0), null, null, true)),
		Map.entry("ece4460\\dosbox.exe", new DosboxVersionExo("DOSBox ECE r4460", SettingsService.ECE_DOSBOX_FAM.title_, SettingsService.ECE_DOSBOX_FAM.versions_.get(0), null, null, true)),
		Map.entry("GunStick_dosbox\\dosbox.exe", new DosboxVersionExo("DOSBox Gunstick", SettingsService.OFFICIAL_DOSBOX_FAM.title_, SettingsService.OFFICIAL_DOSBOX_FAM.versions_.get(6), null, "dosbox.conf", true)),
		Map.entry("staging0.81.1\\dosbox.exe", new DosboxVersionExo("DOSBox Staging 0.81.1", SettingsService.STAGING_DOSBOX_FAM.title_, SettingsService.STAGING_DOSBOX_FAM.versions_.get(1), "staging0.81.1\\dosbox.exe", "dosbox-staging.conf", true)),
		Map.entry("staging0.82.0\\dosbox.exe", new DosboxVersionExo("DOSBox Staging 0.82.0", SettingsService.STAGING_DOSBOX_FAM.title_, SettingsService.STAGING_DOSBOX_FAM.versions_.get(3), "staging0.82.0\\dosbox.exe", "dosbox-staging.conf", true)),
		Map.entry("x\\dosbox.exe", new DosboxVersionExo("DOSBox-X 0.82.18", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, SettingsService.DOSBOX_X_DOSBOX_FAM.versions_.get(0), null, "dosbox.reference.conf", true)),
		Map.entry("x2\\dosbox.exe", new DosboxVersionExo("DOSBox-X 0.83.25", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, SettingsService.DOSBOX_X_DOSBOX_FAM.versions_.get(0), null, "dosbox-x.reference.full.conf", true)),
		Map.entry("dosbox0.63\\dosbox.exe", new DosboxVersionExo("DOSBox 0.63", SettingsService.OFFICIAL_DOSBOX_FAM.title_, SettingsService.OFFICIAL_DOSBOX_FAM.versions_.get(0), null, null, false)),
		Map.entry("dosbox0.73\\dosbox.exe", new DosboxVersionExo("DOSBox 0.74-3", SettingsService.OFFICIAL_DOSBOX_FAM.title_, SettingsService.LATEST_SUPPORTED_DOSBOX_RELEASE, null, null, true)),
		Map.entry("DWDdosbox\\dosbox.exe", new DosboxVersionExo("DOSBox R17 by David Walters", SettingsService.OFFICIAL_DOSBOX_FAM.title_, SettingsService.OFFICIAL_DOSBOX_FAM.versions_.get(6), null, "dosbox.conf", true)),
		Map.entry("ece4481\\dosbox.exe", new DosboxVersionExo("DOSBox ECE r4481", SettingsService.ECE_DOSBOX_FAM.title_, SettingsService.ECE_DOSBOX_FAM.versions_.get(0), null, null, true)),
		Map.entry("staging0.80.1\\dosbox.exe", new DosboxVersionExo("DOSBox Staging 0.80.1", SettingsService.STAGING_DOSBOX_FAM.title_, SettingsService.STAGING_DOSBOX_FAM.versions_.get(0), null, "dosbox-staging.conf", true)),
		Map.entry("staging0.81.0a\\dosbox.exe", new DosboxVersionExo("DOSBox Staging 0.81.0a", SettingsService.STAGING_DOSBOX_FAM.title_, SettingsService.STAGING_DOSBOX_FAM.versions_.get(0), null, "dosbox-staging.conf", true)),
		Map.entry("tc_dosbox\\dosbox.exe", new DosboxVersionExo("DOSBox TC", SettingsService.SVN_DOSBOX_FAM.title_, SettingsService.SVN_DOSBOX_FAM.versions_.get(0), null, null, true)),
		Map.entry("mpubuild_dosbox\\dosbox.exe", new DosboxVersionExo("DOSBox MPU", SettingsService.OFFICIAL_DOSBOX_FAM.title_, SettingsService.OFFICIAL_DOSBOX_FAM.versions_.get(6), null, null, true))
	);
	private static final Map<String, DosboxVersionExo> EXODOS_DOSBOX_LINUX_TO_EXO = Map.ofEntries(
		Map.entry("flatpak run com.retro_exo.dosbox-074r3-1", new DosboxVersionExo("DOSBox 0.74-3", SettingsService.OFFICIAL_DOSBOX_FAM.title_, SettingsService.LATEST_SUPPORTED_DOSBOX_RELEASE, "0.74-31/dosbox", null, true)),
		Map.entry("flatpak run com.retro_exo.dosbox-ece-r4301", new DosboxVersionExo("DOSBox ECE r4301", SettingsService.ECE_DOSBOX_FAM.title_, SettingsService.ECE_DOSBOX_FAM.versions_.get(0), "ECEr4301/dosbox", null, true)),
		Map.entry("flatpak run com.retro_exo.dosbox-ece-r4482", new DosboxVersionExo("DOSBox ECE r4482", SettingsService.ECE_DOSBOX_FAM.title_, SettingsService.ECE_DOSBOX_FAM.versions_.get(0), "ECEr4482/dosbox", null, true)),
		Map.entry("flatpak run com.retro_exo.dosbox-staging-081-2", new DosboxVersionExo("DOSBox Staging 0.81.2", SettingsService.STAGING_DOSBOX_FAM.title_, SettingsService.STAGING_DOSBOX_FAM.versions_.get(2), "Staging0812/dosbox", null, true)),
		Map.entry("flatpak run com.retro_exo.dosbox-staging-082-0", new DosboxVersionExo("DOSBox Staging 0.82.0", SettingsService.STAGING_DOSBOX_FAM.title_, SettingsService.STAGING_DOSBOX_FAM.versions_.get(3), "Staging0820/dosbox", null, true)),
		Map.entry("flatpak run com.retro_exo.dosbox-x-08220", new DosboxVersionExo("DOSBox-X 0.82.20", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, SettingsService.DOSBOX_X_DOSBOX_FAM.versions_.get(0), "X08220/dosbox", null, true)),
		Map.entry("flatpak run com.retro_exo.dosbox-x-20240701", new DosboxVersionExo("DOSBox-X 2024.07.01", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, SettingsService.DOSBOX_X_DOSBOX_FAM.versions_.get(2), "X20240701/dosbox", null, true)),
		Map.entry("flatpak run com.retro_exo.wine emulators/dosbox/daum/dosbox.exe", new DosboxVersionExo("DOSBox SVN Daum Jan 15, 2015", SettingsService.DAUM_DOSBOX_FAM.title_, SettingsService.DAUM_DOSBOX_FAM.versions_.get(0), "daum/dosbox", null, true)),
		Map.entry("flatpak run com.retro_exo.wine emulators/dosbox/DOSBox.exe", new DosboxVersionExo("DOSBox 0.74-3", SettingsService.OFFICIAL_DOSBOX_FAM.title_, SettingsService.LATEST_SUPPORTED_DOSBOX_RELEASE, "dosbox0.73/dosbox", null, true)),
		Map.entry("flatpak run com.retro_exo.wine emulators/dosbox/GunStick_dosbox/dosbox.exe", new DosboxVersionExo("DOSBox Gunstick", SettingsService.OFFICIAL_DOSBOX_FAM.title_, SettingsService.OFFICIAL_DOSBOX_FAM.versions_.get(6), "GunStick_dosbox/dosbox", null, true))
	);
	private static final Map<String, DosboxVersionExo> DEMO_DOSBOX_EXE_TO_EXO = Map.ofEntries(
		Map.entry("dosbox.exe", new DosboxVersionExo("DOSBox 0.74-3", SettingsService.OFFICIAL_DOSBOX_FAM.title_, SettingsService.LATEST_SUPPORTED_DOSBOX_RELEASE, "SVN\\DOSBox.exe", null, true)),
		Map.entry("ece4481\\dosbox.exe", new DosboxVersionExo("DOSBox ECE r4481", SettingsService.ECE_DOSBOX_FAM.title_, SettingsService.ECE_DOSBOX_FAM.versions_.get(0), "ece4481\\DOSBox.exe", null, true)),
		Map.entry("staging0.81.1\\dosbox.exe", new DosboxVersionExo("DOSBox Staging 0.81.1", SettingsService.STAGING_DOSBOX_FAM.title_, SettingsService.STAGING_DOSBOX_FAM.versions_.get(1), "staging0.81.1\\dosbox.exe", "dosbox-staging.conf", true)),
		Map.entry("staging0.82.0\\dosbox.exe", new DosboxVersionExo("DOSBox Staging 0.82.0", SettingsService.STAGING_DOSBOX_FAM.title_, SettingsService.STAGING_DOSBOX_FAM.versions_.get(3), "staging0.82.0\\dosbox.exe", "dosbox-staging.conf", true)),
		Map.entry("mingw\\dosbox-x.exe", new DosboxVersionExo("DOSBox-X 2024.07.01 MingW", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, SettingsService.DOSBOX_X_DOSBOX_FAM.versions_.get(2), "mingw\\dosbox-x.exe", "dosbox-x.reference.full.conf", true)),
		Map.entry("X_2024\\dosbox-x.exe", new DosboxVersionExo("DOSBox-X 2024.07.01", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, SettingsService.DOSBOX_X_DOSBOX_FAM.versions_.get(2), "X_2024\\dosbox-x.exe", "dosbox-x.reference.full.conf", true)),
		Map.entry("X_Nov\\dosbox-x",     new DosboxVersionExo("DOSBox-X 2024.10.01", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, SettingsService.DOSBOX_X_DOSBOX_FAM.versions_.get(3), "X_Nov\\dosbox-x.exe", "dosbox-x.reference.full.conf", true)),
		Map.entry("X_Nov\\dosbox-x.exe", new DosboxVersionExo("DOSBox-X 2024.10.01", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, SettingsService.DOSBOX_X_DOSBOX_FAM.versions_.get(3), "X_Nov\\dosbox-x.exe", "dosbox-x.reference.full.conf", true)),
		Map.entry("x\\dosbox.exe", new DosboxVersionExo("DOSBox-X 0.82.18", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, SettingsService.DOSBOX_X_DOSBOX_FAM.versions_.get(0), "x\\dosbox.exe", "dosbox.reference.conf", true)),
		Map.entry("X\\dosbox.exe", new DosboxVersionExo("DOSBox-X 0.82.18", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, SettingsService.DOSBOX_X_DOSBOX_FAM.versions_.get(0), "x\\dosbox.exe", "dosbox.reference.conf", true))
	);
	

	private final File exoDir_, exoUtilDir_, contentDir_, gameZipsDir_;
	private final File utilZipFile_, metadataZipFile_, xoMetadataZipFile_;
	private final ExoDosVersion version_;

	public ExoContext(File srcDir) {
		exoDir_ = new File(srcDir, EXO_DIR);
		exoUtilDir_ = new File(exoDir_, UTIL_DIR);
		utilZipFile_ = new File(exoUtilDir_, FilesUtils.isExistingFile(new File(exoUtilDir_, UTILDSN_FILE)) ? UTILDSN_FILE: UTIL_FILE);
		contentDir_ = new File(srcDir, CONTENT_DIR);

		if (!srcDir.exists()) {
			System.out.println("The directory [" + srcDir + "] does not exist.");
			metadataZipFile_ = null;
			xoMetadataZipFile_ = null;
			gameZipsDir_ = null;
			version_ = ExoDosVersion.UNKNOWN;
			return;
		}
		if (!exoDir_.exists()) {
			System.out.println("The directory [" + srcDir + "] does not contain the [" + EXO_DIR + "] directory.");
			metadataZipFile_ = null;
			xoMetadataZipFile_ = null;
			gameZipsDir_ = null;
			version_ = ExoDosVersion.UNKNOWN;
			return;
		}
		if (!exoUtilDir_.exists()) {
			System.out.println("The directory [" + exoDir_ + "] does not contain the [" + UTIL_DIR + "] directory.");
			metadataZipFile_ = null;
			xoMetadataZipFile_ = null;
			gameZipsDir_ = null;
			version_ = ExoDosVersion.UNKNOWN;
			return;
		}
		if (!FilesUtils.isExistingFile(utilZipFile_)) {
			System.out.println("The file [" + utilZipFile_ + "] does not exist.");
			metadataZipFile_ = null;
			xoMetadataZipFile_ = null;
			gameZipsDir_ = null;
			version_ = ExoDosVersion.UNKNOWN;
			return;
		}
		if (!contentDir_.exists()) {
			System.out.println("The directory [" + srcDir + "] does not contain the [" + CONTENT_DIR + "] directory.");
			metadataZipFile_ = null;
			xoMetadataZipFile_ = null;
			gameZipsDir_ = null;
			version_ = ExoDosVersion.UNKNOWN;
			return;
		}

		File exodosDir = new File(exoDir_, EXODOS_DIR);
		File demosceneDir = new File(exoDir_, EXODEMOSCN_DIR);

		if (exodosDir.exists()) {
			metadataZipFile_ = new File(contentDir_, DOS_METADATA_FILE);
			xoMetadataZipFile_ = new File(contentDir_, XODOS_METADATA_FILE);
			gameZipsDir_ = exodosDir;
		} else if (demosceneDir.exists()) {
			metadataZipFile_ = new File(contentDir_, DEMO_METADATA_FILE);
			xoMetadataZipFile_ = new File(contentDir_, XODEMO_METADATA_FILE);
			gameZipsDir_ = demosceneDir;
		} else {
			System.out.println("The directory [" + exoDir_ + "] does not contain the [" + EXODOS_DIR + "] or ["
					+ EXODEMOSCN_DIR + "] directory.");
			metadataZipFile_ = null;
			xoMetadataZipFile_ = null;
			gameZipsDir_ = null;
			version_ = ExoDosVersion.UNKNOWN;
			return;
		}

		if (!FilesUtils.isExistingFile(metadataZipFile_)) {
			System.out.println("The file [" + metadataZipFile_ + "] does not exist.");
			version_ = ExoDosVersion.UNKNOWN;
			return;
		}
		if (!FilesUtils.isExistingFile(xoMetadataZipFile_)) {
			System.out.println("The file [" + xoMetadataZipFile_ + "] does not exist.");
			version_ = ExoDosVersion.UNKNOWN;
			return;
		}

		List<ZipEntry> entries = ExoUtils.listEntries(xoMetadataZipFile_, true);
		if (entries.parallelStream().anyMatch(x -> x.getName().equals(V5_METADATA_XML)))
			version_ = ExoDosVersion.V5;
		else if (entries.parallelStream().anyMatch(x -> x.getName().equals(V6_METADATA_XML)))
			version_ = ExoDosVersion.V6;
		else if (entries.parallelStream().anyMatch(x -> x.getName().equals(V6_R2_METADATA_XML)))
			version_ = entries.parallelStream().anyMatch(x -> x.getName().equals(V6_R4_METADATA_PARENTS)) ? ExoDosVersion.V6_R4: ExoDosVersion.V6_R2;
		else if (entries.parallelStream().anyMatch(x -> x.getName().equals(DEMO_METADATA_XML)))
			version_ = ExoDosVersion.DEMOSCENE_V2_0;
		else
			version_ = ExoDosVersion.UNKNOWN;
	}

	public ExoDosVersion version() {
		return version_;
	}
	
	public File gameZipsDir() {
		return gameZipsDir_;
	}

	public boolean isMediapackAvailable() {
		return EnumSet.of(ExoDosVersion.V6, ExoDosVersion.V6_R2, ExoDosVersion.V6_R4).contains(version_)
			&& FilesUtils.isExistingFile(booksZipFile())
			&& FilesUtils.isExistingFile(catalogsZipFile())
			&& FilesUtils.isExistingFile(magazinesZipFile())
			&& FilesUtils.isExistingFile(soundtracksZipFile());
	}
	
	public boolean useLinuxPatch() {
		return (version_ == ExoDosVersion.V6_R4) && FilesUtils.isExistingFile(new File(exoUtilDir_, UTIL_FILE_LINUX)) && SystemUtils.IS_LINUX;
	}

	public File booksZipFile() {
		return new File(contentDir_, MEDIAPACK_BOOKS_FILE);
	}

	public File catalogsZipFile() {
		return new File(contentDir_, MEDIAPACK_CATALOGS_FILE);
	}

	public File magazinesZipFile() {
		return new File(contentDir_, MEDIAPACK_MAGAZINES_FILE);
	}

	public File soundtracksZipFile() {
		return new File(contentDir_, MEDIAPACK_SOUNDTRACKS_FILE);
	}
	
	public File videosZipFile() {
		return new File(contentDir_, MEDIAPACK_VIDEOS_FILE);
	}

	public List<ZipEntry> bookEntries() {
		return ExoUtils.listEntries(booksZipFile(), x -> x.startsWith(MEDIAPACK_BOOKS_DIR + SLASH), true);
	}

	public List<ZipEntry> catalogEntries() {
		return ExoUtils.listEntries(catalogsZipFile(), x -> x.startsWith(MEDIAPACK_CATALOGS_DIR + SLASH), true);
	}

	public List<ZipEntry> magazineEntries() {
		return ExoUtils.listEntries(magazinesZipFile(), x -> x.startsWith(MEDIAPACK_MAGAZINES_DIR + SLASH), true);
	}

	public List<ZipEntry> soundtrackEntries() {
		return ExoUtils.listEntries(soundtracksZipFile(), x -> x.startsWith(MEDIAPACK_SOUNDTRACKS_DIR + SLASH), true);
	}
	
	public List<ZipEntry> videoEntries() {
		return ExoUtils.listEntries(videosZipFile(), x -> x.startsWith(MEDIAPACK_VIDEOS_DIR + SLASH), true);
	}
	
	public List<ZipEntry> demoVideoEntries() {
		return ExoUtils.listEntries(demoVideosZipFile(), x -> x.startsWith(MEDIAPACK_VIDEOS_DIR + SLASH), true);
	}

	public File metadataZipFile() {
		return new File(contentDir_,
			EnumSet.of(ExoDosVersion.DEMOSCENE_V2_0).contains(version_) ? DEMO_METADATA_FILE : DOS_METADATA_FILE);
	}

	public File xoMetadataZipFile() {
		return new File(contentDir_,
			EnumSet.of(ExoDosVersion.DEMOSCENE_V2_0).contains(version_) ? XODEMO_METADATA_FILE : XODOS_METADATA_FILE);
	}

	public File demoVideosZipFile() {
		return new File(contentDir_, DEMO_VIDEOS_FILE);
	}

	public String xoMetadataGamesXml() {
		return switch (version_) {
			case V5 -> V5_METADATA_XML;
			case V6 -> V6_METADATA_XML;
			case V6_R2, V6_R4 -> V6_R2_METADATA_XML;
			case DEMOSCENE_V2_0 -> DEMO_METADATA_XML;
			default -> null;
		};
	}

	public String xoMetadataImages() {
		return IMAGES_DIR + SLASH + (EnumSet.of(ExoDosVersion.DEMOSCENE_V2_0).contains(version_) ? DEMOSCN : MSDOS) + SLASH;
	}
	
	public String xoMetadataManuals() {
		return MANUALS_DIR + SLASH+  (EnumSet.of(ExoDosVersion.DEMOSCENE_V2_0).contains(version_) ? DEMOSCN : MSDOS) + SLASH;
	}
	
	public String xoMetadataMusic() {
		return MUSIC_DIR + SLASH + (EnumSet.of(ExoDosVersion.DEMOSCENE_V2_0).contains(version_) ? DEMOSCN : MSDOS) + SLASH;
	}
	
	public String xoMetadataVideos() {
		return VIDEOS_DIR + SLASH + (EnumSet.of(ExoDosVersion.DEMOSCENE_V2_0).contains(version_) ? DEMOSCN : MSDOS) + SLASH;
	}
	
	public String xoMetadataGameDir(String gameDir) {
		return EXO_DIR + SLASH +
				(EnumSet.of(ExoDosVersion.DEMOSCENE_V2_0).contains(version_) ? EXODEMOSCN_DIR: EXODOS_DIR) + SLASH +
				(EnumSet.of(ExoDosVersion.DEMOSCENE_V2_0).contains(version_) ? DEMO_METADATA_GAME_DIR : DOS_METADATA_GAME_DIR) + SLASH +
				gameDir;
	}
	
	public String xoMetadataExtras(String gameDir) {
		return xoMetadataGameDir(gameDir) + SLASH + EXTRAS_DIR + SLASH;
	}

	public File utilZipFile() {
		return utilZipFile_;
	}
	
	public File outerZipFileContainingGameDosboxversions() {
		return useLinuxPatch() ? new File(exoUtilDir_, UTIL_FILE_LINUX): utilZipFile_;
	}
	
	public String innerZipFileContainingGameDosboxversions() {
		return useLinuxPatch() ? UTIL_EXTDOS_LINUX_FILE : utilExtZip();
	}

	public String utilExtZip() {
		return EnumSet.of(ExoDosVersion.DEMOSCENE_V2_0).contains(version_) ? UTIL_EXTDEMOSCN_FILE : UTIL_EXTDOS_FILE;
	}

	public String utilDosboxTxt() {
		return EnumSet.of(ExoDosVersion.DEMOSCENE_V2_0).contains(version_)
			? UTIL_DEMOSCN_TXT_FILE : (useLinuxPatch() ? UTIL_DOSBOX_LINUX_TXT_FILE: UTIL_DOSBOX_TXT_FILE);
	}

	public boolean launchboxGamesXmlIsComplete() {
		return version_ != ExoDosVersion.V6;
	}

	public boolean hasDosboxVersions() {
		return version_ != ExoDosVersion.V5;
	}

	public boolean hasRoms() {
		return EnumSet.of(ExoDosVersion.V6, ExoDosVersion.V6_R2, ExoDosVersion.V6_R4, ExoDosVersion.DEMOSCENE_V2_0).contains(version_);
	}
	
	public boolean hasDemoVideos() {
		return version_ == ExoDosVersion.DEMOSCENE_V2_0;
	}

	public boolean hasPlaylists() {
		return EnumSet.of(ExoDosVersion.V6, ExoDosVersion.V6_R2, ExoDosVersion.V6_R4).contains(version_);
	}

	public File gameZipFile(String gameZipFile) {
		return new File(gameZipsDir_, gameZipFile);
	}

	public boolean extrasInXoDosMetadata() {
		return version_ == ExoDosVersion.V5;
	}

	public File gameExtrasZipFile(String gameZipFile) {
		return FileUtils.getFile(contentDir_, "GameData",
			EnumSet.of(ExoDosVersion.DEMOSCENE_V2_0).contains(version_) ? EXODEMOSCN_DIR : EXODOS_DIR, gameZipFile);
	}
	
	public String gpaFileName(String firstTitle, String lastTitle) {
		String name = switch (version_) {
			case V5 -> V5_GPA_BASE_FILENAME;
			case V6, V6_R2, V6_R4 -> V6_GPA_BASE_FILENAME;
			case DEMOSCENE_V2_0 -> DEMO_GPA_BASE_FILENAME;
			default -> null;
		};
		name += "__" + FilesUtils.toSafeFilename(firstTitle);
		if (lastTitle != null)
			name += " - " + FilesUtils.toSafeFilename(lastTitle);
		return name + FilesUtils.GAMEPACKARCHIVE_EXT;
	}

	public Map<String, DosboxVersionExo> dosboxMap() {
		return EnumSet.of(ExoDosVersion.DEMOSCENE_V2_0).contains(version_)
			? DEMO_DOSBOX_EXE_TO_EXO: (useLinuxPatch() ? EXODOS_DOSBOX_LINUX_TO_EXO: EXODOS_DOSBOX_EXE_TO_EXO);
	}
}