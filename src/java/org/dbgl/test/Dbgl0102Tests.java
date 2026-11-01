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
		File result = File.createTempFile("dbgl", name.substring(name.lastIndexOf('.', name.length() - 4), name.length() - 3));
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
	@Test
	public void testPc98OtherImageFormats() throws IOException {
		// built with make_pc98_floppy.py: .NHD (from an .HDI), .FDI and .D88 floppies with 1024 byte sectors
		File nhd = unzipToTemp("pc98-512.nhd.gz");
		org.dbgl.util.fat.FileDisk disk = new org.dbgl.util.fat.FileDisk(nhd);
		org.dbgl.util.fat.Pc98HdiImage.Info info = org.dbgl.util.fat.Pc98HdiImage.readInfo(disk);
		disk.close();
		assertEquals(512, info.base());
		assertEquals(512, info.sectorSize());
		assertTrue(info.ipl1());
		assertTrue(org.dbgl.util.FilesUtils.isPc98HdImage(nhd.getPath()));
		assertTrue(org.dbgl.util.FilesUtils.listExecutablesInZipOrIsoOrFat(nhd.getPath()).length > 0);

		for (String name: new String[] {"pc98-floppy.fdi.gz", "pc98-floppy.d88.gz"}) {
			File floppy = unzipToTemp(name);
			assertTrue(name, org.dbgl.util.FilesUtils.isPc98Image(floppy.getPath()));
			assertFalse(name, org.dbgl.util.FilesUtils.isPc98HdImage(floppy.getPath()));
			assertEquals(name, Arrays.asList("GAME.EXE", "UTIL/SETUP.COM"),
				Arrays.stream(org.dbgl.util.FilesUtils.listExecutablesInZipOrIsoOrFat(floppy.getPath())).map(x -> x.replace(File.separatorChar, '/')).toList());
		}
	}

	@Test
	public void testDosboxXExecutableScan() throws IOException {
		File exe = File.createTempFile("dbgl-dosbox-x-", ".exe");
		exe.deleteOnExit();
		Files.write(exe.toPath(), ("\0\0MZ...DOSBox-X\0built 2024.03.01\0" + "DOSBox-X 2026.05.02\0OS-Free build\0").getBytes(StandardCharsets.ISO_8859_1));
		DosboxVersionDetector.Result r = DosboxVersionDetector.scanDosboxXExecutable(exe);
		assertEquals(SettingsService.DOSBOX_X_DOSBOX_FAM.title_, r.family_);
		assertEquals("2026.05.02", r.version_);
		assertEquals(java.util.EnumSet.of(DosboxVersionDetector.XBuild.OSFREE), DosboxVersionDetector.dosboxXBuild(exe));

		Files.write(exe.toPath(), "DOSBox Staging 0.83.0".getBytes(StandardCharsets.ISO_8859_1));
		assertNull(DosboxVersionDetector.scanDosboxXExecutable(exe));
		assertTrue(DosboxVersionDetector.dosboxXBuild(exe).isEmpty());
	}

	@Test
	public void testInvalidValues() throws IOException {
		org.dbgl.model.aggregate.DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null,
			"staging083", "", "", "staging083/dosbox.conf");
		File dosboxConfFile = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
		v083.loadConfigurationData(GenericTextService.getInstance(), Files.readString(dosboxConfFile.toPath(), StandardCharsets.UTF_8), dosboxConfFile);
		org.dbgl.model.aggregate.Profile profile = org.dbgl.model.factory.ProfileFactory.create(v083, null);
		Configuration c = profile.getConfiguration();
		c.setValue("dos", "xms", "on");
		c.setValue("dos", "ems", "true");
		c.setValue("sdl", "output", "overlay");
		c.setValue("cpu", "cpu_cycles", "40000");
		List<String[]> invalid = profile.listInvalidValues();
		assertEquals(1, invalid.size());
		assertEquals("output", invalid.get(0)[1]);
		assertEquals("overlay", invalid.get(0)[2]);
	}
	@Test
	public void testSettingPlacement() {
		String[][] cases = {
			{"sblaster", "adlib pcm boost", org.dbgl.gui.abstractdialog.SettingPlacement.SOUNDBLASTER},
			{"sblaster2", "adlib pcm boost", org.dbgl.gui.abstractdialog.SettingPlacement.SOUNDBLASTER2},
			{"emu8k", "rompath", org.dbgl.gui.abstractdialog.SettingPlacement.SOUNDBLASTER},
			{"dosbox", "acpi", org.dbgl.gui.abstractdialog.SettingPlacement.MAINBOARD},
			{"dosbox", "memsizekb", org.dbgl.gui.abstractdialog.SettingPlacement.MEMORY},
			{"dosbox", "keyboard hook", org.dbgl.gui.abstractdialog.SettingPlacement.KEYBOARD},
			{"dosbox", "video debug at startup", org.dbgl.gui.abstractdialog.SettingPlacement.LOGGING},
			{"dos", "int33 max x", org.dbgl.gui.abstractdialog.SettingPlacement.MOUSE},
			{"dos", "ems frame", org.dbgl.gui.abstractdialog.SettingPlacement.MEMORY},
			{"dos", "drive z hide files", org.dbgl.gui.abstractdialog.SettingPlacement.MOUNTING},
			{"dos", "badcommandhandler", org.dbgl.gui.abstractdialog.SettingPlacement.DOS},
			{"cpu", "apmbios version", org.dbgl.gui.abstractdialog.SettingPlacement.MAINBOARD},
			{"cpu", "enable msr", org.dbgl.gui.abstractdialog.SettingPlacement.CPU},
			{"video", "vesa modelist cap", org.dbgl.gui.abstractdialog.SettingPlacement.VESA},
			{"video", "cgasnow", org.dbgl.gui.abstractdialog.SettingPlacement.CGACOMPOSITE},
			{"video", "sierra ramdac", org.dbgl.gui.abstractdialog.SettingPlacement.VIDEO},
			{"ide, secondary", "irq", org.dbgl.gui.abstractdialog.SettingPlacement.CONTROLLERS},
			{"fdc, primary", "chip", org.dbgl.gui.abstractdialog.SettingPlacement.CONTROLLERS},
			{"pc98", "pc-98 fm board irq", org.dbgl.gui.abstractdialog.SettingPlacement.PC98},
			{"midi", "fluid.reverb", org.dbgl.gui.abstractdialog.SettingPlacement.FLUIDSYNTH},
			{"midi", "mt32.engage.channel1", org.dbgl.gui.abstractdialog.SettingPlacement.MT32},
			{"midi", "roland gs sysex", org.dbgl.gui.abstractdialog.SettingPlacement.MIDI},
			{"speaker", "initial frequency", org.dbgl.gui.abstractdialog.SettingPlacement.PCSPEAKER},
			{"wss", "wssbase", org.dbgl.gui.abstractdialog.SettingPlacement.WSS},
			{"ethernet, slirp", "mtu", org.dbgl.gui.abstractdialog.SettingPlacement.NE2000},
			{"webserver", "webserver_port", org.dbgl.gui.abstractdialog.SettingPlacement.NETWORK},
			{"soundcanvas", "soundcanvas_filter", org.dbgl.gui.abstractdialog.SettingPlacement.MIDI},
			{"log", "int21", org.dbgl.gui.abstractdialog.SettingPlacement.LOGGING}};
		for (String[] c: cases)
			assertEquals(c[0] + "/" + c[1], c[2], org.dbgl.gui.abstractdialog.SettingPlacement.pageFor(c[0], c[1]));
		assertNull(org.dbgl.gui.abstractdialog.SettingPlacement.pageFor("sdl", "forcesquarecorner")); // decided by where the [sdl] fields are
	}
	@Test
	public void testSettingSummaryAndPlacementOfOtherFamilies() {
		assertEquals("Amount of memory DOSBox has in megabytes.", org.dbgl.gui.abstractdialog.SettingPlacement.summary("Amount of memory DOSBox has in megabytes.\n  This value is best left at its default.", 90));
		assertEquals("", org.dbgl.gui.abstractdialog.SettingPlacement.summary(null, 90));
		String longText = "This is a very long description without any full stop that goes on and on about the setting and what it does";
		String sum = org.dbgl.gui.abstractdialog.SettingPlacement.summary(longText, 40);
		assertTrue(sum, sum.endsWith("\u2026") && (sum.length() <= 41));
		// sections of DOSBox ECE, Daum and SVN builds
		assertEquals(org.dbgl.gui.abstractdialog.SettingPlacement.VOODOO, org.dbgl.gui.abstractdialog.SettingPlacement.pageFor("glide", "glide"));
		assertEquals(org.dbgl.gui.abstractdialog.SettingPlacement.VOODOO, org.dbgl.gui.abstractdialog.SettingPlacement.pageFor("pci", "voodoo"));
		assertEquals(org.dbgl.gui.abstractdialog.SettingPlacement.NE2000, org.dbgl.gui.abstractdialog.SettingPlacement.pageFor("ne2000", "nicirq"));
		assertEquals(org.dbgl.gui.abstractdialog.SettingPlacement.PRINTER, org.dbgl.gui.abstractdialog.SettingPlacement.pageFor("printer", "dpi"));
		assertEquals(org.dbgl.gui.abstractdialog.SettingPlacement.MODEM, org.dbgl.gui.abstractdialog.SettingPlacement.pageFor("serial", "serial1"));
	}

	@Test
	public void testPathRelocation() {
		String sep = File.separator;
		String oldPath = sep + "old" + sep + "Games" + sep + "Doom";
		String newPath = sep + "new" + sep + "stuff" + sep + "Games" + sep + "Doom";
		String[] prefixes = org.dbgl.util.PathRelocation.changedPrefixes(oldPath, newPath);
		assertEquals(sep + "old", prefixes[0]);
		assertEquals(sep + "new" + sep + "stuff", prefixes[1]);
		assertEquals(sep + "new" + sep + "stuff" + sep + "Games" + sep + "Keen", org.dbgl.util.PathRelocation.relocate(sep + "old" + sep + "Games" + sep + "Keen", prefixes[0], prefixes[1]));
		assertNull(org.dbgl.util.PathRelocation.relocate(sep + "older" + sep + "Games", prefixes[0], prefixes[1]));

		File dosroot = new File(sep + "dbgl" + sep + "dosroot");
		String conf = "[autoexec]\nmount C \"" + sep + "old" + sep + "Games" + sep + "Doom\"\nmount D \"games" + sep + "Keen\"\nimgmount E \"" + sep + "old" + sep + "Games" + sep + "Doomed.iso\" -t iso\n";
		java.util.Map<String, String> moves = new java.util.LinkedHashMap<>();
		moves.put(sep + "old" + sep + "Games" + sep + "Doom", sep + "new" + sep + "Games" + sep + "Doom");
		moves.put(dosroot + sep + "games" + sep + "Keen", dosroot + sep + "dos" + sep + "Keen");
		String result = org.dbgl.util.PathRelocation.replaceInConfiguration(conf, moves, dosroot);
		assertTrue(result, result.contains("mount C \"" + sep + "new" + sep + "Games" + sep + "Doom\""));
		assertTrue(result, result.contains("mount D \"dos" + sep + "Keen\""));
		assertTrue(result, result.contains(sep + "old" + sep + "Games" + sep + "Doomed.iso")); // a different file, untouched
	}

	@Test
	public void testDosboxUpdateCheckerParsing() {
		assertEquals("0.83.0", org.dbgl.util.DosboxUpdateChecker.versionOfTag(SettingsService.STAGING_DOSBOX_FAM.title_, "v0.83.0"));
		assertEquals("2026.10.01", org.dbgl.util.DosboxUpdateChecker.versionOfTag(SettingsService.DOSBOX_X_DOSBOX_FAM.title_, "dosbox-x-v2026.10.01"));
		assertTrue(org.dbgl.util.DosboxUpdateChecker.compareVersions("0.83.1", "0.83.0") > 0);
		assertTrue(org.dbgl.util.DosboxUpdateChecker.compareVersions("0.84", "0.83.9") > 0);
		assertEquals(0, org.dbgl.util.DosboxUpdateChecker.compareVersions("0.83.0", "0.83"));
		assertTrue(org.dbgl.util.DosboxUpdateChecker.compareVersions("2026.05.02", "2026.10.01") < 0);
		assertEquals("", org.dbgl.model.ProfileStats.formatPlayTime(0));
		assertEquals("1:01", org.dbgl.model.ProfileStats.formatPlayTime(3660));
		assertEquals("0:05", org.dbgl.model.ProfileStats.formatPlayTime(300));
	}
	@Test
	public void testStatusWindowSettingLinks() {
		assertEquals("cpu_cycles", org.dbgl.gui.DosboxStatusWindow.settingIn("2026-11-01 12:00:00.000 | CONFIG: Invalid [color=light-green]'cpu_cycles'[reset] setting: [color=white]'fast'[reset];"));
		assertEquals("glshader", org.dbgl.gui.DosboxStatusWindow.settingIn("Deprecated 'glshader' setting: 'sharp'; using 'interpolation/sharp'"));
		assertEquals("aspect", org.dbgl.gui.DosboxStatusWindow.settingIn("Deprecated setting 'aspect'"));
		assertEquals("output", org.dbgl.gui.DosboxStatusWindow.settingIn("\"overlay\" is not a valid value for variable: output."));
		assertEquals("foo bar", org.dbgl.gui.DosboxStatusWindow.settingIn("CONFIG: Unknown option foo bar."));
		assertNull(org.dbgl.gui.DosboxStatusWindow.settingIn("DOSBox Staging 0.83.0 started"));
	}

	@Test
	public void testAutoBackupDue() {
		java.time.LocalDate today = java.time.LocalDate.of(2026, 11, 1);
		assertFalse(org.dbgl.service.BackupService.isAutoBackupDue("never", "", today));
		assertTrue(org.dbgl.service.BackupService.isAutoBackupDue("daily", "", today));
		assertFalse(org.dbgl.service.BackupService.isAutoBackupDue("daily", "2026-11-01", today));
		assertTrue(org.dbgl.service.BackupService.isAutoBackupDue("daily", "2026-10-31", today));
		assertFalse(org.dbgl.service.BackupService.isAutoBackupDue("weekly", "2026-10-28", today));
		assertTrue(org.dbgl.service.BackupService.isAutoBackupDue("weekly", "2026-10-25", today));
		assertTrue(org.dbgl.service.BackupService.isAutoBackupDue("weekly", "garbage", today));
	}

	@Test
	public void testPresetsAndConfImport() throws IOException {
		assertEquals("a_b_c", org.dbgl.util.Presets.safeName(" a/b:c "));
		org.dbgl.model.aggregate.DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null,
			"staging083", "", "", "staging083/dosbox.conf");
		File dosboxConfFile = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
		v083.loadConfigurationData(GenericTextService.getInstance(), Files.readString(dosboxConfFile.toPath(), StandardCharsets.UTF_8), dosboxConfFile);
		org.dbgl.model.aggregate.Profile profile = org.dbgl.model.factory.ProfileFactory.create(v083, null);
		profile.setTitle("Import");
		String conf = "[sdl]\nfullscreen = on\noutput = opengl\n\n[cpu]\ncpu_cycles = 12000\n[dosbox]\nmemsize = 32\nnotasetting = 1\n[autoexec]\nmount c .\n";
		org.dbgl.util.ProfileReports.ImportPlan plan = org.dbgl.util.ProfileReports.planImport(profile, conf);
		// output=opengl is the default already, so it does not change anything
		assertEquals(3, plan.count());
		assertEquals("12000", plan.values().get("cpu").get("cpu_cycles"));
		assertEquals(List.of("[dosbox] notasetting=1"), plan.unknown());
		org.dbgl.util.ProfileReports.applyImport(profile, plan);
		assertEquals("32", profile.getConfiguration().getValue("dosbox", "memsize"));

		org.dbgl.model.aggregate.Profile other = org.dbgl.model.factory.ProfileFactory.create(v083, null);
		other.setTitle("Other");
		String report = org.dbgl.util.ProfileReports.compare(profile, other, GenericTextService.getInstance());
		assertTrue(report, report.contains("[dosbox] memsize") && report.contains("[cpu] cpu_cycles") && !report.contains("[sdl] output"));
	}
	@Test
	public void testStagingExecutableScan() throws IOException {
		File exe = File.createTempFile("dbgl-staging-", ".exe");
		exe.deleteOnExit();
		// Staging also contains "DOSBox-X" (a CD-ROM name) and dates; it must not be taken for DOSBox-X
		Files.write(exe.toPath(), "MZ..DOSBox Staging version %s | DOSBox-X Virtual CD-ROM | 2024.03.01 | 0.83.0 (7b400) |".getBytes(StandardCharsets.ISO_8859_1));
		DosboxVersionDetector.Result r = DosboxVersionDetector.detect(exe, null, false);
		assertEquals(SettingsService.STAGING_DOSBOX_FAM.title_, r.family_);
		assertEquals("0.83.0", r.version_);
		Files.write(exe.toPath(), "MZ..DOSBox version 0.74-3 |".getBytes(StandardCharsets.ISO_8859_1));
		assertNull(DosboxVersionDetector.detect(exe, null, false)); // not run, so unknown
	}

	@Test
	public void testClassicExecutableScan() throws IOException {
		File exe = File.createTempFile("dbgl-classic-", ".exe");
		exe.deleteOnExit();
		String[][] cases = {
			{"MZ..DOSBox ECE r4482 compiled..\0mapper-ECE.map\0", SettingsService.ECE_DOSBOX_FAM.title_, "r4482"},
			{"MZ..DOSBox version %s\0mapper-SVN-Daum.map\0", SettingsService.DAUM_DOSBOX_FAM.title_, "2015.01.25"},
			{"MZ..DOSBox version %s\0mapper-SVN.map\0", SettingsService.SVN_DOSBOX_FAM.title_, "r4483"},
			{"MZ..DOSBox version %s\0mapper-0.74-3.map\0", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.74-3"}};
		for (String[] c: cases) {
			Files.write(exe.toPath(), c[0].getBytes(StandardCharsets.ISO_8859_1));
			DosboxVersionDetector.Result r = DosboxVersionDetector.detect(exe, null, false);
			assertEquals(c[0], c[1], r.family_);
			assertEquals(c[0], c[2], r.version_);
		}
		Files.write(exe.toPath(), "MZ..some other program\0mapper-0.74-3.map\0".getBytes(StandardCharsets.ISO_8859_1));
		assertNull(DosboxVersionDetector.detect(exe, null, false));
	}

	@Test
	public void testPageTreeCacheFormat() {
		java.util.Map<String, List<String[]>> extras = new java.util.HashMap<>();
		extras.put("dialog.template.tab.vesa", List.of(new String[] {"dosbox", "allow tty vesa modes"}, new String[] {"dosbox", "vesa lfb base scanline adjust"}));
		org.dbgl.gui.abstractdialog.PageTreeCache.Info info = new org.dbgl.gui.abstractdialog.PageTreeCache.Info(new java.util.LinkedHashSet<>(List.of("dialog.template.tab.cpu", "dialog.template.tab.vesa")), extras);
		String text = org.dbgl.gui.abstractdialog.PageTreeCache.serialize("key|1", info);
		org.dbgl.gui.abstractdialog.PageTreeCache.Info back = org.dbgl.gui.abstractdialog.PageTreeCache.deserialize("key|1", text);
		assertEquals(info.shownPages(), back.shownPages());
		assertEquals(2, back.extrasByPage().get("dialog.template.tab.vesa").size());
		assertEquals("allow tty vesa modes", back.extrasByPage().get("dialog.template.tab.vesa").get(0)[1]);
		assertNull(org.dbgl.gui.abstractdialog.PageTreeCache.deserialize("key|2", text)); // written for another key
		assertNull(org.dbgl.gui.abstractdialog.PageTreeCache.deserialize("key|1", text + "garbage\n"));
	}

	@Test
	public void testPlacementOfDaumAndEceSettings() throws IOException {
		String[][] cases = {
			{"dosbox", "allow 32bpp vesa modes", org.dbgl.gui.abstractdialog.SettingPlacement.VESA},
			{"dosbox", "vesa lfb base scanline adjust", org.dbgl.gui.abstractdialog.SettingPlacement.VESA},
			{"dosbox", "cgasnow", org.dbgl.gui.abstractdialog.SettingPlacement.CGACOMPOSITE},
			{"dosbox", "sierra ramdac lock 565", org.dbgl.gui.abstractdialog.SettingPlacement.VIDEO},
			{"dosbox", "shell environment size", org.dbgl.gui.abstractdialog.SettingPlacement.DOS},
			{"glide", "lfb", org.dbgl.gui.abstractdialog.SettingPlacement.VOODOO},
			{"log", "vgagfx", org.dbgl.gui.abstractdialog.SettingPlacement.LOGGING}};
		for (String[] c: cases)
			assertEquals(c[0] + "/" + c[1], c[2], org.dbgl.gui.abstractdialog.SettingPlacement.pageFor(c[0], c[1]));
		// the real configuration files of both builds load, with the help texts for the tooltips of the additional fields
		for (String[] f: new String[][] {{"dosbox-daum-2015.01.25.conf", "DOSBox Daum", "2015.01.25", "sierra ramdac"}, {"dosbox-ece-r4482.conf", "DOSBox ECE", "r4482", "fmstrength"}}) {
			org.dbgl.model.aggregate.DosboxVersion v = DosboxVersionFactory.create(f[1] + " " + f[2], f[1], f[2], false, true, false, null, "x", "", "", "x/dosbox.conf");
			File conf = new File(DATA_DIR, f[0]);
			v.loadConfigurationData(GenericTextService.getInstance(), Files.readString(conf.toPath(), StandardCharsets.UTF_8), conf);
			assertTrue(f[0], v.getConfiguration().getSectionNames().length >= 14);
			assertTrue(f[0] + " " + f[3], v.getConfValueOptions() != null);
		}
	}

	@Test
	public void testDosboxInstallerAssetChoice() throws Exception {
		List<String[]> staging = List.of(new String[] {"dosbox-staging-windows-x64-v0.83.0.zip", "a"}, new String[] {"dosbox-staging-windows-x64-v0.83.0-setup.exe", "b"},
			new String[] {"dosbox-staging-linux-x86_64-v0.83.0.tar.xz", "c"}, new String[] {"dosbox-staging-macOS-universal-v0.83.0.dmg", "d"},
			new String[] {"dosbox-staging-v0.83.0-source.tar.xz", "e"}, new String[] {"dosbox-staging-windows-arm64-v0.83.0.zip", "f"});
		assertEquals("a", org.dbgl.util.DosboxInstaller.chooseAsset(staging, true, false, false)[1]);
		assertEquals("f", org.dbgl.util.DosboxInstaller.chooseAsset(staging, true, false, true)[1]);
		assertEquals("c", org.dbgl.util.DosboxInstaller.chooseAsset(staging, false, true, false)[1]);
		assertNull(org.dbgl.util.DosboxInstaller.chooseAsset(staging, false, false, false)); // macOS: only a disk image
		List<String[]> x = List.of(new String[] {"dosbox-x-mingw-win64-20261001153012.zip", "m"}, new String[] {"dosbox-x-vsbuild-win64-20261001153012.zip", "v"},
			new String[] {"dosbox-x-vsbuild-win32-20261001153012.zip", "w"}, new String[] {"dosbox-x-macosx-x86_64-20261001153012.zip", "o"});
		assertEquals("v", org.dbgl.util.DosboxInstaller.chooseAsset(x, true, false, false)[1]);
		assertNull(org.dbgl.util.DosboxInstaller.chooseAsset(x, false, true, false));
		assertEquals("dosbox-staging-0.83.0", org.dbgl.util.DosboxInstaller.folderName("DOSBox Staging", "0.83.0"));

		// unpacking a zip and finding the executable in a sub folder
		File dir = Files.createTempDirectory("dbgl-install").toFile();
		File zip = new File(dir, "build.zip");
		try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
			out.putNextEntry(new java.util.zip.ZipEntry("dosbox-staging-v0.83.0/resources/shaders/x.glsl"));
			out.write(1);
			out.putNextEntry(new java.util.zip.ZipEntry("dosbox-staging-v0.83.0/dosbox.exe"));
			out.write(new byte[] {'M', 'Z'});
		}
		File target = new File(dir, "out");
		org.dbgl.util.DosboxInstaller.unpack(zip, target, false);
		assertEquals("dosbox.exe", org.dbgl.util.DosboxInstaller.findExecutable(target).getName());
		// existing files stay unless overwriting was confirmed, configuration files always stay
		File exe = new File(target, "dosbox-staging-v0.83.0/dosbox.exe");
		File conf = new File(target, "dosbox-staging-v0.83.0/dosbox.conf");
		Files.writeString(exe.toPath(), "old");
		Files.writeString(conf.toPath(), "[cpu]\ncycles=max\n");
		assertEquals(2, org.dbgl.util.DosboxInstaller.unpack(zip, target, false)); // dosbox.exe and the shader stay
		assertEquals("old", Files.readString(exe.toPath()));
		org.dbgl.util.DosboxInstaller.unpack(zip, target, true);
		assertEquals("MZ", Files.readString(exe.toPath()));
		assertTrue(org.dbgl.util.DosboxInstaller.hasContent(target));
		assertEquals("dosbox-x.conf", org.dbgl.util.DosboxInstaller.configurationName("DOSBox-X"));
		assertEquals("dosbox.conf", org.dbgl.util.DosboxInstaller.configurationName("DOSBox Staging"));
		assertEquals("dosbox-x.exe", org.dbgl.util.DosboxInstaller.executableNames("DOSBox-X").get(0));
		File evil = new File(dir, "evil.zip");
		try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(evil.toPath()))) {
			out.putNextEntry(new java.util.zip.ZipEntry("../escaped.txt"));
			out.write(1);
		}
		try {
			org.dbgl.util.DosboxInstaller.unpack(evil, new File(dir, "out2"), false);
			assertTrue("an entry outside the folder must be refused", false);
		} catch (IOException expected) {
			assertFalse(new File(dir, "escaped.txt").exists());
		}
	}

	@Test
	public void testNewerVersionsAndDocumentedValues() throws IOException {
		org.dbgl.model.aggregate.DosboxVersion x = DosboxVersionFactory.create("DOSBox-X 2027.02.01", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, "2027.02.01", false, true, false, null, "x", "", "", "x/dosbox.conf");
		assertEquals(Generation.x_20241001, GenerationAwareConfigurable.determineGen(x));
		org.dbgl.model.aggregate.DosboxVersion s = DosboxVersionFactory.create("DOSBox Staging 0.84.1", SettingsService.STAGING_DOSBOX_FAM.title_, "0.84.1", false, true, false, null, "x", "", "", "x/dosbox.conf");
		assertEquals(Generation.staging_0830, GenerationAwareConfigurable.determineGen(s));
		// the long, right aligned help texts of DOSBox-X document all values of sbtype on one line
		File conf = new File(DATA_DIR, "dosbox-x-2026.10.01.conf");
		if (conf.isFile()) {
			ConfValueOptions o = ConfValueOptions.parse(Files.readString(conf.toPath(), StandardCharsets.UTF_8));
			assertTrue(o.get("sblaster", "sbtype").getValues().containsAll(List.of("sb16", "awe32", "pas16", "none")));
			assertFalse(o.get("sblaster", "sbtype").getValues().contains("sb99"));
		}
	}

	@Test
	public void testOwnPresetText() {
		java.util.Map<String, java.util.Map<String, String>> v = org.dbgl.util.Presets.parse("# my preset\n[cpu]\ncycles = max\n\n[DOSBOX]\nmemsize=32\nnot a setting\n");
		assertEquals("max", v.get("cpu").get("cycles"));
		assertEquals("32", v.get("dosbox").get("memsize"));
		assertEquals(2, v.size());
	}

	@Test
	public void testDownloadChecksumAndFolderRemoval() throws Exception {
		File dir = Files.createTempDirectory("dbgl-a21").toFile();
		File f = new File(dir, "x.zip");
		Files.writeString(f.toPath(), "abc");
		// sha256("abc")
		assertTrue(org.dbgl.util.DosboxInstaller.verify(f, "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
		assertFalse(org.dbgl.util.DosboxInstaller.verify(f, ""));
		try {
			org.dbgl.util.DosboxInstaller.verify(f, "sha256:0000");
			assertTrue("a wrong checksum must be refused", false);
		} catch (IOException expected) {
			// ok
		}
		List<String[]> x = List.of(new String[] {"dosbox-x-mingw-win64-20261001.zip", "m", ""}, new String[] {"dosbox-x-vsbuild-win64-20261001.zip", "v", ""},
			new String[] {"dosbox-x-macosx-x86_64-20261001.zip", "o", ""});
		List<String[]> c = org.dbgl.util.DosboxInstaller.candidates(x, true, false, false);
		assertEquals(2, c.size());
		assertEquals("v", c.get(0)[1]);

		File dbgl = new File(dir, "dbgl"), dosbox = new File(dbgl, "dosbox"), staging = new File(dosbox, "staging-0.83"), other = new File(dosbox, "x-2026");
		staging.mkdirs();
		other.mkdirs();
		Files.writeString(new File(staging, "dosbox.exe").toPath(), "MZ");
		List<File> protectedDirs = List.of(dbgl, new File(dbgl, "profiles"), dosbox);
		assertEquals(org.dbgl.util.DosboxFolderRemoval.Verdict.OK, org.dbgl.util.DosboxFolderRemoval.check(staging, protectedDirs, List.of(other)));
		assertEquals(org.dbgl.util.DosboxFolderRemoval.Verdict.DBGL, org.dbgl.util.DosboxFolderRemoval.check(dosbox, protectedDirs, List.of(other)));
		assertEquals(org.dbgl.util.DosboxFolderRemoval.Verdict.SHARED, org.dbgl.util.DosboxFolderRemoval.check(staging, protectedDirs, List.of(staging)));
		assertEquals(org.dbgl.util.DosboxFolderRemoval.Verdict.MISSING, org.dbgl.util.DosboxFolderRemoval.check(new File(dosbox, "gone"), protectedDirs, List.of()));
		assertEquals(org.dbgl.util.DosboxFolderRemoval.Verdict.ROOT, org.dbgl.util.DosboxFolderRemoval.check(File.listRoots()[0], protectedDirs, List.of()));
		assertEquals(1, org.dbgl.util.DosboxFolderRemoval.countFiles(staging));
		org.dbgl.util.DosboxFolderRemoval.delete(staging);
		assertFalse(staging.exists());
		assertTrue(other.exists());
	}
}
