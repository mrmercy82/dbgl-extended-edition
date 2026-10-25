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

import org.dbgl.service.ImageService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;


public class MenuItem_ {

	private MenuItem_() {
	}

	public static Builder on(Menu menu) {
		return new Builder(menu);
	}

	public static class Builder {
		private final Menu menu_;
		private int style_ = SWT.SEPARATOR;
		private int pos_ = -1;
		private String text_;
		private Image image_;
		private int accelerator_ = -1;
		private SelectionListener listener_;

		Builder(Menu menu) {
			menu_ = menu;
		}

		public Builder txt(String text) {
			text_ = text;
			style_ = SWT.CASCADE;
			return this;
		}

		public Builder key(String key) {
			return txt(TextService.getInstance().get(key));
		}

		public Builder image(String path) {
			int display = SettingsService.getInstance().getIntValue("gui", "buttondisplay");
			if (display != 1)
				image_ = ImageService.getResourceImage(menu_.getDisplay(), path);
			return this;
		}

		public Builder pos(int pos) {
			pos_ = pos;
			return this;
		}

		public Builder accel(int acc) {
			accelerator_ = acc;
			return this;
		}

		public Builder listen(SelectionListener listener) {
			listener_ = listener;
			style_ = SWT.NONE;
			return this;
		}

		public MenuItem build() {
			MenuItem menuItem = pos_ != -1 ? new MenuItem(menu_, style_, pos_): new MenuItem(menu_, style_);
			if (text_ != null)
				menuItem.setText(text_);
			if (image_ != null)
				menuItem.setImage(image_);
			if (accelerator_ != -1)
				menuItem.setAccelerator(accelerator_);
			if (listener_ != null)
				menuItem.addSelectionListener(listener_);
			return menuItem;
		}
	}
}
