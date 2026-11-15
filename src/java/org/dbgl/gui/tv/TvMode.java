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
package org.dbgl.gui.tv;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.ITextService;
import org.dbgl.service.ImageService;
import org.dbgl.service.TextService;
import org.dbgl.util.ProfileReports;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;


/**
 * Alpha 48: the TV mode, for the sofa: the profiles of the list as big covers over the whole screen, to choose and start with the
 * keyboard or a game controller (see {@link Gamepad}). A/Enter starts, B/Esc goes back, Y/F shows only the favorites, LB/RB and the
 * page keys turn the page.
 */
public final class TvMode {

	private static final int MARGIN = 60, GAP = 34, TITLE_H = 56, HEADER_H = 96, FOOTER_H = 130;

	/** the height of a cover: two rows at least, at most 360 pixels (full HD: two rows of 300) */
	private int coverH() {
		int avail = canvas_.getClientArea().height - HEADER_H - FOOTER_H;
		return Math.max(150, Math.min(360, (avail - 2 * TITLE_H - GAP - 30) / 2));
	}

	private int tileW() {
		return coverH() * 11 / 15; // as a box of a game
	}

	private final Shell shell_;
	private final Canvas canvas_;
	private final List<Profile> all_;
	private final Consumer<Profile> runner_;
	private final ITextService text_ = TextService.getInstance();
	private final Color bg_, tile_, selected_, fg_, dim_;
	private final Color stackNear_ = new Color(74, 74, 80), stackFar_ = new Color(56, 56, 62); // alpha 49: the covers behind a stack
	private final Font big_, medium_, small_;
	private List<Profile> shown_;
	/** alpha 49: the games the filter leaves; shown_ is what is on screen: with series as stacks, or the parts of the open series */
	private List<Profile> filtered_;
	private String openSeries_;
	private final java.util.Map<Integer, Integer> stackSize_ = new java.util.HashMap<>();
	private final java.util.Map<Integer, String> stackAnchor_ = new java.util.HashMap<>(); // the title a stack is sorted by
	private final boolean stacks_ = org.dbgl.service.SettingsService.getInstance().getBooleanValue("gui", "tvstacks");
	/** which games: all, the favorites, or one genre (Y/F goes on to the next); remembered with the game for the next time */
	private record Filter(String key, String genre) {
	}

	private final List<Filter> filters_ = new ArrayList<>();
	private int filter_;
	private boolean info_;
	/** alpha 49: the pictures below the notes of the info: two or three, chosen by chance when it opens, never box front or back */
	private List<File> infoShots_ = List.of();
	/** alpha 49: the menu at the left (Y/F): which games and in which order */
	private boolean menu_;
	private int menuIndex_, menuFirst_;
	private List<MenuEntry> menuItems_ = List.of();
	/** how the games are sorted: 0 as in the list of the main window, then SORTS */
	private int sort_;
	private static final String[] SORTS = {"list", "title", "year", "lastplayed", "playtime"};

	/** a line of the menu: 0 a heading, 1 a filter (value: its index), 2 an order (value: index of SORTS) */
	private record MenuEntry(int kind, int value, String label) {
	}

	private int index_, firstRow_;
	private Gamepad pad_;

	private TvMode(Shell parent, List<Profile> profiles, Profile current, Consumer<Profile> runner) {
		Display display = parent.getDisplay();
		all_ = new ArrayList<>(profiles);
		runner_ = runner;
		shown_ = all_;
		filters_.add(new Filter("dialog.tv.all", null));
		filters_.add(new Filter("dialog.tv.favorites", null));
		all_.stream().map(p -> StringUtils.trimToEmpty(p.getGenre())).filter(StringUtils::isNotEmpty).distinct().sorted(String.CASE_INSENSITIVE_ORDER)
				.forEach(g -> filters_.add(new Filter("dialog.tv.genre", g)));
		// the filter and the game of the last time, else the game chosen in the list
		org.dbgl.service.SettingsService settings = org.dbgl.service.SettingsService.getInstance();
		String lastFilter = StringUtils.defaultString(settings.getValue("gui", "tvfilter"));
		for (int i = 0; i < filters_.size(); i++)
			if (lastFilter.equals(filterName(filters_.get(i))))
				filter_ = i;
		try {
			sort_ = Math.max(0, Math.min(SORTS.length - 1, settings.getIntValue("gui", "tvsort")));
		} catch (RuntimeException e) {
			sort_ = 0;
		}
		applyFilter();
		Profile last = null;
		for (Profile p: filtered_)
			if (String.valueOf(p.getId()).equals(settings.getValue("gui", "tvlastprofile")))
				last = p;
		index_ = Math.max(0, indexOfOrStack((last != null) ? last: current));

		shell_ = new Shell(parent, SWT.NO_TRIM); // above the main window, also after a game (alpha 48)
		shell_.setText(text_.get("dialog.tv.title"));
		shell_.setImages(parent.getImages());
		shell_.setLayout(new FillLayout());
		bg_ = new Color(display, 18, 18, 20);
		tile_ = new Color(display, 40, 40, 44);
		selected_ = new Color(display, 168, 78, 70); // the red of the banderole, darker: white on it 6:1 (WCAG AA)
		fg_ = new Color(display, 236, 236, 236);
		dim_ = new Color(display, 150, 150, 155);
		FontData base = display.getSystemFont().getFontData()[0];
		big_ = new Font(display, base.getName(), Math.round(base.getHeight() * 2.4f), SWT.BOLD);
		medium_ = new Font(display, base.getName(), Math.round(base.getHeight() * 1.5f), SWT.BOLD);
		small_ = new Font(display, base.getName(), Math.round(base.getHeight() * 1.25f), SWT.NORMAL);

		canvas_ = new Canvas(shell_, SWT.DOUBLE_BUFFERED | SWT.NO_BACKGROUND);
		canvas_.addListener(SWT.Paint, this::paint);
		canvas_.addListener(SWT.KeyDown, this::key);
		canvas_.addListener(SWT.Resize, e -> canvas_.redraw());
		canvas_.addListener(SWT.MouseDown, this::click);
		canvas_.addListener(SWT.MouseDoubleClick, e -> start());
		canvas_.addListener(SWT.MouseVerticalWheel, e -> move(0, e.count > 0 ? -1: 1));
		// back to the front when DBGL is active again after a game
		org.eclipse.swt.widgets.Listener toFront = e -> display.asyncExec(() -> {
			if (!shell_.isDisposed() && display.getActiveShell() != shell_ && (display.getActiveShell() == null || display.getActiveShell() == parent)) {
				shell_.forceActive();
				canvas_.setFocus();
			}
		});
		parent.addListener(SWT.Activate, toFront);
		shell_.addListener(SWT.Dispose, e -> parent.removeListener(SWT.Activate, toFront));
		shell_.addListener(SWT.Dispose, e -> {
			org.dbgl.service.SettingsService store = org.dbgl.service.SettingsService.getInstance();
			store.setValue("gui", "tvfilter", filterName(filters_.get(filter_)));
			store.setIntValue("gui", "tvsort", sort_);
			if (!shown_.isEmpty())
				store.setValue("gui", "tvlastprofile", String.valueOf(shown_.get(index_).getId()));
			if (pad_ != null)
				pad_.stop();
			for (Object r: new Object[] {bg_, tile_, selected_, fg_, dim_, big_, medium_, small_})
				if (r instanceof org.eclipse.swt.graphics.Resource res)
					res.dispose();
		});
	}

