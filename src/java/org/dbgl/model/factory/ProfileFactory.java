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
package org.dbgl.model.factory;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.FileLocation;
import org.dbgl.model.GenericStats;
import org.dbgl.model.Link;
import org.dbgl.model.NativeCommand;
import org.dbgl.model.ProfileStats;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.service.TextService;


public class ProfileFactory {

	private ProfileFactory() {
	}

	/**
	 * Used when creating a new Profile
	 */
	public static Profile create(DosboxVersion dosboxVersion, Template template) {
		String[] customStrings = new String[Profile.NR_OF_CUSTOM_STRINGS];
		Arrays.fill(customStrings, StringUtils.EMPTY);
		Link[] links = new Link[Profile.NR_OF_LINK_TITLES];
		Arrays.fill(links, new Link(StringUtils.EMPTY, StringUtils.EMPTY));
		List<NativeCommand> nativeCommands = new ArrayList<>();
		NativeCommand.insertDosboxCommand(nativeCommands);

		Profile profile = create(StringUtils.EMPTY, false, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, customStrings,
			new int[] {0, 0}, links, null, null, StringUtils.EMPTY, new String[] {StringUtils.EMPTY, StringUtils.EMPTY}, new String[] {StringUtils.EMPTY, StringUtils.EMPTY}, nativeCommands,
			dosboxVersion);

		Configuration config = profile.getConfiguration();

		if (template != null) {
			config.setSections(template.getConfiguration().getSections());
			config.getAutoexec().setExit(template.getConfiguration().getAutoexec().isExit());
			config.getAutoexec().setCustomSections(template.getConfiguration().getAutoexec().getCustomSections());
			config.getAutoexec().setBooterByDefault(template.getConfiguration().getAutoexec().isBooter());
			config.getAutoexec().setMountingpoints(template.getConfiguration().getAutoexec().getMountingpoints());
			profile.setNativeCommands(template.getNativeCommands());
		} else {
			config.getAutoexec().setExit(true);
		}

		return profile;
	}

	/**
	 * Used when instantiating a Profile from data in the database
	 */
	public static Profile create(int id, String title, boolean favorite, String developer, String publisher, String genre, String year, String status, String notes, String[] customStrings,
			int[] customInts, String[] links, String captures, String setup, String setupParams, String altExe1, String altExe1Params, String altExe2, String altExe2Params,
			List<NativeCommand> nativeCommands, int dosboxVersionId, String configuration, Timestamp created, Timestamp modified, Timestamp lastRun, Timestamp lastSetup, int runs, int setups,
			int playTime, List<DosboxVersion> dosboxVersions) {
		Link[] link = new Link[Profile.NR_OF_LINK_TITLES];
		for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
			link[i] = new Link(links[i * 2], links[i * 2 + 1]);
		}

