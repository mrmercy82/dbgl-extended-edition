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
package org.dbgl.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.model.Link;
import org.dbgl.model.NativeCommand;
import org.dbgl.model.SearchResult;
import org.dbgl.model.SearchResult.ResultType;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;
import org.dbgl.model.entity.LogEntry;
import org.dbgl.model.factory.DosboxVersionFactory;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.factory.TemplateFactory;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.model.repository.TemplateRepository;
import org.dbgl.service.DatabaseService;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ITextService;
import org.dbgl.service.ImportExportTemplatesService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.SystemUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.xml.sax.SAXException;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TemplateTests {

	static final SettingsService settings = SettingsService.getInstance();
	static {
		settings.setIntValue("profiledefaults", "confpath", 0);
		settings.setIntValue("profiledefaults", "conffile", 0);

		settings.setValue("directory", "data", new File("src", "test").getPath());
		settings.setValue("directory", "dosbox", new File(new File("src", "test"), "DOSBox").getPath());
		settings.setValue("database", "connectionstring", "jdbc:hsqldb:file:./Database/db/test");

		settings.setBooleanValue("log", "enabled", true);
	}

	static final DosboxVersionRepository dRepo = new DosboxVersionRepository();
	static final TemplateRepository tRepo = new TemplateRepository();
	static final ProfileRepository pRepo = new ProfileRepository();
	static final FileLocationService locService = FileLocationService.getInstance();
	static final ITextService text = TextService.getInstance();

	static final int DOSBOX_VERSIONS_CREATED = 18;
	static final int TEMPLATES_IN_IMPORT = 14;
	static final int TEMPLATES_CREATED = TEMPLATES_IN_IMPORT + 2;
	static final int PROFILES_CREATED = 5;
	static final int PROFILES_DUPLICATED = PROFILES_CREATED;
	static final int PROFILES_UPDATED = PROFILES_CREATED * 2;
	static final int LOG_ENTRIES = DOSBOX_VERSIONS_CREATED * 2 + TEMPLATES_CREATED * 5 + PROFILES_CREATED * 5 + PROFILES_DUPLICATED + PROFILES_UPDATED;

	@BeforeClass
	public static void setupBeforeClass() throws Exception {
		checkDirectories();
		checkDatabase();

		setupDosBoxVersions();
		setupTemplates();
		setupProfiles();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		cleanupProfiles();
		cleanupTemplates();
		cleanupDosboxVersions();
		cleanupLogEntries();

		DatabaseService.getInstance().shutdown();

		cleanupTemporaryDirectories();
	}

	private static void checkDirectories() {
		assertEquals(new File("src", "test").getPath(), settings.getValue("directory", "data"));
		assertEquals(new File(new File("src", "test"), "DOSBox").getPath(), settings.getValue("directory", "dosbox"));
		assertEquals("jdbc:hsqldb:file:./Database/db/test", settings.getValue("database", "connectionstring"));
		assertEquals("dosroot", locService.getDosroot().getName());
		assertEquals("test", locService.getDosroot().getParentFile().getName());
		assertEquals("src", locService.getDosroot().getParentFile().getParentFile().getName());
	}

	private static void checkDatabase() throws SQLException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Template> allTemplates = tRepo.listAll(allDosboxVersions);
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);
		List<LogEntry> allLogEntries = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY);

		assertEquals(0, allDosboxVersions.size());
		assertEquals(0, allTemplates.size());
		assertEquals(0, allProfiles.size());
		assertEquals(0, allLogEntries.size());
	}

	private static void setupDosBoxVersions() throws SQLException {
		SearchResult found = locService.findDosbox();
		assertEquals(ResultType.COMPLETE, found.result_);
		assertEquals("dosroot", found.dosbox_.getCwd().getName());
		assertEquals("test", found.dosbox_.getCwd().getParentFile().getName());
		assertEquals("src", found.dosbox_.getCwd().getParentFile().getParentFile().getName());
		assertEquals(-1, found.dosbox_.getId());

		DosboxVersion db0743 = dRepo.add(found.dosbox_);
		int db074Id = db0743.getId();

		assertTrue(db074Id >= 0);
		assertEquals("0.74-3", db0743.getVersion());

		DosboxVersion db063 = DosboxVersionFactory.create("DOSBox 0.63", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.63", false, false, false, null, "DOSBox-0.63", "", "", "DOSBox-0.63/dosbox.conf");
		assertEquals(-1, db063.getId());
		db063 = dRepo.add(db063);
		assertEquals(db074Id + 1L, db063.getId());
		assertEquals("0.63", db063.getVersion());

		assertEquals(113, db0743.distance(db063));

		dRepo.add(DosboxVersionFactory.create("DOSBox 0.65", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.65", false, false, false, null, "DOSBox-0.65", "", "", "DOSBox-0.65/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox 0.70", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.70", false, true, false, null, "DOSBox-0.70", "", "", "DOSBox-0.70/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox 0.72", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.72", false, true, false, null, "DOSBox-0.72", "", "", "DOSBox-0.72/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox 0.73", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.73", false, true, false, null, "DOSBox-0.73", "", "", "DOSBox-0.73/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox 0.74", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.74", false, true, false, null, "DOSBox-0.74", "", "", "DOSBox-0.74/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox 0.74-WithMounts", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.74", false, true, false, null, "DOSBox-0.74-WithMounts", "", "", "DOSBox-0.74-WithMounts/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox Gulikoza", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.73", false, true, false, null, "DOSBox-Gulikoza", "", "", "DOSBox-Gulikoza/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox MB5", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.73", false, true, false, null, "DOSBox-MB5", "", "", "DOSBox-MB5/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox SVN", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.74", false, true, false, null, "DOSBox-SVN", "", "", "DOSBox-SVN/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox VOD", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.74", false, true, false, null, "DOSBox-VOD", "", "", "DOSBox-VOD/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox Ykhwong", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.74", false, true, false, null, "DOSBox-Ykhwong", "", "", "DOSBox-Ykhwong/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox ECE WithMounts", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.74-2", false, true, false, null, "DOSBox-ECE-r4192-WithMounts", "", "", "DOSBox-ECE-r4192-WithMounts/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox SVN r4483", SettingsService.SVN_DOSBOX_FAM.title_, "r4483", false, true, false, null, "DOSBox-SVN-r4483", "", "", "DOSBox-SVN-r4483/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox ECE r4482", SettingsService.ECE_DOSBOX_FAM.title_, "r4482", false, true, false, null, "DOSBox-ECE-r4482", "", "", "DOSBox-ECE-r4482/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox Staging 0.81.2", SettingsService.STAGING_DOSBOX_FAM.title_, "0.81.2", false, true, false, null, "DOSBox-Staging-0.81.2", "", "", "DOSBox-Staging-0.81.2/dosbox.conf"));
		dRepo.add(DosboxVersionFactory.create("DOSBox X 20240701", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, "2024.07.01", false, true, false, null, "DOSBox-X-20240701", "", "", "DOSBox-X-20240701/dosbox.conf"));
		
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		assertEquals(DOSBOX_VERSIONS_CREATED, allDosboxVersions.size());
	}

	private static void setupTemplates() throws SQLException, XPathExpressionException, ParseException, SAXException, ParserConfigurationException, IOException {
		List<Template> templates = new ArrayList<>();
		try {
			ImportExportTemplatesService.doImport(templates);
			fail("IOException expected");
		} catch (IOException e) {
			assertTrue(StringUtils.isNotBlank(e.getMessage()));
		}

		File srcTemplates = new File("src/dist/shared/templates/", locService.getDefaultTemplatesXmlFile().getName());
		FileUtils.copyFile(srcTemplates, locService.getDefaultTemplatesXmlFile());

		String importTemplatesWarningsLog = ImportExportTemplatesService.doImport(templates);

		assertEquals(StringUtils.EMPTY, importTemplatesWarningsLog);
		int templatesCount = templates.size();

		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		DosboxVersion defaultDosboxVersion = BaseRepository.findDefault(allDosboxVersions);
		defaultDosboxVersion.resetAndLoadConfiguration();

		Template template01 = TemplateFactory.create("Custom template with 2 mounts", defaultDosboxVersion);
		template01.getConfiguration().setValue("template", "myitem", "myvalue");
		template01.getConfiguration().getAutoexec().setExit(false);
		template01.getConfiguration().getAutoexec().setCustomSections(new String[] {"start", "before", "after", "end"});
		template01.setBooter(true);
		template01.addMount("mount a .");
		template01.addMount("imgmount d mygame\\game.iso");
		template01.getNativeCommands().add(new NativeCommand("mycommand.exe", "param1", ".", true, 1));
		template01 = tRepo.add(template01);
		assertEquals(2, template01.getNettoMountedDrives().size());
		templatesCount++;

		DosboxVersion dosboxStaging = allDosboxVersions.get(14);
		Template template02 = TemplateFactory.create("Custom template using DOSBox Staging", dosboxStaging);
		template02.getConfiguration().setValue("sdl", "vsync", "true");
		template02 = tRepo.add(template02);
		assertEquals(0, template02.getNettoMountedDrives().size());
		templatesCount++;
		
		assertEquals(TEMPLATES_CREATED, templatesCount);
	}

	public static void setupProfiles() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Template> allTemplates = tRepo.listAll(allDosboxVersions);
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);

		assertEquals(DOSBOX_VERSIONS_CREATED, allDosboxVersions.size());
		assertEquals(TEMPLATES_CREATED, allTemplates.size());
		assertEquals(0, allProfiles.size());

		DosboxVersion defaultDosboxVersion = BaseRepository.findDefault(allDosboxVersions);
		defaultDosboxVersion.resetAndLoadConfiguration();

		Profile profile0 = ProfileFactory.create(defaultDosboxVersion, null);
		assertEquals(-1, profile0.getId());

		profile0.setTitle("Profile 0 : Empty");
		profile0.setDeveloper(StringUtils.EMPTY);
		profile0.setPublisher(StringUtils.EMPTY);
		profile0.setGenre(StringUtils.EMPTY);
		profile0.setYear(StringUtils.EMPTY);
		profile0.setStatus(StringUtils.EMPTY);
		profile0.setNotes(StringUtils.EMPTY);
		profile0.setFavorite(false);
		Link[] links = new Link[Profile.NR_OF_LINK_TITLES];
		for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
			links[i] = new Link(StringUtils.EMPTY, StringUtils.EMPTY);
		}
		profile0.setLinks(links);
		profile0.setCustomStrings(new String[] {StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY,
				StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY});
		profile0.setCustomInts(new int[] {0, 0});
		profile0.setSetupFileLocation(StringUtils.EMPTY);
		profile0.setSetupParams(StringUtils.EMPTY);
		profile0.setAltExeFileLocations(new String[] {StringUtils.EMPTY, StringUtils.EMPTY});
		profile0.setAltExeParams(new String[] {StringUtils.EMPTY, StringUtils.EMPTY});

		profile0 = pRepo.add(profile0);

		assertTrue(profile0.getId() >= 0);

		Template template = allTemplates.get(0); // IBM PCjr, i8088 4.77Mhz, 512KB RAM, CGA Plus, 3-voice sound, ~1984 (Booter)
		template.resetAndLoadConfiguration();

		Profile profile1 = ProfileFactory.create(defaultDosboxVersion, template);
		assertEquals(-1, profile1.getId());

		profile1.setTitle("Profile 1 : PCjr");
		profile1.setDeveloper("Dev1");
		profile1.setPublisher("Pub1");
		profile1.setGenre("Gen1");
		profile1.setYear("Yr1");
		profile1.setStatus("St1");
		profile1.setNotes("Not1");
		profile1.setFavorite(true);
		Link[] links1 = new Link[Profile.NR_OF_LINK_TITLES];
		for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
			links1[i] = new Link("LnkTit" + i, "LnkDst" + i);
		}
		profile1.setLinks(links1);
		profile1.setCustomStrings(new String[] {"cust0", "cust1", "cust2", "cust3", "cust4", "cust5", "cust6", "cust7", "cust8", "cust9", "cust10", "cust11"});
		profile1.setCustomInts(new int[] {1, 2});
		profile1.setSetupFileLocation("game1" + File.separator + "setup.exe");
		profile1.setSetupParams("setupparams");
		profile1.setAltExeFileLocations(new String[] {"game1" + File.separator + "altexe1.exe", "game1" + File.separator + "altexe2.exe"});
		profile1.setAltExeParams(new String[] {"alt1params", "alt2params"});

		profile1.addMount("mount c .");
		profile1.setAutoexecSettings(StringUtils.EMPTY, StringUtils.EMPTY);
		profile1.setAutoexecSettings(new String[] {"game1" + File.separator + "disk.ima", StringUtils.EMPTY, StringUtils.EMPTY}, StringUtils.EMPTY);

		profile1 = pRepo.add(profile1);

		assertTrue(profile1.getId() >= 0);

		DosboxVersion dbEceWithMounts = allDosboxVersions.get(8);
		dbEceWithMounts.resetAndLoadConfiguration();

		Profile profile2 = ProfileFactory.create(dbEceWithMounts, null);
		assertEquals(-1, profile2.getId());

		profile2.setTitle("Profile 2 : using dosbox mount");
		profile2.getConfiguration().setValue("cpu", "cycles", "auto limit 10000");
		profile2.setAutoexecSettings("game.exe", StringUtils.EMPTY);

		profile2 = pRepo.add(profile2);

		assertTrue(profile2.getId() >= 0);

		Template templateWithSpecialFeatures = allTemplates.get(14); // 2 mounts, native command, custom sections and more
		templateWithSpecialFeatures.resetAndLoadConfiguration();

		Profile profile3 = ProfileFactory.create(defaultDosboxVersion, templateWithSpecialFeatures);
		assertEquals(-1, profile3.getId());

		profile3.setTitle("Profile 3 : using template features");
		assertEquals("myvalue", profile3.getConfiguration().getValue("template", "myitem"));
		assertEquals(false, profile3.getConfiguration().getAutoexec().isExit());
		assertArrayEquals(new String[] {"start" + SystemUtils.EOLN, "before" + SystemUtils.EOLN, "after" + SystemUtils.EOLN, "end" + SystemUtils.EOLN},
			profile3.getConfiguration().getAutoexec().getCustomSections());
		assertEquals(true, profile3.getConfiguration().getAutoexec().getBooterByDefault());
		assertEquals(2, profile3.getConfiguration().getAutoexec().getMountingpoints().size());
		assertEquals(2, profile3.getNativeCommands().size());

		profile3.setAutoexecSettings(new String[] {"mygame.img", StringUtils.EMPTY, StringUtils.EMPTY}, StringUtils.EMPTY);
		profile3.addMount("mount e dosgames");
		profile3 = pRepo.add(profile3);

		assertTrue(profile3.getId() >= 0);
		
		DosboxVersion dosboxStaging = allDosboxVersions.get(14);
		dosboxStaging.resetAndLoadConfiguration();
		Profile profile4 = ProfileFactory.create(dosboxStaging, null);
		assertEquals(-1, profile4.getId());

		profile4.setTitle("Profile 4 : using dosbox staging");
		profile4.getConfiguration().setValue("cpu", "cycles", "20000");
		profile4.addMount("mount c .");
		profile4.setAutoexecSettings("prof4.exe", "parm4");

		profile4 = pRepo.add(profile4);

		assertTrue(profile4.getId() >= 0);
	}

	private static void cleanupProfiles() throws SQLException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);
		assertEquals(PROFILES_CREATED * 2L, allProfiles.size());
		for (Profile profile: allProfiles) {
			pRepo.remove(profile, true, true, true);
		}
		assertEquals(0, pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions).size());
	}

	private static void cleanupTemplates() throws SQLException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Template> allTemplates = tRepo.listAll(allDosboxVersions);
		assertEquals(TEMPLATES_CREATED * 2L, allTemplates.size());
		for (Template template: allTemplates) {
			tRepo.remove(template, true);
		}
		assertEquals(0, tRepo.listAll(allDosboxVersions).size());
	}

	private static void cleanupDosboxVersions() throws SQLException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		assertEquals(DOSBOX_VERSIONS_CREATED, allDosboxVersions.size());
		for (DosboxVersion version: allDosboxVersions) {
			dRepo.remove(version);
		}
		assertEquals(0, dRepo.listAll().size());
	}

	private static void cleanupLogEntries() throws SQLException {
		List<LogEntry> allLogEntries = dRepo.list(StringUtils.EMPTY, StringUtils.EMPTY);
		assertEquals(LOG_ENTRIES, allLogEntries.size());
		dRepo.clear();
		assertEquals(0, dRepo.list(StringUtils.EMPTY, StringUtils.EMPTY).size());
	}

	private static void cleanupTemporaryDirectories() throws IOException {
		FileUtils.deleteDirectory(new File(locService.getDosroot().getParentFile(), "Database"));
		FileUtils.deleteDirectory(new File(locService.getDosroot().getParentFile(), "captures"));
		FileUtils.deleteDirectory(new File(locService.getDosroot().getParentFile(), "dosroot"));
		FileUtils.deleteDirectory(new File(locService.getDosroot().getParentFile(), "export"));
		FileUtils.deleteDirectory(new File(locService.getDosroot().getParentFile(), "profiles"));
		FileUtils.deleteDirectory(new File(locService.getDosroot().getParentFile(), "templates"));
		FileUtils.deleteDirectory(new File(locService.getDosroot().getParentFile(), "xsl"));
	}

	@Test
	public void test001_LoadDosboxVersionsAndCheckConfiguration() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();

		assertEquals(DOSBOX_VERSIONS_CREATED, allDosboxVersions.size());

		for (DosboxVersion version: allDosboxVersions) {
			assertTrue(version.getId() >= 0);

			String loadDosboxWarningsLog = version.resetAndLoadConfiguration();

			assertEquals(StringUtils.EMPTY, loadDosboxWarningsLog);

			switch (version.getTitle()) {
				case "DOSBox 0.63":
					assertEquals(false, version.isDefault());
					assertEquals(false, version.isMultiConfig());
					assertEquals(Generation.official_063, GenerationAwareConfigurable.determineGen(version));
					assertEquals(0, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox 0.65":
					assertEquals(false, version.isDefault());
					assertEquals(false, version.isMultiConfig());
					assertEquals(Generation.official_065, GenerationAwareConfigurable.determineGen(version));
					assertEquals(0, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox 0.70":
					assertEquals(false, version.isDefault());
					assertEquals(true, version.isMultiConfig());
					assertEquals(Generation.official_070, GenerationAwareConfigurable.determineGen(version));
					assertEquals(0, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox 0.72":
					assertEquals(false, version.isDefault());
					assertEquals(true, version.isMultiConfig());
					assertEquals(Generation.official_070, GenerationAwareConfigurable.determineGen(version));
					assertEquals(0, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox 0.73":
					assertEquals(false, version.isDefault());
					assertEquals(true, version.isMultiConfig());
					assertEquals(Generation.official_073, GenerationAwareConfigurable.determineGen(version));
					assertEquals(0, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox 0.74":
					assertEquals(false, version.isDefault());
					assertEquals(true, version.isMultiConfig());
					assertEquals(Generation.official_073, GenerationAwareConfigurable.determineGen(version));
					assertEquals(0, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox 0.74-3":
					assertEquals(true, version.isDefault());
					assertEquals(true, version.isMultiConfig());
					assertEquals(Generation.official_073, GenerationAwareConfigurable.determineGen(version));
					assertEquals(0, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox 0.74-WithMounts":
					assertEquals(false, version.isDefault());
					assertEquals(true, version.isMultiConfig());
					assertEquals(Generation.official_073, GenerationAwareConfigurable.determineGen(version));
					assertEquals(3, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox Gulikoza":
					assertEquals(false, version.isDefault());
					assertEquals(true, version.isMultiConfig());
					assertEquals(Generation.official_073, GenerationAwareConfigurable.determineGen(version));
					assertEquals(0, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox MB5":
					assertEquals(false, version.isDefault());
					assertEquals(true, version.isMultiConfig());
					assertEquals(Generation.official_073, GenerationAwareConfigurable.determineGen(version));
					assertEquals(0, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox SVN":
					assertEquals(false, version.isDefault());
					assertEquals(true, version.isMultiConfig());
					assertEquals(Generation.official_073, GenerationAwareConfigurable.determineGen(version));
					assertEquals(0, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox VOD":
					assertEquals(false, version.isDefault());
					assertEquals(true, version.isMultiConfig());
					assertEquals(Generation.official_073, GenerationAwareConfigurable.determineGen(version));
					assertEquals(0, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox Ykhwong":
					assertEquals(false, version.isDefault());
					assertEquals(true, version.isMultiConfig());
					assertEquals(Generation.official_073, GenerationAwareConfigurable.determineGen(version));
					assertEquals(0, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox ECE WithMounts":
					assertEquals(false, version.isDefault());
					assertEquals(true, version.isMultiConfig());
					assertEquals(Generation.official_073, GenerationAwareConfigurable.determineGen(version));
					assertEquals(3, version.getConfiguration().getAutoexec().getMountingpoints().size());
					break;
				case "DOSBox SVN r4483":
					assertEquals(Generation.svn_r4483, GenerationAwareConfigurable.determineGen(version));
					assertTrue(version.getConfiguration().hasValue("joystick", "circularinput"));
					break;
				case "DOSBox ECE r4482":
					assertEquals(Generation.ece_r4482, GenerationAwareConfigurable.determineGen(version));
					assertTrue(version.getConfiguration().hasValue("glide", "glide"));
					break;
				case "DOSBox Staging 0.81.2":
					assertEquals(Generation.staging_0810, GenerationAwareConfigurable.determineGen(version));
					assertTrue(version.getConfiguration().hasValue("capture", "capture_dir"));
					break;
				case "DOSBox X 20240701":
					assertEquals(Generation.x_20240301, GenerationAwareConfigurable.determineGen(version));
					assertTrue(version.getConfiguration().hasValue("render", "monochrome_pal"));
					break;
				default:
					fail("unknown dosbox version");
			}
		}
	}

	@Test
	public void test002_LoadTemplatesAndCheckConfiguration() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Template> allTemplates = tRepo.listAll(allDosboxVersions);

		assertEquals(TEMPLATES_CREATED, allTemplates.size());

		for (Template template: allTemplates) {
			assertTrue(template.getId() >= 0);

			String loadTemplateWarningsLog = template.resetAndLoadConfiguration();
			assertEquals(StringUtils.EMPTY, loadTemplateWarningsLog);

			File templateConfigurationFile = template.getConfigurationCanonicalFile();
			String fileToString = FileUtils.readFileToString(templateConfigurationFile, StandardCharsets.UTF_8);
			String confString = template.getConfigurationString();
			assertEquals(confString, fileToString);
		}
	}

	@Test
	public void test003_DuplicateTemplates() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Template> allTemplates = tRepo.listAll(allDosboxVersions);

		assertEquals(TEMPLATES_CREATED, allTemplates.size());

		for (Template template: allTemplates) {
			String loadTemplateWarningsLog = template.resetAndLoadConfiguration();
			assertEquals(StringUtils.EMPTY, loadTemplateWarningsLog);

			Template duplicate = tRepo.duplicate(template);
			assertTrue(duplicate.getId() >= TEMPLATES_CREATED);
			assertEquals(duplicate.getTitle(), template.getTitle());
			File templateConfigurationFile = template.getConfigurationCanonicalFile();
			String fileToString = FileUtils.readFileToString(templateConfigurationFile, StandardCharsets.UTF_8);
			String confString = duplicate.getConfigurationString();
			assertEquals(confString, fileToString);
		}

		allTemplates = tRepo.listAll(allDosboxVersions);
		assertEquals(TEMPLATES_CREATED * 2L, allTemplates.size());
	}

	@Test
	public void test004_ReloadDosboxVersionForTemplates() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Template> allTemplates = tRepo.listAll(allDosboxVersions);

		assertEquals(TEMPLATES_CREATED * 2L, allTemplates.size());

		for (Template template: allTemplates) {
			String loadTemplateWarningsLog = template.resetAndLoadConfiguration();
			assertEquals(StringUtils.EMPTY, loadTemplateWarningsLog);

			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(2, template.getConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(0, template.getConfiguration().getAutoexec().getMountingpoints().size());
			}

			if (template.getTitle().equals("Custom template using DOSBox Staging")) {

				assertEquals("capture", template.getDosboxVersion().getConfiguration().getValue("capture", "capture_dir"));
				assertEquals(false, template.getConfiguration().hasValue("capture", "capture_dir"));
				assertEquals("capture", template.getCombinedConfiguration().getValue("capture", "capture_dir"));

				template.setValue("render", "glshader", "crt-auto-arcade-sharp");
				assertEquals(true, template.getConfiguration().hasValue("render", "glshader"));
				assertEquals("crt-auto", template.getDosboxVersion().getConfiguration().getValue("render", "glshader"));
				assertEquals("crt-auto-arcade-sharp", template.getCombinedConfiguration().getValue("render", "glshader"));
				
				template.reloadDosboxVersion(template.getDosboxVersion());
				assertEquals(false, template.getConfiguration().hasValue("render", "glshader"));
				assertEquals("crt-auto", template.getDosboxVersion().getConfiguration().getValue("render", "glshader"));
				assertEquals("crt-auto", template.getCombinedConfiguration().getValue("render", "glshader"));
				
			} else {
				assertEquals("normal2x", template.getDosboxVersion().getConfiguration().getValue("render", "scaler"));
				assertEquals(false, template.getConfiguration().hasValue("render", "scaler"));
				assertEquals("normal2x", template.getCombinedConfiguration().getValue("render", "scaler"));
	
				template.setValue("render", "scaler", "hq3x");
				assertEquals(true, template.getConfiguration().hasValue("render", "scaler"));
				assertEquals("normal2x", template.getDosboxVersion().getConfiguration().getValue("render", "scaler"));
				assertEquals("hq3x", template.getCombinedConfiguration().getValue("render", "scaler"));
	
				template.reloadDosboxVersion(template.getDosboxVersion());
				assertEquals(false, template.getConfiguration().hasValue("render", "scaler"));
				assertEquals("normal2x", template.getDosboxVersion().getConfiguration().getValue("render", "scaler"));
				assertEquals("normal2x", template.getCombinedConfiguration().getValue("render", "scaler"));
			}

			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(2, template.getConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(0, template.getConfiguration().getAutoexec().getMountingpoints().size());
			}
		}

		allTemplates = tRepo.listAll(allDosboxVersions);
		assertEquals(TEMPLATES_CREATED * 2L, allTemplates.size());
	}

	@Test
	public void test005_ReloadOtherDosboxVersionForTemplates() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Template> allTemplates = tRepo.listAll(allDosboxVersions);

		DosboxVersion db063 = allDosboxVersions.get(0); // list ordered by title
		DosboxVersion db0742 = allDosboxVersions.get(6);
		DosboxVersion db0742WithMounts = allDosboxVersions.get(7);
		DosboxVersion dbEceWithMounts = allDosboxVersions.get(8);
		DosboxVersion dbVod = allDosboxVersions.get(15);
		DosboxVersion dbStaging = allDosboxVersions.get(14);
		DosboxVersion dbX = allDosboxVersions.get(16);

		assertEquals(db0742, BaseRepository.findDefault(allDosboxVersions));

		for (Template template: allTemplates) {
			String loadTemplateWarningsLog = template.resetAndLoadConfiguration();
			assertEquals(StringUtils.EMPTY, loadTemplateWarningsLog);

			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(2, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(0, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			}

			assertEquals("svga_s3", template.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", template.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
	
			template.reloadDosboxVersion(db063);
			assertEquals("vga", template.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("normal", template.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals("vga", template.getCombinedConfiguration().getValue("dosbox", "machine"));
			assertEquals("normal", template.getCombinedConfiguration().getValue("cpu", "core"));
		
			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(2, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(0, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			}

			template.reloadDosboxVersion(db0742);
			assertEquals("svga_s3", template.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", template.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals("svga_s3", template.getCombinedConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", template.getCombinedConfiguration().getValue("cpu", "core"));

			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(2, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(0, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			}

			template.addMount("mount c .");
			assertEquals(0, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(3, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(3, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals("mount A \".\"", template.getCombinedConfiguration().getAutoexec().getMountingpoints().get(0).toString());
				assertEquals("imgmount D \"mygame" + File.separator + "game.iso\"", template.getCombinedConfiguration().getAutoexec().getMountingpoints().get(1).toString());
				assertEquals("mount C \".\"", template.getCombinedConfiguration().getAutoexec().getMountingpoints().get(2).toString());
			} else {
				assertEquals(1, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(1, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals("mount C \".\"", template.getCombinedConfiguration().getAutoexec().getMountingpoints().get(0).toString());
			}

			// reloading dbversion will keep the existing 'custom' mount
			template.reloadDosboxVersion(db0742);
			assertEquals(0, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(3, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(3, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals("mount A \".\"", template.getCombinedConfiguration().getAutoexec().getMountingpoints().get(0).toString());
				assertEquals("imgmount D \"mygame" + File.separator + "game.iso\"", template.getCombinedConfiguration().getAutoexec().getMountingpoints().get(1).toString());
				assertEquals("mount C \".\"", template.getCombinedConfiguration().getAutoexec().getMountingpoints().get(2).toString());
			} else {
				assertEquals(1, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(1, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals("mount C \".\"", template.getCombinedConfiguration().getAutoexec().getMountingpoints().get(0).toString());
			}

			// reloading dbversion will overwrite the existing 'custom' c-drive mount since it also exists in db0742WithMounts
			template.reloadDosboxVersion(db0742WithMounts);
			assertEquals(3, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());

			if (template.getTitle().equals("Custom template with 2 mounts")) {
				// here, the D drive also got overwritten, leaving only 1 custom mount
				assertEquals(1, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(4, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals("mount C \".\"", template.getCombinedConfiguration().getAutoexec().getMountingpoints().get(0).toString());
			} else {
				assertEquals(0, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(3, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals("mount C \".\"", template.getCombinedConfiguration().getAutoexec().getMountingpoints().get(0).toString());
			}

			// removing a dosbox mount will actually introduce 1 extra custom unmount
			template.removeMountBasedOnIndexUI(1);
			assertEquals(3, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(2, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(5, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(4, template.getMountingPointsForUI().size());
			} else {
				assertEquals(1, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(4, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(3, template.getMountingPointsForUI().size());
			}

			// reloading dbversion will remove all existing dosbox mounts, leaving none
			template.reloadDosboxVersion(db0742);
			assertEquals(0, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(1, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(1, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(0, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(0, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			}
			template.addMount("mount a .");
			template.addMount("mount b .");
			template.addMount("mount c .");
			template.addMount("mount d .");
			template.addMount("mount e .");
			template.addMount("mount f .");
			if (template.getTitle().equals("Custom template with 2 mounts")) {
				// double A mount, not sure if this should be allowed
				assertEquals(7, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(6, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			}

			// reloading dbversion will keep the existing a, b and f mounts, and overwrite the c, d and e ones
			template.reloadDosboxVersion(dbEceWithMounts);
			assertEquals(3, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(4, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(7, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(3, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(6, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			}
			assertEquals("50", template.getDosboxVersion().getConfiguration().getValue("sdl", "surfacenp-sharpness"));
			assertEquals("150", template.getDosboxVersion().getConfiguration().getValue("sblaster", "fmstrength"));

			// reloading dbversion will keep the 3 existing 'custom' mounts
			template.reloadDosboxVersion(dbVod);
			assertEquals(0, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(4, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(4, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(3, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(3, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			}
			
			template.reloadDosboxVersion(dbStaging);
			assertEquals(0, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
			assertEquals(0, template.getConfiguration().getSectionNames().length);
			assertFalse(template.getCombinedConfiguration().hasValue("dosbox", "captures"));
			assertTrue(template.getCombinedConfiguration().hasValue("capture", "capture_dir"));
			assertEquals("opengl", template.getCombinedConfiguration().getValue("sdl", "output"));
			assertEquals("auto auto", template.getCombinedConfiguration().getValue("sdl", "priority"));
			
			template.reloadDosboxVersion(dbX);
			assertEquals(0, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
			assertEquals(0, template.getConfiguration().getSectionNames().length);
			assertTrue(template.getCombinedConfiguration().hasValue("dosbox", "captures"));
			assertFalse(template.getCombinedConfiguration().hasValue("capture", "capture_dir"));
			assertEquals("ttf", template.getCombinedConfiguration().getValue("sdl", "output"));
			assertEquals("higher,normal", template.getCombinedConfiguration().getValue("sdl", "priority"));
		}
	}

	@Test
	public void test006_SetOtherDosboxVersionForTemplates() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Template> allTemplates = tRepo.listAll(allDosboxVersions);

		DosboxVersion db063 = allDosboxVersions.get(0);
		DosboxVersion db074 = allDosboxVersions.get(5); // list ordered by title
		DosboxVersion db0742WithMounts = allDosboxVersions.get(7);
		DosboxVersion dbStaging = allDosboxVersions.get(14);
		DosboxVersion dbX = allDosboxVersions.get(16);

		for (Template template: allTemplates) {
			String loadTemplateWarningsLog = template.resetAndLoadConfiguration();
			assertEquals(StringUtils.EMPTY, loadTemplateWarningsLog);

			assertEquals("svga_s3", template.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", template.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals(false, template.getDosboxVersion().getConfiguration().getBooleanValue("gus", "gus"));
			assertEquals(true, template.getCombinedConfiguration().hasValue("joystick", "joysticktype"));

			template.setToDosboxVersion(db063);
			assertEquals("vga", template.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("normal", template.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals(false, template.getCombinedConfiguration().getBooleanValue("gus", "gus"));
			assertEquals(false, template.getCombinedConfiguration().hasValue("joystick", "joysticktype"));

			template.reloadDosboxVersion(db074);
			assertEquals("svga_s3", template.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", template.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals(false, template.getDosboxVersion().getConfiguration().getBooleanValue("gus", "gus"));
			assertEquals("svga_s3", template.getCombinedConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", template.getCombinedConfiguration().getValue("cpu", "core"));
			assertEquals(false, template.getCombinedConfiguration().getBooleanValue("gus", "gus"));
			assertEquals(true, template.getCombinedConfiguration().hasValue("joystick", "joysticktype"));

			template.setToDosboxVersion(db0742WithMounts);
			template.addMount("mount f .");
			assertEquals(3, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(3, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(6, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(1, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(4, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			}

			template.setToDosboxVersion(db074);
			assertEquals(0, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(3, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(3, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(1, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(1, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			}
			
			template.setToDosboxVersion(dbStaging);
			assertEquals("opengl", template.getDosboxVersion().getConfiguration().getValue("sdl", "output"));
			assertEquals("discrete", template.getDosboxVersion().getConfiguration().getValue("speaker", "pcspeaker"));
			assertEquals("100", template.getDosboxVersion().getConfiguration().getValue("mouse", "mouse_sensitivity"));
			assertEquals(false, template.getDosboxVersion().getConfiguration().hasValue("dosbox", "captures"));
			assertEquals(true, template.getDosboxVersion().getConfiguration().hasValue("capture", "capture_dir"));
			assertEquals("original", template.getCombinedConfiguration().getValue("sdl", "fullresolution"));
			assertEquals("higher normal", template.getCombinedConfiguration().getValue("sdl", "priority"));
			assertEquals("false", template.getCombinedConfiguration().getValue("render", "aspect"));
			
			template.setToDosboxVersion(dbX);
			assertEquals("ttf", template.getDosboxVersion().getConfiguration().getValue("sdl", "output"));
			assertEquals("true", template.getDosboxVersion().getConfiguration().getValue("speaker", "pcspeaker"));
			assertEquals("100", template.getDosboxVersion().getConfiguration().getValue("sdl", "sensitivity"));
			assertEquals(true, template.getDosboxVersion().getConfiguration().hasValue("dosbox", "captures"));
			assertEquals(false, template.getDosboxVersion().getConfiguration().hasValue("capture", "capture_dir"));
			assertEquals("original", template.getCombinedConfiguration().getValue("sdl", "fullresolution"));
			assertEquals("higher,normal", template.getCombinedConfiguration().getValue("sdl", "priority"));
			assertEquals("false", template.getCombinedConfiguration().getValue("render", "aspect"));
		}
	}

	@Test
	public void test007_SwitchToOtherDosboxVersionForTemplates() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Template> allTemplates = tRepo.listAll(allDosboxVersions);

		DosboxVersion db063 = allDosboxVersions.get(0);
		DosboxVersion db074 = allDosboxVersions.get(5); // list ordered by title
		DosboxVersion db0742WithMounts = allDosboxVersions.get(7);
		DosboxVersion dbStaging = allDosboxVersions.get(14);
		DosboxVersion dbX = allDosboxVersions.get(16);

		for (Template template: allTemplates) {
			String loadTemplateWarningsLog = template.resetAndLoadConfiguration();
			assertEquals(StringUtils.EMPTY, loadTemplateWarningsLog);
			
			boolean customTemplate = Strings.CS.equalsAny(template.getTitle(), "Custom template with 2 mounts", "Custom template using DOSBox Staging");

			assertEquals("svga_s3", template.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", template.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals(false, template.getDosboxVersion().getConfiguration().getBooleanValue("gus", "gus"));
			assertEquals(true, template.getCombinedConfiguration().hasValue("joystick", "joysticktype"));
			assertEquals(!customTemplate, template.getConfiguration().hasValue("cpu", "cycles"));
			String cycles = template.getConfiguration().getValue("cpu", "cycles");

			template.switchToDosboxVersion(db063);
			assertEquals("vga", template.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("normal", template.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals(true, template.getCombinedConfiguration().getBooleanValue("gus", "gus"));
			assertEquals(false, template.getCombinedConfiguration().hasValue("joystick", "joysticktype"));
			assertEquals(cycles, template.getConfiguration().getValue("cpu", "cycles"));

			template.switchToDosboxVersion(db074);
			assertEquals("svga_s3", template.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", template.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals(false, template.getDosboxVersion().getConfiguration().getBooleanValue("gus", "gus"));
			assertEquals(true, template.getCombinedConfiguration().hasValue("joystick", "joysticktype"));
			assertEquals(false, template.getCombinedConfiguration().getBooleanValue("gus", "gus"));
			assertEquals(cycles, template.getConfiguration().getValue("cpu", "cycles"));

			template.switchToDosboxVersion(db0742WithMounts);
			template.addMount("mount f .");
			assertEquals(3, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(3, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(6, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(1, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(4, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			}

			template.switchToDosboxVersion(db074);
			assertEquals(0, template.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
			if (template.getTitle().equals("Custom template with 2 mounts")) {
				assertEquals(3, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(3, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			} else {
				assertEquals(1, template.getConfiguration().getAutoexec().getMountingpoints().size());
				assertEquals(1, template.getCombinedConfiguration().getAutoexec().getMountingpoints().size());
			}
			
			template.switchToDosboxVersion(dbStaging);
			assertEquals("opengl", template.getDosboxVersion().getConfiguration().getValue("sdl", "output"));
			assertEquals("discrete", template.getDosboxVersion().getConfiguration().getValue("speaker", "pcspeaker"));
			assertEquals("100", template.getDosboxVersion().getConfiguration().getValue("mouse", "mouse_sensitivity"));
			assertEquals(false, template.getDosboxVersion().getConfiguration().hasValue("dosbox", "captures"));
			assertEquals(true, template.getDosboxVersion().getConfiguration().hasValue("capture", "capture_dir"));
			assertEquals("desktop", template.getCombinedConfiguration().getValue("sdl", "fullresolution"));
			assertEquals("auto auto", template.getCombinedConfiguration().getValue("sdl", "priority"));
			assertEquals("auto", template.getCombinedConfiguration().getValue("render", "aspect"));
			if (cycles == null)
				assertEquals("auto", template.getCombinedConfiguration().getValue("cpu", "cycles"));
			else
				assertEquals(cycles, template.getCombinedConfiguration().getValue("cpu", "cycles"));
			
			template.switchToDosboxVersion(dbX);
			assertEquals("ttf", template.getDosboxVersion().getConfiguration().getValue("sdl", "output"));
			assertEquals("true", template.getDosboxVersion().getConfiguration().getValue("speaker", "pcspeaker"));
			assertEquals("100", template.getDosboxVersion().getConfiguration().getValue("sdl", "sensitivity"));
			assertEquals(true, template.getDosboxVersion().getConfiguration().hasValue("dosbox", "captures"));
			assertEquals(false, template.getDosboxVersion().getConfiguration().hasValue("capture", "capture_dir"));
			assertEquals("desktop", template.getCombinedConfiguration().getValue("sdl", "fullresolution"));
			assertEquals("higher,normal", template.getCombinedConfiguration().getValue("sdl", "priority"));
			assertEquals("false", template.getCombinedConfiguration().getValue("render", "aspect"));
			if (cycles == null)
				assertEquals("auto", template.getCombinedConfiguration().getValue("cpu", "cycles"));
			else
				assertEquals(cycles, template.getCombinedConfiguration().getValue("cpu", "cycles"));
		}
	}

	@Test
	public void test030_LoadProfilesAndCheckConfiguration() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);

		assertEquals(PROFILES_CREATED, allProfiles.size());

		Profile profile0 = allProfiles.get(0);
		String loadProfileWarningsLog = profile0.resetAndLoadConfiguration();
		assertEquals(text.get("general.error.profileincomplete", new String[] {profile0.getConfigurationCanonicalFile().getPath()}), loadProfileWarningsLog);

		File profile0ConfigurationFile = profile0.getConfigurationFile();
		assertEquals("profiles" + File.separator + "0.conf", profile0ConfigurationFile.getPath());
		File profile0CapturesFolder = profile0.getCapturesFile();
		assertEquals("captures" + File.separator + "0", profile0CapturesFolder.getPath());
		String fileToString0 = FileUtils.readFileToString(profile0.getConfigurationCanonicalFile(), StandardCharsets.UTF_8);
		String confString0 = profile0.getConfigurationString();
		assertEquals(fileToString0, confString0);
		assertEquals("[dosbox]" + SystemUtils.EOLN + "captures=.." + File.separator + "captures" + File.separator + "0" + SystemUtils.EOLN + SystemUtils.EOLN + "[autoexec]" + SystemUtils.EOLN + "exit"
				+ SystemUtils.EOLN,
			confString0);

		Profile profile1 = allProfiles.get(1);

		assertEquals("Profile 1 : PCjr", profile1.getTitle());
		assertEquals("Dev1", profile1.getDeveloper());
		assertEquals("Pub1", profile1.getPublisher());
		assertEquals("Gen1", profile1.getGenre());
		assertEquals("Yr1", profile1.getYear());
		assertEquals("St1", profile1.getStatus());
		assertEquals("Not1", profile1.getNotes());
		assertEquals(true, profile1.isFavorite());

		Link[] l = profile1.getLinks();
		assertEquals(Profile.NR_OF_LINK_TITLES, l.length);
		for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
			assertEquals("LnkTit" + i, l[i].getTitle());
			assertEquals("LnkDst" + i, l[i].getDestination());
		}

		assertArrayEquals(new String[] {"cust0", "cust1", "cust2", "cust3", "cust4", "cust5", "cust6", "cust7", "cust8", "cust9", "cust10", "cust11"}, profile1.getCustomStrings());
		assertArrayEquals(new int[] {1, 2}, profile1.getCustomInts());
		assertEquals("game1" + File.separator + "setup.exe", profile1.getSetupString());
		assertEquals("setupparams", profile1.getSetupParams());
		assertArrayEquals(new String[] {"game1" + File.separator + "altexe1.exe", "game1" + File.separator + "altexe2.exe"}, profile1.getAltExeStrings());
		assertArrayEquals(new String[] {"alt1params", "alt2params"}, profile1.getAltExeParams());

		String loadProfile1WarningsLog = profile1.resetAndLoadConfiguration();
		assertEquals(StringUtils.EMPTY, loadProfile1WarningsLog);

		File profile1ConfigurationFile = profile1.getConfigurationFile();
		assertEquals("profiles" + File.separator + "1.conf", profile1ConfigurationFile.getPath());
		File profile1CapturesFolder = profile1.getCapturesFile();
		assertEquals("captures" + File.separator + "1", profile1CapturesFolder.getPath());
		String fileToString1 = FileUtils.readFileToString(profile1.getConfigurationCanonicalFile(), StandardCharsets.UTF_8);
		String confString1 = profile1.getConfigurationString();
		assertEquals(fileToString1, confString1);
		assertEquals("[dosbox]" + SystemUtils.EOLN + "machine=pcjr" + SystemUtils.EOLN + "captures=.." + File.separator + "captures" + File.separator + "1" + SystemUtils.EOLN + SystemUtils.EOLN
				+ "[cpu]" + SystemUtils.EOLN + "cycles=120" + SystemUtils.EOLN + SystemUtils.EOLN + "[midi]" + SystemUtils.EOLN + "mpu401=none" + SystemUtils.EOLN + "mididevice=none"
				+ SystemUtils.EOLN + SystemUtils.EOLN + "[sblaster]" + SystemUtils.EOLN + "sbtype=none" + SystemUtils.EOLN + "sbmixer=false" + SystemUtils.EOLN + SystemUtils.EOLN + "[speaker]"
				+ SystemUtils.EOLN + "disney=false" + SystemUtils.EOLN + SystemUtils.EOLN + "[dos]" + SystemUtils.EOLN + "xms=false" + SystemUtils.EOLN + "ems=false" + SystemUtils.EOLN + "umb=false"
				+ SystemUtils.EOLN + SystemUtils.EOLN + "[autoexec]" + SystemUtils.EOLN + "mount C \".\"" + SystemUtils.EOLN + "boot C:\\game1\\disk.ima" + SystemUtils.EOLN + "exit"
				+ SystemUtils.EOLN,
			confString1);

		Profile profile2 = allProfiles.get(2);

		assertEquals("Profile 2 : using dosbox mount", profile2.getTitle());

		String loadProfile2WarningsLog = profile2.resetAndLoadConfiguration();
		assertEquals(StringUtils.EMPTY, loadProfile2WarningsLog);
		
		Profile profile3 = allProfiles.get(3);

		assertEquals("Profile 3 : using template features", profile3.getTitle());

		String loadProfile3WarningsLog = profile3.resetAndLoadConfiguration();
		assertEquals(StringUtils.EMPTY, loadProfile3WarningsLog);
		
		Profile profile4 = allProfiles.get(4);

		assertEquals("Profile 4 : using dosbox staging", profile4.getTitle());

		String loadProfile4WarningsLog = profile4.resetAndLoadConfiguration();
		assertEquals(StringUtils.EMPTY, loadProfile4WarningsLog);
	}

	@Test
	public void test031_DuplicateProfiles() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);

		assertEquals(PROFILES_CREATED, allProfiles.size());

		for (Profile profile: allProfiles) {
			String loadProfileWarningsLog = profile.resetAndLoadConfiguration();

			if (profile.getId() == 0)
				assertEquals(text.get("general.error.profileincomplete", new String[] {profile.getConfigurationCanonicalFile().getPath()}), loadProfileWarningsLog);
			else
				assertEquals(StringUtils.EMPTY, loadProfileWarningsLog);

			Profile duplicate = pRepo.duplicate(profile);
			assertTrue(duplicate.getId() >= PROFILES_CREATED);
			assertEquals(duplicate.getTitle(), profile.getTitle());
			File profileConfigurationFile = duplicate.getConfigurationCanonicalFile();
			String fileToString = FileUtils.readFileToString(profileConfigurationFile, StandardCharsets.UTF_8);
			String confString = duplicate.getConfigurationString();
			assertEquals(confString, fileToString);
			assertEquals(profile.getConfiguration().getAutoexec().toString(), duplicate.getConfiguration().getAutoexec().toString());
			assertNotEquals(profile.getCapturesStringForConfig(), duplicate.getCapturesStringForConfig());
			
			if (duplicate.getTitle().equals("Profile 4 : using dosbox staging"))
				assertEquals(duplicate.getCapturesStringForConfig(), duplicate.getCombinedConfiguration().getValue("capture", "capture_dir"));
			else
				assertEquals(duplicate.getCapturesStringForConfig(), duplicate.getCombinedConfiguration().getValue("dosbox", "captures"));
		}

		allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);
		assertEquals(PROFILES_CREATED * 2L, allProfiles.size());
	}

	@Test
	public void test032_ReloadDosboxVersionForProfiles() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);

		for (int i = 0; i < PROFILES_CREATED; i++) {
			Profile profile = allProfiles.get(i);
			String loadProfileWarningsLog = profile.resetAndLoadConfiguration();
			if (i % PROFILES_CREATED == 0)
				assertEquals("This profile (" + profile.getConfigurationCanonicalFile().getPath() + ") appears to be incomplete." + StringUtils.LF, loadProfileWarningsLog);
			else
				assertEquals(StringUtils.EMPTY, loadProfileWarningsLog);

			if (profile.getTitle().equals("Profile 4 : using dosbox staging")) {
				assertEquals("crt-auto", profile.getDosboxVersion().getConfiguration().getValue("render", "glshader"));
				assertEquals(false, profile.getConfiguration().hasValue("render", "glshader"));
				assertEquals("crt-auto", profile.getCombinedConfiguration().getValue("render", "glshader"));
			} else if (i % PROFILES_CREATED == 2) {
				assertEquals("none", profile.getDosboxVersion().getConfiguration().getValue("render", "scaler"));
				assertEquals(false, profile.getConfiguration().hasValue("render", "scaler"));
				assertEquals("none", profile.getCombinedConfiguration().getValue("render", "scaler"));
			} else {
				assertEquals("normal2x", profile.getDosboxVersion().getConfiguration().getValue("render", "scaler"));
				assertEquals(false, profile.getConfiguration().hasValue("render", "scaler"));
				assertEquals("normal2x", profile.getCombinedConfiguration().getValue("render", "scaler"));
			}

			if (profile.getTitle().equals("Profile 4 : using dosbox staging")) {
				profile.setValue("render", "glshader", "crt-auto-machine");
				assertEquals(true, profile.getConfiguration().hasValue("render", "glshader"));
				assertEquals("crt-auto", profile.getDosboxVersion().getConfiguration().getValue("render", "glshader"));
				assertEquals("crt-auto-machine", profile.getCombinedConfiguration().getValue("render", "glshader"));
			} else {
				profile.setValue("render", "scaler", "hq3x");
				
				assertEquals(true, profile.getConfiguration().hasValue("render", "scaler"));
				if (i % PROFILES_CREATED == 2) {
					assertEquals("none", profile.getDosboxVersion().getConfiguration().getValue("render", "scaler"));
				} else {
					assertEquals("normal2x", profile.getDosboxVersion().getConfiguration().getValue("render", "scaler"));
				}
				assertEquals("hq3x", profile.getCombinedConfiguration().getValue("render", "scaler"));
			}

			profile.reloadDosboxVersion(profile.getDosboxVersion());
			
			if (profile.getTitle().equals("Profile 4 : using dosbox staging")) {
				assertEquals(false, profile.getConfiguration().hasValue("render", "glshader"));
				assertEquals("crt-auto", profile.getDosboxVersion().getConfiguration().getValue("render", "glshader"));
				assertEquals("crt-auto", profile.getCombinedConfiguration().getValue("render", "glshader"));
			} else if (i % PROFILES_CREATED == 2) {
				assertEquals(false, profile.getConfiguration().hasValue("render", "scaler"));
				assertEquals("none", profile.getDosboxVersion().getConfiguration().getValue("render", "scaler"));
				assertEquals("none", profile.getCombinedConfiguration().getValue("render", "scaler"));
			} else {
				assertEquals(false, profile.getConfiguration().hasValue("render", "scaler"));
				assertEquals("normal2x", profile.getDosboxVersion().getConfiguration().getValue("render", "scaler"));
				assertEquals("normal2x", profile.getCombinedConfiguration().getValue("render", "scaler"));
			}
		}
	}

	@Test
	public void test033_SetOtherDosboxVersionForProfile() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);

		DosboxVersion db063 = allDosboxVersions.get(0);
		DosboxVersion db074 = allDosboxVersions.get(5); // list ordered by title
		DosboxVersion dbStaging = allDosboxVersions.get(14);

		for (int i = 0; i < PROFILES_CREATED; i++) {
			Profile profile = allProfiles.get(i);
			String loadProfileWarningsLog = profile.resetAndLoadConfiguration();
			if (i % PROFILES_CREATED == 0)
				assertEquals("This profile (" + profile.getConfigurationCanonicalFile().getPath() + ") appears to be incomplete." + StringUtils.LF, loadProfileWarningsLog);
			else
				assertEquals(StringUtils.EMPTY, loadProfileWarningsLog);

			assertEquals("svga_s3", profile.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", profile.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals(false, profile.getDosboxVersion().getConfiguration().getBooleanValue("gus", "gus"));
			assertEquals(true, profile.getCombinedConfiguration().hasValue("joystick", "joysticktype"));

			profile.setToDosboxVersion(db063);
			assertEquals("vga", profile.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("normal", profile.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals(false, profile.getCombinedConfiguration().getBooleanValue("gus", "gus"));
			assertEquals(false, profile.getCombinedConfiguration().hasValue("joystick", "joysticktype"));

			profile.reloadDosboxVersion(db074);
			assertEquals("svga_s3", profile.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", profile.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals(false, profile.getDosboxVersion().getConfiguration().getBooleanValue("gus", "gus"));
			assertEquals("svga_s3", profile.getCombinedConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", profile.getCombinedConfiguration().getValue("cpu", "core"));
			assertEquals(false, profile.getCombinedConfiguration().getBooleanValue("gus", "gus"));
			assertEquals(true, profile.getCombinedConfiguration().hasValue("joystick", "joysticktype"));
			
			profile.reloadDosboxVersion(dbStaging);
			assertEquals(true, profile.getConfiguration().toString().equals(StringUtils.EMPTY));
			assertEquals(true, profile.getCombinedConfiguration().hasValue("capture", "capture_dir"));
			
			profile.setToDosboxVersion(db074);
			assertEquals(false, profile.getCombinedConfiguration().hasValue("capture", "capture_dir"));
			assertEquals(true, profile.getCombinedConfiguration().hasValue("dosbox", "captures"));
			assertEquals("desktop", profile.getCombinedConfiguration().getValue("sdl", "fullresolution"));
		}
	}

	@Test
	public void test034_SwitchToOtherDosboxVersionForProfiles() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);

		DosboxVersion db063 = allDosboxVersions.get(0);
		DosboxVersion db074 = allDosboxVersions.get(5); // list ordered by title
		DosboxVersion dbStaging = allDosboxVersions.get(14);

		for (int i = 0; i < PROFILES_CREATED; i++) {
			Profile profile = allProfiles.get(i);
			String loadProfileWarningsLog = profile.resetAndLoadConfiguration();
			if (i % PROFILES_CREATED == 0) {
				assertEquals("This profile (" + profile.getConfigurationCanonicalFile().getPath() + ") appears to be incomplete." + StringUtils.LF, loadProfileWarningsLog);
			} else {
				assertEquals(StringUtils.EMPTY, loadProfileWarningsLog);

				assertEquals("svga_s3", profile.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
				assertEquals("auto", profile.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
				assertEquals(false, profile.getDosboxVersion().getConfiguration().getBooleanValue("gus", "gus"));
				assertEquals(true, profile.getCombinedConfiguration().hasValue("joystick", "joysticktype"));

				if (i % PROFILES_CREATED == 3) {
					assertEquals(true, profile.getConfiguration().hasValue("template", "myitem"));
				} else {
					assertEquals(true, profile.getConfiguration().hasValue("cpu", "cycles"));
				}
				String cycles = profile.getConfiguration().getValue("cpu", "cycles");
				String captures = profile.getTitle().equals("Profile 4 : using dosbox staging")
						? profile.getConfiguration().getValue("capture", "capture_dir")
						: profile.getConfiguration().getValue("dosbox", "captures");

				profile.switchToDosboxVersion(db063);
				assertEquals("vga", profile.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
				assertEquals("normal", profile.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
				assertEquals(true, profile.getCombinedConfiguration().getBooleanValue("gus", "gus"));
				assertEquals(false, profile.getCombinedConfiguration().hasValue("joystick", "joysticktype"));
				assertEquals(cycles, profile.getConfiguration().getValue("cpu", "cycles"));
				assertEquals(captures, profile.getConfiguration().getValue("dosbox", "captures"));

				profile.switchToDosboxVersion(db074);
				assertEquals("svga_s3", profile.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
				assertEquals("auto", profile.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
				assertEquals(false, profile.getDosboxVersion().getConfiguration().getBooleanValue("gus", "gus"));
				assertEquals(true, profile.getCombinedConfiguration().hasValue("joystick", "joysticktype"));
				assertEquals(false, profile.getCombinedConfiguration().getBooleanValue("gus", "gus"));
				assertEquals(cycles, profile.getConfiguration().getValue("cpu", "cycles"));
				assertEquals(captures, profile.getConfiguration().getValue("dosbox", "captures"));
				
				profile.switchToDosboxVersion(dbStaging);
				assertEquals(cycles, profile.getConfiguration().getValue("cpu", "cycles"));
				assertEquals(captures, profile.getConfiguration().getValue("capture", "capture_dir"));
			}
		}
	}

	@Test
	public void test035_ReloadTemplateForProfiles() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Template> allTemplates = tRepo.listAll(allDosboxVersions);
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);

		for (int i = 0; i < PROFILES_CREATED; i++) {
			Profile profile = allProfiles.get(i);
			profile.resetAndLoadConfiguration();

			int mounts = profile.getConfiguration().getAutoexec().getMountingpoints().size();

			if (profile.getTitle().equals("Profile 4 : using dosbox staging")) {
				profile.setValue("render", "glshader", "crt-auto-machine-arcade");
				assertEquals(true, profile.getConfiguration().hasValue("render", "glshader"));
			} else {
				profile.setValue("render", "scaler", "hq3x");
				assertEquals(true, profile.getConfiguration().hasValue("render", "scaler"));
			}
			
			for (int t = 0; t < TEMPLATES_CREATED; t++) {

				Template template = allTemplates.get(t);
				String loadTemplateWarningsLog = template.resetAndLoadConfiguration();
				assertEquals(StringUtils.EMPTY, loadTemplateWarningsLog);

				profile.reloadTemplate(profile.getDosboxVersion(), template);

				if (profile.getId() % PROFILES_CREATED == 2) {
					assertEquals(3, profile.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
				} else {
					assertEquals(0, profile.getDosboxVersion().getConfiguration().getAutoexec().getMountingpoints().size());
				}

				if (template.getTitle().equals("Custom template with 2 mounts")) {
					assertEquals("myvalue", profile.getConfiguration().getValue("template", "myitem"));
					assertEquals(false, profile.getConfiguration().getAutoexec().isExit());
					assertArrayEquals(new String[] {"start" + SystemUtils.EOLN, "before" + SystemUtils.EOLN, "after" + SystemUtils.EOLN, "end" + SystemUtils.EOLN},
						profile.getConfiguration().getAutoexec().getCustomSections());

					assertEquals(profile.getId() % PROFILES_CREATED == 0, profile.getConfiguration().getAutoexec().getBooterByDefault()); // was (un)changed because both main and img1 are unset /
																																			// already set
					assertEquals(profile.getId() % PROFILES_CREATED != 2 && profile.getId() % PROFILES_CREATED != 4, profile.getConfiguration().getAutoexec().isBooter());
					if (profile.getId() % PROFILES_CREATED == 0) {
						assertEquals(2, profile.getConfiguration().getAutoexec().getMountingpoints().size()); // was changed because custom mounts were not set
					} else if (profile.getId() % PROFILES_CREATED == 2) {
						assertEquals(0, profile.getConfiguration().getAutoexec().getMountingpoints().size()); // was unchanged because custom mounts were not set, only dosbox mounts
					} else {
						assertEquals(mounts, profile.getConfiguration().getAutoexec().getMountingpoints().size()); // was unchanged because custom mounts were already set
					}
					assertEquals(2, profile.getNativeCommands().size());
				} else if (template.getTitle().equals("Custom template using DOSBox Staging")) {
					assertEquals("true", profile.getConfiguration().getValue("sdl", "vsync"));
					assertEquals(true, profile.getCombinedConfiguration().hasValue("sdl", "vsync"));
				} else {
					assertEquals(true, profile.getConfiguration().getAutoexec().isExit());
					assertArrayEquals(new String[] {"", "", "", ""}, profile.getConfiguration().getAutoexec().getCustomSections());

					if ((profile.getId() % PROFILES_CREATED == 1) || (profile.getId() % PROFILES_CREATED == 4)) {
						assertEquals(1, profile.getConfiguration().getAutoexec().getMountingpoints().size());
					} else if (profile.getId() % PROFILES_CREATED == 3) {
						assertEquals(3, profile.getConfiguration().getAutoexec().getMountingpoints().size()); // 2 mounts in profile from template, 1 extra custom mount
					} else {
						assertEquals(0, profile.getConfiguration().getAutoexec().getMountingpoints().size());
					}
					assertEquals(1, profile.getNativeCommands().size());
				}

			}
		}
	}

	/**
	 * Warning signs with the tooltip "null": the check read configurations again while the window used the same DOSBox
	 * versions. Checks now use their own copies, and a failure while checking is never shown as a problem.
	 */
	@Test
	public void test036_ProfileWarningsWhileDosboxVersionsReload() throws Exception {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);
		java.util.Map<Integer, List<String>> expected = new java.util.HashMap<>();
		for (Profile p: allProfiles) {
			org.dbgl.util.ProfileWarnings.forget(p.getId());
			expected.put(p.getId(), org.dbgl.util.ProfileWarnings.check(p));
		}
		java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
		Thread window = new Thread(() -> {
			while (!stop.get()) {
				for (DosboxVersion dbv: allDosboxVersions) {
					try {
						dbv.resetAndLoadConfiguration();
						dbv.getConfigurationString();
					} catch (Exception e) {
						// only the checks matter here
					}
				}
			}
		});
		window.start();
		try {
			for (int round = 0; round < 15; round++) {
				for (Profile p: allProfiles) {
					org.dbgl.util.ProfileWarnings.forget(p.getId());
					List<String> problems = org.dbgl.util.ProfileWarnings.check(p);
					assertFalse(problems.contains("null"));
					assertEquals(expected.get(p.getId()), problems);
				}
			}
		} finally {
			stop.set(true);
			window.join();
		}
	}

	@Test
	public void test040_CombiningProfiles() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);

		StringBuilder warningsLog = new StringBuilder();

		Profile prof0 = allProfiles.get(0);
		warningsLog.append(prof0.resetAndLoadConfiguration());
		Profile prof1 = allProfiles.get(1);
		warningsLog.append(prof1.resetAndLoadConfiguration());
		Profile prof2 = allProfiles.get(5);
		warningsLog.append(prof2.resetAndLoadConfiguration());
		Profile prof3 = allProfiles.get(6);
		warningsLog.append(prof3.resetAndLoadConfiguration());

		Profile prof01 = ProfileFactory.combine(allDosboxVersions, Arrays.asList(prof0, prof1), warningsLog);
		Profile prof02 = ProfileFactory.combine(allDosboxVersions, Arrays.asList(prof0, prof2), warningsLog);
		Profile prof13 = ProfileFactory.combine(allDosboxVersions, Arrays.asList(prof1, prof3), warningsLog);
		Profile prof31 = ProfileFactory.combine(allDosboxVersions, Arrays.asList(prof3, prof1), warningsLog);

		assertEquals("auto", prof01.getConfiguration().getValue("cpu", "core"));
		assertFalse(prof01.getConfiguration().hasValue("cpu", "cycles"));

		assertEquals("auto", prof02.getConfiguration().getValue("cpu", "core"));
		assertTrue(prof02.getConfiguration().hasValue("cpu", "cycles"));

		assertEquals(prof13.getCombinedConfiguration().toString(null), prof31.getCombinedConfiguration().toString(null));

		assertEquals(prof1.getDosboxVersion().getConfigurationString(), prof13.getDosboxVersion().getConfigurationString());
		assertEquals(prof3.getDosboxVersion().getConfigurationString(), prof13.getDosboxVersion().getConfigurationString());

		assertEquals("This profile (" + prof0.getConfigurationCanonicalFile().getPath() + ") appears to be incomplete." + StringUtils.LF + "This profile ("
				+ prof2.getConfigurationCanonicalFile().getPath() + ") appears to be incomplete." + StringUtils.LF,
			warningsLog.toString());
	}
	
	@Test
	public void test041_CombiningProfiles() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);

		StringBuilder warningsLog = new StringBuilder();

		Profile prof0 = allProfiles.get(0);
		warningsLog.append(prof0.resetAndLoadConfiguration());
		Profile prof1 = allProfiles.get(1);
		warningsLog.append(prof1.resetAndLoadConfiguration());
		Profile prof2 = allProfiles.get(4); // dosbox staging
		warningsLog.append(prof2.resetAndLoadConfiguration());

		Profile prof012 = ProfileFactory.combine(allDosboxVersions, Arrays.asList(prof0, prof1, prof2), warningsLog);
		Profile prof021 = ProfileFactory.combine(allDosboxVersions, Arrays.asList(prof0, prof2, prof1), warningsLog);
		Profile prof120 = ProfileFactory.combine(allDosboxVersions, Arrays.asList(prof1, prof2, prof0), warningsLog);
		Profile prof102 = ProfileFactory.combine(allDosboxVersions, Arrays.asList(prof1, prof0, prof2), warningsLog);
		Profile prof201 = ProfileFactory.combine(allDosboxVersions, Arrays.asList(prof2, prof0, prof1), warningsLog);
		Profile prof210 = ProfileFactory.combine(allDosboxVersions, Arrays.asList(prof2, prof1, prof0), warningsLog);

		assertEquals(prof0.getDosboxVersion(), prof012.getDosboxVersion());
		assertEquals(prof0.getDosboxVersion(), prof021.getDosboxVersion());
		assertEquals(prof0.getDosboxVersion(), prof120.getDosboxVersion());
		assertEquals(prof0.getDosboxVersion(), prof102.getDosboxVersion());
		assertEquals(prof0.getDosboxVersion(), prof201.getDosboxVersion());
		assertEquals(prof0.getDosboxVersion(), prof210.getDosboxVersion());
		
		prof012.getConfiguration().setAutoexec(new Autoexec());
		prof021.getConfiguration().setAutoexec(new Autoexec());
		prof120.getConfiguration().setAutoexec(new Autoexec());
		prof102.getConfiguration().setAutoexec(new Autoexec());
		prof201.getConfiguration().setAutoexec(new Autoexec());
		prof210.getConfiguration().setAutoexec(new Autoexec());
		
		assertEquals(prof012.getConfiguration().toString(null), prof021.getConfiguration().toString(null));
		assertEquals(prof120.getConfiguration().toString(null), prof102.getConfiguration().toString(null));
		assertEquals(prof201.getConfiguration().toString(null), prof210.getConfiguration().toString(null));
		assertEquals(prof012.getConfiguration().toString(null), prof120.getConfiguration().toString(null));
		assertEquals(prof012.getConfiguration().toString(null), prof201.getConfiguration().toString(null));
		
		assertEquals(false, prof012.getConfiguration().hasValue("sdl", "output"));
		assertEquals(false, prof012.getConfiguration().hasValue("render", "aspect"));
		assertEquals(true, prof012.getConfiguration().hasValue("sdl", "windowresolution"));
		assertEquals(true, prof012.getConfiguration().hasValue("render", "scaler"));
	}

	@Test
	public void test050_ReloadOtherDosboxVersionForProfiles() throws SQLException, IOException {
		List<DosboxVersion> allDosboxVersions = dRepo.listAll();
		List<Profile> allProfiles = pRepo.list(StringUtils.EMPTY, StringUtils.EMPTY, allDosboxVersions);

		DosboxVersion db063 = allDosboxVersions.get(0);
		DosboxVersion db0742 = allDosboxVersions.get(6); // list ordered by title
		DosboxVersion dbStaging = allDosboxVersions.get(14);

		assertEquals(db0742, BaseRepository.findDefault(allDosboxVersions));

		for (int i = 0; i < PROFILES_CREATED; i++) {
			Profile profile = allProfiles.get(i);
			String loadProfileWarningsLog = profile.resetAndLoadConfiguration();
			if (i % PROFILES_CREATED == 0)
				assertEquals("This profile (" + profile.getConfigurationCanonicalFile().getPath() + ") appears to be incomplete." + StringUtils.LF, loadProfileWarningsLog);
			else
				assertEquals(StringUtils.EMPTY, loadProfileWarningsLog);

			assertEquals("svga_s3", profile.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", profile.getDosboxVersion().getConfiguration().getValue("cpu", "core"));

			profile.reloadDosboxVersion(db063);

			if (i % PROFILES_CREATED == 0)
				assertEquals("[autoexec]" + SystemUtils.EOLN + "exit" + SystemUtils.EOLN, profile.getConfigurationString());
			else if (i % PROFILES_CREATED == 1)
				assertEquals("[autoexec]" + SystemUtils.EOLN + "mount C \".\"" + SystemUtils.EOLN + "boot C:\\game1\\disk.ima" + SystemUtils.EOLN + "exit" + SystemUtils.EOLN,
					profile.getConfigurationString());
			else if (i % PROFILES_CREATED == 2)
				assertEquals("[autoexec]" + SystemUtils.EOLN + "exit" + SystemUtils.EOLN, profile.getConfigurationString());

			assertNull(profile.getCombinedConfiguration().getValue("dosbox", "catures"));
			assertEquals("vga", profile.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("normal", profile.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals("vga", profile.getCombinedConfiguration().getValue("dosbox", "machine"));
			assertEquals("normal", profile.getCombinedConfiguration().getValue("cpu", "core"));

			pRepo.update(profile);
			assertEquals(profile.getCapturesStringForConfig(), profile.getCombinedConfiguration().getValue("dosbox", "captures"));

			profile.reloadDosboxVersion(db0742);
			assertNull(profile.getCombinedConfiguration().getValue("dosbox", "catures"));
			assertEquals("svga_s3", profile.getDosboxVersion().getConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", profile.getDosboxVersion().getConfiguration().getValue("cpu", "core"));
			assertEquals("svga_s3", profile.getCombinedConfiguration().getValue("dosbox", "machine"));
			assertEquals("auto", profile.getCombinedConfiguration().getValue("cpu", "core"));

			pRepo.update(profile);
			assertEquals(profile.getCapturesStringForConfig(), profile.getCombinedConfiguration().getValue("dosbox", "captures"));
			
			profile.reloadDosboxVersion(dbStaging);
			assertNull(profile.getCombinedConfiguration().getValue("dosbox", "catures"));
			assertNull(profile.getCombinedConfiguration().getValue("capture", "cature_dir"));
			pRepo.update(profile);
			assertEquals(profile.getCapturesStringForConfig(), profile.getCombinedConfiguration().getValue("capture", "capture_dir"));
		}
	}
}
