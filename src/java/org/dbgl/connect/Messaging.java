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
package org.dbgl.connect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import org.dbgl.gui.SendToProfile;
import org.dbgl.gui.dialog.MainWindow;
import org.dbgl.service.ITextService;


public class Messaging extends Thread {

	private final int port_;
	private final MainWindow mainWindow_;
	private final ITextService text_;

	private ServerSocket server_;

	public Messaging(int port, MainWindow mainWindow, ITextService text) {
		port_ = port;
		mainWindow_ = mainWindow;
		text_ = text;
	}

	public void close() {
		try {
			if (server_ != null)
				server_.close();
		} catch (IOException e) {
			System.err.println(text_.get("communication.error.closesocket"));
		}
	}

	@Override
	public void run() {
		try {
			server_ = new ServerSocket(port_);
			System.out.println(text_.get("communication.notice.listening", new Object[] {port_}));

			while (true) {
				Socket accepted = server_.accept();
				// alpha 48: a game started from outside (Steam: "dbgl --run <id>"); the answer comes when it ended
				accepted.setSoTimeout(5000);
				BufferedReader reader = new BufferedReader(new InputStreamReader(accepted.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
				String first;
				try {
					first = reader.readLine();
				} catch (IOException e) {
					first = null;
				}
				if (first != null && first.startsWith(org.dbgl.gui.RunProfile.RUN_PROFILE)) {
					String id = first.substring(org.dbgl.gui.RunProfile.RUN_PROFILE.length()).trim();
					Thread t = new Thread(() -> {
						try (Socket client = accepted) {
							Thread game = mainWindow_.runProfileById(Integer.parseInt(id));
							if (game != null)
								game.join();
							client.getOutputStream().write((org.dbgl.gui.RunProfile.DONE + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
						} catch (IOException | NumberFormatException e) {
							System.err.println(text_.get("communication.error.io"));
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
					}, "DBGL run from outside");
					t.setDaemon(true);
					t.start();
					continue;
				}
				final String firstLine = first;
				try (Socket client = accepted; BufferedReader bufferedReader = reader) {
					String line = firstLine;
					System.out.print(text_.get("communication.notice.receivingmessage"));
					System.out.println(' ' + line);
					if (line != null && line.startsWith(SendToProfile.SEND_TO_PROFILE))
						mainWindow_.addProfile(line.substring(SendToProfile.SEND_TO_PROFILE.length()));
				} catch (SocketException se) {
					break;
				} catch (IOException e) {
					System.err.println(text_.get("communication.error.io"));
				}
			}
		} catch (IOException e) {
			System.err.println(text_.get("communication.error.createsocket", new Object[] {port_}));
		}
	}
}
