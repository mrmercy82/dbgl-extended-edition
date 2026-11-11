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
		if (new File(dir, "slowwatch").exists() || new File(dir, "slowwatch.txt").exists())
			startSlowWatch(display, dir);
	}

	/**
	 * For a report about short delays (alpha 47): with a file "slowwatch" (or "slowwatch.txt") in the folder of DBGL, every time
	 * the window does not answer for more than 0.8 seconds, where DBGL spent that time is written to slow-<date>.txt: the
	 * places in the program, each with how long it was seen there (looked at every 50 ms). At most 30 files per start.
	 */
	private static void startSlowWatch(Display display, File dir) {
		Thread ui = display.getThread();
		Thread watch = new Thread(() -> {
			int files = 0;
			java.util.concurrent.atomic.AtomicLong answered = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
			long sent = 0;
			boolean pending = false;
			java.util.Map<String, Integer> seen = new java.util.LinkedHashMap<>();
			while (!display.isDisposed() && (files < 30)) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					return;
				}
				long now = System.currentTimeMillis();
				if (pending && (answered.get() >= sent)) { // answered again
					long stall = answered.get() - sent;
					if ((stall > 800) && !seen.isEmpty()) {
						writeSlow(dir, stall, seen);
						files++;
					}
					seen.clear();
					pending = false;
				}
				if (!pending) {
					sent = now;
					pending = true;
					try {
						display.asyncExec(() -> answered.set(System.currentTimeMillis()));
					} catch (org.eclipse.swt.SWTException e) {
						return;
					}
				} else if (now - sent > 300) {
					StringBuilder sb = new StringBuilder();
					int n = 0;
					for (StackTraceElement e: ui.getStackTrace()) {
						if (n++ >= 14)
							break;
						sb.append("\tat ").append(e).append('\n');
					}
					seen.merge(sb.toString(), 1, Integer::sum);
				}
			}
		}, "DBGL slow watch");
		watch.setDaemon(true);
		watch.start();
	}

	private static void writeSlow(File dir, long stall, java.util.Map<String, Integer> seen) {
		StringBuilder sb = new StringBuilder("DBGL did not answer for " + stall + " ms (" + LocalDateTime.now() + "), Java " + System.getProperty("java.version") + ", "
				+ System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n\n");
		seen.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).forEach(e -> sb.append(e.getValue() * 50).append(" ms:\n").append(e.getKey()).append('\n'));
		File file = new File(dir, "slow-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".txt");
		try {
			Files.writeString(file.toPath(), sb, StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println(sb);
		}
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
