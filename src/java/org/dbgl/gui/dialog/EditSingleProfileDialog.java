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
package org.dbgl.gui.dialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.constants.Constants;
import org.dbgl.gui.abstractdialog.EditProfileDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.GridData_;
import org.dbgl.gui.controls.Label_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.dialog.LoadSharedConfDialog.SharedConfLoading;
import org.dbgl.model.FileLocation;
import org.dbgl.model.SearchEngineImageInformation.SearchEngineImageType;
import org.dbgl.model.WebProfile;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.entity.SharedConf;
import org.dbgl.model.entity.TemplateProfileBase;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ImageService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.ShortFilenameUtils;
import org.dbgl.util.StringRelatedUtils;
import org.dbgl.util.SystemUtils;
import org.dbgl.util.searchengine.MetropolisSearchEngine;
import org.dbgl.util.searchengine.MobyGamesSearchEngine;
import org.dbgl.util.searchengine.WebSearchEngine;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.TreeItem;


public class EditSingleProfileDialog extends EditProfileDialog<Profile> {

	private static final boolean MULTI_EDIT = false;

	private Profile profile_;
	/** settings lost by switching the DOSBox version here, written when the profile is saved (see SwitchMemory) */
	private java.util.Properties switchMemory_;
	private final String sendTo_;
	private final boolean editing_;
	private final boolean focusOnTitle_;
	// [autoexec] and DOSBox version as read when the dialog opens: to tell whether [autoexec] stays as written (alpha 47)
	private String autoexecAtOpen_;
	private int dosboxAtOpen_ = -1;

	public EditSingleProfileDialog(Shell parent, Profile prof, String sendTo, boolean focusOnTitle) {
		super(parent);
		profile_ = prof == null ? prof: ProfileFactory.create(prof);
		sendTo_ = sendTo;
		focusOnTitle_ = focusOnTitle;

		editing_ = profile_ != null;
		windowImage_ = editing_ ? ImageService.IMG_TB_EDIT: ImageService.IMG_TB_NEW; // the symbols of the toolbar
	}

	@Override
	protected org.dbgl.model.aggregate.Profile savegamesProfile() {
		return editing_ ? profile_: null;
	}

	@Override
	protected File savestatesDir() {
		return editing_ ? org.dbgl.util.ExecuteUtils.savestatesDir(profile_): null;
	}

	private String initialSearch_;

	/** open the dialog showing a setting, e.g. one DOSBox complained about in the status window */
	public EditSingleProfileDialog showSetting(String setting) {
		initialSearch_ = setting;
		return this;
	}

	private boolean consultSearchEngine_;

	/** open the dialog and ask the game database (as with its button) right away */
	public EditSingleProfileDialog consultSearchEngine() {
		consultSearchEngine_ = true;
		return this;
	}

