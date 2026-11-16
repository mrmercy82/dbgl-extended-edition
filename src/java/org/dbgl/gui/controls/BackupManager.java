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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.dbgl.util.BackupCatalog;
import org.dbgl.util.BackupCatalog.Backup;
import org.dbgl.util.BackupCatalog.Kind;
import org.dbgl.util.DiskUsage;
import org.dbgl.util.SystemUtils;
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
 * Alpha 49: the window that lists the backups DBGL made (File > Manage backups): kind, content, version of DBGL, date and
 * size of each; a backup can be locked (then it is not deleted, neither here nor when DBGL tidies up), deleted or restored.
 */
public final class BackupManager {

	private BackupManager() {
	}

	public static String kindText(ITextService text, Kind kind) {
		return text.get("dialog.backups.kind." + kind.name().toLowerCase(Locale.ROOT).replace("_", ""));
	}

	public static String contentText(ITextService text, Kind kind) {
		String what = kind.restorable() ? "data": kind == Kind.UPDATE_PROGRAM ? "program": kind == Kind.FULL ? "full": kind == Kind.STICK_RETURN ? "stick": "installer";
		return text.get("dialog.backups.content." + what);
	}

	/** the cells of the row of a backup: lock, kind, content, version, date, size, file */
	public static String[] row(ITextService text, Backup b, Locale locale) {
		return new String[] {"", kindText(text, b.kind()), contentText(text, b.kind()),
				b.kind() == Kind.STICK_RETURN ? "": b.version().isEmpty() ? text.get("dialog.backups.unknownversion"): BackupCatalog.pretty(b.version()),
				DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(new Date(b.when())), DiskUsage.format(b.size(), locale), b.file().getName()};
	}

	/**
	 * shows the window
	 *
	 * @return the backup to restore (the main window does that and ends DBGL), null if none was chosen
	 */
	public static File open(Shell shell, Locale locale) {
		ITextService text = TextService.getInstance();
		Display display = shell.getDisplay();
		Shell dlg = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.MAX);
		dlg.setText(text.get("dialog.backups.title"));
		// a symbol of its own in the title, as in the menu
		org.eclipse.swt.graphics.Image windowImage = org.dbgl.service.ImageService.getResourceImage(display, org.dbgl.service.ImageService.IMG_BACKUPS);
		if (windowImage != null) {
			dlg.setImage(windowImage);
			dlg.addDisposeListener(e -> windowImage.dispose());
		} else {
			BaseDialog.inheritWindowImage(dlg, shell);
		}
		dlg.setLayout(new GridLayout(2, false));
		DarkTheme.window(dlg);

		Label info = new Label(dlg, SWT.WRAP);
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		infoData.widthHint = 900;
		info.setLayoutData(infoData);

