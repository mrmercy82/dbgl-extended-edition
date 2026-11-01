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
package org.dbgl.gui.abstractdialog;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.dbgl.constants.Constants;
import org.dbgl.service.FileLocationService;


/**
 * Which pages of the profile/template dialog have content, and where the settings without a field of their own go, per
 * dialog and DOSBox version. Finding that out means creating all pages, which takes a moment; it is remembered while DBGL
 * runs and kept on disk (in the folder cache/pages), so the dialog opens quickly the first time after DBGL was started too.
 * The key contains everything the result depends on: the DBGL version, the dialog, the DOSBox version and the content of its
 * configuration file.
 */
public final class PageTreeCache {

	public record Info(Set<String> shownPages, Map<String, List<String[]>> extrasByPage) {
	}

	private static final int KEEP_FILES = 100;
	private static final Map<String, Optional<Info>> MEMORY = new ConcurrentHashMap<>();
	private static volatile File dirOverride_;

	private PageTreeCache() {
	}

	/** for the tests */
	static void setDir(File dir) {
		dirOverride_ = dir;
		MEMORY.clear();
	}

	static File dir() {
		if (dirOverride_ != null)
			return dirOverride_;
		try {
			File dataDir = FileLocationService.getInstance().getDataDir();
			return dataDir == null ? null: new File(new File(dataDir, "cache"), "pages");
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** @return the key with the DBGL version, the layout of the dialog may change with it */
	static String fullKey(String key) {
		return Constants.PROGRAM_VERSION + '|' + key;
	}

	public static Info get(String key) {
		String full = fullKey(key);
		return MEMORY.computeIfAbsent(full, k -> Optional.ofNullable(load(k))).orElse(null);
	}

	public static boolean contains(String key) {
		return get(key) != null;
	}

	public static void put(String key, Info info) {
		String full = fullKey(key);
		MEMORY.put(full, Optional.of(info));
		store(full, info);
	}

	/** forget everything, e.g. when the configuration file of a DOSBox version was edited outside DBGL */
	public static void clear() {
		MEMORY.clear();
		File dir = dir();
		File[] files = dir == null ? null: dir.listFiles((d, n) -> n.endsWith(".txt"));
		if (files != null)
			Arrays.stream(files).forEach(File::delete);
	}

	private static File fileOf(String fullKey) {
		File dir = dir();
		if (dir == null)
			return null;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-1").digest(fullKey.getBytes(StandardCharsets.UTF_8));
			return new File(dir, HexFormat.of().formatHex(hash) + ".txt");
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}

	public static String serialize(String fullKey, Info info) {
		StringBuilder sb = new StringBuilder(fullKey.replaceAll("[\\r\\n]", " ")).append('\n');
		info.shownPages().forEach(p -> sb.append("P\t").append(p).append('\n'));
		info.extrasByPage().forEach((page, settings) -> settings.forEach(s -> sb.append("E\t").append(page).append('\t').append(s[0]).append('\t').append(s[1]).append('\n')));
		return sb.toString();
	}

	/** @return null if the text is not for this key or not readable */
	public static Info deserialize(String fullKey, String text) {
		String[] lines = text.split("\n");
		if ((lines.length == 0) || !lines[0].equals(fullKey.replaceAll("[\\r\\n]", " ")))
			return null;
		Set<String> pages = new LinkedHashSet<>();
		Map<String, List<String[]>> extras = new HashMap<>();
		for (int i = 1; i < lines.length; i++) {
			String[] f = lines[i].split("\t", -1);
			if ((f.length == 2) && f[0].equals("P"))
				pages.add(f[1]);
			else if ((f.length == 4) && f[0].equals("E"))
				extras.computeIfAbsent(f[1], k -> new ArrayList<>()).add(new String[] {f[2], f[3]});
			else if (!lines[i].isEmpty())
				return null;
		}
		return pages.isEmpty() ? null: new Info(pages, extras);
	}

	private static Info load(String fullKey) {
		File file = fileOf(fullKey);
		if ((file == null) || !file.isFile())
			return null;
		try {
			Info info = deserialize(fullKey, Files.readString(file.toPath(), StandardCharsets.UTF_8));
			if (info != null)
				file.setLastModified(System.currentTimeMillis()); // recently used, kept when pruning
			return info;
		} catch (IOException e) {
			return null;
		}
	}

	private static void store(String fullKey, Info info) {
		File file = fileOf(fullKey);
		if (file == null)
			return;
		try {
			file.getParentFile().mkdirs();
			Files.writeString(file.toPath(), serialize(fullKey, info), StandardCharsets.UTF_8);
			prune(file.getParentFile());
		} catch (IOException e) {
			// not kept on disk, it is found out again next time
		}
	}

	/** keep the most recently used files only */
	private static void prune(File dir) {
		File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
		if ((files == null) || (files.length <= KEEP_FILES))
			return;
		Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
		for (int i = KEEP_FILES; i < files.length; i++)
			files[i].delete();
	}
}
