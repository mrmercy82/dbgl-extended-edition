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

	static int zoom(Control c) {
		try {
			Method m = org.eclipse.swt.widgets.Widget.class.getDeclaredMethod("getZoom");
			m.setAccessible(true);
			int z = (Integer)m.invoke(c);
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
			if (e.ownerDraw())
				return result;
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
