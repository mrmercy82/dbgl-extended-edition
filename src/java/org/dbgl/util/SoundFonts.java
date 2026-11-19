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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.SettingsService;


/**
 * alpha 52: the SoundFonts (.sf2, .sf3) for FluidSynth, to choose from a list: in the folder the user chose for them in DBGL
 * ([gui] soundfontdir), in the folder of the setting soundfont_dir (DOSBox Staging), in a folder "soundfonts" beside DOSBox,
 * in its folder of settings and in the data folder of DBGL, and where the system keeps them. Only these folders, not their
 * sub folders deeper than one level; at most MAX files.
 */
public final class SoundFonts {

	static final int MAX = 300;
	public static final String SETTING = "soundfontdir";

	public record Found(List<File> folders, List<File> files) {
	}

	private SoundFonts() {
	}

	public static boolean isSoundFont(File f) {
		String n = f.getName().toLowerCase(Locale.ROOT);
		return f.isFile() && (n.endsWith(".sf2") || n.endsWith(".sf3"));
	}

	/** the folders looked into, in this order, those that are there */
	public static List<File> folders(DosboxVersion v, String soundfontDirSetting) {
		Set<File> dirs = new LinkedHashSet<>();
		SettingsService settings = SettingsService.getInstance();
		if (settings.hasValue("gui", SETTING) && StringUtils.isNotBlank(settings.getValue("gui", SETTING)))
			dirs.add(new File(settings.getValue("gui", SETTING)));
		File cwd = v == null ? null: v.getCwd();
		if (StringUtils.isNotBlank(soundfontDirSetting)) {
			File d = new File(soundfontDirSetting.trim());
			if (d.isAbsolute() || (cwd == null))
				dirs.add(d);
			else { // DOSBox runs in the DOS folder of DBGL; beside DOSBox as well, where its own files are
				dirs.add(new File(cwd, soundfontDirSetting.trim()));
				if (v != null)
					dirs.add(new File(v.getCanonicalExecutable().getParentFile(), soundfontDirSetting.trim()));
			}
		}
		if (cwd != null)
			dirs.add(new File(cwd, "soundfonts"));
		if (v != null)
			dirs.add(new File(v.getCanonicalExecutable().getParentFile(), "soundfonts"));
		String home = System.getProperty("user.home");
		if (SystemUtils.IS_WINDOWS) {
			String local = System.getenv("LOCALAPPDATA");
			if (local != null)
				dirs.add(new File(local, "DOSBox" + File.separator + "soundfonts"));
			dirs.add(new File("C:\\soundfonts"));
		} else if (SystemUtils.IS_OSX) {
			dirs.add(new File(home, "Library/Preferences/DOSBox/soundfonts"));
			dirs.add(new File(home, "Library/Audio/Sounds/Banks"));
		} else {
			String xdgConfig = System.getenv("XDG_CONFIG_HOME"), xdgData = System.getenv("XDG_DATA_HOME");
			dirs.add(new File(StringUtils.isNotBlank(xdgConfig) ? new File(xdgConfig): new File(home, ".config"), "dosbox/soundfonts"));
			File data = StringUtils.isNotBlank(xdgData) ? new File(xdgData): new File(home, ".local/share");
			dirs.add(new File(data, "soundfonts"));
			dirs.add(new File(data, "sounds/sf2"));
			for (String d: new String[] {"/usr/local/share/soundfonts", "/usr/share/soundfonts", "/usr/share/sounds/sf2", "/usr/share/sounds/sf3"})
				dirs.add(new File(d));
		}
		dirs.add(new File(FileLocationService.getInstance().getDataDir(), "soundfonts"));
		List<File> result = new ArrayList<>();
		for (File d: dirs) {
			File a = d.getAbsoluteFile();
			if (a.isDirectory() && !result.contains(a))
				result.add(a);
		}
		return result;
	}

	public static Found find(DosboxVersion v, String soundfontDirSetting) {
		List<File> dirs = folders(v, soundfontDirSetting);
		Set<File> files = new LinkedHashSet<>();
		for (File d: dirs)
			collect(d, 0, files);
		List<File> sorted = new ArrayList<>(files);
		sorted.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
		return new Found(dirs, sorted.size() > MAX ? sorted.subList(0, MAX): sorted);
	}

	private static void collect(File dir, int depth, Set<File> into) {
		File[] list = dir.listFiles();
		if (list == null)
			return;
		Arrays.sort(list);
		for (File f: list) {
			if (into.size() >= MAX)
				return;
			if (isSoundFont(f))
				into.add(f.getAbsoluteFile());
			else if ((depth == 0) && f.isDirectory())
				collect(f, 1, into);
		}
	}

	/** the values for the field: the paths of the SoundFonts found */
	public static String[] values(DosboxVersion v, String soundfontDirSetting) {
		return find(v, soundfontDirSetting).files().stream().map(File::getPath).toArray(String[]::new);
	}
}
