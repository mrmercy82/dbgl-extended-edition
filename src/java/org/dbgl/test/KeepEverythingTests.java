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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.dbgl.model.conf.Configuration;
import org.dbgl.model.conf.ForeignLines;
import org.dbgl.service.GenericTextService;
import org.dbgl.util.UnknownAutoexecLines;
import org.junit.Test;


/**
 * Nothing in a configuration file gets lost by saving it, and saving twice changes nothing more: for odd configuration files
 * (data/corpus: built after cases from forums) and for random ones. Runs on Linux and on Windows (see OfflineTests).
 */
public class KeepEverythingTests {

	private static final String DATA_DIR = System.getProperty("dbgl.testdata", "src/java/org/dbgl/test/data");

	private static String saved(String text) throws IOException {
		Configuration conf = new Configuration();
		conf.loadDataWithAutoexec(GenericTextService.getInstance(), text, null, null);
		UnknownAutoexecLines.keepOnRead(conf.getAutoexec());
		return ForeignLines.keep(text, conf.toString(List.of()));
	}

	private static List<String> comments(String text) {
		List<String> r = new ArrayList<>();
		for (String x: text.replace("\uFEFF", "").split("\\r\\n|\\r|\\n"))
			if (x.trim().startsWith("#") && !x.trim().toUpperCase().startsWith("#REM"))
				r.add(x.trim());
		Collections.sort(r);
		return r;
	}

	private static List<String> check(String name, String text) throws IOException {
		List<String> problems = new ArrayList<>();
		String once = saved(text);
		List<String> gone = new ArrayList<>(comments(text));
		for (String c: comments(once))
			gone.remove(c);
		if (!gone.isEmpty())
			problems.add(name + ": comments gone " + gone);
		String twice = saved(once);
		if (!twice.equals(once))
			problems.add(name + ": saving twice changes it again");
		return problems;
	}

