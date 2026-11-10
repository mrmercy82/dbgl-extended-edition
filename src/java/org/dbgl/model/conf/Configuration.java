/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-07. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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

	/**
	 * A comment line at the top of a profile configuration: this profile keeps all settings of its DOSBox version, not only
	 * those that differ (see FullConfProfiles). DOSBox reads it as a comment; it travels with the file, e.g. on export.
	 */
	public static final String ALL_SETTINGS_MARKER = "# DBGL: all DOSBox settings are kept in this profile";

	private String customSection_;
	private Autoexec autoexec_;
	private boolean allSettings_;
	private java.nio.charset.Charset charset_; // null: the charset of the system (as FileReader and FileWriter use it)

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
		allSettings_ = configuration.allSettings_;
		charset_ = configuration.charset_;
	}

	/**
	 * The charset the file was read with when it was not the one of the system: a file written on Windows with umlauts
	 * (Windows-1252), read where the system charset is UTF-8 (macOS, Linux, Java 18 and newer on Windows). Saving writes it in
	 * that charset again, so the umlauts stay as they were (read as UTF-8 they were lost: "?" in the file).
	 *
	 * @return null if the file was read with the charset of the system
	 */
	public java.nio.charset.Charset getCharset() {
		return charset_;
	}

	/** @param charset null to save with the charset of the system (e.g. to convert the file to UTF-8) */
	public void setCharset(java.nio.charset.Charset charset) {
		charset_ = charset;
	}

	/** @return true if the file was not written in the charset of the system, see {@link #getCharset()} */
	public boolean isLegacyCharset() {
		return charset_ != null;
	}

	/** the text of the file in the charset of the system; if it is not valid in it, as Windows-1252 (remembered for saving) */
	private String readText(File file) throws IOException {
		byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
		charset_ = legacyCharsetOf(bytes);
		return new String(bytes, charset_ != null ? charset_: java.nio.charset.Charset.defaultCharset());
	}

	/**
	 * The charset of a file when it is not the one of the system, both ways:
	 * <ul>
	 * <li>the system uses UTF-8 (macOS, Linux, Java 18 and newer): a file that is not valid UTF-8 was written with the code page
	 * of Windows (Windows-1252 in Western Europe, as DBGL with Java 17 and older wrote it on Windows)</li>
	 * <li>the system uses the code page of Windows (Java 17 and older on Windows): a file with umlauts that is valid UTF-8 was
	 * written in UTF-8 (by DBGL with a newer Java, on macOS, or by hand); read with the code page, "Ä" became "Ã„"</li>
	 * </ul>
	 *
	 * @return null if the file is in the charset of the system (or has no characters outside ASCII)
	 */
	public static java.nio.charset.Charset legacyCharsetOf(byte[] bytes) {
		java.nio.charset.Charset system = java.nio.charset.Charset.defaultCharset();
		boolean utf8 = isValidUtf8(bytes);
		if (system.equals(java.nio.charset.StandardCharsets.UTF_8)) {
			if (utf8)
				return null;
			java.nio.charset.Charset legacy = windowsCodePage();
			return legacy.equals(system) ? null: legacy;
		}
		boolean ascii = true;
		for (byte b: bytes)
			if (b < 0) {
				ascii = false;
				break;
			}
		return (!ascii && utf8) ? java.nio.charset.StandardCharsets.UTF_8: null;
	}

	private static boolean isValidUtf8(byte[] bytes) {
		try {
			java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
					.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes));
			return true;
		} catch (java.nio.charset.CharacterCodingException e) {
			return false;
		}
	}

	/**
	 * The code page Windows uses for file names in programs without Unicode (DOSBox 0.74): on Windows the one of the system
	 * (Windows-1252 in Western Europe, Windows-1250 in Central Europe, ...), elsewhere Windows-1252.
	 */
	public static java.nio.charset.Charset windowsCodePage() {
		if (org.dbgl.util.SystemUtils.IS_WINDOWS) {
			try {
				java.nio.charset.Charset cs = java.nio.charset.Charset.forName(System.getProperty("sun.jnu.encoding"));
				if (!cs.equals(java.nio.charset.StandardCharsets.UTF_8))
					return cs;
			} catch (RuntimeException e) {
				// the usual one
			}
		}
		return java.nio.charset.Charset.forName("windows-1252");
	}

	/** @return true if this (profile) configuration keeps all DOSBox settings, see {@link #ALL_SETTINGS_MARKER} */
	public boolean isAllSettings() {
		return allSettings_;
	}

	public void setAllSettings(boolean allSettings) {
		allSettings_ = allSettings;
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
		allSettings_ = false;

		return loadWithAutoexec(text, dbMounts, cwd);
	}

	/**
	 * Like {@link #loadDataWithAutoexec(ITextService, String, File, List)}, but the settings and the autoexec loaded before
	 * are dropped first. Loading again on top added every mount a second time ("Check profiles", repair missing paths).
	 */
	public String replaceDataWithAutoexec(ITextService text, String data, File file, List<Mount> dbMounts) throws IOException {
		sections_ = new SectionsCollection();
		customSection_ = StringUtils.EMPTY;
		autoexec_ = new Autoexec();
		allSettings_ = false;
		return loadDataWithAutoexec(text, data, file, dbMounts, null);
	}

	public String loadWithAutoexec(ITextService text, List<Mount> dbMounts, File cwd) throws IOException {
		File file = fileLocation_.getCanonicalFile();
		if (file == null || !file.isFile() || !file.canRead())
			throw new IOException(text.get("general.error.openfile", new Object[] {String.valueOf(file)}));

		try (StringReader reader = new StringReader(readText(file))) {
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
		String content = org.apache.commons.io.IOUtils.toString(reader);
		if (content.lines().anyMatch(l -> l.trim().equalsIgnoreCase(ALL_SETTINGS_MARKER)))
			allSettings_ = true;
		warningsLog.append(loadData(text, new StringReader(content), file, AUTOEXEC_SECTION_NAME, autoexecLines, CUSTOM_SECTION, customLines));
		if (!autoexecLines.isEmpty())
			warningsLog.append(autoexec_.load(autoexecLines, dbMounts, cwd));
		if (!customLines.isEmpty())
			customSection_ = StringUtils.join(customLines, SystemUtils.EOLN);

		return warningsLog.toString();
	}

	/**
	 * DOSBox finds a section in any case ("[SDL]", "[Autoexec]"): DBGL too, else the values of "[SDL]" were not shown and the
	 * lines of "[Autoexec]" were gone after saving
	 */
	@Override
	protected String sectionTitle(String written) {
		return written.toLowerCase(java.util.Locale.ROOT);
	}

	public void load(Configuration conf) {
		sections_.load(conf.getSections());
	}

	/** the file whose comments and unreadable lines are kept when saving (see {@link ForeignLines}); null: the file saved to */
	private File keepForeignLinesFrom_;

	public void keepForeignLinesFrom(File file) {
		keepForeignLinesFrom_ = file;
	}

	public void save(boolean prepareOnly, List<Mount> combinedMountingPoints) throws IOException {
		File file = fileLocation_.getCanonicalFile();
		FileUtils.forceMkdirParent(file);
		byte[] bytes = bytesToSave(prepareOnly, combinedMountingPoints);
		// the same as in the file: not written (a read-only file stays fine, and its date stays)
		if (file.isFile() && java.util.Arrays.equals(bytes, java.nio.file.Files.readAllBytes(file.toPath())))
			return;
		java.nio.file.Files.write(file.toPath(), bytes);
	}

	/** the file as save would write it */
	public byte[] bytesToSave(boolean prepareOnly, List<Mount> combinedMountingPoints) throws IOException {
		File file = fileLocation_.getCanonicalFile();
		StringWriter data = new StringWriter();
		try (BufferedWriter writer = new BufferedWriter(data)) {
			writeData(writer, prepareOnly, combinedMountingPoints);
		}
		java.nio.charset.Charset readAs = charset_ != null ? charset_: java.nio.charset.Charset.defaultCharset();
		// comments ("# ...") and lines DBGL cannot read stay where they were (before alpha 46 they were gone after saving)
		File old = keepForeignLinesFrom_ != null ? keepForeignLinesFrom_: file;
		String content = data.toString();
		if (old.isFile())
			content = ForeignLines.keep(new String(java.nio.file.Files.readAllBytes(old.toPath()), readAs), content);
		// a character the old charset does not have (e.g. typed in the editor): in the charset of the system, as before
		java.nio.charset.Charset charset = (charset_ != null) && charset_.newEncoder().canEncode(content) ? charset_: java.nio.charset.Charset.defaultCharset();
		return content.getBytes(charset);
	}

	private void writeData(BufferedWriter writer, boolean prepareOnly, List<Mount> combinedMountingPoints) throws IOException {
		if (allSettings_)
			writer.write(ALL_SETTINGS_MARKER + SystemUtils.EOLN + SystemUtils.EOLN);
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
