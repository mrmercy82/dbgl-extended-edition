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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.constants.Constants;
import org.dbgl.model.GamePack;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.entity.GamePackEntry;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.SettingsService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.StringRelatedUtils;
import org.dbgl.util.archive.ZipUtils;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;


public class ImportThread extends UIThread<GamePackEntry> {

	private GamePack gamePack_;
	private boolean useExistingConf_;
	private boolean importFullSettings_;
	private boolean customValues_;
	private boolean customFields_;

	public ImportThread(Text log, ProgressBar progressBar, Label status, GamePack packageData, boolean useExistingConf, boolean fullSettings, boolean customValues, boolean customFields)
			throws IOException {
		super(log, progressBar, status, true);

		gamePack_ = packageData;
		useExistingConf_ = useExistingConf;
		importFullSettings_ = fullSettings;
		customValues_ = customValues;
		customFields_ = customFields;

		File[] zipfiles = gamePack_.getEntries().stream().map(x -> x.getGamePack().getArchive()).distinct().toArray(File[]::new);
		List<File> archiveGameDirs = gamePack_.getEntries().parallelStream().map(GamePackEntry::getArchiveGameDir).toList();
		List<File> archiveCaptureDirs = gamePack_.getEntries().parallelStream().map(GamePackEntry::getArchiveCapturesDir).toList();
		List<File> archiveMapperFiles = gamePack_.getEntries().parallelStream().filter(GamePackEntry::hasMapper).map(GamePackEntry::getArchiveMapper).toList();

		long bytes = 0;

		for (File zipfile: zipfiles) {
			List<ZipEntry> allZipEntries = ZipUtils.readEntriesInZip(zipfile);

			Map<File, List<ZipEntry>> gameDirMap = allZipEntries.parallelStream().collect(
				Collectors.groupingBy(x -> archiveGameDirs.stream().filter(y -> FilesUtils.areRelated(y, new File(x.getName()))).findAny().orElse(new File("."))));
			Map<File, List<ZipEntry>> captureDirMap = allZipEntries.parallelStream().collect(
				Collectors.groupingBy(x -> archiveCaptureDirs.stream().filter(y -> FilesUtils.areRelated(y, new File(x.getName()))).findAny().orElse(new File("."))));
			Map<File, List<ZipEntry>> mapperFileMap = allZipEntries.parallelStream().collect(
				Collectors.groupingBy(x -> archiveMapperFiles.stream().filter(y -> y.equals(new File(x.getName()))).findAny().orElse(new File("."))));

			if (gamePack_.isGamedataAvailable()) {
				for (GamePackEntry entry: gamePack_.getEntries()) {
					if (entry.getGamePack().getArchive().equals(zipfile)) {
						List<ZipEntry> archiveEntries = gameDirMap.get(entry.getArchiveGameDir());
						if (archiveEntries == null) {
							entry.setGameArchiveEntries(new ArrayList<>());
						} else {
							bytes += archiveEntries.stream().mapToLong(ZipEntry::getSize).sum();
							entry.setGameArchiveEntries(archiveEntries);
						}
					}
				}
			}

			if (gamePack_.isCapturesAvailable()) {
				for (GamePackEntry entry: gamePack_.getEntries()) {
					if (entry.getGamePack().getArchive().equals(zipfile)) {
						List<ZipEntry> archiveEntries = captureDirMap.get(entry.getArchiveCapturesDir());
						if (archiveEntries == null) {
							entry.setCaptureArchiveEntries(new ArrayList<>());
						} else {
							bytes += archiveEntries.stream().mapToLong(ZipEntry::getSize).sum();
							entry.setCaptureArchiveEntries(archiveEntries);
						}
					}
				}
			}

			if (gamePack_.isMapperfilesAvailable()) {
				for (GamePackEntry entry: gamePack_.getEntries()) {
					if (entry.hasMapper() && entry.getGamePack().getArchive().equals(zipfile)) {
						List<ZipEntry> archiveEntries = mapperFileMap.get(entry.getArchiveMapper());
						if (archiveEntries == null) {
							entry.setMapperArchiveEntries(new ArrayList<>());
						} else {
							bytes += archiveEntries.stream().mapToLong(ZipEntry::getSize).sum();
							entry.setMapperArchiveEntries(archiveEntries);
						}
					}
				}
			}
		}

		setObjects(gamePack_.getEntries());
		setTotal(bytes);
	}

