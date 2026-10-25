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

import org.dbgl.service.TextService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Layout;


public class Group_ {

	private Group_() {
	}

	public static Builder on(Composite composite) {
		return new Builder(composite);
	}

	public static class Builder extends ControlBuilder<Builder> {
		private String text_;
		private Layout layout_;

		Builder(Composite composite) {
			super(composite, SWT.NONE, SWT.BEGINNING, SWT.TOP, false, false);
		}

		public Builder layout(Layout layout) {
			layout_ = layout;
			return this;
		}

		public Builder txt(String text) {
			text_ = text;
			return this;
		}

		public Builder key(String key) {
			return txt(TextService.getInstance().get(key));
		}

		public Group build() {
			Group group = new Group(composite_, style_);
			if (DarkTheme.forced()) {
				group.setBackground(composite_.getBackground());
				group.setForeground(DarkTheme.defaultForeground);
			}
			group.setLayoutData(layoutData());
			group.setLayout(layout_);
			if (text_ != null)
				group.setText(text_);
			return group;
		}
	}
}
