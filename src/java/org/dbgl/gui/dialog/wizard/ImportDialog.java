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

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.constants.Constants;
import org.dbgl.gui.abstractdialog.JobWizardDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.GridData_;
import org.dbgl.gui.controls.Group_;
import org.dbgl.gui.controls.Label_;
import org.dbgl.gui.controls.List_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.Table_;
import org.dbgl.gui.thread.ImportThread;
import org.dbgl.model.GamePack;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.entity.GamePackEntry;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ImportExportProfilesService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.XmlUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;


public class ImportDialog extends JobWizardDialog<Boolean> {

	private Button fullGames_, fullSettingsButton_;
	private Button importCapturesButton_, importMapperfilesButton_, importNativeCommandsButton_, useOrgConf_;
	private Button customValues_, customFields_;
	private Table profilesTable_, impDbVersionsList_;

	private List<DosboxVersion> dbversionsList_;
	private List<File> archive_;
	private List<GamePack> gamePackList_;
	private GamePack gamePack_;
	private StringBuilder messageLog_;

	private List<Integer> dbmapping_; // mapping to dbversion IDs

	public ImportDialog(Shell parent, List<DosboxVersion> dbList, File archive, String[] fileNames) {
		super(parent, "import");
		dbversionsList_ = dbList;

		if (fileNames != null && fileNames.length > 0) {
			archive_ = Stream.of(fileNames).map(File::new).toList();
		} else {
			archive_ = Collections.singletonList(archive);
		}
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.import.title");
	}

	@Override
	protected boolean prepare() {
		messageLog_ = new StringBuilder();
		try {
			gamePackList_ = new ArrayList<>();

			Set<DosboxVersion> combinedDosboxVersions = new LinkedHashSet<>();
			for (File archiveFile: archive_) {
				Document doc = getProfilesXmlDocFromZip(archiveFile);
				if (doc == null)
					throw new ZipException(text_.get("dialog.import.error.gamepackarchivemissingprofilesxml"));

				GamePack gamePack = new GamePack(archiveFile);
				gamePack.setDosboxVersions(combinedDosboxVersions);
				messageLog_.append(ImportExportProfilesService.doImport(doc, gamePack));
				combinedDosboxVersions = gamePack.getDosboxVersions();
				gamePackList_.add(gamePack);

				messageLog_.append(text_.get("dialog.import.notice.importinformation",
					new Object[] {archiveFile.getName(), gamePack.getVersion(), gamePack.getCreationDate(), gamePack.getCreationApp(), gamePack.getCreationAppVersion()})).append(StringUtils.LF);
			}

			gamePack_ = combineGamePacks();
		} catch (ParserConfigurationException | SAXException | ZipException e) {
			Mess_.on(getParent()).exception(e).fatal();
			return false;
		} catch (IOException e) {
			messageLog_.append(text_.get("general.error.openfile", new Object[] {archive_})).append(StringUtils.LF).append(e.toString()).append(StringUtils.LF);
			Mess_.on(getParent()).txt(messageLog_.toString()).exception(e).fatal();
			return false;
		} catch (ParseException e) {
			messageLog_.append(e.toString()).append(StringUtils.LF);
			e.printStackTrace();
		} catch (XPathExpressionException e) {
			messageLog_.append(text_.get("dialog.import.error.profilesxmlinvalidformat", new Object[] {e.toString()})).append(StringUtils.LF);
			e.printStackTrace();
		}

		return true;
	}

	public Document getProfilesXmlDocFromZip(File archiveFile) throws IOException, ParserConfigurationException, SAXException {
		DocumentBuilder builder = XmlUtils.getDocumentBuilder();
		String archive = archiveFile.getPath();

		if (FilesUtils.isZipFile(archive)) {
			try (ZipFile zf = new ZipFile(archive)) {
				for (Enumeration<? extends ZipEntry> entries = zf.entries(); entries.hasMoreElements();) {
					ZipEntry entry = entries.nextElement();
					if (!entry.isDirectory() && entry.getName().equalsIgnoreCase(ImportExportProfilesService.PROFILES_XML)) {
						return builder.parse(zf.getInputStream(entry));
					}
				}
			}
		}

		return null;
	}

