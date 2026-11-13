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
import org.eclipse.swt.widgets.Shell;


/**
 * Alpha 48, Windows, dark mode: SWT draws the menu bar itself there (owner draw), but only marks an entry when its menu is
 * open. This listens to the window of DBGL before SWT does: it follows the mouse over the menu bar itself (WM_NCMOUSEMOVE, the
 * rectangles of the entries) and also takes the "hot" entry Windows reports (ODS_HOTLIGHT, which SWT ignores); that entry is
 * passed on to SWT as selected, so SWT draws it marked. Everything through reflection (the classes of SWT for Windows are not
 * there when DBGL is built elsewhere); if anything is missing, the menu bar stays as it was. What happens is written once per
 * start to menubar-check.txt in the data folder (for a report).
 */
public final class MenuBarHover {

	private static final int WM_DRAWITEM = 0x002B, WM_NCMOUSEMOVE = 0x00A0, WM_MOUSEMOVE = 0x0200, WM_NCMOUSELEAVE = 0x02A2;
	private static final int ODT_MENU = 1, ODS_SELECTED = 0x0001, ODS_HOTLIGHT = 0x0040, HTMENU = 5;

	private static Object callback_; // kept, so that its native part stays valid
	private static final StringBuilder diag_ = new StringBuilder();
	private static int draws_;
	private static java.io.File diagFile_;

	private final Class<?> os_, rect_;
	private final Method callWindowProc_, readInts_, writeInts_, getMenu_, getMenuItemCount_, getMenuItemRect_, getWindowRect_, drawMenuBar_, trackMouseEvent_;
	private final Class<?> track_;
	private long oldProc_;
	private int hotLeft_ = Integer.MIN_VALUE; // the left edge (window coordinates) of the entry under the mouse
	private boolean tracking_;

	private MenuBarHover() throws ReflectiveOperationException {
		os_ = Class.forName("org.eclipse.swt.internal.win32.OS");
		rect_ = Class.forName("org.eclipse.swt.internal.win32.RECT");
		track_ = Class.forName("org.eclipse.swt.internal.win32.TRACKMOUSEEVENT");
		callWindowProc_ = os_.getMethod("CallWindowProc", long.class, long.class, int.class, long.class, long.class);
		readInts_ = os_.getMethod("MoveMemory", int[].class, long.class, int.class);
		writeInts_ = os_.getMethod("MoveMemory", long.class, int[].class, int.class);
		getMenu_ = os_.getMethod("GetMenu", long.class);
		getMenuItemCount_ = os_.getMethod("GetMenuItemCount", long.class);
		getMenuItemRect_ = os_.getMethod("GetMenuItemRect", long.class, long.class, int.class, rect_);
		getWindowRect_ = os_.getMethod("GetWindowRect", long.class, rect_);
		drawMenuBar_ = os_.getMethod("DrawMenuBar", long.class);
		trackMouseEvent_ = os_.getMethod("TrackMouseEvent", track_);
	}

	public static void install(Shell shell) {
		diagFile_ = new java.io.File(org.dbgl.service.FileLocationService.getInstance().getDataDir(), "menubar-check.txt");
		try {
			Class<?> os = Class.forName("org.eclipse.swt.internal.win32.OS");
			long hwnd = org.eclipse.swt.widgets.Control.class.getField("handle").getLong(shell);
			int gwlpWndProc = os.getField("GWLP_WNDPROC").getInt(null);
			Method get = os.getMethod("GetWindowLongPtr", long.class, int.class);
			Method set = os.getMethod("SetWindowLongPtr", long.class, int.class, long.class);
			MenuBarHover hover = new MenuBarHover();
			Class<?> cb = Class.forName("org.eclipse.swt.internal.Callback");
			Object callback = cb.getConstructor(Object.class, String.class, int.class).newInstance(hover, "windowProc", 4);
			long address = (Long)cb.getMethod("getAddress").invoke(callback);
			if (address == 0)
				return;
			hover.oldProc_ = (Long)get.invoke(null, hwnd, gwlpWndProc);
			if (hover.oldProc_ == 0)
				return;
			callback_ = callback;
			set.invoke(null, hwnd, gwlpWndProc, address);
			note("installed on " + Long.toHexString(hwnd));
			shell.addListener(org.eclipse.swt.SWT.Dispose, e -> {
				try {
					set.invoke(null, hwnd, gwlpWndProc, hover.oldProc_); // back before the window goes
				} catch (ReflectiveOperationException | RuntimeException ex) {
					// the window goes anyway
				}
				writeDiag();
			});
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			note("not installed: " + e); // not on Windows, or another SWT: no marking
			writeDiag();
		}
	}

