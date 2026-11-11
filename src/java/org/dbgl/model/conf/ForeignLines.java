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
package org.dbgl.model.conf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * The lines of a configuration file that DBGL does not keep in its settings: comments ("# ..."), also in [autoexec], and lines
 * it cannot read ("fullscreen true" without "=", "[sdl" without "]", a setting before the first section). Before alpha 46 they
 * were gone without a word once the profile was saved. Now they are put again into the newly written text, each before the
 * line it was before (the same setting, section or [autoexec] line), or else at the end of its section. "Check profiles"
 * tells the lines DOSBox cannot read and repairs them.
 */
public final class ForeignLines {

	/** what a kept line was before: a setting (section + item), a section header, an [autoexec] line, or the end */
	private record Anchor(String kind, String section, String key) {
	}

	private ForeignLines() {
	}

	/** as DOSBox and DBGL read it: "[name]", anything after the "]" left out (e.g. "[autoexec]# Lines in this section ...") */
	private static String header(String t) {
		int end = t.indexOf(']');
		return t.startsWith("[") && (end > 0) ? t.substring(1, end).trim().toLowerCase(Locale.ROOT): null;
	}

	/** an [autoexec] line as DBGL may write it again: without quotes, one blank between the words, small letters */
	private static String normal(String t) {
		return t.replace("\"", "").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
	}

	private static boolean isOwn(String t) {
		return t.equalsIgnoreCase(Configuration.ALL_SETTINGS_MARKER);
	}

	/** the kept lines of a file, with what they were before; DBGL's own lines (its marker, its custom section) left out */
	private static Map<Anchor, List<String>> of(String text) {
		Map<Anchor, List<String>> result = new LinkedHashMap<>();
		List<String> pending = new ArrayList<>();
		String section = null;
		boolean custom = false;
		String withoutMark = text.startsWith("\uFEFF") ? text.substring(1): text.startsWith("\u00EF\u00BB\u00BF") ? text.substring(3): text; // byte order mark
		for (String line: withoutMark.split("\r\n|\r|\n", -1)) {
			String t = line.trim();
			if (t.equalsIgnoreCase("#REM DBGL-CUSTOM")) {
				custom = true;
				continue;
			}
			if (t.equalsIgnoreCase("#REM /DBGL-CUSTOM")) {
				custom = false;
				continue;
			}
			if (custom || t.isEmpty() || isOwn(t))
				continue;
			String h = header(t);
			Anchor anchor = null;
			if (h != null) {
				// what is at the end of a section stays at the end of it (also when the section has nothing else left: it comes
				// back with just these lines); before the first section, before it
				if (!pending.isEmpty() && (section != null)) {
					result.computeIfAbsent(new Anchor("end", section, null), a -> new ArrayList<>()).addAll(pending);
					pending.clear();
				}
				anchor = new Anchor("header", h, null);
				section = h;
			} else if (t.startsWith("#")) {
				pending.add(line);
			} else if ("autoexec".equals(section)) {
				anchor = new Anchor("line", section, normal(t));
			} else if ((section == null) || t.startsWith("[") || (t.indexOf('=') <= 0)) {
				pending.add(line); // not read by DBGL (nor by DOSBox)
			} else {
				anchor = new Anchor("item", section, t.substring(0, t.indexOf('=')).trim().toLowerCase(Locale.ROOT));
			}
			if ((anchor != null) && !pending.isEmpty()) {
				result.computeIfAbsent(anchor, a -> new ArrayList<>()).addAll(pending);
				pending.clear();
			}
		}
		if (!pending.isEmpty())
			result.computeIfAbsent(new Anchor("end", section, null), a -> new ArrayList<>()).addAll(pending);
		return result;
	}

