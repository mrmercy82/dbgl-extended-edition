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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.gui.abstractdialog.SizeControlledTabbedDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.Text_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.conf.Settings;
import org.dbgl.model.factory.DosboxVersionFactory;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.SettingsService;
import org.dbgl.util.ExecuteUtils;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.StringRelatedUtils;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


public class EditDosboxVersionDialog extends SizeControlledTabbedDialog<DosboxVersion> {

	private final boolean isDefault_;
	private final DosboxVersion dbversion_;
	private String lastTitleSet = null;
	private int lastOptionSelection = -1;

	public EditDosboxVersionDialog(Shell parent, boolean isDefault, DosboxVersion dbversion) {
		super(parent, "dosboxdialog");
		isDefault_ = isDefault;
		dbversion_ = dbversion;
	}

	@Override
	protected String getDialogTitle() {
		return (dbversion_ == null || (dbversion_.getId() == -1)) ? text_.get("dialog.dosboxversion.title.add")
				: text_.get("dialog.dosboxversion.title.edit", new Object[] {dbversion_.getTitle(), dbversion_.getId()});
	}

	private void setTitleByVersion(Combo family, Combo version, Text title) {
		if ((lastTitleSet == null) || StringUtils.isBlank(title.getText()) || Strings.CS.equals(title.getText(), lastTitleSet)) {
			String fam = family.getText().equals("Official") ? "DOSBox" : family.getText();
			lastTitleSet = fam + " " + version.getText();
			title.setText(lastTitleSet);
		}
	}

