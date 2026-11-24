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
import org.dbgl.util.Win3x.GroupItem;
import org.eclipse.swt.SWT;
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
 * Alpha 53: the programs in the groups of the Program Manager of the Windows 3.x a profile starts, to choose those that get a
 * profile of their own (a copy of the profile whose Windows starts the program right away). The result: the chosen items.
 */
public class Win3ProgramsDialog extends SizeControlledButtonDialog<List<GroupItem>> {

	private final String profileTitle_;
	private final List<GroupItem> items_;
	private final List<Boolean> there_;
	private Table table_;

	/** @param there for each item: its program is there (else shown, but not chosen at first) */
	public Win3ProgramsDialog(Shell parent, String profileTitle, List<GroupItem> items, List<Boolean> there) {
		super(parent, "win3programsdialog");
		profileTitle_ = profileTitle;
		items_ = items;
		there_ = there;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.win3programs.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(2, false));
		Label info = new Label(contents_, SWT.WRAP);
		info.setText(text_.get("dialog.win3programs.info", new Object[] {profileTitle_}));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		infoData.widthHint = 620;
		info.setLayoutData(infoData);

		Button all = new Button(contents_, SWT.PUSH);
		all.setText(text_.get("button.all"));
		Button none = new Button(contents_, SWT.PUSH);
		none.setText(text_.get("button.none"));

		table_ = new Table(contents_, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
		table_.setHeaderVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		tableData.heightHint = 300;
		table_.setLayoutData(tableData);
		for (String key: new String[] {"dialog.win3programs.column.name", "dialog.win3programs.column.program", "dialog.win3programs.column.group"})
			new TableColumn(table_, SWT.NONE).setText(text_.get(key));
		for (int i = 0; i < items_.size(); i++) {
			GroupItem it = items_.get(i);
			TableItem row = new TableItem(table_, SWT.NONE);
			row.setText(new String[] {it.name(), it.command() + (there_.get(i) ? "": "   (" + text_.get("dialog.win3programs.missing") + ")"), it.group()});
			row.setChecked(false);
		}
		for (TableColumn c: table_.getColumns())
			c.pack();
		all.addListener(SWT.Selection, e -> {
			for (TableItem row: table_.getItems())
				row.setChecked(true);
		});
		none.addListener(SWT.Selection, e -> {
			for (TableItem row: table_.getItems())
				row.setChecked(false);
		});

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				List<GroupItem> chosen = new ArrayList<>();
				TableItem[] rows = table_.getItems();
				for (int i = 0; i < rows.length; i++)
					if (rows[i].getChecked())
						chosen.add(items_.get(i));
				result_ = chosen;
				shell_.close();
			}
		});
	}
}
