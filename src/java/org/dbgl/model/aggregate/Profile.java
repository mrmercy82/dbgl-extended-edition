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
package org.dbgl.model.aggregate;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.dbgl.exception.DrivelettersExhaustedException;
import org.dbgl.exception.InvalidMountstringException;
import org.dbgl.model.FileLocation;
import org.dbgl.model.Link;
import org.dbgl.model.NativeCommand;
import org.dbgl.model.ProfileStats;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.model.entity.TemplateProfileBase;
import org.dbgl.model.factory.MountFactory;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.TextService;
import org.dbgl.util.FilesUtils;


public class Profile extends TemplateProfileBase {

	public static final int NR_OF_CUSTOM_STRING_DROPDOWNS = 4;
	public static final int NR_OF_CUSTOM_STRING_FIELDS_1 = 4;
	public static final int NR_OF_CUSTOM_STRING_FIELDS_2 = 4;
	public static final int NR_OF_CUSTOM_STRING_FIELDS = NR_OF_CUSTOM_STRING_FIELDS_1 + NR_OF_CUSTOM_STRING_FIELDS_2;
	public static final int NR_OF_CUSTOM_STRINGS = NR_OF_CUSTOM_STRING_DROPDOWNS + NR_OF_CUSTOM_STRING_FIELDS;
	public static final int NR_OF_CUSTOM_INTS = 2;
	public static final int NR_OF_LINK_TITLES = 8;
	public static final int NR_OF_LINK_DESTINATIONS = NR_OF_LINK_TITLES;
	public static final int NR_OF_LINK_STRINGS = NR_OF_LINK_TITLES + NR_OF_LINK_DESTINATIONS;
	public static final int NR_OF_ALT_EXECUTABLES = 2;

	private String developer_, publisher_, genre_, year_, status_, notes_, setupParams_;
	private Boolean favorite_;
	private String[] customStrings_, altExeParams_;
	private Integer[] customInts_;
	private Link[] links_;
	private FileLocation captures_, setup_;
	private FileLocation[] altExe_;
	private ProfileStats profileStats_;

	public Profile() {
		super();
	}

	public String getDeveloper() {
		return developer_;
	}

	public void setDeveloper(String developer) {
		developer_ = developer;
	}

	public String getPublisher() {
		return publisher_;
	}

	public void setPublisher(String publisher) {
		publisher_ = publisher;
	}

	public String getGenre() {
		return genre_;
	}

	public void setGenre(String genre) {
		genre_ = genre;
	}

	public String getYear() {
		return year_;
	}

	public void setYear(String year) {
		year_ = year;
	}

	public String getStatus() {
		return status_;
	}

	public void setStatus(String status) {
		status_ = status;
	}

	public Boolean isFavorite() {
		return favorite_;
	}

	public String getFavorite() {
		return BooleanUtils.toStringTrueFalse(favorite_);
	}

	public void setFavorite(Boolean favorite) {
		favorite_ = favorite;
	}

	public void setFavorite(String favorite) {
		if (StringUtils.isNotBlank(favorite))
			favorite_ = Boolean.valueOf(favorite);
		else
			favorite_ = null;
	}

	public String getNotes() {
		return notes_;
	}

	public void setNotes(String notes) {
		notes_ = notes;
	}

	public String getSetupParams() {
		return setupParams_;
	}

	public void setSetupParams(String setupParams) {
		setupParams_ = setupParams;
	}

	public String[] getAltExeParams() {
		return altExeParams_;
	}

	public void setAltExeParams(String[] altExeParams) {
		altExeParams_ = altExeParams;
	}

	public Link[] getLinks() {
		return links_;
	}

	public void setLinks(Link[] link) {
		links_ = link;
	}

	public String getLinkDestination(int index) {
		return links_[index].getDestination();
	}

	public void setLinkDestination(int index, String destination) {
		links_[index] = new Link(links_[index].getTitle(), destination);
	}

	public String getLinkTitle(int index) {
		return links_[index].getTitle();
	}

