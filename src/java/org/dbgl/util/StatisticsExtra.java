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

import java.text.DateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.ProfileStats;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.repository.ProfileRepository.PlaySession;
import org.dbgl.service.ITextService;
import org.dbgl.util.ProfileReports.Out;


/**
 * Alpha 53: more of the statistics: the play time of the last weeks, by weekday and time of day, the days played in a row
 * and as a calendar, the length of the sessions, the games never started by the age of their profile, series and
 * publishers, the state of the collection (what is missing), the largest games; and the review of a year as a web page.
 */
public final class StatisticsExtra {

	private static final int WEEKS = 12;

	private StatisticsExtra() {
	}

	static LocalDate day(java.util.Date d) {
		return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

	static LocalDateTime time(java.util.Date d) {
		return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
	}

	/** the seconds played per day */
	static Map<LocalDate, Long> perDay(List<PlaySession> sessions) {
		Map<LocalDate, Long> result = new TreeMap<>();
		for (PlaySession s: sessions)
			result.merge(day(s.started()), (long)s.seconds(), Long::sum);
		return result;
	}

	/** the longest run of days played one after the other: its length, first and last day (null if none) */
	public record Streak(int days, LocalDate from, LocalDate to) {
	}

	public static Streak longestStreak(java.util.Collection<LocalDate> days) {
		List<LocalDate> sorted = days.stream().distinct().sorted().toList();
		Streak best = null;
		LocalDate start = null, prev = null;
		for (LocalDate d: sorted) {
			if ((prev == null) || !d.equals(prev.plusDays(1)))
				start = d;
			int len = (int)ChronoUnit.DAYS.between(start, d) + 1;
			if ((best == null) || (len > best.days()))
				best = new Streak(len, start, d);
			prev = d;
		}
		return best;
	}

	/** the days played one after the other up to today (or up to yesterday, if not played today yet) */
	public static int currentStreak(java.util.Set<LocalDate> days, LocalDate today) {
		LocalDate d = days.contains(today) ? today: today.minusDays(1);
		int n = 0;
		while (days.contains(d)) {
			n++;
			d = d.minusDays(1);
		}
		return n;
	}

	/** the bucket of a session's length: 0 under 15 minutes, 1 to 30, 2 to 60, 3 to 2 hours, 4 to 4 hours, 5 longer */
	public static int lengthBucket(int seconds) {
		int[] limits = {15 * 60, 30 * 60, 60 * 60, 2 * 3600, 4 * 3600};
		for (int i = 0; i < limits.length; i++)
			if (seconds < limits[i])
				return i;
		return limits.length;
	}

	/** the bucket of a profile's age: 0 under a month, 1 to half a year, 2 to a year, 3 to two years, 4 older */
	public static int ageBucket(LocalDate created, LocalDate today) {
		long months = ChronoUnit.MONTHS.between(created, today);
		return months < 1 ? 0: months < 6 ? 1: months < 12 ? 2: months < 24 ? 3: 4;
	}

	static void sections(Out out, List<Profile> profiles, List<PlaySession> own, ITextService text, Locale locale, java.util.Date now) {
		LocalDate today = day(now);
		Map<Integer, Profile> byId = new HashMap<>();
		profiles.forEach(p -> byId.put(p.getId(), p));

		// the last 12 weeks, the week begins as in the language of DBGL
		WeekFields wf = WeekFields.of(locale);
		LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(wf.getFirstDayOfWeek()));
		long[][] weeks = new long[WEEKS][2];
		for (PlaySession s: own) {
			long ago = ChronoUnit.WEEKS.between(day(s.started()).with(TemporalAdjusters.previousOrSame(wf.getFirstDayOfWeek())), thisWeek);
			if ((ago >= 0) && (ago < WEEKS)) {
				weeks[(int)(WEEKS - 1 - ago)][0]++;
				weeks[(int)(WEEKS - 1 - ago)][1] += s.seconds();
			}
		}
		DateFormat shortDate = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
		out.section(text.get("dialog.statistics.trend"));
		for (int i = 0; i < WEEKS; i++) {
			LocalDate start = thisWeek.minusWeeks(WEEKS - 1 - i);
			out.row(ProfileReports.W7_9, new String[] {String.valueOf(weeks[i][0]), weeks[i][0] == 0 ? "-": ProfileReports.time(weeks[i][1])},
				text.get("dialog.statistics.weekof", new Object[] {shortDate.format(java.util.Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant()))}), weeks[i][1]);
		}

