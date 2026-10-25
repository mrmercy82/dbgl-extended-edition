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
import org.eclipse.swt.widgets.Table;


public class Table_ {

	private Table_() {
	}

	public static Builder on(Composite composite) {
		return new Builder(composite);
	}

	public static final class Builder extends ControlBuilder<Builder> {
		private boolean header_;

		Builder(Composite composite) {
			super(composite, SWT.BORDER | SWT.FULL_SELECTION, SWT.FILL, SWT.CENTER, true, false);
		}

		public Builder header() {
			header_ = true;
			return this;
		}

		public Builder multi() {
			style_ |= SWT.MULTI;
			return this;
		}

		public Builder check() {
			style_ |= SWT.CHECK;
			return this;
		}

		public Builder scroll() {
			style_ |= SWT.H_SCROLL;
			style_ |= SWT.V_SCROLL;
			verticalAlignment_ = SWT.FILL;
			grabExcessVerticalSpace_ = true;
			return this;
		}

		public Table build() {
			Table table = new Table(composite_, style_);
			table.setHeaderVisible(header_);
			if (DarkTheme.forced()) {
				table.setBackground(DarkTheme.inputBackground);
				table.setForeground(DarkTheme.defaultForeground);
				table.setHeaderBackground(DarkTheme.tableHeaderBackground);
				table.setHeaderForeground(DarkTheme.tableHeaderForeground);
			}
			table.setLayoutData(layoutData());
			table.setLinesVisible(!DarkTheme.forced());
			return table;
		}
	}
}
