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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.service.TextService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
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
 * Alpha 48: which buttons the toolbar of the main window shows (right-click on it, "Customize toolbar..."), and (alpha 48, later)
 * in which order, with lines between them where the user wants them, and which buttons are in the second bar (the one that goes
 * to the next row in a narrow window).
 */
public final class ToolbarCustomizer {

	/** in the order: a line between buttons, and where the second bar starts */
	public static final String LINE = "|", RIGHT = ">";

	/** a button of the toolbar: its key (also when hidden), its name and symbol */
	public record Entry(String key, String label, Image image) {
	}

	/** what the user chose: the order (null: as DBGL builds it) and the buttons to hide */
	public record Result(List<String> layout, Set<String> hidden) {
	}

	private ToolbarCustomizer() {
	}

	/** @return the keys in the setting (comma separated) */
	public static Set<String> hidden(String setting) {
		Set<String> result = new LinkedHashSet<>();
		if (setting != null)
			for (String key: setting.split(","))
				if (!key.isBlank())
					result.add(key.strip());
		return result;
	}

	/** @return the order in the setting (keys, lines, the start of the second bar), null if there is none (as built) */
	public static List<String> tokens(String setting) {
		if ((setting == null) || setting.isBlank())
			return null;
		List<String> result = new ArrayList<>();
		for (String t: setting.split(","))
			if (!t.isBlank())
				result.add(t.strip());
		return result.isEmpty() ? null: result;
	}

	/**
	 * the order of the user made whole: buttons it does not know (gone in this version) left out, new ones (of a newer version)
	 * after the button they follow as built, one start of the second bar, no line at the start or end of a bar or twice
	 */
	public static List<String> normalize(List<String> chosen, List<String> defaults) {
		Set<String> known = new LinkedHashSet<>();
		for (String t: defaults)
			if (!LINE.equals(t) && !RIGHT.equals(t))
				known.add(t);
		List<String> out = new ArrayList<>();
		for (String t: chosen == null ? defaults: chosen)
			if (LINE.equals(t) || (RIGHT.equals(t) && !out.contains(RIGHT)) || (known.contains(t) && !out.contains(t)))
				out.add(t);
		if (!out.contains(RIGHT)) { // before the first button that is in the second bar as built
			int at = out.size();
			List<String> rightOnes = defaults.indexOf(RIGHT) < 0 ? List.of(): defaults.subList(defaults.indexOf(RIGHT) + 1, defaults.size());
			for (int i = 0; i < out.size(); i++)
				if (rightOnes.contains(out.get(i)) && !LINE.equals(out.get(i))) {
					at = i;
					break;
				}
			out.add(at, RIGHT);
		}
		boolean right = false;
		String before = null;
		for (String t: defaults) {
			if (RIGHT.equals(t)) {
				right = true;
				before = RIGHT;
				continue;
			}
			if (LINE.equals(t))
				continue;
			if (!out.contains(t)) {
				int at = before != null ? out.indexOf(before) + 1: (right ? out.indexOf(RIGHT) + 1: 0);
				out.add(at, t);
			}
			before = t;
		}
		// lines only between buttons
		List<String> clean = new ArrayList<>();
		for (String t: out) {
			boolean line = LINE.equals(t);
			String last = clean.isEmpty() ? null: clean.get(clean.size() - 1);
			if (line && ((last == null) || LINE.equals(last) || RIGHT.equals(last)))
				continue;
			if (RIGHT.equals(t) && LINE.equals(last))
				clean.remove(clean.size() - 1);
			clean.add(t);
		}
		if (!clean.isEmpty() && LINE.equals(clean.get(clean.size() - 1)))
			clean.remove(clean.size() - 1);
		return clean;
	}

