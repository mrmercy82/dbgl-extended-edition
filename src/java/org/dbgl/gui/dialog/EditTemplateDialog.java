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
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.EditConfigurableDialog;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.entity.TemplateProfileBase;
import org.dbgl.model.factory.TemplateFactory;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.TemplateRepository;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeItem;


public class EditTemplateDialog extends EditConfigurableDialog<Template> {

	private static final boolean MULTI_EDIT = false;

	private Text title_;

	private Template template_;
	private final boolean editing_;

	public EditTemplateDialog(Shell parent, Template template) {
		super(parent, "templatedialog");
		template_ = template;
		editing_ = template_ != null;
	}

	@Override
	protected String getDialogTitle() {
		return editing_ ? text_.get("dialog.template.title.edit", new Object[] {template_.getTitle(), template_.getId()}): text_.get("dialog.template.title.add");
	}

	@Override
	protected boolean prepare() {
		if (!super.prepare())
			return false;

		try {
			if (editing_) {
				dbversionIndex_ = BaseRepository.indexOf(dbversionsList_, template_.getDosboxVersion());
			} else {
				template_ = TemplateFactory.create(BaseRepository.findDefault(dbversionsList_));
			}
			String warningsLog = template_.resetAndLoadConfiguration();
			if (StringUtils.isNotBlank(warningsLog))
				Mess_.on(getParent()).txt(warningsLog).warning();
			return true;
		} catch (IOException e) {
			Mess_.on(getParent()).exception(e).warning();
			return false;
		}
	}

	@Override
	protected void doPerformDosboxConfAction(DosboxConfAction action, DosboxVersion newDosboxVersion) {
		try {
			computeAllPages(template_);
			updateConfigurableByControls(template_);

			StringBuilder warningsLog = new StringBuilder(newDosboxVersion.resetAndLoadConfiguration());

			if (action == DosboxConfAction.SET)
				warningsLog.append(template_.setToDosboxVersion(newDosboxVersion));
			else if (action == DosboxConfAction.SWITCH)
				warningsLog.append(template_.switchToDosboxVersion(newDosboxVersion));
			else if (action == DosboxConfAction.RELOAD)
				warningsLog.append(template_.reloadDosboxVersion(newDosboxVersion));

			updateControlsByConfigurable(template_);

			if (StringUtils.isNotEmpty(warningsLog)) {
				Mess_.on(getParent()).txt(warningsLog.toString()).warning();
			}
		} catch (IOException e) {
			Mess_.on(getParent()).exception(e).warning();
		}
	}
	
	@Override
	protected String doOpenMixerDialog(String command) {
		computeAllPages(template_);
		updateConfigurableByControls(template_);
		return new EditMixerDialog(shell_, command, template_.mixerDeviceNames(), template_.enabledMixerDevices()).open();
	}

	@Override
	protected TemplateProfileBase currentConfigurable() {
		return template_;
	}

	@Override
	protected void onShellCreated() {
		TreeItem infoItem = addPageGenerator("dialog.template.tab.info", this::createInfoPage);
		addPageGenerator("dialog.template.tab.mounting", this::createMountingPage);
		TreeItem dosboxItem = addPageGenerator("dialog.template.tab.dosbox", this::createDosboxPage);
		addCommonPageGenerators(dosboxItem);

		addSearchListener(template_);
		Stream.of(tree_.getItems()).forEach(x -> x.setExpanded(true));
		updatePageTree(template_);
		tree_.addListener(SWT.Selection, event -> renderPage(template_, event.item));

		// MacOS does not automatically select the first entry in the tree, so do that now, manually 
		if (SystemUtils.IS_OSX) {
			tree_.setSelection(infoItem);
			renderPage(template_, infoItem);
		}

		createOkCancelButtons();
	}

	protected Composite createInfoPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.info", parent, 2);
		title_ = Chain.on(composite).lbl(l -> l.key("dialog.template.title")).txt(t -> t).template(Template::getTitle, Template::setTitle).build(metaControls_).init(configurable).getText();
		Chain.on(composite).lbl(l -> l.key("dialog.template.default")).but(b -> b.horSpan(1)).template(Template::getDefault, Template::setDefault).build(metaControls_).init(configurable);
		if (!editing_)
			title_.setFocus();
		return composite;
	}
	
	protected Composite createMountingPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = super.createMountingPage(configurable, parent, MULTI_EDIT);
		createMountingPageAdditions(configurable, composite);
		return composite;
	}
	
	protected Composite createDosboxPage(TemplateProfileBase configurable, Composite parent) {
		return super.createDosboxPage(configurable, parent, text_.get("dialog.profile.automatic"),
			editing_ ? template_.getConfigurationCanonicalFile().getPath(): SettingsDialog.getConfFilenames().get(0));
	}

	protected void createOkCancelButtons() {
		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				computePage(template_, "dialog.template.tab.mounting");
				computePage(template_, "dialog.template.tab.dosbox");
				computePage(template_, "dialog.template.tab.startup");
				if (!isValid()) {
					return;
				}
				try {
					updateConfigurableByControls(template_);

					if (editing_) {
						new TemplateRepository().update(template_);
						result_ = template_;
					} else {
						result_ = new TemplateRepository().add(template_);
					}
				} catch (Exception e) {
					Mess_.on(shell_).exception(e).warning();
				}
				shell_.close();
			}
		});
	}

	private boolean isValid() {
		Mess_.Builder mess = Mess_.on(shell_);
		if (StringUtils.isBlank(title_.getText())) {
			mess.key("dialog.template.required.title").bind(title_, getTreeItemByControl(title_));
		}
		applyPendingDosboxVersion(); // a newly selected DOSBox version is applied like 'Set' does, instead of asking
		validateMounts(template_, mess);
		return mess.valid();
	}
}