		Table table = new Table(dlg, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION | SWT.MULTI);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = 380;
		tableData.widthHint = 1000;
		table.setLayoutData(tableData);
		table.setHeaderVisible(true);
		table.setLinesVisible(!DarkTheme.forced());
		if (DarkTheme.forced()) { // as the other tables (test 12: the row of the headings was light)
			table.setBackground(DarkTheme.inputBackground);
			table.setForeground(DarkTheme.defaultForeground);
			table.setHeaderBackground(DarkTheme.tableHeaderBackground);
			table.setHeaderForeground(DarkTheme.tableHeaderForeground);
		}
		org.eclipse.swt.graphics.Image lockImage = org.dbgl.service.ImageService.getResourceImage(display, org.dbgl.service.ImageService.IMG_LOCK);
		dlg.addDisposeListener(e -> {
			if (lockImage != null)
				lockImage.dispose();
		});
		String[] heads = {"lock", "kind", "content", "version", "date", "size", "file"};
		// the widths and the order of the columns as the user left them
		org.dbgl.service.SettingsService settings = org.dbgl.service.SettingsService.getInstance();
		int[] widths = stored(ints(settings, "backupscolumnwidths"), new int[] {70, 180, 310, 120, 130, 80, 400}, false);
		int[] sort = {4, SWT.DOWN}; // column, direction: the newest first
		Runnable[] refill = new Runnable[1];
		for (int i = 0; i < heads.length; i++) {
			int index = i;
			TableColumn column = new TableColumn(table, i == 5 ? SWT.RIGHT: SWT.NONE);
			column.setText(text.get("dialog.backups.col." + heads[i]));
			column.setWidth(widths[i]);
			column.setMoveable(true);
			column.addListener(SWT.Selection, e -> { // a click on a heading sorts by it, another one the other way round
				sort[1] = (sort[0] == index) && (sort[1] == SWT.UP) ? SWT.DOWN: SWT.UP;
				sort[0] = index;
				refill[0].run();
			});
		}
		table.addListener(SWT.PaintItem, e -> {
			if ((e.index == 0) && Boolean.TRUE.equals(e.item.getData()) && (lockImage != null)) {
				Rectangle cell = ((TableItem)e.item).getBounds(0), img = lockImage.getBounds();
				e.gc.drawImage(lockImage, cell.x + (table.getColumn(0).getWidth() - img.width) / 2 - (cell.x - ((TableItem)e.item).getBounds(0).x), cell.y + (cell.height - img.height) / 2);
			}
		});
		table.setColumnOrder(stored(ints(settings, "backupscolumnorder"), new int[] {0, 1, 2, 3, 4, 5, 6}, true));
		dlg.addListener(SWT.Close, e -> {
			int[] now = new int[heads.length];
			for (int i = 0; i < heads.length; i++)
				now[i] = table.getColumn(i).getWidth();
			settings.setIntValues("gui", "backupscolumnwidths", now);
			settings.setIntValues("gui", "backupscolumnorder", table.getColumnOrder());
		});

		Composite side = new Composite(dlg, SWT.NONE);
		side.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		GridLayout sideLayout = new GridLayout(1, false);
		sideLayout.marginWidth = sideLayout.marginHeight = 0;
		side.setLayout(sideLayout);
		// alpha 50: what was "Create backup" and "Restore backup" in the menu is here
		Button create = sideButton(side, text.get("dialog.main.menu.backup"));
		create.setToolTipText(text.get("dialog.backups.tip.create"));
		Button restoreFile = sideButton(side, text.get("dialog.backups.restorefile"));
		restoreFile.setToolTipText(text.get("dialog.backups.tip.restorefile"));
		new Label(side, SWT.SEPARATOR | SWT.HORIZONTAL).setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button lock = sideButton(side, text.get("dialog.backups.lock"));
		Button delete = sideButton(side, text.get("dialog.backups.delete"));
		Button restore = sideButton(side, text.get("dialog.backups.restore"));
		Button folder = sideButton(side, text.get("dialog.backups.openfolder"));
		Button options = sideButton(side, text.get("dialog.backups.options"));
		options.setToolTipText(text.get("dialog.backups.tip.options"));
		Button close = sideButton(side, text.get("button.close"));
		lock.setToolTipText(text.get("dialog.backups.tip.lock"));
		delete.setToolTipText(text.get("dialog.backups.tip.delete"));
		restore.setToolTipText(text.get("dialog.backups.tip.restore"));
		folder.setToolTipText(text.get("dialog.backups.tip.openfolder"));

