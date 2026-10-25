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
package org.dbgl.app;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.dbgl.model.SearchResult;
import org.dbgl.model.SearchResult.ResultType;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.DatabaseService;
import org.dbgl.service.FileLocationService;
import org.dbgl.util.FilesUtils;


public class Repair {

	public static final class FileComparator implements Comparator<File> {
		@Override
		public int compare(File file1, File file2) {
			String f1 = FilenameUtils.getBaseName(file1.getName());
			String f2 = FilenameUtils.getBaseName(file2.getName());
			try {
				int i1 = Integer.parseInt(f1);
				int i2 = Integer.parseInt(f2);
				return Integer.compare(i1, i2);
			} catch (NumberFormatException e) {
				return file1.getPath().compareToIgnoreCase(file2.getPath());
			}
		}
	}

	public static void main(String[] args) {
		System.out.println("Repairs broken DBGL installations (v0.3)");
		System.out.println();

		validateData();

		System.out.println();

		try {
			DosboxVersionRepository dbRepo = new DosboxVersionRepository();
			List<DosboxVersion> dbversionsList = dbRepo.listAll();

			if (BaseRepository.findDefault(dbversionsList) == null) {
				SearchResult result = FileLocationService.getInstance().findDosbox();
				if (result.result_ == ResultType.COMPLETE) {
					dbRepo.add(result.dosbox_);
					dbversionsList = dbRepo.listAll();
				}
				if (BaseRepository.findDefault(dbversionsList) == null) {
					System.out.println("DOSBox installation could not be located, exiting.");
					System.exit(1);
				}
			}

			System.out.println("Using DOSBox installation located in: [" + BaseRepository.findDefault(dbversionsList).getPath() + "]");

			List<Profile> profileList = analyzeExistingData(dbversionsList);

			repairExistingData(profileList);
		} catch (SQLException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void validateData() {
		File profilesDir = new File(FileLocationService.PROFILES_DIR_STRING);
		if (!profilesDir.exists()) {
			System.out.println("The current directory does not contain the [" + FileLocationService.PROFILES_DIR_STRING + "] directory, exiting.");
			System.exit(1);
		}
	}

	private static List<Profile> analyzeExistingData(List<DosboxVersion> dbversionsList) {
		System.out.println();
		System.out.println("===========================================");
		System.out.println(" Phase 1 of 2: Analyzing existing DBGL data");
		System.out.println("===========================================");

		List<Profile> profileList = new ArrayList<>();

		File profilesDir = new File(FileLocationService.PROFILES_DIR_STRING);
		File[] profileConfFiles = profilesDir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(FilesUtils.CONF_EXT) && !f.getName().equalsIgnoreCase(FileLocationService.SETUP_CONF));
		Arrays.sort(profileConfFiles, new FileComparator());

		DosboxVersion dbversion = BaseRepository.findDefault(dbversionsList);

		for (File file: profileConfFiles) {
			try {

				String baseName = FilenameUtils.getBaseName(file.getName());

				Profile profile = ProfileFactory.create(file, dbversion);

				String warnings = profile.resetAndLoadConfiguration();
				if (!warnings.isEmpty())
					System.out.println(warnings);

				try {
					profile.setId(Integer.parseInt(baseName));
				} catch (NumberFormatException e) {
					String captures = profile.getConfiguration().getValue("dosbox", "captures");
					if (captures != null) {
						profile.setId(Integer.parseInt(captures));
					} else {
						throw new NumberFormatException("Unable to determine profile ID");
					}
				}

				if (profile.isIncomplete())
					System.out.println("WARNING: " + file.getName() + ": This profile's autoexec section seems incomplete");

				Autoexec autoexec = profile.getConfiguration().getAutoexec();
				if (autoexec.isDos())
					profile.setTitle(baseName + ' ' + autoexec.getMain());
				else if (autoexec.isBooter())
					profile.setTitle(baseName + ' ' + autoexec.getImg(0));
				else
					profile.setTitle(baseName);

				profileList.add(profile);

			} catch (IOException | NumberFormatException e) {
				System.out.println("SKIPPED " + file.getName() + " " + e.toString());
			}
		}

		System.out.println("Analysis done");
		return profileList;
	}

	private static void repairExistingData(List<Profile> profileList) {
		System.out.println();
		System.out.println("===============================");
		System.out.println(" Phase 2 of 2: Repair DBGL data");
		System.out.println("===============================");

		for (Profile prof: profileList) {
			try {
				new ProfileRepository().add(prof, true);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		System.out.println("Finished.");
		try {
			DatabaseService.getInstance().shutdown();
		} catch (SQLException e) {
			// nothing we can do
		}
	}
}
