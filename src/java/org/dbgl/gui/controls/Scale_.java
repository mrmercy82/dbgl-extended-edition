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

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Scale;


public class Scale_ {

	private Scale_() {
	}

	public static Builder on(Composite composite) {
		return new Builder(composite);
	}

	public static class Builder extends ControlBuilder<Builder> {
		String tooltip_;
		Integer minimum_ = null;
		Integer maximum_ = null;
		Integer increment_ = null;
		Integer pageIncrement_ = null;
		Integer selection_ = null;
		boolean enabled_ = true;

		Builder(Composite composite) {
			super(composite, SWT.HORIZONTAL, SWT.FILL, SWT.CENTER, true, false);
		}

		public Builder tooltip(String tooltip) {
			tooltip_ = tooltip;
			return this;
		}

		public Builder vertical() {
			style_ = SWT.VERTICAL;
			horizontalAlignment_ = SWT.CENTER;
			verticalAlignment_ = SWT.FILL;
			grabExcessHorizontalSpace_ = false;
			grabExcessVerticalSpace_ = true;
			return this;
		}

		public Builder min(int min) {
			minimum_ = min;
			return this;
		}

		public Builder max(int max) {
			maximum_ = max;
			return this;
		}

		public Builder incr(int incr) {
			increment_ = incr;
			return this;
		}

		public Builder pageIncr(int pageIncr) {
			pageIncrement_ = pageIncr;
			return this;
		}

		public Builder select(int select) {
			selection_ = select;
			return this;
		}
		
		public Builder enable(boolean enable) {
			enabled_ = enable;
			return this;
		}

		public Scale build() {
			Scale scale = new Scale(composite_, style_);
			scale.setEnabled(enabled_);
			if (DarkTheme.forced()) {
				scale.setBackground(composite_.getBackground());
				scale.setForeground(DarkTheme.defaultForeground);
			}
			scale.setLayoutData(layoutData());

			if (minimum_ != null)
				scale.setMinimum(minimum_);
			if (maximum_ != null)
				scale.setMaximum(maximum_);
			if (increment_ != null)
				scale.setIncrement(increment_);
			if (pageIncrement_ != null)
				scale.setPageIncrement(pageIncrement_);
			if (tooltip_ != null)
				scale.setToolTipText(tooltip_);
			if (selection_ != null)
				scale.setSelection(selection_);
			return scale;
		}
	}
}
