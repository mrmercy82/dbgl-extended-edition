/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-07. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.SearchEngineImageInformation;
import org.dbgl.model.SearchEngineImageInformation.SearchEngineImageType;
import org.dbgl.model.WebProfile;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.ImageService;
import org.dbgl.service.SettingsService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.searchengine.WebSearchEngine;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;


public class SearchEngineThread extends UIThread<Profile> {

	private final String logDelimiter_;
	private final WebSearchEngine engine_;

	public SearchEngineThread(List<Profile> profs, WebSearchEngine engine, Text log, ProgressBar progressBar, Label status) {
		super(log, progressBar, status, false);
		setObjects(profs);
		logDelimiter_ = log.getLineDelimiter();
		engine_ = engine;
	}

	@Override
	public String work(Profile prof) throws SQLException {
		displayTitle(text_.get("dialog.multiprofile.updating", new Object[] {getTitle(prof)}));

		StringBuilder messageLog = new StringBuilder();

		String title = getTitle(prof);

		if (engine_ != null) {
			try {
				SettingsService settings = SettingsService.getInstance();

				List<WebProfile> webGamesList = engine_.getEntries(title, settings.getValues(engine_.getSimpleName(), "platform_filter"));

				if (webGamesList.size() > 1) {
					int firstMatch = WebSearchEngine.getEntryFirstExactMatchIndex(title, webGamesList);
					if (firstMatch != -1)
						webGamesList = webGamesList.subList(firstMatch, firstMatch + 1);
				}

				if (webGamesList.size() == 1) {
					WebProfile thisGame = engine_.getEntryDetailedInformation(webGamesList.get(0));

					if (settings.getBooleanValue(engine_.getSimpleName(), "set_title"))
						prof.setTitle(thisGame.getTitle());
					if (settings.getBooleanValue(engine_.getSimpleName(), "set_developer"))
						prof.setDeveloper(thisGame.getDeveloperName());
					if (settings.getBooleanValue(engine_.getSimpleName(), "set_publisher"))
						prof.setPublisher(thisGame.getPublisherName());
					if (settings.getBooleanValue(engine_.getSimpleName(), "set_year"))
						prof.setYear(thisGame.getYear());
					if (settings.getBooleanValue(engine_.getSimpleName(), "set_genre"))
						prof.setGenre(thisGame.getGenre());
					if (settings.getBooleanValue(engine_.getSimpleName(), "set_link")) {
						prof.setLinkDestination(0, thisGame.getUrl());
						prof.setLinkTitle(0, text_.get("dialog.profile.searchengine.link.maininfo", new String[] {engine_.getName()}));
					}
					if (settings.getBooleanValue(engine_.getSimpleName(), "set_description")) {
						String engineNotes = thisGame.getNotes().replace(StringUtils.LF, logDelimiter_);
						if (!prof.getNotes().endsWith(engineNotes)) {
							StringBuilder notes = new StringBuilder(prof.getNotes());
							if (notes.length() > 0)
								notes.append(logDelimiter_ + logDelimiter_);
							notes.append(engineNotes);
							prof.setNotes(notes.toString());
						}
					}
					if (settings.getBooleanValue(engine_.getSimpleName(), "set_rank"))
						prof.setCustomInt(0, String.valueOf(thisGame.getRank()));

					new ProfileRepository().update(prof);

					boolean forceAllRegionsCoverArt = settings.getBooleanValue(engine_.getSimpleName(), "force_all_regions_coverart");
					SearchEngineImageInformation[] imageInformation = engine_.getEntryImages(thisGame, settings.getIntValue(engine_.getSimpleName(), "multi_max_coverart"),
						settings.getIntValue(engine_.getSimpleName(), "multi_max_screenshot"), forceAllRegionsCoverArt);
					for (int i = 0; i < imageInformation.length; i++) {
						if (thisGame.getWebImage(i) != null) {
							String description = FilesUtils.toSafeFilenameForWebImages(imageInformation[i].getDescription());
							File file;
							if (imageInformation[i].getType() == SearchEngineImageType.COVER_ART) {
								String filename = text_.get("dialog.profile.mobygames.coverartfilename", new Object[] {i, description});
								file = new File(prof.getCanonicalCaptures(), filename + ".jpg");
							} else {
								String filename = text_.get("dialog.profile.mobygames.screenshotfilename", new Object[] {i, description});
								file = new File(prof.getCanonicalCaptures(), filename + ".png");
							}
							if (!FilesUtils.isExistingFile(file)) {
								ImageService.save(display_, thisGame.getWebImage(i), file.getPath());
							} else {
								messageLog.append(text_.get("dialog.profile.error.imagealreadyexists", new String[] {file.getPath(), engine_.getName()})).append(StringUtils.LF);
							}
						}
					}

				} else if (webGamesList.isEmpty()) {
					messageLog.append(text_.get("general.notice.searchenginenoresults", new String[] {engine_.getName(), title})).append(StringUtils.LF);
				} else {
					messageLog.append(text_.get("dialog.multiprofile.notice.titlenotunique", new String[] {engine_.getName(), title})).append(StringUtils.LF);
				}
			} catch (IOException | RuntimeException e) {
				String explained = org.dbgl.util.searchengine.SearchEngineException.explain(engine_.getName(), e).getMessage();
				messageLog.append(text_.get("searchengine.error.retrieve", new Object[] {engine_.getName(), title, explained})).append(StringUtils.LF);
			}
		}

		return messageLog.toString();
	}

	@Override
	public String getTitle(Profile profile) {
		return profile.getTitle();
	}

}
