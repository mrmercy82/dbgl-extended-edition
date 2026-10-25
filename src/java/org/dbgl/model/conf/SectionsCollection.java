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
package org.dbgl.model.conf;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import org.dbgl.util.SystemUtils;


class SectionsCollection {

	private Map<String, Map<String, String>> sections_;

	SectionsCollection() {
		sections_ = new LinkedHashMap<>();
	}

	SectionsCollection(SectionsCollection sectionsCollection) {
		this();
		for (String sectionTitle: sectionsCollection.sections_.keySet())
			for (String sectionItem: sectionsCollection.sections_.get(sectionTitle).keySet())
				setValue(sectionTitle, sectionItem, sectionsCollection.sections_.get(sectionTitle).get(sectionItem));
	}

	boolean hasValue(String sectionTitle, String sectionItem) {
		return sections_.containsKey(sectionTitle) && sections_.get(sectionTitle).containsKey(sectionItem);
	}

	String getValue(String sectionTitle, String sectionItem) {
		if (sections_.containsKey(sectionTitle))
			return sections_.get(sectionTitle).get(sectionItem);
		return null;
	}

	void setValue(String sectionTitle, String sectionItem, String value) {
		getOrCreateSection(sectionTitle).put(sectionItem, value);
	}

	String[] getSectionNames() {
		Set<String> sectionNames = sections_.keySet();
		return sectionNames.toArray(new String[sectionNames.size()]);
	}

	String[] getItemNames(String sectionTitle) {
		Set<String> itemNames = new TreeSet<>(sections_.get(sectionTitle).keySet());
		return itemNames.toArray(new String[itemNames.size()]);
	}

	void removeSection(String sectionTitle) {
		sections_.remove(sectionTitle);
	}

	void removeValue(String sectionTitle, String sectionItem) {
		Map<String, String> section = sections_.get(sectionTitle);
		section.remove(sectionItem);
		if (section.isEmpty())
			sections_.remove(sectionTitle);
	}

	void removeDuplicateValuesIn(SectionsCollection collection) {
		for (Iterator<String> itSections = sections_.keySet().iterator(); itSections.hasNext();) {
			String sectionTitle = itSections.next();
			for (Iterator<String> itItems = sections_.get(sectionTitle).keySet().iterator(); itItems.hasNext();) {
				String sectionItem = itItems.next();
				if (getValue(sectionTitle, sectionItem).equals(collection.getValue(sectionTitle, sectionItem)))
					itItems.remove();
			}
			if (sections_.get(sectionTitle).isEmpty())
				itSections.remove();
		}
	}

	void removeUnequalValuesIn(SectionsCollection collection) {
		for (Iterator<String> itSections = sections_.keySet().iterator(); itSections.hasNext();) {
			String sectionTitle = itSections.next();
			for (Iterator<String> itItems = sections_.get(sectionTitle).keySet().iterator(); itItems.hasNext();) {
				String sectionItem = itItems.next();
				if (!getValue(sectionTitle, sectionItem).equals(collection.getValue(sectionTitle, sectionItem)))
					itItems.remove();
			}
			if (sections_.get(sectionTitle).isEmpty())
				itSections.remove();
		}
	}

	void removeValuesNotSetIn(SectionsCollection collection) {
		for (Iterator<String> itSections = sections_.keySet().iterator(); itSections.hasNext();) {
			String sectionTitle = itSections.next();
			for (Iterator<String> itItems = sections_.get(sectionTitle).keySet().iterator(); itItems.hasNext();) {
				String sectionItem = itItems.next();
				if (!collection.hasValue(sectionTitle, sectionItem))
					itItems.remove();
			}
			if (sections_.get(sectionTitle).isEmpty())
				itSections.remove();
		}
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		for (Entry<String, Map<String, String>> section: sections_.entrySet()) {
			result.append("[" + section.getKey() + "]" + SystemUtils.EOLN);
			for (Entry<String, String> item: section.getValue().entrySet()) {
				result.append(item.getKey()).append('=');
				result.append(item.getValue()).append(SystemUtils.EOLN);
			}
			result.append(SystemUtils.EOLN);
		}
		return result.toString();
	}

	private Map<String, String> getOrCreateSection(String sectionTitle) {
		return sections_.computeIfAbsent(sectionTitle, s -> new LinkedHashMap<>());
	}

	public void load(SectionsCollection sections) {
		for (Entry<String, Map<String, String>> section: sections.sections_.entrySet())
			for (Entry<String, String> item: section.getValue().entrySet())
				setValue(section.getKey(), item.getKey(), item.getValue());
	}
}