		// by weekday (in the order of the week there) and by the hour the session began (in steps of 3 hours)
		long[][] weekdays = new long[7][2], hours = new long[8][2];
		for (PlaySession s: own) {
			LocalDateTime t = time(s.started());
			int wd = (t.getDayOfWeek().getValue() - wf.getFirstDayOfWeek().getValue() + 7) % 7;
			weekdays[wd][0]++;
			weekdays[wd][1] += s.seconds();
			hours[t.getHour() / 3][0]++;
			hours[t.getHour() / 3][1] += s.seconds();
		}
		out.section(text.get("dialog.statistics.byweekday"));
		for (int i = 0; i < 7; i++) {
			DayOfWeek d = wf.getFirstDayOfWeek().plus(i);
			out.row(ProfileReports.W7_9, new String[] {String.valueOf(weekdays[i][0]), weekdays[i][0] == 0 ? "-": ProfileReports.time(weekdays[i][1])},
				d.getDisplayName(TextStyle.FULL, locale), weekdays[i][1]);
		}
		out.section(text.get("dialog.statistics.bytimeofday"));
		for (int i = 0; i < 8; i++)
			out.row(ProfileReports.W7_9, new String[] {String.valueOf(hours[i][0]), hours[i][0] == 0 ? "-": ProfileReports.time(hours[i][1])},
				text.get("dialog.statistics.hours", new Object[] {String.format("%02d", i * 3), String.format("%02d", i * 3 + 3)}), hours[i][1]);

		// the days played: how many, the longest run, the current one, and a calendar of the last 12 months (the web page)
		Map<LocalDate, Long> days = perDay(own);
		out.section(text.get("dialog.statistics.playdays"));
		out.line(text.get("dialog.statistics.playdayscount", new Object[] {days.size()}));
		Streak best = longestStreak(days.keySet());
		if (best != null)
			out.line(text.get("dialog.statistics.longeststreak", new Object[] {best.days(), format(shortDate, best.from()), format(shortDate, best.to())}));
		out.line(text.get("dialog.statistics.currentstreak", new Object[] {currentStreak(days.keySet(), today)}));
		out.heatmap(today.minusWeeks(52).with(TemporalAdjusters.previousOrSame(wf.getFirstDayOfWeek())), today, days, locale);

		// how long the sessions are
		long[][] lengths = new long[6][2];
		for (PlaySession s: own) {
			int b = lengthBucket(s.seconds());
			lengths[b][0]++;
			lengths[b][1] += s.seconds();
		}
		String[] lengthNames = {text.get("dialog.statistics.range.below", new Object[] {15}), text.get("dialog.statistics.range.min", new Object[] {15, 30}),
				text.get("dialog.statistics.range.min", new Object[] {30, 60}), text.get("dialog.statistics.range.hours", new Object[] {1, 2}),
				text.get("dialog.statistics.range.hours", new Object[] {2, 4}), text.get("dialog.statistics.range.above", new Object[] {4})};
		out.section(text.get("dialog.statistics.sessionlengths"));
		for (int i = 0; i < 6; i++)
			out.row(ProfileReports.W7_9, new String[] {String.valueOf(lengths[i][0]), lengths[i][0] == 0 ? "-": ProfileReports.time(lengths[i][1])}, lengthNames[i], lengths[i][0]);

