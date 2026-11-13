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

import org.dbgl.service.SettingsService;
import org.eclipse.swt.graphics.Color;


/**
 * the colours of the dark mode; alpha 48: neutral greys instead of blue-grey ones, as e.g. PCSX2: fields and buttons lighter than
 * the background, lists darker. Text on all of them has a contrast of at least 7:1 (WCAG AAA), dimmed text at least 4.5:1 (AA).
 */
public class DarkTheme {

	/** alpha 48: "high contrast" (theme 3): black, white text and frames, yellow focus, as the high contrast of Windows; read first */
	public static final boolean HIGH_CONTRAST = readHighContrast();

	/** the text colour of a ticked box (on the accent colour) */
	public static final Color checkForeground = pick(232, 232, 232, 0, 0, 0);

	public static final Color defaultForeground = pick(232, 232, 232, 255, 255, 255);

	public static final Color dialogBackground = pick(50, 50, 50, 0, 0, 0);
	/** lists, tables, trees and long texts (the profile list among them) */
	public static final Color inputBackground = pick(36, 36, 36, 0, 0, 0);
	/** text fields, drop-downs and spinners (alpha 48) */
	public static final Color fieldBackground = pick(70, 70, 70, 0, 0, 0);
	/** push buttons (alpha 48; where the system lets them be coloured) */
	public static final Color buttonBackground = pick(76, 76, 76, 0, 0, 0);
	/** dimmed text, e.g. hints (alpha 48): 4.5:1 on the background */
	public static final Color dimForeground = pick(165, 165, 165, 225, 225, 225);

	public static final Color toolbarBackground = pick(42, 42, 42, 0, 0, 0);

	public static final Color tableHeaderBackground = pick(58, 58, 58, 0, 0, 0);
	public static final Color tableHeaderForeground = pick(214, 214, 214, 255, 255, 255);
	public static final Color tableHighlightedBackground = pick(80, 80, 20, 70, 70, 0);

	public static final Color tabForeground = pick(187, 187, 187, 230, 230, 230);
	public static final Color tabSelectedForeground = pick(247, 248, 248, 255, 255, 0);

	public static final Color linkForeground = pick(111, 197, 238, 255, 255, 0);

	public static final Color conflictingForeground = pick(138, 201, 242, 0, 255, 255);
	public static final Color changedForeground = pick(108, 210, 17, 0, 255, 0);

	/** frames of groups, boxes and buttons drawn by DBGL on Windows (alpha 48): subtle, but with 3:1 where they matter */
	public static final Color frameForeground = pick(78, 78, 78, 255, 255, 255);
	public static final Color boxBorder = pick(140, 140, 140, 255, 255, 255);
	public static final Color accentBackground = pick(0, 95, 184, 26, 235, 255);
	public static final Color focusForeground = pick(110, 170, 240, 255, 255, 0);
	public static final Color buttonHoverBackground = pick(90, 90, 90, 45, 45, 45);
	public static final Color buttonPressedBackground = pick(62, 62, 62, 80, 80, 80);
	public static final Color disabledForeground = pick(125, 125, 125, 63, 242, 63);

	/** setData key of controls whose text colour is chosen on purpose (it stays, also with little contrast) */
	public static final String KEEP_FOREGROUND = "dbgl.keepforeground";
	private static final String PAINTED = "dbgl.darkpainted";

	/** fields made by the builders get the colour of lists first; apply() gives them their own */
	private static final org.eclipse.swt.graphics.RGB OLD_INPUT = new org.eclipse.swt.graphics.RGB(36, 36, 36);

	private DarkTheme() {
	}

