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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;


/**
 * The settings a profile lost when it was switched to another DOSBox version (not known there, or the same as its default),
 * remembered for the kind of DOSBox it was switched from, in profiles/history/&lt;profile id&gt;/switched.conf. Switched back
 * to that kind of DOSBox, they are offered again (not in the configuration file itself: saving would leave them out).
 */
public final class SwitchMemory {

	private static final String FILE = "switched.conf";
	/** written by DBGL itself for each profile (its own folders): nothing to remember */
	private static final java.util.Set<String> OWN_PATHS = java.util.Set.of("captures", "capture_dir", "mapperfile");

	private SwitchMemory() {
	}

	private static File fileOf(int profileId) {
		return new File(ProfileHistory.dirOf(profileId), FILE);
	}

	/** what is remembered for this profile (empty if nothing, or for a new profile) */
	public static Properties load(int profileId) {
		Properties result = new Properties();
		File f = profileId >= 0 ? fileOf(profileId): null;
		if ((f != null) && f.isFile()) {
			try (InputStream in = Files.newInputStream(f.toPath())) {
				result.load(in);
			} catch (IOException e) {
				// nothing remembered then
			}
		}
		return result;
	}

	public static void save(int profileId, Properties memory) {
		if (profileId < 0)
			return;
		File f = fileOf(profileId);
		try {
			if (memory.isEmpty()) {
				Files.deleteIfExists(f.toPath());
				return;
			}
			f.getParentFile().mkdirs();
			// sorted: the same content gives the same file
			Properties sorted = new Properties() {
				private static final long serialVersionUID = 1L;

				@Override
				public synchronized java.util.Set<Map.Entry<Object, Object>> entrySet() {
					return java.util.Collections.unmodifiableSet(new java.util.TreeMap<>(memory).entrySet());
				}
			};
			try (OutputStream out = Files.newOutputStream(f.toPath())) {
				sorted.store(out, "DBGL: settings lost when the profile was switched to another kind of DOSBox (family|section|item)");
			}
		} catch (IOException e) {
			// not remembered: nothing worse than before
		}
	}

	private static int family(Generation gen) {
		return gen == null ? -1: GenerationAwareConfigurable.familyIdx(gen);
	}

	/**
	 * After switching: the settings the profile had before and has no longer, remembered for the kind of DOSBox it was switched
	 * from (not those only written under another name, nor the paths DBGL keeps itself); what is set in it now is no longer
	 * remembered for the kind of DOSBox it uses now (it is in it).
	 *
	 * @param choices what switching changed (see {@link SwitchChoices#after}): a setting with a new text was converted, not lost
	 */
	public static void rememberInto(Properties memory, Profile prof, SwitchChoices.Snapshot before, Generation from, List<SwitchChoices.Choice> choices) {
		int fam = family(from);
		Configuration conf = prof.getConfiguration();
		java.util.Set<String> converted = new java.util.HashSet<>();
		for (SwitchChoices.Choice c: choices)
			if ((c.kind() == SwitchChoices.Kind.SETTING) && (c.newText() != null))
				converted.add((c.section() + "|" + c.item()).toLowerCase(java.util.Locale.ROOT));
		if (fam >= 0)
			before.settings().forEach((section, items) -> items.forEach((item, value) -> {
				if (OWN_PATHS.contains(item.toLowerCase(java.util.Locale.ROOT)) || converted.contains((section + "|" + item).toLowerCase(java.util.Locale.ROOT)))
					return;
				if (!conf.hasValue(section, item) && StringUtils.isNotBlank(value))
					memory.setProperty(fam + "|" + section + "|" + item, value);
			}));
		int now = family(prof.getGeneration());
		for (String key: memory.stringPropertyNames()) {
			String[] k = key.split("\\|", 3);
			if ((k.length == 3) && k[0].equals(String.valueOf(now)) && conf.hasValue(k[1], k[2]))
				memory.remove(key);
		}
	}

	/**
	 * After switching: the settings remembered for the kind of DOSBox the profile uses now, that it does not set, that this
	 * DOSBox knows and that differ from its default (the same as the default: nothing to restore).
	 */
	public static List<SwitchChoices.Choice> restoresFrom(Properties memory, Profile prof) {
		List<SwitchChoices.Choice> result = new ArrayList<>();
		int fam = family(prof.getGeneration());
		if ((fam < 0) || (prof.getDosboxVersion() == null))
			return result;
		Configuration conf = prof.getConfiguration(), dosbox = prof.getDosboxVersion().getConfiguration();
		Map<String, String> sorted = new TreeMap<>();
		memory.stringPropertyNames().forEach(k -> sorted.put(k, memory.getProperty(k)));
		sorted.forEach((key, value) -> {
			String[] k = key.split("\\|", 3);
			if ((k.length != 3) || !k[0].equals(String.valueOf(fam)) || conf.hasValue(k[1], k[2]) || !dosbox.hasValue(k[1], k[2]))
				return;
			if (StringUtils.trim(dosbox.getValue(k[1], k[2])).equalsIgnoreCase(value.trim()))
				return;
			result.add(new SwitchChoices.Choice(prof.getId(), SwitchChoices.Kind.RESTORE, k[1], k[2], value, null, -1, false));
		});
		return result;
	}
}