		// the backlog: never started, by the age of the profile, and the oldest of them
		List<Profile> never = profiles.stream().filter(p -> ProfileReports.runs(p) == 0).toList();
		long[] ages = new long[6];
		for (Profile p: never) {
			java.util.Date created = p.getStats() == null ? null: p.getStats().getCreated();
			ages[created == null ? 5: ageBucket(day(created), today)]++;
		}
		String[] ageNames = {text.get("dialog.statistics.age.month"), text.get("dialog.statistics.age.halfyear"), text.get("dialog.statistics.age.year"),
				text.get("dialog.statistics.age.twoyears"), text.get("dialog.statistics.age.older"), text.get("dialog.statistics.none")};
		out.section(text.get("dialog.statistics.backlog", new Object[] {never.size()}));
		for (int i = 0; i < 6; i++)
			if ((i < 5) || (ages[i] > 0))
				out.row(ProfileReports.W7, new String[] {String.valueOf(ages[i])}, ageNames[i], ages[i]);
		List<Profile> oldest = never.stream().filter(p -> (p.getStats() != null) && (p.getStats().getCreated() != null))
				.sorted(Comparator.comparing((Profile p) -> p.getStats().getCreated())).limit(ProfileReports.TOP).toList();
		DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
		out.section(text.get("dialog.statistics.oldestunplayed"));
		oldest.forEach(p -> out.row(ProfileReports.W22, new String[] {df.format(p.getStats().getCreated())}, p.getTitle(), -1));
		if (oldest.isEmpty())
			out.empty();

		// series and publishers
		String none = text.get("dialog.statistics.none");
		if (profiles.stream().anyMatch(p -> StringUtils.isNotBlank(p.getSeriesName())))
			ProfileReports.groups(out, text.get("dialog.statistics.byseries"), profiles.stream().filter(p -> StringUtils.isNotBlank(p.getSeriesName())).toList(),
				p -> StringUtils.strip(p.getSeriesName()), none, true, false, ProfileReports.TOP);
		ProfileReports.groups(out, text.get("dialog.statistics.bypublisher"), profiles, p -> StringUtils.defaultIfBlank(StringUtils.strip(p.getPublisher()), none), none, true, false,
			ProfileReports.TOP);

		// the state of the collection: what is missing
		out.section(text.get("dialog.statistics.health"));
		int n = Math.max(1, profiles.size());
		long noPictures = profiles.stream().filter(p -> new org.dbgl.model.ThumbInfo(p.getCapturesString()).getAllThumbs().length == 0).count();
		long[] counts = {noPictures, profiles.stream().filter(p -> StringUtils.isBlank(p.getYear())).count(), profiles.stream().filter(p -> StringUtils.isBlank(p.getGenre())).count(),
				profiles.stream().filter(p -> StringUtils.isBlank(p.getDeveloper())).count(), profiles.stream().filter(p -> StringUtils.isBlank(p.getPublisher())).count(),
				profiles.stream().filter(p -> {
					List<String> w = ProfileWarnings.lastKnown(p);
					return (w != null) && !w.isEmpty();
				}).count()};
		String[] healthNames = {"nopictures", "noyear", "nogenre", "nodeveloper", "nopublisher", "warnings"};
		for (int i = 0; i < counts.length; i++)
			out.row(ProfileReports.W7_9, new String[] {String.valueOf(counts[i]), String.format(locale, "%.0f %%", 100.0 * counts[i] / n)},
				text.get("dialog.statistics.health." + healthNames[i]), counts[i]);
		out.line(text.get("dialog.statistics.health.tip"));

