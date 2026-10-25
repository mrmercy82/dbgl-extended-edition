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

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.interfaces.ProgressNotifyable;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;
import org.dbgl.util.StringRelatedUtils;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;


public abstract class UIThread<T> extends Thread implements ProgressNotifyable {

	protected static final String PREFIX_OK = "  + ";
	protected static final String PREFIX_ERR = "  - ";

	protected static final ITextService text_ = TextService.getInstance();
	protected final StringBuilder messageLog_ = new StringBuilder();

	private final Text log_;
	private final ProgressBar progressBar_;
	private final Label status_;
	protected final Display display_;
	private final boolean extensiveLogging_;
	private List<T> objects_;

	private boolean everythingOk_ = true;

	protected UIThread(Text log, ProgressBar progressBar, Label status, boolean extensiveLogging) {
		log_ = log;
		progressBar_ = progressBar;
		status_ = status;
		display_ = log.getShell().getDisplay();
		extensiveLogging_ = extensiveLogging;
	}

	protected List<T> getObjects() {
		return objects_;
	}

	protected void setObjects(List<T> objects) {
		objects_ = objects;
		progressBar_.setMaximum(objects.size());
	}

	@Override
	public void run() {
		for (final T o: objects_) {
			try {

				if (extensiveLogging_)
					messageLog_.append(getTitle(o)).append(":").append(StringUtils.LF);

				String warnings = work(o);
				if (StringUtils.isNotEmpty(warnings)) {
					if (!extensiveLogging_)
						messageLog_.append(getTitle(o));
					messageLog_.append(PREFIX_ERR).append(warnings);
				}

			} catch (Exception e) {

				e.printStackTrace();
				if (!extensiveLogging_)
					messageLog_.append(getTitle(o));
				messageLog_.append(PREFIX_ERR).append(StringRelatedUtils.toString(e)).append(StringUtils.LF);
				everythingOk_ = false;

			}

			if (!display_.isDisposed()) {
				display_.syncExec(() -> {
					if (!log_.isDisposed() && !progressBar_.isDisposed() && !status_.isDisposed()) {
						if (messageLog_.length() > 0) {
							String additionalOutput = messageLog_.toString();
							log_.append(additionalOutput);
							System.out.print(additionalOutput);
							messageLog_.setLength(0);
						}
						incrProgress(1024); // advance 1 'object'
					}
				});
			}
		}

		try {
			preFinish();
		} catch (IOException e) {
			e.printStackTrace();
		}

		completeProgress();
	}

	public void displayTitle(String title) {
		if (!display_.isDisposed()) {
			display_.asyncExec(() -> {
				if (!status_.isDisposed()) {
					status_.setText(title);
					status_.pack();
				}
			});
		}
	}

	@Override
	public void setTotal(long total) {
		if (!display_.isDisposed()) {
			display_.asyncExec(() -> {
				if (!progressBar_.isDisposed())
					progressBar_.setMaximum((int)(total / 1024) + objects_.size());
			});
		}
	}

	@Override
	public void incrProgress(long progress) {
		if (!display_.isDisposed()) {
			display_.asyncExec(() -> {
				if (!progressBar_.isDisposed())
					progressBar_.setSelection(progressBar_.getSelection() + (int)(progress / 1024));
			});
		}
	}

	@Override
	public void setProgress(long progress) {
		if (!display_.isDisposed()) {
			display_.asyncExec(() -> {
				if (!progressBar_.isDisposed())
					progressBar_.setSelection((int)(progress / 1024));
			});
		}
	}

	private void completeProgress() {
		if (!display_.isDisposed()) {
			display_.asyncExec(() -> {
				if (!progressBar_.isDisposed())
					progressBar_.setSelection(progressBar_.getMaximum());
			});
		}
	}

	public boolean isEverythingOk() {
		return everythingOk_;
	}

	public abstract String getTitle(T obj);

	public abstract String work(T obj) throws IOException, SQLException;

	public void preFinish() throws IOException {
	}
}
