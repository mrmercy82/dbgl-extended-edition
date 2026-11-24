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

import java.io.File;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;


/**
 * Alpha 53: before "Database cleanup": what it does and what it leaves alone, and whether a backup is made first (on at
 * first; it shows in "Manage backups" and can be restored there). The result: true with a backup, false without, null when
 * cancelled.
 */
public class DatabaseCleanupDialog extends SizeControlledButtonDialog<Boolean> {

	private final File backupDir_;
	private Button backup_;

	public DatabaseCleanupDialog(Shell parent, File backupDir) {
		super(parent, "databasecleanupdialog");
		backupDir_ = backupDir;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.databasecleanup.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));
		Label info = new Label(contents_, SWT.WRAP);
		info.setText(text_.get("dialog.databasecleanup.info"));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		infoData.widthHint = 600;
		info.setLayoutData(infoData);
		backup_ = new Button(contents_, SWT.CHECK);
		backup_.setText(text_.get("dialog.databasecleanup.backup", new Object[] {backupDir_.getPath()}));
		backup_.setSelection(true);
		backup_.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		Label restore = new Label(contents_, SWT.WRAP);
		restore.setText(text_.get("dialog.databasecleanup.restore"));
		GridData restoreData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		restoreData.widthHint = 600;
		restore.setLayoutData(restoreData);

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				result_ = backup_.getSelection();
				shell_.close();
			}
		});
		okButton_.setText(text_.get("dialog.databasecleanup.go"));
	}
}
