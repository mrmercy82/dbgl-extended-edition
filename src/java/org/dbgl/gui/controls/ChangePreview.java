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

import java.util.ArrayList;
import java.util.List;

import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;


/**
 * alpha 49: a preview before something is changed, in the colors of the other previews: lines that go ("- ") red, new
 * ones ("+ ") green, kept ones ("= ") and sections gray, what is only inherited ("> ") blue-green, a warning ("! ")
 * orange. "Apply" or back; the preview can be saved as the other reports.
 */
public final class ChangePreview {

	private ChangePreview() {
	}

	/** the system color of a line by how it begins, 0 for none */
	public static int colorOf(String line) {
		String t = line.trim();
		return t.startsWith("- ") ? SWT.COLOR_DARK_RED: t.startsWith("+ ") ? SWT.COLOR_DARK_GREEN: t.startsWith("! ") ? SWT.COLOR_DARK_YELLOW: t.startsWith("> ") ? SWT.COLOR_DARK_CYAN
				: t.startsWith("= ") || (t.startsWith("[") && t.endsWith("]")) ? SWT.COLOR_DARK_GRAY: 0;
	}

	/**
	 * @param info  what is about to happen, above the lines
	 * @param lines the changes
	 * @param base  the beginning of the name of a saved report, e.g. "dbgl-switch-preview"
	 * @return true to go on, false to leave everything as it is
	 */
	public static boolean confirm(Shell parent, String info, List<String> lines, String base) {
		ITextService text = TextService.getInstance();
		Display display = parent.getDisplay();
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		dlg.setText(text.get("dialog.checkprofiles.previewtitle"));
		BaseDialog.inheritWindowImage(dlg, parent);
		dlg.setLayout(new GridLayout(3, false));
		DarkTheme.window(dlg);
		Label infoLabel = new Label(dlg, SWT.WRAP);
		infoLabel.setText(info.replace("&", "&&"));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		infoData.widthHint = 820;
		infoLabel.setLayoutData(infoData);
		StyledText area = new StyledText(dlg, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		GridData areaData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		areaData.widthHint = 820;
		areaData.heightHint = 380;
		area.setLayoutData(areaData);
		Font font = new Font(display, org.dbgl.util.SystemUtils.IS_WINDOWS ? "Consolas": (org.dbgl.util.SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		area.setFont(font);
		if (DarkTheme.forced()) {
			area.setBackground(DarkTheme.inputBackground);
			area.setForeground(DarkTheme.defaultForeground);
		}
		dlg.addDisposeListener(e -> font.dispose());
		StringBuilder sb = new StringBuilder();
		List<StyleRange> styles = new ArrayList<>();
		for (String line: lines) {
			int color = colorOf(line);
			if (color != 0) {
				Color c = DarkTheme.textColor(display, color);
				styles.add(new StyleRange(sb.length(), line.length(), c, null));
			}
			sb.append(line).append('\n');
		}
		area.setText(sb.toString());
		area.setStyleRanges(styles.toArray(new StyleRange[0]));

		Button save = new Button(dlg, SWT.PUSH);
		save.setText(text.get("dialog.switchdosbox.savereport"));
		GridData saveData = new GridData(SWT.BEGINNING, SWT.CENTER, true, false);
		saveData.widthHint = Math.max(110, save.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
		save.setLayoutData(saveData);
		save.addListener(SWT.Selection, e -> ReportSave.menu(dlg, base, info, () -> info + System.lineSeparator() + System.lineSeparator() + area.getText(), () -> ReportSave.html(area, info)));
		Button back = new Button(dlg, SWT.PUSH), take = new Button(dlg, SWT.PUSH);
		back.setText(text.get("button.back"));
		take.setText(text.get("dialog.conftakeover.apply"));
		for (Button b: new Button[] {back, take}) {
			GridData data = new GridData(SWT.END, SWT.CENTER, false, false);
			data.widthHint = Math.max(110, b.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
			b.setLayoutData(data);
		}
		boolean[] ok = new boolean[1];
		take.addListener(SWT.Selection, e -> {
			ok[0] = true;
			dlg.close();
		});
		back.addListener(SWT.Selection, e -> dlg.close());
		dlg.setDefaultButton(take);
		DarkTheme.apply(dlg);
		dlg.pack();
		Rectangle screen = parent.getMonitor().getClientArea(), around = parent.getBounds();
		Point size = dlg.getSize();
		dlg.setLocation(Math.max(screen.x, Math.min(around.x + (around.width - size.x) / 2, screen.x + screen.width - size.x)),
			Math.max(screen.y, Math.min(around.y + (around.height - size.y) / 2, screen.y + screen.height - size.y)));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		return ok[0];
	}
}
