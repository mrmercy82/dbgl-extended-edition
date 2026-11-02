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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.service.SettingsService;


/**
 * Lines of the [autoexec] of a profile that DBGL does not understand (e.g. added by hand) used to be dropped whenever DBGL
 * wrote the configuration again. Now they are always kept when a profile is read, wherever DBGL writes it afterwards
 * (profile dialog, editing several profiles, presets, switching the DOSBox version, ...). The settings decide how:
 * <ul>
 * <li>"auto": into the matching own section (At Start, Before Launch, After Launch, At Finish) with DBGL's "@REM" marker
 * lines, so they show up in the profile dialog; or, with "nomarkers", at the same place without marker lines;</li>
 * <li>"ask": at the same place without marker lines, until the user chooses line by line what to keep and where (when
 * opening the profile dialog, and in the preview when switching the DOSBox version).</li>
 * </ul>
 * Lines after "exit" were never run by DOSBox; they stay after "exit".
 */
public final class UnknownAutoexecLines {

	public static final String SECTION = "autoexec", MODE = "unknownlines", NO_MARKERS = "nomarkers";
	public static final String AUTO = "auto", ASK = "ask";
	private static final String KEPT_PLAIN = "keptplain.";

	/** a choice of the user for one line: keep it or not, in which own section, with or without marker lines */
	public record Decision(Autoexec.UnknownLine line, boolean keep, int section, boolean markers) {
	}

	private UnknownAutoexecLines() {
	}

	public static boolean ask() {
		return ASK.equals(SettingsService.getInstance().getValue(SECTION, MODE));
	}

	/** with "auto": the own sections without DBGL's marker lines */
	public static boolean withoutMarkers() {
		return !ask() && SettingsService.getInstance().getBooleanValue(SECTION, NO_MARKERS);
	}

	/** whether lines are put into the own sections with marker lines when a profile is read */
	public static boolean markersOnRead() {
		return !ask() && !withoutMarkers();
	}

	/** called after reading the configuration of a profile: keep its unknown lines, see the class description */
	public static void keepOnRead(Autoexec autoexec) {
		boolean markers = markersOnRead();
		for (Autoexec.UnknownLine l: autoexec.getUnknownLines())
			autoexec.placeLine(l, markers);
	}

	/** with "ask": the unknown lines of a profile the user has not decided on yet */
	public static List<Autoexec.UnknownLine> undecided(Profile profile) {
		Set<String> kept = keptPlain(profile.getId());
		return profile.getConfiguration().getAutoexec().getUnknownLines().stream().filter(l -> !kept.contains(hash(l.text()))).toList();
	}

	/**
	 * Carry out the choices of the user.
	 *
	 * @param remember lines kept without marker lines are remembered, so the user is not asked again (not for a preview)
	 */
	public static void decide(Profile profile, List<Decision> decisions, boolean remember) {
		Autoexec autoexec = profile.getConfiguration().getAutoexec();
		Set<String> kept = keptPlain(profile.getId());
		for (Decision d: decisions) {
			autoexec.removePlacedLine(d.line());
			kept.remove(hash(d.line().text()));
			if (d.keep()) {
				autoexec.placeLine(new Autoexec.UnknownLine(d.line().text(), d.section(), d.line().afterExit()), d.markers());
				if (!d.markers() || d.line().afterExit())
					kept.add(hash(d.line().text()));
			}
		}
		if (remember)
			SettingsService.getInstance().setValue(SECTION, KEPT_PLAIN + profile.getId(), String.join(",", kept));
	}

	private static Set<String> keptPlain(int profileId) {
		String value = SettingsService.getInstance().getValue(SECTION, KEPT_PLAIN + profileId);
		return StringUtils.isBlank(value) ? new LinkedHashSet<>(): Arrays.stream(value.split(",")).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static String hash(String line) {
		return Integer.toHexString(line.trim().hashCode());
	}
}
