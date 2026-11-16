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
import java.util.List;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.dbgl.util.StickReturn;
import org.dbgl.util.StickReturn.FileAction;
import org.dbgl.util.StickReturn.Plan;
import org.dbgl.util.StickReturn.StatChange;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
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
 * alpha 50: the preview of "Bring back from the USB stick": what would come into this installation, in groups with an empty
 * line between them, each kind in its color (as the legend above shows), and a box before each line: only what is ticked is
 * taken over. What was changed on both sides and what is gone here is shown, without a box.
 */
public final class StickReturnPreview {

	private StickReturnPreview() {
	}

	public enum Kind {
		HEADING, EMPTY, STAT, NEW, CHANGED, CONFLICT, GONE
	}

	/** a line of the list; what: the StatChange or FileAction it stands for, null for the others */
	public record Row(Kind kind, String text, Object what) {
	}

	/** the lines: per group a heading, its lines, and an empty line before the next group */
	public static List<Row> rows(Plan plan, ITextService text) {
		List<Row> rows = new ArrayList<>();
		if (!plan.stats().isEmpty()) {
			rows.add(new Row(Kind.HEADING, text.get("dialog.stickreturn.stats") + ": " + plan.stats().size(), null));
			for (StatChange s: plan.stats())
				rows.add(new Row(Kind.STAT, text.get("dialog.stickreturn.stat", new Object[] {s.title(), String.valueOf(s.runs()), String.format("%d:%02d", s.seconds() / 3600, (s.seconds() % 3600) / 60)}), s));
		}
		List<FileAction> fresh = plan.files().stream().filter(f -> !f.replaces()).toList(), changed = plan.files().stream().filter(FileAction::replaces).toList();
		if (!fresh.isEmpty()) {
			group(rows, text.get("dialog.stickreturn.new") + ": " + fresh.size());
			fresh.forEach(f -> rows.add(new Row(Kind.NEW, "+ " + f.to().getPath(), f)));
		}
		if (!changed.isEmpty()) {
			group(rows, text.get("dialog.stickreturn.changed") + ": " + changed.size());
			changed.forEach(f -> rows.add(new Row(Kind.CHANGED, "> " + f.to().getPath(), f)));
		}
		if (!plan.conflicts().isEmpty()) {
			group(rows, text.get("dialog.stickreturn.conflicts") + ": " + plan.conflicts().size());
			plan.conflicts().forEach(f -> rows.add(new Row(Kind.CONFLICT, "! " + f.getPath(), null)));
		}
		if (!plan.gone().isEmpty()) {
			group(rows, text.get("dialog.stickreturn.gone") + ": " + plan.gone().size());
			plan.gone().forEach(t -> rows.add(new Row(Kind.GONE, "! " + t, null)));
		}
		return rows;
	}

	private static void group(List<Row> rows, String heading) {
		if (!rows.isEmpty())
			rows.add(new Row(Kind.EMPTY, "", null));
		rows.add(new Row(Kind.HEADING, heading, null));
	}

	/** the plan with only what was chosen (the play times and the files); the rest of it as it is */
	public static Plan chosen(Plan plan, java.util.Collection<Object> chosen) {
		java.util.Set<Object> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		set.addAll(chosen);
		return new Plan(plan.stickId(), plan.created(), plan.stats().stream().filter(set::contains).toList(), plan.files().stream().filter(set::contains).toList(), plan.conflicts(), plan.gone(),
				plan.seen(), plan.stateThere());
	}

	/** the colors of the kinds: for a light and for a dark window */
	private static int[] rgb(Kind kind, boolean dark) {
		return switch (kind) {
			case STAT -> dark ? new int[] {120, 180, 255}: new int[] {0, 90, 180};
			case NEW -> dark ? new int[] {120, 205, 120}: new int[] {0, 125, 0};
			case CHANGED -> dark ? new int[] {240, 170, 80}: new int[] {190, 100, 0};
			case CONFLICT, GONE -> dark ? new int[] {255, 125, 125}: new int[] {185, 0, 0};
			default -> null;
		};
	}

	/**
	 * shows the window
	 *
	 * @param info where from and where to, above the legend
	 * @return the plan with what was chosen, null if nothing is to be taken over
	 */
	public static Plan open(Shell shell, String info, Plan plan) {
		ITextService text = TextService.getInstance();
		Display display = shell.getDisplay();
		boolean dark = DarkTheme.forced();
		Shell dlg = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.MAX);
		dlg.setText(text.get("dialog.stickreturn.title"));
		org.eclipse.swt.graphics.Image windowImage = org.dbgl.service.ImageService.getResourceImage(display, org.dbgl.service.ImageService.IMG_USB);
		if (windowImage != null) {
			dlg.setImage(windowImage);
			dlg.addDisposeListener(e -> windowImage.dispose());
		} else {
			BaseDialog.inheritWindowImage(dlg, shell);
		}
		dlg.setLayout(new GridLayout(1, false));
		DarkTheme.window(dlg);

		Label infoLabel = new Label(dlg, SWT.WRAP);
		infoLabel.setText(info);
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		infoData.widthHint = 860;
		infoLabel.setLayoutData(infoData);