	/** shows the TV mode until it is left; the runner starts a profile (as the main window does) */
	public static void open(Shell parent, List<Profile> profiles, Profile current, Consumer<Profile> runner) {
		TvMode tv = new TvMode(parent, profiles, current, runner);
		tv.shell_.setBounds(parent.getMonitor().getBounds());
		tv.shell_.open();
		tv.shell_.setFullScreen(true);
		tv.canvas_.setFocus();
		Display display = parent.getDisplay();
		tv.pad_ = Gamepad.start(b -> display.asyncExec(() -> {
			if (!tv.shell_.isDisposed() && display.getActiveShell() == tv.shell_) // not while a game runs
				tv.press(b);
		}));
		while (!tv.shell_.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
	}

	private void press(Gamepad.Button b) {
		if (menu_) {
			switch (b) {
				case UP -> menuMove(-1);
				case DOWN -> menuMove(1);
				case LB -> menuSection(-1);
				case RB -> menuSection(1);
				case A, START -> menuChoose();
				case Y -> menuAll();
				default -> closeMenu();
			}
			return;
		}
		if (info_ && b != Gamepad.Button.A && b != Gamepad.Button.START) {
			// alpha 49: up/down (left/right) show the info of the game before or after; any other button closes the info
			if ((b == Gamepad.Button.UP) || (b == Gamepad.Button.LEFT)) {
				infoStep(b == Gamepad.Button.UP ? -columns(): -1);
			} else if ((b == Gamepad.Button.DOWN) || (b == Gamepad.Button.RIGHT)) {
				infoStep(b == Gamepad.Button.DOWN ? columns(): 1);
			} else {
				info_ = false;
				canvas_.redraw();
			}
			return;
		}
		switch (b) {
			case UP -> move(0, -1);
			case DOWN -> move(0, 1);
			case LEFT -> move(-1, 0);
			case RIGHT -> move(1, 0);
			case A, START -> start();
			case B -> back();
			case BACK -> random();
			case X -> toggleInfo();
			case Y -> openMenu();
			case LB -> move(0, -visibleRows());
			case RB -> move(0, visibleRows());
			default -> {
				// nothing
			}
		}
	}

	private void key(Event e) {
		if (menu_) {
			switch (e.keyCode) {
				case SWT.ARROW_UP -> menuMove(-1);
				case SWT.ARROW_DOWN -> menuMove(1);
				case SWT.PAGE_UP -> menuSection(-1);
				case SWT.PAGE_DOWN -> menuSection(1);
				case SWT.HOME -> {
					menuIndex_ = 0;
					menuMove(1);
				}
				case SWT.END -> {
					menuIndex_ = menuItems_.size() - 1;
					canvas_.redraw();
				}
				case SWT.CR, SWT.KEYPAD_CR, ' ' -> menuChoose();
				case SWT.TAB, 'f', 'F' -> menuAll();
				case SWT.F11 -> shell_.close();
				default -> closeMenu();
			}
			e.doit = false;
			return;
		}
		if (info_ && e.keyCode != SWT.CR && e.keyCode != SWT.KEYPAD_CR && e.keyCode != ' ') {
			if ((e.keyCode == SWT.ARROW_UP) || (e.keyCode == SWT.ARROW_LEFT)) {
				infoStep(e.keyCode == SWT.ARROW_UP ? -columns(): -1);
			} else if ((e.keyCode == SWT.ARROW_DOWN) || (e.keyCode == SWT.ARROW_RIGHT)) {
				infoStep(e.keyCode == SWT.ARROW_DOWN ? columns(): 1);
			} else {
				info_ = false;
				canvas_.redraw();
			}
			e.doit = false;
			return;
		}
		switch (e.keyCode) {
			case SWT.ARROW_UP -> move(0, -1);
			case SWT.ARROW_DOWN -> move(0, 1);
			case SWT.ARROW_LEFT -> move(-1, 0);
			case SWT.ARROW_RIGHT -> move(1, 0);
			case SWT.PAGE_UP -> move(0, -visibleRows());
			case SWT.PAGE_DOWN -> move(0, visibleRows());
			case SWT.HOME -> select(0);
			case SWT.END -> select(shown_.size() - 1);
			case SWT.CR, SWT.KEYPAD_CR, ' ' -> start();
			case SWT.ESC, SWT.BS -> back();
			case SWT.F11 -> shell_.close();
			case SWT.TAB, 'f', 'F' -> openMenu();
			case 'i', 'I' -> toggleInfo();
			case 'z', 'Z' -> random();
			default -> {
				return;
			}
		}
		e.doit = false;
	}

	private void click(Event e) {
		if (menu_) { // a line of the menu is chosen, a click next to it closes the menu
			Rectangle panel = menuBounds(canvas_.getClientArea());
			int row = panel.contains(e.x, e.y) ? menuFirst_ + (e.y - panel.y - MENU_PAD) / menuRowHeight(): -1;
			if ((row >= 0) && (row < menuItems_.size()) && (e.y >= panel.y + MENU_PAD) && (menuItems_.get(row).kind() != 0)) {
				menuIndex_ = row;
				menuChoose();
			} else {
				closeMenu();
			}
			return;
		}
		int i = indexAt(e.x, e.y);
		if (i >= 0)
			select(i);
	}

	private static String filterName(Filter f) {
		return (f.genre() == null) ? f.key(): "genre:" + f.genre();
	}

	private List<Profile> matching(Filter f) {
		if (f.genre() != null)
			return all_.stream().filter(p -> f.genre().equalsIgnoreCase(StringUtils.trimToEmpty(p.getGenre()))).toList();
		if (f.key().equals("dialog.tv.favorites"))
			return all_.stream().filter(p -> Boolean.TRUE.equals(p.isFavorite())).toList();
		return all_;
	}

	private void applyFilter() {
		filtered_ = sorted(matching(filters_.get(filter_)), sort_);
		openSeries_ = null;
		arrange();
	}

	/** alpha 49: the games in the order chosen in the menu: 0 as given, 1 title, 2 year, 3 played last first, 4 played longest first */
	static List<Profile> sorted(List<Profile> list, int sort) {
		java.util.Comparator<Profile> byTitle = java.util.Comparator.comparing(p -> StringUtils.defaultString(p.getTitle()), String.CASE_INSENSITIVE_ORDER);
		java.util.Comparator<Profile> order;
		switch (sort) {
			case 1:
				order = byTitle;
				break;
			case 2:
				order = java.util.Comparator.<Profile>comparingInt(TvMode::yearOf).thenComparing(byTitle);
				break;
			case 3:
				order = java.util.Comparator.<Profile>comparingLong(p -> -lastRunOf(p)).thenComparing(byTitle);
				break;
			case 4:
				order = java.util.Comparator.<Profile>comparingInt(p -> -playTimeOf(p)).thenComparing(byTitle);
				break;
			default:
				return list;
		}
		List<Profile> result = new ArrayList<>(list);
		result.sort(order);
		return result;
	}

	/** the year of a game as a number; games without one come last */
	static int yearOf(Profile p) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{4}").matcher(StringUtils.defaultString(p.getYear()));
		return m.find() ? Integer.parseInt(m.group()): Integer.MAX_VALUE;
	}

