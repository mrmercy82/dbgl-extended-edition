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
import java.util.ArrayList;
import java.util.List;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.dbgl.util.ConfTakeover;
import org.dbgl.util.ConfTakeover.Row;
import org.dbgl.util.ConfTakeover.State;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * Alpha 49: the window of "Take over settings": the settings of another configuration file of DOSBox (of another DOSBox
 * version, or any file) that differ from the ones of this DOSBox version, each with a box; the chosen ones are written into
 * the file of this version, after a copy of it has been made.
 */
public final class ConfTakeoverDialog {

	private ConfTakeoverDialog() {
	}

	/** the other versions whose file is there: first the ones of the same family (the likely source), newest name first */
	public static List<DosboxVersion> sources(DosboxVersion target, List<DosboxVersion> all) {
		File own = target.getConfigurationCanonicalFile();
		List<DosboxVersion> same = new ArrayList<>(), others = new ArrayList<>();
		for (DosboxVersion db: all) {
			File file = db.getConfigurationCanonicalFile();
			if ((db != target) && (file != null) && file.isFile() && !file.equals(own))
				(String.valueOf(db.getFamily()).equalsIgnoreCase(String.valueOf(target.getFamily())) ? same: others).add(db);
		}
		same.sort((a, b) -> String.valueOf(b.getVersion()).compareToIgnoreCase(String.valueOf(a.getVersion())));
		same.addAll(others);
		return same;
	}

	/** what a row shows as its value: the lines of [autoexec] as their number and the first one that does something */
	static String shown(Row row, String value, ITextService text) {
		if (value == null)
			return "";
		if (row.state() != State.AUTOEXEC)
			return value;
		List<String> lines = value.lines().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
		return lines.isEmpty() ? text.get("dialog.conftakeover.lines", new Object[] {0})
				: text.get("dialog.conftakeover.lines", new Object[] {lines.size()}) + ": " + lines.get(0) + (lines.size() > 1 ? " ...": "");
	}

