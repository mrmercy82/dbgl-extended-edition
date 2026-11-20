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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
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
import org.eclipse.swt.widgets.TableItem;


/**
 * alpha 52: which columns go into the exported list (at first those of the profile list), and as CSV or as a web page
 */
public final class ExportColumnsDialog {

	/** the columns chosen (their numbers, in the order of the list) and the kind of file: "csv", "html" or "txt" */
	public record Choice(int[] columns, String kind) {
		public boolean html() {
			return "html".equals(kind);
		}
	}

	private ExportColumnsDialog() {
	}

	/**
	 * @param ids the numbers of all columns that can be exported, in order
	 * @param names the names of the columns by number
	 * @param shownInOrder the columns of the profile list, in the order shown there
	 */
	public static Choice open(Shell parent, int[] ids, String[] names, List<Integer> shownInOrder) {
		Set<Integer> shown = new java.util.HashSet<>(shownInOrder);
		ITextService text = TextService.getInstance();
		Display display = parent.getDisplay();
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
		dlg.setText(text.get("dialog.exportlist.columns.title"));
		BaseDialog.inheritWindowImage(dlg, parent);
		dlg.setLayout(new GridLayout(1, false));
		if (DarkTheme.forced())
			dlg.setBackground(DarkTheme.dialogBackground);
		Label info = new Label(dlg, SWT.WRAP);
		info.setText(text.get("dialog.exportlist.columns.info2"));
		GridData id = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		id.widthHint = 420;
		info.setLayoutData(id);
		Composite middle = new Composite(dlg, SWT.NONE);
		middle.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		GridLayout ml = new GridLayout(2, false);
		ml.marginWidth = 0;
		ml.marginHeight = 0;
		middle.setLayout(ml);
		Table table = new Table(middle, SWT.CHECK | SWT.BORDER | SWT.V_SCROLL | SWT.FULL_SELECTION);
		GridData td = new GridData(SWT.FILL, SWT.FILL, true, true);
		td.heightHint = 360;
		table.setLayoutData(td);
		// alpha 52: the order of the columns in the file is the order here; the last choice is remembered
		Runnable asProfileList = () -> fill(table, ids, names, shown, shownInOrder);
		List<Integer> saved = saved(ids);
		if (saved.isEmpty())
			asProfileList.run();
		else
			fill(table, ids, names, new java.util.HashSet<>(saved), saved);
		Composite moves = new Composite(middle, SWT.NONE);
		moves.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		moves.setLayout(new GridLayout(1, true));
		Button up = new Button(moves, SWT.PUSH);
		up.setText(text.get("dialog.exportlist.columns.up"));
		up.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button down = new Button(moves, SWT.PUSH);
		down.setText(text.get("dialog.exportlist.columns.down"));
		down.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		up.addListener(SWT.Selection, e -> move(table, -1));
		down.addListener(SWT.Selection, e -> move(table, 1));
		Runnable enable = () -> {
			int i = table.getSelectionIndex();
			up.setEnabled(i > 0);
			down.setEnabled((i >= 0) && (i < table.getItemCount() - 1));
		};
		table.addListener(SWT.Selection, e -> enable.run());
		enable.run();
		Composite pick = new Composite(dlg, SWT.NONE);
		pick.setLayout(new GridLayout(3, false));
		Button asList = new Button(pick, SWT.PUSH);
		asList.setText(text.get("dialog.exportlist.columns.aslist"));
		asList.addListener(SWT.Selection, e -> {
			asProfileList.run();
			enable.run();
		});
		Button all = new Button(pick, SWT.PUSH);
		all.setText(text.get("button.all"));
		all.addListener(SWT.Selection, e -> {
			for (TableItem item: table.getItems())
				item.setChecked(true);
		});
		Button none = new Button(pick, SWT.PUSH);
		none.setText(text.get("button.none"));
		none.addListener(SWT.Selection, e -> {
			for (TableItem item: table.getItems())
				item.setChecked(false);
		});
		Composite kind = new Composite(dlg, SWT.NONE);
		kind.setLayout(new GridLayout(3, false));
		Button csv = new Button(kind, SWT.RADIO);
		csv.setText(text.get("dialog.exportlist.columns.csv"));
		Button html = new Button(kind, SWT.RADIO);
		html.setText(text.get("dialog.exportlist.columns.html"));
		Button txt = new Button(kind, SWT.RADIO); // alpha 52: also as plain text, the columns lined up
		txt.setText(text.get("dialog.exportlist.columns.txt"));
		String was = org.dbgl.service.SettingsService.getInstance().getValue("gui", "exportcolumnskind");
		html.setSelection("html".equals(was));
		txt.setSelection("txt".equals(was));
		csv.setSelection(!html.getSelection() && !txt.getSelection());

		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
		row.setLayout(new GridLayout(2, true));
		Button ok = new Button(row, SWT.PUSH);
		ok.setText(text.get("dialog.exportlist.startexport"));
		ok.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button cancel = new Button(row, SWT.PUSH);
		cancel.setText(text.get("button.cancel"));
		cancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		dlg.setDefaultButton(ok);
		Choice[] result = new Choice[1];
		ok.addListener(SWT.Selection, e -> {
			List<Integer> chosen = new ArrayList<>();
			for (TableItem item: table.getItems())
				if (item.getChecked())
					chosen.add((Integer)item.getData());
			if (chosen.isEmpty()) {
				Mess_.on(dlg).key("dialog.exportlist.columns.nothing").warning();
				return;
			}
			String chosenKind = html.getSelection() ? "html": txt.getSelection() ? "txt": "csv";
			result[0] = new Choice(chosen.stream().mapToInt(Integer::intValue).toArray(), chosenKind);
			org.dbgl.service.SettingsService.getInstance().setValue("gui", "exportcolumns", String.join(",", chosen.stream().map(String::valueOf).toList()));
			org.dbgl.service.SettingsService.getInstance().setValue("gui", "exportcolumnskind", chosenKind);
			dlg.close();
		});
		cancel.addListener(SWT.Selection, e -> dlg.close());
		DarkTheme.apply(dlg);
		dlg.pack();
		Rectangle area = parent.getMonitor().getClientArea(), p = parent.getBounds();
		Point size = dlg.getSize();
		dlg.setLocation(Math.max(area.x, p.x + (p.width - size.x) / 2), Math.max(area.y, p.y + (p.height - size.y) / 2));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		return result[0];
	}

