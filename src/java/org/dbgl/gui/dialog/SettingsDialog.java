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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.constants.Constants;
import org.dbgl.gui.abstractdialog.SizeControlledTabbedDialog;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.CTabFolder_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Combo_;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.GridData_;
import org.dbgl.gui.controls.Group_;
import org.dbgl.gui.controls.Label_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.Table_;
import org.dbgl.gui.controls.Text_;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.StringRelatedUtils;
import org.dbgl.util.searchengine.MetropolisSearchEngine;
import org.dbgl.util.searchengine.MobyGamesSearchEngine;
import org.dbgl.util.searchengine.PouetSearchEngine;
import org.dbgl.util.searchengine.TheGamesDBSearchEngine;
import org.dbgl.util.searchengine.WebSearchEngine;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FontDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


public class SettingsDialog extends SizeControlledTabbedDialog<Boolean> {

	private static final int EDITABLE_COLUMN = 0;

	/** alpha 49: opens the window that arranges the toolbar of the main window (given by it; null: no button for it) */
	private static java.util.function.Consumer<Shell> toolbarCustomizer_;

	public static void setToolbarCustomizer(java.util.function.Consumer<Shell> customizer) {
		toolbarCustomizer_ = customizer;
	}

	private int lastOptionSelection = -1;
	private final String[] columnNames_;

	public SettingsDialog(Shell parent, String[] columnNames) {
		super(parent, "settingsdialog");
		columnNames_ = columnNames;
		result_ = false;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.settings.title");
	}