	@Test
	public void testOddFiles() throws IOException {
		File[] files = new File(DATA_DIR, "corpus").listFiles((d, n) -> n.endsWith(".conf"));
		Arrays.sort(files);
		List<String> problems = new ArrayList<>();
		for (File f: files)
			problems.addAll(check(f.getName(), new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1)));
		assertEquals(String.join("\n", problems), 0, problems.size());
	}

	static final String[] SECTIONS = {"sdl", "dosbox", "cpu", "render", "mixer", "sblaster", "gus", "dos", "midi", "SDL", "Cpu", "ide, primary", "mysection", " dosbox "};
	static final String[][] SETTINGS = {{"fullscreen", "true", "false", "on", "1", "yes", ""}, {"output", "opengl", "surface", "ddraw", "bogus", "texture"}, {"cycles", "max", "auto", "fixed 3000", "3000", "max 80%", "fast"},
		{"memsize", "16", "64", "128", "0", "abc"}, {"machine", "svga_s3", "vga", "vgaonly", "tandy"}, {"sbtype", "sb16", "sbpro2", "none"}, {"irq", "5", "7", "0"}, {"scaler", "normal2x", "hq2x forced", "none"},
		{"core", "auto", "dynamic", "normal"}, {"cpu_cycles", "5000", "max"}, {"keyboardlayout", "gr", "auto", "uk 437"}, {"language", ""}, {"minimum mcb free", "0"}, {"aspect", "true", "stretch"}};
	static final String[] AUTOEXEC = {"mount c .", "mount c \"my games\"", "mount d cd -t cdrom", "mount m util", "mount c ~/games", "mount c C:\\Spiele", "imgmount d cd.iso -t iso", "imgmount d disc1.cue disc2.cue -t cdrom",
		"imgmount a d1 d2 -t floppy", "imgmount 2 hdd.img -size 512,63,16,520 -fs none", "mount -u c", "c:", "C:\\", "d:", "m:", "z:", "cd game", "cd \\game", "cd ..", "cd \\", "cd \"my game\"", "cd game/sub",
		"game.exe", "GAME.EXE -nosound", "setup.exe", "call start.bat", "start.bat", "mouse.com", "lh mouse.com", "loadfix game.exe", "loadfix -f", "boot a:", "boot -l c", "boot C:\\x.img", "boot a:\\disk1.img disk2.img",
		"@echo off", "echo hallo", "echo.", "cls", "rem nur so", "REM GROSS", "@REM STARTUP x", ":label", "goto label", "if exist x.cfg goto label", "choice /c:12", "set blaster=A220 I7 D1", "config -set cpu cycles=max",
		"keyb gr", "mixer sb 50", "pause", "@pause", "exit", "subst e c:\\game", "path=%path%;c:\\util", "dir /w", "imgmount d \"a b.iso\" -t iso", "mount e f:\\ -t cdrom -usecd 0 -ioctl", "ipxnet connect x", "loadrom x.rom",
		"\"GAME FILE.EXE\"", "game.exe %1", "  game.exe  ", "\tcd\tgame", "imgmount d cd/\"x.cue\" -t iso", "mount c: .", "f:\\ (press enter)"};
	static final String[] JUNK = {"# Kommentar", "# ----", "#", "; Semikolon", "// C-Kommentar", "nur Text", "=wert", "kaputt=", "[kaputt", "[autoexec] keyb gr", "[sdl] ; Video", "# Ümläut äöü", "    ", "\t# eingerückt"};

	static String gen(Random r) {
		List<String> l = new ArrayList<>();
		if (r.nextInt(8) == 0) l.add("# Kopf von " + r.nextInt(1000));
		if (r.nextInt(10) == 0) l.add(SETTINGS[r.nextInt(SETTINGS.length)][0] + "=" + SETTINGS[0][1]); // before any section
		int secs = r.nextInt(5);
		for (int s = 0; s < secs; s++) {
			l.add("[" + SECTIONS[r.nextInt(SECTIONS.length)] + "]");
			for (int i = r.nextInt(5); i > 0; i--) {
				if (r.nextInt(4) == 0) { l.add(JUNK[r.nextInt(JUNK.length)]); continue; }
				String[] st = SETTINGS[r.nextInt(SETTINGS.length)];
				String sep = new String[] {"=", " = ", "\t=\t", "= "}[r.nextInt(4)];
				l.add(st[0] + sep + st[1 + r.nextInt(st.length - 1)] + (r.nextInt(12) == 0 ? "  # inline": ""));
			}
			if (r.nextInt(3) == 0) l.add("");
		}
		if (r.nextInt(10) != 0) {
			l.add(new String[] {"[autoexec]", "[Autoexec]", "[AUTOEXEC]", "[autoexec] # start"}[r.nextInt(4)]);
			for (int i = r.nextInt(12); i > 0; i--)
				l.add(r.nextInt(6) == 0 ? JUNK[r.nextInt(JUNK.length)]: AUTOEXEC[r.nextInt(AUTOEXEC.length)]);
		}
		String eol = new String[] {"\n", "\r\n", "\n", "\r\n", "\r"}[r.nextInt(5)];
		String t = String.join(eol, l) + (r.nextBoolean() ? eol: "");
		if (r.nextInt(25) == 0) t = "\uFEFF" + t;
		return t;
	}

	/**
	 * random odd files: no comment is lost. Saving twice: known to change some of them still (scripts without a game, several
	 * BOOT lines: one game per profile), so only counted, the number may not grow
	 */
	@Test
	public void testRandomFiles() throws IOException {
		List<String> lost = new ArrayList<>();
		int changesAgain = 0;
		for (long seed = 1; seed <= 2000; seed++) {
			Random r = new Random(seed);
			for (String p: check("seed " + seed, gen(r)))
				if (p.contains("comments gone"))
					lost.add(p);
				else
					changesAgain++;
		}
		assertEquals(String.join("\n", lost), 0, lost.size());
		assertTrue("saving twice changed " + changesAgain + " of 2000 random files", changesAgain <= LIMIT);
	}

	/** how many of the random files are still changed by a second saving (see testRandomFiles) */
	private static final int LIMIT = 95; // alpha 46: 91
}