		// the largest games (as far as their size is known)
		List<Profile> sized = profiles.stream().filter(p -> DiskUsage.known(p) != null).sorted(Comparator.comparingLong((Profile p) -> DiskUsage.known(p)).reversed()).limit(10)
				.toList();
		out.section(text.get("dialog.statistics.largest"));
		sized.forEach(p -> out.row(new int[] {11}, new String[] {DiskUsage.format(DiskUsage.known(p), locale)}, p.getTitle(), DiskUsage.known(p)));
		if (sized.isEmpty())
			out.empty();
	}

	private static String format(DateFormat df, LocalDate d) {
		return df.format(java.util.Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant()));
	}

	/** a calendar of the days played, one column a week, darker the longer; for the web page */
	static String heatmapHtml(LocalDate from, LocalDate to, Map<LocalDate, Long> seconds, Locale locale) {
		long max = seconds.entrySet().stream().filter(e -> !e.getKey().isBefore(from) && !e.getKey().isAfter(to)).mapToLong(Map.Entry::getValue).max().orElse(0);
		StringBuilder sb = new StringBuilder("<div style=\"overflow-x:auto;margin:8px 0\"><table style=\"border-collapse:separate;border-spacing:2px;width:auto\">");
		WeekFields wf = WeekFields.of(locale);
		long weeks = ChronoUnit.WEEKS.between(from, to) + 1;
		for (int row = 0; row < 7; row++) {
			sb.append("<tr><td class=\"n\" style=\"font-size:.75em;padding-right:4px\">").append(ProfileReports.html(wf.getFirstDayOfWeek().plus(row).getDisplayName(TextStyle.SHORT, locale)))
					.append("</td>");
			for (int w = 0; w < weeks; w++) {
				LocalDate d = from.plusWeeks(w).plusDays(row);
				if (d.isAfter(to)) {
					sb.append("<td></td>");
					continue;
				}
				long s = seconds.getOrDefault(d, 0L);
				String color = s == 0 ? "#e4e8ee": shade(max == 0 ? 0: (double)s / max);
				sb.append("<td title=\"").append(d).append(s == 0 ? "": " · " + ProfileReports.time(s)).append("\" style=\"width:11px;height:11px;padding:0;border-radius:2px;background:")
						.append(color).append("\"></td>");
			}
			sb.append("</tr>");
		}
		sb.append("</table></div>\n");
		return sb.toString();
	}

	private static String shade(double share) {
		String[] steps = {"#c6dbef", "#9ecae1", "#6baed6", "#3182bd", "#08519c"};
		return steps[Math.min(steps.length - 1, (int)(share * steps.length))];
	}

	/** what the review of a year needs from the sessions of that year */
	record Year(long seconds, int sessions, Map<Integer, Long> perProfile, PlaySession longest, PlaySession first, PlaySession last, long[] months, Map<LocalDate, Long> days) {
	}

	static Year year(List<PlaySession> sessions, int year) {
		long total = 0;
		Map<Integer, Long> perProfile = new HashMap<>();
		PlaySession longest = null, first = null, last = null;
		long[] months = new long[12];
		List<PlaySession> inYear = new ArrayList<>();
		for (PlaySession s: sessions) {
			LocalDate d = day(s.started());
			if (d.getYear() != year)
				continue;
			inYear.add(s);
			total += s.seconds();
			perProfile.merge(s.profileId(), (long)s.seconds(), Long::sum);
			months[d.getMonthValue() - 1] += s.seconds();
			if ((longest == null) || (s.seconds() > longest.seconds()))
				longest = s;
			if ((first == null) || s.started().before(first.started()))
				first = s;
			if ((last == null) || s.started().after(last.started()))
				last = s;
		}
		return new Year(total, inYear.size(), perProfile, longest, first, last, months, perDay(inYear));
	}

	/** alpha 53: the review of a year as a web page: time, games, new profiles, longest session, first and last game, calendar */
	public static String yearReviewHtml(List<Profile> profiles, List<PlaySession> sessions, ITextService text, Locale locale, int year) {
		Map<Integer, Profile> byId = new HashMap<>();
		profiles.forEach(p -> byId.put(p.getId(), p));
		List<PlaySession> own = sessions.stream().filter(s -> byId.containsKey(s.profileId())).toList();
		Year y = year(own, year);
		ProfileReports.HtmlOut out = new ProfileReports.HtmlOut(text.get("dialog.yearreview.title", new Object[] {String.valueOf(year)}), locale.getLanguage());
		DateFormat df = DateFormat.getDateInstance(DateFormat.LONG, locale);
		if (y.sessions() == 0) {
			out.line(text.get("dialog.yearreview.none", new Object[] {String.valueOf(year)}));
			return out.finish("DBGL " + org.dbgl.constants.Constants.EDITION_NAME, org.dbgl.constants.Constants.EDITION_URL);
		}
		out.line(text.get("dialog.yearreview.time", new Object[] {ProfileReports.time(y.seconds()), y.sessions()}));
		// played for the first time this year: no session of the game before it
		long firstTime = y.perProfile().keySet().stream().filter(id -> own.stream().noneMatch(s -> (s.profileId() == id) && (day(s.started()).getYear() < year))).count();
		out.line(text.get("dialog.yearreview.games", new Object[] {y.perProfile().size(), firstTime}));
		out.line(text.get("dialog.yearreview.added", new Object[] {profiles.stream().filter(p -> (p.getStats() != null) && (p.getStats().getCreated() != null)
				&& (day(p.getStats().getCreated()).getYear() == year)).count()}));
		Streak streak = longestStreak(y.days().keySet());
		out.line(text.get("dialog.yearreview.days", new Object[] {y.days().size(), streak == null ? 0: streak.days()}));
		out.line(text.get("dialog.yearreview.longest", new Object[] {ProfileReports.time(y.longest().seconds()), byId.get(y.longest().profileId()).getTitle(), df.format(y.longest().started())}));
		out.line(text.get("dialog.yearreview.first", new Object[] {byId.get(y.first().profileId()).getTitle(), df.format(y.first().started())}));
		out.line(text.get("dialog.yearreview.last", new Object[] {byId.get(y.last().profileId()).getTitle(), df.format(y.last().started())}));
		int bestMonth = 0;
		for (int m = 1; m < 12; m++)
			if (y.months()[m] > y.months()[bestMonth])
				bestMonth = m;
		out.line(text.get("dialog.yearreview.month", new Object[] {java.text.DateFormatSymbols.getInstance(locale).getMonths()[bestMonth], ProfileReports.time(y.months()[bestMonth])}));
		Map<String, Long> genres = new HashMap<>();
		y.perProfile().forEach((id, s) -> {
			String g = StringUtils.strip(byId.get(id).getGenre());
			if (StringUtils.isNotBlank(g))
				genres.merge(g, s, Long::sum);
		});
		genres.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e -> out.line(text.get("dialog.yearreview.genre", new Object[] {e.getKey()})));

		out.section(text.get("dialog.yearreview.top", new Object[] {String.valueOf(year)}));
		y.perProfile().entrySet().stream().sorted(Map.Entry.<Integer, Long>comparingByValue().reversed()).limit(10)
				.forEach(e -> out.row(ProfileReports.W9, new String[] {ProfileStats.formatPlayTime((int)Math.min(Integer.MAX_VALUE, e.getValue()))}, byId.get(e.getKey()).getTitle(), e.getValue()));
		String[] monthNames = java.text.DateFormatSymbols.getInstance(locale).getMonths();
		out.section(text.get("dialog.yearreview.months"));
		for (int m = 0; m < 12; m++)
			out.row(ProfileReports.W9, new String[] {y.months()[m] == 0 ? "-": ProfileReports.time(y.months()[m])}, monthNames[m], y.months()[m]);
		out.section(text.get("dialog.yearreview.calendar"));
		WeekFields wf = WeekFields.of(locale);
		out.heatmap(LocalDate.of(year, 1, 1).with(TemporalAdjusters.previousOrSame(wf.getFirstDayOfWeek())), LocalDate.of(year, 12, 31), y.days(), locale);
		return out.finish("DBGL " + org.dbgl.constants.Constants.EDITION_NAME, org.dbgl.constants.Constants.EDITION_URL);
	}

	/** alpha 53: a game never started, by chance; null if all were started */
	public static Profile randomUnplayed(List<Profile> profiles, java.util.Random random) {
		List<Profile> never = profiles.stream().filter(p -> ProfileReports.runs(p) == 0).toList();
		return never.isEmpty() ? null: never.get(random.nextInt(never.size()));
	}
}
