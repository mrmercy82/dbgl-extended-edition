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
				version = new DosboxVersionRepository().add(DosboxVersionFactory.create(Scummvm.FAMILY, Scummvm.FAMILY, org.apache.commons.lang3.StringUtils.defaultString(Scummvm.installedVersion(exe)), false, false,
					false, null, exe.getParent(), exe.getPath(), "", conf.getPath()));
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
			boolean[] chosen = choose(shell, text, games, known, newVersion ? version.getCanonicalExecutable().getPath(): null, Scummvm.returnsToLauncher(ini), null);
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

	/**
	 * alpha 54 (TEST14): after the import from a folder, the games ScummVM recognizes there itself (it knows them by the
	 * checksums of their files) and no profile has yet; the chosen ones are added to ScummVM as its "Add game" does, each with
	 * a profile. Nothing without a ScummVM version in DBGL. The number of profiles added
	 */
	public static int importDetected(Shell shell, List<DosboxVersion> versions, File root, List<Profile> profiles) {
		DosboxVersion version = versions.stream().filter(Scummvm::is).findFirst().orElse(null);
		if (version == null)
			return 0;
		File exe = version.getCanonicalExecutable();
		File ini = Scummvm.iniOf(version);
		if ((exe == null) || !exe.isFile() || (ini == null))
			return 0;
		ITextService text = TextService.getInstance();
		List<Scummvm.Detected> found;
		try {
			found = org.dbgl.gui.controls.BusyTask.run(shell, text.get("dialog.scummvmimport.detecting"), () -> Scummvm.detect(exe, root));
		} catch (Exception e) {
			return 0; // ScummVM could not look (or was stopped): the import as it was
		}
		found = notCovered(found, profiles);
		if (found.isEmpty())
			return 0;
		List<Scummvm.Game> shown = found.stream().map(d -> new Scummvm.Game(d.id(), d.description(), d.description(), d.id(), d.path(), "", "")).toList();
		boolean[] chosen = choose(shell, text, shown, Set.of(), null, Scummvm.returnsToLauncher(ini), text.get("dialog.scummvmimport.detected", new Object[] {found.size(), ini.getPath()}));
		if (chosen == null)
			return 0;
		int added = 0;
		for (int i = 0; i < found.size(); i++)
			if (chosen[i])
				try {
					for (Scummvm.Game g: Scummvm.add(exe, ini, found.get(i))) {
						new ProfileRepository().add(profileOf(g, ini, version));
						added++;
					}
				} catch (IOException | java.sql.SQLException | RuntimeException e) {
					Mess_.on(shell).exception(e).warning();
				}
		Mess_.on(shell).key("dialog.scummvmimport.detectedadded", new Object[] {added}).display();
		return added;
	}

	/** the games found whose folder no profile has yet (as its drive C: or first folder, or with its start program in it) */
	public static List<Scummvm.Detected> notCovered(List<Scummvm.Detected> found, List<Profile> profiles) {
		Set<File> dirs = new HashSet<>();
		List<java.nio.file.Path> mains = new ArrayList<>();
		for (Profile p: profiles) {
			boolean scummvm = Scummvm.is(p.getDosboxVersion());
			File d = scummvm ? Scummvm.gameDirOf(p): org.dbgl.util.SavegameBackup.gameDir(p);
			if (d != null)
				dirs.add(canonical(d));
			if (!scummvm) {
				String main = p.getConfiguration().getAutoexec().getMain();
				if (org.apache.commons.lang3.StringUtils.isNotBlank(main))
					try {
						mains.add(new org.dbgl.model.FileLocation(main, FileLocationService.getInstance().dosrootRelative()).getCanonicalFile().toPath());
					} catch (RuntimeException e) {
						// none
					}
			}
		}
		List<Scummvm.Detected> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Scummvm.Detected d: found) {
			File c = canonical(d.path());
			if (dirs.contains(c) || mains.stream().anyMatch(m -> m.startsWith(c.toPath())) || !seen.add(c.getPath() + "|" + d.id()))
				continue;
			result.add(d);
		}
		return result;
	}

	private static File canonical(File f) {
		try {
			return f.getCanonicalFile();
		} catch (IOException e) {
			return f.getAbsoluteFile();
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

	private static boolean[] choose(Shell parent, ITextService text, List<Scummvm.Game> games, Set<String> known, String newExe, boolean returnsToLauncher, String infoOverride) {
		Display display = parent.getDisplay();
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
		dlg.setText(text.get("dialog.scummvmimport.title"));
		BaseDialog.inheritWindowImage(dlg, parent);
		dlg.setLayout(new GridLayout(1, false));
		if (DarkTheme.forced())
			dlg.setBackground(DarkTheme.dialogBackground);

		int already = (int)games.stream().filter(g -> known.contains(g.target().toLowerCase(Locale.ROOT))).count();
		Label info = new Label(dlg, SWT.WRAP);
		String infoText = infoOverride != null ? infoOverride: text.get("dialog.scummvmimport.info", new Object[] {games.size(), already});
		if (newExe != null)
			infoText = text.get("dialog.scummvmimport.versionadded", new Object[] {newExe}) + "\n\n" + infoText;
		if (returnsToLauncher) // alpha 52: ScummVM stays open after the game, and the play time goes on until it is closed
			infoText += "\n\n" + text.get("dialog.scummvmimport.returntolauncher");
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
			int column = i;
			col.addListener(SWT.Selection, e -> sortBy(table, col, column)); // alpha 52: a click on the title sorts, a second one the other way
		}
		// alpha 54 (TEST13): at first by the title, A to Z (as a click on its column would sort it)
		List<Integer> order = new java.util.ArrayList<>();
		for (int i = 0; i < games.size(); i++)
			order.add(i);
		order.sort((x, y) -> naturalCompare(games.get(x).title(), games.get(y).title()));
		table.setSortColumn(table.getColumn(0));
		table.setSortDirection(SWT.UP);
		for (int index: order) {
			Scummvm.Game g = games.get(index);
			boolean isKnown = known.contains(g.target().toLowerCase(Locale.ROOT));
			TableItem item = new TableItem(table, SWT.NONE);
			String kind = String.join(" / ", java.util.stream.Stream.of(g.platform(), g.language()).filter(s -> !s.isBlank()).toList());
			item.setText(new String[] {isKnown ? text.get("dialog.storeimport.known", new Object[] {g.title()}): g.title(), g.target(), kind, g.path() == null ? "": g.path().getPath()});
			item.setChecked(!isKnown);
			item.setData(index); // its game, whatever the order
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
			for (TableItem item: table.getItems())
				r[(Integer)item.getData()] = item.getChecked();
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

	/** the rows by the text of a column (case and the numbers in it as people read them: "Indy 2" before "Indy 10") */
	static void sortBy(Table table, TableColumn col, int column) {
		int dir = (table.getSortColumn() == col) && (table.getSortDirection() == SWT.UP) ? SWT.DOWN: SWT.UP;
		TableItem[] items = table.getItems();
		List<Object[]> rows = new java.util.ArrayList<>();
		int columns = table.getColumnCount();
		for (TableItem it: items) {
			String[] texts = new String[columns];
			for (int c = 0; c < columns; c++)
				texts[c] = it.getText(c);
			rows.add(new Object[] {texts, it.getChecked(), it.getData(), it.getForeground()});
		}
		java.util.Comparator<Object[]> by = (a, b) -> naturalCompare(((String[])a[0])[column], ((String[])b[0])[column]);
		rows.sort(dir == SWT.UP ? by: by.reversed());
		for (int r = 0; r < items.length; r++) {
			Object[] row = rows.get(r);
			items[r].setText((String[])row[0]);
			items[r].setChecked((Boolean)row[1]);
			items[r].setData(row[2]);
			items[r].setForeground((org.eclipse.swt.graphics.Color)row[3]);
		}
		table.setSortColumn(col);
		table.setSortDirection(dir);
	}

	public static int naturalCompare(String a, String b) {
		java.util.regex.Matcher ma = java.util.regex.Pattern.compile("\\d+|\\D+").matcher(a.toLowerCase(Locale.ROOT)), mb = java.util.regex.Pattern.compile("\\d+|\\D+").matcher(b.toLowerCase(Locale.ROOT));
		while (ma.find() && mb.find()) {
			String x = ma.group(), y = mb.group();
			int c = (Character.isDigit(x.charAt(0)) && Character.isDigit(y.charAt(0))) ? new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y)): x.compareTo(y);
			if (c != 0)
				return c;
		}
		return Integer.compare(a.length(), b.length());
	}
}
