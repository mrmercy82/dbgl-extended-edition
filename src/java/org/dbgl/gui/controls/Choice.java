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

import org.dbgl.gui.abstractdialog.BaseDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;


/**
 * A question with buttons of its own words, each as wide as its text needs (alpha 48: in a MessageBox of Windows the buttons
 * have one width, and "Manuell herunterladen …" did not fit in it).
 */
public final class Choice {

	private Choice() {
	}

	/**
	 * @param icon SWT.ICON_QUESTION, SWT.ICON_INFORMATION, ...
	 * @param buttons the texts of the buttons, the first one the default
	 * @return the index of the button chosen, -1 if the window was closed (Esc, its close box)
	 */
	public static int ask(Shell shell, String title, String message, int icon, String... buttons) {
		Display display = shell.getDisplay();
		Shell dlg = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		dlg.setText(title);
		BaseDialog.inheritWindowImage(dlg, shell);
		dlg.setLayout(new GridLayout(2, false));

		Label image = new Label(dlg, SWT.NONE);
		image.setImage(display.getSystemImage(icon));
		image.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		Label text = new Label(dlg, SWT.WRAP);
		text.setText(message);
		GridData textData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		textData.widthHint = Math.min(520, Math.max(300, text.computeSize(SWT.DEFAULT, SWT.DEFAULT).x));
		text.setLayoutData(textData);

		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
		row.setLayout(new GridLayout(buttons.length, false));
		int[] answer = {-1};
		for (int i = 0; i < buttons.length; i++) {
			Button b = new Button(row, SWT.PUSH);
			b.setText(buttons[i]);
			GridData d = new GridData(SWT.FILL, SWT.CENTER, false, false);
			d.widthHint = Math.max(90, b.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
			b.setLayoutData(d);
			int index = i;
			b.addListener(SWT.Selection, e -> {
				answer[0] = index;
				dlg.close();
			});
			if (i == 0)
				dlg.setDefaultButton(b);
		}

		dlg.pack();
		Rectangle area = shell.getMonitor().getClientArea(), parent = shell.getBounds();
		Point size = dlg.getSize();
		dlg.setLocation(Math.max(area.x, Math.min(parent.x + (parent.width - size.x) / 2, area.x + area.width - size.x)),
			Math.max(area.y, Math.min(parent.y + (parent.height - size.y) / 2, area.y + area.height - size.y)));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		return answer[0];
	}
}