	@Override
	protected void onShellCreated() {
		Composite generalComposite = createScrolledTabWithComposite("dialog.settings.tab.general", new GridLayout());

		Map<String, Locale> locales = getLocales();
		Locale locale = Locale.of(settings_.getValue("locale", "language"), settings_.getValue("locale", "country"), settings_.getValue("locale", "variant"));
		String locString = locales.keySet().stream().filter(key -> locales.get(key).equals(locale)).findFirst().orElse(StringUtils.EMPTY);

		// alpha 48: the language first
		Group i18nGroup = Group_.on(generalComposite).layout(new GridLayout(2, false)).key("dialog.settings.i18n").build();
		Combo localeCombo = Chain.on(i18nGroup).lbl(l -> l.key("dialog.settings.languagecountry")).cmb(c -> c.items(locales.keySet().toArray(new String[0])).visibleItemCount(20)).combo();
		localeCombo.setText(locString);

		// then what DBGL looks for on the internet by itself, then the settings for DOSBox
		Group updatesGroup = Group_.on(generalComposite).layout(new GridLayout(2, false)).key("dialog.settings.autoupdates").build();
		Button checkForUpdates = Chain.on(updatesGroup).lbl(l -> l.key("dialog.settings.checkforupdates")).but(b -> b.tooltip("dialog.settings.checkforupdates.tooltip").select(settings_.getBooleanValue("dosbox", "checkforupdates"))).button();
		Button editionCheck = Chain.on(updatesGroup).lbl(l -> l.key("dialog.settings.edition.check2")).but(b -> b.tooltip("dialog.settings.edition.check.tooltip")
				.select(settings_.getBooleanValue(org.dbgl.util.EditionUpdateChecker.SECTION, org.dbgl.util.EditionUpdateChecker.CHECK))).button();
		Button editionPre = Chain.on(updatesGroup).lbl(l -> l.key("dialog.settings.edition.prereleases2")).but(b -> b.select(settings_.getBooleanValue(org.dbgl.util.EditionUpdateChecker.SECTION,
			org.dbgl.util.EditionUpdateChecker.PRERELEASES))).button();

		Group dosboxGroup = Group_.on(generalComposite).layout(new GridLayout(2, false)).key("dialog.settings.dosbox").build();
		Button console = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.hidestatuswindow")).but(b -> b.select(settings_.getBooleanValue("dosbox", "hideconsole"))).button();
		Button detectFailedStart = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.detectfailedstart")).but(b -> b.select(settings_.getBooleanValue("dosbox", "detectfailedstart"))).button();
		Button checkVersions = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.checkversionsatstartup")).but(b -> b.select(settings_.getBooleanValue("dosbox", "checkversionsatstartup"))).button();
		Button checkBeforeStart = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.checkbeforestart")).but(b -> b.select(settings_.getBooleanValue("dosbox", "checkbeforestart"))).button();
		// alpha 53: DOSBox Staging without its configuration in the user folder (which it reads before those of DBGL)
		Button noPrimaryConf = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.noprimaryconf")).but(b -> b.tooltip("dialog.settings.noprimaryconf.tooltip")
			.select(settings_.getBooleanValue("dosbox", "noprimaryconf"))).button();
		Button hideWin3Missing = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.hidewin3missing")).but(b -> b.tooltip("dialog.settings.hidewin3missing.tooltip")
			.select(settings_.getBooleanValue("dosbox", "hidewin3missing"))).button();
		Button hideWin3Programs = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.hidewin3programs")).but(b -> b.tooltip("dialog.settings.hidewin3programs.tooltip")
			.select(settings_.getBooleanValue("dosbox", "hidewin3programs"))).button();
		Button hideWin3Start = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.hidewin3start")).but(b -> b.tooltip("dialog.settings.hidewin3start.tooltip")
			.select(settings_.getBooleanValue("dosbox", "hidewin3start"))).button();
		Button hideShortSessions = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.hideshortsessions")).but(b -> b.tooltip("dialog.settings.hideshortsessions.tooltip")
			.select(settings_.getBooleanValue("dosbox", "hideshortsessions"))).button();
		Button ownSavestates = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.ownsavestates")).but(b -> b.tooltip("dialog.settings.ownsavestates.tooltip")
			.select(settings_.getBooleanValue("dosbox", "ownsavestates"))).button(); // alpha 48
		// "Do not ask again" in the question about the charset of a file (CharsetQuestion): asked again when ticked here
		Button askCharset = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.askcharset")).but(b -> b.tooltip("dialog.settings.askcharset2.tooltip")
				.select(!settings_.getBooleanValue(org.dbgl.gui.controls.CharsetQuestion.SECTION, org.dbgl.gui.controls.CharsetQuestion.ITEM))).button();
		Button longToolTips = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.longtooltips")).but(b -> b.tooltip("dialog.settings.longtooltips.tooltip").select(settings_.getBooleanValue("gui", "longtooltips"))).button();
		boolean askUnknown = org.dbgl.util.UnknownAutoexecLines.ASK.equals(settings_.getValue(org.dbgl.util.UnknownAutoexecLines.SECTION, org.dbgl.util.UnknownAutoexecLines.MODE));
		Combo unknownMode = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.unknownlines.mode")).cmb(c -> c.items(new String[] {text_.get("dialog.settings.unknownlines.auto"),
			text_.get("dialog.settings.unknownlines.ask")}).select(askUnknown ? 1: 0)).combo();
		Button unknownNoMarkers = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.unknownlines.nomarkers")).but(b -> b.tooltip("dialog.settings.unknownlines.nomarkers.tooltip2")
				.select(settings_.getBooleanValue(org.dbgl.util.UnknownAutoexecLines.SECTION, org.dbgl.util.UnknownAutoexecLines.NO_MARKERS))).button();
		unknownNoMarkers.setEnabled(!askUnknown);
		unknownMode.addListener(SWT.Selection, e -> unknownNoMarkers.setEnabled(unknownMode.getSelectionIndex() == 0));

		// alpha 48: settings of the profile editor
		Group editorGroup = Group_.on(generalComposite).layout(new GridLayout(3, false)).key("dialog.settings.profileeditor").build();
		// alpha 49: in this order: play time, manuals and extras, unsupported settings, then all about the groups of "More settings"
		Button showDiskSpace = Chain.on(editorGroup).lbl(l -> l.key("dialog.settings.showdiskspaceininfo")).but(b -> b.horSpan(2).select(settings_.getBooleanValue("gui", "showdiskspaceininfo"))).button();
		Button showPlayTime = Chain.on(editorGroup).lbl(l -> l.key("dialog.settings.showplaytimeininfo")).but(b -> b.horSpan(2).select(settings_.getBooleanValue("gui", "showplaytimeininfo"))).button();
		Button findExtras = Chain.on(editorGroup).lbl(l -> l.key("dialog.settings.findextras")).but(b -> b.horSpan(2).tooltip("dialog.settings.findextras.tooltip")
				.select(settings_.getBooleanValue(org.dbgl.util.ExtrasFinder.SECTION, org.dbgl.util.ExtrasFinder.ON_ADD))).button();
		Button scummvmWanted = Chain.on(editorGroup).lbl(l -> l.key("dialog.settings.scummvm")).but(b -> b.horSpan(2).tooltip("dialog.settings.scummvm.tooltip2")
				.select(settings_.getBooleanValue("gui", org.dbgl.util.Scummvm.SETTING))).button();
		Button hideUnsupported = Chain.on(editorGroup).lbl(l -> l.key("dialog.settings.hideunsupportedsettings")).but(b -> b.horSpan(2).select(settings_.getBooleanValue("gui", "hideunsupportedsettings"))).button();
		String[] expandModes = {"collapsed", "always", "changed"};
		Combo extrasExpand = Chain.on(editorGroup).lbl(l -> l.key("dialog.settings.extrasexpand")).cmb(c -> c.horSpan(2).items(new String[] {text_.get("dialog.settings.extrasexpand.collapsed"),
			text_.get("dialog.settings.extrasexpand.always"), text_.get("dialog.settings.extrasexpand.changed")}).select(Math.max(0, Arrays.asList(expandModes).indexOf(settings_.getValue("gui", "extrasexpand"))))).combo();
		Spinner extrasExpandMax = Chain.on(editorGroup).lbl(l -> l.key("dialog.settings.extrasexpandmax")).spn(s -> s.min(0).max(99).tooltip("dialog.settings.extrasexpandmax.tooltip").select(settings_.getIntValue("gui", "extrasexpandmax"))).lbl(l -> l.key("dialog.settings.extrasexpandmax.unit")).spinner();
		Button extrasOnExtrasPages = Chain.on(editorGroup).lbl(l -> l.key("dialog.settings.extrasexpandonextraspages")).but(b -> b.horSpan(2).tooltip("dialog.settings.extrasexpandonextraspages.tooltip").select(settings_.getBooleanValue("gui", "extrasexpandonextraspages"))).button();
		Button extrasCount = Chain.on(editorGroup).lbl(l -> l.key("dialog.settings.extrascountchanges")).but(b -> b.horSpan(2).select(settings_.getBooleanValue("gui", "extrascountchanges"))).button();
		java.util.List<WebSearchEngine> engines = SettingsService.availableWebSearchEngines();
		WebSearchEngine currentEngine = WebSearchEngine.getBySimpleName(settings_.getValue("gui", "searchengine"));
		Combo defaultEngine = Chain.on(editorGroup).lbl(l -> l.key("dialog.settings.defaultsearchengine")).cmb(c -> c.horSpan(2).tooltip("dialog.settings.defaultsearchengine.tooltip")
			.items(engines.stream().map(WebSearchEngine::getName).toArray(String[]::new)).select(Math.max(0, engines.indexOf(currentEngine)))).combo();
		alignFirstColumns(updatesGroup, dosboxGroup, editorGroup); // alpha 48: the boxes of these groups in one column

		Group backupGroup = Group_.on(generalComposite).layout(new GridLayout(2, false)).key("dialog.settings.autobackup").build();
		String[] backupModes = {"never", "daily", "weekly"};
		Combo autoBackup = Chain.on(backupGroup).lbl(l -> l.key("dialog.settings.autobackup.when")).cmb(c -> c.items(new String[] {text_.get("dialog.settings.autobackup.never"),
			text_.get("dialog.settings.autobackup.daily"), text_.get("dialog.settings.autobackup.weekly")}).select(Math.max(0, Arrays.asList(backupModes).indexOf(settings_.getValue("backup", "auto"))))).combo();
		Spinner backupKeep = Chain.on(backupGroup).lbl(l -> l.key("dialog.settings.autobackup.keep")).spn(s -> s.min(1).max(50).select(settings_.getIntValue("backup", "keep"))).spinner();
		// alpha 54 (TEST3): the folder of all of DBGL's backups (empty: backups in the data folder)
		new Label(backupGroup, SWT.NONE).setText(text_.get("dialog.settings.autobackup.dir"));
		org.eclipse.swt.widgets.Composite backupDirRow = new org.eclipse.swt.widgets.Composite(backupGroup, SWT.NONE);
		GridLayout backupDirLayout = new GridLayout(2, false);
		backupDirLayout.marginWidth = backupDirLayout.marginHeight = 0;
		backupDirRow.setLayout(backupDirLayout);
		Text backupDir = new Text(backupDirRow, SWT.BORDER);
		String backupDirBefore = org.dbgl.service.BackupService.backupDir().getAbsolutePath();
		backupDir.setText(backupDirBefore);
		backupDir.setToolTipText(text_.get("dialog.settings.autobackup.dir.tooltip", new Object[] {org.dbgl.service.BackupService.defaultAutoBackupDir().getPath()}));
		GridData backupDirData = new GridData(SWT.FILL, SWT.CENTER, false, false);
		backupDirData.widthHint = 420;
		backupDir.setLayoutData(backupDirData);
		Button backupDirBrowse = new Button(backupDirRow, SWT.PUSH);
		backupDirBrowse.setText("...");
		backupDirBrowse.addListener(SWT.Selection, e -> {
			org.eclipse.swt.widgets.DirectoryDialog dd = new org.eclipse.swt.widgets.DirectoryDialog(shell_);
			dd.setFilterPath(backupDir.getText());
			String chosen = dd.open();
			if (chosen != null)
				backupDir.setText(chosen);
		});
		// alpha 52: on request with the screenshots and the backups of the saved games; their size is counted in the background
		Button backupCaptures = Chain.on(backupGroup).lbl(l -> l.key("dialog.settings.autobackup.captures")).but(b -> b.select(settings_.getBooleanValue("backup", "captures"))).button();
		Button backupSavegames = Chain.on(backupGroup).lbl(l -> l.key("dialog.settings.autobackup.savegames")).but(b -> b.select(settings_.getBooleanValue("backup", "savegames"))).button();
		Thread sizes = new Thread(() -> {
			long[] size = org.dbgl.service.BackupService.capturesSizes();
			if (!shell_.isDisposed())
				shell_.getDisplay().asyncExec(() -> {
					if (backupCaptures.isDisposed())
						return;
					java.util.Locale sizeLocale = java.util.Locale.of(settings_.getValue("locale", "language"), settings_.getValue("locale", "country"));
					backupCaptures.setText(text_.get("dialog.settings.autobackup.size", new Object[] {org.dbgl.util.DiskUsage.format(size[0], sizeLocale)}));
					backupSavegames.setText(text_.get("dialog.settings.autobackup.size", new Object[] {org.dbgl.util.DiskUsage.format(size[1], sizeLocale)}));
					backupCaptures.getParent().layout(true);
				});
		}, "backup sizes");
		sizes.setDaemon(true);
		sizes.start();

		// alpha 52: a second place for the backups, e.g. OneDrive or Dropbox (found on this computer, offered in the list)
		Group secondGroup = Group_.on(generalComposite).layout(new GridLayout(3, false)).key("dialog.settings.secondplace").build();
		java.util.Map<String, java.io.File> clouds = org.dbgl.util.SecondBackupPlace.cloudFolders();
		Combo secondPlace = Chain.on(secondGroup).lbl(l -> l.key("dialog.settings.secondplace.folder")).cmb(c -> c.editable().tooltip("dialog.settings.secondplace.folder.tooltip")
			.items(clouds.values().stream().map(java.io.File::getPath).toArray(String[]::new))).combo();
		secondPlace.setText(settings_.getValue(org.dbgl.util.SecondBackupPlace.SECTION, org.dbgl.util.SecondBackupPlace.PLACE));
		GridData secondData = new GridData(SWT.FILL, SWT.CENTER, false, false);
		secondData.widthHint = 420;
		secondPlace.setLayoutData(secondData);
		Button secondBrowse = new Button(secondGroup, SWT.PUSH);
		secondBrowse.setText("...");
		secondBrowse.addListener(SWT.Selection, e -> {
			org.eclipse.swt.widgets.DirectoryDialog dd = new org.eclipse.swt.widgets.DirectoryDialog(shell_);
			if (!secondPlace.getText().isBlank())
				dd.setFilterPath(secondPlace.getText());
			String chosen = dd.open();
			if (chosen != null)
				secondPlace.setText(chosen);
		});
		Button secondSavegames = Chain.on(secondGroup).lbl(l -> l.key("dialog.settings.secondplace.savegames")).but(b -> b.horSpan(2).select(settings_.getBooleanValue(org.dbgl.util.SecondBackupPlace.SECTION, org.dbgl.util.SecondBackupPlace.SAVEGAMES))).button();
		Button secondAuto = Chain.on(secondGroup).lbl(l -> l.key("dialog.settings.secondplace.auto")).but(b -> b.horSpan(2).select(settings_.getBooleanValue(org.dbgl.util.SecondBackupPlace.SECTION, org.dbgl.util.SecondBackupPlace.AUTO))).button();
		Label secondHint = new Label(secondGroup, SWT.WRAP);
		GridData hintData = new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false, 3, 1);
		hintData.widthHint = 700;
		secondHint.setLayoutData(hintData);
		secondHint.setText(text_.get(clouds.isEmpty() ? "dialog.settings.secondplace.hint.none": "dialog.settings.secondplace.hint", new Object[] {String.join(", ", clouds.keySet())}));
		alignFirstColumns(backupGroup, secondGroup); // TEST4: the settings of both groups in one column

		Group sendToGroup = Group_.on(generalComposite).layout(new GridLayout(2, false)).key("dialog.settings.sendto").build();
		Button portEnabled = Chain.on(sendToGroup).lbl(l -> l.key("dialog.settings.enableport")).but(b -> b.select(settings_.getBooleanValue("communication", "port_enabled"))).button();
		Text port = Chain.on(sendToGroup).lbl(l -> l.key("dialog.settings.port")).txt(t -> t.val(settings_.getValue("communication", "port"))).text();
		GridData portData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		portData.widthHint = 70;
		port.setLayoutData(portData);
		port.setEnabled(portEnabled.getSelection());
		portEnabled.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				port.setEnabled(portEnabled.getSelection());
			}
		});

		Group profileDefGroup = Group_.on(generalComposite).layout(new GridLayout(3, false)).key("dialog.settings.profiledefaults").build();
		Combo confLocation = Chain.on(profileDefGroup).lbl(l -> l.key("dialog.settings.configfile")).cmb(
			c -> c.items(getConfLocations()).select(settings_.getIntValue("profiledefaults", "confpath"))).combo();
		Combo confFilename = Combo_.on(profileDefGroup).items(getConfFilenames()).select(settings_.getIntValue("profiledefaults", "conffile")).build();

		Combo capturesLocation = Chain.on(profileDefGroup).lbl(l -> l.key("dialog.settings.captures")).cmb(
			c -> c.horSpan(2).items(getCapsLocations()).select(settings_.getIntValue("profiledefaults", "capturespath"))).combo();
		// all DOSBox settings in each profile (as D-Fend Reloaded does), with the drawbacks right below, impossible to miss
		boolean allSettingsBefore = org.dbgl.util.FullConfProfiles.enabled();
		Button allSettings = Chain.on(profileDefGroup).lbl(l -> l.key("dialog.settings.allsettings.new")).but(b -> b.horSpan(2).select(allSettingsBefore)).button();
		org.eclipse.swt.widgets.Label allSettingsWarning = new org.eclipse.swt.widgets.Label(profileDefGroup, SWT.WRAP);
		GridData warningData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		warningData.widthHint = 300; // alpha 49: as wide as the window lets it be, wrapped to that width
		allSettingsWarning.setLayoutData(warningData);
		allSettingsWarning.setText(text_.get("dialog.settings.allsettings.warning"));
		allSettingsWarning.setForeground(org.dbgl.gui.controls.DarkTheme.textColor(shell_.getDisplay(), SWT.COLOR_DARK_RED));
		// the height a wrapped text needs depends on its width: taken from the group whenever that changes
		profileDefGroup.addListener(SWT.Resize, e -> {
			int width = profileDefGroup.getClientArea().width - 16;
			if ((width > 100) && (Math.abs(warningData.widthHint - width) > 4)) {
				warningData.widthHint = width;
				profileDefGroup.getParent().layout(true, true);
			}
		});

		// alpha 49: the automatic backup right below the automatic updates, "Send to" at the very bottom
		backupGroup.moveBelow(updatesGroup);
		secondGroup.moveBelow(backupGroup); // alpha 52: right below the automatic backup
		sendToGroup.moveBelow(profileDefGroup);

		// alpha 48: all groups of this tab as wide as the widest
		for (org.eclipse.swt.widgets.Control c: generalComposite.getChildren())
			if (c instanceof Group)
				c.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));


		Composite profileTableComposite = createTabWithComposite("dialog.settings.tab.profiletable", new GridLayout());

		List<Integer> allColumnIds = getColumnIds();

		Group visColumnsGroup = Group_.on(profileTableComposite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).layout(new FillLayout()).key("dialog.settings.visiblecolunms").build();

		Table visibleColumnsTable = Table_.on(visColumnsGroup).check().build();
		TableColumn column1 = new TableColumn(visibleColumnsTable, SWT.NONE);
		column1.setWidth(350);
		// alpha 49: how the text stands in each column of the profile list; a click changes it
		column1.setText(text_.get("dialog.settings.columnname"));
		TableColumn column2 = new TableColumn(visibleColumnsTable, SWT.NONE);
		column2.setWidth(240);
		column2.setText(text_.get("dialog.settings.columnalign"));
		visibleColumnsTable.setHeaderVisible(true);
		String[] alignNames = {text_.get("dialog.settings.columnalign.default"), text_.get("dialog.settings.columnalign.left"), text_.get("dialog.settings.columnalign.center"),
				text_.get("dialog.settings.columnalign.right")};
		int[] aligns = new int[columnNames_.length];
		TableItem[] visibleColumns = new TableItem[columnNames_.length];
		for (int i = 0; i < columnNames_.length; i++) {
			visibleColumns[i] = new TableItem(visibleColumnsTable, SWT.BORDER);
			visibleColumns[i].setText(columnNames_[allColumnIds.get(i)]);
			visibleColumns[i].setChecked(settings_.getBooleanValue("gui", "column" + (allColumnIds.get(i) + 1) + "visible"));
			aligns[i] = Math.max(0, Math.min(3, settings_.getIntValue("gui", "column" + (allColumnIds.get(i) + 1) + "align")));
			visibleColumns[i].setText(1, alignNames[aligns[i]]);
		}
		visibleColumnsTable.addListener(SWT.MouseDown, e -> {
			for (int i = 0; i < visibleColumns.length; i++) {
				if (visibleColumns[i].getBounds(1).contains(e.x, e.y)) {
					aligns[i] = (aligns[i] + 1) % alignNames.length;
					visibleColumns[i].setText(1, alignNames[aligns[i]]);
					break;
				}
			}
		});

		TableEditor editor = new TableEditor(visibleColumnsTable);
		editor.horizontalAlignment = SWT.LEFT;
		editor.grabHorizontal = true;
		editor.minimumWidth = 50;

		visibleColumnsTable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				// Clean up any previous editor control
				Control oldEditor = editor.getEditor();
				if (oldEditor != null) {
					oldEditor.dispose();
				}

				// Identify the selected row
				TableItem item = (TableItem)event.item;
				if (item == null) {
					return;
				}
				int selIdx = item.getParent().getSelectionIndex();
				if (selIdx == -1)
					return;
				int idx = allColumnIds.get(selIdx);
				if ((idx >= Constants.RO_COLUMN_NAMES && idx < (Constants.RO_COLUMN_NAMES + Constants.EDIT_COLUMN_NAMES_1))
						|| (idx >= Constants.RO_COLUMN_NAMES + Constants.EDIT_COLUMN_NAMES_1 + Constants.STATS_COLUMN_NAMES
								&& idx < (Constants.RO_COLUMN_NAMES + Constants.EDIT_COLUMN_NAMES_1 + Constants.STATS_COLUMN_NAMES + Constants.EDIT_COLUMN_NAMES_2))) {
					// The control that will be the editor must be a child of the table
					Text newEditor = Text_.on(visibleColumnsTable).val(item.getText(EDITABLE_COLUMN)).build();
					newEditor.addModifyListener(mEvent -> {
						Text text = (Text)editor.getEditor();
						editor.getItem().setText(EDITABLE_COLUMN, text.getText());
					});
					newEditor.selectAll();
					newEditor.setFocus();
					editor.setEditor(newEditor, item, EDITABLE_COLUMN);
				}
			}
		});

		Group addProfGroup = Group_.on(profileTableComposite).layout(new GridLayout(2, false)).layoutData(new GridData(SWT.FILL, SWT.FILL, true, false)).key(
			"dialog.settings.addeditduplicateprofile").build();
		Button autosort = Chain.on(addProfGroup).lbl(l -> l.key("dialog.settings.autosort")).but(b -> b.select(settings_.getBooleanValue("gui", "autosortonupdate"))).button();

		Composite dynamicOptionsComposite = createTabWithComposite("dialog.settings.tab.dynamicoptions", 1);

		SashForm sashForm = createSashForm(dynamicOptionsComposite, 1);

		Composite left = Composite_.on(sashForm).innerLayout(1).build();
		org.eclipse.swt.widgets.List optionsList = Chain.on(left).lbl(l -> l.key("dialog.settings.options")).lst(l -> l).list();

		Composite right = Composite_.on(sashForm).innerLayout(1).build();
		Text values = Chain.on(right).lbl(l -> l.key("dialog.settings.values")).txt(Text_.Builder::multi).text();

		Map<String, String> optionsMap = new LinkedHashMap<>();
		for (String s: settings_.getProfileSectionItemNames()) {
			optionsMap.put(s, settings_.getMultilineValue("profile", s, values.getLineDelimiter()));
			optionsList.add(s);
		}

		optionsList.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				updateOptionsMap(optionsMap, optionsList, lastOptionSelection, values);
				lastOptionSelection = optionsList.getSelectionIndex();
				if (lastOptionSelection != -1) {
					values.setText(optionsMap.get(optionsList.getItem(lastOptionSelection)));
				}
			}
		});

		Composite guiComposite = createScrolledTabWithComposite("dialog.settings.tab.gui", new GridLayout(4, false)); // alpha 48: scrolls when the window is lower than the page

		// alpha 48: what the list of profiles and the area below it show
		Group overviewGroup = Group_.on(guiComposite).layout(new GridLayout(2, false)).layoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 4, 1)).key("dialog.settings.profileoverview").build();
		Button showDiskSpaceInNotes = Chain.on(overviewGroup).lbl(l -> l.key("dialog.settings.showdiskspaceinnotes")).but(b -> b.select(settings_.getBooleanValue("gui", "showdiskspaceinnotes"))).button();
		Button showPlayTimeInNotes = Chain.on(overviewGroup).lbl(l -> l.key("dialog.settings.showplaytimeinnotes")).but(b -> b.select(settings_.getBooleanValue("gui", "showplaytimeinnotes"))).button();
		Button profileHints = Chain.on(overviewGroup).lbl(l -> l.key("dialog.settings.profilehints")).but(b -> b.tooltip("dialog.settings.profilehints.tooltip").select(settings_.getBooleanValue("gui", "profilehints"))).button();
		Button offerNextPart = Chain.on(overviewGroup).lbl(l -> l.key("dialog.settings.offernextpart")).but(b -> b.select(settings_.getBooleanValue("gui", "offernextpart"))).button(); // alpha 52
		Button profileWarnings = Chain.on(overviewGroup).lbl(l -> l.key("dialog.settings.profilewarnings")).but(b -> b.tooltip("dialog.settings.profilewarnings.tooltip").select(settings_.getBooleanValue("gui", "profilewarnings"))).button();
		// alpha 49: the screenshots below the profiles, per view: as chosen with the button in the toolbar, always shown, always hidden
		// alpha 50: the tab with the profiles that have no pictures: never, always, or only while there are such profiles
		Combo noImagesTab = Chain.on(overviewGroup).lbl(l -> l.key("dialog.settings.noimages")).cmb(
			c -> c.items(new String[] {text_.get("dialog.settings.noimagestab.never"), text_.get("dialog.settings.noimagestab.always"), text_.get("dialog.settings.noimagestab.found")})).combo();
		noImagesTab.select(Math.max(0, Math.min(2, settings_.getIntValue("gui", "noimagestab"))));
		String[] shotRules = {text_.get("dialog.settings.screenshots.asis"), text_.get("dialog.settings.screenshots.show"), text_.get("dialog.settings.screenshots.hide")};
		String[] shotKeys = {"screenshots_list", "screenshots_tiles", "screenshots_boxes"};
		Combo[] shotCombos = new Combo[3];
		for (int i = 0; i < 3; i++) {
			String shotLabel = "dialog.settings.screenshots." + new String[] {"list", "tiles", "boxes"}[i];
			shotCombos[i] = Chain.on(overviewGroup).lbl(l -> l.key(shotLabel)).cmb(c -> c.items(shotRules)).combo();
			shotCombos[i].select(Math.max(0, Math.min(2, settings_.getIntValue("gui", shotKeys[i]))));
		}
		// alpha 50: the four lists below each other have one width: that of the widest of them
		int comboWidth = noImagesTab.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
		for (Combo combo: shotCombos)
			comboWidth = Math.max(comboWidth, combo.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
		for (Combo combo: new Combo[] {noImagesTab, shotCombos[0], shotCombos[1], shotCombos[2]}) {
			GridData comboData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
			comboData.minimumWidth = comboWidth;
			comboData.horizontalAlignment = SWT.FILL;
			combo.setLayoutData(comboData);
		}
		// alpha 49: the window that arranges the toolbar (else only in the menu of a right click on the toolbar)
		if (toolbarCustomizer_ != null) {
			Button customize = new Button(overviewGroup, SWT.PUSH);
			customize.setText(text_.get("dialog.main.tb.customize"));
			customize.setToolTipText(text_.get("dialog.settings.tip.customizetoolbar"));
			customize.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
			customize.addListener(SWT.Selection, e -> toolbarCustomizer_.accept(shell_));
		}

		// alpha 48: the TV mode: where its key hints are, the names and colours of the four buttons of the controller
		Group tvGroup = Group_.on(guiComposite).layout(new GridLayout(6, false)).layoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 4, 1)).key("dialog.settings.tv").build();
		new Label(tvGroup, SWT.NONE).setText(TextService.getInstance().get("dialog.settings.tv.hints"));
		Combo tvHints = new Combo(tvGroup, SWT.READ_ONLY);
		tvHints.setItems(TextService.getInstance().get("dialog.settings.tv.hints.right"), TextService.getInstance().get("dialog.settings.tv.hints.center"),
			TextService.getInstance().get("dialog.settings.tv.hints.left"));
		tvHints.select(Math.max(0, Math.min(2, settings_.getIntValue("gui", "tvhints"))));
		tvHints.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 5, 1));
		Text[] tvLabels = new Text[4];
		org.eclipse.swt.graphics.RGB[] tvColors = new org.eclipse.swt.graphics.RGB[4];
		for (int i = 0; i < 4; i++) {
			int n = i;
			Label buttonLabel = new Label(tvGroup, SWT.NONE);
			buttonLabel.setText(TextService.getInstance().get("dialog.settings.tv.button", new Object[] {i + 1}));
			if (i % 2 == 1) { // the second button of a row a bit apart from the first
				GridData bd = new GridData();
				bd.horizontalIndent = 24;
				buttonLabel.setLayoutData(bd);
			}
			tvLabels[i] = new Text(tvGroup, SWT.BORDER);
			tvLabels[i].setText(settings_.getValue("gui", "tvbutton" + (i + 1)));
			tvLabels[i].setTextLimit(6);
			GridData labelData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
			labelData.widthHint = 60;
			tvLabels[i].setLayoutData(labelData);
			tvColors[i] = org.dbgl.gui.tv.TvMode.buttonColor(i + 1);
			Button color = new Button(tvGroup, SWT.PUSH);
			color.setText(TextService.getInstance().get("dialog.settings.tv.color"));
			Runnable swatch = () -> {
				org.eclipse.swt.graphics.Image old = color.getImage();
				org.eclipse.swt.graphics.Image img = new org.eclipse.swt.graphics.Image(shell_.getDisplay(), 16, 12);
				org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(img);
				gc.setBackground(new org.eclipse.swt.graphics.Color(tvColors[n]));
				gc.fillRectangle(0, 0, 16, 12);
				gc.dispose();
				color.setImage(img);
				if (old != null)
					old.dispose();
			};
			swatch.run();
			color.addDisposeListener(e -> {
				if (color.getImage() != null)
					color.getImage().dispose();
			});
			color.addListener(SWT.Selection, e -> {
				org.eclipse.swt.widgets.ColorDialog cd = new org.eclipse.swt.widgets.ColorDialog(shell_);
				cd.setRGB(tvColors[n]);
				org.eclipse.swt.graphics.RGB rgb = cd.open();
				if (rgb != null) {
					tvColors[n] = rgb;
					swatch.run();
				}
			});
		}
		// alpha 52: the background: a colour (made darker where it is light, for the writing), a picture behind if wanted
		// in a row of its own across the group: its path field must not widen the columns of the four buttons above
		Composite tvBackRow = new Composite(tvGroup, SWT.NONE);
		tvBackRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 6, 1)); // not grabbing: ends with the colour buttons above
		GridLayout backLayout = new GridLayout(5, false);
		backLayout.marginWidth = 0;
		backLayout.marginHeight = 0;
		tvBackRow.setLayout(backLayout);
		Label tvBackLabel = new Label(tvBackRow, SWT.NONE);
		tvBackLabel.setText(TextService.getInstance().get("dialog.settings.tv.background"));
		org.eclipse.swt.graphics.RGB[] tvBack = {org.dbgl.gui.tv.TvMode.background()};
		Button tvBackColor = new Button(tvBackRow, SWT.PUSH);
		tvBackColor.setText(TextService.getInstance().get("dialog.settings.tv.color"));
		Runnable backSwatch = () -> {
			org.eclipse.swt.graphics.Image old = tvBackColor.getImage();
			org.eclipse.swt.graphics.Image img = new org.eclipse.swt.graphics.Image(shell_.getDisplay(), 16, 12);
			org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(img);
			gc.setBackground(new org.eclipse.swt.graphics.Color(tvBack[0]));
			gc.fillRectangle(0, 0, 16, 12);
			gc.dispose();
			tvBackColor.setImage(img);
			if (old != null)
				old.dispose();
		};
		backSwatch.run();
		tvBackColor.addDisposeListener(e -> {
			if (tvBackColor.getImage() != null)
				tvBackColor.getImage().dispose();
		});
		tvBackColor.setToolTipText(TextService.getInstance().get("dialog.settings.tv.background.tooltip"));
		tvBackColor.addListener(SWT.Selection, e -> {
			org.eclipse.swt.widgets.ColorDialog cd = new org.eclipse.swt.widgets.ColorDialog(shell_);
			cd.setRGB(tvBack[0]);
			org.eclipse.swt.graphics.RGB rgb = cd.open();
			if (rgb != null) {
				tvBack[0] = org.dbgl.gui.tv.TvMode.readable(rgb);
				backSwatch.run();
			}
		});
		Text tvBackImage = new Text(tvBackRow, SWT.BORDER);
		tvBackImage.setText(settings_.getValue("gui", "tvbackgroundimage"));
		tvBackImage.setMessage(TextService.getInstance().get("dialog.settings.tv.background.noimage"));
		GridData backImageData = new GridData(SWT.FILL, SWT.CENTER, true, false); // as wide as the room up to the colour buttons above
		backImageData.widthHint = 80;
		tvBackImage.setLayoutData(backImageData);
		Button tvBackBrowse = new Button(tvBackRow, SWT.PUSH);
		tvBackBrowse.setText(TextService.getInstance().get("dialog.settings.tv.background.image"));
		tvBackBrowse.addListener(SWT.Selection, e -> {
			org.eclipse.swt.widgets.FileDialog fd = new org.eclipse.swt.widgets.FileDialog(shell_, SWT.OPEN);
			fd.setFilterExtensions(new String[] {"*.png;*.jpg;*.jpeg;*.gif;*.bmp", "*.*"});
			if (!tvBackImage.getText().isBlank())
				fd.setFilterPath(new java.io.File(tvBackImage.getText()).getParent());
			String chosen = fd.open();
			if (chosen != null)
				tvBackImage.setText(chosen);
		});
		Button tvBackReset = new Button(tvBackRow, SWT.PUSH);
		tvBackReset.setText(TextService.getInstance().get("dialog.settings.tv.background.reset"));
		tvBackReset.addListener(SWT.Selection, e -> {
			tvBack[0] = new org.eclipse.swt.graphics.RGB(18, 18, 20);
			backSwatch.run();
			tvBackImage.setText("");
		});
		// alpha 49: the disk space of the chosen game
		Label tvDiskLabel = new Label(tvGroup, SWT.NONE);
		tvDiskLabel.setText(TextService.getInstance().get("dialog.settings.tv.diskspace"));
		tvDiskLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));
		Button tvDiskSpace = new Button(tvGroup, SWT.CHECK);
		tvDiskSpace.setSelection(settings_.getBooleanValue("gui", "showdiskspaceintv"));
		tvDiskSpace.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));
		// alpha 48: the play time below the chosen game
		Label tvPlayLabel = new Label(tvGroup, SWT.NONE);
		tvPlayLabel.setText(TextService.getInstance().get("dialog.settings.tv.playtime"));
		tvPlayLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));
		Button tvPlayTime = new Button(tvGroup, SWT.CHECK);
		tvPlayTime.setSelection(settings_.getBooleanValue("gui", "showplaytimeintv"));
		tvPlayTime.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));
		// alpha 49: the games of a series as one stack
		Label tvStacksLabel = new Label(tvGroup, SWT.NONE);
		tvStacksLabel.setText(TextService.getInstance().get("dialog.settings.tv.stacks"));
		tvStacksLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));
		Button tvStacks = new Button(tvGroup, SWT.CHECK);
		tvStacks.setSelection(settings_.getBooleanValue("gui", "tvstacks"));
		tvStacks.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));

		Font notesFont = stringToFont(shell_.getDisplay(), settings_.getValues("gui", "notesfont"), port.getFont());

		Group screenshots = Group_.on(guiComposite).layout(new GridLayout(3, false)).layoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 4, 1)).key("dialog.settings.screenshots").build();
		Scale screenshotsHeight = Chain.on(screenshots).lbl(l -> l.key("dialog.settings.height")).scl(
			s -> s.min(50).max(750).incr(25).pageIncr(100).select(settings_.getIntValue("gui", "screenshotsheight"))).scale();
		Label heightValue = Label_.on(screenshots).key("dialog.settings.px", new Object[] {screenshotsHeight.getSelection()}).build();
		screenshotsHeight.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent evt) {
				heightValue.setText(text_.get("dialog.settings.px", new Object[] {screenshotsHeight.getSelection()}));
				heightValue.pack();
				heightValue.getParent().layout(true);
			}
		});
		Button displayFilename = Chain.on(screenshots).lbl(l -> l.key("dialog.settings.screenshotsfilename")).but(
			b -> b.horSpan(2).select(settings_.getBooleanValue("gui", "screenshotsfilename"))).button();
		// alpha 52: the screenshots below the list (as before) or at the right, above the description
		Combo screenshotsPosition = Chain.on(screenshots).lbl(l -> l.key("dialog.settings.screenshotsposition")).cmb(
			c -> c.horSpan(2).items(new String[] {text_.get("dialog.settings.screenshotsposition.below"), text_.get("dialog.settings.screenshotsposition.right")})).combo();
		screenshotsPosition.select("right".equals(settings_.getValue("gui", "screenshotsposition")) ? 1: 0);

		Group screenshotsColumn = Group_.on(guiComposite).layout(new GridLayout(3, false)).layoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 4, 1)).key(
			"dialog.settings.screenshotscolumn").build();
		Scale screenshotsColumnHeight = Chain.on(screenshotsColumn).lbl(l -> l.key("dialog.settings.height")).scl(
			s -> s.min(16).max(200).incr(4).pageIncr(16).select(settings_.getIntValue("gui", "screenshotscolumnheight"))).scale();
		Label columnHeightValue = Label_.on(screenshotsColumn).key("dialog.settings.px", new Object[] {screenshotsColumnHeight.getSelection()}).build();
		screenshotsColumnHeight.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent evt) {
				columnHeightValue.setText(text_.get("dialog.settings.px", new Object[] {screenshotsColumnHeight.getSelection()}));
				columnHeightValue.pack();
				columnHeightValue.getParent().layout(true);
			}
		});
		Button stretch = Chain.on(screenshotsColumn).lbl(l -> l.key("dialog.settings.screenshotscolumnstretch")).but(
			b -> b.horSpan(2).select(settings_.getBooleanValue("gui", "screenshotscolumnstretch"))).button();
		Button keepAspectRatio = Chain.on(screenshotsColumn).lbl(l -> l.key("dialog.settings.screenshotscolumnkeepaspectratio")).but(
			b -> b.horSpan(2).select(settings_.getBooleanValue("gui", "screenshotscolumnkeepaspectratio"))).button();
		keepAspectRatio.setEnabled(stretch.getSelection());
		stretch.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				keepAspectRatio.setEnabled(stretch.getSelection());
			}
		});

		Group buttonsGroup = Group_.on(guiComposite).layout(new GridLayout(2, false)).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false)).key("dialog.settings.buttons").build();
		Combo buttonDisplay = Chain.on(buttonsGroup).lbl(l -> l.key("dialog.settings.display")).cmb(c -> c.items(
			new String[] {text_.get("dialog.settings.displaybuttonimageandtext"), text_.get("dialog.settings.displaybuttontextonly"), text_.get("dialog.settings.displaybuttonimageonly")}).select(
				settings_.getIntValue("gui", "buttondisplay"))).combo();

		Group linkingGroup = Group_.on(guiComposite).layout(new GridLayout(2, false)).key("dialog.settings.linking").build();
		Combo linkingSelector = Chain.on(linkingGroup).lbl(l -> l.key("dialog.settings.linking.tofile")).cmb(
			c -> c.items(Arrays.asList(TextService.getInstance().get("dialog.settings.linking.byurl"), TextService.getInstance().get("dialog.settings.linking.bypath"))).select(
				settings_.getIntValue("gui", "linking"))).combo();

		Group notesGroup = Group_.on(guiComposite).layout(new GridLayout(2, false)).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false)).key("dialog.profile.notes").build();
		Button fontButton = Chain.on(notesGroup).lbl(l -> l.key("dialog.settings.font")).but(b -> b.text().txt(notesFont.getFontData()[0].getName()).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Button fntButton = (Button)e.getSource();
				FontDialog fd = new FontDialog(shell_, SWT.NONE);
				fd.setFontList(fntButton.getFont().getFontData());
				FontData newFont = fd.open();
				if (newFont != null) {
					Font f = fntButton.getFont();
					fntButton.setText(newFont.getName());
					fntButton.setFont(new Font(shell_.getDisplay(), newFont));
					f.dispose();
					notesGroup.setSize(notesGroup.computeSize(SWT.DEFAULT, SWT.DEFAULT));
					guiComposite.layout();
				}
			}
		})).button();
		fontButton.addDisposeListener(e -> fontButton.getFont().dispose());
		fontButton.setFont(notesFont);

		Group themeGroup = Group_.on(guiComposite).layout(new GridLayout(2, false)).key("dialog.settings.colors").build();
		Combo themeSelector = Chain.on(themeGroup).lbl(l -> l.key("dialog.settings.theme")).cmb(
			c -> c.items(Arrays.asList(TextService.getInstance().get("dialog.settings.theme.default"), TextService.getInstance().get("dialog.settings.theme.forcedarkmode"),
				TextService.getInstance().get("dialog.settings.theme.system"), TextService.getInstance().get("dialog.settings.theme.highcontrast"))).select(
					settings_.getIntValue("gui", "theme"))).combo();
		// alpha 48: a box with its text over both columns (the drop-down stays next to its short label)
		Button ownFrames = new Button(themeGroup, SWT.CHECK);
		ownFrames.setText(TextService.getInstance().get("dialog.settings.theme.ownframes"));
		ownFrames.setToolTipText(TextService.getInstance().get("dialog.settings.theme.ownframes.tooltip"));
		ownFrames.setSelection(settings_.getBooleanValue("gui", "darkownframes"));
		ownFrames.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		// alpha 49: only the dark mode uses them (dark, or as the system: that may be dark)
		Runnable ownFramesState = () -> ownFrames.setEnabled(org.dbgl.util.SystemUtils.IS_WINDOWS && ((themeSelector.getSelectionIndex() == 1) || (themeSelector.getSelectionIndex() == 2)));
		ownFramesState.run();
		themeSelector.addListener(SWT.Selection, e -> ownFramesState.run());

		// alpha 48: the row of small groups right below the profile overview, the screenshots after them
		for (Group g: new Group[] {buttonsGroup, linkingGroup, notesGroup, themeGroup})
			g.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false)); // the same height in their row
		buttonsGroup.moveAbove(overviewGroup); // alpha 49: controls, links, information and colours at the very top
		linkingGroup.moveBelow(buttonsGroup);
		notesGroup.moveBelow(linkingGroup);
		themeGroup.moveBelow(notesGroup);

		Composite enginesComposite = createTabWithComposite("dialog.settings.tab.engines", new FillLayout());

		int nrOfEngines = SettingsService.availableWebSearchEngines().size();
		Button[] setTitle = new Button[nrOfEngines];
		Button[] setDev = new Button[nrOfEngines];
		Button[] setPub = new Button[nrOfEngines];
		Button[] setYear = new Button[nrOfEngines];
		Button[] setGenre = new Button[nrOfEngines];
		Button[] setLink = new Button[nrOfEngines];
		Button[] setRank = new Button[nrOfEngines];
		Button[] setDescr = new Button[nrOfEngines];
		Button[] allRegionsCoverArt = new Button[nrOfEngines];
		Button[] chooseCoverArt = new Button[nrOfEngines];
		Button[] chooseScreenshot = new Button[nrOfEngines];
		Spinner[] maxCoverArt = new Spinner[nrOfEngines];
		Spinner[] maxScreenshots = new Spinner[nrOfEngines];
		Text[] apiKeys = new Text[nrOfEngines];
		Text[] apiSecrets = new Text[nrOfEngines];
		Text[] platformFilterValues = new Text[nrOfEngines];

		CTabFolder enginesTabFolder = CTabFolder_.on(enginesComposite).ctrl();

		for (int i = 0; i < nrOfEngines; i++) {
			WebSearchEngine engine = SettingsService.availableWebSearchEngines().get(i);

			Composite engineComposite = Composite_.on(enginesTabFolder).layout(new GridLayout()).tab("dialog.settings.tab." + engine.getSimpleName()).build();

			Group consultGroup = Group_.on(engineComposite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).layout(new GridLayout(3, false)).txt(
				text_.get("dialog.settings.consult", new String[] {engine.getName()})).build();
			
			if (engine instanceof TheGamesDBSearchEngine || engine instanceof org.dbgl.util.searchengine.SteamGridDbSearchEngine || engine instanceof MobyGamesSearchEngine || engine instanceof org.dbgl.util.searchengine.RawgSearchEngine) {
				apiKeys[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.apikey")).txt(
						t -> t.horSpan(2).val(settings_.getValue(engine.getSimpleName(), "api_key"))).text();
			}
			if (engine instanceof org.dbgl.util.searchengine.ScreenScraperSearchEngine) {
				apiKeys[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.screenscraper.user")).txt(
						t -> t.horSpan(2).val(settings_.getValue(engine.getSimpleName(), org.dbgl.util.searchengine.ScreenScraperSearchEngine.USER))).text();
				apiSecrets[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.screenscraper.password")).txt(
						t -> t.horSpan(2).val(settings_.getValue(engine.getSimpleName(), org.dbgl.util.searchengine.ScreenScraperSearchEngine.PASSWORD))).text();
				apiSecrets[i].setEchoChar('\u25CF'); // the password is not shown
			}
			if (engine instanceof org.dbgl.util.searchengine.IgdbSearchEngine) {
				apiKeys[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.igdb.clientid")).txt(
						t -> t.horSpan(2).val(settings_.getValue(engine.getSimpleName(), org.dbgl.util.searchengine.IgdbSearchEngine.CLIENT_ID))).text();
				apiSecrets[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.igdb.clientsecret")).txt(
						t -> t.horSpan(2).val(settings_.getValue(engine.getSimpleName(), org.dbgl.util.searchengine.IgdbSearchEngine.CLIENT_SECRET))).text();
				apiSecrets[i].setEchoChar('\u25CF'); // the secret is not shown (alpha 48)
			}
			{
				// where to get the key: a link, and what this source gives (for every source since alpha 45)
				org.eclipse.swt.widgets.Link how = new org.eclipse.swt.widgets.Link(consultGroup, SWT.WRAP);
				GridData howData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
				howData.widthHint = 480;
				how.setLayoutData(howData);
				// MobyGames: the API key is no longer free (other text since alpha 44)
				how.setText(text_.get("dialog.settings." + engine.getSimpleName() + (engine instanceof MobyGamesSearchEngine ? ".howto2": ".howto")));
				how.addListener(SWT.Selection, e -> org.dbgl.util.SystemUtils.openForBrowsing(e.text, shell_));
			}
			
			setTitle[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.settitle")).but(b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_title"))).button();
			setDev[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setdeveloper")).but(
				b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_developer"))).button();
			if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof TheGamesDBSearchEngine || engine instanceof org.dbgl.util.searchengine.IgdbSearchEngine || engine instanceof org.dbgl.util.searchengine.RawgSearchEngine || engine instanceof org.dbgl.util.searchengine.ScreenScraperSearchEngine) {
				setPub[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setpublisher")).but(
					b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_publisher"))).button();
			}
			setYear[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setyear")).but(b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_year"))).button();
			setGenre[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setgenre")).but(b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_genre"))).button();
			String name = engine instanceof MetropolisSearchEngine ? MobyGamesSearchEngine.getInstance().getName(): engine.getName();
			setLink[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setlink", name)).but(b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_link"))).button();
			if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof org.dbgl.util.searchengine.IgdbSearchEngine || engine instanceof org.dbgl.util.searchengine.RawgSearchEngine || engine instanceof org.dbgl.util.searchengine.ScreenScraperSearchEngine) {
				setRank[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setrank", columnNames_[Constants.RO_COLUMN_NAMES + 8])).but(
					b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_rank"))).button();
			}
			if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof TheGamesDBSearchEngine || engine instanceof org.dbgl.util.searchengine.IgdbSearchEngine || engine instanceof org.dbgl.util.searchengine.RawgSearchEngine || engine instanceof org.dbgl.util.searchengine.ScreenScraperSearchEngine) {
				setDescr[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setdescription")).but(
					b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_description"))).button();
			}
			if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof TheGamesDBSearchEngine || engine instanceof org.dbgl.util.searchengine.IgdbSearchEngine || engine instanceof org.dbgl.util.searchengine.RawgSearchEngine || engine instanceof org.dbgl.util.searchengine.ScreenScraperSearchEngine || engine instanceof org.dbgl.util.searchengine.SteamGridDbSearchEngine) {
				chooseCoverArt[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.choosecoverart")).but(
					b -> b.select(settings_.getBooleanValue(engine.getSimpleName(), "choose_coverart"))).button();

				Composite comp = Composite_.on(consultGroup).layoutData(new GridData_().horizontalIndent(40).build()).innerLayout(2).build();
				if (engine instanceof MetropolisSearchEngine) {
					allRegionsCoverArt[i] = Chain.on(comp).lbl(l -> l.key("dialog.settings.allregionscoverart")).but(
						b -> b.select(settings_.getBooleanValue(engine.getSimpleName(), "force_all_regions_coverart"))).button();
				}
			}
			chooseScreenshot[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.choosescreenshot")).but(
				b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "choose_screenshot"))).button();
			if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof TheGamesDBSearchEngine || engine instanceof org.dbgl.util.searchengine.IgdbSearchEngine || engine instanceof org.dbgl.util.searchengine.RawgSearchEngine || engine instanceof org.dbgl.util.searchengine.ScreenScraperSearchEngine || engine instanceof org.dbgl.util.searchengine.SteamGridDbSearchEngine) {
				maxCoverArt[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.multieditmaxcoverart")).spn(
					s -> s.min(0).horSpan(2).select(settings_.getIntValue(engine.getSimpleName(), "multi_max_coverart"))).spinner();
			}
			maxScreenshots[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.multieditmaxscreenshot")).spn(
				s -> s.min(0).horSpan(2).select(settings_.getIntValue(engine.getSimpleName(), "multi_max_screenshot"))).spinner();
			if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof PouetSearchEngine) {
				platformFilterValues[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.platformfilter")).txt(
					t -> t.multi().horSpan(2).val(settings_.getMultilineValue(engine.getSimpleName(), "platform_filter", values.getLineDelimiter()))).text();
			} else {
				platformFilterValues[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.platformfilter")).txt(
					t -> t.horSpan(2).val(settings_.getValue(engine.getSimpleName(), "platform_filter"))).text();
			}
		}

		enginesTabFolder.setSelection(0);

		Composite envComposite = createTabWithComposite("dialog.settings.tab.environment", new GridLayout());
		moveTabToFront(guiComposite);
		moveTabToFront(generalComposite); // alpha 48: General first, then GUI
		moveTab(enginesComposite, 3); // test 15: the game databases after the profile overview

		Group envGroup = Group_.on(envComposite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).layout(new GridLayout(2, false)).key("dialog.settings.environment").build();
		Button enableEnv = Chain.on(envGroup).lbl(l -> l.key("dialog.settings.enableenvironment")).but(b -> b.select(settings_.getBooleanValue("environment", "use"))).button();
		Text envValues = Chain.on(envGroup).lbl(l -> l.key("dialog.settings.environmentvariables")).txt(
			t -> t.multi().val(settings_.getMultilineValue("environment", "value", values.getLineDelimiter()))).text();
		envValues.setEnabled(enableEnv.getSelection());
		enableEnv.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				envValues.setEnabled(enableEnv.getSelection());
			}
		});

		// alpha 49: every option tells in a tooltip what it does
		applyTips(shell_, tips());

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (!isValid(visibleColumnsTable, visibleColumns, getTabItemByComposite(profileTableComposite))) {
					return;
				}

				// alpha 48: Windows changes its menus to the other mode only in a new start of DBGL: asked, else the mode stays
				// (only when it really gets lighter or darker: "as the system" may be the same as before)
				if ((themeSelector.getSelectionIndex() != settings_.getIntValue("gui", "theme")) && ((DarkTheme.isDark(themeSelector.getSelectionIndex()) != DarkTheme.forced())
						|| ((themeSelector.getSelectionIndex() == 3) != DarkTheme.HIGH_CONTRAST))) {
					if (Mess_.on(shell_).key("dialog.settings.theme.restart").confirm())
						MainWindow.requestProcessRestart();
					else
						themeSelector.select(settings_.getIntValue("gui", "theme"));
				}

				boolean changedVisColumns = haveColumnsBeenChanged();
				if (changedVisColumns)
					updateColumnSettings(allColumnIds, visibleColumns);

				boolean requireRestart = changedVisColumns || (portEnabled.getSelection() != (settings_.getBooleanValue("communication", "port_enabled")))
						|| (!port.getText().equalsIgnoreCase(settings_.getValue("communication", "port"))) || (screenshotsHeight.getSelection() != settings_.getIntValue("gui", "screenshotsheight"))
						|| (screenshotsColumnHeight.getSelection() != settings_.getIntValue("gui", "screenshotscolumnheight"))
						|| (buttonDisplay.getSelectionIndex() != settings_.getIntValue("gui", "buttondisplay")) || (themeSelector.getSelectionIndex() != settings_.getIntValue("gui", "theme"))
						|| (ownFrames.getSelection() != settings_.getBooleanValue("gui", "darkownframes"));

				if ((!locales.get(localeCombo.getText()).getLanguage().equalsIgnoreCase(settings_.getValue("locale", "language")))
						|| (!locales.get(localeCombo.getText()).getCountry().equalsIgnoreCase(settings_.getValue("locale", "country")))
						|| (!locales.get(localeCombo.getText()).getVariant().equalsIgnoreCase(settings_.getValue("locale", "variant")))) {
					settings_.setValue("locale", "language", locales.get(localeCombo.getText()).getLanguage());
					settings_.setValue("locale", "country", locales.get(localeCombo.getText()).getCountry());
					settings_.setValue("locale", "variant", locales.get(localeCombo.getText()).getVariant());
					text_.refresh();
					requireRestart = true;
				}

				settings_.setBooleanValue("dosbox", "hideconsole", console.getSelection());
				settings_.setBooleanValue("gui", "hideunsupportedsettings", hideUnsupported.getSelection());
				settings_.setBooleanValue("dosbox", "detectfailedstart", detectFailedStart.getSelection());
				settings_.setBooleanValue("dosbox", "checkversionsatstartup", checkVersions.getSelection());
				settings_.setBooleanValue("dosbox", "checkforupdates", checkForUpdates.getSelection());
				settings_.setBooleanValue("dosbox", "checkbeforestart", checkBeforeStart.getSelection());
				settings_.setBooleanValue("dosbox", "ownsavestates", ownSavestates.getSelection());
				settings_.setBooleanValue("dosbox", "noprimaryconf", noPrimaryConf.getSelection());
				boolean hidingChanged = (hideWin3Missing.getSelection() != settings_.getBooleanValue("dosbox", "hidewin3missing"))
						|| (hideWin3Programs.getSelection() != settings_.getBooleanValue("dosbox", "hidewin3programs"))
						|| (hideWin3Start.getSelection() != settings_.getBooleanValue("dosbox", "hidewin3start"))
						|| (hideShortSessions.getSelection() != settings_.getBooleanValue("dosbox", "hideshortsessions"));
				settings_.setBooleanValue("dosbox", "hidewin3missing", hideWin3Missing.getSelection());
				settings_.setBooleanValue("dosbox", "hidewin3programs", hideWin3Programs.getSelection());
				settings_.setBooleanValue("dosbox", "hidewin3start", hideWin3Start.getSelection());
				settings_.setBooleanValue("dosbox", "hideshortsessions", hideShortSessions.getSelection());
				if (hidingChanged)
					org.dbgl.util.ProfileWarnings.forgetAll(); // the signs in the list without (or with) these notes
				settings_.setBooleanValue(org.dbgl.gui.controls.CharsetQuestion.SECTION, org.dbgl.gui.controls.CharsetQuestion.ITEM, !askCharset.getSelection());
				settings_.setBooleanValue("gui", "profilewarnings", profileWarnings.getSelection());
				settings_.setBooleanValue("gui", "profilehints", profileHints.getSelection());
				settings_.setBooleanValue("gui", "longtooltips", longToolTips.getSelection());
				settings_.setBooleanValue(org.dbgl.util.EditionUpdateChecker.SECTION, org.dbgl.util.EditionUpdateChecker.CHECK, editionCheck.getSelection());
				settings_.setBooleanValue(org.dbgl.util.EditionUpdateChecker.SECTION, org.dbgl.util.EditionUpdateChecker.PRERELEASES, editionPre.getSelection());
				settings_.setValue(org.dbgl.util.UnknownAutoexecLines.SECTION, org.dbgl.util.UnknownAutoexecLines.MODE,
					unknownMode.getSelectionIndex() == 1 ? org.dbgl.util.UnknownAutoexecLines.ASK: org.dbgl.util.UnknownAutoexecLines.AUTO);
				settings_.setBooleanValue(org.dbgl.util.UnknownAutoexecLines.SECTION, org.dbgl.util.UnknownAutoexecLines.NO_MARKERS, unknownNoMarkers.getSelection());
				org.dbgl.gui.LongToolTips.setEnabled(longToolTips.getSelection());
				settings_.setValue("gui", "extrasexpand", expandModes[Math.max(0, extrasExpand.getSelectionIndex())]);
				settings_.setBooleanValue("gui", "extrasexpandonextraspages", extrasOnExtrasPages.getSelection());
				settings_.setBooleanValue("gui", "extrascountchanges", extrasCount.getSelection());
				settings_.setBooleanValue("gui", "showplaytimeininfo", showPlayTime.getSelection());
				settings_.setBooleanValue("gui", org.dbgl.util.Scummvm.SETTING, scummvmWanted.getSelection());
				settings_.setBooleanValue("gui", "showdiskspaceininfo", showDiskSpace.getSelection());
				settings_.setBooleanValue("gui", "showdiskspaceinnotes", showDiskSpaceInNotes.getSelection());
				settings_.setBooleanValue("gui", "showdiskspaceintv", tvDiskSpace.getSelection());
				settings_.setValue("gui", "tvbackground", tvBack[0].red + "," + tvBack[0].green + "," + tvBack[0].blue); // alpha 52
				settings_.setValue("gui", "tvbackgroundimage", tvBackImage.getText().trim());
				settings_.setBooleanValue("gui", "showplaytimeinnotes", showPlayTimeInNotes.getSelection());
				for (int i = 0; i < 3; i++)
					settings_.setIntValue("gui", shotKeys[i], Math.max(0, shotCombos[i].getSelectionIndex()));
				settings_.setIntValue("gui", "extrasexpandmax", extrasExpandMax.getSelection());
				if (defaultEngine.getSelectionIndex() >= 0)
					settings_.setValue("gui", "searchengine", engines.get(defaultEngine.getSelectionIndex()).getSimpleName());
				settings_.setValue("backup", "auto", backupModes[Math.max(0, autoBackup.getSelectionIndex())]);
				settings_.setIntValue("backup", "keep", backupKeep.getSelection());
				setBackupDir(backupDirBefore, backupDir.getText().trim());
				settings_.setBooleanValue("backup", "captures", backupCaptures.getSelection());
				settings_.setBooleanValue("backup", "savegames", backupSavegames.getSelection());
				settings_.setValue(org.dbgl.util.SecondBackupPlace.SECTION, org.dbgl.util.SecondBackupPlace.PLACE, secondPlace.getText().trim());
				settings_.setBooleanValue(org.dbgl.util.SecondBackupPlace.SECTION, org.dbgl.util.SecondBackupPlace.SAVEGAMES, secondSavegames.getSelection());
				settings_.setBooleanValue(org.dbgl.util.SecondBackupPlace.SECTION, org.dbgl.util.SecondBackupPlace.AUTO, secondAuto.getSelection());
				settings_.setBooleanValue("communication", "port_enabled", portEnabled.getSelection());
				settings_.setValue("communication", "port", port.getText());
				settings_.setIntValue("profiledefaults", "confpath", confLocation.getSelectionIndex());
				settings_.setIntValue("profiledefaults", "conffile", confFilename.getSelectionIndex());
				settings_.setIntValue("profiledefaults", "capturespath", capturesLocation.getSelectionIndex());
				settings_.setBooleanValue(org.dbgl.util.ExtrasFinder.SECTION, org.dbgl.util.ExtrasFinder.ON_ADD, findExtras.getSelection());
				if (allSettings.getSelection() != allSettingsBefore)
					changeAllSettings(allSettings.getSelection());
				for (int i = 0; i < columnNames_.length; i++) {
					settings_.setBooleanValue("gui", "column" + (i + 1) + "visible", visibleColumns[allColumnIds.indexOf(i)].getChecked());
					settings_.setIntValue("gui", "column" + (i + 1) + "align", aligns[allColumnIds.indexOf(i)]);
				}
				settings_.setBooleanValue("gui", "autosortonupdate", autosort.getSelection());
				for (int i = 0; i < Constants.EDIT_COLUMN_NAMES_1; i++) {
					settings_.setValue("gui", "custom" + (i + 1), visibleColumns[allColumnIds.indexOf(i + Constants.RO_COLUMN_NAMES)].getText());
				}
				for (int i = 0; i < Constants.EDIT_COLUMN_NAMES_2; i++) {
					settings_.setValue("gui", "custom" + (i + Constants.EDIT_COLUMN_NAMES_1 + 1),
						visibleColumns[allColumnIds.indexOf(i + Constants.RO_COLUMN_NAMES + Constants.EDIT_COLUMN_NAMES_1 + Constants.STATS_COLUMN_NAMES)].getText());
				}
				settings_.setIntValue("gui", "screenshotsheight", screenshotsHeight.getSelection());
				settings_.setBooleanValue("gui", "screenshotsfilename", displayFilename.getSelection());
				settings_.setBooleanValue("gui", "offernextpart", offerNextPart.getSelection());
				settings_.setValue("gui", "screenshotsposition", screenshotsPosition.getSelectionIndex() == 1 ? "right": "below");
				settings_.setIntValue("gui", "noimagestab", noImagesTab.getSelectionIndex());
				settings_.setIntValue("gui", "screenshotscolumnheight", screenshotsColumnHeight.getSelection());
				settings_.setBooleanValue("gui", "screenshotscolumnstretch", stretch.getSelection());
				settings_.setBooleanValue("gui", "screenshotscolumnkeepaspectratio", keepAspectRatio.getSelection());

				Rectangle rec = shell_.getBounds();
				settings_.setIntValue("gui", "settingsdialog_width", rec.width);
				settings_.setIntValue("gui", "settingsdialog_height", rec.height);
				settings_.setIntValue("gui", "buttondisplay", buttonDisplay.getSelectionIndex());
				settings_.setMultilineValue("gui", "notesfont", fontToString(fontButton.getFont()), "|");
				settings_.setIntValue("gui", "theme", themeSelector.getSelectionIndex());
				settings_.setBooleanValue("gui", "darkownframes", ownFrames.getSelection());
				settings_.setIntValue("gui", "tvhints", tvHints.getSelectionIndex());
				settings_.setBooleanValue("gui", "showplaytimeintv", tvPlayTime.getSelection());
				settings_.setBooleanValue("gui", "tvstacks", tvStacks.getSelection());
				for (int i = 0; i < 4; i++) {
					String label = tvLabels[i].getText().strip();
					settings_.setValue("gui", "tvbutton" + (i + 1), label.isEmpty() ? org.dbgl.gui.tv.TvMode.DEFAULT_BUTTONS[i]: label);
					settings_.setValue("gui", "tvbutton" + (i + 1) + "color", tvColors[i].red + "," + tvColors[i].green + "," + tvColors[i].blue);
				}
				settings_.setIntValue("gui", "linking", linkingSelector.getSelectionIndex());

				for (int i = 0; i < nrOfEngines; i++) {
					WebSearchEngine engine = SettingsService.availableWebSearchEngines().get(i);
					settings_.setBooleanValue(engine.getSimpleName(), "set_title", setTitle[i].getSelection());
					settings_.setBooleanValue(engine.getSimpleName(), "set_developer", setDev[i].getSelection());
					if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof TheGamesDBSearchEngine || engine instanceof org.dbgl.util.searchengine.IgdbSearchEngine || engine instanceof org.dbgl.util.searchengine.RawgSearchEngine || engine instanceof org.dbgl.util.searchengine.ScreenScraperSearchEngine) {
						settings_.setBooleanValue(engine.getSimpleName(), "set_publisher", setPub[i].getSelection());
						settings_.setBooleanValue(engine.getSimpleName(), "set_description", setDescr[i].getSelection());
					}
					settings_.setBooleanValue(engine.getSimpleName(), "set_year", setYear[i].getSelection());
					settings_.setBooleanValue(engine.getSimpleName(), "set_genre", setGenre[i].getSelection());
					settings_.setBooleanValue(engine.getSimpleName(), "set_link", setLink[i].getSelection());
					if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof org.dbgl.util.searchengine.IgdbSearchEngine || engine instanceof org.dbgl.util.searchengine.RawgSearchEngine || engine instanceof org.dbgl.util.searchengine.ScreenScraperSearchEngine) {
						settings_.setBooleanValue(engine.getSimpleName(), "set_rank", setRank[i].getSelection());
					}
					if (engine instanceof MetropolisSearchEngine) {
						settings_.setBooleanValue(engine.getSimpleName(), "force_all_regions_coverart", allRegionsCoverArt[i].getSelection());
					}
					if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof TheGamesDBSearchEngine || engine instanceof org.dbgl.util.searchengine.IgdbSearchEngine || engine instanceof org.dbgl.util.searchengine.RawgSearchEngine || engine instanceof org.dbgl.util.searchengine.ScreenScraperSearchEngine || engine instanceof org.dbgl.util.searchengine.SteamGridDbSearchEngine) {
						settings_.setBooleanValue(engine.getSimpleName(), "choose_coverart", chooseCoverArt[i].getSelection());
						settings_.setIntValue(engine.getSimpleName(), "multi_max_coverart", maxCoverArt[i].getSelection());
					}
					if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof PouetSearchEngine || engine instanceof TheGamesDBSearchEngine || engine instanceof org.dbgl.util.searchengine.IgdbSearchEngine || engine instanceof org.dbgl.util.searchengine.RawgSearchEngine || engine instanceof org.dbgl.util.searchengine.ScreenScraperSearchEngine || engine instanceof org.dbgl.util.searchengine.SteamGridDbSearchEngine) {
						settings_.setBooleanValue(engine.getSimpleName(), "choose_screenshot", chooseScreenshot[i].getSelection());
						settings_.setIntValue(engine.getSimpleName(), "multi_max_screenshot", maxScreenshots[i].getSelection());
					}
					if (engine instanceof TheGamesDBSearchEngine || engine instanceof org.dbgl.util.searchengine.SteamGridDbSearchEngine || engine instanceof MobyGamesSearchEngine || engine instanceof org.dbgl.util.searchengine.RawgSearchEngine) {
						settings_.setValue(engine.getSimpleName(), "api_key", apiKeys[i].getText().trim());
					}
					if (engine instanceof org.dbgl.util.searchengine.ScreenScraperSearchEngine) {
						settings_.setValue(engine.getSimpleName(), org.dbgl.util.searchengine.ScreenScraperSearchEngine.USER, apiKeys[i].getText().trim());
						settings_.setValue(engine.getSimpleName(), org.dbgl.util.searchengine.ScreenScraperSearchEngine.PASSWORD, apiSecrets[i].getText().trim());
					}
					if (engine instanceof org.dbgl.util.searchengine.IgdbSearchEngine) {
						settings_.setValue(engine.getSimpleName(), org.dbgl.util.searchengine.IgdbSearchEngine.CLIENT_ID, apiKeys[i].getText().trim());
						settings_.setValue(engine.getSimpleName(), org.dbgl.util.searchengine.IgdbSearchEngine.CLIENT_SECRET, apiSecrets[i].getText().trim());
					}
					if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof PouetSearchEngine || engine instanceof TheGamesDBSearchEngine || engine instanceof org.dbgl.util.searchengine.IgdbSearchEngine || engine instanceof org.dbgl.util.searchengine.RawgSearchEngine || engine instanceof org.dbgl.util.searchengine.ScreenScraperSearchEngine || engine instanceof org.dbgl.util.searchengine.SteamGridDbSearchEngine) {
						String vOld = settings_.getMultilineValue(engine.getSimpleName(), "platform_filter", platformFilterValues[i].getLineDelimiter());
						String vNew = platformFilterValues[i].getText();
						if (!vOld.equals(vNew)) {
							try {
								engine.updatePlatforms(StringRelatedUtils.textAreaToStringArray(vNew, platformFilterValues[i].getLineDelimiter()));
							} catch (IOException e) {
								// ignore for now
							}
							settings_.setMultilineValue(engine.getSimpleName(), "platform_filter", vNew, platformFilterValues[i].getLineDelimiter());
						}
					} else {
						settings_.setValue(engine.getSimpleName(), "platform_filter", platformFilterValues[i].getText());
					}
				}

				settings_.setBooleanValue("environment", "use", enableEnv.getSelection());
				settings_.setMultilineValue("environment", "value", envValues.getText(), envValues.getLineDelimiter());

				updateOptionsMap(optionsMap, optionsList, lastOptionSelection, values);
				for (Map.Entry<String, String> entry: optionsMap.entrySet())
					settings_.setMultilineValue("profile", entry.getKey(), entry.getValue(), values.getLineDelimiter());

				result_ = requireRestart;
				shell_.close();
			}

			private boolean haveColumnsBeenChanged() {
				for (int i = 0; i < columnNames_.length; i++)
					if ((settings_.getBooleanValue("gui", "column" + (allColumnIds.get(i) + 1) + "visible") != visibleColumns[i].getChecked())
							|| (settings_.getIntValue("gui", "column" + (allColumnIds.get(i) + 1) + "align") != aligns[i])
							|| !columnNames_[allColumnIds.get(i)].equals(visibleColumns[i].getText()))
						return true;
				return false;
			}
		});
	}

	public static void updateOptionsMap(Map<String, String> optionsMap, org.eclipse.swt.widgets.List options, int lastOptionSelection, Text values) {
		if (lastOptionSelection != -1)
			optionsMap.put(options.getItem(lastOptionSelection), values.getText());
	}

	private void updateColumnSettings(List<Integer> allColumnIds, TableItem[] visibleColumns) {
		int[] sort = settings_.getIntValues("gui", "sortcolumn");
		boolean[] ascs = settings_.getBooleanValues("gui", "sortascending");
		List<Integer> sortColumnIDs = new ArrayList<>(sort.length);
		List<Boolean> sortColumnAscs = new ArrayList<>(sort.length);

		for (int i = 0; i < sort.length; i++) {
			if (visibleColumns[allColumnIds.indexOf(sort[i])].getChecked()) {
				sortColumnIDs.add(sort[i]);
				sortColumnAscs.add(ascs[i]);
			}
		}
		if (sortColumnIDs.isEmpty()) {
			OptionalInt firstVisibleColumn = IntStream.range(0, visibleColumns.length).filter(i -> visibleColumns[i].getChecked()).findFirst();
			if (firstVisibleColumn.isPresent()) {
				sortColumnIDs.add(allColumnIds.get(firstVisibleColumn.getAsInt()));
				sortColumnAscs.add(true);
			}
		}

		settings_.setIntValues("gui", "sortcolumn", ArrayUtils.toPrimitive(sortColumnIDs.toArray(new Integer[0])));
		settings_.setBooleanValues("gui", "sortascending", ArrayUtils.toPrimitive(sortColumnAscs.toArray(new Boolean[0])));

		List<Integer> visColumns = new ArrayList<>();
		for (int i = 0; i < columnNames_.length; i++)
			if (visibleColumns[i].getChecked())
				visColumns.add(allColumnIds.get(i));

		List<Integer> orderedVisColumns = new ArrayList<>(visColumns);
		Collections.sort(orderedVisColumns);

		List<Integer> colOrder = new ArrayList<>();
		for (int id: visColumns)
			colOrder.add(orderedVisColumns.indexOf(id));

		settings_.setValue("gui", "columnorder", StringUtils.join(colOrder, ' '));
	}

	static SortedMap<String, Locale> getLocales() { // alpha 53: also for the welcome wizard (was private)
		List<String> supportedLanguages = new ArrayList<>(Constants.SUPPORTED_LANGUAGES);
		File[] files = new File("./plugins/i18n").listFiles();
		if (files != null) {
			for (File file: files) {
				String name = file.getName();
				if (name.startsWith("MessagesBundle_") && name.endsWith(".properties")) {
					String code = name.substring("MessagesBundle_".length(), name.indexOf(".properties"));
					if (code.length() > 0) {
						supportedLanguages.add(code);
					}
				}
			}
		}

		SortedMap<String, Locale> allLocales = new TreeMap<>();
		for (Locale loc: Locale.getAvailableLocales())
			allLocales.put(loc.toString(), loc);

		SortedMap<String, Locale> locales = new TreeMap<>();

		for (String lang: supportedLanguages) {
			Locale loc = allLocales.get(lang);
			String variant = null;
			if (loc == null && StringUtils.countMatches(lang, "_") == 2) {
				String langWithoutVariant = Strings.CS.removeEnd(StringUtils.substringBeforeLast(lang, "_"), "_");
				variant = StringUtils.substringAfterLast(lang, "_");
				loc = allLocales.get(langWithoutVariant);
			}
			if (loc != null) {
				StringBuilder s = new StringBuilder(loc.getDisplayLanguage());
				s.append(" [").append(loc.getDisplayLanguage(loc)).append("]");
				if (loc.getCountry().length() > 0)
					s.append(" - ").append(loc.getDisplayCountry()).append(" [").append(loc.getDisplayCountry(loc)).append("]");
				if (variant != null) {
					s.append(" (").append(variant).append(')');
					loc = Locale.of(loc.getLanguage(), loc.getCountry(), variant);
				}
				locales.put(s.toString(), loc);
			}
		}

		return locales;
	}

	private List<Integer> getColumnIds() {
		List<Integer> visibleColumnIds = new ArrayList<>();
		for (int i = 0; i < columnNames_.length; i++)
			if (settings_.getBooleanValue("gui", "column" + (i + 1) + "visible"))
				visibleColumnIds.add(i);
		List<Integer> orderedVisibleColumnIds = new ArrayList<>();
		int[] columnOrder = settings_.getIntValues("gui", "columnorder");
		for (int element: columnOrder)
			orderedVisibleColumnIds.add(visibleColumnIds.get(element));
		List<Integer> remainingColumnIDs = new ArrayList<>();
		for (int i = 0; i < columnNames_.length; i++)
			if (!orderedVisibleColumnIds.contains(i))
				remainingColumnIDs.add(i);
		List<Integer> allColumnIds = new ArrayList<>(orderedVisibleColumnIds);
		allColumnIds.addAll(remainingColumnIDs);
		return allColumnIds;
	}

	/** alpha 48: this tab first (at the left); the one shown at first stays the same */
	/** alpha 48: the labels in the first column of the groups as wide as the widest, so their controls stand in one column */
	/** alpha 49: the options that had no tooltip: the key of the label and the tooltip that explains it */
	private static final String[][] TIPS = {{"languagecountry", "language"}, {"edition.prereleases2", "prereleases"}, {"hidestatuswindow", "hidestatuswindow"},
			{"detectfailedstart", "detectfailedstart"}, {"checkversionsatstartup", "checkversions"}, {"checkbeforestart", "checkbeforestart"}, {"unknownlines.mode", "unknownlines"},
			{"showplaytimeininfo", "playtimeininfo"}, {"hideunsupportedsettings", "hideunsupported"}, {"extrasexpand", "extrasexpand"}, {"extrascountchanges", "extrascount"},
			{"autobackup.when", "autobackupwhen"}, {"autobackup.keep", "autobackupkeep"}, {"autobackup.captures", "autobackupcaptures"}, {"autobackup.savegames", "autobackupsavegames"}, {"secondplace.savegames", "secondplacesavegames"}, {"secondplace.auto", "secondplaceauto"}, {"enableport", "enableport"}, {"port", "port"}, {"configfile", "configfile"},
			{"captures", "captures"}, {"allsettings.new", "allsettings"}, {"autosort", "autosort"}, {"showplaytimeinnotes", "playtimeinnotes"}, {"height", "height"},
			{"screenshotsfilename", "screenshotsfilename"}, {"screenshotsposition", "screenshotsposition"}, {"offernextpart", "offernextpart"}, {"screenshotscolumnstretch", "columnstretch"}, {"screenshotscolumnkeepaspectratio", "columnkeepaspect"},
			{"display", "display"}, {"linking.tofile", "linking"}, {"font", "font"}, {"theme", "theme"}, {"apikey", "apikey"}, {"screenscraper.user", "screenscraper"},
			{"screenscraper.password", "screenscraper"}, {"igdb.clientid", "igdb"}, {"igdb.clientsecret", "igdb"}, {"settitle", "setfield"}, {"setdeveloper", "setfield"},
			{"setpublisher", "setfield"}, {"setyear", "setfield"}, {"setgenre", "setfield"}, {"setlink", "setlink"}, {"setrank", "setrank"}, {"setdescription", "setdescription"},
			{"choosecoverart", "choosecoverart"}, {"allregionscoverart", "allregions"}, {"choosescreenshot", "choosescreenshot"}, {"multieditmaxcoverart", "multieditmax"},
			{"multieditmaxscreenshot", "multieditmax"}, {"platformfilter", "platformfilter"}, {"enableenvironment", "environment"}, {"environmentvariables", "environment"},
			{"noimages", "noimages"}, {"screenshots.list", "screenshotsrule"}, {"screenshots.tiles", "screenshotsrule"}, {"screenshots.boxes", "screenshotsrule"}, {"tv.stacks", "tvstacks"},
			{"tv.playtime", "tvplaytime"}, {"showdiskspaceininfo", "diskspace"}, {"showdiskspaceinnotes", "diskspace"}, {"tv.diskspace", "diskspace"}, {"tv.hints", "tvhints"}, {"tv.button", "tvbutton"}, {"options", "options"}, {"values", "values"}};

	/** alpha 49: the text of each such label (what is filled in later, like the name of an archive, as a gap) and its tooltip */
	private List<String[]> tips() {
		List<String[]> result = new ArrayList<>();
		for (String[] tip: TIPS) {
			String label = text_.get("dialog.settings." + tip[0], new Object[] {"\u0001"});
			int gap = label.indexOf('\u0001');
			result.add(new String[] {gap < 0 ? label: label.substring(0, gap), gap < 0 ? null: label.substring(gap + 1), text_.get("dialog.settings.tip." + tip[1])});
		}
		return result;
	}

	/** alpha 49: the tooltip of a label with this text, or null: the whole text, or its beginning and end around what was filled in */
	public static String tipFor(String label, List<String[]> tips) {
		for (String[] tip: tips) {
			if (tip[1] == null ? label.equals(tip[0]): ((label.length() > tip[0].length() + tip[1].length()) && label.startsWith(tip[0]) && label.endsWith(tip[1])))
				return tip[2];
		}
		return null;
	}

	/**
	 * alpha 49: gives the labels that have no tooltip theirs, and the same one to what the label stands for: the fields and buttons that follow it up to the next label. What has a
	 * tooltip of its own keeps it.
	 */
	private static void applyTips(Composite parent, List<String[]> tips) {
		String current = null;
		Control[] children = parent.getChildren();
		for (int i = 0; i < children.length; i++) {
			Control control = children[i];
			if (control instanceof Label) {
				String text = ((Label)control).getText();
				current = control.getToolTipText();
				if ((current == null) && StringUtils.isNotBlank(text) && (text.length() < 120)) {
					current = tipFor(text, tips);
					// a label of an option that explains itself in its own tooltip: the same one, or that of the field it follows ("... entries")
					if ((current == null) && (i + 1 < children.length) && !(children[i + 1] instanceof Label))
						current = children[i + 1].getToolTipText();
					if ((current == null) && (i > 0) && !(children[i - 1] instanceof Label))
						current = children[i - 1].getToolTipText();
					if (current != null)
						control.setToolTipText(current);
				}
			} else if ((control instanceof Composite) && (((Composite)control).getChildren().length > 0)) {
				// a group or another container with options of its own
				current = null;
				applyTips((Composite)control, tips);
			} else if ((current != null) && (control.getToolTipText() == null)) {
				control.setToolTipText(current);
			}
		}
	}

	private static void alignFirstColumns(Composite... groups) {
		java.util.List<org.eclipse.swt.widgets.Label> labels = new java.util.ArrayList<>();
		for (Composite group: groups) {
			int columns = ((GridLayout)group.getLayout()).numColumns, col = 0;
			for (org.eclipse.swt.widgets.Control c: group.getChildren()) {
				int span = (c.getLayoutData() instanceof GridData gd) ? gd.horizontalSpan: 1;
				if (col == 0 && span == 1 && c instanceof org.eclipse.swt.widgets.Label label) // not a hint over the whole width
					labels.add(label);
				col = (col + span) % columns;
			}
		}
		int width = labels.stream().mapToInt(l -> l.computeSize(SWT.DEFAULT, SWT.DEFAULT).x).max().orElse(0);
		for (org.eclipse.swt.widgets.Label label: labels) {
			GridData data = (label.getLayoutData() instanceof GridData gd) ? gd: new GridData();
			data.widthHint = width;
			label.setLayoutData(data);
		}
	}

	private void moveTabToFront(Composite composite) {
		moveTab(composite, 0);
	}

	/** @param index the place the tab gets (the others move on) */
	private void moveTab(Composite composite, int index) {
		CTabItem item = getTabItemByComposite(composite);
		if ((item == null) || (tabFolder_.indexOf(item) == index) || (index >= tabFolder_.getItemCount()))
			return;
		CTabItem selected = tabFolder_.getSelection();
		org.eclipse.swt.widgets.Control control = item.getControl();
		CTabItem moved = new CTabItem(tabFolder_, item.getStyle(), index);
		moved.setText(item.getText());
		moved.setImage(item.getImage());
		moved.setToolTipText(item.getToolTipText());
		item.setControl(null);
		item.dispose();
		moved.setControl(control);
		tabFolder_.setSelection((selected == item) ? moved: selected);
	}

	private boolean isValid(Table visibleColumnsTable, TableItem[] visibleColumns, CTabItem columnsTabItem) {
		Mess_.Builder mess = Mess_.on(shell_);
		if (Stream.of(visibleColumns).noneMatch(TableItem::getChecked)) {
			mess.key("dialog.settings.required.onevisiblecolumn").bind(visibleColumnsTable, columnsTabItem);
		}
		return mess.valid();
	}

	public static List<String> getConfLocations() {
		return Arrays.asList(TextService.getInstance().get("dialog.settings.confindbgldir"), TextService.getInstance().get("dialog.settings.confingamedir"));
	}

	public static List<String> getConfFilenames() {
		return Arrays.asList(TextService.getInstance().get("dialog.settings.conffilebyid"), TextService.getInstance().get("dialog.settings.conffilebytitle"));
	}

	public static List<String> getCapsLocations() {
		return Arrays.asList(TextService.getInstance().get("dialog.settings.capturestogether"), TextService.getInstance().get("dialog.settings.capturesseparate"));
	}
	/**
	 * Switch the option "all DOSBox settings in each profile": say clearly what it costs, and offer to bring the existing
	 * profiles in line now (fill them up, or take out again what equals the DOSBox configuration).
	 */
	/**
	 * alpha 54 (TEST3): another folder for the backups: set (the data folder's own: empty again), and the backups of the folder
	 * so far moved along if wanted
	 */
	private void setBackupDir(String before, String chosen) {
		java.io.File now = chosen.isEmpty() ? org.dbgl.service.BackupService.defaultAutoBackupDir(): new java.io.File(chosen);
		java.io.File was = new java.io.File(before);
		boolean same;
		try {
			same = now.getCanonicalFile().equals(was.getCanonicalFile());
		} catch (java.io.IOException e) {
			same = now.getAbsoluteFile().equals(was.getAbsoluteFile());
		}
		if (same)
			return;
		settings_.setValue("backup", "dir", now.getAbsoluteFile().equals(org.dbgl.service.BackupService.defaultAutoBackupDir().getAbsoluteFile()) ? "": now.getPath());
		java.util.List<java.io.File> there = was.isDirectory() ? org.dbgl.service.BackupService.ownBackupsIn(was): java.util.List.of();
		if (there.isEmpty())
			return;
		long bytes = there.stream().mapToLong(f -> f.isDirectory() ? org.apache.commons.io.FileUtils.sizeOfDirectory(f): f.length()).sum();
		if (!Mess_.on(shell_).key("dialog.settings.autobackup.move", new Object[] {there.size(), org.apache.commons.io.FileUtils.byteCountToDisplaySize(bytes), was.getPath(), now.getPath()}).confirm())
			return;
		try {
			int moved = org.dbgl.gui.controls.BusyTask.runToEnd(shell_, text_.get("dialog.settings.autobackup.moving"), () -> org.dbgl.service.BackupService.moveBackups(was, now));
			if (moved < there.size())
				Mess_.on(shell_).key("dialog.settings.autobackup.moved", new Object[] {moved, there.size() - moved, was.getPath()}).display();
		} catch (Exception e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	private void changeAllSettings(boolean on) {
		int profiles = 0;
		try {
			profiles = new org.dbgl.model.repository.ProfileRepository().list("", "", new org.dbgl.model.repository.DosboxVersionRepository().listAll()).size();
		} catch (java.sql.SQLException e) {
			e.printStackTrace();
		}
		// the default for new profiles; existing ones only when asked to, each profile can also be switched in its own window
		settings_.setBooleanValue(org.dbgl.util.FullConfProfiles.SECTION, org.dbgl.util.FullConfProfiles.KEY, on);
		if ((profiles == 0) || !Mess_.on(shell_).key(on ? "dialog.settings.allsettings.existingon": "dialog.settings.allsettings.existingoff", new Object[] {profiles}).confirm())
			return;
		try {
			int[] r = org.dbgl.gui.controls.BusyTask.run(shell_, text_.get("dialog.settings.allsettings.working"), () -> org.dbgl.util.FullConfProfiles.applyToAll(on));
			Mess_.on(shell_).key("dialog.settings.allsettings.done", new Object[] {r[0], r[1]}).display();
		} catch (org.dbgl.gui.controls.BusyTask.Cancelled e) {
			// the work goes on in the background; each profile is complete or not touched
		} catch (Exception e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}
}