		List<File> folders = BackupCatalog.folders();
		List<Backup> shown = new ArrayList<>();
		File[] result = new File[1];
		Runnable[] state = new Runnable[1];
		Runnable fill = () -> {
			List<File> selected = new ArrayList<>();
			for (int i: table.getSelectionIndices())
				selected.add(shown.get(i).file());
			shown.clear();
			shown.addAll(BackupCatalog.list(folders));
			shown.sort(order(text, sort[0], sort[1] == SWT.UP));
			table.setSortColumn(table.getColumn(sort[0]));
			table.setSortDirection(sort[1]);
			table.removeAll();
			long sum = 0;
			for (Backup b: shown) {
				TableItem item = new TableItem(table, SWT.NONE);
				item.setText(row(text, b, locale));
				item.setData(b.locked()); // a padlock, not a word: drawn in the middle of its column (see below)
				sum += b.size();
				if (selected.contains(b.file()))
					table.select(table.getItemCount() - 1);
			}
			StringBuilder where = new StringBuilder();
			for (File f: folders)
				where.append(where.length() == 0 ? "": "; ").append(f.getPath());
			info.setText(shown.isEmpty() ? text.get("dialog.backups.none", new Object[] {where.toString()})
					: text.get("dialog.backups.info", new Object[] {shown.size(), DiskUsage.format(sum, locale), where.toString()}));
			state[0].run();
		};
		state[0] = () -> {
			int[] sel = table.getSelectionIndices();
			boolean anyLocked = false, allLocked = sel.length > 0;
			for (int i: sel) {
				anyLocked |= shown.get(i).locked();
				allLocked &= shown.get(i).locked();
			}
			java.util.Set<Kind> always = BackupCatalog.autoLocked();
			boolean onlyAlways = sel.length > 0; // locked by their kind (options): cannot be unlocked one by one
			for (int i: sel)
				onlyAlways &= always.contains(shown.get(i).kind());
			lock.setEnabled((sel.length > 0) && !onlyAlways);
			lock.setText(text.get(allLocked ? "dialog.backups.unlock": "dialog.backups.lock"));
			delete.setEnabled((sel.length > 0) && !anyLocked);
			restore.setEnabled((sel.length == 1) && shown.get(sel[0]).kind().restorable());
			folder.setEnabled(!folders.isEmpty());
			for (Button b: new Button[] {lock, delete, restore, folder})
				b.redraw(); // drawn by DBGL in the dark mode: shows at once that it can be used now (test 12: only when the mouse came)
		};
		refill[0] = fill;
		table.addListener(SWT.Selection, e -> state[0].run());
		lock.addListener(SWT.Selection, e -> {
			int[] sel = table.getSelectionIndices();
			boolean allLocked = sel.length > 0;
			for (int i: sel)
				allLocked &= shown.get(i).locked();
			try {
				for (int i: sel)
					BackupCatalog.setLocked(shown.get(i).file(), !allLocked);
			} catch (IOException | RuntimeException ex) {
				Mess_.on(dlg).exception(ex).warning();
			}
			fill.run();
		});
		delete.addListener(SWT.Selection, e -> {
			int[] sel = table.getSelectionIndices();
			long sum = 0;
			for (int i: sel)
				sum += shown.get(i).size();
			if ((sel.length == 0) || !Mess_.on(dlg).key("dialog.backups.confirm.delete", new Object[] {sel.length, DiskUsage.format(sum, locale)}).confirm())
				return;
			// alpha 50: the files replaced when a USB stick was brought back are the only copy of what was here before
			long sticks = java.util.Arrays.stream(sel).filter(i -> shown.get(i).kind() == Kind.STICK_RETURN).count();
			if ((sticks > 0) && !Mess_.on(dlg).key("dialog.backups.confirm.deletestick", new Object[] {sticks}).confirm()) {
				dlg.forceActive();
				return;
			}
			try {
				for (int i: sel)
					BackupCatalog.delete(shown.get(i).file());
			} catch (IOException | RuntimeException ex) {
				Mess_.on(dlg).exception(ex).warning();
			}
			table.deselectAll();
			fill.run();
			dlg.forceActive();
		});
		restore.addListener(SWT.Selection, e -> {
			int[] sel = table.getSelectionIndices();
			if (sel.length != 1)
				return;
			// asked here: who says no is back in this window (test 12: was back in the main window)
			File chosen = shown.get(sel[0]).file();
			if (!org.dbgl.service.BackupService.isBackup(chosen)) {
				Mess_.on(dlg).key("dialog.main.error.nobackup", new Object[] {chosen.getPath()}).warning();
			} else if (Mess_.on(dlg).key("dialog.main.confirm.restore", new Object[] {chosen.getPath()}).confirm()) {
				result[0] = chosen;
				dlg.close();
				return;
			}
			dlg.forceActive();
			table.setFocus();
		});
		create.addListener(SWT.Selection, e -> {
			File dir = new File(org.dbgl.service.FileLocationService.getInstance().getDataDir(), org.dbgl.util.VersionBackup.DIR);
			dir.mkdirs();
			org.eclipse.swt.widgets.FileDialog fd = new org.eclipse.swt.widgets.FileDialog(dlg, SWT.SAVE);
			fd.setFilterExtensions(new String[] {"*.zip", "*.*"});
			fd.setFilterPath(dir.getPath()); // in the folder of the backups it shows in this window
			fd.setFileName("dbgl-backup-" + java.time.LocalDate.now() + ".zip");
			fd.setOverwrite(true);
			String file = fd.open();
			if (file != null) {
				dlg.setCursor(display.getSystemCursor(SWT.CURSOR_WAIT));
				try {
					int count = org.dbgl.service.BackupService.backup(new File(file));
					dlg.setCursor(null);
					Mess_.on(dlg).key("dialog.main.notice.backupdone", new Object[] {file, count}).display();
				} catch (IOException | java.sql.SQLException | RuntimeException ex) {
					dlg.setCursor(null);
					Mess_.on(dlg).exception(ex).warning();
				}
				fill.run();
			}
			dlg.forceActive();
		});
		restoreFile.addListener(SWT.Selection, e -> {
			org.eclipse.swt.widgets.FileDialog fd = new org.eclipse.swt.widgets.FileDialog(dlg, SWT.OPEN);
			fd.setFilterExtensions(new String[] {"*.zip", "*.*"});
			String file = fd.open();
			if (file != null) {
				File chosen = new File(file);
				if (!org.dbgl.service.BackupService.isBackup(chosen)) {
					Mess_.on(dlg).key("dialog.main.error.nobackup", new Object[] {chosen.getPath()}).warning();
				} else if (Mess_.on(dlg).key("dialog.main.confirm.restore", new Object[] {chosen.getPath()}).confirm()) {
					result[0] = chosen;
					dlg.close();
					return;
				}
			}
			dlg.forceActive();
		});
		folder.addListener(SWT.Selection, e -> {
			int[] sel = table.getSelectionIndices();
			// alpha 49: with the backup marked in the file manager (before: only its folder, the file to be looked for)
			if (sel.length > 0)
				SystemUtils.showInFolder(shown.get(sel[0]).file(), dlg);
			else if (folders.get(0).isDirectory())
				SystemUtils.openDirForViewing(folders.get(0), dlg);
		});
		options.addListener(SWT.Selection, e -> {
			if (askOptions(dlg, text))
				fill.run();
			dlg.forceActive();
		});
		close.addListener(SWT.Selection, e -> dlg.close());

