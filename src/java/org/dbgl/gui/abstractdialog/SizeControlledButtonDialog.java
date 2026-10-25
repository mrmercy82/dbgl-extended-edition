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
package org.dbgl.gui.abstractdialog;

import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Composite_;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;


public abstract class SizeControlledButtonDialog<T> extends SizeControlledDialog<T> {

	protected Composite contents_;
	protected Composite buttons_;
	protected Composite okCancelButtons_;
	protected Composite otherButtons_;

	protected Button okButton_;
	protected Button cancelButton_;

	protected SizeControlledButtonDialog(Shell parent, String dialogName) {
		super(parent, dialogName);
	}

	@Override
	protected void onShellInit() {
		super.onShellInit();

		shell_.setLayout(new GridLayout());

		contents_ = Composite_.on(shell_).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).layout(null).build();

		buttons_ = Composite_.on(shell_).layoutData(new GridData(SWT.FILL, SWT.FILL, true, false)).innerLayout(2).build();
		okCancelButtons_ = Composite_.on(buttons_).layoutData(new GridData(SWT.BEGINNING, SWT.FILL, true, false)).innerLayout(2).build();
		otherButtons_ = Composite_.on(buttons_).layoutData(new GridData(SWT.BEGINNING, SWT.FILL, false, false)).innerLayout(2).build();
	}

	protected void createGoButton(String title, SelectionListener listener) {
		okButton_ = Button_.on(okCancelButtons_).text().key(title).listen(listener).ctrl();

		shell_.setDefaultButton(okButton_);
	}

	protected void createOkButton(SelectionListener listener) {
		createGoButton("button.ok", listener);
	}

	protected void createGoCancelButtons(String title, SelectionListener listener) {
		createGoButton(title, listener);
		cancelButton_ = Button_.on(okCancelButtons_).text().key("button.cancel").listen(closeShellAdapter).ctrl();
		setLayoutDataButtons(okButton_, cancelButton_);
	}

	protected void createOkCancelButtons(SelectionListener listener) {
		createGoCancelButtons("button.ok", listener);
	}
}