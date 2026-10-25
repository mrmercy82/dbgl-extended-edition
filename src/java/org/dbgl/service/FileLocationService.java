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
package org.dbgl.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.FileLocation;
import org.dbgl.model.ICanonicalize;
import org.dbgl.model.SearchResult;
import org.dbgl.model.SearchResult.ResultType;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.factory.DosboxVersionFactory;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.SystemUtils;


public class FileLocationService {

	public static final String PROFILES_DIR_STRING = "profiles" + File.separatorChar;
	public static final String TEMPLATES_DIR_STRING = "templates" + File.separatorChar;
	public static final String CAPTURES_DIR_STRING = "captures" + File.separatorChar;
	public static final String DOSROOT_DIR_STRING = "dosroot" + File.separatorChar;
	public static final String EXPORT_DIR_STRING = "export" + File.separatorChar;
	public static final String XSL_DIR_STRING = "xsl" + File.separatorChar;
	public static final String MAPPER_DIR_STRING = "mapper" + File.separatorChar;
	public static final String DATABASE_DIR_STRING = "db" + File.separatorChar;

	public static final String MOBYGAMES_DATABASE_DATA = "mobygames.data";
	public static final String MOBYGAMES_DATABASE_SCRIPT = "mobygames.script";
	public static final String MOBYGAMES_DATABASE_PROPERTIES = "mobygames.properties";

	public static final String SHADERS_DIR_STRING = "shaders" + File.separatorChar;

	public static final String DOSBOX_EXE_STRING = SystemUtils.IS_WINDOWS ? "DOSBox.exe": SystemUtils.IS_OSX ? "DOSBox": "dosbox";
	public static final String DB_APP_EXT = ".app";
	public static final String DB_APP = "DOSBox" + DB_APP_EXT;
	public static final String DB_APP_EXE = "/Contents/MacOS/" + DOSBOX_EXE_STRING;

	public static final String DOSBOX_CONF_STRING = "dosbox.conf";
	public static final String SETUP_CONF = "setup.conf";
	public static final String TEMPLATE_CONF = "template.conf";

	public static final String PROGRAM_FILES = SystemUtils.IS_WINDOWS ? System.getenv("ProgramFiles"): null;
	public static final String DFEND_PATH_STRING = StringUtils.isNotBlank(PROGRAM_FILES) ? FilesUtils.concat(PROGRAM_FILES, "D-Fend"): "D-Fend";
	public static final String DFEND_PROFILES_STRING = "Profiles.dat";
	public static final String DFEND_STRING = FilesUtils.concat(DFEND_PATH_STRING, DFEND_PROFILES_STRING);
	public static final String DFEND_RELOADED_PATH_STRING = FilesUtils.concat(SystemUtils.USER_HOME, "D-Fend Reloaded");

	private static final String DEFAULT_TEMPLATES_XML_STRING = "default.xml";

	private static final String[] SHADER_EXTS = {"fx"};
	private static final String XSL_EXT = "xsl";

	private final File dataDir_, profilesDir_, templatesDir_, capturesDir_, dosrootDir_, exportDir_, xslDir_, dosboxDir_, tmpInstDir_, shadersDir_;

