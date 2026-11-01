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

import java.util.List;

import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;


/** pick one entry of a list; the first one is preselected. The result is the index, or null when cancelled */
public class ChoiceDialog extends SizeControlledButtonDialog<Integer> {

	private final String title_;
	private final String label_;
	private final List<String> items_;

	public ChoiceDialog(Shell parent, String title, String label, List<String> items) {
		super(parent, "choicedialog");
		title_ = title;
		label_ = label;
		items_ = items;
	}

	@Override
	protected String getDialogTitle() {
		return title_;
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));
		Label label = new Label(contents_, SWT.WRAP);
		label.setText(label_);
		label.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List(contents_, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
		items_.forEach(list::add);
		list.select(0);
		list.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		SelectionAdapter ok = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				result_ = list.getSelectionIndex() < 0 ? null: list.getSelectionIndex();
				shell_.close();
			}
		};
		createOkCancelButtons(ok);
		list.addListener(SWT.DefaultSelection, e -> ok.widgetSelected(null));
		shell_.setDefaultButton(okButton_);
		list.setFocus();
	}
}
