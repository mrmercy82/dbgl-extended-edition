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
import java.util.List;

import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.util.SeriesSuggester;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;


/**
 * Alpha 49: game series suggested from the titles of the profiles (see {@link SeriesSuggester}): each series with its profiles
 * and the part each would get; what is ticked is taken over. @return the number of profiles put into a series, null if cancelled
 */
public class SeriesDialog extends SizeControlledButtonDialog<Integer> {

	private final List<SeriesSuggester.Suggestion> suggestions_;
	private Tree tree_;
	private Label count_;

	public SeriesDialog(Shell parent, List<SeriesSuggester.Suggestion> suggestions) {
		super(parent, "seriesdialog");
		suggestions_ = suggestions;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.series.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));
		Label info = new Label(contents_, SWT.WRAP);
		info.setText(text_.get("dialog.series.info"));
		GridData infoData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		infoData.widthHint = 520;
		info.setLayoutData(infoData);
		if (suggestions_.stream().anyMatch(s -> !s.sure())) { // found by the first words alone: listed last, not ticked
			Label unsure = new Label(contents_, SWT.WRAP);
			unsure.setText(text_.get("dialog.series.unsure"));
			GridData unsureData = new GridData(SWT.FILL, SWT.CENTER, true, false);
			unsureData.widthHint = 520;
			unsure.setLayoutData(unsureData);
		}

		tree_ = new Tree(contents_, SWT.CHECK | SWT.BORDER | SWT.V_SCROLL | SWT.FULL_SELECTION);
		tree_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tree_.setHeaderVisible(true);
		TreeColumn nameColumn = new TreeColumn(tree_, SWT.NONE);
		nameColumn.setText(text_.get("dialog.series.column.series"));
		nameColumn.setWidth(430);
		TreeColumn partColumn = new TreeColumn(tree_, SWT.RIGHT);
		partColumn.setText(text_.get("dialog.profile.seriespart"));
		partColumn.setWidth(70);
		for (SeriesSuggester.Suggestion s: suggestions_) {
			TreeItem series = new TreeItem(tree_, SWT.NONE);
			series.setText(new String[] {s.series(), ""});
			series.setData(s);
			series.setChecked(s.sure());
			for (SeriesSuggester.Member m: s.members()) {
				TreeItem item = new TreeItem(series, SWT.NONE);
				item.setText(new String[] {String.valueOf(m.profile().getTitle()), m.part() > 0 ? String.valueOf(m.part()): ""});
				item.setData(m);
				item.setChecked(s.sure());
			}
			series.setExpanded(true);
		}
		tree_.addListener(SWT.Selection, e -> {
			if ((e.detail != SWT.CHECK) || !(e.item instanceof TreeItem item))
				return;
			if (item.getParentItem() == null) { // a series: all of its profiles with it
				item.setGrayed(false);
				for (TreeItem child: item.getItems())
					child.setChecked(item.getChecked());
			} else {
				updateParent(item.getParentItem());
			}
			updateCount();
		});

		Composite row = new Composite(contents_, SWT.NONE);
		GridLayout rowLayout = new GridLayout(3, false);
		rowLayout.marginWidth = 0;
		row.setLayout(rowLayout);
		row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button all = new Button(row, SWT.PUSH);
		all.setText(text_.get("button.all"));
		all.addListener(SWT.Selection, e -> tickAll(true));
		wide(all);
		Button none = new Button(row, SWT.PUSH);
		none.setText(text_.get("button.none"));
		none.addListener(SWT.Selection, e -> tickAll(false));
		wide(none);
		count_ = new Label(row, SWT.NONE);
		count_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		createGoCancelButtons("dialog.series.apply", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				apply();
			}
		});
		okCancelButtons_.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));
		updateCount();
	}

	private static void wide(Button b) {
		GridData data = new GridData();
		data.widthHint = Math.max(90, b.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
		b.setLayoutData(data);
	}

	private static void updateParent(TreeItem series) {
		int ticked = 0;
		for (TreeItem child: series.getItems())
			if (child.getChecked())
				ticked++;
		series.setChecked(ticked > 0);
		series.setGrayed((ticked > 0) && (ticked < series.getItemCount())); // some of them only
	}

	private void tickAll(boolean ticked) {
		for (TreeItem series: tree_.getItems()) {
			series.setGrayed(false);
			series.setChecked(ticked);
			for (TreeItem child: series.getItems())
				child.setChecked(ticked);
		}
		updateCount();
	}

	private int ticked() {
		int n = 0;
		for (TreeItem series: tree_.getItems())
			for (TreeItem child: series.getItems())
				if (child.getChecked())
					n++;
		return n;
	}

	private void updateCount() {
		int n = ticked();
		count_.setText(text_.get("dialog.series.count", new Object[] {n}));
		okButton_.setEnabled(n > 0);
	}

	private void apply() {
		ProfileRepository repo = new ProfileRepository();
		int done = 0;
		try {
			for (TreeItem series: tree_.getItems()) {
				String name = ((SeriesSuggester.Suggestion)series.getData()).series();
				for (TreeItem child: series.getItems()) {
					if (!child.getChecked())
						continue;
					SeriesSuggester.Member m = (SeriesSuggester.Member)child.getData();
					repo.setSeries(m.profile(), name, m.part());
					done++;
				}
			}
		} catch (SQLException e) {
			Mess_.on(shell_).exception(e).warning();
		}
		result_ = done;
		shell_.close();
	}
}
