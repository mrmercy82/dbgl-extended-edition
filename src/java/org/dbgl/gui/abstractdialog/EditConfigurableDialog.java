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
package org.dbgl.gui.abstractdialog;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.exception.DrivelettersExhaustedException;
import org.dbgl.gui.controls.BaseDaControlConvertor;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.GridCompactor;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.Group_;
import org.dbgl.gui.controls.Label_;
import org.dbgl.gui.controls.List_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.Text_;
import org.dbgl.gui.dialog.EditMountDialog;
import org.dbgl.gui.dialog.EditNativeCommandDialog;
import org.dbgl.gui.interfaces.DaControlConvertor;
import org.dbgl.model.NativeCommand;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.conf.ConfValueOptions;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.TemplateProfileBase;
import org.dbgl.model.helper.DriveLetterHelper;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeItem;


public abstract class EditConfigurableDialog<T> extends SizeControlledPagedDialog<T> {

	protected enum DosboxConfAction {
		SET, SWITCH, RELOAD, LOAD_TEMPLATE, RELOAD_TEMPLATE
	}

	public enum MountsValidationResult {
		OK, INVALID_OVERLAY_NO_BASEDRIVE, INVALID_OVERLAY_MIXED_ABSREL_PATHS, INVALID_OVERLAY_PATHS_EQUAL
	}

	protected final List<Chain> metaControls_ = new ArrayList<>();
	protected Chain nativeCommandControl_;

	protected List<DosboxVersion> dbversionsList_;
	protected int dbversionIndex_;
	protected Combo dbversionCombo_;
	protected Button setButton_, dosRadio_, booterRadio_;
	protected org.eclipse.swt.widgets.List mountingpointsList_;

	private Button switchButton_, ipx_;
	private Text ipxNet_;

	/**
	 * a setting of the DOSBox version without a field of its own; it is shown on the page that fits its topic (see
	 * {@link SettingPlacement}), in a group at the end of that page
	 */
	private record GenericSetting(String section, String item, Control control, Label label, String initialValue) {
		String value() {
			return control instanceof Combo c ? c.getText().strip(): ((Text)control).getText().strip();
		}
	}

	// managed by DBGL itself (the captures folder of each profile)
	private static final Set<String> DBGL_MANAGED_SETTINGS = Set.of("dosbox/captures", "capture/capture_dir");
	/** per page: the composite at its end that takes the settings without a field of their own */
	private final Map<String, Composite> extrasAreas_ = new HashMap<>();
	/** per page: the DOSBox version its settings were created for */
	private final Map<String, DosboxVersion> extrasBuiltFor_ = new HashMap<>();
	/** per page: the settings without a field of their own, as {section, item}, for one DOSBox version */
	private Map<String, List<String[]>> extrasByPage_ = new HashMap<>();
	private DosboxVersion extrasComputedFor_;
	private final List<GenericSetting> genericSettings_ = new ArrayList<>();
	protected org.eclipse.swt.widgets.List nativeCommandsList_;

	protected EditConfigurableDialog(Shell parent, String dialogName) {
		super(parent, dialogName);
	}

	protected abstract void doPerformDosboxConfAction(DosboxConfAction action, DosboxVersion newDosboxVersion);
	protected abstract String doOpenMixerDialog(String command);

	@Override
	protected boolean prepare() {
		try {
			dbversionsList_ = new DosboxVersionRepository().listAll();
			dbversionIndex_ = BaseRepository.indexOfDefault(dbversionsList_);
			return true;
		} catch (SQLException e) {
			Mess_.on(getParent()).exception(e).warning();
			return false;
		}
	}
	
	protected void addCommonPageGenerators(TreeItem dosboxItem) {
		addPageGenerator(dosboxItem, "dialog.template.tab.startup", this::createDosboxStartupPage);
		addPageGenerator(dosboxItem, "dialog.template.tab.dosboxautoexec", this::createDosboxAutoexecPage);
		TreeItem renderTreeItem = addPageGenerator(dosboxItem, "dialog.template.tab.rendering", this::createRenderingPage);
		addPageGenerator(renderTreeItem, "dialog.template.tab.imageadjustments", this::createImageAdjustmentsPage);
		addPageGenerator(renderTreeItem, "dialog.template.tab.ttf", this::createTtfPage);
		TreeItem dosTreeItem = addPageGenerator(dosboxItem, "dialog.template.dos", this::createDosPage);
		addPageGenerator(dosTreeItem, "dialog.template.tab.configsys", this::createConfigSysPage);
		addPageGenerator(dosTreeItem, "dialog.template.tab.dosv", this::createDosVPage);
		addPageGenerator(dosboxItem, SettingPlacement.LOGGING, (c, p) -> createPage("dialog.template.tab.dosbox", SettingPlacement.LOGGING, p, 1));
		TreeItem machineTreeItem = addPageGenerator("dialog.template.tab.system", this::createSystemPage);
		addPageGenerator(machineTreeItem, "dialog.template.tab.cpu", this::createCpuPage);
		addPageGenerator(machineTreeItem, "dialog.template.tab.memory", this::createMemoryPage);
		TreeItem disksTreeItem = addPageGenerator(machineTreeItem, "dialog.template.tab.disks", this::createDisksPage);
		addPageGenerator(disksTreeItem, SettingPlacement.CONTROLLERS, (c, p) -> createPage("dialog.template.tab.disks", SettingPlacement.CONTROLLERS, p, 1));
		addPageGenerator(machineTreeItem, SettingPlacement.MAINBOARD, (c, p) -> createPage("dialog.template.tab.system", SettingPlacement.MAINBOARD, p, 1));
		addPageGenerator(machineTreeItem, SettingPlacement.PC98, (c, p) -> createPage("dialog.template.tab.system", SettingPlacement.PC98, p, 1));
		TreeItem videoTreeItem = addPageGenerator("dialog.template.tab.video", this::createVideoPage);
		addPageGenerator(videoTreeItem, SettingPlacement.VESA, (c, p) -> createPage("dialog.template.tab.video", SettingPlacement.VESA, p, 1));
		addPageGenerator(videoTreeItem, "dialog.template.tab.cgacomposite", this::createCgaCompositePage);
		addPageGenerator(videoTreeItem, "dialog.template.tab.voodoo", this::createVoodooPage);
		TreeItem audioTreeItem = addPageGenerator("dialog.template.tab.audio", this::createAudioPage);
		TreeItem midiTreeItem = addPageGenerator(audioTreeItem, "dialog.template.tab.midi", this::createMidiPage);
		addPageGenerator(midiTreeItem, "dialog.template.tab.fluidsynth", this::createFluidsynthPage);
		addPageGenerator(midiTreeItem, "dialog.template.tab.mt32", this::createMt32Page);
		addPageGenerator(audioTreeItem, "dialog.template.tab.soundblaster", this::createSoundBlasterPage);
		addPageGenerator(audioTreeItem, "dialog.template.tab.soundblaster2", this::createSoundBlaster2Page);
		addPageGenerator(audioTreeItem, "dialog.template.tab.gravisultrasound", this::createGravisUltrasoundPage);
		addPageGenerator(audioTreeItem, SettingPlacement.WSS, (c, p) -> createPage("dialog.template.tab.audio", SettingPlacement.WSS, p, 1));
		addPageGenerator(audioTreeItem, "dialog.template.tab.pcspeaker", this::createPCSpeakerPage);
		addPageGenerator(audioTreeItem, "dialog.template.tab.tandy", this::createTandyAudioPage);
		addPageGenerator(audioTreeItem, "dialog.template.tab.ps1", this::createPS1AudioPage);
		addPageGenerator(audioTreeItem, "dialog.template.tab.parallelportdac", this::createLptDacPage);
		addPageGenerator(audioTreeItem, "dialog.template.tab.imfc", this::createImfcPage);
		addPageGenerator(audioTreeItem, "dialog.template.tab.innova", this::createInnovaPage);
		TreeItem peripheralsTreeItem = addPageGenerator("dialog.template.tab.peripherals", this::createPeripheralsPage);
		addPageGenerator(peripheralsTreeItem, "dialog.template.tab.keyboard", this::createKeyboardPage);
		addPageGenerator(peripheralsTreeItem, "dialog.template.tab.mouse", this::createMousePage);
		addPageGenerator(peripheralsTreeItem, "dialog.template.tab.joystick", this::createJoystickPage);
		addPageGenerator(peripheralsTreeItem, "dialog.template.tab.printer", this::createPrinterPage);
		TreeItem networkTreeItem = addPageGenerator("dialog.template.tab.network", this::createNetworkPage);
		addPageGenerator(networkTreeItem, "dialog.template.tab.ipx", this::createIpxPage);
		addPageGenerator(networkTreeItem, "dialog.template.tab.modem", this::createModemPage);
		addPageGenerator(networkTreeItem, "dialog.template.tab.ne2000", this::createNe2000Page);
	}

	/** the settings without a field of their own are offered when editing one profile or template at a time */
	protected boolean supportsMoreSettingsPage() {
		return true;
	}