	/** @return what the user chose, null when cancelled */
	public static Result ask(Shell shell, List<Entry> entries, List<String> defaults, List<String> current, Set<String> hidden) {
		org.dbgl.service.ITextService text = TextService.getInstance();
		Display display = shell.getDisplay();
		Map<String, Entry> byKey = new LinkedHashMap<>();
		for (Entry e: entries)
			byKey.putIfAbsent(e.key(), e);
		List<String> defaultOrder = normalize(defaults, defaults);
		List<String> order = new ArrayList<>(normalize(current, defaults));
		Set<String> hide = new LinkedHashSet<>(hidden);

		Shell dlg = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		dlg.setText(text.get("dialog.main.tb.customize.title"));
		BaseDialog.inheritWindowImage(dlg, shell);
		dlg.setLayout(new GridLayout(2, false));
		if (DarkTheme.forced()) {
			dlg.setBackground(DarkTheme.dialogBackground);
			dlg.setForeground(DarkTheme.defaultForeground);
		}

		Label info = new Label(dlg, SWT.WRAP);
		info.setText(text.get("dialog.main.tb.customize.info"));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		infoData.widthHint = 460;
		info.setLayoutData(infoData);

		Table table = new Table(dlg, SWT.BORDER | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.SINGLE);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = 420;
		tableData.widthHint = 480;
		table.setLayoutData(tableData);
		org.eclipse.swt.graphics.Color dim = display.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
		CheckColumn[] checks = new CheckColumn[1];
		Runnable[] fill = new Runnable[1];
		checks[0] = CheckColumn.on(table, () -> {
			for (TableItem item: table.getItems())
				if (item.getData() instanceof String key && byKey.containsKey(key)) {
					if (CheckColumn.checked(item))
						hide.remove(key);
					else
						hide.add(key);
				}
		}, 22);
		fill[0] = () -> {
			int selected = Math.max(0, table.getSelectionIndex());
			table.setRedraw(false);
			table.removeAll();
			for (String t: order) {
				TableItem item = new TableItem(table, SWT.NONE);
				item.setData(t);
				if (LINE.equals(t)) {
					item.setText("\u2500\u2500\u2500\u2500  " + text.get("dialog.main.tb.customize.line") + "  \u2500\u2500\u2500\u2500");
					item.setForeground(dim);
					checks[0].addWithoutBox(item);
				} else if (RIGHT.equals(t)) {
					item.setText(text.get("dialog.main.tb.customize.right"));
					item.setForeground(dim);
					checks[0].addWithoutBox(item);
				} else {
					Entry e = byKey.get(t);
					item.setText(e.label());
					checks[0].add(item, !hide.contains(t), false);
					if (e.image() != null)
						item.setData("dbgl.symbol", e.image());
				}
			}
			table.setRedraw(true);
			if (table.getItemCount() > 0)
				table.setSelection(Math.min(selected, table.getItemCount() - 1));
			table.notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());
		};
		// the symbol of a button after its box
		table.addListener(SWT.PaintItem, e -> {
			if ((e.index == 0) && (e.item.getData("dbgl.symbol") instanceof Image img) && !img.isDisposed()) {
				TableItem item = (TableItem)e.item;
				Rectangle room = item.getImageBounds(0);
				Rectangle size = img.getBounds();
				// in the room after the box
				e.gc.drawImage(img, room.x + checks[0].boxWidth() + 2, room.y + (room.height - size.height) / 2);
			}
		});

