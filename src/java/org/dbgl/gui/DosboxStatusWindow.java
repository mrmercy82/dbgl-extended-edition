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
		"Write [0-9a-f]+ to rom at|unknown SCSI BIOS call|unknown call AX=|NVRAM write .* not implemented|GDC: .*Unknown command|PIC:ICW4|PEGC warning|port [0-9a-f]+h unknown command|MMIO attempt to write FONT ROM|PC-98 memcpy:|Segment limit violation|Limit check|DISPLAY: Unknown video mode",
		Pattern.CASE_INSENSITIVE);
	// numbers and addresses differ between otherwise equal lines
	// (alpha 49: also a hexadecimal value of four or eight letters without a digit, "Write ffff to rom at ...")
	private static final Pattern NUMBERS_PATRN = Pattern.compile("(?i)0x[0-9a-f]+|\\b[0-9a-f]*[0-9][0-9a-f]*\\b|\\b[a-f]{4}(?:[a-f]{4})?\\b");
	/** alpha 49: lines of DOSBox not yet shown, and whether showing them is under way (see {@link #append}) */
	private final java.util.concurrent.ConcurrentLinkedQueue<String> pending_ = new java.util.concurrent.ConcurrentLinkedQueue<>();
	private final java.util.concurrent.atomic.AtomicBoolean draining_ = new java.util.concurrent.atomic.AtomicBoolean();
	private static final int SUMMARY_MILLIS = 100;
	private boolean batch_, scrollDue_, summaryDue_, summaryTimer_; // only used by the thread of the window
	private long summaryShownAt_;

	/** the key by which consecutive lines count as the same: the line without "LOG:", every number replaced */
	public static String similarityKey(String line) {
		return NUMBERS_PATRN.matcher(line.replaceFirst("^\\s*LOG:\\s*", "")).replaceAll("#");
	}

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
	/** every line as shown (or hidden by "only warnings and errors"): to show them again when the box is changed */
	private record Shown(String line, Color color, String setting, int[] repeats, boolean always) {
	}

	private final java.util.List<Shown> shown_ = new ArrayList<>();
	private Button onlyProblems_;
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

	private static volatile DosboxStatusWindow last_;

	/**
	 * Test 15: the status window is gone at once when DOSBox ends without a problem, and with it what DOSBox said. The
	 * messages of the last start (of this run of DBGL) in a window that stays, to read, copy or save.
	 *
	 * @return false if DOSBox has not been started yet
	 */
	public static boolean showLast(Shell parent) {
		DosboxStatusWindow last = last_;
		if (last == null)
			return false;
		Shell dlg = new Shell(parent, SWT.SHELL_TRIM | SWT.APPLICATION_MODAL);
		dlg.setText(text("dialog.statuswindow.last"));
		org.dbgl.gui.abstractdialog.BaseDialog.inheritWindowImage(dlg, parent);
		dlg.setLayout(new GridLayout(4, false));
		DarkTheme.window(dlg);
		org.eclipse.swt.widgets.Text log = new org.eclipse.swt.widgets.Text(dlg, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		GridData logData = new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1);
		logData.widthHint = 900;
		logData.heightHint = 500;
		log.setLayoutData(logData);
		Font font = new Font(parent.getDisplay(), SystemUtils.IS_WINDOWS ? "Consolas": (SystemUtils.IS_OSX ? "Menlo": "Monospace"), 9, SWT.NORMAL);
		log.setFont(font);
		dlg.addDisposeListener(e -> font.dispose());
		String content = last.rawLog_.toString();
		log.setText(content);
		Label space = new Label(dlg, SWT.NONE);
		space.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button copy = new Button(dlg, SWT.PUSH), save = new Button(dlg, SWT.PUSH), close = new Button(dlg, SWT.PUSH);
		copy.setText(text("dialog.statuswindow.copy"));
		save.setText(text("dialog.statuswindow.save"));
		close.setText(text("dialog.statuswindow.close"));
		for (Button b: new Button[] {copy, save, close}) {
			GridData data = new GridData(SWT.END, SWT.CENTER, false, false);
			data.widthHint = Math.max(110, b.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 16);
			b.setLayoutData(data);
		}
		copy.addListener(SWT.Selection, e -> {
			Clipboard clipboard = new Clipboard(dlg.getDisplay());
			try {
				String chosen = log.getSelectionCount() > 0 ? log.getSelectionText(): content;
				if (!chosen.isEmpty())
					clipboard.setContents(new Object[] {chosen}, new Transfer[] {TextTransfer.getInstance()});
			} finally {
				clipboard.dispose();
			}
		});
		save.addListener(SWT.Selection, e -> {
			FileDialog dialog = new FileDialog(dlg, SWT.SAVE);
			dialog.setFilterExtensions(new String[] {"*.txt", "*.log", "*.*"});
			dialog.setFileName("dosbox-log-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)) + ".txt");
			dialog.setOverwrite(true);
			String file = dialog.open();
			if (file != null) {
				try {
					Files.writeString(new File(file).toPath(), content, StandardCharsets.UTF_8);
				} catch (IOException ex) {
					Mess_.on(dlg).exception(ex).warning();
				}
			}
		});
		close.addListener(SWT.Selection, e -> dlg.close());
		dlg.setDefaultButton(close);
		DarkTheme.apply(dlg);
		dlg.pack();
		Rectangle area = parent.getMonitor().getClientArea(), around = parent.getBounds();
		org.eclipse.swt.graphics.Point size = dlg.getSize();
		dlg.setLocation(Math.max(area.x, Math.min(around.x + (around.width - size.x) / 2, area.x + area.width - size.x)),
			Math.max(area.y, Math.min(around.y + (around.height - size.y) / 2, area.y + area.height - size.y)));
		dlg.open();
		return true;
	}

	private void create() {
		last_ = this;
		shell_ = new Shell(display_, SWT.SHELL_TRIM);
		shell_.setText(TITLE);
		// a window of its own in the taskbar: the DBGL symbol
		try {
			shell_.setImages(org.dbgl.service.ImageService.getResourceImages(display_, new String[] {"ico/016.png", "ico/024.png", "ico/032.png", "ico/048.png", "ico/064.png"}));
		} catch (RuntimeException e) {
			// without a symbol then
		}
		GridLayout layout = new GridLayout(6, false);
		layout.marginWidth = 4;
		layout.marginHeight = 4;
		shell_.setLayout(layout);

		text_ = new StyledText(shell_, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		text_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 6, 1));
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
		// only what went wrong: errors and warnings (and when DOSBox started and ended); copying and saving keep all of it
		onlyProblems_ = new Button(shell_, SWT.CHECK);
		onlyProblems_.setText(text("dialog.statuswindow.onlyproblems"));
		onlyProblems_.setSelection(SettingsService.getInstance().getBooleanValue("gui", "statuswindowonlyproblems"));
		onlyProblems_.addListener(SWT.Selection, e -> {
			SettingsService.getInstance().setBooleanValue("gui", "statuswindowonlyproblems", onlyProblems_.getSelection());
			showAgain();
		});
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
		shownAt_ = System.currentTimeMillis();
		if (SettingsService.getInstance().getBooleanValue("gui", "statuswindow_maximized"))
			shell_.setMaximized(true);
	}

	/** when the window was shown: moves right after that come from the system, not from the user */
	private long shownAt_ = Long.MAX_VALUE;

	/**
	 * use the size of the last status window and the place the user moved it to, if that is still on one of the screens, else
	 * the top left of the screen (DOSBox itself usually opens in the middle)
	 */
	private void restoreBounds() {
		SettingsService settings = SettingsService.getInstance();
		int width = Math.max(300, settings.getIntValue("gui", "statuswindow_width"));
		int height = Math.max(150, settings.getIntValue("gui", "statuswindow_height"));
		shell_.setSize(width, height);
		if (settings.hasValue("gui", "statuswindow_left") && settings.hasValue("gui", "statuswindow_top")) {
			Rectangle saved = new Rectangle(settings.getIntValue("gui", "statuswindow_left"), settings.getIntValue("gui", "statuswindow_top"), width, height);
			// at least the title bar area must be visible on a screen, the monitor setup may have changed
			Rectangle titleBar = new Rectangle(saved.x + 40, saved.y, Math.max(1, saved.width - 80), 20);
			if (Stream.of(display_.getMonitors()).anyMatch(m -> m.getClientArea().intersects(titleBar))) {
				shell_.setLocation(saved.x, saved.y);
				return;
			}
		}
		Rectangle area = display_.getPrimaryMonitor().getClientArea();
		shell_.setLocation(area.x + 20, area.y + 20);
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
		if (System.currentTimeMillis() - shownAt_ > 1000) { // only a place the user chose
			settings.setIntValue("gui", "statuswindow_left", r.x);
			settings.setIntValue("gui", "statuswindow_top", r.y);
		}
		settings.setIntValue("gui", "statuswindow_width", r.width);
		settings.setIntValue("gui", "statuswindow_height", r.height);
	}

	private void appendLine(String line, Color color) {
		if (summaryDue_)
			showSummary();
		lastKey_ = null; // a line of DBGL in between: what DOSBox writes after it starts a count of its own
		summaryStart_ = -1;
		rawLog_.append(line).append(System.lineSeparator());
		Shown s = new Shown(line, color, null, new int[1], true);
		shown_.add(s);
		appendVisibleLine(line, color);
	}

	private boolean visible(Shown s) {
		return s.always() || (onlyProblems_ == null) || onlyProblems_.isDisposed() || !onlyProblems_.getSelection() || (s.color() == errorColor_) || (s.color() == warningColor_);
	}

	/** one line of DOSBox in the text: in its color, as a link if it names a setting */
	private void showLine(Shown s) {
		int start = text_.getCharCount();
		appendVisibleLine(s.line(), s.color());
		if (s.setting() != null) {
			StyleRange range = new StyleRange();
			range.start = start;
			range.length = s.line().length();
			range.foreground = s.color();
			range.underline = true;
			range.underlineStyle = SWT.UNDERLINE_LINK;
			text_.setStyleRange(range);
			linkRanges_.add(new int[] {start, start + s.line().length()});
			linkSettings_.add(s.setting());
		}
	}

	/** all lines again, as the box "only warnings and errors" says now */
	private void showAgain() {
		if (isGone())
			return;
		text_.setText("");
		linkRanges_.clear();
		linkSettings_.clear();
		summaryStart_ = -1;
		summaryDue_ = false; // written below, with the number as it is now
		for (int i = 0; i < shown_.size(); i++) {
			Shown s = shown_.get(i);
			if (!visible(s))
				continue;
			if (s.always())
				appendVisibleLine(s.line(), s.color());
			else
				showLine(s);
			if (s.repeats()[0] > 0) {
				if (i == shown_.size() - 1)
					summaryStart_ = text_.getCharCount(); // more of it may follow
				appendVisibleLine("    " + TextService.getInstance().get("dialog.statuswindow.repeated", new Object[] {s.repeats()[0]}), harmlessColor_);
			}
		}
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
		if (batch_)
			scrollDue_ = true; // once, at the end of the lines that came together
		else
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

	/**
	 * May be called from any thread. Alpha 49: the lines are collected and shown together, all that have come by then in one
	 * go and painted once. Before, each line was a task of its own for the window: when DOSBox wrote thousands of lines at
	 * once (Windows 95 shutting down in DOSBox-X: 5300 lines "Write ... to rom"), DBGL did not answer for more than ten seconds.
	 */
	public void append(String line) {
		if (display_.isDisposed())
			return;
		pending_.add(line);
		if (draining_.compareAndSet(false, true))
			display_.asyncExec(this::showPending);
	}

	private void showPending() {
		draining_.set(false); // a line that comes from now on asks for the next round
		if (isGone()) {
			pending_.clear();
			return;
		}
		String line = pending_.poll();
		if (line == null)
			return;
		text_.setRedraw(false);
		batch_ = true;
		try {
			for (; line != null; line = pending_.poll())
				addOutputLine(line);
			// alpha 50: the counter of similar lines is written at most ten times a second, not once for each line. Lines that
			// come one after the other, each as a round of its own, froze the window in alpha 49 all the same
			if (summaryDue_) {
				if (System.currentTimeMillis() - summaryShownAt_ >= SUMMARY_MILLIS) {
					showSummary();
				} else if (!summaryTimer_) {
					summaryTimer_ = true;
					display_.timerExec(SUMMARY_MILLIS, () -> {
						summaryTimer_ = false;
						if (!isGone() && summaryDue_) {
							showSummary();
							text_.setTopIndex(text_.getLineCount() - 1);
						}
					});
				}
			}
		} finally {
			batch_ = false;
			if (scrollDue_)
				text_.setTopIndex(text_.getLineCount() - 1);
			scrollDue_ = false;
			text_.setRedraw(true);
		}
	}

	/** the line "... more similar lines" below the last line of DOSBox, with the number as it is now */
	private void showSummary() {
		summaryDue_ = false;
		summaryShownAt_ = System.currentTimeMillis();
		Shown last = shown_.isEmpty() ? null: shown_.get(shown_.size() - 1);
		if ((repeatCount_ <= 0) || ((last != null) && !visible(last)))
			return;
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
			if (batch_)
				scrollDue_ = true;
			else
				text_.setTopIndex(text_.getLineCount() - 1);
		}
	}

	/** consecutive lines that only differ in numbers are shown once, with a counter */
	private void addOutputLine(String line) {
		String key = similarityKey(line);
		if (key.equals(lastKey_)) {
			rawLog_.append(line).append(System.lineSeparator());
			repeatCount_++;
			Shown last = shown_.isEmpty() ? null: shown_.get(shown_.size() - 1);
			if (last != null)
				last.repeats()[0] = repeatCount_;
			summaryDue_ = true;
			return;
		}
		if (summaryDue_)
			showSummary(); // the number of the lines before, ahead of this other line
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
		rawLog_.append(line).append(System.lineSeparator());
		String setting = (profileId_ >= 0) && (settingLinkHandler_ != null) ? settingIn(line): null;
		Shown s = new Shown(line, color, setting, new int[1], false);
		shown_.add(s);
		if (visible(s))
			showLine(s);
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
