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
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.connect.Messaging;
import org.dbgl.constants.Constants;
import org.dbgl.gui.abstractdialog.SizeControlledDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.CTabFolder_;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.MenuItem_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.ProfilesList;
import org.dbgl.gui.controls.ProfilesList.ProfilesListItem;
import org.dbgl.gui.controls.ProfilesList.ProfilesListType;
import org.dbgl.gui.controls.Table_;
import org.dbgl.gui.controls.Text_;
import org.dbgl.gui.dialog.wizard.AddGameWizardDialog;
import org.dbgl.gui.dialog.wizard.DFendImportDialog;
import org.dbgl.gui.dialog.wizard.ExportDialog;
import org.dbgl.gui.dialog.wizard.ImportDialog;
import org.dbgl.gui.dialog.wizard.ImportDirDialog;
import org.dbgl.gui.dialog.wizard.MigrateDialog;
import org.dbgl.gui.interfaces.ReOrderable;
import org.dbgl.gui.thread.DelayedAction;
import org.dbgl.model.OrderingVector;
import org.dbgl.model.SearchResult;
import org.dbgl.model.SearchResult.ResultType;
import org.dbgl.model.ThumbInfo;
import org.dbgl.model.ViewType;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.entity.Filter;
import org.dbgl.model.factory.DosboxVersionFactory;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.FilterRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.model.repository.TemplateRepository;
import org.dbgl.model.repository.TitledEntityRepository;
import org.dbgl.service.DatabaseService;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ImageService;
import org.dbgl.service.ImportExportTemplatesService;
import org.dbgl.service.BackupService;
import org.dbgl.service.SettingsService;
import org.dbgl.util.DosboxVersionDetector;
import org.dbgl.util.ExecuteUtils;
import org.dbgl.util.ExecuteUtils.ProfileRunMode;
import org.dbgl.util.DosboxUpdateChecker;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.xml.sax.SAXException;


public class MainWindow extends SizeControlledDialog<Boolean> implements ReOrderable {

	private static final String[] ICOS_DBGL = {"ico/016.png", "ico/024.png", "ico/032.png", "ico/048.png", "ico/064.png", "ico/128.png", "ico/256.png"};
	private static DelayedAction delayedAction_;

	private String filterClause_;
	private OrderingVector orderingVector_;

	private List<Profile> profilesList_;
	private List<DosboxVersion> dbversionsList_;
	private List<Template> templatesList_;
	private List<Filter> filtersList_;

	private Messaging mess_;

	private String[] columnNames_;
	private int[] columnIds_;

	private ToolItem setupToolItem_, viewSelector_;
	private CTabFolder filterFolder_;
	private ProfilesList profileTable_;
	private Text notesField_;
	private Label playInfo_; // play time, sessions and the last one, below the notes
	private Link[] link_;
	private SashForm sashInfoForm_;
	private ScrolledComposite scrolledCompositeForThumbs_;
	private Composite thumbsComposite_;
	private int thumbHeight_;
	private File currentThumbFile_ = null;
	private Menu thumbMenu_;
	private Table dbversionTable_, templateTable_;

	public MainWindow() {
		super(new Shell(), SWT.PRIMARY_MODAL, StringUtils.EMPTY);
		if (SystemUtils.IS_WINDOWS)
			setTheme(DarkTheme.forced());
		result_ = false;
	}

	public static final void setTheme(boolean isDarkTheme) {
		Display display = Display.getCurrent();
		display.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme", isDarkTheme);
		display.setData("org.eclipse.swt.internal.win32.useShellTitleColoring", isDarkTheme);
		display.setData("org.eclipse.swt.internal.win32.menuBarForegroundColor", isDarkTheme ? new Color(display, 0xD0, 0xD0, 0xD0): null);
		display.setData("org.eclipse.swt.internal.win32.menuBarBackgroundColor", isDarkTheme ? new Color(display, 0x30, 0x30, 0x30): null);
		display.setData("org.eclipse.swt.internal.win32.menuBarBorderColor", isDarkTheme ? new Color(display, 0x50, 0x50, 0x50): null);
		display.setData("org.eclipse.swt.internal.win32.Canvas.use_WS_BORDER", isDarkTheme);
		display.setData("org.eclipse.swt.internal.win32.List.use_WS_BORDER", isDarkTheme);
		display.setData("org.eclipse.swt.internal.win32.Table.use_WS_BORDER", isDarkTheme);
		display.setData("org.eclipse.swt.internal.win32.Text.use_WS_BORDER", isDarkTheme);
		display.setData("org.eclipse.swt.internal.win32.Tree.use_WS_BORDER", isDarkTheme);
		display.setData("org.eclipse.swt.internal.win32.Table.headerLineColor", isDarkTheme ? new Color(display, 0x50, 0x50, 0x50): null);
		display.setData("org.eclipse.swt.internal.win32.Label.disabledForegroundColor", isDarkTheme ? new Color(display, 0x80, 0x80, 0x80): null);
		display.setData("org.eclipse.swt.internal.win32.Combo.useDarkTheme", isDarkTheme);
		display.setData("org.eclipse.swt.internal.win32.ProgressBar.useColors", isDarkTheme);
	}

	@Override
	protected String getDialogTitle() {
		// e.g. "DOSBox Game Launcher - Extended Edition v0.101 alpha 42"
		return Constants.PROGRAM_NAME_FULL + " - " + Constants.EDITION_NAME + " v" + org.dbgl.util.EditionUpdateChecker.readable(Constants.PROGRAM_VERSION);
	}

	@Override
	protected boolean prepare() {
		if (!super.prepare())
			return false;

		try {
			dbversionsList_ = new DosboxVersionRepository().listAll();

			ProfileRepository profileRepo = new ProfileRepository();

			List<Profile> invalidProfiles = profileRepo.listInvalidProfiles(dbversionsList_);
			if (!invalidProfiles.isEmpty()) {
				String titles = invalidProfiles.stream().map(Profile::getTitle).collect(Collectors.joining(", "));
				if (Mess_.on(new Shell()).key("dialog.main.confirm.removeinvalidprofiles", new Object[] {invalidProfiles.size(), titles}).confirm()) {
					for (Profile prof: invalidProfiles)
						profileRepo.remove(prof, false, false, false);
				}
			}

			templatesList_ = new TemplateRepository().listAll(dbversionsList_);

			orderingVector_ = new OrderingVector(settings_.getIntValues("gui", "sortcolumn"), settings_.getBooleanValues("gui", "sortascending"));

			filtersList_ = allFilters(new FilterRepository().listAll());
			int filterIdx = Math.min(settings_.getIntValue("gui", "filtertab"), filtersList_.size() - 1);
			filterClause_ = filtersList_.get(filterIdx).getFilter();

			profilesList_ = listProfiles();
		} catch (Exception e) {
			Mess_.on(new Shell()).exception(e).warning();
			return false;
		}

		if (settings_.getBooleanValue("communication", "port_enabled")) {
			mess_ = new Messaging(settings_.getIntValue("communication", "port"), this, text_);
			mess_.start();
		}

		return true;
	}

	@Override
	protected void onShellCreated() {
		createAppMenuBar();

		shell_.setLayout(new FillLayout());
		shell_.setImages(ImageService.getResourceImages(display_, ICOS_DBGL));

		CTabFolder tabFolder = CTabFolder_.on(shell_).ctrl();
		createProfilesTab(tabFolder);
		createDosboxVersionsTab(tabFolder);
		createTemplatesTab(tabFolder);

		shell_.addListener(SWT.Activate, e -> {
			if (tabFolder.getSelectionIndex() == 0) {
				profileTable_.setFocus();
				displayProfileInformation(true);
			}
		});

		// init values
		profilesList_.forEach(this::addProfileToTable);
		dbversionsList_.forEach(this::addDosboxVersionToTable);
		templatesList_.forEach(this::addTemplateToTable);

		if (DatabaseService.getInstance().isInitializedNewDatabase()) {
			doLocateDosbox(false);
			if (!dbversionsList_.isEmpty())
				doImportDefaultTemplates(false);
		}
	}

	@Override
	protected void onShellOpened() {
		super.onShellOpened();

		profileTable_.setFocus();
		int selected = settings_.getIntValue("gui", "selectedprofile");
		if (selected >= 0)
			profileTable_.setSelection(selected);
		displayProfileInformation(false);

		if (settings_.getBooleanValue("dosbox", "checkversionsatstartup"))
			checkDosboxVersionsInBackground();
		if (settings_.getBooleanValue("dosbox", "checkforupdates"))
			checkForDosboxUpdatesInBackground();
		checkForEditionUpdateInBackground(); // has its own setting
		checkAllProfilesInBackground();

		display_.asyncExec(this::autoBackupIfDue);
		display_.asyncExec(this::cleanUpAfterEditionUpdate);
		// if the window ever stops answering: what DBGL was doing, written to freeze-<date>.txt for a report
		org.dbgl.util.FreezeWatch.start(display_, org.dbgl.service.FileLocationService.getInstance().getDataDir());

		// messages of DOSBox about a setting open the profile dialog at that setting
		org.dbgl.gui.DosboxStatusWindow.setSettingLinkHandler((profileId, setting) -> display_.asyncExec(() -> doEditProfileSetting(profileId, setting)));

		// the play time is added when DOSBox ends; show it
		ExecuteUtils.setPlayTimeListener(id -> {
			if (!shell_.isDisposed() && profilesList_.stream().anyMatch(p -> p.getId() == id)) {
				updateProfilesList(getSelectedProfileIds());
				displayProfileInformation(false);
			}
		});
	}

