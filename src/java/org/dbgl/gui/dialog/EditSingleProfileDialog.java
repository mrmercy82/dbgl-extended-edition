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
	private final String sendTo_;
	private final boolean editing_;
	private final boolean focusOnTitle_;

	public EditSingleProfileDialog(Shell parent, Profile prof, String sendTo, boolean focusOnTitle) {
		super(parent);
		profile_ = prof == null ? prof: ProfileFactory.create(prof);
		sendTo_ = sendTo;
		focusOnTitle_ = focusOnTitle;

		editing_ = profile_ != null;
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

	@Override
	protected void doPerformDosboxConfAction(DosboxConfAction action, DosboxVersion newDosboxVersion) {
		try {
			StringBuilder warningsLog = new StringBuilder();

			computeAllPages(profile_);
			updateConfigurableByControls(profile_);

			warningsLog.append(newDosboxVersion.resetAndLoadConfiguration());

			if (action == DosboxConfAction.SET) {
				warningsLog.append(profile_.setToDosboxVersion(newDosboxVersion));
			} else if (action == DosboxConfAction.SWITCH) {
				warningsLog.append(profile_.switchToDosboxVersion(newDosboxVersion));
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
	protected void onShellCreated() {
		TreeItem infoItem = addPageGenerator("dialog.template.tab.info", this::createInfoPage);
		addPageGenerator(infoItem, "dialog.profile.tab.custominfo", this::createCustomInfoPage);
		addPageGenerator("dialog.template.tab.mounting", this::createMountingPage);
		TreeItem dosboxItem = addPageGenerator("dialog.template.tab.dosbox", this::createDosboxPage);
		addCommonPageGenerators(dosboxItem);

		addSearchListener(profile_);
		Stream.of(tree_.getItems()).forEach(x -> x.setExpanded(true));
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
								List<WebProfile> webGamesList = engine.getEntries(currTitle, settings_.getValues(engine.getSimpleName(), "platform_filter"));
								if (webGamesList.isEmpty()) {
									Mess_.on(shell_).key("general.notice.searchenginenoresults", new String[] {engine.getName(), currTitle}).display();
								} else if (webGamesList.size() == 1) {
									thisGame = webGamesList.get(0);
								} else {
									thisGame = new BrowseSearchEngineDialog(shell_, currTitle, webGamesList, engine).open();
								}
								if (thisGame != null) {
									computePage(profile_, "dialog.profile.tab.custominfo");
									WebProfile profExt = engine.getEntryDetailedInformation(thisGame);

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
										imageInformation_ = engine.getEntryImages(profExt, ca, ss, forceAllRegionsCoverArt);

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
												new Thread() {
													@Override
													public void run() {
														try {
															ImageData imgData = profExt.getWebImage(j);
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
														} catch (IOException e) {
															// ignore any missing images
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
							} catch (Exception e) {
								Mess_.on(shell_).key("general.error.retrieveinfosearchengine", new String[] {engine.getName(), currTitle, StringRelatedUtils.toString(e)}).exception(e).warning();
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
		return super.createDosboxPage(configurable, parent,
			editing_ ? profile_.getCanonicalCaptures().getPath() : text_.get("dialog.profile.automatic"),
			editing_ ? profile_.getConfigurationCanonicalFile().getPath() : 
				SettingsDialog.getConfLocations().get(settings_.getIntValue("profiledefaults", "confpath")) + ", " + 
				SettingsDialog.getConfFilenames().get(settings_.getIntValue("profiledefaults", "conffile")));
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
				computePage(profile_, "dialog.template.tab.mounting");
				computePage(profile_, "dialog.template.tab.dosbox");
				computePage(profile_, "dialog.template.tab.startup");
				if (!isValid()) {
					return;
				}
				try {
					updateConfigurableByControls(profile_);

					if (editing_) {
						new ProfileRepository().update(profile_);
						result_ = profile_;
					} else {
						result_ = new ProfileRepository().add(profile_);
					}

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
		if (setButton_.isEnabled()) {
			mess.key("dialog.template.required.dosboxassociation").bind(setButton_, getTreeItemByControl(setButton_));
		}
		validateMounts(profile_, mess);
		return mess.valid();
	}

	private String dealWithField(boolean booter, String target) {
		String requiredMount = profile_.getRequiredMount(booter, target, true, false);
		if (requiredMount != null && Mess_.on(shell_).key("dialog.profile.confirm.addmountlocation", requiredMount).confirm()) {
			profile_.addRequiredMount(booter, target, false);
			mountingpointsList_.setItems(profile_.getMountStringsForUI());
		}
		return profile_.getRequiredMount(booter, target, true, false);
	}

	private boolean validDosFilename(Text text) {
		String filename = profile_.getDosFilename(text.getText());
		if (!ShortFilenameUtils.valid(filename) && !Mess_.on(shell_).key("dialog.profile.confirm.filenameinvalid", filename).confirm())
			return Mess_.on(null).txt(StringUtils.EMPTY).bind(text, getTreeItemByControl(text)).valid();
		return true;
	}
}
