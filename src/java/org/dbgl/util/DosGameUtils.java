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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;


public class DosGameUtils {

	private DosGameUtils() {
	}

	private static final Set<String> SETUPFILES = new TreeSet<>(Arrays.asList("setup.exe", "install.exe", "setsound.exe", "setup.bat", "config.exe", "setsound.bat", "sound.bat", "sound.exe",
		"install.com", "install.bat", "sndsetup.exe", "soundset.exe", "config.bat", "setup.com", "setsnd.exe", "setd.exe", "configur.exe", "uwsound.exe"));
	private static final Set<String> UNLIKELYMAINFILES = new TreeSet<>(Arrays.asList("dos4gw.exe", "readme.bat", "intro.exe", "loadpats.exe", "uvconfig.exe", "soundrv.com", "sblaster.com",
		"sound.bat", "univbe.exe", "midpak.com", "ultramid.exe", "mpscopy.exe", "readme.exe", "sbpro.com", "help.bat", "exists.com", "helpme.exe", "paudio.com", "bootdisk.exe", "pas16.com",
		"mssw95.exe", "setd.exe", "adlib.com", "sbclone.com", "sb16.com", "godir.com", "ibmsnd.com", "crack.com", "gf166.com", "__insth.bat", "adlibg.com", "space.com", "instgame.bat", "ibmbak.com",
		"pkunzjr.com", "nosound.com", "sview.exe", "mgraphic.exe", "title.exe", "misc.exe", "checkcd.bat", "patch.exe", "tansltl.com", "readme.com", "uninstal.exe", "source.com", "cmidpak.com",
		"vector.com", "view.exe", "rtm.exe", "eregcard.exe", "sndsys.com", "info.exe", "docshell.exe", "catalog.exe", "ipxsetup.exe", "yes.com", "stfx.com", "getkey.com", "lsize.com", "makepath.com",
		"sersetup.exe", "commit.exe", "_setup.exe", "end.exe", "what.exe", "setm.exe", "cvxsnd.com", "aria.com", "tgraphic.exe", "egraphic.exe", "smidpak.com", "tlivesa.com", "vmsnd.com",
		"detect.exe", "digvesa.com", "cwsdpmi.exe", "vesa.exe", "havevesa.exe", "_install.bat", "smsnd.com", "insticon.exe", "installh.bat", "install2.bat", "info.bat", "setmain.exe", "swcbbs.exe",
		"vbetest.exe", "pmidpak.com", "inst.exe", "cleardrv.exe", "winstall.exe", "ibm1bit.com", "tmidpak.com", "dealers.exe", "digisp.com", "drv_bz.com", "drv_sb.com", "drv_ss.com", "convert.exe",
		"editor.exe", "cgraphic.exe", "update.bat", "smackply.exe", "univesa.exe", "lha.exe", "makeboot.bat", "nnansi.com", "setblast.exe", "autoexec.bat", "helpme.bat", "exist.com", "fixboot.exe",
		"ask.com", "vesatest.exe", "manual.exe", "sbwave.com", "rmidpak.com", "diagnost.exe", "pkunzip.exe", "sinstall.exe", "megaem.exe", "vesa.com", "getdrv.exe", "drv_sbd.com", "chkvesa.exe",
		"chkmem.com", "setstick.exe"));

