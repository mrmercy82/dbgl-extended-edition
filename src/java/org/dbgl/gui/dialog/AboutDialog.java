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
		shell_.setSize(700, 500);

		GridLayout layout = new GridLayout(2, false);
		layout.marginTop = 2;
		layout.marginBottom = 5;
		shell_.setLayout(layout);

		Canvas canvas = new Canvas(shell_, SWT.NONE);
		canvas.setBackground(shell_.getBackground());
		canvas.setLayoutData(new GridData_(SWT.LEFT, SWT.CENTER, true, true).widthHint(256).heightHint(256).build());
		canvas.addPaintListener(e -> e.gc.drawImage(ImageService.getResourceImage(getParent().getDisplay(), "ico/256.png"), 0, 0));

		Group group = Group_.on(shell_).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, true)).layout(new GridLayout()).build();
		Chain.on(group).lbl(l -> l.style(SWT.WRAP).layoutData(new GridData_(360).build()).key("dialog.about.createdby", new Object[] {Constants.PROGRAM_NAME_FULL, Constants.PROGRAM_VERSION})).lbl(
			l -> l.style(SWT.WRAP).layoutData(new GridData_(360).build()).key("dialog.about.info",
				new Object[] {SystemUtils.JVM_ARCH, SystemUtils.JVM_VERSION, SystemUtils.OS_NAME, SystemUtils.OS_VERSION, SystemUtils.OS_ARCH, HsqlDatabaseProperties.PRODUCT_NAME,
						HsqlDatabaseProperties.THIS_FULL_VERSION, String.valueOf(SWT.getVersion()), SWT.getPlatform()})).lbl(
							l -> l.style(SWT.WRAP).layoutData(new GridData_(360).build()).key("dialog.about.thanks")).build();

		// this edition: the credit goes to the original author, the edition itself is mentioned in small print
		Chain.on(group).lbl(l -> l.style(SWT.WRAP).layoutData(new GridData_(360).build()).key("dialog.about.edition", new Object[] {Constants.EDITION_NAME})).build();
		Label editionBy = new Label(group, SWT.WRAP);
		editionBy.setLayoutData(new GridData_(360).build());
		editionBy.setText(text_.get("dialog.about.editionby", new Object[] {Constants.EDITION_NAME}));
		FontData[] fontData = editionBy.getFont().getFontData();
		for (FontData fd: fontData)
			fd.setHeight(Math.max(6, fd.getHeight() - 2));
		Font small = new Font(editionBy.getDisplay(), fontData);
		editionBy.setFont(small);
		editionBy.setForeground(editionBy.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
		editionBy.addListener(SWT.Dispose, e -> small.dispose());

		createLink(group, "<a href=\"https://dbgl.org\">" + text_.get("dialog.about.website") + "</a>", browseAdapter);

		shell_.setDefaultButton(Button_.on(group).text().key("button.ok").listen(closeShellAdapter).ctrl());
		shell_.getDefaultButton().setFocus();
	}
}
