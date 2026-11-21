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
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.dbgl.util.SavegameBackup;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * alpha 52: before a game with backups of its saved games, which state is in the game folder now (● the backup with the same
 * files) and the chance to start with another one: it is restored first (the state now goes into a backup before). Can be
 * switched off for the profile (page Savegames, or here); not in the TV mode.
 */
public final class SavegameStart {

	/** what was chosen: start (with the backup to restore first, or null), or not start at all */
	public record Choice(boolean start, File restore) {
	}

	private SavegameStart() {
	}

	/** @return null when the window is not wanted (switched off, no backups, the TV mode): start as always */
	public static Choice ask(Shell parent, Profile p) {
		if ((parent == null) || org.dbgl.gui.tv.TvMode.isShown() || !SavegameBackup.showAtStart(p))
			return null;
		List<File> backups = SavegameBackup.backups(p);
		if (backups.isEmpty())
			return null;
		File current = SavegameBackup.current(p);
		ITextService text = TextService.getInstance();
		Display display = parent.getDisplay();
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		dlg.setText(text.get("dialog.savegamestart.title", new Object[] {p.getTitle()}));
		BaseDialog.inheritWindowImage(dlg, parent);
		dlg.setLayout(new GridLayout(1, false));
		if (DarkTheme.forced())
			dlg.setBackground(DarkTheme.dialogBackground);
		Label info = new Label(dlg, SWT.WRAP);
		String currentText = current == null ? text.get("dialog.savegamestart.nocurrent")
				: text.get("dialog.savegamestart.current", new Object[] {describe(p, current, text)});
		info.setText(currentText);
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		infoData.widthHint = 460;
		info.setLayoutData(infoData);
		Table table = new Table(dlg, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);
		table.setHeaderVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = table.getHeaderHeight() + table.getItemHeight() * Math.min(10, backups.size() + 1);
		table.setLayoutData(tableData);
		for (String key: new String[] {null, "dialog.template.savestates.date", "dialog.savegames.name"})
			new TableColumn(table, SWT.NONE).setText(key == null ? "": text.get(key));
		DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale());
		for (File f: backups) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(new String[] {f.equals(current) ? "●": "", format.format(new Date(f.lastModified())), SavegameBackup.nameOf(p, f)});
			item.setData(f);
			if (f.equals(current))
				table.setSelection(item);
		}
		for (TableColumn c: table.getColumns())
			c.pack();
		Button showAgain = new Button(dlg, SWT.CHECK);
		showAgain.setText(text.get("dialog.savegamestart.show"));
		showAgain.setSelection(true);
		showAgain.setToolTipText(text.get("dialog.savegames.showatstart.tooltip")); // switching it off is risky: said there
		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
		row.setLayout(new GridLayout(3, false));
		Button start = new Button(row, SWT.PUSH);
		start.setText(text.get("dialog.savegamestart.start"));
		Button withChosen = new Button(row, SWT.PUSH);
		withChosen.setText(text.get("dialog.savegamestart.withchosen"));
		withChosen.setToolTipText(text.get("dialog.savegamestart.withchosen.tooltip"));
		Button cancel = new Button(row, SWT.PUSH);
		cancel.setText(text.get("button.cancel"));
		Runnable enable = () -> {
			File chosen = table.getSelectionCount() == 1 ? (File)table.getSelection()[0].getData(): null;
			withChosen.setEnabled((chosen != null) && !chosen.equals(current));
		};
		table.addListener(SWT.Selection, e -> enable.run());
		enable.run();
		Choice[] result = {new Choice(false, null)};
		start.addListener(SWT.Selection, e -> {
			result[0] = new Choice(true, null);
			dlg.close();
		});
		withChosen.addListener(SWT.Selection, e -> {
			result[0] = new Choice(true, (File)table.getSelection()[0].getData());
			dlg.close();
		});
		cancel.addListener(SWT.Selection, e -> dlg.close());
		dlg.setDefaultButton(start);
		dlg.addListener(SWT.Dispose, e -> {
			if (!showAgain.isDisposed() && !showAgain.getSelection()) {
				try {
					SavegameBackup.setShowAtStart(p, false);
				} catch (java.io.IOException ex) {
					// asked again next time
				}
			}
		});
		DarkTheme.apply(dlg);
		dlg.pack();
		Rectangle area = parent.getMonitor().getClientArea(), pb = parent.getBounds();
		Point size = dlg.getSize();
		dlg.setLocation(Math.max(area.x, pb.x + (pb.width - size.x) / 2), Math.max(area.y, pb.y + (pb.height - size.y) / 2));
		start.setFocus();
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		return result[0];
	}

	private static java.util.Locale locale() {
		org.dbgl.service.SettingsService s = org.dbgl.service.SettingsService.getInstance();
		return java.util.Locale.of(s.getValue("locale", "language"), s.getValue("locale", "country"));
	}

	private static String describe(Profile p, File zip, ITextService text) {
		String name = SavegameBackup.nameOf(p, zip);
		String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale()).format(new Date(zip.lastModified()));
		return name.isBlank() ? date: name + " (" + date + ")";
	}
}
