/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-10-31. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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
package org.dbgl.model.conf;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.dbgl.exception.InvalidMountstringException;
import org.dbgl.model.FileLocation;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.ImageMount;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.model.factory.MountFactory;
import org.dbgl.service.FileLocationService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.SystemUtils;


public class Autoexec {

	class DosPath {
		String drive_, directory_, file_;

		public DosPath(FileLocation hostFileLocation, List<Mount> mountingpoints) {
			int minLengthMount = Integer.MAX_VALUE;
			for (Mount mount: mountingpoints) {
				File dosboxDir = mount.canBeUsedFor(hostFileLocation);
				// a ":" in it: no DOS path (e.g. "mount c D:\games" of Windows, read on Linux, where D:\games is no path of its
				// own): written as it was read, else "cd \D:\games\..." grew with each saving (alpha 46)
				if ((dosboxDir != null) && dosboxDir.getPath().contains(":"))
					continue;
				// not made relative to the mount (e.g. "mount C ~/games": a path DBGL cannot resolve): the whole path would become
				// "cd \~\games\..." and grow with each saving (found with DBConfWS, alpha 46); written as it was read instead
				if ((dosboxDir != null) && !dosboxDir.isAbsolute() && dosboxDir.equals(hostFileLocation.getCanonicalFile()))
					continue;
				if (dosboxDir != null && (dosboxDir.getPath().length() < minLengthMount)) {
					drive_ = mount.getDrive() + ":";
					directory_ = dosboxDir.getParent() == null ? StringUtils.EMPTY: FilenameUtils.separatorsToWindows(dosboxDir.getParent());
					file_ = hostFileLocation.getFile().getName();
					minLengthMount = dosboxDir.getPath().length();
					if (mount instanceof DirMount dirMount) {
						// names with spaces or longer than 8.3 don't work in DOS commands, use the short names DOSBox gives them
						List<String> dos = DosboxShortNames.toDosPath(dirMount.getCanonicalPath(), dosboxDir);
						if (!dos.isEmpty()) {
							directory_ = String.join("\\", dos.subList(0, dos.size() - 1));
							file_ = dos.get(dos.size() - 1);
						}
					}
				}
			}
		}
	}

	private static final String DFR_4DOS_PREFIX = "C:\\FREEDOS\\4DOS.COM /C ";
	private static final String DFR_DOS32A_PREFIX = "C:\\FREEDOS\\DOS32A.EXE ";

	private static final String[] CUSTOM_SECTION_MARKERS = {"@REM START", "@REM /START", "@REM PRE-LAUNCH", "@REM /PRE-LAUNCH", "@REM POST-LAUNCH", "@REM /POST-LAUNCH", "@REM FINISH", "@REM /FINISH"};
	public static final int SECTIONS = CUSTOM_SECTION_MARKERS.length / 2;
	public static final int BOOTER_IMAGES = 3;

