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
package org.dbgl.gui.controls;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;


/**
 * alpha 51: for error reports - the windows of DBGL opened one after the other, a picture taken of each (of each tab or page
 * of those that have some) and closed again, without saving anything. The pictures go into one ZIP file in the folder "logs".
 * Made because a window can look right on one operating system and wrong on another (white headings in the dark mode, rows of
 * a list out of place), which only shows on the computer of the one who has that system.
 */
public final class WindowTour {

	/** how a window shows several pages: not at all, by tabs, or by a tree of pages at its left (the editors) */
	public enum Pages {
		NONE, TABS, TREE
	}

	/** a window to take a picture of: a name for its file, and what opens it (and returns once it is closed) */
	public record Step(String name, Pages pages, Runnable open) {
	}

	private static final int MAX_PAGES = 80, WAIT = 1200, SETTLE = 700, PAGE = 450, TRIES = 20;

	private final Shell main_;
	private final Display display_;
	private final List<Step> steps_;
	private final File dir_;
	private final String suffix_;
	private final java.util.function.BiConsumer<File, Integer> done_;
	private final List<String> notes_ = new ArrayList<>();
	private int at_, pictures_;
	private boolean running_, captured_;

	private WindowTour(Shell main, List<Step> steps, File dir, String suffix, List<String> info, java.util.function.BiConsumer<File, Integer> done) {
		main_ = main;
		display_ = main.getDisplay();
		steps_ = steps;
		dir_ = dir;
		suffix_ = suffix;
		done_ = done;
		notes_.addAll(info);
	}

	/**
	 * @param logsDir the folder "logs" of DBGL
	 * @param suffix  goes into the names of the pictures ("light", "dark")
	 * @param info    lines for info.txt (version, system, ...)
	 * @param done    gets the ZIP file (null if it could not be written) and the number of pictures once all is done
	 */
	public static void run(Shell main, List<Step> steps, File logsDir, String stamp, String suffix, List<String> info, java.util.function.BiConsumer<File, Integer> done) {
		File dir = new File(logsDir, "window-tour-" + stamp);
		if (!dir.mkdirs() && !dir.isDirectory()) {
			done.accept(null, 0);
			return;
		}
		WindowTour tour = new WindowTour(main, steps, dir, suffix, info, done);
		tour.shoot(main, tour.fileName(0, "main", -1));
		main.getDisplay().asyncExec(tour::next);
	}

	/** the name of a picture: number of the window, its name, the page if it has some, light or dark */
	String fileName(int number, String name, int page) {
		return String.format("%02d-%s%s-%s.png", number, name.replaceAll("[^A-Za-z0-9]+", "-"), page < 0 ? "": String.format("-%02d", page + 1), suffix_);
	}

	private void next() {
		if (main_.isDisposed())
			return;
		if (at_ >= steps_.size()) {
			finish();
			return;
		}
		Step step = steps_.get(at_++);
		List<Shell> before = List.of(display_.getShells());
		running_ = true;
		captured_ = false;
		int[] tries = {0};
		Runnable[] look = new Runnable[1];
		look[0] = () -> {
			if (main_.isDisposed())
				return;
			List<Shell> opened = new ArrayList<>();
			for (Shell s: display_.getShells())
				if (!before.contains(s) && !s.isDisposed() && s.isVisible())
					opened.add(s);
			if (opened.isEmpty()) {
				if (running_ && (++tries[0] < TRIES)) {
					display_.timerExec(500, look[0]);
					return;
				}
				notes_.add(step.name() + ": no window");
				captured_ = true;
				if (!running_)
					display_.asyncExec(this::next);
				return;
			}
			display_.timerExec(SETTLE, () -> capture(step, opened));
		};
		display_.timerExec(WAIT, look[0]);
		try {
			step.open().run();
		} catch (RuntimeException e) {
			notes_.add(step.name() + ": " + e);
		}
		running_ = false;
		if (captured_)
			display_.asyncExec(this::next);
	}

	/** pictures of the newest of the opened windows (page by page), then they are closed: that ends what opened them */
	private void capture(Step step, List<Shell> opened) {
		Shell shell = opened.get(opened.size() - 1);
		if (shell.isDisposed()) {
			closeAll(opened);
			return;
		}
		List<Runnable> turns = pagesOf(shell, step.pages());
		if (turns.isEmpty()) {
			shoot(shell, fileName(at_, step.name(), -1));
			closeAll(opened);
			return;
		}
		int[] page = {0};
		Runnable[] turn = new Runnable[1];
		turn[0] = () -> {
			if (shell.isDisposed() || (page[0] >= turns.size())) {
				closeAll(opened);
				return;
			}
			try {
				turns.get(page[0]).run();
			} catch (RuntimeException e) {
				notes_.add(step.name() + " page " + (page[0] + 1) + ": " + e);
			}
			display_.timerExec(PAGE, () -> {
				if (!shell.isDisposed())
					shoot(shell, fileName(at_, step.name(), page[0]));
				page[0]++;
				turn[0].run();
			});
		};
		turn[0].run();
	}

	private void closeAll(List<Shell> opened) {
		captured_ = true;
		for (int i = opened.size() - 1; i >= 0; i--)
			if (!opened.get(i).isDisposed())
				opened.get(i).close();
		if (!running_)
			display_.asyncExec(this::next);
	}

