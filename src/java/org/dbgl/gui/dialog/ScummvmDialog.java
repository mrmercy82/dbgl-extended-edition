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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.factory.DosboxVersionFactory;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.dbgl.util.Scummvm;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/** alpha 52: the games of ScummVM (its scummvm.ini) as profiles of the version "ScummVM", which is entered when there is none */
public final class ScummvmDialog {

	/** what the import did: the ScummVM version (new or not), how many profiles were added */
	public record Result(DosboxVersion version, boolean newVersion, int added) {
	}

	private ScummvmDialog() {
	}

	/** @return null if nothing was done */
	public static Result importGames(Shell shell, List<DosboxVersion> versions) {
		ITextService text = TextService.getInstance();
		DosboxVersion version = versions.stream().filter(Scummvm::is).findFirst().orElse(null);
		boolean newVersion = false;
		try {
			if (version == null) {
				File exe = Scummvm.findExecutable();
				if (exe == null) {
					FileDialog dialog = new FileDialog(shell, SWT.OPEN);
					dialog.setText(text.get("dialog.scummvmimport.chooseexe"));
					String chosen = dialog.open();
					if (chosen == null)
						return null;
					exe = new File(chosen);
					if (exe.isDirectory() && exe.getName().toLowerCase(Locale.ROOT).endsWith(".app"))
						exe = new File(exe, "Contents/MacOS/scummvm");
				}
				if (!exe.isFile() || !Scummvm.isExecutable(exe)) {
					Mess_.on(shell).key("dialog.scummvmimport.notscummvm", new Object[] {exe.getPath()}).warning();
					return null;
				}
				File conf = Scummvm.versionConf(FileLocationService.getInstance().getDataDir());
				version = new DosboxVersionRepository().add(DosboxVersionFactory.create(Scummvm.FAMILY, Scummvm.FAMILY, "", false, false, false, null, exe.getParent(), exe.getPath(), "",
					conf.getPath()));
				newVersion = true;
			}

			// a portable ScummVM keeps scummvm.ini next to itself, an installed one in the folder of the user
			File ini = new File(version.getCanonicalExecutable().getParentFile(), "scummvm.ini");
			if (!ini.isFile())
				ini = Scummvm.defaultIni();
			if ((ini == null) || !ini.isFile()) {
				FileDialog dialog = new FileDialog(shell, SWT.OPEN);
				dialog.setText(text.get("dialog.scummvmimport.chooseini"));
				dialog.setFilterExtensions(new String[] {"*.ini", "*"});
				String chosen = dialog.open();
				if (chosen == null)
					return newVersion ? new Result(version, true, 0): null;
				ini = new File(chosen);
			}
			List<Scummvm.Game> games = Scummvm.readIni(ini);
			if (games.isEmpty()) {
				Mess_.on(shell).key("dialog.scummvmimport.nogames", new Object[] {ini.getPath()}).display();
				return newVersion ? new Result(version, true, 0): null;
			}
			Set<String> known = knownTargets(versions, version);
			boolean[] chosen = choose(shell, text, games, known, newVersion ? version.getCanonicalExecutable().getPath(): null);
			if (chosen == null)
				return newVersion ? new Result(version, true, 0): null;
			int added = 0;
			for (int i = 0; i < games.size(); i++)
				if (chosen[i]) {
					new ProfileRepository().add(profileOf(games.get(i), ini, version));
					added++;
				}
			return new Result(version, newVersion, added);
		} catch (IOException | java.sql.SQLException | RuntimeException e) {
			Mess_.on(shell).exception(e).warning();
			return newVersion ? new Result(version, true, 0): null;
		}
	}

	/** the targets the profiles of ScummVM have already, in lower case */
	static Set<String> knownTargets(List<DosboxVersion> versions, DosboxVersion scummvm) throws java.sql.SQLException {
		Set<String> result = new HashSet<>();
		List<DosboxVersion> all = new ArrayList<>(versions);
		if (!all.contains(scummvm))
			all.add(scummvm);
		for (Profile p: new ProfileRepository().list("", "", all))
			if (Scummvm.is(p.getDosboxVersion())) {
				String t = Scummvm.targetOf(p);
				if (t != null)
					result.add(t.toLowerCase(Locale.ROOT));
			}
		return result;
	}

