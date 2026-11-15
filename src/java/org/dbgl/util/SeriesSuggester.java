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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dbgl.model.aggregate.Profile;


/**
 * Alpha 49: game series suggested from the titles of the profiles: "Commander Keen 4: Secret of the Oracle" and "Commander Keen
 * 5" are parts 4 and 5 of "Commander Keen"; "Doom" next to "Doom II" is its part 1. Only suggestions: the user ticks what is right.
 */
public final class SeriesSuggester {

	/** a profile and the part it would get (0: none, the title has no number) */
	public record Member(Profile profile, int part) {
	}

	/**
	 * a series that could be made: its name and its profiles, by part. Not sure: found by the first words of the titles alone
	 * ("Pro Pinball The Web", "Pro Pinball Timeshock"), not ticked at first
	 */
	public record Suggestion(String series, List<Member> members, boolean sure) {
		public Suggestion(String series, List<Member> members) {
			this(series, members, true);
		}
	}

	/** what a title says: the name without the number and what follows it, and the number (0 if it has none) */
	public record Parsed(String base, int part, boolean plain) {
	}

	private static final int MAX_PART = 20; // "SimCity 2000", "Fifa 96" or "1942" are not parts
	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
	private static final List<String> WORDS = List.of("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten");
	private static final Pattern BRACKETS_PATRN = Pattern.compile("\\s*[(\\[][^)\\]]*[)\\]]\\s*$");
	private static final String KEYWORDS = "episode|part|teil|vol\\.?|volume|chapter|kapitel|book|buch";
	private static final Pattern NUMBER_PATRN = Pattern.compile("^(.*?\\S)[\\s,]+(?:(?:" + KEYWORDS + "|#)\\s*)?([0-9]{1,2}|[IVX]{1,5})(?:\\.0)?$", Pattern.CASE_INSENSITIVE); // "Comanche 2.0" too
	/** "Might and Magic Book One": a number as a word counts only after "Book", "Part", ... */
	private static final Pattern WORD_PATRN = Pattern.compile("^(.*?\\S)[\\s,]+(?:" + KEYWORDS + ")\\s+(one|two|three|four|five|six|seven|eight|nine|ten)$", Pattern.CASE_INSENSITIVE);
	private static final String[] SEPARATORS = {": ", " - ", " – ", " — "};
	/** words a series name does not end with ("Prince of" Persia / Destiny is no series) */
	private static final java.util.Set<String> STOP = java.util.Set.of("of", "the", "a", "an", "in", "on", "at", "to", "for", "and", "from", "with", "der", "die", "das", "von", "und", "im", "des", "le", "la",
		"de", "du");

	private SeriesSuggester() {
	}

	/** the title without what stands in brackets at its end: "(v1.9)", "[Floppy]" */
	static String clean(String title) {
		String t = title == null ? "": title.strip();
		for (Matcher m = BRACKETS_PATRN.matcher(t); m.find(); m = BRACKETS_PATRN.matcher(t))
			t = t.substring(0, m.start()).strip();
		return t.replace("\u00b2", " 2").replace("\u00b3", " 3"); // "Vision\u00b2: Aufbruch ins Weltall"
	}

	/** where the next subtitle starts at or after from: {index, length of the separator}, null if there is none */
	private static int[] nextSeparator(String t, int from) {
		int[] best = null;
		for (String sep: SEPARATORS) {
			int at = t.indexOf(sep, from);
			if ((at > 0) && ((best == null) || (at < best[0])))
				best = new int[] {at, sep.length()};
		}
		return best;
	}

	/** "Space Quest III" -> ("Space Quest", 3), null if the text does not end with a part */
	private static Parsed numbered(String text) {
		Matcher m = NUMBER_PATRN.matcher(text);
		int part = -1;
		if (m.matches())
			part = number(m.group(2));
		if ((part < 1) || (part > MAX_PART)) {
			m = WORD_PATRN.matcher(text);
			part = m.matches() ? WORDS.indexOf(m.group(2).toLowerCase(Locale.ROOT)): -1;
		}
		if ((part < 1) || (part > MAX_PART))
			return null;
		String base = m.group(1).replaceAll("[\\s,:–—-]+$", "");
		return base.isEmpty() ? null: new Parsed(base, part, false);
	}

