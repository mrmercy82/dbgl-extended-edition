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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.service.ITextService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.SoundScan;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


/**
 * alpha 52: menu DOSBox > "Look for music files...": where (the whole computer or a folder), the search with the folder it is in
 * and a Stop, then what was found, to copy where DOSBox looks for it, to keep as the SoundFont folder of DBGL, or to open.
 */
public final class SoundScanDialog {

	private SoundScanDialog() {
	}

	public static void open(Shell parent, DosboxVersion defaultVersion) {
		ITextService text = TextService.getInstance();
		List<File> roots = askWhere(parent, text);
		if (roots == null)
			return;
		List<SoundScan.Find> found = search(parent, text, roots);
		if (found == null)
			return;
		if (found.isEmpty()) {
			Mess_.on(parent).key("dialog.soundscan.nothing").display();
			return;
		}
		showFinds(parent, text, found, defaultVersion);
	}

	private static Shell shell(Shell parent, String title, int columns) {
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
		dlg.setText(title);
		BaseDialog.inheritWindowImage(dlg, parent);
		dlg.setLayout(new GridLayout(columns, false));
		if (DarkTheme.forced())
			dlg.setBackground(DarkTheme.dialogBackground);
		return dlg;
	}

	private static void run(Shell parent, Shell dlg) {
		DarkTheme.apply(dlg);
		dlg.pack();
		Rectangle area = parent.getMonitor().getClientArea(), p = parent.getBounds();
		Point size = dlg.getSize();
		dlg.setLocation(Math.max(area.x, p.x + (p.width - size.x) / 2), Math.max(area.y, p.y + (p.height - size.y) / 2));
		dlg.open();
		Display display = parent.getDisplay();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
	}

