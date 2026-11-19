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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;


/**
 * alpha 52: profiles as games in LaunchBox, on the platform MS-DOS ({@code Data\Platforms\MS-DOS.xml}). Only new games are
 * added, at the end of the file; everything that is in it stays as it is, byte for byte. Before, the file is copied to
 * {@code MS-DOS.xml.dbgl-backup}. A game that is there already (the same program and command line) is left alone. The
 * cover goes to {@code Images\MS-DOS\Box - Front}, the other pictures to {@code Screenshot - Gameplay}, unless LaunchBox has
 * pictures of that game there already.
 */
public final class LaunchBoxExport {

	/** a profile as LaunchBox starts it, with what LaunchBox shows of it */
	public record Entry(String title, File exe, File startDir, String commandLine, String developer, String publisher, String year, String genre, String notes,
			File manual, File cover, List<File> screenshots) {
	}

	public record Result(int added, int already, File backup) {
	}

	static final String PLATFORM = "MS-DOS";
	static final String BACKUP_SUFFIX = ".dbgl-backup";
	private static final Pattern END = Pattern.compile("</LaunchBox>\\s*$");
	private static final Pattern EMPTY = Pattern.compile("<LaunchBox\\s*/>\\s*$");

	private LaunchBoxExport() {
	}

	/** the LaunchBox folder: the one chosen or one above it; null if it is none */
	public static File rootOf(File chosen) {
		return LaunchBoxImport.rootOf(chosen);
	}

	/** the file of the games of the platform MS-DOS; null if LaunchBox has no such platform yet */
	public static File platformFile(File root) {
		File f = new File(root, "Data" + File.separator + "Platforms" + File.separator + PLATFORM + ".xml");
		return f.isFile() ? f: null;
	}

	public static boolean launchBoxRunning() {
		return ProcessHandle.allProcesses().anyMatch(p -> p.info().command().map(c -> {
			String name = new File(c).getName().toLowerCase(Locale.ROOT);
			return name.equals("launchbox.exe") || name.equals("bigbox.exe");
		}).orElse(false));
	}

