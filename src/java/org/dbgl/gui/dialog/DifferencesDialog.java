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
package org.dbgl.gui.dialog;

import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


/**
 * Shows a text that was put together already, e.g. which settings of a profile differ from the defaults of its DOSBox version.
 */
public class DifferencesDialog extends SizeControlledButtonDialog<Object> {

	private final String title_;
	private String content_;
	private final String saveName_;
	private String actionKey_;
	private java.util.function.Function<Shell, String> action_;
	private Text text_;
	private Font font_;

	public DifferencesDialog(Shell parent, String title, String content) {
		this(parent, title, content, null, "differencesdialog");
	}

	/**
	 * @param saveName with a button to save the text as a report, suggesting this file name; null for none
	 * @param dialogName for the size and place of the window that are kept
	 */
	public DifferencesDialog(Shell parent, String title, String content, String saveName, String dialogName) {
		super(parent, dialogName);
		title_ = title;
		content_ = content;
		saveName_ = saveName;
	}

	/**
	 * a button at the far left, e.g. to tidy up what the text is about
	 *
	 * @param action does it (it may ask first) and gives the new text, or null when nothing changed
	 */
	/** the small symbol in the title bar (otherwise that of the log) */
	public DifferencesDialog withImage(String img) {
		windowImage_ = img;
		return this;
	}

	/** other formats to save in (e.g. "html", "csv"): chosen by the ending of the file name in the save dialog */
	private final java.util.Map<String, java.util.function.Supplier<String>> exports_ = new java.util.LinkedHashMap<>();
	private final java.util.Map<String, String> exportNames_ = new java.util.LinkedHashMap<>();

	public DifferencesDialog withExport(String extension, String name, java.util.function.Supplier<String> content) {
		exports_.put(extension, content);
		exportNames_.put(extension, name);
		return this;
	}

	public DifferencesDialog withAction(String key, java.util.function.Function<Shell, String> action) {
		actionKey_ = key;
		action_ = action;
		return this;
	}

	@Override
	protected String getDialogTitle() {
		return title_;
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));
		text_ = new Text(contents_, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		text_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		font_ = new Font(shell_.getDisplay(), SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		text_.setFont(font_);
		shell_.addDisposeListener(e -> font_.dispose());
		text_.setText(content_);
		// 'save report' right next to OK, at the bottom right as usual for a dialog that only shows something
		if (saveName_ != null)
			org.dbgl.gui.controls.Button_.on(okCancelButtons_).text().key("dialog.switchdosbox.savereport").listen(new org.eclipse.swt.events.SelectionAdapter() {
				@Override
				public void widgetSelected(org.eclipse.swt.events.SelectionEvent event) {
					saveReport();
				}
			}).ctrl();
		createOkButton(closeShellAdapter);
		okCancelButtons_.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));
		// another action at the far left
		if (action_ != null) {
			otherButtons_.moveAbove(okCancelButtons_);
			org.dbgl.gui.controls.Button_.on(otherButtons_).text().key(actionKey_).listen(new org.eclipse.swt.events.SelectionAdapter() {
				@Override
				public void widgetSelected(org.eclipse.swt.events.SelectionEvent event) {
					String changed = action_.apply(shell_);
					if (changed != null) {
						content_ = changed;
						text_.setText(changed);
					}
				}
			}).ctrl();
		}
	}

	/** with other formats: first a small menu (text, web page, CSV) below the button, then the save dialog for that format */
	private void saveReport() {
		if (exports_.isEmpty()) {
			saveAs(null);
			return;
		}
		org.eclipse.swt.widgets.Menu menu = new org.eclipse.swt.widgets.Menu(shell_, SWT.POP_UP);
		org.eclipse.swt.widgets.MenuItem plain = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
		plain.setText(org.dbgl.service.TextService.getInstance().get("dialog.statistics.save.text") + " (*." + ownExtension() + ")");
		plain.addListener(SWT.Selection, e -> saveAs(null));
		exportNames_.forEach((x, n) -> {
			org.eclipse.swt.widgets.MenuItem item = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
			item.setText(n + " (*." + x + ")");
			item.addListener(SWT.Selection, e -> saveAs(x));
		});
		org.eclipse.swt.graphics.Point at = shell_.getDisplay().getCursorLocation();
		menu.setLocation(at.x, at.y);
		menu.setVisible(true);
	}

	private String ownExtension() {
		return saveName_.contains(".") ? saveName_.substring(saveName_.lastIndexOf('.') + 1): "txt";
	}

	/** save as text (export null) or in one of the other formats; the file gets the ending of its format */
	private void saveAs(String export) {
		String ext = export == null ? ownExtension(): export;
		String base = saveName_.contains(".") ? saveName_.substring(0, saveName_.lastIndexOf('.')): saveName_;
		org.eclipse.swt.widgets.FileDialog dialog = new org.eclipse.swt.widgets.FileDialog(shell_, SWT.SAVE);
		dialog.setFilterExtensions(new String[] {"*." + ext, "*.*"});
		dialog.setFileName(base + "." + ext);
		dialog.setOverwrite(true);
		String file = dialog.open();
		if (file == null)
			return;
		if (!file.toLowerCase().endsWith("." + ext))
			file += "." + ext;
		try {
			String content = export == null ? content_: exports_.get(export).get();
			java.nio.file.Files.writeString(new java.io.File(file).toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
		} catch (RuntimeException | java.io.IOException e) {
			org.dbgl.gui.controls.Mess_.on(shell_).exception(e).warning();
		}
	}
}