		java.util.Map<Kind, Color> colors = new java.util.EnumMap<>(Kind.class);
		for (Kind k: Kind.values()) {
			int[] c = rgb(k, dark);
			if (c != null)
				colors.put(k, new Color(display, c[0], c[1], c[2]));
		}
		FontData[] fd = dlg.getFont().getFontData();
		for (FontData f: fd)
			f.setStyle(SWT.BOLD);
		Font bold = new Font(display, fd);
		dlg.addDisposeListener(e -> {
			colors.values().forEach(Color::dispose);
			bold.dispose();
		});

		// the legend: each kind in its color
		Composite legend = new Composite(dlg, SWT.NONE);
		legend.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		GridLayout legendLayout = new GridLayout(1, false);
		legendLayout.marginWidth = 0;
		legendLayout.verticalSpacing = 2;
		legend.setLayout(legendLayout);
		Object[][] entries = {{Kind.STAT, text.get("dialog.stickreturn.legend.stat")}, {Kind.NEW, "+ " + text.get("dialog.stickreturn.legend.new")},
				{Kind.CHANGED, "> " + text.get("dialog.stickreturn.legend.changed")}, {Kind.CONFLICT, "! " + text.get("dialog.stickreturn.legend.conflict")}};
		for (Object[] entry: entries) {
			Label l = new Label(legend, SWT.WRAP);
			l.setText((String)entry[1]);
			l.setForeground(colors.get(entry[0]));
			l.setData(DarkTheme.KEEP_FOREGROUND, Boolean.TRUE);
			GridData ld = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
			ld.widthHint = 860;
			l.setLayoutData(ld);
		}

		Table table = new Table(dlg, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = 400;
		tableData.widthHint = 860;
		table.setLayoutData(tableData);
		if (dark) {
			table.setBackground(DarkTheme.inputBackground);
			table.setForeground(DarkTheme.defaultForeground);
		}
		Label count = new Label(dlg, SWT.NONE);
		count.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button[] apply = new Button[1];
		List<Row> rows = rows(plan, text);
		List<TableItem> boxes = new ArrayList<>();
		Runnable[] changed = new Runnable[1];
		CheckColumn checks = CheckColumn.on(table, () -> changed[0].run());
		TableColumn column = new TableColumn(table, SWT.NONE);
		for (Row row: rows) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(row.text());
			item.setData(row);
			Color c = colors.get(row.kind());
			if (c != null)
				item.setForeground(c);
			if (row.kind() == Kind.HEADING)
				item.setFont(bold);
			if (row.what() != null) {
				checks.add(item, true, false);
				boxes.add(item);
			} else {
				checks.addWithoutBox(item);
			}
		}
		column.pack();
		column.setWidth(Math.max(column.getWidth() + 20, 840));
		changed[0] = () -> {
			long n = boxes.stream().filter(CheckColumn::checked).count();
			count.setText(text.get("dialog.stickreturn.count", new Object[] {n, boxes.size()}));
			if ((apply[0] != null) && !apply[0].isDisposed()) {
				apply[0].setEnabled(n > 0);
				apply[0].redraw();
			}
		};

		Composite buttons = new Composite(dlg, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout buttonsLayout = new GridLayout(5, false);
		buttonsLayout.marginWidth = 0;
		buttons.setLayout(buttonsLayout);
		Button all = new Button(buttons, SWT.PUSH);
		all.setText(text.get("button.all"));
		BaseDialog.wideButton(all);
		Button none = new Button(buttons, SWT.PUSH);
		none.setText(text.get("button.none"));
		BaseDialog.wideButton(none);
		new Label(buttons, SWT.NONE).setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		apply[0] = new Button(buttons, SWT.PUSH);
		apply[0].setText(text.get("dialog.stickreturn.apply"));
		BaseDialog.wideButton(apply[0]);
		Button cancel = new Button(buttons, SWT.PUSH);
		cancel.setText(text.get("button.cancel"));
		BaseDialog.wideButton(cancel);
		boolean[] ok = {false};
		all.addListener(SWT.Selection, e -> {
			boxes.forEach(i -> checks.set(i, true, false));
			changed[0].run();
		});
		none.addListener(SWT.Selection, e -> {
			boxes.forEach(i -> checks.set(i, false, false));
			changed[0].run();
		});
		List<Object> picked = new ArrayList<>();
		apply[0].addListener(SWT.Selection, e -> {
			for (TableItem i: boxes)
				if (CheckColumn.checked(i))
					picked.add(((Row)i.getData()).what());
			ok[0] = true;
			dlg.close();
		});
		cancel.addListener(SWT.Selection, e -> dlg.close());
		if (boxes.isEmpty()) { // only something to tell: nothing to take over
			all.setVisible(false);
			none.setVisible(false);
			apply[0].setVisible(false);
			cancel.setText(text.get("button.close"));
		}
		changed[0].run();
		if (boxes.isEmpty())
			count.setText("");

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
		return ok[0] && !picked.isEmpty() ? chosen(plan, picked): null;
	}
}
