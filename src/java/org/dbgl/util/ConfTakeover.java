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
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dbgl.model.conf.ConfValueOptions;


/**
 * Alpha 49: the own settings of an older configuration file of DOSBox taken over into a newer one (e.g. the file of DOSBox
 * Staging 0.83.0 into the one 0.83.1 wrote). The two files are compared setting by setting; a value that was only the default
 * of the older version is told from one that was changed (as far as the older file says what its default was), so that new
 * defaults of the newer version stay. The newer file keeps its comments; nothing is written before it is copied.
 */
public final class ConfTakeover {

	public enum State {
		/** differs from what the older file gives as its default: changed by the user */
		OWN,
		/** differs from the newer file; the older one does not say what its default was */
		UNKNOWN,
		/** was the default of the older version: the newer version has another default now */
		OLD_DEFAULT,
		/** the newer file lists the values it knows, and this one is not among them */
		NOT_LISTED,
		/** the newer file does not have the setting (removed or renamed) */
		MISSING,
		/** a setting DOSBox Staging removed: nothing takes its place */
		REMOVED,
		/** a setting DOSBox Staging replaced by one with other values ({@link Row#from}): to be set by hand */
		MANUAL,
		/** the lines of [autoexec] */
		AUTOEXEC
	}

	/**
	 * One difference; for {@link State#AUTOEXEC} the values are the lines, joined by "\n".
	 * 
	 * @param from for a renamed setting its earlier name ("[sdl] sensitivity"; section, item and oldValue are the new ones
	 *             then), for {@link State#MANUAL} the setting that replaces it; else null
	 */
	public record Row(String section, String item, String oldValue, String newValue, State state, String from) {

		/** chosen from the start: what the user changed (and what may well be changed) */
		public boolean preselected() {
			return (state == State.OWN) || (state == State.UNKNOWN) || (state == State.AUTOEXEC);
		}

		/** false for a setting the newer file does not have: written there, it would do nothing */
		public boolean selectable() {
			return (state != State.MISSING) && (state != State.REMOVED) && (state != State.MANUAL);
		}
	}

	private static final String AUTOEXEC = "autoexec";
	private static final Pattern EN_DEFAULT = Pattern.compile("\\(('?)([^()']*?)\\1 by default\\)");
	private static final Pattern STAGING_VERSION = Pattern.compile("(?i)dosbox[- ]staging \\((\\d+\\.\\d+\\.\\d+)");

	private ConfTakeover() {
	}

	private static String header(String line) {
		String t = line.trim();
		int end = t.indexOf(']');
		return t.startsWith("[") && (end > 0) ? t.substring(1, end): null;
	}

	private static String key(String section, String item) {
		return section.toLowerCase(Locale.ROOT) + '\n' + item.toLowerCase(Locale.ROOT);
	}

	/** section and setting (as written) with the value that counts (the last one), in the order of the file; no [autoexec] */
	public static Map<String, String[]> settings(String text) {
		Map<String, String[]> result = new LinkedHashMap<>();
		String current = null;
		for (String line: lines(text)) {
			String h = header(line);
			if (h != null) {
				current = h;
				continue;
			}
			String t = line.trim();
			int eq = t.indexOf('=');
			if ((current == null) || current.equalsIgnoreCase(AUTOEXEC) || t.startsWith("#") || (eq <= 0))
				continue;
			String item = t.substring(0, eq).trim();
			result.put(key(current, item), new String[] {current, item, t.substring(eq + 1).trim()});
		}
		return result;
	}

	private static String[] lines(String text) {
		return text == null ? new String[0]: text.split("\r?\n", -1);
	}

	/** the lines of [autoexec], without empty lines at its end */
	static List<String> autoexec(String text) {
		List<String> result = new ArrayList<>();
		String current = null;
		for (String line: lines(text)) {
			String h = header(line);
			if (h != null) {
				current = h;
				continue;
			}
			if (AUTOEXEC.equalsIgnoreCase(current))
				result.add(line);
		}
		while (!result.isEmpty() && result.get(result.size() - 1).trim().isEmpty())
			result.remove(result.size() - 1);
		return result;
	}

