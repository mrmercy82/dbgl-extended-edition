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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.service.BackupService;
import org.dbgl.service.ITextService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.FullBackup;
import org.dbgl.util.FullBackup.Item;
import org.dbgl.util.FullBackup.Part;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


/**
 * Asked before an update is installed: whether everything is backed up first, also the DOSBox versions, the program folder,
 * the captures and the games (see {@link FullBackup}). What DBGL keeps about the games is always in it; the update backs it up
 * anyway. The sizes are shown as they are found out; what was chosen is remembered.
 */
public final class UpdateBackupQuestion {

	/** BACKED_UP: the backup was written (see {@link #lastFile()}); SKIP: on without it; CANCEL: no update */
	public enum Answer {
		BACKED_UP, SKIP, CANCEL
	}

	private static File lastFile_;

	private static final Map<Part, String> LABELS = Map.of(Part.DOSBOX, "dialog.updatebackup.dosbox", Part.PROGRAM, "dialog.updatebackup.program2", Part.CAPTURES,
		"dialog.updatebackup.captures", Part.BACKUPS, "dialog.updatebackup.backups", Part.GAMES, "dialog.updatebackup.games");

	private UpdateBackupQuestion() {
	}

	/** the backup written by the last {@link #ask} that answered BACKED_UP */
	public static File lastFile() {
		return lastFile_;
	}

