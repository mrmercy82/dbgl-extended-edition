/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dbgl.service.FileLocationService;


/**
 * Presets set a few settings of a profile or template at once, e.g. what Windows 95 needs. The built-in ones list several
 * candidate values per setting; the first one the DOSBox version accepts is used, and settings the DOSBox version does not
 * have are skipped. Own presets are saved from a profile as small configuration files in the folder 'presets'.
 */
public final class Presets {

	/** one setting of a preset; the first candidate value the DOSBox version accepts is used */
	public record Entry(String section, String item, String... candidates) {
	}

	/** @param key for the built-in ones the text key of the name, for own ones the name itself */
	public record Preset(String key, boolean builtIn, List<Entry> entries) {
	}

	private static Entry e(String section, String item, String... candidates) {
		return new Entry(section, item, candidates);
	}

	public static final List<Preset> BUILT_IN = List.of(
		new Preset("dialog.preset.win9x", true, List.of(e("dosbox", "memsize", "64"), e("cpu", "cputype", "pentium_mmx", "pentium", "pentium_slow"), e("cpu", "core", "normal"),
			e("dos", "ems", "off", "false"))),
		new Preset("dialog.preset.win31", true, List.of(e("dosbox", "machine", "svga_s3", "svga_s3trio64", "vesa_nolfb"), e("dosbox", "memsize", "16"),
			e("cpu", "cputype", "486", "486_slow", "486_prefetch", "auto"), e("cpu", "core", "normal"), e("dos", "ems", "off", "false"))),
		new Preset("dialog.preset.slow", true, List.of(e("cpu", "core", "normal"), e("cpu", "cputype", "286", "386", "auto"), e("cpu", "cycles", "fixed 3000"),
			e("cpu", "cpu_cycles", "3000"), e("cpu", "cpu_cycles_protected", "3000"))),
		new Preset("dialog.preset.fast", true, List.of(e("cpu", "core", "dynamic", "auto"), e("cpu", "cputype", "auto"), e("cpu", "cycles", "max"),
			e("cpu", "cpu_cycles", "max"), e("cpu", "cpu_cycles_protected", "max"))),
		new Preset("dialog.preset.tandy", true, List.of(e("dosbox", "machine", "tandy"), e("speaker", "tandy", "on", "auto"))),
		new Preset("dialog.preset.pc98", true, List.of(e("dosbox", "machine", "pc98"))));

	private Presets() {
	}

	public static File dir() {
		return new File(FileLocationService.getInstance().getProfilesDir().getParentFile(), "presets");
	}

	/** the own presets, sorted by name */
	public static List<Preset> listOwn() {
		List<Preset> result = new ArrayList<>();
		File[] files = dir().listFiles((d, n) -> n.toLowerCase().endsWith(".conf"));
		if (files == null)
			return result;
		Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase()));
		for (File f: files) {
			try {
				String name = f.getName().substring(0, f.getName().length() - 5);
				List<Entry> entries = new ArrayList<>();
				parse(Files.readString(f.toPath(), StandardCharsets.UTF_8)).forEach((section, items) -> items.forEach((item, value) -> entries.add(e(section, item, value))));
				result.add(new Preset(name, false, entries));
			} catch (IOException ex) {
				// skip unreadable files
			}
		}
		return result;
	}

	/** a plain configuration: [section] and item = value lines */
	public static Map<String, Map<String, String>> parse(String text) {
		Map<String, Map<String, String>> result = new LinkedHashMap<>();
		String section = null;
		for (String raw: text.split("\\R")) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#"))
				continue;
			if (line.startsWith("[") && line.endsWith("]")) {
				section = line.substring(1, line.length() - 1).strip().toLowerCase();
				continue;
			}
			int eq = line.indexOf('=');
			if ((section != null) && (eq > 0))
				result.computeIfAbsent(section, k -> new LinkedHashMap<>()).put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
		}
		return result;
	}

	/** file names may not contain characters that are not allowed on Windows */
	public static String safeName(String name) {
		return name.strip().replaceAll("[\\\\/:*?\"<>|]", "_");
	}

	public static File save(String name, Map<String, Map<String, String>> values) throws IOException {
		File dir = dir();
		dir.mkdirs();
		StringBuilder sb = new StringBuilder("# DBGL preset\n");
		values.forEach((section, items) -> {
			sb.append('\n').append('[').append(section).append(']').append('\n');
			items.forEach((item, value) -> sb.append(item).append('=').append(value).append('\n'));
		});
		File file = new File(dir, safeName(name) + ".conf");
		Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
		return file;
	}

	/**
	 * set the values of a preset, as far as the DOSBox version has the settings and accepts the values
	 *
	 * @return the number of settings that were set
	 */
	public static int apply(org.dbgl.model.entity.TemplateProfileBase configurable, Preset preset) {
		int applied = 0;
		for (Entry entry: preset.entries())
			if (setFirstSupported(configurable, entry.section(), entry.item(), entry.candidates()))
				applied++;
		return applied;
	}

	/** @return true if one of the values was set */
	static boolean setFirstSupported(org.dbgl.model.entity.TemplateProfileBase configurable, String section, String item, String... candidates) {
		if (configurable.getDosboxVersion() == null)
			return false;
		org.dbgl.model.conf.Configuration dosboxConf = configurable.getDosboxVersion().getConfiguration();
		if (!dosboxConf.hasValue(section, item))
			return false;
		org.dbgl.model.conf.ConfValueOptions.Options options = configurable.getDosboxVersion().getConfValueOptions().get(section, item);
		for (String candidate: candidates) {
			if ((options == null) || options.isFreeForm() || options.getValues().stream().anyMatch(v -> v.equalsIgnoreCase(candidate))) {
				configurable.setValue(section, item, candidate);
				return true;
			}
		}
		return false;
	}

	/** @return the file of an own preset as text, for editing */
	public static String read(String name) throws IOException {
		return Files.readString(new File(dir(), safeName(name) + ".conf").toPath(), StandardCharsets.UTF_8);
	}

	/** replace an own preset by the edited text, possibly under a new name; lines that are no settings are kept as they are */
	public static void update(String oldName, String newName, String text) throws IOException {
		if (parse(text).isEmpty())
			throw new IOException("no [section] with item=value lines");
		File dir = dir();
		dir.mkdirs();
		Files.writeString(new File(dir, safeName(newName) + ".conf").toPath(), text.endsWith("\n") ? text: text + '\n', StandardCharsets.UTF_8);
		if (!safeName(oldName).equalsIgnoreCase(safeName(newName)))
			delete(oldName);
	}

	public static void delete(String name) throws IOException {
		Files.deleteIfExists(new File(dir(), safeName(name) + ".conf").toPath());
	}
}
