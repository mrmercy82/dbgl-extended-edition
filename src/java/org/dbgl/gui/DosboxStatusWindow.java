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

import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


/**
 * Shows the output (log) of a running DOSBox process.
 *
 * Up to 0.82, DOSBox Staging opened such a "status window" (a console) on Windows by itself, unless started with -noconsole.
 * DOSBox Staging 0.83 no longer does, and since DBGL starts DOSBox without a console, nothing would be shown at all.
 * DBGL reads the output of DOSBox anyway, so it can show it itself.
 *
 * The window closes when DOSBox ends normally, like the console did. If DOSBox ends with an error, it stays open so the
 * messages can be read.
 */
public class DosboxStatusWindow {

	public static final String TITLE = "DOSBox Status Window";

	private final Display display_;
	private Shell shell_;
	private Text text_;
	private Font font_;
	private int pendingEvents_ = 3; // stdout closed, stderr closed, process ended
	private int exitCode_ = 0;

	private DosboxStatusWindow(Display display) {
		display_ = display;
	}

	/**
	 * @return the window, or null if there is no user interface to show it in
	 */
	public static DosboxStatusWindow open() {
		Display display = Display.getDefault();
		if ((display == null) || display.isDisposed())
			return null;
		DosboxStatusWindow window = new DosboxStatusWindow(display);
		display.syncExec(window::create);
		return window;
	}

	private void create() {
		shell_ = new Shell(display_, SWT.SHELL_TRIM);
		shell_.setText(TITLE);
		shell_.setLayout(new FillLayout());
		text_ = new Text(shell_, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
		font_ = new Font(display_, SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		text_.setFont(font_);
		if (DarkTheme.forced()) {
			text_.setBackground(DarkTheme.inputBackground);
			text_.setForeground(DarkTheme.defaultForeground);
		} else {
			text_.setBackground(display_.getSystemColor(SWT.COLOR_BLACK));
			text_.setForeground(display_.getSystemColor(SWT.COLOR_GRAY));
		}
		shell_.addDisposeListener(e -> font_.dispose());
		shell_.setSize(760, 420);
		shell_.setVisible(true);
	}

	private boolean isGone() {
		return (shell_ == null) || shell_.isDisposed() || (text_ == null) || text_.isDisposed();
	}

	/** may be called from any thread */
	public void append(String line) {
		if (display_.isDisposed())
			return;
		display_.asyncExec(() -> {
			if (!isGone())
				text_.append(line + Text.DELIMITER);
		});
	}

	/** may be called from any thread, once per output stream */
	public void streamClosed() {
		eventHappened();
	}

	/** may be called from any thread, once */
	public void processEnded(int exitCode) {
		synchronized (this) {
			exitCode_ = exitCode;
		}
		eventHappened();
	}

	private void eventHappened() {
		int exitCode;
		synchronized (this) {
			if (--pendingEvents_ > 0)
				return;
			exitCode = exitCode_;
		}
		if (display_.isDisposed())
			return;
		display_.asyncExec(() -> {
			if (isGone())
				return;
			if (exitCode == 0) {
				shell_.dispose();
			} else {
				text_.append(Text.DELIMITER + "DOSBox exited with code " + exitCode + Text.DELIMITER);
				shell_.setText(TITLE + " (exit code " + exitCode + ")");
			}
		});
	}
}
