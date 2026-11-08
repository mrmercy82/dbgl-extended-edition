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
package org.dbgl.gui.controls;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.dbgl.service.TextService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;


/**
 * Runs slow work (e.g. a request to a game database) in the background while a small window says what is going on and offers
 * to cancel, instead of the window not reacting until the work is done.
 */
public final class BusyTask {

	/** the user clicked Cancel; the work may still finish in the background, its result is not used */
	public static final class Cancelled extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	private BusyTask() {
	}

	public static <T> T run(Shell parent, String message, Callable<T> work) throws Exception {
		return run(parent, message, null, 0, work);
	}

	/**
	 * Like {@link #run(Shell, String, Callable)}, with a bar that shows how far the work is: it counts progress up to total.
	 */
	public static <T> T run(Shell parent, String message, java.util.concurrent.atomic.AtomicInteger progress, int total, Callable<T> work) throws Exception {
		return run(parent, message, progress, total, true, 400, work);
	}

	/** for work that must not be left half done (e.g. saving): a progress bar without Cancel, shown a little earlier */
	public static <T> T runToEnd(Shell parent, String message, Callable<T> work) throws Exception {
		return run(parent, message, null, 0, false, 150, work);
	}

	private static <T> T run(Shell parent, String message, java.util.concurrent.atomic.AtomicInteger progress, int total, boolean cancellable, long delayMillis, Callable<T> work)
			throws Exception {
		Display display = parent.getDisplay();
		AtomicReference<Object> result = new AtomicReference<>();
		AtomicReference<Exception> failure = new AtomicReference<>();
		boolean[] done = {false};
		boolean[] cancelled = {false};
		Thread worker = new Thread(() -> {
			try {
				result.set(work.call());
			} catch (Exception e) {
				failure.set(e);
			} finally {
				synchronized (done) {
					done[0] = true;
				}
				if (!display.isDisposed())
					display.wake();
			}
		}, "BusyTask");
		worker.setDaemon(true);
		worker.start();

		// only show the window when it takes a moment, so quick answers do not flicker
		long showAt = System.currentTimeMillis() + delayMillis;
		Shell busy = null;
		Runnable[] tick = new Runnable[1];
		tick[0] = () -> {
			synchronized (done) {
				if (!done[0] && !cancelled[0])
					display.timerExec(50, tick[0]); // wake up now and then to check
			}
		};
		display.timerExec(50, tick[0]);
		while (true) {
			synchronized (done) {
				if (done[0])
					break;
			}
			if (cancelled[0])
				break;
			if ((busy == null) && (System.currentTimeMillis() >= showAt))
				busy = open(parent, message, cancellable ? cancelled: null, (progress != null) && (total > 0) ? total: 0);
			if ((busy != null) && (progress != null) && (busy.getData() instanceof ProgressBar bar) && !bar.isDisposed())
				bar.setSelection(Math.min(total, progress.get()));
			if (!display.readAndDispatch())
				display.sleep();
		}
		if ((busy != null) && !busy.isDisposed())
			busy.dispose();
		if (cancelled[0])
			throw new Cancelled();
		if (failure.get() != null)
			throw failure.get();
		@SuppressWarnings("unchecked")
		T t = (T)result.get();
		return t;
	}

	/** @param cancelled null: no Cancel, and the window cannot be closed */
	private static Shell open(Shell parent, String message, boolean[] cancelled, int total) {
		Shell busy = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		busy.setText(parent.getText());
		org.dbgl.gui.abstractdialog.BaseDialog.inheritWindowImage(busy, parent);
		busy.setLayout(new GridLayout(1, false));
		Label label = new Label(busy, SWT.NONE);
		label.setText(message);
		ProgressBar bar = new ProgressBar(busy, total > 0 ? SWT.SMOOTH: SWT.INDETERMINATE);
		bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		if (total > 0) {
			bar.setMaximum(total);
			busy.setData(bar);
		}
		if (cancelled != null) {
			Button cancel = new Button(busy, SWT.PUSH);
			cancel.setText(TextService.getInstance().get("button.cancel"));
			GridData cd = new GridData(SWT.END, SWT.CENTER, false, false);
			cd.widthHint = Math.max(80, cancel.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
			cancel.setLayoutData(cd);
			cancel.addListener(SWT.Selection, e -> cancelled[0] = true);
			busy.addListener(SWT.Close, e -> cancelled[0] = true);
		} else {
			busy.addListener(SWT.Close, e -> e.doit = false);
		}
		busy.pack();
		org.eclipse.swt.graphics.Point size = busy.getSize();
		size.x = Math.max(size.x, 360);
		busy.setSize(size);
		org.eclipse.swt.graphics.Rectangle p = parent.getBounds();
		busy.setLocation(p.x + (p.width - size.x) / 2, p.y + (p.height - size.y) / 2);
		busy.open();
		return busy;
	}
}
