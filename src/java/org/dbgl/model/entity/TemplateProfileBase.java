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
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.Mount;
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

	public Configuration getCombinedConfiguration() {
		ITextService text = TextService.getInstance();
		Configuration conf = new Configuration();
		try {
			StringBuilder warnings = new StringBuilder();
			warnings.append(conf.loadDataWithAutoexec(text, dosboxVersion_.getConfigurationString(), dosboxVersion_.getConfigurationFile(), null));
			warnings.append(conf.loadDataWithAutoexec(text, getConfigurationString(), configuration_.getFileLocation() != null ? getConfigurationFile(): null, null));
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
			configuration_.removeDuplicateValuesIn(dosboxVersion_.getConfiguration());
		}
		return warningsLog.toString();
	}

	public String loadConfiguration(File cwd) throws IOException {
		StringBuilder warningsLog = new StringBuilder();
		if (configuration_.getFileLocation() != null) {
			warningsLog.append(configuration_.loadWithAutoexec(TextService.getInstance(), getDosboxNettoMountingPoints(), cwd));
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
		configuration_.save(false, getNettoMountingPoints());
	}

	public void saveConfiguration(boolean prepareOnly) throws IOException {
		configuration_.save(prepareOnly, getNettoMountingPoints());
	}

	public void setValue(String sectionTitle, String sectionItem, String value) {
		if (configuration_.hasValue(sectionTitle, sectionItem))
			configuration_.removeValue(sectionTitle, sectionItem);

		if (dosboxVersion_.getConfiguration().hasValue(sectionTitle, sectionItem)) {
			String dbValue = dosboxVersion_.getConfiguration().getValue(sectionTitle, sectionItem);
			if (!value.equals(dbValue) && (!value.startsWith("0.") || !value.substring(1).equals(dbValue)))
				configuration_.setValue(sectionTitle, sectionItem, value);
		}
	}
	
	public DaControlConvertor detectPriorityConvertor() {
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
