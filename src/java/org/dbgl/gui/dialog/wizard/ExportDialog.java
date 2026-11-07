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
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.JobWizardDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.Group_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.Table_;
import org.dbgl.gui.thread.ExportThread;
import org.dbgl.model.GamePack;
import org.dbgl.model.ICanonicalize;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.entity.GamePackEntry;
import org.dbgl.service.FileLocationService;
import org.dbgl.util.FilesUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


public class ExportDialog extends JobWizardDialog<Object> {

	private GamePack gamePack_;
	private Button settingsOnly_, exportGameData_, exportCapturesButton_, exportMapperfilesButton_, exportNativeCommandsButton_;
	private Text title_, notes_, author_, filename_;
	private Table profilesTable_;
	private ICanonicalize canonicalizer_;

	public ExportDialog(Shell parent, List<Profile> profiles) {
		super(parent, "export");

		gamePack_ = new GamePack();
		for (int i = 0; i < profiles.size(); i++) {
			Profile prof = profiles.get(i);
			gamePack_.getEntries().add(new GamePackEntry(i, prof, gamePack_));
		}
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.export.title", new Object[] {gamePack_.getEntries().size()});
	}

	@Override
	protected int stepSize(int step, boolean forward) {
		if (((step == 0 && forward) || (step == 2 && !forward)) && settingsOnly_.getSelection())
			return 2;
		return super.stepSize(step, forward);
	}

	@Override
	protected boolean onNext(int step) {
		if (step == 1) {
			return isValidGameDirs();
		} else if (step == 2) {
			return isValidTargetZip();
		} else if (step == 3) {
			try {
				gamePack_.setTitle(title_.getText());
				gamePack_.setAuthor(author_.getText());
				gamePack_.setNotes(notes_.getText());
				gamePack_.setCapturesAvailable(exportCapturesButton_.getSelection());
				gamePack_.setMapperfilesAvailable(exportMapperfilesButton_.getSelection());
				gamePack_.setNativecommandsAvailable(exportNativeCommandsButton_.getSelection());
				gamePack_.setGamedataAvailable(exportGameData_.getSelection());
				job_ = new ExportThread(log_, progressBar_, status_, gamePack_, canonicalizer_.canonicalize(new File(filename_.getText())));
			} catch (Exception ex) {
				Mess_.on(shell_).exception(ex).warning();
				job_ = null;
				return false;
			}
		} else if (step == 4) {
			if (job_.isEverythingOk()) {
				Mess_.on(shell_).key("dialog.export.notice.exportok").display();
			} else {
				Mess_.on(shell_).key("dialog.export.error.problem").warning();
			}
			setReviewStatus("dialog.export.reviewlog");
			status_.pack();
		}
		return true;
	}

	private boolean isValidTargetZip() {
		Mess_.Builder mess = Mess_.on(shell_);
		if (StringUtils.isBlank(title_.getText())) {
			mess.key("dialog.export.required.title").bind(title_);
		}
		String gpaFilename = filename_.getText();
		if (StringUtils.isBlank(gpaFilename)) {
			mess.key("dialog.export.required.filename").bind(filename_);
		} else {
			File target = canonicalizer_.canonicalize(new File(gpaFilename));
			if (FilesUtils.isExistingFile(target))
				mess.key("dialog.export.error.fileexists", new Object[] {target}).bind(filename_);
			else if (!FilesUtils.isExistingDirectory(target.getParentFile()))
				mess.key("dialog.export.error.exportdirmissing", new Object[] {target}).bind(filename_);
		}
		return mess.valid();
	}

	private boolean isValidGameDirs() {
		Mess_.Builder mess = Mess_.on(shell_);
		for (GamePackEntry entry: gamePack_.getEntries()) {
			if (!FilesUtils.isExistingDirectory(entry.getCanonicalGameDir())) {
				mess.key("dialog.export.error.gamedirmissing", new Object[] {entry.getCanonicalGameDir()}).bind(profilesTable_);
			}
			if (entry.getGameDir().isAbsolute()) {
				mess.key("dialog.export.error.gamedirnotrelative", new Object[] {entry.getGameDir()}).bind(profilesTable_);
			}
			for (GamePackEntry entry2: gamePack_.getEntries()) {
				if (entry != entry2 && FilesUtils.areRelated(entry.getCanonicalGameDir(), entry2.getCanonicalGameDir())) {
					mess.key("dialog.export.error.gamedirsconflict",
						new Object[] {entry.getCanonicalGameDir(), entry.getProfile().getTitle(), entry2.getCanonicalGameDir(), entry2.getProfile().getTitle()}).bind(profilesTable_);
				}
			}
		}
		return mess.valid();
	}

	@Override
	protected void onShellCreated() {
		Color regularValueColor = DarkTheme.forced() ? DarkTheme.inputBackground: Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND);
		Color suspiciousValueColor = DarkTheme.forced() ? DarkTheme.tableHighlightedBackground: Display.getDefault().getSystemColor(SWT.COLOR_YELLOW);

