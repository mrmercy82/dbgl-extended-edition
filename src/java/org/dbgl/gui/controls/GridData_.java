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

import org.eclipse.swt.layout.GridData;


public class GridData_ {

	private final GridData gridData_;

	public GridData_() {
		gridData_ = new GridData();
	}

	public GridData_(int widthHint) {
		this();
		widthHint(widthHint);
	}

	public GridData_(int horizontalAlignment, int verticalAlignment, boolean grabExcessHorizontalSpace, boolean grabExcessVerticalSpace) {
		gridData_ = new GridData(horizontalAlignment, verticalAlignment, grabExcessHorizontalSpace, grabExcessVerticalSpace);
	}

	public GridData_(int horizontalAlignment, int verticalAlignment, boolean grabExcessHorizontalSpace, boolean grabExcessVerticalSpace, int horizontalSpan, int verticalSpan) {
		gridData_ = new GridData(horizontalAlignment, verticalAlignment, grabExcessHorizontalSpace, grabExcessVerticalSpace, horizontalSpan, verticalSpan);
	}

	public GridData_ widthHint(int widthHint) {
		gridData_.widthHint = widthHint;
		return this;
	}

	public GridData_ heightHint(int heightHint) {
		gridData_.heightHint = heightHint;
		return this;
	}

	public GridData_ horizontalIndent(int horizontalIndent) {
		gridData_.horizontalIndent = horizontalIndent;
		return this;
	}

	public GridData_ verticalIndent(int verticalIndent) {
		gridData_.verticalIndent = verticalIndent;
		return this;
	}

	public GridData build() {
		return gridData_;
	}
}
