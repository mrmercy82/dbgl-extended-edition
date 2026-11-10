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
import java.util.Map;
import java.util.function.Predicate;

import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.conf.Configuration;


/**
 * What gets lost or changed when a profile is switched to another DOSBox version, line by line, so that the user can keep the
 * previous line: lines of the [autoexec] DBGL does not write again (e.g. added by hand) and settings that are dropped or get
 * another value.
 */
public final class SwitchChoices {

	/** RESTORE: a setting lost when the profile was switched away from this kind of DOSBox before (see {@link SwitchMemory}) */
	public enum Kind {
		AUTOEXEC, SETTING, RESTORE
	}

	/**
	 * @param section         the section of a setting, "autoexec" for a line of the [autoexec]
	 * @param item            the name of a setting, null for a line of the [autoexec]
	 * @param oldText         the previous value or line
	 * @param newText         the value after switching, null if dropped
	 * @param autoexecSection for a line of the [autoexec]: the own section it is kept in, see {@link Autoexec.UnknownLine}
	 * @param afterExit       for a line of the [autoexec]: it came after "exit" and was never run
	 */
	public record Choice(int profileId, Kind kind, String section, String item, String oldText, String newText, int autoexecSection, boolean afterExit) {

		/** lines added by hand are kept (a line after "exit" stays there); converted settings take the new value; lost ones come back */
		public boolean defaultKeep() {
			return (kind == Kind.AUTOEXEC) || (kind == Kind.RESTORE);
		}

		/** identifies the choice again when switching for real after the preview */
		public String key() {
			return profileId + "\u0000" + kind + '\u0000' + section + '\u0000' + item + '\u0000' + oldText;
		}
	}

	/** the state of a profile before switching */
	public record Snapshot(Map<String, Map<String, String>> settings, List<Autoexec.UnknownLine> unknownLines) {
	}

	private SwitchChoices() {
	}

	/**
	 * Call after loading the configuration of the profile and before switching. With "ask" (see {@link UnknownAutoexecLines}),
	 * lines the user already decided to keep are not offered again.
	 */
	public static Snapshot before(Profile profile) {
		List<Autoexec.UnknownLine> lines = UnknownAutoexecLines.ask() ? UnknownAutoexecLines.undecided(profile): profile.getConfiguration().getAutoexec().getUnknownLines();
		return new Snapshot(values(profile.getConfiguration()), new ArrayList<>(lines));
	}

	/** call after switching: everything the user can keep */
	public static List<Choice> after(Snapshot before, Profile profile) {
		List<Choice> result = new ArrayList<>();
		for (Autoexec.UnknownLine l: before.unknownLines())
			result.add(new Choice(profile.getId(), Kind.AUTOEXEC, "autoexec", null, l.text(), null, l.section(), l.afterExit()));
		Configuration conf = profile.getConfiguration();
		Map<String, Map<String, String>> after = values(conf);
		before.settings().forEach((section, items) -> items.forEach((item, value) -> {
			String now = conf.hasValue(section, item) ? conf.getValue(section, item): null;
			if (now == null)
				now = renamedTo(section, item, value, before.settings(), after);
			if (!value.equals(now))
				result.add(new Choice(profile.getId(), Kind.SETTING, section, item, value, now, -1, false));
		}));
		return result;
	}

	/**
	 * Keep the previous line for the choices the user wants. Lines of the [autoexec] were already kept when the profile was
	 * read (see {@link UnknownAutoexecLines}); one not wanted is taken out, a wanted one is put with or without marker lines.
	 */
	public static List<Choice> apply(Profile profile, List<Choice> choices, Predicate<Choice> keep, boolean markers, boolean preview) {
		List<Choice> kept = new ArrayList<>();
		List<UnknownAutoexecLines.Decision> decisions = new ArrayList<>();
		for (Choice c: choices) {
			boolean k = keep.test(c);
			if (c.kind() == Kind.AUTOEXEC)
				decisions.add(new UnknownAutoexecLines.Decision(new Autoexec.UnknownLine(c.oldText(), c.autoexecSection(), c.afterExit()), k, c.autoexecSection(), markers));
			else if (k)
				profile.getConfiguration().setValue(c.section(), c.item(), c.oldText());
			if (k)
				kept.add(c);
		}
		if (!decisions.isEmpty())
			UnknownAutoexecLines.decide(profile, decisions, !preview);
		return kept;
	}

	/** a dropped setting whose value now is under another name (e.g. glshader -> shader): "shader=value", else null */
	private static String renamedTo(String section, String item, String value, Map<String, Map<String, String>> before, Map<String, Map<String, String>> after) {
		for (Map.Entry<String, Map<String, String>> s: after.entrySet())
			for (Map.Entry<String, String> i: s.getValue().entrySet()) {
				boolean isNew = !before.containsKey(s.getKey()) || !before.get(s.getKey()).containsKey(i.getKey());
				if (isNew && i.getValue().equalsIgnoreCase(value))
					return (s.getKey().equals(section) ? "": "[" + s.getKey() + "] ") + i.getKey() + "=" + i.getValue();
			}
		return null;
	}

	private static Map<String, Map<String, String>> values(Configuration conf) {
		Map<String, Map<String, String>> result = new LinkedHashMap<>();
		for (String section: conf.getSectionNames()) {
			Map<String, String> items = new LinkedHashMap<>();
			for (String item: conf.getItemNames(section))
				items.put(item, conf.getValue(section, item));
			result.put(section, items);
		}
		return result;
	}
}
