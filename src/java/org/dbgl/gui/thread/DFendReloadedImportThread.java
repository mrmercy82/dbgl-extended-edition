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
package org.dbgl.gui.thread;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.dfend.DFendReloadedConfiguration;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.util.FilesUtils;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;


public class DFendReloadedImportThread extends UIThread<File> {

	private final boolean performCleanup_;
	private final DosboxVersion defaultDBVersion_;
	private final File dfendPath_;
	private final DFendReloadedConfiguration profiles_;

	public DFendReloadedImportThread(Text log, ProgressBar progressBar, Label status, File dfendPath, File confsPath, boolean performCleanup, DosboxVersion defaultDBVersion) throws IOException {
		super(log, progressBar, status, false);

		performCleanup_ = performCleanup;
		defaultDBVersion_ = defaultDBVersion;

		File settingsFile = new File(new File(dfendPath, "Settings"), "DFend.ini");
		profiles_ = new DFendReloadedConfiguration(settingsFile);
		messageLog_.append(profiles_.loadDat(new File(dfendPath, "Confs"), confsPath));
		setObjects(profiles_.getConfFiles());

		String defLoc = profiles_.getValue("ProgramSets", "defloc");
		dfendPath_ = StringUtils.isNotBlank(defLoc) ? new File(defLoc): dfendPath;
	}

	@Override
	public String work(File profFile) throws IOException, SQLException {
		StringBuilder warnings = new StringBuilder();

		warnings.append(profiles_.loadProfile(profFile));

		String title = profiles_.getValue(profFile, "ExtraInfo", "name");
		displayTitle(text_.get("dialog.dfendimport.importing", new Object[] {title}));

		String developer = profiles_.getValue(profFile, "ExtraInfo", "developer");
		String publisher = profiles_.getValue(profFile, "ExtraInfo", "publisher");
		String genre = profiles_.getValue(profFile, "ExtraInfo", "genre");
		String year = profiles_.getValue(profFile, "ExtraInfo", "year");
		String status = text_.get("dialog.dfendimport.defaultprofilestatus");
		String notes = fixCrLf(profiles_.getValue(profFile, "ExtraInfo", "notes"));
		boolean favorite = profiles_.getValue(profFile, "ExtraInfo", "favorite").equals("1");
		String[] links = {fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www")), fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www2")),
				fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www3")), fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www4")), fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www5")),
				fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www6")), fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www7")), fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www8"))};
		String[] linkTitles = {profiles_.getValue(profFile, "ExtraInfo", "wwwname"), profiles_.getValue(profFile, "ExtraInfo", "www2name"), profiles_.getValue(profFile, "ExtraInfo", "www3name"),
				profiles_.getValue(profFile, "ExtraInfo", "www4name"), profiles_.getValue(profFile, "ExtraInfo", "www5name"), profiles_.getValue(profFile, "ExtraInfo", "www6name"),
				profiles_.getValue(profFile, "ExtraInfo", "www7name"), profiles_.getValue(profFile, "ExtraInfo", "www8name")};
		String language = profiles_.getValue(profFile, "ExtraInfo", "language");
		String userInfo = profiles_.getValue(profFile, "ExtraInfo", "userinfo");
		if (StringUtils.isNotEmpty(userInfo))
			userInfo = StringUtils.join(StringUtils.split(fixCrLf(userInfo), StringUtils.LF), ", ");
		String[] customStrings = new String[] {language, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, userInfo, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY,
				StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY};
		String setup = profiles_.getValue(profFile, "Extra", "setup");
		if (StringUtils.isNotEmpty(setup))
			setup = FilesUtils.concat(dfendPath_, setup);
		String setupParams = profiles_.getValue(profFile, "Extra", "setupparameters");

		Profile newProfile = ProfileFactory.create(title, developer, publisher, genre, year, status, notes, favorite, links, linkTitles, customStrings, setup, setupParams);

		warnings.append(newProfile.loadConfigurationData(text_, profiles_.getConf(profFile), profiles_.getConfFile(profFile)));

		Generation srcGeneration = newProfile.determineGenByConf();
		if (srcGeneration == null)
			srcGeneration = Generation.official_073; // most likely

		if (performCleanup_) {
			newProfile.getConfiguration().removeSection("joystick");
			newProfile.getConfiguration().removeSection("sdl");
		}

		// The new profile is currently not associated with any DOSBox version
		// We need to update the profile configuration so it will match the default DOSBox version
		// because the imported configuration might be written using an older DOSBox generation
		newProfile.alterToDosboxVersionGeneration(srcGeneration, defaultDBVersion_);

		newProfile = new ProfileRepository().add(newProfile);

		String captures = profiles_.getValue(profFile, "dosbox", "captures");
		File srcCaptures = StringUtils.isBlank(captures) ? null: new File(dfendPath_, captures);

		if (FilesUtils.isExistingDirectory(srcCaptures))
			FileUtils.copyDirectory(srcCaptures, newProfile.getCanonicalCaptures(), FileFileFilter.INSTANCE);

		if (newProfile.isIncomplete())
			warnings.append(text_.get("general.error.profileincomplete", new String[] {newProfile.getConfigurationCanonicalFile().getPath()}));

		return warnings.toString();
	}

	private static String fixCrLf(String s) {
		return Strings.CS.replace(Strings.CS.replace(s, "[13][10]", StringUtils.LF), "[13]", "").trim();
	}

	private static String fixUrl(String s) {
		return (StringUtils.isNotEmpty(s) && !s.toLowerCase().startsWith("http://") && !s.toLowerCase().startsWith("https://")) ? "http://" + s: s;
	}

	@Override
	public String getTitle(File file) {
		return file.getName();
	}

}