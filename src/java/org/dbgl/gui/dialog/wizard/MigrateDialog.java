/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-07. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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
package org.dbgl.gui.dialog.wizard;

import java.sql.SQLException;

import org.dbgl.gui.abstractdialog.JobWizardDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Group_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.thread.MigrateThread;
import org.dbgl.model.FileLocation;
import org.dbgl.model.ICanonicalize;
import org.dbgl.service.FileLocationService;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


public class MigrateDialog extends JobWizardDialog<String> {

	private Text from_;
	private ICanonicalize canonicalizer_;

	public MigrateDialog(Shell parent) {
		super(parent, "migratedialog");
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.migration.title");
	}

	@Override
	protected void onShellCreated() {
		Group optionsGroup = Group_.on(shell_).layout(new GridLayout(3, false)).key("dialog.migration.options").build();

		Chain chn = Chain.on(optionsGroup).lbl(l -> l.key("dialog.migration.from")).txt(t -> t).but(b -> b.browse(false, Button_.BrowseType.DIR, Button_.CanonicalType.NONE, false)).build();
		from_ = chn.getText();
		canonicalizer_ = (ICanonicalize)chn.getButton().getData(Button_.DATA_CANONICALIZER);
		Chain.on(optionsGroup).lbl(l -> l.key("dialog.migration.to")).txt(t -> t.horSpan(2).nonEditable().val(FileLocationService.getInstance().getDosroot().getPath())).build();
		addStep(optionsGroup);

		addFinalStep("dialog.migration.progress", "dialog.migration.startmigration");
	}

	@Override
	protected boolean onNext(int step) {
		if (step == 0) {
			if (!isValid())
				return false;
		} else if (step == 1) {
			try {
				job_ = new MigrateThread(log_, progressBar_, status_, new FileLocation(from_.getText(), canonicalizer_));
			} catch (SQLException e) {
				Mess_.on(shell_).exception(e).warning();
				return false;
			}
		} else if (step == 2) {
			if (job_.isEverythingOk())
				Mess_.on(shell_).key("dialog.migration.notice.migrationok").display();
			else
				Mess_.on(shell_).key("dialog.migration.error.problem").warning();

			setReviewStatus("dialog.migration.reviewlog");
			status_.pack();

			result_ = from_.getText();
		}
		return true;
	}

	private boolean isValid() {
		Mess_.Builder mess = Mess_.on(shell_);
		if (from_.getText().equals("")) {
			mess.key("dialog.migration.required.from").bind(from_);
		}
		return mess.valid();
	}
}
