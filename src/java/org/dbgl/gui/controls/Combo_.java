/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
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

import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.ITitledEntity;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;


public final class Combo_ {

	public static final String DYN_OPT_SECTION = "dynOptSection";
	public static final String DYN_OPT_ITEM = "dynOptItem";
	public static final String DYN_SECTION_ITEM_GETTER = "dynSectionItemGetter";
	public static final String DYN_ITEMS_GETTER = "dynItemsGetter";

	private Combo_() {
	}

	public static Builder on(Composite composite) {
		return new Builder(composite);
	}

	public static final class Builder extends ControlBuilder<Builder> {
		int visibleItemCount_ = 10;
		String tooltip_;
		String[] items_;
		int select_ = -1;
		boolean autoSelect_ = false;
		String dynOptSection_, dynOptItem_;
		
		Function<GenerationAwareConfigurable, String> dynSectionItemGetter_;
		Function<GenerationAwareConfigurable, String[]> dynItemsGetter_;

		Builder(Composite composite) {
			super(composite, SWT.READ_ONLY, SWT.BEGINNING, SWT.CENTER, false, false);
		}

		public Builder tooltip(String key) {
			tooltip_ = TextService.getInstance().get(key);
			return this;
		}

		public Builder editable() {
			return style(SWT.NONE);
		}

		public Builder wide() {
			horizontalAlignment_ = SWT.FILL;
			grabExcessHorizontalSpace_ = true;
			return this;
		}

		public Builder autoSelect(String[] values) {
			autoSelect_ = true;
			items_ = values;
			wide();
			editable();
			visibleItemCount(15);
			return this;
		}

		public Builder autoSelect(List<ITitledEntity> values) {
			return autoSelect(values.stream().map(ITitledEntity::getTitle).toArray(String[]::new));
		}

		public Builder visibleItemCount(int count) {
			visibleItemCount_ = count;
			return this;
		}

		public Builder items(String[] items) {
			items_ = items;
			return this;
		}

		public Builder items(List<String> items) {
			items_ = items.toArray(new String[items.size()]);
			return this;
		}

		public Builder dynItem(Function<GenerationAwareConfigurable, String> getMethod) {
			dynSectionItemGetter_ = getMethod;
			return this;
		}
		
		public Builder dynItems(Function<GenerationAwareConfigurable, String[]> getMethod) {
			dynItemsGetter_ = getMethod;
			return this;
		}
		
		public Builder items(String section, String item) {
			items_ = SettingsService.getInstance().getValues(section, item);
			dynOptSection_ = section;
			dynOptItem_ = item;
			return this;
		}

		public Builder select(int select) {
			select_ = select;
			return this;
		}

		public Combo build() {
			Combo combo = new Combo(composite_, style_);
			if (DarkTheme.forced()) {
				combo.setBackground(DarkTheme.inputBackground);
				combo.setForeground(DarkTheme.defaultForeground);
			}
			combo.setLayoutData(layoutData());
			combo.setVisibleItemCount(visibleItemCount_);

			if (tooltip_ != null)
				combo.setToolTipText(tooltip_);
			if (items_ != null)
				combo.setItems(items_);
			if (select_ >= 0)
				combo.select(select_);

			if (StringUtils.isNotBlank(dynOptSection_)) {
				combo.setData(DYN_OPT_SECTION, dynOptSection_);
				combo.setData(DYN_OPT_ITEM, dynOptItem_);
			}
			
			if (dynSectionItemGetter_ != null)
				combo.setData(DYN_SECTION_ITEM_GETTER, dynSectionItemGetter_);
			if (dynItemsGetter_ != null)
				combo.setData(DYN_ITEMS_GETTER, dynItemsGetter_);

			if (autoSelect_) {
				combo.setData("currentLength", 0);
				combo.setData("mutex", false);
				combo.addModifyListener(event -> {
					if (!(boolean)combo.getData("mutex")) {
						String text = combo.getText();
						int newLength = text.length();
						if (newLength > (int)combo.getData("currentLength")) {
							OptionalInt opt = IntStream.range(0, combo.getItemCount()).filter(i -> combo.getItem(i).toLowerCase().startsWith(text.toLowerCase())).findFirst();
							if (opt.isPresent()) {
								combo.setData("mutex", true);
								combo.setText(text + combo.getItem(opt.getAsInt()).substring(newLength));
								combo.setSelection(new Point(newLength, combo.getText().length()));
								combo.setData("mutex", false);
							}
						}
						combo.setData("currentLength", newLength);
					}
				});
			}
			return combo;
		}
	}
}
