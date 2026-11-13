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
import org.dbgl.model.conf.Autoexec;
import org.dbgl.util.UnknownAutoexecLines;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * Lets the user choose, line by line, what happens to the lines of the [autoexec] of a profile that DBGL does not know: keep
 * (in which own section, with or without DBGL's marker lines) or remove. The result is null when cancelled.
 */
public class UnknownLinesDialog extends SizeControlledButtonDialog<List<UnknownAutoexecLines.Decision>> {

	private final String profileTitle_;
	private final List<Autoexec.UnknownLine> lines_;
	private java.util.function.Function<List<UnknownAutoexecLines.Decision>, String> preview_;

	public UnknownLinesDialog(Shell parent, String profileTitle, List<Autoexec.UnknownLine> lines) {
		super(parent, "unknownlinesdialog");
		profileTitle_ = profileTitle;
		lines_ = lines;
	}

	/**
	 * Alpha 48: at the right the [autoexec] as it will be with the choices made so far, changing with every box ticked
	 *
	 * @param preview the [autoexec] for the choices
	 */
	public UnknownLinesDialog withPreview(java.util.function.Function<List<UnknownAutoexecLines.Decision>, String> preview) {
		preview_ = preview;
		return this;
	}

	/**
	 * Below the "with the @REM comment lines" box: where its default is set, in the words of the menus of the language used
	 * (e.g. "Default: Menu > Settings > General > Lines in [autoexec] DBGL does not know").
	 */
	public static void markersWhere(org.eclipse.swt.widgets.Composite parent, int span) {
		org.dbgl.service.ITextService text = org.dbgl.service.TextService.getInstance();
		Label where = new Label(parent, SWT.WRAP);
		where.setText(text.get("dialog.unknownlines.markers.where3", new Object[] {text.get("dialog.main.menu.file").replace("&", ""),
				text.get("dialog.main.menu.adjustsettings").replace("&", "").replace("...", "").replace("…", "").trim(), text.get("dialog.settings.tab.general").replace("&", ""),
				text.get("dialog.settings.dosbox")})); // alpha 48: the options are in the group DOSBox now
		GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false, span, 1);
		data.horizontalIndent = 20;
		where.setLayoutData(data);
		where.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.unknownlines.title");
	}

	@Override
	protected void onShellCreated() {
		org.eclipse.swt.widgets.Composite left = contents_;
		if (preview_ != null) {
			contents_.setLayout(new GridLayout(2, false));
			left = new org.eclipse.swt.widgets.Composite(contents_, SWT.NONE);
			left.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		}
		left.setLayout(new GridLayout(3, false));
		if (preview_ != null)
			((GridLayout)left.getLayout()).marginWidth = ((GridLayout)left.getLayout()).marginHeight = 0;
		Label info = new Label(left, SWT.WRAP);
		info.setText(text_.get("dialog.unknownlines.info", new Object[] {profileTitle_}));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		infoData.widthHint = 400;
		info.setLayoutData(infoData);
		Button all = new Button(left, SWT.PUSH);
		all.setText(text_.get("dialog.switchdosbox.keepall"));
		Button none = new Button(left, SWT.PUSH);
		none.setText(text_.get("dialog.switchdosbox.keepnone"));

		Table table = new Table(left, SWT.CHECK | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		TableColumn lineColumn = new TableColumn(table, SWT.NONE);
		lineColumn.setText(text_.get("dialog.unknownlines.column.line"));
		TableColumn sectionColumn = new TableColumn(table, SWT.NONE);
		sectionColumn.setText(text_.get("dialog.unknownlines.column.section"));

		String[] sections = new String[Autoexec.SECTIONS];
		for (int i = 0; i < sections.length; i++)
			sections[i] = text_.get("dialog.template.customcommand" + (i + 1));
		List<CCombo> combos = new ArrayList<>();
		for (Autoexec.UnknownLine l: lines_) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(0, l.text());
			item.setChecked(true);
			CCombo combo = new CCombo(table, SWT.READ_ONLY | SWT.FLAT);
			if (l.afterExit()) {
				combo.setItems(new String[] {text_.get("dialog.unknownlines.afterexit")});
				combo.select(0);
				combo.setEnabled(false);
			} else {
				combo.setItems(sections);
				combo.select(l.section());
			}
			combo.pack();
			TableEditor editor = new TableEditor(table);
			editor.grabHorizontal = true;
			editor.setEditor(combo, item, 1);
			combos.add(combo);
		}
		lineColumn.pack();
		lineColumn.setWidth(Math.max(lineColumn.getWidth() + 20, 260));
		// wide enough for the longest section name plus the arrow of the list
		org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(table);
		int longest = 0;
		for (String s: sections)
			longest = Math.max(longest, gc.textExtent(s).x);
		longest = Math.max(longest, gc.textExtent(text_.get("dialog.unknownlines.afterexit")).x);
		gc.dispose();
		sectionColumn.setWidth(Math.max(longest + 70, combos.stream().mapToInt(c -> c.getSize().x).max().orElse(0) + 40));

		Button markers = new Button(left, SWT.CHECK);
		markers.setText(text_.get("dialog.unknownlines.markers"));
		markers.setSelection(org.dbgl.util.UnknownAutoexecLines.markersChosen()); // as last time
		markers.addListener(SWT.Selection, e -> org.dbgl.util.UnknownAutoexecLines.rememberMarkers(markers.getSelection()));
		markers.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		markersWhere(left, 3);

		all.addListener(SWT.Selection, e -> {
			for (TableItem item: table.getItems())
				item.setChecked(true);
		});
		none.addListener(SWT.Selection, e -> {
			for (TableItem item: table.getItems())
				item.setChecked(false);
		});

		java.util.function.Supplier<List<UnknownAutoexecLines.Decision>> decisions = () -> {
			List<UnknownAutoexecLines.Decision> result = new ArrayList<>();
			for (int i = 0; i < lines_.size(); i++) {
				Autoexec.UnknownLine l = lines_.get(i);
				int section = l.afterExit() ? l.section(): combos.get(i).getSelectionIndex();
				result.add(new UnknownAutoexecLines.Decision(l, table.getItem(i).getChecked(), section, markers.getSelection()));
			}
			return result;
		};

		if (preview_ != null) {
			org.eclipse.swt.widgets.Group group = new org.eclipse.swt.widgets.Group(contents_, SWT.NONE);
			group.setText(text_.get("dialog.unknownlines.preview"));
			group.setLayout(new GridLayout(1, false));
			GridData groupData = new GridData(SWT.FILL, SWT.FILL, true, true);
			groupData.widthHint = 380;
			group.setLayoutData(groupData);
			org.eclipse.swt.widgets.Text autoexec = new org.eclipse.swt.widgets.Text(group, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
			GridData textData = new GridData(SWT.FILL, SWT.FILL, true, true);
			textData.widthHint = 360;
			autoexec.setLayoutData(textData);
			org.eclipse.swt.graphics.Font mono = new org.eclipse.swt.graphics.Font(shell_.getDisplay(),
				org.dbgl.util.SystemUtils.IS_WINDOWS ? "Consolas": (org.dbgl.util.SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
			autoexec.setFont(mono);
			autoexec.addDisposeListener(e -> mono.dispose());
			Runnable update = () -> {
				if (autoexec.isDisposed())
					return;
				String text;
				try {
					text = preview_.apply(decisions.get());
				} catch (RuntimeException e) {
					text = e.toString();
				}
				int top = autoexec.getTopIndex();
				autoexec.setText(text.replace("\r\n", "\n").replace("\n", org.eclipse.swt.widgets.Text.DELIMITER));
				autoexec.setTopIndex(top);
			};
			table.addListener(SWT.Selection, e -> {
				if (e.detail == SWT.CHECK)
					update.run();
			});
			for (CCombo combo: combos)
				combo.addListener(SWT.Selection, e -> update.run());
			markers.addListener(SWT.Selection, e -> update.run());
			all.addListener(SWT.Selection, e -> update.run());
			none.addListener(SWT.Selection, e -> update.run());
			update.run();
		}

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				result_ = decisions.get();
				shell_.close();
			}
		});
		shell_.setDefaultButton(okButton_);

		// wide enough for both sides (with the preview), also when a smaller size was remembered from before; since alpha 48 also
		// high enough for OK and Cancel (they were cut off below)
		org.eclipse.swt.graphics.Rectangle bounds = shell_.getBounds(), area = shell_.getMonitor().getClientArea();
		int width = Math.min(Math.max(bounds.width, (preview_ != null) ? 1100: 0), area.width);
		int height = Math.min(Math.max(bounds.height, shell_.computeSize(width, SWT.DEFAULT).y), area.height);
		if ((width != bounds.width) || (height != bounds.height))
			shell_.setSize(width, height); // placed in the middle afterwards
	}
}
