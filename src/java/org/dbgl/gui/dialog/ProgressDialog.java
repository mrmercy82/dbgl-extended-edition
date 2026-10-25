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

import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.controls.Group_;
import org.dbgl.gui.controls.ProgressBar_;
import org.dbgl.gui.interfaces.ProgressNotifyable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;


public class ProgressDialog extends BaseDialog<Object> implements ProgressNotifyable {

	private final String title_;
	private Thread thread_;
	private ProgressBar progressBar_;

	public ProgressDialog(Shell parent, String title) {
		super(parent);
		title_ = title;
	}

	public void setThread(Thread thread) {
		thread_ = thread;
	}

	@Override
	protected String getDialogTitle() {
		return title_;
	}

	@Override
	protected void onShellCreated() {
		shell_.setSize(600, 140);
		shell_.setLayout(new GridLayout());

		Group progressGroup = Group_.on(shell_).layout(new GridLayout()).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, true)).key("dialog.migration.progress").build();
		progressBar_ = ProgressBar_.on(progressGroup).build();
	}

	@Override
	protected void onShellOpened() {
		thread_.start();
	}

	@Override
	protected void shellDispatchCallback() {
		if (!thread_.isAlive())
			shell_.close();
	}

	@Override
	public void setTotal(long total) {
		if (!display_.isDisposed()) {
			display_.asyncExec(() -> {
				if (!progressBar_.isDisposed())
					progressBar_.setMaximum((int)(total / 1024));
			});
		}
	}

	@Override
	public void incrProgress(long progress) {
		if (!display_.isDisposed()) {
			display_.asyncExec(() -> {
				if (!progressBar_.isDisposed())
					progressBar_.setSelection(progressBar_.getSelection() + (int)(progress / 1024));
			});
		}
	}

	@Override
	public void setProgress(long progress) {
		if (!display_.isDisposed()) {
			display_.asyncExec(() -> {
				if (!progressBar_.isDisposed())
					progressBar_.setSelection((int)(progress / 1024));
			});
		}
	}
}
