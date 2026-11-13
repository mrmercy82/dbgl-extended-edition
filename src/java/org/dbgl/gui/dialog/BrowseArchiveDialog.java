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
import org.dbgl.gui.controls.Mess_;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.StringRelatedUtils;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Shell;


public class BrowseArchiveDialog extends SizeControlledButtonDialog<String> {

	private final String fileToBrowse_;
	private String[] executablesInArchive_;

	/**
	 * file names that start a game in many collections and on PC-98 disks (alpha 48): shown in bold, the first of them (the one
	 * least deep in folders) chosen beforehand
	 */
	static final java.util.Set<String> START_FILES = java.util.Set.of("play.bat", "start.bat", "start.exe", "start.com", "run.bat", "go.bat", "game.bat",
		"game.exe", "game.com", "play.exe", "play.com", "run.exe", "run.com", "launch.bat", "menu.bat", "go.exe", "go.com");

	/** the index of the file to choose beforehand: a known start file, else the most likely main file as for a new profile */
	static int likelyStart(String archive, String[] files, java.util.Set<Integer> bold) {
		int best = -1;
		for (int i = 0; i < files.length; i++) {
			if (START_FILES.contains(new java.io.File(files[i].replace('\\', '/')).getName().toLowerCase(java.util.Locale.ROOT))) {
				bold.add(i);
				if ((best < 0) || (depth(files[i]) < depth(files[best])))
					best = i;
			}
		}
		java.util.List<java.io.File> list = java.util.Arrays.stream(files).map(f -> new java.io.File(f.replace('\\', '/'))).toList();
		int main = org.dbgl.util.DosGameUtils.findMostLikelyMainIndex(org.apache.commons.io.FilenameUtils.getBaseName(archive), list);
		if ((main >= 0) && (main < files.length)) {
			bold.add(main);
			if (best < 0)
				best = main;
		}
		return Math.max(best, 0);
	}

	private static int depth(String file) {
		return (int)file.chars().filter(c -> (c == '/') || (c == '\\')).count();
	}

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

		// a table instead of a list: likely start files in bold (alpha 48)
		org.eclipse.swt.widgets.Label hint = new org.eclipse.swt.widgets.Label(contents_, org.eclipse.swt.SWT.WRAP);
		hint.setText(text_.get("dialog.archivebrowser.boldhint"));
		org.eclipse.swt.layout.GridData hintData = new org.eclipse.swt.layout.GridData(org.eclipse.swt.SWT.FILL, org.eclipse.swt.SWT.CENTER, true, false);
		hintData.widthHint = 300; // wraps as wide as the dialog
		hint.setLayoutData(hintData);
		org.eclipse.swt.widgets.Table files = new org.eclipse.swt.widgets.Table(contents_, org.eclipse.swt.SWT.SINGLE | org.eclipse.swt.SWT.FULL_SELECTION | org.eclipse.swt.SWT.BORDER | org.eclipse.swt.SWT.V_SCROLL | org.eclipse.swt.SWT.H_SCROLL);
		files.setLayoutData(new org.eclipse.swt.layout.GridData(org.eclipse.swt.SWT.FILL, org.eclipse.swt.SWT.FILL, true, true));
		org.eclipse.swt.graphics.FontData[] fd = files.getFont().getFontData();
		for (org.eclipse.swt.graphics.FontData d: fd)
			d.setStyle(org.eclipse.swt.SWT.BOLD);
		org.eclipse.swt.graphics.Font bold = new org.eclipse.swt.graphics.Font(shell_.getDisplay(), fd);
		shell_.addDisposeListener(e -> bold.dispose());
		new org.eclipse.swt.widgets.TableColumn(files, org.eclipse.swt.SWT.NONE);
		java.util.Set<Integer> boldOnes = new java.util.HashSet<>();
		int start = likelyStart(fileToBrowse_, executablesInArchive_, boldOnes);
		for (int i = 0; i < executablesInArchive_.length; i++) {
			org.eclipse.swt.widgets.TableItem item = new org.eclipse.swt.widgets.TableItem(files, org.eclipse.swt.SWT.NONE);
			item.setText(executablesInArchive_[i]);
			if (boldOnes.contains(i))
				item.setFont(bold);
		}
		files.getColumn(0).pack();
		files.setSelection(start);
		files.showSelection();
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

	private void doChooseFile(org.eclipse.swt.widgets.Table files) {
		if (files.getSelectionIndex() < 0)
			return;
		result_ = FilesUtils.determineFullArchiveName(fileToBrowse_, executablesInArchive_[files.getSelectionIndex()]);
		shell_.close();
	}
}