	/**
	 * "Space Quest III: The Pirates of Pestulon" -> ("Space Quest", 3); "Doom" -> ("Doom", 0); the number can also stand in a later
	 * part of the title: "Star Wars: Rebel Assault II: The Hidden Empire" -> ("Star Wars: Rebel Assault", 2)
	 */
	public static Parsed parse(String title) {
		String t = clean(title);
		int[] first = nextSeparator(t, 0);
		for (int[] sep = first; ; sep = nextSeparator(t, sep[0] + sep[1])) {
			Parsed p = numbered(sep == null ? t: t.substring(0, sep[0]).strip());
			if (p != null)
				return p;
			if (sep == null)
				break;
		}
		return new Parsed(first == null ? t: t.substring(0, first[0]).strip(), 0, first == null); // plain: the name alone, no subtitle
	}

	/** "Pagan: Ultima" -> "Ultima": what follows the last separator; the text itself if it has none */
	private static String lastSegment(String base) {
		String result = base;
		for (int[] sep = nextSeparator(base, 0); sep != null; sep = nextSeparator(base, sep[0] + sep[1]))
			result = base.substring(sep[0] + sep[1]).strip();
		return result;
	}

	private static int number(String s) {
		if (s.matches("[0-9]+"))
			return Integer.parseInt(s);
		String roman = s.toUpperCase(Locale.ROOT);
		for (int i = 1; i < ROMAN.length; i++)
			if (ROMAN[i].equals(roman))
				return i;
		return -1;
	}

	/** names that mean the same series: case, punctuation and a leading article do not count */
	static String key(String base) {
		String k = base.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
		return k.replaceFirst("^(the|der|die|das) ", "");
	}

	private static boolean inSeries(Profile p) {
		return (p.getSeries() != null) && !p.getSeries().isBlank();
	}

	private static long distinctTitles(List<Member> members) {
		return members.stream().map(m -> key(clean(m.profile().getTitle()))).distinct().count();
	}

