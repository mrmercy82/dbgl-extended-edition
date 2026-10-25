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

import org.apache.commons.lang3.StringUtils;

import org.dbgl.gui.abstractdialog.SizeControlledTabbedDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.Text_;
import org.dbgl.model.NativeCommand;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


public class EditNativeCommandDialog extends SizeControlledTabbedDialog<NativeCommand> {

	private final NativeCommand nativeCommand_;

	public EditNativeCommandDialog(Shell parent, NativeCommand cmd) {
		super(parent, "nativecommanddialog");
		nativeCommand_ = cmd;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get(((nativeCommand_ == null) ? "dialog.nativecommand.title.add": "dialog.nativecommand.title.edit"));
	}

	@Override
	protected void onShellCreated() {
		Composite composite = createTabWithComposite("dialog.nativecommand.tab.info", 3);

		Chain chnCommand = Chain.on(composite).lbl(l -> l.key("dialog.nativecommand.command")).txt(Text_.Builder::focus).but(
			b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.NATIVE_EXE, false)).build();
		Text parameters = Chain.on(composite).lbl(l -> l.key("dialog.nativecommand.parameters")).txt(t -> t.horSpan(2)).text();
		Text cwd = Chain.on(composite).lbl(l -> l.key("dialog.nativecommand.cwd")).txt(t -> t).but(b -> b.browse(false, Button_.BrowseType.DIR, Button_.CanonicalType.NATIVE_EXE, false)).text();
		Button waitFor = Chain.on(composite).lbl(l -> l.key("dialog.nativecommand.waitfor")).but(b -> b.horSpan(2)).button();

		chnCommand.getButton().setData(Button_.DATA_ALT_CONTROL, cwd);

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (!isValid(chnCommand.getText(), cwd)) {
					return;
				}
				int orderNr = (nativeCommand_ == null) ? -1: nativeCommand_.getOrderNr();
				result_ = new NativeCommand(chnCommand.getText().getText(), parameters.getText(), cwd.getText(), waitFor.getSelection(), orderNr);
				shell_.close();
			}
		});

		if (nativeCommand_ != null) {
			chnCommand.getText().setText(nativeCommand_.getCommand().getPath());
			parameters.setText(nativeCommand_.getParameters());
			cwd.setText(nativeCommand_.getCwd().getPath());
			waitFor.setSelection(nativeCommand_.isWaitFor());
		}
	}

	private boolean isValid(Text command, Text cwd) {
		Mess_.Builder mess = Mess_.on(shell_);
		if (StringUtils.isBlank(command.getText())) {
			mess.key("dialog.nativecommand.required.command").bind(command);
		} else if (StringUtils.isBlank(cwd.getText())) {
			mess.key("dialog.nativecommand.required.cwd").bind(cwd);
		}
		return mess.valid();
	}
}
