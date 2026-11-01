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
package org.dbgl.gui.dialog.wizard;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.IntStream;

import org.dbgl.gui.abstractdialog.JobWizardDialog;
import org.dbgl.gui.abstractdialog.ExtraSettings;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Group_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.Text_;
import org.dbgl.gui.thread.SearchEngineThread;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.util.SystemUtils;
import org.dbgl.util.searchengine.WebSearchEngine;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;


public class UpdateMultiProfileDialog extends JobWizardDialog<Boolean> {

	private final List<Profile> orgProfs_, profs_;
	private final boolean dbversionChanged_, templateReloaded_;
	private final List<Chain> metaControls_;
	private final Chain nativeCommandControl_;
	private final List<ExtraSettings.Change> extraChanges_;
	private final WebSearchEngine engine_;

	public UpdateMultiProfileDialog(Shell parent, List<Profile> orgProfs, List<Profile> profs, boolean dbversionChanged, boolean templateReloaded, List<Chain> metaControls, Chain nativeCommandControl,
			List<ExtraSettings.Change> extraChanges, WebSearchEngine engine) {
		super(parent, "multiprofiledialog");
		orgProfs_ = orgProfs;
		profs_ = profs;
		dbversionChanged_ = dbversionChanged;
		templateReloaded_ = templateReloaded;
		metaControls_ = metaControls.stream().filter(Chain::hasChangedValue).toList();
		nativeCommandControl_ = nativeCommandControl.hasChangedValue() ? nativeCommandControl: null;
		extraChanges_ = extraChanges;
		engine_ = engine;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.multiprofile.title.edit", new Object[] {profs_.size()});
	}

	@Override
	protected void onShellCreated() {
		StringBuilder info = new StringBuilder();
		if (dbversionChanged_) {
			long count = IntStream.range(0, profs_.size()).filter(x -> profs_.get(x).getDosboxVersion().getId() != orgProfs_.get(x).getDosboxVersion().getId()).count();
			info.append(text_.get("dialog.multiprofile.dosboxversionchanged", new Object[] {profs_.get(0).getDosboxVersion().getTitle(), count})).append(SystemUtils.EOLN);
		}
		if (templateReloaded_) {
			info.append(text_.get("dialog.multiprofile.templateloaded")).append(SystemUtils.EOLN);
		}
		String conflicting = text_.get("dialog.multiprofile.conflictingvalues");
		if (nativeCommandControl_ != null) {
			info.append(nativeCommandControl_.toString()).append(": ").append(
				nativeCommandControl_.conflictingValues() ? conflicting: '"' + nativeCommandControl_.getInitialNativeCommandsAsString() + '"').append(" -> ").append(
					'"' + nativeCommandControl_.getCurrentNativeCommandsAsString() + '"').append(SystemUtils.EOLN);
		}
		metaControls_.forEach(x -> info.append(x.toString()).append(": ").append(x.conflictingValues() ? conflicting: '"' + x.getInitialStringValue() + '"').append(" -> ").append(
			'"' + x.getCurrentStringValueForDisplay() + '"').append(SystemUtils.EOLN));
		String defaultText = text_.get("dialog.template.moresettings.default");
		extraChanges_.forEach(x -> info.append('[').append(x.section()).append("] ").append(x.item()).append(": ").append(x.conflicting() && x.oldValue().isEmpty() ? conflicting: '"' + x.oldValue() + '"')
				.append(" -> ").append(x.newValue().isEmpty() ? defaultText: '"' + x.newValue() + '"').append(SystemUtils.EOLN));

		if (!dbversionChanged_ && !templateReloaded_ && (nativeCommandControl_ == null) && metaControls_.isEmpty() && extraChanges_.isEmpty()) {
			info.append(text_.get("dialog.multiprofile.notice.nochanges")).append(SystemUtils.EOLN);
			nextButton_.setEnabled(false);
		}

		Group infoGroup = Group_.on(shell_).layout(new GridLayout()).key("dialog.multiprofile.reviewchanges").build();
		Text_.on(infoGroup).multi().readOnly().val(info.toString()).build();
		addStep(infoGroup);

		addFinalStep("dialog.dfendimport.progress", "dialog.multiprofile.applychanges");
		progressBar_.setMaximum(profs_.size());
	}

	@Override
	protected boolean onNext(int step) {
		if (step == 1) {
			for (Profile prof: profs_) {
				Configuration combinedConf = prof.getCombinedConfiguration();
				metaControls_.forEach(x -> x.updateConfigurableByControl(prof, combinedConf));
				if (nativeCommandControl_ != null)
					nativeCommandControl_.updateConfigurableByControl(prof, combinedConf);
				ExtraSettings.apply(extraChanges_, prof);

				try {
					new ProfileRepository().update(prof);
				} catch (SQLException e) {
					log_.append(e.toString());
					e.printStackTrace();
				}

				progressBar_.setSelection(progressBar_.getSelection() + 1);
			}

			progressBar_.setSelection(0);
			job_ = new SearchEngineThread(profs_, engine_, log_, progressBar_, status_);
		} else if (step == 2) {
			if (!job_.isEverythingOk())
				Mess_.on(shell_).key("dialog.multiprofile.error.problem").warning();

			status_.setText(text_.get("dialog.multiprofile.reviewlog"));
			status_.pack();

			result_ = true;
		}
		return true;
	}
}
