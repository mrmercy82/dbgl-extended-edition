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

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.EditProfileDialog;
import org.dbgl.gui.abstractdialog.ExtraSettings;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.dialog.wizard.UpdateMultiProfileDialog;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.entity.TemplateProfileBase;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.util.SystemUtils;
import org.dbgl.util.searchengine.WebSearchEngine;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;


public class EditMultiProfileDialog extends EditProfileDialog<List<Profile>> {

	private static final boolean MULTI_EDIT = true;

	private List<Profile> profiles_, orgProfiles_;
	private Profile profile_;
	private boolean dbversionChanged_, templateReloaded_;

	public EditMultiProfileDialog(Shell parent, List<Profile> profiles) {
		super(parent);
		profiles_ = profiles;
		orgProfiles_ = profiles_.stream().map(ProfileFactory::createCopy).toList();
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.multiprofile.title.edit", new Object[] {profiles_.size()});
	}

	@Override
	protected boolean prepare() {
		if (!super.prepare())
			return false;

		try {
			StringBuilder warningsLog = new StringBuilder();

			profile_ = ProfileFactory.combine(dbversionsList_, profiles_, warningsLog);
			extras_.setConflictingValues(this::hasDifferingValues);
			dbversionIndex_ = BaseRepository.indexOf(dbversionsList_, profile_.getDosboxVersion());
			templateIndex_ = -1;

			if (StringUtils.isNotEmpty(warningsLog)) {
				Mess_.on(getParent()).txt(warningsLog.toString()).warning();
			}
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	/** @param key "section/item" of a setting: true if the selected profiles don't all have the same value */
	private boolean hasDifferingValues(String key) {
		int slash = key.indexOf('/');
		String section = key.substring(0, slash), item = key.substring(slash + 1);
		return profiles_.stream().map(p -> StringUtils.strip(StringUtils.defaultString(p.getConfiguration().getValue(section, item)))).distinct().count() > 1;
	}

	@Override
	protected void doPerformDosboxConfAction(DosboxConfAction action, DosboxVersion newDosboxVersion) {
		try {
			StringBuilder warningsLog = new StringBuilder();
			List<ExtraSettings.Change> extraChanges = extras_.changes();

			for (Profile prof: profiles_) {
				computeAllPages(profile_);
				
				Configuration combinedConf = prof.getCombinedConfiguration();
				metaControls_.stream().filter(Chain::hasChangedValue).forEach(x -> x.updateConfigurableByControl(prof, combinedConf));
				Stream.of(nativeCommandControl_).filter(Chain::hasChangedValue).forEach(x -> x.updateConfigurableByControl(prof, combinedConf));
				ExtraSettings.apply(extraChanges, prof);

				warningsLog.append(newDosboxVersion.resetAndLoadConfiguration());

				if (action == DosboxConfAction.SET) {
					warningsLog.append(prof.setToDosboxVersion(newDosboxVersion));
					dbversionChanged_ = true;
				} else if (action == DosboxConfAction.SWITCH) {
					warningsLog.append(prof.switchToDosboxVersion(newDosboxVersion));
					dbversionChanged_ = true;
				} else if (action == DosboxConfAction.RELOAD) {
					warningsLog.append(prof.reloadDosboxVersion(newDosboxVersion));
					dbversionChanged_ = true;
				} else if (action == DosboxConfAction.LOAD_TEMPLATE) {
					Template templ = templatesList_.get(templateCombo_.getSelectionIndex());
					templ.setDosboxVersion(newDosboxVersion);
					warningsLog.append(templ.resetAndLoadConfiguration());
					warningsLog.append(prof.loadTemplate(newDosboxVersion, templ));
				} else if (action == DosboxConfAction.RELOAD_TEMPLATE) {
					Template templ = templatesList_.get(templateCombo_.getSelectionIndex());
					templ.setDosboxVersion(newDosboxVersion);
					warningsLog.append(templ.resetAndLoadConfiguration());
					warningsLog.append(prof.reloadTemplate(newDosboxVersion, templ));
					templateReloaded_ = true;
				}
			}

			profile_ = ProfileFactory.combine(dbversionsList_, profiles_, warningsLog);
			extras_.setConflictingValues(this::hasDifferingValues);

			updateControlsByConfigurable(profile_);

			if (StringUtils.isNotEmpty(warningsLog)) {
				Mess_.on(getParent()).txt(warningsLog.toString()).warning();
			}
		} catch (IOException e) {
			Mess_.on(getParent()).exception(e).warning();
		}
	}
	
	@Override
	protected String doOpenMixerDialog(String command) {
		computeAllPages(profile_);
		updateConfigurableByControls(profile_);
		return new EditMixerDialog(shell_, command, profile_.mixerDeviceNames(), profile_.enabledMixerDevices()).open();
	}

	@Override
	protected void updateControlsByConfigurable(TemplateProfileBase configurable) {
		super.updateControlsByConfigurable(configurable);

		loadfixValue_.setEnabled(loadfix_.getSelection());
	}

	@Override
	protected TemplateProfileBase currentConfigurable() {
		return profile_;
	}

	@Override
	protected void onShellCreated() {
		TreeItem infoItem = addPageGenerator("dialog.template.tab.info", this::createInfoPage);
		addPageGenerator(infoItem, "dialog.profile.tab.custominfo", this::createCustomInfoPage);
		addPageGenerator("dialog.template.tab.mounting", this::createMountingPage);
		TreeItem dosboxItem = addPageGenerator("dialog.template.tab.dosbox", this::createDosboxPage);
		addCommonPageGenerators(dosboxItem);

		addSearchListener(profile_);
		Stream.of(tree_.getItems()).forEach(x -> x.setExpanded(true));
		updatePageTree(profile_);
		tree_.addListener(SWT.Selection, event -> renderPage(profile_, event.item));

		// MacOS does not automatically select the first entry in the tree, so do that now, manually 
		if (SystemUtils.IS_OSX) {
			tree_.setSelection(infoItem);
			renderPage(profile_, infoItem);
		}

		createOkCancelButtons();
	}

	protected Composite createInfoPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = super.createInfoPage(configurable, parent, false);
		engineSelector_.addToggleSelectionListener(false);
		return composite;
	}
	
	protected Composite createMountingPage(TemplateProfileBase configurable, Composite parent) {
		return super.createMountingPage(configurable, parent, MULTI_EDIT);
	}
	
	protected Composite createDosboxPage(TemplateProfileBase configurable, Composite parent) {
		return super.createDosboxPage(configurable, parent, text_.get("dialog.profile.automatic"),
			SettingsDialog.getConfLocations().get(settings_.getIntValue("profiledefaults", "confpath")) + ", " 
		  + SettingsDialog.getConfFilenames().get(settings_.getIntValue("profiledefaults", "conffile")));
	}

	protected void createOkCancelButtons() {
		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				computeAllPages(profile_);
				if (!isValid()) {
					return;
				}
				try {
					UpdateMultiProfileDialog empDialog = new UpdateMultiProfileDialog(shell_, orgProfiles_, profiles_, dbversionChanged_, templateReloaded_, metaControls_, nativeCommandControl_, extras_.changes(),
							engineSelector_.isSelected() ? WebSearchEngine.getBySimpleName(settings_.getValue("gui", "searchengine")): null);

					if (empDialog.open() != null) {
						result_ = profiles_;
						shell_.close();
					}
				} catch (Exception e) {
					Mess_.on(shell_).exception(e).warning();
				}
			}
		});
	}

	protected boolean isValid() {
		Mess_.Builder mess = Mess_.on(shell_);
		applyPendingDosboxVersion(); // a newly selected DOSBox version is applied like 'Set' does, instead of asking
		return mess.valid();
	}
}