	/** the automatic backup, if one is due (see the settings) */
	private void autoBackupIfDue() {
		String today = java.time.LocalDate.now().toString();
		if (!BackupService.isAutoBackupDue(settings_.getValue("backup", "auto"), settings_.getValue("backup", "last"), java.time.LocalDate.now()))
			return;
		String dirSetting = settings_.getValue("backup", "dir");
		File dir = StringUtils.isBlank(dirSetting) ? BackupService.defaultAutoBackupDir(): new File(dirSetting);
		try {
			settings_.setValue("backup", "last", today);
			File zip = BackupService.autoBackup(dir, settings_.getIntValue("backup", "keep"));
			System.out.println("Automatic backup: " + zip);
		} catch (IOException | SQLException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	/** open the profile dialog showing a setting, e.g. one DOSBox complained about in the status window */
	private void doEditProfileSetting(int profileId, String setting) {
		if (shell_.isDisposed())
			return;
		int index = -1;
		for (int i = 0; i < profilesList_.size(); i++)
			if (profilesList_.get(i).getId() == profileId)
				index = i;
		if (index == -1)
			return;
		shell_.forceActive();
		profileTable_.setSelection(index);
		Profile profile = new EditSingleProfileDialog(shell_, profilesList_.get(index), null, false).showSetting(setting).open();
		if (profile != null) {
			updateProfileListAfterEdit(index, profile);
			displayProfileInformation(false);
		}
	}

	private void doCompareProfiles() {
		List<Profile> selected = getSelectedProfiles();
		if (selected.size() != 2) {
			Mess_.on(shell_).key("dialog.compareprofiles.selecttwo").warning();
			return;
		}
		try {
			StringBuilder warnings = new StringBuilder();
			for (Profile p: selected)
				warnings.append(p.resetAndLoadConfiguration());
			if (StringUtils.isNotBlank(warnings))
				System.err.println(warnings);
			new DifferencesDialog(shell_, text_.get("dialog.compareprofiles.title"), org.dbgl.util.ProfileReports.compare(selected.get(0), selected.get(1), text_)).withImage(ImageService.IMG_ZOOM).open();
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	/** set the values of a preset in all selected profiles, as far as their DOSBox version has the settings */
	private void doApplyPreset(org.dbgl.util.Presets.Preset preset) {
		List<Profile> selected = getSelectedProfiles();
		if (selected.isEmpty())
			return;
		String name = org.dbgl.gui.abstractdialog.PresetsMenu.nameOf(preset);
		if (!Mess_.on(shell_).key("dialog.main.profile.applypreset.confirm", new Object[] {name, selected.size()}).confirm())
			return;
		org.dbgl.util.BulkUndo.remember(text_.get("dialog.bulkundo.preset", new Object[] {name}), selected);
		int changed = 0;
		StringBuilder problems = new StringBuilder();
		for (Profile prof: selected) {
			try {
				String warnings = prof.resetAndLoadConfiguration();
				if (StringUtils.isNotBlank(warnings))
					System.err.println(warnings);
				if (org.dbgl.util.Presets.apply(prof, preset) > 0) {
					new ProfileRepository().update(prof);
					changed++;
				}
			} catch (IOException | SQLException e) {
				problems.append(prof.getTitle()).append(": ").append(e.getMessage()).append(SystemUtils.EOLN);
			}
		}
		updateProfilesList(getSelectedProfileIds());
		displayProfileInformation(false);
		if (problems.length() > 0)
			Mess_.on(shell_).txt(problems.toString()).warning();
		Mess_.on(shell_).key("dialog.main.profile.applypreset.done", new Object[] {name, changed, selected.size()}).display();
	}

	private void doRestoreProfileVersion() {
		int index = profileTable_.getSelectionIndex();
		if (index == -1)
			return;
		Profile prof = profilesList_.get(index);
		if (!org.dbgl.util.ProfileWarnings.confMissing(prof)) { // its file gone: an earlier version is just what can bring it back
			try {
				String warnings = prof.resetAndLoadConfiguration();
				if (StringUtils.isNotBlank(warnings))
					System.err.println(warnings);
			} catch (IOException e) {
				Mess_.on(shell_).exception(e).warning();
				return;
			}
		}
		if (Boolean.TRUE.equals(new ProfileHistoryDialog(shell_, prof).open())) {
			org.dbgl.util.ProfileWarnings.forget(prof.getId());
			updateProfilesList(getSelectedProfileIds());
			displayProfileInformation(false);
		}
	}

	private void doImportConf(int index, String file) {
		if (index == -1)
			return;
		if (file == null) {
			FileDialog dialog = new FileDialog(shell_, SWT.OPEN);
			dialog.setFilterExtensions(new String[] {"*.conf;*.cfg;*.ini", "*.*"});
			file = dialog.open();
			if (file == null)
				return;
		}
		Profile prof = profilesList_.get(index);
		try {
			String warnings = prof.resetAndLoadConfiguration();
			if (StringUtils.isNotBlank(warnings))
				System.err.println(warnings);
			String confText = java.nio.file.Files.readString(new File(file).toPath(), java.nio.charset.StandardCharsets.ISO_8859_1);
			org.dbgl.util.ProfileReports.ImportPlan plan = org.dbgl.util.ProfileReports.planImport(prof, confText);
			if ((plan.count() == 0) && plan.unknown().isEmpty()) {
				Mess_.on(shell_).key("dialog.importconf.nothing", new Object[] {new File(file).getName()}).display();
				return;
			}
			String title = text_.get("dialog.importconf.title", new Object[] {new File(file).getName()});
			if (!Boolean.TRUE.equals(new ConfirmTextDialog(shell_, title, org.dbgl.util.ProfileReports.describe(prof, plan, text_), "dialog.importconf.apply").open()) || (plan.count() == 0))
				return;
			org.dbgl.util.ProfileReports.applyImport(prof, plan);
			new ProfileRepository().update(prof);
			updateProfilesList(getSelectedProfileIds());
			displayProfileInformation(false);
		} catch (IOException | SQLException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	/**
	 * remove what is left of removed profiles (play sessions, log entries), after asking
	 *
	 * @return the statistics again, or null if nothing was removed
	 */
	private String cleanRemovedProfileData(Shell shell, List<Profile> all, java.util.Locale locale) {
		try {
			ProfileRepository repo = new ProfileRepository();
			int[] counts = repo.countRemovedProfileData();
			if ((counts[0] == 0) && (counts[1] == 0)) {
				Mess_.on(shell).key("dialog.statistics.cleanremoved.nothing").display();
				return null;
			}
			if (!Mess_.on(shell).key("dialog.statistics.cleanremoved.confirm", new Object[] {counts[0], counts[1]}).confirm())
				return null;
			repo.removeRemovedProfileData();
			Mess_.on(shell).key("dialog.statistics.cleanremoved.done", new Object[] {counts[0], counts[1]}).display();
			return org.dbgl.util.ProfileReports.statistics(all, repo.listPlaySessions(), text_, locale, new java.util.Date());
		} catch (SQLException e) {
			Mess_.on(shell).exception(e).warning();
			return null;
		}
	}

	/** a toolbar button like those at the left (symbol with its name below), with a longer tooltip */
	private ToolItem createLabelledToolItem(ToolBar toolBar, String key, String img, String tooltipKey, SelectionAdapter listener) {
		ToolItem item = createImageToolItem(toolBar, key, img, listener);
		if (settings_.getIntValue("gui", "buttondisplay") != 2)
			item.setToolTipText(tip(tooltipKey));
		return item;
	}

	/** a menu text as a tooltip: without the mnemonic '&' */
	private String tip(String key) {
		return text_.get(key).replace("&", "");
	}

	private void doCheckProfiles() {
		if (Boolean.TRUE.equals(new CheckProfilesDialog(shell_).open())) {
			updateProfilesList(getSelectedProfileIds());
			displayProfileInformation(false);
		}
	}

	/** the profile dialog of the selected profile, asking the game database right away; several profiles: their dialog */
	private void doOnlineInfo() {
		int index = profileTable_.getSelectionIndex();
		if (index == -1) {
			Mess_.on(shell_).key("dialog.main.onlineinfo.select").display();
			return;
		}
		if (profileTable_.getSelectionCount() > 1) {
			doEditProfile(false);
			return;
		}
		Profile profile = new EditSingleProfileDialog(shell_, profilesList_.get(index), null, false).consultSearchEngine().open();
		if (profile != null) {
			updateProfileListAfterEdit(index, profile);
			displayProfileInformation(false);
		}
	}

	/** which games the random game is picked from: all, never started ones, favorites */
	private static final String[] RANDOM_MODES = {"all", "neverstarted", "favorites", "mostplayed", "leastplayed"};

	private String randomMode() {
		String mode = settings_.getValue("gui", "randomgame");
		return java.util.Arrays.asList(RANDOM_MODES).contains(mode) ? mode: RANDOM_MODES[0];
	}

	/** 'Random game' with an arrow next to it: from which games */
	private void createRandomToolItem(ToolBar toolBar) {
		ToolItem item = new ToolItem(toolBar, SWT.DROP_DOWN);
		int displaySelection = settings_.getIntValue("gui", "buttondisplay");
		if (displaySelection != 1)
			item.setImage(ImageService.getResourceImage(toolBar.getDisplay(), ImageService.IMG_RANDOM));
		if (displaySelection != 2)
			item.setText(text_.get("dialog.main.tb.random"));
		Menu modeMenu = new Menu(shell_, SWT.POP_UP);
		Runnable tooltip = () -> item.setToolTipText(text_.get("dialog.main.random.tooltip2", new Object[] {text_.get("dialog.main.random.mode." + randomMode())}));
		for (String mode: RANDOM_MODES) {
			MenuItem menuItem = new MenuItem(modeMenu, SWT.RADIO);
			menuItem.setData(mode);
			menuItem.setText(text_.get("dialog.main.random.mode." + mode));
			menuItem.setSelection(mode.equals(randomMode()));
			menuItem.addListener(SWT.Selection, e -> {
				if (menuItem.getSelection()) {
					settings_.setValue("gui", "randomgame", mode);
					tooltip.run();
				}
			});
		}
		tooltip.run();
		// what the list (as filtered) does not have is grayed out, e.g. no favorites, nothing played yet
		modeMenu.addListener(SWT.Show, e -> {
			for (MenuItem m: modeMenu.getItems())
				m.setEnabled(!randomCandidates((String)m.getData()).isEmpty());
		});
		item.addListener(SWT.Selection, e -> {
			if (e.detail == SWT.ARROW) {
				Rectangle rect = item.getBounds();
				Point pt = toolBar.toDisplay(new Point(rect.x, rect.y + rect.height));
				modeMenu.setLocation(pt.x, pt.y);
				modeMenu.setVisible(true);
			} else {
				doRandomGame();
			}
		});
	}

	/** pick a game of the list (as filtered, and as set at the arrow: all, never started, favorites) by chance: start it, or roll again */
	private void doRandomGame() {
		if (profilesList_.isEmpty()) {
			Mess_.on(shell_).key("dialog.main.random.none").display();
			return;
		}
		String mode = randomMode();
		List<Integer> candidates = randomCandidates(mode);
		if (candidates.isEmpty()) {
			Mess_.on(shell_).key("dialog.main.random.nonemode", new Object[] {text_.get("dialog.main.random.mode." + mode)}).display();
			return;
		}
		java.util.Random random = new java.util.Random();
		int last = -1;
		while (!shell_.isDisposed()) {
			int pick = random.nextInt(candidates.size());
			if ((pick == last) && (candidates.size() > 1))
				pick = (pick + 1 + random.nextInt(candidates.size() - 1)) % candidates.size();
			last = pick;
			int index = candidates.get(pick);
			profileTable_.setSelection(index);
			displayProfileInformation(false);
			org.eclipse.swt.widgets.MessageBox box = new org.eclipse.swt.widgets.MessageBox(shell_, SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
			box.setText(text_.get("dialog.main.random.title"));
			box.setMessage(text_.get("dialog.main.random.question2", new Object[] {profilesList_.get(index).getTitle(), text_.get("dialog.main.random.mode." + mode), candidates.size()}));
			box.setButtonLabels(java.util.Map.of(SWT.YES, text_.get("dialog.main.random.start"), SWT.NO, text_.get("dialog.main.random.again"), SWT.CANCEL, text_.get("button.cancel")));
			int answer = box.open();
			if (answer == SWT.YES)
				doRunProfile(ProfileRunMode.NORMAL, false);
			if (answer != SWT.NO)
				return;
		}
	}

	/** the games of the list a random game is picked from (indexes in the list); empty: none fits */
	private List<Integer> randomCandidates(String mode) {
		List<Integer> candidates = new ArrayList<>();
		for (int i = 0; i < profilesList_.size(); i++) {
			Profile p = profilesList_.get(i);
			if ("all".equals(mode) || ("neverstarted".equals(mode) && p.getStats().getRuns() == 0) || ("favorites".equals(mode) && Boolean.TRUE.equals(p.isFavorite())))
				candidates.add(i);
		}
		// most / least played: the list by play time (then starts) in two halves of the same size (with an odd number, the middle one in both)
		if ("mostplayed".equals(mode) || "leastplayed".equals(mode)) {
			java.util.function.ToLongFunction<Integer> played = i -> {
				Profile p = profilesList_.get(i);
				long seconds = p.getProfileStats() == null ? 0: p.getProfileStats().getPlayTime();
				return seconds * 100_000L + p.getStats().getRuns();
			};
			List<Integer> sorted = new ArrayList<>();
			for (int i = 0; i < profilesList_.size(); i++)
				sorted.add(i);
			sorted.sort(java.util.Comparator.comparingLong(played).reversed());
			int half = (sorted.size() + 1) / 2;
			// halves only make sense when something was played at all
			if (sorted.stream().anyMatch(i -> played.applyAsLong(i) > 0) && (sorted.size() > 1))
				candidates.addAll("mostplayed".equals(mode) ? sorted.subList(0, half): sorted.subList(sorted.size() - half, sorted.size()));
		}
		return candidates;
	}

	/** the statistics as a web page (or a CSV table), with the play sessions as they are now */
	private String statisticsAs(boolean html, List<Profile> all, java.util.Locale locale, java.util.Date now) {
		try {
			List<ProfileRepository.PlaySession> sessions = new ProfileRepository().listPlaySessions();
			return html ? org.dbgl.util.ProfileReports.statisticsHtml(all, sessions, text_, locale, now): org.dbgl.util.ProfileReports.statisticsCsv(all, sessions, text_, locale, now);
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	private void doShowStatistics() {
		try {
			List<Profile> all = new ProfileRepository().list(StringUtils.EMPTY, StringUtils.EMPTY, dbversionsList_);
			java.util.Date now = new java.util.Date();
			java.util.Locale locale = new java.util.Locale(settings_.getValue("locale", "language"), settings_.getValue("locale", "country"));
			String report = org.dbgl.util.ProfileReports.statistics(all, new ProfileRepository().listPlaySessions(), text_, locale, now);
			new DifferencesDialog(shell_, text_.get("dialog.statistics.title"), report, "dbgl-statistics-" + new java.text.SimpleDateFormat("yyyy-MM-dd").format(now) + ".txt",
					"statisticsdialog").withImage(ImageService.IMG_STATISTICS).withAction("dialog.statistics.cleanremoved", s -> cleanRemovedProfileData(s, all, locale))
					// also as a web page with bars, or as a table for a spreadsheet (the type in the save dialog)
					.withExport("html", text_.get("dialog.statistics.save.html3"), () -> statisticsAs(true, all, locale, now))
					.withExport("csv", text_.get("dialog.statistics.save.csv"), () -> statisticsAs(false, all, locale, now)).open();
		} catch (SQLException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	/**
	 * Once a day, look on GitHub for newer releases of DOSBox Staging and DOSBox-X than the installed ones, and tell about each
	 * new release once. Nothing is downloaded, the download page can be opened.
	 */
	/**
	 * Check all profiles once in the background after the start (not only those in the list), so the tab 'With problems' opens
	 * without waiting. Profiles are only checked again when their configuration changes.
	 */
	/**
	 * With "ask" (see {@link org.dbgl.util.UnknownAutoexecLines}): before a profile is edited, let the user choose what happens
	 * to the lines of its [autoexec] DBGL does not know. The choice is saved to the configuration file right away. Cancelled,
	 * everything stays as it is and the user is asked again next time.
	 *
	 * @param loaded the profile was read already and is edited as it is (several profiles), else it is read here
	 */
	private void askUnknownAutoexecLines(Profile prof, boolean loaded) {
		if (!org.dbgl.util.UnknownAutoexecLines.ask())
			return;
		try {
			Profile p = loaded ? prof: org.dbgl.model.factory.ProfileFactory.create(prof);
			if (!loaded)
				p.resetAndLoadConfiguration();
			List<org.dbgl.model.conf.Autoexec.UnknownLine> open = org.dbgl.util.UnknownAutoexecLines.undecided(p);
			if (open.isEmpty())
				return;
			List<org.dbgl.util.UnknownAutoexecLines.Decision> decisions = new UnknownLinesDialog(shell_, p.getTitle(), open).open();
			if (decisions == null)
				return;
			org.dbgl.util.UnknownAutoexecLines.decide(p, decisions, true);
			p.saveConfiguration();
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	/** warning signs in the list, or notes for profiles with hints (each can be turned on in the settings) */
	private boolean markersOn() {
		return settings_.getBooleanValue("gui", "profilewarnings") || settings_.getBooleanValue("gui", "profilehints");
	}

	/** the warning sign, or the note of hints (only if turned on), as known for the profile now */
	private void setMarker(ProfilesList.ProfilesListItem item, Profile prof) {
		// not checked since it changed: as the last check found it, until it was checked again (then set anew)
		List<String> problems = org.dbgl.util.ProfileWarnings.lastKnown(prof), hints = org.dbgl.util.ProfileWarnings.lastKnownHints(prof);
		boolean warning = settings_.getBooleanValue("gui", "profilewarnings") && (problems != null) && !problems.isEmpty();
		boolean hint = settings_.getBooleanValue("gui", "profilehints") && (hints != null) && !hints.isEmpty();
		item.setMarker(warning, warning && org.dbgl.util.ProfileWarnings.confMissing(prof), hint);
	}

	private void checkAllProfilesInBackground() {
		if (!markersOn())
			return;
		List<DosboxVersion> versions = new ArrayList<>(dbversionsList_);
		Thread checker = new Thread(() -> {
			try {
				for (Profile prof: new ProfileRepository().list("", null, versions)) {
					if (display_.isDisposed())
						return;
					org.dbgl.util.ProfileWarnings.check(prof);
					showWarningSign(prof); // also to take a sign away the list still showed from the last check
				}
			} catch (SQLException e) {
				// the list or tab will check them when needed
			}
		}, "DBGL check all profiles");
		checker.setDaemon(true);
		checker.setPriority(Thread.MIN_PRIORITY);
		checker.start();
	}

	/** once a day, if turned on: is a newer version of the Extended Edition out? Each new version is told about once */
	private void checkForEditionUpdateInBackground() {
		String section = org.dbgl.util.EditionUpdateChecker.SECTION;
		if (!settings_.getBooleanValue(section, org.dbgl.util.EditionUpdateChecker.CHECK))
			return;
		String today = java.time.LocalDate.now().toString();
		if (today.equals(settings_.getValue(section, org.dbgl.util.EditionUpdateChecker.CHECKED)))
			return;
		boolean prereleases = settings_.getBooleanValue(section, org.dbgl.util.EditionUpdateChecker.PRERELEASES);
		Thread checker = new Thread(() -> {
			org.dbgl.util.EditionUpdateChecker.Result result = org.dbgl.util.EditionUpdateChecker.check(prereleases);
			if (result.error() != null) {
				System.out.println("Extended Edition update check failed: " + result.error()); // tried again at the next start
				return;
			}
			if (!display_.isDisposed())
				display_.asyncExec(() -> settings_.setValue(section, org.dbgl.util.EditionUpdateChecker.CHECKED, today));
			if ((result.newer() == null) || display_.isDisposed())
				return;
			display_.asyncExec(() -> {
				if (shell_.isDisposed() || result.newer().version().equals(settings_.getValue(section, org.dbgl.util.EditionUpdateChecker.SHOWN)))
					return;
				settings_.setValue(section, org.dbgl.util.EditionUpdateChecker.SHOWN, result.newer().version());
				offerEditionRelease(result.newer());
			});
		}, "Extended Edition update check");
		checker.setDaemon(true);
		checker.start();
	}

	/** Help -> check for updates of the Extended Edition, right now */
	private void doCheckForEditionUpdate() {
		boolean prereleases = settings_.getBooleanValue(org.dbgl.util.EditionUpdateChecker.SECTION, org.dbgl.util.EditionUpdateChecker.PRERELEASES);
		shell_.setCursor(display_.getSystemCursor(SWT.CURSOR_WAIT));
		org.dbgl.util.EditionUpdateChecker.Result result;
		try {
			result = org.dbgl.util.EditionUpdateChecker.check(prereleases);
		} finally {
			shell_.setCursor(null);
		}
		if (result.error() != null)
			Mess_.on(shell_).key("dialog.main.editionupdate.error", new Object[] {result.error()}).warning();
		else if (result.newer() == null)
			Mess_.on(shell_).key("dialog.main.editionupdate.none", new Object[] {Constants.EDITION_NAME, org.dbgl.util.EditionUpdateChecker.readable(Constants.PROGRAM_VERSION)}).display();
		else
			offerEditionRelease(result.newer());
	}

	/**
	 * First start after an update installed with one click: keep its installation archive (in the backup folder, to install it
	 * again if GitHub can no longer be reached) or delete it; the other files of the update are deleted either way (alpha 47).
	 */
	private void cleanUpAfterEditionUpdate() {
		File archive = org.dbgl.util.EditionUpdater.installedArchive();
		if ((archive == null) || shell_.isDisposed())
			return;
		org.eclipse.swt.widgets.MessageBox box = new org.eclipse.swt.widgets.MessageBox(shell_, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
		box.setText(text_.get("dialog.main.menu.editionupdate").replace("&", "").replace("...", ""));
		box.setMessage(text_.get("dialog.main.editionupdate.keeparchive", new Object[] {Constants.EDITION_NAME,
			org.dbgl.util.EditionUpdateChecker.readable(Constants.PROGRAM_VERSION), archive.getName(), (archive.length() + 512 * 1024) / (1024 * 1024),
			org.dbgl.service.BackupService.defaultAutoBackupDir().getPath()}));
		box.setButtonLabels(java.util.Map.of(SWT.YES, text_.get("dialog.main.editionupdate.keep"), SWT.NO, text_.get("dialog.main.editionupdate.delete")));
		boolean keep = box.open() == SWT.YES;
		try {
			org.dbgl.util.EditionUpdater.cleanUp(archive, keep);
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	private void offerEditionRelease(org.dbgl.util.EditionUpdateChecker.Release r) {
		String pre = r.prerelease() ? text_.get("dialog.main.editionupdate.prerelease"): "";
		// with one click: download, check, back up, install, start again (when the release has the files for it)
		if (r.installable() && org.dbgl.util.EditionUpdater.canUpdate()) {
			org.eclipse.swt.widgets.MessageBox box = new org.eclipse.swt.widgets.MessageBox(shell_, SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
			box.setText(text_.get("dialog.main.menu.editionupdate").replace("&", "").replace("...", ""));
			box.setMessage(text_.get("dialog.main.editionupdate.oneclick2",
				new Object[] {Constants.EDITION_NAME, r.version(), org.dbgl.util.EditionUpdateChecker.readable(Constants.PROGRAM_VERSION), pre}));
			box.setButtonLabels(java.util.Map.of(SWT.YES, text_.get("dialog.main.editionupdate.install"), SWT.NO, text_.get("dialog.main.editionupdate.manual"), SWT.CANCEL,
				text_.get("button.cancel")));
			int answer = box.open();
			if (answer == SWT.YES)
				installEditionUpdate(r);
			else if (answer == SWT.NO)
				downloadEditionUpdateManually(r);
			return;
		}
		if (Mess_.on(shell_).key("dialog.main.confirm.editionupdate",
			new Object[] {Constants.EDITION_NAME, r.version(), org.dbgl.util.EditionUpdateChecker.readable(Constants.PROGRAM_VERSION), pre}).confirm())
			downloadEditionUpdateManually(r);
	}

	/** the user downloads and installs the update: first, if wanted, everything backed up (see installEditionUpdate), then the download page */
	private void downloadEditionUpdateManually(org.dbgl.util.EditionUpdateChecker.Release r) {
		File programDir = org.dbgl.util.EditionUpdater.runningJar() != null ? org.dbgl.util.EditionUpdater.runningJar().getParentFile(): null;
		if (org.dbgl.gui.controls.UpdateBackupQuestion.ask(shell_, new ArrayList<>(dbversionsList_), programDir, Constants.PROGRAM_VERSION,
			true) == org.dbgl.gui.controls.UpdateBackupQuestion.Answer.CANCEL)
			return;
		SystemUtils.openForBrowsing(r.url(), shell_);
	}

	/** download and check the update, make the backups, then end DBGL: a helper copies the new files and starts DBGL again */
	private void installEditionUpdate(org.dbgl.util.EditionUpdateChecker.Release r) {
		// first, if the user wants it: everything backed up, also the DOSBox versions, the program folder and the games
		File programDir = org.dbgl.util.EditionUpdater.runningJar() != null ? org.dbgl.util.EditionUpdater.runningJar().getParentFile(): null;
		org.dbgl.gui.controls.UpdateBackupQuestion.Answer backup = org.dbgl.gui.controls.UpdateBackupQuestion.ask(shell_, new ArrayList<>(dbversionsList_), programDir,
			Constants.PROGRAM_VERSION, false);
		if (backup == org.dbgl.gui.controls.UpdateBackupQuestion.Answer.CANCEL)
			return;
		File fullBackup = backup == org.dbgl.gui.controls.UpdateBackupQuestion.Answer.BACKED_UP ? org.dbgl.gui.controls.UpdateBackupQuestion.lastFile(): null;
		java.util.concurrent.atomic.AtomicInteger progress = new java.util.concurrent.atomic.AtomicInteger();
		org.dbgl.util.EditionUpdater.Prepared prepared;
		try {
			prepared = org.dbgl.gui.controls.BusyTask.run(shell_, text_.get("dialog.main.editionupdate.downloading", new Object[] {r.version()}), progress, 1000,
				() -> org.dbgl.util.EditionUpdater.prepare(r.updateZip(), r.sums(), Constants.PROGRAM_VERSION, r.version(), progress));
		} catch (org.dbgl.gui.controls.BusyTask.Cancelled e) {
			return;
		} catch (Exception e) {
			Mess_.on(shell_).key("dialog.main.editionupdate.failed", new Object[] {String.valueOf(e.getMessage() == null ? e: e.getMessage())}).exception(e).warning();
			return;
		}
		String backups = prepared.programBackup().getPath() + (fullBackup != null ? "\n" + fullBackup.getPath(): "");
		if (!Mess_.on(shell_).key("dialog.main.editionupdate.ready", new Object[] {r.version(), prepared.dataBackup().getPath(), backups}).confirm())
			return;
		if (!org.dbgl.util.EditionUpdater.launchInstaller(prepared)) {
			Mess_.on(shell_).key("dialog.main.editionupdate.failed", new Object[] {"installer"}).warning();
			return;
		}
		shell_.close();
	}

	/** @return true if the configuration file of the DOSBox version says it was written by this very version */
	private static boolean confIsFrom(DosboxVersion v) {
		String written = DosboxVersionDetector.versionOfConf(v.getConfigurationCanonicalFile());
		boolean same = (written != null) && (DosboxUpdateChecker.compareVersions(releaseOf(written), releaseOf(v.getVersion())) == 0);
		System.out.println("dosbox.conf of " + v.getTitle() + " written by " + written + ", executable " + v.getVersion() + (same ? ": kept": ": may be written again"));
		return same;
	}

	/** the release number only, e.g. "0.83.0-abc123" or "0.83.0 (git)" -> "0.83.0", "2026.10.01 SDL2" -> "2026.10.01" */
	private static String releaseOf(String version) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)*").matcher(version);
		return m.find() ? m.group(): version;
	}

	private void checkForDosboxUpdatesInBackground() {
		String today = java.time.LocalDate.now().toString();
		if (today.equals(settings_.getValue("gui", "updatechecked")))
			return;
		settings_.setValue("gui", "updatechecked", today);
		List<DosboxVersion> versions = new ArrayList<>(dbversionsList_);
		Thread checker = new Thread(() -> {
			// what is really installed: the version of the executable, which may be newer than the entry (e.g. Staging 0.83 entered
			// as 0.82.2 under DBGL 0.100 beta 17, corrected at this start in parallel)
			java.util.Map<String, String> installed = new java.util.HashMap<>();
			for (DosboxVersion v: versions) {
				DosboxVersionDetector.Result r = DosboxVersionDetector.detectCached(v.getCanonicalExecutable(), v.getCwd());
				String family = r != null ? r.family_: v.getFamily(), version = r != null ? r.version_: v.getVersion();
				installed.merge(family, version, (a, b) -> DosboxUpdateChecker.compareVersions(a, b) >= 0 ? a: b);
			}
			if (!installed.containsKey(SettingsService.STAGING_DOSBOX_FAM.title_) && !installed.containsKey(SettingsService.DOSBOX_X_DOSBOX_FAM.title_))
				return; // only for the DOSBox families in use
			List<DosboxUpdateChecker.Release> newer = DosboxUpdateChecker.latestReleases().stream()
					.filter(r -> installed.containsKey(r.family()) && (DosboxUpdateChecker.compareVersions(r.version(), installed.get(r.family())) > 0)).toList();
			if (newer.isEmpty() || display_.isDisposed())
				return;
			display_.asyncExec(() -> {
				if (shell_.isDisposed())
					return;
				String shown = StringUtils.defaultString(settings_.getValue("gui", "updatenoticeshown"));
				for (DosboxUpdateChecker.Release r: newer) {
					String id = r.family() + ' ' + r.version();
					if (shown.contains(id))
						continue;
					shown = (shown + ';' + id).replaceFirst("^;", "");
					settings_.setValue("gui", "updatenoticeshown", shown);
					offerDosboxRelease(r, text_.get("dialog.main.confirm.dosboxupdate", new Object[] {r.family(), r.version(), installed.get(r.family())}));
				}
			});
		}, "DOSBox update check");
		checker.setDaemon(true);
		checker.start();
	}

	/** menu DOSBox -> download: the latest releases of DOSBox Staging and DOSBox-X */
	private void doDownloadDosbox() {
		shell_.setCursor(display_.getSystemCursor(SWT.CURSOR_WAIT));
		Thread fetcher = new Thread(() -> {
			List<DosboxUpdateChecker.Release> releases = DosboxUpdateChecker.latestReleases();
			display_.asyncExec(() -> {
				if (shell_.isDisposed())
					return;
				shell_.setCursor(null);
				if (releases.isEmpty()) {
					Mess_.on(shell_).key("dialog.main.notice.noreleases").warning();
					return;
				}
				for (DosboxUpdateChecker.Release r: releases) {
					boolean present = dbversionsList_.stream().anyMatch(v -> v.getFamily().equals(r.family()) && (DosboxUpdateChecker.compareVersions(v.getVersion(), r.version()) >= 0));
					String question = text_.get("dialog.main.confirm.downloaddosbox", new Object[] {r.family(), r.version()}) + (present ? "\n" + text_.get("dialog.main.notice.alreadyinstalled"): "");
					if (offerDosboxRelease(r, question))
						break;
				}
			});
		}, "DOSBox releases");
		fetcher.setDaemon(true);
		fetcher.start();
	}

	/**
	 * Ask what to do with a release: download and set up (when there is a build for this computer), open the download page, or
	 * nothing.
	 *
	 * @return true if the user chose to download or to open the page
	 */
	private boolean offerDosboxRelease(DosboxUpdateChecker.Release r, String question) {
		String[] asset = org.dbgl.util.DosboxInstaller.chooseAsset(r.assets(), SystemUtils.IS_WINDOWS, SystemUtils.IS_LINUX, System.getProperty("os.arch").toLowerCase().contains("aarch64") || System.getProperty("os.arch").toLowerCase().contains("arm"));
		MessageBox box = new MessageBox(shell_, SWT.ICON_QUESTION | (asset != null ? SWT.YES | SWT.NO | SWT.CANCEL: SWT.YES | SWT.NO));
		box.setText(text_.get("dialog.main.title.dosboxrelease", new Object[] {r.family(), r.version()}));
		box.setMessage(question + "\n\n" + (asset != null ? text_.get("dialog.main.confirm.dosboxrelease.install", new Object[] {asset[0]}): text_.get("dialog.main.confirm.dosboxrelease.page")));
		int answer = box.open();
		if ((asset != null) && (answer == SWT.YES)) {
			// several builds fit (e.g. DOSBox-X: Visual Studio or MinGW, SDL1 or SDL2): let the user pick, the recommended one first
			boolean arm = System.getProperty("os.arch").toLowerCase().contains("aarch64") || System.getProperty("os.arch").toLowerCase().contains("arm");
			List<String[]> candidates = org.dbgl.util.DosboxInstaller.candidates(r.assets(), SystemUtils.IS_WINDOWS, SystemUtils.IS_LINUX, arm);
			if (candidates.size() > 1) {
				List<String> names = new ArrayList<>();
				for (int i = 0; i < candidates.size(); i++)
					names.add(candidates.get(i)[0] + (i == 0 ? "   (" + text_.get("dialog.main.notice.recommended") + ")": ""));
				Integer pick = new ChoiceDialog(shell_, text_.get("dialog.main.title.dosboxrelease", new Object[] {r.family(), r.version()}),
					text_.get("dialog.main.notice.choosebuild"), names).open();
				if (pick == null)
					return false;
				asset = candidates.get(pick);
			}
			if (((asset.length < 3) || StringUtils.isBlank(asset[2])) && !Mess_.on(shell_).key("dialog.main.confirm.nochecksum", new Object[] {asset[0]}).confirm())
				return false;
			installDosboxRelease(r, asset);
			return true;
		}
		if (((asset != null) && (answer == SWT.NO)) || ((asset == null) && (answer == SWT.YES))) {
			SystemUtils.openForBrowsing(r.url(), getParent());
			return true;
		}
		return false;
	}

	/** download and unpack a release into the DOSBox folder, then add it with the DOSBox version dialog */
	private void installDosboxRelease(DosboxUpdateChecker.Release r, String[] asset) {
		// where to: a sub folder named after the release in the folder the user picks (DBGL's DOSBox folder by default)
		String folderName = org.dbgl.util.DosboxInstaller.folderName(r.family(), r.version());
		DirectoryDialog dirDialog = new DirectoryDialog(shell_);
		dirDialog.setText(text_.get("dialog.main.title.dosboxrelease", new Object[] {r.family(), r.version()}));
		dirDialog.setMessage(text_.get("dialog.main.notice.choosedosboxdir", new Object[] {r.family() + " " + r.version(), folderName}));
		dirDialog.setFilterPath(FileLocationService.getInstance().getDosboxDir().getAbsolutePath());
		String chosen = dirDialog.open();
		if (chosen == null)
			return;
		File targetDir = new File(chosen).getName().equalsIgnoreCase(folderName) ? new File(chosen): new File(chosen, folderName);
		boolean overwrite = false;
		if (org.dbgl.util.DosboxInstaller.hasContent(targetDir)) {
			MessageBox ask = new MessageBox(shell_, SWT.ICON_WARNING | SWT.YES | SWT.NO | SWT.CANCEL);
			ask.setText(text_.get("dialog.main.title.dosboxrelease", new Object[] {r.family(), r.version()}));
			ask.setMessage(text_.get("dialog.main.confirm.dosboxdirexists", new Object[] {targetDir.getPath()}));
			int answer = ask.open();
			if (answer == SWT.CANCEL)
				return;
			overwrite = answer == SWT.YES;
		}
		final boolean replaceFiles = overwrite;
		Shell progressShell = new Shell(shell_, SWT.APPLICATION_MODAL | SWT.TITLE | SWT.BORDER);
		progressShell.setText(text_.get("dialog.main.title.dosboxrelease", new Object[] {r.family(), r.version()}));
		progressShell.setLayout(new GridLayout());
		Label label = new Label(progressShell, SWT.NONE);
		label.setText(text_.get("dialog.main.notice.downloading", new Object[] {asset[0]}));
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		ProgressBar bar = new ProgressBar(progressShell, SWT.HORIZONTAL);
		bar.setMaximum(100);
		bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
		Button cancelButton = new Button(progressShell, SWT.PUSH);
		cancelButton.setText(text_.get("button.cancel"));
		cancelButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
		cancelButton.addListener(SWT.Selection, e -> {
			cancelled.set(true);
			cancelButton.setEnabled(false);
		});
		progressShell.addListener(SWT.Close, e -> cancelled.set(true));
		progressShell.setSize(480, 150);
		progressShell.setLocation(shell_.getBounds().x + (shell_.getBounds().width - 480) / 2, shell_.getBounds().y + (shell_.getBounds().height - 150) / 2);
		progressShell.open();
		Thread worker = new Thread(() -> {
			File archive = null;
			Exception failure = null;
			File exe = null;
			try {
				archive = File.createTempFile("dbgl-dosbox-", "-" + asset[0]);
				org.dbgl.util.DosboxInstaller.download(asset[1], archive, percent -> display_.asyncExec(() -> {
					if (!bar.isDisposed())
						bar.setSelection(Math.max(0, percent));
				}), cancelled::get);
				// the checksum GitHub publishes: an incomplete or changed file is not unpacked
				if ((asset.length > 2) && org.dbgl.util.DosboxInstaller.verify(archive, asset[2]))
					System.out.println("SHA-256 verified: " + asset[0]);
				display_.asyncExec(() -> {
					if (!label.isDisposed())
						label.setText(text_.get("dialog.main.notice.unpacking", new Object[] {targetDir.getPath()}));
				});
				org.dbgl.util.DosboxInstaller.unpack(archive, targetDir, replaceFiles);
				exe = org.dbgl.util.DosboxInstaller.findExecutable(targetDir, org.dbgl.util.DosboxInstaller.executableNames(r.family()));
				if (exe == null)
					exe = org.dbgl.util.DosboxInstaller.findExecutable(targetDir);
			} catch (Exception e) {
				failure = e;
			} finally {
				if (archive != null)
					archive.delete();
			}
			Exception error = failure;
			File executable = exe;
			display_.asyncExec(() -> {
				if (!progressShell.isDisposed())
					progressShell.dispose();
				if (shell_.isDisposed())
					return;
				if (error instanceof org.dbgl.util.DosboxInstaller.CancelledException) {
					Mess_.on(shell_).key("dialog.main.notice.downloadcancelled").display();
					return;
				}
				if (error != null) {
					Mess_.on(shell_).key("dialog.main.error.dosboxinstall", new Object[] {r.family() + " " + r.version(), error.toString()}).exception(error).warning();
					return;
				}
				if (executable == null) {
					Mess_.on(shell_).key("dialog.main.error.dosboxinstall", new Object[] {r.family() + " " + r.version(), targetDir.getPath()}).warning();
					SystemUtils.openForBrowsing(targetDir.getPath(), getParent());
					return;
				}
				// the executable with its full path unless it has the default name (e.g. dosbox-x.exe), and the configuration file
				// name DOSBox itself uses (dosbox-x.conf for DOSBox-X)
				String exePath = executable.getName().equalsIgnoreCase(FileLocationService.DOSBOX_EXE_STRING) ? StringUtils.EMPTY: executable.getAbsolutePath();
				File confFile = new File(executable.getParentFile(), org.dbgl.util.DosboxInstaller.configurationName(r.family()));
				DosboxVersion fresh = DosboxVersionFactory.create(r.family() + " " + r.version(), r.family(), r.version(), BaseRepository.findDefault(dbversionsList_) == null, true, false, null,
					executable.getParentFile().getAbsolutePath(), exePath, StringUtils.EMPTY, confFile.getAbsolutePath());
				DosboxVersion added = new EditDosboxVersionDialog(shell_, BaseRepository.findDefault(dbversionsList_) == null, fresh).open();
				if (added != null) {
					updateDosboxVersionList(added);
					if (!profilesList_.isEmpty() && Mess_.on(shell_).key("dialog.main.confirm.switchtonewdosbox", new Object[] {added.getTitle()}).confirm()
							&& Boolean.TRUE.equals(new SwitchDosboxVersionDialog(shell_).open())) {
						updateProfilesList(getSelectedProfileIds());
						displayProfileInformation(false);
					}
				}
			});
		}, "DOSBox download");
		worker.setDaemon(true);
		worker.start();
	}

	private final SelectionAdapter backupAdapter = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent event) {
			FileDialog dialog = new FileDialog(shell_, SWT.SAVE);
			dialog.setFilterExtensions(new String[] {"*.zip", "*.*"});
			dialog.setFileName("dbgl-backup-" + java.time.LocalDate.now() + ".zip");
			dialog.setOverwrite(true);
			String file = dialog.open();
			if (file == null)
				return;
			shell_.setCursor(display_.getSystemCursor(SWT.CURSOR_WAIT));
			try {
				int count = BackupService.backup(new File(file));
				shell_.setCursor(null);
				Mess_.on(shell_).key("dialog.main.notice.backupdone", new Object[] {file, count}).display();
			} catch (IOException | SQLException e) {
				shell_.setCursor(null);
				Mess_.on(shell_).exception(e).warning();
			}
		}
	};

	private final SelectionAdapter restoreAdapter = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent event) {
			FileDialog dialog = new FileDialog(shell_, SWT.OPEN);
			dialog.setFilterExtensions(new String[] {"*.zip", "*.*"});
			String file = dialog.open();
			if (file == null)
				return;
			if (!BackupService.isBackup(new File(file))) {
				Mess_.on(shell_).key("dialog.main.error.nobackup", new Object[] {file}).warning();
				return;
			}
			if (!Mess_.on(shell_).key("dialog.main.confirm.restore", new Object[] {file}).confirm())
				return;
			try {
				BackupService.restore(new File(file));
				restored_ = true;
				Mess_.on(shell_).key("dialog.main.notice.restoredone").display();
				shell_.close();
			} catch (IOException | SQLException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		}
	};

	/** after restoring a backup, the database is shut down and the settings were replaced: end without saving anything */
	private boolean restored_;

	/**
	 * Compare the family and version of every DOSBox entry with what the executable reports. A wrong version makes DBGL use the
	 * wrong setting names and leads to empty or invalid entries in the profiles. Runs in the background, as each check starts
	 * the executable once (the results are cached per executable file).
	 */
	private void checkDosboxVersionsInBackground() {
		List<DosboxVersion> versions = new ArrayList<>(dbversionsList_);
		Thread checker = new Thread(() -> {
			List<DosboxVersion> mismatching = new ArrayList<>();
			List<DosboxVersionDetector.Result> detected = new ArrayList<>();
			for (DosboxVersion v: versions) {
				DosboxVersionDetector.Result r = DosboxVersionDetector.detectCached(v.getCanonicalExecutable(), v.getCwd());
				if ((r != null) && !(r.family_.equalsIgnoreCase(v.getFamily()) && r.version_.equals(v.getVersion()))) {
					mismatching.add(v);
					detected.add(r);
				}
			}
			if (mismatching.isEmpty() || display_.isDisposed())
				return;
			display_.asyncExec(() -> {
				if (shell_.isDisposed())
					return;
				StringBuilder list = new StringBuilder();
				for (int i = 0; i < mismatching.size(); i++) {
					// short, so that a line fits: "title: 0.82.2 -> 0.83.0", the family only where it changes
					DosboxVersion v = mismatching.get(i);
					DosboxVersionDetector.Result r = detected.get(i);
					boolean sameFamily = r.family_.equalsIgnoreCase(v.getFamily());
					list.append(v.getTitle()).append(": ").append(sameFamily ? "": v.getFamily() + ' ').append(v.getVersion()).append(" -> ").append(sameFamily ? "": r.family_ + ' ').append(r.version_).append('\n');
				}
				if (!Mess_.on(shell_).key("dialog.main.confirm.versionmismatch", new Object[] {list.toString().trim()}).confirmUnwrapped())
					return;
				try {
					for (int i = 0; i < mismatching.size(); i++) {
						DosboxVersion v = mismatching.get(i);
						v.setFamily(detected.get(i).family_);
						v.setVersion(detected.get(i).version_);
						new DosboxVersionRepository().update(v);
					}
				} catch (SQLException e) {
					Mess_.on(shell_).exception(e).warning();
				}
				// the configuration files were written by the previous version, and may lack settings of the new one; a file the new
				// version wrote already (e.g. DBGL 0.100 beta 17 ran a newer DOSBox under the last version it knew) is kept as it is
				List<DosboxVersion> withConf = mismatching.stream().filter(v -> FilesUtils.isReadableFile(v.getConfigurationCanonicalFile()) && FilesUtils.isReadableFile(v.getCanonicalExecutable()))
						.filter(v -> !confIsFrom(v)).toList();
				if (!withConf.isEmpty() && Mess_.on(shell_).key("dialog.main.confirm.recreateconfs2",
					new Object[] {withConf.stream().map(v -> v.getTitle() + ": " + v.getConfigurationCanonicalFile() + "  [" + StringUtils.defaultIfEmpty(DosboxVersionDetector.versionOfConf(v.getConfigurationCanonicalFile()), "?")
							+ " -> " + v.getVersion() + "]").collect(Collectors.joining("\n"))}).confirm()) {
					shell_.setCursor(display_.getSystemCursor(SWT.CURSOR_WAIT));
					try {
						for (DosboxVersion v: withConf) {
							ExecuteUtils.recreateDosboxConf(v);
							v.resetAndLoadConfiguration();
						}
					} catch (IOException e) {
						Mess_.on(shell_).exception(e).warning();
					} finally {
						shell_.setCursor(null);
					}
				}
				updateDosboxVersionList(mismatching.get(0));
				// the profiles with the corrected versions, and their warning signs checked again for what the new version can
				org.dbgl.util.ProfileWarnings.forgetAll();
				updateProfilesList(getSelectedProfileIds());
				displayProfileInformation(false);
				checkAllProfilesInBackground();
				Mess_.on(shell_).key("dialog.main.notice.versioncorrected", new Object[] {list.toString().trim()}).display();
			});
		}, "DOSBox version check");
		checker.setDaemon(true);
		checker.start();
	}

	@Override
	protected void onClose() {
		super.onClose();

		ImageService.clearCache();
		display_.dispose();

		if (mess_ != null)
			mess_.close();

		if (!restored_) {
			try {
				settings_.save();
			} catch (IOException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		}

		if (Boolean.TRUE.equals(result_))
			Mess_.on(null).key("dialog.main.notice.restart").display();
	}

	private void initColumnIds() {
		columnNames_ = new String[Constants.RO_COLUMN_NAMES + Constants.EDIT_COLUMN_NAMES + 8 + 1];

		int c = 0;
		columnNames_[c++] = text_.get("dialog.main.profiles.column.title");
		columnNames_[c++] = text_.get("dialog.main.profiles.column.setup");
		columnNames_[c++] = text_.get("dialog.main.profiles.column.developer");
		columnNames_[c++] = text_.get("dialog.main.profiles.column.publisher");
		columnNames_[c++] = text_.get("dialog.main.profiles.column.genre");
		columnNames_[c++] = text_.get("dialog.main.profiles.column.year");
		columnNames_[c++] = text_.get("dialog.main.profiles.column.status");
		columnNames_[c++] = text_.get("dialog.main.profiles.column.favorite");
		columnNames_[c++] = text_.get("dialog.main.profiles.column.id");
		columnNames_[c++] = text_.get("dialog.main.profiles.column.dosboxversionid");
		for (int i = 0; i < Constants.EDIT_COLUMN_NAMES_1; i++)
			columnNames_[c++] = settings_.getValue("gui", "custom" + (i + 1));
		columnNames_[c++] = text_.get("dialog.main.profiles.column.screenshot");
		columnNames_[c++] = text_.get("dialog.main.profiles.column.dosboxversiontitle");
		columnNames_[c++] = text_.get("dialog.main.generic.column.created");
		columnNames_[c++] = text_.get("dialog.main.generic.column.lastmodify");
		columnNames_[c++] = text_.get("dialog.main.generic.column.lastrun");
		columnNames_[c++] = text_.get("dialog.main.generic.column.lastsetup");
		columnNames_[c++] = text_.get("dialog.main.generic.column.runs");
		columnNames_[c++] = text_.get("dialog.main.generic.column.setups");
		for (int i = 0; i < Constants.EDIT_COLUMN_NAMES_2; i++)
			columnNames_[c++] = settings_.getValue("gui", "custom" + (i + 1 + Constants.EDIT_COLUMN_NAMES_1));
		columnNames_[c++] = text_.get("dialog.main.generic.column.playtime");

		columnIds_ = IntStream.range(0, c).filter(x -> settings_.getBooleanValue("gui", "column" + (x + 1) + "visible")).toArray();
	}

	private void createAppMenuBar() {
		Menu appMenuBar = display_.getMenuBar();
		if (appMenuBar == null) {
			appMenuBar = new Menu(shell_, SWT.BAR);
			shell_.setMenuBar(appMenuBar);
		}

		SelectionAdapter settingsAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doOpenSettingsDialog();
			}
		};
		SelectionAdapter logAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				new LogDialog(shell_).open();
			}
		};
		SelectionAdapter cleanupAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				try {
					if (Mess_.on(shell_).key("dialog.main.confirm.databasecleanup").confirm()) {
						int itemsRemoved = new TitledEntityRepository().cleanup();
						Mess_.on(shell_).key("dialog.main.notice.databasecleanupok", new Object[] {itemsRemoved}).display();
					}
				} catch (SQLException e) {
					Mess_.on(shell_).exception(e).warning();
				}
			}
		};
		SelectionAdapter openAboutAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				new AboutDialog(shell_).open();
			}
		};
		SelectionAdapter quitAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				shell_.close();
			}
		};

		Menu systemMenu = display_.getSystemMenu();
		if (systemMenu != null) {
			addMenuItemListener(systemMenu, SWT.ID_PREFERENCES, settingsAdapter);
			addMenuItemListener(systemMenu, SWT.ID_ABOUT, openAboutAdapter);

			int prefsIndex = systemMenu.indexOf(getMenuItemById(systemMenu, SWT.ID_PREFERENCES));
			MenuItem_.on(systemMenu).pos(prefsIndex + 1).key("dialog.main.menu.log").listen(logAdapter).build();
			MenuItem_.on(systemMenu).pos(prefsIndex + 2).key("dialog.main.menu.databasecleanup").listen(cleanupAdapter).build();
			MenuItem_.on(systemMenu).pos(prefsIndex + 3).key("dialog.main.menu.backup").listen(backupAdapter).build();
			MenuItem_.on(systemMenu).pos(prefsIndex + 4).key("dialog.main.menu.restore").listen(restoreAdapter).build();
		} else {
			Menu fileMenu = createMenu(appMenuBar, "dialog.main.menu.file");
			MenuItem_.on(fileMenu).key("dialog.main.menu.adjustsettings").image(ImageService.IMG_TB_SETTINGS).listen(settingsAdapter).build();
			MenuItem_.on(fileMenu).key("dialog.main.menu.log").image(ImageService.IMG_LOG).listen(logAdapter).build();
			MenuItem_.on(fileMenu).key("dialog.main.menu.databasecleanup").image(ImageService.IMG_CLEAN).listen(cleanupAdapter).build();
			MenuItem_.on(fileMenu).build();
			MenuItem_.on(fileMenu).key("dialog.main.menu.backup").image(ImageService.IMG_TABLEEXPORT).listen(backupAdapter).build();
			MenuItem_.on(fileMenu).key("dialog.main.menu.restore").image(ImageService.IMG_IMPORT).listen(restoreAdapter).build();
			MenuItem_.on(fileMenu).build();
			MenuItem_.on(fileMenu).key("dialog.main.menu.exit").image(ImageService.IMG_EXIT).listen(quitAdapter).build();
		}

		Menu profilesMenu = createMenu(appMenuBar, "dialog.main.menu.profiles");
		MenuItem_.on(profilesMenu).key("dialog.main.menu.import").image(ImageService.IMG_IMPORT).accel(SWT.MOD1 | SWT.MOD3 | 'I').listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doImportProfiles(true);
			}
		}).build();
		MenuItem_.on(profilesMenu).key("dialog.main.menu.importprofile").image(ImageService.IMG_IMPORT).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doImportProfiles(false);
			}
		}).build();
		MenuItem_.on(profilesMenu).key("dialog.main.menu.importscanfolder").image(ImageService.IMG_IMPORT).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				importScanFolder();
			}
		}).build();
		MenuItem_.on(profilesMenu).key("dialog.main.menu.importstoregames").image(ImageService.IMG_IMPORT).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doImportStoreGames();
			}
		}).build();
		MenuItem_.on(profilesMenu).key("dialog.main.menu.importdfendprofiles").image(ImageService.IMG_DFEND).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				DosboxVersion defaultDbversion = requireDefaultDBVersion();
				if (defaultDbversion == null)
					return;

				if (settings_.getIntValue("profiledefaults", "confpath") == 1)
					Mess_.on(shell_).key("dialog.main.notice.dfendimportconflocation", SettingsDialog.getConfLocations().get(0)).display();

				if (new DFendImportDialog(shell_, defaultDbversion).open() != null) {
					updateProfilesList(getSelectedProfileIds());
					displayProfileInformation(false);
				}
			}
		}).build();
		MenuItem_.on(profilesMenu).build();
		MenuItem_.on(profilesMenu).key("dialog.main.menu.export").image(ImageService.IMG_TABLEEXPORT).accel(SWT.MOD1 | SWT.MOD3 | 'E').listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (profileTable_.getSelectionIndex() != -1) {
					List<Profile> loadedProfiles = new ProfileLoader(shell_, getSelectedProfiles()).open();
					if (loadedProfiles != null)
						new ExportDialog(shell_, loadedProfiles).open();
				}
			}
		}).build();
		MenuItem_.on(profilesMenu).key("dialog.main.menu.exportprofileslist").image(ImageService.IMG_TABLEEXPORT).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				new ExportListDialog(shell_, profilesList_).open();
			}
		}).build();
		MenuItem_.on(profilesMenu).build();
		MenuItem_.on(profilesMenu).key("dialog.main.menu.migrateprofiles").image(ImageService.IMG_MIGRATE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doMigrate();
			}
		}).build();
		// the tools for all profiles, the statistics at the end
		MenuItem_.on(profilesMenu).build();
		MenuItem_.on(profilesMenu).key("dialog.main.menu.duplicates").image(ImageService.IMG_DUPLICATE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doFindDuplicates();
			}
		}).build();
		MenuItem_.on(profilesMenu).key("dialog.main.menu.checkprofiles").image(ImageService.IMG_CHECKPROFILES).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doCheckProfiles();
			}
		}).build();
		MenuItem_.on(profilesMenu).key("dialog.main.menu.coverbatch").image(ImageService.IMG_GLOBE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doCoverBatch();
			}
		}).build();
		MenuItem undoBulkItem = MenuItem_.on(profilesMenu).key("dialog.main.menu.undobulk").image(ImageService.IMG_UNDO).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doUndoBulkChange();
			}
		}).build();
		profilesMenu.addMenuListener(new MenuAdapter() {
			@Override
			public void menuShown(MenuEvent event) {
				undoBulkItem.setEnabled(org.dbgl.util.BulkUndo.last() != null);
			}
		});
		MenuItem_.on(profilesMenu).build();
		MenuItem_.on(profilesMenu).key("dialog.main.menu.statistics").image(ImageService.IMG_STATISTICS).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doShowStatistics();
			}
		}).build();

		Menu dbversionsMenu = createMenu(appMenuBar, "dialog.main.menu.dosboxversions");
		MenuItem_.on(dbversionsMenu).key("dialog.main.menu.locatedosbox").image(ImageService.IMG_ZOOM).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doLocateDosbox(true);
			}
		}).build();
		MenuItem_.on(dbversionsMenu).key("dialog.main.menu.downloaddosbox").image(ImageService.IMG_IMPORT).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doDownloadDosbox();
			}
		}).build();
		MenuItem_.on(dbversionsMenu).key("dialog.main.menu.switchdosboxversion2").image(ImageService.IMG_MIGRATE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (Boolean.TRUE.equals(new SwitchDosboxVersionDialog(shell_).open())) {
					updateProfilesList(getSelectedProfileIds());
					displayProfileInformation(false);
				}
			}
		}).build();

		Menu templatesMenu = createMenu(appMenuBar, "dialog.main.menu.templates");
		if (SystemUtils.IS_WINDOWS && SystemUtils.IS_LINUX) // comment this line out to be able to export templates
			MenuItem_.on(templatesMenu).key("dialog.main.menu.exporttemplates").image(ImageService.IMG_TABLEEXPORT).listen(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					try {
						String warnings = ImportExportTemplatesService.export(templatesList_);
						if (StringUtils.isNotEmpty(warnings))
							Mess_.on(shell_).txt(warnings).warning();
					} catch (Exception e) {
						Mess_.on(shell_).txt(e.toString()).exception(e).fatal();
					}
				}
			}).build();
		MenuItem_.on(templatesMenu).key("dialog.main.menu.importdefaulttemplates").image(ImageService.IMG_IMPORT).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doImportDefaultTemplates(true);
			}
		}).build();

		Menu filterMenu = createMenu(appMenuBar, "dialog.main.menu.filter");
		MenuItem_.on(filterMenu).key("dialog.main.menu.addfilter").image(ImageService.IMG_FILTER).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doAddFilter();
			}
		}).build();
		MenuItem_.on(filterMenu).key("dialog.main.menu.editfilter").image(ImageService.IMG_EDITFILTER).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doEditFilter();
			}
		}).build();

		createViewMenu(appMenuBar);

		// the update check first, then what is new since DBGL 0.100 beta 17 and the full changelog, above 'About' (on macOS
		// 'About' is in the application menu)
		Menu helpMenu = createMenu(appMenuBar, "dialog.main.menu.help");
		MenuItem_.on(helpMenu).key("dialog.main.menu.editionupdate").image(ImageService.IMG_REFRESH).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doCheckForEditionUpdate();
			}
		}).build();
		MenuItem_.on(helpMenu).build();
		// the thread of the Extended Edition in the Vogons forum: news, feedback, bug reports
		MenuItem_.on(helpMenu).key("dialog.main.menu.vogons").image(ImageService.IMG_GLOBE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				SystemUtils.openForBrowsing("https://www.vogons.org/viewtopic.php?t=113077", shell_);
			}
		}).build();
		MenuItem_.on(helpMenu).key("dialog.main.menu.newfeatures").image(ImageService.IMG_WHATSNEW).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				showDocument("whatsnew", "dialog.main.menu.newfeatures");
			}
		}).build();
		MenuItem_.on(helpMenu).key("dialog.main.menu.changelog").image(ImageService.IMG_LOG).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				showDocument("changelog", "dialog.main.menu.changelog");
			}
		}).build();
		if (systemMenu == null) {
			MenuItem_.on(helpMenu).build();
			MenuItem_.on(helpMenu).key("dialog.main.menu.about2").image(ImageService.IMG_ABOUT).listen(openAboutAdapter).build();
		}
	}

	/** a text shipped inside DBGL (doc/&lt;name&gt;_de.txt or _en.txt): German for the German interface, else English */
	private void showDocument(String name, String titleKey) {
		showDocument(name, titleKey, "changelog".equals(name) ? ImageService.IMG_LOG: ImageService.IMG_WHATSNEW);
	}

	private void showDocument(String name, String titleKey, String img) {
		String lang = "de".equals(settings_.getValue("locale", "language")) ? "de": "en";
		try (java.io.InputStream in = MainWindow.class.getClassLoader().getResourceAsStream("doc/" + name + "_" + lang + ".txt")) {
			if (in == null)
				throw new IOException("doc/" + name + "_" + lang + ".txt");
			String content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\n", org.eclipse.swt.widgets.Text.DELIMITER);
			new DifferencesDialog(shell_, text_.get(titleKey).replace("...", "").replace("\u2026", "").replace("&", ""), content).withImage(img).open();
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	private static MenuItem getMenuItemById(Menu menu, int id) {
		for (MenuItem item: menu.getItems())
			if (item.getID() == id)
				return item;
		return null;
	}

	private static void addMenuItemListener(Menu menu, int id, SelectionListener listener) {
		MenuItem item = getMenuItemById(menu, id);
		if (item != null)
			item.addSelectionListener(listener);
	}

	private void createProfilesTab(CTabFolder tabFolder) {
		Composite composite = Composite_.on(tabFolder).innerLayout(1).tab("dialog.main.profiles").build();

		Composite toolbarComposite = createRow(composite);
		// as wide as the window: when it is too narrow, the right part moves to a second row instead of being cut off
		toolbarComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		ToolBar toolBar = createToolBar(toolbarComposite);
		createImageToolItem(toolBar, "dialog.main.addprofile", ImageService.IMG_TB_NEW, addProfAdapter);
		createImageToolItem(toolBar, "dialog.main.editprofile", ImageService.IMG_TB_EDIT, editProfAdapter);
		// undo the last change of several profiles at once; its tooltip says which one it is
		ToolItem undoItem = createImageToolItem(toolBar, "dialog.main.undobulk", ImageService.IMG_TB_UNDO, new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (org.dbgl.util.BulkUndo.last() == null)
					Mess_.on(shell_).key("dialog.main.undobulk.none").display();
				else
					doUndoBulkChange();
			}
		});
		// greyed out when there is nothing to undo (also after a change made in another dialog or thread)
		Runnable refreshUndoItem = () -> {
			if (!undoItem.isDisposed())
				undoItem.setEnabled(org.dbgl.util.BulkUndo.last() != null);
		};
		refreshUndoItem.run();
		org.dbgl.util.BulkUndo.onChange(() -> {
			if (!shell_.isDisposed() && !shell_.getDisplay().isDisposed())
				shell_.getDisplay().asyncExec(refreshUndoItem);
		});
		toolBar.addListener(SWT.MouseEnter, e -> {
			org.dbgl.util.BulkUndo.Info last = org.dbgl.util.BulkUndo.last();
			undoItem.setToolTipText(last == null ? text_.get("dialog.main.undobulk.none"): text_.get("dialog.main.undobulk.tooltip", new Object[] {last.label(),
				java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(last.when()), last.profiles()}));
		});
		createImageToolItem(toolBar, "dialog.main.removeprofile", ImageService.IMG_TB_DELETE, removeProfAdapter);
		// groups of buttons, all with the same space between them
		createSeparatorToolItem(toolBar, TOOLBAR_SEPARATOR);
		createImageToolItem(toolBar, "dialog.main.runprofile", ImageService.IMG_TB_RUN, runProfAdapter);
		setupToolItem_ = createImageToolItem(toolBar, "dialog.main.runprofilesetup", ImageService.IMG_TB_SETUP, setupProfAdapter);
		createSeparatorToolItem(toolBar, TOOLBAR_SEPARATOR);
		createImageToolItem(toolBar, "dialog.main.addwizard", ImageService.IMG_TB_ADDGAMEWIZARD, new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (requireDefaultDBVersion() == null)
					return;

				updateWithAddedProfile(new AddGameWizardDialog(shell_).open());
			}
		});
		// quick access to what is otherwise in the menus: filters, game information from the internet, checking, a random game
		createSeparatorToolItem(toolBar, TOOLBAR_SEPARATOR);
		createLabelledToolItem(toolBar, "dialog.main.tb.addfilter", ImageService.IMG_TB_FILTER, "dialog.main.menu.addfilter", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doAddFilter();
			}
		});
		createLabelledToolItem(toolBar, "dialog.main.tb.editfilter", ImageService.IMG_TB_EDITFILTER, "dialog.main.menu.editfilter", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doEditFilter();
			}
		});
		createSeparatorToolItem(toolBar, TOOLBAR_SEPARATOR);
		createLabelledToolItem(toolBar, "dialog.main.tb.onlineinfo", ImageService.IMG_GLOBE, "dialog.main.onlineinfo", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doOnlineInfo();
			}
		});
		createLabelledToolItem(toolBar, "dialog.main.tb.checkprofiles", ImageService.IMG_CHECKPROFILES, "dialog.main.menu.checkprofiles", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doCheckProfiles();
			}
		});
		createSeparatorToolItem(toolBar, TOOLBAR_SEPARATOR);
		createRandomToolItem(toolBar);
		createSeparatorToolItem(toolBar, TOOLBAR_SEPARATOR);

		initColumnIds();

		ViewType[] views = VIEWS;
		// the view that is set (before, any tile or box view showed the symbol of small tiles after a start)
		ViewType currentViewType = views[currentViewIndex()];

		Menu viewMenu = new Menu(shell_, SWT.POP_UP);
		for (ViewType view: views) {
			MenuItem_.on(viewMenu).key(view.getDisplayName()).image(view.getImage()).listen(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					MenuItem menuItem = (MenuItem)event.widget;
					ViewType newViewType = views[menuItem.getParent().indexOf(menuItem)];
					if (!settings_.getValue("gui", "viewstyle").equalsIgnoreCase(newViewType.getName())) {
						toggleProfileViewType(newViewType);
					}
				}
			}).build();
		}

		ToolBar toolBarRight = createToolBar(toolbarComposite);
		// List | Tiles | Boxes as labelled buttons, easy to see; the size comes from the arrow next to them or the View menu
		String[] kindKeys = {"dialog.main.view.list", "dialog.main.view.tiles", "dialog.main.view.boxes"};
		String[] kindImages = {ImageService.IMG_TABLE, ImageService.IMG_TILES_MEDIUM, ImageService.IMG_BOXES_MEDIUM};
		for (int k = 0; k < 3; k++) {
			final int kind = k;
			viewKindItems_[k] = new ToolItem(toolBarRight, SWT.RADIO);
			viewKindItems_[k].setImage(ImageService.getResourceImage(shell_.getDisplay(), kindImages[k]));
			viewKindItems_[k].setText(text_.get(kindKeys[k]));
			viewKindItems_[k].setToolTipText(text_.get(kindKeys[k] + ".tooltip"));
			viewKindItems_[k].addListener(SWT.Selection, e -> {
				if (viewKindItems_[kind].getSelection() && (kindOfView(currentViewIndex()) != kind))
					toggleProfileViewType(VIEWS[kind == 0 ? 0: 1 + 3 * (kind - 1) + settings_.getIntValue("gui", kind == 1 ? "viewsize_tiles": "viewsize_boxes")]);
			});
		}
		viewKindItems_[kindOfView(currentViewIndex())].setSelection(true);
		viewSelector_ = createImageToolItem(toolBarRight, SWT.DROP_DOWN, ImageService.getResourceImage(shell_.getDisplay(), currentViewType.getImage()), text_.get(currentViewType.getDisplayName()),
			new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					if (event.detail == SWT.ARROW) {
						Rectangle rect = viewSelector_.getBounds();
						Point pt = new Point(rect.x, rect.y + rect.height);
						pt = toolBarRight.toDisplay(pt);
						viewMenu.setLocation(pt.x, pt.y);
						viewMenu.setVisible(true);
					} else {
						for (int i = 0; i < views.length; i++) {
							if (settings_.getValue("gui", "viewstyle").equalsIgnoreCase(views[i].getName())) {
								toggleProfileViewType(views[(i + 1) % views.length]);
								break;
							}
						}
					}
				}
			});
		setToolItemLabel(viewSelector_, text_.get(currentViewType.getDisplayName()));
		createSeparatorToolItem(toolBarRight, TOOLBAR_SEPARATOR);
		ToolItem displayScreenshots = createImageToolItem(toolBarRight, SWT.CHECK, ImageService.getResourceImage(shell_.getDisplay(), ImageService.IMG_SCREENSHOTS),
			text_.get("dialog.main.profiles.togglebutton.screenshots"), new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					ToolItem item = (ToolItem)event.widget;
					((GridData)scrolledCompositeForThumbs_.getLayoutData()).exclude = !item.getSelection();
					scrolledCompositeForThumbs_.getParent().layout();
					settings_.setBooleanValue("gui", "screenshotsvisible", item.getSelection());
					setToolItemLabel(item, text_.get(item.getSelection() ? "dialog.main.tb.screenshots.on": "dialog.main.tb.screenshots.off"));
					displayProfileInformation(false);
				}
			});
		displayScreenshots.setSelection(settings_.getBooleanValue("gui", "screenshotsvisible"));
		setToolItemLabel(displayScreenshots, text_.get(displayScreenshots.getSelection() ? "dialog.main.tb.screenshots.on": "dialog.main.tb.screenshots.off"));
		ToolItem displayNotes = createImageToolItem(toolBarRight, SWT.CHECK, ImageService.getResourceImage(shell_.getDisplay(), ImageService.IMG_NOTES),
			text_.get("dialog.main.profiles.togglebutton.notes"), new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					ToolItem item = (ToolItem)event.widget;
					sashInfoForm_.setMaximizedControl(item.getSelection() ? null: filterFolder_);
					settings_.setBooleanValue("gui", "notesvisible", item.getSelection());
					setToolItemLabel(item, text_.get(item.getSelection() ? "dialog.main.tb.notes.on": "dialog.main.tb.notes.off"));
					displayProfileInformation(false);
				}
			});
		displayNotes.setSelection(settings_.getBooleanValue("gui", "notesvisible"));
		setToolItemLabel(displayNotes, text_.get(displayNotes.getSelection() ? "dialog.main.tb.notes.on": "dialog.main.tb.notes.off"));
		// statistics and settings at the right, 'About' at the very right
		createSeparatorToolItem(toolBarRight, TOOLBAR_SEPARATOR);
		createLabelledToolItem(toolBarRight, "dialog.main.tb.statistics", ImageService.IMG_STATISTICS, "dialog.main.menu.statistics", new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					doShowStatistics();
				}
			});
		createLabelledToolItem(toolBarRight, "dialog.main.tb.settings", ImageService.IMG_TB_SETTINGS, "dialog.main.menu.adjustsettings", new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					doOpenSettingsDialog();
				}
			});
		createSeparatorToolItem(toolBarRight, TOOLBAR_SEPARATOR);
		createLabelledToolItem(toolBarRight, "dialog.main.tb.about", ImageService.IMG_TB_ABOUT, "dialog.main.menu.about2", new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					new AboutDialog(shell_).open();
				}
			});

		createViewHint(composite, toolbarComposite);
		sashInfoForm_ = createSashForm(composite, 1);
		filterFolder_ = CTabFolder_.on(sashInfoForm_).style(SWT.BORDER).ctrl();
		filterFolder_.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				updateProfilesAfterTabAction();
			}
		});
		filterFolder_.addCTabFolder2Listener(new CTabFolder2Adapter() {
			@Override
			public void close(CTabFolderEvent event) {
				if (Mess_.on(shell_).key("dialog.main.confirm.removefilter", new Object[] {((CTabItem)event.item).getText().trim()}).confirm()) {
					boolean currentTabToBeClosed = (event.item == filterFolder_.getSelection());
					try {
						FilterRepository filterRepo = new FilterRepository();
						filterRepo.remove(BaseRepository.findById(filtersList_, (Integer)event.item.getData()));
						filtersList_ = allFilters(filterRepo.listAll());
					} catch (SQLException e) {
						Mess_.on(shell_).exception(e).warning();
					}
					if (currentTabToBeClosed) {
						filterFolder_.setSelection(0);
						updateProfilesAfterTabAction();
					}
				} else {
					event.doit = false;
				}
			}
		});
		filterFolder_.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent event) {
				doEditFilter();
			}
		});
		filtersList_.forEach(this::addFilterTab);
		int filterIdx = Math.min(settings_.getIntValue("gui", "filtertab"), filtersList_.size() - 1);
		filterFolder_.setSelection(filterIdx);
		filterFolder_.getSelection().setToolTipText(text_.get("dialog.filter.notice.results", new Object[] {profilesList_.size()}));

		constructProfilesList();

		Composite informationGroup = Composite_.on(sashInfoForm_).innerLayout(1).build();
		informationGroup.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent event) {
				settings_.setIntValues("gui", "sashweights", sashInfoForm_.getWeights());
			}
		});
		notesField_ = Text_.on(informationGroup).multi().readOnly().wrap().build();
		notesField_.setFont(stringToFont(display_, settings_.getValues("gui", "notesfont"), notesField_.getFont()));
		notesField_.addDisposeListener(e -> notesField_.getFont().dispose());
		GridLayout linksGridLayout = new GridLayout();
		linksGridLayout.marginWidth = 0;
		linksGridLayout.marginHeight = 1;
		linksGridLayout.verticalSpacing = 2;
		Composite linksComposite = Composite_.on(informationGroup).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(linksGridLayout).build();
		link_ = new Link[Profile.NR_OF_LINK_DESTINATIONS];
		for (int i = 0; i < link_.length; i++)
			link_[i] = createLink(linksComposite, StringUtils.EMPTY, browseAdapter);
		playInfo_ = new Label(informationGroup, SWT.WRAP);
		playInfo_.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

		thumbHeight_ = settings_.getIntValue("gui", "screenshotsheight");
		scrolledCompositeForThumbs_ = createScrolledComposite(composite, SWT.H_SCROLL);
		scrolledCompositeForThumbs_.setMinHeight(thumbHeight_ + 10);
		GridData thumbsGridData = new GridData(SWT.FILL, SWT.TOP, true, false);
		thumbsGridData.exclude = !settings_.getBooleanValue("gui", "screenshotsvisible");
		scrolledCompositeForThumbs_.setLayoutData(thumbsGridData);

		RowLayout thumbRowLayout = new RowLayout(SWT.HORIZONTAL);
		thumbRowLayout.marginLeft = thumbRowLayout.marginRight = thumbRowLayout.marginTop = thumbRowLayout.marginBottom = 0;
		thumbRowLayout.spacing = 5;
		thumbsComposite_ = Composite_.on(scrolledCompositeForThumbs_).layout(thumbRowLayout).build();
		scrolledCompositeForThumbs_.setContent(thumbsComposite_);
		scrolledCompositeForThumbs_.getHorizontalBar().setPageIncrement(300);
		scrolledCompositeForThumbs_.getHorizontalBar().setIncrement(50);

		sashInfoForm_.setWeights(settings_.getIntValues("gui", "sashweights"));
		sashInfoForm_.setMaximizedControl(settings_.getBooleanValue("gui", "notesvisible") ? null: filterFolder_);

		thumbMenu_ = new Menu(thumbsComposite_);
		MenuItem_.on(thumbMenu_).key("dialog.main.thumb.remove").image(ImageService.IMG_DELETE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (currentThumbFile_ != null) {
					if (Mess_.on(shell_).key("dialog.main.confirm.removethumb", new Object[] {currentThumbFile_}).confirm()) {
						FilesUtils.removeFile(currentThumbFile_);
						ImageService.clearCache(currentThumbFile_.getPath());
						displayProfileInformation(true);
					}
					currentThumbFile_ = null;
				}
			}
		}).build();
		MenuItem_.on(thumbMenu_).build();
		MenuItem_.on(thumbMenu_).key("dialog.main.thumb.openfolder").image(ImageService.IMG_FOLDER).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (currentThumbFile_ != null)
					SystemUtils.openDirForViewing(currentThumbFile_.getParentFile(), getParent());
			}
		}).build();
		MenuItem_.on(thumbMenu_).key("dialog.main.thumb.refresh").image(ImageService.IMG_REFRESH).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				for (ProfilesListItem item: profileTable_.getItems())
					item.resetCachedInfo();
				ImageService.clearCache();
				displayProfileInformation(true);
			}
		}).build();
	}

	private void constructProfilesList() {
		if (settings_.getValue("gui", "viewstyle").equalsIgnoreCase(ProfilesListType.SMALL_TILES.toString())) {
			profileTable_ = new ProfilesList(filterFolder_, ProfilesListType.SMALL_TILES);
		} else if (settings_.getValue("gui", "viewstyle").equalsIgnoreCase(ProfilesListType.MEDIUM_TILES.toString())) {
			profileTable_ = new ProfilesList(filterFolder_, ProfilesListType.MEDIUM_TILES);
		} else if (settings_.getValue("gui", "viewstyle").equalsIgnoreCase(ProfilesListType.LARGE_TILES.toString())) {
			profileTable_ = new ProfilesList(filterFolder_, ProfilesListType.LARGE_TILES);
		} else if (settings_.getValue("gui", "viewstyle").equalsIgnoreCase(ProfilesListType.SMALL_BOXES.toString())) {
			profileTable_ = new ProfilesList(filterFolder_, ProfilesListType.SMALL_BOXES);
		} else if (settings_.getValue("gui", "viewstyle").equalsIgnoreCase(ProfilesListType.MEDIUM_BOXES.toString())) {
			profileTable_ = new ProfilesList(filterFolder_, ProfilesListType.MEDIUM_BOXES);
		} else if (settings_.getValue("gui", "viewstyle").equalsIgnoreCase(ProfilesListType.LARGE_BOXES.toString())) {
			profileTable_ = new ProfilesList(filterFolder_, ProfilesListType.LARGE_BOXES);
		} else {
			profileTable_ = new ProfilesList(filterFolder_, ProfilesListType.TABLE, this, columnIds_, columnNames_);
		}

		for (CTabItem tab: filterFolder_.getItems())
			tab.setControl(profileTable_.getControl());
		profileTable_.setWarningHandlers(this::profileProblemsText, this::doShowProfileProblem);

		Menu menu = new Menu(profileTable_.getControl());
		MenuItem_.on(menu).build();
		MenuItem_.on(menu).key("dialog.main.profile.compare").image(ImageService.IMG_ZOOM).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doCompareProfiles();
			}
		}).build();
		MenuItem_.on(menu).key("dialog.main.profile.checkprofile").image(ImageService.IMG_ZOOM).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if ((profileTable_.getSelectionIndex() != -1) && Boolean.TRUE.equals(new CheckProfilesDialog(shell_, getSelectedProfiles()).open())) {
					updateProfilesList(getSelectedProfileIds());
					displayProfileInformation(false);
				}
			}
		}).build();
		MenuItem fixProblemsItem = MenuItem_.on(menu).key("dialog.main.profile.fixproblems").image(ImageService.IMG_WARNING).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doFixProfileProblems();
			}
		}).build();
		menu.addMenuListener(new MenuAdapter() {
			@Override
			public void menuShown(MenuEvent event) {
				// only for profiles with a warning sign
				fixProblemsItem.setEnabled(getSelectedProfiles().stream().map(org.dbgl.util.ProfileWarnings::known).anyMatch(p -> (p != null) && !p.isEmpty()));
			}
		});
		MenuItem_.on(menu).build();
		MenuItem presetsItem = MenuItem_.on(menu).key("dialog.main.profile.applypreset").image(ImageService.IMG_SETTINGS).build();
		Menu presetsMenu = new Menu(presetsItem);
		presetsItem.setMenu(presetsMenu);
		presetsMenu.addMenuListener(new MenuAdapter() {
			@Override
			public void menuShown(MenuEvent event) {
				Stream.of(presetsMenu.getItems()).forEach(MenuItem::dispose); // own presets may have been added
				org.dbgl.gui.abstractdialog.PresetsMenu.addPresetItems(presetsMenu, MainWindow.this::doApplyPreset);
			}
		});
		MenuItem_.on(menu).build();
		MenuItem_.on(menu).key("dialog.main.profile.switchdosboxversion").image(ImageService.IMG_MIGRATE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if ((profileTable_.getSelectionIndex() != -1) && Boolean.TRUE.equals(new SwitchDosboxVersionDialog(shell_, getSelectedProfiles()).open())) {
					updateProfilesList(getSelectedProfileIds());
					displayProfileInformation(false);
				}
			}
		}).build();
		MenuItem_.on(menu).key("dialog.main.profile.importconf").image(ImageService.IMG_IMPORT).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doImportConf(profileTable_.getSelectionIndex(), null);
			}
		}).build();
		MenuItem restoreVersionItem = MenuItem_.on(menu).key("dialog.main.profile.restoreversion").image(ImageService.IMG_REFRESH).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doRestoreProfileVersion();
			}
		}).build();
		menu.addMenuListener(new MenuAdapter() {
			@Override
			public void menuShown(MenuEvent event) {
				// greyed out while DBGL keeps no earlier version of the profile (nor its original)
				int index = profileTable_.getSelectionIndex();
				int id = (index >= 0) && (index < profilesList_.size()) ? profilesList_.get(index).getId(): -1;
				restoreVersionItem.setEnabled((id != -1) && (!org.dbgl.util.ProfileHistory.list(id).isEmpty() || (org.dbgl.util.ProfileHistory.original(id) != null)));
			}
		});
		MenuItem_.on(menu).build();
		MenuItem_.on(menu).key("dialog.main.profile.findextras").image(ImageService.IMG_ZOOM).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doFindExtras();
			}
		}).build();
		MenuItem_.on(menu).key("dialog.main.menu.coverbatch").image(ImageService.IMG_GLOBE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doCoverBatch();
			}
		}).build();
		MenuItem_.on(menu).build();
		MenuItem_.on(menu).key("dialog.main.profile.openfolder").image(ImageService.IMG_FOLDER).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				int index = profileTable_.getSelectionIndex();
				if (index != -1) {
					Profile prof = profilesList_.get(index);
					try {
						String warnings = prof.resetAndLoadConfiguration();
						if (StringUtils.isNotEmpty(warnings))
							Mess_.on(shell_).txt(warnings).warning();
						SystemUtils.openDirForViewing(prof.getConfiguration().getAutoexec().getCanonicalGameDir(), getParent());
					} catch (IOException e) {
						Mess_.on(shell_).exception(e).warning();
					}
				}
			}
		}).build();
		MenuItem_.on(menu).key("dialog.main.profile.opencapturesfolder").image(ImageService.IMG_FOLDER).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				int index = profileTable_.getSelectionIndex();
				if (index != -1)
					SystemUtils.openDirForViewing(profilesList_.get(index).getCanonicalCaptures(), getParent());
			}
		}).build();
		MenuItem_.on(menu).key("dialog.main.profile.openprofilesfolder").image(ImageService.IMG_FOLDER).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				int index = profileTable_.getSelectionIndex();
				if (index != -1) {
					File conf = profilesList_.get(index).getConfigurationCanonicalFile();
					if (conf != null)
						SystemUtils.openDirForViewing(conf.getParentFile(), getParent());
				}
			}
		}).build();
		Menu profileViewSubMenu = createMenu(menu, "dialog.main.profile.view", ImageService.IMG_ZOOM);
		MenuItem_.on(menu).build();
		MenuItem_.on(menu).key("dialog.main.profile.add").image(ImageService.IMG_NEW).listen(addProfAdapter).build();
		MenuItem_.on(menu).key("dialog.main.profile.edit").image(ImageService.IMG_EDIT).listen(editProfAdapter).build();
		MenuItem_.on(menu).key("dialog.main.profile.replacevalues").image(ImageService.IMG_EDIT).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doReplaceValues();
			}
		}).build();
		MenuItem_.on(menu).key("dialog.main.profile.duplicate").image(ImageService.IMG_DUPLICATE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doDuplicateProfile();
			}
		}).build();
		MenuItem_.on(menu).key("dialog.main.profile.remove").image(ImageService.IMG_DELETE).listen(removeProfAdapter).build();
		MenuItem_.on(menu).build();
		MenuItem_.on(menu).key("dialog.main.profile.togglefavorite").image(ImageService.IMG_FAVORITE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doToggleFavoriteProfile();
			}
		}).build();
		if (SystemUtils.IS_WINDOWS || SystemUtils.IS_LINUX) {
			MenuItem_.on(menu).build();
			MenuItem_.on(menu).key("dialog.main.profile.createshortcut").image(ImageService.IMG_SHORTCUT).listen(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					IntStream.of(profileTable_.getSelectionIndices()).forEach(x -> {
						try {
							ExecuteUtils.createShortcut(profilesList_.get(x));
						} catch (IOException e) {
							Mess_.on(shell_).exception(e).warning();
						}
					});
				}
			}).build();
		}

		menu.addMenuListener(new MenuAdapter() {
			@Override
			public void menuShown(MenuEvent event) {
				if (profileTable_.getSelectionIndex() != -1) {
					Profile prof = profilesList_.get(profileTable_.getSelectionIndex());

					for (MenuItem it: menu.getItems()) {
						if (it.getStyle() == SWT.SEPARATOR)
							break;
						it.dispose();
					}

					if (dbversionsList_.size() > 1)
						MenuItem_.on(menu).pos(0).key("dialog.main.profile.startmanuallywith").image(ImageService.IMG_RUN).build().setMenu(createDosboxVersionsSubmenu(menu, ProfileRunMode.NORMAL, true));
					MenuItem_.on(menu).pos(0).key("dialog.main.profile.startmanually").image(ImageService.IMG_RUN).listen(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent evnt) {
							doRunProfile(ProfileRunMode.NORMAL, true);
						}
					}).build();
					if (prof.hasAltExe(1)) {
						if (dbversionsList_.size() > 1)
							MenuItem_.on(menu).pos(0).key(prof.getAltExeFilenames()[1]).image(ImageService.IMG_RUN).build().setMenu(createDosboxVersionsSubmenu(menu, ProfileRunMode.ALT2, false));
						MenuItem_.on(menu).pos(0).key(prof.getAltExeFilenames()[1]).image(ImageService.IMG_RUN).listen(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent evnt) {
								doRunProfile(ProfileRunMode.ALT2, false);
							}
						}).build();
					}
					if (prof.hasAltExe(0)) {
						if (dbversionsList_.size() > 1)
							MenuItem_.on(menu).pos(0).key(prof.getAltExeFilenames()[0]).image(ImageService.IMG_RUN).build().setMenu(createDosboxVersionsSubmenu(menu, ProfileRunMode.ALT1, false));
						MenuItem_.on(menu).pos(0).key(prof.getAltExeFilenames()[0]).image(ImageService.IMG_RUN).listen(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent evnt) {
								doRunProfile(ProfileRunMode.ALT1, false);
							}
						}).build();
					}
					if (prof.hasSetup()) {
						if (dbversionsList_.size() > 1)
							MenuItem_.on(menu).pos(0).key("dialog.main.profile.setupwith").image(ImageService.IMG_SETUP).build().setMenu(createDosboxVersionsSubmenu(menu, ProfileRunMode.SETUP, false));
						MenuItem_.on(menu).pos(0).key("dialog.main.profile.setup").image(ImageService.IMG_SETUP).listen(setupProfAdapter).build();
					}
					if (dbversionsList_.size() > 1)
						MenuItem_.on(menu).pos(0).key("dialog.main.profile.runwith").image(ImageService.IMG_RUN).build().setMenu(createDosboxVersionsSubmenu(menu, ProfileRunMode.NORMAL, false));
					MenuItem_.on(menu).pos(0).key("dialog.main.profile.run").image(ImageService.IMG_RUN).listen(runProfAdapter).build();

					for (MenuItem item: profileViewSubMenu.getItems())
						item.dispose();
					// the profile configuration file always first, then the links (manuals, extras, ...)
					MenuItem_.on(profileViewSubMenu).key("dialog.main.profile.view.conf").image(ImageService.IMG_PROFILE_CONF).listen(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent evnt) {
							SystemUtils.openForEditing(prof.getConfigurationCanonicalFile(), getParent());
						}
					}).build();
					// the output of the last start, when the profile saves it (DOSBox page of the profile)
					File lastLog = org.dbgl.util.DosboxLog.latest(prof.getId());
					if (lastLog != null) {
						MenuItem_.on(profileViewSubMenu).key("dialog.main.profile.view.lastlog").image(ImageService.IMG_LOG).listen(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent evnt) {
								doShowLastLog(prof, lastLog);
							}
						}).build();
					}
					if (Stream.of(prof.getLinks()).anyMatch(l -> StringUtils.isNotBlank(l.getDestination())))
						MenuItem_.on(profileViewSubMenu).build();
					for (org.dbgl.model.Link lnk: prof.getLinks()) {
						String destination = lnk.getDestination();
						if (StringUtils.isNotBlank(destination)) {
							File linked = Profile.linkFile(lnk);
							String shown = StringUtils.abbreviateMiddle(lnk.getDisplayTitle(), "....", 80);
							if ((linked != null) && !linked.exists()) // moved or deleted: say so before the click does nothing
								shown = text_.get("dialog.main.profile.view.missinglink", new Object[] {shown});
							MenuItem_.on(profileViewSubMenu).txt(shown)
									.image(org.dbgl.util.ExtrasFinder.iconOf(lnk.getTitle(), destination)).listen(new SelectionAdapter() {
								@Override
								public void widgetSelected(SelectionEvent evnt) {
									SystemUtils.openForBrowsing(destination, getParent());
								}
							}).build();
						}
					}
				}
			}
		});

		profileTable_.setMenu(menu);
		profileTable_.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent event) {
				doRunProfile(ProfileRunMode.NORMAL, false);
			}
		});
		profileTable_.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent event) {
				if (event.keyCode == SWT.DEL || (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'r'))) {
					doRemoveProfile();
				} else if (event.keyCode == SWT.INSERT || (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'n'))) {
					doAddProfile(null, null);
				} else if (event.keyCode == SWT.F2) {
					doEditProfile(true);
				} else if (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'm')) {
					doToggleFavoriteProfile();
				} else if (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'd')) {
					doDuplicateProfile();
				} else if (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'a')) {
					profileTable_.selectAll();
				} else if (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'f')) {
					doAddFilter();
				} else if (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'c')) {
					int index = profileTable_.getSelectionIndex();
					if (index != -1)
						SystemUtils.openForEditing(profilesList_.get(index).getConfigurationCanonicalFile(), getParent());
				} else if (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'w')) {
					if (requireDefaultDBVersion() == null)
						return;

					updateWithAddedProfile(new AddGameWizardDialog(shell_).open());
				}
			}
		});
		profileTable_.addTraverseListener(event -> {
			if ((event.stateMask == SWT.MOD1) && (event.detail == SWT.TRAVERSE_RETURN))
				doEditProfile(false);
			else if ((event.stateMask == SWT.SHIFT) && (event.detail == SWT.TRAVERSE_RETURN))
				doRunProfile(ProfileRunMode.SETUP, false);
			else if (event.detail == SWT.TRAVERSE_RETURN)
				doRunProfile(ProfileRunMode.NORMAL, false);
		});
		profileTable_.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (delayedAction_ == null || !delayedAction_.isAlive()) {
					int delay = settings_.getIntValue("gui", "profileinfo_delay");
					delayedAction_ = new DelayedAction(display_, x -> displayProfileInformation(x), false, delay);
					delayedAction_.start();
				} else if (delayedAction_.isAlive()) {
					delayedAction_.restart();
				}
			}
		});

		DropTarget target = new DropTarget(profileTable_.getControl(), DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_DEFAULT);
		target.setTransfer(FileTransfer.getInstance());
		target.addDropListener(new DropTargetAdapter() {
			@Override
			public void drop(DropTargetEvent event) {
				String[] filenames = (String[])event.data;
				if ((filenames != null) && (filenames.length == 1) && filenames[0].toLowerCase().endsWith(".conf") && (event.item instanceof org.eclipse.swt.widgets.TableItem item)) {
					// a DOSBox configuration dropped onto a profile: take over its settings
					int index = item.getParent().indexOf(item);
					if (index != -1) {
						profileTable_.setSelection(index);
						doImportConf(index, filenames[0]);
						return;
					}
				}
				if (filenames != null && filenames.length >= 1)
					doAddProfile(filenames[0], filenames);
			}
		});
	}

	private void rebuildProfilesTable() {
		Set<Integer> selectedProfileIds = getSelectedProfileIds();

		initColumnIds();

		for (CTabItem tab: filterFolder_.getItems())
			tab.setControl(null);
		profileTable_.dispose();

		constructProfilesList();

		updateProfilesList(selectedProfileIds);
	}

	private void updateWithAddedProfile(Profile profile) {
		if (profile != null) {
			if (settings_.getBooleanValue("gui", "autosortonupdate") || (filterFolder_.getSelectionIndex() > 0)) {
				updateProfilesList(new HashSet<>(Arrays.asList(profile.getId())));
			} else {
				profilesList_.add(profile);
				addProfileToTable(profile);
				profileTable_.setSelection(profileTable_.getItemCount() - 1);
				profileTable_.setFocus();
			}
			displayProfileInformation(false);
		}
	}

	private void updateProfileListAfterEdit(int index, Profile profile) {
		boolean quickUpdate = true;
		if (settings_.getBooleanValue("gui", "autosortonupdate") || (filterFolder_.getSelectionIndex() > 0)) {
			try {
				profilesList_ = listProfiles();
				if (index != BaseRepository.indexOf(profilesList_, profile))
					quickUpdate = false;
			} catch (SQLException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		}
		if (quickUpdate) {
			profilesList_.set(index, profile);
			setProfileTableItem(profileTable_.getItem(index), profile);
		} else {
			updateProfilesList(new HashSet<>(Arrays.asList(profile.getId())));
		}
	}

	private void updateProfilesAfterTabAction() {
		int tabIndex = filterFolder_.getSelectionIndex();
		settings_.setIntValue("gui", "filtertab", tabIndex);
		filterClause_ = filtersList_.get(tabIndex).getFilter();
		updateProfilesList(getSelectedProfileIds());
		for (CTabItem tab: filterFolder_.getItems())
			tab.setToolTipText(null);
		filterFolder_.getSelection().setToolTipText(text_.get("dialog.filter.notice.results", new Object[] {profilesList_.size()}));
		displayProfileInformation(false);
	}

	/** the same space between all groups of buttons in the toolbar */
	private static final int TOOLBAR_SEPARATOR = 12;

	/** a name below a toolbar button that changes with what it shows (as a tooltip when only symbols are wanted) */
	private void setToolItemLabel(ToolItem item, String label) {
		if (settings_.getIntValue("gui", "buttondisplay") == 2) {
			item.setToolTipText(label);
		} else {
			item.setText(label);
			item.getParent().getParent().layout(true, true);
		}
	}

	private void toggleProfileViewType(ViewType newViewType) {
		viewSelector_.setImage(ImageService.getResourceImage(shell_.getDisplay(), newViewType.getImage()));
		viewSelector_.setToolTipText(text_.get(newViewType.getDisplayName()));
		setToolItemLabel(viewSelector_, text_.get(newViewType.getDisplayName()));
		settings_.setValue("gui", "viewstyle", newViewType.getName().toLowerCase());
		int index = currentViewIndex();
		if (index > 0) // remember the size per kind, for the List | Tiles | Boxes buttons
			settings_.setIntValue("gui", kindOfView(index) == 1 ? "viewsize_tiles": "viewsize_boxes", (index - 1) % 3);
		for (int k = 0; k < viewKindItems_.length; k++)
			if (viewKindItems_[k] != null)
				viewKindItems_[k].setSelection(k == kindOfView(index));
		for (int i = 0; i < viewMenuItems_.length; i++)
			if (viewMenuItems_[i] != null)
				viewMenuItems_[i].setSelection(i == index);
		settings_.setBooleanValue("gui", "viewhintshown", true); // found it: no more hint
		if ((viewHint_ != null) && !viewHint_.isDisposed()) {
			Composite p = viewHint_.getParent();
			viewHint_.dispose();
			p.layout();
		}
		prepareTiles(newViewType);
		rebuildProfilesTable();
	}

	/** how many images of the tiles and boxes are read in advance (the rest when they are scrolled to) */
	private static final int PREPARED_TILES = 240;

	/**
	 * Tiles and boxes: read and scale the first images in the background, with a progress bar, instead of the window hanging
	 * while they are drawn. Cancel just shows the view without waiting.
	 */
	private void prepareTiles(ViewType viewType) {
		ProfilesListType type;
		try {
			type = ProfilesListType.valueOf(viewType.getName().toUpperCase());
		} catch (IllegalArgumentException e) {
			return;
		}
		if ((type == ProfilesListType.TABLE) || (shell_.getMonitor().getZoom() != 100) || profilesList_.isEmpty())
			return;
		String kind = type.name().toLowerCase().replace("_tiles", "_tile").replace("_boxes", "_box");
		int width = settings_.getIntValue("gui", kind + "_width") - 4;
		int height = settings_.getIntValue("gui", kind + "_height") - 22;
		boolean keepAspect = settings_.getBooleanValue("gui", "screenshotscolumnkeepaspectratio");
		// the selected ones first, they are shown
		Set<Integer> selected = getSelectedProfileIds();
		List<String> captures = new ArrayList<>();
		profilesList_.stream().filter(p -> selected.contains(p.getId())).forEach(p -> captures.add(p.getCapturesString()));
		profilesList_.stream().filter(p -> !selected.contains(p.getId())).limit(PREPARED_TILES).forEach(p -> captures.add(p.getCapturesString()));
		java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
		try {
			org.dbgl.gui.controls.BusyTask.run(shell_, text_.get("dialog.main.view.preparing"), done, captures.size(), () -> {
				for (String c: captures) {
					File[] thumbs = new org.dbgl.model.ThumbInfo(c).getAllThumbs();
					if (thumbs.length > 0)
						ImageService.prepareResizedImage(width, height, keepAspect, thumbs[0].getPath());
					done.incrementAndGet();
				}
				return null;
			});
		} catch (org.dbgl.gui.controls.BusyTask.Cancelled e) {
			// show the view now, the remaining images come when drawn
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** the 7 views: list, then small, medium and large tiles, then small, medium and large boxes */
	private static final ViewType[] VIEWS = {new ViewType(ProfilesListType.TABLE.toString(), ImageService.IMG_TABLE, "dialog.main.profiles.viewtype.table"),
			new ViewType(ProfilesListType.SMALL_TILES.toString(), ImageService.IMG_TILES_SMALL, "dialog.main.profiles.viewtype.smalltiles"),
			new ViewType(ProfilesListType.MEDIUM_TILES.toString(), ImageService.IMG_TILES_MEDIUM, "dialog.main.profiles.viewtype.mediumtiles"),
			new ViewType(ProfilesListType.LARGE_TILES.toString(), ImageService.IMG_TILES_LARGE, "dialog.main.profiles.viewtype.largetiles"),
			new ViewType(ProfilesListType.SMALL_BOXES.toString(), ImageService.IMG_BOXES_SMALL, "dialog.main.profiles.viewtype.smallboxes"),
			new ViewType(ProfilesListType.MEDIUM_BOXES.toString(), ImageService.IMG_BOXES_MEDIUM, "dialog.main.profiles.viewtype.mediumboxes"),
			new ViewType(ProfilesListType.LARGE_BOXES.toString(), ImageService.IMG_BOXES_LARGE, "dialog.main.profiles.viewtype.largeboxes")};
	private final ToolItem[] viewKindItems_ = new ToolItem[3];
	private final MenuItem[] viewMenuItems_ = new MenuItem[VIEWS.length];
	private Composite viewHint_;

	private int currentViewIndex() {
		String style = settings_.getValue("gui", "viewstyle");
		for (int i = 0; i < VIEWS.length; i++)
			if (VIEWS[i].getName().equalsIgnoreCase(style))
				return i;
		return 0;
	}

	/** 0 list, 1 tiles, 2 boxes */
	private static int kindOfView(int index) {
		return index == 0 ? 0: (index <= 3 ? 1: 2);
	}

	/** the View menu: all 7 views, Ctrl+1 to Ctrl+7 */
	private void createViewMenu(Menu appMenuBar) {
		Menu viewMenu = createMenu(appMenuBar, "dialog.main.menu.view");
		for (int i = 0; i < VIEWS.length; i++) {
			if ((i == 1) || (i == 4))
				new MenuItem(viewMenu, SWT.SEPARATOR);
			final int index = i;
			MenuItem item = new MenuItem(viewMenu, SWT.RADIO);
			item.setText(text_.get(VIEWS[i].getDisplayName()) + (SystemUtils.IS_OSX ? "\tCmd+": "\tCtrl+") + (i + 1));
			item.setAccelerator(SWT.MOD1 | ('1' + i));
			item.setImage(ImageService.getResourceImage(shell_.getDisplay(), VIEWS[i].getImage()));
			item.addListener(SWT.Selection, e -> {
				if (item.getSelection() && (currentViewIndex() != index))
					toggleProfileViewType(VIEWS[index]);
			});
			viewMenuItems_[i] = item;
		}
		viewMenuItems_[currentViewIndex()].setSelection(true);
	}

	/** once, while the list view is used: a small bar that tells about the tile and box views */
	private void createViewHint(Composite parent, Control below) {
		if (settings_.getBooleanValue("gui", "viewhintshown") || (currentViewIndex() != 0))
			return;
		viewHint_ = new Composite(parent, SWT.BORDER);
		viewHint_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout layout = new GridLayout(4, false);
		layout.marginHeight = 2;
		viewHint_.setLayout(layout);
		Label icon = new Label(viewHint_, SWT.NONE);
		icon.setImage(ImageService.getResourceImage(shell_.getDisplay(), ImageService.IMG_TILES_MEDIUM));
		Label text = new Label(viewHint_, SWT.WRAP);
		text.setText(text_.get("dialog.main.view.hint"));
		text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button tryIt = new Button(viewHint_, SWT.PUSH);
		tryIt.setText(text_.get("dialog.main.view.hint.try"));
		tryIt.addListener(SWT.Selection, e -> display_.asyncExec(() -> toggleProfileViewType(VIEWS[2])));
		Button never = new Button(viewHint_, SWT.PUSH);
		never.setText(text_.get("dialog.main.view.hint.never"));
		never.addListener(SWT.Selection, e -> {
			settings_.setBooleanValue("gui", "viewhintshown", true);
			Composite p = viewHint_.getParent();
			viewHint_.dispose();
			p.layout();
		});
		viewHint_.moveBelow(below);
	}

	@Override
	public void doReorder(int columnId, int dir) {
		Set<Integer> selectedProfiles = getSelectedProfileIds();
		try {
			orderingVector_.addOrdering(columnIds_[columnId], dir == SWT.UP);
			profilesList_ = listProfiles();
		} catch (SQLException e) {
			Mess_.on(shell_).exception(e).warning();
		}
		for (int i = 0; i < profilesList_.size(); i++) {
			setProfileTableItem(profileTable_.getItem(i), profilesList_.get(i));
		}
		profileTable_.setSelection(getIndicesByIds(selectedProfiles));
		settings_.setIntValues("gui", "sortcolumn", orderingVector_.getColumns());
		settings_.setBooleanValues("gui", "sortascending", orderingVector_.getAscendings());
	}

	private Menu createDosboxVersionsSubmenu(Menu parent, ProfileRunMode mode, boolean prepareOnly) {
		Menu dosboxVersionsSubMenu = new Menu(parent);
		for (DosboxVersion dbVersion: dbversionsList_) {
			MenuItem_.on(dosboxVersionsSubMenu).txt(dbVersion.getTitle()).listen(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					Profile prof = profilesList_.get(profileTable_.getSelectionIndex());
					try {
						String warningsLog = prof.resetAndLoadConfiguration();
						if (StringUtils.isNotEmpty(warningsLog))
							Mess_.on(shell_).txt(warningsLog).warning();
						ExecuteUtils.doRunProfile(mode, prof, dbVersion, prepareOnly, display_);
					} catch (IOException e) {
						Mess_.on(shell_).exception(e).warning();
					}
				}
			}).build();
		}
		return dosboxVersionsSubMenu;
	}

	private void displayProfileInformation(boolean forceRefresh) {
		boolean screenshotsVisible = settings_.getBooleanValue("gui", "screenshotsvisible");
		boolean notesVisible = settings_.getBooleanValue("gui", "notesvisible");
		int index = profileTable_.getSelectionIndex();
		if (index == -1) {
			if (screenshotsVisible)
				displayScreenshots(new File[] {});
			if (notesVisible) {
				notesField_.setText(StringUtils.EMPTY);
				displayLinks(new org.dbgl.model.Link[] {});
				playInfo_.setText(StringUtils.EMPTY);
			}
			setupToolItem_.setEnabled(false);
		} else {
			settings_.setIntValue("gui", "selectedprofile", index);
			Profile prof = profilesList_.get(index);
			ThumbInfo thumbInfo = (ThumbInfo)profileTable_.getItem(index).getData();
			if (forceRefresh)
				thumbInfo.resetCachedInfo();
			if (screenshotsVisible)
				displayScreenshots(thumbInfo.getAllThumbs());
			if (notesVisible) {
				if (!Strings.CS.equals(prof.getNotes(), notesField_.getText()))
					notesField_.setText(prof.getNotes());
				displayLinks(prof.getLinks());
				displayPlayInfo(prof);
			}
			setupToolItem_.setEnabled(prof.hasSetup());
		}
		if (forceRefresh)
			profileTable_.redraw();
	}

	/** the last log of the DOSBox output of a profile, shown in DBGL (to save it elsewhere, or to open its folder) */
	private void doShowLastLog(Profile prof, File log) {
		try {
			String content = org.dbgl.util.DosboxLog.read(log, 2_000_000).replace("\r\n", "\n").replace("\n", org.eclipse.swt.widgets.Text.DELIMITER);
			String name = log.getName().replace(".log", "");
			new DifferencesDialog(shell_, text_.get("dialog.main.profile.view.lastlog.title", new Object[] {prof.getTitle(), name}), content, "dosbox-" + name + ".log", "lastlogdialog")
					.withImage(ImageService.IMG_LOG).withAction("dialog.profile.dosboxlog.open", s -> {
						SystemUtils.openForBrowsing(log.getParentFile().getPath(), s);
						return null;
					}).open();
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	/** below the notes: how long the game was played, in how many sessions, and when last */
	private void displayPlayInfo(Profile prof) {
		String text = org.dbgl.util.ProfileReports.playInfo(prof, text_);
		if (!text.equals(playInfo_.getText())) {
			playInfo_.setText(text);
			playInfo_.getParent().layout();
		}
	}

	private void displayLinks(org.dbgl.model.Link[] profileLinks) {
		boolean showLinks = false;
		for (int i = 0; i < profileLinks.length; i++) {
			String destination = profileLinks[i].getDestination();
			boolean isBlank = StringUtils.isBlank(destination);
			if (!isBlank) {
				if (SettingsService.getInstance().getIntValue("gui", "linking") == 1) {
					link_[i].setText(profileLinks[i].getPathBasedAnchor());
					link_[i].setToolTipText(destination);
				} else {
					link_[i].setText(profileLinks[i].getUrlBasedAnchor());
					link_[i].setToolTipText(profileLinks[i].getUrl());
				}
			} else {
				link_[i].setText(StringUtils.EMPTY);
				link_[i].setToolTipText(StringUtils.EMPTY);
			}
			link_[i].pack();
			((GridData)link_[i].getLayoutData()).exclude = isBlank;
			showLinks |= !isBlank;
		}
		((GridData)link_[0].getParent().getLayoutData()).exclude = !showLinks;
		link_[0].getParent().layout();
		link_[0].getParent().getParent().layout();
	}

	private void displayScreenshots(File[] files) {
		for (Control c: thumbsComposite_.getChildren()) {
			c.setMenu(null);
			c.dispose();
		}
		for (int i = 0; i < files.length; i++) {
			File file = files[i];
			String label = null;
			if (settings_.getBooleanValue("gui", "screenshotsfilename")) {
				label = file.getName().toLowerCase();
				label = ' ' + label.substring(0, label.lastIndexOf('.')) + ' ';
			}
			int j = i;
			Image img = ImageService.getCachedHeightLimitedImage(display_, thumbHeight_, file.getPath(), label);
			Button buttonItem = Button_.on(thumbsComposite_).layoutData(new RowData(img.getImageData().width + 10, img.getImageData().height + 10)).image(img, false).tooltipTxt(file.getPath()).listen(
				new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent event) {
						new Thumb(shell_, files, j).open();
					}
				}).ctrl();
			buttonItem.addMenuDetectListener(e -> currentThumbFile_ = new File(file.getPath()));
			buttonItem.setMenu(thumbMenu_);
		}
		thumbsComposite_.setVisible(thumbsComposite_.getChildren().length != 0);
		thumbsComposite_.layout();
		thumbsComposite_.pack();
	}

	private void createDosboxVersionsTab(CTabFolder tabFolder) {
		SelectionAdapter addDosboxAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doAddDosboxVersion();
			}
		};
		SelectionAdapter editDosboxAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doEditDosboxVersion();
			}
		};
		SelectionAdapter removeDosboxAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doRemoveDosboxVersion();
			}
		};
		SelectionAdapter runDosboxAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doRunDosbox();
			}
		};

		Composite composite = Composite_.on(tabFolder).innerLayout(1).tab("dialog.main.dosboxversions").build();
		ToolBar toolBar = createToolBar(createRow(composite));
		createImageToolItem(toolBar, "dialog.main.addversion", ImageService.IMG_TB_NEW, addDosboxAdapter);
		createImageToolItem(toolBar, "dialog.main.editversion", ImageService.IMG_TB_EDIT, editDosboxAdapter);
		createImageToolItem(toolBar, "dialog.main.removeversion", ImageService.IMG_TB_DELETE, removeDosboxAdapter);
		createImageToolItem(toolBar, "dialog.main.runversion", ImageService.IMG_TB_RUN, runDosboxAdapter);
		dbversionTable_ = Table_.on(composite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).header().build();
		addDBColumn("dialog.main.dosboxversions.column.title", 0);
		addDBColumn("dialog.main.dosboxversions.column.family", 1);
		addDBColumn("dialog.main.dosboxversions.column.version", 2);
		addDBColumn("dialog.main.dosboxversions.column.path", 3);
		addDBColumn("dialog.main.dosboxversions.column.default", 4);
		addDBColumn("dialog.main.dosboxversions.column.id", 5);
		addDBColumn("dialog.main.generic.column.created", 6);
		addDBColumn("dialog.main.generic.column.lastmodify", 7);
		addDBColumn("dialog.main.generic.column.lastrun", 8);
		addDBColumn("dialog.main.generic.column.runs", 9);

		Menu menu = new Menu(dbversionTable_);
		MenuItem_.on(menu).key("dialog.main.dosboxversion.run").image(ImageService.IMG_RUN).listen(runDosboxAdapter).build();
		MenuItem_.on(menu).build();
		MenuItem_.on(menu).key("dialog.main.dosboxversion.openfolder").image(ImageService.IMG_FOLDER).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				int index = dbversionTable_.getSelectionIndex();
				if (index != -1)
					SystemUtils.openDirForViewing(dbversionsList_.get(index).getConfigurationCanonicalFile().getParentFile(), getParent());
			}
		}).build();
		Menu viewDosboxSubMenu = createMenu(menu, "dialog.main.profile.view", ImageService.IMG_ZOOM);
		MenuItem_.on(viewDosboxSubMenu).key("dialog.main.dosboxversion.view.conf").image(ImageService.IMG_PROFILE_CONF).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (dbversionTable_.getSelectionIndex() != -1) {
					DosboxVersion dbversion = dbversionsList_.get(dbversionTable_.getSelectionIndex());
					SystemUtils.openForEditing(dbversion.getConfigurationCanonicalFile(), getParent());
				}
			}
		}).build();
		MenuItem_.on(menu).build();
		MenuItem_.on(menu).key("dialog.main.dosboxversion.add").image(ImageService.IMG_NEW).listen(addDosboxAdapter).build();
		MenuItem_.on(menu).key("dialog.main.dosboxversion.edit").image(ImageService.IMG_EDIT).listen(editDosboxAdapter).build();
		MenuItem_.on(menu).key("dialog.main.dosboxversion.remove").image(ImageService.IMG_DELETE).listen(removeDosboxAdapter).build();
		MenuItem_.on(menu).key("dialog.main.dosboxversion.removewithfolder").image(ImageService.IMG_DELETE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doRemoveDosboxVersionWithFolder();
			}
		}).build();
		MenuItem_.on(menu).build();
		MenuItem_.on(menu).key("dialog.main.dosboxversion.toggledefault").image(ImageService.IMG_HOME).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doToggleDefaultVersion();
			}
		}).build();

		dbversionTable_.setMenu(menu);
		dbversionTable_.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent event) {
				if (event.keyCode == SWT.DEL || (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'r'))) {
					doRemoveDosboxVersion();
				} else if (event.keyCode == SWT.INSERT || (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'n'))) {
					doAddDosboxVersion();
				} else if (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'm')) {
					doToggleDefaultVersion();
				}
			}
		});
		dbversionTable_.addTraverseListener(event -> {
			if ((event.stateMask == SWT.MOD1) && (event.detail == SWT.TRAVERSE_RETURN)) {
				doEditDosboxVersion();
			} else if (event.detail == SWT.TRAVERSE_RETURN) {
				doRunDosbox();
			}
		});
		dbversionTable_.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent event) {
				doRunDosbox();
			}
		});
	}

	private void addDBColumn(String title, int colIndex) {
		String width = "column2_" + (colIndex + 1) + "width";
		createTableColumn(dbversionTable_, settings_.getIntValue("gui", width), title).addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent event) {
				settings_.setIntValue("gui", width, ((TableColumn)event.widget).getWidth());
			}
		});
	}

	private void createTemplatesTab(CTabFolder tabFolder) {
		SelectionAdapter addTemplAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doAddTemplate();
			}
		};
		SelectionAdapter editTemplAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doEditTemplate();
			}
		};
		SelectionAdapter removeTemplAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doRemoveTemplate();
			}
		};
		SelectionAdapter runTemplAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doRunTemplate();
			}
		};

		Composite composite = Composite_.on(tabFolder).innerLayout(1).tab("dialog.main.templates").build();
		ToolBar toolBar = createToolBar(createRow(composite));
		createImageToolItem(toolBar, "dialog.main.addtemplate", ImageService.IMG_TB_NEW, addTemplAdapter);
		createImageToolItem(toolBar, "dialog.main.edittemplate", ImageService.IMG_TB_EDIT, editTemplAdapter);
		createImageToolItem(toolBar, "dialog.main.removetemplate", ImageService.IMG_TB_DELETE, removeTemplAdapter);
		createImageToolItem(toolBar, "dialog.main.runtemplate", ImageService.IMG_TB_RUN, runTemplAdapter);
		templateTable_ = Table_.on(composite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).header().build();
		addTemplateColumn("dialog.main.templates.column.title", 0);
		addTemplateColumn("dialog.main.templates.column.default", 1);
		addTemplateColumn("dialog.main.templates.column.id", 2);
		addTemplateColumn("dialog.main.generic.column.created", 3);
		addTemplateColumn("dialog.main.generic.column.lastmodify", 4);
		addTemplateColumn("dialog.main.generic.column.lastrun", 5);
		addTemplateColumn("dialog.main.generic.column.runs", 6);

		Menu menu = new Menu(templateTable_);
		MenuItem_.on(menu).key("dialog.main.template.run").image(ImageService.IMG_RUN).listen(runTemplAdapter).build();
		MenuItem_.on(menu).build();
		Menu viewTemplateSubMenu = createMenu(menu, "dialog.main.profile.view", ImageService.IMG_ZOOM);
		MenuItem_.on(viewTemplateSubMenu).key("dialog.main.template.view.conf").image(ImageService.IMG_PROFILE_CONF).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (templateTable_.getSelectionIndex() != -1) {
					Template template = templatesList_.get(templateTable_.getSelectionIndex());
					SystemUtils.openForEditing(template.getConfigurationCanonicalFile(), getParent());
				}
			}
		}).build();
		MenuItem_.on(menu).build();
		MenuItem_.on(menu).key("dialog.main.template.add").image(ImageService.IMG_NEW).listen(addTemplAdapter).build();
		MenuItem_.on(menu).key("dialog.main.template.edit").image(ImageService.IMG_EDIT).listen(editTemplAdapter).build();
		MenuItem_.on(menu).key("dialog.main.template.duplicate").image(ImageService.IMG_DUPLICATE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doDuplicateTemplate();
			}
		}).build();
		MenuItem_.on(menu).key("dialog.main.template.remove").image(ImageService.IMG_DELETE).listen(removeTemplAdapter).build();
		MenuItem_.on(menu).build();
		MenuItem_.on(menu).key("dialog.main.template.toggledefault").image(ImageService.IMG_HOME).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doToggleDefaultTemplate();
			}
		}).build();

		templateTable_.setMenu(menu);
		templateTable_.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent event) {
				if (event.keyCode == SWT.DEL || (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'r'))) {
					doRemoveTemplate();
				} else if (event.keyCode == SWT.INSERT || (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'n'))) {
					doAddTemplate();
				} else if (event.stateMask == SWT.MOD1 && (Character.toLowerCase(event.keyCode) == 'm')) {
					doToggleDefaultTemplate();
				}
			}
		});
		templateTable_.addTraverseListener(event -> {
			if ((event.stateMask == SWT.MOD1) && (event.detail == SWT.TRAVERSE_RETURN)) {
				doEditTemplate();
			} else if (event.detail == SWT.TRAVERSE_RETURN) {
				doRunTemplate();
			}
		});
		templateTable_.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent event) {
				doRunTemplate();
			}
		});
	}

	private void addTemplateColumn(String title, int colIndex) {
		String width = "column3_" + (colIndex + 1) + "width";
		createTableColumn(templateTable_, settings_.getIntValue("gui", width), title).addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent event) {
				settings_.setIntValue("gui", width, ((TableColumn)event.widget).getWidth());
			}
		});
	}

	private void addProfileToTable(Profile prof) {
		setProfileTableItem(profileTable_.new ProfilesListItem(profileTable_), prof);
	}

	private void addDosboxVersionToTable(DosboxVersion dbversion) {
		setDosboxTableItem(new TableItem(dbversionTable_, SWT.BORDER), dbversion);
	}

	private void addTemplateToTable(Template template) {
		setTemplateTableItem(new TableItem(templateTable_, SWT.BORDER), template);
	}

	private void setProfileTableItem(ProfilesListItem newItemTableItem, Profile prof) {
		for (int i = 0; i < columnIds_.length; i++) {
			final int colId = columnIds_[i];
			final String value;
			switch (colId) {
				case 0:
					value = prof.getTitle();
					break;
				case 1:
					value = prof.hasSetup() ? text_.get("general.yes"): text_.get("general.no");
					break;
				case 2:
					value = prof.getDeveloper();
					break;
				case 3:
					value = prof.getPublisher();
					break;
				case 4:
					value = prof.getGenre();
					break;
				case 5:
					value = prof.getYear();
					break;
				case 6:
					value = prof.getStatus();
					break;
				case 7:
					value = Boolean.TRUE.equals(prof.isFavorite()) ? text_.get("general.yes"): text_.get("general.no");
					break;
				case 8:
					value = String.valueOf(prof.getId());
					break;
				case 9:
					value = String.valueOf(prof.getDosboxVersion().getId());
					break;
				case 10:
				case 11:
				case 12:
				case 13:
				case 14:
				case 15:
				case 16:
				case 17:
					value = prof.getCustomStrings()[colId - Constants.RO_COLUMN_NAMES];
					break;
				case 18:
					value = prof.getCustomInts()[0] + " %";
					break;
				case 19:
					value = String.valueOf(prof.getCustomInts()[1]);
					break;
				case 21:
					value = prof.getDosboxVersion().getTitle();
					break;
				case 22:
					value = text_.toString(prof.getStats().getCreated(), DateFormat.SHORT);
					break;
				case 23:
					value = text_.toString(prof.getStats().getModified(), DateFormat.SHORT);
					break;
				case 24:
					value = text_.toString(prof.getStats().getLastRun(), DateFormat.SHORT);
					break;
				case 25:
					value = text_.toString(prof.getProfileStats().getLastSetup(), DateFormat.SHORT);
					break;
				case 26:
					value = String.valueOf(prof.getStats().getRuns());
					break;
				case 27:
					value = String.valueOf(prof.getProfileStats().getSetups());
					break;
				case 28:
				case 29:
				case 30:
				case 31:
					value = prof.getCustomStrings()[colId - Constants.RO_COLUMN_NAMES - Constants.EDIT_COLUMN_NAMES_1];
					break;
				case 32:
					value = org.dbgl.model.ProfileStats.formatPlayTime(prof.getProfileStats().getPlayTime());
					break;
				default:
					value = StringUtils.EMPTY;
			}
			if (colId != 20) {
				newItemTableItem.setText(i, colId, columnNames_[colId], value);
			}
		}
		newItemTableItem.setData(new ThumbInfo(prof.getCapturesString()));
		if (markersOn()) {
			setMarker(newItemTableItem, prof);
			if (org.dbgl.util.ProfileWarnings.known(prof) == null)
				scheduleProfileWarnings();
		} else {
			newItemTableItem.setWarning(false);
		}
	}

	private volatile int warningsGeneration_;
	private volatile int warningsRetries_;
	private boolean warningsScheduled_;

	/**
	 * Check the profiles in the list in the background, and mark those with invalid values or missing folders or files with a
	 * warning sign. A profile is only checked again when its configuration file or that of its DOSBox version changed.
	 */
	private void scheduleProfileWarnings() {
		if (warningsScheduled_)
			return;
		warningsScheduled_ = true;
		display_.asyncExec(() -> {
			warningsScheduled_ = false;
			if (shell_.isDisposed() || (profilesList_ == null))
				return;
			int generation = ++warningsGeneration_;
			List<Profile> profiles = new ArrayList<>(profilesList_);
			Thread checker = new Thread(() -> {
				boolean unchecked = false;
				for (Profile prof: profiles) {
					if ((generation != warningsGeneration_) || display_.isDisposed())
						return;
					// check() answers at once when the profile was checked meanwhile (e.g. by the check of all profiles at startup);
					// such a profile must get its warning sign too
					org.dbgl.util.ProfileWarnings.check(prof);
					showWarningSign(prof); // also to take a sign away the list still showed from the last check
					unchecked |= org.dbgl.util.ProfileWarnings.known(prof) == null;
				}
				// a check that did not get through (e.g. a DOSBox configuration read again at that moment): tried again a little later
				if (unchecked && (generation == warningsGeneration_) && !display_.isDisposed() && (warningsRetries_++ < 3))
					display_.asyncExec(() -> display_.timerExec(10000, () -> {
						if (!shell_.isDisposed())
							scheduleProfileWarnings();
					}));
				else if (!unchecked)
					warningsRetries_ = 0;
			}, "DBGL profile warnings");
			checker.setDaemon(true);
			checker.setPriority(Thread.MIN_PRIORITY);
			checker.start();
		});
	}

	/** Show the warning sign of a profile in the list, if it is there. May be called from any thread. */
	private final java.util.Set<Profile> pendingWarningSigns_ = new java.util.LinkedHashSet<>();
	private boolean warningSignsScheduled_;

	/**
	 * Show the warning sign of a profile; called from the background checks, one profile at a time. The signs are collected and
	 * shown together a moment later: in the tile and box views each sign repainted the whole view, with 1000 profiles some 600
	 * times in a row at startup.
	 */
	private void showWarningSign(Profile prof) {
		if (display_.isDisposed())
			return;
		synchronized (pendingWarningSigns_) {
			pendingWarningSigns_.add(prof);
			if (warningSignsScheduled_)
				return;
			warningSignsScheduled_ = true;
		}
		display_.asyncExec(() -> display_.timerExec(150, this::applyWarningSigns));
	}

	private void applyWarningSigns() {
		List<Profile> profiles;
		synchronized (pendingWarningSigns_) {
			profiles = new ArrayList<>(pendingWarningSigns_);
			pendingWarningSigns_.clear();
			warningSignsScheduled_ = false;
		}
		if (shell_.isDisposed() || (profilesList_ == null) || !markersOn())
			return;
		profileTable_.setRedraw(false);
		try {
			for (Profile prof: profiles) {
				int index = BaseRepository.indexOf(profilesList_, prof);
				if ((index >= 0) && (index < profileTable_.getItemCount()))
					setMarker(profileTable_.getItem(index), prof);
			}
		} finally {
			profileTable_.setRedraw(true);
		}
	}

	private void setDosboxTableItem(TableItem newItemTableItem, DosboxVersion dbversion) {
		newItemTableItem.setText(0, dbversion.getTitle());
		newItemTableItem.setText(1, dbversion.getFamily());
		newItemTableItem.setText(2, dbversion.getVersion());
		newItemTableItem.setText(3, dbversion.getPath().getPath());
		newItemTableItem.setText(4, dbversion.isDefault() ? text_.get("general.yes"): text_.get("general.no"));
		newItemTableItem.setText(5, String.valueOf(dbversion.getId()));
		newItemTableItem.setText(6, text_.toString(dbversion.getStats().getCreated(), DateFormat.SHORT));
		newItemTableItem.setText(7, text_.toString(dbversion.getStats().getModified(), DateFormat.SHORT));
		newItemTableItem.setText(8, text_.toString(dbversion.getStats().getLastRun(), DateFormat.SHORT));
		newItemTableItem.setText(9, String.valueOf(dbversion.getStats().getRuns()));
	}

	private void setTemplateTableItem(TableItem newItemTableItem, Template template) {
		newItemTableItem.setText(0, template.getTitle());
		newItemTableItem.setText(1, template.isDefault() ? text_.get("general.yes"): text_.get("general.no"));
		newItemTableItem.setText(2, String.valueOf(template.getId()));
		newItemTableItem.setText(3, text_.toString(template.getStats().getCreated(), DateFormat.SHORT));
		newItemTableItem.setText(4, text_.toString(template.getStats().getModified(), DateFormat.SHORT));
		newItemTableItem.setText(5, text_.toString(template.getStats().getLastRun(), DateFormat.SHORT));
		newItemTableItem.setText(6, String.valueOf(template.getStats().getRuns()));
	}

	private void updateProfilesList(Set<Integer> profileIds) {
		try {
			profilesList_ = listProfiles();
		} catch (SQLException e) {
			Mess_.on(shell_).exception(e).warning();
		}
		profileTable_.setRedraw(false);
		profileTable_.removeAll();
		for (Profile prof: profilesList_) {
			addProfileToTable(prof);
		}
		profileTable_.setSelection(getIndicesByIds(profileIds));
		profileTable_.setRedraw(true);
		profileTable_.setFocus();
	}

	private void updateDosboxVersionList(DosboxVersion dbversion) {
		try {
			dbversionsList_ = new DosboxVersionRepository().listAll();
		} catch (SQLException e) {
			Mess_.on(shell_).exception(e).warning();
		}
		dbversionTable_.removeAll();
		for (DosboxVersion version: dbversionsList_) {
			addDosboxVersionToTable(version);
		}
		dbversionTable_.setSelection(BaseRepository.indexOf(dbversionsList_, dbversion));
		dbversionTable_.setFocus();
	}

	private void updateTemplateList(Template template) {
		try {
			templatesList_ = new TemplateRepository().listAll(dbversionsList_);
		} catch (SQLException e) {
			Mess_.on(shell_).exception(e).warning();
		}
		templateTable_.removeAll();
		for (Template temp: templatesList_) {
			addTemplateToTable(temp);
		}
		templateTable_.setSelection(BaseRepository.indexOf(templatesList_, template));
		templateTable_.setFocus();
	}

	private void doAddProfile(String filename, String[] fileNames) {
		if (requireDefaultDBVersion() == null)
			return;

		if (filename == null || FilesUtils.isBooterImage(filename) || FilesUtils.isExecutable(filename) || FilesUtils.isConfFile(filename)) {
			Profile profile = new EditSingleProfileDialog(shell_, null, filename, false).open();
			if (profile != null)
				updateWithAddedProfile(profile);
		} else if (FilesUtils.isArchive(filename)) {
			if (Stream.of(fileNames).allMatch(FilesUtils::isArchive)) {
				Boolean updateCustomFields = new ImportDialog(shell_, dbversionsList_, new File(filename), fileNames).open();
				if (updateCustomFields != null) {
					if (updateCustomFields) {
						rebuildProfilesTable();
					} else {
						updateProfilesList(getSelectedProfileIds());
					}
					displayProfileInformation(false);
				}
			} else {
				Mess_.on(shell_).key("dialog.import.error.mixedfiles").warning();
			}
		} else if (FilesUtils.isExistingDirectory(new File(filename))) {
			doImportScanFolder(filename);
		} else {
			Mess_.on(shell_).key("general.error.cannotimportunknownfile").warning();
		}
	}

	public void addProfile(String file) {
		display_.syncExec(() -> doAddProfile(file, new String[] {file}));
	}

	private void doAddDosboxVersion() {
		DosboxVersion dbversion = new EditDosboxVersionDialog(shell_, BaseRepository.findDefault(dbversionsList_) == null, null).open();
		if (dbversion != null)
			updateDosboxVersionList(dbversion);
	}

	private void doAddTemplate() {
		if (requireDefaultDBVersion() == null)
			return;

		Template template = new EditTemplateDialog(shell_, null).open();
		if (template != null)
			updateTemplateList(template);
	}

	private void doAddFilter() {
		Filter filter = new EditFilterDialog(shell_, null, profileTable_.getSelectionCount() > 1 ? getSelectedProfileIds(): null, columnNames_).open();
		if (filter != null) {
			filtersList_.add(filter);
			addFilterTab(filter).setControl(profileTable_.getControl());
			filterFolder_.setSelection(filterFolder_.getItemCount() - 1);
			updateProfilesAfterTabAction();
		}
	}

	/** the filter clause of the built-in tab with the profiles that have a warning sign; not SQL, handled by listProfiles */
	private static final String PROBLEMS_FILTER = "/* dbgl: profiles with problems */";

	/** 'All profiles' first, the user's filters, and 'With problems' last (when the warning signs are turned on) */
	private List<Filter> allFilters(List<Filter> userFilters) {
		List<Filter> result = new ArrayList<>(userFilters);
		result.add(0, new Filter(text_.get("dialog.main.allprofiles"), null));
		if (settings_.getBooleanValue("gui", "profilewarnings"))
			result.add(new Filter(text_.get("dialog.main.problemprofiles"), PROBLEMS_FILTER));
		return result;
	}

	private static boolean isBuiltInFilter(Filter filter) {
		return (filter.getFilter() == null) || PROBLEMS_FILTER.equals(filter.getFilter());
	}

	/** the profiles of the current filter tab; for 'With problems' every profile is checked (once, the result is kept) */
	private List<Profile> listProfiles() throws SQLException {
		boolean problems = PROBLEMS_FILTER.equals(filterClause_);
		List<Profile> list = new ProfileRepository().list(orderingVector_.toClause(), problems ? null: filterClause_, dbversionsList_);
		if (!problems)
			return list;
		boolean busy = (shell_ != null) && !shell_.isDisposed();
		if (busy)
			shell_.setCursor(display_.getSystemCursor(SWT.CURSOR_WAIT));
		try {
			return list.stream().filter(p -> !org.dbgl.util.ProfileWarnings.check(p).isEmpty()).collect(Collectors.toCollection(ArrayList::new));
		} finally {
			if (busy)
				shell_.setCursor(null);
		}
	}

	/** jump from the warning sign of a profile to its first problem */
	private void doShowProfileProblem(int index) {
		if ((index < 0) || (index >= profilesList_.size()))
			return;
		List<String> problems = org.dbgl.util.ProfileWarnings.lastKnown(profilesList_.get(index));
		String first = (problems == null) || problems.isEmpty() ? null: problems.get(0);
		java.util.regex.Matcher m = first == null ? null: java.util.regex.Pattern.compile("^\\[[^\\]]+\\] ([^=]+)=").matcher(first);
		if ((m != null) && m.find())
			doEditProfileSetting(profilesList_.get(index).getId(), m.group(1).trim());
		else
			doFixProfileProblems();
	}

	/** the tooltip of the warning sign: what is wrong with the profile */
	private String profileProblemsText(int index) {
		if ((index < 0) || (index >= profilesList_.size()))
			return null;
		List<String> problems = org.dbgl.util.ProfileWarnings.lastKnown(profilesList_.get(index)); // as the sign shows it
		boolean warnings = settings_.getBooleanValue("gui", "profilewarnings");
		if (!warnings || (problems == null) || problems.isEmpty()) { // a note: the hints
			List<String> hints = org.dbgl.util.ProfileWarnings.lastKnownHints(profilesList_.get(index));
			if (!settings_.getBooleanValue("gui", "profilehints") || (hints == null) || hints.isEmpty())
				return null;
			StringBuilder sb = new StringBuilder(text_.get("dialog.main.profilehints"));
			hints.stream().limit(10).forEach(p -> sb.append("\n  ").append(p));
			if (hints.size() > 10)
				sb.append("\n  ...");
			return sb.append("\n").append(text_.get("dialog.main.profilehints.click")).toString();
		}
		StringBuilder sb = new StringBuilder(text_.get("dialog.main.profileproblems"));
		problems.stream().limit(10).forEach(p -> sb.append("\n  ").append(p));
		if (problems.size() > 10)
			sb.append("\n  ...");
		return sb.append("\n").append(text_.get("dialog.main.profileproblems.click")).toString();
	}

	/** bring back the profiles of the last change of many profiles at once */
	private void doUndoBulkChange() {
		org.dbgl.util.BulkUndo.Info info = org.dbgl.util.BulkUndo.last();
		if (info == null)
			return;
		// what goes back, profile by profile, in colors as in the preview of 'Check profiles'; profiles can be left out
		java.util.Set<Integer> restored = new BulkUndoDialog(shell_, info, dbversionsList_).open();
		if (restored == null)
			return;
		updateProfilesList(new HashSet<>(restored));
		displayProfileInformation(false);
		Mess_.on(shell_).key("dialog.main.notice.undobulkdone", new Object[] {restored.size()}).display();
	}

	/** the repair of 'Check profiles', for the selected profiles only */
	private void doFixProfileProblems() {
		List<Profile> selected = getSelectedProfiles();
		if (selected.isEmpty())
			return;
		new CheckProfilesDialog(shell_, selected).open();
		selected.forEach(p -> org.dbgl.util.ProfileWarnings.forget(p.getId()));
		updateProfilesList(getSelectedProfileIds());
		displayProfileInformation(false);
	}

	private CTabItem addFilterTab(Filter filter) {
		CTabItem item = new CTabItem(filterFolder_, isBuiltInFilter(filter) ? SWT.NONE: SWT.CLOSE);
		item.setText("   " + filter.getTitle() + "   ");
		item.setData(filter.getId());
		return item;
	}

	/** link manuals, walkthroughs, maps and soundtracks found in the game folders of the selected profiles (see ExtrasDialog) */
	/** covers for the selected profiles (or, with one or none selected, all in the list) that have no image yet */
	private void doCoverBatch() {
		boolean selection = profileTable_.getSelectionCount() > 1;
		int[] indices = selection ? profileTable_.getSelectionIndices(): java.util.stream.IntStream.range(0, profilesList_.size()).toArray();
		List<Profile> without = new ArrayList<>();
		for (int i: indices) {
			ThumbInfo info = (ThumbInfo)profileTable_.getItem(i).getData();
			if ((info == null) || (info.getAllThumbs().length == 0))
				without.add(profilesList_.get(i));
		}
		if (without.isEmpty()) {
			Mess_.on(shell_).key("dialog.coverbatch.none").display();
			return;
		}
		Integer saved = new CoverBatchDialog(shell_, without).open();
		if ((saved != null) && (saved > 0)) {
			for (int i: indices) {
				ThumbInfo info = (ThumbInfo)profileTable_.getItem(i).getData();
				if (info != null)
					info.resetCachedInfo();
			}
			displayProfileInformation(true);
			Mess_.on(shell_).key("dialog.coverbatch.saved", new Object[] {saved}).display();
		}
	}

	private void doFindExtras() {
		if (profileTable_.getSelectionIndex() == -1)
			return;
		List<Profile> loadedProfiles = new ProfileLoader(shell_, getSelectedProfiles()).open();
		if (loadedProfiles == null)
			return;
		java.util.Map<Profile, List<org.dbgl.util.ExtrasFinder.Extra>> found;
		try { // many games or large folders take a moment: a bar shows how far it is, Cancel stops waiting
			java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
			found = org.dbgl.gui.controls.BusyTask.run(shell_, text_.get("dialog.extras.searching"), done, loadedProfiles.size(), () -> ExtrasDialog.search(loadedProfiles, done));
		} catch (org.dbgl.gui.controls.BusyTask.Cancelled e) {
			return;
		} catch (Exception e) {
			Mess_.on(shell_).exception(e).warning();
			return;
		}
		Integer added = new ExtrasDialog(shell_, loadedProfiles, found).open();
		if (added != null) {
			updateProfilesList(getSelectedProfileIds());
			displayProfileInformation(false);
			Mess_.on(shell_).key("dialog.extras.done", new Object[] {added}).display();
		}
	}

	/** all profiles that start the same game (see DuplicateProfiles), with the offer to merge them */
	private void doFindDuplicates() {
		List<org.dbgl.util.DuplicateProfiles.Group> groups;
		try {
			List<Profile> all = new ProfileRepository().list("", "", dbversionsList_);
			java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
			groups = org.dbgl.gui.controls.BusyTask.run(shell_, text_.get("dialog.duplicates.searching"), done, all.size(),
				() -> org.dbgl.util.DuplicateProfiles.find(all, p -> done.incrementAndGet()));
		} catch (org.dbgl.gui.controls.BusyTask.Cancelled e) {
			return;
		} catch (Exception e) {
			Mess_.on(shell_).exception(e).warning();
			return;
		}
		if (groups.isEmpty()) {
			Mess_.on(shell_).key("dialog.duplicates.none").display();
			return;
		}
		Integer removed = new DuplicatesDialog(shell_, groups).open();
		if ((removed != null) && (removed > 0)) {
			updateProfilesList(getSelectedProfileIds());
			displayProfileInformation(false);
			Mess_.on(shell_).key("dialog.duplicates.done", new Object[] {removed}).display();
		}
	}

	/** GOG and Steam DOS games in a folder: profiles from the DOSBox configuration they come with (see StoreGameImport) */
	private void doImportStoreGames() {
		DosboxVersion defaultVersion = requireDefaultDBVersion();
		if (defaultVersion == null)
			return;
		DirectoryDialog dialog = new DirectoryDialog(shell_);
		dialog.setMessage(text_.get("dialog.storeimport.choose2"));
		String dir = dialog.open();
		if (dir == null)
			return;
		List<org.dbgl.util.StoreGameImport.Found> found;
		java.util.Set<String> existing;
		try {
			List<Profile> all = new ProfileRepository().list("", "", dbversionsList_);
			Object[] result = org.dbgl.gui.controls.BusyTask.run(shell_, text_.get("dialog.storeimport.searching"),
				() -> new Object[] {org.dbgl.util.StoreGameImport.find(new File(dir)), StoreImportDialog.existingKeys(all)});
			@SuppressWarnings("unchecked")
			List<org.dbgl.util.StoreGameImport.Found> f = (List<org.dbgl.util.StoreGameImport.Found>)result[0];
			@SuppressWarnings("unchecked")
			java.util.Set<String> e = (java.util.Set<String>)result[1];
			found = f;
			existing = e;
		} catch (org.dbgl.gui.controls.BusyTask.Cancelled e) {
			return;
		} catch (Exception e) {
			Mess_.on(shell_).exception(e).warning();
			return;
		}
		if (found.isEmpty()) {
			Mess_.on(shell_).key("dialog.storeimport.none", new Object[] {dir}).display();
			return;
		}
		Integer added = new StoreImportDialog(shell_, found, dbversionsList_, defaultVersion, existing).open();
		if ((added != null) && (added > 0)) {
			updateProfilesList(getSelectedProfileIds());
			displayProfileInformation(false);
			Mess_.on(shell_).key("dialog.storeimport.done", new Object[] {added}).display();
		}
	}

	/** replace a setting value in all selected profiles at once, with a preview (see ReplaceValuesDialog) */
	private void doReplaceValues() {
		if (profileTable_.getSelectionIndex() == -1)
			return;
		List<Profile> loadedProfiles = new ProfileLoader(shell_, getSelectedProfiles()).open();
		if (loadedProfiles == null)
			return;
		Integer changed = new ReplaceValuesDialog(shell_, loadedProfiles).open();
		if (changed != null) {
			updateProfilesList(getSelectedProfileIds());
			displayProfileInformation(false);
			Mess_.on(shell_).key("dialog.replacevalues.done", new Object[] {changed}).display();
		}
	}

	private void doEditProfile(boolean focusTitle) {
		int index = profileTable_.getSelectionIndex();
		if (index != -1) {
			if (profileTable_.getSelectionCount() > 1) {
				if (requireDefaultDBVersion() == null)
					return;
				
				List<Profile> loadedProfiles = new ProfileLoader(shell_, getSelectedProfiles()).open();
				if (loadedProfiles != null) {
					if (loadedProfiles.size() > 1) // a single profile: its dialog asks itself
						for (Profile p: loadedProfiles)
							askUnknownAutoexecLines(p, true);
					Object results = (loadedProfiles.size() > 1) ? new EditMultiProfileDialog(shell_, loadedProfiles).open()
							: new EditSingleProfileDialog(shell_, loadedProfiles.get(0), null, focusTitle).open();
					if (results != null) {
						updateProfilesList(getSelectedProfileIds());
						displayProfileInformation(false);
					}
				}
			} else {
				Profile profile = new EditSingleProfileDialog(shell_, profilesList_.get(index), null, focusTitle).open();
				if (profile != null) {
					updateProfileListAfterEdit(index, profile);
					displayProfileInformation(false);
				}
			}
		}
	}

	private void doEditDosboxVersion() {
		int index = dbversionTable_.getSelectionIndex();
		if (index != -1) {
			DosboxVersion dbversion = new EditDosboxVersionDialog(shell_, false, dbversionsList_.get(index)).open();
			if (dbversion != null) {
				updateDosboxVersionList(dbversion);
				updateProfilesList(getSelectedProfileIds());
				try {
					templatesList_ = new TemplateRepository().listAll(dbversionsList_);
				} catch (SQLException e) {
					Mess_.on(shell_).exception(e).warning();
				}
			}
		}
	}

	private void doEditTemplate() {
		int index = templateTable_.getSelectionIndex();
		if (index != -1) {
			Template template = new EditTemplateDialog(shell_, templatesList_.get(index)).open();
			if (template != null)
				updateTemplateList(template);
		}
	}

	private void doEditFilter() {
		int index = filterFolder_.getSelectionIndex();
		if ((index > 0) && !isBuiltInFilter(filtersList_.get(index))) {
			Filter filter = new EditFilterDialog(shell_, filtersList_.get(index), null, columnNames_).open();
			if (filter != null) {
				filtersList_.set(index, filter);
				filterFolder_.getSelection().setText("    " + filter.getTitle() + "    ");
				updateProfilesAfterTabAction();
			}
		}
	}

	private void doToggleFavoriteProfile() {
		int index = profileTable_.getSelectionIndex();
		if (index != -1) {
			Profile profile = profilesList_.get(index);
			profile.setFavorite(!profile.isFavorite());
			try {
				String warningsLog = profile.resetAndLoadConfiguration();
				if (StringUtils.isNotBlank(warningsLog))
					Mess_.on(shell_).txt(warningsLog).warning();
				new ProfileRepository().update(profile);
				setProfileTableItem(profileTable_.getItem(index), profile);
			} catch (SQLException | IOException e) {
				profile.setFavorite(!profile.isFavorite());
				Mess_.on(shell_).exception(e).warning();
			}
		}
	}

	private void doToggleDefaultVersion() {
		int index = dbversionTable_.getSelectionIndex();
		if (index != -1) {
			DosboxVersion dbversion = dbversionsList_.get(index);
			dbversion.setDefault(!dbversion.isDefault());
			try {
				new DosboxVersionRepository().update(dbversion);
				updateDosboxVersionList(dbversion);
			} catch (SQLException e) {
				dbversion.setDefault(!dbversion.isDefault());
				Mess_.on(shell_).exception(e).warning();
			}
		}
	}

	private void doToggleDefaultTemplate() {
		int index = templateTable_.getSelectionIndex();
		if (index != -1) {
			Template template = templatesList_.get(index);
			template.setDefault(!template.isDefault());
			try {
				String warningsLog = template.resetAndLoadConfiguration();
				if (StringUtils.isNotBlank(warningsLog))
					Mess_.on(shell_).txt(warningsLog).warning();
				new TemplateRepository().update(template);
				updateTemplateList(template);
			} catch (SQLException | IOException e) {
				template.setDefault(!template.isDefault());
				Mess_.on(shell_).exception(e).warning();
			}
		}
	}

	private void doRemoveProfile() {
		int index = profileTable_.getSelectionIndex();
		if ((index != -1) && (new DeleteProfilesDialog(shell_, getSelectedProfiles()).open() != null)) {
			int[] idxs = profileTable_.getSelectionIndices();
			Arrays.sort(idxs);
			for (int i = idxs.length - 1; i >= 0; i--) {
				profileTable_.remove(idxs[i]);
				profilesList_.remove(idxs[i]);
			}
			if (idxs[0] > 0)
				profileTable_.setSelection(idxs[0] - 1);
			displayProfileInformation(false);
		}
	}

	private void doRemoveDosboxVersion() {
		int index = dbversionTable_.getSelectionIndex();
		if ((index != -1) && Mess_.on(shell_).key("dialog.main.confirm.removedosboxversion").confirm()) {
			try {
				new DosboxVersionRepository().remove(dbversionsList_.get(index));
				dbversionTable_.remove(index);
				dbversionsList_.remove(index);
			} catch (SQLException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		}
	}

	/**
	 * Remove a DOSBox version that no profile or template uses, and delete its folder. Two safety questions: a confirmation with
	 * the folder and its number of files, then typing the folder name. DBGL's own folders, roots and folders shared with other
	 * DOSBox versions are never deleted.
	 */
	private void doRemoveDosboxVersionWithFolder() {
		int index = dbversionTable_.getSelectionIndex();
		if (index == -1)
			return;
		DosboxVersion dbversion = dbversionsList_.get(index);
		File folder = dbversion.getCanonicalPath();
		FileLocationService fls = FileLocationService.getInstance();
		List<File> protectedDirs = List.of(new File("."), fls.getDataDir(), fls.getProfilesDir(), fls.getTemplatesDir(), fls.getDosroot(), fls.getDosboxDir());
		List<File> others = dbversionsList_.stream().filter(v -> v.getId() != dbversion.getId()).map(DosboxVersion::getCanonicalPath).toList();
		org.dbgl.util.DosboxFolderRemoval.Verdict verdict = org.dbgl.util.DosboxFolderRemoval.check(folder, protectedDirs, others);
		if (verdict != org.dbgl.util.DosboxFolderRemoval.Verdict.OK) {
			Mess_.on(shell_).key("dialog.main.error.removefolder." + verdict.name().toLowerCase(), new Object[] {String.valueOf(folder)}).warning();
			return;
		}
		long files = org.dbgl.util.DosboxFolderRemoval.countFiles(folder);
		// first safety question
		if (!Mess_.on(shell_).key("dialog.main.confirm.removewithfolder", new Object[] {dbversion.getTitle(), folder.getPath(), files}).confirm())
			return;
		// second safety question: type the name of the folder
		String typed = new TextInputDialog(shell_, text_.get("dialog.main.dosboxversion.removewithfolder"),
			text_.get("dialog.main.confirm.removewithfolder.type", new Object[] {folder.getName()}), "").open();
		if ((typed == null) || !typed.strip().equals(folder.getName())) {
			Mess_.on(shell_).key("dialog.main.notice.removefoldercancelled").display();
			return;
		}
		try {
			new DosboxVersionRepository().remove(dbversion); // refused while profiles or templates use it: then nothing is deleted
		} catch (SQLException e) {
			Mess_.on(shell_).exception(e).warning();
			return;
		}
		dbversionTable_.remove(index);
		dbversionsList_.remove(index);
		try {
			org.dbgl.util.DosboxFolderRemoval.delete(folder);
			Mess_.on(shell_).key("dialog.main.notice.removefolderdone", new Object[] {dbversion.getTitle(), folder.getPath()}).display();
		} catch (IOException e) {
			Mess_.on(shell_).key("dialog.main.error.removefolder.failed", new Object[] {folder.getPath(), e.toString()}).exception(e).warning();
		}
	}

	private void doRemoveTemplate() {
		int index = templateTable_.getSelectionIndex();
		if ((index != -1) && Mess_.on(shell_).key("dialog.main.confirm.removetemplate").confirm()) {
			Template template = templatesList_.get(index);
			boolean removeConfig = Mess_.on(shell_).key("dialog.main.confirm.removetemplateconf", template.getConfigurationFile().getPath()).confirm();
			try {
				new TemplateRepository().remove(template, removeConfig);
				templateTable_.remove(index);
				templatesList_.remove(index);
			} catch (SQLException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		}
	}

	private void doDuplicateProfile() {
		int index = profileTable_.getSelectionIndex();
		if (index != -1) {
			try {
				Profile profile = profilesList_.get(index);
				String warnings = profile.resetAndLoadConfiguration();
				if (StringUtils.isNotEmpty(warnings))
					Mess_.on(shell_).txt(warnings).warning();
				Profile duplicate = new ProfileRepository().duplicate(profile);
				updateWithAddedProfile(duplicate);
			} catch (SQLException | IOException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		}
	}

	private void doDuplicateTemplate() {
		int index = templateTable_.getSelectionIndex();
		if (index != -1) {
			try {
				Template template = templatesList_.get(index);
				String warnings = template.resetAndLoadConfiguration();
				if (StringUtils.isNotEmpty(warnings))
					Mess_.on(shell_).txt(warnings).warning();
				Template duplicate = new TemplateRepository().duplicate(template);
				updateTemplateList(duplicate);
			} catch (SQLException | IOException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		}
	}

	private void doRunProfile(ProfileRunMode mode, boolean prepareOnly) {
		int index = profileTable_.getSelectionIndex();
		if (index != -1) {
			Profile prof = profilesList_.get(index);
			if (mode != ProfileRunMode.SETUP || prof.hasSetup()) {
				try {
					String warningsLog = prof.resetAndLoadConfiguration();
					if (StringUtils.isNotEmpty(warningsLog))
						Mess_.on(shell_).txt(warningsLog).warning();
					ExecuteUtils.doRunProfile(mode, prof, prepareOnly, display_);
					if (mode == ProfileRunMode.NORMAL) {
						prof = new ProfileRepository().registerRun(prof);
					} else if (mode == ProfileRunMode.SETUP) {
						prof = new ProfileRepository().registerSetup(prof);
					}
					updateProfileListAfterEdit(index, prof);
				} catch (IOException | SQLException e) {
					Mess_.on(shell_).exception(e).warning();
				}
			}
		}
	}

	private void doRunDosbox() {
		int index = dbversionTable_.getSelectionIndex();
		if (index != -1) {
			DosboxVersion dbversion = dbversionsList_.get(index);
			try {
				ExecuteUtils.doRunDosbox(dbversion);
				new DosboxVersionRepository().registerRun(dbversion);
				updateDosboxVersionList(dbversion);
			} catch (IOException | SQLException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		}
	}

	private void doRunTemplate() {
		int index = templateTable_.getSelectionIndex();
		if (index != -1) {
			Template template = templatesList_.get(index);
			try {
				String warningsLog = template.resetAndLoadConfiguration();
				if (StringUtils.isNotEmpty(warningsLog))
					Mess_.on(shell_).txt(warningsLog).warning();
				ExecuteUtils.doRunTemplate(template, display_);
				new TemplateRepository().registerRun(template);
				updateTemplateList(template);
			} catch (IOException | SQLException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		}
	}

	private DosboxVersion requireDefaultDBVersion() {
		DosboxVersion dbv = BaseRepository.findDefault(dbversionsList_);
		if (dbv == null)
			Mess_.on(shell_).key("dialog.main.required.defaultdosboxversion").display();
		return dbv;
	}

	private void doOpenSettingsDialog() {
		if (Boolean.TRUE.equals(new SettingsDialog(shell_, columnNames_).open())) {
			result_ = true;
			shell_.close();
			return;
		}
		Font f = notesField_.getFont();
		notesField_.setFont(stringToFont(display_, settings_.getValues("gui", "notesfont"), notesField_.getFont()));
		f.dispose();
		refreshMarkers(); // warning signs and notes turned on or off: at once, not only after a restart
	}

	/** the warning signs and notes of all profiles in the list, as the settings say now */
	private void refreshMarkers() {
		if ((profilesList_ == null) || (profileTable_ == null))
			return;
		profileTable_.setRedraw(false);
		try {
			boolean unknown = false;
			for (int i = 0; (i < profilesList_.size()) && (i < profileTable_.getItemCount()); i++) {
				if (markersOn()) {
					setMarker(profileTable_.getItem(i), profilesList_.get(i));
					unknown |= org.dbgl.util.ProfileWarnings.known(profilesList_.get(i)) == null;
				} else {
					profileTable_.getItem(i).setWarning(false);
				}
			}
			if (unknown)
				scheduleProfileWarnings();
		} finally {
			profileTable_.setRedraw(true);
		}
	}

	private void doImportProfiles(boolean defaultToGamepacks) {
		List<String> names = new ArrayList<>(Arrays.asList(text_.get("filetype.conf"), text_.get("filetype.exe") + ", " + text_.get("filetype.booterimage"), FilesUtils.ALL_FILTER));
		List<String> extensions = new ArrayList<>(Arrays.asList(FilesUtils.CNF_FILTER, FilesUtils.EXE_FILTER + ";" + FilesUtils.BTR_FILTER, FilesUtils.ALL_FILTER));
		if (defaultToGamepacks) {
			names.add(0, text_.get("filetype.gamepack"));
			extensions.add(0, FilesUtils.ARC_FILTER);
		}
		FileDialog dialog = new FileDialog(shell_, defaultToGamepacks ? SWT.OPEN | SWT.MULTI: SWT.OPEN);
		dialog.setFilterNames(names.toArray(new String[0]));
		dialog.setFilterExtensions(extensions.toArray(new String[0]));
		String result = dialog.open();
		if (result != null) {
			String[] fileNames = dialog.getFileNames();
			if (fileNames != null && fileNames.length > 0) {
				fileNames = Stream.of(fileNames).map(x -> new File(dialog.getFilterPath(), x).getPath()).toArray(String[]::new);
			} else {
				fileNames = new String[] {result};
			}
			doAddProfile(result, fileNames);
		}
	}

	private void importScanFolder() {
		DirectoryDialog dialog = new DirectoryDialog(shell_);
		dialog.setFilterPath(FileLocationService.getInstance().getDosroot().getPath());
		String result = dialog.open();
		if (result != null) {
			doImportScanFolder(result);
		}
	}

	private void doImportScanFolder(String dir) {
		if (requireDefaultDBVersion() == null)
			return;

		List<Profile> loadedProfiles = new ProfileLoader(shell_, profilesList_).open();
		if ((loadedProfiles != null) && (new ImportDirDialog(shell_, new File(dir), loadedProfiles).open() != null)) {
			updateProfilesList(getSelectedProfileIds());
			displayProfileInformation(false);
		}
	}

	private void doMigrate() {
		Mess_.on(shell_).key("dialog.main.notice.premigration").display();
		String from = new MigrateDialog(shell_).open();
		if (from != null) {
			updateProfilesList(getSelectedProfileIds());
			displayProfileInformation(false);
			Mess_.on(shell_).key("dialog.main.notice.postmigration", new Object[] {from, FileLocationService.getInstance().getDosroot()}).display();
		}
	}

	private void doLocateDosbox(boolean interactive) {
		SearchResult result = FileLocationService.getInstance().findDosbox();
		if (result.result_ == ResultType.NOTFOUND) {
			Mess_.on(shell_).key("dialog.locatedosbox.notice.notfound").warning();
			return;
		}

		if (result.result_ == ResultType.COMPLETE && !interactive) {
			try {
				DosboxVersion newDbversion = new DosboxVersionRepository().add(result.dosbox_);
				updateDosboxVersionList(newDbversion);
				Mess_.on(null).key("dialog.locatedosbox.notice.foundandadded").display();
			} catch (SQLException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		} else {
			DosboxVersion dbversion = new EditDosboxVersionDialog(shell_, BaseRepository.findDefault(dbversionsList_) == null, result.dosbox_).open();
			if (dbversion != null)
				updateDosboxVersionList(dbversion);
		}
	}

	private void doImportDefaultTemplates(boolean interactive) {
		if (!interactive || Mess_.on(shell_).key("dialog.importdefaulttemplates.confirm.start").confirm()) {
			if (requireDefaultDBVersion() == null)
				return;

			try {
				List<Template> importedTemplates = new ArrayList<>();
				String warnings = ImportExportTemplatesService.doImport(importedTemplates);
				if (StringUtils.isNotEmpty(warnings)) {
					Mess_.on(shell_).txt(warnings).warning();
				} else {
					if (interactive)
						Mess_.on(shell_).key("dialog.import.notice.importok").display();
				}
				if (!importedTemplates.isEmpty())
					updateTemplateList(importedTemplates.get(0));
			} catch (XPathExpressionException | SAXException e) {
				Mess_.on(shell_).key("dialog.importdefaulttemplates.error.defaultxmlinvalidformat", e.toString()).exception(e).fatal();
			} catch (Exception e) {
				Mess_.on(shell_).exception(e).txt(e.toString()).fatal();
			}
		}
	}

	public static void openSendToProfileDialog(String file) {
		Shell shell = new Shell();
		shell.setMinimized(true);
		shell.open();
		try {
			List<DosboxVersion> dbversionsList = new DosboxVersionRepository().listAll();
			if (BaseRepository.findDefault(dbversionsList) == null) {
				Mess_.on(shell).key("dialog.main.required.defaultdosboxversion").display();
			} else {
				if (FilesUtils.isGamePackArchiveFile(file))
					new ImportDialog(shell, dbversionsList, new File(file), null).open();
				else
					new EditSingleProfileDialog(shell, null, file, false).open();
			}
		} catch (SQLException e) {
			Mess_.on(shell).exception(e).warning();
		} finally {
			try {
				DatabaseService.getInstance().shutdown();
			} catch (SQLException e) {
				// nothing we can do
			}
		}
	}

	private List<Profile> getSelectedProfiles() {
		return Arrays.stream(profileTable_.getSelectionIndices()).mapToObj(x -> profilesList_.get(x)).toList();
	}

	private Set<Integer> getSelectedProfileIds() {
		return getSelectedProfiles().stream().map(Profile::getId).collect(Collectors.toSet());
	}

	private int[] getIndicesByIds(Set<Integer> profileIds) {
		return IntStream.range(0, profilesList_.size()).filter(x -> profileIds.contains(profilesList_.get(x).getId())).toArray();
	}

	private final SelectionAdapter addProfAdapter = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent event) {
			doAddProfile(null, null);
		}
	};
	private final SelectionAdapter editProfAdapter = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent event) {
			doEditProfile(false);
		}
	};
	private final SelectionAdapter removeProfAdapter = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent event) {
			doRemoveProfile();
		}
	};
	private final SelectionAdapter setupProfAdapter = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent event) {
			doRunProfile(ProfileRunMode.SETUP, false);
		}
	};
	private final SelectionAdapter runProfAdapter = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent event) {
			doRunProfile(ProfileRunMode.NORMAL, false);
		}
	};
}