	@Override
	public String work(GamePackEntry entry) throws IOException, SQLException {
		displayTitle(text_.get("dialog.import.importing", new Object[] {getTitle(entry)}));

		Profile prof = entry.getProfile();

		if (!customValues_)
			prof.resetAllCustomValues();

		if (!entry.getGamePack().isNativecommandsAvailable())
			prof.resetNativeCommands();

		prof.loadConfigurationData(text_, importFullSettings_ ? entry.getFullConfig(): entry.getIncrConfig(), entry.getGamePack().getArchive());

		if (entry.getGamePack().isMapperfilesAvailable() && entry.hasMapper())
			prof.setValue("sdl", "mapperfile", entry.getNewMapper().getName());

		// fix paths for profile autoexec, links, setup, altexe
		prof.setBaseDir(entry.getBaseDir());

		prof = new ProfileRepository().add(prof, useExistingConf_);

		if (entry.getGamePack().isCapturesAvailable()) {
			try {
				ZipUtils.extractDirInZip(entry.getCaptureArchiveEntries(), entry.getGamePack().getArchive(), entry.getArchiveCapturesDir(), prof.getCanonicalCaptures(), true, this);
				messageLog_.append(PREFIX_OK).append(text_.get("dialog.import.notice.extractedcaptures", new Object[] {prof.getCanonicalCaptures()})).append(StringUtils.LF);
			} catch (IOException e) {
				messageLog_.append(PREFIX_ERR).append(text_.get("dialog.import.error.capturesextraction", new Object[] {StringRelatedUtils.toString(e)})).append(StringUtils.LF);
			}
		}

		if (entry.getGamePack().isGamedataAvailable()) {
			File canonicalFullDir = entry.getCanonicalFullDir();
			if (!canonicalFullDir.exists()) {
				FilesUtils.createDir(canonicalFullDir);
				messageLog_.append(PREFIX_OK).append(text_.get("dialog.import.notice.createddir", new Object[] {canonicalFullDir})).append(StringUtils.LF);
			}
			try {
				ZipUtils.extractDirInZip(entry.getGameArchiveEntries(), entry.getGamePack().getArchive(), entry.getArchiveGameDir(), canonicalFullDir, false, this);
				messageLog_.append(PREFIX_OK).append(text_.get("dialog.import.notice.extractedgamedata", new Object[] {canonicalFullDir})).append(StringUtils.LF);
			} catch (IOException e) {
				throw new IOException(text_.get("dialog.import.error.gamedataextraction", new Object[] {StringRelatedUtils.toString(e)}), e);
			}
		}

		if (entry.getGamePack().isMapperfilesAvailable() && entry.hasMapper()) {
			File dstFile = entry.getNewMapper();
			try {
				ZipUtils.extractDirInZip(entry.getMapperArchiveEntries(), entry.getGamePack().getArchive(), entry.getArchiveMapper(), dstFile, false, this);
				messageLog_.append(PREFIX_OK).append(text_.get("dialog.import.notice.extractedmapperfile", new Object[] {dstFile})).append(StringUtils.LF);
			} catch (IOException e) {
				messageLog_.append(PREFIX_ERR).append(text_.get("dialog.import.error.mapperfileextraction", new Object[] {StringRelatedUtils.toString(e)})).append(StringUtils.LF);
			}
		}

		messageLog_.append(PREFIX_OK).append(
			text_.get("dialog.import.notice.createddbentry", new Object[] {prof.getId(), prof.getConfigurationFile(), prof.getCapturesString(), prof.getDosboxVersion().getTitle()})).append(
				StringUtils.LF);

		return null;
	}

	@Override
	public String getTitle(GamePackEntry entry) {
		return entry.getProfile().getTitle();
	}

	@Override
	public void preFinish() throws IOException {
		if (customFields_) {
			for (int i = 0; i < Constants.EDIT_COLUMN_NAMES; i++) {
				String customField = gamePack_.getCustomFieldTitles()[i];
				if (StringUtils.isNotBlank(customField) && !customField.equalsIgnoreCase("Custom" + (i + 1)))
					SettingsService.getInstance().setValue("gui", "custom" + (i + 1), customField);
			}
		}
	}
}