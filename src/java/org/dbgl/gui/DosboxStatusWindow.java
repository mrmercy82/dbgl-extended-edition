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
package org.dbgl.gui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;


/**
 * Shows the output (log) of a running DOSBox process.
 *
 * Up to 0.82, DOSBox Staging opened such a "status window" (a console) on Windows by itself, unless started with -noconsole.
 * DOSBox Staging 0.83 no longer does, and DOSBox-X never did. DBGL reads the output of DOSBox anyway, so it can show it
 * itself.
 *
 * The window closes when DOSBox ends normally, like the console did, unless 'keep open' is checked. If DOSBox ends with an
 * error, it stays open and comes to the front so the messages can be read. Warnings and errors are highlighted, and the log
 * can be copied or saved (exported) to a text file.
 */
public class DosboxStatusWindow {

	public static final String TITLE = "DOSBox Status Window";

	private static final Pattern ERROR_PATRN = Pattern.compile("\\b(error|fatal|failed|exception|cannot|can't|unable)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern WARNING_PATRN = Pattern.compile("\\b(warning|warn|deprecated|invalid|not found|unknown)\\b", Pattern.CASE_INSENSITIVE);
	// notices of the emulator about hardware details it does not emulate (yet); not problems of the configuration
	private static final Pattern HARMLESS_PATRN = Pattern.compile(
		"Write [0-9a-f]+ to rom at|unknown SCSI BIOS call|unknown call AX=|NVRAM write .* not implemented|GDC: .*Unknown command|PIC:ICW4|PEGC warning|port [0-9a-f]+h unknown command|MMIO attempt to write FONT ROM|PC-98 memcpy:|Segment limit violation|Limit check",
		Pattern.CASE_INSENSITIVE);
	// numbers and addresses differ between otherwise equal lines
	private static final Pattern NUMBERS_PATRN = Pattern.compile("(?i)0x[0-9a-f]+|\\b[0-9a-f]*[0-9][0-9a-f]*\\b");

	/** DOSBox ending within this time most likely means that starting the game failed */
	public static final long QUICK_EXIT_MILLIS = 5000;

	private final Display display_;
	private final String commandLine_;
	private Shell shell_;
	private StyledText text_;
	private Font font_;
	private Color errorColor_, warningColor_, infoColor_;
	private Button keepOpen_;
	private int pendingEvents_ = 3; // stdout closed, stderr closed, process ended
	private int exitCode_ = 0;
	private int warnings_ = 0, errors_ = 0;
	private final StringBuilder rawLog_ = new StringBuilder(); // everything, also repeated lines, for copy and save
	private String lastKey_;
	private int repeatCount_ = 0;
	private int summaryStart_ = -1;
	private Color harmlessColor_;
	private final long startMillis_ = System.currentTimeMillis();

	/** messages of DOSBox about a setting of the configuration; group 1 is the name of the setting */
	private static final java.util.List<java.util.regex.Pattern> SETTING_PATRNS = java.util.List.of(
		java.util.regex.Pattern.compile("(?:Invalid|Deprecated) '([^']+)' setting"), // Staging
		java.util.regex.Pattern.compile("Deprecated setting '([^']+)'"),
		java.util.regex.Pattern.compile("No config section or setting exists with the name '([^']+)'"),
		java.util.regex.Pattern.compile("is not a valid value for variable: ([^.\\s][^.]*)\\."), // DOSBox, DOSBox-X
		java.util.regex.Pattern.compile("(?i)unknown (?:option|setting|property|config option)[: ]+'?([A-Za-z0-9_][A-Za-z0-9_ .-]*?)'?\\.?\\s*$"));
	private static final java.util.regex.Pattern MARKUP_PATRN = java.util.regex.Pattern.compile("\\[(?:color=[a-z-]+|reset)\\]");

	/** asked to show a setting of a profile (profile id, name of the setting), e.g. by the main window */
	private static volatile java.util.function.BiConsumer<Integer, String> settingLinkHandler_;
	private volatile int profileId_ = -1;
	private final java.util.List<int[]> linkRanges_ = new ArrayList<>();
	private final java.util.List<String> linkSettings_ = new ArrayList<>();

	public static void setSettingLinkHandler(java.util.function.BiConsumer<Integer, String> handler) {
		settingLinkHandler_ = handler;
	}

	/** @return the name of the setting a message of DOSBox is about, or null */
	public static String settingIn(String line) {
		if (line == null)
			return null;
		String plain = MARKUP_PATRN.matcher(line).replaceAll("");
		for (java.util.regex.Pattern p: SETTING_PATRNS) {
			java.util.regex.Matcher m = p.matcher(plain);
			if (m.find())
				return m.group(1).strip();
		}
		return null;
	}

	/** the profile DOSBox was started for; messages about its settings become links to the profile dialog */
	public void setProfileId(int profileId) {
		profileId_ = profileId;
	}

	private DosboxStatusWindow(Display display, String commandLine) {
		display_ = display;
		commandLine_ = commandLine;
	}

	/**
	 * May be called from any thread.
	 *
	 * @return the window, or null if there is no user interface to show it in
	 */
	public static DosboxStatusWindow open(String commandLine) {
		Display display = Display.getDefault();
		if ((display == null) || display.isDisposed())
			return null;
		DosboxStatusWindow window = new DosboxStatusWindow(display, commandLine);
		if (display.getThread() == Thread.currentThread())
			window.create();
		else
			display.syncExec(window::create);
		return window;
	}

	private static String text(String key) {
		return TextService.getInstance().get(key);
	}

	private void create() {
		shell_ = new Shell(display_, SWT.SHELL_TRIM);
		shell_.setText(TITLE);
		GridLayout layout = new GridLayout(5, false);
		layout.marginWidth = 4;
		layout.marginHeight = 4;
		shell_.setLayout(layout);

		text_ = new StyledText(shell_, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		text_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 5, 1));
		// messages about a setting of the profile open the profile dialog there
		text_.addListener(SWT.MouseUp, e -> {
			String setting = (e.button == 1) && (text_.getSelectionCount() == 0) ? linkAt(e.x, e.y): null;
			java.util.function.BiConsumer<Integer, String> handler = settingLinkHandler_;
			if ((setting != null) && (handler != null))
				handler.accept(profileId_, setting);
		});
		text_.addListener(SWT.MouseMove, e -> text_.setCursor(linkAt(e.x, e.y) != null ? display_.getSystemCursor(SWT.CURSOR_HAND): null));
		font_ = new Font(display_, SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		text_.setFont(font_);
		if (DarkTheme.forced()) {
			text_.setBackground(DarkTheme.inputBackground);
			text_.setForeground(DarkTheme.defaultForeground);
		} else {
			text_.setBackground(display_.getSystemColor(SWT.COLOR_BLACK));
			text_.setForeground(display_.getSystemColor(SWT.COLOR_GRAY));
		}
		errorColor_ = new Color(255, 96, 96);
		warningColor_ = new Color(240, 200, 80);
		infoColor_ = new Color(120, 170, 255);
		harmlessColor_ = new Color(110, 110, 110);

		keepOpen_ = new Button(shell_, SWT.CHECK);
		keepOpen_.setText(text("dialog.statuswindow.keepopen"));
		keepOpen_.setSelection(SettingsService.getInstance().getBooleanValue("gui", "statuswindowkeepopen"));
		keepOpen_.addListener(SWT.Selection, e -> SettingsService.getInstance().setBooleanValue("gui", "statuswindowkeepopen", keepOpen_.getSelection()));
		Label filler = new Label(shell_, SWT.NONE);
		filler.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button copy = new Button(shell_, SWT.PUSH);
		copy.setText(text("dialog.statuswindow.copy"));
		copy.addListener(SWT.Selection, e -> copyToClipboard());
		Button save = new Button(shell_, SWT.PUSH);
		save.setText(text("dialog.statuswindow.save"));
		save.addListener(SWT.Selection, e -> saveToFile());
		Button close = new Button(shell_, SWT.PUSH);
		close.setText(text("dialog.statuswindow.close"));
		close.addListener(SWT.Selection, e -> shell_.close());

		shell_.addDisposeListener(e -> {
			font_.dispose();
			errorColor_.dispose();
			warningColor_.dispose();
			infoColor_.dispose();
			harmlessColor_.dispose();
		});

		appendLine(text("dialog.statuswindow.started") + ": " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)), infoColor_);
		if (commandLine_ != null)
			appendLine(commandLine_, infoColor_);
		appendLine("", null);

