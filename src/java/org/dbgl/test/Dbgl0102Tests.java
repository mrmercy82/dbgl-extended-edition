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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.dbgl.gui.dialog.SwitchDosboxVersionDialog;
import org.dbgl.model.conf.ConfValueOptions;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.conf.DosboxShortNames;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;
import org.dbgl.model.factory.DosboxVersionFactory;
import org.dbgl.service.GenericTextService;
import org.dbgl.service.SettingsService;
import org.dbgl.util.DosboxVersionDetector;
import org.junit.Test;


/**
 * DBGL 0.102: documented setting values, version detection, version to generation mapping, switch report.
 */
public class Dbgl0102Tests {

	private static final String DATA_DIR = System.getProperty("dbgl.testdata", "src/java/org/dbgl/test/data");
	private static final String OFFICIAL_CONF = System.getProperty("dbgl.officialconf", "src/dist/winshared/DOSBox-0.74-3/dosbox.conf");

	private static ConfValueOptions parse(File file) throws IOException {
		return ConfValueOptions.parse(Files.readString(file.toPath(), StandardCharsets.ISO_8859_1));
	}

	private static void assertValues(ConfValueOptions o, String section, String item, boolean freeForm, String... expected) {
		ConfValueOptions.Options v = o.get(section, item);
		assertTrue("[" + section + "] " + item + " should be documented", v != null);
		assertEquals("[" + section + "] " + item, Arrays.asList(expected), v.getValues());
		assertEquals("[" + section + "] " + item + " free form", freeForm, v.isFreeForm());
	}

	@Test
	public void testDocumentedValuesStaging083() throws IOException {
		ConfValueOptions o = parse(new File(DATA_DIR, "dosbox-staging-0.83.0.conf"));
		assertValues(o, "sdl", "vsync", false, "off", "on", "fullscreen-only");
		assertValues(o, "sdl", "presentation_mode", false, "auto", "dos-rate", "host-rate");
		assertValues(o, "dos", "locale_period", false, "historic", "modern", "native");
		assertValues(o, "sblaster", "oplmode", false, "auto", "opl2", "dualopl2", "opl3", "opl3gold", "esfm", "none", "off"); // "none/off"
		assertValues(o, "sdl", "window_size", true, "default"); // WxH and "small, medium, large (s, m, l)" are placeholders
		assertValues(o, "cpu", "cpu_cycles", true, "max");
		assertNull("not documented as a list", o.get("dos", "ems"));
		assertNull(o.get("nosuchsection", "nosuchitem"));
	}

	@Test
	public void testDocumentedValuesOfficialFormat() throws IOException {
		ConfValueOptions o = parse(new File(OFFICIAL_CONF));
		assertValues(o, "sdl", "output", false, "surface", "overlay", "opengl", "openglnb", "ddraw");
		assertValues(o, "cpu", "core", false, "auto", "dynamic", "normal", "simple");
		assertValues(o, "cpu", "cputype", false, "auto", "386", "386_slow", "486_slow", "pentium_slow", "386_prefetch");
		assertNull("cycleup documents no values", o.get("cpu", "cycleup"));
	}

	@Test
	public void testDocumentedValuesOldFormatWithNestedHelp() {
		// DOSBox Staging up to 0.82 and DOSBox-X: help texts may contain "name:" lines of their own, which are not settings
		String conf = String.join("\n",
			"[sdl]",
			"#              output: Rendering backend to use for graphics output ('opengl' by default).",
			"#                        opengl:     OpenGL backend with shader support (default).",
			"#                        texture:    SDL's texture backend with bilinear interpolation.",
			"#                      Possible values: opengl, texture, texturenb.",
			"#",
			"#    texture_renderer: Render driver to use in 'texture' output mode ('auto' by default).",
			"#                      Possible values: auto, opengl, opengles2, software.",
			"#",
			"#    windowresolution: Set intial window size for windowed mode.",
			"#                        WxH:       Specify window size in WxH format.",
			"",
			"output           = opengl",
			"texture_renderer = auto",
			"windowresolution = default",
			"[cpu]",
			"# core: CPU Core used in emulation.",
			"#       Possible values: auto, dynamic, normal, simple.",
			"core=auto");
		ConfValueOptions o = ConfValueOptions.parse(conf);
		assertValues(o, "sdl", "output", false, "opengl", "texture", "texturenb");
		assertValues(o, "sdl", "texture_renderer", false, "auto", "opengl", "opengles2", "software");
		assertNull(o.get("sdl", "windowresolution"));
		assertNull("not a setting, part of a help text", o.get("sdl", "opengl"));
		assertValues(o, "cpu", "core", false, "auto", "dynamic", "normal", "simple");
	}

