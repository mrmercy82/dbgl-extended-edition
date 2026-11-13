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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.ImageService;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.dbgl.util.PortableExport;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


/**
 * Alpha 48: the assistant for a USB stick: which profiles (with the room their games take), which parts of DBGL (backups not
 * at first; the games and data of the chosen profiles always), where the ZIP file goes (with the free room there), whether a
 * Java runtime comes with it; then the ZIP file is made (with progress, can be cancelled) and a report shows what went in. To
 * unpack onto the stick and start with dbgl.cmd (or dbgl.exe). Sizes are found in the background, the window stays usable.
 */
public final class PortableDialog {

	private static final long JAVA_BYTES = 50L * 1024 * 1024;

	private PortableDialog() {
	}

	public static void open(Shell parent, List<Profile> all, Set<Integer> chosen) {
		ITextService text = TextService.getInstance();
		Display display = parent.getDisplay();
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		dlg.setText(text.get("dialog.portable.title"));
		dlg.setImage(ImageService.getResourceImage(display, ImageService.IMG_USB));
		if (DarkTheme.forced())
			dlg.setBackground(DarkTheme.dialogBackground);
		dlg.setLayout(new GridLayout(3, false));
		Color dim = DarkTheme.forced() ? DarkTheme.dimForeground: display.getSystemColor(SWT.COLOR_DARK_GRAY);
		Color warn = DarkTheme.forced() ? new Color(255, 140, 120): display.getSystemColor(SWT.COLOR_DARK_RED);

		Label info = new Label(dlg, SWT.WRAP);
		info.setText(text.get("dialog.portable.info"));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		infoData.widthHint = 820;
		info.setLayoutData(infoData);

		Composite lists = new Composite(dlg, SWT.NONE);
		lists.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		GridLayout listsLayout = new GridLayout(2, false);
		listsLayout.marginWidth = 0;
		lists.setLayout(listsLayout);

		// the profiles, with ID and size
		Group profilesGroup = new Group(lists, SWT.NONE);
		profilesGroup.setText(text.get("dialog.portable.profiles"));
		profilesGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		profilesGroup.setLayout(new GridLayout(3, false));
		Table table = new Table(profilesGroup, SWT.CHECK | SWT.BORDER | SWT.V_SCROLL | SWT.FULL_SELECTION);
		table.setHeaderVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		tableData.heightHint = 300;
		tableData.widthHint = 430;
		table.setLayoutData(tableData);
		TableColumn titleCol = new TableColumn(table, SWT.NONE);
		titleCol.setText(text.get("dialog.main.profiles.column.title"));
		titleCol.setWidth(270);
		TableColumn idCol = new TableColumn(table, SWT.RIGHT);
		idCol.setText(text.get("dialog.main.profiles.column.id"));
		idCol.setWidth(50);
		TableColumn sizeCol = new TableColumn(table, SWT.RIGHT);
		sizeCol.setText(text.get("dialog.portable.column.size"));
		sizeCol.setWidth(90);
		for (Profile p: all) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(new String[] {p.getTitle(), String.valueOf(p.getId()), ""});
			item.setData(p);
			item.setChecked(chosen.contains(p.getId()));
		}
		Button allButton = new Button(profilesGroup, SWT.PUSH);
		allButton.setText(text.get("button.all"));
		BaseDialog.wideButton(allButton);
		Button noneButton = new Button(profilesGroup, SWT.PUSH);
		noneButton.setText(text.get("button.none"));
		BaseDialog.wideButton(noneButton);
		Label count = new Label(profilesGroup, SWT.NONE);
		count.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		// the parts of DBGL
		Group partsGroup = new Group(lists, SWT.NONE);
		partsGroup.setText(text.get("dialog.portable.content"));
		partsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		partsGroup.setLayout(new GridLayout(1, false));
		// alpha 48: boxes drawn by DBGL, so that the ones that cannot be changed look greyed out (not "half chosen")
		Table parts = new Table(partsGroup, SWT.BORDER | SWT.V_SCROLL | SWT.FULL_SELECTION);
		Runnable[] onPartChanged = {() -> {}};
		org.dbgl.gui.controls.CheckColumn checks = org.dbgl.gui.controls.CheckColumn.on(parts, () -> onPartChanged[0].run());
		parts.setHeaderVisible(true);
		GridData partsData = new GridData(SWT.FILL, SWT.FILL, true, true);
		partsData.heightHint = 300;
		partsData.widthHint = 440;
		parts.setLayoutData(partsData);
		TableColumn partCol = new TableColumn(parts, SWT.NONE);
		partCol.setText(text.get("dialog.portable.column.part"));
		partCol.setWidth(345); // alpha 48: with room for the box in front
		TableColumn partSizeCol = new TableColumn(parts, SWT.RIGHT);
		partSizeCol.setText(text.get("dialog.portable.column.size"));
		partSizeCol.setWidth(80);
		TableItem gamesItem = new TableItem(parts, SWT.NONE);
		gamesItem.setText(new String[] {text.get("dialog.portable.content.games"), ""});
		TableItem dataItem = new TableItem(parts, SWT.NONE);
		dataItem.setText(new String[] {text.get("dialog.portable.content.data"), ""});
		checks.add(gamesItem, false, true);
		checks.add(dataItem, false, true);
		Label partsHint = new Label(partsGroup, SWT.WRAP);
		partsHint.setText(text.get("dialog.portable.content.hint"));
		GridData hintData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		hintData.widthHint = 440;
		partsHint.setLayoutData(hintData);
		partsHint.setForeground(dim);
		partsHint.setData(DarkTheme.KEEP_FOREGROUND, Boolean.TRUE);