	/** what shows each page of the window in turn: its tabs, or the entries of the tree of pages */
	static List<Runnable> pagesOf(Shell shell, Pages pages) {
		List<Runnable> turns = new ArrayList<>();
		if (pages == Pages.NONE)
			return turns;
		for (Control c: allOf(shell, new ArrayList<>())) {
			if ((pages == Pages.TABS) && (c instanceof CTabFolder folder)) {
				for (int i = 0; i < Math.min(MAX_PAGES, folder.getItemCount()); i++) {
					int index = i;
					turns.add(() -> {
						folder.setSelection(index);
						Event e = new Event();
						e.item = folder.getItem(index);
						folder.notifyListeners(SWT.Selection, e);
					});
				}
				return turns;
			}
			if ((pages == Pages.TABS) && (c instanceof TabFolder folder)) {
				for (int i = 0; i < Math.min(MAX_PAGES, folder.getItemCount()); i++) {
					int index = i;
					turns.add(() -> {
						folder.setSelection(index);
						Event e = new Event();
						e.item = folder.getItem(index);
						folder.notifyListeners(SWT.Selection, e);
					});
				}
				return turns;
			}
			if ((pages == Pages.TREE) && (c instanceof Tree tree)) {
				List<TreeItem> items = new ArrayList<>();
				collect(tree.getItems(), items);
				for (TreeItem item: items.subList(0, Math.min(MAX_PAGES, items.size())))
					turns.add(() -> {
						if (item.isDisposed())
							return;
						tree.setSelection(item);
						Event e = new Event();
						e.item = item;
						tree.notifyListeners(SWT.Selection, e);
					});
				return turns;
			}
		}
		return turns;
	}

	private static List<Control> allOf(Composite parent, List<Control> out) {
		for (Control c: parent.getChildren()) {
			out.add(c);
			if (c instanceof Composite inner)
				allOf(inner, out);
		}
		return out;
	}

	private static void collect(TreeItem[] items, List<TreeItem> out) {
		for (TreeItem item: items) {
			out.add(item);
			item.setExpanded(true);
			collect(item.getItems(), out);
		}
	}

	/** a picture of the window as it is on the screen */
	private void shoot(Shell shell, String name) {
		try {
			shell.forceActive();
			shell.update();
			display_.update();
			Rectangle b = shell.getBounds().intersection(display_.getBounds());
			if ((b.width <= 0) || (b.height <= 0)) {
				notes_.add(name + ": not on the screen");
				return;
			}
			Image image = new Image(display_, b.width, b.height);
			GC gc = new GC(display_);
			try {
				gc.copyArea(image, b.x, b.y);
				hideKeys(shell, image, b);
				ImageLoader loader = new ImageLoader();
				loader.data = new ImageData[] {image.getImageData()};
				loader.save(new File(dir_, name).getPath(), SWT.IMAGE_PNG);
				pictures_++;
			} finally {
				gc.dispose();
				image.dispose();
			}
		} catch (RuntimeException e) {
			notes_.add(name + ": " + e);
		}
	}

	/**
	 * alpha 51: fields that hold a key (of TheGamesDB, MobyGames, IGDB, ScreenScraper, ...) are painted over in the picture,
	 * so a picture sent with an error report does not give the key away. Nothing in the window itself is changed.
	 */
	private void hideKeys(Shell shell, Image image, Rectangle shot) {
		GC gc = new GC(image);
		try {
			gc.setBackground(display_.getSystemColor(SWT.COLOR_DARK_GRAY));
			gc.setForeground(display_.getSystemColor(SWT.COLOR_WHITE));
			for (Control c: allOf(shell, new ArrayList<>())) {
				if (!(c instanceof org.eclipse.swt.widgets.Text t) || !t.isVisible() || !looksLikeKey(t.getText()))
					continue;
				Rectangle r = display_.map(t.getParent(), null, t.getBounds());
				gc.fillRectangle(r.x - shot.x, r.y - shot.y, r.width, r.height);
				gc.drawText("(hidden)", r.x - shot.x + 4, r.y - shot.y + 1, true);
			}
		} finally {
			gc.dispose();
		}
	}

	/**
	 * a key, not a name, a path, a version or a web address: 16 or more letters and digits with at least 4 digits, no space,
	 * no dot and none of \\ : /
	 */
	public static boolean looksLikeKey(String text) {
		String s = text == null ? "": text.trim();
		return (s.length() >= 16) && s.matches("[A-Za-z0-9+=_-]+") && s.matches("(.*[0-9]){4}.*") && s.matches(".*[A-Za-z].*");
	}

	/** the pictures and info.txt into one ZIP file beside their folder; the folder (only what was made here) goes */
	private void finish() {
		File zip = new File(dir_.getParentFile(), dir_.getName() + ".zip");
		try {
			notes_.add("pictures: " + pictures_);
			Files.write(new File(dir_, "info.txt").toPath(), notes_, StandardCharsets.UTF_8);
			File[] files = dir_.listFiles((d, n) -> n.endsWith(".png") || n.equals("info.txt"));
			if (files == null)
				throw new IOException("no files");
			java.util.Arrays.sort(files);
			try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
				for (File f: files) {
					out.putNextEntry(new ZipEntry(f.getName()));
					Files.copy(f.toPath(), out);
					out.closeEntry();
				}
			}
			for (File f: files)
				Files.deleteIfExists(f.toPath());
			Files.deleteIfExists(dir_.toPath()); // only if nothing else is in it
		} catch (IOException e) {
			zip = null;
		}
		main_.forceActive();
		done_.accept(zip, pictures_);
	}
}
