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

import java.util.Map;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;


/**
 * alpha 52: columns of a table centred or to the right, drawn by DBGL: Windows drew the text of such columns (set after the
 * table was shown) only once the mouse went over the row, and left parts behind when rows went away.
 */
public final class AlignedColumns {

	private static final int PADDING = 6;

	private AlignedColumns() {
	}

	/** @param alignment by the index of the column: SWT.CENTER or SWT.RIGHT */
	public static void install(Table table, Map<Integer, Integer> alignment) {
		table.addListener(SWT.EraseItem, e -> {
			if (alignment.containsKey(e.index))
				e.detail &= ~SWT.FOREGROUND; // the text drawn below, not by the system
		});
		table.addListener(SWT.PaintItem, e -> {
			Integer align = alignment.get(e.index);
			if (align == null)
				return;
			TableItem item = (TableItem)e.item;
			String text = item.getText(e.index);
			if (text.isEmpty())
				return;
			Rectangle cell = item.getBounds(e.index);
			Point size = e.gc.textExtent(text);
			int x = align == SWT.RIGHT ? cell.x + cell.width - size.x - PADDING: cell.x + (cell.width - size.x) / 2;
			e.gc.drawText(text, Math.max(cell.x, x), cell.y + (cell.height - size.y) / 2, true);
		});
	}

	/** the columns as wide as their content, the drawn ones with room for their text */
	public static void pack(Table table, Map<Integer, Integer> alignment) {
		org.eclipse.swt.widgets.TableColumn[] columns = table.getColumns();
		for (int i = 0; i < columns.length; i++) {
			columns[i].pack();
			if (alignment.containsKey(i))
				columns[i].setWidth(columns[i].getWidth() + PADDING);
		}
	}

	/** changes of the rows shown at once and completely (Windows: else parts of rows gone stay until the mouse comes) */
	public static void redrawAll(Table table) {
		table.setRedraw(true);
		table.redraw();
		table.update();
	}
}
