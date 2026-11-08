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
package org.dbgl.model.entity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.exception.InvalidMountstringException;
import org.dbgl.gui.abstractdialog.EditConfigurableDialog.MountsValidationResult;
import org.dbgl.gui.controls.SeparatedValuesDaControlConvertor;
import org.dbgl.gui.interfaces.DaControlConvertor;
import org.dbgl.model.GenericStats;
import org.dbgl.model.NativeCommand;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.conf.ConfValueOptions;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.util.FilesUtils;
import org.dbgl.model.conf.mount.OverlayMount;
import org.dbgl.model.factory.MountFactory;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;


public abstract class TemplateProfileBase extends GenerationAwareConfigurable {
	
	private List<NativeCommand> nativeCommands_;
	private GenericStats stats_;

	protected TemplateProfileBase() {
		super();
	}

	public abstract void setConfigurationFileLocationByIdentifiers();

	public abstract void setBooter(boolean booter);
	

	public String getCustomSection() {
		return configuration_.getCustomSection();
	}

	public void setCustomSection(String customSection) {
		configuration_.setCustomSection(customSection);
	}

	public List<NativeCommand> getNativeCommands() {
		return nativeCommands_;
	}

	public void setNativeCommands(List<NativeCommand> nativeCommands) {
		nativeCommands_ = nativeCommands;
	}

	@SuppressWarnings("unchecked")
	public void setNativeCommandsWithObject(Object nativeCommands) {
		nativeCommands_ = (List<NativeCommand>)nativeCommands;
	}

	public void resetNativeCommands() {
		List<NativeCommand> nativeCommands = new ArrayList<>();
		NativeCommand.insertDosboxCommand(nativeCommands);
		setNativeCommands(nativeCommands);
	}

	public GenericStats getStats() {
		return stats_;
	}

	public void setStats(GenericStats stats) {
		stats_ = stats;
	}

	public String getConfigurationString() {
		return configuration_.toString(getNettoMountingPoints());
	}

	private String combinedCacheKey_;
	private Configuration combinedCache_;

	/**
	 * Like {@link #getCombinedConfiguration()}, but parsed again only when the DOSBox configuration or the own configuration
	 * changed. Building a profile dialog asks for it for every field, and parsing a configuration of DOSBox-X (some 3000 lines)
	 * each time made opening the dialog slow. The result must only be read.
	 */
	public Configuration getCombinedConfigurationForReading() {
		String dosboxString = dosboxVersion_.getConfigurationStringCached();
		String ownString = getConfigurationString();
		String key = dosboxString.length() + ":" + dosboxString.hashCode() + ":" + System.identityHashCode(dosboxVersion_) + ":" + ownString + ":"
				+ configuration_.getAutoexec().getBooterByDefault() + ":" + configuration_.getAutoexec().getGameMain();
		if ((combinedCache_ == null) || !key.equals(combinedCacheKey_)) {
			combinedCache_ = combine(dosboxString, ownString);
			combinedCacheKey_ = key;
		}
		return combinedCache_;
	}

	public Configuration getCombinedConfiguration() {
		return combine(dosboxVersion_.getConfigurationString(), getConfigurationString());
	}

