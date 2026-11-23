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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.SavegamesPanel;
import org.dbgl.service.ITextService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;


/**
 * alpha 53: the first start: language and appearance, DOSBox, the first games, information from the internet, in five pages. Each page only calls what DBGL
 * has anyway (Locate and Download DOSBox, the imports); nothing is done that the user does not choose. Shown once, when there
 * are no profiles yet; again from Help > Welcome wizard.
 */
public final class WelcomeWizard {

	/** what the wizard needs from the main window */
	public interface Actions {
		/** the DOSBox versions entered now: per version its title and its folder */
		List<String[]> dosboxVersions();

		/** the DOSBox that comes with DBGL (or the one Locate DOSBox finds by itself), entered if it is not yet */
		void enterFoundDosbox();

		void locateDosbox();

		void downloadDosbox();

		/** to be told when the list of DOSBox versions has changed (a download ends later, in a window of its own) */
		void watchDosbox(Runnable changed);

		int profileCount();

		void importFolder();

		void importStoreGames();

		void importLaunchBox();

		void importExo();

		/** whether ScummVM is wanted (Settings > General): else its button is left out */
		boolean scummvmWanted();

		void importScummvm();

		void importDfend();

		/** Profiles > Covers for profiles without pictures */
		void fetchCovers();

		/** Profiles > Import known games in folder (recognized by their files) */
		void importKnown();
	}

	/** what came out of it: whether DBGL is to start again now (language or appearance changed), and whether the process must */
	public record Result(boolean restart, boolean processRestart) {
	}

	public static final String SECTION = "gui", SHOWN = "welcomeshown";

	private WelcomeWizard() {
	}

	/** whether the wizard is due by itself: never shown, and no profiles yet (who updates from an earlier DBGL has some) */
	public static boolean due(boolean shown, int profiles) {
		return !shown && (profiles == 0);
	}

