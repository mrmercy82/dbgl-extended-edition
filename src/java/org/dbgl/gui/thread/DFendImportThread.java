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
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.dfend.DFendConfiguration;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.util.FilesUtils;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;


public class DFendImportThread extends UIThread<String> {

	private final boolean performCleanup_;
	private final DosboxVersion defaultDBVersion_;
	private final DFendConfiguration profiles_;

	public DFendImportThread(Text log, ProgressBar progressBar, Label status, File dfendProfilesFile, boolean performCleanup, DosboxVersion defaultDBVersion) throws IOException {
		super(log, progressBar, status, false);

		performCleanup_ = performCleanup;
		defaultDBVersion_ = defaultDBVersion;

		profiles_ = new DFendConfiguration(dfendProfilesFile);
		messageLog_.append(profiles_.loadDat());

		setObjects(Arrays.asList(profiles_.getSectionNames()));
	}

	@Override
	public String work(String title) throws IOException, SQLException {
		displayTitle(text_.get("dialog.dfendimport.importing", new Object[] {title}));

		StringBuilder warnings = new StringBuilder(profiles_.loadProfile(title));

		String developer = profiles_.getValue(title, "ExtraInfo", "developer");
		String publisher = profiles_.getValue(title, "ExtraInfo", "publisher");
		String genre = profiles_.getValue(title, "ExtraInfo", "genre");
		String year = profiles_.getValue(title, "ExtraInfo", "year");
		String status = text_.get("dialog.dfendimport.defaultprofilestatus");
		String notes = profiles_.getValue(title, "ExtraInfo", "notes");
		boolean favorite = profiles_.getValue(title, "fav").equals("1");
		String setup = profiles_.getValue(title, "Extra", "setup");
		String setupParams = profiles_.getValue(title, "Extra", "setupparameters");

		Profile newProfile = ProfileFactory.create(title, developer, publisher, genre, year, status, notes, favorite, setup, setupParams);

		warnings.append(newProfile.loadConfigurationData(text_, profiles_.getConf(title), profiles_.getConfFile(title)));

		Generation srcGeneration = newProfile.determineGenByConf();
		if (srcGeneration == null)
			srcGeneration = Generation.official_063; // most likely

		if (performCleanup_) {
			newProfile.getConfiguration().removeSection("directserial");
			newProfile.getConfiguration().removeSection("modem");
			newProfile.getConfiguration().removeSection("ipx");
			newProfile.getConfiguration().removeSection("sdl");
		}

		// The new profile is currently not associated with any DOSBox version
		// We need to update the profile configuration so it will match the default DOSBox version
		// because the imported configuration is written using an old DOSBox generation (most likely ~0.63)
		warnings.append(newProfile.alterToDosboxVersionGeneration(srcGeneration, defaultDBVersion_));

		newProfile = new ProfileRepository().add(newProfile);

		String captures = profiles_.getValue(title, "dosbox", "captures");
		File srcCaptures = new File(captures);
		if (!FilesUtils.isExistingDirectory(srcCaptures)) {
			File captureDir = new File(profiles_.getConfCanonicalFile(title).getParentFile().getParent(), "Capture");
			File alternative = new File(captureDir, srcCaptures.getName());
			if (FilesUtils.isExistingDirectory(alternative))
				srcCaptures = alternative;
		}

		if (FilesUtils.isExistingDirectory(srcCaptures))
			FileUtils.copyDirectory(srcCaptures, newProfile.getCanonicalCaptures(), FileFileFilter.INSTANCE);

		if (newProfile.isIncomplete())
			warnings.append(text_.get("general.error.profileincomplete", new String[] {newProfile.getConfigurationCanonicalFile().getPath()}));

		return warnings.toString();
	}

	@Override
	public String getTitle(String title) {
		return title;
	}

}