	private GamePack combineGamePacks() {
		if (gamePackList_.isEmpty())
			return null;
		if (gamePackList_.size() == 1)
			return gamePackList_.get(0);

		GamePack combined = new GamePack();
		try {
			combined.setVersion(String.valueOf(gamePackList_.stream().mapToDouble(x -> Double.parseDouble(x.getVersion())).min()));
		} catch (Exception e) {
			combined.setVersion(ImportExportProfilesService.PROFILES_XML_FORMAT_VERSION);
		}
		combined.setCustomFieldTitles(new String[Constants.EDIT_COLUMN_NAMES]);
		for (int i = 0; i < Constants.EDIT_COLUMN_NAMES; i++) {
			final int idx = i;
			Optional<String> val = gamePackList_.stream().map(x -> x.getCustomFieldTitles()[idx]).filter(StringUtils::isNotBlank).findFirst();
			combined.getCustomFieldTitles()[idx] = val.isPresent() ? val.get(): StringUtils.EMPTY;
		}

		combined.setCreationApp(Constants.PROGRAM_NAME_FULL);
		combined.setCreationAppVersion(Constants.PROGRAM_VERSION);
		combined.setCreationDate(new Date());
		combined.setTitle(gamePackList_.stream().map(GamePack::getTitle).collect(Collectors.joining(", ")));
		combined.setAuthor(gamePackList_.stream().map(GamePack::getAuthor).distinct().collect(Collectors.joining(", ")));
		combined.setNotes(gamePackList_.stream().map(x -> "=== " + x.getTitle() + " ===" + StringUtils.LF + x.getNotes()).collect(Collectors.joining(Text.DELIMITER)));
		combined.setCapturesAvailable(gamePackList_.stream().anyMatch(GamePack::isCapturesAvailable));
		combined.setMapperfilesAvailable(gamePackList_.stream().anyMatch(GamePack::isMapperfilesAvailable));
		combined.setNativecommandsAvailable(gamePackList_.stream().anyMatch(GamePack::isNativecommandsAvailable));
		combined.setGamedataAvailable(gamePackList_.stream().anyMatch(GamePack::isGamedataAvailable));
		combined.setDosboxVersions(gamePackList_.stream().flatMap(x -> x.getDosboxVersions().stream()).collect(Collectors.toSet()));
		combined.setEntries(gamePackList_.stream().flatMap(x -> x.getEntries().stream()).toList());

		return combined;
	}

	@Override
	protected boolean onNext(int step) {
		if (step == 2) {
			return conditionsForStep3Ok() && refillImportedDBVersionsList();
		} else if (step == 4) {
			try {
				// check for equal gamedirs, if there are, set importedid to the first
				for (int i = 0; i < gamePack_.getEntries().size(); i++) {
					for (int j = 0; j < i; j++) {
						GamePackEntry pI = gamePack_.getEntries().get(i);
						GamePackEntry pJ = gamePack_.getEntries().get(j);
						if (pI.getGameDir().equals(pJ.getGameDir())) {
							pI.setImportedId(pJ.getImportedId());
						}
					}
				}

				// set correct dosboxversion
				for (int i = gamePack_.getEntries().size() - 1; i >= 0; i--) {
					TableItem it = profilesTable_.getItem(i);
					if (it.getChecked()) {
						GamePackEntry entry = gamePack_.getEntries().get(i);
						int dosboxId = entry.getProfile().getDosboxVersion().getId();
						int dosboxIndex = BaseRepository.findIndexById(new ArrayList<>(gamePack_.getDosboxVersions()), dosboxId);
						entry.getProfile().setDosboxVersion(BaseRepository.findById(dbversionsList_, dbmapping_.get(dosboxIndex)));
					} else {
						gamePack_.getEntries().remove(i);
					}
				}

				for (GamePackEntry entry: gamePack_.getEntries()) {
					GamePack gamePack = entry.getGamePack();
					gamePack.setCapturesAvailable(gamePack.isCapturesAvailable() && importCapturesButton_.getSelection());
					gamePack.setMapperfilesAvailable(gamePack.isMapperfilesAvailable() && importMapperfilesButton_.getSelection());
					gamePack.setNativecommandsAvailable(gamePack.isNativecommandsAvailable() && importNativeCommandsButton_.getSelection());
					gamePack.setGamedataAvailable(gamePack.isGamedataAvailable() && fullGames_.getSelection());
				}

				job_ = new ImportThread(log_, progressBar_, status_, gamePack_, useOrgConf_.getSelection(), fullSettingsButton_.getSelection(), customValues_.getSelection(),
						customFields_.getSelection());

			} catch (IOException e) {
				Mess_.on(shell_).exception(e).warning();
				job_ = null;
			}
		} else if (step == 5) {
			if (job_.isEverythingOk()) {
				Mess_.on(shell_).key("dialog.import.notice.importok").display();
			} else {
				Mess_.on(shell_).key("dialog.import.error.problem").warning();
			}
			status_.setText(text_.get("dialog.export.reviewlog"));
			status_.pack();

			result_ = Boolean.valueOf(job_ != null && customFields_.getSelection());
		}
		return true;
	}

