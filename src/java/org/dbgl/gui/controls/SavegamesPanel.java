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
		SavegameBackup.moveToNewPlace(profile); // alpha 52: those of the earlier place (next to the screenshots) copied first
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

		// alpha 52: before each start the state of the saved games, with the chance to start with another backup
		Label showAtStartLabel = new Label(group, SWT.NONE);
		showAtStartLabel.setText(text.get("dialog.savegames.showatstart"));
		showAtStartLabel.setToolTipText(text.get("dialog.savegames.showatstart.tooltip"));
		Button showAtStart = new Button(group, SWT.CHECK);
		showAtStart.setSelection(SavegameBackup.showAtStart(profile));
		showAtStart.setToolTipText(text.get("dialog.savegames.showatstart.tooltip"));
		showAtStart.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		showAtStart.addListener(SWT.Selection, e -> {
			try {
				SavegameBackup.setShowAtStart(profile, showAtStart.getSelection());
			} catch (IOException ex) {
				Mess_.on(group.getShell()).exception(ex).warning();
			}
		});

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
		patterns.setData(GridCompactor.KEEP_WIDTH, Boolean.TRUE); // alpha 52: not shortened to the width of the other fields of the page
		Button suggest = new Button(group, SWT.PUSH);
		suggest.setText(text.get("dialog.savegames.suggest"));
		suggest.setToolTipText(text.get("dialog.savegames.suggest.tooltip"));
		suggest.setEnabled(gameDir != null);

		new Label(group, SWT.NONE);
		Label matches = new Label(group, SWT.NONE);
		matches.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		// alpha 52: counted again by itself when the patterns change; this is for files changed in the folder (e.g. after playing)
		Button refresh = new Button(group, SWT.PUSH);
		refresh.setText(text.get("dialog.savegames.refresh"));
		refresh.setToolTipText(text.get("dialog.savegames.refresh.tooltip"));
		refresh.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		refresh.setEnabled(gameDir != null);

		new Label(group, SWT.NONE).setText(text.get("dialog.savegames.keep"));
		// alpha 52: the number and its unit side by side (the field of the files above is as wide as the table now)
		Composite keepRow = new Composite(group, SWT.NONE);
		GridLayout keepLayout = new GridLayout(2, false);
		keepLayout.marginWidth = keepLayout.marginHeight = 0;
		keepRow.setLayout(keepLayout);
		keepRow.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		keepRow.setData(GridCompactor.KEEP_WIDTH, Boolean.TRUE);
		Spinner keep = new Spinner(keepRow, SWT.BORDER);
		keep.setMinimum(1);
		keep.setMaximum(100);
		keep.setSelection(config.keep());
		keep.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false)); // with layout data: else the page hides the row as empty
		Label keepUnit = new Label(keepRow, SWT.NONE);
		keepUnit.setText(text.get("dialog.savegames.keep.unit"));
		keepUnit.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		new Label(group, SWT.NONE); // the third column of the row

		Label listLabel = new Label(group, SWT.NONE);
		listLabel.setText(text.get("dialog.savegames.backups"));
		listLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		Table table = new Table(group, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);
		table.setHeaderVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, false);
		tableData.heightHint = table.getItemHeight() * 6;
		tableData.widthHint = 360;
		table.setLayoutData(tableData);
		// alpha 52: a mark for the backup with the state of the game folder now, a name, whether it is kept
		// alpha 52: the mark and "kept" centred, the numbers to the right; an empty first column of no width, as Windows always puts
		// the first one to the left
		String[] keys = {"", null, "dialog.template.savestates.date", "dialog.savegames.name", "dialog.savegames.files", "dialog.template.savestates.size", "dialog.savegames.kept"};
		int[] align = {SWT.LEFT, SWT.CENTER, SWT.LEFT, SWT.LEFT, SWT.RIGHT, SWT.RIGHT, SWT.CENTER};
		for (int i = 0; i < keys.length; i++) {
			TableColumn column = new TableColumn(table, align[i]);
			column.setText((keys[i] == null) || keys[i].isEmpty() ? "": text.get(keys[i]));
		}
		table.getColumn(0).setResizable(false);
		tableData.widthHint = 460;
		Composite buttons = new Composite(group, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, false));
		GridLayout buttonsLayout = new GridLayout(1, true);
		buttonsLayout.marginWidth = buttonsLayout.marginHeight = 0;
		buttons.setLayout(buttonsLayout);
		Button now = button(buttons, text.get("dialog.savegames.now"));
		Button restore = button(buttons, text.get("dialog.savegames.restore"));
		Button rename = button(buttons, text.get("dialog.savegames.rename"));
		rename.setToolTipText(text.get("dialog.savegames.rename.tooltip"));
		Button keepThis = button(buttons, text.get("dialog.savegames.keepthis"));
		keepThis.setToolTipText(text.get("dialog.savegames.keepthis.tooltip"));
		Button delete = button(buttons, text.get("dialog.savegames.delete"));
		Button open = button(buttons, text.get("dialog.template.savestates.open"));

		new Label(group, SWT.NONE);
		Label legend = new Label(group, SWT.WRAP); // alpha 52: what the mark and the kept ones mean
		legend.setText(text.get("dialog.savegames.legend"));
		GridData legendData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		legendData.widthHint = 460;
		legend.setLayoutData(legendData);

		Runnable store = () -> {
			try {
				SavegameBackup.save(profile, new SavegameBackup.Config(enabled.getSelection(), patterns.getText().trim(), keep.getSelection()));
			} catch (IOException e) {
				Mess_.on(group.getShell()).exception(e).warning();
			}
		};
		// alpha 52: counted in the background (a large folder does not stop the window), the size of the files with it
		long[] matched = {0, 0}; // files, bytes
		int[] generation = {0};
		Runnable count = () -> {
			int mine = ++generation[0];
			String pattern = patterns.getText();
			if (pattern.isBlank() || (gameDir == null)) {
				matched[0] = matched[1] = 0;
				matches.setText("");
				return;
			}
			matches.setText(text.get("dialog.savegames.counting"));
			Thread t = new Thread(() -> {
				long[] result = {0, 0};
				try {
					java.util.Map<String, File> files = SavegameBackup.matching(gameDir, pattern);
					result[0] = files.size();
					result[1] = SavegameBackup.size(files);
				} catch (IOException | RuntimeException e) {
					// nothing found then
				}
				if (!group.isDisposed())
					group.getDisplay().asyncExec(() -> {
						if (matches.isDisposed() || (mine != generation[0]))
							return; // the patterns were changed again meanwhile
						matched[0] = result[0];
						matched[1] = result[1];
						matches.setText(text.get("dialog.savegames.matches2", new Object[] {result[0], org.dbgl.util.DiskUsage.format(result[1], locale())}));
						matches.getParent().layout(new org.eclipse.swt.widgets.Control[] {matches});
					});
			}, "savegame count");
			t.setDaemon(true);
			t.start();
		};
		int[] pending = {0};
		Runnable countSoon = () -> { // while typing: once it stops for a moment
			int mine = ++pending[0];
			group.getDisplay().timerExec(400, () -> {
				if ((mine == pending[0]) && !group.isDisposed())
					count.run();
			});
		};
		// alpha 52: from LARGE on, asked first; allowed then for this profile, also before each start
		java.util.function.BooleanSupplier largeOk = () -> {
			if ((matched[1] < SavegameBackup.LARGE) || SavegameBackup.largeAllowed(profile))
				return true;
			if (!Mess_.on(group.getShell()).key("dialog.savegames.large", new Object[] {org.dbgl.util.DiskUsage.format(matched[1], locale()),
				org.dbgl.util.DiskUsage.format(SavegameBackup.LARGE, locale())}).confirm())
				return false;
			try {
				SavegameBackup.setLargeAllowed(profile, true);
			} catch (IOException e) {
				Mess_.on(group.getShell()).exception(e).warning();
			}
			return true;
		};
		int[] fillGeneration = {0};
		Runnable fill = () -> {
			// alpha 52: read away from the windows (the game folder is compared, every ZIP file opened): "loading ..." meanwhile
			int mine = ++fillGeneration[0];
			table.removeAll();
			new TableItem(table, SWT.NONE).setText(2, text.get("dialog.savegames.loading"));
			packColumns(table);
			restore.setEnabled(false);
			delete.setEnabled(false);
			rename.setEnabled(false);
			keepThis.setEnabled(false);
			Thread t = new Thread(() -> {
				java.util.List<SavegameBackup.Listed> rows;
				try {
					rows = SavegameBackup.list(profile);
				} catch (RuntimeException ex) {
					rows = java.util.List.of();
				}
				java.util.List<SavegameBackup.Listed> done = rows;
				if (!table.isDisposed())
					table.getDisplay().asyncExec(() -> {
						if (table.isDisposed() || (mine != fillGeneration[0]))
							return; // a newer one is on its way
						table.removeAll();
						DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale()); // in the language of DBGL
						boolean anyCurrent = false;
						for (SavegameBackup.Listed r: done) {
							File f = r.zip();
							TableItem item = new TableItem(table, SWT.NONE);
							String date = format.format(new Date(f.lastModified())) + (f.getName().contains("before-restore") ? " (" + text.get("dialog.savegames.beforerestore") + ")": "")
								+ (r.secondPlaceOnly() ? " (" + text.get("dialog.savegames.secondplace") + ")": "");
							item.setText(new String[] {"", r.current() ? "\u25CF": "", date, r.name(), String.valueOf(r.files()), org.dbgl.util.DiskUsage.format(f.length(), locale()),
									r.kept() ? "\u2713": ""});
							item.setData(f);
							anyCurrent |= r.current();
						}
						if (table.getItemCount() == 0)
							new TableItem(table, SWT.NONE).setText(2, text.get("dialog.savegames.none"));
						packColumns(table);
						table.setToolTipText(anyCurrent ? text.get("dialog.savegames.current"): null);
					});
			}, "savegame-list");
			t.setDaemon(true);
			t.start();
		};
		enabled.addListener(SWT.Selection, e -> {
			if (enabled.getSelection() && !largeOk.getAsBoolean()) // alpha 52: before each start, so asked here already
				enabled.setSelection(false);
			store.run();
		});
		keep.addListener(SWT.Modify, e -> store.run());
		patterns.addListener(SWT.FocusOut, e -> store.run());
		patterns.addListener(SWT.Modify, e -> countSoon.run());
		refresh.addListener(SWT.Selection, e -> {
			count.run();
			fill.run();
		});
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
			// a name only for those of DBGL (one only in the second place has none here)
			File parentOfChosen = one ? ((File)table.getSelection()[0].getData()).getParentFile(): null;
			rename.setEnabled(one && (parentOfChosen.equals(SavegameBackup.dir(profile)) || parentOfChosen.equals(SavegameBackup.oldDir(profile))));
			keepThis.setEnabled(one);
			if (one)
				keepThis.setText(text.get(SavegameBackup.isKept(profile, (File)table.getSelection()[0].getData()) ? "dialog.savegames.unkeep": "dialog.savegames.keepthis"));
		});
		Runnable doRename = () -> {
			if ((table.getSelectionCount() != 1) || (table.getSelection()[0].getData() == null) || !rename.isEnabled())
				return;
			File f = (File)table.getSelection()[0].getData();
			String name = new org.dbgl.gui.dialog.TextInputDialog(group.getShell(), text.get("dialog.savegames.renametitle"), text.get("dialog.savegames.name"), SavegameBackup.nameOf(profile, f)).open();
			if (name == null)
				return;
			try {
				SavegameBackup.setName(profile, f, name);
			} catch (IOException ex) {
				Mess_.on(group.getShell()).exception(ex).warning();
			}
			fill.run();
		};
		rename.addListener(SWT.Selection, e -> doRename.run());
		table.addListener(SWT.DefaultSelection, e -> doRename.run()); // a double click on a line
		keepThis.addListener(SWT.Selection, e -> {
			File f = (File)table.getSelection()[0].getData();
			try {
				SavegameBackup.setKept(profile, f, !SavegameBackup.isKept(profile, f));
			} catch (IOException ex) {
				Mess_.on(group.getShell()).exception(ex).warning();
			}
			fill.run();
		});
		now.addListener(SWT.Selection, e -> {
			store.run();
			if (!largeOk.getAsBoolean())
				return;
			// alpha 52: with a progress bar (375 MB took several seconds without a sign)
			java.util.concurrent.atomic.AtomicInteger progress = new java.util.concurrent.atomic.AtomicInteger();
			try {
				File made = BusyTask.run(group.getShell(), text.get("dialog.savegames.saving"), progress, (int)Math.max(1, matched[0]),
					() -> SavegameBackup.backup(profile, null, n -> progress.addAndGet(n)));
				if (made == null)
					Mess_.on(group.getShell()).key("dialog.savegames.nothing").display();
			} catch (BusyTask.Cancelled ex) {
				// stopped: the part file is removed by the backup
			} catch (Exception ex) {
				Mess_.on(group.getShell()).exception(ex).warning();
			}
			fill.run();
		});
		restore.addListener(SWT.Selection, e -> {
			File f = (File)table.getSelection()[0].getData();
			if (!Mess_.on(group.getShell()).key("dialog.savegames.confirmrestore", new Object[] {table.getSelection()[0].getText(2)}).confirm())
				return;
			try {
				int n = BusyTask.runToEnd(group.getShell(), text.get("dialog.savegames.restoring"), () -> SavegameBackup.restore(profile, f)); // alpha 52
				Mess_.on(group.getShell()).key("dialog.savegames.restored", new Object[] {n}).display();
			} catch (Exception ex) {
				Mess_.on(group.getShell()).exception(ex).warning();
			}
			fill.run();
		});
		delete.addListener(SWT.Selection, e -> {
			File f = (File)table.getSelection()[0].getData();
			if (!Mess_.on(group.getShell()).key(org.dbgl.util.SecondBackupPlace.root() != null ? "dialog.savegames.confirmdelete.second": "dialog.savegames.confirmdelete",
				new Object[] {table.getSelection()[0].getText(2)}).confirm())
				return;
			SavegameBackup.delete(profile, f); // alpha 52: everywhere (both places of DBGL, the second place)
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

	private static java.util.Locale locale() {
		org.dbgl.service.SettingsService s = org.dbgl.service.SettingsService.getInstance();
		return java.util.Locale.of(s.getValue("locale", "language"), s.getValue("locale", "country"));
	}

	/** the columns as wide as their content, the empty first one of no width */
	private static void packColumns(Table table) {
		for (TableColumn c: table.getColumns())
			c.pack();
		table.getColumn(0).setWidth(0);
	}

	private static Button button(Composite parent, String label) {
		Button b = new Button(parent, SWT.PUSH);
		b.setText(label);
		b.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		return b;
	}
}
