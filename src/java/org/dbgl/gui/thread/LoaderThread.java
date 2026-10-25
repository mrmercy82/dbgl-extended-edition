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
import java.util.ArrayList;
import java.util.List;
import org.dbgl.model.aggregate.Profile;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;


public class LoaderThread extends UIThread<Profile> {

	private List<Profile> result_ = new ArrayList<>();

	public LoaderThread(Text log, ProgressBar progressBar, Label status, List<Profile> profs) {
		super(log, progressBar, status, false);
		setObjects(profs);
	}

	@Override
	public String work(Profile profile) throws IOException {
		displayTitle(text_.get("dialog.profileloader.reading", new Object[] {getTitle(profile)}));

		String warnings = profile.resetAndLoadConfiguration();

		if (profile.isIncomplete())
			throw new IOException(text_.get("dialog.multiprofile.error.profileincomplete"));

		result_.add(profile);

		return warnings;
	}

	@Override
	public String getTitle(Profile profile) {
		return profile.getTitle();
	}

	public List<Profile> getResult() {
		return result_;
	}
}