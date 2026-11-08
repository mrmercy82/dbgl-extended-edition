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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;


/**
 * A progress bar with a line of text above the images of a game database while they are loaded in the background: how many
 * are loaded, and when some could not be loaded, why (e.g. "HTTP 403, www.mobygames.com"). Its methods may be called from any
 * thread.
 */
public final class ImageLoadStatus {

	private static final Pattern HTTP_CODE = Pattern.compile("response code: (\\d{3})");

	private final ITextService text_ = TextService.getInstance();
	private final Display display_;
	private final Composite composite_;
	private final ProgressBar bar_;
	private final Label label_;
	private final int total_;
	private int loaded_;
	private int failed_;
	private String reason_;

	/** @param layoutData where it goes in its parent, e.g. across all columns */
	public ImageLoadStatus(Composite parent, int total, Object layoutData) {
		display_ = parent.getDisplay();
		total_ = total;
		composite_ = new Composite(parent, SWT.NONE);
		composite_.setLayoutData(layoutData);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 2;
		composite_.setLayout(layout);
		label_ = new Label(composite_, SWT.WRAP);
		label_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		bar_ = new ProgressBar(composite_, SWT.SMOOTH | SWT.HORIZONTAL);
		bar_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		bar_.setMaximum(Math.max(1, total));
		show();
	}

	/** an image was loaded */
	public void loaded() {
		update(null, null);
	}

	/** an image could not be loaded from 'url' */
	public void failed(Exception e, String url) {
		update(e, url);
	}

	private synchronized void count(Exception e, String url) {
		if (e == null) {
			loaded_++;
		} else {
			failed_++;
			if (reason_ == null)
				reason_ = reason(e, url);
		}
	}

	private void update(Exception e, String url) {
		count(e, url);
		if (!display_.isDisposed())
			display_.asyncExec(this::show);
	}

	private synchronized void show() {
		if (composite_.isDisposed())
			return;
		int done = loaded_ + failed_;
		bar_.setSelection(done);
		if (done < total_) {
			label_.setText(text_.get("dialog.profile.images.loading", new Object[] {done, total_}));
		} else if (failed_ == 0) {
			label_.setText(text_.get("dialog.profile.images.loaded", new Object[] {loaded_}));
			((GridData)bar_.getLayoutData()).exclude = true;
			bar_.setVisible(false);
		} else {
			String text = text_.get("dialog.profile.images.failed", new Object[] {loaded_, total_, failed_, reason_});
			// 403: the site lets browsers in only after a check (e.g. Cloudflare at MobyGames), not programs
			if (reason_.startsWith("HTTP 403"))
				text += "\n" + text_.get("dialog.profile.images.blocked");
			label_.setText(text);
			((GridData)bar_.getLayoutData()).exclude = true;
			bar_.setVisible(false);
		}
		composite_.getParent().layout(true, true);
	}

	/** short: the HTTP status and the host, or the kind of error */
	static String reason(Exception e, String url) {
		String host = "";
		try {
			host = java.net.URI.create(url).getHost();
		} catch (Exception ignore) {
			// no host then
		}
		String msg = String.valueOf(e.getMessage());
		Matcher m = HTTP_CODE.matcher(msg);
		String what = m.find() ? "HTTP " + m.group(1): (e instanceof java.io.FileNotFoundException ? "HTTP 404": e.getClass().getSimpleName());
		return (host == null) || host.isEmpty() ? what: what + ", " + host;
	}
}