	private static void note(String line) {
		if (diag_.length() < 20000)
			diag_.append(java.time.LocalTime.now()).append(' ').append(line).append(System.lineSeparator());
	}

	private static void writeDiag() {
		try {
			if (diagFile_ != null)
				java.nio.file.Files.writeString(diagFile_.toPath(), "DBGL menu bar (dark mode)" + System.lineSeparator() + diag_);
		} catch (java.io.IOException | RuntimeException e) {
			// only a report
		}
	}

	/** called by Windows for each message of the window (through SWT's Callback) */
	public long windowProc(long hwnd, long msg, long wParam, long lParam) {
		try {
			if (msg == WM_NCMOUSEMOVE)
				follow(hwnd, wParam == HTMENU ? lParam: Long.MIN_VALUE);
			else if (msg == WM_NCMOUSELEAVE || msg == WM_MOUSEMOVE)
				follow(hwnd, Long.MIN_VALUE);
			else if (msg == WM_DRAWITEM && lParam != 0)
				mark(lParam);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// drawn as SWT would
		}
		try {
			return (Long)callWindowProc_.invoke(null, oldProc_, hwnd, (int)msg, wParam, lParam);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0; // cannot happen: the method was found when installing
		}
	}

	/** which entry is under the mouse (screen point in lParam, or none): when that changes, the menu bar is drawn again */
	private void follow(long hwnd, long lParam) throws ReflectiveOperationException {
		int hot = Integer.MIN_VALUE;
		if (lParam != Long.MIN_VALUE) {
			int x = (short)(lParam & 0xFFFF), y = (short)((lParam >> 16) & 0xFFFF);
			long menu = (Long)getMenu_.invoke(null, hwnd);
			int count = menu == 0 ? 0: (Integer)getMenuItemCount_.invoke(null, menu);
			Object wr = rect_.getConstructor().newInstance();
			getWindowRect_.invoke(null, hwnd, wr);
			int wl = rect_.getField("left").getInt(wr);
			Object r = rect_.getConstructor().newInstance();
			for (int i = 0; i < count; i++) {
				if (!(Boolean)getMenuItemRect_.invoke(null, hwnd, menu, i, r))
					continue;
				if (x >= rect_.getField("left").getInt(r) && x < rect_.getField("right").getInt(r) && y >= rect_.getField("top").getInt(r) && y < rect_.getField("bottom").getInt(r)) {
					hot = rect_.getField("left").getInt(r) - wl;
					break;
				}
			}
			if (!tracking_) { // to hear when the mouse leaves the menu bar
				Object t = track_.getConstructor().newInstance();
				track_.getField("cbSize").setInt(t, track_.getField("sizeof").getInt(null));
				track_.getField("dwFlags").setInt(t, 0x0002 | 0x0010); // TME_LEAVE | TME_NONCLIENT
				track_.getField("hwndTrack").setLong(t, hwnd);
				tracking_ = (Boolean)trackMouseEvent_.invoke(null, t);
			}
		} else {
			tracking_ = false;
		}
		if (hot != hotLeft_) {
			if (draws_ < 60)
				note("mouse over the entry at " + hot);
			hotLeft_ = hot;
			drawMenuBar_.invoke(null, hwnd);
		}
	}

	/** an entry of the menu bar being drawn: marked when the mouse is over it (ours) or Windows says so (hot) */
	private void mark(long lParam) throws ReflectiveOperationException {
		int[] head = new int[5]; // CtlType, CtlID, itemID, itemAction, itemState
		readInts_.invoke(null, head, lParam, 20);
		if (head[0] != ODT_MENU)
			return;
		int[] rc = new int[4]; // rcItem: after CtlType..itemState (20 bytes), padding, hwndItem and hDC (8 bytes each)
		readInts_.invoke(null, rc, lParam + 40, 16);
		if (draws_ < 60) {
			draws_++;
			note("WM_DRAWITEM item " + head[2] + " state " + Integer.toHexString(head[4]) + " left " + rc[0]);
			if (draws_ == 60)
				writeDiag();
		}
		boolean hot = (head[4] & ODS_HOTLIGHT) != 0 || (hotLeft_ != Integer.MIN_VALUE && Math.abs(rc[0] - hotLeft_) <= 1);
		if (hot && (head[4] & ODS_SELECTED) == 0)
			writeInts_.invoke(null, lParam + 16, new int[] {head[4] | ODS_SELECTED}, 4);
	}
}
