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
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.dbgl.util.Scummvm;
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
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


/**
 * alpha 52: windows of their own for ScummVM, instead of those for DOSBox: the ScummVM version (title, folder, program,
 * parameters; its version number read from scummvm.ini), and a profile of a ScummVM game (its data, the game as ScummVM knows
 * it, the file scummvm.ini).
 */
public final class ScummvmEditDialogs {

	private ScummvmEditDialogs() {
	}

	/** @return the version as saved, or null if cancelled */
	public static DosboxVersion editVersion(Shell parent, DosboxVersion version) {
		ITextService text = TextService.getInstance();
		Shell dlg = shell(parent, text.get("dialog.scummvmversion.title", new Object[] {version.getTitle()}), 3);
		Text title = field(dlg, text.get("dialog.scummvmversion.name"), 2);
		title.setText(StringUtils.defaultString(version.getTitle()));
		Text folder = field(dlg, text.get("dialog.scummvmversion.folder"), 1);
		folder.setText(version.getPath().getPath());
		Button folderButton = new Button(dlg, SWT.PUSH);
		folderButton.setText(text.get("button.browse"));
		Text program = field(dlg, text.get("dialog.scummvmversion.program"), 1);
		program.setText(version.getCanonicalExecutable().getPath());
		Button programButton = new Button(dlg, SWT.PUSH);
		programButton.setText(text.get("button.browse"));
		Text parameters = field(dlg, text.get("dialog.dosboxversion.parameters"), 2);
		parameters.setText(StringUtils.defaultString(version.getExecutableParameters()));
		new Label(dlg, SWT.NONE).setText(text.get("dialog.scummvmversion.version"));
		Label versionLabel = new Label(dlg, SWT.WRAP);
		GridData vd = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		vd.widthHint = 420;
		versionLabel.setLayoutData(vd);
		Runnable showVersion = () -> {
			String v = Scummvm.installedVersion(new File(program.getText().trim()));
			versionLabel.setText(v == null ? text.get("dialog.scummvmversion.versionunknown"): text.get("dialog.scummvmversion.versionfrom", new Object[] {v}));
			versionLabel.setData(v);
			dlg.layout(true, true);
		};
		program.addModifyListener(e -> showVersion.run());
		folderButton.addListener(SWT.Selection, e -> {
			DirectoryDialog dd = new DirectoryDialog(dlg);
			dd.setFilterPath(folder.getText());
			String chosen = dd.open();
			if (chosen != null)
				folder.setText(chosen);
		});
		programButton.addListener(SWT.Selection, e -> {
			FileDialog fd = new FileDialog(dlg, SWT.OPEN);
			fd.setFilterPath(new File(program.getText()).getParent());
			String chosen = fd.open();
			if (chosen != null) {
				File f = new File(chosen);
				if (f.isDirectory() && f.getName().toLowerCase().endsWith(".app"))
					f = new File(f, "Contents/MacOS/scummvm");
				program.setText(f.getPath());
				folder.setText(f.getParent());
			}
		});
		showVersion.run();
		DosboxVersion[] result = new DosboxVersion[1];
		buttons(dlg, text, () -> {
			if (StringUtils.isBlank(title.getText())) {
				Mess_.on(dlg).key("dialog.scummvmversion.required.title").warning();
				return false;
			}
			File exe = new File(program.getText().trim());
			if (!exe.isFile()) {
				Mess_.on(dlg).key("dialog.scummvmversion.missing", new Object[] {exe.getPath()}).warning();
				return false;
			}
			try {
				version.setTitle(title.getText().trim());
				version.setFamily(Scummvm.FAMILY);
				if (versionLabel.getData() instanceof String v)
					version.setVersion(v);
				version.setPath(StringUtils.defaultIfBlank(folder.getText().trim(), exe.getParent()));
				version.setExe(exe.getPath());
				version.setExecutableParameters(parameters.getText().trim());
				new DosboxVersionRepository().update(version);
				result[0] = version;
				return true;
			} catch (java.sql.SQLException | RuntimeException ex) {
				Mess_.on(dlg).exception(ex).warning();
				return false;
			}
		});
		run(parent, dlg, title);
		return result[0];
	}

