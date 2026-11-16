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
package org.dbgl.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


/**
 * Alpha 50, a tool for testing: which DOSBox DBGL takes each program in some folders for, without starting any of them,
 * and the pieces of text in the program that tell its version. Started on its own:
 * "java -cp dbgl.jar;lib\* org.dbgl.util.DetectReport folder..." writes dbgl-erkennung.txt into the current folder. It
 * reads the programs only; nothing is changed, and no setting of DBGL is read or written.
 */
public final class DetectReport {

	private static final Pattern PROGRAM = Pattern.compile("(?i)dosbox[^\\\\/]*\\.exe|dosbox(-x|-staging)?");
	private static final Pattern RUN = Pattern.compile("[\\x20-\\x7e]{4,200}");
	private static final Pattern TELLING = Pattern.compile("(?i)mapper[-.][\\w.-]*(map|txt)|configuration ?file for|dosbox[ -]?(staging|x|ece|svn)?[^\\d]{0,24}\\d+\\.\\d+|^v?\\d\\.\\d\\d(\\.\\d+)?(-\\d)?$|^20\\d\\d\\.[01]\\d\\.[0-3]\\d$|SVN|ECE r\\d+");

	private DetectReport() {
	}

	public static void main(String[] args) throws IOException {
		StringBuilder report = new StringBuilder("DBGL Extended Edition: recognition of DOSBox programs\r\n\r\n");
		int programs = 0, unknown = 0;
		for (String arg: args.length == 0 ? new String[] {"."}: args) {
			File root = new File(arg);
			report.append("=== ").append(root.getAbsolutePath()).append("\r\n");
			List<Path> found;
			try (Stream<Path> walk = Files.walk(root.toPath())) {
				found = walk.filter(p -> Files.isRegularFile(p) && PROGRAM.matcher(p.getFileName().toString()).matches()).sorted().toList();
			}
			for (Path p: found) {
				File exe = p.toFile();
				if (exe.length() < 100_000)
					continue; // no DOSBox: a starter or a script
				programs++;
				DosboxVersionDetector.Result r = DosboxVersionDetector.detect(exe, null, false);
				if (r == null)
					unknown++;
				report.append("\r\n").append(root.toPath().relativize(p)).append("  (").append(exe.length()).append(" bytes)\r\n");
				report.append("  DBGL: ").append(r == null ? "NOT RECOGNIZED": r.family_ + " " + r.version_).append("\r\n");
				for (String s: telling(exe))
					report.append("    | ").append(s).append("\r\n");
				System.out.println((r == null ? "?  ": "ok ") + root.toPath().relativize(p) + (r == null ? "": "  ->  " + r.family_ + " " + r.version_));
			}
		}
		report.append("\r\n").append(programs).append(" programs, ").append(unknown).append(" not recognized\r\n");
		File out = new File("dbgl-erkennung.txt");
		Files.write(out.toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println();
		System.out.println(programs + " programs, " + unknown + " not recognized. Report: " + out.getAbsolutePath());
	}

	/** the pieces of text of a program that may tell its kind and version, at most 40 */
	static List<String> telling(File exe) throws IOException {
		Set<String> result = new LinkedHashSet<>();
		String text = new String(Files.readAllBytes(exe.toPath()), StandardCharsets.ISO_8859_1);
		Matcher m = RUN.matcher(text);
		while (m.find() && (result.size() < 40)) {
			String s = m.group().trim();
			if (TELLING.matcher(s).find() && (s.length() <= 120))
				result.add(s);
		}
		return new ArrayList<>(result);
	}
}