	static Profile profileOf(Scummvm.Game g, File ini, DosboxVersion version) throws IOException {
		Profile p = ProfileFactory.create(version, null);
		p.setTitle(g.title());
		p.setDeveloper("");
		p.setPublisher("");
		p.setGenre("");
		p.setYear("");
		p.setStatus(Scummvm.FAMILY); // marked in the list (the column of the DOSBox version is not shown by default)
		p.setNotes(Scummvm.FAMILY + ": " + g.target() + (g.path() == null ? "": " (" + g.path().getPath() + ")"));
		p.setSetupFileLocation("");
		p.setSetupParams("");
		p.setAltExeFileLocations(new String[] {"", ""});
		p.setAltExeParams(new String[] {"", ""});
		p.loadConfigurationData(TextService.getInstance(), Scummvm.confOf(g, ini), ini, ini.getParentFile());
		return p;
	}

	private static boolean[] choose(Shell parent, ITextService text, List<Scummvm.Game> games, Set<String> known, String newExe) {
		Display display = parent.getDisplay();
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
		dlg.setText(text.get("dialog.scummvmimport.title"));
		BaseDialog.inheritWindowImage(dlg, parent);
		dlg.setLayout(new GridLayout(1, false));
		if (DarkTheme.forced())
			dlg.setBackground(DarkTheme.dialogBackground);

		int already = (int)games.stream().filter(g -> known.contains(g.target().toLowerCase(Locale.ROOT))).count();
		Label info = new Label(dlg, SWT.WRAP);
		String infoText = text.get("dialog.scummvmimport.info", new Object[] {games.size(), already});
		if (newExe != null)
			infoText = text.get("dialog.scummvmimport.versionadded", new Object[] {newExe}) + "\n\n" + infoText;
		info.setText(infoText);
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		infoData.widthHint = 700;
		info.setLayoutData(infoData);

		Table table = new Table(dlg, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
		table.setHeaderVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = 320;
		table.setLayoutData(tableData);
		String[] keys = {"dialog.storeimport.column.game", "dialog.scummvmimport.column.target", "dialog.scummvmimport.column.kind", "dialog.storeimport.column.folder"};
		int[] widths = {300, 110, 150, 260};
		for (int i = 0; i < keys.length; i++) {
			TableColumn col = new TableColumn(table, SWT.NONE);
			col.setText(text.get(keys[i]));
			col.setWidth(widths[i]);
		}
		for (Scummvm.Game g: games) {
			boolean isKnown = known.contains(g.target().toLowerCase(Locale.ROOT));
			TableItem item = new TableItem(table, SWT.NONE);
			String kind = String.join(" / ", java.util.stream.Stream.of(g.platform(), g.language()).filter(s -> !s.isBlank()).toList());
			item.setText(new String[] {isKnown ? text.get("dialog.storeimport.known", new Object[] {g.title()}): g.title(), g.target(), kind, g.path() == null ? "": g.path().getPath()});
			item.setChecked(!isKnown);
		}

		Composite pick = new Composite(dlg, SWT.NONE);
		pick.setLayout(new GridLayout(2, false));
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

		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
		row.setLayout(new GridLayout(2, true));
		Button ok = new Button(row, SWT.PUSH); // the text is longer than a wide button
		ok.setText(text.get("dialog.storeimport.add"));
		ok.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button cancel = new Button(row, SWT.PUSH);
		cancel.setText(text.get("button.cancel"));
		cancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		dlg.setDefaultButton(ok);
		boolean[][] result = new boolean[1][];
		ok.addListener(SWT.Selection, e -> {
			boolean[] r = new boolean[games.size()];
			for (int i = 0; i < r.length; i++)
				r[i] = table.getItem(i).getChecked();
			result[0] = r;
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
