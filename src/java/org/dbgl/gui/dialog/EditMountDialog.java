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
package org.dbgl.gui.dialog;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.exception.InvalidMountstringException;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.Label_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.ImageMount;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.model.conf.mount.OverlayMount;
import org.dbgl.model.conf.mount.PhysFsMount;
import org.dbgl.model.factory.MountFactory;
import org.dbgl.util.StringRelatedUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


public class EditMountDialog extends SizeControlledButtonDialog<String> {

	private final String mount_;
	private final char driveletter_;

	public EditMountDialog(Shell parent, String mount, char driveletter) {
		super(parent, "mountdialog");
		mount_ = mount;
		driveletter_ = driveletter;
	}

	/** the mount being edited, as read */
	private Mount previous_;

	@Override
	protected String getDialogTitle() {
		return (mount_ == null) ? text_.get("dialog.mount.title.add"): text_.get("dialog.mount.title.edit");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(2, false));

		Combo driveletter = Chain.on(contents_).lbl(l -> l.key("dialog.mount.driveletter")).cmb(c -> c.items("ABCDEFGHIJKLMNOPQRSTUVWXY".split("(?!^)"))).combo();
		// parameters DBGL has no field for, e.g. of newer DOSBox versions: shown and kept as they are
		Text extraOptions = Chain.on(contents_).lbl(l -> l.key("dialog.mount.extraoptions")).txt(t -> t.tooltip("dialog.mount.extraoptions.tooltip")).text();

		Button mountDirButton = Button_.on(contents_).radio().key("dialog.mount.mountdir").ctrl();

