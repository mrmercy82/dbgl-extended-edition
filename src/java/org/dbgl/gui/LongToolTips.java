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
package org.dbgl.gui;

import java.util.function.Function;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;


/**
 * Tooltips that stay visible as long as the mouse is over the control (the tooltips of the operating system disappear after a
 * few seconds, too early for the long help texts of DOSBox). The text of a control's tooltip is taken over when the mouse
 * enters it; controls whose tooltip depends on the mouse position (e.g. a table row) register a provider.
 */
public final class LongToolTips {

	private static final String TEXT_KEY = "dbgl.longtooltip";
	private static final String PROVIDER_KEY = "dbgl.longtooltip.provider";
	private static final int MAX_WIDTH = 640;

	private static Shell tip_;
	private static Control owner_;
	private static String shownText_;
	private static boolean installed_;
	private static volatile boolean enabled_ = true;

	private LongToolTips() {
	}

	/** @return the tooltip text of a control, also when it was taken over by this class */
	public static String textOf(Control control) {
		Object text = control.getData(TEXT_KEY);
		return text instanceof String s ? s: control.getToolTipText();
	}

	/** a tooltip that depends on where the mouse is in the control (null: no tooltip there) */
	public static void setProvider(Control control, Function<Point, String> provider) {
		control.setData(PROVIDER_KEY, provider);
	}

	/**
	 * false: the normal tooltips of the operating system again; tooltips that depend on the mouse position (profile list) are
	 * still shown by this class
	 */
	public static void setEnabled(boolean enabled) {
		enabled_ = enabled;
	}

	public static synchronized void install(Display display) {
		if (installed_)
			return;
		installed_ = true;
		try {
			enabled_ = org.dbgl.service.SettingsService.getInstance().getBooleanValue("gui", "longtooltips");
		} catch (RuntimeException e) {
			enabled_ = true;
		}
		display.addFilter(SWT.MouseEnter, e -> {
			if (!enabled_) {
				if ((e.widget instanceof Control c) && (c.getData(TEXT_KEY) instanceof String text)) {
					c.setData(TEXT_KEY, null);
					c.setToolTipText(text); // give the text back to the operating system
				}
				return;
			}
			if ((e.widget instanceof Control c) && !(c instanceof Shell)) {
				String text = c.getToolTipText();
				if (text != null) {
					c.setData(TEXT_KEY, text);
					c.setToolTipText(null);
				}
			}
		});
		display.addFilter(SWT.MouseHover, LongToolTips::onHover);
		display.addFilter(SWT.MouseMove, e -> {
			if ((tip_ != null) && (e.widget == owner_) && (owner_.getData(PROVIDER_KEY) != null) && !java.util.Objects.equals(textAt(owner_, e), shownText_))
				hide();
		});
		for (int type: new int[] {SWT.MouseExit, SWT.MouseDown, SWT.KeyDown, SWT.MouseWheel, SWT.Deactivate})
			display.addFilter(type, e -> {
				if ((type != SWT.MouseExit) || (e.widget == owner_))
					hide();
			});
	}

	@SuppressWarnings("unchecked")
	private static String textAt(Control control, Event e) {
		Object provider = control.getData(PROVIDER_KEY);
		if (provider instanceof Function<?, ?> f)
			return ((Function<Point, String>)f).apply(new Point(e.x, e.y));
		return textOf(control);
	}

	private static void onHover(Event e) {
		if (!(e.widget instanceof Control control) || control.isDisposed() || (control instanceof Shell))
			return;
		if (!enabled_ && (control.getData(PROVIDER_KEY) == null))
			return;
		String text = textAt(control, e);
		if ((text == null) || text.isBlank())
			return;
		if ((tip_ != null) && (owner_ == control) && text.equals(shownText_))
			return;
		hide();
		Display display = control.getDisplay();
		tip_ = new Shell(control.getShell(), SWT.ON_TOP | SWT.NO_FOCUS | SWT.TOOL);
		tip_.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
		FillLayout layout = new FillLayout();
		layout.marginWidth = 4;
		layout.marginHeight = 3;
		tip_.setLayout(layout);
		Label label = new Label(tip_, SWT.WRAP);
		label.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
		label.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
		label.setText(text.replace("&", "&&"));
		Point natural = tip_.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		Point size = natural.x > MAX_WIDTH ? tip_.computeSize(MAX_WIDTH, SWT.DEFAULT): natural;
		Point pt = control.toDisplay(e.x, e.y + 20);
		Monitor monitor = control.getMonitor();
		Rectangle area = monitor == null ? display.getClientArea(): monitor.getClientArea();
		int x = Math.max(area.x, Math.min(pt.x, area.x + area.width - size.x));
		int y = pt.y + size.y > area.y + area.height ? control.toDisplay(e.x, e.y).y - size.y - 4: pt.y;
		tip_.setBounds(x, Math.max(area.y, y), size.x, size.y);
		tip_.setVisible(true);
		owner_ = control;
		shownText_ = text;
	}

	private static void hide() {
		if ((tip_ != null) && !tip_.isDisposed())
			tip_.dispose();
		tip_ = null;
		owner_ = null;
		shownText_ = null;
	}
}