		Composite side = new Composite(dlg, SWT.NONE);
		side.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, false));
		GridLayout sideLayout = new GridLayout(1, true);
		sideLayout.marginWidth = 0;
		side.setLayout(sideLayout);
		Button up = sideButton(side, text.get("dialog.main.tb.customize.up"));
		Button down = sideButton(side, text.get("dialog.main.tb.customize.down"));
		new Label(side, SWT.NONE);
		Button addLine = sideButton(side, text.get("dialog.main.tb.customize.addline"));
		Button removeLine = sideButton(side, text.get("dialog.main.tb.customize.removeline"));
		new Label(side, SWT.NONE);
		Button all = sideButton(side, text.get("dialog.main.tb.customize.all"));
		Button reset = sideButton(side, text.get("dialog.main.tb.customize.reset"));

		table.addListener(SWT.Selection, e -> {
			int i = table.getSelectionIndex();
			up.setEnabled(i > 0);
			down.setEnabled((i >= 0) && (i < order.size() - 1));
			addLine.setEnabled(i >= 0);
			removeLine.setEnabled((i >= 0) && LINE.equals(order.get(i)));
		});
		up.addListener(SWT.Selection, e -> move(table, order, -1, fill[0]));
		down.addListener(SWT.Selection, e -> move(table, order, 1, fill[0]));
		addLine.addListener(SWT.Selection, e -> {
			int i = table.getSelectionIndex();
			if (i < 0)
				return;
			order.add(i + 1, LINE);
			table.setSelection(i + 1);
			fill[0].run();
			table.setSelection(i + 1);
			table.notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());
		});
		removeLine.addListener(SWT.Selection, e -> {
			int i = table.getSelectionIndex();
			if ((i >= 0) && LINE.equals(order.get(i))) {
				order.remove(i);
				fill[0].run();
			}
		});
		all.addListener(SWT.Selection, e -> {
			hide.clear();
			fill[0].run();
		});
		reset.addListener(SWT.Selection, e -> {
			order.clear();
			order.addAll(defaultOrder);
			hide.clear();
			fill[0].run();
		});
		// Alt+Up and Alt+Down move too
		table.addListener(SWT.KeyDown, e -> {
			if ((e.stateMask & SWT.ALT) != 0 && (e.keyCode == SWT.ARROW_UP || e.keyCode == SWT.ARROW_DOWN)) {
				move(table, order, e.keyCode == SWT.ARROW_UP ? -1: 1, fill[0]);
				e.doit = false;
			}
		});

		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		row.setLayout(new GridLayout(3, false));
		Label space = new Label(row, SWT.NONE);
		space.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button ok = new Button(row, SWT.PUSH);
		ok.setText(text.get("button.ok"));
		GridData okData = new GridData(SWT.END, SWT.CENTER, false, false);
		okData.widthHint = Math.max(90, ok.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
		ok.setLayoutData(okData);
		Button cancel = new Button(row, SWT.PUSH);
		cancel.setText(text.get("button.cancel"));
		GridData cancelData = new GridData(SWT.END, SWT.CENTER, false, false);
		cancelData.widthHint = Math.max(90, cancel.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
		cancel.setLayoutData(cancelData);
		dlg.setDefaultButton(ok);

		Result[] result = new Result[1];
		ok.addListener(SWT.Selection, e -> {
			List<String> chosen = normalize(order, defaults);
			result[0] = new Result(chosen.equals(defaultOrder) ? null: chosen, new LinkedHashSet<>(hide));
			dlg.close();
		});
		cancel.addListener(SWT.Selection, e -> dlg.close());

		fill[0].run();
		DarkTheme.apply(dlg); // alpha 48: dark like the other windows
		dlg.pack();
		Rectangle area = shell.getMonitor().getClientArea(), parent = shell.getBounds();
		Point size = dlg.getSize();
		dlg.setLocation(Math.max(area.x, Math.min(parent.x + (parent.width - size.x) / 2, area.x + area.width - size.x)),
			Math.max(area.y, Math.min(parent.y + (parent.height - size.y) / 2, area.y + area.height - size.y)));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		return result[0];
	}

	private static Button sideButton(Composite side, String label) {
		Button b = new Button(side, SWT.PUSH);
		b.setText(label);
		b.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		return b;
	}

	private static void move(Table table, List<String> order, int by, Runnable fill) {
		int i = table.getSelectionIndex(), j = i + by;
		if ((i < 0) || (j < 0) || (j >= order.size()))
			return;
		java.util.Collections.swap(order, i, j);
		table.setSelection(j);
		fill.run();
		table.setSelection(j);
		table.notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());
	}
}
