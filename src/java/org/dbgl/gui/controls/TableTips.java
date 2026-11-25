/*
 *  Copyright (C) 2026  mrmercy82 (DBGL Extended Edition, written with the help of AI)
 *  Part of a modified version of DBGL, Copyright (C) 2006-2025  Ronald Blankendaal
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
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;


/**
 * TEST32: a tooltip for each cell of a table (a table has only one of its own): the text for the row and column under the
 * mouse; column -1 is the check box of a table with SWT.CHECK (left of the first column's text).
 */
public final class TableTips {

	/** the tooltip for a cell, or null for none */
	public interface Tip {
		String at(int row, int column);
	}

	private TableTips() {
	}

	public static void install(Table table, Tip tip) {
		String[] shown = {null};
		table.addListener(SWT.MouseMove, e -> {
			String text = null;
			Point pt = new Point(e.x, e.y);
			TableItem item = table.getItem(pt);
			if (item != null) {
				int row = table.indexOf(item);
				int column = -2;
				for (int c = 0; c < Math.max(1, table.getColumnCount()); c++) {
					Rectangle r = item.getBounds(c);
					if (r.contains(pt)) {
						column = c;
						break;
					}
				}
				Rectangle first = item.getBounds(0);
				if (((table.getStyle() & SWT.CHECK) != 0) && (e.x < first.x + Math.min(first.width, 24)) && (e.x >= 0) && (column <= 0)) {
					Rectangle text0 = item.getTextBounds(0);
					Rectangle img0 = item.getImageBounds(0);
					int contentStart = Math.min(text0.width > 0 ? text0.x: Integer.MAX_VALUE, img0.width > 0 ? img0.x: Integer.MAX_VALUE);
					if ((e.x < first.x) || (e.x < contentStart))
						column = -1;
				}
				if (column >= -1)
					text = tip.at(row, column);
			}
			if ((text == null) ? (shown[0] != null): !text.equals(shown[0])) {
				shown[0] = text;
				table.setToolTipText(text);
			}
		});
		table.addListener(SWT.MouseExit, e -> {
			shown[0] = null;
			table.setToolTipText(null);
		});
	}
}