	/**
	 * @param profiles all profiles; those in a series already are not suggested again, but their series takes more parts
	 * @return the series that could be made: the sure ones by name, then those found by the first words alone; each with at least
	 *         two profiles (or one more for a series there is)
	 */
	public static List<Suggestion> suggest(List<Profile> profiles) {
		Map<String, String> existing = new LinkedHashMap<>(); // key -> the name of a series there is
		for (Profile p: profiles)
			if (inSeries(p))
				existing.putIfAbsent(key(p.getSeries()), p.getSeries());

		// 1. what every title says by itself
		Map<Profile, Parsed> parsed = new LinkedHashMap<>();
		java.util.Set<String> simple = new java.util.HashSet<>(existing.keySet()); // series named without a subtitle: "Ultima"
		for (Profile p: profiles) {
			if (inSeries(p))
				continue;
			Parsed ps = parse(p.getTitle());
			if (key(ps.base()).isEmpty())
				continue;
			parsed.put(p, ps);
			if ((ps.part() > 0) && (nextSeparator(ps.base(), 0) == null))
				simple.add(key(ps.base()));
		}
		// "Pagan: Ultima VIII" belongs to "Ultima" if there is such a series, not to "Pagan: Ultima"
		for (Map.Entry<Profile, Parsed> e: parsed.entrySet()) {
			Parsed ps = e.getValue();
			if ((ps.part() > 0) && (nextSeparator(ps.base(), 0) != null)) {
				String tail = lastSegment(ps.base());
				if (!key(tail).isEmpty() && simple.contains(key(tail)))
					e.setValue(new Parsed(tail, ps.part(), false));
			}
		}

		// 2. series with numbers, and those there are
		Map<String, List<Member>> groups = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		java.util.Set<Profile> plain = new java.util.HashSet<>();
		for (Map.Entry<Profile, Parsed> e: parsed.entrySet()) {
			Parsed ps = e.getValue();
			String k = key(ps.base());
			if ((ps.part() == 0) && !existing.containsKey(k))
				continue;
			groups.computeIfAbsent(k, x -> new ArrayList<>()).add(new Member(e.getKey(), ps.part()));
			names.putIfAbsent(k, ps.base()); // the name as the first title writes it
		}
		for (String k: existing.keySet())
			groups.computeIfAbsent(k, x -> new ArrayList<>());
		// titles without a number that start with the name of such a series: "Wing Commander: Privateer", "Wing Commander
		// Armada", "Star Wars: Rebel Assault" next to "Star Wars: Rebel Assault II"; the longest name wins
		List<Profile> left = new ArrayList<>();
		for (Map.Entry<Profile, Parsed> e: parsed.entrySet()) {
			Profile p = e.getKey();
			if ((e.getValue().part() > 0) || existing.containsKey(key(e.getValue().base())))
				continue;
			String titleKey = key(clean(p.getTitle())), best = null;
			for (String k: groups.keySet())
				if ((titleKey.equals(k) || titleKey.startsWith(k + " ")) && ((best == null) || (k.length() > best.length())))
					best = k;
			if (best == null) {
				left.add(p);
			} else {
				groups.get(best).add(new Member(p, 0));
				if (titleKey.equals(best))
					plain.add(p);
			}
		}
		for (Map.Entry<Profile, Parsed> e: parsed.entrySet())
			if (e.getValue().plain() && existing.containsKey(key(e.getValue().base())))
				plain.add(e.getKey());

		List<Suggestion> result = new ArrayList<>();
		for (Map.Entry<String, List<Member>> e: groups.entrySet()) {
			List<Member> members = e.getValue();
			boolean numbered = members.stream().anyMatch(m -> m.part() > 0);
			String known = existing.get(e.getKey());
			if (members.isEmpty() || ((known == null) && (members.size() < 2))) {
				members.stream().filter(m -> m.part() == 0).forEach(m -> left.add(m.profile()));
				continue;
			}
			// "Doom" next to "Doom II" is the first part: the one title that is the name alone, if there is no part 1 yet
			// (not "Wing Commander: Privateer" next to "Wing Commander 2")
			boolean hasFirst = members.stream().anyMatch(m -> m.part() == 1);
			long plainOnes = members.stream().filter(m -> (m.part() == 0) && plain.contains(m.profile())).count();
			boolean giveFirst = numbered && !hasFirst && (known == null) && (plainOnes == 1);
			// "Ishar: Legend of the Fortress" next to "Ishar 2" and "Ishar 3": the only title without a number, the parts start at 2
			long unnumbered = members.stream().filter(m -> m.part() == 0).count();
			boolean onlyOne = numbered && (known == null) && (plainOnes == 0) && (unnumbered == 1) && (members.stream().filter(m -> m.part() > 0).mapToInt(Member::part).min().orElse(0) == 2);
			List<Member> parts = new ArrayList<>();
			for (Member m: members)
				parts.add((m.part() == 0) && (onlyOne || (giveFirst && plain.contains(m.profile()))) ? new Member(m.profile(), 1): m);
			result.add(new Suggestion(known != null ? known: names.get(e.getKey()), sorted(parts), true));
		}

		// 3. no numbers, but the same name before a subtitle: "X-COM: UFO Defense", "X-COM: Terror from the Deep"
		// (the same title twice is a duplicate, and "Red Baron" with "Red Baron: Mission Builder" a game and its extra: two
		// different subtitles are needed)
		Map<String, List<Member>> heads = new LinkedHashMap<>();
		Map<String, String> headNames = new LinkedHashMap<>();
		for (Profile p: left) {
			Parsed ps = parsed.get(p);
			heads.computeIfAbsent(key(ps.base()), x -> new ArrayList<>()).add(new Member(p, 0));
			if (!ps.plain())
				headNames.putIfAbsent(key(ps.base()), ps.base());
		}
		List<Profile> rest = new ArrayList<>();
		for (Map.Entry<String, List<Member>> e: heads.entrySet()) {
			List<Member> withSubtitle = new ArrayList<>(e.getValue());
			withSubtitle.removeIf(m -> parsed.get(m.profile()).plain());
			if (headNames.containsKey(e.getKey()) && (distinctTitles(withSubtitle) >= 2))
				result.add(new Suggestion(headNames.get(e.getKey()), sorted(byYear(e.getValue())), true));
			else
				e.getValue().forEach(m -> rest.add(m.profile()));
		}

		// 4. not sure: the same two or more words at the start: "Pro Pinball The Web", "Pro Pinball Timeshock". The shortest
		// such start takes all its titles
		Map<Profile, String[]> words = new LinkedHashMap<>();
		Map<String, List<Profile>> starts = new LinkedHashMap<>();
		for (Profile p: rest) {
			String[] w = java.util.Arrays.stream(clean(p.getTitle()).replaceFirst("(?i)^(the|der|die|das)\\s+", "").split("\\s+")).filter(x -> !key(x).isEmpty()).toArray(String[]::new);
			words.put(p, w);
			for (int n = 2; n <= w.length; n++) {
				if (STOP.contains(key(w[n - 1])))
					continue;
				starts.computeIfAbsent(startKey(w, n), x -> new ArrayList<>()).add(p);
			}
		}
		List<String> startKeys = new ArrayList<>(starts.keySet());
		startKeys.sort((a, b) -> a.split("\u0000").length != b.split("\u0000").length ? Integer.compare(a.split("\u0000").length, b.split("\u0000").length): a.compareTo(b));
		java.util.Set<Profile> taken = new java.util.HashSet<>();
		for (String k: startKeys) {
			List<Member> members = new ArrayList<>();
			for (Profile p: starts.get(k))
				if (!taken.contains(p))
					members.add(new Member(p, 0));
			if ((members.size() < 2) || (distinctTitles(members) < 2))
				continue;
			members.forEach(m -> taken.add(m.profile()));
			Profile first = members.get(0).profile();
			int n = k.split("\u0000").length;
			String name = String.join(" ", java.util.Arrays.copyOf(words.get(first), n)).replaceAll("[\\s,:–—-]+$", "");
			result.add(new Suggestion(name, sorted(members), false));
		}

		result.sort((a, b) -> a.sure() != b.sure() ? (a.sure() ? -1: 1): String.CASE_INSENSITIVE_ORDER.compare(a.series(), b.series()));
		return result;
	}

