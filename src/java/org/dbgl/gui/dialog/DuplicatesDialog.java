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

import java.util.ArrayList;
import java.util.List;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.util.DuplicateProfiles;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * Profiles that start the same game, grouped. In each group the profile with the most information is kept (not ticked); the
 * ticked ones are merged into it (what only they have is taken over) and then removed. The result is the number of profiles
 * removed, or null when cancelled.
 */
public class DuplicatesDialog extends SizeControlledButtonDialog<Integer> {

	/** a line of the table: the heading of a group (profile null) or one profile of it */
	record Row(DuplicateProfiles.Group group, Profile profile) {
	}

	private final List<DuplicateProfiles.Group> groups_;
	private final List<Row> rows_ = new ArrayList<>();

	public DuplicatesDialog(Shell parent, List<DuplicateProfiles.Group> groups) {
		super(parent, "duplicatesdialog");
		groups_ = groups;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.duplicates.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));
		Label info = new Label(contents_, SWT.WRAP);
		info.setText(text_.get("dialog.duplicates.info"));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		infoData.widthHint = 640;
		info.setLayoutData(infoData);

		Table table = new Table(contents_, SWT.CHECK | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = 320;
		table.setLayoutData(tableData);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		String[] keys = {"dialog.duplicates.column.profile", "dialog.duplicates.column.what"};
		int[] widths = {560, 300};
		for (int i = 0; i < keys.length; i++) {
			TableColumn col = new TableColumn(table, SWT.NONE);
			col.setText(text_.get(keys[i]));
			col.setWidth(widths[i]);
		}
		org.eclipse.swt.graphics.Color gray = shell_.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);
		for (DuplicateProfiles.Group g: groups_) {
			TableItem head = new TableItem(table, SWT.NONE);
			head.setText(0, g.program());
			head.setForeground(gray);
			head.setGrayed(true);
			rows_.add(new Row(g, null));
			for (int i = 0; i < g.profiles().size(); i++) {
				TableItem item = new TableItem(table, SWT.NONE);
				item.setText(0, "    " + DuplicateProfiles.describe(g.profiles().get(i)));
				item.setChecked(i > 0);
				rows_.add(new Row(g, g.profiles().get(i)));
			}
		}
		// a group heading cannot be ticked; and one profile of each group always stays
		table.addListener(SWT.Selection, e -> {
			if (e.detail != SWT.CHECK)
				return;
			TableItem item = (TableItem)e.item;
			Row row = rows_.get(table.indexOf(item));
			if (row.profile() == null) {
				item.setChecked(false);
				return;
			}
			showWhat(table);
		});
		showWhat(table);
		table.deselectAll();

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				int remove = 0;
				for (int i = 0; i < rows_.size(); i++)
					if ((rows_.get(i).profile() != null) && table.getItem(i).getChecked())
						remove++;
				if (remove == 0) {
					shell_.close();
					return;
				}
				if (!Mess_.on(shell_).key("dialog.duplicates.confirm", new Object[] {remove}).confirm())
					return;
				int removed = 0;
				StringBuilder failed = new StringBuilder();
				for (DuplicateProfiles.Group g: groups_) {
					Profile keep = null;
					List<Profile> others = new ArrayList<>();
					for (int i = 0; i < rows_.size(); i++) {
						Row r = rows_.get(i);
						if ((r.group() != g) || (r.profile() == null))
							continue;
						if (table.getItem(i).getChecked())
							others.add(r.profile());
						else if (keep == null)
							keep = r.profile();
					}
					if ((keep == null) || others.isEmpty())
						continue;
					try {
						DuplicateProfiles.merge(keep, others);
						new ProfileRepository().update(keep);
						for (Profile o: others) {
							new ProfileRepository().movePlayTime(o, keep); // the play time is not lost
							new ProfileRepository().remove(o, true, true, false); // screenshots are kept
							removed++;
						}
					} catch (Exception e) {
						failed.append(keep.getTitle()).append(": ").append(e.getMessage()).append('\n');
					}
				}
				if (failed.length() > 0)
					Mess_.on(shell_).txt(failed.toString().trim()).warning();
				result_ = removed;
				shell_.close();
			}
		});
		okButton_.setText(text_.get("dialog.duplicates.merge"));
		if (okButton_.getLayoutData() instanceof GridData d) // the longer text must fit
			d.widthHint = Math.max(d.widthHint, okButton_.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
	}

	/** second column: kept or merged into the kept one; a group with all ticked keeps its first */
	private void showWhat(Table table) {
		for (DuplicateProfiles.Group g: groups_) {
			int first = -1;
			boolean anyKept = false;
			for (int i = 0; i < rows_.size(); i++)
				if ((rows_.get(i).group() == g) && (rows_.get(i).profile() != null)) {
					if (first == -1)
						first = i;
					anyKept |= !table.getItem(i).getChecked();
				}
			if (!anyKept && (first != -1))
				table.getItem(first).setChecked(false);
			for (int i = 0; i < rows_.size(); i++)
				if ((rows_.get(i).group() == g) && (rows_.get(i).profile() != null))
					table.getItem(i).setText(1, text_.get(table.getItem(i).getChecked() ? "dialog.duplicates.mergedinto": "dialog.duplicates.kept"));
		}
	}
}