	/**
	 * @param manual the update is downloaded and installed by the user (the download page is opened next): nothing else is
	 *               backed up then
	 */
	public static Answer ask(Shell shell, List<DosboxVersion> versions, File programDir, String fromVersion, boolean manual) {
		ITextService t = TextService.getInstance();
		SettingsService settings = SettingsService.getInstance();
		List<Item> all = FullBackup.items(versions, programDir);
		Display display = shell.getDisplay();
		Shell dlg = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		dlg.setText(t.get("dialog.updatebackup.title"));
		BaseDialog.inheritWindowImage(dlg, shell);
		dlg.setLayout(new GridLayout(3, false));

		Label intro = new Label(dlg, SWT.WRAP);
		intro.setText(t.get(manual ? "dialog.updatebackup.intro.manual": "dialog.updatebackup.intro"));
		GridData introData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		introData.widthHint = 600;
		intro.setLayoutData(introData);

		Button data = new Button(dlg, SWT.CHECK);
		data.setText(t.get("dialog.updatebackup.data2"));
		data.setSelection(true);
		data.setEnabled(false); // always: it is what "Restore" brings back
		data.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));

		Set<Part> remembered = FullBackup.remembered();
		Map<Part, Button> checks = new EnumMap<>(Part.class);
		Map<Part, Label> sizes = new EnumMap<>(Part.class);
		Map<Part, List<Item>> itemsOf = new EnumMap<>(Part.class);
		for (Part p: Part.values()) {
			List<Item> items = all.stream().filter(i -> i.part() == p).toList();
			if (items.isEmpty() || (p == Part.DATA))
				continue; // DATA: always in it, with the database
			itemsOf.put(p, items);
			Button check = new Button(dlg, SWT.CHECK);
			check.setText(t.get(LABELS.get(p), new Object[] {versions.size()}));
			check.setSelection(remembered.contains(p));
			StringBuilder tip = new StringBuilder();
			items.forEach(i -> tip.append(i.file().getPath()).append('\n'));
			check.setToolTipText(tip.toString().trim());
			check.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
			Label size = new Label(dlg, SWT.RIGHT);
			size.setText(t.get("dialog.updatebackup.calculating"));
			GridData sd = new GridData(SWT.END, SWT.CENTER, true, false);
			sd.widthHint = 110;
			size.setLayoutData(sd);
			checks.put(p, check);
			sizes.put(p, size);
		}

		Label folderLabel = new Label(dlg, SWT.NONE);
		folderLabel.setText(t.get("dialog.updatebackup.folder"));
		Text folder = new Text(dlg, SWT.BORDER);
		String dirSetting = settings.getValue(FullBackup.SECTION, "backupdir");
		folder.setText(org.apache.commons.lang3.StringUtils.isBlank(dirSetting) ? BackupService.defaultAutoBackupDir().getPath(): dirSetting);
		folder.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button change = new Button(dlg, SWT.PUSH);
		change.setText(t.get("dialog.updatebackup.change"));
		change.addListener(SWT.Selection, e -> {
			DirectoryDialog dd = new DirectoryDialog(dlg);
			dd.setFilterPath(folder.getText());
			String chosen = dd.open();
			if (chosen != null)
				folder.setText(chosen);
		});
		Label total = new Label(dlg, SWT.NONE);
		total.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

		Composite buttons = new Composite(dlg, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.END, SWT.END, true, false, 3, 1));
		buttons.setLayout(new GridLayout(3, false));
		Answer[] answer = {Answer.CANCEL};
		Set<Part> chosen = EnumSet.of(Part.DATA);
		String[] dir = {null};
		String[] keys = {manual ? "dialog.updatebackup.backup.manual": "dialog.updatebackup.backup", "dialog.updatebackup.skip", "button.cancel"};
		Answer[] answers = {Answer.BACKED_UP, Answer.SKIP, Answer.CANCEL};
		for (int i = 0; i < keys.length; i++) {
			Answer a = answers[i];
			Button button = new Button(buttons, SWT.PUSH);
			button.setText(t.get(keys[i]));
			GridData bd = new GridData(SWT.FILL, SWT.CENTER, false, false);
			bd.widthHint = Math.max(80, button.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
			button.setLayoutData(bd);
			button.addListener(SWT.Selection, e -> {
				answer[0] = a;
				checks.forEach((p, c) -> {
					if (c.getSelection())
						chosen.add(p);
				});
				dir[0] = folder.getText().trim();
				dlg.close();
			});
			if (a == Answer.BACKED_UP)
				dlg.setDefaultButton(button);
		}

		// the sizes: found out in the background (the games can take a moment)
		long[] partSize = new long[Part.values().length];
		boolean[] known = {false};
		Runnable showTotal = () -> {
			if (total.isDisposed())
				return;
			long sum = 0;
			for (Map.Entry<Part, Button> c: checks.entrySet())
				if (c.getValue().getSelection())
					sum += partSize[c.getKey().ordinal()];
			File target = new File(folder.getText().trim());
			File existing = target;
			while ((existing != null) && !existing.isDirectory())
				existing = existing.getParentFile();
			String free = existing != null ? FullBackup.readable(existing.getUsableSpace()): "?";
			total.setText(known[0] ? t.get("dialog.updatebackup.total", new Object[] {FullBackup.readable(sum), free}): t.get("dialog.updatebackup.calculating"));
			total.getParent().layout(new org.eclipse.swt.widgets.Control[] {total});
		};
		checks.values().forEach(c -> c.addListener(SWT.Selection, e -> showTotal.run()));
		folder.addListener(SWT.Modify, e -> showTotal.run());
		Thread sizer = new Thread(() -> {
			for (Map.Entry<Part, List<Item>> e: itemsOf.entrySet()) {
				long s = FullBackup.size(e.getValue(), all, null);
				partSize[e.getKey().ordinal()] = s;
				if (display.isDisposed())
					return;
				display.asyncExec(() -> {
					Label l = sizes.get(e.getKey());
					if ((l != null) && !l.isDisposed())
						l.setText(FullBackup.readable(s));
				});
			}
			known[0] = true;
			if (!display.isDisposed())
				display.asyncExec(showTotal);
		}, "UpdateBackupSizes");
		sizer.setDaemon(true);
		sizer.start();
		showTotal.run();

		dlg.pack();
		Rectangle area = shell.getMonitor().getClientArea();
		org.eclipse.swt.graphics.Point size = dlg.getSize();
		size.x = Math.min(size.x, area.width);
		size.y = Math.min(size.y, area.height);
		dlg.setSize(size);
		Rectangle parent = shell.getBounds();
		dlg.setLocation(Math.max(area.x, Math.min(parent.x + (parent.width - size.x) / 2, area.x + area.width - size.x)),
			Math.max(area.y, Math.min(parent.y + (parent.height - size.y) / 2, area.y + area.height - size.y)));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		sizer.interrupt();

		if (answer[0] != Answer.BACKED_UP)
			return answer[0];
		FullBackup.remember(chosen);
		settings.setValue(FullBackup.SECTION, "backupdir", dir[0]);
		return write(shell, all, chosen, new File(dir[0]), fromVersion);
	}

	private static Answer write(Shell shell, List<Item> all, Set<Part> parts, File dir, String fromVersion) {
		ITextService t = TextService.getInstance();
		List<Item> chosen = new ArrayList<>(all.stream().filter(i -> parts.contains(i.part())).toList());
		String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
		java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
		File zip = new File(dir, FullBackup.PREFIX + fromVersion.replaceAll("[^0-9A-Za-z.]+", "") + "-" + stamp + ".zip");
		try {
			java.nio.file.Files.createDirectories(dir.toPath());
			// found out in the background (the games can take a while), with a window to cancel
			long needed = BusyTask.run(shell, t.get("dialog.updatebackup.calculating"), () -> FullBackup.size(chosen, all, zip));
			if ((needed > dir.getUsableSpace())
					&& !Mess_.on(shell).key("dialog.updatebackup.nospace", new Object[] {dir.getPath(), FullBackup.readable(needed), FullBackup.readable(dir.getUsableSpace())}).confirm())
				return Answer.CANCEL;
			AtomicInteger progress = new AtomicInteger();
			BusyTask.run(shell, t.get("dialog.updatebackup.writing", new Object[] {zip.getName()}), progress, 1000,
				() -> FullBackup.write(zip, chosen, all, fromVersion, progress, stop::get));
			lastFile_ = zip;
			System.out.println("Backup before the update: " + zip);
			return Answer.BACKED_UP;
		} catch (BusyTask.Cancelled e) {
			stop.set(true); // the work stops at the next file and removes its half written file
			return Answer.CANCEL;
		} catch (Exception e) {
			Mess_.on(shell).key("dialog.updatebackup.failed", new Object[] {String.valueOf(e.getMessage() == null ? e: e.getMessage())}).exception(e).warning();
			return Answer.CANCEL;
		}
	}
}
