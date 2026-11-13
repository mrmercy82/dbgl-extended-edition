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
package org.dbgl.gui.controls;

import java.io.File;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import org.apache.commons.io.FileUtils;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * Alpha 48: the saved states of a game (DOSBox-X keeps them per game in "savestates" in the folder of its screenshots): file, date
 * and size, to delete the ones not needed any more. Saving and loading happen in DOSBox-X itself (menu or keys).
 */
public final class SavestatesList {

	private SavestatesList() {
	}

	/** @return the composite spanning the columns of the parent, with GridData */
	public static Composite create(Composite parent, int columns, File dir) {
		ITextService text = TextService.getInstance();
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setBackground(parent.getBackground());
		GridData data = new GridData(SWT.FILL, SWT.BEGINNING, true, false, columns, 1);
		composite.setLayoutData(data);
		GridLayout layout = new GridLayout(3, false);
		layout.marginWidth = 0;
		composite.setLayout(layout);

		Label title = new Label(composite, SWT.NONE);
		title.setText(text.get("dialog.template.savestates"));
		title.setToolTipText(text.get("dialog.template.savestates.tooltip"));
		title.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false, 1, 2));

		Table table = new Table(composite, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION);
		table.setHeaderVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 2);
		tableData.heightHint = table.getItemHeight() * 4;
		tableData.widthHint = 380;
		table.setLayoutData(tableData);
		for (String key: new String[] {"dialog.template.savestates.file", "dialog.template.savestates.date", "dialog.template.savestates.size"}) {
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText(text.get(key));
		}

		Button delete = new Button(composite, SWT.PUSH);
		delete.setText(text.get("dialog.template.savestates.delete"));
		delete.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, false));
		Button open = new Button(composite, SWT.PUSH);
		open.setText(text.get("dialog.template.savestates.open"));
		open.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, false));

		Runnable fill = () -> {
			table.removeAll();
			File[] files = dir.listFiles(File::isFile);
			if (files != null) {
				Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
				DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
				for (File f: files) {
					TableItem item = new TableItem(table, SWT.NONE);
					item.setText(new String[] {f.getName(), format.format(new Date(f.lastModified())), FileUtils.byteCountToDisplaySize(f.length())});
					item.setData(f);
				}
			}
			if (table.getItemCount() == 0) {
				TableItem item = new TableItem(table, SWT.NONE);
				item.setText(text.get("dialog.template.savestates.none"));
			}
			for (TableColumn c: table.getColumns())
				c.pack();
			delete.setEnabled(false);
		};
		table.addListener(SWT.Selection, e -> delete.setEnabled(Arrays.stream(table.getSelection()).anyMatch(i -> i.getData() != null)));
		delete.addListener(SWT.Selection, e -> {
			File[] chosen = Arrays.stream(table.getSelection()).map(i -> (File)i.getData()).filter(f -> f != null).toArray(File[]::new);
			if (chosen.length == 0 || !Mess_.on(composite.getShell()).key("dialog.template.savestates.confirmdelete", new Object[] {chosen.length}).confirm())
				return;
			for (File f: chosen)
				FileUtils.deleteQuietly(f);
			fill.run();
		});
		open.addListener(SWT.Selection, e -> {
			dir.mkdirs();
			SystemUtils.openDirForViewing(dir, composite.getShell());
		});
		fill.run();
		return composite;
	}
}