	public static Result add(File root, List<Entry> entries, boolean pictures) throws IOException {
		File xml = platformFile(root);
		if (xml == null)
			throw new IOException("no platform " + PLATFORM + " in " + root);
		byte[] bytes = Files.readAllBytes(xml.toPath());
		boolean bom = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF;
		String text = new String(bytes, bom ? 3: 0, bytes.length - (bom ? 3: 0), StandardCharsets.UTF_8);
		String nl = text.contains("\r\n") ? "\r\n": "\n";

		Set<String> there = new HashSet<>();
		try (InputStream in = new FileInputStream(xml)) {
			LaunchBoxImport.readElements(in, "Game", f -> there.add(keyOf(f.get("ApplicationPath"), f.get("CommandLine"))));
		} catch (javax.xml.stream.XMLStreamException | RuntimeException e) {
			throw new IOException(xml + ": " + e.getMessage(), e);
		}

		StringBuilder games = new StringBuilder();
		int added = 0, already = 0;
		String now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		for (Entry e: entries) {
			if (!there.add(keyOf(e.exe().getPath(), e.commandLine()))) {
				already++;
				continue;
			}
			games.append(gameXml(e, now, nl));
			added++;
			if (pictures)
				copyPictures(root, e);
		}
		if (added == 0)
			return new Result(0, already, null);

		Matcher m = END.matcher(text);
		String result;
		if (m.find())
			result = text.substring(0, m.start()) + games + text.substring(m.start());
		else if ((m = EMPTY.matcher(text)).find())
			result = text.substring(0, m.start()) + "<LaunchBox>" + nl + games + "</LaunchBox>" + text.substring(m.end());
		else
			throw new IOException(xml + ": no LaunchBox file");

		File backup = new File(xml.getPath() + BACKUP_SUFFIX);
		Files.copy(xml.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
		File tmp = new File(xml.getPath() + ".dbgl-new");
		byte[] out = result.getBytes(StandardCharsets.UTF_8);
		try (java.io.OutputStream o = Files.newOutputStream(tmp.toPath())) {
			if (bom)
				o.write(new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF});
			o.write(out);
		}
		try {
			Files.move(tmp.toPath(), xml.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException ex) {
			Files.move(tmp.toPath(), xml.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		return new Result(added, already, backup);
	}

	/** the notes of a profile for LaunchBox: without the line an import of DBGL puts at the end ("GOG: folder of the game") */
	public static String notesFor(String notes) {
		return notes == null ? null: notes.replaceFirst("(?:(?:\\r?\\n){1,2}|^)(?:GOG|Steam|DOSBox|eXoDOS|LaunchBox): [^\\r\\n]*[\\\\/][^\\r\\n]*\\s*$", "").stripTrailing();
	}

	static String keyOf(String exe, String commandLine) {
		return StringUtils.defaultString(exe).trim().replace('/', '\\').toLowerCase(Locale.ROOT) + "|" + StringUtils.defaultString(commandLine).trim();
	}

	static String gameXml(Entry e, String now, String nl) {
		StringBuilder b = new StringBuilder("  <Game>").append(nl);
		field(b, "ID", UUID.randomUUID().toString(), nl);
		field(b, "Title", e.title(), nl);
		field(b, "ApplicationPath", e.exe().getPath(), nl);
		field(b, "CommandLine", e.commandLine(), nl);
		field(b, "RootFolder", e.startDir() == null ? null: e.startDir().getPath(), nl);
		field(b, "UseDosBox", "false", nl);
		field(b, "Platform", PLATFORM, nl);
		field(b, "Source", "DBGL", nl);
		field(b, "Developer", e.developer(), nl);
		field(b, "Publisher", e.publisher(), nl);
		if (StringUtils.defaultString(e.year()).trim().matches("\\d{4}"))
			field(b, "ReleaseDate", e.year().trim() + "-01-01T00:00:00" + now.substring(19), nl);
		field(b, "Genre", e.genre(), nl);
		field(b, "Notes", e.notes(), nl);
		field(b, "ManualPath", e.manual() == null ? null: e.manual().getPath(), nl);
		field(b, "DateAdded", now, nl);
		field(b, "DateModified", now, nl);
		return b.append("  </Game>").append(nl).toString();
	}

	private static void field(StringBuilder b, String name, String value, String nl) {
		if (StringUtils.isBlank(value))
			return;
		b.append("    <").append(name).append('>').append(escape(value.replace("\r\n", "\n").replace("\n", nl))).append("</").append(name).append('>').append(nl);
	}

	/** text for XML 1.0: the five special characters escaped, characters XML does not allow left out */
	static String escape(String s) {
		StringBuilder b = new StringBuilder(s.length());
		s.codePoints().forEach(c -> {
			switch (c) {
				case '&' -> b.append("&amp;");
				case '<' -> b.append("&lt;");
				case '>' -> b.append("&gt;");
				case '"' -> b.append("&quot;");
				case '\'' -> b.append("&apos;");
				default -> {
					if (c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD) || c >= 0x10000)
						b.appendCodePoint(c);
				}
			}
		});
		return b.toString();
	}

	/** the name LaunchBox gives pictures of a game: its title, with the characters a file name cannot have as "_" */
	static String pictureName(String title) {
		return title.replaceAll("[\\\\/:*?\"<>|']", "_").trim();
	}

	static void copyPictures(File root, Entry e) throws IOException {
		File images = new File(root, "Images" + File.separator + PLATFORM);
		String name = pictureName(e.title());
		if (name.isEmpty())
			return;
		if (e.cover() != null && e.cover().isFile())
			copyNumbered(new File(images, "Box - Front"), name, List.of(e.cover()));
		if (e.screenshots() != null && !e.screenshots().isEmpty())
			copyNumbered(new File(images, "Screenshot - Gameplay"), name, e.screenshots().subList(0, Math.min(9, e.screenshots().size())));
	}

	/** as name-01.png, name-02.jpg ...; nothing if LaunchBox has pictures of this name there already (also in a region folder) */
	private static void copyNumbered(File dir, String name, List<File> files) throws IOException {
		if (hasPicture(dir, name, 0))
			return;
		dir.mkdirs();
		int n = 1;
		for (File f: files) {
			if (!f.isFile())
				continue;
			String ext = FilenameUtils.getExtension(f.getName()).toLowerCase(Locale.ROOT);
			Files.copy(f.toPath(), new File(dir, String.format(Locale.ROOT, "%s-%02d.%s", name, n++, ext)).toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static boolean hasPicture(File dir, String name, int depth) {
		File[] files = dir.listFiles();
		if (files == null)
			return false;
		String prefix = (name + "-").toLowerCase(Locale.ROOT);
		for (File f: files)
			if (f.isFile() ? f.getName().toLowerCase(Locale.ROOT).startsWith(prefix) && f.getName().substring(prefix.length()).matches("\\d{2}\\..*"): depth == 0 && hasPicture(f, name, 1))
				return true;
		return false;
	}
}
