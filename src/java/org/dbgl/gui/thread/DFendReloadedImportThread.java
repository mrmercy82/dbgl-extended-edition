/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-01. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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
package org.dbgl.gui.thread;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.dfend.DFendReloadedConfiguration;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.util.FilesUtils;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;


public class DFendReloadedImportThread extends UIThread<File> {

	private final boolean performCleanup_;
	private final DosboxVersion defaultDBVersion_;
	private final File dfendPath_;
	private final DFendReloadedConfiguration profiles_;
	private final boolean archive_;

	public DFendReloadedImportThread(Text log, ProgressBar progressBar, Label status, File dfendPath, File confsPath, boolean performCleanup, DosboxVersion defaultDBVersion) throws IOException {
		super(log, progressBar, status, false);

		performCleanup_ = performCleanup;
		defaultDBVersion_ = defaultDBVersion;

		File settingsFile = new File(new File(dfendPath, "Settings"), "DFend.ini");
		profiles_ = new DFendReloadedConfiguration(settingsFile);
		messageLog_.append(profiles_.loadDat(new File(dfendPath, "Confs"), confsPath));
		setObjects(profiles_.getConfFiles());

		String defLoc = profiles_.getValue("ProgramSets", "defloc");
		dfendPath_ = StringUtils.isNotBlank(defLoc) ? new File(defLoc): dfendPath;
		archive_ = false;
	}

	/** alpha 48: the .prof files of a DFendX game archive, whose paths are relative to the folder they are in */
	public DFendReloadedImportThread(Text log, ProgressBar progressBar, Label status, java.util.List<File> profFiles, boolean performCleanup, DosboxVersion defaultDBVersion) {
		super(log, progressBar, status, false);

		performCleanup_ = performCleanup;
		defaultDBVersion_ = defaultDBVersion;
		dfendPath_ = profFiles.get(0).getAbsoluteFile().getParentFile();
		archive_ = true;
		profiles_ = new DFendReloadedConfiguration(new File(dfendPath_, "-no D-Fend settings-"));
		profiles_.loadArchive(profFiles);
		setObjects(profiles_.getConfFiles());
	}

