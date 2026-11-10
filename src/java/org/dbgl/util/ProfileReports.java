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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.ProfileStats;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Configuration;
import org.dbgl.service.ITextService;


/** texts for comparing two profiles, the statistics of all profiles, and taking over a dosbox.conf into a profile */
public final class ProfileReports {

	private static final String NL = System.lineSeparator();
	/** set by DBGL for each profile, not worth comparing or taking over */
	private static final Set<String> MANAGED = Set.of("dosbox/captures", "capture/capture_dir");

	private ProfileReports() {
	}

	private static String pad(String s, int width) {
		return s.length() >= width ? s: s + " ".repeat(width - s.length());
	}

	/**
	 * Every setting whose value differs between the two profiles, as they take effect (own value, or else the default of their
	 * DOSBox version), and the [autoexec] lines that differ.
	 */
	public static String compare(Profile a, Profile b, ITextService text) {
		Configuration ca = a.getCombinedConfiguration();
		Configuration cb = b.getCombinedConfiguration();
		Set<String> sections = new LinkedHashSet<>(List.of(ca.getSectionNames()));
		sections.addAll(List.of(cb.getSectionNames()));
		List<String[]> rows = new ArrayList<>();
		for (String section: sections) {
			if (section.equalsIgnoreCase("autoexec"))
				continue;
			Set<String> items = new LinkedHashSet<>();
			if (ca.getSectionNames().length > 0 && List.of(ca.getSectionNames()).contains(section))
				items.addAll(List.of(ca.getItemNames(section)));
			if (List.of(cb.getSectionNames()).contains(section))
				items.addAll(List.of(cb.getItemNames(section)));
			for (String item: items) {
				if (MANAGED.contains(section.toLowerCase() + '/' + item.toLowerCase()))
					continue;
				String va = ca.hasValue(section, item) ? ca.getValue(section, item): "-";
				String vb = cb.hasValue(section, item) ? cb.getValue(section, item): "-";
				if (!Objects.equals(StringUtils.strip(va), StringUtils.strip(vb)))
					rows.add(new String[] {"[" + section + "] " + item, va, vb});
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append("A: ").append(a.getTitle()).append(" (").append(a.getDosboxVersion().getTitle()).append(')').append(NL);
		sb.append("B: ").append(b.getTitle()).append(" (").append(b.getDosboxVersion().getTitle()).append(')').append(NL).append(NL);
		if (rows.isEmpty()) {
			sb.append(text.get("dialog.compareprofiles.same")).append(NL);
		} else {
			int w0 = Math.min(48, rows.stream().mapToInt(r -> r[0].length()).max().orElse(10) + 2);
			int w1 = Math.min(30, Math.max(3, rows.stream().mapToInt(r -> r[1].length()).max().orElse(1) + 2));
			sb.append(pad("", w0)).append(pad("A", w1)).append("B").append(NL);
			rows.forEach(r -> sb.append(pad(r[0], w0)).append(pad(r[1], w1)).append(r[2]).append(NL));
		}
		List<String> autoA = autoexecLines(a), autoB = autoexecLines(b);
		if (!autoA.equals(autoB)) {
			sb.append(NL).append("[autoexec]").append(NL);
			autoA.stream().filter(l -> !autoB.contains(l)).forEach(l -> sb.append("A: ").append(l).append(NL));
			autoB.stream().filter(l -> !autoA.contains(l)).forEach(l -> sb.append("B: ").append(l).append(NL));
		}
		return sb.toString();
	}

	private static List<String> autoexecLines(Profile p) {
		String s = p.getConfiguration().getAutoexec().toString();
		List<String> result = new ArrayList<>();
		for (String l: s.split("\\R"))
			if (StringUtils.isNotBlank(l))
				result.add(l.strip());
		return result;
	}

	/** play time, starts and the games played most, last and never */
	/**
	 * What changes between two DOSBox versions: settings the newer one no longer has, settings that are new, and settings with
	 * another default value. Both configurations must be loaded.
	 */
	public static String compareVersions(Configuration from, String fromTitle, Configuration to, String toTitle, ITextService text) {
		List<String> gone = new ArrayList<>(), added = new ArrayList<>(), changed = new ArrayList<>();
		for (String s: from.getSectionNames()) {
			if (s.equalsIgnoreCase("autoexec"))
				continue;
			for (String i: from.getItemNames(s)) {
				if (!to.hasValue(s, i))
					gone.add("[" + s + "] " + i + " = " + from.getValue(s, i));
				else if (!Objects.equals(StringUtils.strip(from.getValue(s, i)), StringUtils.strip(to.getValue(s, i))))
					changed.add("[" + s + "] " + i + ": " + from.getValue(s, i) + "  ->  " + to.getValue(s, i));
			}
		}
		for (String s: to.getSectionNames()) {
			if (s.equalsIgnoreCase("autoexec"))
				continue;
			for (String i: to.getItemNames(s))
				if (!from.hasValue(s, i))
					added.add("[" + s + "] " + i + " = " + to.getValue(s, i));
		}
		String nl = System.lineSeparator();
		StringBuilder sb = new StringBuilder(text.get("dialog.switchdosbox.compare.title", new Object[] {fromTitle, toTitle})).append(nl).append(nl);
		Object[][] parts = {{"dialog.switchdosbox.compare.gone", gone}, {"dialog.switchdosbox.compare.added", added}, {"dialog.switchdosbox.compare.changed", changed}};
		for (Object[] part: parts) {
			@SuppressWarnings("unchecked")
			List<String> lines = (List<String>)part[1];
			Object[] args = part[0].equals("dialog.switchdosbox.compare.added") ? new Object[] {toTitle, lines.size()}: new Object[] {lines.size()};
			sb.append(text.get((String)part[0], args)).append(nl);
			lines.forEach(l -> sb.append("   ").append(l).append(nl));
			sb.append(nl);
		}
		sb.append(text.get("dialog.switchdosbox.compare.note"));
		return sb.toString();
	}

	/** how many entries the top lists have */
	private static final int TOP = 15;

	/** where the statistics go: a text, a web page with bars, or a table (CSV) */
	private interface Out {
		void line(String line);

		void section(String title);

		/** a row: values in columns of the given widths (for the text), the name, and the length of its bar (negative: none) */
		void row(int[] widths, String[] cells, String label, long bar);

		void more();

		void empty();
	}

	private static final class TextOut implements Out {
		final StringBuilder sb_ = new StringBuilder();

		public void line(String line) {
			sb_.append(line).append(NL);
		}

		public void section(String title) {
			sb_.append(NL).append(title).append(NL).append("-".repeat(Math.max(3, title.length()))).append(NL);
		}

		public void row(int[] widths, String[] cells, String label, long bar) {
			for (int i = 0; i < cells.length; i++)
				sb_.append(pad(cells[i], widths[i]));
			sb_.append(label).append(NL);
		}

		public void more() {
			sb_.append("...").append(NL);
		}

		public void empty() {
			sb_.append("-").append(NL);
		}

		@Override
		public String toString() {
			return sb_.toString();
		}
	}

	private static String html(String s) {
		return StringUtils.defaultString(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	/** a web page that opens in any browser, without anything from the internet; bars as wide as their share of the largest */
	private static final class HtmlOut implements Out {
		final StringBuilder sb_ = new StringBuilder();
		final List<String[]> rows_ = new ArrayList<>();
		final List<Long> bars_ = new ArrayList<>();
		boolean inSection_ = false;
		boolean overview_ = false;

		HtmlOut(String title, String lang) {
			sb_.append("<!DOCTYPE html>\n<html lang=\"").append(html(lang)).append("\"><head><meta charset=\"utf-8\"><title>").append(html(title)).append("</title>\n");
			sb_.append("<style>body{font-family:system-ui,Segoe UI,Arial,sans-serif;margin:24px auto;max-width:960px;padding:0 16px;color:#222;background:#fff}")
					.append("h1{font-size:1.5em}h2{font-size:1.1em;margin-top:2em;border-bottom:1px solid #ccc;padding-bottom:4px}")
					.append("table{border-collapse:collapse;width:100%}td{padding:2px 8px 2px 0;vertical-align:middle;white-space:nowrap}td.n{text-align:right;color:#555;width:1%}")
					.append("td.l{white-space:normal}td.b{width:35%}div.bar{height:12px;background:#4a7fb5;border-radius:2px;min-width:1px}")
					.append("ul.o{list-style:none;padding:0}ul.o li{padding:2px 0}footer{margin-top:3em;color:#888;font-size:.85em}")
					.append("@media(prefers-color-scheme:dark){body{background:#1e1e1e;color:#ddd}td.n{color:#aaa}h2{border-color:#444}div.bar{background:#6f9fd8}}</style></head><body>\n");
			sb_.append("<h1>").append(html(title)).append("</h1>\n");
		}

		public void line(String line) {
			if (!overview_) {
				sb_.append("<ul class=\"o\">\n");
				overview_ = true;
			}
			sb_.append("<li>").append(html(line)).append("</li>\n");
		}

		private void flush() {
			if (overview_) {
				sb_.append("</ul>\n");
				overview_ = false;
			}
			if (!inSection_)
				return;
			long max = bars_.stream().mapToLong(Long::longValue).max().orElse(0);
			boolean withBars = max > 0;
			sb_.append("<table>\n");
			int cols = rows_.stream().mapToInt(r -> r.length).max().orElse(1);
			for (int i = 0; i < rows_.size(); i++) {
				String[] r = rows_.get(i);
				sb_.append("<tr>");
				// a row with fewer values ("...", "-"): empty cells first, so that the name is in its column (the bars too)
				for (int c = r.length; c < cols; c++)
					sb_.append("<td class=\"n\"></td>");
				for (int c = 0; c < r.length - 1; c++)
					sb_.append("<td class=\"n\">").append(html(r[c])).append("</td>");
				sb_.append("<td class=\"l\">").append(html(r[r.length - 1])).append("</td>");
				if (withBars) {
					long b = bars_.get(i);
					sb_.append("<td class=\"b\">");
					if (b > 0)
						sb_.append("<div class=\"bar\" style=\"width:").append(String.format(java.util.Locale.ROOT, "%.1f", 100.0 * b / max)).append("%\"></div>");
					sb_.append("</td>");
				}
				sb_.append("</tr>\n");
			}
			sb_.append("</table>\n");
			rows_.clear();
			bars_.clear();
			inSection_ = false;
		}

		public void section(String title) {
			flush();
			sb_.append("<h2>").append(html(title)).append("</h2>\n");
			inSection_ = true;
		}

		public void row(int[] widths, String[] cells, String label, long bar) {
			String[] r = java.util.Arrays.copyOf(cells, cells.length + 1);
			r[cells.length] = label;
			rows_.add(r);
			bars_.add(bar);
		}

		public void more() {
			rows_.add(new String[] {"..."});
			bars_.add(-1L);
		}

		public void empty() {
			rows_.add(new String[] {"-"});
			bars_.add(-1L);
		}

		String finish(String footer) {
			flush();
			sb_.append("<footer>").append(html(footer)).append("</footer>\n</body></html>\n");
			return sb_.toString();
		}
	}

	/** a table for a spreadsheet: section, values, name; ';' where the comma is the decimal sign (as Excel expects there) */
	private static final class CsvOut implements Out {
		final StringBuilder sb_ = new StringBuilder("﻿"); // UTF-8 for Excel
		final char sep_;
		String section_ = "";

		CsvOut(java.util.Locale locale) {
			sep_ = java.text.DecimalFormatSymbols.getInstance(locale).getDecimalSeparator() == ',' ? ';': ',';
		}

		private String cell(String s) {
			s = StringUtils.defaultString(s);
			return (s.indexOf(sep_) >= 0 || s.contains("\"") || s.contains("\n") || s.contains("\r")) ? '"' + s.replace("\"", "\"\"") + '"': s;
		}

		private void write(String... cells) {
			for (int i = 0; i < cells.length; i++) {
				if (i > 0)
					sb_.append(sep_);
				sb_.append(cell(cells[i]));
			}
			sb_.append("\r\n");
		}

		public void line(String line) {
			write("", "", "", line);
		}

		public void section(String title) {
			section_ = title;
		}

		public void row(int[] widths, String[] cells, String label, long bar) {
			write(section_, cells.length > 0 ? cells[0]: "", cells.length > 1 ? cells[1]: "", label);
		}

		public void more() {
		}

		public void empty() {
		}

		@Override
		public String toString() {
			return sb_.toString();
		}
	}

	/**
	 * The statistics of all profiles as a text that can be saved as a report.
	 *
	 * @param sessions the play sessions (kept since 0.101 alpha 44), of these profiles and maybe of removed ones
	 * @param locale for the names of the months and the dates
	 * @param now the date of the report (the current calendar year comes from it)
	 */
	public static String statistics(List<Profile> profiles, List<org.dbgl.model.repository.ProfileRepository.PlaySession> sessions, ITextService text, java.util.Locale locale,
			java.util.Date now) {
		TextOut out = new TextOut();
		statistics(out, profiles, sessions, text, locale, now);
		return out.toString();
	}

	/** the same as a web page with bars (since alpha 45) */
	public static String statisticsHtml(List<Profile> profiles, List<org.dbgl.model.repository.ProfileRepository.PlaySession> sessions, ITextService text,
			java.util.Locale locale, java.util.Date now) {
		HtmlOut out = new HtmlOut(text.get("dialog.statistics.title"), locale.getLanguage());
		statistics(out, profiles, sessions, text, locale, now);
		return out.finish("DBGL " + org.dbgl.constants.Constants.EDITION_NAME);
	}

	/** the same as a table for a spreadsheet (since alpha 45) */
	public static String statisticsCsv(List<Profile> profiles, List<org.dbgl.model.repository.ProfileRepository.PlaySession> sessions, ITextService text,
			java.util.Locale locale, java.util.Date now) {
		CsvOut out = new CsvOut(locale);
		statistics(out, profiles, sessions, text, locale, now);
		return out.toString();
	}

	private static final int[] W9 = {9}, W22 = {22}, W9_22 = {9, 22}, W7_9 = {7, 9}, W7 = {7};

	private static void statistics(Out out, List<Profile> profiles, List<org.dbgl.model.repository.ProfileRepository.PlaySession> sessions, ITextService text,
			java.util.Locale locale, java.util.Date now) {
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale);
		out.line(text.get("dialog.statistics.reportdate", new Object[] {df.format(now)}));
		if (out instanceof TextOut)
			out.line("");

		long total = profiles.stream().mapToLong(ProfileReports::playTime).sum();
		long played = profiles.stream().filter(p -> runs(p) > 0).count();
		long starts = profiles.stream().mapToLong(ProfileReports::runs).sum();
		Map<Integer, Profile> byId = new java.util.HashMap<>();
		profiles.forEach(p -> byId.put(p.getId(), p));
		List<org.dbgl.model.repository.ProfileRepository.PlaySession> own = sessions.stream().filter(x -> byId.containsKey(x.profileId())).toList();
		out.line(text.get("dialog.statistics.profiles", new Object[] {profiles.size()}));
		out.line(text.get("dialog.statistics.played", new Object[] {played, profiles.size() - played}));
		out.line(text.get("dialog.statistics.starts", new Object[] {starts}));
		out.line(text.get("dialog.statistics.totaltime", new Object[] {time(total)}));
		long timed = profiles.stream().filter(p -> playTime(p) > 0).count();
		out.line(text.get("dialog.statistics.averagetime", new Object[] {time(timed == 0 ? 0: total / timed)}));
		out.line(text.get("dialog.statistics.favorites", new Object[] {profiles.stream().filter(p -> Boolean.TRUE.equals(p.isFavorite())).count()}));
		long monthAgo = now.getTime() - 30L * 24 * 60 * 60 * 1000;
		out.line(text.get("dialog.statistics.recentplayed", new Object[] {profiles.stream().filter(p -> (p.getStats() != null) && (p.getStats().getLastRun() != null)
				&& (p.getStats().getLastRun().getTime() >= monthAgo)).count()}));
		out.line(text.get("dialog.statistics.recentadded", new Object[] {profiles.stream().filter(p -> (p.getStats() != null) && (p.getStats().getCreated() != null)
				&& (p.getStats().getCreated().getTime() >= monthAgo)).count()}));
		out.line(text.get("dialog.statistics.sessions", new Object[] {own.size()}));

		List<Profile> byTime = profiles.stream().filter(p -> playTime(p) > 0).sorted(Comparator.comparingInt(ProfileReports::playTime).reversed()).limit(TOP).toList();
		out.section(text.get("dialog.statistics.mostplayed"));
		byTime.forEach(p -> out.row(W9, new String[] {ProfileStats.formatPlayTime(playTime(p))}, p.getTitle(), playTime(p)));
		if (byTime.isEmpty())
			out.empty();

		List<Profile> byStarts = profiles.stream().filter(p -> runs(p) > 0).sorted(Comparator.comparingInt(ProfileReports::runs).reversed()).limit(TOP).toList();
		out.section(text.get("dialog.statistics.moststarted"));
		byStarts.forEach(p -> out.row(W9, new String[] {String.valueOf(runs(p))}, p.getTitle(), runs(p)));
		if (byStarts.isEmpty())
			out.empty();

		// the longest single sessions, and per game its play time divided by its starts
		List<org.dbgl.model.repository.ProfileRepository.PlaySession> longest = own.stream()
				.sorted(Comparator.comparingInt(org.dbgl.model.repository.ProfileRepository.PlaySession::seconds).reversed()).limit(TOP).toList();
		out.section(text.get("dialog.statistics.longestsessions"));
		longest.forEach(x -> out.row(W9_22, new String[] {time(x.seconds()), df.format(x.started())}, byId.get(x.profileId()).getTitle(), x.seconds()));
		if (longest.isEmpty())
			out.empty();

		List<Profile> byAverage = profiles.stream().filter(p -> (playTime(p) > 0) && (runs(p) > 0))
				.sorted(Comparator.comparingDouble((Profile p) -> (double)playTime(p) / runs(p)).reversed()).limit(TOP).toList();
		out.section(text.get("dialog.statistics.longestaverage"));
		byAverage.forEach(p -> out.row(W9_22, new String[] {time(playTime(p) / runs(p)), ProfileStats.formatPlayTime(playTime(p)) + " / " + runs(p)}, p.getTitle(),
			playTime(p) / runs(p)));
		if (byAverage.isEmpty())
			out.empty();

		List<Profile> last = profiles.stream().filter(p -> (p.getStats() != null) && (p.getStats().getLastRun() != null))
				.sorted(Comparator.comparing((Profile p) -> p.getStats().getLastRun()).reversed()).limit(TOP).toList();
		out.section(text.get("dialog.statistics.lastplayed"));
		last.forEach(p -> out.row(W22, new String[] {df.format(p.getStats().getLastRun())}, p.getTitle(), -1));
		if (last.isEmpty())
			out.empty();

		// played, but not for the longest time (not those under 'last played' already)
		List<Profile> longAgo = profiles.stream().filter(p -> (p.getStats() != null) && (p.getStats().getLastRun() != null) && !last.contains(p))
				.sorted(Comparator.comparing((Profile p) -> p.getStats().getLastRun())).limit(TOP).toList();
		out.section(text.get("dialog.statistics.notplayedlong"));
		longAgo.forEach(p -> out.row(W22, new String[] {df.format(p.getStats().getLastRun())}, p.getTitle(), -1));
		if (longAgo.isEmpty())
			out.empty();

		List<Profile> added = profiles.stream().filter(p -> (p.getStats() != null) && (p.getStats().getCreated() != null))
				.sorted(Comparator.comparing((Profile p) -> p.getStats().getCreated()).reversed()).limit(TOP).toList();
		out.section(text.get("dialog.statistics.lastadded"));
		added.forEach(p -> out.row(W22, new String[] {df.format(p.getStats().getCreated())}, p.getTitle(), -1));
		if (added.isEmpty())
			out.empty();

		// when it was played: per calendar year, per month over all years, and per month of the current year
		java.util.Calendar cal = java.util.Calendar.getInstance(locale);
		cal.setTime(now);
		int thisYear = cal.get(java.util.Calendar.YEAR);
		java.util.TreeMap<Integer, long[]> years = new java.util.TreeMap<>();
		long[][] months = new long[12][2], monthsThisYear = new long[12][2];
		for (org.dbgl.model.repository.ProfileRepository.PlaySession x: own) {
			cal.setTime(x.started());
			int y = cal.get(java.util.Calendar.YEAR), m = cal.get(java.util.Calendar.MONTH);
			long[] c = years.computeIfAbsent(y, k -> new long[2]);
			c[0]++;
			c[1] += x.seconds();
			months[m][0]++;
			months[m][1] += x.seconds();
			if (y == thisYear) {
				monthsThisYear[m][0]++;
				monthsThisYear[m][1] += x.seconds();
			}
		}
		out.section(text.get("dialog.statistics.bycalendaryear"));
		years.forEach((y, c) -> out.row(W7_9, new String[] {String.valueOf(c[0]), time(c[1])}, String.valueOf(y), c[1]));
		if (years.isEmpty())
			out.empty();
		String[] monthNames = java.text.DateFormatSymbols.getInstance(locale).getMonths();
		out.section(text.get("dialog.statistics.bymonth"));
		for (int m = 0; m < 12; m++)
			out.row(W7_9, new String[] {String.valueOf(months[m][0]), months[m][0] == 0 ? "-": time(months[m][1])}, monthNames[m], months[m][1]);
		out.section(text.get("dialog.statistics.bymonththisyear", new Object[] {String.valueOf(thisYear)}));
		cal.setTime(now);
		for (int m = 0; m <= cal.get(java.util.Calendar.MONTH); m++)
			out.row(W7_9, new String[] {String.valueOf(monthsThisYear[m][0]), monthsThisYear[m][0] == 0 ? "-": time(monthsThisYear[m][1])}, monthNames[m],
				monthsThisYear[m][1]);

		String none = text.get("dialog.statistics.none");
		groups(out, text.get("dialog.statistics.byversion"), profiles, p -> p.getDosboxVersion() == null ? none: p.getDosboxVersion().getTitle(), none, true, false, Integer.MAX_VALUE);
		groups(out, text.get("dialog.statistics.bygenre"), profiles, p -> StringUtils.defaultIfBlank(StringUtils.strip(p.getGenre()), none), none, true, false, TOP);
		groups(out, text.get("dialog.statistics.bydecade2"), profiles, p -> {
			String y = releaseYear(p);
			return y != null ? text.get("dialog.statistics.decade", new Object[] {y.substring(0, 3) + "0"}): none;
		}, none, true, true, Integer.MAX_VALUE);
		groups(out, text.get("dialog.statistics.byreleaseyear"), profiles, p -> StringUtils.defaultIfEmpty(releaseYear(p), none), none, true, true, Integer.MAX_VALUE);
		groups(out, text.get("dialog.statistics.bydeveloper2"), profiles, p -> StringUtils.defaultIfBlank(StringUtils.strip(p.getDeveloper()), none), none, true, false, TOP);
		if (profiles.stream().anyMatch(p -> StringUtils.isNotBlank(p.getStatus())))
			groups(out, text.get("dialog.statistics.bystatus"), profiles, p -> StringUtils.defaultIfBlank(StringUtils.strip(p.getStatus()), none), none, false, false, Integer.MAX_VALUE);

		List<Profile> never = profiles.stream().filter(p -> runs(p) == 0).sorted(Comparator.comparing(p -> p.getTitle().toLowerCase())).toList();
		out.section(text.get("dialog.statistics.neverplayed", new Object[] {never.size()}));
		never.forEach(p -> out.row(new int[0], new String[0], p.getTitle(), -1));
	}

	/** one line: how long a profile was played, in how many sessions, and when last (main window and profile editor) */
	public static String playInfo(Profile prof, ITextService text) {
		try {
			int seconds = prof.getProfileStats() == null ? 0: prof.getProfileStats().getPlayTime();
			org.dbgl.model.repository.ProfileRepository.SessionSummary sessions = new org.dbgl.model.repository.ProfileRepository().sessionSummary(prof.getId());
			String total = time(seconds);
			if ((seconds <= 0) && (sessions.count() == 0))
				return text.get("dialog.main.playinfo.none");
			if (sessions.last() == null)
				return text.get("dialog.main.playinfo.nosession", new Object[] {total});
			org.dbgl.service.SettingsService settings = org.dbgl.service.SettingsService.getInstance();
			java.util.Locale locale = new java.util.Locale(settings.getValue("locale", "language"), settings.getValue("locale", "country"));
			String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(sessions.last().started());
			return text.get("dialog.main.playinfo", new Object[] {total, sessions.count(), when, time(sessions.last().seconds())});
		} catch (java.sql.SQLException e) {
			return "";
		}
	}

	/** the year of release of a profile (the first 4 digits), or null */
	private static String releaseYear(Profile p) {
		String y = StringUtils.left(StringUtils.strip(p.getYear()), 4);
		return (y != null) && y.matches("\\d{4}") ? y: null;
	}

	/** seconds as hours:minutes, "0:00" for none */
	private static String time(long seconds) {
		return StringUtils.defaultIfEmpty(ProfileStats.formatPlayTime((int)Math.min(Integer.MAX_VALUE, seconds)), "0:00");
	}

	/**
	 * The profiles counted per group, most first (or by name, for the decades), with their play time if wanted; the group
	 * 'none' (no value) always last. At most 'max' groups are listed. The bars show the number of profiles.
	 */
	private static void groups(Out out, String title, List<Profile> profiles, java.util.function.Function<Profile, String> groupOf, String none, boolean withTime,
			boolean byName, int max) {
		Map<String, long[]> counts = new LinkedHashMap<>(); // count and play time; groups are case-insensitive, shown as first seen
		Map<String, String> names = new LinkedHashMap<>();
		for (Profile p: profiles) {
			String g = groupOf.apply(p);
			String k = g.toLowerCase();
			names.putIfAbsent(k, g);
			long[] c = counts.computeIfAbsent(k, x -> new long[2]);
			c[0]++;
			c[1] += playTime(p);
		}
		String noneKey = none.toLowerCase();
		List<String> keys = new ArrayList<>(counts.keySet());
		keys.remove(noneKey);
		keys.sort(byName ? Comparator.naturalOrder(): Comparator.comparingLong((String k) -> counts.get(k)[0]).reversed().thenComparing(Comparator.naturalOrder()));
		boolean more = keys.size() > max;
		if (more)
			keys = new ArrayList<>(keys.subList(0, max));
		if (counts.containsKey(noneKey))
			keys.add(noneKey);
		out.section(title);
		for (String k: keys) {
			String count = String.valueOf(counts.get(k)[0]);
			if (withTime)
				out.row(W7_9, new String[] {count, StringUtils.defaultIfEmpty(ProfileStats.formatPlayTime((int)Math.min(Integer.MAX_VALUE, counts.get(k)[1])), "-")}, names.get(k),
					counts.get(k)[0]);
			else
				out.row(W7, new String[] {count}, names.get(k), counts.get(k)[0]);
		}
		if (more)
			out.more();
		if (keys.isEmpty())
			out.empty();
	}

	private static int playTime(Profile p) {
		return p.getProfileStats() == null ? 0: p.getProfileStats().getPlayTime();
	}

	private static int runs(Profile p) {
		return p.getStats() == null ? 0: p.getStats().getRuns();
	}

	/** what taking over a dosbox.conf would do: values to set, and settings the DOSBox version of the profile does not have */
	public record ImportPlan(Map<String, Map<String, String>> values, List<String> unknown) {
		public int count() {
			return values.values().stream().mapToInt(Map::size).sum();
		}
	}

	/** the settings of a dosbox.conf (not its [autoexec]) that the DOSBox version of the profile has and that change the profile */
	public static ImportPlan planImport(Profile profile, String confText) {
		Map<String, Map<String, String>> values = new LinkedHashMap<>();
		List<String> unknown = new ArrayList<>();
		Configuration dosboxConf = profile.getDosboxVersion().getConfiguration();
		Configuration combined = profile.getCombinedConfiguration();
		Presets.parse(confText).forEach((section, items) -> {
			if (section.equalsIgnoreCase("autoexec"))
				return;
			items.forEach((item, value) -> {
				if (MANAGED.contains(section + '/' + item.toLowerCase()))
					return;
				if (!dosboxConf.hasValue(section, item)) {
					unknown.add("[" + section + "] " + item + "=" + value);
					return;
				}
				if (!value.equals(StringUtils.strip(combined.getValue(section, item))))
					values.computeIfAbsent(section, k -> new LinkedHashMap<>()).put(item, value);
			});
		});
		return new ImportPlan(values, unknown);
	}

	public static void applyImport(Profile profile, ImportPlan plan) {
		plan.values().forEach((section, items) -> items.forEach((item, value) -> profile.setValue(section, item, value)));
	}

	/** the plan as text, for the confirmation */
	public static String describe(Profile profile, ImportPlan plan, ITextService text) {
		Configuration combined = profile.getCombinedConfiguration();
		StringBuilder sb = new StringBuilder(text.get("dialog.importconf.changes", new Object[] {plan.count(), profile.getTitle()})).append(NL).append(NL);
		plan.values().forEach((section, items) -> items.forEach((item, value) -> sb.append("[").append(section).append("] ").append(item).append(": ")
				.append(StringUtils.defaultIfEmpty(combined.getValue(section, item), "-")).append(" -> ").append(value).append(NL)));
		if (!plan.unknown().isEmpty()) {
			sb.append(NL).append(text.get("dialog.importconf.unknown", new Object[] {plan.unknown().size()})).append(NL);
			plan.unknown().forEach(l -> sb.append("   ").append(l).append(NL));
		}
		return sb.toString();
	}
}
