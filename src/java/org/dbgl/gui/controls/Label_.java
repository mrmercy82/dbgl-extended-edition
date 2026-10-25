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
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;


public class Label_ {

	private Label_() {
	}

	public static Builder on(Composite composite) {
		return new Builder(composite);
	}

	public static class Builder extends ControlBuilder<Builder> {
		private String labelText_;
		private boolean large_;

		Builder(Composite composite) {
			super(composite, SWT.NONE, SWT.BEGINNING, SWT.CENTER, false, false);
		}
		
		public Builder grabHorSpace() {
			horizontalAlignment_ = SWT.FILL;
			grabExcessHorizontalSpace_ = true;
			return this;
		}
		
		public Builder large() {
			large_ = true;
			return this;
		}

		public Builder txt(String txt) {
			labelText_ = txt;
			return this;
		}

		public Builder key(String key) {
			return txt(TextService.getInstance().get(key));
		}

		public Builder key(String key, String param) {
			return txt(TextService.getInstance().get(key, param));
		}

		public Builder key(String key, Object[] objs) {
			return txt(TextService.getInstance().get(key, objs));
		}

		public Label build() {
			Label label = new Label(composite_, style_);
			if (DarkTheme.forced()) {
				label.setBackground(composite_.getBackground());
				label.setForeground(DarkTheme.defaultForeground);
			}
			label.setLayoutData(layoutData());
			if (labelText_ != null)
				label.setText(labelText_);
			if (large_) {
				FontData[] fontData = label.getFont().getFontData();
				int height = fontData[0].getHeight();
				fontData[0].setHeight((int)(height * 1.5));
				Font largeFont = new Font(composite_.getDisplay(), fontData[0]);
				label.setFont(largeFont);
				label.addDisposeListener(new DisposeListener() {
				    public void widgetDisposed(DisposeEvent e) {
				        largeFont.dispose();
				    }
				});
			}
			return label;
		}
	}
}