	@Override
	protected void onShellOpened() {
		super.onShellOpened();
		if (initialSearch_ != null)
			display_.asyncExec(() -> searchAndJump(initialSearch_));
		if (consultSearchEngine_)
			display_.asyncExec(() -> {
				ToolItem item = engineSelector_ == null ? null: engineSelector_.getToolItem();
				if ((item != null) && !item.isDisposed())
					item.notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());
			});
	}

	@Override
	protected String getDialogTitle() {
		if (editing_)
			return text_.get("dialog.profile.title.edit", new Object[] {profile_.getTitle(), profile_.getId()});
		else if (sendTo_ != null)
			return text_.get("dialog.profile.title.send", new Object[] {sendTo_});
		else
			return text_.get("dialog.profile.title.add");
	}

	@Override
	protected boolean prepare() {
		if (!super.prepare())
			return false;

		try {
			StringBuilder warningsLog = new StringBuilder();

			if (editing_) {
				dbversionIndex_ = BaseRepository.indexOf(dbversionsList_, profile_.getDosboxVersion());
			} else {
				Template template = BaseRepository.findDefault(templatesList_);

				if (template != null)
					warningsLog.append(template.resetAndLoadConfiguration());

				profile_ = ProfileFactory.create(BaseRepository.findDefault(dbversionsList_), template);

				if (sendTo_ != null) {
					if (FilesUtils.isConfFile(sendTo_)) {

						File cwd = null;
						File filterPath = new File(sendTo_).getParentFile();
						if (filterPath != null && !filterPath.equals(FileLocationService.getInstance().getProfilesDir())) {
							File dosboxDir = new File(filterPath, "DOSBOX"); // common GOG DOSBox directory name
							if (FilesUtils.isExistingDirectory(dosboxDir))
								filterPath = dosboxDir;

							Mess_.on(getParent()).key("dialog.profile.notice.selectworkingdirectory", sendTo_).display();

							DirectoryDialog dirDialog = new DirectoryDialog(getParent());
							dirDialog.setFilterPath(filterPath.getPath());
							String resultDir = dirDialog.open();
							if (resultDir != null)
								cwd = new File(resultDir);
							else
								cwd = FileLocationService.getInstance().getDosroot();
						}

						profile_.setConfigurationFileLocation(sendTo_);
						warningsLog.append(profile_.resetAndLoadConfiguration(cwd));

						String additional = StringUtils.EMPTY;
						while (profile_.isIncomplete() && (additional != null)) {
							Mess_.on(getParent()).key("dialog.profile.notice.looksincompleteloadadditional").display();

							List<String> names = new ArrayList<>(
									Arrays.asList(text_.get("filetype.conf"), text_.get("filetype.exe") + ", " + text_.get("filetype.booterimage"), FilesUtils.ALL_FILTER));
							List<String> extensions = new ArrayList<>(Arrays.asList(FilesUtils.CNF_FILTER, FilesUtils.EXE_FILTER + ";" + FilesUtils.BTR_FILTER, FilesUtils.ALL_FILTER));
							FileDialog dialog = new FileDialog(getParent(), SWT.OPEN);
							dialog.setFilterPath(new File(sendTo_).getParent());
							dialog.setFilterNames(names.toArray(new String[0]));
							dialog.setFilterExtensions(extensions.toArray(new String[0]));
							additional = dialog.open();
							if (additional != null) {
								profile_.setConfigurationFileLocation(additional);
								warningsLog.append(profile_.loadConfiguration(cwd));
							}
						}

						if (StringUtils.isNotBlank(warningsLog))
							Mess_.on(getParent()).txt(warningsLog.toString()).warning();

						return true;
					} else if (FilesUtils.isExecutable(sendTo_)) {
						profile_.getConfiguration().getAutoexec().setGameMain(new FileLocation(sendTo_, FileLocationService.getInstance().dosrootRelative()).getFile().getPath());
					} else if (FilesUtils.isBooterImage(sendTo_)) {
						profile_.getConfiguration().getAutoexec().setImg(0, new FileLocation(sendTo_, FileLocationService.getInstance().dosrootRelative()).getFile().getPath());
					}
				}
			}

			warningsLog.append(profile_.resetAndLoadConfiguration());
			autoexecAtOpen_ = autoexecText(profile_);
			dosboxAtOpen_ = profile_.getDosboxVersion() != null ? profile_.getDosboxVersion().getId(): -1;

			if (StringUtils.isNotBlank(warningsLog))
				Mess_.on(getParent()).txt(warningsLog.toString()).warning();
			return true;
		} catch (Exception e) {
			Mess_.on(getParent()).exception(e).warning();
			return false;
		}
	}

	@Override
	protected void updateControlsByConfigurable(TemplateProfileBase configurable) {
		super.updateControlsByConfigurable(configurable);

		loadfixValue_.setEnabled(loadfix_.getSelection());
	}

	/** the user was asked about the lines of the [autoexec] DBGL does not know, in this dialog */
	private boolean unknownLinesAsked_;

	/**
	 * With "ask" (see {@link org.dbgl.util.UnknownAutoexecLines}): let the user choose what happens to the lines of the
	 * [autoexec] DBGL does not know, once per dialog, when a DOSBox version or template is assigned and when saving. The
	 * controls must have been written to the profile before. Cancelled, the lines stay where they are.
	 *
	 * @return true if lines were put into the own sections, so the controls must show them
	 */
	private boolean askUnknownAutoexecLines() {
		if (unknownLinesAsked_ || !org.dbgl.util.UnknownAutoexecLines.ask())
			return false;
		List<org.dbgl.model.conf.Autoexec.UnknownLine> open = org.dbgl.util.UnknownAutoexecLines.undecided(profile_);
		if (open.isEmpty())
			return false;
		unknownLinesAsked_ = true;
		List<org.dbgl.util.UnknownAutoexecLines.Decision> decisions = new UnknownLinesDialog(shell_, profile_.getTitle(), open).open();
		if (decisions == null)
			return false;
		org.dbgl.util.UnknownAutoexecLines.decide(profile_, decisions, true);
		profile_.getConfiguration().getAutoexec().getUnknownLines().clear();
		return true;
	}

	@Override
	protected void doPerformDosboxConfAction(DosboxConfAction action, DosboxVersion newDosboxVersion) {
		StringBuilder warningsLog = new StringBuilder();
		// all pages are built first and the values taken over: that takes a moment, so with a progress bar
		try (org.dbgl.gui.controls.UiProgress progress = new org.dbgl.gui.controls.UiProgress(shell_, text_.get("dialog.profile.working"), pagesToCompute() + 4 + controlsUpdateSteps())) {
			computeAllPages(profile_, progress);
			updateConfigurableByControls(profile_);
			progress.step();
			askUnknownAutoexecLines();

			warningsLog.append(newDosboxVersion.resetAndLoadConfiguration());
			progress.step();

			if (action == DosboxConfAction.SET) {
				warningsLog.append(profile_.setToDosboxVersion(newDosboxVersion));
			} else if (action == DosboxConfAction.SWITCH) {
				org.dbgl.util.SwitchChoices.Snapshot before = org.dbgl.util.SwitchChoices.before(profile_);
				org.dbgl.model.entity.GenerationAwareConfigurable.Generation from = profile_.getGeneration();
				warningsLog.append(profile_.switchToDosboxVersion(newDosboxVersion));
				if (editing_) {
					// lost when switched away from this kind of DOSBox before: back again; what it loses now: remembered when saved
					if (switchMemory_ == null)
						switchMemory_ = org.dbgl.util.SwitchMemory.load(profile_.getId());
					List<org.dbgl.util.SwitchChoices.Choice> restores = org.dbgl.util.SwitchMemory.restoresFrom(switchMemory_, profile_);
					// asked first: brought back only if the user wants it (otherwise they stay remembered)
					if (!restores.isEmpty() && !Mess_.on(shell_).key("dialog.switchdosbox.confirmrestore", new Object[] {
						restores.stream().map(c -> "[" + c.section() + "] " + c.item() + "=" + c.oldText()).collect(java.util.stream.Collectors.joining("\n"))}).confirmUnwrapped())
						restores = List.of();
					for (org.dbgl.util.SwitchChoices.Choice c: restores)
						profile_.getConfiguration().setValue(c.section(), c.item(), c.oldText());
					if (!restores.isEmpty())
						warningsLog.append(text_.get("dialog.switchdosbox.restoredinfo", new Object[] {
							restores.stream().map(c -> "[" + c.section() + "] " + c.item() + "=" + c.oldText()).collect(java.util.stream.Collectors.joining(", "))})).append('\n');
					org.dbgl.util.SwitchMemory.rememberInto(switchMemory_, profile_, before, from, org.dbgl.util.SwitchChoices.after(before, profile_));
				}
			} else if (action == DosboxConfAction.RELOAD) {
				warningsLog.append(profile_.reloadDosboxVersion(newDosboxVersion));
			} else if (action == DosboxConfAction.LOAD_TEMPLATE) {
				Template templ = templatesList_.get(templateCombo_.getSelectionIndex());
				templ.setDosboxVersion(newDosboxVersion);
				warningsLog.append(templ.resetAndLoadConfiguration());
				warningsLog.append(profile_.loadTemplate(newDosboxVersion, templ));
			} else if (action == DosboxConfAction.RELOAD_TEMPLATE) {
				Template templ = templatesList_.get(templateCombo_.getSelectionIndex());
				templ.setDosboxVersion(newDosboxVersion);
				warningsLog.append(templ.resetAndLoadConfiguration());
				warningsLog.append(profile_.reloadTemplate(newDosboxVersion, templ));
			}
			progress.step();

			uiProgress_ = progress;
			updateControlsByConfigurable(profile_);
		} catch (IOException e) {
			Mess_.on(getParent()).exception(e).warning();
			return;
		} finally {
			uiProgress_ = null;
		}
		if (StringUtils.isNotEmpty(warningsLog)) {
			Mess_.on(getParent()).txt(warningsLog.toString()).warning();
		}
	}
	
	@Override
	protected String doOpenMixerDialog(String command) {
		computeAllPages(profile_);
		updateConfigurableByControls(profile_);
		return new EditMixerDialog(shell_, command, profile_.mixerDeviceNames(), profile_.enabledMixerDevices()).open();
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
		addSavePageGenerators(true); // alpha 48: below the drives
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
		Composite composite = super.createInfoPage(configurable, parent, !editing_ || focusOnTitle_);

		ToolItem engineToolItem = engineSelector_.getToolItem();
		engineToolItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (event.detail != SWT.ARROW) {

					final int WEB_IMAGE_WIDTH = settings_.getIntValue("mobygames", "image_width");
					final int WEB_IMAGE_HEIGHT = settings_.getIntValue("mobygames", "image_height");
					final int WEB_IMAGE_COLUMNS = settings_.getIntValue("mobygames", "image_columns");
					final int DIALOG_RESIZE_WIDTH = ((WEB_IMAGE_WIDTH + 10) * WEB_IMAGE_COLUMNS) + (3 * (WEB_IMAGE_COLUMNS - 1)) + 19;

					WebSearchEngine engine = WebSearchEngine.getBySimpleName(settings_.getValue("gui", "searchengine"));

					WebProfile orgProf = (WebProfile)engineToolItem.getData("profile");
					if (orgProf == null) {
						String currTitle = title_.getText();
						if (currTitle.length() >= 1) {
							try {
								WebProfile thisGame = null;
								// in the background with a Cancel button: a slow or blocking game database made the window look hung
								String searching = text_.get("searchengine.searching", new Object[] {engine.getName()});
								String[] platformFilter = settings_.getValues(engine.getSimpleName(), "platform_filter");
								List<WebProfile> webGamesList = org.dbgl.gui.controls.BusyTask.run(shell_, searching, () -> engine.getEntries(currTitle, platformFilter));
								if (webGamesList.isEmpty()) {
									Mess_.on(shell_).key("general.notice.searchenginenoresults", new String[] {engine.getName(), currTitle}).display();
								} else if (webGamesList.size() == 1) {
									thisGame = webGamesList.get(0);
								} else {
									thisGame = new BrowseSearchEngineDialog(shell_, currTitle, webGamesList, engine).open();
								}
								if (thisGame != null) {
									computePage(profile_, "dialog.profile.tab.custominfo");
									final WebProfile chosen = thisGame;
									WebProfile profExt = org.dbgl.gui.controls.BusyTask.run(shell_, searching, () -> engine.getEntryDetailedInformation(chosen));

									WebProfile currentProf = new WebProfile();
									currentProf.setTitle(title_.getText());
									currentProf.setDeveloperName(developer_.getText());
									currentProf.setPublisherName(publisher_.getText());
									currentProf.setYear(year_.getText());
									currentProf.setGenre(genre_.getText());
									currentProf.setUrl(link_[0].getText());
									currentProf.setPlatform(linkTitle_[0].getText());
									currentProf.setNotes(notes_.getText());
									currentProf.setRank(custom9_.getSelection());
									engineToolItem.setData("profile", currentProf);
									engineToolItem.setImage(ImageService.getResourceImage(display_, ImageService.IMG_UNDO));
									engineToolItem.setToolTipText(text_.get("dialog.profile.undosearchengine"));

									if (settings_.getBooleanValue(engine.getSimpleName(), "set_title"))
										title_.setText(profExt.getTitle());
									if (settings_.getBooleanValue(engine.getSimpleName(), "set_developer"))
										developer_.setText(profExt.getDeveloperName());
									if (settings_.getBooleanValue(engine.getSimpleName(), "set_publisher"))
										publisher_.setText(profExt.getPublisherName());
									if (settings_.getBooleanValue(engine.getSimpleName(), "set_year"))
										year_.setText(profExt.getYear());
									if (settings_.getBooleanValue(engine.getSimpleName(), "set_genre"))
										genre_.setText(profExt.getGenre());
									if (settings_.getBooleanValue(engine.getSimpleName(), "set_link")) {
										link_[0].setText(profExt.getUrl());
										linkTitle_[0].setText(text_.get("dialog.profile.searchengine.link.maininfo",
											new String[] {engine instanceof MetropolisSearchEngine ? MobyGamesSearchEngine.getInstance().getName(): engine.getName()}));
									}
									if (settings_.getBooleanValue(engine.getSimpleName(), "set_description")) {
										String n = notes_.getText();
										String p = profExt.getNotes().replace(StringUtils.LF, notes_.getLineDelimiter());
										if (!n.endsWith(p)) {
											if (n.length() > 0) {
												notes_.append(notes_.getLineDelimiter() + notes_.getLineDelimiter());
											}
											notes_.append(p);
										}
									}
									if (settings_.getBooleanValue(engine.getSimpleName(), "set_rank"))
										custom9_.setSelection(profExt.getRank());

									int ca = settings_.getBooleanValue(engine.getSimpleName(), "choose_coverart") ? Integer.MAX_VALUE: 0;
									int ss = settings_.getBooleanValue(engine.getSimpleName(), "choose_screenshot") ? Integer.MAX_VALUE: 0;
									if ((ca > 0) || (ss > 0)) {
										boolean forceAllRegionsCoverArt = settings_.getBooleanValue(engine.getSimpleName(), "force_all_regions_coverart");
										imageInformation_ = org.dbgl.gui.controls.BusyTask.run(shell_, searching, () -> engine.getEntryImages(profExt, ca, ss, forceAllRegionsCoverArt));

										webImagesSpaceHolder_ = Composite_.on(contents_).layoutData(new GridData(SWT.FILL, SWT.FILL, false, true)).innerLayout(2).build();

										if (imageInformation_.length > 0) {
											Chain.on(webImagesSpaceHolder_).but(
												b -> b.layoutData(new GridData((WEB_IMAGE_WIDTH + 10), SWT.DEFAULT)).text().key("button.all").listen(new SelectionAdapter() {
													@Override
													public void widgetSelected(SelectionEvent e) {
														for (Button but: imgButtons_) {
															if (!but.getSelection())
																but.setSelection(swapImages(but));
														}
													}
												})).but(b -> b.layoutData(new GridData((WEB_IMAGE_WIDTH + 10), SWT.DEFAULT)).text().key("button.none").listen(new SelectionAdapter() {
													@Override
													public void widgetSelected(SelectionEvent e) {
														for (Button but: imgButtons_) {
															if (but.getSelection())
																but.setSelection(!swapImages(but));
														}
													}
												})).build();
										}

										// how far loading the images is, and why some could not be loaded
										GridData statusData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
										statusData.widthHint = 2 * (WEB_IMAGE_WIDTH + 10);
										org.dbgl.gui.controls.ImageLoadStatus loadStatus = imageInformation_.length > 0
												? new org.dbgl.gui.controls.ImageLoadStatus(webImagesSpaceHolder_, imageInformation_.length, statusData)
												: null;

										ScrolledComposite webImagesSpace = createScrolledComposite(webImagesSpaceHolder_, SWT.V_SCROLL);
										webImagesSpace.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
										webImagesSpace.setLayout(new GridLayout());
										webImagesSpace.getVerticalBar().setIncrement(WEB_IMAGE_HEIGHT / WEB_IMAGE_COLUMNS);
										webImagesSpace.getVerticalBar().setPageIncrement((WEB_IMAGE_HEIGHT / WEB_IMAGE_COLUMNS) * 8);

										GridLayout gridLayoutImagesGroup = new GridLayout(WEB_IMAGE_COLUMNS, true);
										gridLayoutImagesGroup.marginHeight = 0;
										gridLayoutImagesGroup.marginWidth = 0;
										gridLayoutImagesGroup.horizontalSpacing = 1;
										gridLayoutImagesGroup.verticalSpacing = 1;
										Composite webImagesComposite = Composite_.on(webImagesSpace).layout(gridLayoutImagesGroup).build();

										webImagesSpace.setContent(webImagesComposite);

										if (imageInformation_.length > 0) {
											imgButtons_ = new Button[imageInformation_.length];

											for (int i = 0; i < imageInformation_.length; i++) {
												imgButtons_[i] = Button_.on(webImagesComposite).image(ImageService.getEmptyImage(display_, WEB_IMAGE_WIDTH, WEB_IMAGE_HEIGHT),
													true).toggle().tooltipTxt(imageInformation_[i].getDescription()).ctrl();
											
												final int j = i;
												final String imgUrl = imageInformation_[i].getUrl();
												new Thread() {
													@Override
													public void run() {
														try {
															ImageData imgData = profExt.getWebImage(j);
															loadStatus.loaded();
															if (!display_.isDisposed()) {
																display_.asyncExec(() -> {
																	if (!imgButtons_[j].isDisposed()) {
																		Image img = ImageService.getWidthLimitedImage(display_, WEB_IMAGE_WIDTH, imgData);
																		Image selectedImg = ImageService.createSelectedImage(img);
																		imgButtons_[j].getImage().dispose();
																		imgButtons_[j].setImage(img);
																		imgButtons_[j].setData("selectedImage", selectedImg);
																		imgButtons_[j].addSelectionListener(new SelectionAdapter() {
																			@Override
																			public void widgetSelected(SelectionEvent e) {
																				e.doit = swapImages(imgButtons_[j]);
																			}
																		});
																		webImagesComposite.setSize(webImagesComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
																		webImagesComposite.layout();
																	}
																});
															}
														} catch (Exception e) {
															loadStatus.failed(e, imgUrl); // shown above the images
														}
													}
												}.start();
											}
										} else {
											Label_.on(webImagesComposite).style(SWT.WRAP | SWT.CENTER).layoutData(new GridData_(SWT.CENTER, SWT.FILL, true, true, WEB_IMAGE_COLUMNS, 1).widthHint(
												(WEB_IMAGE_WIDTH + 10) * WEB_IMAGE_COLUMNS + (3 * (WEB_IMAGE_COLUMNS - 1)) + 2).verticalIndent(WEB_IMAGE_HEIGHT / 2).build()).key(
													"dialog.profile.notice.noimagesfound", engine.getName()).build();
										}

										setListenerEnabled(false);
										webImagesComposite.pack();
										shell_.setSize(shell_.getSize().x + DIALOG_RESIZE_WIDTH, shell_.getSize().y);
										shell_.layout();
									}
								}
							} catch (org.dbgl.gui.controls.BusyTask.Cancelled e) {
								// the user cancelled: nothing changed
							} catch (Exception e) {
								org.dbgl.util.searchengine.SearchEngineException explained = org.dbgl.util.searchengine.SearchEngineException.explain(engine.getName(), e);
								e.printStackTrace();
								Mess_.on(shell_).key("searchengine.error.retrieve", new Object[] {engine.getName(), currTitle, explained.getMessage()}).warning();
							}
						}
					} else {
						title_.setText(orgProf.getTitle());
						developer_.setText(orgProf.getDeveloperName());
						publisher_.setText(orgProf.getPublisherName());
						year_.setText(orgProf.getYear());
						genre_.setText(orgProf.getGenre());
						link_[0].setText(orgProf.getUrl());
						linkTitle_[0].setText(orgProf.getPlatform());
						notes_.setText(orgProf.getNotes());
						custom9_.setSelection(orgProf.getRank());

						engineToolItem.setData("profile", null);
						engineToolItem.setImage(ImageService.getResourceImage(display_, engine.getIcon()));
						engineToolItem.setToolTipText(text_.get("dialog.profile.consultsearchengine", new String[] {engine.getName()}));

						if (webImagesSpaceHolder_ != null) {
							webImagesSpaceHolder_.dispose();
							webImagesSpaceHolder_ = null;
							shell_.setSize(shell_.getSize().x - DIALOG_RESIZE_WIDTH, shell_.getSize().y);
							shell_.layout();
							setListenerEnabled(true);
							imageInformation_ = null;
						}
					}
				}
			}
		});

		ToolBar toolBar = engineToolItem.getParent();
		createImageToolItem(toolBar, SWT.PUSH, ImageService.getResourceImage(display_, ImageService.IMG_SHARE), text_.get("button.consultconfsearchengine", new String[] {Constants.DBCONFWS}),
			new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					String currTitle = title_.getText();
					if (currTitle.length() >= 1) {
						try {
							Client client = ClientBuilder.newClient();
							List<SharedConf> confs = client.target(settings_.getValue("confsharing", "endpoint")).path("/configurations/bytitle/{i}").resolveTemplate("i", currTitle).request().accept(
								MediaType.APPLICATION_XML).get(new GenericType<List<SharedConf>>() {
								});
							client.close();

							if (confs.isEmpty()) {
								Mess_.on(shell_).key("general.notice.searchenginenoresults", new String[] {Constants.DBCONFWS, currTitle}).display();
								return;
							}

							SharedConfLoading result = new LoadSharedConfDialog(shell_, currTitle, confs).open();
							if (result != null) {
								computeAllPages(profile_);
								updateConfigurableByControls(profile_);
								if (result.reloadDosboxDefaults_)
									profile_.getConfiguration().clearSections();
								profile_.loadConfigurationData(text_, result.conf_.getIncrConf(), new File(result.conf_.getGameTitle()));
								updateControlsByConfigurable(profile_);
							}
						} catch (Exception e) {
							Mess_.on(shell_).key("general.error.retrieveinfosearchengine", new String[] {Constants.DBCONFWS, currTitle, StringRelatedUtils.toString(e)}).exception(e).warning();
						}
					}
				}
			});

		return composite;
	}
	
	protected Composite createMountingPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = super.createMountingPage(configurable, parent, MULTI_EDIT);
		
		final VerifyListener addMountListener = event -> {
			if ((event.text.length() > 1) && (mountingpointsList_.getItemCount() == 0)) {
				boolean booter = Stream.of(img_).anyMatch(x -> event.widget == x);
				profile_.addRequiredMount(booter, event.text, false);
				mountingpointsList_.setItems(profile_.getMountStringsForUI());
				usePc98MachineForHdi(event.text);
			}
		};

		main_.addVerifyListener(addMountListener);
		setup_.addVerifyListener(addMountListener);
		for (int i = 0; i < Autoexec.BOOTER_IMAGES; i++) {
			img_[i].addVerifyListener(addMountListener);
		}
		
		return composite;
	}
	
	protected Composite createDosboxPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = super.createDosboxPage(configurable, parent,
			editing_ ? profile_.getCanonicalCaptures().getPath() : text_.get("dialog.profile.automatic"),
			editing_ ? profile_.getConfigurationCanonicalFile().getPath() : 
				SettingsDialog.getConfLocations().get(settings_.getIntValue("profiledefaults", "confpath")) + ", " + 
				SettingsDialog.getConfFilenames().get(settings_.getIntValue("profiledefaults", "conffile")));
		createAllSettingsControls(composite);
		createDosboxLogControls(composite);
		return composite;
	}

	private Button saveDosboxLog_;

	/** save what DOSBox puts out as a log file, for this profile; at the top of the page */
	private void createDosboxLogControls(Composite composite) {
		org.eclipse.swt.widgets.Group group = new org.eclipse.swt.widgets.Group(composite, SWT.NONE);
		group.setText(text_.get("dialog.profile.dosboxlog.group"));
		group.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		group.setLayout(new GridLayout(2, false));
		saveDosboxLog_ = new Button(group, SWT.CHECK);
		saveDosboxLog_.setText(text_.get("dialog.profile.dosboxlog"));
		saveDosboxLog_.setToolTipText(text_.get("dialog.profile.dosboxlog.tooltip", new Object[] {org.dbgl.util.DosboxLog.logsDir().getPath(), org.dbgl.util.DosboxLog.KEEP}));
		saveDosboxLog_.setSelection(editing_ && org.dbgl.util.DosboxLog.isOn(profile_.getId()));
		saveDosboxLog_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button open = new Button(group, SWT.PUSH);
		open.setText(text_.get("dialog.profile.dosboxlog.open"));
		java.io.File dir = editing_ ? org.dbgl.util.DosboxLog.folderOf(profile_.getId(), profile_.getTitle()): null;
		open.setEnabled((dir != null) && dir.isDirectory());
		open.addListener(SWT.Selection, e -> SystemUtils.openDirForViewing(dir, shell_));
		// below the title of the page, above its first group
		for (org.eclipse.swt.widgets.Control child: composite.getChildren()) {
			if ((child != group) && (child instanceof org.eclipse.swt.widgets.Group)) {
				group.moveAbove(child);
				break;
			}
		}
	}

	private org.eclipse.swt.widgets.Group allSettingsGroup_;

	/** alpha 48: the settings without a field of their own come at the end of a page; on this one above 'all settings' */
	@Override
	protected void onPageCreated(String key, Composite page) {
		super.onPageCreated(key, page);
		if ((allSettingsGroup_ != null) && !allSettingsGroup_.isDisposed() && (allSettingsGroup_.getParent() == page))
			allSettingsGroup_.moveBelow(null);
	}

	/** this profile alone keeps all DOSBox settings (as D-Fend Reloaded does), with the drawbacks right below it */
	private void createAllSettingsControls(Composite composite) {
		org.eclipse.swt.widgets.Group group = new org.eclipse.swt.widgets.Group(composite, SWT.NONE);
		allSettingsGroup_ = group;
		group.setText(text_.get("dialog.profile.allsettings.group"));
		group.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		group.setLayout(new GridLayout(1, false));
		Button keepAll = new Button(group, SWT.CHECK);
		keepAll.setText(text_.get("dialog.profile.allsettings"));
		keepAll.setSelection(profile_.keepsAllSettings());
		org.eclipse.swt.widgets.Label warning = new org.eclipse.swt.widgets.Label(group, SWT.WRAP);
		GridData warningData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		warningData.widthHint = 560;
		warning.setLayoutData(warningData);
		warning.setText(text_.get("dialog.profile.allsettings.warning"));
		warning.setForeground(display_.getSystemColor(SWT.COLOR_DARK_RED));
		keepAll.addListener(SWT.Selection, e -> {
			// take over what is in the dialog first, then fill in (on) or take out what equals the DOSBox configuration (off)
			try (org.dbgl.gui.controls.UiProgress progress = new org.dbgl.gui.controls.UiProgress(shell_, text_.get("dialog.profile.working"), pagesToCompute() + 3 + controlsUpdateSteps())) {
				computeAllPages(profile_, progress);
				updateConfigurableByControls(profile_);
				progress.step();
				profile_.setKeepsAllSettings(keepAll.getSelection());
				progress.step();
				uiProgress_ = progress;
				updateControlsByConfigurable(profile_);
			} finally {
				uiProgress_ = null;
			}
		});
	}
	
	private static boolean swapImages(Button btn) {
		Image img1 = btn.getImage();
		Image img2 = (Image)btn.getData("selectedImage");
		
		if (img1 != null && img2 != null) {
			btn.setImage(img2);
			btn.setData("selectedImage", img1);
			return true;
		}
		
		return false;
	}

	protected void createOkCancelButtons() {
		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				try (org.dbgl.gui.controls.UiProgress progress = new org.dbgl.gui.controls.UiProgress(shell_, text_.get("dialog.profile.working"), 4)) {
					computePage(profile_, "dialog.template.tab.mounting");
					progress.step();
					computePage(profile_, "dialog.template.tab.dosbox");
					progress.step();
					computePage(profile_, "dialog.template.tab.startup");
					progress.step();
				}
				if (!isValid()) {
					return;
				}
				// a file in Windows-1252 (umlauts) where the system uses UTF-8: kept so, or converted, as the user wants
				if (editing_ && (org.dbgl.gui.controls.CharsetQuestion.ask(shell_, java.util.List.of(profile_)) == org.dbgl.gui.controls.CharsetQuestion.Answer.CANCEL))
					return;
				try {
					String autoexecBefore = editing_ ? autoexecText(profile_): null;
					try (org.dbgl.gui.controls.UiProgress progress = new org.dbgl.gui.controls.UiProgress(shell_, text_.get("dialog.profile.working"), 2)) {
						updateConfigurableByControls(profile_);
						progress.step();
					}
					// nothing done here changes [autoexec]: it stays as written in the file, lines DBGL does not know too (no question)
					if (editing_ && autoexecUnchanged(profile_, autoexecAtOpen_, dosboxAtOpen_, autoexecBefore))
						profile_.getConfiguration().keepAutoexecAsWritten();
					else if (!(editing_ && org.dbgl.model.conf.MountLines.onlyMountsDiffer(autoexecAtOpen_, autoexecText(profile_))))
						askUnknownAutoexecLines(); // kept lines go straight into the configuration saved below
					// (only mounts changed: only those lines of [autoexec] change, the others stay as written, no question either)

					// saving (database, configuration file, history, extras) in the background: with a progress bar when it takes a moment
					// (without Cancel: half saved is worse than waiting)
					result_ = org.dbgl.gui.controls.BusyTask.runToEnd(shell_, text_.get("dialog.profile.saving"), () -> {
						if (editing_) {
							new ProfileRepository().update(profile_);
							if (switchMemory_ != null)
								org.dbgl.util.SwitchMemory.save(profile_.getId(), switchMemory_);
							return profile_;
						}
						if (org.dbgl.util.ExtrasFinder.onAdd()) // manuals, walkthroughs, maps, soundtrack of the new game into free links
							org.dbgl.util.ExtrasFinder.link(profile_, org.dbgl.util.ExtrasFinder.find(profile_));
						return new ProfileRepository().add(profile_);
					});
					if ((saveDosboxLog_ != null) && !saveDosboxLog_.isDisposed())
						org.dbgl.util.DosboxLog.setOn(result_.getId(), saveDosboxLog_.getSelection());

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
				} catch (Exception e) {
					Mess_.on(shell_).exception(e).warning();
				}
				shell_.close();
			}
		});

		Button_.on(otherButtons_).imageText(ImageService.IMG_SHARE, "button.shareconf").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				computeAllPages(profile_);
				updateConfigurableByControls(profile_);
				new ShareConfDialog(shell_, title_.getText(), year_.getText(), profile_).open();
			}
		}).build();
	}

	protected boolean isValid() {
		Mess_.Builder mess = Mess_.on(shell_);
		String requiredMount = null;
		if (title_.getText().length() == 0) {
			mess.key("dialog.profile.required.title").bind(title_, getTreeItemByControl(title_));
		}
		if (dosRadio_.getSelection()) {
			if (FilesUtils.isExecutable(main_.getText())) {
				requiredMount = dealWithField(false, main_.getText());
				if (!validDosFilename(main_))
					return false;
			} else {
				mess.key("dialog.profile.required.mainexe").bind(main_, getTreeItemByControl(main_));
			}
			if (FilesUtils.isExecutable(setup_.getText())) {
				requiredMount = dealWithField(false, setup_.getText());
				if (!validDosFilename(setup_))
					return false;
			}
		} else {
			if (img_[0].getText().length() == 0) {
				mess.key("dialog.profile.required.booterimage").bind(img_[0], getTreeItemByControl(img_[0]));
			} else {
				requiredMount = dealWithField(true, img_[0].getText());
				if (!validDosFilename(img_[0]))
					return false;
			}
			for (int i = 1; i < Autoexec.BOOTER_IMAGES; i++) {
				if (img_[i].getText().length() > 0) {
					requiredMount = dealWithField(true, img_[i].getText());
					if (!validDosFilename(img_[i]))
						return false;
				}
			}
		}
		if (requiredMount != null) {
			mess.key("dialog.profile.required.mountlocation").bind(mountingpointsList_, getTreeItemByControl(mountingpointsList_));
		}
		applyPendingDosboxVersion(); // a newly selected DOSBox version is applied like 'Set' does, instead of asking
		usePc98MachineIfNeeded(); // e.g. a .HDI added through the mount dialog
		validateMounts(profile_, mess);
		return mess.valid();
	}

	private String dealWithField(boolean booter, String target) {
		String requiredMount = profile_.getRequiredMount(booter, target, true, false);
		if (requiredMount != null && Mess_.on(shell_).key("dialog.profile.confirm.addmountlocation", requiredMount).confirm()) {
			profile_.addRequiredMount(booter, target, false);
			mountingpointsList_.setItems(profile_.getMountStringsForUI());
			usePc98MachineForHdi(target);
		}
		return profile_.getRequiredMount(booter, target, true, false);
	}

	/**
	 * DOSBox-X can only use PC-98 hard disk images (.HDI) in PC-98 mode. When one is used for this profile and DOSBox-X does
	 * not run as a PC-98 yet, set machine=pc98.
	 */
	private void usePc98MachineForHdi(String target) {
		String image = FilesUtils.fatImageIndex(target) != -1 ? target.substring(0, FilesUtils.fatImageIndex(target)): target;
		if (FilesUtils.isPc98Image(image))
			usePc98MachineIfNeeded();
	}

	private void usePc98MachineIfNeeded() {
		// only the pages shown so far: the others changed nothing (before alpha 47 all 50 pages were built here first, which took
		// two seconds on Windows after each OK, for profiles without a .HDI too)
		updateConfigurableByControls(profile_);
		if (profile_.ensurePc98MachineForHdi())
			updateControlsByConfigurable(profile_);
	}

	private boolean validDosFilename(Text text) {
		String filename = profile_.getDosFilename(text.getText());
		if (filename == null)
			return true; // not reached through a DOS drive, e.g. a hard disk image attached by number (imgmount 2) and booted with 'boot -l c'
		if (!ShortFilenameUtils.valid(filename) && !Mess_.on(shell_).key("dialog.profile.confirm.filenameinvalid", filename).confirm())
			return Mess_.on(null).txt(StringUtils.EMPTY).bind(text, getTreeItemByControl(text)).valid();
		return true;
	}
}
