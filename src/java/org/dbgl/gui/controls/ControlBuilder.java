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
import org.eclipse.swt.widgets.Composite;


public class ControlBuilder<T extends ControlBuilder<T>> {

	protected final Composite composite_;
	private Object customLayoutData_;
	protected int style_;
	protected int horizontalAlignment_, verticalAlignment_;
	protected boolean grabExcessHorizontalSpace_, grabExcessVerticalSpace_;
	protected int horSpan_ = 1;
	protected int widthHint_ = -1;
	protected int heightHint_ = -1;

	protected ControlBuilder(Composite composite, int style, int horizontalAlignment, int verticalAlignment, boolean grabExcessHorizontalSpace, boolean grabExcessVerticalSpace) {
		composite_ = composite;
		style_ = style;
		horizontalAlignment_ = horizontalAlignment;
		verticalAlignment_ = verticalAlignment;
		grabExcessHorizontalSpace_ = grabExcessHorizontalSpace;
		grabExcessVerticalSpace_ = grabExcessVerticalSpace;
	}

	@SuppressWarnings("unchecked")
	public T getThis() {
		return (T)this;
	}

	public T style(int style) {
		style_ = style;
		return getThis();
	}

	public T layoutData(Object layoutData) {
		customLayoutData_ = layoutData;
		return getThis();
	}

	public T horSpan(int horizontalSpan) {
		horSpan_ = horizontalSpan;
		return getThis();
	}

	public T widthHint(int widthHint) {
		widthHint_ = widthHint;
		return getThis();
	}
	
	public T heightHint(int heightHint) {
		heightHint_ = heightHint;
		return getThis();
	}

	protected Object layoutData() {
		if (customLayoutData_ != null) {
			return customLayoutData_;
		} else {
			GridData gd = new GridData(horizontalAlignment_, verticalAlignment_, grabExcessHorizontalSpace_, grabExcessVerticalSpace_, horSpan_, 1);
			if (widthHint_ != -1)
				gd.widthHint = widthHint_;
			if (heightHint_ != -1)
				gd.heightHint = heightHint_;
			return gd;
		}
	}
}