		Profile profile = create(title, favorite, developer, publisher, genre, year, status, notes, customStrings, customInts, link, captures, setup, setupParams, new String[] {altExe1, altExe2},
			new String[] {altExe1Params, altExe2Params}, nativeCommands, BaseRepository.findById(dosboxVersions, dosboxVersionId));
		profile.setId(id);
		profile.setConfigurationFileLocation(configuration);
		profile.setStats(new GenericStats(created, modified, lastRun, runs));
		profile.setProfileStats(new ProfileStats(lastSetup, setups, playTime));
		return profile;
	}

	/**
	 * Used when duplicating a Profile
	 */
	public static Profile createCopy(Profile prof) {
		return create(prof.getTitle(), prof.isFavorite(), prof.getDeveloper(), prof.getPublisher(), prof.getGenre(), prof.getYear(), prof.getStatus(), prof.getNotes(), prof.getCustomStrings().clone(),
			prof.getCustomInts().clone(), prof.getLinks().clone(), prof.getCapturesString(), prof.getSetupString(), prof.getSetupParams(), prof.getAltExeStrings().clone(),
			prof.getAltExeParams().clone(), new ArrayList<>(prof.getNativeCommands()), prof.getDosboxVersion());
	}
	
	/**
	 * Used when editing a Profile
	 */
	public static Profile create(Profile prof) {
		Profile profile = createCopy(prof);
		profile.setId(prof.getId());
		profile.setConfiguration(new Configuration(prof.getConfiguration()));
		profile.setConfigurationFileLocation(prof.getConfiguration().getFile().getPath());
		profile.setStats(new GenericStats(
				prof.getStats().getCreated(), prof.getStats().getModified(),
				prof.getStats().getLastRun(), prof.getStats().getRuns()));
		profile.setProfileStats(new ProfileStats(
				prof.getProfileStats().getLastSetup(), prof.getProfileStats().getSetups(), prof.getProfileStats().getPlayTime()));
		return profile;
	}

	/**
	 * Used when instantiating a Profile from existing D-Fend data
	 */
	public static Profile create(String title, String developer, String publisher, String genre, String year, String status, String notes, boolean favorite, String setup, String setupParams) {
		String[] customStrings = new String[Profile.NR_OF_CUSTOM_STRINGS];
		Arrays.fill(customStrings, StringUtils.EMPTY);
		Link[] links = new Link[Profile.NR_OF_LINK_TITLES];
		Arrays.fill(links, new Link(StringUtils.EMPTY, StringUtils.EMPTY));
		List<NativeCommand> nativeCommands = new ArrayList<>();
		NativeCommand.insertDosboxCommand(nativeCommands);

		return create(title, favorite, developer, publisher, genre, year, status, notes, customStrings, new int[] {0, 0}, links, null, setup, setupParams,
			new String[] {StringUtils.EMPTY, StringUtils.EMPTY}, new String[] {StringUtils.EMPTY, StringUtils.EMPTY}, nativeCommands, null);
	}

	/**
	 * Used when instantiating a Profile from existing D-Fend Reloaded data
	 */
	public static Profile create(String title, String developer, String publisher, String genre, String year, String status, String notes, boolean favorite, String[] links, String[] linkTitles,
			String[] customStrings, String setup, String setupParams) {
		Link[] link = new Link[Profile.NR_OF_LINK_TITLES];
		for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
			link[i] = new Link(linkTitles[i], links[i]);
		}
		List<NativeCommand> nativeCommands = new ArrayList<>();
		NativeCommand.insertDosboxCommand(nativeCommands);

		return create(title, favorite, developer, publisher, genre, year, status, notes, customStrings, new int[] {0, 0}, link, null, setup, setupParams,
			new String[] {StringUtils.EMPTY, StringUtils.EMPTY}, new String[] {StringUtils.EMPTY, StringUtils.EMPTY}, nativeCommands, null);
	}

	/**
	 * Used when instantiating a Profile from eXoDOS data for converter and importer
	 */
	public static Profile create(String title, String developer, String publisher, String genre, String year, String status, String notes, boolean favorite, String[] links, String[] linkTitles,
			String rating, DosboxVersion dbVersion, String configuration) {
		String[] customStrings = new String[Profile.NR_OF_CUSTOM_STRINGS];
		Arrays.fill(customStrings, StringUtils.EMPTY);
		Link[] link = new Link[Profile.NR_OF_LINK_TITLES];
		for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
			link[i] = new Link(linkTitles[i], links[i]);
		}
		List<NativeCommand> nativeCommands = new ArrayList<>();
		NativeCommand.insertDosboxCommand(nativeCommands);

		int[] customInts = {0, 0};
		if (StringUtils.isNotBlank(rating))
			customInts[0] = (int)(Double.parseDouble(rating) * 20d);

		Profile profile = create(title, favorite, developer, publisher, genre, year, status, notes, customStrings, customInts, link, null, StringUtils.EMPTY, StringUtils.EMPTY,
			new String[] {StringUtils.EMPTY, StringUtils.EMPTY}, new String[] {StringUtils.EMPTY, StringUtils.EMPTY}, nativeCommands, dbVersion);
		profile.getConfiguration().setFileLocation(new FileLocation(configuration));
		return profile;
	}

	/**
	 * Used when instantiating a Profile from a GamePackArchive
	 */
	public static Profile create(String title, boolean favorite, String developer, String publisher, String genre, String year, String status, String notes, String[] customStrings, int[] customInts,
			String[] links, String[] linkTitles, String setup, String setupParams, String[] altExe, String[] altExeParams, List<NativeCommand> nativeCommands, DosboxVersion dbVersion,
			String configuration) {
		Link[] link = new Link[Profile.NR_OF_LINK_TITLES];
		for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
			link[i] = new Link(linkTitles[i], links[i]);
		}
		Profile profile = create(title, favorite, developer, publisher, genre, year, status, notes, customStrings, customInts, link, StringUtils.EMPTY, setup, setupParams, altExe, altExeParams,
			nativeCommands, dbVersion);
		profile.setConfigurationFileLocation(configuration);
		return profile;
	}

	/**
	 * Used when instantiating a new Profile based on just an existing configuration file (Repair)
	 */
	public static Profile create(File configuration, DosboxVersion dosboxVersion) {
		String[] customStrings = new String[Profile.NR_OF_CUSTOM_STRINGS];
		Arrays.fill(customStrings, StringUtils.EMPTY);
		Link[] links = new Link[Profile.NR_OF_LINK_TITLES];
		Arrays.fill(links, new Link(StringUtils.EMPTY, StringUtils.EMPTY));
		List<NativeCommand> nativeCommands = new ArrayList<>();
		NativeCommand.insertDosboxCommand(nativeCommands);

		Profile profile = create(StringUtils.EMPTY, false, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, customStrings,
			new int[] {0, 0}, links, null, StringUtils.EMPTY, StringUtils.EMPTY, new String[] {StringUtils.EMPTY, StringUtils.EMPTY}, new String[] {StringUtils.EMPTY, StringUtils.EMPTY},
			nativeCommands, dosboxVersion);
		profile.setConfigurationFileLocation(configuration.getPath());
		return profile;
	}

	/*
	 * Used when editing multiple profiles at once, to keep only the identical properties. Unequal properties are set to null
	 */
	public static Profile combine(List<DosboxVersion> allVersions, List<Profile> profiles, StringBuilder warningsLog) throws IOException {
		List<DosboxVersion> dbversions = profiles.stream().map(Profile::getDosboxVersion).toList();

		Map<DosboxVersion, Long> counts = dbversions.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
		Long max = counts.entrySet().stream().max(Comparator.comparing(Entry::getValue)).get().getValue();
		List<DosboxVersion> mostUsed = counts.entrySet().stream().filter(x -> x.getValue() == max).map(Entry::getKey).toList();

		DosboxVersion dbv = DosboxVersionRepository.findBestMatch(mostUsed, BaseRepository.findDefault(allVersions));

		Profile profile = null;
		Configuration configuration = null;

		for (Profile prof: profiles) {
			Profile currentProfile = createCopy(prof);
			warningsLog.append(currentProfile.loadConfigurationData(TextService.getInstance(), prof.getConfigurationString(), prof.getConfigurationCanonicalFile()));
			if (currentProfile.getDosboxVersion().getId() != dbv.getId())
				currentProfile.setToDosboxVersion(dbv);

			if (profile == null) {
				profile = currentProfile;
				configuration = currentProfile.getCombinedConfiguration();
			} else {
				profile.removeUnequalValuesIn(currentProfile);
				if (configuration != null)
					configuration.removeUnequalValuesIn(currentProfile.getCombinedConfiguration());
			}
		}

		if (profile != null && configuration != null) {
			profile.getConfiguration().setSections(configuration.getSections());
			profile.getConfiguration().setCustomSection(configuration.getCustomSection());
			profile.getConfiguration().setAutoexec(configuration.getAutoexec());
			profile.setStats(null);
		}

		return profile;
	}

	private static Profile create(String title, boolean favorite, String developer, String publisher, String genre, String year, String status, String notes, String[] customStrings, int[] customInts,
			Link[] links, String captures, String setup, String setupParams, String[] altExe, String[] altExeParams, List<NativeCommand> nativeCommands, DosboxVersion dosboxVersion) {
		Profile profile = new Profile();
		profile.setTitle(title);
		profile.setFavorite(favorite);
		profile.setDeveloper(developer);
		profile.setPublisher(publisher);
		profile.setGenre(genre);
		profile.setYear(year);
		profile.setStatus(status);
		profile.setNotes(notes);
		profile.setCustomStrings(customStrings);
		profile.setCustomInts(customInts);
		profile.setLinks(links);
		profile.setCapturesFileLocation(captures);
		profile.setSetupFileLocation(setup);
		profile.setSetupParams(setupParams);
		profile.setAltExeFileLocations(altExe);
		profile.setAltExeParams(altExeParams);
		profile.setNativeCommands(nativeCommands);
		profile.setDosboxVersion(dosboxVersion);
		profile.setConfiguration(new Configuration());
		profile.setStats(new GenericStats());
		profile.setProfileStats(new ProfileStats());
		return profile;
	}
}
