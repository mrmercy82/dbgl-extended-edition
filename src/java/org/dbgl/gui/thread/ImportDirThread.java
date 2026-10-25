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
import java.util.List;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.constants.Constants;
import org.dbgl.model.Link;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.entity.SharedConf;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.repository.GameFilesRepository.GameDirEntry;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.SettingsService;
import org.dbgl.util.searchengine.MetropolisSearchEngine;
import org.dbgl.util.searchengine.WebSearchEngine;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;


public class ImportDirThread extends UIThread<GameDirEntry> {

	private static final SettingsService settings_ = SettingsService.getInstance();
	private static final WebSearchEngine engine_ = MetropolisSearchEngine.getInstance();

	private final List<GameDirEntry> games_;
	private final Profile profile_;
	private final boolean consultConf_;

	public ImportDirThread(Text log, ProgressBar progressBar, Label status, List<GameDirEntry> games, Profile profile, boolean consultConf) {
		super(log, progressBar, status, true);

		games_ = games;
		profile_ = profile;
		consultConf_ = consultConf;

		setObjects(games_);
		setTotal(games_.size());
	}

	@Override
	public String work(GameDirEntry entry) throws IOException, SQLException {
		displayTitle(text_.get("dialog.import.importing", new Object[] {getTitle(entry)}));

		Profile prof = ProfileFactory.createCopy(profile_);
		prof.loadConfigurationData(text_, profile_.getConfigurationString(), new File(""));

		prof.setTitle(entry.webProfile_.getTitle());
		if (settings_.getBooleanValue(engine_.getSimpleName(), "set_developer"))
			prof.setDeveloper(entry.webProfile_.getDeveloperName());
		if (settings_.getBooleanValue(engine_.getSimpleName(), "set_publisher"))
			prof.setPublisher(entry.webProfile_.getPublisherName());
		if (settings_.getBooleanValue(engine_.getSimpleName(), "set_year"))
			prof.setYear(entry.webProfile_.getYear());
		if (settings_.getBooleanValue(engine_.getSimpleName(), "set_genre"))
			prof.setGenre(entry.webProfile_.getGenre());
		if (settings_.getBooleanValue(engine_.getSimpleName(), "set_link")) {
			Link[] links = new Link[Profile.NR_OF_LINK_TITLES];
			for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
				links[i] = new Link(StringUtils.EMPTY, StringUtils.EMPTY);
			}
			links[0] = new Link(text_.get("dialog.profile.searchengine.link.maininfo", new String[] {engine_.getName()}), entry.webProfile_.getUrl());
			prof.setLinks(links);
		}
		if (settings_.getBooleanValue(engine_.getSimpleName(), "set_description"))
			prof.setNotes(entry.webProfile_.getNotes());
		if (settings_.getBooleanValue(engine_.getSimpleName(), "set_rank"))
			prof.setCustomInts(new int[] {entry.webProfile_.getRank(), 0});
		prof.setSetupFileLocation(entry.getOptSetup().isPresent() ? entry.getSetup().getPath(): StringUtils.EMPTY);

		prof.addRequiredMount(false, entry.getMain().getPath(), false);
		prof.setAutoexecSettings(entry.getMain().getPath(), StringUtils.EMPTY);

		if (consultConf_) {
			Client client = ClientBuilder.newClient();
			GenericType<List<SharedConf>> confType = new GenericType<List<SharedConf>>() {
			};
			List<SharedConf> confs = client.target(SettingsService.getInstance().getValue("confsharing", "endpoint")).path("/configurations/bytitle/{i}").resolveTemplate("i",
				prof.getTitle()).request().accept(MediaType.APPLICATION_XML).get(confType);
			client.close();

			if (confs.isEmpty()) {
				messageLog_.append(PREFIX_ERR).append(text_.get("general.notice.searchenginenoresults", new String[] {Constants.DBCONFWS, prof.getTitle()})).append(StringUtils.LF);
			} else if (confs.size() == 1) {
				prof.getConfiguration().clearSections();
				prof.loadConfigurationData(text_, confs.get(0).getIncrConf(), new File(confs.get(0).getGameTitle()));
				messageLog_.append(PREFIX_OK).append(text_.get("dialog.importdir.notice.conffound", prof.getTitle())).append(StringUtils.LF);
			} else {
				messageLog_.append(PREFIX_ERR).append(text_.get("dialog.multiprofile.notice.titlenotunique", new String[] {Constants.DBCONFWS, prof.getTitle()})).append(StringUtils.LF);
			}
		}

		ProfileRepository pRepo = new ProfileRepository();
		prof = pRepo.add(prof);

		messageLog_.append(PREFIX_OK).append(
			text_.get("dialog.import.notice.createddbentry", new Object[] {prof.getId(), prof.getConfigurationFile(), prof.getCapturesString(), prof.getDosboxVersion().getTitle()})).append(
				StringUtils.LF);

		return null;
	}

	@Override
	public String getTitle(GameDirEntry obj) {
		return obj.webProfile_.getTitle() + " (" + obj.dir_.getPath() + ")";
	}

}