	private FileLocationService() {
		SettingsService settings = SettingsService.getInstance();

		File data = replaceTildeInPath(settings.getValue("directory", "data"));
		boolean dataInUserDir = SystemUtils.USE_USER_HOME_DIR || !FilesUtils.isWritableDirectory(data);
		dataDir_ = canonicalTo(new File("."), dataInUserDir ? new File(FilesUtils.concat(SystemUtils.USER_DATA_DIR, data)): data);

		profilesDir_ = new File(dataDir_, PROFILES_DIR_STRING);
		templatesDir_ = new File(dataDir_, TEMPLATES_DIR_STRING);
		capturesDir_ = new File(dataDir_, CAPTURES_DIR_STRING);
		dosrootDir_ = new File(dataDir_, DOSROOT_DIR_STRING);
		exportDir_ = new File(dataDir_, EXPORT_DIR_STRING);
		xslDir_ = new File(dataDir_, XSL_DIR_STRING);
		dosboxDir_ = canonicalTo(new File("."), replaceTildeInPath(settings.getValue("directory", "dosbox")));
		tmpInstDir_ = new File(dosrootDir_, settings.getValue("directory", "tmpinstall"));
		shadersDir_ = new File(dosrootDir_, SHADERS_DIR_STRING);

		if (dataInUserDir) {
			FilesUtils.copyDirIfDestinationDoesNotExist(new File(data, PROFILES_DIR_STRING), profilesDir_);
			FilesUtils.copyDirIfDestinationDoesNotExist(new File(data, TEMPLATES_DIR_STRING), templatesDir_);
			FilesUtils.copyDirIfDestinationDoesNotExist(new File(data, CAPTURES_DIR_STRING), capturesDir_);
			FilesUtils.copyDirIfDestinationDoesNotExist(new File(data, DOSROOT_DIR_STRING), dosrootDir_);
			FilesUtils.copyDirIfDestinationDoesNotExist(new File(data, EXPORT_DIR_STRING), exportDir_);
			FilesUtils.copyDirIfDestinationDoesNotExist(new File(data, XSL_DIR_STRING), xslDir_);

			File databaseFile = getFileFromConnectionString(settings.getValue("database", "connectionstring"));
			if (databaseFile != null)
				FilesUtils.copyDirIfDestinationDoesNotExist(new File(data, databaseFile.getParent()), new File(dataDir_, databaseFile.getParent()));

			File mobygamesDbFile = getFileFromConnectionString(settings.getValue("mobygames_database", "connectionstring"));
			if (mobygamesDbFile != null) {
				FilesUtils.copyFileIfDestinationDoesNotExist(new File(mobygamesDbFile.getParentFile(), MOBYGAMES_DATABASE_DATA), new File(dataDir_, mobygamesDbFile.getParent()));
				FilesUtils.copyFileIfDestinationDoesNotExist(new File(mobygamesDbFile.getParentFile(), MOBYGAMES_DATABASE_SCRIPT), new File(dataDir_, mobygamesDbFile.getParent()));
				FilesUtils.copyFileIfDestinationDoesNotExist(new File(mobygamesDbFile.getParentFile(), MOBYGAMES_DATABASE_PROPERTIES), new File(dataDir_, mobygamesDbFile.getParent()));
			}
		}

		FilesUtils.createDirIfNonExisting(profilesDir_);
		FilesUtils.createDirIfNonExisting(templatesDir_);
		FilesUtils.createDirIfNonExisting(capturesDir_);
		FilesUtils.createDirIfNonExisting(dosrootDir_);
		FilesUtils.createDirIfNonExisting(exportDir_);
		FilesUtils.createDirIfNonExisting(xslDir_);
		FilesUtils.createDirIfNonExisting(dosboxDir_);
	}

	private static class FileLocationServiceHolder {
		private static final FileLocationService instance_ = new FileLocationService();
	}

	public static FileLocationService getInstance() {
		return FileLocationServiceHolder.instance_;
	}

	private static File canonicalTo(File base, File file) {
		if (base.isAbsolute() || file.isAbsolute()) {
			return new File(FilesUtils.concat(base, file));
		}
		try {
			return new File(base, file.getPath()).getCanonicalFile();
		} catch (IOException e) {
			return new File(base, file.getPath()).getAbsoluteFile();
		}
	}

	public ICanonicalize dataRelative() {
		return new ICanonicalize() {
			@Override
			public File initialize(String location) {
				return FilesUtils.makeRelativeTo(dataDir_, new File(location));
			}

			@Override
			public File canonicalize(File file) {
				return canonicalTo(dataDir_, file);
			}
		};
	}

	public ICanonicalize dosrootRelative() {
		return new ICanonicalize() {
			@Override
			public File initialize(String location) {
				return FilesUtils.makeRelativeTo(dosrootDir_, new File(location));
			}

			@Override
			public File canonicalize(File file) {
				return canonicalTo(dosrootDir_, file);
			}
		};
	}

	public ICanonicalize dosboxRelative() {
		return new ICanonicalize() {
			@Override
			public File initialize(String location) {
				return FilesUtils.makeRelativeTo(dosboxDir_, new File(location));
			}

			@Override
			public File canonicalize(File file) {
				return canonicalTo(dosboxDir_, file);
			}
		};
	}

	public static ICanonicalize standard() {
		return new ICanonicalize() {
			@Override
			public File initialize(String location) {
				return new File(location);
			}

			@Override
			public File canonicalize(File file) {
				return file;
			}
		};
	}

	public File getDataDir() {
		return dataDir_;
	}
	
	public File getDosroot() {
		return dosrootDir_;
	}

	public File getProfilesDir() {
		return profilesDir_;
	}

	public File getTmpInstallDir() {
		return tmpInstDir_;
	}

	public FileLocation getDosrootLocation() {
		return new FileLocation(".", dosrootRelative());
	}

