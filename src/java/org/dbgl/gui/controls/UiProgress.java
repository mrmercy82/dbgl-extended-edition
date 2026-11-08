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

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;


/**
 * A progress bar for work that has to run in the user interface thread (e.g. building all pages of the profile window): the
 * work calls {@link #step()} now and then, and the bar is drawn right away. It only appears when the work takes a moment.
 */
public final class UiProgress implements AutoCloseable {

	private static final long SHOW_AFTER_MILLIS = Long.getLong("dbgl.uiprogress.delay", 150);

	private final Shell parent_;
	private final String message_;
	private final int total_;
	private final long start_ = System.currentTimeMillis();
	private int done_;
	private Shell shell_;
	private ProgressBar bar_;

	public UiProgress(Shell parent, String message, int total) {
		parent_ = parent;
		message_ = message;
		total_ = Math.max(1, total);
	}

	/** one part of the work is done */
	public void step() {
		done_ = Math.min(total_, done_ + 1);
		if ((shell_ == null) && (System.currentTimeMillis() - start_ >= SHOW_AFTER_MILLIS) && (done_ < total_) && !parent_.isDisposed())
			open();
		if ((bar_ != null) && !bar_.isDisposed()) {
			bar_.setSelection(done_);
			shell_.update();
		}
	}

	private void open() {
		Display display = parent_.getDisplay();
		shell_ = new Shell(parent_, SWT.TITLE | SWT.BORDER | SWT.APPLICATION_MODAL);
		shell_.setText(parent_.getText());
		shell_.setLayout(new GridLayout(1, false));
		Label label = new Label(shell_, SWT.NONE);
		label.setText(message_);
		bar_ = new ProgressBar(shell_, SWT.SMOOTH);
		bar_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		bar_.setMaximum(total_);
		shell_.addListener(SWT.Close, e -> e.doit = false);
		shell_.pack();
		org.eclipse.swt.graphics.Point size = shell_.getSize();
		size.x = Math.max(size.x, 360);
		shell_.setSize(size);
		org.eclipse.swt.graphics.Rectangle p = parent_.getBounds();
		shell_.setLocation(p.x + (p.width - size.x) / 2, p.y + (p.height - size.y) / 2);
		shell_.open();
		// let the system show it (only painting; the window is modal, so no clicks reach the work)
		for (int i = 0; (i < 20) && display.readAndDispatch(); i++) {
			// nothing else
		}
		shell_.update();
	}

	@Override
	public void close() {
		if ((shell_ != null) && !shell_.isDisposed())
			shell_.dispose();
	}
}
