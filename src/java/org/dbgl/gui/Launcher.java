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
package org.dbgl.gui;

import java.io.File;
import java.sql.SQLException;
import org.dbgl.constants.Constants;
import org.dbgl.gui.dialog.MainWindow;
import org.dbgl.service.DatabaseService;
import org.dbgl.service.MetropolisDatabaseService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.hsqldb.persist.HsqlDatabaseProperties;


public class Launcher {

	private static final String LIB_PATH = "lib";

	public static void main(String[] args) {
		File homeDir = new File(SystemUtils.USER_HOME).getAbsoluteFile();
		if (SystemUtils.SWT_LIB_PATH == null && homeDir.isDirectory() && !FilesUtils.isWritableDirectory(homeDir)) {
			File libDir = new File(LIB_PATH).getAbsoluteFile();
			if (libDir.isDirectory() && FilesUtils.isWritableDirectory(libDir)) {
				System.out.print(", user.home '" + homeDir + "' appears unwritable - switched swt.library.path to '" + LIB_PATH + "'");
				System.setProperty("swt.library.path", LIB_PATH);
			}
		}

		System.out.println("Launching DBGL using " + SystemUtils.JVM_ARCH + "-Bit VM " + SystemUtils.JVM_VERSION + " on " + SystemUtils.OS_NAME + " v" + SystemUtils.OS_VERSION + SystemUtils.OS_ARCH
				+ ", " + HsqlDatabaseProperties.PRODUCT_NAME + " " + HsqlDatabaseProperties.THIS_FULL_VERSION + ", SWT v" + SWT.getVersion() + SWT.getPlatform());

		if (SystemUtils.IS_OSX) {
			Display.setAppName(Constants.PROGRAM_NAME_SHORT);
			Display.setAppVersion(Constants.PROGRAM_VERSION);
		}

		while (Boolean.TRUE.equals(new MainWindow().open()));
		try {
			DatabaseService.getInstance().shutdown();
			MetropolisDatabaseService.getInstance().shutdown(false);
		} catch (SQLException e) {
			// nothing we can do
		}
		System.exit(0);
	}
}