	public FileLocation getTmpInstallLocation() {
		return new FileLocation(tmpInstDir_.getPath(), dosrootRelative());
	}

	public String getCanonicalConnectionString(String connString) {
		File file = getFileFromConnectionString(connString);
		if (file != null) {
			// Some magic on the connection string
			int start = connString.indexOf("file:") + 5; // skip 'file:'
			int end = connString.indexOf(';', start);
			if (end == -1)
				end = connString.length();
			return connString.substring(0, start) + new FileLocation(file.getPath(), dataRelative()).getCanonicalFile() + connString.substring(end);
		}
		return connString;
	}

	public static File getFileFromConnectionString(String connString) {
		if (connString.contains("file:")) {
			// Some magic on the connection string
			int start = connString.indexOf("file:") + 5; // skip 'file:'
			int end = connString.indexOf(';', start);
			if (end == -1)
				end = connString.length();
			return replaceTildeInPath(connString.substring(start, end));
		}
		return null;
	}

	private static File replaceTildeInPath(String path) {
		// Linux and OSX have ~/ for the user's home-directory. Allow single ~ as well
		if ((SystemUtils.IS_LINUX || SystemUtils.IS_OSX) && (path.equals("~") || path.startsWith("~/")))
			return new File(path.replaceAll("^~", SystemUtils.USER_HOME));
		return new File(path);
	}

	public FileLocation getUniqueTemplateConfigFileLocation(int templateId) {
		return new FileLocation(TEMPLATES_DIR_STRING + templateId + FilesUtils.CONF_EXT, dataRelative());
	}

	public FileLocation getUniqueProfileConfigFileLocation(int profileId, String profileTitle, File mainDir) {
		SettingsService settings = SettingsService.getInstance();
		File path = (settings.getIntValue("profiledefaults", "confpath") == 0) || (mainDir == null) ? profilesDir_: mainDir;
		String prefix = settings.getIntValue("profiledefaults", "conffile") == 0 ? String.valueOf(profileId): profileTitle;
		File candidate = null;
		int nr = 1;
		do {
			candidate = new File(path, FilesUtils.toSafeFilename(prefix + ((nr > 1) ? "(" + nr + ")": "")) + FilesUtils.CONF_EXT);
			nr++;
		} while (FilesUtils.isExistingFile(dataRelative().canonicalize(candidate)));
		return new FileLocation(candidate.getPath(), dataRelative());
	}

	public FileLocation getProfileCapturesFileLocation(int profileId) {
		SettingsService settings = SettingsService.getInstance();
		String s = FileLocationService.CAPTURES_DIR_STRING;
		if (settings.getIntValue("profiledefaults", "capturespath") == 1)
			s += String.valueOf(profileId);
		return new FileLocation(s, dataRelative());
	}

	public boolean isCapturesDir(File location) {
		return location.equals(capturesDir_);
	}

	public FileLocation getTmpConfLocation() {
		return new FileLocation(PROFILES_DIR_STRING + SETUP_CONF, dataRelative());
	}

	public File getDefaultTemplatesXmlFile() {
		return new File(templatesDir_, DEFAULT_TEMPLATES_XML_STRING);
	}

	public static String getGpaExportFile(String fullPath, String title) {
		if (StringUtils.isBlank(title))
			return title;
		File base = new File(fullPath).getParentFile();
		if (base == null)
			base = new File(EXPORT_DIR_STRING);
		return new File(base, FilesUtils.toSafeFilename(title) + FilesUtils.GAMEPACKARCHIVE_EXT).getPath();
	}

	public String[] listGlShaderFilenames() {
		return new String[0];
	}

	public String[] listShaderFilenames() {
		if (shadersDir_.exists() && shadersDir_.isDirectory())
			return FileUtils.listFiles(shadersDir_, SHADER_EXTS, false).stream().sorted().map(File::getName).toArray(String[]::new);
		return new String[0];
	}

	public boolean hasXslDir() {
		return xslDir_.exists() && xslDir_.isDirectory();
	}

	public String[] listXslBaseNames() {
		if (hasXslDir())
			return FileUtils.listFiles(xslDir_, new String[] {XSL_EXT}, false).stream().sorted().map(x -> FilenameUtils.getBaseName(x.getName())).toArray(String[]::new);
		return new String[0];
	}

	public File xslBaseNameToFile(String baseName) {
		return new File(xslDir_, baseName + FilenameUtils.EXTENSION_SEPARATOR + XSL_EXT);
	}

