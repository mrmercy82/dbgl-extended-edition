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

import java.sql.SQLException;
import java.util.List;

import org.dbgl.gui.abstractdialog.SizeControlledTabbedDialog;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.ProfileRepository;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;


public class DeleteProfilesDialog extends SizeControlledTabbedDialog<List<Profile>> {

	private final List<Profile> profilesToBeDeleted_;

	public DeleteProfilesDialog(Shell parent, List<Profile> profs) {
		super(parent, "profiledeletedialog");
		profilesToBeDeleted_ = profs;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.deleteprofiles.title", new Object[] {profilesToBeDeleted_.size()});
	}

	@Override
	protected void onShellCreated() {
		Composite composite = createTabWithComposite("dialog.deleteprofiles.options", 2);

		Chain.on(composite).lbl(l -> l.key("dialog.deleteprofiles.confirm.removedatabaseentry")).but(b -> b.enable(false).select(true)).build();
		Button deleteConfs = Chain.on(composite).lbl(l -> l.key("dialog.deleteprofiles.confirm.removeprofileconf")).but(b -> b.select(true)).button();
		Button deleteMapperfiles = Chain.on(composite).lbl(l -> l.key("dialog.deleteprofiles.confirm.removemapperfile")).but(b -> b.select(true)).button();
		Button deleteCaptures = Chain.on(composite).lbl(l -> l.key("dialog.deleteprofiles.confirm.removeprofilecaptures")).but(b -> b.select(true)).button();

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (Mess_.on(shell_).key("dialog.deleteprofiles.confirm.removal", new Object[] {profilesToBeDeleted_.size()}).confirm()) {
					for (Profile prof: profilesToBeDeleted_) {
						try {
							new ProfileRepository().remove(prof, deleteConfs.getSelection(), deleteMapperfiles.getSelection(), deleteCaptures.getSelection());
						} catch (SQLException e) {
							Mess_.on(shell_).exception(e).warning();
						}
					}
					result_ = profilesToBeDeleted_;
				}
				shell_.close();
			}
		});
	}
}
