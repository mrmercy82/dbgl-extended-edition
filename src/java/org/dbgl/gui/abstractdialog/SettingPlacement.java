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
package org.dbgl.gui.abstractdialog;

import java.util.List;
import java.util.regex.Pattern;


/**
 * Decides on which page of the profile/template dialog a DOSBox setting without a field of its own is shown. The settings
 * come from the configuration file of the selected DOSBox version, so every DOSBox version (DOSBox-X, Staging, ECE, ...)
 * shows its own ones. The rules go by section and setting name; the first matching rule wins. Settings no rule knows are
 * put on the page that already holds most fields of their section (decided by the dialog), or on the DOSBox page.
 */
public final class SettingPlacement {

	public static final String DOSBOX = "dialog.template.tab.dosbox";
	public static final String STARTUP = "dialog.template.tab.startup";
	public static final String LOGGING = "dialog.template.tab.logging";
	public static final String RENDERING = "dialog.template.tab.rendering";
	public static final String TTF = "dialog.template.tab.ttf";
	public static final String DOS = "dialog.template.dos";
	public static final String CONFIGSYS = "dialog.template.tab.configsys";
	public static final String DOSV = "dialog.template.tab.dosv";
	public static final String MOUNTING = "dialog.template.tab.mounting";
	public static final String SYSTEM = "dialog.template.tab.system";
	public static final String CPU = "dialog.template.tab.cpu";
	public static final String MEMORY = "dialog.template.tab.memory";
	public static final String DISKS = "dialog.template.tab.disks";
	public static final String CONTROLLERS = "dialog.template.tab.controllers";
	public static final String MAINBOARD = "dialog.template.tab.mainboard";
	public static final String PC98 = "dialog.template.tab.pc98";
	public static final String VIDEO = "dialog.template.tab.video";
	public static final String VESA = "dialog.template.tab.vesa";
	public static final String CGACOMPOSITE = "dialog.template.tab.cgacomposite";
	public static final String VOODOO = "dialog.template.tab.voodoo";
	public static final String AUDIO = "dialog.template.tab.audio";
	public static final String MIDI = "dialog.template.tab.midi";
	public static final String FLUIDSYNTH = "dialog.template.tab.fluidsynth";
	public static final String MT32 = "dialog.template.tab.mt32";
	public static final String SOUNDBLASTER = "dialog.template.tab.soundblaster";
	public static final String SOUNDBLASTER2 = "dialog.template.tab.soundblaster2";
	public static final String GUS = "dialog.template.tab.gravisultrasound";
	public static final String WSS = "dialog.template.tab.wss";
	public static final String PCSPEAKER = "dialog.template.tab.pcspeaker";
	public static final String TANDY = "dialog.template.tab.tandy";
	public static final String PS1 = "dialog.template.tab.ps1";
	public static final String LPTDAC = "dialog.template.tab.parallelportdac";
	public static final String IMFC = "dialog.template.tab.imfc";
	public static final String INNOVA = "dialog.template.tab.innova";
	public static final String KEYBOARD = "dialog.template.tab.keyboard";
	public static final String MOUSE = "dialog.template.tab.mouse";
	public static final String JOYSTICK = "dialog.template.tab.joystick";
	public static final String PRINTER = "dialog.template.tab.printer";
	public static final String NETWORK = "dialog.template.tab.network";
	public static final String IPX = "dialog.template.tab.ipx";
	public static final String MODEM = "dialog.template.tab.modem";
	public static final String NE2000 = "dialog.template.tab.ne2000";

	private record Rule(Pattern section, Pattern item, String page) {
		boolean matches(String s, String i) {
			return section.matcher(s).matches() && item.matcher(i).find();
		}
	}

	private static Rule r(String section, String item, String page) {
		return new Rule(Pattern.compile(section), Pattern.compile(item), page);
	}

	private static final String ANY = "";