	/** @return the profile as saved, or null if cancelled */
	public static Profile editProfile(Shell parent, Profile profile) {
		return editProfile(parent, profile, false);
	}

	/** @param search asking the game database at once (menu "Online info") */
	public static Profile editProfile(Shell parent, Profile profile, boolean search) {
		ITextService text = TextService.getInstance();
		try {
			profile.resetAndLoadConfiguration();
		} catch (java.io.IOException e) {
			Mess_.on(parent).exception(e).warning();
			return null;
		}
		Shell dlg = shell(parent, text.get("dialog.profile.title.edit", new Object[] {profile.getTitle(), profile.getId()}), 4);
		Text title = field(dlg, text.get("dialog.profile.title"), 3);
		title.setText(StringUtils.defaultString(profile.getTitle()));
		Text developer = field(dlg, text.get("dialog.profile.developer"), 1);
		developer.setText(StringUtils.defaultString(profile.getDeveloper()));
		Text publisher = field(dlg, text.get("dialog.profile.publisher"), 1);
		publisher.setText(StringUtils.defaultString(profile.getPublisher()));
		Text genre = field(dlg, text.get("dialog.profile.genre"), 1);
		genre.setText(StringUtils.defaultString(profile.getGenre()));
		Text year = field(dlg, text.get("dialog.profile.year"), 1);
		year.setText(StringUtils.defaultString(profile.getYear()));
		Text status = field(dlg, text.get("dialog.profile.status"), 1);
		status.setText(StringUtils.defaultString(profile.getStatus()));
		Button favorite = new Button(dlg, SWT.CHECK);
		favorite.setText(text.get("dialog.profile.favorite.short"));
		favorite.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		favorite.setSelection(Boolean.TRUE.equals(profile.isFavorite()));

		Label gameLabel = new Label(dlg, SWT.NONE);
		gameLabel.setText(text.get("dialog.scummvmprofile.game"));
		Combo game = new Combo(dlg, SWT.DROP_DOWN);
		game.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		game.setToolTipText(text.get("dialog.scummvmprofile.game.tooltip"));
		Text ini = field(dlg, text.get("dialog.scummvmprofile.ini"), 2);
		ini.setText(StringUtils.defaultString(profile.getConfiguration().getValue(Scummvm.SECTION, "config")));
		Button iniButton = new Button(dlg, SWT.PUSH);
		iniButton.setText(text.get("button.browse"));
		List<String> targets = new ArrayList<>();
		Runnable fillGames = () -> {
			String current = game.getText();
			targets.clear();
			List<String> items = new ArrayList<>();
			try {
				File f = new File(ini.getText().trim());
				if (f.isFile())
					for (Scummvm.Game g: Scummvm.readIni(f)) {
						targets.add(g.target());
						items.add(g.target() + " – " + g.title());
					}
			} catch (java.io.IOException e) {
				// no list then: the ID can still be typed in
			}
			game.setItems(items.toArray(new String[0]));
			game.setText(current);
		};
		fillGames.run();
		String target = Scummvm.targetOf(profile);
		int at = target == null ? -1: targets.indexOf(target);
		if (at >= 0)
			game.select(at);
		else
			game.setText(StringUtils.defaultString(target));
		ini.addModifyListener(e -> fillGames.run());
		iniButton.addListener(SWT.Selection, e -> {
			FileDialog fd = new FileDialog(dlg, SWT.OPEN);
			fd.setFilterExtensions(new String[] {"*.ini", "*"});
			fd.setFilterPath(new File(ini.getText()).getParent());
			String chosen = fd.open();
			if (chosen != null)
				ini.setText(chosen);
		});

		Label notesLabel = new Label(dlg, SWT.NONE);
		notesLabel.setText(text.get("dialog.profile.notes"));
		notesLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		Text notes = new Text(dlg, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		GridData nd = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		nd.heightHint = 120;
		nd.widthHint = 560;
		notes.setLayoutData(nd);
		notes.setText(StringUtils.defaultString(profile.getNotes()));

		// alpha 52: the data and pictures from the game database chosen in the settings, as in the profile window of DOSBox games
		org.dbgl.service.SettingsService settings = org.dbgl.service.SettingsService.getInstance();
		org.dbgl.util.searchengine.WebSearchEngine engine = org.dbgl.util.searchengine.WebSearchEngine.getBySimpleName(settings.getValue("gui", "searchengine"));
		new Label(dlg, SWT.NONE);
		Button consult = new Button(dlg, SWT.PUSH);
		consult.setText(text.get("dialog.profile.consultsearchengine", new Object[] {engine.getName()}));
		consult.setImage(org.dbgl.service.ImageService.getResourceImage(dlg.getDisplay(), org.dbgl.service.ImageService.IMG_GLOBE));
		consult.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));
		List<org.dbgl.model.SearchEngineImageInformation> chosenImages = new ArrayList<>();
		Runnable doSearch = () -> {
			String currTitle = title.getText().trim();
			if (currTitle.isEmpty())
				return;
			String searching = text.get("searchengine.searching", new Object[] {engine.getName()});
			try {
				String[] platformFilter = settings.getValues(engine.getSimpleName(), "platform_filter");
				List<org.dbgl.model.WebProfile> found = org.dbgl.gui.controls.BusyTask.run(dlg, searching, () -> engine.getEntries(currTitle, platformFilter));
				org.dbgl.model.WebProfile webGame = null;
				if (found.isEmpty())
					Mess_.on(dlg).key("general.notice.searchenginenoresults", new String[] {engine.getName(), currTitle}).display();
				else
					webGame = found.size() == 1 ? found.get(0): new BrowseSearchEngineDialog(dlg, currTitle, found, engine).open();
				if (webGame == null)
					return;
				org.dbgl.model.WebProfile chosen = webGame;
				org.dbgl.model.WebProfile ext = org.dbgl.gui.controls.BusyTask.run(dlg, searching, () -> engine.getEntryDetailedInformation(chosen));
				String s = engine.getSimpleName();
				if (settings.getBooleanValue(s, "set_title"))
					title.setText(ext.getTitle());
				if (settings.getBooleanValue(s, "set_developer"))
					developer.setText(ext.getDeveloperName());
				if (settings.getBooleanValue(s, "set_publisher"))
					publisher.setText(ext.getPublisherName());
				if (settings.getBooleanValue(s, "set_year"))
					year.setText(ext.getYear());
				if (settings.getBooleanValue(s, "set_genre"))
					genre.setText(ext.getGenre());
				if (settings.getBooleanValue(s, "set_description") && StringUtils.isNotBlank(ext.getNotes())) {
					String p = ext.getNotes().replace("\n", notes.getLineDelimiter());
					if (!notes.getText().endsWith(p))
						notes.append((notes.getText().isEmpty() ? "": notes.getLineDelimiter() + notes.getLineDelimiter()) + p);
				}
				int ca = settings.getBooleanValue(s, "choose_coverart") ? Integer.MAX_VALUE: 0;
				int ss = settings.getBooleanValue(s, "choose_screenshot") ? Integer.MAX_VALUE: 0;
				if ((ca > 0) || (ss > 0)) {
					boolean allRegions = settings.getBooleanValue(s, "force_all_regions_coverart");
					org.dbgl.model.SearchEngineImageInformation[] images = org.dbgl.gui.controls.BusyTask.run(dlg, searching, () -> engine.getEntryImages(ext, ca, ss, allRegions));
					if (images.length == 0)
						Mess_.on(dlg).key("dialog.profile.notice.noimagesfound", new Object[] {engine.getName()}).display();
					else
						chosenImages.addAll(pickImages(dlg, text, ext, images));
				}
			} catch (org.dbgl.gui.controls.BusyTask.Cancelled e) {
				// nothing changed
			} catch (Exception e) {
				org.dbgl.util.searchengine.SearchEngineException explained = org.dbgl.util.searchengine.SearchEngineException.explain(engine.getName(), e);
				Mess_.on(dlg).key("searchengine.error.retrieve", new Object[] {engine.getName(), currTitle, explained.getMessage()}).warning();
			}
		};
		consult.addListener(SWT.Selection, e -> doSearch.run());

