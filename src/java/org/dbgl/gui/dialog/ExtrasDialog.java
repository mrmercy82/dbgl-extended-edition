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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.util.ExtrasFinder;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * Shows the manuals, walkthroughs, maps and soundtracks found in the folders of the selected games; the ticked ones are linked
 * in free link fields of their profiles. The result is the number of links added, or null when cancelled.
 */
public class ExtrasDialog extends SizeControlledButtonDialog<Integer> {

	record Row(Profile profile, ExtrasFinder.Extra extra) {
	}

	private final List<Profile> profiles_;
	private final Map<Profile, List<ExtrasFinder.Extra>> found_;
	private final List<Row> rows_ = new ArrayList<>();

	/** @param found what was found per profile (searched before, with a progress bar); a profile without a game folder is missing */
	public ExtrasDialog(Shell parent, List<Profile> profiles, Map<Profile, List<ExtrasFinder.Extra>> found) {
		super(parent, "extrasdialog");
		profiles_ = profiles;
		found_ = found;
	}

	/** search the game folders of the profiles, counting the profiles done */
	public static Map<Profile, List<ExtrasFinder.Extra>> search(List<Profile> profiles, java.util.concurrent.atomic.AtomicInteger done) {
		Map<Profile, List<ExtrasFinder.Extra>> found = new LinkedHashMap<>();
		for (Profile p: profiles) {
			if (ExtrasFinder.gameFolder(p) != null)
				found.put(p, ExtrasFinder.find(p));
			done.incrementAndGet();
		}
		return found;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.extras.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));
		Label info = new Label(contents_, SWT.WRAP);
		info.setText(text_.get("dialog.extras.info"));
		GridData infoData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		infoData.widthHint = 720;
		info.setLayoutData(infoData);

		Table table = new Table(contents_, SWT.CHECK | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = 300;
		table.setLayoutData(tableData);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		String[] keys = {"dialog.replacevalues.column.profile", "dialog.extras.column.links", "dialog.extras.column.kind", "dialog.extras.column.file",
				"dialog.extras.column.open"};
		int[] widths = {190, 120, 150, 330, 90};
		for (int i = 0; i < keys.length; i++) {
			TableColumn col = new TableColumn(table, SWT.NONE);
			col.setText(text_.get(keys[i]));
			col.setWidth(widths[i]);
		}
		// the kind can be changed per find (it decides the title of the link), and each find can be opened to check it
		ExtrasFinder.Kind[] kinds = ExtrasFinder.Kind.values();
		String[] kindNames = java.util.Arrays.stream(kinds).map(k -> new ExtrasFinder.Extra(k, null).title()).toArray(String[]::new);
		List<org.eclipse.swt.widgets.Combo> kindCombos = new ArrayList<>();
		java.util.Map<ExtrasFinder.Kind, org.eclipse.swt.graphics.Image> kindIcons = new java.util.EnumMap<>(ExtrasFinder.Kind.class);
		// the system drop-down (its list follows the mouse); on Windows the rows are made as high as it is (on Linux that
		// would cost the colour of the link count)
		org.eclipse.swt.widgets.Combo probe = new org.eclipse.swt.widgets.Combo(table, SWT.DROP_DOWN | SWT.READ_ONLY);
		int rowHeight = "win32".equals(SWT.getPlatform()) ? probe.computeSize(SWT.DEFAULT, SWT.DEFAULT).y: 0;
		probe.dispose();
		if (rowHeight > 0)
			table.addListener(SWT.MeasureItem, ev -> ev.height = Math.max(ev.height, rowHeight));
		// the free link fields per profile
		Map<Profile, Integer> free = new java.util.HashMap<>();
		int noFolder = 0;
		for (Profile p: profiles_) {
			if (!found_.containsKey(p)) {
				noFolder++;
				continue;
			}
			for (ExtrasFinder.Extra e: found_.get(p)) {
				String link = ExtrasFinder.linkOf(e.file());
				boolean linked = false;
				for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++)
					linked |= link.equalsIgnoreCase(p.getLinkDestination(i));
				if (linked)
					continue;
				rows_.add(new Row(p, e));
				int f = free.computeIfAbsent(p, x -> {
					int n = 0;
					for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++)
						if (org.apache.commons.lang3.StringUtils.isBlank(x.getLinkDestination(i)))
							n++;
					return n;
				});
				TableItem item = new TableItem(table, SWT.NONE);
				item.setText(new String[] {p.getTitle(), text_.get("dialog.extras.links", new Object[] {Profile.NR_OF_LINK_TITLES - f, f}), "", link, ""});
				item.setChecked(true);
				org.eclipse.swt.widgets.Combo kind = new org.eclipse.swt.widgets.Combo(table, SWT.DROP_DOWN | SWT.READ_ONLY);
				kind.setItems(kindNames);
				kind.select(e.kind().ordinal());
				// the icon of the kind in front of the file, as in the context menu 'View' later
				java.util.function.IntConsumer showIcon = k -> item.setImage(3, kindIcons.computeIfAbsent(kinds[Math.max(0, k)], x -> {
					org.eclipse.swt.graphics.Image img = org.dbgl.service.ImageService.getResourceImage(shell_.getDisplay(),
						org.dbgl.service.ImageService.IMG_EXTRA_PREFIX + x.name().toLowerCase(java.util.Locale.ROOT) + ".png");
					shell_.addDisposeListener(d -> img.dispose());
					return img;
				}));
				showIcon.accept(e.kind().ordinal());
				kind.addListener(SWT.Selection, ev -> showIcon.accept(kind.getSelectionIndex()));
				org.eclipse.swt.custom.TableEditor kindEditor = new org.eclipse.swt.custom.TableEditor(table);
				kindEditor.grabHorizontal = true;
				kindEditor.minimumHeight = rowHeight;
				kindEditor.setEditor(kind, item, 2);
				kindCombos.add(kind);
				org.eclipse.swt.widgets.Button open = new org.eclipse.swt.widgets.Button(table, SWT.PUSH);
				open.setText(text_.get("dialog.extras.open"));
				open.addListener(SWT.Selection, ev -> openFind(e.file()));
				org.eclipse.swt.custom.TableEditor openEditor = new org.eclipse.swt.custom.TableEditor(table);
				openEditor.grabHorizontal = true;
				openEditor.minimumHeight = rowHeight;
				openEditor.setEditor(open, item, 4);
			}
		}
		table.addListener(SWT.DefaultSelection, ev -> { // double-click on a row opens the find too
			int i = table.getSelectionIndex();
			if (i >= 0)
				openFind(rows_.get(i).extra().file());
		});
		Label summary = new Label(contents_, SWT.WRAP);
		summary.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		String found = text_.get("dialog.extras.summary", new Object[] {rows_.size(), profiles_.size(), noFolder});
		// more ticked than free: the link count of those profiles in red, and a note below
		Runnable checkFree = () -> {
			Map<Profile, Integer> ticked = new java.util.HashMap<>();
			for (int i = 0; i < rows_.size(); i++)
				if (table.getItem(i).getChecked())
					ticked.merge(rows_.get(i).profile(), 1, Integer::sum);
			java.util.Set<Profile> over = new java.util.HashSet<>();
			ticked.forEach((p, n) -> {
				if (n > free.get(p))
					over.add(p);
			});
			for (int i = 0; i < rows_.size(); i++)
				table.getItem(i).setForeground(1, over.contains(rows_.get(i).profile()) ? shell_.getDisplay().getSystemColor(SWT.COLOR_RED): null);
			summary.setText(over.isEmpty() ? found: found + "\n" + text_.get("dialog.extras.toomany", new Object[] {over.size()}));
			contents_.layout(true, true);
		};
		checkFree.run();
		table.addListener(SWT.Selection, ev -> {
			if (ev.detail == SWT.CHECK)
				checkFree.run();
		});

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				Map<Profile, List<ExtrasFinder.Extra>> chosen = new LinkedHashMap<>();
				for (int i = 0; i < rows_.size(); i++)
					if (table.getItem(i).getChecked())
						chosen.computeIfAbsent(rows_.get(i).profile(), k -> new ArrayList<>())
								.add(new ExtrasFinder.Extra(kinds[Math.max(0, kindCombos.get(i).getSelectionIndex())], rows_.get(i).extra().file()));
				int added = 0, full = 0;
				StringBuilder failed = new StringBuilder();
				for (Map.Entry<Profile, List<ExtrasFinder.Extra>> c: chosen.entrySet()) {
					int n = ExtrasFinder.link(c.getKey(), c.getValue());
					if (n < c.getValue().size())
						full++;
					if (n == 0)
						continue;
					try {
						new ProfileRepository().update(c.getKey());
						added += n;
					} catch (Exception e) {
						failed.append(c.getKey().getTitle()).append(": ").append(e.getMessage()).append('\n');
					}
				}
				if (failed.length() > 0)
					Mess_.on(shell_).txt(failed.toString().trim()).warning();
				if (full > 0)
					Mess_.on(shell_).key("dialog.extras.full2", new Object[] {full}).display();
				result_ = added;
				shell_.close();
			}
		});
		okButton_.setText(text_.get("dialog.extras.link"));
	}

	/** show a find with the program the system has for it, a soundtrack folder in the file manager */
	private void openFind(java.io.File file) {
		if (file.isDirectory())
			org.dbgl.util.SystemUtils.openDirForViewing(file, shell_);
		else
			org.dbgl.util.SystemUtils.openForEditing(file, shell_);
	}
}