		Group group1 = Group_.on(shell_).layout(new GridLayout(2, false)).key("dialog.export.step1").build();

		SelectionListener gameDataAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				settings_.setBooleanValue("exportwizard", "gamedata", exportGameData_.getSelection());
			}
		};
		settingsOnly_ = Chain.on(group1).lbl(l -> l.key("dialog.export.export")).but(
			b -> b.radio().key("dialog.export.export.profiles").select(!settings_.getBooleanValue("exportwizard", "gamedata")).listen(gameDataAdapter)).button();
		exportGameData_ = Chain.on(group1).lbl(l -> l).but(
			b -> b.radio().key("dialog.export.export.games").select(settings_.getBooleanValue("exportwizard", "gamedata")).listen(gameDataAdapter)).button();
		Chain.on(group1).lbl(l -> l.horSpan(2));
		exportCapturesButton_ = Chain.on(group1).lbl(l -> l).but(b -> b.key("dialog.template.captures").select(true)).button();
		exportMapperfilesButton_ = Chain.on(group1).lbl(l -> l).but(b -> b.key("dialog.template.mapperfile")).button();
		exportNativeCommandsButton_ = Chain.on(group1).lbl(l -> l).but(b -> b.key("dialog.export.nativecommands")).button();
		addStep(group1);

		Group reviewDirsGroup = Group_.on(shell_).layout(new FillLayout()).key("dialog.export.step2").build();
		profilesTable_ = Table_.on(reviewDirsGroup).header().build();
		TableColumn titleColumn = new TableColumn(profilesTable_, SWT.NONE);
		titleColumn.setWidth(260);
		titleColumn.setText(text_.get("dialog.main.profiles.column.title"));
		TableColumn subdirColumn = new TableColumn(profilesTable_, SWT.NONE);
		subdirColumn.setWidth(120);
		subdirColumn.setText(text_.get("dialog.export.column.gamedir")); 
		for (GamePackEntry entry: gamePack_.getEntries()) {
			TableItem item = new TableItem(profilesTable_, SWT.NONE);
			item.setText(entry.getProfile().getTitle());
			item.setText(1, entry.getGameDir().getPath());
			item.setBackground(entry.getGameDir().getPath().contains(File.separator) 
				? suspiciousValueColor: regularValueColor);
		}
		profilesTable_.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent event) {
				int idx = profilesTable_.getSelectionIndex();
				GamePackEntry entry = gamePack_.getEntries().get(idx);
				DirectoryDialog dialog = new DirectoryDialog(shell_);
				dialog.setFilterPath(entry.getCanonicalGameDir().getPath());
				String result = dialog.open();
				if (result != null) {
					entry.setGameDir(result);
					profilesTable_.getSelection()[0].setText(1, entry.getGameDir().getPath());
					profilesTable_.getSelection()[0].setBackground(
						entry.getGameDir().getPath().contains(File.separator)
							? suspiciousValueColor: regularValueColor);
				}
			}
		});
		addStep(reviewDirsGroup);

		Group settingsGroup = Group_.on(shell_).layout(new GridLayout(3, false)).key("dialog.export.step3").build();
		title_ = Chain.on(settingsGroup).lbl(l -> l.key("dialog.export.exporttitle")).txt(t -> t.horSpan(2)).text();
		title_.addVerifyListener(e -> {
			String oldTitle = title_.getText();
			String newTitle = oldTitle.substring(0, e.start) + e.text + oldTitle.substring(e.end);
			String filename = filename_.getText();
			if (StringUtils.isBlank(filename) || filename.equals(FileLocationService.getGpaExportFile(filename, oldTitle)))
				filename_.setText(FileLocationService.getGpaExportFile(filename, newTitle));
		});
		author_ = Chain.on(settingsGroup).lbl(l -> l.key("dialog.export.author")).txt(t -> t.horSpan(2)).text();
		notes_ = Chain.on(settingsGroup).lbl(l -> l.key("dialog.export.notes")).txt(t -> t.multi().wrap().horSpan(2)).text();
		StringBuilder sb = new StringBuilder();
		for (GamePackEntry entry: gamePack_.getEntries()) {
			sb.append(entry.getProfile().getTitle()).append(notes_.getLineDelimiter());
		}
		notes_.setText(sb.toString());
		Chain chn = Chain.on(settingsGroup).lbl(l -> l.key("dialog.export.file")).txt(t -> t).but(b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.DBGLZIP, true)).build();
		filename_ = chn.getText();
		canonicalizer_ = (ICanonicalize)chn.getButton().getData(Button_.DATA_CANONICALIZER);
		addStep(settingsGroup);

		addFinalStep("dialog.export.step4", "dialog.export.start");
	}
}