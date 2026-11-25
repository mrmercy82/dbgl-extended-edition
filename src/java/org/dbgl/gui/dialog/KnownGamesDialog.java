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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.util.OwnKnownGames;
import org.dbgl.util.OwnKnownGames.Entry;
import org.dbgl.util.OwnKnownGames.KnownFile;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


/**
 * Alpha 53 (TEST26): the games DBGL has learned from profiles (config/known-games.json), to look at, change and forget: the
 * title and details, and the start or setup program chosen anew (its name and checksum are then taken from the file chosen).
 * Each change is saved at once. The result: whether anything changed.
 */
public class KnownGamesDialog extends SizeControlledButtonDialog<Boolean> {

	private final File file_;
	private List<Entry> entries_;
	private final List<Entry> shown_ = new ArrayList<>();
	private Table table_;
	private Text filter_;
	private Label info_;
	private boolean changed_;

	public KnownGamesDialog(Shell parent) {
		super(parent, "knowngamesdialog");
		file_ = OwnKnownGames.file();
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.knowngames.title");
	}

	@Override
	protected boolean prepare() {
		entries_ = new ArrayList<>(OwnKnownGames.load(file_));
		return true;
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(4, false));
		info_ = new Label(contents_, SWT.WRAP);
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 4, 1);
		infoData.widthHint = 760;
		info_.setLayoutData(infoData);

		new Label(contents_, SWT.NONE).setText(text_.get("dialog.knowngames.filter"));
		filter_ = new Text(contents_, SWT.BORDER | SWT.SEARCH);
		filter_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		filter_.addListener(SWT.Modify, e -> fill());

		table_ = new Table(contents_, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
		table_.setHeaderVisible(true);
		table_.setLinesVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1);
		tableData.heightHint = 360;
		table_.setLayoutData(tableData);
		String[] heads = {text_.get("dialog.complete.column.title"), text_.get("dialog.knowngames.column.developer"), text_.get("dialog.importdir.column.publisher"),
				text_.get("dialog.importdir.column.year"), text_.get("dialog.knowngames.column.genre"), text_.get("dialog.importdir.column.main"),
				text_.get("dialog.importdir.column.setup")};
		int[] widths = {220, 120, 120, 50, 90, 120, 110};
		for (int i = 0; i < heads.length; i++) {
			TableColumn c = new TableColumn(table_, SWT.NONE);
			c.setText(heads[i]);
			c.setWidth(widths[i]);
		}
		table_.addListener(SWT.DefaultSelection, e -> edit());

		Button edit = new Button(contents_, SWT.PUSH);
		edit.setText(text_.get("dialog.knowngames.edit"));
		edit.addListener(SWT.Selection, e -> edit());
		Button forget = new Button(contents_, SWT.PUSH);
		forget.setText(text_.get("dialog.knowngames.forget"));
		forget.addListener(SWT.Selection, e -> forget());
		Label where = new Label(contents_, SWT.NONE);
		where.setText(file_.getPath());
		where.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));

		createOkButton(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				result_ = changed_;
				shell_.close();
			}
		});
		okButton_.setText(text_.get("dialog.statuswindow.close"));
		fill();
	}

	private void fill() {
		String f = filter_.getText().strip().toLowerCase(Locale.ROOT);
		shown_.clear();
		table_.removeAll();
		for (Entry e: entries_) {
			String all = (e.title() + " " + e.developer() + " " + e.publisher() + " " + e.year() + " " + e.genre() + " " + name(e.main()) + " " + name(e.setupFile())).toLowerCase(Locale.ROOT);
			if (!f.isEmpty() && !all.contains(f))
				continue;
			shown_.add(e);
			TableItem item = new TableItem(table_, SWT.NONE);
			item.setText(new String[] {e.title(), e.developer(), e.publisher(), e.year(), e.genre(), name(e.main()), name(e.setupFile())});
		}
		info_.setText(text_.get("dialog.knowngames.info", new Object[] {entries_.size(), shown_.size()}));
	}

	private static String name(KnownFile f) {
		return f == null ? "": f.name();
	}

	private void forget() {
		int[] sel = table_.getSelectionIndices();
		if (sel.length == 0)
			return;
		if (!Mess_.on(shell_).key("dialog.knowngames.confirmforget", new Object[] {sel.length}).confirm())
			return;
		List<Entry> gone = new ArrayList<>();
		for (int i: sel)
			gone.add(shown_.get(i));
		entries_.removeAll(gone);
		save();
	}

	private void edit() {
		int i = table_.getSelectionIndex();
		if (i < 0)
			return;
		Entry before = shown_.get(i);
		Entry after = new EditEntry(shell_, before).open();
		if ((after == null) || after.equals(before))
			return;
		entries_.set(entries_.indexOf(before), after);
		save();
	}

	private void save() {
		try {
			OwnKnownGames.save(file_, entries_);
			changed_ = true;
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
		fill();
	}

	/** a learned game changed: its details, and its start and setup program chosen anew (name and checksum from the file) */
	static final class EditEntry {
		private final Shell parent_;
		private final Entry entry_;
		private Entry result_;
		private KnownFile main_, setup_;

		EditEntry(Shell parent, Entry entry) {
			parent_ = parent;
			entry_ = entry;
			main_ = entry.main();
			setup_ = entry.setupFile();
		}

		Entry open() {
			var text = org.dbgl.service.TextService.getInstance();
			Shell dlg = new Shell(parent_, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
			dlg.setText(text.get("dialog.knowngames.edit.title"));
			BaseDialog.inheritWindowImage(dlg, parent_);
			dlg.setLayout(new GridLayout(3, false));
			Text title = field(dlg, text.get("dialog.complete.column.title"), entry_.title());
			Text developer = field(dlg, text.get("dialog.knowngames.column.developer"), entry_.developer());
			Text publisher = field(dlg, text.get("dialog.importdir.column.publisher"), entry_.publisher());
			Text year = field(dlg, text.get("dialog.importdir.column.year"), entry_.year());
			Text genre = field(dlg, text.get("dialog.knowngames.column.genre"), entry_.genre());
			Label mainLabel = fileRow(dlg, text.get("dialog.importdir.column.main"), main_);
			Button chooseMain = new Button(dlg, SWT.PUSH);
			chooseMain.setText(text.get("dialog.knowngames.choose"));
			chooseMain.setToolTipText(text.get("dialog.knowngames.choose.tooltip"));
			Label setupLabel = fileRow(dlg, text.get("dialog.importdir.column.setup"), setup_);
			Button chooseSetup = new Button(dlg, SWT.PUSH);
			chooseSetup.setText(text.get("dialog.knowngames.choose"));
			chooseSetup.setToolTipText(text.get("dialog.knowngames.choose.tooltip"));
			new Label(dlg, SWT.NONE);
			Button noSetup = new Button(dlg, SWT.PUSH);
			noSetup.setText(text.get("dialog.knowngames.nosetup"));
			noSetup.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
			Label problem = new Label(dlg, SWT.WRAP);
			problem.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
			chooseMain.addListener(SWT.Selection, e -> {
				KnownFile k = choose(dlg, false, problem);
				if (k != null) {
					main_ = k;
					mainLabel.setText(describe(k));
				}
			});
			chooseSetup.addListener(SWT.Selection, e -> {
				KnownFile k = choose(dlg, true, problem);
				if (k != null) {
					setup_ = k;
					setupLabel.setText(describe(k));
				}
			});
			noSetup.addListener(SWT.Selection, e -> {
				setup_ = null;
				setupLabel.setText("-");
			});
			org.eclipse.swt.widgets.Composite buttons = new org.eclipse.swt.widgets.Composite(dlg, SWT.NONE);
			buttons.setLayout(new GridLayout(2, true));
			buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 3, 1));
			Button ok = new Button(buttons, SWT.PUSH);
			ok.setText(text.get("button.ok"));
			ok.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			Button cancel = new Button(buttons, SWT.PUSH);
			cancel.setText(text.get("button.cancel"));
			cancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			ok.addListener(SWT.Selection, e -> {
				if (title.getText().isBlank()) {
					problem.setText(text.get("dialog.knowngames.notitle"));
					dlg.layout(true, true);
					return;
				}
				List<KnownFile> files = new ArrayList<>();
				files.add(main_);
				if (setup_ != null)
					files.add(setup_);
				result_ = new Entry(title.getText().strip(), developer.getText().strip(), publisher.getText().strip(), year.getText().strip(), genre.getText().strip(), files);
				dlg.close();
			});
			cancel.addListener(SWT.Selection, e -> dlg.close());
			dlg.setDefaultButton(ok);
			org.dbgl.gui.controls.DarkTheme.apply(dlg);
			dlg.pack();
			dlg.setSize(Math.max(560, dlg.getSize().x), dlg.getSize().y);
			BaseDialog.centerOn(dlg, parent_);
			dlg.open();
			while (!dlg.isDisposed())
				if (!dlg.getDisplay().readAndDispatch())
					dlg.getDisplay().sleep();
			return result_;
		}

		private static Text field(Shell dlg, String label, String value) {
			new Label(dlg, SWT.NONE).setText(label);
			Text t = new Text(dlg, SWT.BORDER);
			t.setText(StringUtils.defaultString(value));
			t.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
			return t;
		}

		private static Label fileRow(Shell dlg, String label, KnownFile f) {
			new Label(dlg, SWT.NONE).setText(label);
			Label l = new Label(dlg, SWT.NONE);
			l.setText(f == null ? "-": describe(f));
			l.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			return l;
		}

		static String describe(KnownFile f) {
			return f.name() + "  (MD5 " + f.md5() + ")";
		}

		/** a program chosen on this computer: its name and checksum (the path is not kept) */
		private static KnownFile choose(Shell dlg, boolean setup, Label problem) {
			FileDialog fd = new FileDialog(dlg, SWT.OPEN);
			fd.setFilterExtensions(new String[] {"*.exe;*.com;*.bat;*.EXE;*.COM;*.BAT", "*.*"});
			String path = fd.open();
			if (path == null)
				return null;
			File f = new File(path);
			try {
				return new KnownFile(f.getName(), OwnKnownGames.md5(f), setup);
			} catch (IOException | java.security.NoSuchAlgorithmException e) {
				problem.setText(e.toString());
				return null;
			}
		}
	}
}
