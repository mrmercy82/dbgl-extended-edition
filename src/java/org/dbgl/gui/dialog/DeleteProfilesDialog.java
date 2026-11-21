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
		// alpha 52: the backups of the saved games a choice of their own, not ticked: they stay (in savegames\deleted)
		Button deleteSavegames = Chain.on(composite).lbl(l -> l.key("dialog.deleteprofiles.confirm.removesavegames")).but(b -> b.select(false)).button();
		deleteSavegames.setToolTipText(text_.get("dialog.deleteprofiles.confirm.removesavegames.tooltip"));
		org.eclipse.swt.widgets.Control[] siblings = composite.getChildren(); // its label: the one before it
		int at = java.util.Arrays.asList(siblings).indexOf(deleteSavegames);
		if (at > 0)
			siblings[at - 1].setToolTipText(deleteSavegames.getToolTipText());
		org.eclipse.swt.widgets.Label keptHint = new org.eclipse.swt.widgets.Label(composite, org.eclipse.swt.SWT.WRAP);
		keptHint.setText(text_.get("dialog.deleteprofiles.savegames.kept"));
		org.eclipse.swt.layout.GridData hintData = new org.eclipse.swt.layout.GridData(org.eclipse.swt.SWT.FILL, org.eclipse.swt.SWT.BEGINNING, true, false, 2, 1);
		hintData.widthHint = 520;
		keptHint.setLayoutData(hintData);
		deleteSavegames.addListener(org.eclipse.swt.SWT.Selection, e -> {
			keptHint.setVisible(!deleteSavegames.getSelection());
			hintData.exclude = deleteSavegames.getSelection();
			composite.layout(true, true);
		});

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (deleteSavegames.getSelection() && !savegamesFirst())
					return; // alpha 52: cancelled, or the ZIP file could not be made: nothing deleted, the dialog stays open
				if (Mess_.on(shell_).key("dialog.deleteprofiles.confirm.removal", new Object[] {profilesToBeDeleted_.size()}).confirm()) {
					for (Profile prof: profilesToBeDeleted_) {
						try {
							new ProfileRepository().remove(prof, deleteConfs.getSelection(), deleteMapperfiles.getSelection(), deleteCaptures.getSelection(), deleteSavegames.getSelection());
						} catch (SQLException e) {
							Mess_.on(shell_).exception(e).warning();
						}
					}
					result_ = profilesToBeDeleted_;
				}
				shell_.close();
			}
		});

		// alpha 52: wide and high enough for its texts (550 cut off the check boxes in German, also the one for the screenshots)
		org.eclipse.swt.graphics.Point need = shell_.computeSize(org.eclipse.swt.SWT.DEFAULT, org.eclipse.swt.SWT.DEFAULT), now = shell_.getSize();
		org.eclipse.swt.graphics.Rectangle screen = shell_.getDisplay().getPrimaryMonitor().getClientArea();
		if ((need.x > now.x) || (need.y > now.y))
			shell_.setSize(Math.min(screen.width, Math.max(now.x, need.x)), Math.min(screen.height, Math.max(now.y, need.y)));
	}

	/**
	 * alpha 52: the backups of saved games are to be deleted too; if any of the profiles has some, a warning
	 * and the chance to keep them in a locked ZIP file in the folder of the backups first
	 *
	 * @return whether to go on deleting
	 */
	private boolean savegamesFirst() {
		org.dbgl.util.SavegameBackup.WithBackups with = org.dbgl.util.SavegameBackup.withBackups(profilesToBeDeleted_);
		if (with.profiles().isEmpty())
			return true;
		org.dbgl.service.SettingsService settings = org.dbgl.service.SettingsService.getInstance();
		java.util.Locale locale = java.util.Locale.of(settings.getValue("locale", "language"), settings.getValue("locale", "country"));
		String titles = with.profiles().stream().limit(8).map(p -> "\u2022 " + p.getTitle()).collect(java.util.stream.Collectors.joining("\n"))
				+ (with.profiles().size() > 8 ? "\n\u2022 ...": "");
		int answer = org.dbgl.gui.controls.Choice.ask(shell_, text_.get("dialog.deleteprofiles.savegames.title"),
			text_.get("dialog.deleteprofiles.savegames.message", new Object[] {with.profiles().size(), with.count(), org.dbgl.util.DiskUsage.format(with.size(), locale), titles}),
			org.eclipse.swt.SWT.ICON_WARNING, text_.get("dialog.deleteprofiles.savegames.backup"), text_.get("dialog.deleteprofiles.savegames.nobackup"),
			text_.get("button.cancel"));
		if (answer == 1)
			return true;
		if (answer != 0)
			return false;
		try {
			java.io.File zip = org.dbgl.gui.controls.BusyTask.runToEnd(shell_, text_.get("dialog.deleteprofiles.savegames.packing"),
				() -> org.dbgl.util.SavegameBackup.packBeforeDelete(with.profiles(), org.dbgl.service.BackupService.defaultAutoBackupDir()));
			if (zip != null)
				Mess_.on(shell_).key("dialog.deleteprofiles.savegames.packed", new Object[] {zip.getPath()}).display();
			return true;
		} catch (Exception e) {
			Mess_.on(shell_).exception(e).warning();
			return false;
		}
	}
}
