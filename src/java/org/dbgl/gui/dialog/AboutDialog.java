/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-01. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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

import org.dbgl.constants.Constants;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.GridData_;
import org.dbgl.gui.controls.Group_;
import org.dbgl.service.ImageService;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.hsqldb.persist.HsqlDatabaseProperties;


public class AboutDialog extends BaseDialog<Object> {

	public AboutDialog(Shell parent) {
		super(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.about.title");
	}

	@Override
	protected void onShellCreated() {
		shell_.setSize(720, 560);

		GridLayout layout = new GridLayout(2, false);
		layout.marginTop = 2;
		layout.marginBottom = 5;
		shell_.setLayout(layout);

		Canvas canvas = new Canvas(shell_, SWT.NONE);
		canvas.setBackground(shell_.getBackground());
		canvas.setLayoutData(new GridData_(SWT.LEFT, SWT.CENTER, true, true).widthHint(256).heightHint(256).build());
		canvas.addPaintListener(e -> e.gc.drawImage(ImageService.getResourceImage(getParent().getDisplay(), "ico/256.png"), 0, 0));

		Group group = Group_.on(shell_).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, true)).layout(new GridLayout()).build();
		// the original program and its author: DBGL up to 0.100 beta 17, introduced by a small "based on"
		Label basedOn = new Label(group, SWT.NONE);
		basedOn.setText(text_.get("dialog.about.basedon"));
		smallGray(basedOn);
		Chain.on(group).lbl(l -> l.style(SWT.WRAP).layoutData(new GridData_(380).build()).key("dialog.about.createdby2", new Object[] {Constants.PROGRAM_NAME_FULL, Constants.BASE_VERSION}))
				.lbl(l -> l.style(SWT.WRAP).layoutData(new GridData_(380).build()).key("dialog.about.thanks")).build();
		createLink(group, "<a href=\"" + Constants.ORIGINAL_URL + "\">" + text_.get("dialog.about.website.original") + "</a>", browseAdapter);

		// this edition, clearly apart: an independent continuation the original author is not involved in
		Label separator = new Label(group, SWT.SEPARATOR | SWT.HORIZONTAL);
		separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Chain.on(group).lbl(l -> l.style(SWT.WRAP).layoutData(new GridData_(380).build()).key("dialog.about.editioninfo2",
			new Object[] {"DBGL " + Constants.EDITION_NAME, org.dbgl.util.EditionUpdateChecker.readable(Constants.PROGRAM_VERSION), Constants.BASE_VERSION.replace(" ", "\u00A0")})).build(); // the version not broken over two lines
		Label editionBy = new Label(group, SWT.WRAP);
		editionBy.setLayoutData(new GridData_(380).build());
		editionBy.setText(text_.get("dialog.about.editionby2", new Object[] {"DBGL " + Constants.EDITION_NAME}));
		smallGray(editionBy);
		createLink(group, "<a href=\"" + Constants.EDITION_URL + "\">" + text_.get("dialog.about.website.edition", new Object[] {Constants.EDITION_NAME}) + "</a>", browseAdapter);

		Label separator2 = new Label(group, SWT.SEPARATOR | SWT.HORIZONTAL);
		separator2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Chain.on(group).lbl(l -> l.style(SWT.WRAP).layoutData(new GridData_(380).build()).key("dialog.about.info",
			new Object[] {SystemUtils.JVM_ARCH, SystemUtils.JVM_VERSION, SystemUtils.OS_NAME, SystemUtils.OS_VERSION, SystemUtils.OS_ARCH, HsqlDatabaseProperties.PRODUCT_NAME,
					HsqlDatabaseProperties.THIS_FULL_VERSION, String.valueOf(SWT.getVersion()), SWT.getPlatform()})).build();
		// the version of the DBGL database (e.g. v0.103), right above OK
		Label dbVersion = new Label(group, SWT.NONE);
		dbVersion.setText(text_.get("dialog.about.dbversion", new Object[] {org.dbgl.service.DatabaseService.getInstance().getVersionString()}));


		shell_.setDefaultButton(Button_.on(group).text().key("button.ok").listen(closeShellAdapter).ctrl());
		shell_.getDefaultButton().setFocus();
		// only as high as the content needs (it had much empty space above and below)
		shell_.setSize(720, Math.min(620, shell_.computeSize(720, SWT.DEFAULT).y + 10));
	}

	/** smaller and gray, for the lines that only introduce or credit */
	private static void smallGray(Label label) {
		FontData[] fontData = label.getFont().getFontData();
		for (FontData fd: fontData)
			fd.setHeight(Math.max(6, fd.getHeight() - 2));
		Font small = new Font(label.getDisplay(), fontData);
		label.setFont(small);
		label.setForeground(label.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
		label.addListener(SWT.Dispose, e -> small.dispose());
	}
}
