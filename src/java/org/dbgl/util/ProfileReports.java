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

	public static String statistics(List<Profile> profiles, ITextService text) {
		StringBuilder sb = new StringBuilder();
		long total = profiles.stream().mapToLong(ProfileReports::playTime).sum();
		long played = profiles.stream().filter(p -> runs(p) > 0).count();
		long starts = profiles.stream().mapToLong(ProfileReports::runs).sum();
		sb.append(text.get("dialog.statistics.profiles", new Object[] {profiles.size()})).append(NL);
		sb.append(text.get("dialog.statistics.played", new Object[] {played, profiles.size() - played})).append(NL);
		sb.append(text.get("dialog.statistics.starts", new Object[] {starts})).append(NL);
		sb.append(text.get("dialog.statistics.totaltime", new Object[] {StringUtils.defaultIfEmpty(ProfileStats.formatPlayTime((int)Math.min(Integer.MAX_VALUE, total)), "0:00")})).append(NL);

		List<Profile> byTime = profiles.stream().filter(p -> playTime(p) > 0).sorted(Comparator.comparingInt(ProfileReports::playTime).reversed()).limit(10).toList();
		section(sb, text.get("dialog.statistics.mostplayed"));
		byTime.forEach(p -> sb.append(pad(ProfileStats.formatPlayTime(playTime(p)), 9)).append(p.getTitle()).append(NL));
		if (byTime.isEmpty())
			sb.append("-").append(NL);

		List<Profile> byStarts = profiles.stream().filter(p -> runs(p) > 0).sorted(Comparator.comparingInt(ProfileReports::runs).reversed()).limit(10).toList();
		section(sb, text.get("dialog.statistics.moststarted"));
		byStarts.forEach(p -> sb.append(pad(String.valueOf(runs(p)), 9)).append(p.getTitle()).append(NL));
		if (byStarts.isEmpty())
			sb.append("-").append(NL);

		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
		List<Profile> last = profiles.stream().filter(p -> (p.getStats() != null) && (p.getStats().getLastRun() != null))
				.sorted(Comparator.comparing((Profile p) -> p.getStats().getLastRun()).reversed()).limit(10).toList();
		section(sb, text.get("dialog.statistics.lastplayed"));
		last.forEach(p -> sb.append(pad(df.format(p.getStats().getLastRun()), 22)).append(p.getTitle()).append(NL));
		if (last.isEmpty())
			sb.append("-").append(NL);

		List<Profile> never = profiles.stream().filter(p -> runs(p) == 0).sorted(Comparator.comparing(p -> p.getTitle().toLowerCase())).toList();
		section(sb, text.get("dialog.statistics.neverplayed", new Object[] {never.size()}));
		never.forEach(p -> sb.append(p.getTitle()).append(NL));
		return sb.toString();
	}

	private static void section(StringBuilder sb, String title) {
		sb.append(NL).append(title).append(NL).append("-".repeat(Math.max(3, title.length()))).append(NL);
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
