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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;


/**
 * Alpha 48, Windows, dark mode, "alternative controls": a few things SWT cannot do there, done by listening to the window of the
 * control before SWT does (one shared callback for all of them):
 * <ul>
 * <li>one-line text fields and the field of editable drop-downs: Windows writes the text at the top of the field; the room
 * above and below the line becomes border (WM_NCCALCSIZE, painted in the colour of the field), so the text is in the middle;</li>
 * <li>push buttons: drawn only by DBGL (owner draw), so that Windows does not draw its light hover first (they flickered).</li>
 * </ul>
 * Everything through reflection (the classes of SWT for Windows are not there when DBGL is built elsewhere); if anything is
 * missing, the control stays as it was.
 */
final class WinSubclass {

	private static final int WM_WINDOWPOSCHANGING = 0x0046, WM_NCDESTROY = 0x0082, WM_NCCALCSIZE = 0x0083, WM_NCHITTEST = 0x0084, WM_NCPAINT = 0x0085;
	private static final int BM_SETSTYLE = 0x00F4, BS_TYPEMASK = 0x000F, BS_OWNERDRAW = 0x000B;
	private static final int WM_ENABLE = 0x000A, WM_SETTEXT = 0x000C;
	private static final int HTNOWHERE = 0, HTCLIENT = 1, HTBORDER = 18;

	private record Entry(long oldProc, Control control, int lineHeight, boolean ownerDraw, long comboHandle) {
	}

	private static WinSubclass instance_;
	private static boolean failed_;

	private final Class<?> os_, rect_;
	private final Method get_, set_, call_, readInts_, writeInts_, setWindowPos_, getWindowRect_, getClientRect_, mapRect_, getWindowDC_, releaseDC_,
			fillRect_, createBrush_, deleteObject_, getWindow_, getWindowLong_, setWindowLong_;
	private final int gwlpWndProc_;
	private final long address_;
	private final Object callback_;
	private final Map<Long, Entry> entries_ = new HashMap<>();

	private WinSubclass() throws ReflectiveOperationException {
		os_ = Class.forName("org.eclipse.swt.internal.win32.OS");
		rect_ = Class.forName("org.eclipse.swt.internal.win32.RECT");
		get_ = os_.getMethod("GetWindowLongPtr", long.class, int.class);
		set_ = os_.getMethod("SetWindowLongPtr", long.class, int.class, long.class);
		call_ = os_.getMethod("CallWindowProc", long.class, long.class, int.class, long.class, long.class);
		readInts_ = os_.getMethod("MoveMemory", int[].class, long.class, int.class);
		writeInts_ = os_.getMethod("MoveMemory", long.class, int[].class, int.class);
		setWindowPos_ = os_.getMethod("SetWindowPos", long.class, long.class, int.class, int.class, int.class, int.class, int.class);
		getWindowRect_ = os_.getMethod("GetWindowRect", long.class, rect_);
		getClientRect_ = os_.getMethod("GetClientRect", long.class, rect_);
		mapRect_ = os_.getMethod("MapWindowPoints", long.class, long.class, rect_, int.class);
		getWindowDC_ = os_.getMethod("GetWindowDC", long.class);
		releaseDC_ = os_.getMethod("ReleaseDC", long.class, long.class);
		fillRect_ = os_.getMethod("FillRect", long.class, rect_, long.class);
		createBrush_ = os_.getMethod("CreateSolidBrush", int.class);
		deleteObject_ = os_.getMethod("DeleteObject", long.class);
		getWindow_ = os_.getMethod("GetWindow", long.class, int.class);
		getWindowLong_ = os_.getMethod("GetWindowLong", long.class, int.class);
		setWindowLong_ = os_.getMethod("SetWindowLong", long.class, int.class, int.class);
		gwlpWndProc_ = os_.getField("GWLP_WNDPROC").getInt(null);
		Class<?> cb = Class.forName("org.eclipse.swt.internal.Callback");
		callback_ = cb.getConstructor(Object.class, String.class, int.class).newInstance(this, "windowProc", 4);
		address_ = (Long)cb.getMethod("getAddress").invoke(callback_);
		if (address_ == 0)
			throw new ReflectiveOperationException("no callback");
	}

	private static WinSubclass get() {
		if (instance_ == null && !failed_) {
			try {
				instance_ = new WinSubclass();
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				failed_ = true;
			}
		}
		return instance_;
	}

