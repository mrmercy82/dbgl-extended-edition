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

import java.awt.image.BufferedImage;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.service.ImageService;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.dbgl.util.ShaderPreview;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;


/**
 * alpha 52: what a shader roughly does to the picture, on the built-in test picture of {@link ShaderPreview}: the whole
 * picture and a part of it enlarged, with a tick to compare it with the picture without shader.
 */
public final class ShaderPreviewDialog {

	/** the size of the whole picture shown (4:3, like the picture of a DOS monitor) */
	static final int W = 480, H = 360;
	/** the enlarged part (in pixels of the test picture: the house with its window and door), three times as large as in the whole picture */
	static final int ZOOM = 3, PART_X = 34, PART_Y = 70, PART_W = 80, PART_H = 66;

	private ShaderPreviewDialog() {
	}

	/** the whole picture and the enlarged part with this kind of shader ("nearest": without shader) */
	static BufferedImage[] pictures(String kind) {
		BufferedImage whole = ShaderPreview.render(kind, W, H);
		int bw = W * ZOOM, bh = H * ZOOM;
		BufferedImage big = ShaderPreview.render(kind, bw, bh);
		int px = PART_X * bw / ShaderPreview.SRC_W, py = PART_Y * bh / ShaderPreview.SRC_H;
		int pw = PART_W * bw / ShaderPreview.SRC_W, ph = PART_H * bh / ShaderPreview.SRC_H;
		return new BufferedImage[] {whole, big.getSubimage(px, py, pw, ph)};
	}

	public static void open(Shell parent, String shader) {
		String kind = ShaderPreview.kindOf(shader);
		if (kind == null)
			return;
		ITextService text = TextService.getInstance();
		Display display = parent.getDisplay();
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		dlg.setText(text.get("dialog.template.glshader.preview.title", new Object[] {shader.trim()}));
		BaseDialog.inheritWindowImage(dlg, parent);
		dlg.setLayout(new GridLayout(2, false));
		if (DarkTheme.forced())
			dlg.setBackground(DarkTheme.dialogBackground);
		new Label(dlg, SWT.NONE).setText(text.get("dialog.template.glshader.preview.whole"));
		new Label(dlg, SWT.NONE).setText(text.get("dialog.template.glshader.preview.detail"));
		Label whole = new Label(dlg, SWT.NONE);
		Label part = new Label(dlg, SWT.NONE);
		part.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		Image[] shown = new Image[2];
		Image[][] cache = new Image[2][];
		Button compare = new Button(dlg, SWT.CHECK);
		compare.setText(text.get("dialog.template.glshader.preview.compare"));
		compare.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		Runnable show = () -> {
			int i = compare.getSelection() ? 1: 0;
			if (cache[i] == null) {
				BufferedImage[] pics = pictures(i == 1 ? "nearest": kind);
				cache[i] = new Image[] {new Image(display, ImageService.convertToSWT(pics[0])), new Image(display, ImageService.convertToSWT(pics[1]))};
			}
			shown[0] = cache[i][0];
			shown[1] = cache[i][1];
			whole.setImage(shown[0]);
			part.setImage(shown[1]);
		};
		compare.addListener(SWT.Selection, e -> show.run());
		show.run();
		Label note = new Label(dlg, SWT.WRAP);
		GridData noteData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		noteData.widthHint = W + shown[1].getBounds().width;
		note.setLayoutData(noteData);
		note.setText(text.get("dialog.template.glshader.preview.note", new Object[] {text.get("dialog.template.glshader.tryrun")}));
		Button close = new Button(dlg, SWT.PUSH);
		close.setText(text.get("button.close"));
		close.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
		close.addListener(SWT.Selection, e -> dlg.close());
		dlg.setDefaultButton(close);
		dlg.addDisposeListener(e -> {
			for (Image[] images: cache)
				if (images != null)
					for (Image img: images)
						img.dispose();
		});
		DarkTheme.apply(dlg);
		dlg.pack();
		Rectangle area = parent.getMonitor().getClientArea(), p = parent.getBounds();
		Point size = dlg.getSize();
		dlg.setLocation(Math.max(area.x, p.x + (p.width - size.x) / 2), Math.max(area.y, p.y + (p.height - size.y) / 2));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
	}
}