		fill.run();
		if (table.getItemCount() > 0) { // the newest one chosen: the buttons can be used right away
			table.select(0);
			state[0].run();
		}
		DarkTheme.apply(dlg);
		dlg.pack();
		Rectangle area = shell.getMonitor().getClientArea(), parent = shell.getBounds();
		Point size = dlg.getSize();
		size.x = Math.min(size.x, area.width);
		size.y = Math.min(size.y, area.height);
		dlg.setSize(size);
		dlg.setLocation(Math.max(area.x, Math.min(parent.x + (parent.width - size.x) / 2, area.x + area.width - size.x)),
			Math.max(area.y, Math.min(parent.y + (parent.height - size.y) / 2, area.y + area.height - size.y)));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		return result[0];
	}

	/** the options: which kinds of backups are always locked; true if they were changed */
	private static boolean askOptions(Shell parent, ITextService text) {
		Display display = parent.getDisplay();
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		dlg.setText(text.get("dialog.backups.options").replace("...", ""));
		if (parent.getImage() != null)
			dlg.setImage(parent.getImage());
		dlg.setLayout(new GridLayout(2, false));
		DarkTheme.window(dlg);
		Label info = new Label(dlg, SWT.WRAP);
		info.setText(text.get("dialog.backups.options.info"));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		infoData.widthHint = 460;
		info.setLayoutData(infoData);
		java.util.Set<Kind> before = BackupCatalog.autoLocked();
		Kind[] kinds = Kind.values();
		Button[] boxes = new Button[kinds.length];
		for (int i = 0; i < kinds.length; i++) {
			boxes[i] = new Button(dlg, SWT.CHECK);
			boxes[i].setText(kindText(text, kinds[i]));
			boxes[i].setSelection(before.contains(kinds[i]));
			boxes[i].setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false, 2, 1));
		}
		Button ok = new Button(dlg, SWT.PUSH);
		ok.setText(text.get("button.ok"));
		GridData okData = new GridData(SWT.END, SWT.CENTER, true, false);
		okData.widthHint = 90;
		ok.setLayoutData(okData);
		Button cancel = new Button(dlg, SWT.PUSH);
		cancel.setText(text.get("button.cancel"));
		GridData cancelData = new GridData(SWT.END, SWT.CENTER, false, false);
		cancelData.widthHint = 90;
		cancel.setLayoutData(cancelData);
		dlg.setDefaultButton(ok);
		boolean[] changed = {false};
		ok.addListener(SWT.Selection, e -> {
			java.util.Set<Kind> now = java.util.EnumSet.noneOf(Kind.class);
			for (int i = 0; i < kinds.length; i++)
				if (boxes[i].getSelection())
					now.add(kinds[i]);
			if (!now.equals(before)) {
				BackupCatalog.setAutoLocked(now);
				changed[0] = true;
			}
			dlg.close();
		});
		cancel.addListener(SWT.Selection, e -> dlg.close());
		DarkTheme.apply(dlg);
		dlg.pack();
		Rectangle p = parent.getBounds();
		Point size = dlg.getSize();
		dlg.setLocation(p.x + Math.max(0, (p.width - size.x) / 2), p.y + Math.max(0, (p.height - size.y) / 2));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		return changed[0];
	}

	private static int[] ints(org.dbgl.service.SettingsService settings, String item) {
		try {
			return settings.getIntValues("gui", item);
		} catch (RuntimeException e) {
			return null; // nothing stored yet
		}
	}

	/** what was stored if it fits (as many numbers; an order: each column once), else the default */
	static int[] stored(int[] values, int[] defaults, boolean order) {
		if ((values == null) || (values.length != defaults.length))
			return defaults;
		boolean[] seen = new boolean[defaults.length];
		for (int v: values) {
			if (order ? (v < 0) || (v >= defaults.length) || seen[v]: (v < 20) || (v > 2000))
				return defaults;
			if (order)
				seen[v] = true;
		}
		return values;
	}

	/** the order of the rows after a click on the heading of a column */
	public static java.util.Comparator<Backup> order(ITextService text, int column, boolean ascending) {
		java.util.Comparator<Backup> c;
		switch (column) {
			case 0:
				c = java.util.Comparator.comparing(b -> !b.locked()); // the locked ones first
				break;
			case 1:
				c = java.util.Comparator.comparing(b -> kindText(text, b.kind()), String.CASE_INSENSITIVE_ORDER);
				break;
			case 2:
				c = java.util.Comparator.comparing(b -> contentText(text, b.kind()), String.CASE_INSENSITIVE_ORDER);
				break;
			case 3:
				c = java.util.Comparator.comparing(b -> BackupCatalog.pretty(b.version()), String.CASE_INSENSITIVE_ORDER);
				break;
			case 5:
				c = java.util.Comparator.comparingLong(Backup::size);
				break;
			case 6:
				c = java.util.Comparator.comparing(b -> b.file().getName(), String.CASE_INSENSITIVE_ORDER);
				break;
			default:
				c = java.util.Comparator.comparingLong(Backup::when);
		}
		if (!ascending)
			c = c.reversed();
		return c.thenComparing(java.util.Comparator.comparingLong(Backup::when).reversed());
	}

	private static Button sideButton(Composite side, String label) {
		Button b = new Button(side, SWT.PUSH);
		b.setText(label);
		GridData data = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		data.widthHint = Math.max(150, b.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
		b.setLayoutData(data);
		return b;
	}
}