	/**
	 * Test 16: before anything is written, what changes in the file: the lines that go in red, the new ones in green (as in
	 * "Check profiles"), either only the sections that change or the whole file.
	 *
	 * @return true to take over, false to go back to the choice
	 */
	static boolean preview(Shell parent, File file, String before, String after, int settings) {
		ITextService text = TextService.getInstance();
		Display display = parent.getDisplay();
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		dlg.setText(text.get("dialog.checkprofiles.previewtitle"));
		BaseDialog.inheritWindowImage(dlg, parent);
		dlg.setLayout(new GridLayout(3, false));
		DarkTheme.window(dlg);
		Label info = new Label(dlg, SWT.WRAP);
		info.setText(text.get("dialog.conftakeover.previewinfo", new Object[] {settings, file.getPath()}));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		infoData.widthHint = 900;
		info.setLayoutData(infoData);
		Button full = new Button(dlg, SWT.CHECK);
		full.setText(text.get("dialog.checkprofiles.previewfull"));
		full.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));
		full.setSelection(org.dbgl.service.SettingsService.getInstance().getBooleanValue("checkprofiles", "previewfull"));
		org.eclipse.swt.custom.StyledText area = new org.eclipse.swt.custom.StyledText(dlg, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		GridData areaData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		areaData.widthHint = 900;
		areaData.heightHint = 460;
		area.setLayoutData(areaData);
		org.eclipse.swt.graphics.Font font = new org.eclipse.swt.graphics.Font(display,
				org.dbgl.util.SystemUtils.IS_WINDOWS ? "Consolas": (org.dbgl.util.SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		area.setFont(font);
		// the colours of "Check profiles"; on the dark background lighter ones, so that they can be read
		boolean dark = DarkTheme.forced();
		org.eclipse.swt.graphics.Color gone = DarkTheme.textColor(display, SWT.COLOR_DARK_RED), added = DarkTheme.textColor(display, SWT.COLOR_DARK_GREEN);
		org.eclipse.swt.graphics.Color quiet = DarkTheme.textColor(display, SWT.COLOR_DARK_GRAY);
		if (dark) {
			area.setBackground(DarkTheme.inputBackground);
			area.setForeground(DarkTheme.defaultForeground);
		}
		dlg.addDisposeListener(e -> font.dispose());
		Runnable render = () -> {
			StringBuilder sb = new StringBuilder();
			List<org.eclipse.swt.custom.StyleRange> styles = new ArrayList<>();
			for (org.dbgl.util.ProfileFindings.DiffLine line: ConfTakeover.preview(before, after, full.getSelection())) {
				boolean section = (line.kind() == ' ') && line.text().trim().startsWith("[") && line.text().trim().endsWith("]");
				boolean comment = (line.kind() == ' ') && line.text().trim().startsWith("#");
				String shown = (line.kind() == ' ' ? "  ": line.kind() + " ") + line.text();
				org.eclipse.swt.graphics.Color color = line.kind() == '-' ? gone: line.kind() == '+' ? added: (section || comment) && full.getSelection() ? (comment ? quiet: null): section ? quiet: null;
				if (color != null)
					styles.add(new org.eclipse.swt.custom.StyleRange(sb.length(), shown.length(), color, null));
				sb.append(shown).append('\n');
			}
			area.setText(sb.toString());
			area.setStyleRanges(styles.toArray(new org.eclipse.swt.custom.StyleRange[0]));
			if (full.getSelection()) { // to the first line that changes
				int first = sb.indexOf("\n- ");
				if (first >= 0)
					area.setTopIndex(Math.max(0, area.getLineAtOffset(first + 1) - 3));
			}
		};
		full.addListener(SWT.Selection, e -> {
			org.dbgl.service.SettingsService.getInstance().setBooleanValue("checkprofiles", "previewfull", full.getSelection());
			render.run();
		});
		// alpha 49: the preview as it is shown, saved as text, as a web page in its colors or as PDF
		Button save = new Button(dlg, SWT.PUSH);
		save.setText(text.get("dialog.switchdosbox.savereport"));
		GridData saveData = new GridData(SWT.BEGINNING, SWT.CENTER, true, false);
		saveData.widthHint = Math.max(110, save.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
		save.setLayoutData(saveData);
		save.addListener(SWT.Selection, e -> ReportSave.menu(dlg, "dbgl-takeover-preview", dlg.getText(), area::getText, () -> ReportSave.html(area, dlg.getText())));
		Button back = new Button(dlg, SWT.PUSH), take = new Button(dlg, SWT.PUSH);
		back.setText(text.get("button.back"));
		take.setText(text.get("dialog.conftakeover.apply"));
		for (Button b: new Button[] {back, take}) {
			GridData data = new GridData(SWT.END, SWT.CENTER, false, false);
			data.widthHint = Math.max(110, b.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
			b.setLayoutData(data);
		}
		boolean[] ok = new boolean[1];
		take.addListener(SWT.Selection, e -> {
			ok[0] = true;
			dlg.close();
		});
		back.addListener(SWT.Selection, e -> dlg.close());
		dlg.setDefaultButton(take);
		DarkTheme.apply(dlg);
		render.run();
		dlg.pack();
		Rectangle area2 = parent.getMonitor().getClientArea(), around = parent.getBounds();
		Point size = dlg.getSize();
		dlg.setLocation(Math.max(area2.x, Math.min(around.x + (around.width - size.x) / 2, area2.x + area2.width - size.x)),
			Math.max(area2.y, Math.min(around.y + (around.height - size.y) / 2, area2.y + area2.height - size.y)));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		return ok[0];
	}

	/** what a row says about itself; a renamed setting also under which name it was found */
	static String note(Row row, ITextService text) {
		String key = "dialog.conftakeover.state." + row.state().name().toLowerCase(java.util.Locale.ROOT);
		if (row.state() == State.MANUAL)
			return text.get(key, new Object[] {row.from()});
		return text.get(key) + (row.from() == null ? "": " - " + text.get("dialog.conftakeover.renamed", new Object[] {row.from()}));
	}

	/**
	 * @return true if settings were written into the configuration file of the DOSBox version (it is to be read again)
	 */
	public static boolean open(Shell shell, DosboxVersion target, List<DosboxVersion> all) {
		ITextService text = TextService.getInstance();
		Display display = shell.getDisplay();
		File targetFile = target.getConfigurationCanonicalFile();
		if ((targetFile == null) || !targetFile.isFile()) {
			Mess_.on(shell).key("dialog.conftakeover.nofile", new Object[] {String.valueOf(targetFile)}).warning();
			return false;
		}
		Shell dlg = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		dlg.setText(text.get("dialog.conftakeover.title", new Object[] {target.getTitle()}));
		BaseDialog.inheritWindowImage(dlg, shell);
		dlg.setLayout(new GridLayout(3, false));
		DarkTheme.window(dlg);

		Label intro = new Label(dlg, SWT.WRAP);
		intro.setText(text.get("dialog.conftakeover.intro", new Object[] {targetFile.getPath()}));
		GridData introData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		introData.widthHint = 900;
		intro.setLayoutData(introData);

		Label sourceLabel = new Label(dlg, SWT.NONE);
		sourceLabel.setText(text.get("dialog.conftakeover.source"));
		Combo source = new Combo(dlg, SWT.READ_ONLY);
		source.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button browse = new Button(dlg, SWT.PUSH);
		browse.setText(text.get("dialog.conftakeover.file"));
		GridData browseData = new GridData(SWT.FILL, SWT.CENTER, false, false);
		browseData.widthHint = Math.max(110, browse.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16); // test 15: as wide as the other buttons
		browse.setLayoutData(browseData);
		List<File> files = new ArrayList<>();
		for (DosboxVersion db: sources(target, all)) {
			files.add(db.getConfigurationCanonicalFile());
			source.add(db.getTitle() + "  -  " + db.getConfigurationCanonicalFile().getPath());
		}

		Table table = new Table(dlg, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.CHECK);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		tableData.heightHint = 330;
		tableData.widthHint = 900;
		table.setLayoutData(tableData);
		table.setHeaderVisible(true);
		table.setLinesVisible(!DarkTheme.forced());
		if (DarkTheme.forced()) {
			table.setBackground(DarkTheme.inputBackground);
			table.setForeground(DarkTheme.defaultForeground);
			table.setHeaderBackground(DarkTheme.tableHeaderBackground);
			table.setHeaderForeground(DarkTheme.tableHeaderForeground);
		}
		String[] heads = {"setting", "old", "new", "note"};
		int[] widths = {210, 200, 200, 300};
		for (int i = 0; i < heads.length; i++) {
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText(text.get("dialog.conftakeover.col." + heads[i]));
			column.setWidth(widths[i]);
		}

		Label count = new Label(dlg, SWT.WRAP);
		GridData countData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		countData.widthHint = 900;
		count.setLayoutData(countData);

		Composite buttons = new Composite(dlg, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		GridLayout buttonsLayout = new GridLayout(5, false);
		buttonsLayout.marginWidth = buttonsLayout.marginHeight = 0;
		buttons.setLayout(buttonsLayout);
		Button all_ = new Button(buttons, SWT.PUSH);
		all_.setText(text.get("dialog.checkprofiles.selectall"));
		Button none = new Button(buttons, SWT.PUSH);
		none.setText(text.get("dialog.conftakeover.none"));
		Label space = new Label(buttons, SWT.NONE);
		space.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button apply = new Button(buttons, SWT.PUSH);
		apply.setText(text.get("dialog.checkprofiles.preview")); // test 16: first what changes, then written
		apply.setToolTipText(text.get("dialog.conftakeover.apply.tip"));
		Button close = new Button(buttons, SWT.PUSH);
		close.setText(text.get("button.close"));
		for (Button b: new Button[] {all_, none, apply, close}) {
			GridData data = new GridData(b == all_ || b == none ? SWT.BEGINNING: SWT.END, SWT.CENTER, false, false);
			data.widthHint = Math.max(110, b.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
			b.setLayoutData(data);
		}

		List<Row> rows = new ArrayList<>();
		String[] targetText = new String[1];
		Runnable showCount = () -> {
			long chosen = java.util.Arrays.stream(table.getItems()).filter(TableItem::getChecked).count();
			if (source.getSelectionIndex() < 0)
				count.setText(text.get("dialog.conftakeover.choose"));
			else if (rows.isEmpty())
				count.setText(text.get("dialog.conftakeover.nodiff"));
			else
				count.setText(text.get("dialog.conftakeover.count", new Object[] {rows.size(), chosen}));
			apply.setEnabled(chosen > 0);
			apply.redraw();
			for (Button b: new Button[] {all_, none}) {
				b.setEnabled(!rows.isEmpty());
				b.redraw();
			}
			dlg.layout(true, true);
		};
		Runnable compare = () -> {
			table.removeAll();
			rows.clear();
			int sel = source.getSelectionIndex();
			if (sel >= 0) {
				try {
					targetText[0] = ConfTakeover.read(targetFile);
					rows.addAll(ConfTakeover.compare(ConfTakeover.read(files.get(sel)), targetText[0]));
				} catch (IOException e) {
					Mess_.on(dlg).exception(e).warning();
				}
				for (Row row: rows) {
					TableItem item = new TableItem(table, SWT.NONE);
					item.setText(new String[] {row.state() == State.AUTOEXEC ? "[autoexec]": "[" + row.section() + "] " + row.item(), shown(row, row.oldValue(), text),
							shown(row, row.newValue(), text), note(row, text)});
					item.setChecked(row.preselected());
					item.setGrayed(!row.selectable());
					if (!row.selectable())
						item.setForeground(display.getSystemColor(DarkTheme.forced() ? SWT.COLOR_GRAY: SWT.COLOR_DARK_GRAY));
				}
			}
			showCount.run();
		};
		source.addListener(SWT.Selection, e -> compare.run());
		browse.addListener(SWT.Selection, e -> {
			FileDialog fd = new FileDialog(dlg, SWT.OPEN);
			fd.setText(text.get("dialog.conftakeover.source"));
			fd.setFilterExtensions(new String[] {"*.conf", "*.*"});
			fd.setFilterPath(targetFile.getParent());
			String chosen = fd.open();
			if (chosen == null)
				return;
			File file = new File(chosen);
			if (file.equals(targetFile) || !file.isFile()) {
				Mess_.on(dlg).key("dialog.conftakeover.samefile").warning();
				return;
			}
			int at = files.indexOf(file);
			if (at < 0) {
				files.add(file);
				source.add(file.getPath());
				at = files.size() - 1;
			}
			source.select(at);
			compare.run();
		});
		table.addListener(SWT.Selection, e -> {
			if (e.detail == SWT.CHECK) {
				int at = table.indexOf((TableItem)e.item);
				if ((at >= 0) && !rows.get(at).selectable())
					((TableItem)e.item).setChecked(false); // a setting the file of this version does not have
				showCount.run();
			}
		});
		all_.addListener(SWT.Selection, e -> {
			for (int i = 0; i < rows.size(); i++)
				table.getItem(i).setChecked(rows.get(i).selectable());
			showCount.run();
		});
		none.addListener(SWT.Selection, e -> {
			for (TableItem item: table.getItems())
				item.setChecked(false);
			showCount.run();
		});
		boolean[] written = new boolean[1];
		apply.addListener(SWT.Selection, e -> {
			List<Row> chosen = new ArrayList<>();
			for (int i = 0; i < rows.size(); i++)
				if (table.getItem(i).getChecked() && rows.get(i).selectable())
					chosen.add(rows.get(i));
			if (chosen.isEmpty())
				return;
			try {
				// the file as it is now: it may have been changed while the window was open
				String now = ConfTakeover.read(targetFile);
				if (!now.equals(targetText[0])) {
					Mess_.on(dlg).key("dialog.conftakeover.changed").warning();
					compare.run();
					return;
				}
				String result = ConfTakeover.apply(now, chosen);
				if (!preview(dlg, targetFile, now, result, chosen.size()))
					return; // back to the choice
				if (!now.equals(ConfTakeover.read(targetFile))) {
					Mess_.on(dlg).key("dialog.conftakeover.changed").warning();
					compare.run();
					return;
				}
				File backup = ConfTakeover.write(targetFile, result);
				written[0] = true;
				Mess_.on(dlg).key("dialog.conftakeover.done", new Object[] {chosen.size(), backup.getPath()}).display();
				dlg.close();
			} catch (IOException ex) {
				Mess_.on(dlg).exception(ex).warning();
			}
		});
		close.addListener(SWT.Selection, e -> dlg.close());

		if (source.getItemCount() > 0)
			source.select(0);
		compare.run();
		DarkTheme.apply(dlg);
		dlg.pack();
		Rectangle area = shell.getMonitor().getClientArea(), parent = shell.getBounds();
		Point size = dlg.getSize();
		dlg.setLocation(Math.max(area.x, Math.min(parent.x + (parent.width - size.x) / 2, area.x + area.width - size.x)),
			Math.max(area.y, Math.min(parent.y + (parent.height - size.y) / 2, area.y + area.height - size.y)));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		return written[0];
	}
}
