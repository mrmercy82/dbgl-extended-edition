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
		boolean gridDb = (Boolean)choice[3];

		List<SteamShortcuts.Game> games = new ArrayList<>();
		for (Profile p: profiles)
			games.add(viaDbgl ? gameViaDbgl(p): gameOf(p));
		java.io.File tmp = null;
		try {
			// alpha 52: banner, background and logo (and a cover where the profile has none) from SteamGridDB, fetched first
			java.util.Map<SteamShortcuts.Game, java.util.Map<String, java.io.File>> art = new java.util.HashMap<>();
			int[] missing = {0};
			if (gridDb) {
				java.io.File dir = java.nio.file.Files.createTempDirectory("dbgl-steamart").toFile();
				tmp = dir;
				java.util.concurrent.atomic.AtomicInteger progress = new java.util.concurrent.atomic.AtomicInteger();
				try {
					org.dbgl.gui.controls.BusyTask.run(shell, text.get("dialog.steam.griddb.fetching"), progress, games.size(), () -> {
						org.dbgl.util.searchengine.SteamGridDbSearchEngine engine = (org.dbgl.util.searchengine.SteamGridDbSearchEngine)org.dbgl.util.searchengine.SteamGridDbSearchEngine.getInstance();
						for (int i = 0; i < games.size(); i++) {
							SteamShortcuts.Game g = games.get(i);
							try {
								java.util.Map<String, String> urls = engine.steamArt(g.name());
								java.io.File own = new java.io.File(dir, String.valueOf(i));
								java.nio.file.Files.createDirectories(own.toPath());
								java.util.Map<String, java.io.File> files = new java.util.LinkedHashMap<>();
								for (java.util.Map.Entry<String, String> u: urls.entrySet())
									files.put(u.getKey(), org.dbgl.util.searchengine.SteamGridDbSearchEngine.download(u.getValue(), own, u.getKey()));
								if (files.isEmpty())
									missing[0]++;
								art.put(g, files);
							} catch (java.io.IOException e) {
								missing[0]++;
							}
							progress.incrementAndGet();
						}
						return null;
					});
				} catch (org.dbgl.gui.controls.BusyTask.Cancelled e) {
					return;
				}
			}
			SteamShortcuts.Result result = SteamShortcuts.add(user, games, pictures, art);
			Mess_.on(shell).key(gridDb && (missing[0] > 0) ? "dialog.steam.done.griddb": "dialog.steam.done", new Object[] {result.added(), result.updated(), missing[0]}).display();
		} catch (Exception e) {
			Mess_.on(shell).exception(e).warning();
		} finally {
			if (tmp != null)
				org.apache.commons.io.FileUtils.deleteQuietly(tmp);
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
		if (org.dbgl.util.Scummvm.is(db)) { // alpha 52: ScummVM with the game
			StringBuilder args = new StringBuilder();
			for (String arg: org.dbgl.util.Scummvm.arguments(profile, db))
				args.append(args.length() > 0 ? " ": "").append(arg.contains(" ") ? "\"" + arg + "\"": arg);
			return new SteamShortcuts.Game(profile.getTitle(), db.getCanonicalExecutable(), db.getCanonicalExecutable().getParentFile(), args.toString(), coverOf(profile));
		}
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
		// alpha 52: the pictures of the Steam library from SteamGridDB (needs its key, as for the online infos)
		boolean hasKey = ((org.dbgl.util.searchengine.SteamGridDbSearchEngine)org.dbgl.util.searchengine.SteamGridDbSearchEngine.getInstance()).hasKey();
		Button gridDb = new Button(dlg, SWT.CHECK);
		gridDb.setText(text.get(hasKey ? "dialog.steam.griddb": "dialog.steam.griddb.nokey"));
		gridDb.setToolTipText(text.get("dialog.steam.griddb.tooltip"));
		gridDb.setEnabled(hasKey);
		gridDb.setSelection(hasKey && org.dbgl.service.SettingsService.getInstance().getBooleanValue("steam", "griddb"));
		gridDb.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));

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
			result[0] = new Object[] {users.get(Math.max(0, user.getSelectionIndex())), pictures.getSelection(), viaDbgl.getSelection(), gridDb.getSelection()};
			if (hasKey)
				org.dbgl.service.SettingsService.getInstance().setBooleanValue("steam", "griddb", gridDb.getSelection());
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