	private static final List<Rule> RULES = List.of(
		// DOSBox itself
		r("log", ANY, LOGGING),
		r("script", ANY, STARTUP),
		r("capture", ANY, DOSBOX),
		r("dosbox", "debug|bochs", LOGGING),
		r("dosbox", "shell environment", DOS), // before 'shell' below
		r("dosbox", "capture|record|skip encoding", DOSBOX),
		r("dosbox", "call binary|splash|quit after warning|shell|compresssaveparts|mcp_server|title|language|saveslot|savefile", DOSBOX),
		r("dosbox", "memsize|memalias|memory hole|mem limit|640kb|a20|private area", MEMORY),
		r("dosbox", "keyboard", KEYBOARD),
		r("dosbox", "fat|disk image|write protect|leading colon", MOUNTING),
		r("dosbox", "weitek|undocumented cf", CPU),
		r("dosbox", "beep", PCSPEAKER),
		// DOSBox Daum keeps the VESA and VGA switches of DOSBox-X in [dosbox]
		r("dosbox", "vesa|vbe|lfb|bpp", VESA),
		r("dosbox", "cgasnow", CGACOMPOSITE),
		r("dosbox", "sierra|hpel|hretrace|forcerate|vga resize|vga active", VIDEO),
		r("dosbox", "machine|vmemsize|vmem|video", VIDEO),
		r("dosbox", ANY, MAINBOARD), // ACPI, DMA, PIC, PCI/ISA bus, port 92, ROM BIOS, I/O delays
		r("sdl", "mapper", KEYBOARD),
		r("sdl", "mouse|sensitivity|capture", MOUSE),
		r("render|vsync", ANY, RENDERING),
		r("ttf", ANY, TTF),
		r("4dos|config", ANY, CONFIGSYS),
		r("dosv", ANY, DOSV),

		// DOS
		r("dos", "int ?33|int15 mouse|biosps2|mouse", MOUSE),
		r("dos", "int ?13|disk io|floppy|hard ?disk", DISKS),
		r("dos", "automount|drive z|mscdex|cd-?rom", MOUNTING),
		r("dos", "keyboard|int 16h|con device|keyb", KEYBOARD),
		r("dos", "break on int3|log", LOGGING),
		r("dos", "ems|xms|umb|hma|a20|vcpi|emm386|zero memory|int 67h|int 68h|mcb|free memory|private (segment|area)|loadfix|memory size|memory block|allocation in umb", MEMORY),
		r("dos", ANY, DOS),

		// system
		r("cpu", "apm|pnp|isapnp", MAINBOARD),
		r("cpu|fpu", ANY, CPU),
		r("pc98", ANY, PC98),
		r("ide,.*|fdc,.*", ANY, CONTROLLERS),
		r("pci", ANY, VOODOO),

		// video
		r("video", "debug", LOGGING),
		r("video", "composite|cgasnow", CGACOMPOSITE),
		r("video", "vesa|vbe|svga|lfb|bpp|high definition|modelist", VESA),
		r("video|vga|reelmagic", ANY, VIDEO),
		r("voodoo|glide|3dfx", ANY, VOODOO),

		// audio
		r("mixer", ANY, AUDIO),
		r("midi", "^fluid", FLUIDSYNTH),
		r("midi", "^mt32", MT32),
		r("midi|soundcanvas", ANY, MIDI),
		r("fluidsynth", ANY, FLUIDSYNTH),
		r("mt32", ANY, MT32),
		r("sblaster|emu8k", ANY, SOUNDBLASTER),
		r("sblaster2", ANY, SOUNDBLASTER2),
		r("gus", ANY, GUS),
		r("wss", ANY, WSS),
		r("speaker", "tandy|tandydac", TANDY),
		r("speaker", "ps1", PS1),
		r("speaker", "lpt|disney|covox|stereo-on-1", LPTDAC),
		r("speaker", ANY, PCSPEAKER),
		r("imfc", ANY, IMFC),
		r("innova|innovation", ANY, INNOVA),

		// peripherals
		r("keyboard|keymap", ANY, KEYBOARD),
		r("mouse", ANY, MOUSE),
		r("joystick|mapper", ANY, JOYSTICK),
		r("parallel|printer", ANY, PRINTER),

		// network
		r("ipx", ANY, IPX),
		r("serial|modem", ANY, MODEM),
		r("ne2000|ethernet.*|pcap|slirp", ANY, NE2000),
		r("webserver|network", ANY, NETWORK));

	private SettingPlacement() {
	}

	/**
	 * @return the first sentence of the description DOSBox gives for a setting, at most about max characters, or "" if there
	 *         is none
	 */
	public static String summary(String help, int max) {
		if (help == null)
			return "";
		String text = help.replaceAll("\\s+", " ").strip();
		int end = -1;
		for (int i = 0; i < text.length() - 1; i++) {
			if (((text.charAt(i) == '.') || (text.charAt(i) == ':')) && (text.charAt(i + 1) == ' ')) {
				end = text.charAt(i) == '.' ? i + 1: i;
				break;
			}
		}
		if (end > 0)
			text = text.substring(0, end);
		if (text.length() > max) {
			int cut = text.lastIndexOf(' ', max);
			text = text.substring(0, cut > max / 2 ? cut: max).strip() + "\u2026";
		}
		return text;
	}

	/** @return the page for the setting, or null if no rule knows it */
	public static String pageFor(String section, String item) {
		String s = section.strip().toLowerCase();
		String i = item.strip().toLowerCase();
		for (Rule rule: RULES)
			if (rule.matches(s, i))
				return rule.page();
		return null;
	}
}