	/** the lines of [autoexec] that do something: no empty lines, no comments */
	private static List<String> effective(List<String> lines) {
		return lines.stream().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
	}

	private static final Pattern CLASSIC_VERSION = Pattern.compile("(?i)file for DOSBox (0\\.74(?:-\\d)?)\\b");

	/**
	 * The defaults of the version that wrote the file. From a list that comes with DBGL, for DOSBox 0.74 to 0.74-3, DOSBox
	 * Staging 0.82, 0.83.0 and 0.83.1, DOSBox-X, DOSBox ECE and DOSBox SVN-Daum (the file names its version in its first line, in every language); and from what the
	 * comment above a setting says: "('opengl' by default)", "(0 by default)" - that one counts first, as it is the default
	 * on the system the file was written on (the size of the sound buffer differs between Windows and Linux). Only the
	 * English comments are read: the translations word it in many ways and translate some of the values.
	 * 
	 * @return the defaults by section and setting (see {@link #key}), more than one split by " || "; none for a setting whose
	 *         default is not known
	 */
	static Map<String, String> defaults(String text) {
		Map<String, String> result = new LinkedHashMap<>();
		String[] all = lines(text);
		for (int i = 0; (i < Math.min(5, all.length)) && result.isEmpty(); i++)
			if (all[i].trim().startsWith("#"))
				result.putAll(shipped(listFor(all[i])));
		// only DOSBox Staging writes a comment of its own above each setting; the others have all of them at the top of the
		// section, where "by default" belongs to some other setting
		if (Arrays.stream(all).limit(5).noneMatch(l -> STAGING_VERSION.matcher(l).find()))
			return result;
		String current = null;
		StringBuilder comment = new StringBuilder();
		for (String line: all) {
			String h = header(line);
			String t = line.trim();
			if (h != null) {
				current = h;
				comment.setLength(0);
			} else if (t.startsWith("#")) {
				comment.append(t.substring(1).trim()).append(' ');
			} else if (t.isEmpty()) {
				comment.setLength(0);
			} else if ((current != null) && (t.indexOf('=') > 0)) {
				String found = defaultIn(comment.toString());
				if (found != null)
					result.put(key(current, t.substring(0, t.indexOf('=')).trim()), found);
				comment.setLength(0);
			}
		}
		return result;
	}

	/** which DOSBox wrote the file, going by its first lines: the start of the name of its list of defaults, "" if not known */
	private static String kind(String text) {
		return Arrays.stream(lines(text)).limit(5).map(ConfTakeover::listFor).filter(java.util.Objects::nonNull).map(n -> n.replaceAll("-[0-9].*", "")).findFirst().orElse("");
	}

	/** the name of the list of defaults for the version a first line of a file names, null if there is none */
	public static String listFor(String line) {
		Matcher staging = STAGING_VERSION.matcher(line);
		if (staging.find()) {
			String version = staging.group(1);
			// the three releases of 0.82 have the same settings
			return "staging-" + (version.startsWith("0.82.") ? "0.82.2": version);
		}
		if (CLASSIC_VERSION.matcher(line).find())
			return "dosbox-0.74-3";
		// DOSBox-X comes out every month: one list for all of its versions (its defaults change seldom)
		if (line.matches("(?i).*file for DOSBox-X\\b.*"))
			return "dosbox-x";
		if (line.matches("(?i).*file for DOSBox ECE\\b.*"))
			return "dosbox-ece";
		if (line.matches("(?i).*file for DOSBox SVN(?![\\w-]).*"))
			return "dosbox-svn"; // the builds of the development version (not "SVN-Daum")
		return line.matches("(?i).*file for DOSBox SVN-Daum\\b.*") ? "dosbox-daum": null;
	}

