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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.dbgl.util.SwitchChoices;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * Switches all profiles of one DOSBox version to another one (like 'Switch' in the profile dialog, so settings are converted),
 * with a preview and a report of what changed in each profile. With a profile given, only this one profile is switched.
 */
public class SwitchDosboxVersionDialog extends SizeControlledButtonDialog<Boolean> {

	private List<DosboxVersion> dbversions_;
	private List<Profile> profiles_;
	private Combo from_, to_;
	private Label count_;
	private org.eclipse.swt.custom.StyledText report_;
	private Font bold_;
	private Table choices_;
	private org.eclipse.swt.widgets.Button markers_;
	/** alpha 49: "Preview" and "Compare versions": off, like "Switch", until a DOSBox version to switch to is chosen */
	private org.eclipse.swt.widgets.Button previewButton_, compareButton_;
	/** what the user ticked in the preview, by {@link SwitchChoices.Choice#key()}; not in it: the default */
	private final java.util.Map<String, Boolean> keep_ = new java.util.HashMap<>();
	private Font font_;
	/** the ids of the profiles chosen in the main window, or null for all profiles of a DOSBox version */
	private final java.util.Set<Integer> selected_;
	/** what the last preview was for (the two versions), to ask before switching without one */
	private String previewed_;
	private boolean lastWasPreview_;
	private org.eclipse.swt.widgets.Button overviewButton_;
	private String reportBeforeOverview_; // shown again with the button, null: the overview is not shown

	/** the overview, or back to what the report showed before it (the preview); without one yet, the preview is made */
	private void toggleOverview() {
		if (reportBeforeOverview_ == null) {
			reportBeforeOverview_ = report_.getText();
			showOverview();
			overviewButton_.setText(text_.get("dialog.switchdosbox.preview"));
			return;
		}
		String before = reportBeforeOverview_;
		if (StringUtils.isBlank(before)) {
			run(true); // (if it cannot be made, e.g. no profiles, the overview stays and the button too)
		} else {
			reportShown();
			report_.setText(before);
			colorReport();
		}
	}

	/** a report (preview, switch, comparison) is shown: the button offers the overview again */
	private void reportShown() {
		reportBeforeOverview_ = null;
		if ((overviewButton_ != null) && !overviewButton_.isDisposed())
			overviewButton_.setText(text_.get("dialog.switchdosbox.overview"));
	}

	/** alpha 49: below each profile, what it takes from the configuration of the new DOSBox version (nothing of it is in its file) */
	private static boolean showInherited() {
		return org.dbgl.service.SettingsService.getInstance().getBooleanValue("switchdosbox", "showinherited");
	}

	private static boolean fullFile() {
		return org.dbgl.service.SettingsService.getInstance().getBooleanValue("switchdosbox", "fullfile");
	}

	private static boolean byId() {
		return "id".equals(org.dbgl.service.SettingsService.getInstance().getValue("switchdosbox", "sort"));
	}

	public SwitchDosboxVersionDialog(Shell parent) {
		this(parent, (List<Profile>)null);
	}

	/** @param single only this profile is switched, or all profiles of a DOSBox version if null */
	public SwitchDosboxVersionDialog(Shell parent, Profile single) {
		this(parent, single == null ? null: List.of(single));
	}

	/** @param selected only these profiles are switched, or all profiles of a DOSBox version if null */
	public SwitchDosboxVersionDialog(Shell parent, List<Profile> selected) {
		super(parent, "switchdosboxdialog");
		selected_ = selected == null ? null: selected.stream().map(Profile::getId).collect(java.util.stream.Collectors.toSet());
	}

	/** the DOSBox version to switch to, chosen beforehand (e.g. DOSBox-X for the profiles that need it), or -1 */
	private int targetId_ = -1;

	public SwitchDosboxVersionDialog withTarget(int dbversionId) {
		targetId_ = dbversionId;
		return this;
	}

	/** the DOSBox version to switch from, chosen beforehand, or -1 */
	private int fromId_ = -1;
	/** what the profiles were written for, chosen beforehand (null: as the DOSBox version says) */
	private Generation writtenForPreset_;
	private Combo writtenFor_;

	/**
	 * Alpha 48: the version of a DOSBox version in DBGL was changed (e.g. "DOSBox Staging 0.82" that runs 0.83 in truth, and
	 * DBGL found out), its profiles are still written for the old one. They are converted in place, from that old version.
	 */
	public SwitchDosboxVersionDialog withWrittenFor(int dbversionId, Generation gen) {
		fromId_ = dbversionId;
		targetId_ = dbversionId;
		writtenForPreset_ = gen;
		return this;
	}

	/** a name for a generation, as DBGL lists the versions ("DOSBox Staging 0.82") */
	static String generationTitle(Generation g) {
		String n = g.name();
		String v = n.substring(n.indexOf('_') + 1);
		return switch (n.substring(0, n.indexOf('_'))) {
			case "official" -> "DOSBox " + v.charAt(0) + '.' + v.substring(1);
			case "svn" -> "DOSBox SVN " + v;
			case "daum" -> "DOSBox Daum";
			case "ece" -> "DOSBox ECE " + v;
			case "staging" -> "DOSBox Staging " + v.charAt(0) + '.' + v.substring(1, 3);
			default -> "DOSBox-X " + (v.length() == 8 ? v.substring(0, 4) + '.' + v.substring(4, 6) + '.' + v.substring(6): v.charAt(0) + "." + v.substring(1, 3) + '.' + v.substring(3));
		};
	}

