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
import java.util.List;
import java.util.function.BooleanSupplier;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;
import org.dbgl.service.TextService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


/**
 * Alpha 48: suggestions for "Other parameters" of a mount, as far as the kind of DOSBox has them and DBGL has no field of its
 * own for them: a list below the field while a word that starts with "-" is typed, or with Ctrl+Space. Only parameters found in the documentation
 * of that DOSBox (DOSBox Staging 0.83 manual, DOSBox-X wiki); the other kinds of DOSBox have fields for all of theirs.
 */
public final class MountOptionSuggest {

	/** what is put into the field, and the key of its explanation */
	public record Option(String insert, String key) {
		String name() {
			return insert.split(" |=")[0].trim();
		}
	}

	private MountOptionSuggest() {
	}

	/** the parameters to suggest, for a directory or for images */
	public static List<Option> options(Generation gen, boolean image) {
		List<Option> result = new ArrayList<>();
		if (gen == null)
			return result;
		if (gen == Generation.staging_0830) {
			result.add(new Option("-ro", "dialog.mount.opt.ro"));
			if (image)
				result.add(new Option("-chs ", "dialog.mount.opt.chs"));
		} else if (GenerationAwareConfigurable.familyIdx(gen) == GenerationAwareConfigurable.familyIdx(Generation.x_20241001)) {
			result.add(new Option("-ro", "dialog.mount.opt.ro"));
			if (image) {
				result.add(new Option("-ide 2m", "dialog.mount.opt.ide"));
				result.add(new Option("-o partidx=1", "dialog.mount.opt.partidx"));
				result.add(new Option("-chs", "dialog.mount.opt.xchs"));
				result.add(new Option("-lba", "dialog.mount.opt.lba"));
				result.add(new Option("-o joliet=0", "dialog.mount.opt.joliet"));
			} else {
				result.add(new Option("-nocachedir", "dialog.mount.opt.nocachedir"));
			}
		}
		return result;
	}

	public static void attach(Text field, Generation gen, BooleanSupplier image) {
		field.setToolTipText(field.getToolTipText() + "\n" + TextService.getInstance().get("dialog.mount.opt.how"));
		new Popup(field, gen, image);
	}

	/**
	 * the list below the field, which leaves the keyboard to it: what is typed filters it, Up/Down choose, Enter or Tab take
	 * the one chosen, Esc closes it
	 */
	private static final class Popup {
		private final Text field_;
		private final Generation gen_;
		private final BooleanSupplier image_;
		private Shell shell_;
		private Table table_;
		private List<Option> shown_ = List.of();

		Popup(Text field, Generation gen, BooleanSupplier image) {
			field_ = field;
			gen_ = gen;
			image_ = image;
			field.addListener(SWT.KeyDown, e -> {
				if ((e.character == ' ') && ((e.stateMask & SWT.MOD1) != 0)) {
					e.doit = false;
					update(true);
				} else if (isOpen() && ((e.keyCode == SWT.ARROW_DOWN) || (e.keyCode == SWT.ARROW_UP))) {
					e.doit = false;
					int i = table_.getSelectionIndex() + (e.keyCode == SWT.ARROW_DOWN ? 1: -1);
					table_.setSelection(Math.max(0, Math.min(i, table_.getItemCount() - 1)));
				}
			});
			field.addListener(SWT.Traverse, e -> {
				if (!isOpen())
					return;
				if ((e.detail == SWT.TRAVERSE_RETURN) || (e.detail == SWT.TRAVERSE_TAB_NEXT)) {
					e.doit = false; // not OK of the dialog, nor the next field
					e.detail = SWT.TRAVERSE_NONE;
					accept();
				} else if (e.detail == SWT.TRAVERSE_ESCAPE) {
					e.doit = false; // the dialog stays open
					e.detail = SWT.TRAVERSE_NONE;
					close();
				}
			});
			field.addListener(SWT.Modify, e -> field.getDisplay().asyncExec(() -> {
				if (!field.isDisposed())
					update(false);
			}));
			field.addListener(SWT.FocusOut, e -> field.getDisplay().timerExec(150, () -> { // after a click into the list
				if (!field.isDisposed() && !field.isFocusControl())
					close();
			}));
			field.getShell().addListener(SWT.Move, e -> close());
			field.addListener(SWT.Dispose, e -> close());
		}

