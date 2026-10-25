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
package org.dbgl.model.entity;

import java.io.File;

import org.dbgl.model.conf.Configuration;
import org.dbgl.util.FilesUtils;


public abstract class Configurable extends TitledEntity {

	protected Configuration configuration_;

	protected Configurable() {
		super();
	}

	public Configuration getConfiguration() {
		return configuration_;
	}

	public void setConfiguration(Configuration configuration) {
		configuration_ = configuration;
	}

	public File getConfigurationFile() {
		return configuration_.getFile();
	}

	public File getConfigurationCanonicalFile() {
		return configuration_.getCanonicalFile();
	}

	public String getConfigurationFileUrl() {
		return FilesUtils.toUrl(getConfigurationCanonicalFile());
	}

	public void addMount(String mount) {
		configuration_.getAutoexec().addMount(mount);
	}
}
