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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.util.PathRelocation;
import org.dbgl.util.ProfileFindings;
import org.dbgl.util.ProfileFindings.Finding;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;


/**
 * Checks the profiles and their DOSBox versions. What is found is shown as a tree: the games, under each its problems. A
 * problem DBGL can solve has a box to choose it; the next step shows, for each game, the lines of its configuration file
 * before and after, and the chosen solutions are applied there. Problems (or games) can be hidden until the next check, or
 * for good: just this one, or all of its kind for all games.
 */
public class CheckProfilesDialog extends SizeControlledButtonDialog<Boolean> {

	private List<DosboxVersion> dbversions_;
	private List<Profile> profiles_;
	/** the ids of the profiles chosen in the main window, or null for all */
	private final Set<Integer> selected_;

	private Tree tree_;
	private Text details_;
	private Label summary_;
	/** while checking: how far it got (shown at once, the dialog open before the check starts) */
	private org.eclipse.swt.widgets.ProgressBar progress_;
	private boolean checking_;
	/** the games in the list by title (0) or by ID (1); kept while DBGL runs */
	private static int sortBy_;
	private org.eclipse.swt.widgets.Combo sort_;
	private Button onlyFixable_, showHidden_, undismiss_;

	/** what the last check found (also what is hidden for good: left out when shown) */
	private ProfileFindings.Result result_found_;
	/** the solutions chosen, by the key of their finding (kept while the tree is built again, also after a game is checked again) */
	private final Set<String> chosen_ = new HashSet<>();
	/** the solvable findings known, by key: a new one is chosen at first */
	private final Set<String> known_ = new HashSet<>();
	/** hidden until the next check: findings (their key) and games ("p" and the id of the profile) */
	private final Set<String> hiddenNow_ = new HashSet<>();
	/** configuration files opened for editing: checked again when they are saved (file, last modified and size) */
	private final Map<File, String> watched_ = new LinkedHashMap<>();
	/** the boxes, drawn (a box DBGL cannot tick is gray) */
	private org.eclipse.swt.graphics.Image boxOn_, boxOff_, boxSome_, boxNone_;
	private org.eclipse.swt.graphics.Color gameBackground_;
	private org.eclipse.swt.graphics.Font gameFont_;
	/** games the user closed in the tree (built again with them closed) */
	private final Set<Integer> collapsed_ = new HashSet<>();
	/** the report as shown (to save it; also read by the tests) */
	private String reportText_ = StringUtils.EMPTY;
	private int dismissedCount_, withProblems_;

	public CheckProfilesDialog(Shell parent) {
		this(parent, null);
	}

	/** @param selected only these profiles are checked, or all if null */
	public CheckProfilesDialog(Shell parent, List<Profile> selected) {
		super(parent, "checkprofilesdialog");
		selected_ = selected == null ? null: selected.stream().map(Profile::getId).collect(java.util.stream.Collectors.toSet());
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.checkprofiles.title");
	}

