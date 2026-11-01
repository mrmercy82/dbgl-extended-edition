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

import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


/**
 * Shows a text that was put together already, e.g. which settings of a profile differ from the defaults of its DOSBox version.
 */
public class DifferencesDialog extends SizeControlledButtonDialog<Object> {

	private final String title_;
	private final String content_;
	private Font font_;

	public DifferencesDialog(Shell parent, String title, String content) {
		super(parent, "differencesdialog");
		title_ = title;
		content_ = content;
	}

	@Override
	protected String getDialogTitle() {
		return title_;
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));
		Text text = new Text(contents_, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		font_ = new Font(shell_.getDisplay(), SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		text.setFont(font_);
		shell_.addDisposeListener(e -> font_.dispose());
		text.setText(content_);
		createOkButton(closeShellAdapter);
	}
}
