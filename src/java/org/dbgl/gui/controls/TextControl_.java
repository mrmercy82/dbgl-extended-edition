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

import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Text;


public class TextControl_ {

	private Text text_;
	private Combo combo_;

	public TextControl_(Text text) {
		text_ = text;
	}

	public TextControl_(Combo combo) {
		combo_ = combo;
	}

	public void setText(String text) {
		if (text_ != null)
			text_.setText(text);
		else if (combo_ != null)
			combo_.setText(text);
	}

	public String getText() {
		if (text_ != null)
			return text_.getText();
		else if (combo_ != null)
			return combo_.getText();
		return null;
	}

	public String getLineDelimiter() {
		if (text_ != null)
			return text_.getLineDelimiter();
		return null;
	}

	public void selectAll() {
		if (text_ != null)
			text_.selectAll();
	}

	public void setFocus() {
		if (text_ != null)
			text_.setFocus();
		else if (combo_ != null)
			combo_.setFocus();
	}
}
