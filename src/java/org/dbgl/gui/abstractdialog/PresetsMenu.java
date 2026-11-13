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
package org.dbgl.gui.abstractdialog;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import org.dbgl.gui.controls.Mess_;
import org.dbgl.service.TextService;
import org.dbgl.util.Presets;
import org.dbgl.util.Presets.Preset;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;


/**
 * The menu entries for presets: in the profile/template dialog (button 'Presets') and in the main window, where a preset is
 * applied to all selected profiles at once.
 */
public final class PresetsMenu {

	private PresetsMenu() {
	}

	/** the built-in presets, and below a separator the own ones */
	public static void addPresetItems(Menu menu, Consumer<Preset> onApply) {
		var text = TextService.getInstance();
		for (Preset preset: Presets.BUILT_IN) {
			if (preset.key().equals(Presets.FIRST_PICTURE_PRESET)) { // alpha 48: the picture presets as a group of their own
				new MenuItem(menu, SWT.SEPARATOR);
				MenuItem header = new MenuItem(menu, SWT.PUSH);
				header.setText(text.get("dialog.preset.group.picture").replace("&", "&&"));
				header.setEnabled(false);
			}
			MenuItem item = new MenuItem(menu, SWT.PUSH);
			item.setText(text.get(preset.key()).replace("&", "&&"));
			item.addListener(SWT.Selection, e -> onApply.accept(preset));
		}
		List<Preset> own = Presets.listOwn();
		if (!own.isEmpty()) {
			new MenuItem(menu, SWT.SEPARATOR);
			for (Preset preset: own) {
				MenuItem item = new MenuItem(menu, SWT.PUSH);
				item.setText(preset.key().replace("&", "&&"));
				item.addListener(SWT.Selection, e -> onApply.accept(preset));
			}
		}
	}

	/** edit an own preset in a small text dialog */
	public static void edit(Shell shell, String name) {
		try {
			String[] result = new org.dbgl.gui.dialog.PresetEditDialog(shell, name, Presets.read(name)).open();
			if (result != null)
				Presets.update(name, result[0], result[1]);
		} catch (IOException ex) {
			Mess_.on(shell).exception(ex).warning();
		}
	}

	/** the name of a preset as shown to the user */
	public static String nameOf(Preset preset) {
		return preset.builtIn() ? TextService.getInstance().get(preset.key()): preset.key();
	}

	/** the menu of the presets button: built-in and own presets, save the current values as a preset, delete own presets */
	public static void show(Shell shell, Button button, Consumer<Preset> onApply, Runnable onSave) {
		Menu menu = new Menu(button);
		var text = TextService.getInstance();
		addPresetItems(menu, onApply);
		List<Preset> own = Presets.listOwn();
		new MenuItem(menu, SWT.SEPARATOR);
		MenuItem save = new MenuItem(menu, SWT.PUSH);
		save.setText(text.get("dialog.template.presets.save"));
		save.addListener(SWT.Selection, e -> onSave.run());
		if (!own.isEmpty()) {
			MenuItem edit = new MenuItem(menu, SWT.CASCADE);
			edit.setText(text.get("dialog.template.presets.edit"));
			Menu editMenu = new Menu(edit);
			edit.setMenu(editMenu);
			for (Preset preset: own) {
				MenuItem item = new MenuItem(editMenu, SWT.PUSH);
				item.setText(preset.key().replace("&", "&&"));
				item.addListener(SWT.Selection, e -> edit(shell, preset.key()));
			}
			MenuItem delete = new MenuItem(menu, SWT.CASCADE);
			delete.setText(text.get("dialog.template.presets.delete"));
			Menu deleteMenu = new Menu(delete);
			delete.setMenu(deleteMenu);
			for (Preset preset: own) {
				MenuItem item = new MenuItem(deleteMenu, SWT.PUSH);
				item.setText(preset.key().replace("&", "&&"));
				item.addListener(SWT.Selection, e -> {
					if (Mess_.on(shell).key("dialog.template.presets.confirmdelete", new Object[] {preset.key()}).confirm()) {
						try {
							Presets.delete(preset.key());
						} catch (IOException ex) {
							Mess_.on(shell).exception(ex).warning();
						}
					}
				});
			}
		}
		Rectangle r = button.getBounds();
		menu.setLocation(button.getParent().toDisplay(r.x, r.y + r.height));
		menu.setVisible(true);
	}
}
