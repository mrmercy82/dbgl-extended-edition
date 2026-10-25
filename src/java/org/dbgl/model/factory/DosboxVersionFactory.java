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
import java.util.Map;

import org.dbgl.model.GenericStats;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.conf.Configuration;


public class DosboxVersionFactory {

	private DosboxVersionFactory() {
	}

	/**
	 * Used when creating a new DosboxVersion
	 *
	 * @param title
	 * @param family
	 * @param version
	 * @param isDefault
	 * @param multiConfig
	 * @param usingCurses
	 * @param dynamicOptions
	 * @param path
	 * @param exe
	 * @param params
	 * @param conf
	 * @return
	 */
	public static DosboxVersion create(String title, String family, String version, boolean isDefault, boolean multiConfig, boolean usingCurses, Map<String, String> dynamicOptions, String path, String exe,
			String params, String conf) {
		return create(title, family, version, isDefault, multiConfig, usingCurses, dynamicOptions, path, exe, params, conf, null, null, null, 0);
	}

	/**
	 * Used when instantiating a DosboxVersion from data in the database
	 *
	 * @param id
	 * @param title
	 * @param family
	 * @param version
	 * @param isDefault
	 * @param multiConfig
	 * @param usingCurses
	 * @param path
	 * @param exe
	 * @param params
	 * @param conf
	 * @param created
	 * @param modified
	 * @param lastrun
	 * @param runs
	 * @return
	 */
	public static DosboxVersion create(int id, String title, String family, String version, boolean isDefault, boolean multiConfig, boolean usingCurses, Map<String, String> dynamicOptions, String path, String exe,
			String params, String conf, Timestamp created, Timestamp modified, Timestamp lastrun, int runs) {
		DosboxVersion dbv = create(title, family, version, isDefault, multiConfig, usingCurses, dynamicOptions, path, exe, params, conf, created, modified, lastrun, runs);
		dbv.setId(id);
		return dbv;
	}

	private static DosboxVersion create(String title, String family, String version, boolean isDefault, boolean multiConfig, boolean usingCurses, Map<String, String> dynamicOptions, String path, String exe,
			String params, String conf, Timestamp created, Timestamp modified, Timestamp lastrun, int runs) {
		DosboxVersion dbv = new DosboxVersion();
		dbv.setTitle(title);
		dbv.setFamily(family);
		dbv.setVersion(version);
		dbv.setDefault(isDefault);
		dbv.setMultiConfig(multiConfig);
		dbv.setUsingCurses(usingCurses);
		dbv.setDynamicOptions(dynamicOptions);
		dbv.setPath(path);
		dbv.setExe(exe);
		dbv.setExecutableParameters(params);
		dbv.setConfiguration(new Configuration());
		dbv.setConfigurationFileLocation(conf);
		dbv.setStats(new GenericStats(created, modified, lastrun, runs));
		return dbv;
	}
}
