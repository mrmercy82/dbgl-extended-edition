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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
		assertEquals("r4494", r.version_); // no revision named: the newest one DBGL knows (alpha 49: r4494)
		r = DosboxVersionDetector.parse("DOSBox version 0.74-SVN-r4483, copyright 2002-2021 DOSBox Team.");
		assertEquals(SettingsService.SVN_DOSBOX_FAM.title_, r.family_);
		assertEquals("r4483", r.version_);
		assertEquals("r4494", DosboxVersionDetector.parse("DOSBox version 0.74-SVN-r4494, copyright 2002-2021 DOSBox Team.").version_);
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
		// alpha 53: the releases of 2020 to 2024.02 have the settings of 2024.03.01 (compared with their reference configurations)
		assertEquals(Generation.x_20240301, gen(x, "2023.09.01"));
		assertEquals(Generation.x_20240301, gen(x, "2022.08.0"));
		assertEquals(Generation.x_20240301, gen(x, "0.84.1"));
		assertEquals(Generation.x_20240301, gen(x, "0.83.25"));
		assertEquals(Generation.x_08220, gen(x, "0.82.26"));
		assertEquals(Generation.x_08220, gen(x, "0.82.20"));
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

		// alpha 53: older releases without a full date
		Files.write(exe.toPath(), "DOSBox-X\0libFLAC 0.83.25 20190804\0/INIT\0SDL1\u00000.83.25\0COMMAND\0".getBytes(StandardCharsets.ISO_8859_1));
		assertEquals("0.83.25", DosboxVersionDetector.scanDosboxXExecutable(exe).version_);
		Files.write(exe.toPath(), "DOSBox-X\0SHELL_CMD_VER_VER\0SDL2\u00002022.08.0\0Aug 1, 2022".getBytes(StandardCharsets.ISO_8859_1));
		assertEquals("2022.08.0", DosboxVersionDetector.scanDosboxXExecutable(exe).version_);
		Files.write(exe.toPath(), "DOSBox-X\0vm86 mode\0dosbox-x 0.82.20 (C) 2002-2019".getBytes(StandardCharsets.ISO_8859_1));
		assertEquals("0.82.20", DosboxVersionDetector.scanDosboxXExecutable(exe).version_);

		Files.write(exe.toPath(), "DOSBox Staging 0.83.0".getBytes(StandardCharsets.ISO_8859_1));
		assertNull(DosboxVersionDetector.scanDosboxXExecutable(exe));
		assertTrue(DosboxVersionDetector.dosboxXBuild(exe).isEmpty());
	}

	/**
	 * Lines of the [autoexec] added by hand were lost when switching a profile to another DOSBox version (DBGL writes the
	 * [autoexec] again from what it understood). They are now listed, and kept lines go into the own sections.
	 */
	@Test
	public void testUnknownAutoexecLinesAndSwitchChoices() throws IOException {
		org.dbgl.model.conf.Autoexec autoexec = new org.dbgl.model.conf.Autoexec();
		autoexec.load(List.of("set BLASTER=A220 I7 D1 T6", "mount c \"games\"", "c:", "cd \\keen", "mouse.com", "rem my own note", "keen1.exe -nojoy", "cls", "exit", "echo never shown"), null, null);
		List<org.dbgl.model.conf.Autoexec.UnknownLine> unknown = autoexec.getUnknownLines();
		assertEquals(List.of(new org.dbgl.model.conf.Autoexec.UnknownLine("set BLASTER=A220 I7 D1 T6", 0, false), new org.dbgl.model.conf.Autoexec.UnknownLine("mouse.com", 1, false),
			new org.dbgl.model.conf.Autoexec.UnknownLine("rem my own note", 1, false), new org.dbgl.model.conf.Autoexec.UnknownLine("cls", 2, false),
			new org.dbgl.model.conf.Autoexec.UnknownLine("echo never shown", 3, true)), unknown);
		assertEquals("-nojoy", autoexec.getParameters());

		// kept lines are written into the own sections, and read again from there they are known
		for (org.dbgl.model.conf.Autoexec.UnknownLine l: unknown.subList(0, 4))
			autoexec.keepUnknownLine(l);
		String eol = org.dbgl.util.SystemUtils.EOLN;
		assertEquals("set BLASTER=A220 I7 D1 T6" + eol, autoexec.getCustomSection(0));
		assertEquals("mouse.com" + eol + "rem my own note" + eol, autoexec.getCustomSection(1));
		assertEquals("cls" + eol, autoexec.getCustomSection(2));
		String written = autoexec.toString(false, null);
		assertTrue(written, written.contains("mouse.com") && written.contains("keen1.exe -nojoy") && written.contains("set BLASTER"));
		org.dbgl.model.conf.Autoexec again = new org.dbgl.model.conf.Autoexec();
		List<String> writtenLines = Arrays.asList(written.split("\\R"));
		assertEquals("[autoexec]", writtenLines.get(0));
		again.load(writtenLines.subList(1, writtenLines.size()), null, null); // without the section header, as when reading a file
		assertTrue(again.getUnknownLines().toString(), again.getUnknownLines().isEmpty());
		assertEquals(autoexec.getCustomSection(1), again.getCustomSection(1));

		// a game without own program line: the last line is the game, nothing is lost
		org.dbgl.model.conf.Autoexec noExe = new org.dbgl.model.conf.Autoexec();
		noExe.load(List.of("mount c \"games\"", "c:", "cd \\doom", "doom -nomonsters"), null, null);
		assertTrue(noExe.getUnknownLines().isEmpty());

		// settings: a dropped value can be kept
		org.dbgl.model.aggregate.DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null,
			"staging083", "", "", "staging083/dosbox.conf");
		File dosboxConfFile = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
		v083.loadConfigurationData(GenericTextService.getInstance(), Files.readString(dosboxConfFile.toPath(), StandardCharsets.UTF_8), dosboxConfFile);
		org.dbgl.model.aggregate.Profile profile = org.dbgl.model.factory.ProfileFactory.create(v083, null);
		profile.getConfiguration().setValue("sdl", "output", "opengl");
		org.dbgl.util.SwitchChoices.Snapshot snapshot = org.dbgl.util.SwitchChoices.before(profile);
		profile.getConfiguration().removeValue("sdl", "output"); // as a switch to a version without this value would do
		List<org.dbgl.util.SwitchChoices.Choice> choices = org.dbgl.util.SwitchChoices.after(snapshot, profile);
		org.dbgl.util.SwitchChoices.Choice output = choices.stream().filter(c -> "output".equals(c.item())).findFirst().orElseThrow();
		assertNull(output.newText());
		assertFalse(output.defaultKeep());
		org.dbgl.util.SwitchChoices.apply(profile, choices, c -> c == output, true, true);
		assertEquals("opengl", profile.getConfiguration().getValue("sdl", "output"));
	}

	/**
	 * Lines DBGL does not know are kept whenever a profile is read: into the own sections with marker lines, at the same
	 * place without them, or until the user decides; a line after "exit" stays there.
	 */
	@Test
	public void testUnknownAutoexecLinesKeptOnRead() {
		SettingsService settings = SettingsService.getInstance();
		String mode = settings.getValue("autoexec", "unknownlines");
		boolean noMarkers = settings.getBooleanValue("autoexec", "nomarkers");
		List<String> lines = List.of("mount c \"games\"", "c:", "cd \\keen", "mouse.com", "keen1.exe", "exit", "rem never run");
		String eol = org.dbgl.util.SystemUtils.EOLN;
		try {
			// automatic, with marker lines
			settings.setValue("autoexec", "unknownlines", "auto");
			settings.setBooleanValue("autoexec", "nomarkers", false);
			org.dbgl.model.conf.Autoexec a = new org.dbgl.model.conf.Autoexec();
			a.load(lines, null, null);
			org.dbgl.util.UnknownAutoexecLines.keepOnRead(a);
			String written = a.toString(false, null);
			assertTrue(written, written.contains("@REM PRE-LAUNCH" + eol + "mouse.com" + eol + "@REM /PRE-LAUNCH" + eol + "keen1.exe"));
			assertTrue(written, written.endsWith("exit" + eol + "rem never run" + eol));

			// automatic, without marker lines: in place, and the same again after reading what was written
			settings.setBooleanValue("autoexec", "nomarkers", true);
			a = new org.dbgl.model.conf.Autoexec();
			a.load(lines, null, null);
			org.dbgl.util.UnknownAutoexecLines.keepOnRead(a);
			written = a.toString(false, null);
			assertFalse(written, written.contains("@REM"));
			assertTrue(written, written.contains("cd \\keen" + eol + "mouse.com" + eol + "keen1.exe"));
			org.dbgl.model.conf.Autoexec b = new org.dbgl.model.conf.Autoexec();
			List<String> writtenLines = Arrays.asList(written.split("\\R"));
			b.load(writtenLines.subList(1, writtenLines.size()), null, null);
			org.dbgl.util.UnknownAutoexecLines.keepOnRead(b);
			assertEquals(written, b.toString(false, null));

			// ask: kept in place until decided, then as chosen
			settings.setValue("autoexec", "unknownlines", "ask");
			assertFalse(org.dbgl.util.UnknownAutoexecLines.withoutMarkers());
			a = new org.dbgl.model.conf.Autoexec();
			a.load(lines, null, null);
			org.dbgl.util.UnknownAutoexecLines.keepOnRead(a);
			assertFalse(a.toString(false, null).contains("@REM"));
			org.dbgl.model.conf.Autoexec.UnknownLine mouse = a.getUnknownLines().get(0);
			assertEquals("mouse.com", mouse.text());
			a.removePlacedLine(mouse);
			a.placeLine(new org.dbgl.model.conf.Autoexec.UnknownLine("mouse.com", 0, false), true); // chosen: At Start, with marker lines
			written = a.toString(false, null);
			assertTrue(written, written.startsWith("[autoexec]" + eol + "@REM START" + eol + "mouse.com" + eol + "@REM /START" + eol));
			assertEquals(1, written.split("mouse\\.com", -1).length - 1);
		} finally {
			settings.setValue("autoexec", "unknownlines", mode);
			settings.setBooleanValue("autoexec", "nomarkers", noMarkers);
		}
	}

	/**
	 * Mount commands added by hand: "mount" with CD images (as DOSBox Staging 0.83 does it, IMGMOUNT is only an alias there) is
	 * an image mount now, shown in the profile dialog and written again with "mount". A mount command DBGL cannot read at all
	 * was dropped without a word when the profile was saved (already in 0.100 beta 17); it is a line DBGL does not know now,
	 * and kept.
	 */
	@Test
	public void testMountLinesAddedByHand() {
		SettingsService settings = SettingsService.getInstance();
		String mode = settings.getValue("autoexec", "unknownlines");
		try {
			settings.setValue("autoexec", "unknownlines", "ask"); // kept in place until the user decides
			String cd = "mount D \"D:\\Spiele\\DOS\\SPIELE\\MEGA2\\iso\\Mega2_DEU1.iso\" \"D:\\Spiele\\DOS\\SPIELE\\MEGA2\\iso\\Mega2_DEU2.iso\" -t cdrom";
			String odd = "mount E"; // no folder or image: DBGL cannot read it
			// the game folder relative, as the test also runs on Linux
			List<String> lines = List.of("@echo off", "mount C \"games\\MEGA2\"", cd, odd, "C:", "cd \\", "cls", "MEGARACE.EXE", "exit");
			org.dbgl.model.conf.Autoexec a = new org.dbgl.model.conf.Autoexec();
			a.load(lines, null, null);
			assertEquals(2, a.getMountingpoints().size());
			org.dbgl.model.conf.mount.ImageMount images = (org.dbgl.model.conf.mount.ImageMount)a.getMountingpoints().get(1);
			assertEquals(2, images.getImgPathStrings().length);
			assertEquals("mount", images.getCommand());
			assertTrue(a.getUnknownLines().toString(), a.getUnknownLines().stream().anyMatch(l -> l.text().equals(odd)));
			assertFalse(a.getUnknownLines().toString(), a.getUnknownLines().stream().anyMatch(l -> l.text().equals(cd)));
			org.dbgl.util.UnknownAutoexecLines.keepOnRead(a);
			String written = a.toString(false, null);
			assertTrue(written, written.contains(odd));
			assertTrue(written, written.contains("Mega2_DEU2.iso\" -t cdrom"));
			assertFalse(written, written.contains("imgmount"));
			assertTrue(written, written.contains("MEGARACE.EXE"));
			// read again: the same
			org.dbgl.model.conf.Autoexec b = new org.dbgl.model.conf.Autoexec();
			List<String> again = Arrays.asList(written.split("\\R"));
			b.load(again.subList(1, again.size()), null, null);
			org.dbgl.util.UnknownAutoexecLines.keepOnRead(b);
			assertEquals(written, b.toString(false, null));
			// plain imgmount stays imgmount
			org.dbgl.model.conf.Autoexec c = new org.dbgl.model.conf.Autoexec();
			c.load(List.of("imgmount D \"games\\cd.iso\" -t iso"), null, null);
			assertTrue(c.toString(false, null), c.toString(false, null).contains("imgmount D"));
		} finally {
			settings.setValue("autoexec", "unknownlines", mode);
		}
	}

	/**
	 * The configuration of the DOSBox version mounts C, the profile mounts C too (another folder, with the game): DOSBox keeps
	 * its own C, so DBGL did not find the game through the drives that count and dropped the lines starting it on every save.
	 * They are kept now, written through the profile's own mount, as they were.
	 */
	@Test
	public void testProfileDriveAlsoInDosboxConf() throws IOException {
		File dosboxC = java.nio.file.Files.createTempDirectory("dbgldosboxc").toFile(), gameDir = java.nio.file.Files.createTempDirectory("dbglgame").toFile();
		java.nio.file.Files.writeString(new File(gameDir, "GAME.EXE").toPath(), "MZ");
		Configuration dosbox = new Configuration();
		dosbox.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount C \"" + dosboxC.getPath() + "\"\n", null, null);
		List<org.dbgl.model.conf.mount.Mount> dbMounts = dosbox.getAutoexec().getMountingpoints();
		Configuration c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount C \"" + gameDir.getPath() + "\"\nC:\ncd \\\nGAME.EXE\nexit\n", null, dbMounts);
		assertTrue(c.getAutoexec().getMain(), c.getAutoexec().getMain().endsWith("GAME.EXE"));
		String written = c.toString(dbMounts); // as saved: through the drives that count, the DOSBox version's C
		assertTrue(written, written.contains("GAME.EXE"));
		assertTrue(written, written.contains("C:"));
		// read with that DOSBox version, written for one without its "mount C" (switched): the game stays (alpha 46, profile 54
		// lost its start lines so); where DOSBox looks is still told
		assertEquals(new File(gameDir, "GAME.EXE").getPath(), c.getAutoexec().getMain());
		assertEquals(new File(dosboxC, "GAME.EXE").getPath(), c.getAutoexec().getMainWhereDosboxLooks());
		String switched = c.toString(List.of());
		assertTrue(switched, switched.contains("GAME.EXE") && switched.contains("C:"));
		org.apache.commons.io.FileUtils.deleteDirectory(dosboxC);
		org.apache.commons.io.FileUtils.deleteDirectory(gameDir);
	}

	/**
	 * Comments and lines DBGL cannot read (alpha 46): before, saving a profile dropped them without a word. They stay where they
	 * were; DBGL's own marker and custom section are not kept twice; a section that is gone comes back with just them.
	 */
	@Test
	public void testForeignLinesKeptOnSave() {
		String before = "# Kopf\n[sdl]\n# zum Vollbild\nfullscreen true\noutput=opengl\n[cpu]\ncycles=max\n[autoexec]\n# erst einbinden\nmount C .\nC:\nGAME.EXE\n# Ende\n";
		String after = "[sdl]\noutput=opengl\n\n[autoexec]\nmount C \".\"\nC:\nGAME.EXE\n";
		String kept = org.dbgl.model.conf.ForeignLines.keep(before, after);
		assertEquals("# Kopf\n[sdl]\n# zum Vollbild\nfullscreen true\noutput=opengl\n\n[autoexec]\n# erst einbinden\nmount C \".\"\nC:\nGAME.EXE\n# Ende\n", kept);
		assertEquals(kept, org.dbgl.model.conf.ForeignLines.keep(kept, kept)); // nothing twice
		String own = org.dbgl.model.conf.Configuration.ALL_SETTINGS_MARKER + "\n\n[sdl]\nfullscreen=true\n";
		assertEquals("[sdl]\nfullscreen=true\n", org.dbgl.model.conf.ForeignLines.keep(own, "[sdl]\nfullscreen=true\n")); // the marker is DBGL's
		assertEquals("[render]\naspect=true\n[sdl]\n# nur ein Kommentar\n\n", org.dbgl.model.conf.ForeignLines.keep("[sdl]\n# nur ein Kommentar\nfullscreen=false\n", "[render]\naspect=true\n"));
		assertEquals(after, org.dbgl.model.conf.ForeignLines.keep("\uFEFF[sdl]\noutput=opengl\n", after)); // a byte order mark is no line of its own
	}

	/**
	 * Alpha 47: a mount added to a profile whose [autoexec] DBGL would write in another order (a start script with "win" at its
	 * end): only the mount line is added, after the other ones, everything else stays as written. Removed again: as before.
	 */
	@Test
	public void testOnlyMountLineAdded() throws IOException {
		String before = "[cpu]\ncputype = pentium\n\n[autoexec]\n@ECHO OFF\ncls\nmount c .\n\nSET PATH=Z:\\;C:\\;C:\\WINDOWS;\nC:\\WINDOWS\\SMARTDRV.EXE\n\nc:\ncd\\\ncd windows\ncls\nwin\nexit\n";
		Configuration c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), before, null, null);
		org.dbgl.util.UnknownAutoexecLines.keepOnRead(c.getAutoexec());
		c.getAutoexec().addMount("mount E \"other\"");
		String saved = c.textToSave(before, false, List.of()).replace("\r\n", "\n");
		assertEquals(before.replace("mount c .\n", "mount c .\nmount E \"other\"\n"), saved);
		assertEquals("mount d x", org.dbgl.model.conf.MountLines.patch("[autoexec]\nmount d x\nmount e y", "[autoexec]\nmount D \"x\"\nmount E \"y\"", "[autoexec]\nmount D \"x\"").replace("\r\n", "\n").substring(11));
		assertNull(org.dbgl.model.conf.MountLines.patch("[autoexec]\nmount d x", "[autoexec]\nmount D \"x\"\nC:", "[autoexec]\nmount D \"x\"\nD:")); // more than a mount
	}

	/** Alpha 47: a line put after a section from the profile dialog (no line break at its end) stays a line of its own */
	@Test
	public void testPlacedLineAfterDialogSection() {
		org.dbgl.model.conf.Autoexec a = new org.dbgl.model.conf.Autoexec();
		a.setCustomSection(1, "SET PATH=Z:\\;C:\\");
		a.placeLine(new org.dbgl.model.conf.Autoexec.UnknownLine("cls", 1, false), true);
		assertEquals(List.of("SET PATH=Z:\\;C:\\", "cls"), List.of(a.getCustomSection(1).split("\\R")));
	}

	/**
	 * Alpha 47: saving keeps the order of the sections and settings, the empty lines and settings as written ("fullscreen =
	 * true"); a changed value is written as DBGL writes it, a new setting after the others of its section. An [autoexec] DBGL
	 * would write as it reads it stays as written (a menu with CHOICE and GOTO stays in its order, "c:" stays "c:").
	 */
	@Test
	public void testLayoutKeptOnSave() throws IOException {
		String before = "[cpu]\ncycles=max\n\n\n[sdl]\nfullscreen = true\n\n# Ausgabe\noutput=opengl\n\n[autoexec]\n@echo off\nchoice /c:12\nif errorlevel 2 goto b\nmount c .\nc:\ngame.exe\ngoto end\n:b\nmount c .\nc:\nsetup.exe\n:end\n";
		Configuration c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), before, null, null);
		org.dbgl.util.UnknownAutoexecLines.keepOnRead(c.getAutoexec());
		assertEquals(before, c.textToSave(before, false, List.of()).replace("\r\n", "\n")); // nothing changed: the same
		c.setValue("cpu", "cycles", "auto");
		c.setValue("sdl", "autolock", "true");
		c.setValue("render", "aspect", "true");
		String saved = c.textToSave(before, false, List.of()).replace("\r\n", "\n");
		assertEquals("[cpu]\ncycles=auto\n\n\n[sdl]\nfullscreen = true\n\n# Ausgabe\noutput=opengl\nautolock=true\n\n[render]\naspect=true\n\n[autoexec]" + before.substring(before.indexOf("[autoexec]") + 10), saved);
		Configuration again = new Configuration();
		again.loadDataWithAutoexec(GenericTextService.getInstance(), saved, null, null);
		org.dbgl.util.UnknownAutoexecLines.keepOnRead(again.getAutoexec());
		assertEquals(saved, again.textToSave(saved, false, List.of()).replace("\r\n", "\n")); // saving twice: the same
	}

	/**
	 * Found with configurations from the internet (alpha 46): DOSBox reads "[SDL]" and "[Autoexec]" as [sdl] and [autoexec].
	 * DBGL did not: the value of "[SDL]" was not shown, and the lines of "[Autoexec]" were gone after saving.
	 */
	@Test
	public void testSectionNamesInAnyCase() throws IOException {
		Configuration c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[SDL]\nFullScreen=True\n[Autoexec]\nmount C \".\"\nC:\nGAME.EXE\n", null, null);
		assertEquals("True", c.getValue("sdl", "fullscreen"));
		assertEquals(1, c.getAutoexec().getMountingpoints().size());
		String written = c.toString(List.of());
		assertTrue(written, written.contains("[autoexec]") && written.contains("GAME.EXE"));
	}

	/**
	 * Found by the round trip test (alpha 46): a game on a drive mounted only by the configuration of the DOSBox version
	 * ("mount Y" there, "Y:", "cd \", "GAME.EXE" in the profile). Switched to a DOSBox version without that drive, the lines
	 * that start it were left out without a word, and switching back did not bring the game back.
	 */
	@Test
	public void testStartLinesKeptWithoutTheDriveOfTheDosboxConf() throws IOException {
		File gameDir = java.nio.file.Files.createTempDirectory("dbglgame").toFile();
		java.nio.file.Files.writeString(new File(gameDir, "GAME.EXE").toPath(), "MZ");
		Configuration dosbox = new Configuration();
		dosbox.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount Y \"" + gameDir.getPath() + "\"\n", null, null);
		List<org.dbgl.model.conf.mount.Mount> withY = dosbox.getAutoexec().getMountingpoints();
		Configuration c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nY:\ncd \\\nGAME.EXE\nexit\n", null, withY);
		assertEquals(new File(gameDir, "GAME.EXE").getPath(), c.getAutoexec().getMain());
		String switched = c.toString(List.of()); // written for a DOSBox version without "mount Y"
		assertTrue(switched, switched.contains("Y:") && switched.contains("GAME.EXE"));
		Configuration without = new Configuration();
		without.loadDataWithAutoexec(GenericTextService.getInstance(), switched, null, List.of());
		for (org.dbgl.model.conf.Autoexec.UnknownLine l: without.getAutoexec().getUnknownLines())
			without.getAutoexec().placeLine(l, true); // also when kept with marker lines: the start lines stay where they were
		String again = without.toString(List.of());
		assertFalse(again, again.contains("@REM"));
		Configuration back = new Configuration();
		back.loadDataWithAutoexec(GenericTextService.getInstance(), again, null, withY); // and switched back
		assertEquals(new File(gameDir, "GAME.EXE").getPath(), back.getAutoexec().getMain());
		org.apache.commons.io.FileUtils.deleteDirectory(gameDir);
	}

	/**
	 * Found by the cross test of "Check profiles" (alpha 46), all three in the reading of a configuration:
	 * a byte order mark (Notepad) hid the first section, and saving wrote an empty profile; the game was looked for in the
	 * last mount of its drive (also an overlay), DOSBox uses the first; lines to start a game on a drive that is not mounted
	 * were dropped on the next save.
	 */
	@Test
	public void testReadingFoundByCrossTest() throws IOException {
		File game = java.nio.file.Files.createTempDirectory("dbglgame").toFile(), other = java.nio.file.Files.createTempDirectory("dbglother").toFile();
		java.nio.file.Files.writeString(new File(game, "GAME.EXE").toPath(), "MZ");
		String g = "mount C \"" + game.getPath() + "\"";

		File conf = new File(game, "bom.conf");
		java.nio.file.Files.write(conf.toPath(), ("\uFEFF[sdl]\r\nfullscreen=true\r\n[autoexec]\r\n" + g + "\r\nC:\r\nGAME.EXE\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Configuration c = new Configuration();
		c.setFileLocation(new org.dbgl.model.FileLocation(conf.getPath()));
		String warnings = c.loadWithAutoexec(GenericTextService.getInstance(), null, null);
		assertEquals(warnings, "true", c.getValue("sdl", "fullscreen"));
		assertEquals(new File(game, "GAME.EXE").getPath(), c.getAutoexec().getMain());
		java.nio.file.Files.write(conf.toPath(), ("\u00EF\u00BB\u00BF[autoexec]\n" + g + "\nC:\nGAME.EXE\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)); // as Windows-1252 reads it
		c = new Configuration();
		c.setFileLocation(new org.dbgl.model.FileLocation(conf.getPath()));
		c.loadWithAutoexec(GenericTextService.getInstance(), null, null);
		assertTrue(c.getAutoexec().getMain().endsWith("GAME.EXE"));

		for (String second: new String[] {"mount C \"" + other.getPath() + "\" -t overlay", "mount C \"" + other.getPath() + "\""}) {
			c = new Configuration();
			c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\n" + g + "\n" + second + "\nC:\nGAME.EXE\n", null, null);
			assertEquals(second, new File(game, "GAME.EXE").getPath(), c.getAutoexec().getMain());
		}
		c = new Configuration(); // unmounted first: the second one counts
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount C \"" + other.getPath() + "\"\nmount -u C\n" + g + "\nC:\nGAME.EXE\n", null, null);
		assertEquals(new File(game, "GAME.EXE").getPath(), c.getAutoexec().getMain());

		c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\n" + g + "\nY:\ncd \\GAMES\nOTHER.EXE -x\nexit\n", null, null);
		org.dbgl.util.UnknownAutoexecLines.keepOnRead(c.getAutoexec());
		String written = c.toString(null);
		assertTrue(written, written.contains("Y:") && written.contains("cd \\GAMES") && written.contains("OTHER.EXE -x"));
		org.apache.commons.io.FileUtils.deleteDirectory(game);
		org.apache.commons.io.FileUtils.deleteDirectory(other);
	}

	/** GOG's CD images: game.gog (an ISO or BIN) with game.ins (a cue sheet), mounted as images and checked like .iso and .cue */
	@Test
	public void testGogImages() throws IOException {
		assertTrue(org.dbgl.util.FilesUtils.isCdImageFile("Game.GOG"));
		assertTrue(org.dbgl.util.FilesUtils.isCdImageFile("game.ins"));
		assertTrue(org.dbgl.util.FilesUtils.isCueSheet("GAME.INS"));
		assertFalse(org.dbgl.util.FilesUtils.isCueSheet("game.gog"));
		assertTrue(org.dbgl.util.FilesUtils.isDiskImage("game.gog"));
		assertTrue(org.dbgl.util.FilesUtils.isDiskImage("GAME.GAME")); // alpha 46: GOG's .game (an ISO) and .inst
		assertTrue(org.dbgl.util.FilesUtils.isCueSheet("game.inst"));
		assertTrue(org.dbgl.util.FilesUtils.isCdImageFile("Lure.game"));
		File dir = java.nio.file.Files.createTempDirectory("dbglgog").toFile();
		File gog = new File(dir, "game.gog"), ins = new File(dir, "game.ins");
		java.nio.file.Files.writeString(gog.toPath(), "x");
		java.nio.file.Files.writeString(ins.toPath(), "FILE \"game.gog\" BINARY\r\n  TRACK 01 MODE1/2352\r\nFILE \"track02.ogg\" OGG\r\n");
		Configuration c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount D \"" + gog.getPath() + "\" -t cdrom\nimgmount E \"" + ins.getPath() + "\" -t iso\n", null, null);
		List<org.dbgl.model.conf.mount.Mount> mounts = c.getAutoexec().getMountingpoints();
		assertEquals(2, mounts.size());
		assertTrue(mounts.get(0).toString(), mounts.get(0) instanceof org.dbgl.model.conf.mount.ImageMount); // not a folder mount
		assertTrue(mounts.get(1).toString(), mounts.get(1) instanceof org.dbgl.model.conf.mount.ImageMount);
		assertEquals(List.of("track02.ogg"), org.dbgl.util.ProfileChecks.missingCueFiles(ins));

		// an image by its options, whatever its name (GOG's "gog.game"); -size alone is also there for a folder
		File game = new File(dir, "gog.game");
		java.nio.file.Files.writeString(game.toPath(), "x");
		c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount D \"" + game.getPath() + "\" -t iso -fs iso\nmount E \"" + game.getPath() + "\" -t cdrom\nmount C \"" + dir.getPath() + "\" -size 512,127,16383,4000\nmount F \"" + dir.getPath() + "\" -t cdrom\n", null, null);
		mounts = c.getAutoexec().getMountingpoints();
		assertTrue(mounts.get(0).toString(), mounts.get(0) instanceof org.dbgl.model.conf.mount.ImageMount);
		assertTrue(mounts.get(1).toString(), mounts.get(1) instanceof org.dbgl.model.conf.mount.ImageMount);
		assertFalse(mounts.get(2).toString(), mounts.get(2) instanceof org.dbgl.model.conf.mount.ImageMount);
		assertFalse(mounts.get(3).toString(), mounts.get(3) instanceof org.dbgl.model.conf.mount.ImageMount);
		assertTrue(c.toString(null), c.toString(null).contains("mount D \"" + game.getPath() + "\" -t iso -fs iso")); // written again as it was
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	/**
	 * " - " in the name of an image ("Werewolf vs. Comanche 2.0 - Comanche 2.0 (deutsch).iso"): "mount D ... -t cdrom" was read
	 * as a folder (the path was cut at " -"), and a "boot" line with such an image was not read at all and dropped on saving.
	 */
	@Test
	public void testDashInImageNames() throws IOException {
		File dir = java.nio.file.Files.createTempDirectory("dbgldash").toFile();
		File iso = new File(dir, "Werewolf vs. Comanche 2.0 - Comanche 2.0 (deutsch).iso"), img = new File(dir, "Game - Disk 1.img"), folder = new File(dir, "Ordner - mit Strich");
		java.nio.file.Files.writeString(iso.toPath(), "x");
		java.nio.file.Files.writeString(img.toPath(), "x");
		folder.mkdir();
		Configuration c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount D \"" + iso.getPath() + "\" -t cdrom\nmount C \"" + folder.getPath() + "\"\n", null, null);
		List<org.dbgl.model.conf.mount.Mount> mounts = c.getAutoexec().getMountingpoints();
		assertTrue(mounts.get(0).toString(), mounts.get(0) instanceof org.dbgl.model.conf.mount.ImageMount);
		assertEquals(iso.getName(), ((org.dbgl.model.conf.mount.ImageMount)mounts.get(0)).getCanonicalImgPaths()[0].getName());
		assertFalse(mounts.get(1).toString(), mounts.get(1) instanceof org.dbgl.model.conf.mount.ImageMount);
		c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nboot \"" + img.getPath() + "\" -l a\n", null, null);
		assertTrue(c.getAutoexec().isBooter());
		assertEquals(img.getPath(), c.getAutoexec().getImg(0));
		assertTrue(c.toString(null), c.toString(null).contains("boot \"" + img.getPath() + "\""));
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	/**
	 * A mount that fails (its image is not there) leaves the drive free: the next line for it is the one that works. It was
	 * told as "DOSBox skips this line" (the working one), and the game looked for on the failing one.
	 */
	@Test
	public void testFailingMountLeavesDriveFree() throws IOException {
		File dir = java.nio.file.Files.createTempDirectory("dbglfail").toFile();
		File iso = new File(dir, "GAME.ISO"), game = new File(dir, "game");
		java.nio.file.Files.writeString(iso.toPath(), "x");
		game.mkdir();
		java.nio.file.Files.writeString(new File(game, "GAME.EXE").toPath(), "MZ");
		String gone = new File(dir, "gone.iso").getPath(), here = iso.getPath();
		Configuration c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount C \"" + new File(dir, "nothere").getPath() + "\"\nmount C \"" + game.getPath() + "\"\nmount D \"" + gone
			+ "\" -t cdrom\nmount D \"" + here + "\" -t cdrom\nmount D \"" + here + "\" -t cdrom\nC:\nGAME.EXE\n", null, null);
		org.dbgl.model.conf.Autoexec a = c.getAutoexec();
		assertEquals(new File(game, "GAME.EXE").getPath(), a.getMain()); // the C that works
		assertEquals(0, a.mountsOfDrivesMountedAlready().size()); // nothing "skipped" that works
		assertEquals(1, a.repeatedMounts().size()); // the second line with GAME.ISO
		assertEquals(2, a.deadMounts().size()); // "nothere" and gone.iso: later lines for C and D work
		assertEquals(3, a.removeRepeatedMounts());
		assertEquals(2, a.getMountingpoints().size());
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	/**
	 * Cases like Death Gate: "cd \\DEATHGATE" (DOS does not find a name longer than 8.3, written again with DOSBox's short name),
	 * a path with spaces without quotes (DOSBox reads several paths), "-pr" (relative to the configuration file read last).
	 */
	@Test
	public void testDeathGateLikeCases() throws IOException {
		File dir = java.nio.file.Files.createTempDirectory("dbgldg").toFile();
		File games = new File(dir, "SPIELE"), longDir = new File(games, "DEATHGATE"), shortDir = new File(games, "DGATE"), spaces = new File(dir, "Spiele DOS"), cds = new File(dir, "CD Images");
		longDir.mkdirs();
		shortDir.mkdirs();
		spaces.mkdirs();
		cds.mkdirs();
		java.nio.file.Files.writeString(new File(longDir, "GAME.EXE").toPath(), "MZ");
		java.nio.file.Files.writeString(new File(shortDir, "DGATE.EXE").toPath(), "MZ");
		java.nio.file.Files.writeString(new File(cds, "dgate.iso").toPath(), "x");

		Configuration c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount C \"" + games.getPath() + "\"\nC:\ncd \\DEATHGATE\nGAME.EXE\n", null, null);
		org.dbgl.model.conf.Autoexec a = c.getAutoexec();
		assertTrue(a.hasLongNamesWithSpaces());
		assertEquals("DEATHGATE → DEATHG~1", org.dbgl.util.ProfileChecks.longDosNames(new File(a.getMain()), a.getMountingpoints()));
		assertTrue(c.toString(null), c.toString(null).contains("cd \\DEATHG~1"));
		c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount C \"" + games.getPath() + "\"\nC:\ncd \\DGATE\nDGATE.EXE\n", null, null);
		assertFalse(c.getAutoexec().hasLongNamesWithSpaces());

		assertEquals("mount C \"" + spaces.getPath() + "\"", org.dbgl.util.ProfileChecks.withQuotes("mount C " + spaces.getPath()));
		assertEquals("imgmount D \"" + new File(cds, "dgate.iso").getPath() + "\" -t iso", org.dbgl.util.ProfileChecks.withQuotes("imgmount D " + new File(cds, "dgate.iso").getPath() + " -t iso"));
		assertNull(org.dbgl.util.ProfileChecks.withQuotes("mount C " + new File(dir, "Gibt es nicht").getPath()));
		assertNull(org.dbgl.util.ProfileChecks.withQuotes("mount C \"" + spaces.getPath() + "\""));

		c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount c SPIELE -pr\nmount d \"" + games.getPath() + "\" -pr\nmount e SPIELE\n", null, null);
		List<org.dbgl.model.conf.mount.Mount> mounts = c.getAutoexec().getMountingpoints();
		assertTrue(mounts.get(0).toString(), org.dbgl.util.ProfileChecks.relativeToConf(mounts.get(0)));
		assertFalse(mounts.get(1).toString(), org.dbgl.util.ProfileChecks.relativeToConf(mounts.get(1))); // absolute: -pr does nothing
		assertFalse(mounts.get(2).toString(), org.dbgl.util.ProfileChecks.relativeToConf(mounts.get(2)));
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	/**
	 * The lines of [autoexec] as DOSBox runs them (with DBGL's own section markers), programs a DOSBox configuration starts before
	 * every game, and programs a batch file starts that are not in its folder.
	 */
	@Test
	public void testAutoexecRunLikeDosbox() throws IOException {
		File dir = java.nio.file.Files.createTempDirectory("dbglrun").toFile();
		File conf = new File(dir, "x.conf");
		java.nio.file.Files.writeString(conf.toPath(), "\uFEFF[sdl]\r\nfullscreen=false\r\n[autoexec]\r\n@echo off\r\nrem a remark\r\n@REM START\r\nC:\r\n@REM /START\r\nmount C .\r\n[next]\r\nx=1\r\n",
			java.nio.charset.StandardCharsets.UTF_8);
		assertEquals(List.of("echo off", "REM START", "C:", "REM /START", "mount C ."), org.dbgl.util.ProfileChecks.autoexecLines(conf));

		assertTrue(org.dbgl.util.ProfileChecks.isProgramCall("C:\\TOOLS\\MENU.EXE"));
		assertTrue(org.dbgl.util.ProfileChecks.isProgramCall("ctmouse"));
		assertTrue(org.dbgl.util.ProfileChecks.isProgramCall("call C:\\DOS\\START.BAT"));
		assertTrue(org.dbgl.util.ProfileChecks.isProgramCall("lh c:\\dos\\mouse"));
		for (String not: new String[] {"echo off", "keyb gr", "set BLASTER=A220", "path=Z:\\", "cls", "mount c .", "imgmount d x.iso -t iso", "Z:\\MIXER.COM", "config -set x", "C:", "REM START"})
			assertFalse(not, org.dbgl.util.ProfileChecks.isProgramCall(not));

		java.nio.file.Files.writeString(new File(dir, "GAME.EXE").toPath(), "MZ");
		java.nio.file.Files.writeString(new File(dir, "Lange Datei.exe").toPath(), "MZ");
		File bat = new File(dir, "START.BAT");
		java.nio.file.Files.writeString(bat.toPath(), "@echo off\r\nrem x\r\nGAME.EXE -fast\r\ngame.exe\r\nLANGED~1.EXE\r\nMISSING.EXE\r\nmem\r\nif exist X.EXE X.EXE\r\ncd SUB\r\nOTHER.EXE\r\n");
		List<String[]> missing = org.dbgl.util.ProfileChecks.batMissing(bat);
		assertEquals(1, missing.size()); // OTHER.EXE comes after "cd": another folder, not checked
		assertEquals("MISSING.EXE", missing.get(0)[0]);
		// alpha 50: an installer and launcher in one (a mod of Duke Nukem 3D): what it makes itself and what it asks for before
		// is not missing; a program that is missing is told once
		java.nio.file.Files.writeString(bat.toPath(), "@echo off\r\nif exist sst_full.ins goto menu\r\ncopy sst_full.dat sst_full.exe >nul\r\nsst_full.exe\r\ndel sst_full.exe\r\n"
				+ "copy /y sst_atom.dat SST_ATOM.EXE\r\nsst_atom\r\nif not exist ump.bat goto noump\r\ncall ump.bat /tcdetect\r\n:noump\r\nren old.dat NEW.COM\r\nnew.com\r\n"
				+ "echo x >made.bat\r\ncall made.bat\r\ngone.exe\r\ngone.exe /again\r\n:menu\r\ngame.exe\r\n");
		missing = org.dbgl.util.ProfileChecks.batMissing(bat);
		assertEquals("GONE.EXE", missing.stream().map(m -> m[0]).collect(java.util.stream.Collectors.joining(",")));
		// the program that starts the game: not one for Windows, if there is one for DOS (SST_TC.EXE next to SST_TC.BAT)
		byte[] pe = new byte[0x100];
		pe[0] = 'M';
		pe[1] = 'Z';
		pe[0x3c] = (byte)0x80;
		System.arraycopy("This program cannot be run in DOS mode.".getBytes(StandardCharsets.ISO_8859_1), 0, pe, 0x4e, 39);
		pe[0x80] = 'P';
		pe[0x81] = 'E';
		File winLauncher = new File(dir, "SST_TC.EXE"), dosLauncher = new File(dir, "SST_TC.BAT"), engine = new File(dir, "DUKE3D.EXE");
		java.nio.file.Files.write(winLauncher.toPath(), pe);
		java.nio.file.Files.writeString(dosLauncher.toPath(), "duke3d.exe\r\n");
		java.nio.file.Files.writeString(engine.toPath(), "MZ");
		assertEquals("pe", org.dbgl.util.ProfileChecks.programKind(winLauncher));
		assertEquals(1, org.dbgl.util.DosGameUtils.findMostLikelyMainIndex("SST TC", List.of(winLauncher, dosLauncher, engine)));
		assertEquals(0, org.dbgl.util.DosGameUtils.findMostLikelyMainIndex("SST TC", List.of(winLauncher))); // nothing else: taken, and told as a Windows program
		// as found in a collection of games: a program is called without its ending, and it is there or not; a line of plain
		// words is no call; a setup program is known by its name (GAMESET.EXE, SETMUSE.EXE), not one for Windows or a network
		java.nio.file.Files.writeString(new File(dir, "WRMS.EXE").toPath(), "MZ");
		java.nio.file.Files.writeString(new File(dir, "U8.COM").toPath(), "MZ");
		java.nio.file.Files.writeString(bat.toPath(), "@echo off\r\nblack\r\nwrms\r\nu8 -demo\r\ncls\r\npause >nul\r\nmode co80\r\ntype read.me | more\r\nloadfix -32 WRMS.EXE\r\nmoins d'avoir\r\n"
				+ "nouveau SOUNDSET.\r\ncopy a.dat tool.exe\r\ntool\r\nif not exist extra.bat goto x\r\ncall extra\r\n:x\r\n");
		assertEquals("BLACK", org.dbgl.util.ProfileChecks.batMissing(bat).stream().map(m -> m[0]).collect(java.util.stream.Collectors.joining(",")));
		// a "goto" that is sure is followed: the first start unpacks with programs that are gone afterwards (King's Quest VI);
		// IPXNET is a command of DOSBox, SMARTDRV one of DOS that DOSBox passes over
		java.nio.file.Files.writeString(bat.toPath(), "echo off\r\nif not exist seq.exe goto continue\r\nseq.exe >nul\r\ndel seq.exe\r\nsync.exe >nul\r\n:continue\r\nsmartdrv /Q\r\nIPXNET CONNECT %1\r\nwrms\r\n"
				+ "if exist wrms.exe goto done\r\nneverrun\r\n:done\r\nif \"%1\"==\"x\" goto other\r\nmaybe\r\n:other\r\ngoto end\r\nskipped\r\n:end\r\nlast\r\ngoto continue\r\nafter\r\n");
		assertEquals("MAYBE,LAST", org.dbgl.util.ProfileChecks.batMissing(bat).stream().map(m -> m[0]).collect(java.util.stream.Collectors.joining(",")));
		java.util.function.Function<String, String> setupOf = names -> {
			List<File> fl = java.util.Arrays.stream(names.split(",")).map(n -> new File(dir, n)).toList();
			int i = org.dbgl.util.DosGameUtils.findSetupIndex(fl);
			return i < 0 ? "-": fl.get(i).getName();
		};
		assertEquals("GAMESET.EXE", setupOf.apply("C.BAT,COMANCHE.EXE,GAMESET.EXE"));
		assertEquals("SETMUSE.EXE", setupOf.apply("BWING.EXE,INSTALL.EXE,SETMUSE.EXE,XWINGCD.BAT"));
		assertEquals("SBSETUP.EXE", setupOf.apply("GAME.EXE,SBSETUP.EXE,V2INTRO.EXE"));
		assertEquals("SETUP.EXE", setupOf.apply("GAMESET.EXE,INSTALL.EXE,SETUP.EXE"));
		assertEquals("-", setupOf.apply("DEVICE.COM,DIAGNOSE.EXE,MIXERSET.EXE,WINSETUP.EXE"));
		assertEquals("-", setupOf.apply("DOOM.EXE,IPXSETUP.EXE,SERSETUP.EXE"));
		assertEquals(1, org.dbgl.util.DosGameUtils.findMostLikelyMainIndex("Comanche", List.of(new File(dir, "C.BAT"), new File(dir, "COMANCHE.EXE"), new File(dir, "GAMESET.EXE"))));
		// which program starts the game, as its owner says: WG.EXE (it has its settings in WG.CFG), WOLF3D.EXE, ACES.COM
		java.util.function.BiFunction<String, String, String> mainOf = (title, names) -> {
			List<File> fl = java.util.Arrays.stream(names.split(",")).map(n -> new File(dir, n)).toList();
			return fl.get(org.dbgl.util.DosGameUtils.findMostLikelyMainIndex(title, fl)).getName();
		};
		java.nio.file.Files.writeString(new File(dir, "WG.CFG").toPath(), "-ds271\r\n");
		assertEquals("WG.EXE", mainOf.apply("Wings of Glory", "GLORY.EXE,INSTALL.EXE,RUNTEST.EXE,SNDTEST.EXE,WG.EXE,WINGS_OF.EXE"));
		assertEquals("WOLF3D.EXE", mainOf.apply("Wolfenstein 3D", "WOLF3D.EXE,WOLFEN.EXE"));
		assertEquals("DUKE3D.EXE", mainOf.apply("Duke Nukem 3D", "COMMIT.EXE,DUKE3D.EXE,NUKEM.EXE,SETUP.EXE"));
		assertEquals("ACES.COM", mainOf.apply("Aces of the Pacific", "ACES.COM,AP.EXE,INSTALL.EXE,INTRO.EXE,PS.EXE"));
		assertEquals("COLONIZE.BAT", mainOf.apply("Colonization", "CLOSING.EXE,COLDEMO.BAT,COLONIZE.BAT,INSTALL.EXE,MAPEDIT.EXE,OPENING.EXE,VICEROY.EXE"));
		assertEquals("JP.BAT", mainOf.apply("Jurassic Park", "INSTALL.BAT,JP.BAT,UNISOUND.COM"));
		assertEquals("DARK.EXE", mainOf.apply("Star Wars: Dark Forces", "BOOTMKR.EXE,DARK.EXE,DOS4GW.EXE,IMUSE.EXE,SETUP.EXE"));
		assertEquals("HMISETUP.EXE", setupOf.apply("DOS4GW.EXE,HMISETUP.EXE,INSTALL.EXE,RAN2.BAT"));
		assertEquals("SIERRA.COM", mainOf.apply("Space Quest III", "EXISTS.COM,INSTALL.EXE,SCIV.EXE,SIERRA.COM,__INSTH.BAT"));
		assertEquals("SQ4.BAT", mainOf.apply("Space Quest IV", "INSTALL.EXE,SIERRA.EXE,SQ4.BAT"));
		assertEquals("TV.EXE", mainOf.apply("Terminal Velocity", "ARIA.COM,COMMIT.EXE,GAME.EXE,GF166.COM,INSTALL.EXE,NOSOUND.COM,SB16.COM,SETUP.EXE,TV.EXE"));
		java.nio.file.Files.writeString(new File(dir, "U7.CFG").toPath(), "x");
		assertEquals("ULTIMA7.COM", mainOf.apply("Ultima VII: The Black Gate", "ENDGAME.EXE,INSTALL.EXE,INTRO.EXE,MAINMENU.EXE,U7.BAT,U7.EXE,ULTIMA7.COM")); // not U7.EXE for its U7.CFG
		// the sound settings other games write, as found: HMI (Dynamix), the switches of Origin, Build, the batch file of Arena
		assertEquals("A220 I7 D1", org.dbgl.util.GameHints.hmiCfg("RESOURCE.CFG", " soundDrv  = ADL.DRV\r\n[SoundFX]\r\nDriver#=E015\r\nDMA=1\r\nIRQ=7\r\nPortAddress=220\r\n[Music]\r\nDriver#=A001\r\nPortAddress=330\r\n").blaster());
		assertNull(org.dbgl.util.GameHints.hmiCfg("RESOURCE.CFG", "[SoundFX]\r\nDriver#=E023\r\nDMA=1\r\nIRQ=7\r\nPortAddress=220\r\n")); // another card
		assertNull(org.dbgl.util.GameHints.hmiCfg("RESOURCE.CFG", " soundDrv  = ADL.DRV\r\n"));
		assertEquals("A220 I7 D1", org.dbgl.util.GameHints.hmiSet("HMISET.CFG", "\r\n[DIGITAL]\r\nDeviceName  = Sound Blaster AWE32          \r\nDeviceIRQ   = 7\r\nDeviceDMA   = 1\r\nDevicePort  = 0x220\r\nDeviceID    = 0xe016\r\n\r\n[MIDI]\r\nDeviceName  = MPU-401\r\nDevicePort  = 0x330\r\nDeviceID    = 0xa001\r\n").blaster());
		assertEquals("A220 I7 H5", org.dbgl.util.GameHints.hmiSet("MAX.INI", "[MODEM]\r\nmodem_irq=4\r\n[DIGITAL]\r\nDevice_Name=Sound Blaster 16/AWE32\r\nDevice_IRQ=7\r\nDevice_DMA=5\r\nDevice_Port=0x220\r\nDevice_ID=0xe018\r\n").blaster());
		assertEquals("A220 I7 D1", org.dbgl.util.GameHints.hmiSet("CONFIG.INI", "CDTransferRate=3236\r\nSoundCard=0xe016\r\nSoundPort=0x220\r\nSoundIRQ=7\r\nSoundDMA=1\r\nMusicCard=0xa001\r\nMusicPort=0x330\r\n").blaster());
		assertNull(org.dbgl.util.GameHints.hmiSet("HMISET.CFG", "[DIGITAL]\r\nDeviceIRQ = 11\r\nDeviceDMA = 1\r\nDevicePort = 0x220\r\nDeviceID = 0xe023\r\n")); // a Gravis Ultrasound
		assertNull(org.dbgl.util.GameHints.hmiSet("WIN.INI", "[windows]\r\nload=\r\nirq=5\r\n"));
		assertEquals("A220 I7 D1", org.dbgl.util.GameHints.otherCfg("FB.CFG", "*  Einstellung Flashback PC  V 1.1 \r\nSFX = 0 \r\nBLASTER = A220 I7 D1\r\nMUSIC = 2 \r\n").blaster());
		assertEquals("A220 I7 D1", org.dbgl.util.GameHints.otherCfg("SC2000.CFG", "MUSICDRIVER=A32MT32.DLL\r\nMUSICIRQ=*\r\n[Sound Effekte]\r\nSFXNAME=Sound Blaster 16\r\nSFXDRIVER=A32SBPDG.DLL\r\nSFXADDRESS=220\r\nSFXIRQ=7\r\nSFXDMA=1\r\nSFXDRQ=*\r\n").blaster());
		assertEquals("A220", org.dbgl.util.GameHints.otherCfg("SC2000.CFG", "SFXDRIVER=A32SBDG.DLL\r\nSFXADDRESS=220\r\nSFXIRQ=*\r\nSFXDMA=*\r\n").blaster());
		assertEquals("A220 I7 D1", org.dbgl.util.GameHints.otherCfg("TURRICAN.CFG", "CardName=Creative Labs SoundBlaster PRO\r\nSoundDriver=SBPRO.MSD\r\nPort=544\r\nInterrupt=7\r\nDMAChannel=1\r\n").blaster());
		assertNull(org.dbgl.util.GameHints.otherCfg("GAME.CFG", "Port=544\r\nInterrupt=7\r\n"));
		assertEquals("A220 I7 D1", org.dbgl.util.GameHints.originCfg("armada.cfg", "-w\r\n=data.tre\r\n-mg330\r\n-ds271\r\n").blaster());
		assertEquals("A240 I10 D3", org.dbgl.util.GameHints.originCfg("x.cfg", "-ds4103\n").blaster());
		assertNull(org.dbgl.util.GameHints.originCfg("WG.CFG", "#so, -ds251 means: address 220, IRQ 5, DMAchannel 1\r\n-d6271\r\n")); // a comment, and another switch
		assertEquals("sb ", org.dbgl.util.GameHints.buildCfg("BLOOD.CFG", "[Sound Setup]\r\nFXDevice = 0\r\nMusicDevice = 4\r\nBlasterInterrupt = 5\r\n").device() + " "
				+ org.dbgl.util.GameHints.buildCfg("BLOOD.CFG", "[Sound Setup]\r\nFXDevice = 0\r\nBlasterInterrupt = 5\r\n").blaster());
		assertNull(org.dbgl.util.GameHints.buildCfg("BLOOD.CFG", "[Sound Setup]\r\nFXDevice = 13\r\n"));
		assertNull(org.dbgl.util.GameHints.buildCfg("OTHER.CFG", "FXDevice = 0\r\n"));
		assertEquals("A220 I7 D1", org.dbgl.util.GameHints.switchesLine("ARENA.BAT", "A -sa:220 -si:7 -sd:1 -ma:330 -mq:2 -md:1 -ssbdig.adv -mgenmidi.adv").blaster());
		assertNull(org.dbgl.util.GameHints.switchesLine("ARENA.BAT", "A -sa:220 -si:7 -sd:1 -sgusdig.adv")); // not a Sound Blaster
		assertNull(org.dbgl.util.GameHints.switchesLine("ARENA.BAT", "rem A -sa:220 -si:5 -sd:1 -ssbdig.adv"));
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	/**
	 * "Check profiles", the preview: settings compared as "[section] item=value" (a setting moved to another section is one
	 * gone, one new), [autoexec] line by line; the key to hide one message for good stays the same while its line does.
	 */
	/**
	 * A file written on Windows with umlauts (Windows-1252), read where the system uses UTF-8: saved in Windows-1252 again (read
	 * as UTF-8 before, "Ä" became U+FFFD and was saved as EF BF BD); a UTF-8 file and an ASCII file stay as they are.
	 */
	@Test
	public void testCharsetKeptOnSave() throws IOException {
		org.junit.Assume.assumeTrue(java.nio.charset.Charset.defaultCharset().equals(StandardCharsets.UTF_8));
		File dir = java.nio.file.Files.createTempDirectory("dbglcharset").toFile();
		String text = "[sdl]\r\nfullscreen=true\r\n[dosbox]\r\ncaptures=Aufnahmen \u00C4\r\n";
		java.nio.charset.Charset cp1252 = java.nio.charset.Charset.forName("windows-1252");
		Object[][] cases = {{cp1252, cp1252}, {StandardCharsets.UTF_8, null}, {StandardCharsets.US_ASCII, null}};
		for (Object[] cs: cases) {
			String content = cs[0] == StandardCharsets.US_ASCII ? text.replace("\u00C4", "A"): text;
			File conf = new File(dir, "test.conf");
			byte[] bytes = content.getBytes((java.nio.charset.Charset)cs[0]);
			java.nio.file.Files.write(conf.toPath(), bytes);
			Configuration c = new Configuration();
			c.setFileLocation(new org.dbgl.model.FileLocation(conf.getPath()));
			c.loadWithAutoexec(GenericTextService.getInstance(), null, null);
			assertEquals(cs[1], c.getCharset());
			assertEquals(content.contains("\u00C4") ? "Aufnahmen \u00C4": "Aufnahmen A", c.getValue("dosbox", "captures"));
			Configuration copy = new Configuration(c);
			assertEquals(cs[1], copy.getCharset()); // a copy (e.g. the temporary file for starting) is written the same way
			c.save(false, null);
			String saved = new String(java.nio.file.Files.readAllBytes(conf.toPath()), (java.nio.charset.Charset)cs[0]);
			assertTrue(saved, saved.contains("captures=" + (content.contains("\u00C4") ? "Aufnahmen \u00C4": "Aufnahmen A")));
			assertFalse(new String(java.nio.file.Files.readAllBytes(conf.toPath()), StandardCharsets.ISO_8859_1).contains("\u00EF\u00BF\u00BD"));
		}
		// converted on request: UTF-8
		File conf = new File(dir, "test.conf");
		java.nio.file.Files.write(conf.toPath(), text.getBytes(cp1252));
		Configuration c = new Configuration();
		c.setFileLocation(new org.dbgl.model.FileLocation(conf.getPath()));
		c.loadWithAutoexec(GenericTextService.getInstance(), null, null);
		c.setCharset(null);
		c.save(false, null);
		assertTrue(java.nio.file.Files.readString(conf.toPath(), StandardCharsets.UTF_8).contains("captures=Aufnahmen \u00C4"));
		// a character Windows-1252 does not have: the whole file in UTF-8, nothing lost
		java.nio.file.Files.write(conf.toPath(), text.getBytes(cp1252));
		c = new Configuration();
		c.setFileLocation(new org.dbgl.model.FileLocation(conf.getPath()));
		c.loadWithAutoexec(GenericTextService.getInstance(), null, null);
		c.setValue("dosbox", "captures", "\u00C4 \u0141");
		c.save(false, null);
		assertTrue(java.nio.file.Files.readString(conf.toPath(), StandardCharsets.UTF_8).contains("captures=\u00C4 \u0141"));

		// only the lines concerned: a value set in the charset of the file
		org.dbgl.util.ProfileFindings.ConfText t = org.dbgl.util.ProfileFindings.ConfText.of(new String(text.getBytes(cp1252), StandardCharsets.ISO_8859_1), cp1252);
		t.set("sdl", "output", "\u00D6");
		org.junit.Assert.assertArrayEquals(("[sdl]\r\nfullscreen=true\r\noutput=\u00D6\r\n[dosbox]\r\ncaptures=Aufnahmen \u00C4\r\n").getBytes(cp1252), t.text().getBytes(StandardCharsets.ISO_8859_1));
		t = org.dbgl.util.ProfileFindings.ConfText.of(new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
		t.set("sdl", "output", "\u00D6");
		assertEquals("[sdl]\r\nfullscreen=true\r\noutput=\u00D6\r\n[dosbox]\r\ncaptures=Aufnahmen \u00C4\r\n", new String(t.text().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));

		// the comment lines writing as a whole drops: told in the preview
		List<org.dbgl.util.ProfileFindings.DiffLine> d = org.dbgl.util.ProfileFindings.diff("# mine\n[sdl]\n# fast\nfullscreen=true\n", "[sdl]\nfullscreen=true\n");
		assertEquals(List.of("-# mine", " [sdl]", "-# fast"), d.stream().map(l -> l.kind() + l.text()).toList());
	}

	@Test
	public void testFindingsDiffAndDismissKey() {
		String before = "[sblaster]\r\noplemu=fast\r\nsbtype=sb16\r\n[dos]\r\nmemsize=16\r\n\r\n[autoexec]\r\nmount C \"x\"\r\nC:\r\ncd \\DEATHGATE\r\nGAME.EXE\r\n";
		String after = "[sblaster]\r\nsbtype=sb16\r\n[dosbox]\r\nmemsize=16\r\n\r\n[autoexec]\r\nmount C \"x\"\r\nC:\r\ncd \\DEATHG~1\r\nGAME.EXE\r\n";
		List<org.dbgl.util.ProfileFindings.DiffLine> d = org.dbgl.util.ProfileFindings.diff(before, after);
		// [sblaster] stays (sbtype), [dos] is gone, [dosbox] is new
		// [autoexec]: with up to two lines around a change, so that it can be told where it is
		assertEquals(List.of(" [sblaster]", "-oplemu=fast", "-[dos]", "-memsize=16", "+[dosbox]", "+memsize=16", " [autoexec]", " mount C \"x\"", " C:", "-cd \\DEATHGATE", "+cd \\DEATHG~1",
			" GAME.EXE"), d.stream().map(l -> l.kind() + l.text()).toList());
		// far apart: the lines between left out, told once
		List<String> a = List.of("E:", "1", "2", "3", "4", "5", "6", "7"), b = List.of("1", "2", "3", "4", "5", "6", "E:", "7");
		assertEquals(List.of("-E:", " 1", " 2", " " + org.dbgl.util.ProfileFindings.GAP, " 5", " 6", "+E:", " 7"),
			org.dbgl.util.ProfileFindings.diffLines(a, b).stream().map(l -> l.kind() + l.text()).toList());
		assertTrue(org.dbgl.util.ProfileFindings.diff(before, before).isEmpty());
		// all of the file: the lines that stay too, in their place
		assertEquals(List.of(" [sblaster]", "-oplemu=fast", " sbtype=sb16", "-[dos]", "-memsize=16", "+[dosbox]", "+memsize=16", " [autoexec]", " mount C \"x\"", " C:", "-cd \\DEATHGATE",
			"+cd \\DEATHG~1", " GAME.EXE"), org.dbgl.util.ProfileFindings.diff(before, after, null, true).stream().map(l -> l.kind() + l.text()).toList());
		assertTrue(org.dbgl.util.ProfileFindings.diff(before, before.replace("\r\n\r\n", "\r\n")).isEmpty()); // empty lines do not count

		// only the lines concerned: a section left empty goes, the rest stays as it was (line ends too)
		org.dbgl.util.ProfileFindings.ConfText t = org.dbgl.util.ProfileFindings.ConfText.of(before);
		t.remove("sblaster", "oplemu");
		t.remove("dos", "memsize");
		t.set("dosbox", "memsize", "16");
		assertEquals("[sblaster]\r\nsbtype=sb16\r\n\r\n[dosbox]\r\nmemsize=16\r\n\r\n[autoexec]\r\nmount C \"x\"\r\nC:\r\ncd \\DEATHGATE\r\nGAME.EXE\r\n", t.text());
		org.junit.Assert.assertArrayEquals(new String[] {"sblaster", "oplemu"}, org.dbgl.util.ProfileFindings.setting("[sblaster] oplemu"));
		assertNull(org.dbgl.util.ProfileFindings.setting("oplemu"));
		org.junit.Assert.assertArrayEquals(new String[] {"dos", "minimum mcb free"}, org.dbgl.util.ProfileFindings.setting("[dos] minimum mcb free"));
		org.junit.Assert.assertArrayEquals(new String[] {"ide, secondary", "enable"}, org.dbgl.util.ProfileFindings.setting("[ide, secondary] enable = true"));

		String k1 = org.dbgl.util.ProfileFindings.dismissKey(null, null, "dosboxconfprogram", new Object[] {"C:\\TOOLS\\MENU.EXE"});
		assertEquals(k1, org.dbgl.util.ProfileFindings.dismissKey(null, null, "dosboxconfprogram", new Object[] {"C:\\TOOLS\\MENU.EXE"}));
		org.junit.Assert.assertNotEquals(k1, org.dbgl.util.ProfileFindings.dismissKey(null, null, "dosboxconfprogram", new Object[] {"C:\\TOOLS\\MENU.EXE /fast"}));
		org.junit.Assert.assertNotEquals(k1, org.dbgl.util.ProfileFindings.dismissKey(null, null, "batmissing", new Object[] {"C:\\TOOLS\\MENU.EXE"}));
		java.util.Set<String> keys = java.util.Set.of(k1), none = java.util.Set.of();
		assertTrue(org.dbgl.util.ProfileFindings.isDismissed(null, null, "dosboxconfprogram", new Object[] {"C:\\TOOLS\\MENU.EXE"}, keys, none));
		assertTrue(org.dbgl.util.ProfileFindings.isDismissed(null, null, "batmissing", new Object[] {"X"}, none, java.util.Set.of("batmissing")));
		assertFalse(org.dbgl.util.ProfileFindings.isDismissed(null, null, "batmissing", new Object[] {"X"}, keys, none));
	}

	/** the "cd" told when the game is on the profile's drive that DOSBox skips: "\DGATE" only for names DOS takes as they are */
	@Test
	public void testCdForHiddenDrive() {
		assertEquals("\\", org.dbgl.util.ProfileChecks.dosDir(""));
		assertEquals("\\GAMES\\DGATE", org.dbgl.util.ProfileChecks.dosDir("GAMES/DGATE/"));
		assertTrue(org.dbgl.util.ProfileChecks.isDosName("DGATE"));
		assertTrue(org.dbgl.util.ProfileChecks.isDosName("GAMES/C&C"));
		assertFalse(org.dbgl.util.ProfileChecks.isDosName("Death Gate"));
		assertFalse(org.dbgl.util.ProfileChecks.isDosName("DEATHGATE1"));
		String base = File.separator + "spiele" + File.separator + "SPIELE";
		assertEquals("DGATE", org.dbgl.util.ProfileChecks.subPathIgnoringCase(base, base + File.separator + "DGATE"));
		assertNull(org.dbgl.util.ProfileChecks.subPathIgnoringCase(base, base + "X" + File.separator + "DGATE"));
		assertNull(org.dbgl.util.ProfileChecks.subPathIgnoringCase(base, base));
	}

	/** "Check profiles": Windows programs told from DOS programs by their header, files a cue sheet names that are missing */
	@Test
	public void testProfileChecksFiles() throws IOException {
		File dir = java.nio.file.Files.createTempDirectory("dbglchecks").toFile();
		java.util.function.BiFunction<String, byte[], File> exe = (name, sig) -> {
			byte[] b = new byte[0x100];
			b[0] = 'M';
			b[1] = 'Z';
			if (sig != null) {
				b[0x3C] = (byte)0x80;
				System.arraycopy(sig, 0, b, 0x80, Math.min(sig.length, 0x40));
				if (sig.length > 0x40) // a DOS extender's name in the DOS part
					System.arraycopy(sig, 0x40, b, 0x40, sig.length - 0x40);
			}
			File f = new File(dir, name);
			try {
				java.nio.file.Files.write(f.toPath(), b);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return f;
		};
		assertNull(org.dbgl.util.ProfileChecks.programKind(exe.apply("DOS.EXE", null)));
		assertNull(org.dbgl.util.ProfileChecks.programKind(exe.apply("DOS4GW.EXE", new byte[] {'L', 'E', 0, 0}))); // DOS extender
		assertEquals("pe", org.dbgl.util.ProfileChecks.programKind(exe.apply("WIN.EXE", new byte[] {'P', 'E', 0, 0})));
		byte[] ne = new byte[0x40];
		ne[0] = 'N';
		ne[1] = 'E';
		ne[0x36] = 2; // for Windows
		assertEquals("ne", org.dbgl.util.ProfileChecks.programKind(exe.apply("WIN16.EXE", ne)));
		byte[] neDos = ne.clone();
		neDos[0x36] = 1; // OS/2 or none: a 16 bit DOS extender (Phar Lap 286, Borland RTM)
		assertNull(org.dbgl.util.ProfileChecks.programKind(exe.apply("PHAR.EXE", neDos)));
		byte[] dos16m = java.util.Arrays.copyOf(ne, 0x40 + 8); // Windows by its header, but DOS/16M in its DOS part (e.g. Ultima VIII)
		System.arraycopy("DOS/16M ".getBytes(), 0, dos16m, 0x40, 8);
		assertNull(org.dbgl.util.ProfileChecks.programKind(exe.apply("U8.EXE", dos16m)));
		byte[] wdosx = java.util.Arrays.copyOf(new byte[] {'P', 'E', 0, 0}, 0x40 + 5);
		System.arraycopy("WDOSX".getBytes(), 0, wdosx, 0x40, 5);
		assertNull(org.dbgl.util.ProfileChecks.programKind(exe.apply("WDOSX.EXE", wdosx)));

		// "dual mode" (setup programs of Westwood, Cryo): a Windows header, but the DOS part is a whole DOS program
		java.util.function.BiFunction<String, String, File> dual = (name, stubText) -> {
			byte[] b = new byte[48 * 1024];
			b[0] = 'M';
			b[1] = 'Z';
			b[4] = 80; // 80 pages of 512 bytes: 40 KB DOS program
			b[8] = 2; // header of 2 paragraphs
			System.arraycopy(stubText.getBytes(), 0, b, 0x40, stubText.length());
			int at = 41 * 1024;
			b[0x3C] = (byte)(at & 0xFF);
			b[0x3D] = (byte)(at >> 8);
			b[at] = 'N';
			b[at + 1] = 'E';
			b[at + 0x36] = 2;
			File f = new File(dir, name);
			try {
				java.nio.file.Files.write(f.toPath(), b);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return f;
		};
		assertNull(org.dbgl.util.ProfileChecks.programKind(dual.apply("SETUP.EXE", "Westwood setup program")));
		assertEquals("ne", org.dbgl.util.ProfileChecks.programKind(dual.apply("WINONLY.EXE", "This program requires Microsoft Windows.")));

		// a small DOS part that starts the DOS version next to it (Lost Eden's INSTALL.EXE: 666 bytes, runs INSTDOS.EXE)
		byte[] starter = new byte[0x500];
		starter[0] = 'M';
		starter[1] = 'Z';
		starter[2] = (byte)154; // last page: 154 bytes
		starter[4] = 3; // 3 pages
		starter[8] = 32; // header of 32 paragraphs (512 bytes): 666 bytes of DOS program
		System.arraycopy("INSTDOS.EXE WINPATH=\0Cannot execute INSTDOS.EXE!".getBytes(), 0, starter, 0x300, 47);
		starter[0x3C] = 0x00;
		starter[0x3D] = 0x04; // NE header at 0x400
		starter[0x400] = 'N';
		starter[0x401] = 'E';
		starter[0x436] = 2;
		File starterFile = new File(dir, "INSTALL.EXE");
		java.nio.file.Files.write(starterFile.toPath(), starter);
		assertNull(org.dbgl.util.ProfileChecks.programKind(starterFile));
		System.arraycopy("This program requires Microsoft Windows.$".getBytes(), 0, starter, 0x300, 41); // the same size, Windows only
		java.nio.file.Files.write(starterFile.toPath(), starter);
		assertEquals("ne", org.dbgl.util.ProfileChecks.programKind(starterFile));
		File txt = new File(dir, "README.TXT");
		java.nio.file.Files.writeString(txt.toPath(), "PE".repeat(100));
		assertNull(org.dbgl.util.ProfileChecks.programKind(txt));

		File cue = new File(dir, "game.cue");
		java.nio.file.Files.writeString(cue.toPath(), "FILE \"GAME.BIN\" BINARY\r\n  TRACK 01 MODE1/2352\r\nFILE track02.wav WAVE\r\nFILE \"sub\\Track 03.ogg\" OGG\r\n");
		java.nio.file.Files.writeString(new File(dir, "game.bin").toPath(), "x"); // other case: DOSBox finds it too
		assertEquals(List.of("track02.wav", "sub\\Track 03.ogg"), org.dbgl.util.ProfileChecks.missingCueFiles(cue));
		new File(dir, "sub").mkdir();
		java.nio.file.Files.writeString(new File(dir, "sub/Track 03.ogg").toPath(), "x");
		java.nio.file.Files.writeString(new File(dir, "TRACK02.WAV").toPath(), "x");
		assertEquals(List.of(), org.dbgl.util.ProfileChecks.missingCueFiles(cue));
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	/**
	 * "Check profiles", repair missing paths, read the changed configuration on top of the loaded one: every mount came twice,
	 * and once more on every repair. Now it replaces the configuration, and mount lines repeated by that are removed.
	 */
	@Test
	public void testRelocationDoesNotRepeatMounts() throws IOException {
		java.util.function.Function<List<String>, String> conf = l -> "[capture]\ncapture_dir=..\\captures\\151\n\n[autoexec]\n" + String.join("\n", l) + "\n";
		String cMount = "mount C \"games\\DGATE\"", oldCd = "mount D \"iso\\death_~1.iso\" -t cdrom", newCd = "mount D \"games\\DGATE\\iso\\DEATH_GATE.ISO\" -t cdrom";
		List<String> start = List.of(cMount, oldCd, "C:", "cd \\", "DGATE.EXE", "exit");
		java.util.Map<String, String> moves = java.util.Map.of("iso\\death_~1.iso", "games\\DGATE\\iso\\DEATH_GATE.ISO");
		File dosroot = new File(".").getAbsoluteFile();

		Configuration c = new Configuration();
		c.loadDataWithAutoexec(GenericTextService.getInstance(), conf.apply(start), null, null);
		for (int i = 0; i < 3; i++) { // repaired three times
			String updated = org.dbgl.util.PathRelocation.replaceInConfiguration(c.toString(null), moves, dosroot);
			c.replaceDataWithAutoexec(GenericTextService.getInstance(), updated, null, null);
			c.getAutoexec().removeRepeatedMounts();
		}
		String written = c.toString(null);
		assertEquals(written, 2, c.getAutoexec().getMountingpoints().size());
		assertEquals(written, 1, written.split("DEATH_GATE.ISO", -1).length - 1);
		assertFalse(written, written.contains("death_~1"));
		assertEquals(written, 1, written.split("DGATE.EXE", -1).length - 1);
		assertTrue(written, written.contains("capture_dir"));

		// a profile already spoiled that way: repaired once more, it is clean again
		List<String> spoiled = List.of(cMount, oldCd, cMount, newCd, cMount, newCd, cMount, newCd, "C:", "cd \\", "DGATE.EXE", "exit");
		Configuration s = new Configuration();
		s.loadDataWithAutoexec(GenericTextService.getInstance(), conf.apply(spoiled), null, null);
		// "Check profiles" reports them: C 3 and the new D 2 more times (removable), the new D skipped by DOSBox as the old D comes first
		assertEquals(5, s.getAutoexec().repeatedMounts().size());
		// none of the images is there in this test: no line takes drive D, so DOSBox skips none (a failing mount leaves the
		// drive free, see testFailingMountLeavesDriveFree)
		assertEquals(0, s.getAutoexec().mountsOfDrivesMountedAlready().size());
		// unmounted in between: mounting again is fine
		org.dbgl.model.conf.Autoexec u = new org.dbgl.model.conf.Autoexec();
		u.load(List.of(cMount, oldCd, "mount -u D", oldCd, "mount -u D", newCd), null, null);
		assertEquals(u.getMountingpoints().toString(), 0, u.repeatedMounts().size() + u.mountsOfDrivesMountedAlready().size());
		String updated = org.dbgl.util.PathRelocation.replaceInConfiguration(s.toString(null), moves, dosroot);
		s.replaceDataWithAutoexec(GenericTextService.getInstance(), updated, null, null);
		s.getAutoexec().removeRepeatedMounts();
		assertEquals(s.toString(null), 2, s.getAutoexec().getMountingpoints().size());
	}

	/**
	 * Writing the configuration of a DOSBox again (after an update) replaced the whole file, and with it a "mount C ..." in its
	 * [autoexec] that all profiles relied on: every profile lost its drive C and its game. The [autoexec] and the user's values
	 * are taken over now; new settings and comments come from the new file.
	 */
	@Test
	public void testRewrittenDosboxConfKeepsAutoexecAndValues() throws IOException {
		File dir = Files.createTempDirectory("dbgl-merge").toFile();
		File previous = new File(dir, "dosbox-x.conf.bak"), fresh = new File(dir, "dosbox-x.conf");
		String old = Files.readString(new File(DATA_DIR, "dosbox-x-2026.10.01.conf").toPath(), StandardCharsets.ISO_8859_1)
				.replaceFirst("(?m)^(\\s*fullscreen\\s*=\\s*).*$", "$1true")
				.replace("[autoexec]", "[autoexec]\r\nmount C \"/Volumes/Games/DOS\"\r\nimgmount D \"/Volumes/Games/CDs/game.cue\" -t iso");
		Files.writeString(previous.toPath(), old, StandardCharsets.ISO_8859_1);
		String newer = Files.readString(new File(DATA_DIR, "dosbox-x-2026.10.01.conf").toPath(), StandardCharsets.ISO_8859_1) + "\r\n[brandnew]\r\nsetting = 1\r\n";
		Files.writeString(fresh.toPath(), newer, StandardCharsets.ISO_8859_1);
		org.dbgl.util.ConfMerge.keepPrevious(previous, fresh);
		String merged = Files.readString(fresh.toPath(), StandardCharsets.ISO_8859_1);
		assertTrue(merged.contains("mount C \"/Volumes/Games/DOS\""));
		assertTrue(merged.contains("imgmount D \"/Volumes/Games/CDs/game.cue\" -t iso"));
		assertTrue(java.util.regex.Pattern.compile("(?m)^\\s*fullscreen\\s*=\\s*true\\s*$").matcher(merged).find());
		assertTrue(merged.contains("[brandnew]") && merged.contains("setting = 1"));
		assertEquals(1, java.util.regex.Pattern.compile("(?m)^\\[autoexec\\]\\s*$").matcher(merged).results().count());
		org.apache.commons.io.FileUtils.deleteQuietly(dir);
	}

	/** mount lines are written again exactly as long as they are not changed; parameters DBGL does not know are kept */
	@Test
	public void testMountLinesKeptExactly() throws Exception {
		// written exactly as read, whatever the spelling
		for (String line: new String[] {"MOUNT c games\\KEEN -freesize 1024", "mount C \"games\" -t dir", "imgmount d \"cd\\game.cue\" -t cdrom",
			"mount D \"a.iso\" \"b.iso\" -t cdrom", "mount -u d"})
			assertEquals(line, org.dbgl.model.factory.MountFactory.create(line).toString());

		// parameters DBGL does not know: read without them, kept as further parameters
		org.dbgl.model.conf.mount.Mount m = org.dbgl.model.factory.MountFactory.create("mount C \"games\\KEEN\" -ro -label KEEN");
		assertTrue(m instanceof org.dbgl.model.conf.mount.DirMount);
		assertEquals("KEEN", m.getLabel());
		assertEquals("-ro", m.getExtraOptions());
		assertEquals("mount C \"games\\KEEN\" -ro -label KEEN", m.toString());
		m = org.dbgl.model.factory.MountFactory.create("mount D \"a.iso\" \"b.iso\" -t cdrom -nosuchoption 42");
		assertTrue(m instanceof org.dbgl.model.conf.mount.ImageMount);
		assertEquals("-nosuchoption 42", m.getExtraOptions());
		m = org.dbgl.model.factory.MountFactory.create("mount E \"games\" -t hdd");
		assertEquals("-t hdd", m.getExtraOptions());

		// changed: written by DBGL, with the further parameters
		org.dbgl.model.conf.mount.DirMount d = (org.dbgl.model.conf.mount.DirMount)org.dbgl.model.factory.MountFactory.create("mount C \"games\\KEEN\" -ro -label KEEN");
		d.setLabel("KEEN2");
		String changed = d.toString();
		assertTrue(changed, changed.contains("-label KEEN2") && changed.contains("-ro"));

		// as the mount dialog does it: a new object, unchanged -> the line stays exactly
		org.dbgl.model.conf.mount.Mount previous = org.dbgl.model.factory.MountFactory.create("MOUNT c games -ro");
		org.dbgl.model.conf.mount.Mount edited = org.dbgl.model.factory.MountFactory.createDirMount("C", "games", "", "", null, null, null);
		edited.setExtraOptions("-ro");
		edited.takeOverFrom(previous);
		assertEquals("MOUNT c games -ro", edited.toString());

		// a whole profile: nothing of the [autoexec] mounts is lost
		org.dbgl.model.conf.Autoexec a = new org.dbgl.model.conf.Autoexec();
		a.load(List.of("mount C \"games\\KEEN\" -ro", "imgmount D \"cd\\game.cue\" -t iso -nosuchoption", "c:", "keen1.exe"), null, null);
		assertEquals(2, a.getMountingpoints().size());
		assertTrue(a.getUnknownLines().isEmpty());
		String written = a.toString(false, null);
		assertTrue(written, written.contains("mount C \"games\\KEEN\" -ro") && written.contains("imgmount D \"cd\\game.cue\" -t iso -nosuchoption"));
	}

	/**
	 * Warning signs with the tooltip "null": the check read configurations again while the window used the same DOSBox
	 * versions. Checks now use their own copies, and a failure while checking is never shown as a problem.
	 */
	@Test
	public void testProfileWarningsWhileDosboxVersionReloads() throws Exception {
		File dir = Files.createTempDirectory("dbgl-warnings").toFile();
		File dosboxConf = new File(dir, "dosbox.conf");
		Files.copy(new File(DATA_DIR, "dosbox-staging-0.83.0.conf").toPath(), dosboxConf.toPath());
		org.dbgl.model.aggregate.DosboxVersion dbv = DosboxVersionFactory.create(9000, "Staging", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, dir.getPath(), "", "",
			dosboxConf.getPath(), null, null, null, 0);
		dbv.resetAndLoadConfiguration();
		List<org.dbgl.model.aggregate.Profile> profiles = new java.util.ArrayList<>();
		java.util.Map<Integer, List<String>> expected = new java.util.HashMap<>();
		for (int i = 0; i < 6; i++) {
			org.dbgl.model.aggregate.Profile p = org.dbgl.model.factory.ProfileFactory.create(dbv, null);
			p.setId(9000 + i);
			p.setConfigurationFileLocation(new File(dir, i + ".conf").getPath());
			if (i % 2 == 0)
				p.getConfiguration().setValue("sdl", "output", "overlay"); // invalid in Staging 0.83
			p.saveConfiguration();
			profiles.add(p);
			org.dbgl.util.ProfileWarnings.forget(p.getId());
			expected.put(p.getId(), org.dbgl.util.ProfileWarnings.check(p));
		}
		assertFalse(expected.get(9000).isEmpty());
		java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
		Thread window = new Thread(() -> {
			while (!stop.get()) {
				try {
					dbv.resetAndLoadConfiguration();
					dbv.getConfigurationString();
				} catch (Exception e) {
					// only the checks matter here
				}
			}
		});
		window.start();
		try {
			for (int round = 0; round < 20; round++) {
				for (org.dbgl.model.aggregate.Profile p: profiles) {
					org.dbgl.util.ProfileWarnings.forget(p.getId());
					List<String> problems = org.dbgl.util.ProfileWarnings.check(p);
					assertFalse(problems.contains("null"));
					assertEquals(expected.get(p.getId()), problems);
				}
			}
		} finally {
			stop.set(true);
			window.join();
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
	}

	/** the update check of the Extended Edition: version order, pre-releases (GitHub "latest" would leave them out), drafts */
	@Test
	public void testEditionUpdateCheck() {
		java.util.function.BiFunction<String, String, Integer> cmp = org.dbgl.util.EditionUpdateChecker::compare;
		assertTrue(cmp.apply("v0.101-alpha31", "0.101alpha30") > 0);
		assertTrue(cmp.apply("v0.101-alpha30", "0.101alpha30") == 0);
		assertTrue(cmp.apply("v0.101-alpha9", "0.101alpha30") < 0); // 9 < 30, not compared as text
		assertTrue(cmp.apply("v0.101-beta1", "0.101alpha30") > 0);
		assertTrue(cmp.apply("v0.101-rc1", "0.101beta5") > 0);
		assertTrue(cmp.apply("v0.101", "0.101rc2") > 0);
		assertTrue(cmp.apply("v0.101.1", "0.101") > 0);
		assertTrue(cmp.apply("v0.102-alpha1", "0.101.1") > 0);
		assertTrue(cmp.apply("0.101 alpha 31", "0.101alpha30") > 0);
		assertEquals("0.101 alpha 31", org.dbgl.util.EditionUpdateChecker.readable("v0.101-alpha31"));
		assertEquals("0.101 alpha 30", org.dbgl.util.EditionUpdateChecker.readable("0.101alpha30"));
		// alpha 48: a test build with its number, in the title of the main window; it counts as that version
		assertEquals("0.101 alpha 48 TEST 9", org.dbgl.util.EditionUpdateChecker.readable("0.101alpha48-test9"));
		assertEquals("0.101 alpha 48 test", org.dbgl.util.EditionUpdateChecker.readable("0.101alpha48-test"));
		assertEquals(0, org.dbgl.util.EditionUpdateChecker.compare("0.101alpha48-test9", "0.101alpha48"));

		String json = "[{\"tag_name\":\"v0.101-alpha32\",\"draft\":true,\"prerelease\":true,\"html_url\":\"u32\"},"
				+ "{\"tag_name\":\"v0.101-alpha31\",\"draft\":false,\"prerelease\":true,\"html_url\":\"u31\"},"
				+ "{\"tag_name\":\"v0.100-final\",\"draft\":false,\"prerelease\":false,\"html_url\":\"x\"},"
				+ "{\"tag_name\":\"v0.101-alpha30\",\"draft\":false,\"prerelease\":true,\"html_url\":\"u30\"}]";
		org.dbgl.util.EditionUpdateChecker.Release r = org.dbgl.util.EditionUpdateChecker.newest(json, "0.101alpha30", true);
		assertEquals("0.101 alpha 31", r.version()); // the draft is left out, an odd tag is skipped
		assertEquals("u31", r.url());
		assertTrue(r.prerelease());
		assertNull(org.dbgl.util.EditionUpdateChecker.newest(json, "0.101alpha30", false)); // without pre-releases: nothing newer
		assertNull(org.dbgl.util.EditionUpdateChecker.newest(json, "0.101alpha31", true));
		String withFinal = json.replace("v0.100-final", "v0.101");
		assertEquals("0.101", org.dbgl.util.EditionUpdateChecker.newest(withFinal, "0.101alpha30", false).version());
		assertEquals("0.101", org.dbgl.util.EditionUpdateChecker.newest(withFinal, "0.101alpha30", true).version());
	}

	/** alpha 49: headings shown in red, and the name suggested for an exported list of profiles */
	@Test
	public void testImportantHeadingAndListExportName() {
		assertTrue(org.dbgl.gui.dialog.DocumentDialog.important("Wichtig"));
		assertTrue(org.dbgl.gui.dialog.DocumentDialog.important("IMPORTANT: JAVA 21 IS NEEDED NOW"));
		assertTrue(org.dbgl.gui.dialog.DocumentDialog.important("  WICHTIG: JAVA 21 IST JETZT NÖTIG"));
		assertFalse(org.dbgl.gui.dialog.DocumentDialog.important("Neu"));
		assertFalse(org.dbgl.gui.dialog.DocumentDialog.important("Fixed"));
		// the changelog of this version starts with such a heading, and names the page Java 21 comes from
		for (String doc: new String[] {"changelog_de.txt", "changelog_en.txt", "whatsnew_de.txt", "whatsnew_en.txt"}) {
			String text;
			try (java.io.InputStream in = org.dbgl.gui.dialog.DocumentDialog.class.getResourceAsStream("/doc/" + doc)) {
				text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			} catch (java.io.IOException e) {
				throw new IllegalStateException(e);
			}
			assertTrue(doc, org.dbgl.gui.dialog.DocumentDialog.blocks(text).stream().anyMatch(b -> (b.kind() != org.dbgl.gui.dialog.DocumentDialog.Kind.BULLET)
					&& (b.kind() != org.dbgl.gui.dialog.DocumentDialog.Kind.PARAGRAPH) && org.dbgl.gui.dialog.DocumentDialog.important(b.text())));
			assertTrue(doc, text.contains("https://adoptium.net/temurin/releases/?version=21"));
		}

		assertEquals("html", org.dbgl.gui.dialog.MainWindow.listExportEnding("fancy_html"));
		assertEquals("csv", org.dbgl.gui.dialog.MainWindow.listExportEnding("simple_CSV"));
		assertEquals("txt", org.dbgl.gui.dialog.MainWindow.listExportEnding("txt"));
		assertEquals("dbgllist-2026-11-04_09-05.html", org.dbgl.gui.dialog.MainWindow.listExportName("html", java.time.LocalDateTime.of(2026, 11, 4, 9, 5, 59)));
	}

	/** alpha 49: the quick search above the list: every word must be found, in the title or the other columns, in any case */
	@Test
	public void testQuickSearch() {
		String[][] games = {{"Commander Keen 4", "id Software", "Action", "1991", ""}, {"Wing Commander", "Origin", "Simulation", "1990", "Wing Commander"},
				{"Ultima VIII: Pagan", "Origin", "Role-Playing (RPG)", "1994", "Ultima"}, {"Doom", "id Software", "Action", "1993", ""}};
		List<org.dbgl.model.aggregate.Profile> all = new java.util.ArrayList<>();
		for (int i = 0; i < games.length; i++) {
			org.dbgl.model.aggregate.Profile p = new org.dbgl.model.aggregate.Profile();
			p.setId(i);
			p.setTitle(games[i][0]);
			p.setDeveloper(games[i][1]);
			p.setGenre(games[i][2]);
			p.setYear(games[i][3]);
			p.setSeries(games[i][4], 0);
			all.add(p);
		}
		java.util.function.Function<String, String> ids = s -> org.dbgl.gui.dialog.MainWindow.quickSearched(all, s).stream().map(p -> String.valueOf(p.getId())).collect(java.util.stream.Collectors.joining(","));
		assertEquals("0,1,2,3", ids.apply(""));
		assertEquals("0,1,2,3", ids.apply("   "));
		assertEquals("0,1", ids.apply("commander"));
		assertEquals("0", ids.apply("KEEN  comm"));
		assertEquals("1,2", ids.apply("origin"));
		assertEquals("2", ids.apply("origin 1994"));
		assertEquals("0,3", ids.apply("action id"));
		assertEquals("2", ids.apply("ultima")); // title and series
		assertEquals("", ids.apply("keen 1993"));
	}

	/** alpha 49: libraries replaced by a newer version are found in the lib folder; others stay */
	@Test
	public void testSupersededLibraries() {
		List<String> wanted = List.of("commons-io-2.20.0.jar", "json-20260814.jar", "gallery_1.0.0.202509131601.jar", "hsqldb-2.7.4.jar", "swtwin64.jar", "jersey-2.48.jar");
		List<String> present = List.of("commons-io-2.20.0.jar", "json-20250517.jar", "json-20260814.jar", "gallery_1.0.0.202001010000.jar", "gallery_1.0.0.202509131601.jar", "hsqldb-2.7.4.jar",
			"swtwin64.jar", "swtlin64.jar", "jersey-2.47.jar", "jersey-2.48.jar", "my-own-1.0.jar", "jsoup-1.21.2.jar", "commons-io-extra.txt", "JSON-20240303.JAR");
		assertEquals("[JSON-20240303.JAR, gallery_1.0.0.202001010000.jar, jersey-2.47.jar, json-20250517.jar]", org.dbgl.util.EditionUpdater.supersededLibraries(wanted, present).toString());
		// nothing wanted with a version: nothing to remove, whatever is there
		assertEquals("[]", org.dbgl.util.EditionUpdater.supersededLibraries(List.of("swtwin64.jar"), present).toString());
	}

	/** alpha 49: the series IGDB names goes into a profile without one; the part comes from the title */
	@Test
	public void testSeriesFromOnlineSource() {
		org.dbgl.model.WebProfile w = new org.dbgl.model.WebProfile();
		org.dbgl.util.searchengine.IgdbSearchEngine.parseDetails(w,
			new org.json.JSONObject("{\"name\":\"Space Quest III\",\"collections\":[{\"id\":5,\"name\":\"Space Quest\"}],\"franchises\":[{\"id\":9,\"name\":\"Sierra\"}]}"));
		assertEquals("Space Quest", w.getSeries());
		org.dbgl.util.searchengine.IgdbSearchEngine.parseDetails(w, new org.json.JSONObject("{\"name\":\"X\",\"franchises\":[{\"name\":\"Star Wars\"}]}"));
		assertEquals("Star Wars", w.getSeries());
		org.dbgl.util.searchengine.IgdbSearchEngine.parseDetails(w, new org.json.JSONObject("{\"name\":\"X\",\"collections\":[7]}"));
		assertEquals("", w.getSeries());

		org.dbgl.model.aggregate.Profile p = new org.dbgl.model.aggregate.Profile();
		p.adoptSeries("Space Quest", "Space Quest III: The Pirates of Pestulon");
		assertEquals("Space Quest 3", p.getSeriesLabel());
		p.adoptSeries("Other", "Other 5");
		assertEquals("Space Quest 3", p.getSeriesLabel());
		org.dbgl.model.aggregate.Profile q = new org.dbgl.model.aggregate.Profile();
		q.adoptSeries("  ", "Doom II");
		assertEquals("", q.getSeriesLabel());
		q.adoptSeries("Doom", "Doom");
		assertEquals("Doom", q.getSeriesLabel());
	}

	/** alpha 49: game series suggested from the titles */
	@Test
	public void testSeriesSuggester() {
		java.util.function.Function<String, String> parse = t -> {
			org.dbgl.util.SeriesSuggester.Parsed p = org.dbgl.util.SeriesSuggester.parse(t);
			return p.base() + "|" + p.part();
		};
		assertEquals("Commander Keen|4", parse.apply("Commander Keen 4: Secret of the Oracle"));
		assertEquals("Space Quest|3", parse.apply("Space Quest III: The Pirates of Pestulon"));
		assertEquals("King's Quest|6", parse.apply("King's Quest VI - Heir Today, Gone Tomorrow"));
		assertEquals("Commander Keen|5", parse.apply("Commander Keen Episode 5"));
		assertEquals("Doom|2", parse.apply("Doom II (v1.9) [Floppy]"));
		assertEquals("Doom|0", parse.apply("Doom"));
		assertEquals("SimCity 2000|0", parse.apply("SimCity 2000")); // not a part
		assertEquals("FIFA 96|0", parse.apply("FIFA 96"));
		assertEquals("1942|0", parse.apply("1942"));
		assertEquals("Wing Commander|0", parse.apply("Wing Commander: Privateer"));
		assertEquals("Ultima|7", parse.apply("Ultima VII"));
		assertEquals("Lemmings|0", parse.apply("Lemmings"));

		String[] titles = {"Doom", "Doom II", "Commander Keen 4: Secret of the Oracle", "Commander Keen 5", "commander keen 6", "Lemmings", "SimCity 2000", "Wing Commander: Privateer",
				"Wing Commander: Armada", "Wing Commander 2", "The Secret of Monkey Island", "Monkey Island 2: LeChuck's Revenge", "Tetris", "Tetris", "Ultima VII", "Ultima 8", "Ultima Underworld"};
		List<org.dbgl.model.aggregate.Profile> profiles = new java.util.ArrayList<>();
		for (int i = 0; i < titles.length; i++) {
			org.dbgl.model.aggregate.Profile p = new org.dbgl.model.aggregate.Profile();
			p.setId(i);
			p.setTitle(titles[i]);
			p.setSeries("", 0);
			profiles.add(p);
		}
		java.util.function.Function<List<org.dbgl.model.aggregate.Profile>, String> show = list -> org.dbgl.util.SeriesSuggester.suggest(list).stream().map(
			s -> s.series() + "=" + s.members().stream().map(m -> m.profile().getId() + ":" + m.part()).collect(java.util.stream.Collectors.joining(","))).collect(
				java.util.stream.Collectors.joining(" | "));
		// Doom is part 1; the two Tetris are duplicates, no series; the Wing Commanders without a number keep 0; Monkey Island
		// alone ("The Secret of ..." has another name) is none; a title starting with the name of a series joins it
		assertEquals("Commander Keen=2:4,3:5,4:6 | Doom=0:1,1:2 | Ultima=14:7,15:8,16:0 | Wing Commander=9:2,8:0,7:0", show.apply(profiles));

		// the number later in the title, as a word, as "2.0" or raised; the series named after a subtitle
		assertEquals("Star Wars: Rebel Assault|2", parse.apply("Star Wars: Rebel Assault II - The Hidden Empire (CD)"));
		assertEquals("Might and Magic|1", parse.apply("Might and Magic: Book One - Secret of the Inner Sanctum"));
		assertEquals("The Elder Scrolls|2", parse.apply("The Elder Scrolls: Chapter II - Daggerfall"));
		assertEquals("Comanche|2", parse.apply("Comanche 2.0"));
		assertEquals("Vision|2", parse.apply("Vision\u00b2: Aufbruch ins Weltall"));
		assertEquals("Area One|0", parse.apply("Area One")); // a number as a word only after "Book", "Part", ...
		String[] more = {"Pagan: Ultima VIII: Gold Edition", "Ultima IV: Quest of the Avatar", "Star Wars: Rebel Assault", "Star Wars: Rebel Assault II - The Hidden Empire", "Star Wars: Dark Forces",
				"Star Wars: X-Wing (CD)", "Pro Pinball: The Web", "Pro Pinball: Timeshock!", "Wing Commander", "Wing Commander II: Vengeance of the Kilrathi", "Wing Commander Academy",
				"Wing Commander: Armada", "Ishar: Legend of the Fortress", "Ishar 2: Messengers of Doom", "Red Baron", "Red Baron: Mission Builder", "Sid Meier's Civilization",
				"Sid Meier's Colonization", "Prince of Persia", "Prince of Destiny", "Master of Magic", "Master of Orion", "Jurassic Park", "Jurassic Park (CD-ROM)", "Dark Seed", "Dark Universe"};
		List<org.dbgl.model.aggregate.Profile> others = new java.util.ArrayList<>();
		for (int i = 0; i < more.length; i++) {
			org.dbgl.model.aggregate.Profile p = new org.dbgl.model.aggregate.Profile();
			p.setId(i);
			p.setTitle(more[i]);
			p.setSeries("", 0);
			others.add(p);
		}
		assertEquals("Ishar=12:1,13:2 | Pro Pinball=6:0,7:0 | Star Wars=4:0,5:0 | Star Wars: Rebel Assault=2:1,3:2 | Ultima=1:4,0:8 | Wing Commander=8:1,9:2,10:0,11:0 | Red Baron=14:0,15:0"
				+ " | Sid Meier's=16:0,17:0",
			show.apply(others));
		// sure: the first six; found by the first words alone: the last two, not ticked in the window
		assertEquals("[true, true, true, true, true, true, false, false]", org.dbgl.util.SeriesSuggester.suggest(others).stream().map(s -> s.sure()).toList().toString());

		// no numbers in the titles: the parts by year, if the years differ and the genre is shared
		String[][] dated = {{"Ravenloft: Stone Prophet", "1995", "Role-Playing (RPG)"}, {"Ravenloft: Strahd's Possession", "1994", "Role-Playing (RPG)"}, {"Star Wars: Dark Forces", "1995", "Action"},
				{"Star Wars: TIE Fighter", "1994", "Simulation"}, {"X-COM: UFO Defense", "1994", "Strategy"}, {"X-COM: Terror from the Deep", "", "Strategy"},
				{"Pro Pinball: The Web", "1995", "Simulation, Pinball"}, {"Pro Pinball: Timeshock!", "1997", "Pinball"}, {"Wizardry: A", "1990", "RPG"}, {"Wizardry: B", "1990", "RPG"}};
		List<org.dbgl.model.aggregate.Profile> datedProfiles = new java.util.ArrayList<>();
		for (int i = 0; i < dated.length; i++) {
			org.dbgl.model.aggregate.Profile p = new org.dbgl.model.aggregate.Profile();
			p.setId(i);
			p.setTitle(dated[i][0]);
			p.setYear(dated[i][1]);
			p.setGenre(dated[i][2]);
			p.setSeries("", 0);
			datedProfiles.add(p);
		}
		assertEquals("Pro Pinball=6:1,7:2 | Ravenloft=1:1,0:2 | Star Wars=2:0,3:0 | Wizardry=8:0,9:0 | X-COM=5:0,4:0", show.apply(datedProfiles));

		// a series there is takes one more part, and keeps its name; its profiles are not suggested again
		profiles.get(2).setSeries("Commander Keen", 4);
		profiles.get(3).setSeries("Commander Keen", 5);
		assertTrue(show.apply(profiles), show.apply(profiles).startsWith("Commander Keen=4:6 | Doom="));
	}

	/** alpha 48: the order of the toolbar chosen by the user, made whole against what DBGL builds */
	@Test
	public void testToolbarLayout() {
		java.util.function.Function<String, List<String>> t = org.dbgl.gui.controls.ToolbarCustomizer::tokens;
		java.util.function.BiFunction<List<String>, List<String>, String> n = (c, d) -> String.join(",", org.dbgl.gui.controls.ToolbarCustomizer.normalize(c, d));
		List<String> def = t.apply("add,edit,|,run,|,wizard,usb,>,list,tiles,|,settings");
		assertNull(t.apply(""));
		assertNull(t.apply(null));
		assertEquals("add,edit,|,run,|,wizard,usb,>,list,tiles,|,settings", n.apply(null, def));
		// moved, a line more, one less, a button into the second bar
		assertEquals("run,|,add,edit,wizard,>,usb,list,|,tiles,settings", n.apply(t.apply("run,|,add,edit,wizard,|,>,usb,list,|,tiles,settings"), def));
		// lines at the start, at the end, twice, before the second bar: gone
		assertEquals("add,|,edit,run,wizard,usb,>,list,tiles,settings", n.apply(t.apply("|,add,|,|,edit,run,wizard,usb,|,>,|,list,tiles,settings,|"), def));
		// a button of an older version left out, a new one after the one it follows as built, the second bar added if missing
		assertEquals("edit,add,run,wizard,usb,>,list,tiles,settings", n.apply(t.apply("edit,add,old,run,wizard,list,tiles,settings"), t.apply("add,edit,run,wizard,usb,>,list,tiles,settings")));
		assertEquals("usb,add,>,list", n.apply(t.apply("usb,add,>"), t.apply("add,usb,>,list")));
		assertEquals("add,usb,>,tv,list", n.apply(t.apply("add,usb,>"), t.apply("add,usb,>,tv,list")));
	}

	/** alpha 48: the changelog and "What is new" in parts for their window (headings, parts, entries, paragraphs) */
	@Test
	public void testDocumentBlocks() throws Exception {
		String doc = "DBGL Extended Edition - Changes\r\n===============\r\n\r\nBased on DBGL 0.100 beta 17\r\nby Ronald.\r\n\r\n\r\n0.101 alpha 48 (2026-11-11)\r\n------\r\n"
				+ "Fixed\r\n- Saving leaves a file\r\n  as it was.\r\n- Second & last\r\nNew\r\n- A thing:\r\n    - nested entry\r\n\r\nWHAT IS NEW\r\n-----\r\n  - indented entry\r\n    continued\r\n\r\nAll details: Help.\r\n";
		List<org.dbgl.gui.dialog.DocumentDialog.Block> b = org.dbgl.gui.dialog.DocumentDialog.blocks(doc);
		String shown = b.stream().map(x -> x.kind() + (x.level() > 0 ? "" + x.level(): "") + ":" + x.text()).collect(java.util.stream.Collectors.joining("|"));
		assertEquals("TITLE:DBGL Extended Edition - Changes|PARAGRAPH:Based on DBGL 0.100 beta 17 by Ronald.|SECTION:0.101 alpha 48 (2026-11-11)|SUB:Fixed"
				+ "|BULLET:Saving leaves a file as it was.|BULLET:Second & last|SUB:New|BULLET:A thing:|BULLET1:nested entry|SECTION:WHAT IS NEW"
				+ "|BULLET:indented entry continued|PARAGRAPH:All details: Help.", shown);
		// the real documents: every line of text ends up in a part, nothing is lost
		for (String name: new String[] {"changelog_de", "changelog_en", "whatsnew_de", "whatsnew_en"}) {
			String text;
			try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("doc/" + name + ".txt")) {
				text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			}
			String joined = org.dbgl.gui.dialog.DocumentDialog.blocks(text).stream().map(org.dbgl.gui.dialog.DocumentDialog.Block::text).collect(java.util.stream.Collectors.joining(" "));
			String words = text.replaceAll("(?m)^\\s*[-=]{3,}\\s*$", "").replaceAll("(?m)^\\s*[-*] ", "").replaceAll("\\s+", " ").trim();
			assertEquals(name, words, joined.replaceAll("\\s+", " ").trim());
		}
	}

	/** alpha 48: the text of a release (Markdown) as plain text in the changelog window, problems with GitHub in words */
	@Test
	public void testEditionReleaseNotesAndProblems() throws Exception {
		org.dbgl.service.ITextService text = org.dbgl.service.TextService.getInstance();
		String md = "**DBGL Extended Edition 0.101 alpha 47** \u2013 by Ronald ([dbgl.org](https://dbgl.org), [Vogons](https://www.vogons.org/x?p=1#p1)).\r\n\r\n"
				+ "> [!WARNING]\r\n> **This is an alpha. Back up** \u2013 copy it.\r\n>\r\n> *Deutsch:* **Sichern**\r\n\r\n"
				+ "### Changes in this version (2026-11-11)\r\n**Fixed**\r\n- Saving leaves `[autoexec]` as it was; a very long line that goes on and on and on, well past the width of one hundred characters of the window\r\n"
				+ "  continued line\r\n- writes slow-&lt;date&gt;.txt\r\n\r\n<details>\r\n<summary>Earlier versions</summary>\r\n\r\n#### 0.101 alpha 46\r\n- _old_ entry\r\n</details>\r\n\r\n---\r\n```\r\nsha256  file\r\n```\r\n";
		String plain = org.dbgl.util.EditionUpdateChecker.plainNotes(md);
		for (String mark: new String[] {"**", "###", "](", "> ", "[!WARNING]", "&lt;", "<details>", "<summary>", "`", "```", "_old_", "\r"})
			assertFalse(mark + " in: " + plain, plain.contains(mark));
		assertTrue(plain, plain.replaceAll("\n(?!\n)", " ").startsWith("DBGL Extended Edition 0.101 alpha 47 \u2013 by Ronald (dbgl.org (https://dbgl.org), Vogons (https://www.vogons.org/x?p=1#p1))."));
		assertTrue(plain, plain.contains("\n\n" + text.get("dialog.main.editionupdate.notes.warning") + ":\nThis is an alpha. Back up \u2013 copy it.\n\nDeutsch: Sichern\n"));
		assertTrue(plain, plain.contains("\n\nChanges in this version (2026-11-11)\nFixed\n\u2022 Saving leaves [autoexec] as it was;"));
		assertTrue(plain, plain.contains("\n  continued line\n\u2022 writes slow-<date>.txt\n"));
		assertTrue(plain, plain.contains("Earlier versions\n\n0.101 alpha 46\n\u2022 old entry"));
		assertTrue(plain, plain.contains("    sha256  file"));
		for (String line: plain.split("\n"))
			assertTrue(line, line.length() <= 80);
		assertTrue(plain, plain.contains("\n  ")); // the wrapped part of a bullet is indented like its text
		assertEquals("", org.dbgl.util.EditionUpdateChecker.plainNotes(null));

		assertEquals(text.get("dialog.main.editionupdate.problem.offline"), org.dbgl.util.EditionUpdateChecker.describe(new java.io.IOException(new java.net.ConnectException())));
		assertEquals(text.get("dialog.main.editionupdate.problem.offline"), org.dbgl.util.EditionUpdateChecker.describe(new java.net.UnknownHostException("api.github.com")));
		assertEquals(text.get("dialog.main.editionupdate.problem.timeout"), org.dbgl.util.EditionUpdateChecker.describe(new java.net.http.HttpConnectTimeoutException("x")));
		assertEquals(text.get("dialog.main.editionupdate.problem.certificate"), org.dbgl.util.EditionUpdateChecker.describe(new javax.net.ssl.SSLHandshakeException("PKIX")));
		assertEquals(text.get("dialog.main.editionupdate.problem.checksum"), org.dbgl.util.EditionUpdateChecker.describe(new org.dbgl.util.EditionUpdater.ChecksumMismatch("SHA-256")));
		assertEquals("something else", org.dbgl.util.EditionUpdateChecker.describe(new java.io.IOException("something else")));
		assertEquals(text.get("dialog.main.editionupdate.problem.ratelimit"), org.dbgl.util.EditionUpdateChecker.httpProblem(403));
		assertTrue(org.dbgl.util.EditionUpdateChecker.httpProblem(500).contains("500"));
	}

	/** alpha 45: one-click update (files of a release, sums, safe unpacking), statistics as web page and CSV, DOSBox-X advice */
	@Test
	public void testAlpha45() throws Exception {
		String json = "[{\"tag_name\":\"v0.101-alpha46\",\"draft\":false,\"prerelease\":true,\"html_url\":\"u46\",\"assets\":["
				+ "{\"name\":\"DBGL-Extended-Edition-0.101alpha46.zip\",\"browser_download_url\":\"https://x/full.zip\"},"
				+ "{\"name\":\"dbgl-0.101alpha46-update.zip\",\"browser_download_url\":\"https://x/dbgl-0.101alpha46-update.zip\"},"
				+ "{\"name\":\"SHA256SUMS.txt\",\"browser_download_url\":\"https://x/SHA256SUMS.txt\"}]},"
				+ "{\"tag_name\":\"v0.101-alpha45\",\"draft\":false,\"prerelease\":true,\"html_url\":\"u45\"}]";
		org.dbgl.util.EditionUpdateChecker.Release r = org.dbgl.util.EditionUpdateChecker.newest(json, "0.101alpha45", true);
		assertEquals("0.101 alpha 46", r.version());
		assertTrue(r.installable());
		assertEquals("https://x/dbgl-0.101alpha46-update.zip", r.updateZip());
		assertEquals("https://x/SHA256SUMS.txt", r.sums());
		assertFalse(org.dbgl.util.EditionUpdateChecker.newest(json.replace("SHA256SUMS.txt", "other.txt"), "0.101alpha45", true).installable());

		String hash = "ab".repeat(32);
		String sums = "cd".repeat(32) + "  dbgl.jar\n" + hash + "  dbgl-0.101alpha46-update.zip\n";
		assertEquals(hash, org.dbgl.util.EditionUpdater.sumOf(sums, "dbgl-0.101alpha46-update.zip"));
		assertNull(org.dbgl.util.EditionUpdater.sumOf(sums, "missing.zip"));

		File dir = Files.createTempDirectory("dbgl-upd").toFile();
		try {
			File zip = new File(dir, "u.zip");
			try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
				out.putNextEntry(new java.util.zip.ZipEntry("dbgl.jar"));
				out.write("jar".getBytes(StandardCharsets.UTF_8));
				out.putNextEntry(new java.util.zip.ZipEntry("plugins/i18n/MessagesBundle.properties"));
				out.write("a=b".getBytes(StandardCharsets.UTF_8));
			}
			assertEquals(List.of("dbgl.jar", "plugins/i18n/MessagesBundle.properties"), org.dbgl.util.EditionUpdater.unzip(zip, new File(dir, "files")));
			assertEquals(64, org.dbgl.util.EditionUpdater.sha256(zip).length());
			File evil = new File(dir, "evil.zip");
			try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(evil.toPath()))) {
				out.putNextEntry(new java.util.zip.ZipEntry("../outside.txt"));
				out.write(1);
			}
			try {
				org.dbgl.util.EditionUpdater.unzip(evil, new File(dir, "files2"));
				fail("an entry outside of the folder must be refused");
			} catch (IOException e) {
				assertFalse(new File(dir, "outside.txt").exists());
			}
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}

		// D-Fend Reloaded import: which DOSBox wrote the .conf (DFendX can write them for DOSBox-X and Staging)
		java.util.Map<String, org.dbgl.model.entity.GenerationAwareConfigurable.Generation> gens = new java.util.LinkedHashMap<>();
		org.dbgl.model.aggregate.DosboxVersion vGen = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, "staging083", "", "", "staging083/dosbox.conf");
		gens.put("dosbox-x-2026.10.01.conf", org.dbgl.model.entity.GenerationAwareConfigurable.Generation.x_20241001);
		gens.put("dosbox-staging-0.83.0.conf", org.dbgl.model.entity.GenerationAwareConfigurable.Generation.staging_0830);
		gens.put("dosbox-staging-0.82.2.conf", org.dbgl.model.entity.GenerationAwareConfigurable.Generation.staging_0820);
		gens.put("dosbox-daum-2015.01.25.conf", org.dbgl.model.entity.GenerationAwareConfigurable.Generation.official_073);
		gens.put("dosbox-ece-r4482.conf", org.dbgl.model.entity.GenerationAwareConfigurable.Generation.official_073);
		for (java.util.Map.Entry<String, org.dbgl.model.entity.GenerationAwareConfigurable.Generation> g: gens.entrySet()) {
			File f = new File(DATA_DIR, g.getKey());
			org.dbgl.model.aggregate.Profile p = org.dbgl.model.factory.ProfileFactory.create(vGen, null);
			p.loadConfigurationData(GenericTextService.getInstance(), Files.readString(f.toPath(), StandardCharsets.ISO_8859_1), f);
			assertEquals(g.getKey(), g.getValue(), p.determineGenByConf());
		}
		String[][] partial = {{"[sdl]\nfullscreen=false\n[dosbox]\nmachine=pc98\nmemsize=16\n[cpu]\ncputype=auto\n[autoexec]\nmount c .\n", "x_20241001"},
				{"[sdl]\nfullscreen=false\n[capture]\ncapture_dir=capture\n[cpu]\ncputype=auto\n[autoexec]\nmount c .\n", "staging_0820"},
				{"[sdl]\nwindow_size=default\n[cpu]\ncputype=auto\n[autoexec]\nmount c .\n", "staging_0830"}};
		for (String[] c: partial) {
			org.dbgl.model.aggregate.Profile p = org.dbgl.model.factory.ProfileFactory.create(vGen, null);
			p.loadConfigurationData(GenericTextService.getInstance(), c[0], new File("dfendx.conf"));
			assertEquals(c[0], c[1], String.valueOf(p.determineGenByConf()));
		}

		// from the stress test: file names that are too long, or that Windows keeps for devices
		assertEquals(100, org.dbgl.util.FilesUtils.toSafeFilenameForWebImages("x".repeat(400)).length());
		assertTrue(org.dbgl.util.DosboxLog.folderOf(999901, "AUX.txt").getName().startsWith("_AUX.txt"));
		assertTrue(org.dbgl.util.DosboxLog.folderOf(999902, "CON").getName().startsWith("_CON"));
		assertEquals("Doom (999903)", org.dbgl.util.DosboxLog.folderOf(999903, "Doom").getName());

		// DOSBox-X advice from the [autoexec], for profiles too
		assertEquals("ide", org.dbgl.util.StoreGameImport.dosboxXReason("[autoexec]\nimgmount d cd.iso -t iso -ide 2m\n"));
		assertEquals("pc98", org.dbgl.util.StoreGameImport.dosboxXReason("[autoexec]\nimgmount c game.hdi\nboot -l c\n"));
		assertEquals("boot", org.dbgl.util.StoreGameImport.dosboxXReason("[autoexec]\nmount 2 win95.img -t hdd -fs none\nboot -l c\n"));
		assertNull(org.dbgl.util.StoreGameImport.dosboxXReason("[autoexec]\nmount c .\nboot disk1.img\n"));

		// statistics as a web page (with bars, nothing from the internet) and as CSV
		org.dbgl.model.aggregate.DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null,
			"staging083", "", "", "staging083/dosbox.conf");
		org.dbgl.model.aggregate.Profile a = org.dbgl.model.factory.ProfileFactory.create(v083, null);
		a.setTitle("Tom & <Jerry>");
		org.dbgl.model.aggregate.Profile b = org.dbgl.model.factory.ProfileFactory.create(v083, null);
		b.setTitle("Doom; \"II\"");
		List<org.dbgl.model.aggregate.Profile> profiles = List.of(a, b);
		java.util.Date now = new java.util.Date();
		String html = org.dbgl.util.ProfileReports.statisticsHtml(profiles, List.of(), GenericTextService.getInstance(), java.util.Locale.ENGLISH, now);
		assertTrue(html.startsWith("<!DOCTYPE html>"));
		assertTrue(html, html.contains("Tom &amp; &lt;Jerry&gt;") && !html.contains("<Jerry>") && !html.contains("http://") && html.trim().endsWith("</html>"));
		String text = org.dbgl.util.ProfileReports.statistics(profiles, List.of(), GenericTextService.getInstance(), java.util.Locale.ENGLISH, now);
		assertTrue(text.contains("Tom & <Jerry>"));
		String csv = org.dbgl.util.ProfileReports.statisticsCsv(profiles, List.of(), GenericTextService.getInstance(), java.util.Locale.GERMAN, now);
		assertTrue(csv.startsWith("\uFEFF"));
		assertTrue(csv, csv.contains(";\"Doom; \"\"II\"\"\"\r\n")); // ; in German (the comma is the decimal sign), the title quoted
	}

	/** alpha 46: D-Fend Reloaded profiles (.prof, real templates of D-Fend Reloaded) converted without an exported .conf */
	@Test
	public void testDFendProf() throws Exception {
		File dir = new File(DATA_DIR, "dfend");
		File dfend = new File("C:\\Games\\D-Fend Reloaded");

		org.dbgl.model.conf.Settings game = new org.dbgl.model.conf.Settings();
		game.setFileLocation(new org.dbgl.model.FileLocation(new File(dir, "game.prof").getPath()));
		game.loadDetectingCharset(GenericTextService.getInstance());
		assertEquals("Ärger im Weltall", game.getValue("ExtraInfo", "name")); // written in ANSI (Windows-1252) like D-Fend does
		org.dbgl.model.conf.dfend.DFendProfConverter.Result r = org.dbgl.model.conf.dfend.DFendProfConverter.convert(game, dfend);
		String c = r.conf();
		assertTrue(c, c.contains("[cpu]\ncore=dynamic\ncputype=auto\ncycles=45000\ncycleup=500\ncycledown=20\n"));
		assertTrue(c, c.contains("[dosbox]\nmachine=svga_s3\nmemsize=63\n"));
		assertTrue(c, c.contains("[midi]\nmpu401=intelligent\nmididevice=default\n"));
		assertTrue(c, c.contains("[gus]\ngus=true\ngusrate=22050\ngusbase=240\ngusirq=5\ngusdma=1\nultradir=C:\\ULTRASND\n"));
		assertTrue(c, c.contains("sbmixer=true") && c.contains("[dos]\nxms=true\nems=true\numb=true\n") && c.contains("fullscreen=true") && c.contains("aspect=false"));
		assertFalse(c, c.contains("keyboardlayout=default") || c.contains("DOSBoxlanguage") || c.contains("Volume"));
		String root = new File(dfend, "VirtualHD").getPath();
		assertTrue(c, c.contains("mount C \"" + root + "\"\n"));
		assertTrue(c, c.contains("imgmount D \"" + new File(new File(dfend, "VirtualHD"), "CD" + File.separator + "game.iso").getPath() + "\" -t iso\n"));
		assertTrue(c, c.contains("@REM PRE-LAUNCH\nset BLASTER=A220 I7 D1 T6\n@REM /PRE-LAUNCH\nC:\ncd \\SPACE\nSPACE.EXE -nosound\nexit\n"));
		assertTrue(r.notes().toString(), r.notes().isEmpty());

		// the whole way: a DBGL profile on DOSBox Staging 0.83, from 0.74 settings
		org.dbgl.model.aggregate.DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null,
			new File(DATA_DIR).getAbsolutePath(), "", "", new File(DATA_DIR, "dosbox-staging-0.83.0.conf").getAbsolutePath());
		org.dbgl.model.aggregate.Profile p = org.dbgl.model.factory.ProfileFactory.create(v083, null);
		p.loadConfigurationData(GenericTextService.getInstance(), c, new File("game.conf"));
		p.alterToDosboxVersionGeneration(org.dbgl.model.entity.GenerationAwareConfigurable.Generation.official_073, v083);
		assertEquals("SPACE.EXE", new File(p.getConfiguration().getAutoexec().getGameMain()).getName().toUpperCase());
		assertTrue(p.getConfigurationString(), p.getConfigurationString().contains("set BLASTER=A220 I7 D1 T6")); // D-Fend's own autoexec lines are kept

		// the templates: CGA without sound, and a booter (BootImage noted, not guessed)
		org.dbgl.model.conf.Settings cga = new org.dbgl.model.conf.Settings();
		cga.setFileLocation(new org.dbgl.model.FileLocation(new File(dir, "template-early-cga.prof").getPath()));
		cga.loadDetectingCharset(GenericTextService.getInstance());
		String cc = org.dbgl.model.conf.dfend.DFendProfConverter.convert(cga, dfend).conf();
		assertTrue(cc, cc.contains("machine=cga") && cc.contains("cycles=500") && cc.contains("sbtype=none") && cc.contains("mpu401=none") && cc.contains("joysticktype=2axis")
				&& cc.contains("xms=false"));
		org.dbgl.model.conf.Settings boot = new org.dbgl.model.conf.Settings();
		boot.setFileLocation(new org.dbgl.model.FileLocation(new File(dir, "template-boot.prof").getPath()));
		boot.loadDetectingCharset(GenericTextService.getInstance());
		assertTrue(org.dbgl.model.conf.dfend.DFendProfConverter.convert(boot, dfend).notes().contains("BootImage=2 (no image)"));
		String bootConf = org.dbgl.model.conf.dfend.DFendProfConverter.convert(boot, dfend).conf();
		assertTrue(bootConf, bootConf.contains("boot -l c\n") && !bootConf.contains("exit"));

		// real example profiles of D-Fend Reloaded
		java.util.function.Function<String, org.dbgl.model.conf.dfend.DFendProfConverter.Result> conv = name -> {
			try {
				org.dbgl.model.conf.Settings s = new org.dbgl.model.conf.Settings();
				s.setFileLocation(new org.dbgl.model.FileLocation(new File(dir, name).getPath()));
				s.loadDetectingCharset(GenericTextService.getInstance());
				return org.dbgl.model.conf.dfend.DFendProfConverter.convert(s, dfend);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		};
		String hd = new File(dfend, "VirtualHD").getPath();
		// FreeDOS: two hard disk images with geometry on drive numbers 2 and 3, a floppy image on 0, booted from 2 (C:)
		org.dbgl.model.conf.dfend.DFendProfConverter.Result freedos = conv.apply("freedos.prof");
		String fd = freedos.conf();
		String fdboot = new File(hd, "FDBOOT").getPath();
		assertTrue(fd, fd.contains("imgmount 2 \"" + new File(fdboot, "c.img").getPath() + "\" -t hdd -size 512,63,16,30\n"));
		assertTrue(fd, fd.contains("imgmount 3 \"" + new File(fdboot, "d.img").getPath() + "\" -t hdd -size 512,63,16,30\n"));
		assertTrue(fd, fd.contains("imgmount 0 \"" + new File(fdboot, "A.IMG").getPath() + "\" -t floppy\n"));
		assertTrue(fd, fd.endsWith("boot -l c\n"));
		assertTrue(freedos.notes().toString(), freedos.notes().isEmpty());
		// 4DOS: its own PATH, a drive label; NDN: PATH with C:\NDN and C: in the autoexec
		String fourdos = conv.apply("4dos.prof").conf();
		assertTrue(fourdos, fourdos.contains("mount C \"" + hd + "\" -label test\n") && fourdos.contains("@REM PRE-LAUNCH\nset PATH=C:\\4DOS\n@REM /PRE-LAUNCH\n")
				&& fourdos.contains("cd \\4DOS\n4DOS.COM\nexit\n") && fourdos.contains("cyclesdown") == false && fourdos.contains("cycledown=500"));
		String ndn = conv.apply("ndn.prof").conf();
		assertTrue(ndn, ndn.contains("set PATH=Z:\\;C:\\NDN\nC:\n@REM /PRE-LAUNCH\nC:\ncd \\NDN\nNDN.COM\nexit\n") && ndn.contains("usescancodes=true"));
		// VGA Copy: written in ANSI with an umlaut, parameters, both D-Fend names of the cycle steps
		org.dbgl.model.conf.Settings vga = new org.dbgl.model.conf.Settings();
		vga.setFileLocation(new org.dbgl.model.FileLocation(new File(dir, "vgacopy.prof").getPath()));
		vga.loadDetectingCharset(GenericTextService.getInstance());
		assertEquals("Thomas Mönkemeier", vga.getValue("ExtraInfo", "developer"));
		String vgac = org.dbgl.model.conf.dfend.DFendProfConverter.convert(vga, dfend).conf();
		assertTrue(vgac, vgac.contains("vgacopy.exe /B\n") && vgac.contains("memsize=16") && (vgac.split("cycleup=", -1).length == 2));
		// OpenGEM and DB-Info: a .BAT as the program, and no 'exit' when D-Fend keeps DOSBox open
		assertTrue(conv.apply("opengem.prof").conf().contains("cd \\GEMAPPS\nGEM.BAT\nexit\n"));
		String dbinfo = conv.apply("dbinfo.prof").conf();
		assertTrue(dbinfo, dbinfo.contains("cd \\DB-INFO\ndb-info.bat\n") && !dbinfo.contains("exit"));
		// two CD images on D: (one line each in D-Fend) become one imgmount to swap them; the program on E:, a folder outside of D-Fend
		org.dbgl.model.conf.dfend.DFendProfConverter.Result cds = conv.apply("cdimages.prof");
		String isoDir = new File("D:\\Spiele\\DOS\\SPIELE\\C&C\\iso".replace('\\', File.separatorChar)).getPath();
		assertTrue(cds.conf(), cds.conf().contains("imgmount D \"" + new File(isoDir, "GDI.iso").getPath() + "\" \"" + new File(isoDir, "NOD.iso").getPath() + "\" -t iso\n"));
		assertEquals(cds.conf(), 1, cds.conf().split("imgmount D", -1).length - 1);
		assertTrue(cds.conf(), cds.conf().contains("mount E \"" + new File("D:\\Spiele\\DOS\\SPIELE\\AODCD".replace('\\', File.separatorChar)).getPath() + "\"\n")
				&& cds.conf().contains("E:\ncd \\\nAODCD.EXE\nexit\n"));
		assertTrue(cds.notes().toString(), cds.notes().isEmpty());
		// DOSBox DOS: no program, a batch file in the autoexec (labels, goto, if exist), DOSBox stays open
		String dd = conv.apply("dosboxdos.prof").conf();
		assertTrue(dd, dd.contains("@REM PRE-LAUNCH\nC:\necho.\nIf exist C:\\FREEDOS\\COMMAND.COM goto AddFreeDos\n") && dd.contains(":Next6\n@REM /PRE-LAUNCH\n")
				&& !dd.contains("exit"));
	}

	/**
	 * Which version wrote a DOSBox configuration file: a file written by the new version already is kept when DBGL corrects
	 * the version of a DOSBox at startup (e.g. DBGL 0.100 beta 17 ran Staging 0.83 under the last version it knew).
	 */
	@Test
	public void testVersionOfConf() throws IOException {
		assertEquals("0.83.0", DosboxVersionDetector.versionOfConf(new File(DATA_DIR, "dosbox-staging-0.83.0.conf")));
		assertEquals("0.82.2", DosboxVersionDetector.versionOfConf(new File(DATA_DIR, "dosbox-staging-0.82.2.conf")));
		assertEquals("2026.10.01", DosboxVersionDetector.versionOfConf(new File(DATA_DIR, "dosbox-x-2026.10.01.conf")));
		assertEquals("0.74-3", DosboxVersionDetector.versionOfConf(new File(OFFICIAL_CONF)));
		// Staging writes the first line in its language: "# Dies ist die Konfigurationsdatei für dosbox-staging (0.83.0)."
		assertEquals("0.83.0", DosboxVersionDetector.versionOfConf(new File(DATA_DIR, "dosbox-staging-0.83.0-de.conf")));
		assertNull(DosboxVersionDetector.versionOfConf(new File(DATA_DIR, "dosbox-daum-2015.01.25.conf"))); // "SVN-Daum": no version
		assertEquals("0.82.2", DosboxVersionDetector.versionOfConf(new File(DATA_DIR, "dosbox-staging-0.82.2-subset.conf"))); // "# DOSBox Staging 0.82.2 settings ..." 
		assertNull(DosboxVersionDetector.versionOfConf(new File(DATA_DIR, "does-not-exist.conf")));
		File old = Files.createTempFile("dbgl", ".conf").toFile();
		Files.writeString(old.toPath(), "# This is the configuration file for dosbox-staging (0.81.2).\n[sdl]\n");
		assertEquals("0.81.2", DosboxVersionDetector.versionOfConf(old));
		old.delete();
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
		c.setValue("render", "shader", "scaler/advinterp2x"); // a shader file of Staging, not listed in the help text
		List<String[]> invalid = profile.listInvalidValues();
		assertEquals(1, invalid.size());
		assertEquals("output", invalid.get(0)[1]);
		assertEquals("overlay", invalid.get(0)[2]);
	}
	/**
	 * Found with configurations from the internet (alpha 46): a command before the game on another drive or in another folder
	 * ran in the game's folder after saving; "C:" of a profile without a game was gone; "[sdl] ; Video" lost its comment.
	 */
	@Test
	public void testLinesRunWhereTheyRan() throws IOException {
		String[][] cases = {
			{"[autoexec]\nmount c /games\nmount m /mdf\nm:\nCALL SC55.BAT\nc:\ncd game\ngame.exe\n", "M:\ncd \\\nCALL SC55.BAT\nC:\ncd \\game\n"},
			{"[autoexec]\nmount c /games\nc:\ncd util\nmouse.com\ncd \\game\ngame.exe\n", "cd \\util\nmouse.com\ncd \\game\n"},
			{"[autoexec]\nmount c /games\nc:\ncd game\n", "c:\ncd game\n"},
			{"[sdl] ; Video\nfullscreen=true\n[autoexec]\nmount c /games\n", "[sdl] ; Video\n"}};
		for (String[] c: cases) {
			String text = c[0];
			for (int round = 0; round < 3; round++) {
				Configuration conf = new Configuration();
				conf.loadDataWithAutoexec(GenericTextService.getInstance(), text, null, null);
				org.dbgl.util.UnknownAutoexecLines.keepOnRead(conf.getAutoexec()); // as a profile does after reading
				String written = ForeignLinesAccess.keep(text, conf.toString(List.of()));
				assertTrue(written, written.replace("\r\n", "\n").contains(c[1]));
				if (round > 0)
					assertEquals(text, written);
				text = written;
			}
		}
	}

	private static String saved(String text) throws IOException {
		Configuration conf = new Configuration();
		conf.loadDataWithAutoexec(GenericTextService.getInstance(), text, null, null);
		org.dbgl.util.UnknownAutoexecLines.keepOnRead(conf.getAutoexec());
		return ForeignLinesAccess.keep(text, conf.toString(List.of())).replace("\r\n", "\n");
	}

	/** found by the independent review (alpha 46): each case lost or changed something on saving */
	@Test
	public void testFoundByReview() throws IOException {
		// the way back after a line that ran elsewhere follows a game changed later
		Configuration conf = new Configuration();
		conf.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount c .\nmount m util\nm:\ncall sc55.bat\nc:\ncd game\ngame.exe\n", null, null);
		org.dbgl.util.UnknownAutoexecLines.keepOnRead(conf.getAutoexec());
		conf.getAutoexec().setMain(new File("GAME2", "START.EXE").getPath());
		String w = conf.toString(List.of()).replace("\r\n", "\n");
		assertTrue(w, w.contains("call sc55.bat\nC:\ncd \\GAME2\n") && !w.contains("cd \\game\n"));
		// repeated comments all stay
		String text = "[sdl]\n# ----\nfullscreen=false\n[cpu]\n# ----\ncycles=max\n[autoexec]\n# ----\nmount c .\n";
		assertEquals(3, saved(text).split("# ----", -1).length - 1);
		// "@pause" after boot without "exit" stays
		assertTrue(saved("[autoexec]\nmount a game\nboot a:\\disk1.img\n@pause\n").contains("pause"));
		// lines after "exit" stay when "exit" is unticked
		conf = new Configuration();
		conf.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount c .\nc:\ngame.exe\nexit\necho after\n", null, null);
		org.dbgl.util.UnknownAutoexecLines.keepOnRead(conf.getAutoexec());
		conf.getAutoexec().setExit(false);
		assertTrue(conf.toString(List.of()).contains("echo after"));
		// a fix acts on the setting that counts: the last one
		org.dbgl.util.ProfileFindings.ConfText t = org.dbgl.util.ProfileFindings.ConfText.of("[sdl]\noutput=opengl\nfullscreen=true\noutput=bogus\n");
		t.remove("sdl", "output");
		assertEquals("opengl", t.get("sdl", "output"));
		// "]" in the comment after a header
		Configuration c2 = new Configuration();
		c2.loadDataWithAutoexec(GenericTextService.getInstance(), "[SDL] ; see [docs]\nfullscreen=true\n[autoexec]\n", null, null);
		assertEquals("true", c2.getValue("sdl", "fullscreen"));
		// back on a drive: in its folder; "cd \\" after the game is not where the game is
		assertTrue(saved("[autoexec]\nmount c .\nmount m util\nc:\ncd \\game\nm:\n@rem x\nc:\ngame.exe\n").contains("cd \\game\n"));
		String after = saved("[autoexec]\nmount c .\nc:\ncd game\ngame.exe\ncd \\\nexit\n");
		assertTrue(after, after.contains("cd \\game\ngame.exe\ncd \\\n"));
		// a line of the user that starts like a marker of DBGL
		assertTrue(saved("[autoexec]\n@REM STARTUP of my game\nmount c .\nc:\ngame.exe\n").contains("game.exe"));
		// "boot C:\\x.img" without a drive C: the path as written
		assertTrue(saved("[autoexec]\nboot C:\\Users\\me\\boot.img\n").contains("boot C:\\Users\\me\\boot.img"));
	}

	/** found with a real collection (alpha 46): the parameters of an earlier program went to the game */
	@Test
	public void testParametersOfTheGameOnly() throws IOException {
		Configuration conf = new Configuration();
		conf.loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nmount c .\nC:\\SB16\\DEVICE.COM C:\\WINDOWS\\IFSHLP.SYS\nC:\\WINDOWS\\SMARTDRV.EXE\n", null, null);
		assertEquals("", conf.getAutoexec().getParameters());
	}

	/** ForeignLines as Configuration.save uses it */
	private static final class ForeignLinesAccess {
		static String keep(String before, String after) {
			return org.dbgl.model.conf.ForeignLines.keep(before, after);
		}
	}

	/**
	 * Found with the shared configurations of DBConfWS (alpha 46): lines DBGL cannot give a DOS path for were lost or grew
	 * with each saving. They are written as they were read.
	 */
	@Test
	public void testStartAndBootLinesKeptAsRead() throws IOException {
		String[] cases = {
			"[autoexec]\nimgmount D \"D:\\3DFX\\bat\\Toshinden.img\" -t iso\nmount D \".\"\nD:\ncd \\\nbat.exe\nexit\n", "bat.exe",
			"[autoexec]\nmount C \"~/.dosbox/drive_c\"\nC:\ncd \\CC\nC&C.EXE\nexit\n", "cd \\CC\n",
			"[autoexec]\nmount A \"C:\\Users\\x\\Downloads\"\nboot A:\\Caveland.img\nexit\n", "boot A:\\Caveland.img"};
		for (int i = 0; i < cases.length; i += 2) {
			String text = cases[i];
			for (int round = 0; round < 3; round++) {
				Configuration c = new Configuration();
				c.loadDataWithAutoexec(GenericTextService.getInstance(), text, null, null);
				String written = c.toString(List.of()).replace("\r\n", "\n"); // on Windows written with CRLF
				assertTrue(written, written.contains(cases[i + 1]));
				if (round > 0)
					assertEquals(text, written); // the same with each saving
				text = written;
			}
		}
	}

	/**
	 * Found with configurations from the internet (alpha 46): "imgmount A disk1 disk2 -t floppy" (no image extension) was
	 * written as MOUNT for Staging 0.83 and then read as a folder. It stays IMGMOUNT; images with an extension become MOUNT.
	 */
	@Test
	public void testImagesWithoutExtensionStayImgmountForStaging083() throws IOException {
		org.dbgl.model.aggregate.DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null,
			"staging083", "", "", "staging083/dosbox.conf");
		File dosboxConfFile = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
		v083.loadConfigurationData(GenericTextService.getInstance(), Files.readString(dosboxConfFile.toPath(), StandardCharsets.UTF_8), dosboxConfFile);
		org.dbgl.model.aggregate.Profile profile = org.dbgl.model.factory.ProfileFactory.create(v083, null);
		profile.getConfiguration().loadDataWithAutoexec(GenericTextService.getInstance(), "[autoexec]\nimgmount A disk1 disk2 -t floppy\nimgmount D game.iso -t iso\n", null, null);
		profile.adaptImageMountsToDosbox();
		List<org.dbgl.model.conf.mount.Mount> mounts = profile.getConfiguration().getAutoexec().getMountingpoints();
		assertEquals("imgmount", ((org.dbgl.model.conf.mount.ImageMount)mounts.get(0)).getCommand());
		assertEquals("mount", ((org.dbgl.model.conf.mount.ImageMount)mounts.get(1)).getCommand());
	}

	/** found with configurations from the internet (alpha 46): told as invalid, and the solution removed them */
	@Test
	public void testValuesWithParametersAreValid() throws IOException {
		org.dbgl.model.aggregate.DosboxVersion v074 = DosboxVersionFactory.create("DOSBox 0.74-3", "DOSBox", "0.74-3", false, true, false, null, "official074", "", "",
			"official074/dosbox.conf");
		File dosboxConfFile = new File(System.getProperty("dbgl.officialconf"));
		v074.loadConfigurationData(GenericTextService.getInstance(), Files.readString(dosboxConfFile.toPath(), StandardCharsets.ISO_8859_1), dosboxConfFile);
		org.dbgl.model.aggregate.Profile profile = org.dbgl.model.factory.ProfileFactory.create(v074, null);
		Configuration c = profile.getConfiguration();
		for (String cycles: new String[] {"fixed 3000", "3000", "max 80%", "auto 3000 90% limit 50000", "max limit 60000"}) {
			c.setValue("cpu", "cycles", cycles);
			c.setValue("render", "scaler", "hq2x forced");
			c.setValue("serial", "serial1", "modem listenport:5000");
			assertEquals(cycles, 0, profile.listInvalidValues().size());
		}
		c.setValue("cpu", "cycles", "fast");
		assertEquals(1, profile.listInvalidValues().size());
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
			{"MZ..DOSBox version %s\0mapper-SVN.map\0", SettingsService.SVN_DOSBOX_FAM.title_, "r4494"},
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
		// TEST32: the names of DOSBox-X 2026.08.31: MinGW without "win" in the name, offered after the Visual Studio build of 64 bits
		List<String[]> x2 = List.of(new String[] {"dosbox-x-mingw32-2026.08.31-portable.zip", "m32"}, new String[] {"dosbox-x-mingw32_lowend-2026.08.31-portable.zip", "low"},
			new String[] {"dosbox-x-mingw32_lowend9x-2026.08.31-portable.zip", "9x"}, new String[] {"dosbox-x-mingw64-2026.08.31-portable.zip", "m64"},
			new String[] {"dosbox-x-vsbuild-arm64-2026.08.31-portable.zip", "arm"}, new String[] {"dosbox-x-vsbuild-win32-2026.08.31-portable.zip", "v32"},
			new String[] {"dosbox-x-vsbuild-win64-2026.08.31-portable.zip", "v64"}, new String[] {"dosbox-x-macosx-x86_64-2026.08.31.zip", "mac"});
		List<String> order = org.dbgl.util.DosboxInstaller.candidates(x2, true, false, false).stream().map(a -> a[1]).toList();
		assertEquals(List.of("v64", "m64"), order.subList(0, 2));
		assertTrue(order.indexOf("m32") < order.indexOf("low"));
		assertFalse(order.contains("arm") || order.contains("mac"));
		assertEquals("mingw", org.dbgl.util.DosboxInstaller.buildKind("dosbox-x-mingw64-2026.08.31-portable.zip"));
		// TEST33: the name proposed for the folder: by build, and a free one when it is there with files
		org.dbgl.util.DosboxUpdateChecker.Release rx = new org.dbgl.util.DosboxUpdateChecker.Release("DOSBox-X", "2026.08.31", "u", List.of());
		assertEquals("dosbox-x-2026.08.31", org.dbgl.gui.controls.DosboxDownload.defaultFolderName(rx, "dosbox-x-vsbuild-win64-2026.08.31-portable.zip"));
		assertEquals("dosbox-x-2026.08.31-mingw", org.dbgl.gui.controls.DosboxDownload.defaultFolderName(rx, "dosbox-x-mingw64-2026.08.31-portable.zip"));
		assertEquals("dosbox-x-2026.08.31-mingw-lowend-32bit", org.dbgl.gui.controls.DosboxDownload.defaultFolderName(rx, "dosbox-x-mingw32_lowend-2026.08.31-portable.zip"));
		File base = Files.createTempDirectory("dbgl-dlname").toFile();
		assertEquals("dosbox-x-2026.08.31", org.dbgl.gui.controls.DosboxDownload.freeFolderName(base, "dosbox-x-2026.08.31"));
		new File(base, "dosbox-x-2026.08.31").mkdirs();
		Files.writeString(new File(base, "dosbox-x-2026.08.31/dosbox-x.exe").toPath(), "x");
		assertEquals("dosbox-x-2026.08.31-2", org.dbgl.gui.controls.DosboxDownload.freeFolderName(base, "dosbox-x-2026.08.31"));
		org.apache.commons.io.FileUtils.deleteDirectory(base);
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
		// alpha 49: the one folder all of the archive is in is left out
		File exe = new File(target, "dosbox.exe");
		assertTrue(exe.isFile());
		assertTrue(new File(target, "resources/shaders/x.glsl").isFile());
		assertFalse(new File(target, "dosbox-staging-v0.83.0").exists());
		File conf = new File(target, "dosbox.conf");
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
		// alpha 53: the kind in front and what tells it apart (DOSBox-X: Visual Studio or MinGW)
		org.dbgl.service.ITextService text = org.dbgl.service.TextService.getInstance();
		assertEquals("Visual Studio – dosbox-x-vsbuild-win64-20261001.zip   (" + text.get("dialog.main.notice.recommended") + ")",
			org.dbgl.gui.controls.DosboxDownload.buildLabel(c.get(0), true, text));
		assertEquals("MinGW – dosbox-x-mingw-win64-20261001.zip", org.dbgl.gui.controls.DosboxDownload.buildLabel(c.get(1), false, text));
		assertEquals(text.get("dialog.dosboxdownload.hint.mingw"), org.dbgl.gui.controls.DosboxDownload.buildHint(c.get(1), text));
		assertEquals("", org.dbgl.gui.controls.DosboxDownload.buildHint(new String[] {"dosbox-staging-windows-x64-v0.83.1.zip"}, text));
		assertNull(org.dbgl.util.DosboxInstaller.buildKind("dosbox-staging-linux-x86_64-v0.83.1.tar.xz"));

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
	@Test
	public void testExistsAsDosProgram() throws IOException {
		// "ran2.exe" is missing, but "RAN2.BAT" is there: DOS starts it when "ran2" is called, so no warning
		File dir = Files.createTempDirectory("dbgl-prog").toFile();
		Files.writeString(new File(dir, "RAN2.BAT").toPath(), "@echo off");
		Files.writeString(new File(dir, "readme.txt").toPath(), "x");
		assertTrue(org.dbgl.util.FilesUtils.existsAsDosProgram(new File(dir, "ran2.exe")));
		assertTrue(org.dbgl.util.FilesUtils.existsAsDosProgram(new File(dir, "ran2")));
		assertTrue(org.dbgl.util.FilesUtils.existsAsDosProgram(new File(dir, "Ran2.Bat")));
		assertFalse(org.dbgl.util.FilesUtils.existsAsDosProgram(new File(dir, "ran3.exe")));
		assertFalse(org.dbgl.util.FilesUtils.existsAsDosProgram(new File(dir, "readme.exe")));
		assertFalse(org.dbgl.util.FilesUtils.existsAsDosProgram(new File(dir, "ran2.txt")));
		assertFalse(org.dbgl.util.FilesUtils.existsAsDosProgram(new File(new File(dir, "gone"), "ran2.exe")));
		assertTrue(org.dbgl.util.EditionUpdateChecker.readable("0.101alpha42").equals("0.101 alpha 42"));
	}
	@Test
	public void testStagingShaderPresetsAndRefresh() throws IOException {
		// Staging 0.83 looks for presets as shader-presets/<shader>/<preset>.preset next to its shaders
		File dir = Files.createTempDirectory("dbgl-shaders").toFile();
		File exe = new File(dir, "dosbox.exe");
		Files.writeString(exe.toPath(), "MZ");
		File crt = new File(dir, "resources/shaders/crt");
		crt.mkdirs();
		Files.writeString(new File(crt, "crt-hyllian.glsl").toPath(), "//");
		File presets = new File(dir, "resources/shader-presets/crt/crt-hyllian");
		presets.mkdirs();
		Files.writeString(new File(presets, "vga-4k.preset").toPath(), "[settings]");
		Files.writeString(new File(dir, "resources/shader-presets/readme.preset").toPath(), "x"); // belongs to no shader
		List<String> found = DosboxVersionDetector.listShadersCached(exe, dir, true);
		assertTrue(found.toString(), found.contains("crt/crt-hyllian"));
		assertTrue(found.toString(), found.contains("crt/crt-hyllian:vga-4k"));
		assertFalse(found.toString(), found.stream().anyMatch(x -> x.contains("readme")));
		assertFalse(DosboxVersionDetector.listShadersCached(exe, dir, false).contains("crt/crt-hyllian:vga-4k")); // 0.81/0.82 have no presets

		// a shader pack copied there while DBGL runs: found after the caches are cleared (button "Refresh")
		Files.writeString(new File(crt, "crt-new.glsl").toPath(), "//");
		assertFalse(DosboxVersionDetector.listShadersCached(exe, dir, true).contains("crt/crt-new"));
		DosboxVersionDetector.clearShaderCaches();
		assertTrue(DosboxVersionDetector.listShadersCached(exe, dir, true).contains("crt/crt-new"));
	}
	@Test
	public void testImageSeries() throws IOException {
		// CD1.ISO chosen: CD2 and CD3 are offered too, sorted by number; other files and other games are not
		File dir = Files.createTempDirectory("dbgl-cds").toFile();
		for (String n: new String[] {"GAME_CD1.ISO", "game_cd3.iso", "GAME_CD2.ISO", "GAME_CD2.CUE", "OTHER_CD1.ISO", "readme.txt"})
			Files.writeString(new File(dir, n).toPath(), "x");
		List<File> series = org.dbgl.util.FilesUtils.findImageSeries(new File(dir, "GAME_CD1.ISO"));
		assertEquals(List.of("GAME_CD1.ISO", "GAME_CD2.ISO", "game_cd3.iso"), series.stream().map(File::getName).toList());
		assertEquals(1, org.dbgl.util.FilesUtils.findImageSeries(new File(dir, "OTHER_CD1.ISO")).size());
		assertEquals(1, org.dbgl.util.FilesUtils.findImageSeries(new File(dir, "readme.txt")).size());
	}

	@Test
	public void testSearchEngineErrorsExplained() {
		// a user gets told what happened, not a stack trace
		assertTrue(org.dbgl.util.searchengine.SearchEngineException.explain("MobyGames", new java.net.SocketTimeoutException("Read timed out")).getMessage().contains("MobyGames"));
		String blocked = org.dbgl.util.searchengine.SearchEngineException.explain("MobyGames", new org.jsoup.HttpStatusException("x", 429, "u")).getMessage();
		assertTrue(blocked, blocked.contains("429"));
		assertFalse(blocked.equals(org.dbgl.util.searchengine.SearchEngineException.explain("MobyGames", new org.jsoup.HttpStatusException("x", 500, "u")).getMessage()));
		assertTrue(org.dbgl.util.searchengine.SearchEngineException.explain("X", new java.net.UnknownHostException("h")).getMessage().length() > 10);
		assertEquals(org.dbgl.util.searchengine.SearchEngineException.changed("X", null).getMessage(),
			org.dbgl.util.searchengine.SearchEngineException.explain("X", new NullPointerException()).getMessage());
		assertTrue(org.dbgl.util.searchengine.SearchEngineException.explain("X", new java.io.IOException("wrapped", new java.net.SocketTimeoutException())).getMessage()
				.equals(org.dbgl.util.searchengine.SearchEngineException.explain("X", new java.net.SocketTimeoutException()).getMessage()));
	}
	@Test
	public void testExtrasFinder() throws IOException {
		File game = Files.createTempDirectory("dbgl-extras").toFile();
		File docs = new File(game, "DOCS");
		File music = new File(game, "Soundtrack");
		File sounds = new File(game, "SOUND");
		docs.mkdirs();
		music.mkdirs();
		sounds.mkdirs();
		for (String n: new String[] {"GAME.EXE", "README.TXT", "setup.txt", "DOCS/Manual.pdf", "DOCS/walkthrough.txt", "DOCS/Karte.jpg", "DOCS/cover.jpg", "Soundtrack/01 Intro.mp3",
				"SOUND/explode.wav", "SOUND/shot.voc", "reference card.pdf", "DOCS/Codewheel.jpg", "Kopierschutz.txt"})
			Files.writeString(new File(game, n).toPath(), "x");
		List<org.dbgl.util.ExtrasFinder.Extra> found = org.dbgl.util.ExtrasFinder.find(game);
		java.util.Map<String, org.dbgl.util.ExtrasFinder.Kind> byName = new java.util.HashMap<>();
		found.forEach(e -> byName.put(e.file().getName(), e.kind()));
		assertEquals(byName.toString(), org.dbgl.util.ExtrasFinder.Kind.MANUAL, byName.get("Manual.pdf"));
		assertEquals(org.dbgl.util.ExtrasFinder.Kind.REFERENCE, byName.get("reference card.pdf"));
		assertEquals(org.dbgl.util.ExtrasFinder.Kind.WALKTHROUGH, byName.get("walkthrough.txt"));
		assertEquals(org.dbgl.util.ExtrasFinder.Kind.COPYPROTECTION, byName.get("Codewheel.jpg"));
		assertEquals(org.dbgl.util.ExtrasFinder.Kind.COPYPROTECTION, byName.get("Kopierschutz.txt"));
		assertEquals(org.dbgl.util.ExtrasFinder.Kind.MAP, byName.get("Karte.jpg"));
		assertEquals(org.dbgl.util.ExtrasFinder.Kind.SOUNDTRACK, byName.get("Soundtrack")); // the folder, named so
		// not extras: the program, a readme, other pictures, sound effects of the game
		for (String n: new String[] {"GAME.EXE", "README.TXT", "setup.txt", "cover.jpg", "SOUND", "explode.wav", "01 Intro.mp3"})
			assertFalse(n, byName.containsKey(n));
	}
	@Test
	public void testExtrasFinderCollectionFolder() throws IOException {
		// SMARTDRV.EXE as main program in the folder of all games: not the manuals of all the games
		File games = Files.createTempDirectory("dbgl-games").toFile();
		Files.writeString(new File(games, "SMARTDRV.EXE").toPath(), "x");
		Files.writeString(new File(games, "games-list.pdf").toPath(), "x");
		for (int i = 0; i < 6; i++) {
			File g = new File(games, "GAME" + i);
			g.mkdirs();
			Files.writeString(new File(g, "GAME.EXE").toPath(), "x");
			Files.writeString(new File(g, "manual.pdf").toPath(), "x");
		}
		// the folders of the other games (they have profiles of their own) are left out
		java.util.Set<File> others = new java.util.HashSet<>();
		for (int i = 0; i < 6; i++)
			others.add(new File(games, "GAME" + i));
		List<org.dbgl.util.ExtrasFinder.Extra> found = org.dbgl.util.ExtrasFinder.find(games, others);
		assertEquals(found.toString(), 1, found.size());
		assertEquals("games-list.pdf", found.get(0).file().getName());
		// a game whose parts lie in subfolders without profiles of their own (like DSA): all of it is searched
		assertEquals(7, org.dbgl.util.ExtrasFinder.find(games).size());
		assertEquals(1, org.dbgl.util.ExtrasFinder.find(new File(games, "GAME0")).size());
	}

	@Test
	public void testStagingCdMountForOtherDosbox() throws Exception {
		// "mount d x.cue -t cdrom" (DOSBox Staging 0.83) under DOSBox-X: becomes "imgmount d x.cue -t iso"
		org.dbgl.model.conf.mount.Mount m = org.dbgl.model.factory.MountFactory.create("mount D \"JP CD-ROM.CUE\" -t cdrom");
		assertTrue(m instanceof org.dbgl.model.conf.mount.ImageMount);
		org.dbgl.model.conf.mount.ImageMount im = (org.dbgl.model.conf.mount.ImageMount)m;
		assertTrue(im.toString(false).toLowerCase().startsWith("mount d"));
		assertTrue(im.toString(false).contains("-t cdrom"));
		im.setCommand("imgmount");
		assertTrue(im.toString(false).toLowerCase().startsWith("imgmount d"));
		assertTrue(im.toString(false).contains("-t iso"));
	}

	@Test
	public void testExtraIcons() {
		assertEquals("extra_web.png", org.dbgl.util.ExtrasFinder.iconOf("MobyGames", "https://www.mobygames.com/game/1"));
		assertEquals("extra_manual.png", org.dbgl.util.ExtrasFinder.iconOf("x", "docs/Handbuch.pdf"));
		assertEquals("extra_copyprotection.png", org.dbgl.util.ExtrasFinder.iconOf("", "Codewheel.jpg"));
		assertEquals("extra_other.png", org.dbgl.util.ExtrasFinder.iconOf("", "readme.txt"));
		for (org.dbgl.util.ExtrasFinder.Kind k: org.dbgl.util.ExtrasFinder.Kind.values())
			assertNotNull(k.name(), getClass().getResource("/img/extra_" + k.name().toLowerCase() + ".png"));
		assertNotNull(getClass().getResource("/img/profile_conf.png"));
	}

	@Test
	public void testDocumentedValuesStaging083German() throws IOException {
		// written in the language of the system: no "Possible values:", but the list looks the same
		ConfValueOptions o = parse(new File(DATA_DIR, "dosbox-staging-0.83.0-de.conf"));
		assertValues(o, "cpu", "core", false, "auto", "normal", "simple", "dynamic");
		assertValues(o, "sblaster", "sbtype", false, "gb", "sb1", "sb2", "sbpro1", "sbpro2", "sb16", "ess", "none", "off");
		assertTrue(o.get("dosbox", "machine").getValues().contains("svga_s3"));
		assertTrue(o.get("sdl", "output").getValues().contains("opengl"));
	}

	@Test
	public void testShaderFiles() throws IOException {
		// a Staging 0.83 folder with one shader file of its own: that one is valid, one that is not there is reported
		File dir = Files.createTempDirectory("dbgl-staging").toFile();
		new File(dir, org.dbgl.service.FileLocationService.DOSBOX_EXE_STRING).createNewFile();
		File shader = new File(dir, "resources/shaders/scaler/advinterp2x.glsl");
		shader.getParentFile().mkdirs();
		Files.writeString(shader.toPath(), "// shader");
		org.dbgl.util.DosboxVersionDetector.clearShaderCaches();
		org.dbgl.model.aggregate.DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null,
			dir.getAbsolutePath(), "", "", new File(dir, "dosbox.conf").getAbsolutePath());
		File dosboxConfFile = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
		v083.loadConfigurationData(GenericTextService.getInstance(), Files.readString(dosboxConfFile.toPath(), StandardCharsets.UTF_8), dosboxConfFile);
		org.dbgl.model.aggregate.Profile profile = org.dbgl.model.factory.ProfileFactory.create(v083, null);
		Configuration c = profile.getConfiguration();
		c.setValue("render", "shader", "scaler/advinterp2x");
		assertEquals(0, profile.listInvalidValues().size());
		c.setValue("render", "shader", "crt-auto");
		assertEquals(0, profile.listInvalidValues().size());
		c.setValue("render", "shader", shader.getAbsolutePath());
		assertEquals(0, profile.listInvalidValues().size());
		c.setValue("render", "shader", "scaler/notthere");
		List<String[]> invalid = profile.listInvalidValues();
		assertEquals(1, invalid.size());
		assertEquals("shader", invalid.get(0)[4]);
		org.apache.commons.io.FileUtils.deleteQuietly(dir);
		org.dbgl.util.DosboxVersionDetector.clearShaderCaches();
	}

	private static org.json.JSONObject savedAnswer(String name) throws IOException {
		return new org.json.JSONObject(Files.readString(new File(DATA_DIR, "online/" + name).toPath(), StandardCharsets.UTF_8));
	}

	@Test
	public void testMobyGamesApiAnswers() throws IOException {
		// saved answers of the MobyGames API (https://www.mobygames.com/info/api): a change there shows up here
		assertEquals(List.of(2, 3), org.dbgl.util.searchengine.MobyGamesSearchEngine.Api.platformIds(new String[] {"DOS", "Windows", "Amiga 500"}));
		List<org.dbgl.model.WebProfile> entries = org.dbgl.util.searchengine.MobyGamesSearchEngine.Api.parseEntries(savedAnswer("mobygames-games.json"), List.of(2));
		assertEquals(2, entries.size()); // the Amiga and Windows versions are not wanted
		org.dbgl.model.WebProfile e = entries.get(0);
		assertEquals("The Secret of Monkey Island", e.getTitle());
		assertEquals("DOS", e.getPlatform());
		assertEquals("1990", e.getYear());
		assertEquals("Adventure", e.getGenre()); // only the basic genres
		assertEquals(1, e.getGameId());
		assertEquals(4, org.dbgl.util.searchengine.MobyGamesSearchEngine.Api.parseEntries(savedAnswer("mobygames-games.json"), List.of()).size()); // every platform
		org.dbgl.util.searchengine.MobyGamesSearchEngine.Api.Entry entry = (org.dbgl.util.searchengine.MobyGamesSearchEngine.Api.Entry)e;
		org.dbgl.util.searchengine.MobyGamesSearchEngine.Api.parseDetails(entry, savedAnswer("mobygames-platform.json"));
		assertEquals("Lucasfilm Games LLC", entry.getDeveloperName());
		assertEquals("Lucasfilm Games LLC, Softgold", entry.getPublisherName());
		assertEquals(86, entry.getRank());
		assertTrue(entry.getNotes().contains("pirate & more"));
		assertFalse(entry.getNotes().contains("<"));
		// a game without description and score
		org.dbgl.util.searchengine.MobyGamesSearchEngine.Api.Entry second = (org.dbgl.util.searchengine.MobyGamesSearchEngine.Api.Entry)entries.get(1);
		org.dbgl.util.searchengine.MobyGamesSearchEngine.Api.parseDetails(second, new org.json.JSONObject("{}"));
		assertEquals("", second.getNotes());
		assertEquals(0, second.getRank());
		var covers = org.dbgl.util.searchengine.MobyGamesSearchEngine.Api.parseCovers(savedAnswer("mobygames-covers.json"), 2);
		assertEquals(2, covers.size());
		assertEquals("https://cdn.mobygames.com/covers/front.jpg", covers.get(0).getUrl());
		assertEquals("Front Cover", covers.get(0).getDescription());
		var shots = org.dbgl.util.searchengine.MobyGamesSearchEngine.Api.parseScreenshots(savedAnswer("mobygames-screenshots.json"), 10);
		assertEquals(3, shots.size());
		assertEquals("Screenshot 2", shots.get(1).getDescription()); // no caption
	}

	@Test
	public void testIgdbAnswers() throws IOException {
		org.json.JSONArray search = new org.json.JSONArray(Files.readString(new File(DATA_DIR, "online/igdb-search.json").toPath(), StandardCharsets.UTF_8));
		List<org.dbgl.model.WebProfile> entries = org.dbgl.util.searchengine.IgdbSearchEngine.parseEntries(search);
		assertEquals(2, entries.size());
		assertEquals("The Secret of Monkey Island", entries.get(0).getTitle());
		assertEquals("1990", entries.get(0).getYear());
		assertEquals("DOS, Amiga", entries.get(0).getPlatform());
		assertEquals("", entries.get(1).getYear()); // no date
		org.dbgl.model.WebProfile e = entries.get(0);
		org.dbgl.util.searchengine.IgdbSearchEngine.parseDetails(e, savedAnswer("igdb-details.json"));
		assertEquals("Lucasfilm Games", e.getDeveloperName());
		assertEquals("Lucasfilm Games, U.S. Gold", e.getPublisherName());
		assertEquals("Adventure, Puzzle", e.getGenre());
		assertEquals(88, e.getRank());
		var images = org.dbgl.util.searchengine.IgdbSearchEngine.parseImages(savedAnswer("igdb-images.json"), 1, 2);
		assertEquals(3, images.size());
		assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/co1abc.jpg", images.get(0).getUrl());
		assertEquals("https://images.igdb.com/igdb/image/upload/t_screenshot_big/sc2.jpg", images.get(2).getUrl());
	}

	@Test
	public void testMissingLinks() throws IOException {
		org.dbgl.model.aggregate.DosboxVersion v = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null,
			"staging083", "", "", "staging083/dosbox.conf");
		org.dbgl.model.aggregate.Profile profile = org.dbgl.model.factory.ProfileFactory.create(v, null);
		File manual = Files.createTempFile("dbgl-manual", ".pdf").toFile();
		profile.setLinkDestination(0, manual.getAbsolutePath());
		profile.setLinkDestination(1, new File(manual.getParentFile(), "moved-away.pdf").getAbsolutePath());
		profile.setLinkDestination(2, "https://www.mobygames.com/game/1");
		profile.setLinkDestination(3, "file://" + new File(manual.getParentFile(), "gone.txt").getAbsolutePath());
		assertEquals(List.of(1, 3), profile.listMissingLinks()); // web pages and empty links are not checked
		assertNull(org.dbgl.model.aggregate.Profile.linkFile(profile.getLinks()[2]));
		manual.delete();
		assertEquals(List.of(0, 1, 3), profile.listMissingLinks());
	}

	@Test
	public void testScreenScraperAnswers() throws IOException {
		// saved answer of jeuRecherche.php (ScreenScraper API v2); [{}] at the end is how it says "nothing more found"
		org.json.JSONObject response = savedAnswer("screenscraper-search.json").getJSONObject("response");
		List<org.dbgl.model.WebProfile> entries = org.dbgl.util.searchengine.ScreenScraperSearchEngine.parseEntries(response);
		assertEquals(1, entries.size());
		org.dbgl.util.searchengine.ScreenScraperSearchEngine.Entry e = (org.dbgl.util.searchengine.ScreenScraperSearchEngine.Entry)entries.get(0);
		assertEquals("The Secret of Monkey Island", e.getTitle()); // the name for the whole world
		assertEquals("PC Dos", e.getPlatform());
		assertEquals("1990", e.getYear()); // the US date before the European one
		assertEquals(18471, e.getGameId());
		org.dbgl.util.searchengine.ScreenScraperSearchEngine.parseDetails(e, "de");
		assertEquals("Lucasfilm Games", e.getDeveloperName());
		assertEquals("Abenteuer, Point and Click", e.getGenre()); // German where there is, else English
		assertEquals("Guybrush will Pirat werden.", e.getNotes());
		assertEquals(85, e.getRank()); // 17 of 20
		org.dbgl.util.searchengine.ScreenScraperSearchEngine.parseDetails(e, "ko");
		assertEquals("Guybrush wants to be a pirate.", e.getNotes());
		var images = org.dbgl.util.searchengine.ScreenScraperSearchEngine.parseImages(e, 1, 5);
		assertEquals(3, images.size());
		assertTrue(images.get(0).getUrl().endsWith("box-2D(us)")); // US box before the European one
		assertTrue(images.get(1).getUrl().endsWith("sstitle")); // title screen first
		assertEquals(List.of(135, 138), org.dbgl.util.searchengine.ScreenScraperSearchEngine.systemIds(new String[] {"DOS", "Windows", "Atari"}));
		assertEquals(List.of(135), org.dbgl.util.searchengine.ScreenScraperSearchEngine.systemIds(new String[0]));
		assertEquals(Integer.valueOf(430), org.dbgl.util.searchengine.ScreenScraperSearchEngine.statusIn("ScreenScraper refused the request (HTTP 430)."));
		assertNull(org.dbgl.util.searchengine.ScreenScraperSearchEngine.statusIn("no connection"));
		assertTrue(org.dbgl.util.searchengine.ScreenScraperSearchEngine.getInstance().available()); // DBGL has a developer access since alpha 45
		assertEquals("DBGL-EE", org.dbgl.util.searchengine.ScreenScraperSearchEngine.reveal(java.util.Base64.getEncoder().encodeToString(new byte[7]))); // xor with the key
	}

	/** a GOG game as GOG installs it, and a Steam game with one dosbox.conf */
	private static File storeGames() throws IOException {
		File root = Files.createTempDirectory("dbgl-store").toFile();
		File duke = new File(root, "Duke Nukem 3D");
		new File(duke, "DOSBOX").mkdirs();
		new File(duke, "DUKE3D").mkdirs();
		new File(duke, "DOSBOX/DOSBox.exe").createNewFile();
		new File(duke, "DUKE3D/DUKE3D.EXE").createNewFile();
		new File(duke, "game.ins").createNewFile();
		Files.writeString(new File(duke, "goggame-1207658730.info").toPath(), "{\"gameId\":\"1207658730\",\"name\":\"Duke Nukem 3D: Atomic Edition\",\"playTasks\":[{\"isPrimary\":true,"
			+ "\"type\":\"FileTask\",\"path\":\"DOSBOX\\\\dosbox.exe\",\"workingDir\":\"DOSBOX\",\"arguments\":\"-conf \\\"..\\\\dosboxDUKE3D.conf\\\" -conf \\\"..\\\\dosboxDUKE3D_single.conf\\\" -noconsole -c \\\"exit\\\"\"}]}");
		Files.writeString(new File(duke, "dosboxDUKE3D.conf").toPath(), "[sdl]\nfullscreen=true\noutput=overlay\n[cpu]\ncore=auto\ncycles=fixed 30000\n[sblaster]\nsbtype=sb16\n[autoexec]\n");
		Files.writeString(new File(duke, "dosboxDUKE3D_single.conf").toPath(), "[autoexec]\n@echo off\ncls\nmount C \"..\"\nmount C \"..\\cloud_saves\" -t overlay\nimgmount d \"..\\game.ins\" -t iso -fs iso\nc:\ncd DUKE3D\nDUKE3D.EXE\nexit\n");
		File quake = new File(root, "steamapps/common/Quake");
		quake.mkdirs();
		new File(quake, "QUAKE.EXE").createNewFile();
		Files.writeString(new File(quake, "dosbox_quake.conf").toPath(), "[cpu]\ncycles=max\n[autoexec]\nmount c .\nc:\nQUAKE.EXE\nexit\n");
		new File(root, "other").mkdirs();
		Files.writeString(new File(root, "other/dosbox-x.conf").toPath(), "[sdl]\nfullscreen=false\n[autoexec]\n"); // a DOSBox, not a game
		return root;
	}

	@Test
	public void testStoreGameImport() throws IOException {
		File root = storeGames();
		List<org.dbgl.util.StoreGameImport.Found> found = org.dbgl.util.StoreGameImport.find(root);
		assertEquals(found.toString(), 2, found.size());
		org.dbgl.util.StoreGameImport.Found duke = found.stream().filter(f -> f.store().equals("GOG")).findFirst().orElseThrow();
		assertEquals("Duke Nukem 3D: Atomic Edition", duke.title());
		assertEquals(2, duke.confs().size());
		assertEquals("DOSBOX", duke.workingDir().getName());
		org.dbgl.util.StoreGameImport.Found quake = found.stream().filter(f -> f.store().equals("Steam")).findFirst().orElseThrow();
		assertEquals("Quake", quake.title());

		String combined = org.dbgl.util.StoreGameImport.combined(duke);
		assertFalse(combined, combined.contains("fullscreen")); // the screen is DBGL's business
		assertTrue(combined.contains("sbtype=sb16"));
		assertFalse(combined.contains("overlay"));
		assertFalse(combined.toLowerCase().contains("echo"));

		File dosboxConfFile = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
		org.dbgl.model.aggregate.DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null,
			"staging083", "", "", dosboxConfFile.getAbsolutePath());
		v083.resetAndLoadConfiguration();
		org.dbgl.model.aggregate.Profile p = org.dbgl.util.StoreGameImport.toProfile(duke, v083, GenericTextService.getInstance());
		assertEquals("Duke Nukem 3D: Atomic Edition", p.getTitle());
		var mounts = p.getConfiguration().getAutoexec().getMountingpoints();
		assertEquals(mounts.toString(), 2, mounts.size());
		assertTrue(mounts.get(1) instanceof org.dbgl.model.conf.mount.ImageMount);
		assertEquals(new File(duke.gameDir(), "game.ins").getCanonicalFile(), ((org.dbgl.model.conf.mount.ImageMount)mounts.get(1)).getCanonicalImgPaths()[0].getCanonicalFile());
		assertEquals(new File(duke.gameDir(), "DUKE3D/DUKE3D.EXE").getCanonicalFile(), new File(p.getConfiguration().getAutoexec().getMain()).getCanonicalFile());
		assertFalse(p.getConfiguration().hasValue("sblaster", "sbtype")); // sb16 is what Staging uses anyway: not written twice
		org.dbgl.model.aggregate.Profile q = org.dbgl.util.StoreGameImport.toProfile(quake, v083, GenericTextService.getInstance());
		assertEquals(new File(quake.gameDir(), "QUAKE.EXE").getCanonicalFile(), new File(q.getConfiguration().getAutoexec().getMain()).getCanonicalFile());
		// Steam's X-COM: dosbox.exe and a DOSBox 0.72 dosbox.conf in the game folder, a "dosbox" folder with only the documentation
		File xcom = new File(root, "steamapps/common/xcom ufo defense");
		new File(xcom, "dosbox").mkdirs();
		new File(xcom, "XCOM").mkdirs();
		new File(xcom, "dosbox.exe").createNewFile();
		new File(xcom, "dosbox/README.txt").createNewFile();
		new File(xcom, "XCOM/UFOCD.BAT").createNewFile();
		Files.writeString(new File(xcom, "dosbox.conf").toPath(), "# This is the configurationfile for DOSBox 0.72.\n[sdl]\nfullscreen=true\n[dosbox]\nmachine=vga\nmemsize=16\n[cpu]\ncore=normal\ncycles=max\n"
			+ "[sblaster]\nsbtype=sb16\nmixer=true\n[speaker]\npcspeaker=true\n[autoexec]\n# Lines in this section will be run at startup.\n@echo off\nmount c XCOM -freesize 1024\nc:\ncd XCOM\ncall ufocd.bat\nexit\n");
		org.dbgl.util.StoreGameImport.Found x = org.dbgl.util.StoreGameImport.find(root).stream().filter(f -> f.gameDir().getName().equals("xcom ufo defense")).findFirst().orElseThrow();
		assertEquals(xcom.getCanonicalFile(), x.workingDir().getCanonicalFile()); // not the "dosbox" folder
		org.dbgl.model.aggregate.Profile xp = org.dbgl.util.StoreGameImport.toProfile(x, v083, GenericTextService.getInstance());
		var xm = xp.getConfiguration().getAutoexec().getMountingpoints();
		assertEquals(xm.toString(), 1, xm.size());
		assertTrue(xm.get(0).toString(), xm.get(0).toString().contains("XCOM"));
		// "cd XCOM" on C: (which is ...\XCOM already) fails in DOSBox, so UFOCD.BAT is started from C:\
		assertEquals(new File(xcom, "XCOM/UFOCD.BAT").getCanonicalPath().toLowerCase(), new File(xp.getConfiguration().getAutoexec().getMain()).getCanonicalPath().toLowerCase());
		assertFalse(xp.getConfiguration().hasValue("sblaster", "mixer")); // unknown to Staging 0.83
		assertFalse(xp.getConfiguration().hasValue("cpu", "cycles")); // converted for Staging
		assertTrue(xp.listInvalidValues().isEmpty());
		System.out.println("X-COM for Staging 0.83: " + xp.getConfiguration().getSectionNames() + " " + xm);
		// GOG's World of Xeen: its own menu (choice, goto) -> the game is the program, the settings program the setup
		File xeen = new File(root, "World of Xeen");
		new File(xeen, "DOSBOX").mkdirs();
		new File(xeen, "DOSBOX/DOSBox.exe").createNewFile();
		new File(xeen, "XEEN.EXE").createNewFile();
		new File(xeen, "INSTALL.EXE").createNewFile();
		Files.writeString(new File(xeen, "dosboxMM4-5.conf").toPath(), "# This is the configuration file for DOSBox 0.74-2.\n[dosbox]\nmemsize=16\n[cpu]\ncycles=fixed 20000\n");
		Files.writeString(new File(xeen, "dosboxMM4-5_single.conf").toPath(), "[autoexec]\n# Lines in this section will be run at startup.\n\n@ECHO OFF\nmount C \"..\"\nmount C \"..\\cloud_saves\" -t overlay\n"
			+ "c:\ngoto launcher\n\n:launcher\ncls\nECHO  1) World of Xeen\nECHO  2) Game DOS Settings\n\nchoice /c123 /s Which program do you want to run? [1-3]: /n\nif errorlevel 3 goto exit\n"
			+ "if errorlevel 2 goto setup\nif errorlevel 1 goto game\n\n:game\ncls\nxeen.exe\ngoto exit\n\n:setup\ncls\ninstall.exe\ngoto launcher\n\n:exit\nexit\n");
		org.dbgl.util.StoreGameImport.Found w = org.dbgl.util.StoreGameImport.find(root).stream().filter(f -> f.gameDir().getName().equals("World of Xeen")).findFirst().orElseThrow();
		String wc = org.dbgl.util.StoreGameImport.combined(w);
		assertFalse(wc, wc.toLowerCase().contains("choice"));
		assertFalse(wc, wc.toLowerCase().contains("goto"));
		org.dbgl.model.aggregate.Profile wp = org.dbgl.util.StoreGameImport.toProfile(w, v083, GenericTextService.getInstance());
		assertEquals(new File(xeen, "XEEN.EXE").getCanonicalPath().toLowerCase(), new File(wp.getConfiguration().getAutoexec().getMain()).getCanonicalPath().toLowerCase());
		assertEquals(new File(xeen, "INSTALL.EXE").getCanonicalFile(), new File(wp.getSetupString()).getCanonicalFile());
		assertEquals(1, wp.getConfiguration().getAutoexec().getMountingpoints().size());
		// a DOSBox SVN-Daum bundle (Earthsiege 2): Windows 95 booted from a hard disk image, the CD on the IDE bus, "imgmount d:" with a colon
		File es = new File(root, "earthsiege ii");
		new File(es, "CD").mkdirs();
		for (String n: new String[] {"dosbox.exe", "250.img", "CD/track01.cue"})
			new File(es, n).createNewFile();
		Files.writeString(new File(es, "dosbox.conf").toPath(), "# This is the configuration file for DOSBox SVN-Daum.\n[cpu]\ncore=dynamic\ncycles=max 105%\n[autoexec]\nlgdfxrom 86c764x1.bin\n"
			+ "imgmount 2 250.img -size 512,63,16,520 -t hdd -fs none\nimgmount d: \"CD\\track01.cue\" -t iso -fs iso -ide 2m\nboot -l c\n");
		org.dbgl.util.StoreGameImport.Found e = org.dbgl.util.StoreGameImport.find(root).stream().filter(f -> f.gameDir().getName().equals("earthsiege ii")).findFirst().orElseThrow();
		org.dbgl.model.aggregate.Profile ep = org.dbgl.util.StoreGameImport.toProfile(e, v083, GenericTextService.getInstance());
		var em = ep.getConfiguration().getAutoexec().getMountingpoints();
		assertEquals(em.toString(), 2, em.size()); // the CD is there (before, "d:" made it an invalid mount and it was left out)
		assertTrue(em.get(1).toString(), em.get(1).toString().contains("track01.cue"));
		assertFalse(em.get(1).toString(), em.get(1).toString().contains("-ide")); // Staging has no IDE bus
		assertEquals("max", ep.getConfiguration().getValue("cpu", "cpu_cycles"));
		assertFalse(String.valueOf(ep.getConfiguration().getValue("cpu", "cpu_cycles_protected")).contains("%"));
		assertEquals("ide", org.dbgl.util.StoreGameImport.needsDosboxX(e)); // the import recommends DOSBox-X for it
		assertTrue(ep.getConfiguration().getAutoexec().toString(), ep.getConfiguration().getAutoexec().toString().contains("boot -l C")); // the hard disk by its drive, not the file again
		assertNull(org.dbgl.util.StoreGameImport.needsDosboxX(duke));
		// alpha 49: what DOSBox-X has elsewhere than Daum and ECE (video memory, 3dfx card), if the game changed it
		String daum = "# This is the configuration file for DOSBox SVN-Daum. (Please use the latest version of DOSBox)";
		assertEquals("{video|vmemsize=4, voodoo|voodoo_card=opengl}", org.dbgl.util.StoreGameImport.forDosboxX(daum,
			"[dosbox]\nvmemsize=4\nmemsize=512\n\n[pci]\nvoodoo=OpenGL\n\n[glide]\nglide=emu\nlfb=full\nsplash=true\n\n[autoexec]\nvmemsize=9\n").toString());
		assertEquals("{voodoo|lfb=none}", org.dbgl.util.StoreGameImport.forDosboxX(daum, "[dosbox]\r\nvmemsize = 2\r\n[pci]\r\nvoodoo=auto\r\n[glide]\r\nglide=true\r\nlfb=none\r\n").toString());
		assertEquals("{voodoo|voodoo_card=false}", org.dbgl.util.StoreGameImport.forDosboxX("# This is the configuration file for DOSBox ECE r4482. (Please use the latest version of DOSBox)",
			"[pci]\nvoodoo=false\n[glide]\nlfb=full_noaux\n").toString());
		assertTrue(org.dbgl.util.StoreGameImport.forDosboxX("# This is the configurationfile for DOSBox 0.74. (Please use the latest version of DOSBox)", "[dosbox]\nvmemsize=4\n").isEmpty());
		assertTrue(org.dbgl.util.StoreGameImport.forDosboxX(null, "[pci]\nvoodoo=opengl\n").isEmpty());
		// for Staging 0.83: the CD image and the hard disk image for booting both become MOUNT
		ep.adaptImageMountsToDosbox();
		assertEquals("mount", ((org.dbgl.model.conf.mount.ImageMount)em.get(0)).getCommand().toLowerCase());
		assertEquals("mount", ((org.dbgl.model.conf.mount.ImageMount)em.get(1)).getCommand().toLowerCase());
		String auto = ep.getConfiguration().getAutoexec().toString();
		assertTrue(auto, auto.contains("-size 512,63,16,520") && auto.contains("-fs none") && auto.contains("boot -l C")); // nothing of the booter lost
		// and back: switched to DOSBox-X, both are IMGMOUNT again, with everything the hard disk image needs
		org.dbgl.model.aggregate.DosboxVersion vx = DosboxVersionFactory.create("DOSBox-X", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, "2025.10.07", false, true, false, null,
			"dosboxx", "", "", "dosboxx/dosbox-x.conf");
		// alpha 49: for Staging nothing about the mouse is said (it is DOSBox-X that passes the mouse on only when captured)
		assertFalse(org.dbgl.util.ProfileChecks.bootWithoutMouseCapture(ep));
		ep.setDosboxVersion(vx);
		assertTrue(ep.adaptImageMountsToDosbox());
		// alpha 49: DOSBox-X boots Windows 95 and does not capture the mouse on its own: said, until one of the two is set
		assertTrue(org.dbgl.util.ProfileChecks.bootWithoutMouseCapture(ep));
		ep.getConfiguration().setValue("sdl", "mouse_emulation", "always");
		assertFalse(org.dbgl.util.ProfileChecks.bootWithoutMouseCapture(ep));
		ep.getConfiguration().setValue("sdl", "mouse_emulation", "locked");
		assertTrue(org.dbgl.util.ProfileChecks.bootWithoutMouseCapture(ep));
		ep.getConfiguration().setValue("sdl", "autolock", "true");
		assertFalse(org.dbgl.util.ProfileChecks.bootWithoutMouseCapture(ep));
		ep.getConfiguration().removeValue("sdl", "autolock");
		ep.getConfiguration().removeValue("sdl", "mouse_emulation");
		assertEquals("imgmount", ((org.dbgl.model.conf.mount.ImageMount)em.get(0)).getCommand().toLowerCase());
		assertEquals("imgmount", ((org.dbgl.model.conf.mount.ImageMount)em.get(1)).getCommand().toLowerCase());
		auto = ep.getConfiguration().getAutoexec().toString();
		assertTrue(auto, auto.contains("-size 512,63,16,520") && auto.contains("-fs none") && auto.contains("boot -l C"));
		// a PC-98 image (.hdi): only DOSBox-X can mount it, a warning for any other DOSBox, and the import recommends DOSBox-X
		org.dbgl.model.aggregate.Profile hp = org.dbgl.model.factory.ProfileFactory.create(v083, null);
		hp.addMount("imgmount 2 \"" + new File(root, "win95.hdi").getPath() + "\" -t hdd");
		assertTrue(hp.needsDosboxXForPc98());
		hp.setDosboxVersion(vx);
		assertFalse(hp.needsDosboxXForPc98());
		File pc = new File(root, "pc98 game");
		pc.mkdirs();
		new File(pc, "dosbox.exe").createNewFile();
		new File(pc, "game.hdi").createNewFile();
		Files.writeString(new File(pc, "dosbox.conf").toPath(), "[autoexec]\nimgmount 2 game.hdi\nboot -l c\n");
		org.dbgl.util.StoreGameImport.Found pf = org.dbgl.util.StoreGameImport.find(root).stream().filter(f -> f.gameDir().getName().equals("pc98 game")).findFirst().orElseThrow();
		assertEquals("pc98", org.dbgl.util.StoreGameImport.needsDosboxX(pf));
		org.apache.commons.io.FileUtils.deleteQuietly(root);
	}

	@Test
	public void testDuplicateProfiles() {
		org.dbgl.model.aggregate.DosboxVersion v = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null,
			"staging083", "", "", "staging083/dosbox.conf");
		java.util.function.Function<String[], org.dbgl.model.aggregate.Profile> make = a -> {
			org.dbgl.model.aggregate.Profile p = org.dbgl.model.factory.ProfileFactory.create(v, null);
			p.setTitle(a[0]); p.setDeveloper(a[2]); p.setPublisher(""); p.setGenre(""); p.setYear(a[3]); p.setStatus(""); p.setNotes(a[4]);
			p.getConfiguration().getAutoexec().setMain(a[1]);
			return p;
		};
		org.dbgl.model.aggregate.Profile doom = make.apply(new String[] {"Doom", "GAMES/DOOM/DOOM.EXE", "id Software", "", ""});
		org.dbgl.model.aggregate.Profile doom2 = make.apply(new String[] {"DOOM (GOG)", "games/doom/doom.exe", "", "1993", "GOG: C:\\GOG Games\\DOOM"});
		org.dbgl.model.aggregate.Profile setup = make.apply(new String[] {"Doom Setup", "GAMES/DOOM/SETUP.EXE", "", "", ""});
		org.dbgl.model.aggregate.Profile none = make.apply(new String[] {"Empty", "", "", "", ""});
		doom2.setLinkDestination(0, "https://www.mobygames.com/game/1069/doom/");
		doom2.setLinkTitle(0, "MobyGames");
		List<org.dbgl.util.DuplicateProfiles.Group> groups = org.dbgl.util.DuplicateProfiles.findLoaded(List.of(doom, doom2, setup, none));
		assertEquals(1, groups.size()); // the setup starts another program, the empty one none
		assertEquals(2, groups.get(0).profiles().size());
		org.dbgl.model.aggregate.Profile keep = groups.get(0).profiles().get(0); // the one with more information
		org.dbgl.model.aggregate.Profile other = groups.get(0).profiles().get(1);
		org.dbgl.util.DuplicateProfiles.merge(keep, List.of(other));
		assertEquals("id Software", keep.getDeveloper());
		assertEquals("1993", keep.getYear());
		assertTrue(keep.getNotes().contains("GOG"));
		assertTrue(java.util.Arrays.stream(keep.getLinks()).anyMatch(l -> l != null && "https://www.mobygames.com/game/1069/doom/".equals(l.getDestination())));
		org.dbgl.util.DuplicateProfiles.merge(keep, List.of(other)); // twice: nothing doubled
		assertEquals(1, java.util.Arrays.stream(keep.getLinks()).filter(l -> l != null && "https://www.mobygames.com/game/1069/doom/".equals(l.getDestination())).count());
		assertEquals(1, keep.getNotes().split("GOG:").length - 1);
	}

	/** alpha 46: a value of DOSBox 0.74 that DOSBox Staging knows under a shorter name is set to it, not removed (cputype=386_slow) */
	@Test
	public void testNearValueInsteadOfRemoving() {
		String staging = "auto, 386, 386_fast, 386_prefetch, 486, pentium, pentium_mmx";
		assertEquals("386", org.dbgl.util.ProfileFindings.nearValue("386_slow", staging));
		assertEquals("486", org.dbgl.util.ProfileFindings.nearValue("486_SLOW", staging));
		assertEquals("pentium", org.dbgl.util.ProfileFindings.nearValue("pentium_slow", staging));
		assertNull(org.dbgl.util.ProfileFindings.nearValue("286", staging)); // nothing near: removed as before
		assertNull(org.dbgl.util.ProfileFindings.nearValue("386_slow", "auto, 486")); // the shorter one not there either
	}

	/** alpha 46: "cd" lines before the drive change act on Z:; moved behind it, also once their quote was removed */
	@Test
	public void testCdLinesMovedBehindTheDriveChange() {
		String conf = "[cpu]\ncycles=5000\n\n[autoexec]\n@echo off\ncd \\DSA\ncd DSA1\"\nmount D \"x.cue\" -t cdrom\nC:\ncls\ncall SCHICK.BAT\nexit\n";
		org.dbgl.util.ProfileFindings.ConfText t = org.dbgl.util.ProfileFindings.ConfText.of(conf);
		t.moveBehind(List.of("cd \\DSA", "cd DSA1\""), "C:");
		assertEquals("[cpu]\ncycles=5000\n\n[autoexec]\n@echo off\nmount D \"x.cue\" -t cdrom\nC:\ncd \\DSA\ncd DSA1\"\ncls\ncall SCHICK.BAT\nexit\n", t.text());
		t = org.dbgl.util.ProfileFindings.ConfText.of(conf);
		assertTrue(t.replaceLine("cd DSA1\"", "cd DSA1"));
		t.moveBehind(List.of("cd \\DSA", "cd DSA1\""), "C:");
		assertEquals("[cpu]\ncycles=5000\n\n[autoexec]\n@echo off\nmount D \"x.cue\" -t cdrom\nC:\ncd \\DSA\ncd DSA1\ncls\ncall SCHICK.BAT\nexit\n", t.text());
		t = org.dbgl.util.ProfileFindings.ConfText.of(conf); // the drive change not there: nothing moved
		t.moveBehind(List.of("cd \\DSA"), "E:");
		assertEquals(conf, t.text());
	}

	/** alpha 46: Caesar starts Cohort II if it is installed in its folder: a hint, not a missing program */
	@Test
	public void testCaesarStartsCohortIfThere() {
		assertTrue(org.dbgl.util.ProfileChecks.isCompanion(new File("/games/CAESAR/CAESAR.BAT"), "COHORT2.EXE"));
		assertTrue(org.dbgl.util.ProfileChecks.isCompanion(new File("/games/Caesar I/START.BAT"), "cohort"));
		assertFalse(org.dbgl.util.ProfileChecks.isCompanion(new File("/games/CAESAR/CAESAR.BAT"), "CAESAR.EXE"));
		assertFalse(org.dbgl.util.ProfileChecks.isCompanion(new File("/games/OTHER/GO.BAT"), "COHORT2.EXE"));
	}

	/** alpha 46: the version of the GUS patch library from the head of its ULTRASND.INI */
	@Test
	public void testGusPatchVersion() throws IOException {
		File dir = java.nio.file.Files.createTempDirectory("dbglgus").toFile();
		java.nio.file.Files.writeString(new File(dir, "ULTRASND.INI").toPath(), "#==============================\r\n# Ver:     2.1\r\n# Title:   ULTRASOUND Patch Library for Windows\r\n[Ultrasound]\r\nPatchDir=C:\\ULTRASND\\MIDI\\\r\n");
		assertEquals("2.1", org.dbgl.util.MidiRoms.patchVersion(dir));
		java.nio.file.Files.writeString(new File(dir, "ULTRASND.INI").toPath(), "[Ultrasound]\r\n# Ver: 9.9\r\n");
		assertNull(org.dbgl.util.MidiRoms.patchVersion(dir)); // not in its head: no version
	}

	/** alpha 47: MT-32 ROMs recognised by their checksums, as DOSBox does it (Munt): a model needs its control and its PCM ROM */
	@Test
	public void testMt32RomsByChecksum() {
		String ctrl107 = "b083518fffb7f66b03c23b7eb4f868e62dc5a987", ctrlCm102 = "a439fbb390da38cada95a7cbb1d6ca199cd66ef8";
		String pcmMt32 = "f6b1eebc4b2d200ec6d3d21d51325d5b48c60252", pcmCm32l = "289cc298ad532b702461bfc738009d9ebe8025ea", pcmCm32lHigh = "3ad889fde5db5b6437cbc2eb6e305312fec3df93";
		assertEquals(java.util.Set.of("MT-32 v1.07"), org.dbgl.util.MidiRoms.mt32ModelsOfSums(List.of(ctrl107, pcmMt32)));
		assertEquals(java.util.Set.of("CM-32L v1.02"), org.dbgl.util.MidiRoms.mt32ModelsOfSums(List.of(ctrlCm102, pcmCm32l)));
		assertEquals(java.util.Set.of("CM-32L v1.02", "MT-32 v1.07"), org.dbgl.util.MidiRoms.mt32ModelsOfSums(List.of(ctrl107, ctrlCm102, pcmMt32, pcmCm32lHigh)));
		assertEquals(java.util.Set.of(), org.dbgl.util.MidiRoms.mt32ModelsOfSums(List.of(ctrl107))); // no PCM ROM: nothing whole
		assertEquals(java.util.Set.of(), org.dbgl.util.MidiRoms.mt32ModelsOfSums(List.of(ctrlCm102, pcmMt32))); // the PCM ROM of the MT-32 alone is not the one of the CM-32L
		assertEquals(java.util.Set.of("MT-32 v1.07"), org.dbgl.util.MidiRoms.mt32ModelsOfSums(List.of("13f06b38f0d9e0fc050b6503ab777bb938603260", "c55e165487d71fa88bd8c5e9c083bc456c1a89aa", pcmMt32))); // halves
	}

	private static File image(long size, java.util.function.Consumer<byte[]> first, byte[] footer) throws IOException {
		File f = File.createTempFile("dbgl-geometry-", ".img");
		f.deleteOnExit();
		try (java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "rw")) {
			r.setLength(size);
			byte[] s = new byte[512];
			first.accept(s);
			r.write(s);
			if (footer != null) {
				r.seek(size - 512);
				r.write(footer);
			}
		}
		return f;
	}

	/** alpha 48: the geometry for "-size" from a partition table, a boot sector or a VHD footer; nothing from other files */
	@Test
	public void testDiskGeometry() throws IOException {
		long size = 20L * 1024 * 1024;
		File mbr = image(size, s -> {
			s[510] = 0x55;
			s[511] = (byte)0xaa;
			s[446] = (byte)0x80;
			s[446 + 4] = 0x06; // FAT16
			s[446 + 5] = 15; // end head: 16 heads
			s[446 + 6] = 63; // end sector: 63
		}, null);
		org.dbgl.util.DiskGeometry.Result r = org.dbgl.util.DiskGeometry.of(mbr);
		assertNotNull(r);
		assertEquals("mbr", r.source());
		assertEquals("512,63,16,40", r.size().toString());

		File bpb = image(size, s -> {
			s[0] = (byte)0xeb;
			s[0x0b] = 0;
			s[0x0c] = 2; // 512 bytes per sector
			s[0x18] = 32; // sectors per track
			s[0x1a] = 64; // heads
			s[510] = 0x55;
			s[511] = (byte)0xaa;
		}, null);
		assertEquals("512,32,64,20", org.dbgl.util.DiskGeometry.of(bpb).size().toString());

		byte[] footer = new byte[512];
		System.arraycopy("conectix".getBytes(StandardCharsets.ISO_8859_1), 0, footer, 0, 8);
		footer[0x38] = 0x01;
		footer[0x39] = 0x2c; // 300 cylinders
		footer[0x3a] = 4;
		footer[0x3b] = 17;
		footer[0x3f] = 2; // fixed
		r = org.dbgl.util.DiskGeometry.of(image(size + 512, s -> {}, footer));
		assertEquals("vhd", r.source());
		assertEquals("512,17,4,300", r.size().toString());
		footer[0x3f] = 3; // dynamic: not usable as it is
		assertNull(org.dbgl.util.DiskGeometry.of(image(size + 512, s -> {}, footer)));

		assertNull(org.dbgl.util.DiskGeometry.of(image(size, s -> {}, null))); // nothing in it
		assertNull(org.dbgl.util.DiskGeometry.of(image(1474560, s -> {
			s[510] = 0x55;
			s[511] = (byte)0xaa;
		}, null))); // a floppy: DOSBox knows it by its size
		assertNull(org.dbgl.util.DiskGeometry.of(new File("does-not-exist.img")));
	}

	/** alpha 48: the controller assistant changes only the lines of the binds it assigns, the others stay as DOSBox wrote them */
	@Test
	public void testMapperFileAssign() {
		String text = "hand_shutdown \"key 66 mod1\" \"stick_0 button 7 mod3\" \r\nkey_esc \"key 41\"\r\nkey_space \"key 44\" \r\nkey_lctrl \"key 224\" \r\n"
				+ "key_up \"key 82\" \r\njbutton_0_0 \"stick_0 button 0\" \r\njhat_0_0_0 \"stick_0 hat 0 1\" \r\n";
		org.dbgl.util.MapperFile m = new org.dbgl.util.MapperFile(text);
		assertEquals(List.of("key_esc", "key_space", "key_lctrl", "key_up"), m.keyEvents());
		assertNull(m.keyOf("stick_0 button 0"));
		m.assign("stick_0 button 0", "key_space");
		m.assign("stick_0 hat 0 1", "key_up");
		m.assign("stick_0 button 7", "key_esc");
		assertEquals("key_space", m.keyOf("stick_0 button 0"));
		String out = m.text();
		assertTrue(out, out.contains("\r\nkey_space \"key 44\" \"stick_0 button 0\" \r\n"));
		assertTrue(out, out.contains("\r\njbutton_0_0 \"stick_0 button 0\" \r\n")); // the emulated joystick keeps it (joysticktype=hidden)
		assertTrue(out, out.startsWith("hand_shutdown \"key 66 mod1\" \"stick_0 button 7 mod3\" \r\n")); // with Ctrl: a bind of its own
		assertTrue(out, out.contains("\r\nkey_lctrl \"key 224\" \r\n"));
		m.assign("stick_0 button 0", "key_lctrl"); // moved
		assertEquals("key_lctrl", m.keyOf("stick_0 button 0"));
		assertTrue(m.text().contains("\r\nkey_space \"key 44\" \r\n"));
		m.assign("stick_0 button 0", null); // removed
		assertNull(m.keyOf("stick_0 button 0"));
		assertEquals(7, m.eventCount());
		assertEquals(text.split("\r\n", -1).length, m.text().split("\r\n", -1).length);
		// unchanged: exactly as read
		assertEquals(text, new org.dbgl.util.MapperFile(text).text());
	}

	/** alpha 48: a changed value in the way the line was written; the end of the file as it was (no line end after "exit") */
	@Test
	public void testChangedValueKeepsItsSpacing() {
		String before = "[dos]\r\nems = false\r\nxms =true\r\n\r\n[autoexec]\r\nexit";
		String after = "[dos]\r\nems=off\r\nxms=on\r\n\r\n[autoexec]\r\nexit\r\n";
		assertEquals("[dos]\r\nems = off\r\nxms =on\r\n\r\n[autoexec]\r\nexit", org.dbgl.model.conf.ConfLayout.keep(before, after));
		assertEquals("[dos]\r\nems = off\r\nxms =on\r\n\r\n[autoexec]\r\nexit\r\n", org.dbgl.model.conf.ConfLayout.keep(before + "\r\n", after));
	}

	/** alpha 48: an eXoDOS collection: installed games with LaunchBox's data and cover, the others counted; paths from its folder */
	@Test
	public void testExoImport() throws IOException {
		File root = Files.createTempDirectory("dbgl-exo-").toFile();
		File bang = new File(root, "eXo/eXoDOS/!dos/1942");
		bang.mkdirs();
		Files.writeString(new File(bang, "1942 - The Pacific Air War (1994).bat").toPath(), "@echo off\r\n");
		Files.writeString(new File(bang, "dosbox.conf").toPath(), "[sdl]\r\nfullscreen=true\r\n\r\n[cpu]\r\ncycles=fixed 15000\r\n\r\n[dos]\r\nxms=false\r\n\r\n"
				+ "[autoexec]\r\n@echo off\r\nmount c .\\eXo\\eXoDOS\\1942\\\r\nc:\r\ncls\r\ncd 1942\r\n1942.EXE\r\nexit\r\n");
		File game = new File(root, "eXo/eXoDOS/1942/1942");
		game.mkdirs();
		Files.writeString(new File(game, "1942.EXE").toPath(), "MZ");
		File other = new File(root, "eXo/eXoDOS/!dos/keen1");
		other.mkdirs();
		Files.writeString(new File(other, "dosbox.conf").toPath(), "[autoexec]\r\nmount c .\\eXo\\eXoDOS\\keen1\\\r\n");
		File platforms = new File(root, "Data/Platforms");
		platforms.mkdirs();
		Files.writeString(new File(platforms, "MS-DOS.xml").toPath(), "<?xml version=\"1.0\" standalone=\"yes\"?>\r\n<LaunchBox>\r\n  <Game>\r\n"
				+ "    <ApplicationPath>eXo\\eXoDOS\\!dos\\1942\\1942 - The Pacific Air War (1994).bat</ApplicationPath>\r\n    <Title>1942: The Pacific Air War</Title>\r\n"
				+ "    <Developer>MicroProse</Developer>\r\n    <Publisher>MicroProse Software, Inc.</Publisher>\r\n    <ReleaseDate>1994-01-01T00:00:00-08:00</ReleaseDate>\r\n"
				+ "    <Genre>Simulation; Flight</Genre>\r\n    <Notes>A flight simulator &amp; more.</Notes>\r\n  </Game>\r\n</LaunchBox>\r\n");
		File covers = new File(root, "Images/MS-DOS/Box - Front/North America");
		covers.mkdirs();
		Files.write(new File(covers, "1942_ The Pacific Air War-01.jpg").toPath(), new byte[] {1, 2, 3});

		org.dbgl.util.ExoImport.Result r = org.dbgl.util.ExoImport.find(new File(root, "eXo/eXoDOS")); // a folder below is fine
		assertNotNull(r);
		assertEquals(root.getCanonicalFile(), r.root().getCanonicalFile());
		assertEquals(1, r.games().size());
		assertEquals(1, r.notInstalled());
		org.dbgl.util.StoreGameImport.Found f = r.games().get(0);
		assertEquals("1942: The Pacific Air War", f.title());
		assertEquals("eXoDOS", f.store());
		assertEquals("1994", f.meta().year());
		assertEquals("MicroProse", f.meta().developer());
		assertEquals("A flight simulator & more.", f.meta().notes());
		assertEquals("1942_ The Pacific Air War-01.jpg", f.meta().cover().get().getName());

		File dosboxConf = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
		org.dbgl.model.aggregate.DosboxVersion v = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, "staging083", "", "",
			dosboxConf.getAbsolutePath());
		v.resetAndLoadConfiguration();
		org.dbgl.model.aggregate.Profile p = org.dbgl.util.StoreGameImport.toProfile(f, v, GenericTextService.getInstance());
		assertEquals("MicroProse Software, Inc.", p.getPublisher());
		assertEquals("Simulation; Flight", p.getGenre());
		assertTrue(p.getNotes(), p.getNotes().endsWith("eXoDOS: " + f.gameDir().getPath()));
		assertEquals("1942.EXE", new File(p.getConfiguration().getAutoexec().getMain()).getName());
		assertTrue(p.getConfiguration().getAutoexec().getMountingpoints().get(0).toString(), p.getConfiguration().getAutoexec().getMountingpoints().get(0).toString().contains(new File(root, "eXo").getPath()));
		assertEquals("off", p.getConfiguration().getValue("dos", "xms")); // converted for Staging 0.83

		// alpha 53: the collection as a comparison for "Check profiles": by the exact title, the speed left out for Staging
		org.dbgl.util.ExoAdvice.setRoot(root);
		try {
			org.dbgl.util.ExoAdvice.Game g = org.dbgl.util.ExoAdvice.gameFor("1942 - The Pacific Air War");
			assertNotNull(g);
			assertEquals("1942: The Pacific Air War", g.title());
			assertNull(org.dbgl.util.ExoAdvice.gameFor("1942"));
			assertNotNull(org.dbgl.util.ExoAdvice.gameFor("keen1")); // not installed, without LaunchBox data: its folder
			List<org.dbgl.util.DBConfAdvice.Difference> diffs = org.dbgl.util.ExoAdvice.differences(g, v, v.getConfiguration());
			assertTrue(diffs.stream().anyMatch(d -> d.item().equals("xms")));
			assertFalse(diffs.stream().anyMatch(d -> d.item().equals("cycles")));
		} finally {
			org.dbgl.util.ExoAdvice.setRoot(null);
		}

		assertNull(org.dbgl.util.ExoImport.find(game)); // no collection: nothing
	}

	/**
	 * alpha 52: an own LaunchBox collection: a game with DOSBox and its own configuration (settings kept, an [autoexec] that mounts
	 * its folder), one that starts with a configuration of its own (taken as it is), eXoDOS left out, a Windows game counted
	 */
	@Test
	public void testLaunchBoxImport() throws IOException {
		File root = Files.createTempDirectory("dbgl-lb-").toFile();
		File game = new File(root, "Games/MS-DOS/Doom/BIN");
		game.mkdirs();
		Files.writeString(new File(game, "DOOM.EXE").toPath(), "MZ");
		File ownConf = new File(root, "Games/MS-DOS/Doom/doom.conf");
		Files.writeString(ownConf.toPath(), "# This is the configuration file for DOSBox 0.74-3.\r\n[sdl]\r\nfullscreen=true\r\n\r\n[cpu]\r\ncycles=fixed 20000\r\n\r\n"
				+ "[autoexec]\r\n@echo off\r\n");
		File keen = new File(root, "Games/MS-DOS/Keen");
		keen.mkdirs();
		Files.writeString(new File(keen, "KEEN1.EXE").toPath(), "MZ");
		File keenConf = new File(keen, "keen.conf");
		Files.writeString(keenConf.toPath(), "[cpu]\r\ncycles=auto\r\n\r\n[autoexec]\r\nmount c \"" + keen.getPath() + "\"\r\nc:\r\nKEEN1.EXE\r\n");
		File manual = new File(root, "Manuals/MS-DOS/Doom.pdf");
		manual.getParentFile().mkdirs();
		Files.write(manual.toPath(), new byte[] {1});
		File platforms = new File(root, "Data/Platforms");
		platforms.mkdirs();
		Files.writeString(new File(root, "Data/Emulators.xml").toPath(), "<?xml version=\"1.0\" standalone=\"yes\"?>\r\n<LaunchBox>\r\n  <Emulator>\r\n"
				+ "    <ID>11111111-2222-3333-4444-555555555555</ID>\r\n    <Title>DOSBox Staging</Title>\r\n    <ApplicationPath>Emulators\\DOSBox Staging\\dosbox.exe</ApplicationPath>\r\n"
				+ "  </Emulator>\r\n</LaunchBox>\r\n");
		Files.writeString(new File(platforms, "MS-DOS.xml").toPath(), "<?xml version=\"1.0\" standalone=\"yes\"?>\r\n<LaunchBox>\r\n"
				+ "  <Game>\r\n    <ApplicationPath>Games\\MS-DOS\\Doom\\BIN\\DOOM.EXE</ApplicationPath>\r\n    <CommandLine>-warp 1 1</CommandLine>\r\n"
				+ "    <RootFolder>Games\\MS-DOS\\Doom</RootFolder>\r\n    <DosBoxConfigurationPath>Games\\MS-DOS\\Doom\\doom.conf</DosBoxConfigurationPath>\r\n"
				+ "    <UseDosBox>true</UseDosBox>\r\n    <Title>Doom</Title>\r\n    <Developer>id Software</Developer>\r\n    <ReleaseDate>1993-12-10T00:00:00+01:00</ReleaseDate>\r\n"
				+ "    <ManualPath>Manuals\\MS-DOS\\Doom.pdf</ManualPath>\r\n    <Platform>MS-DOS</Platform>\r\n"
				+ "    <Favorite>true</Favorite>\r\n    <PlayCount>12</PlayCount>\r\n    <PlayTime>7200</PlayTime>\r\n    <Completed>true</Completed>\r\n"
				+ "    <StarRating>4</StarRating>\r\n    <LastPlayedDate>2025-03-01T20:00:00.0000000+01:00</LastPlayedDate>\r\n  </Game>\r\n"
				+ "  <Game>\r\n    <ApplicationPath>" + keenConf.getPath() + "</ApplicationPath>\r\n    <Emulator>11111111-2222-3333-4444-555555555555</Emulator>\r\n"
				+ "    <UseDosBox>false</UseDosBox>\r\n    <Title>Commander Keen</Title>\r\n    <Platform>MS-DOS</Platform>\r\n  </Game>\r\n"
				+ "  <Game>\r\n    <ApplicationPath>eXo\\eXoDOS\\!dos\\1942\\1942.bat</ApplicationPath>\r\n    <Title>1942</Title>\r\n    <Platform>MS-DOS</Platform>\r\n  </Game>\r\n"
				+ "  <Game>\r\n    <ApplicationPath>Games\\Windows\\game.exe</ApplicationPath>\r\n    <UseDosBox>false</UseDosBox>\r\n    <Title>Windows game</Title>\r\n"
				+ "    <Platform>MS-DOS</Platform>\r\n  </Game>\r\n</LaunchBox>\r\n");
		File covers = new File(root, "Images/MS-DOS/Box - Front/Europe");
		covers.mkdirs();
		Files.write(new File(covers, "Doom-01.jpg").toPath(), new byte[] {1, 2, 3});
		File work = Files.createTempDirectory("dbgl-lbwork-").toFile();

		org.dbgl.util.LaunchBoxImport.Result r = org.dbgl.util.LaunchBoxImport.find(platforms, work); // a folder below is fine
		assertNotNull(r);
		assertEquals(root.getCanonicalFile(), r.root().getCanonicalFile());
		assertEquals(2, r.games().size());
		assertEquals(1, r.exo());
		assertEquals(1, r.skipped());
		org.dbgl.util.StoreGameImport.Found k = r.games().get(0), d = r.games().get(1);
		assertEquals("Commander Keen", k.title());
		assertEquals(keenConf.getCanonicalFile(), k.confs().get(0)); // mounts by itself: as it is
		assertEquals("Doom", d.title());
		assertEquals("LaunchBox", d.store());
		assertEquals("1993", d.meta().year());
		assertEquals("id Software", d.meta().developer());
		assertEquals("Doom-01.jpg", d.meta().cover().get().getName());
		assertEquals(1, d.meta().extras().size());
		assertEquals(new File(root, "Games/MS-DOS/Doom").getCanonicalFile(), d.gameDir());
		String made = Files.readString(d.confs().get(0).toPath(), java.nio.charset.StandardCharsets.ISO_8859_1);
		assertTrue(made, made.startsWith("# This is the configuration file for DOSBox 0.74-3."));
		assertTrue(made, made.contains("cycles=fixed 20000"));
		assertFalse(made, made.contains("@echo off"));
		assertTrue(made, made.contains("mount c \"" + d.gameDir().getPath() + "\"\nc:\ncd \\BIN\nDOOM.EXE -warp 1 1\n"));

		File dosboxConf = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
		org.dbgl.model.aggregate.DosboxVersion v = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, "staging083", "", "",
			dosboxConf.getAbsolutePath());
		v.resetAndLoadConfiguration();
		org.dbgl.model.aggregate.Profile p = org.dbgl.util.StoreGameImport.toProfile(d, v, GenericTextService.getInstance());
		assertEquals("DOOM.EXE", new File(p.getConfiguration().getAutoexec().getMain()).getName());
		assertEquals("-warp 1 1", p.getConfiguration().getAutoexec().getParameters());
		assertTrue(p.getNotes(), p.getNotes().endsWith("LaunchBox: " + d.gameDir().getPath()));
		// what LaunchBox knew of playing it
		org.dbgl.util.StoreGameImport.Stats s = d.meta().stats();
		assertTrue(s.favorite());
		assertEquals(12, s.runs());
		assertEquals(7200, s.playSeconds());
		assertTrue(s.completed());
		assertEquals(80, s.rating());
		assertEquals(java.time.OffsetDateTime.parse("2025-03-01T20:00:00+01:00").toInstant().toEpochMilli(), s.lastPlayedMillis());
		assertTrue(p.isFavorite());
		assertEquals("80", p.getCustomInt(0));
		assertFalse(p.getStatus().isEmpty());
		assertFalse(k.meta().stats().favorite()); // none in LaunchBox
		assertEquals(-1, k.meta().stats().rating());

		assertNull(org.dbgl.util.LaunchBoxImport.find(game, work)); // no collection: nothing
	}

	/**
	 * alpha 52: profiles into LaunchBox: appended at the end, what was there stays byte for byte (with its BOM), a copy of the file
	 * before, a game there already left alone, the pictures named as LaunchBox does and not over pictures it has; read back by
	 * the import
	 */
	@Test
	public void testLaunchBoxExport() throws IOException, javax.xml.stream.XMLStreamException {
		File root = Files.createTempDirectory("dbgl-lbx-").toFile();
		File platforms = new File(root, "Data/Platforms");
		platforms.mkdirs();
		assertNull(org.dbgl.util.LaunchBoxExport.platformFile(root));
		String before = "<?xml version=\"1.0\" standalone=\"yes\"?>\r\n<LaunchBox>\r\n  <Game>\r\n    <ApplicationPath>C:\\Games\\old.exe</ApplicationPath>\r\n"
				+ "    <Title>Altes Spiel &amp; mehr</Title>\r\n    <Unknown>bleibt</Unknown>\r\n  </Game>\r\n</LaunchBox>";
		File xml = new File(platforms, "MS-DOS.xml");
		byte[] bom = {(byte)0xEF, (byte)0xBB, (byte)0xBF};
		Files.write(xml.toPath(), org.apache.commons.lang3.ArrayUtils.addAll(bom, before.getBytes(StandardCharsets.UTF_8)));
		assertEquals(xml, org.dbgl.util.LaunchBoxExport.platformFile(root));
		assertEquals(root.getCanonicalFile(), org.dbgl.util.LaunchBoxExport.rootOf(platforms).getCanonicalFile());

		File pics = Files.createTempDirectory("dbgl-lbxpic-").toFile();
		File cover = new File(pics, "cover.jpg"), shot1 = new File(pics, "a.png"), shot2 = new File(pics, "b.png");
		for (File f: new File[] {cover, shot1, shot2})
			Files.write(f.toPath(), new byte[] {1});
		File had = new File(root, "Images/MS-DOS/Box - Front/North America/Monkey Island 2_ LeChuck_s Revenge-01.jpg");
		had.getParentFile().mkdirs();
		Files.write(had.toPath(), new byte[] {9});
		java.util.List<org.dbgl.util.LaunchBoxExport.Entry> entries = java.util.List.of(
			new org.dbgl.util.LaunchBoxExport.Entry("Doom: \"Ultimate\" <1>", new File("C:\\DBGL\\launch.exe"), new File("C:\\DBGL"), "--run 7", "id Software", "GT", "1995", "Shooter",
					"Zeile 1\nZeile 2 & \u0001", null, cover, java.util.List.of(shot1, shot2)),
			new org.dbgl.util.LaunchBoxExport.Entry("Monkey Island 2: LeChuck's Revenge", new File("C:\\DBGL\\launch.exe"), new File("C:\\DBGL"), "--run 8", null, null, "", null, null, null, cover,
					java.util.List.of()));
		org.dbgl.util.LaunchBoxExport.Result r = org.dbgl.util.LaunchBoxExport.add(root, entries, true);
		assertEquals(2, r.added());
		assertEquals(0, r.already());
		org.junit.Assert.assertArrayEquals(org.apache.commons.lang3.ArrayUtils.addAll(bom, before.getBytes(StandardCharsets.UTF_8)), Files.readAllBytes(r.backup().toPath()));
		byte[] after = Files.readAllBytes(xml.toPath());
		String text = new String(after, 3, after.length - 3, StandardCharsets.UTF_8);
		assertEquals((byte)0xEF, after[0]);
		assertTrue(text.startsWith(before.substring(0, before.indexOf("</LaunchBox>"))));
		assertTrue(text.endsWith("</LaunchBox>"));
		assertFalse(text.contains("\u0001"));
		assertFalse(text.replace("\r\n", "").contains("\n"));
		java.util.List<java.util.Map<String, String>> games = new java.util.ArrayList<>();
		try (java.io.InputStream in = new java.io.FileInputStream(xml)) {
			org.dbgl.util.LaunchBoxImport.readElements(in, "Game", games::add);
		}
		assertEquals(3, games.size());
		assertEquals("bleibt", games.get(0).get("Unknown"));
		assertEquals("Doom: \"Ultimate\" <1>", games.get(1).get("Title"));
		assertEquals("--run 7", games.get(1).get("CommandLine"));
		assertEquals("Zeile 1\nZeile 2 & ", games.get(1).get("Notes").replace("\r\n", "\n"));
		assertTrue(games.get(1).get("ReleaseDate").startsWith("1995-01-01T00:00:00"));
		assertEquals("MS-DOS", games.get(1).get("Platform"));
		assertEquals("false", games.get(1).get("UseDosBox"));
		assertEquals(36, games.get(1).get("ID").length());
		assertNull(games.get(2).get("ReleaseDate"));
		File front = new File(root, "Images/MS-DOS/Box - Front");
		assertTrue(new File(front, "Doom_ _Ultimate_ _1_-01.jpg").isFile());
		assertTrue(new File(root, "Images/MS-DOS/Screenshot - Gameplay/Doom_ _Ultimate_ _1_-02.png").isFile());
		assertFalse(new File(front, "Monkey Island 2_ LeChuck_s Revenge-01.jpg").exists()); // LaunchBox has one (in a region folder)

		r = org.dbgl.util.LaunchBoxExport.add(root, entries, true); // again: nothing new
		assertEquals(0, r.added());
		assertEquals(2, r.already());
		org.junit.Assert.assertArrayEquals(after, Files.readAllBytes(xml.toPath()));

		Files.writeString(xml.toPath(), "<?xml version=\"1.0\"?>\n<LaunchBox />\n"); // an empty platform
		assertEquals(2, org.dbgl.util.LaunchBoxExport.add(root, entries, false).added());
		games.clear();
		try (java.io.InputStream in = new java.io.FileInputStream(xml)) {
			org.dbgl.util.LaunchBoxImport.readElements(in, "Game", games::add);
		}
		assertEquals(2, games.size());

		assertEquals("Ein Spiel.", org.dbgl.util.LaunchBoxExport.notesFor("Ein Spiel.\n\nGOG: C:\\GOG Games\\Doom"));
		assertEquals("", org.dbgl.util.LaunchBoxExport.notesFor("LaunchBox: /games/doom"));
		assertEquals("Hinweis: siehe Handbuch", org.dbgl.util.LaunchBoxExport.notesFor("Hinweis: siehe Handbuch"));
	}

	/** alpha 52: the games in scummvm.ini (not ScummVM itself), their titles, the command line for ScummVM, the target read back */
	@Test
	public void testScummvm() throws IOException {
		File dir = Files.createTempDirectory("dbgl-svm-").toFile();
		File ini = new File(dir, "scummvm.ini");
		Files.writeString(ini.toPath(), "[scummvm]\r\nlastselectedgame=monkey2\r\nversioninfo=2.9.1\r\n\r\n[monkey2]\r\ndescription=Monkey Island 2: LeChuck\u2019s Revenge (DOS/Deutsch)\r\n"
				+ "path=C:\\Spiele\\Monkey2\r\nengineid=scumm\r\ngameid=monkey2\r\nlanguage=de\r\nplatform=pc\r\n\r\n# a comment\r\n[sky]\r\ngameid=sky\r\npath=sky\r\n"
				+ "description=Beneath a Steel Sky (v0.0372 CD/DOS)\r\n\r\n[keymapper]\r\nkeymap_global_MENU=F5\r\n\r\n[bad target]\r\ngameid=x\r\n\r\n[nogame]\r\npath=x\r\n",
				StandardCharsets.UTF_8);
		java.util.List<org.dbgl.util.Scummvm.Game> games = org.dbgl.util.Scummvm.readIni(ini);
		assertEquals(2, games.size());
		assertEquals("monkey2", games.get(0).target());
		assertEquals("Monkey Island 2: LeChuck\u2019s Revenge", games.get(0).title());
		assertEquals("pc", games.get(0).platform());
		assertEquals("Beneath a Steel Sky", games.get(1).title());
		assertEquals(new File(dir, "sky"), games.get(1).path()); // relative to the file
		assertFalse(org.dbgl.util.Scummvm.returnsToLauncher(ini));
		File ini2 = new File(dir, "scummvm2.ini");
		Files.writeString(ini2.toPath(), "[monkey2]\ngui_return_to_launcher_at_exit=true\n[scummvm]\ngui_return_to_launcher_at_exit=true\n");
		assertTrue(org.dbgl.util.Scummvm.returnsToLauncher(ini2));

		String conf = org.dbgl.util.Scummvm.confOf(games.get(0), ini);
		assertEquals("monkey2", org.dbgl.util.Scummvm.valueInConf("[sdl]\r\nfullscreen=true\r\n" + conf, "target"));
		assertNull(org.dbgl.util.Scummvm.valueInConf("[dosbox]\ntarget=x\n", "target"));

		File exe = new File(dir, "scummvm");
		Files.writeString(exe.toPath(), "");
		org.dbgl.model.aggregate.DosboxVersion v = DosboxVersionFactory.create("ScummVM", org.dbgl.util.Scummvm.FAMILY, "", false, false, false, null, dir.getPath(), exe.getPath(), "-f", ini.getPath());
		assertTrue(org.dbgl.util.Scummvm.is(v));
		assertTrue(org.dbgl.util.Scummvm.isExecutable(new File("ScummVM", "scummvm.exe")));
		assertFalse(org.dbgl.util.Scummvm.isExecutable(new File("dosbox.exe")));
		org.dbgl.model.aggregate.Profile p = org.dbgl.model.factory.ProfileFactory.create(v, null);
		p.loadConfigurationData(GenericTextService.getInstance(), conf, ini, dir);
		assertEquals("monkey2", org.dbgl.util.Scummvm.targetOf(p));
		assertEquals(java.util.List.of("--config=" + ini.getAbsolutePath(), "-f", "monkey2"), org.dbgl.util.Scummvm.arguments(p, v));
		assertNull(org.dbgl.util.DosboxVersionDetector.detect(exe, dir)); // not started to ask it
		// two profiles of the same game of ScummVM are duplicates, of another game not
		org.dbgl.model.aggregate.Profile p2 = org.dbgl.model.factory.ProfileFactory.create(v, null);
		p2.loadConfigurationData(GenericTextService.getInstance(), conf, ini, dir);
		org.dbgl.model.aggregate.Profile p3 = org.dbgl.model.factory.ProfileFactory.create(v, null);
		p3.loadConfigurationData(GenericTextService.getInstance(), org.dbgl.util.Scummvm.confOf(games.get(1), ini), ini, dir);
		assertEquals(1, org.dbgl.util.DuplicateProfiles.findLoaded(java.util.List.of(p, p2, p3)).size());

		// the update check: the version installed from scummvm.ini next to the program, the newest from the tags
		assertEquals("2.9.1", org.dbgl.util.Scummvm.installedVersion(v));
		org.json.JSONArray tags = new org.json.JSONArray("[{\"name\":\"desc/2.9.1\"},{\"name\":\"v2.9.1\"},{\"name\":\"v2026.3.0\"},{\"name\":\"v2026.4.0git\"},{\"name\":\"v2.10.0\"}]");
		org.dbgl.util.DosboxUpdateChecker.Release newest = org.dbgl.util.DosboxUpdateChecker.newestScummvmTag(tags);
		assertEquals("2026.3.0", newest.version());
		assertEquals(org.dbgl.util.Scummvm.FAMILY, newest.family());
		assertTrue(newest.assets().isEmpty()); // only its page, nothing set up
		assertTrue(org.dbgl.util.DosboxUpdateChecker.compareVersions("2026.3.0", "2.9.1") > 0);

		// the screenshots of the game: from the folder of scummvm.ini (screenshotpath), copied, only new ones of ScummVM or the game
		File portable = Files.createTempDirectory("dbgl-svmp-").toFile(), shots = new File(portable, "shots"), captures = new File(portable, "captures");
		shots.mkdirs();
		File portableExe = new File(portable, "scummvm.exe");
		Files.writeString(portableExe.toPath(), "");
		Files.writeString(new File(portable, "scummvm.ini").toPath(), "[scummvm]\nscreenshotpath=" + shots.getPath() + "\n");
		assertEquals(shots, org.dbgl.util.Scummvm.screenshotDir(portableExe));
		long start = System.currentTimeMillis();
		File old = new File(shots, "scummvm-lol-cd-00000.png"), fresh = new File(shots, "scummvm-lol-cd-00001.png"), other = new File(shots, "holiday.png"), text = new File(shots, "scummvm-log.txt");
		File otherGame = new File(shots, "scummvm-sky-00000.png"); // another game, started from ScummVM's list meanwhile
		for (File f: new File[] {old, fresh, other, text, otherGame})
			Files.write(f.toPath(), new byte[] {1});
		old.setLastModified(start - 60_000);
		assertEquals(1, org.dbgl.util.Scummvm.copyScreenshots(shots, "lol-cd", start, captures));
		assertTrue(new File(captures, fresh.getName()).isFile());
		assertTrue(fresh.isFile()); // copied, not moved
		assertEquals(0, org.dbgl.util.Scummvm.copyScreenshots(shots, "lol-cd", start, captures)); // there already
	}

	/** alpha 52: RAWG: the answers as its API gives them (search, game, screenshots), the platform filter, the source among the others */
	@Test
	public void testRawg() {
		org.json.JSONObject search = new org.json.JSONObject("{\"count\":2,\"results\":[{\"id\":53551,\"slug\":\"the-secret-of-monkey-island\",\"name\":\"The Secret of Monkey Island\","
				+ "\"released\":\"1990-10-01\",\"platforms\":[{\"platform\":{\"id\":4,\"name\":\"PC\"}},{\"platform\":{\"id\":166,\"name\":\"Commodore / Amiga\"}}]},"
				+ "{\"id\":2,\"slug\":\"x\",\"name\":\"Monkey Island 2\",\"released\":null,\"platforms\":null}]}");
		java.util.List<org.dbgl.model.WebProfile> found = org.dbgl.util.searchengine.RawgSearchEngine.parseEntries(search);
		assertEquals(2, found.size());
		assertEquals("The Secret of Monkey Island", found.get(0).getTitle());
		assertEquals("1990", found.get(0).getYear());
		assertEquals("PC, Commodore / Amiga", found.get(0).getPlatform());
		assertEquals("https://rawg.io/games/the-secret-of-monkey-island", found.get(0).getUrl());
		assertEquals("", found.get(1).getYear());

		org.dbgl.model.WebProfile game = found.get(0);
		org.dbgl.util.searchengine.RawgSearchEngine.parseDetails(game, new org.json.JSONObject("{\"id\":53551,\"slug\":\"the-secret-of-monkey-island\",\"name\":\"The Secret of Monkey Island\","
				+ "\"released\":\"1990-10-01\",\"description_raw\":\"Guybrush wants to be a pirate.\",\"rating\":4.3,\"background_image\":\"https://media.rawg.io/media/games/a/b.jpg\","
				+ "\"genres\":[{\"name\":\"Adventure\"}],\"developers\":[{\"name\":\"Lucasfilm Games\"}],\"publishers\":[{\"name\":\"Lucasfilm Games\"},{\"name\":\"U.S. Gold\"}]}"));
		assertEquals("Guybrush wants to be a pirate.", game.getNotes());
		assertEquals("Adventure", game.getGenre());
		assertEquals("Lucasfilm Games", game.getDeveloperName());
		assertEquals("Lucasfilm Games, U.S. Gold", game.getPublisherName());
		assertEquals(86, game.getRank());
		assertEquals("https://media.rawg.io/media/games/a/b.jpg", game.getCoreGameCoverUrl());

		java.util.List<org.dbgl.model.SearchEngineImageInformation> shots = org.dbgl.util.searchengine.RawgSearchEngine.parseScreenshots(new org.json.JSONObject(
			"{\"results\":[{\"id\":1,\"image\":\"https://media.rawg.io/1.jpg\"},{\"id\":2,\"image\":\"\"},{\"id\":3,\"image\":\"https://media.rawg.io/3.jpg\"},{\"id\":4,\"image\":\"https://media.rawg.io/4.jpg\"}]}"), 2);
		assertEquals(2, shots.size());
		assertEquals("https://media.rawg.io/3.jpg", shots.get(1).getUrl());

		assertEquals(java.util.Set.of(4), org.dbgl.util.searchengine.RawgSearchEngine.platformIds(new String[] {"PC DOS", "Windows"}));
		assertEquals(java.util.Set.of(166), org.dbgl.util.searchengine.RawgSearchEngine.platformIds(new String[] {"166"}));
		assertNotNull(org.dbgl.util.searchengine.WebSearchEngine.getBySimpleName("rawg"));
		assertTrue(SettingsService.availableWebSearchEngines().indexOf(org.dbgl.util.searchengine.WebSearchEngine.getBySimpleName("rawg"))
				< SettingsService.availableWebSearchEngines().indexOf(org.dbgl.util.searchengine.WebSearchEngine.getBySimpleName("mobygames"))); // MobyGames stays last
	}

	/** alpha 52: the backups of saved games move from next to the screenshots to savegames\<number>: nothing may get lost */
	@Test
	public void testSavegamesMove() throws Exception {
		File dir = Files.createTempDirectory("dbgl-sgmove").toFile();
		org.dbgl.model.aggregate.Profile p = null;
		File kept = null;
		try {
			File game = new File(dir, "game");
			game.mkdirs();
			File conf = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
			org.dbgl.model.aggregate.DosboxVersion dbv = DosboxVersionFactory.create(9400, "Staging", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, dir.getPath(), "", "",
				conf.getPath(), null, null, null, 0);
			p = org.dbgl.model.factory.ProfileFactory.create(dbv, null);
			p.setId(9400);
			p.setConfigurationFileLocation(new File(dir, "9400.conf").getPath());
			p.getConfiguration().getAutoexec().addMount("mount C \"" + game.getPath() + "\"");
			p.setCapturesById();
			org.apache.commons.io.FileUtils.deleteQuietly(p.getCanonicalCaptures());
			org.apache.commons.io.FileUtils.deleteQuietly(org.dbgl.util.SavegameBackup.dir(p));
			// as an earlier alpha left them: two backups and the settings next to the screenshots
			File old = org.dbgl.util.SavegameBackup.oldDir(p);
			old.mkdirs();
			Files.writeString(new File(old, "savegames.properties").toPath(), "enabled=true\npatterns=*.SAV\nkeep=1\n");
			for (String n: new String[] {"20261101-100000.zip", "20261102-100000.zip"})
				try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(Files.newOutputStream(new File(old, n).toPath()))) {
					z.putNextEntry(new java.util.zip.ZipEntry("SAVE1.SAV"));
					z.write(n.getBytes());
					z.closeEntry();
				}
			assertTrue(org.dbgl.util.SavegameBackup.needsMove(p));
			assertEquals(2, org.dbgl.util.SavegameBackup.backups(p).size()); // shown from the earlier place before the move
			assertTrue(org.dbgl.util.SavegameBackup.load(p).enabled()); // and its settings count
			// packed once before the first move, locked
			File root = org.dbgl.util.SavegameBackup.dir(p).getParentFile();
			new File(root, "before-move.txt").delete();
			File packs = new File(dir, "backups");
			File pack = org.dbgl.util.SavegameBackup.packEarlierPlaces(java.util.List.of(p), packs);
			assertNotNull(pack);
			assertEquals(3, org.dbgl.util.archive.ZipUtils.readEntriesInZip(pack).size());
			assertTrue(org.dbgl.util.BackupCatalog.isLocked(pack));
			assertNull(org.dbgl.util.SavegameBackup.packEarlierPlaces(java.util.List.of(p), packs)); // only once
			new File(root, "before-move.txt").delete();
			// copied and checked, the earlier ones untouched
			org.dbgl.util.SavegameBackup.Moved moved = org.dbgl.util.SavegameBackup.moveToNewPlace(p);
			assertEquals(3, moved.copied());
			assertEquals(0, moved.failed());
			assertFalse(org.dbgl.util.SavegameBackup.needsMove(p));
			assertEquals(3, old.list().length);
			assertEquals(2, org.dbgl.util.SavegameBackup.backups(p).size()); // each once
			File newPlace = org.dbgl.util.SavegameBackup.dir(p);
			assertTrue(org.dbgl.util.SavegameBackup.backups(p).stream().allMatch(f -> f.getParentFile().equals(newPlace)));
			assertEquals(0, org.dbgl.util.SavegameBackup.moveToNewPlace(p).copied()); // again: nothing to do
			// a new backup with keep 1 removes only at the new place; what was moved does not come back from the earlier place
			Files.writeString(new File(game, "SAVE1.SAV").toPath(), "now");
			assertNotNull(org.dbgl.util.SavegameBackup.backup(p, null));
			assertEquals(3, old.list().length);
			assertEquals(1, org.dbgl.util.SavegameBackup.backups(p).size());
			// back on an earlier alpha: it makes one at the earlier place; shown, and moved the next time
			try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(Files.newOutputStream(new File(old, "20261120-100000.zip").toPath()))) {
				z.putNextEntry(new java.util.zip.ZipEntry("SAVE1.SAV"));
				z.write("old alpha".getBytes());
				z.closeEntry();
			}
			assertTrue(org.dbgl.util.SavegameBackup.needsMove(p));
			assertEquals(2, org.dbgl.util.SavegameBackup.backups(p).size());
			assertEquals(1, org.dbgl.util.SavegameBackup.moveToNewPlace(p).copied());
			// deleted by hand: at both places
			File oldOne = new File(org.dbgl.util.SavegameBackup.dir(p), "20261120-100000.zip");
			org.dbgl.util.SavegameBackup.delete(p, oldOne);
			assertFalse(oldOne.exists());
			assertFalse(new File(old, "20261120-100000.zip").exists());
			// the list of the page: everything read at once, the same backups
			assertEquals(org.dbgl.util.SavegameBackup.backups(p).size(), org.dbgl.util.SavegameBackup.list(p).size());
			// before deleting the profile (with its screenshots): its backups counted, and packed at both places in a locked ZIP file
			org.dbgl.util.SavegameBackup.WithBackups with = org.dbgl.util.SavegameBackup.withBackups(java.util.List.of(p));
			assertEquals(1, with.profiles().size());
			assertEquals(org.dbgl.util.SavegameBackup.backups(p).size(), with.count());
			assertTrue(with.size() > 0);
			File deleted = org.dbgl.util.SavegameBackup.packBeforeDelete(with.profiles(), packs);
			assertNotNull(deleted);
			assertTrue(deleted.getName().startsWith("dbgl-savegames-deleted-profiles-"));
			assertTrue(org.dbgl.util.BackupCatalog.isLocked(deleted));
			java.util.List<String> names = org.dbgl.util.archive.ZipUtils.readEntriesInZip(deleted).stream().map(Object::toString).toList();
			assertTrue(names.toString(), names.stream().allMatch(n -> n.startsWith("9400 ")));
			assertTrue(names.toString(), names.stream().anyMatch(n -> n.contains("/earlier/")));
			assertEquals(newPlace.list().length + old.list().length, names.size());
			assertNull(org.dbgl.util.SavegameBackup.packBeforeDelete(java.util.List.of(), packs)); // none: no file
			// the profile deleted, its backups kept: copied into savegames\deleted (not a number), its own folder gone
			java.util.Set<String> expected = new java.util.TreeSet<>(java.util.Arrays.asList(newPlace.list()));
			expected.addAll(java.util.Arrays.asList(old.list()));
			int oldCount = old.list().length;
			kept = org.dbgl.util.SavegameBackup.keepOfDeleted(p);
			assertNotNull(kept);
			assertEquals(org.dbgl.util.SavegameBackup.DELETED, kept.getParentFile().getName());
			assertTrue(kept.getName().startsWith("9400 "));
			assertEquals(expected, new java.util.TreeSet<>(java.util.Arrays.asList(kept.list())));
			assertFalse(newPlace.exists());
			assertEquals(oldCount, old.list().length); // the earlier place only goes with the screenshots
			// in the window of the backups: the ZIP file of the deleted profiles and the kept folder, which can be deleted there
			assertEquals(org.dbgl.util.BackupCatalog.Kind.SAVEGAMES, org.dbgl.util.BackupCatalog.kindOf(deleted.getName()));
			File keptNow = kept;
			java.util.List<org.dbgl.util.BackupCatalog.Backup> listed = org.dbgl.util.BackupCatalog.list(java.util.List.of(kept.getParentFile()));
			assertTrue(listed.toString(), listed.stream().anyMatch(b -> b.file().equals(keptNow) && (b.kind() == org.dbgl.util.BackupCatalog.Kind.DELETED_SAVEGAMES)));
			File other = new File(kept.getParentFile(), "not one");
			other.mkdirs();
			assertFalse(org.dbgl.util.BackupCatalog.delete(other)); // a folder of another name stays
			org.apache.commons.io.FileUtils.deleteQuietly(other);
			assertTrue(org.dbgl.util.BackupCatalog.delete(keptNow));
			assertFalse(keptNow.exists());
		} finally {
			if (p != null) {
				org.apache.commons.io.FileUtils.deleteQuietly(p.getCanonicalCaptures());
				org.apache.commons.io.FileUtils.deleteQuietly(org.dbgl.util.SavegameBackup.dir(p));
			}
			if (kept != null)
				org.apache.commons.io.FileUtils.deleteQuietly(kept);
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
	}

	/** alpha 52: back from a USB stick: backups of saved games at the earlier place (a stick of an earlier version) and at the new one */
	@Test
	public void testStickReturnSavegames() throws Exception {
		File base = Files.createTempDirectory("dbgl-sticksg").toFile();
		try {
			File data = new File(base, "main"), stick = new File(base, "stick/DBGL");
			File captures = new File(data, "captures/7"), savegames = new File(data, "savegames/7");
			captures.mkdirs();
			savegames.mkdirs();
			Files.writeString(new File(savegames, "20261101-100000.zip").toPath(), "here");
			long created = System.currentTimeMillis() - 100000;
			java.util.Map<Integer, java.util.Map.Entry<org.dbgl.util.StickReturn.Stats, String>> profiles = new java.util.LinkedHashMap<>();
			profiles.put(7, java.util.Map.entry(new org.dbgl.util.StickReturn.Stats(0, 0, 0), "Doom"));
			java.util.Map<String, File> paths = new java.util.LinkedHashMap<>();
			paths.put("captures/7", captures);
			paths.put("savegames/7", savegames);
			new File(stick, "captures/7/savegames").mkdirs();
			new File(stick, "savegames/7").mkdirs();
			Files.write(new File(stick, org.dbgl.util.StickReturn.MANIFEST).toPath(), org.dbgl.util.StickReturn.manifest("stick-sg", created, profiles, paths, data, new File(data, "dosroot")));
			Files.writeString(new File(stick, "savegames/7/20261101-100000.zip").toPath(), "here"); // the same: nothing to do
			Files.writeString(new File(stick, "savegames/7/20261121-100000.zip").toPath(), "made on the stick");
			Files.writeString(new File(stick, "captures/7/savegames/20261120-100000.zip").toPath(), "an earlier version on the stick");
			org.dbgl.util.StickReturn.Plan plan = org.dbgl.util.StickReturn.plan(stick, java.util.Map.of(7, "Doom"), data, new File(data, "dosroot"));
			java.util.Set<String> to = new java.util.TreeSet<>();
			plan.files().forEach(f -> {
				assertFalse(f.replaces()); // nothing is replaced, nothing deleted
				to.add(data.toPath().relativize(f.to().toPath()).toString().replace(File.separatorChar, '/'));
			});
			assertEquals(java.util.Set.of("captures/7/savegames/20261120-100000.zip", "savegames/7/20261121-100000.zip"), to);
			assertTrue(plan.conflicts().isEmpty());
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(base);
		}
	}

	/** alpha 52: backups of saved games: no big data file suggested, names, kept ones stay, the current state, large ones before a start */
	@Test
	public void testSavegameNamesKeptLarge() throws Exception {
		File dir = Files.createTempDirectory("dbgl-sav52").toFile();
		try {
			File game = new File(dir, "game");
			game.mkdirs();
			Files.write(new File(game, "EDEN.DAT").toPath(), new byte[5 * 1024 * 1024]);
			Files.writeString(new File(game, "SAVE1.SAV").toPath(), "a");
			assertEquals("SAVE1.SAV", org.dbgl.util.SavegameBackup.suggest(game)); // not the 5 MB of the game
			File conf = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
			org.dbgl.model.aggregate.DosboxVersion dbv = DosboxVersionFactory.create(9300, "Staging", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, dir.getPath(), "", "",
				conf.getPath(), null, null, null, 0);
			org.dbgl.model.aggregate.Profile p = org.dbgl.model.factory.ProfileFactory.create(dbv, null);
			p.setId(9300);
			p.setConfigurationFileLocation(new File(dir, "9300.conf").getPath());
			p.getConfiguration().getAutoexec().addMount("mount C \"" + game.getPath() + "\"");
			p.setCapturesById();
			org.apache.commons.io.FileUtils.deleteQuietly(p.getCanonicalCaptures()); // left by an earlier run (the captures are in the data folder)
			org.apache.commons.io.FileUtils.deleteQuietly(org.dbgl.util.SavegameBackup.dir(p));
			org.dbgl.util.SavegameBackup.save(p, new org.dbgl.util.SavegameBackup.Config(true, "*.SAV", 2));
			java.util.List<File> made = new java.util.ArrayList<>();
			for (int i = 0; i < 4; i++) {
				Files.writeString(new File(game, "SAVE1.SAV").toPath(), "state " + i);
				new File(game, "SAVE1.SAV").setLastModified(1_700_000_000_000L + i * 1000L);
				File zip = org.dbgl.util.SavegameBackup.backup(p, null);
				assertNotNull(zip);
				made.add(zip);
				if (i == 0) {
					org.dbgl.util.SavegameBackup.setName(p, zip, "before the end");
					org.dbgl.util.SavegameBackup.setKept(p, zip, true);
				}
				Thread.sleep(1100); // a new name (seconds)
			}
			assertTrue(made.get(0).isFile()); // kept, not counted
			assertFalse(made.get(1).isFile()); // the oldest of the others
			assertEquals("before the end", org.dbgl.util.SavegameBackup.nameOf(p, made.get(0)));
			assertEquals(made.get(3), org.dbgl.util.SavegameBackup.current(p));
			org.dbgl.util.SavegameBackup.save(p, new org.dbgl.util.SavegameBackup.Config(true, "*.SAV", 3)); // the names stay
			assertEquals("before the end", org.dbgl.util.SavegameBackup.nameOf(p, made.get(0)));
			// a large one is not made before a start unless allowed
			org.dbgl.util.SavegameBackup.save(p, new org.dbgl.util.SavegameBackup.Config(true, "*.SAV; big.bin", 3));
			try (java.io.RandomAccessFile big = new java.io.RandomAccessFile(new File(game, "big.bin"), "rw")) {
				big.setLength(org.dbgl.util.SavegameBackup.LARGE + 1);
			}
			int before = org.dbgl.util.SavegameBackup.backups(p).size();
			org.dbgl.util.SavegameBackup.beforeStart(p);
			assertEquals(before, org.dbgl.util.SavegameBackup.backups(p).size());
			// a file of an earlier alpha (enabled, no limit then): large ones stay allowed, nothing stops without notice
			File props = new File(org.dbgl.util.SavegameBackup.dir(p), "savegames.properties");
			Files.writeString(props.toPath(), "enabled=true\npatterns=*.SAV; big.bin\nkeep=3\n");
			assertTrue(org.dbgl.util.SavegameBackup.largeAllowed(p));
			org.dbgl.util.SavegameBackup.save(p, new org.dbgl.util.SavegameBackup.Config(true, "*.SAV; big.bin", 3));
			assertTrue(org.dbgl.util.SavegameBackup.largeAllowed(p)); // kept when written by alpha 52
			Files.writeString(props.toPath(), "enabled=false\npatterns=*.SAV\nkeep=3\n");
			assertFalse(org.dbgl.util.SavegameBackup.largeAllowed(p)); // was off: asked when switched on
			org.apache.commons.io.FileUtils.deleteQuietly(p.getCanonicalCaptures());
			org.apache.commons.io.FileUtils.deleteQuietly(org.dbgl.util.SavegameBackup.dir(p));
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
	}

	/** alpha 52: points 8 to 13 of the list: second backup place, Steam pictures, TV played last, next part, duplicates by content, mixer */
	@Test
	public void testListPointsAlpha52() throws Exception {
		File dir = Files.createTempDirectory("dbgl-list52").toFile();
		try {
			// 8: OneDrive by its variable, Dropbox by its info.json and in the home folder, each once
			File one = new File(dir, "OneDrive"), box = new File(dir, "Dropbox"), home = new File(dir, "home");
			one.mkdirs();
			box.mkdirs();
			new File(home, "Dropbox (Firma)").mkdirs();
			File info = new File(new File(new File(dir, "local"), "Dropbox"), "info.json");
			info.getParentFile().mkdirs();
			Files.writeString(info.toPath(), new org.json.JSONObject().put("personal", new org.json.JSONObject().put("path", box.getPath())).toString());
			java.util.Map<String, File> clouds = org.dbgl.util.SecondBackupPlace.cloudFolders(java.util.Map.of("OneDrive", one.getPath(), "LOCALAPPDATA", new File(dir, "local").getPath()), home);
			assertEquals(java.util.List.of("OneDrive", "Dropbox", "Dropbox (Firma)"), new java.util.ArrayList<>(clouds.keySet()));
			assertEquals("Space Quest III The Pirates", org.dbgl.util.SecondBackupPlace.safe("Space Quest III: The Pirates?"));
			assertEquals("profile", org.dbgl.util.SecondBackupPlace.safe("***"));
			File target = new File(dir, "target");
			for (int i = 1; i <= 4; i++) {
				File zip = new File(dir, "2026110" + i + "-120000.zip");
				Files.writeString(zip.toPath(), "x" + i);
				org.dbgl.util.SecondBackupPlace.copyAndPrune(zip, target, 2, f -> f.getName().endsWith(".zip"));
			}
			assertEquals(java.util.List.of("20261103-120000.zip", "20261104-120000.zip"), java.util.Arrays.stream(target.list()).sorted().toList());
			assertTrue(org.dbgl.service.BackupService.isSavegamePart("12/savegames/20261101-120000.zip"));
			assertFalse(org.dbgl.service.BackupService.isSavegamePart("12/shot0001.png"));
			// 9: how Steam names the pictures of its library
			assertEquals("", org.dbgl.util.SteamShortcuts.artSuffix("banner"));
			assertEquals("_hero", org.dbgl.util.SteamShortcuts.artSuffix("hero"));
			assertEquals("_logo", org.dbgl.util.SteamShortcuts.artSuffix("logo"));
			assertEquals("p", org.dbgl.util.SteamShortcuts.artSuffix("cover"));
			assertNull(org.dbgl.util.SteamShortcuts.artSuffix("icon"));
			assertEquals(org.dbgl.util.searchengine.SteamGridDbSearchEngine.normalized("Commander Keen: Goodbye, Galaxy!"), org.dbgl.util.searchengine.SteamGridDbSearchEngine.normalized("commander keen goodbye galaxy"));
			// 11: the series from the groups of MobyGames, the next part of a series
			assertEquals("Space Quest", org.dbgl.util.searchengine.MobyGamesSearchEngine.seriesOf(java.util.List.of("Gameplay feature: Death", "Series: Space Quest")));
			assertEquals("Commander Keen", org.dbgl.util.searchengine.MobyGamesSearchEngine.seriesOf(java.util.List.of("Commander Keen series")));
			assertEquals("", org.dbgl.util.searchengine.MobyGamesSearchEngine.seriesOf(java.util.List.of("Protagonist: Roger Wilco")));
			File conf = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
			org.dbgl.model.aggregate.DosboxVersion dbv = DosboxVersionFactory.create(9100, "Staging", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, dir.getPath(), "", "",
				conf.getPath(), null, null, null, 0);
			List<org.dbgl.model.aggregate.Profile> games = new java.util.ArrayList<>();
			String[][] keen = {{"Commander Keen 1", "1"}, {"Commander Keen 4", "4"}, {"Commander Keen 5", "5"}, {"Space Quest", "1"}};
			for (int i = 0; i < keen.length; i++) {
				org.dbgl.model.aggregate.Profile p = org.dbgl.model.factory.ProfileFactory.create(dbv, null);
				p.setId(9100 + i);
				p.setTitle(keen[i][0]);
				p.setSeries(i == 3 ? "Space Quest": "Commander Keen", Integer.parseInt(keen[i][1]));
				p.setStats(new org.dbgl.model.GenericStats(new java.util.Date(), new java.util.Date(), i == 0 ? new java.util.Date(1000L): i == 2 ? new java.util.Date(5000L): null, i == 0 || i == 2 ? 1: 0));
				games.add(p);
			}
			assertEquals("Commander Keen 4", org.dbgl.util.SeriesSuggester.nextPart(games, games.get(0)).getTitle());
			assertTrue(org.dbgl.util.SeriesSuggester.neverPlayed(games.get(1)));
			assertNull(org.dbgl.util.SeriesSuggester.nextPart(games, games.get(2))); // the last part
			assertNull(org.dbgl.util.SeriesSuggester.nextPart(games, games.get(3))); // alone in its series
			// 10: played last: only those played, the newest first
			assertEquals(java.util.List.of("Commander Keen 5", "Commander Keen 1"), org.dbgl.gui.tv.TvMode.recent(games).stream().map(org.dbgl.model.aggregate.Profile::getTitle).toList());
			// 12: the same program in two folders by its content; small programs and batch files are not compared
			File a = new File(dir, "a/KEEN4E.EXE"), c = new File(dir, "c/KEEN4E.EXE"), bat = new File(dir, "a/START.BAT"), small = new File(dir, "a/SMALL.EXE");
			a.getParentFile().mkdirs();
			c.getParentFile().mkdirs();
			byte[] data = new byte[20000];
			new java.util.Random(4).nextBytes(data);
			Files.write(a.toPath(), data);
			Files.write(c.toPath(), data);
			Files.write(bat.toPath(), new byte[20000]);
			Files.write(small.toPath(), new byte[100]);
			assertNotNull(org.dbgl.util.DuplicateProfiles.contentSum(a));
			assertEquals(org.dbgl.util.DuplicateProfiles.contentSum(a), org.dbgl.util.DuplicateProfiles.contentSum(c));
			assertNull(org.dbgl.util.DuplicateProfiles.contentSum(bat));
			assertNull(org.dbgl.util.DuplicateProfiles.contentSum(small));
			assertTrue(org.dbgl.util.DuplicateProfiles.startsWindows("WIN.COM")); // many games start Windows first: no duplicates by that
			assertTrue(org.dbgl.util.DuplicateProfiles.startsWindows("win"));
			assertFalse(org.dbgl.util.DuplicateProfiles.startsWindows("WINGS.EXE"));
			// 13: the MIDI channels of DOSBox Staging in the mixer, and what the mixer does not show is kept
			org.dbgl.model.MixerCommand staging = new org.dbgl.model.MixerCommand("master 70:70 mt32 150:150 innovation 50:50",
				new String[] {"master", "pcspeaker", "sb", "gus", "opl", "disney", "cdaudio", "mt32", "fsynth", "soundcanvas"}, new boolean[10]);
			assertEquals(10, staging.getChannelCount());
			assertEquals("master 70:70 mt32 150:150 innovation 50:50", staging.toString());
			org.dbgl.model.MixerCommand classic = new org.dbgl.model.MixerCommand("master 80:80 mt32 150:150", new String[] {"master", "spkr", "sb", "gus", "fm", "disney", "cdaudio"},
				new boolean[7]);
			assertEquals(7, classic.getChannelCount());
			assertEquals("master 80:80 mt32 150:150", classic.toString()); // before, mt32 was lost
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
	}

	/** alpha 52: the shader preview: the kinds of the shader field, the test picture and the pictures drawn from it */
	@Test
	public void testShaderPreview() {
		assertEquals("crtauto", org.dbgl.util.ShaderPreview.kindOf("crt-auto"));
		assertEquals("crt", org.dbgl.util.ShaderPreview.kindOf("crt/crt-hyllian:monochrome-hires"));
		assertEquals("sharp", org.dbgl.util.ShaderPreview.kindOf("sharp"));
		assertEquals("nearest", org.dbgl.util.ShaderPreview.kindOf("none"));
		assertNull(org.dbgl.util.ShaderPreview.kindOf("my-own-shader.glsl"));
		assertNull(org.dbgl.util.ShaderPreview.kindOf(""));
		java.awt.image.BufferedImage test = org.dbgl.util.ShaderPreview.testImage();
		assertEquals(320, test.getWidth());
		assertEquals(200, test.getHeight());
		for (String kind: new String[] {"nearest", "bilinear", "interpolation", "sharp", "scanlines", "rgb", "crt", "crtauto", "crtautoarcade", "crtautoarcadesharp", "crtcurved", "advmame",
				"xbr"}) {
			assertTrue(kind, org.dbgl.util.ShaderPreview.supports(kind));
			java.awt.image.BufferedImage img = org.dbgl.util.ShaderPreview.render(kind, 160, 120);
			assertEquals(kind, 160, img.getWidth());
			assertEquals(kind, 120, img.getHeight());
		}
		// without shader the picture stays in its 16 colours; the curved screen has black corners
		java.awt.image.BufferedImage hard = org.dbgl.util.ShaderPreview.render("nearest", 640, 400);
		assertEquals(test.getRGB(10, 10) & 0xFFFFFF, hard.getRGB(20, 20) & 0xFFFFFF);
		assertEquals(0, org.dbgl.util.ShaderPreview.render("crtcurved", 160, 120).getRGB(0, 0) & 0xFFFFFF);
	}

	/** alpha 52: a link to a file that is not there (moved manual, Vogons) says so; web addresses and files that are there do not */
	@Test
	public void testMissingFile() throws java.io.IOException {
		java.io.File there = java.io.File.createTempFile("dbgl-link", ".txt");
		try {
			assertFalse(org.dbgl.util.SystemUtils.missingFile(there.getPath()));
			assertFalse(org.dbgl.util.SystemUtils.missingFile(there.toURI().toString()));
			java.io.File gone = new java.io.File(there.getParentFile(), "dbgl-not-there-" + System.nanoTime() + ".pdf");
			assertTrue(org.dbgl.util.SystemUtils.missingFile(gone.getPath()));
			assertTrue(org.dbgl.util.SystemUtils.missingFile(gone.toURI().toString()));
			assertTrue(org.dbgl.util.SystemUtils.missingFile("docs-not-there-" + System.nanoTime() + "/manual.pdf"));
			assertFalse(org.dbgl.util.SystemUtils.missingFile("https://www.mobygames.com/game/190/alley-cat/"));
			assertFalse(org.dbgl.util.SystemUtils.missingFile("mailto:someone@example.org"));
			assertFalse(org.dbgl.util.SystemUtils.missingFile(""));
		} finally {
			there.delete();
		}
	}

	/** alpha 53: the welcome wizard by itself only at the very first start: never shown and no profiles yet */
	@Test
	public void testWelcomeDue() {
		assertTrue(org.dbgl.gui.dialog.WelcomeWizard.due(false, 0));
		assertFalse(org.dbgl.gui.dialog.WelcomeWizard.due(true, 0)); // shown (or skipped) once
		assertFalse(org.dbgl.gui.dialog.WelcomeWizard.due(false, 12)); // updated from an earlier DBGL: has profiles
	}

	/** alpha 53: games learned from profiles: kept in a file of their own, read back, recognized by the start program */
	@Test
	public void testOwnKnownGames() throws Exception {
		File dir = Files.createTempDirectory("dbgl-ownknown-").toFile();
		File f = org.dbgl.util.OwnKnownGames.file(dir);
		assertTrue(org.dbgl.util.OwnKnownGames.load(f).isEmpty()); // no file yet
		String md5 = "0123456789abcdef0123456789abcdef", other = "fedcba9876543210fedcba9876543210";
		java.util.List<org.dbgl.util.OwnKnownGames.Entry> entries = java.util.List.of(
			new org.dbgl.util.OwnKnownGames.Entry("Ishar: Legend of the Fortress", "Silmarils", "Silmarils", "1992", "RPG",
				java.util.List.of(new org.dbgl.util.OwnKnownGames.KnownFile("ISHAR.EXE", md5, false), new org.dbgl.util.OwnKnownGames.KnownFile("INSTALL.EXE", other, true))));
		org.dbgl.util.OwnKnownGames.save(f, entries);
		assertTrue(f.getPath().replace('\\', '/').endsWith("config/known-games.json"));
		java.util.List<org.dbgl.util.OwnKnownGames.Entry> read = org.dbgl.util.OwnKnownGames.load(f);
		assertEquals(entries, read);
		assertTrue(org.dbgl.util.OwnKnownGames.filenames(read).containsAll(java.util.List.of("ISHAR.EXE", "ishar.exe", "INSTALL.EXE")));
		// the start program with its checksum, in any case; another checksum (another version) is not the game
		assertEquals("Ishar: Legend of the Fortress", org.dbgl.util.OwnKnownGames.match(read, java.util.Map.of("ishar.exe", md5, "README.TXT", other)).title());
		assertNull(org.dbgl.util.OwnKnownGames.match(read, java.util.Map.of("ISHAR.EXE", other)));
		assertNull(org.dbgl.util.OwnKnownGames.match(read, java.util.Map.of("INSTALL.EXE", other))); // the setup program alone is not enough
		// TEST24: the start program changed (another checksum), the setup program the same: still the game; the setup alone is not
		String changed = "00000000000000000000000000000000";
		assertEquals("Ishar: Legend of the Fortress", org.dbgl.util.OwnKnownGames.matchBySetup(read, java.util.Map.of("ISHAR.EXE", changed, "install.exe", other)).title());
		assertNull(org.dbgl.util.OwnKnownGames.matchBySetup(read, java.util.Map.of("INSTALL.EXE", other)));
		// TEST29: several learned games in one folder: all of them
		java.util.List<org.dbgl.util.OwnKnownGames.Entry> wc = java.util.List.of(new org.dbgl.util.OwnKnownGames.Entry("WC2", "", "", "", "", java.util.List.of(new org.dbgl.util.OwnKnownGames.KnownFile("WC2.EXE", md5, false))),
			new org.dbgl.util.OwnKnownGames.Entry("SO1", "", "", "", "", java.util.List.of(new org.dbgl.util.OwnKnownGames.KnownFile("SO1.EXE", other, false))));
		assertEquals(2, org.dbgl.util.OwnKnownGames.matchAll(wc, java.util.Map.of("WC2.EXE", md5, "so1.exe", other)).size());
		assertEquals(java.util.List.of("WC2"), org.dbgl.util.OwnKnownGames.report(wc, java.util.Map.of("WC2.EXE", java.util.Set.of(md5), "SO1.EXE", java.util.Set.of(other)), java.util.Set.of("WC2")).recognized().subList(0, 1));
		assertTrue(org.dbgl.util.OwnKnownGames.report(wc, java.util.Map.of("WC2.EXE", java.util.Set.of(md5), "SO1.EXE", java.util.Set.of(other)), java.util.Set.of("WC2")).changed().isEmpty());
		assertNull(org.dbgl.util.OwnKnownGames.matchBySetup(read, java.util.Map.of("ISHAR.EXE", changed, "INSTALL.EXE", changed)));
		// what became of the learned games in a search
		java.util.List<org.dbgl.util.OwnKnownGames.Entry> two = new java.util.ArrayList<>(read);
		two.add(new org.dbgl.util.OwnKnownGames.Entry("Gone", "", "", "", "", java.util.List.of(new org.dbgl.util.OwnKnownGames.KnownFile("GONE.EXE", md5, false))));
		org.dbgl.util.OwnKnownGames.Report report = org.dbgl.util.OwnKnownGames.report(two, java.util.Map.of("ishar.exe", java.util.Set.of(changed)), java.util.Set.of());
		assertEquals(java.util.List.of("Ishar: Legend of the Fortress (ISHAR.EXE)"), report.changed());
		assertEquals(java.util.List.of("Gone (GONE.EXE)"), report.missing());
		assertEquals(java.util.List.of("Gone"), org.dbgl.util.OwnKnownGames.report(two, java.util.Map.of(), java.util.Set.of("Gone")).recognized());
		// what is no valid entry is left out: no title, a path as name, a bad checksum
		Files.writeString(f.toPath(), "{\"games\":[{\"title\":\"\",\"files\":[{\"name\":\"A.EXE\",\"md5\":\"" + md5 + "\"}]},"
				+ "{\"title\":\"B\",\"files\":[{\"name\":\"..\\\\B.EXE\",\"md5\":\"" + md5 + "\"}]},{\"title\":\"C\",\"files\":[{\"name\":\"C.EXE\",\"md5\":\"xyz\"}]},"
				+ "{\"title\":\"D\",\"files\":[{\"name\":\"D.EXE\",\"md5\":\"" + md5.toUpperCase() + "\"}]}]}");
		assertEquals(java.util.List.of("D"), org.dbgl.util.OwnKnownGames.load(f).stream().map(org.dbgl.util.OwnKnownGames.Entry::title).toList());
		Files.writeString(f.toPath(), "{ damaged");
		assertTrue(org.dbgl.util.OwnKnownGames.load(f).isEmpty());
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	/** alpha 53: learning from a profile: start and setup program with checksums, its details; a ScummVM-free, Windows-free game */
	@Test
	public void testOwnKnownGamesLearnFromProfile() throws Exception {
		File dir = Files.createTempDirectory("dbgl-ownlearn-").toFile();
		File dosboxConf = new File(dir, "dosbox.conf");
		Files.copy(new File(DATA_DIR, "dosbox-staging-0.83.0.conf").toPath(), dosboxConf.toPath());
		org.dbgl.model.aggregate.DosboxVersion dbv = DosboxVersionFactory.create(9100, "Staging", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, dir.getPath(), "", "",
			dosboxConf.getPath(), null, null, null, 0);
		dbv.resetAndLoadConfiguration();
		File game = new File(dir, "ISHAR");
		game.mkdirs();
		Files.write(new File(game, "ISHAR.EXE").toPath(), program(false));
		Files.write(new File(game, "INSTALL.EXE").toPath(), "setup".getBytes());
		org.dbgl.model.aggregate.Profile p = org.dbgl.model.factory.ProfileFactory.create(dbv, null);
		p.setTitle("Ishar: Legend of the Fortress");
		assertFalse(org.dbgl.util.OwnKnownGames.worthLearning(p)); // a title alone: perhaps only the name of the folder
		p.setYear("1992");
		assertTrue(org.dbgl.util.OwnKnownGames.worthLearning(p));
		p.setAutoexecSettings(new File(game, "ISHAR.EXE").getAbsolutePath(), "");
		p.setSetupFileLocation(new File(game, "INSTALL.EXE").getAbsolutePath());
		File f = org.dbgl.util.OwnKnownGames.file(dir);
		assertEquals(1, org.dbgl.util.OwnKnownGames.learn(f, java.util.List.of(p)));
		assertEquals(1, org.dbgl.util.OwnKnownGames.learn(f, java.util.List.of(p))); // again: replaced, not twice
		java.util.List<org.dbgl.util.OwnKnownGames.Entry> read = org.dbgl.util.OwnKnownGames.load(f);
		assertEquals(1, read.size());
		assertEquals("ISHAR.EXE", read.get(0).main().name());
		assertEquals(org.dbgl.util.OwnKnownGames.md5(new File(game, "ISHAR.EXE")), read.get(0).main().md5());
		assertEquals("INSTALL.EXE", read.get(0).setupFile().name());
		assertEquals("1992", read.get(0).year());
		assertFalse(Files.readString(f.toPath()).contains(dir.getName())); // no paths kept
		// the import of known games: the folder recognized by the start program learned, with the details and the setup program
		java.util.List<org.apache.commons.lang3.tuple.ImmutablePair<File, byte[]>> files = new java.util.ArrayList<>();
		for (String n: new String[] {"ISHAR.EXE", "INSTALL.EXE"})
			files.add(new org.apache.commons.lang3.tuple.ImmutablePair<>(new File(game, n), org.dbgl.util.FilesUtils.md5(new File(game, n))));
		org.dbgl.model.repository.GameFilesRepository.GameDirEntry e = org.dbgl.gui.dialog.wizard.ImportDirDialog.ownGameDirEntry(game, files, read, "own");
		assertEquals("Ishar: Legend of the Fortress", e.webProfile_.getTitle());
		assertEquals("1992", e.webProfile_.getYear());
		assertEquals(new File(game, "ISHAR.EXE"), e.getMain());
		assertEquals(new File(game, "INSTALL.EXE"), e.getSetup());
		assertEquals(50, e.score_);
		Files.write(new File(game, "ISHAR.EXE").toPath(), program(true)); // another version of the program: not recognized
		files.set(0, new org.apache.commons.lang3.tuple.ImmutablePair<>(new File(game, "ISHAR.EXE"), org.dbgl.util.FilesUtils.md5(new File(game, "ISHAR.EXE"))));
		// TEST24: the setup program still the same: recognized, with a lower score; without the setup program not
		assertEquals(30, org.dbgl.gui.dialog.wizard.ImportDirDialog.ownGameDirEntry(game, files, read, "own").score_);
		assertNull(org.dbgl.gui.dialog.wizard.ImportDirDialog.ownGameDirEntry(game, files.subList(0, 1), read, "own"));
		// the import from a folder: a whole game folder, the start program in a folder below; an unknown folder is not in the result
		File collection = new File(dir, "collection");
		File copy = new File(collection, "Ishar 1/BIN");
		copy.mkdirs();
		Files.write(new File(copy, "ISHAR.EXE").toPath(), program(false)); // the version learned (the one in the game was changed above)
		Files.copy(new File(game, "INSTALL.EXE").toPath(), new File(collection, "Ishar 1/INSTALL.EXE").toPath());
		File other = new File(collection, "Other");
		other.mkdirs();
		Files.write(new File(other, "OTHER.EXE").toPath(), program(false));
		java.util.Map<File, org.dbgl.model.repository.GameFilesRepository.GameDirEntry> rec = org.dbgl.gui.dialog.wizard.ImportDirDialog.recognize(
			List.of(new File(collection, "Ishar 1"), other), read, false, "own");
		assertEquals(java.util.Set.of(new File(collection, "Ishar 1")), rec.keySet());
		assertEquals(new File(copy, "ISHAR.EXE"), rec.get(new File(collection, "Ishar 1")).getMain());
		assertEquals(new File(collection, "Ishar 1/INSTALL.EXE"), rec.get(new File(collection, "Ishar 1")).getSetup());
		assertEquals("Ishar: Legend of the Fortress", rec.get(new File(collection, "Ishar 1")).webProfile_.getTitle());
		// Windows started: not the game
		p.setAutoexecSettings(new File(game, "WIN.COM").getAbsolutePath(), "");
		Files.write(new File(game, "WIN.COM").toPath(), new byte[10]);
		assertNull(org.dbgl.util.OwnKnownGames.fromProfile(p));
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	/**
	 * alpha 53: the recognition of known games by the database of DBGL: a folder given up on leaves no scores behind for the next,
	 * and one further file alone is not enough
	 */
	@Test
	public void testKnownGamesScoring() throws Exception {
		java.lang.reflect.Method construct = org.dbgl.gui.dialog.wizard.ImportDirDialog.class.getDeclaredMethod("constructGameDirEntry", File.class, List.class, java.util.Map.class);
		construct.setAccessible(true);
		File dir = Files.createTempDirectory("dbgl-knownscore-").toFile();
		File data = new File(dir, "DATA"); // known files, but no program: given up on
		data.mkdirs();
		Files.write(new File(data, "LEVEL1.DAT").toPath(), "level".getBytes());
		Files.write(new File(data, "LEVEL2.DAT").toPath(), "level2".getBytes());
		File driver = new File(dir, "OTHER"); // only a further file that many games have, and a program of its own
		driver.mkdirs();
		Files.write(new File(driver, "SNDDRV.COM").toPath(), "driver".getBytes());
		Files.write(new File(driver, "OTHER.EXE").toPath(), program(false));
		java.util.function.Function<File, byte[]> md5 = f -> {
			try {
				return org.dbgl.util.FilesUtils.md5(f);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		};
		org.dbgl.model.repository.GameFilesRepository.GameData game = new org.dbgl.model.repository.GameFilesRepository.GameData();
		java.util.Map<String, org.dbgl.model.repository.GameFilesRepository.FileData> set = new java.util.HashMap<>();
		set.put("LEVEL1.DAT", new org.dbgl.model.repository.GameFilesRepository.FileData(org.dbgl.model.repository.GameFilesRepository.FileType.EXTRA, md5.apply(new File(data, "LEVEL1.DAT"))));
		set.put("LEVEL2.DAT", new org.dbgl.model.repository.GameFilesRepository.FileData(org.dbgl.model.repository.GameFilesRepository.FileType.EXTRA, md5.apply(new File(data, "LEVEL2.DAT"))));
		set.put("SNDDRV.COM", new org.dbgl.model.repository.GameFilesRepository.FileData(org.dbgl.model.repository.GameFilesRepository.FileType.EXTRA, md5.apply(new File(driver, "SNDDRV.COM"))));
		game.fileSets_.add(new org.dbgl.model.repository.GameFilesRepository.FileSet(set));
		java.util.Map<Integer, org.dbgl.model.repository.GameFilesRepository.GameData> games = new java.util.HashMap<>(java.util.Map.of(1, game));
		List<org.apache.commons.lang3.tuple.ImmutablePair<File, byte[]>> dataFiles = List.of(
			new org.apache.commons.lang3.tuple.ImmutablePair<>(new File(data, "LEVEL1.DAT"), md5.apply(new File(data, "LEVEL1.DAT"))),
			new org.apache.commons.lang3.tuple.ImmutablePair<>(new File(data, "LEVEL2.DAT"), md5.apply(new File(data, "LEVEL2.DAT"))));
		assertNull(construct.invoke(null, data, dataFiles, games)); // no program in it
		assertEquals(0, game.fileSets_.get(0).getScore()); // before: 20 left behind
		List<org.apache.commons.lang3.tuple.ImmutablePair<File, byte[]>> driverFiles = List.of(
			new org.apache.commons.lang3.tuple.ImmutablePair<>(new File(driver, "SNDDRV.COM"), md5.apply(new File(driver, "SNDDRV.COM"))));
		assertNull(construct.invoke(null, driver, driverFiles, games)); // before: the game of the data files, or of the driver alone
		assertEquals(0, game.fileSets_.get(0).getScore());
		// a start program that two games have (e.g. SIERRA.COM): no game; the setup program alone: no game either
		File sierra = new File(dir, "SQ3");
		sierra.mkdirs();
		Files.write(new File(sierra, "SIERRA.COM").toPath(), program(false));
		Files.write(new File(sierra, "INSTALL.EXE").toPath(), "install".getBytes());
		byte[] loader = md5.apply(new File(sierra, "SIERRA.COM")), install = md5.apply(new File(sierra, "INSTALL.EXE"));
		games.clear();
		for (int id = 1; id <= 2; id++) {
			org.dbgl.model.repository.GameFilesRepository.GameData g = new org.dbgl.model.repository.GameFilesRepository.GameData();
			java.util.Map<String, org.dbgl.model.repository.GameFilesRepository.FileData> fs = new java.util.HashMap<>();
			fs.put("SIERRA.COM", new org.dbgl.model.repository.GameFilesRepository.FileData(org.dbgl.model.repository.GameFilesRepository.FileType.MAIN, loader));
			if (id == 1)
				fs.put("INSTALL.EXE", new org.dbgl.model.repository.GameFilesRepository.FileData(org.dbgl.model.repository.GameFilesRepository.FileType.SETUP, install));
			g.fileSets_.add(new org.dbgl.model.repository.GameFilesRepository.FileSet(fs));
			g.webProfile_.setGameId(100 + id);
			games.put(id, g);
		}
		List<org.apache.commons.lang3.tuple.ImmutablePair<File, byte[]>> sierraFiles = List.of(
			new org.apache.commons.lang3.tuple.ImmutablePair<>(new File(sierra, "SIERRA.COM"), loader),
			new org.apache.commons.lang3.tuple.ImmutablePair<>(new File(sierra, "INSTALL.EXE"), install));
		// two releases of the same game share their start program: that still counts (the map has releases, not games)
		java.util.Map<Integer, org.dbgl.model.repository.GameFilesRepository.GameData> releases = new java.util.HashMap<>(games);
		releases.get(2).webProfile_.setGameId(101);
		java.lang.reflect.Method score = org.dbgl.gui.dialog.wizard.ImportDirDialog.class.getDeclaredMethod("scoreGameDirEntry", File.class, List.class, java.util.Map.class);
		score.setAccessible(true);
		try {
			score.invoke(null, sierra, sierraFiles, releases); // gets as far as the details of the database, which this test does not have
		} catch (java.lang.reflect.InvocationTargetException expected) {
			// fine
		}
		assertEquals(50, releases.get(1).fileSets_.get(0).getScore()); // SIERRA.COM 35 and INSTALL.EXE 15 counted
		releases.values().forEach(x -> x.fileSets_.forEach(y -> y.set_.values().forEach(org.dbgl.model.repository.GameFilesRepository.FileData::resetScore)));
		releases.get(2).webProfile_.setGameId(102);
		assertNull(construct.invoke(null, sierra, sierraFiles, games)); // the loader counts for neither, the setup program (15) alone is too little
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	/** alpha 53: the start program guessed for a game recognized by other files */
	@Test
	public void testImportGuessMain() {
		File dir = new File("SPIELE", "BLOOD");
		java.util.function.Function<String, File> f = n -> new File(dir, n);
		java.util.function.BiFunction<List<File>, List<String>, String> guess = (exes, names) -> org.dbgl.gui.dialog.wizard.ImportDirDialog.guessMain(dir, exes, names).map(
			x -> dir.toPath().relativize(x.toPath()).toString().replace('\\', '/')).orElse("-");
		// the start program of the database by its name (here patched, so another checksum), in the folder itself before one below
		assertEquals("BLOOD.EXE", guess.apply(List.of(f.apply("3DFX.EXE"), f.apply("BLOOD.EXE"), f.apply("SUB/BLOOD.EXE"), f.apply("SETUP.EXE")), List.of("BLOOD.EXE")));
		assertEquals("SUB/BLOOD.EXE", guess.apply(List.of(f.apply("SETUP.EXE"), f.apply("SUB/BLOOD.EXE")), List.of("blood.exe")));
		// else one named like the folder
		assertEquals("BLOOD.BAT", guess.apply(List.of(f.apply("A.EXE"), f.apply("BLOOD.BAT"), f.apply("SETUP.EXE")), List.of()));
		// else the only program that is no helper
		assertEquals("GAME.EXE", guess.apply(List.of(f.apply("GAME.EXE"), f.apply("SETUP.EXE"), f.apply("DOS4GW.EXE"), f.apply("INSTALL.EXE")), List.of()));
		// two candidates: none guessed
		assertEquals("-", guess.apply(List.of(f.apply("A.EXE"), f.apply("B.EXE"), f.apply("SETUP.EXE")), List.of()));
		assertEquals("-", guess.apply(List.of(f.apply("SETUP.EXE"), f.apply("SUB/GAME.EXE")), List.of()));
	}

	/** alpha 53: DOSBox and ScummVM themselves are no games: their folders are left out of the import from a folder */
	@Test
	public void testUnlinkedLeavesOutDosbox() throws Exception {
		File root = Files.createTempDirectory("dbgl-unlinkeddosbox-").toFile();
		new File(root, "Doom").mkdirs();
		Files.write(new File(root, "Doom/DOOM.EXE").toPath(), program(false));
		new File(root, "dosbox-x/drivez").mkdirs(); // a DOSBox-X with DOS tools of its own
		Files.write(new File(root, "dosbox-x/dosbox-x.exe").toPath(), program(true));
		Files.write(new File(root, "dosbox-x/drivez/MOUSE.COM").toPath(), new byte[100]);
		Files.write(new File(root, "dosbox-x/drivez/BOOTBOX.EXE").toPath(), program(false));
		new File(root, "Staging/tools").mkdirs(); // entered in DBGL: left out whatever it holds
		Files.write(new File(root, "Staging/tools/TOOL.EXE").toPath(), program(false));
		new File(root, "GogGame/DOSBOX").mkdirs(); // a game of GOG with its DOSBox below: counted as before
		Files.write(new File(root, "GogGame/goggame-1.info").toPath(), "{}".getBytes());
		Files.write(new File(root, "GogGame/DOSBOX/dosbox.exe").toPath(), program(true));
		Files.write(new File(root, "GogGame/GAME.EXE").toPath(), program(false));
		org.dbgl.util.UnlinkedGames.Result r = org.dbgl.util.UnlinkedGames.find(root, java.util.List.of(), java.util.List.of(new File(root, "Staging")), null, () -> false);
		assertEquals(java.util.List.of("Doom"), r.games().stream().map(g -> g.dir().getName()).toList());
		assertEquals(1, r.store());
		assertEquals(0, r.empty());
		// a DOSBox in the chosen folder itself or above it (e.g. DBGL's own folder) does not leave everything out
		r = org.dbgl.util.UnlinkedGames.find(root, java.util.List.of(), java.util.List.of(root, root.getParentFile()), null, () -> false);
		assertEquals(java.util.List.of("Doom", "Staging"), r.games().stream().map(g -> g.dir().getName()).toList());
		org.apache.commons.io.FileUtils.deleteDirectory(root);
	}

	/** alpha 53: the import has the newest DOSBox Staging chosen at first, else the default version */
	@Test
	public void testImportPreselectsStaging() {
		org.dbgl.model.aggregate.DosboxVersion plain = org.dbgl.model.factory.DosboxVersionFactory.create("DOSBox 0.74-3", "DOSBox", "0.74-3", true, false, false, null, "a", "", "", "a/dosbox.conf");
		org.dbgl.model.aggregate.DosboxVersion s82 = org.dbgl.model.factory.DosboxVersionFactory.create("DOSBox Staging 0.82.2", "DOSBox Staging", "0.82.2", false, false, false, null, "b", "", "", "b/dosbox.conf");
		org.dbgl.model.aggregate.DosboxVersion s83 = org.dbgl.model.factory.DosboxVersionFactory.create("DOSBox Staging 0.83.1", "DOSBox Staging", "0.83.1", false, false, false, null, "c", "", "", "c/dosbox.conf");
		org.dbgl.model.aggregate.DosboxVersion x = org.dbgl.model.factory.DosboxVersionFactory.create("DOSBox-X 2026.10.01", "DOSBox-X", "2026.10.01", false, false, false, null, "d", "", "", "d/dosbox-x.conf");
		assertSame(s83, org.dbgl.gui.dialog.StoreImportDialog.preselected(java.util.List.of(plain, s83, s82, x), plain));
		assertSame(plain, org.dbgl.gui.dialog.StoreImportDialog.preselected(java.util.List.of(plain, x), plain));
	}

	/** alpha 53: with several profiles at once, a title written a little differently is an exact match when it is the only one */
	@Test
	public void testSearchEngineTitleMatch() {
		java.util.function.Function<String[], java.util.List<org.dbgl.model.WebProfile>> entries = titles -> java.util.Arrays.stream(titles).map(t -> {
			org.dbgl.model.WebProfile w = new org.dbgl.model.WebProfile();
			w.setTitle(t);
			return w;
		}).toList();
		java.util.List<org.dbgl.model.WebProfile> l = entries.apply(new String[] {"Monkey Island 2: LeChuck's Revenge", "Monkey Island 2: LeChuck's Revenge - Special Edition"});
		assertEquals(0, org.dbgl.util.searchengine.WebSearchEngine.getEntryFirstExactMatchIndex("monkey island 2 - lechuck's revenge", l));
		assertEquals(0, org.dbgl.util.searchengine.WebSearchEngine.getEntryFirstExactMatchIndex("Monkey Island 2 LeChucks Revenge", l));
		assertEquals(1, org.dbgl.util.searchengine.WebSearchEngine.getEntryFirstExactMatchIndex("Monkey Island 2: LeChuck's Revenge - Special Edition", l));
		assertEquals(0, org.dbgl.util.searchengine.WebSearchEngine.getEntryFirstExactMatchIndex("Lost Vikings", entries.apply(new String[] {"The Lost Vikings"})));
		assertEquals(0, org.dbgl.util.searchengine.WebSearchEngine.getEntryFirstExactMatchIndex("Spy vs Spy and more", entries.apply(new String[] {"Spy vs. Spy & More"})));
		assertEquals(0, org.dbgl.util.searchengine.WebSearchEngine.getEntryFirstExactMatchIndex("Pokemon", entries.apply(new String[] {"Pokémon"})));
		// two the same that way: none taken
		assertEquals(-1, org.dbgl.util.searchengine.WebSearchEngine.getEntryFirstExactMatchIndex("Doom 2", entries.apply(new String[] {"DOOM II", "Doom: 2", "Doom-2"})));
		assertEquals(-1, org.dbgl.util.searchengine.WebSearchEngine.getEntryFirstExactMatchIndex("Doom", entries.apply(new String[] {"Doom II"})));
		assertEquals("", org.dbgl.util.searchengine.WebSearchEngine.comparable("!!"));
	}

	/** alpha 53: the texts inside the program (Help > New features, Changelog) the same as those shipped next to it: the "new" ones missed alpha 51 and 52 */
	@Test
	public void testDocCopiesInSync() throws IOException {
		File root = new File(DATA_DIR).getAbsoluteFile();
		for (int i = 0; i < 6 && root != null; i++)
			root = root.getParentFile();
		org.junit.Assume.assumeTrue("not run in the repository", (root != null) && new File(root, "CHANGELOG-en.txt").isFile());
		String[][] pairs = {{"CHANGELOG-de.txt", "changelog_de.txt"}, {"CHANGELOG-en.txt", "changelog_en.txt"}, {"NEU-IN-DER-EXTENDED-EDITION.txt", "whatsnew_de.txt"},
				{"NEW-IN-EXTENDED-EDITION.txt", "whatsnew_en.txt"}};
		for (String[] pair: pairs)
			org.junit.Assert.assertArrayEquals(pair[0], Files.readAllBytes(new File(root, pair[0]).toPath()), Files.readAllBytes(new File(root, "src/resources/doc/" + pair[1]).toPath()));
	}

	/** alpha 53: the port for "Send to" and Steam only on this computer (it was open to the network; Windows asked about the firewall) */
	@Test
	public void testMessagingOnlyLocal() throws Exception {
		int port;
		try (java.net.ServerSocket probe = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
			port = probe.getLocalPort();
		}
		org.dbgl.connect.Messaging m = new org.dbgl.connect.Messaging(port, null, GenericTextService.getInstance());
		m.setDaemon(true);
		m.start();
		try {
			java.lang.reflect.Field f = org.dbgl.connect.Messaging.class.getDeclaredField("server_");
			f.setAccessible(true);
			java.net.ServerSocket server = null;
			for (int i = 0; (i < 100) && (server == null || !server.isBound()); i++) {
				Thread.sleep(20);
				server = (java.net.ServerSocket)f.get(m);
			}
			assertNotNull(server);
			assertTrue(server.getInetAddress().toString(), server.getInetAddress().isLoopbackAddress());
			try (java.net.Socket s = new java.net.Socket(java.net.InetAddress.getLoopbackAddress(), port)) {
				assertTrue(s.isConnected()); // as RunProfile and SendToProfile connect
			}
		} finally {
			m.close();
		}
	}

	/** alpha 52: a too long publisher (Doom from MobyGames, reported on Vogons) made saving fail: now cut after a whole name, marked */
	/**
	 * Alpha 53: the configurations of DBConf from the database next to DBGL (DBGL 0.100 beta 18 by Ronald Blankendaal), read
	 * read-only; the exact match leaves out longer namesakes.
	 */
	@Test
	public void testDBConfDatabase() throws Exception {
		File dist = new File(DATA_DIR, "../../../../../dist/shared/db");
		File tmp = Files.createTempDirectory("dbgl-dbconf-").toFile();
		try {
			for (String name: new String[] {org.dbgl.service.DBConfDatabaseService.DATA, org.dbgl.service.DBConfDatabaseService.SCRIPT,
					org.dbgl.service.DBConfDatabaseService.PROPERTIES})
				Files.copy(new File(dist, name).toPath(), new File(tmp, name).toPath());
			File script = new File(tmp, org.dbgl.service.DBConfDatabaseService.SCRIPT);
			long before = script.lastModified();
			List<org.dbgl.model.entity.SharedConf> all = org.dbgl.service.DBConfDatabaseService.load(new File(tmp, "dbconf"));
			assertTrue(all.size() >= 270);
			assertTrue(all.stream().allMatch(c -> (c.getGameTitle() != null) && (c.getIncrConf() != null) && (c.getDosboxVersion() != null)));
			// read-only: nothing written, no lock or log left behind
			assertEquals(before, script.lastModified());
			assertEquals(3, tmp.list().length);

			org.dbgl.model.entity.SharedConf doom = new org.dbgl.model.entity.SharedConf(null, "", "Doom", "", "1993", "[cpu]\ncycles=max", "", "x", "DOSBox", "0.74");
			org.dbgl.model.entity.SharedConf doom2 = new org.dbgl.model.entity.SharedConf(null, "", "Doom II: Hell on Earth", "", "1994", "[cpu]\ncycles=max", "", "x", "DOSBox", "0.74");
			org.dbgl.model.entity.SharedConf theDoom = new org.dbgl.model.entity.SharedConf(null, "", "The DOOM", "", "1993", "", "", "x", "DOSBox", "0.74");
			List<org.dbgl.model.entity.SharedConf> confs = List.of(doom2, doom, theDoom);
			assertEquals(List.of(doom, theDoom), org.dbgl.service.DBConfDatabaseService.exact(confs, "doom"));
			assertTrue(org.dbgl.service.DBConfDatabaseService.exact(confs, "").isEmpty());
			assertTrue(org.dbgl.service.DBConfDatabaseService.exact(confs, "Doom 3").isEmpty());
			for (org.dbgl.model.entity.SharedConf c: all)
				assertTrue(org.dbgl.service.DBConfDatabaseService.exact(all, c.getGameTitle()).contains(c));
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(tmp);
		}
		assertNotNull(org.dbgl.service.DBConfDatabaseService.load(new File(System.getProperty("java.io.tmpdir"), "no-such-dbconf")));
		assertTrue(org.dbgl.service.DBConfDatabaseService.load(new File(System.getProperty("java.io.tmpdir"), "no-such-dbconf")).isEmpty());
	}

	/** Alpha 53: the note of "Check profiles" from DBConf: DOSBox 0.74 only, exactly the title, only settings of its own */
	@Test
	public void testDBConfAdvice() throws Exception {
		org.dbgl.model.entity.SharedConf demon = new org.dbgl.model.entity.SharedConf(null, "", "3-Demon", "", "1983", "[dosbox]\nmachine=cga\n\n[cpu]\ncycles=750\n[autoexec]\nmount c .", "", "x", "DOSBox", "0.74");
		org.dbgl.model.entity.SharedConf empty = new org.dbgl.model.entity.SharedConf(null, "", "Doom", "", "1993", "", "", "N/A", "DOSBox", "0.74");
		org.dbgl.model.entity.SharedConf other = new org.dbgl.model.entity.SharedConf(null, "", "3-Demon", "", "1983", "[cpu]\ncycles=1000", "", "x", "DOSBox", "0.74");
		assertEquals(2, org.dbgl.util.DBConfAdvice.settings(demon.getIncrConf()).size()); // without [autoexec]
		assertSame(demon, org.dbgl.util.DBConfAdvice.configurationFor(List.of(demon, empty), "3-demon"));
		assertNull(org.dbgl.util.DBConfAdvice.configurationFor(List.of(demon, empty), "Doom")); // no configuration at all
		// nothing of its own: the full configuration says what does (TEST12: Doom with memsize = 8 got no note); settings about
		// the own computer are left out
		org.dbgl.model.entity.SharedConf doom = new org.dbgl.model.entity.SharedConf(null, "", "DOOM", "", "1993", "",
				"[sdl]\nfullscreen=false\noutput=surface\n[dosbox]\nmachine=svga_s3\nmemsize=16\n[dos]\nkeyboardlayout=auto\n[autoexec]\nmount c .", "N/A", "DOSBox", "0.74");
		assertSame(doom, org.dbgl.util.DBConfAdvice.configurationFor(List.of(doom), "Doom"));
		Configuration mine = new Configuration();
		mine.setValue("sdl", "fullscreen", "true");
		mine.setValue("dosbox", "machine", "svga_s3");
		mine.setValue("dosbox", "memsize", "8");
		mine.setValue("dos", "keyboardlayout", "gr");
		assertEquals("[dosbox] memsize = 16", org.dbgl.util.DBConfAdvice.shown(org.dbgl.util.DBConfAdvice.differences(doom, mine), true));

		// loading: DOSBox 0.74 also takes the settings about the game from the full configuration, the others the own ones only
		org.dbgl.model.aggregate.DosboxVersion v74 = DosboxVersionFactory.create("t", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.74-3", false, false, false, null, "x", "", "", "x/dosbox.conf");
		org.dbgl.model.aggregate.DosboxVersion st = DosboxVersionFactory.create("t", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.1", false, false, false, null, "x", "", "", "x/dosbox.conf");
		assertEquals("[dosbox]\nmachine=svga_s3\nmemsize=16\n", org.dbgl.util.DBConfAdvice.toLoad(doom, v74));
		assertEquals("", org.dbgl.util.DBConfAdvice.toLoad(doom, st));
		org.dbgl.model.entity.SharedConf mixed = new org.dbgl.model.entity.SharedConf(null, "", "X", "", "1990", "[cpu]\ncputype=pentium_slow\ncycles=3000\n\n[autoexec]\nmount c .",
				"[dosbox]\nmemsize=16\n[cpu]\ncputype=auto\ncycles=auto\ncore=auto\n[sdl]\nfullscreen=true", "x", "DOSBox", "0.74");
		assertEquals("[cpu]\ncputype=pentium_slow\ncycles=3000\ncore=auto\n\n[dosbox]\nmemsize=16\n\n[autoexec]\nmount c .\n", org.dbgl.util.DBConfAdvice.toLoad(mixed, v74));
		assertEquals("[cpu]\ncputype=pentium\ncycles=3000\n\n[autoexec]\nmount c .\n", org.dbgl.util.DBConfAdvice.toLoad(mixed, st));
		Configuration loaded = new Configuration();
		loaded.loadDataWithAutoexec(GenericTextService.getInstance(), org.dbgl.util.DBConfAdvice.toLoad(mixed, v74), new File("x.conf"), null);
		assertEquals("16", loaded.getValue("dosbox", "memsize"));
		assertEquals("3000", loaded.getValue("cpu", "cycles"));
		assertEquals("auto", loaded.getValue("cpu", "core"));
		assertFalse(loaded.hasValue("sdl", "fullscreen"));
		assertNull(org.dbgl.util.DBConfAdvice.configurationFor(List.of(demon, empty), "3-Demon 2"));
		assertNull(org.dbgl.util.DBConfAdvice.configurationFor(List.of(demon, other), "3-Demon")); // two that do not agree

		Configuration applies = new Configuration();
		applies.setValue("dosbox", "machine", "svga_s3");
		applies.setValue("cpu", "cycles", "750");
		List<org.dbgl.util.DBConfAdvice.Difference> d = org.dbgl.util.DBConfAdvice.differences(demon, applies);
		assertEquals(1, d.size());
		assertEquals("[dosbox] machine = cga", org.dbgl.util.DBConfAdvice.shown(d, true));
		assertEquals("[dosbox] machine = svga_s3", org.dbgl.util.DBConfAdvice.shown(d, false));

		String x = SettingsService.DOSBOX_X_DOSBOX_FAM.title_, staging = SettingsService.STAGING_DOSBOX_FAM.title_;
		assertTrue(org.dbgl.util.DBConfAdvice.forVersion(DosboxVersionFactory.create("t", SettingsService.OFFICIAL_DOSBOX_FAM.title_, "0.74-3", false, false, false, null, "x", "", "", "x/dosbox.conf")));
		assertFalse(org.dbgl.util.DBConfAdvice.forVersion(DosboxVersionFactory.create("t", staging, "0.83.1", false, false, false, null, "x", "", "", "x/dosbox.conf")));
		assertFalse(org.dbgl.util.DBConfAdvice.forVersion(DosboxVersionFactory.create("t", x, "2026.10.01", false, false, false, null, "x", "", "", "x/dosbox.conf")));
		assertFalse(org.dbgl.util.DBConfAdvice.forVersion(DosboxVersionFactory.create("t", "Something else", "1.0", false, false, false, null, "x", "", "", "x/dosbox.conf")));
		assertFalse(org.dbgl.util.DBConfAdvice.forVersion(null));
	}

	/** Alpha 53: a setup program for Windows (Ultima VII Part Two: SETUP.EXE of a screen saver) is not guessed */
	@Test
	public void testNoWindowsSetupGuessed() throws Exception {
		File dir = Files.createTempDirectory("dbgl-winsetup-").toFile();
		try {
			byte[] ne = new byte[0x100];
			ne[0] = 'M';
			ne[1] = 'Z';
			ne[0x3c] = (byte)0x80;
			ne[0x80] = 'N';
			ne[0x81] = 'E';
			ne[0x80 + 0x36] = 2; // Windows
			File setup = new File(dir, "SETUP.EXE"), install = new File(dir, "INSTALL.EXE"), game = new File(dir, "SERPENT.EXE");
			Files.write(setup.toPath(), ne);
			Files.write(install.toPath(), "MZ dos program".getBytes(StandardCharsets.ISO_8859_1));
			Files.write(game.toPath(), "MZ dos program".getBytes(StandardCharsets.ISO_8859_1));
			assertEquals("ne", org.dbgl.util.ProfileChecks.programKind(setup));
			List<File> files = List.of(game, setup, install);
			assertEquals(2, org.dbgl.util.DosGameUtils.findSetupIndex(files)); // INSTALL.EXE, not the SETUP.EXE for Windows
			assertEquals(-1, org.dbgl.util.DosGameUtils.findSetupIndex(List.of(game, setup)));
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
	}

	/** a group file of the Program Manager of Windows 3.1 with these items (name, command line), without icons */
	private static byte[] group(String name, String[][] items) {
		java.io.ByteArrayOutputStream strings = new java.io.ByteArrayOutputStream();
		int base = 34 + 2 * items.length + 24 * items.length;
		java.util.function.Function<String, Integer> add = s -> {
			int at = base + strings.size();
			strings.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
			strings.write(0);
			return at;
		};
		int pName = add.apply(name);
		byte[] d = new byte[base];
		java.util.function.BiConsumer<Integer, Integer> word = (o, v) -> {
			d[o] = (byte)(v & 0xff);
			d[o + 1] = (byte)(v >> 8);
		};
		d[0] = 'P';
		d[1] = 'M';
		d[2] = 'C';
		d[3] = 'C';
		word.accept(22, pName);
		word.accept(32, items.length);
		for (int i = 0; i < items.length; i++) {
			int off = 34 + 2 * items.length + 24 * i;
			word.accept(34 + 2 * i, off);
			word.accept(off + 18, add.apply(items[i][0]));
			word.accept(off + 20, add.apply(items[i][1]));
			word.accept(off + 22, add.apply(""));
		}
		java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
		all.writeBytes(d);
		all.writeBytes(strings.toByteArray());
		byte[] r = all.toByteArray();
		r[6] = (byte)(r.length & 0xff); // the size of the group: the tags (none) follow
		r[7] = (byte)(r.length >> 8);
		return r;
	}

	/** Alpha 53: a profile that starts Windows 3.x: what it starts, its sound and display drivers against DOSBox */
	@Test
	public void testStagingPrimaryConf() {
		// alpha 53: what of the configuration of DOSBox Staging in the user folder counts: what the files of DBGL do not set, its autoexec
		org.dbgl.util.StagingPrimaryConf.Counts c = org.dbgl.util.StagingPrimaryConf.counts(
			"[sdl]\nfullscreen = true\noutput = opengl\n[cpu]\ncycles = max\n[autoexec]\n# a comment\nmount c d:\\games\n", List.of("[sdl]\nfullscreen=false\n"));
		assertEquals(List.of("[sdl] output = opengl", "[cpu] cycles = max"), c.settings());
		assertEquals(List.of("mount c d:\\games"), c.autoexec());
		assertTrue(c.any());
		assertFalse(org.dbgl.util.StagingPrimaryConf.counts("[sdl]\nfullscreen = true\n[autoexec]\n", List.of("[sdl]\nfullscreen=false\n")).any());
	}

	@Test
	public void testStatisticsExtra() {
		// alpha 53: days in a row, the length of sessions, the age of profiles, short sessions only
		java.time.LocalDate d = java.time.LocalDate.of(2026, 11, 20);
		java.util.Set<java.time.LocalDate> days = new java.util.HashSet<>(List.of(d, d.minusDays(1), d.minusDays(2), d.minusDays(5), d.minusDays(6), d.plusDays(4)));
		org.dbgl.util.StatisticsExtra.Streak s = org.dbgl.util.StatisticsExtra.longestStreak(days);
		assertEquals(3, s.days());
		assertEquals(d.minusDays(2), s.from());
		assertEquals(d, s.to());
		assertNull(org.dbgl.util.StatisticsExtra.longestStreak(List.of()));
		assertEquals(3, org.dbgl.util.StatisticsExtra.currentStreak(days, d));
		assertEquals(3, org.dbgl.util.StatisticsExtra.currentStreak(days, d.plusDays(1))); // not played today yet: up to yesterday
		assertEquals(0, org.dbgl.util.StatisticsExtra.currentStreak(days, d.plusDays(2)));
		assertEquals(0, org.dbgl.util.StatisticsExtra.lengthBucket(60));
		assertEquals(2, org.dbgl.util.StatisticsExtra.lengthBucket(45 * 60));
		assertEquals(5, org.dbgl.util.StatisticsExtra.lengthBucket(5 * 3600));
		assertEquals(0, org.dbgl.util.StatisticsExtra.ageBucket(d.minusDays(10), d));
		assertEquals(3, org.dbgl.util.StatisticsExtra.ageBucket(d.minusMonths(13), d));
		assertEquals(4, org.dbgl.util.StatisticsExtra.ageBucket(d.minusYears(3), d));
		assertNull(org.dbgl.util.ProfileFindings.shortSessions(List.of(60, 70)));
		assertNull(org.dbgl.util.ProfileFindings.shortSessions(List.of(60, 70, 900))); // the newest was long
		org.junit.Assert.assertArrayEquals(new Object[] {3, 3}, org.dbgl.util.ProfileFindings.shortSessions(List.of(900, 1200, 60, 70, 40))); // the newest three short
		org.junit.Assert.assertArrayEquals(new Object[] {3, 3}, org.dbgl.util.ProfileFindings.shortSessions(List.of(60, 70, 40)));
	}

	@Test
	public void testCheckExportAnonymous() throws Exception {
		// alpha 53: the folders and names of the user replaced, paths with \\ and / and in any case
		java.util.Map<String, String> r = new java.util.LinkedHashMap<>();
		r.put("D:\\Spiele\\DOS\\dosroot", "<DOSROOT>");
		r.put("D:\\Spiele\\DOS", "<DBGL>");
		r.put("C:\\Users\\Erika", "<HOME>");
		r.put("Erika", "<USER>");
		r.put("ab", "<SHORT>"); // too short to be replaced safely
		java.lang.reflect.Method m = org.dbgl.util.CheckExport.class.getDeclaredMethod("anonymous", String.class, java.util.Map.class);
		m.setAccessible(true);
		String out = (String)m.invoke(null, "mount C \"d:/spiele/dos/dosroot/KEEN\"\nconf D:\\Spiele\\DOS\\profiles\\1.conf\nC:\\Users\\Erika\\x erika@example.org Erikas Erika\nabc", r);
		assertEquals("set PATH=<HOME>\\games;<HOME>\\x\n\"<HOME>\"", m.invoke(null, "set PATH=C:\\Users\\Max Muster\\games;/home/max\\x\n\"C:\\Documents and Settings\\Max\"", r));
		assertEquals("mount C \"<DOSROOT>/KEEN\"\nconf <DBGL>\\profiles\\1.conf\n<HOME>\\x <EMAIL> Erikas <USER>\nabc", out);
	}

	@Test
	public void testImportFolderRulesTest26() throws Exception {
		File dir = Files.createTempDirectory("dbgl-import26-").toFile();
		// SMARTDRV.EXE and the like are no start programs
		File g = new File(dir, "Game");
		g.mkdirs();
		Files.write(new File(g, "SMARTDRV.EXE").toPath(), program(false));
		Files.write(new File(g, "GAME.EXE").toPath(), program(false));
		// Windows in the game folder: WIN.COM starts it, Windows starts the game (run= of WIN.INI)
		File w = new File(dir, "WinGame");
		File windows = new File(w, "WINDOWS");
		windows.mkdirs();
		Files.write(new File(windows, "WIN.COM").toPath(), new byte[200]);
		Files.writeString(new File(windows, "SYSTEM.INI").toPath(), "[boot]\r\nshell=progman.exe\r\n");
		Files.writeString(new File(windows, "WIN.INI").toPath(), "[windows]\r\nrun=C:\\GAME\\PLAY.EXE\r\n");
		Files.write(new File(windows, "SMARTDRV.EXE").toPath(), program(false));
		List<org.dbgl.util.UnlinkedGames.Game> found = org.dbgl.util.UnlinkedGames.find(dir, List.of(), null, null).games();
		assertEquals(2, found.size());
		assertEquals("GAME.EXE", found.get(0).main().getName());
		assertEquals("WIN.COM", found.get(1).main().getName());
		assertEquals("", found.get(1).params()); // Windows starts the game itself
		Files.writeString(new File(windows, "WIN.INI").toPath(), "[windows]\r\n");
		assertEquals("WIN.COM", org.dbgl.util.UnlinkedGames.find(dir, List.of(), null, null).games().get(1).main().getName());
		// the same start program in a folder and one below it: one game, the upper one, with a note
		File sub = new File(g, "COPY");
		sub.mkdirs();
		Files.copy(new File(g, "GAME.EXE").toPath(), new File(sub, "game.exe").toPath());
		org.dbgl.model.WebProfile web = new org.dbgl.model.WebProfile();
		web.setTitle("Game");
		org.dbgl.model.repository.GameFilesRepository.GameDirEntry upper = new org.dbgl.model.repository.GameFilesRepository.GameDirEntry(g,
				java.util.Optional.of(new File(g, "GAME.EXE")), java.util.Optional.empty(), List.of(), web, 35, "x");
		org.dbgl.model.repository.GameFilesRepository.GameDirEntry lower = new org.dbgl.model.repository.GameFilesRepository.GameDirEntry(sub,
				java.util.Optional.of(new File(sub, "game.exe")), java.util.Optional.empty(), List.of(), web, 35, "y");
		List<org.dbgl.model.repository.GameFilesRepository.GameDirEntry> once = org.dbgl.gui.dialog.wizard.ImportDirDialog.nestedOnce(List.of(lower, upper));
		assertEquals(1, once.size());
		assertEquals(g, once.get(0).dir_);
		assertEquals("x", once.get(0).explanation_); // TEST29: the notes in a list of their own (a column of the import)
		assertTrue(once.get(0).notes_.get(0).contains("COPY"));
		// TEST27: a game recognized in the chosen folder itself gets a note
		List<org.dbgl.model.repository.GameFilesRepository.GameDirEntry> marked = org.dbgl.gui.dialog.wizard.ImportDirDialog.markRoot(List.of(upper,
			new org.dbgl.model.repository.GameFilesRepository.GameDirEntry(dir, java.util.Optional.empty(), java.util.Optional.empty(), List.of(), web, 35, "r")), dir);
		assertEquals("x", marked.get(0).explanation_);
		assertTrue(marked.get(1).untick_ && !marked.get(1).notes_.isEmpty());
		// TEST28: the same title in several folders: one ticked (higher score, then the shorter path), the others told and not ticked
		File copy = new File(dir, "Game - Kopie");
		org.dbgl.model.repository.GameFilesRepository.GameDirEntry other = new org.dbgl.model.repository.GameFilesRepository.GameDirEntry(copy,
				java.util.Optional.of(new File(copy, "GAME.EXE")), java.util.Optional.empty(), List.of(), web, 35, "z");
		List<org.dbgl.model.repository.GameFilesRepository.GameDirEntry> titled = org.dbgl.gui.dialog.wizard.ImportDirDialog.sameTitles(List.of(other, upper), dir);
		assertTrue(titled.get(0).untick_);
		assertFalse(titled.get(1).untick_);
		assertTrue(String.join(" ", titled.get(1).notes_).contains("Game - Kopie"));
		// TEST30: a folder with this game alone before one that has another game too; then a folder named like the title
		File coll = new File(dir, "COLL");
		org.dbgl.model.WebProfile otherWeb = new org.dbgl.model.WebProfile();
		otherWeb.setTitle("Collection");
		org.dbgl.model.repository.GameFilesRepository.GameDirEntry inColl = new org.dbgl.model.repository.GameFilesRepository.GameDirEntry(coll,
				java.util.Optional.of(new File(coll, "GAME.EXE")), java.util.Optional.empty(), List.of(), web, 35, "c");
		org.dbgl.model.repository.GameFilesRepository.GameDirEntry collItself = new org.dbgl.model.repository.GameFilesRepository.GameDirEntry(coll,
				java.util.Optional.of(new File(coll, "COLL.EXE")), java.util.Optional.empty(), List.of(), otherWeb, 35, "d");
		org.dbgl.model.repository.GameFilesRepository.GameDirEntry alone = new org.dbgl.model.repository.GameFilesRepository.GameDirEntry(new File(dir, "GAMEFOLDER"),
				java.util.Optional.of(new File(dir, "GAMEFOLDER/GAME.EXE")), java.util.Optional.empty(), List.of(), web, 35, "a");
		org.dbgl.gui.dialog.wizard.ImportDirDialog.sameTitles(List.of(inColl, collItself, alone), dir);
		assertTrue(inColl.untick_);
		assertFalse(alone.untick_);
		assertFalse(collItself.untick_);
		org.dbgl.model.repository.GameFilesRepository.GameDirEntry arger = new org.dbgl.model.repository.GameFilesRepository.GameDirEntry(new File(dir, "ARGER"),
				java.util.Optional.of(new File(dir, "ARGER/X.EXE")), java.util.Optional.empty(), List.of(), web, 35, "");
		org.dbgl.model.repository.GameFilesRepository.GameDirEntry named = new org.dbgl.model.repository.GameFilesRepository.GameDirEntry(new File(dir, "GAMES"),
				java.util.Optional.of(new File(dir, "GAMES/X.EXE")), java.util.Optional.empty(), List.of(), web, 35, "");
		org.dbgl.gui.dialog.wizard.ImportDirDialog.sameTitles(List.of(arger, named), dir);
		assertTrue(arger.untick_);
		assertFalse(named.untick_);
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	@Test
	public void testProfileCompletion() {
		// alpha 53 (TEST24): the traffic light of "Complete profiles", and what is taken into a profile
		java.util.function.BiFunction<String, Integer, org.dbgl.model.WebProfile> w = (title, id) -> {
			org.dbgl.model.WebProfile p = new org.dbgl.model.WebProfile();
			p.setTitle(title);
			p.setGameId(id);
			return p;
		};
		java.util.function.Function<String, List<org.dbgl.model.WebProfile>> db = t -> t.toLowerCase().contains("monkey")
				? List.of(w.apply("The Secret of Monkey Island", 1), w.apply("Monkey Island 2: LeChuck's Revenge", 2)): List.of();
		assertEquals(org.dbgl.util.ProfileCompletion.State.DONE, org.dbgl.util.ProfileCompletion.assess("MI2", true, db, null).state());
		org.dbgl.util.ProfileCompletion.Assessment found = org.dbgl.util.ProfileCompletion.assess("Secret of Monkey Island", false, db, null);
		assertEquals(org.dbgl.util.ProfileCompletion.State.FOUND, found.state()); // "The" left out: still exactly that title
		org.dbgl.util.ProfileCompletion.Assessment likely = org.dbgl.util.ProfileCompletion.assess("Monkey Island 2", false, db, null);
		assertEquals(org.dbgl.util.ProfileCompletion.State.LIKELY, likely.state());
		assertEquals("Monkey Island 2: LeChuck's Revenge", likely.proposal().getTitle());
		assertEquals(org.dbgl.util.ProfileCompletion.State.NONE, org.dbgl.util.ProfileCompletion.assess("MI2", false, db, null).state());
		assertEquals(org.dbgl.util.ProfileCompletion.State.NONE, org.dbgl.util.ProfileCompletion.assess("Monkey", false, null, null).state());
		// taken: the title, the details only where the profile has none
		org.dbgl.model.aggregate.Profile p = org.dbgl.model.factory.ProfileFactory.create((org.dbgl.model.aggregate.DosboxVersion)null, (org.dbgl.model.aggregate.Template)null);
		p.setTitle("MI2");
		p.setYear("1992");
		org.dbgl.model.WebProfile game = w.apply("Monkey Island 2: LeChuck's Revenge", 2);
		game.setYear("1991");
		game.setDeveloperName("LucasArts");
		org.dbgl.util.ProfileCompletion.apply(p, game);
		assertEquals("Monkey Island 2: LeChuck's Revenge", p.getTitle());
		assertEquals("1992", p.getYear());
		assertEquals("LucasArts", p.getDeveloper());
	}

	@Test
	public void testTextTitles() throws Exception {
		// alpha 53 (TEST24): title candidates from the text files of a game folder
		assertTrue(org.dbgl.util.TextTitles.fromText("\r\n   ********************\r\n   WING COMMANDER II\r\n   ********************\r\nPlease read this file.", false)
				.contains("WING COMMANDER II"));
		assertTrue(org.dbgl.util.TextTitles.fromText("Welcome to Ultima Underworld!\r\nThis file tells you how to start.", false).contains("Ultima Underworld"));
		assertTrue(org.dbgl.util.TextTitles.fromText("x\r\ny\r\nz\r\nDOOM v1.9 - the shareware version", false).contains("DOOM"));
		assertTrue(org.dbgl.util.TextTitles.fromText("x\r\ny\r\nz\r\nMaster of Orion (c) 1993 MicroProse", false).contains("Master of Orion"));
		assertEquals("Commander Keen 4", org.dbgl.util.TextTitles.fromText("Commander Keen 4 v1.4\r\nThe galaxy awaits", true).get(0));
		assertTrue(org.dbgl.util.TextTitles.fromText("Please insert the disk into your drive and type INSTALL", false).isEmpty());
		File dir = Files.createTempDirectory("dbgl-texttitles-").toFile();
		File game = new File(dir, "WC2");
		game.mkdirs();
		Files.writeString(game.toPath().resolve("README.TXT"), "\r\n      WING COMMANDER II\r\n\r\nPlease read this file first.\r\n");
		Files.writeString(game.toPath().resolve("LICENSE.TXT"), "Welcome to Something Else\r\n");
		Files.write(game.toPath().resolve("WC2.EXE"), program(false));
		// a CD image with its volume label
		byte[] iso = new byte[50 * 2048];
		System.arraycopy(new byte[] {1, 'C', 'D', '0', '0', '1'}, 0, iso, 16 * 2048, 6);
		byte[] label = String.format("%-32s", "MONKEY_ISLAND_2").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		System.arraycopy(label, 0, iso, 16 * 2048 + 40, 32);
		Files.write(game.toPath().resolve("CD.ISO"), iso);
		List<String[]> c = org.dbgl.util.TextTitles.candidates(game);
		assertEquals("WING COMMANDER II", c.get(0)[0]);
		assertEquals("README.TXT", c.get(0)[1]);
		assertTrue(c.stream().noneMatch(x -> x[1].equals("LICENSE.TXT"))); // a file not about the game
		assertTrue(c.stream().anyMatch(x -> x[0].equals("MONKEY ISLAND 2") && x[1].equals("CD.ISO")));
		// only a title the database has, of one game: "Wing Commander II" for "Wing Commander II: Vengeance of the Kilrathi"
		java.util.function.BiFunction<String, Integer, org.dbgl.model.WebProfile> w = (title, id) -> {
			org.dbgl.model.WebProfile p = new org.dbgl.model.WebProfile();
			p.setTitle(title);
			p.setGameId(id);
			return p;
		};
		org.dbgl.util.TextTitles.Found f = org.dbgl.util.TextTitles.match(game, t -> t.toUpperCase().contains("WING") ? List.of(w.apply("Wing Commander II: Vengeance of the Kilrathi", 7),
			w.apply("Wing Commander II: Special Operations 1", 8)): List.of());
		assertNull(f); // two games of that name before the colon
		f = org.dbgl.util.TextTitles.match(game, t -> t.toUpperCase().contains("WING") ? List.of(w.apply("Wing Commander II: Vengeance of the Kilrathi", 7)): List.of());
		assertEquals("Wing Commander II: Vengeance of the Kilrathi", f.game().getTitle());
		assertEquals("README.TXT", f.source());
		// the import from a folder: the folders not recognized, with the start program guessed and the title from the text or the folder
		File other = new File(dir, "my_game");
		other.mkdirs();
		Files.write(other.toPath().resolve("PLAY.EXE"), program(false));
		List<org.dbgl.model.repository.GameFilesRepository.GameDirEntry> extra = org.dbgl.gui.dialog.wizard.ImportDirDialog.unrecognizedFolders(dir, List.of(), List.of(), List.of(),
			t -> t.toUpperCase().contains("WING") ? List.of(w.apply("Wing Commander II: Vengeance of the Kilrathi", 7)): List.of());
		assertEquals(2, extra.size());
		assertEquals("My Game", extra.get(0).webProfile_.getTitle()); // in the order of the folders
		assertEquals(0, extra.get(0).score_);
		assertEquals(new File(other, "PLAY.EXE").getCanonicalFile(), extra.get(0).getMain().getCanonicalFile()); // Windows: RUNNER~1 or runneradmin
		assertEquals(10, extra.get(1).score_);
		assertEquals("Wing Commander II: Vengeance of the Kilrathi", extra.get(1).webProfile_.getTitle());
		// a folder already recognized (or in one) is not taken twice
		org.dbgl.model.repository.GameFilesRepository.GameDirEntry known = new org.dbgl.model.repository.GameFilesRepository.GameDirEntry(game, java.util.Optional.empty(),
				java.util.Optional.empty(), List.of(), w.apply("WC2", 1), 50, "");
		assertEquals(1, org.dbgl.gui.dialog.wizard.ImportDirDialog.unrecognizedFolders(dir, List.of(known), List.of(), List.of(), null).size());
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	@Test
	public void testPresetPictureVersion() {
		// alpha 53: the heading of the picture presets names the newest DOSBox Staging set up that has them, else 0.83
		java.util.function.BiFunction<String, String, org.dbgl.model.aggregate.DosboxVersion> v = (family, version) -> DosboxVersionFactory.create("t " + version, family, version, false,
			false, false, null, "x", "", "", "x/dosbox.conf");
		String staging = SettingsService.STAGING_DOSBOX_FAM.title_;
		assertEquals("0.83", org.dbgl.util.Presets.newestStaging(List.of()));
		assertEquals("0.83", org.dbgl.util.Presets.newestStaging(List.of(v.apply(staging, "0.82.2"))));
		assertEquals("0.83.1", org.dbgl.util.Presets.newestStaging(List.of(v.apply(staging, "0.83.0"), v.apply(staging, "0.83.1"), v.apply(staging, "0.82.2"),
			v.apply(SettingsService.DOSBOX_X_DOSBOX_FAM.title_, "2026.10.01"))));
	}

	@Test
	public void testRunIssues() {
		// alpha 53: lines as DOSBox-X 2026.10.01 writes them (EarthSiege II, TEST19)
		List<org.dbgl.util.RunIssues.Issue> found = org.dbgl.util.RunIssues.scan(List.of("LOG: SETUP: mkdir_recursive failed: Permission denied",
			"LOG: \"\" is not a valid value for variable: enable supermegazeux tweakmode.", "It might now be reset to the default value: ",
			"LOG: \"\" is not a valid value for variable: enable supermegazeux tweakmode.", "LOG: WinPcap has to be installed for the NE2000 to work.",
			"LOG: ETHERNET: NE2000 Ethernet emulation backend selected: ethnet"));
		assertEquals(3, found.size());
		assertEquals("runmkdir", found.get(0).key());
		assertEquals(List.of("", "enable supermegazeux tweakmode"), found.get(1).args());
		assertEquals("runpcap", found.get(2).key());
		// with -savedir and that folder there: the mkdir message is DOSBox-X itself, not told
		String saveDir = System.getProperty("java.io.tmpdir");
		assertEquals(2, org.dbgl.util.RunIssues.withoutSaveDirNoise(found, List.of("dosbox-x.exe", "-conf", "x.conf", "-savedir", saveDir)).size());
		assertEquals(3, org.dbgl.util.RunIssues.withoutSaveDirNoise(found, List.of("dosbox-x.exe", "-conf", "x.conf")).size());
		assertTrue(org.dbgl.util.RunIssues.scan(List.of("LOG: MT32: Found ROM pair in ./: CM32L_CONTROL.ROM and CM32L_PCM.ROM")).isEmpty());
		// where the value is: an empty one in the configuration of the DOSBox version (TEST22, dosbox-x.conf), not elsewhere
		try {
			java.io.File conf = java.io.File.createTempFile("dbgl-badvalue", ".conf");
			conf.deleteOnExit();
			java.nio.file.Files.writeString(conf.toPath(), "[video]\r\nenable supermegazeux tweakmode = \r\nvmemsize = 4\r\n");
			assertTrue(org.dbgl.util.ProfileChecks.hasLine(conf, "enable supermegazeux tweakmode", ""));
			assertFalse(org.dbgl.util.ProfileChecks.hasLine(conf, "enable supermegazeux tweakmode", "true"));
			assertFalse(org.dbgl.util.ProfileChecks.hasLine(conf, "vmemsize", ""));
			assertFalse(org.dbgl.util.ProfileChecks.hasLine(null, "vmemsize", "4"));
		} catch (java.io.IOException e) {
			fail(e.toString());
		}
		// the notes that can be switched off in the settings (DOSBox), off at first
		org.dbgl.service.SettingsService settings = org.dbgl.service.SettingsService.getInstance();
		boolean before = settings.getBooleanValue("dosbox", "hideshortsessions");
		settings.setBooleanValue("dosbox", "hideshortsessions", true);
		assertTrue(org.dbgl.util.ProfileFindings.hidden("shortsessions"));
		settings.setBooleanValue("dosbox", "hideshortsessions", false);
		assertFalse(org.dbgl.util.ProfileFindings.hidden("shortsessions"));
		settings.setBooleanValue("dosbox", "hideshortsessions", before);
		assertFalse(org.dbgl.util.ProfileFindings.hidden("runbadvalue"));
		// the ROMs of the MT-32 not found: DOSBox Staging 0.83.1 names the folders it looked in, DOSBox-X does not
		List<org.dbgl.util.RunIssues.Issue> staging = org.dbgl.util.RunIssues.scan(List.of("2026-11-24 20:40:20.491 | MPU-401: Running in intelligent mode on ports 330h and 331h",
			"2026-11-24 20:40:20.491 | MT32: Failed to find ROMs for model auto in:", "2026-11-24 20:40:20.491 | MT32:  |- D:\\Games\\DOSBox-Staging\\mt32-roms",
			"2026-11-24 20:40:20.491 | MT32:  `- C:\\mt32-rom-data\\", "2026-11-24 20:40:20.491 | MIDI: Error opening MIDI device 'mt32'; using 'port'"));
		assertEquals(1, staging.size());
		assertEquals("runmt32", staging.get(0).key());
		assertEquals("D:\\Games\\DOSBox-Staging\\mt32-roms, C:\\mt32-rom-data\\", staging.get(0).args().get(0));
		List<org.dbgl.util.RunIssues.Issue> x = org.dbgl.util.RunIssues.scan(List.of("LOG: MT32: failed to locate ROMs.", "LOG: MT32 emulation requires the PCM and CONTROL ROM files."));
		assertEquals(List.of(new org.dbgl.util.RunIssues.Issue("runmt32", List.of(""))), x);
	}

	@Test
	public void testTitledEntityTidy() {
		// alpha 53: MobyGames gives names with no-break spaces; they are kept with normal ones and found as the same entry
		assertEquals("Accolade, Inc.", org.dbgl.model.repository.TitledEntityRepository.tidy("Accolade,\u00a0Inc."));
		assertEquals("Core Design Ltd.", org.dbgl.model.repository.TitledEntityRepository.tidy("  Core\u00a0\u00a0Design  Ltd. "));
		assertTrue(org.dbgl.model.repository.TitledEntityRepository.same("Role-playing (RPG)", "Role-Playing (RPG)"));
		assertTrue(org.dbgl.model.repository.TitledEntityRepository.same("ORIGIN\u00a0Systems,\u00a0Inc.", "ORIGIN Systems, Inc."));
		assertFalse(org.dbgl.model.repository.TitledEntityRepository.same("Sierra On-Line", "Sierra On-Line, Inc."));
		assertNull(org.dbgl.model.repository.TitledEntityRepository.tidy(null));
		// similar, not the same: offered by hand after the cleanup
		java.util.function.BinaryOperator<String> k = org.dbgl.model.repository.TitledEntityRepository::similarKey;
		assertEquals(k.apply("DEVELOPERS", "Sierra On-Line, Inc."), k.apply("DEVELOPERS", "Sierra Online"));
		assertEquals(k.apply("PUBLISHERS", "Logic Factory, Inc., The"), k.apply("PUBLISHERS", "The Logic Factory"));
		assertEquals(k.apply("PUBLISHERS", "Max Design GmbH & Co."), k.apply("PUBLISHERS", "Max Design"));
		assertFalse(k.apply("DEVELOPERS", "Impressions").equals(k.apply("DEVELOPERS", "Impressions Games")));
		assertEquals(k.apply("GENRES", "Simulation, Strategy/Tactics"), k.apply("GENRES", "Strategy, Simulation"));
		assertEquals(k.apply("GENRES", "Role-Playing (RPG), Add-on"), k.apply("GENRES", "Role-playing (RPG), DLC / Add-on"));
		assertFalse(k.apply("GENRES", "Action").equals(k.apply("GENRES", "Action, Adventure")));
	}

	@Test
	public void testWin9x() throws Exception {
		// alpha 53: Windows 95 in a hard disk image (MBR, FAT16 at sector 63, long names), see data/make_win95_hdd.py
		File img = unzipToTemp("win95-hdd.img.gz");
		org.dbgl.util.Win9x.Report r = org.dbgl.util.Win9x.read(img);
		assertNotNull(r);
		java.util.Map<String, org.dbgl.util.Win9x.Shortcut> byName = new java.util.HashMap<>();
		for (org.dbgl.util.Win9x.Shortcut s: r.shortcuts())
			byName.put(s.name(), s);
		assertEquals(6, r.shortcuts().size());
		assertEquals("C:\\SIERRA\\ES2\\ES2.EXE", byName.get("EarthSiege 2").target());
		assertEquals("Startmenü\\Programme", byName.get("EarthSiege 2").folder());
		assertEquals("D:\\SETUP.EXE", byName.get("CD Game").target());
		assertTrue(byName.get("Tool").startup());
		assertFalse(byName.get("EarthSiege 2").startup());
		assertEquals(List.of("C:\\TOOLS\\CLOCK.EXE"), r.run());
		List<org.dbgl.util.ProfileChecks.Problem> found = new java.util.ArrayList<>();
		org.dbgl.util.Win9x.check(r, found);
		java.util.Map<String, String> byKey = new java.util.HashMap<>();
		for (org.dbgl.util.ProfileChecks.Problem p: found)
			byKey.put(p.key(), String.valueOf(p.args()[0]));
		// the programs of Windows itself (Editor, Zubehör) and documents (Readme) are left out; D: (the CD) counts as there
		assertEquals(java.util.Set.of("win9xstartup", "win9xprograms", "win9xgone"), byKey.keySet());
		assertTrue(byKey.get("win9xprograms"), byKey.get("win9xprograms").contains("EarthSiege 2 (C:\\SIERRA\\ES2\\ES2.EXE)"));
		assertTrue(byKey.get("win9xprograms").contains("CD Game"));
		assertFalse(byKey.get("win9xprograms").contains("Editor"));
		assertFalse(byKey.get("win9xprograms").contains("Readme"));
		assertTrue(byKey.get("win9xgone").contains("Gone Game (C:\\GAMES\\GONE.EXE)"));
		assertTrue(byKey.get("win9xstartup").contains("Tool"));
		assertTrue(byKey.get("win9xstartup").contains("CLOCK.EXE"));
		img.delete();

		// no Windows 9x (a DOS hard disk image, no VMM32.VXD), no FAT, no shortcut
		File hdi = unzipToTemp("pc98-512.hdi.gz");
		assertNull(org.dbgl.util.Win9x.read(hdi));
		hdi.delete();
		assertNull(org.dbgl.util.Win9x.linkTarget(new byte[10]));
		assertNull(org.dbgl.util.Win9x.linkTarget("MZ not a shortcut, long enough to be read as one, but no header of 0x4C at its start......".getBytes()));
	}

	@Test
	public void testWin3x() throws Exception {
		File root = Files.createTempDirectory("dbgl-win3-").toFile();
		try {
			File win = new File(root, "WINDOWS"), game = new File(root, "GAME");
			win.mkdirs();
			game.mkdirs();
			Files.write(new File(win, "WIN.COM").toPath(), "MZ dos program".getBytes(StandardCharsets.ISO_8859_1));
			Files.write(new File(game, "GAME.EXE").toPath(), "MZ".getBytes(StandardCharsets.ISO_8859_1));
			Files.writeString(new File(win, "system.ini").toPath(), "[boot]\r\ndisplay.drv=s3vsnd.drv\r\nshell=progman.exe\r\n[drivers]\r\nWave=sb16snd.drv\r\n[sndblst.drv]\r\nPort=220\r\nInt=5\r\nDmaChannel=1\r\nHDmaChannel=5\r\n",
				StandardCharsets.ISO_8859_1);
			Files.writeString(new File(win, "WIN.INI").toPath(), "[windows]\r\nload=\r\nrun=C:\\game\\game.exe\r\n", StandardCharsets.ISO_8859_1);
			org.dbgl.util.Win3x.Install in = org.dbgl.util.Win3x.of(new File(win, "WIN.COM"));
			assertNotNull(in);
			assertNull(org.dbgl.util.Win3x.of(new File(game, "GAME.EXE"))); // no start of Windows
			assertEquals(List.of("C:\\game\\game.exe"), org.dbgl.util.Win3x.started(in, ""));
			assertEquals(List.of("C:\\GAME\\GAME.EXE", "C:\\game\\game.exe"), org.dbgl.util.Win3x.started(in, "C:\\GAME\\GAME.EXE /s"));
			assertEquals(List.of("C:\\game\\game.exe"), org.dbgl.util.Win3x.started(in, "/3")); // a switch of WIN.COM, no program
			List<org.dbgl.model.conf.mount.Mount> mounts = List.of(org.dbgl.model.factory.MountFactory.create("mount C \"" + root.getPath() + "\""));
			assertEquals(new File(game, "GAME.EXE").getCanonicalFile(), org.dbgl.util.Win3x.resolve("C:\\game\\game", win, mounts).getCanonicalFile());
			assertNull(org.dbgl.util.Win3x.resolve("D:\\GAME\\GAME.EXE", win, mounts));
			assertEquals(java.util.Map.of("sbbase", "220", "irq", "5", "dma", "1", "hdma", "5"), org.dbgl.util.Win3x.sound(in));

			Configuration applies = new Configuration();
			applies.setValue("dosbox", "machine", "svga_et4000");
			applies.setValue("sblaster", "sbtype", "sb16");
			applies.setValue("sblaster", "sbbase", "220");
			applies.setValue("sblaster", "irq", "7");
			applies.setValue("sblaster", "dma", "1");
			applies.setValue("sblaster", "hdma", "5");
			List<org.dbgl.util.ProfileChecks.Problem> found = new java.util.ArrayList<>();
			org.dbgl.util.Win3x.check(in, "", applies, mounts, found);
			assertEquals(List.of("win3start", "win3sound", "win3display"), found.stream().map(org.dbgl.util.ProfileChecks.Problem::key).toList());
			org.junit.Assert.assertArrayEquals(new Object[] {"[sblaster] irq", "5", "7"}, found.get(1).args());
			applies.setValue("dosbox", "machine", "svga_s3");
			applies.setValue("sblaster", "irq", "5");
			found.clear();
			org.dbgl.util.Win3x.check(in, "C:\\NOTHERE.EXE", applies, mounts, found);
			assertEquals(List.of("win3start", "win3startmissing"), found.stream().map(org.dbgl.util.ProfileChecks.Problem::key).toList());
			// started by own lines of the autoexec, as many profiles do it ("C:", "cd \\WINDOWS", "win")
			assertEquals(win.getCanonicalFile(), org.dbgl.util.Win3x.fromAutoexec(List.of("@echo off", "C:", "cd \\WINDOWS", "SET TEMP=C:\\TEMP", "win"), mounts, null).install().dir().getCanonicalFile());
			assertEquals(win.getCanonicalFile(), org.dbgl.util.Win3x.fromAutoexec(List.of("cls", "win /3"), mounts, win).install().dir().getCanonicalFile()); // where DBGL left it
			assertEquals("/3", org.dbgl.util.Win3x.fromAutoexec(List.of("C:\\WINDOWS\\WIN.COM /3"), mounts, null).params());
			assertNull(org.dbgl.util.Win3x.fromAutoexec(List.of("C:", "cd \\GAME", "win"), mounts, null)); // no Windows there
			assertNull(org.dbgl.util.Win3x.fromAutoexec(List.of("winmine"), mounts, win));

			// a profile for a program of Windows: the line that starts Windows starts it right away
			assertEquals("[sdl]\r\nwin=x\r\n[autoexec]\r\nC:\r\ncd \\WINDOWS\r\n@win C:\\GAME\\GAME.EXE\r\nexit", org.dbgl.util.Win3x.withProgram(
				"[sdl]\r\nwin=x\r\n[autoexec]\r\nC:\r\ncd \\WINDOWS\r\n@win\r\nexit", "C:\\GAME\\GAME.EXE"));
			assertEquals("[autoexec]\nC:\\WINDOWS\\WIN.COM C:\\GAME\\GAME.EXE\n", org.dbgl.util.Win3x.withProgram("[autoexec]\nC:\\WINDOWS\\WIN.COM /3\n", "C:\\GAME\\GAME.EXE"));
			assertNull(org.dbgl.util.Win3x.withProgram("[autoexec]\nwinmine\n", "C:\\GAME\\GAME.EXE"));

			// the groups of the Program Manager: a group file of Windows 3.1 ("PMCC") with two items
			Files.writeString(new File(win, "PROGMAN.INI").toPath(), "[Groups]\r\nGroup1=C:\\WINDOWS\\GAMES.GRP\r\n;Group2=C:\\WINDOWS\\OLD.GRP\r\n", StandardCharsets.ISO_8859_1);
			Files.write(new File(win, "GAMES.GRP").toPath(), group("Games", new String[][] {{"My Game", "C:\\GAME\\GAME.EXE /x"}, {"Clock", "CLOCK.EXE"}, {"Gone", "C:\\OLD\\OLD.EXE"}}));
			List<org.dbgl.util.Win3x.GroupItem> items = org.dbgl.util.Win3x.groups(in, mounts);
			assertEquals(3, items.size());
			assertEquals("Games", items.get(0).group());
			assertEquals("C:\\GAME\\GAME.EXE", items.get(0).program());
			found.clear();
			applies.setValue("sblaster", "sbtype", "sb16");
			org.dbgl.util.Win3x.check(in, "", applies, mounts, found);
			assertEquals(List.of("win3start", "win3programs", "win3itemsgone"), found.stream().map(org.dbgl.util.ProfileChecks.Problem::key).toList());
			assertEquals("My Game (C:\\GAME\\GAME.EXE)", found.get(1).args()[0]);
			assertEquals("Gone (C:\\OLD\\OLD.EXE)", found.get(2).args()[0]);

			applies.setValue("sblaster", "sbtype", "none");
			found.clear();
			org.dbgl.util.Win3x.check(in, "", applies, mounts, found);
			org.junit.Assert.assertArrayEquals(new Object[] {"[sblaster] sbtype", "sb16", "none"}, found.stream().filter(x -> x.key().equals("win3nosound")).findFirst().get().args());
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(root);
		}
	}

	@Test
	public void testFitIntoColumn() throws Exception {
		StringBuilder pubs = new StringBuilder();
		for (int i = 1; pubs.length() < 400; i++)
			pubs.append(i > 1 ? ", " : "").append("Publisher Number ").append(i).append(" Inc.");
		String fit = org.dbgl.model.repository.TitledEntityRepository.fit(pubs.toString());
		assertTrue(fit.length() <= 256);
		assertTrue(fit, fit.endsWith(" Inc....") && pubs.toString().startsWith(fit.substring(0, fit.length() - 3)));
		assertEquals("id Software", org.dbgl.model.repository.TitledEntityRepository.fit("id Software"));
		assertNull(org.dbgl.model.repository.TitledEntityRepository.fit(null));
		assertEquals(256, org.dbgl.model.repository.TitledEntityRepository.fit("x".repeat(300)).length());
		// alpha 53, as in beta 18 (remax): paths and parameters cut at the width of their column, without "..."
		assertEquals("C:\\" + "d".repeat(253), org.dbgl.model.repository.TitledEntityRepository.cut("C:\\" + "d".repeat(300)));
		assertEquals("SETUP.EXE", org.dbgl.model.repository.TitledEntityRepository.cut("SETUP.EXE"));
		assertNull(org.dbgl.model.repository.TitledEntityRepository.cut(null));
		// the code: every path and parameter of profiles, templates and DOSBox versions goes through it, links and filters do not
		String repo = Files.readString(new File(DATA_DIR).toPath().resolve("../../model/repository/ProfileRepository.java").normalize());
		assertTrue(repo.contains("cut(profile.getSetupString())") && repo.contains("cut(profile.getAltExeParams()[i])") && repo.contains("cut(cmd.getCwd().getPath())"));
		assertFalse(repo.contains("cut(profile.getLinks()[i].getDestination())")); // links hold 2048 since database 0.103
		String dbv = Files.readString(new File(DATA_DIR).toPath().resolve("../../model/repository/DosboxVersionRepository.java").normalize());
		assertEquals(12, dbv.split("TitledEntityRepository.cut\\(", -1).length - 1);
		assertFalse(Files.readString(new File(DATA_DIR).toPath().resolve("../../model/repository/FilterRepository.java").normalize()).contains("cut(filter.getFilter())")); // LONGVARCHAR
		// a network share whose computer has "_" in its name: commons-io gave null, DBGL showed "null" (reported on Vogons)
		assertEquals("\\\\nas_box\\games\\x.iso", org.dbgl.util.FilesUtils.concat("C:\\dosroot", "\\\\nas_box\\games\\x.iso"));
		// as before (commons-io writes it with the separator of the system: \\nas\games on Windows, //nas/games elsewhere)
		assertEquals("//nas/games/x.iso", org.dbgl.util.FilesUtils.concat("C:\\dosroot", "\\\\nas\\games\\x.iso").replace('\\', '/'));
	}

	/** alpha 52: the changelog in the update window from the version running on; the exported table; the TV background readable */
	@Test
	public void testChangelogSinceAndListTable() {
		String log = "DBGL Extended Edition - changes\n================\n\nIntro.\n\n0.101 alpha 53 (2026-12-01)\n---------------------------\nNew\n- c\n\n"
				+ "0.101 alpha 52 (2026-11-21)\n---------------------------\nNew\n- b\n\n0.101 alpha 51 (2026-11-18)\n---------------------------\nNew\n- a\n";
		String since = org.dbgl.util.EditionUpdateChecker.newerPart(log, "0.101alpha51");
		assertTrue(since, since.contains("alpha 53") && since.contains("alpha 52") && !since.contains("alpha 51") && since.startsWith("DBGL Extended Edition"));
		assertFalse(org.dbgl.util.EditionUpdateChecker.newerPart(log, "0.101alpha52").contains("alpha 52"));
		assertNull(org.dbgl.util.EditionUpdateChecker.newerPart(log, "0.101alpha53"));
		assertNull(org.dbgl.util.EditionUpdateChecker.changelogUrl("v0.101-alpha52/../x", "de"));
		assertTrue(org.dbgl.util.EditionUpdateChecker.changelogUrl("v0.101-alpha52", "de").endsWith("/v0.101-alpha52/CHANGELOG-de.txt"));
		assertTrue(org.dbgl.util.EditionUpdateChecker.changelogUrl("v0.101-alpha52", "fr").endsWith("/CHANGELOG-en.txt"));

		java.util.List<org.dbgl.gui.dialog.DocumentDialog.Block> blocks = org.dbgl.gui.dialog.DocumentDialog.blocks(log);
		assertEquals(2, org.dbgl.gui.dialog.DocumentDialog.firstSections(blocks, 2).stream().filter(b -> b.kind() == org.dbgl.gui.dialog.DocumentDialog.Kind.SECTION).count());
		assertEquals(blocks.size(), org.dbgl.gui.dialog.DocumentDialog.firstSections(blocks, 3).size());

		String csv = org.dbgl.util.ListTable.csv(java.util.Locale.GERMANY, java.util.List.of("Titel", "Setup"), java.util.List.of(java.util.List.of("Monkey; Island \"2\"", "Ja")));
		assertEquals("﻿Titel;Setup\r\n\"Monkey; Island \"\"2\"\"\";Ja\r\n", csv);
		assertTrue(org.dbgl.util.ListTable.csv(java.util.Locale.US, java.util.List.of("a", "b"), java.util.List.of()).startsWith("﻿a,b"));
		String html = org.dbgl.util.ListTable.html("DBGL", "today", java.util.List.of("Title"), java.util.List.of(java.util.List.of("<b>&</b>")));
		assertTrue(html.contains("<td>&lt;b&gt;&amp;&lt;/b&gt;</td>"));
		String txt = org.dbgl.util.ListTable.txt("DBGL", "today", java.util.List.of("Title", "Year"), java.util.List.of(java.util.List.of("Doom", "1993"), java.util.List.of("x".repeat(80), "")));
		String[] lines = txt.split("\r\n");
		assertEquals("Title" + " ".repeat(55) + "  Year", lines[3]);
		assertEquals("-".repeat(60) + "  ----", lines[4]);
		assertEquals("Doom" + " ".repeat(56) + "  1993", lines[5]);
		assertEquals("x".repeat(57) + "...", lines[6]); // cut, no spaces at the end

		org.eclipse.swt.graphics.RGB white = org.dbgl.gui.tv.TvMode.readable(new org.eclipse.swt.graphics.RGB(255, 255, 255));
		assertTrue(white.red <= 72 && white.red == white.green);
		assertEquals(new org.eclipse.swt.graphics.RGB(18, 18, 20), org.dbgl.gui.tv.TvMode.readable(new org.eclipse.swt.graphics.RGB(18, 18, 20)));
		assertTrue(org.dbgl.gui.dialog.ScummvmDialog.naturalCompare("Indy 2", "Indy 10") < 0);
		assertTrue(org.dbgl.gui.dialog.ScummvmDialog.naturalCompare("b", "A") > 0);
	}

	/** alpha 52: Java along with the update: what Adoptium offers, the version of a Java folder, unpacking the archive for Linux safely */
	@Test
	public void testJavaRuntime() throws Exception {
		String json = "[{\"binary\":{\"architecture\":\"x64\",\"image_type\":\"jre\",\"os\":\"windows\",\"package\":{\"checksum\":\"" + "ab".repeat(32)
				+ "\",\"link\":\"https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%2B10/OpenJDK21U-jre_x64_windows_hotspot_21.0.9_10.zip\","
				+ "\"name\":\"OpenJDK21U-jre_x64_windows_hotspot_21.0.9_10.zip\",\"size\":48123456}},\"release_name\":\"jdk-21.0.9+10\",\"vendor\":\"eclipse\","
				+ "\"version\":{\"major\":21,\"openjdk_version\":\"21.0.9+10-LTS\",\"semver\":\"21.0.9+10.0.LTS\"}}]";
		org.dbgl.util.JavaRuntime.Offer offer = org.dbgl.util.JavaRuntime.parse(json);
		assertEquals("21.0.9+10-LTS", offer.version());
		assertEquals("ab".repeat(32), offer.sha256());
		assertEquals(48123456L, offer.size());
		assertTrue(offer.name().endsWith(".zip"));
		try {
			org.dbgl.util.JavaRuntime.parse(json.replace("https://", "http://")); // only over https
			fail();
		} catch (IOException e) {
			// fine
		}
		String other = "[{\"binaries\":[{\"package\":{\"checksum\":\"" + "cd".repeat(32) + "\",\"link\":\"https://example.org/OpenJDK21U-jre_x64_linux_hotspot_21.0.9_10.tar.gz\",\"name\":\"OpenJDK21U-jre_x64_linux_hotspot_21.0.9_10.tar.gz\",\"size\":1}}],\"version_data\":{\"openjdk_version\":\"21.0.9+10-LTS\"}}]";
		assertEquals("21.0.9+10-LTS", org.dbgl.util.JavaRuntime.parse(other).version()); // the shape of the other lists of the API
		assertTrue(org.dbgl.util.JavaRuntime.newer("21.0.9+10-LTS", "21.0.5+11-LTS"));
		assertFalse(org.dbgl.util.JavaRuntime.newer("21.0.9+10-LTS", "21.0.9+10-LTS"));
		assertFalse(org.dbgl.util.JavaRuntime.newer("21.0.5+11-LTS", "21.0.9+10-LTS"));
		assertTrue(org.dbgl.util.JavaRuntime.newer("21.0.9+10-LTS", null));

		File dir = Files.createTempDirectory("dbgl-jre-").toFile();
		java.util.List<String> names = org.dbgl.util.JavaRuntime.untar(new File(DATA_DIR, "jre-test.tar.gz"), dir);
		if (org.dbgl.util.SystemUtils.IS_WINDOWS) // the test archive is the Java for Linux (bin/java); on Windows DBGL looks for bin\java.exe
			Files.write(new File(dir, "jdk-21.0.9+10-jre/bin/java.exe").toPath(), new byte[] {0});
		assertTrue(names.toString(), names.contains("jdk-21.0.9+10-jre/lib/" + "a".repeat(120) + ".txt")); // a name longer than 100 characters
		File home = org.dbgl.util.JavaRuntime.homeIn(dir);
		assertEquals(new File(dir, "jdk-21.0.9+10-jre").getCanonicalFile(), home.getCanonicalFile());
		assertEquals("21.0.9+10-LTS", org.dbgl.util.JavaRuntime.versionIn(home));
		assertNull(org.dbgl.util.JavaRuntime.versionIn(dir));
		if (!org.dbgl.util.SystemUtils.IS_WINDOWS) {
			assertTrue(new File(home, "bin/java").canExecute());
			assertTrue(Files.isSymbolicLink(new File(home, "legal/release-link").toPath()));
		}
		assertFalse(Files.exists(new File(home, "legal/outside").toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)); // a link out of the folder: left out
		File evil = Files.createTempDirectory("dbgl-jre-evil-").toFile();
		try {
			org.dbgl.util.JavaRuntime.untar(new File(DATA_DIR, "jre-evil.tar.gz"), new File(evil, "in"));
			fail();
		} catch (IOException e) {
			assertFalse(new File(evil, "evil.txt").exists());
		}
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
		org.apache.commons.io.FileUtils.deleteDirectory(evil);
	}

	/** a DOS program (only the MZ header) or a Windows program (its stub says so, then the PE header) for the tests */
	private static byte[] program(boolean windows) {
		byte[] b = new byte[2048];
		b[0] = 'M';
		b[1] = 'Z';
		b[2] = 0; // last page full
		b[4] = 4; // 4 pages of 512 bytes
		b[8] = 4; // header: 4 paragraphs
		if (windows) {
			b[0x3C] = (byte)0x80;
			byte[] stub = "This program cannot be run in DOS mode.".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
			System.arraycopy(stub, 0, b, 0x40, stub.length);
			b[0x80] = 'P';
			b[0x81] = 'E';
		}
		return b;
	}

	/** alpha 52: the game folders no profile uses: the start file guessed, the setup found, linked, Windows and GOG folders left out */
	@Test
	public void testUnlinkedGames() throws Exception {
		File root = Files.createTempDirectory("dbgl-unlinked-").toFile();
		new File(root, "Doom").mkdirs();
		Files.write(new File(root, "Doom/SETUP.EXE").toPath(), program(false));
		Files.write(new File(root, "Doom/DOS4GW.EXE").toPath(), program(false));
		Files.write(new File(root, "Doom/DOOM.EXE").toPath(), program(false));
		Files.write(new File(root, "Doom/dosbox.conf").toPath(), "[cpu]\r\ncycles=fixed 20000\r\n[autoexec]\r\nmount c .\r\nc:\r\ndoom\r\n".getBytes());
		new File(root, "keen_4/KEEN4").mkdirs();
		Files.write(new File(root, "keen_4/KEEN4/KEEN4E.EXE").toPath(), program(false));
		Files.write(new File(root, "keen_4/KEEN4/UNINST.EXE").toPath(), program(false));
		new File(root, "WinGame").mkdirs();
		Files.write(new File(root, "WinGame/GAME.EXE").toPath(), program(true));
		new File(root, "GogGame/DOSBOX").mkdirs();
		Files.write(new File(root, "GogGame/goggame-1207658.info").toPath(), "{}".getBytes());
		Files.write(new File(root, "GogGame/GAME.EXE").toPath(), program(false));
		new File(root, "Linked").mkdirs();
		Files.write(new File(root, "Linked/GAME.EXE").toPath(), program(false));
		new File(root, "Docs").mkdirs();
		Files.write(new File(root, "Docs/readme.txt").toPath(), "x".getBytes());
		// a profile with its program in Linked, one that mounts the whole folder as C: (that does not count for the folders in it)
		java.util.List<File> used = java.util.List.of(new File(root, "Linked"), root);
		org.dbgl.util.UnlinkedGames.Result r = org.dbgl.util.UnlinkedGames.find(root, used, null, () -> false);
		assertEquals(r.games().stream().map(g -> g.dir().getName()).toList().toString(), java.util.List.of("Doom", "keen_4"), r.games().stream().map(g -> g.dir().getName()).toList());
		assertEquals(1, r.linked());
		assertEquals(1, r.windows());
		assertEquals(1, r.store());
		assertEquals(1, r.empty());
		org.dbgl.util.UnlinkedGames.Game doom = r.games().get(0), keen = r.games().get(1);
		assertEquals("DOOM.EXE", doom.main().getName());
		assertEquals("SETUP.EXE", doom.setup().getName());
		assertEquals("KEEN4E.EXE", keen.main().getName());
		assertNull(keen.setup());
		assertEquals("Keen 4", org.dbgl.util.UnlinkedGames.titleOf(keen.dir()));
		assertEquals("WinGame", org.dbgl.util.UnlinkedGames.titleOf(new File(root, "WinGame")));

		File work = Files.createTempDirectory("dbgl-unlinkedwork-").toFile();
		org.dbgl.util.StoreGameImport.Found f = org.dbgl.util.UnlinkedGames.toFound(doom, work, "Ordner");
		File dosboxConfFile = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
		org.dbgl.model.aggregate.DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null,
			"staging083", "", "", dosboxConfFile.getAbsolutePath());
		v083.resetAndLoadConfiguration();
		org.dbgl.model.aggregate.Profile p = org.dbgl.util.StoreGameImport.toProfile(f, v083, GenericTextService.getInstance());
		assertEquals("Doom", p.getTitle());
		assertEquals(new File(root, "Doom/DOOM.EXE").getCanonicalFile(), new File(p.getConfiguration().getAutoexec().getMain()).getCanonicalFile());
		assertEquals(new File(root, "Doom/SETUP.EXE").getCanonicalFile(), new File(p.getSetupString()).getCanonicalFile());
		assertEquals("20000", p.getConfiguration().getValue("cpu", "cpu_cycles")); // its own dosbox.conf, for Staging
		assertTrue(p.getNotes(), p.getNotes().endsWith("Ordner: " + doom.dir().getPath()));
		org.apache.commons.io.FileUtils.deleteDirectory(root);
		org.apache.commons.io.FileUtils.deleteDirectory(work);
	}

	private static File canonical(File f) {
		try {
			return f.getCanonicalFile();
		} catch (IOException e) {
			return f.getAbsoluteFile();
		}
	}

	/** alpha 52: the search for music files: SoundFonts and Ultrasound patches found, a Sound Canvas folder without its ROMs not, system folders at the top left out, copies never overwrite */
	@Test
	public void testSoundScan() throws IOException {
		File root = Files.createTempDirectory("dbgl-scan-").toFile();
		new File(root, "Games/Doom/sf").mkdirs();
		Files.write(new File(root, "Games/Doom/sf/Arachno.sf2").toPath(), new byte[] {1, 2});
		Files.write(new File(root, "Games/Doom/sf/Other.SF3").toPath(), new byte[] {3});
		new File(root, "ULTRASND/MIDI").mkdirs();
		Files.write(new File(root, "ULTRASND/MIDI/acpiano.pat").toPath(), new byte[] {1});
		Files.write(new File(root, "ULTRASND/MIDI/britepno.pat").toPath(), new byte[] {1});
		Files.write(new File(root, "ULTRASND/ULTRASND.INI").toPath(), "# Ver:     2.1\r\n[Ultrasound]\r\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
		new File(root, "roms/SC-55-v1.00").mkdirs(); // a model folder with nothing in it: no find
		Files.write(new File(root, "roms/SC-55-v1.00/wrong.bin").toPath(), new byte[] {1});
		new File(root, "windows").mkdirs(); // at the top: left out
		Files.write(new File(root, "windows/system.sf2").toPath(), new byte[] {1});
		new File(root, "Games/Doom/windows").mkdirs(); // a game folder of that name: looked into
		Files.write(new File(root, "Games/Doom/windows/game.sf2").toPath(), new byte[] {1});
		new File(root, "$RECYCLE.BIN").mkdirs();
		Files.write(new File(root, "$RECYCLE.BIN/gone.sf2").toPath(), new byte[] {1});
		java.util.List<File> looked = new java.util.ArrayList<>();
		java.util.List<org.dbgl.util.SoundScan.Find> found = org.dbgl.util.SoundScan.scan(java.util.List.of(root), looked::add, () -> false);
		java.util.Map<String, org.dbgl.util.SoundScan.Find> byDir = new java.util.HashMap<>();
		// the folders found are canonical: on Windows the temp folder can come as a short name (RUNNER~1), compared canonically
		java.nio.file.Path rootPath = root.getCanonicalFile().toPath();
		for (org.dbgl.util.SoundScan.Find f: found)
			byDir.put(rootPath.relativize(f.dir().getCanonicalFile().toPath()).toString().replace('\\', '/'), f);
		assertEquals(byDir.keySet().toString(), java.util.Set.of("Games/Doom/sf", "ULTRASND", "Games/Doom/windows"), byDir.keySet());
		assertEquals(org.dbgl.util.SoundScan.Kind.SOUNDFONT, byDir.get("Games/Doom/sf").kind());
		assertEquals(2, byDir.get("Games/Doom/sf").files());
		assertEquals(org.dbgl.util.SoundScan.Kind.ULTRASND, byDir.get("ULTRASND").kind());
		assertEquals(2, byDir.get("ULTRASND").files());
		assertEquals(java.util.List.of("2.1"), byDir.get("ULTRASND").details());
		assertFalse(looked.stream().anyMatch(l -> rootPath.resolve("windows").toFile().equals(canonical(l)))); // canonical, as above
		assertTrue(org.dbgl.util.SoundScan.scan(java.util.List.of(root), null, () -> true).isEmpty()); // stopped at once

		File to = Files.createTempDirectory("dbgl-scanto-").toFile();
		Files.write(new File(to, "Arachno.sf2").toPath(), new byte[] {9}); // there already: kept
		assertEquals(1, org.dbgl.util.SoundScan.copy(byDir.get("Games/Doom/sf"), to));
		org.junit.Assert.assertArrayEquals(new byte[] {9}, Files.readAllBytes(new File(to, "Arachno.sf2").toPath()));
		assertTrue(new File(to, "Other.SF3").isFile());
		assertTrue(new File(root, "Games/Doom/sf/Arachno.sf2").isFile()); // copied, not moved
		File gus = new File(to, "ULTRASND");
		assertEquals(3, org.dbgl.util.SoundScan.copy(byDir.get("ULTRASND"), gus));
		assertTrue(new File(gus, "MIDI/acpiano.pat").isFile());
		org.apache.commons.io.FileUtils.deleteDirectory(root);
		org.apache.commons.io.FileUtils.deleteDirectory(to);
	}

	/** alpha 52: the SoundFonts to choose from: in the folder chosen in DBGL (and one level below), in soundfont_dir, beside DOSBox */
	@Test
	public void testSoundFonts() throws IOException {
		File mine = Files.createTempDirectory("dbgl-sf-").toFile();
		Files.write(new File(mine, "GeneralUser GS.sf2").toPath(), new byte[] {1});
		new File(mine, "more/deeper").mkdirs();
		Files.write(new File(mine, "more/Arachno.SF3").toPath(), new byte[] {1});
		Files.write(new File(mine, "more/deeper/too deep.sf2").toPath(), new byte[] {1});
		Files.write(new File(mine, "readme.txt").toPath(), new byte[] {1});
		File dosbox = Files.createTempDirectory("dbgl-sfdb-").toFile();
		new File(dosbox, "soundfonts").mkdirs();
		Files.write(new File(dosbox, "soundfonts/FluidR3_GM.sf2").toPath(), new byte[] {1});
		File own = new File(dosbox, "sf");
		own.mkdirs();
		Files.write(new File(own, "Roland SC-55.sf2").toPath(), new byte[] {1});
		File exe = new File(dosbox, "dosbox");
		Files.write(exe.toPath(), new byte[] {1});
		org.dbgl.model.aggregate.DosboxVersion v = DosboxVersionFactory.create("Staging", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, dosbox.getPath(), exe.getPath(), "",
			new File(dosbox, "dosbox.conf").getPath());
		SettingsService settings = SettingsService.getInstance();
		String before = settings.hasValue("gui", org.dbgl.util.SoundFonts.SETTING) ? settings.getValue("gui", org.dbgl.util.SoundFonts.SETTING): null;
		try {
			settings.setValue("gui", org.dbgl.util.SoundFonts.SETTING, mine.getPath());
			org.dbgl.util.SoundFonts.Found found = org.dbgl.util.SoundFonts.find(v, "sf"); // relative to DOSBox
			java.util.List<String> names = found.files().stream().map(File::getName).toList();
			assertTrue(names.toString(), names.containsAll(java.util.List.of("GeneralUser GS.sf2", "Arachno.SF3", "FluidR3_GM.sf2", "Roland SC-55.sf2")));
			assertFalse(names.contains("too deep.sf2"));
			assertFalse(names.contains("readme.txt"));
			assertEquals(mine.getAbsoluteFile(), found.folders().get(0));
			assertEquals(own.getAbsoluteFile(), found.folders().get(1));
			assertEquals(new File(dosbox, "soundfonts").getAbsoluteFile(), found.folders().get(2));
		} finally {
			settings.setValue("gui", org.dbgl.util.SoundFonts.SETTING, before == null ? "": before);
		}
	}

	/** alpha 52: the description below the shader field, by the name of the shader, in every language */
	@Test
	public void testShaderInfo() {
		String p = "dialog.template.glshader.info.";
		assertEquals(p + "crtauto", org.dbgl.util.ShaderInfo.keyOf("crt-auto"));
		assertEquals(p + "crtautoarcadesharp", org.dbgl.util.ShaderInfo.keyOf("crt-auto-arcade-sharp"));
		assertEquals(p + "sharp", org.dbgl.util.ShaderInfo.keyOf("interpolation/sharp"));
		assertEquals(p + "nearest", org.dbgl.util.ShaderInfo.keyOf("none"));
		assertEquals(p + "crtcurved", org.dbgl.util.ShaderInfo.keyOf("crt/geom"));
		assertEquals(p + "crtcurved", org.dbgl.util.ShaderInfo.keyOf("crt-lottes.glsl"));
		assertEquals(p + "crt", org.dbgl.util.ShaderInfo.keyOf("crt/easymode-flat"));
		assertEquals(p + "crt", org.dbgl.util.ShaderInfo.keyOf("crt/vga-1080p"));
		assertEquals(p + "xbr", org.dbgl.util.ShaderInfo.keyOf("scaler/xbr-lv3"));
		assertEquals(p + "advmame", org.dbgl.util.ShaderInfo.keyOf("advinterp3x"));
		assertEquals(p + "scanlines", org.dbgl.util.ShaderInfo.keyOf("TV2X"));
		assertEquals(p + "interpolation", org.dbgl.util.ShaderInfo.keyOf("interpolation/catmull-rom"));
		assertNull(org.dbgl.util.ShaderInfo.keyOf("C:\\shaders\\mine.glsl"));
		assertNull(org.dbgl.util.ShaderInfo.keyOf(""));
		for (String kind: new String[] {"nearest", "bilinear", "sharp", "crtauto", "crtautomachine", "crtautoarcade", "crtautoarcadesharp", "scanlines", "rgb", "advmame", "xbr",
				"interpolation", "crtcurved", "crt"})
			for (String lang: new String[] {"", "_de", "_fr", "_es", "_it", "_nl", "_pt_BR", "_da", "_sv", "_fi", "_pl", "_sk", "_sl", "_ru", "_el", "_ar", "_ko", "_zh", "_zh_TW"}) {
				java.util.ResourceBundle b = java.util.ResourceBundle.getBundle("i18n/MessagesBundle", lang.isEmpty() ? java.util.Locale.ROOT: java.util.Locale.forLanguageTag(lang.substring(1).replace('_', '-')));
				assertTrue(kind + lang, b.getString(p + kind).length() > 10);
			}
	}

	/** alpha 48: empty images DOSBox can use at once: formatted, read again by DBGL's own FAT reader, the geometry as said */
	@Test
	public void testDiskImageMaker() throws IOException {
		File dir = Files.createTempDirectory("dbgl-img-").toFile();
		for (org.dbgl.util.DiskImageMaker.Floppy t: org.dbgl.util.DiskImageMaker.Floppy.values()) {
			File f = new File(dir, t.name() + ".img");
			org.dbgl.util.DiskImageMaker.floppy(f, t, "Spiel 1");
			assertEquals(t.kb() * 1024L, f.length());
			org.dbgl.util.fat.FileDisk d = new org.dbgl.util.fat.FileDisk(f);
			assertNotNull(new org.dbgl.util.fat.FatFileSystem(d).getRoot());
			d.close();
			byte[] b = Files.readAllBytes(f.toPath());
			assertEquals("SPIEL 1    ", new String(b, 0x2b, 11, StandardCharsets.US_ASCII));
		}
		for (int mb: new int[] {32, 250, 2047}) {
			File f = new File(dir, "hd" + mb + ".img");
			org.dbgl.util.DiskImageMaker.hardDisk(f, mb, "");
			org.dbgl.util.DiskGeometry.Result g = org.dbgl.util.DiskGeometry.of(f);
			assertNotNull(g);
			assertEquals(org.dbgl.util.DiskImageMaker.geometry(mb).toString(), g.size().toString());
			org.dbgl.util.fat.FileDisk d = new org.dbgl.util.fat.FileDisk(f);
			assertNotNull(new org.dbgl.util.fat.FatFileSystem(d).getRoot());
			d.close();
		}
		try {
			org.dbgl.util.DiskImageMaker.hardDisk(new File(dir, "hd32.img"), 32, "");
			fail("an image there already must not be overwritten");
		} catch (IOException e) {
			// as it should
		}
	}

	/** alpha 48: the picture presets of DOSBox Staging 0.83 as a whole on 0.83, nothing of them on 0.82 */
	@Test
	public void testStaging083PicturePresets() throws IOException {
		java.util.Map<String, org.dbgl.util.Presets.Preset> byKey = new java.util.HashMap<>();
		org.dbgl.util.Presets.BUILT_IN.forEach(p -> byKey.put(p.key(), p));
		org.dbgl.model.aggregate.DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, "s", "", "",
			new File(DATA_DIR, "dosbox-staging-0.83.0.conf").getAbsolutePath());
		v083.resetAndLoadConfiguration();
		org.dbgl.model.aggregate.Profile p = org.dbgl.model.factory.ProfileFactory.create(v083, null);
		assertEquals(10, org.dbgl.util.Presets.apply(p, byKey.get("dialog.preset.monogreen")));
		assertEquals("crt/crt-hyllian:monochrome-hires", p.getConfiguration().getValue("render", "shader"));
		assertEquals("125", p.getConfiguration().getValue("render", "green_gain"));
		assertEquals(1, org.dbgl.util.Presets.apply(p, byKey.get("dialog.preset.fmv")));
		assertEquals("on", p.getConfiguration().getValue("render", "deinterlacing"));
		assertEquals(1, org.dbgl.util.Presets.apply(p, byKey.get("dialog.preset.ega")));
		assertTrue(org.dbgl.util.Presets.apply(p, byKey.get("dialog.preset.win31")) >= 5);
		assertEquals("on", p.getConfiguration().getValue("sdl", "keyboard_capture"));

		org.dbgl.model.aggregate.DosboxVersion v082 = DosboxVersionFactory.create("DOSBox Staging 0.82.2", SettingsService.STAGING_DOSBOX_FAM.title_, "0.82.2", false, true, false, null, "s", "", "",
			new File(DATA_DIR, "dosbox-staging-0.82.2.conf").getAbsolutePath());
		v082.resetAndLoadConfiguration();
		org.dbgl.model.aggregate.Profile old = org.dbgl.model.factory.ProfileFactory.create(v082, null);
		for (String k: new String[] {"dialog.preset.crt", "dialog.preset.monogreen", "dialog.preset.monoamber", "dialog.preset.fmv", "dialog.preset.ega"})
			assertEquals(k, 0, org.dbgl.util.Presets.apply(old, byKey.get(k)));
		assertFalse(old.getConfiguration().hasValue("render", "shader"));
	}

	/** alpha 48: a DFendX game archive (ZIP: .prof and .conf next to VirtualHD), read like D-Fend Reloaded; an unsafe ZIP is stopped */
	@Test
	public void testDFendXArchive() throws Exception {
		File tmp = java.nio.file.Files.createTempDirectory("dfx").toFile();
		try {
			File zip = new File(tmp, "Game X.zip");
			try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip))) {
				z.putNextEntry(new java.util.zip.ZipEntry("Game X.prof"));
				z.write(("[Extra]\r\nExe=.\\VirtualHD\\GAME\\GAME.EXE\r\nNrOfMounts=1\r\n0=.\\VirtualHD;Drive;C;False;;\r\nProfileMode=DOSBox\r\n"
						+ "[ExtraInfo]\r\nGenre=Action\r\nDeveloper=Dev\r\nYear=1993\r\nName=Game X\r\n[dosbox]\r\ncaptures=.\\Capture\\Game X\\\r\n").getBytes("windows-1252"));
				z.putNextEntry(new java.util.zip.ZipEntry("Game X.conf"));
				z.write("[dosbox]\r\nmemsize=16\r\n[autoexec]\r\nmount C \".\\VIRTUA~1\\\"\r\nC:\r\nGAME.EXE\r\n".getBytes("windows-1252"));
				z.putNextEntry(new java.util.zip.ZipEntry("VirtualHD/GAME/GAME.EXE"));
				z.write(1);
			}
			File dir = org.dbgl.model.conf.dfend.DFendXArchive.freeTargetDir(tmp, zip);
			assertEquals("Game X", dir.getName());
			org.dbgl.model.conf.dfend.DFendXArchive.extract(zip, dir);
			assertEquals("Game X (2)", org.dbgl.model.conf.dfend.DFendXArchive.freeTargetDir(tmp, zip).getName());
			java.util.List<File> profs = org.dbgl.model.conf.dfend.DFendXArchive.profFiles(dir);
			assertEquals(1, profs.size());

			org.dbgl.model.conf.dfend.DFendReloadedConfiguration cfg = new org.dbgl.model.conf.dfend.DFendReloadedConfiguration(new File(dir, "-none-"));
			cfg.loadArchive(profs);
			cfg.loadProfile(profs.get(0));
			// the sections of a .prof keep their case (read as a Configuration, they were lower case since alpha 46: no title)
			assertEquals("Game X", cfg.getValue(profs.get(0), "ExtraInfo", "name"));
			assertEquals("Dev", cfg.getValue(profs.get(0), "ExtraInfo", "developer"));
			assertTrue(cfg.hasConf(profs.get(0)));

			String conv = org.dbgl.model.conf.dfend.DFendProfConverter.convert(cfg.getProf(profs.get(0)), dir).conf();
			String conf = org.dbgl.gui.thread.DFendReloadedImportThread.withAutoexecOf(cfg.getConf(profs.get(0)), conv);
			assertTrue(conf, conf.contains("memsize=16"));
			assertFalse(conf, conf.contains("VIRTUA~1"));
			assertTrue(conf, conf.contains("mount C \"" + new File(dir, "VirtualHD").getPath() + "\""));
			assertEquals(1, org.apache.commons.lang3.StringUtils.countMatches(conf, "[autoexec]"));
			assertEquals(new File(new File(dir, "Capture"), "Game X"), org.dbgl.model.conf.dfend.DFendProfConverter.hostPath(dir, ".\\Capture\\Game X\\"));

			File evil = new File(tmp, "evil.zip");
			try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(evil))) {
				z.putNextEntry(new java.util.zip.ZipEntry("../outside.txt"));
				z.write(1);
			}
			try {
				org.dbgl.model.conf.dfend.DFendXArchive.extract(evil, new File(tmp, "evil"));
				fail("unsafe entry not stopped");
			} catch (java.io.IOException e) {
				// expected
			}
			assertFalse(new File(tmp, "outside.txt").exists());
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(tmp);
		}
	}

	/** alpha 48 (wish 11): which files are saved games: patterns (* ? ** and folders), the suggestion */
	@Test
	public void testSavegamePatterns() throws Exception {
		assertTrue(org.dbgl.util.SavegameBackup.glob("*.SAV").matcher("game1.sav").matches());
		assertFalse(org.dbgl.util.SavegameBackup.glob("*.SAV").matcher("saves/game1.sav").matches());
		assertTrue(org.dbgl.util.SavegameBackup.glob("**/*.sav").matcher("saves/game1.sav").matches());
		assertTrue(org.dbgl.util.SavegameBackup.glob("SAVES/").matcher("saves/a/b.dat").matches());
		assertTrue(org.dbgl.util.SavegameBackup.glob("SAVEGAME.?").matcher("savegame.1").matches());
		assertTrue(org.dbgl.util.SavegameBackup.glob("\\SAVES\\*").matcher("saves/x").matches());
		File tmp = Files.createTempDirectory("sg").toFile();
		try {
			long old = System.currentTimeMillis() - 400L * 24 * 3600 * 1000;
			for (String n: new String[] {"GAME.EXE", "DATA.DAT", "SOUND.CFG", "MAP.DAT"}) {
				File f = new File(tmp, n);
				Files.writeString(f.toPath(), n);
				f.setLastModified(old);
			}
			new File(tmp, "SAVES").mkdirs();
			for (int i = 1; i <= 5; i++)
				Files.writeString(new File(tmp, "SAVES/SLOT" + i + ".DAT").toPath(), "s");
			Files.writeString(new File(tmp, "RESUME.GAM").toPath(), "r");
			Files.writeString(new File(tmp, "ASCEND.CFG").toPath(), "c");
			String suggestion = org.dbgl.util.SavegameBackup.suggest(tmp);
			assertEquals("SAVES/**; RESUME.GAM", suggestion);
			java.util.Map<String, File> m = org.dbgl.util.SavegameBackup.matching(tmp, suggestion);
			assertEquals(6, m.size());
			assertTrue(m.containsKey("SAVES/SLOT1.DAT"));
			assertTrue(org.dbgl.util.SavegameBackup.matching(tmp, " ; ").isEmpty());
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(tmp);
		}
	}

	/** alpha 48: the USB stick: the package of Adoptium with its own checksum (not that of the installer), images with their tracks */
	@Test
	public void testPortableJavaAndImages() throws Exception {
		String sumMsi = "a".repeat(64), sumZip = "b".repeat(64);
		String json = "[{\"binary\":{\"image_type\":\"jre\",\"installer\":{\"checksum\":\"" + sumMsi + "\",\"link\":\"https://x/OpenJDK21U-jre_x64_windows_hotspot.msi\"},"
				+ "\"package\":{\"checksum\":\"" + sumZip + "\",\"link\":\"https://x/OpenJDK21U-jre_x64_windows_hotspot.zip\"}}}]";
		String[] pkg = org.dbgl.util.PortableExport.adoptiumPackage(json);
		assertNotNull(pkg);
		assertEquals("https://x/OpenJDK21U-jre_x64_windows_hotspot.zip", pkg[0]);
		assertEquals(sumZip, pkg[1]);
		assertNull(org.dbgl.util.PortableExport.adoptiumPackage("[]"));
		assertNull(org.dbgl.util.PortableExport.adoptiumPackage("not json"));

		File tmp = Files.createTempDirectory("img").toFile();
		try {
			Files.writeString(new File(tmp, "Game.cue").toPath(), "FILE \"Game.bin\" BINARY\n  TRACK 01 MODE1/2352\nFILE \"music 2.wav\" WAVE\nFILE \"../outside.bin\" BINARY\n");
			for (String n: new String[] {"Game.bin", "music 2.wav", "other.iso"})
				Files.writeString(new File(tmp, n).toPath(), n);
			Files.writeString(new File(tmp.getParentFile(), "outside.bin").toPath(), "x");
			java.util.Set<String> names = new java.util.TreeSet<>();
			for (File f: org.dbgl.util.PortableExport.imageFiles(new File(tmp, "Game.cue").getCanonicalFile()))
				names.add(f.getName());
			assertEquals(new java.util.TreeSet<>(List.of("Game.cue", "Game.bin", "music 2.wav")), names);
			new File(tmp.getParentFile(), "outside.bin").delete();
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(tmp);
		}
	}

	/** alpha 48: Steam's binary shortcuts.vdf (read, add, write, nothing lost), the paths of the LaunchBox data, the controller directions */
	@Test
	public void testSteamShortcutsAndExoPaths() throws Exception {
		File tmp = java.nio.file.Files.createTempDirectory("steam").toFile();
		try {
			// a file as Steam writes it: one game of the user with a field this class does not know
			java.util.Map<String, Object> game = new java.util.LinkedHashMap<>();
			game.put("appid", 0x9a2b3c4d);
			game.put("AppName", "Other game");
			game.put("Exe", "\"C:\\Games\\other.exe\"");
			game.put("UnknownField", "kept");
			java.util.Map<String, Object> tags = new java.util.LinkedHashMap<>();
			tags.put("0", "favorite");
			game.put("tags", tags);
			java.util.Map<String, Object> list = new java.util.LinkedHashMap<>();
			list.put("0", game);
			java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
			root.put("shortcuts", list);
			byte[] bytes = org.dbgl.util.SteamShortcuts.write(root);
			assertEquals(0x00, bytes[0]);
			assertEquals(0x08, bytes[bytes.length - 1]);
			assertEquals(0x08, bytes[bytes.length - 2]);
			assertEquals(root, org.dbgl.util.SteamShortcuts.read(bytes));

			File config = new File(tmp, "userdata/123/config");
			config.mkdirs();
			java.nio.file.Files.write(new File(config, "shortcuts.vdf").toPath(), bytes);
			File cover = new File(tmp, "cover.png");
			java.nio.file.Files.write(cover.toPath(), new byte[] {1, 2, 3});
			org.dbgl.util.SteamShortcuts.User user = new org.dbgl.util.SteamShortcuts.User(new File(tmp, "userdata/123"), "Me");
			org.dbgl.util.SteamShortcuts.Game doom = new org.dbgl.util.SteamShortcuts.Game("Doom", new File(tmp, "dosbox.exe"), tmp, "-conf \"x.conf\"", cover);
			org.dbgl.util.SteamShortcuts.Result r = org.dbgl.util.SteamShortcuts.add(user, java.util.List.of(doom), true);
			assertEquals(1, r.added());
			r = org.dbgl.util.SteamShortcuts.add(user, java.util.List.of(doom), true); // the same again: updated, not twice
			assertEquals(0, r.added());
			assertEquals(1, r.updated());
			java.util.Map<String, Object> after = org.dbgl.util.SteamShortcuts.read(java.nio.file.Files.readAllBytes(new File(config, "shortcuts.vdf").toPath()));
			@SuppressWarnings("unchecked")
			java.util.Map<String, Object> shortcuts = (java.util.Map<String, Object>)after.get("shortcuts");
			assertEquals(2, shortcuts.size());
			assertEquals(game, shortcuts.get("0")); // the game of the user as it was
			@SuppressWarnings("unchecked")
			java.util.Map<String, Object> added = (java.util.Map<String, Object>)shortcuts.get("1");
			assertEquals("Doom", added.get("AppName"));
			assertEquals("-conf \"x.conf\"", added.get("LaunchOptions"));
			long appId = org.dbgl.util.SteamShortcuts.appId("\"" + doom.exe().getPath() + "\"", "Doom");
			assertEquals((int)appId, added.get("appid"));
			assertTrue(appId >= 0x80000000L);
			assertTrue(new File(config, "grid/" + appId + "p.png").isFile());
			assertTrue(new File(config, "shortcuts.vdf.dbgl-backup").isFile());
			// cut off or wrong: an error, not a broken file
			try {
				org.dbgl.util.SteamShortcuts.read(java.util.Arrays.copyOf(bytes, 10));
				fail("cut off file read");
			} catch (java.io.IOException e) {
				// expected
			}

			// the paths of the LaunchBox data stay in the collection
			File manual = new File(tmp, "Manuals/MS-DOS/Doom.pdf");
			manual.getParentFile().mkdirs();
			java.nio.file.Files.write(manual.toPath(), new byte[] {1});
			assertEquals(manual.getCanonicalFile(), org.dbgl.util.ExoImport.insideForTest(tmp, "Manuals\\MS-DOS\\Doom.pdf"));
			assertNull(org.dbgl.util.ExoImport.insideForTest(tmp, "..\\outside.pdf"));
			assertNull(org.dbgl.util.ExoImport.insideForTest(tmp, "C:\\Windows\\win.ini"));
			assertNull(org.dbgl.util.ExoImport.insideForTest(tmp, "/etc/passwd"));
			assertNull(org.dbgl.util.ExoImport.insideForTest(tmp, "Manuals/MS-DOS/Missing.pdf"));
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(tmp);
		}
	}

	/** alpha 49: DOSBox Staging 0.83.1: known, same settings as 0.83.0, but "auto" is no value of keyboard_layout and country any more */
	@Test
	public void testConfTakeover() throws Exception {
		String ref = Files.readString(new File(DATA_DIR, "dosbox-staging-0.83.0.conf").toPath(), StandardCharsets.UTF_8).replace("\r\n", "\n");
		String german = Files.readString(new File(DATA_DIR, "dosbox-staging-0.83.0-de.conf").toPath(), StandardCharsets.UTF_8);
		// the defaults of 0.83.0 come from the list of DBGL: also for a file with German comments
		assertEquals(193, org.dbgl.util.ConfTakeover.shipped("staging-0.83.0").size());
		assertEquals(193, org.dbgl.util.ConfTakeover.shipped("staging-0.83.1").size());
		assertEquals(172, org.dbgl.util.ConfTakeover.shipped("staging-0.82.2").size());
		assertEquals(64, org.dbgl.util.ConfTakeover.shipped("dosbox-0.74-3").size());
		assertEquals("staging-0.82.0", org.dbgl.util.ConfTakeover.listFor("# This is the configuration file for DOSBox Staging (0.82.0)."));
		assertEquals("staging-0.82.2", org.dbgl.util.ConfTakeover.listFor("# This is the configuration file for DOSBox Staging (0.82.1)."));
		assertEquals("staging-0.81.2", org.dbgl.util.ConfTakeover.listFor("# Dies ist die Konfigurationsdatei f\u00fcr dosbox-staging (0.81.1)."));
		assertEquals("false", org.dbgl.util.ConfTakeover.shipped("staging-0.82.0").get("voodoo\nvoodoo_bilinear_filtering"));
		assertEquals("true", org.dbgl.util.ConfTakeover.shipped("staging-0.82.2").get("voodoo\nvoodoo_bilinear_filtering"));
		assertFalse(org.dbgl.util.ConfTakeover.shipped("staging-0.81.0").isEmpty());
		assertEquals("staging-0.81.0", org.dbgl.util.ConfTakeover.listFor("# DOSBox Staging 0.81.0 - plik konfiguracyjny."));
		// DOSBox-X in other languages
		assertEquals("dosbox-x", org.dbgl.util.ConfTakeover.listFor("# Dies ist die Konfig-Datei fuer DOSBox-X 2026.10.01. (Verwende bitte die neuste Version von DOSBox-X)"));
		assertEquals("dosbox-x", org.dbgl.util.ConfTakeover.listFor("??? ???? ???????????? ??? DOSBox-X 2026.10.01. (??????????)"));
		assertNull(org.dbgl.util.ConfTakeover.listFor("# a note about DOSBox-X and its files"));
		String germanX = "# Dies ist die Konfig-Datei fuer DOSBox-X 2026.10.01. (Verwende bitte die neuste Version von DOSBox-X)\n[sdl]\n#     output: Welches Videosystem\n#               Moegliche Werte: default, surface.\noutput = default\n";
		org.dbgl.model.conf.ConfValueOptions.Options xOut = org.dbgl.model.conf.ConfValueOptions.parse(germanX).get("sdl", "output");
		assertTrue(xOut.getValues().contains("ttf") && xOut.getValues().contains("opengl"));
		assertEquals("48000 || 49716", String.join(" || ", new java.util.TreeSet<>(java.util.Arrays.asList(org.dbgl.util.ConfTakeover.shipped("dosbox-x").get("sblaster\noplrate").split(" \\|\\| ")))));
		assertEquals("staging-0.82.2", org.dbgl.util.ConfTakeover.listFor("# DOSBox Staging (0.82.2) - plik konfiguracyjny."));
		assertEquals("staging-0.83.1", org.dbgl.util.ConfTakeover.listFor("# Dies ist die Konfigurationsdatei für dosbox-staging (0.83.1)."));
		assertEquals("dosbox-0.74-3", org.dbgl.util.ConfTakeover.listFor("# This is the configuration file for DOSBox 0.74-2. (Please use the latest version of DOSBox)"));
		assertEquals("dosbox-x", org.dbgl.util.ConfTakeover.listFor("# This is the configuration file for DOSBox-X 2026.10.01. (Please use the latest version of DOSBox-X)"));
		assertEquals("dosbox-daum", org.dbgl.util.ConfTakeover.listFor("# This is the configuration file for DOSBox SVN-Daum. (Please use the latest version of DOSBox)"));
		assertEquals("dosbox-ece", org.dbgl.util.ConfTakeover.listFor("# This is the configuration file for DOSBox ECE. (Please use the latest version of DOSBox)"));
		assertNull(org.dbgl.util.ConfTakeover.listFor("# This is the configuration file for DOSBox Pure."));
		// alpha 50: DOSBox 0.71 to 0.73, as they name themselves ("configurationfile")
		assertEquals("dosbox-0.73", org.dbgl.util.ConfTakeover.listFor("# This is the configurationfile for DOSBox 0.73."));
		assertEquals("dosbox-0.72", org.dbgl.util.ConfTakeover.listFor("# This is the configurationfile for DOSBox 0.71."));
		assertEquals("dosbox-0.74-3", org.dbgl.util.ConfTakeover.listFor("# This is the configurationfile for DOSBox 0.74. (Please use the latest version of DOSBox)"));
		assertEquals("22050", org.dbgl.util.ConfTakeover.shipped("dosbox-0.73").get("mixer\nrate"));
		assertEquals("vga", org.dbgl.util.ConfTakeover.shipped("dosbox-0.72").get("dosbox\nmachine"));
		// a file in another language: the values from the list of the version, not from its comments
		String germanMidi = "# Dies ist die Konfigurationsdatei f\u00fcr dosbox-staging (0.83.1).\n[midi]\n# Legt fest ...\n#\n#   Port:   Ein MIDI-Port\n#   mt32:   Der interne\n#\nmididevice = port\n";
		org.dbgl.model.conf.ConfValueOptions.Options midi = org.dbgl.model.conf.ConfValueOptions.parse(germanMidi).get("midi", "mididevice");
		assertTrue(midi.getValues().contains("port") && midi.getValues().contains("fluidsynth") && !midi.getValues().contains("Port"));
		assertNotNull(org.dbgl.model.conf.ConfValueOptions.parse(germanMidi).get("sdl", "output")); // also for a setting the file does not have
		// an English file is read as it is
		String english = "# This is the configuration file for DOSBox Staging (0.83.1).\n[midi]\n# Possible values:\n#\n#   port:   x\n#   mine:   x\n#\nmididevice = port\n";
		assertEquals(java.util.List.of("port", "mine"), org.dbgl.model.conf.ConfValueOptions.parse(english).get("midi", "mididevice").getValues());
		assertEquals("dosbox-svn", org.dbgl.util.ConfTakeover.listFor("# This is the configuration file for DOSBox SVN. (Please use the latest version of DOSBox)"));
		assertEquals(66, org.dbgl.util.ConfTakeover.shipped("dosbox-svn").size());
		assertEquals(108, org.dbgl.util.ConfTakeover.shipped("dosbox-ece").size());
		// DOSBox ECE with more memory and a scaler, into DOSBox Staging: the memory is taken over, the scaler is gone there
		List<org.dbgl.util.ConfTakeover.Row> fromEce = org.dbgl.util.ConfTakeover.compare(Files.readString(new File(DATA_DIR, "dosbox-ece-r4482.conf").toPath(), StandardCharsets.ISO_8859_1),
			Files.readString(new File(DATA_DIR, "dosbox-staging-0.83.0.conf").toPath(), StandardCharsets.UTF_8));
		assertTrue(fromEce.toString(), fromEce.stream().anyMatch(r -> r.item().equals("memsize") && r.oldValue().equals("512") && r.preselected()));
		assertTrue(fromEce.stream().anyMatch(r -> r.item().equals("scaler") && (r.state() == org.dbgl.util.ConfTakeover.State.REMOVED)));
		assertTrue(fromEce.stream().noneMatch(r -> r.item().equals("mapperfile") || (r.state() == org.dbgl.util.ConfTakeover.State.UNKNOWN)));
		assertTrue(org.dbgl.util.ConfTakeover.shipped("dosbox-x").size() > 800);
		assertTrue(org.dbgl.util.ConfTakeover.shipped("dosbox-daum").size() > 400);
		// DOSBox-X and SVN-Daum as they come: nothing of them is a change of the user (not the hundreds of settings the other
		// DOSBox does not have, not the name of the mapper file); only what was changed shows
		String daum = Files.readString(new File(DATA_DIR, "dosbox-daum-2015.01.25.conf").toPath(), StandardCharsets.ISO_8859_1);
		String dosboxX = Files.readString(new File(DATA_DIR, "dosbox-x-2026.10.01.conf").toPath(), StandardCharsets.ISO_8859_1);
		for (String[] pair: new String[][] {{daum, ref}, {dosboxX, ref}, {daum, dosboxX}, {dosboxX, daum}}) {
			List<org.dbgl.util.ConfTakeover.Row> none = org.dbgl.util.ConfTakeover.compare(pair[0], pair[1]);
			assertTrue(none.toString(), none.stream().noneMatch(r -> r.preselected() || !r.selectable()));
		}
		// the language of DOSBox Staging ("de") is no file of translations of DOSBox-X: shown, not chosen
		assertTrue(org.dbgl.util.ConfTakeover.compare(ref.replace("\nlanguage = auto", "\nlanguage = de"), dosboxX).stream()
				.anyMatch(r -> r.item().equals("language") && (r.state() == org.dbgl.util.ConfTakeover.State.NOT_LISTED) && !r.preselected()));
		List<org.dbgl.util.ConfTakeover.Row> changedX = org.dbgl.util.ConfTakeover.compare(
			dosboxX.replaceFirst("(?m)^output( *)= *default", "output$1= ttf").replaceFirst("(?m)^memsize( *)= *16", "memsize$1= 64").replaceFirst("(?m)^cycles( *)= *auto", "cycles$1= fixed 20000"), ref);
		assertEquals(changedX.toString(), 2, changedX.stream().filter(org.dbgl.util.ConfTakeover.Row::preselected).count()); // not "ttf" of its setup
		assertTrue(changedX.stream().anyMatch(r -> r.item().equals("cpu_cycles") && r.oldValue().equals("20000") && r.preselected()));
		assertTrue(changedX.stream().anyMatch(r -> r.item().equals("memsize") && r.oldValue().equals("64") && r.preselected()));
		assertEquals("", org.dbgl.util.ConfTakeover.defaultIn("File (empty by default)."));
		assertEquals("off", org.dbgl.util.ConfTakeover.defaultIn("Web server (disabled by default)."));
		assertTrue(org.dbgl.util.ConfTakeover.shipped("staging-9.9.9").isEmpty());
		// the older file: fullscreen and the sensitivity changed, commands in [autoexec]
		String older = ref.replace("\nfullscreen = off", "\nfullscreen = true").replace("\nmouse_sensitivity = 100", "\nmouse_sensitivity = 55 98") + "mount c /games\nc:\n";
		// the newer file, as 0.83.1 writes it: other defaults, a setting less, its version in the name of the mapper file
		String newer = ref.replace("(0.83.0)", "(0.83.1)").replace("\nblocksize = 512", "\nblocksize = 1024").replace("\nkeyboard_layout = auto", "\nkeyboard_layout = us")
				.replace("mapper-sdl2-0.83.0.map", "mapper-sdl2-0.83.1.map").replace("\nvoodoo_threads = auto", "").replace("\n", "\r\n");
		assertFalse(ref.replace("\n", "\r\n").equals(newer));
		List<org.dbgl.util.ConfTakeover.Row> rows = org.dbgl.util.ConfTakeover.compare(older, newer);
		java.util.Map<String, org.dbgl.util.ConfTakeover.State> states = new java.util.LinkedHashMap<>();
		rows.forEach(r -> states.put(r.item(), r.state()));
		assertEquals(org.dbgl.util.ConfTakeover.State.OWN, states.get("fullscreen"));
		assertEquals(org.dbgl.util.ConfTakeover.State.OWN, states.get("mouse_sensitivity"));
		assertEquals(org.dbgl.util.ConfTakeover.State.OLD_DEFAULT, states.get("blocksize")); // the newer default stays
		assertEquals(org.dbgl.util.ConfTakeover.State.OLD_DEFAULT, states.get("keyboard_layout")); // "auto" is not taken over
		assertEquals(org.dbgl.util.ConfTakeover.State.AUTOEXEC, states.get(""));
		assertFalse(states.toString(), states.containsKey("mapperfile")); // each version names its own file
		assertFalse(states.toString(), states.containsKey("voodoo_threads")); // gone, but it was the default anyway
		assertEquals(states.toString(), 5, states.size());
		assertEquals(3, rows.stream().filter(org.dbgl.util.ConfTakeover.Row::preselected).count());
		// the same from a file with German comments ("on" and "true" are one value)
		String olderGerman = german.replace("\r\n", "\n").replaceAll("\nfullscreen *= *\\S+", "\nfullscreen = on");
		assertTrue(org.dbgl.util.ConfTakeover.compare(olderGerman, newer).stream()
				.anyMatch(r -> r.item().equals("fullscreen") && (r.state() == org.dbgl.util.ConfTakeover.State.OWN) && r.newValue().equals("off")));
		assertTrue(org.dbgl.util.ConfTakeover.compare(olderGerman, newer).stream().noneMatch(r -> r.state() == org.dbgl.util.ConfTakeover.State.UNKNOWN));
		// a version of DOSBox Staging DBGL has no list for: the English comments say the defaults
		assertEquals(org.dbgl.util.ConfTakeover.State.OLD_DEFAULT, org.dbgl.util.ConfTakeover.compare(ref.replace("(0.83.0)", "(0.84.7)"), newer).stream()
				.filter(r -> r.item().equals("blocksize")).findFirst().get().state());
		assertEquals("opengl", org.dbgl.util.ConfTakeover.defaultIn("Rendering backend ('opengl' by default). Only"));
		assertEquals("0", org.dbgl.util.ConfTakeover.defaultIn("Number of display (0 by default)."));
		assertEquals("", org.dbgl.util.ConfTakeover.defaultIn("Path (unset by default)."));
		assertNull(org.dbgl.util.ConfTakeover.defaultIn("(standardmäßig 'opengl')"));
		// a file that does not say which DOSBox wrote it: nothing known about its defaults; what became of older settings
		String classic = "[sdl]\nfullscreen=true\nsensitivity=150\nfulldouble=true\npriority = higher normal\nfullresolution=1280x1024\ncapture_mouse=seamless middlegame\n"
				+ "[mixer]\nblocksize=1024\n[speaker]\ndisney=true\n[mouse]\ndos_mouse_driver=false\n";
		List<org.dbgl.util.ConfTakeover.Row> fromClassic = org.dbgl.util.ConfTakeover.compare(classic, ref);
		assertEquals(fromClassic.toString(), 9, fromClassic.size());
		assertEquals(org.dbgl.util.ConfTakeover.State.UNKNOWN, fromClassic.get(0).state());
		assertEquals("mouse_sensitivity", fromClassic.get(1).item()); // under its new name, in its new section
		assertEquals("mouse", fromClassic.get(1).section());
		assertEquals("[sdl] sensitivity", fromClassic.get(1).from());
		assertEquals(org.dbgl.util.ConfTakeover.State.MISSING, fromClassic.get(2).state()); // fulldouble: not known to DBGL
		assertFalse(fromClassic.get(2).selectable());
		assertEquals(org.dbgl.util.ConfTakeover.State.REMOVED, fromClassic.get(3).state()); // priority
		assertEquals(org.dbgl.util.ConfTakeover.State.MANUAL, fromClassic.get(4).state()); // fullresolution
		assertEquals("[sdl] fullscreen_mode", fromClassic.get(4).from());
		assertFalse(fromClassic.get(4).selectable());
		assertEquals("[mouse] mouse_capture=seamless", "[" + fromClassic.get(5).section() + "] " + fromClassic.get(5).item() + "=" + fromClassic.get(5).oldValue());
		assertEquals("blocksize", fromClassic.get(6).item());
		assertEquals("lpt_dac=disney", fromClassic.get(7).item() + "=" + fromClassic.get(7).oldValue());
		assertEquals("builtin_dos_mouse_driver=off", fromClassic.get(8).item() + "=" + fromClassic.get(8).oldValue());
		String fromClassicApplied = org.dbgl.util.ConfTakeover.apply(ref, fromClassic);
		assertTrue(fromClassicApplied.contains("\nmouse_capture = seamless\n") && fromClassicApplied.contains("\nlpt_dac = disney\n"));
		assertFalse(fromClassicApplied.contains("priority") || fromClassicApplied.contains("fulldouble"));
		// a target that is not DOSBox Staging: no guess about what became of a setting
		assertTrue(org.dbgl.util.ConfTakeover.compare("[sdl]\nsensitivity=150\npriority=higher,normal\n", "[sdl]\nfullscreen=false\n[mouse]\nmouse_sensitivity=100\n").stream()
				.allMatch(r -> r.state() == org.dbgl.util.ConfTakeover.State.MISSING));
		// a file of 0.82 written on Windows, set out in columns: the sound buffer of Windows is no change of the user
		String win082 = "# This is the configuration file for DOSBox Staging (0.82.2).\r\n[sdl]\r\nfullscreen          = true\r\npriority            = auto auto\r\n"
				+ "[mixer]\r\nblocksize  = 1024\r\nnegotiate  = false\r\n[dos]\r\nver = 6.22\r\n";
		List<org.dbgl.util.ConfTakeover.Row> from082 = org.dbgl.util.ConfTakeover.compare(win082, ref);
		assertEquals(from082.toString(), 4, from082.size());
		assertEquals(org.dbgl.util.ConfTakeover.State.OWN, from082.get(0).state()); // fullscreen
		assertEquals(org.dbgl.util.ConfTakeover.State.OLD_DEFAULT, from082.get(1).state()); // blocksize: the default on Windows
		assertEquals(org.dbgl.util.ConfTakeover.State.OLD_DEFAULT, from082.get(2).state()); // negotiate
		assertEquals(org.dbgl.util.ConfTakeover.State.OWN, from082.get(3).state()); // ver
		// a file of DOSBox 0.74: its own defaults are known; what DOSBox Staging takes in another form is written in that form
		String classic074 = "# This is the configurationfile for DOSBox 0.74. (Please use the latest version of DOSBox)\n[sdl]\nfullscreen=true\nwindowresolution=960x600\n"
				+ "output=surface\nmapperfile=mapper-0.74.map\n[dosbox]\nlanguage=german-0.74.lang\nmemsize=32\n[render]\naspect=true\nscaler=advinterp2x\n"
				+ "[cpu]\ncore=dynamic\ncycles=max\n[mixer]\nrate=44100\nprebuffer=20\n[gus]\ngus=true\n[autoexec]\n# Lines in this section will be run at startup.\nmount c c:\\games\n";
		java.util.Map<String, String> from074 = new java.util.LinkedHashMap<>();
		List<org.dbgl.util.ConfTakeover.Row> rows074 = org.dbgl.util.ConfTakeover.compare(classic074, ref);
		rows074.forEach(r -> from074.put(r.item(), r.oldValue() + " " + r.state() + (r.preselected() ? " x": "")));
		assertEquals("on OWN x", from074.get("fullscreen"));
		assertEquals("960x600 OWN x", from074.get("window_size"));
		assertEquals("surface OLD_DEFAULT", from074.get("output"));
		assertFalse(from074.toString(), from074.containsKey("mapperfile"));
		assertEquals("de OWN x", from074.get("language"));
		assertEquals("32 OWN x", from074.get("memsize"));
		assertEquals("on OWN x", from074.get("aspect"));
		assertEquals("advinterp2x REMOVED", from074.get("scaler"));
		assertEquals("dynamic OWN x", from074.get("core"));
		assertEquals("max OWN x", from074.get("cpu_cycles"));
		assertEquals("44100 OLD_DEFAULT", from074.get("rate"));
		assertFalse(from074.toString(), from074.containsKey("prebuffer")); // 20 here as there
		assertEquals("on OWN x", from074.get("gus"));
		assertEquals(from074.toString(), 12, from074.size()); // and [autoexec]
		assertEquals("german-0.74.lang", rows074.stream().filter(r -> r.item().equals("language")).findFirst().get().from());
		String applied074 = org.dbgl.util.ConfTakeover.apply(ref, rows074.stream().filter(org.dbgl.util.ConfTakeover.Row::preselected).toList());
		assertTrue(applied074.contains("\nlanguage = de\n") && applied074.contains("\ncpu_cycles = max\n") && applied074.contains("\naspect = on\n"));
		assertTrue(applied074.endsWith("\nmount c c:\\games\n"));
		assertFalse(applied074.contains("Lines in this section will be run")); // the comment of the older file is not taken
		assertEquals("de", org.dbgl.util.ConfTakeover.languageCode("C:\\DOSBox\\german-0.74.lang"));
		assertNull(org.dbgl.util.ConfTakeover.languageCode("klingon.lang"));
		assertEquals(org.dbgl.util.ConfTakeover.State.MANUAL, org.dbgl.util.ConfTakeover.compare("[cpu]\ncycles=auto 5000 80% limit 20000\n", ref).get(0).state());
		// taken over into a file set out in columns: the line keeps its form
		assertEquals("[sdl]\r\nfullscreen          = on\r\n", org.dbgl.util.ConfTakeover.apply("[sdl]\r\nfullscreen          = false\r\n",
			org.dbgl.util.ConfTakeover.compare("[sdl]\nfullscreen=on\n", "[sdl]\r\nfullscreen          = false\r\n")));
		// taking over: the lines keep their form, the comments and the line ends stay
		String result = org.dbgl.util.ConfTakeover.apply(newer, rows.stream().filter(org.dbgl.util.ConfTakeover.Row::preselected).toList());
		assertTrue(result.contains("\r\nfullscreen = on\r\n"));
		assertTrue(result.contains("\r\nmouse_sensitivity = 55 98\r\n"));
		assertTrue(result.contains("\r\nblocksize = 1024\r\n")); // not chosen
		assertTrue(result.endsWith("\r\nmount c /games\r\nc:\r\n"));
		assertFalse(result.replace("\r\n", "").contains("\n"));
		assertEquals(newer.split("\r\n").length + 3, result.split("\r\n").length); // the empty line at the end, and the two commands
		assertTrue(result.contains("[autoexec]\r\n\r\n# Each line in this section")); // the comment of the section stays
		assertTrue(org.dbgl.util.ConfTakeover.compare(older, result).stream().noneMatch(org.dbgl.util.ConfTakeover.Row::preselected));
		// test 16: the preview: the lines that change below their section, or the whole file
		List<org.dbgl.util.ProfileFindings.DiffLine> shortPreview = org.dbgl.util.ConfTakeover.preview(newer, result, false);
		StringBuilder shown = new StringBuilder();
		shortPreview.forEach(l -> shown.append(l.kind()).append(l.text()).append('|'));
		assertEquals(" [sdl]|-fullscreen = off|+fullscreen = on| [mouse]|-mouse_sensitivity = 100|+mouse_sensitivity = 55 98| [autoexec]|+mount c /games|+c:|", shown.toString());
		List<org.dbgl.util.ProfileFindings.DiffLine> fullPreview = org.dbgl.util.ConfTakeover.preview(newer, result, true);
		assertEquals(4, fullPreview.stream().filter(l -> l.kind() == '+').count());
		assertEquals(2, fullPreview.stream().filter(l -> l.kind() == '-').count());
		assertTrue(org.dbgl.util.ConfTakeover.preview(newer, newer, false).isEmpty());
		// [autoexec] replaced, not added to; a file without the section gets one
		assertTrue(org.dbgl.util.ConfTakeover.withAutoexec("[sdl]\na=1\n[autoexec]\n# comment\nold\n", List.of("# other comment", "new")).endsWith("[autoexec]\n# comment\nnew\n"));
		assertEquals("[sdl]\na=1\n\n[autoexec]\nnew\n", org.dbgl.util.ConfTakeover.withAutoexec("[sdl]\na=1\n", List.of("new")));
		// written with a copy of the file as it was
		File dir = Files.createTempDirectory("dbgltakeover").toFile();
		File conf = new File(dir, "dosbox-staging.conf");
		Files.write(conf.toPath(), newer.getBytes(StandardCharsets.UTF_8));
		File backup = org.dbgl.util.ConfTakeover.write(conf, org.dbgl.util.ConfTakeover.apply(org.dbgl.util.ConfTakeover.read(conf), rows));
		assertEquals(newer, Files.readString(backup.toPath(), StandardCharsets.UTF_8));
		assertTrue(backup.getName().startsWith("dosbox-staging.conf.") && backup.getName().endsWith(".bak"));
		assertTrue(Files.readString(conf.toPath(), StandardCharsets.UTF_8).contains("\r\nkeyboard_layout = auto\r\n")); // all rows: also the old default
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	@Test
	public void testStaging0831() throws Exception {
		assertTrue(SettingsService.STAGING_DOSBOX_FAM.versions_.contains("0.83.1"));
		assertEquals(Generation.staging_0830, gen(SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.1"));
		String conf083 = Files.readString(new File(DATA_DIR, "dosbox-staging-0.83.0.conf").toPath(), StandardCharsets.UTF_8);
		File confFile = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
		org.dbgl.model.aggregate.DosboxVersion[] versions = new org.dbgl.model.aggregate.DosboxVersion[3];
		String[] numbers = {"0.83.0", "0.83.1", "0.84.0 (abc12)"};
		for (int i = 0; i < 3; i++) {
			versions[i] = DosboxVersionFactory.create("DOSBox Staging " + numbers[i], SettingsService.STAGING_DOSBOX_FAM.title_, numbers[i], false, true, false, null, "staging", "", "",
				"staging/dosbox.conf");
			// as 0.83.1 writes its configuration: a value instead of "auto"
			versions[i].loadConfigurationData(GenericTextService.getInstance(),
				i == 0 ? conf083: conf083.replace("keyboard_layout = auto", "keyboard_layout = us").replace("country = auto", "country = 1"), confFile);
		}
		assertFalse(org.dbgl.util.ProfileChecks.staging0831(versions[0]));
		assertTrue(org.dbgl.util.ProfileChecks.staging0831(versions[1]));
		assertTrue(org.dbgl.util.ProfileChecks.staging0831(versions[2]));
		assertFalse(org.dbgl.util.ProfileChecks.staging0831(DosboxVersionFactory.create("X", "DOSBox-X", "2026.10.01", false, true, false, null, "x", "", "", "x/dosbox.conf")));
		for (int i = 0; i < 2; i++) {
			org.dbgl.model.aggregate.Profile profile = org.dbgl.model.factory.ProfileFactory.create(versions[i], null);
			profile.getConfiguration().setValue("dos", "keyboard_layout", "auto");
			profile.getConfiguration().setValue("dos", "country", "49");
			profile.getConfiguration().setValue("dos", "locale_period", "modern"); // a value that stays (only "native" went)
			List<org.dbgl.util.ProfileChecks.StagingChange> changes = org.dbgl.util.ProfileChecks.stagingChanges(profile);
			if (i == 0) {
				assertTrue(changes.toString(), changes.isEmpty()); // 0.83.0 knows "auto"
			} else {
				assertEquals(1, changes.size()); // only the setting that says auto
				assertTrue(org.dbgl.util.ProfileChecks.applyStagingChange(profile, "dos", "keyboard_layout"));
				assertFalse(profile.getConfiguration().hasValue("dos", "keyboard_layout")); // gone: the value of the DOSBox version counts
				assertEquals("49", profile.getConfiguration().getValue("dos", "country")); // a real value stays
				assertTrue(org.dbgl.util.ProfileChecks.stagingChanges(profile).isEmpty());
			}
			// test 15: the old name with "auto": for 0.83.0 renamed to keyboard_layout = auto, for 0.83.1 the value goes
			org.dbgl.model.aggregate.Profile oldName = org.dbgl.model.factory.ProfileFactory.create(versions[i], null);
			oldName.getConfiguration().setValue("dos", "keyboardlayout", "auto");
			List<org.dbgl.util.ProfileChecks.StagingChange> renamed = org.dbgl.util.ProfileChecks.stagingChanges(oldName);
			assertEquals(1, renamed.size());
			assertEquals(i == 0 ? "rename": "autogone", renamed.get(0).old().kind());
			assertTrue(org.dbgl.util.ProfileChecks.applyStagingChange(oldName, "dos", "keyboardlayout"));
			assertFalse(oldName.getConfiguration().hasValue("dos", "keyboardlayout"));
			assertEquals(i == 0, oldName.getConfiguration().hasValue("dos", "keyboard_layout"));
			// another value under the old name is renamed for both
			oldName.getConfiguration().setValue("dos", "keyboardlayout", "gr");
			assertEquals("rename", org.dbgl.util.ProfileChecks.stagingChanges(oldName).get(0).old().kind());
		}
		// test 16: a profile switched from 0.83.0 to 0.83.1: "auto" goes with the switch, other values stay
		org.dbgl.model.aggregate.Profile switched = org.dbgl.model.factory.ProfileFactory.create(versions[0], null);
		switched.getConfiguration().setValue("dos", "keyboard_layout", "auto");
		switched.getConfiguration().setValue("dos", "country", "49");
		assertTrue(org.dbgl.util.ProfileChecks.removeGone0831(switched).isEmpty()); // 0.83.0: nothing to do
		switched.setDosboxVersion(versions[1]);
		List<String[]> goneNow = org.dbgl.util.ProfileChecks.removeGone0831(switched);
		assertEquals(1, goneNow.size());
		assertEquals("[dos] keyboard_layout=auto", goneNow.get(0)[0] + "=" + goneNow.get(0)[1]);
		assertFalse(switched.getConfiguration().hasValue("dos", "keyboard_layout"));
		assertEquals("49", switched.getConfiguration().getValue("dos", "country"));
		assertTrue(org.dbgl.util.ProfileChecks.removeGone0831(switched).isEmpty());
		// test 15: the version chosen in DBGL says 0.83.0, its configuration file was written by 0.83.1: that one counts
		File dir = Files.createTempDirectory("dbgl0831").toFile();
		File written = new File(dir, "dosbox.conf");
		Files.writeString(written.toPath(), conf083.replace("(0.83.0)", "(0.83.1)"), StandardCharsets.UTF_8);
		assertTrue(org.dbgl.util.ProfileChecks.staging0831(DosboxVersionFactory.create("Staging", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, dir.getPath(), "", "",
			written.getPath())));
		long writtenBefore = written.lastModified();
		Files.writeString(written.toPath(), conf083, StandardCharsets.UTF_8);
		// same length, and on a fast machine written within the same millisecond: DBGL tells a changed file by time and size
		assertTrue(written.setLastModified(writtenBefore + 2000));
		assertFalse(org.dbgl.util.ProfileChecks.staging0831(DosboxVersionFactory.create("Staging", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, dir.getPath(), "", "",
			written.getPath())));
		// test 17: values written in the file of a profile that are the same as in its DOSBox version: read from the file, DBGL
		// does not hold them. Switched to 0.83.1 with other values there: "memsize = 16" stays, "auto" is told as gone
		File newDir = new File(dir, "new");
		newDir.mkdirs();
		File newConf = new File(newDir, "dosbox.conf");
		Files.writeString(newConf.toPath(), conf083.replace("(0.83.0)", "(0.83.1)").replaceFirst("(?m)^memsize = 16", "memsize = 32").replace("keyboard_layout = auto", "keyboard_layout = gr"),
			StandardCharsets.UTF_8);
		org.dbgl.model.aggregate.DosboxVersion oldVersion = DosboxVersionFactory.create("Old", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, dir.getPath(), "", "",
			written.getPath());
		org.dbgl.model.aggregate.DosboxVersion newVersion = DosboxVersionFactory.create("New", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.1", false, true, false, null, newDir.getPath(), "", "",
			newConf.getPath());
		oldVersion.setId(9001);
		newVersion.setId(9002);
		oldVersion.resetAndLoadConfiguration();
		newVersion.resetAndLoadConfiguration();
		File profileFile = new File(dir, "profile.conf");
		Files.writeString(profileFile.toPath(), "[dosbox]\nmemsize = 16\n[dos]\nkeyboard_layout = auto\n[cpu]\ncore = normal\n[autoexec]\n", StandardCharsets.UTF_8);
		org.dbgl.model.aggregate.Profile fromFile = org.dbgl.model.factory.ProfileFactory.create(oldVersion, null);
		fromFile.setConfigurationFileLocation(profileFile.getPath());
		fromFile.resetAndLoadConfiguration();
		assertFalse(fromFile.getConfiguration().hasValue("dosbox", "memsize")); // the same as in the DOSBox version: not held
		assertEquals("normal", fromFile.getConfiguration().getValue("cpu", "core"));
		assertTrue(org.dbgl.util.ProfileChecks.gone0831InFile(profileFile, oldVersion).isEmpty());
		List<String[]> inFile = org.dbgl.util.ProfileChecks.gone0831InFile(profileFile, newVersion);
		assertEquals(1, inFile.size());
		assertEquals("[dos] keyboard_layout=auto", inFile.get(0)[0] + "=" + inFile.get(0)[1]);
		assertTrue(org.dbgl.util.ProfileChecks.keepWrittenValues(profileFile, oldVersion, fromFile).isEmpty()); // not switched: nothing to do
		fromFile.switchToDosboxVersion(newVersion);
		assertTrue(org.dbgl.util.ProfileChecks.removeGone0831(fromFile).isEmpty());
		List<String[]> keptValues = org.dbgl.util.ProfileChecks.keepWrittenValues(profileFile, oldVersion, fromFile);
		assertEquals(1, keptValues.size());
		assertEquals("[dosbox] memsize=16 (32)", keptValues.get(0)[0] + "=" + keptValues.get(0)[1] + " (" + keptValues.get(0)[2] + ")");
		assertEquals("16", fromFile.getConfiguration().getValue("dosbox", "memsize"));
		assertFalse(fromFile.getConfiguration().hasValue("dos", "keyboard_layout")); // "auto" is not put back
		assertTrue(org.dbgl.util.ProfileChecks.keepWrittenValues(profileFile, oldVersion, fromFile).isEmpty());
		// test 18: what the profiles take from their DOSBox version and the new one has otherwise: memsize, not "auto" (no
		// value of 0.83.1); kept in a profile only when chosen, and only where the profile has no value of its own
		List<org.dbgl.util.SwitchChoices.Choice> inherited = org.dbgl.util.SwitchChoices.inherited(oldVersion, newVersion);
		assertEquals(1, inherited.size());
		assertEquals("dosbox.memsize 16 -> 32", inherited.get(0).section() + "." + inherited.get(0).item() + " " + inherited.get(0).oldText() + " -> " + inherited.get(0).newText());
		assertFalse(inherited.get(0).defaultKeep());
		assertTrue(org.dbgl.util.SwitchChoices.inherited(oldVersion, oldVersion).isEmpty());
		assertTrue(org.dbgl.util.SwitchChoices.inherited(oldVersion, DosboxVersionFactory.create("X", "DOSBox-X", "2026.10.01", false, true, false, null, "x", "", "", "x/dosbox.conf")).isEmpty());
		org.dbgl.model.aggregate.Profile plain = org.dbgl.model.factory.ProfileFactory.create(oldVersion, null);
		plain.switchToDosboxVersion(newVersion);
		assertTrue(org.dbgl.util.SwitchChoices.applyInherited(plain, inherited, c -> false).isEmpty());
		assertFalse(plain.getConfiguration().hasValue("dosbox", "memsize")); // not chosen: 32 from the new version
		assertEquals(1, org.dbgl.util.SwitchChoices.applyInherited(plain, inherited, c -> true).size());
		assertEquals("16", plain.getConfiguration().getValue("dosbox", "memsize"));
		org.dbgl.model.aggregate.Profile own = org.dbgl.model.factory.ProfileFactory.create(oldVersion, null);
		own.getConfiguration().setValue("dosbox", "memsize", "8");
		own.switchToDosboxVersion(newVersion);
		assertTrue(org.dbgl.util.SwitchChoices.applyInherited(own, inherited, c -> true).isEmpty());
		assertEquals("8", own.getConfiguration().getValue("dosbox", "memsize")); // its own value stays
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}

	/** alpha 49: where the toolbar is broken into two rows */
	@Test
	public void testToolbarSplit() {
		// test 18: the window opened with a toolbar button gets the picture of the menu entry of the same function
		assertEquals("new.png", org.dbgl.gui.abstractdialog.BaseDialog.menuPicture(org.dbgl.service.ImageService.IMG_TB_NEW));
		assertEquals("about.png", org.dbgl.gui.abstractdialog.BaseDialog.menuPicture(org.dbgl.service.ImageService.IMG_TB_ABOUT));
		assertEquals(org.dbgl.service.ImageService.IMG_TB_SETTINGS, org.dbgl.gui.abstractdialog.BaseDialog.menuPicture(org.dbgl.service.ImageService.IMG_TB_SETTINGS));
		assertEquals(org.dbgl.service.ImageService.IMG_TB_ADDGAMEWIZARD, org.dbgl.gui.abstractdialog.BaseDialog.menuPicture(org.dbgl.service.ImageService.IMG_TB_ADDGAMEWIZARD));
		assertEquals(org.dbgl.service.ImageService.IMG_USB, org.dbgl.gui.abstractdialog.BaseDialog.menuPicture(org.dbgl.service.ImageService.IMG_USB));
		assertNull(org.dbgl.gui.abstractdialog.BaseDialog.menuPicture(null));
		// a b | c d | e : each button 100 wide, a line 10
		List<int[]> widths = Arrays.asList(new int[] {100, 0}, new int[] {100, 0}, new int[] {10, 1}, new int[] {100, 0}, new int[] {100, 0}, new int[] {10, 1}, new int[] {100, 0});
		List<String> keys = Arrays.asList("a", "b", null, "c", "d", null, "e");
		assertEquals("e", org.dbgl.gui.controls.ToolbarCustomizer.bestSplit(widths, keys, 450)); // the first row as full as it gets
		assertEquals("e", org.dbgl.gui.controls.ToolbarCustomizer.bestSplit(widths, keys, 410));
		assertEquals("c", org.dbgl.gui.controls.ToolbarCustomizer.bestSplit(widths, keys, 400)); // a b | c d does not fit: a b, then c d | e
		assertEquals("c", org.dbgl.gui.controls.ToolbarCustomizer.bestSplit(widths, keys, 310));
		assertNull(org.dbgl.gui.controls.ToolbarCustomizer.bestSplit(widths, keys, 300)); // c d | e needs 310
		assertNull(org.dbgl.gui.controls.ToolbarCustomizer.bestSplit(widths, keys, 150));
		// test 15: a wide group after the last line that fits: the row goes on into the group rather than ending far from the edge
		List<int[]> wide = Arrays.asList(new int[] {100, 0}, new int[] {10, 1}, new int[] {300, 0}, new int[] {300, 0}, new int[] {300, 0});
		List<String> wideKeys = Arrays.asList("a", null, "b", "c", "d");
		assertEquals("d", org.dbgl.gui.controls.ToolbarCustomizer.bestSplit(wide, wideKeys, 720)); // a | b c, then d
		assertEquals("c", org.dbgl.gui.controls.ToolbarCustomizer.bestSplit(wide, wideKeys, 700)); // a | b, then c d
		assertNull(org.dbgl.gui.controls.ToolbarCustomizer.bestSplit(wide, wideKeys, 500));
		// the toolbar of test 15 at 1440: the first row ended after "random game" (1141), it can go on to "boxes" (1351)
		int[] real = {82, 76, 74, 71, -12, 78, 106, -12, 100, 71, -12, 46, 86, 50, -12, 86, 55, -12, 100, -12, 66, 35, 53, 44, 100, -12, 90, 73, -12, 61, 91, -12, 45};
		List<int[]> realWidths = new java.util.ArrayList<>();
		List<String> realKeys = new java.util.ArrayList<>();
		for (int i = 0; i < real.length; i++) {
			realWidths.add(new int[] {Math.abs(real[i]), real[i] < 0 ? 1: 0});
			realKeys.add(real[i] < 0 ? null: "k" + i);
		}
		assertEquals("k24", org.dbgl.gui.controls.ToolbarCustomizer.bestSplit(realWidths, realKeys, 1430)); // the detail view starts the second row
		assertEquals("k20", org.dbgl.gui.controls.ToolbarCustomizer.bestSplit(realWidths, realKeys, 1250)); // near enough to a line: there
		List<String> built = Arrays.asList("a", "b", "|", "c", ">", "d", "|", "e");
		assertEquals("[a, b, >, c, d, |, e]", org.dbgl.gui.controls.ToolbarCustomizer.splitAt(built, "c").toString());
		assertEquals("[a, b, |, c, d, >, e]", org.dbgl.gui.controls.ToolbarCustomizer.splitAt(built, "e").toString());
		assertEquals("[a, b, |, c, >, d, |, e]", org.dbgl.gui.controls.ToolbarCustomizer.splitAt(built, "d").toString());
		assertNull(org.dbgl.gui.controls.ToolbarCustomizer.splitAt(built, "x"));
	}

	/** alpha 49: the backups in the window that manages them: kind and version from the name, locked ones stay */
	@Test
	public void testBackupCatalog() throws Exception {
		org.dbgl.util.BackupCatalog.Kind auto = org.dbgl.util.BackupCatalog.Kind.AUTO;
		String[][] names = {{"dbgl-backup-20261114-153000.zip", "AUTO", ""}, {"dbgl-backup-2026-11-14.zip", "MANUAL", ""},
				{"before-0.101alpha49-from-0.101alpha48-20261114-153000.zip", "VERSION", "0.101alpha48"}, {"before-0.101alpha49-from-older-20261114-153000.zip", "VERSION", "older"},
				{"dbgl-backup-before-update-0.101alpha48-2026-11-14_15-30.zip", "UPDATE_DATA", "0.101alpha48"}, {"dbgl-program-0.101alpha48-2026-11-14_15-30.zip", "UPDATE_PROGRAM", "0.101alpha48"},
				{"dbgl-full-backup-before-update-0.101alpha48-2026-11-14_15-30.zip", "FULL", "0.101alpha48"}, {"dbgl-ee-0.101alpha49-update.zip", "INSTALLER", "ee-0.101alpha49"}};
		for (String[] n: names) {
			org.dbgl.util.BackupCatalog.Kind kind = org.dbgl.util.BackupCatalog.kindOf(n[0]);
			assertEquals(n[0], n[1], String.valueOf(kind));
			assertEquals(n[0], n[2], org.dbgl.util.BackupCatalog.versionInName(n[0], kind));
		}
		assertNull(org.dbgl.util.BackupCatalog.kindOf("holiday.zip"));
		assertEquals("0.101 alpha 48", org.dbgl.util.BackupCatalog.pretty("0.101alpha48"));
		assertEquals("0.101 alpha 49", org.dbgl.util.BackupCatalog.pretty("ee-0.101alpha49"));
		assertEquals("0.101 alpha 48", org.dbgl.util.BackupCatalog.pretty("0.101 alpha 48"));
		assertEquals("0.93 \u2013 0.98", org.dbgl.util.BackupCatalog.pretty("0.93-0.98")); // alpha 51
		assertEquals("< 0.92", org.dbgl.util.BackupCatalog.pretty("pre0.92"));
		assertEquals("0.99 \u2013 0.100", org.dbgl.util.BackupCatalog.pretty("0.99-0.100"));
		assertEquals("0.101 alpha", org.dbgl.util.BackupCatalog.pretty("0.101alpha"));
		assertEquals("older", org.dbgl.util.BackupCatalog.pretty("older"));
		assertNull(org.dbgl.util.BackupCatalog.kindOf("dbgl-backup-20261114-153000.zip.part"));
		assertTrue(auto.restorable());
		assertFalse(org.dbgl.util.BackupCatalog.Kind.FULL.restorable());
		File dir = Files.createTempDirectory("backups").toFile();
		try {
			File a = new File(dir, "dbgl-backup-20261101-100000.zip"), b = new File(dir, "dbgl-backup-20261102-100000.zip"), other = new File(dir, "notes.txt");
			for (File f: new File[] {a, b, other})
				Files.write(f.toPath(), new byte[123]);
			assertTrue(a.setLastModified(1000000L));
			assertTrue(b.setLastModified(2000000L));
			List<org.dbgl.util.BackupCatalog.Backup> list = org.dbgl.util.BackupCatalog.list(List.of(dir));
			assertEquals(2, list.size());
			assertEquals(b, list.get(0).file()); // the newest first
			assertEquals(123, list.get(0).size());
			assertFalse(list.get(0).locked());
			org.dbgl.util.BackupCatalog.setLocked(a, true);
			assertTrue(org.dbgl.util.BackupCatalog.isLocked(a));
			assertTrue(org.dbgl.util.BackupCatalog.list(List.of(dir)).get(1).locked());
			assertFalse(org.dbgl.util.BackupCatalog.delete(a)); // locked: stays
			assertTrue(a.isFile());
			assertTrue(org.dbgl.util.BackupCatalog.delete(b));
			assertFalse(b.exists());
			org.dbgl.util.BackupCatalog.setLocked(a, false);
			assertFalse(new File(dir, org.dbgl.util.BackupCatalog.LOCKED).exists()); // no list of nothing
			assertTrue(org.dbgl.util.BackupCatalog.delete(a));
			assertTrue(other.isFile()); // not ours: never touched
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
	}

	/** alpha 49: the disk space of a game folder and how it is written; the tooltips of the settings */
	@Test
	public void testDiskUsageAndSettingsTips() throws Exception {
		java.util.Locale de = java.util.Locale.GERMAN, en = java.util.Locale.ENGLISH;
		assertEquals("0 B", org.dbgl.util.DiskUsage.format(0, en));
		assertEquals("0 B", org.dbgl.util.DiskUsage.format(-5, en));
		assertEquals("1023 B", org.dbgl.util.DiskUsage.format(1023, en));
		assertEquals("1 KB", org.dbgl.util.DiskUsage.format(1024, en));
		assertEquals("740 KB", org.dbgl.util.DiskUsage.format(740 * 1024, en));
		assertEquals("1.5 MB", org.dbgl.util.DiskUsage.format(1536 * 1024, en));
		assertEquals("1,5 MB", org.dbgl.util.DiskUsage.format(1536 * 1024, de));
		assertEquals("4 MB", org.dbgl.util.DiskUsage.format(4L << 20, de));
		assertEquals("250 MB", org.dbgl.util.DiskUsage.format((250L << 20) + (300L << 10), en));
		assertEquals("1.2 GB", org.dbgl.util.DiskUsage.format((long)(1.2 * (1L << 30)), en));
		assertEquals("2048 TB", org.dbgl.util.DiskUsage.format(1L << 51, null));
		File tmp = Files.createTempDirectory("disk").toFile();
		try {
			Files.write(new File(tmp, "a.bin").toPath(), new byte[1000]);
			File sub = new File(tmp, "sub/deeper");
			assertTrue(sub.mkdirs());
			Files.write(new File(sub, "b.bin").toPath(), new byte[2345]);
			assertEquals(3345, org.dbgl.util.DiskUsage.folderSize(tmp, () -> false));
			assertEquals(0, org.dbgl.util.DiskUsage.folderSize(new File(tmp, "missing"), () -> false));
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(tmp);
		}
		// one folder with many games mounted as C: (dosroot): each game counts its own folder in it
		String sep = File.separator, root = sep + "dos", other = sep + "games";
		java.util.Map<Integer, String[]> mounted = new java.util.LinkedHashMap<>();
		mounted.put(1, new String[] {root, "KEEN"});
		mounted.put(2, new String[] {root, "DOOM"});
		mounted.put(3, new String[] {root, ""}); // started from dosroot itself
		mounted.put(4, new String[] {other + sep + "ULTIMA", "BIN"}); // a folder of its own, the program in a folder of it
		mounted.put(5, new String[] {other + sep + "shared", "A"});
		mounted.put(6, new String[] {other + sep + "shared", "B"});
		mounted.put(7, new String[] {"", ""});
		assertEquals("{1=" + root + sep + "KEEN, 2=" + root + sep + "DOOM, 3=" + root + ", 4=" + other + sep + "ULTIMA, 5=" + other + sep + "shared" + sep + "A, 6=" + other + sep
				+ "shared" + sep + "B, 7=}", org.dbgl.util.DiskUsage.gameFolders(mounted, java.util.Set.of(), root).toString());
		// a single profile of a shared folder, the other one found before
		mounted.clear();
		mounted.put(8, new String[] {other + sep + "shared", "C"});
		assertEquals("{8=" + other + sep + "shared" + sep + "C}", org.dbgl.util.DiskUsage.gameFolders(mounted, java.util.Set.of(other + sep + "shared" + sep + "A"), null).toString());
		assertEquals("{8=" + other + sep + "shared}", org.dbgl.util.DiskUsage.gameFolders(mounted, java.util.Set.of(), null).toString());
		// the games folders: where the game folders of two or more profiles lie directly; not one inside another
		assertEquals("[" + root + ", " + other + "]", org.dbgl.util.DiskUsage.rootPaths(List.of(root + sep + "KEEN", root + sep + "DOOM", other + sep + "A", other + sep + "B",
			sep + "single" + sep + "GAME", root + sep + "KEEN" + sep + "ADDON1", root + sep + "KEEN" + sep + "ADDON2", "")).toString());
		assertEquals("[]", org.dbgl.util.DiskUsage.rootPaths(List.of(root + sep + "KEEN", other + sep + "A")).toString());
		File tree = Files.createTempDirectory("disk2").toFile();
		try {
			assertTrue(new File(tree, "A/sub").mkdirs());
			assertTrue(new File(tree, "B").mkdirs());
			Files.write(new File(tree, "loose.txt").toPath(), new byte[10]);
			Files.write(new File(tree, "A/a.bin").toPath(), new byte[100]);
			Files.write(new File(tree, "A/sub/s.bin").toPath(), new byte[1000]);
			Files.write(new File(tree, "B/b.bin").toPath(), new byte[10000]);
			String t = tree.getCanonicalPath();
			File canon = new File(t);
			assertEquals(11110, org.dbgl.util.DiskUsage.folderSize(canon, false, java.util.Set.of(), () -> false));
			assertEquals(1110, org.dbgl.util.DiskUsage.folderSize(canon, false, java.util.Set.of(t + sep + "B", t), () -> false)); // without the other game in it
			assertEquals(10, org.dbgl.util.DiskUsage.folderSize(canon, true, java.util.Set.of(), () -> false)); // dosroot: only its own files
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(tree);
		}
		// the label of an option leads to its tooltip: the whole text, or what stands around a name that was filled in
		List<String[]> tips = Arrays.asList(new String[] {"Height", null, "tip height"}, new String[] {"\"", "\" links to the page", "tip link"});
		assertEquals("tip height", org.dbgl.gui.dialog.SettingsDialog.tipFor("Height", tips));
		assertEquals("tip link", org.dbgl.gui.dialog.SettingsDialog.tipFor("\"Link 1\" links to the page", tips));
		assertNull(org.dbgl.gui.dialog.SettingsDialog.tipFor("Height of the column", tips));
		assertNull(org.dbgl.gui.dialog.SettingsDialog.tipFor("\"\" links to the page", tips));
	}

	/** alpha 49: configuration files in the profile folder that no profile uses: found by DBGL's own names only, moved, never deleted */
	@Test
	public void testOrphanConfs() throws Exception {
		File dir = Files.createTempDirectory("dbgl-orphans").toFile();
		for (String n: new String[] {"0.conf", "7.conf", "109.conf", "12.CONF", "template.conf", "setup.conf", "7.conf.bak", "x9.conf", "notes.txt"})
			Files.writeString(new File(dir, n).toPath(), "[cpu]\n# " + n);
		new File(dir, "history/109").mkdirs();
		Files.writeString(new File(dir, "history/109/original.conf").toPath(), "x");
		new File(dir, "55.conf").mkdirs(); // a folder of that name is none
		java.util.List<File> used = java.util.Arrays.asList(new File(dir, "0.conf"), new File(new File(dir, "history").getParentFile(), "./12.conf"), null, new File(dir, "elsewhere/3.conf"));
		java.util.List<File> found = org.dbgl.util.ProfileChecks.orphanConfs(dir, used);
		assertEquals(java.util.List.of("7.conf", "109.conf"), found.stream().map(File::getName).toList()); // by number, not by text
		assertTrue(org.dbgl.util.ProfileChecks.orphanConfs(new File(dir, "nothing here"), used).isEmpty());
		assertTrue(org.dbgl.util.ProfileChecks.orphanConfs(null, used).isEmpty());

		java.util.List<org.dbgl.util.ProfileFindings.Finding> told = org.dbgl.util.ProfileFindings.orphans(found, GenericTextService.getInstance());
		assertEquals(2, told.size());
		assertTrue(told.get(0).hint() && !told.get(0).fixable() && told.get(0).profile() == null && told.get(0).version() == null);
		assertEquals(org.dbgl.util.ProfileFindings.ORPHAN, told.get(0).kind());
		org.junit.Assert.assertNotEquals(told.get(0).dismissKey(), told.get(1).dismissKey());

		File sub = new File(dir, org.dbgl.util.ProfileChecks.ORPHANS_DIR);
		sub.mkdirs();
		Files.writeString(new File(sub, "7.conf").toPath(), "moved earlier"); // stays what it is
		java.util.List<File> moved = org.dbgl.util.ProfileChecks.moveOrphans(found, dir);
		assertEquals(2, moved.size());
		assertEquals("moved earlier", Files.readString(new File(sub, "7.conf").toPath()));
		assertTrue(moved.get(0).getName(), moved.get(0).getName().matches("7\\.\\d{8}-\\d{6}\\.conf"));
		assertEquals("[cpu]\n# 7.conf", Files.readString(moved.get(0).toPath()));
		assertEquals(new File(sub, "109.conf"), moved.get(1));
		assertFalse(new File(dir, "7.conf").exists() || new File(dir, "109.conf").exists());
		for (String n: new String[] {"0.conf", "12.CONF", "template.conf", "setup.conf", "7.conf.bak", "x9.conf", "notes.txt", "history/109/original.conf"})
			assertTrue(n, new File(dir, n).isFile()); // everything else where it was
		assertTrue(org.dbgl.util.ProfileChecks.orphanConfs(dir, used).isEmpty()); // the folder they went to is not looked into
		org.apache.commons.io.FileUtils.deleteQuietly(dir);
	}

	/** alpha 49: the report of switching to another DOSBox version saved as a page in the colors of the window */
	@Test
	public void testSwitchReportHtml() {
		String html = org.dbgl.util.ProfileReports.reportHtml("== Doom <II> & more\r\n[cpu]\r\n- cycles=max\r\n+ cpu_cycles=max\r\n= core=auto\r\n! check \"this\"\r\nplain line\r\n  + indented\r\n-not a change", "Title & <b>");
		assertTrue(html, html.startsWith("<!DOCTYPE html>") && html.contains("<meta charset=\"utf-8\">") && html.contains("<title>Title &amp; &lt;b&gt;</title>"));
		assertTrue(html, html.contains("<span class=\"prof\">== Doom &lt;II&gt; &amp; more</span>\n"));
		assertTrue(html, html.contains("<span class=\"sec\">[cpu]</span>\n<span class=\"del\">- cycles=max</span>\n<span class=\"add\">+ cpu_cycles=max</span>\n<span class=\"same\">= core=auto</span>\n"));
		assertTrue(html, html.contains("<span class=\"warn\">! check &quot;this&quot;</span>\nplain line\n<span class=\"add\">  + indented</span>\n-not a change\n</pre>"));
		assertFalse(html, html.contains("\r"));
		assertTrue(org.dbgl.util.ProfileReports.reportHtml("!! missing\ni  for information\n  # a comment", "t").contains("<span class=\"warn\">!! missing</span>\n<span class=\"same\">i  for information</span>\n<span class=\"same\">  # a comment</span>"));
		// the colors of a window (lighter in the dark theme) as the fixed colors of the page, by their hue
		assertEquals("#b00020", org.dbgl.util.ProfileReports.printColor(128, 0, 0));
		assertEquals("#b00020", org.dbgl.util.ProfileReports.printColor(255, 120, 120));
		assertEquals("#17731a", org.dbgl.util.ProfileReports.printColor(0, 128, 0));
		assertEquals("#17731a", org.dbgl.util.ProfileReports.printColor(120, 220, 120));
		assertEquals("#a35f00", org.dbgl.util.ProfileReports.printColor(128, 128, 0));
		assertEquals("#0b6e75", org.dbgl.util.ProfileReports.printColor(0, 128, 128));
		assertEquals("#767676", org.dbgl.util.ProfileReports.printColor(128, 128, 128));
		assertEquals("#767676", org.dbgl.util.ProfileReports.printColor(200, 200, 205));
		assertNull(org.dbgl.util.ProfileReports.printColor(-1, -1, -1));
		String styled = org.dbgl.util.ProfileReports.styledHtml("Doom <1>\r\n- a=1\r\n+ a=2\r\nplain\r\n", java.util.List.of(new int[] {17, 5, 120, 220, 120, 0}, new int[] {0, 8, -1, -1, -1, 1},
			new int[] {10, 5, 255, 120, 120, 1}, new int[] {900, 5, 0, 0, 0, 0}), "T");
		assertTrue(styled, styled.contains("<pre><span style=\"font-weight:bold\">Doom &lt;1&gt;</span>\n<span style=\"color:#b00020;font-weight:bold\">- a=1</span>\n"
			+ "<span style=\"color:#17731a\">+ a=2</span>\nplain\n</pre>"));
		assertFalse(styled, styled.contains("\r"));
		String print = org.dbgl.util.ProfileReports.forPrinting(styled, "dbgl-switch-report-2026-11-15_12-30");
		assertTrue(print, print.contains("<title>dbgl-switch-report-2026-11-15_12-30</title>") && print.contains("window.print()") && print.endsWith("</body></html>\n"));
		assertEquals("dbgl-switch-report-2026-11-15_12-30.html", org.dbgl.gui.controls.ReportSave.name("dbgl-switch-report", "html", java.time.LocalDateTime.of(2026, 11, 15, 12, 30, 59)));
		assertTrue(org.dbgl.util.ProfileReports.reportHtml("", null).contains("<pre>\n</pre>"));
	}

	/** alpha 49: a remembered setting is not offered again when the DOSBox version says the same in other words ("true" and "on") */
	@Test
	public void testSwitchMemorySameValue() throws Exception {
		org.dbgl.model.aggregate.DosboxVersion v083 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null,
			"staging083", "", "", "staging083/dosbox.conf");
		File dosboxConfFile = new File(DATA_DIR, "dosbox-staging-0.83.0.conf");
		v083.loadConfigurationData(GenericTextService.getInstance(), Files.readString(dosboxConfFile.toPath(), StandardCharsets.UTF_8), dosboxConfFile);
		org.dbgl.model.aggregate.Profile profile = org.dbgl.model.factory.ProfileFactory.create(v083, null);
		int fam = org.dbgl.model.entity.GenerationAwareConfigurable.familyIdx(profile.getGeneration());
		String on = v083.getConfiguration().getValue("dos", "ems"), off = v083.getConfiguration().getValue("ipx", "ipx");
		assertTrue(on + " " + off, org.dbgl.util.ConfTakeover.same("true", on) && org.dbgl.util.ConfTakeover.same("false", off));
		java.util.Properties memory = new java.util.Properties();
		for (String[] kv: new String[][] {{"dos|ems", "true"}, {"dos|xms", "ON"}, {"ipx|ipx", "false"}, {"dos|umb", " " + v083.getConfiguration().getValue("dos", "umb") + " "}})
			memory.setProperty(fam + "|" + kv[0], kv[1]);
		assertTrue(org.dbgl.util.SwitchMemory.restoresFrom(memory, profile).toString(), org.dbgl.util.SwitchMemory.restoresFrom(memory, profile).isEmpty());
		memory.setProperty(fam + "|dos|ems", "false"); // really another value: offered
		java.util.List<org.dbgl.util.SwitchChoices.Choice> offered = org.dbgl.util.SwitchMemory.restoresFrom(memory, profile);
		assertEquals(offered.toString(), 1, offered.size());
		assertEquals("ems", offered.get(0).item());
		assertEquals("false", offered.get(0).oldText());
	}

	/** alpha 49: what a profile only inherits from its new DOSBox version: not what it set itself, not what it keeps */
	@Test
	public void testInheritedByProfile() {
		java.util.List<org.dbgl.util.SwitchChoices.Choice> inherited = java.util.List.of(
			new org.dbgl.util.SwitchChoices.Choice(0, org.dbgl.util.SwitchChoices.Kind.INHERITED, "cpu", "core", "auto", "dynamic", -1, false),
			new org.dbgl.util.SwitchChoices.Choice(0, org.dbgl.util.SwitchChoices.Kind.INHERITED, "sdl", "window_size", "default", "960x600", -1, false),
			new org.dbgl.util.SwitchChoices.Choice(0, org.dbgl.util.SwitchChoices.Kind.INHERITED, "midi", "midiconfig", "", "0", -1, false));
		// a profile with its own core (also when it is the value of the new version: it did not take it from there)
		java.util.List<org.dbgl.util.SwitchChoices.Choice> got = org.dbgl.util.SwitchChoices.inheritedBy("[CPU]\r\nCore = dynamic\r\ncpu_cycles=20000\r\n\r\n[autoexec]\r\nmidiconfig=9\r\n", inherited, c -> false);
		assertEquals(java.util.List.of("window_size", "midiconfig"), got.stream().map(org.dbgl.util.SwitchChoices.Choice::item).toList());
		// the previous value kept for all profiles (ticked): not inherited
		got = org.dbgl.util.SwitchChoices.inheritedBy("[cpu]\ncpu_cycles=20000\n", inherited, c -> c.item().equals("core"));
		assertEquals(java.util.List.of("window_size", "midiconfig"), got.stream().map(org.dbgl.util.SwitchChoices.Choice::item).toList());
		assertEquals(3, org.dbgl.util.SwitchChoices.inheritedBy(null, inherited, c -> false).size());
		assertTrue(org.dbgl.util.SwitchChoices.inheritedBy("[cpu]\ncore=auto\n", java.util.List.of(), c -> false).isEmpty());
	}

	/** alpha 49: the log of DOSBox: lines that differ only in numbers count as the same, hexadecimal values without a digit too */
	@Test
	public void testLogSimilarLines() {
		String a = org.dbgl.gui.DosboxStatusWindow.similarityKey("LOG:  829029616 ERROR CPU:Write 0 to rom at lin=e00a0 phys=e00a0");
		assertEquals(a, org.dbgl.gui.DosboxStatusWindow.similarityKey("LOG:  829029616 ERROR CPU:Write ffff to rom at lin=e00a2 phys=e00a2"));
		assertEquals(a, org.dbgl.gui.DosboxStatusWindow.similarityKey("LOG: 1734466030 ERROR CPU:Write 20 to rom at lin=c142347e phys=c247e"));
		assertEquals(a, org.dbgl.gui.DosboxStatusWindow.similarityKey("LOG: 1 ERROR CPU:Write FFFFFFFF to rom at lin=0xC000 phys=1"));
		assertEquals("# ERROR CPU:Write # to rom at lin=# phys=#", a);
		assertFalse(a.equals(org.dbgl.gui.DosboxStatusWindow.similarityKey("LOG:  829029616 ERROR CPU:Read 0 from rom at lin=e00a0 phys=e00a0")));
		// words stay words
		assertEquals("MIDI:Opened device:mt32".replace("mt32", "mt32"), org.dbgl.gui.DosboxStatusWindow.similarityKey("LOG: MIDI:Opened device:mt32"));
		assertEquals("Loaded config file added", org.dbgl.gui.DosboxStatusWindow.similarityKey("Loaded config file added"));
	}

	/** alpha 49: what a profile inherits, listed like the other values; the file manager command; the preview of a switch in the editor */
	@Test
	public void testSwitchPreviewLines() throws Exception {
		java.util.List<org.dbgl.util.SwitchChoices.Choice> takes = java.util.List.of(
			new org.dbgl.util.SwitchChoices.Choice(0, org.dbgl.util.SwitchChoices.Kind.INHERITED, "cpu", "core", "auto", "dynamic", -1, false),
			new org.dbgl.util.SwitchChoices.Choice(0, org.dbgl.util.SwitchChoices.Kind.INHERITED, "sdl", "window_size", "default", "960x600", -1, false),
			new org.dbgl.util.SwitchChoices.Choice(0, org.dbgl.util.SwitchChoices.Kind.INHERITED, "cpu", "cpu_cycles", "3000", "20000", -1, false));
		assertEquals(java.util.List.of("[cpu]", "> core=dynamic           from X (auto)", "> cpu_cycles=20000       from X (3000)", "[sdl]", "> window_size=960x600    from X (default)"),
			org.dbgl.util.SwitchChoices.inheritedLines(takes, c -> "from X (" + c.oldText() + ")"));
		assertTrue(org.dbgl.util.SwitchChoices.inheritedLines(java.util.List.of(), c -> "").isEmpty());

		org.junit.Assert.assertArrayEquals(new String[] {"explorer.exe", "/select," + new File("a b/x.zip").getAbsolutePath()}, org.dbgl.util.SystemUtils.showInFolderCommand(new File("a b/x.zip"), true, false));
		org.junit.Assert.assertArrayEquals(new String[] {"open", "-R", new File("x.zip").getAbsolutePath()}, org.dbgl.util.SystemUtils.showInFolderCommand(new File("x.zip"), false, true));
		assertNull(org.dbgl.util.SystemUtils.showInFolderCommand(new File("x.zip"), false, false));

		assertEquals(org.eclipse.swt.SWT.COLOR_DARK_RED, org.dbgl.gui.controls.ChangePreview.colorOf("   - a=1"));
		assertEquals(org.eclipse.swt.SWT.COLOR_DARK_GREEN, org.dbgl.gui.controls.ChangePreview.colorOf("+ a=2"));
		assertEquals(org.eclipse.swt.SWT.COLOR_DARK_CYAN, org.dbgl.gui.controls.ChangePreview.colorOf("> core=dynamic    inherited"));
		assertEquals(org.eclipse.swt.SWT.COLOR_DARK_GRAY, org.dbgl.gui.controls.ChangePreview.colorOf("[cpu]"));
		assertEquals(0, org.dbgl.gui.controls.ChangePreview.colorOf("no changes"));

		// a profile of DOSBox Staging 0.83.0 with "auto" for the keyboard, switched to 0.83.1 with other defaults: nothing changes in the profile itself
		String conf = Files.readString(new File(DATA_DIR, "dosbox-staging-0.83.0.conf").toPath(), StandardCharsets.UTF_8);
		File dir = Files.createTempDirectory("dbgl-switchpreview").toFile();
		File c0 = new File(dir, "s0.conf"), c1 = new File(dir, "s1.conf");
		Files.writeString(c0.toPath(), conf, StandardCharsets.UTF_8);
		Files.writeString(c1.toPath(), conf.replace("(0.83.0)", "(0.83.1)").replaceFirst("(?m)^core = auto", "core = dynamic"), StandardCharsets.UTF_8);
		org.dbgl.model.aggregate.DosboxVersion v0 = DosboxVersionFactory.create("DOSBox Staging 0.83.0", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.0", false, true, false, null, dir.getPath(), "", "", c0.getPath());
		v0.setId(1);
		v0.resetAndLoadConfiguration();
		org.dbgl.model.aggregate.DosboxVersion v1 = DosboxVersionFactory.create("DOSBox Staging 0.83.1", SettingsService.STAGING_DOSBOX_FAM.title_, "0.83.1", false, true, false, null, dir.getPath(), "", "", c1.getPath());
		v1.setId(2);
		v1.resetAndLoadConfiguration();
		org.dbgl.model.aggregate.Profile p = org.dbgl.model.factory.ProfileFactory.create(v0, null);
		p.getConfiguration().setValue("dos", "keyboard_layout", "auto");
		p.getConfiguration().setValue("cpu", "cpu_cycles", "20000");
		String before = p.getConfiguration().toString(null);
		java.util.List<String> lines = org.dbgl.gui.dialog.EditSingleProfileDialog.switchPreviewLines(p, v0, v1, true, GenericTextService.getInstance());
		assertEquals(before, p.getConfiguration().toString(null));
		org.junit.Assert.assertSame(v0, p.getDosboxVersion());
		assertTrue(lines.toString(), lines.stream().anyMatch(l -> l.startsWith("- ") && l.contains("keyboard_layout")));
		assertTrue(lines.toString(), lines.stream().anyMatch(l -> l.startsWith("> core=dynamic")));
		assertTrue(lines.toString(), lines.stream().anyMatch(l -> l.startsWith("! ")));
		assertFalse(lines.toString(), lines.stream().anyMatch(l -> l.contains("cpu_cycles")));
		org.apache.commons.io.FileUtils.deleteQuietly(dir);
	}

	/** alpha 49: files of earlier versions put in order: only the ones DBGL knows by name; the size cache moved to "cache" */
	@Test
	public void testOldFiles() throws Exception {
		File root = Files.createTempDirectory("dbgl-oldfiles").toFile();
		File program = new File(root, "program"), data = new File(root, "data"), logs = new File(data, "logs");
		new File(program, "docs").mkdirs();
		data.mkdirs();
		for (String n: new String[] {"CHANGELOG-de.txt", "CHANGELOG-en.txt", "NEU-IN-DER-EXTENDED-EDITION.txt", "NEW-IN-EXTENDED-EDITION.txt", "UPDATE-LIESMICH.txt", "COPYING", "readme.txt", "changelog.txt",
				"dbgl.jar", "freeze-20261115-123133.txt", "docs/changelog-de.txt", "docs/changelog-en.txt", "docs/whats-new-de.txt", "update-readme.txt"})
			Files.writeString(new File(program, n).toPath(), n);
		for (String n: new String[] {"freeze-20261114-010203.txt", "freeze-notes.txt", "freeze-20261114-010203.txt.bak", "menubar-check.txt", "toolbar-metrics.txt", "settings.conf", "my-notes.txt",
				"disksizes.properties"})
			Files.writeString(new File(data, n).toPath(), n);
		new File(data, "freeze-20260101-000000.txt").mkdirs(); // a folder of that name is none
		org.dbgl.util.OldFiles.Found found = org.dbgl.util.OldFiles.find(program, data);
		assertEquals(java.util.List.of("freeze-20261114-010203.txt", "menubar-check.txt", "toolbar-metrics.txt", "freeze-20261115-123133.txt"), found.toLogs().stream().map(File::getName).toList());
		// the English "what is new" has no successor in "docs" yet: it stays
		assertEquals(java.util.List.of("CHANGELOG-de.txt", "CHANGELOG-en.txt", "NEU-IN-DER-EXTENDED-EDITION.txt", "UPDATE-LIESMICH.txt"), found.toDelete().stream().map(File::getName).toList());
		assertEquals(8, found.all().size());
		assertTrue(org.dbgl.util.OldFiles.clean(found, logs).isEmpty());
		assertEquals("freeze-20261115-123133.txt", Files.readString(new File(logs, "freeze-20261115-123133.txt").toPath()));
		for (String n: new String[] {"freeze-20261114-010203.txt", "menubar-check.txt", "toolbar-metrics.txt"})
			assertTrue(n, new File(logs, n).isFile() && !new File(data, n).exists());
		for (String n: new String[] {"NEW-IN-EXTENDED-EDITION.txt", "COPYING", "readme.txt", "changelog.txt", "dbgl.jar", "docs/changelog-de.txt", "docs/changelog-en.txt", "docs/whats-new-de.txt", "update-readme.txt"})
			assertTrue(n, new File(program, n).isFile());
		for (String n: new String[] {"freeze-notes.txt", "freeze-20261114-010203.txt.bak", "settings.conf", "my-notes.txt", "disksizes.properties"})
			assertTrue(n, new File(data, n).isFile());
		assertFalse(new File(program, "CHANGELOG-de.txt").exists() || new File(program, "NEU-IN-DER-EXTENDED-EDITION.txt").exists());
		assertTrue(org.dbgl.util.OldFiles.find(program, data).isEmpty()); // nothing left, also not what is in "logs" now
		// a portable DBGL: program and data in one folder, each file once; without a program folder only the data folder
		Files.writeString(new File(data, "menubar-check.txt").toPath(), "x");
		assertEquals(1, org.dbgl.util.OldFiles.find(data, data).toLogs().size());
		assertEquals(1, org.dbgl.util.OldFiles.find(null, data).all().size());
		assertTrue(org.dbgl.util.OldFiles.find(null, null).isEmpty());
		// the old "how to install" stays as long as the new one is not there
		Files.writeString(new File(program, "UPDATE-LIESMICH.txt").toPath(), "x");
		new File(program, "update-readme.txt").delete();
		assertTrue(org.dbgl.util.OldFiles.find(program, null).toDelete().isEmpty());
		assertEquals(4, org.dbgl.util.OldFiles.DOCS.size());

		// the sizes of the game folders: from the data folder into its folder "cache", once
		java.lang.reflect.Method file = org.dbgl.util.DiskUsage.class.getDeclaredMethod("file", File.class);
		file.setAccessible(true);
		File moved = (File)file.invoke(null, data);
		assertEquals(new File(new File(data, "cache"), "disksizes.properties"), moved);
		assertEquals("disksizes.properties", Files.readString(moved.toPath()));
		assertFalse(new File(data, "disksizes.properties").exists());
		Files.writeString(new File(data, "disksizes.properties").toPath(), "older");
		assertEquals(moved, file.invoke(null, data));
		assertEquals("disksizes.properties", Files.readString(moved.toPath())); // the one in "cache" stays, the other one goes
		assertFalse(new File(data, "disksizes.properties").exists());
		org.apache.commons.io.FileUtils.deleteQuietly(root);
	}
	/** alpha 50: letters typed in the list of profiles (a virtual table does not search by itself) */
	@Test
	public void testListTypeAhead() {
		String[] titles = {"Alone in the Dark", "Doom", "Doom II", "Duke Nukem 3D", "Dune", null, "Wolfenstein 3D"};
		java.util.function.IntFunction<String> label = i -> titles[i];
		StringBuilder typed = new StringBuilder();
		// one letter: the next row with it after the selected one, going round
		assertEquals(1, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'd', -1, titles.length, label));
		assertEquals(2, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'D', 1, titles.length, label));
		assertEquals(3, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'd', 2, titles.length, label));
		assertEquals(4, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'd', 3, titles.length, label));
		assertEquals(1, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'd', 4, titles.length, label));
		// more letters: the beginning typed so far, from the selected row on (it stays if it fits)
		typed.setLength(0);
		assertEquals(1, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'd', 0, titles.length, label));
		assertEquals(3, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'u', 1, titles.length, label));
		assertEquals(4, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'n', 3, titles.length, label));
		assertEquals("dun", typed.toString());
		// a letter nothing begins with is dropped, the list stays where it is
		assertEquals(-1, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'x', 4, titles.length, label));
		assertEquals("dun", typed.toString());
		assertEquals(4, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'e', 4, titles.length, label));
		// nothing with the letter at all, an empty list, a row without a text
		typed.setLength(0);
		assertEquals(-1, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'z', 2, titles.length, label));
		typed.setLength(0);
		assertEquals(-1, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'a', -1, 0, label));
		typed.setLength(0);
		assertEquals(6, org.dbgl.gui.controls.ProfilesList.typeAhead(typed, 'w', 4, titles.length, label));
		assertEquals(0, org.dbgl.gui.controls.ProfilesList.typeAhead(new StringBuilder(), '\u00c4', 3, 1, x -> "\u00e4rger"));
	}
	/** alpha 50: values of DOSBox 0.74 that DOSBox Staging has under another name, taken over as what they mean there */
	@Test
	public void testTakeoverClassicValues() {
		String target = "# This is the configuration file for dosbox-staging (0.83.0).\n[sdl]\noutput = opengl\n[cpu]\n# CPU type. Possible values:\n#\n#   auto:  x\n#   386:  x\n#   386_fast:  x\n#   486:  x\n#   pentium:  x\n#\ncputype = auto\n"
				+ "[midi]\n# Possible values:\n#\n#   port:  x\n#   mt32:  x\n#   none:  x\n#\nmididevice = port\n[joystick]\n# Possible values:\n#\n#   auto:  x\n#   hidden:  x\n#   disabled:  x\n#\njoysticktype = auto\n[mouse]\nmouse_capture = onclick\n";
		String classic = "# This is the configuration file for DOSBox 0.74-3. (Please use the latest version of DOSBox)\n[sdl]\nautolock=false\n[cpu]\ncputype=%s\n[midi]\nmididevice=%s\n[joystick]\njoysticktype=%s\n";
		java.util.function.BiFunction<String, String, org.dbgl.util.ConfTakeover.Row> row = (text, item) -> org.dbgl.util.ConfTakeover.compare(text, target).stream().filter(r -> r.item().equals(item))
				.findFirst().orElse(null);
		String[][] cpu = {{"386_slow", "386"}, {"486_slow", "486"}, {"pentium_slow", "pentium"}}; // as DOSBox Staging 0.83.1 says in its log
		for (String[] c: cpu) {
			org.dbgl.util.ConfTakeover.Row r = row.apply(String.format(classic, c[0], "default", "auto"), "cputype");
			assertEquals(c[1], r.oldValue());
			assertEquals(c[0], r.from()); // told as "earlier ..."
			assertEquals(org.dbgl.util.ConfTakeover.State.OWN, r.state());
		}
		assertEquals("port", row.apply(String.format(classic, "auto", "win32", "auto"), "mididevice") == null ? "port": "changed"); // the same as the new file has: nothing to take over
		String otherPort = target.replace("mididevice = port", "mididevice = mt32");
		org.dbgl.util.ConfTakeover.Row midi = org.dbgl.util.ConfTakeover.compare(String.format(classic, "auto", "alsa", "auto"), otherPort).stream().filter(r -> r.item().equals("mididevice")).findFirst().orElse(null);
		assertEquals("port", midi.oldValue());
		assertEquals("alsa", midi.from());
		org.dbgl.util.ConfTakeover.Row joy = row.apply(String.format(classic, "auto", "default", "none"), "joysticktype");
		assertEquals("disabled", joy.oldValue());
		// "386" DOSBox Staging takes as its own "386", without a word: so does DBGL
		org.dbgl.util.ConfTakeover.Row plain = row.apply(String.format(classic, "386", "default", "auto"), "cputype");
		assertEquals("386", plain.oldValue());
		assertNull(plain.from());
		// output, machine, pcspeaker: what DOSBox Staging uses for them
		String more = "# This is the configuration file for dosbox-staging (0.83.1).\n[sdl]\n# Possible values:\n#\n#   opengl:  x\n#   texture:  x\n#\noutput = texture\n[dosbox]\n# Possible values:\n#\n#   svga_s3:  x\n#   svga_paradise:  x\n#\nmachine = svga_s3\n"
				+ "[speaker]\n# Possible values:\n#\n#   impulse:  x\n#   discrete:  x\n#   none:  x\n#   off:  x\n#\npcspeaker = impulse\n";
		String classic2 = "# This is the configuration file for DOSBox 0.74-3. (Please use the latest version of DOSBox)\n[sdl]\noutput=%s\n[dosbox]\nmachine=vgaonly\n[speaker]\npcspeaker=false\n";
		org.dbgl.util.ConfTakeover.Row nb = org.dbgl.util.ConfTakeover.compare(String.format(classic2, "openglnb"), more).stream().filter(x -> x.item().equals("output")).findFirst().orElse(null);
		assertEquals("opengl", nb.oldValue());
		assertEquals("openglnb", nb.from());
		// "surface" is the default of DOSBox 0.74: an old default is shown as that and not converted
		org.dbgl.util.ConfTakeover.Row surface = org.dbgl.util.ConfTakeover.compare(String.format(classic2, "surface"), more).stream().filter(x -> x.item().equals("output")).findFirst().orElse(null);
		assertEquals(org.dbgl.util.ConfTakeover.State.OLD_DEFAULT, surface.state());
		List<org.dbgl.util.ConfTakeover.Row> rows2 = org.dbgl.util.ConfTakeover.compare(String.format(classic2, "ddraw"), more);
		assertEquals("svga_paradise", rows2.stream().filter(x -> x.item().equals("machine")).findFirst().get().oldValue());
		assertEquals("off", rows2.stream().filter(x -> x.item().equals("pcspeaker")).findFirst().get().oldValue());
		org.dbgl.util.ConfTakeover.Row ddraw = rows2.stream().filter(x -> x.item().equals("output")).findFirst().get();
		assertEquals("ddraw", ddraw.oldValue()); // invalid for DOSBox Staging: not converted, not preselected
		assertEquals(org.dbgl.util.ConfTakeover.State.NOT_LISTED, ddraw.state());
		// a new file that still has the old name keeps it (DOSBox Staging 0.81: "pentium_slow")
		String target081 = "# This is the configuration file for dosbox-staging (0.81.2).\n[cpu]\n# CPU type. Possible values:\n#\n#   auto:  x\n#   386:  x\n#   pentium_slow:  x\n#   pentium:  x\n#\ncputype = auto\n";
		org.dbgl.util.ConfTakeover.Row old081 = org.dbgl.util.ConfTakeover.compare(String.format(classic, "pentium_slow", "default", "auto"), target081).stream().filter(r -> r.item().equals("cputype")).findFirst().orElse(null);
		assertEquals("pentium_slow", old081.oldValue());
		assertNull(old081.from());
		// a value without a twin stays as it is: not among the values of the new file
		org.dbgl.util.ConfTakeover.Row prefetch = row.apply(String.format(classic, "386_prefetch", "default", "auto"), "cputype");
		assertEquals("386_prefetch", prefetch.oldValue());
		assertEquals(org.dbgl.util.ConfTakeover.State.NOT_LISTED, prefetch.state());
		// "autolock = false": shown with mouse_capture, to be set by hand
		org.dbgl.util.ConfTakeover.Row lock = row.apply(String.format(classic, "auto", "default", "auto"), "autolock");
		assertEquals(org.dbgl.util.ConfTakeover.State.MANUAL, lock.state());
		assertEquals("[mouse] mouse_capture", lock.from());
		// from a file of DOSBox Staging itself nothing is renamed: its "386" is the "386" of the new file
		String staging = "# This is the configuration file for dosbox-staging (0.82.2).\n[cpu]\ncputype = 386\n";
		org.dbgl.util.ConfTakeover.Row same = org.dbgl.util.ConfTakeover.compare(staging, target).stream().filter(r -> r.item().equals("cputype")).findFirst().orElse(null);
		assertEquals("386", same.oldValue());
		assertNull(same.from());
	}

	/** alpha 50: the configuration file of a DOSBox version names an older version than its entry */
	@Test
	public void testDosboxConfOlder() throws Exception {
		File dir = Files.createTempDirectory("dbglolder").toFile();
		File conf = new File(dir, "dosbox-staging.conf");
		Files.writeString(conf.toPath(), "# This is the configuration file for dosbox-staging (0.83.0).\n[sdl]\nfullscreen = off\n", StandardCharsets.UTF_8);
		java.util.function.Function<String, Boolean> told = version -> {
			org.dbgl.model.aggregate.DosboxVersion db = DosboxVersionFactory.create("Staging", SettingsService.STAGING_DOSBOX_FAM.title_, version, false, true, false, null, dir.getPath(), "", "", conf.getPath());
			return org.dbgl.util.ProfileChecks.dosboxVersionHints(db).stream().anyMatch(p -> p.key().equals("dosboxconfolder"));
		};
		assertTrue(told.apply("0.83.1"));
		assertFalse(told.apply("0.83.0"));
		assertFalse(told.apply("0.83.0-abc12")); // the same release
		assertFalse(told.apply("0.82.2")); // a newer file under an older entry: put right at the start of DBGL
		assertEquals("0.83.0", org.dbgl.util.ProfileChecks.releaseOf("0.83.0 (git)"));
		assertEquals("2026.10.01", org.dbgl.util.ProfileChecks.releaseOf("2026.10.01 SDL2"));
		assertFalse(told.apply("r4483")); // no release number to compare
	}
	/** alpha 50: the condition of a filter in words */
	@Test
	public void testFilterInWords() {
		org.dbgl.service.ITextService text = org.dbgl.service.TextService.getInstance();
		java.util.Map<String, String> names = java.util.Map.of("GAM.TITLE", "Title", "DEV.NAME", "Developer", "GAM.FAVORITE", "Favorite", "GAM.SETUP", "Setup", "GAM.STATS_LASTRUN", "Last run", "SER.NAME",
			"Series", "GAM.DBVERSION_ID", "DOSBox version", "YR.YEAR", "Year");
		java.util.function.Function<String, String> words = c -> org.dbgl.util.FilterText.readable(c, names::get, id -> id == 3 ? "Staging 0.83": null, text);
		String and = text.get("dialog.filter.read.and"), or = text.get("dialog.filter.read.or");
		assertEquals(text.get("dialog.filter.read.contains", new Object[] {"Title", "JURASSIC"}), words.apply("UPPER(GAM.TITLE) LIKE '%JURASSIC%'"));
		assertEquals(text.get("dialog.filter.read.is", new Object[] {"Developer", "id Software"}), words.apply("DEV.NAME='id Software'"));
		assertEquals(text.get("dialog.filter.read.is", new Object[] {"Developer", "Sid's"}), words.apply("DEV.NAME='Sid''s'"));
		assertEquals(text.get("dialog.filter.read.empty", new Object[] {"Developer"}), words.apply("DEV.NAME=''"));
		assertEquals(text.get("dialog.filter.read.yes", new Object[] {"Favorite"}), words.apply("GAM.FAVORITE=true"));
		assertEquals(text.get("dialog.filter.read.no", new Object[] {"Favorite"}), words.apply("GAM.FAVORITE=false"));
		assertEquals(text.get("dialog.filter.read.notempty", new Object[] {"Setup"}), words.apply("GAM.SETUP<>''"));
		assertEquals(text.get("dialog.filter.read.empty", new Object[] {"Setup"}), words.apply("(GAM.SETUP IS NULL OR GAM.SETUP='')"));
		assertEquals(text.get("dialog.filter.read.empty", new Object[] {"Last run"}), words.apply("GAM.STATS_LASTRUN IS NULL"));
		assertEquals(text.get("dialog.filter.read.on", new Object[] {"Last run", "2026-11-05"}), words.apply("YEAR(GAM.STATS_LASTRUN)=2026 AND MONTH(GAM.STATS_LASTRUN)=11 AND DAYOFMONTH(GAM.STATS_LASTRUN)=5"));
		assertEquals(text.get("dialog.filter.read.selected", new Object[] {"3"}), words.apply("GAM.ID IN (4,17,23)"));
		assertEquals(text.get("dialog.filter.read.is", new Object[] {"Series", "Commander Keen"}), words.apply("GAM.ID IN (SELECT SER.GAME_ID FROM SERIES SER WHERE SER.NAME='Commander Keen')"));
		assertEquals(text.get("dialog.filter.read.is", new Object[] {"DOSBox version", "Staging 0.83"}), words.apply("GAM.DBVERSION_ID=3"));
		assertEquals(text.get("dialog.filter.read.is", new Object[] {"DOSBox version", "8"}), words.apply("GAM.DBVERSION_ID=8")); // not known: its number
		assertEquals(text.get("dialog.filter.read.is", new Object[] {"Year", "1993"}), words.apply("YR.YEAR='1993'"));
		// joined as the window writes them, over several lines
		String a = text.get("dialog.filter.read.is", new Object[] {"Developer", "Apogee"}), b = text.get("dialog.filter.read.is", new Object[] {"Developer", "Sierra"}),
				c = text.get("dialog.filter.read.contains", new Object[] {"Title", "QUEST"});
		assertEquals("(" + a + " " + or + " " + b + ") " + and + " (" + c + ")", words.apply("(DEV.NAME='Apogee'\n\tOR DEV.NAME='Sierra')\nAND\n(UPPER(GAM.TITLE) LIKE '%QUEST%')"));
		// a field without a name for the user keeps its own
		assertEquals(text.get("dialog.filter.read.is", new Object[] {"GAM.CUSTOM9", "5"}), words.apply("GAM.CUSTOM9=5"));
		// "AND" inside a value is a value
		assertEquals(text.get("dialog.filter.read.contains", new Object[] {"Title", "ROCK AND ROLL"}), words.apply("UPPER(GAM.TITLE) LIKE '%ROCK AND ROLL%'"));
		// what this class does not know is not guessed at
		assertNull(words.apply("GAM.STATS_RUNS > 10"));
		assertNull(words.apply("UPPER(GAM.TITLE) LIKE '%DOOM%' AND NOT GAM.FAVORITE=true"));
		assertNull(words.apply("LENGTH(GAM.TITLE) = 4"));
		assertNull(words.apply(""));
		assertNull(words.apply(null));
		assertNull(words.apply("/* dbgl: profiles with problems */"));
	}
	/** alpha 50: the way back from a USB stick: play time, new and changed files; what changed on both sides stays */
	@Test
	public void testStickReturn() throws Exception {
		File base = Files.createTempDirectory("dbglstick").toFile();
		File stick = new File(base, "stick/DBGL"), game = new File(base, "main/games/DOOM"), captures = new File(base, "main/captures/7"), data = new File(base, "main/data");
		File image = new File(base, "main/images/cd.iso");
		for (File d: new File[] {new File(stick, "dosroot/DOOM"), new File(stick, "captures/7"), new File(stick, "dosroot/img"), game, captures, data, image.getParentFile()})
			assertTrue(d.mkdirs());
		long created = System.currentTimeMillis() - 3_600_000L, old = created - 86_400_000L, later = created + 600_000L;
		java.util.function.BiConsumer<File, String> write = (f, s) -> {
			try {
				Files.writeString(f.toPath(), s, StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};
		// as the stick was made: the same files on both sides, older than the stick
		String[] names = {"DOOM.EXE", "SAVE0.DSG", "BOTH.DSG", "HERE.CFG", "MOVED.DAT"};
		for (String n: names) {
			write.accept(new File(game, n), "as it was " + n);
			write.accept(new File(stick, "dosroot/DOOM/" + n), "as it was " + n);
			assertTrue(new File(game, n).setLastModified(old) && new File(stick, "dosroot/DOOM/" + n).setLastModified(old));
		}
		write.accept(image, "image");
		write.accept(new File(stick, "dosroot/img/cd.iso"), "image");
		assertTrue(image.setLastModified(old) && new File(stick, "dosroot/img/cd.iso").setLastModified(old));
		// played on the stick: a saved game changed, a new one, a screenshot
		write.accept(new File(stick, "dosroot/DOOM/SAVE0.DSG"), "played on the stick");
		assertTrue(new File(stick, "dosroot/DOOM/SAVE0.DSG").setLastModified(later));
		write.accept(new File(stick, "dosroot/DOOM/SAVE1.DSG"), "new on the stick");
		write.accept(new File(stick, "captures/7/doom_001.png"), "picture");
		// changed on both sides
		write.accept(new File(stick, "dosroot/DOOM/BOTH.DSG"), "stick");
		write.accept(new File(game, "BOTH.DSG"), "here!");
		assertTrue(new File(stick, "dosroot/DOOM/BOTH.DSG").setLastModified(later) && new File(game, "BOTH.DSG").setLastModified(later + 5000));
		// changed only here: stays
		write.accept(new File(game, "HERE.CFG"), "changed here");
		assertTrue(new File(game, "HERE.CFG").setLastModified(later));
		// the same content with another time (a tool that sets the time when it unpacks): no change
		assertTrue(new File(stick, "dosroot/DOOM/MOVED.DAT").setLastModified(later));

		java.util.Map<Integer, java.util.Map.Entry<org.dbgl.util.StickReturn.Stats, String>> profiles = new java.util.LinkedHashMap<>();
		profiles.put(7, java.util.Map.entry(new org.dbgl.util.StickReturn.Stats(3, 1000, old), "Doom | the first"));
		profiles.put(8, java.util.Map.entry(new org.dbgl.util.StickReturn.Stats(1, 50, old), "Gone"));
		profiles.put(9, java.util.Map.entry(new org.dbgl.util.StickReturn.Stats(0, 0, 0), "Not played"));
		java.util.Map<String, File> paths = new java.util.LinkedHashMap<>();
		paths.put("dosroot/DOOM/", game);
		paths.put("captures/7", captures);
		paths.put("dosroot/img/cd.iso", image);
		assertFalse(org.dbgl.util.StickReturn.isStick(stick));
		Files.write(new File(stick, org.dbgl.util.StickReturn.MANIFEST).toPath(), org.dbgl.util.StickReturn.manifest("stick-1", created, profiles, paths, data, new File(base, "main/games")));
		assertTrue(org.dbgl.util.StickReturn.isStick(stick));
		// what lies in the DOSroot is written from there on, what lies elsewhere with its whole path
		String written = Files.readString(new File(stick, org.dbgl.util.StickReturn.MANIFEST).toPath());
		assertTrue(written.contains("dosroot/DOOM|@dosroot/DOOM") && written.contains("captures/7|") && !written.contains("@dosroot/cd.iso"));
		java.util.Map<Integer, String> here = java.util.Map.of(7, "Doom | the first", 8, "Another game with this number", 9, "Not played");

		// DBGL on the stick has not said anything yet: the files only
		org.dbgl.util.StickReturn.Plan plan = org.dbgl.util.StickReturn.plan(stick, here, data, new File(base, "main/games"));
		assertFalse(plan.stateThere());
		assertTrue(plan.stats().isEmpty());
		assertEquals(3, plan.files().size());

		java.util.Map<Integer, org.dbgl.util.StickReturn.Stats> played = new java.util.LinkedHashMap<>();
		played.put(7, new org.dbgl.util.StickReturn.Stats(5, 4600, later));
		played.put(8, new org.dbgl.util.StickReturn.Stats(2, 80, later));
		played.put(9, new org.dbgl.util.StickReturn.Stats(0, 0, 0));
		org.dbgl.util.StickReturn.writeState(stick, played);
		org.dbgl.util.StickReturn.writeState(data, played); // no stick: nothing is written
		assertFalse(new File(data, org.dbgl.util.StickReturn.STATE).exists());

		plan = org.dbgl.util.StickReturn.plan(stick, here, data, new File(base, "main/games"));
		assertTrue(plan.stateThere());
		assertEquals(1, plan.stats().size());
		assertEquals(7, plan.stats().get(0).id());
		assertEquals(2, plan.stats().get(0).runs());
		assertEquals(3600, plan.stats().get(0).seconds());
		assertEquals("Doom | the first", plan.stats().get(0).title());
		assertEquals(java.util.List.of("Gone"), plan.gone()); // the number is another profile here now
		java.util.Map<String, Boolean> actions = new java.util.TreeMap<>();
		plan.files().forEach(f -> actions.put(f.to().getName(), f.replaces()));
		assertEquals("{SAVE0.DSG=true, SAVE1.DSG=false, doom_001.png=false}", actions.toString());
		assertEquals(java.util.List.of(new File(game, "BOTH.DSG")), plan.conflicts());
		// alpha 50, the preview: groups with an empty line between them; only the play times and the files can be chosen
		// a group per game: the folder of its program and that of its screenshots tell which file belongs to it
		java.util.Map<File, String> gameFolders = java.util.Map.of(game, "Doom | the first", captures, "Doom | the first");
		List<org.dbgl.gui.controls.StickReturnPreview.Row> preview = org.dbgl.gui.controls.StickReturnPreview.rows(plan, org.dbgl.service.TextService.getInstance(), gameFolders, data, new File(base, "main/games"));
		assertEquals("HEADING STAT NEW NEW CHANGED CONFLICT EMPTY HEADING GONE", preview.stream().map(r -> r.kind().name()).collect(java.util.stream.Collectors.joining(" ")));
		assertEquals("Doom | the first", preview.get(0).text());
		assertEquals(4, preview.stream().filter(r -> r.what() != null).count());
		assertTrue(preview.stream().allMatch(r -> (r.size() >= 0) == (r.what() instanceof org.dbgl.util.StickReturn.FileAction)));
		assertEquals("new on the stick".length(), preview.stream().filter(r -> r.text().endsWith("SAVE1.DSG")).findFirst().get().size());
		// nothing known about the games: the files in a group of their own, what was played under its title
		preview = org.dbgl.gui.controls.StickReturnPreview.rows(plan, org.dbgl.service.TextService.getInstance(), java.util.Map.of(), data, new File(base, "main/games"));
		assertEquals("HEADING STAT EMPTY HEADING NEW NEW CHANGED CONFLICT EMPTY HEADING GONE", preview.stream().map(r -> r.kind().name()).collect(java.util.stream.Collectors.joining(" ")));
		// which game: the nearest folder of a game above; else the folder above that holds one game only; not above the stops
		File g = new File(base, "g").getAbsoluteFile();
		java.util.Map<File, String> owners = java.util.Map.of(new File(g, "A/BIN"), "A", new File(g, "B"), "B", new File(g, "B/ADDON"), "B addon");
		assertEquals("A", org.dbgl.gui.controls.StickReturnPreview.gameOf(new File(g, "A/BIN/SUB/x.sav"), owners, g));
		assertEquals("A", org.dbgl.gui.controls.StickReturnPreview.gameOf(new File(g, "A/SAVE/x.sav"), owners, g));
		assertEquals("B addon", org.dbgl.gui.controls.StickReturnPreview.gameOf(new File(g, "B/ADDON/x.sav"), owners, g));
		assertEquals("B", org.dbgl.gui.controls.StickReturnPreview.gameOf(new File(g, "B/x.sav"), owners, g));
		assertNull(org.dbgl.gui.controls.StickReturnPreview.gameOf(new File(g, "x.sav"), owners, g));
		assertNull(org.dbgl.gui.controls.StickReturnPreview.gameOf(new File(g, "C/x.sav"), owners, g));
		assertNull(org.dbgl.gui.controls.StickReturnPreview.gameOf(new File(g, "x.sav"), java.util.Map.of(new File(g, "A"), "A"), g)); // the stop itself is not asked
		assertEquals("captures" + File.separator + "7" + File.separator + "a.png", org.dbgl.gui.controls.StickReturnPreview.shown(new File(g, "captures/7/a.png"), g, new File(g, "dosroot")));
		assertEquals("games" + File.separator + "A" + File.separator + "a.sav", org.dbgl.gui.controls.StickReturnPreview.shown(new File(base, "elsewhere/games/A/a.sav").getAbsoluteFile(), g, new File(base, "elsewhere/games").getAbsoluteFile()));
		assertEquals(java.util.Set.of(7, 8, 9), org.dbgl.util.StickReturn.profilesOf(stick));
		assertTrue(preview.stream().allMatch(r -> (r.what() != null) == java.util.EnumSet.of(org.dbgl.gui.controls.StickReturnPreview.Kind.STAT, org.dbgl.gui.controls.StickReturnPreview.Kind.NEW, org.dbgl.gui.controls.StickReturnPreview.Kind.CHANGED).contains(r.kind())));
		org.dbgl.util.StickReturn.Plan part = org.dbgl.gui.controls.StickReturnPreview.chosen(plan, java.util.List.of(plan.files().get(0)));
		assertTrue(part.stats().isEmpty());
		assertEquals(java.util.List.of(plan.files().get(0)), part.files());
		assertEquals(plan.conflicts(), part.conflicts());
		assertEquals(plan.gone(), part.gone());
		assertEquals(plan.stickId(), part.stickId());
		part = org.dbgl.gui.controls.StickReturnPreview.chosen(plan, java.util.List.of());
		assertTrue(part.stats().isEmpty() && part.files().isEmpty());
		List<Object> all = new java.util.ArrayList<>(plan.stats());
		all.addAll(plan.files());
		part = org.dbgl.gui.controls.StickReturnPreview.chosen(plan, all);
		assertEquals(plan.stats(), part.stats());
		assertEquals(plan.files(), part.files());

		File backup = new File(data, "backups/stick-return-test");
		assertTrue(org.dbgl.util.StickReturn.copy(plan, backup).isEmpty());
		assertEquals("played on the stick", Files.readString(new File(game, "SAVE0.DSG").toPath()));
		assertEquals("new on the stick", Files.readString(new File(game, "SAVE1.DSG").toPath()));
		assertEquals("picture", Files.readString(new File(captures, "doom_001.png").toPath()));
		assertEquals("here!", Files.readString(new File(game, "BOTH.DSG").toPath()));
		assertEquals("changed here", Files.readString(new File(game, "HERE.CFG").toPath()));
		// the file that was replaced is kept, under its path
		File kept = new File(backup, org.dbgl.util.StickReturn.backupName(new File(game, "SAVE0.DSG")));
		assertEquals("as it was SAVE0.DSG", Files.readString(kept.toPath()));
		assertEquals("C/Games/SAVE.DSG", org.dbgl.util.StickReturn.backupName(new File("C:\\Games\\SAVE.DSG")).replaceFirst("^.*?C/", "C/"));
		org.dbgl.util.StickReturn.remember(plan, data, java.util.List.of(7));

		// again: nothing is added twice, the files are the same now; the conflict is still told
		plan = org.dbgl.util.StickReturn.plan(stick, here, data, new File(base, "main/games"));
		assertTrue(plan.stats().isEmpty());
		assertTrue(plan.files().isEmpty());
		assertEquals(1, plan.conflicts().size());
		// played on: only what came since
		played.put(7, new org.dbgl.util.StickReturn.Stats(6, 5000, later + 1000));
		org.dbgl.util.StickReturn.writeState(stick, played);
		plan = org.dbgl.util.StickReturn.plan(stick, here, data, new File(base, "main/games"));
		assertEquals(1, plan.stats().get(0).runs());
		assertEquals(400, plan.stats().get(0).seconds());
		// a folder that is no stick
		assertFalse(org.dbgl.util.StickReturn.isStick(base));
		assertNull(org.dbgl.util.StickReturn.Stats.of("nonsense"));
		org.apache.commons.io.FileUtils.deleteQuietly(base);
	}
	/** alpha 50: which program starts a game and which one sets it up, for the wizard */
	@Test
	public void testGameFileDetection() {
		java.util.function.BiFunction<String, String[], String> main = (title, names) -> {
			List<File> files = java.util.Arrays.stream(names).map(n -> new File("C:/tmp/game/" + n)).collect(java.util.stream.Collectors.toList());
			int i = org.dbgl.util.DosGameUtils.findMostLikelyMainIndex(title, files);
			return i < 0 ? null: names[i];
		};
		java.util.function.Function<String[], String> setup = names -> {
			List<File> files = java.util.Arrays.stream(names).map(n -> new File("C:/tmp/game/" + n)).collect(java.util.stream.Collectors.toList());
			int i = org.dbgl.util.DosGameUtils.findSetupIndex(files);
			return i < 0 ? null: names[i];
		};
		assertEquals("KEEN4E.EXE", main.apply("Commander Keen 4: Secret of the Oracle", new String[] {"CATALOG.EXE", "KEEN4E.EXE", "ORDER.FRM.EXE"}));
		assertEquals("UW.EXE", main.apply("Ultima Underworld: The Stygian Abyss", new String[] {"UINSTALL.EXE", "UW.EXE", "UWSOUND.EXE", "DATA/CNV.EXE"}));
		assertEquals("DOOM2.EXE", main.apply("Doom II", new String[] {"DM.EXE", "DOOM2.EXE", "DWANGO.EXE", "IPXSETUP.EXE", "SERSETUP.EXE", "SETUP.EXE"}));
		assertEquals("MONKEY.EXE", main.apply("The Secret of Monkey Island", new String[] {"INSTALL.EXE", "MONKEY.EXE", "IMUSE.EXE"}));
		assertEquals("WAR2.EXE", main.apply("Warcraft II: Tides of Darkness", new String[] {"DOS4GW.EXE", "SETUP.EXE", "WAR2.EXE", "WAR2DAT.EXE"}));
		assertEquals("SC2000.EXE", main.apply("SimCity 2000", new String[] {"DOS4GW.EXE", "INSTALL.EXE", "SC2000.EXE", "WILLTV.EXE"}));
		assertEquals("SQ4.BAT", main.apply("Space Quest IV", new String[] {"INSTALL.EXE", "SCIV.EXE", "SIERRA.COM", "SQ4.BAT"}));
		assertEquals("TENTACLE.EXE", main.apply("Day of the Tentacle", new String[] {"INSTALL.EXE", "TENTACLE.EXE", "DOSBOX.EXE", "UNINS000.EXE"}));
		assertEquals("DOTT.EXE", main.apply("Day of the Tentacle", new String[] {"DOTT.EXE", "IMUSE.EXE"}));
		assertEquals("UFO.BAT", main.apply("X-COM: UFO Defense", new String[] {"GEOSCAPE.EXE", "GO.BAT", "SETUP.EXE", "TACTICAL.EXE", "UFO.BAT"}));
		assertEquals("PRINCE.EXE", main.apply("Prince of Persia", new String[] {"PRINCE.EXE"}));
		// the same name at the top and in a folder: the one at the top; a program before a batch file of the same name
		assertEquals("DOOM.EXE", main.apply("Doom", new String[] {"OLD/DOOM.EXE", "DOOM.EXE", "DOOM.BAT"}));
		// nothing but helpers: one of them, not nothing
		assertEquals("SETUP.EXE", main.apply("Some Game", new String[] {"SETUP.EXE"}));
		assertNull(main.apply("Some Game", new String[] {}));
		// the title run together and cut off after eight letters
		assertEquals("SIMCITY2.EXE", main.apply("SimCity 2000", new String[] {"README.EXE", "SIMCITY2.EXE", "VESA.EXE"}));

		// set up: in capitals too, SETUP before INSTALL whichever comes first
		assertEquals("SETUP.EXE", setup.apply(new String[] {"DOOM2.EXE", "INSTALL.EXE", "SETUP.EXE"}));
		assertEquals("setup.exe", setup.apply(new String[] {"install.exe", "setup.exe"}));
		assertEquals("INSTALL.EXE", setup.apply(new String[] {"MONKEY.EXE", "INSTALL.EXE"}));
		assertEquals("UWSOUND.EXE", setup.apply(new String[] {"UINSTALL.EXE", "UW.EXE", "UWSOUND.EXE"}));
		assertEquals("SETUP.EXE", setup.apply(new String[] {"SUB/SETUP.EXE", "SETUP.EXE"}));
		assertNull(setup.apply(new String[] {"KEEN4E.EXE", "CATALOG.EXE"}));
		assertEquals(java.util.List.of("simcity", "2000"), org.dbgl.util.DosGameUtils.titleWords("SimCity 2000"));
		assertEquals(java.util.List.of("space", "quest", "4"), org.dbgl.util.DosGameUtils.titleWords("Space Quest IV"));

		// DOSBox 0.62 to 0.70 do not name themselves in their file
		String c063 = "[sdl]\nfullscreen=false\nfullfixed=false\n[midi]\nmpu401=true\ndevice=default\n[modem]\nmodem=false\n", c065 = "[sdl]\nfullscreen=false\n[midi]\ndevice=default\n[bios]\njoysticktype=2axis\n",
				c070 = "[sdl]\nfullscreen=false\n[midi]\ndevice=default\n[joystick]\njoysticktype=auto\n", c074 = "[sdl]\nfullscreen=false\n[midi]\nmididevice=default\n[joystick]\njoysticktype=auto\n";
		assertEquals("0.63", org.dbgl.util.ConfTakeover.classicByContent(c063));
		assertEquals("0.65", org.dbgl.util.ConfTakeover.classicByContent(c065));
		assertEquals("0.70", org.dbgl.util.ConfTakeover.classicByContent(c070));
		assertNull(org.dbgl.util.ConfTakeover.classicByContent(c074)); // a later one without its comments: not guessed at
		assertNull(org.dbgl.util.ConfTakeover.classicByContent("[sdl]\noutput=opengl\n"));
		assertEquals("1024x768", org.dbgl.util.ConfTakeover.shipped("dosbox-0.63").get("sdl\nfullresolution"));
		// "version v0.79.1"
		assertEquals("0.79.1", org.dbgl.util.DosboxVersionDetector.parse("2026-11-15 21:52:04.072 | dosbox-staging version v0.79.1").version_);
		assertEquals("0.75.0", org.dbgl.util.DosboxVersionDetector.parse("dosbox-staging version 0.75.0").version_);
		// the programs of DOSBox 0.62 to 0.73: no version in the name of the mapper file, but in the greeting of the modem
		String old065 = "x\0DOSBox 0.65\0" + "0.65\0mapper.txt\0Modem compiled for DosBox version 0.65\0";
		org.dbgl.util.DosboxVersionDetector.Result r065 = org.dbgl.util.DosboxVersionDetector.scanClassicExecutableText(old065);
		assertEquals("Official 0.65", r065.family_ + " " + r065.version_);
		assertEquals("0.63", org.dbgl.util.DosboxVersionDetector.scanClassicExecutableText(old065.replace("0.65", "0.62")).version_); // the settings of 0.63
		assertEquals("0.73", org.dbgl.util.DosboxVersionDetector.scanClassicExecutableText(old065.replace("0.65", "0.73")).version_);
		assertNull(org.dbgl.util.DosboxVersionDetector.scanClassicExecutableText(old065.replace("mapper.txt", "other.txt")));
		assertNull(org.dbgl.util.DosboxVersionDetector.scanClassicExecutableText(old065.replace("0.65", "0.99")));
		assertEquals("0.74-3", org.dbgl.util.DosboxVersionDetector.scanClassicExecutableText("DOSBox\0mapper-0.74-3.map\0Modem compiled for DosBox version 0.74-3\0").version_);
		// what DOSBox Staging uses for a value of DOSBox 0.74
		assertEquals("svga_paradise", org.dbgl.util.ConfTakeover.stagingValueFor("dosbox", "machine", "VGAONLY"));
		assertNull(org.dbgl.util.ConfTakeover.stagingValueFor("sdl", "output", "ddraw"));
	}

	/**
	 * alpha 50: settings that do not go together, what the game itself is set to against the profile, a CD image nobody
	 * mounts, and the games with a documented need.
	 */
	@Test
	public void testGameHints() throws Exception {
		java.util.function.Function<String, Configuration> conf = text -> {
			try {
				Configuration c = new Configuration();
				c.loadDataWithAutoexec(GenericTextService.getInstance(), text, null, null);
				return c;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};
		java.util.function.Function<List<org.dbgl.util.ProfileChecks.Problem>, String> told = list -> list.stream()
				.map(pr -> pr.key() + java.util.Arrays.toString(pr.args())).collect(java.util.stream.Collectors.joining(" | "));
		String defaults = "[sblaster]\nsbtype=sb16\nsbbase=220\nirq=7\ndma=1\nhdma=5\n[gus]\ngus=false\ngusbase=240\ngusirq=5\ngusdma=3\n[cpu]\ncore=auto\ncputype=auto\n[midi]\nmididevice=default\n";

		// the two cards on the same IRQ, DMA channel, port: only with both switched on
		assertEquals("", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(defaults), true)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(defaults.replace("irq=7", "irq=5")), true))); // the GUS is off
		String gusOn = defaults.replace("gus=false", "gus=true");
		assertEquals("", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(gusOn), true)));
		assertEquals("deviceconflict[[sblaster] irq, [gus] gusirq, 5]", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(gusOn.replace("irq=7", "irq=5")), true)));
		assertEquals("deviceconflict[[sblaster] hdma, [gus] gusdma, 5]", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(gusOn.replace("gusdma=3", "gusdma=5")), true)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(gusOn.replace("gusdma=3", "gusdma=5").replace("sb16", "sbpro2")), true))); // no high DMA
		assertEquals("deviceconflict[[sblaster] sbbase, [gus] gusbase, 240]", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(gusOn.replace("sbbase=220", "sbbase=240")), true)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(gusOn.replace("irq=7", "irq=5").replace("sb16", "none")), true)));
		// DOSBox 0.74 quits: "prefetch queue emulation requires the normal core setting."
		String prefetch = defaults.replace("cputype=auto", "cputype=386_prefetch");
		assertEquals("", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(prefetch), true)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(prefetch.replace("core=auto", "core=normal")), true)));
		assertEquals("prefetchcore[[cpu] core, 386_prefetch, dynamic]", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(prefetch.replace("core=auto", "core=dynamic")), true)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(prefetch.replace("core=auto", "core=dynamic")), false))); // only known of DOSBox 0.74

		// the files the setup of a game writes
		org.dbgl.util.GameHints.Sound dig = org.dbgl.util.GameHints.milesIni("DIG.INI",
			";\r\n;AIL 3 digital audio configuration file\r\n;\r\n\r\nDRIVER      SB16.DIG\r\nDEVICE      Creative Labs Sound Blaster 16 or AWE32\r\nIO_ADDR     220h\r\nIRQ         5\r\nDMA_8_bit   1\r\nDMA_16_bit  5\r\n");
		assertEquals("sb A220 I5 D1 H5", dig.device() + " " + dig.blaster());
		assertEquals("sb A220 I7", org.dbgl.util.GameHints.milesIni("DIG.INI", "DRIVER SBPRO2.DIG\nIO_ADDR 220h\nIRQ 7\nDMA_8_bit -1\n").device() + " "
				+ org.dbgl.util.GameHints.milesIni("DIG.INI", "DRIVER SBPRO2.DIG\nIO_ADDR 220h\nIRQ 7\nDMA_8_bit -1\n").blaster());
		assertEquals("gus", org.dbgl.util.GameHints.milesIni("DIG.INI", "DRIVER ULTRA.DIG\n").device());
		assertEquals("mt32", org.dbgl.util.GameHints.milesIni("MDI.INI", "DRIVER      MT32MPU.MDI\nDEVICE Roland MT-32 or compatible with MPU-401\nIO_ADDR 330h\n").device());
		assertNull(org.dbgl.util.GameHints.milesIni("MDI.INI", "DRIVER SBAWE32.MDI\nIO_ADDR 620h\n")); // music on the card: nothing to compare
		assertNull(org.dbgl.util.GameHints.milesIni("DIG.INI", "; nothing chosen\n"));
		org.dbgl.util.GameHints.Sound doom = org.dbgl.util.GameHints.doomCfg("DEFAULT.CFG", "mouse_sensitivity\t\t5\nsnd_musicdevice\t\t3\nsnd_sfxdevice\t\t3\nsnd_sbport\t\t544\nsnd_sbirq\t\t5\nsnd_sbdma\t\t1\nsnd_mport\t\t816\n");
		assertEquals("sb ", doom.device() + " " + doom.blaster()); // its port, IRQ and DMA are not compared: the game looks for the card itself
		assertEquals("gus", org.dbgl.util.GameHints.doomCfg("DEFAULT.CFG", "snd_sfxdevice 5\n").device());
		assertNull(org.dbgl.util.GameHints.doomCfg("DEFAULT.CFG", "snd_sfxdevice 1\nsnd_sbirq 5\n")); // the PC speaker
		assertEquals("mt32", org.dbgl.util.GameHints.sierraCfg("RESOURCE.CFG", "videoDrv = VGA320.DRV\r\nsoundDrv = MTBLAST.DRV\r\naudioDrv = AUDBLAST.DRV\r\n").device());
		assertNull(org.dbgl.util.GameHints.sierraCfg("RESOURCE.CFG", "soundDrv = ADL.DRV\n"));
		assertNull(org.dbgl.util.GameHints.sierraCfg("RESOURCE.CFG", "soundDrv = MT540.DRV\n")); // a Casio, not the MT-32
		assertEquals("A220 I5 D1 H5", org.dbgl.util.GameHints.blasterLine("x", "@SET BLASTER=A220 I5 D1 H5 P330 T6").blaster());
		assertNull(org.dbgl.util.GameHints.blasterLine("x", "set path=c:\\blaster"));
		assertNull(org.dbgl.util.GameHints.blasterLine("x", "rem set blaster=A220 I5"));

		// against the profile: only what both sides name is compared, the high DMA only with a Sound Blaster 16
		assertEquals("soundmismatch[DIG.INI, A220 I5 D1 H5, A220 I7 D1 H5, , 5, , ]", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(dig), conf.apply(defaults), false)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(dig), conf.apply(defaults.replace("irq=7", "irq=5")), false)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(dig), conf.apply(defaults.replace("irq=7", "irq=5").replace("hdma=5", "hdma=7").replace("sb16", "sbpro2")), false)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(doom), conf.apply(defaults), false)));
		assertEquals("sounddeviceoff[DEFAULT.CFG, Sound Blaster, [sblaster] sbtype = none]", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(doom), conf.apply(defaults.replace("sb16", "none")), false)));
		assertEquals("sounddeviceoff[DIG.INI, Sound Blaster, [sblaster] sbtype = none]", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(dig, doom), conf.apply(defaults.replace("sb16", "none")), false)));
		org.dbgl.util.GameHints.Sound gus = org.dbgl.util.GameHints.doomCfg("DEFAULT.CFG", "snd_sfxdevice 5\n"), mt32 = org.dbgl.util.GameHints.sierraCfg("RESOURCE.CFG", "soundDrv=MT32.DRV\n");
		assertEquals("sounddeviceoff[DEFAULT.CFG, Gravis Ultrasound, [gus] gus = false]", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(gus), conf.apply(defaults), false)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(gus), conf.apply(gusOn), false)));
		assertEquals("gamewantsmt32[RESOURCE.CFG, [midi] mididevice = default]", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(mt32), conf.apply(defaults), true)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(mt32), conf.apply(defaults), false))); // DOSBox 0.74: the device is outside
		assertEquals("", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(mt32), conf.apply(defaults.replace("mididevice=default", "mididevice=mt32")), true)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(dig), conf.apply("[cpu]\ncore=auto\n"), true))); // a version without these settings

		// as the setup of Heroes of Might and Magic II writes it: capitals, -1 for "found by the game itself" - nothing to compare
		org.dbgl.util.GameHints.Sound homm2 = org.dbgl.util.GameHints.milesIni("SOUND\\DIG.INI",
			";\n;Miles Sound System V3.50E of 24-Sep-96\n;\nDEVICE Creative Labs Sound Blaster 16 or AWE32\nDRIVER SB16.DIG\nIO_ADDR 220h\nIRQ -1\nDMA_8_BIT -1\nDMA_16_BIT -1\n");
		assertEquals("A220", homm2.blaster());
		assertEquals("", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(homm2), conf.apply(defaults), false)));
		// a value DOSBox does not take (IRQ 2): told, without a solution that would set it
		assertEquals("soundmismatch[DIG.INI, A220 I2 D1, A220 I7 D1]", told.apply(org.dbgl.util.GameHints.soundProblems(
			List.of(org.dbgl.util.GameHints.milesIni("DIG.INI", "DRIVER SBPRO2.DIG\nIO_ADDR 220h\nIRQ 2\nDMA_8_bit 1\n")), conf.apply(defaults), false)));
		// a line that sets BLASTER or ULTRASND: DOSBox sets them itself; a line of the profile can go, one of the game is told
		org.dbgl.util.GameHints.Sound ownLine = org.dbgl.util.GameHints.blasterLine("[autoexec] set BLASTER=A220 I5 D1 T4", "  set BLASTER=A220 I5 D1 T4"),
				batLine = org.dbgl.util.GameHints.blasterLine("GO.BAT: SET BLASTER=A240 I7 D1", "SET BLASTER=A240 I7 D1"),
				ultra = org.dbgl.util.GameHints.blasterLine("[autoexec] SET ULTRASND=220,1,1,11,11", "SET ULTRASND=220,1,1,11,11");
		assertEquals("soundvariable[set BLASTER=A220 I5 D1 T4, A220 I5 D1, A220 I7 D1, BLASTER, profile] | soundvariable[GO.BAT: SET BLASTER=A240 I7 D1, A240 I7 D1, A220 I7 D1, BLASTER, ]",
			told.apply(org.dbgl.util.GameHints.soundProblems(List.of(ownLine, batLine), conf.apply(defaults), false)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(ownLine), conf.apply(defaults.replace("irq=7", "irq=5")), false))); // the same values: harmless
		assertEquals("", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(ownLine), conf.apply(defaults.replace("sb16", "none")), false)));
		assertEquals("gus A220 I11 D1", ultra.device() + " " + ultra.blaster());
		assertEquals("", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(ultra), conf.apply(defaults), false))); // the GUS is off
		assertEquals("soundvariable[SET ULTRASND=220,1,1,11,11, A220 I11 D1, A240 I5 D3, ULTRASND, profile]", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(ultra), conf.apply(gusOn), false)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(org.dbgl.util.GameHints.blasterLine("x", "set ultrasnd=240,3,3,5,5")), conf.apply(gusOn), false)));
		assertEquals("sounddeviceoff[DIG.INI, Sound Blaster, [sblaster] sbtype = off]", told.apply(org.dbgl.util.GameHints.soundProblems(List.of(dig), conf.apply(defaults.replace("sb16", "off")), false)));
		// the names DOSBox 0.72 and older have: "base" for both cards (0.63), "irq1" and "dma1" of the Gravis Ultrasound
		String old063 = "[sblaster]\ntype=sb16\nsbtype=sb16\nbase=220\nirq=5\ndma=1\nhdma=5\n[gus]\ngus=true\nbase=240\nirq1=5\nirq2=5\ndma1=3\ndma2=3\n";
		assertEquals("deviceconflict[[sblaster] irq, [gus] irq1, 5]", told.apply(org.dbgl.util.GameHints.conflicts(conf.apply(old063), true)));
		assertEquals("soundmismatch[DIG.INI, A240 I5 D1 H5, A220 I5 D1 H5, 240, , , ]", told.apply(org.dbgl.util.GameHints.soundProblems(
			List.of(org.dbgl.util.GameHints.milesIni("DIG.INI", "DRIVER SB16.DIG\nIO_ADDR 240h\nIRQ 5\nDMA_8_bit 1\nDMA_16_bit 5\n")), conf.apply(old063), false)));
		assertEquals("base", org.dbgl.util.GameHints.itemOf(conf.apply(old063), "sblaster", "sbbase", "base"));
		assertEquals("sbbase", org.dbgl.util.GameHints.itemOf(conf.apply(defaults), "sblaster", "sbbase", "base"));

		// the files of a game: DIG.INI in SOUND, the BLASTER variable in the batch file, a CD image nobody mounts
		File game = Files.createTempDirectory("dbgl-hints").toFile();
		try {
			File sound = new File(game, "Sound");
			assertTrue(sound.mkdir());
			Files.writeString(new File(sound, "dig.ini").toPath(), "DRIVER SB16.DIG\r\nIO_ADDR 220h\r\nIRQ 5\r\nDMA_8_bit 1\r\nDMA_16_bit 5\r\n", StandardCharsets.ISO_8859_1);
			File bat = new File(game, "GO.BAT");
			Files.writeString(bat.toPath(), "@echo off\r\nset blaster=A240 I7 D1\r\ngame.exe\r\n", StandardCharsets.ISO_8859_1);
			List<org.dbgl.util.GameHints.Sound> found = org.dbgl.util.GameHints.soundOf(bat);
			assertEquals("Sound" + File.separator + "dig.ini=A220 I5 D1 H5, GO.BAT: set blaster=A240 I7 D1=A240 I7 D1",
				found.stream().map(s -> s.file() + "=" + s.blaster()).collect(java.util.stream.Collectors.joining(", ")));
			assertNull(org.dbgl.util.GameHints.unusedCdImage(bat, List.of(), List.of()));
			Files.writeString(new File(game, "game.bin").toPath(), "x", StandardCharsets.ISO_8859_1);
			// by what is in the file, not by its name: games have files of their own with these endings (an ".ins" of 25 bytes)
			Files.writeString(new File(game, "SETUP.INS").toPath(), "C:\\GAME\\DATA\r\nSOUND=1\r\n", StandardCharsets.ISO_8859_1);
			Files.writeString(new File(game, "DATA.ISO").toPath(), "not an image", StandardCharsets.ISO_8859_1);
			Files.writeString(new File(game, "X.MDS").toPath(), "something", StandardCharsets.ISO_8859_1);
			Files.writeString(new File(game, "X.CHD").toPath(), "something", StandardCharsets.ISO_8859_1);
			Files.writeString(new File(game, "X.CCD").toPath(), "some settings of the game", StandardCharsets.ISO_8859_1);
			Files.writeString(new File(game, "EMPTY.CUE").toPath(), "REM nothing in it\r\n", StandardCharsets.ISO_8859_1);
			assertNull(org.dbgl.util.GameHints.unusedCdImage(bat, List.of(), List.of()));
			Files.writeString(new File(game, "GOG.INS").toPath(), "FILE \"game.gog\" BINARY\r\n  TRACK 01 MODE1/2352\r\n    INDEX 01 00:00:00\r\n", StandardCharsets.ISO_8859_1);
			assertEquals("GOG.INS", org.dbgl.util.GameHints.unusedCdImage(bat, List.of(), List.of()));
			assertTrue(new File(game, "GOG.INS").delete());
			byte[] iso = new byte[17 * 2048];
			System.arraycopy("\u0001CD001".getBytes(StandardCharsets.ISO_8859_1), 0, iso, 16 * 2048, 6);
			Files.write(new File(game, "REAL.ISO").toPath(), iso);
			assertEquals("REAL.ISO", org.dbgl.util.GameHints.unusedCdImage(bat, List.of(), List.of()));
			assertTrue(new File(game, "REAL.ISO").delete());
			Files.writeString(new File(game, "GAME.CUE").toPath(), "FILE \"game.bin\" BINARY\r\n  TRACK 01 MODE1/2352\r\n", StandardCharsets.ISO_8859_1);
			assertEquals("GAME.CUE", org.dbgl.util.GameHints.unusedCdImage(bat, List.of(), List.of("echo hello")));
			assertNull(org.dbgl.util.GameHints.unusedCdImage(bat, List.of(), List.of("imgmount D \"x.iso\" -t iso")));
			assertNull(org.dbgl.util.GameHints.unusedCdImage(bat, List.of(), List.of("mount D \"D:\\\" -t cdrom")));
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(game);
		}

		// games with a documented need, by title or by program
		assertEquals("ultima vii the black gate", org.dbgl.util.GameHints.titleKey("Ultima VII: The Black Gate"));
		assertEquals("contra", org.dbgl.util.GameHints.titleKey("Contra (1988)"));
		assertEquals("quake", org.dbgl.util.GameHints.titleKey(" Quake [CD] (id Software) "));
		assertEquals("the terminator", org.dbgl.util.GameHints.titleKey("Terminator, The"));
		assertEquals("x men madness in murderworld", org.dbgl.util.GameHints.titleKey("X-Men: Madness in Murderworld"));
		Configuration normal = conf.apply("[dosbox]\nmemsize=16\n[cpu]\ncore=auto\ncputype=auto\ncycles=auto\n[dos]\nems=true\n");
		assertEquals("gameadvice[[dos] ems, Ultima VII: The Black Gate, [dos] ems = false, dosbox.com, Wiki, [dos] ems = true, false]",
			told.apply(org.dbgl.util.GameHints.advice("Ultima VII: The Black Gate", null, null, normal)));
		assertEquals("gameadvice[[dos] ems, Ultima VII: The Black Gate, [dos] ems = false, dosbox.com, Wiki, [dos] ems = true, false]",
			told.apply(org.dbgl.util.GameHints.advice("U7", new File("C:/games/u7/ULTIMA7.COM"), null, normal)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.advice("Ultima VII Part Two: Serpent Isle", new File("C:/games/si/SERPENT.COM"), null, normal)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.advice("Ultima VII", null, null, conf.apply("[dos]\nems=false\n"))));
		assertEquals("", told.apply(org.dbgl.util.GameHints.advice("Ultima VII", null, null, conf.apply("[dos]\nems=off\n")))); // as DOSBox Staging writes it
		assertEquals("gameadvice[[dosbox] memsize, Quake, [dosbox] memsize = 32, dosbox-staging.org, Manual, [dosbox] memsize = 16, 32]",
			told.apply(org.dbgl.util.GameHints.advice("Quake", null, null, normal)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.advice("Quake", null, null, conf.apply("[dosbox]\nmemsize=63\n"))));
		assertEquals("", told.apply(org.dbgl.util.GameHints.advice("Quake II", null, null, normal))); // another game
		assertTrue(told.apply(org.dbgl.util.GameHints.advice("Quake (1996)", null, null, normal)).startsWith("gameadvice[[dosbox] memsize, Quake,"));
		assertTrue(told.apply(org.dbgl.util.GameHints.advice("Terminator, The", null, null, normal)).startsWith("gameadvice[[cpu] cputype, The Terminator,"));
		assertEquals("", told.apply(org.dbgl.util.GameHints.advice("Terminator 2: Judgment Day", null, null, normal)));
		assertEquals(Character.valueOf('D'), org.dbgl.util.GameHints.freeDrive(List.of(), List.of()));
		assertEquals("gameadvice[[cpu] cputype, Contra, [cpu] cputype = 386_prefetch, dosbox-staging.org, Manual, [cpu] cputype = auto, 386_prefetch]",
			told.apply(org.dbgl.util.GameHints.advice("Contra", null, null, normal)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.advice("Contra", null, null, conf.apply("[cpu]\ncputype=386_prefetch\n"))));
		assertEquals("gameadvice[[cpu] cycles, Alley Cat, [cpu] cycles = 140 ... 300, dosbox-staging.org, Manual, [cpu] cycles = auto, ]",
			told.apply(org.dbgl.util.GameHints.advice("Alley Cat", null, null, normal)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.advice("Alley Cat", null, null, conf.apply("[cpu]\ncycles=fixed 250\n"))));
		assertEquals("", told.apply(org.dbgl.util.GameHints.advice("Alley Cat", null, null, conf.apply("[cpu]\ncycles=170\n"))));
		// DOSBox Staging 0.82 and later: "cpu_cycles", unless the profile itself still says "cycles"
		Configuration staging = conf.apply("[cpu]\ncpu_cycles=3000\n"), ownCycles = conf.apply("[cpu]\ncycles=200\n"), both = conf.apply("[cpu]\ncpu_cycles=3000\ncycles=200\n");
		assertEquals("gameadvice[[cpu] cpu_cycles, Sopwith, [cpu] cpu_cycles = 140 ... 300, dosbox-staging.org, Manual, [cpu] cpu_cycles = 3000, ]",
			told.apply(org.dbgl.util.GameHints.advice("Sopwith", null, conf.apply("[cpu]\ncore=auto\n"), staging)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.advice("Sopwith", null, ownCycles, both)));
		assertEquals("", told.apply(org.dbgl.util.GameHints.advice("Some other game", new File("C:/games/x/GAME.EXE"), null, normal)));
	}

	/**
	 * alpha 50, before the release: a DOSBox version DBGL does not know yet and the report on it; the logs and what may be
	 * deleted among them; the folders of a USB stick brought back among the backups; the preview of bringing a stick back; the
	 * note on Java 21 at the top of the newest part of the changelog.
	 */
	@Test
	public void testFineTuningAlpha50() throws Exception {
		// a version newer than the newest one of its family is "newer"; one not in the list but older is not
		assertTrue(org.dbgl.util.DosboxSupport.compare("0.84.0", "0.83.1") > 0);
		assertTrue(org.dbgl.util.DosboxSupport.compare("0.83.10", "0.83.9") > 0);
		assertEquals(0, org.dbgl.util.DosboxSupport.compare("0.83", "0.83.0"));
		assertTrue(org.dbgl.util.DosboxSupport.compare("2027.01.01", "2026.10.01") > 0);
		assertTrue(org.dbgl.util.DosboxSupport.compare("0.74-4", "0.74-3") > 0);
		assertEquals(org.dbgl.util.DosboxSupport.Status.KNOWN, org.dbgl.util.DosboxSupport.status("DOSBox Staging", "0.83.1"));
		assertEquals(org.dbgl.util.DosboxSupport.Status.NEWER, org.dbgl.util.DosboxSupport.status("DOSBox Staging", "0.84.0"));
		assertEquals(org.dbgl.util.DosboxSupport.Status.NEWER, org.dbgl.util.DosboxSupport.status("dosbox staging", "1.0.0"));
		assertEquals(org.dbgl.util.DosboxSupport.Status.OTHER, org.dbgl.util.DosboxSupport.status("DOSBox Staging", "0.80.1")); // older than the list
		assertEquals(org.dbgl.util.DosboxSupport.Status.NEWER, org.dbgl.util.DosboxSupport.status("Official", "0.74-4"));
		assertEquals(org.dbgl.util.DosboxSupport.Status.NEWER, org.dbgl.util.DosboxSupport.status("DOSBox-X", "2031.01.01"));
		assertEquals(org.dbgl.util.DosboxSupport.Status.OTHER, org.dbgl.util.DosboxSupport.status("Some other DOSBox", "9.9"));
		assertEquals(org.dbgl.util.DosboxSupport.Status.OTHER, org.dbgl.util.DosboxSupport.status("DOSBox Staging", ""));
		assertEquals("0.83.1", org.dbgl.util.DosboxSupport.newestKnown("DOSBox Staging"));
		assertEquals("0.74-3", org.dbgl.util.DosboxSupport.newestKnown("Official"));
		assertEquals("staging-0.83.1", org.dbgl.util.DosboxSupport.listOf("DOSBox Staging"));
		assertNotNull(Dbgl0102Tests.class.getResourceAsStream("/confdefaults/" + org.dbgl.util.DosboxSupport.listOf("DOSBox Staging") + ".txt"));
		assertNotNull(Dbgl0102Tests.class.getResourceAsStream("/confdefaults/" + org.dbgl.util.DosboxSupport.listOf("Official") + ".txt"));
		assertNotNull(Dbgl0102Tests.class.getResourceAsStream("/confdefaults/" + org.dbgl.util.DosboxSupport.listOf("DOSBox-X") + ".txt"));
		// the report: new, gone, another value - and no [autoexec]
		java.util.Map<String, String> conf = org.dbgl.util.DosboxSupport.settingsOf("# a comment\r\n[sdl]\r\noutput = opengl\r\nfancy_new = on\r\n[newsection]\r\nthing=1\r\n[cpu]\r\ncore = normal\r\n[autoexec]\r\nmount c \"C:\\Users\\someone\\games\"\r\n");
		assertEquals("{sdl|output=opengl, sdl|fancy_new=on, newsection|thing=1, cpu|core=normal}", conf.toString());
		java.util.Map<String, String> known = new java.util.LinkedHashMap<>();
		known.put("sdl|output", "opengl");
		known.put("sdl|old_one", "x");
		known.put("cpu|core", "auto || dynamic");
		String report = org.dbgl.util.DosboxSupport.report("0.101 alpha 50", "DOSBox Staging", "0.84.0", "0.83.1", "staging-0.83.1", known, conf);
		assertTrue(report, report.contains("DOSBox version:   DOSBox Staging 0.84.0") && report.contains("Treated as:       DOSBox Staging 0.83.1"));
		assertTrue(report, report.contains("New sections: newsection") && report.contains("New settings (2):\n  [sdl] fancy_new = on\n  [newsection] thing = 1"));
		assertTrue(report, report.contains("Settings that are gone (1):\n  [sdl] old_one"));
		assertTrue(report, report.contains("[cpu] core = normal    (default known to DBGL: auto or dynamic)"));
		assertFalse(report, report.contains("someone") || report.contains("autoexec"));
		assertTrue(org.dbgl.util.DosboxSupport.report("x", "DOSBox Staging", "0.84.0", new File("no-such-file.conf")).contains("could not be read"));

		File logs = Files.createTempDirectory("dbgl-logs").toFile();
		try {
			// the logs of two profiles, a diagnostic file of DBGL, and things a user put there
			// (no colon in the name of a folder: Windows does not take one, the test failed there)
			File one = new File(logs, "Game One (12)"), two = new File(logs, "Other - Game (7)"), foreign = new File(logs, "my notes");
			assertTrue(one.mkdir() && two.mkdir() && foreign.mkdir());
			for (int i = 1; i <= 5; i++)
				Files.writeString(new File(one, "2026-11-0" + i + "_10-00-00.log").toPath(), "x" + i);
			Files.writeString(new File(two, "2026-11-09_10-00-00.log").toPath(), "yy");
			Files.writeString(new File(one, "readme.txt").toPath(), "of the user");
			Files.writeString(new File(foreign, "2026-11-09_10-00-00.log").toPath(), "of the user");
			Files.writeString(new File(logs, "menubar-check.txt").toPath(), "d");
			Files.writeString(new File(logs, "freeze-20261109-101500.txt").toPath(), "d");
			Files.writeString(new File(logs, "holiday.txt").toPath(), "of the user");
			List<org.dbgl.util.DosboxLog.Entry> entries = org.dbgl.util.DosboxLog.list(logs);
			assertEquals(8, entries.size());
			assertEquals(6, entries.stream().filter(e -> !e.profile().isEmpty()).count());
			assertTrue(entries.stream().noneMatch(e -> e.file().getName().equals("holiday.txt") || e.file().getName().equals("readme.txt") || e.file().getParentFile().equals(foreign)));
			// only what DBGL wrote is deleted
			assertFalse(org.dbgl.util.DosboxLog.delete(logs, new File(logs, "holiday.txt")));
			assertFalse(org.dbgl.util.DosboxLog.delete(logs, new File(one, "readme.txt")));
			assertFalse(org.dbgl.util.DosboxLog.delete(logs, new File(foreign, "2026-11-09_10-00-00.log")));
			assertTrue(new File(logs, "holiday.txt").isFile() && new File(one, "readme.txt").isFile() && new File(foreign, "2026-11-09_10-00-00.log").isFile());
			assertTrue(org.dbgl.util.DosboxLog.delete(logs, new File(logs, "menubar-check.txt")));
			assertFalse(new File(logs, "menubar-check.txt").exists());
			assertEquals(3, org.dbgl.util.DosboxLog.removeOld(logs, 2)); // of the five of the first profile the two newest stay
			assertEquals("2026-11-04_10-00-00.log,2026-11-05_10-00-00.log,readme.txt", java.util.Arrays.stream(one.list()).sorted().collect(java.util.stream.Collectors.joining(",")));
			assertTrue(org.dbgl.util.DosboxLog.delete(logs, new File(two, "2026-11-09_10-00-00.log")));
			assertFalse(two.exists()); // the folder of a profile goes with its last log
			assertTrue(foreign.isDirectory());
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(logs);
		}

		File backups = Files.createTempDirectory("dbgl-backups").toFile();
		try {
			// the folder of the files that were replaced when a stick was brought back: listed, deleted only by its exact name
			File stick = new File(backups, "stick-return-20261116-101500"), other = new File(backups, "stick-return-mine"), sub = new File(stick, "dosroot");
			assertTrue(sub.mkdirs() && other.mkdir());
			Files.writeString(new File(sub, "SAVE.DAT").toPath(), "12345");
			Files.writeString(new File(other, "keep.txt").toPath(), "of the user");
			assertEquals(org.dbgl.util.BackupCatalog.Kind.STICK_RETURN, org.dbgl.util.BackupCatalog.kindOf("stick-return-20261116-101500"));
			assertNull(org.dbgl.util.BackupCatalog.kindOf("stick-return-mine"));
			assertNull(org.dbgl.util.BackupCatalog.kindOf("stick-return-20261116-101500.txt"));
			List<org.dbgl.util.BackupCatalog.Backup> listed = org.dbgl.util.BackupCatalog.list(List.of(backups));
			assertEquals(1, listed.size());
			assertEquals(org.dbgl.util.BackupCatalog.Kind.STICK_RETURN, listed.get(0).kind());
			assertEquals(5, listed.get(0).size());
			assertFalse(listed.get(0).kind().restorable());
			assertFalse(org.dbgl.util.BackupCatalog.delete(other)); // not one of DBGL: stays
			assertTrue(new File(other, "keep.txt").isFile());
			assertTrue(org.dbgl.util.BackupCatalog.delete(stick));
			assertFalse(stick.exists());
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(backups);
		}

		// the part of alpha 51 (where Java 21 became necessary) starts with the note on Java 21, in both languages; alpha 52 on: the
		// newer parts do not repeat it (decided at alpha 52)
		for (String[] doc: new String[][] {{"changelog_de.txt", "Wichtig"}, {"changelog_en.txt", "Important"}}) {
			String text;
			try (java.io.InputStream in = org.dbgl.gui.dialog.DocumentDialog.class.getResourceAsStream("/doc/" + doc[0])) {
				text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
			}
			java.util.regex.Matcher first = java.util.regex.Pattern.compile("(?m)^0\\.101 alpha 51 .*\\n-+\\n").matcher(text);
			assertTrue(doc[0], first.find());
			String part = text.substring(first.end());
			assertTrue(doc[0], part.startsWith(doc[1] + "\n") && part.substring(0, 400).contains("Java 21") && part.substring(0, 600).contains("https://adoptium.net/temurin/releases/?version=21"));
		}
	}

	/**
	 * alpha 50: Dungeon Master II with an emulated Sound Blaster 16: the hint to choose "Sound Blaster Pro" in its setup; with
	 * SKULL.CFG beside the program only while another Sound Blaster is chosen there
	 */
	@Test
	public void testSetupAdviceDungeonMaster2() throws Exception {
		org.dbgl.model.conf.Configuration sb16 = new org.dbgl.model.conf.Configuration(), pro = new org.dbgl.model.conf.Configuration(), none = new org.dbgl.model.conf.Configuration();
		sb16.setValue("sblaster", "sbtype", "sb16");
		pro.setValue("sblaster", "sbtype", "sbpro1");
		for (String title: new String[] {"Dungeon Master II: The Legend of Skullkeep", "Dungeon Master 2", "dungeon master ii (1995)"}) {
			org.dbgl.util.ProfileChecks.Problem p = org.dbgl.util.GameHints.setupAdvice(title, sb16, null);
			assertNotNull(title, p);
			assertEquals("dm2sbpro", p.key());
			assertEquals("[sblaster] sbtype = sb16", p.args()[1]);
		}
		assertNull(org.dbgl.util.GameHints.setupAdvice("Dungeon Master II", pro, null));
		assertNull(org.dbgl.util.GameHints.setupAdvice("Dungeon Master II", none, null));
		assertNull(org.dbgl.util.GameHints.setupAdvice("Dungeon Master", sb16, null));
		assertNull(org.dbgl.util.GameHints.setupAdvice("Dungeon Keeper 2", sb16, null));
		assertFalse(org.dbgl.service.TextService.getInstance().get("dialog.checkprofiles.dm2sbpro", new Object[] {"X", "Y"}).contains("dm2sbpro"));

		String cfg = "\r\n[DIGITAL]\r\nDeviceName  = %s\r\nDeviceIRQ   = 7\r\nDeviceDMA   = 1\r\nDevicePort  = 0x220\r\nDeviceID    = %s\r\n\r\n[MIDI]\r\nDeviceName  = Sound Blaster 16\r\nDevicePort  = 0x388\r\nDeviceID    = 0xa009\r\n";
		assertEquals("e001", org.dbgl.util.GameHints.hmiDigitalId(String.format(cfg, "Sound Blaster Pro", "0xe001")));
		assertEquals("e015", org.dbgl.util.GameHints.hmiDigitalId(String.format(cfg, "Sound Blaster 16", "0xE015")));
		assertNull(org.dbgl.util.GameHints.hmiDigitalId("[MIDI]\r\nDeviceID = 0xa009\r\n"));
		File dir = Files.createTempDirectory("dbgl-dm2").toFile();
		try {
			File main = new File(dir, "DM2.EXE");
			Files.writeString(main.toPath(), "x");
			assertNotNull(org.dbgl.util.GameHints.setupAdvice("Dungeon Master II", sb16, main)); // no file of the setup yet
			File skull = new File(dir, "skull.cfg");
			Files.writeString(skull.toPath(), String.format(cfg, "Sound Blaster Pro", "0xe001"), StandardCharsets.ISO_8859_1);
			assertNull(org.dbgl.util.GameHints.setupAdvice("Dungeon Master II", sb16, main)); // done: no hint
			Files.writeString(skull.toPath(), String.format(cfg, "Sound Blaster 16", "0xe015"), StandardCharsets.ISO_8859_1);
			assertNotNull(org.dbgl.util.GameHints.setupAdvice("Dungeon Master II", sb16, main));
			assertNotNull(org.dbgl.util.GameHints.setupAdvice("DM2 (my copy)", sb16, main)); // the file tells the game
			assertNull(org.dbgl.util.GameHints.setupAdvice("DM2 (my copy)", pro, main));
			Files.writeString(skull.toPath(), "[MIDI]\r\nDeviceID = 0xa009\r\n", StandardCharsets.ISO_8859_1);
			assertNull(org.dbgl.util.GameHints.setupAdvice("Dungeon Master II", sb16, main)); // no digital sound chosen
			Files.writeString(skull.toPath(), String.format(cfg, "Sound Blaster", "0xe000"), StandardCharsets.ISO_8859_1);
			assertNull(org.dbgl.util.GameHints.setupAdvice("Dungeon Master II", sb16, main)); // another one: nothing known about it
			// the file in a folder of the game, or the program in a folder of the game and the file above it
			assertTrue(skull.delete());
			File sub = new File(dir, "DATA"), deep = new File(dir, "BIN");
			assertTrue(sub.mkdir() && deep.mkdir());
			Files.writeString(new File(sub, "SKULL.CFG").toPath(), String.format(cfg, "Sound Blaster Pro", "0xe001"), StandardCharsets.ISO_8859_1);
			assertNull(org.dbgl.util.GameHints.setupAdvice("Dungeon Master II", sb16, main));
			assertTrue(new File(sub, "SKULL.CFG").delete());
			Files.writeString(new File(dir, "SKULL.CFG").toPath(), String.format(cfg, "Sound Blaster Pro", "0xe001"), StandardCharsets.ISO_8859_1);
			File inBin = new File(deep, "DM2.EXE");
			Files.writeString(inBin.toPath(), "x");
			assertNull(org.dbgl.util.GameHints.setupAdvice("Dungeon Master II", sb16, inBin));
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
	}

	/** alpha 50: 1942: The Pacific Air War wants the memory manager JEMMEX loaded before it; the hint goes once that is so */
	@Test
	public void testMemoryManagerAdvice1942() throws Exception {
		List<String> plain = List.of("@echo off", "C:", "cd \\", "1942.EXE"), done = List.of("cd \\", "JEMMEX.EXE FRAME=E000 LOAD", "1942.EXE");
		for (String title: new String[] {"1942: The Pacific Air War", "1942 - The Pacific Air War (1994)", "1942 Pacific Air War Gold"}) {
			org.dbgl.util.ProfileChecks.Problem p = org.dbgl.util.GameHints.memoryManagerAdvice(title, null, plain);
			assertNotNull(title, p);
			assertEquals("paw1942jemmex", p.key());
			assertNull(title, org.dbgl.util.GameHints.memoryManagerAdvice(title, null, done));
		}
		assertNotNull(org.dbgl.util.GameHints.memoryManagerAdvice("PAW", new File("1942.exe"), plain)); // the program tells the game
		assertNull(org.dbgl.util.GameHints.memoryManagerAdvice("PAW", new File("1942.exe"), List.of("c:\\tools\\jemmex frame=e000 load")));
		assertNull(org.dbgl.util.GameHints.memoryManagerAdvice("PAW", new File("1942.exe"), List.of("@LH JEMMEX LOAD")));
		assertNotNull(org.dbgl.util.GameHints.memoryManagerAdvice("PAW", new File("1942.exe"), List.of("rem JEMMEX.EXE FRAME=E000 LOAD", "echo jemmex")));
		assertNull(org.dbgl.util.GameHints.memoryManagerAdvice("1942", null, plain)); // another game of that name
		assertNull(org.dbgl.util.GameHints.memoryManagerAdvice("Pacific Strike", new File("PS.EXE"), plain));
		File dir = Files.createTempDirectory("dbgl-1942").toFile();
		try {
			File bat = new File(dir, "START.BAT");
			Files.writeString(bat.toPath(), "@echo off\r\n1942.EXE\r\n");
			assertNotNull(org.dbgl.util.GameHints.memoryManagerAdvice("1942: The Pacific Air War", bat, List.of()));
			Files.writeString(bat.toPath(), "@echo off\r\nJEMMEX.EXE FRAME=E000 LOAD\r\n1942.EXE\r\n");
			assertNull(org.dbgl.util.GameHints.memoryManagerAdvice("1942: The Pacific Air War", bat, List.of()));
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
		assertFalse(org.dbgl.service.TextService.getInstance().get("dialog.checkprofiles.paw1942jemmex", new Object[] {"X", "Y"}).contains("paw1942jemmex"));
	}

	/** alpha 51: the ZIP file with the pictures of the windows is one of the diagnostic files of DBGL: listed, and it can go */
	@Test
	public void testWindowTourFile() throws Exception {
		assertTrue(org.dbgl.util.OldFiles.isDiagnostic("window-tour-20261117-062743.zip"));
		assertTrue(org.dbgl.util.OldFiles.isDiagnostic("restart-output.txt"));
		assertFalse(org.dbgl.util.OldFiles.isDiagnostic("window-tour-mine.zip"));
		assertFalse(org.dbgl.util.OldFiles.isDiagnostic("window-tour-20261117-062743.zip.bak"));
		assertFalse(org.dbgl.util.OldFiles.isDiagnostic("holiday.zip"));
		File logs = Files.createTempDirectory("dbgl-tour").toFile();
		try {
			File zip = new File(logs, "window-tour-20261117-062743.zip"), other = new File(logs, "holiday.zip");
			Files.writeString(zip.toPath(), "x");
			Files.writeString(other.toPath(), "of the user");
			assertEquals(1, org.dbgl.util.DosboxLog.list(logs).size());
			assertFalse(org.dbgl.util.DosboxLog.delete(logs, other));
			assertTrue(org.dbgl.util.DosboxLog.delete(logs, zip));
			assertTrue(other.isFile() && !zip.exists());
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(logs);
		}
		// no text of the windows of the tour is missing in any language
		for (String key: new String[] {"dialog.main.menu.windowtour", "dialog.windowtour.confirm", "dialog.windowtour.done", "dialog.windowtour.failed"})
			assertFalse(key, org.dbgl.service.TextService.getInstance().get(key, new Object[] {1, "x"}).contains(key));
	}

	/**
	 * alpha 51: the class the jar starts with tells an older Java that Java 21 is needed; it is compiled for Java 8 so that
	 * such a Java can run it at all
	 */
	@Test
	public void testStartNeedsJava21() throws Exception {
		assertEquals(8, org.dbgl.gui.Start.feature("1.8"));
		assertEquals(11, org.dbgl.gui.Start.feature("11"));
		assertEquals(17, org.dbgl.gui.Start.feature("17"));
		assertEquals(21, org.dbgl.gui.Start.feature("21"));
		assertEquals(21, org.dbgl.gui.Start.feature("21.0.2"));
		assertEquals(25, org.dbgl.gui.Start.feature(" 25-ea"));
		assertEquals(0, org.dbgl.gui.Start.feature(""));
		assertEquals(0, org.dbgl.gui.Start.feature(null));
		assertEquals(0, org.dbgl.gui.Start.feature("x"));
		assertTrue(org.dbgl.gui.Start.feature(System.getProperty("java.specification.version")) >= org.dbgl.gui.Start.JAVA_NEEDED);
		String de = org.dbgl.gui.Start.message(17, "C:\\Java\\jre17", "de"), en = org.dbgl.gui.Start.message(8, null, "fr");
		assertTrue(de, de.contains("ben\u00f6tigt Java 21") && de.contains("Java 17 (C:\\Java\\jre17)") && de.contains(org.dbgl.gui.Start.JAVA_PAGE));
		assertTrue(en, en.contains("needs Java 21") && en.contains("Found: Java 8.") && en.contains(org.dbgl.gui.Start.JAVA_PAGE));
		assertTrue(org.dbgl.gui.Start.message(0, null, "en").contains("Found: Java ?."));
		// the class file is one of Java 8 (52), the others are of Java 21 (65)
		try (java.io.InputStream in = org.dbgl.gui.Start.class.getResourceAsStream("Start.class"); java.io.InputStream other = org.dbgl.gui.Launcher.class.getResourceAsStream("Launcher.class")) {
			assertEquals(52, in.readNBytes(8)[7]);
			assertEquals(65, other.readNBytes(8)[7]);
		}
		// and the jar starts with it: as written in build.xml
		String build = Files.readString(new File(System.getProperty("dbgl.testdata")).toPath().resolve("../../../../../../build.xml").normalize());
		assertTrue(build.contains("<attribute name=\"Main-Class\" value=\"org.dbgl.gui.Start\"/>") && build.contains("includes=\"org/dbgl/gui/Start.java\" release=\"8\""));
		assertTrue(build.contains("<attribute name=\"Enable-Native-Access\" value=\"ALL-UNNAMED\"/>")); // no warning for SWT from Java 24 on
	}

	/** alpha 51: another file of a release whose name ends with "update" is not taken for the update zip */
	@Test
	public void testUpdateZipName() {
		String json = "[{\"tag_name\":\"v0.101-alpha52\",\"draft\":false,\"prerelease\":true,\"html_url\":\"u52\",\"assets\":["
				+ "{\"name\":\"dbgl-0.101alpha52-update.zip\",\"browser_download_url\":\"https://x/dbgl-0.101alpha52-update.zip\"},"
				+ "{\"name\":\"DBGL-Extended-Edition-0.101alpha52-source-update.zip\",\"browser_download_url\":\"https://x/source.zip\"},"
				+ "{\"name\":\"SHA256SUMS.txt\",\"browser_download_url\":\"https://x/SHA256SUMS.txt\"}]}]";
		assertEquals("https://x/dbgl-0.101alpha52-update.zip", org.dbgl.util.EditionUpdateChecker.newest(json, "0.101alpha51", true).updateZip());
	}

	/** alpha 51: a backup "before ... from older" tells the DBGL of its database (0.99, 0.100) */
	@Test
	public void testVersionBackupFromDatabase() throws Exception {
		File dir = java.nio.file.Files.createTempDirectory("dbglver").toFile();
		try {
			String[][] cases = {{"INSERT INTO VERSION VALUES(0,99)", "0.99-0.100"}, {"INSERT INTO VERSION VALUES(0,103)", "0.101alpha"},
					{"INSERT INTO PUBLIC.VERSION VALUES(0,101)", "0.101alpha"}, {"INSERT INTO VERSION VALUES(0,104)", null}, {"INSERT INTO VERSION VALUES(0,94)", "0.93-0.98"},
					{"INSERT INTO VERSION VALUES(0,92)", "0.92"}, {"INSERT INTO VERSION VALUES(0,75)", "pre0.92"}, {"INSERT INTO VERSION VALUES(0,50)", "pre0.92"},
					{"INSERT INTO VERSION VALUES(0,20)", null}, {"", null}};
			int i = 0;
			for (String[] c: cases) {
				File zip = new File(dir, "before-0.101alpha51-from-older-" + (i++) + ".zip");
				try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip))) {
					out.putNextEntry(new java.util.zip.ZipEntry("db/database.script"));
					out.write(("SET DATABASE UNIQUE NAME HSQLDB\nCREATE MEMORY TABLE VERSION(MAJORVERSION INTEGER)\n" + c[0] + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
					out.closeEntry();
				}
				assertEquals(c[0], c[1], org.dbgl.util.VersionBackup.dbglInBackup(zip));
			}
			assertNull(org.dbgl.util.VersionBackup.dbglInBackup(new File(dir, "missing.zip")));
			// in the list: a name a test version wrote ("0.75") is told anew from the database, a version of the Extended Edition stays
			File list = new File(dir, "list");
			list.mkdirs();
			for (String n: new String[] {"before-0.101alpha51-from-0.75-20261118-120000.zip", "before-0.101alpha51-from-0.101alpha50-20261118-120001.zip"})
				try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(new File(list, n)))) {
					out.putNextEntry(new java.util.zip.ZipEntry("db/database.script"));
					out.write("INSERT INTO VERSION VALUES(0,75)\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
					out.closeEntry();
				}
			java.util.Map<String, String> versions = new java.util.HashMap<>();
			for (org.dbgl.util.BackupCatalog.Backup b: org.dbgl.util.BackupCatalog.list(java.util.List.of(list)))
				versions.put(b.file().getName(), b.version());
			assertEquals("pre0.92", versions.get("before-0.101alpha51-from-0.75-20261118-120000.zip"));
			assertEquals("0.101alpha50", versions.get("before-0.101alpha51-from-0.101alpha50-20261118-120001.zip"));
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
	}

	/** alpha 51: the database of DBGL 0.91 or older (HSQLDB 1.8) is recognised, also as the cause of another exception */
	@Test
	public void testOldDatabaseFormat() {
		java.sql.SQLException old = new java.sql.SQLException("wrong database file version: unsupported old database version");
		assertTrue(org.dbgl.service.DatabaseService.isOldFormat(old));
		assertTrue(org.dbgl.service.DatabaseService.isOldFormat(new java.sql.SQLException("x", old)));
		assertFalse(org.dbgl.service.DatabaseService.isOldFormat(new java.sql.SQLException("user lacks privilege or object not found: GAMES")));
		assertFalse(org.dbgl.service.DatabaseService.isOldFormat(new java.sql.SQLException((String)null)));
	}

	/** alpha 51: the program backup of an update also keeps the libraries the new version makes superfluous */
	@Test
	public void testLibrariesReplacedByUpdate() throws Exception {
		File dir = java.nio.file.Files.createTempDirectory("dbgllibs").toFile();
		try {
			File lib = new File(dir, "lib");
			lib.mkdirs();
			for (String n: new String[] {"gallery_1.0.0.202509131601.jar", "hsqldb-2.7.4.jar", "swtwin64.jar", "my-own-tool.jar"})
				java.nio.file.Files.writeString(new File(lib, n).toPath(), n);
			File jar = new File(dir, "new-dbgl.jar");
			java.util.jar.Manifest mf = new java.util.jar.Manifest();
			mf.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
			mf.getMainAttributes().put(java.util.jar.Attributes.Name.CLASS_PATH, "lib/gallery_1.1.0.202606141240.jar lib/hsqldb-2.7.4.jar lib/swtwin64.jar ./");
			try (java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(jar), mf)) {
				// only the manifest
			}
			assertEquals(java.util.List.of("lib/gallery_1.0.0.202509131601.jar"), org.dbgl.util.EditionUpdater.librariesReplacedBy(jar, dir));
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
	}

	/** alpha 51: launch.exe of an update is installed on Windows only */
	@Test
	public void testUpdateWindowsOnly() throws Exception {
		File dir = java.nio.file.Files.createTempDirectory("dbglupd").toFile();
		try {
			for (String n: new String[] {"dbgl.jar", "launch.exe", "lib/json.jar"}) {
				File f = new File(dir, n);
				f.getParentFile().mkdirs();
				java.nio.file.Files.writeString(f.toPath(), n);
			}
			java.util.List<String> all = java.util.List.of("dbgl.jar", "launch.exe", "lib/json.jar");
			assertEquals(all, org.dbgl.util.EditionUpdater.onlyForThisSystem(all, dir, true));
			assertTrue(new File(dir, "launch.exe").isFile());
			assertEquals(java.util.List.of("dbgl.jar", "lib/json.jar"), org.dbgl.util.EditionUpdater.onlyForThisSystem(all, dir, false));
			assertFalse(new File(dir, "launch.exe").exists()); // not installed on Linux or macOS
			assertTrue(new File(dir, "dbgl.jar").isFile());
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
	}

	/** alpha 51: after an update with SWT of two systems, the one of the other system goes before SWT is loaded */
	@Test
	public void testRemoveForeignSwt() throws Exception {
		File lib = Files.createTempDirectory("dbgl-lib").toFile();
		try {
			for (String n: new String[] {"swtwin64.jar", "swtlin64.jar", "gallery_1.1.0.jar", "mine.jar"})
				Files.writeString(new File(lib, n).toPath(), "x");
			assertEquals(List.of(), org.dbgl.gui.Start.removeForeignSwt(lib, "Mac OS X")); // the own one is not there: nothing goes
			assertEquals(List.of(), org.dbgl.gui.Start.removeForeignSwt(lib, "SunOS"));
			assertEquals(List.of(), org.dbgl.gui.Start.removeForeignSwt(null, "Linux"));
			assertEquals(List.of("swtwin64.jar"), org.dbgl.gui.Start.removeForeignSwt(lib, "Linux"));
			assertEquals("gallery_1.1.0.jar,mine.jar,swtlin64.jar", java.util.Arrays.stream(lib.list()).sorted().collect(java.util.stream.Collectors.joining(",")));
			Files.writeString(new File(lib, "swtwin64.jar").toPath(), "x");
			assertEquals(List.of("swtlin64.jar"), org.dbgl.gui.Start.removeForeignSwt(lib, "Windows 10"));
			assertEquals(List.of(), org.dbgl.gui.Start.removeForeignSwt(lib, "Windows 11"));
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(lib);
		}
		// what the pictures of the windows paint over
		assertTrue(org.dbgl.gui.controls.WindowTour.looksLikeKey("8364ae0f983f8484afbb70a4d1030292"));
		assertTrue(org.dbgl.gui.controls.WindowTour.looksLikeKey(" abcDEF123456ghijkl "));
		assertFalse(org.dbgl.gui.controls.WindowTour.looksLikeKey("D:\\Spiele\\DOS\\dosroot"));
		assertFalse(org.dbgl.gui.controls.WindowTour.looksLikeKey("https://www.mobygames.com/game/123"));
		assertFalse(org.dbgl.gui.controls.WindowTour.looksLikeKey("DOSBox Staging 0.83.1"));
		assertFalse(org.dbgl.gui.controls.WindowTour.looksLikeKey("DOSBox-Staging-0.83.1"));
		assertFalse(org.dbgl.gui.controls.WindowTour.looksLikeKey("abcdefghijklmnopqrstuvwxyz"));
		assertFalse(org.dbgl.gui.controls.WindowTour.looksLikeKey(""));
		assertFalse(org.dbgl.gui.controls.WindowTour.looksLikeKey(null));
	}

	/** alpha 53: the calendar of the days played takes its colours from classes, so that the dark mode of the page has its own */
	@Test
	public void testHeatmapColoursByClass() {
		java.time.LocalDate from = java.time.LocalDate.of(2026, 11, 1);
		String html = org.dbgl.util.StatisticsExtra.heatmapHtml(from, from.plusDays(13), java.util.Map.of(from.plusDays(2), 600L, from.plusDays(3), 3600L), java.util.Locale.GERMANY);
		assertFalse(html, html.contains("background"));
		assertTrue(html.contains("class=\"h0\""));
		assertTrue(html.contains("class=\"h5\"")); // the longest day
		assertTrue(html.contains("class=\"h1\"")); // a sixth of it
		String dark = org.dbgl.util.StatisticsExtra.heatCss(org.dbgl.util.StatisticsExtra.HEAT_DARK, false);
		assertTrue(dark.contains("td.h0{background:#2b3036}"));
		assertTrue(org.dbgl.util.StatisticsExtra.heatCss(org.dbgl.util.StatisticsExtra.HEAT_LIGHT, true).contains("print-color-adjust:exact"));
	}
}
