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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.WebProfile;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.util.ProfileCompletion;
import org.dbgl.util.ProfileCompletion.Assessment;
import org.dbgl.util.ProfileCompletion.State;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


/**
 * Alpha 53 (TEST24): "Complete profiles": for the profiles chosen, in this order, the title first (each looked up in the
 * database of MobyGames that comes with DBGL, else in the text files of its game folder; green: found or details there,
 * yellow: a title proposed, red: nothing found, to be typed in by double-clicking it), then the details of the game taken from
 * that database for the profiles ticked, then (if wished) the covers, which are searched by the title. The result: the number
 * of profiles changed and whether the covers are to be fetched next, or null when cancelled.
 */
public class CompleteProfilesDialog extends SizeControlledButtonDialog<CompleteProfilesDialog.Result> {

	public record Result(int changed, boolean covers) {
	}

	private static final int COL_STATE = 1, COL_TITLE = 2, COL_PROPOSAL = 3, COL_SOURCE = 4, COL_YEAR = 5;

	private final List<Profile> profiles_;
	private final Assessment[] assessed_;
	private final String[] titles_; // the title as typed in by hand
	private Table table_;
	private Label status_;
	private Button covers_;
	private volatile boolean closed_;

	public CompleteProfilesDialog(Shell parent, List<Profile> profiles) {
		super(parent, "completeprofilesdialog");
		profiles_ = profiles;
		assessed_ = new Assessment[profiles.size()];
		titles_ = new String[profiles.size()];
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.complete.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(3, false));
		Label info = new Label(contents_, SWT.WRAP);
		info.setText(text_.get("dialog.complete.info"));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		infoData.widthHint = 760;
		info.setLayoutData(infoData);

