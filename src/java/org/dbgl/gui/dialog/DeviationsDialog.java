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
import java.util.function.Function;
import org.dbgl.gui.abstractdialog.Deviations;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * "Differences from the defaults" (alpha 48): the settings of a profile or template that differ from the defaults of its DOSBox
 * version, as a list; ticked ones (or the ones chosen, with the menu) are set back to the defaults of the DOSBox version.
 */
public class DeviationsDialog extends SizeControlledButtonDialog<Boolean> {

	private final String heading_;
	private List<Deviations.Entry> entries_;
	/** sets the entries back to the defaults and gives the differences then (null: nothing changed) */
	private final Function<List<Deviations.Entry>, List<Deviations.Entry>> reset_;
	private Table table_;
	private Button resetButton_;

	/**
	 * @param heading what the differences are of (the profile, its DOSBox version)
	 * @param reset null: the list can only be looked at
	 */
	public DeviationsDialog(Shell parent, String heading, List<Deviations.Entry> entries, Function<List<Deviations.Entry>, List<Deviations.Entry>> reset) {
		super(parent, "deviationsdialog");
		heading_ = heading;
		entries_ = entries;
		reset_ = reset;
		result_ = false;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.differences.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(3, false));
		Label heading = new Label(contents_, SWT.WRAP);
		heading.setText(heading_);
		heading.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		table_ = new Table(contents_, (reset_ != null ? SWT.CHECK: SWT.NONE) | SWT.MULTI | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		table_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		table_.setHeaderVisible(true);
		table_.setLinesVisible(true);
		for (String key: new String[] {"dialog.differences.column.setting", "dialog.differences.column.value", "dialog.differences.column.default"})
			new TableColumn(table_, SWT.NONE).setText(text_.get(key));

		Menu menu = new Menu(table_);
		if (reset_ != null) {
			MenuItem reset = new MenuItem(menu, SWT.PUSH);
			reset.setText(text_.get("dialog.differences.resetchosen"));
			reset.addListener(SWT.Selection, e -> reset(chosen(false)));
			menu.addListener(SWT.Show, e -> reset.setEnabled(table_.getSelectionCount() > 0));
			new MenuItem(menu, SWT.SEPARATOR);
		}
		MenuItem copy = new MenuItem(menu, SWT.PUSH);
		copy.setText(text_.get("dialog.checkprofiles.copy"));
		copy.addListener(SWT.Selection, e -> copy());
		table_.setMenu(menu);

		if (reset_ != null) {
			Composite row = new Composite(contents_, SWT.NONE);
			row.setLayout(new GridLayout(3, false));
			row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
			resetButton_ = new Button(row, SWT.PUSH);
			resetButton_.setText(text_.get("dialog.differences.resetticked"));
			resetButton_.addListener(SWT.Selection, e -> reset(chosen(true)));
			Button all = new Button(row, SWT.PUSH);
			all.setText(text_.get("button.all"));
			all.addListener(SWT.Selection, e -> tickAll(true));
			wideButton(all);
			Button none = new Button(row, SWT.PUSH);
			none.setText(text_.get("button.none"));
			none.addListener(SWT.Selection, e -> tickAll(false));
			wideButton(none);
			table_.addListener(SWT.Selection, e -> {
				if (e.detail == SWT.CHECK)
					updateButton();
			});
		}
		createOkButton(closeShellAdapter);
		okCancelButtons_.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));
		fill();
	}

	private void fill() {
		table_.removeAll();
		for (Deviations.Entry e: entries_) {
			TableItem item = new TableItem(table_, SWT.NONE);
			item.setText(new String[] {'[' + e.section() + "] " + e.item(), e.value(), e.standard() == null ? text_.get("dialog.differences.notindosbox"): e.standard()});
			item.setData(e);
		}
		if (entries_.isEmpty()) {
			TableItem item = new TableItem(table_, SWT.NONE);
			item.setText(text_.get("dialog.differences.none"));
			item.setGrayed(true);
		}
		for (TableColumn col: table_.getColumns())
			col.pack();
		updateButton();
	}

	private void tickAll(boolean tick) {
		for (TableItem item: table_.getItems())
			if (item.getData() != null)
				item.setChecked(tick);
		updateButton();
	}

	private void updateButton() {
		if (resetButton_ != null)
			resetButton_.setEnabled(!chosen(true).isEmpty());
	}

	private List<Deviations.Entry> chosen(boolean ticked) {
		List<Deviations.Entry> result = new ArrayList<>();
		for (TableItem item: ticked ? table_.getItems(): table_.getSelection())
			if ((item.getData() instanceof Deviations.Entry e) && (!ticked || item.getChecked()))
				result.add(e);
		return result;
	}

	private void reset(List<Deviations.Entry> chosen) {
		if (chosen.isEmpty())
			return;
		List<Deviations.Entry> now = reset_.apply(chosen);
		if (now != null) {
			entries_ = now;
			result_ = true;
			fill();
		}
	}

	private void copy() {
		String text = Deviations.text(heading_, entries_, text_.get("dialog.template.moresettings.default"), text_.get("dialog.differences.none"), System.lineSeparator());
		Clipboard clipboard = new Clipboard(shell_.getDisplay());
		try {
			clipboard.setContents(new Object[] {text}, new Transfer[] {TextTransfer.getInstance()});
		} finally {
			clipboard.dispose();
		}
	}
}