	@Override
	protected boolean prepare() {
		try {
			dbversions_ = new DosboxVersionRepository().listAll();
			profiles_ = new ProfileRepository().list(StringUtils.EMPTY, StringUtils.EMPTY, dbversions_);
			if (selected_ != null)
				profiles_ = profiles_.stream().filter(p -> selected_.contains(p.getId())).toList();
			return true;
		} catch (SQLException e) {
			Mess_.on(getParent()).exception(e).warning();
			return false;
		}
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));

		Composite bar = new Composite(contents_, SWT.NONE);
		bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout barLayout = new GridLayout(7, false);
		barLayout.marginWidth = 0;
		barLayout.marginHeight = 0;
		bar.setLayout(barLayout);
		Button all = new Button(bar, SWT.PUSH);
		all.setText(text_.get("dialog.checkprofiles.allfixable"));
		all.addListener(SWT.Selection, e -> chooseAll(true));
		Button none = new Button(bar, SWT.PUSH);
		none.setText(text_.get("button.none"));
		none.addListener(SWT.Selection, e -> chooseAll(false));
		onlyFixable_ = new Button(bar, SWT.CHECK);
		onlyFixable_.setText(text_.get("dialog.checkprofiles.onlyfixable"));
		onlyFixable_.addListener(SWT.Selection, e -> fillTree());
		showHidden_ = new Button(bar, SWT.PUSH);
		showHidden_.addListener(SWT.Selection, e -> {
			hiddenNow_.clear();
			fillTree();
		});
		undismiss_ = new Button(bar, SWT.PUSH);
		undismiss_.addListener(SWT.Selection, e -> undismiss());
		Label sortLabel = new Label(bar, SWT.NONE);
		sortLabel.setText(text_.get("dialog.checkprofiles.sortby"));
		GridData sortLabelData = new GridData(SWT.END, SWT.CENTER, true, false);
		sortLabel.setLayoutData(sortLabelData);
		sort_ = new org.eclipse.swt.widgets.Combo(bar, SWT.READ_ONLY);
		sort_.setItems(text_.get("dialog.checkprofiles.sortby.title"), text_.get("dialog.checkprofiles.sortby.id"));
		sort_.select(sortBy_);
		sort_.addListener(SWT.Selection, e -> {
			sortBy_ = sort_.getSelectionIndex();
			fillTree();
		});
		summary_ = new Label(contents_, SWT.NONE);
		summary_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		progress_ = new org.eclipse.swt.widgets.ProgressBar(contents_, SWT.HORIZONTAL | SWT.SMOOTH);
		GridData progressData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		progressData.exclude = true;
		progress_.setLayoutData(progressData);
		progress_.setVisible(false);

		tree_ = new Tree(contents_, SWT.MULTI | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		tree_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tree_.setHeaderVisible(true);
		tree_.setLinesVisible(true); // a line between the message and its solution
		createBoxes();
		TreeColumn problem = new TreeColumn(tree_, SWT.LEFT);
		problem.setText(text_.get("dialog.checkprofiles.col.problem"));
		problem.setWidth(560);
		TreeColumn solution = new TreeColumn(tree_, SWT.LEFT);
		solution.setText(text_.get("dialog.checkprofiles.col.solution"));
		solution.setWidth(190);
		tree_.addListener(SWT.Resize, e -> { // the messages get the room the solutions do not need
			int rest = tree_.getClientArea().width - solution.getWidth();
			if (rest > 200)
				problem.setWidth(rest);
		});
		tree_.addListener(SWT.Selection, e -> showDetails());
		// the box: a click on it, or the space key for the selected lines
		tree_.addListener(SWT.MouseDown, e -> {
			if (e.button != 1)
				return;
			TreeItem item = tree_.getItem(new org.eclipse.swt.graphics.Point(e.x, e.y));
			if ((item != null) && item.getImageBounds(0).contains(e.x, e.y))
				toggle(List.of(item));
		});
		tree_.addListener(SWT.KeyDown, e -> {
			if (e.character == ' ') {
				toggle(List.of(tree_.getSelection()));
				e.doit = false;
			}
		});
		tree_.addListener(SWT.Collapse, e -> {
			if (((TreeItem)e.item).getData() instanceof Profile p)
				collapsed_.add(p.getId());
		});
		tree_.addListener(SWT.Expand, e -> {
			if (((TreeItem)e.item).getData() instanceof Profile p)
				collapsed_.remove(p.getId());
		});
		tree_.addListener(SWT.MenuDetect, e -> showMenu());

		details_ = new Text(contents_, SWT.MULTI | SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL | SWT.BORDER);
		GridData detailsData = new GridData(SWT.FILL, SWT.FILL, true, false);
		detailsData.heightHint = details_.getLineHeight() * 5;
		details_.setLayoutData(detailsData);

		createGoCancelButtons("dialog.checkprofiles.preview", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				preview();
			}
		});
		okButton_.setToolTipText(text_.get("dialog.checkprofiles.preview.tooltip"));
		cancelButton_.setText(text_.get("button.close"));
		Button_.on(otherButtons_).text().key("dialog.checkprofiles.relocate").tooltip("dialog.checkprofiles.relocate.tooltip").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				relocateMissing();
			}
		}).ctrl();
		Button_.on(otherButtons_).text().key("dialog.checkprofiles.todosboxx").tooltip("dialog.checkprofiles.todosboxx.tooltip").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				switchToDosboxX();
			}
		}).ctrl();
		Button_.on(otherButtons_).text().key("dialog.switchdosbox.savereport").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				saveReport();
			}
		}).ctrl();
		// all buttons in one row, 'save report' at the far right
		((GridLayout)otherButtons_.getLayout()).numColumns = otherButtons_.getChildren().length;
		shell_.addDisposeListener(e -> {
			for (org.eclipse.swt.graphics.Resource r: new org.eclipse.swt.graphics.Resource[] {boxOn_, boxOff_, boxSome_, boxNone_, gameBackground_, gameFont_})
				if (r != null)
					r.dispose();
		});
		// the window first, then the check with its progress (with many profiles it takes a while: before, nothing was to be seen)
		shell_.getDisplay().asyncExec(() -> {
			if (!shell_.isDisposed()) {
				check();
				watch();
			}
		});
	}

	/** the boxes as images: ticked, empty, some ticked (a game), gray (nothing DBGL can solve); and how a game line stands out */
	private void createBoxes() {
		org.eclipse.swt.widgets.Display d = shell_.getDisplay();
		int size = Math.max(13, tree_.getItemHeight() - 6);
		org.eclipse.swt.graphics.RGB bg = tree_.getBackground().getRGB(), sel = d.getSystemColor(SWT.COLOR_LIST_SELECTION).getRGB();
		org.eclipse.swt.graphics.RGB fg = tree_.getForeground().getRGB();
		boxOff_ = box(d, size, bg, mix(fg, bg, 0.45), null, false);
		boxOn_ = box(d, size, sel, sel, d.getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT).getRGB(), true);
		boxSome_ = box(d, size, bg, mix(fg, bg, 0.45), sel, false);
		boxNone_ = box(d, size, mix(fg, bg, 0.90), mix(fg, bg, 0.75), null, false);
		gameBackground_ = new org.eclipse.swt.graphics.Color(d, mix(sel, bg, 0.82));
		org.eclipse.swt.graphics.FontData[] fd = tree_.getFont().getFontData();
		for (org.eclipse.swt.graphics.FontData f: fd)
			f.setStyle(SWT.BOLD);
		gameFont_ = new org.eclipse.swt.graphics.Font(d, fd);
	}

	/** a's share of a, the rest b */
	private static org.eclipse.swt.graphics.RGB mix(org.eclipse.swt.graphics.RGB a, org.eclipse.swt.graphics.RGB b, double ofB) {
		return new org.eclipse.swt.graphics.RGB((int)(a.red * (1 - ofB) + b.red * ofB), (int)(a.green * (1 - ofB) + b.green * ofB), (int)(a.blue * (1 - ofB) + b.blue * ofB));
	}

	private org.eclipse.swt.graphics.Image box(org.eclipse.swt.widgets.Display d, int size, org.eclipse.swt.graphics.RGB fill, org.eclipse.swt.graphics.RGB border,
			org.eclipse.swt.graphics.RGB mark, boolean tick) {
		org.eclipse.swt.graphics.Image img = new org.eclipse.swt.graphics.Image(d, size + 2, size);
		org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(img);
		org.eclipse.swt.graphics.Color cb = new org.eclipse.swt.graphics.Color(d, tree_.getBackground().getRGB()), cf = new org.eclipse.swt.graphics.Color(d, fill),
				cr = new org.eclipse.swt.graphics.Color(d, border), cm = mark == null ? null: new org.eclipse.swt.graphics.Color(d, mark);
		gc.setAntialias(SWT.ON);
		gc.setBackground(cb);
		gc.fillRectangle(0, 0, size + 2, size);
		gc.setBackground(cf);
		gc.fillRoundRectangle(1, 0, size - 1, size - 1, 3, 3);
		gc.setForeground(cr);
		gc.drawRoundRectangle(1, 0, size - 2, size - 2, 3, 3);
		if (cm != null) {
			if (tick) {
				gc.setForeground(cm);
				gc.setLineWidth(Math.max(2, size / 7));
				gc.drawPolyline(new int[] {1 + size / 4, size / 2, 1 + size * 5 / 12, size * 3 / 4 - 1, 1 + size * 3 / 4, size / 4});
			} else {
				gc.setBackground(cm);
				gc.fillRectangle(1 + size / 4, size / 4, size / 2, size / 2);
			}
		}
		gc.dispose();
		cb.dispose();
		cf.dispose();
		cr.dispose();
		if (cm != null)
			cm.dispose();
		return img;
	}

	/** checks all again; what was chosen or hidden stays so where it is still found, a new solvable finding is chosen */
	private void check() {
		if (checking_)
			return;
		checking_ = true;
		org.eclipse.swt.widgets.Display display = shell_.getDisplay();
		shell_.setCursor(display.getSystemCursor(SWT.CURSOR_WAIT));
		// nothing to click meanwhile but "Close" (the events are handled to show the progress)
		List<org.eclipse.swt.widgets.Control> disabled = new ArrayList<>();
		for (org.eclipse.swt.widgets.Control c: new org.eclipse.swt.widgets.Control[] {tree_, okButton_, otherButtons_, onlyFixable_.getParent(), details_})
			if (c.getEnabled()) {
				c.setEnabled(false);
				disabled.add(c);
			}
		int total = profiles_.size();
		progress_.setMaximum(Math.max(total, 1));
		progress_.setSelection(0);
		((GridData)progress_.getLayoutData()).exclude = false;
		progress_.setVisible(true);
		summary_.setText(text_.get("dialog.checkprofiles.checking", new Object[] {0, total}));
		contents_.layout(true);
		long[] shown = {0};
		try {
			result_found_ = ProfileFindings.collect(profiles_, profiles_, dbversions_, text_, done -> {
				if (shell_.isDisposed())
					throw new java.util.concurrent.CancellationException(); // closed meanwhile
				long now = System.currentTimeMillis();
				if ((now - shown[0] < 80) && (done < total))
					return;
				shown[0] = now;
				progress_.setSelection(done);
				summary_.setText(text_.get("dialog.checkprofiles.checking", new Object[] {done, total}));
				while (!shell_.isDisposed() && display.readAndDispatch()) {
					// the progress painted, "Close" handled
				}
				if (shell_.isDisposed())
					throw new java.util.concurrent.CancellationException();
			});
		} catch (java.util.concurrent.CancellationException e) {
			return;
		} finally {
			checking_ = false;
			if (!shell_.isDisposed()) {
				shell_.setCursor(null);
				disabled.forEach(c -> c.setEnabled(true));
				progress_.setVisible(false);
				((GridData)progress_.getLayoutData()).exclude = true;
				contents_.layout(true);
			}
		}
		chooseNew();
		fillTree();
	}

	/** a solvable finding not seen before is chosen at first */
	private void chooseNew() {
		Set<String> keys = ProfileFindings.dismissedKeys(), kinds = ProfileFindings.dismissedKinds();
		for (List<Finding> list: result_found_.profiles().values())
			for (Finding f: list)
				if (f.fixable() && !isDismissed(f, keys, kinds) && known_.add(f.dismissKey()))
					chosen_.add(f.dismissKey());
	}

	/** the configuration files opened for editing: a game whose file was saved is checked again (its solved messages go) */
	private void watch() {
		if (shell_.isDisposed())
			return;
		if (checking_) { // looked at again after the check
			shell_.getDisplay().timerExec(1000, this::watch);
			return;
		}
		List<File> changed = new ArrayList<>();
		for (Map.Entry<File, String> e: watched_.entrySet())
			if (!stamp(e.getKey()).equals(e.getValue())) {
				changed.add(e.getKey());
				e.setValue(stamp(e.getKey()));
			}
		if (!changed.isEmpty()) {
			boolean ofVersion = dbversions_.stream().anyMatch(db -> changed.contains(db.getConfigurationCanonicalFile()));
			if (ofVersion) { // the configuration of a DOSBox version: all of its profiles, and itself
				check();
			} else {
				for (Profile prof: profiles_)
					if (changed.contains(prof.getConfigurationCanonicalFile()))
						recheck(prof);
				chooseNew();
				fillTree();
			}
			org.dbgl.util.ProfileWarnings.forgetAll();
			result_ = Boolean.TRUE;
		}
		shell_.getDisplay().timerExec(1000, this::watch);
	}

	private static String stamp(File f) {
		return f.lastModified() + "/" + f.length();
	}

	/** one game checked again (e.g. after its configuration file was changed by hand) */
	private void recheck(Profile prof) {
		ProfileFindings.Result one = ProfileFindings.collect(List.of(prof), profiles_, dbversions_, text_);
		replace(result_found_.profiles(), prof, one.profiles().get(prof));
		replace(result_found_.missing(), prof, one.missing().get(prof));
		replace(result_found_.missingLinks(), prof, one.missingLinks().get(prof));
		replace(result_found_.dosboxX(), prof, one.dosboxX().get(prof));
	}

	private static <V> void replace(Map<Profile, V> map, Profile prof, V value) {
		if (value == null)
			map.remove(prof);
		else
			map.put(prof, value); // where it was in the list (the same profile), or at the end
	}

	private static boolean isDismissed(Finding f, Set<String> keys, Set<String> kinds) {
		return ProfileFindings.isDismissed(f.profile(), f.version(), f.kind(), f.args(), keys, kinds);
	}

	private static String key(Profile prof) {
		return "p" + prof.getId();
	}

	/** the tree built (again) from what was found, and the report as text */
	private void fillTree() {
		Set<String> keys = ProfileFindings.dismissedKeys(), kinds = ProfileFindings.dismissedKinds();
		tree_.setRedraw(false);
		tree_.removeAll();
		StringBuilder report = new StringBuilder();
		dismissedCount_ = 0;
		withProblems_ = 0;
		int hidden = 0;

		List<Finding> versions = new ArrayList<>();
		for (Finding f: result_found_.versions()) {
			if (isDismissed(f, keys, kinds))
				dismissedCount_++;
			else if (hiddenNow_.contains(f.dismissKey()))
				hidden++;
			else if (!onlyFixable_.getSelection())
				versions.add(f);
		}
		if (!versions.isEmpty()) {
			TreeItem node = new TreeItem(tree_, SWT.NONE);
			node.setText(0, text_.get("dialog.checkprofiles.versions"));
			node.setData(versions);
			markGame(node);
			for (Finding f: versions) {
				TreeItem item = new TreeItem(node, SWT.NONE);
				item.setText(0, oneLine((f.hint() ? "i  ": "!! ") + f.text()));
				item.setData(f);
				report.append(f.hint() ? "i  ": "!! ").append(f.text()).append(Text.DELIMITER);
			}
			node.setExpanded(true);
			report.append(Text.DELIMITER);
		}

		List<Map.Entry<Profile, List<Finding>>> games = new ArrayList<>(result_found_.profiles().entrySet());
		games.sort(sortBy_ == 1 ? java.util.Comparator.comparingInt(e -> e.getKey().getId())
				: java.util.Comparator.<Map.Entry<Profile, List<Finding>>, String>comparing(e -> StringUtils.defaultString(e.getKey().getTitle()), String.CASE_INSENSITIVE_ORDER)
						.thenComparingInt(e -> e.getKey().getId()));
		for (Map.Entry<Profile, List<Finding>> e: games) {
			Profile prof = e.getKey();
			List<Finding> list = new ArrayList<>();
			for (Finding f: e.getValue()) {
				if (isDismissed(f, keys, kinds))
					dismissedCount_++;
				else if (hiddenNow_.contains(f.dismissKey()) || hiddenNow_.contains(key(prof)))
					hidden++;
				else if (!onlyFixable_.getSelection() || f.fixable())
					list.add(f);
			}
			if (list.isEmpty())
				continue;
			withProblems_++;
			TreeItem node = new TreeItem(tree_, SWT.NONE);
			node.setData(prof);
			markGame(node);
			String title = prof.getTitle() + " (ID " + prof.getId() + ", " + (prof.getDosboxVersion() == null ? "-": prof.getDosboxVersion().getTitle()) + ")";
			node.setText(0, title);
			long fixable = list.stream().filter(Finding::fixable).count();
			node.setText(1, text_.get("dialog.checkprofiles.gamecount", new Object[] {list.size(), fixable}));
			report.append("== ").append(title).append(Text.DELIMITER);
			for (Finding f: list) {
				TreeItem item = new TreeItem(node, SWT.NONE);
				item.setText(0, oneLine(f.text()));
				if (f.fixable())
					item.setText(1, text_.get("dialog.checkprofiles.fix." + f.solution()));
				item.setData(f);
				report.append("   ").append(f.text()).append(Text.DELIMITER);
			}
			updateBoxes(node);
			node.setExpanded(!collapsed_.contains(prof.getId()));
			report.append(Text.DELIMITER);
		}
		tree_.setRedraw(true);

		if (withProblems_ == 0)
			report.append(text_.get("dialog.checkprofiles.ok")).append(Text.DELIMITER).append(Text.DELIMITER);
		report.append(text_.get("dialog.checkprofiles.summary", new Object[] {profiles_.size(), withProblems_}));
		if (!result_found_.dosboxX().isEmpty())
			report.append(Text.DELIMITER).append(text_.get("dialog.checkprofiles.dosboxx.summary", new Object[] {result_found_.dosboxX().size()}));
		reportText_ = report.toString();

		showHidden_.setText(text_.get("dialog.checkprofiles.showhidden", new Object[] {hidden}));
		showHidden_.setEnabled(hidden > 0);
		undismiss_.setText(text_.get("dialog.checkprofiles.undismiss", new Object[] {dismissedCount_}));
		undismiss_.setEnabled(dismissedCount_ > 0);
		updateSummary();
		showHidden_.getParent().layout(true);
		details_.setText(withProblems_ == 0 && versions.isEmpty() ? text_.get("dialog.checkprofiles.ok"): StringUtils.EMPTY);
	}

	/** a line of a game (or of the DOSBox versions) stands out, over both columns */
	private void markGame(TreeItem node) {
		node.setBackground(gameBackground_);
		node.setFont(gameFont_);
	}

	private static String oneLine(String s) {
		return s.replaceAll("\\s*\\R\\s*", " ");
	}

	private void updateSummary() {
		long chosen = visibleChosen().values().stream().mapToLong(List::size).sum();
		summary_.setText(text_.get("dialog.checkprofiles.summary2", new Object[] {profiles_.size(), withProblems_, chosen}));
		summary_.getParent().layout(true);
	}

	/** the boxes of a game and its messages: a game ticked if all its solvable messages are, partly if some, gray if none can be */
	private void updateBoxes(TreeItem node) {
		int fixable = 0, checked = 0;
		for (TreeItem child: node.getItems())
			if (child.getData() instanceof Finding f) {
				if (f.fixable()) {
					fixable++;
					boolean on = chosen_.contains(f.dismissKey());
					if (on)
						checked++;
					child.setImage(0, on ? boxOn_: boxOff_);
				} else {
					child.setImage(0, boxNone_);
				}
			}
		node.setImage(0, fixable == 0 ? boxNone_: checked == fixable ? boxOn_: checked > 0 ? boxSome_: boxOff_);
	}

	/** the boxes of these lines clicked: a message on/off; a game all on, or all off if all are on already */
	private void toggle(List<TreeItem> items) {
		for (TreeItem item: items) {
			if (item.getData() instanceof Finding f) {
				if (f.fixable())
					setChosen(f, !chosen_.contains(f.dismissKey()));
			} else if (item.getData() instanceof Profile) {
				List<Finding> fixable = java.util.Arrays.stream(item.getItems()).map(TreeItem::getData).filter(d -> (d instanceof Finding f) && f.fixable()).map(d -> (Finding)d).toList();
				boolean allOn = fixable.stream().allMatch(f -> chosen_.contains(f.dismissKey()));
				fixable.forEach(f -> setChosen(f, !allOn));
			}
		}
		refreshBoxes();
	}

	private void refreshBoxes() {
		for (TreeItem node: tree_.getItems())
			if (node.getData() instanceof Profile)
				updateBoxes(node);
		updateSummary();
	}

	private void setChosen(Finding f, boolean on) {
		if (on)
			chosen_.add(f.dismissKey());
		else
			chosen_.remove(f.dismissKey());
	}

	private void chooseAll(boolean on) {
		for (TreeItem node: tree_.getItems())
			for (TreeItem child: node.getItems())
				if ((child.getData() instanceof Finding f) && f.fixable())
					setChosen(f, on);
		refreshBoxes();
	}

	private void showDetails() {
		TreeItem[] sel = tree_.getSelection();
		if (sel.length == 0) {
			details_.setText(StringUtils.EMPTY);
			return;
		}
		Object data = sel[0].getData();
		if (data instanceof Finding f) {
			StringBuilder sb = new StringBuilder(f.text());
			sb.append(Text.DELIMITER).append(Text.DELIMITER);
			if (f.fixable()) {
				sb.append(text_.get("dialog.checkprofiles.details.fix", new Object[] {text_.get("dialog.checkprofiles.fix." + f.solution())}));
				if (f.rewrite())
					sb.append(' ').append(text_.get("dialog.checkprofiles.details.rewrite"));
			} else if (!f.hint()) {
				sb.append(text_.get("dialog.checkprofiles.details.nofix"));
			}
			details_.setText(sb.toString());
		} else if (data instanceof Profile p) {
			long fixable = java.util.Arrays.stream(sel[0].getItems()).filter(i -> (i.getData() instanceof Finding f) && f.fixable()).count();
			details_.setText(text_.get("dialog.checkprofiles.details.game", new Object[] {p.getTitle(), p.getConfigurationCanonicalFile().getPath(), sel[0].getItemCount(), fixable}));
		} else {
			details_.setText(StringUtils.EMPTY);
		}
	}

	/** the findings of the selected lines (a game: all of its findings shown) */
	private List<Finding> selectedFindings() {
		List<Finding> result = new ArrayList<>();
		for (TreeItem item: tree_.getSelection()) {
			if (item.getData() instanceof Finding f) {
				if (!result.contains(f))
					result.add(f);
			} else {
				for (TreeItem child: item.getItems())
					if ((child.getData() instanceof Finding f) && !result.contains(f))
						result.add(f);
			}
		}
		return result;
	}

	/** the configuration file of the selected line: of its game, or of the DOSBox version a message of it is about */
	private File selectedConfFile() {
		TreeItem[] sel = tree_.getSelection();
		if (sel.length != 1)
			return null;
		Object data = sel[0].getData();
		if (data instanceof Profile p)
			return p.getConfigurationCanonicalFile();
		if (data instanceof Finding f)
			return f.profile() != null ? f.profile().getConfigurationCanonicalFile(): f.version() != null ? f.version().getConfigurationCanonicalFile(): null;
		return null;
	}

	private void showMenu() {
		TreeItem[] sel = tree_.getSelection();
		if (sel.length == 0)
			return;
		boolean game = java.util.Arrays.stream(sel).anyMatch(i -> !(i.getData() instanceof Finding));
		List<Finding> findings = selectedFindings();
		Menu menu = new Menu(tree_);
		if (findings.stream().anyMatch(Finding::fixable)) {
			addMenuItem(menu, "dialog.checkprofiles.menu.choose", () -> choose(findings, true));
			addMenuItem(menu, "dialog.checkprofiles.menu.unchoose", () -> choose(findings, false));
			new MenuItem(menu, SWT.SEPARATOR);
		}
		File conf = selectedConfFile();
		if (conf != null) {
			boolean ofVersion = (sel[0].getData() instanceof Finding f) && (f.profile() == null);
			addMenuItem(menu, ofVersion ? "dialog.checkprofiles.menu.editdosboxconf": "dialog.checkprofiles.menu.editconf", () -> {
				watched_.put(conf, stamp(conf)); // when it is saved, its game is checked again
				edit(conf);
			});
			addMenuItem(menu, "dialog.checkprofiles.menu.openfolder", () -> org.dbgl.util.SystemUtils.openDirForViewing(conf.getParentFile(), shell_));
			// a hint or value of one setting (e.g. the MT-32 for Lure of the Temptress): the profile editor opened on it
			String setting = settingOf(findings);
			if (setting != null)
				addMenuItem(menu, "dialog.checkprofiles.menu.showsetting", () -> showSetting(findings.get(0).profile(), setting));
			// the patch files of the GUS not where the game looks: the folder ULTRASND beside DOSBox, if there is one, to copy
			if ((findings.size() == 1) && findings.get(0).kind().equals("gusnopatches") && (findings.get(0).profile() != null)) {
				File ultrasnd = org.dbgl.util.MidiRoms.ultrasnd(findings.get(0).profile().getDosboxVersion()).dir();
				if (ultrasnd != null)
					addMenuItem(menu, "dialog.checkprofiles.menu.showultrasnd", () -> org.dbgl.util.SystemUtils.openDirForViewing(ultrasnd, shell_));
			}
			// the configuration file gone: an earlier version of it brought back (DBGL keeps them in profiles/history)
			if ((findings.size() == 1) && findings.get(0).kind().equals("confmissing") && (findings.get(0).profile() != null)) {
				int id = findings.get(0).profile().getId();
				boolean kept = !org.dbgl.util.ProfileHistory.list(id).isEmpty() || (org.dbgl.util.ProfileHistory.original(id) != null);
				addMenuItem(menu, "dialog.main.profile.restoreversion", () -> {
					Profile prof = findings.get(0).profile();
					if (Boolean.TRUE.equals(new ProfileHistoryDialog(shell_, prof).open())) {
						org.dbgl.util.ProfileWarnings.forget(prof.getId());
						recheck(prof);
						chooseNew();
						fillTree();
						result_ = Boolean.TRUE;
					}
				});
				menu.getItem(menu.getItemCount() - 1).setEnabled(kept); // greyed out while DBGL keeps no earlier version
			}
			// choosing what is missing (the game, its setup, another program) right above the folder of the game
			if ((findings.size() == 1) && findings.get(0).kind().equals("missing") && (findings.get(0).profile() != null))
				addMenuItem(menu, "dialog.checkprofiles.menu.choosemissing", () -> chooseMissing(findings.get(0)));
			if ((findings.size() == 1) && (findings.get(0).profile() != null) && List.of("setupmissing", "altexemissing").contains(findings.get(0).kind()))
				addMenuItem(menu, "dialog.checkprofiles.menu.choosemissing", () -> chooseMissingProgram(findings.get(0)));
			Profile ofGame = selectedProfile();
			if (ofGame != null) // the folder of the game itself (where its program is), or the nearest one there is
				addMenuItem(menu, "dialog.main.profile.openfolder", () -> org.dbgl.util.SystemUtils.openDirForViewing(existing(gameDir(ofGame)), shell_));
			// what another kind of DOSBox can (a command, a mount option, PC-98): the profile switched to it
			if ((findings.size() == 1) && (findings.get(0).profile() != null) && List.of("notinversion", "pc98", "dosboxx", "unknownsettings", "othersetting2").contains(findings.get(0).kind()))
				addMenuItem(menu, "dialog.main.profile.switchdosboxversion", () -> {
					Profile prof = findings.get(0).profile();
					new SwitchDosboxVersionDialog(shell_, prof).open();
					try { // the DOSBox version it uses now (the dialog read the profile itself)
						new ProfileRepository().list(StringUtils.EMPTY, "GAM.ID = " + prof.getId(), dbversions_).stream().findFirst()
								.ifPresent(now -> prof.setDosboxVersion(now.getDosboxVersion()));
					} catch (SQLException e) {
						Mess_.on(shell_).exception(e).warning();
					}
					org.dbgl.util.ProfileWarnings.forget(prof.getId());
					recheck(prof);
					chooseNew();
					fillTree();
					result_ = Boolean.TRUE;
				});
			new MenuItem(menu, SWT.SEPARATOR);
		}
		addMenuItem(menu, game ? "dialog.checkprofiles.menu.hidegame": "dialog.checkprofiles.menu.hide", () -> {
			for (TreeItem item: tree_.getSelection()) {
				if (item.getData() instanceof Finding f)
					hiddenNow_.add(f.dismissKey());
				else if (item.getData() instanceof Profile p)
					hiddenNow_.add(key(p));
				else if (item.getData() instanceof List<?> l) // the DOSBox versions: their findings one by one
					l.forEach(o -> hiddenNow_.add(((Finding)o).dismissKey()));
			}
			fillTree();
		});
		new MenuItem(menu, SWT.SEPARATOR);
		addMenuItem(menu, game ? "dialog.checkprofiles.menu.dismissgame": "dialog.checkprofiles.menu.dismissone", () -> dismiss(findings, false));
		if (!findings.isEmpty())
			addMenuItem(menu, "dialog.checkprofiles.menu.dismisskind", () -> dismiss(findings, true));
		menu.setLocation(shell_.getDisplay().getCursorLocation());
		menu.setVisible(true);
	}

	/** the game of the selected line, or null (a line of a DOSBox version) */
	private Profile selectedProfile() {
		TreeItem[] sel = tree_.getSelection();
		if (sel.length != 1)
			return null;
		if (sel[0].getData() instanceof Profile p)
			return p;
		return (sel[0].getData() instanceof Finding f) ? f.profile(): null;
	}

	private static File gameDir(Profile prof) {
		try {
			File dir = prof.getConfiguration().getAutoexec().getCanonicalGameDir();
			if (dir != null)
				return dir;
		} catch (RuntimeException e) {
			// nothing to start: the folder of its first mount below
		}
		for (org.dbgl.model.conf.mount.Mount m: prof.getConfiguration().getAutoexec().getMountingpoints())
			if (m instanceof org.dbgl.model.conf.mount.DirMount dm)
				return dm.getCanonicalPath();
		return org.dbgl.service.FileLocationService.getInstance().getDosroot();
	}

	/** the folder, or the nearest one above it that is there */
	private static File existing(File f) {
		File d = f;
		while ((d != null) && !d.isDirectory())
			d = d.getParentFile();
		return d != null ? d: org.dbgl.service.FileLocationService.getInstance().getDosroot();
	}

	/**
	 * A missing folder or file chosen anew (starting in the nearest folder there is): the path is replaced in the profile, as
	 * "Find missing folders" does it, and the game checked again.
	 */
	private void chooseMissing(Finding f) {
		Profile prof = f.profile();
		File missing = new File(String.valueOf(f.args()[0]));
		File start = existing(missing.getParentFile() != null ? missing.getParentFile(): gameDir(prof));
		String name = missing.getName();
		boolean file = name.matches(".+\\.[A-Za-z0-9]{1,4}"); // a program or an image; else a mounted folder
		String chosen;
		if (file) {
			FileDialog dialog = new FileDialog(shell_, SWT.OPEN);
			dialog.setFilterPath(start.getPath());
			dialog.setFileName(name);
			dialog.setText(text_.get("dialog.checkprofiles.choosemissing.title", new Object[] {name}));
			chosen = dialog.open();
		} else {
			org.eclipse.swt.widgets.DirectoryDialog dialog = new org.eclipse.swt.widgets.DirectoryDialog(shell_);
			dialog.setFilterPath(start.getPath());
			dialog.setText(text_.get("dialog.checkprofiles.choosemissing.title", new Object[] {name}));
			chosen = dialog.open();
		}
		if (chosen == null)
			return;
		try {
			prof.resetAndLoadConfiguration();
			if (org.dbgl.gui.controls.CharsetQuestion.ask(shell_, List.of(prof)) == org.dbgl.gui.controls.CharsetQuestion.Answer.CANCEL)
				return;
			String conf = prof.getConfigurationString();
			String updated = PathRelocation.replaceInConfiguration(conf, Map.of(missing.getPath(), new File(chosen).getPath()),
				org.dbgl.service.FileLocationService.getInstance().getDosroot());
			byte[] before = org.dbgl.util.ProfileHistory.read(prof);
			if (updated.equals(conf)) {
				// the program of the game, its path made of lines DBGL does not write so (e.g. a drive of the configuration of the
				// DOSBox version and "cd" lines before it): set as the profile dialog does it, the lines that started it written anew
				org.dbgl.model.conf.Autoexec autoexec = prof.getConfiguration().getAutoexec();
				String main = autoexec.getGameMain();
				boolean isMain = StringUtils.isNotEmpty(main) && new org.dbgl.model.FileLocation(main, org.dbgl.service.FileLocationService.getInstance().dosrootRelative())
						.getCanonicalFile().getPath().equals(missing.getPath());
				if (!isMain) {
					Mess_.on(shell_).key("dialog.checkprofiles.choosemissing.notfound", new Object[] {missing.getPath()}).warning();
					return;
				}
				autoexec.setGameMain(new org.dbgl.model.FileLocation(chosen, org.dbgl.service.FileLocationService.getInstance().dosrootRelative()).getFile().getPath());
			} else {
				prof.replaceConfigurationData(text_, updated, prof.getConfigurationCanonicalFile());
			}
			prof.saveConfiguration();
			org.dbgl.util.ProfileHistory.recordIfChanged(prof, before); // the state before can be brought back
			org.dbgl.util.ProfileWarnings.forget(prof.getId());
			recheck(prof);
			chooseNew();
			fillTree();
			result_ = Boolean.TRUE;
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	/**
	 * The missing setup or alternative program chosen anew (starting in the nearest folder there is). It is kept in the
	 * database, as the profile dialog does it; the .conf file is not touched. In an image ("WV1DE.CUE\\WVINST.EXE") the
	 * missing one is the image: the part inside it stays.
	 */
	private void chooseMissingProgram(Finding f) {
		Profile prof = f.profile();
		boolean setup = f.kind().equals("setupmissing");
		int nr = setup ? 0: ((Number)f.args()[0]).intValue() - 1;
		File missing = new File(String.valueOf(f.args()[setup ? 0: 1]));
		FileDialog dialog = new FileDialog(shell_, SWT.OPEN);
		dialog.setFilterPath(existing(missing.getParentFile() != null ? missing.getParentFile(): gameDir(prof)).getPath());
		dialog.setFileName(missing.getName());
		dialog.setText(text_.get("dialog.checkprofiles.choosemissing.title", new Object[] {missing.getName()}));
		String chosen = dialog.open();
		if (chosen == null)
			return;
		String old = setup ? prof.getSetupString(): prof.getAltExeString(nr);
		String oldCanonical = new org.dbgl.model.FileLocation(old, setup ? org.dbgl.service.FileLocationService.getInstance().dosrootRelative()
				: org.dbgl.service.FileLocationService.getInstance().dataRelative()).getCanonicalFile().getPath();
		String inside = oldCanonical.startsWith(missing.getPath()) ? oldCanonical.substring(missing.getPath().length()): StringUtils.EMPTY;
		String updated = chosen + inside;
		if (setup)
			prof.setSetupFileLocation(updated);
		else
			prof.setAltExeFileLocation(nr, updated);
		try {
			new org.dbgl.model.repository.ProfileRepository().updateKeepingConfigurationFile(prof);
			org.dbgl.util.ProfileWarnings.forget(prof.getId());
			recheck(prof);
			chooseNew();
			fillTree();
			result_ = Boolean.TRUE;
		} catch (java.sql.SQLException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	/** in the program for .conf files; on Windows, if there is none, in Notepad */
	private void edit(File conf) {
		if (org.dbgl.util.SystemUtils.IS_WINDOWS && (org.eclipse.swt.program.Program.findProgram(".conf") == null)) {
			try {
				new ProcessBuilder("notepad.exe", conf.getPath()).start();
				return;
			} catch (IOException e) {
				// tried the usual way then
			}
		}
		org.dbgl.util.SystemUtils.openForEditing(conf, shell_);
	}

	/** the setting one finding of a game is about, as the profile editor finds it; null if none */
	private static String settingOf(List<Finding> findings) {
		if ((findings.size() != 1) || (findings.get(0).profile() == null))
			return null;
		Finding f = findings.get(0);
		return switch (f.kind()) {
			case "lurecm32l" -> "mididevice";
			case "gusnopatches" -> "ultradir";
			case "invalid", "outofrange" -> (f.args().length > 1) && (f.args()[1] instanceof String item) ? item: null;
			default -> null;
		};
	}

	/** the profile editor on that setting; after OK the game is checked again */
	private void showSetting(Profile prof, String setting) {
		if (new EditSingleProfileDialog(shell_, prof, null, false).showSetting(setting).open() != null) {
			org.dbgl.util.ProfileWarnings.forget(prof.getId());
			recheck(prof);
			chooseNew();
			fillTree();
			result_ = Boolean.TRUE;
		}
	}

	/** the icons of the entries, as in the menu of the profile list */
	private static final Map<String, String> MENU_IMAGES = Map.ofEntries(
		Map.entry("dialog.checkprofiles.menu.editconf", org.dbgl.service.ImageService.IMG_EDIT),
		Map.entry("dialog.checkprofiles.menu.editdosboxconf", org.dbgl.service.ImageService.IMG_EDIT),
		Map.entry("dialog.checkprofiles.menu.openfolder", org.dbgl.service.ImageService.IMG_FOLDER),
		Map.entry("dialog.main.profile.openfolder", org.dbgl.service.ImageService.IMG_FOLDER),
		Map.entry("dialog.checkprofiles.menu.choosemissing", org.dbgl.service.ImageService.IMG_ZOOM),
		Map.entry("dialog.main.profile.switchdosboxversion", org.dbgl.service.ImageService.IMG_MIGRATE),
		Map.entry("dialog.main.profile.restoreversion", org.dbgl.service.ImageService.IMG_REFRESH),
		Map.entry("dialog.checkprofiles.menu.dismissone", org.dbgl.service.ImageService.IMG_DELETE),
		Map.entry("dialog.checkprofiles.menu.dismissgame", org.dbgl.service.ImageService.IMG_DELETE),
		Map.entry("dialog.checkprofiles.menu.dismisskind", org.dbgl.service.ImageService.IMG_DELETE),
		Map.entry("dialog.checkprofiles.menu.showsetting", org.dbgl.service.ImageService.IMG_EDIT),
		Map.entry("dialog.checkprofiles.menu.showultrasnd", org.dbgl.service.ImageService.IMG_FOLDER));

	private static void addMenuItem(Menu menu, String key, Runnable action) {
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(org.dbgl.service.TextService.getInstance().get(key));
		if (MENU_IMAGES.containsKey(key) && (org.dbgl.service.SettingsService.getInstance().getIntValue("gui", "buttondisplay") != 1)) { // 1: text only
			org.eclipse.swt.graphics.Image image = org.dbgl.service.ImageService.getResourceImage(menu.getDisplay(), MENU_IMAGES.get(key));
			if (image != null) {
				item.setImage(image);
				item.addDisposeListener(e -> image.dispose()); // the menu is built anew each time
			}
		}
		item.addListener(SWT.Selection, e -> action.run());
	}

	private void choose(List<Finding> findings, boolean on) {
		for (Finding f: findings)
			if (f.fixable())
				setChosen(f, on);
		refreshBoxes();
	}

	/** hidden for good: just these, or (byKind) all of their kinds for all games, after asking */
	private void dismiss(List<Finding> findings, boolean byKind) {
		if (findings.isEmpty())
			return;
		if (byKind) {
			String examples = findings.stream().map(f -> f.kind()).distinct().map(k -> findings.stream().filter(f -> f.kind().equals(k)).findFirst().get().text())
					.map(t -> "- " + StringUtils.abbreviate(oneLine(t), 200)).collect(java.util.stream.Collectors.joining("\n"));
			if (!Mess_.on(shell_).key("dialog.checkprofiles.confirmdismisskind", new Object[] {examples}).confirm())
				return;
		}
		try {
			ProfileFindings.dismiss(findings, byKind);
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
		findings.forEach(f -> chosen_.remove(f.dismissKey()));
		org.dbgl.util.ProfileWarnings.forgetAll(); // the warning signs in the list leave out what is hidden for good
		result_ = Boolean.TRUE;
		fillTree();
	}

	private void undismiss() {
		if (!Mess_.on(shell_).key("dialog.checkprofiles.confirmundismiss", new Object[] {dismissedCount_}).confirm())
			return;
		try {
			ProfileFindings.undismissAll();
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
		org.dbgl.util.ProfileWarnings.forgetAll();
		result_ = Boolean.TRUE;
		check();
	}

	/** the chosen solutions of what is shown, per profile */
	private Map<Profile, List<Finding>> visibleChosen() {
		Map<Profile, List<Finding>> result = new LinkedHashMap<>();
		for (TreeItem node: tree_.getItems())
			if (node.getData() instanceof Profile p)
				for (TreeItem child: node.getItems())
					if ((child.getData() instanceof Finding f) && f.fixable() && chosen_.contains(f.dismissKey()))
						result.computeIfAbsent(p, x -> new ArrayList<>()).add(f);
		return result;
	}

	/** the next step: the configuration files before and after, applied from there */
	private void preview() {
		Map<Profile, List<Finding>> chosen = visibleChosen();
		if (chosen.isEmpty()) {
			Mess_.on(shell_).key("dialog.checkprofiles.nothingchosen").display();
			return;
		}
		CheckProfilesPreviewDialog dialog = new CheckProfilesPreviewDialog(shell_, chosen, dbversions_);
		if (Boolean.TRUE.equals(dialog.open())) {
			result_ = Boolean.TRUE;
			prepare();
			check();
			String menu = text_.get("dialog.main.menu.profiles").replace("&", ""), undo = StringUtils.removeEnd(text_.get("dialog.main.menu.undobulk").replace("&", "").trim(), "...");
			Mess_.on(shell_).key("dialog.checkprofiles.applied", new Object[] {dialog.changed(), menu, undo}).display();
		}
	}

	/** the profiles that should use DOSBox-X: to the switch dialog, with DOSBox-X chosen already (the newest, or the default one) */
	private void switchToDosboxX() {
		if (result_found_.dosboxX().isEmpty()) {
			Mess_.on(shell_).key("dialog.checkprofiles.dosboxx.none").display();
			return;
		}
		List<DosboxVersion> xs = dbversions_.stream().filter(d -> StringUtils.defaultString(d.getFamily()).toLowerCase().contains("dosbox-x")).toList();
		if (xs.isEmpty()) {
			Mess_.on(shell_).key("dialog.checkprofiles.dosboxx.missing").warning();
			return;
		}
		DosboxVersion target = xs.stream().filter(DosboxVersion::isDefault).findFirst().orElse(xs.get(xs.size() - 1));
		if (Boolean.TRUE.equals(new SwitchDosboxVersionDialog(shell_, new java.util.ArrayList<>(result_found_.dosboxX().keySet())).withTarget(target.getId()).open())) {
			result_ = true;
			prepare();
			check();
		}
	}

	/**
	 * Ask where the first missing folder or file is now. From that, the part of the path that changed is known (e.g. the drive
	 * letter or a renamed folder); it is changed in all profiles with missing paths, wherever the new path exists.
	 */
	private void relocateMissing() {
		if (result_found_.missing().isEmpty() && result_found_.missingLinks().isEmpty()) {
			Mess_.on(shell_).key("dialog.checkprofiles.nomissing").display();
			return;
		}
		String first = !result_found_.missing().isEmpty() ? result_found_.missing().values().iterator().next().get(0)
				: Profile.linkFile(result_found_.missingLinks().keySet().iterator().next().getLinks()[result_found_.missingLinks().values().iterator().next().get(0)]).getPath();
		String question = text_.get("dialog.checkprofiles.relocate.where", new Object[] {first});
		String chosen;
		if (looksLikeFile(first)) {
			FileDialog dialog = new FileDialog(shell_, SWT.OPEN);
			dialog.setText(question);
			dialog.setFileName(new File(first).getName());
			chosen = dialog.open();
		} else {
			org.eclipse.swt.widgets.DirectoryDialog dialog = new org.eclipse.swt.widgets.DirectoryDialog(shell_);
			dialog.setText(question);
			dialog.setMessage(question);
			chosen = dialog.open();
		}
		if (chosen == null)
			return;
		String[] prefixes = PathRelocation.changedPrefixes(first, chosen);

		java.util.Map<Profile, java.util.Map<String, String>> changes = new java.util.LinkedHashMap<>();
		int count = 0;
		for (java.util.Map.Entry<Profile, List<String>> e: result_found_.missing().entrySet()) {
			java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
			for (String path: e.getValue()) {
				String moved = PathRelocation.relocate(path, prefixes[0], prefixes[1]);
				if ((moved != null) && new File(moved).exists())
					map.put(path, moved);
			}
			if (!map.isEmpty()) {
				changes.put(e.getKey(), map);
				count += map.size();
			}
		}
		// links (manuals, extras) moved the same way: they are kept in the database, not in the profile configuration
		java.util.Map<Profile, java.util.Map<Integer, String>> linkChanges = new java.util.LinkedHashMap<>();
		for (java.util.Map.Entry<Profile, List<Integer>> e: result_found_.missingLinks().entrySet()) {
			java.util.Map<Integer, String> map = new java.util.LinkedHashMap<>();
			for (int i: e.getValue()) {
				String moved = PathRelocation.relocate(Profile.linkFile(e.getKey().getLinks()[i]).getPath(), prefixes[0], prefixes[1]);
				if ((moved != null) && new File(moved).exists())
					map.put(i, moved);
			}
			if (!map.isEmpty()) {
				linkChanges.put(e.getKey(), map);
				count += map.size();
			}
		}
		if (changes.isEmpty() && linkChanges.isEmpty()) {
			Mess_.on(shell_).key("dialog.checkprofiles.relocate.nothing", new Object[] {prefixes[0], prefixes[1]}).warning();
			return;
		}
		java.util.Set<Profile> touched = new java.util.LinkedHashSet<>(changes.keySet());
		touched.addAll(linkChanges.keySet());
		if (!Mess_.on(shell_).key("dialog.checkprofiles.relocate.confirm", new Object[] {count, touched.size(), prefixes[0], prefixes[1]}).confirm())
			return;
		org.dbgl.gui.controls.CharsetQuestion.Answer charsetAnswer = org.dbgl.gui.controls.CharsetQuestion.ask(shell_, changes.keySet());
		if (charsetAnswer == org.dbgl.gui.controls.CharsetQuestion.Answer.CANCEL)
			return;
		if (!changes.isEmpty())
			org.dbgl.util.BulkUndo.remember(text_.get("dialog.bulkundo.relocate"), changes.keySet());
		int changed = 0;
		for (java.util.Map.Entry<Profile, java.util.Map<Integer, String>> e: linkChanges.entrySet()) {
			Profile prof = e.getKey();
			try {
				for (java.util.Map.Entry<Integer, String> l: e.getValue().entrySet()) // stored relative to the DBGL folder when inside it
					prof.setLinkDestination(l.getKey(), new org.dbgl.model.FileLocation(l.getValue(), org.dbgl.service.FileLocationService.getInstance().dataRelative()).getFile().getPath());
				new ProfileRepository().update(prof);
				if (!changes.containsKey(prof))
					changed++;
			} catch (SQLException ex) {
				Mess_.on(shell_).exception(ex).warning();
			}
		}
		for (java.util.Map.Entry<Profile, java.util.Map<String, String>> e: changes.entrySet()) {
			Profile prof = e.getKey();
			try {
				prof.resetAndLoadConfiguration();
				org.dbgl.gui.controls.CharsetQuestion.applyTo(prof, charsetAnswer);
				String conf = prof.getConfigurationString();
				String updated = PathRelocation.replaceInConfiguration(conf, e.getValue(), org.dbgl.service.FileLocationService.getInstance().getDosroot());
				if (!updated.equals(conf)) {
					prof.replaceConfigurationData(org.dbgl.service.TextService.getInstance(), updated, prof.getConfigurationCanonicalFile());
					prof.getConfiguration().getAutoexec().removeRepeatedMounts(); // left by earlier versions, which added them on every repair
					byte[] before = org.dbgl.util.ProfileHistory.read(prof);
					prof.saveConfiguration();
					org.dbgl.util.ProfileHistory.recordIfChanged(prof, before); // the state before can be brought back too
					changed++;
				}
			} catch (IOException ex) {
				Mess_.on(shell_).exception(ex).warning();
			}
		}
		result_ = Boolean.TRUE;
		check();
		Mess_.on(shell_).key("dialog.checkprofiles.relocate.done", new Object[] {changed}).display();
	}

	private static boolean looksLikeFile(String path) {
		String name = new File(path).getName();
		int dot = name.lastIndexOf('.');
		return (dot > 0) && (dot >= name.length() - 4);
	}

	private void saveReport() {
		if (StringUtils.isBlank(reportText_))
			return;
		FileDialog dialog = new FileDialog(shell_, SWT.SAVE);
		dialog.setFilterExtensions(new String[] {"*.txt", "*.*"});
		dialog.setFileName("dbgl-profile-check.txt");
		dialog.setOverwrite(true);
		String file = dialog.open();
		if (file != null) {
			try {
				Files.writeString(new File(file).toPath(), reportText_, StandardCharsets.UTF_8);
			} catch (IOException e) {
				Mess_.on(shell_).exception(e).warning();
			}
		}
	}
}