	/** a list of defaults that comes with DBGL ("section|setting=value" a line), empty if there is none of that name */
	public static Map<String, String> shipped(String name) {
		Map<String, String> result = new LinkedHashMap<>();
		try (java.io.InputStream in = name == null ? null: ConfTakeover.class.getResourceAsStream("/confdefaults/" + name + ".txt")) {
			if (in != null)
				for (String line: lines(new String(in.readAllBytes(), StandardCharsets.UTF_8))) {
					int bar = line.indexOf('|'), eq = line.indexOf('=');
					if (!line.startsWith("#") && (bar > 0) && (eq > bar))
						result.put(key(line.substring(0, bar), line.substring(bar + 1, eq)), line.substring(eq + 1));
				}
		} catch (IOException e) {
			// no list then: the comments of the file are read
		}
		return result;
	}

	public static String defaultIn(String comment) {
		Matcher en = EN_DEFAULT.matcher(comment);
		if (en.find()) {
			String value = en.group(2).trim();
			if (!en.group(1).isEmpty())
				return value;
			return switch (value) {
				case "unset", "empty" -> "";
				case "disabled" -> "off";
				case "enabled" -> "on";
				default -> value.contains(" ") ? null: value;
			};
		}
		return null;
	}

	/** "true" and "on" are the same to DOSBox, as are "false" and "off"; upper and lower case too */
	public static boolean same(String a, String b) {
		return norm(a).equals(norm(b));
	}

