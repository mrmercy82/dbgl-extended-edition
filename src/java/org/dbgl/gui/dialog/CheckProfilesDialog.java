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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


/**
 * Checks all profiles: values the DOSBox version does not allow (as documented in its configuration file), and folders or
 * images that do not exist. Invalid values can be removed, so the DOSBox default applies.
 */
public class CheckProfilesDialog extends SizeControlledButtonDialog<Boolean> {

	private List<DosboxVersion> dbversions_;
	private List<Profile> profiles_;
	private Text report_;
	private Font font_;
	private int invalidValues_, invalidProfiles_;

	public CheckProfilesDialog(Shell parent) {
		super(parent, "checkprofilesdialog");
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.checkprofiles.title");
	}

	@Override
	protected boolean prepare() {
		try {
			dbversions_ = new DosboxVersionRepository().listAll();
			profiles_ = new ProfileRepository().list(StringUtils.EMPTY, StringUtils.EMPTY, dbversions_);
			return true;
		} catch (SQLException e) {
			Mess_.on(getParent()).exception(e).warning();
			return false;
		}
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));
		report_ = new Text(contents_, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		report_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		font_ = new Font(shell_.getDisplay(), SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		report_.setFont(font_);
		shell_.addDisposeListener(e -> font_.dispose());

		createGoCancelButtons("dialog.checkprofiles.fix", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				fix();
			}
		});
		Button_.on(otherButtons_).text().key("dialog.switchdosbox.savereport").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				saveReport();
			}
		}).ctrl();
		check();
	}

	private void check() {
		shell_.setCursor(shell_.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
		StringBuilder report = new StringBuilder();
		invalidValues_ = 0;
		invalidProfiles_ = 0;
		int withProblems = 0;
		try {
			for (Profile prof: profiles_) {
				StringBuilder lines = new StringBuilder();
				try {
					String warnings = prof.resetAndLoadConfiguration();
					List<String[]> invalid = prof.listInvalidValues();
					for (String[] v: invalid)
						lines.append("   [").append(v[0]).append("] ").append(v[1]).append('=').append(v[2]).append(": ").append(
							text_.get("dialog.checkprofiles.invalid", new Object[] {v[3]})).append(Text.DELIMITER);
					for (String path: prof.listMissingPaths())
						lines.append("   ").append(text_.get("dialog.checkprofiles.missing", new Object[] {path})).append(Text.DELIMITER);
					if (StringUtils.isNotBlank(warnings))
						lines.append("   ! ").append(warnings.trim().replace("\n", Text.DELIMITER + "   ! ")).append(Text.DELIMITER);
					if (!invalid.isEmpty()) {
						invalidValues_ += invalid.size();
						invalidProfiles_++;
					}
				} catch (IOException e) {
					lines.append("   ! ").append(e.getMessage()).append(Text.DELIMITER);
				}
				if (lines.length() > 0) {
					withProblems++;
					report.append("== ").append(prof.getTitle()).append(" (").append(prof.getDosboxVersion().getTitle()).append(')').append(Text.DELIMITER).append(lines).append(Text.DELIMITER);
				}
			}
		} finally {
			shell_.setCursor(null);
		}
		if (withProblems == 0)
			report.append(text_.get("dialog.checkprofiles.ok")).append(Text.DELIMITER).append(Text.DELIMITER);
		report.append(text_.get("dialog.checkprofiles.summary", new Object[] {profiles_.size(), withProblems}));
		report_.setText(report.toString());
	}

	private void fix() {
		if (invalidValues_ == 0) {
			Mess_.on(shell_).key("dialog.checkprofiles.ok").display();
			return;
		}
		if (!Mess_.on(shell_).key("dialog.checkprofiles.confirmfix", new Object[] {invalidValues_, invalidProfiles_}).confirm())
			return;
		int removed = 0;
		for (Profile prof: profiles_) {
			try {
				prof.resetAndLoadConfiguration();
				List<String[]> invalid = prof.listInvalidValues();
				if (invalid.isEmpty())
					continue;
				for (String[] v: invalid)
					prof.getConfiguration().removeValue(v[0], v[1]);
				new ProfileRepository().update(prof);
				removed += invalid.size();
			} catch (IOException | SQLException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		}
		result_ = true;
		check();
		Mess_.on(shell_).key("dialog.checkprofiles.fixed", new Object[] {removed}).display();
	}

	private void saveReport() {
		if (StringUtils.isBlank(report_.getText()))
			return;
		FileDialog dialog = new FileDialog(shell_, SWT.SAVE);
		dialog.setFilterExtensions(new String[] {"*.txt", "*.*"});
		dialog.setFileName("dbgl-profile-check.txt");
		dialog.setOverwrite(true);
		String file = dialog.open();
		if (file != null) {
			try {
				Files.writeString(new File(file).toPath(), report_.getText(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		}
	}
}
