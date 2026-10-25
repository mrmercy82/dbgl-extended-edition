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

import java.io.IOException;
import java.util.ArrayList;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.conf.Settings;
import org.dbgl.service.ITextService;


class DFendProfile {

	private final Settings prof_;
	private final Configuration conf_;

	public DFendProfile(Settings prof, Configuration conf) {
		prof_ = prof;
		conf_ = conf;
	}

	public Settings getProf() {
		return prof_;
	}

	public Configuration getConf() {
		return conf_;
	}

	public String load(ITextService text) throws IOException {
		return conf_.reloadWithAutoexec(text, new ArrayList<>());
	}
}