	private static List<File> askWhere(Shell parent, ITextService text) {
		Shell dlg = shell(parent, text.get("dialog.soundscan.title"), 3);
		Label info = new Label(dlg, SWT.WRAP);
		info.setText(text.get("dialog.soundscan.info"));
		GridData id = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		id.widthHint = 520;
		info.setLayoutData(id);
		Button whole = new Button(dlg, SWT.RADIO);
		whole.setText(text.get("dialog.soundscan.whole", new Object[] {String.join(", ", SoundScan.wholeComputer().stream().map(File::getPath).toList())}));
		whole.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		Button one = new Button(dlg, SWT.RADIO);
		one.setText(text.get("dialog.soundscan.folder"));
		Text folder = new Text(dlg, SWT.BORDER);
		GridData fd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		fd.widthHint = 320;
		folder.setLayoutData(fd);
		Button browse = new Button(dlg, SWT.PUSH);
		browse.setText(text.get("button.browse"));
		whole.setSelection(true);
		folder.addModifyListener(e -> {
			one.setSelection(true);
			whole.setSelection(false);
		});
		browse.addListener(SWT.Selection, e -> {
			DirectoryDialog dd = new DirectoryDialog(dlg);
			String chosen = dd.open();
			if (chosen != null)
				folder.setText(chosen);
		});
		List<File>[] result = new List[1];
		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 3, 1));
		row.setLayout(new GridLayout(2, true));
		Button start = new Button(row, SWT.PUSH);
		start.setText(text.get("dialog.soundscan.start"));
		start.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button cancel = new Button(row, SWT.PUSH);
		cancel.setText(text.get("button.cancel"));
		cancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		dlg.setDefaultButton(start);
		start.addListener(SWT.Selection, e -> {
			if (one.getSelection()) {
				File f = new File(folder.getText().trim());
				if (!f.isDirectory()) {
					Mess_.on(dlg).key("dialog.soundscan.nofolder", new Object[] {f.getPath()}).warning();
					return;
				}
				result[0] = List.of(f);
			} else
				result[0] = SoundScan.wholeComputer();
			dlg.close();
		});
		cancel.addListener(SWT.Selection, e -> dlg.close());
		run(parent, dlg);
		return result[0];
	}

	/** the search in the background; Stop keeps what was found so far. @return null if the window was closed */
	private static List<SoundScan.Find> search(Shell parent, ITextService text, List<File> roots) {
		Display display = parent.getDisplay();
		Shell dlg = shell(parent, text.get("dialog.soundscan.title"), 1);
		Label now = new Label(dlg, SWT.NONE);
		GridData nd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		nd.widthHint = 560;
		now.setLayoutData(nd);
		now.setText(text.get("dialog.soundscan.searching", new Object[] {""}));
		Button stop = new Button(dlg, SWT.PUSH);
		stop.setText(text.get("dialog.soundscan.stop"));
		stop.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
		AtomicBoolean cancelled = new AtomicBoolean(), done = new AtomicBoolean();
		AtomicReference<File> current = new AtomicReference<>();
		AtomicReference<List<SoundScan.Find>> result = new AtomicReference<>(new ArrayList<>());
		stop.addListener(SWT.Selection, e -> cancelled.set(true));
		Thread worker = new Thread(() -> {
			try {
				result.set(SoundScan.scan(roots, current::set, cancelled::get));
			} finally {
				done.set(true);
				display.wake();
			}
		}, "SoundScan");
		worker.setDaemon(true);
		Runnable[] tick = new Runnable[1];
		tick[0] = () -> {
			if (dlg.isDisposed())
				return;
			if (done.get()) {
				dlg.close();
				return;
			}
			File f = current.get();
			if (f != null)
				now.setText(text.get("dialog.soundscan.searching", new Object[] {org.apache.commons.lang3.StringUtils.abbreviateMiddle(f.getPath(), "...", 80)}));
			display.timerExec(200, tick[0]);
		};
		dlg.addListener(SWT.Close, e -> cancelled.set(true));
		worker.start();
		display.timerExec(200, tick[0]);
		run(parent, dlg);
		try {
			worker.join(5000); // after Stop it ends with the folder it is in
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return result.get();
	}

	private static String kindText(ITextService text, SoundScan.Kind kind) {
		return text.get("dialog.soundscan.kind." + kind.name().toLowerCase(java.util.Locale.ROOT));
	}

	private static void showFinds(Shell parent, ITextService text, List<SoundScan.Find> found, DosboxVersion db) {
		List<SoundScan.Find> finds = new ArrayList<>(found);
		finds.sort(java.util.Comparator.comparing((SoundScan.Find f) -> f.kind()).thenComparing(f -> f.dir().getPath(), String.CASE_INSENSITIVE_ORDER));
		Shell dlg = shell(parent, text.get("dialog.soundscan.title"), 1);
		Label info = new Label(dlg, SWT.WRAP);
		info.setText(text.get("dialog.soundscan.found", new Object[] {finds.size()}));
		GridData id = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		id.widthHint = 720;
		info.setLayoutData(id);
		Table table = new Table(dlg, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
		table.setHeaderVisible(true);
		GridData td = new GridData(SWT.FILL, SWT.FILL, true, true);
		td.heightHint = 280;
		table.setLayoutData(td);
		String[] cols = {"dialog.soundscan.column.kind", "dialog.soundscan.column.content", "dialog.soundscan.column.folder"};
		int[] widths = {150, 200, 380};
		for (int i = 0; i < cols.length; i++) {
			TableColumn c = new TableColumn(table, SWT.NONE);
			c.setText(text.get(cols[i]));
			c.setWidth(widths[i]);
		}
		for (SoundScan.Find f: finds) {
			TableItem item = new TableItem(table, SWT.NONE);
			String files = text.get("dialog.soundscan.files", new Object[] {f.files()});
			String content = f.details().isEmpty() ? files: (f.kind() == SoundScan.Kind.ULTRASND) ? f.details().get(0) + ", " + files: String.join(", ", f.details());
			item.setText(new String[] {kindText(text, f.kind()), content, f.dir().getPath()});
		}
		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		row.setLayout(new GridLayout(4, false));
		Button copy = new Button(row, SWT.PUSH);
		copy.setText(text.get("dialog.soundscan.copy"));
		copy.setToolTipText(text.get("dialog.soundscan.copy.tooltip"));
		Button keep = new Button(row, SWT.PUSH);
		keep.setText(text.get("dialog.soundscan.keepsoundfonts"));
		Button openDir = new Button(row, SWT.PUSH);
		openDir.setText(text.get("dialog.soundscan.open"));
		Button close = new Button(row, SWT.PUSH);
		close.setText(text.get("button.close"));
		close.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
		Runnable enable = () -> {
			int[] sel = table.getSelectionIndices();
			copy.setEnabled(sel.length > 0);
			keep.setEnabled((sel.length == 1) && (finds.get(sel[0]).kind() == SoundScan.Kind.SOUNDFONT));
			openDir.setEnabled(sel.length == 1);
		};
		table.addListener(SWT.Selection, e -> enable.run());
		enable.run();
		copy.addListener(SWT.Selection, e -> {
			StringBuilder report = new StringBuilder();
			java.util.Map<SoundScan.Kind, File> chosen = new java.util.EnumMap<>(SoundScan.Kind.class);
			for (int i: table.getSelectionIndices()) {
				SoundScan.Find f = finds.get(i);
				if (!chosen.containsKey(f.kind())) { // once for each kind, with the folder DOSBox looks in as a start
					DirectoryDialog dd = new DirectoryDialog(dlg);
					dd.setText(text.get("dialog.soundscan.copyto", new Object[] {kindText(text, f.kind())}));
					File suggested = SoundScan.target(f.kind(), db);
					if (suggested != null)
						dd.setFilterPath((suggested.isDirectory() ? suggested: suggested.getParentFile()).getPath());
					String dir = dd.open();
					if ((dir != null) && (suggested != null) && !suggested.isDirectory() && new File(dir).equals(suggested.getParentFile()))
						dir = suggested.getPath(); // the folder that is not there yet
					chosen.put(f.kind(), dir == null ? null: new File(dir));
				}
				File to = chosen.get(f.kind());
				if (to == null)
					continue;
				try {
					int n = SoundScan.copy(f, to);
					report.append(text.get("dialog.soundscan.copied", new Object[] {n, kindText(text, f.kind()), to.getPath()})).append('\n');
				} catch (java.io.IOException | RuntimeException ex) {
					report.append(f.dir().getPath()).append(": ").append(ex.getMessage()).append('\n');
				}
			}
			if (report.length() > 0)
				Mess_.on(dlg).txt(report.toString().trim()).display();
		});
		keep.addListener(SWT.Selection, e -> {
			int i = table.getSelectionIndex();
			if (i < 0)
				return;
			SettingsService.getInstance().setValue("gui", org.dbgl.util.SoundFonts.SETTING, finds.get(i).dir().getPath());
			Mess_.on(dlg).key("dialog.soundscan.kept", new Object[] {finds.get(i).dir().getPath()}).display();
		});
		openDir.addListener(SWT.Selection, e -> {
			int i = table.getSelectionIndex();
			if (i >= 0)
				SystemUtils.openDirForViewing(finds.get(i).dir(), dlg);
		});
		close.addListener(SWT.Selection, e -> dlg.close());
		run(parent, dlg);
	}
}
