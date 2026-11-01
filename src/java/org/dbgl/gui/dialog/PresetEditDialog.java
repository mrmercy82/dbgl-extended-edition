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

import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


/**
 * Edit an own preset: its name, and its settings as a small configuration ([section] and item=value lines). The result is
 * {name, text}, or null when cancelled.
 */
public class PresetEditDialog extends SizeControlledButtonDialog<String[]> {

	private final String name_;
	private final String content_;

	public PresetEditDialog(Shell parent, String name, String content) {
		super(parent, "preseteditdialog");
		name_ = name;
		content_ = content;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.presetedit.title", new Object[] {name_});
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(2, false));
		new Label(contents_, SWT.NONE).setText(text_.get("dialog.presetedit.name"));
		Text name = new Text(contents_, SWT.BORDER);
		name.setText(name_);
		name.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		Label help = new Label(contents_, SWT.WRAP);
		help.setText(text_.get("dialog.presetedit.help"));
		help.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1));
		Text content = new Text(contents_, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		content.setText(content_.replace("\r\n", "\n").replace("\n", Text.DELIMITER));
		content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		Font mono = new Font(content.getDisplay(), "Monospace", content.getFont().getFontData()[0].getHeight(), SWT.NORMAL);
		content.setFont(mono);
		content.addListener(SWT.Dispose, e -> mono.dispose());
		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (name.getText().isBlank())
					return;
				result_ = new String[] {name.getText().strip(), content.getText().replace(Text.DELIMITER, "\n")};
				shell_.close();
			}
		});
		content.setFocus();
	}
}
