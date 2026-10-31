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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.service.TextService;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
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
		GridLayout layout = new GridLayout(3, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		shell_.setLayout(layout);
		text_ = new Text(shell_, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
		text_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		font_ = new Font(display_, SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		text_.setFont(font_);
		if (DarkTheme.forced()) {
			text_.setBackground(DarkTheme.inputBackground);
			text_.setForeground(DarkTheme.defaultForeground);
		} else {
			text_.setBackground(display_.getSystemColor(SWT.COLOR_BLACK));
			text_.setForeground(display_.getSystemColor(SWT.COLOR_GRAY));
		}
		Label filler = new Label(shell_, SWT.NONE);
		filler.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button copy = new Button(shell_, SWT.PUSH);
		copy.setText(TextService.getInstance().get("dialog.statuswindow.copy"));
		copy.addListener(SWT.Selection, e -> copyToClipboard());
		Button save = new Button(shell_, SWT.PUSH);
		save.setText(TextService.getInstance().get("dialog.statuswindow.save"));
		save.addListener(SWT.Selection, e -> saveToFile());
		shell_.addDisposeListener(e -> font_.dispose());
		shell_.setSize(760, 420);
		shell_.setVisible(true);
	}

	private void copyToClipboard() {
		Clipboard clipboard = new Clipboard(display_);
		try {
			String content = text_.getSelectionCount() > 0 ? text_.getSelectionText(): text_.getText();
			if (!content.isEmpty())
				clipboard.setContents(new Object[] {content}, new Transfer[] {TextTransfer.getInstance()});
		} finally {
			clipboard.dispose();
		}
	}

	private void saveToFile() {
		FileDialog dialog = new FileDialog(shell_, SWT.SAVE);
		dialog.setFilterExtensions(new String[] {"*.txt", "*.*"});
		dialog.setFileName("dosbox-log.txt");
		dialog.setOverwrite(true);
		String file = dialog.open();
		if (file == null)
			return;
		try {
			Files.writeString(new File(file).toPath(), text_.getText(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
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
