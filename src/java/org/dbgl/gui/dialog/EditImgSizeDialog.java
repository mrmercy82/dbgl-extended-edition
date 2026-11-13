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

import org.dbgl.gui.abstractdialog.SizeControlledTabbedDialog;
import org.dbgl.gui.controls.Chain;
import org.dbgl.model.ImgSizeCommand;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;


public class EditImgSizeDialog extends SizeControlledTabbedDialog<String> {

	private ImgSizeCommand sizeCommand;

	public EditImgSizeDialog(Shell parent, String command) {
		super(parent, "imgsizedialog");
		sizeCommand = new ImgSizeCommand(command);
	}

	/** the image the size is for: a button reads its geometry (alpha 48), null: none */
	private java.io.File image_;

	public EditImgSizeDialog withImage(java.io.File image) {
		image_ = image;
		return this;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.imgsize.title");
	}

	@Override
	protected void onShellCreated() {
		Composite composite = createTabWithComposite("dialog.imgsize.params", 2);

		Spinner bytesPerSector = Chain.on(composite).lbl(l -> l.key("dialog.imgsize.bytespersector")).spn(
			s -> s.min(1).max(4096).incr(1).pageIncr(512).select(sizeCommand.getBytesPerSector())).spinner();
		Spinner sectorsPerTrack = Chain.on(composite).lbl(l -> l.key("dialog.imgsize.sectorspertrack")).spn(
			s -> s.min(1).max(255).incr(1).pageIncr(64).select(sizeCommand.getSectorsPerTrack())).spinner();
		Spinner heads = Chain.on(composite).lbl(l -> l.key("dialog.imgsize.heads")).spn(s -> s.min(1).max(64).incr(1).pageIncr(16).select(sizeCommand.getHeads())).spinner();
		Spinner cylinders = Chain.on(composite).lbl(l -> l.key("dialog.imgsize.cylinders")).spn(s -> s.min(1).max(8192).incr(1).pageIncr(20).select(sizeCommand.getCylinders())).spinner();

		bytesPerSector.setFocus();

		Text totalSize = Chain.on(composite).lbl(l -> l.key("dialog.imgsize.totalsize")).txt(
			t -> t.val(text_.get("dialog.imgsize.totalsize.value", new Long[] {sizeCommand.getTotalSize(), sizeCommand.getTotalSizeInMB()})).nonEditable()).text();

		ModifyListener listener = event -> {
			sizeCommand = new ImgSizeCommand(bytesPerSector.getSelection(), sectorsPerTrack.getSelection(), heads.getSelection(), cylinders.getSelection());
			totalSize.setText(text_.get("dialog.imgsize.totalsize.value", new Long[] {sizeCommand.getTotalSize(), sizeCommand.getTotalSizeInMB()}));
			composite.layout();
		};

		// alpha 48: what the image says itself (VHD footer, partition table, boot sector)
		if (image_ != null) {
			new org.eclipse.swt.widgets.Label(composite, org.eclipse.swt.SWT.NONE);
			org.eclipse.swt.widgets.Button read = new org.eclipse.swt.widgets.Button(composite, org.eclipse.swt.SWT.PUSH);
			read.setText(text_.get("dialog.imgsize.fromimage"));
			read.addListener(org.eclipse.swt.SWT.Selection, e -> {
				org.dbgl.util.DiskGeometry.Result found = org.dbgl.util.DiskGeometry.of(image_);
				if (found == null) {
					org.dbgl.gui.controls.Mess_.on(shell_).key("dialog.imgsize.notfound", new Object[] {image_.getName()}).warning();
					return;
				}
				bytesPerSector.setSelection(found.size().getBytesPerSector());
				sectorsPerTrack.setSelection(found.size().getSectorsPerTrack());
				heads.setSelection(found.size().getHeads());
				cylinders.setSelection(found.size().getCylinders());
			});
		}

		bytesPerSector.addModifyListener(listener);
		sectorsPerTrack.addModifyListener(listener);
		heads.addModifyListener(listener);
		cylinders.addModifyListener(listener);

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				result_ = sizeCommand.toString();
				shell_.close();
			}
		});
	}
}
