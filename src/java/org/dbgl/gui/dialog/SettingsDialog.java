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
		Locale locale = new Locale(settings_.getValue("locale", "language"), settings_.getValue("locale", "country"), settings_.getValue("locale", "variant"));
		String locString = locales.keySet().stream().filter(key -> locales.get(key).equals(locale)).findFirst().orElse(StringUtils.EMPTY);

		Group dosboxGroup = Group_.on(generalComposite).layout(new GridLayout(2, false)).key("dialog.settings.dosbox").build();
		Button console = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.hidestatuswindow")).but(b -> b.select(settings_.getBooleanValue("dosbox", "hideconsole"))).button();
		Button hideUnsupported = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.hideunsupportedsettings")).but(b -> b.select(settings_.getBooleanValue("gui", "hideunsupportedsettings"))).button();
		Button detectFailedStart = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.detectfailedstart")).but(b -> b.select(settings_.getBooleanValue("dosbox", "detectfailedstart"))).button();
		Button checkVersions = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.checkversionsatstartup")).but(b -> b.select(settings_.getBooleanValue("dosbox", "checkversionsatstartup"))).button();
		Button checkForUpdates = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.checkforupdates")).but(b -> b.tooltip("dialog.settings.checkforupdates.tooltip").select(settings_.getBooleanValue("dosbox", "checkforupdates"))).button();
		Button checkBeforeStart = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.checkbeforestart")).but(b -> b.select(settings_.getBooleanValue("dosbox", "checkbeforestart"))).button();
		Button profileWarnings = Chain.on(dosboxGroup).lbl(l -> l.key("dialog.settings.profilewarnings")).but(b -> b.tooltip("dialog.settings.profilewarnings.tooltip").select(settings_.getBooleanValue("gui", "profilewarnings"))).button();

		Group extrasGroup = Group_.on(generalComposite).layout(new GridLayout(2, false)).key("dialog.settings.extras").build();
		String[] expandModes = {"collapsed", "always", "changed"};
		Combo extrasExpand = Chain.on(extrasGroup).lbl(l -> l.key("dialog.settings.extrasexpand")).cmb(c -> c.items(new String[] {text_.get("dialog.settings.extrasexpand.collapsed"),
			text_.get("dialog.settings.extrasexpand.always"), text_.get("dialog.settings.extrasexpand.changed")}).select(Math.max(0, Arrays.asList(expandModes).indexOf(settings_.getValue("gui", "extrasexpand"))))).combo();
		Button extrasOnExtrasPages = Chain.on(extrasGroup).lbl(l -> l.key("dialog.settings.extrasexpandonextraspages")).but(b -> b.tooltip("dialog.settings.extrasexpandonextraspages.tooltip").select(settings_.getBooleanValue("gui", "extrasexpandonextraspages"))).button();
		Button extrasCount = Chain.on(extrasGroup).lbl(l -> l.key("dialog.settings.extrascountchanges")).but(b -> b.select(settings_.getBooleanValue("gui", "extrascountchanges"))).button();

		Group backupGroup = Group_.on(generalComposite).layout(new GridLayout(2, false)).key("dialog.settings.autobackup").build();
		String[] backupModes = {"never", "daily", "weekly"};
		Combo autoBackup = Chain.on(backupGroup).lbl(l -> l.key("dialog.settings.autobackup.when")).cmb(c -> c.items(new String[] {text_.get("dialog.settings.autobackup.never"),
			text_.get("dialog.settings.autobackup.daily"), text_.get("dialog.settings.autobackup.weekly")}).select(Math.max(0, Arrays.asList(backupModes).indexOf(settings_.getValue("backup", "auto"))))).combo();
		Spinner backupKeep = Chain.on(backupGroup).lbl(l -> l.key("dialog.settings.autobackup.keep")).spn(s -> s.min(1).max(50).select(settings_.getIntValue("backup", "keep"))).spinner();

		Group sendToGroup = Group_.on(generalComposite).layout(new GridLayout(2, false)).key("dialog.settings.sendto").build();
		Button portEnabled = Chain.on(sendToGroup).lbl(l -> l.key("dialog.settings.enableport")).but(b -> b.select(settings_.getBooleanValue("communication", "port_enabled"))).button();
		Text port = Chain.on(sendToGroup).lbl(l -> l.key("dialog.settings.port")).txt(t -> t.val(settings_.getValue("communication", "port"))).text();
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

		Group i18nGroup = Group_.on(generalComposite).layout(new GridLayout(2, false)).key("dialog.settings.i18n").build();
		Combo localeCombo = Chain.on(i18nGroup).lbl(l -> l.key("dialog.settings.languagecountry")).cmb(c -> c.items(locales.keySet().toArray(new String[0])).visibleItemCount(20)).combo();
		localeCombo.setText(locString);

		Composite profileTableComposite = createTabWithComposite("dialog.settings.tab.profiletable", new GridLayout());

		List<Integer> allColumnIds = getColumnIds();

		Group visColumnsGroup = Group_.on(profileTableComposite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).layout(new FillLayout()).key("dialog.settings.visiblecolunms").build();

		Table visibleColumnsTable = Table_.on(visColumnsGroup).check().build();
		TableColumn column1 = new TableColumn(visibleColumnsTable, SWT.NONE);
		column1.setWidth(350);
		TableItem[] visibleColumns = new TableItem[columnNames_.length];
		for (int i = 0; i < columnNames_.length; i++) {
			visibleColumns[i] = new TableItem(visibleColumnsTable, SWT.BORDER);
			visibleColumns[i].setText(columnNames_[allColumnIds.get(i)]);
			visibleColumns[i].setChecked(settings_.getBooleanValue("gui", "column" + (allColumnIds.get(i) + 1) + "visible"));
		}

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

		Composite guiComposite = createTabWithComposite("dialog.settings.tab.gui", 4);

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
			c -> c.items(Arrays.asList(TextService.getInstance().get("dialog.settings.theme.default"), TextService.getInstance().get("dialog.settings.theme.forcedarkmode"))).select(
				settings_.getIntValue("gui", "theme"))).combo();

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
		Text[] platformFilterValues = new Text[nrOfEngines];

		CTabFolder enginesTabFolder = CTabFolder_.on(enginesComposite).ctrl();

		for (int i = 0; i < nrOfEngines; i++) {
			WebSearchEngine engine = SettingsService.availableWebSearchEngines().get(i);

			Composite engineComposite = Composite_.on(enginesTabFolder).layout(new GridLayout()).tab("dialog.settings.tab." + engine.getSimpleName()).build();

			Group consultGroup = Group_.on(engineComposite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).layout(new GridLayout(3, false)).txt(
				text_.get("dialog.settings.consult", new String[] {engine.getName()})).build();
			
			if (engine instanceof TheGamesDBSearchEngine) {
				apiKeys[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.apikey")).txt(
						t -> t.horSpan(2).val(settings_.getValue(engine.getSimpleName(), "api_key"))).text();
			}
			
			setTitle[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.settitle")).but(b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_title"))).button();
			setDev[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setdeveloper")).but(
				b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_developer"))).button();
			if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof TheGamesDBSearchEngine) {
				setPub[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setpublisher")).but(
					b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_publisher"))).button();
			}
			setYear[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setyear")).but(b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_year"))).button();
			setGenre[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setgenre")).but(b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_genre"))).button();
			String name = engine instanceof MetropolisSearchEngine ? MobyGamesSearchEngine.getInstance().getName(): engine.getName();
			setLink[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setlink", name)).but(b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_link"))).button();
			if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine) {
				setRank[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setrank", columnNames_[Constants.RO_COLUMN_NAMES + 8])).but(
					b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_rank"))).button();
			}
			if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof TheGamesDBSearchEngine) {
				setDescr[i] = Chain.on(consultGroup).lbl(l -> l.key("dialog.settings.setdescription")).but(
					b -> b.horSpan(2).select(settings_.getBooleanValue(engine.getSimpleName(), "set_description"))).button();
			}
			if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof TheGamesDBSearchEngine) {
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
			if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof TheGamesDBSearchEngine) {
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

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (!isValid(visibleColumnsTable, visibleColumns, getTabItemByComposite(profileTableComposite))) {
					return;
				}

				boolean changedVisColumns = haveColumnsBeenChanged();
				if (changedVisColumns)
					updateColumnSettings(allColumnIds, visibleColumns);

				boolean requireRestart = changedVisColumns || (portEnabled.getSelection() != (settings_.getBooleanValue("communication", "port_enabled")))
						|| (!port.getText().equalsIgnoreCase(settings_.getValue("communication", "port"))) || (screenshotsHeight.getSelection() != settings_.getIntValue("gui", "screenshotsheight"))
						|| (screenshotsColumnHeight.getSelection() != settings_.getIntValue("gui", "screenshotscolumnheight"))
						|| (buttonDisplay.getSelectionIndex() != settings_.getIntValue("gui", "buttondisplay")) || (themeSelector.getSelectionIndex() != settings_.getIntValue("gui", "theme"));

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
				settings_.setBooleanValue("gui", "profilewarnings", profileWarnings.getSelection());
				settings_.setValue("gui", "extrasexpand", expandModes[Math.max(0, extrasExpand.getSelectionIndex())]);
				settings_.setBooleanValue("gui", "extrasexpandonextraspages", extrasOnExtrasPages.getSelection());
				settings_.setBooleanValue("gui", "extrascountchanges", extrasCount.getSelection());
				settings_.setValue("backup", "auto", backupModes[Math.max(0, autoBackup.getSelectionIndex())]);
				settings_.setIntValue("backup", "keep", backupKeep.getSelection());
				settings_.setBooleanValue("communication", "port_enabled", portEnabled.getSelection());
				settings_.setValue("communication", "port", port.getText());
				settings_.setIntValue("profiledefaults", "confpath", confLocation.getSelectionIndex());
				settings_.setIntValue("profiledefaults", "conffile", confFilename.getSelectionIndex());
				settings_.setIntValue("profiledefaults", "capturespath", capturesLocation.getSelectionIndex());
				for (int i = 0; i < columnNames_.length; i++) {
					settings_.setBooleanValue("gui", "column" + (i + 1) + "visible", visibleColumns[allColumnIds.indexOf(i)].getChecked());
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
				settings_.setIntValue("gui", "screenshotscolumnheight", screenshotsColumnHeight.getSelection());
				settings_.setBooleanValue("gui", "screenshotscolumnstretch", stretch.getSelection());
				settings_.setBooleanValue("gui", "screenshotscolumnkeepaspectratio", keepAspectRatio.getSelection());

				Rectangle rec = shell_.getBounds();
				settings_.setIntValue("gui", "settingsdialog_width", rec.width);
				settings_.setIntValue("gui", "settingsdialog_height", rec.height);
				settings_.setIntValue("gui", "buttondisplay", buttonDisplay.getSelectionIndex());
				settings_.setMultilineValue("gui", "notesfont", fontToString(fontButton.getFont()), "|");
				settings_.setIntValue("gui", "theme", themeSelector.getSelectionIndex());
				settings_.setIntValue("gui", "linking", linkingSelector.getSelectionIndex());

				for (int i = 0; i < nrOfEngines; i++) {
					WebSearchEngine engine = SettingsService.availableWebSearchEngines().get(i);
					settings_.setBooleanValue(engine.getSimpleName(), "set_title", setTitle[i].getSelection());
					settings_.setBooleanValue(engine.getSimpleName(), "set_developer", setDev[i].getSelection());
					if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof TheGamesDBSearchEngine) {
						settings_.setBooleanValue(engine.getSimpleName(), "set_publisher", setPub[i].getSelection());
						settings_.setBooleanValue(engine.getSimpleName(), "set_description", setDescr[i].getSelection());
					}
					settings_.setBooleanValue(engine.getSimpleName(), "set_year", setYear[i].getSelection());
					settings_.setBooleanValue(engine.getSimpleName(), "set_genre", setGenre[i].getSelection());
					settings_.setBooleanValue(engine.getSimpleName(), "set_link", setLink[i].getSelection());
					if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine) {
						settings_.setBooleanValue(engine.getSimpleName(), "set_rank", setRank[i].getSelection());
					}
					if (engine instanceof MetropolisSearchEngine) {
						settings_.setBooleanValue(engine.getSimpleName(), "force_all_regions_coverart", allRegionsCoverArt[i].getSelection());
					}
					if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof TheGamesDBSearchEngine) {
						settings_.setBooleanValue(engine.getSimpleName(), "choose_coverart", chooseCoverArt[i].getSelection());
						settings_.setIntValue(engine.getSimpleName(), "multi_max_coverart", maxCoverArt[i].getSelection());
					}
					if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof PouetSearchEngine || engine instanceof TheGamesDBSearchEngine) {
						settings_.setBooleanValue(engine.getSimpleName(), "choose_screenshot", chooseScreenshot[i].getSelection());
						settings_.setIntValue(engine.getSimpleName(), "multi_max_screenshot", maxScreenshots[i].getSelection());
					}
					if (engine instanceof TheGamesDBSearchEngine) {
						settings_.setValue(engine.getSimpleName(), "api_key", apiKeys[i].getText());
					}
					if (engine instanceof MobyGamesSearchEngine || engine instanceof MetropolisSearchEngine || engine instanceof PouetSearchEngine || engine instanceof TheGamesDBSearchEngine) {
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

	private static SortedMap<String, Locale> getLocales() {
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
					loc = new Locale(loc.getLanguage(), loc.getCountry(), variant);
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
}