	public SearchResult findDosbox() {
		List<String> allVersions = new ArrayList<>(SettingsService.SUPPORTED_DOSBOX_RELEASES);
		Collections.reverse(allVersions);
		return allVersions.stream().map(this::findDosbox).filter(x -> x.result_ != ResultType.NOTFOUND).findFirst().orElse(new SearchResult(ResultType.NOTFOUND, null));
	}

	private SearchResult findDosbox(String versionToSearch) {
		File canExePath = null;
		File canConf = null;
		File canConfSuggestion = null;
		String version = null;

		String dosboxDirectoryString = "DOSBox-" + versionToSearch;
		File dosboxDirectory = new File(dosboxDir_, dosboxDirectoryString);

		if (FilesUtils.isExistingDirectory(dosboxDirectory)) {
			File exe = new File(dosboxDirectory, DOSBOX_EXE_STRING);

			if (FilesUtils.isExistingFile(exe)) {
				canExePath = exe.getParentFile();
				version = versionToSearch;
			}
		}

		if (canExePath == null) {
			if (SystemUtils.IS_WINDOWS) {
				String programFiles = System.getenv("ProgramFiles(x86)");
				if (programFiles == null)
					programFiles = System.getenv("ProgramFiles");

				File exePF = new File(new File(programFiles, dosboxDirectoryString), DOSBOX_EXE_STRING);
				if (FilesUtils.isExistingFile(exePF)) {
					canExePath = exePF.getParentFile();
					version = versionToSearch;
				}
			} else if (SystemUtils.IS_LINUX) {
				File exePF = new File("/usr/bin", DOSBOX_EXE_STRING);
				if (FilesUtils.isExistingFile(exePF)) {
					canExePath = exePF.getParentFile();
				}
			} else if (SystemUtils.IS_OSX) {
				File exePF = new File("/Applications/DOSBox.app/Contents/MacOS", DOSBOX_EXE_STRING);
				if (FilesUtils.isExistingFile(exePF)) {
					canExePath = exePF.getParentFile();
				}
			}
		}

		if (canExePath != null) {
			File conf = new File(canExePath, DOSBOX_CONF_STRING);
			if (FilesUtils.isExistingFile(conf)) {
				canConf = conf;
			} else {
				if (SystemUtils.IS_WINDOWS) {
					File confLAD = new File(new File(System.getenv("LOCALAPPDATA"), "DOSBox"), "dosbox-" + versionToSearch + ".conf");
					if (FilesUtils.isExistingFile(confLAD)) {
						canConf = confLAD;
						version = versionToSearch;
					} else {
						canConfSuggestion = confLAD;
					}
				} else if (SystemUtils.IS_LINUX) {
					File confLAD = new File(new File(SystemUtils.USER_HOME, ".dosbox"), "dosbox-" + versionToSearch + ".conf");
					if (FilesUtils.isExistingFile(confLAD)) {
						canConf = confLAD;
						version = versionToSearch;
					} else {
						canConfSuggestion = confLAD;
					}
				} else if (SystemUtils.IS_OSX) {
					File confLAD = new File(new File(SystemUtils.USER_HOME, "Library/Preferences"), "DOSBox " + versionToSearch + " Preferences");
					if (FilesUtils.isExistingFile(confLAD)) {
						canConf = confLAD;
						version = versionToSearch;
					} else {
						canConfSuggestion = confLAD;
					}
				}
			}
		}

		FileLocation exePath = null;
		if (canExePath != null)
			exePath = new FileLocation(canExePath.getPath(), dosboxRelative());
		FileLocation confPath = null;
		if (canConf != null)
			confPath = new FileLocation(canConf.getPath(), dosboxRelative());
		else if (canConfSuggestion != null)
			confPath = new FileLocation(canConfSuggestion.getPath(), dosboxRelative());

		DosboxVersion db = DosboxVersionFactory.create("DOSBox " + (version != null ? version: SettingsService.LATEST_SUPPORTED_DOSBOX_RELEASE),
			SettingsService.OFFICIAL_DOSBOX_FAM.title_, version != null ? version: SettingsService.LATEST_SUPPORTED_DOSBOX_RELEASE, true, true, false, null, exePath != null ? exePath.getFile().getPath(): StringUtils.EMPTY, StringUtils.EMPTY,
			StringUtils.EMPTY, confPath != null ? confPath.getFile().getPath(): StringUtils.EMPTY);

		ResultType res = ResultType.NOTFOUND;
		if (canExePath != null) {
			res = ResultType.EXEONLY;

			if (canConf != null && version != null) {
				res = ResultType.COMPLETE;
			}
		}

		return new SearchResult(res, db);
	}
}