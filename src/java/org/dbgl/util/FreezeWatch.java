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
package org.dbgl.util;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.eclipse.swt.widgets.Display;


/**
 * When the window of DBGL stops answering (its user interface thread busy or waiting for more than 10 seconds), what every
 * thread of DBGL was doing is written to freeze-<date>.txt in the folder of DBGL: what a report of the freeze needs, the window
 * itself is not touched. Once per freeze; nothing is written while DBGL answers.
 */
public final class FreezeWatch {

	private static final long PING_MS = 2000, FROZEN_MS = 10000;
	private static volatile long answered_;

	private FreezeWatch() {
	}

	public static void start(Display display, File dir) {
		answered_ = System.currentTimeMillis();
		Thread watch = new Thread(() -> {
			boolean written = false;
			while (!display.isDisposed()) {
				try {
					display.asyncExec(() -> answered_ = System.currentTimeMillis());
				} catch (org.eclipse.swt.SWTException e) {
					return; // closed
				}
				try {
					Thread.sleep(PING_MS);
				} catch (InterruptedException e) {
					return;
				}
				long late = System.currentTimeMillis() - answered_;
				if ((late > FROZEN_MS) && !written) {
					written = write(dir, late);
				} else if (late < PING_MS * 2) {
					written = false; // answers again: a next freeze is written again
				}
			}
		}, "DBGL freeze watch");
		watch.setDaemon(true);
		watch.setPriority(Thread.MIN_PRIORITY);
		watch.start();
	}

	private static boolean write(File dir, long late) {
		StringBuilder sb = new StringBuilder("DBGL has not answered for " + (late / 1000) + " s (" + LocalDateTime.now() + "), Java " + System.getProperty("java.version")
				+ ", " + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n\n");
		for (ThreadInfo info: ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
			sb.append('"').append(info.getThreadName()).append("\" ").append(info.getThreadState());
			if (info.getLockName() != null)
				sb.append(" waiting for ").append(info.getLockName()).append(info.getLockOwnerName() != null ? " held by \"" + info.getLockOwnerName() + "\"": "");
			sb.append('\n');
			for (StackTraceElement e: info.getStackTrace())
				sb.append("\tat ").append(e).append('\n');
			sb.append('\n');
		}
		File file = new File(dir, "freeze-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
		try {
			Files.writeString(file.toPath(), sb, StandardCharsets.UTF_8);
			System.err.println("DBGL not answering: written to " + file);
			return true;
		} catch (IOException e) {
			System.err.println(sb);
			return true;
		}
	}
}
