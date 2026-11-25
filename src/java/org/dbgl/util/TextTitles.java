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
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.WebProfile;
import org.dbgl.util.searchengine.WebSearchEngine;


/**
 * Alpha 53 (TEST24): the title of a game folder not recognized by its files, from what lies in it: its text files (README,
 * READ.ME, *.TXT, *.DOC, FILE_ID.DIZ, *.NFO) and the volume label of its CD images. The candidates found are looked up in the
 * database of MobyGames that comes with DBGL; only a title found there with exactly that name counts. Everything is only read,
 * and only the beginning of each file.
 */
public final class TextTitles {

	/** a title found, and where (the name of the file) */
	public record Found(WebProfile game, String source) {
	}

	private static final int MAX_FILES = 12, MAX_BYTES = 16 * 1024, MAX_LINES = 40, MAX_LINE = 160, MAX_TRIES = 6;
	private static final long MAX_TEXT_FILE = 512L * 1024;
	private static final Charset CP437 = charset("IBM437");

	private static final Pattern TEXT_NAME = Pattern.compile("(?i)(readme.*|read\\.me|read_me.*|liesmich.*|file_id\\.diz|.*\\.nfo|.*\\.txt|.*\\.doc|.*\\.1st|info|manual.*)");
	private static final Pattern NOT_ABOUT_THE_GAME = Pattern.compile("(?i)(license|licence|copying|order|register|vendor|catalog|dos4gw|univbe|install|setup|sound.*|"
			+ "midi.*|vesa.*|mouse|ems|xms|memory|help|faq|trouble.*|whatsnew|history|changes|patch.*|update.*|bugs?)\\..*");
	private static final Pattern IMAGE_NAME = Pattern.compile("(?i).*\\.(iso|cue|bin|img|mdf)");

	/** "Welcome to Wing Commander II", "Thank you for buying Ultima VII" */
	private static final Pattern WELCOME = Pattern.compile("(?i)^(?:welcome to|thank you for (?:buying|purchasing|playing)|thanks for (?:buying|playing))\\s+(?:the\\s+)?(.+?)[.!:,]*$");
	/** "Wing Commander II Version 1.0", "DOOM v1.9", "Commander Keen 4 - release 1.4" */
	private static final Pattern VERSION = Pattern.compile("(?i)^(.+?)\\s*[-:,(]?\\s*(?:version|ver\\.?|v|release|rel\\.?)\\s*\\d+(?:\\.\\d+)*[a-z]?\\)?.*$");
	/** "Wing Commander II: Vengeance of the Kilrathi (c) 1991 Origin Systems" */
	private static final Pattern COPYRIGHT_AFTER = Pattern.compile("(?i)^(.+?)\\s*(?:\\(c\\)|©|copyright)\\s*(?:19|20)\\d\\d.*$");
	/** "README for Ultima Underworld", "Notes on Master of Orion" */
	private static final Pattern README_FOR = Pattern.compile("(?i)^(?:readme|read me|notes|release notes|information|installation notes)\\s+(?:for|on|to|about)\\s+(?:the\\s+)?(.+?)[.!:,]*$");
	private static final Pattern DECORATION = Pattern.compile("^[\\s\\p{Punct}\\u2500-\\u259F\\u00B0-\\u00BF]*$");

	private TextTitles() {
	}

