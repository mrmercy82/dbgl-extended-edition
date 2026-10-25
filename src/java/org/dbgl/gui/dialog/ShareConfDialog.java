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

import java.util.stream.Stream;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledTabbedDialog;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Label_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.Text_;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.entity.SharedConf;
import org.dbgl.util.StringRelatedUtils;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;


public class ShareConfDialog extends SizeControlledTabbedDialog<Object> {

	private SharedConf sharedConf_;

	public ShareConfDialog(Shell parent, String gameTitle, String gameYear, Profile profile) {
		super(parent, "shareconfdialog");

		sharedConf_ = new SharedConf(StringUtils.EMPTY, StringUtils.EMPTY, gameTitle, StringUtils.EMPTY, gameYear, profile.getConfigurationForSharing().toString(null),
				profile.getCombinedConfiguration().toString(null), StringUtils.EMPTY, profile.getDosboxVersion().getTitle(), profile.getDosboxVersion().getVersion());
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.confsharing.title");
	}

	@Override
	protected void onShellCreated() {
		Composite composite = createTabWithComposite("dialog.confsharing.tab.info", 3);

		Text author = Chain.on(composite).lbl(l -> l.key("dialog.confsharing.author")).txt(t -> t.horSpan(2)).text();
		Text gameTitle = Chain.on(composite).lbl(l -> l.key("dialog.confsharing.gametitle")).txt(t -> t.horSpan(2)).text();
		Text gameVersion = Chain.on(composite).lbl(l -> l.key("dialog.confsharing.gameversion")).txt(t -> t.horSpan(2)).text();
		Text gameYear = Chain.on(composite).lbl(l -> l.key("dialog.confsharing.gameyear")).txt(t -> t.horSpan(2)).text();
		Label_.on(composite).key("dialog.confsharing.explanation").build();
		Tree incrConf = createTree(composite, SWT.BORDER | SWT.CHECK);
		incrConf.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
		Text explanation = Text_.on(composite).multi().wrap().build();
		Text notes = Chain.on(composite).lbl(l -> l.key("dialog.confsharing.notes")).txt(t -> t.multi().wrap().horSpan(2)).text();

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (!isValid(author, gameTitle, gameYear, explanation)) {
					return;
				}
				try {
					sharedConf_.setAuthor(author.getText());
					sharedConf_.setGameTitle(gameTitle.getText());
					sharedConf_.setGameVersion(gameVersion.getText());
					sharedConf_.setGameYear(gameYear.getText());
					sharedConf_.setIncrConf(extractConfFromTree(incrConf));
					sharedConf_.setExplanation(explanation.getText());
					sharedConf_.setNotes(notes.getText());

					Client client = ClientBuilder.newClient();
					SharedConf result = client.target(settings_.getValue("confsharing", "endpoint")).path("/submissions").request().post(Entity.entity(sharedConf_, MediaType.APPLICATION_XML),
						SharedConf.class);
					Mess_.on(shell_).key("dialog.confsharing.confirmation", result.getGameTitle()).display();
					client.close();
				} catch (Exception e) {
					Mess_.on(shell_).key("dialog.confsharing.error.submit", StringRelatedUtils.toString(e)).exception(e).fatal();
				}
				settings_.setValue("confsharing", "author", author.getText());
				shell_.close();
			}
		});

		// init values
		author.setText(StringUtils.defaultString(settings_.getValue("confsharing", "author")));
		gameTitle.setText(sharedConf_.getGameTitle());
		gameYear.setText(sharedConf_.getGameYear());
		fillStringIntoTree(sharedConf_.getIncrConf(), incrConf);

		if (StringUtils.isBlank(sharedConf_.getIncrConf()))
			explanation.setText("N/A");

		if (StringUtils.isBlank(gameTitle.getText()))
			gameTitle.setFocus();
		else if (StringUtils.isBlank(gameYear.getText()))
			gameYear.setFocus();
		else if (StringUtils.isBlank(explanation.getText()))
			explanation.setFocus();
		else
			notes.setFocus();
	}

	private boolean isValid(Text author, Text gameTitle, Text gameYear, Text explanation) {
		Mess_.Builder mess = Mess_.on(shell_);
		if (StringUtils.isBlank(author.getText()))
			mess.key("dialog.confsharing.required.author").bind(author);
		if (StringUtils.isBlank(gameTitle.getText()))
			mess.key("dialog.confsharing.required.gametitle").bind(gameTitle);
		if (StringUtils.isBlank(gameYear.getText()))
			mess.key("dialog.confsharing.required.gameyear").bind(gameYear);
		if (StringUtils.isBlank(explanation.getText()))
			mess.key("dialog.confsharing.required.explanation").bind(explanation);
		return mess.valid();
	}

	private static void fillStringIntoTree(String conf, Tree tree) {
		String[] lines = StringUtils.split(conf, SystemUtils.EOLN);

		TreeItem sectionItem = null;
		for (String s: lines) {
			if (s.startsWith("[")) {
				sectionItem = new TreeItem(tree, SWT.NONE);
				sectionItem.setText(s);
				sectionItem.setChecked(true);
			} else {
				TreeItem node = new TreeItem(sectionItem, SWT.NONE);
				node.setText(s);
				node.setChecked(true);
			}
		}

		for (TreeItem item: tree.getItems())
			item.setExpanded(true);

		tree.addListener(SWT.Selection, event -> {
			if (event.detail == SWT.CHECK) {
				TreeItem tItem = (TreeItem)event.item;
				if (tItem.getParentItem() == null) {
					tItem.setGrayed(false);
					for (TreeItem item: tItem.getItems())
						item.setChecked(tItem.getChecked());
				} else {
					TreeItem parent = tItem.getParentItem();
					long checkedCount = Stream.of(parent.getItems()).filter(TreeItem::getChecked).count();
					parent.setChecked(checkedCount > 0);
					parent.setGrayed(checkedCount > 0 && checkedCount < parent.getItemCount());
				}
			}
		});
	}

	private static String extractConfFromTree(Tree tree) {
		Configuration conf = new Configuration();
		for (TreeItem sectionItem: tree.getItems()) {
			if (sectionItem.getChecked()) {
				for (TreeItem node: sectionItem.getItems()) {
					if (node.getChecked()) {
						String[] v = StringUtils.split(node.getText(), '=');
						conf.setValue(StringUtils.substring(sectionItem.getText(), 1, -1), v[0], v[1]);
					}
				}
			}
		}
		return conf.toString(null);
	}
}
