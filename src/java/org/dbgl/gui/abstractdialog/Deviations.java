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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.entity.TemplateProfileBase;


/**
 * The settings of a profile or template that differ from the defaults of its DOSBox version ("Differences from the defaults"),
 * and setting some of them back to those defaults (alpha 48: also from the menu of a profile in the main window).
 */
public final class Deviations {

	/** one setting: its own value, and that of the DOSBox version (null if the DOSBox version doesn't have it) */
	public record Entry(String section, String item, String value, String standard) {
		public String key() {
			return section + '/' + item;
		}
	}

	private Deviations() {
	}

	/** as the configuration of the profile or template is now */
	public static List<Entry> of(TemplateProfileBase configurable) {
		List<Entry> result = new ArrayList<>();
		if (configurable.getDosboxVersion() == null)
			return result;
		Configuration own = configurable.getConfiguration();
		Configuration dosboxConf = configurable.getDosboxVersion().getConfiguration();
		for (String section: own.getSectionNames()) {
			for (String item: own.getItemNames(section)) {
				String value = own.getValue(section, item);
				String def = dosboxConf.getValue(section, item);
				if (StringUtils.equals(value, def) || ExtraSettings.DBGL_MANAGED_SETTINGS.contains(section + '/' + item))
					continue;
				result.add(new Entry(section, item, value, def));
			}
		}
		return result;
	}

	/**
	 * back to the defaults of the DOSBox version: the own value goes (a profile that keeps all settings gets the default written
	 * instead). A setting the DOSBox version doesn't know goes as well.
	 *
	 * @return the number of settings changed
	 */
	public static int reset(TemplateProfileBase configurable, Collection<Entry> entries) {
		Configuration own = configurable.getConfiguration();
		int count = 0;
		for (Entry e: entries) {
			if (!own.hasValue(e.section(), e.item()))
				continue;
			if ((e.standard() != null) && configurable.keepsAllSettings())
				own.setValue(e.section(), e.item(), e.standard());
			else
				own.removeValue(e.section(), e.item());
			count++;
		}
		return count;
	}

	/** as a text, as "Differences from the defaults" showed it before alpha 48 (to copy) */
	public static String text(String title, List<Entry> entries, String defaultText, String noneText, String eoln) {
		StringBuilder sb = new StringBuilder(title).append(eoln).append(eoln);
		for (Entry e: entries)
			sb.append('[').append(e.section()).append("] ").append(e.item()).append(" = ").append(e.value()).append("   (").append(defaultText).append(": ")
					.append(e.standard() == null ? "-": e.standard()).append(')').append(eoln);
		if (entries.isEmpty())
			sb.append(noneText);
		return sb.toString();
	}
}
