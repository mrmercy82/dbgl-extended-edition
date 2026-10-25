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
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Layout;


public class Composite_ {

	private Composite_() {
	}

	public static Builder on(Composite composite) {
		return new Builder(composite);
	}

	public static class Builder extends ControlBuilder<Builder> {
		private Layout layout_;
		private String text_;

		public Builder(Composite composite) {
			super(composite, SWT.NONE, SWT.BEGINNING, SWT.TOP, false, false);
		}
		
		public Builder grabHorSpace() {
			horizontalAlignment_ = SWT.FILL;
			grabExcessHorizontalSpace_ = true;
			return this;
		}
		
		public Builder grabVerSpace() {
			verticalAlignment_ = SWT.FILL;
			grabExcessVerticalSpace_ = true;
			return this;
		}
		
		public Builder grabAllSpace() {
			grabHorSpace();
			grabVerSpace();
			return this;
		}

		public Builder layout(Layout layout) {
			layout_ = layout;
			return this;
		}
		
		public Builder pageLayout(int numColumns) {
			GridLayout layout = new GridLayout(numColumns, false);
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			layout_ = layout;
			return this;
		}

		public Builder innerLayout(int numColumns) {
			GridLayout layout = new GridLayout(numColumns, false);
			layout.horizontalSpacing = numColumns == 1 ? 0: 4;
			layout.verticalSpacing = numColumns == 1 ? 0: 4;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			layout_ = layout;
			return this;
		}

		public Builder vertSpacing() {
			((GridLayout)layout_).verticalSpacing = 4;
			return this;
		}

		public Builder tab(String key) {
			text_ = TextService.getInstance().get(key);
			return this;
		}

		public Composite build() {
			Composite composite = new Composite(composite_, style_);
			if (DarkTheme.forced()) {
				composite.setBackground(composite_.getBackground());
			}
			composite.setLayoutData(layoutData());
			composite.setLayout(layout_);
			if (text_ != null) {
				CTabItem tabItem = new CTabItem((CTabFolder)composite_, style_);
				tabItem.setText("   " + text_ + "   ");
				tabItem.setControl(composite);
				((CTabFolder)composite_).setSelection(0);
			}
			return composite;
		}
	}
}
