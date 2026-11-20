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
		Shell dlg = shell(parent, text.get("dialog.profile.title.edit", new Object[] {profile.getTitle(), profile.getId()}), 1);
		// alpha 52: in tabs as in the profile window of DOSBox games: the information, the links 1 to 8, the custom fields 1 to 14
		org.eclipse.swt.widgets.TabFolder tabs = new org.eclipse.swt.widgets.TabFolder(dlg, SWT.NONE);
		tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		Composite main = page(tabs, text.get("dialog.profile.tab.info"), 4);
		Text title = field(main, text.get("dialog.profile.title"), 3);
		title.setText(StringUtils.defaultString(profile.getTitle()));
		Text developer = field(main, text.get("dialog.profile.developer"), 1);
		developer.setText(StringUtils.defaultString(profile.getDeveloper()));
		Text publisher = field(main, text.get("dialog.profile.publisher"), 1);
		publisher.setText(StringUtils.defaultString(profile.getPublisher()));
		Text genre = field(main, text.get("dialog.profile.genre"), 1);
		genre.setText(StringUtils.defaultString(profile.getGenre()));
		Text year = field(main, text.get("dialog.profile.year"), 1);
		year.setText(StringUtils.defaultString(profile.getYear()));
		Text status = field(main, text.get("dialog.profile.status"), 1);
		status.setText(StringUtils.defaultString(profile.getStatus()));
		Button favorite = new Button(main, SWT.CHECK);
		favorite.setText(text.get("dialog.profile.favorite.short"));
		favorite.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		favorite.setSelection(Boolean.TRUE.equals(profile.isFavorite()));

		Label gameLabel = new Label(main, SWT.NONE);
		gameLabel.setText(text.get("dialog.scummvmprofile.game"));
		Combo game = new Combo(main, SWT.DROP_DOWN);
		game.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		game.setToolTipText(text.get("dialog.scummvmprofile.game.tooltip"));
		Text ini = field(main, text.get("dialog.scummvmprofile.ini"), 2);
		ini.setText(StringUtils.defaultString(profile.getConfiguration().getValue(Scummvm.SECTION, "config")));
		Button iniButton = new Button(main, SWT.PUSH);
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

		Label notesLabel = new Label(main, SWT.NONE);
		notesLabel.setText(text.get("dialog.profile.notes"));
		notesLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		Text notes = new Text(main, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		GridData nd = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		nd.heightHint = 120;
		nd.widthHint = 560;
		notes.setLayoutData(nd);
		notes.setText(StringUtils.defaultString(profile.getNotes()));

		// the links: where (a file or a web address) and its title, as in the profile window
		Composite linkPage = page(tabs, text.get("dialog.scummvmprofile.tab.links"), 5);
		Text[] links = new Text[Profile.NR_OF_LINK_DESTINATIONS], linkTitles = new Text[Profile.NR_OF_LINK_TITLES];
		for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
			new Label(linkPage, SWT.NONE).setText(text.get("dialog.profile.link", new Object[] {i + 1}));
			links[i] = new Text(linkPage, SWT.BORDER);
			GridData ld = new GridData(SWT.FILL, SWT.CENTER, true, false);
			ld.widthHint = 300;
			links[i].setLayoutData(ld);
			links[i].setText(StringUtils.defaultString(profile.getLinkDestination(i)));
			Button browse = new Button(linkPage, SWT.PUSH);
			browse.setText(text.get("button.browse"));
			Text dest = links[i];
			browse.addListener(SWT.Selection, e -> {
				FileDialog fd = new FileDialog(dlg, SWT.OPEN);
				String chosen = fd.open();
				if (chosen != null)
					dest.setText(chosen);
			});
			new Label(linkPage, SWT.NONE).setText(text.get("dialog.profile.linktitle"));
			linkTitles[i] = new Text(linkPage, SWT.BORDER);
			GridData td = new GridData(SWT.FILL, SWT.CENTER, true, false);
			td.widthHint = 180;
			linkTitles[i].setLayoutData(td);
			linkTitles[i].setText(StringUtils.defaultString(profile.getLinkTitle(i)));
		}
		// the custom fields, named as in the settings (Custom 1 to 4 with the values used so far to choose from)
		Composite customPage = page(tabs, text.get("dialog.profile.tab.custominfo"), 2);
		org.dbgl.service.SettingsService names = org.dbgl.service.SettingsService.getInstance();
		org.eclipse.swt.widgets.Control[] customs = new org.eclipse.swt.widgets.Control[Profile.NR_OF_CUSTOM_STRINGS];
		org.dbgl.model.repository.TitledEntityRepository titled = new org.dbgl.model.repository.TitledEntityRepository();
		for (int i = 0; i < Profile.NR_OF_CUSTOM_STRINGS; i++) {
			// the strings 1-8 are Custom 1 to 8, the strings 9-12 Custom 11 to 14 (9 and 10 are the two numbers)
			int number = i < Profile.NR_OF_CUSTOM_STRING_DROPDOWNS + Profile.NR_OF_CUSTOM_STRING_FIELDS_1 ? i + 1: i + 1 + Profile.NR_OF_CUSTOM_INTS;
			if (i == Profile.NR_OF_CUSTOM_STRING_DROPDOWNS + Profile.NR_OF_CUSTOM_STRING_FIELDS_1) // Custom 9 and 10 between 8 and 11
				customNumbers(customPage, names, profile);
			new Label(customPage, SWT.NONE).setText(names.getValue("gui", "custom" + number));
			GridData cd = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
			cd.widthHint = 360;
			if (i < Profile.NR_OF_CUSTOM_STRING_DROPDOWNS) {
				Combo combo = new Combo(customPage, SWT.DROP_DOWN);
				try {
					combo.setItems(titled.customValues(i));
				} catch (java.sql.SQLException e) {
					// typed in then
				}
				combo.setText(StringUtils.defaultString(profile.getCustomString(i)));
				combo.setLayoutData(cd);
				customs[i] = combo;
			} else {
				Text field = new Text(customPage, SWT.BORDER);
				field.setText(StringUtils.defaultString(profile.getCustomString(i)));
				field.setLayoutData(cd);
				customs[i] = field;
			}
		}
		org.eclipse.swt.widgets.Spinner[] numbers = numbersOf(customPage);

		// alpha 52: the data and pictures from the game database chosen in the settings, as in the profile window of DOSBox games
		org.dbgl.service.SettingsService settings = org.dbgl.service.SettingsService.getInstance();
		org.dbgl.util.searchengine.WebSearchEngine engine = org.dbgl.util.searchengine.WebSearchEngine.getBySimpleName(settings.getValue("gui", "searchengine"));
		new Label(main, SWT.NONE);
		Button consult = new Button(main, SWT.PUSH);
		consult.setText(text.get("dialog.profile.consultsearchengine", new Object[] {engine.getName()}));
		consult.setImage(org.dbgl.service.ImageService.getResourceImage(dlg.getDisplay(), org.dbgl.service.ImageService.IMG_GLOBE));
		consult.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));
		List<org.dbgl.model.SearchEngineImageInformation> chosenImages = new ArrayList<>();
		int[] rank = {-1};
		String[] link = new String[2]; // Link 1 to the page of the game, as the setting "set_link" wants (RAWG asks for it)
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
				if (settings.getBooleanValue(s, "set_link") && StringUtils.isNotBlank(ext.getUrl())) {
					link[0] = ext.getUrl();
					link[1] = text.get("dialog.profile.searchengine.link.maininfo", new String[] {engine instanceof org.dbgl.util.searchengine.MetropolisSearchEngine
							? org.dbgl.util.searchengine.MobyGamesSearchEngine.getInstance().getName(): engine.getName()});
					links[0].setText(link[0]); // Link 1, shown at once on its tab
					linkTitles[0].setText(link[1]);
				}
				if (settings.getBooleanValue(s, "set_rank") && (ext.getRank() > 0)) {
					rank[0] = ext.getRank();
					numbers[0].setSelection(Math.min(100, ext.getRank())); // "Custom 9", as the profile window puts the rank
				}
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
				for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
					profile.setLinkDestination(i, links[i].getText().trim());
					profile.setLinkTitle(i, linkTitles[i].getText().trim());
				}
				for (int i = 0; i < Profile.NR_OF_CUSTOM_STRINGS; i++)
					profile.setCustomString(i, customs[i] instanceof Combo c ? c.getText().trim(): ((Text)customs[i]).getText().trim());
				for (int i = 0; i < Profile.NR_OF_CUSTOM_INTS; i++)
					profile.setCustomInt(i, String.valueOf(numbers[i].getSelection()));
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

	/** a tab with its page */
	private static Composite page(org.eclipse.swt.widgets.TabFolder tabs, String title, int columns) {
		org.eclipse.swt.widgets.TabItem item = new org.eclipse.swt.widgets.TabItem(tabs, SWT.NONE);
		item.setText(title);
		Composite page = new Composite(tabs, SWT.NONE);
		page.setLayout(new GridLayout(columns, false));
		item.setControl(page);
		return page;
	}

	/** Custom 9 (the rating in percent, as the profile window has it) and Custom 10 (a number), as spinners */
	private static void customNumbers(Composite page, org.dbgl.service.SettingsService names, Profile profile) {
		for (int i = 0; i < Profile.NR_OF_CUSTOM_INTS; i++) {
			new Label(page, SWT.NONE).setText(names.getValue("gui", "custom" + (Profile.NR_OF_CUSTOM_STRING_DROPDOWNS + Profile.NR_OF_CUSTOM_STRING_FIELDS_1 + 1 + i)));
			org.eclipse.swt.widgets.Spinner spinner = new org.eclipse.swt.widgets.Spinner(page, SWT.BORDER);
			spinner.setValues(0, 0, i == 0 ? 100: Integer.MAX_VALUE, 0, 1, 10);
			String v = profile.getCustomInt(i);
			try {
				spinner.setSelection(v == null ? 0: Integer.parseInt(v));
			} catch (NumberFormatException e) {
				spinner.setSelection(0);
			}
			spinner.setData("dbgl.customInt", i);
			GridData sd = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
			sd.widthHint = 90; // both as wide
			spinner.setLayoutData(sd);
		}
	}

	private static org.eclipse.swt.widgets.Spinner[] numbersOf(Composite page) {
		org.eclipse.swt.widgets.Spinner[] result = new org.eclipse.swt.widgets.Spinner[Profile.NR_OF_CUSTOM_INTS];
		for (org.eclipse.swt.widgets.Control c: page.getChildren())
			if ((c instanceof org.eclipse.swt.widgets.Spinner s) && (s.getData("dbgl.customInt") instanceof Integer i))
				result[i] = s;
		return result;
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