	/** what the user said the profiles were written for, or null: as their DOSBox version says */
	private Generation writtenFor() {
		int i = (writtenFor_ == null) ? 0: writtenFor_.getSelectionIndex();
		return i <= 0 ? null: Generation.values()[i - 1];
	}

	@Override
	protected String getDialogTitle() {
		return text_.get((selected_ == null) || (selected_.size() > 1) ? "dialog.switchdosbox.title": "dialog.switchdosbox.title.single");
	}

	@Override
	protected boolean prepare() {
		try {
			dbversions_ = new DosboxVersionRepository().listAll();
			profiles_ = new ProfileRepository().list(StringUtils.EMPTY, StringUtils.EMPTY, dbversions_);
			return true;
		} catch (SQLException e) {
			Mess_.on(getParent()).exception(e).warning();
			return false;
		}
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(2, false));
		String[] titles = dbversions_.stream().map(DosboxVersion::getTitle).toArray(String[]::new);
		from_ = Chain.on(contents_).lbl(l -> l.key("dialog.switchdosbox.from")).cmb(c -> c.items(titles).visibleItemCount(15)).combo();
		to_ = Chain.on(contents_).lbl(l -> l.key("dialog.switchdosbox.to")).cmb(c -> c.items(titles).visibleItemCount(15)).combo();
		// alpha 48: the profiles may be written for another version than their DOSBox version says (its version changed later)
		java.util.List<String> gens = new java.util.ArrayList<>();
		gens.add(text_.get("dialog.switchdosbox.writtenfor.asis"));
		for (Generation g: Generation.values())
			gens.add(generationTitle(g));
		writtenFor_ = Chain.on(contents_).lbl(l -> l.key("dialog.switchdosbox.writtenfor")).cmb(c -> c.items(gens.toArray(String[]::new)).visibleItemCount(15)).combo();
		writtenFor_.setToolTipText(text_.get("dialog.switchdosbox.writtenfor.tooltip"));
		// all three as wide as the longest entry of any of them (it was cut off, alpha 48)
		int comboWidth = 0;
		for (Combo c: new Combo[] {from_, to_, writtenFor_})
			comboWidth = Math.max(comboWidth, c.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x + 24);
		for (Combo c: new Combo[] {from_, to_, writtenFor_}) {
			GridData g = c.getLayoutData() instanceof GridData gd ? gd: new GridData();
			g.widthHint = comboWidth;
			g.horizontalAlignment = SWT.BEGINNING;
			g.grabExcessHorizontalSpace = false;
			c.setLayoutData(g);
		}
		writtenFor_.select(writtenForPreset_ == null ? 0: writtenForPreset_.ordinal() + 1);
		new Label(contents_, SWT.NONE);
		Composite countRow = new Composite(contents_, SWT.NONE);
		countRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout countLayout = new GridLayout(3, false);
		countLayout.marginWidth = 0;
		countLayout.marginHeight = 0;
		countRow.setLayout(countLayout);
		// on a line of its own (alpha 48: in one line with the controls below, it was cut off or pushed them over the list)
		count_ = new Label(countRow, SWT.WRAP);
		count_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		// the profiles in the preview and the report: by the title of the game or by its profile ID (remembered)
		Combo sort = new Combo(countRow, SWT.READ_ONLY);
		sort.setItems(text_.get("dialog.checkprofiles.sort.name"), text_.get("dialog.checkprofiles.sort.id"));
		sort.select(byId() ? 1: 0);
		sort.addListener(SWT.Selection, e -> {
			org.dbgl.service.SettingsService.getInstance().setValue("switchdosbox", "sort", sort.getSelectionIndex() == 1 ? "id": "name");
			if (lastWasPreview_)
				run(true); // the preview shown again in the new order (the ticks stay)
		});
		// the whole configuration of each profile in the preview, its changes marked, or only the changes (alpha 47, remembered)
		org.eclipse.swt.widgets.Button full = new org.eclipse.swt.widgets.Button(countRow, SWT.CHECK);
		full.setText(text_.get("dialog.checkprofiles.previewfull"));
		full.setSelection(fullFile());
		full.addListener(SWT.Selection, e -> {
			org.dbgl.service.SettingsService.getInstance().setBooleanValue("switchdosbox", "fullfile", full.getSelection());
			if (lastWasPreview_)
				run(true);
		});
		// how many profiles use which DOSBox version, into the report below; then the same button goes back to the preview
		overviewButton_ = (org.eclipse.swt.widgets.Button)Button_.on(countRow).text().key("dialog.switchdosbox.overview").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				toggleOverview();
			}
		}).ctrl();
		// alpha 49: below each profile what it inherits from the new DOSBox version (remembered; off: only said once at the top);
		// in a line of its own: next to the other controls it pushed the button out of the window
		org.eclipse.swt.widgets.Button inheritedBox = new org.eclipse.swt.widgets.Button(countRow, SWT.CHECK);
		inheritedBox.setText(text_.get("dialog.switchdosbox.showinherited"));
		inheritedBox.setToolTipText(text_.get("dialog.switchdosbox.showinherited.tooltip"));
		inheritedBox.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));
		inheritedBox.setSelection(showInherited());
		inheritedBox.addListener(SWT.Selection, e -> {
			org.dbgl.service.SettingsService.getInstance().setBooleanValue("switchdosbox", "showinherited", inheritedBox.getSelection());
			if (lastWasPreview_)
				run(true);
		});
		int wide = 0; // as wide as the longer of its two texts, so that nothing moves when it changes
		for (String key: new String[] {"dialog.switchdosbox.overview", "dialog.switchdosbox.preview"}) {
			overviewButton_.setText(text_.get(key));
			wide = Math.max(wide, overviewButton_.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
		}
		overviewButton_.setText(text_.get("dialog.switchdosbox.overview"));
		GridData ob = overviewButton_.getLayoutData() instanceof GridData g ? g: new GridData();
		ob.widthHint = Math.max(ob.widthHint, wide);
		ob.horizontalAlignment = SWT.BEGINNING; // not over the rest of the row (alpha 48)
		ob.grabExcessHorizontalSpace = false;
		overviewButton_.setLayoutData(ob);

		SashForm sash = new SashForm(contents_, SWT.VERTICAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		report_ = new org.eclipse.swt.custom.StyledText(sash, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		createChoices(sash);
		sash.setWeights(55, 45);
		font_ = new Font(shell_.getDisplay(), SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		report_.setFont(font_);
		bold_ = new Font(shell_.getDisplay(), SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.BOLD);
		shell_.addDisposeListener(e -> {
			font_.dispose();
			bold_.dispose();
		});
		// to copy from it
		org.eclipse.swt.widgets.Menu menu = new org.eclipse.swt.widgets.Menu(report_);
		org.eclipse.swt.widgets.MenuItem copy = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
		copy.setText(text_.get("dialog.checkprofiles.copy"));
		copy.addListener(SWT.Selection, e -> {
			if (report_.getSelectionCount() == 0)
				report_.selectAll();
			report_.copy();
		});
		org.eclipse.swt.widgets.MenuItem selectAll = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
		selectAll.setText(text_.get("dialog.checkprofiles.selectall"));
		selectAll.addListener(SWT.Selection, e -> report_.selectAll());
		report_.setMenu(menu);

		// preselect: from the version with the most profiles (or the version of the single profile), to the newest one
		int best = 0;
		for (int i = 0; i < dbversions_.size(); i++)
			if (profilesOf(dbversions_.get(i)).size() > profilesOf(dbversions_.get(best)).size())
				best = i;
		if (!dbversions_.isEmpty()) {
			from_.select(best);
			// alpha 49: "to" is the same as "from" at first, and nothing can be started: the DOSBox version to switch to is
			// chosen, not proposed (before: the last of the list, whatever that was - a 0.74 for profiles of DOSBox Staging)
			to_.select(best);
			for (int i = 0; i < dbversions_.size(); i++)
				if (dbversions_.get(i).getId() == targetId_)
					to_.select(i);
			for (int i = 0; i < dbversions_.size(); i++)
				if (dbversions_.get(i).getId() == fromId_)
					from_.select(i);
		}
		from_.setEnabled(selected_ == null);
		SelectionAdapter update = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateCount();
			}
		};
		from_.addSelectionListener(update);
		to_.addSelectionListener(update);
		writtenFor_.addSelectionListener(update);
		updateCount();

		createGoCancelButtons("dialog.switchdosbox.switch2", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				run(false);
			}
		});
		// Preview, Switch, Cancel and Compare versions together on the left; Save report alone on the right
		((GridLayout)okCancelButtons_.getLayout()).numColumns = 4;
		org.eclipse.swt.widgets.Control first = okCancelButtons_.getChildren()[0];
		org.eclipse.swt.widgets.Control preview = Button_.on(okCancelButtons_).text().key("dialog.switchdosbox.preview").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				run(true);
			}
		}).ctrl();
		preview.moveAbove(first);
		previewButton_ = (org.eclipse.swt.widgets.Button)preview;
		shell_.setDefaultButton((org.eclipse.swt.widgets.Button)preview); // looking first is what Enter does
		if ((first.getLayoutData() instanceof GridData fd) && (fd.widthHint > 0)) { // as wide as Switch and Cancel
			GridData pd = preview.getLayoutData() instanceof GridData g ? g: new GridData();
			pd.widthHint = Math.max(fd.widthHint, preview.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
			preview.setLayoutData(pd);
		}
		compareButton_ = (org.eclipse.swt.widgets.Button)Button_.on(okCancelButtons_).text().key("dialog.switchdosbox.compare").tooltip("dialog.switchdosbox.compare.tooltip")
				.listen(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent event) {
						compareVersions();
					}
				}).ctrl();
		updateButtons();
		Button_.on(otherButtons_).text().key("dialog.switchdosbox.savereport").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				saveReport();
			}
		}).ctrl();
	}

	/** the lines that are dropped or changed, each with a tick to keep the previous line */
	private void createChoices(Composite parent) {
		Composite box = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(4, false);
		layout.marginWidth = 0;
		box.setLayout(layout);
		Label info = new Label(box, SWT.WRAP);
		info.setText(text_.get("dialog.switchdosbox.choices"));
		info.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		org.eclipse.swt.widgets.Button all = new org.eclipse.swt.widgets.Button(box, SWT.PUSH);
		all.setText(text_.get("dialog.switchdosbox.keepall"));
		org.eclipse.swt.widgets.Button none = new org.eclipse.swt.widgets.Button(box, SWT.PUSH);
		none.setText(text_.get("dialog.switchdosbox.keepnone"));
		choices_ = new Table(box, SWT.CHECK | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		choices_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1));
		markers_ = new org.eclipse.swt.widgets.Button(box, SWT.CHECK);
		markers_.setText(text_.get("dialog.unknownlines.markers"));
		markers_.setSelection(org.dbgl.util.UnknownAutoexecLines.markersChosen()); // as last time
		markers_.addListener(SWT.Selection, e -> org.dbgl.util.UnknownAutoexecLines.rememberMarkers(markers_.getSelection()));
		markers_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));
		UnknownLinesDialog.markersWhere(box, 4);
		choices_.setHeaderVisible(true);
		choices_.setLinesVisible(true);
		for (String key: new String[] {"dialog.switchdosbox.column.profile", "dialog.switchdosbox.column.where", "dialog.switchdosbox.column.old", "dialog.switchdosbox.column.new"}) {
			TableColumn col = new TableColumn(choices_, SWT.NONE);
			col.setText(text_.get(key));
			col.setWidth(key.endsWith("profile") ? 180: 200);
		}
		choices_.addListener(SWT.Selection, e -> {
			if ((e.detail == SWT.CHECK) && (e.item instanceof TableItem item)) {
				keep_.put(((SwitchChoices.Choice)item.getData()).key(), item.getChecked());
				previewOutdated();
			}
		});
		all.addListener(SWT.Selection, e -> setAllChoices(true));
		none.addListener(SWT.Selection, e -> setAllChoices(false));
	}

	private void setAllChoices(boolean keep) {
		for (TableItem item: choices_.getItems()) {
			// "keep all" is about what the profiles lose; a value of the DOSBox version is kept only when ticked by hand
			if (keep && (((SwitchChoices.Choice)item.getData()).kind() == SwitchChoices.Kind.INHERITED))
				continue;
			item.setChecked(keep);
			keep_.put(((SwitchChoices.Choice)item.getData()).key(), keep);
		}
		previewOutdated();
	}

	private boolean outdatedShown_;

	/** ticks changed after the preview: it says so at its top, until the next preview (alpha 48: it showed the old state) */
	private void previewOutdated() {
		if (!lastWasPreview_ || outdatedShown_ || (reportBeforeOverview_ != null))
			return;
		outdatedShown_ = true;
		report_.setText("! " + text_.get("dialog.switchdosbox.previewoutdated") + Text.DELIMITER + Text.DELIMITER + report_.getText());
		colorReport();
		report_.setTopIndex(0);
	}

	private boolean keeps(SwitchChoices.Choice c) {
		return keep_.getOrDefault(c.key(), c.defaultKeep());
	}

	private String where(SwitchChoices.Choice c) {
		if (c.kind() != SwitchChoices.Kind.AUTOEXEC)
			return '[' + c.section() + "] " + c.item();
		String where = "[autoexec] " + text_.get("dialog.template.customcommand" + (c.autoexecSection() + 1));
		return c.afterExit() ? where + " (" + text_.get("dialog.switchdosbox.afterexit") + ')': where;
	}

	/** the values the profiles take from their DOSBox version, at the top of the list: one line for all profiles */
	private void addInherited(DosboxVersion orig, List<SwitchChoices.Choice> inherited) {
		int at = 0;
		for (SwitchChoices.Choice c: inherited) {
			if (java.util.Arrays.stream(choices_.getItems()).anyMatch(i -> ((SwitchChoices.Choice)i.getData()).key().equals(c.key())))
				continue; // the same from another old DOSBox version
			TableItem item = new TableItem(choices_, SWT.NONE, at++);
			item.setText(new String[] {text_.get("dialog.switchdosbox.inherited", new Object[] {orig.getTitle()}), where(c), c.oldText(), c.newText()});
			item.setChecked(keeps(c));
			item.setData(c);
		}
	}

	private void addChoices(Profile prof, List<SwitchChoices.Choice> choices) {
		for (SwitchChoices.Choice c: choices) {
			TableItem item = new TableItem(choices_, SWT.NONE);
			if (c.kind() == SwitchChoices.Kind.RESTORE) // lost when switched away from this kind of DOSBox before: tick to restore
				item.setText(new String[] {prof.getTitle(), where(c), text_.get("dialog.switchdosbox.remembered"), c.oldText()});
			else
				item.setText(new String[] {prof.getTitle(), where(c), c.oldText(), c.newText() == null ? text_.get("dialog.switchdosbox.dropped"): c.newText()});
			item.setChecked(keeps(c));
			item.setData(c);
		}
	}

	/** show in the report what changes from the old to the new DOSBox version */
	private void compareVersions() {
		if ((from_.getSelectionIndex() < 0) || (to_.getSelectionIndex() < 0))
			return;
		DosboxVersion src = dbversions_.get(from_.getSelectionIndex());
		DosboxVersion dst = dbversions_.get(to_.getSelectionIndex());
		try {
			for (DosboxVersion v: new DosboxVersion[] {src, dst})
				if (v.getConfiguration().getSectionNames().length == 0)
					v.resetAndLoadConfiguration();
			report_.setText(org.dbgl.util.ProfileReports.compareVersions(src.getConfiguration(), src.getTitle(), dst.getConfiguration(), dst.getTitle(), text_)
					.replace(System.lineSeparator(), Text.DELIMITER));
			colorReport();
			reportShown();
		} catch (java.io.IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	private List<Profile> profilesOf(DosboxVersion dbversion) {
		return profiles_.stream().filter(p -> (p.getDosboxVersion().getId() == dbversion.getId()) && ((selected_ == null) || selected_.contains(p.getId()))).toList();
	}

	private List<Profile> selectedProfiles() {
		return profiles_.stream().filter(p -> selected_.contains(p.getId())).toList();
	}

	/** the number of profiles of each DOSBox version, in the report */
	private void showOverview() {
		StringBuilder sb = new StringBuilder("== ").append(text_.get("dialog.switchdosbox.overview.title", new Object[] {profiles_.size()})).append(Text.DELIMITER);
		for (DosboxVersion v: dbversions_) {
			long n = profiles_.stream().filter(p -> p.getDosboxVersion().getId() == v.getId()).count();
			sb.append(String.format("   %5d  %s", n, v.getTitle())).append(Text.DELIMITER);
		}
		report_.setText(sb.toString());
		colorReport();
	}

	private void updateCount() {
		if ((from_.getSelectionIndex() < 0) || (to_.getSelectionIndex() < 0))
			return;
		if ((selected_ != null) && (selected_.size() == 1) && !selectedProfiles().isEmpty())
			count_.setText(text_.get("dialog.switchdosbox.single", new Object[] {selectedProfiles().get(0).getTitle()}));
		else if (selected_ != null) {
			// which DOSBox versions the chosen profiles use, and how many of them are switched (not those on the new one already)
			DosboxVersion dst = dbversions_.get(to_.getSelectionIndex());
			java.util.Map<String, Long> byVersion = new java.util.LinkedHashMap<>();
			selectedProfiles().forEach(p -> byVersion.merge(p.getDosboxVersion().getTitle(), 1L, Long::sum));
			String parts = byVersion.entrySet().stream().map(e -> e.getValue() + " \u00d7 " + e.getKey()).collect(java.util.stream.Collectors.joining(", "));
			long toSwitch = selectedProfiles().stream().filter(p -> (writtenFor() != null) || (p.getDosboxVersion().getId() != dst.getId())).count();
			count_.setText(text_.get("dialog.switchdosbox.selected2", new Object[] {selectedProfiles().size(), parts, toSwitch}));
		}
		else
			count_.setText(text_.get("dialog.switchdosbox.count2", new Object[] {profilesOf(dbversions_.get(from_.getSelectionIndex())).size(), profiles_.size()}));
		if (!canRun())
			count_.setText(count_.getText() + "  " + text_.get("dialog.switchdosbox.choosetarget"));
		count_.getParent().layout();
		updateButtons();
	}

	/** alpha 49: whether there is anything to switch: another DOSBox version chosen, or profiles written for another one */
	private boolean canRun() {
		if ((from_.getSelectionIndex() < 0) || (to_.getSelectionIndex() < 0))
			return false;
		if (writtenFor() != null)
			return true;
		DosboxVersion dst = dbversions_.get(to_.getSelectionIndex());
		return selected_ == null ? from_.getSelectionIndex() != to_.getSelectionIndex(): selectedProfiles().stream().anyMatch(p -> p.getDosboxVersion().getId() != dst.getId());
	}

	private void updateButtons() {
		boolean go = canRun();
		for (org.eclipse.swt.widgets.Button b: new org.eclipse.swt.widgets.Button[] {previewButton_, okButton_})
			if ((b != null) && !b.isDisposed())
				b.setEnabled(go);
		if ((compareButton_ != null) && !compareButton_.isDisposed())
			compareButton_.setEnabled((from_.getSelectionIndex() >= 0) && (from_.getSelectionIndex() != to_.getSelectionIndex()));
	}

	private void run(boolean preview) {
		if ((from_.getSelectionIndex() < 0) || (to_.getSelectionIndex() < 0))
			return;
		DosboxVersion src = dbversions_.get(from_.getSelectionIndex());
		DosboxVersion dst = dbversions_.get(to_.getSelectionIndex());
		List<Profile> profiles;
		String srcTitle;
		Generation writtenFor = writtenFor();
		if (selected_ == null) {
			if ((src.getId() == dst.getId()) && (writtenFor == null)) {
				Mess_.on(shell_).key("dialog.switchdosbox.error.same").warning();
				return;
			}
			profiles = profilesOf(src);
			srcTitle = src.getTitle();
			if (profiles.isEmpty()) {
				Mess_.on(shell_).key("dialog.switchdosbox.error.noprofiles").warning();
				return;
			}
		} else {
			// the chosen profiles, which may use different DOSBox versions
			profiles = selectedProfiles().stream().filter(p -> (writtenFor != null) || (p.getDosboxVersion().getId() != dst.getId())).toList();
			srcTitle = profiles.stream().map(p -> p.getDosboxVersion().getTitle()).distinct().collect(java.util.stream.Collectors.joining(", "));
			if (profiles.isEmpty()) {
				Mess_.on(shell_).key("dialog.switchdosbox.error.same").warning();
				return;
			}
		}
		profiles = new ArrayList<>(profiles);
		profiles.sort(byId() ? java.util.Comparator.comparingInt(Profile::getId): java.util.Comparator.comparing(Profile::getTitle, String.CASE_INSENSITIVE_ORDER));
		if (writtenFor != null) // converted from the version they were written for
			srcTitle += " (" + text_.get("dialog.switchdosbox.writtenfor.short", new Object[] {generationTitle(writtenFor)}) + ")";
		String what = src.getId() + ">" + dst.getId() + ":" + writtenFor;
		// alpha 48: profiles of more than one DOSBox version at once: which ones, how many of each, to be confirmed first
		if (!preview) {
			java.util.Map<String, Long> sources = new java.util.LinkedHashMap<>();
			profiles.forEach(p -> sources.merge(p.getDosboxVersion().getTitle(), 1L, Long::sum));
			if (sources.size() > 1) {
				StringBuilder list = new StringBuilder();
				sources.forEach((t, n) -> list.append(String.format("%5d \u00d7 %s", n, t)).append('\n'));
				if (!Mess_.on(shell_).key("dialog.switchdosbox.confirmmulti", new Object[] {sources.size(), list.toString().stripTrailing(), dst.getTitle(), profiles.size()}).confirmUnwrapped())
					return;
			}
		}
		// two questions: without a look at the preview first, then the switch itself
		if (!preview && !what.equals(previewed_) && !Mess_.on(shell_).key("dialog.switchdosbox.nopreview2", new Object[] {profiles.size()}).confirm())
			return;
		if (!preview && !Mess_.on(shell_).key("dialog.switchdosbox.confirm2", new Object[] {profiles.size(), srcTitle, dst.getTitle()}).confirm())
			return;
		if (preview)
			previewed_ = what;
		lastWasPreview_ = preview;
		outdatedShown_ = false;
		// files in Windows-1252 (umlauts) where the system uses UTF-8: kept so, or converted, as the user wants
		org.dbgl.gui.controls.CharsetQuestion.Answer charsetAnswer = preview ? org.dbgl.gui.controls.CharsetQuestion.Answer.KEEP: org.dbgl.gui.controls.CharsetQuestion.ask(shell_, profiles, p -> dst);
		if (charsetAnswer == org.dbgl.gui.controls.CharsetQuestion.Answer.CANCEL)
			return;

		if (!preview)
			org.dbgl.util.BulkUndo.remember(text_.get("dialog.bulkundo.switch", new Object[] {dst.getTitle()}), profiles);
		shell_.setCursor(shell_.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
		if (preview) {
			choices_.removeAll();
		}
		StringBuilder report = new StringBuilder();
		report.append(text_.get(preview ? "dialog.switchdosbox.report.preview": "dialog.switchdosbox.report.done", new Object[] {srcTitle, dst.getTitle()})).append(Text.DELIMITER).append(Text.DELIMITER);
		if (selected_ != null) { // chosen profiles that use the new DOSBox version already: not switched, told
			List<Profile> already = selectedProfiles().stream().filter(p -> (writtenFor == null) && (p.getDosboxVersion().getId() == dst.getId())).toList();
			if (!already.isEmpty()) {
				report.append("= ").append(text_.get("dialog.switchdosbox.report.notswitched", new Object[] {already.size(), dst.getTitle()})).append(Text.DELIMITER);
				already.forEach(p -> report.append("=    ").append(p.getTitle()).append(" (").append(p.getId()).append(')').append(Text.DELIMITER));
				report.append(Text.DELIMITER);
			}
		}
		int changed = 0, failed = 0;
		// alpha 49: what the new DOSBox version says otherwise than the old one, once per old version: the profiles take it
		// from there unless they have a value of their own
		java.util.Map<Integer, List<SwitchChoices.Choice>> inheritedBy = new java.util.HashMap<>();
		try {
			for (Profile prof: profiles) {
				DosboxVersion orig = prof.getDosboxVersion();
				List<SwitchChoices.Choice> inherited = List.of();
				if ((writtenFor == null) && (orig != null)) {
					if (!inheritedBy.containsKey(orig.getId())) {
						List<SwitchChoices.Choice> found = new ArrayList<>();
						try {
							for (DosboxVersion v: new DosboxVersion[] {orig, dst})
								if (v.getConfiguration().getSectionNames().length == 0)
									v.resetAndLoadConfiguration();
							found = SwitchChoices.inherited(orig, dst);
						} catch (IOException e) {
							// not readable: told for the profile below
						}
						inheritedBy.put(orig.getId(), found);
						if (!found.isEmpty()) {
							report.append("= ").append(text_.get("dialog.switchdosbox.report.inherited", new Object[] {dst.getTitle(), orig.getTitle()})).append(Text.DELIMITER);
							List<String> lines = org.dbgl.util.ProfileReports.aligned(found.stream().map(c -> new String[] {where(c), c.oldText(), c.newText()}).toList());
							int widest = lines.stream().mapToInt(String::length).max().orElse(0);
							for (int n = 0; n < found.size(); n++)
								report.append("=    ").append(keeps(found.get(n)) ? StringUtils.rightPad(lines.get(n), widest) + "   [" + text_.get("dialog.switchdosbox.report.kept") + "]": lines.get(n))
										.append(Text.DELIMITER);
							report.append(Text.DELIMITER);
							if (preview)
								addInherited(orig, found);
						}
					}
					inherited = inheritedBy.get(orig.getId());
				}
				report.append("== ").append(prof.getTitle()).append(" (").append(prof.getId()).append(')').append(Text.DELIMITER);
				try {
					// alpha 49: what the file of the profile says itself (a value the same as in the old DOSBox version is not among
					// the settings DBGL holds, yet its line goes from the file)
					List<String[]> goneInFile = org.dbgl.util.ProfileChecks.gone0831InFile(prof.getConfigurationCanonicalFile(), dst);
					String warnings = prof.resetAndLoadConfiguration();
					String before = prof.getConfiguration().toString(null);
					String autoexecRead = prof.getConfiguration().getAutoexec().toString(false, null);
					SwitchChoices.Snapshot snapshot = SwitchChoices.before(prof);
					Generation fromGen = writtenFor != null ? writtenFor: prof.getGeneration();
					warnings += writtenFor != null ? prof.alterToDosboxVersionGeneration(writtenFor, dst): prof.switchToDosboxVersion(dst);
					org.dbgl.gui.controls.CharsetQuestion.applyTo(prof, charsetAnswer); // in the charset the new DOSBox version reads
					// alpha 48: the [autoexec] as written in the file, when the switch changes nothing in it (before, it was written
					// anew in DBGL's order: "win" before "cd windows", empty lines gone, "keyb" as "keyb.com")
					boolean autoexecSame = autoexecRead.equals(prof.getConfiguration().getAutoexec().toString(false, null));
					List<SwitchChoices.Choice> choices = SwitchChoices.after(snapshot, prof);
					// lost when switched away from this kind of DOSBox before: offered again
					java.util.Properties memory = org.dbgl.util.SwitchMemory.load(prof.getId());
					choices.addAll(org.dbgl.util.SwitchMemory.restoresFrom(memory, prof));
					// alpha 49: to DOSBox Staging 0.83.1: "auto" for keyboard and country is no value there any more
					List<String[]> gone = org.dbgl.util.ProfileChecks.removeGone0831(prof);
					// test 17: a value written in the file, the same as in the old DOSBox version and another one in the new: it stays
					List<String[]> written = org.dbgl.util.ProfileChecks.keepWrittenValues(prof.getConfigurationCanonicalFile(), orig, prof);
					// alpha 49: what the profile only takes from the new DOSBox version (not in its file before or after)
					for (SwitchChoices.Choice c: SwitchChoices.applyInherited(prof, inherited, this::keeps))
						written.add(new String[] {where(c), c.oldText(), c.newText()});
					// (a value written in its file that was put back above is its own again: not inherited)
					List<SwitchChoices.Choice> takes = showInherited() ? new ArrayList<>(SwitchChoices.inheritedBy(before, inherited, this::keeps)): new ArrayList<>();
					takes.removeIf(c -> prof.getConfiguration().hasValue(c.section(), c.item()));
					String afterSwitch = prof.getConfiguration().toString(null);
					List<SwitchChoices.Choice> kept = SwitchChoices.apply(prof, choices, this::keeps, markers_.getSelection(), preview);
					boolean asWritten = autoexecSame && choices.stream().filter(c -> c.kind() == SwitchChoices.Kind.AUTOEXEC).allMatch(this::keeps);
					if (asWritten)
						prof.getConfiguration().keepAutoexecAsWritten(); // all its lines kept: as it is
					List<String> diff = new ArrayList<>(grouped(before, afterSwitch, fullFile())); // each section, below it its values
					for (SwitchChoices.Choice c: choices)
						if ((c.kind() == SwitchChoices.Kind.AUTOEXEC) && !kept.contains(c))
							diff.add("- [autoexec] " + c.oldText()); // not ticked: removed
					for (SwitchChoices.Choice c: kept)
						if (asWritten && (c.kind() == SwitchChoices.Kind.AUTOEXEC))
							continue; // not each line: the [autoexec] stays as a whole, said below
						else if (c.kind() == SwitchChoices.Kind.RESTORE)
							diff.add("+ " + text_.get("dialog.switchdosbox.report.restored") + ": " + where(c) + "=" + c.oldText());
						else
							diff.add("= " + text_.get("dialog.switchdosbox.report.kept") + ": " + where(c) + " " + c.oldText());
					for (String[] w: written)
						diff.add("= " + text_.get("dialog.switchdosbox.report.kept") + ": " + w[0] + "=" + w[1] + "  (" + dst.getTitle() + ": " + w[2] + ")");
					for (String[] g: goneInFile)
						if (gone.stream().noneMatch(x -> x[0].equalsIgnoreCase(g[0]))) {
							diff.add("- " + g[0] + "=" + g[1]);
							gone.add(g);
						}
					if (preview)
						addChoices(prof, choices);
					if (diff.isEmpty() && takes.isEmpty())
						report.append("   ").append(text_.get("dialog.switchdosbox.report.nochanges")).append(Text.DELIMITER);
					else if (!diff.isEmpty())
						changed++;
					diff.forEach(l -> report.append("   ").append(l).append(Text.DELIMITER));
					// not counted as a change: the file of the profile stays as it is
					SwitchChoices.inheritedLines(takes, c -> text_.get("dialog.switchdosbox.report.inherits", new Object[] {dst.getTitle(), c.oldText()}))
						.forEach(l -> report.append("   ").append(l).append(Text.DELIMITER));
					for (String[] g: gone)
						report.append("   ! ").append(text_.get("dialog.checkprofiles.autogone0831", new Object[] {g[0], g[1]})).append(Text.DELIMITER);
					if (asWritten && choices.stream().anyMatch(c -> c.kind() == SwitchChoices.Kind.AUTOEXEC))
						report.append("   = ").append(text_.get("dialog.switchdosbox.report.autoexecaswritten")).append(Text.DELIMITER);
					if (StringUtils.isNotBlank(warnings))
						report.append("   ! ").append(warnings.trim().replace("\n", Text.DELIMITER + "   ! ")).append(Text.DELIMITER);
					if (!preview) {
						new ProfileRepository().update(prof);
						// what it lost now, for switching back
						org.dbgl.util.SwitchMemory.rememberInto(memory, prof, snapshot, fromGen, choices);
						org.dbgl.util.SwitchMemory.save(prof.getId(), memory);
					}
					else
						prof.setDosboxVersion(orig); // in memory only, nothing saved
				} catch (IOException | SQLException e) {
					failed++;
					report.append("   ! ").append(e.getMessage()).append(Text.DELIMITER);
				}
				report.append(Text.DELIMITER);
			}
		} finally {
			shell_.setCursor(null);
		}
		report.append(text_.get("dialog.switchdosbox.report.summary", new Object[] {profiles.size(), changed, failed}));
		report_.setText(report.toString());
		colorReport();
		reportShown();
		if (preview) {
			for (TableColumn col: choices_.getColumns())
				col.pack();
		} else {
			choices_.removeAll();
			keep_.clear();
			result_ = true;
			try {
				profiles_ = new ProfileRepository().list(StringUtils.EMPTY, StringUtils.EMPTY, dbversions_);
			} catch (SQLException e) {
				Mess_.on(shell_).exception(e).warning();
			}
			if (selected_ != null) // the profiles now use the new DOSBox version
				from_.select(to_.getSelectionIndex());
			updateCount();
			// alpha 49: said in a window of its own that it is done, and how it is taken back
			Mess_.on(shell_).key("dialog.switchdosbox.done", new Object[] {dst.getTitle(), text_.get("dialog.switchdosbox.report.summary", new Object[] {profiles.size(), changed, failed})})
					.display();
		}
	}

	/** the report in colors, as the preview of "Check profiles": red gone, green new, gray kept, bold a profile, a warning orange */
	private void colorReport() {
		org.eclipse.swt.widgets.Display d = shell_.getDisplay();
		List<org.eclipse.swt.custom.StyleRange> styles = new ArrayList<>();
		String text = report_.getText();
		int offset = 0;
		for (String line: text.split("\n", -1)) {
			String t = line.replace("\r", "").trim();
			org.eclipse.swt.graphics.Color c = t.startsWith("- ") ? org.dbgl.gui.controls.DarkTheme.textColor(d, SWT.COLOR_DARK_RED): t.startsWith("+ ") ? org.dbgl.gui.controls.DarkTheme.textColor(d, SWT.COLOR_DARK_GREEN)
				: t.startsWith("= ") ? org.dbgl.gui.controls.DarkTheme.textColor(d, SWT.COLOR_DARK_GRAY): t.startsWith("! ") ? org.dbgl.gui.controls.DarkTheme.textColor(d, SWT.COLOR_DARK_YELLOW)
				: t.startsWith("> ") ? org.dbgl.gui.controls.DarkTheme.textColor(d, SWT.COLOR_DARK_CYAN)
				: (t.startsWith("[") && t.endsWith("]")) ? org.dbgl.gui.controls.DarkTheme.textColor(d, SWT.COLOR_DARK_GRAY): null;
			if (t.startsWith("== ")) {
				org.eclipse.swt.custom.StyleRange r = new org.eclipse.swt.custom.StyleRange(offset, line.length(), null, null);
				r.font = bold_;
				styles.add(r);
			} else if (c != null) {
				styles.add(new org.eclipse.swt.custom.StyleRange(offset, line.length(), c, null));
			}
			offset += line.length() + 1;
		}
		report_.setStyleRanges(styles.toArray(new org.eclipse.swt.custom.StyleRange[0]));
	}

	/** as the preview of "Check profiles": the section in a line of its own ("[sdl]"), below it its values ("- output=..") */
	static List<String> grouped(String before, String after) {
		return grouped(before, after, false);
	}

	/** @param full also the lines that stay as they are (the whole configuration, its changes marked) */
	static List<String> grouped(String before, String after, boolean full) {
		List<String> result = new ArrayList<>();
		boolean changes = false;
		for (org.dbgl.util.ProfileFindings.DiffLine d: org.dbgl.util.ProfileFindings.diff(before, after, null, full)) {
			boolean section = d.text().startsWith("[") && d.text().endsWith("]");
			if (section && (d.kind() == ' '))
				result.add(d.text());
			else if (d.kind() == '-' || d.kind() == '+') {
				result.add(d.kind() + " " + d.text()); // the values below their section, not indented
				changes = true;
			} else if (full && StringUtils.isNotBlank(d.text()))
				result.add("  " + d.text());
		}
		return full && !changes ? new ArrayList<>(): result; // nothing changed: said so, as without the whole file
	}

	/** lines removed (-) and added (+), ignoring order */
	public static List<String> diff(String before, String after) {
		Set<String> b = new LinkedHashSet<>(Arrays.asList(before.split("\\R")));
		Set<String> a = new LinkedHashSet<>(Arrays.asList(after.split("\\R")));
		List<String> result = new ArrayList<>();
		String section = null;
		for (String l: before.split("\\R")) {
			if (l.startsWith("["))
				section = l;
			else if (StringUtils.isNotBlank(l) && !a.contains(l))
				result.add("- " + (section != null ? section + " ": "") + l);
		}
		section = null;
		for (String l: after.split("\\R")) {
			if (l.startsWith("["))
				section = l;
			else if (StringUtils.isNotBlank(l) && !b.contains(l))
				result.add("+ " + (section != null ? section + " ": "") + l);
		}
		return result;
	}

	/** alpha 49: as text, as a web page in the colors of this window, or as PDF; the file name with date and time */
	private void saveReport() {
		if (StringUtils.isBlank(report_.getText()))
			return;
		org.dbgl.gui.controls.ReportSave.menu(shell_, "dbgl-switch-report", shell_.getText(), report_::getText, () -> org.dbgl.gui.controls.ReportSave.html(report_, shell_.getText()));
	}
}
