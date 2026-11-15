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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.dbgl.service.FileLocationService;
import org.dbgl.service.TextService;
import org.dbgl.util.ProfileReports;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;


/**
 * alpha 49: "Save report..." of the windows that show a report in colors (switching profiles to another DOSBox version,
 * the previews of "Check profiles" and of taking over settings, the report of "Check profiles"): a small menu as the
 * statistics have it - as text, as a web page in the colors of the window, or as PDF by the print window of the browser -
 * and a file name with date and time, like the other reports.
 */
public final class ReportSave {

	private ReportSave() {
	}

	/** "dbgl-switch-report-2026-11-15_12-30.html" */
	public static String name(String base, String ending, LocalDateTime now) {
		return base + "-" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + "." + ending;
	}

	/** the text of a StyledText as a web page, each line in the color and weight it has in the window (on white, for print) */
	public static String html(StyledText area, String title) {
		List<int[]> ranges = new ArrayList<>();
		for (StyleRange r: area.getStyleRanges()) {
			Color c = r.foreground;
			boolean bold = (r.fontStyle & SWT.BOLD) != 0;
			if (r.font != null)
				for (FontData d: r.font.getFontData())
					bold |= (d.getStyle() & SWT.BOLD) != 0;
			ranges.add(new int[] {r.start, r.length, c == null ? -1: c.getRed(), c == null ? -1: c.getGreen(), c == null ? -1: c.getBlue(), bold ? 1: 0});
		}
		return ProfileReports.styledHtml(area.getText(), ranges, title);
	}

	/**
	 * The menu at the mouse: text, web page, PDF.
	 *
	 * @param base  the beginning of the file name, e.g. "dbgl-switch-report"
	 * @param title the title of the page
	 * @param text  the report as text
	 * @param html  the report as a web page
	 */
	public static void menu(Shell shell, String base, String title, Supplier<String> text, Supplier<String> html) {
		org.dbgl.service.ITextService t = TextService.getInstance();
		Menu menu = new Menu(shell, SWT.POP_UP);
		MenuItem plain = new MenuItem(menu, SWT.PUSH);
		plain.setText(t.get("dialog.statistics.save.text") + " (*.txt)");
		plain.addListener(SWT.Selection, e -> save(shell, base, "txt", text));
		MenuItem page = new MenuItem(menu, SWT.PUSH);
		page.setText(t.get("dialog.report.save.html") + " (*.html)");
		page.addListener(SWT.Selection, e -> save(shell, base, "html", html));
		MenuItem pdf = new MenuItem(menu, SWT.PUSH);
		pdf.setText(t.get("dialog.statistics.save.pdf"));
		pdf.addListener(SWT.Selection, e -> print(shell, base, html));
		Point at = shell.getDisplay().getCursorLocation();
		menu.setLocation(at.x, at.y);
		menu.setVisible(true);
	}

	private static void save(Shell shell, String base, String ending, Supplier<String> content) {
		FileDialog dialog = new FileDialog(shell, SWT.SAVE);
		dialog.setFilterExtensions(new String[] {"*." + ending, "*.*"});
		dialog.setFileName(name(base, ending, LocalDateTime.now()));
		dialog.setOverwrite(true);
		String file = dialog.open();
		if (file == null)
			return;
		if (!file.toLowerCase().endsWith("." + ending))
			file += "." + ending;
		try {
			Files.writeString(new File(file).toPath(), content.get(), StandardCharsets.UTF_8);
			if (Mess_.on(shell).key("dialog.report.confirm.view", new Object[] {file}).confirm())
				SystemUtils.openForBrowsing(file, shell);
		} catch (RuntimeException | IOException e) {
			Mess_.on(shell).exception(e).warning();
		}
	}

	/** as the statistics: the page in the export folder, opened in the browser with its print window ("Save as PDF" there) */
	public static void print(Shell shell, String base, Supplier<String> html) {
		try {
			File page = new File(FileLocationService.getInstance().getExportDir(), base + "-print.html");
			Files.writeString(page.toPath(), ProfileReports.forPrinting(html.get(), name(base, "pdf", LocalDateTime.now()).replaceFirst("\\.pdf$", "")), StandardCharsets.UTF_8);
			SystemUtils.openForBrowsing(page.getPath(), shell);
		} catch (RuntimeException | IOException e) {
			Mess_.on(shell).exception(e).warning();
		}
	}
}