	// alpha 50: helpers that are no game either: DOS extenders, tools of DOS, packers, what a store puts next to the game
	private static final Set<String> MORE_UNLIKELY = Set.of("dos32a.exe", "dos4g.exe", "pmodew.exe", "dosbox.exe", "dosbox-x.exe", "unins000.exe", "uninst.exe", "uninstall.exe", "emm386.exe",
		"mouse.com", "mouse.exe", "mscdex.exe", "smartdrv.exe", "share.exe", "choice.com", "choice.exe", "pkzip.exe", "arj.exe", "lharc.exe", "unzip.exe", "rar.exe", "unrar.exe", "xcopy.exe",
		"keyb.com", "mode.com", "debug.exe", "edit.com", "qbasic.exe", "deltree.exe", "attrib.exe", "format.com", "fdisk.exe", "scandisk.exe", "gogwrap.exe", "dxcfg.exe", "cloud_saves.exe",
		// as found in a collection of games: what plays before and after the game, starts the sound card, makes a boot disk
		"opening.exe", "closing.exe", "outro.exe", "endgame.exe", "unisound.com", "unisound.exe", "bootmkr.exe", "boottie.exe", "imuse.exe", "cwsdpmi.exe", "insticon.exe", "autorun.exe",
		"checkcd.exe", "detvesa.exe", "command.com",
		// the games of Sierra are started with SIERRA.COM or SIERRA.EXE, not with the interpreter behind it
		"sciv.exe", "scidhuv.exe", "sciduv.exe", "sciwv.exe");
	// an editor of maps or sounds is not the game (MAPEDIT.EXE, WAR2EDIT.EXE, SFXED95.EXE)
	private static final java.util.regex.Pattern EDITOR_LIKE = java.util.regex.Pattern.compile("(?i).*(edit|ed95)[a-z0-9]{0,2}\\.(exe|com|bat)");
	private static final java.util.regex.Pattern SETUP_LIKE = java.util.regex.Pattern
			.compile("(?i).*(setup|install|setsound|sndsetup|soundset|setsnd|config|uninst|gameset|setmain|setmuse|setvideo).*");
	// a program that sets up by its name, though not one of the known names (GAMESET.EXE of Comanche, SETMUSE.EXE of X-Wing,
	// SBSETUP.EXE): after the known ones, before the installers. Not those for Windows (WINSETUP.EXE of a sound card) or a
	// network (IPXSETUP.EXE), nor one that sets the picture only.
	private static final java.util.regex.Pattern SETUP_BY_NAME = java.util.regex.Pattern.compile("(?i)(?!win|ipx|ser|mod|net|un).*(setup|setsound|sndsetup|soundset|setsnd|gameset|setmuse)\\.(exe|com|bat)");
	// the programs that set up sound and controls first, the installers last: an installed game is set up with SETUP.EXE,
	// INSTALL.EXE would install it again (for some games it is the only one that sets the sound card: then it is taken)
	private static final List<String> SETUP_ORDER = List.of("setup.exe", "setsound.exe", "sndsetup.exe", "soundset.exe", "setsnd.exe", "uwsound.exe", "setup.com", "setup.bat", "setsound.bat",
		"sound.exe", "sound.bat", "config.exe", "configur.exe", "config.bat", "setd.exe", "install.exe", "install.com", "install.bat");
	private static final Set<String> SMALL_WORDS = Set.of("the", "of", "and", "a", "an", "in", "on", "to", "for", "der", "die", "das", "und");

	/**
	 * The program that most likely starts the game. Alpha 50: before, the file whose name is nearest to the title letter by
	 * letter was taken, which for a title like "Ultima Underworld: The Stygian Abyss" is just the longest name. Now a name
	 * counts by what such names are made of: the title run together or a part of it (DOOM2, MONKEY), its first letters (UW,
	 * DOTT), the beginning of a word with the number of the title (WAR2, SC2000, KEEN4E); among equals the file nearer to the
	 * top of the folder, a program before a batch file, then the nearer name as before.
	 */
	public static int findMostLikelyMainIndex(String title, List<File> files) {
		if (files.isEmpty())
			return -1;
		List<Integer> candidates = IntStream.range(0, files.size()).filter(i -> {
			String name = files.get(i).getName().toLowerCase(java.util.Locale.ROOT);
			return !SETUPFILES.contains(name) && !UNLIKELYMAINFILES.contains(name) && !MORE_UNLIKELY.contains(name) && !SETUP_LIKE.matcher(name).matches() && !EDITOR_LIKE.matcher(name).matches();
		}).boxed().toList();
		if (candidates.isEmpty())
			candidates = IntStream.range(0, files.size()).boxed().toList();
		int minDepth = candidates.stream().mapToInt(i -> depth(files.get(i))).min().orElse(0);
		// a program for Windows cannot start in DOSBox: taken only if there is nothing else (a launcher for Windows next to
		// the batch file of the same name, as mods of Duke Nukem 3D have it)
		List<Integer> forDos = candidates.stream().filter(i -> {
			String kind = files.get(i).isFile() ? ProfileChecks.programKind(files.get(i)): null;
			return !"pe".equals(kind) && !"ne".equals(kind);
		}).toList();
		if (!forDos.isEmpty())
			candidates = forDos;
		int best = candidates.get(0);
		long bestScore = Long.MIN_VALUE;
		for (int i: candidates) {
			File f = files.get(i);
			String base = org.apache.commons.io.FilenameUtils.getBaseName(f.getName());
			long score = (titleScore(title, base) + (hasOwnSettings(f, base) ? 15: 0)) * 1000L - (depth(f) - minDepth) * 15_000L - (f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".bat") ? 2000: 0)
					- org.apache.commons.text.similarity.LevenshteinDistance.getDefaultInstance().apply(compact(title), compact(base));
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		return best;
	}

	/**
	 * Is there a file of settings by the name of the program next to it (WG.CFG next to WG.EXE, WAR2.INI next to WAR2.EXE)? The
	 * game itself keeps its settings there, not a program that only shows the intro (GLORY.EXE of Wings of Glory).
	 */
	private static boolean hasOwnSettings(File f, String base) {
		File dir = f.getParentFile();
		String[] names = dir == null ? null: dir.list();
		if (names != null)
			for (String n: names)
				if (n.equalsIgnoreCase(base + ".CFG") || n.equalsIgnoreCase(base + ".INI"))
					return true;
		return false;
	}

	private static int depth(File f) {
		int result = 0;
		for (File p = f.getParentFile(); p != null; p = p.getParentFile())
			result++;
		return result;
	}

