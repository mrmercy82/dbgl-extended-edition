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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.repository.TitledEntityRepository;
import org.dbgl.model.repository.TitledEntityRepository.Entry;
import org.dbgl.model.repository.TitledEntityRepository.Similar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;


/**
 * Alpha 53: similar entries of the lists of developers, publishers and genres ("Sierra On-Line", "Sierra On-Line, Inc.",
 * "Sierra Online"; "Strategy" and "Strategy/Tactics"), offered after "Database cleanup" to be merged by hand: each group
 * ticked gets the name chosen, the profiles with the other names get it too. Nothing is ticked at first. The result: the
 * number of entries merged into others, or null when cancelled.
 */
public class SimilarEntriesDialog extends SizeControlledButtonDialog<Integer> {

	private final List<Similar> groups_;
	private final List<Button> checks_ = new ArrayList<>();
	private final List<Combo> keeps_ = new ArrayList<>();

	public SimilarEntriesDialog(Shell parent, List<Similar> groups) {
		super(parent, "similarentriesdialog");
		groups_ = groups;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.similarentries.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(2, false));
		Label info = new Label(contents_, SWT.WRAP);
		info.setText(text_.get("dialog.similarentries.info"));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		infoData.widthHint = 640;
		info.setLayoutData(infoData);
		Button all = new Button(contents_, SWT.PUSH);
		all.setText(text_.get("button.all"));
		Button none = new Button(contents_, SWT.PUSH);
		none.setText(text_.get("button.none"));

		ScrolledComposite scroll = new ScrolledComposite(contents_, SWT.V_SCROLL | SWT.BORDER);
		GridData scrollData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		scrollData.heightHint = 360;
		scroll.setLayoutData(scrollData);
		Composite rows = new Composite(scroll, SWT.NONE);
		rows.setLayout(new GridLayout(3, false));
		for (Similar g: groups_) {
			Button check = new Button(rows, SWT.CHECK);
			check.setText(text_.get("dialog.similarentries.list." + g.table().toLowerCase()));
			Combo keep = new Combo(rows, SWT.READ_ONLY);
			for (Entry e: g.entries())
				keep.add(text_.get("dialog.similarentries.entry", new Object[] {e.name(), e.uses()}));
			keep.select(0);
			keep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
			Label others = new Label(rows, SWT.NONE);
			StringBuilder sb = new StringBuilder();
			for (Entry e: g.entries())
				sb.append(sb.length() == 0 ? "": "  |  ").append(e.name());
			others.setText(sb.toString());
			checks_.add(check);
			keeps_.add(keep);
		}
		scroll.setContent(rows);
		scroll.setExpandHorizontal(true);
		scroll.setExpandVertical(true);
		scroll.setMinSize(rows.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		all.addListener(SWT.Selection, e -> checks_.forEach(c -> c.setSelection(true)));
		none.addListener(SWT.Selection, e -> checks_.forEach(c -> c.setSelection(false)));

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				int merged = 0;
				try {
					TitledEntityRepository repo = new TitledEntityRepository();
					for (int i = 0; i < groups_.size(); i++) {
						if (!checks_.get(i).getSelection())
							continue;
						Similar g = groups_.get(i);
						int keep = g.entries().get(keeps_.get(i).getSelectionIndex()).id();
						List<Integer> others = g.entries().stream().map(Entry::id).filter(id -> id != keep).toList();
						repo.mergeInto(g.table(), keep, others);
						merged += others.size();
					}
				} catch (SQLException e) {
					Mess_.on(shell_).exception(e).warning();
				}
				result_ = merged;
				shell_.close();
			}
		});
	}
}
