/*
 *  Copyright (C) 2026  mrmercy82 (DBGL Extended Edition, written with the help of AI)
 *  Part of a modified version of DBGL, Copyright (C) 2006-2025  Ronald Blankendaal
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.DatabaseService;
import org.dbgl.service.SettingsService;
import org.dbgl.util.ExecuteUtils;
import org.dbgl.util.ExecuteUtils.ProfileRunMode;
import org.eclipse.swt.widgets.Display;


/**
 * Alpha 48: "dbgl --run &lt;id&gt;" starts one profile, e.g. from Steam, and ends when the game ends, so that Steam shows it as
 * being played. The play time is counted: by the DBGL that runs already (asked through its communication port, it starts the
 * game and answers when it ended), or else by this one, without a window.
 */
public final class RunProfile {

	public static final String RUN_PROFILE = "runprofile ";
	public static final String DONE = "done";
	public static final String ARG = "--run";

	private RunProfile() {
	}

	/** @return the id after --run, or -1 */
	public static int idOf(String[] args) {
		for (int i = 0; i + 1 < args.length; i++)
			if (ARG.equals(args[i]))
				try {
					return Integer.parseInt(args[i + 1].trim());
				} catch (NumberFormatException e) {
					return -1;
				}
		return -1;
	}

	/** starts the profile, and returns when its game ended */
	public static void run(int id) {
		SettingsService settings = SettingsService.getInstance();
		if (settings.getBooleanValue("communication", "port_enabled") && byRunningDbgl(settings.getIntValue("communication", "port"), id))
			return;
		runHere(id);
	}

	/** @return false if no DBGL answers on the port */
	static boolean byRunningDbgl(int port, int id) {
		try (Socket socket = new Socket("localhost", port);
				PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
			out.println(RUN_PROFILE + id);
			for (String line; (line = in.readLine()) != null;)
				if (line.startsWith(DONE))
					return true;
			return true; // the DBGL ended before the game: nothing to wait for any more
		} catch (IOException e) {
			return false;
		}
	}

	/** without a window: the database of this DBGL, DOSBox, the play time */
	static void runHere(int id) {
		Display display = Display.getDefault();
		try {
			List<DosboxVersion> versions = new DosboxVersionRepository().listAll();
			List<Profile> found = new ProfileRepository().list("", "GAM.ID = " + id, versions);
			if (found.isEmpty()) {
				System.err.println("DBGL --run: no profile " + id);
				return;
			}
			Profile prof = found.get(0);
			prof.resetAndLoadConfiguration();
			ExecuteUtils.doRunProfile(ProfileRunMode.NORMAL, prof, false, display);
			new ProfileRepository().registerRun(prof);
			Thread game = ExecuteUtils.lastStarted();
			while (game != null && game.isAlive())
				if (!display.readAndDispatch())
					display.sleep(); // the executor wakes the display when it ends (asyncExec)
		} catch (IOException | java.sql.SQLException | RuntimeException e) {
			System.err.println("DBGL --run " + id + ": " + e);
		} finally {
			try {
				DatabaseService.getInstance().shutdown();
			} catch (java.sql.SQLException e) {
				// nothing to do
			}
		}
	}

	/** how Steam (or a shortcut) starts this DBGL with a profile: the program and its options */
	public static List<String> command(int id) {
		List<String> cmd = new ArrayList<>(org.dbgl.util.EditionUpdater.restartCommand());
		int at = cmd.indexOf(ARG); // started with --run itself: without it
		if (at >= 0)
			cmd.subList(at, Math.min(cmd.size(), at + 2)).clear();
		cmd.add(ARG);
		cmd.add(String.valueOf(id));
		return cmd;
	}

	/** the folder to start in (where dbgl.jar is) */
	public static File startDir() {
		return new File(".").getAbsoluteFile().getParentFile();
	}
}
