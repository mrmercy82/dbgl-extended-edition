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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.dbgl.gui.abstractdialog.BaseDialog;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.ITextService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.ConfCharset;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


/**
 * Asked before profiles are saved whose configuration file has umlauts in its [autoexec] in another charset than their DOSBox
 * version reads it with (see {@link ConfCharset}): DOSBox would not find the folder. Converting is recommended; keeping it as
 * it is (as DBGL does without asking) and not saving at all are the other choices. Nothing is asked when the charset fits.
 */
public final class CharsetQuestion {

	public enum Answer {
		KEEP, CONVERT, CANCEL
	}

	/** true: the charset of a file is kept without asking ("Do not ask again", undone in the settings) */
	public static final String SECTION = "profile", ITEM = "charsetkeepalways";

	private CharsetQuestion() {
	}

	/** for the DOSBox version each profile uses */
	public static Answer ask(Shell shell, Collection<Profile> profiles) {
		return ask(shell, profiles, Profile::getDosboxVersion);
	}

	/**
	 * Looks at the files themselves (the profiles need not be read). With CONVERT, the given profiles are set to be saved in the
	 * charset their DOSBox version needs; a profile read again afterwards gets {@link #applyTo} after reading (and switching).
	 *
	 * @param target the DOSBox version each profile will use (e.g. the one it is switched to)
	 * @return KEEP if the charset fits for all of them, or the user chose to always keep it
	 */
	public static Answer ask(Shell shell, Collection<Profile> profiles, Function<Profile, DosboxVersion> target) {
		if ((shell == null) || SettingsService.getInstance().getBooleanValue(SECTION, ITEM))
			return Answer.KEEP;
		Map<Profile, ConfCharset.Mismatch> found = new LinkedHashMap<>();
		for (Profile p: profiles) {
			ConfCharset.Mismatch m = ConfCharset.mismatch(p, target.apply(p));
			if (m != null)
				found.put(p, m);
		}
		if (found.isEmpty())
			return Answer.KEEP;
		ITextService t = TextService.getInstance();
		StringBuilder list = new StringBuilder();
		found.forEach((p, m) -> list.append(p.getTitle()).append(" (").append(target.apply(p).getTitle()).append("): ").append(m.file().displayName()).append(" → ")
				.append(m.needed().displayName()).append(":  ").append(m.line()).append('\n'));
		Map.Entry<Profile, ConfCharset.Mismatch> first = found.entrySet().iterator().next();
		String msg = found.size() == 1
				? t.get("dialog.charset.single2", new Object[] {first.getKey().getTitle(), target.apply(first.getKey()).getTitle(), first.getValue().file().displayName(),
					first.getValue().needed().displayName()})
				: t.get("dialog.charset.multi2", new Object[] {found.size()});

		Display display = shell.getDisplay();
		Shell dlg = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
		dlg.setText(t.get("general.confirmation"));
		BaseDialog.inheritWindowImage(dlg, shell);
		dlg.setLayout(new GridLayout(2, false));
		Label icon = new Label(dlg, SWT.NONE);
		icon.setImage(display.getSystemImage(SWT.ICON_WARNING));
		icon.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		Label text = new Label(dlg, SWT.WRAP);
		text.setText(msg.replace("&", "&&"));
		GridData textData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		textData.horizontalIndent = 8;
		textData.widthHint = 620;
		text.setLayoutData(textData);
		Label example = new Label(dlg, SWT.NONE);
		example.setText(t.get("dialog.charset.example2"));
		example.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		Text log = new Text(dlg, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		log.setText(list.toString().trim().replace("\n", log.getLineDelimiter()));
		GridData logData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		logData.widthHint = 660;
		logData.heightHint = Math.min(240, found.size() * log.getLineHeight() + 12);
		log.setLayoutData(logData);
		Button always = new Button(dlg, SWT.CHECK);
		always.setText(t.get("dialog.charset.always2"));
		always.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));

		Composite buttons = new Composite(dlg, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.END, SWT.END, true, false, 2, 1));
		buttons.setLayout(new GridLayout(3, true));
		Answer[] answer = {Answer.CANCEL};
		boolean[] keepAlways = {false}; // read while the dialog is open (afterwards its controls are gone)
		String[] keys = {"dialog.charset.convert2", "dialog.charset.keep", "button.cancel"};
		Answer[] answers = {Answer.CONVERT, Answer.KEEP, Answer.CANCEL};
		Button convert = null;
		for (int i = 0; i < keys.length; i++) {
			Answer a = answers[i];
			Button button = new Button(buttons, SWT.PUSH);
			button.setText(t.get(keys[i]));
			GridData bd = new GridData(SWT.FILL, SWT.CENTER, true, false);
			bd.widthHint = Math.max(80, button.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
			button.setLayoutData(bd);
			button.addListener(SWT.Selection, e -> {
				answer[0] = a;
				keepAlways[0] = always.getSelection();
				dlg.close();
			});
			if (a == Answer.CONVERT) {
				dlg.setDefaultButton(button); // recommended: as it is, DOSBox does not find the folder
				convert = button;
			}
		}
		Button convertButton = convert;
		// "always keep" only goes with keeping (converting stays a choice made each time)
		always.addListener(SWT.Selection, e -> convertButton.setEnabled(!always.getSelection()));

		dlg.pack();
		Rectangle area = shell.getMonitor().getClientArea();
		org.eclipse.swt.graphics.Point size = dlg.getSize();
		size.x = Math.min(size.x, area.width);
		size.y = Math.min(size.y, area.height);
		dlg.setSize(size);
		Rectangle parent = shell.getBounds();
		dlg.setLocation(Math.max(area.x, Math.min(parent.x + (parent.width - size.x) / 2, area.x + area.width - size.x)),
			Math.max(area.y, Math.min(parent.y + (parent.height - size.y) / 2, area.y + area.height - size.y)));
		dlg.open();
		while (!dlg.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();

		System.out.println("Charset of " + found.size() + " configuration file(s) not as their DOSBox version reads it: " + answer[0]);
		if ((answer[0] == Answer.KEEP) && keepAlways[0])
			SettingsService.getInstance().setBooleanValue(SECTION, ITEM, true);
		if (answer[0] == Answer.CONVERT)
			found.forEach((p, m) -> ConfCharset.writeIn(p, m.needed()));
		return answer[0];
	}

	/** after reading the profile (and switching it): with CONVERT, it is saved in the charset its DOSBox version needs */
	public static void applyTo(Profile p, Answer answer) {
		if (answer != Answer.CONVERT)
			return;
		ConfCharset.Mismatch m = ConfCharset.mismatch(p, p.getDosboxVersion());
		if (m != null)
			ConfCharset.writeIn(p, m.needed());
	}

	/** the profiles of the collection whose file does not fit (for tests and reports) */
	public static java.util.List<Profile> notFitting(Collection<Profile> profiles) {
		java.util.List<Profile> result = new ArrayList<>();
		for (Profile p: profiles)
			if (ConfCharset.mismatch(p, p.getDosboxVersion()) != null)
				result.add(p);
		return result;
	}
}
