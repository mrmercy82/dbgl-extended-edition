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
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.CTabFolder_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.Table_;
import org.dbgl.gui.controls.Text_;
import org.dbgl.model.entity.SharedConf;
import org.dbgl.util.StringRelatedUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


public class LoadSharedConfDialog extends SizeControlledButtonDialog<LoadSharedConfDialog.SharedConfLoading> {

	public class SharedConfLoading {
		public final SharedConf conf_;
		public final boolean reloadDosboxDefaults_;

		public SharedConfLoading(SharedConf conf, boolean reloadDosboxDefaults) {
			conf_ = conf;
			reloadDosboxDefaults_ = reloadDosboxDefaults;
		}
	}

	private String title_;
	private List<SharedConf> confs_;

	public LoadSharedConfDialog(Shell parent, String title, List<SharedConf> confs) {
		super(parent, "sharedconfbrowser");
		title_ = title;
		confs_ = confs;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.loadsharedconf.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout());

		SashForm sashForm = createSashForm(contents_, 1);
		sashForm.setLayout(new FillLayout());

		Table table = Table_.on(sashForm).header().build();

		Listener sortListener = e -> {
			SharedConf selWebProfile = confs_.get(table.getSelectionIndex());
			TableColumn column = (TableColumn)e.widget;
			switch ((Integer)column.getData()) {
				case 0:
					Collections.sort(confs_, new SharedConf.byTitle());
					break;
				case 1:
					Collections.sort(confs_, new SharedConf.byYear());
					break;
				case 2:
					Collections.sort(confs_, new SharedConf.byVersion());
					break;
				default: // do nothing
			}
			table.removeAll();
			populate(table);
			table.setSortColumn(column);
			table.setSortDirection(SWT.UP);
			for (int i = 0; i < confs_.size(); i++) {
				if (selWebProfile == confs_.get(i)) {
					table.setSelection(i);
					break;
				}
			}
		};

		String[] titles = {text_.get("dialog.profile.title"), text_.get("dialog.profile.year"), text_.get("dialog.confsharing.gameversion")};
		for (int i = 0; i < titles.length; i++) {
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText(titles[i]);
			column.setData(i);
			column.addListener(SWT.Selection, sortListener);
			if (i == 0) {
				table.setSortColumn(column);
				table.setSortDirection(SWT.UP);
			}
			Collections.sort(confs_, new SharedConf.byTitle());
		}

		CTabFolder tabFolder = CTabFolder_.on(sashForm).ctrl();
		sashForm.setWeights(40, 60);

		Composite composite = Composite_.on(tabFolder).layout(new GridLayout(3, false)).tab("dialog.confsharing.tab.info").build();
		Text author = Chain.on(composite).lbl(l -> l.key("dialog.confsharing.author")).txt(t -> t.horSpan(2).nonEditable()).text();
		Text dosbox = Chain.on(composite).lbl(l -> l.key("dialog.loadsharedconf.dosboxversion")).txt(t -> t.horSpan(2).nonEditable()).text();
		Text incrConf = Chain.on(composite).lbl(l -> l.key("dialog.confsharing.explanation")).txt(t -> t.multi().readOnly()).text();
		Text explanation = Text_.on(composite).multi().readOnly().wrap().build();
		Text notes = Chain.on(composite).lbl(l -> l.key("dialog.confsharing.notes")).txt(t -> t.multi().wrap().readOnly().horSpan(2)).text();
		Button reloadDosboxDefaults = Button_.on(composite).horSpan(3).key("dialog.loadsharedconf.reloaddefaults").ctrl();

		table.addListener(SWT.Selection, e -> displaySharedConfData(table, author, dosbox, incrConf, explanation, notes));

		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent event) {
				result_ = new SharedConfLoading(confs_.get(table.getSelectionIndex()), reloadDosboxDefaults.getSelection());
				shell_.close();
			}
		});

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				result_ = new SharedConfLoading(confs_.get(table.getSelectionIndex()), reloadDosboxDefaults.getSelection());
				shell_.close();
			}
		});

		// pre-fill data
		populate(table);
		for (int i = 0; i < titles.length; i++)
			table.getColumn(i).pack();
		table.setSelection(getEntryBestMatchIndex(title_, confs_));
		table.showSelection();
		displaySharedConfData(table, author, dosbox, incrConf, explanation, notes);
	}

	private void populate(Table table) {
		for (SharedConf p: confs_) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(0, p.getGameTitle());
			item.setText(1, p.getGameYear());
			item.setText(2, p.getGameVersion());
		}
	}

	public static int getEntryBestMatchIndex(String search, List<SharedConf> confs) {
		String[] titles = new String[confs.size()];
		for (int i = 0; i < confs.size(); i++)
			titles[i] = confs.get(i).getGameTitle();
		return StringRelatedUtils.findBestMatchIndex(search, titles);
	}

	private void displaySharedConfData(Table table, Text author, Text dosbox, Text incrConf, Text explanation, Text notes) {
		int selection = table.getSelectionIndex();
		if (selection != -1) {
			SharedConf conf = confs_.get(selection);
			author.setText(conf.getAuthor());
			dosbox.setText(conf.getDosboxTitle() + " (" + conf.getDosboxVersion() + ")");
			incrConf.setText(conf.getIncrConf());
			explanation.setText(conf.getExplanation());
			notes.setText(conf.getNotes());
		}
	}
}