	private static long lastRunOf(Profile p) {
		return (p.getStats() != null) && (p.getStats().getLastRun() != null) ? p.getStats().getLastRun().getTime(): 0;
	}

	private static int playTimeOf(Profile p) {
		return p.getProfileStats() != null ? p.getProfileStats().getPlayTime(): 0;
	}

	// ----- alpha 49: the menu at the left: all games and the favourites at the top, the orders, then the genres -----

	private static final int MENU_PAD = 22;

	private void openMenu() {
		info_ = false;
		List<MenuEntry> items = new ArrayList<>();
		items.add(new MenuEntry(0, 0, text_.get("dialog.tv.menu.show")));
		for (int i = 0; i < Math.min(2, filters_.size()); i++)
			items.add(new MenuEntry(1, i, filterLabel(i)));
		items.add(new MenuEntry(0, 0, text_.get("dialog.tv.menu.sort")));
		for (int i = 0; i < SORTS.length; i++)
			items.add(new MenuEntry(2, i, text_.get("dialog.tv.sort." + SORTS[i])));
		if (filters_.size() > 2) {
			items.add(new MenuEntry(0, 0, text_.get("dialog.tv.menu.genres")));
			for (int i = 2; i < filters_.size(); i++)
				items.add(new MenuEntry(1, i, filterLabel(i)));
		}
		menuItems_ = items;
		menuIndex_ = 1;
		for (int i = 0; i < items.size(); i++)
			if ((items.get(i).kind() == 1) && (items.get(i).value() == filter_))
				menuIndex_ = i; // at what is shown now
		menuFirst_ = 0;
		menu_ = true;
		canvas_.redraw();
	}

	private String filterLabel(int i) {
		Filter f = filters_.get(i);
		int n = matching(f).size();
		return (f.genre() == null) ? text_.get(f.key(), new Object[] {n}): text_.get("dialog.tv.genre", new Object[] {f.genre(), n});
	}

	private void closeMenu() {
		menu_ = false;
		canvas_.redraw();
	}

	private void menuMove(int step) {
		int n = menuItems_.size(), i = menuIndex_;
		do {
			i = (i + step + n) % n;
		} while (menuItems_.get(i).kind() == 0);
		menuIndex_ = i;
		canvas_.redraw();
	}

	/** to the first line of the next (or the previous) part of the menu */
	private void menuSection(int step) {
		int n = menuItems_.size(), i = menuIndex_;
		if (step > 0) {
			while ((i < n) && (menuItems_.get(i).kind() != 0))
				i++;
			menuIndex_ = (i >= n - 1) ? 1: i + 1;
		} else {
			while ((i > 0) && (menuItems_.get(i).kind() != 0))
				i--; // the heading of this part
			i--;
			while ((i > 0) && (menuItems_.get(i - 1).kind() != 0))
				i--; // the first line of the part before
			menuIndex_ = (i < 1) ? 1: i;
		}
		canvas_.redraw();
	}

	/** Y/F again: back to all games, from wherever one is */
	private void menuAll() {
		menu_ = false;
		showFilter(0);
	}

	private void menuChoose() {
		MenuEntry e = menuItems_.get(menuIndex_);
		menu_ = false;
		if (e.kind() == 2)
			sort_ = e.value();
		showFilter(e.kind() == 1 ? e.value(): filter_);
	}

	private void showFilter(int filter) {
		Profile current = shown_.isEmpty() ? null: shown_.get(index_);
		filter_ = filter;
		applyFilter();
		index_ = Math.max(0, indexOfOrStack(current));
		firstRow_ = 0;
		select(index_);
		canvas_.redraw();
	}

	private Rectangle menuBounds(Rectangle area) {
		int top = HEADER_H - 14;
		return new Rectangle(0, top, Math.max(360, Math.min(560, area.width / 3)), area.height - top - FOOTER_H - 6);
	}

	private int menuRowHeight() {
		GC gc = new GC(canvas_);
		try {
			gc.setFont(small_);
			return gc.textExtent("Ag").y + 14;
		} finally {
			gc.dispose();
		}
	}

