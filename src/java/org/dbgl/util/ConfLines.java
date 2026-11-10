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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;


/**
 * What "Check profiles" finds in the lines of the configuration file itself (since alpha 46), with what is needed to repair it:
 * <ul>
 * <li>lines DOSBox cannot read: "fullscreen true" (no "="), "[sdl" (no "]"), a setting before the first section</li>
 * <li>a setting twice in its section (the last one counts)</li>
 * <li>a number outside of what the DOSBox version takes (from the comments of its configuration, e.g. "Valid range is 0 to
 * 100", and memsize 1 to 63 for DOSBox 0.74)</li>
 * <li>a command or mount option the DOSBox version does not have (e.g. "-t overlay" with DOSBox 0.74, IMGMAKE without DOSBox-X)</li>
 * </ul>
 * The arguments of each problem: first what the text shows, then the raw lines (as in the file, bytes as ISO-8859-1) the solution
 * works with.
 */
public final class ConfLines {

	private ConfLines() {
	}

	private static final Pattern HEADER = Pattern.compile("^\\[([^\\]]*)\\].*$"); // anything after the "]" DOSBox leaves out
	private static final Pattern WORD_VALUE = Pattern.compile("^([A-Za-z0-9_]+)(?:\\s*:\\s*|\\s+)(.+)$");
	private static final Pattern NUMBER = Pattern.compile("^-?\\d+(?:\\.\\d+)?$");
	private static final Pattern RANGE = Pattern.compile("(?i)valid range is (?:from )?(-?\\d+(?:\\.\\d+)?)\\s*%?\\s*(?:ns\\s*)?to\\s*(-?\\d+(?:\\.\\d+)?)");
	private static final Pattern NAMED = Pattern.compile("^#\\s*([a-z0-9_ ]+?):\\s");

	/** programs of DOSBox-X on its drive Z: (the other kinds of DOSBox do not have them) */
	private static final Set<String> X_ONLY = Set.of("imgmake", "vhdmake", "imgswap", "a20gate", "biostest", "capmouse", "flagsave", "int2fdbg", "nmitest", "prinfo",
		"vesamoed", "vfrcrate", "dx-capture", "addkey", "dsxmenu");
	/** commands of DOSBox Staging and DOSBox-X, not of DOSBox 0.74 and the builds based on it */
	private static final Set<String> STAGING_AND_X = Set.of("autotype", "ls");

	/** ranges from the comments of the configuration of a DOSBox version, per file and its time */
	private static final Map<String, Map<String, double[]>> RANGES = new ConcurrentHashMap<>();

	private static int family(DosboxVersion db) {
		Generation g = db == null ? null: GenerationAwareConfigurable.determineGen(db);
		return g == null ? -1: GenerationAwareConfigurable.familyIdx(g);
	}

	private static boolean oldDosbox(int family) { // DOSBox 0.74, SVN, ECE: no overlay, no -ro, no IDE
		return (family == 0) || (family == 1) || (family == 3);
	}

