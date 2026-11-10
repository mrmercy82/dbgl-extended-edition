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
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.util.ProfileFindings;
import org.dbgl.util.ProfileFindings.DiffLine;
import org.dbgl.util.ProfileFindings.Finding;
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
 * The step after "Check profiles": for each game the lines of its configuration file that the chosen solutions change
 * (red: gone, green: new), each under the section it is in. A click on the line of a game leaves it out (or takes it again).
 * "Apply" writes them (remembered first, to be undone).
 */
public class CheckProfilesPreviewDialog extends SizeControlledButtonDialog<Boolean> {

	/** @param full all of the file, with the changes marked */
	private record Entry(Profile profile, List<Finding> chosen, List<DiffLine> diff, List<DiffLine> full, String error) {
	}

	private final Map<Profile, List<Finding>> chosen_;
	private final List<DosboxVersion> dbversions_;
	private final List<Entry> entries_ = new ArrayList<>();
	private final Map<Profile, Boolean> take_ = new LinkedHashMap<>();
	/** the line of the text where each game starts */
	private final Map<Integer, Profile> headerLines_ = new LinkedHashMap<>();
	private StyledText text_area_;
	private Label info_;
	private org.eclipse.swt.widgets.Button full_;
	private Font font_, bold_;
	private int changed_;

	public CheckProfilesPreviewDialog(Shell parent, Map<Profile, List<Finding>> chosen, List<DosboxVersion> dbversions) {
		super(parent, "checkprofilespreviewdialog");
		chosen_ = chosen;
		dbversions_ = dbversions;
	}

	/** how many profiles were changed */
	public int changed() {
		return changed_;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.checkprofiles.previewtitle");
	}

