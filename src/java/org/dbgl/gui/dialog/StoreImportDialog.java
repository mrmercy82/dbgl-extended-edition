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
		super(parent, "storeimportdialog");
		found_ = found;
		versions_ = versions;
		defaultVersion_ = defaultVersion;
		existing_ = existing;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.storeimport.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(2, false));
		Label info = new Label(contents_, SWT.WRAP);
		info.setText(text_.get("dialog.storeimport.info"));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		infoData.widthHint = 640;
		info.setLayoutData(infoData);

		new Label(contents_, SWT.NONE).setText(text_.get("dialog.storeimport.dosbox"));
		Combo version = new Combo(contents_, SWT.READ_ONLY);
		for (DosboxVersion v: versions_)
			version.add(v.getTitle());
		version.select(Math.max(0, versions_.indexOf(defaultVersion_)));
		// a game that boots Windows 95 or needs its CD on the IDE bus only runs in DOSBox-X: that one is taken for it
		DosboxVersion x = versions_.stream().filter(v -> org.dbgl.service.SettingsService.DOSBOX_X_DOSBOX_FAM.title_.equals(v.getFamily())).findFirst().orElse(null);
		org.eclipse.swt.widgets.Button useAdvice = new org.eclipse.swt.widgets.Button(contents_, SWT.CHECK);
		useAdvice.setText(text_.get("dialog.storeimport.useadvice"));
		useAdvice.setSelection(true);
		useAdvice.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		List<String> advice = found_.stream().map(StoreGameImport::needsDosboxX).toList();

		Table table = new Table(contents_, SWT.CHECK | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		tableData.heightHint = 300;
		table.setLayoutData(tableData);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		String[] keys = {"dialog.storeimport.column.game", "dialog.storeimport.column.store", "dialog.storeimport.column.advice", "dialog.storeimport.column.folder"};
		int[] widths = {240, 70, 260, 360};
		for (int i = 0; i < keys.length; i++) {
			TableColumn col = new TableColumn(table, SWT.NONE);
			col.setText(text_.get(keys[i]));
			col.setWidth(widths[i]);
		}
		for (int r = 0; r < found_.size(); r++) {
			StoreGameImport.Found f = found_.get(r);
			TableItem item = new TableItem(table, SWT.NONE);
			boolean known = isKnown(f);
			String a = advice.get(r) == null ? "": text_.get(x != null ? "dialog.storeimport.advice.x": "dialog.storeimport.advice.missing",
				new Object[] {text_.get("dialog.storeimport.reason." + advice.get(r))});
			item.setText(new String[] {known ? text_.get("dialog.storeimport.known", new Object[] {f.title()}): f.title(), f.store(), a, f.gameDir().getPath()});
			item.setChecked(!known);
		}

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				DosboxVersion v = versions_.get(Math.max(0, version.getSelectionIndex()));
				int added = 0;
				StringBuilder failed = new StringBuilder();
				for (int i = 0; i < found_.size(); i++) {
					if (!table.getItem(i).getChecked())
						continue;
					try {
						DosboxVersion use = (useAdvice.getSelection() && (advice.get(i) != null) && (x != null)) ? x: v;
						Profile p = StoreGameImport.toProfile(found_.get(i), use, TextService.getInstance());
						new ProfileRepository().add(p);
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

	/** a profile starts this game already */
	private boolean isKnown(StoreGameImport.Found f) {
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
		}
		return keys;
	}
}
