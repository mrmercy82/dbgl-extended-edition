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
package org.dbgl.model.conf.dfend;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.FileLocation;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.conf.Settings;
import org.dbgl.service.TextService;
import org.dbgl.util.FilesUtils;


public class DFendConfiguration {

	private Settings datConfiguration_;
	private Map<String, DFendProfile> profiles_;

	public DFendConfiguration(File file) {
		datConfiguration_ = new Settings();
		datConfiguration_.setFileLocation(new FileLocation(file.getPath()));
		profiles_ = new LinkedHashMap<>();
	}

	public String loadDat() throws IOException {
		StringBuilder warningsLog = new StringBuilder();
		warningsLog.append(datConfiguration_.load(TextService.getInstance()));
		for (String title: getSectionNames()) {
			String profFile = datConfiguration_.getValue(title, "prof");
			String confFile = datConfiguration_.getValue(title, "conf");
			Configuration prof = new Configuration();
			prof.setFileLocation(new FileLocation(profFile));
			Configuration conf = new Configuration();
			conf.setFileLocation(new FileLocation(confFile));

			File confsDir = new File(datConfiguration_.getCanonicalFile().getParent(), "Confs");
			if (!FilesUtils.isExistingFile(prof.getCanonicalFile())) {
				File alternative = new File(confsDir, prof.getFile().getName());
				if (FilesUtils.isExistingFile(alternative))
					prof.setFileLocation(new FileLocation(alternative.getPath()));
			}
			if (!FilesUtils.isExistingFile(conf.getCanonicalFile())) {
				File alternative = new File(confsDir, conf.getFile().getName());
				if (FilesUtils.isExistingFile(alternative))
					conf.setFileLocation(new FileLocation(alternative.getPath()));
			}

			profiles_.put(title, new DFendProfile(prof, conf));
		}
		return warningsLog.toString();
	}

	public String loadProfile(String title) throws IOException {
		StringBuilder warningsLog = new StringBuilder();
		warningsLog.append(profiles_.get(title).getProf().load(TextService.getInstance()));
		warningsLog.append(profiles_.get(title).load(TextService.getInstance()));
		return warningsLog.toString();
	}

	public String[] getSectionNames() {
		return datConfiguration_.getSectionNames();
	}

	public String getValue(String sectionTitle, String sectionItem) {
		return datConfiguration_.getValue(sectionTitle, sectionItem);
	}

	public String getValue(String profileTitle, String sectionTitle, String sectionItem) {
		return StringUtils.defaultString(profiles_.get(profileTitle).getProf().getValue(sectionTitle, sectionItem));
	}

	public File getConfFile(String profileTitle) {
		return profiles_.get(profileTitle).getConf().getFile();
	}

	public File getConfCanonicalFile(String profileTitle) {
		return profiles_.get(profileTitle).getConf().getCanonicalFile();
	}

	public String getConf(String profileTitle) {
		return profiles_.get(profileTitle).getConf().toString(null);
	}
}
