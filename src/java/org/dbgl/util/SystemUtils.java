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
package org.dbgl.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.apache.commons.lang3.Strings;
import org.dbgl.gui.controls.Mess_;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Shell;


public class SystemUtils {

	public static final String EOLN = System.getProperty("line.separator");

	public static final String OS_NAME = System.getProperty("os.name");
	public static final String OS_ARCH = System.getProperty("os.arch");
	public static final String OS_VERSION = System.getProperty("os.version");

	public static final String JVM_ARCH = System.getProperty("sun.arch.data.model");
	public static final String JVM_VERSION = System.getProperty("java.version");

	public static final boolean IS_WINDOWS = OS_NAME.startsWith("Windows");
	public static final boolean IS_LINUX = OS_NAME.startsWith("Linux");
	public static final boolean IS_OSX = OS_NAME.startsWith("Mac OS");

	public static final String USER_HOME = System.getProperty("user.home");
	public static final String SWT_LIB_PATH = System.getProperty("swt.library.path");

	public static final boolean USE_DBGL_DATA_LOCAL_APP_DATA = Boolean.parseBoolean(System.getProperty("dbgl.data.localappdata"));
	public static final boolean USE_DBGL_DATA_USER_HOME = Boolean.parseBoolean(System.getProperty("dbgl.data.userhome"));
	public static final boolean USE_USER_HOME_DIR = USE_DBGL_DATA_LOCAL_APP_DATA || USE_DBGL_DATA_USER_HOME;

	public static final File USER_DATA_DIR = IS_WINDOWS ? new File(System.getenv("LOCALAPPDATA"), "/DBGL")
			: IS_LINUX ? new File(USER_HOME, "/.local/share/dbgl"): new File(USER_HOME, "/Library/dbgl");

	private SystemUtils() {
	}

	public static void openForEditing(File file, Shell shell) {
		if (!Program.launch(file.getPath()) && (!IS_LINUX || !tryToRun(new String[] {"xdg-open", "gnome-open"}, file.getPath())))
			Mess_.on(shell).key("general.error.openfile", file.toString()).warning();
	}

	public static void openForBrowsing(String target, Shell shell) {
		try {
			if (target.contains("://"))
				Desktop.getDesktop().browse(URI.create(target));
			else
				Desktop.getDesktop().open(new File(target));
		} catch (Exception e) {
			if (!IS_LINUX || !tryToRun(new String[] {"xdg-open", "gnome-open"}, target))
				Mess_.on(shell).key("general.error.openurl", target).warning();
		}
	}

	public static void openDirForViewing(File file, Shell shell) {
		file = new File(Strings.CS.appendIfMissing(file.getPath(), File.separator + "."));
		try {
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(file);
				return;
			}
		} catch (IOException | IllegalArgumentException | UnsupportedOperationException | SecurityException e) {
			// fall through
		}
		if (!Program.launch(file.getPath()) && (!IS_LINUX || !tryToRun(new String[] {"nautilus", "dolphin", "kfmclient"}, file.getPath())))
			Mess_.on(shell).key("general.error.opendir", file.toString()).warning();
	}

	private static boolean tryToRun(String[] executables, String param) {
		for (String exe: executables) {
			try {
				Runtime.getRuntime().exec(new String[] {exe, param}, null, null);
				return true;
			} catch (Exception e) {
				// try next in line
			}
		}
		return false;
	}
}
