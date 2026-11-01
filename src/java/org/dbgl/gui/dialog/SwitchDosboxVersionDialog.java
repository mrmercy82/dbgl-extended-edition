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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
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
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


/**
 * Switches all profiles of one DOSBox version to another one (like 'Switch' in the profile dialog, so settings are converted),
 * with a preview and a report of what changed in each profile. With a profile given, only this one profile is switched.
 */
public class SwitchDosboxVersionDialog extends SizeControlledButtonDialog<Boolean> {

	private List<DosboxVersion> dbversions_;
	private List<Profile> profiles_;
	private Combo from_, to_;
	private Label count_;
	private Text report_;
	private Font font_;
	/** the ids of the profiles chosen in the main window, or null for all profiles of a DOSBox version */
	private final java.util.Set<Integer> selected_;

	public SwitchDosboxVersionDialog(Shell parent) {
		this(parent, (List<Profile>)null);
	}

	/** @param single only this profile is switched, or all profiles of a DOSBox version if null */
	public SwitchDosboxVersionDialog(Shell parent, Profile single) {
		this(parent, single == null ? null: List.of(single));
	}

	/** @param selected only these profiles are switched, or all profiles of a DOSBox version if null */
	public SwitchDosboxVersionDialog(Shell parent, List<Profile> selected) {
		super(parent, "switchdosboxdialog");
		selected_ = selected == null ? null: selected.stream().map(Profile::getId).collect(java.util.stream.Collectors.toSet());
	}

