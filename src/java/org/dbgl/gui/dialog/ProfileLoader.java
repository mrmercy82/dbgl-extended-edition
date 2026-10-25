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

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Group_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.ProgressBar_;
import org.dbgl.gui.thread.LoaderThread;
import org.dbgl.model.aggregate.Profile;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


public class ProfileLoader extends SizeControlledButtonDialog<List<Profile>> {

	private LoaderThread job_;
	private ProgressBar progressBar_;
	private Label status_;
	private Text log_;
	private final List<Profile> profs_;
	private boolean warningsDisplayed_;

	public ProfileLoader(Shell parent, List<Profile> profs) {
		super(parent, "profileloaderdialog");
		profs_ = profs;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.profileloader.title", new Object[] {profs_.size()});
	}

	@Override
	protected void onShellOpened() {
		job_ = new LoaderThread(log_, progressBar_, status_, profs_);
		job_.start();
	}

	@Override
	protected void shellDispatchCallback() {
		if (job_ != null && !job_.isAlive() && !warningsDisplayed_) {
			if (!job_.isEverythingOk()) {
				String msg = text_.get("dialog.profileloader.error.reading");
				if (!job_.getResult().isEmpty()) {
					okButton_.setEnabled(true);
					msg += StringUtils.LF + StringUtils.LF + text_.get("dialog.profileloader.confirm.continue", new Object[] {job_.getResult().size()});
				}
				Mess_.on(shell_).txt(msg).warning();
				status_.setText(text_.get("dialog.migration.reviewlog"));
				status_.pack();
			} else {
				result_ = job_.getResult();
				shell_.close();
			}
			warningsDisplayed_ = true;
		}
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout());

		Group progressGroup = Group_.on(contents_).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).layout(new GridLayout()).key("dialog.migration.progress").build();
		progressBar_ = ProgressBar_.on(progressGroup).max(profs_.size()).build();
		Chain chain = Chain.on(progressGroup).lbl(l -> l).txt(t -> t.multi().wrap().readOnly()).build();
		status_ = chain.getLabel();
		log_ = chain.getText();

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				result_ = job_.getResult();
				shell_.close();
			}
		});
		okButton_.setEnabled(false);
	}
}