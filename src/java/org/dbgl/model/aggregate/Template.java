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
package org.dbgl.model.aggregate;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.ICanBeDefault;
import org.dbgl.model.entity.TemplateProfileBase;
import org.dbgl.service.FileLocationService;


public class Template extends TemplateProfileBase implements ICanBeDefault {

	private boolean default_;

	public Template() {
		super();
	}

	@Override
	public boolean isDefault() {
		return default_;
	}

	public String getDefault() {
		return String.valueOf(default_);
	}

	public void setDefault(boolean isDefault) {
		default_ = isDefault;
	}

	public void setDefault(String isDefault) {
		setDefault(Boolean.valueOf(isDefault));
	}

	@Override
	public void setConfigurationFileLocationByIdentifiers() {
		getConfiguration().setFileLocation(FileLocationService.getInstance().getUniqueTemplateConfigFileLocation(getId()));
	}

	@Override
	public void setBooter(boolean booter) {
		getConfiguration().getAutoexec().setImg(0, booter ? "file": StringUtils.EMPTY);
	}
}