	private void paintMenu(GC gc, Rectangle area) {
		Rectangle panel = menuBounds(area);
		gc.setBackground(tile_);
		gc.fillRectangle(panel);
		gc.setForeground(selected_);
		gc.setLineWidth(3);
		gc.drawLine(panel.x + panel.width, panel.y, panel.x + panel.width, panel.y + panel.height);
		gc.setFont(small_);
		int rowH = gc.textExtent("Ag").y + 14, hintH = rowH + 8;
		int rows = Math.max(1, (panel.height - 2 * MENU_PAD - hintH) / rowH);
		// the chosen line in view, with the heading of its part if it is the first one of it
		if (menuIndex_ < menuFirst_)
			menuFirst_ = menuIndex_;
		if ((menuIndex_ > 0) && (menuItems_.get(menuIndex_ - 1).kind() == 0) && (menuIndex_ - 1 < menuFirst_))
			menuFirst_ = menuIndex_ - 1;
		if (menuIndex_ >= menuFirst_ + rows)
			menuFirst_ = menuIndex_ - rows + 1;
		int x = panel.x + MARGIN / 2, w = panel.width - MARGIN, y = panel.y + MENU_PAD;
		for (int i = menuFirst_; (i < menuItems_.size()) && (i < menuFirst_ + rows); i++) {
			MenuEntry e = menuItems_.get(i);
			if (e.kind() == 0) {
				gc.setForeground(dim_);
				gc.drawText(shorten(gc, e.label().toUpperCase(), w), x, y + 7, true);
				gc.setLineWidth(1);
				gc.drawLine(x, y + rowH - 2, x + w, y + rowH - 2);
			} else {
				if (i == menuIndex_) {
					gc.setBackground(selected_);
					gc.fillRoundRectangle(x - 10, y, w + 20, rowH - 2, 12, 12);
				}
				boolean current = (e.kind() == 1) ? e.value() == filter_: e.value() == sort_;
				gc.setForeground(fg_);
				if (current) {
					gc.setBackground(fg_);
					gc.fillOval(x + 4, y + rowH / 2 - 5, 9, 9);
				}
				gc.drawText(shorten(gc, e.label(), w - 28), x + 28, y + 7, true);
			}
			y += rowH;
		}
		gc.setForeground(dim_);
		gc.drawText(shorten(gc, text_.get("dialog.tv.menu.hint"), w), x, panel.y + panel.height - MENU_PAD - rowH + 10, true);
	}

	private static String seriesOf(Profile p) {
		return StringUtils.trimToEmpty(p.getSeries());
	}

	/** by part (those without one last), then by title */
	private static final java.util.Comparator<Profile> BY_PART = java.util.Comparator.<Profile>comparingInt(p -> p.getSeriesPart() > 0 ? p.getSeriesPart(): Integer.MAX_VALUE)
			.thenComparing(p -> StringUtils.defaultString(p.getTitle()), String.CASE_INSENSITIVE_ORDER);

