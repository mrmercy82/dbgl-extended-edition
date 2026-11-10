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

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.util.BulkUndo;
import org.dbgl.util.ProfileFindings;
import org.dbgl.util.ProfileFindings.DiffLine;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;


/**
 * Undo the last change of many profiles at once, looking like the preview of "Check profiles": for each game what goes back
 * (red: now, goes; green: comes back), each under its section. A click on the line of a game leaves it out (it stays remembered,
 * to be brought back later) or takes it again.
 */
public class BulkUndoDialog extends SizeControlledButtonDialog<Set<Integer>> {

	private record Entry(BulkUndo.Change change, List<DiffLine> diff, List<DiffLine> full) {
	}

	private final List<DosboxVersion> dbversions_;
	private final BulkUndo.Info info_;
	private final List<Entry> entries_ = new ArrayList<>();
	private final Map<Integer, Boolean> take_ = new LinkedHashMap<>();
	private final Map<Integer, Integer> headerLines_ = new LinkedHashMap<>();
	private StyledText text_area_;
	private Label infoLabel_;
	private org.eclipse.swt.widgets.Button full_;
	private Font font_, bold_;

	public BulkUndoDialog(Shell parent, BulkUndo.Info info, List<DosboxVersion> dbversions) {
		super(parent, "bulkundodialog");
		info_ = info;
		dbversions_ = dbversions;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.bulkundo.title");
	}