	/** the code DOSBox Staging has for the language a file of translations of DOSBox is named after, null if it has none */
	public static String languageCode(String file) {
		String name = file.replace('\\', '/').substring(file.replace('\\', '/').lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
		String[][] known = {{"german", "de"}, {"deutsch", "de"}, {"english", "en"}, {"spanish", "es"}, {"espa", "es"}, {"french", "fr"}, {"fran", "fr"}, {"italian", "it"},
				{"dutch", "nl"}, {"nederlands", "nl"}, {"polish", "pl"}, {"polski", "pl"}, {"brazil", "pt_BR"}, {"portug", "pt_BR"}, {"russian", "ru"}};
		for (String[] k: known)
			if (name.startsWith(k[0]))
				return k[1];
		return null;
	}

	/** "mapper-sdl2-0.83.0.map" and "mapper-sdl2-0.83.1.map": each version names its own file */
	private static boolean sameButVersion(String a, String b) {
		return a.replaceAll("\\d+(\\.\\d+)+", "*").equalsIgnoreCase(b.replaceAll("\\d+(\\.\\d+)+", "*"));
	}

	private static String norm(String value) {
		String v = value == null ? "": value.trim().toLowerCase(Locale.ROOT);
		return v.equals("true") ? "on": v.equals("false") ? "off": v;
	}

	/**
	 * @param oldText the older file (the source)
	 * @param newText the newer file (the target, the one that is changed)
	 * @return the settings of the older file whose value differs from the newer one, in the order of the older file, then the
	 *         lines of [autoexec] if they differ
	 */
	public static List<Row> compare(String oldText, String newText) {
		Map<String, String[]> oldSettings = settings(oldText), newSettings = settings(newText);
		Map<String, String> oldDefaults = defaults(oldText);
		ConfValueOptions options = ConfValueOptions.parse(newText);
		// from one kind of DOSBox to another (DOSBox Staging, DOSBox-X, ...), as far as the files say what wrote them
		boolean otherKind = !kind(oldText).equals(kind(newText));
		// what became of older settings is known for DOSBox Staging only
		boolean stagingTarget = Arrays.stream(lines(newText)).limit(5).anyMatch(l -> STAGING_VERSION.matcher(l).find());
		// a file that says "on" and "off" all over and nowhere "true" or "false" (DOSBox Staging 0.83)
		boolean writesOnOff = newSettings.values().stream().anyMatch(v -> v[2].equalsIgnoreCase("on") || v[2].equalsIgnoreCase("off"))
				&& newSettings.values().stream().noneMatch(v -> v[2].equalsIgnoreCase("true") || v[2].equalsIgnoreCase("false"));
		List<Row> result = new ArrayList<>();
		for (Map.Entry<String, String[]> entry: oldSettings.entrySet()) {
			String section = entry.getValue()[0], item = entry.getValue()[1];
			String value = entry.getValue()[2], from = null;
			String[] target = newSettings.get(entry.getKey());
			String oldDefault = oldDefaults.get(entry.getKey());
			final String written = value;
			boolean wasDefault = (oldDefault != null) && Arrays.stream(oldDefault.split("\\s*\\|\\|\\s*", -1)).anyMatch(d -> same(d, written));
			// "mapper-0.74.map", "mapper-sdl2-0.83.1.map": each version names its own file; only another name is a change
			if (item.equalsIgnoreCase("mapperfile") && (wasDefault || ((oldDefault != null) && sameButVersion(oldDefault, value))
					|| ((target != null) && sameButVersion(value, target[2])) || value.matches("(?i)mapper-[\\w.\\-]+\\.map")))
				continue;
			// nothing set, and not known whether that was a change: the newer file keeps what it has
			if (value.isEmpty() && !wasDefault && (oldDefault == null))
				continue;
			if (target == null) {
				if (wasDefault || value.isEmpty())
					continue; // never changed: nothing is lost
				// what DOSBox Staging made of the setting: renamed (its value as the new setting takes it), replaced, removed
				String[] next = stagingTarget ? ProfileChecks.stagingSuccessor(section, item, value): null;
				// two settings DOSBox Staging 0.83 still reads, but no longer writes into its file
				if ((next == null) && stagingTarget && section.equalsIgnoreCase("cpu") && item.equalsIgnoreCase("cycles")) {
					// "max" and a number ("fixed 12000", "12000") are what cpu_cycles takes; "auto ..." was the default
					Matcher fixed = Pattern.compile("(?i)(?:fixed\\s+)?(\\d+)").matcher(value);
					next = new String[] {"manual", "cpu", "cpu_cycles", value.equalsIgnoreCase("max") ? "max": fixed.matches() ? fixed.group(1): null};
				} else if ((next == null) && stagingTarget && section.equalsIgnoreCase("sdl") && item.equalsIgnoreCase("windowresolution")) {
					next = new String[] {"manual", "sdl", "window_size", value.matches("\\d+x\\d+") ? value: null};
				}
				String kind = next == null ? "": next[0];
				String converted = next == null ? null: next[3];
				if (kind.equals("manual") && item.equalsIgnoreCase("capture_mouse")) {
					// "onclick middlegame": the first word is what mouse_capture takes
					String first = value.split("\\s+")[0].toLowerCase(Locale.ROOT);
					converted = List.of("seamless", "onclick", "onstart", "nomouse").contains(first) ? first: null;
				}
				target = (next == null) || (next[1] == null) ? null: newSettings.get(key(next[1], next[2]));
				if (kind.equals("removed")) {
					result.add(new Row(section, item, value, null, State.REMOVED, null));
					continue;
				}
				if (target == null) {
					result.add(new Row(section, item, value, null, State.MISSING, null));
					continue;
				}
				if (converted == null) {
					if (kind.equals("manual"))
						result.add(new Row(section, item, value, target[2], State.MANUAL, "[" + target[0] + "] " + target[1]));
					continue; // "disney = false": nothing to set
				}
				from = "[" + section + "] " + item;
				value = converted;
			}
			ConfValueOptions.Options known = options.get(target[0], target[1]);
			// "true" where the newer file says "on" and "off": written as the newer file writes it
			boolean onOff = writesOnOff || target[2].equalsIgnoreCase("on") || target[2].equalsIgnoreCase("off")
					|| ((known != null) && known.getValues().stream().anyMatch(v -> v.equalsIgnoreCase("on") || v.equalsIgnoreCase("off")));
			if (onOff && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")))
				value = value.equalsIgnoreCase("true") ? "on": "off";
			boolean languageFile = false;
			if (stagingTarget && target[0].equalsIgnoreCase("dosbox") && target[1].equalsIgnoreCase("language") && !value.isEmpty()
					&& !value.matches("[A-Za-z]{2}(_[A-Za-z]{2})?")) {
				// "german-0.74.lang" was a file of translations; DOSBox Staging takes the code of a language
				String code = languageCode(value);
				if ((code != null) && (from == null))
					from = value;
				languageFile = code == null;
				value = code == null ? value: code;
			} else if (!stagingTarget && otherKind && target[0].equalsIgnoreCase("dosbox") && target[1].equalsIgnoreCase("language") && !value.isEmpty()) {
				languageFile = true; // each kind of DOSBox has its own files of translations ("de" is none of DOSBox-X)
			}
			final String wanted = value;
			if (same(value, target[2]))
				continue;
			State state;
			if (wasDefault)
				state = State.OLD_DEFAULT;
			else if (languageFile)
				state = State.NOT_LISTED;
			else if ((known != null) && !known.isFreeForm() && (known.getValues().size() > 1) && !value.isEmpty() && !value.contains(" ") && !value.contains(",")
					&& known.getValues().stream().noneMatch(v -> same(v, wanted)))
				state = State.NOT_LISTED;
			else
				state = oldDefault == null ? State.UNKNOWN: State.OWN;
			result.add(new Row(target[0], target[1], value, target[2], state, from));
		}
		List<String> oldLines = autoexec(oldText), newLines = autoexec(newText);
		if (!effective(oldLines).isEmpty() && !effective(oldLines).equals(effective(newLines)))
			result.add(new Row(AUTOEXEC, "", String.join("\n", oldLines), String.join("\n", newLines), State.AUTOEXEC, null));
		return result;
	}

	/**
	 * @param newText the newer file as it is
	 * @param chosen  the rows of {@link #compare} to take over
	 * @return the newer file with the chosen values of the older one; its comments, its order and its line ends stay
	 */
	public static String apply(String newText, List<Row> chosen) {
		String eol = newText.contains("\r\n") ? "\r\n": "\n";
		List<String> all = new ArrayList<>(Arrays.asList(newText.split("\r?\n", -1)));
		for (Row row: chosen) {
			if ((row.state() == State.AUTOEXEC) || !row.selectable())
				continue;
			// the line that counts (the last one of that name); written as it is, "name = value" or "name=value"
			String current = null;
			int found = -1;
			for (int i = 0; i < all.size(); i++) {
				String h = header(all.get(i));
				String t = all.get(i).trim();
				int eq = t.indexOf('=');
				if (h != null)
					current = h;
				else if ((current != null) && current.equalsIgnoreCase(row.section()) && !t.startsWith("#") && (eq > 0) && t.substring(0, eq).trim().equalsIgnoreCase(row.item()))
					found = i;
			}
			if (found >= 0) {
				String line = all.get(found);
				int eq = line.indexOf('=');
				boolean spaced = (eq + 1 < line.length()) && (line.charAt(eq + 1) == ' ') || line.substring(0, eq).endsWith(" ");
				all.set(found, line.substring(0, eq + 1) + (spaced && !row.oldValue().isEmpty() ? " ": "") + row.oldValue());
			}
		}
		String result = String.join(eol, all);
		for (Row row: chosen)
			if (row.state() == State.AUTOEXEC)
				result = withAutoexec(result, Arrays.asList(row.oldValue().split("\n", -1)));
		return result;
	}

	/**
	 * Test 16: what taking over changes in the file, to be shown before anything is written.
	 *
	 * @param before the file as it is
	 * @param after  what {@link #apply} makes of it
	 * @param full   every line of the file; else only the lines that change, each below the name of its section
	 * @return the lines: '-' gone, '+' new, ' ' staying (also the names of the sections)
	 */
	public static List<ProfileFindings.DiffLine> preview(String before, String after, boolean full) {
		List<String> a = Arrays.asList(before.split("\\r?\\n", -1)), b = Arrays.asList(after.split("\\r?\\n", -1));
		// the settings are changed in their lines; only [autoexec], the last section, may get more or fewer lines
		int autoexecA = a.size(), autoexecB = b.size();
		for (int i = 0; (i < a.size()) && (autoexecA == a.size()); i++)
			if (AUTOEXEC.equalsIgnoreCase(header(a.get(i))))
				autoexecA = i;
		for (int i = 0; (i < b.size()) && (autoexecB == b.size()); i++)
			if (AUTOEXEC.equalsIgnoreCase(header(b.get(i))))
				autoexecB = i;
		List<ProfileFindings.DiffLine> result = new ArrayList<>();
		if (autoexecA != autoexecB) { // not as expected: line by line all the same
			result.addAll(ProfileFindings.diffLines(a, b, full));
			return result;
		}
		String section = null, told = null;
		for (int i = 0; i < autoexecA; i++) {
			String h = header(a.get(i));
			if (h != null)
				section = a.get(i).trim();
			boolean same = a.get(i).equals(b.get(i));
			if (full && same) {
				result.add(new ProfileFindings.DiffLine(' ', a.get(i)));
			} else if (!same) {
				if (!full && (section != null) && !section.equals(told)) {
					result.add(new ProfileFindings.DiffLine(' ', section));
					told = section;
				}
				result.add(new ProfileFindings.DiffLine('-', a.get(i)));
				result.add(new ProfileFindings.DiffLine('+', b.get(i)));
			}
		}
		List<ProfileFindings.DiffLine> tail = ProfileFindings.diffLines(a.subList(autoexecA, a.size()), b.subList(autoexecB, b.size()), true);
		if (full || tail.stream().anyMatch(d -> d.kind() != ' '))
			for (ProfileFindings.DiffLine d: tail)
				if (full || (d.kind() != ' ') || (header(d.text()) != null))
					result.add(d);
		return result;
	}

	/** the lines of [autoexec] replaced (the section added at the end if there is none) */
	public static String withAutoexec(String text, List<String> autoexec) {
		String eol = text.contains("\r\n") ? "\r\n": "\n";
		List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
		boolean endsWithEol = text.endsWith("\n");
		if (endsWithEol)
			lines.remove(lines.size() - 1);
		int start = -1;
		for (int i = 0; (i < lines.size()) && (start < 0); i++)
			if (AUTOEXEC.equalsIgnoreCase(header(lines.get(i))))
				start = i;
		if (start < 0) {
			if (!lines.isEmpty() && !lines.get(lines.size() - 1).trim().isEmpty())
				lines.add("");
			lines.add("[" + AUTOEXEC + "]");
			start = lines.size() - 1;
		}
		// the comment DOSBox writes at the top of the section stays (the one of the older file is not taken)
		int from = start + 1;
		while ((from < lines.size()) && (lines.get(from).trim().startsWith("#") || lines.get(from).trim().isEmpty()))
			from++;
		lines.subList(from, lines.size()).clear();
		List<String> add = new ArrayList<>(autoexec);
		while (!add.isEmpty() && (add.get(0).trim().startsWith("#") || add.get(0).trim().isEmpty()))
			add.remove(0);
		lines.addAll(add);
		return String.join(eol, lines) + (endsWithEol || !add.isEmpty() ? eol: "");
	}

	/** a file read byte by byte (each byte one character), so that it is written again as it was, whatever its charset */
	public static String read(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
	}

	/**
	 * The file written with the new text, after a copy of it as it was has been made next to it.
	 * 
	 * @return the copy (name of the file, date and time, ".bak")
	 */
	public static File write(File file, String text) throws IOException {
		File backup = new File(file.getParentFile(), file.getName() + '.' + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".bak");
		for (int i = 2; backup.exists(); i++)
			backup = new File(file.getParentFile(), file.getName() + '.' + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + '-' + i + ".bak");
		Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
		Files.write(file.toPath(), text.getBytes(StandardCharsets.ISO_8859_1));
		return backup;
	}
}
