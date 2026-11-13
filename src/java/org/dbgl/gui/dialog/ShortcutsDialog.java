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
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.service.ImageService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;


/**
 * alpha 48: the keys of DBGL (help menu, F1) in two columns: the keys a little brighter (in the light mode darker) and bold, what
 * they do next to them, one heading per part. The text is "Heading\nKeys: what\n...\n\nHeading\n...".
 */
public class ShortcutsDialog extends SizeControlledButtonDialog<Object> {

	private final String title_;
	private final String content_;

	public ShortcutsDialog(Shell parent, String title, String content) {
		this(parent, title, content, ImageService.IMG_KEYBOARD);
	}

	/**
	 * also for other reports in this form (e.g. the USB stick): a line is "what\tvalue" or "keys: meaning"; a line without
	 * either is a sentence over both columns
	 */
	public ShortcutsDialog(Shell parent, String title, String content, String image) {
		super(parent, "shortcutsdialog");
		title_ = title;
		content_ = content;
		windowImage_ = image;
	}

	@Override
	protected String getDialogTitle() {
		return title_;
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));
		ScrolledComposite scroll = new ScrolledComposite(contents_, SWT.V_SCROLL | SWT.BORDER);
		scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scroll.setExpandHorizontal(true);
		scroll.setExpandVertical(true);
		Composite page = new Composite(scroll, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 12;
		layout.marginHeight = 8;
		layout.horizontalSpacing = 18;
		layout.verticalSpacing = 3;
		page.setLayout(layout);
		boolean dark = DarkTheme.forced();
		Color keyColor = dark ? new Color(255, 255, 255): new Color(0, 0, 0);
		Color textColor = dark ? new Color(200, 200, 200): new Color(70, 70, 70);
		Font base = page.getFont();
		FontData[] boldData = base.getFontData();
		FontData[] headData = base.getFontData();
		for (FontData d: boldData)
			d.setStyle(SWT.BOLD);
		for (FontData d: headData) {
			d.setStyle(SWT.BOLD);
			d.setHeight(d.getHeight() + 2);
		}
		Font bold = new Font(shell_.getDisplay(), boldData);
		Font head = new Font(shell_.getDisplay(), headData);
		shell_.addDisposeListener(e -> {
			bold.dispose();
			head.dispose();
		});
		boolean first = true;
		for (String part: content_.split("\n\\s*\n")) {
			String[] lines = part.split("\n");
			Label heading = new Label(page, SWT.NONE);
			heading.setText(lines[0].trim());
			heading.setFont(head);
			GridData hd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
			hd.verticalIndent = first ? 0: 12;
			heading.setLayoutData(hd);
			first = false;
			for (int i = 1; i < lines.length; i++) {
				String line = lines[i].trim();
				if (line.isEmpty())
					continue;
				int tab = line.indexOf('\t');
				int colon = tab >= 0 ? tab: line.lastIndexOf(": ");
				int skip = tab >= 0 ? 1: 2;
				if (colon < 0) { // a sentence
					Label sentence = new Label(page, SWT.WRAP);
					sentence.setText(line);
					sentence.setData(DarkTheme.KEEP_FOREGROUND, Boolean.TRUE);
					sentence.setForeground(textColor);
					GridData sd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
					sd.horizontalIndent = 8;
					sd.widthHint = 520;
					sentence.setLayoutData(sd);
					continue;
				}
				Label keys = new Label(page, SWT.NONE);
				keys.setText(line.substring(0, colon).trim());
				keys.setFont(bold);
				keys.setData(DarkTheme.KEEP_FOREGROUND, Boolean.TRUE);
				keys.setForeground(keyColor);
				GridData kd = new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false);
				kd.horizontalIndent = 8;
				keys.setLayoutData(kd);
				Label what = new Label(page, SWT.WRAP);
				what.setText(line.substring(colon + skip).trim());
				what.setData(DarkTheme.KEEP_FOREGROUND, Boolean.TRUE);
				what.setForeground(textColor);
				what.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
			}
		}
		scroll.setContent(page);
		scroll.addListener(SWT.Resize, e -> scroll.setMinSize(page.computeSize(scroll.getClientArea().width, SWT.DEFAULT)));
		createOkButton(closeShellAdapter);
		okCancelButtons_.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));
	}
}