	public void setLinkTitle(int index, String title) {
		links_[index] = new Link(title, links_[index].getDestination());
	}

	public String[] getCustomStrings() {
		return customStrings_;
	}

	public void setCustomStrings(String[] customString) {
		customStrings_ = customString;
	}

	public String getCustomString(int index) {
		return customStrings_[index];
	}

	public void setCustomString(int index, String customString) {
		customStrings_[index] = customString;
	}

	public int[] getCustomInts() {
		return ArrayUtils.toPrimitive(customInts_);
	}

	public void setCustomInts(int[] customInt) {
		customInts_ = ArrayUtils.toObject(customInt);
	}

	public String getCustomInt(int index) {
		return Objects.toString(customInts_[index], null);
	}

	public void setCustomInt(int index, String customInt) {
		customInts_[index] = NumberUtils.createInteger(customInt);
	}

	public void resetAllCustomValues() {
		String[] customStrings = new String[Profile.NR_OF_CUSTOM_STRINGS];
		Arrays.fill(customStrings, StringUtils.EMPTY);
		setCustomStrings(customStrings);
		setCustomInts(new int[] {0, 0});
	}

	public ProfileStats getProfileStats() {
		return profileStats_;
	}

	public void setProfileStats(ProfileStats profileStats) {
		profileStats_ = profileStats;
	}

	public boolean hasSetup() {
		return setup_ != null;
	}

	public String getSetupString() {
		return setup_ != null ? setup_.getFile().getPath(): StringUtils.EMPTY;
	}

	public void setSetupFileLocation(String setup) {
		setup_ = StringUtils.isNotBlank(setup) ? new FileLocation(FilenameUtils.separatorsToSystem(setup), FileLocationService.getInstance().dosrootRelative()): null;
	}

	public String[] getAltExeFilenames() {
		return FilesUtils.listFileNames(altExe_);
	}

	public String[] getAltExeStrings() {
		return FilesUtils.listFilePaths(altExe_);
	}

	public boolean hasAltExe(int i) {
		return altExe_[i] != null;
	}

	public String getAltExeString(int index) {
		return getAltExeStrings()[index];
	}

	public void setAltExeFileLocations(String[] altExe) {
		altExe_ = new FileLocation[altExe.length];
		for (int i = 0; i < altExe.length; i++)
			altExe_[i] = StringUtils.isNotBlank(altExe[i]) ? new FileLocation(FilenameUtils.separatorsToSystem(altExe[i]), FileLocationService.getInstance().dataRelative()): null;
	}

	public void setAltExeFileLocation(int index, String altExe) {
		altExe_[index] = StringUtils.isNotBlank(altExe) ? new FileLocation(FilenameUtils.separatorsToSystem(altExe), FileLocationService.getInstance().dataRelative()): null;
	}

	public String getAltExeParam(int index) {
		return altExeParams_[index];
	}

	public void setAltExeParam(int index, String altExeParams) {
		altExeParams_[index] = altExeParams;
	}

	public File getCapturesFile() {
		return captures_.getFile();
	}

	public String getCapturesString() {
		return captures_ != null ? captures_.getFile().getPath(): StringUtils.EMPTY;
	}

	public File getCanonicalCaptures() {
		return captures_.getCanonicalFile();
	}

	public String getCapturesUrl() {
		return FilesUtils.toUrl(getCanonicalCaptures());
	}

	public void setCapturesFileLocation(String captures) {
		captures_ = StringUtils.isNotBlank(captures) ? new FileLocation(FilenameUtils.separatorsToSystem(captures), FileLocationService.getInstance().dataRelative()): null;
	}

	public void setCapturesById() {
		captures_ = FileLocationService.getInstance().getProfileCapturesFileLocation(getId());
	}

	public String getCapturesStringForConfig() {
		File baseDir = null;
		if (capturesDirRelativeToDosboxDir()) {
			baseDir = new FileLocation(getDosboxVersion().getCwd().getPath(), FileLocationService.getInstance().dataRelative()).getFile(); // cwd when starting DOSBox
		} else {
			baseDir = getConfigurationFile().getParentFile(); // directory containing the profile's .conf
		}
		return baseDir.isAbsolute() ? getCanonicalCaptures().getPath(): FilesUtils.relativize(baseDir, getCapturesFile());
	}