	/**
	 * Test 15: the empty boxes of a table or tree with check boxes were white inside in dark mode (Windows draws them as in
	 * light mode). The pictures of the empty boxes in the list of the control are replaced by dark ones; the ticked ones
	 * (blue, white tick) stay. Not tried out where DBGL is built: if anything is not as expected, the boxes stay as they are.
	 */
	static void darkCheckBoxes(Control c) {
		WinSubclass w = get();
		if ((w == null) || c.isDisposed())
			return;
		try {
			boolean table = c instanceof org.eclipse.swt.widgets.Table;
			Method send = w.os_.getMethod("SendMessage", long.class, int.class, long.class, long.class);
			// LVM_GETIMAGELIST / TVM_GETIMAGELIST, the list of the states (LVSIL_STATE, TVSIL_STATE)
			long list = (Long)send.invoke(null, handle(c), table ? 0x1002: 0x1108, 2L, 0L);
			if (list == 0)
				return;
			int[] cx = new int[1], cy = new int[1];
			if (!(Boolean)w.os_.getMethod("ImageList_GetIconSize", long.class, int[].class, int[].class).invoke(null, list, cx, cy) || (cx[0] < 8) || (cy[0] < 8))
				return;
			// test 18: SWT makes the boxes anew when the rows get pictures or another height (the list of covers: the empty boxes
			// were white again). Looked at whenever the control is drawn: a new list of boxes gets the dark ones again.
			c.setData(BOXES_DONE, new long[] {list, cx[0], cy[0]});
			if (c.getData(BOXES_WATCHED) == null) {
				c.setData(BOXES_WATCHED, Boolean.TRUE);
				c.addListener(org.eclipse.swt.SWT.Paint, e -> {
					if (boxesChanged(c))
						c.getDisplay().asyncExec(() -> darkCheckBoxes(c));
				});
			}
			int count = (Integer)w.os_.getMethod("ImageList_GetImageCount", long.class).invoke(null, list);
			// as SWT fills the list: a table has empty, ticked, empty, mixed and the same four greyed; a tree has nothing, empty,
			// ticked, empty, mixed
			if (count != (table ? 8: 5))
				return;
			Method replace = w.os_.getMethod("ImageList_Replace", long.class, int.class, long.class, long.class);
			org.eclipse.swt.graphics.RGB bg = c.getBackground().getRGB();
			for (int index: table ? new int[] {0, 2, 4, 6}: new int[] {1, 3}) {
				boolean greyed = table && (index >= 4);
				long bitmap = w.box(cx[0], cy[0], colorRef(bg), greyed ? 0x00606060: 0x009A9A9A, colorRef(DarkTheme.fieldBackground.getRGB()), -1);
				if (bitmap != 0) {
					replace.invoke(null, list, index, bitmap, 0L);
					w.deleteObject_.invoke(null, bitmap);
				}
			}
			// TEST34: the ticked boxes too (they were white with a black tick): the accent colour with a light tick, as the check
			// boxes of DBGL elsewhere in the dark mode; greyed ones in grey
			for (int index: table ? new int[] {1, 5}: new int[] {2}) {
				boolean greyed = table && (index >= 4);
				int fill = greyed ? 0x00505050: colorRef(DarkTheme.accentBackground.getRGB());
				long bitmap = w.box(cx[0], cy[0], colorRef(bg), fill, fill, greyed ? 0x00A0A0A0: colorRef(DarkTheme.checkForeground.getRGB()));
				if (bitmap != 0) {
					replace.invoke(null, list, index, bitmap, 0L);
					w.deleteObject_.invoke(null, bitmap);
				}
			}
			c.redraw();
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			// as they are
		}
	}

	private static final String BOXES_DONE = "dbgl.darkboxes", BOXES_WATCHED = "dbgl.darkboxes.watched";

	/** the control has another list of boxes (or boxes of another size) than the one that was made dark */
	private static boolean boxesChanged(Control c) {
		WinSubclass w = get();
		if ((w == null) || c.isDisposed() || !(c.getData(BOXES_DONE) instanceof long[] done))
			return false;
		try {
			long list = (Long)w.os_.getMethod("SendMessage", long.class, int.class, long.class, long.class).invoke(null, handle(c),
				c instanceof org.eclipse.swt.widgets.Table ? 0x1002: 0x1108, 2L, 0L);
			if (list == 0)
				return false;
			int[] cx = new int[1], cy = new int[1];
			w.os_.getMethod("ImageList_GetIconSize", long.class, int[].class, int[].class).invoke(null, list, cx, cy);
			return (list != done[0]) || (cx[0] != done[1]) || (cy[0] != done[2]);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return false;
		}
	}

