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
package org.dbgl.gui.listeners;

import org.eclipse.nebula.widgets.gallery.Gallery;
import org.eclipse.nebula.widgets.gallery.GalleryItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;


public class ToolTipListener implements Listener {

	private static final Color infoForegroundColor_ = Display.getDefault().getSystemColor(SWT.COLOR_INFO_FOREGROUND);
	private static final Color infoBackgroundColor_ = Display.getDefault().getSystemColor(SWT.COLOR_INFO_BACKGROUND);

	private Shell tip_ = null;

	@Override
	public void handleEvent(Event event) {
		if (event.type == SWT.MouseHover) {
			Gallery gallery = (Gallery)event.widget;
			GalleryItem galleryItem = gallery.getItem(new Point(event.x, event.y));
			if (galleryItem != null) {
				if (tip_ != null && !tip_.isDisposed())
					tip_.dispose();
				tip_ = new Shell(gallery.getShell(), SWT.ON_TOP | SWT.NO_FOCUS | SWT.TOOL);
				tip_.setBackground(infoBackgroundColor_);
				FillLayout layout = new FillLayout();
				layout.marginWidth = 2;
				tip_.setLayout(layout);
				Label label = new Label(tip_, SWT.NONE);
				label.setForeground(infoForegroundColor_);
				label.setBackground(infoBackgroundColor_);
				label.setText(galleryItem.getText(1));
				label.addListener(SWT.MouseExit, this);
				Point size = tip_.computeSize(SWT.DEFAULT, SWT.DEFAULT);
				Point pt = gallery.toDisplay(event.x, event.y + 16);
				tip_.setBounds(pt.x, pt.y, size.x, size.y);
				tip_.setVisible(true);
			}
		} else {
			if (tip_ != null && !tip_.isDisposed())
				tip_.dispose();
			tip_ = null;
		}
	}
}
