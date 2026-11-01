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
package org.dbgl.gui.dialog;

import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


/** asks for one line of text, e.g. a name; the result is null when cancelled or empty */
public class TextInputDialog extends SizeControlledButtonDialog<String> {

	private final String title_;
	private final String label_;
	private final String initial_;

	public TextInputDialog(Shell parent, String title, String label, String initial) {
		super(parent, "textinputdialog");
		title_ = title;
		label_ = label;
		initial_ = initial;
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
		Text text = new Text(contents_, SWT.BORDER);
		text.setText(initial_ == null ? "": initial_);
		text.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		text.selectAll();
		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				String value = text.getText().strip();
				result_ = value.isEmpty() ? null: value;
				shell_.close();
			}
		});
		shell_.setDefaultButton(okButton_);
		text.setFocus();
	}
}
