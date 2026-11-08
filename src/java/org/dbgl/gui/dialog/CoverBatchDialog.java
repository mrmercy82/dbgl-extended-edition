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
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.BusyTask;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.SearchEngineImageInformation;
import org.dbgl.model.SearchEngineImageInformation.SearchEngineImageType;
import org.dbgl.model.WebProfile;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.ImageService;
import org.dbgl.service.SettingsService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.searchengine.WebSearchEngine;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * Covers for many profiles at once: for profiles without any image, a game database (TheGamesDB or SteamGridDB) is asked for
 * each title; the cover found is shown, and the ticked ones are saved in the captures folder of their profile. A title found
 * exactly is ticked, a similar one is not (to look at first). The result is the number of images saved, or null.
 */
public class CoverBatchDialog extends SizeControlledButtonDialog<Integer> {

	/** at most this many profiles per search: the game databases count the queries (TheGamesDB per key, shared by DBGL users) */
	public static final int MAX_PROFILES = 100;
	private static final int THUMB = 64;
	/** all sources DBGL can use here (ScreenScraper only with its developer access) */
	private final List<WebSearchEngine> engines_ = SettingsService.availableWebSearchEngines();

	/** what was found for a profile: the game, the cover (its data), and whether the title is the same */
	record Found(Profile profile, WebProfile game, SearchEngineImageInformation image, ImageData data, boolean exact, String error) {
	}

	private final List<Profile> profiles_;
	private final List<Found> found_ = new ArrayList<>();
	private final List<Image> thumbs_ = new ArrayList<>();
	private Table table_;
	private Combo engine_;
	private Label summary_;

	public CoverBatchDialog(Shell parent, List<Profile> profiles) {
		super(parent, "coverbatchdialog");
		profiles_ = profiles.size() > MAX_PROFILES ? profiles.subList(0, MAX_PROFILES): profiles;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.coverbatch.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(3, false));
		Label info = new Label(contents_, SWT.WRAP);
		info.setText(text_.get("dialog.coverbatch.info", new Object[] {profiles_.size(), MAX_PROFILES}));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		infoData.widthHint = 640;
		info.setLayoutData(infoData);

		new Label(contents_, SWT.NONE).setText(text_.get("dialog.coverbatch.source"));
		engine_ = new Combo(contents_, SWT.READ_ONLY);
		String current = SettingsService.getInstance().getValue("gui", "searchengine");
		for (WebSearchEngine e: engines_) {
			engine_.add(e.getName());
			if (e.getSimpleName().equals(current))
				engine_.select(engine_.getItemCount() - 1);
		}
		if (engine_.getSelectionIndex() < 0)
			engine_.select(0);
		Button search = new Button(contents_, SWT.PUSH);
		search.setText(text_.get("dialog.coverbatch.search"));
		search.addListener(SWT.Selection, e -> search());