	public static List<ProfileChecks.Problem> check(Profile prof) {
		List<ProfileChecks.Problem> result = new ArrayList<>();
		File file = prof.getConfigurationCanonicalFile();
		if ((file == null) || !file.isFile())
			return result;
		String raw;
		try {
			raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		} catch (IOException e) {
			return result;
		}
		if (raw.startsWith("ï»¿"))
			raw = raw.substring(3);
		Charset cs = ProfileFindings.fileCharset(prof);
		DosboxVersion db = prof.getDosboxVersion();
		Configuration dbConf = db == null ? null: db.getConfiguration();
		int family = family(db);
		Map<String, double[]> ranges = ranges(db);
		String section = null;
		boolean custom = false;
		Map<String, String[]> seen = new LinkedHashMap<>(); // section.item -> first value, last value
		Map<String, Integer> times = new HashMap<>();
		int brokenAt = -1; // the problem of a heading without "]": the settings below it are added to it (as "[section] item")
		List<String> belowBroken = new ArrayList<>();
		for (String line: raw.split("\r?\n")) {
			String t = line.trim();
			if (t.equalsIgnoreCase("#REM DBGL-CUSTOM") || t.equalsIgnoreCase("#REM /DBGL-CUSTOM")) {
				custom = !t.contains("/");
				continue;
			}
			if (custom || t.isEmpty() || t.startsWith("#") || t.startsWith("%"))
				continue;
			String shown = ProfileFindings.readable(t, cs);
			Matcher h = HEADER.matcher(t);
			if (h.matches()) {
				section = h.group(1).trim().toLowerCase(Locale.ROOT);
				brokenAt = finish(result, brokenAt, belowBroken);
				// "[autoexec] keyb gr": DOSBox reads up to the "]" only (a comment after it is fine); repaired: on its own line
				String rest = t.substring(t.indexOf(']') + 1).trim();
				if (!rest.isEmpty() && !rest.startsWith("#") && !rest.startsWith(";"))
					result.add(new ProfileChecks.Problem("syntaxheadertext", shown, t, t.substring(0, t.indexOf(']') + 1) + "\n" + rest));
				continue;
			}
			if (t.startsWith("[")) { // DOSBox skips it: the settings below it go to the section before
				brokenAt = finish(result, brokenAt, belowBroken);
				String name = t.substring(1).trim().toLowerCase(Locale.ROOT);
				boolean known = (dbConf != null) && java.util.Arrays.stream(dbConf.getSectionNames()).anyMatch(name::equalsIgnoreCase);
				result.add(new ProfileChecks.Problem("syntaxheader", shown, t, known ? "[" + name + "]": "", ""));
				brokenAt = result.size() - 1;
				continue;
			}
			if ((brokenAt >= 0) && (section != null) && (t.indexOf('=') > 0))
				belowBroken.add("[" + section + "] " + t.substring(0, t.indexOf('=')).trim().toLowerCase(Locale.ROOT));
			if ("autoexec".equals(section)) {
				checkAutoexecLine(result, t, shown, family, db);
				continue;
			}
			int eq = t.indexOf('=');
			if (section == null) { // DOSBox reads nothing before the first section
				String item = eq > 0 ? t.substring(0, eq).trim().toLowerCase(Locale.ROOT): "";
				String target = eq > 0 ? sectionOf(dbConf, item): null;
				result.add(new ProfileChecks.Problem("syntaxnosection", shown, target == null ? "": target, t, item, eq > 0 ? t.substring(eq + 1).trim(): ""));
				continue;
			}
			if (eq <= 0) {
				Matcher wv = WORD_VALUE.matcher(t);
				String repaired = "";
				if (wv.matches() && (dbConf != null) && dbConf.hasValue(section, wv.group(1).toLowerCase(Locale.ROOT)))
					repaired = wv.group(1).toLowerCase(Locale.ROOT) + "=" + wv.group(2).trim();
				result.add(new ProfileChecks.Problem("syntaxnoequals", shown, section, ProfileFindings.readable(repaired, cs), t, repaired));
				continue;
			}
			String item = t.substring(0, eq).trim().toLowerCase(Locale.ROOT), value = t.substring(eq + 1).trim();
			String key = section + "." + item;
			times.merge(key, 1, Integer::sum);
			seen.computeIfAbsent(key, k -> new String[] {value, value})[1] = value;
			double[] range = ranges.get(key);
			if ((range != null) && NUMBER.matcher(value).matches()) {
				double v = Double.parseDouble(value);
				if ((v < range[0]) || (v > range[1])) {
					double to = v < range[0] ? range[0]: range[1];
					String clamped = (to == Math.rint(to)) ? String.valueOf((long)to): String.valueOf(to);
					result.add(new ProfileChecks.Problem("outofrange", section, item, ProfileFindings.readable(value, cs), number(range[0]), number(range[1]), clamped));
				}
			}
		}
		finish(result, brokenAt, belowBroken);
		seen.forEach((key, v) -> {
			if (times.get(key) > 1) {
				int dot = key.indexOf('.');
				result.add(new ProfileChecks.Problem("duplicatekey", key.substring(0, dot), key.substring(dot + 1), ProfileFindings.readable(v[0], cs), ProfileFindings.readable(v[1], cs)));
			}
		});
		return result;
	}

