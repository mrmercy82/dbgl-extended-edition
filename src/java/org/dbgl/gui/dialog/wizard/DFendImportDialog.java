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

import java.io.File;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.JobWizardDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.Group_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.thread.DFendImportThread;
import org.dbgl.gui.thread.DFendReloadedImportThread;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.service.FileLocationService;
import org.eclipse.swt.events.ExpandAdapter;
import org.eclipse.swt.events.ExpandEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ExpandBar;
import org.eclipse.swt.widgets.ExpandItem;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


public class DFendImportDialog extends JobWizardDialog<Object> {

	private DosboxVersion defaultDbversion_;
	private ExpandItem orginalExpandItem_;
	private Text location_, dfrLocation_, dfrConfsLocation_;
	private Button cleanup_, dfrCleanup_;

	public DFendImportDialog(Shell parent, DosboxVersion dbversion) {
		super(parent, "dfendimportdialog");
		defaultDbversion_ = dbversion;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.dfendimport.title");
	}

	@Override
	protected void onShellCreated() {
		Group optionsGroup = Group_.on(shell_).layout(new FillLayout()).key("dialog.dfendimport.options").build();

		ExpandBar bar = createExpandBar(optionsGroup);

		Composite originalComposite = Composite_.on(bar).layout(new GridLayout(3, false)).build();
		location_ = Chain.on(originalComposite).lbl(l -> l.key("dialog.dfendimport.dfendpath")).txt(t -> t.val(FileLocationService.DFEND_STRING)).but(
			b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.DFEND, false)).text();
		cleanup_ = Chain.on(originalComposite).lbl(l -> l.key("dialog.dfendimport.cleanup")).but(b -> b.horSpan(2).key("dialog.dfendimport.removesections").select(true)).button();

		orginalExpandItem_ = createExpandItem(bar, "dialog.dfendimport.original.title", false, originalComposite);

		Composite reloadedComposite = Composite_.on(bar).layout(new GridLayout(3, false)).build();
		dfrLocation_ = Chain.on(reloadedComposite).lbl(l -> l.key("dialog.dfendimport.reloaded.path")).txt(t -> t.val(FileLocationService.DFEND_RELOADED_PATH_STRING)).but(
			b -> b.browse(false, Button_.BrowseType.DIR, Button_.CanonicalType.NONE, false)).text();
		dfrConfsLocation_ = Chain.on(reloadedComposite).lbl(l -> l.key("dialog.dfendimport.reloaded.exportedconfspath2")).txt(t -> t).but(
			b -> b.browse(false, Button_.BrowseType.DIR, Button_.CanonicalType.NONE, false)).text();
		dfrCleanup_ = Chain.on(reloadedComposite).lbl(l -> l.key("dialog.dfendimport.cleanup")).but(b -> b.horSpan(2).key("dialog.dfendimport.reloaded.removesections").select(false)).button();

		createExpandItem(bar, "dialog.dfendimport.reloaded.title", true, reloadedComposite);

		bar.addExpandListener(new ExpandAdapter() {
			@Override
			public void itemCollapsed(ExpandEvent e) {
				bar.getItem((((ExpandItem)e.item).getText().equals(text_.get("dialog.dfendimport.reloaded.title"))) ? 0: 1).setExpanded(true);
				display_.asyncExec(() -> shell_.layout());
			}

			@Override
			public void itemExpanded(ExpandEvent e) {
				bar.getItem((((ExpandItem)e.item).getText().equals(text_.get("dialog.dfendimport.reloaded.title"))) ? 0: 1).setExpanded(false);
				display_.asyncExec(() -> shell_.layout());
			}
		});

		addStep(optionsGroup);

		addFinalStep("dialog.dfendimport.progress", "dialog.dfendimport.startimport");
	}

	@Override
	protected boolean onNext(int step) {
		if (step == 0) {
			if (!isValid())
				return false;
		} else if (step == 1) {
			try {
				if (orginalExpandItem_.getExpanded())
					job_ = new DFendImportThread(log_, progressBar_, status_, new File(location_.getText()), cleanup_.getSelection(), defaultDbversion_);
				else
					// maybe also import data files in the future
					job_ = new DFendReloadedImportThread(log_, progressBar_, status_, new File(dfrLocation_.getText()), StringUtils.isBlank(dfrConfsLocation_.getText()) ? new File(dfrLocation_.getText(), "-no exported confs-"): new File(dfrConfsLocation_.getText()), dfrCleanup_.getSelection(),
							defaultDbversion_);
			} catch (IOException e) {
				Mess_.on(shell_).exception(e).warning();
				return false;
			}
		} else if (step == 2) {
			if (job_.isEverythingOk())
				Mess_.on(shell_).key("dialog.dfendimport.notice.importok").display();
			else
				Mess_.on(shell_).key("dialog.dfendimport.error.problem").warning();

			setReviewStatus("dialog.dfendimport.reviewlog");
			status_.pack();

			result_ = job_;
		}
		return true;
	}

	private boolean isValid() {
		Mess_.Builder mess = Mess_.on(shell_);
		if (orginalExpandItem_.getExpanded()) {
			if (StringUtils.isBlank(location_.getText())) {
				mess.key("dialog.dfendimport.required.location").bind(location_);
			}
		} else {
			if (StringUtils.isBlank(dfrLocation_.getText())) {
				mess.key("dialog.dfendimport.reloaded.required.location").bind(dfrLocation_);
			}
			// the exported .conf files are optional since alpha 46: without them, the .prof files are converted
		}
		return mess.valid();
	}
}