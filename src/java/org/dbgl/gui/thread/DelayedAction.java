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
package org.dbgl.gui.thread;

import java.util.function.Consumer;

import org.eclipse.swt.widgets.Display;

public class DelayedAction extends Thread {

	private Display display_;
	private Consumer<Boolean> consumer_;
	private boolean b_;
	private int delay_;
	private boolean restart_;
	
	public DelayedAction(Display display, Consumer<Boolean> consumer, boolean b, int delay) {
		display_ = display;
		consumer_ = consumer;
		b_ = b;
		delay_ = delay;
	}
	
	public void restart() {
		restart_ = true;
		interrupt();
	}
	
	@Override
	public void run() {
		do {
			restart_ = false;
			try {
				synchronized(this) {
					if (delay_ > 0)
						wait(delay_);
				}	
			} catch (InterruptedException e) {
				// do nothing
			}
		} while (restart_);
		
		if (!display_.isDisposed()) {
			display_.asyncExec(() -> {
				consumer_.accept(b_);
			});
		}
	}
}