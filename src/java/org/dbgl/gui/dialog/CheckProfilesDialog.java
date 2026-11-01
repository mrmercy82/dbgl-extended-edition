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
import org.dbgl.util.PathRelocation;
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
	/** the ids of the profiles chosen in the main window, or null for all */
	private final java.util.Set<Integer> selected_;
	/** per profile: folders and files that do not exist */
	private final java.util.Map<Profile, List<String>> missing_ = new java.util.LinkedHashMap<>();

	public CheckProfilesDialog(Shell parent) {
		this(parent, null);
	}

	/** @param selected only these profiles are checked, or all if null */
	public CheckProfilesDialog(Shell parent, List<Profile> selected) {
		super(parent, "checkprofilesdialog");
		selected_ = selected == null ? null: selected.stream().map(Profile::getId).collect(java.util.stream.Collectors.toSet());
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
			if (selected_ != null)
				profiles_ = profiles_.stream().filter(p -> selected_.contains(p.getId())).toList();
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
		Button_.on(otherButtons_).text().key("dialog.checkprofiles.relocate").tooltip("dialog.checkprofiles.relocate.tooltip").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				relocateMissing();
			}
		}).ctrl();
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
		missing_.clear();
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
					List<String> missing = prof.listMissingPaths();
					if (!missing.isEmpty())
						missing_.put(prof, missing);
					for (String path: missing)
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

	/**
	 * Ask where the first missing folder or file is now. From that, the part of the path that changed is known (e.g. the drive
	 * letter or a renamed folder); it is changed in all profiles with missing paths, wherever the new path exists.
	 */
	private void relocateMissing() {
		if (missing_.isEmpty()) {
			Mess_.on(shell_).key("dialog.checkprofiles.nomissing").display();
			return;
		}
		String first = missing_.values().iterator().next().get(0);
		String question = text_.get("dialog.checkprofiles.relocate.where", new Object[] {first});
		String chosen;
		if (looksLikeFile(first)) {
			FileDialog dialog = new FileDialog(shell_, SWT.OPEN);
			dialog.setText(question);
			dialog.setFileName(new File(first).getName());
			chosen = dialog.open();
		} else {
			org.eclipse.swt.widgets.DirectoryDialog dialog = new org.eclipse.swt.widgets.DirectoryDialog(shell_);
			dialog.setText(question);
			dialog.setMessage(question);
			chosen = dialog.open();
		}
		if (chosen == null)
			return;
		String[] prefixes = PathRelocation.changedPrefixes(first, chosen);

		java.util.Map<Profile, java.util.Map<String, String>> changes = new java.util.LinkedHashMap<>();
		int count = 0;
		for (java.util.Map.Entry<Profile, List<String>> e: missing_.entrySet()) {
			java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
			for (String path: e.getValue()) {
				String moved = PathRelocation.relocate(path, prefixes[0], prefixes[1]);
				if ((moved != null) && new File(moved).exists())
					map.put(path, moved);
			}
			if (!map.isEmpty()) {
				changes.put(e.getKey(), map);
				count += map.size();
			}
		}
		if (changes.isEmpty()) {
			Mess_.on(shell_).key("dialog.checkprofiles.relocate.nothing", new Object[] {prefixes[0], prefixes[1]}).warning();
			return;
		}
		if (!Mess_.on(shell_).key("dialog.checkprofiles.relocate.confirm", new Object[] {count, changes.size(), prefixes[0], prefixes[1]}).confirm())
			return;
		int changed = 0;
		for (java.util.Map.Entry<Profile, java.util.Map<String, String>> e: changes.entrySet()) {
			Profile prof = e.getKey();
			try {
				prof.resetAndLoadConfiguration();
				String conf = prof.getConfigurationString();
				String updated = PathRelocation.replaceInConfiguration(conf, e.getValue(), org.dbgl.service.FileLocationService.getInstance().getDosroot());
				if (!updated.equals(conf)) {
					prof.loadConfigurationData(org.dbgl.service.TextService.getInstance(), updated, prof.getConfigurationCanonicalFile());
					prof.saveConfiguration();
					changed++;
				}
			} catch (IOException ex) {
				Mess_.on(shell_).exception(ex).warning();
			}
		}
		result_ = true;
		check();
		Mess_.on(shell_).key("dialog.checkprofiles.relocate.done", new Object[] {changed}).display();
	}

	private static boolean looksLikeFile(String path) {
		String name = new File(path).getName();
		int dot = name.lastIndexOf('.');
		return (dot > 0) && (dot >= name.length() - 4);
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