	public void setCapturesInConfig() {
		getConfiguration().setValue(capturesConfSection(), capturesConfSectionItem(), getCapturesStringForConfig());
	}

	public File getCustomMapperFile() {
		if (getConfiguration().hasValue("sdl", "mapperfile")) {
			String customMapperfile = getConfiguration().getValue("sdl", "mapperfile");
			if (StringUtils.isNotBlank(customMapperfile)) {
				File mapperFile = new File(getConfigurationCanonicalFile().getParentFile(), customMapperfile);
				if (FilesUtils.isExistingFile(mapperFile))
					return mapperFile;
			}
		}
		return null;
	}

	public void updateMapperFileInConfigByIdentifiers() {
		String oldMapperFile = getCombinedConfiguration().getValue("sdl", "mapperfile");
		if (StringUtils.isNotBlank(oldMapperFile)) {
			String newMapperFile = String.format(oldMapperFile, getId(), FilesUtils.toSafeFilename(getTitle()));
			getConfiguration().updateValue("sdl", "mapperfile", newMapperFile);
		}
	}

	public void setConfigurationFileLocation(String file) {
		getConfiguration().setFileLocation(StringUtils.isNotBlank(file) ? new FileLocation(FilenameUtils.separatorsToSystem(file), FileLocationService.getInstance().dataRelative()): null);
	}

	@Override
	public void setConfigurationFileLocationByIdentifiers() {
		File gameDir = getCombinedConfiguration().getAutoexec().getCanonicalGameDir();
		getConfiguration().setFileLocation(FileLocationService.getInstance().getUniqueProfileConfigFileLocation(getId(), getTitle(), gameDir));
	}

	@Override
	public void setBooter(boolean booter) {
		if (booter) {
			getConfiguration().getAutoexec().setMain(StringUtils.EMPTY);
			getConfiguration().getAutoexec().setParameters(StringUtils.EMPTY);
		} else {
			for (int i = 0; i < Autoexec.BOOTER_IMAGES; i++) {
				getConfiguration().getAutoexec().setImg(i, StringUtils.EMPTY);
			}
		}
	}

	public void setAutoexecSettings(String main, String parameters) {
		Autoexec autoexec = getConfiguration().getAutoexec();
		autoexec.setMain(main);
		autoexec.setParameters(parameters);
	}

	public void setAutoexecSettings(Boolean pause, Boolean exit) {
		Autoexec autoexec = getConfiguration().getAutoexec();
		autoexec.setPause(pause);
		autoexec.setExit(exit);
	}

	public void setAutoexecSettings(String[] img, String imgDriveletter) {
		Autoexec autoexec = getConfiguration().getAutoexec();
		for (int i = 0; i < Autoexec.BOOTER_IMAGES; i++) {
			autoexec.setImg(i, img[i]);
		}
		autoexec.setImgDriveletter(imgDriveletter);
	}

	public Configuration getConfigurationForSharing() {
		Configuration conf = new Configuration(getConfiguration());
		conf.removeValueIfSet("sdl", "fullscreen");
		conf.removeValueIfSet("sdl", "fulldouble");
		conf.removeValueIfSet("sdl", "fullresolution");
		conf.removeValueIfSet("sdl", "windowresolution");
		conf.removeValueIfSet("sdl", "output");
		conf.removeValueIfSet("sdl", "mapperfile");
		conf.removeValueIfSet("dosbox", "language");
		conf.removeValueIfSet("dosbox", "captures");
		conf.removeValueIfSet("render", "scaler");
		conf.removeValueIfSet("midi", "midiconfig");
		conf.setAutoexec(new Autoexec());
		return conf;
	}