		Profile[] result = new Profile[1];
		buttons(dlg, text, () -> {
			if (StringUtils.isBlank(title.getText())) {
				Mess_.on(dlg).key("dialog.profile.required.title").warning();
				return false;
			}
			String chosen = StringUtils.substringBefore(game.getText(), " – ").trim();
			if (!chosen.matches("[A-Za-z0-9][A-Za-z0-9._:+-]{0,63}")) {
				Mess_.on(dlg).key("dialog.scummvmprofile.required.game").warning();
				return false;
			}
			try {
				profile.setTitle(title.getText().trim());
				profile.setDeveloper(developer.getText().trim());
				profile.setPublisher(publisher.getText().trim());
				profile.setGenre(genre.getText().trim());
				profile.setYear(year.getText().trim());
				profile.setStatus(status.getText().trim());
				profile.setFavorite(favorite.getSelection());
				profile.setNotes(notes.getText());
				profile.getConfiguration().setValue(Scummvm.SECTION, "target", chosen);
				profile.getConfiguration().setValue(Scummvm.SECTION, "config", ini.getText().trim());
				new ProfileRepository().update(profile);
				saveImages(dlg, text, profile, chosenImages);
				result[0] = profile;
				return true;
			} catch (java.sql.SQLException | RuntimeException ex) {
				Mess_.on(dlg).exception(ex).warning();
				return false;
			}
		});
		if (search)
			dlg.getDisplay().asyncExec(() -> {
				if (!dlg.isDisposed())
					doSearch.run();
			});
		run(parent, dlg, title);
		return result[0];
	}

	/** the pictures found, to tick (all loaded in the background); @return those ticked */
	private static List<org.dbgl.model.SearchEngineImageInformation> pickImages(Shell parent, ITextService text, org.dbgl.model.WebProfile ext,
			org.dbgl.model.SearchEngineImageInformation[] images) {
		Display display = parent.getDisplay();
		int w = Math.max(80, org.dbgl.service.SettingsService.getInstance().getIntValue("mobygames", "image_width"));
		int h = Math.max(60, org.dbgl.service.SettingsService.getInstance().getIntValue("mobygames", "image_height"));
		Shell dlg = shell(parent, text.get("dialog.scummvmprofile.images"), 1);
		org.eclipse.swt.custom.ScrolledComposite scroll = new org.eclipse.swt.custom.ScrolledComposite(dlg, SWT.V_SCROLL | SWT.BORDER);
		GridData sd = new GridData(SWT.FILL, SWT.FILL, true, true);
		sd.widthHint = 4 * (w + 14);
		sd.heightHint = Math.min(3, (images.length + 3) / 4) * (h + 40);
		scroll.setLayoutData(sd);
		Composite grid = new Composite(scroll, SWT.NONE);
		grid.setLayout(new GridLayout(4, true));
		scroll.setContent(grid);
		Button[] boxes = new Button[images.length];
		for (int i = 0; i < images.length; i++) {
			Composite cell = new Composite(grid, SWT.NONE);
			cell.setLayout(new GridLayout(1, false));
			Label picture = new Label(cell, SWT.CENTER);
			GridData pd = new GridData(SWT.CENTER, SWT.CENTER, false, false);
			pd.widthHint = w;
			pd.heightHint = h;
			picture.setLayoutData(pd);
			boxes[i] = new Button(cell, SWT.CHECK);
			boxes[i].setText(StringUtils.abbreviate(StringUtils.defaultString(images[i].getDescription()), 28));
			boxes[i].setToolTipText(images[i].getDescription());
			int j = i;
			Thread loader = new Thread(() -> {
				try {
					org.eclipse.swt.graphics.ImageData data = ext.getWebImage(j);
					if (!display.isDisposed())
						display.asyncExec(() -> {
							if (!picture.isDisposed()) {
								org.eclipse.swt.graphics.Image img = org.dbgl.service.ImageService.getWidthLimitedImage(display, w, data);
								picture.setImage(img);
								picture.addDisposeListener(e -> img.dispose());
							}
						});
				} catch (Exception e) {
					// shown without its picture
				}
			}, "picture");
			loader.setDaemon(true);
			loader.start();
		}
		Point gridSize = grid.computeSize(SWT.DEFAULT, SWT.DEFAULT); // all four columns visible, however long the names are
		grid.setSize(gridSize);
		sd.widthHint = gridSize.x + 24;
		List<org.dbgl.model.SearchEngineImageInformation> result = new ArrayList<>();
		buttons(dlg, text, () -> {
			for (int i = 0; i < images.length; i++)
				if (boxes[i].getSelection())
					result.add(images[i]);
			return true;
		});
		DarkTheme.apply(dlg);
		dlg.pack();
		Rectangle area = parent.getMonitor().getClientArea(), p = parent.getBounds();
		Point size = dlg.getSize();
		dlg.setLocation(Math.max(area.x, p.x + (p.width - size.x) / 2), Math.max(area.y, p.y + (p.height - size.y) / 2));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		return result;
	}

	/** the pictures ticked into the folder of the profile, named as the profile window of DOSBox games names them */
	private static void saveImages(Shell shell, ITextService text, Profile profile, List<org.dbgl.model.SearchEngineImageInformation> images) {
		if (images.isEmpty())
			return;
		File dir = profile.getCanonicalCaptures();
		dir.mkdirs();
		int i = 0;
		for (org.dbgl.model.SearchEngineImageInformation info: images) {
			if (info.getData() == null)
				continue; // not loaded
			String description = org.dbgl.util.FilesUtils.toSafeFilenameForWebImages(info.getDescription());
			String filename = (info.getType() == org.dbgl.model.SearchEngineImageInformation.SearchEngineImageType.COVER_ART)
					? text.get("dialog.profile.mobygames.coverartfilename", new Object[] {i, description}) + ".jpg"
					: text.get("dialog.profile.mobygames.screenshotfilename", new Object[] {i, description}) + ".png";
			i++;
			File file = new File(dir, filename);
			if (file.exists())
				continue;
			try {
				org.dbgl.service.ImageService.save(shell.getDisplay(), info.getData(), file.getPath());
			} catch (RuntimeException e) {
				Mess_.on(shell).key("general.error.savefile", file.getPath()).exception(e).warning();
			}
		}
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

	private static Text field(Composite parent, String label, int span) {
		new Label(parent, SWT.NONE).setText(label);
		Text t = new Text(parent, SWT.BORDER);
		GridData d = new GridData(SWT.FILL, SWT.CENTER, true, false, span, 1);
		d.widthHint = span > 1 ? 420: 200;
		t.setLayoutData(d);
		return t;
	}

	private static void buttons(Shell dlg, ITextService text, java.util.function.BooleanSupplier save) {
		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, ((GridLayout)dlg.getLayout()).numColumns, 1));
		row.setLayout(new GridLayout(2, true));
		Button ok = BaseDialog.wideButton(new Button(row, SWT.PUSH));
		ok.setText(text.get("button.ok"));
		Button cancel = BaseDialog.wideButton(new Button(row, SWT.PUSH));
		cancel.setText(text.get("button.cancel"));
		dlg.setDefaultButton(ok);
		ok.addListener(SWT.Selection, e -> {
			if (save.getAsBoolean())
				dlg.close();
		});
		cancel.addListener(SWT.Selection, e -> dlg.close());
	}

	private static void run(Shell parent, Shell dlg, Text focus) {
		DarkTheme.apply(dlg);
		dlg.pack();
		Rectangle area = parent.getMonitor().getClientArea(), p = parent.getBounds();
		Point size = dlg.getSize();
		dlg.setLocation(Math.max(area.x, p.x + (p.width - size.x) / 2), Math.max(area.y, p.y + (p.height - size.y) / 2));
		dlg.open();
		focus.setFocus();
		focus.selectAll();
		Display display = parent.getDisplay();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
	}
}
