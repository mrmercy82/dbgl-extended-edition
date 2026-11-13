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
package org.dbgl.gui.controls;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.service.TextService;
import org.dbgl.util.StringRelatedUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;


public class Mess_ {

	private Mess_() {
	}

	public static Builder on(Shell shell) {
		return new Builder(shell);
	}

	public static final class Builder {
		private Shell shell_;
		private int style_ = SWT.APPLICATION_MODAL | SWT.ICON_INFORMATION | SWT.OK;
		private PrintStream printStream_ = System.out;
		private String title_ = TextService.getInstance().get("general.information");
		private List<String> messages_ = new ArrayList<>();
		private Exception exception_;
		private Control control_;
		private CTabItem tabItem_;
		private TreeItem treeItem_;

		public Builder(Shell shell) {
			shell_ = shell;
		}

		public Builder txt(String txt) {
			messages_.add(txt);
			return this;
		}

		public Builder key(String key) {
			return txt(TextService.getInstance().get(key));
		}

		public Builder key(String key, String param) {
			return txt(TextService.getInstance().get(key, param));
		}

		public Builder key(String key, Object[] objs) {
			return txt(TextService.getInstance().get(key, objs));
		}

		public Builder exception(Exception e) {
			exception_ = e;
			return this;
		}

		public Builder bind(Control control) {
			if (control_ == null)
				control_ = control;
			return this;
		}

		public Builder bind(Control control, CTabItem tabItem) {
			if (control_ == null) {
				control_ = control;
				tabItem_ = tabItem;
			}
			return this;
		}
		
		public Builder bind(Control control, TreeItem treeItem) {
			if (control_ == null) {
				control_ = control;
				treeItem_ = treeItem;
			}
			return this;
		}

		public int display() {
			String msg = StringRelatedUtils.stringArrayToString(messages_.toArray(new String[messages_.size()]), StringUtils.LF);
			if (StringUtils.isBlank(msg) && exception_ != null)
				msg += StringRelatedUtils.toString(exception_);

			printStream_.println(title_ + ": " + msg);
			if (exception_ != null)
				exception_.printStackTrace(printStream_);

			if (shell_ != null && DarkTheme.forced())
				return darkBox(msg); // alpha 48: the message box of the system stays light
			if (shell_ != null) {
				MessageBox messageBox = new MessageBox(shell_, style_);
				messageBox.setText(title_);
				messageBox.setMessage(msg);
				return messageBox.open();
			}
			return SWT.NO;
		}

		public void warning() {
			style_ = SWT.APPLICATION_MODAL | SWT.ICON_WARNING | SWT.OK;
			title_ = TextService.getInstance().get("general.warning");
			printStream_ = System.err;
			display();
		}

		public void fatal() {
			style_ = SWT.APPLICATION_MODAL | SWT.ICON_ERROR | SWT.OK;
			title_ = TextService.getInstance().get("general.fatalerror");
			printStream_ = System.err;
			display();
		}

		public boolean confirm() {
			style_ = SWT.APPLICATION_MODAL | SWT.ICON_WARNING | SWT.YES | SWT.NO;
			title_ = TextService.getInstance().get("general.confirmation");
			return display() == SWT.YES;
		}

		/** alpha 48: a question to be answered with OK (go on) or Cancel, e.g. after doing something outside DBGL */
		public boolean okCancel() {
			style_ = SWT.APPLICATION_MODAL | SWT.ICON_INFORMATION | SWT.OK | SWT.CANCEL;
			title_ = TextService.getInstance().get("general.confirmation");
			return display() == SWT.OK;
		}

