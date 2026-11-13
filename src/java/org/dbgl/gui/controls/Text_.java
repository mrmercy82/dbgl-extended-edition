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
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;


public final class Text_ {

	private static Integer averageCharacterWidth_;
	private static Integer characterHeight_;

	private Text_() {
	}

	public static Builder on(Composite composite) {
		return new Builder(composite);
	}

	public static final class Builder extends ControlBuilder<Builder> {
		private String tooltip_;
		private String value_;
		private boolean editable_ = true;
		private boolean focus_;

		Builder(Composite composite) {
			super(composite, SWT.BORDER, SWT.FILL, SWT.CENTER, true, false);
		}

		public Builder tooltip(String tooltip) {
			tooltip_ = TextService.getInstance().get(tooltip);
			return this;
		}

		public Builder tooltip(String tooltip, String param) {
			tooltip_ = TextService.getInstance().get(tooltip, param);
			return this;
		}

		public Builder multi() {
			style_ |= SWT.MULTI;
			style_ |= SWT.H_SCROLL;
			style_ |= SWT.V_SCROLL;
			verticalAlignment_ = SWT.FILL;
			grabExcessVerticalSpace_ = true;
			return this;
		}

		public Builder readOnly() {
			style_ |= SWT.READ_ONLY;
			return this;
		}

		public Builder wrap() {
			style_ &= ~SWT.H_SCROLL;
			style_ |= SWT.WRAP;
			return this;
		}

		public Builder nonEditable() {
			editable_ = false;
			return this;
		}

		public Builder val(String value) {
			value_ = value;
			return this;
		}

		public Builder focus() {
			focus_ = true;
			return this;
		}

		public Text build() {
			Text text = new Text(composite_, style_);
			if (DarkTheme.forced()) {
				text.setBackground(((text.getStyle() & SWT.MULTI) != 0) ? DarkTheme.inputBackground: DarkTheme.fieldBackground); // alpha 48: fields lighter
				text.setForeground(DarkTheme.defaultForeground);
			}
			if (averageCharacterWidth_ == null) {
				GC gc = new GC(text);
				averageCharacterWidth_ = (int)gc.getFontMetrics().getAverageCharacterWidth();
				characterHeight_ = (int)gc.getFontMetrics().getHeight();
				gc.dispose();
			}
			widthHint(averageCharacterWidth_ * 8);
			
			if ((style_ & SWT.MULTI) != 0) {
				heightHint(characterHeight_ * 3);
			}
			
			text.setLayoutData(layoutData());

			if (focus_)
				text.setFocus();
			if (tooltip_ != null)
				text.setToolTipText(tooltip_);
			if (value_ != null)
				text.setText(value_);
			if (!editable_)
				text.setEditable(false);
			return text;
		}
	}
}