	/** the settings below a heading without "]" added to its problem (told there, not as being in the wrong section) */
	private static int finish(List<ProfileChecks.Problem> result, int brokenAt, List<String> below) {
		if (brokenAt >= 0) {
			Object[] args = result.get(brokenAt).args().clone();
			args[3] = String.join("|", below);
			result.set(brokenAt, new ProfileChecks.Problem("syntaxheader", args));
			below.clear();
		}
		return -1;
	}

	private static String number(double d) {
		return (d == Math.rint(d)) ? String.valueOf((long)d): String.valueOf(d);
	}

	/** the one section of the DOSBox configuration that has the setting, or null */
	private static String sectionOf(Configuration dbConf, String item) {
		if ((dbConf == null) || item.isEmpty())
			return null;
		String found = null;
		for (String s: dbConf.getSectionNames())
			if (!s.equalsIgnoreCase("autoexec") && dbConf.hasValue(s, item)) {
				if (found != null)
					return null; // in more than one: DBGL cannot know
				found = s;
			}
		return found;
	}

	/** a command or mount option of [autoexec] the kind of DOSBox does not have */
	private static void checkAutoexecLine(List<ProfileChecks.Problem> result, String t, String shown, int family, DosboxVersion db) {
		if (family < 0)
			return;
		String l = t.startsWith("@") ? t.substring(1).trim(): t;
		String lower = l.toLowerCase(Locale.ROOT);
		if (lower.startsWith("rem ") || lower.equals("rem"))
			return;
		String title = db.getTitle();
		if (lower.matches("^(?:img)?mount\\s.*") && oldDosbox(family)) {
			List<String> tokens = tokens(l);
			for (int i = 0; i < tokens.size(); i++) {
				String tk = tokens.get(i).toLowerCase(Locale.ROOT);
				if (tk.equals("-t") && (i + 1 < tokens.size()) && tokens.get(i + 1).equalsIgnoreCase("overlay"))
					result.add(new ProfileChecks.Problem("notinversion", "-t overlay", shown, title, t, "")); // the line goes
				else if (tk.equals("-ro"))
					result.add(new ProfileChecks.Problem("notinversion", "-ro", shown, title, t, without(tokens, i, 1)));
				else if (tk.equals("-ide"))
					result.add(new ProfileChecks.Problem("notinversion", "-ide", shown, title, t,
						without(tokens, i, (i + 1 < tokens.size()) && !tokens.get(i + 1).startsWith("-") ? 2: 1)));
			}
			return;
		}
		String first = lower.split("\\s+")[0];
		if (first.equals("lh") || first.equals("loadhigh"))
			first = lower.split("\\s+").length > 1 ? lower.split("\\s+")[1]: "";
		if (first.contains("\\") || first.contains("/") || first.contains(":"))
			return; // a program of the game, with its path
		String command = first.replaceFirst("\\.(com|exe)$", "");
		boolean x = family == 5, stagingOrX = (family == 4) || x;
		if ((X_ONLY.contains(command) && !x) || (STAGING_AND_X.contains(command) && !stagingOrX))
			result.add(new ProfileChecks.Problem("notinversion", command.toUpperCase(Locale.ROOT), shown, title, t, ""));
	}

	/** the words of a line, a quoted path as one */
	private static List<String> tokens(String line) {
		List<String> result = new ArrayList<>();
		Matcher m = Pattern.compile("\"[^\"]*\"|\\S+").matcher(line);
		while (m.find())
			result.add(m.group());
		return result;
	}

	private static String without(List<String> tokens, int from, int count) {
		List<String> rest = new ArrayList<>(tokens);
		for (int i = 0; i < count; i++)
			rest.remove(from);
		return String.join(" ", rest);
	}