		table_ = new Table(contents_, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
		table_.setHeaderVisible(true);
		table_.setLinesVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		tableData.heightHint = 380;
		table_.setLayoutData(tableData);
		String[] heads = {"", text_.get("dialog.complete.column.state"), text_.get("dialog.complete.column.title"), text_.get("dialog.complete.column.proposal"),
				text_.get("dialog.complete.column.source"), text_.get("dialog.importdir.column.year")};
		int[] widths = {28, 110, 230, 260, 110, 50};
		for (int i = 0; i < heads.length; i++) {
			TableColumn c = new TableColumn(table_, SWT.NONE);
			c.setText(heads[i]);
			c.setWidth(widths[i]);
		}
		for (int i = 0; i < profiles_.size(); i++) {
			TableItem item = new TableItem(table_, SWT.NONE);
			item.setText(COL_TITLE, profiles_.get(i).getTitle());
			item.setText(COL_STATE, text_.get("dialog.complete.state.checking"));
		}
		editTitles();

		Button withProposal = new Button(contents_, SWT.PUSH);
		withProposal.setText(text_.get("dialog.complete.tickproposals"));
		withProposal.addListener(SWT.Selection, e -> {
			for (int i = 0; i < assessed_.length; i++)
				if ((assessed_[i] != null) && (assessed_[i].proposal() != null))
					table_.getItem(i).setChecked(true);
		});
		Button none = new Button(contents_, SWT.PUSH);
		none.setText(text_.get("button.none"));
		none.addListener(SWT.Selection, e -> {
			for (TableItem item: table_.getItems())
				item.setChecked(false);
		});
		status_ = new Label(contents_, SWT.NONE);
		status_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		covers_ = new Button(contents_, SWT.CHECK);
		covers_.setText(text_.get("dialog.complete.covers"));
		covers_.setSelection(true);
		covers_.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));

		createGoCancelButtons("dialog.complete.apply", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				result_ = new Result(apply(), covers_.getSelection());
				shell_.close();
			}
		});
		shell_.addListener(SWT.Dispose, e -> closed_ = true);
		assessAll();
	}

	/** each profile looked up in the background; the rows filled as the answers come */
	private void assessAll() {
		Function<String, List<WebProfile>> lookup = ProfileCompletion.databaseLookup();
		if (lookup == null)
			status_.setText(text_.get("dialog.complete.nodatabase"));
		List<File> dirs = new ArrayList<>();
		for (Profile p: profiles_)
			dirs.add(gameDir(p));
		Thread t = new Thread(() -> {
			for (int i = 0; i < profiles_.size() && !closed_; i++)
				assess(i, lookup, dirs.get(i));
			if (!closed_)
				display_.asyncExec(() -> {
					if (!status_.isDisposed())
						status_.setText(lookup == null ? text_.get("dialog.complete.nodatabase") + "  " + summary(): summary());
				});
		}, "DBGL complete profiles");
		t.setDaemon(true);
		t.start();
	}

	private void assess(int i, Function<String, List<WebProfile>> lookup, File dir) {
		Profile p = profiles_.get(i);
		String title = titles_[i] != null ? titles_[i]: p.getTitle();
		Assessment a = ProfileCompletion.assess(title, (titles_[i] == null) && ProfileCompletion.hasDetails(p), lookup, dir);
		assessed_[i] = a;
		if (closed_)
			return;
		display_.asyncExec(() -> {
			if (table_.isDisposed())
				return;
			TableItem item = table_.getItem(i);
			item.setText(COL_STATE, text_.get("dialog.complete.state." + a.state().name().toLowerCase()));
			item.setText(COL_PROPOSAL, a.proposal() == null ? "": a.proposal().getTitle());
			item.setText(COL_SOURCE, a.source() != null ? a.source(): a.proposal() != null ? "MobyGames": "");
			item.setText(COL_YEAR, a.proposal() == null ? StringUtils.defaultString(p.getYear()): StringUtils.defaultString(a.proposal().getYear()));
			item.setBackground(COL_STATE, color(a.state()));
			if (a.state() == State.FOUND)
				item.setChecked(true); // exactly its title: the details can be taken at once
		});
	}

	private Color color(State s) {
		boolean dark = org.dbgl.gui.controls.DarkTheme.forced();
		return switch (s) {
			case DONE, FOUND -> new Color(dark ? 40: 200, dark ? 110: 240, dark ? 40: 200);
			case LIKELY -> new Color(dark ? 120: 255, dark ? 110: 240, dark ? 30: 170);
			case NONE -> new Color(dark ? 130: 255, dark ? 40: 205, dark ? 40: 205);
		};
	}

	private String summary() {
		int[] n = new int[State.values().length];
		for (Assessment a: assessed_)
			if (a != null)
				n[a.state().ordinal()]++;
		return text_.get("dialog.complete.summary", new Object[] {n[State.DONE.ordinal()] + n[State.FOUND.ordinal()], n[State.LIKELY.ordinal()], n[State.NONE.ordinal()]});
	}

	/** a double click on a title: typed in by hand, then looked up again; the row is ticked */
	private void editTitles() {
		TableEditor editor = new TableEditor(table_);
		editor.horizontalAlignment = SWT.LEFT;
		editor.grabHorizontal = true;
		table_.addListener(SWT.MouseDoubleClick, event -> {
			int index = table_.getSelectionIndex();
			if ((index < 0) || !table_.getItem(index).getBounds(COL_TITLE).contains(event.x, event.y))
				return;
			TableItem item = table_.getItem(index);
			Text text = new Text(table_, SWT.NONE);
			text.setText(item.getText(COL_TITLE));
			text.selectAll();
			text.setFocus();
			editor.setEditor(text, item, COL_TITLE);
			boolean[] done = {false};
			Runnable commit = () -> {
				if (done[0] || text.isDisposed())
					return;
				done[0] = true;
				String typed = text.getText().strip();
				text.dispose();
				if (typed.isEmpty() || typed.equals(item.getText(COL_TITLE)))
					return;
				item.setText(COL_TITLE, typed);
				item.setText(COL_STATE, text_.get("dialog.complete.state.checking"));
				item.setChecked(true);
				titles_[index] = typed;
				Function<String, List<WebProfile>> lookup = ProfileCompletion.databaseLookup();
				File dir = gameDir(profiles_.get(index));
				Thread t = new Thread(() -> assess(index, lookup, dir), "DBGL complete profiles");
				t.setDaemon(true);
				t.start();
			};
			text.addListener(SWT.FocusOut, e -> commit.run());
			text.addListener(SWT.Traverse, e -> {
				if (e.detail == SWT.TRAVERSE_RETURN)
					commit.run();
				else if (e.detail == SWT.TRAVERSE_ESCAPE) {
					done[0] = true;
					text.dispose();
				}
				if ((e.detail == SWT.TRAVERSE_RETURN) || (e.detail == SWT.TRAVERSE_ESCAPE))
					e.doit = false;
			});
		});
	}

	/** the ticked profiles: the title typed in or proposed, and the details of the game where the profile has none */
	private int apply() {
		List<Profile> changed = new ArrayList<>();
		for (int i = 0; i < profiles_.size(); i++) {
			TableItem item = table_.getItem(i);
			if (!item.getChecked())
				continue;
			Profile p = profiles_.get(i);
			Assessment a = assessed_[i];
			boolean byHand = titles_[i] != null;
			if ((a != null) && (a.proposal() != null) && (a.state() != State.DONE)) {
				changed.add(p);
			} else if (byHand && !titles_[i].equals(p.getTitle())) {
				changed.add(p);
			}
		}
		if (changed.isEmpty())
			return 0;
		org.dbgl.util.BulkUndo.remember(text_.get("dialog.complete.title"), changed);
		int count = 0;
		ProfileRepository repo = new ProfileRepository();
		for (int i = 0; i < profiles_.size(); i++) {
			Profile p = profiles_.get(i);
			if (!changed.contains(p))
				continue;
			Assessment a = assessed_[i];
			if ((a != null) && (a.proposal() != null) && (a.state() != State.DONE))
				ProfileCompletion.apply(p, ProfileCompletion.detailed(a.proposal()));
			else
				p.setTitle(titles_[i]);
			try {
				repo.updateKeepingConfigurationFile(p);
				count++;
			} catch (SQLException e) {
				Mess_.on(shell_).exception(e).warning();
				break;
			}
		}
		return count;
	}

	private static File gameDir(Profile p) {
		try {
			return p.getCombinedConfiguration().getAutoexec().getCanonicalGameDir();
		} catch (RuntimeException e) {
			return null;
		}
	}
}
