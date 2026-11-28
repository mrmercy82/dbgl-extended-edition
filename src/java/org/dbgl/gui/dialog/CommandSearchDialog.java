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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


/**
 * Alpha 54 (Ctrl+K): finds a command of the menus or a setting by a word of it, and runs or shows it. The commands are taken
 * from the menus as they are (with what is on and off at the moment), the settings from the window of the settings. The result:
 * what to do once this window is closed, or null.
 */
public class CommandSearchDialog extends BaseDialog<Runnable> {

	/** a command or setting: its name, where it is (menu or tabs), its keys, what it does; null when it cannot be done now */
	public record Command(String name, String where, String keys, Runnable run) {
		String searchText() {
			return normalized(name + " " + where);
		}
	}

	private static final int MAX_SHOWN = 200;

	private final List<Command> commands_;
	private final List<Command> shown_ = new ArrayList<>();
	private Text search_;
	private Table table_;
	private String query_ = "";

	public CommandSearchDialog(Shell parent, List<Command> commands) {
		super(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		commands_ = commands;
	}

	/** the words to search for at first */
	public CommandSearchDialog query(String query) {
		query_ = query == null ? "": query;
		return this;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.commandsearch.title");
	}

	@Override
	protected void onShellCreated() {
		shell_.setLayout(new GridLayout());
		search_ = new Text(shell_, SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL | SWT.BORDER);
		search_.setMessage(text_.get("dialog.commandsearch.hint"));
		search_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		table_ = new Table(shell_, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
		table_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		TableColumn name = new TableColumn(table_, SWT.NONE);
		TableColumn where = new TableColumn(table_, SWT.NONE);
		TableColumn keys = new TableColumn(table_, SWT.NONE);
		where.setWidth(250);
		keys.setWidth(70);
		// the name gets what is left, also when the window is made larger
		table_.addListener(SWT.Resize, e -> name.setWidth(Math.max(150, table_.getClientArea().width - where.getWidth() - keys.getWidth())));

		search_.addListener(SWT.Modify, e -> fill());
		search_.addListener(SWT.KeyDown, e -> {
			if ((e.keyCode == SWT.ARROW_DOWN) || (e.keyCode == SWT.ARROW_UP) || (e.keyCode == SWT.PAGE_DOWN) || (e.keyCode == SWT.PAGE_UP)) {
				int n = table_.getItemCount();
				if (n == 0)
					return;
				int step = (e.keyCode == SWT.PAGE_DOWN || e.keyCode == SWT.PAGE_UP) ? 10: 1;
				int at = table_.getSelectionIndex() + ((e.keyCode == SWT.ARROW_DOWN || e.keyCode == SWT.PAGE_DOWN) ? step: -step);
				table_.setSelection(Math.max(0, Math.min(n - 1, at)));
				e.doit = false;
			}
		});
		search_.addListener(SWT.DefaultSelection, e -> choose());
		table_.addListener(SWT.DefaultSelection, e -> choose());

		shell_.setSize(820, 460);
		search_.setText(query_);
		search_.setSelection(query_.length());
		fill();
		search_.setFocus();
	}

	private void fill() {
		shown_.clear();
		shown_.addAll(find(commands_, search_.getText(), MAX_SHOWN));
		table_.setRedraw(false);
		table_.removeAll();
		for (Command c: shown_) {
			TableItem item = new TableItem(table_, SWT.NONE);
			item.setText(new String[] {c.name(), c.where(), c.keys()});
			if (c.run() == null) {
				item.setForeground(display_.getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
				item.setText(0, c.name() + " (" + text_.get("dialog.commandsearch.disabled") + ")");
			}
		}
		if (shown_.isEmpty())
			new TableItem(table_, SWT.NONE).setText(text_.get("dialog.commandsearch.none"));
		else
			table_.setSelection(0);
		table_.setRedraw(true);
	}

	private void choose() {
		int i = table_.getSelectionIndex();
		if ((i < 0) || (i >= shown_.size()) || (shown_.get(i).run() == null)) {
			display_.beep();
			return;
		}
		result_ = shown_.get(i).run();
		shell_.close();
	}

	/**
	 * the commands that fit the words searched for (each word somewhere in the name or the place, any case, accents ignored), the
	 * best first: the name starting with it, then a word of the name, then somewhere in the name, then only in the place
	 */
	public static List<Command> find(List<Command> commands, String query, int max) {
		String[] words = normalized(query).split(" +");
		List<Command> result = new ArrayList<>();
		List<Integer> scores = new ArrayList<>();
		for (Command c: commands) {
			String all = c.searchText();
			boolean fits = true;
			for (String w: words)
				if (!w.isEmpty() && !all.contains(w))
					fits = false;
			if (!fits)
				continue;
			result.add(c);
			scores.add(score(c, words[0]));
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < result.size(); i++)
			order.add(i);
		order.sort(Comparator.comparingInt((Integer i) -> scores.get(i)).thenComparingInt(i -> result.get(i).run() == null ? 1: 0).thenComparingInt(i -> i));
		List<Command> sorted = new ArrayList<>();
		for (int i = 0; i < order.size() && i < max; i++)
			sorted.add(result.get(order.get(i)));
		return sorted;
	}

	private static int score(Command c, String word) {
		String name = normalized(c.name());
		if (word.isEmpty())
			return 0;
		if (name.startsWith(word))
			return 0;
		if (name.contains(" " + word) || name.contains("-" + word) || name.contains("(" + word))
			return 1;
		if (name.contains(word))
			return 2;
		return 3;
	}

	/** small letters without accents and marks, spaces between words (Größe, GRÖSSE and grosse all fit "gro") */
	public static String normalized(String s) {
		String t = Normalizer.normalize(s == null ? "": s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace("ß", "ss");
		return t.replaceAll("[\\s ]+", " ").strip();
	}

	/**
	 * the commands of a menu and the menus below, with the place (the menus above, split by "›"); a menu is told that it is shown
	 * first, so that it fills itself and turns its entries on and off as it would when opened
	 */
	public static void collect(Menu menu, String where, List<Command> into, Set<String> seen) {
		if ((menu == null) || menu.isDisposed())
			return;
		try {
			menu.notifyListeners(SWT.Show, new Event());
		} catch (RuntimeException e) {
			// a menu that cannot fill itself now: as it is
		}
		for (MenuItem item: menu.getItems()) {
			if (item.isDisposed() || ((item.getStyle() & SWT.SEPARATOR) != 0))
				continue;
			String[] parts = item.getText().split("\t", 2);
			String name = SettingsDialog.placeText(parts[0]);
			if (name == null)
				continue;
			if (item.getMenu() != null) {
				collect(item.getMenu(), where.isEmpty() ? name: where + " › " + name, into, seen);
			} else if (seen.add(where + "|" + name)) {
				into.add(new Command(name, where, parts.length > 1 ? parts[1].strip(): "", enabled(item) ? run(item): null));
			}
		}
		try {
			menu.notifyListeners(SWT.Hide, new Event());
		} catch (RuntimeException e) {
			// as above
		}
	}

	private static boolean enabled(MenuItem item) {
		for (MenuItem i = item; i != null; i = i.getParent().getParentItem())
			if (!i.getEnabled())
				return false;
		return true;
	}

	/** what choosing the entry in its menu does: a box turned on or off, a choice made, and then what the entry does */
	private static Runnable run(MenuItem item) {
		return () -> {
			if (item.isDisposed())
				return;
			if ((item.getStyle() & SWT.CHECK) != 0) {
				item.setSelection(!item.getSelection());
			} else if ((item.getStyle() & SWT.RADIO) != 0) {
				MenuItem[] all = item.getParent().getItems();
				int at = java.util.Arrays.asList(all).indexOf(item);
				for (int i = at - 1; i >= 0 && (all[i].getStyle() & SWT.RADIO) != 0; i--)
					all[i].setSelection(false);
				for (int i = at + 1; i < all.length && (all[i].getStyle() & SWT.RADIO) != 0; i++)
					all[i].setSelection(false);
				item.setSelection(true);
			}
			item.notifyListeners(SWT.Selection, new Event());
		};
	}

	/** the settings, each opening the window of the settings at it */
	public static List<Command> settings(List<SettingsDialog.Place> places, String settingsTitle, java.util.function.Consumer<SettingsDialog.Place> open) {
		List<Command> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (SettingsDialog.Place p: places) {
			String where = settingsTitle + " › " + String.join(" › ", p.tabs());
			if (seen.add(where + "|" + p.text()))
				result.add(new Command(p.text(), where, "", () -> open.accept(p)));
		}
		return result;
	}
}
