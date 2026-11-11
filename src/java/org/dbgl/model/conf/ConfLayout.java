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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * The layout of a configuration file kept when DBGL saves it (alpha 47): its sections and the settings in them in the order of
 * the file, its empty lines, and a setting whose value did not change exactly as it was written ("cycles = max" stays so,
 * not "cycles=max"). Before, DBGL wrote the sections in the order it set them (e.g. [midi] first after the profile editor),
 * each setting as "name=value" and one empty line after each section. What DBGL changes is written as DBGL writes it, new
 * settings after the others of their section, new sections after the others (before [autoexec]). The lines of [autoexec] and DBGL's own
 * section are left as DBGL writes them (Autoexec keeps what it does not write itself).
 */
public final class ConfLayout {

	private static final String CUSTOM_START = "#REM DBGL-CUSTOM", CUSTOM_END = "#REM /DBGL-CUSTOM";

	private ConfLayout() {
	}

	/** as DBGL reads it: "[name]", anything after the "]" left out, "[ dosbox ]" another section than "[dosbox]" */
	private static String header(String t) {
		int end = t.indexOf(']');
		return t.startsWith("[") && (end > 0) ? t.substring(1, end).toLowerCase(Locale.ROOT): null;
	}

	/** the name of a setting in a line of a section other than [autoexec], or null (a comment, a line DBGL does not read) */
	private static String key(String t) {
		if (t.isEmpty() || t.startsWith("#") || t.startsWith("[") || (t.indexOf('=') <= 0))
			return null;
		return t.substring(0, t.indexOf('=')).trim().toLowerCase(Locale.ROOT);
	}

	private static String value(String t) {
		return t.substring(t.indexOf('=') + 1).trim();
	}

	/** what the file was like: its sections in their order, per section its settings in their order */
	private static final class Old {
		final List<String> order = new ArrayList<>();
		final Map<String, String> headerLines = new HashMap<>();
		final Map<String, Integer> blanksBefore = new HashMap<>();
		final Map<String, List<String>> keys = new HashMap<>();
		final Map<String, Integer> lead = new HashMap<>(); // section|key: empty lines before the comments before the setting
		final Map<String, Integer> gap = new HashMap<>(); // section|key: empty lines right before the setting
		final Map<String, String> rawLines = new HashMap<>(); // section|key: the line as written
		int blanksAtEnd;
		boolean head; // lines before the first section
		final java.util.Set<String> filled = new java.util.HashSet<>(); // the sections with lines in them
	}