	/** the candidates for the title in the text files and CD images of a game folder (its top and the folders right below) */
	public static List<String[]> candidates(File dir) {
		List<String[]> result = new ArrayList<>(); // {title, source}
		List<File> texts = new ArrayList<>(), images = new ArrayList<>();
		collect(dir, 0, texts, images);
		texts.sort(Comparator.comparingInt(TextTitles::rank).thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
		for (File f: texts.subList(0, Math.min(MAX_FILES, texts.size()))) {
			String text = read(f);
			if (text == null)
				continue;
			for (String t: fromText(text, f.getName().equalsIgnoreCase("file_id.diz")))
				result.add(new String[] {t, f.getName()});
		}
		for (File img: images.subList(0, Math.min(4, images.size()))) {
			String label = volumeLabel(img);
			if (label != null)
				result.add(new String[] {label, img.getName()});
		}
		return result;
	}

	/**
	 * the game in the database for the first candidate whose title it has exactly (compared as WebSearchEngine.comparable does);
	 * null if none
	 * @param lookup the entries of the database for a search term
	 */
	public static Found match(File dir, Function<String, List<WebProfile>> lookup) {
		Set<String> tried = new LinkedHashSet<>();
		for (String[] c: candidates(dir)) {
			String key = WebSearchEngine.comparable(c[0]);
			if (key.length() < 3 || !tried.add(key))
				continue;
			List<WebProfile> found;
			try {
				found = lookup.apply(c[0]);
			} catch (RuntimeException e) {
				continue;
			}
			if (found == null)
				continue;
			List<WebProfile> exact = found.stream().filter(w -> WebSearchEngine.comparable(w.getTitle()).equals(key)).toList();
			if (exact.isEmpty()) // "Wing Commander II" for "Wing Commander II: Vengeance of the Kilrathi"
				exact = found.stream().filter(w -> w.getTitle().contains(":") && WebSearchEngine.comparable(StringUtils.substringBefore(w.getTitle(), ":")).equals(key)).toList();
			if (!exact.isEmpty() && exact.stream().map(WebProfile::getGameId).distinct().count() == 1) // one game, not two of that name
				return new Found(exact.get(0), c[1]);
			if (tried.size() >= MAX_TRIES)
				break;
		}
		return null;
	}

	/** the candidates in a text: from lines like "Welcome to ...", "... Version 1.2", "... (c) 1992", the top of a FILE_ID.DIZ */
	public static List<String> fromText(String text, boolean diz) {
		Set<String> result = new LinkedHashSet<>();
		String[] lines = text.split("\\R", MAX_LINES + 1);
		int seen = 0;
		for (String raw: lines) {
			if (seen >= MAX_LINES)
				break;
			if (raw.length() > MAX_LINE) // a title is short; a long line (a paragraph, or a file without line breaks) took the patterns minutes (TEST26)
				continue;
			String line = tidy(raw);
			if (line.isEmpty() || DECORATION.matcher(line).matches())
				continue;
			seen++;
			if (diz && (seen == 1)) {
				String t = cut(VERSION.matcher(line).matches() ? VERSION.matcher(line).replaceFirst("$1"): line);
				if (t != null)
					result.add(t);
				continue;
			}
			for (Pattern p: new Pattern[] {WELCOME, README_FOR, VERSION, COPYRIGHT_AFTER}) {
				Matcher m = p.matcher(line);
				if (m.matches()) {
					String t = cut(m.group(1));
					if (t != null)
						result.add(t);
					break;
				}
			}
			// a title alone near the top, in capitals ("WING COMMANDER II"), as many readme files begin
			if ((seen <= 3) && line.equals(line.toUpperCase(Locale.ROOT)) && line.matches(".*[A-Z].*") && line.split(" ").length <= 8) {
				String t = cut(line);
				if (t != null)
					result.add(t);
			}
		}
		return new ArrayList<>(result);
	}

	/** the volume label of an ISO 9660 image (also in a .BIN of 2352 bytes per sector), "MONKEY2" -> "MONKEY2"; null if none */
	public static String volumeLabel(File image) {
		File data = image;
		if (image.getName().toLowerCase(Locale.ROOT).endsWith(".cue")) {
			File bin = new File(image.getParentFile(), StringUtils.substringBeforeLast(image.getName(), ".") + ".bin");
			if (!bin.isFile())
				return null;
			data = bin;
		}
		try (RandomAccessFile r = new RandomAccessFile(data, "r")) {
			for (long offset: new long[] {16L * 2048, 16L * 2352 + 16, 16L * 2352 + 24}) {
				if (r.length() < offset + 72)
					continue;
				byte[] b = new byte[72];
				r.seek(offset);
				r.readFully(b);
				if (b[0] == 1 && b[1] == 'C' && b[2] == 'D' && b[3] == '0' && b[4] == '0' && b[5] == '1') {
					String label = new String(b, 40, 32, java.nio.charset.StandardCharsets.US_ASCII).strip().replace('_', ' ');
					return (label.length() >= 3) && !label.matches("(?i)(cdrom|cd|disc ?\\d*|disk ?\\d*|new volume|untitled|\\d+)") ? label: null;
				}
			}
		} catch (IOException e) {
			// not readable: no label
		}
		return null;
	}

	private static void collect(File dir, int depth, List<File> texts, List<File> images) {
		File[] list = dir.listFiles();
		if (list == null)
			return;
		Arrays.sort(list, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
		for (File f: list) {
			if (f.isFile()) {
				String n = f.getName();
				if (TEXT_NAME.matcher(n).matches() && !NOT_ABOUT_THE_GAME.matcher(n).matches() && (f.length() > 0) && (f.length() <= MAX_TEXT_FILE))
					texts.add(f);
				else if (IMAGE_NAME.matcher(n).matches() && !n.toLowerCase(Locale.ROOT).endsWith(".bin") && (f.length() > 40 * 2048))
					images.add(f);
				else if (n.toLowerCase(Locale.ROOT).endsWith(".bin") && (f.length() > 40 * 2352) && !new File(f.getParentFile(), StringUtils.substringBeforeLast(n, ".") + ".cue").isFile())
					images.add(f);
			}
		}
		if (depth < 1)
			for (File f: list)
				if (f.isDirectory() && !f.getName().startsWith("."))
					collect(f, depth + 1, texts, images);
	}

	/** FILE_ID.DIZ first, then README, then the other text files */
	private static int rank(File f) {
		String n = f.getName().toLowerCase(Locale.ROOT);
		if (n.equals("file_id.diz"))
			return 0;
		if (n.startsWith("readme") || n.startsWith("read.me") || n.startsWith("read_me") || n.startsWith("liesmich"))
			return 1;
		return n.endsWith(".nfo") ? 2: 3;
	}

	/** the beginning of a text file (DOS code page 437), or null if it is no text */
	static String read(File f) {
		try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
			byte[] b = new byte[(int)Math.min(MAX_BYTES, r.length())];
			r.readFully(b);
			int control = 0;
			for (byte x: b)
				if ((x >= 0) && (x < 32) && (x != '\r') && (x != '\n') && (x != '\t') && (x != 12) && (x != 26))
					control++;
			if (control > b.length / 50) // a program or a document of a word processor
				return null;
			return new String(b, CP437);
		} catch (IOException e) {
			return null;
		}
	}

	private static String tidy(String raw) {
		String s = raw.replace('\t', ' ').replaceAll("[\\u2500-\\u259F*=~#|]+", " ").replaceAll("\\s+", " ").strip();
		return s;
	}

	/** a candidate as a title: not too short or long, not a sentence, without quotes around it; null if it cannot be one */
	private static String cut(String s) {
		if (s == null)
			return null;
		String t = StringUtils.strip(s.strip(), "\"'`-:.,;!()[]<> ");
		if ((t.length() < 3) || (t.length() > 70) || (t.split(" ").length > 10))
			return null;
		if (t.matches("(?i).*\\b(you|your|please|this file|following|program|files?|disk|drive|computer|installation|instructions)\\b.*"))
			return null;
		return t;
	}

	private static Charset charset(String name) {
		try {
			return Charset.forName(name);
		} catch (RuntimeException e) {
			return java.nio.charset.StandardCharsets.ISO_8859_1;
		}
	}
}
