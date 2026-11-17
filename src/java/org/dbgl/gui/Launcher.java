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

		// alpha 49: older versions of libraries an update replaced (also one unzipped by hand) are deleted
		for (String name: org.dbgl.util.EditionUpdater.removeSupersededLibraries())
			System.out.println("Removed replaced library lib/" + name);

		if (SystemUtils.IS_OSX) {
			Display.setAppName(Constants.PROGRAM_NAME_SHORT);
			Display.setAppVersion(Constants.PROGRAM_VERSION);
		}

		// alpha 48: one profile, e.g. from Steam ("--run <id>"), until its game ends
		int runId = RunProfile.idOf(args);
		if (runId >= 0) {
			RunProfile.run(runId);
			System.exit(0);
		}

		while (Boolean.TRUE.equals(new MainWindow().open()) && !MainWindow.isProcessRestartRequested());
		try {
			DatabaseService.getInstance().shutdown();
			MetropolisDatabaseService.getInstance().shutdown(false);
		} catch (SQLException e) {
			// nothing we can do
		}
		// alpha 48: a new start as a new program, after the database is closed
		if (MainWindow.isProcessRestartRequested()) {
			java.util.List<String> cmd = org.dbgl.util.EditionUpdater.restartCommand();
			if (!cmd.isEmpty()) {
				try {
					org.eclipse.swt.widgets.Shell waiting = restartNotice();
					// alpha 51: what the new DBGL writes (e.g. why it could not start) is kept in logs/restart-output.txt; nobody would
					// read it otherwise, and on Windows a full pipe could even stop the new DBGL
					ProcessBuilder pb = new ProcessBuilder(cmd).directory(new java.io.File(".").getAbsoluteFile()).redirectErrorStream(true);
					try {
						File logs = org.dbgl.util.DosboxLog.logsDir();
						if (logs.isDirectory() || logs.mkdirs())
							pb.redirectOutput(new File(logs, org.dbgl.util.OldFiles.RESTART_OUTPUT));
						else
							pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
					} catch (RuntimeException e) {
						pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
					}
					pb.start();
					waitForNewStart(waiting);
				} catch (java.io.IOException e) {
					System.err.println("DBGL could not start again: " + e);
				}
			}
		}
		System.exit(0);
	}

	/**
	 * Test 15: after the new start (e.g. for another theme) DBGL came up behind the other windows. Windows lets a new program
	 * come to the front only while the program that started it is in front; the main window is closed by then. So a small
	 * notice stays open and in front until the new DBGL takes over (or some seconds have passed).
	 *
	 * @return the notice, null where it is not needed or cannot be shown
	 */
	private static org.eclipse.swt.widgets.Shell restartNotice() {
		if (!org.dbgl.util.SystemUtils.IS_WINDOWS)
			return null;
		try {
			org.eclipse.swt.widgets.Display display = org.eclipse.swt.widgets.Display.getDefault();
			org.eclipse.swt.widgets.Shell shell = new org.eclipse.swt.widgets.Shell(display, org.eclipse.swt.SWT.ON_TOP | org.eclipse.swt.SWT.BORDER);
			org.eclipse.swt.layout.FillLayout layout = new org.eclipse.swt.layout.FillLayout();
			layout.marginWidth = layout.marginHeight = 18;
			shell.setLayout(layout);
			new org.eclipse.swt.widgets.Label(shell, org.eclipse.swt.SWT.CENTER).setText(org.dbgl.service.TextService.getInstance().get("dialog.main.notice.restart"));
			org.dbgl.gui.controls.DarkTheme.window(shell);
			org.dbgl.gui.controls.DarkTheme.apply(shell);
			shell.pack();
			org.eclipse.swt.graphics.Rectangle area = display.getPrimaryMonitor().getClientArea();
			shell.setLocation(area.x + (area.width - shell.getSize().x) / 2, area.y + (area.height - shell.getSize().y) / 2);
			shell.open();
			shell.forceActive();
			return shell;
		} catch (RuntimeException | Error e) {
			return null;
		}
	}

	/** until the notice is no longer in front (the new DBGL has opened its window), 15 seconds at most */
	private static void waitForNewStart(org.eclipse.swt.widgets.Shell waiting) {
		if (waiting == null)
			return;
		try {
			org.eclipse.swt.widgets.Display display = waiting.getDisplay();
			boolean[] done = new boolean[1];
			waiting.addListener(org.eclipse.swt.SWT.Deactivate, e -> done[0] = true);
			long end = System.currentTimeMillis() + 15000;
			while (!done[0] && !waiting.isDisposed() && (System.currentTimeMillis() < end)) {
				if (!display.readAndDispatch()) {
					display.timerExec(200, () -> {
					});
					display.sleep();
				}
			}
			if (!waiting.isDisposed())
				waiting.dispose();
		} catch (RuntimeException | Error e) {
			// the new DBGL starts all the same
		}
	}
}
