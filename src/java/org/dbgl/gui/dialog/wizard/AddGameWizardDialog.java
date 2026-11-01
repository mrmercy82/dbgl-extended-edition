/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-10-31. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.constants.Constants;
import org.dbgl.gui.abstractdialog.EditConfigurableDialog;
import org.dbgl.gui.abstractdialog.WizardDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.Group_;
import org.dbgl.gui.controls.Label_;
import org.dbgl.gui.controls.List_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.Mess_.Builder;
import org.dbgl.gui.controls.SearchEngineSelector;
import org.dbgl.gui.dialog.BrowseSearchEngineDialog;
import org.dbgl.gui.dialog.LoadSharedConfDialog;
import org.dbgl.gui.dialog.LoadSharedConfDialog.SharedConfLoading;
import org.dbgl.model.FileLocation;
import org.dbgl.model.Link;
import org.dbgl.model.SearchEngineImageInformation;
import org.dbgl.model.SearchEngineImageInformation.SearchEngineImageType;
import org.dbgl.model.WebProfile;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.entity.SharedConf;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.model.repository.TemplateRepository;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ImageService;
import org.dbgl.util.DosGameUtils;
import org.dbgl.util.ExecuteUtils;
import org.dbgl.util.ExecuteUtils.ProfileRunMode;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.StringRelatedUtils;
import org.dbgl.util.iso.ISO9660FileSystem;
import org.dbgl.util.searchengine.WebSearchEngine;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;


public class AddGameWizardDialog extends WizardDialog<Profile> {

	private List<DosboxVersion> dbversionsList_;
	private List<Template> templatesList_;
	private Profile profile_;

	private Text title_, mainText_, setupText_, installExe_, installParameters_, patchExe_, patchParameters_, dstDirectory_, imagesDstDirectory_;
	private SearchEngineSelector engineSelector_;
	private ToolItem loadSharedConfButton_;
	private Combo main_, setup_, machine_, core_, cycles_, cpuCycles_, cpuCyclesProtected_, mapper_;
	private Button moveImages_, btnPreinstalledGame_, btnGameNeedsToBeInstalled_, btnInstallManual_, btnPatchManual_;
	private org.eclipse.swt.widgets.List mountingpoints_, installedFilesList_, orgImagesList_;
	private List<FileLocation> orgImages_;
	private List<FileLocation> installedFiles_;

	private Composite webImagesSpaceHolder_;
	private SearchEngineImageInformation[] imageInformation_;
	private Button[] imgButtons_;

	public AddGameWizardDialog(Shell parent) {
		super(parent, "addgamewizard");
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.addgamewizard.title");
	}

	@Override
	protected boolean prepare() {
		try {
			dbversionsList_ = new DosboxVersionRepository().listAll();
			templatesList_ = new TemplateRepository().listAll(dbversionsList_);

			StringBuilder warningsLog = new StringBuilder();

			DosboxVersion dbVersion = BaseRepository.findDefault(dbversionsList_);
			warningsLog.append(dbVersion.resetAndLoadConfiguration());

			Template template = BaseRepository.findDefault(templatesList_);
			if (template != null)
				warningsLog.append(template.resetAndLoadConfiguration());

			profile_ = ProfileFactory.create(dbVersion, template);

			if (StringUtils.isNotBlank(warningsLog))
				Mess_.on(getParent()).txt(warningsLog.toString()).warning();

			return true;
		} catch (Exception e) {
			Mess_.on(getParent()).exception(e).warning();
			return false;
		}
	}

