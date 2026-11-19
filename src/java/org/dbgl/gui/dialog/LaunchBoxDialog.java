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
import java.util.Locale;
import org.apache.commons.io.FilenameUtils;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.FileLocation;
import org.dbgl.model.Link;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ITextService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.LaunchBoxExport;
import org.dbgl.util.SteamShortcuts;
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


/** alpha 52: the chosen profiles as games in LaunchBox (platform MS-DOS), see LaunchBoxExport */
public final class LaunchBoxDialog {

	private LaunchBoxDialog() {
	}

	/** asks for the LaunchBox folder and the options and adds the profiles; messages for the user on the shell */
	public static void addToLaunchBox(Shell shell, List<Profile> profiles) {
		ITextService text = TextService.getInstance();
		SettingsService settings = SettingsService.getInstance();
		DirectoryDialog chooser = new DirectoryDialog(shell);
		chooser.setMessage(text.get("dialog.launchboxexport.choose"));
		String last = settings.hasValue("gui", "launchboxdir") ? settings.getValue("gui", "launchboxdir"): null;
		if (last != null && !last.isBlank() && new File(last).isDirectory())
			chooser.setFilterPath(last);
		String dir = chooser.open();
		if (dir == null)
			return;
		File root = LaunchBoxExport.rootOf(new File(dir));
		if (root == null) {
			Mess_.on(shell).key("dialog.launchboximport.none", new Object[] {dir}).warning();
			return;
		}
		settings.setValue("gui", "launchboxdir", root.getPath());
		if (LaunchBoxExport.platformFile(root) == null) {
			Mess_.on(shell).key("dialog.launchboxexport.noplatform", new Object[] {root.getPath()}).warning();
			return;
		}
		while (LaunchBoxExport.launchBoxRunning()) {
			if (!Mess_.on(shell).key("dialog.launchboxexport.running").okCancel())
				return;
		}
		boolean[] choice = ask(shell, text, profiles.size());
		if (choice == null)
			return;
		List<LaunchBoxExport.Entry> entries = new ArrayList<>();
		for (Profile p: profiles)
			entries.add(entryOf(p, choice[1]));
		try {
			LaunchBoxExport.Result result = LaunchBoxExport.add(root, entries, choice[0]);
			Mess_.on(shell).key("dialog.launchboxexport.done", new Object[] {result.added(), result.already()}).display();
		} catch (java.io.IOException | RuntimeException e) {
			Mess_.on(shell).exception(e).warning();
		}
	}

	/** the profile as LaunchBox starts it: through DBGL ("--run", the play time is counted) or DOSBox directly */
	static LaunchBoxExport.Entry entryOf(Profile p, boolean viaDbgl) {
		SteamShortcuts.Game g = viaDbgl ? SteamDialog.gameViaDbgl(p): SteamDialog.gameOf(p);
		File cover = g.cover();
		List<File> screenshots = new ArrayList<>();
		File[] images = p.getCanonicalCaptures().listFiles(f -> f.isFile()
				&& List.of("png", "jpg", "jpeg", "gif", "bmp").contains(FilenameUtils.getExtension(f.getName()).toLowerCase(Locale.ROOT)));
		if (images != null) {
			java.util.Arrays.sort(images);
			for (File f: images)
				if (!f.equals(cover))
					screenshots.add(f);
		}
		return new LaunchBoxExport.Entry(p.getTitle(), g.exe(), g.startDir(), g.launchOptions(), p.getDeveloper(), p.getPublisher(), p.getYear(), p.getGenre(), LaunchBoxExport.notesFor(p.getNotes()),
				manualOf(p), cover, screenshots);
	}

	/** the first link of the profile to a PDF on this computer, or null */
	static File manualOf(Profile p) {
		for (Link link: p.getLinks()) {
			String d = link.getDestination();
			if (d == null || d.isBlank() || d.contains("://") || !d.toLowerCase(Locale.ROOT).endsWith(".pdf"))
				continue;
			File f = new FileLocation(d, FileLocationService.getInstance().dataRelative()).getCanonicalFile();
			if (f.isFile())
				return f;
		}
		return null;
	}

	/** @return {pictures, through DBGL}, or null */
	private static boolean[] ask(Shell parent, ITextService text, int count) {
		Display display = parent.getDisplay();
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		dlg.setText(text.get("dialog.launchboxexport.title"));
		BaseDialog.inheritWindowImage(dlg, parent);
		dlg.setLayout(new GridLayout(1, false));
		if (DarkTheme.forced())
			dlg.setBackground(DarkTheme.dialogBackground);

		Label info = new Label(dlg, SWT.WRAP);
		info.setText(text.get("dialog.launchboxexport.info", new Object[] {count}));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		infoData.widthHint = 440;
		info.setLayoutData(infoData);
		Button pictures = new Button(dlg, SWT.CHECK);
		pictures.setText(text.get("dialog.launchboxexport.pictures"));
		pictures.setSelection(true);
		Button viaDbgl = new Button(dlg, SWT.CHECK);
		viaDbgl.setText(text.get("dialog.steam.viadbgl"));
		viaDbgl.setToolTipText(text.get("dialog.launchboxexport.viadbgl.tooltip"));
		viaDbgl.setSelection(true);

		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
		row.setLayout(new GridLayout(2, true));
		Button ok = BaseDialog.wideButton(new Button(row, SWT.PUSH));
		ok.setText(text.get("button.ok"));
		Button cancel = BaseDialog.wideButton(new Button(row, SWT.PUSH));
		cancel.setText(text.get("button.cancel"));
		dlg.setDefaultButton(ok);
		boolean[][] result = new boolean[1][];
		ok.addListener(SWT.Selection, e -> {
			result[0] = new boolean[] {pictures.getSelection(), viaDbgl.getSelection()};
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
}