		Composite dirComposite = Composite_.on(contents_).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).innerLayout(7).build();

		Text dirMountDir = Chain.on(dirComposite).txt(t -> t.horSpan(6)).but(b -> b.browse(false, Button_.BrowseType.DIR, Button_.CanonicalType.DOSROOT, false)).text();
		Combo dirMountType = Chain.on(dirComposite).lbl(l -> l.key("dialog.mount.mountdiras")).cmb(c -> c.horSpan(6).items("profile", "mount_type")).combo();
		Text dirMountLabel = Chain.on(dirComposite).lbl(l -> l.key("dialog.mount.drivelabel")).txt(t -> t.horSpan(6)).text();
		Combo dirMountLowlevelCdType = Chain.on(dirComposite).lbl(l -> l.horSpan(3).key("dialog.mount.lowlevelcdsupport")).cmb(c -> c.items("profile", "lowlevelcd_type")).combo();
		Combo dirMountUseCd = Chain.on(dirComposite).lbl(l -> l.key("dialog.mount.usecd")).cmb(
			c -> c.horSpan(2).items(new String[] {StringUtils.EMPTY, "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"})).combo();
		Combo dirMountFreesize = Chain.on(dirComposite).lbl(l -> l.key("dialog.mount.freesize")).cmb(c -> c.editable().items("profile", "freesize")).combo();
		Label dirMountMbLabel = Chain.on(dirComposite).lbl(l -> l.horSpan(5)).label();

		dirMountType.add(StringUtils.EMPTY, 0);
		dirMountLowlevelCdType.add(StringUtils.EMPTY, 0);
		dirMountFreesize.add(StringUtils.EMPTY, 0);

		Label_.on(contents_).style(SWT.SEPARATOR | SWT.HORIZONTAL).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1)).build();

		Button mountImageButton = Button_.on(contents_).radio().key("dialog.mount.mountimages").ctrl();

		Composite imgComposite = Composite_.on(contents_).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).innerLayout(5).build();

		Chain imgChain = Chain.on(imgComposite).txt(t -> t.horSpan(4).multi()).but(b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.CDIMAGE, false)).build();
		Combo imgMountType = Chain.on(imgComposite).lbl(l -> l.key("dialog.mount.mountdiras")).cmb(c -> c.items("profile", "imgmount_type")).combo();
		Combo imgMountFs = Chain.on(imgComposite).lbl(l -> l.key("dialog.mount.imgmountfs")).cmb(c -> c.horSpan(2).items("profile", "imgmount_fs")).combo();
		Text imgMountSize = Chain.on(imgComposite).lbl(l -> l.key("dialog.mount.imgmountsize")).txt(t -> t.horSpan(3)).text();
		Button imgSizeHelperButton = Chain.on(imgComposite).but(b -> b.layoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false)).threedots().listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				String command = new EditImgSizeDialog(shell_, imgMountSize.getText()).open();
				if (command != null)
					imgMountSize.setText(command);
			}
		})).button();

		// how to change CDs in the game: hard to find otherwise
		Label imgSeriesHint = new Label(imgComposite, SWT.WRAP);
		GridData hintData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 5, 1);
		hintData.widthHint = 420;
		imgSeriesHint.setLayoutData(hintData);
		imgSeriesHint.setText(text_.get("dialog.mount.imageseries.hint3"));
		imgSeriesHint.setForeground(shell_.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));

		Text imgMountImage = imgChain.getText();
		imgChain.getButton().setData(Button_.DATA_COMBO, imgMountType);

		imgMountType.setText("iso");
		imgMountFs.add(StringUtils.EMPTY, 0);

		Label_.on(contents_).style(SWT.SEPARATOR | SWT.HORIZONTAL).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1)).build();

		Button mountZipButton = Button_.on(contents_).radio().key("dialog.mount.mountzip").ctrl();

		Composite zipComposite = Composite_.on(contents_).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).innerLayout(3).build();

		Text zipMountZip = Chain.on(zipComposite).lbl(l -> l.key("dialog.mount.zipfile")).txt(t -> t).but(b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.ZIP, false)).text();
		Text zipMountWrite = Chain.on(zipComposite).lbl(l -> l.key("dialog.mount.writedirectory")).txt(t -> t).but(
			b -> b.browse(false, Button_.BrowseType.DIR, Button_.CanonicalType.DOSROOT, false)).text();
		Combo zipMountType = Chain.on(zipComposite).lbl(l -> l.key("dialog.mount.mountzipas")).cmb(c -> c.horSpan(2).items("profile", "zipmount_type")).combo();
		Text zipMountLabel = Chain.on(zipComposite).lbl(l -> l.key("dialog.mount.drivelabel")).txt(t -> t.horSpan(2)).text();

		zipMountType.add(StringUtils.EMPTY, 0);

		dirMountType.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				updateInterfaceElements(imgSizeHelperButton, dirMountUseCd, dirMountFreesize, dirMountMbLabel, dirMountType, dirMountLowlevelCdType, imgMountFs, imgMountSize);
			}
		});

		imgMountFs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				updateInterfaceElements(imgSizeHelperButton, dirMountUseCd, dirMountFreesize, dirMountMbLabel, dirMountType, dirMountLowlevelCdType, imgMountFs, imgMountSize);
			}
		});

		SelectionAdapter driveLetterSelectionAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				boolean imgFsNone = "none".equalsIgnoreCase(imgMountFs.getText());
				int sel = driveletter.getSelectionIndex();
				if (sel >= 0) {
					if (mountImageButton.getSelection() && imgFsNone) {
						if (!StringUtils.isNumeric(driveletter.getItem(sel))) {
							driveletter.setItems("0", "1", "2", "3");
							driveletter.setText(driveletter.getItem(Math.min(sel, 3)));
						}
					} else {
						if (StringUtils.isNumeric(driveletter.getItem(sel))) {
							driveletter.setItems("ABCDEFGHIJKLMNOPQRSTUVWXY".split("(?!^)"));
							driveletter.setText(driveletter.getItem(Math.min(sel, 3)));
						}
					}
				}
			}
		};

		imgMountFs.addSelectionListener(driveLetterSelectionAdapter);
		mountDirButton.addSelectionListener(driveLetterSelectionAdapter);
		mountImageButton.addSelectionListener(driveLetterSelectionAdapter);
		mountZipButton.addSelectionListener(driveLetterSelectionAdapter);

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (!isValid(mountDirButton, dirMountDir, mountImageButton, imgMountImage, imgMountFs, imgMountSize, mountZipButton, zipMountZip)) {
					return;
				}
				Mount mount = null;
				if (mountDirButton.getSelection()) {
					if ("overlay".equalsIgnoreCase(dirMountType.getText())) {
						mount = MountFactory.createOverlayMount(driveletter.getText(), dirMountDir.getText(), dirMountType.getText(), dirMountLabel.getText(),
							dirMountLowlevelCdType.isEnabled() ? dirMountLowlevelCdType.getText(): null, dirMountFreesize.isEnabled() ? dirMountFreesize.getText(): null,
							dirMountUseCd.isEnabled() ? dirMountUseCd.getText(): null);
					} else {
						mount = MountFactory.createDirMount(driveletter.getText(), dirMountDir.getText(), dirMountType.getText(), dirMountLabel.getText(),
							dirMountLowlevelCdType.isEnabled() ? dirMountLowlevelCdType.getText(): null, dirMountFreesize.isEnabled() ? dirMountFreesize.getText(): null,
							dirMountUseCd.isEnabled() ? dirMountUseCd.getText(): null);
					}
				} else if (mountImageButton.getSelection()) {
					mount = MountFactory.createImageMount(driveletter.getText(), StringRelatedUtils.textAreaToStringArray(imgMountImage.getText(), imgMountImage.getLineDelimiter()),
						imgMountType.getText(), imgMountFs.getText(), imgMountSize.isEnabled() ? imgMountSize.getText(): null, null);
				} else if (mountZipButton.getSelection()) {
					mount = MountFactory.createPhysFsMount(driveletter.getText(), zipMountZip.getText(), zipMountWrite.getText(), zipMountType.getText(), zipMountLabel.getText());
				}
				if (mount != null) {
					mount.setExtraOptions(extraOptions.getText());
					mount.takeOverFrom(previous_); // unchanged: the line is kept exactly
					result_ = mount.toString();
				}
				shell_.close();
			}
		});

		ModifyListener modListener = event -> {
			mountDirButton.setSelection(event.widget == dirMountDir);
			mountImageButton.setSelection(event.widget == imgMountImage);
			mountZipButton.setSelection(event.widget == zipMountZip);
			mountDirButton.notifyListeners(SWT.Selection, null);
		};

		dirMountDir.addModifyListener(modListener);
		imgMountImage.addModifyListener(modListener);
		zipMountZip.addModifyListener(modListener);

		Mount mount = null;
		if (mount_ != null) {
			try {
				mount = MountFactory.create(mount_);
			} catch (InvalidMountstringException e1) {
				// if the mount could not be instantiated, use default values
			}
		}
		if (mount == null)
			mount = MountFactory.createDefaultNewMount(driveletter_);
		else
			previous_ = mount;
		extraOptions.setText(mount.getExtraOptions());

		driveletter.select(mount.getDrive() - 'A');
		if (mount instanceof PhysFsMount) {
			PhysFsMount physFsMount = (PhysFsMount)mount;
			mountZipButton.setSelection(true);
			zipMountZip.setText(physFsMount.getPath().getPath());
			if (physFsMount.getWrite() != null)
				zipMountWrite.setText(physFsMount.getWrite().getPath());
			if (StringUtils.isNotBlank(physFsMount.getMountAs())) {
				zipMountType.setText(physFsMount.getMountAs());
			}
			zipMountLabel.setText(physFsMount.getLabel());
			zipMountZip.selectAll();
			zipMountZip.setFocus();
		} else if (mount instanceof ImageMount) {
			ImageMount imageMount = (ImageMount)mount;
			mountImageButton.setSelection(true);
			if (StringUtils.isNotBlank(imageMount.getMountAs())) {
				imgMountType.setText(imageMount.getMountAs());
			}
			if (imageMount.getFs().equalsIgnoreCase("none")) {
				driveletter.setItems("0", "1", "2", "3");
			}
			if (StringUtils.isNotBlank(imageMount.getFs())) {
				imgMountFs.setText(imageMount.getFs());
			}
			if (StringUtils.isNotBlank(imageMount.getSize())) {
				imgMountSize.setText(imageMount.getSize());
			}
			imgMountImage.setText(StringRelatedUtils.stringArrayToString(imageMount.getImgPathStrings(), imgMountImage.getLineDelimiter()));
			imgMountImage.selectAll();
			imgMountImage.setFocus();
		} else if (mount instanceof OverlayMount) {
			OverlayMount overlayMount = (OverlayMount)mount;
			mountDirButton.setSelection(true);
			dirMountDir.setText(overlayMount.getPath().getPath());
			if (StringUtils.isNotBlank(overlayMount.getMountAs())) {
				dirMountType.setText(overlayMount.getMountAs());
			}
			dirMountLabel.setText(overlayMount.getLabel());
			dirMountLowlevelCdType.setText(overlayMount.getLowlevelCD());
			dirMountUseCd.setText(overlayMount.getUseCD());
			if (StringUtils.isNotBlank(overlayMount.getFreesize())) {
				dirMountFreesize.setText(overlayMount.getFreesize());
			}
			dirMountDir.selectAll();
			dirMountDir.setFocus();
		} else if (mount instanceof DirMount) {
			DirMount dirMount = (DirMount)mount;
			mountDirButton.setSelection(true);
			dirMountDir.setText(dirMount.getPath().getPath());
			if (StringUtils.isNotBlank(dirMount.getMountAs())) {
				dirMountType.setText(dirMount.getMountAs());
			}
			dirMountLabel.setText(dirMount.getLabel());
			dirMountLowlevelCdType.setText(dirMount.getLowlevelCD());
			dirMountUseCd.setText(dirMount.getUseCD());
			if (StringUtils.isNotBlank(dirMount.getFreesize())) {
				dirMountFreesize.setText(dirMount.getFreesize());
			}
			dirMountDir.selectAll();
			dirMountDir.setFocus();
		}
		driveletter.setText(mount.getDriveAsString());

		driveletter.addSelectionListener(new SelectionAdapter() {
			String previouslyChosenDirMountType = null;

			@Override
			public void widgetSelected(SelectionEvent e) {
				if (driveletter.getText().equals("A") || driveletter.getText().equals("B")) {
					if (previouslyChosenDirMountType == null) {
						previouslyChosenDirMountType = dirMountType.getText();
						dirMountType.setText("floppy");
					}
				} else {
					if (previouslyChosenDirMountType != null) {
						dirMountType.setText(previouslyChosenDirMountType);
						previouslyChosenDirMountType = null;
					}
				}
			}
		});

		updateInterfaceElements(imgSizeHelperButton, dirMountUseCd, dirMountFreesize, dirMountMbLabel, dirMountType, dirMountLowlevelCdType, imgMountFs, imgMountSize);

		// room for three images and the hint below them, also when a smaller size of this window was saved before
		((GridData)imgMountImage.getLayoutData()).heightHint = imgMountImage.getLineHeight() * 3;
		org.eclipse.swt.graphics.Point size = shell_.getSize();
		org.eclipse.swt.graphics.Point wanted = shell_.computeSize(size.x, SWT.DEFAULT);
		org.eclipse.swt.graphics.Rectangle area = shell_.getMonitor().getClientArea();
		if (wanted.y > size.y)
			shell_.setSize(size.x, Math.min(wanted.y, area.height));
	}

	private void updateInterfaceElements(Button imgSizeConfig, Combo usecd, Combo freesize, Label mbLabel, Combo mountType, Combo lowlevelcdType, Combo imgmountFs, Text imgmountSize) {
		boolean enableLLItems = "cdrom".equalsIgnoreCase(mountType.getText());
		lowlevelcdType.setEnabled(enableLLItems);
		usecd.setEnabled(enableLLItems);
		freesize.setEnabled(!enableLLItems);

		String sizeLabel = "floppy".equalsIgnoreCase(mountType.getText()) ? text_.get("dialog.mount.kb"): text_.get("dialog.mount.mb");
		mbLabel.setText(sizeLabel);
		mbLabel.pack();

		boolean imgFsNone = "none".equalsIgnoreCase(imgmountFs.getText());
		imgmountSize.setEnabled(imgFsNone);
		imgSizeConfig.setEnabled(imgFsNone);
	}

	private boolean isValid(Button imgSizeHelperButton, Text dirMountDir, Button mountImageButton, Text imgMountImage, Combo imgMountFs, Text imgMountSize, Button mountZipButton, Text zipMountZip) {
		Mess_.Builder mess = Mess_.on(shell_);
		if (imgSizeHelperButton.getSelection() && StringUtils.isBlank(dirMountDir.getText())) {
			mess.key("dialog.mount.required.path").bind(dirMountDir);
		} else if (mountImageButton.getSelection()) {
			if (StringUtils.isBlank(imgMountImage.getText()))
				mess.key("dialog.mount.required.image").bind(imgMountImage);
			if (imgMountFs.getText().equalsIgnoreCase("none") && StringUtils.isBlank(imgMountSize.getText()))
				mess.key("dialog.mount.required.imgsize").bind(imgMountSize);
		} else if (mountZipButton.getSelection() && StringUtils.isBlank(zipMountZip.getText())) {
			mess.key("dialog.mount.required.zip").bind(zipMountZip);
		}
		return mess.valid();
	}
}