	/** the columns ticked the last time, in their order (setting [gui] exportcolumns); only those that are still there */
	static List<Integer> saved(int[] ids) {
		List<Integer> result = new ArrayList<>();
		Set<Integer> known = new java.util.HashSet<>();
		for (int i: ids)
			known.add(i);
		for (String s: org.dbgl.service.SettingsService.getInstance().getValue("gui", "exportcolumns").split(",")) {
			try {
				int i = Integer.parseInt(s.trim());
				if (known.contains(i) && !result.contains(i))
					result.add(i);
			} catch (NumberFormatException e) {
				// left out
			}
		}
		return result;
	}

	/** the ticked ones in their order at the top, the others below in the order of the profile list */
	private static void fill(Table table, int[] ids, String[] names, Set<Integer> checked, List<Integer> top) {
		table.removeAll();
		List<Integer> all = new ArrayList<>(top);
		for (int i: ids)
			if (!all.contains(i))
				all.add(i);
		for (int i: all) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(names[i]);
			item.setData(i);
			item.setChecked(checked.contains(i));
		}
	}

	/** the selected column one place up (-1) or down (1) */
	private static void move(Table table, int by) {
		int i = table.getSelectionIndex(), j = i + by;
		if ((i < 0) || (j < 0) || (j >= table.getItemCount()))
			return;
		TableItem a = table.getItem(i), b = table.getItem(j);
		String text = a.getText();
		Object data = a.getData();
		boolean checked = a.getChecked();
		a.setText(b.getText());
		a.setData(b.getData());
		a.setChecked(b.getChecked());
		b.setText(text);
		b.setData(data);
		b.setChecked(checked);
		table.setSelection(j);
		table.notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());
	}
}