		private boolean isOpen() {
			return (shell_ != null) && !shell_.isDisposed() && shell_.isVisible();
		}

		/** the word at the caret, back to the space before it */
		private int wordStart() {
			String t = field_.getText();
			int at = Math.min(field_.getCaretPosition(), t.length()), from = at;
			while ((from > 0) && !Character.isWhitespace(t.charAt(from - 1)))
				from--;
			return from;
		}

		private void update(boolean asked) {
			String t = field_.getText();
			int at = Math.min(field_.getCaretPosition(), t.length()), from = wordStart();
			String word = t.substring(from, at);
			if (!asked && !word.startsWith("-")) {
				close();
				return;
			}
			String now = " " + t.substring(0, from) + " " + t.substring(at) + " ";
			List<Option> fitting = new ArrayList<>();
			for (Option o: options(gen_, image_.getAsBoolean()))
				if (o.insert().startsWith(word) && !o.insert().trim().equals(word) && !now.contains(" " + o.name() + " ") && !now.contains(" " + o.name() + "="))
					fitting.add(o);
			if (fitting.isEmpty() && !(asked && options(gen_, image_.getAsBoolean()).isEmpty())) {
				close();
				return;
			}
			open(fitting);
		}

		private void open(List<Option> options) {
			if ((shell_ == null) || shell_.isDisposed()) {
				shell_ = new Shell(field_.getShell(), SWT.ON_TOP | SWT.TOOL | SWT.NO_FOCUS);
				shell_.setLayout(new FillLayout());
				table_ = new Table(shell_, SWT.SINGLE | SWT.FULL_SELECTION | SWT.NO_FOCUS);
				table_.addListener(SWT.MouseDown, e -> {
					TableItem item = table_.getItem(new Point(e.x, e.y));
					if (item != null) {
						table_.setSelection(item);
						accept();
					}
				});
			}
			table_.removeAll();
			org.dbgl.service.ITextService text = TextService.getInstance();
			shown_ = options;
			for (Option o: options)
				new TableItem(table_, SWT.NONE).setText(o.insert().trim() + "  \u2013  " + text.get(o.key()));
			if (options.isEmpty()) {
				TableItem none = new TableItem(table_, SWT.NONE);
				none.setText(text.get("dialog.mount.opt.none"));
				none.setForeground(field_.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
			} else {
				table_.setSelection(0);
			}
			Point size = table_.computeSize(SWT.DEFAULT, SWT.DEFAULT);
			Point at = field_.toDisplay(0, field_.getSize().y);
			shell_.setBounds(at.x, at.y, Math.max(size.x + 8, field_.getSize().x), Math.min(size.y + 4, 300));
			shell_.setVisible(true);
		}

		private void close() {
			if ((shell_ != null) && !shell_.isDisposed())
				shell_.dispose();
			shell_ = null;
		}

		private void accept() {
			int i = isOpen() ? table_.getSelectionIndex(): -1;
			if ((i < 0) || (i >= shown_.size())) {
				close();
				return;
			}
			String option = shown_.get(i).insert();
			close();
			String t = field_.getText();
			int at = Math.min(field_.getCaretPosition(), t.length()), from = wordStart();
			String before = t.substring(0, from), after = t.substring(at);
			String add = option + (option.endsWith(" ") || after.startsWith(" ") ? "": " ");
			field_.setText(before + add + after);
			field_.setSelection(before.length() + add.length());
			field_.setFocus();
		}
	}
}
