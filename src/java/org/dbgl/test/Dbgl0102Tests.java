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
}
