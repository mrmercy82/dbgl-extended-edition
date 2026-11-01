/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-10-31. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.model.FileLocation;
import org.dbgl.service.ITextService;
import org.dbgl.util.StringRelatedUtils;


public class Settings {

	protected FileLocation fileLocation_;
	protected SectionsCollection sections_;

	public Settings() {
		sections_ = new SectionsCollection();
		fileLocation_ = null;
	}

	public SectionsCollection getSections() {
		return sections_;
	}

	public String[] getSectionNames() {
		return sections_.getSectionNames();
	}

	public void setSections(SectionsCollection sections) {
		sections_ = sections;
	}

	public FileLocation getFileLocation() {
		return fileLocation_;
	}

	public File getFile() {
		return fileLocation_.getFile();
	}

	public File getCanonicalFile() {
		return fileLocation_.getCanonicalFile();
	}

	public void setFileLocation(FileLocation fileLocation) {
		fileLocation_ = fileLocation;
	}

	public void removeSection(String sectionTitle) {
		sections_.removeSection(sectionTitle);
	}

	public void clearSections() {
		sections_ = new SectionsCollection();
	}

	public String load(ITextService text) throws IOException {
		File file = fileLocation_.getCanonicalFile();
		if (file == null || !file.isFile() || !file.canRead())
			throw new IOException(text.get("general.error.openfile", new Object[] {String.valueOf(file)}));

		try (FileReader reader = new FileReader(file)) {
			return loadData(text, reader, file, null, null, null, null);
		}
	}

	protected String loadData(ITextService text, Reader reader, File file, String specialSection, List<String> specialSectionLines, String[] customSection, List<String> customSectionLines)
			throws IOException {
		StringBuilder warningsLog = new StringBuilder();

		try (BufferedReader configData = new BufferedReader(reader)) {
			String orgLine = null;
			String currSectionTitle = null;
			boolean lastItemHadMissingSection = false;
			boolean customSectionInProgress = false;

			for (int lineNumber = 1; (orgLine = configData.readLine()) != null; lineNumber++) {
				String textLine = orgLine.trim();

				if (ArrayUtils.isNotEmpty(customSection) && textLine.startsWith(customSection[0])) {
					customSectionInProgress = true;
				} else if (ArrayUtils.isNotEmpty(customSection) && textLine.startsWith(customSection[1])) {
					customSectionInProgress = false;
				} else if (customSectionInProgress) {
					customSectionLines.add(orgLine);
				} else if ((textLine.length() > 0) && (textLine.charAt(0) != '#')) {
					if (textLine.charAt(0) == '[') { // a new section starts here
						int start = textLine.indexOf(('['));
						int end = textLine.lastIndexOf(']');
						if (end == -1) {
							warningsLog.append(text.get("general.error.parseconf", new Object[] {file.getPath(), lineNumber, textLine}));
						} else {
							currSectionTitle = textLine.substring(start + 1, end);
						}
					} else { // an item starts here
						if (currSectionTitle == null) { // value before section
							if (!lastItemHadMissingSection) {
								warningsLog.append(text.get("general.error.sectionmissing", new Object[] {file.getPath(), lineNumber, textLine}));
							}
							lastItemHadMissingSection = true;
						} else if (currSectionTitle.equals(specialSection)) {
							specialSectionLines.add(textLine);
						} else { // normal config item
							int end = textLine.indexOf('=');
							if (end == -1) {
								warningsLog.append(text.get("general.error.parseconf", new Object[] {file.getPath(), lineNumber, textLine}));
							} else {
								String name = textLine.substring(0, end).trim();
								String value = textLine.substring(end + 1).trim();
								sections_.setValue(currSectionTitle, name.toLowerCase(), value);
							}
							lastItemHadMissingSection = false;
						}
					}
				}
			}
		}

		return warningsLog.toString();
	}

