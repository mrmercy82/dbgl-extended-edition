/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-07. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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
package org.dbgl.gui.abstractdialog;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.service.SettingsService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Shell;


public abstract class SizeControlledDialog<T> extends BaseDialog<T> {

	private final String prefix_;
	private boolean listenerEnabled_;
	/** when the window was shown: moves right after that come from the system, not from the user */
	private long shownAt_ = Long.MAX_VALUE;
	private static final long SETTLE_MILLIS = 1000;

	protected final SettingsService settings_;

	protected SizeControlledDialog(Shell parent, int shellStyle, String dialogName) {
		super(parent, shellStyle);

		prefix_ = shellStyle_ == SWT.PRIMARY_MODAL ? StringUtils.EMPTY: dialogName + '_';
		listenerEnabled_ = true;

		settings_ = SettingsService.getInstance();
	}

	protected SizeControlledDialog(Shell parent, String dialogName) {
		this(parent, SWT.TITLE | SWT.CLOSE | SWT.BORDER | SWT.RESIZE | SWT.APPLICATION_MODAL, dialogName);
	}

	@Override
	protected void onShellInit() {
		if ((shellStyle_ == SWT.PRIMARY_MODAL) && settings_.getBooleanValue("gui", "maximized")) {
			shell_.setMaximized(true);
		} else {
			// the main window where it was; other windows are placed in placeShell, when their size is known
			if (isMain() && settings_.hasValue("gui", prefix_ + "x") && settings_.hasValue("gui", prefix_ + "y")) {
				shell_.setLocation(settings_.getIntValue("gui", prefix_ + "x"), settings_.getIntValue("gui", prefix_ + "y"));
			}
			// a window without a size of its own yet (e.g. a new one) opened tiny, almost like minimized
			int width = settings_.hasValue("gui", prefix_ + "width") ? settings_.getIntValue("gui", prefix_ + "width"): 0;
			int height = settings_.hasValue("gui", prefix_ + "height") ? settings_.getIntValue("gui", prefix_ + "height"): 0;
			shell_.setSize(width < 200 ? 800: width, height < 150 ? 600: height);
		}

		shell_.addListener(SWT.Show, e -> shownAt_ = System.currentTimeMillis());
		shell_.addControlListener(new ControlAdapter() {
			@Override
			public void controlMoved(ControlEvent event) {
				if (!shell_.getMaximized()) {
					Rectangle rec = shell_.getBounds();
					if (isMain()) {
						settings_.setIntValue("gui", prefix_ + "x", rec.x);
						settings_.setIntValue("gui", prefix_ + "y", rec.y);
					} else if (movedByUser()) {
						rememberPosition(rec);
					}
				}
			}
		});
		
		shell_.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent event) {
				if (listenerEnabled_) {
					boolean isMaximized = shell_.getMaximized();
					if (!isMaximized) {
						Rectangle rec = shell_.getBounds();
						settings_.setIntValue("gui", prefix_ + "width", rec.width);
						settings_.setIntValue("gui", prefix_ + "height", rec.height);
						if (isMain()) {
							settings_.setIntValue("gui", prefix_ + "x", rec.x);
							settings_.setIntValue("gui", prefix_ + "y", rec.y);
						} else if (movedByUser()) {
							rememberPosition(rec); // e.g. made larger at the left edge
						}
					}
					if (shellStyle_ == SWT.PRIMARY_MODAL)
						settings_.setBooleanValue("gui", "maximized", isMaximized);
				}
			}
		});
	}

	private boolean isMain() {
		return shellStyle_ == SWT.PRIMARY_MODAL;
	}

	private boolean movedByUser() {
		return listenerEnabled_ && shell_.isVisible() && (System.currentTimeMillis() - shownAt_ > SETTLE_MILLIS);
	}

	/** only a place the user chose is kept (own keys: the places kept before also came from the system) */
	private void rememberPosition(Rectangle rec) {
		settings_.setIntValue("gui", prefix_ + "left", rec.x);
		settings_.setIntValue("gui", prefix_ + "top", rec.y);
	}

	/** where the user put this window last, if that is still on a screen; else in the middle of the window it belongs to */
	@Override
	protected void placeShell() {
		if (isMain())
			return;
		if (settings_.hasValue("gui", prefix_ + "left") && settings_.hasValue("gui", prefix_ + "top")) {
			org.eclipse.swt.graphics.Point size = shell_.getSize();
			Rectangle saved = new Rectangle(settings_.getIntValue("gui", prefix_ + "left"), settings_.getIntValue("gui", prefix_ + "top"), size.x, size.y);
			// at least a part of the title bar must be on one of the screens, the monitors may have changed
			Rectangle titleBar = new Rectangle(saved.x + 40, saved.y, Math.max(1, saved.width - 80), 20);
			for (org.eclipse.swt.widgets.Monitor m: shell_.getDisplay().getMonitors()) {
				if (m.getClientArea().intersects(titleBar)) {
					shell_.setLocation(saved.x, saved.y);
					return;
				}
			}
		}
		super.placeShell();
	}

	public void setListenerEnabled(boolean listenerEnabled) {
		listenerEnabled_ = listenerEnabled;
	}
}