	private Configuration combine(String dosboxString, String ownString) {
		ITextService text = TextService.getInstance();
		Configuration conf = new Configuration();
		try {
			StringBuilder warnings = new StringBuilder();
			warnings.append(conf.loadDataWithAutoexec(text, dosboxString, dosboxVersion_.getConfigurationFile(), null));
			warnings.append(conf.loadDataWithAutoexec(text, ownString, configuration_.getFileLocation() != null ? getConfigurationFile(): null, null));
			conf.getAutoexec().setBooterByDefault(configuration_.getAutoexec().getBooterByDefault());
			conf.getAutoexec().setGameMain(configuration_.getAutoexec().getGameMain());
			if (StringUtils.isNotEmpty(warnings)) {
				System.err.println(warnings);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return conf;
	}

	public String resetAndLoadConfiguration() throws IOException {
		return resetAndLoadConfiguration(null);
	}

	public String resetAndLoadConfiguration(File cwd) throws IOException {
		StringBuilder warningsLog = new StringBuilder();
		warningsLog.append(dosboxVersion_.resetAndLoadConfiguration());
		if (configuration_.getFileLocation() != null) {
			warningsLog.append(configuration_.reloadWithAutoexec(TextService.getInstance(), getDosboxNettoMountingPoints(), cwd));
			org.dbgl.util.UnknownAutoexecLines.keepOnRead(configuration_.getAutoexec()); // lines DBGL does not know are not lost
			if (!keepsAllSettings())
				configuration_.removeDuplicateValuesIn(dosboxVersion_.getConfiguration());
		}
		return warningsLog.toString();
	}

	public String loadConfiguration(File cwd) throws IOException {
		StringBuilder warningsLog = new StringBuilder();
		if (configuration_.getFileLocation() != null) {
			warningsLog.append(configuration_.loadWithAutoexec(TextService.getInstance(), getDosboxNettoMountingPoints(), cwd));
			if (!keepsAllSettings())
				configuration_.removeDuplicateValuesIn(dosboxVersion_.getConfiguration());
		}
		return warningsLog.toString();
	}

	public String loadConfigurationData(ITextService text, String data, File file) throws IOException {
		return loadConfigurationData(text, data, file, null);
	}

	public String loadConfigurationData(ITextService text, String data, File file, File cwd) throws IOException {
		return configuration_.loadDataWithAutoexec(text, data, file, getDosboxNettoMountingPoints(), cwd);
	}

	public void saveConfiguration() throws IOException {
		saveConfiguration(false);
	}

	public void saveConfiguration(boolean prepareOnly) throws IOException {
		removeEmptyValuesOverridingDosboxDefaults();
		adaptImageMountsToDosbox();
		if (keepsAllSettings())
			completeWithDosboxSettings();
		configuration_.save(prepareOnly, getNettoMountingPoints());
	}

	/**
	 * "mount D game.cue -t cdrom" for an image works in DOSBox Staging 0.83 and newer only (IMGMOUNT is an alias there).
	 * DOSBox-X, older Staging versions and DOSBox 0.74 need "imgmount"; DOSBox-X even quits with "PHYSFS couldn't mount
	 * ... unsupported". So for those, such lines are written as imgmount (e.g. after switching a profile from Staging 0.83
	 * to DOSBox-X), and for Staging 0.83 and newer imgmount lines as mount (IMGMOUNT is only an old name there).
	 * 
	 * @return true if a line was changed
	 */
	public boolean adaptImageMountsToDosbox() {
		if (dosboxVersion_ == null)
			return false;
		// DOSBox Staging 0.83 and newer: MOUNT, IMGMOUNT is only an old name there; all others: IMGMOUNT
		String wanted = mountTakesImages(dosboxVersion_) ? "mount": "imgmount";
		boolean changed = false;
		for (org.dbgl.model.conf.mount.Mount m: configuration_.getAutoexec().getMountingpoints()) {
			if ((m instanceof org.dbgl.model.conf.mount.ImageMount im) && !wanted.equalsIgnoreCase(im.getCommand())) {
				// all image kinds, also hard disk images for booting (drive 2/3, -size, -t hdd, -fs none): tested with Staging 0.83
				im.setCommand(wanted);
				changed = true;
			}
		}
		return changed;
	}

	/** DOSBox Staging 0.83 and newer mount images with MOUNT as well */
	public static boolean mountTakesImages(DosboxVersion v) {
		String family = StringUtils.defaultString(v.getFamily()).toLowerCase();
		return family.contains("staging") && (org.dbgl.util.DosboxUpdateChecker.compareVersions(StringUtils.defaultString(v.getVersion()), "0.83") >= 0);
	}

	/** a profile that keeps all DOSBox settings (its own choice, see {@link org.dbgl.util.FullConfProfiles}) */
	public boolean keepsAllSettings() {
		return (this instanceof org.dbgl.model.aggregate.Profile) && configuration_.isAllSettings();
	}

	/**
	 * Switch keeping all DOSBox settings for this profile: on fills in all settings, off takes out again what equals the DOSBox
	 * configuration.
	 */
	public void setKeepsAllSettings(boolean keep) {
		configuration_.setAllSettings(keep);
		if (keep)
			completeWithDosboxSettings();
		else
			reduceToDifferences();
	}

	/** @return how many settings of the DOSBox configuration were added because the profile did not have them yet */
	public int completeWithDosboxSettings() {
		if (dosboxVersion_ == null)
			return 0;
		Configuration dosboxConf = dosboxVersion_.getConfiguration();
		int added = 0;
		for (String section: dosboxConf.getSectionNames()) {
			for (String item: dosboxConf.getItemNames(section)) {
				if (org.dbgl.util.FullConfProfiles.skipped(section, item) || configuration_.hasValue(section, item))
					continue;
				configuration_.setValue(section, item, dosboxConf.getValue(section, item));
				added++;
			}
		}
		return added;
	}

	/** @return how many settings were taken out of the profile because they are the same as in the DOSBox configuration */
	public int reduceToDifferences() {
		if (dosboxVersion_ == null)
			return 0;
		int before = org.dbgl.util.FullConfProfiles.count(configuration_);
		configuration_.removeDuplicateValuesIn(dosboxVersion_.getConfiguration());
		return before - org.dbgl.util.FullConfProfiles.count(configuration_);
	}

	/**
	 * An empty value in a profile while the DOSBox configuration has a value for the same setting is invalid, so leave it out
	 * and let the DOSBox default apply. Such entries could be left behind by profiles edited with a mismatching DOSBox
	 * version. DOSBox Staging rejects every such empty value and warns. For the other DOSBox versions an empty value can be
	 * meaningful (e.g. a path), so it is only left out if DOSBox documents a fixed list of values for the setting.
	 */
	private void removeEmptyValuesOverridingDosboxDefaults() {
		if ((dosboxVersion_ == null) || (gen_ == null))
			return;
		boolean staging = familyIdx(gen_) == familyIdx(Generation.staging_0830);
		ConfValueOptions documented = staging ? null: dosboxVersion_.getConfValueOptions();
		if (!staging && ((documented == null) || documented.isEmpty()))
			return;
		Configuration dosboxConf = dosboxVersion_.getConfiguration();
		for (String section: configuration_.getSectionNames()) {
			for (String item: configuration_.getItemNames(section)) {
				if (StringUtils.isBlank(configuration_.getValue(section, item)) && StringUtils.isNotBlank(dosboxConf.getValue(section, item))) {
					ConfValueOptions.Options options = staging ? null: documented.get(section, item);
					if (staging || ((options != null) && !options.isFreeForm()))
						configuration_.removeValue(section, item);
				}
			}
		}
	}

	/**
	 * @return the folders and files this profile needs that do not exist: mounted folders and images, the main program or the
	 *         image holding it, booter images. Physical floppy and CD drives are skipped, they may just be empty.
	 */
	public List<String> listMissingPaths() {
		List<String> result = new ArrayList<>();
		for (Mount mount: getNettoMountingPoints()) {
			if (mount.isUnmounted())
				continue;
			if (mount instanceof org.dbgl.model.conf.mount.ImageMount im) {
				for (File f: im.getCanonicalImgPaths())
					if (!f.exists() && !result.contains(f.getPath()))
						result.add(f.getPath());
			} else if (mount instanceof DirMount dm) {
				String type = StringUtils.defaultString(mount.getMountAs()).toLowerCase();
				File f = dm.getCanonicalPath();
				if (!type.equals("floppy") && !type.equals("cdrom") && !f.exists() && !result.contains(f.getPath()))
					result.add(f.getPath());
			}
		}
		Autoexec autoexec = configuration_.getAutoexec();
		List<String> files = new ArrayList<>();
		if (StringUtils.isNotBlank(autoexec.getMain()))
			files.add(autoexec.getMain());
		for (int i = 0; i < Autoexec.BOOTER_IMAGES; i++)
			if (StringUtils.isNotBlank(autoexec.getImg(i)) && !autoexec.getImg(i).equals("file"))
				files.add(autoexec.getImg(i));
		for (String file: files) {
			File f = FilesUtils.determineMainFile(new org.dbgl.model.FileLocation(file, org.dbgl.service.FileLocationService.getInstance().dosrootRelative()).getCanonicalFile());
			if (!FilesUtils.existsAsDosProgram(f) && !result.contains(f.getPath()))
				result.add(f.getPath());
		}
		return result;
	}

	private static final java.util.Set<String> SWITCH_VALUES = java.util.Set.of("on", "off", "true", "false", "yes", "no", "1", "0", "enabled", "disabled");

	/**
	 * @return the values in this profile that the DOSBox version does not allow, as {section, item, value, allowed values}.
	 *         Only settings for which DOSBox documents a fixed list of values are checked.
	 */
	public List<String[]> listInvalidValues() {
		List<String[]> result = new ArrayList<>();
		if (dosboxVersion_ == null)
			return result;
		org.dbgl.model.conf.ConfValueOptions documented = dosboxVersion_.getConfValueOptions();
		for (String section: configuration_.getSectionNames()) {
			for (String item: configuration_.getItemNames(section)) {
				org.dbgl.model.conf.ConfValueOptions.Options options = documented.get(section, item);
				String value = StringUtils.defaultString(configuration_.getValue(section, item)).trim().toLowerCase();
				if ((options == null) || options.isFreeForm() || value.isEmpty())
					continue;
				List<String> allowed = options.getValues();
				if (isShaderItem(item)) {
					// DOSBox lists only its built-in shaders; others are files in its shader folders: those must be there
					if (!allowed.contains(value) && !isKnownOrFileShader(configuration_.getValue(section, item).trim())) // as written: paths
						result.add(new String[] {section, item, configuration_.getValue(section, item), String.join(", ", allowed), "shader"});
					continue;
				}
				boolean switchSetting = SWITCH_VALUES.containsAll(allowed);
				boolean valid = java.util.Arrays.stream(value.split("[,\\s]+")).allMatch(v -> allowed.contains(v) || (switchSetting && SWITCH_VALUES.contains(v)));
				if (!valid)
					result.add(new String[] {section, item, configuration_.getValue(section, item), String.join(", ", allowed)});
			}
		}
		return result;
	}

	static boolean isShaderItem(String item) {
		return item.equalsIgnoreCase("shader") || item.equalsIgnoreCase("glshader");
	}

	/**
	 * A shader DOSBox finds in its shader folders (e.g. "scaler/advinterp2x", "crt/crt-hyllian:vga-4k"), or a shader file that
	 * exists: valid, even though the documentation of the setting only lists the built-in ones. When DBGL cannot look into the
	 * shader folders of this DOSBox version (none found, or a kind of DOSBox it does not know them of), any shader is taken
	 * as valid, so that no warning is given that may be wrong.
	 */
	boolean isKnownOrFileShader(String value) {
		String v = value.trim();
		try {
			for (String s: glshaderValues())
				if (s.equalsIgnoreCase(v))
					return true;
		} catch (RuntimeException e) {
			// no list of shaders at hand
		}
		boolean file = v.matches("(?i).*\\.(glsl|slang|fx)$") || new File(v).isAbsolute();
		if (file) {
			File f = new File(v);
			java.util.List<File> candidates = new ArrayList<>();
			candidates.add(f);
			if (!f.isAbsolute() && (dosboxVersion_ != null)) {
				File exeDir = dosboxVersion_.getCanonicalExecutable().getParentFile();
				if (exeDir != null)
					candidates.add(new File(exeDir, v));
				if (dosboxVersion_.getCwd() != null)
					candidates.add(new File(dosboxVersion_.getCwd(), v));
			}
			if (candidates.stream().anyMatch(File::isFile))
				return true;
			String name = v.replaceAll("(?i)\\.(glsl|slang|fx)$", "").replace('\\', '/');
			java.util.List<String> found = installedShaders();
			return found.stream().anyMatch(s -> s.equalsIgnoreCase(name) || name.toLowerCase().endsWith("/" + s.toLowerCase()));
		}
		return installedShaders().isEmpty(); // a name: only known missing if DBGL found the shader folders
	}

	/** the shaders DBGL finds in the shader folders of this DOSBox version (empty if none or not known) */
	java.util.List<String> installedShaders() {
		if ((dosboxVersion_ == null) || (gen_ == null))
			return java.util.List.of();
		try {
			if (familyIdx(gen_) == familyIdx(Generation.x_20241001))
				return org.dbgl.util.DosboxVersionDetector.listDosboxXShadersCached(dosboxVersion_.getCanonicalExecutable(), dosboxVersion_.getCwd());
			if (familyIdx(gen_) == familyIdx(Generation.staging_0830))
				return org.dbgl.util.DosboxVersionDetector.listShadersCached(dosboxVersion_.getCanonicalExecutable(), dosboxVersion_.getCwd(), gen_ == Generation.staging_0830);
		} catch (RuntimeException e) {
			// not known
		}
		return java.util.List.of();
	}

	/** for DOSBox Staging, also the shaders the executable finds in its resource folders (--list-shaders) */
	@Override
	public String[] glshaderValues() {
		String[] values = super.glshaderValues();
		if ((dosboxVersion_ != null) && (gen_ != null) && (familyIdx(gen_) == familyIdx(Generation.x_20241001))) {
			java.util.Set<String> result = new java.util.LinkedHashSet<>(java.util.Arrays.asList(values));
			result.addAll(org.dbgl.util.DosboxVersionDetector.listDosboxXShadersCached(dosboxVersion_.getCanonicalExecutable(), dosboxVersion_.getCwd()));
			return result.toArray(new String[0]);
		}
		if ((dosboxVersion_ == null) || (gen_ == null) || (familyIdx(gen_) != familyIdx(Generation.staging_0830)))
			return values;
		java.util.Set<String> result = new java.util.LinkedHashSet<>(java.util.Arrays.asList(values));
		result.addAll(org.dbgl.util.DosboxVersionDetector.listShadersCached(dosboxVersion_.getCanonicalExecutable(), dosboxVersion_.getCwd(), gen_ == Generation.staging_0830));
		return result.toArray(new String[0]);
	}

	/** @return true if a PC-98 hard disk image (.HDI) is mounted, booted or holds the main program */
	public boolean usesPc98HdImage() {
		Autoexec autoexec = configuration_.getAutoexec();
		for (Mount mount: getNettoMountingPoints())
			if ((mount instanceof org.dbgl.model.conf.mount.ImageMount im) && java.util.stream.Stream.of(im.getImgPathStrings()).anyMatch(FilesUtils::isPc98Image))
				return true;
		for (int i = 0; i < Autoexec.BOOTER_IMAGES; i++)
			if (StringUtils.isNotBlank(autoexec.getImg(i)) && FilesUtils.isPc98Image(autoexec.getImg(i)))
				return true;
		String main = autoexec.getMain();
		return StringUtils.isNotBlank(main) && (FilesUtils.fatImageIndex(main) != -1) && FilesUtils.isPc98Image(main.substring(0, FilesUtils.fatImageIndex(main)));
	}

	/**
	 * DOSBox-X can only use PC-98 hard disk images (.HDI) in PC-98 mode; in IBM PC mode it boots them and crashes. Set
	 * machine=pc98 if such an image is used and DOSBox-X does not run as a PC-98 yet.
	 *
	 * @return true if the machine was changed
	 */
	/**
	 * a PC-98 image (.hdi, .nhd, .fdi, .d88) with a DOSBox that is not DOSBox-X: only DOSBox-X reads these formats (DOSBox
	 * Staging, for example, asks for the geometry of the .hdi and then cannot boot it)
	 */
	public boolean needsDosboxXForPc98() {
		return (dosboxVersion_ != null) && !StringUtils.defaultString(dosboxVersion_.getFamily()).toLowerCase().contains("dosbox-x") && usesPc98HdImage();
	}

	/**
	 * Why this profile should rather use DOSBox-X ("ide": a CD on the IDE bus, "pc98": a PC-98 image, "boot": boots a hard
	 * disk image), or null: also null if it uses DOSBox-X already
	 */
	public String dosboxXReason() {
		if ((dosboxVersion_ == null) || StringUtils.defaultString(dosboxVersion_.getFamily()).toLowerCase().contains("dosbox-x"))
			return null;
		if (usesPc98HdImage())
			return "pc98";
		return org.dbgl.util.StoreGameImport.dosboxXReason(getConfigurationString());
	}

	public boolean ensurePc98MachineForHdi() {
		if ((dosboxVersion_ == null) || (gen_ == null) || (familyIdx(gen_) != familyIdx(Generation.x_20241001)) || !usesPc98HdImage())
			return false;
		String machine = StringUtils.defaultString(getCombinedConfiguration().getValue("dosbox", "machine")).toLowerCase();
		if (machine.startsWith("pc98") || machine.startsWith("pc9801") || machine.startsWith("pc9821"))
			return false;
		setValue("dosbox", "machine", "pc98");
		return true;
	}

	public void setValue(String sectionTitle, String sectionItem, String value) {
		if (configuration_.hasValue(sectionTitle, sectionItem))
			configuration_.removeValue(sectionTitle, sectionItem);

		if (dosboxVersion_.getConfiguration().hasValue(sectionTitle, sectionItem)) {
			String dbValue = dosboxVersion_.getConfiguration().getValue(sectionTitle, sectionItem);
			if (keepsAllSettings() || (!value.equals(dbValue) && (!value.startsWith("0.") || !value.substring(1).equals(dbValue))))
				configuration_.setValue(sectionTitle, sectionItem, value);
		}
	}
	
	public DaControlConvertor detectPriorityConvertor() {
		if (!getCombinedConfiguration().hasValue("sdl", "priority"))
			return null; // e.g. DOSBox Staging 0.83+, which has no priority setting anymore
		return SeparatedValuesDaControlConvertor.detect(getCombinedConfiguration().getValue("sdl", "priority"));
	}

	/**
	 * set to another DOSBox version but try to maintain as many settings as possible
	 */
	public String setToDosboxVersion(DosboxVersion dstDosboxVersion) throws IOException {
		if (dosboxVersion_.getId() == dstDosboxVersion.getId())
			return StringUtils.EMPTY;

		configuration_.setSections(getCombinedConfiguration().getSections());
		return alterToDosboxVersionGeneration(gen_, dstDosboxVersion);
	}

	/**
	 * set to another DOSBox version while maintaining only the changes from the defaults
	 */
	public String switchToDosboxVersion(DosboxVersion dstDosboxVersion) throws IOException {
		if (dosboxVersion_.getId() == dstDosboxVersion.getId())
			return StringUtils.EMPTY;

		return alterToDosboxVersionGeneration(gen_, dstDosboxVersion);
	}

	/**
	 * reloads all DOSBox settings, but keeps exit and unique mounts and booter/executable data
	 */
	public String reloadDosboxVersion(DosboxVersion newDosboxVersion) throws IOException {
		String warningsLog = newDosboxVersion.resetAndLoadConfiguration();
		setDosboxVersion(newDosboxVersion);
		configuration_.clearSections();
		configuration_.removeUnnecessaryMounts(dosboxVersion_.getConfiguration());
		return warningsLog;
	}

	public String alterToDosboxVersionGeneration(Generation src, DosboxVersion dstDosboxVersion) throws IOException {
		String warningsLog = dstDosboxVersion.resetAndLoadConfiguration();
		Generation dst = determineGen(dstDosboxVersion);

		if (src != dst) {
			int srcFam = familyIdx(src);
			int srcRev = revisionIdx(src);
			int dstFam = familyIdx(dst);
			int dstRev = revisionIdx(dst);
			int srcRevs = GENS[srcFam].length;
			int dstRevs = GENS[dstFam].length;

			for (int i = 0; i < srcRevs - srcRev - 1; i++) {
				if (srcFam == 0)
					upgradeOneOfficialGeneration(GENS[srcFam][srcRev + i]);
				else if (srcFam == 4)
					upgradeOneStagingGeneration(GENS[srcFam][srcRev + i]);
				else if (srcFam == 5)
					upgradeOneXGeneration(GENS[srcFam][srcRev + i]);
			}

			if (srcFam != dstFam)
				switchFamily(GENS[srcFam][srcRevs - 1], GENS[dstFam][dstRevs - 1]);

			for (int i = 0; i < dstRevs - dstRev - 1; i++) {
				if (dstFam == 0)
					downgradeOneOfficialGeneration(GENS[dstFam][dstRevs - 1 - i]);
				else if (dstFam == 4)
					downgradeOneStagingGeneration(GENS[dstFam][dstRevs - 1 - i]);
				else if (dstFam == 5)
					downgradeOneXGeneration(GENS[dstFam][dstRevs - 1 - i]);
			}
		}
		
		configuration_.removeValuesNotSetIn(dstDosboxVersion.getConfiguration());
		alignBooleanNotation(configuration_, dstDosboxVersion.getConfiguration());
		if (!keepsAllSettings())
			configuration_.removeDuplicateValuesIn(dstDosboxVersion.getConfiguration());
		setDosboxVersion(dstDosboxVersion);
		return warningsLog;
	}

	private List<Mount> getDosboxNettoMountingPoints() {
		List<Mount> result = new ArrayList<>();
		if (dosboxVersion_ != null) {
			for (Mount mount: dosboxVersion_.getConfiguration().getAutoexec().getMountingpoints()) {
				if (result.stream().anyMatch(x -> (x.getDrive() == mount.getDrive()) && !(mount instanceof OverlayMount))) {
					if (mount.isUnmounted())
						result.removeIf(x -> x.getDrive() == mount.getDrive());
				} else {
					if (!mount.isUnmounted())
						result.add(mount);
				}
			}
		}
		return result;
	}

	public List<Mount> getNettoMountingPoints() {
		List<Mount> result = new ArrayList<>(getDosboxNettoMountingPoints());
		for (Mount mount: configuration_.getAutoexec().getMountingpoints()) {
			if (result.stream().anyMatch(x -> (x.getDrive() == mount.getDrive()) && !(mount instanceof OverlayMount))) {
				if (mount.isUnmounted())
					result.removeIf(x -> x.getDrive() == mount.getDrive());
			} else {
				if (!mount.isUnmounted())
					result.add(mount);
			}
		}
		return result;
	}

	public List<Mount> getMountingPointsForUI() {
		List<Mount> result = new ArrayList<>();
		try {
			for (Mount mount: getDosboxNettoMountingPoints())
				result.add(MountFactory.createCopy(mount));
			for (Mount mount: configuration_.getAutoexec().getMountingpoints()) {
				Mount m = result.stream().filter(x -> x.getDrive() == mount.getDrive() && !x.isUnmounted()).findFirst().orElse(null);
				if (m != null && mount.isUnmounted())
					m.setUnmounted(true);
				else
					result.add(MountFactory.createCopy(mount));
			}
		} catch (InvalidMountstringException e) {
			e.printStackTrace();
		}
		return result;
	}

	public Set<Character> getNettoMountedDrives() {
		return getNettoMountingPoints().stream().map(Mount::getDrive).collect(Collectors.toSet());
	}

	public void unmountDosboxMounts() {
		for (char drive: getNettoMountedDrives())
			addMount(MountFactory.createUnmount(drive).toString());
	}

	/**
	 * Set the mountingpoints so that it reaches the same nettoMounts with the minimum required mounts. In other words, unnecessary mounts are removed. The order of mounts is likely to be changed,
	 * unless dosboxAutoexec has no mounts, then the original order will be kept.
	 */
	public void removeUnnecessaryMounts() {
		List<Mount> dbList = getDosboxNettoMountingPoints();
		List<Mount> fullList = getNettoMountingPoints();
		List<Mount> result = new ArrayList<>();

		// determine mounts from dosbox that have been removed - unmounts, or have remained the same, or have changed
		for (Mount dbMount: dbList) {
			Optional<Mount> finalMount = fullList.stream().filter(x -> x.getDrive() == dbMount.getDrive()).findFirst();
			if (!finalMount.isPresent()) {
				result.add(MountFactory.createUnmount(dbMount));
			} else {
				if (!finalMount.get().toString().equals(dbMount.toString())) {
					result.add(MountFactory.createUnmount(dbMount));
					try {
						result.add(MountFactory.createCopy(finalMount.get()));
					} catch (InvalidMountstringException e) {
						e.printStackTrace();
					}
				}
			}
		}
		// determine added mounts
		for (Mount mnt: fullList) {
			if (dbList.stream().noneMatch(x -> x.getDrive() == mnt.getDrive()))
				result.add(mnt);
		}

		getConfiguration().getAutoexec().setMountingpoints(result);
	}

	public void editMountBasedOnIndexUI(int index, String mount) {
		List<Mount> uiMounts = getMountingPointsForUI();
		int dosboxMounts = dosboxVersion_.getConfiguration().getAutoexec().getMountingpoints().size();
		int unmountedDosboxMountsBeforeIndex = (int)IntStream.range(0, Math.min(index, dosboxMounts)).filter(x -> uiMounts.get(x).isUnmounted()).count();

		if (index < dosboxMounts) {
			Mount mnt = uiMounts.get(index);
			if (!mnt.isUnmounted()) {
				removeMountBasedOnIndexUI(index);
				addMount(mount);
			}
		} else {
			try {
				configuration_.getAutoexec().getMountingpoints().set(index - (dosboxMounts - unmountedDosboxMountsBeforeIndex), MountFactory.create(mount));
			} catch (InvalidMountstringException e) {
				// nothing we can do
			}
		}
	}

	public void removeMountBasedOnIndexUI(int index) {
		List<Mount> uiMounts = getMountingPointsForUI();
		int dosboxMounts = dosboxVersion_.getConfiguration().getAutoexec().getMountingpoints().size();
		int unmountedDosboxMountsBeforeIndex = (int)IntStream.range(0, Math.min(index, dosboxMounts)).filter(x -> uiMounts.get(x).isUnmounted()).count();

		if (index < dosboxMounts) {
			Mount mnt = uiMounts.get(index);
			if (mnt.isUnmounted()) {
				if (!getNettoMountedDrives().contains(mnt.getDrive()))
					configuration_.getAutoexec().getMountingpoints().remove(unmountedDosboxMountsBeforeIndex);
			} else {
				configuration_.getAutoexec().getMountingpoints().add(unmountedDosboxMountsBeforeIndex, MountFactory.createUnmount(mnt));
			}
		} else {
			configuration_.getAutoexec().getMountingpoints().remove(index - (dosboxMounts - unmountedDosboxMountsBeforeIndex));
		}
	}

	public void removeFloppyMounts() {
		for (Iterator<Mount> it = configuration_.getAutoexec().getMountingpoints().iterator(); it.hasNext();)
			if (it.next().getMountAs().equals("floppy"))
				it.remove();
	}

	public String[] getMountStringsForUI() {
		return getMountingPointsForUI().stream().map(x -> x.toString(true)).toArray(String[]::new);
	}

	public MountsValidationResult checkMounts() {
		List<Mount> mountingpoints = getCombinedConfiguration().getAutoexec().getMountingpoints();
		for (int i = 0; i < mountingpoints.size(); i++) {
			Mount mount = mountingpoints.get(i);

			if (mount instanceof OverlayMount) {
				int idx = IntStream.range(0, i).filter(
					x -> (!mountingpoints.get(x).isUnmounted()) && (mountingpoints.get(x).getDrive() == mount.getDrive()) && (mountingpoints.get(x) instanceof DirMount)).findFirst().orElse(-1);
				if (idx == -1)
					return MountsValidationResult.INVALID_OVERLAY_NO_BASEDRIVE;

				DirMount baseMount = (DirMount)mountingpoints.get(idx);
				OverlayMount overlayMount = (OverlayMount)mount;
				boolean absolute1 = baseMount.getPath().isAbsolute();
				boolean absolute2 = overlayMount.getPath().isAbsolute();
				if (absolute1 != absolute2)
					return MountsValidationResult.INVALID_OVERLAY_MIXED_ABSREL_PATHS;

				if (overlayMount.getPath().equals(baseMount.getPath()))
					return MountsValidationResult.INVALID_OVERLAY_PATHS_EQUAL;
			}
		}

		return MountsValidationResult.OK;
	}
}