		new Label(dlg, SWT.NONE).setText(text.get("dialog.portable.zip"));
		Text zip = new Text(dlg, SWT.BORDER);
		// alpha 48: with date and time, so that an earlier one stays
		String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", java.util.Locale.ROOT));
		zip.setText(new File(org.dbgl.service.FileLocationService.getInstance().getDataDir(), "DBGL-portable-" + stamp + ".zip").getAbsolutePath());
		zip.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button browse = new Button(dlg, SWT.PUSH);
		browse.setText(text.get("button.browse"));
		browse.addListener(SWT.Selection, e -> {
			FileDialog fd = new FileDialog(dlg, SWT.SAVE);
			fd.setFilterExtensions(new String[] {"*.zip"});
			fd.setFileName(new File(zip.getText()).getName());
			fd.setFilterPath(new File(zip.getText()).getParent());
			fd.setOverwrite(true);
			String f = fd.open();
			if (f != null)
				zip.setText(f.toLowerCase(java.util.Locale.ROOT).endsWith(".zip") ? f: f + ".zip");
		});
		new Label(dlg, SWT.NONE);
		Label space = new Label(dlg, SWT.WRAP);
		space.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		space.setData(DarkTheme.KEEP_FOREGROUND, Boolean.TRUE);

		Button java = new Button(dlg, SWT.CHECK);
		java.setText(text.get("dialog.portable.java"));
		java.setToolTipText(text.get("dialog.portable.java.tooltip"));
		java.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));
		Label javaInfo = new Label(dlg, SWT.WRAP);
		javaInfo.setText(text.get("dialog.portable.java.info"));
		GridData javaInfoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		javaInfoData.widthHint = 820;
		javaInfoData.horizontalIndent = 20;
		javaInfo.setLayoutData(javaInfoData);
		javaInfo.setForeground(dim);
		javaInfo.setData(DarkTheme.KEEP_FOREGROUND, Boolean.TRUE);

		ProgressBar bar = new ProgressBar(dlg, SWT.HORIZONTAL);
		bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		bar.setMaximum(1000);
		Label status = new Label(dlg, SWT.NONE);
		status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 3, 1));
		row.setLayout(new GridLayout(2, false));
		Button start = new Button(row, SWT.PUSH);
		start.setText(text.get("dialog.portable.start"));
		BaseDialog.wideButton(start); // after the text: as wide as it needs
		Button close = new Button(row, SWT.PUSH);
		close.setText(text.get("dialog.portable.stop"));
		BaseDialog.wideButton(close);
		close.setText(text.get("button.cancel"));
		dlg.setDefaultButton(start);

		// sizes, found in the background
		ExecutorService sizer = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "DBGL portable sizes");
			t.setDaemon(true);
			return t;
		});
		dlg.addDisposeListener(e -> sizer.shutdownNow());
		PortableExport helper;
		try {
			helper = new PortableExport(new PortableExport.Options(List.of(), all, null, false), (d, t, w) -> {}, () -> false);
		} catch (java.io.IOException e) {
			Mess_.on(parent).exception(e).warning();
			return;
		}
		Map<Integer, Long> sizes = new HashMap<>(), confSizes = new HashMap<>();
		long[] databaseSize = {helper.databaseSize()};
		Set<Integer> asked = new LinkedHashSet<>();
		Map<File, Long> partSizes = new HashMap<>();
		boolean[] partsKnown = {false}, running = {false}, cancelled = {false};

		Runnable updateTotals = () -> {
			if (dlg.isDisposed())
				return;
			int n = 0;
			long games = 0;
			boolean allKnown = true;
			for (TableItem i: table.getItems()) {
				if (!i.getChecked())
					continue;
				n++;
				Long s = sizes.get(((Profile)i.getData()).getId());
				if (s == null)
					allKnown = false;
				else
					games += s;
			}
			long common = 0;
			for (TableItem i: parts.getItems())
				if (i.getData() instanceof PortableExport.Part part && org.dbgl.gui.controls.CheckColumn.checked(i))
					common += part.bytes();
			// the profiles themselves: their files and the database
			long data = 0;
			if (n > 0) {
				data = databaseSize[0];
				for (TableItem i: table.getItems())
					if (i.getChecked())
						data += confSizes.computeIfAbsent(((Profile)i.getData()).getId(), id -> {
							try {
								return ((Profile)i.getData()).getConfigurationCanonicalFile().length();
							} catch (RuntimeException e) {
								return 0L;
							}
						});
			}
			dataItem.setText(1, n == 0 ? "": FileUtils.byteCountToDisplaySize(data));
			long needed = games + common + data + (java.getSelection() ? JAVA_BYTES: 0);
			String sizeText = (allKnown ? "": "\u2265 ") + FileUtils.byteCountToDisplaySize(games);
			count.setText(text.get("dialog.portable.count2", new Object[] {n, sizeText}));
			gamesItem.setText(1, n == 0 ? "": sizeText);
			boolean locked = n > 0;
			checks.set(gamesItem, locked, true); // cannot be changed
			checks.set(dataItem, locked, true);
			// only chosen profiles show their size
			for (TableItem i: table.getItems()) {
				Long s = sizes.get(((Profile)i.getData()).getId());
				String shown = i.getChecked() && s != null ? FileUtils.byteCountToDisplaySize(s): "";
				if (!shown.equals(i.getText(2)))
					i.setText(2, shown);
			}
			File dir = new File(zip.getText().strip()).getAbsoluteFile().getParentFile();
			long free = (dir != null && dir.isDirectory()) ? dir.getUsableSpace(): -1;
			String neededText = (allKnown && partsKnown[0] ? "": "\u2265 ") + FileUtils.byteCountToDisplaySize(needed);
			if (free < 0) {
				space.setText(text.get("dialog.portable.space.nodir", new Object[] {neededText}));
				space.setForeground(warn);
			} else if (needed > free) {
				space.setText(text.get("dialog.portable.space.toolittle", new Object[] {neededText, FileUtils.byteCountToDisplaySize(free)}));
				space.setForeground(warn);
			} else {
				space.setText(text.get("dialog.portable.space", new Object[] {neededText, FileUtils.byteCountToDisplaySize(free)}));
				space.setForeground(dim);
			}
			count.getParent().layout();
			space.getParent().layout();
		};
		Runnable askSizes = () -> {
			for (TableItem i: table.getItems()) {
				Profile p = (Profile)i.getData();
				if (i.getChecked() && asked.add(p.getId())) {
					sizer.submit(() -> {
						long s = helper.profileSize(p);
						display.asyncExec(() -> {
							if (dlg.isDisposed())
								return;
							sizes.put(p.getId(), s);
							updateTotals.run();
						});
					});
				}
			}
			updateTotals.run();
		};
		sizer.submit(() -> {
			List<PortableExport.Part> found = helper.parts();
			display.asyncExec(() -> {
				if (dlg.isDisposed())
					return;
				for (PortableExport.Part part: found) {
					TableItem item = new TableItem(parts, SWT.NONE);
					item.setText(new String[] {part.file() == null ? text.get("dialog.portable.content.files", new Object[] {part.name()}): part.name(), FileUtils.byteCountToDisplaySize(part.bytes())});
					item.setData(part);
					checks.add(item, part.suggested() || part.required(), part.required());
					if (part.required())
						item.setForeground(dim);
					if (part.file() != null)
						partSizes.put(part.file(), part.bytes());
				}
				partsKnown[0] = true;
				updateTotals.run();
			});
		});

		// the games, the data and what DBGL needs to run cannot be left out
		onPartChanged[0] = updateTotals; // the locked boxes cannot be clicked
		gamesItem.setForeground(dim);
		dataItem.setForeground(dim);
		allButton.addListener(SWT.Selection, e -> {
			for (TableItem i: table.getItems())
				i.setChecked(true);
			askSizes.run();
		});
		noneButton.addListener(SWT.Selection, e -> {
			for (TableItem i: table.getItems())
				i.setChecked(false);
			askSizes.run();
		});
		table.addListener(SWT.Selection, e -> askSizes.run());
		zip.addListener(SWT.Modify, e -> updateTotals.run());
		java.addListener(SWT.Selection, e -> updateTotals.run());

		close.addListener(SWT.Selection, e -> {
			if (running[0])
				cancelled[0] = true;
			else
				dlg.close();
		});
		dlg.addListener(SWT.Close, e -> {
			if (running[0]) {
				cancelled[0] = true;
				e.doit = false;
			}
		});
		start.addListener(SWT.Selection, e -> {
			List<Profile> chosenProfiles = new ArrayList<>();
			for (TableItem i: table.getItems())
				if (i.getChecked())
					chosenProfiles.add((Profile)i.getData());
			if (chosenProfiles.isEmpty()) {
				Mess_.on(dlg).key("dialog.portable.none").warning();
				return;
			}
			File target = new File(zip.getText().strip()).getAbsoluteFile();
			if (target.getParentFile() == null || !target.getParentFile().isDirectory()) {
				Mess_.on(dlg).key("dialog.portable.nozip").warning();
				return;
			}
			if (space.getForeground().equals(warn) && !Mess_.on(dlg).key("dialog.portable.space.confirm").confirm())
				return;
			Set<File> excluded = new LinkedHashSet<>();
			for (TableItem i: parts.getItems())
				if (i.getData() instanceof PortableExport.Part part && part.file() != null && !org.dbgl.gui.controls.CheckColumn.checked(i))
					excluded.add(part.file());
			running[0] = true;
			cancelled[0] = false;
			start.setEnabled(false);
			table.setEnabled(false);
			parts.setEnabled(false);
			close.setText(text.get("dialog.portable.stop"));
			PortableExport.Options options = new PortableExport.Options(chosenProfiles, all, target, java.getSelection(), excluded);
			Thread worker = new Thread(() -> {
				Object outcome;
				try {
					outcome = new PortableExport(options, (done, total, what) -> display.asyncExec(() -> {
						if (!bar.isDisposed()) {
							bar.setSelection(total <= 0 ? 0: (int)Math.min(1000, done * 1000 / total));
							status.setText(what.equals("java") ? text.get("dialog.portable.downloadingjava"): what);
						}
					}), () -> cancelled[0]).run();
				} catch (InterruptedException ex) {
					outcome = null;
				} catch (Exception ex) {
					outcome = ex;
				}
				Object result = outcome;
				display.asyncExec(() -> {
					if (dlg.isDisposed())
						return;
					running[0] = false;
					if (result instanceof PortableExport.Result r) {
						new ShortcutsDialog(dlg, text.get("dialog.portable.title"), report(text, r, target), ImageService.IMG_USB).open();
						dlg.close();
					} else {
						if (result instanceof Exception ex)
							Mess_.on(dlg).exception(ex).warning();
						start.setEnabled(true);
						table.setEnabled(true);
						parts.setEnabled(true);
						close.setText(text.get("button.cancel"));
						bar.setSelection(0);
						status.setText("");
					}
				});
			}, "DBGL portable");
			worker.setDaemon(true);
			worker.start();
		});

		askSizes.run();
		DarkTheme.apply(dlg);
		dlg.pack();
		Rectangle area = parent.getMonitor().getClientArea(), p = parent.getBounds();
		Point size = dlg.getSize();
		size.x = Math.min(size.x, area.width);
		size.y = Math.min(size.y, area.height);
		dlg.setSize(size);
		dlg.setLocation(Math.max(area.x, p.x + (p.width - size.x) / 2), Math.max(area.y, p.y + (p.height - size.y) / 2));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
	}

	private static int countChecked(Table table) {
		int n = 0;
		for (TableItem i: table.getItems())
			if (i.getChecked())
				n++;
		return n;
	}

	/**
	 * what went into the ZIP file, in the form of the window of the keys (alpha 48): the result, a line per profile (ID and size,
	 * title), what to do with it, what is missing
	 */
	static String report(ITextService text, PortableExport.Result r, File target) {
		StringBuilder sb = new StringBuilder();
		sb.append(text.get("dialog.portable.report.result")).append('\n');
		sb.append(text.get("dialog.portable.report.profiles")).append('\t').append(r.profiles()).append('\n');
		sb.append(text.get("dialog.portable.zip")).append('\t').append(target.getPath()).append('\n');
		sb.append(text.get("dialog.portable.column.size")).append('\t').append(FileUtils.byteCountToDisplaySize(r.bytes())).append("\n\n");
		sb.append(text.get("dialog.portable.report.transferred")).append('\n');
		for (PortableExport.Transferred t: r.transferred())
			sb.append("ID ").append(t.id()).append("  \u00b7  ").append(FileUtils.byteCountToDisplaySize(t.bytes())).append('\t').append(t.title().replace('\t', ' ').replace('\n', ' ')).append('\n');
		sb.append('\n').append(text.get("dialog.portable.report.next")).append('\n').append(text.get("dialog.portable.next")).append('\n');
		if (!r.notes().isEmpty()) {
			sb.append('\n').append(text.get("dialog.portable.report.notes")).append('\n');
			r.notes().forEach(n -> sb.append(n.replace('\t', ' ').replace('\n', ' ')).append('\n'));
		}
		return sb.toString().replace("\r\n", "\n").trim();
	}
}