	@Override
	public String work(File profFile) throws IOException, SQLException {
		StringBuilder warnings = new StringBuilder();

		warnings.append(profiles_.loadProfile(profFile));

		String title = profiles_.getValue(profFile, "ExtraInfo", "name");
		displayTitle(text_.get("dialog.dfendimport.importing", new Object[] {title}));

		String developer = profiles_.getValue(profFile, "ExtraInfo", "developer");
		String publisher = profiles_.getValue(profFile, "ExtraInfo", "publisher");
		String genre = profiles_.getValue(profFile, "ExtraInfo", "genre");
		String year = profiles_.getValue(profFile, "ExtraInfo", "year");
		String status = text_.get("dialog.dfendimport.defaultprofilestatus");
		String notes = fixCrLf(profiles_.getValue(profFile, "ExtraInfo", "notes"));
		boolean favorite = profiles_.getValue(profFile, "ExtraInfo", "favorite").equals("1");
		String[] links = {fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www")), fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www2")),
				fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www3")), fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www4")), fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www5")),
				fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www6")), fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www7")), fixUrl(profiles_.getValue(profFile, "ExtraInfo", "www8"))};
		String[] linkTitles = {profiles_.getValue(profFile, "ExtraInfo", "wwwname"), profiles_.getValue(profFile, "ExtraInfo", "www2name"), profiles_.getValue(profFile, "ExtraInfo", "www3name"),
				profiles_.getValue(profFile, "ExtraInfo", "www4name"), profiles_.getValue(profFile, "ExtraInfo", "www5name"), profiles_.getValue(profFile, "ExtraInfo", "www6name"),
				profiles_.getValue(profFile, "ExtraInfo", "www7name"), profiles_.getValue(profFile, "ExtraInfo", "www8name")};
		String language = profiles_.getValue(profFile, "ExtraInfo", "language");
		String userInfo = profiles_.getValue(profFile, "ExtraInfo", "userinfo");
		if (StringUtils.isNotEmpty(userInfo))
			userInfo = StringUtils.join(StringUtils.split(fixCrLf(userInfo), StringUtils.LF), ", ");
		String[] customStrings = new String[] {language, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, userInfo, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY,
				StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY};
		String setup = profiles_.getValue(profFile, "Extra", "setup");
		if (StringUtils.isNotEmpty(setup))
			setup = org.dbgl.model.conf.dfend.DFendProfConverter.hostPath(dfendPath_, setup).getPath(); // alpha 48: also .\ and \ on Linux and macOS
		String setupParams = profiles_.getValue(profFile, "Extra", "setupparameters");

		Profile newProfile = ProfileFactory.create(title, developer, publisher, genre, year, status, notes, favorite, links, linkTitles, customStrings, setup, setupParams);

		// the .conf exported from D-Fend, or (since alpha 46) one made from the .prof itself
		String confText;
		if (profiles_.hasConf(profFile)) {
			confText = profiles_.getConf(profFile);
			// alpha 48: the .conf in a DFendX archive mounts with relative short names (".\VIRTUA~1\") that only work in its
			// own folder on Windows: its settings are kept, the [autoexec] comes from the .prof (full paths in this folder)
			if (archive_) {
				org.dbgl.model.conf.dfend.DFendProfConverter.Result converted = org.dbgl.model.conf.dfend.DFendProfConverter.convert(profiles_.getProf(profFile), dfendPath_);
				confText = withAutoexecOf(confText, converted.conf());
				warnings.append(text_.get("dialog.dfendimport.dfendx.autoexecfromprof", new Object[] {title})).append(StringUtils.LF);
				if (!converted.notes().isEmpty())
					warnings.append(text_.get("dialog.dfendimport.notconverted", new Object[] {title, StringUtils.join(converted.notes(), ", ")})).append(StringUtils.LF);
			}
		} else {
			org.dbgl.model.conf.dfend.DFendProfConverter.Result converted = org.dbgl.model.conf.dfend.DFendProfConverter.convert(profiles_.getProf(profFile), dfendPath_);
			confText = converted.conf();
			warnings.append(text_.get("dialog.dfendimport.fromprof", new Object[] {title})).append(StringUtils.LF);
			if (!converted.notes().isEmpty())
				warnings.append(text_.get("dialog.dfendimport.notconverted", new Object[] {title, StringUtils.join(converted.notes(), ", ")})).append(StringUtils.LF);
		}
		warnings.append(newProfile.loadConfigurationData(text_, confText, profiles_.getConfFile(profFile)));

		// a configuration made from the .prof has the settings of DOSBox 0.74 (D-Fend Reloaded's DOSBox)
		Generation srcGeneration = profiles_.hasConf(profFile) ? newProfile.determineGenByConf(): Generation.official_073;
		if (srcGeneration == null)
			srcGeneration = Generation.official_073; // most likely

		if (performCleanup_) {
			newProfile.getConfiguration().removeSection("joystick");
			newProfile.getConfiguration().removeSection("sdl");
		}

		// The new profile is currently not associated with any DOSBox version
		// We need to update the profile configuration so it will match the default DOSBox version
		// because the imported configuration might be written using an older DOSBox generation.
		// Since alpha 45: a configuration for DOSBox-X or Staging (e.g. from DFendX) goes to such a version if there is one
		DosboxVersion target = targetFor(srcGeneration);
		java.util.Map<String, String> before = settingsOf(newProfile.getConfiguration());
		newProfile.alterToDosboxVersionGeneration(srcGeneration, target);
		warnings.append(report(title, srcGeneration, target, before, newProfile));

		newProfile = new ProfileRepository().add(newProfile);

		String captures = profiles_.getValue(profFile, "dosbox", "captures");
		File srcCaptures = StringUtils.isBlank(captures) ? null: org.dbgl.model.conf.dfend.DFendProfConverter.hostPath(dfendPath_, captures); // alpha 48: as above

		if (FilesUtils.isExistingDirectory(srcCaptures))
			FileUtils.copyDirectory(srcCaptures, newProfile.getCanonicalCaptures(), FileFileFilter.INSTANCE);

		if (newProfile.isIncomplete())
			warnings.append(text_.get("general.error.profileincomplete", new String[] {newProfile.getConfigurationCanonicalFile().getPath()}));

		return warnings.toString();
	}

	/**
	 * For a configuration of DOSBox-X or Staging: a DOSBox version of that family (the default one if it is). Otherwise, as before,
	 * the default version (a 0.74 configuration of D-Fend goes to it, e.g. Staging)
	 */
	private DosboxVersion targetFor(Generation src) throws SQLException {
		int family = org.dbgl.model.entity.GenerationAwareConfigurable.familyIdx(src);
		Generation defaultGen = org.dbgl.model.entity.GenerationAwareConfigurable.determineGen(defaultDBVersion_);
		if ((family != 4 && family != 5) || (defaultGen == null) || (org.dbgl.model.entity.GenerationAwareConfigurable.familyIdx(defaultGen) == family))
			return defaultDBVersion_;
		DosboxVersion found = null;
		for (DosboxVersion v: new org.dbgl.model.repository.DosboxVersionRepository().listAll()) {
			Generation g = org.dbgl.model.entity.GenerationAwareConfigurable.determineGen(v);
			if ((g != null) && (org.dbgl.model.entity.GenerationAwareConfigurable.familyIdx(g) == family))
				found = v; // the last one: the newest, as the list is sorted
		}
		return found != null ? found: defaultDBVersion_;
	}

	/** the configuration text with its [autoexec] section replaced by the one of the other text (alpha 48) */
	public static String withAutoexecOf(String conf, String other) {
		return withoutAutoexec(conf).stripTrailing() + StringUtils.LF + StringUtils.LF + autoexecOf(other);
	}

	private static String withoutAutoexec(String conf) {
		StringBuilder sb = new StringBuilder();
		boolean inAutoexec = false;
		for (String line: conf.split("\\r?\\n", -1)) {
			String t = line.strip();
			if (t.startsWith("[") && t.endsWith("]"))
				inAutoexec = t.equalsIgnoreCase("[autoexec]");
			if (!inAutoexec)
				sb.append(line).append(StringUtils.LF);
		}
		return sb.toString();
	}

	private static String autoexecOf(String conf) {
		StringBuilder sb = new StringBuilder();
		boolean inAutoexec = false;
		for (String line: conf.split("\\r?\\n", -1)) {
			String t = line.strip();
			if (t.startsWith("[") && t.endsWith("]"))
				inAutoexec = t.equalsIgnoreCase("[autoexec]");
			if (inAutoexec)
				sb.append(line).append(StringUtils.LF);
		}
		return sb.toString();
	}

	/** all settings of a configuration as "section.item" = value */
	private static java.util.Map<String, String> settingsOf(org.dbgl.model.conf.Configuration conf) {
		java.util.Map<String, String> all = new java.util.LinkedHashMap<>();
		for (String section: conf.getSectionNames())
			for (String item: conf.getItemNames(section))
				all.put(section + "." + item, StringUtils.defaultString(conf.getValue(section, item)));
		return all;
	}

	/**
	 * What became of the profile: the kind of configuration found, the DOSBox version used, how many settings and drives, and the
	 * settings that the DOSBox version does not know (lost, not just renamed or the same as its defaults)
	 */
	private String report(String title, Generation src, DosboxVersion target, java.util.Map<String, String> before, Profile profile) {
		java.util.Map<String, String> after = settingsOf(profile.getConfiguration());
		org.dbgl.model.conf.Configuration targetConf = target.getConfiguration();
		java.util.Set<String> newValues = new java.util.HashSet<>();
		after.forEach((k, v) -> {
			if (!before.containsKey(k))
				newValues.add(v);
		});
		// renamed, and then the same as the default of the DOSBox version (so left out of the profile): not lost, e.g. cycles=3000
		// that became Staging's cpu_cycles=3000
		settingsOf(targetConf).forEach((k, v) -> {
			if (!before.containsKey(k))
				newValues.add(v);
		});
		// only what was changed from the defaults counts as lost (when a DOSBox of the configuration's kind is set up to compare with)
		org.dbgl.model.conf.Configuration srcDefaults = null;
		try {
			for (DosboxVersion v: new org.dbgl.model.repository.DosboxVersionRepository().listAll()) {
				Generation g = org.dbgl.model.entity.GenerationAwareConfigurable.determineGen(v);
				if ((g != null) && (org.dbgl.model.entity.GenerationAwareConfigurable.familyIdx(g) == org.dbgl.model.entity.GenerationAwareConfigurable.familyIdx(src))) {
					v.resetAndLoadConfiguration();
					srcDefaults = v.getConfiguration();
				}
			}
		} catch (SQLException | IOException e) {
			// compare without
		}
		org.dbgl.model.conf.Configuration defaults = srcDefaults;
		java.util.List<String> lost = new java.util.ArrayList<>();
		before.forEach((k, v) -> {
			String section = StringUtils.substringBeforeLast(k, "."), item = StringUtils.substringAfterLast(k, ".");
			if (item.equalsIgnoreCase("captures") || item.equalsIgnoreCase("capture_dir"))
				return; // DBGL keeps the captures itself
			if ((defaults != null) && StringUtils.equalsIgnoreCase(StringUtils.trim(defaults.getValue(section, item)), StringUtils.trim(v)))
				return; // a default value, nothing lost
			if (!after.containsKey(k) && !targetConf.hasValue(section, item) && !newValues.contains(v))
				lost.add(item + "=" + v);
		});
		StringBuilder sb = new StringBuilder();
		String kind = switch (org.dbgl.model.entity.GenerationAwareConfigurable.familyIdx(src)) {
			case 4 -> "DOSBox Staging";
			case 5 -> "DOSBox-X";
			default -> "DOSBox";
		};
		sb.append(text_.get("dialog.dfendimport.report", new Object[] {title, kind, target.getTitle(), after.size(), profile.getNettoMountingPoints().size()})).append(StringUtils.LF);
		if (!lost.isEmpty())
			sb.append(text_.get("dialog.dfendimport.lost", new Object[] {title, StringUtils.join(lost, ", ")})).append(StringUtils.LF);
		return sb.toString();
	}

	private static String fixCrLf(String s) {
		return Strings.CS.replace(Strings.CS.replace(s, "[13][10]", StringUtils.LF), "[13]", "").trim();
	}

	private static String fixUrl(String s) {
		return (StringUtils.isNotEmpty(s) && !s.toLowerCase().startsWith("http://") && !s.toLowerCase().startsWith("https://")) ? "http://" + s: s;
	}

	@Override
	public String getTitle(File file) {
		return file.getName();
	}

}