	/**
	 * A series without numbers in its titles ("Ravenloft: Strahd's Possession" 1994, "Ravenloft: Stone Prophet" 1995): the parts
	 * in the order of their years, if every profile has another year and they share a genre ("Star Wars: Dark Forces" and "Star
	 * Wars: TIE Fighter" do not: games of one world, not parts of one series).
	 */
	private static List<Member> byYear(List<Member> members) {
		java.util.TreeMap<Integer, Member> years = new java.util.TreeMap<>();
		java.util.Set<String> shared = null;
		for (Member m: members) {
			String year = m.profile().getYear() == null ? "": m.profile().getYear().strip();
			if (!year.matches("(19|20)[0-9]{2}") || (years.put(Integer.parseInt(year), m) != null))
				return members;
			java.util.Set<String> genres = new java.util.HashSet<>();
			for (String g: String.valueOf(m.profile().getGenre() == null ? "": m.profile().getGenre()).split("[,;/]"))
				if (!key(g).isEmpty())
					genres.add(key(g));
			if (shared == null)
				shared = genres;
			else
				shared.retainAll(genres);
			if (shared.isEmpty())
				return members;
		}
		List<Member> result = new ArrayList<>();
		for (Member m: years.values())
			result.add(new Member(m.profile(), result.size() + 1));
		return result.size() > MAX_PART ? members: result;
	}

	/** the first n words as a key (words kept apart by a character no title has) */
	private static String startKey(String[] w, int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++)
			sb.append(i == 0 ? "": "\u0000").append(key(w[i]));
		return sb.toString();
	}

	private static List<Member> sorted(List<Member> parts) {
		parts.sort((a, b) -> a.part() != b.part() ? Integer.compare(a.part() == 0 ? Integer.MAX_VALUE: a.part(), b.part() == 0 ? Integer.MAX_VALUE: b.part())
				: String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(a.profile().getTitle()), String.valueOf(b.profile().getTitle())));
		return parts;
	}
}
