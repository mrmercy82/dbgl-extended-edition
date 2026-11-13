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
import org.eclipse.swt.widgets.Spinner;


public class Spinner_ {

	private Spinner_() {
	}

	public static Builder on(Composite composite) {
		return new Builder(composite);
	}

	public static class Builder extends ControlBuilder<Builder> {
		String tooltip_;
		int minimum_ = Integer.MIN_VALUE;
		int maximum_ = Integer.MAX_VALUE;
		Integer digits_ = null;
		Integer increment_ = null;
		Integer pageIncrement_ = null;
		Integer selection_ = null;

		Builder(Composite composite) {
			super(composite, SWT.BORDER, SWT.BEGINNING, SWT.CENTER, false, false);
		}

		public Builder tooltip(String key) {
			tooltip_ = TextService.getInstance().get(key);
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

		public Builder digits(int digits) {
			digits_ = digits;
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

		public Spinner build() {
			Spinner spinner = new Spinner(composite_, style_);
			if (DarkTheme.forced()) {
				spinner.setBackground(DarkTheme.fieldBackground); // alpha 48: fields lighter
				spinner.setForeground(DarkTheme.defaultForeground);
			}
			spinner.setLayoutData(layoutData());
			spinner.setMinimum(minimum_);
			spinner.setMaximum(maximum_);
			if (digits_ != null)
				spinner.setDigits(digits_);
			if (increment_ != null)
				spinner.setIncrement(increment_);
			if (pageIncrement_ != null)
				spinner.setPageIncrement(pageIncrement_);
			if (tooltip_ != null)
				spinner.setToolTipText(tooltip_);
			if (selection_ != null)
				spinner.setSelection(selection_);
			return spinner;
		}
	}
}
