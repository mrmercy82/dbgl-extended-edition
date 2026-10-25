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
package org.dbgl.model.factory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.GenericStats;
import org.dbgl.model.NativeCommand;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.repository.BaseRepository;


public class TemplateFactory {

	private TemplateFactory() {
	}

	/**
	 * Used when creating a new Template
	 *
	 * @param dosboxVersion
	 * @return Template
	 */
	public static Template create(DosboxVersion dosboxVersion) {
		Template template = new Template();
		template.setTitle(StringUtils.EMPTY);
		template.setDefault(false);
		List<NativeCommand> nativeCommands = new ArrayList<>();
		NativeCommand.insertDosboxCommand(nativeCommands);
		template.setNativeCommands(nativeCommands);
		template.setDosboxVersion(dosboxVersion);
		Configuration config = new Configuration();
		config.getAutoexec().setExit(true);
		template.setConfiguration(config);
		template.setStats(new GenericStats());
		return template;
	}

	/**
	 * Used when importing Templates from default.xml
	 *
	 * @param title
	 * @param dosboxVersion
	 * @return Template
	 */
	public static Template create(String title, DosboxVersion dosboxVersion) {
		Template template = new Template();
		template.setTitle(title);
		template.setDefault(false);
		List<NativeCommand> nativeCommands = new ArrayList<>();
		NativeCommand.insertDosboxCommand(nativeCommands);
		template.setNativeCommands(nativeCommands);
		template.setDosboxVersion(dosboxVersion);
		template.setConfiguration(new Configuration());
		template.setStats(new GenericStats());
		return template;
	}

	/**
	 * Used when instantiating a Template from data in the database
	 *
	 * @param id
	 * @param title
	 * @param isDefault
	 * @param nativeCommands
	 * @param dosboxVersionId
	 * @param created
	 * @param modified
	 * @param lastrun
	 * @param runs
	 * @param dosboxVersions
	 * @return Template
	 */
	public static Template create(int id, String title, boolean isDefault, List<NativeCommand> nativeCommands, int dosboxVersionId, Timestamp created, Timestamp modified, Timestamp lastrun, int runs,
			List<DosboxVersion> dosboxVersions) {
		Template template = new Template();
		template.setId(id);
		template.setTitle(title);
		template.setDefault(isDefault);
		template.setNativeCommands(nativeCommands);
		template.setDosboxVersion(BaseRepository.findById(dosboxVersions, dosboxVersionId));
		template.setConfiguration(new Configuration());
		template.setConfigurationFileLocationByIdentifiers();
		template.setStats(new GenericStats(created, modified, lastrun, runs));
		return template;
	}

	/**
	 * Used when duplicating a Template
	 *
	 * @param templ
	 * @return Template
	 */
	public static Template createCopy(Template templ) {
		Template template = new Template();
		template.setTitle(templ.getTitle());
		template.setDefault(false);
		template.setNativeCommands(templ.getNativeCommands());
		template.setDosboxVersion(templ.getDosboxVersion());
		template.setConfiguration(new Configuration());
		template.setStats(new GenericStats());
		return template;
	}
}