	private static int colorRef(org.eclipse.swt.graphics.RGB rgb) {
		return (rgb.blue << 16) | (rgb.green << 8) | rgb.red;
	}

	/**
	 * a bitmap with a box in its middle (the caller deletes it), 0 if it cannot be made
	 * @param tick the colour of a tick in it, or -1 for an empty box
	 */
	private long box(int width, int height, int background, int border, int inner, int tick) throws ReflectiveOperationException {
		Method getDC = os_.getMethod("GetDC", long.class), createDC = os_.getMethod("CreateCompatibleDC", long.class);
		Method createBitmap = os_.getMethod("CreateCompatibleBitmap", long.class, int.class, int.class), select = os_.getMethod("SelectObject", long.class, long.class);
		Method deleteDC = os_.getMethod("DeleteDC", long.class), setRect = os_.getMethod("SetRect", rect_, int.class, int.class, int.class, int.class);
		long screen = (Long)getDC.invoke(null, 0L);
		if (screen == 0)
			return 0;
		long dc = (Long)createDC.invoke(null, screen), bitmap = (Long)createBitmap.invoke(null, screen, width, height);
		if ((dc != 0) && (bitmap != 0)) {
			long old = (Long)select.invoke(null, dc, bitmap);
			int size = Math.max(7, Math.round(Math.min(width, height) * 13 / 16f)), x = (width - size) / 2, y = (height - size) / 2;
			int[][] rects = {{0, 0, width, height, background}, {x, y, x + size, y + size, border}, {x + 1, y + 1, x + size - 1, y + size - 1, inner}};
			for (int[] r: rects) {
				Object rect = rect_.getConstructor().newInstance();
				setRect.invoke(null, rect, r[0], r[1], r[2], r[3]);
				long brush = (Long)createBrush_.invoke(null, r[4]);
				fillRect_.invoke(null, dc, rect, brush);
				deleteObject_.invoke(null, brush);
			}
			if (tick >= 0) {
				// the tick as a line in three points (PS_SOLID), as DarkTheme draws it on its own check boxes
				long pen = (Long)os_.getMethod("CreatePen", int.class, int.class, int.class).invoke(null, 0, Math.max(2, size / 7), tick);
				long oldPen = (Long)select.invoke(null, dc, pen);
				int[] pts = {x + size * 3 / 13, y + size * 7 / 13, x + size * 5 / 13, y + size * 9 / 13, x + size * 10 / 13, y + size * 4 / 13};
				os_.getMethod("Polyline", long.class, int[].class, int.class).invoke(null, dc, pts, 3);
				select.invoke(null, dc, oldPen);
				deleteObject_.invoke(null, pen);
			}
			select.invoke(null, dc, old);
		}
		if (dc != 0)
			deleteDC.invoke(null, dc);
		releaseDC_.invoke(null, 0L, screen);
		return bitmap;
	}

	/** a one-line text field, or an editable drop-down: its text in the middle */
	static void centerText(Control c) {
		WinSubclass w = get();
		if (w == null)
			return;
		try {
			long hwnd = handle(c), combo = 0;
			if (c instanceof Combo) {
				combo = hwnd;
				hwnd = (Long)w.getWindow_.invoke(null, hwnd, 5 /* GW_CHILD: the edit field */);
			} else if (c instanceof org.eclipse.swt.widgets.Spinner) {
				// alpha 49: the field with the number is a window of its own next to the arrows
				java.lang.reflect.Field text = org.eclipse.swt.widgets.Spinner.class.getDeclaredField("hwndText");
				text.setAccessible(true);
				hwnd = text.getLong(c);
			}
			if (hwnd == 0)
				return;
			org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(c);
			int line;
			try {
				line = gc.getFontMetrics().getHeight();
			} finally {
				gc.dispose();
			}
			w.install(hwnd, c, Math.round(line * zoom(c) / 100f), false, combo);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// as it is
		}
	}

	/** a push button drawn only by DBGL (its paint listener) */
	static void ownerDraw(Button b) {
		WinSubclass w = get();
		if (w == null)
			return;
		try {
			long hwnd = handle(b);
			int style = (Integer)w.getWindowLong_.invoke(null, hwnd, -16 /* GWL_STYLE */);
			int type = style & BS_TYPEMASK;
			if (type != 0 && type != 1) // only BS_PUSHBUTTON and BS_DEFPUSHBUTTON
				return;
			w.install(hwnd, b, 0, true, 0);
			w.setWindowLong_.invoke(null, hwnd, -16, (style & ~BS_TYPEMASK) | BS_OWNERDRAW);
			b.redraw();
		} catch (ReflectiveOperationException | RuntimeException e) {
			// as it is
		}
	}

