/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-01. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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
package org.dbgl.model.conf.dfend;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.FileLocation;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.conf.Settings;
import org.dbgl.service.TextService;
import org.dbgl.util.FilesUtils;


public class DFendReloadedConfiguration {

	private Settings datConfiguration_;
	private Map<File, DFendProfile> profiles_;

	public DFendReloadedConfiguration(File file) {
		datConfiguration_ = new Settings();
		datConfiguration_.setFileLocation(new FileLocation(file.getPath()));
		profiles_ = new LinkedHashMap<>();
	}

	public String loadDat(File profsPath, File confsPath) throws IOException {
		StringBuilder warningsLog = new StringBuilder();
		warningsLog.append(datConfiguration_.loadDetectingCharset(TextService.getInstance()));
		for (File profFile: FileUtils.listFiles(profsPath, new String[] {"prof"}, false)) {
			File confFile = new File(confsPath, FilenameUtils.removeExtension(profFile.getName()) + FilesUtils.CONF_EXT);
			Settings prof = new Settings(); // keeps [ExtraInfo] as written (a Configuration writes section names in lower case since alpha 46)
			prof.setFileLocation(new FileLocation(profFile.getPath()));
			Configuration conf = new Configuration();
			conf.setFileLocation(new FileLocation(confFile.getPath()));

			profiles_.put(profFile, new DFendProfile(prof, conf));
		}
		return warningsLog.toString();
	}

	/** alpha 48: the .prof files of a DFendX archive (without the settings of a D-Fend installation); a .conf next to one is used */
	public void loadArchive(List<File> profFiles) {
		for (File profFile: profFiles) {
			Settings prof = new Settings(); // keeps [ExtraInfo] as written (a Configuration writes section names in lower case since alpha 46)
			prof.setFileLocation(new FileLocation(profFile.getPath()));
			Configuration conf = new Configuration();
			conf.setFileLocation(new FileLocation(new File(profFile.getParentFile(), FilenameUtils.removeExtension(profFile.getName()) + FilesUtils.CONF_EXT).getPath()));
			profiles_.put(profFile, new DFendProfile(prof, conf));
		}
	}

	public String loadProfile(File profFile) throws IOException {
		StringBuilder warningsLog = new StringBuilder();
		warningsLog.append(profiles_.get(profFile).getProf().loadDetectingCharset(TextService.getInstance()));
		// without an exported .conf, the configuration is made from the .prof itself (since alpha 46)
		if (hasConf(profFile))
			warningsLog.append(profiles_.get(profFile).load(TextService.getInstance()));
		return warningsLog.toString();
	}

	/** whether D-Fend exported a .conf for this profile */
	public boolean hasConf(File profFile) {
		File conf = profiles_.get(profFile).getConf().getCanonicalFile();
		return (conf != null) && conf.isFile();
	}

	public Settings getProf(File profFile) {
		return profiles_.get(profFile).getProf();
	}

	public List<File> getConfFiles() {
		return new ArrayList<>(profiles_.keySet());
	}

	public String getValue(String sectionTitle, String sectionItem) {
		return datConfiguration_.getValue(sectionTitle, sectionItem);
	}

	public String getValue(File profFile, String sectionTitle, String sectionItem) {
		return StringUtils.defaultString(profiles_.get(profFile).getProf().getValue(sectionTitle, sectionItem));
	}

	public File getConfFile(File profFile) {
		return profiles_.get(profFile).getConf().getFile();
	}

	public File getConfCanonicalFile(File profFile) {
		return profiles_.get(profFile).getConf().getCanonicalFile();
	}

	public String getConf(File profFile) {
		return profiles_.get(profFile).getConf().toString(null);
	}
}
