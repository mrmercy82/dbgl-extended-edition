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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.List;

import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.util.ProfileHistory;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Shell;


/** shows the earlier states of a profile's configuration, what differs from now, and brings one back */
public class ProfileHistoryDialog extends SizeControlledButtonDialog<Boolean> {

	private final Profile profile_;
	private List<File> versions_;
	private org.eclipse.swt.widgets.List list_;
	private org.eclipse.swt.custom.StyledText diff_;
	private Font font_;

	public ProfileHistoryDialog(Shell parent, Profile profile) {
		super(parent, "profilehistorydialog");
		profile_ = profile;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.profilehistory.title", new Object[] {profile_.getTitle()});
	}

	@Override
	protected boolean prepare() {
		versions_ = new java.util.ArrayList<>(ProfileHistory.list(profile_.getId()));
		File original = ProfileHistory.original(profile_.getId());
		if (original != null)
			versions_.add(original); // last: the oldest
		if (versions_.isEmpty()) {
			Mess_.on(getParent()).key("dialog.profilehistory.none").display();
			return false;
		}
		return true;
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));
		SashForm sash = new SashForm(contents_, SWT.HORIZONTAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		list_ = new org.eclipse.swt.widgets.List(sash, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
		versions_.forEach(f -> list_.add(f.getName().equals(ProfileHistory.ORIGINAL)
				? text_.get("dialog.profilehistory.original", new Object[] {df.format(new java.util.Date(f.lastModified()))})
				: df.format(ProfileHistory.dateOf(f))));
		// as the preview of "Check profiles" and switching the DOSBox version: each section in a line, below it its values in color
		diff_ = new org.eclipse.swt.custom.StyledText(sash, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		font_ = new Font(shell_.getDisplay(), SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		diff_.setFont(font_);
		shell_.addDisposeListener(e -> font_.dispose());
		sash.setWeights(new int[] {30, 70});
		list_.addListener(SWT.Selection, e -> showDiff());
		list_.select(0);
		showDiff();

		createGoCancelButtons("dialog.profilehistory.restore", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				restore();
			}
		});
	}

	private void showDiff() {
		int i = list_.getSelectionIndex();
		if (i < 0)
			return;
		try {
			String old = Files.readString(versions_.get(i).toPath(), StandardCharsets.UTF_8);
			byte[] now = ProfileHistory.read(profile_);
			String current = now == null ? "": new String(now, StandardCharsets.UTF_8);
			List<String> lines = SwitchDosboxVersionDialog.grouped(current, old);
			org.eclipse.swt.widgets.Display d = shell_.getDisplay();
			List<org.eclipse.swt.custom.StyleRange> styles = new java.util.ArrayList<>();
			StringBuilder sb = new StringBuilder();
			java.util.function.BiConsumer<String, org.eclipse.swt.graphics.Color> add = (line, color) -> {
				if (color != null)
					styles.add(new org.eclipse.swt.custom.StyleRange(sb.length(), line.length(), color, null));
				sb.append(line).append('\n');
			};
			add.accept(text_.get("dialog.profilehistory.legend"), d.getSystemColor(SWT.COLOR_DARK_GRAY));
			add.accept("", null);
			if (lines.stream().noneMatch(l -> l.startsWith("- ") || l.startsWith("+ ")))
				add.accept(text_.get("dialog.profilehistory.same"), null);
			else
				for (String l: lines)
					add.accept(l, l.startsWith("- ") ? d.getSystemColor(SWT.COLOR_DARK_RED): l.startsWith("+ ") ? d.getSystemColor(SWT.COLOR_DARK_GREEN): d.getSystemColor(SWT.COLOR_DARK_GRAY));
			diff_.setText(sb.toString());
			diff_.setStyleRanges(styles.toArray(new org.eclipse.swt.custom.StyleRange[0]));
		} catch (IOException e) {
			diff_.setText(String.valueOf(e.getMessage()));
		}
	}

	private void restore() {
		int i = list_.getSelectionIndex();
		if ((i < 0) || !Mess_.on(shell_).key("dialog.profilehistory.confirm", new Object[] {list_.getItem(i)}).confirm())
			return;
		try {
			byte[] before = ProfileHistory.read(profile_);
			Files.copy(versions_.get(i).toPath(), profile_.getConfigurationCanonicalFile().toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			ProfileHistory.recordIfChanged(profile_, before); // the state before restoring can be brought back too
			String warnings = profile_.resetAndLoadConfiguration();
			new ProfileRepository().updateKeepingConfigurationFile(profile_); // the file byte for byte as it was then
			if ((warnings != null) && !warnings.isBlank())
				Mess_.on(shell_).txt(warnings).warning();
			result_ = true;
			shell_.close();
		} catch (IOException | SQLException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}
}
