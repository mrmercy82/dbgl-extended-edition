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
import org.dbgl.util.DiskUsage;
import org.dbgl.util.DosboxLog;
import org.dbgl.util.DosboxLog.Entry;
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
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * alpha 50: the window "Manage log files", like the one of the backups: the logs of the DOSBox output of the profiles (folder
 * "logs", one folder per profile) and the diagnostic files of DBGL there, to look at, to delete, and how many per profile are
 * kept. Only files DBGL wrote are shown and deleted.
 */
public final class LogManager {

	private LogManager() {
	}

	/** the cells of a row: profile, date, size, file */
	public static String[] row(ITextService text, Entry e, Locale locale) {
		return new String[] {e.profile().isEmpty() ? text.get("dialog.logs.own"): e.profile(), DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale).format(new Date(e.when())),
				DiskUsage.format(e.size(), locale), e.file().getName()};
	}

	public static java.util.Comparator<Entry> order(ITextService text, int column, boolean ascending) {
		java.util.Comparator<Entry> c = switch (column) {
			case 0 -> java.util.Comparator.comparing((Entry e) -> (e.profile().isEmpty() ? text.get("dialog.logs.own"): e.profile()).toLowerCase(Locale.ROOT));
			case 2 -> java.util.Comparator.comparingLong(Entry::size);
			case 3 -> java.util.Comparator.comparing((Entry e) -> e.file().getName().toLowerCase(Locale.ROOT));
			default -> java.util.Comparator.comparingLong(Entry::when);
		};
		c = ascending ? c: c.reversed();
		return c.thenComparing(java.util.Comparator.comparingLong(Entry::when).reversed()).thenComparing(e -> e.file().getPath());
	}

	/**
	 * shows the window
	 *
	 * @param view shows a log (the main window does that, in its window for texts)
	 */
	public static void open(Shell shell, Locale locale, java.util.function.BiConsumer<Shell, Entry> view) {
		ITextService text = TextService.getInstance();
		Display display = shell.getDisplay();
		Shell dlg = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.MAX);
		dlg.setText(text.get("dialog.logs.title"));
		org.eclipse.swt.graphics.Image windowImage = org.dbgl.service.ImageService.getResourceImage(display, org.dbgl.service.ImageService.IMG_LOG);
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
		infoData.widthHint = 800;
		info.setLayoutData(infoData);

		Table table = new Table(dlg, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION | SWT.MULTI);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = 380;
		tableData.widthHint = 760;
		table.setLayoutData(tableData);
		table.setHeaderVisible(true);
		table.setLinesVisible(!DarkTheme.forced());
		if (DarkTheme.forced()) {
			table.setBackground(DarkTheme.inputBackground);
			table.setForeground(DarkTheme.defaultForeground);
			table.setHeaderBackground(DarkTheme.tableHeaderBackground);
			table.setHeaderForeground(DarkTheme.tableHeaderForeground);
		}
		String[] heads = {text.get("dialog.logs.col.profile"), text.get("dialog.backups.col.date"), text.get("dialog.backups.col.size"), text.get("dialog.backups.col.file")};
		int[] widths = {300, 160, 90, 190};
		int[] sort = {1, SWT.DOWN}; // the newest first
		Runnable[] refill = new Runnable[1];
		for (int i = 0; i < heads.length; i++) {
			int index = i;
			TableColumn column = new TableColumn(table, i == 2 ? SWT.RIGHT: SWT.NONE);
			column.setText(heads[i]);
			column.setWidth(widths[i]);
			column.addListener(SWT.Selection, e -> {
				sort[1] = (sort[0] == index) && (sort[1] == SWT.UP) ? SWT.DOWN: (sort[0] == index) ? SWT.UP: (index == 1 || index == 2) ? SWT.DOWN: SWT.UP;
				sort[0] = index;
				refill[0].run();
			});
		}

		Composite side = new Composite(dlg, SWT.NONE);
		side.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		GridLayout sideLayout = new GridLayout(1, false);
		sideLayout.marginWidth = sideLayout.marginHeight = 0;
		side.setLayout(sideLayout);
		Button show = sideButton(side, text.get("dialog.logs.view"));
		Button delete = sideButton(side, text.get("dialog.backups.delete"));
		Button folder = sideButton(side, text.get("dialog.backups.openfolder"));
		Button close = sideButton(side, text.get("button.close"));

		// how many logs of a profile are kept
		Composite keepRow = new Composite(dlg, SWT.NONE);
		keepRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		GridLayout keepLayout = new GridLayout(3, false);
		keepLayout.marginWidth = 0;
		keepRow.setLayout(keepLayout);
		new Label(keepRow, SWT.NONE).setText(text.get("dialog.logs.keep"));
		Spinner keep = new Spinner(keepRow, SWT.BORDER);
		keep.setMinimum(1);
		keep.setMaximum(999);
		keep.setSelection(DosboxLog.keep());
		Button apply = new Button(keepRow, SWT.PUSH);
		apply.setText(text.get("dialog.logs.keep.apply"));
		BaseDialog.wideButton(apply);

		File logsDir = DosboxLog.logsDir();
		List<Entry> shown = new ArrayList<>();
		Runnable[] state = new Runnable[1];
		Runnable fill = () -> {
			List<File> selected = new ArrayList<>();
			for (int i: table.getSelectionIndices())
				selected.add(shown.get(i).file());
			shown.clear();
			shown.addAll(DosboxLog.list(logsDir));
			shown.sort(order(text, sort[0], sort[1] == SWT.UP));
			table.setSortColumn(table.getColumn(sort[0]));
			table.setSortDirection(sort[1]);
			table.removeAll();
			long sum = 0;
			for (Entry e: shown) {
				TableItem item = new TableItem(table, SWT.NONE);
				item.setText(row(text, e, locale));
				sum += e.size();
				if (selected.contains(e.file()))
					table.select(table.getItemCount() - 1);
			}
			info.setText(shown.isEmpty() ? text.get("dialog.logs.none", new Object[] {logsDir.getPath()})
					: text.get("dialog.logs.info", new Object[] {shown.size(), DiskUsage.format(sum, locale), logsDir.getPath()}));
			dlg.layout(true, true);
			state[0].run();
		};
		state[0] = () -> {
			int n = table.getSelectionCount();
			show.setEnabled(n == 1);
			delete.setEnabled(n > 0);
			for (Button b: new Button[] {show, delete, folder})
				b.redraw();
		};
		refill[0] = fill;
		table.addListener(SWT.Selection, e -> state[0].run());
		Runnable view1 = () -> {
			int[] sel = table.getSelectionIndices();
			if (sel.length == 1) {
				view.accept(dlg, shown.get(sel[0]));
				dlg.forceActive();
			}
		};
		show.addListener(SWT.Selection, e -> view1.run());
		table.addListener(SWT.DefaultSelection, e -> view1.run());
		delete.addListener(SWT.Selection, e -> {
			int[] sel = table.getSelectionIndices();
			long sum = 0;
			for (int i: sel)
				sum += shown.get(i).size();
			if ((sel.length == 0) || !Mess_.on(dlg).key("dialog.logs.confirm.delete", new Object[] {sel.length, DiskUsage.format(sum, locale)}).confirm())
				return;
			try {
				for (int i: sel)
					DosboxLog.delete(logsDir, shown.get(i).file());
			} catch (IOException | RuntimeException ex) {
				Mess_.on(dlg).exception(ex).warning();
			}
			table.deselectAll();
			fill.run();
			dlg.forceActive();
		});
		folder.addListener(SWT.Selection, e -> {
			int[] sel = table.getSelectionIndices();
			if (sel.length > 0)
				SystemUtils.showInFolder(shown.get(sel[0]).file(), dlg);
			else if (logsDir.isDirectory())
				SystemUtils.openDirForViewing(logsDir, dlg);
		});
		apply.addListener(SWT.Selection, e -> {
			int n = keep.getSelection();
			// how many would go: told before, with their number
			int over = 0;
			java.util.Map<String, Integer> perProfile = new java.util.HashMap<>();
			for (Entry en: DosboxLog.list(logsDir))
				if (!en.profile().isEmpty() && (perProfile.merge(en.profile(), 1, Integer::sum) > n))
					over++;
			if ((over > 0) && !Mess_.on(dlg).key("dialog.logs.keep.confirm", new Object[] {n, over}).confirm()) {
				dlg.forceActive();
				return;
			}
			DosboxLog.setKeep(n);
			DosboxLog.removeOld(logsDir, n);
			fill.run();
			dlg.forceActive();
		});
		close.addListener(SWT.Selection, e -> dlg.close());

		fill.run();
		if (table.getItemCount() > 0) {
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
