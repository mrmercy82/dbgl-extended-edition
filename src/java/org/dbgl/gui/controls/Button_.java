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
package org.dbgl.gui.controls;

import java.io.File;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.exception.InvalidMountstringException;
import org.dbgl.gui.dialog.BrowseArchiveDialog;
import org.dbgl.model.FileLocation;
import org.dbgl.model.ICanonicalize;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.ImageMount;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.model.conf.mount.PhysFsMount;
import org.dbgl.model.factory.MountFactory;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ITextService;
import org.dbgl.service.ImageService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.StringRelatedUtils;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


public final class Button_ {

	public static final String DATA_CANONICALIZER = "canonicalizer";
	public static final String DATA_COMBO = "combo";
	public static final String DATA_ALT_CONTROL = "altControl";
	public static final String DATA_CONTROL = "control";

	public enum BrowseType {
		DIR, FILE
	}

	public enum CanonicalType {
		DOSROOT, DFEND, CDIMAGE, ZIP, DBGLZIP, DOSBOX, DOSBOXEXE, DOSBOXCONF, DOC, BOOTER, EXE, INSTALLER, NATIVE_EXE, GLSHADER, NONE
	}

	private final Button button_;

	private Button_(Builder builder, TextControl_ textControl) {
		button_ = new Button(builder.composite_, builder.style_);
		button_.setEnabled(builder.enabled_);
		if (DarkTheme.forced()) {
			button_.setBackground(builder.composite_.getBackground());
			button_.setForeground(DarkTheme.defaultForeground);
		}
		button_.setLayoutData(builder.layoutData());

		if (builder.tooltip_ != null)
			button_.setToolTipText(builder.tooltip_);
		if (builder.text_ != null)
			button_.setText(builder.text_);
		if (builder.image_ != null)
			button_.setImage(builder.image_);
		if (builder.selected_)
			button_.setSelection(true);

		if (builder.browseType_ != null) {
			button_.setData(DATA_CONTROL, textControl);
			button_.setData(DATA_ALT_CONTROL, null);
			button_.setData(DATA_COMBO, null);
			button_.setData(DATA_CANONICALIZER, builder.canonicalizer_);

			builder.listener_ = new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					TextControl_ control = (TextControl_)button_.getData(DATA_CONTROL);
					Text altControl = (Text)button_.getData(DATA_ALT_CONTROL);
					Combo combo = (Combo)button_.getData(DATA_COMBO);

					String result = null;
					File filterPath = null;
					String[] filenames = null;

					button_.getShell().setEnabled(false);

					String rawFilterPath = filterPath(builder, control, altControl);
					File fpath = new File(rawFilterPath);
					if (!fpath.isDirectory())
						fpath = fpath.getParentFile();

					if (builder.browseType_ == BrowseType.DIR) {
						DirectoryDialog dialog = new DirectoryDialog(button_.getShell());
						if (fpath != null)
							dialog.setFilterPath(fpath.getPath());
						result = dialog.open();
					} else if (builder.browseType_ == BrowseType.FILE && ((builder.browseCanonicalType_ == CanonicalType.EXE) || (builder.browseCanonicalType_ == CanonicalType.INSTALLER))
							&& (FilesUtils.isArchive(rawFilterPath) || FilesUtils.isCdImageFile(rawFilterPath) || FilesUtils.isFatImage(rawFilterPath))) {
						result = rawFilterPath;
					} else if (builder.browseType_ == BrowseType.FILE) {
						int style = builder.browseSave_ ? SWT.SAVE: (builder.browseCanonicalType_ == CanonicalType.CDIMAGE) ? SWT.OPEN | SWT.MULTI: SWT.OPEN;
						FileDialog dialog = new FileDialog(button_.getShell(), style);
						if (fpath != null)
							dialog.setFilterPath(fpath.getPath());

						ITextService text = TextService.getInstance();

						String[] filterNames = null;
						String[] filterExts = null;
						switch (builder.browseCanonicalType_) {
							case DOC:
								filterNames = new String[] {FilesUtils.ALL_FILTER};
								filterExts = new String[] {FilesUtils.ALL_FILTER};
								break;
							case EXE:
							case DOSBOXEXE:
							case INSTALLER:
								filterNames = new String[] {text.get("filetype.applicable"), text.get("filetype.exe"), text.get("filetype.hdimage"), text.get("filetype.cdimage"),
										text.get("filetype.floppyimage"), text.get("filetype.archive"), FilesUtils.ALL_FILTER};
								filterExts = new String[] {FilesUtils.EXE_FILTER + ";" + FilesUtils.HDI_FILTER + ';' + FilesUtils.CDI_FILTER + ';' + FilesUtils.FATI_FILTER, FilesUtils.EXE_FILTER,
										FilesUtils.HDI_FILTER, FilesUtils.CDI_FILTER, FilesUtils.FATI_FILTER, FilesUtils.ARC_FILTER, FilesUtils.ALL_FILTER};
								break;
							case DOSBOX: // only applicable on OSX
							case NATIVE_EXE:
								filterNames = new String[] {text.get("filetype.native_exe"), FilesUtils.ALL_FILTER};
								filterExts = new String[] {FilesUtils.NATIVE_EXE_FILTER, FilesUtils.ALL_FILTER};
								break;
							case ZIP:
								filterNames = new String[] {text.get("filetype.archive"), FilesUtils.ALL_FILTER};
								filterExts = new String[] {FilesUtils.ARC_FILTER, FilesUtils.ALL_FILTER};
								break;
							case DBGLZIP:
								filterNames = new String[] {text.get("filetype.gamepack"), FilesUtils.ALL_FILTER};
								filterExts = new String[] {FilesUtils.DBGLZIP_FILTER, FilesUtils.ALL_FILTER};
								break;
							case BOOTER:
								filterNames = new String[] {text.get("filetype.booterimage"), FilesUtils.ALL_FILTER};
								filterExts = new String[] {FilesUtils.BTR_FILTER, FilesUtils.ALL_FILTER};
								break;
							case DFEND:
								filterNames = new String[] {text.get("filetype.dfendprofile")};
								filterExts = new String[] {FileLocationService.DFEND_PROFILES_STRING};
								break;
							case CDIMAGE:
								filterNames = new String[] {text.get("filetype.applicable"), text.get("filetype.hdimage"), text.get("filetype.cdimage"), text.get("filetype.floppyimage"),
										FilesUtils.ALL_FILTER};
								filterExts = new String[] {FilesUtils.HDI_FILTER + ';' + FilesUtils.CDI_FILTER + ';' + FilesUtils.FATI_FILTER, FilesUtils.HDI_FILTER, FilesUtils.CDI_FILTER,
										FilesUtils.FATI_FILTER, FilesUtils.ALL_FILTER};
								break;
							case DOSBOXCONF:
								filterNames = new String[] {text.get("filetype.conf"), FilesUtils.ALL_FILTER};
								filterExts = new String[] {FilesUtils.CNF_FILTER, FilesUtils.ALL_FILTER};
								break;
							case GLSHADER:
								filterNames = new String[] {text.get("filetype.glshader"), FilesUtils.ALL_FILTER};
								filterExts = new String[] {FilesUtils.GLSHADER_FILTER, FilesUtils.ALL_FILTER};
								break;
							default:
						}

						if (filterNames != null)
							dialog.setFilterNames(filterNames);

						if (filterExts != null)
							dialog.setFilterExtensions(filterExts);

						if (builder.browseCanonicalType_ == CanonicalType.DFEND)
							dialog.setFileName(FileLocationService.DFEND_PROFILES_STRING);

						result = dialog.open();

						if (builder.browseCanonicalType_ == CanonicalType.CDIMAGE) {
							filterPath = new File(dialog.getFilterPath());
							filenames = dialog.getFileNames();
						}
					}

					if (result != null) {
						result = getResult(button_.getShell(), builder, control, result, filterPath, filenames);
						if (result != null) {
							control.setText(result);
							if ((builder.browseCanonicalType_ == CanonicalType.DOSBOX) && (altControl != null)) {
								File confFile = new File(result, FileLocationService.DOSBOX_CONF_STRING);
								String confText = new FileLocation(confFile.getPath(), builder.canonicalizer_).getFile().getPath();
								altControl.setText(confText);
							} else if ((builder.browseCanonicalType_ == CanonicalType.CDIMAGE) && (combo != null) && (filenames != null)) {
								if (FilesUtils.isCdImageFile(filenames[0])) {
									combo.setText("iso");
								} else if (FilesUtils.isFatImage(filenames[0])) {
									combo.setText("floppy");
								}
							} else if ((builder.browseCanonicalType_ == CanonicalType.NATIVE_EXE) && (altControl != null)) {
								String dir = new FileLocation(result, builder.canonicalizer_).getFile().getParent();
								if (dir != null)
									altControl.setText(dir);
							}
						}
					}

					while (button_.getDisplay().readAndDispatch());

					button_.getShell().setEnabled(true);
				}
			};
		}

		if (builder.grabListSource_ != null) {
			button_.setData(DATA_CONTROL, textControl);

			builder.listener_ = new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					TextControl_ control = (TextControl_)button_.getData(DATA_CONTROL);

					int index = builder.grabListSource_.getSelectionIndex();
					if (index == -1 && builder.grabListSource_.getItemCount() == 1) {
						builder.grabListSource_.select(0);
						index = 0;
					}
					if (index != -1) {
						try {
							Mount mnt = MountFactory.create(builder.grabListSource_.getItem(index));
							if (builder.grabIsBooter_ && mnt instanceof ImageMount) {
								control.setText(((ImageMount)mnt).getImgPaths()[0].getPath());
							} else {
								if (mnt instanceof DirMount)
									control.setText(((DirMount)mnt).getPath().getPath() + File.separatorChar);
								else if (mnt instanceof ImageMount)
									control.setText(((ImageMount)mnt).getImgPaths()[0].getPath() + File.separatorChar);
								else if (mnt instanceof PhysFsMount)
									control.setText(((PhysFsMount)mnt).getPath().getPath() + File.separatorChar);
							}
							control.selectAll();
							control.setFocus();
						} catch (InvalidMountstringException e1) {
							// nothing we can do
						}
					}
				}
			};
		}

		if (builder.dispose_) {
			button_.addDisposeListener(e -> {
				button_.getImage().dispose();
				if (button_.getData("selectedImage") != null)
					((Image)(button_.getData("selectedImage"))).dispose();
			});
		}

		if (builder.listener_ != null)
			button_.addSelectionListener(builder.listener_);
	}

	public Button ctrl() {
		return button_;
	}

	private static String filterPath(Builder builder, TextControl_ control, Text altControl) {
		final String fieldValue = StringUtils.isBlank(control.getText()) && (altControl != null) ? altControl.getText(): control.getText();
		final String path;

		switch (builder.browseCanonicalType_) {
			case EXE:
				if (StringUtils.isNotBlank(fieldValue)) {
					File main = FilesUtils.determineMainFile(new File(fieldValue));
					if (main != null && main.getParentFile() != null && !main.getPath().equals(fieldValue) && !(main.getPath() + File.separator).equals(fieldValue)) {
						path = main.getParent();
						break;
					}
				}
				path = fieldValue;
				break;
			case DOC:
			case DBGLZIP:
			case NATIVE_EXE:
			case INSTALLER:
			case ZIP:
			case BOOTER:
			case DOSROOT:
			case DOSBOX:
			case DOSBOXEXE:
			case DOSBOXCONF:
			case GLSHADER:
			case NONE:
				path = fieldValue;
				break;
			case CDIMAGE:
				String[] fPaths = StringRelatedUtils.textAreaToStringArray(fieldValue, control.getLineDelimiter());
				path = (fPaths.length > 0) ? fPaths[0]: StringUtils.EMPTY;
				break;
			case DFEND:
				path = FileLocationService.DFEND_PATH_STRING;
				break;
			default:
				path = StringUtils.EMPTY;
		}

		return new FileLocation(path, builder.canonicalizer_).getCanonicalFile().getPath();
	}

	private static String getResult(Shell shell, Builder builder, TextControl_ textControl, String result, File filterPath, String[] filenames) {
		if (builder.browseCanonicalType_ == CanonicalType.DOSBOX && SystemUtils.IS_OSX) {
			File f = new FileLocation(result, builder.canonicalizer_).getFile();
			if (f.getName().endsWith(FileLocationService.DB_APP_EXT)) {
				File exe = new File(f, FileLocationService.DB_APP_EXE);
				if (FilesUtils.isExistingFile(exe)) {
					return exe.getParent();
				}
			} else if (f.getName().equals(FileLocationService.DOSBOX_EXE_STRING)) {
				return f.getParent();
			}
		} else if (builder.browseCanonicalType_ == CanonicalType.EXE || builder.browseCanonicalType_ == CanonicalType.INSTALLER) {
			if (FilesUtils.isArchive(result) || FilesUtils.isCdImageFile(result) || FilesUtils.isFatImage(result)) {
				BrowseArchiveDialog dialog = new BrowseArchiveDialog(shell, result);
				String choice = dialog.open();
				return (choice == null) ? null: new FileLocation(choice, builder.canonicalizer_).getFile().getPath();
			}
		} else if ((builder.browseCanonicalType_ == CanonicalType.CDIMAGE) && (filterPath != null)) {
			File path = new FileLocation(filterPath.getPath(), builder.canonicalizer_).getFile();
			StringBuilder images = new StringBuilder();
			for (String file: filenames) {
				images.append(FilesUtils.concat(path, file)).append(textControl.getLineDelimiter());
			}
			return images.toString();
		}

		return new FileLocation(result, builder.canonicalizer_).getFile().getPath();
	}

	public static Builder on(Composite composite) {
		return new Builder(composite);
	}

	public static final class Builder extends ControlBuilder<Builder> {
		private String text_;
		private Image image_;
		private boolean dispose_;
		private String tooltip_;
		private boolean selected_;
		private boolean enabled_ = true;
		private SelectionListener listener_;
		private BrowseType browseType_;
		private CanonicalType browseCanonicalType_;
		private boolean browseSave_;
		private ICanonicalize canonicalizer_;
		private List grabListSource_;
		private boolean grabIsBooter_;

		Builder(Composite composite) {
			super(composite, SWT.CHECK, SWT.FILL, SWT.CENTER, false, false);
		}

		public Builder tooltipTxt(String text) {
			tooltip_ = text;
			return this;
		}

		public Builder tooltip(String key) {
			return tooltipTxt(TextService.getInstance().get(key));
		}

		public Builder tooltip(String key, String param) {
			return tooltipTxt(TextService.getInstance().get(key, param));
		}

		public Builder txt(String text) {
			text_ = text;
			return this;
		}

		public Builder key(String key) {
			return txt(TextService.getInstance().get(key));
		}

		public Builder key(String key, String param) {
			return txt(TextService.getInstance().get(key, param));
		}

		public Builder select(boolean select) {
			selected_ = select;
			return this;
		}

		public Builder enable(boolean enable) {
			enabled_ = enable;
			return this;
		}

		public Builder text() {
			style_ = SWT.NONE;
			return this;
		}

		public Builder radio() {
			style_ = SWT.RADIO;
			return this;
		}

		public Builder toggle() {
			style_ = SWT.TOGGLE;
			return this;
		}

		public Builder threedots() {
			text();
			text_ = "...";
			return this;
		}

		public Builder star() {
			text();
			text_ = "*";
			return this;
		}

		public Builder arrow(boolean up) {
			style_ = SWT.ARROW | (up ? SWT.UP: SWT.DOWN);
			return this;
		}

		public Builder image(Image image, boolean dispose) {
			image_ = image;
			dispose_ = dispose;
			return text();
		}

		public Builder imageText(String path, String text) {
			text();
			int display = SettingsService.getInstance().getIntValue("gui", "buttondisplay");
			if (display != 1)
				image_ = ImageService.getResourceImage(composite_.getDisplay(), path);
			if (display == 2)
				tooltip(text);
			else
				key(text);
			return this;
		}

		public Builder imageText(String path, String text, String param) {
			text();
			int display = SettingsService.getInstance().getIntValue("gui", "buttondisplay");
			if (display != 1)
				image_ = ImageService.getResourceImage(composite_.getDisplay(), path);
			if (display == 2)
				tooltip(text, param);
			else
				key(text, param);
			return this;
		}

		public Builder browse(boolean small, BrowseType browse, CanonicalType canon, boolean save) {
			if (small) {
				threedots();
				tooltip("button.browse");
			} else {
				imageText(ImageService.IMG_FOLDER, "button.browse");
			}
			browseType_ = browse;
			browseCanonicalType_ = canon;
			browseSave_ = save;

			switch (browseCanonicalType_) {
				case DOC:
				case DBGLZIP:
				case NATIVE_EXE:
					canonicalizer_ = FileLocationService.getInstance().dataRelative();
					break;
				case EXE:
				case INSTALLER:
				case ZIP:
				case BOOTER:
				case DOSROOT:
				case CDIMAGE:
					canonicalizer_ = FileLocationService.getInstance().dosrootRelative();
					break;
				case DOSBOX:
				case DOSBOXEXE:
				case DOSBOXCONF:
					canonicalizer_ = FileLocationService.getInstance().dosboxRelative();
					break;
				case DFEND:
				case NONE:
				default:
					canonicalizer_ = FileLocationService.standard();
					break;
			}

			return this;
		}

		public Builder grab(List source, boolean isBooter) {
			imageText(ImageService.IMG_GRAB, "button.grab");
			grabListSource_ = source;
			grabIsBooter_ = isBooter;
			return this;
		}

		public Builder listen(SelectionListener listener) {
			listener_ = listener;
			return this;
		}

		public Button_ build() {
			return new Button_(this, null);
		}

		public Button_ build(TextControl_ textControl) {
			return new Button_(this, textControl);
		}

		public Button ctrl() {
			return build().ctrl();
		}

		public Button ctrl(TextControl_ textControl) {
			return build(textControl).ctrl();
		}
	}
}