	@Override
	public String resetAndLoadConfiguration() throws IOException {
		StringBuilder warningsLog = new StringBuilder();
		warningsLog.append(super.resetAndLoadConfiguration());
		if (getConfiguration().getFileLocation() != null && isIncomplete())
			warningsLog.append(TextService.getInstance().get("general.error.profileincomplete", new String[] {getConfigurationCanonicalFile().getPath()}));
		return warningsLog.toString();
	}

	public boolean isIncomplete() {
		return getConfiguration().getAutoexec().isIncomplete(getNettoMountingPoints());
	}

	public String getDosFilename(String hostFileLocation) {
		return getConfiguration().getAutoexec().getDosFilename(hostFileLocation, getNettoMountingPoints());
	}

	public String getRequiredMount(boolean booter, String hostFileLocation, boolean pathOnly, boolean forInstaller) {
		if (getConfiguration().getAutoexec().canBeReachedUsingMounts(booter, hostFileLocation, getNettoMountingPoints()))
			return null;

		try {
			Mount mount = null;

			if (forInstaller) {
				FileLocation fileLocation = new FileLocation(hostFileLocation, FileLocationService.getInstance().dosrootRelative());
				File[] overrideFilesToMount = FilesUtils.listFileSequence(FilesUtils.determineMainFile(fileLocation.getCanonicalFile()));
				if (overrideFilesToMount.length > 1)
					mount = MountFactory.create(booter, hostFileLocation, getNettoMountedDrives(), overrideFilesToMount);
			}
			if (mount == null)
				mount = MountFactory.create(booter, hostFileLocation, getNettoMountedDrives());

			return pathOnly ? mount.getPathString(): mount.toString();
		} catch (DrivelettersExhaustedException | InvalidMountstringException e) {
			e.printStackTrace();
			return null;
		}
	}

	public void addRequiredMount(boolean booter, String hostFileLocation, boolean forInstaller) {
		addMount(getRequiredMount(booter, hostFileLocation, false, forInstaller));
	}

	/**
	 * load template
	 */
	public String loadTemplate(DosboxVersion dstDosboxVersion, Template template) throws IOException {
		getConfiguration().load(template.getConfiguration());
		getConfiguration().getAutoexec().setExit(template.getConfiguration().getAutoexec().isExit());
		for (int i = 0; i < Autoexec.SECTIONS; i++) {
			String templCustom = template.getConfiguration().getAutoexec().getCustomSection(i);
			if (StringUtils.isNotBlank(templCustom)) {
				getConfiguration().getAutoexec().setCustomSection(i, templCustom);
			}
		}
		if (StringUtils.isBlank(getConfiguration().getAutoexec().getMain()) && StringUtils.isBlank(getConfiguration().getAutoexec().getImg(0))) {
			getConfiguration().getAutoexec().setBooterByDefault(template.getConfiguration().getAutoexec().isBooter());
		}
		if (getMountingPointsForUI().isEmpty()) {
			getConfiguration().getAutoexec().setMountingpoints(template.getConfiguration().getAutoexec().getMountingpoints());
		}
		if (template.getNativeCommands().size() != 1)
			setNativeCommands(template.getNativeCommands());

		// keep the own changes and the template's settings, everything else comes from the new DOSBox version
		return switchToDosboxVersion(dstDosboxVersion);
	}

	/**
	 * reload DOSBox version and template
	 */
	public String reloadTemplate(DosboxVersion dstDosboxVersion, Template template) throws IOException {
		getConfiguration().setSections(template.getConfiguration().getSections());
		getConfiguration().getAutoexec().setExit(template.getConfiguration().getAutoexec().isExit());
		getConfiguration().getAutoexec().setCustomSections(template.getConfiguration().getAutoexec().getCustomSections());
		if (StringUtils.isBlank(getConfiguration().getAutoexec().getMain()) && StringUtils.isBlank(getConfiguration().getAutoexec().getImg(0))) {
			getConfiguration().getAutoexec().setBooterByDefault(template.getConfiguration().getAutoexec().isBooter());
		}
		if (getMountingPointsForUI().isEmpty()) {
			getConfiguration().getAutoexec().setMountingpoints(template.getConfiguration().getAutoexec().getMountingpoints());
		}
		setNativeCommands(template.getNativeCommands());

		return switchToDosboxVersion(dstDosboxVersion);
	}

