/*
 *  Copyright (C) 2026  mrmercy82 (DBGL Extended Edition, written with the help of AI)
 *  Part of a modified version of DBGL, Copyright (C) 2006-2025  Ronald Blankendaal
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.dbgl.model.FileLocation;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;
import org.dbgl.model.factory.DosboxVersionFactory;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.service.GenericTextService;
import org.dbgl.service.SettingsService;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * DOSBox Staging 0.83.0 support. Needs no DOSBox installation: uses the configuration file written by DOSBox Staging 0.83.0 itself
 * ('config -writeconf') and a 0.82.2 configuration derived from the 0.82.2 sources, both in the data directory next to this class.
 */
public class Staging083Tests {

	private static final String DATA_DIR = System.getProperty("dbgl.testdata", "src/java/org/dbgl/test/data");

	private static Configuration staging083Conf_;
	private static Configuration staging082Conf_;

	@BeforeClass
	public static void loadDosboxConfigurations() throws IOException {
		staging083Conf_ = load("dosbox-staging-0.83.0.conf");
		staging082Conf_ = load("dosbox-staging-0.82.2-subset.conf");
	}

	private static Configuration load(String name) throws IOException {
		File file = new File(DATA_DIR, name);
		Configuration conf = new Configuration();
		conf.loadDataWithAutoexec(GenericTextService.getInstance(), Files.readString(file.toPath(), StandardCharsets.UTF_8), file, null);
		return conf;
	}

	private static GenerationAwareConfigurable configurable(String... sectionItemValueTriples) {
		Configuration conf = new Configuration();
		for (int i = 0; i < sectionItemValueTriples.length; i += 3)
			conf.setValue(sectionItemValueTriples[i], sectionItemValueTriples[i + 1], sectionItemValueTriples[i + 2]);
		GenerationAwareConfigurable gac = new GenerationAwareConfigurable();
		gac.setConfiguration(conf);
		return gac;
	}

	/** The same final steps TemplateProfileBase.alterToDosboxVersionGeneration performs after the generation conversions */
	private static void finishFor(Configuration profileConf, Configuration dosboxConf) {
		profileConf.removeValuesNotSetIn(dosboxConf);
		GenerationAwareConfigurable.alignBooleanNotation(profileConf, dosboxConf);
		profileConf.removeDuplicateValuesIn(dosboxConf);
	}

	private static void assertValue(Configuration conf, String section, String item, String expected) {
		assertTrue("[" + section + "] " + item + " should be set", conf.hasValue(section, item));
		assertEquals("[" + section + "] " + item, expected, conf.getValue(section, item));
	}

	private static void assertAbsent(Configuration conf, String section, String item) {
		assertFalse("[" + section + "] " + item + " should not be set, but is " + conf.getValue(section, item), conf.hasValue(section, item));
	}

	@Test
	public void testVersionIsKnown() {
		assertTrue(SettingsService.STAGING_DOSBOX_FAM.versions_.contains("0.83.0"));

		DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, "staging083", "", "", "staging083/dosbox.conf");
		DosboxVersion v082 = DosboxVersionFactory.create("DOSBox Staging 0.82.2", SettingsService.STAGING_DOSBOX_FAM.title_, "0.82.2", false, true, false, null, "staging082", "", "", "staging082/dosbox.conf");
		assertEquals(Generation.staging_0830, GenerationAwareConfigurable.determineGen(v083));
		assertEquals(Generation.staging_0820, GenerationAwareConfigurable.determineGen(v082));

