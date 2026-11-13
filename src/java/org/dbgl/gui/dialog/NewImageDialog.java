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
package org.dbgl.gui.dialog;

import java.io.File;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.util.DiskImageMaker;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;


/**
 * Alpha 48: an empty, formatted floppy or hard disk image, made by DBGL (like MAKEIMG of DOSBox Staging 0.83), to install a
 * game onto or to keep its saves. The result is the file made, with "floppy" or "hdd" for the mount type.
 */
public class NewImageDialog extends SizeControlledButtonDialog<NewImageDialog.Made> {

	public record Made(File file, String type) {
	}

	private final File dir_;

	/** @param dir where the file dialog starts (e.g. the DOSroot) */
	public NewImageDialog(Shell parent, File dir) {
		super(parent, "newimagedialog");
		dir_ = dir;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.newimage.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(3, false));
		new Label(contents_, SWT.NONE).setText(text_.get("dialog.newimage.kind"));
		Combo kind = new Combo(contents_, SWT.READ_ONLY);
		for (DiskImageMaker.Floppy f: DiskImageMaker.Floppy.values())
			kind.add(text_.get("dialog.newimage.floppy", new Object[] {f.kb() < 1000 ? f.kb() + " KB": String.format("%.2f MB", f.kb() / 1000.0).replace(".00", "").replace("0 MB", " MB")}));
		kind.add(text_.get("dialog.newimage.harddisk"));
		kind.select(3); // 1.44 MB
		kind.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		new Label(contents_, SWT.NONE).setText(text_.get("dialog.newimage.size"));
		org.eclipse.swt.widgets.Composite size = new org.eclipse.swt.widgets.Composite(contents_, SWT.NONE);
		GridLayout sl = new GridLayout(2, false);
		sl.marginWidth = 0;
		sl.marginHeight = 0;
		size.setLayout(sl);
		size.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		Spinner mb = new Spinner(size, SWT.BORDER);
		mb.setValues(256, DiskImageMaker.MIN_HD_MB, DiskImageMaker.MAX_HD_MB, 0, 32, 256);
		new Label(size, SWT.NONE).setText("MB");

		new Label(contents_, SWT.NONE).setText(text_.get("dialog.newimage.label"));
		Text label = new Text(contents_, SWT.BORDER);
		label.setTextLimit(11);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		new Label(contents_, SWT.NONE).setText(text_.get("dialog.newimage.file"));
		Text file = new Text(contents_, SWT.BORDER);
		GridData fd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		fd.widthHint = 360;
		file.setLayoutData(fd);
		Button browse = new Button(contents_, SWT.PUSH);
		browse.setText(text_.get("button.browse"));
		browse.addListener(SWT.Selection, e -> {
			FileDialog d = new FileDialog(shell_, SWT.SAVE);
			d.setFilterPath(dir_ == null ? null: dir_.getPath());
			d.setFileName(kind.getSelectionIndex() < DiskImageMaker.Floppy.values().length ? "disk.img": "hdd.img");
			d.setFilterExtensions(new String[] {"*.img;*.IMG;*.ima;*.IMA", "*"});
			String chosen = d.open();
			if (chosen != null)
				file.setText(chosen);
		});
		Label hint = new Label(contents_, SWT.WRAP);
		hint.setText(text_.get("dialog.newimage.hint"));
		GridData hd = new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1);
		hd.widthHint = 460;
		hint.setLayoutData(hd);
		Runnable enable = () -> mb.setEnabled(kind.getSelectionIndex() == DiskImageMaker.Floppy.values().length);
		kind.addListener(SWT.Selection, e -> enable.run());
		enable.run();

		createOkCancelButtons(new org.eclipse.swt.events.SelectionAdapter() {
			@Override
			public void widgetSelected(org.eclipse.swt.events.SelectionEvent event) {
				if (StringUtils.isBlank(file.getText())) {
					Mess_.on(shell_).key("dialog.newimage.required.file").bind(file).valid();
					return;
				}
				File f = new File(file.getText().trim());
				if (!f.isAbsolute() && (dir_ != null))
					f = new File(dir_, f.getPath());
				if (f.exists()) {
					Mess_.on(shell_).key("dialog.newimage.exists", new Object[] {f.getPath()}).warning();
					return;
				}
				try {
					boolean floppy = kind.getSelectionIndex() < DiskImageMaker.Floppy.values().length;
					if (floppy)
						DiskImageMaker.floppy(f, DiskImageMaker.Floppy.values()[kind.getSelectionIndex()], label.getText());
					else
						DiskImageMaker.hardDisk(f, mb.getSelection(), label.getText());
					result_ = new Made(f, floppy ? "floppy": "hdd");
					shell_.close();
				} catch (java.io.IOException e) {
					Mess_.on(shell_).exception(e).warning();
				}
			}
		});
	}
}