	private static Old read(String text) {
		Old old = new Old();
		String section = null;
		int blanks = 0;
		boolean custom = false;
		int leadBlanks = -1; // the empty lines before the comments seen since the last setting, -1: no comment
		List<String> lines = split(text);
		if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty())
			lines.remove(lines.size() - 1); // the end of the last line, no empty line
		for (String line: lines) {
			String t = line.trim();
			if (t.equalsIgnoreCase(CUSTOM_START))
				custom = true;
			if (custom) {
				if (t.equalsIgnoreCase(CUSTOM_END))
					custom = false;
				blanks = 0;
				continue;
			}
			if (t.isEmpty()) {
				blanks++;
				continue;
			}
			String h = header(t);
			if ((h == null) && (section == null))
				old.head = true;
			if (h != null) {
				if (!old.order.contains(h)) {
					old.order.add(h);
					old.headerLines.put(h, line);
					old.blanksBefore.put(h, blanks);
				}
				section = h;
				leadBlanks = -1;
			} else if (section != null) {
				old.filled.add(section);
			}
			if ((h == null) && (section != null) && !section.equals("autoexec")) {
				String k = key(t);
				if (k != null) {
					List<String> keys = old.keys.computeIfAbsent(section, s -> new ArrayList<>());
					if (!keys.contains(k)) {
						keys.add(k);
						old.lead.put(section + "|" + k, leadBlanks >= 0 ? leadBlanks: blanks);
						old.gap.put(section + "|" + k, leadBlanks >= 0 ? blanks: 0);
						old.rawLines.put(section + "|" + k, line);
					}
					leadBlanks = -1;
				} else if (leadBlanks < 0) {
					leadBlanks = blanks;
				}
			}
			blanks = 0;
		}
		old.blanksAtEnd = blanks;
		return old;
	}

	/** a section of the new text: its header, its settings each with the lines kept before it, what is left at its end */
	private static final class Section {
		String name;
		String headerLine;
		final List<String> entryKeys = new ArrayList<>();
		final Map<String, List<String>> entryLines = new LinkedHashMap<>();
		final List<String> rest = new ArrayList<>(); // [autoexec]: all its lines
	}

	/**
	 * @param before the file as it was (null or empty: nothing to keep)
	 * @param after the text DBGL writes now (with the lines kept by {@link ForeignLines})
	 * @return after, in the layout of before
	 */
	public static String keep(String before, String after) {
		if ((before == null) || before.isBlank() || (after == null))
			return after;
		Old old = read(before);
		if (old.order.isEmpty())
			return after;
		// the ends of lines of the file ("\r" alone, as on old Macs, as DBGL writes them)
		String eol = before.contains("\r\n") ? "\r\n": before.contains("\n") ? "\n": after.contains("\r\n") ? "\r\n": "\n";
		List<String> lines = split(after);
		boolean endsWithEol = !lines.isEmpty() && lines.get(lines.size() - 1).isEmpty();
		if (endsWithEol)
			lines.remove(lines.size() - 1);

		// the new text in parts: what comes before the first section, DBGL's own section, the sections
		List<String> head = new ArrayList<>(), custom = new ArrayList<>();
		List<Section> sections = new ArrayList<>();
		Section current = null;
		List<String> pending = new ArrayList<>();
		boolean inCustom = false;
		for (String line: lines) {
			String t = line.trim();
			if (t.equalsIgnoreCase(CUSTOM_START))
				inCustom = true;
			if (inCustom) {
				custom.add(line);
				if (t.equalsIgnoreCase(CUSTOM_END))
					inCustom = false;
				continue;
			}
			String h = header(t);
			if (h != null) {
				if (current != null)
					current.rest.addAll(pending);
				else
					head.addAll(pending);
				pending.clear();
				current = new Section();
				current.name = h;
				current.headerLine = line;
				sections.add(current);
				continue;
			}
			if (current == null) {
				head.add(line);
				continue;
			}
			if (current.name.equals("autoexec")) {
				current.rest.add(line);
				continue;
			}
			String k = key(t);
			if ((k != null) && !current.entryLines.containsKey(k)) {
				List<String> entry = new ArrayList<>();
				for (String p: pending)
					if (!p.isBlank())
						entry.add(p);
				entry.add(line);
				pending.clear();
				current.entryKeys.add(k);
				current.entryLines.put(k, entry);
			} else {
				pending.add(line);
			}
		}
		if (current != null)
			current.rest.addAll(pending);
		else
			head.addAll(pending);
		if (sections.isEmpty())
			return after;

		// the sections in the order of the file; new ones after the others, before [autoexec]
		List<Section> ordered = new ArrayList<>();
		for (String name: old.order)
			for (Section s: sections)
				if (s.name.equals(name))
					ordered.add(s);
		for (Section s: sections) {
			if (ordered.contains(s))
				continue;
			int at = ordered.size();
			for (int j = 0; j < ordered.size(); j++)
				if (ordered.get(j).name.equals("autoexec")) {
					at = j;
					break;
				}
			ordered.add(at, s);
		}

		// a section without anything in it, left out by DBGL ("[devices]" of "config -writeconf"): where it was
		for (int i = 0; i < old.order.size(); i++) {
			String name = old.order.get(i);
			if (old.filled.contains(name) || name.equals("autoexec") || sections.stream().anyMatch(x -> x.name.equals(name)))
				continue;
			Section empty = new Section();
			empty.name = name;
			empty.headerLine = old.headerLines.get(name);
			int at = 0;
			for (int j = i - 1; j >= 0; j--) {
				String prev = old.order.get(j);
				java.util.Optional<Section> p = ordered.stream().filter(x -> x.name.equals(prev)).findFirst();
				if (p.isPresent()) {
					at = ordered.indexOf(p.get()) + 1;
					break;
				}
			}
			ordered.add(at, empty);
		}

		List<String> out = new ArrayList<>();
		while (!head.isEmpty() && head.get(head.size() - 1).isBlank())
			head.remove(head.size() - 1);
		out.addAll(head);
		boolean customDone = custom.isEmpty();
		for (Section s: ordered) {
			if (!customDone && s.name.equals("autoexec")) { // DBGL's own section: before [autoexec], as DBGL writes it
				out.addAll(custom);
				out.add("");
				customDone = true;
			}
			// the empty lines before the header as in the file (one for a new section, none at the very start)
			while (!out.isEmpty() && out.get(out.size() - 1).isBlank())
				out.remove(out.size() - 1);
			int blanks = old.blanksBefore.containsKey(s.name) && (old.head || (old.order.indexOf(s.name) > 0)) ? old.blanksBefore.get(s.name): 1;
			if (out.isEmpty() && (old.order.indexOf(s.name) == 0))
				blanks = old.blanksBefore.get(s.name); // empty lines at the start of the file
			if (!out.isEmpty() || (old.order.indexOf(s.name) == 0))
				for (int b = 0; b < blanks; b++)
					out.add("");
			// the header as written in the file ("[SDL]", "[sdl] ; Video"; ForeignLines already kept the text after the "]")
			String oldHeader = old.headerLines.get(s.name);
			out.add((oldHeader != null) && (header(oldHeader.trim()) != null) && s.headerLine.trim().equalsIgnoreCase("[" + s.name + "]") ? oldHeader: s.headerLine);
			if (s.name.equals("autoexec")) {
				List<String> rest = new ArrayList<>(s.rest);
				while (!rest.isEmpty() && rest.get(rest.size() - 1).isBlank())
					rest.remove(rest.size() - 1);
				out.addAll(rest);
				continue;
			}
			List<String> keys = new ArrayList<>();
			for (String k: old.keys.getOrDefault(s.name, List.of()))
				if (s.entryLines.containsKey(k))
					keys.add(k);
			for (String k: s.entryKeys)
				if (!keys.contains(k))
					keys.add(k); // a new setting: after the ones of the file
			for (String k: keys) {
				List<String> entry = new ArrayList<>(s.entryLines.get(k));
				String id = s.name + "|" + k;
				// the empty lines before the setting and the comments before it as in the file
				int lead = old.lead.getOrDefault(id, 0), gap = old.gap.getOrDefault(id, 0);
				if (entry.size() == 1)
					lead += gap;
				for (int b = 0; b < lead; b++)
					out.add("");
				// the value unchanged: the line as it was written
				String raw = old.rawLines.get(id);
				String now = entry.remove(entry.size() - 1);
				out.addAll(entry);
				if (!entry.isEmpty())
					for (int b = 0; b < gap; b++)
						out.add("");
				out.add((raw != null) && value(raw.trim()).equals(value(now.trim())) ? raw: now);
			}
			List<String> rest = new ArrayList<>(s.rest);
			while (!rest.isEmpty() && rest.get(rest.size() - 1).isBlank())
				rest.remove(rest.size() - 1);
			while (!rest.isEmpty() && rest.get(0).isBlank())
				rest.remove(0);
			out.addAll(rest);
		}
		if (!customDone) {
			out.add("");
			out.addAll(custom);
		}
		// the end of the file as it was: its empty lines
		while (!out.isEmpty() && out.get(out.size() - 1).isBlank())
			out.remove(out.size() - 1);
		for (int b = 0; b < old.blanksAtEnd; b++)
			out.add("");
		return String.join(eol, out) + (endsWithEol || (old.blanksAtEnd > 0) ? eol: "");
	}

	/**
	 * The [autoexec] section of a file as written there, its header and its lines up to the next section, ending with the end of
	 * a line as DBGL writes it; null if the file has none, more than one or DBGL's own section in it.
	 */
	public static String autoexecOf(String text) {
		List<String> lines = split(text);
		List<String> result = null;
		boolean done = false;
		for (String line: lines) {
			String t = line.trim();
			String h = header(t);
			if ((h != null) && h.equals("autoexec")) {
				if (result != null)
					return null; // a second one
				result = new ArrayList<>();
			} else if ((h != null) && (result != null)) {
				done = true;
			} else if (t.equalsIgnoreCase(CUSTOM_START) || t.equalsIgnoreCase(CUSTOM_END)) {
				if ((result != null) && !done)
					return null;
			}
			if ((result != null) && !done)
				result.add(line);
		}
		if (result == null)
			return null;
		while (!result.isEmpty() && result.get(result.size() - 1).isBlank())
			result.remove(result.size() - 1);
		return String.join(org.dbgl.util.SystemUtils.EOLN, result) + org.dbgl.util.SystemUtils.EOLN;
	}

	/** the text with its [autoexec] section (see {@link #autoexecOf(String)}) replaced; the text itself if it has none */
	public static String withAutoexec(String text, String autoexec) {
		String eol = text.contains("\r\n") ? "\r\n": "\n";
		List<String> lines = split(text), out = new ArrayList<>();
		boolean in = false, done = false;
		for (String line: lines) {
			String h = header(line.trim());
			if (!done && (h != null) && h.equals("autoexec")) {
				in = true;
				done = true;
				out.addAll(split(autoexec.replaceAll("(\\r?\\n)+$", "")));
				continue;
			}
			if (in && (h != null))
				in = false;
			if (!in)
				out.add(line);
		}
		return done ? String.join(eol, out) + eol: text;
	}

	private static List<String> split(String text) {
		String t = text.startsWith("\uFEFF") ? text.substring(1): text;
		return new ArrayList<>(List.of(t.split("\r\n|\r|\n", -1)));
	}
}
