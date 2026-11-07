/*
 *  Copyright (C) 2026  mrmercy82 (DBGL Extended Edition, written with the help of AI)
 *  Part of a modified version of DBGL, Copyright (C) 2006-2025  Ronald Blankendaal
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

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Configuration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


/**
 * Replace a value in many profiles at once, e.g. output=surface by output=texture in all selected profiles after moving to
 * another monitor or DOSBox version. A preview lists which profiles change; only the ticked ones are changed. The value
 * compared is the one that applies: from the profile, or else from its DOSBox configuration. The result is the number of
 * profiles changed, or null when cancelled.
 */
public class ReplaceValuesDialog extends SizeControlledButtonDialog<Integer> {

	/** one profile that would change */
	record Change(Profile profile, String oldValue) {
	}

	private final List<Profile> profiles_;
	private final List<Change> changes_ = new ArrayList<>();

	public ReplaceValuesDialog(Shell parent, List<Profile> profiles) {
		super(parent, "replacevaluesdialog");
		profiles_ = profiles;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.replacevalues.title", new Object[] {profiles_.size()});
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(4, false));
		Label info = new Label(contents_, SWT.WRAP);
		info.setText(text_.get("dialog.replacevalues.info"));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 4, 1);
		infoData.widthHint = 560;
		info.setLayoutData(infoData);

		// the sections and settings the DOSBox versions of these profiles know, to choose from (typing is fine as well)
		TreeSet<String> sections = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		profiles_.stream().map(p -> p.getDosboxVersion().getConfiguration()).distinct().forEach(c -> sections.addAll(List.of(c.getSectionNames())));

		new Label(contents_, SWT.NONE).setText(text_.get("dialog.replacevalues.section"));
		Combo section = new Combo(contents_, SWT.DROP_DOWN);
		section.setItems(sections.toArray(new String[0]));
		section.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		new Label(contents_, SWT.NONE).setText(text_.get("dialog.replacevalues.item"));
		Combo item = new Combo(contents_, SWT.DROP_DOWN);
		item.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		section.addListener(SWT.Modify, e -> {
			TreeSet<String> items = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			profiles_.stream().map(p -> p.getDosboxVersion().getConfiguration()).distinct().filter(c -> List.of(c.getSectionNames()).contains(section.getText().trim().toLowerCase()))
					.forEach(c -> items.addAll(List.of(c.getItemNames(section.getText().trim().toLowerCase()))));
			String current = item.getText();
			item.setItems(items.toArray(new String[0]));
			item.setText(current);
		});

		new Label(contents_, SWT.NONE).setText(text_.get("dialog.replacevalues.from"));
		// before and after: free text, or chosen from a list: the values the profiles have now (before) and the values the
		// DOSBox version documents for this setting (after; both lists hold both, the likely ones first)
		Combo from = new Combo(contents_, SWT.DROP_DOWN);
		from.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		from.setToolTipText(text_.get("dialog.replacevalues.from.tooltip"));
		new Label(contents_, SWT.NONE).setText(text_.get("dialog.replacevalues.to"));
		Combo to = new Combo(contents_, SWT.DROP_DOWN);
		to.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		to.setToolTipText(text_.get("dialog.replacevalues.to.tooltip"));
		Runnable fillValues = () -> {
			String sec = section.getText().trim().toLowerCase(), itm = item.getText().trim().toLowerCase();
			java.util.LinkedHashSet<String> current = new java.util.LinkedHashSet<>(), documented = new java.util.LinkedHashSet<>();
			if (!sec.isEmpty() && !itm.isEmpty()) {
				for (Profile p: profiles_) {
					String v = currentValue(p, sec, itm);
					if (v != null)
						current.add(v.trim());
				}
				// the values DBGL offers for this setting in the profile window (for the DOSBox version of each profile)
				for (Profile p: profiles_)
					documented.addAll(dbglValues(p, itm));
				profiles_.stream().map(Profile::getDosboxVersion).distinct().forEach(d -> {
					try {
						org.dbgl.model.conf.ConfValueOptions.Options o = d.getConfValueOptions().get(sec, itm);
						if (o != null)
							documented.addAll(o.getValues());
					} catch (RuntimeException ex) {
						// no documented values for this version
					}
				});
			}
			// a switch DOSBox documents without a list ("start in fullscreen mode"): its pair of values
			if (documented.isEmpty() && !current.isEmpty())
				for (String[] pair: new String[][] {{"true", "false"}, {"on", "off"}, {"yes", "no"}})
					if (current.stream().allMatch(v -> v.equalsIgnoreCase(pair[0]) || v.equalsIgnoreCase(pair[1])))
						documented.addAll(List.of(pair));
			for (Combo c: new Combo[] {from, to}) {
				java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>(c == from ? current: documented);
				values.addAll(c == from ? new TreeSet<>(documented): new TreeSet<>(current));
				values.remove("");
				String text = c.getText();
				c.setItems(values.toArray(new String[0]));
				c.setText(text);
			}
		};
		section.addListener(SWT.Modify, e -> fillValues.run());
		item.addListener(SWT.Modify, e -> fillValues.run());

		Button preview = new Button(contents_, SWT.PUSH);
		preview.setText(text_.get("dialog.replacevalues.preview"));
		preview.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 4, 1));

		Table table = new Table(contents_, SWT.CHECK | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1);
		tableData.heightHint = 260;
		table.setLayoutData(tableData);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		for (String key: new String[] {"dialog.replacevalues.column.profile", "dialog.replacevalues.column.old", "dialog.replacevalues.column.new"}) {
			TableColumn col = new TableColumn(table, SWT.NONE);
			col.setText(text_.get(key));
			col.setWidth(key.endsWith("profile") ? 260: 150);
		}
		Label summary = new Label(contents_, SWT.NONE);
		summary.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));

		preview.addListener(SWT.Selection, e -> {
			table.removeAll();
			changes_.clear();
			String sec = section.getText().trim().toLowerCase();
			String itm = item.getText().trim().toLowerCase();
			if (sec.isEmpty() || itm.isEmpty()) {
				summary.setText(text_.get("dialog.replacevalues.choosesetting"));
				return;
			}
			int unknown = 0;
			for (Profile p: profiles_) {
				String old = currentValue(p, sec, itm);
				if (old == null) {
					unknown++;
					continue;
				}
				if (matches(old, from.getText()) && !StringUtils.equals(old.trim(), to.getText().trim())) {
					changes_.add(new Change(p, old));
					TableItem ti = new TableItem(table, SWT.NONE);
					ti.setText(new String[] {p.getTitle(), old, to.getText().trim()});
					ti.setChecked(true);
				}
			}
			summary.setText(text_.get("dialog.replacevalues.summary", new Object[] {changes_.size(), profiles_.size(), unknown}));
			summary.getParent().layout();
		});

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (changes_.isEmpty()) {
					Mess_.on(shell_).key("dialog.replacevalues.nothing").display();
					return;
				}
				String sec = section.getText().trim().toLowerCase();
				String itm = item.getText().trim().toLowerCase();
				int done = 0;
				StringBuilder failed = new StringBuilder();
				List<Profile> touched = new ArrayList<>();
				for (int i = 0; i < changes_.size(); i++)
					if (table.getItem(i).getChecked())
						touched.add(changes_.get(i).profile());
				org.dbgl.util.BulkUndo.remember(text_.get("dialog.main.profile.replacevalues").replace("...", ""), touched);
				for (int i = 0; i < changes_.size(); i++) {
					if (!table.getItem(i).getChecked())
						continue;
					Profile p = changes_.get(i).profile();
					try {
						apply(p, sec, itm, to.getText().trim());
						done++;
					} catch (Exception e) {
						failed.append(p.getTitle()).append(": ").append(e.getMessage()).append('\n');
					}
				}
				if (failed.length() > 0)
					Mess_.on(shell_).txt(failed.toString().trim()).warning();
				result_ = done;
				shell_.close();
			}
		});
		okButton_.setText(text_.get("dialog.replacevalues.replace"));
	}

	/** @return the value that applies for the profile (own or from its DOSBox configuration), null if its DOSBox does not have the setting */
	/**
	 * @return the values the profile window offers for this setting: DBGL keeps them per kind and version of DOSBox (e.g.
	 *         "output_staging0820"), which of them applies tells the profile (its ...SectionItem methods)
	 */
	static List<String> dbglValues(Profile p, String item) {
		org.dbgl.service.SettingsService settings = org.dbgl.service.SettingsService.getInstance();
		java.util.Set<String> keys = new java.util.LinkedHashSet<>();
		for (java.lang.reflect.Method m: p.getClass().getMethods()) {
			if (m.getName().endsWith("SectionItem") && (m.getParameterCount() == 0) && (m.getReturnType() == String.class)) {
				try {
					String key = (String)m.invoke(p);
					if ((key != null) && (key.equalsIgnoreCase(item) || key.toLowerCase().startsWith(item + "_")))
						keys.add(key);
				} catch (ReflectiveOperationException | RuntimeException e) {
					// not this one
				}
			}
		}
		if (keys.isEmpty())
			keys.add(item);
		List<String> values = new ArrayList<>();
		for (String key: keys)
			if (settings.hasValue("profile", key))
				for (String v: settings.getValues("profile", key))
					if (StringUtils.isNotBlank(v) && !values.contains(v.trim()))
						values.add(v.trim());
		return values;
	}

	static String currentValue(Profile p, String section, String item) {
		Configuration dosboxConf = p.getDosboxVersion().getConfiguration();
		if (!dosboxConf.hasValue(section, item))
			return null;
		Configuration own = p.getConfiguration();
		return own.hasValue(section, item) ? own.getValue(section, item): dosboxConf.getValue(section, item);
	}

	/** an empty "from" matches any value; otherwise the same value, ignoring case and spaces around it */
	static boolean matches(String value, String from) {
		return StringUtils.isBlank(from) || value.trim().equalsIgnoreCase(from.trim());
	}

	static void apply(Profile p, String section, String item, String value) throws java.io.IOException {
		p.setValue(section, item, value);
		p.saveConfiguration();
	}
}
