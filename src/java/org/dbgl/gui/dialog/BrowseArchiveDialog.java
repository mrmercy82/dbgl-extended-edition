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

import java.io.IOException;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.List_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.StringRelatedUtils;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;


public class BrowseArchiveDialog extends SizeControlledButtonDialog<String> {

	private final String fileToBrowse_;
	private String[] executablesInArchive_;

	public BrowseArchiveDialog(Shell parent, String fileToBrowse) {
		super(parent, "archivebrowser");
		fileToBrowse_ = fileToBrowse;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.archivebrowser.title");
	}

	@Override
	protected boolean prepare() {
		try {
			executablesInArchive_ = FilesUtils.listExecutablesInZipOrIsoOrFat(fileToBrowse_);
			if (executablesInArchive_.length <= 0) {
				Mess_.on(getParent()).key("dialog.archivebrowser.notice.noexe").warning();
				return false;
			}
		} catch (IOException e) {
			Mess_.on(getParent()).key("dialog.archivebrowser.error.readarchive", new Object[] {fileToBrowse_, StringRelatedUtils.toString(e)}).exception(e).warning();
			return false;
		}
		return true;
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout());

		List files = List_.on(contents_).items(executablesInArchive_).select(0).build();
		files.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent event) {
				doChooseFile(files);
			}
		});

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doChooseFile(files);
			}
		});
	}

	private void doChooseFile(List files) {
		result_ = FilesUtils.determineFullArchiveName(fileToBrowse_, files.getItem(files.getSelectionIndex()));
		shell_.close();
	}
}