		/** alpha 48: the message in a dialog of our own, in the colours of the dark mode; the buttons and their result as with MessageBox */
		private int darkBox(String msg) {
			org.eclipse.swt.widgets.Display display = shell_.getDisplay();
			Shell dlg = new Shell(shell_, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
			dlg.setText(title_);
			org.dbgl.gui.abstractdialog.BaseDialog.inheritWindowImage(dlg, shell_);
			dlg.setBackground(DarkTheme.dialogBackground);
			org.eclipse.swt.layout.GridLayout layout = new org.eclipse.swt.layout.GridLayout(2, false);
			layout.marginWidth = 16;
			layout.marginHeight = 14;
			layout.horizontalSpacing = 14;
			layout.verticalSpacing = 16;
			dlg.setLayout(layout);
			int iconStyle = style_ & (SWT.ICON_ERROR | SWT.ICON_WARNING | SWT.ICON_INFORMATION | SWT.ICON_QUESTION | SWT.ICON_WORKING);
			org.eclipse.swt.widgets.Label icon = new org.eclipse.swt.widgets.Label(dlg, SWT.NONE);
			icon.setImage(display.getSystemImage(iconStyle == 0 ? SWT.ICON_INFORMATION: iconStyle));
			icon.setLayoutData(new org.eclipse.swt.layout.GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
			org.eclipse.swt.widgets.Label text = new org.eclipse.swt.widgets.Label(dlg, SWT.WRAP);
			text.setText(msg.replace("&", "&&"));
			org.eclipse.swt.layout.GridData textData = new org.eclipse.swt.layout.GridData(SWT.FILL, SWT.CENTER, true, false);
			textData.widthHint = Math.min(640, Math.max(260, text.computeSize(SWT.DEFAULT, SWT.DEFAULT).x)); // a usual sentence on one line
			text.setLayoutData(textData);

			List<int[]> buttons = new ArrayList<>(); // result, text key index
			if ((style_ & SWT.YES) != 0)
				buttons.add(new int[] {SWT.YES});
			if ((style_ & SWT.NO) != 0)
				buttons.add(new int[] {SWT.NO});
			if ((style_ & SWT.OK) != 0)
				buttons.add(new int[] {SWT.OK});
			if ((style_ & SWT.CANCEL) != 0)
				buttons.add(new int[] {SWT.CANCEL});
			if (buttons.isEmpty())
				buttons.add(new int[] {SWT.OK});
			org.eclipse.swt.widgets.Composite row = new org.eclipse.swt.widgets.Composite(dlg, SWT.NONE);
			row.setBackground(DarkTheme.dialogBackground);
			row.setLayoutData(new org.eclipse.swt.layout.GridData(SWT.END, SWT.END, true, false, 2, 1));
			org.eclipse.swt.layout.GridLayout rowLayout = new org.eclipse.swt.layout.GridLayout(buttons.size(), true);
			rowLayout.marginWidth = 0;
			rowLayout.marginHeight = 0;
			row.setLayout(rowLayout);
			int[] result = {(style_ & SWT.CANCEL) != 0 ? SWT.CANCEL: (style_ & SWT.NO) != 0 ? SWT.NO: SWT.OK}; // closing the window
			for (int[] b: buttons) {
				org.eclipse.swt.widgets.Button button = new org.eclipse.swt.widgets.Button(row, SWT.PUSH);
				String key = switch (b[0]) {
					case SWT.YES -> "button.yes";
					case SWT.NO -> "button.no";
					case SWT.CANCEL -> "button.cancel";
					default -> "button.ok";
				};
				button.setText(TextService.getInstance().get(key));
				org.eclipse.swt.layout.GridData bd = new org.eclipse.swt.layout.GridData(SWT.FILL, SWT.CENTER, true, false);
				bd.widthHint = Math.max(90, button.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
				button.setLayoutData(bd);
				button.addListener(SWT.Selection, e -> {
					result[0] = b[0];
					dlg.close();
				});
				if (b == buttons.get(0))
					dlg.setDefaultButton(button);
			}
			DarkTheme.apply(dlg);
			dlg.pack();
			org.eclipse.swt.graphics.Rectangle area = shell_.getMonitor().getClientArea(), parent = shell_.getBounds();
			org.eclipse.swt.graphics.Point size = dlg.getSize();
			dlg.setLocation(Math.max(area.x, Math.min(parent.x + (parent.width - size.x) / 2, area.x + area.width - size.x)),
				Math.max(area.y, Math.min(parent.y + (parent.height - size.y) / 2, area.y + area.height - size.y)));
			dlg.open();
			while (!dlg.isDisposed())
				if (!display.readAndDispatch())
					display.sleep();
			return result[0];
		}

		/**
		 * Like {@link #confirm()}, but in a small dialog of its own that never wraps lines: the system message box (on Windows
		 * in particular) breaks long lines, which makes lists such as "title: old -> new" hard to read.
		 */
		public boolean confirmUnwrapped() {
			return confirmUnwrapped(null);
		}

		/** @param log shown below the question like a log (scrollable, can be selected and copied), e.g. what an undo changes */
		public boolean confirmUnwrapped(String log) {
			title_ = TextService.getInstance().get("general.confirmation");
			String msg = StringRelatedUtils.stringArrayToString(messages_.toArray(new String[messages_.size()]), StringUtils.LF);
			printStream_.println(title_ + ": " + msg);
			if (shell_ == null)
				return false;
			org.eclipse.swt.widgets.Display display = shell_.getDisplay();
			Shell dlg = new Shell(shell_, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | (log != null ? SWT.RESIZE: SWT.NONE));
			dlg.setText(title_);
			org.dbgl.gui.abstractdialog.BaseDialog.inheritWindowImage(dlg, shell_);
			dlg.setLayout(new org.eclipse.swt.layout.GridLayout(2, false));
			org.eclipse.swt.widgets.Label icon = new org.eclipse.swt.widgets.Label(dlg, SWT.NONE);
			icon.setImage(display.getSystemImage(SWT.ICON_WARNING));
			icon.setLayoutData(new org.eclipse.swt.layout.GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
			org.eclipse.swt.widgets.Label text = new org.eclipse.swt.widgets.Label(dlg, SWT.NONE);
			text.setText(msg.replace("&", "&&"));
			org.eclipse.swt.layout.GridData textData = new org.eclipse.swt.layout.GridData(SWT.FILL, SWT.FILL, true, true);
			textData.horizontalIndent = 8;
			text.setLayoutData(textData);
			if (log != null) {
				textData.grabExcessVerticalSpace = false;
				org.eclipse.swt.widgets.Text logText = new org.eclipse.swt.widgets.Text(dlg, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
				logText.setText(log.replace("\n", logText.getLineDelimiter()));
				org.eclipse.swt.layout.GridData logData = new org.eclipse.swt.layout.GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
				logData.widthHint = 720;
				logData.heightHint = 320;
				logText.setLayoutData(logData);
			}
			org.eclipse.swt.widgets.Composite buttons = new org.eclipse.swt.widgets.Composite(dlg, SWT.NONE);
			buttons.setLayoutData(new org.eclipse.swt.layout.GridData(SWT.END, SWT.END, true, false, 2, 1));
			buttons.setLayout(new org.eclipse.swt.layout.GridLayout(2, true));
			boolean[] yes = {false};
			for (boolean b: new boolean[] {true, false}) {
				org.eclipse.swt.widgets.Button button = new org.eclipse.swt.widgets.Button(buttons, SWT.PUSH);
				button.setText(TextService.getInstance().get(b ? "button.yes": "button.no"));
				org.eclipse.swt.layout.GridData bd = new org.eclipse.swt.layout.GridData(SWT.FILL, SWT.CENTER, true, false);
				bd.widthHint = Math.max(80, button.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
				button.setLayoutData(bd);
				button.addListener(SWT.Selection, e -> {
					yes[0] = b;
					dlg.close();
				});
				if (b)
					dlg.setDefaultButton(button);
			}
			if (DarkTheme.forced()) { // alpha 48
				dlg.setBackground(DarkTheme.dialogBackground);
				DarkTheme.apply(dlg);
			}
			dlg.pack();
			org.eclipse.swt.graphics.Rectangle area = shell_.getMonitor().getClientArea();
			org.eclipse.swt.graphics.Point size = dlg.getSize();
			size.x = Math.min(size.x, area.width);
			size.y = Math.min(size.y, area.height);
			dlg.setSize(size);
			org.eclipse.swt.graphics.Rectangle parent = shell_.getBounds();
			dlg.setLocation(Math.max(area.x, Math.min(parent.x + (parent.width - size.x) / 2, area.x + area.width - size.x)),
				Math.max(area.y, Math.min(parent.y + (parent.height - size.y) / 2, area.y + area.height - size.y)));
			dlg.open();
			while (!dlg.isDisposed())
				if (!display.readAndDispatch())
					display.sleep();
			return yes[0];
		}

		public boolean noErrors() {
			return messages_.isEmpty();
		}

		public boolean valid() {
			if (noErrors())
				return true;

			display();
			if (tabItem_ != null) {
				tabItem_.getParent().setSelection(tabItem_);
			} else if (treeItem_ != null) {
				Event evt = new Event();
				evt.item = treeItem_;
				treeItem_.getParent().setSelection(treeItem_);
				treeItem_.getParent().notifyListeners(SWT.Selection, evt);
			}
			control_.setFocus();
			return false;
		}
	}
}
