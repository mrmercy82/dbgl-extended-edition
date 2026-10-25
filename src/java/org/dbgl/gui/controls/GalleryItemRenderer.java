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
package org.dbgl.gui.controls;

import org.eclipse.nebula.widgets.gallery.AbstractGalleryItemRenderer;
import org.eclipse.nebula.widgets.gallery.GalleryItem;
import org.eclipse.nebula.widgets.gallery.RendererHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;


public class GalleryItemRenderer extends AbstractGalleryItemRenderer {

	private static final int LINE_WIDTH = 2;
	private static final int SELECTION_RADIUS = 8;

	private static final String ELLIPSIS = "...";

	private static final Color selectionForegroundColor_ = Display.getDefault().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
	private static final Color selectionBackgroundColor_ = Display.getDefault().getSystemColor(SWT.COLOR_LIST_SELECTION);

	private final boolean truncPositionEnd_;

	public GalleryItemRenderer(boolean truncPositionEnd) {
		truncPositionEnd_ = truncPositionEnd;
	}

	@Override
	public void draw(GC gc, GalleryItem item, int index, int x, int y, int width, int height) {
		// Set up the GC
		gc.setFont(getFont(item));

		// Create some room for the label
		int fontHeight = gc.getFontMetrics().getHeight();

		// Draw background (rounded rectangles)
		if (selected) {
			gc.setBackground(selectionBackgroundColor_);
			gc.setForeground(selectionBackgroundColor_);

			gc.setLineStyle(SWT.LINE_DOT);
			gc.setLineWidth(LINE_WIDTH);
			gc.drawRoundRectangle(x + (LINE_WIDTH / 2), y + (LINE_WIDTH / 2), width - LINE_WIDTH, height - LINE_WIDTH, SELECTION_RADIUS, SELECTION_RADIUS);
			gc.fillRoundRectangle(x + (LINE_WIDTH / 2), y + height - fontHeight - (LINE_WIDTH / 2) - 1, width - LINE_WIDTH, fontHeight + 1, SELECTION_RADIUS, SELECTION_RADIUS);

			gc.setForeground(selectionForegroundColor_);
		} else {
			gc.setForeground(item.getForeground());			
		}

		// Draw image
		Image drawImage = item.getImage();
		if (drawImage != null) {
			int xShift = RendererHelper.getShift(width - (LINE_WIDTH * 2), drawImage.getBounds().width);
			gc.drawImage(drawImage, x + LINE_WIDTH + xShift, y + LINE_WIDTH);
		}

		// Create label
		String text = truncPositionEnd_
			? createLabelTruncAtEnd(item.getText(), gc, width - SELECTION_RADIUS)
			: RendererHelper.createLabel(item.getText(), gc, width - SELECTION_RADIUS); // middle

		// Draw centered text
		gc.drawText(text, x + RendererHelper.getShift(width, gc.textExtent(text).x), y + height - fontHeight, true);
	}

	@Override
	public void dispose() {
		// nothing to dispose
	}

	private static String createLabelTruncAtEnd(String text, GC gc, int width) {
		if (text != null) {
			int extent = gc.textExtent(text).x;
			if (extent > width) {
				int w = gc.textExtent(ELLIPSIS).x;
				if (width > w) {
					int l = text.length();
					while (extent > width)
						extent = gc.textExtent(text.substring(0, --l)).x + w;
					return text.substring(0, l) + ELLIPSIS;
				}
			}
		}
		return text;
	}
}