	public void setBaseDir(File baseDir) {
		// fix autoexec paths
		getConfiguration().getAutoexec().setBaseDir(baseDir);

		// fix link destinations
		for (Link link: getLinks())
			link.setBaseDir(baseDir);

		// fix setup path
		if (hasSetup())
			setup_ = FilesUtils.concat(baseDir, setup_);

		// fix altexe paths
		for (int i = 0; i < NR_OF_ALT_EXECUTABLES; i++)
			if (hasAltExe(i))
				altExe_[i] = FilesUtils.concat(baseDir, altExe_[i]);
	}

	public void migrate(FileLocation fromPath, FileLocation toPath) {
		// fix autoexec paths
		getConfiguration().getAutoexec().migrate(fromPath, toPath);

		// fix link destinations
		for (Link link: getLinks())
			link.migrate(fromPath, toPath);

		// fix setup path
		if (hasSetup())
			setup_ = FilesUtils.migrate(setup_, fromPath, toPath);

		// fix altexe paths
		for (int i = 0; i < NR_OF_ALT_EXECUTABLES; i++)
			if (hasAltExe(i))
				altExe_[i] = FilesUtils.migrate(altExe_[i], fromPath, toPath);

		// fix configuration file path
		FileLocation conf = getConfiguration().getFileLocation();
		if (conf != null)
			getConfiguration().setFileLocation(FilesUtils.migrate(conf, fromPath, toPath));
	}

	/**
	 * Sets all multi-editable meta-data properties that are unequal with the properties from prof, to null
	 *
	 * @param prof
	 */
	public void removeUnequalValuesIn(Profile prof) {
		if (!Strings.CS.equals(getTitle(), prof.getTitle()))
			setTitle(null);
		if (!Strings.CS.equals(getDeveloper(), prof.getDeveloper()))
			setDeveloper(null);
		if (!Strings.CS.equals(getPublisher(), prof.getPublisher()))
			setPublisher(null);
		if (!Strings.CS.equals(getGenre(), prof.getGenre()))
			setGenre(null);
		if (!Strings.CS.equals(getYear(), prof.getYear()))
			setYear(null);
		if (!Strings.CS.equals(getStatus(), prof.getStatus()))
			setStatus(null);
		if (!Strings.CS.equals(getNotes(), prof.getNotes()))
			setNotes(null);
		if (!Strings.CS.equals(getFavorite(), prof.getFavorite()))
			setFavorite((Boolean)null);
		for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
			if (!Strings.CS.equals(getLinkTitle(i), prof.getLinkTitle(i)))
				setLinkTitle(i, null);
			if (!Strings.CS.equals(getLinkDestination(i), prof.getLinkDestination(i)))
				setLinkDestination(i, null);
		}
		for (int i = 0; i < Profile.NR_OF_CUSTOM_STRINGS; i++) {
			if (!Strings.CS.equals(getCustomString(i), prof.getCustomString(i)))
				setCustomString(i, null);
		}
		for (int i = 0; i < Profile.NR_OF_CUSTOM_INTS; i++) {
			if (!Strings.CS.equals(getCustomInt(i), prof.getCustomInt(i)))
				setCustomInt(i, null);
		}
		List<NativeCommand> natvCtrls = getNativeCommands();
		List<NativeCommand> profNatvCtrls = prof.getNativeCommands();
		if (natvCtrls == null || profNatvCtrls == null || natvCtrls.size() != profNatvCtrls.size()) {
			setNativeCommands(null);
		} else {
			for (int i = 0; i < natvCtrls.size(); i++) {
				if (!Strings.CS.equals(natvCtrls.get(i).toString(), profNatvCtrls.get(i).toString())) {
					setNativeCommands(null);
					break;
				}
			}
		}
	}
}