	@Override
	protected boolean prepare() {
		for (BulkUndo.Change c: BulkUndo.changes(dbversions_)) {
			List<DiffLine> diff = new ArrayList<>(ProfileFindings.diff(c.now(), c.then(), null));
			List<DiffLine> full = new ArrayList<>(ProfileFindings.diff(c.now(), c.then(), null, true));
			if ((c.nowVersion() != null) && (c.thenVersion() != null) && (c.nowVersion().getId() != c.thenVersion().getId())) {
				List<DiffLine> v = List.of(new DiffLine('-', text_.get("dialog.bulkundo.version", new Object[] {c.nowVersion().getTitle()})),
					new DiffLine('+', text_.get("dialog.bulkundo.version", new Object[] {c.thenVersion().getTitle()})));
				diff.addAll(0, v);
				full.addAll(0, v);
			}
			entries_.add(new Entry(c, diff, full));
			take_.put(c.profile().getId(), !diff.isEmpty());
		}
		return true;
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));
		org.eclipse.swt.widgets.Composite top = new org.eclipse.swt.widgets.Composite(contents_, SWT.NONE);
		top.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout topLayout = new GridLayout(3, false);
		topLayout.marginWidth = 0;
		topLayout.marginHeight = 0;
		top.setLayout(topLayout);
		infoLabel_ = new Label(top, SWT.WRAP);
		infoLabel_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		// by the title of the game or by its profile ID (remembered)
		org.eclipse.swt.widgets.Combo sort = new org.eclipse.swt.widgets.Combo(top, SWT.READ_ONLY);
		sort.setItems(text_.get("dialog.checkprofiles.sort.name"), text_.get("dialog.checkprofiles.sort.id"));
		sort.select("id".equals(org.dbgl.service.SettingsService.getInstance().getValue("checkprofiles", "previewsort")) ? 1: 0);
		sort.addListener(SWT.Selection, e -> {
			org.dbgl.service.SettingsService.getInstance().setValue("checkprofiles", "previewsort", sort.getSelectionIndex() == 1 ? "id": "name");
			sortEntries();
			text_area_.setTopIndex(0);
			render();
		});
		full_ = new org.eclipse.swt.widgets.Button(top, SWT.CHECK);
		full_.setText(text_.get("dialog.checkprofiles.previewfull"));
		full_.setSelection(org.dbgl.service.SettingsService.getInstance().getBooleanValue("checkprofiles", "previewfull"));
		full_.addListener(SWT.Selection, e -> {
			org.dbgl.service.SettingsService.getInstance().setBooleanValue("checkprofiles", "previewfull", full_.getSelection());
			text_area_.setTopIndex(0);
			render();
		});
		org.eclipse.swt.widgets.Composite legend = new org.eclipse.swt.widgets.Composite(top, SWT.NONE);
		legend.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		org.eclipse.swt.layout.RowLayout row = new org.eclipse.swt.layout.RowLayout();
		row.marginLeft = 0;
		row.marginTop = 0;
		row.marginBottom = 0;
		row.spacing = 18;
		legend.setLayout(row);
		org.eclipse.swt.widgets.Display d = shell_.getDisplay();
		for (Object[] l: new Object[][] {{"dialog.bulkundo.legend.now", SWT.COLOR_DARK_RED}, {"dialog.bulkundo.legend.back", SWT.COLOR_DARK_GREEN}}) {
			Label label = new Label(legend, SWT.NONE);
			label.setText(text_.get((String)l[0]));
			label.setForeground(d.getSystemColor((Integer)l[1]));
		}
		text_area_ = new StyledText(contents_, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		text_area_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		String face = SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace");
		font_ = new Font(d, face, 9, SWT.NORMAL);
		bold_ = new Font(d, face, 9, SWT.BOLD);
		text_area_.setFont(font_);
		shell_.addDisposeListener(e -> {
			font_.dispose();
			bold_.dispose();
		});
		text_area_.addListener(SWT.MouseUp, e -> {
			if ((e.button != 1) || (text_area_.getSelectionCount() > 0))
				return;
			try {
				toggle(text_area_.getLineIndex(e.y));
			} catch (IllegalArgumentException ex) {
				// below the last line
			}
		});
		text_area_.addListener(SWT.KeyDown, e -> {
			if (e.character == ' ')
				toggle(text_area_.getLineAtOffset(text_area_.getCaretOffset()));
		});
		org.eclipse.swt.widgets.Menu menu = new org.eclipse.swt.widgets.Menu(text_area_);
		org.eclipse.swt.widgets.MenuItem copy = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
		copy.setText(text_.get("dialog.checkprofiles.copy"));
		copy.addListener(SWT.Selection, e -> {
			if (text_area_.getSelectionCount() == 0)
				text_area_.selectAll();
			text_area_.copy();
		});
		org.eclipse.swt.widgets.MenuItem selectAll = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
		selectAll.setText(text_.get("dialog.checkprofiles.selectall"));
		selectAll.addListener(SWT.Selection, e -> text_area_.selectAll());
		text_area_.setMenu(menu);

		createGoCancelButtons("dialog.bulkundo.undo", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				undo();
			}
		});
		int width = okButton_.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
		org.eclipse.swt.widgets.Button all = Button_.on(otherButtons_).text().key("button.all").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				entries_.forEach(en -> take_.put(en.change().profile().getId(), !en.diff().isEmpty()));
				render();
			}
		}).ctrl();
		org.eclipse.swt.widgets.Button none = Button_.on(otherButtons_).text().key("button.none").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				take_.replaceAll((p, b) -> false);
				render();
			}
		}).ctrl();
		for (org.eclipse.swt.widgets.Button b: new org.eclipse.swt.widgets.Button[] {all, none}) {
			GridData gd = new GridData(SWT.BEGINNING, SWT.FILL, false, false);
			gd.widthHint = Math.max(width, ((GridData)okButton_.getLayoutData()).widthHint);
			b.setLayoutData(gd);
		}
		sortEntries();
		render();
	}

	/** by title (as in the list) or by profile ID */
	private void sortEntries() {
		boolean byId = "id".equals(org.dbgl.service.SettingsService.getInstance().getValue("checkprofiles", "previewsort"));
		entries_.sort(byId ? java.util.Comparator.comparingInt(en -> en.change().profile().getId())
			: java.util.Comparator.comparing(en -> en.change().profile().getTitle(), String.CASE_INSENSITIVE_ORDER));
	}

	private void toggle(int line) {
		Integer id = headerLines_.get(line);
		if (id == null)
			return;
		Entry entry = entries_.stream().filter(en -> en.change().profile().getId() == id).findFirst().get();
		if (entry.diff().isEmpty())
			return;
		take_.put(id, !take_.get(id));
		render();
	}

	private void render() {
		int top = text_area_.getTopIndex();
		StringBuilder sb = new StringBuilder();
		List<StyleRange> styles = new ArrayList<>();
		headerLines_.clear();
		int line = 0;
		org.eclipse.swt.widgets.Display d = shell_.getDisplay();
		for (Entry e: entries_) {
			int id = e.change().profile().getId();
			if (line > 0) {
				sb.append('\n');
				line++;
			}
			headerLines_.put(line, id);
			DosboxVersion v = e.change().nowVersion();
			String header = (take_.get(id) ? "☑ ": "☐ ") + e.change().profile().getTitle() + " (" + (v == null ? "-": v.getTitle()) + ")";
			styles.add(range(sb.length(), header.length(), null, bold_));
			sb.append(header).append('\n');
			line++;
			if (e.diff().isEmpty()) {
				String s = "    " + text_.get("dialog.checkprofiles.nochange");
				styles.add(range(sb.length(), s.length(), d.getSystemColor(SWT.COLOR_DARK_GRAY), null));
				sb.append(s).append('\n');
				line++;
			}
			for (DiffLine dl: full_.getSelection() ? e.full(): e.diff()) {
				if (dl.kind() == '!')
					continue;
				boolean section = dl.text().startsWith("[") && dl.text().endsWith("]") && ((dl.kind() == ' ') || (dl.kind() == '~'));
				String s = section ? "    " + dl.text(): "  " + (dl.kind() == '~' ? ' ': dl.kind()) + " " + dl.text();
				org.eclipse.swt.graphics.Color c = switch (dl.kind()) {
					case '-' -> d.getSystemColor(SWT.COLOR_DARK_RED);
					case '+' -> d.getSystemColor(SWT.COLOR_DARK_GREEN);
					case ' ' -> section || ProfileFindings.GAP.equals(dl.text()) ? d.getSystemColor(SWT.COLOR_DARK_GRAY): null;
					default -> section ? d.getSystemColor(SWT.COLOR_DARK_GRAY): d.getSystemColor(SWT.COLOR_DARK_CYAN); // sections gray; '~' (gone without effect) teal
				};
				styles.add(range(sb.length(), s.length(), c, null));
				sb.append(s).append('\n');
				line++;
			}
		}
		text_area_.setText(sb.toString());
		text_area_.setStyleRanges(styles.toArray(new StyleRange[0]));
		text_area_.setTopIndex(top);
		long taken = take_.values().stream().filter(b -> b).count();
		String when = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(info_.when());
		infoLabel_.setText(text_.get("dialog.bulkundo.info", new Object[] {info_.label(), when, taken, entries_.size()}));
		okButton_.setEnabled(taken > 0);
		infoLabel_.getParent().layout(true);
	}

	private static StyleRange range(int start, int length, org.eclipse.swt.graphics.Color color, Font font) {
		StyleRange r = new StyleRange(start, length, color, null);
		r.font = font;
		return r;
	}

	private void undo() {
		Set<Integer> ids = new LinkedHashSet<>();
		take_.forEach((id, b) -> {
			if (b)
				ids.add(id);
		});
		if (ids.isEmpty() || !Mess_.on(shell_).key("dialog.bulkundo.confirm", new Object[] {ids.size(), entries_.size()}).confirm())
			return;
		try {
			result_ = new LinkedHashSet<>(BulkUndo.undo(dbversions_, ids));
		} catch (IOException | SQLException e) {
			Mess_.on(shell_).exception(e).warning();
			return;
		}
		shell_.close();
	}
}