	private static boolean readHighContrast() {
		try {
			return SettingsService.getInstance().getIntValue("gui", "theme") == 3;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static Color pick(int r, int g, int b, int hr, int hg, int hb) {
		return HIGH_CONTRAST ? new Color(hr, hg, hb): new Color(r, g, b);
	}

	/**
	 * alpha 48: in the dark mode, the controls below that still have the light colours of the system (made without the builders of
	 * DBGL, e.g. pages that scroll): the background of their parent, light text, dark fields. Colours set on purpose stay.
	 */
	public static void apply(org.eclipse.swt.widgets.Control root) {
		if (!forced() || (root == null) || root.isDisposed())
			return;
		org.eclipse.swt.widgets.Display d = root.getDisplay();
		org.eclipse.swt.graphics.RGB widgetBg = d.getSystemColor(org.eclipse.swt.SWT.COLOR_WIDGET_BACKGROUND).getRGB();
		org.eclipse.swt.graphics.RGB widgetFg = d.getSystemColor(org.eclipse.swt.SWT.COLOR_WIDGET_FOREGROUND).getRGB();
		org.eclipse.swt.graphics.RGB listBg = d.getSystemColor(org.eclipse.swt.SWT.COLOR_LIST_BACKGROUND).getRGB();
		org.eclipse.swt.graphics.RGB listFg = d.getSystemColor(org.eclipse.swt.SWT.COLOR_LIST_FOREGROUND).getRGB();
		java.util.ArrayDeque<org.eclipse.swt.widgets.Control> todo = new java.util.ArrayDeque<>();
		todo.add(root);
		while (!todo.isEmpty()) {
			org.eclipse.swt.widgets.Control c = todo.poll();
			if (c.isDisposed())
				continue;
			org.eclipse.swt.graphics.RGB bg = c.getBackground().getRGB(), fg = c.getForeground().getRGB();
			boolean list = (c instanceof org.eclipse.swt.widgets.List) || (c instanceof org.eclipse.swt.widgets.Table) || (c instanceof org.eclipse.swt.widgets.Tree)
					|| (c instanceof org.eclipse.swt.custom.StyledText) || ((c instanceof org.eclipse.swt.widgets.Text) && ((c.getStyle() & org.eclipse.swt.SWT.MULTI) != 0));
			boolean field = list || (c instanceof org.eclipse.swt.widgets.Text) || (c instanceof org.eclipse.swt.widgets.Combo) || (c instanceof org.eclipse.swt.custom.CCombo)
					|| (c instanceof org.eclipse.swt.widgets.Spinner);
			boolean pushButton = (c instanceof org.eclipse.swt.widgets.Button b) && ((b.getStyle() & (org.eclipse.swt.SWT.CHECK | org.eclipse.swt.SWT.RADIO)) == 0);
			if (field) {
				if (bg.equals(listBg) || bg.equals(widgetBg) || bg.equals(OLD_INPUT))
					c.setBackground(list ? inputBackground: fieldBackground);
				if (fg.equals(listFg) || fg.equals(widgetFg))
					c.setForeground(defaultForeground);
			} else if (pushButton && org.dbgl.util.SystemUtils.IS_WINDOWS) {
				if (bg.equals(widgetBg) || bg.equals(c.getParent().getBackground().getRGB()))
					c.setBackground(buttonBackground);
			} else if (c != root && !pushButton && (c.getParent() != null) && bg.equals(widgetBg)) {
				c.setBackground(c.getParent().getBackground());
			}
			// push buttons: on Windows dark (explorer theme), with light text as from Button_; elsewhere as the system draws them
			if (!field && (!pushButton || org.dbgl.util.SystemUtils.IS_WINDOWS) && (fg.equals(widgetFg) || fg.equals(listFg)))
				c.setForeground(defaultForeground);
			else if (!field && !Boolean.TRUE.equals(c.getData(KEEP_FOREGROUND)))
				readable(c); // e.g. dark red warnings: lighter, so that they can be read on grey
			if ((org.dbgl.util.SystemUtils.IS_WINDOWS || Boolean.getBoolean("dbgl.ownframes")) && ownFrames())
				paintOwnFrames(c);
			if (c instanceof org.eclipse.swt.widgets.Composite comp)
				for (org.eclipse.swt.widgets.Control child: comp.getChildren())
					todo.add(child);
		}
	}

	/** a text colour with less than 4.5:1 against its background (WCAG AA) is made lighter, keeping its hue */
	private static void readable(org.eclipse.swt.widgets.Control c) {
		org.eclipse.swt.graphics.RGB fg = c.getForeground().getRGB(), bg = c.getBackground().getRGB();
		if (contrast(fg, bg) >= 4.5)
			return;
		for (int step = 1; step <= 20; step++) {
			double t = step / 20.0;
			org.eclipse.swt.graphics.RGB lighter = new org.eclipse.swt.graphics.RGB((int)Math.round(fg.red + (255 - fg.red) * t), (int)Math.round(fg.green + (255 - fg.green) * t),
					(int)Math.round(fg.blue + (255 - fg.blue) * t));
			if (contrast(lighter, bg) >= 4.5) {
				c.setForeground(new Color(lighter));
				return;
			}
		}
	}

	/** the contrast ratio of WCAG 2 (1 to 21) */
	public static double contrast(org.eclipse.swt.graphics.RGB a, org.eclipse.swt.graphics.RGB b) {
		double la = luminance(a), lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double luminance(org.eclipse.swt.graphics.RGB c) {
		return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue);
	}

	private static double channel(int v) {
		double x = v / 255.0;
		return x <= 0.03928 ? x / 12.92: Math.pow((x + 0.055) / 1.055, 2.4);
	}

	/**
	 * alpha 48, Windows: frames of groups, check boxes, radio buttons, push buttons and drop-downs are drawn by Windows in light
	 * colours that SWT cannot change. SWT lets a control paint over what Windows drew (paint event after the native painting), so
	 * DBGL draws them again: subtle frames, dark boxes, flat buttons with a clearly visible focus. In the light mode nothing of this
	 * happens.
	 */
	private static void paintOwnFrames(org.eclipse.swt.widgets.Control c) {
		if (c.getData(PAINTED) != null)
			return;
		if (c instanceof org.eclipse.swt.widgets.Group g) {
			c.setData(PAINTED, Boolean.TRUE);
			g.addListener(org.eclipse.swt.SWT.Paint, e -> safely(() -> paintGroup(g, e.gc)));
		} else if (c instanceof org.eclipse.swt.widgets.Button b) {
			int style = b.getStyle();
			if ((style & org.eclipse.swt.SWT.ARROW) != 0)
				return;
			c.setData(PAINTED, Boolean.TRUE);
			int[] state = new int[1]; // 1: mouse over, 2: pressed
			b.addListener(org.eclipse.swt.SWT.MouseEnter, e -> redraw(b, state, state[0] | 1));
			b.addListener(org.eclipse.swt.SWT.MouseExit, e -> redraw(b, state, 0));
			b.addListener(org.eclipse.swt.SWT.MouseDown, e -> redraw(b, state, state[0] | 2));
			b.addListener(org.eclipse.swt.SWT.MouseUp, e -> redraw(b, state, state[0] & 1));
			// (a button that closes its window is disposed by then: test 12 froze on OK of several dialogs)
			b.addListener(org.eclipse.swt.SWT.FocusIn, e -> redrawIfAlive(b));
			b.addListener(org.eclipse.swt.SWT.FocusOut, e -> redrawIfAlive(b));
			b.addListener(org.eclipse.swt.SWT.Selection, e -> redrawIfAlive(b));
			if ((style & (org.eclipse.swt.SWT.CHECK | org.eclipse.swt.SWT.RADIO)) != 0)
				b.addListener(org.eclipse.swt.SWT.Paint, e -> safely(() -> paintBox(b, e.gc, (style & org.eclipse.swt.SWT.RADIO) != 0)));
			else {
				b.addListener(org.eclipse.swt.SWT.Paint, e -> safely(() -> paintButton(b, e.gc, state[0])));
				if (org.dbgl.util.SystemUtils.IS_WINDOWS && (style & org.eclipse.swt.SWT.TOGGLE) == 0)
					WinSubclass.ownerDraw(b); // Windows no longer draws it first: no flicker when the mouse comes
			}
		} else if (c instanceof org.eclipse.swt.widgets.Combo combo) {
			c.setData(PAINTED, Boolean.TRUE);
			combo.addListener(org.eclipse.swt.SWT.Paint, e -> safely(() -> {
				org.eclipse.swt.graphics.Point size = combo.getSize();
				e.gc.setForeground(combo.getParent().getBackground());
				e.gc.drawRectangle(0, 0, size.x - 1, size.y - 1);
				e.gc.setForeground(combo.isFocusControl() ? focusForeground: frameForeground);
				e.gc.drawRectangle(1, 1, size.x - 3, size.y - 3);
			}));
			combo.addListener(org.eclipse.swt.SWT.FocusIn, e -> redrawIfAlive(combo));
			combo.addListener(org.eclipse.swt.SWT.FocusOut, e -> redrawIfAlive(combo));
			if ((combo.getStyle() & org.eclipse.swt.SWT.READ_ONLY) == 0)
				centerText(combo);
		} else if (c instanceof org.eclipse.swt.widgets.Text t && (t.getStyle() & org.eclipse.swt.SWT.MULTI) == 0 && (t.getStyle() & org.eclipse.swt.SWT.BORDER) != 0) {
			c.setData(PAINTED, Boolean.TRUE);
			centerText(t);
		}
	}

	private static void redrawIfAlive(org.eclipse.swt.widgets.Control c) {
		if (!c.isDisposed())
			c.redraw();
	}

	/** drawing must never break DBGL: whatever goes wrong there, the control just keeps the look Windows gave it */
	private static void safely(Runnable paint) {
		try {
			paint.run();
		} catch (RuntimeException e) {
			// the native look then
		}
	}

	/** Windows: the text of a one-line field in the middle (see WinSubclass) */
	private static void centerText(org.eclipse.swt.widgets.Control c) {
		if (org.dbgl.util.SystemUtils.IS_WINDOWS)
			WinSubclass.centerText(c);
	}

	private static void redraw(org.eclipse.swt.widgets.Button b, int[] state, int newState) {
		if (state[0] != newState) {
			state[0] = newState;
			redrawIfAlive(b);
		}
	}

	private static void paintGroup(org.eclipse.swt.widgets.Group g, org.eclipse.swt.graphics.GC gc) {
		org.eclipse.swt.graphics.Point size = g.getSize();
		Color bg = g.getBackground();
		String text = g.getText();
		gc.setFont(g.getFont());
		int th = gc.getFontMetrics().getHeight();
		int top = th / 2;
		// away with the light lines of Windows (the children are not painted over: they lie in front)
		gc.setBackground(bg);
		gc.fillRectangle(0, 0, size.x, th + 1);
		gc.fillRectangle(0, 0, 3, size.y);
		gc.fillRectangle(size.x - 3, 0, 3, size.y);
		gc.fillRectangle(0, size.y - 3, size.x, 3);
		gc.setAntialias(org.eclipse.swt.SWT.ON);
		gc.setForeground(frameForeground);
		gc.drawRoundRectangle(0, top, size.x - 1, size.y - top - 1, 8, 8);
		if (text != null && !text.isEmpty()) {
			org.eclipse.swt.graphics.Point extent = gc.textExtent(text, org.eclipse.swt.SWT.DRAW_MNEMONIC);
			gc.fillRectangle(6, 0, extent.x + 6, th);
			gc.setForeground(g.isEnabled() ? g.getForeground(): disabledForeground);
			gc.drawText(text, 9, 0, org.eclipse.swt.SWT.DRAW_MNEMONIC | org.eclipse.swt.SWT.DRAW_TRANSPARENT);
		}
	}

	private static void paintBox(org.eclipse.swt.widgets.Button b, org.eclipse.swt.graphics.GC gc, boolean radio) {
		org.eclipse.swt.graphics.Point size = b.getSize();
		int box = glyphSize(b);
		int y = Math.max(0, (size.y - box) / 2);
		boolean on = b.getSelection(), enabled = b.isEnabled();
		gc.setAntialias(org.eclipse.swt.SWT.ON);
		gc.setBackground(b.getBackground());
		gc.fillRectangle(0, Math.max(0, y - 1), box + 2, box + 2);
		int r = Math.max(3, box / 3);
		gc.setBackground(on && enabled ? accentBackground: inputBackground);
		gc.setForeground(on && enabled ? accentBackground: (enabled ? boxBorder: frameForeground));
		if (radio) {
			gc.fillOval(0, y, box, box);
			gc.drawOval(0, y, box - 1, box - 1);
			if (on) {
				gc.setBackground(enabled ? checkForeground: disabledForeground);
				gc.fillOval(box * 4 / 13, y + box * 4 / 13, box - 2 * (box * 4 / 13), box - 2 * (box * 4 / 13));
			}
		} else {
			gc.fillRoundRectangle(0, y, box, box, r, r);
			gc.drawRoundRectangle(0, y, box - 1, box - 1, r, r);
			if (on) {
				gc.setForeground(enabled ? checkForeground: disabledForeground);
				gc.setLineWidth(Math.max(2, box / 7));
				gc.drawPolyline(new int[] {box * 3 / 13, y + box * 7 / 13, box * 5 / 13, y + box * 9 / 13, box * 10 / 13, y + box * 4 / 13});
				gc.setLineWidth(1);
			}
		}
		if (b.isFocusControl()) {
			gc.setForeground(focusForeground);
			if (radio)
				gc.drawOval(0, Math.max(0, y - 1), box + 1, box + 1);
			else
				gc.drawRoundRectangle(0, Math.max(0, y - 1), box + 1, box + 1, r + 1, r + 1);
		}
	}

	/**
	 * the size of the box Windows draws, in the units of SWT: Windows says it in pixels (Button.checkWidth), SWT scales by the zoom
	 * of the monitor; 13 when that cannot be read
	 */
	private static int glyphSize(org.eclipse.swt.widgets.Button b) {
		try {
			java.lang.reflect.Field f = org.eclipse.swt.widgets.Button.class.getDeclaredField("checkWidth");
			f.setAccessible(true);
			int px = f.getInt(b);
			java.lang.reflect.Method m = org.eclipse.swt.widgets.Widget.class.getDeclaredMethod("getZoom");
			m.setAccessible(true);
			int zoom = (Integer)m.invoke(b);
			if (px > 0 && zoom > 0)
				return Math.max(11, Math.min(32, Math.round(px * 100f / zoom)));
		} catch (ReflectiveOperationException | RuntimeException e) {
			// another SWT
		}
		return 13;
	}

	private static void paintButton(org.eclipse.swt.widgets.Button b, org.eclipse.swt.graphics.GC gc, int state) {
		org.eclipse.swt.graphics.Point size = b.getSize();
		boolean enabled = b.isEnabled();
		boolean toggled = ((b.getStyle() & org.eclipse.swt.SWT.TOGGLE) != 0) && b.getSelection();
		Color fill = !enabled ? dialogBackground: (state & 2) != 0 ? buttonPressedBackground: toggled ? accentBackground: (state & 1) != 0 ? buttonHoverBackground: buttonBackground;
		gc.setAntialias(org.eclipse.swt.SWT.ON);
		gc.setBackground(b.getParent().getBackground());
		gc.fillRectangle(0, 0, size.x, size.y);
		gc.setBackground(fill);
		gc.fillRoundRectangle(1, 1, size.x - 2, size.y - 2, 6, 6);
		boolean isDefault = b.getShell().getDefaultButton() == b;
		gc.setForeground(b.isFocusControl() ? focusForeground: isDefault && enabled ? boxBorder: frameForeground);
		gc.drawRoundRectangle(1, 1, size.x - 3, size.y - 3, 6, 6);
		if (b.isFocusControl()) // a second line: easy to see where the keyboard is
			gc.drawRoundRectangle(2, 2, size.x - 5, size.y - 5, 5, 5);
		String text = b.getText();
		org.eclipse.swt.graphics.Image img = b.getImage();
		gc.setFont(b.getFont());
		org.eclipse.swt.graphics.Point te = (text == null || text.isEmpty()) ? new org.eclipse.swt.graphics.Point(0, 0): gc.textExtent(text, org.eclipse.swt.SWT.DRAW_MNEMONIC);
		int iw = 0, ih = 0;
		if (img != null) {
			org.eclipse.swt.graphics.Rectangle ib = img.getBounds();
			iw = ib.width;
			ih = ib.height;
		}
		int gap = (iw > 0 && te.x > 0) ? 4: 0;
		int x = Math.max(4, (size.x - iw - gap - te.x) / 2);
		if (img != null)
			gc.drawImage(img, x, (size.y - ih) / 2);
		if (te.x > 0) {
			gc.setForeground(enabled ? defaultForeground: disabledForeground);
			gc.drawText(text, x + iw + gap, (size.y - te.y) / 2, org.eclipse.swt.SWT.DRAW_MNEMONIC | org.eclipse.swt.SWT.DRAW_TRANSPARENT);
		}
	}

	private static Boolean dark_, ownFrames_;

	private static boolean ownFrames() {
		if (ownFrames_ == null)
			ownFrames_ = SettingsService.getInstance().getBooleanValue("gui", "darkownframes");
		return ownFrames_;
	}

	/** whether DBGL is dark: chosen (1), or as the system (2, alpha 48); fixed for the whole run, changing it starts DBGL again */
	public static boolean forced() {
		if (dark_ == null)
			dark_ = isDark(SettingsService.getInstance().getIntValue("gui", "theme"));
		return dark_;
	}

	public static boolean isDark(int theme) {
		if (theme == 2) {
			try {
				return org.eclipse.swt.widgets.Display.isSystemDarkTheme();
			} catch (RuntimeException | LinkageError e) {
				return false;
			}
		}
		return theme == 1 || theme == 3; // 3: high contrast, a dark mode too
	}
}
