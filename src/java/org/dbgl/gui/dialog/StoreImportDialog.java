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

import java.util.List;
import java.util.Set;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.TextService;
import org.dbgl.util.StoreGameImport;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * The GOG and Steam DOS games found in a folder; the ticked ones get a profile for the DOSBox version chosen. Games that have a
 * profile already (same main program) are not ticked. The result is the number of profiles added, or null when cancelled.
 */
public class StoreImportDialog extends SizeControlledButtonDialog<Integer> {

	private final List<StoreGameImport.Found> found_;
	private final List<DosboxVersion> versions_;
	private final DosboxVersion defaultVersion_;
	/** the main programs of the profiles there are (DuplicateProfiles.keyOf) */
	private final Set<String> existing_;

	public StoreImportDialog(Shell parent, List<StoreGameImport.Found> found, List<DosboxVersion> versions, DosboxVersion defaultVersion, Set<String> existing) {
		this(parent, found, versions, defaultVersion, existing, -1);
	}

	/** an eXo collection (eXoDOS): its games, how many of them are not installed yet (alpha 48); -1: GOG and Steam */
	private final int notInstalled_;
	private final boolean exo_;
	/** ticked, by the index in found_ (the table may show only some of them, filtered) */
	private boolean[] ticked_;

	public StoreImportDialog(Shell parent, List<StoreGameImport.Found> found, List<DosboxVersion> versions, DosboxVersion defaultVersion, Set<String> existing, int notInstalled) {
		super(parent, notInstalled >= 0 ? "exoimportdialog": "storeimportdialog");
		found_ = found;
		versions_ = versions;
		defaultVersion_ = defaultVersion;
		existing_ = existing;
		notInstalled_ = notInstalled;
		exo_ = notInstalled >= 0;
	}

	/** alpha 52: an own LaunchBox collection, like an eXo collection (filter, the user picks); its own title and text */
	private String titleKey_;
	private String info_;

	public static StoreImportDialog forLaunchBox(Shell parent, List<StoreGameImport.Found> found, List<DosboxVersion> versions, DosboxVersion defaultVersion,
			Set<String> existing, String info) {
		StoreImportDialog d = new StoreImportDialog(parent, found, versions, defaultVersion, existing, 0);
		d.titleKey_ = "dialog.launchboximport.title";
		d.info_ = info;
		return d;
	}

	private String secondKey_;
	private java.util.function.Function<StoreGameImport.Found, String> second_;

	/** alpha 52: what the second column shows instead of the collection (the start file guessed, for the games in a folder) */
	public StoreImportDialog showing(String headerKey, java.util.function.Function<StoreGameImport.Found, String> value) {
		secondKey_ = headerKey;
		second_ = value;
		return this;
	}

