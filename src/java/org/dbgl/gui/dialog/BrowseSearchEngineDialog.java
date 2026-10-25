/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
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

import java.util.Collections;
import java.util.List;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Table_;
import org.dbgl.model.WebProfile;
import org.dbgl.util.searchengine.WebSearchEngine;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


public class BrowseSearchEngineDialog extends SizeControlledButtonDialog<WebProfile> {

	private final String title_;
	private final List<WebProfile> webProfiles_;
	private final WebSearchEngine engine_;

	public BrowseSearchEngineDialog(Shell parent, String title, List<WebProfile> profs, WebSearchEngine engine) {
		super(parent, "mobygamesbrowser");
		title_ = title;
		webProfiles_ = profs;
		engine_ = engine;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.searchenginebrowser.title", new String[] {engine_.getName()});
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new FillLayout());

		Table table = Table_.on(contents_).header().build();

		String[] titles = {text_.get("dialog.profile.title"), text_.get("dialog.profile.year"), text_.get("dialog.searchenginebrowser.column.platform")};

		for (int i = 0; i < titles.length; i++) {
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText(titles[i]);
			column.setData(i);
			column.addListener(SWT.Selection, e -> {
				int selIndex = table.getSelectionIndex();
				WebProfile selWebProfile = webProfiles_.get(selIndex);
				TableColumn col = (TableColumn)e.widget;
				int index = (Integer)col.getData();
				switch (index) {
					case 0:
						Collections.sort(webProfiles_, new WebProfile.byTitle());
						break;
					case 1:
						Collections.sort(webProfiles_, new WebProfile.byYear());
						break;
					case 2:
						Collections.sort(webProfiles_, new WebProfile.byPlatform());
						break;
					default: // do nothing
				}
				table.removeAll();
				populate(table);
				table.setSortColumn(col);
				table.setSortDirection(SWT.UP);
				for (int j = 0; j < webProfiles_.size(); j++) {
					if (selWebProfile == webProfiles_.get(j)) {
						table.setSelection(j);
						break;
					}
				}
			});
			if (i == (titles.length - 1)) {
				table.setSortColumn(column);
				table.setSortDirection(SWT.UP);
			}
		}

		populate(table);
		for (int i = 0; i < titles.length; i++) {
			table.getColumn(i).pack();
		}
		table.setSelection(WebSearchEngine.getEntryBestMatchIndex(title_, webProfiles_));
		table.showSelection();
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent event) {
				doChooseGame(table);
			}
		});

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doChooseGame(table);
			}
		});
	}

	private void populate(Table table) {
		for (WebProfile p: webProfiles_) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(0, p.getTitle());
			item.setText(1, p.getYear());
			item.setText(2, p.getPlatform());
		}
	}

	private void doChooseGame(Table table) {
		result_ = webProfiles_.get(table.getSelectionIndex());
		shell_.close();
	}
}