	private static long handle(Control c) throws ReflectiveOperationException {
		return Control.class.getField("handle").getLong(c);
	}

	/**
	 * the zoom SWT scales this control by (100 = unscaled). SWT keeps it to itself: up to 4.37 Widget.getZoom(), since then
	 * (seen in 4.41) Widget.getAutoscalingZoom(); if neither is there, the zoom Windows gave the control, else that of its
	 * monitor.
	 */
	static int zoom(Control c) {
		for (String name: new String[] {"getZoom", "getAutoscalingZoom"}) {
			try {
				Method m = org.eclipse.swt.widgets.Widget.class.getDeclaredMethod(name);
				m.setAccessible(true);
				int z = (Integer)m.invoke(c);
				if (z > 0)
					return z;
			} catch (ReflectiveOperationException | RuntimeException e) {
				// the next one
			}
		}
		try {
			int z = org.eclipse.swt.widgets.Widget.class.getField("nativeZoom").getInt(c);
			if (z > 0)
				return z;
		} catch (ReflectiveOperationException | RuntimeException e) {
			// below
		}
		return Math.max(100, c.getMonitor().getZoom());
	}

	private void install(long hwnd, Control c, int lineHeight, boolean ownerDraw, long combo) throws ReflectiveOperationException {
		if (entries_.containsKey(hwnd))
			return;
		long old = (Long)get_.invoke(null, hwnd, gwlpWndProc_);
		if (old == 0 || old == address_)
			return;
		entries_.put(hwnd, new Entry(old, c, lineHeight, ownerDraw, combo));
		set_.invoke(null, hwnd, gwlpWndProc_, address_);
		if (ownerDraw)
			return;
		if (combo != 0) { // the field of a drop-down: placed again (in the middle of the drop-down, see below)
			Object r = rect_.getConstructor().newInstance();
			getWindowRect_.invoke(null, hwnd, r);
			mapRect_.invoke(null, 0L, combo, r, 2);
			int l = rect_.getField("left").getInt(r), t = rect_.getField("top").getInt(r);
			setWindowPos_.invoke(null, hwnd, 0L, l, t, rect_.getField("right").getInt(r) - l, rect_.getField("bottom").getInt(r) - t, 0x0004 | 0x0010 | 0x0020);
		} else { // the border is calculated again, with the room above and below
			setWindowPos_.invoke(null, hwnd, 0L, 0, 0, 0, 0, 0x0001 | 0x0002 | 0x0004 | 0x0010 | 0x0020);
		}
	}

	/** called by Windows for each message of these windows (through SWT's Callback) */
	public long windowProc(long hwnd, long msg, long wParam, long lParam) {
		Entry e = entries_.get(hwnd);
		if (e == null)
			return 0; // cannot happen: only windows of the map use this
		try {
			if (msg == WM_NCDESTROY) {
				entries_.remove(hwnd);
				set_.invoke(null, hwnd, gwlpWndProc_, e.oldProc());
				return (Long)call_.invoke(null, e.oldProc(), hwnd, (int)msg, wParam, lParam);
			}
			if (e.ownerDraw() && msg == BM_SETSTYLE) // SWT makes a button the default one this way: it stays drawn by DBGL
				wParam = (wParam & ~BS_TYPEMASK) | BS_OWNERDRAW;
			if (e.comboHandle() != 0 && msg == WM_WINDOWPOSCHANGING && lParam != 0)
				middleOfCombo(e, lParam);
			long result = (Long)call_.invoke(null, e.oldProc(), hwnd, (int)msg, wParam, lParam);
			if (e.ownerDraw()) {
				// test 17: a button DBGL draws itself kept its look when it was enabled or disabled, until the mouse moved over it
				// ("Next: preview" in "Check profiles" stayed dark). Windows draws such a button anew only when told to.
				if ((msg == WM_ENABLE) || (msg == WM_SETTEXT) || (msg == BM_SETSTYLE)) {
					Control c = e.control();
					if ((c != null) && !c.isDisposed())
						c.getDisplay().asyncExec(() -> {
							if (!c.isDisposed())
								c.redraw();
						});
				}
				return result;
			}
			if (msg == WM_NCCALCSIZE && lParam != 0)
				center(e, lParam);
			else if (msg == WM_NCPAINT)
				paintBands(hwnd, e);
			else if (msg == WM_NCHITTEST && (result == HTNOWHERE || result == HTBORDER))
				return HTCLIENT; // a click above or below the text still goes into the field
			return result;
		} catch (ReflectiveOperationException | RuntimeException ex) {
			try {
				return (Long)call_.invoke(null, e.oldProc(), hwnd, (int)msg, wParam, lParam);
			} catch (ReflectiveOperationException | RuntimeException ex2) {
				return 0;
			}
		}
	}