	private boolean conditionsForStep3Ok() {
		Mess_.Builder mess = Mess_.on(shell_);
		if (Stream.of(profilesTable_.getItems()).noneMatch(TableItem::getChecked))
			mess.key("dialog.import.required.oneprofiletoimport").bind(profilesTable_);

		if (fullGames_.getSelection()) {
			for (int i = 0; i < profilesTable_.getItemCount(); i++) {
				if (profilesTable_.getItem(i).getChecked()) {
					GamePackEntry entry = gamePack_.getEntries().get(i);
					if (entry.getCanonicalFullDir().exists())
						mess.key("dialog.import.error.gamedatadirexists", new Object[] {entry.getCanonicalFullDir()}).bind(profilesTable_);

					for (int j = 0; j < i; j++) {
						if (profilesTable_.getItem(j).getChecked()) {
							GamePackEntry pJ = gamePack_.getEntries().get(j);
							if (entry.getGameDir().equals(pJ.getGameDir()) && entry.getBaseDir().equals(pJ.getBaseDir()) && (entry.getGamePack() != pJ.getGamePack())) {
								mess.key("dialog.import.error.gamedatadirequal", new Object[] {entry.getProfile().getTitle(), pJ.getProfile().getTitle(), entry.getGameDir()}).bind(profilesTable_);
							}
						}
					}
				}
			}
		}
		return mess.valid();
	}

	private boolean refillImportedDBVersionsList() {
		int idx = 0;
		for (DosboxVersion dbversion: gamePack_.getDosboxVersions()) {
			int dbid = dbversion.getId();
			TableItem ti = impDbVersionsList_.getItem(idx);
			ti.setForeground(isUsed(dbid) ? null: display_.getSystemColor(SWT.COLOR_GRAY));
			idx++;
		}
		return true;
	}

	private boolean isUsed(int dbVersionId) {
		for (int i = 0; i < gamePack_.getEntries().size(); i++) {
			int dbid = gamePack_.getEntries().get(i).getProfile().getDosboxVersion().getId();
			if (profilesTable_.getItem(i).getChecked() && dbVersionId == dbid)
				return true;
		}
		return false;
	}