		table_ = new Table(contents_, SWT.CHECK | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		tableData.heightHint = 380;
		table_.setLayoutData(tableData);
		table_.setHeaderVisible(true);
		table_.setLinesVisible(true);
		String[] keys = {"dialog.coverbatch.column.cover", "dialog.coverbatch.column.profile", "dialog.coverbatch.column.found"};
		int[] widths = {THUMB + 40, 280, 320};
		for (int i = 0; i < keys.length; i++) {
			TableColumn col = new TableColumn(table_, SWT.NONE);
			col.setText(text_.get(keys[i]));
			col.setWidth(widths[i]);
		}
		// only a row with a cover can be ticked
		table_.addListener(SWT.Selection, e -> {
			if ((e.detail == SWT.CHECK) && (found_.get(table_.indexOf((TableItem)e.item)).data() == null))
				((TableItem)e.item).setChecked(false);
			updateSummary();
		});
		summary_ = new Label(contents_, SWT.NONE);
		summary_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		shell_.addDisposeListener(e -> thumbs_.forEach(Image::dispose));

		createGoCancelButtons("dialog.coverbatch.save", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				save();
			}
		});
	}

	@Override
	protected void onShellOpened() {
		search();
	}

	/** ask the game database for every profile, in the background with a progress bar (and Cancel) */
	private void search() {
		WebSearchEngine engine = engines_.get(Math.max(0, engine_.getSelectionIndex()));
		String engineName = engine.getSimpleName();
		// a source with a key of its own (SteamGridDB, MobyGames): not without it
		String key = SettingsService.getInstance().getValue(engineName, "api_key");
		if ((key != null) && StringUtils.isBlank(key) && !"thegamesdb".equals(engineName)) {
			Mess_.on(shell_).key("dialog.coverbatch.nokey", new Object[] {engine.getName()}).warning();
			return;
		}
		String[] platforms = SettingsService.getInstance().getValues(engine.getSimpleName(), "platform_filter");
		AtomicInteger progress = new AtomicInteger();
		List<Found> results;
		try {
			results = BusyTask.run(shell_, text_.get("searchengine.searching", new Object[] {engine.getName()}), progress, profiles_.size(), () -> {
				List<Found> list = new ArrayList<>();
				for (Profile p: profiles_) {
					if (Thread.currentThread().isInterrupted())
						throw new BusyTask.Cancelled();
					list.add(find(engine, p, platforms));
					progress.incrementAndGet();
				}
				return list;
			});
		} catch (BusyTask.Cancelled e) {
			return;
		} catch (Exception e) {
			Mess_.on(shell_).exception(e).warning();
			return;
		}
		show(results);
	}

	/** the best game for the title (the same title, else the first one), and its cover (the front of the box, if marked) */
	static Found find(WebSearchEngine engine, Profile p, String[] platforms) {
		try {
			List<WebProfile> games = engine.getEntries(p.getTitle(), platforms);
			if (games.isEmpty())
				return new Found(p, null, null, null, false, null);
			String title = StringUtils.strip(p.getTitle());
			WebProfile game = games.stream().filter(g -> StringUtils.strip(g.getTitle()).equalsIgnoreCase(title)).findFirst().orElse(games.get(0));
			boolean exact = StringUtils.strip(game.getTitle()).equalsIgnoreCase(title);
			game = engine.getEntryDetailedInformation(game);
			SearchEngineImageInformation[] images = engine.getEntryImages(game, 10, 1, true);
			int best = -1;
			for (int i = 0; i < images.length; i++) {
				if (images[i].getType() != SearchEngineImageType.COVER_ART)
					continue;
				String d = StringUtils.defaultString(images[i].getDescription()).toLowerCase();
				if ((best == -1) || (d.contains("boxart") && d.contains("front")))
					best = i;
				if (d.contains("boxart") && d.contains("front"))
					break;
			}
			if ((best == -1) && (images.length > 0))
				best = 0; // no cover (e.g. Pouët): the first image, a screenshot
			if (best == -1)
				return new Found(p, game, null, null, exact, null);
			ImageData data = game.getWebImage(best);
			return new Found(p, game, images[best], data, exact, null);
		} catch (Exception e) {
			return new Found(p, null, null, null, false, String.valueOf(e.getMessage() == null ? e: e.getMessage()));
		}
	}

	private void show(List<Found> results) {
		table_.removeAll();
		thumbs_.forEach(Image::dispose);
		thumbs_.clear();
		found_.clear();
		found_.addAll(results);
		for (Found f: found_) {
			TableItem item = new TableItem(table_, SWT.NONE);
			item.setText(1, f.profile().getTitle());
			if (f.data() != null) {
				Image thumb = new Image(shell_.getDisplay(), fit(f.data()));
				thumbs_.add(thumb);
				item.setImage(0, thumb);
				item.setText(2, f.game().getTitle() + (StringUtils.isBlank(f.game().getYear()) ? "": " (" + f.game().getYear() + ")")
						+ (f.exact() ? "": "  " + text_.get("dialog.coverbatch.similar")));
				item.setChecked(f.exact());
			} else {
				item.setText(2, f.error() != null ? text_.get("dialog.coverbatch.error", new Object[] {f.error()})
						: f.game() != null ? text_.get("dialog.coverbatch.nocover", new Object[] {f.game().getTitle()}): text_.get("dialog.coverbatch.notfound"));
				item.setForeground(shell_.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
			}
		}
		updateSummary();
	}

	/** the image as large as fits into THUMB x THUMB, keeping its shape */
	private static ImageData fit(ImageData d) {
		double f = Math.min((double)THUMB / d.width, (double)THUMB / d.height);
		return d.scaledTo(Math.max(1, (int)Math.round(d.width * f)), Math.max(1, (int)Math.round(d.height * f)));
	}

	private void updateSummary() {
		int withCover = (int)found_.stream().filter(f -> f.data() != null).count();
		int ticked = 0;
		for (TableItem item: table_.getItems())
			if (item.getChecked())
				ticked++;
		summary_.setText(text_.get("dialog.coverbatch.summary", new Object[] {found_.size(), withCover, ticked}));
	}

	private void save() {
		int saved = 0;
		StringBuilder failed = new StringBuilder();
		for (int i = 0; i < found_.size(); i++) {
			Found f = found_.get(i);
			if (!table_.getItem(i).getChecked() || (f.data() == null))
				continue;
			File dir = f.profile().getCanonicalCaptures();
			String description = FilesUtils.toSafeFilenameForWebImages(f.image().getDescription());
			File file = new File(dir, text_.get("dialog.profile.mobygames.coverartfilename", new Object[] {0, description}) + ".jpg");
			try {
				java.nio.file.Files.createDirectories(dir.toPath());
				if (!FilesUtils.isExistingFile(file)) {
					ImageService.save(shell_.getDisplay(), f.data(), file.getPath());
					saved++;
				}
			} catch (java.io.IOException | SWTException e) {
				failed.append(file.getPath()).append(": ").append(e.getMessage()).append('\n');
			}
		}
		if (failed.length() > 0)
			Mess_.on(shell_).key("general.error.savefile", failed.toString().trim()).warning();
		result_ = saved;
		shell_.close();
	}
}
