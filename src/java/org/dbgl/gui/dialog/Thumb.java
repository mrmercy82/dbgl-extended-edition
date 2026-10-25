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
package org.dbgl.gui.dialog;

import java.io.File;

import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.service.ImageService;
import org.dbgl.service.SettingsService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;


public class Thumb extends BaseDialog<Object> {

	private static final int BUTTON_BORDER_WIDTH = 0;
	private static final int BUTTON_BORDER_HEIGHT = 24;

	private File[] files_;
	private int index_, mxh_;

	private Rectangle monitorClientArea_, butSize_, imgSize_;
	private Button imgButton_;
	private Image thumbImage_;

	public Thumb(Shell parent, File[] files, int index) {
		super(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		files_ = files;
		index_ = index;
		imgSize_ = new Rectangle(0, 0, 0, 0);
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.screenshot.title", new Object[] { files_[index_].getPath(), imgSize_.width, imgSize_.height });
	}

	@Override
	protected boolean prepare() {
		int maxOverlap = -1;
		for (Monitor monitor: display_.getMonitors()) {
			Rectangle intersection = monitor.getBounds().intersection(getParent().getBounds());
			int overlap = intersection.width * intersection.height;
			if (overlap > maxOverlap) {
				maxOverlap = overlap;
				monitorClientArea_ = monitor.getClientArea();
			}
		}

		return true;
	}

	@Override
	protected void onShellCreated() {
		shell_.setBounds(monitorClientArea_);
		shell_.setLayout(new GridLayout(3, true));

		imgButton_ = Button_.on(shell_).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1)).text().listen(closeShellAdapter).ctrl();
		imgButton_.addTraverseListener(e -> {
			if ((e.keyCode == SWT.ARROW_LEFT) || (e.keyCode == SWT.ARROW_RIGHT)) {
				nextPrev(e.keyCode == SWT.ARROW_RIGHT);
				e.doit = false;
			}
		});

		Button_.on(shell_).text().key("button.previousimage").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				nextPrev(false);
			}
		}).build();
		Button_.on(shell_).text().key("button.close").listen(closeShellAdapter).build();
		Button_.on(shell_).text().key("button.nextimage").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				nextPrev(true);
			}
		}).build();
	}

	@Override
	protected void onShellOpened() {
		butSize_ = imgButton_.getBounds();
		butSize_.x = SettingsService.getInstance().getIntValue("gui", "screenshotsmaxwidth");  
		butSize_.y = SettingsService.getInstance().getIntValue("gui", "screenshotsmaxheight");
		butSize_.width -= BUTTON_BORDER_WIDTH;
		butSize_.height -= BUTTON_BORDER_HEIGHT;
		mxh_ = SettingsService.getInstance().getIntValue("gui", "screenshotsmaxheight");

		adjustShellBounds();
	}

	@Override
	protected void onClose() {
		if (thumbImage_ != null && !thumbImage_.isDisposed())
			thumbImage_.dispose();
	}

	private void nextPrev(boolean next) {
		if (next) {
			index_++;
			if (index_ >= files_.length)
				index_ = 0;
		} else {
			index_--;
			if (index_ < 0)
				index_ = files_.length - 1;
		}

		adjustShellBounds();
	}

	private void adjustShellBounds() {
		if (thumbImage_ != null && !thumbImage_.isDisposed())
			thumbImage_.dispose();

		Image orgThumb = ImageService.getImage(display_, files_[index_].getPath());
		imgSize_ = orgThumb.getBounds();

		double factor = calcFactor(imgSize_.width, imgSize_.height, butSize_.x, butSize_.y, butSize_.width, butSize_.height, mxh_);
		thumbImage_ = ImageService.createScaledImage(display_, orgThumb, imgSize_.width, imgSize_.height, 
				(int)(imgSize_.width * factor), (int)(imgSize_.height * factor), false, null);

		orgThumb.dispose();
		
		imgButton_.setImage(thumbImage_);
		
		shell_.pack();
		Rectangle shellBounds = shell_.getBounds();
		shell_.setText(getDialogTitle());
		shell_.setLocation(monitorClientArea_.x + (monitorClientArea_.width - shellBounds.width) / 2, monitorClientArea_.y + (monitorClientArea_.height - shellBounds.height) / 2);
	}

	private static double calcFactor(int w, int h, int mx, int my, int bw, int bh, int mxh) {
		double factor = Math.min(Math.min((double)bw / w, (double)bh / h), mxh / 100d);
		if (w * factor > mx) factor = (double)mx / w;
		if (h * factor > my) factor = (double)my / h;
		return factor;
	}
}