	/**
	 * alpha 49: what is on screen. The parts of the open series, by part; else all games, where two or more of one series are
	 * one stack (its first part, at the place of the first of them in the list).
	 */
	private void arrange() {
		stackSize_.clear();
		stackAnchor_.clear();
		if (openSeries_ != null) {
			shown_ = filtered_.stream().filter(p -> openSeries_.equalsIgnoreCase(seriesOf(p))).sorted(BY_PART).toList();
			return;
		}
		if (!stacks_) {
			shown_ = filtered_;
			return;
		}
		java.util.Map<String, List<Profile>> series = new java.util.HashMap<>();
		for (Profile p: filtered_)
			if (!seriesOf(p).isEmpty())
				series.computeIfAbsent(seriesOf(p).toLowerCase(java.util.Locale.ROOT), k -> new ArrayList<>()).add(p);
		List<Profile> result = new ArrayList<>();
		java.util.Set<String> placed = new java.util.HashSet<>();
		for (Profile p: filtered_) {
			String key = seriesOf(p).toLowerCase(java.util.Locale.ROOT);
			List<Profile> members = key.isEmpty() ? null: series.get(key);
			if ((members == null) || (members.size() < 2)) {
				result.add(p);
			} else if (placed.add(key)) {
				Profile first = members.stream().min(BY_PART).get();
				result.add(first);
				stackSize_.put(first.getId(), members.size());
				stackAnchor_.put(first.getId(), anchorTitle(members));
			}
		}
		// in a list sorted by title a stack stands where one would look for the series: at the title most of its games start
		// like ("Ultima" under U, not under P for "Pagan: Ultima VIII"); in any other order it keeps the place of its first game
		java.util.function.Function<Profile, String> label = p -> stackSize_.containsKey(p.getId()) ? stackAnchor_.get(p.getId()): StringUtils.defaultString(p.getTitle());
		java.util.Comparator<Profile> byTitle = (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(StringUtils.defaultString(a.getTitle()), StringUtils.defaultString(b.getTitle()));
		java.util.Comparator<Profile> byLabel = (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(label.apply(a), label.apply(b));
		if (!stackSize_.isEmpty()) {
			if (ordered(filtered_, byTitle))
				result.sort(byLabel);
			else if (ordered(filtered_, byTitle.reversed()))
				result.sort(byLabel.reversed());
		}
		shown_ = result;
	}

	/**
	 * the title a stack is sorted by: of the games that start with the word most of the series start with, the earliest one (by
	 * year, then by part) - the name known longest, not a later part that is called differently
	 */
	private static String anchorTitle(List<Profile> members) {
		java.util.function.Function<Profile, String> start = p -> {
			String[] w = StringUtils.defaultString(p.getTitle()).strip().toLowerCase(java.util.Locale.ROOT).replaceFirst("^(the|der|die|das)\\s+", "").split("[^\\p{L}\\p{N}]+", 2);
			return w.length == 0 ? "": w[0];
		};
		java.util.Map<String, Integer> count = new java.util.HashMap<>();
		for (Profile p: members)
			count.merge(start.apply(p), 1, Integer::sum);
		int most = java.util.Collections.max(count.values());
		java.util.function.ToIntFunction<Profile> year = p -> {
			String y = StringUtils.trimToEmpty(p.getYear());
			return y.matches("[0-9]{4}") ? Integer.parseInt(y): Integer.MAX_VALUE;
		};
		return members.stream().filter(p -> count.get(start.apply(p)) == most).min(java.util.Comparator.comparingInt(year).thenComparing(BY_PART)).map(p -> StringUtils.defaultString(p.getTitle())).orElse("");
	}

	private static boolean ordered(List<Profile> list, java.util.Comparator<Profile> order) {
		for (int i = 1; i < list.size(); i++)
			if (order.compare(list.get(i - 1), list.get(i)) > 0)
				return false;
		return true;
	}

	/** where a game is on screen: itself, or the stack of its series */
	private int indexOfOrStack(Profile p) {
		if (p == null)
			return -1;
		int i = shown_.indexOf(p);
		if ((i >= 0) || seriesOf(p).isEmpty())
			return i;
		for (int k = 0; k < shown_.size(); k++)
			if (stackSize_.containsKey(shown_.get(k).getId()) && seriesOf(shown_.get(k)).equalsIgnoreCase(seriesOf(p)))
				return k;
		return -1;
	}

	private boolean isStack(Profile p) {
		return (openSeries_ == null) && stackSize_.containsKey(p.getId());
	}

	/** B/Esc: out of the open series, back to its stack; else the TV mode ends */
	private void back() {
		if (openSeries_ == null) {
			shell_.close();
			return;
		}
		Profile current = shown_.isEmpty() ? null: shown_.get(index_);
		openSeries_ = null;
		infoInSeries_ = false;
		arrange();
		firstRow_ = 0;
		select(Math.max(0, indexOfOrStack(current)));
	}

	private void random() {
		if (openSeries_ != null) {
			if (shown_.size() > 1)
				select(new java.util.Random().nextInt(shown_.size()));
			return;
		}
		// alpha 49: every game with the same chance, also those in a stack: its series opens with the game chosen
		if (filtered_.size() < 2)
			return;
		Profile p = filtered_.get(new java.util.Random().nextInt(filtered_.size()));
		int at = shown_.indexOf(p);
		if ((at < 0) || isStack(p)) {
			openSeries_ = seriesOf(p);
			arrange();
			firstRow_ = 0;
			info_ = false;
			at = Math.max(0, shown_.indexOf(p));
		}
		select(at);
	}

	/** the open series was entered with the info open: at its end the info goes on with what follows its stack */
	private boolean infoInSeries_;

	/**
	 * alpha 49: with the info open, on to another game: left/right the one before or after, up/down the one above or below
	 * (test 15: up and down went left and right). A stack of a series opens, and the info goes through its parts and out of
	 * it again at its end (test 15; before, a stack was passed over). At the first and the last game nothing happens.
	 *
	 * @param step -1 and 1, or a row up or down (the number of columns)
	 */
	private void infoStep(int step) {
		if (shown_.isEmpty())
			return;
		int i = index_ + step;
		if ((i < 0) || (i >= shown_.size())) {
			if ((openSeries_ == null) || !infoInSeries_)
				return;
			// out of the series: on with the game next to its stack
			Profile current = shown_.get(index_);
			String series = openSeries_;
			openSeries_ = null;
			arrange();
			firstRow_ = 0;
			int stack = Math.max(0, indexOfOrStack(current));
			i = stack + (step > 0 ? 1: -1);
			if ((i < 0) || (i >= shown_.size())) { // the stack is the first or the last one: the series stays open
				openSeries_ = series;
				arrange();
				select(Math.max(0, shown_.indexOf(current)));
				return;
			}
			infoInSeries_ = false;
		}
		if (isStack(shown_.get(i))) { // into the series: its first part, coming from behind its last one
			openSeries_ = seriesOf(shown_.get(i));
			infoInSeries_ = true;
			arrange();
			firstRow_ = 0;
			i = step > 0 ? 0: shown_.size() - 1;
		}
		infoShots_ = randomShots(shown_.get(i), new java.util.Random());
		select(i);
	}

	private void toggleInfo() {
		info_ = !info_ && !shown_.isEmpty() && !isStack(shown_.get(index_)); // a stack has no notes of its own
		infoShots_ = info_ ? randomShots(shown_.get(index_), new java.util.Random()): List.of();
		canvas_.redraw();
	}

	private void start() {
		if (shown_.isEmpty())
			return;
		Profile p = shown_.get(index_);
		if (isStack(p)) { // alpha 49: a stack opens: the parts of the series
			openSeries_ = seriesOf(p);
			arrange();
			firstRow_ = 0;
			info_ = false;
			select(0);
			return;
		}
		runner_.accept(p);
	}

	private int columns() {
		int w = canvas_.getClientArea().width - 2 * MARGIN;
		return Math.max(1, (w + GAP) / (tileW() + GAP));
	}

	private int visibleRows() {
		int h = canvas_.getClientArea().height - HEADER_H - FOOTER_H;
		return Math.max(1, (h + GAP) / (coverH() + TITLE_H + GAP));
	}

	private void move(int dx, int dy) {
		if (shown_.isEmpty())
			return;
		int cols = columns();
		int i = index_ + dx + dy * cols;
		if (dx != 0 && (index_ / cols) != (Math.max(0, Math.min(shown_.size() - 1, i)) / cols) && Math.abs(dx) == 1)
			i = index_ + dx; // left and right also go on to the next row
		select(Math.max(0, Math.min(shown_.size() - 1, i)));
	}

	private void select(int i) {
		if (shown_.isEmpty())
			return;
		index_ = Math.max(0, Math.min(shown_.size() - 1, i));
		int row = index_ / columns(), rows = visibleRows();
		if (row < firstRow_)
			firstRow_ = row;
		else if (row >= firstRow_ + rows)
			firstRow_ = row - rows + 1;
		canvas_.redraw();
	}

	private Rectangle tileBounds(int i) {
		int cols = columns();
		int gridW = cols * tileW() + (cols - 1) * GAP;
		int x0 = (canvas_.getClientArea().width - gridW) / 2;
		int row = i / cols - firstRow_, col = i % cols;
		return new Rectangle(x0 + col * (tileW() + GAP), HEADER_H + row * (coverH() + TITLE_H + GAP), tileW(), coverH() + TITLE_H);
	}

	private int indexAt(int x, int y) {
		int from = firstRow_ * columns(), to = Math.min(shown_.size(), from + visibleRows() * columns());
		for (int i = from; i < to; i++)
			if (tileBounds(i).contains(x, y))
				return i;
		return -1;
	}

	private void paint(Event e) {
		GC gc = e.gc;
		Rectangle area = canvas_.getClientArea();
		gc.setAntialias(SWT.ON);
		gc.setTextAntialias(SWT.ON);
		gc.setInterpolation(SWT.HIGH);
		gc.setBackground(bg_);
		gc.fillRectangle(area);

		// header: the name of DBGL, which games
		gc.setForeground(fg_);
		gc.setFont(big_);
		// alpha 48: the full name, the version small next to it
		String name = "DOSBox Game Launcher - " + org.dbgl.constants.Constants.EDITION_NAME;
		gc.drawText(name, MARGIN, 24, true);
		Point nameSize = gc.textExtent(name);
		gc.setFont(small_);
		gc.setForeground(dim_);
		Point versionSize = gc.textExtent("Ag");
		gc.drawText(org.dbgl.util.EditionUpdateChecker.readable(org.dbgl.constants.Constants.PROGRAM_VERSION), MARGIN + nameSize.x + 16, 24 + nameSize.y - versionSize.y - 4, true);
		gc.setForeground(fg_);
		gc.setFont(medium_);
		Filter f = filters_.get(filter_);
		String which = (f.genre() == null) ? text_.get(f.key(), new Object[] {filtered_.size()}): text_.get("dialog.tv.genre", new Object[] {f.genre(), filtered_.size()});
		if (openSeries_ != null) // alpha 49: in a series: its name and how many parts
			which = text_.get("dialog.tv.genre", new Object[] {shown_.isEmpty() ? openSeries_: seriesOf(shown_.get(0)), shown_.size()});
		Point w = gc.textExtent(which);
		gc.setForeground(dim_);
		gc.drawText(which, area.width - MARGIN - w.x, 36, true);

		if (shown_.isEmpty()) {
			gc.setFont(medium_);
			String none = text_.get("dialog.tv.empty");
			Point p = gc.textExtent(none);
			gc.drawText(none, (area.width - p.x) / 2, area.height / 2 - p.y, true);
		} else {
			int cols = columns(), from = firstRow_ * cols, to = Math.min(shown_.size(), from + visibleRows() * cols);
			for (int i = from; i < to; i++)
				paintTile(gc, shown_.get(i), tileBounds(i), i == index_);
			paintScrollBar(gc, area, cols);
			paintFooter(gc, area, shown_.get(index_));
			if (info_)
				paintInfo(gc, area, shown_.get(index_));
		}
		if (menu_)
			paintMenu(gc, area);
	}

	/** alpha 49: a thin bar at the right that shows where one is in the list, when not all rows fit on the screen */
	private void paintScrollBar(GC gc, Rectangle area, int cols) {
		int rows = (shown_.size() + cols - 1) / cols, visible = visibleRows();
		if (rows <= visible)
			return;
		int top = HEADER_H, height = area.height - HEADER_H - FOOTER_H - 10, x = area.width - 22, w = 6;
		gc.setBackground(tile_);
		gc.fillRoundRectangle(x, top, w, height, w, w);
		int thumb = Math.max(40, height * visible / rows);
		int y = top + (height - thumb) * Math.min(firstRow_, rows - visible) / Math.max(1, rows - visible);
		gc.setBackground(dim_);
		gc.fillRoundRectangle(x, y, w, thumb, w, w);
	}

	/** X/I: the notes of the game and a screenshot, over the covers */
	private void paintInfo(GC gc, Rectangle area, Profile p) {
		// between the header and the footer (the title and the hints stay visible)
		int room = area.height - 80 - FOOTER_H;
		int w = Math.min(area.width - 2 * MARGIN, 1300), h = Math.min(room, 760);
		int x = (area.width - w) / 2, y = 75 + (room - h) / 2;
		gc.setBackground(tile_);
		gc.fillRoundRectangle(x, y, w, h, 24, 24);
		gc.setForeground(selected_);
		gc.setLineWidth(3);
		gc.drawRoundRectangle(x, y, w, h, 24, 24);
		int pad = 36, textX = x + pad, textW = w - 2 * pad;
		File shot = screenshotOf(p);
		if (shot != null) {
			try {
				int iw = w * 2 / 5, ih = h - 2 * pad;
				Image img = fitted(shot, iw, ih); // alpha 48: whole (a back of a box was cut before), centred in its room
				Rectangle ib = img.getBounds();
				gc.drawImage(img, x + w - pad - iw + (iw - ib.width) / 2, y + pad + (ih - ib.height) / 2);
				textW = w - 3 * pad - iw;
			} catch (RuntimeException ex) {
				// text only
			}
		}
		gc.setForeground(fg_);
		gc.setFont(medium_);
		gc.drawText(shorten(gc, StringUtils.defaultString(p.getTitle()), textW), textX, y + pad, true);
		gc.setFont(small_);
		int lineH = gc.textExtent("Ag").y, ty = y + pad + gc.textExtent("Ag").y * 2;
		String notes = StringUtils.defaultIfBlank(p.getNotes(), text_.get("dialog.tv.nonotes"));
		List<String> lines = wrap(gc, notes, textW);
		// alpha 49: room left below the notes: two or three screenshots of the game there
		int bottom = y + h - pad, free = bottom - ty - lines.size() * (lineH + 4);
		int shotsH = !infoShots_.isEmpty() && (free >= 170) ? Math.min(free - 24, 280): 0;
		if (shotsH > 0)
			bottom -= shotsH + 24;
		for (String line: lines) {
			if (ty + lineH > bottom)
				break;
			gc.drawText(line, textX, ty, true);
			ty += lineH + 4;
		}
		if (shotsH > 0) {
			int gap = 16, slotW = (textW - 2 * gap) / 3, sx = textX, sy = y + h - pad - shotsH;
			for (File f: infoShots_) {
				try {
					Image img = fitted(f, slotW, shotsH);
					Rectangle ib = img.getBounds();
					gc.drawImage(img, sx, sy + (shotsH - ib.height) / 2);
					sx += ib.width + gap;
				} catch (RuntimeException ex) {
					// without this one
				}
			}
		}
	}

	/** alpha 49: up to three pictures of the game by chance, never the first two (the front and the back of the box) */
	private static List<File> randomShots(Profile p, java.util.Random random) {
		File[] images = p.getCanonicalCaptures().listFiles(f -> f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpe?g)"));
		if ((images == null) || (images.length <= 2))
			return List.of();
		java.util.Arrays.sort(images);
		List<File> rest = new ArrayList<>(java.util.Arrays.asList(images).subList(2, images.length));
		java.util.Collections.shuffle(rest, random);
		return new ArrayList<>(rest.subList(0, Math.min(3, rest.size())));
	}

	private static List<String> wrap(GC gc, String text, int width) {
		List<String> lines = new ArrayList<>();
		for (String para: text.replace("\r", "").split("\n")) {
			StringBuilder line = new StringBuilder();
			for (String word: para.split(" ")) {
				String next = line.length() == 0 ? word: line + " " + word;
				if (gc.textExtent(next).x > width && line.length() > 0) {
					lines.add(line.toString());
					line = new StringBuilder(word);
				} else {
					line = new StringBuilder(next);
				}
			}
			lines.add(line.toString());
		}
		return lines;
	}

	/** a picture of the game other than its cover (the second one), or null */
	private File screenshotOf(Profile p) {
		File cover = coverOf(p);
		File[] images = p.getCanonicalCaptures().listFiles(f -> f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpe?g)"));
		if (images == null)
			return null;
		java.util.Arrays.sort(images);
		for (File f: images)
			if (!f.equals(cover))
				return f;
		return null;
	}

	private void paintTile(GC gc, Profile p, Rectangle r, boolean selected) {
		if (selected) {
			gc.setBackground(selected_);
			gc.fillRoundRectangle(r.x - 8, r.y - 8, r.width + 16, r.height + 16, 18, 18);
		}
		boolean stack = isStack(p);
		if (stack) { // alpha 49: two more covers peeking out behind it, top right
			for (int k = 2; k >= 1; k--) {
				gc.setBackground(k == 2 ? stackFar_: stackNear_);
				gc.fillRectangle(r.x + 7 * k, r.y - 6 * k, r.width, coverH());
			}
		}
		gc.setBackground(tile_);
		gc.fillRectangle(r.x, r.y, r.width, coverH());
		File cover = coverOf(p);
		if (cover != null) {
			try {
				// alpha 48: whole and stretched to fill its field (before cut, then with bands beside it)
				Image img = ImageService.getCachedResizedImage(canvas_.getDisplay(), tileW(), coverH(), false, cover.getPath());
				gc.drawImage(img, r.x, r.y);
			} catch (RuntimeException ex) {
				// no picture then
			}
		} else {
			gc.setFont(medium_);
			gc.setForeground(dim_);
			String initial = StringUtils.left(StringUtils.defaultString(p.getTitle()).trim(), 1).toUpperCase();
			Point s = gc.textExtent(initial);
			gc.drawText(initial, r.x + (tileW() - s.x) / 2, r.y + (coverH() - s.y) / 2, true);
		}
		gc.setFont(small_);
		if (stack) { // how many parts, on the cover
			String count = String.valueOf(stackSize_.get(p.getId()));
			Point cs = gc.textExtent(count);
			int bw = Math.max(cs.x + 16, cs.y + 6), bh = cs.y + 6;
			gc.setBackground(selected_);
			gc.fillRoundRectangle(r.x + tileW() - bw - 8, r.y + 8, bw, bh, bh, bh);
			gc.setForeground(fg_);
			gc.drawText(count, r.x + tileW() - bw - 8 + (bw - cs.x) / 2, r.y + 8 + 3, true);
		}
		gc.setForeground(selected ? fg_: dim_);
		String title = shorten(gc, stack ? seriesOf(p): StringUtils.defaultString(p.getTitle()), tileW());
		Point s = gc.textExtent(title);
		gc.drawText(title, r.x + (tileW() - s.x) / 2, r.y + coverH() + 12, true);
	}

	private void paintFooter(GC gc, Rectangle area, Profile p) {
		int y = area.height - FOOTER_H + 10;
		gc.setForeground(fg_);
		gc.setFont(medium_);
		boolean stack = isStack(p); // alpha 49: a stack: the name of the series and the titles of its parts
		gc.drawText(shorten(gc, stack ? seriesOf(p): StringUtils.defaultString(p.getTitle()), area.width - 2 * MARGIN), MARGIN, y, true);
		gc.setFont(small_);
		gc.setForeground(dim_);
		List<String> facts = new ArrayList<>();
		if (stack) {
			filtered_.stream().filter(m -> seriesOf(m).equalsIgnoreCase(seriesOf(p))).sorted(BY_PART).forEach(m -> facts.add(StringUtils.defaultString(m.getTitle())));
		} else {
			for (String f: new String[] {p.getYear(), p.getGenre(), p.getDeveloper()})
				if (StringUtils.isNotBlank(f))
					facts.add(f.trim());
		}
		String line = String.join("  ·  ", facts);
		// alpha 48: can be turned off (settings, GUI, TV mode)
		String play = !stack && org.dbgl.service.SettingsService.getInstance().getBooleanValue("gui", "showplaytimeintv") ? ProfileReports.playInfo(p, text_): "";
		// alpha 49: before it the disk space of the game folder, once it was found
		String disk = !stack && org.dbgl.service.SettingsService.getInstance().getBooleanValue("gui", "showdiskspaceintv") ? org.dbgl.util.DiskUsage.line(p, text_): "";
		if (StringUtils.isNotBlank(disk))
			play = StringUtils.isBlank(play) ? disk: disk + "     " + play;
		if (StringUtils.isNotBlank(play))
			line = line.isEmpty() ? play: line + "     " + play;
		int lineH = gc.textExtent("Ag").y;
		gc.drawText(shorten(gc, line, area.width - 2 * MARGIN), MARGIN, y + gc.textExtent("Ag").y + 22, true);
		paintHints(gc, area, area.height - lineH - 18, lineH);
	}

	/** the other buttons of the controller: grey pills */
	private static final java.util.Set<String> PAD = java.util.Set.of("LB", "RB", "BACK", "START");
	/** the four buttons as the hints name them (A, B, X, Y): their name and colour can be changed in the settings */
	public static final String[] DEFAULT_BUTTONS = {"A", "B", "X", "Y"};
	private static final int[][] DEFAULT_COLORS = {{16, 124, 16}, {196, 43, 28}, {16, 92, 196}, {214, 160, 0}};

	/** the colour of button 1 to 4 (settings "tvbuttonNcolor": r,g,b) */
	public static org.eclipse.swt.graphics.RGB buttonColor(int n) {
		int[] d = DEFAULT_COLORS[n - 1];
		try {
			String[] v = org.dbgl.service.SettingsService.getInstance().getValue("gui", "tvbutton" + n + "color").split(",");
			return new org.eclipse.swt.graphics.RGB(Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()), Integer.parseInt(v[2].trim()));
		} catch (RuntimeException e) {
			return new org.eclipse.swt.graphics.RGB(d[0], d[1], d[2]);
		}
	}

	private static String buttonLabel(int n) {
		String v = org.dbgl.service.SettingsService.getInstance().getValue("gui", "tvbutton" + n);
		return (v == null || v.isBlank()) ? DEFAULT_BUTTONS[n - 1]: v.strip();
	}

	/** 1 to 4 for A, B, X, Y; 0 for anything else */
	private static int buttonOf(String key) {
		for (int i = 0; i < 4; i++)
			if (DEFAULT_BUTTONS[i].equalsIgnoreCase(key))
				return i + 1;
		return 0;
	}

	/**
	 * alpha 48: the hints at the bottom ("A/Enter: Start   B/Esc: Back ..." in the language of DBGL) drawn with symbols: each part
	 * before the colon split at "/", the controller buttons as coloured circles (or grey pills), the other keys as key caps
	 */
	private void paintHints(GC gc, Rectangle area, int y, int lineH) {
		String[] items = text_.get("dialog.tv.hints").trim().split("\\s{3,}");
		int h = lineH + 6, gap = 6, itemGap = 26;
		List<String[]> parts = new ArrayList<>();
		for (String item: items) {
			int colon = item.indexOf(':');
			if (colon < 0)
				continue;
			String[] keys = item.substring(0, colon).trim().split("/");
			String label = item.substring(colon + 1).trim();
			parts.add(new String[] {String.join("/", keys), label});
		}
		// widths first, so that the whole line ends at the right
		int total = 0;
		for (String[] part: parts) {
			for (String k: part[0].split("/"))
				total += keyWidth(gc, k.trim(), h) + gap;
			total += gc.textExtent(part[1]).x + itemGap;
		}
		total -= itemGap;
		int where = org.dbgl.service.SettingsService.getInstance().getIntValue("gui", "tvhints"); // 0 right, 1 centre, 2 left
		int cx = where == 2 ? MARGIN: where == 1 ? (area.width - total) / 2: area.width - MARGIN - total;
		gc.setLineWidth(1);
		for (String[] part: parts) {
			for (String k: part[0].split("/")) {
				String key = k.trim();
				int button = buttonOf(key);
				if (button > 0)
					key = buttonLabel(button);
				int sw = keyWidth(gc, k.trim(), h);
				if (button > 0 || PAD.contains(key.toUpperCase(java.util.Locale.ROOT))) {
					org.eclipse.swt.graphics.RGB rgb = button > 0 ? buttonColor(button): new org.eclipse.swt.graphics.RGB(80, 80, 86);
					gc.setBackground(new Color(rgb));
					if (button > 0 && sw == h)
						gc.fillOval(cx, y - 3, h, h);
					else
						gc.fillRoundRectangle(cx, y - 3, sw, h, h, h);
					// dark or light text, whichever can be read on the colour
					double lum = 0.2126 * rgb.red + 0.7152 * rgb.green + 0.0722 * rgb.blue;
					gc.setForeground(lum > 150 ? bg_: fg_);
				} else {
					gc.setBackground(tile_);
					gc.fillRoundRectangle(cx, y - 3, sw, h, 8, 8);
					gc.setForeground(dim_);
					gc.drawRoundRectangle(cx, y - 3, sw - 1, h - 1, 8, 8);
					gc.setForeground(fg_);
				}
				Point te = gc.textExtent(key);
				gc.drawText(key, cx + (sw - te.x) / 2, y - 3 + (h - te.y) / 2, true);
				cx += sw + gap;
			}
			gc.setForeground(dim_);
			gc.drawText(part[1], cx, y, true);
			cx += gc.textExtent(part[1]).x + itemGap;
		}
	}

	/** a round button for a short name, a pill or key cap for a longer one */
	private static int keyWidth(GC gc, String key, int h) {
		int button = buttonOf(key);
		String shown = button > 0 ? buttonLabel(button): key;
		int w = gc.textExtent(shown).x;
		return button > 0 ? (w + 8 <= h ? h: w + 14): Math.max(h, w + 14);
	}

	/** a picture as large as fits, whole (the size of each picture read once) */
	private Image fitted(File f, int maxW, int maxH) {
		Point size = sizes_.computeIfAbsent(f.getPath(), p -> {
			try (javax.imageio.stream.ImageInputStream in = javax.imageio.ImageIO.createImageInputStream(f)) {
				java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(in);
				if (readers.hasNext()) {
					javax.imageio.ImageReader r = readers.next();
					try {
						r.setInput(in);
						return new Point(r.getWidth(0), r.getHeight(0));
					} finally {
						r.dispose();
					}
				}
			} catch (java.io.IOException | RuntimeException e) {
				// below
			}
			return new Point(maxW, maxH);
		});
		double scale = Math.min((double)maxW / Math.max(1, size.x), (double)maxH / Math.max(1, size.y));
		int w = Math.max(1, (int)Math.round(size.x * scale)), h = Math.max(1, (int)Math.round(size.y * scale));
		return ImageService.getCachedResizedImage(canvas_.getDisplay(), w, h, false, f.getPath());
	}

	private final java.util.Map<String, Point> sizes_ = new java.util.HashMap<>();

	private static String shorten(GC gc, String s, int width) {
		if (gc.textExtent(s).x <= width)
			return s;
		String t = s;
		while (t.length() > 1 && gc.textExtent(t + "…").x > width)
			t = t.substring(0, t.length() - 1);
		return t.trim() + "…";
	}

	/** the cover of the profile if there is one, else its first picture, or null (as for Steam); looked for once */
	private File coverOf(Profile p) {
		return covers_.computeIfAbsent(p.getId(), id -> java.util.Optional.ofNullable(org.dbgl.gui.dialog.SteamDialog.coverOf(p))).orElse(null);
	}

	private final java.util.Map<Integer, java.util.Optional<File>> covers_ = new java.util.HashMap<>();
}
