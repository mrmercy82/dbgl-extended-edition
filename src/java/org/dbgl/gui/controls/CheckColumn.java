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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;


/**
 * alpha 48: check boxes drawn by DBGL in the first column of a table (made without SWT.CHECK), so that a box that cannot be
 * changed looks greyed out. A table of SWT has no disabled boxes: setGrayed shows a third state instead (a black square under
 * Linux, a filled box under Windows), which looked like "half chosen". Click on the box or the space bar changes it.
 */
public final class CheckColumn {

	private static final String CHECKED = "dbgl.check.checked", LOCKED = "dbgl.check.locked";

	private final Table table_;
	private final Runnable changed_;
	private final Image room_;
	private final int size_;

	private CheckColumn(Table table, Runnable changed, int extra) {
		table_ = table;
		changed_ = changed;
		int size = Math.max(16, table.getItemHeight() - 4);
		size_ = size;
		// a transparent picture that keeps the room for the box in front of the text (and for more, e.g. a symbol)
		ImageData data = new ImageData(size + 4 + extra, size, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
		data.alphaData = new byte[data.width * data.height];
		room_ = new Image(table.getDisplay(), data);
		table.addDisposeListener(e -> room_.dispose());
		table.addListener(SWT.PaintItem, this::paint);
		table.addListener(SWT.MouseDown, e -> {
			if (e.button != 1)
				return;
			TableItem item = table.getItem(new org.eclipse.swt.graphics.Point(e.x, e.y));
			if (item != null) {
				Rectangle box = item.getImageBounds(0);
				if ((item.getData(CHECKED) != null) && (e.x >= box.x - 2) && (e.x <= box.x + size_ + 4))
					toggle(item);
			}
		});
		table.addListener(SWT.KeyDown, e -> {
			if (e.character == ' ') {
				for (TableItem item: table.getSelection())
					if (item.getData(CHECKED) != null)
						toggle(item);
				e.doit = false;
			}
		});
	}

	/** @param changed run after the user changed a box */
	public static CheckColumn on(Table table, Runnable changed) {
		return new CheckColumn(table, changed, 0);
	}

	/** @param extra room after the box (e.g. for a symbol drawn there, from x() + boxWidth()) */
	public static CheckColumn on(Table table, Runnable changed, int extra) {
		return new CheckColumn(table, changed, extra);
	}

	/** the width taken by the box and the space after it */
	public int boxWidth() {
		return size_ + 4;
	}

	/** an item without a box, its text where the others start (e.g. a line between groups) */
	public void addWithoutBox(TableItem item) {
		item.setImage(0, room_);
	}

	/** give a new item its box */
	public void add(TableItem item, boolean checked, boolean locked) {
		item.setImage(0, room_);
		set(item, checked, locked);
	}

	public void set(TableItem item, boolean checked, boolean locked) {
		if (Boolean.valueOf(checked).equals(item.getData(CHECKED)) && Boolean.valueOf(locked).equals(item.getData(LOCKED)))
			return;
		item.setData(CHECKED, checked);
		item.setData(LOCKED, locked);
		Rectangle r = item.getBounds(0);
		table_.redraw(r.x, r.y, r.width, r.height, false);
	}

	public static boolean checked(TableItem item) {
		return Boolean.TRUE.equals(item.getData(CHECKED));
	}

	private void toggle(TableItem item) {
		if (Boolean.TRUE.equals(item.getData(LOCKED)) || !table_.isEnabled())
			return;
		set(item, !checked(item), false);
		changed_.run();
	}

	private void paint(Event e) {
		if ((e.index != 0) || !(e.item instanceof TableItem item) || (item.getData(CHECKED) == null))
			return;
		Rectangle img = item.getImageBounds(0);
		int s = Math.min(img.height, size_) - 2;
		int x = img.x + 1, y = img.y + (img.height - s) / 2;
		GC gc = e.gc;
		boolean locked = Boolean.TRUE.equals(item.getData(LOCKED)) || !table_.isEnabled();
		RGB bg = table_.getBackground().getRGB();
		// by the table itself (a window that is not dark in the dark mode would get a white mark on white)
		boolean dark = (bg.red * 299 + bg.green * 587 + bg.blue * 114) / 1000 < 128;
		// greyed: the colours of a disabled box (lighter border and mark, the inside a little grey)
		Color border = new Color(locked ? mix(bg, dark ? 0x80: 0xA8): (dark ? new RGB(0xC8, 0xC8, 0xC8): new RGB(0x50, 0x50, 0x50)));
		Color inside = new Color(locked ? mix(bg, dark ? 0x3C: 0xE6): bg);
		Color mark = new Color(locked ? mix(bg, dark ? 0x8C: 0x9A): (dark ? new RGB(0xFF, 0xFF, 0xFF): new RGB(0x10, 0x10, 0x10)));
		int oldAa = gc.getAntialias(), oldW = gc.getLineWidth();
		gc.setAntialias(SWT.ON);
		gc.setBackground(inside);
		gc.fillRoundRectangle(x, y, s, s, 4, 4);
		gc.setForeground(border);
		gc.setLineWidth(1);
		gc.drawRoundRectangle(x, y, s - 1, s - 1, 4, 4);
		if (checked(item)) {
			gc.setForeground(mark);
			gc.setLineWidth(Math.max(2, s / 7));
			gc.setLineCap(SWT.CAP_ROUND);
			gc.setLineJoin(SWT.JOIN_ROUND);
			gc.drawPolyline(new int[] {x + s * 22 / 100, y + s * 52 / 100, x + s * 42 / 100, y + s * 72 / 100, x + s * 78 / 100, y + s * 30 / 100});
		}
		gc.setLineWidth(oldW);
		gc.setAntialias(oldAa);
	}

	/** a grey of the given brightness, moved a little toward the background (so it fits light and dark tables) */
	private static RGB mix(RGB bg, int grey) {
		return new RGB((grey * 3 + bg.red) / 4, (grey * 3 + bg.green) / 4, (grey * 3 + bg.blue) / 4);
	}
}