	@Test
	public void testDocumentedValuesStaging082() throws IOException {
		File conf = new File(DATA_DIR, "dosbox-staging-0.82.2.conf");
		ConfValueOptions o = parse(conf);
		assertValues(o, "sdl", "output", false, "opengl", "texture", "texturenb");
		assertValues(o, "sdl", "texture_renderer", false, "auto", "opengl", "opengles2", "software");
		// every documented list contains the default DOSBox writes, otherwise the dialog would offer wrong values
		Configuration c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), Files.readString(conf.toPath(), StandardCharsets.UTF_8), conf, null);
		for (String section: c.getSectionNames())
			for (String item: c.getItemNames(section)) {
				ConfValueOptions.Options v = o.get(section, item);
				if ((v != null) && !v.isFreeForm() && !item.equals("priority")) // priority holds two values
					assertTrue("[" + section + "] " + item + "=" + c.getValue(section, item) + " in " + v.getValues(), v.getValues().contains(c.getValue(section, item).toLowerCase()));
			}
	}

	@Test
	public void testVersionDetectionParsing() {
		DosboxVersionDetector.Result r = DosboxVersionDetector.parse("dosbox-staging, version 0.83.0 (7b400)\n\nCopyright (C) 2020-2026 The DOSBox Staging Team");
		assertEquals(SettingsService.STAGING_DOSBOX_FAM.title_, r.family_);
		assertEquals("0.83.0", r.version_);
		r = DosboxVersionDetector.parse("DOSBox-X version 2026.10.01 SDL2, 64-bit");
		assertEquals(SettingsService.DOSBOX_X_DOSBOX_FAM.title_, r.family_);
		assertEquals("2026.10.01", r.version_);
		r = DosboxVersionDetector.parse("DOSBox version 0.74-3, copyright 2002-2019 DOSBox Team.");
		assertEquals(SettingsService.OFFICIAL_DOSBOX_FAM.title_, r.family_);
		assertEquals("0.74-3", r.version_);
		r = DosboxVersionDetector.parse("DOSBox version SVN, copyright 2002-2021 DOSBox Team.");
		assertEquals(SettingsService.SVN_DOSBOX_FAM.title_, r.family_);
		assertEquals("r4483", r.version_);
		r = DosboxVersionDetector.parse("DOSBox version 0.74-SVN-r4483, copyright 2002-2021 DOSBox Team.");
		assertEquals(SettingsService.SVN_DOSBOX_FAM.title_, r.family_);
		r = DosboxVersionDetector.parse("DOSBox ECE version r4482, copyright 2002-2021 DOSBox Team.");
		assertEquals(SettingsService.ECE_DOSBOX_FAM.title_, r.family_);
		assertEquals("r4482", r.version_);
		r = DosboxVersionDetector.parse("DOSBox version SVN-Daum, copyright 2002-2015 DOSBox Team.");
		assertEquals(SettingsService.DAUM_DOSBOX_FAM.title_, r.family_);
		assertEquals("2015.01.25", r.version_);
		assertNull(DosboxVersionDetector.parse("something else"));
		assertTrue(DosboxVersionDetector.isStaging083OrNewer("0.83.0"));
		assertTrue(DosboxVersionDetector.isStaging083OrNewer("0.84.1"));
		assertTrue(DosboxVersionDetector.isStaging083OrNewer("1.0.0"));
		assertFalse(DosboxVersionDetector.isStaging083OrNewer("0.82.2"));
		assertFalse(DosboxVersionDetector.isStaging083OrNewer(null));
		assertNull(DosboxVersionDetector.parse(null));
	}

	private static Generation gen(String family, String version) {
		return GenerationAwareConfigurable.determineGen(DosboxVersionFactory.create("t", family, version, false, false, false, null, "x", "", "", "x/dosbox.conf"));
	}

	@Test
	public void testUnlistedVersionsUseNearestGeneration() {
		String staging = SettingsService.STAGING_DOSBOX_FAM.title_;
		String x = SettingsService.DOSBOX_X_DOSBOX_FAM.title_;
		assertEquals(Generation.staging_0830, gen(staging, "0.83.1"));
		assertEquals(Generation.staging_0830, gen(staging, "0.84.0"));
		assertEquals(Generation.staging_0820, gen(staging, "0.82.3"));
		assertEquals(Generation.staging_0810, gen(staging, "0.80.1"));
		assertEquals(Generation.x_20241001, gen(x, "2026.10.01"));
		assertEquals(Generation.x_20241001, gen(x, "2027.01.01"));
		assertEquals(Generation.x_20240301, gen(x, "2024.05.01"));
		assertEquals(Generation.x_08220, gen(x, "2023.09.01"));
		assertTrue(SettingsService.DOSBOX_X_DOSBOX_FAM.versions_.contains("2026.10.01"));
	}

	@Test
	public void testDosboxShortNames() throws IOException {
		assertTrue(DosboxShortNames.needsShortName("M&M 3"));
		assertTrue(DosboxShortNames.needsShortName("MM 3.EXE"));
		assertTrue(DosboxShortNames.needsShortName("LONGFILENAME.EXE"));
		assertTrue(DosboxShortNames.needsShortName("GAME.EXE1"));
		assertFalse(DosboxShortNames.needsShortName("M&M"));
		assertFalse(DosboxShortNames.needsShortName("mm3.exe"));

		File root = Files.createTempDirectory("dbgl short names").toFile();
		try {
			File game = new File(root, "M & M" + File.separator + "M&M 3");
			assertTrue(game.mkdirs());
			File exe = new File(game, "MM 3.EXE");
			assertTrue(exe.createNewFile());
			assertTrue(new File(game, "Setup Program.exe").createNewFile());

			List<String> dos = DosboxShortNames.toDosPath(root, new File("M & M" + File.separator + "M&M 3" + File.separator + "MM 3.EXE"));
			assertEquals(Arrays.asList("M&M~1", "M&M3~1", "MM3~1.EXE"), dos);
			assertEquals("SETUPP~1.EXE", DosboxShortNames.toDosName(game, "Setup Program.exe"));
			assertEquals("plain.exe", DosboxShortNames.toDosName(game, "plain.exe")); // valid 8.3 names stay as they are

			// and back, as when reading an autoexec
			assertEquals(exe.getCanonicalFile(), DosboxShortNames.toHostPath(root, "M&M~1\\m&m3~1\\MM3~1.EXE").getCanonicalFile());
			assertEquals(exe.getCanonicalFile(), DosboxShortNames.toHostPath(root, "M & M/M&M 3/MM 3.EXE").getCanonicalFile());
		} finally {
			org.apache.commons.io.FileUtils.deleteDirectory(root);
		}
	}

	private static File unzipToTemp(String name) throws IOException {
		File result = File.createTempFile("dbgl", ".hdi");
		result.deleteOnExit();
		try (java.io.InputStream in = new java.util.zip.GZIPInputStream(new java.io.FileInputStream(new File(DATA_DIR, name)))) {
			Files.copy(in, result.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		return result;
	}

	@Test
	public void testPc98HdiImages() throws IOException {
		// built with make_pc98_hdi.py: 4096 byte header, IPL1 partition table, FAT12 with 1024 bytes per sector
		for (String[] t: new String[][] {{"pc98-512.hdi.gz", "512", "139264"}, {"pc98-256.hdi.gz", "256", "72960"}}) {
			File hdi = unzipToTemp(t[0]);
			org.dbgl.util.fat.FileDisk disk = new org.dbgl.util.fat.FileDisk(hdi);
			org.dbgl.util.fat.Pc98HdiImage.Info info = org.dbgl.util.fat.Pc98HdiImage.readInfo(disk);
			disk.close();
			assertEquals(4096, info.base());
			assertEquals(Integer.parseInt(t[1]), info.sectorSize());
			assertTrue(info.ipl1());
			assertEquals(1, info.partitions().size());
			assertEquals("MS-DOS", org.dbgl.util.fat.Pc98HdiImage.chooseFatPartition(info).name());
			assertEquals(Long.parseLong(t[2]), org.dbgl.util.fat.Pc98HdiImage.chooseFatPartition(info).byteOffset());
			assertEquals(Arrays.asList("AUTOEXEC.BAT", "GAME/GAME.EXE", "GAME/SETUP.COM", "GAME/DATA/INSTALL.EXE"),
				Arrays.stream(org.dbgl.util.FilesUtils.listExecutablesInZipOrIsoOrFat(hdi.getPath())).map(x -> x.replace(File.separatorChar, '/')).toList());
		}
	}

	@Test
	public void testSwitchReportDiff() {
		String before = "[sdl]\nwindowresolution=1024x768\nfullscreen=true\n[dos]\nxms=false\n";
		String after = "[sdl]\nfullscreen=true\nwindow_size=1024x768\n[dos]\nxms=off\n";
		List<String> diff = SwitchDosboxVersionDialog.diff(before, after);
		assertEquals(Arrays.asList("- [sdl] windowresolution=1024x768", "- [dos] xms=false", "+ [sdl] window_size=1024x768", "+ [dos] xms=off"), diff);
		assertFalse(diff.stream().anyMatch(l -> l.contains("fullscreen")));
		assertTrue(SwitchDosboxVersionDialog.diff(before, before).isEmpty());
	}
}
