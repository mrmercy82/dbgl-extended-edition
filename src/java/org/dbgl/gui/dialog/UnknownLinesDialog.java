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

import java.util.ArrayList;
import java.util.List;

import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.util.UnknownAutoexecLines;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * Lets the user choose, line by line, what happens to the lines of the [autoexec] of a profile that DBGL does not know: keep
 * (in which own section, with or without DBGL's marker lines) or remove. The result is null when cancelled.
 */
public class UnknownLinesDialog extends SizeControlledButtonDialog<List<UnknownAutoexecLines.Decision>> {

	private final String profileTitle_;
	private final List<Autoexec.UnknownLine> lines_;

	public UnknownLinesDialog(Shell parent, String profileTitle, List<Autoexec.UnknownLine> lines) {
		super(parent, "unknownlinesdialog");
		profileTitle_ = profileTitle;
		lines_ = lines;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.unknownlines.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(3, false));
		Label info = new Label(contents_, SWT.WRAP);
		info.setText(text_.get("dialog.unknownlines.info", new Object[] {profileTitle_}));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		infoData.widthHint = 400;
		info.setLayoutData(infoData);
		Button all = new Button(contents_, SWT.PUSH);
		all.setText(text_.get("dialog.switchdosbox.keepall"));
		Button none = new Button(contents_, SWT.PUSH);
		none.setText(text_.get("dialog.switchdosbox.keepnone"));

		Table table = new Table(contents_, SWT.CHECK | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		TableColumn lineColumn = new TableColumn(table, SWT.NONE);
		lineColumn.setText(text_.get("dialog.unknownlines.column.line"));
		TableColumn sectionColumn = new TableColumn(table, SWT.NONE);
		sectionColumn.setText(text_.get("dialog.unknownlines.column.section"));

		String[] sections = new String[Autoexec.SECTIONS];
		for (int i = 0; i < sections.length; i++)
			sections[i] = text_.get("dialog.template.customcommand" + (i + 1));
		List<CCombo> combos = new ArrayList<>();
		for (Autoexec.UnknownLine l: lines_) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(0, l.text());
			item.setChecked(true);
			CCombo combo = new CCombo(table, SWT.READ_ONLY | SWT.FLAT);
			if (l.afterExit()) {
				combo.setItems(new String[] {text_.get("dialog.unknownlines.afterexit")});
				combo.select(0);
				combo.setEnabled(false);
			} else {
				combo.setItems(sections);
				combo.select(l.section());
			}
			combo.pack();
			TableEditor editor = new TableEditor(table);
			editor.grabHorizontal = true;
			editor.setEditor(combo, item, 1);
			combos.add(combo);
		}
		lineColumn.pack();
		lineColumn.setWidth(Math.max(lineColumn.getWidth() + 20, 260));
		// wide enough for the longest section name plus the arrow of the list
		org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(table);
		int longest = 0;
		for (String s: sections)
			longest = Math.max(longest, gc.textExtent(s).x);
		longest = Math.max(longest, gc.textExtent(text_.get("dialog.unknownlines.afterexit")).x);
		gc.dispose();
		sectionColumn.setWidth(Math.max(longest + 70, combos.stream().mapToInt(c -> c.getSize().x).max().orElse(0) + 40));

		Button markers = new Button(contents_, SWT.CHECK);
		markers.setText(text_.get("dialog.unknownlines.markers"));
		markers.setSelection(true);
		markers.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

		all.addListener(SWT.Selection, e -> {
			for (TableItem item: table.getItems())
				item.setChecked(true);
		});
		none.addListener(SWT.Selection, e -> {
			for (TableItem item: table.getItems())
				item.setChecked(false);
		});

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				List<UnknownAutoexecLines.Decision> result = new ArrayList<>();
				for (int i = 0; i < lines_.size(); i++) {
					Autoexec.UnknownLine l = lines_.get(i);
					int section = l.afterExit() ? l.section(): combos.get(i).getSelectionIndex();
					result.add(new UnknownAutoexecLines.Decision(l, table.getItem(i).getChecked(), section, markers.getSelection()));
				}
				result_ = result;
				shell_.close();
			}
		});
		shell_.setDefaultButton(okButton_);
	}
}