		Generation[] stagingGens = GenerationAwareConfigurable.GENS[GenerationAwareConfigurable.familyIdx(Generation.staging_0830)];
		assertEquals("0.83 must be the newest Staging generation", Generation.staging_0830, stagingGens[stagingGens.length - 1]);
	}

	@Test
	public void testReadsOnOffBooleans() {
		assertValue(staging083Conf_, "dos", "xms", "on");
		assertTrue(staging083Conf_.getBooleanValue("dos", "xms"));
		assertFalse(staging083Conf_.getBooleanValue("gus", "gus"));
		assertTrue(staging082Conf_.getBooleanValue("dos", "xms")); // true/false notation keeps working
	}

	@Test
	public void testUpgradeFrom082() {
		GenerationAwareConfigurable gac = configurable(
			"sdl", "windowresolution", "1024x768",
			"sdl", "fullresolution", "desktop",
			"sdl", "transparency", "10",
			"sdl", "priority", "higher normal",
			"sdl", "waitonerror", "false",
			"sdl", "vsync", "adaptive",
			"sdl", "presentation_mode", "cfr",
			"sdl", "vsync_skip", "7000",
			"sdl", "host_rate", "60",
			"sdl", "fullscreen", "true",
			"render", "glshader", "sharp",
			"composite", "brightness", "60",
			"dosbox", "vga_render_per_scanline", "false",
			"dos", "keyboardlayout", "gr453",
			"dos", "file_locking", "false",
			"dos", "expand_shell_variable", "true",
			"dos", "ems", "false",
			"dos", "xms", "true",
			"dos", "umb", "false",
			"midi", "mididevice", "win32",
			"innovation", "sidmodel", "6581",
			"innovation", "6581filter", "70",
			"innovation", "8580filter", "30",
			"innovation", "sidclock", "c64pal",
			"innovation", "sidport", "280",
			"mouse", "dos_mouse_driver", "false",
			"mouse", "dos_mouse_immediate", "true",
			"gus", "gus", "true");

		gac.upgradeOneStagingGeneration(Generation.staging_0820);
		Configuration c = gac.getConfiguration();
		finishFor(c, staging083Conf_);

		assertValue(c, "sdl", "window_size", "1024x768");
		assertAbsent(c, "sdl", "fullscreen_mode"); // desktop -> standard, which is the default
		assertValue(c, "sdl", "window_transparency", "10");
		assertValue(c, "sdl", "presentation_mode", "host-rate"); // vsync_skip replacement wins over cfr -> dos-rate
		assertAbsent(c, "sdl", "vsync"); // off, the 0.83 default
		assertValue(c, "sdl", "fullscreen", "on");
		for (String gone: new String[] {"windowresolution", "fullresolution", "transparency", "priority", "waitonerror", "vsync_skip", "host_rate"})
			assertAbsent(c, "sdl", gone);

		assertValue(c, "render", "shader", "sharp");
		assertAbsent(c, "render", "glshader");
		assertAbsent(c, "composite", "brightness");
		assertAbsent(c, "dosbox", "vga_render_per_scanline");

		assertValue(c, "dos", "keyboard_layout", "gr453");
		assertValue(c, "dos", "file_locking", "off");
		assertValue(c, "dos", "expand_shell_variable", "on");
		assertValue(c, "dos", "ems", "off");
		assertAbsent(c, "dos", "xms"); // equal to the default
		assertValue(c, "dos", "umb", "off");

		assertAbsent(c, "midi", "mididevice"); // win32 -> port, which is the default

		assertValue(c, "innovation", "innovation", "on");
		assertValue(c, "innovation", "innovation_sid_filter", "70");
		for (String gone: new String[] {"sidmodel", "6581filter", "8580filter", "sidclock", "sidport"})
			assertAbsent(c, "innovation", gone);

		assertValue(c, "mouse", "builtin_dos_mouse_driver", "off");
		assertValue(c, "mouse", "builtin_dos_mouse_driver_options", "immediate");
		assertAbsent(c, "mouse", "dos_mouse_driver");
		assertAbsent(c, "mouse", "dos_mouse_immediate");

		assertValue(c, "gus", "gus", "on");
	}

	@Test
	public void testUpgradeMapsVsyncAndPresentationMode() {
		GenerationAwareConfigurable gac = configurable("sdl", "vsync", "yield", "sdl", "presentation_mode", "vfr", "sdl", "vsync_skip", "0");
		gac.upgradeOneStagingGeneration(Generation.staging_0820);
		Configuration c = gac.getConfiguration();
		assertValue(c, "sdl", "vsync", "off");
		assertValue(c, "sdl", "presentation_mode", "host-rate");
		assertAbsent(c, "sdl", "vsync_skip");

		gac = configurable("sdl", "vsync", "auto", "midi", "mididevice", "fluidsynth", "innovation", "sidmodel", "none");
		gac.upgradeOneStagingGeneration(Generation.staging_0820);
		c = gac.getConfiguration();
		assertAbsent(c, "sdl", "vsync");
		assertValue(c, "midi", "mididevice", "fluidsynth");
		assertValue(c, "innovation", "innovation", "off");
	}

	@Test
	public void testDowngradeTo082() {
		GenerationAwareConfigurable gac = configurable(
			"sdl", "window_size", "800x600",
			"sdl", "fullscreen_mode", "forced-borderless",
			"sdl", "window_transparency", "20",
			"sdl", "vsync", "fullscreen-only",
			"sdl", "presentation_mode", "host-rate",
			"sdl", "fullscreen", "on",
			"render", "shader", "bilinear",
			"render", "crt_color_profile", "philips",
			"dos", "keyboard_layout", "de129",
			"dos", "file_locking", "auto",
			"dos", "expand_shell_variable", "off",
			"dos", "ems", "emsboard",
			"dos", "locale_period", "native",
			"dos", "umb", "off",
			"midi", "mididevice", "soundcanvas",
			"innovation", "innovation", "on",
			"innovation", "innovation_sid_filter", "40",
			"mouse", "builtin_dos_mouse_driver", "no-tsr",
			"mouse", "builtin_dos_mouse_driver_options", "immediate modern",
			"soundcanvas", "soundcanvas_model", "sc55");

		gac.downgradeOneStagingGeneration(Generation.staging_0830);
		Configuration c = gac.getConfiguration();
		finishFor(c, staging082Conf_);

		assertValue(c, "sdl", "windowresolution", "800x600");
		assertAbsent(c, "sdl", "fullresolution"); // desktop, the 0.82 default
		assertValue(c, "sdl", "transparency", "20");
		assertAbsent(c, "sdl", "vsync"); // auto, the 0.82 default
		assertValue(c, "sdl", "presentation_mode", "vfr");
		assertValue(c, "sdl", "fullscreen", "true");

		assertValue(c, "render", "glshader", "bilinear");
		assertAbsent(c, "render", "shader");
		assertAbsent(c, "render", "crt_color_profile");

		assertValue(c, "dos", "keyboardlayout", "de129");
		assertAbsent(c, "dos", "file_locking");
		assertValue(c, "dos", "expand_shell_variable", "false");
		assertValue(c, "dos", "ems", "emsboard");
		assertAbsent(c, "dos", "locale_period"); // native doesn't exist in 0.82
		assertValue(c, "dos", "umb", "false");

		assertAbsent(c, "midi", "mididevice");

		assertValue(c, "innovation", "sidmodel", "6581");
		assertValue(c, "innovation", "6581filter", "40");

		assertAbsent(c, "mouse", "dos_mouse_driver"); // true, the 0.82 default
		assertValue(c, "mouse", "dos_mouse_immediate", "true");
		assertAbsent(c, "soundcanvas", "soundcanvas_model");
	}

	@Test
	public void testRoundTripKeepsValues() {
		GenerationAwareConfigurable gac = configurable(
			"sdl", "windowresolution", "large",
			"sdl", "presentation_mode", "cfr",
			"render", "glshader", "crt-auto-arcade",
			"dos", "keyboardlayout", "uk166",
			"midi", "mididevice", "mt32",
			"innovation", "sidmodel", "auto",
			"innovation", "6581filter", "55");
		gac.upgradeOneStagingGeneration(Generation.staging_0820);
		gac.downgradeOneStagingGeneration(Generation.staging_0830);
		Configuration c = gac.getConfiguration();
		assertValue(c, "sdl", "windowresolution", "large");
		assertValue(c, "sdl", "presentation_mode", "cfr");
		assertValue(c, "render", "glshader", "crt-auto-arcade");
		assertValue(c, "dos", "keyboardlayout", "uk166");
		assertValue(c, "midi", "mididevice", "mt32");
		assertValue(c, "innovation", "sidmodel", "6581");
		assertValue(c, "innovation", "6581filter", "55");
	}

	@Test
	public void testSwitchFromOfficialToStaging083() {
		GenerationAwareConfigurable gac = configurable(
			"dosbox", "captures", "mycaptures",
			"sdl", "priority", "higher,normal",
			"sdl", "fullresolution", "desktop",
			"sdl", "windowresolution", "original",
			"cpu", "cycles", "max",
			"cpu", "cputype", "386",
			"midi", "mididevice", "default",
			"dos", "keyboardlayout", "gr453",
			"dos", "xms", "false");
		gac.switchFamily(Generation.official_073, Generation.staging_0830);
		Configuration c = gac.getConfiguration();
		finishFor(c, staging083Conf_);

		assertValue(c, "capture", "capture_dir", "mycaptures");
		assertAbsent(c, "sdl", "priority");
		assertAbsent(c, "sdl", "fullresolution");
		assertAbsent(c, "sdl", "fullscreen_mode"); // standard is the default
		assertAbsent(c, "sdl", "window_size"); // 'original' doesn't exist in Staging
		assertValue(c, "cpu", "cpu_cycles", "max");
		assertAbsent(c, "cpu", "cycles");
		assertValue(c, "cpu", "cputype", "386_fast");
		assertAbsent(c, "midi", "mididevice");
		assertValue(c, "dos", "keyboard_layout", "gr453");
		assertValue(c, "dos", "xms", "off");
	}

	@Test
	public void testSwitchFromStaging083ToOfficial() {
		GenerationAwareConfigurable gac = configurable(
			"capture", "capture_dir", "mycaptures",
			"sdl", "window_size", "640x480",
			"sdl", "pause_when_inactive", "on",
			"midi", "mididevice", "port",
			"dos", "keyboard_layout", "gr453",
			"cpu", "cpu_cycles", "20000",
			"cpu", "cpu_cycles_protected", "auto");
		gac.switchFamily(Generation.staging_0830, Generation.official_073);
		Configuration c = gac.getConfiguration();

		assertValue(c, "dosbox", "captures", "mycaptures");
		assertValue(c, "sdl", "windowresolution", "640x480");
		assertValue(c, "sdl", "priority", "higher,pause");
		assertAbsent(c, "midi", "mididevice"); // port -> auto, which official DOSBox doesn't have
		assertValue(c, "dos", "keyboardlayout", "gr453");
		assertValue(c, "cpu", "cycles", "20000");
	}

	@Test
	public void testAlignBooleanNotation() {
		Configuration profile = new Configuration();
		profile.setValue("dos", "xms", "true");
		profile.setValue("dos", "umb", "0");
		profile.setValue("dos", "ems", "emsboard");
		profile.setValue("cpu", "cpu_cycles", "1"); // not a boolean setting, must stay untouched
		GenerationAwareConfigurable.alignBooleanNotation(profile, staging083Conf_);
		assertValue(profile, "dos", "xms", "on");
		assertValue(profile, "dos", "umb", "off");
		assertValue(profile, "dos", "ems", "emsboard");
		assertValue(profile, "cpu", "cpu_cycles", "1");

		Configuration back = new Configuration();
		back.setValue("dos", "xms", "on");
		GenerationAwareConfigurable.alignBooleanNotation(back, staging082Conf_);
		assertValue(back, "dos", "xms", "true");
	}

	@Test
	public void testEmptyValuesAreLeftOutWhenSaving() throws IOException {
		DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, "staging083", "", "", "staging083/dosbox.conf");
		File dosboxConfFile = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
		v083.loadConfigurationData(GenericTextService.getInstance(), Files.readString(dosboxConfFile.toPath(), StandardCharsets.UTF_8), dosboxConfFile);

		Profile profile = ProfileFactory.create(v083, null);
		Configuration c = profile.getConfiguration();
		// profile as left behind by editing with a mismatching DOSBox version
		c.setValue("dos", "locale_period", "");
		c.setValue("dos", "file_locking", "");
		c.setValue("dos", "xms", "off");
		c.setValue("dos", "ems", "off");
		c.setValue("sdl", "fullscreen_mode", "");
		c.setValue("speaker", "ps1audio", "");
		c.setValue("cpu", "cpu_cycles", "40000");
		c.setValue("mouse", "builtin_dos_mouse_driver_options", ""); // empty is the DOSBox default here, may stay

		File saved = File.createTempFile("dbgl-staging083-", ".conf");
		saved.deleteOnExit();
		c.setFileLocation(new FileLocation(saved.getPath()));
		profile.saveConfiguration();
		String written = Files.readString(saved.toPath(), StandardCharsets.UTF_8);

		for (String gone: new String[] {"locale_period=", "file_locking=", "fullscreen_mode=", "ps1audio=", "[speaker]"})
			assertFalse(gone + " should have been left out:\n" + written, written.contains(gone));
		for (String kept: new String[] {"xms=off", "ems=off", "cpu_cycles=40000", "builtin_dos_mouse_driver_options="})
			assertTrue(kept + " should have been kept:\n" + written, written.contains(kept));
	}
}