	public void save() throws IOException {
		File file = fileLocation_.getCanonicalFile();
		FileUtils.forceMkdirParent(file);
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
			writeData(writer);
		}
	}

	private void writeData(BufferedWriter writer) throws IOException {
		writer.write(sections_.toString());
	}

	public boolean hasValue(String sectionTitle, String sectionItem) {
		return sections_.hasValue(sectionTitle, sectionItem);
	}

	public String getValue(String sectionTitle, String sectionItem) {
		return sections_.getValue(sectionTitle, sectionItem);
	}

	public String getValue(String sectionTitle, String sectionItem, String defaultValue) {
		if (hasValue(sectionTitle, sectionItem))
			return sections_.getValue(sectionTitle, sectionItem);
		return defaultValue;
	}

	public boolean getBooleanValue(String sectionTitle, String sectionItem) {
		// DOSBox Staging 0.83+ writes booleans as on/off instead of true/false
		return Strings.CI.equalsAny(StringUtils.trim(sections_.getValue(sectionTitle, sectionItem)), "true", "on", "yes", "1", "enabled");
	}

	public int getIntValue(String sectionTitle, String sectionItem) {
		try {
			return Integer.parseInt(sections_.getValue(sectionTitle, sectionItem));
		} catch (NumberFormatException e) {
			return -1; // value is not a number
		}
	}

	public String getMultilineValue(String sectionTitle, String sectionItem, String delimiter) {
		return StringRelatedUtils.stringArrayToString(getValues(sectionTitle, sectionItem), delimiter);
	}

	public String[] getValues(String sectionTitle, String sectionItem) {
		return splitValues(sections_.getValue(sectionTitle, sectionItem));
	}

	public static String[] splitValues(String val) {
		if (StringUtils.isBlank(val)) {
			return new String[0];
		}
		String[] res = val.split(" ");
		for (int i = 0; i < res.length; i++) {
			res[i] = res[i].replace("<space>", " ");
		}
		return res;
	}

	public static String combineValues(String text, String delimiter) {
		return text.replace(" ", "<space>").replace(delimiter, " ").trim();
	}

	public int[] getIntValues(String sectionTitle, String sectionItem) {
		return StringRelatedUtils.stringToIntArray(sections_.getValue(sectionTitle, sectionItem));
	}

	public boolean[] getBooleanValues(String sectionTitle, String sectionItem) {
		return StringRelatedUtils.stringToBooleanArray(sections_.getValue(sectionTitle, sectionItem));
	}

	public String[] getItemNames(String sectionTitle) {
		return sections_.getItemNames(sectionTitle);
	}

	public void setValue(String sectionTitle, String sectionItem, String value) {
		sections_.setValue(sectionTitle, sectionItem, value);
	}

	public void setValue(String sectionTitle, String sectionItem, boolean value) {
		sections_.setValue(sectionTitle, sectionItem, String.valueOf(value));
	}

	public void setValue(String sectionTitle, String sectionItem, int value) {
		sections_.setValue(sectionTitle, sectionItem, String.valueOf(value));
	}

	public void setMultilineValue(String sectionTitle, String sectionItem, String values, String delimiter) {
		sections_.setValue(sectionTitle, sectionItem, combineValues(values, delimiter));
	}

	public void setValues(String sectionTitle, String sectionItem, int[] values) {
		sections_.setValue(sectionTitle, sectionItem, StringUtils.join(values, ' '));
	}

	public void setValues(String sectionTitle, String sectionItem, boolean[] values) {
		sections_.setValue(sectionTitle, sectionItem, StringUtils.replaceChars(Arrays.toString(values), "[,]", ""));
	}

	public void updateValue(String sectionTitle, String sectionItem, String value) {
		if (sections_.hasValue(sectionTitle, sectionItem))
			sections_.setValue(sectionTitle, sectionItem, value);
	}

	public void switchSetting(String oldSection, String oldItem, String newSection, String newItem) {
		if (sections_.hasValue(oldSection, oldItem)) {
			sections_.setValue(newSection, newItem, getValue(oldSection, oldItem));
			sections_.removeValue(oldSection, oldItem);
		}
	}

	public void removeValue(String sectionTitle, String sectionItem) {
		sections_.removeValue(sectionTitle, sectionItem);
	}

	public void removeValueIfSet(String sectionTitle, String sectionItem) {
		if (hasValue(sectionTitle, sectionItem))
			removeValue(sectionTitle, sectionItem);
	}
	
	public void removeValueIfAny(String section, String item, String... values) {
		if (hasValue(section, item) && Strings.CI.equalsAny(getValue(section, item), values))
			removeValue(section, item);
	}
	
	public void removeValueIfNone(String section, String item, String... values) {
		if (hasValue(section, item) && !Strings.CI.equalsAny(getValue(section, item), values))
			removeValue(section, item);
	}
	
	public void setValueIfAnyOr(String section, String item, String val1, String val2, String... values) {
		if (hasValue(section, item))
			setValue(section, item, Strings.CI.equalsAny(getValue(section, item), values) ? val1: val2);
	}
	
	public void setValueIfAny(String section, String item, String val, String... values) {
		if (hasValue(section, item) && Strings.CI.equalsAny(getValue(section, item), values))
			setValue(section, item, val);
	}

	public void removeDuplicateValuesIn(Settings configuration) {
		sections_.removeDuplicateValuesIn(configuration.sections_);
	}

	public void removeUnequalValuesIn(Settings configuration) {
		sections_.removeUnequalValuesIn(configuration.sections_);
	}

	public void removeValuesNotSetIn(Settings configuration) {
		sections_.removeValuesNotSetIn(configuration.sections_);
	}

	@Override
	public String toString() {
		StringWriter result = new StringWriter();
		try (BufferedWriter writer = new BufferedWriter(result)) {
			writeData(writer);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return result.toString();
	}
}