	/**
	 * the field of a drop-down: Windows places it at the top of the drop-down; in the middle of it instead (WINDOWPOS: two
	 * handles, then x, y, cx, cy, flags)
	 */
	private void middleOfCombo(Entry e, long lParam) throws ReflectiveOperationException {
		int[] pos = new int[5];
		readInts_.invoke(null, pos, lParam + 16, 20);
		if ((pos[4] & 0x0002) != 0) // SWP_NOMOVE
			return;
		Object cr = rect_.getConstructor().newInstance();
		getClientRect_.invoke(null, e.comboHandle(), cr);
		int comboH = rect_.getField("bottom").getInt(cr) - rect_.getField("top").getInt(cr);
		int h = pos[3];
		if ((pos[4] & 0x0001) != 0) { // SWP_NOSIZE: the height as it is
			Object wr = rect_.getConstructor().newInstance();
			getWindowRect_.invoke(null, e.control() == null ? 0L: entriesKey(e), wr);
			h = rect_.getField("bottom").getInt(wr) - rect_.getField("top").getInt(wr);
		}
		int y = (comboH - h) / 2;
		if (h <= 0 || y < 0 || y == pos[1])
			return;
		writeInts_.invoke(null, lParam + 20, new int[] {y}, 4);
	}

	private long entriesKey(Entry e) {
		for (Map.Entry<Long, Entry> m: entries_.entrySet())
			if (m.getValue() == e)
				return m.getKey();
		return 0;
	}

	/** the client area (first RECT of the parameter) just as high as a line, in the middle */
	private void center(Entry e, long lParam) throws ReflectiveOperationException {
		int[] rc = new int[4]; // left, top, right, bottom
		readInts_.invoke(null, rc, lParam, 16);
		int extra = (rc[3] - rc[1]) - e.lineHeight();
		if (extra < 2 || e.lineHeight() <= 0)
			return;
		int top = extra / 2;
		rc[1] += top;
		rc[3] -= extra - top;
		writeInts_.invoke(null, lParam, rc, 16);
	}

	/** the room above and below the client area in the colour of the field (inside the border Windows drew) */
	private void paintBands(long hwnd, Entry e) throws ReflectiveOperationException {
		Object wr = rect_.getConstructor().newInstance(), cr = rect_.getConstructor().newInstance();
		getWindowRect_.invoke(null, hwnd, wr);
		getClientRect_.invoke(null, hwnd, cr);
		mapRect_.invoke(null, hwnd, 0L, cr, 2);
		int wl = rect_.getField("left").getInt(wr), wt = rect_.getField("top").getInt(wr), wrr = rect_.getField("right").getInt(wr), wb = rect_.getField("bottom").getInt(wr);
		int cl = rect_.getField("left").getInt(cr) - wl, ct = rect_.getField("top").getInt(cr) - wt, crr = rect_.getField("right").getInt(cr) - wl,
				cb = rect_.getField("bottom").getInt(cr) - wt;
		int w = wrr - wl, h = wb - wt;
		int border = Math.max(0, Math.min(cl, w - crr));
		org.eclipse.swt.graphics.RGB rgb = e.control().isDisposed() ? new org.eclipse.swt.graphics.RGB(0, 0, 0): e.control().getBackground().getRGB();
		long brush = (Long)createBrush_.invoke(null, rgb.red | (rgb.green << 8) | (rgb.blue << 16));
		long dc = (Long)getWindowDC_.invoke(null, hwnd);
		try {
			fill(dc, brush, border, border, w - border, ct);
			fill(dc, brush, border, cb, w - border, h - border);
			fill(dc, brush, border, ct, cl, cb); // left and right, if Windows left any room
			fill(dc, brush, crr, ct, w - border, cb);
		} finally {
			releaseDC_.invoke(null, hwnd, dc);
			deleteObject_.invoke(null, brush);
		}
	}

	private void fill(long dc, long brush, int left, int top, int right, int bottom) throws ReflectiveOperationException {
		if (right <= left || bottom <= top)
			return;
		Object r = rect_.getConstructor().newInstance();
		rect_.getField("left").setInt(r, left);
		rect_.getField("top").setInt(r, top);
		rect_.getField("right").setInt(r, right);
		rect_.getField("bottom").setInt(r, bottom);
		fillRect_.invoke(null, dc, r, brush);
	}
}
