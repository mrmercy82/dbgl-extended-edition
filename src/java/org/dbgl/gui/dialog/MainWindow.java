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
import org.dbgl.service.SettingsService;
import org.dbgl.util.DosboxVersionDetector;
import org.dbgl.util.ExecuteUtils;
import org.dbgl.util.ExecuteUtils.ProfileRunMode;
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
import org.eclipse.swt.widgets.Menu;
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
		return text_.get("main.title", new Object[] {Constants.PROGRAM_VERSION});
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

			filtersList_ = new FilterRepository().listAll();
			filtersList_.add(0, new Filter(text_.get("dialog.main.allprofiles"), null));
			int filterIdx = Math.min(settings_.getIntValue("gui", "filtertab"), filtersList_.size() - 1);
			filterClause_ = filtersList_.get(filterIdx).getFilter();

			profilesList_ = profileRepo.list(orderingVector_.toClause(), filterClause_, dbversionsList_);
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
	}

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
				for (int i = 0; i < mismatching.size(); i++)
					list.append(mismatching.get(i).getTitle()).append(": ").append(mismatching.get(i).getFamily()).append(' ').append(mismatching.get(i).getVersion()).append(" -> ").append(detected.get(i)).append('\n');
				if (!Mess_.on(shell_).key("dialog.main.confirm.versionmismatch", new Object[] {list.toString().trim()}).confirm())
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
				updateDosboxVersionList(mismatching.get(0));
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

		try {
			settings_.save();
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}

		if (Boolean.TRUE.equals(result_))
			Mess_.on(null).key("dialog.main.notice.restart").display();
	}

	private void initColumnIds() {
		columnNames_ = new String[Constants.RO_COLUMN_NAMES + Constants.EDIT_COLUMN_NAMES + 8];

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
		} else {
			Menu fileMenu = createMenu(appMenuBar, "dialog.main.menu.file");
			MenuItem_.on(fileMenu).key("dialog.main.menu.adjustsettings").image(ImageService.IMG_SETTINGS).listen(settingsAdapter).build();
			MenuItem_.on(fileMenu).key("dialog.main.menu.log").image(ImageService.IMG_LOG).listen(logAdapter).build();
			MenuItem_.on(fileMenu).key("dialog.main.menu.databasecleanup").image(ImageService.IMG_CLEAN).listen(cleanupAdapter).build();
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

		Menu dbversionsMenu = createMenu(appMenuBar, "dialog.main.menu.dosboxversions");
		MenuItem_.on(dbversionsMenu).key("dialog.main.menu.locatedosbox").image(ImageService.IMG_ZOOM).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doLocateDosbox(true);
			}
		}).build();
		MenuItem_.on(dbversionsMenu).key("dialog.main.menu.switchdosboxversion").image(ImageService.IMG_MIGRATE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (Boolean.TRUE.equals(new SwitchDosboxVersionDialog(shell_).open())) {
					updateProfilesList(getSelectedProfileIds());
					displayProfileInformation(false);
				}
			}
		}).build();
		MenuItem_.on(dbversionsMenu).key("dialog.main.menu.checkprofiles").image(ImageService.IMG_MIGRATE).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (Boolean.TRUE.equals(new CheckProfilesDialog(shell_).open())) {
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

		if (systemMenu == null) {
			Menu helpMenu = createMenu(appMenuBar, "dialog.main.menu.help");
			MenuItem_.on(helpMenu).key("dialog.main.menu.about").image(ImageService.IMG_ABOUT).listen(openAboutAdapter).build();
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

		ToolBar toolBar = createToolBar(toolbarComposite);
		createImageToolItem(toolBar, "dialog.main.addprofile", ImageService.IMG_TB_NEW, addProfAdapter);
		createImageToolItem(toolBar, "dialog.main.editprofile", ImageService.IMG_TB_EDIT, editProfAdapter);
		createImageToolItem(toolBar, "dialog.main.removeprofile", ImageService.IMG_TB_DELETE, removeProfAdapter);
		createImageToolItem(toolBar, "dialog.main.runprofile", ImageService.IMG_TB_RUN, runProfAdapter);
		setupToolItem_ = createImageToolItem(toolBar, "dialog.main.runprofilesetup", ImageService.IMG_TB_SETUP, setupProfAdapter);
		createSeparatorToolItem(toolBar, 40);
		createImageToolItem(toolBar, "dialog.main.addwizard", ImageService.IMG_TB_ADDGAMEWIZARD, new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (requireDefaultDBVersion() == null)
					return;

				updateWithAddedProfile(new AddGameWizardDialog(shell_).open());
			}
		});

		initColumnIds();

		ViewType[] views = new ViewType[] {new ViewType(ProfilesListType.TABLE.toString(), ImageService.IMG_TABLE, "dialog.main.profiles.viewtype.table"),
				new ViewType(ProfilesListType.SMALL_TILES.toString(), ImageService.IMG_TILES_SMALL, "dialog.main.profiles.viewtype.smalltiles"),
				new ViewType(ProfilesListType.MEDIUM_TILES.toString(), ImageService.IMG_TILES_MEDIUM, "dialog.main.profiles.viewtype.mediumtiles"),
				new ViewType(ProfilesListType.LARGE_TILES.toString(), ImageService.IMG_TILES_LARGE, "dialog.main.profiles.viewtype.largetiles"),
				new ViewType(ProfilesListType.SMALL_BOXES.toString(), ImageService.IMG_BOXES_SMALL, "dialog.main.profiles.viewtype.smallboxes"),
				new ViewType(ProfilesListType.MEDIUM_BOXES.toString(), ImageService.IMG_BOXES_MEDIUM, "dialog.main.profiles.viewtype.mediumboxes"),
				new ViewType(ProfilesListType.LARGE_BOXES.toString(), ImageService.IMG_BOXES_LARGE, "dialog.main.profiles.viewtype.largeboxes")};
		ViewType currentViewType = settings_.getValue("gui", "viewstyle").equalsIgnoreCase(views[0].getName()) ? views[0]: views[1];

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
		createSeparatorToolItem(toolBarRight, 4);
		ToolItem displayScreenshots = createImageToolItem(toolBarRight, SWT.CHECK, ImageService.getResourceImage(shell_.getDisplay(), ImageService.IMG_SCREENSHOTS),
			text_.get("dialog.main.profiles.togglebutton.screenshots"), new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					ToolItem item = (ToolItem)event.widget;
					((GridData)scrolledCompositeForThumbs_.getLayoutData()).exclude = !item.getSelection();
					scrolledCompositeForThumbs_.getParent().layout();
					settings_.setBooleanValue("gui", "screenshotsvisible", item.getSelection());
					displayProfileInformation(false);
				}
			});
		displayScreenshots.setSelection(settings_.getBooleanValue("gui", "screenshotsvisible"));
		createSeparatorToolItem(toolBarRight, 4);
		ToolItem displayNotes = createImageToolItem(toolBarRight, SWT.CHECK, ImageService.getResourceImage(shell_.getDisplay(), ImageService.IMG_NOTES),
			text_.get("dialog.main.profiles.togglebutton.notes"), new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					ToolItem item = (ToolItem)event.widget;
					sashInfoForm_.setMaximizedControl(item.getSelection() ? null: filterFolder_);
					settings_.setBooleanValue("gui", "notesvisible", item.getSelection());
					displayProfileInformation(false);
				}
			});
		displayNotes.setSelection(settings_.getBooleanValue("gui", "notesvisible"));

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
						filtersList_ = filterRepo.listAll();
						filtersList_.add(0, new Filter(text_.get("dialog.main.allprofiles"), null));
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

		Menu menu = new Menu(profileTable_.getControl());
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
		Menu profileViewSubMenu = createMenu(menu, "dialog.main.profile.view", ImageService.IMG_ZOOM);
		MenuItem_.on(menu).build();
		MenuItem_.on(menu).key("dialog.main.profile.add").image(ImageService.IMG_NEW).listen(addProfAdapter).build();
		MenuItem_.on(menu).key("dialog.main.profile.edit").image(ImageService.IMG_EDIT).listen(editProfAdapter).build();
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
						MenuItem_.on(menu).pos(0).key("dialog.main.profile.startmanuallywith").build().setMenu(createDosboxVersionsSubmenu(menu, ProfileRunMode.NORMAL, true));
					MenuItem_.on(menu).pos(0).key("dialog.main.profile.startmanually").listen(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent evnt) {
							doRunProfile(ProfileRunMode.NORMAL, true);
						}
					}).build();
					if (prof.hasAltExe(1)) {
						if (dbversionsList_.size() > 1)
							MenuItem_.on(menu).pos(0).key(prof.getAltExeFilenames()[1]).build().setMenu(createDosboxVersionsSubmenu(menu, ProfileRunMode.ALT2, false));
						MenuItem_.on(menu).pos(0).key(prof.getAltExeFilenames()[1]).listen(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent evnt) {
								doRunProfile(ProfileRunMode.ALT2, false);
							}
						}).build();
					}
					if (prof.hasAltExe(0)) {
						if (dbversionsList_.size() > 1)
							MenuItem_.on(menu).pos(0).key(prof.getAltExeFilenames()[0]).build().setMenu(createDosboxVersionsSubmenu(menu, ProfileRunMode.ALT1, false));
						MenuItem_.on(menu).pos(0).key(prof.getAltExeFilenames()[0]).listen(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent evnt) {
								doRunProfile(ProfileRunMode.ALT1, false);
							}
						}).build();
					}
					if (prof.hasSetup()) {
						if (dbversionsList_.size() > 1)
							MenuItem_.on(menu).pos(0).key("dialog.main.profile.setupwith").build().setMenu(createDosboxVersionsSubmenu(menu, ProfileRunMode.SETUP, false));
						MenuItem_.on(menu).pos(0).key("dialog.main.profile.setup").image(ImageService.IMG_SETUP).listen(setupProfAdapter).build();
					}
					if (dbversionsList_.size() > 1)
						MenuItem_.on(menu).pos(0).key("dialog.main.profile.runwith").build().setMenu(createDosboxVersionsSubmenu(menu, ProfileRunMode.NORMAL, false));
					MenuItem_.on(menu).pos(0).key("dialog.main.profile.run").image(ImageService.IMG_RUN).listen(runProfAdapter).build();

					for (MenuItem item: profileViewSubMenu.getItems())
						item.dispose();
					for (org.dbgl.model.Link lnk: prof.getLinks()) {
						String destination = lnk.getDestination();
						if (StringUtils.isNotBlank(destination)) {
							MenuItem_.on(profileViewSubMenu).txt(StringUtils.abbreviateMiddle(lnk.getDisplayTitle(), "....", 80)).listen(new SelectionAdapter() {
								@Override
								public void widgetSelected(SelectionEvent evnt) {
									SystemUtils.openForBrowsing(destination, getParent());
								}
							}).build();
						}
					}
					MenuItem_.on(profileViewSubMenu).key("dialog.main.profile.view.conf").listen(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent evnt) {
							SystemUtils.openForEditing(prof.getConfigurationCanonicalFile(), getParent());
						}
					}).build();
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
				profilesList_ = new ProfileRepository().list(orderingVector_.toClause(), filterClause_, dbversionsList_);
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

	private void toggleProfileViewType(ViewType newViewType) {
		viewSelector_.setImage(ImageService.getResourceImage(shell_.getDisplay(), newViewType.getImage()));
		viewSelector_.setToolTipText(text_.get(newViewType.getDisplayName()));
		settings_.setValue("gui", "viewstyle", newViewType.getName().toLowerCase());
		rebuildProfilesTable();
	}

	@Override
	public void doReorder(int columnId, int dir) {
		Set<Integer> selectedProfiles = getSelectedProfileIds();
		try {
			orderingVector_.addOrdering(columnIds_[columnId], dir == SWT.UP);
			profilesList_ = new ProfileRepository().list(orderingVector_.toClause(), filterClause_, dbversionsList_);
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
			}
			setupToolItem_.setEnabled(prof.hasSetup());
		}
		if (forceRefresh)
			profileTable_.redraw();
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
		MenuItem_.on(viewDosboxSubMenu).key("dialog.main.profile.view.conf").listen(new SelectionAdapter() {
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
		MenuItem_.on(viewTemplateSubMenu).key("dialog.main.profile.view.conf").listen(new SelectionAdapter() {
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
				default:
					value = StringUtils.EMPTY;
			}
			if (colId != 20) {
				newItemTableItem.setText(i, colId, columnNames_[colId], value);
			}
		}
		newItemTableItem.setData(new ThumbInfo(prof.getCapturesString()));
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
			profilesList_ = new ProfileRepository().list(orderingVector_.toClause(), filterClause_, dbversionsList_);
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

	private CTabItem addFilterTab(Filter filter) {
		CTabItem item = new CTabItem(filterFolder_, filter.getFilter() == null ? SWT.NONE: SWT.CLOSE);
		item.setText("   " + filter.getTitle() + "   ");
		item.setData(filter.getId());
		return item;
	}

	private void doEditProfile(boolean focusTitle) {
		int index = profileTable_.getSelectionIndex();
		if (index != -1) {
			if (profileTable_.getSelectionCount() > 1) {
				if (requireDefaultDBVersion() == null)
					return;
				
				List<Profile> loadedProfiles = new ProfileLoader(shell_, getSelectedProfiles()).open();
				if (loadedProfiles != null) {
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
		if (index > 0) {
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
