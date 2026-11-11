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
		// for Staging 0.83: the CD image and the hard disk image for booting both become MOUNT
		ep.adaptImageMountsToDosbox();
		assertEquals("mount", ((org.dbgl.model.conf.mount.ImageMount)em.get(0)).getCommand().toLowerCase());
		assertEquals("mount", ((org.dbgl.model.conf.mount.ImageMount)em.get(1)).getCommand().toLowerCase());
		String auto = ep.getConfiguration().getAutoexec().toString();
		assertTrue(auto, auto.contains("-size 512,63,16,520") && auto.contains("-fs none") && auto.contains("boot -l C")); // nothing of the booter lost
		// and back: switched to DOSBox-X, both are IMGMOUNT again, with everything the hard disk image needs
		org.dbgl.model.aggregate.DosboxVersion vx = DosboxVersionFactory.create("DOSBox-X", SettingsService.DOSBOX_X_DOSBOX_FAM.title_, "2025.10.07", false, true, false, null,
			"dosboxx", "", "", "dosboxx/dosbox-x.conf");
		ep.setDosboxVersion(vx);
		assertTrue(ep.adaptImageMountsToDosbox());
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
}
