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
package org.dbgl.gui.controls;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import org.apache.commons.io.FileUtils;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.dbgl.util.SavegameBackup;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


/**
 * Alpha 48 (wish 11): the page "Savegames" of the profile editor: switch on the backups before each start, which files (with a
 * suggestion), how many to keep; the backups with date, files and size, to make one now, restore, delete or show the folder. The
 * settings are kept right away (in the folder of the backups), like the backups themselves.
 */
public final class SavegamesPanel {

	private SavegamesPanel() {
	}

	/** @param profile the profile as edited (its mounts as they are now), or null when it does not exist yet */
	public static void create(Composite parent, Profile profile) {
		ITextService text = TextService.getInstance();
		Group group = new Group(parent, SWT.NONE);
		group.setText(text.get("dialog.savegames.group"));
		group.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		group.setLayout(new GridLayout(3, false));
		if (profile == null) {
			Label none = new Label(group, SWT.WRAP);
			none.setText(text.get("dialog.savegames.notyet"));
			GridData d = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
			d.widthHint = 500;
			none.setLayoutData(d);
			return;
		}
		SavegameBackup.Config config = SavegameBackup.load(profile);
		File gameDir = SavegameBackup.gameDir(profile);

		Label intro = new Label(group, SWT.WRAP);
		intro.setText(text.get("dialog.savegames.info"));
		GridData introData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		introData.widthHint = 560;
		intro.setLayoutData(introData);

		new Label(group, SWT.NONE).setText(text.get("dialog.savegames.enabled"));
		Button enabled = new Button(group, SWT.CHECK);
		enabled.setSelection(config.enabled());
		enabled.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));

		new Label(group, SWT.NONE).setText(text.get("dialog.savegames.folder"));
		Label folder = new Label(group, SWT.WRAP);
		folder.setText(gameDir == null ? text.get("dialog.savegames.nogamedir"): gameDir.getPath());
		GridData folderData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		folderData.widthHint = 420;
		folder.setLayoutData(folderData);

		Label patternsLabel = new Label(group, SWT.NONE);
		patternsLabel.setText(text.get("dialog.savegames.patterns"));
		patternsLabel.setToolTipText(text.get("dialog.savegames.patterns.tooltip"));
		Text patterns = new Text(group, SWT.BORDER);
		patterns.setText(config.patterns());
		patterns.setToolTipText(text.get("dialog.savegames.patterns.tooltip"));
		GridData patternsData = new GridData(SWT.FILL, SWT.CENTER, true, false); // alpha 48: as long as the table below
		patternsData.widthHint = 360;
		patterns.setLayoutData(patternsData);
		Button suggest = new Button(group, SWT.PUSH);
		suggest.setText(text.get("dialog.savegames.suggest"));
		suggest.setToolTipText(text.get("dialog.savegames.suggest.tooltip"));
		suggest.setEnabled(gameDir != null);

		new Label(group, SWT.NONE);
		Label matches = new Label(group, SWT.NONE);
		matches.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		new Label(group, SWT.NONE).setText(text.get("dialog.savegames.keep"));
		Spinner keep = new Spinner(group, SWT.BORDER);
		keep.setMinimum(1);
		keep.setMaximum(100);
		keep.setSelection(config.keep());
		new Label(group, SWT.NONE).setText(text.get("dialog.savegames.keep.unit"));

		Label listLabel = new Label(group, SWT.NONE);
		listLabel.setText(text.get("dialog.savegames.backups"));
		listLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		Table table = new Table(group, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);
		table.setHeaderVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, false);
		tableData.heightHint = table.getItemHeight() * 6;
		tableData.widthHint = 360;
		table.setLayoutData(tableData);
		for (String key: new String[] {"dialog.template.savestates.date", "dialog.savegames.files", "dialog.template.savestates.size"}) {
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText(text.get(key));
		}
		Composite buttons = new Composite(group, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, false));
		GridLayout buttonsLayout = new GridLayout(1, true);
		buttonsLayout.marginWidth = buttonsLayout.marginHeight = 0;
		buttons.setLayout(buttonsLayout);
		Button now = button(buttons, text.get("dialog.savegames.now"));
		Button restore = button(buttons, text.get("dialog.savegames.restore"));
		Button delete = button(buttons, text.get("dialog.template.savestates.delete"));
		Button open = button(buttons, text.get("dialog.template.savestates.open"));

		Runnable store = () -> {
			try {
				SavegameBackup.save(profile, new SavegameBackup.Config(enabled.getSelection(), patterns.getText().trim(), keep.getSelection()));
			} catch (IOException e) {
				Mess_.on(group.getShell()).exception(e).warning();
			}
		};
		Runnable count = () -> {
			try {
				int n = SavegameBackup.matching(gameDir, patterns.getText()).size();
				matches.setText(patterns.getText().isBlank() ? "": text.get("dialog.savegames.matches", new Object[] {n}));
			} catch (IOException | RuntimeException e) {
				matches.setText("");
			}
		};
		Runnable fill = () -> {
			table.removeAll();
			DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
			for (File f: SavegameBackup.backups(profile)) {
				TableItem item = new TableItem(table, SWT.NONE);
				String date = format.format(new Date(f.lastModified())) + (f.getName().contains("before-restore") ? " (" + text.get("dialog.savegames.beforerestore") + ")": "");
				item.setText(new String[] {date, String.valueOf(SavegameBackup.count(f)), FileUtils.byteCountToDisplaySize(f.length())});
				item.setData(f);
			}
			if (table.getItemCount() == 0)
				new TableItem(table, SWT.NONE).setText(text.get("dialog.savegames.none"));
			for (TableColumn c: table.getColumns())
				c.pack();
			restore.setEnabled(false);
			delete.setEnabled(false);
		};
		enabled.addListener(SWT.Selection, e -> store.run());
		keep.addListener(SWT.Modify, e -> store.run());
		patterns.addListener(SWT.FocusOut, e -> store.run());
		patterns.addListener(SWT.Modify, e -> count.run());
		suggest.addListener(SWT.Selection, e -> {
			try {
				String s = SavegameBackup.suggest(gameDir);
				if (s.isEmpty())
					Mess_.on(group.getShell()).key("dialog.savegames.nosuggestion").display();
				else
					patterns.setText(s);
				store.run();
			} catch (IOException ex) {
				Mess_.on(group.getShell()).exception(ex).warning();
			}
		});
		table.addListener(SWT.Selection, e -> {
			boolean one = table.getSelectionCount() == 1 && table.getSelection()[0].getData() != null;
			restore.setEnabled(one);
			delete.setEnabled(one);
		});
		now.addListener(SWT.Selection, e -> {
			store.run();
			try {
				File made = SavegameBackup.backup(profile, null);
				if (made == null)
					Mess_.on(group.getShell()).key("dialog.savegames.nothing").display();
			} catch (IOException ex) {
				Mess_.on(group.getShell()).exception(ex).warning();
			}
			fill.run();
		});
		restore.addListener(SWT.Selection, e -> {
			File f = (File)table.getSelection()[0].getData();
			if (!Mess_.on(group.getShell()).key("dialog.savegames.confirmrestore", new Object[] {table.getSelection()[0].getText(0)}).confirm())
				return;
			try {
				int n = SavegameBackup.restore(profile, f);
				Mess_.on(group.getShell()).key("dialog.savegames.restored", new Object[] {n}).display();
			} catch (IOException ex) {
				Mess_.on(group.getShell()).exception(ex).warning();
			}
			fill.run();
		});
		delete.addListener(SWT.Selection, e -> {
			File f = (File)table.getSelection()[0].getData();
			if (!Mess_.on(group.getShell()).key("dialog.template.savestates.confirmdelete", new Object[] {1}).confirm())
				return;
			FileUtils.deleteQuietly(f);
			fill.run();
		});
		open.addListener(SWT.Selection, e -> {
			File d = SavegameBackup.dir(profile);
			d.mkdirs();
			SystemUtils.openDirForViewing(d, group.getShell());
		});
		count.run();
		fill.run();
	}

	private static Button button(Composite parent, String label) {
		Button b = new Button(parent, SWT.PUSH);
		b.setText(label);
		b.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		return b;
	}
}