	public static Result open(Shell parent, Actions actions) {
		ITextService text = TextService.getInstance();
		SettingsService settings = SettingsService.getInstance();
		Display display = parent.getDisplay();
		Shell dlg = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		dlg.setText(text.get("dialog.welcome.title"));
		BaseDialog.inheritWindowImage(dlg, parent);
		dlg.setLayout(new GridLayout(1, false));
		if (DarkTheme.forced())
			dlg.setBackground(DarkTheme.dialogBackground);

		Label heading = new Label(dlg, SWT.NONE);
		FontData[] fd = heading.getFont().getFontData();
		for (FontData f: fd) {
			f.setHeight(f.getHeight() + 3);
			f.setStyle(SWT.BOLD);
		}
		Font big = new Font(display, fd);
		heading.setFont(big);
		heading.addListener(SWT.Dispose, e -> big.dispose());
		heading.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

		Composite stack = new Composite(dlg, SWT.NONE);
		StackLayout pages = new StackLayout();
		stack.setLayout(pages);
		GridData stackData = new GridData(SWT.FILL, SWT.FILL, true, true);
		stackData.widthHint = 560;
		stack.setLayoutData(stackData);

		// page 1: language and appearance
		Composite p1 = page(stack, 2);
		wrap(p1, text.get("dialog.welcome.p1.text"), 2);
		label(p1, text.get("dialog.welcome.language"));
		Map<String, Locale> locales = SettingsDialog.getLocales();
		Combo language = new Combo(p1, SWT.READ_ONLY);
		language.setItems(locales.keySet().toArray(new String[0]));
		language.setVisibleItemCount(20);
		Locale now = Locale.of(settings.getValue("locale", "language"), settings.getValue("locale", "country"), settings.getValue("locale", "variant"));
		locales.forEach((name, l) -> {
			if (l.equals(now))
				language.setText(name);
		});
		label(p1, text.get("dialog.welcome.appearance"));
		Composite themes = new Composite(p1, SWT.NONE);
		GridLayout themesLayout = new GridLayout(3, false);
		themesLayout.marginWidth = themesLayout.marginHeight = 0;
		themes.setLayout(themesLayout);
		int theme = settings.getIntValue("gui", "theme");
		Button[] themeButtons = {radio(themes, text.get("dialog.welcome.light")), radio(themes, text.get("dialog.welcome.dark")), radio(themes, text.get("dialog.welcome.system"))};
		themeButtons[(theme >= 0) && (theme < 3) ? theme: 1].setSelection(true); // 3 (high contrast) is dark

		// page 2: DOSBox
		Composite p2 = page(stack, 2);
		wrap(p2, text.get("dialog.welcome.p2.recommend"), 2);
		bullets(p2, 2, text.get("dialog.welcome.p2.staging"), text.get("dialog.welcome.p2.dosboxx"), text.get("dialog.welcome.p2.classic"));
		wrap(p2, text.get("dialog.welcome.p2.found"), 2);
		// one row per version: tick, title, folder, in columns (the folders one below the other)
		Composite versions = new Composite(p2, SWT.NONE);
		GridLayout versionsLayout = new GridLayout(3, false);
		versionsLayout.marginWidth = versionsLayout.marginHeight = 0;
		versionsLayout.horizontalSpacing = 10;
		versionsLayout.verticalSpacing = 3;
		versions.setLayout(versionsLayout);
		GridData versionsData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		versionsData.horizontalIndent = 12;
		versions.setLayoutData(versionsData);
		Button locate = push(p2, text.get("dialog.welcome.locate"), text.get("dialog.welcome.locate.tooltip"));
		Button download = push(p2, text.get("dialog.welcome.download"), text.get("dialog.welcome.download.tooltip"));
		for (Button b: new Button[] {locate, download}) // an empty line between the list and the buttons
			((GridData)b.getLayoutData()).verticalIndent = 14;
		wrap(p2, text.get("dialog.welcome.p2.text"), 2);

		// page 3: the first games
		Composite p3 = page(stack, 2);
		wrap(p3, text.get("dialog.welcome.p3.text"), 2);
		Button[] imports = {importRow(p3, text, "folder", actions::importFolder), importRow(p3, text, "known", actions::importKnown),
				importRow(p3, text, "store", actions::importStoreGames),
				importRow(p3, text, "launchbox", actions::importLaunchBox), importRow(p3, text, "exo", actions::importExo),
				actions.scummvmWanted() ? importRow(p3, text, "scummvm", actions::importScummvm): null, importRow(p3, text, "dfend", actions::importDfend)};
		Label games = new Label(p3, SWT.NONE);
		GridData gamesData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		gamesData.verticalIndent = 8;
		games.setLayoutData(gamesData);

		// page 4: game information from the internet: which database, how, and that a precise title helps
		Composite pOnline = page(stack, 1);
		wrap(pOnline, text.get("dialog.welcome.p5.text"), 1);
		wrap(pOnline, text.get("dialog.welcome.p5.howto"), 1);
		bullets(pOnline, 1, text.get("dialog.welcome.p5.single"), text.get("dialog.welcome.p5.multi"));
		wrap(pOnline, text.get("dialog.welcome.p5.note"), 1);

		// page 5: covers from the internet
		Composite pCovers = page(stack, 1);
		wrap(pCovers, text.get("dialog.welcome.p6.text"), 1);
		wrap(pCovers, text.get("dialog.welcome.p5.howto"), 1);
		bullets(pCovers, 1, text.get("dialog.welcome.p6.single"), text.get("dialog.welcome.p6.batch"));
		wrap(pCovers, text.get("dialog.welcome.p6.note"), 1);

		// page 6: done, what comes next, and how the wizard is opened again
		Composite p4 = page(stack, 1);
		Label summary = new Label(p4, SWT.WRAP);
		summary.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		wrap(p4, text.get("dialog.welcome.p4.next"), 1);
		bullets(p4, 1, text.get("dialog.welcome.p4.next1"), text.get("dialog.welcome.p4.next2"), text.get("dialog.welcome.p4.next3"), text.get("dialog.welcome.p4.next4"));
		wrap(p4, text.get("dialog.welcome.p4.again"), 1);
		Button restart = new Button(p4, SWT.CHECK);
		restart.setText(text.get("dialog.welcome.restart"));
		restart.setSelection(true);
		GridData restartData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		restartData.verticalIndent = 8;
		restart.setLayoutData(restartData);

		Composite row = new Composite(dlg, SWT.NONE);
		row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		row.setLayout(new GridLayout(4, false));
		// the covers at the bottom left, as large as the buttons next to it; only on the page about covers
		Button covers = new Button(row, SWT.PUSH);
		covers.setText(text.get("dialog.welcome.p5.covers"));
		covers.setToolTipText(text.get("dialog.welcome.p5.covers.tooltip"));
		covers.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
		covers.addListener(SWT.Selection, e -> actions.fetchCovers());
		Button back = new Button(row, SWT.PUSH);
		back.setText(text.get("dialog.welcome.back"));
		Button skip = new Button(row, SWT.PUSH);
		skip.setText(text.get("dialog.welcome.skip"));
		skip.setToolTipText(text.get("dialog.welcome.skip.tooltip"));
		Button next = new Button(row, SWT.PUSH);
		next.setText(text.get("dialog.welcome.next"));
		SavegamesPanel.roomy(new Button[] {covers, back, skip, next}, text.get("dialog.welcome.finish"));

		Composite[] all = {p1, p2, p3, pOnline, pCovers, p4};
		String[] titles = {text.get("dialog.welcome.p1.title"), text.get("dialog.welcome.p2.title"), text.get("dialog.welcome.p3.title"), text.get("dialog.welcome.p5.title"),
				text.get("dialog.welcome.p6.title"), text.get("dialog.welcome.p4.title")};
		int[] at = {0};
		boolean[] finished = {false};
		// the choices are read before the window is closed: its controls are gone after that
		boolean[] restartChosen = {true};
		Locale[] localeChosen = {now};
		int[] themeChosen = {theme == 3 ? 1: theme};
		// the choices of page 1 against how DBGL runs now
		java.util.function.Supplier<Locale> chosenLocale = () -> locales.getOrDefault(language.getText(), now);
		java.util.function.IntSupplier chosenTheme = () -> themeButtons[1].getSelection() ? 1: themeButtons[2].getSelection() ? 2: 0;
		java.util.function.BooleanSupplier pageOneChanged = () -> !chosenLocale.get().equals(now) || (chosenTheme.getAsInt() != (theme == 3 ? 1: theme));
		Runnable refresh = () -> {
			if (dlg.isDisposed())
				return;
			List<String[]> found = actions.dosboxVersions();
			for (org.eclipse.swt.widgets.Control c: versions.getChildren())
				c.dispose();
			if (found.isEmpty()) {
				Label none = new Label(versions, SWT.WRAP);
				none.setText(text.get("dialog.welcome.p2.none"));
				none.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1));
			}
			for (String[] v: found) {
				new Label(versions, SWT.NONE).setText("\u2713");
				new Label(versions, SWT.NONE).setText(v[0]);
				Label folder = new Label(versions, SWT.NONE);
				folder.setText(v[1]);
				folder.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			}
			DarkTheme.apply(versions);
			int n = actions.profileCount();
			games.setText(text.get("dialog.welcome.p3.count", new Object[] {n}));
			covers.setEnabled(n > 0);
			covers.setVisible(all[at[0]] == pCovers);
			summary.setText(text.get("dialog.welcome.p4.dosbox", new Object[] {found.size()}) + "\n" + text.get("dialog.welcome.p4.games", new Object[] {n}));
			restart.setVisible(pageOneChanged.getAsBoolean());
			((GridData)restart.getLayoutData()).exclude = !pageOneChanged.getAsBoolean();
			heading.setText(titles[at[0]]);
			back.setEnabled(at[0] > 0);
			next.setText(text.get(at[0] == all.length - 1 ? "dialog.welcome.finish": "dialog.welcome.next"));
			skip.setVisible(at[0] < all.length - 1);
			pages.topControl = all[at[0]];
			stack.layout(true, true);
			dlg.layout(true, true);
		};
		Runnable show = () -> {
			refresh.run();
			Point size = dlg.computeSize(SWT.DEFAULT, SWT.DEFAULT);
			Point current = dlg.getSize();
			if ((size.x > current.x) || (size.y > current.y))
				dlg.setSize(Math.max(size.x, current.x), Math.max(size.y, current.y));
		};
		locate.addListener(SWT.Selection, e -> {
			actions.locateDosbox();
			refresh.run();
		});
		download.addListener(SWT.Selection, e -> {
			actions.downloadDosbox(); // the list comes a moment later: shown when DBGL has entered the new version
			refresh.run();
		});
		actions.watchDosbox(() -> display.asyncExec(refresh));
		covers.addListener(SWT.Selection, e -> refresh.run());
		for (Button b: imports)
			if (b != null)
				b.addListener(SWT.Selection, e -> refresh.run());
		dlg.addListener(SWT.Activate, e -> display.asyncExec(refresh)); // after a window of its own (a download, an import)
		back.addListener(SWT.Selection, e -> {
			at[0] = Math.max(0, at[0] - 1);
			show.run();
		});
		next.addListener(SWT.Selection, e -> {
			if (at[0] == all.length - 1) {
				finished[0] = true;
				restartChosen[0] = restart.getSelection();
				localeChosen[0] = chosenLocale.get();
				themeChosen[0] = chosenTheme.getAsInt();
				dlg.close();
				return;
			}
			at[0]++;
			show.run();
		});
		// skipping keeps what was chosen on page 1 (language, appearance) and starts again for it, as the box on the last page would
		skip.addListener(SWT.Selection, e -> {
			finished[0] = true;
			localeChosen[0] = chosenLocale.get();
			themeChosen[0] = chosenTheme.getAsInt();
			dlg.close();
		});

		actions.enterFoundDosbox();
		DarkTheme.apply(dlg);
		refresh.run();
		dlg.pack();
		Point size = dlg.getSize();
		Rectangle area = parent.getMonitor().getClientArea(), pb = parent.getBounds();
		dlg.setSize(Math.min(size.x, area.width), Math.min(size.y, area.height));
		size = dlg.getSize();
		dlg.setLocation(Math.max(area.x, pb.x + (pb.width - size.x) / 2), Math.max(area.y, pb.y + (pb.height - size.y) / 2));
		dlg.setDefaultButton(next);
		next.setFocus();
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();

		settings.setBooleanValue(SECTION, SHOWN, true); // also when skipped or closed: it is in the Help menu
		if (!finished[0])
			return new Result(false, false);
		// page 1 is applied at the end only (the choices of the other pages took effect at once)
		Locale l = localeChosen[0];
		boolean languageChanged = !l.equals(now);
		if (languageChanged) {
			settings.setValue("locale", "language", l.getLanguage());
			settings.setValue("locale", "country", l.getCountry());
			settings.setValue("locale", "variant", l.getVariant());
		}
		int newTheme = themeChosen[0];
		boolean themeChanged = newTheme != (theme == 3 ? 1: theme);
		if (themeChanged)
			settings.setIntValue("gui", "theme", newTheme);
		boolean again = (languageChanged || themeChanged) && restartChosen[0];
		// a different dark mode needs a new process (it is fixed for the whole run), a language only a new main window
		boolean process = again && themeChanged && (DarkTheme.isDark(newTheme) != DarkTheme.forced());
		return new Result(again, process);
	}

	private static Composite page(Composite stack, int columns) {
		Composite c = new Composite(stack, SWT.NONE);
		GridLayout layout = new GridLayout(columns, false);
		layout.verticalSpacing = 8;
		c.setLayout(layout);
		return c;
	}

	private static void wrap(Composite parent, String s, int span) {
		Label l = new Label(parent, SWT.WRAP);
		l.setText(s);
		GridData d = new GridData(SWT.FILL, SWT.BEGINNING, true, false, span, 1);
		d.widthHint = 540;
		l.setLayoutData(d);
	}

	/** paragraphs with a bullet, a little indented */
	private static void bullets(Composite parent, int span, String... texts) {
		for (String t: texts) {
			Label l = new Label(parent, SWT.WRAP);
			l.setText("\u2022 " + t);
			GridData d = new GridData(SWT.FILL, SWT.BEGINNING, true, false, span, 1);
			d.widthHint = 528;
			d.horizontalIndent = 12;
			l.setLayoutData(d);
		}
	}

	private static void label(Composite parent, String s) {
		Label l = new Label(parent, SWT.NONE);
		l.setText(s);
	}

	private static Button radio(Composite parent, String s) {
		Button b = new Button(parent, SWT.RADIO);
		b.setText(s);
		return b;
	}

	private static Button push(Composite parent, String s, String tooltip) {
		Button b = new Button(parent, SWT.PUSH);
		b.setText(s);
		b.setToolTipText(tooltip);
		b.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		return b;
	}

	/** a button for an import and what it brings, on one row */
	private static Button importRow(Composite parent, ITextService text, String key, Runnable action) {
		Button b = new Button(parent, SWT.PUSH);
		b.setText(text.get("dialog.welcome.import." + key));
		b.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		b.addListener(SWT.Selection, e -> action.run());
		Label info = new Label(parent, SWT.NONE);
		info.setText(text.get("dialog.welcome.import." + key + ".info"));
		return b;
	}
}
