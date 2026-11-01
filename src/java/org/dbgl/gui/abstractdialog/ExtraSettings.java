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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.controls.Chain;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.conf.ConfValueOptions;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.entity.TemplateProfileBase;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;


/**
 * The settings of a DOSBox version that have no field of their own in the profile/template dialog. They are shown on the
 * page that fits their topic (see {@link SettingPlacement}), in one collapsible group per section at the end of that page.
 * The fields are only created when their page is shown.
 *
 * When several profiles are edited at once, the dialog shows their common values; a setting whose value differs between the
 * profiles starts empty and is only changed in the profiles if a value is entered (or the reset button is clicked).
 */
public final class ExtraSettings {

	/** settings managed by DBGL itself (the captures folder of each profile) */
	static final Set<String> DBGL_MANAGED_SETTINGS = Set.of("dosbox/captures", "capture/capture_dir");

	private static final String ONLY_CHANGED = "dbgl.extras.onlychanged";
	private static final String HEADER = "dbgl.extras.header";
	private static final String GROUP_KEY = "dbgl.extras.groupkey";
	private static final String GROUP_TITLE = "dbgl.extras.grouptitle";
	private static final String BOLD = "dbgl.extras.bold";
	private static final String ONLY_EXTRAS_PAGE = "dbgl.extras.onlyextraspage";

	/** one setting with its field; initialValue is what the profile/template had when the field was last filled */
	record Setting(String section, String item, Control control, Label label, String initialValue, String defaultValue) {
		String key() {
			return section.toLowerCase() + '/' + item.toLowerCase();
		}

		String value() {
			return control instanceof Combo c ? c.getText().strip(): ((Text)control).getText().strip();
		}

		/** set by the profile/template, and not just the DOSBox default */
		boolean isChanged() {
			String v = value();
			return !v.isEmpty() && !v.equalsIgnoreCase(StringUtils.defaultString(defaultValue).strip());
		}
	}

	/** a change to apply to every profile when several are edited at once; an empty value means the DOSBox default */
	public record Change(String section, String item, String oldValue, boolean conflicting, String newValue) {
	}

	private final EditConfigurableDialog<?> dialog_;

	/** per page: the composite at its end that takes the settings */
	private final Map<String, Composite> areas_ = new HashMap<>();
	/** per page: the DOSBox version its settings were created for */
	private final Map<String, DosboxVersion> builtFor_ = new HashMap<>();
	/** per page: the settings as {section, item}, for one DOSBox version */
	private Map<String, List<String[]>> byPage_ = new HashMap<>();
	private DosboxVersion computedFor_;
	private final List<Setting> settings_ = new ArrayList<>();
	/** per page and section: group open or collapsed, for as long as the dialog is open */
	private final Map<String, Boolean> expanded_ = new HashMap<>();
	private Font bold_;

	// several profiles at once
	private Predicate<String> conflicting_;
	/** per setting: the common value of the profiles when the dialog opened (or the DOSBox version changed) */
	private final Map<String, String> firstValues_ = new HashMap<>();
	/** settings with differing values that were reset to the DOSBox default */
	private final Set<String> resetConflicting_ = new HashSet<>();

	ExtraSettings(EditConfigurableDialog<?> dialog) {
		dialog_ = dialog;
	}

	/** several profiles at once: tells which settings (as "section/item") have differing values */
	public void setConflictingValues(Predicate<String> conflicting) {
		conflicting_ = conflicting;
		firstValues_.clear();
		resetConflicting_.clear();
	}

	boolean isMultiEdit() {
		return conflicting_ != null;
	}

