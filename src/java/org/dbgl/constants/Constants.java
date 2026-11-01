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
package org.dbgl.constants;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;


public class Constants {

	public static final String PROGRAM_NAME_SHORT = "DBGL";

	public static final String PROGRAM_NAME_FULL = "DOSBox Game Launcher";

	/** this edition of DBGL; based on DBGL 0.100 beta 17 by Ronald Blankendaal, see the about dialog */
	public static final String EDITION_NAME = "Extended Edition";

	public static final String PROGRAM_VERSION;

	static {
		Properties prop = new Properties();
		try (InputStream is = Constants.class.getClassLoader().getResourceAsStream("version.properties")) {
			prop.load(is);
		} catch (IOException ex) {
			// ignore
		} finally {
			PROGRAM_VERSION = prop.getProperty("majorversion", "?") + '.' + prop.getProperty("minorversion", "?");
		}
	}

	public static final int RO_COLUMN_NAMES = 10;
	public static final int EDIT_COLUMN_NAMES_1 = 10;
	public static final int EDIT_COLUMN_NAMES_2 = 4;
	public static final int EDIT_COLUMN_NAMES = EDIT_COLUMN_NAMES_1 + EDIT_COLUMN_NAMES_2;
	public static final int STATS_COLUMN_NAMES = 8;

	public static final List<String> SUPPORTED_LANGUAGES = Arrays.asList("ar", "da", "de", "el", "en", "es", "es__Capitalizado", "fi", "fr", "it", "ko", "nl", "pl", "pt_BR", "ru", "sk", "sl", "sv",
		"zh", "zh_TW");

	public static final String DBCONFWS = "DBConfWS";

	private Constants() {
	}
}