	@Override
	protected void onShellCreated() {
		Group step0Group = Group_.on(shell_).layout(new GridLayout(3, false)).key("dialog.addgamewizard.step1").build();
		title_ = Chain.on(step0Group).lbl(l -> l.key("dialog.profile.title")).txt(t -> t).text();

		ToolBar toolBar = createToolBar(step0Group);
		engineSelector_ = new SearchEngineSelector(toolBar, true);
		engineSelector_.addToggleSelectionListener(settings_.getBooleanValue("addgamewizard", "consultsearchengine"));

		boolean loadSharedConfEnabledByDefault = settings_.getBooleanValue("addgamewizard", "consultdbconfws");
		loadSharedConfButton_ = createImageToolItem(toolBar, SWT.PUSH,
			loadSharedConfEnabledByDefault ? ImageService.getResourceImage(shell_.getDisplay(), ImageService.IMG_SHARE)
					: ImageService.createDisabledImage(ImageService.getResourceImage(shell_.getDisplay(), ImageService.IMG_SHARE)),
			text_.get("button.consultconfsearchengine", new String[] {Constants.DBCONFWS}), new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					if (Boolean.TRUE.equals(loadSharedConfButton_.getData("selected"))) {
						loadSharedConfButton_.setImage(ImageService.createDisabledImage(ImageService.getResourceImage(shell_.getDisplay(), ImageService.IMG_SHARE)));
						loadSharedConfButton_.setData("selected", false);
						settings_.setBooleanValue("addgamewizard", "consultdbconfws", false);
					} else {
						loadSharedConfButton_.setImage(ImageService.getResourceImage(shell_.getDisplay(), ImageService.IMG_SHARE));
						loadSharedConfButton_.setData("selected", true);
						settings_.setBooleanValue("addgamewizard", "consultdbconfws", true);
					}
				}
			});
		loadSharedConfButton_.setData("selected", loadSharedConfEnabledByDefault);

		boolean requiresInstallation = settings_.getBooleanValue("addgamewizard", "requiresinstallation");
		SelectionAdapter adapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				settings_.setBooleanValue("addgamewizard", "requiresinstallation", event.widget == btnGameNeedsToBeInstalled_);
			}
		};
		Composite radios = Composite_.on(step0Group).horSpan(3).layout(new GridLayout(1, false)).build();
		btnPreinstalledGame_ = Chain.on(radios).lbl(l -> l).lbl(l -> l.key("dialog.addgamewizard.thisgame")).but(
			b -> b.radio().key("dialog.addgamewizard.preinstalled").select(!requiresInstallation).listen(adapter)).button();
		btnGameNeedsToBeInstalled_ = Button_.on(radios).radio().key("dialog.addgamewizard.notyetinstalled").select(requiresInstallation).listen(adapter).ctrl();
		addStep(step0Group);

		Group step1Group = Group_.on(shell_).layout(new GridLayout(3, false)).key("dialog.addgamewizard.step2").build();
		installExe_ = Chain.on(step1Group).lbl(l -> l.key("dialog.addgamewizard.installexe")).txt(t -> t).but(
			b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.INSTALLER, false)).text();
		installExe_.addVerifyListener(event -> {
			if ((event.text.length() > 1) && (profile_.getRequiredMount(false, event.text, false, true) != null)) {
				profile_.addRequiredMount(false, event.text, true);
				mountingpoints_.setItems(profile_.getMountStringsForUI());
			}
		});
		installParameters_ = Chain.on(step1Group).lbl(l -> l.key("dialog.profile.mainparameters")).txt(t -> t.horSpan(2)).text();
		btnInstallManual_ = Chain.on(step1Group).lbl(l -> l.key("dialog.addgamewizard.manualmode")).but(b -> b.horSpan(2).key("dialog.addgamewizard.manualmodeinfo")).button();
		Label_.on(step1Group).horSpan(3).build();
		Group mountGroup = Group_.on(step1Group).layout(new GridLayout(2, false)).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1)).key("dialog.template.mountingoverview").build();
		mountingpoints_ = List_.on(mountGroup).build();
		mountingpoints_.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent event) {
				if (mountingpoints_.getSelectionIndex() == -1) {
					EditConfigurableDialog.doAddMount(shell_, false, mountingpoints_, profile_);
				} else {
					EditConfigurableDialog.doEditMount(shell_, mountingpoints_, profile_);
				}
			}
		});
		Composite mntButComp = Composite_.on(mountGroup).innerLayout(1).build();
		Chain.on(mntButComp).but(b -> b.text().key("dialog.template.mount.add").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				EditConfigurableDialog.doAddMount(shell_, false, mountingpoints_, profile_);
			}
		})).but(b -> b.text().key("dialog.template.mount.edit").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				EditConfigurableDialog.doEditMount(shell_, mountingpoints_, profile_);
			}
		})).but(b -> b.text().key("dialog.template.mount.remove").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				EditConfigurableDialog.doRemoveMount(mountingpoints_, profile_);
			}
		})).build();
		Group associationGroup = Group_.on(step1Group).layout(new GridLayout(2, false)).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1)).key("dialog.template.association").build();
		int dbversionIndex = BaseRepository.indexOf(dbversionsList_, profile_.getDosboxVersion());
		Combo dbversion = Chain.on(associationGroup).lbl(l -> l.key("dialog.template.dosboxversion")).cmb(
			c -> c.layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false)).items(dbversionsList_.stream().map(DosboxVersion::getTitle).toArray(String[]::new)).visibleItemCount(20).select(
				dbversionIndex)).combo();
		dbversion.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				try {
					String warningsLog = profile_.switchToDosboxVersion(dbversionsList_.get(dbversion.getSelectionIndex()));

					resetMounts();

					if (StringUtils.isNotEmpty(warningsLog)) {
						Mess_.on(getParent()).txt(warningsLog).warning();
					}
				} catch (IOException e) {
					Mess_.on(getParent()).exception(e).warning();
				}
			}
		});
		addStep(step1Group);

		Group step2Group = Group_.on(shell_).layout(new GridLayout(3, false)).key("dialog.addgamewizard.step3").build();
		patchExe_ = Chain.on(step2Group).lbl(l -> l.key("dialog.addgamewizard.patcherexe")).txt(t -> t).but(
			b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.INSTALLER, false)).text();
		patchParameters_ = Chain.on(step2Group).lbl(l -> l.key("dialog.profile.mainparameters")).txt(t -> t.horSpan(2)).text();
		btnPatchManual_ = Chain.on(step2Group).lbl(l -> l.key("dialog.addgamewizard.manualmode")).but(b -> b.horSpan(2).key("dialog.addgamewizard.manualpatchmodeinfo")).button();
		addStep(step2Group);

		Group step3Group = Group_.on(shell_).layout(new GridLayout(2, false)).key("dialog.addgamewizard.step4").build();
		main_ = Chain.on(step3Group).lbl(l -> l.key("dialog.profile.mainexe")).cmb(c -> c.wide().visibleItemCount(20)).combo();
		setup_ = Chain.on(step3Group).lbl(l -> l.key("dialog.profile.setupexe")).cmb(c -> c.wide().visibleItemCount(20)).combo();
		addStep(step3Group);

		Group step4Group = Group_.on(shell_).layout(new GridLayout(3, false)).key("dialog.addgamewizard.step4").build();
		mainText_ = Chain.on(step4Group).lbl(l -> l.key("dialog.profile.mainexe")).txt(t -> t).but(b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.EXE, false)).text();
		Chain chnSetup = Chain.on(step4Group).lbl(l -> l.key("dialog.profile.setupexe")).txt(t -> t).but(b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.EXE, false)).build();
		setupText_ = chnSetup.getText();
		chnSetup.getButton().setData(Button_.DATA_ALT_CONTROL, mainText_);
		addStep(step4Group);

		Group step5Group = Group_.on(shell_).layout(new GridLayout()).key("dialog.addgamewizard.step5").build();
		Group installedFilesGroup = Group_.on(step5Group).layout(new GridLayout(3, false)).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false)).key(
			"dialog.addgamewizard.installedfiles").build();
		installedFilesList_ = List_.on(installedFilesGroup).multi().horSpan(3).build();
		dstDirectory_ = Chain.on(installedFilesGroup).lbl(l -> l.key("dialog.migration.to")).txt(t -> t.val(FileLocationService.getInstance().getDosroot().getPath())).but(
			b -> b.browse(false, Button_.BrowseType.DIR, Button_.CanonicalType.NONE, false)).text();
		Group orgImagesGroup = Group_.on(step5Group).layout(new GridLayout(2, false)).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false)).key("dialog.addgamewizard.originalimages").build();
		orgImagesList_ = List_.on(orgImagesGroup).multi().horSpan(2).build();
		moveImages_ = Chain.on(orgImagesGroup).lbl(l -> l.key("dialog.addgamewizard.moveimages")).but(b -> b.select(false)).button();
		imagesDstDirectory_ = Chain.on(orgImagesGroup).lbl(l -> l.key("dialog.migration.to")).txt(t -> t).text();
		imagesDstDirectory_.setEnabled(moveImages_.getSelection());
		moveImages_.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				imagesDstDirectory_.setEnabled(moveImages_.getSelection());
			}
		});
		addStep(step5Group);

		Configuration combinedConf = profile_.getCombinedConfiguration();

		Group step6Group = Group_.on(shell_).layout(new GridLayout(5, false)).key("dialog.addgamewizard.step6").build();
		Combo template = Chain.on(step6Group).lbl(l -> l.key("dialog.profile.template")).cmb(
			c -> c.horSpan(3).wide().items(templatesList_.stream().map(Template::getTitle).toArray(String[]::new)).select(BaseRepository.indexOfDefault(templatesList_))).combo();
		Button_.on(step6Group).text().key("dialog.profile.reloadsettings").tooltip("dialog.profile.reloadsettings.tooltip").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (template.getSelectionIndex() != -1) {
					doReloadTemplate(templatesList_.get(template.getSelectionIndex()));
				}
			}
		}).build();
		
		if (combinedConf.hasValue("dosbox", "machine"))
			machine_ = Chain.on(step6Group).lbl(l -> l.key("dialog.template.machine")).cmb(
				c -> c.horSpan(4).items("profile", profile_.machineSectionItem()).visibleItemCount(20).tooltip("dialog.template.machine.tooltip")).combo();
		if (combinedConf.hasValue("cpu", "core"))
			core_ = Chain.on(step6Group).lbl(l -> l.key("dialog.template.core")).cmb(
				c -> c.horSpan(4).items("profile", "core").visibleItemCount(20).tooltip("dialog.template.core.tooltip")).combo();
		if (combinedConf.hasValue("cpu", "cycles"))
			cycles_ = Chain.on(step6Group).lbl(l -> l.key("dialog.template.cycles")).cmb(
				c -> c.horSpan(4).editable().items("profile", "cycles").visibleItemCount(15).tooltip("dialog.template.cycles.tooltip")).combo();
		if (combinedConf.hasValue("cpu", "cpu_cycles") && combinedConf.hasValue("cpu", "cpu_cycles_protected")) {
			cpuCycles_ = Chain.on(step6Group).lbl(l -> l.key("dialog.template.cpucycles")).cmb(
				c -> c.editable().items("profile", "cpucycles_staging0820").visibleItemCount(15).tooltip("dialog.template.cpucycles.tooltip")).combo();
			cpuCyclesProtected_ = Chain.on(step6Group).lbl(l -> l.key("dialog.template.cpucyclesprotected")).cmb(
				c -> c.horSpan(2).editable().items("profile", "cpucyclesprotected_staging0820").visibleItemCount(15).tooltip("dialog.template.cpucyclesprotected.tooltip")).combo();
		}
		mapper_ = Chain.on(step6Group).lbl(l -> l.key("dialog.template.mapperfile")).cmb(
			c -> c.horSpan(3).items(new String[] {text_.get("dialog.addgamewizard.mapper.generic"), text_.get("dialog.addgamewizard.mapper.specific")}).visibleItemCount(5).tooltip(
				"dialog.template.mapperfile.tooltip")).combo();
		mapper_.select(settings_.getBooleanValue("addgamewizard", "useuniquemapperfile") ? 1: 0);
		mapper_.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				settings_.setBooleanValue("addgamewizard", "useuniquemapperfile", ((Combo)event.widget).getSelectionIndex() == 1);
			}
		});
		addStep(step6Group);

		updateControlsByProfile();
	}

	private static void swapImages(Button btn) {
		Image img1 = btn.getImage();
		Image img2 = (Image)btn.getData("selectedImage");
		btn.setImage(img2);
		btn.setData("selectedImage", img1);
	}

	@Override
	protected int stepSize(int step, boolean forward) {
		if (btnPreinstalledGame_.getSelection()) {
			if ((forward && step == 0) || (!forward && step == 4))
				return 4; // skip installing and patching and maincombo
			if ((forward && step == 4) || (!forward && step == 6))
				return 2; // skip moving game data
		} else {
			if ((forward && step == 3) || (!forward && step == 5))
				return 2; // skip maintext
		}
		return super.stepSize(step, forward);
	}

	@Override
	protected boolean onNext(int step) {
		Mess_.Builder mess = Mess_.on(shell_);
		if (step == 0) {
			return titleEntered(mess) && doWebSearch();
		} else if (step == 1) {
			return installExeEntered(mess) && runInstallerAndCheckResults();
		} else if (step == 2) {
			return determineMainAndSetup();
		} else if (step == 3) {
			return mainExeEntered(mess) && setMain();
		} else if (step == 4) {
			return mainExeEntered(mess) && setMain();
		} else if (step == 5) {
			return conditionsOkForStep5(mess);
		} else if (step == totalSteps() - 1) {
			return createProfile();
		} else if (step == 6) {
			return true;
		}
		return false;
	}

	@Override
	protected void onClose() {
		super.onClose();
		try {
			org.apache.commons.io.FileUtils.deleteDirectory(FileLocationService.getInstance().getTmpInstallDir());
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	private void updateControlsByProfile() {
		Configuration combinedConf = profile_.getCombinedConfiguration();
		if (machine_ != null)
			machine_.setText(combinedConf.getValue("dosbox", "machine"));
		if (core_ != null)
			core_.setText(combinedConf.getValue("cpu", "core"));
		if (cycles_ != null)
			cycles_.setText(combinedConf.getValue("cpu", "cycles"));
		if ((cpuCycles_ != null) && (cpuCyclesProtected_ != null)) {
			cpuCycles_.setText(combinedConf.getValue("cpu", "cpu_cycles"));
			cpuCyclesProtected_.setText(combinedConf.getValue("cpu", "cpu_cycles_protected"));
		}
	}

	private void updateProfileByControls() {
		if (machine_ != null)
			profile_.setValue("dosbox", "machine", machine_.getText());
		if (core_ != null)
			profile_.setValue("cpu", "core", core_.getText());
		if (cycles_ != null)
			profile_.setValue("cpu", "cycles", cycles_.getText());
		if ((cpuCycles_ != null) && (cpuCyclesProtected_ != null)) {
			profile_.setValue("cpu", "cpu_cycles", cpuCycles_.getText());
			profile_.setValue("cpu", "cpu_cycles_protected", cpuCyclesProtected_.getText());
		}
	}

	private void doReloadTemplate(Template template) {
		try {
			StringBuilder warningsLog = new StringBuilder();

			warningsLog.append(template.resetAndLoadConfiguration());
			warningsLog.append(profile_.reloadTemplate(profile_.getDosboxVersion(), template));

			updateControlsByProfile();

			if (StringUtils.isNotEmpty(warningsLog))
				Mess_.on(getParent()).txt(warningsLog.toString()).warning();
		} catch (IOException e) {
			Mess_.on(getParent()).exception(e).warning();
		}
	}

	private boolean titleEntered(Builder mess) {
		if (StringUtils.isBlank(title_.getText()))
			mess.key("dialog.profile.required.title").bind(title_);
		return mess.valid();
	}

	private boolean doWebSearch() {
		String currTitle = title_.getText();

		if (Boolean.TRUE.equals(loadSharedConfButton_.getData("selected"))) {
			try {
				Client client = ClientBuilder.newClient();
				GenericType<List<SharedConf>> confType = new GenericType<List<SharedConf>>() {
				};
				List<SharedConf> confs = client.target(settings_.getValue("confsharing", "endpoint")).path("/configurations/bytitle/{i}").resolveTemplate("i", currTitle).request().accept(
					MediaType.APPLICATION_XML).get(confType);
				client.close();

				if (confs.isEmpty()) {
					Mess_.on(shell_).key("general.notice.searchenginenoresults", new String[] {Constants.DBCONFWS, currTitle}).display();
				} else {
					SharedConfLoading result = new LoadSharedConfDialog(shell_, currTitle, confs).open();
					if (result != null) {
						if (result.reloadDosboxDefaults_)
							profile_.getConfiguration().clearSections();
						profile_.loadConfigurationData(text_, result.conf_.getIncrConf(), new File(result.conf_.getGameTitle()));
						updateControlsByProfile();
					}
				}
			} catch (Exception e) {
				Mess_.on(shell_).key("general.error.retrieveinfosearchengine", new String[] {Constants.DBCONFWS, currTitle, StringRelatedUtils.toString(e)}).exception(e).warning();
			}
		}

		if (engineSelector_.isSelected()) {
			WebSearchEngine engine = WebSearchEngine.getBySimpleName(settings_.getValue("gui", "searchengine"));
			try {
				List<WebProfile> webGamesList = engine.getEntries(currTitle, settings_.getValues(engine.getSimpleName(), "platform_filter"));
				final WebProfile thisGame = webGamesList.isEmpty() ? null: new BrowseSearchEngineDialog(shell_, currTitle, webGamesList, engine).open();
				if (thisGame != null) {
					engine.getEntryDetailedInformation(thisGame);
					if (settings_.getBooleanValue(engine.getSimpleName(), "set_title"))
						title_.setText(thisGame.getTitle());
					if (settings_.getBooleanValue(engine.getSimpleName(), "set_developer"))
						profile_.setDeveloper(thisGame.getDeveloperName());
					if (settings_.getBooleanValue(engine.getSimpleName(), "set_publisher"))
						profile_.setPublisher(thisGame.getPublisherName());
					if (settings_.getBooleanValue(engine.getSimpleName(), "set_year"))
						profile_.setYear(thisGame.getYear());
					if (settings_.getBooleanValue(engine.getSimpleName(), "set_genre"))
						profile_.setGenre(thisGame.getGenre());
					if (settings_.getBooleanValue(engine.getSimpleName(), "set_link"))
						profile_.getLinks()[0] = new Link(text_.get("dialog.profile.searchengine.link.maininfo", new String[] {engine.getName()}), thisGame.getUrl());
					if (settings_.getBooleanValue(engine.getSimpleName(), "set_description"))
						profile_.setNotes(thisGame.getNotes());
					if (settings_.getBooleanValue(engine.getSimpleName(), "set_rank"))
						profile_.getCustomInts()[0] = thisGame.getRank();

					final int WEB_IMAGE_WIDTH = settings_.getIntValue("mobygames", "image_width");
					final int WEB_IMAGE_HEIGHT = settings_.getIntValue("mobygames", "image_height");

					int ca = settings_.getBooleanValue(engine.getSimpleName(), "choose_coverart") ? Integer.MAX_VALUE: 0;
					int ss = settings_.getBooleanValue(engine.getSimpleName(), "choose_screenshot") ? Integer.MAX_VALUE: 0;
					if ((ca > 0) || (ss > 0)) {

						if (webImagesSpaceHolder_ == null) {
							Group step7Group = Group_.on(shell_).layout(new GridLayout(1, false)).key("dialog.addgamewizard.step7").build();
							webImagesSpaceHolder_ = Composite_.on(step7Group).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).innerLayout(2).build();
							addStep(step7Group);
						} else {
							Arrays.stream(webImagesSpaceHolder_.getChildren()).forEach(Control::dispose);
						}

						boolean forceAllRegionsCoverArt = settings_.getBooleanValue(engine.getSimpleName(), "force_all_regions_coverart");
						imageInformation_ = engine.getEntryImages(thisGame, ca, ss, forceAllRegionsCoverArt);

						if (imageInformation_.length > 0) {
							Chain.on(webImagesSpaceHolder_).but(b -> b.layoutData(new GridData((WEB_IMAGE_WIDTH + 10), SWT.DEFAULT)).text().key("button.all").listen(new SelectionAdapter() {
								@Override
								public void widgetSelected(SelectionEvent e) {
									for (Button but: imgButtons_) {
										if (!but.getSelection())
											swapImages(but);
										but.setSelection(true);
									}
								}
							})).but(b -> b.layoutData(new GridData((WEB_IMAGE_WIDTH + 10), SWT.DEFAULT)).text().key("button.none").listen(new SelectionAdapter() {
								@Override
								public void widgetSelected(SelectionEvent e) {
									for (Button but: imgButtons_) {
										if (but.getSelection())
											swapImages(but);
										but.setSelection(false);
									}
								}
							})).build();
						}

						ScrolledComposite webImagesSpace = createScrolledComposite(webImagesSpaceHolder_, SWT.V_SCROLL);
						webImagesSpace.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

						RowLayout rowLayoutImagesGroup = new RowLayout(SWT.HORIZONTAL);
						rowLayoutImagesGroup.marginHeight = 0;
						rowLayoutImagesGroup.marginWidth = 0;
						Composite webImagesComposite = Composite_.on(webImagesSpace).layout(rowLayoutImagesGroup).build();

						webImagesSpace.setContent(webImagesComposite);
						webImagesSpace.setExpandVertical(true);
						webImagesSpace.setExpandHorizontal(true);
						webImagesSpace.addControlListener(new ControlAdapter() {
							@Override
							public void controlResized(ControlEvent e) {
								webImagesSpace.setMinSize(webImagesComposite.computeSize(webImagesSpace.getClientArea().width, SWT.DEFAULT));
							}
						});

						if (imageInformation_.length > 0) {
							imgButtons_ = new Button[imageInformation_.length];

							for (int i = 0; i < imageInformation_.length; i++) {
								imgButtons_[i] = Button_.on(webImagesComposite).image(ImageService.getEmptyImage(display_, WEB_IMAGE_WIDTH, WEB_IMAGE_HEIGHT), true).toggle().tooltipTxt(
									imageInformation_[i].getDescription()).layoutData(new RowData()).ctrl();

								final int j = i;
								Thread thread = new Thread() {
									@Override
									public void run() {
										try {
											ImageData imgData = thisGame.getWebImage(j);
											if (!display_.isDisposed()) {
												display_.asyncExec(() -> {
													if (!imgButtons_[j].isDisposed()) {
														Image img = ImageService.getHeightLimitedImage(display_, WEB_IMAGE_HEIGHT, imgData);
														Image selectedImg = ImageService.createSelectedImage(img);
														imgButtons_[j].getImage().dispose();
														imgButtons_[j].setImage(img);
														imgButtons_[j].setData("selectedImage", selectedImg);
														imgButtons_[j].addSelectionListener(new SelectionAdapter() {
															@Override
															public void widgetSelected(SelectionEvent e) {
																swapImages(imgButtons_[j]);
															}
														});
														webImagesComposite.setSize(webImagesComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
														webImagesComposite.layout();

														webImagesSpaceHolder_.layout(true);
													}
												});
											}
										} catch (IOException e) {
											// ignore any missing images
										}
									}
								};
								thread.start();
							}
						} else {
							Label_.on(webImagesComposite).style(SWT.WRAP | SWT.CENTER).layoutData(new RowData()).key("dialog.profile.notice.noimagesfound", engine.getName()).build();
						}

						webImagesComposite.pack();
					}
				}
			} catch (Exception e) {
				Mess_.on(shell_).key("general.error.retrieveinfosearchengine", new String[] {engine.getName(), currTitle, StringRelatedUtils.toString(e)}).exception(e).warning();
			}
		}

		if (btnGameNeedsToBeInstalled_.getSelection() && StringUtils.isBlank(installExe_.getText()))
			resetMounts();

		return true;
	}

	private boolean installExeEntered(Builder mess) {
		if (StringUtils.isBlank(installExe_.getText()))
			mess.key("dialog.addgamewizard.required.installexe").bind(installExe_);
		return mess.valid();
	}

	private boolean runInstallerAndCheckResults() {
		try {
			FileUtils.deleteDirectory(FileLocationService.getInstance().getTmpInstallDir());
			FilesUtils.createDir(FileLocationService.getInstance().getTmpInstallDir());

			profile_.setAutoexecSettings(installExe_.getText(), installParameters_.getText());

			ExecuteUtils.doRunProfile(ProfileRunMode.INSTALLER, profile_, btnInstallManual_.getSelection(), display_);

			shell_.forceFocus();
			shell_.forceActive();

			orgImages_ = new ArrayList<>();
			File[] firstImageMountPath = profile_.getConfiguration().getAutoexec().findFirstImageMountCanonicalPath();
			for (File file: firstImageMountPath) {
				orgImages_.add(toImageFileLocation(file));
			}
			List<FileLocation> additionalImageFiles = new ArrayList<>();
			for (FileLocation orgImage: orgImages_) {
				File file = orgImage.getCanonicalFile();
				if (FilesUtils.isCueSheet(file.getName())) {
					File binFile = ISO9660FileSystem.parseCueSheet(file);
					if (binFile != null && file.getParentFile().equals(binFile.getParentFile())) {
						additionalImageFiles.add(toImageFileLocation(binFile));
					}
				}
			}
			orgImages_.addAll(additionalImageFiles);

			installedFiles_ = new ArrayList<>();
			File[] files = FileLocationService.getInstance().getTmpInstallDir().listFiles();
			for (File file: files)
				installedFiles_.add(toImageFileLocation(file));
			if (installedFiles_.isEmpty()) {
				Mess_.on(shell_).key("dialog.addgamewizard.error.nofilesinstalled").warning();
				return false;
			}

			return true;
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
			return false;
		}
	}

	private static FileLocation toImageFileLocation(File file) {
		return new FileLocation(file.getPath(), FileLocationService.getInstance().dosrootRelative());
	}

	private boolean determineMainAndSetup() {
		if (StringUtils.isNotBlank(patchExe_.getText())) {
			try {
				profile_.setAutoexecSettings(patchExe_.getText(), patchParameters_.getText());

				ExecuteUtils.doRunProfile(ProfileRunMode.INSTALLER, profile_, btnPatchManual_.getSelection(), shell_.getDisplay());

				shell_.forceFocus();
				shell_.forceActive();
			} catch (IOException e) {
				Mess_.on(shell_).exception(e).warning();
				return false;
			}
		}

		orgImagesList_.removeAll();
		if (orgImages_ != null) {
			for (FileLocation orgImage: orgImages_) {
				orgImagesList_.add(orgImage.getFile().getPath());
			}
		}
		orgImagesList_.selectAll();
		orgImagesList_.pack();
		orgImagesList_.getParent().layout();

		installedFilesList_.removeAll();
		File gameDir = null;
		for (FileLocation installedFile: installedFiles_) {
			if (installedFile.getFile().isDirectory()) {
				installedFilesList_.add("[ " + installedFile.getFile().getPath() + " ]");
				if (gameDir == null)
					gameDir = installedFile.getCanonicalFile();
			} else {
				installedFilesList_.add(installedFile.getFile().getPath());
			}
		}
		installedFilesList_.selectAll();
		installedFilesList_.pack();
		installedFilesList_.getParent().layout();

		moveImages_.setEnabled(profile_.getConfiguration().getAutoexec().countImageMounts() == 1);
		String imagesDirString = settings_.getValue("directory", "orgimages");
		File imagesSubDir = gameDir != null ? new File(gameDir.getName(), imagesDirString): new File(imagesDirString);
		imagesDstDirectory_.setText(imagesSubDir.getPath());

		List<File> executables = FilesUtils.listExecutablesInDirRecursive(FileLocationService.getInstance().getTmpInstallDir());

		main_.removeAll();
		setup_.removeAll();
		setup_.add(StringUtils.EMPTY);
		for (File f: executables) {
			String filename = toImageFileLocation(f).getFile().getPath();
			main_.add(filename);
			setup_.add(filename);
		}
		if (executables.isEmpty())
			main_.add(installExe_.getText());
		int mainFileIndex = DosGameUtils.findMostLikelyMainIndex(title_.getText(), executables);
		if (mainFileIndex != -1) {
			main_.select(mainFileIndex);
		} else {
			main_.select(0);
		}
		int setupFileIndex = DosGameUtils.findSetupIndex(executables);
		if (setupFileIndex != -1) {
			setup_.select(setupFileIndex + 1);
		} else {
			setup_.select(0);
		}
		setup_.setEnabled(setup_.getItemCount() > 1);
		return true;
	}

	private boolean mainExeEntered(Builder mess) {
		if (btnPreinstalledGame_.getSelection()) {
			if (StringUtils.isBlank(mainText_.getText()))
				mess.key("dialog.profile.required.mainexe").bind(mainText_);
		} else {
			if (StringUtils.isBlank(main_.getText()))
				mess.key("dialog.profile.required.mainexe").bind(main_);
		}
		return mess.valid();
	}

	private boolean setMain() {
		if (btnPreinstalledGame_.getSelection()) {
			if (profile_.getRequiredMount(false, mainText_.getText(), false, false) != null) {
				profile_.addRequiredMount(false, mainText_.getText(), false);
			}
			profile_.setAutoexecSettings(mainText_.getText(), StringUtils.EMPTY);
		} else {
			if (profile_.getRequiredMount(false, main_.getText(), false, false) != null) {
				profile_.addRequiredMount(false, main_.getText(), false);
			}
			profile_.setAutoexecSettings(main_.getText(), StringUtils.EMPTY);
		}
		return true;
	}

	private boolean conditionsOkForStep5(Builder mess) {
		if (btnPreinstalledGame_.getSelection())
			return true;

		try {
			if (installedFilesList_.getSelectionCount() > 0) {
				File destDir = new File(dstDirectory_.getText());
				if (!destDir.isDirectory() && (Mess_.on(shell_).key("dialog.addgamewizard.confirm.createdestinationdir", new String[] {destDir.toString()}).confirm())) {
					destDir.mkdirs();
				}
				if (!destDir.isDirectory()) {
					mess.key("dialog.addgamewizard.error.destinationdirmissing", destDir.toString()).bind(dstDirectory_);
				} else {
					for (int i = 0; i < installedFiles_.size(); i++) {
						if (installedFilesList_.isSelected(i)) {
							File destFile = new File(destDir, installedFiles_.get(i).getFile().getName());
							if (org.apache.commons.io.FileUtils.directoryContains(destDir, destFile)) {
								mess.key("dialog.addgamewizard.error.gamedatadirexists", destFile.toString()).bind(dstDirectory_);
							}
						}
					}
				}
			} else {
				mess.key("dialog.addgamewizard.error.gamedatamustbemoved").bind(installedFilesList_);
			}

			return mess.valid();
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
			return false;
		}
	}

	private boolean createProfile() {
		try {

			String setupString = btnPreinstalledGame_.getSelection() ? setupText_.getText(): setup_.getText();

			profile_.setTitle(title_.getText());
			profile_.setSetupFileLocation(setupString);

			if (installedFilesList_.getSelectionCount() > 0) {
				FileLocation destDir = new FileLocation(dstDirectory_.getText(), FileLocationService.getInstance().dosrootRelative());

				profile_.migrate(FileLocationService.getInstance().getTmpInstallLocation(), destDir);
				profile_.removeFloppyMounts();
				profile_.removeUnnecessaryMounts();

				for (int i = 0; i < installedFiles_.size(); i++) {
					File src = installedFiles_.get(i).getCanonicalFile();
					if (installedFilesList_.isSelected(i)) {
						FileUtils.moveToDirectory(src, destDir.getCanonicalFile(), true);
					}
				}

				if (moveImages_.getSelection()) {
					File imgDestDir = new File(destDir.getCanonicalFile(), imagesDstDirectory_.getText());

					for (int i = 0; i < orgImages_.size(); i++) {
						FileLocation src = orgImages_.get(i);
						if (orgImagesList_.isSelected(i)) {
							FileUtils.moveToDirectory(src.getCanonicalFile(), imgDestDir, true);
							FileLocation dst = new FileLocation(FilesUtils.concat(imgDestDir, src.getFile().getName()), destDir.getCanonicalizer());
							profile_.getConfiguration().getAutoexec().migrate(src, dst);
						}
					}
				}
			}

			if (mapper_.getSelectionIndex() == 1)
				profile_.setValue("sdl", "mapperfile", settings_.getValue("profile", "uniquemapperfile"));

			updateProfileByControls();

			profile_.ensurePc98MachineForHdi();
			result_ = new ProfileRepository().add(profile_);

			if (imageInformation_ != null) {
				File canonicalCapturesDir = profile_.getCanonicalCaptures();
				for (int i = 0; i < imageInformation_.length; i++) {
					if (imgButtons_[i].getSelection() && (imageInformation_[i].getData() != null)) {
						String description = FilesUtils.toSafeFilenameForWebImages(imageInformation_[i].getDescription());
						String filename = (imageInformation_[i].getType() == SearchEngineImageType.COVER_ART) 
								? text_.get("dialog.profile.mobygames.coverartfilename", new Object[] {i, description}) + ".jpg"
								: text_.get("dialog.profile.mobygames.screenshotfilename", new Object[] {i, description}) + ".png";
						File file = new File(canonicalCapturesDir, filename);
						if (!FilesUtils.isExistingFile(file)) {
							try {
								ImageService.save(display_, imageInformation_[i].getData(), file.getPath());
							} catch (SWTException e) {
								Mess_.on(shell_).key("general.error.savefile", file.getPath()).exception(e).warning();
							}
						} else {
							Mess_.on(shell_).key("dialog.profile.error.imagealreadyexists",
								new Object[] {file.getPath(), WebSearchEngine.getBySimpleName(settings_.getValue("gui", "searchengine")).getName()}).warning();
						}
					}
				}
			}

		} catch (IOException | SQLException e) {
			Mess_.on(shell_).exception(e).warning();
		}

		return true;
	}

	private void resetMounts() {
		profile_.getConfiguration().getAutoexec().getMountingpoints().clear();
		profile_.unmountDosboxMounts();
		profile_.addMount("mount C \"" + FileLocationService.getInstance().getTmpInstallDir() + "\"");
		mountingpoints_.setItems(profile_.getMountStringsForUI());
	}
}