	/** every page gets a place at its end for the settings without a field of their own that belong there */
	@Override
	protected void onPageCreated(String key, Composite page) {
		if (!supportsMoreSettingsPage())
			return;
		Composite area = new Composite(page, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = layout.marginHeight = 0;
		area.setLayout(layout);
		int span = page.getLayout() instanceof GridLayout gl ? gl.numColumns: 1;
		area.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false, span, 1));
		extrasAreas_.put(key, area);
	}

	/**
	 * Decide for every setting of the DOSBox version without a field of its own on which page it goes. All pages must exist,
	 * they tell which settings have a field of their own, and where the fields of each section are.
	 */
	private void computeExtras(TemplateProfileBase configurable) {
		DosboxVersion dbversion = configurable.getDosboxVersion();
		if ((dbversion == null) || (dbversion == extrasComputedFor_))
			return;
		computeAllPages(configurable);
		Set<String> bound = new HashSet<>(DBGL_MANAGED_SETTINGS);
		Map<String, Map<String, Integer>> pagesOfSection = new HashMap<>();
		for (Chain chain: metaControls_) {
			Set<String> settings = chain.boundSettings();
			bound.addAll(settings);
			String page = pageKeyOf(chain.composite());
			if (page != null)
				settings.forEach(x -> pagesOfSection.computeIfAbsent(x.substring(0, x.indexOf('/')), k -> new HashMap<>()).merge(page, 1, Integer::sum));
		}

		Map<String, List<String[]>> result = new HashMap<>();
		Configuration dosboxConf = dbversion.getConfiguration();
		for (String section: dosboxConf.getSectionNames()) {
			if (section.equalsIgnoreCase("autoexec"))
				continue;
			for (String item: dosboxConf.getItemNames(section)) {
				if (bound.contains(section.toLowerCase() + '/' + item.toLowerCase()))
					continue;
				String page = SettingPlacement.pageFor(section, item);
				if ((page == null) || !extrasAreas_.containsKey(page)) // no rule: where most fields of the section are
					page = pagesOfSection.getOrDefault(section.toLowerCase(), Map.of()).entrySet().stream().filter(e -> extrasAreas_.containsKey(e.getKey()))
							.max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(SettingPlacement.DOSBOX);
				if (extrasAreas_.containsKey(page))
					result.computeIfAbsent(page, k -> new ArrayList<>()).add(new String[] {section, item});
			}
		}
		extrasByPage_ = result;
		extrasComputedFor_ = dbversion;
	}

	/** after a change of the DOSBox version, the settings created for the previous one are removed, and created again when needed */
	private void discardOutdatedExtras(TemplateProfileBase configurable) {
		DosboxVersion dbversion = configurable.getDosboxVersion();
		for (Map.Entry<String, Composite> e: extrasAreas_.entrySet()) {
			if (extrasBuiltFor_.containsKey(e.getKey()) && (extrasBuiltFor_.get(e.getKey()) != dbversion)) {
				Stream.of(e.getValue().getChildren()).forEach(Control::dispose);
				extrasBuiltFor_.remove(e.getKey());
			}
		}
		genericSettings_.removeIf(g -> g.control().isDisposed());
	}

	/** create the settings without a field of their own for one page */
	private void buildExtras(TemplateProfileBase configurable, String key) {
		DosboxVersion dbversion = configurable.getDosboxVersion();
		Composite area = extrasAreas_.get(key);
		if ((dbversion == null) || (area == null) || area.isDisposed())
			return;
		computeExtras(configurable);
		Stream.of(area.getChildren()).forEach(Control::dispose);
		genericSettings_.removeIf(g -> g.control().isDisposed());

		Configuration dosboxConf = dbversion.getConfiguration();
		ConfValueOptions documented = dbversion.getConfValueOptions();
		String defaultText = TextService.getInstance().get("dialog.template.moresettings.default");
		String groupText = TextService.getInstance().get("dialog.template.tab.moresettings");
		String info = TextService.getInstance().get("dialog.template.moresettings.tooltip");
		Map<String, Group> groups = new LinkedHashMap<>();
		for (String[] setting: extrasByPage_.getOrDefault(key, List.of())) {
			String section = setting[0], item = setting[1];
			Group group = groups.computeIfAbsent(section, s -> {
				Group g = new Group(area, SWT.NONE);
				g.setText(groupText + " [" + s + "]");
				g.setToolTipText(info);
				g.setLayout(new GridLayout(2, false));
				g.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
				return g;
			});
			String defaultValue = dosboxConf.getValue(section, item);
			String help = documented.getHelp(section, item);
			String tooltip = (help != null ? help + "\n\n": "") + "[" + section + "] " + item + "\n" + defaultText + ": " + (StringUtils.isEmpty(defaultValue) ? "-": defaultValue);
			Label label = new Label(group, SWT.NONE);
			label.setText(item);
			label.setToolTipText(tooltip);
			label.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
			ConfValueOptions.Options options = documented.get(section, item);
			List<String> booleans = booleanValues(defaultValue);
			Control control;
			if ((options != null) || (booleans != null)) {
				boolean readOnly = (options == null) || !options.isFreeForm();
				List<String> values = options != null ? options.getValues(): booleans;
				Combo combo = new Combo(group, readOnly ? SWT.DROP_DOWN | SWT.READ_ONLY: SWT.DROP_DOWN);
				combo.add(""); // empty: DOSBox default
				values.forEach(combo::add);
				combo.setVisibleItemCount(Math.min(20, values.size() + 1));
				control = combo;
			} else {
				Text text = new Text(group, SWT.BORDER);
				text.setMessage(defaultText + ": " + StringUtils.defaultString(defaultValue));
				control = text;
			}
			control.setToolTipText(tooltip);
			GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
			gd.widthHint = 220;
			control.setLayoutData(gd);
			genericSettings_.add(new GenericSetting(section, item, control, label, null));
		}
		extrasBuiltFor_.put(key, dbversion);
		area.setData(HAS_CONTENT, groups.isEmpty() ? null: Boolean.TRUE);
		setGenericValues(configurable);
		area.layout(true, true);
	}

	/** the search looks through all pages, so all settings must exist */
	@Override
	protected void beforeSearch(TemplateProfileBase base) {
		if (!supportsMoreSettingsPage())
			return;
		TemplateProfileBase configurable = currentConfigurable();
		computeExtras(configurable);
		for (String key: extrasByPage_.keySet())
			if (extrasBuiltFor_.get(key) != configurable.getDosboxVersion())
				buildExtras(configurable, key);
	}

	/** values: what the profile/template sets itself, empty means the DOSBox default */
	private void setGenericValues(TemplateProfileBase configurable) {
		for (int i = 0; i < genericSettings_.size(); i++) {
			GenericSetting g = genericSettings_.get(i);
			if (g.control().isDisposed())
				continue;
			String value = StringUtils.defaultString(configurable.getConfiguration().getValue(g.section(), g.item()));
			if (g.control() instanceof Combo combo) {
				if (combo.indexOf(value) == -1)
					combo.add(value); // e.g. a value from an older DOSBox version
				if ((combo.getStyle() & SWT.READ_ONLY) != 0)
					combo.select(combo.indexOf(value));
				else
					combo.setText(value);
			} else {
				((Text)g.control()).setText(value);
			}
			genericSettings_.set(i, new GenericSetting(g.section(), g.item(), g.control(), g.label(), value));
		}
	}

	/** DOSBox documents no values for most switches, offer the two values in the notation of the default */
	private static List<String> booleanValues(String defaultValue) {
		if (defaultValue == null)
			return null;
		switch (defaultValue.strip().toLowerCase()) {
			case "true", "false": return List.of("true", "false");
			case "on", "off": return List.of("on", "off");
			case "yes", "no": return List.of("yes", "no");
			default: return null;
		}
	}

	/** write the changed settings without a field of their own into the profile/template */
	protected void updateConfigurableByMoreSettings(TemplateProfileBase configurable) {
		for (GenericSetting g: genericSettings_) {
			if (g.control().isDisposed())
				continue;
			String value = g.value();
			if (value.equals(StringUtils.defaultString(g.initialValue())))
				continue; // unchanged
			if (value.isEmpty())
				configurable.getConfiguration().removeValueIfSet(g.section(), g.item());
			else
				configurable.setValue(g.section(), g.item(), value);
		}
	}

	@Override
	protected void updatePageTree(TemplateProfileBase configurable, boolean hideEmptyPages) {
		if (hideEmptyPages && supportsMoreSettingsPage()) {
			// the settings themselves are only created when their page is shown; until then, mark the pages that will have some
			computeExtras(configurable);
			for (Map.Entry<String, Composite> e: extrasAreas_.entrySet())
				if (!e.getValue().isDisposed())
					e.getValue().setData(HAS_CONTENT, extrasByPage_.containsKey(e.getKey()) ? Boolean.TRUE: null);
		}
		super.updatePageTree(configurable, hideEmptyPages);
	}

	protected void updateControlsByConfigurable(TemplateProfileBase configurable) {
		Configuration combinedConf = configurable.getCombinedConfiguration();
		Autoexec combinedAuto = combinedConf.getAutoexec();

		// enable or disable controls, and leave pages without supported settings out of the tree
		boolean hideEmptyPages = SettingsService.getInstance().getBooleanValue("gui", "hideunsupportedsettings");
		if (hideEmptyPages)
			computeAllPages(configurable);
		discardOutdatedExtras(configurable);
		metaControls_.forEach(x -> x.enableOrDisableControl(configurable));
		updatePageTree(configurable, hideEmptyPages);
		relayoutVisiblePage();
		// set combo values
		metaControls_.forEach(x -> x.setComboValues(configurable));

		if (mountingpointsList_.isEnabled() && !Arrays.equals(mountingpointsList_.getItems(), configurable.getMountStringsForUI()))
			mountingpointsList_.setItems(configurable.getMountStringsForUI());
		dosRadio_.setSelection(combinedAuto.isDos());
		booterRadio_.setSelection(combinedAuto.isBooter());

		// set control values
		metaControls_.forEach(x -> x.setControlByConfigurable(configurable, combinedConf));
		nativeCommandControl_.setControlByConfigurable(configurable, combinedConf);
		setGenericValues(configurable); // the settings of the pages shown so far

		setButton_.setEnabled(false);
		switchButton_.setEnabled(false);

		ipxNet_.setEnabled(ipx_.getSelection());
	}

	/**
	 * A DOSBox version was selected, but neither 'Set' nor 'Keep all' was clicked: do what 'Set' does, so the profile keeps
	 * its own changes and takes everything else from the configuration of the selected DOSBox version.
	 */
	protected void applyPendingDosboxVersion() {
		if (setButton_.isEnabled() && (dbversionCombo_.getSelectionIndex() != -1))
			doPerformDosboxConfAction(DosboxConfAction.SWITCH, dbversionsList_.get(dbversionCombo_.getSelectionIndex()));
	}

	protected void updatePageTree(TemplateProfileBase configurable) {
		updatePageTree(configurable, SettingsService.getInstance().getBooleanValue("gui", "hideunsupportedsettings"));
	}

	@Override
	protected void onPageShown(Composite page) {
		String key = extrasAreas_.entrySet().stream().filter(e -> !e.getValue().isDisposed() && (e.getValue().getParent() == page)).map(Map.Entry::getKey).findFirst().orElse(null);
		if ((key != null) && (extrasBuiltFor_.get(key) != currentConfigurable().getDosboxVersion())) {
			display_.asyncExec(() -> { // all other pages must exist first, which can't be done while this page is being created
				if (!page.isDisposed() && (extrasBuiltFor_.get(key) != currentConfigurable().getDosboxVersion())) {
					buildExtras(currentConfigurable(), key);
					relayoutVisiblePage();
				}
			});
		}
		GridCompactor.compact(page);
	}

	/**
	 * Settings the DOSBox-X guide recommends for booting Windows 95/98 from a disk image: 64 MB memory, Pentium MMX, the
	 * normal (most compatible) CPU core and no EMS of the built-in DOS. Only values the DOSBox version supports are set.
	 */
	protected void applyWindows9xSettings() {
		TemplateProfileBase configurable = currentConfigurable();
		computeAllPages(configurable);
		updateConfigurableByControls(configurable);
		setFirstSupported(configurable, "dosbox", "memsize", "64");
		setFirstSupported(configurable, "cpu", "cputype", "pentium_mmx", "pentium", "pentium_slow");
		setFirstSupported(configurable, "cpu", "core", "normal");
		String ems = StringUtils.defaultString(configurable.getDosboxVersion().getConfiguration().getValue("dos", "ems")).toLowerCase();
		setFirstSupported(configurable, "dos", "ems", ems.equals("on") || ems.equals("off") ? "off": "false");
		updateControlsByConfigurable(configurable);
	}

	private static void setFirstSupported(TemplateProfileBase configurable, String section, String item, String... candidates) {
		Configuration dosboxConf = configurable.getDosboxVersion().getConfiguration();
		if (!dosboxConf.hasValue(section, item))
			return;
		ConfValueOptions.Options options = configurable.getDosboxVersion().getConfValueOptions().get(section, item);
		for (String candidate: candidates) {
			if ((options == null) || options.isFreeForm() || options.getValues().contains(candidate)) {
				configurable.setValue(section, item, candidate);
				return;
			}
		}
	}

	/** list the settings that differ from the defaults of the DOSBox version, as they are in the dialog right now */
	protected void showDifferences() {
		TemplateProfileBase configurable = currentConfigurable();
		computeAllPages(configurable);
		updateConfigurableByControls(configurable);
		Configuration own = configurable.getConfiguration();
		Configuration dosboxConf = configurable.getDosboxVersion().getConfiguration();
		String defaultText = TextService.getInstance().get("dialog.template.moresettings.default");
		StringBuilder sb = new StringBuilder(configurable.getDosboxVersion().getTitle()).append(Text.DELIMITER).append(Text.DELIMITER);
		int count = 0;
		for (String section: own.getSectionNames()) {
			for (String item: own.getItemNames(section)) {
				String value = own.getValue(section, item);
				String def = dosboxConf.getValue(section, item);
				if (StringUtils.equals(value, def) || DBGL_MANAGED_SETTINGS.contains(section + '/' + item))
					continue;
				sb.append('[').append(section).append("] ").append(item).append(" = ").append(value).append("   (").append(defaultText).append(": ")
						.append(def == null ? "-": def).append(')').append(Text.DELIMITER);
				count++;
			}
		}
		if (count == 0)
			sb.append(TextService.getInstance().get("dialog.differences.none"));
		new org.dbgl.gui.dialog.DifferencesDialog(shell_, TextService.getInstance().get("dialog.differences.title"), sb.toString()).open();
	}

	/** the profile or template being edited */
	protected abstract TemplateProfileBase currentConfigurable();

	protected void updateConfigurableByControls(TemplateProfileBase configurable) {
		Configuration combinedConf = configurable.getCombinedConfiguration();
		metaControls_.forEach(x -> x.updateConfigurableByControl(configurable, combinedConf));
		nativeCommandControl_.updateConfigurableByControl(configurable, combinedConf);
		updateConfigurableByMoreSettings(configurable);

		configurable.setBooter(booterRadio_.getSelection());
	}

	protected Composite createDosboxPage(TemplateProfileBase configurable, Composite parent, String capturesText, String configFileText) {
		Composite composite = createPage("dialog.template.tab.dosbox", parent, 1);

		Group associationGroup = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false)).layout(new GridLayout(5, false)).key("dialog.template.association").build();
		dbversionCombo_ = Chain.on(associationGroup).lbl(l -> l.key("dialog.template.dosboxversion")).cmb(
			c -> c.wide().items(dbversionsList_.stream().map(DosboxVersion::getTitle).toArray(String[]::new)).select(dbversionIndex_).visibleItemCount(20)).combo();
		// 'Set' keeps only the own changes, everything else comes from the configuration of the new DOSBox version (SWITCH);
		// 'Keep all' also keeps the values that came from the defaults of the previous DOSBox version (SET)
		setButton_ = Button_.on(associationGroup).text().key("dialog.template.set").tooltip("dialog.template.set.tooltip").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doPerformDosboxConfAction(DosboxConfAction.SWITCH, dbversionsList_.get(dbversionCombo_.getSelectionIndex()));
			}
		}).enable(false).ctrl();
		switchButton_ = Button_.on(associationGroup).text().key("dialog.template.switch").tooltip("dialog.template.switch.tooltip").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doPerformDosboxConfAction(DosboxConfAction.SET, dbversionsList_.get(dbversionCombo_.getSelectionIndex()));
			}
		}).enable(false).ctrl();
		Button_.on(associationGroup).text().key("dialog.template.reloadsettings").tooltip("dialog.template.reloadsettings.tooltip").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doPerformDosboxConfAction(DosboxConfAction.RELOAD, dbversionsList_.get(dbversionCombo_.getSelectionIndex()));
			}
		}).ctrl();
		dbversionCombo_.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				setButton_.setEnabled(true);
				switchButton_.setEnabled(true);
			}
		});
		Button_.on(associationGroup).text().key("dialog.template.showdifferences").tooltip("dialog.template.showdifferences.tooltip").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				showDifferences();
			}
		}).ctrl().setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 5, 1));

		Group miscGroup = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).layout(new GridLayout(3, false)).key("dialog.template.miscellaneous").build();
		Chain.on(miscGroup).lbl(l -> l).lbl(l -> l.key("dialog.template.active")).lbl(l -> l.key("dialog.template.inactive")).build();
		Chain.on(miscGroup).lbl(l -> l.key("dialog.template.priority")).cmb(c -> c.tooltip("dialog.template.priority.tooltip")
			.items("profile", configurable.priorityActiveSectionItem()).dynItem(GenerationAwareConfigurable::priorityActiveSectionItem)).cmb(c -> c.tooltip("dialog.template.priority.tooltip")
			.items("profile", configurable.priorityInactiveSectionItem()).dynItem(GenerationAwareConfigurable::priorityInactiveSectionItem)).section("sdl").item("priority")
			.convert(configurable.getPriorityConvertor()).customInputConvertor(TemplateProfileBase::detectPriorityConvertor).customOutputConvertor(TemplateProfileBase::getPriorityConvertor)
			.build(metaControls_).init(configurable);
		Chain.on(miscGroup).lbl(l -> l.key("dialog.template.waitonerror")).but(b -> b.horSpan(2).tooltip("dialog.template.waitonerror.tooltip")).section("sdl").item("waitonerror").build(metaControls_).init(configurable);
		Chain.on(miscGroup).lbl(l -> l.key("dialog.template.exitafterwards")).but(b -> b.horSpan(2).tooltip("dialog.template.exitafterwards.tooltip")).autoexec(Autoexec::getExit, Autoexec::setExit).build(metaControls_).init(configurable);
		Chain.on(miscGroup).lbl(l -> l.key("dialog.template.languagefile")).cmb(c -> c.editable().horSpan(2).tooltip("dialog.template.languagefile.tooltip").items("profile", "languages")).section("dosbox").item("language").build(
			metaControls_).init(configurable);
		Chain.on(miscGroup).lbl(l -> l.key("dialog.template.customconf")).txt(t -> t.horSpan(2).tooltip("dialog.template.customconf.tooltip").multi()).customSection(TemplateProfileBase::getCustomSection,
				TemplateProfileBase::setCustomSection).build(metaControls_).init(configurable);
		Chain.on(miscGroup).lbl(l -> l.key("dialog.template.captures")).txt(t -> t.horSpan(2).tooltip("dialog.template.captures.tooltip").val(capturesText).nonEditable()).build();
		Chain.on(miscGroup).lbl(l -> l.key("dialog.template.configfile")).txt(t -> t.horSpan(2).tooltip("dialog.template.configfile.tooltip").val(configFileText).nonEditable()).build();

		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		gd.verticalIndent = 20;
		Group expDosbox = Group_.on(composite).layoutData(gd).layout(new GridLayout(5, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.startbanner")).but(b -> b.tooltip("dialog.template.startbanner.tooltip")).section("dosbox").item("startbanner").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.bannercolortheme")).cmb(c -> c.tooltip("dialog.template.bannercolortheme.tooltip").items("profile", "bannercolortheme_x20240301"))
			.section("dosbox").item("bannercolortheme").build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.showbasic")).but(b -> b.tooltip("dialog.template.showbasic.tooltip")).section("sdl").item("showbasic").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.startupverbosity")).cmb(c -> c.tooltip("dialog.template.startupverbosity.tooltip").items("profile", "startupverbosity_staging0810"))
			.section("dosbox").item("startup_verbosity").build(metaControls_).init(configurable);

		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.showdetails")).but(b -> b.tooltip("dialog.template.showdetails.tooltip")).section("sdl").item("showdetails").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.titlebar")).txt(t -> t.tooltip("dialog.template.titlebar.tooltip")).section("sdl", "sdl").item("titlebar", "window_titlebar").build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.fastbioslogo")).but(b -> b.tooltip("dialog.template.fastbioslogo.tooltip")).section("dosbox").item("fastbioslogo").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.addtitlebar")).txt(t -> t.tooltip("dialog.template.addtitlebar.tooltip")).section("dosbox").item("title").build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.logotext")).txt(t -> t.tooltip("dialog.template.logotext.tooltip")).section("dosbox").item("logo text").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.logo")).txt(t -> t.tooltip("dialog.template.logo.tooltip")).section("dosbox").item("logo").build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.showmenu")).but(b -> b.tooltip("dialog.template.showmenu.tooltip")).section("sdl").item("showmenu").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.quitwarning")).cmb(c -> c.tooltip("dialog.template.quitwarning.tooltip").items("profile", "quitwarning_x20240301"))
			.section("dosbox").item("quit warning").build(metaControls_).init(configurable);
	
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.showadvancedoptions")).but(b -> b.tooltip("dialog.template.showadvancedoptions.tooltip")).section("dosbox").item("show advanced options").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.configurationtooltheme")).cmb(c -> c.tooltip("dialog.template.configurationtooltheme.tooltip").items("profile", "configurationtooltheme_x20241001").editable())
			.section("dosbox").item("configuration tool theme").build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.configshortcuts")).but(b -> b.tooltip("dialog.template.configshortcuts.tooltip")).section("dosbox", "dos").item("shell_config_shortcuts", "shell configuration as commands").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.resolveconfigpath")).cmb(c -> c.tooltip("dialog.template.resolveconfigpath.tooltip").items("profile", "resolveconfigpath_x20240301"))
			.section("dosbox").item("resolve config path").build(metaControls_).init(configurable);
	
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.startcmd")).but(b -> b.tooltip("dialog.template.startcmd.tooltip")).section("dos").item("startcmd").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.hostkey")).cmb(c -> c.tooltip("dialog.template.hostkey.tooltip").items("profile", "hostkey_x20240301"))
			.section("dosbox").item("hostkey").build(metaControls_).init(configurable);

		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.startquiet")).but(b -> b.tooltip("dialog.template.startquiet.tooltip")).section("dos").item("startquiet").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.workingdiroption")).cmb(c -> c.tooltip("dialog.template.workingdiroption.tooltip").items("profile", "workingdiroption_x20240301"))
			.section("dosbox").item("working directory option").build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.startwait")).but(b -> b.tooltip("dialog.template.startwait.tooltip")).section("dos").item("startwait").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.workingdirdefault")).txt(t -> t.tooltip("dialog.template.workingdirdefault.tooltip")).section("dosbox").item("working directory default").build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.startnopause")).but(b -> b.tooltip("dialog.template.startnopause.tooltip")).section("dos").item("startnopause").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.autoexecsections")).cmb(c -> c.tooltip("dialog.template.autoexecsections.tooltip").items("profile", "autoexecsections_staging0810"))
			.section("dosbox").item("autoexec_section").build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.starttranspath")).but(b -> b.tooltip("dialog.template.starttranspath.tooltip")).section("dos").item("starttranspath").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.imgcaptureformat")).cmb(c -> c.tooltip("dialog.template.imgcaptureformat.tooltip").items("profile", "imgcaptureformat_staging0810").editable()).section("capture").item("default_image_capture_formats")
			.build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.writeprotect")).but(b -> b.tooltip("dialog.template.writeprotect.tooltip")).section("dosbox").item("allow_write_protected_files").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.logfile")).txt(t -> t.tooltip("dialog.template.logfile.tooltip")).section("log").item("logfile").build(metaControls_).init(configurable);

		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.pausewheninactive")).but(b -> b.tooltip("dialog.template.pausewheninactive.tooltip")).section("sdl").item("pause_when_inactive")
			.build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.debuggerrun")).cmb(c -> c.tooltip("dialog.template.debuggerrun.tooltip").items("profile", "debuggerrun_x20240301"))
			.section("log").item("debuggerrun").build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.saveslot")).spn(s -> s.min(1).max(100).tooltip("dialog.template.saveslot.tooltip")).section("dosbox").item("saveslot")
			.build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.savefile")).txt(t -> t.tooltip("dialog.template.savefile.tooltip")).section("dosbox").item("savefile").build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.saveremark")).but(b -> b.tooltip("dialog.template.saveremark.tooltip")).section("dosbox").item("saveremark").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.autosave")).txt(t -> t.tooltip("dialog.template.autosave.tooltip")).section("dosbox").item("autosave").build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.forceloadstate")).but(b -> b.tooltip("dialog.template.forceloadstate.tooltip")).section("dosbox").item("forceloadstate").build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.synctime")).but(b -> b.tooltip("dialog.template.synctime.tooltip")).section("dosbox").item("synchronize time").build(metaControls_).init(configurable);

		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.clipboardspeed")).spn(s -> s.min(0).tooltip("dialog.template.clipboardspeed.tooltip")).section("sdl").item("clip_paste_speed")
			.build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.clipboardbutton")).cmb(c -> c.tooltip("dialog.template.clipboardbutton.tooltip").items("profile", "clipboardbutton_x20240301")).section("sdl").item("clip_mouse_button")
			.build(metaControls_).init(configurable);
		
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.clipboardmod")).cmb(c -> c.tooltip("dialog.template.clipboardmod.tooltip").items("profile", "clipboardmod_x20240301")).section("sdl").item("clip_key_modifier")
			.build(metaControls_).init(configurable);
		Label_.on(expDosbox).grabHorSpace().build();
		Chain.on(expDosbox).lbl(l -> l.key("dialog.template.clipboardbios")).cmb(c -> c.tooltip("dialog.template.clipboardbios.tooltip").items("profile", "clipboardbios_x20240301")).section("sdl").item("clip_paste_bios")
			.build(metaControls_).init(configurable);
		
		return composite;
	}

	protected Composite createRenderingPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.dosbox", "dialog.template.tab.rendering", parent, 6);
		Chain.on(composite).lbl(l -> l.key("dialog.template.output")).cmb(c -> c.tooltip("dialog.template.output.tooltip").items("profile", configurable.outputSectionItem()).dynItem(GenerationAwareConfigurable::outputSectionItem))
			.section("sdl").item("output").build(metaControls_).init(configurable);
		Label_.on(composite).horSpan(2).grabHorSpace().build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.fullscreen")).but(b -> b.tooltip("dialog.template.fullscreen.tooltip")).section("sdl").item("fullscreen").build(
			metaControls_).init(configurable);
		
		Chain.on(composite).lbl(l -> l.key("dialog.template.windowresolution")).cmb(c -> c.editable().tooltip("dialog.template.windowresolution.tooltip").items("profile", configurable.windowresSectionItem()).dynItem(GenerationAwareConfigurable::windowresSectionItem))
			.section("sdl").item("windowresolution", "window_size").newItemLabel("dialog.template.windowsize").build(metaControls_).init(configurable);
		Label_.on(composite).horSpan(2).grabHorSpace().build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.fullscreenresolution")).cmb(
			c -> c.tooltip("dialog.template.fullscreenresolution.tooltip").items("profile", configurable.fullscreenmodeSectionItem()).dynItem(GenerationAwareConfigurable::fullscreenmodeSectionItem)).section("sdl").item("fullresolution", "fullscreen_mode").newItemLabel("dialog.template.fullscreenmode").build(metaControls_).init(configurable);
			
		Chain.on(composite).lbl(l -> l.key("dialog.template.scaler")).cmb(c -> c.tooltip("dialog.template.scaler.tooltip").items("profile", configurable.scalerSectionItem()).dynItem(GenerationAwareConfigurable::scalerSectionItem).visibleItemCount(15)).lbl(
			l -> l.key("dialog.template.scalerforced")).but(b -> b.tooltip("dialog.template.scalerforced.tooltip")).section("render").item("scaler").convert(new BaseDaControlConvertor() {
				@Override
				public String toConfValue(String existingValue, String[] values) {
					String joinedValues = values[0];
					if ((values.length >= 2) && Boolean.parseBoolean(values[1]))
						joinedValues += " forced";
					if (StringUtils.isBlank(existingValue))
						return joinedValues;

					String[] exVals = toControlValues(existingValue);
					if (exVals.length != values.length) {
						System.err.println("configuration values mismatch: [" + existingValue + "] vs. [" + joinedValues + "]");
						return existingValue;
					}

					joinedValues = StringUtils.isBlank(values[0]) ? exVals[0]: values[0];
					if (Boolean.parseBoolean(values[1]))
						joinedValues += " forced";
					return joinedValues;
				}

				@Override
				public String toConfValueForDisplay(String[] values) {
					String result = toConfValue(null, values);
					switch (result) {
						case StringUtils.EMPTY:
							return "... <not forced>";
						case " forced":
							return "... forced";
						default:
							return result;
					}
				}

				@Override
				public String[] toControlValues(String value) {
					if (value == null)
						return new String[0];
					String[] results = new String[2];
					if (value.endsWith("forced")) {
						results[0] = value.substring(0, Math.max(value.length() - 7, 0));
						results[1] = String.valueOf(true);
					} else {
						results[0] = value;
						results[1] = String.valueOf(false);
					}
					return results;
				}
			}).build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.doublebuffering")).but(b -> b.tooltip("dialog.template.doublebuffering.tooltip")).section("sdl").item("fulldouble").build(
				metaControls_).init(configurable);

		Chain.on(composite).lbl(l -> l.key("dialog.template.aspectcorrection")).cmb(c -> c.tooltip("dialog.template.aspectcorrection.tooltip").items("profile", configurable.aspectSectionItem()).dynItem(GenerationAwareConfigurable::aspectSectionItem))
			.section("render").item("aspect").build(metaControls_).init(configurable);
		Label_.on(composite).horSpan(2).grabHorSpace().build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.frameskip")).cmb(
				c -> c.tooltip("dialog.template.frameskip.tooltip").items("profile", "frameskip").visibleItemCount(15)).section("render").item("frameskip").build(metaControls_).init(configurable);
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 6, 1);
		gd.verticalIndent = 20;
		Group expRendering = Group_.on(composite).layoutData(gd).layout(new GridLayout(6, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.glshader")).cmb(c -> c.horSpan(4).tooltip("dialog.template.glshader.tooltip").editable().wide().items(configurable.glshaderValues()).dynItems(GenerationAwareConfigurable::glshaderValues))
			.but(b -> b.browse(true, Button_.BrowseType.FILE, Button_.CanonicalType.GLSHADER, false)).section("render").item("glshader", "shader").newItemLabel("dialog.template.shader").build(metaControls_).init(configurable);
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.pixelshader")).txt(t -> t.horSpan(4).tooltip("dialog.template.pixelshader.tooltip"))
			.but(b -> b.browse(true, Button_.BrowseType.FILE, Button_.CanonicalType.GLSHADER, false)).section("render").item("pixelshader").build(metaControls_).init(configurable);
		
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.texturerenderer")).cmb(c -> c.tooltip("dialog.template.texturerenderer.tooltip").items("profile", "texturerenderer_staging0810"))
			.section("sdl").item("texture_renderer").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.maximize")).but(b -> b.tooltip("dialog.template.maximize.tooltip"))
			.section("sdl").item("maximize").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
	
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.videodriver")).txt(t -> t.tooltip("dialog.template.videodriver.tooltip"))
			.section("sdl").item("videodriver").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.dpiaware")).cmb(c -> c.tooltip("dialog.template.dpiaware.tooltip").items("profile", "dpiaware_x20240301"))
			.section("dosbox").item("dpi aware").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.windowpositionx")).cmb(c -> c.editable().tooltip("dialog.template.windowpositionx.tooltip").items("profile", "windowposition_x20240301"))
			.section("sdl").item("windowposition").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.windowposition")).cmb(c -> c.editable().tooltip("dialog.template.windowposition.tooltip").items("profile", "windowposition_staging0810"))
			.section("sdl").item("window_position").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
	
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.aspectratio")).cmb(c -> c.editable().tooltip("dialog.template.aspectratio.tooltip").items("profile", "aspectratio_x20240301"))
			.section("render").item("aspect_ratio").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.overscan")).spn(s -> s.min(0).max(10).tooltip("dialog.template.overscan.tooltip"))
			.section("sdl").item("overscan").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.integerscaling")).cmb(c -> c.tooltip("dialog.template.integerscaling.tooltip").items("profile", "integerscaling_staging0810"))
			.section("render").item("integer_scaling").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.autofit")).but(b -> b.tooltip("dialog.template.autofit.tooltip")).section("render").item("autofit")
			.build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.viewport")).txt(t -> t.tooltip("dialog.template.viewport.tooltip"))
			.section("render").item("viewport").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.windowdecorations")).but(b -> b.tooltip("dialog.template.windowdecorations.tooltip")).section("sdl").item("window_decorations")
			.build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.vsyncmode")).cmb(c -> c.tooltip("dialog.template.vsyncmode.tooltip").items("profile", configurable.vsyncmodeSectionItem()).dynItem(GenerationAwareConfigurable::vsyncmodeSectionItem))
			.section("sdl", "vsync").item("vsync", "vsyncmode").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.vsyncrate")).spn(s -> s.min(30).max(120).tooltip("dialog.template.vsyncrate.tooltip"))
			.section("vsync").item("vsyncrate").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
	
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.vsyncskip")).spn(s -> s.min(0).max(15000).tooltip("dialog.template.vsyncskip.tooltip")).section("sdl").item("vsync_skip")
			.build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.hostrefreshrate")).cmb(c -> c.editable().tooltip("dialog.template.hostrefreshrate.tooltip").items("profile", "hostrefreshrate_staging0810"))
			.section("sdl").item("host_rate").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
	
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.framepresentationmode")).cmb(c -> c.tooltip("dialog.template.framepresentationmode.tooltip").items("profile", configurable.presentationmodeSectionItem()).dynItem(GenerationAwareConfigurable::presentationmodeSectionItem))
			.section("sdl").item("presentation_mode").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.transparency")).spn(s -> s.min(0).max(90).tooltip("dialog.template.transparency.tooltip")).section("sdl").item("transparency", "window_transparency")
			.build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
	
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.displayno")).spn(s -> s.min(0).max(10).tooltip("dialog.template.displayno.tooltip"))
			.section("sdl").item("display").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.doublescan")).but(b -> b.tooltip("dialog.template.doublescan.tooltip")).section("render").item("doublescan")
			.build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();

		Chain.on(expRendering).lbl(l -> l.key("dialog.template.screensaver")).cmb(c -> c.tooltip("dialog.template.screensaver.tooltip").items("profile", "screensaver_staging0810"))
			.section("sdl").item("screensaver").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.euro")).txt(t -> t.tooltip("dialog.template.euro.tooltip"))
			.section("render").item("euro").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();

		Chain.on(expRendering).lbl(l -> l.key("dialog.template.char9")).but(b -> b.tooltip("dialog.template.char9.tooltip")).section("render").item("char9")
			.build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.crtcolorprofile")).cmb(c -> c.tooltip("dialog.template.crtcolorprofile.tooltip").items("profile", "crtcolorprofile_staging0830"))
			.section("render").item("crt_color_profile").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();

		Chain.on(expRendering).lbl(l -> l.key("dialog.template.deinterlacing")).cmb(c -> c.tooltip("dialog.template.deinterlacing.tooltip").items("profile", "deinterlacing_staging0830"))
			.section("render").item("deinterlacing").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();
		Chain.on(expRendering).lbl(l -> l.key("dialog.template.dedithering")).cmb(c -> c.editable().tooltip("dialog.template.dedithering.tooltip").items("profile", "dedithering_staging0830"))
			.section("render").item("dedithering").build(metaControls_).init(configurable);
		Label_.on(expRendering).grabHorSpace().build();

		Chain.on(expRendering).lbl(l -> l.key("dialog.template.imageadjustments")).but(b -> b.tooltip("dialog.template.imageadjustments.tooltip")).section("render").item("image_adjustments")
			.build(metaControls_).init(configurable);
		
		return composite;
	}
	
	protected Composite createImageAdjustmentsPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.rendering", "dialog.template.tab.imageadjustments", parent, 1);
		Group expImg = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(new GridLayout(5, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expImg).lbl(l -> l.key("dialog.template.brightness")).spn(s -> s.min(0).max(100).tooltip("dialog.template.brightness.tooltip"))
			.section("render").item("brightness").build(metaControls_).init(configurable);
		Label_.on(expImg).grabHorSpace().build();
		Chain.on(expImg).lbl(l -> l.key("dialog.template.contrast")).spn(s -> s.min(0).max(100).tooltip("dialog.template.contrast.tooltip"))
			.section("render").item("contrast").build(metaControls_).init(configurable);

		Chain.on(expImg).lbl(l -> l.key("dialog.template.gamma")).spn(s -> s.min(-50).max(50).tooltip("dialog.template.gamma.tooltip"))
			.section("render").item("gamma").build(metaControls_).init(configurable);
		Label_.on(expImg).grabHorSpace().build();
		Chain.on(expImg).lbl(l -> l.key("dialog.template.saturation")).spn(s -> s.min(-50).max(50).tooltip("dialog.template.saturation.tooltip"))
			.section("render").item("saturation").build(metaControls_).init(configurable);

		Chain.on(expImg).lbl(l -> l.key("dialog.template.digitalcontrast")).spn(s -> s.min(-50).max(50).tooltip("dialog.template.digitalcontrast.tooltip"))
			.section("render").item("digital_contrast").build(metaControls_).init(configurable);
		Label_.on(expImg).grabHorSpace().build();
		Chain.on(expImg).lbl(l -> l.key("dialog.template.blacklevel")).cmb(c -> c.editable().tooltip("dialog.template.blacklevel.tooltip").items("profile", "blacklevel_staging0830"))
			.section("render").item("black_level").build(metaControls_).init(configurable);

		Chain.on(expImg).lbl(l -> l.key("dialog.template.colortemperature")).cmb(c -> c.editable().tooltip("dialog.template.colortemperature.tooltip").items("profile", "colortemperature_staging0830"))
			.section("render").item("color_temperature").build(metaControls_).init(configurable);
		Label_.on(expImg).grabHorSpace().build();
		Chain.on(expImg).lbl(l -> l.key("dialog.template.colortemperatureluma")).spn(s -> s.min(0).max(100).tooltip("dialog.template.colortemperatureluma.tooltip"))
			.section("render").item("color_temperature_luma_preserve").build(metaControls_).init(configurable);

		Chain.on(expImg).lbl(l -> l.key("dialog.template.redgain")).spn(s -> s.min(0).max(200).tooltip("dialog.template.redgain.tooltip"))
			.section("render").item("red_gain").build(metaControls_).init(configurable);
		Label_.on(expImg).grabHorSpace().build();
		Chain.on(expImg).lbl(l -> l.key("dialog.template.greengain")).spn(s -> s.min(0).max(200).tooltip("dialog.template.greengain.tooltip"))
			.section("render").item("green_gain").build(metaControls_).init(configurable);

		Chain.on(expImg).lbl(l -> l.key("dialog.template.bluegain")).spn(s -> s.min(0).max(200).tooltip("dialog.template.bluegain.tooltip"))
			.section("render").item("blue_gain").build(metaControls_).init(configurable);
		Label_.on(expImg).grabHorSpace().build();
		Chain.on(expImg).lbl(l -> l.key("dialog.template.colorspace")).cmb(c -> c.tooltip("dialog.template.colorspace.tooltip").items("profile", "colorspace_staging0830"))
			.section("render").item("color_space").build(metaControls_).init(configurable);

		return composite;
	}

	protected Composite createTtfPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.rendering", "dialog.template.tab.ttf", parent, 1);
		Group expTtf = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(new GridLayout(5, false)).key("dialog.template.tab.experimentaloptions").build();
		
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.bold")).but(b -> b.tooltip("dialog.template.ttf.bold.tooltip"))
			.section("ttf").item("bold").build(metaControls_).init(configurable);
		Label_.on(expTtf).grabHorSpace().build();
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.font")).txt(t -> t.tooltip("dialog.template.ttf.font.tooltip"))
			.section("ttf").item("font").build(metaControls_).init(configurable);
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.italic")).but(b -> b.tooltip("dialog.template.ttf.italic.tooltip"))
			.section("ttf").item("italic").build(metaControls_).init(configurable);
		Label_.on(expTtf).grabHorSpace().build();
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.fontbold")).txt(t -> t.tooltip("dialog.template.ttf.fontbold.tooltip"))
			.section("ttf").item("fontbold").build(metaControls_).init(configurable);
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.underline")).but(b -> b.tooltip("dialog.template.ttf.underline.tooltip"))
			.section("ttf").item("underline").build(metaControls_).init(configurable);
		Label_.on(expTtf).grabHorSpace().build();
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.fontital")).txt(t -> t.tooltip("dialog.template.ttf.fontital.tooltip"))
			.section("ttf").item("fontital").build(metaControls_).init(configurable);
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.strikeout")).but(b -> b.tooltip("dialog.template.ttf.strikeout.tooltip"))
			.section("ttf").item("strikeout").build(metaControls_).init(configurable);
		Label_.on(expTtf).grabHorSpace().build();
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.fontboit")).txt(t -> t.tooltip("dialog.template.ttf.fontboit.tooltip"))
			.section("ttf").item("fontboit").build(metaControls_).init(configurable);
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.righttoleft")).but(b -> b.tooltip("dialog.template.ttf.righttoleft.tooltip"))
			.section("ttf").item("righttoleft").build(metaControls_).init(configurable);
		Label_.on(expTtf).grabHorSpace().build();
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.blinkc")).cmb(c -> c.tooltip("dialog.template.ttf.blinkc.tooltip").items("profile", "ttfblinkc_x20240301").editable())
			.section("ttf").item("blinkc").build(metaControls_).init(configurable);
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.printfont")).but(b -> b.tooltip("dialog.template.ttf.printfont.tooltip"))
			.section("ttf").item("printfont").build(metaControls_).init(configurable);
		Label_.on(expTtf).grabHorSpace().build();
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.colors")).txt(t -> t.tooltip("dialog.template.ttf.colors.tooltip"))
			.section("ttf").item("colors").build(metaControls_).init(configurable);
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.autodbcs")).but(b -> b.tooltip("dialog.template.ttf.autodbcs.tooltip"))
			.section("ttf").item("autodbcs").build(metaControls_).init(configurable);
		Label_.on(expTtf).grabHorSpace().build();
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.winperc")).spn(s -> s.min(0).max(100).tooltip("dialog.template.ttf.winperc.tooltip"))
			.section("ttf").item("winperc").build(metaControls_).init(configurable);
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.gbk")).but(b -> b.tooltip("dialog.template.ttf.gbk.tooltip"))
			.section("ttf").item("gbk").build(metaControls_).init(configurable);
		Label_.on(expTtf).grabHorSpace().build();
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.ptsize")).spn(s -> s.min(0).tooltip("dialog.template.ttf.ptsize.tooltip"))
			.section("ttf").item("ptsize").build(metaControls_).init(configurable);
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.chinasea")).but(b -> b.tooltip("dialog.template.ttf.chinasea.tooltip"))
			.section("ttf").item("chinasea").build(metaControls_).init(configurable);
		Label_.on(expTtf).grabHorSpace().build();
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.lins")).spn(s -> s.min(0).tooltip("dialog.template.ttf.lins.tooltip"))
			.section("ttf").item("lins").build(metaControls_).init(configurable);
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.dosvfunc")).but(b -> b.tooltip("dialog.template.ttf.dosvfunc.tooltip"))
			.section("ttf").item("dosvfunc").build(metaControls_).init(configurable);
		Label_.on(expTtf).grabHorSpace().build();
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.cols")).spn(s -> s.min(0).tooltip("dialog.template.ttf.cols.tooltip"))
			.section("ttf").item("cols").build(metaControls_).init(configurable);
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.wp")).cmb(c -> c.tooltip("dialog.template.ttf.wp.tooltip").items("profile", "ttfwp_x20240301").editable())
			.section("ttf").item("wp").build(metaControls_).init(configurable);
		Label_.on(expTtf).grabHorSpace().build();
		Chain.on(expTtf).lbl(l -> l.key("dialog.template.ttf.outputswitch")).cmb(c -> c.tooltip("dialog.template.ttf.outputswitch.tooltip").items("profile", "ttfoutputswitch_x20240301"))
			.section("ttf").item("outputswitch").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createDosPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.dosbox", "dialog.template.dos", parent, 1);
		Group expDos = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(new GridLayout(5, false)).key("dialog.template.tab.experimentaloptions").build();
		
		Chain.on(expDos).lbl(l -> l.key("dialog.template.dos.version")).cmb(c -> c.tooltip("dialog.template.dos.version.tooltip").editable().items("profile", configurable.dosverSectionItem()).dynItem(GenerationAwareConfigurable::dosverSectionItem))
			.section("dos").item("ver").build(metaControls_).init(configurable);
		Label_.on(expDos).grabHorSpace().build();
		Chain.on(expDos).lbl(l -> l.key("dialog.template.dosclipboardapi")).but(b -> b.tooltip("dialog.template.dosclipboardapi.tooltip")).section("dos").item("dos clipboard api")
			.build(metaControls_).init(configurable);
		
		Chain.on(expDos).lbl(l -> l.key("dialog.template.dos.setverfile")).txt(t -> t.tooltip("dialog.template.dos.setverfile.tooltip")).section("dos").item("setver_table_file").build(metaControls_).init(configurable);
		Label_.on(expDos).grabHorSpace().build();
		Chain.on(expDos).lbl(l -> l.key("dialog.template.dosclipboarddevice")).cmb(c -> c.tooltip("dialog.template.dosclipboarddevice.tooltip").items("profile", "dosclipboarddevice_x20240301"))
			.section("dos").item("dos clipboard device enable").build(metaControls_).init(configurable);
		
		Chain.on(expDos).lbl(l -> l.key("dialog.template.fat32setversion")).cmb(c -> c.tooltip("dialog.template.fat32setversion.tooltip").items("profile", "fat32setversion_x20240301"))
			.section("dos").item("fat32setversion").build(metaControls_).init(configurable);
		Label_.on(expDos).grabHorSpace().build();
		Chain.on(expDos).lbl(l -> l.key("dialog.template.dosclipboarddevicename")).txt(t -> t.tooltip("dialog.template.dosclipboarddevicename.tooltip"))
			.section("dos").item("dos clipboard device name").build(metaControls_).init(configurable);
		
		Chain.on(expDos).lbl(l -> l.key("dialog.template.dos.localeperiod")).cmb(c -> c.tooltip("dialog.template.dos.localeperiod.tooltip").items("profile", configurable.localeperiodSectionItem()).dynItem(GenerationAwareConfigurable::localeperiodSectionItem)).section("dos").item("locale_period").build(
			metaControls_).init(configurable);
		Label_.on(expDos).grabHorSpace().build();
		Chain.on(expDos).lbl(l -> l.key("dialog.template.dos.country")).txt(t -> t.tooltip("dialog.template.dos.country.tooltip")).section("dos").item("country").build(
			metaControls_).init(configurable);
		
		Chain.on(expDos).lbl(l -> l.key("dialog.template.dos.expandshellvar")).cmb(c -> c.tooltip("dialog.template.dos.expandshellvar.tooltip").items("profile", configurable.expandshellvarSectionItem()).dynItem(GenerationAwareConfigurable::expandshellvarSectionItem)).section("dos").item("expand_shell_variable").build(
			metaControls_).init(configurable);
		Label_.on(expDos).grabHorSpace().build();
		Chain.on(expDos).lbl(l -> l.key("dialog.template.dos.shellhistoryfile")).txt(t -> t.tooltip("dialog.template.dos.shellhistoryfile.tooltip")).section("dos").item("shell_history_file").build(
				metaControls_).init(configurable);
		
		Chain.on(expDos).lbl(l -> l.key("dialog.template.longfilenames")).cmb(c -> c.tooltip("dialog.template.longfilenames.tooltip").items("profile", "longfilenames_x20240301"))
			.section("dos").item("lfn").build(metaControls_).init(configurable);
		Label_.on(expDos).grabHorSpace().build();
		Chain.on(expDos).lbl(l -> l.key("dialog.template.fileaccesstries")).spn(s -> s.min(-1).tooltip("dialog.template.fileaccesstries.tooltip"))
			.section("dos").item("file access tries").build(metaControls_).init(configurable);

		Chain.on(expDos).lbl(l -> l.key("dialog.template.quickreboot")).but(b -> b.tooltip("dialog.template.quickreboot.tooltip"))
			.section("dos").item("quick reboot").build(metaControls_).init(configurable);
		Label_.on(expDos).grabHorSpace().build();
		Chain.on(expDos).lbl(l -> l.key("dialog.template.share")).cmb(c -> c.tooltip("dialog.template.share.tooltip").items("profile", configurable.filelockingSectionItem()).dynItem(GenerationAwareConfigurable::filelockingSectionItem))
			.section("dos").item("share", "file_locking").newItemLabel("dialog.template.filelocking").build(metaControls_).init(configurable);
		
		Chain.on(expDos).lbl(l -> l.key("dialog.template.doslogconsole")).cmb(c -> c.tooltip("dialog.template.doslogconsole.tooltip").items("profile", "doslogconsole_x20240301"))
			.section("dos").item("log console").build(metaControls_).init(configurable);
		Label_.on(expDos).grabHorSpace().build();
		Chain.on(expDos).lbl(l -> l.key("dialog.template.autofixwarning")).cmb(c -> c.tooltip("dialog.template.autofixwarning.tooltip").items("profile", "autofixwarning_x20240301"))
			.section("dos").item("autofixwarning").build(metaControls_).init(configurable);
		
		return composite;
	}

	protected Composite createConfigSysPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.dos", "dialog.template.tab.configsys", parent, 1);
		Group expConfigSys = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(new GridLayout(8, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.shell")).cmb(c -> c.horSpan(7).tooltip("dialog.template.configsys.shell.tooltip").items("profile", "configsysshell_x20240301").editable())
			.section("config").item("shell").build(metaControls_).init(configurable);
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.dos")).txt(t -> t.horSpan(7).tooltip("dialog.template.configsys.dos.tooltip"))
			.section("config").item("dos").build(metaControls_).init(configurable);
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.fcbs")).spn(s -> s.horSpan(7).min(0).max(255).tooltip("dialog.template.configsys.fcbs.tooltip"))
			.section("config").item("fcbs").build(metaControls_).init(configurable);
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.files")).spn(s -> s.horSpan(7).min(0).max(255).tooltip("dialog.template.configsys.files.tooltip"))
			.section("config").item("files").build(metaControls_).init(configurable);
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.country")).txt(t -> t.horSpan(7).tooltip("dialog.template.configsys.country.tooltip"))
			.section("config").item("country").build(metaControls_).init(configurable);
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.lastdrive")).cmb(c -> c.tooltip("dialog.template.configsys.lastdrive.tooltip").items("profile", "configsyslastdrive_x20240301"))
			.section("config").item("lastdrive").build(metaControls_).init(configurable);
		Label_.on(expConfigSys).grabHorSpace().build();
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.numlock")).cmb(c -> c.tooltip("dialog.template.configsys.numlock.tooltip").items("profile", "configsysnumlock_x20240301").editable())
			.section("config").item("numlock").build(metaControls_).init(configurable);
		Label_.on(expConfigSys).grabHorSpace().build();
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.break")).but(b -> b.tooltip("dialog.template.configsys.break.tooltip"))
			.section("config").item("break").onOff().build(metaControls_).init(configurable);
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.setpath")).txt(t -> t.horSpan(7)).section("config").item("set path").build(metaControls_).init(configurable);
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.setprompt")).txt(t -> t.horSpan(7)).section("config").item("set prompt").build(metaControls_).init(configurable);
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.settemp")).txt(t -> t.horSpan(7)).section("config").item("set temp").build(metaControls_).init(configurable);
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.install")).txt(t -> t.horSpan(7)).section("config").item("install").build(metaControls_).init(configurable);
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.installhigh")).txt(t -> t.horSpan(7)).section("config").item("installhigh").build(metaControls_).init(configurable);
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.device")).txt(t -> t.horSpan(7)).section("config").item("device").build(metaControls_).init(configurable);
		Chain.on(expConfigSys).lbl(l -> l.key("dialog.template.configsys.devicehigh")).txt(t -> t.horSpan(7)).section("config").item("devicehigh").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createDosVPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.dos", "dialog.template.tab.dosv", parent, 1);
		Group expDosv = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(new GridLayout(8, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.dosvemu")).cmb(c -> c.tooltip("dialog.template.dosvemu.tooltip").items("profile", "dosv_x20240301"))
			.section("dosv").item("dosv").build(metaControls_).init(configurable);
		Label_.on(expDosv).grabHorSpace().build();
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.dosdbcs")).but(b -> b.tooltip("dialog.template.dosdbcs.tooltip"))
			.section("dos").item("dbcs").build(metaControls_).init(configurable);
		Label_.on(expDosv).grabHorSpace().build();
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.dosvgetsysfont")).but(b -> b.tooltip("dialog.template.dosvgetsysfont.tooltip"))
			.section("dosv").item("getsysfont").build(metaControls_).init(configurable);
		
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.showdbcsnodosv")).cmb(c -> c.tooltip("dialog.template.showdbcsnodosv.tooltip").items("profile", "showdbcsnodosv_x20240301"))
			.section("dosv").item("showdbcsnodosv").build(metaControls_).init(configurable);
		Label_.on(expDosv).grabHorSpace().build();
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.dosvyen")).but(b -> b.tooltip("dialog.template.dosvyen.tooltip"))
			.section("dosv").item("yen").build(metaControls_).init(configurable);
		Label_.on(expDosv).grabHorSpace().build();
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.fepcontrol")).cmb(c -> c.tooltip("dialog.template.fepcontrol.tooltip").items("profile", "fepcontrol_x20240301"))
			.section("dosv").item("fepcontrol").build(metaControls_).init(configurable);
		
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.vtext1")).cmb(c -> c.tooltip("dialog.template.vtext1.tooltip").items("profile", "vtext1_x20240301"))
			.section("dosv").item("vtext1").build(metaControls_).init(configurable);
		Label_.on(expDosv).grabHorSpace().build();
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.vtext2")).cmb(c -> c.tooltip("dialog.template.vtext2.tooltip").items("profile", "vtext2_x20240301"))
			.section("dosv").item("vtext2").build(metaControls_).init(configurable);
		Label_.on(expDosv).grabHorSpace().build();
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.dosvuse20pixelfont")).but(b -> b.tooltip("dialog.template.dosvuse20pixelfont.tooltip"))
			.section("dosv").item("use20pixelfont").build(metaControls_).init(configurable);
		
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.j3100")).cmb(c -> c.tooltip("dialog.template.j3100.tooltip").items("profile", "dosvj3100_x20240301"))
			.section("dosv").item("j3100").build(metaControls_).init(configurable);
		Label_.on(expDosv).grabHorSpace().build();
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.j3100type")).cmb(c -> c.tooltip("dialog.template.j3100type.tooltip").items("profile", "dosvj3100type_x20240301"))
			.section("dosv").item("j3100type").build(metaControls_).init(configurable);
		Label_.on(expDosv).grabHorSpace().build();
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.dosvj3100colorscroll")).but(b -> b.tooltip("dialog.template.dosvj3100colorscroll.tooltip"))
			.section("dosv").item("j3100colorscroll").build(metaControls_).init(configurable);
		
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.dosvfontxsbcs")).txt(t -> t.horSpan(7).tooltip("dialog.template.dosvfontxsbcs.tooltip"))
			.section("dosv").item("fontxsbcs").build(metaControls_).init(configurable);
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.dosvfontxsbcs16")).txt(t -> t.horSpan(7).tooltip("dialog.template.dosvfontxsbcs16.tooltip"))
			.section("dosv").item("fontxsbcs16").build(metaControls_).init(configurable);
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.dosvfontxsbcs24")).txt(t -> t.horSpan(7).tooltip("dialog.template.dosvfontxsbcs24.tooltip"))
			.section("dosv").item("fontxsbcs24").build(metaControls_).init(configurable);
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.dosvfontxdbcs")).txt(t -> t.horSpan(7).tooltip("dialog.template.dosvfontxdbcs.tooltip"))
			.section("dosv").item("fontxdbcs").build(metaControls_).init(configurable);
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.dosvfontxdbcs14")).txt(t -> t.horSpan(7).tooltip("dialog.template.dosvfontxdbcs14.tooltip"))
			.section("dosv").item("fontxdbcs14").build(metaControls_).init(configurable);
		Chain.on(expDosv).lbl(l -> l.key("dialog.template.dosvfontxdbcs24")).txt(t -> t.horSpan(7).tooltip("dialog.template.dosvfontxdbcs24.tooltip"))
			.section("dosv").item("fontxdbcs24").build(metaControls_).init(configurable);
		
		return composite;
	}
	
	protected Composite createSystemPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.system", parent, 2);
		Chain.on(composite).lbl(l -> l.key("dialog.template.machine")).cmb(c -> c.tooltip("dialog.template.machine.tooltip")
				.items("profile", configurable.machineSectionItem()).dynItem(GenerationAwareConfigurable::machineSectionItem)
				.visibleItemCount(20)).section("dosbox").item("machine").build(metaControls_).init(configurable);
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		gd.verticalIndent = 20;
		Group expSystem = Group_.on(composite).layoutData(gd).layout(new GridLayout(5, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expSystem).lbl(l -> l.key("dialog.template.integrationdevice")).but(b -> b.tooltip("dialog.template.integrationdevice.tooltip"))
			.section("cpu").item("integration device").build(metaControls_).init(configurable);
		Label_.on(expSystem).grabHorSpace().build();
		Chain.on(expSystem).lbl(l -> l.key("dialog.template.vmware")).but(b -> b.tooltip("dialog.template.vmware.tooltip"))
			.section("dos").item("vmware").build(metaControls_).init(configurable);

		Chain.on(expSystem).lbl(l -> l.key("dialog.template.pc98256color")).but(b -> b.tooltip("dialog.template.pc98256color.tooltip"))
			.section("pc98").item("pc-98 enable 256-color").build(metaControls_).init(configurable);
		Label_.on(expSystem).grabHorSpace().build();
		Chain.on(expSystem).lbl(l -> l.key("dialog.template.pc98anex86font")).txt(t -> t.tooltip("dialog.template.pc98anex86font.tooltip"))
			.section("pc98").item("pc-98 anex86 font").build(metaControls_).init(configurable);
		
		Chain.on(expSystem).lbl(l -> l.key("dialog.template.pc9816color")).but(b -> b.tooltip("dialog.template.pc9816color.tooltip"))
			.section("pc98").item("pc-98 enable 16-color").build(metaControls_).init(configurable);
		Label_.on(expSystem).grabHorSpace().build();
		Chain.on(expSystem).lbl(l -> l.key("dialog.template.pc98fmboard")).cmb(c -> c.tooltip("dialog.template.pc98fmboard.tooltip").items("profile", "pc98fmboard_x20240301"))
			.section("pc98").item("pc-98 fm board").build(metaControls_).init(configurable);
		
		Chain.on(expSystem).lbl(l -> l.key("dialog.template.pc98grcg")).but(b -> b.tooltip("dialog.template.pc98grcg.tooltip"))
			.section("pc98").item("pc-98 enable grcg").build(metaControls_).init(configurable);
		Label_.on(expSystem).grabHorSpace().build();
		Chain.on(expSystem).lbl(l -> l.key("dialog.template.pc98busmouse")).but(b -> b.tooltip("dialog.template.pc98busmouse.tooltip"))
			.section("pc98").item("pc-98 bus mouse").build(metaControls_).init(configurable);

		Chain.on(expSystem).lbl(l -> l.key("dialog.template.pc98egc")).but(b -> b.tooltip("dialog.template.pc98egc.tooltip"))
			.section("pc98").item("pc-98 enable egc").build(metaControls_).init(configurable);
		Label_.on(expSystem).grabHorSpace().build();
		Chain.on(expSystem).lbl(l -> l.key("dialog.template.pc98forceibmkeyboard")).cmb(c ->c.tooltip("dialog.template.pc98forceibmkeyboard.tooltip").items("profile", "pc98forceibmkeyboard_x20240301"))
			.section("pc98").item("pc-98 force ibm keyboard layout").build(metaControls_).init(configurable);
		
		Chain.on(expSystem).lbl(l -> l.key("dialog.template.pc98fontrom")).but(b -> b.tooltip("dialog.template.pc98fontrom.tooltip"))
			.section("pc98").item("pc-98 try font rom").build(metaControls_).init(configurable);
		Label_.on(expSystem).grabHorSpace().build();
		Chain.on(expSystem).lbl(l -> l.key("dialog.template.pc98forcejiskeyboard")).cmb(c -> c.tooltip("dialog.template.pc98forcejiskeyboard.tooltip").items("profile", "pc98forcejiskeyboard_x20240301"))
			.section("pc98").item("pc-98 force jis keyboard layout").build(metaControls_).init(configurable);
		
		Chain.on(expSystem).lbl(l -> l.key("dialog.template.pc98biosstring")).but(b -> b.tooltip("dialog.template.pc98biosstring.tooltip"))
			.section("pc98").item("pc-98 bios copyright string").build(metaControls_).init(configurable);
		
		return composite;
	}
	
	protected Composite createCpuPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.system", "dialog.template.tab.cpu", parent, 6);
		Chain.on(composite).lbl(l -> l.key("dialog.template.core")).cmb(c -> c.horSpan(5).tooltip("dialog.template.core.tooltip").items("profile", configurable.coreSectionItem()).dynItem(GenerationAwareConfigurable::coreSectionItem))
			.section("cpu").item("core").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.cputype")).cmb(c -> c.horSpan(5).tooltip("dialog.template.cputype.tooltip").items("profile", configurable.cputypeSectionItem()).dynItem(GenerationAwareConfigurable::cputypeSectionItem))
			.section("cpu").item("cputype").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.cycles")).cmb(c -> c.editable().tooltip("dialog.template.cycles.tooltip").items("profile", "cycles").visibleItemCount(15)).section(
			"cpu").item("cycles").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.up")).cmb(c -> c.editable().tooltip("dialog.template.up.tooltip").items("profile", "cycles_up")).section("cpu").item("cycleup").build(
			metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.down")).cmb(c -> c.editable().tooltip("dialog.template.down.tooltip").items("profile", "cycles_down")).section("cpu").item(
			"cycledown").build(metaControls_).init(configurable);
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 6, 1);
		gd.verticalIndent = 20;
		Group expCpu = Group_.on(composite).layoutData(gd).layout(new GridLayout(4, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.cpucycles")).cmb(c -> c.editable().tooltip("dialog.template.cpucycles.tooltip").items("profile", "cpucycles_staging0820").visibleItemCount(15))
			.section("cpu").item("cpu_cycles").build(metaControls_).init(configurable);
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.cpucyclesprotected")).cmb(c -> c.editable().tooltip("dialog.template.cpucyclesprotected.tooltip").items("profile", "cpucyclesprotected_staging0820").visibleItemCount(15))
			.section("cpu").item("cpu_cycles_protected").build(metaControls_).init(configurable);
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.cputhrottle")).but(b -> b.horSpan(3).tooltip("dialog.template.cputhrottle.tooltip"))
			.section("cpu").item("cpu_throttle").build(metaControls_).init(configurable);
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.fpuemulation")).cmb(c -> c.horSpan(3).tooltip("dialog.template.fpuemulation.tooltip").items("profile", "fpuemulation_x20240301"))
			.section("cpu").item("fpu").build(metaControls_).init(configurable);
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.rdtscrate")).spn(s -> s.horSpan(3).min(0).tooltip("dialog.template.rdtscrate.tooltip"))
			.section("cpu").item("rdtsc rate").build(metaControls_).init(configurable);
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.dosidleapi")).but(b -> b.horSpan(3).tooltip("dialog.template.dosidleapi.tooltip"))
			.section("dos").item("dos idle api").build(metaControls_).init(configurable);
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.segmentlimits")).but(b -> b.horSpan(3).tooltip("dialog.template.segmentlimits.tooltip"))
			.section("cpu").item("segment limits").build(metaControls_).init(configurable);
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.speedmods")).but(b -> b.horSpan(3).tooltip("dialog.template.speedmods.tooltip")).section("dosbox").item("speed_mods").build(metaControls_).init(configurable);
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.turbo")).but(b -> b.horSpan(3).tooltip("dialog.template.turbo.tooltip"))
			.section("cpu").item("turbo").build(metaControls_).init(configurable);
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.outputportreset")).but(b -> b.horSpan(3).tooltip("dialog.template.outputportreset.tooltip"))
			.section("keyboard").item("allow output port reset").build(metaControls_).init(configurable);
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.apmbios")).but(b -> b.horSpan(3).tooltip("dialog.template.apmbios.tooltip"))
			.section("cpu").item("apmbios").build(metaControls_).init(configurable);
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.isapnpbios")).but(b -> b.horSpan(3).tooltip("dialog.template.isapnpbios.tooltip"))
			.section("cpu").item("isapnpbios").build(metaControls_).init(configurable);
		Chain.on(expCpu).lbl(l -> l.key("dialog.template.cpuidle")).but(b -> b.horSpan(3).tooltip("dialog.template.cpuidle.tooltip"))
			.section("cpu").item("cpu_idle").build(metaControls_).init(configurable);
	
		return composite;
	}
	
	protected Composite createMemoryPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.system", "dialog.template.tab.memory", parent, 4);
		Chain.on(composite).lbl(l -> l.key("dialog.template.memorysize")).cmb(c -> c.horSpan(3).tooltip("dialog.template.memorysize.tooltip").items("profile", "memsize")).section("dosbox").item(
			"memsize").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.xms")).but(b -> b.horSpan(3).tooltip("dialog.template.xms.tooltip")).section("dos").item("xms").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.ems")).cmb(c -> c.horSpan(3).tooltip("dialog.template.ems.tooltip").items("profile", configurable.emsSectionItem()).dynItem(GenerationAwareConfigurable::emsSectionItem))
			.section("dos").item("ems").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.umb")).but(b -> b.horSpan(3).tooltip("dialog.template.umb.tooltip"))
			.section("dos").item("umb").build(metaControls_).init(configurable);
		
		createMemoryPageAdditions(configurable, composite);
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 4, 1);
		gd.verticalIndent = 20;
		Group expMemory = Group_.on(composite).layoutData(gd).layout(new GridLayout(2, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expMemory).lbl(l -> l.key("dialog.template.hma")).but(b -> b.tooltip("dialog.template.hma.tooltip"))
			.section("dos").item("hma").build(metaControls_).init(configurable);
		Chain.on(expMemory).lbl(l -> l.key("dialog.template.shellhigh")).cmb(c -> c.tooltip("dialog.template.shellhigh.tooltip").items("profile", "shellhigh_x20240301"))
			.section("dos").item("shellhigh").build(metaControls_).init(configurable);
		Chain.on(expMemory).lbl(l -> l.key("dialog.template.xmshandles")).spn(s -> s.tooltip("dialog.template.xmshandles.tooltip").min(0)).section("dos").item("xms handles").build(metaControls_).init(configurable);
		Chain.on(expMemory).lbl(l -> l.key("dialog.template.minimummcbfree")).spn(s -> s.tooltip("dialog.template.minimummcbfree.tooltip").min(0)).section("dos").item("minimum mcb free").build(metaControls_).init(configurable);
		Chain.on(expMemory).lbl(l -> l.key("dialog.template.a20")).cmb(c -> c.tooltip("dialog.template.a20.tooltip").items("profile", "a20_x20240301"))
			.section("dosbox").item("a20").build(metaControls_).init(configurable);
		Chain.on(expMemory).lbl(l -> l.key("dialog.template.mcbfaults")).cmb(c -> c.tooltip("dialog.template.mcbfaults.tooltip").items("profile", "mcbfaults_staging0810"))
			.section("dosbox").item("mcb_fault_strategy").build(metaControls_).init(configurable);
		Chain.on(expMemory).lbl(l -> l.key("dialog.template.pcjrmemorylayout")).cmb(c -> c.tooltip("dialog.template.pcjrmemorylayout.tooltip").items("profile", "pcjrmemorylayout_staging0811"))
			.section("dos").item("pcjr_memory_config").build(metaControls_).init(configurable);
		Chain.on(expMemory).lbl(l -> l.key("dialog.template.memoryfile")).txt(t -> t.tooltip("dialog.template.memoryfile.tooltip"))
			.section("dosbox").item("memory file").build(metaControls_).init(configurable);

		return composite;
	}
	
	protected void createMemoryPageAdditions(TemplateProfileBase configurable, Composite composite) {}
	
	protected Composite createDisksPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.system", "dialog.template.tab.disks", parent, 1);
		Group expDisks = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(new GridLayout(5, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.fdc1enable")).but(b -> b.tooltip("dialog.template.fdc1enable.tooltip")).section("fdc, primary").item("enable").build(metaControls_).init(configurable);
		Label_.on(expDisks).grabHorSpace().build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.fdc1pnp")).but(b -> b.tooltip("dialog.template.fdc1pnp.tooltip")).section("fdc, primary").item("pnp").build(metaControls_).init(configurable);
		
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.floppydatarate")).spn(s -> s.min(-1).tooltip("dialog.template.floppydatarate.tooltip")).section("dos").item("floppy drive data rate limit").build(metaControls_).init(configurable);
		Label_.on(expDisks).grabHorSpace().build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.fdc1mode")).cmb(c -> c.tooltip("dialog.template.fdc1mode.tooltip").items("profile", "fdc1mode_x20240301"))
			.section("fdc, primary").item("mode").build(metaControls_).init(configurable);
		
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide1enable")).but(b -> b.tooltip("dialog.template.ide1enable.tooltip")).section("ide, primary").item("enable").build(metaControls_).init(configurable);
		Label_.on(expDisks).grabHorSpace().build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide1pnp")).but(b -> b.tooltip("dialog.template.ide1pnp.tooltip")).section("ide, primary").item("pnp").build(metaControls_).init(configurable);
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide2enable")).but(b -> b.tooltip("dialog.template.ide1enable.tooltip")).section("ide, secondary").item("enable").build(metaControls_).init(configurable);
		Label_.on(expDisks).grabHorSpace().build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide2pnp")).but(b -> b.tooltip("dialog.template.ide1pnp.tooltip")).section("ide, secondary").item("pnp").build(metaControls_).init(configurable);
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide3enable")).but(b -> b.tooltip("dialog.template.ide1enable.tooltip")).section("ide, tertiary").item("enable").build(metaControls_).init(configurable);
		Label_.on(expDisks).grabHorSpace().build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide3pnp")).but(b -> b.tooltip("dialog.template.ide1pnp.tooltip")).section("ide, tertiary").item("pnp").build(metaControls_).init(configurable);
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide4enable")).but(b -> b.tooltip("dialog.template.ide1enable.tooltip")).section("ide, quaternary").item("enable").build(metaControls_).init(configurable);
		Label_.on(expDisks).grabHorSpace().build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide4pnp")).but(b -> b.tooltip("dialog.template.ide1pnp.tooltip")).section("ide, quaternary").item("pnp").build(metaControls_).init(configurable);
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide5enable")).but(b -> b.tooltip("dialog.template.ide1enable.tooltip")).section("ide, quinternary").item("enable").build(metaControls_).init(configurable);
		Label_.on(expDisks).grabHorSpace().build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide5pnp")).but(b -> b.tooltip("dialog.template.ide1pnp.tooltip")).section("ide, quinternary").item("pnp").build(metaControls_).init(configurable);
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide6enable")).but(b -> b.tooltip("dialog.template.ide1enable.tooltip")).section("ide, sexternary").item("enable").build(metaControls_).init(configurable);
		Label_.on(expDisks).grabHorSpace().build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide6pnp")).but(b -> b.tooltip("dialog.template.ide1pnp.tooltip")).section("ide, sexternary").item("pnp").build(metaControls_).init(configurable);
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide7enable")).but(b -> b.tooltip("dialog.template.ide1enable.tooltip")).section("ide, sexternary").item("enable").build(metaControls_).init(configurable);
		Label_.on(expDisks).grabHorSpace().build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide7pnp")).but(b -> b.tooltip("dialog.template.ide1pnp.tooltip")).section("ide, sexternary").item("pnp").build(metaControls_).init(configurable);
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide8enable")).but(b -> b.tooltip("dialog.template.ide1enable.tooltip")).section("ide, sexternary").item("enable").build(metaControls_).init(configurable);
		Label_.on(expDisks).grabHorSpace().build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.ide8pnp")).but(b -> b.tooltip("dialog.template.ide1pnp.tooltip")).section("ide, sexternary").item("pnp").build(metaControls_).init(configurable);

		Chain.on(expDisks).lbl(l -> l.key("dialog.template.hddatarate")).spn(s -> s.min(-1).tooltip("dialog.template.hddatarate.tooltip")).section("dos").item("hard drive data rate limit").build(metaControls_).init(configurable);
		Label_.on(expDisks).horSpan(3).build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.harddiskspeed")).cmb(c -> c.tooltip("dialog.template.harddiskspeed.tooltip").items("profile", "diskspeed_staging0830"))
			.section("dosbox").item("hard_disk_speed").build(metaControls_).init(configurable);
		Label_.on(expDisks).grabHorSpace().build();
		Chain.on(expDisks).lbl(l -> l.key("dialog.template.floppydiskspeed")).cmb(c -> c.tooltip("dialog.template.floppydiskspeed.tooltip").items("profile", "diskspeed_staging0830"))
			.section("dosbox").item("floppy_disk_speed").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createVideoPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.video", parent, 1);
		Group expVideo = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(new GridLayout(5, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.vmemsizemb")).cmb(c -> c.tooltip("dialog.template.vmemsizemb.tooltip").editable().items("profile", "vmemsizemb_x20240301")).section("video").item("vmemsize").build(metaControls_).init(configurable);
		Label_.on(expVideo).grabHorSpace().build();
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.scanlineondemand")).cmb(c -> c.tooltip("dialog.template.scanlineondemand.tooltip").items("profile", "scanlineondemand_x20240301"))
			.section("video").item("scanline render on demand").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.vmemsizekb")).spn(s -> s.tooltip("dialog.template.vmemsizekb.tooltip").min(0)).section("video").item("vmemsizekb").build(metaControls_).init(configurable);
		Label_.on(expVideo).grabHorSpace().build();
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.vgarenderperscanline")).but(b -> b.tooltip("dialog.template.vgarenderperscanline.tooltip")).section("dosbox").item("vga_render_per_scanline").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.vmemsize")).cmb(c -> c.tooltip("dialog.template.vmemsize.tooltip").items("profile", "vmemsize_staging0810")).section("dosbox").item("vmemsize").build(metaControls_).init(configurable);
		Label_.on(expVideo).grabHorSpace().build();
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.ioopt1")).but(b -> b.tooltip("dialog.template.ioopt1.tooltip")).section("video").item("memory io optimization 1").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.vmemdelay")).cmb(c -> c.tooltip("dialog.template.vmemdelay.tooltip").editable().items("profile", "vmemdelay_staging0811")).section("dosbox").item("vmem_delay").build(metaControls_).init(configurable);
		Label_.on(expVideo).grabHorSpace().build();
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.dosrate")).cmb(c -> c.tooltip("dialog.template.dosrate.tooltip").editable().items("profile", "dosrate_staging0810")).section("dosbox").item("dos_rate").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.monochromepalette")).cmb(c -> c.editable().tooltip("dialog.template.monochromepalette.tooltip").items("profile", configurable.monochromepalSectionItem()).dynItem(GenerationAwareConfigurable::monochromepalSectionItem))
			.section("render").item("monochrome_palette", "monochrome_pal").build(metaControls_).init(configurable);
		Label_.on(expVideo).grabHorSpace().build();
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.ansi")).but(b -> b.tooltip("dialog.template.ansi.tooltip")).section("dos").item("ansi.sys").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.cgacolors")).cmb(c -> c.tooltip("dialog.template.cgacolors.tooltip").editable().items("profile", "cgacolors_staging0810"))
			.section("render").item("cga_colors").build(metaControls_).init(configurable);
		Label_.on(expVideo).grabHorSpace().build();
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.blinking")).but(b -> b.tooltip("dialog.template.blinking.tooltip")).section("video").item("high intensity blinking").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.vesamodes")).cmb(c -> c.tooltip("dialog.template.vesamodes.tooltip").items("profile", "vesamodes_staging0810")).section("dosbox").item("vesa_modes").build(metaControls_).init(configurable);
		Label_.on(expVideo).grabHorSpace().build();
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.8pxvgafont")).but(b -> b.tooltip("dialog.template.8pxvgafont.tooltip")).section("dosbox").item("vga_8dot_font").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.reelmagic")).cmb(c -> c.tooltip("dialog.template.reelmagic.tooltip").items("profile", "reelmagic_staging0810")).section("reelmagic").item("reelmagic").build(metaControls_).init(configurable);
		Label_.on(expVideo).grabHorSpace().build();		
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.reelmagicfcode")).spn(s -> s.min(0).max(7).tooltip("dialog.template.reelmagicfcode.tooltip")).section("reelmagic").item("reelmagic_fcode").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.reelmagickey")).txt(t -> t.tooltip("dialog.template.reelmagickey.tooltip")).section("reelmagic").item("reelmagic_key").build(metaControls_).init(configurable);
		return composite;
	}
		
	protected Composite createCgaCompositePage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.video", "dialog.template.tab.cgacomposite", parent, 1);
		Group expVideo = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(new GridLayout(2, false)).key("dialog.template.tab.experimentaloptions").build();	
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.composite")).cmb(c -> c.tooltip("dialog.template.composite.tooltip").items("profile", "composite_staging0810")).section("composite").item("composite").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.compositeera")).cmb(c -> c.tooltip("dialog.template.compositeera.tooltip").items("profile", "compositeera_staging0810")).section("composite").item("era").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.compositehue")).spn(s -> s.min(0).tooltip("dialog.template.compositehue.tooltip")).section("composite").item("hue").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.compositesaturation")).spn(s -> s.min(0).max(100).tooltip("dialog.template.compositesaturation.tooltip")).section("composite").item("saturation").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.compositecontrast")).spn(s -> s.min(0).max(100).tooltip("dialog.template.compositecontrast.tooltip")).section("composite").item("contrast").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.compositebrightness")).spn(s -> s.min(0).tooltip("dialog.template.compositebrightness.tooltip")).section("composite").item("brightness").build(metaControls_).init(configurable);
		Chain.on(expVideo).lbl(l -> l.key("dialog.template.compositeconvergence")).spn(s -> s.min(0).tooltip("dialog.template.compositeconvergence.tooltip")).section("composite").item("convergence").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createVoodooPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.video", "dialog.template.tab.voodoo", parent, 1);
		Group expVoodoo = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(new GridLayout(2, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expVoodoo).lbl(l -> l.key("dialog.template.voodoo3dfx")).but(b -> b.tooltip("dialog.template.voodoo3dfx.tooltip")).section("voodoo").item("voodoo").build(metaControls_).init(configurable);
		Chain.on(expVoodoo).lbl(l -> l.key("dialog.template.voodooemu")).cmb(c -> c.tooltip("dialog.template.voodooemu.tooltip").items("profile", configurable.voodooSectionItem()).dynItem(GenerationAwareConfigurable::voodooSectionItem))
			.section("pci", "voodoo").item("voodoo", "voodoo_card").build(metaControls_).init(configurable);
		Chain.on(expVoodoo).lbl(l -> l.key("dialog.template.voodoomem")).cmb(c -> c.tooltip("dialog.template.voodoomem.tooltip").items("profile", "voodoomem_ecer4482")).section("pci").item("voodoomem").build(metaControls_).init(configurable);
		Chain.on(expVoodoo).lbl(l -> l.key("dialog.template.voodoomem")).cmb(c -> c.tooltip("dialog.template.voodoomemsize.tooltip").items("profile", "voodoomemsize_staging0810")).section("voodoo").item("voodoo_memsize").build(metaControls_).init(configurable);
		Chain.on(expVoodoo).lbl(l -> l.key("dialog.template.voodoomaxmem")).but(b -> b.tooltip("dialog.template.voodoomaxmem.tooltip")).section("voodoo").item("voodoo_maxmem").build(metaControls_).init(configurable);
		Chain.on(expVoodoo).lbl(l -> l.key("dialog.template.glide")).but(b -> b.tooltip("dialog.template.glide.tooltip")).section("glide", "voodoo").item("glide", "glide").build(metaControls_).init(configurable);
		Chain.on(expVoodoo).lbl(l -> l.key("dialog.template.lfbglide")).cmb(c -> c.tooltip("dialog.template.lfbglide.tooltip").items("profile", configurable.lfbglideSectionItem()).dynItem(GenerationAwareConfigurable::lfbglideSectionItem))
			.section("glide", "voodoo").item("lfb", "lfb").build(metaControls_).init(configurable);
		Chain.on(expVoodoo).lbl(l -> l.key("dialog.template.splash3dfx")).but(b -> b.tooltip("dialog.template.splash3dfx.tooltip")).section("glide", "voodoo").item("splash", "splash").build(metaControls_).init(configurable);
		Chain.on(expVoodoo).lbl(l -> l.key("dialog.template.voodoomultithreading")).cmb(c -> c.tooltip("dialog.template.voodoomultithreading.tooltip").items("profile", "voodoothreads_staging0820").editable())
			.section("voodoo").item("voodoo_threads").build(metaControls_).init(configurable);
		Chain.on(expVoodoo).lbl(l -> l.key("dialog.template.voodoobilinearfiltering")).but(b -> b.tooltip("dialog.template.voodoobilinearfiltering.tooltip")).section("voodoo").item("voodoo_bilinear_filtering").build(metaControls_).init(configurable);
		return composite;
	}
		
	protected Composite createAudioPage(TemplateProfileBase configurable, Composite parent) {
		Composite generalGroup = createPage("dialog.template.tab.audio", parent, 3);
		Chain.on(generalGroup).lbl(l -> l.key("dialog.template.silentmode")).but(b -> b.horSpan(2).tooltip("dialog.template.silentmode.tooltip")).section("mixer").item("nosound").build(metaControls_).init(configurable);
		Chain.on(generalGroup).lbl(l -> l.key("dialog.template.samplerate")).cmb(c -> c.horSpan(2).tooltip("dialog.template.samplerate.tooltip").items("profile", "rate")).section("mixer").item(
			"rate").build(metaControls_).init(configurable);
		Chain.on(generalGroup).lbl(l -> l.key("dialog.template.blocksize")).cmb(c -> c.horSpan(2).tooltip("dialog.template.blocksize.tooltip").items("profile", "blocksize")).section("mixer").item(
			"blocksize").build(metaControls_).init(configurable);
		Chain.on(generalGroup).lbl(l -> l.key("dialog.template.prebuffer")).cmb(c -> c.horSpan(2).editable().tooltip("dialog.template.prebuffer.tooltip").items("profile", "prebuffer")).section(
			"mixer").item("prebuffer").build(metaControls_).init(configurable);
		Chain chnMixer = Chain.on(generalGroup).lbl(l -> l.key("dialog.template.mixercommand")).txt(t -> t.tooltip("dialog.template.mixercommand.tooltip")).autoexec(Autoexec::getMixer, Autoexec::setMixer).but(Button_.Builder::threedots).build(
			metaControls_).init(configurable);
		chnMixer.getButton().addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				String command = doOpenMixerDialog(chnMixer.getText().getText());
				if (command != null)
					chnMixer.getText().setText(command);
			}
		});
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		gd.verticalIndent = 20;
		Group audioExpGroup = Group_.on(generalGroup).layoutData(gd).layout(new GridLayout(5, false)).key(
			"dialog.template.tab.experimentaloptions").build();
		Chain.on(audioExpGroup).lbl(l -> l.key("dialog.template.mutewheninactive")).but(b -> b.tooltip("dialog.template.mutewheninactive.tooltip")).section("sdl").item("mute_when_inactive")
			.build(metaControls_).init(configurable);
		Label_.on(audioExpGroup).grabHorSpace().build();
		Chain.on(audioExpGroup).lbl(l -> l.key("dialog.template.audiocrossfeed")).cmb(c -> c.tooltip("dialog.template.audiocrossfeed.tooltip").items("profile", "audiocrossfeed_staging0810"))
			.section("mixer").item("crossfeed").build(metaControls_).init(configurable);
		
		Chain.on(audioExpGroup).lbl(l -> l.key("dialog.template.sampleaccurate")).but(b -> b.tooltip("dialog.template.sampleaccurate.tooltip")).section("mixer").item("sample accurate")
			.build(metaControls_).init(configurable);
		Label_.on(audioExpGroup).grabHorSpace().build();
		Chain.on(audioExpGroup).lbl(l -> l.key("dialog.template.audioreverb")).cmb(c -> c.tooltip("dialog.template.audioreverb.tooltip").items("profile", "audioreverb_staging0810"))
			.section("mixer").item("reverb").build(metaControls_).init(configurable);
	
		Chain.on(audioExpGroup).lbl(l -> l.key("dialog.template.audionegotiate")).but(b -> b.tooltip("dialog.template.audionegotiate.tooltip")).section("mixer").item("negotiate")
			.build(metaControls_).init(configurable);
		Label_.on(audioExpGroup).grabHorSpace().build();
		Chain.on(audioExpGroup).lbl(l -> l.key("dialog.template.audiochorus")).cmb(c -> c.tooltip("dialog.template.audiochorus.tooltip").items("profile", "audiochorus_staging0810"))
			.section("mixer").item("chorus").build(metaControls_).init(configurable);
		
		Chain.on(audioExpGroup).lbl(l -> l.key("dialog.template.swapstereo")).but(b -> b.tooltip("dialog.template.swapstereo.tooltip")).section("mixer").item("swapstereo")
			.build(metaControls_).init(configurable);
		Label_.on(audioExpGroup).grabHorSpace().build();
		Chain.on(audioExpGroup).lbl(l -> l.key("dialog.template.audioleveling")).but(b -> b.tooltip("dialog.template.audioleveling.tooltip")).section("mixer").item("compressor")
			.build(metaControls_).init(configurable);

		Chain.on(audioExpGroup).lbl(l -> l.key("dialog.template.audiodenoiser")).but(b -> b.tooltip("dialog.template.audiodenoiser.tooltip")).section("mixer").item("denoiser")
			.build(metaControls_).init(configurable);
		Label_.on(audioExpGroup).grabHorSpace().build();
		Chain.on(audioExpGroup).lbl(l -> l.key("dialog.template.harddisknoise")).cmb(c -> c.tooltip("dialog.template.harddisknoise.tooltip").items("profile", "disknoise_staging0830"))
			.section("disknoise").item("hard_disk_noise").build(metaControls_).init(configurable);

		Label_.on(audioExpGroup).horSpan(3).build();
		Chain.on(audioExpGroup).lbl(l -> l.key("dialog.template.floppydisknoise")).cmb(c -> c.tooltip("dialog.template.floppydisknoise.tooltip").items("profile", "disknoise_staging0830"))
			.section("disknoise").item("floppy_disk_noise").build(metaControls_).init(configurable);
		return generalGroup;
	}
	
	protected Composite createMidiPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.audio", "dialog.template.tab.midi", parent, 2);
		Chain.on(composite).lbl(l -> l.key("dialog.template.mpu401")).cmb(c -> c.tooltip("dialog.template.mpu401.tooltip").items("profile", "mpu401")).section("midi", "midi").item(
				"intelligent", "mpu401").convert(new DaControlConvertor() {
					@Override
					public String toConfValue(String existingValue, String[] values) {
						return values[0];
					}

					@Override
					public String toConfValueForDisplay(String[] values) {
						return toConfValue(null, values);
					}

					@Override
					public String[] toControlValues(String value) {
						if (value == null)
							return new String[0];
						return new String[] {value};
					}

					@Override
					public String[] toConfValues(String[] values) {
						String[] result = new String[2];
						result[0] = String.valueOf(!values[0].equalsIgnoreCase("none"));
						result[1] = String.valueOf(!values[0].equalsIgnoreCase("uart"));
						return result;
					}

					@Override
					public String[] toControlValues(String[] values) {
						boolean intelligent = Boolean.parseBoolean(values[0]);
						boolean mpu = Boolean.parseBoolean(values[1]);
						return new String[] {mpu ? intelligent ? "intelligent": "uart": "none"};
					}
				}).build(metaControls_).init(configurable);

		Chain.on(composite).lbl(l -> l.key("dialog.template.mididevice")).cmb(c -> c.tooltip("dialog.template.mididevice.tooltip").items("profile", configurable.midideviceSectionItem()).dynItem(GenerationAwareConfigurable::midideviceSectionItem))
			.section("midi").item("device", "mididevice").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.midiconfig")).txt(t -> t.tooltip("dialog.template.midiconfig.tooltip")).section("midi").item("config", "midiconfig").build(
			metaControls_).init(configurable);
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		gd.verticalIndent = 20;
		Group midiExpGroup = Group_.on(composite).layoutData(gd).layout(new GridLayout(2, false)).key(
				"dialog.template.tab.experimentaloptions").build();
		Chain.on(midiExpGroup).lbl(l -> l.key("dialog.template.midisamplerate")).cmb(c -> c.tooltip("dialog.template.midisamplerate.tooltip").items("profile", "midisamplerate_x20240301")).section("midi").item("samplerate").build(
				metaControls_).init(configurable);
		Chain.on(midiExpGroup).lbl(l -> l.key("dialog.template.mpubase")).cmb(c -> c.tooltip("dialog.template.mpubase.tooltip").items("profile", "mpubase_x20240301")).section("midi").item("mpubase").build(
				metaControls_).init(configurable);
		Chain.on(midiExpGroup).lbl(l -> l.key("dialog.template.mpuirq")).cmb(c -> c.tooltip("dialog.template.mpuirq.tooltip").items("profile", "mpuirq_x20240301")).section("midi").item("mpuirq").build(
				metaControls_).init(configurable);
		Chain.on(midiExpGroup).lbl(l -> l.key("dialog.template.rawmidioutput")).but(b -> b.tooltip("dialog.template.rawmidioutput.tooltip")).section("midi").item("raw_midi_output")
			.build(metaControls_).init(configurable);
		Chain.on(midiExpGroup).lbl(l -> l.key("dialog.template.soundcanvasmodel")).cmb(c -> c.tooltip("dialog.template.soundcanvasmodel.tooltip").items("profile", "soundcanvasmodel_staging0830"))
			.section("soundcanvas").item("soundcanvas_model").build(metaControls_).init(configurable);
		Chain.on(midiExpGroup).lbl(l -> l.key("dialog.template.soundcanvasromdir")).txt(t -> t.tooltip("dialog.template.soundcanvasromdir.tooltip"))
			.section("soundcanvas").item("soundcanvas_rom_dir").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createFluidsynthPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.midi", "dialog.template.tab.fluidsynth", parent, 1);
		Group fluidExpGroup = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, false)).layout(new GridLayout(3, false)).key(
			"dialog.template.tab.experimentaloptions").build();
		Chain.on(fluidExpGroup).lbl(l -> l.key("dialog.template.fluidsynth.driver")).cmb(c -> c.tooltip("dialog.template.fluidsynth.driver.tooltip").horSpan(2).items("profile", configurable.fluidsynthdriverSectionItem()).dynItem(GenerationAwareConfigurable::fluidsynthdriverSectionItem))
			.section("midi").item("fluid.driver").build(metaControls_).init(configurable);
		Chain.on(fluidExpGroup).lbl(l -> l.key("dialog.template.fluidsynth.soundfont")).txt(t -> t.tooltip("dialog.template.fluidsynth.soundfont.tooltip")).but(b -> b.browse(true, Button_.BrowseType.FILE, Button_.CanonicalType.NONE, false)).section(
				"midi", "fluidsynth").item("fluid.soundfont", "soundfont").build(metaControls_).init(configurable);
		Chain.on(fluidExpGroup).lbl(l -> l.key("dialog.template.fluidsynth.samplerate")).cmb(c -> c.tooltip("dialog.template.fluidsynth.samplerate.tooltip").horSpan(2).items("profile", "fluidsynthsamplerate_ecer4482")).section("midi").item("fluid.samplerate").build(
				metaControls_).init(configurable);
		Chain.on(fluidExpGroup).lbl(l -> l.key("dialog.template.fluidsynth.gain")).spn(s -> s.digits(1).tooltip("dialog.template.fluidsynth.gain.tooltip").horSpan(2)).section("midi").item("fluid.gain").build(metaControls_).init(configurable);
		Chain.on(fluidExpGroup).lbl(l -> l.key("dialog.template.fluidsynth.chorus")).cmb(c -> c.tooltip("dialog.template.fluidsynth.chorus.tooltip").horSpan(2).editable().items("profile", "fluidsynthchorus_staging0810")).section("fluidsynth").item("fsynth_chorus").build(
				metaControls_).init(configurable);
		Chain.on(fluidExpGroup).lbl(l -> l.key("dialog.template.fluidsynth.reverb")).cmb(c -> c.tooltip("dialog.template.fluidsynth.reverb.tooltip").horSpan(2).editable().items("profile", "fluidsynthreverb_staging0810")).section("fluidsynth").item("fsynth_reverb").build(
				metaControls_).init(configurable);
		Chain.on(fluidExpGroup).lbl(l -> l.key("dialog.template.fluidsynth.filter")).txt(t -> t.tooltip("dialog.template.fluidsynth.filter.tooltip").horSpan(2)).section("fluidsynth").item("fsynth_filter").build(metaControls_).init(configurable);
		Chain.on(fluidExpGroup).lbl(l -> l.key("dialog.template.fluidsynth.soundfontdir")).txt(t -> t.tooltip("dialog.template.fluidsynth.soundfontdir.tooltip")).but(b -> b.browse(false, Button_.BrowseType.DIR, Button_.CanonicalType.NONE, false))
			.section("fluidsynth").item("soundfont_dir").build(metaControls_).init(configurable);
		Chain.on(fluidExpGroup).lbl(l -> l.key("dialog.template.fluidsynth.soundfontvolume")).spn(s -> s.min(1).max(800).tooltip("dialog.template.fluidsynth.soundfontvolume.tooltip").horSpan(2))
			.section("fluidsynth").item("soundfont_volume").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createMt32Page(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.midi", "dialog.template.tab.mt32", parent, 1);
		Group mt32ExpGroup = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, false)).layout(new GridLayout(6, false)).key(
			"dialog.template.tab.experimentaloptions").build();
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.model")).cmb(c -> c.tooltip("dialog.template.mt32.model.tooltip").items("profile", configurable.mt32modelSectionItem()).dynItem(GenerationAwareConfigurable::mt32modelSectionItem))
			.section("mt32", "midi").item("model", "mt32.model").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.dac")).cmb(c -> c.tooltip("dialog.template.mt32.dac.tooltip").items("profile", "mt32dac_ecer4482").editable()).section("midi").item("mt32.dac").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.romdir")).txt(t -> t.horSpan(4).tooltip("dialog.template.mt32.romdir.tooltip")).but(b -> b.browse(true, Button_.BrowseType.DIR, Button_.CanonicalType.NONE, false)).section("midi", "mt32").item(
			"mt32.romdir", "romdir").build(metaControls_).init(configurable);
		
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.rate")).cmb(c -> c.tooltip("dialog.template.mt32.rate.tooltip").items("profile", "mt32rate_ecer4482")).section("midi").item(
				"mt32.rate").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.gain")).spn(s -> s.tooltip("dialog.template.mt32.gain.tooltip").min(0).max(1000)).section("midi").item("mt32.output.gain").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.analog")).cmb(c -> c.tooltip("dialog.template.mt32.analog.tooltip").items("profile", "mt32analog_ecer4482")).section("midi").item("mt32.analog").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.chunk")).spn(s -> s.tooltip("dialog.template.mt32.chunk.tooltip").min(2).max(100)).section("midi").item("mt32.chunk").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.srcquality")).cmb(c -> c.tooltip("dialog.template.mt32.srcquality.tooltip").items("profile", "mt32srcquality_ecer4482")).section("midi").item("mt32.src.quality").build(
				metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.prebuffer")).spn(s -> s.tooltip("dialog.template.mt32.prebuffer.tooltip").min(3).max(200)).section("midi").item("mt32.prebuffer").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.niceampramp")).but(b -> b.tooltip("dialog.template.mt32.niceampramp.tooltip")).section("midi").item("mt32.niceampramp").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.partials")).spn(s -> s.tooltip("dialog.template.mt32.partials.tooltip").min(0).max(256)).section("midi").item("mt32.partials").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.reverbmode")).cmb(c -> c.tooltip("dialog.template.mt32.reverbmode.tooltip").items("profile", "mt32reverbmode_ecer4482")).section("midi").item("mt32.reverb.mode").build(
			metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.reverboutputgain")).spn(s -> s.tooltip("dialog.template.mt32.reverboutputgain.tooltip").min(0).max(1000)).section("midi").item("mt32.reverb.output.gain").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.reverbtime")).cmb(c -> c.tooltip("dialog.template.mt32.reverbtime.tooltip").items("profile", "mt32reverbtime_ecer4482")).section("midi").item("mt32.reverb.time").build(
			metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.reverblevel")).cmb(c -> c.tooltip("dialog.template.mt32.reverblevel.tooltip").items("profile", "mt32reverblevel_ecer4482")).section("midi").item("mt32.reverb.level").build(
			metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.filter")).txt(t -> t.tooltip("dialog.template.mt32.filter.tooltip")).section("mt32").item("mt32_filter").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.swapstereo")).but(b -> b.tooltip("dialog.template.mt32.swapstereo.tooltip")).section("midi").item("mt32.reverse.stereo").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.multithread")).but(b -> b.tooltip("dialog.template.mt32.multithread.tooltip")).section("midi").item("mt32.thread").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		Chain.on(mt32ExpGroup).lbl(l -> l.key("dialog.template.mt32.verboselogging")).but(b -> b.tooltip("dialog.template.mt32.verboselogging.tooltip")).section("midi").item("mt32.verbose").build(metaControls_).init(configurable);
		Label_.on(mt32ExpGroup).build();
		
		return composite;
	}
	
	/** DOSBox-X can emulate a second Sound Blaster card, section [sblaster2] */
	protected Composite createSoundBlaster2Page(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.audio", "dialog.template.tab.soundblaster2", parent, 5);
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbtype")).cmb(c -> c.tooltip("dialog.template.sbtype.tooltip").items("profile", configurable.sbtypeSectionItem()).dynItem(GenerationAwareConfigurable::sbtypeSectionItem))
			.section("sblaster2").item("sbtype").build(metaControls_).init(configurable);
		Label_.on(composite).build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbaddress")).cmb(c -> c.editable().tooltip("dialog.template.sbaddress.tooltip").items("profile", configurable.sbbaseSectionItem())
				.dynItem(GenerationAwareConfigurable::sbbaseSectionItem)).section("sblaster2").item("sbbase").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.sboplrate")).cmb(c -> c.tooltip("dialog.template.sboplrate.tooltip").items("profile", "oplrate")).section("sblaster2").item("oplrate")
			.build(metaControls_).init(configurable);
		Label_.on(composite).build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbirq")).cmb(c -> c.editable().tooltip("dialog.template.sbirq.tooltip").items("profile", configurable.sbirqSectionItem()).dynItem(GenerationAwareConfigurable::sbirqSectionItem))
			.section("sblaster2").item("irq").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.sboplmode")).cmb(c -> c.tooltip("dialog.template.sboplmode.tooltip").items("profile", configurable.oplmodeSectionItem()).dynItem(GenerationAwareConfigurable::oplmodeSectionItem))
			.section("sblaster2").item("oplmode").build(metaControls_).init(configurable);
		Label_.on(composite).build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbdma")).cmb(c -> c.editable().tooltip("dialog.template.sbdma.tooltip").items("profile", configurable.sbdmaSectionItem()).dynItem(GenerationAwareConfigurable::sbdmaSectionItem))
			.section("sblaster2").item("dma").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.sboplemu")).cmb(c -> c.tooltip("dialog.template.sboplemu.tooltip").items("profile", configurable.oplemuSectionItem()).dynItem(GenerationAwareConfigurable::oplemuSectionItem))
			.section("sblaster2").item("oplemu").build(metaControls_).init(configurable);
		Label_.on(composite).build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbhdma")).cmb(c -> c.editable().tooltip("dialog.template.sbhdma.tooltip").items("profile", configurable.sbhdmaSectionItem()).dynItem(GenerationAwareConfigurable::sbhdmaSectionItem))
			.section("sblaster2").item("hdma").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.mixer")).but(b -> b.tooltip("dialog.template.mixer.tooltip")).section("sblaster2").item("sbmixer").build(metaControls_).init(configurable);
		Label_.on(composite).build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.cms")).cmb(c -> c.editable().tooltip("dialog.template.cms.tooltip").items("profile", configurable.sbcmsSectionItem()).dynItem(GenerationAwareConfigurable::sbcmsSectionItem))
			.section("sblaster2").item("cms").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbenablespeaker")).but(b -> b.tooltip("dialog.template.sbenablespeaker.tooltip")).section("sblaster2").item("enable speaker").build(metaControls_).init(configurable);
		Label_.on(composite).build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.goldplay")).but(b -> b.tooltip("dialog.template.goldplay.tooltip")).section("sblaster2").item("goldplay").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbenv")).but(b -> b.tooltip("dialog.template.sbenv.tooltip")).section("sblaster2").item("blaster environment variable").build(metaControls_).init(configurable);
		Label_.on(composite).build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbrecordingsource")).cmb(c -> c.tooltip("dialog.template.sbrecordingsource.tooltip").items("profile", "sbrecordingsource_x20240301")).section("sblaster2").item("recording source")
			.build(metaControls_).init(configurable);
		return composite;
	}

	protected Composite createSoundBlasterPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.audio", "dialog.template.tab.soundblaster", parent, 5);
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbtype")).cmb(c -> c.tooltip("dialog.template.sbtype.tooltip").items("profile", configurable.sbtypeSectionItem()).dynItem(GenerationAwareConfigurable::sbtypeSectionItem))
			.section("sblaster").item("type", "sbtype").build(metaControls_).init(configurable);
		Label_.on(composite).build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbaddress")).cmb(c -> c.tooltip("dialog.template.sbaddress.tooltip").items("profile", configurable.sbbaseSectionItem())
				.dynItem(GenerationAwareConfigurable::sbbaseSectionItem)).section("sblaster").item("base", "sbbase").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.sboplrate")).cmb(c -> c.tooltip("dialog.template.sboplrate.tooltip").items("profile", "oplrate")).section("sblaster").item(
			"oplrate").build(metaControls_).init(configurable);
		Label_.on(composite).build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbirq")).cmb(c -> c.tooltip("dialog.template.sbirq.tooltip").items("profile", configurable.sbirqSectionItem()).dynItem(GenerationAwareConfigurable::sbirqSectionItem))
			.section("sblaster").item("irq").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.sboplmode")).cmb(c -> c.tooltip("dialog.template.sboplmode.tooltip").items("profile", configurable.oplmodeSectionItem()).dynItem(GenerationAwareConfigurable::oplmodeSectionItem))
			.section("sblaster").item("oplmode").build(metaControls_).init(configurable);
		Label_.on(composite).build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbdma")).cmb(c -> c.tooltip("dialog.template.sbdma.tooltip").items("profile", configurable.sbdmaSectionItem()).dynItem(GenerationAwareConfigurable::sbdmaSectionItem))
			.section("sblaster").item("dma").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.sboplemu")).cmb(c -> c.tooltip("dialog.template.sboplemu.tooltip").items("profile", configurable.oplemuSectionItem()).dynItem(GenerationAwareConfigurable::oplemuSectionItem))
			.section("sblaster").item("oplemu").build(metaControls_).init(configurable);
		Label_.on(composite).build();
		Chain.on(composite).lbl(l -> l.key("dialog.template.sbhdma")).cmb(c -> c.tooltip("dialog.template.sbhdma.tooltip").items("profile", configurable.sbhdmaSectionItem()).dynItem(GenerationAwareConfigurable::sbhdmaSectionItem))
			.section("sblaster").item("hdma").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.mixer")).but(b -> b.tooltip("dialog.template.mixer.tooltip")).section("sblaster").item("mixer", "sbmixer").build(metaControls_).init(configurable);
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 5, 1);
		gd.verticalIndent = 20;
		Group soundblasterExpGroup = Group_.on(composite).layoutData(gd).layout(new GridLayout(5, false)).key(
				"dialog.template.tab.experimentaloptions").build();
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sbenv")).but(b -> b.tooltip("dialog.template.sbenv.tooltip")).section("sblaster").item("blaster environment variable").build(metaControls_).init(configurable);
		Label_.on(soundblasterExpGroup).grabHorSpace().build();
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sbwarmup")).spn(s -> s.tooltip("dialog.template.sbwarmup.tooltip").min(0).max(1000)).section("sblaster").item("sbwarmup")
			.build(metaControls_).init(configurable);
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sbenablespeaker")).but(b -> b.tooltip("dialog.template.sbenablespeaker.tooltip")).section("sblaster").item("enable speaker").build(metaControls_).init(configurable);
		Label_.on(soundblasterExpGroup).grabHorSpace().build();
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.fmstrength")).spn(s -> s.tooltip("dialog.template.fmstrength.tooltip").min(1).max(1000)).section("sblaster").item("fmstrength")
			.build(metaControls_).init(configurable);
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.cms")).cmb(c -> c.editable().tooltip("dialog.template.cms.tooltip").items("profile", configurable.sbcmsSectionItem()).dynItem(GenerationAwareConfigurable::sbcmsSectionItem))
			.section("sblaster").item("cms").build(metaControls_).init(configurable);
		Label_.on(soundblasterExpGroup).grabHorSpace().build();
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.oplremovedcbias")).but(b -> b.tooltip("dialog.template.oplremovedcbias.tooltip")).section("sblaster").item("opl_remove_dc_bias")
			.build(metaControls_).init(configurable);
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.goldplay")).but(b -> b.tooltip("dialog.template.goldplay.tooltip")).section("sblaster").item("goldplay").build(metaControls_).init(configurable);
		Label_.on(soundblasterExpGroup).grabHorSpace().build();
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sboplfadeout")).cmb(c -> c.editable().tooltip("dialog.template.sboplfadeout.tooltip").items("profile", "sboplfadeout_staging0810"))
			.section("sblaster").item("opl_fadeout").build(metaControls_).init(configurable);
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sbfilter")).cmb(c -> c.editable().tooltip("dialog.template.sbfilter.tooltip").items("profile", "sbfilter_staging0810")).section("sblaster").item("sb_filter").build(
				metaControls_).init(configurable);
		Label_.on(soundblasterExpGroup).grabHorSpace().build();
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sboplfilter")).cmb(c -> c.editable().tooltip("dialog.template.sboplfilter.tooltip").items("profile", "sboplfilter_staging0810")).section("sblaster").item("opl_filter").build(
				metaControls_).init(configurable);
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sbforcefilter")).but(b -> b.tooltip("dialog.template.sbforcefilter.tooltip")).section("sblaster").item("sb_filter_always_on").build(metaControls_).init(configurable);
		Label_.on(soundblasterExpGroup).grabHorSpace().build();
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sbcmsfilter")).cmb(c -> c.editable().tooltip("dialog.template.sbcmsfilter.tooltip").items("profile", "sbcmsfilter_staging0810")).section("sblaster").item("cms_filter").build(
				metaControls_).init(configurable);
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sblistentorecordingsource")).but(b -> b.tooltip("dialog.template.sblistentorecordingsource.tooltip")).section("sblaster").item("listen to recording source").build(metaControls_).init(configurable);
		Label_.on(soundblasterExpGroup).grabHorSpace().build();
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sbretrowavebus")).cmb(c -> c.tooltip("dialog.template.sbretrowavebus.tooltip").items("profile", "sbretrowavebus_x20240301")).section("sblaster").item("retrowave_bus").build(
				metaControls_).init(configurable);
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sbrecordingsource")).cmb(c -> c.tooltip("dialog.template.sbrecordingsource.tooltip").items("profile", "sbrecordingsource_x20240301")).section("sblaster").item("recording source").build(
				metaControls_).init(configurable);
		Label_.on(soundblasterExpGroup).grabHorSpace().build();
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.retrowaveport")).txt(t -> t.tooltip("dialog.template.retrowaveport.tooltip")).section("sblaster").item("retrowave_port").build(metaControls_).init(configurable);
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sboplport")).txt(t -> t.tooltip("dialog.template.sboplport.tooltip")).section("sblaster").item("oplport").build(metaControls_).init(configurable);
		Label_.on(soundblasterExpGroup).grabHorSpace().build();
		Chain.on(soundblasterExpGroup).lbl(l -> l.key("dialog.template.sbhardwarebase")).cmb(c -> c.tooltip("dialog.template.sbhardwarebase.tooltip").items("profile", "sbhardwarebase_x20240301")).section("sblaster").item("hardwarebase").build(
				metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createGravisUltrasoundPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.audio", "dialog.template.tab.gravisultrasound", parent, 4);
		Chain.on(composite).lbl(l -> l.key("dialog.template.enablegus")).but(b -> b.horSpan(3).tooltip("dialog.template.enablegus.tooltip")).section("gus").item("gus").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.gusrate")).cmb(c -> c.horSpan(3).tooltip("dialog.template.gusrate.tooltip").items("profile", "gusrate")).section("gus").item("rate", "gusrate").build(
			metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.gusaddress")).cmb(c -> c.horSpan(3).tooltip("dialog.template.gusaddress.tooltip").items("profile", configurable.gusbaseSectionItem()).dynItem(GenerationAwareConfigurable::gusbaseSectionItem))
			.section("gus").item("base", "gusbase").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.gusirq1")).cmb(c -> c.tooltip("dialog.template.gusirq1.tooltip").items("profile", configurable.gusirq1SectionItem()).dynItem(GenerationAwareConfigurable::gusirq1SectionItem))
			.section("gus").item("irq1", "gusirq").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.gusirq2")).cmb(c -> c.tooltip("dialog.template.gusirq1.tooltip").items("profile", "irq2")).section("gus").item("irq2").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.gusdma1")).cmb(c -> c.tooltip("dialog.template.gusdma1.tooltip").items("profile", configurable.gusdma1SectionItem()).dynItem(GenerationAwareConfigurable::gusdma1SectionItem))
			.section("gus").item("dma1", "gusdma").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.gusdma2")).cmb(c -> c.tooltip("dialog.template.gusdma1.tooltip").items("profile", "dma2")).section("gus").item("dma2").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.ultradir")).txt(t -> t.horSpan(3).tooltip("dialog.template.ultradir.tooltip")).section("gus").item("ultradir").build(metaControls_).init(configurable).getText();
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 4, 1);
		gd.verticalIndent = 20;
		Group gusExpGroup = Group_.on(composite).layoutData(gd).layout(new GridLayout(2, false)).key(
				"dialog.template.tab.experimentaloptions").build();
		Chain.on(gusExpGroup).lbl(l -> l.key("dialog.template.gustype")).cmb(c -> c.tooltip("dialog.template.gustype.tooltip").items("profile", "gustype_x20240301"))
			.section("gus").item("gustype").build(metaControls_).init(configurable);
		Chain.on(gusExpGroup).lbl(l -> l.key("dialog.template.gusmemsize")).cmb(c -> c.tooltip("dialog.template.gusmemsize.tooltip").editable().items("profile", "gusmemsize_x20240301"))
			.section("gus").item("gusmemsize").build(metaControls_).init(configurable);
		Chain.on(gusExpGroup).lbl(l -> l.key("dialog.template.gusmastervolume")).spn(s -> s.digits(2).tooltip("dialog.template.gusmastervolume.tooltip"))
			.section("gus").item("gus master volume").build(metaControls_).init(configurable);
		Chain.on(gusExpGroup).lbl(l -> l.key("dialog.template.gusglobalregisterreadalias")).cmb(c -> c.tooltip("dialog.template.gusglobalregisterreadalias.tooltip").items("profile", "gusglobalregisterreadalias_x20240301"))
			.section("gus").item("global register read alias").build(metaControls_).init(configurable);
		Chain.on(gusExpGroup).lbl(l -> l.key("dialog.template.gusfilter")).txt(t -> t.tooltip("dialog.template.gusfilter.tooltip")).section("gus").item("gus_filter").build(metaControls_).init(configurable).getText();
	
		return composite;
	}
	
	protected Composite createPCSpeakerPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.audio", "dialog.template.tab.pcspeaker", parent, 2);
		Chain.on(composite).lbl(l -> l.key("dialog.template.enablepcspeaker")).cmb(c -> c.tooltip("dialog.template.enablepcspeaker.tooltip").items("profile", configurable.pcspeakerSectionItem()).dynItem(GenerationAwareConfigurable::pcspeakerSectionItem))
			.section("speaker").item("pcspeaker").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.pcrate")).cmb(c -> c.tooltip("dialog.template.pcrate.tooltip").items("profile", configurable.pcrateSectionItem()).dynItem(GenerationAwareConfigurable::pcrateSectionItem))
			.section("speaker").item("pcrate").build(metaControls_).init(configurable);
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		gd.verticalIndent = 20;
		Group pcspeakerExpGroup = Group_.on(composite).layoutData(gd).layout(new GridLayout(2, false)).key(
				"dialog.template.tab.experimentaloptions").build();
		Chain.on(pcspeakerExpGroup).lbl(l -> l.key("dialog.template.pcspeakerfilter")).cmb(c -> c.tooltip("dialog.template.pcspeakerfilter.tooltip").editable().items("profile", "pcspeakerfilter_staging0810"))
			.section("speaker").item("pcspeaker_filter").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createTandyAudioPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.audio", "dialog.template.tab.tandy", parent, 2);
		Chain.on(composite).lbl(l -> l.key("dialog.template.enabletandy")).cmb(c -> c.tooltip("dialog.template.enabletandy.tooltip").items("profile", configurable.tandySectionItem()).dynItem(GenerationAwareConfigurable::tandySectionItem))
			.section("speaker").item("tandy").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.tandyrate")).cmb(c -> c.tooltip("dialog.template.tandyrate.tooltip").items("profile", "tandyrate")).section("speaker").item(
			"tandyrate").build(metaControls_).init(configurable);
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		gd.verticalIndent = 20;
		Group tandyExpGroup = Group_.on(composite).layoutData(gd).layout(new GridLayout(2, false)).key(
				"dialog.template.tab.experimentaloptions").build();
		Chain.on(tandyExpGroup).lbl(l -> l.key("dialog.template.tandyfilter")).cmb(c -> c.tooltip("dialog.template.tandyfilter.tooltip").editable().items("profile", "tandyfilter_staging0810"))
			.section("speaker").item("tandy_filter").build(metaControls_).init(configurable);
		Chain.on(tandyExpGroup).lbl(l -> l.key("dialog.template.tandydacfilter")).cmb(c -> c.tooltip("dialog.template.tandydacfilter.tooltip").editable().items("profile", "tandydacfilter_staging0810"))
			.section("speaker").item("tandy_dac_filter").build(metaControls_).init(configurable);
		Chain.on(tandyExpGroup).lbl(l -> l.key("dialog.template.tandyfadeout")).cmb(c -> c.tooltip("dialog.template.tandyfadeout.tooltip").editable().items("profile", "tandyfadeout_staging0810"))
			.section("speaker").item("tandy_fadeout").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createPS1AudioPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.audio", "dialog.template.tab.ps1", parent, 2);
		Group ps1ExpGroup = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, false)).layout(new GridLayout(2, false)).key(
				"dialog.template.tab.experimentaloptions").build();
		Chain.on(ps1ExpGroup).lbl(l -> l.key("dialog.template.ps1enable")).cmb(c -> c.tooltip("dialog.template.ps1enable.tooltip").items("profile", configurable.ps1SectionItem()).dynItem(GenerationAwareConfigurable::ps1SectionItem))
			.section("speaker").item("ps1audio").build(metaControls_).init(configurable);
		Chain.on(ps1ExpGroup).lbl(l -> l.key("dialog.template.ps1rate")).cmb(c -> c.tooltip("dialog.template.ps1rate.tooltip").editable().items("profile", "ps1rate_x20240301"))
			.section("speaker").item("ps1audiorate").build(metaControls_).init(configurable);
		Chain.on(ps1ExpGroup).lbl(l -> l.key("dialog.template.ps1filter")).cmb(c -> c.tooltip("dialog.template.ps1filter.tooltip").editable().items("profile", "ps1filter_staging0810"))
			.section("speaker").item("ps1audio_filter").build(metaControls_).init(configurable);
		Chain.on(ps1ExpGroup).lbl(l -> l.key("dialog.template.ps1dacfilter")).cmb(c -> c.tooltip("dialog.template.ps1dacfilter.tooltip").editable().items("profile", "ps1dacfilter_staging0810"))
			.section("speaker").item("ps1audio_dac_filter").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createLptDacPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.audio", "dialog.template.tab.parallelportdac", parent, 2);
		Chain.on(composite).lbl(l -> l.key("dialog.template.enablesoundsource")).but(b -> b.tooltip("dialog.template.enablesoundsource.tooltip")).section("speaker").item("disney").build(
				metaControls_).init(configurable);
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		gd.verticalIndent = 20;
		Group lptDacExpGroup = Group_.on(composite).layoutData(gd).layout(new GridLayout(2, false)).key(
				"dialog.template.tab.experimentaloptions").build();
		Chain.on(lptDacExpGroup).lbl(l -> l.key("dialog.template.lptdac")).cmb(c -> c.tooltip("dialog.template.lptdac.tooltip").items("profile", "lptdac_staging0810"))
			.section("speaker").item("lpt_dac").build(metaControls_).init(configurable);
		Chain.on(lptDacExpGroup).lbl(l -> l.key("dialog.template.lptdacfilter")).cmb(c -> c.tooltip("dialog.template.lptdacfilter.tooltip").editable().items("profile", "lptdacfilter_staging0810"))
			.section("speaker").item("lpt_dac_filter").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createImfcPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.audio", "dialog.template.tab.imfc", parent, 1);
		Group imfcExpGroup = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, false)).layout(new GridLayout(2, false)).key(
			"dialog.template.tab.experimentaloptions").build();
		Chain.on(imfcExpGroup).lbl(l -> l.key("dialog.template.imfcenable")).but(b -> b.tooltip("dialog.template.imfcenable.tooltip")).section("imfc").item("imfc").build(metaControls_).init(configurable);
		Chain.on(imfcExpGroup).lbl(l -> l.key("dialog.template.imfcbase")).cmb(c -> c.tooltip("dialog.template.imfcbase.tooltip").items("profile", "imfcbase_staging0810")).section("imfc").item("imfc_base").build(metaControls_).init(configurable);
		Chain.on(imfcExpGroup).lbl(l -> l.key("dialog.template.imfcirq")).cmb(c -> c.tooltip("dialog.template.imfcirq.tooltip").items("profile", "imfcirq_staging0810")).section("imfc").item("imfc_irq").build(metaControls_).init(configurable);
		Chain.on(imfcExpGroup).lbl(l -> l.key("dialog.template.imfcfilter")).cmb(c -> c.tooltip("dialog.template.imfcfilter.tooltip").editable().items("profile", "imfcfilter_staging0810")).section("imfc").item("imfc_filter").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createInnovaPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.audio", "dialog.template.tab.innova", parent, 1);
		Group innovaExpGroup = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.FILL, true, false)).layout(new GridLayout(2, false)).key(
			"dialog.template.tab.experimentaloptions").build();
		Chain.on(innovaExpGroup).lbl(l -> l.key("dialog.template.innovaenable")).but(b -> b.tooltip("dialog.template.innovaenable.tooltip")).section("innova", "innovation").item("innova", "innovation").build(metaControls_).init(configurable);
		Chain.on(innovaExpGroup).lbl(l -> l.key("dialog.template.innovamodel")).cmb(c -> c.tooltip("dialog.template.innovamodel.tooltip").items("profile", "innovamodel_staging0810")).section("innovation").item("sidmodel").build(metaControls_).init(configurable);
		Chain.on(innovaExpGroup).lbl(l -> l.key("dialog.template.innovarate")).cmb(c -> c.tooltip("dialog.template.innovarate.tooltip").items("profile", "innovarate_x20240301")).section("innova").item("samplerate").build(metaControls_).init(configurable);
		Chain.on(innovaExpGroup).lbl(l -> l.key("dialog.template.innovaport")).cmb(c -> c.tooltip("dialog.template.innovaport.tooltip").items("profile", configurable.innovaportSectionItem()).dynItem(GenerationAwareConfigurable::innovaportSectionItem))
			.section("innovation", "innova").item("sidport", "sidbase").build(metaControls_).init(configurable);
		Chain.on(innovaExpGroup).lbl(l -> l.key("dialog.template.innovaclock")).cmb(c -> c.tooltip("dialog.template.innovaclock.tooltip").items("profile", "innovaclock_staging0810")).section("innovation").item("sidclock").build(metaControls_).init(configurable);
		Chain.on(innovaExpGroup).lbl(l -> l.key("dialog.template.innovaquality")).spn(s -> s.tooltip("dialog.template.innovaquality.tooltip").min(0).max(3)).section("innova").item("quality").build(metaControls_).init(configurable).init(configurable);		
		Chain.on(innovaExpGroup).lbl(l -> l.key("dialog.template.innovafilter")).cmb(c -> c.tooltip("dialog.template.innovafilter.tooltip").editable().items("profile", "innovafilter_staging0810")).section("innovation").item("innovation_filter").build(metaControls_).init(configurable);
		Chain.on(innovaExpGroup).lbl(l -> l.key("dialog.template.innova6581")).spn(s -> s.tooltip("dialog.template.innova6581.tooltip").min(0).max(100)).section("innovation").item("6581filter").build(metaControls_).init(configurable).init(configurable);
		Chain.on(innovaExpGroup).lbl(l -> l.key("dialog.template.innova8580")).spn(s -> s.tooltip("dialog.template.innova8580.tooltip").min(0).max(100)).section("innovation").item("8580filter").build(metaControls_).init(configurable).init(configurable);
		Chain.on(innovaExpGroup).lbl(l -> l.key("dialog.template.innovasidfilter")).spn(s -> s.tooltip("dialog.template.innovasidfilter.tooltip").min(0).max(100)).section("innovation").item("innovation_sid_filter").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createPeripheralsPage(TemplateProfileBase configurable, Composite parent) {
		return createPage("dialog.template.tab.peripherals", parent, 1);
	}
	
	protected Composite createKeyboardPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.peripherals", "dialog.template.tab.keyboard", parent, 3);
		Chain.on(composite).lbl(l -> l.key("dialog.template.usescancodes")).cmb(c-> c.horSpan(2).tooltip("dialog.template.usescancodes.tooltip").items("profile",  configurable.scancodesSectionItem()).dynItem(GenerationAwareConfigurable::scancodesSectionItem))
			.section("sdl").item("usescancodes").build(metaControls_).init(configurable);
		Text mapperfile = Chain.on(composite).lbl(l -> l.key("dialog.template.mapperfile")).txt(t -> t.tooltip("dialog.template.mapperfile.tooltip")).section("sdl").item("mapperfile").build(
			metaControls_).init(configurable).getText();
		Button_.on(composite).star().listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				mapperfile.setText(settings_.getValue("profile", "uniquemapperfile"));
			}
		}).ctrl();
		Chain.on(composite).lbl(l -> l.key("dialog.template.keyboardlayout")).cmb(
			c -> c.horSpan(2).wide().editable().tooltip("dialog.template.keyboardlayout.tooltip").items("profile", "keyboardlayout").visibleItemCount(15)).section("dos").item("keyboardlayout", "keyboard_layout").build(
				metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.keybcommand")).txt(t -> t.tooltip("dialog.template.keybcommand.tooltip").horSpan(2)).autoexec(Autoexec::getKeyb, Autoexec::setKeyb).build(metaControls_).init(configurable);
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		gd.verticalIndent = 20;
		Group keybExpGroup = Group_.on(composite).layoutData(gd).layout(new GridLayout(5, false)).key(
				"dialog.template.tab.experimentaloptions").build();
		Chain.on(keybExpGroup).lbl(l -> l.key("dialog.template.controllertype")).cmb(c -> c.tooltip("dialog.template.controllertype.tooltip").items("profile", "controllertype_x20240301"))
			.section("keyboard").item("controllertype").build(metaControls_).init(configurable);
		Label_.on(keybExpGroup).grabHorSpace().build();
		Chain.on(keybExpGroup).lbl(l -> l.key("dialog.template.mappersendkey")).cmb(c -> c.tooltip("dialog.template.mappersendkey.tooltip").items("profile", "mappersendkey_x20240301"))
			.section("dosbox").item("mapper send key").build(metaControls_).init(configurable);
		Chain.on(keybExpGroup).lbl(l -> l.key("dialog.template.customcodepage")).txt(t -> t.tooltip("dialog.template.customcodepage.tooltip"))
			.section("dos").item("customcodepage").build(metaControls_).init(configurable);
		Label_.on(keybExpGroup).grabHorSpace().build();
		Chain.on(keybExpGroup).lbl(l -> l.key("dialog.template.ime")).cmb(c -> c.tooltip("dialog.template.ime.tooltip").items("profile", "ime_x20240301"))
			.section("dosbox").item("ime").build(metaControls_).init(configurable);
		Chain.on(keybExpGroup).lbl(l -> l.key("dialog.template.keyboardcapture")).but(b -> b.tooltip("dialog.template.keyboardcapture.tooltip"))
			.section("sdl").item("keyboard_capture").build(metaControls_).init(configurable);
	
		return composite;
	}
	
	protected Composite createMousePage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.peripherals", "dialog.template.tab.mouse", parent, 2);
		Chain.on(composite).lbl(l -> l.key("dialog.template.autolock")).but(b -> b.tooltip("dialog.template.autolock.tooltip")).section("sdl").item("autolock").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.sensitivity")).cmb(
			c -> c.editable().items("profile", "sensitivity").visibleItemCount(20).tooltip("dialog.template.sensitivity.tooltip")).section("sdl", "mouse").item("sensitivity", "mouse_sensitivity").convert(new BaseDaControlConvertor() {
				@Override
				public String toConfValue(String existingValue, String[] values) {
					return String.join(",", values);
				}

				@Override
				public String toConfValueForDisplay(String[] values) {
					return toConfValue(null, values);
				}

				@Override
				public String[] toControlValues(String value) {
					return value == null ? new String[0]: new String[] {value};
				}
			}).build(metaControls_).init(configurable);
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		gd.verticalIndent = 20;
		Group mouseExpGroup = Group_.on(composite).layoutData(gd).layout(new GridLayout(5, false)).key(
				"dialog.template.tab.experimentaloptions").build();
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mouseemu")).cmb(c -> c.tooltip("dialog.template.mouseemu.tooltip").items("profile", "mouseemu_x20240301"))
			.section("sdl").item("mouse_emulation").build(metaControls_).init(configurable);
		Label_.on(mouseExpGroup).grabHorSpace().build();
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mousecapture")).cmb(c -> c.tooltip("dialog.template.mousecapture.tooltip").items("profile", "mousecapture_staging0810"))
			.section("mouse").item("mouse_capture").build(metaControls_).init(configurable);
		
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.middleunlock")).cmb(c -> c.tooltip("dialog.template.middleunlock.tooltip").items("profile", "middleunlock_x20240301"))
			.section("sdl").item("middle_unlock").build(metaControls_).init(configurable);
		Label_.on(mouseExpGroup).grabHorSpace().build();
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mousemiddlerelease")).but(b -> b.tooltip("dialog.template.mousemiddlerelease.tooltip"))
			.section("mouse").item("mouse_middle_release").build(metaControls_).init(configurable);

		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.autolockfeedback")).cmb(c -> c.tooltip("dialog.template.autolockfeedback.tooltip").items("profile", "autolockfeedback_x20240301"))
			.section("sdl").item("autolock_feedback").build(metaControls_).init(configurable);
		Label_.on(mouseExpGroup).grabHorSpace().build();
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.usesystemcursor")).but(b -> b.tooltip("dialog.template.usesystemcursor.tooltip"))
			.section("sdl").item("usesystemcursor").build(metaControls_).init(configurable);

		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.int33")).but(b -> b.tooltip("dialog.template.int33.tooltip"))
			.section("dos").item("int33").build(metaControls_).init(configurable);
		Label_.on(mouseExpGroup).grabHorSpace().build();
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.aux8042")).but(b -> b.tooltip("dialog.template.aux8042.tooltip"))
			.section("keyboard").item("aux").build(metaControls_).init(configurable);

		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mousevmware")).but(b -> b.tooltip("dialog.template.mousevmware.tooltip"))
			.section("mouse").item("vmware_mouse").build(metaControls_).init(configurable);
		Label_.on(mouseExpGroup).grabHorSpace().build();
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mousevirtualbox")).but(b -> b.tooltip("dialog.template.mousevirtualbox.tooltip"))
			.section("mouse").item("virtualbox_mouse").build(metaControls_).init(configurable);

		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mousecommodel")).cmb(c -> c.tooltip("dialog.template.mousecommodel.tooltip").items("profile", "mousecommodel_staging0810"))
			.section("mouse").item("com_mouse_model").build(metaControls_).init(configurable);
		Label_.on(mouseExpGroup).grabHorSpace().build();
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mouseps2model")).cmb(c -> c.tooltip("dialog.template.mouseps2model.tooltip").items("profile", "mouseps2model_staging0810"))
			.section("mouse").item("ps2_mouse_model").build(metaControls_).init(configurable);
		
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mousedosdriver")).but(b -> b.tooltip("dialog.template.mousedosdriver.tooltip"))
			.section("mouse").item("dos_mouse_driver").build(metaControls_).init(configurable);
		Label_.on(mouseExpGroup).grabHorSpace().build();
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.auxdevice")).cmb(c -> c.tooltip("dialog.template.auxdevice.tooltip").items("profile", "auxdevice_x20240301"))
			.section("keyboard").item("auxdevice").build(metaControls_).init(configurable);
	
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mouserawinput")).but(b -> b.tooltip("dialog.template.mouserawinput.tooltip"))
			.section("mouse").item("mouse_raw_input").build(metaControls_).init(configurable);
		Label_.on(mouseExpGroup).grabHorSpace().build();
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mouseimmediate")).but(b -> b.tooltip("dialog.template.mouseimmediate.tooltip"))
			.section("mouse").item("dos_mouse_immediate").build(metaControls_).init(configurable);
		
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mousemultidisplayaware")).but(b -> b.tooltip("dialog.template.mousemultidisplayaware.tooltip"))
			.section("mouse").item("mouse_multi_display_aware").build(metaControls_).init(configurable);
		Label_.on(mouseExpGroup).grabHorSpace().build();
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mousereportrate")).spn(s -> s.min(0).tooltip("dialog.template.mousereportrate.tooltip"))
			.section("dos").item("mouse report rate").build(metaControls_).init(configurable);

		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.mousewheelkey")).spn(s -> s.min(-7).max(7).tooltip("dialog.template.mousewheelkey.tooltip"))
			.section("sdl").item("mouse_wheel_key").build(metaControls_).init(configurable);
		Label_.on(mouseExpGroup).grabHorSpace().build();
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.builtindosmousedriver")).cmb(c -> c.tooltip("dialog.template.builtindosmousedriver.tooltip").items("profile", "builtindosmousedriver_staging0830"))
			.section("mouse").item("builtin_dos_mouse_driver").build(metaControls_).init(configurable);

		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.builtindosmousedrivermodel")).cmb(c -> c.tooltip("dialog.template.builtindosmousedrivermodel.tooltip").items("profile", "builtindosmousedrivermodel_staging0830"))
			.section("mouse").item("builtin_dos_mouse_driver_model").build(metaControls_).init(configurable);
		Label_.on(mouseExpGroup).grabHorSpace().build();
		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.builtindosmousedrivermovethreshold")).cmb(c -> c.editable().tooltip("dialog.template.builtindosmousedrivermovethreshold.tooltip").items("profile", "builtindosmousedrivermovethreshold_staging0830"))
			.section("mouse").item("builtin_dos_mouse_driver_move_threshold").build(metaControls_).init(configurable);

		Chain.on(mouseExpGroup).lbl(l -> l.key("dialog.template.builtindosmousedriveroptions")).cmb(c -> c.editable().tooltip("dialog.template.builtindosmousedriveroptions.tooltip").items("profile", "builtindosmousedriveroptions_staging0830"))
			.section("mouse").item("builtin_dos_mouse_driver_options").build(metaControls_).init(configurable);

		return composite;
	}
	
	protected Composite createJoystickPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.peripherals", "dialog.template.tab.joystick", parent, 2);
		Chain.on(composite).lbl(l -> l.key("dialog.template.joysticktype")).cmb(c -> c.tooltip("dialog.template.joysticktype.tooltip").items("profile", "joysticktype").dynItem(GenerationAwareConfigurable::joysticktypeSectionItem))
			.section("bios", "joystick").item("joysticktype", "joysticktype").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.timedemulation")).but(b -> b.tooltip("dialog.template.timedemulation.tooltip")).section("joystick").item("timed").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.autofire")).but(b -> b.tooltip("dialog.template.autofire.tooltip")).section("joystick").item("autofire").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.swap34")).but(b -> b.tooltip("dialog.template.swap34.tooltip")).section("joystick").item("swap34").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.buttonwrapping")).but(b -> b.tooltip("dialog.template.buttonwrapping.tooltip")).section("joystick").item("buttonwrap").build(
			metaControls_).init(configurable);
		
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		gd.verticalIndent = 20;
		Group joystickExpGroup = Group_.on(composite).layoutData(gd).layout(new GridLayout(5, false)).key(
				"dialog.template.tab.experimentaloptions").build();
		Chain.on(joystickExpGroup).lbl(l -> l.key("dialog.template.calibratex")).txt(t -> t.tooltip("dialog.template.calibratex.tooltip")).section("joystick").item("joy_x_calibration").build(metaControls_).init(configurable);
		Label_.on(joystickExpGroup).grabHorSpace().build();
		Chain.on(joystickExpGroup).lbl(l -> l.key("dialog.template.calibratey")).txt(t -> t.tooltip("dialog.template.calibratey.tooltip")).section("joystick").item("joy_y_calibration").build(metaControls_).init(configurable);

		Chain.on(joystickExpGroup).lbl(l -> l.key("dialog.template.calibratehotkeys")).but(b -> b.tooltip("dialog.template.calibratehotkeys.tooltip")).section("joystick").item("use_joy_calibration_hotkeys").build(metaControls_).init(configurable);
		Label_.on(joystickExpGroup).horSpan(3).grabHorSpace().build();

		Chain.on(joystickExpGroup).lbl(l -> l.key("dialog.template.deadzone")).spn(s -> s.tooltip("dialog.template.deadzone.tooltip").min(0).max(100)).section("joystick").item("deadzone").build(
				metaControls_).init(configurable);
		Label_.on(joystickExpGroup).grabHorSpace().build();
		Chain.on(joystickExpGroup).lbl(l -> l.key("dialog.template.circularinput")).but(b -> b.tooltip("dialog.template.circularinput.tooltip")).section("joystick").item("circularinput").build(
			metaControls_).init(configurable);
		
		for (int j = 1; j <= 2; j++) {
			for (int a = 0; a < 8; a++) {
				for (int s = 0; s < 2; s++) {
					Integer joy = j;
					Integer axis = a;
					char sign = s == 0 ? '-': '+';
					Chain.on(joystickExpGroup).lbl(l -> l.key("dialog.template.joydeadzone", new Object[]{joy, axis, sign}))
						.spn(sp -> sp.tooltip("dialog.template.deadzone.tooltip").min(0).max(10000).digits(2))
						.section("mapper").item("joy" + joy + "deadzone" + axis + sign)
						.build(metaControls_).init(configurable);
					if (s == 0) {
						Label_.on(joystickExpGroup).grabHorSpace().build();
					}
				}
			}
		}
		
		return composite;
	}
	
	protected Composite createPrinterPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.peripherals", "dialog.template.tab.printer", parent, 1);
		Group expPrinter = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(new GridLayout(5, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.printerenable")).but(b -> b.tooltip("dialog.template.printerenable.tooltip")).section("printer").item("printer").build(metaControls_).init(configurable);
		Label_.on(expPrinter).grabHorSpace().build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.dongle")).but(b -> b.tooltip("dialog.template.dongle.tooltip")).section("parallel").item("dongle").build(metaControls_).init(configurable);
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.printoutput")).cmb(c -> c.tooltip("dialog.template.printoutput.tooltip").items("profile", "printoutput_x20240301"))
			.section("printer").item("printoutput").build(metaControls_).init(configurable);
		Label_.on(expPrinter).grabHorSpace().build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.printerdevice")).txt(t -> t.tooltip("dialog.template.printerdevice.tooltip")).section("printer").item("device").build(metaControls_).init(configurable);
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.printerwidth")).spn(s -> s.tooltip("dialog.template.printerwidth.tooltip").min(1)).section("printer").item("width").build(
				metaControls_).init(configurable);
		Label_.on(expPrinter).grabHorSpace().build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.printerdpi")).spn(s -> s.tooltip("dialog.template.printerdpi.tooltip").min(50)).section("printer").item("dpi").build(
				metaControls_).init(configurable);
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.printerheight")).spn(s -> s.tooltip("dialog.template.printerheight.tooltip").min(1)).section("printer").item("height").build(
				metaControls_).init(configurable);
		Label_.on(expPrinter).grabHorSpace().build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.printertimeout")).spn(s -> s.tooltip("dialog.template.printertimeout.tooltip").min(0)).section("printer").item("timeout").build(
				metaControls_).init(configurable);
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.printerdocpath")).txt(t -> t.tooltip("dialog.template.printerdocpath.tooltip")).section("printer").item("docpath").build(metaControls_).init(configurable);
		Label_.on(expPrinter).grabHorSpace().build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.printeropenwith")).txt(t -> t.tooltip("dialog.template.printeropenwith.tooltip")).section("printer").item("openwith").build(metaControls_).init(configurable);
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.printerfontpath")).txt(t -> t.tooltip("dialog.template.printerfontpath.tooltip")).section("printer").item("fontpath").build(metaControls_).init(configurable);
		Label_.on(expPrinter).grabHorSpace().build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.printeropenerror")).txt(t -> t.tooltip("dialog.template.printeropenerror.tooltip")).section("printer").item("openerror").build(metaControls_).init(configurable);
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.multipage")).but(b -> b.tooltip("dialog.template.multipage.tooltip")).section("printer").item("multipage").build(metaControls_).init(configurable);
		Label_.on(expPrinter).grabHorSpace().build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.shellhide")).but(b -> b.tooltip("dialog.template.shellhide.tooltip")).section("printer").item("shellhide").build(metaControls_).init(configurable);
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.parallel1")).txt(t -> t.tooltip("dialog.template.parallel1.tooltip")).section("parallel").item("parallel1").build(metaControls_).init(configurable);
		Label_.on(expPrinter).grabHorSpace().build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.parallel6")).txt(t -> t.tooltip("dialog.template.parallel1.tooltip")).section("parallel").item("parallel6").build(metaControls_).init(configurable);
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.parallel2")).txt(t -> t.tooltip("dialog.template.parallel1.tooltip")).section("parallel").item("parallel2").build(metaControls_).init(configurable);
		Label_.on(expPrinter).grabHorSpace().build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.parallel7")).txt(t -> t.tooltip("dialog.template.parallel1.tooltip")).section("parallel").item("parallel7").build(metaControls_).init(configurable);
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.parallel3")).txt(t -> t.tooltip("dialog.template.parallel1.tooltip")).section("parallel").item("parallel3").build(metaControls_).init(configurable);
		Label_.on(expPrinter).grabHorSpace().build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.parallel8")).txt(t -> t.tooltip("dialog.template.parallel1.tooltip")).section("parallel").item("parallel8").build(metaControls_).init(configurable);
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.parallel4")).txt(t -> t.tooltip("dialog.template.parallel1.tooltip")).section("parallel").item("parallel4").build(metaControls_).init(configurable);
		Label_.on(expPrinter).grabHorSpace().build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.parallel9")).txt(t -> t.tooltip("dialog.template.parallel1.tooltip")).section("parallel").item("parallel9").build(metaControls_).init(configurable);
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.parallel5")).txt(t -> t.tooltip("dialog.template.parallel1.tooltip")).section("parallel").item("parallel5").build(metaControls_).init(configurable);
		Label_.on(expPrinter).grabHorSpace().build();
		Chain.on(expPrinter).lbl(l -> l.key("dialog.template.printdbcs")).cmb(c -> c.tooltip("dialog.template.printdbcs.tooltip").items("profile", "printdbcs_x20240301"))
			.section("printer").item("printdbcs").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createNetworkPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.network", parent, 1);
		Group expNetwork = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(new GridLayout(2, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expNetwork).lbl(l -> l.key("dialog.template.networkredirector")).but(b -> b.tooltip("dialog.template.networkredirector.tooltip"))
			.section("dos").item("network redirector").build(metaControls_).init(configurable);
		Chain.on(expNetwork).lbl(l -> l.key("dialog.template.pcaprealnic")).txt(t -> t.tooltip("dialog.template.pcaprealnic.tooltip")).section("ethernet, pcap").item("realnic").build(metaControls_).init(configurable);
		Chain.on(expNetwork).lbl(l -> l.key("dialog.template.pcaptimeout")).txt(t -> t.tooltip("dialog.template.pcaptimeout.tooltip")).section("ethernet, pcap").item("timeout").build(metaControls_).init(configurable);
		Chain.on(expNetwork).lbl(l -> l.key("dialog.template.slirpnetwork")).txt(t -> t.tooltip("dialog.template.slirpnetwork.tooltip")).section("ethernet, slirp").item("ipv4_network").build(metaControls_).init(configurable);
		Chain.on(expNetwork).lbl(l -> l.key("dialog.template.slirpnetmask")).txt(t -> t.tooltip("dialog.template.slirpnetmask.tooltip")).section("ethernet, slirp").item("ipv4_netmask").build(metaControls_).init(configurable);
		Chain.on(expNetwork).lbl(l -> l.key("dialog.template.slirphost")).txt(t -> t.tooltip("dialog.template.slirphost.tooltip")).section("ethernet, slirp").item("ipv4_host").build(metaControls_).init(configurable);
		Chain.on(expNetwork).lbl(l -> l.key("dialog.template.slirpnameserver")).txt(t -> t.tooltip("dialog.template.slirpnameserver.tooltip")).section("ethernet, slirp").item("ipv4_nameserver").build(metaControls_).init(configurable);
		Chain.on(expNetwork).lbl(l -> l.key("dialog.template.slirpdhcpstart")).txt(t -> t.tooltip("dialog.template.slirpdhcpstart.tooltip")).section("ethernet, slirp").item("ipv4_dhcp_start").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createIpxPage(TemplateProfileBase configurable, Composite parent) {
		Composite networkGroup = createPage("dialog.template.tab.network", "dialog.template.tab.ipx", parent, 2);
		ipx_ = Chain.on(networkGroup).lbl(l -> l.key("dialog.template.enableipx")).but(b -> b.tooltip("dialog.template.enableipx.tooltip")).section("ipx").item("ipx").build(metaControls_).init(configurable).getButton();
		ipxNet_ = Chain.on(networkGroup).lbl(l -> l.key("dialog.template.ipxnetcommand")).txt(t -> t).autoexec(Autoexec::getIpxnet, Autoexec::setIpxnet).build(metaControls_).init(configurable).getText();
		ipxNet_.setEnabled(ipx_.getSelection());
		ipx_.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				ipxNet_.setEnabled(ipx_.getSelection());
			}
		});
		return networkGroup;
	}
	
	protected Composite createModemPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.network", "dialog.template.tab.modem", parent, 2);
		Chain.on(composite).lbl(l -> l.key("dialog.template.serial1")).txt(t -> t.tooltip("dialog.template.serial.tooltip")).section("serial").item("serial1").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.serial2")).txt(t -> t.tooltip("dialog.template.serial.tooltip")).section("serial").item("serial2").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.serial3")).txt(t -> t.tooltip("dialog.template.serial.tooltip")).section("serial").item("serial3").build(metaControls_).init(configurable);
		Chain.on(composite).lbl(l -> l.key("dialog.template.serial4")).txt(t -> t.tooltip("dialog.template.serial.tooltip")).section("serial").item("serial4").build(metaControls_).init(configurable);
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		gd.verticalIndent = 20;
		Group expSerial = Group_.on(composite).layoutData(gd).layout(new GridLayout(2, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expSerial).lbl(l -> l.key("dialog.template.serial5")).txt(t -> t.tooltip("dialog.template.serial.tooltip")).section("serial").item("serial5").build(metaControls_).init(configurable);
		Chain.on(expSerial).lbl(l -> l.key("dialog.template.serial6")).txt(t -> t.tooltip("dialog.template.serial.tooltip")).section("serial").item("serial6").build(metaControls_).init(configurable);
		Chain.on(expSerial).lbl(l -> l.key("dialog.template.serial7")).txt(t -> t.tooltip("dialog.template.serial.tooltip")).section("serial").item("serial7").build(metaControls_).init(configurable);
		Chain.on(expSerial).lbl(l -> l.key("dialog.template.serial8")).txt(t -> t.tooltip("dialog.template.serial.tooltip")).section("serial").item("serial8").build(metaControls_).init(configurable);
		Chain.on(expSerial).lbl(l -> l.key("dialog.template.serial9")).txt(t -> t.tooltip("dialog.template.serial.tooltip")).section("serial").item("serial9").build(metaControls_).init(configurable);
		Chain.on(expSerial).lbl(l -> l.key("dialog.template.serialphonebook")).txt(t -> t.tooltip("dialog.template.serialphonebook.tooltip")).section("serial").item("phonebookfile").build(metaControls_).init(configurable);
		return composite;
	}
	
	protected Composite createNe2000Page(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.network", "dialog.template.tab.ne2000", parent, 1);
		Group expNe2000 = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false)).layout(new GridLayout(2, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expNe2000).lbl(l -> l.key("dialog.template.ne2000enable")).but(b -> b.tooltip("dialog.template.ne2000enable.tooltip")).section("ethernet", "ne2000").item("ne2000", "ne2000").build(metaControls_).init(configurable);
		Chain.on(expNe2000).lbl(l -> l.key("dialog.template.ne2000backend")).cmb(c -> c.tooltip("dialog.template.ne2000backend.tooltip").items("profile", "ne2000backend_x20240301")).section("ne2000").item("backend").build(metaControls_).init(configurable);
		Chain.on(expNe2000).lbl(l -> l.key("dialog.template.ne2000base")).cmb(c -> c.tooltip("dialog.template.ne2000base.tooltip").items("profile", "ne2000base_staging0810")).section("ethernet", "ne2000").item("nicbase", "nicbase").build(metaControls_).init(configurable);
		Chain.on(expNe2000).lbl(l -> l.key("dialog.template.ne2000irq")).cmb(c -> c.tooltip("dialog.template.ne2000irq.tooltip").items("profile", "ne2000irq_staging0810")).section("ethernet", "ne2000").item("nicirq", "nicirq").build(metaControls_).init(configurable);
		Chain.on(expNe2000).lbl(l -> l.key("dialog.template.ne2000macaddress")).txt(t -> t.tooltip("dialog.template.ne2000macaddress.tooltip")).section("ethernet", "ne2000").item("macaddr", "macaddr").build(metaControls_).init(configurable);
		Chain.on(expNe2000).lbl(l -> l.key("dialog.template.ne2000udpforward")).txt(t -> t.tooltip("dialog.template.ne2000udpforward.tooltip")).section("ethernet").item("udp_port_forwards").build(metaControls_).init(configurable);
		Chain.on(expNe2000).lbl(l -> l.key("dialog.template.ne2000tcpforward")).txt(t -> t.tooltip("dialog.template.ne2000tcpforward.tooltip")).section("ethernet").item("tcp_port_forwards").build(metaControls_).init(configurable);
		return composite;
	}

	protected Composite createDosboxAutoexecPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.dosbox", "dialog.template.tab.dosboxautoexec", parent, 2);
		for (int i = 0; i < Autoexec.SECTIONS; i++) {
			int j = i + 1;
			Chain.on(composite).lbl(l -> l.key("dialog.template.customcommand" + j)).txt(Text_.Builder::multi).autoexec(i, Autoexec::getCustomSection, Autoexec::setCustomSection).build(
				metaControls_).init(configurable);
		}
		return composite;
	}
	
	protected Composite createDosboxStartupPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.dosbox", "dialog.template.tab.startup", parent, 2);
		nativeCommandControl_ = Chain.on(composite).lbl(l -> l.key("dialog.template.nativecommands")).lbl(l -> l).lst(l -> l).nativeCommands(TemplateProfileBase::getNativeCommands,
				TemplateProfileBase::setNativeCommandsWithObject).build().init(configurable);
		nativeCommandsList_ = nativeCommandControl_.getList();
		nativeCommandsList_.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent event) {
				if (nativeCommandsList_.getSelectionIndex() == -1) {
					doAddNativeCommand();
				} else {
					doEditNativeCommand();
				}
			}
		});
		Composite nativeButComp = Composite_.on(composite).layoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false)).innerLayout(1).vertSpacing().build();
		Chain.on(nativeButComp).but(b -> b.text().key("dialog.template.mount.add").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doAddNativeCommand();
			}
		})).but(b -> b.text().key("dialog.template.mount.edit").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doEditNativeCommand();
			}
		})).but(b -> b.text().key("dialog.template.mount.remove").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doRemoveNativeCommand();
			}
		})).but(b -> b.arrow(true).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				int sel = nativeCommandsList_.getSelectionIndex();
				if (sel > 0) {
					List<NativeCommand> cmds = getNativeCommands();
					Collections.swap(cmds, sel, sel - 1);
					nativeCommandControl_.setControlByNativeCommands(cmds);
					nativeCommandsList_.select(sel - 1);

				}
			}
		})).but(b -> b.arrow(false).listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				int sel = nativeCommandsList_.getSelectionIndex();
				if (sel >= 0 && sel < nativeCommandsList_.getItemCount() - 1) {
					List<NativeCommand> cmds = getNativeCommands();
					Collections.swap(cmds, sel, sel + 1);
					nativeCommandControl_.setControlByNativeCommands(cmds);
					nativeCommandsList_.select(sel + 1);
				}
			}
		})).build();
		return composite;
	}

	private List<NativeCommand> getNativeCommands() {
		return new ArrayList<>(nativeCommandControl_.getCurrentNativeCommands());
	}

	protected void doAddNativeCommand() {
		EditNativeCommandDialog cmdDialog = new EditNativeCommandDialog(shell_, null);
		NativeCommand cmd = cmdDialog.open();
		if (cmd != null) {
			int nr = nativeCommandsList_.getSelectionIndex() + 1;
			List<NativeCommand> cmds = getNativeCommands();
			cmds.add(nr, cmd);
			nativeCommandControl_.setControlByNativeCommands(cmds);
		}
	}

	protected void doEditNativeCommand() {
		int sel = nativeCommandsList_.getSelectionIndex();
		if (sel != -1) {
			List<NativeCommand> cmds = getNativeCommands();
			NativeCommand cmd = cmds.get(sel);
			if (!cmd.isDosboxCommand()) {
				EditNativeCommandDialog cmdDialog = new EditNativeCommandDialog(shell_, cmd);
				cmd = cmdDialog.open();
				if (cmd != null) {
					cmds.set(sel, cmd);
					nativeCommandControl_.setControlByNativeCommands(cmds);
				}
			}
		}
	}

	protected void doRemoveNativeCommand() {
		int sel = nativeCommandsList_.getSelectionIndex();
		if (sel != -1) {
			List<NativeCommand> cmds = getNativeCommands();
			NativeCommand cmd = cmds.get(sel);
			if (!cmd.isDosboxCommand()) {
				cmds.remove(sel);
				nativeCommandControl_.setControlByNativeCommands(cmds);
				nativeCommandsList_.select(Math.min(sel, nativeCommandsList_.getItemCount() - 1));
			}
		}
	}

	protected Composite createMountingPage(TemplateProfileBase configurable, Composite parent, boolean multiEdit) {
		Composite composite = createPage("dialog.template.tab.mounting", parent, 2);

		mountingpointsList_ = List_.on(composite).build();
		if (!Arrays.equals(mountingpointsList_.getItems(), configurable.getMountStringsForUI()))
			mountingpointsList_.setItems(configurable.getMountStringsForUI());
		mountingpointsList_.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent event) {
				if (mountingpointsList_.getSelectionIndex() == -1) {
					doAddMount(shell_, booterRadio_.getSelection(), mountingpointsList_, configurable);
				} else {
					doEditMount(shell_, mountingpointsList_, configurable);
				}
			}
		});
		
		Composite mntButComp = Composite_.on(composite).innerLayout(1).vertSpacing().build();
		Chain.on(mntButComp).but(b -> b.text().key("dialog.template.mount.add").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doAddMount(shell_, booterRadio_.getSelection(), mountingpointsList_, configurable);
			}
		})).but(b -> b.text().key("dialog.template.mount.edit").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doEditMount(shell_, mountingpointsList_, configurable);
			}
		})).but(b -> b.text().key("dialog.template.mount.remove").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				doRemoveMount(configurable);
			}
		})).build();
		
		if (multiEdit) {
			mountingpointsList_.setEnabled(false);
			Stream.of(mntButComp.getChildren()).forEach(x -> x.setEnabled(false));
		}
		
		Group executeGroup = Group_.on(composite).layoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1)).layout(new GridLayout(2, false)).key("dialog.template.execute").build();
		dosRadio_ = Chain.on(executeGroup).but(b -> b.radio().key("dialog.template.dos").select(configurable.getCombinedConfiguration().getAutoexec().isDos())).button();
		booterRadio_ = Chain.on(executeGroup).but(b -> b.radio().key("dialog.template.booter").select(configurable.getCombinedConfiguration().getAutoexec().isBooter())).button();
		return composite;
	}
	
	protected void createMountingPageAdditions(TemplateProfileBase configurable, Composite composite) {
		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		Group expMounting = Group_.on(composite).layoutData(gd).layout(new GridLayout(8, false)).key("dialog.template.tab.experimentaloptions").build();
		Chain.on(expMounting).lbl(l -> l.key("dialog.template.automount")).but(b -> b.tooltip("dialog.template.automount.tooltip"))
			.section("dos", "dosbox").item("automount", "automount").build(metaControls_).init(configurable);
		Label_.on(expMounting).grabHorSpace().build();
		Chain.on(expMounting).lbl(l -> l.key("dialog.template.automountall")).cmb(c -> c.tooltip("dialog.template.automountall.tooltip").items("profile", "automountall_x20240301"))
			.section("dos").item("automountall").build(metaControls_).init(configurable);
		Label_.on(expMounting).grabHorSpace().build();
		Chain.on(expMounting).lbl(l -> l.key("dialog.template.convertdrivefat")).but(b -> b.tooltip("dialog.template.convertdrivefat.tooltip"))
			.section("dosbox").item("convertdrivefat").build(metaControls_).init(configurable);
		
		Chain.on(expMounting).lbl(l -> l.key("dialog.template.automountwarning")).but(b -> b.tooltip("dialog.template.automountwarning.tooltip"))
			.section("dos").item("mountwarning").build(metaControls_).init(configurable);
		Label_.on(expMounting).grabHorSpace().build();
		Chain.on(expMounting).lbl(l -> l.key("dialog.template.freesizecap")).cmb(c -> c.tooltip("dialog.template.freesizecap.tooltip").items("profile", "freesizecap_x20240301"))
			.section("dosbox").item("freesizecap").build(metaControls_).init(configurable);
		Label_.on(expMounting).grabHorSpace().build();
		Chain.on(expMounting).lbl(l -> l.key("dialog.template.nocachedir")).but(b -> b.tooltip("dialog.template.nocachedir.tooltip"))
			.section("dosbox").item("nocachedir").build(metaControls_).init(configurable);
	}

	public static void doAddMount(Shell shell, boolean floppy, org.eclipse.swt.widgets.List mountingpoints, TemplateProfileBase configurable) {
		char drive;
		try {
			drive = DriveLetterHelper.getFirstAvailable(floppy, configurable.getNettoMountedDrives());
		} catch (DrivelettersExhaustedException e) {
			// nothing we can do, just take a sensible default
			drive = 'C';
		}
		EditMountDialog addMountDialog = new EditMountDialog(shell, null, drive);
		String mount = addMountDialog.open();
		if (mount != null) {
			configurable.addMount(mount);
			mountingpoints.setItems(configurable.getMountStringsForUI());
			mountingpoints.select(mountingpoints.getItemCount() - 1);
		}
	}

	public static void doEditMount(Shell shell, org.eclipse.swt.widgets.List mountingpoints, TemplateProfileBase configurable) {
		int mounts = mountingpoints.getItemCount();
		int sel = mountingpoints.getSelectionIndex();
		if (sel == -1 && mounts == 1) {
			sel = 0;
			mountingpoints.select(sel);
		}
		if ((sel != -1) && (!configurable.getMountingPointsForUI().get(sel).isUnmounted())) {
			EditMountDialog editMountDialog = new EditMountDialog(shell, mountingpoints.getItem(sel), 'C');
			String mount = editMountDialog.open();
			if (mount != null) {
				configurable.editMountBasedOnIndexUI(sel, mount);
				mountingpoints.setItems(configurable.getMountStringsForUI());
				if (mountingpoints.getItemCount() == mounts) {
					mountingpoints.select(sel);
				} else {
					mountingpoints.select(mountingpoints.getItemCount() - 1);
				}
			}
		}
	}

	protected void doRemoveMount(TemplateProfileBase configurable) {
		doRemoveMount(mountingpointsList_, configurable);
	}

	public static void doRemoveMount(org.eclipse.swt.widgets.List mountingpoints, TemplateProfileBase configurable) {
		int mounts = mountingpoints.getItemCount();
		int sel = mountingpoints.getSelectionIndex();
		if (sel == -1 && mounts == 1) {
			sel = 0;
			mountingpoints.select(sel);
		}
		if (sel != -1) {
			configurable.removeMountBasedOnIndexUI(sel);
			mountingpoints.setItems(configurable.getMountStringsForUI());
			if (mountingpoints.getItemCount() == mounts) {
				mountingpoints.select(sel);
			} else {
				if (mountingpoints.getItemCount() > 0) {
					mountingpoints.select(mountingpoints.getItemCount() - 1);
				}
			}
		}
	}

	protected void validateMounts(TemplateProfileBase configurable, Mess_.Builder mess) {
		if (mess.noErrors()) {
			String msg = null;
			switch (configurable.checkMounts()) {
				case INVALID_OVERLAY_NO_BASEDRIVE:
					msg = text_.get("dialog.template.notice.invalidmounts.overlaynobasedrive");
					break;
				case INVALID_OVERLAY_MIXED_ABSREL_PATHS:
					msg = text_.get("dialog.template.notice.invalidmounts.overlaymixedpaths");
					break;
				case INVALID_OVERLAY_PATHS_EQUAL:
					msg = text_.get("dialog.template.notice.invalidmounts.overlaypathsequal");
					break;
				default:
			}
			if ((msg != null) && !Mess_.on(shell_).txt(msg + StringUtils.LF + text_.get("dialog.template.notice.invalidmounts.ignore")).confirm()) {
				mess.txt(msg).bind(mountingpointsList_, getTreeItemByControl(mountingpointsList_));
			}
		}
	}
}