	private static String compact(String s) {
		return StringUtils.defaultString(s).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	/** the words of a title, roman numbers as digits ("Doom II" -> doom, 2), a number of its own ("SimCity 2000" -> simcity, 2000) */
	public static List<String> titleWords(String title) {
		List<String> result = new ArrayList<>();
		for (String w: StringUtils.defaultString(title).toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+")) {
			if (w.isEmpty())
				continue;
			String roman = switch (w) {
				case "ii" -> "2";
				case "iii" -> "3";
				case "iv" -> "4";
				case "v" -> "5";
				case "vi" -> "6";
				case "vii" -> "7";
				case "viii" -> "8";
				case "ix" -> "9";
				default -> null;
			};
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([a-z]+)(\\d+)$").matcher(w);
			if (roman != null) {
				result.add(roman);
			} else if (m.matches()) {
				result.add(m.group(1));
				result.add(m.group(2));
			} else {
				result.add(w);
			}
		}
		return result;
	}

	/** how well the name of a file (without its ending) fits a title: 0 not at all, more is better */
	public static int titleScore(String title, String baseName) {
		String base = compact(baseName);
		List<String> words = titleWords(title);
		if (base.isEmpty() || words.isEmpty())
			return 0;
		List<String> names = words.stream().filter(w -> !w.matches("\\d+")).toList(), numbers = words.stream().filter(w -> w.matches("\\d+")).toList();
		List<String> main = names.stream().filter(w -> !SMALL_WORDS.contains(w)).toList();
		String all = String.join("", words), allMain = String.join("", main) + String.join("", numbers);
		if (base.equals(all) || base.equals(allMain))
			return 100;
		// "3D" at the end of the name and in the title counts as the number of the title: WOLF3D, DUKE3D
		boolean threeD = base.endsWith("3d") && (base.length() > 2) && compact(title).contains("3d");
		String rest = threeD ? base.substring(0, base.length() - 2): base;
		String letters = rest.replaceAll("\\d+", ""), digits = rest.replaceAll("[^0-9]", "");
		boolean number = threeD || (!digits.isEmpty() && numbers.contains(digits));
		int score = 0;
		// the first letters of the words: DOTT, UW, with or without the small words; with the number of the title: SQ4
		String initials = names.stream().map(w -> w.substring(0, 1)).collect(Collectors.joining()), initialsMain = main.stream().map(w -> w.substring(0, 1)).collect(Collectors.joining());
		if ((letters.length() >= 2) && (letters.equals(initials) || letters.equals(initialsMain)))
			score = Math.max(score, 70);
		for (String w: main) {
			if (letters.equals(w))
				score = Math.max(score, 80); // MONKEY, PRINCE, UFO
			else if ((letters.length() >= 3) && w.startsWith(letters))
				score = Math.max(score, 55 + Math.min(letters.length(), 8)); // WAR for Warcraft
			else if ((w.length() >= 4) && letters.startsWith(w))
				score = Math.max(score, 60); // KEEN4E: the word and something after it
			else if ((w.length() >= 4) && letters.contains(w))
				score = Math.max(score, 45);
			else if (StringUtils.getCommonPrefix(letters, w).length() >= 5)
				score = Math.max(score, 45 + StringUtils.getCommonPrefix(letters, w).length()); // COLONIZE for Colonization
		}
		if ((score == 0) && (letters.length() >= 2) && (all.startsWith(base) || allMain.startsWith(base)))
			score = 50; // the title run together and cut off (DOS names have eight letters): SIMCITY2 for SimCity 2000
		if (number)
			score += (score > 0) || (letters.length() <= 3) ? 20: 0; // the number alone fits many files (LEVEL2)
		return score;
	}

	/**
	 * The program that sets the game up. Alpha 50: the names are compared without regard to upper and lower case (the files of
	 * DOS games are in capitals, so nothing was found), and SETUP.EXE comes before INSTALL.EXE, whichever is first in the list.
	 */
	public static int findSetupIndex(List<File> files) {
		int best = -1, bestRank = Integer.MAX_VALUE;
		for (int i = 0; i < files.size(); i++) {
			String name = files.get(i).getName().toLowerCase(java.util.Locale.ROOT);
			int rank = SETUP_ORDER.indexOf(name) * 2;
			if ((rank < 0) && SETUP_BY_NAME.matcher(name).matches())
				rank = SETUP_ORDER.indexOf("install.exe") * 2 - 1; // alpha 50: before the installers
			if ((rank < 0) && SETUPFILES.contains(name))
				rank = SETUP_ORDER.size() * 2;
			if (rank < 0)
				continue;
			// alpha 53: never a program for Windows (Ultima VII Part Two has a SETUP.EXE for a screen saver of Windows 3.x)
			String kind = files.get(i).isFile() ? ProfileChecks.programKind(files.get(i)): null;
			if ("pe".equals(kind) || "ne".equals(kind))
				continue;
			rank = rank * 100 + Math.min(depth(files.get(i)), 99); // among the same names the one nearer to the top
			if (rank < bestRank) {
				bestRank = rank;
				best = i;
			}
		}
		return best;
	}
}