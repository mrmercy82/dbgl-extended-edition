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
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.lang3.StringUtils;

import org.dbgl.gui.abstractdialog.SizeControlledTabbedDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.FileLocation;
import org.dbgl.model.ICanonicalize;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ImportExportProfilesService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


public class ExportListDialog extends SizeControlledTabbedDialog<Object> {

	private String[] xslBaseNames_;
	private final List<Profile> profiles_;

	public ExportListDialog(Shell parent, List<Profile> profs) {
		super(parent, "exportlistdialog");
		profiles_ = profs;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.exportlist.title");
	}

	@Override
	protected boolean prepare() {
		if (FileLocationService.getInstance().hasXslDir()) {
			xslBaseNames_ = FileLocationService.getInstance().listXslBaseNames();
			if (xslBaseNames_.length == 0) {
				Mess_.on(getParent()).key("dialog.exportlist.error.noxslfiles").fatal();
				return false;
			}
		} else {
			Mess_.on(getParent()).key("dialog.exportlist.error.noxsldir").fatal();
			return false;
		}
		return true;
	}

	@Override
	protected void onShellCreated() {
		Composite composite = createTabWithComposite("dialog.exportlist.options", 3);

		Combo fileTypes = Chain.on(composite).lbl(l -> l.key("dialog.exportlist.exportfiletype")).cmb(c -> c.horSpan(2).items(xslBaseNames_)).combo();
		Chain chn = Chain.on(composite).lbl(l -> l.key("dialog.exportlist.filename")).txt(t -> t.val(FileLocationService.EXPORT_DIR_STRING + "dbgllist")).but(
			b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.DOC, true)).build();
		Text filename = chn.getText();
		ICanonicalize canonicalizer = (ICanonicalize)chn.getButton().getData(Button_.DATA_CANONICALIZER);
		Button saveXml = Chain.on(composite).lbl(l -> l.key("dialog.exportlist.exportintermediatexml")).but(b -> b).button();

		fileTypes.setFocus();
		fileTypes.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				String file = filename.getText();
				String type = fileTypes.getItem(fileTypes.getSelectionIndex());
				int usi = type.lastIndexOf('_');
				if (usi != -1) {
					type = type.substring(usi + 1);
				}
				int index = file.lastIndexOf('.');
				if (index == -1) {
					filename.setText(file + '.' + type);
				} else {
					filename.setText(file.substring(0, index + 1) + type);
				}
			}
		});

		fileTypes.select(0);
		fileTypes.notifyListeners(SWT.Selection, new Event());

		createGoCancelButtons("dialog.exportlist.startexport", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (!isValid(filename, canonicalizer)) {
					return;
				}
				try {
					File exportFile = new FileLocation(filename.getText(), canonicalizer).getCanonicalFile();
					File xmlFile = saveXml.getSelection() ? new File(exportFile + FilesUtils.XML_EXT): null;
					File xsltFile = FileLocationService.getInstance().xslBaseNameToFile(fileTypes.getItem(fileTypes.getSelectionIndex()));
					ImportExportProfilesService.export(profiles_, xmlFile, xsltFile, exportFile);
					if (Mess_.on(shell_).key("dialog.exportlist.confirm.viewexport").confirm()) {
						SystemUtils.openForBrowsing(exportFile.getPath(), getParent());
					}
				} catch (IOException | TransformerException | ParserConfigurationException e) {
					Mess_.on(shell_).exception(e).warning();
				}
			}
		});
	}

	private boolean isValid(Text filename, ICanonicalize canonicalizer) {
		Mess_.Builder mess = Mess_.on(shell_);
		String file = filename.getText();
		File target = new FileLocation(file, canonicalizer).getCanonicalFile();
		if (StringUtils.isBlank(file)) {
			mess.key("dialog.exportlist.required.filename").bind(filename);
		} else if (FilesUtils.isExistingFile(target)) {
			if (!Mess_.on(shell_).key("dialog.exportlist.confirm.overwrite", new Object[] {target}).confirm()) {
				mess.key("dialog.exportlist.notice.anotherfilename").bind(filename);
			}
		} else {
			File dir = target.getParentFile();
			if (dir == null || !dir.exists()) {
				mess.key("dialog.exportlist.error.dirmissing").bind(filename);
			}
		}
		return mess.valid();
	}
}
