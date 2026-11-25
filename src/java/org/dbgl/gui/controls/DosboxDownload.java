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
import java.util.List;
import java.util.function.Predicate;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.dbgl.util.DosboxInstaller;
import org.dbgl.util.DosboxUpdateChecker.Release;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
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
 * Alpha 49: the window of "Download DOSBox": the versions that can be downloaded, which of them are new and which are set up
 * already, the file for this computer, and the folder the files end up in (shown in full before anything is downloaded).
 */
public final class DosboxDownload {

	/** what to download and into which folder (the files lie directly in it) */
	public record Choice(Release release, String[] asset, File dir) {
	}

	private DosboxDownload() {
	}

	/**
	 * Alpha 53: a build as offered for choosing, with its kind in front ("MinGW – dosbox-x-mingw-win64-….zip"), the recommended
	 * one marked.
	 */
	public static String buildLabel(String[] asset, boolean recommended, ITextService text) {
		String kind = DosboxInstaller.buildKind(asset[0]);
		String name = "vsbuild".equals(kind) ? "Visual Studio – " + asset[0]: "mingw".equals(kind) ? "MinGW – " + asset[0]: asset[0];
		return recommended ? name + "   (" + text.get("dialog.main.notice.recommended") + ")": name;
	}

	/** what tells the kind of a build apart, empty for builds of no particular kind */
	public static String buildHint(String[] asset, ITextService text) {
		String kind = DosboxInstaller.buildKind(asset[0]);
		return kind == null ? "": text.get("dialog.dosboxdownload.hint." + kind);
	}

	/** the folder the files of a release end up in, for the folder chosen: a folder named after the release in it */
	public static File targetDir(String chosen, Release r) {
		String folderName = DosboxInstaller.folderName(r.family(), r.version());
		File dir = new File(chosen);
		return dir.getName().equalsIgnoreCase(folderName) ? dir: new File(dir, folderName);
	}

	/**
	 * TEST33: the name proposed for the folder of a build: the release, and the kind of build when it is not the recommended one
	 * ("dosbox-x-2026.08.31-mingw"), so that two builds of a release do not end up in one folder
	 */
	public static String defaultFolderName(Release r, String asset) {
		String name = DosboxInstaller.folderName(r.family(), r.version());
		String a = asset == null ? "": asset.toLowerCase(java.util.Locale.ROOT);
		if (a.contains("mingw"))
			name += "-mingw";
		if (a.contains("lowend9x"))
			name += "-lowend9x";
		else if (a.contains("lowend"))
			name += "-lowend";
		if (a.contains("win32") || a.contains("mingw32") || a.contains("i686"))
			name += "-32bit";
		if (a.contains("sdl1"))
			name += "-sdl1";
		return name;
	}

	/** TEST33: a name not taken in the folder (with files): the name itself, else with "-2", "-3", ... */
	public static String freeFolderName(File base, String name) {
		if (!DosboxInstaller.hasContent(new File(base, name)))
			return name;
		for (int n = 2; n < 100; n++)
			if (!DosboxInstaller.hasContent(new File(base, name + "-" + n)))
				return name + "-" + n;
		return name;
	}