	/** every page gets a place at its end for the settings that belong there */
	void addArea(String key, Composite page) {
		Composite area = new Composite(page, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = layout.marginHeight = 0;
		area.setLayout(layout);
		int span = page.getLayout() instanceof GridLayout gl ? gl.numColumns: 1;
		area.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false, span, 1));
		areas_.put(key, area);
	}

	Map<String, List<String[]>> byPage() {
		return byPage_;
	}

	DosboxVersion computedFor() {
		return computedFor_;
	}

	/** take over what was found out before for this DOSBox version (see {@link PageTreeCache}) */
	void useKnown(DosboxVersion dbversion, Map<String, List<String[]>> byPage) {
		if (computedFor_ != dbversion) {
			byPage_ = byPage;
			computedFor_ = dbversion;
		}
	}

	/** before the tree is updated: mark the pages that will have settings, they are only created when the page is shown */
	void markPagesWithContent() {
		for (Map.Entry<String, Composite> e: areas_.entrySet())
			if (!e.getValue().isDisposed())
				e.getValue().setData(SizeControlledPagedDialog.HAS_CONTENT, byPage_.containsKey(e.getKey()) ? Boolean.TRUE: null);
	}

	/**
	 * Decide for every setting of the DOSBox version without a field of its own on which page it goes. All pages must exist,
	 * they tell which settings have a field of their own, and where the fields of each section are.
	 */
	void compute(TemplateProfileBase configurable) {
		DosboxVersion dbversion = configurable.getDosboxVersion();
		if ((dbversion == null) || (dbversion == computedFor_))
			return;
		dialog_.computeAllPages(configurable);
		Set<String> bound = new HashSet<>(DBGL_MANAGED_SETTINGS);
		Map<String, Map<String, Integer>> pagesOfSection = new HashMap<>();
		for (Chain chain: dialog_.metaControls_) {
			Set<String> settings = chain.boundSettings();
			bound.addAll(settings);
			String page = dialog_.pageKeyOf(chain.composite());
			if (page != null)
				settings.forEach(x -> pagesOfSection.computeIfAbsent(x.substring(0, x.indexOf('/')), k -> new HashMap<>()).merge(page, 1, Integer::sum));
		}
		byPage_ = place(dbversion.getConfiguration(), bound, pagesOfSection, areas_.keySet());
		computedFor_ = dbversion;
	}

	/** where each setting without a field goes: by the rules of {@link SettingPlacement}, else where most fields of its section are */
	static Map<String, List<String[]>> place(Configuration dosboxConf, Set<String> bound, Map<String, Map<String, Integer>> pagesOfSection, Set<String> pages) {
		Map<String, List<String[]>> result = new HashMap<>();
		for (String section: dosboxConf.getSectionNames()) {
			if (section.equalsIgnoreCase("autoexec"))
				continue;
			for (String item: dosboxConf.getItemNames(section)) {
				if (bound.contains(section.toLowerCase() + '/' + item.toLowerCase()))
					continue;
				String page = SettingPlacement.pageFor(section, item);
				if ((page == null) || !pages.contains(page))
					page = pagesOfSection.getOrDefault(section.toLowerCase(), Map.of()).entrySet().stream().filter(e -> pages.contains(e.getKey()))
							.max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(SettingPlacement.DOSBOX);
				if (pages.contains(page))
					result.computeIfAbsent(page, k -> new ArrayList<>()).add(new String[] {section, item});
			}
		}
		return result;
	}

	/** after a change of the DOSBox version, the settings created for the previous one are removed, and created again when needed */
	void discardOutdated(TemplateProfileBase configurable) {
		DosboxVersion dbversion = configurable.getDosboxVersion();
		for (Map.Entry<String, Composite> e: areas_.entrySet()) {
			if (builtFor_.containsKey(e.getKey()) && (builtFor_.get(e.getKey()) != dbversion)) {
				Stream.of(e.getValue().getChildren()).forEach(Control::dispose);
				builtFor_.remove(e.getKey());
			}
		}
		settings_.removeIf(g -> g.control().isDisposed());
	}

	/** the page key of the area on this page, or null */
	String keyOfPage(Composite page) {
		return areas_.entrySet().stream().filter(e -> !e.getValue().isDisposed() && (e.getValue().getParent() == page)).map(Map.Entry::getKey).findFirst().orElse(null);
	}

	boolean isBuilt(String key, DosboxVersion dbversion) {
		return builtFor_.get(key) == dbversion;
	}

	/** the search looks through all pages, so all settings must exist */
	void buildAll(TemplateProfileBase configurable) {
		compute(configurable);
		for (String key: byPage_.keySet())
			if (builtFor_.get(key) != configurable.getDosboxVersion())
				build(configurable, key);
	}

	/**
	 * create the settings for one page: one collapsible group per section, each setting with its name (bold when the profile
	 * changes it), the field, a button to go back to the DOSBox default and a short description
	 */
	void build(TemplateProfileBase configurable, String key) {
		DosboxVersion dbversion = configurable.getDosboxVersion();
		Composite area = areas_.get(key);
		if ((dbversion == null) || (area == null) || area.isDisposed())
			return;
		compute(configurable);
		Stream.of(area.getChildren()).forEach(Control::dispose);
		settings_.removeIf(g -> g.control().isDisposed());

		Configuration dosboxConf = dbversion.getConfiguration();
		ConfValueOptions documented = dbversion.getConfValueOptions();
		var text = TextService.getInstance();
		String defaultText = text.get("dialog.template.moresettings.default");
		String groupText = text.get("dialog.template.tab.moresettings");
		String info = text.get("dialog.template.moresettings.tooltip");
		String conflictingText = text.get("dialog.multiprofile.conflictingvalues");
		List<String[]> settings = byPage_.getOrDefault(key, List.of());
		// on the pages that only hold such settings, the groups are open from the start
		boolean onlyExtras = Stream.of(area.getParent().getChildren()).allMatch(c -> (c == area) || (c.getData("dbgl.pageheader") != null));

		if (!settings.isEmpty()) {
			Button onlyChanged = new Button(area, SWT.CHECK);
			onlyChanged.setText(text.get("dialog.template.moresettings.onlychanged"));
			onlyChanged.setToolTipText(info);
			onlyChanged.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
			onlyChanged.setData(ONLY_CHANGED, Boolean.TRUE);
			onlyChanged.addListener(SWT.Selection, e -> {
				applyFilter(key);
				dialog_.relayoutVisiblePage();
			});
		}

		Map<String, Group> groups = new LinkedHashMap<>();
		for (String[] setting: settings) {
			String section = setting[0], item = setting[1];
			Group group = groups.computeIfAbsent(section, s -> createGroup(area, key, s, settings, groupText, info, onlyExtras));
			String defaultValue = dosboxConf.getValue(section, item);
			String help = documented.getHelp(section, item);
			boolean conflicting = isMultiEdit() && conflicting_.test(section.toLowerCase() + '/' + item.toLowerCase());
			String tooltip = (conflicting ? conflictingText + "\n\n": "") + (help != null ? help + "\n\n": "") + "[" + section + "] " + item + "\n" + defaultText + ": "
					+ (StringUtils.isEmpty(defaultValue) ? "-": defaultValue);
			Label label = new Label(group, SWT.NONE);
			label.setText(item.replace("&", "&&"));
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
				Text txt = new Text(group, SWT.BORDER);
				txt.setMessage(conflicting ? conflictingText: defaultText + ": " + StringUtils.defaultString(defaultValue));
				control = txt;
			}
			control.setToolTipText(tooltip);
			GridData gd = new GridData(SWT.FILL, SWT.CENTER, false, false);
			gd.widthHint = 200;
			control.setLayoutData(gd);
			Button reset = new Button(group, SWT.PUSH);
			reset.setText("↺");
			reset.setToolTipText(text.get("dialog.template.moresettings.reset") + " (" + (StringUtils.isEmpty(defaultValue) ? "-": defaultValue) + ")");
			reset.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
			Label description = new Label(group, SWT.WRAP);
			String summary = SettingPlacement.summary(help, 120);
			description.setText(((conflicting ? "(" + conflictingText + ") ": "") + summary).replace("&", "&&"));
			description.setToolTipText(tooltip);
			description.setForeground(dialog_.display_.getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
			GridData dd = new GridData(SWT.FILL, SWT.CENTER, true, false);
			dd.widthHint = 200; // wraps within the width of the page instead of widening it
			description.setLayoutData(dd);

			Setting g = new Setting(section, item, control, label, null, defaultValue);
			settings_.add(g);
			control.addListener(control instanceof Combo ? SWT.Selection: SWT.Modify, e -> markChanged(g));
			if (control instanceof Combo)
				control.addListener(SWT.Modify, e -> markChanged(g));
			reset.addListener(SWT.Selection, e -> {
				if (g.control() instanceof Combo combo) {
					if ((combo.getStyle() & SWT.READ_ONLY) != 0)
						combo.select(0);
					else
						combo.setText("");
				} else {
					((Text)g.control()).setText("");
				}
				if (conflicting)
					resetConflicting_.add(g.key());
				markChanged(g);
			});
		}
		builtFor_.put(key, dbversion);
		area.setData(SizeControlledPagedDialog.HAS_CONTENT, groups.isEmpty() ? null: Boolean.TRUE);
		setValues(configurable);
		applyFilter(key);
		area.layout(true, true);
	}

	private Group createGroup(Composite area, String key, String section, List<String[]> settings, String groupText, String info, boolean onlyExtras) {
		long count = settings.stream().filter(x -> x[0].equals(section)).count();
		String groupKey = key + '|' + section;
		Button header = new Button(area, SWT.FLAT);
		header.setAlignment(SWT.LEFT);
		header.setToolTipText(info);
		header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Group g = new Group(area, SWT.NONE);
		g.setLayout(new GridLayout(4, false));
		g.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		g.setData(HEADER, header);
		header.setData(GROUP_TITLE, groupText + " [" + section + "] (" + count + ")");
		if (onlyExtras)
			g.setData(ONLY_EXTRAS_PAGE, Boolean.TRUE);
		header.addListener(SWT.Selection, e -> {
			expanded_.put(groupKey, !Boolean.TRUE.equals(expanded_.get(groupKey)));
			applyFilter(key);
			dialog_.relayoutVisiblePage();
		});
		g.setData(GROUP_KEY, groupKey);
		return g;
	}

	/** a setting the profile/template changes (not empty and not the DOSBox default) is shown in bold */
	private void markChanged(Setting g) {
		if (g.label().isDisposed())
			return;
		boolean changed = g.isChanged();
		if (Boolean.valueOf(changed).equals(g.label().getData(BOLD)))
			return;
		if (changed && (bold_ == null)) {
			FontData[] fd = g.label().getFont().getFontData();
			Stream.of(fd).forEach(x -> x.setStyle(SWT.BOLD));
			bold_ = new Font(dialog_.display_, fd);
			dialog_.shell_.addDisposeListener(e -> bold_.dispose());
		}
		g.label().setFont(changed ? bold_: null);
		g.label().setData(BOLD, changed);
		g.label().getParent().layout(true);
		if (g.label().getParent() instanceof Group group)
			updateHeader(group, null);
	}

	/**
	 * Whether a group starts open, as set under 'Settings': always, only when the profile/template changes one of its
	 * settings, or collapsed. Pages that only hold such settings may always start open.
	 */
	static boolean initiallyExpanded(boolean onlyExtrasPage, int changedCount) {
		SettingsService settings = SettingsService.getInstance();
		if (onlyExtrasPage && !settings.getBooleanValue("gui", "extrasexpandonextraspages"))
			return true;
		switch (settings.getValue("gui", "extrasexpand")) {
			case "always": return true;
			case "collapsed": return false;
			default: return changedCount > 0;
		}
	}

	private int changedCount(Group group) {
		return (int)settings_.stream().filter(g -> !g.label().isDisposed() && (g.label().getParent() == group) && g.isChanged()).count();
	}

	/** the heading of a group: open or closed, title, and if wanted the number of changed settings */
	private void updateHeader(Group group, Boolean open) {
		Button header = (Button)group.getData(HEADER);
		if ((header == null) || header.isDisposed())
			return;
		boolean isOpen = open != null ? open: group.getVisible();
		String title = (String)header.getData(GROUP_TITLE);
		int changed = changedCount(group);
		if ((changed > 0) && SettingsService.getInstance().getBooleanValue("gui", "extrascountchanges"))
			title += " - " + TextService.getInstance().get("dialog.template.moresettings.changedcount", new Object[] {changed});
		String text = (isOpen ? "▾ ": "▸ ") + title.replace("&", "&&");
		if (!text.equals(header.getText()))
			header.setText(text);
	}

	/** collapsed or open groups, and 'only changed values' */
	private void applyFilter(String key) {
		Composite area = areas_.get(key);
		if ((area == null) || area.isDisposed())
			return;
		boolean onlyChanged = Stream.of(area.getChildren()).anyMatch(c -> (c.getData(ONLY_CHANGED) != null) && ((Button)c).getSelection());
		for (Control c: area.getChildren()) {
			if (!(c instanceof Group group) || (group.getData(HEADER) == null))
				continue;
			Button header = (Button)group.getData(HEADER);
			boolean expanded = expanded_.computeIfAbsent((String)group.getData(GROUP_KEY), k -> initiallyExpanded(group.getData(ONLY_EXTRAS_PAGE) != null, changedCount(group)));
			int shown = 0;
			Map<Control, Setting> byLabel = new HashMap<>();
			settings_.stream().filter(g -> !g.label().isDisposed() && (g.label().getParent() == group)).forEach(g -> byLabel.put(g.label(), g));
			Control[] row = group.getChildren(); // per setting: label, field, reset button, description
			for (int i = 0; i + 3 < row.length; i += 4) {
				Setting g = byLabel.get(row[i]);
				if (g == null)
					continue;
				boolean show = !onlyChanged || g.isChanged();
				for (int j = 0; j < 4; j++)
					setShown(row[i + j], show);
				if (show)
					shown++;
			}
			setShown(header, !onlyChanged || (shown > 0));
			boolean open = (onlyChanged && (shown > 0)) || (!onlyChanged && expanded);
			setShown(group, open);
			updateHeader(group, open);
		}
		area.layout(true, true);
	}

	private static void setShown(Control c, boolean show) {
		c.setVisible(show);
		((GridData)c.getLayoutData()).exclude = !show;
	}

	/** the search jumped to a page: open its collapsed groups that contain a match */
	void onSearchJump(Composite page, String s) {
		for (Map.Entry<String, Composite> e: areas_.entrySet()) {
			if (e.getValue().isDisposed() || (e.getValue().getParent() != page))
				continue;
			boolean changed = false;
			for (Control c: e.getValue().getChildren()) {
				if (!(c instanceof Group group) || (group.getData(GROUP_KEY) == null))
					continue;
				boolean hit = Stream.of(group.getChildren()).anyMatch(x -> (x instanceof Label l) && (StringUtils.containsIgnoreCase(l.getText(), s) || StringUtils.containsIgnoreCase(l.getToolTipText(), s)));
				if (hit && !Boolean.TRUE.equals(expanded_.get(group.getData(GROUP_KEY)))) {
					expanded_.put((String)group.getData(GROUP_KEY), true);
					changed = true;
				}
			}
			if (changed) {
				applyFilter(e.getKey());
				dialog_.relayoutVisiblePage();
			}
		}
	}

	/** values: what the profile/template sets itself, empty means the DOSBox default */
	void setValues(TemplateProfileBase configurable) {
		for (int i = 0; i < settings_.size(); i++) {
			Setting g = settings_.get(i);
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
			settings_.set(i, new Setting(g.section(), g.item(), g.control(), g.label(), value, g.defaultValue()));
			firstValues_.putIfAbsent(g.key(), value);
			markChanged(settings_.get(i));
		}
	}

	/** DOSBox documents no values for most switches, offer the two values in the notation of the default */
	static List<String> booleanValues(String defaultValue) {
		if (defaultValue == null)
			return null;
		switch (defaultValue.strip().toLowerCase()) {
			case "true", "false": return List.of("true", "false");
			case "on", "off": return List.of("on", "off");
			case "yes", "no": return List.of("yes", "no");
			default: return null;
		}
	}

	/** write the changed settings into the profile/template */
	void writeTo(TemplateProfileBase configurable) {
		for (Setting g: settings_) {
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

	/** several profiles at once: the settings changed since the dialog opened (or the DOSBox version changed) */
	public List<Change> changes() {
		List<Change> result = new ArrayList<>();
		for (Setting g: settings_) {
			if (g.control().isDisposed())
				continue;
			String first = StringUtils.defaultString(firstValues_.get(g.key()));
			String value = g.value();
			boolean conflicting = isMultiEdit() && conflicting_.test(g.key());
			if (!value.equals(first) || (conflicting && resetConflicting_.contains(g.key())))
				result.add(new Change(g.section(), g.item(), first, conflicting, value));
		}
		return result;
	}

	/** apply changes to one of several profiles */
	public static void apply(List<Change> changes, TemplateProfileBase configurable) {
		for (Change c: changes) {
			if (c.newValue().isEmpty())
				configurable.getConfiguration().removeValueIfSet(c.section(), c.item());
			else
				configurable.setValue(c.section(), c.item(), c.newValue());
		}
	}
}
