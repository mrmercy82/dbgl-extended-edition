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
	private final Font big_, medium_, small_;
	private List<Profile> shown_;
	/** which games: all, the favorites, or one genre (Y/F goes on to the next); remembered with the game for the next time */
	private record Filter(String key, String genre) {
	}

	private final List<Filter> filters_ = new ArrayList<>();
	private int filter_;
	private boolean info_;
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
		applyFilter();
		Profile last = null;
		for (Profile p: shown_)
			if (String.valueOf(p.getId()).equals(settings.getValue("gui", "tvlastprofile")))
				last = p;
		index_ = Math.max(0, shown_.indexOf((last != null) ? last: current));

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
		if (info_ && b != Gamepad.Button.A && b != Gamepad.Button.START) { // the info is closed by any other button
			info_ = false;
			canvas_.redraw();
			return;
		}
		switch (b) {
			case UP -> move(0, -1);
			case DOWN -> move(0, 1);
			case LEFT -> move(-1, 0);
			case RIGHT -> move(1, 0);
			case A, START -> start();
			case B -> shell_.close();
			case BACK -> random();
			case X -> toggleInfo();
			case Y -> nextFilter(1);
			case LB -> move(0, -visibleRows());
			case RB -> move(0, visibleRows());
			default -> {
				// nothing
			}
		}
	}

	private void key(Event e) {
		if (info_ && e.keyCode != SWT.CR && e.keyCode != SWT.KEYPAD_CR && e.keyCode != ' ') {
			info_ = false;
			canvas_.redraw();
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
			case SWT.ESC, SWT.BS, SWT.F11 -> shell_.close();
			case SWT.TAB, 'f', 'F' -> nextFilter((e.stateMask & SWT.SHIFT) != 0 ? -1: 1);
			case 'i', 'I' -> toggleInfo();
			case 'z', 'Z' -> random();
			default -> {
				return;
			}
		}
		e.doit = false;
	}

	private void click(Event e) {
		int i = indexAt(e.x, e.y);
		if (i >= 0)
			select(i);
	}

	private static String filterName(Filter f) {
		return (f.genre() == null) ? f.key(): "genre:" + f.genre();
	}

	private void applyFilter() {
		Filter f = filters_.get(filter_);
		if (f.genre() != null)
			shown_ = all_.stream().filter(p -> f.genre().equalsIgnoreCase(StringUtils.trimToEmpty(p.getGenre()))).toList();
		else if (f.key().equals("dialog.tv.favorites"))
			shown_ = all_.stream().filter(p -> Boolean.TRUE.equals(p.isFavorite())).toList();
		else
			shown_ = all_;
	}

	private void nextFilter(int step) {
		Profile current = shown_.isEmpty() ? null: shown_.get(index_);
		filter_ = (filter_ + step + filters_.size()) % filters_.size();
		applyFilter();
		index_ = Math.max(0, shown_.indexOf(current));
		firstRow_ = 0;
		select(index_);
		canvas_.redraw();
	}

	private void random() {
		if (shown_.size() > 1)
			select(new java.util.Random().nextInt(shown_.size()));
	}

	private void toggleInfo() {
		info_ = !info_ && !shown_.isEmpty();
		canvas_.redraw();
	}

	private void start() {
		if (!shown_.isEmpty())
			runner_.accept(shown_.get(index_));
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
		String which = (f.genre() == null) ? text_.get(f.key(), new Object[] {shown_.size()}): text_.get("dialog.tv.genre", new Object[] {f.genre(), shown_.size()});
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
			paintFooter(gc, area, shown_.get(index_));
			if (info_)
				paintInfo(gc, area, shown_.get(index_));
		}
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
		for (String line: wrap(gc, notes, textW)) {
			if (ty + lineH > y + h - pad)
				break;
			gc.drawText(line, textX, ty, true);
			ty += lineH + 4;
		}
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
		gc.setForeground(selected ? fg_: dim_);
		String title = shorten(gc, StringUtils.defaultString(p.getTitle()), tileW());
		Point s = gc.textExtent(title);
		gc.drawText(title, r.x + (tileW() - s.x) / 2, r.y + coverH() + 12, true);
	}

	private void paintFooter(GC gc, Rectangle area, Profile p) {
		int y = area.height - FOOTER_H + 10;
		gc.setForeground(fg_);
		gc.setFont(medium_);
		gc.drawText(shorten(gc, StringUtils.defaultString(p.getTitle()), area.width - 2 * MARGIN), MARGIN, y, true);
		gc.setFont(small_);
		gc.setForeground(dim_);
		List<String> facts = new ArrayList<>();
		for (String f: new String[] {p.getYear(), p.getGenre(), p.getDeveloper()})
			if (StringUtils.isNotBlank(f))
				facts.add(f.trim());
		String line = String.join("  ·  ", facts);
		// alpha 48: can be turned off (settings, GUI, TV mode)
		String play = org.dbgl.service.SettingsService.getInstance().getBooleanValue("gui", "showplaytimeintv") ? ProfileReports.playInfo(p, text_): "";
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