	/**
	 * @param before the file as it was (null or empty: nothing to keep)
	 * @param after  the text DBGL writes now
	 * @return after, with the lines of before that DBGL does not keep, where they were
	 */
	public static String keep(String before, String after) {
		if ((before == null) || before.isEmpty())
			return after;
		Map<Anchor, List<String>> foreign = of(before);
		Map<String, String> headers = headersWithText(before);
		if (foreign.isEmpty() && headers.isEmpty())
			return after;
		String eol = after.contains("\r\n") ? "\r\n": "\n";
		List<String> lines = new ArrayList<>(List.of(after.split("\r?\n", -1)));
		boolean endsWithEol = !lines.isEmpty() && lines.get(lines.size() - 1).isEmpty();
		if (endsWithEol)
			lines.remove(lines.size() - 1);
		// a line DBGL writes itself as well is not kept twice; counted, so that repeated comments ("# ----" in each section of
		// a file written by "config -writeconf") all stay (before, only the first of them did; found by review, alpha 46)
		// in its own section only: a comment of [sdl] the same as one in [autoexec] went to [autoexec] (alpha 47)
		Map<String, Integer> written = new java.util.HashMap<>();
		String region = null;
		for (String l: lines) {
			String h = header(l.trim());
			if (h != null)
				region = h;
			else
				written.merge(region + "\n" + l.trim(), 1, Integer::sum);
		}
		for (Map.Entry<Anchor, List<String>> e: foreign.entrySet()) {
			List<String> add = new ArrayList<>();
			for (String l: e.getValue()) {
				String id = (e.getKey().kind().equals("header") ? null: e.getKey().section()) + "\n" + l.trim(); // before a header: before any section
				Integer n = written.get(id);
				if ((n != null) && (n > 0))
					written.put(id, n - 1);
				else
					add.add(l);
			}
			if (add.isEmpty())
				continue;
			int at = place(lines, e.getKey());
			if (at < 0) { // its section is gone: the section again, with just these lines (DOSBox does not mind)
				Anchor a = e.getKey();
				List<String> block = new ArrayList<>();
				if (a.section() != null)
					block.add("[" + a.section() + "]");
				block.addAll(add);
				block.add("");
				int autoexec = indexOfHeader(lines, "autoexec");
				lines.addAll(autoexec >= 0 ? autoexec: lines.size(), block);
			} else {
				lines.addAll(at, add);
			}
		}
		// "[sdl] ; Video" or "[autoexec] keyb gr": the header as it was written (DOSBox reads the part up to "]" only, DBGL too)
		for (int i = 0; i < lines.size(); i++) {
			String h = header(lines.get(i).trim());
			if ((h != null) && lines.get(i).trim().equalsIgnoreCase("[" + h + "]") && headers.containsKey(h))
				lines.set(i, headers.remove(h));
		}
		// such a header of a section DBGL leaves out now (nothing in it it would write, e.g. "[autoexec] keyb gr"): kept
		for (Map.Entry<String, String> e: headers.entrySet()) {
			if (indexOfHeader(lines, e.getKey()) >= 0)
				continue;
			int autoexec = e.getKey().equals("autoexec") ? -1: indexOfHeader(lines, "autoexec");
			if (autoexec >= 0) {
				lines.addAll(autoexec, List.of(e.getValue(), ""));
			} else {
				if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank())
					lines.add("");
				lines.add(e.getValue());
			}
		}
		return String.join(eol, lines) + (endsWithEol ? eol: "");
	}

	/** the section headers with text after the "]", by their name (the first of each) */
	private static Map<String, String> headersWithText(String text) {
		Map<String, String> result = new LinkedHashMap<>();
		for (String line: text.replace("\uFEFF", "").split("\r\n|\r|\n", -1)) {
			String t = line.trim();
			String h = header(t);
			if ((h != null) && !t.substring(t.indexOf(']') + 1).isBlank())
				result.putIfAbsent(h, t);
		}
		return result;
	}

	private static int indexOfHeader(List<String> lines, String section) {
		for (int i = 0; i < lines.size(); i++)
			if (section.equals(header(lines.get(i).trim())))
				return i;
		return -1;
	}

	/** where the lines go in the new text, -1: their section is not there */
	private static int place(List<String> lines, Anchor a) {
		int start = a.section() == null ? -1: indexOfHeader(lines, a.section());
		switch (a.kind()) {
			case "header" -> {
				return start >= 0 ? start: -1;
			}
			case "item", "line" -> {
				if (start < 0)
					return -1;
				for (int i = start + 1; (i < lines.size()) && (header(lines.get(i).trim()) == null); i++) {
					String t = lines.get(i).trim();
					String key = a.kind().equals("line") ? normal(t): (t.indexOf('=') > 0 ? t.substring(0, t.indexOf('=')).trim().toLowerCase(Locale.ROOT): null);
					if (a.key().equals(key))
						return i;
				}
				// an [autoexec] line written otherwise now: at the start (a comment there is mostly about what follows)
				return a.kind().equals("line") ? start + 1: endOf(lines, start);
			}
			default -> { // the end of its section, or of the file
				if (a.section() == null)
					return lines.size();
				return start < 0 ? -1: endOf(lines, start);
			}
		}
	}

	/** after the last line of the section (before its empty lines) */
	private static int endOf(List<String> lines, int start) {
		int end = start + 1;
		while ((end < lines.size()) && (header(lines.get(end).trim()) == null))
			end++;
		while ((end > start + 1) && lines.get(end - 1).trim().isEmpty())
			end--;
		return end;
	}
}
