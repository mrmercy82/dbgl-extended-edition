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
import org.eclipse.swt.widgets.ProgressBar;


public class ProgressBar_ {

	private ProgressBar_() {
	}

	public static Builder on(Composite composite) {
		return new Builder(composite);
	}

	public static final class Builder extends ControlBuilder<Builder> {
		private int sel_ = Integer.MIN_VALUE;
		private int max_ = 100;

		Builder(Composite composite) {
			super(composite, SWT.HORIZONTAL, SWT.FILL, SWT.CENTER, true, false);
		}

		public Builder indeterminate() {
			style_ |= SWT.INDETERMINATE;
			return this;
		}

		public Builder sel(int sel) {
			sel_ = sel;
			return this;
		}

		public Builder max(int max) {
			max_ = max;
			return this;
		}

		public ProgressBar build() {
			ProgressBar bar = new ProgressBar(composite_, style_);
			if (DarkTheme.forced()) {
				bar.setBackground(DarkTheme.inputBackground);
			}
			bar.setLayoutData(layoutData());
			if ((style_ & SWT.INDETERMINATE) != SWT.INDETERMINATE)
				bar.setMaximum(max_);
			if (sel_ != Integer.MIN_VALUE)
				bar.setSelection(sel_);
			return bar;
		}
	}
}
