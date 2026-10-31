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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;


/**
 * Removes the gaps left by hidden settings: grid rows in which nothing visible remains are taken out of the layout, and
 * groups without any visible setting are hidden. Rows that still show something keep all their cells, so columns stay
 * aligned. Can be called repeatedly, e.g. after switching to another DOSBox version.
 */
public class GridCompactor {

	private static final String COMPACTED = "dbgl.compacted";

	private GridCompactor() {
	}

	/** @return true if the composite still contains anything visible */
	public static boolean compact(Composite composite) {
		boolean anyContent = false;
		if (!(composite.getLayout() instanceof GridLayout)) {
			for (Control c: composite.getChildren())
				if (c instanceof Composite cc)
					compact(cc);
			return true;
		}
		int numColumns = ((GridLayout)composite.getLayout()).numColumns;

		// first restore everything this class took out before, the situation may have changed
		for (Control c: composite.getChildren()) {
			if (Boolean.TRUE.equals(c.getData(COMPACTED))) {
				c.setData(COMPACTED, null);
				((GridData)c.getLayoutData()).exclude = false;
				if ((c instanceof Composite) || isFiller(c))
					c.setVisible(true);
			}
		}

		List<Control> row = new ArrayList<>();
		int col = 0;
		for (Control c: composite.getChildren()) {
			if (!(c.getLayoutData() instanceof GridData gd) || gd.exclude)
				continue;
			if (c instanceof Composite cc && !(c instanceof org.eclipse.swt.widgets.Combo) && !(c instanceof org.eclipse.swt.widgets.Spinner)
					&& (cc.getLayout() instanceof GridLayout) && !compact(cc) && cc.getChildren().length > 0) {
				// a group/composite without visible settings: hide it
				cc.setData(COMPACTED, true);
				cc.setVisible(false);
				gd.exclude = true;
				continue;
			}
			int span = Math.max(1, Math.min(gd.horizontalSpan, numColumns));
			if (col + span > numColumns) {
				anyContent |= closeRow(row);
				row.clear();
				col = 0;
			}
			row.add(c);
			col += span;
			if (col >= numColumns) {
				anyContent |= closeRow(row);
				row.clear();
				col = 0;
			}
		}
		anyContent |= closeRow(row);
		return anyContent;
	}

	/** @return true if the row has visible content (and is kept) */
	private static boolean closeRow(List<Control> row) {
		if (row.isEmpty())
			return false;
		boolean hasHidden = false;
		for (Control c: row) {
			if (isFiller(c))
				continue;
			if (c.getVisible())
				return true;
			hasHidden = true;
		}
		if (!hasHidden)
			return false; // only fillers, e.g. an empty row on purpose: leave it alone
		for (Control c: row) {
			c.setData(COMPACTED, true);
			((GridData)c.getLayoutData()).exclude = true;
			c.setVisible(false);
		}
		return false;
	}

	/** empty labels used to fill cells */
	private static boolean isFiller(Control c) {
		return (c instanceof Label l) && ((l.getStyle() & SWT.SEPARATOR) == 0) && l.getText().isEmpty() && (l.getImage() == null);
	}
}