		restoreBounds();
		shell_.addListener(SWT.Move, e -> rememberBounds());
		shell_.addListener(SWT.Resize, e -> rememberBounds());
		shell_.open(); // in front of DBGL; the DOSBox window appears on top of it moments later
		if (SettingsService.getInstance().getBooleanValue("gui", "statuswindow_maximized"))
			shell_.setMaximized(true);
	}

	/** use the position and size of the last status window, if that is still on one of the screens, else center it */
	private void restoreBounds() {
		SettingsService settings = SettingsService.getInstance();
		int width = Math.max(300, settings.getIntValue("gui", "statuswindow_width"));
		int height = Math.max(150, settings.getIntValue("gui", "statuswindow_height"));
		shell_.setSize(width, height);
		if (settings.hasValue("gui", "statuswindow_x") && settings.hasValue("gui", "statuswindow_y")) {
			Rectangle saved = new Rectangle(settings.getIntValue("gui", "statuswindow_x"), settings.getIntValue("gui", "statuswindow_y"), width, height);
			// at least the title bar area must be visible on a screen, the monitor setup may have changed
			Rectangle titleBar = new Rectangle(saved.x + 40, saved.y, Math.max(1, saved.width - 80), 20);
			if (Stream.of(display_.getMonitors()).anyMatch(m -> m.getClientArea().intersects(titleBar))) {
				shell_.setLocation(saved.x, saved.y);
				return;
			}
		}
		Rectangle area = display_.getPrimaryMonitor().getClientArea();
		Point size = shell_.getSize();
		shell_.setLocation(area.x + Math.max(0, (area.width - size.x) / 2), area.y + Math.max(0, (area.height - size.y) / 3));
	}

	private void rememberBounds() {
		if (shell_.isDisposed() || shell_.getMinimized())
			return;
		SettingsService settings = SettingsService.getInstance();
		boolean maximized = shell_.getMaximized();
		settings.setBooleanValue("gui", "statuswindow_maximized", maximized);
		if (maximized)
			return;
		Rectangle r = shell_.getBounds();
		settings.setIntValue("gui", "statuswindow_x", r.x);
		settings.setIntValue("gui", "statuswindow_y", r.y);
		settings.setIntValue("gui", "statuswindow_width", r.width);
		settings.setIntValue("gui", "statuswindow_height", r.height);
	}

	private void appendLine(String line, Color color) {
		rawLog_.append(line).append(System.lineSeparator());
		appendVisibleLine(line, color);
	}

	private void appendVisibleLine(String line, Color color) {
		int start = text_.getCharCount();
		text_.append(line + text_.getLineDelimiter());
		if ((color != null) && !line.isEmpty()) {
			StyleRange range = new StyleRange();
			range.start = start;
			range.length = line.length();
			range.foreground = color;
			text_.setStyleRange(range);
		}
		text_.setTopIndex(text_.getLineCount() - 1);
	}

	private void copyToClipboard() {
		Clipboard clipboard = new Clipboard(display_);
		try {
			String content = text_.getSelectionCount() > 0 ? text_.getSelectionText(): rawLog_.toString();
			if (!content.isEmpty())
				clipboard.setContents(new Object[] {content}, new Transfer[] {TextTransfer.getInstance()});
		} finally {
			clipboard.dispose();
		}
	}

	private void saveToFile() {
		FileDialog dialog = new FileDialog(shell_, SWT.SAVE);
		dialog.setFilterExtensions(new String[] {"*.txt", "*.log", "*.*"});
		dialog.setFileName("dosbox-log-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)) + ".txt");
		dialog.setOverwrite(true);
		String file = dialog.open();
		if (file == null)
			return;
		try {
			Files.writeString(new File(file).toPath(), rawLog_.toString(), StandardCharsets.UTF_8); // complete, also the repeated lines
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
		}
	}

	private boolean isGone() {
		return (shell_ == null) || shell_.isDisposed() || (text_ == null) || text_.isDisposed();
	}

	/** may be called from any thread */
	public void append(String line) {
		if (display_.isDisposed())
			return;
		display_.asyncExec(() -> {
			if (isGone())
				return;
			addOutputLine(line);
		});
	}

	/** consecutive lines that only differ in numbers are shown once, with a counter */
	private void addOutputLine(String line) {
		String key = NUMBERS_PATRN.matcher(line.replaceFirst("^\\s*LOG:\\s*", "")).replaceAll("#");
		if (key.equals(lastKey_)) {
			rawLog_.append(line).append(System.lineSeparator());
			repeatCount_++;
			String summary = "    " + TextService.getInstance().get("dialog.statuswindow.repeated", new Object[] {repeatCount_});
			if (summaryStart_ == -1) {
				summaryStart_ = text_.getCharCount();
				appendVisibleLine(summary, harmlessColor_);
			} else {
				text_.replaceTextRange(summaryStart_, text_.getCharCount() - summaryStart_, summary + text_.getLineDelimiter());
				StyleRange range = new StyleRange();
				range.start = summaryStart_;
				range.length = summary.length();
				range.foreground = harmlessColor_;
				text_.setStyleRange(range);
				text_.setTopIndex(text_.getLineCount() - 1);
			}
			return;
		}
		lastKey_ = key;
		repeatCount_ = 0;
		summaryStart_ = -1;
		Color color = null;
		if (HARMLESS_PATRN.matcher(line).find()) {
			color = harmlessColor_;
		} else if (ERROR_PATRN.matcher(line).find()) {
			color = errorColor_;
			errors_++;
		} else if (WARNING_PATRN.matcher(line).find()) {
			color = warningColor_;
			warnings_++;
		}
		int start = text_.getCharCount();
		appendLine(line, color);
		String setting = (profileId_ >= 0) && (settingLinkHandler_ != null) ? settingIn(line): null;
		if (setting != null) {
			StyleRange range = new StyleRange();
			range.start = start;
			range.length = line.length();
			range.foreground = color;
			range.underline = true;
			range.underlineStyle = SWT.UNDERLINE_LINK;
			text_.setStyleRange(range);
			linkRanges_.add(new int[] {start, start + line.length()});
			linkSettings_.add(setting);
		}
		if ((color == errorColor_) || (color == warningColor_))
			updateTitle(null);
	}

	/** the setting of the link at a position in the text, or null */
	private String linkAt(int x, int y) {
		int offset = text_.getOffsetAtPoint(new org.eclipse.swt.graphics.Point(x, y));
		if (offset < 0)
			return null;
		for (int i = 0; i < linkRanges_.size(); i++)
			if ((offset >= linkRanges_.get(i)[0]) && (offset < linkRanges_.get(i)[1]))
				return linkSettings_.get(i);
		return null;
	}

	/**
	 * DOSBox ended within moments, but no status window was shown (hidden, or not needed for this DOSBox version): show the
	 * output now, as the start most likely failed and DOSBox closed before its message could be read. May be called from any
	 * thread.
	 */
	public static void showAfterQuickExit(String commandLine, List<String> output, int exitCode, long elapsedMillis) {
		showAfterQuickExit(commandLine, output, exitCode, elapsedMillis, -1);
	}

	public static void showAfterQuickExit(String commandLine, List<String> output, int exitCode, long elapsedMillis, int profileId) {
		DosboxStatusWindow window = open(commandLine);
		if (window == null)
			return;
		window.setProfileId(profileId);
		window.display_.asyncExec(() -> {
			if (window.isGone())
				return;
			output.forEach(window::addOutputLine);
			window.showEnd(exitCode, elapsedMillis);
		});
	}

	private void showEnd(int exitCode, long elapsedMillis) {
		boolean quick = (elapsedMillis < QUICK_EXIT_MILLIS) && SettingsService.getInstance().getBooleanValue("dosbox", "detectfailedstart");
		appendLine("", null);
		appendLine(text("dialog.statuswindow.ended") + (exitCode != 0 ? " (exit code " + exitCode + ")": ""), exitCode != 0 ? errorColor_: infoColor_);
		if (quick)
			appendLine(TextService.getInstance().get("dialog.statuswindow.failedstart", new Object[] {Math.max(1, elapsedMillis / 1000)}), warningColor_);
		updateTitle(exitCode);
		if ((exitCode != 0) || quick) {
			shell_.setMinimized(false);
			shell_.forceActive();
		}
	}

	private void updateTitle(Integer exitCode) {
		StringBuilder title = new StringBuilder(TITLE);
		if (exitCode != null)
			title.append(" - ").append(text("dialog.statuswindow.ended")).append(exitCode != 0 ? " (exit code " + exitCode + ")": "");
		if ((errors_ > 0) || (warnings_ > 0))
			title.append(" [").append(errors_).append(" / ").append(warnings_).append("]");
		shell_.setText(title.toString());
	}

	/** may be called from any thread, once per output stream */
	public void streamClosed() {
		eventHappened();
	}

	/** may be called from any thread, once */
	public void processEnded(int exitCode) {
		synchronized (this) {
			exitCode_ = exitCode;
		}
		eventHappened();
	}

	private void eventHappened() {
		int exitCode;
		synchronized (this) {
			if (--pendingEvents_ > 0)
				return;
			exitCode = exitCode_;
		}
		if (display_.isDisposed())
			return;
		display_.asyncExec(() -> {
			if (isGone())
				return;
			long elapsed = System.currentTimeMillis() - startMillis_;
			boolean quick = (elapsed < QUICK_EXIT_MILLIS) && SettingsService.getInstance().getBooleanValue("dosbox", "detectfailedstart");
			if ((exitCode == 0) && !quick && !keepOpen_.getSelection()) {
				shell_.dispose();
				return;
			}
			showEnd(exitCode, elapsed);
		});
	}
}