	@Override
	protected boolean prepare() {
		for (Map.Entry<Profile, List<Finding>> e: chosen_.entrySet()) {
			try {
				String[] ba = ProfileFindings.preview(e.getKey(), e.getValue(), dbversions_);
				java.nio.charset.Charset from = ProfileFindings.fileCharset(e.getKey()), to = ProfileFindings.charsetAfter(e.getKey(), e.getValue());
				ba = new String[] {ProfileFindings.readable(ba[0], from), ProfileFindings.readable(ba[1], to)}; // umlauts as such
				List<DiffLine> diff = new ArrayList<>(ProfileFindings.diff(ba[0], ba[1], e.getKey().getDosboxVersion()));
				List<DiffLine> full = new ArrayList<>(ProfileFindings.diff(ba[0], ba[1], e.getKey().getDosboxVersion(), true));
				if (!from.equals(to)) { // the text stays, its bytes change
					DiffLine cs = new DiffLine('+', text_.get("dialog.checkprofiles.recoded", new Object[] {from.displayName(), to.displayName()}));
					diff.add(0, cs);
					full.add(0, cs);
				}
				entries_.add(new Entry(e.getKey(), e.getValue(), diff, full, null));
				take_.put(e.getKey(), !diff.isEmpty());
			} catch (IOException | RuntimeException ex) {
				entries_.add(new Entry(e.getKey(), e.getValue(), List.of(), List.of(), String.valueOf(ex.getMessage())));
				take_.put(e.getKey(), false);
			}
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
		info_ = new Label(top, SWT.WRAP);
		info_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		// all of each file, or only what changes (remembered)
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
		// what the colors mean, in their colors, in a line of its own
		org.eclipse.swt.widgets.Composite legend = new org.eclipse.swt.widgets.Composite(top, SWT.NONE);
		legend.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		org.eclipse.swt.layout.RowLayout row = new org.eclipse.swt.layout.RowLayout();
		row.marginLeft = 0;
		row.marginTop = 0;
		row.marginBottom = 0;
		row.spacing = 18;
		legend.setLayout(row);
		org.eclipse.swt.widgets.Display d = shell_.getDisplay();
		for (Object[] l: new Object[][] {{"dialog.checkprofiles.legend.gone", SWT.COLOR_DARK_RED}, {"dialog.checkprofiles.legend.new", SWT.COLOR_DARK_GREEN},
				{"dialog.checkprofiles.legend.same2", SWT.COLOR_DARK_CYAN}}) {
			Label label = new Label(legend, SWT.NONE);
			label.setText(text_.get((String)l[0]));
			label.setForeground(d.getSystemColor((Integer)l[1]));
		}
		text_area_ = new StyledText(contents_, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		text_area_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		font_ = new Font(shell_.getDisplay(), SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		bold_ = new Font(shell_.getDisplay(), SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.BOLD);
		text_area_.setFont(font_);
		shell_.addDisposeListener(e -> {
			font_.dispose();
			bold_.dispose();
		});
		// a click (not marking text to copy) on the line of a game takes it out or in again
		text_area_.addListener(SWT.MouseUp, e -> {
			if ((e.button != 1) || (text_area_.getSelectionCount() > 0))
				return;
			try {
				toggle(text_area_.getLineIndex(e.y));
			} catch (IllegalArgumentException ex) {
				// below the last line
			}
		});
		org.eclipse.swt.widgets.Menu menu = new org.eclipse.swt.widgets.Menu(text_area_);
		org.eclipse.swt.widgets.MenuItem copy = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
		copy.setText(text_.get("dialog.checkprofiles.copy"));
		copy.addListener(SWT.Selection, e -> {
			if (text_area_.getSelectionCount() == 0)
				text_area_.selectAll(); // nothing marked: all of it
			text_area_.copy();
		});
		org.eclipse.swt.widgets.MenuItem selectAll = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
		selectAll.setText(text_.get("dialog.checkprofiles.selectall"));
		selectAll.addListener(SWT.Selection, e -> text_area_.selectAll());
		text_area_.setMenu(menu);
		text_area_.addListener(SWT.KeyDown, e -> {
			if (e.character == ' ')
				toggle(text_area_.getLineAtOffset(text_area_.getCaretOffset()));
		});

		createGoCancelButtons("dialog.checkprofiles.apply2", new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				apply();
			}
		});
		cancelButton_.setText(text_.get("button.back"));
		int width = okButton_.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
		org.eclipse.swt.widgets.Button all = Button_.on(otherButtons_).text().key("button.all").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				entries_.forEach(en -> take_.put(en.profile(), !en.diff().isEmpty()));
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
		// as wide as "Apply" and "Back"
		for (org.eclipse.swt.widgets.Button b: new org.eclipse.swt.widgets.Button[] {all, none})
			b.setLayoutData(new GridData(SWT.BEGINNING, SWT.FILL, false, false));
		for (org.eclipse.swt.widgets.Button b: new org.eclipse.swt.widgets.Button[] {all, none})
			((GridData)b.getLayoutData()).widthHint = Math.max(width, ((GridData)okButton_.getLayoutData()).widthHint);
		sortEntries();
		render();
	}

	/** by title (as in the list) or by profile ID */
	private void sortEntries() {
		boolean byId = "id".equals(org.dbgl.service.SettingsService.getInstance().getValue("checkprofiles", "previewsort"));
		entries_.sort(byId ? java.util.Comparator.comparingInt(en -> en.profile().getId())
			: java.util.Comparator.comparing(en -> en.profile().getTitle(), String.CASE_INSENSITIVE_ORDER));
	}

	private void toggle(int line) {
		Profile p = headerLines_.get(line);
		if (p == null)
			return;
		Entry entry = entries_.stream().filter(en -> en.profile() == p).findFirst().get();
		if (entry.diff().isEmpty())
			return; // nothing to write
		take_.put(p, !take_.get(p));
		render();
	}

	/** the text with its colors, built again (keeping where it was scrolled to) */
	private void render() {
		int top = text_area_.getTopIndex();
		StringBuilder sb = new StringBuilder();
		List<StyleRange> styles = new ArrayList<>();
		headerLines_.clear();
		int line = 0;
		org.eclipse.swt.widgets.Display d = shell_.getDisplay();
		for (Entry e: entries_) {
			if (line > 0) {
				sb.append('\n');
				line++;
			}
			headerLines_.put(line, e.profile());
			String header = (take_.get(e.profile()) ? "☑ ": "☐ ") + e.profile().getTitle() + " ("
				+ (e.profile().getDosboxVersion() == null ? "-": e.profile().getDosboxVersion().getTitle()) + ")  – "
				+ text_.get("dialog.checkprofiles.previewsolutions", new Object[] {e.chosen().size()});
			styles.add(range(sb.length(), header.length(), null, bold_));
			sb.append(header).append('\n');
			line++;
			if (e.error() != null) {
				String s = "    " + text_.get("dialog.checkprofiles.previewerror", new Object[] {e.error()});
				styles.add(range(sb.length(), s.length(), d.getSystemColor(SWT.COLOR_RED), null));
				sb.append(s).append('\n');
				line++;
			} else if (e.diff().isEmpty()) {
				String s = "    " + text_.get("dialog.checkprofiles.nochange");
				styles.add(range(sb.length(), s.length(), d.getSystemColor(SWT.COLOR_DARK_GRAY), null));
				sb.append(s).append('\n');
				line++;
			}
			for (DiffLine dl: full_.getSelection() ? e.full(): e.diff()) {
				if (dl.kind() == '!') { // what follows only goes because DBGL writes what differs from the DOSBox configuration
					String note = "    " + text_.get("dialog.checkprofiles.previewsame");
					styles.add(range(sb.length(), note.length(), d.getSystemColor(SWT.COLOR_DARK_GRAY), null));
					sb.append(note).append('\n');
					line++;
					continue;
				}
				boolean section = dl.text().startsWith("[") && dl.text().endsWith("]") && ((dl.kind() == ' ') || (dl.kind() == '~'));
				String s = section ? "    " + dl.text(): dl.kind() == '~' ? "    = " + dl.text(): "  " + dl.kind() + " " + dl.text();
				org.eclipse.swt.graphics.Color c = switch (dl.kind()) {
					case '-' -> d.getSystemColor(SWT.COLOR_DARK_RED);
					case '+' -> d.getSystemColor(SWT.COLOR_DARK_GREEN);
					case ' ' -> section || ProfileFindings.GAP.equals(dl.text()) ? d.getSystemColor(SWT.COLOR_DARK_GRAY): null; // a line that stays: as written
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
		info_.setText(text_.get("dialog.checkprofiles.previewinfo2", new Object[] {taken, entries_.size()}));
		okButton_.setEnabled(taken > 0);
		info_.getParent().layout(true);
	}

	private static StyleRange range(int start, int length, org.eclipse.swt.graphics.Color color, Font font) {
		StyleRange r = new StyleRange(start, length, color, null);
		r.font = font;
		return r;
	}

	private void apply() {
		List<Profile> profiles = entries_.stream().map(Entry::profile).filter(take_::get).toList();
		if (profiles.isEmpty())
			return;
		String menu = text_.get("dialog.main.menu.profiles").replace("&", ""), undo = StringUtils.removeEnd(text_.get("dialog.main.menu.undobulk").replace("&", "").trim(), "...");
		if (!Mess_.on(shell_).key("dialog.checkprofiles.confirmapply", new Object[] {profiles.size(), menu, undo}).confirm())
			return;
		// written again as a whole: files in Windows-1252 (umlauts) where the system uses UTF-8 kept so, or converted, as the user
		// wants (changing just the lines concerned leaves the bytes of all others as they are)
		List<org.dbgl.model.aggregate.Profile> rewritten = entries_.stream().filter(e -> take_.get(e.profile()) && ProfileFindings.needsRewrite(e.chosen())
			&& e.chosen().stream().noneMatch(f -> f.kind().equals("charsetmismatch"))).map(Entry::profile).toList(); // converted anyway
		org.dbgl.gui.controls.CharsetQuestion.Answer charsetAnswer = org.dbgl.gui.controls.CharsetQuestion.ask(shell_, rewritten);
		if (charsetAnswer == org.dbgl.gui.controls.CharsetQuestion.Answer.CANCEL)
			return;
		org.dbgl.util.BulkUndo.remember(text_.get("dialog.bulkundo.fix"), profiles);
		changed_ = 0;
		List<String> failed = new ArrayList<>(); // one message at the end, not one for each profile (e.g. all read-only)
		for (Entry e: entries_) {
			if (!take_.get(e.profile()))
				continue;
			try {
				ProfileFindings.apply(e.profile(), e.chosen(), dbversions_, charsetAnswer == org.dbgl.gui.controls.CharsetQuestion.Answer.CONVERT);
				org.dbgl.util.ProfileWarnings.forget(e.profile().getId());
				changed_++;
			} catch (IOException | SQLException ex) {
				failed.add(e.profile().getTitle() + ": " + ex.getMessage());
			}
		}
		if (!failed.isEmpty()) {
			String list = failed.stream().limit(20).collect(java.util.stream.Collectors.joining("\n")) + (failed.size() > 20 ? "\n…": "");
			Mess_.on(shell_).key("dialog.checkprofiles.notapplied", new Object[] {failed.size(), list}).warning();
		}
		result_ = Boolean.TRUE;
		shell_.close();
	}
}