	/**
	 * The ranges of numbers the DOSBox version takes: from the comments of its configuration file (Staging: "Valid range is 0 to
	 * 100." above the setting; DOSBox-X and 0.74: "name: ..." in the block of the section), and memsize 1 to 63 for DOSBox 0.74
	 * and SVN (more is refused, the default used).
	 */
	public static Map<String, double[]> ranges(DosboxVersion db) {
		if (db == null)
			return Map.of();
		File conf = db.getConfigurationCanonicalFile();
		String cacheKey = conf == null ? "": conf.getPath() + "|" + conf.lastModified() + "|" + family(db);
		return RANGES.computeIfAbsent(cacheKey, k -> {
			Map<String, double[]> result = new HashMap<>();
			int family = family(db);
			if ((family == 0) || (family == 1))
				result.put("dosbox.memsize", new double[] {1, 63});
			if ((conf == null) || !conf.isFile())
				return result;
			try {
				List<String> all = Files.readAllLines(conf.toPath(), StandardCharsets.ISO_8859_1);
				// the settings of each section, to tell "#  memsize: ..." (about memsize) from "#   on: ..." (about a value)
				Map<String, Set<String>> items = new HashMap<>();
				String s0 = null;
				for (String line: all) {
					String t = line.trim();
					Matcher h = HEADER.matcher(t);
					if (h.matches())
						s0 = h.group(1).trim().toLowerCase(Locale.ROOT);
					else if ((s0 != null) && !t.startsWith("#") && (t.indexOf('=') > 0))
						items.computeIfAbsent(s0, x -> new java.util.HashSet<>()).add(t.substring(0, t.indexOf('=')).trim().toLowerCase(Locale.ROOT));
				}
				String section = null;
				List<String> block = new ArrayList<>();
				for (String line: all) {
					String t = line.trim();
					Matcher h = HEADER.matcher(t);
					if (h.matches()) {
						named(result, section, block, items.getOrDefault(section, Set.of()));
						section = h.group(1).trim().toLowerCase(Locale.ROOT);
						block.clear();
					} else if (t.startsWith("#")) {
						block.add(t);
					} else if ((section != null) && (t.indexOf('=') > 0)) {
						String item = t.substring(0, t.indexOf('=')).trim().toLowerCase(Locale.ROOT);
						Set<String> known = items.getOrDefault(section, Set.of());
						if (block.stream().noneMatch(b -> isNamed(b, known))) { // Staging: the comment is about this setting
							Matcher r = RANGE.matcher(String.join(" ", block).replace("#", " ").replaceAll("\\s+", " "));
							if (r.find())
								result.putIfAbsent(section + "." + item, new double[] {Double.parseDouble(r.group(1)), Double.parseDouble(r.group(2))});
						} else {
							named(result, section, block, known);
						}
						block.clear();
					}
				}
			} catch (IOException e) {
				// without ranges
			}
			return result;
		});
	}

	/** "#  name: description ... Valid range is a to b." per name, in a comment block about a whole section */
	private static boolean isNamed(String line, Set<String> known) {
		Matcher n = NAMED.matcher(line);
		return n.find() && known.contains(n.group(1).trim().toLowerCase(Locale.ROOT));
	}

	private static void named(Map<String, double[]> result, String section, List<String> block, Set<String> known) {
		if (section == null)
			return;
		String name = null;
		StringBuilder text = new StringBuilder();
		for (String b: block) {
			Matcher n = NAMED.matcher(b);
			if (n.find() && known.contains(n.group(1).trim().toLowerCase(Locale.ROOT))) {
				rangeOf(result, section, name, text.toString());
				name = n.group(1).trim().toLowerCase(Locale.ROOT);
				text.setLength(0);
			}
			text.append(' ').append(b.replace("#", " "));
		}
		rangeOf(result, section, name, text.toString());
	}

	private static void rangeOf(Map<String, double[]> result, String section, String name, String text) {
		if (name == null)
			return;
		Matcher r = RANGE.matcher(text.replaceAll("\\s+", " "));
		if (r.find())
			result.putIfAbsent(section + "." + name, new double[] {Double.parseDouble(r.group(1)), Double.parseDouble(r.group(2))});
	}
}