	@Override
	protected void onShellCreated() {
		Group step1 = Group_.on(shell_).layout(new GridLayout(2, false)).key("dialog.import.step1").build();
		Chain.on(step1).lbl(l -> l.key("dialog.export.exporttitle")).txt(t -> t.val(gamePack_.getTitle()).nonEditable()).build();
		Chain.on(step1).lbl(l -> l.key("dialog.export.author")).txt(t -> t.val(gamePack_.getAuthor()).nonEditable()).build();
		Chain.on(step1).lbl(l -> l.key("dialog.export.notes")).txt(t -> t.multi().wrap().readOnly().val(gamePack_.getNotes()));
		Text confLogText = Chain.on(step1).lbl(l -> l.key("dialog.import.log")).txt(t -> t.multi().wrap().readOnly().val(messageLog_.toString())).text();
		confLogText.setLayoutData(new GridData_(SWT.FILL, SWT.BOTTOM, true, false).heightHint(confLogText.getLineHeight() * 2).build());
		addStep(step1);

		Group page2 = Group_.on(shell_).layout(new GridLayout(2, false)).key("dialog.import.step2").build();
		Label_.on(page2).key("dialog.import.import").build();
		Group settingsOrGamesGroup = Group_.on(page2).layout(new GridLayout()).build();
		Button_.on(settingsOrGamesGroup).radio().key("dialog.export.export.profiles").select(!gamePack_.isGamedataAvailable()).build();
		fullGames_ = Button_.on(settingsOrGamesGroup).radio().key("dialog.export.export.games").select(gamePack_.isGamedataAvailable()).ctrl();
		fullGames_.setEnabled(gamePack_.isGamedataAvailable());
		Label_.on(page2).build();
		Group confGroup = Group_.on(page2).layout(new GridLayout()).build();
		Button_.on(confGroup).radio().key("dialog.import.import.incrconf").select(true).build();
		fullSettingsButton_ = Button_.on(confGroup).radio().key("dialog.import.import.fullconf").ctrl();
		Label_.on(page2).build();
		Group extrasGroup = Group_.on(page2).layout(new GridLayout()).build();
		importCapturesButton_ = Button_.on(extrasGroup).key("dialog.template.captures").select(gamePack_.isCapturesAvailable()).ctrl();
		importCapturesButton_.setEnabled(gamePack_.isCapturesAvailable());
		importMapperfilesButton_ = Button_.on(extrasGroup).key("dialog.template.mapperfile").select(gamePack_.isMapperfilesAvailable()).ctrl();
		importMapperfilesButton_.setEnabled(gamePack_.isMapperfilesAvailable());
		importNativeCommandsButton_ = Button_.on(extrasGroup).key("dialog.export.nativecommands").select(gamePack_.isCapturesAvailable()).ctrl();
		importNativeCommandsButton_.setEnabled(gamePack_.isNativecommandsAvailable());
		Label_.on(page2).build();
		Group customGroup = Group_.on(page2).layout(new GridLayout()).build();
		customValues_ = Button_.on(customGroup).key("dialog.import.import.customvalues").select(gamePack_.isCapturesAvailable()).select(true).ctrl();
		customFields_ = Button_.on(customGroup).key("dialog.import.import.customfields").select(gamePack_.isCapturesAvailable()).ctrl();
		Label_.on(page2).key("dialog.main.profile.view.conf").build();
		Group existingConfGroup = Group_.on(page2).layout(new GridLayout()).build();
		boolean useGameDirForConfFile = settings_.getIntValue("profiledefaults", "confpath") == 1;
		useOrgConf_ = Button_.on(existingConfGroup).radio().key("dialog.import.useorgconf").select(useGameDirForConfFile).ctrl();
		useOrgConf_.setEnabled(useGameDirForConfFile);
		Button_.on(existingConfGroup).radio().key("dialog.import.createnewconf").ctrl().setEnabled(useGameDirForConfFile);
		addStep(page2);

		Group profilesGroup = Group_.on(shell_).layout(new GridLayout(2, false)).key("dialog.import.step3").build();
		profilesTable_ = Table_.on(profilesGroup).layoutData(new GridData_(SWT.FILL, SWT.FILL, true, true, 1, 2).heightHint(80).build()).check().header().build();
		profilesTable_.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent event) {
				int idx = profilesTable_.getSelectionIndex();
				GamePackEntry entry = gamePack_.getEntries().get(idx);
				DirectoryDialog dialog = new DirectoryDialog(shell_);
				dialog.setFilterPath(entry.getCanonicalBaseDir().getPath());
				String result = dialog.open();
				if (result != null) {
					entry.setBaseDir(result);
					profilesTable_.getSelection()[0].setText(1, entry.getBaseDir().getPath());
				}
			}
		});
		createTableColumn(profilesTable_, 260, "dialog.main.profiles.column.title");
		createTableColumn(profilesTable_, 100, "dialog.import.column.basedir");
		createTableColumn(profilesTable_, 120, "dialog.export.column.gamedir");
		for (GamePackEntry entry: gamePack_.getEntries()) {
			TableItem item = new TableItem(profilesTable_, SWT.NONE);
			item.setText(entry.getProfile().getTitle());
			item.setText(1, entry.getBaseDir().getPath());
			item.setText(2, entry.getGameDir().getPath());
			item.setChecked(true);
		}
		Chain.on(profilesGroup).but(b -> b.text().key("button.all").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				for (TableItem item: profilesTable_.getItems())
					item.setChecked(true);
			}
		})).but(b -> b.text().key("button.none").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				for (TableItem item: profilesTable_.getItems())
					item.setChecked(false);
			}
		})).build();
		Composite buttonsGroup = Composite_.on(profilesGroup).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1)).innerLayout(3).build();
		Chain.on(buttonsGroup).but(b -> b.text().key("button.setbasedir").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dialog = new DirectoryDialog(shell_);
				dialog.setFilterPath(FileLocationService.getInstance().getDosroot().getPath());
				String result = dialog.open();
				if (result != null) {
					for (int i = 0; i < profilesTable_.getItems().length; i++) {
						TableItem item = profilesTable_.getItem(i);
						if (item.getChecked()) {
							gamePack_.getEntries().get(i).setBaseDir(result);
							item.setText(1, gamePack_.getEntries().get(i).getBaseDir().getPath());
						}
					}
				}
			}
		})).but(b -> b.text().key("button.addgametitletobasedir").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				for (int i = 0; i < profilesTable_.getItems().length; i++) {
					TableItem item = profilesTable_.getItem(i);
					if (item.getChecked()) {
						gamePack_.getEntries().get(i).appendGameTitleToBaseDir();
						item.setText(1, gamePack_.getEntries().get(i).getBaseDir().getPath());
					}
				}
			}
		})).but(b -> b.text().key("button.removegametitlefrombasedir").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				for (int i = 0; i < profilesTable_.getItems().length; i++) {
					TableItem item = profilesTable_.getItem(i);
					if (item.getChecked()) {
						gamePack_.getEntries().get(i).stripGameTitleFromBaseDir();
						item.setText(1, gamePack_.getEntries().get(i).getBaseDir().getPath());
					}
				}
			}
		})).build();
		addStep(profilesGroup);

		Group dosboxVersionsGroup = Group_.on(shell_).layout(new GridLayout(3, false)).key("dialog.import.step4").build();
		Label_.on(dosboxVersionsGroup).key("dialog.import.dosboxversioninimport").build();
		Label_.on(dosboxVersionsGroup).style(SWT.SEPARATOR | SWT.VERTICAL).layoutData(new GridData(SWT.LEFT, SWT.FILL, false, true, 1, 2)).build();
		Label_.on(dosboxVersionsGroup).key("dialog.import.dosboxversioninstalled").build();
		impDbVersionsList_ = Table_.on(dosboxVersionsGroup).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).build();
		org.eclipse.swt.widgets.List myDbVersionsList = List_.on(dosboxVersionsGroup).build();
		myDbVersionsList.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int idx = impDbVersionsList_.getSelectionIndex();
				if (idx != -1) {
					int myIdx = myDbVersionsList.getSelectionIndex();
					dbmapping_.set(idx, dbversionsList_.get(myIdx).getId());
				}
			}
		});
		impDbVersionsList_.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectDbVersionForSelectedImpDbVersion(myDbVersionsList);
			}
		});
		for (DosboxVersion ver: dbversionsList_) {
			myDbVersionsList.add(ver.getTitle() + " (" + ver.getVersion() + ")");
		}
		dbmapping_ = new ArrayList<>();
		for (DosboxVersion dbversion: gamePack_.getDosboxVersions()) {
			TableItem item = new TableItem(impDbVersionsList_, SWT.NONE);
			item.setText(dbversion.getTitle() + " (" + dbversion.getVersion() + ")");
			dbmapping_.add(DosboxVersionRepository.findBestMatch(dbversionsList_, dbversion).getId());
		}
		impDbVersionsList_.setSelection(0);
		selectDbVersionForSelectedImpDbVersion(myDbVersionsList);
		addStep(dosboxVersionsGroup);

		addFinalStep("dialog.import.step5", "dialog.import.start");
	}

	private void selectDbVersionForSelectedImpDbVersion(org.eclipse.swt.widgets.List myDbVersionsList) {
		int impIdx = impDbVersionsList_.getSelectionIndex();
		int mappedId = dbmapping_.get(impIdx);
		int myIdx = BaseRepository.findIndexById(dbversionsList_, mappedId);
		myDbVersionsList.select(myIdx);
	}
}