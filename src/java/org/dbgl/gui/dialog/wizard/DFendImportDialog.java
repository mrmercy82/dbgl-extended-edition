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
	private ExpandItem orginalExpandItem_, dfxExpandItem_;
	private Text location_, dfrLocation_, dfrConfsLocation_, dfxLocation_, dfxTarget_;
	private Button cleanup_, dfrCleanup_, dfxCleanup_;
	private final boolean dfendx_;
	private boolean importingDfendx_; // alpha 48: what is being imported, for the message at the end
	private File unpackedDir_; // alpha 48: the folder a DFendX archive was unpacked into (to take away if no profile came of it)
	private int profilesBefore_ = -1;

	public DFendImportDialog(Shell parent, DosboxVersion dbversion) {
		this(parent, dbversion, false);
	}

	/** alpha 48: with dfendx, the part for a game archive of DFendX is open */
	public DFendImportDialog(Shell parent, DosboxVersion dbversion, boolean dfendx) {
		super(parent, "dfendimportdialog");
		defaultDbversion_ = dbversion;
		dfendx_ = dfendx;
	}

	@Override
	protected String getDialogTitle() {
		// alpha 48: opened for a DFendX archive, the window says so (before "D-Fend (Reloaded) profiles": was it the right one?)
		return dfendx_ ? text_.get("dialog.main.menu.importdfendx").replace("...", "").replace("\u2026", "").replace("&", "").trim(): text_.get("dialog.dfendimport.title");
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

		createExpandItem(bar, "dialog.dfendimport.reloaded.title", !dfendx_, reloadedComposite);

		// alpha 48: a game archive of DFendX (ZIP file or unpacked folder: .prof next to VirtualHD and Capture)
		Composite dfxComposite = Composite_.on(bar).layout(new GridLayout(3, false)).build();
		dfxLocation_ = Chain.on(dfxComposite).lbl(l -> l.key("dialog.dfendimport.dfendx.path")).txt(t -> t).but(
			b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.ZIP, false)).text();
		dfxTarget_ = Chain.on(dfxComposite).lbl(l -> l.key("dialog.dfendimport.dfendx.target")).txt(t -> t.val(FileLocationService.getInstance().getDosroot().getPath())).but(
			b -> b.browse(false, Button_.BrowseType.DIR, Button_.CanonicalType.NONE, false)).text();
		dfxCleanup_ = Chain.on(dfxComposite).lbl(l -> l.key("dialog.dfendimport.cleanup")).but(b -> b.horSpan(2).key("dialog.dfendimport.reloaded.removesections").select(false)).button();
		dfxExpandItem_ = createExpandItem(bar, "dialog.dfendimport.dfendx.title", dfendx_, dfxComposite);

		// one part is always open (since alpha 48 for three parts)
		bar.addExpandListener(new ExpandAdapter() {
			@Override
			public void itemCollapsed(ExpandEvent e) {
				ExpandItem item = (ExpandItem)e.item;
				display_.asyncExec(() -> {
					if (!item.isDisposed() && java.util.Arrays.stream(bar.getItems()).noneMatch(ExpandItem::getExpanded))
						item.setExpanded(true);
					shell_.layout();
				});
			}

			@Override
			public void itemExpanded(ExpandEvent e) {
				for (ExpandItem other: bar.getItems())
					if (other != e.item)
						other.setExpanded(false);
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
				importingDfendx_ = dfxExpandItem_.getExpanded();
				if (importingDfendx_) {
					profilesBefore_ = profileCount();
					java.util.List<File> profs = dfendxProfs();
					if (profs == null)
						return false;
					job_ = new DFendReloadedImportThread(log_, progressBar_, status_, profs, dfxCleanup_.getSelection(), defaultDbversion_);
				} else if (orginalExpandItem_.getExpanded())
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
				Mess_.on(shell_).key(importingDfendx_ ? "dialog.dfendimport.dfendx.importok": "dialog.dfendimport.notice.importok").display();
			else
				Mess_.on(shell_).key(importingDfendx_ ? "dialog.dfendimport.dfendx.problem": "dialog.dfendimport.error.problem").warning();

			setReviewStatus("dialog.dfendimport.reviewlog");
			status_.pack();

			// alpha 48: unpacked, but no profile came of it: the folder need not stay in dosroot
			if (importingDfendx_ && (unpackedDir_ != null) && unpackedDir_.isDirectory() && (profilesBefore_ >= 0) && (profileCount() == profilesBefore_)
					&& Mess_.on(shell_).key("dialog.dfendimport.dfendx.removeunpacked", unpackedDir_.getPath()).confirm())
				org.apache.commons.io.FileUtils.deleteQuietly(unpackedDir_);

			result_ = job_;
		}
		return true;
	}

	/** the .prof files of the DFendX archive, a ZIP file unpacked first into a new folder of its name; null if none */
	private java.util.List<File> dfendxProfs() {
		File src = new File(dfxLocation_.getText().strip());
		File dir = src;
		boolean unpacked = false;
		if (src.isFile()) {
			dir = org.dbgl.model.conf.dfend.DFendXArchive.freeTargetDir(new File(dfxTarget_.getText().strip()), src);
			File target = dir;
			IOException[] failed = new IOException[1];
			org.eclipse.swt.custom.BusyIndicator.showWhile(display_, () -> {
				try {
					org.dbgl.model.conf.dfend.DFendXArchive.extract(src, target);
				} catch (IOException e) {
					failed[0] = e;
				}
			});
			if (failed[0] != null) {
				org.apache.commons.io.FileUtils.deleteQuietly(target); // the folder is new, only what was unpacked so far
				Mess_.on(shell_).exception(failed[0]).warning();
				return null;
			}
			unpacked = true;
			unpackedDir_ = dir;
		}
		java.util.List<File> profs = org.dbgl.model.conf.dfend.DFendXArchive.profFiles(dir);
		if (profs.isEmpty()) {
			if (unpacked)
				org.apache.commons.io.FileUtils.deleteQuietly(dir);
			Mess_.on(shell_).key("dialog.dfendimport.dfendx.noprof", src.getPath()).warning();
			return null;
		}
		return profs;
	}

	/** @return the number of profiles in the database, -1 if it cannot be read */
	private static int profileCount() {
		try {
			return new org.dbgl.model.repository.ProfileRepository().list("", "", new org.dbgl.model.repository.DosboxVersionRepository().listAll()).size();
		} catch (java.sql.SQLException e) {
			return -1;
		}
	}

	private boolean isValid() {
		Mess_.Builder mess = Mess_.on(shell_);
		if (dfxExpandItem_.getExpanded()) {
			File src = new File(dfxLocation_.getText().strip());
			if (StringUtils.isBlank(dfxLocation_.getText()) || !src.exists())
				mess.key("dialog.dfendimport.dfendx.required.location").bind(dfxLocation_);
			else if (src.isFile() && StringUtils.isBlank(dfxTarget_.getText()))
				mess.key("dialog.dfendimport.dfendx.required.target").bind(dfxTarget_);
		} else if (orginalExpandItem_.getExpanded()) {
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