	@Override
	protected String getDialogTitle() {
		return text_.get((selected_ == null) || (selected_.size() > 1) ? "dialog.switchdosbox.title": "dialog.switchdosbox.title.single");
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
		contents_.setLayout(new GridLayout(2, false));
		String[] titles = dbversions_.stream().map(DosboxVersion::getTitle).toArray(String[]::new);
		from_ = Chain.on(contents_).lbl(l -> l.key("dialog.switchdosbox.from")).cmb(c -> c.items(titles).visibleItemCount(15)).combo();
		to_ = Chain.on(contents_).lbl(l -> l.key("dialog.switchdosbox.to")).cmb(c -> c.items(titles).visibleItemCount(15)).combo();
		new Label(contents_, SWT.NONE);
		count_ = new Label(contents_, SWT.NONE);
		count_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		report_ = new Text(contents_, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		report_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		font_ = new Font(shell_.getDisplay(), SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		report_.setFont(font_);
		shell_.addDisposeListener(e -> font_.dispose());

		// preselect: from the version with the most profiles (or the version of the single profile), to the newest one
		int best = 0;
		for (int i = 0; i < dbversions_.size(); i++)
			if (profilesOf(dbversions_.get(i)).size() > profilesOf(dbversions_.get(best)).size())
				best = i;
		if (!dbversions_.isEmpty()) {
			from_.select(best);
			to_.select(dbversions_.size() - 1);
			if ((selected_ != null) && (to_.getSelectionIndex() == best) && (dbversions_.size() > 1))
				to_.select(dbversions_.size() - 2);
		}
		from_.setEnabled(selected_ == null);
		SelectionAdapter update = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateCount();
			}
		};
		from_.addSelectionListener(update);
		to_.addSelectionListener(update);
		updateCount();

		createGoCancelButtons("dialog.switchdosbox.switch", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				run(false);
			}
		});
		Button_.on(otherButtons_).text().key("dialog.switchdosbox.preview").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				run(true);
			}
		}).ctrl();
		Button_.on(otherButtons_).text().key("dialog.switchdosbox.savereport").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				saveReport();
			}
		}).ctrl();
	}

	private List<Profile> profilesOf(DosboxVersion dbversion) {
		return profiles_.stream().filter(p -> (p.getDosboxVersion().getId() == dbversion.getId()) && ((selected_ == null) || selected_.contains(p.getId()))).toList();
	}

	private List<Profile> selectedProfiles() {
		return profiles_.stream().filter(p -> selected_.contains(p.getId())).toList();
	}

	private void updateCount() {
		if ((from_.getSelectionIndex() < 0) || (to_.getSelectionIndex() < 0))
			return;
		if ((selected_ != null) && (selected_.size() == 1) && !selectedProfiles().isEmpty())
			count_.setText(text_.get("dialog.switchdosbox.single", new Object[] {selectedProfiles().get(0).getTitle()}));
		else if (selected_ != null)
			count_.setText(text_.get("dialog.switchdosbox.selected", new Object[] {selected_.size()}));
		else
			count_.setText(text_.get("dialog.switchdosbox.count", new Object[] {profilesOf(dbversions_.get(from_.getSelectionIndex())).size()}));
		count_.getParent().layout();
	}

	private void run(boolean preview) {
		if ((from_.getSelectionIndex() < 0) || (to_.getSelectionIndex() < 0))
			return;
		DosboxVersion src = dbversions_.get(from_.getSelectionIndex());
		DosboxVersion dst = dbversions_.get(to_.getSelectionIndex());
		List<Profile> profiles;
		String srcTitle;
		if (selected_ == null) {
			if (src.getId() == dst.getId()) {
				Mess_.on(shell_).key("dialog.switchdosbox.error.same").warning();
				return;
			}
			profiles = profilesOf(src);
			srcTitle = src.getTitle();
			if (profiles.isEmpty()) {
				Mess_.on(shell_).key("dialog.switchdosbox.error.noprofiles").warning();
				return;
			}
		} else {
			// the chosen profiles, which may use different DOSBox versions
			profiles = selectedProfiles().stream().filter(p -> p.getDosboxVersion().getId() != dst.getId()).toList();
			srcTitle = profiles.stream().map(p -> p.getDosboxVersion().getTitle()).distinct().collect(java.util.stream.Collectors.joining(", "));
			if (profiles.isEmpty()) {
				Mess_.on(shell_).key("dialog.switchdosbox.error.same").warning();
				return;
			}
		}
		if (!preview && !Mess_.on(shell_).key("dialog.switchdosbox.confirm", new Object[] {profiles.size(), srcTitle, dst.getTitle()}).confirm())
			return;

		shell_.setCursor(shell_.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
		StringBuilder report = new StringBuilder();
		report.append(text_.get(preview ? "dialog.switchdosbox.report.preview": "dialog.switchdosbox.report.done", new Object[] {srcTitle, dst.getTitle()})).append(Text.DELIMITER).append(Text.DELIMITER);
		int changed = 0, failed = 0;
		try {
			for (Profile prof: profiles) {
				report.append("== ").append(prof.getTitle()).append(" (").append(prof.getId()).append(')').append(Text.DELIMITER);
				DosboxVersion orig = prof.getDosboxVersion();
				try {
					String warnings = prof.resetAndLoadConfiguration();
					String before = prof.getConfiguration().toString(null);
					warnings += prof.switchToDosboxVersion(dst);
					String after = prof.getConfiguration().toString(null);
					List<String> diff = diff(before, after);
					if (diff.isEmpty())
						report.append("   ").append(text_.get("dialog.switchdosbox.report.nochanges")).append(Text.DELIMITER);
					else
						changed++;
					diff.forEach(l -> report.append("   ").append(l).append(Text.DELIMITER));
					if (StringUtils.isNotBlank(warnings))
						report.append("   ! ").append(warnings.trim().replace("\n", Text.DELIMITER + "   ! ")).append(Text.DELIMITER);
					if (!preview)
						new ProfileRepository().update(prof);
					else
						prof.setDosboxVersion(orig); // in memory only, nothing saved
				} catch (IOException | SQLException e) {
					failed++;
					report.append("   ! ").append(e.getMessage()).append(Text.DELIMITER);
				}
				report.append(Text.DELIMITER);
			}
		} finally {
			shell_.setCursor(null);
		}
		report.append(text_.get("dialog.switchdosbox.report.summary", new Object[] {profiles.size(), changed, failed}));
		report_.setText(report.toString());
		if (!preview) {
			result_ = true;
			try {
				profiles_ = new ProfileRepository().list(StringUtils.EMPTY, StringUtils.EMPTY, dbversions_);
			} catch (SQLException e) {
				Mess_.on(shell_).exception(e).warning();
			}
			if (selected_ != null) // the profiles now use the new DOSBox version
				from_.select(to_.getSelectionIndex());
			updateCount();
		}
	}

	/** lines removed (-) and added (+), ignoring order */
	public static List<String> diff(String before, String after) {
		Set<String> b = new LinkedHashSet<>(Arrays.asList(before.split("\\R")));
		Set<String> a = new LinkedHashSet<>(Arrays.asList(after.split("\\R")));
		List<String> result = new ArrayList<>();
		String section = null;
		for (String l: before.split("\\R")) {
			if (l.startsWith("["))
				section = l;
			else if (StringUtils.isNotBlank(l) && !a.contains(l))
				result.add("- " + (section != null ? section + " ": "") + l);
		}
		section = null;
		for (String l: after.split("\\R")) {
			if (l.startsWith("["))
				section = l;
			else if (StringUtils.isNotBlank(l) && !b.contains(l))
				result.add("+ " + (section != null ? section + " ": "") + l);
		}
		return result;
	}

	private void saveReport() {
		if (StringUtils.isBlank(report_.getText()))
			return;
		FileDialog dialog = new FileDialog(shell_, SWT.SAVE);
		dialog.setFilterExtensions(new String[] {"*.txt", "*.*"});
		dialog.setFileName("dbgl-switch-report.txt");
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
