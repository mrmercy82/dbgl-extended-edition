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

import java.io.IOException;
import java.sql.SQLException;
import org.dbgl.model.FileLocation;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.FileLocationService;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;


public class MigrateThread extends UIThread<Profile> {

	private final FileLocation from_;

	public MigrateThread(Text log, ProgressBar progressBar, Label status, FileLocation from) throws SQLException {
		super(log, progressBar, status, false);
		from_ = from;
		setObjects(new ProfileRepository().list(" ORDER BY LOWER(GAM.TITLE)", null, new DosboxVersionRepository().listAll()));
	}

	@Override
	public String work(Profile profile) throws IOException, SQLException {
		displayTitle(text_.get("dialog.migration.migrating", new Object[] {getTitle(profile)}));

		String warnings = profile.resetAndLoadConfiguration();

		profile.migrate(from_, FileLocationService.getInstance().getDosrootLocation());

		new ProfileRepository().update(profile);

		return warnings;
	}

	@Override
	public String getTitle(Profile profile) {
		return profile.getTitle();
	}

}
