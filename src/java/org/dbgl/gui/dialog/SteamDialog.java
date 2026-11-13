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
import org.apache.commons.io.FilenameUtils;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ITextService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.SteamShortcuts;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;


/** Alpha 48: the chosen profiles as non-Steam games in Steam (library, Big Picture, Steam Deck) */
public final class SteamDialog {

	private SteamDialog() {
	}

	/** asks for the Steam user and adds the profiles; messages for the user on the shell */
	public static void addToSteam(Shell shell, List<Profile> profiles) {
		ITextService text = TextService.getInstance();
		File steam = SteamShortcuts.steamDir();
		if (steam == null) {
			Mess_.on(shell).key("dialog.steam.notfound").warning();
			return;
		}
		List<SteamShortcuts.User> users = SteamShortcuts.users(steam);
		if (users.isEmpty()) {
			Mess_.on(shell).key("dialog.steam.nousers", new Object[] {steam.getPath()}).warning();
			return;
		}
		while (SteamShortcuts.steamRunning()) {
			if (!Mess_.on(shell).key("dialog.steam.running").okCancel())
				return;
		}
		Object[] choice = ask(shell, text, profiles.size(), users);
		if (choice == null)
			return;
		SteamShortcuts.User user = (SteamShortcuts.User)choice[0];
		boolean pictures = (Boolean)choice[1];
		boolean viaDbgl = (Boolean)choice[2];

		List<SteamShortcuts.Game> games = new ArrayList<>();
		for (Profile p: profiles)
			games.add(viaDbgl ? gameViaDbgl(p): gameOf(p));
		try {
			SteamShortcuts.Result result = SteamShortcuts.add(user, games, pictures);
			Mess_.on(shell).key("dialog.steam.done", new Object[] {result.added(), result.updated()}).display();
		} catch (java.io.IOException | RuntimeException e) {
			Mess_.on(shell).exception(e).warning();
		}
	}

	/** DBGL with "--run": the play time is counted, Steam shows the game as played until it ends (alpha 48) */
	static SteamShortcuts.Game gameViaDbgl(Profile profile) {
		List<String> cmd = org.dbgl.gui.RunProfile.command(profile.getId());
		StringBuilder options = new StringBuilder();
		for (String arg: cmd.subList(1, cmd.size())) {
			if (options.length() > 0)
				options.append(' ');
			options.append(arg.contains(" ") ? "\"" + arg + "\"": arg);
		}
		return new SteamShortcuts.Game(profile.getTitle(), new File(cmd.get(0)), org.dbgl.gui.RunProfile.startDir(), options.toString(), coverOf(profile));
	}

	/** DOSBox with the configuration(s) of the profile, started in the DOSroot, as for a shortcut on the desktop */
	static SteamShortcuts.Game gameOf(Profile profile) {
		DosboxVersion db = profile.getDosboxVersion();
		StringBuilder options = new StringBuilder();
		if (db.isMultiConfig())
			options.append("-conf \"").append(db.getConfigurationCanonicalFile()).append("\" ");
		options.append("-conf \"").append(profile.getConfigurationCanonicalFile()).append('"');
		if (SettingsService.getInstance().getBooleanValue("dosbox", "hideconsole"))
			options.append(" -noconsole");
		if (org.dbgl.util.ExecuteUtils.usesOwnSavestates(db))
			options.append(" -savedir \"").append(org.dbgl.util.ExecuteUtils.savestatesDir(profile)).append('"');
		return new SteamShortcuts.Game(profile.getTitle(), db.getCanonicalExecutable(), FileLocationService.getInstance().getDosroot(), options.toString(), coverOf(profile));
	}

	/** the cover of the profile if there is one, else its first picture, or null */
	public static File coverOf(Profile profile) {
		File[] images = profile.getCanonicalCaptures().listFiles(f -> f.isFile()
				&& List.of("png", "jpg", "jpeg").contains(FilenameUtils.getExtension(f.getName()).toLowerCase(java.util.Locale.ROOT)));
		if (images == null || images.length == 0)
			return null;
		java.util.Arrays.sort(images);
		for (File f: images) // a cover of an import ("cover.jpg") or of the online archives ("000-...") first
			if (FilenameUtils.getBaseName(f.getName()).equalsIgnoreCase("cover") || f.getName().matches("\\d{3}-.*"))
				return f;
		return images[0];
	}

	private static Object[] ask(Shell parent, ITextService text, int count, List<SteamShortcuts.User> users) {
		Display display = parent.getDisplay();
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		dlg.setText(text.get("dialog.steam.title"));
		BaseDialog.inheritWindowImage(dlg, parent);
		dlg.setLayout(new GridLayout(2, false));
		if (DarkTheme.forced())
			dlg.setBackground(DarkTheme.dialogBackground);

		Label info = new Label(dlg, SWT.WRAP);
		info.setText(text.get("dialog.steam.info", new Object[] {count}));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		infoData.widthHint = 420;
		info.setLayoutData(infoData);
		new Label(dlg, SWT.NONE).setText(text.get("dialog.steam.user"));
		Combo user = new Combo(dlg, SWT.READ_ONLY);
		users.forEach(u -> user.add(u.toString()));
		user.select(0);
		user.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button pictures = new Button(dlg, SWT.CHECK);
		pictures.setText(text.get("dialog.steam.pictures"));
		pictures.setSelection(true);
		pictures.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		Button viaDbgl = new Button(dlg, SWT.CHECK);
		viaDbgl.setText(text.get("dialog.steam.viadbgl"));
		viaDbgl.setToolTipText(text.get("dialog.steam.viadbgl.tooltip"));
		viaDbgl.setSelection(true);
		viaDbgl.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));

		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
		row.setLayout(new GridLayout(2, true));
		Button ok = BaseDialog.wideButton(new Button(row, SWT.PUSH));
		ok.setText(text.get("button.ok"));
		Button cancel = BaseDialog.wideButton(new Button(row, SWT.PUSH));
		cancel.setText(text.get("button.cancel"));
		dlg.setDefaultButton(ok);
		Object[][] result = new Object[1][];
		ok.addListener(SWT.Selection, e -> {
			result[0] = new Object[] {users.get(Math.max(0, user.getSelectionIndex())), pictures.getSelection(), viaDbgl.getSelection()};
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