	@Override
	protected void onShellCreated() {
		Composite composite = createTabWithComposite("dialog.dosboxversion.tab.info", 5);

		Combo family = Chain.on(composite).lbl(l -> l.key("dialog.dosboxversion.family")).cmb(c -> c.horSpan(1).items(SettingsService.SUPPORTED_DOSBOX_FAMILIES.stream().map(x -> x.title_).toList())).combo();
		Combo version = Chain.on(composite).lbl(l -> l.key("dialog.dosboxversion.version")).cmb(c -> c.horSpan(2).visibleItemCount(15)).combo();

		Text title = Chain.on(composite).lbl(l -> l.key("dialog.dosboxversion.title")).txt(t -> t.horSpan(4)).text();
		Chain chainPath = Chain.on(composite).lbl(l -> l.key("dialog.dosboxversion.path")).txt(t -> t.horSpan(3)).but(
			b -> b.browse(false, SystemUtils.IS_OSX ? Button_.BrowseType.FILE: Button_.BrowseType.DIR, Button_.CanonicalType.DOSBOX, false)).build();
		Text path = chainPath.getText();
		Chain exePath = Chain.on(composite).lbl(l -> l.key("dialog.dosboxversion.executable")).txt(t -> t.horSpan(3).tooltip("dialog.dosboxversion.executable.tooltip", FileLocationService.DOSBOX_EXE_STRING)).but(
			b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.DOSBOXEXE, false)).build();
		Text exe = exePath.getText();
		Text conf = Chain.on(composite).lbl(l -> l.key("dialog.profile.configfile")).txt(t -> t.horSpan(3)).but(b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.DOSBOXCONF, false)).text();

		Text parameters = Chain.on(composite).lbl(l -> l.key("dialog.dosboxversion.parameters")).txt(t -> t.horSpan(4)).text();
		Button multiconf = Chain.on(composite).lbl(l -> l.key("dialog.dosboxversion.multiconfsupport")).but(b -> b.horSpan(4)).button();
		Button usingCurses = Chain.on(composite).lbl(l -> l.key("dialog.dosboxversion.altstartup")).but(b -> b.horSpan(4).key("dialog.dosboxversion.altstartupexplanation")).button();
		Button defaultButton = Chain.on(composite).lbl(l -> l.key("dialog.dosboxversion.default")).but(b -> b.horSpan(4)).button();

		exePath.getButton().setData(Button_.DATA_ALT_CONTROL, path);
		chainPath.getButton().setData(Button_.DATA_ALT_CONTROL, conf);
		family.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				version.setItems(SettingsService.SUPPORTED_DOSBOX_FAMILIES.get(family.getSelectionIndex()).versions_.toArray(String[]::new));
				version.select(version.getItemCount() - 1);
				version.pack();
			}
		});

		Composite dynamicOptionsTab = createTabWithComposite("dialog.dosboxversion.tab.dynamicoptions", 2);
		Button enableCustomization = Chain.on(dynamicOptionsTab).lbl(l -> l.key("dialog.dosboxversion.dynamicoptions.enablecustomization")).but(
			b -> b).button();
		Composite dynamicOptionsComposite = Composite_.on(dynamicOptionsTab).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1)).innerLayout(1).build();

		SashForm sashForm = createSashForm(dynamicOptionsComposite, 1);

		Composite left = Composite_.on(sashForm).innerLayout(1).build();
		org.eclipse.swt.widgets.List optionsList = Chain.on(left).lbl(l -> l.key("dialog.settings.options")).lst(l -> l).list();

		Composite right = Composite_.on(sashForm).innerLayout(1).build();
		Text values = Chain.on(right).lbl(l -> l.key("dialog.settings.values")).txt(Text_.Builder::multi).text();

		enableCustomization.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				optionsList.setEnabled(enableCustomization.getSelection());
				values.setEnabled(enableCustomization.getSelection());
			}
		});

		final LinkedHashMap<String, String> optionsMap = new LinkedHashMap<>();

		optionsList.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				SettingsDialog.updateOptionsMap(optionsMap, optionsList, lastOptionSelection, values);
				lastOptionSelection = optionsList.getSelectionIndex();
				if (lastOptionSelection != -1) {
					values.setText(optionsMap.get(optionsList.getItem(lastOptionSelection)));
				}
			}
		});

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (!isValid(title, path, exe, conf, usingCurses, family, version)) {
					return;
				}
				try {
					SettingsDialog.updateOptionsMap(optionsMap, optionsList, lastOptionSelection, values);
					optionsMap.entrySet().forEach(x -> x.setValue(Settings.combineValues(x.getValue(), values.getLineDelimiter())));

					if (dbversion_ == null || (dbversion_.getId() == -1)) {
						DosboxVersion dbv = DosboxVersionFactory.create(title.getText(), family.getText(), version.getText(), defaultButton.getSelection(), multiconf.getSelection(), usingCurses.getSelection(),
							enableCustomization.getSelection() ? optionsMap: null, path.getText(), exe.getText(), parameters.getText(), conf.getText());
						result_ = new DosboxVersionRepository().add(dbv);
					} else {
						dbversion_.setTitle(title.getText());
						dbversion_.setFamily(family.getText());
						dbversion_.setVersion(version.getText());
						dbversion_.setDefault(defaultButton.getSelection());
						dbversion_.setMultiConfig(multiconf.getSelection());
						dbversion_.setUsingCurses(usingCurses.getSelection());
						dbversion_.setDynamicOptions(enableCustomization.getSelection() ? optionsMap: null);
						dbversion_.setPath(path.getText());
						dbversion_.setExe(exe.getText());
						dbversion_.setExecutableParameters(parameters.getText());
						dbversion_.setConfigurationFileLocation(conf.getText());
						new DosboxVersionRepository().update(dbversion_);
						result_ = dbversion_;
					}
				} catch (SQLException e) {
					Mess_.on(shell_).exception(e).warning();
				}
				shell_.close();
			}
		});

		// init values
		if (dbversion_ != null) {
			title.setText(dbversion_.getTitle());
			path.setText(dbversion_.getPath().getPath());
			exe.setText(dbversion_.getExe().getPath());
			conf.setText(dbversion_.getConfigurationFile().getPath());
			parameters.setText(dbversion_.getExecutableParameters());
			family.setText(dbversion_.getFamily());
			version.setItems(SettingsService.SUPPORTED_DOSBOX_FAMILIES.get(family.getSelectionIndex()).versions_.toArray(String[]::new));
			version.setText(dbversion_.getVersion());
			defaultButton.setSelection(dbversion_.isDefault());
			multiconf.setSelection(dbversion_.isMultiConfig());
			usingCurses.setSelection(dbversion_.isUsingCurses());
			title.setFocus();
		} else {
			family.setText(SettingsService.OFFICIAL_DOSBOX_FAM.title_);
			version.setItems(SettingsService.OFFICIAL_DOSBOX_FAM.versions_.toArray(String[]::new));
			version.select(version.getItemCount() - 1);
			defaultButton.setSelection(isDefault_);
			multiconf.setSelection(true);

			SelectionListener verListener = new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					setTitleByVersion(family, version, title);
				}
			};
			family.addSelectionListener(verListener);
			version.addSelectionListener(verListener);
			setTitleByVersion(family, version, title);
			family.setFocus();
		}

		if ((dbversion_ != null) && (dbversion_.getDynamicOptions() != null)) {
			enableCustomization.setSelection(true);
			optionsMap.putAll(dbversion_.getDynamicOptions());
			optionsMap.entrySet().forEach(x -> x.setValue(StringRelatedUtils.stringArrayToString(Settings.splitValues(x.getValue()), values.getLineDelimiter())));
		} else {
			optionsList.setEnabled(false);
			values.setEnabled(false);
			for (String s: settings_.getProfileSectionItemNames())
				optionsMap.put(s, settings_.getMultilineValue("profile", s, values.getLineDelimiter()));
		}

		optionsMap.keySet().forEach(optionsList::add);
	}

	private boolean isValid(Text title, Text path, Text exe, Text conf, Button usingCurses, Combo family, Combo version) {
		Mess_.Builder mess = Mess_.on(shell_);
		if (StringUtils.isBlank(title.getText())) {
			mess.key("dialog.dosboxversion.required.title").bind(title);
		}
		if (StringUtils.isBlank(path.getText())) {
			mess.key("dialog.dosboxversion.required.path").bind(path);
		}
		if (StringUtils.isBlank(conf.getText())) {
			mess.key("dialog.dosboxversion.required.conf").bind(conf);
		}
		if (mess.noErrors()) {
			DosboxVersion dbversion = DosboxVersionFactory.create(StringUtils.EMPTY, family.getText(), version.getText(), false, false, false, null, path.getText(), exe.getText(), StringUtils.EMPTY, conf.getText());
			File executable = dbversion.getCanonicalExecutable();
			File configFile = dbversion.getConfigurationCanonicalFile();
			boolean exeAvailable = FilesUtils.isReadableFile(executable);
			if (!exeAvailable) {
				mess.key("dialog.dosboxversion.error.dosboxexemissing", new Object[] {executable}).bind(path);
			}
			if (!FilesUtils.isReadableFile(configFile) && exeAvailable && Mess_.on(shell_).key("dialog.dosboxversion.confirm.createmissingdosboxconf", new Object[] {configFile}).confirm()) {
				try {
					ExecuteUtils.doCreateDosboxConf(dbversion);
				} catch (IOException e) {
					Mess_.on(shell_).exception(e).warning();
				}
			}
			if (!usingCurses.getSelection() && !FilesUtils.isReadableFile(configFile)) {
				mess.key("dialog.dosboxversion.error.dosboxconfmissing", new Object[] {configFile}).bind(path);
			}
			checkStagingVersionMatchesConf(configFile, family, version);
		}
		return mess.valid();
	}

	private static final Pattern STAGING_CONF_HEADER_PATRN = Pattern.compile("DOSBox Staging \\((\\d+\\.\\d+\\.\\d+)");

	/**
	 * DOSBox Staging writes its version into the first line of its configuration file. If it doesn't match the selected version,
	 * DBGL would use the wrong option lists for this DOSBox and can lose settings, so offer to correct the version.
	 */
	private void checkStagingVersionMatchesConf(File configFile, Combo family, Combo version) {
		if (!Strings.CI.equals(family.getText(), SettingsService.STAGING_DOSBOX_FAM.title_) || !FilesUtils.isReadableFile(configFile))
			return;
		String confVersion = null;
		try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
			for (int i = 0; i < 5 && confVersion == null; i++) {
				String line = reader.readLine();
				if (line == null)
					break;
				Matcher m = STAGING_CONF_HEADER_PATRN.matcher(line);
				if (m.find())
					confVersion = m.group(1);
			}
		} catch (IOException e) {
			return;
		}
		if ((confVersion == null) || confVersion.equals(version.getText()) || !SettingsService.STAGING_DOSBOX_FAM.versions_.contains(confVersion))
			return;
		if (Mess_.on(shell_).key("dialog.dosboxversion.confirm.versionmismatch", new Object[] {configFile, confVersion, version.getText()}).confirm()) {
			int idx = version.indexOf(confVersion);
			if (idx >= 0)
				version.select(idx);
			else
				version.setText(confVersion);
		}
	}
}
