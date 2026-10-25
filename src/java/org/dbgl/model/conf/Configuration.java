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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.service.ITextService;
import org.dbgl.util.SystemUtils;


public class Configuration extends Settings {

	private static final String AUTOEXEC_SECTION_NAME = "autoexec";
	private static final String[] CUSTOM_SECTION = {"#REM DBGL-CUSTOM", "#REM /DBGL-CUSTOM"};

	private String customSection_;
	private Autoexec autoexec_;

	public Configuration() {
		super();
		customSection_ = StringUtils.EMPTY;
		autoexec_ = new Autoexec();
	}

	public Configuration(Configuration configuration) {
		this();
		sections_ = new SectionsCollection(configuration.getSections());
		customSection_ = configuration.getCustomSection();
		autoexec_ = new Autoexec(configuration.getAutoexec());
	}

	public String getCustomSection() {
		return customSection_;
	}

	public void setCustomSection(String customSection) {
		customSection_ = customSection;
	}

	public Autoexec getAutoexec() {
		return autoexec_;
	}

	public void setAutoexec(Autoexec autoexec) {
		autoexec_ = autoexec;
	}

	public String reloadWithAutoexec(ITextService text, List<Mount> dbMounts) throws IOException {
		return reloadWithAutoexec(text, dbMounts, null);
	}

	public String reloadWithAutoexec(ITextService text, List<Mount> dbMounts, File cwd) throws IOException {
		sections_ = new SectionsCollection();
		customSection_ = StringUtils.EMPTY;
		autoexec_ = new Autoexec();

		return loadWithAutoexec(text, dbMounts, cwd);
	}

	public String loadWithAutoexec(ITextService text, List<Mount> dbMounts, File cwd) throws IOException {
		File file = fileLocation_.getCanonicalFile();
		if (file == null || !file.isFile() || !file.canRead())
			throw new IOException(text.get("general.error.openfile", new Object[] {String.valueOf(file)}));

		try (FileReader reader = new FileReader(file)) {
			return loadDataWithAutoexec(text, reader, file, dbMounts, cwd);
		}
	}

	public String loadDataWithAutoexec(ITextService text, String data, File file, List<Mount> dbMounts) throws IOException {
		return loadDataWithAutoexec(text, data, file, dbMounts, null);
	}

	public String loadDataWithAutoexec(ITextService text, String data, File file, List<Mount> dbMounts, File cwd) throws IOException {
		try (StringReader reader = new StringReader(data)) {
			return loadDataWithAutoexec(text, reader, file, dbMounts, cwd);
		}
	}

	private String loadDataWithAutoexec(ITextService text, Reader reader, File file, List<Mount> dbMounts, File cwd) throws IOException {
		StringBuilder warningsLog = new StringBuilder();

		List<String> autoexecLines = new ArrayList<>();
		List<String> customLines = new ArrayList<>();
		warningsLog.append(loadData(text, reader, file, AUTOEXEC_SECTION_NAME, autoexecLines, CUSTOM_SECTION, customLines));
		if (!autoexecLines.isEmpty())
			warningsLog.append(autoexec_.load(autoexecLines, dbMounts, cwd));
		if (!customLines.isEmpty())
			customSection_ = StringUtils.join(customLines, SystemUtils.EOLN);

		return warningsLog.toString();
	}

	public void load(Configuration conf) {
		sections_.load(conf.getSections());
	}

	public void save(boolean prepareOnly, List<Mount> combinedMountingPoints) throws IOException {
		File file = fileLocation_.getCanonicalFile();
		FileUtils.forceMkdirParent(file);
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
			writeData(writer, prepareOnly, combinedMountingPoints);
		}
	}

	private void writeData(BufferedWriter writer, boolean prepareOnly, List<Mount> combinedMountingPoints) throws IOException {
		writer.write(sections_.toString());
		if (StringUtils.isNotBlank(customSection_)) {
			StringBuilder result = new StringBuilder();
			result.append(CUSTOM_SECTION[0]).append(SystemUtils.EOLN);
			result.append(customSection_).append(SystemUtils.EOLN);
			result.append(CUSTOM_SECTION[1]).append(SystemUtils.EOLN).append(SystemUtils.EOLN);
			writer.write(result.toString());
		}
		writer.write(autoexec_.toString(prepareOnly, combinedMountingPoints));
	}

	public void removeUnequalValuesIn(Configuration configuration) {
		super.removeUnequalValuesIn(configuration);
		if (!Strings.CS.equals(customSection_, configuration.getCustomSection()))
			customSection_ = null;
		autoexec_.removeUnequalValuesIn(configuration.autoexec_);
	}

	public void removeUnnecessaryMounts(Configuration configuration) {
		autoexec_.removeUnnecessaryMounts(configuration.autoexec_);
	}

	public String toString(List<Mount> combinedMountingPoints) {
		StringWriter result = new StringWriter();
		try (BufferedWriter writer = new BufferedWriter(result)) {
			writeData(writer, false, combinedMountingPoints);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return result.toString();
	}
}