	private static final Pattern LOADHIGH_PATRN = Pattern.compile("^(?:lh|loadhigh)\\s+(.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern LOADFIX_PATRN = Pattern.compile("^(?:loadfix\\s+(-f)\\s*|loadfix(\\s+-\\d+)?(\\s+.*)?)$", Pattern.CASE_INSENSITIVE);
	// an image in quotes may hold anything, also " - " ("Game - Disk 1.img"): such a line was not read and dropped on saving
	private static final Pattern BOOT_PATRN = Pattern.compile("^(?:boot)(?=\\s+.\\S+)((?:\\s+(?:\"[^\"]*\"|[^-\\s][^\\s]*))*)(?:\\s+-l\\s+([acd]):?)?\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern BOOTIMGS_PATRN = Pattern.compile("^((?:\"[^\"]+\")|(?:[^\\s]+))(?:\\s((?:\"[^\"]+\")|(?:[^\\s]+)))?(?:\\s((?:\"[^\"]+\")|(?:[^\\s]+)))?$", Pattern.CASE_INSENSITIVE);
	private static final int DEFAULT_LOADFIX_VALUE = 64;

	private String main_, parameters_, img_[], imgDriveletter_, mixer_, keyb_, ipxnet_;
	private Boolean loadfix_, loadhigh_, exit_, pause_, booterByDefault_;
	private Integer loadfixValue_;
	private boolean longNamesWithSpaces_; // names DOS can't use: with spaces (written by an older DBGL version) or too long (e.g. "cd \\DEATHGATE")
	private String[] customSections_;
	private List<Mount> mountingpoints_;

	public Autoexec() {
		main_ = StringUtils.EMPTY;
		parameters_ = StringUtils.EMPTY;
		img_ = new String[BOOTER_IMAGES];
		for (int i = 0; i < BOOTER_IMAGES; i++)
			img_[i] = StringUtils.EMPTY;
		imgDriveletter_ = StringUtils.EMPTY;
		mixer_ = StringUtils.EMPTY;
		keyb_ = StringUtils.EMPTY;
		ipxnet_ = StringUtils.EMPTY;
		loadfix_ = false;
		loadhigh_ = false;
		exit_ = false;
		pause_ = false;
		booterByDefault_ = false;
		loadfixValue_ = 0;
		customSections_ = new String[SECTIONS];
		for (int i = 0; i < SECTIONS; i++)
			customSections_[i] = StringUtils.EMPTY;
		mountingpoints_ = new ArrayList<>();
	}

	/**
	 * A line of the [autoexec] that DBGL does not understand and therefore does not write again, typically added by hand, e.g.
	 * a second program started before the game or a line after "exit".
	 *
	 * @param text      the line as it was in the file
	 * @param section   the own section it belongs in when kept: 0 start, 1 before the game, 2 after the game, 3 finish
	 * @param afterExit the line came after "exit", so DOSBox never ran it
	 */
	public record UnknownLine(String text, int section, boolean afterExit) {
	}

	private List<UnknownLine> unknownLines_ = new ArrayList<>();

	/**
	 * the lines that should start the game where it was not found (e.g. "Y:", "cd \\", "GAME.EXE" with drive Y mounted only by
	 * the configuration of another DOSBox version). They are kept without marker lines, so that they start the game again where
	 * it is found (switched back to that DOSBox version); in an own section they were not taken for the game any more
	 */
	private java.util.Set<String> startLines_ = new java.util.HashSet<>();

	/** @return the lines of the [autoexec] read last that DBGL does not write again, see {@link UnknownLine} */
	public List<UnknownLine> getUnknownLines() {
		return unknownLines_;
	}

	/** keep a line DBGL does not understand: it goes into the matching own section */
	public void keepUnknownLine(UnknownLine line) {
		placeLine(line, true);
	}

	/**
	 * Lines DBGL does not understand that are kept without DBGL's "@REM" marker lines, at the place of the own section (index
	 * 0 to 3), or after "exit" (index 4). Read again, they are unknown lines again.
	 */
	private String[] plainLines_ = emptyPlainLines();
	/** "C:", "cd \\GAME" of a profile without a game (DOSBox ends at its prompt there): left out once it has a game */
	private java.util.Set<String> promptLines_ = new java.util.HashSet<>();
	/** lines given their own drive and folder (see keepWhereTheyRan): {as kept, without the way back to the game} */
	private List<String[]> ranElsewhere_ = new ArrayList<>();

	private static String[] emptyPlainLines() {
		String[] result = new String[SECTIONS + 1];
		Arrays.fill(result, StringUtils.EMPTY);
		return result;
	}

	/**
	 * Keep a line DBGL does not understand.
	 *
	 * @param markers into the own section with DBGL's "@REM" marker lines (shown in the profile dialog), or else without them
	 *                at the same place. A line after "exit" always stays after "exit".
	 */
	public void placeLine(UnknownLine line, boolean markers) {
		if (line.afterExit())
			plainLines_[SECTIONS] += line.text() + SystemUtils.EOLN;
		else if (markers && !startLines_.contains(line.text()))
			customSections_[line.section()] += line.text() + SystemUtils.EOLN;
		else
			plainLines_[line.section()] += line.text() + SystemUtils.EOLN;
	}

	/** take a kept line out again, wherever it was put (see {@link #placeLine(UnknownLine, boolean)}) */
	public void removePlacedLine(UnknownLine line) {
		String l = line.text() + SystemUtils.EOLN;
		for (String[] where: new String[][] {plainLines_, customSections_})
			for (int i = 0; i < where.length; i++) {
				String s = where[i];
				int from = s.startsWith(l) ? 0: s.indexOf(SystemUtils.EOLN + l);
				if (from > 0)
					from += SystemUtils.EOLN.length();
				if (from >= 0) {
					where[i] = s.substring(0, from) + s.substring(from + l.length());
					return;
				}
			}
	}

	/** @return the lines DBGL does not know that are kept without its marker lines, with their section (after "exit": 3, afterExit) */
	public List<UnknownLine> getPlainLines() {
		List<UnknownLine> result = new ArrayList<>();
		for (int i = 0; i < plainLines_.length; i++)
			for (String l: plainLines_[i].split("\\R"))
				if (StringUtils.isNotBlank(l))
					result.add(new UnknownLine(l, Math.min(i, SECTIONS - 1), i == SECTIONS));
		return result;
	}

	/** @return true if the line is kept without DBGL's marker lines */
	public boolean isPlain(UnknownLine line) {
		String l = line.text() + SystemUtils.EOLN;
		return java.util.stream.Stream.of(plainLines_).anyMatch(s -> s.startsWith(l) || s.contains(SystemUtils.EOLN + l));
	}

	private void insertPlainLines(StringBuilder sb, int index) {
		String lines = plainLines_[index];
		if (!promptLines_.isEmpty() && (StringUtils.isNotBlank(main_) || StringUtils.isNotBlank(img_[0]))) // a game now: DBGL goes there itself
			lines = java.util.Arrays.stream(lines.split("\\R")).filter(l -> !promptLines_.contains(l)).map(l -> l + SystemUtils.EOLN).collect(java.util.stream.Collectors.joining());
		if (StringUtils.isNotEmpty(lines))
			sb.append(StringUtils.chomp(lines)).append(SystemUtils.EOLN);
	}

	public Autoexec(Autoexec autoexec) {
		unknownLines_ = new ArrayList<>(autoexec.unknownLines_);
		startLines_ = new java.util.HashSet<>(autoexec.startLines_);
		plainLines_ = Arrays.copyOf(autoexec.plainLines_, autoexec.plainLines_.length);
		promptLines_ = new java.util.HashSet<>(autoexec.promptLines_);
		ranElsewhere_ = new ArrayList<>(autoexec.ranElsewhere_);
		main_ = autoexec.main_;
		mainDosboxLooks_ = autoexec.mainDosboxLooks_;
		startAsRead_ = autoexec.startAsRead_;
		bootAsRead_ = autoexec.bootAsRead_;
		bootAsReadFor_ = autoexec.bootAsReadFor_;
		startAsReadFor_ = autoexec.startAsReadFor_;
		parameters_ = autoexec.parameters_;
		img_ = new String[BOOTER_IMAGES];
		for (int i = 0; i < BOOTER_IMAGES; i++)
			img_[i] = autoexec.img_[i];
		imgDriveletter_ = autoexec.imgDriveletter_;
		mixer_ = autoexec.mixer_;
		keyb_ = autoexec.keyb_;
		ipxnet_ = autoexec.ipxnet_;
		loadfix_ = autoexec.loadfix_;
		loadhigh_ = autoexec.loadhigh_;
		exit_ = autoexec.exit_;
		pause_ = autoexec.pause_;
		booterByDefault_ = autoexec.booterByDefault_;
		loadfixValue_ = autoexec.loadfixValue_;
		customSections_ = Arrays.copyOf(autoexec.customSections_, SECTIONS);
		mountingpoints_ = new ArrayList<>();
	}

	public String getMain() {
		return main_;
	}

	public void setMain(String main) {
		main_ = main;
		mainDosboxLooks_ = null;
	}

	/**
	 * Where DOSBox looks for the program: on a drive the configuration of the DOSBox version mounts already (DOSBox skips the
	 * profile's own line), not where {@link #getMain()} has it (on the profile's own mount). Otherwise the same as getMain().
	 */
	public String getMainWhereDosboxLooks() {
		return mainDosboxLooks_ != null ? mainDosboxLooks_: main_;
	}

	/** see {@link #getMainWhereDosboxLooks()}; null if it is the same as main_ */
	private String mainDosboxLooks_;

	/**
	 * the DOS lines that started the game as read (drive, directory, program), and for which main_. Written as they were when no
	 * mount reaches the game any more (its drive mounted only by the configuration of the DOSBox version, switched to one without
	 * it): before, the lines were left out without a word, and lost when switching back
	 */
	private String[] startAsRead_;
	private String startAsReadFor_;
	/** the BOOT line as read, for these images and drive: written again when DBGL cannot write a DOS path for an image (alpha 46) */
	private String bootAsRead_;
	private String[] bootAsReadFor_;

	private static final java.util.Set<String> ANYWHERE = java.util.Set.of("rem", "cls", "echo", "@echo", "set", "config", "mount", "imgmount", "pause", "exit",
		"keyb", "mixer", "ver", "prompt", "path", "choice", "::");

	/**
	 * A command before the game that ran on another drive or in another folder than the game ("m:", "call sc55.bat", "c:", "cd
	 * game", "game.exe"): DBGL writes the drive and folder of the game first, so it ran there then, and failed (found with
	 * configurations from the internet, alpha 46). It gets its drive and folder again, and the game's after it. Not done for
	 * batch logic (labels, goto, if): what runs where is not clear there.
	 */
	/** "game1/../game2" as "game2": without "..", without "\\" at the start and the end */
	private static String dosFolder(String directory) {
		String n = FilenameUtils.normalize(FilenameUtils.separatorsToUnix(directory), true);
		return StringUtils.strip(FilenameUtils.separatorsToWindows(n == null ? directory: n), "\\");
	}

	private void keepWhereTheyRan(java.util.Map<String, String> contextOf, char mainDrive, String mainDir) {
		String main = mainDrive + "|" + mainDir;
		for (int i = 0; i < unknownLines_.size(); i++) {
			UnknownLine l = unknownLines_.get(i);
			String ctx = contextOf.get(l.text());
			String first = l.text().trim().toLowerCase().split("\\s+")[0];
			if ((l.section() != 1) || (ctx == null) || ctx.equalsIgnoreCase(main) || ctx.startsWith("\0") || ANYWHERE.contains(first) || l.text().contains("\n"))
				continue;
			char drive = ctx.charAt(0);
			String dir = ctx.substring(2);
			StringBuilder b = new StringBuilder();
			if (drive != mainDrive)
				b.append(drive).append(':').append(SystemUtils.EOLN);
			b.append("cd \\").append(dir).append(SystemUtils.EOLN).append(l.text().trim());
			String there = b.toString();
			b.append(SystemUtils.EOLN);
			if (drive != mainDrive)
				b.append(mainDrive).append(':').append(SystemUtils.EOLN);
			b.append("cd \\").append(mainDir);
			unknownLines_.set(i, new UnknownLine(b.toString(), l.section(), l.afterExit()));
			ranElsewhere_.add(new String[] {b.toString(), there, drive + ":"});
		}
	}

	/** the way back after a line that ran elsewhere: to the game as written now (it may have been changed since reading) */
	private String backToTheGame(String text, String[] backToGame) {
		for (String[] r: ranElsewhere_) {
			String now = backToGame == null ? r[1]
				: r[1] + SystemUtils.EOLN + (backToGame[0].equalsIgnoreCase(r[2]) ? "": backToGame[0] + SystemUtils.EOLN) + backToGame[1];
			text = text.replace(r[0], now);
		}
		return text;
	}

	private String[] bootState() {
		String[] r = Arrays.copyOf(img_, BOOTER_IMAGES + 1);
		r[BOOTER_IMAGES] = imgDriveletter_;
		return r;
	}

	/** the BOOT line as read, if the images are unchanged and one of them cannot be written as a DOS path; else null */
	private String bootLineAsRead(List<Mount> mountingpointsForExecutables) {
		if ((bootAsRead_ == null) || !Arrays.equals(bootAsReadFor_, bootState()))
			return null;
		for (int i = 0; i < BOOTER_IMAGES; i++) {
			if (StringUtils.isBlank(img_[i]) || img_[i].equals("file") || hasImageMountMatch(img_[i]))
				continue;
			if (dosPathFor(new FileLocation(img_[i], FileLocationService.getInstance().dosrootRelative()), mountingpointsForExecutables).drive_ == null)
				return bootAsRead_;
		}
		return null;
	}

	public String getParameters() {
		return parameters_;
	}

	public void setParameters(String parameters) {
		parameters_ = parameters;
	}

	public String[] getImgs() {
		return img_;
	}
	
	public String getImg(int idx) {
		return img_[idx];
	}

	public void setImg(int idx, String img1) {
		img_[idx] = img1;
	}

	public String getImgDriveletter() {
		return imgDriveletter_;
	}

	public void setImgDriveletter(String imgDriveletter) {
		imgDriveletter_ = imgDriveletter;
	}

	public String getMixer() {
		return mixer_;
	}

	public void setMixer(String mixer) {
		mixer_ = mixer;
	}

	public String getKeyb() {
		return keyb_;
	}

	public void setKeyb(String keyb) {
		keyb_ = keyb;
	}

	public String getIpxnet() {
		return ipxnet_;
	}

	public void setIpxnet(String ipxnet) {
		ipxnet_ = ipxnet;
	}

	public Boolean isLoadfix() {
		return loadfix_;
	}

	public String getLoadfix() {
		return BooleanUtils.toStringTrueFalse(loadfix_);
	}

	public void setLoadfix(Boolean loadfix) {
		loadfix_ = loadfix;
	}

	public void setLoadfix(String loadfix) {
		if (StringUtils.isNotBlank(loadfix))
			loadfix_ = Boolean.valueOf(loadfix);
		else
			loadfix_ = null;
	}

	public Boolean isLoadhigh() {
		return loadhigh_;
	}

	public String getLoadhigh() {
		return BooleanUtils.toStringTrueFalse(loadhigh_);
	}

	public void setLoadhigh(Boolean loadhigh) {
		loadhigh_ = loadhigh;
	}

	public void setLoadhigh(String loadhigh) {
		if (StringUtils.isNotBlank(loadhigh))
			loadhigh_ = Boolean.valueOf(loadhigh);
		else
			loadhigh_ = null;
	}

	public Boolean isExit() {
		return exit_;
	}

	public String getExit() {
		return BooleanUtils.toStringTrueFalse(exit_);
	}

	public void setExit(Boolean exit) {
		exit_ = exit;
	}

	public void setExit(String exit) {
		if (StringUtils.isNotBlank(exit))
			exit_ = Boolean.valueOf(exit);
		else
			exit_ = null;
	}

	public Boolean getPause() {
		return pause_;
	}

	public void setPause(Boolean pause) {
		pause_ = pause;
	}

	public Boolean getBooterByDefault() {
		return booterByDefault_;
	}

	public void setBooterByDefault(Boolean booterByDefault) {
		booterByDefault_ = booterByDefault;
	}

	public int getLoadfixValue() {
		return loadfixValue_;
	}

	public String getLoadfixValueAsString() {
		return Objects.toString(loadfixValue_, null);
	}

	public void setLoadfixValue(String loadfixValue) {
		try {
			loadfixValue_ = NumberUtils.createInteger(loadfixValue);
		} catch (NumberFormatException e) {
			// ignore invalid values
		}
	}

	public String[] getCustomSections() {
		return customSections_;
	}

	public String getCustomSection(int index) {
		return customSections_[index];
	}

	public void setCustomSections(String[] customSections) {
		customSections_ = customSections;
	}

	public void setCustomSection(int index, String customSection) {
		customSections_[index] = customSection;
	}

	public String[] getSetPathsFromCustomSections() {
		for (String section: customSections_) {
			String[] commands = section.split(SystemUtils.EOLN);
			for (String command: commands) {
				if (command.toLowerCase().startsWith("set path=")) {
					return command.substring(9).trim().split(";");
				} else if (command.toLowerCase().startsWith("path=")) {
					return command.substring(5).trim().split(";");
				}
			}
		}
		return new String[0];
	}

	private void insertCustomSection(StringBuilder sb, int sectionNr) {
		if (StringUtils.isNotEmpty(customSections_[sectionNr])) {
			sb.append(CUSTOM_SECTION_MARKERS[sectionNr * 2 + 0]).append(SystemUtils.EOLN);
			sb.append(StringUtils.chomp(customSections_[sectionNr])).append(SystemUtils.EOLN);
			sb.append(CUSTOM_SECTION_MARKERS[sectionNr * 2 + 1]).append(SystemUtils.EOLN);
		}
		insertPlainLines(sb, sectionNr);
	}

	public List<Mount> getMountingpoints() {
		return mountingpoints_;
	}

	public void setMountingpoints(List<Mount> mountingpoints) {
		mountingpoints_ = mountingpoints;
	}

	/**
	 * Mount lines that are the same as one before them are dropped: DOSBox only says "drive already mounted" for them.
	 *
	 * @return the number of lines dropped
	 */
	public int removeRepeatedMounts() {
		List<Mount> repeated = new ArrayList<>(repeatedMounts());
		repeated.addAll(deadMounts()); // also those that fail with a later one for the drive that works
		mountingpoints_ = new ArrayList<>(mountingpoints_.stream().filter(m -> repeated.stream().noneMatch(r -> r == m)).toList());
		return repeated.size();
	}

	/** a mount DOSBox can do: its folder or all its images are there (a mount that fails leaves the drive free) */
	public static boolean canMount(Mount m) {
		if (m instanceof org.dbgl.model.conf.mount.PhysFsMount)
			return true;
		if (m instanceof ImageMount im)
			return java.util.Arrays.stream(im.getCanonicalImgPaths()).allMatch(File::exists);
		if (m instanceof DirMount dm)
			return dm.getCanonicalPath().exists();
		return true;
	}

	/**
	 * Mount lines that fail (folder or image not there) while a later line for the same drive works: DOSBox takes the later
	 * one, these do nothing (e.g. left after the image was moved and the profile repaired).
	 */
	public List<Mount> deadMounts() {
		List<Mount> result = new ArrayList<>();
		for (int i = 0; i < mountingpoints_.size(); i++) {
			Mount m = mountingpoints_.get(i);
			if (m.isUnmounted() || (m instanceof org.dbgl.model.conf.mount.OverlayMount) || canMount(m))
				continue;
			char drive = Character.toUpperCase(m.getDrive());
			for (int j = i + 1; j < mountingpoints_.size(); j++) {
				Mount later = mountingpoints_.get(j);
				if (Character.toUpperCase(later.getDrive()) != drive || (later instanceof org.dbgl.model.conf.mount.OverlayMount))
					continue;
				if (later.isUnmounted())
					break;
				if (canMount(later)) {
					result.add(m);
					break;
				}
			}
		}
		return result;
	}

	/** mount lines that are the same as one before them for that drive (not unmounted in between): they do nothing */
	public List<Mount> repeatedMounts() {
		return mountsAgain(true);
	}

	/** mount lines for a drive that is mounted already, with something else: DOSBox only uses the first one */
	public List<Mount> mountsOfDrivesMountedAlready() {
		return mountsAgain(false);
	}

	private List<Mount> mountsAgain(boolean same) {
		// per drive: the lines for it since it was last unmounted, and whether one of them worked (the first that can mount
		// counts; one that fails, e.g. its image is not there, leaves the drive free for the next one)
		java.util.Map<Character, java.util.Set<String>> seen = new java.util.HashMap<>();
		java.util.Set<Character> mounted = new java.util.HashSet<>();
		List<Mount> result = new ArrayList<>();
		for (Mount m: mountingpoints_) {
			if (m instanceof org.dbgl.model.conf.mount.OverlayMount)
				continue;
			char drive = Character.toUpperCase(m.getDrive());
			if (m.isUnmounted()) {
				seen.remove(drive);
				mounted.remove(drive);
				continue;
			}
			boolean repeated = !seen.computeIfAbsent(drive, d -> new java.util.HashSet<>()).add(m.toString().toLowerCase());
			if (repeated) {
				if (same)
					result.add(m);
			} else if (mounted.contains(drive)) {
				if (!same)
					result.add(m);
			} else if (canMount(m)) {
				mounted.add(drive);
			}
		}
		return result;
	}

	public long countImageMounts() {
		return mountingpoints_.stream().filter(ImageMount.class::isInstance).count();
	}
	
	public boolean hasCdAudio() {
		return countImageMounts() > 0;
	}

	public File[] findFirstImageMountCanonicalPath() {
		return mountingpoints_.stream().filter(ImageMount.class::isInstance).findFirst().map(x -> ((ImageMount)x).getCanonicalImgPaths()).orElse(new File[0]);
	}

	/**
	 * After a successful boot, DOSBox never returns to the autoexec. If booting fails, this line keeps the message of BOOT on
	 * the screen, instead of 'exit' closing DOSBox right away. Not read back by parse (only 'pause' is the pause option).
	 */
	/** a line put among the lines DBGL does not know at its place (while reading, the section holds the line number) */
	private void insertInOrder(UnknownLine line) {
		int at = 0;
		while ((at < unknownLines_.size()) && (unknownLines_.get(at).afterExit() || (unknownLines_.get(at).section() <= line.section())))
			at++;
		unknownLines_.add(at, line);
	}

	/** DBGL's marker line, not a line of the user that starts like it ("@REM STARTUP", found by review, alpha 46) */
	private static boolean isMarker(String line, String marker) {
		return line.equals(marker) || line.startsWith(marker + " ") || line.startsWith(marker + "\t");
	}

	/** DBGL writes "@pause" after BOOT only with "exit" after it (appendPauseAfterFailedBoot) */
	private static boolean exitFollows(List<String> lines, int lineNo) {
		for (int i = lineNo + 1; i < lines.size(); i++)
			if (StringUtils.isNotBlank(lines.get(i)))
				return lines.get(i).trim().equalsIgnoreCase("exit");
		return false;
	}

	private void appendPauseAfterFailedBoot(StringBuilder result) {
		if (BooleanUtils.isTrue(exit_))
			result.append("@pause").append(SystemUtils.EOLN);
	}

	private boolean hasImageMountMatch(String file) {
		return mountingpoints_.stream().anyMatch(x -> x instanceof ImageMount && ((ImageMount)x).matchesImgPath(file));
	}

	private String findImageMatchByDrive(char driveLetter) {
		return mountingpoints_.stream().filter(x -> (x instanceof ImageMount) && (driveLetter == x.getDrive())).findFirst().map(x -> ((ImageMount)x).getImgPaths()[0].getPath()).orElse(null);
	}

	public void addMount(String mount, File baseDir) {
		tryAddMount(mount, baseDir);
	}

	/** @return false if DBGL cannot read the mount command (e.g. "mount D a.iso b.iso -t cdrom"), so it is not added */
	private boolean tryAddMount(String mount, File baseDir) {
		try {
			Mount mnt = MountFactory.create(mount);
			if (baseDir != null)
				mnt.setBaseDir(baseDir);
			mountingpoints_.add(mnt);
			return true;
		} catch (InvalidMountstringException e) {
			System.err.println("Invalid mount command \"" + mount + "\"");
			return false;
		}
	}

	public void addMount(String mount) {
		tryAddMount(mount, null);
	}

	public boolean isDos() {
		if (StringUtils.isBlank(main_) && StringUtils.isBlank(img_[0]))
			return BooleanUtils.isNotTrue(booterByDefault_);
		return StringUtils.isNotBlank(main_);
	}

	public boolean isBooter() {
		if (StringUtils.isBlank(main_) && StringUtils.isBlank(img_[0]))
			return BooleanUtils.isTrue(booterByDefault_);
		return StringUtils.isNotBlank(img_[0]);
	}

	public String getGameMain() {
		return isDos() ? main_: img_[0];
	}

	public File getCanonicalGameDir() {
		File result = null;
		if (isDos() || isBooter()) {
			File file = new FileLocation(isDos() ? main_: img_[0], FileLocationService.getInstance().dosrootRelative()).getCanonicalFile();
			File main = FilesUtils.determineMainFile(file);
			result = (main != null && !main.equals(file) && main.getParentFile() != null) ? main.getParentFile(): file.getParentFile();
		}
		return result;
	}

	public void setGameMain(String file) {
		if (isDos())
			main_ = file;
		else
			img_[0] = file;
	}

	public boolean isIncomplete(List<Mount> combinedMountingpoints) {
		if (isDos())
			return !canBeReachedUsingMounts(false, main_, combinedMountingpoints);
		else if (isBooter())
			return !canBeReachedUsingMounts(true, img_[0], combinedMountingpoints);
		else
			return true;
	}

	/** @return true if the autoexec that was read uses names DOS can't use (with spaces, or longer than 8.3), which must be rewritten */
	public boolean hasLongNamesWithSpaces() {
		return longNamesWithSpaces_;
	}

	public String getDosFilename(String location, List<Mount> combinedMountingpoints) {
		return new DosPath(new FileLocation(location, FileLocationService.getInstance().dosrootRelative()), combinedMountingpoints).file_;
	}

	public boolean canBeReachedUsingMounts(boolean booter, String location, List<Mount> combinedMountingpoints) {
		return (booter && hasImageMountMatch(location)) || (new DosPath(new FileLocation(location, FileLocationService.getInstance().dosrootRelative()), combinedMountingpoints).drive_ != null);
	}
	
	private static boolean hasDrive(char drive, List<Mount> mountingpoints) {
		return (drive == 'z') || (drive == 'Z') // DOSBox internal drive 
				|| mountingpoints.stream().anyMatch(x -> x.matchesDrive(drive));
	}

	public String load(List<String> lines, List<Mount> dbMounts, File cwd) {
		StringBuilder warningsLog = new StringBuilder();

		File baseDir = (cwd == null) ? FileLocationService.getInstance().getDosroot(): cwd;

		char driveletter = '\0';
		String directory = StringUtils.EMPTY;
		String executable = StringUtils.EMPTY;
		String executableWithSpaces = null;
		longNamesWithSpaces_ = false;
		mainDosboxLooks_ = null;
		startAsRead_ = null;
		bootAsRead_ = null;
		bootAsReadFor_ = null;
		String bootLine = null;
		UnknownLine bootUnknown = null;
		startAsReadFor_ = null;
		String[] image = new String[BOOTER_IMAGES];
		for (int i = 0; i < BOOTER_IMAGES; i++)
			image[i] = StringUtils.EMPTY;
		String[] imageAsWritten = null; // "boot C:\x.img" before its drive was taken off
		int exeIndex = -1;
		StringBuilder customEchos = new StringBuilder();
		List<String> leftOvers = new ArrayList<>();
		List<UnknownLine> leftOverLines = new ArrayList<>();
		List<UnknownLine> echoOffLines = new ArrayList<>();
		java.util.Map<String, String> contextOf = new java.util.HashMap<>(); // the drive and folder a line ran in ("C|\\UTIL")
		boolean controlFlow = false;
		List<String> navigation = new ArrayList<>(); // "C:", "cd \\GAME": written again by DBGL for the game, else kept (below)
		List<UnknownLine> navLines = new ArrayList<>(); // the same, with where they were
		java.util.Map<Character, String> folderOf = new java.util.HashMap<>(); // DOS keeps the folder of each drive
		List<UnknownLine> navAfterGame = new ArrayList<>(); // "cd \\" after the game: not where the game is
		char gameDrive = '\0';
		String gameFolder = null;
		unknownLines_ = new ArrayList<>();
		startLines_ = new java.util.HashSet<>();
		plainLines_ = emptyPlainLines();
		promptLines_ = new java.util.HashSet<>();
		ranElsewhere_ = new ArrayList<>();
		// while reading, the section of an unknown line holds its line number; the real section follows at the end
		int lineNo = -1, firstMountLine = Integer.MAX_VALUE, mainLine = -1;
		boolean afterExit = false;
		UnknownLine lastExeLine = null;
		int customSection = -1;
		// lines to start the game DBGL cannot follow (a drive that is not mounted, "cd" without a drive): written again as
		// they were if DBGL finds no game at all, instead of being dropped on the next save
		List<UnknownLine> unresolvedStart = new ArrayList<>();

		line: for (String orgLine: lines) {
			orgLine = orgLine.trim();
			String rawLine = orgLine;
			lineNo++;
			if (afterExit) {
				if (StringUtils.isNotBlank(orgLine))
					unknownLines_.add(new UnknownLine(orgLine, 3, true));
				continue;
			}
			int here = lineNo;

			for (int i = 0; i < SECTIONS; i++) {
				if (isMarker(orgLine, CUSTOM_SECTION_MARKERS[i * 2])) {
					customSection = i;
					continue line;
				}
			}
			if (customSection > -1) {
				if (isMarker(orgLine, CUSTOM_SECTION_MARKERS[customSection * 2 + 1]))
					customSection = -1;
				else
					customSections_[customSection] += orgLine + SystemUtils.EOLN;
				continue;
			}

			orgLine = Strings.CI.removeEnd(Strings.CI.removeEnd(Strings.CS.removeStart(orgLine, "@"), ">null"), ">nul").trim();

			Matcher loadhighMatcher = LOADHIGH_PATRN.matcher(orgLine);
			Matcher loadfixMatcher = LOADFIX_PATRN.matcher(orgLine);
			Matcher bootMatcher = BOOT_PATRN.matcher(orgLine);

			if (loadhighMatcher.matches()) {
				loadhigh_ = true;
				orgLine = loadhighMatcher.group(1).trim();
			}

			if (loadfixMatcher.matches()) {
				if (BooleanUtils.isNotTrue(loadfix_)) {
					loadfixValue_ = DEFAULT_LOADFIX_VALUE;
					loadfix_ = true;
				}
				if (loadfixMatcher.group(1) != null) // -f
					continue;
				if (loadfixMatcher.group(2) != null) {
					try {
						loadfixValue_ = Integer.parseInt(loadfixMatcher.group(2).trim().substring(1));
					} catch (NumberFormatException e) {
						// use default value of 64
					}
				}
				if (loadfixMatcher.group(3) == null)
					continue;
				orgLine = loadfixMatcher.group(3).trim();
			}

			String line = orgLine.toLowerCase();
			String context = driveletter + "|" + dosFolder(directory);
			if (contextOf.containsKey(rawLine) && !context.equalsIgnoreCase(contextOf.get(rawLine)))
				contextOf.put(rawLine, null); // the same line in two places: not clear which
			else
				contextOf.putIfAbsent(rawLine, context);
			if (line.startsWith(":") || line.startsWith("goto") || line.startsWith("if ") || line.startsWith("call :") || line.startsWith("for "))
				controlFlow = true;

			if (line.startsWith("mount ") || line.startsWith("imgmount ")) {
				firstMountLine = Math.min(firstMountLine, lineNo);
				// a mount command DBGL cannot read is not written again: keep it as a line DBGL does not know
				if (!tryAddMount(orgLine, cwd == null ? null: baseDir))
					unknownLines_.add(new UnknownLine(rawLine, here, false));
			} else if ((line.endsWith(":") && line.length() == 2) || (line.endsWith(":\\") && line.length() == 3)) {
				if (driveletter != Character.toUpperCase(line.charAt(0))) {
					// drive letter change
					List<Mount> mountingpoints = dbMounts != null 
							? Stream.concat(dbMounts.stream(), mountingpoints_.stream()).toList()
							: mountingpoints_;
					if (hasDrive(line.charAt(0), mountingpoints)) {
						firstMountLine = Math.min(firstMountLine, lineNo);
						if (driveletter != '\0')
							folderOf.put(driveletter, directory);
						driveletter = Character.toUpperCase(line.charAt(0));
						// back on a drive: in its folder, as DOS does it (before alpha 46 in its root; found by review)
						directory = folderOf.getOrDefault(driveletter, StringUtils.EMPTY);
						navigation.add(rawLine);
						navLines.add(new UnknownLine(rawLine, here, false));
						if (lastExeLine != null)
							navAfterGame.add(new UnknownLine(rawLine, here, false));
					} else {
						warningsLog.append("autoexec: invalid drive change\n");
						unresolvedStart.add(new UnknownLine(rawLine, here, false));
						driveletter = '\0'; // the "cd" lines that follow are for that drive
					}
				}
			} else if ((line.startsWith("cd\\") || line.startsWith("cd ") || line.startsWith("cd.")) && (driveletter == '\0')) {
				unresolvedStart.add(new UnknownLine(rawLine, here, false));
			} else if (line.startsWith("cd\\")) {
				if (driveletter != '\0') {
					directory = FilenameUtils.separatorsToSystem(orgLine).substring(2);
					navigation.add(rawLine);
					navLines.add(new UnknownLine(rawLine, here, false));
					if (lastExeLine != null)
						navAfterGame.add(new UnknownLine(rawLine, here, false));
				}
			} else if (line.startsWith("cd ")) {
				if (driveletter != '\0') {
					navigation.add(rawLine);
					navLines.add(new UnknownLine(rawLine, here, false));
					if (lastExeLine != null)
						navAfterGame.add(new UnknownLine(rawLine, here, false));
					String add = FilenameUtils.separatorsToSystem(orgLine).substring(3);
					if (add.startsWith(File.separator))
						directory = add;
					else
						directory = new File(directory, add).getPath();
				}
			} else if (line.startsWith("cd.")) {
				if (driveletter != '\0') {
					if (lastExeLine != null)
						navAfterGame.add(new UnknownLine(rawLine, here, false));
					String add = FilenameUtils.separatorsToSystem(orgLine).substring(2);
					if (!directory.equals(StringUtils.EMPTY) || !add.equals(".."))
						directory = new File(directory, add).getPath();
				}
			} else if (line.startsWith("keyb ") || line.startsWith("keyb.com ")) {
				keyb_ = orgLine.substring(line.indexOf(' ') + 1);
			} else if (line.startsWith("mixer ") || line.startsWith("mixer.com ")) {
				mixer_ = orgLine.substring(line.indexOf(' ') + 1);
			} else if (line.startsWith("ipxnet ") || line.startsWith("ipxnet.com ")) {
				ipxnet_ = orgLine.substring(line.indexOf(' ') + 1);
			} else if (line.startsWith("loadrom ") || line.startsWith("loadrom.com ")) {
				customSections_[1] += orgLine + SystemUtils.EOLN; // as written, on its own line (before, in lower case and glued to the next one)
			} else if (line.equals("pause") && rawLine.trim().equalsIgnoreCase("@pause") && (StringUtils.isNotBlank(image[0]) || StringUtils.isNotBlank(img_[0]) || StringUtils.isNotBlank(imgDriveletter_))
				&& exitFollows(lines, lineNo)) {
				// DBGL's own line after BOOT (appendPauseAfterFailedBoot): written again by it, not the pause option (else one more
				// "pause" was added with each saving)
			} else if (line.equals("pause")) {
				pause_ = true;
			} else if (line.startsWith("z:\\config.com")) {
				unknownLines_.add(new UnknownLine(rawLine, here, false));
			} else if ((exeIndex = StringUtils.indexOfAny(line, FilesUtils.dosExecutables())) != -1) {
				// only the last program is the game, an earlier one (e.g. a mouse driver) is not written again; the last one is
				// taken out of the list again below
				lastExeLine = new UnknownLine(rawLine, lineNo, false);
				unknownLines_.add(lastExeLine);
				mainLine = lineNo;
				gameDrive = driveletter;
				gameFolder = directory;
				navAfterGame.clear();
				executable = orgLine;
				// the parameters of this line only (before alpha 46 those of an earlier program stayed: "DEVICE.COM X.SYS" and then
				// "SMARTDRV.EXE" gave "SMARTDRV.EXE X.SYS"; Ronald's 0.100 does so too)
				parameters_ = StringUtils.EMPTY;
				// older DBGL versions wrote long names with spaces, e.g. "MM 3.EXE", keep the complete name as an alternative
				String full = orgLine.substring(0, Math.min(orgLine.length(), exeIndex + 4)).trim();
				if (full.toLowerCase().startsWith("call "))
					full = full.substring(5).trim();
				executableWithSpaces = full.indexOf(' ') != -1 ? full: null;

				// D-Fend Reloaded special cases
				if (executable.startsWith(DFR_4DOS_PREFIX))
					executable = executable.substring(DFR_4DOS_PREFIX.length());
				if (executable.startsWith(DFR_DOS32A_PREFIX))
					executable = executable.substring(DFR_DOS32A_PREFIX.length());

				// If there is a space BEFORE executable name, strip everything before it
				int spaceBeforeIndex = executable.lastIndexOf(' ', exeIndex);
				if (spaceBeforeIndex != -1) {
					executable = executable.substring(spaceBeforeIndex + 1);
				}
				// If there is a space AFTER executable name, take that part as parameters
				int spaceAfterIndex = executable.indexOf(' ');
				if (spaceAfterIndex != -1) {
					parameters_ = orgLine.substring(spaceBeforeIndex + spaceAfterIndex + 2);
					executable = executable.substring(0, spaceAfterIndex);
				}
			} else if (bootMatcher.matches()) {
				mainLine = lineNo;
				bootLine = orgLine.trim();
				bootUnknown = new UnknownLine(rawLine, here, false);
				Matcher bootImgsMatcher = BOOTIMGS_PATRN.matcher(bootMatcher.group(1).trim());
				if (bootImgsMatcher.matches()) {
					for (int i = 0; i < BOOTER_IMAGES; i++) {
						if (bootImgsMatcher.group(i + 1) != null)
							image[i] = FilenameUtils.separatorsToSystem(StringUtils.strip(bootImgsMatcher.group(i + 1), "\""));
					}
				}
				if (bootMatcher.group(2) != null)
					imgDriveletter_ = bootMatcher.group(2).trim().toUpperCase();
				// "boot c:" (DOSBox-X: boot the drive) is no image named "c:": as "boot -l c" (else "imgmount a "c:"" was written)
				if ((image[0] != null) && image[0].matches("^[a-zA-Z]:$") && StringUtils.isEmpty(image[1]) && (bootMatcher.group(2) == null)) {
					imgDriveletter_ = image[0].substring(0, 1).toUpperCase();
					image[0] = StringUtils.EMPTY;
				}
				if (StringUtils.isEmpty(image[0]) && StringUtils.isNotEmpty(imgDriveletter_)) {
					String matchingImageMount = findImageMatchByDrive(imgDriveletter_.charAt(0));
					if (matchingImageMount != null) {
						image[0] = matchingImageMount;
					} else {
						char driveNumber = (char)(imgDriveletter_.charAt(0) - 17);
						matchingImageMount = findImageMatchByDrive(driveNumber);
						if (matchingImageMount != null)
							image[0] = matchingImageMount;
					}
				}
				if ((File.separator + "file").equals(image[0])) {
					img_[0] = "file"; // for template if . was unavailable
				}
			} else if (line.equals("exit") || line.startsWith("exit ")) {
				exit_ = true;
				afterExit = true; // no further parsing of lines as DOSBox must have exited
			} else if (line.equals("echo off")) {
				echoOffLines.add(new UnknownLine(rawLine, here, false));
			} else if (line.equals("echo") || line.startsWith("echo ") || line.startsWith("echo.")) {
				customEchos.append(orgLine).append(SystemUtils.EOLN);
			} else if (line.startsWith(":") || line.equals("cd") || line.equals("cls") || line.startsWith("cls ") || line.startsWith("cls\\") || line.equals("rem") || line.startsWith("rem ")
					|| line.startsWith("goto ") || line.startsWith("if errorlevel ")) {
				unknownLines_.add(new UnknownLine(rawLine, here, false));
			} else if (StringUtils.isNotBlank(line)) {
				leftOvers.add(orgLine);
				UnknownLine leftOver = new UnknownLine(rawLine, here, false);
				leftOverLines.add(leftOver);
				unknownLines_.add(leftOver);
			}
		}

		if (StringUtils.isNotEmpty(customEchos) && !customEchos.toString().equalsIgnoreCase("echo." + SystemUtils.EOLN)) {
			customSections_[1] += "@echo off" + SystemUtils.EOLN + customEchos; // add echo commands to pre-launch custom section
			customSections_[1] += "pause" + SystemUtils.EOLN; // add pause statement to make it all readable
		} else {
			unknownLines_.addAll(echoOffLines);
		}

		// a program and a BOOT line: the last one starts (as before), the other one is kept as it was written (before alpha 46
		// it was gone; found by random tests)
		if (!executable.isEmpty() && (bootUnknown != null)) {
			if ((lastExeLine != null) && (mainLine == lastExeLine.section())) {
				insertInOrder(bootUnknown);
				for (int i = 0; i < BOOTER_IMAGES; i++)
					image[i] = StringUtils.EMPTY;
				imgDriveletter_ = StringUtils.EMPTY;
				bootLine = null;
			} else if (StringUtils.isNotBlank(image[0]) || StringUtils.isNotBlank(imgDriveletter_)) {
				int exeAt = lastExeLine != null ? lastExeLine.section(): Integer.MAX_VALUE;
				for (UnknownLine nav: navLines) // the drive and folders it was started in stay as well
					if (nav.section() < exeAt)
						insertInOrder(nav);
				executable = StringUtils.EMPTY;
				executableWithSpaces = null;
				parameters_ = StringUtils.EMPTY;
				lastExeLine = null; // stays among the lines DBGL does not know
				navAfterGame.clear();
			}
		}

		// "cd \\" (or a drive) after the game: the game stays where it was started, the lines stay after it (before alpha 46 the
		// game was looked for there; found by review)
		if ((lastExeLine != null) && !navAfterGame.isEmpty() && (mainLine == lastExeLine.section())) {
			driveletter = gameDrive;
			directory = gameFolder;
			unknownLines_.addAll(navAfterGame);
		}

		java.util.Set<UnknownLine> written = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		if (lastExeLine != null)
			written.add(lastExeLine); // the game
		if (executable.equals(StringUtils.EMPTY) && image[0].equals(StringUtils.EMPTY))
			written.addAll(leftOverLines); // without a game or booter, these lines become the game and its preparation (below)
		unknownLines_.removeIf(written::contains);
		int mainAt = mainLine >= 0 ? mainLine: (leftOverLines.isEmpty() ? Integer.MAX_VALUE: leftOverLines.get(leftOverLines.size() - 1).section());
		int mountAt = firstMountLine;
		unknownLines_.replaceAll(l -> l.afterExit() ? l: new UnknownLine(l.text(), l.section() < mountAt ? 0: (l.section() < mainAt ? 1: 2), false));
		if (StringUtils.isNotEmpty(customEchos) && customEchos.toString().equalsIgnoreCase("echo." + SystemUtils.EOLN))
			unknownLines_.add(new UnknownLine(customEchos.toString().trim(), 1, false));
		unknownLines_.sort(java.util.Comparator.comparingInt(UnknownLine::section)); // stable: keeps the order within a section

		String preLaunchAsRead = customSections_[1];
		if (executable.equals(StringUtils.EMPTY) && image[0].equals(StringUtils.EMPTY) && !leftOvers.isEmpty()) {
			// add all-but-the-last statement to pre-launch custom section
			for (int i = 0; i < leftOvers.size() - 1; i++)
				customSections_[1] += leftOvers.get(i).trim() + SystemUtils.EOLN;

			// the last statement should be the main game
			executable = leftOvers.get(leftOvers.size() - 1).trim();
			boolean isCalledBatch = executable.toLowerCase().startsWith("call ");
			if (isCalledBatch)
				executable = executable.substring(5);
			int spaceAfterIndex = executable.indexOf(' ');
			if (spaceAfterIndex != -1) {
				parameters_ = executable.substring(spaceAfterIndex + 1);
				executable = executable.substring(0, spaceAfterIndex);
			}
			executable += isCalledBatch ? FilesUtils.dosExecutables()[2]: FilesUtils.dosExecutables()[0];
		}

		if (executable.length() > 2 && executable.charAt(1) == ':') {
			driveletter = executable.charAt(0);
			executable = executable.substring(2);
		} else if (image[0].length() > 2 && image[0].charAt(1) == ':' && !new File(image[0]).isFile()) {
			// a DOS path like A:\GAME.IMG; a host path like C:\Users\...\win95.hdi is an image file on the host, keep it
			imageAsWritten = image.clone();
			driveletter = image[0].charAt(0);
			image[0] = image[0].substring(2);
			for (int i = 1; i < BOOTER_IMAGES; i++) {
				if (image[i].length() > 2 && image[i].charAt(1) == ':')
					image[i] = image[i].substring(2);
			}
		}

		List<Mount> mountingpointsForExecutables = dbMounts != null ? Stream.concat(dbMounts.stream(), mountingpoints_.stream()).toList(): mountingpoints_;
		// the mount DOSBox uses for each drive: the first one (it refuses a drive mounted already), the next one after
		// "mount -u"; an overlay only takes what is written, the game is in the drive below it. The last one was taken before.
		// a mount that fails (folder or image not there) leaves the drive to the next one; if none works, the first is taken
		// (so that the missing path is told)
		java.util.Map<Character, Mount> inEffect = new java.util.HashMap<>(), firstOfDrive = new java.util.HashMap<>();
		for (Mount mount: mountingpointsForExecutables) {
			char drive = Character.toUpperCase(mount.getDrive());
			if (mount instanceof org.dbgl.model.conf.mount.OverlayMount)
				continue;
			if (mount.isUnmounted()) {
				inEffect.remove(drive);
				firstOfDrive.remove(drive);
			} else {
				firstOfDrive.putIfAbsent(drive, mount);
				if (canMount(mount))
					inEffect.putIfAbsent(drive, mount);
			}
		}
		firstOfDrive.forEach(inEffect::putIfAbsent);
		for (Mount mount: mountingpointsForExecutables) {
			boolean usedForDrive = inEffect.get(Character.toUpperCase(mount.getDrive())) == mount;
			if (mount instanceof DirMount) {
				if (StringUtils.isNotBlank(executable) && mount.matchesDrive(driveletter) && usedForDrive) {
					main_ = constructPath(((DirMount)mount).getPath(), directory, executable);
					if (!new File(main_).exists() && (executableWithSpaces != null)) {
						String alternative = constructPath(((DirMount)mount).getPath(), directory, executableWithSpaces);
						if (new File(alternative).exists())
							main_ = alternative;
					}
					// also a name longer than 8.3 (e.g. "cd \\DEATHGATE"): DOSBox does not find it, only the short name (DEATHG~1)
					longNamesWithSpaces_ = (executableWithSpaces != null) || directory.contains(" ")
						|| Arrays.stream((directory + "\\" + executable).split("[\\\\/]+")).anyMatch(c -> !c.isEmpty() && org.dbgl.model.conf.DosboxShortNames.needsShortName(c));
				}
				for (int i = 0; i < BOOTER_IMAGES; i++) {
					if (StringUtils.isNotBlank(image[i]) && mount.matchesDrive(driveletter))
						img_[i] = constructPath(((DirMount)mount).getPath(), directory, image[i]);
				}
			} else if (mount instanceof ImageMount) {
				if (StringUtils.isNotBlank(executable) && mount.matchesDrive(driveletter) && usedForDrive)
					main_ = constructPath(((ImageMount)mount).getImgPaths()[0], directory, executable);
				for (int i = 0; i < BOOTER_IMAGES; i++) {
					if (StringUtils.isNotBlank(image[i]) && ((ImageMount)mount).matchesImgPath(image[i]))
						img_[i] = image[i];
				}
			}
		}

		// the drive of the game taken by a mount of the configuration of the DOSBox version (e.g. its own "mount c"): the game is
		// still the one of the profile's own mount. Without it, the lines that start the game were dropped on the next save
		// (switching the DOSBox version to such a one and back lost them); "Check profiles" tells that the drive is taken.
		// The program found on a drive of the DOSBox configuration that is not there, but on the profile's own mount: that one.
		if (StringUtils.isNotBlank(executable) && (StringUtils.isBlank(main_) || !new File(main_).exists())) {
			String own = null, ownExisting = null;
			for (Mount mount: mountingpoints_) {
				if (mount.isUnmounted() || !mount.matchesDrive(driveletter))
					continue;
				String candidate = mount instanceof DirMount dm ? constructPath(dm.getPath(), directory, executable)
						: mount instanceof ImageMount im ? constructPath(im.getImgPaths()[0], directory, executable): null;
				if (candidate == null)
					continue;
				if (own == null)
					own = candidate;
				if ((ownExisting == null) && new File(candidate).exists())
					ownExisting = candidate;
			}
			// not there either (e.g. a drive not plugged in): still the profile's own, so that its lines are written as they were
			String chosen = ownExisting != null ? ownExisting: own;
			if (chosen != null) {
				if (StringUtils.isNotBlank(main_) && !main_.equals(chosen))
					mainDosboxLooks_ = main_; // told by "Check profiles" (the drive is taken by the DOSBox configuration)
				main_ = chosen;
			}
		}

		// the DOS lines as read, for the program finally chosen (also when it was taken from the profile's own mount above: else
		// the lines were dropped when no mount reaches it, e.g. "imgmount D x.img" missing and "mount D ." after it; alpha 46)
		if (StringUtils.isNotBlank(main_) && (driveletter != '\0') && StringUtils.isNotBlank(executable)) {
			startAsRead_ = new String[] {String.valueOf(Character.toUpperCase(driveletter)), FilenameUtils.separatorsToWindows(directory), executable};
			startAsReadFor_ = main_;
		}
		if (StringUtils.isNotBlank(main_) && (driveletter != '\0') && StringUtils.isNotBlank(executable) && !controlFlow)
			keepWhereTheyRan(contextOf, Character.toUpperCase(driveletter), dosFolder(directory));
		if ((bootLine != null) && StringUtils.isNotBlank(img_[0])) {
			bootAsRead_ = bootLine;
			bootAsReadFor_ = bootState();
		}

		boolean noImages = java.util.Arrays.stream(image).allMatch(StringUtils::isBlank);
		if (StringUtils.isBlank(main_) && StringUtils.isBlank(img_[0]) && noImages && (StringUtils.isNotBlank(executable) || !unresolvedStart.isEmpty())) {
			// no game found: the lines that should start it stay, as they were (after the mounts)
			List<UnknownLine> keep = new ArrayList<>(unresolvedStart);
			if (lastExeLine != null)
				keep.add(lastExeLine);
			else
				keep.addAll(leftOverLines);
			keep.sort(java.util.Comparator.comparingInt(UnknownLine::section));
			for (UnknownLine l: keep) {
				if (unknownLines_.stream().noneMatch(u -> u == l))
					unknownLines_.add(new UnknownLine(l.text(), 1, false));
				startLines_.add(l.text());
			}
			if (lastExeLine == null)
				customSections_[1] = preLaunchAsRead; // the lines before it were put there as the game's preparation: kept above
			parameters_ = StringUtils.EMPTY;
		}

		if (StringUtils.isBlank(main_) && StringUtils.isBlank(img_[0]) && noImages && StringUtils.isBlank(executable) && leftOverLines.isEmpty() && unresolvedStart.isEmpty()
			&& !navigation.isEmpty()) {
			// no game, only "C:" (and "cd \\GAME"): DOSBox ends up there, at its prompt. Kept (before alpha 46 these lines were gone,
			// and DOSBox stayed on Z:)
			for (String nav: navigation) {
				unknownLines_.add(new UnknownLine(nav, 1, false));
				promptLines_.add(nav);
				startLines_.add(nav); // as plain lines, without DBGL's marker lines
			}
		}

		if (StringUtils.isBlank(main_) && StringUtils.isBlank(img_[0])) {
			// "boot C:\x.img" and no drive C: the path of the host as written (the file not there now, e.g. an unplugged disk),
			// not "\x.img" (found by review, alpha 46)
			// not mounted as "imgmount a" either: DOSBox does not find it like that, and the line is written as it was read
			boolean unmountedDrive = (imageAsWritten != null) && !hasDrive(imageAsWritten[0].charAt(0), mountingpoints_);
			if (unmountedDrive)
				image = imageAsWritten;
			for (int i = 0; i < BOOTER_IMAGES; i++) {
				if (unmountedDrive) {
					img_[i] = image[i];
				} else if (StringUtils.isNotBlank(image[i])) {
					// a self-booting PC-98 hard disk image is attached as first hard disk, see MountFactory
					String mount = (i == 0) && FilesUtils.isPc98HdImage(image[i]) ? "imgmount 2 \"" + image[i] + "\" -t hdd": "imgmount a \"" + image[i] + "\"";
					if (cwd == null)
						addMount(mount);
					else
						addMount(mount, baseDir);
					img_[i] = image[i];
				}
			}
		}

		if ((bootLine != null) && (bootAsRead_ == null) && StringUtils.isNotBlank(img_[0])) { // the images taken above
			bootAsRead_ = bootLine;
			bootAsReadFor_ = bootState();
		}

		if (exit_ == null)
			exit_ = false;

		return warningsLog.toString();
	}

	private static String constructPath(File mountDir, String dosPath, String executable) {
		if (executable.contains(":"))
			executable = executable.substring(executable.indexOf(':') + 1);
		
		String normalizedPath;
		try {
			normalizedPath = Paths.get(mountDir.getPath(), dosPath, executable).normalize().toString();
		} catch (java.nio.file.InvalidPathException e) {
			// a name the system cannot write in its charset (e.g. "Größte Hits" with LANG=C on Linux): as text, instead of an error
			// that stopped reading the profile (found by the tests of alpha 46)
			normalizedPath = FilenameUtils.normalize(new File(new File(mountDir.getPath(), dosPath), executable).getPath());
			if (normalizedPath == null)
				normalizedPath = new File(new File(mountDir.getPath(), dosPath), executable).getPath();
		}
		FileLocation location = new FileLocation(normalizedPath, FileLocationService.getInstance().dosrootRelative());
		if (!location.getFile().exists()) {
			// the autoexec may use DOS short names like M&M3~1\MM3~1.EXE, find the host names
			FileLocation mountLocation = new FileLocation(mountDir.getPath(), FileLocationService.getInstance().dosrootRelative());
			File host = DosboxShortNames.toHostPath(mountLocation.getCanonicalFile(), dosPath + "\\" + executable);
			if (host.exists()) {
				File relative = FilesUtils.makeRelativeTo(mountLocation.getCanonicalFile(), host);
				return new FileLocation(Paths.get(mountDir.getPath(), relative.getPath()).normalize().toString(), FileLocationService.getInstance().dosrootRelative()).getFile().getPath();
			}
		}
		return location.getFile().getPath();
	}

	public void setGameMainPath(File mainParentFile) {
		if (isDos()) {
			main_ = new File(mainParentFile, new File(main_).getName()).getPath();
		} else if (isBooter()) {
			img_[0] = new File(mainParentFile, new File(img_[0]).getName()).getPath();
			for (int i = 1; i < BOOTER_IMAGES; i++) {
				if (StringUtils.isNotEmpty(img_[i]))
					img_[i] = new File(mainParentFile, new File(img_[i]).getName()).getPath();
			}
		}
	}

	public void setBaseDir(File baseDir) {
		for (Mount mount: mountingpoints_) {
			mount.setBaseDir(baseDir);
		}
		if (isDos()) {
			setMain(FilesUtils.concat(baseDir, main_));
		} else if (isBooter()) {
			setImg(0, FilesUtils.concat(baseDir, img_[0]));
			for (int i = 1; i < BOOTER_IMAGES; i++) {
				if (StringUtils.isNotEmpty(img_[i]))
					setImg(i, FilesUtils.concat(baseDir, img_[i]));
			}
		}
	}

	public void migrate(FileLocation fromPath, FileLocation toPath) {
		for (Mount mount: mountingpoints_) {
			mount.migrate(fromPath, toPath);
		}

		if (isDos()) {
			FileLocation main = new FileLocation(main_, FileLocationService.getInstance().dosrootRelative());
			String newMainString = FilesUtils.migrate(main, fromPath, toPath).getFile().getPath();
			if (canBeReachedUsingMounts(false, newMainString, mountingpoints_))
				main_ = newMainString;
		} else {
			FileLocation img = new FileLocation(img_[0], FileLocationService.getInstance().dosrootRelative());
			String newImgString = FilesUtils.migrate(img, fromPath, toPath).getFile().getPath();
			if (canBeReachedUsingMounts(true, newImgString, mountingpoints_))
				img_[0] = newImgString;
			for (int i = 1; i < BOOTER_IMAGES; i++) {
				if (StringUtils.isNotEmpty(img_[i])) {
					img = new FileLocation(img_[i], FileLocationService.getInstance().dosrootRelative());
					newImgString = FilesUtils.migrate(img, fromPath, toPath).getFile().getPath();
					if (canBeReachedUsingMounts(true, newImgString, mountingpoints_))
						img_[i] = newImgString;
				}
			}
		}
	}

	public void removeUnequalValuesIn(Autoexec autoexec) {
		if (!Strings.CS.equals(getMixer(), autoexec.getMixer()))
			setMixer(null);
		if (!Strings.CS.equals(getKeyb(), autoexec.getKeyb()))
			setKeyb(null);
		if (!Strings.CS.equals(getIpxnet(), autoexec.getIpxnet()))
			setIpxnet(null);
		if (!Strings.CS.equals(getLoadfix(), autoexec.getLoadfix()))
			setLoadfix((String)null);
		if (!Strings.CS.equals(getLoadhigh(), autoexec.getLoadhigh()))
			setLoadhigh((String)null);
		if (!Strings.CS.equals(getExit(), autoexec.getExit()))
			setExit((String)null);
		if (!Strings.CS.equals(getLoadfixValueAsString(), autoexec.getLoadfixValueAsString()))
			setLoadfixValue(null);
		for (int i = 0; i < Autoexec.SECTIONS; i++) {
			if (!Strings.CS.equals(getCustomSection(i), autoexec.getCustomSection(i)))
				setCustomSection(i, null);
		}
	}

	public void removeUnnecessaryMounts(Autoexec autoexec) {
		mountingpoints_ = mountingpoints_.stream().filter(x -> !x.isUnmounted() && autoexec.mountingpoints_.stream().noneMatch(y -> y.getDrive() == x.getDrive())).collect(Collectors.toList());
	}

	@Override
	public String toString() {
		return toString(false, null);
	}

	/**
	 * Where DOS finds the file, through the drives that count. If none of them holds it, as when the profile mounts a drive the
	 * configuration of the DOSBox version mounts already (DOSBox keeps that one), through the profile's own mounts: the lines
	 * to start it stay as they were. Without that, saving the profile dropped them.
	 */
	private DosPath dosPathFor(FileLocation file, List<Mount> mountingpoints) {
		DosPath result = new DosPath(file, mountingpoints);
		if ((result.drive_ == null) && (mountingpoints != mountingpoints_)) {
			DosPath own = new DosPath(file, mountingpoints_);
			if (own.drive_ != null)
				return own;
		}
		return result;
	}

	public String toString(boolean prepareOnly, List<Mount> combinedMountingpoints) {
		StringBuilder result = new StringBuilder();

		List<Mount> mountingpointsForExecutables = combinedMountingpoints != null ? combinedMountingpoints: mountingpoints_;
		String[] backToGame = null; // the drive and folder of the game as written now

		insertCustomSection(result, 0);
		if (StringUtils.isNotBlank(keyb_)) {
			result.append("keyb.com ").append(keyb_).append(SystemUtils.EOLN);
		}
		if (StringUtils.isNotBlank(ipxnet_)) {
			result.append("ipxnet.com ").append(ipxnet_).append(SystemUtils.EOLN);
		}
		for (Mount mount: mountingpoints_) {
			result.append(mount.toString()).append(SystemUtils.EOLN);
		}
		if (StringUtils.isNotBlank(mixer_)) {
			result.append("mixer.com ").append(mixer_).append(SystemUtils.EOLN);
		}

		if (StringUtils.isNotBlank(main_)) {

			FileLocation main = new FileLocation(main_, FileLocationService.getInstance().dosrootRelative());
			DosPath dosLocation = dosPathFor(main, mountingpointsForExecutables);
			if ((dosLocation.drive_ == null) && (startAsRead_ != null) && main_.equals(startAsReadFor_)) {
				// no mount reaches the game (e.g. its drive is mounted only by the configuration of another DOSBox version): the lines
				// stay as they were read, "Check profiles" tells that the game is not found
				dosLocation.drive_ = startAsRead_[0] + ":";
				dosLocation.directory_ = StringUtils.removeStart(startAsRead_[1], "\\");
				dosLocation.file_ = startAsRead_[2];
			}
			if (dosLocation.drive_ != null) {
				backToGame = new String[] {dosLocation.drive_, "cd \\" + dosLocation.directory_};
				result.append(dosLocation.drive_ + SystemUtils.EOLN);
				result.append("cd \\" + dosLocation.directory_ + SystemUtils.EOLN);
				if (BooleanUtils.isTrue(loadfix_)) {
					result.append("loadfix -").append(loadfixValue_ != null && loadfixValue_ > 0 ? loadfixValue_: DEFAULT_LOADFIX_VALUE).append(SystemUtils.EOLN);
				}
				insertCustomSection(result, 1);
				if (!prepareOnly) {
					if (BooleanUtils.isTrue(loadhigh_)) {
						result.append("loadhigh ");
					}
					if (main_.toLowerCase().endsWith(FilesUtils.dosExecutables()[2])) {
						result.append("call ");
					}
					result.append(dosLocation.file_);
					if (StringUtils.isNotBlank(parameters_)) {
						result.append(' ').append(parameters_);
					}
					result.append(SystemUtils.EOLN);
					insertCustomSection(result, 2);
				}
			}

		} else if (StringUtils.isNotBlank(img_[0])) {

			if (BooleanUtils.isTrue(loadfix_)) {
				result.append("loadfix -").append(loadfixValue_ > 0 ? loadfixValue_: DEFAULT_LOADFIX_VALUE).append(SystemUtils.EOLN);
			}
			insertCustomSection(result, 1);
			// an image attached by number (imgmount 2 x.img -t hdd = C:, 0 = A:) is booted by its drive: "boot -l C". Booting the file
			// again would attach it a second time, without its -size (e.g. Windows 95 on a hard disk image); PC-98 .HDI the same
			Mount hdiMount = mountingpoints_.stream().filter(x -> (x instanceof ImageMount im) && im.matchesImgPath(img_[0]) && (x.getDrive() >= '0') && (x.getDrive() <= '3')).findFirst().orElse(null);
			if (!prepareOnly && (hdiMount != null)) {
				// an image attached by number (imgmount 0 = A:, 2 = C:, 3 = D:), e.g. a PC-98 .HDI or a Windows 95 hard disk
				result.append("boot -l ").append((char)(hdiMount.getDrive() + 17)).append(SystemUtils.EOLN);
				appendPauseAfterFailedBoot(result);
				insertCustomSection(result, 2);
			} else if (!prepareOnly && (bootLineAsRead(mountingpointsForExecutables) != null)) {
				// an image DBGL cannot give a DOS path for (e.g. "boot C:\\x.img" of Windows, read on Linux): the line as it was, else
				// only "boot" was written and the image was gone (found with DBConfWS, alpha 46)
				result.append(bootLineAsRead(mountingpointsForExecutables)).append(SystemUtils.EOLN);
				appendPauseAfterFailedBoot(result);
				insertCustomSection(result, 2);
			} else if (!prepareOnly) {
				result.append("boot");

				if (img_[0].equals("file")) { // booter template
					result.append(" \\file");
				} else if (hasImageMountMatch(img_[0])) {
					result.append(" \"").append(img_[0]).append("\"");
				} else {
					FileLocation img1 = new FileLocation(img_[0], FileLocationService.getInstance().dosrootRelative());
					DosPath dosLocation = dosPathFor(img1, mountingpointsForExecutables);
					if (dosLocation.drive_ != null) {
						String q = dosLocation.file_.indexOf(' ') == -1 ? StringUtils.EMPTY: "\"";
						result.append(" ").append(q).append(dosLocation.drive_).append('\\').append(dosLocation.directory_);
						if (StringUtils.isNotBlank(dosLocation.directory_)) {
							result.append('\\');
						}
						result.append(dosLocation.file_).append(q);
					}
				}
				
				for (int i = 1; i < BOOTER_IMAGES; i++) {
					if (hasImageMountMatch(img_[i])) {
						result.append(" \"").append(img_[i]).append("\"");
					} else {
						if (StringUtils.isNotBlank(img_[i])) {
							FileLocation img = new FileLocation(img_[i], FileLocationService.getInstance().dosrootRelative());
							DosPath dosLocation = dosPathFor(img, mountingpointsForExecutables);
							if (dosLocation.drive_ != null) {
								String q = dosLocation.file_.indexOf(' ') == -1 ? StringUtils.EMPTY: "\"";
								result.append(" ").append(q).append(dosLocation.drive_).append('\\').append(dosLocation.directory_);
								if (StringUtils.isNotBlank(dosLocation.directory_)) {
									result.append('\\');
								}
								result.append(dosLocation.file_).append(q);
							}
						}
					}
				}
				if (StringUtils.isNotBlank(imgDriveletter_)) {
					result.append(" -l ").append(imgDriveletter_);
				}
				result.append(SystemUtils.EOLN);
				appendPauseAfterFailedBoot(result);
				insertCustomSection(result, 2);
			}

		} else {

			// template
			insertCustomSection(result, 1);
			insertCustomSection(result, 2);

		}

		if (!prepareOnly) {
			if (BooleanUtils.isTrue(loadfix_)) {
				result.append("loadfix -f").append(SystemUtils.EOLN);
			}

			insertCustomSection(result, 3);

			if (BooleanUtils.isTrue(pause_)) {
				result.append("pause").append(SystemUtils.EOLN);
			}

			if (BooleanUtils.isTrue(exit_))
				result.append("exit").append(SystemUtils.EOLN);
			// kept lines that were after "exit": also when "exit" is unticked now (before alpha 46 they were gone then)
			insertPlainLines(result, SECTIONS);
		}

		if (result.length() > 0) {
			result.insert(0, "[autoexec]" + SystemUtils.EOLN);
		}

		return backToTheGame(result.toString(), backToGame);
	}
}