	/**
	 * @param present true for a release that is set up in DBGL already (this version or a newer one)
	 * @param baseDir the folder proposed (DBGL's folder for DOSBox)
	 * @return what was chosen to download, null if the window was closed (or the download page was opened)
	 */
	public static Choice open(Shell shell, List<Release> releases, Predicate<Release> present, File baseDir) {
		ITextService text = TextService.getInstance();
		Display display = shell.getDisplay();
		boolean arm = System.getProperty("os.arch").toLowerCase().contains("aarch64") || System.getProperty("os.arch").toLowerCase().contains("arm");
		Shell dlg = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		dlg.setText(text.get("dialog.dosboxdownload.title"));
		BaseDialog.inheritWindowImage(dlg, shell);
		dlg.setLayout(new GridLayout(3, false));
		DarkTheme.window(dlg);

		Table table = new Table(dlg, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		tableData.heightHint = 110;
		tableData.widthHint = 760;
		table.setLayoutData(tableData);
		table.setHeaderVisible(true);
		table.setLinesVisible(!DarkTheme.forced());
		if (DarkTheme.forced()) {
			table.setBackground(DarkTheme.inputBackground);
			table.setForeground(DarkTheme.defaultForeground);
			table.setHeaderBackground(DarkTheme.tableHeaderBackground);
			table.setHeaderForeground(DarkTheme.tableHeaderForeground);
		}
		String[] heads = {"version", "status", "file"};
		int[] widths = {220, 180, 360};
		for (int i = 0; i < heads.length; i++) {
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText(text.get("dialog.dosboxdownload.col." + heads[i]));
			column.setWidth(widths[i]);
		}
		List<List<String[]>> candidates = new ArrayList<>();
		int first = -1;
		for (int i = 0; i < releases.size(); i++) {
			Release r = releases.get(i);
			List<String[]> fitting = DosboxInstaller.candidates(r.assets(), SystemUtils.IS_WINDOWS, SystemUtils.IS_LINUX, arm);
			candidates.add(fitting);
			boolean have = present.test(r);
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(new String[] {r.family() + " " + r.version(), text.get(have ? "dialog.dosboxdownload.status.installed": "dialog.dosboxdownload.status.new"),
					fitting.isEmpty() ? text.get("dialog.dosboxdownload.nofile"): fitting.get(0)[0]});
			if (!have && (first < 0))
				first = i;
		}
		table.select(Math.max(0, first));

		Label buildLabel = new Label(dlg, SWT.NONE);
		buildLabel.setText(text.get("dialog.dosboxdownload.build"));
		Combo build = new Combo(dlg, SWT.READ_ONLY);
		build.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		// alpha 53: what the build chosen is good for (DOSBox-X: Visual Studio or MinGW)
		new Label(dlg, SWT.NONE);
		Label buildHint = new Label(dlg, SWT.WRAP);
		GridData hintData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		hintData.widthHint = 600;
		buildHint.setLayoutData(hintData);

		Label dirLabel = new Label(dlg, SWT.NONE);
		dirLabel.setText(text.get("dialog.dosboxdownload.basedir"));
		Text dir = new Text(dlg, SWT.BORDER);
		dir.setText(baseDir.getAbsolutePath());
		dir.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button browse = new Button(dlg, SWT.PUSH);
		browse.setText(text.get("button.browse"));

		// TEST33: the name of the folder, to be changed (e.g. when there is a folder of that name already)
		Label nameLabel = new Label(dlg, SWT.NONE);
		nameLabel.setText(text.get("dialog.dosboxdownload.foldername"));
		Text name = new Text(dlg, SWT.BORDER);
		name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		name.setToolTipText(text.get("dialog.dosboxdownload.foldername.tooltip"));
		boolean[] nameByHand = {false}, settingName = {false};

		Label target = new Label(dlg, SWT.WRAP);
		GridData targetData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		targetData.widthHint = 760;
		target.setLayoutData(targetData);

		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		GridLayout rowLayout = new GridLayout(4, false);
		rowLayout.marginWidth = rowLayout.marginHeight = 0;
		row.setLayout(rowLayout);
		Label space = new Label(row, SWT.NONE);
		space.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button download = new Button(row, SWT.PUSH);
		download.setText(text.get("dialog.dosboxdownload.download"));
		Button page = new Button(row, SWT.PUSH);
		page.setText(text.get("dialog.dosboxdownload.page"));
		Button close = new Button(row, SWT.PUSH);
		close.setText(text.get("button.close"));
		for (Button b: new Button[] {download, page, close}) {
			GridData data = new GridData(SWT.END, SWT.CENTER, false, false);
			data.widthHint = Math.max(110, b.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
			b.setLayoutData(data);
		}
		dlg.setDefaultButton(download);

		Runnable showTarget = () -> {
			int sel = table.getSelectionIndex();
			boolean can = (sel >= 0) && !candidates.get(sel).isEmpty() && !dir.getText().isBlank() && !name.getText().isBlank();
			String said = "";
			if (can) {
				File t = new File(dir.getText().strip(), name.getText().strip());
				said = text.get("dialog.dosboxdownload.target", new Object[] {t.getPath()});
				String wanted = defaultFolderName(releases.get(sel), candidates.get(sel).get(Math.max(0, build.getSelectionIndex()))[0]);
				if (DosboxInstaller.hasContent(t))
					said += "\n" + text.get("dialog.dosboxdownload.exists");
				else if (!name.getText().strip().equals(wanted) && !nameByHand[0])
					said += "\n" + text.get("dialog.dosboxdownload.taken", new Object[] {wanted});
			}
			target.setText(said);
			download.setEnabled(can);
			download.redraw();
			page.setEnabled(sel >= 0);
			page.redraw();
			dlg.layout(true, true);
		};
		Runnable proposeName = () -> { // a free name for the release and build chosen, unless typed in by hand
			int sel = table.getSelectionIndex();
			if (nameByHand[0] || (sel < 0) || candidates.get(sel).isEmpty())
				return;
			settingName[0] = true;
			name.setText(freeFolderName(new File(dir.getText().strip()), defaultFolderName(releases.get(sel), candidates.get(sel).get(Math.max(0, build.getSelectionIndex()))[0])));
			settingName[0] = false;
		};
		Runnable showHint = () -> {
			int sel = table.getSelectionIndex(), b = build.getSelectionIndex();
			buildHint.setText((sel >= 0) && (b >= 0) && (b < candidates.get(sel).size()) ? buildHint(candidates.get(sel).get(b), text): "");
		};
		Runnable showBuilds = () -> {
			int sel = table.getSelectionIndex();
			build.removeAll();
			if (sel >= 0) {
				List<String[]> fitting = candidates.get(sel);
				for (int i = 0; i < fitting.size(); i++)
					build.add(buildLabel(fitting.get(i), (i == 0) && (fitting.size() > 1), text));
				if (!fitting.isEmpty())
					build.select(0);
			}
			build.setEnabled(build.getItemCount() > 1);
			showHint.run();
			proposeName.run();
			showTarget.run();
		};
		table.addListener(SWT.Selection, e -> {
			nameByHand[0] = false; // another release: its own name
			showBuilds.run();
		});
		build.addListener(SWT.Selection, e -> {
			showHint.run();
			proposeName.run();
			showTarget.run();
		});
		dir.addListener(SWT.Modify, e -> {
			proposeName.run();
			showTarget.run();
		});
		name.addListener(SWT.Modify, e -> {
			if (!settingName[0])
				nameByHand[0] = true;
			showTarget.run();
		});
		browse.addListener(SWT.Selection, e -> {
			DirectoryDialog dd = new DirectoryDialog(dlg);
			dd.setText(text.get("dialog.dosboxdownload.title"));
			dd.setFilterPath(dir.getText());
			String chosen = dd.open();
			if (chosen != null)
				dir.setText(chosen);
		});
		Choice[] result = new Choice[1];
		download.addListener(SWT.Selection, e -> {
			int sel = table.getSelectionIndex();
			if ((sel < 0) || candidates.get(sel).isEmpty() || dir.getText().isBlank())
				return;
			Release r = releases.get(sel);
			if (name.getText().isBlank())
				return;
			result[0] = new Choice(r, candidates.get(sel).get(Math.max(0, build.getSelectionIndex())), new File(dir.getText().strip(), name.getText().strip()));
			dlg.close();
		});
		page.addListener(SWT.Selection, e -> {
			int sel = table.getSelectionIndex();
			if (sel >= 0)
				SystemUtils.openForBrowsing(releases.get(sel).url(), dlg);
		});
		close.addListener(SWT.Selection, e -> dlg.close());

		showBuilds.run();
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
		return result[0];
	}
}
