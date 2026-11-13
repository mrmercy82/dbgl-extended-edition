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
	private static final String REPACKED = "dbgl.repacked"; // a hidden setting moved to the end: only its exclude is ours
	private static final String ORDER = "dbgl.order"; // the children in the order they were created
	private static final String PADDING = "dbgl.padding"; // an empty label of ours filling up a row before a whole row

	/**
	 * alpha 48: what the arrangement of a page depends on: which of its controls exist and are visible (without our own filler
	 * labels), and the mode; the same signature means compacting and aligning again would give the same result
	 */
	public static String signature(Composite page, boolean align) {
		StringBuilder sb = new StringBuilder(align ? "A": "-");
		java.util.ArrayDeque<Control> todo = new java.util.ArrayDeque<>(java.util.List.of(page.getChildren()));
		while (!todo.isEmpty()) {
			Control c = todo.pop();
			if (c.isDisposed() || c.getData(PADDING) != null)
				continue;
			sb.append(System.identityHashCode(c)).append(c.getVisible() ? '+': '-');
			if (c instanceof Composite comp)
				for (Control child: comp.getChildren())
					todo.push(child);
		}
		return sb.toString();
	}

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
		restoreOrder(composite);
		for (Control c: composite.getChildren()) {
			if (Boolean.TRUE.equals(c.getData(COMPACTED))) {
				c.setData(COMPACTED, null);
				((GridData)c.getLayoutData()).exclude = false;
				// only what this class hid itself: groups/composites and fillers. Combos and spinners are composites too in SWT,
				// but their visibility belongs to the setting (hidden when the DOSBox version does not have it)
				if (((c instanceof Composite) && !(c instanceof org.eclipse.swt.widgets.Combo) && !(c instanceof org.eclipse.swt.widgets.Spinner)) || isFiller(c))
					c.setVisible(true);
			}
		}

		repack(composite, numColumns);

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

	/** the children back in the order they were created, the hidden settings moved by repack back in their place */
	private static void restoreOrder(Composite composite) {
		for (Control c: composite.getChildren())
			if (Boolean.TRUE.equals(c.getData(PADDING)))
				c.dispose();
		for (Control c: composite.getChildren()) {
			if (Boolean.TRUE.equals(c.getData(REPACKED))) {
				c.setData(REPACKED, null);
				((GridData)c.getLayoutData()).exclude = false;
			}
		}
		@SuppressWarnings("unchecked")
		List<Control> order = (List<Control>)composite.getData(ORDER);
		if (order != null)
			reorder(composite, order);
	}

	/**
	 * Alpha 48: settings side by side ("label field   label field"): when one of them is hidden, the others close up, so no
	 * row shows a single setting at the right with a hole at the left. Only for groups made of such pairs (two columns wide),
	 * the empty labels between them and rows over the whole width (these stay where they are and the pairs between them close
	 * up on their own); anything else is left as it is.
	 */
	private static void repack(Composite composite, int numColumns) {
		// label field filler label field (filler), or label field label field (6 columns can be either)
		List<Object> entries = null;
		boolean fillers = false;
		if ((numColumns >= 5) && (numColumns % 3 != 1)) {
			entries = read(composite, numColumns, true);
			fillers = entries != null;
		}
		if ((entries == null) && (numColumns >= 4) && (numColumns % 2 == 0))
			entries = read(composite, numColumns, false);
		if (entries == null)
			return;

		// the pairs between two whole rows close up, the hidden ones go to the end
		List<Control> order = new ArrayList<>();
		List<Control> hidden = new ArrayList<>(), spare = new ArrayList<>();
		boolean anyHidden = false;
		List<Integer> paddings = new ArrayList<>(); // made only when the grid is changed (null in order)
		List<List<Control>> pairs = new ArrayList<>();
		List<Control> pool = new ArrayList<>();
		for (int i = 0; i <= entries.size(); i++) {
			Object e = (i < entries.size()) ? entries.get(i): null;
			if ((e == null) || (e instanceof Row)) {
				// place the pairs of this run
				int c = 0, next = 0;
				for (List<Control> pair: pairs) {
					boolean shown = pair.stream().allMatch(GridCompactor::isFiller) || pair.stream().anyMatch(x -> !isFiller(x) && x.getVisible());
					if (!shown) {
						hidden.addAll(pair);
						anyHidden = true;
						continue;
					}
					order.addAll(pair);
					c += 2;
					if (fillers && (c < numColumns) && (c % 3 == 2)) {
						if (next >= pool.size())
							return;
						order.add(pool.get(next++));
						c++;
					}
					if (c >= numColumns)
						c = 0;
				}
				spare.addAll(pool.subList(next, pool.size()));
				pairs.clear();
				pool.clear();
				if (e != null) {
					if (c != 0) {
						order.add(null); // the whole row must start a row of its own
						paddings.add(numColumns - c);
					}
					order.addAll(((Row)e).controls());
				}
			} else if (e instanceof Control filler) {
				pool.add(filler);
			} else {
				@SuppressWarnings("unchecked")
				List<Control> pair = (List<Control>)e;
				pairs.add(pair);
			}
		}
		if (!anyHidden)
			return;

		if (composite.getData(ORDER) == null)
			composite.setData(ORDER, new ArrayList<>(List.of(composite.getChildren())));
		for (int i = 0, p = 0; i < order.size(); i++)
			if (order.get(i) == null)
				order.set(i, paddingOf(composite, paddings.get(p++)));
		for (Control c: hidden) {
			c.setData(REPACKED, true);
			((GridData)c.getLayoutData()).exclude = true;
		}
		for (Control c: spare) {
			c.setData(COMPACTED, true);
			((GridData)c.getLayoutData()).exclude = true;
			c.setVisible(false);
		}
		for (Control c: composite.getChildren())
			if (!order.contains(c))
				order.add(c);
		reorder(composite, order);
	}

	/** the grid as GridLayout lays it out: pairs (List of Control), the fillers between them (Control), whole rows (Row); null: another kind of grid */
	private static List<Object> read(Composite composite, int numColumns, boolean fillers) {
		List<Object> entries = new ArrayList<>();
		List<Control> item = null;
		int itemWidth = 0, itemCol = 0, col = 0;
		for (Control c: composite.getChildren()) {
			if (c.getLayoutData() == null)
				c.setLayoutData(new GridData()); // the same as none
			if (!(c.getLayoutData() instanceof GridData gd))
				return null;
			if (gd.exclude)
				continue;
			if (gd.verticalSpan > 1)
				return null;
			int span = Math.max(1, gd.horizontalSpan);
			if (item == null) {
				if (span > numColumns - col)
					col = 0; // wraps to the next row
				if (fillers && (col % 3 == 2)) {
					if (!isFiller(c) || (span != 1))
						return null;
					entries.add(c);
					col = (col + 1) % numColumns;
					continue;
				}
				item = new ArrayList<>();
				itemWidth = 0;
				itemCol = col;
			}
			if (itemCol + itemWidth + span > numColumns)
				return null;
			item.add(c);
			itemWidth += span;
			if (itemWidth == 2) {
				entries.add(item);
				item = null;
				col = (itemCol + 2) % numColumns;
			} else if ((itemWidth > 2) && (itemCol != 0)) {
				return null;
			} else if (itemWidth == numColumns) {
				entries.add(new Row(item));
				item = null;
				col = 0;
			}
		}
		if (item != null)
			return null;
		return entries;
	}

	private static Control paddingOf(Composite composite, int span) {
		org.eclipse.swt.widgets.Label padding = new org.eclipse.swt.widgets.Label(composite, org.eclipse.swt.SWT.NONE);
		padding.setData(PADDING, true);
		padding.setLayoutData(new GridData(org.eclipse.swt.SWT.FILL, org.eclipse.swt.SWT.CENTER, false, false, span, 1));
		return padding;
	}

	private record Row(List<Control> controls) {
	}

	private static void reorder(Composite composite, List<Control> order) {
		Control previous = null;
		for (Control c: order) {
			if (c.isDisposed() || (c.getParent() != composite))
				continue;
			if (previous == null)
				c.moveAbove(null);
			else
				c.moveBelow(previous);
			previous = c;
		}
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
	private static final String INDENT = "dbgl.indent"; // the vertical indent of a group before dropLeadingIndent took it away

	/**
	 * Alpha 48: a group set apart from the settings above it by some space, when nothing above it is shown (only the title of
	 * the page): it moves up, no empty row any more.
	 */
	public static void dropLeadingIndent(Composite page) {
		for (Control c: page.getChildren()) {
			if ((c.getData(INDENT) instanceof Integer indent) && (c.getLayoutData() instanceof GridData gd)) {
				gd.verticalIndent = indent;
				c.setData(INDENT, null);
			}
		}
		Control[] children = page.getChildren();
		for (int i = 0; i < children.length; i++) {
			Control c = children[i];
			GridData gd = (c.getLayoutData() instanceof GridData d) ? d: null;
			if (!c.getVisible() || ((gd != null) && gd.exclude))
				continue;
			if ((i == 0) || ((c instanceof Label l) && ((l.getStyle() & SWT.SEPARATOR) != 0)))
				continue; // the title of the page and the line below it
			if ((c instanceof Composite) && (gd != null) && (gd.verticalIndent > 0)) {
				c.setData(INDENT, gd.verticalIndent);
				gd.verticalIndent = 0;
			}
			return;
		}
	}

	/** alpha 48: setData key of a field whose width is chosen on purpose (alignColumns leaves it alone) */
	public static final String KEEP_WIDTH = "dbgl.keepwidth";
	private static final String ALIGNED = "dbgl.aligned"; // {widthHint, grab} of a filler before alignColumns changed it
	private static final int GAP = 48, FIELD = 180; // alpha 48: the right column a bit further from the left one (was 16)

	/**
	 * Alpha 48: the right column of settings side by side starts where it starts in the other groups of the page, close to
	 * the left one, not at the far right: the empty labels between them no longer take the free space, they are as wide as
	 * needed to line the right columns of all groups up.
	 *
	 * @param align false: as it was before (all settings shown)
	 */
	public static void alignColumns(Composite page, boolean align) {
		List<Control> all = new ArrayList<>();
		collect(page, all);
		for (Control c: all) {
			if ((c.getData(ALIGNED) instanceof int[] o) && (c.getLayoutData() instanceof GridData gd)) {
				gd.widthHint = o[0];
				gd.grabExcessHorizontalSpace = o[1] == 1;
				if (o.length > 2)
					gd.horizontalAlignment = o[2];
				c.setData(ALIGNED, null);
			}
		}
		if (!align)
			return;

		// alpha 48: one-line fields no longer take all the free width: as wide as their column (so they end where the field
		// above ends) and at least wide enough for a short path
		for (Control c: all) {
			boolean oneLine = ((c instanceof org.eclipse.swt.widgets.Text) && ((c.getStyle() & SWT.MULTI) == 0))
					|| ((c instanceof org.eclipse.swt.widgets.Combo) && ((c.getStyle() & SWT.READ_ONLY) == 0));
			if (oneLine && (c.getData(KEEP_WIDTH) == null) && (c.getLayoutData() instanceof GridData gd) && gd.grabExcessHorizontalSpace && (c.getData(ALIGNED) == null)
					&& (c.getParent().getLayout() instanceof GridLayout)) {
				c.setData(ALIGNED, new int[] {gd.widthHint, 1, gd.horizontalAlignment});
				gd.grabExcessHorizontalSpace = false;
				if (gd.horizontalSpan <= 1) {
					int column = widthOfColumn(c); // as wide as the list above it, or wide enough for a short path
					int trim = (c instanceof org.eclipse.swt.widgets.Text t) ? t.computeTrim(0, 0, 0, 0).width: 0;
					gd.horizontalAlignment = SWT.BEGINNING;
					gd.widthHint = (column > 0) ? Math.max(120, column - trim): -FIELD; // -: decided below
				} // over several columns: as wide as they are (up to the button after it)
			} else if ((c instanceof org.eclipse.swt.widgets.Button) && ((c.getStyle() & SWT.PUSH) != 0) && (c.getLayoutData() instanceof GridData gd)
					&& (gd.horizontalAlignment == SWT.FILL) && (c.getData(ALIGNED) == null) && (c.getParent().getLayout() instanceof GridLayout)
					&& (((GridLayout)c.getParent().getLayout()).numColumns > 1)) { // not a column of buttons
				// a button next to a field ("...") stays as wide as its text
				c.setData(ALIGNED, new int[] {gd.widthHint, gd.grabExcessHorizontalSpace ? 1: 0, gd.horizontalAlignment});
				gd.horizontalAlignment = SWT.BEGINNING;
				gd.grabExcessHorizontalSpace = false;
			}
		}

		// a field without a list above it: as wide as the other fields of its group (e.g. the y next to the x calibration)
		for (Control c: all) {
			if ((c.getData(ALIGNED) != null) && (c.getLayoutData() instanceof GridData gd) && (gd.widthHint == -FIELD)) {
				int others = 0;
				for (Control o: c.getParent().getChildren())
					if ((o != c) && (o.getData(ALIGNED) != null) && (o.getLayoutData() instanceof GridData od) && (od.widthHint > 0) && (od.horizontalSpan <= 1)
							&& ((o instanceof org.eclipse.swt.widgets.Text) || (o instanceof org.eclipse.swt.widgets.Combo)))
						others = Math.max(others, od.widthHint);
				gd.widthHint = (others > 0) ? others: FIELD;
			}
		}

		java.util.Map<Composite, List<Control>> splits = new java.util.LinkedHashMap<>();
		List<Composite> composites = new ArrayList<>();
		composites.add(page);
		for (Control c: all)
			if (c instanceof Composite cc)
				composites.add(cc);
		for (Composite c: composites) {
			List<Control> fillers = middleFillers(c);
			if (!fillers.isEmpty())
				splits.put(c, fillers);
		}
		if (splits.isEmpty())
			return;

		for (java.util.Map.Entry<Composite, List<Control>> e: splits.entrySet()) {
			for (Control f: e.getValue()) {
				GridData gd = (GridData)f.getLayoutData();
				f.setData(ALIGNED, new int[] {gd.widthHint, gd.grabExcessHorizontalSpace ? 1: 0});
				gd.grabExcessHorizontalSpace = false;
				gd.widthHint = SWT.DEFAULT;
			}
			// the free space goes to the right of the split, so the left part is as wide at any width of the window
			for (Control c: leftOfSplit(e.getKey(), e.getValue())) {
				GridData gd = (GridData)c.getLayoutData();
				c.setData(ALIGNED, new int[] {gd.widthHint, 1});
				gd.grabExcessHorizontalSpace = false;
			}
		}
		org.eclipse.swt.graphics.Point size = page.getSize();
		page.setSize(page.computeSize(SWT.DEFAULT, SWT.DEFAULT, true));
		page.layout(true, true);
		java.util.Map<Composite, Integer> rightStart = new java.util.HashMap<>();
		int target = 0;
		for (java.util.Map.Entry<Composite, List<Control>> e: splits.entrySet()) {
			int x = e.getValue().stream().mapToInt(f -> f.getParent().toDisplay(f.getBounds().x + f.getBounds().width, 0).x).max().getAsInt();
			rightStart.put(e.getKey(), x);
			target = Math.max(target, x);
		}
		for (java.util.Map.Entry<Composite, List<Control>> e: splits.entrySet()) {
			int delta = target - rightStart.get(e.getKey());
			for (Control f: e.getValue())
				((GridData)f.getLayoutData()).widthHint = f.getBounds().width + GAP + delta;
		}
		page.setSize(size);
		page.layout(true, true);
	}

	/** as wide as the widest other field in the same column(s), e.g. the list above it */
	private static int widthOfColumn(Control field) {
		List<int[]> cells = new ArrayList<>();
		List<Control> controls = new ArrayList<>();
		cellsOf(field.getParent(), controls, cells);
		if (!controls.contains(field))
			return 0; // left out (hidden)
		int[] cell = cells.get(controls.indexOf(field));
		int width = 0;
		for (int i = 0; i < controls.size(); i++) {
			Control c = controls.get(i);
			if ((c == field) || !c.getVisible() || (c instanceof org.eclipse.swt.widgets.Text) || (c instanceof org.eclipse.swt.widgets.Label) || (c instanceof org.eclipse.swt.widgets.Button)
					|| (c instanceof Composite && !(c instanceof org.eclipse.swt.widgets.Combo || c instanceof org.eclipse.swt.widgets.Spinner))
					|| ((c.getLayoutData() instanceof GridData gd) && gd.grabExcessHorizontalSpace))
				continue;
			if ((cells.get(i)[0] == cell[0]) && (cells.get(i)[1] == cell[1]))
				width = Math.max(width, c.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
		}
		return width;
	}

	/** the controls taking free space that lie completely left of the split (not those reaching over it) */
	private static List<Control> leftOfSplit(Composite composite, List<Control> fillers) {
		List<Control> result = new ArrayList<>();
		List<int[]> cells = new ArrayList<>();
		List<Control> controls = new ArrayList<>();
		cellsOf(composite, controls, cells);
		int split = Integer.MAX_VALUE, splitEnd = 0;
		for (int i = 0; i < controls.size(); i++) {
			if (fillers.contains(controls.get(i))) {
				split = Math.min(split, cells.get(i)[0]);
				splitEnd = Math.max(splitEnd, cells.get(i)[0] + cells.get(i)[1]);
			}
		}
		for (int i = 0; i < controls.size(); i++) {
			Control c = controls.get(i);
			int end = cells.get(i)[0] + cells.get(i)[1];
			// also the empty places in the columns of the split, in rows without a setting at the right
			boolean left = (end <= split) || (isFiller(c) && (end <= splitEnd));
			if (!fillers.contains(c) && left && (c.getLayoutData() instanceof GridData gd) && gd.grabExcessHorizontalSpace && (c.getData(ALIGNED) == null))
				result.add(c);
		}
		return result;
	}

	/** where GridLayout puts the children: start column, span, row */
	private static void cellsOf(Composite composite, List<Control> controls, List<int[]> cells) {
		int n = ((GridLayout)composite.getLayout()).numColumns, col = 0, row = 0;
		for (Control c: composite.getChildren()) {
			GridData gd = (c.getLayoutData() instanceof GridData d) ? d: null;
			if ((gd != null) && gd.exclude)
				continue;
			int span = Math.min(n, Math.max(1, (gd == null) ? 1: gd.horizontalSpan));
			if (col + span > n) {
				col = 0;
				row++;
			}
			cells.add(new int[] {col, span, row});
			controls.add(c);
			col += span;
			if (col >= n) {
				col = 0;
				row++;
			}
		}
	}

	/** the empty labels taking the free space between a setting at the left and one at the right of the same row */
	private static List<Control> middleFillers(Composite composite) {
		List<Control> result = new ArrayList<>();
		if (!(composite.getLayout() instanceof GridLayout layout) || (layout.numColumns < 4))
			return result;
		int n = layout.numColumns;
		List<int[]> cells = new ArrayList<>(); // start, span, row
		List<Control> controls = new ArrayList<>();
		cellsOf(composite, controls, cells);
		for (int i = 0; i < controls.size(); i++) {
			Control c = controls.get(i);
			int[] cell = cells.get(i);
			if (!isFiller(c) || !c.getVisible() || !(c.getLayoutData() instanceof GridData gd) || !gd.grabExcessHorizontalSpace)
				continue;
			if ((cell[0] < 2) || (cell[0] + cell[1] > n - 2))
				continue;
			boolean before = false, after = false;
			for (int j = 0; j < controls.size(); j++) {
				int[] other = cells.get(j);
				if ((other[2] != cell[2]) || isFiller(controls.get(j)) || !controls.get(j).getVisible())
					continue;
				if (other[0] < cell[0])
					before = true;
				else if (other[0] > cell[0])
					after = true;
			}
			if (before && after)
				result.add(c);
		}
		return result;
	}

	private static void collect(Composite composite, List<Control> out) {
		for (Control c: composite.getChildren()) {
			out.add(c);
			if (c instanceof Composite cc)
				collect(cc, out);
		}
	}

	private static boolean isFiller(Control c) {
		return (c instanceof Label l) && ((l.getStyle() & SWT.SEPARATOR) == 0) && l.getText().isEmpty() && (l.getImage() == null);
	}
}
