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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


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

	public static int findMostLikelyMainIndex(String title, List<File> files) {
		Set<String> combined = Stream.concat(SETUPFILES.stream(), UNLIKELYMAINFILES.stream()).sorted().collect(Collectors.toCollection(TreeSet::new));
		List<File> mostLikelyFiles = files.stream().filter(x -> !combined.contains(x.getName().toLowerCase())).toList();
		if (mostLikelyFiles.isEmpty())
			return StringRelatedUtils.findBestMatchIndex(title, FilesUtils.listFileNamesWithoutExtension(files));
		return files.indexOf(mostLikelyFiles.get(StringRelatedUtils.findBestMatchIndex(title, FilesUtils.listFileNamesWithoutExtension(mostLikelyFiles))));
	}

	public static int findSetupIndex(List<File> files) {
		return IntStream.range(0, files.size()).filter(i -> SETUPFILES.contains(files.get(i).getName())).findFirst().orElse(-1);
	}
}