	/** alpha 52: the same window under another title, with a line of its own above the list (the games in a folder) */
	public static StoreImportDialog titled(Shell parent, List<StoreGameImport.Found> found, List<DosboxVersion> versions, DosboxVersion defaultVersion,
			Set<String> existing, String titleKey, String info) {
		StoreImportDialog d = new StoreImportDialog(parent, found, versions, defaultVersion, existing, 0);
		d.titleKey_ = titleKey;
		d.info_ = info;
		return d;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get(titleKey_ != null ? titleKey_: exo_ ? "dialog.exoimport.title": "dialog.storeimport.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(2, false));
		Label info = new Label(contents_, SWT.WRAP);
		info.setText(info_ != null ? info_: exo_ ? text_.get("dialog.exoimport.info", new Object[] {found_.size(), notInstalled_}): text_.get("dialog.storeimport.info"));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		infoData.widthHint = 640;
		info.setLayoutData(infoData);

		new Label(contents_, SWT.NONE).setText(text_.get("dialog.storeimport.dosbox"));
		Combo version = new Combo(contents_, SWT.READ_ONLY);
		for (DosboxVersion v: versions_)
			version.add(v.getTitle());
		version.select(Math.max(0, versions_.indexOf(preselected(versions_, defaultVersion_))));
		// a game that boots Windows 95 or needs its CD on the IDE bus only runs in DOSBox-X: that one is taken for it
		DosboxVersion x = versions_.stream().filter(v -> org.dbgl.service.SettingsService.DOSBOX_X_DOSBOX_FAM.title_.equals(v.getFamily())).findFirst().orElse(null);
		org.eclipse.swt.widgets.Button useAdvice = new org.eclipse.swt.widgets.Button(contents_, SWT.CHECK);
		useAdvice.setText(text_.get("dialog.storeimport.useadvice"));
		useAdvice.setSelection(true);
		useAdvice.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		List<String> advice = found_.stream().map(StoreGameImport::needsDosboxX).toList();

		// many games (eXoDOS has thousands): a search field
		org.eclipse.swt.widgets.Text filter = null;
		if (exo_ || (found_.size() > 30)) {
			new Label(contents_, SWT.NONE).setText(text_.get("dialog.exoimport.filter"));
			filter = new org.eclipse.swt.widgets.Text(contents_, SWT.BORDER | SWT.SEARCH | SWT.ICON_CANCEL);
			filter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		}
		// all games ticked or none at once (alpha 48); with a search, those shown. Since alpha 53 above the list: below it the
		// row could be cut off in a window that is not high enough
		org.eclipse.swt.widgets.Composite allNone = new org.eclipse.swt.widgets.Composite(contents_, SWT.NONE);
		org.eclipse.swt.layout.RowLayout allNoneLayout = new org.eclipse.swt.layout.RowLayout();
		allNoneLayout.marginLeft = allNoneLayout.marginRight = 0;
		allNone.setLayout(allNoneLayout);
		allNone.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		org.eclipse.swt.widgets.Button[] allNoneButtons = new org.eclipse.swt.widgets.Button[2];
		for (int i = 0; i < 2; i++) {
			allNoneButtons[i] = new org.eclipse.swt.widgets.Button(allNone, SWT.PUSH);
			allNoneButtons[i].setText(text_.get(i == 0 ? "button.all": "button.none"));
		}
		Table table = new Table(contents_, SWT.CHECK | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		tableData.heightHint = 300;
		table.setLayoutData(tableData);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		String[] keys = {"dialog.storeimport.column.game", "dialog.storeimport.column.store", "dialog.storeimport.column.advice", "dialog.storeimport.column.folder"};
		int[] widths = {240, second_ != null ? 160: 90, 240, 360};
		for (int i = 0; i < keys.length; i++) {
			TableColumn col = new TableColumn(table, SWT.NONE);
			col.setText(text_.get(exo_ && (i == 1) ? "dialog.exoimport.column.collection": (i == 1) && (secondKey_ != null) ? secondKey_: keys[i]));
			col.setWidth(widths[i]);
		}
		ticked_ = new boolean[found_.size()];
		String[][] rows = new String[found_.size()][];
		for (int r = 0; r < found_.size(); r++) {
			StoreGameImport.Found f = found_.get(r);
			boolean known = isKnown(f);
			String a = advice.get(r) == null ? "": text_.get(x != null ? "dialog.storeimport.advice.x": "dialog.storeimport.advice.missing",
				new Object[] {text_.get("dialog.storeimport.reason." + advice.get(r))});
			rows[r] = new String[] {known ? text_.get("dialog.storeimport.known", new Object[] {f.title()}): f.title(), second_ != null ? second_.apply(f): f.store(), a, f.gameDir().getPath()};
			ticked_[r] = !known && !exo_; // from a collection the user picks the games
		}
		org.eclipse.swt.widgets.Text filterField = filter;
		Runnable fill = () -> {
			String q = filterField == null ? "": filterField.getText().trim().toLowerCase(java.util.Locale.ROOT);
			table.setRedraw(false);
			table.removeAll();
			for (int r = 0; r < rows.length; r++) {
				if (!q.isEmpty() && !rows[r][0].toLowerCase(java.util.Locale.ROOT).contains(q))
					continue;
				TableItem item = new TableItem(table, SWT.NONE);
				item.setText(rows[r]);
				item.setChecked(ticked_[r]);
				item.setData(r);
			}
			table.setRedraw(true);
		};
		fill.run();
		table.addListener(SWT.Selection, e -> {
			if ((e.detail == SWT.CHECK) && (e.item instanceof TableItem item))
				ticked_[(Integer)item.getData()] = item.getChecked();
		});
		if (filter != null)
			filter.addListener(SWT.Modify, e -> fill.run());
		for (int i = 0; i < 2; i++) {
			boolean all = i == 0;
			allNoneButtons[i].addListener(SWT.Selection, e -> {
				for (TableItem item: table.getItems()) {
					item.setChecked(all);
					ticked_[(Integer)item.getData()] = all;
				}
			});
		}

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				DosboxVersion v = versions_.get(Math.max(0, version.getSelectionIndex()));
				int added = 0;
				StringBuilder failed = new StringBuilder();
				for (int i = 0; i < found_.size(); i++) {
					if (!ticked_[i])
						continue;
					try {
						DosboxVersion use = (useAdvice.getSelection() && (advice.get(i) != null) && (x != null)) ? x: v;
						Profile p = StoreGameImport.toProfile(found_.get(i), use, TextService.getInstance());
						p = new ProfileRepository().add(p);
						StoreGameImport.addStats(found_.get(i), p); // alpha 52: starts and play time of LaunchBox
						copyCover(found_.get(i), p);
						added++;
					} catch (Exception e) {
						failed.append(found_.get(i).title()).append(": ").append(e.getMessage()).append('\n');
					}
				}
				if (failed.length() > 0)
					Mess_.on(shell_).txt(failed.toString().trim()).warning();
				result_ = added;
				shell_.close();
			}
		});
		okButton_.setText(text_.get("dialog.storeimport.add"));
	}

	/**
	 * alpha 53: the version chosen at first: the newest DOSBox Staging entered (most DOS games run well with it; those that need
	 * DOSBox-X get that by the advice), else the default version.
	 */
	public static DosboxVersion preselected(List<DosboxVersion> versions, DosboxVersion defaultVersion) {
		DosboxVersion best = null;
		for (DosboxVersion v: versions)
			if (org.dbgl.service.SettingsService.STAGING_DOSBOX_FAM.title_.equals(v.getFamily())
					&& ((best == null) || (org.dbgl.util.DosboxUpdateChecker.compareVersions(v.getVersion(), best.getVersion()) > 0)))
				best = v;
		return best != null ? best: defaultVersion;
	}

	/** the cover of the collection into the captures of the profile, where DBGL shows its pictures; since alpha 48 also the back of the box and screenshots */
	private static void copyCover(StoreGameImport.Found f, Profile p) {
		if (f.meta() == null)
			return;
		java.util.List<java.io.File> pictures = new java.util.ArrayList<>();
		java.io.File cover = (f.meta().cover() == null) ? null: f.meta().cover().get();
		pictures.add(cover);
		if (f.meta().pictures() != null)
			pictures.addAll(f.meta().pictures().get());
		String[] names = {"cover", "picture-back", "screenshot-title", "screenshot-gameplay"}; // the cover first by name
		java.io.File dir = p.getCanonicalCaptures();
		for (int i = 0; i < pictures.size(); i++) {
			java.io.File picture = pictures.get(i);
			if (picture == null)
				continue;
			String name = (i < names.length) ? names[i]: "picture-" + i;
			try {
				java.nio.file.Files.createDirectories(dir.toPath());
				java.nio.file.Files.copy(picture.toPath(), new java.io.File(dir, name + picture.getName().substring(picture.getName().lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT)).toPath(),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (java.io.IOException | RuntimeException e) {
				// the profile is there, just without this picture
			}
		}
	}

	/** a profile starts this game already */
	private boolean isKnown(StoreGameImport.Found f) {
		// imported before: its folder is the last line of the notes ("eXoDOS: ..."); cheap, for thousands of games
		if (existing_.contains(FOLDER + f.gameDir().getPath().toLowerCase(java.util.Locale.ROOT)))
			return true;
		if (exo_)
			return false;
		if (existing_.isEmpty() || defaultVersion_ == null)
			return false;
		try {
			Profile p = StoreGameImport.toProfile(f, defaultVersion_, TextService.getInstance());
			String key = org.dbgl.util.DuplicateProfiles.keyOfLoadedPublic(p);
			return (key != null) && existing_.contains(key.toLowerCase(java.util.Locale.ROOT));
		} catch (Exception e) {
			return false;
		}
	}

	/** the main programs of the profiles there are */
	public static Set<String> existingKeys(List<Profile> profiles) {
		Set<String> keys = new java.util.HashSet<>();
		for (Profile p: profiles) {
			String k = org.dbgl.util.DuplicateProfiles.keyOf(p);
			if (k != null)
				keys.add(k.toLowerCase(java.util.Locale.ROOT));
			String notes = org.apache.commons.lang3.StringUtils.defaultString(p.getNotes()).trim();
			String last = notes.substring(notes.lastIndexOf('\n') + 1);
			int colon = last.indexOf(": ");
			if (colon > 0)
				keys.add(FOLDER + last.substring(colon + 2).trim().toLowerCase(java.util.Locale.ROOT));
		}
		return keys;
	}

	private static final String FOLDER = "folder:";
}
