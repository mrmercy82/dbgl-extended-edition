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
package org.dbgl.model.entity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.dbgl.gui.controls.CommaSeparatedDaControlConvertor;
import org.dbgl.gui.controls.SeparatedValuesDaControlConvertor;
import org.dbgl.gui.controls.SpaceSeparatedDaControlConvertor;
import org.dbgl.gui.interfaces.DaControlConvertor;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.conf.Configuration;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.SettingsService;

public class GenerationAwareConfigurable extends Configurable {
	
	private static final Pattern CYCLES_PATRN = Pattern.compile("^((auto)(\\s+(fixed\\s+)?\\d+\\%?)?(\\s+(fixed\\s+)?\\d+\\%?)?|(max)(\\s+(fixed\\s+)?\\d+\\%?)?|((fixed\\s+)?(\\d+)))(\\s+limit\\s+(\\d+))?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern CPU_CYCLES_PATRN = Pattern.compile("^((max)(\\s+limit\\s+(\\d+))?|((fixed\\s+)?(\\d+)))$", Pattern.CASE_INSENSITIVE);
	private static final Pattern CPU_CYCLES_PROTECTED_PATRN = Pattern.compile("^((auto)|(max)(\\s+limit\\s+(\\d+))?|((fixed\\s+)?(\\d+)))$", Pattern.CASE_INSENSITIVE);

	public static enum Generation {
		official_063, official_065, official_070, official_073,
		svn_r4483,
		daum_20150125,
		ece_r4482,
		staging_0810, staging_0820, staging_0830,
		x_08220, x_20240301, x_20241001
	};
	
	public static Generation[][] GENS = {
			{ Generation.official_063, Generation.official_065, Generation.official_070, Generation.official_073 },
			{ Generation.svn_r4483 },
			{ Generation.daum_20150125 },
			{ Generation.ece_r4482 },
			{ Generation.staging_0810, Generation.staging_0820, Generation.staging_0830 },
			{ Generation.x_08220, Generation.x_20240301, Generation.x_20241001 }
	};

	protected DosboxVersion dosboxVersion_;
	protected Generation gen_;
	
	public DosboxVersion getDosboxVersion() {
		return dosboxVersion_;
	}

	public void setDosboxVersion(DosboxVersion dosboxVersion) {
		dosboxVersion_ = dosboxVersion;
		
		if (dosboxVersion_ != null)
			gen_ = determineGen();
	}
	
	private Generation determineGen() {
		return determineGen(dosboxVersion_);
	}
	
	public static Generation determineGen(DosboxVersion dosboxVersion) {
		String fam = dosboxVersion.getFamily();
		String ver = dosboxVersion.getVersion();
		
		if (Strings.CI.equals(fam, "Official")) {
			if (Strings.CS.equals(ver, "0.63"))
				return Generation.official_063;
			else if (Strings.CS.equals(ver, "0.65"))
				return Generation.official_065;
			else if (Strings.CS.equalsAny(ver, "0.70", "0.71", "0.72"))
				return Generation.official_070;
			else if (Strings.CS.equalsAny(ver, "0.73", "0.74", "0.74-2", "0.74-3"))
				return Generation.official_073;
		} else if (Strings.CI.equals(fam, "DOSBox SVN")) {
			return Generation.svn_r4483;
		} else if (Strings.CI.equals(fam, "DOSBox Daum")) {
			return Generation.daum_20150125;
		} else if (Strings.CI.equals(fam, "DOSBox ECE")) {
			return Generation.ece_r4482;
		} else if (Strings.CI.equals(fam, "DOSBox Staging")) {
			if (Strings.CS.equalsAny(ver, "0.81.0", "0.81.1", "0.81.2"))
				return Generation.staging_0810;
			else if (Strings.CS.equalsAny(ver, "0.82.0", "0.82.1", "0.82.2"))
				return Generation.staging_0820;
			else if (Strings.CS.equalsAny(ver, "0.83.0"))
				return Generation.staging_0830;
		} else if (Strings.CI.equals(fam, "DOSBox-X")) {
			if (Strings.CS.equals(ver, "0.82.20"))
				return Generation.x_08220;
			else if (Strings.CS.equalsAny(ver, "2024.03.01", "2024.07.01"))
				return Generation.x_20240301;
			else if (Strings.CS.equalsAny(ver, "2024.10.01", "2024.12.04", "2025.01.01", "2025.02.01"))
				return Generation.x_20241001;
		}
		
		System.err.println("DOSBox generation could not be determined, assume official_073");
		return Generation.official_073;
	}
		
	public static int familyIdx(Generation g) {
		for (int i = 0; i < GENS.length; i++) {
			for (int j = 0; j < GENS[i].length; j++) {
				if (GENS[i][j] == g)
					return i;
			}
		}
		return -1;
	}
	
	public static int revisionIdx(Generation g) {
		for (int i = 0; i < GENS.length; i++) {
			for (int j = 0; j < GENS[i].length; j++) {
				if (GENS[i][j] == g)
					return j;
			}
		}
		return -1;
	}
	
	public Generation determineGenByConf() {
		if (isDosboxVersion073OrAbove())
			return Generation.official_073;
		if (isDosboxVersion070or071or072())
			return Generation.official_070;
		if (isDosboxVersion065())
			return Generation.official_065;
		if (isDosboxVersion063())
			return Generation.official_063;
		return null;
	}
	
	public boolean isDosboxVersion073OrAbove() {
		return configuration_.hasValue("cpu", "cputype") && configuration_.hasValue("midi", "mididevice") && configuration_.hasValue("midi", "midiconfig") && configuration_.hasValue("sblaster", "sbmixer") && configuration_.hasValue("sblaster", "oplemu")
				&& configuration_.hasValue("gus", "gusirq") && configuration_.hasValue("gus", "gusdma");
	}

	public boolean isDosboxVersion070or071or072() {
		return configuration_.hasValue("joystick", "joysticktype") && configuration_.hasValue("joystick", "timed") && configuration_.hasValue("joystick", "autofire") && configuration_.hasValue("joystick", "swap34") && configuration_.hasValue("joystick", "buttonwrap")
				&& configuration_.hasValue("dos", "keyboardlayout");
	}

	public boolean isDosboxVersion065() {
		return configuration_.hasValue("sdl", "windowresolution") && configuration_.hasValue("sdl", "usescancodes") && configuration_.hasValue("sblaster", "sbtype") && configuration_.hasValue("sblaster", "sbbase") && configuration_.hasValue("gus", "gusrate")
				&& configuration_.hasValue("gus", "gusbase") && configuration_.hasValue("speaker", "tandy") && configuration_.hasValue("bios", "joysticktype") && configuration_.hasValue("serial", "serial1") && configuration_.hasValue("dos", "umb");
	}

	public boolean isDosboxVersion063() {
		return configuration_.hasValue("sdl", "fullfixed") && configuration_.hasValue("sdl", "hwscale") && configuration_.hasValue("midi", "intelligent") && configuration_.hasValue("sblaster", "type") && configuration_.hasValue("sblaster", "base") && configuration_.hasValue("gus", "rate")
				&& configuration_.hasValue("gus", "base") && configuration_.hasValue("modem", "modem") && configuration_.hasValue("modem", "comport") && configuration_.hasValue("modem", "listenport") && configuration_.hasValue("directserial", "directserial");
	}
	
	public boolean capturesDirRelativeToDosboxDir() {
		return switch (gen_) {
			case official_063, official_065, official_070 -> true;
			default -> false;
		};
	}
	
	public String capturesConfSection() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "capture";
			default -> "dosbox";
		};
	}
	
	public String capturesConfSectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "capture_dir";
			default -> "captures";
		};
	}
	
	public DaControlConvertor getPriorityConvertor() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> SpaceSeparatedDaControlConvertor.getInstance();
			default -> CommaSeparatedDaControlConvertor.getInstance();
		};
	}
	
	public String priorityActiveSectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "priority_active_staging0810";
			default -> "priority_active";
		};
	}
	
	public String priorityInactiveSectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "priority_inactive_staging0810";
			default -> "priority_inactive";
		};
	}
	
	public String outputSectionItem() {
		return switch (gen_) {
			case staging_0810 -> "output_staging0810";
			case staging_0820, staging_0830 -> "output_staging0820";
			case daum_20150125, x_08220 -> "output_x08220";
			case x_20240301, x_20241001 -> "output_x20240301";
			default -> "output";
		};
	}
	
	public String windowresSectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "windowresolution_staging0810";
			default -> "windowresolution";
		};
	}
	
	public String scalerSectionItem() {
		return switch (gen_) {
			case daum_20150125 -> "scaler_daum20150125";
			case x_20240301, x_20241001 -> "scaler_x20240301";
			default -> "scaler";
		};
	}
	
	public String aspectSectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "aspect_staging0810";
			case x_20240301, x_20241001 -> "aspect_x20240301";
			default -> "aspect";
		};
	}
	
	public String[] glshaderValues() {
		String[] glShaderFiles = FileLocationService.getInstance().listGlShaderFilenames();
		String[] glShaders = ArrayUtils.addAll(SettingsService.getInstance().getValues("profile", glshaderSectionItem()), glShaderFiles);
		return glShaders;
	}
	
	public String glshaderSectionItem() {
		return switch (gen_) {
			case svn_r4483 -> "glshader_svn4483";
			case ece_r4482 -> "glshader_ecer4482";
			case staging_0810, staging_0820 -> "glshader_staging0810";
			case staging_0830 -> "glshader_staging0830";
			case x_20240301, x_20241001 -> "glshader_x20240301";
			default -> null;
		};
	}
	
	public String vsyncmodeSectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820 -> "vsyncmode_staging0810";
			case staging_0830 -> "vsyncmode_staging0830";
			case daum_20150125, x_08220, x_20240301, x_20241001 -> "vsyncmode_x20240301";
			default -> null;
		};
	}

	public String presentationmodeSectionItem() {
		return switch (gen_) {
			case staging_0830 -> "framepresentationmode_staging0830";
			default -> "framepresentationmode_staging0810";
		};
	}

	public String fullscreenmodeSectionItem() {
		return switch (gen_) {
			case staging_0830 -> "fullscreenmode_staging0830";
			default -> "fullresolution";
		};
	}

	public String localeperiodSectionItem() {
		return switch (gen_) {
			case staging_0830 -> "doslocaleperiod_staging0830";
			default -> "doslocaleperiod_staging0810";
		};
	}

	public String expandshellvarSectionItem() {
		return switch (gen_) {
			case staging_0830 -> "dosexpandshellvar_staging0830";
			default -> "dosexpandshellvar_staging0810";
		};
	}

	public String filelockingSectionItem() {
		return switch (gen_) {
			case staging_0830 -> "filelocking_staging0830";
			default -> "booleans";
		};
	}
	
	public String dosverSectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "dosversion_staging0810";
			case x_08220, x_20240301, x_20241001 -> "dosversion_x20240301";
			default -> null;
		};
	}

	public String machineSectionItem() {
		return switch (gen_) {
			case official_063, official_065, official_070 -> "machine";
			case daum_20150125 -> "machine_daum20150125";
			case staging_0810, staging_0820, staging_0830 -> "machine_staging0810";
			case x_08220 -> "machine_x08220";
			case x_20240301, x_20241001 -> "machine_x20240301";
			default -> "machine073";
		};
	}
	
	public String coreSectionItem() {
		return switch (gen_) {
			case x_20240301, x_20241001 -> "core_x20240301";
			default -> "core";
		};
	}
	
	public String cputypeSectionItem() {
		return switch (gen_) {
			case daum_20150125 -> "cputype_daum20150125";
			case staging_0820, staging_0830 -> "cputype_staging0820";
			case x_20240301, x_20241001 -> "cputype_x20240301";
			default -> "cputype";
		};
	}
	
	public String emsSectionItem() {
		return switch (gen_) {
			case staging_0830 -> "ems_staging0830";
			case x_20240301, x_20241001 -> "ems_x20240301";
			default -> "ems";
		};
	}
	
	public String monochromepalSectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "monochromepalette_staging0810";
			case x_20240301, x_20241001 -> "monochromepalette_x20240301";
			default -> null;
		};
	}
	
	public String voodooSectionItem() {
		return switch (gen_) {
			case daum_20150125, ece_r4482 -> "voodoo_ecer4482";
			case x_20240301, x_20241001 -> "voodoo_x20240301";
			default -> null;
		};
	}
	
	public String lfbglideSectionItem() {
		return switch (gen_) {
			case ece_r4482 -> "lfbglide_ecer4482";
			case x_20240301, x_20241001 -> "lfbglide_x20240301";
			default -> null;
		};
	}
	
	public String midideviceSectionItem() {
		return switch (gen_) {
			case daum_20150125 -> "mididevice_daum20150125";
			case ece_r4482 -> "mididevice_ecer4482";
			case staging_0810, staging_0820 -> "mididevice_staging0810";
			case staging_0830 -> "mididevice_staging0830";
			case x_20240301, x_20241001 -> "mididevice_x20240301";
			default -> "mididevice";
		};
	}
	
	public String fluidsynthdriverSectionItem() {
		return switch (gen_) {
			case ece_r4482 -> "fluidsynthdriver_ecer4482";
			case x_20240301, x_20241001 -> "fluidsynthdriver_x20240301";
			default -> null;
		};
	}
	
	public String mt32modelSectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "mt32model_staging0810";
			case x_20240301, x_20241001 -> "mt32model_x20240301";
			default -> null;
		};
	}
	
	public String sbtypeSectionItem() {
		return switch (gen_) {
			case staging_0820, staging_0830 -> "sbtype_staging0820";
			case x_20240301, x_20241001 -> "sbtype_x20240301";
			default -> "sbtype";
		};
	}
	
	public String sbbaseSectionItem() {
		return switch (gen_) {
			case x_20240301, x_20241001 -> "sbbase_x20240301";
			default -> "sbbase";
		};
	}
	
	public String sbirqSectionItem() {
		return switch (gen_) {
			case x_20240301, x_20241001 -> "sbirq_x20240301";
			default -> "irq";
		};
	}
	
	public String oplmodeSectionItem() {
		return switch (gen_) {
			case svn_r4483 -> "oplmode_svn4483";
			case ece_r4482 -> "oplmode_ecer4482";
			case staging_0810 -> "oplmode_staging0810";
			case staging_0820, staging_0830 -> "oplmode_staging0820";
			case x_20240301 -> "oplmode_x20240301";
			case x_20241001 -> "oplmode_x20241001";
			default -> "oplmode";
		};
	}
	
	public String sbdmaSectionItem() {
		return switch (gen_) {
			case x_20240301, x_20241001 -> "sbdma_x20240301";
			default -> "dma";
		};
	}

	public String oplemuSectionItem() {
		return switch (gen_) {
			case svn_r4483 -> "oplemu_svn4483";
			case ece_r4482 -> "oplemu_ecer4482";
			case x_20240301, x_20241001 -> "oplemu_x20240301";
			default -> "oplemu";
		};
	}
	
	public String sbhdmaSectionItem() {
		return switch (gen_) {
			case x_20240301, x_20241001 -> "sbhdma_x20240301";
			default -> "hdma";
		};
	}
	
	public String sbcmsSectionItem() {
		return switch (gen_) {
			case staging_0820, staging_0830 -> "sbcms_staging0820";
			case x_20241001 -> "sbcms_x20241001";
			default -> null;
		};
	}
	
	public String gusbaseSectionItem() {
		return switch (gen_) {
			case staging_0820, staging_0830 -> "gusbase_staging0820";
			default -> "gusbase";
		};
	}
	
	public String gusirq1SectionItem() {
		return switch (gen_) {
			case staging_0820, staging_0830 -> "irq1_staging0820";
			default -> "irq1";
		};
	}
	
	public String gusdma1SectionItem() {
		return switch (gen_) {
			case staging_0820, staging_0830 -> "dma1_staging0820";
			default -> "dma1";
		};
	}
	
	public String pcspeakerSectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "pcspeaker_staging0810";
			default -> "pcspeaker";
		};
	}
	
	public String pcrateSectionItem() {
		return switch (gen_) {
			case x_20240301, x_20241001 -> "pcrate_x20240301";
			default -> "pcrate";
		};
	}
	
	public String tandySectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "tandy_staging0810";
			default -> "tandy";
		};
	}
	
	public String ps1SectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820 -> "ps1_staging0810";
			case staging_0830, daum_20150125, x_08220, x_20240301, x_20241001 -> "ps1_x20240301"; // on/off
			default -> null;
		};
	}
	
	public String innovaportSectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "innovaport_staging0810";
			case daum_20150125, x_08220, x_20240301, x_20241001 -> "innovaport_x20240301";
			default -> null;
		};
	}
	
	public String scancodesSectionItem() {
		return switch (gen_) {
			case x_20240301, x_20241001 -> "usescancodes_x20240301";
			default -> "usescancodes";
		};
	}

	public String joysticktypeSectionItem() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> "joysticktype_staging0810";
			default -> "joysticktype";
		};
	}
	
	public String[] mixerDeviceNames() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> new String[] {"master", "pcspeaker", "sb", "gus", "opl", "disney", "cdaudio"};
			default -> new String[] {"master", "spkr", "sb", "gus", "fm", "disney", "cdaudio"};
		};
	}
	
	public boolean[] enabledMixerDevices() {
		return switch (gen_) {
			case staging_0810, staging_0820, staging_0830 -> new boolean[] {
					true,
					!Strings.CI.equalsAny(configuration_.getValue("speaker", "pcspeaker"), "none", "off"),
					!Strings.CI.equalsAny(configuration_.getValue("sblaster", "sbtype"), "none", "off"),
					configuration_.getBooleanValue("gus", "gus"),
					!Strings.CI.equalsAny(configuration_.getValue("sblaster", "oplmode"), "none", "off"),
					Strings.CI.equalsAny(configuration_.getValue("speaker", "lpt_dac"), "disney"),
					configuration_.getAutoexec().hasCdAudio()
			};
			default -> new boolean[] {true, true, true, true, true, true, true};
		};
	}
	
	public void downgradeOneOfficialGeneration(Generation generation) {
		switch (generation) {
			case official_073: // towards Gen_070
				configuration_.removeValueIfNone("dosbox", "machine", "cga", "hercules", "pcjr", "tandy");
				configuration_.switchSetting("midi", "mididevice", "midi", "device");
				configuration_.switchSetting("midi", "midiconfig", "midi", "config");
				configuration_.switchSetting("sblaster", "sbmixer", "sblaster", "mixer");
				configuration_.switchSetting("gus", "gusirq", "gus", "irq1");
				if (configuration_.hasValue("gus", "irq1")) {
					configuration_.setValue("gus", "irq2", configuration_.getValue("gus", "irq1"));
				}
				configuration_.switchSetting("gus", "gusdma", "gus", "dma1");
				if (configuration_.hasValue("gus", "dma1")) {
					configuration_.setValue("gus", "dma2", configuration_.getValue("gus", "dma1"));
				}
				configuration_.removeValueIfAny("dos", "keyboardlayout", "auto");
				break;
			case official_070: // towards Gen_065
				configuration_.switchSetting("joystick", "joysticktype", "bios", "joysticktype");
				break;
			case official_065: // towards Gen_063
				if (configuration_.hasValue("midi", "mpu401")) {
					String mpu = configuration_.getValue("midi", "mpu401");
					configuration_.setValue("midi", "mpu401", !mpu.equalsIgnoreCase("none"));
					configuration_.setValue("midi", "intelligent", mpu.equalsIgnoreCase("intelligent"));
				}
				configuration_.switchSetting("sblaster", "sbtype", "sblaster", "type");
				configuration_.switchSetting("sblaster", "sbbase", "sblaster", "base");
				configuration_.switchSetting("gus", "gusrate", "gus", "rate");
				configuration_.switchSetting("gus", "gusbase", "gus", "base");
				break;
			default:
				throw new RuntimeException("Cannot downgrade below generation Gen_063");
		}
	}

	public void upgradeOneOfficialGeneration(Generation generation) {
		switch (generation) {
			case official_063: // towards Gen_065
				boolean mpu = !configuration_.hasValue("midi", "mpu401") || configuration_.getBooleanValue("midi", "mpu401");
				boolean intelli = !configuration_.hasValue("midi", "intelligent") || configuration_.getBooleanValue("midi", "intelligent");
				configuration_.setValue("midi", "mpu401", mpu ? (intelli ? "intelligent": "uart"): "none");
				configuration_.removeValueIfSet("midi", "intelligent");
				configuration_.switchSetting("sblaster", "type", "sblaster", "sbtype");
				configuration_.switchSetting("sblaster", "base", "sblaster", "sbbase");
				configuration_.switchSetting("gus", "rate", "gus", "gusrate");
				configuration_.switchSetting("gus", "base", "gus", "gusbase");
				break;
			case official_065: // towards Gen_070
				configuration_.switchSetting("bios", "joysticktype", "joystick", "joysticktype");
				break;
			case official_070: // towards Gen_073
				configuration_.removeValueIfAny("dosbox", "machine", "vga");
				configuration_.switchSetting("midi", "device", "midi", "mididevice");
				configuration_.switchSetting("midi", "config", "midi", "midiconfig");
				configuration_.switchSetting("sblaster", "mixer", "sblaster", "sbmixer");
				configuration_.switchSetting("gus", "irq1", "gus", "gusirq");
				configuration_.removeValueIfSet("gus", "irq2");
				configuration_.switchSetting("gus", "dma1", "gus", "gusdma");
				configuration_.removeValueIfSet("gus", "dma2");
				configuration_.removeValueIfAny("dos", "keyboardlayout", "none");
				break;
			default:
				throw new RuntimeException("Cannot upgrade above generation Gen_073");
		}
	}
	
	public void downgradeOneStagingGeneration(Generation generation) {
		switch(generation) {
			case staging_0830:
				stagingFrom0830(configuration_);
				break;
			case staging_0820:
				cputypeToOfficial(configuration_);
				cyclesToOfficial(configuration_);
				configuration_.removeValueIfAny("sblaster", "sbtype", "ess");
				configuration_.removeValueIfAny("sblaster", "oplmode", "esfm");
				if (configuration_.hasValue("sblaster", "cms")) {
					if (configuration_.getValue("sblaster", "cms").equals("on")) {
						configuration_.setValue("sblaster", "oplmode", "cms");
					}
				}
				configuration_.removeValueIfAny("gus", "gusbase", "210", "230", "250");
				configuration_.removeValueIfAny("gus", "gusirq", "2", "15");
				break;
			default:
				throw new RuntimeException("Cannot downgrade below generation staging_0810");
		}
	}

	public void upgradeOneStagingGeneration(Generation generation) {
		switch(generation) {
			case staging_0810:
				configuration_.removeValueIfAny("sdl", "output", "openglnb");
				cputypeToStaging(configuration_);
				cyclesToStaging(configuration_);
				if (configuration_.hasValue("sblaster", "oplmode")) {
					if (configuration_.getValue("sblaster", "oplmode").equals("cms")) {
						configuration_.removeValue("sblaster", "oplmode");
						configuration_.setValue("sblaster", "sbtype", "sb1");
					}
				}
				configuration_.removeValueIfAny("gus", "gusbase", "280", "2a0", "2c0", "2e0", "300");
				configuration_.removeValueIfAny("gus", "gusirq", "9", "10");
				configuration_.removeValueIfAny("gus", "gusdma", "0");
				break;
			case staging_0820:
				stagingTo0830(configuration_);
				break;
			default:
				throw new RuntimeException("Cannot upgrade above generation staging_0830");
		}
	}

	private static boolean isTrueValue(String value) {
		return Strings.CI.equalsAny(StringUtils.trim(value), "true", "on", "yes", "1", "enabled");
	}

	private static boolean isBooleanValue(String value) {
		return Strings.CI.equalsAny(StringUtils.trim(value), "true", "false", "on", "off", "yes", "no", "1", "0", "enabled", "disabled");
	}

	/**
	 * DOSBox Staging 0.82.x to 0.83.0: renamed, removed and changed settings.
	 * See https://www.dosbox-staging.org/releases/release-notes/0.83.0/
	 */
	private static void stagingTo0830(Configuration configuration) {
		// [sdl] window & fullscreen
		configuration.switchSetting("sdl", "windowresolution", "sdl", "window_size");
		if (configuration.hasValue("sdl", "fullresolution")) {
			if (Strings.CI.equals(configuration.getValue("sdl", "fullresolution"), "desktop"))
				configuration.setValue("sdl", "fullscreen_mode", "standard");
			configuration.removeValue("sdl", "fullresolution"); // 'original' and WxH are gone
		}
		configuration.switchSetting("sdl", "transparency", "sdl", "window_transparency");
		configuration.removeValueIfSet("sdl", "priority");
		configuration.removeValueIfSet("sdl", "waitonerror");

		// [sdl] frame presentation
		configuration.removeValueIfAny("sdl", "vsync", "auto");
		configuration.setValueIfAny("sdl", "vsync", "on", "adaptive");
		configuration.setValueIfAny("sdl", "vsync", "off", "yield");
		configuration.setValueIfAny("sdl", "presentation_mode", "dos-rate", "cfr");
		configuration.setValueIfAny("sdl", "presentation_mode", "host-rate", "vfr");
		if (configuration.hasValue("sdl", "vsync_skip")) {
			// vsync_skip is gone, 'presentation_mode = host-rate' with 'vsync = off' is the replacement
			if (NumberUtils.toInt(configuration.getValue("sdl", "vsync_skip"), 0) > 0) {
				configuration.setValue("sdl", "presentation_mode", "host-rate");
				configuration.setValue("sdl", "vsync", "off");
			}
			configuration.removeValue("sdl", "vsync_skip");
		}
		configuration.removeValueIfSet("sdl", "host_rate");

		// [render]
		configuration.switchSetting("render", "glshader", "render", "shader");
		configuration.removeValueIfSet("composite", "brightness"); // moved to [render] with a different range
		configuration.removeValueIfSet("composite", "contrast");
		configuration.removeValueIfSet("composite", "saturation");
		configuration.removeValueIfSet("dosbox", "vga_render_per_scanline");

		// [dos]
		configuration.switchSetting("dos", "keyboardlayout", "dos", "keyboard_layout");
		configuration.setValueIfAny("dos", "file_locking", "on", "true", "1");
		configuration.setValueIfAny("dos", "file_locking", "off", "false", "0");
		configuration.setValueIfAny("dos", "expand_shell_variable", "on", "true", "1");
		configuration.setValueIfAny("dos", "expand_shell_variable", "off", "false", "0");
		configuration.setValueIfAny("dos", "ems", "on", "true", "1");
		configuration.setValueIfAny("dos", "ems", "off", "false", "0");

		// [midi]
		configuration.setValueIfAny("midi", "mididevice", "port", "auto", "win32", "alsa", "oss", "coremidi");

		// [innovation]
		if (configuration.hasValue("innovation", "sidmodel")) {
			configuration.setValue("innovation", "innovation", Strings.CI.equals(configuration.getValue("innovation", "sidmodel"), "none") ? "off": "on");
			configuration.removeValue("innovation", "sidmodel");
		}
		configuration.switchSetting("innovation", "6581filter", "innovation", "innovation_sid_filter");
		configuration.removeValueIfSet("innovation", "8580filter");
		configuration.removeValueIfSet("innovation", "sidclock");
		configuration.removeValueIfSet("innovation", "sidport");

		// [mouse]
		if (configuration.hasValue("mouse", "dos_mouse_driver")) {
			configuration.setValue("mouse", "builtin_dos_mouse_driver", isTrueValue(configuration.getValue("mouse", "dos_mouse_driver")) ? "on": "off");
			configuration.removeValue("mouse", "dos_mouse_driver");
		}
		if (configuration.hasValue("mouse", "dos_mouse_immediate")) {
			if (isTrueValue(configuration.getValue("mouse", "dos_mouse_immediate")))
				configuration.setValue("mouse", "builtin_dos_mouse_driver_options", "immediate");
			configuration.removeValue("mouse", "dos_mouse_immediate");
		}
	}

	/**
	 * DOSBox Staging 0.83.0 back to 0.82.x, the reverse of stagingTo0830. Settings that only exist in 0.83 are
	 * dropped later on anyway because the 0.82 DOSBox configuration doesn't contain them.
	 */
	private static void stagingFrom0830(Configuration configuration) {
		// [sdl] window & fullscreen
		configuration.switchSetting("sdl", "window_size", "sdl", "windowresolution");
		if (configuration.hasValue("sdl", "fullscreen_mode")) {
			configuration.setValue("sdl", "fullresolution", "desktop"); // closest match for both standard and forced-borderless
			configuration.removeValue("sdl", "fullscreen_mode");
		}
		configuration.switchSetting("sdl", "window_transparency", "sdl", "transparency");

		// [sdl] frame presentation
		configuration.setValueIfAny("sdl", "vsync", "auto", "fullscreen-only");
		configuration.setValueIfAny("sdl", "presentation_mode", "cfr", "dos-rate");
		configuration.setValueIfAny("sdl", "presentation_mode", "vfr", "host-rate");

		// [render]
		configuration.switchSetting("render", "shader", "render", "glshader");

		// [dos]
		configuration.switchSetting("dos", "keyboard_layout", "dos", "keyboardlayout");
		configuration.removeValueIfAny("dos", "file_locking", "auto");
		configuration.setValueIfAny("dos", "file_locking", "true", "on");
		configuration.setValueIfAny("dos", "file_locking", "false", "off");
		configuration.setValueIfAny("dos", "expand_shell_variable", "true", "on");
		configuration.setValueIfAny("dos", "expand_shell_variable", "false", "off");
		configuration.setValueIfAny("dos", "ems", "true", "on");
		configuration.setValueIfAny("dos", "ems", "false", "off");
		configuration.removeValueIfAny("dos", "locale_period", "native");

		// [midi]
		configuration.setValueIfAny("midi", "mididevice", "auto", "port");
		configuration.removeValueIfAny("midi", "mididevice", "soundcanvas");

		// [innovation]
		if (configuration.hasValue("innovation", "innovation")) {
			configuration.setValue("innovation", "sidmodel", isTrueValue(configuration.getValue("innovation", "innovation")) ? "6581": "none");
			configuration.removeValue("innovation", "innovation");
		}
		configuration.switchSetting("innovation", "innovation_sid_filter", "innovation", "6581filter");

		// [mouse]
		if (configuration.hasValue("mouse", "builtin_dos_mouse_driver")) {
			configuration.setValue("mouse", "dos_mouse_driver", !Strings.CI.equals(configuration.getValue("mouse", "builtin_dos_mouse_driver"), "off"));
			configuration.removeValue("mouse", "builtin_dos_mouse_driver");
		}
		if (configuration.hasValue("mouse", "builtin_dos_mouse_driver_options")) {
			if (Strings.CI.contains(configuration.getValue("mouse", "builtin_dos_mouse_driver_options"), "immediate"))
				configuration.setValue("mouse", "dos_mouse_immediate", true);
			configuration.removeValue("mouse", "builtin_dos_mouse_driver_options");
		}
	}

	/**
	 * Makes boolean values use the same notation as the DOSBox configuration they're compared against. DOSBox Staging 0.83 writes
	 * 'on' and 'off' where earlier versions (and other DOSBox variants) write 'true' and 'false'. Without this, values that are
	 * effectively equal to the DOSBox defaults would no longer be recognized as such.
	 */
	public static void alignBooleanNotation(Configuration configuration, Configuration dosboxConfiguration) {
		for (String section: configuration.getSectionNames()) {
			for (String item: configuration.getItemNames(section)) {
				String value = configuration.getValue(section, item);
				if (!dosboxConfiguration.hasValue(section, item) || !isBooleanValue(value))
					continue;
				String dosboxValue = StringUtils.trim(dosboxConfiguration.getValue(section, item));
				String newValue = null;
				if (Strings.CI.equalsAny(dosboxValue, "on", "off"))
					newValue = isTrueValue(value) ? "on": "off";
				else if (Strings.CI.equalsAny(dosboxValue, "true", "false"))
					newValue = isTrueValue(value) ? "true": "false";
				if ((newValue != null) && !newValue.equals(value))
					configuration.setValue(section, item, newValue);
			}
		}
	}
	
	private static void priorityToStaging(Configuration configuration) {
		if (configuration.hasValue("sdl", "priority")) {
			String s = configuration.getValue("sdl", "priority");
			SeparatedValuesDaControlConvertor conv = SeparatedValuesDaControlConvertor.detect(s);
				
			String[] vSource = conv == null ? null : conv.toControlValues(s);
			if ((vSource == null) || (vSource.length != 2)) {
				System.err.println("Invalid [sdl] priority value detected (" + s + "), using DOSBox defaults instead");
				vSource = new String[] { "higher", "normal" };
			}
			boolean pauseWhenInactive = Strings.CI.equals(vSource[1], "pause");
			if (pauseWhenInactive)
				vSource[1] = "auto"; // use default
			configuration.setValue("sdl", "pause_when_inactive", pauseWhenInactive);
			configuration.setValue("sdl", "priority", SpaceSeparatedDaControlConvertor.getInstance().toConfValue(null, vSource));
		}
	}
	
	private static void priorityToOfficial(Configuration configuration) {
		if (configuration.hasValue("sdl", "priority")) {
			String s = configuration.getValue("sdl", "priority");
			SeparatedValuesDaControlConvertor conv = SeparatedValuesDaControlConvertor.detect(s);
			
			String[] vSource = conv == null ? null : conv.toControlValues(s);
			if ((vSource == null) || (vSource.length != 2)) {
				System.err.println("Invalid [sdl] priority value detected (" + s + "), using DOSBox defaults instead");
				vSource = new String[] { "auto", "auto" };
			}
			if (vSource[0].equalsIgnoreCase("auto"))
				vSource[0] = "higher"; // default value
			if (vSource[1].equalsIgnoreCase("auto"))
				vSource[1] = "normal"; // default value
			boolean pauseWhenInactive = configuration.getBooleanValue("sdl", "pause_when_inactive");
			if (pauseWhenInactive)
				vSource[1] = "pause";
			configuration.removeValueIfSet("sdl", "pause_when_inactive");
			configuration.setValue("sdl", "priority", CommaSeparatedDaControlConvertor.getInstance().toConfValue(null, vSource));
		} else if (configuration.hasValue("sdl", "pause_when_inactive")) {
			// Staging 0.83 has no 'priority' setting anymore, keep 'pause when inactive' anyway
			if (configuration.getBooleanValue("sdl", "pause_when_inactive"))
				configuration.setValue("sdl", "priority", "higher,pause");
			configuration.removeValue("sdl", "pause_when_inactive");
		}
	}
	
	private static void cputypeToStaging(Configuration configuration) {
		if (configuration.hasValue("cpu", "cputype")) {
			String cpuType = configuration.getValue("cpu", "cputype");
			String newCpuType = switch (cpuType) {
				case "386_slow" -> "386";
				case "386" -> "386_fast";
				case "486_slow" -> "486";
				case "pentium_slow" -> "pentium";
				default -> cpuType;
			};
			configuration.setValue("cpu", "cputype", newCpuType);
		}
	}
	
	private static void cputypeToOfficial(Configuration configuration) {
		if (configuration.hasValue("cpu", "cputype")) {
			String cpuType = configuration.getValue("cpu", "cputype");
			String newCpuType = switch (cpuType) {
				case "386" -> "386_slow";
				case "386_fast" -> "386";
				case "486" -> "486_slow";
				case "pentium" -> "pentium_slow";
				default -> cpuType;
			};
			configuration.setValue("cpu", "cputype", newCpuType);
		}
	}
	
	private static void cyclesToStaging(Configuration configuration) {
		if (configuration.hasValue("cpu", "cycles")) {
			String cycles = configuration.getValue("cpu", "cycles");
			Matcher cyclesMatcher = CYCLES_PATRN.matcher(cycles);
			
			if (cyclesMatcher.matches()) {
				String auto = cyclesMatcher.group(2);
				String max = cyclesMatcher.group(7);
				String fixed = cyclesMatcher.group(12);
				String limit = cyclesMatcher.group(14);
				
				String cc = StringUtils.EMPTY;
				String ccp = StringUtils.EMPTY;
				
				if (auto != null) {
					String real = cyclesMatcher.group(3);
					if (real != null) {
						cc = real;

						String prot = cyclesMatcher.group(5);
						ccp = prot != null ? prot: "max";
					} else {
						cc = "3000";
						ccp = "max";
					}

					if (limit != null) {
						ccp += " limit " + limit;
					}
				} else if (max != null) {
					cc = "max";
					
					String prot = cyclesMatcher.group(8);
					ccp = prot != null ? prot: "max";
					
					if (limit != null) {
						cc += " limit " + limit;
						ccp += " limit " + limit;
					}
				} else if (fixed != null) {
					cc = fixed;
					ccp = fixed;
				}
				
				if (Strings.CS.equals(cc, ccp)) {
					ccp = "auto";
				}
				
				configuration.setValue("cpu", "cpu_cycles", cc);
				configuration.setValue("cpu", "cpu_cycles_protected", ccp);
			} else {
				// unknown cycles format, just assign directly to cpu_cycles and hope for the best
				configuration.setValue("cpu", "cpu_cycles", cycles);
				configuration.setValue("cpu", "cpu_cycles_protected", "auto");				
			}

			configuration.removeValue("cpu", "cycles");
		}
	}
	
	private static void cyclesToOfficial(Configuration configuration) {
		if (configuration.hasValue("cpu", "cpu_cycles") && configuration.hasValue("cpu", "cpu_cycles_protected")) {
			String cc = configuration.getValue("cpu", "cpu_cycles");
			String ccp = configuration.getValue("cpu", "cpu_cycles_protected");

			Matcher cpuCyclesMatcher = CPU_CYCLES_PATRN.matcher(cc);
			Matcher cpuCyclesProtectedMatcher = CPU_CYCLES_PROTECTED_PATRN.matcher(ccp);
			
			String cycles = StringUtils.EMPTY;
			
			if (cpuCyclesMatcher.matches() && cpuCyclesProtectedMatcher.matches()) {
				String ccMax = cpuCyclesMatcher.group(2);
				String ccLimit = cpuCyclesMatcher.group(4);
				String ccFixed = cpuCyclesMatcher.group(7);
				String ccpAuto = cpuCyclesProtectedMatcher.group(2);
				String ccpMax = cpuCyclesProtectedMatcher.group(3);
				String ccpLimit = cpuCyclesProtectedMatcher.group(5);
				String ccpFixed = cpuCyclesProtectedMatcher.group(8);
				
				if (ccpAuto != null) {
					if (ccFixed != null) {
						cycles = ccFixed;
					} else {
						cycles = ccMax;
						if (ccLimit != null) {
							cycles += " limit " + ccLimit;
						}
					}
				} else {
					cycles = "auto";
					if (ccFixed != null) {
						cycles += " " + ccFixed;
					} else {
						cycles += " " + ccMax;
					}
					if (ccpFixed != null) {
						cycles += " " + ccpFixed;
					} else {
						cycles += " " + ccpMax;
						if (Strings.CS.equals(cycles, "auto 3000 max")) {
							cycles = "auto";
						}
						if (ccpLimit != null) {
							cycles += " limit " + ccpLimit;
						}
					}
				}
			} else {
				// unknown cycles format, just assign directly to cycles and hope for the best
				cycles = cc;
			}

			if (StringUtils.isBlank(cycles)) {
				cycles = "auto";
			}
			
			configuration.setValue("cpu", "cycles", cycles);
			configuration.removeValue("cpu", "cpu_cycles");
			configuration.removeValue("cpu", "cpu_cycles_protected");
		}
	}
	
	private static void mixerToStaging(Configuration configuration) {
		String mixer = configuration.getAutoexec().getMixer();
		mixer = Strings.CS.replace(mixer, "spkr", "pcspeaker");
		mixer = Strings.CS.replace(mixer, "fm", "opl");
		configuration.getAutoexec().setMixer(mixer);
	}
	
	private static void mixerToOfficial(Configuration configuration) {
		String mixer = configuration.getAutoexec().getMixer();
		mixer = Strings.CS.replace(mixer, "pcspeaker", "spkr");
		mixer = Strings.CS.replace(mixer, "opl", "fm");
		configuration.getAutoexec().setMixer(mixer);
	}
	
	public void downgradeOneXGeneration(Generation generation) {
		switch(generation) {
			case x_20241001:
				if (configuration_.hasValue("sblaster", "cms")) {
					if (configuration_.getValue("sblaster", "cms").equals("on")) {
						configuration_.setValue("sblaster", "oplmode", "cms");
					}
				}
				break;
			case x_20240301:
				configuration_.removeValueIfAny("sdl", "output", "default", "ttf", "openglpp");
				configuration_.setValueIfAny("cpu", "core", "dynamic_x86", "dynamic_nodhfpu", "dynamic_rec");
				configuration_.removeValueIfAny("cpu", "cputype", "pentium_ii", "pentium_iii", "experimental");
				configuration_.setValueIfAny("dosbox", "machine", "hercules", "hercules_plus", "hercules_incolor", "hercules_color");
				configuration_.setValueIfAny("dosbox", "machine", "pcjr", "pcjr_composite", "pcjr_composite2");
				configuration_.setValueIfAny("dosbox", "machine", "ega", "ega200", "jega");
				configuration_.setValueIfAny("dosbox", "machine", "vesa_oldvbe", "vesa_oldvbe10");
				configuration_.removeValueIfAny("dosbox", "machine", "svga_s386c928", "svga_s3vision864", "svga_s3vision868", "svga_s3vision964", "svga_s3vision968", "svga_s3trio32", "svga_s3trio64", "svga_s3trio64v+", "svga_s3virge", "svga_s3virgevx", "svga_ati_egavgawonder", "svga_ati_vgawonder", "svga_ati_vgawonderplus", "svga_ati_vgawonderxl", "svga_ati_vgawonderxl24", "svga_ati_mach8", "svga_ati_mach32", "svga_ati_mach64");
				break;
			default:
				throw new RuntimeException("Cannot downgrade below generation x_08220");
		}
	}

	public void upgradeOneXGeneration(Generation generation) {
		switch(generation) {
			case x_08220:
				break;
			case x_20240301:
				if (configuration_.hasValue("sblaster", "oplmode")) {
					if (configuration_.getValue("sblaster", "oplmode").equals("cms")) {
						configuration_.removeValue("sblaster", "oplmode");
						configuration_.setValue("sblaster", "sbtype", "sb1");
					}
				}
				break;
			default:
				throw new RuntimeException("Cannot upgrade above generation x_20241001");
		}
	}
	
	public void switchFamily(Generation src, Generation dst) {
		// The family conversions below were written against staging_0820 as the latest Staging generation.
		// Staging 0.83 is handled by stepping through 0.82 on the way in or out.
		if (src == Generation.staging_0830) {
			downgradeOneStagingGeneration(Generation.staging_0830);
			src = Generation.staging_0820;
		}
		boolean upgradeToStaging0830 = (dst == Generation.staging_0830);
		if (upgradeToStaging0830)
			dst = Generation.staging_0820;

		switchFamilyViaStaging0820(src, dst);

		if (upgradeToStaging0830)
			upgradeOneStagingGeneration(Generation.staging_0820);
	}

	private void switchFamilyViaStaging0820(Generation src, Generation dst) {
		switch (src) {
			case official_073: 
				switch (dst) {
					case official_073:
						break; // nothing to do
					case svn_r4483:
						// oplmode: svn has superset
						// oplemu: svn has superset
						break;
					case daum_20150125:
						configuration_.setValueIfAny("cpu", "cputype", "386_prefetch", "386_slow");
						configuration_.setValueIfAny("cpu", "cputype", "486", "486_slow");
						configuration_.setValueIfAny("cpu", "cputype", "pentium", "pentium_slow");
						break;
					case ece_r4482:
						// mididevice: ece has superset
						// oplmode: ece has superset
						// oplemu: ece has superset
						break;
					case staging_0820:
						configuration_.switchSetting("dosbox", "captures", "capture", "capture_dir");
						priorityToStaging(configuration_);
						configuration_.removeValueIfAny("sdl", "output", "ddraw", "overlay", "surface", "openglnb");
						configuration_.removeValueIfAny("sdl", "windowresolution", "original", "desktop");
						configuration_.setValueIfAnyOr("sdl", "aspect", "on", "off", "true");
						cputypeToStaging(configuration_);
						cyclesToStaging(configuration_);
						configuration_.removeValueIfAny("dosbox", "machine", "vgaonly");
						mixerToStaging(configuration_);
						configuration_.removeValueIfAny("midi", "mididevice", "default", "alsa", "oss", "coreaudio", "coremidi");
						// sbtype: staging has superset
						if (configuration_.hasValue("sblaster", "oplmode")) {
							if (configuration_.getValue("sblaster", "oplmode").equals("cms")) {
								configuration_.removeValue("sblaster", "oplmode");
								configuration_.setValue("sblaster", "sbtype", "sb1");
							}
						}
						configuration_.removeValueIfAny("gus", "gusbase", "280", "2a0", "2c0", "2e0", "300");
						configuration_.removeValueIfAny("gus", "gusirq", "9", "10");
						configuration_.removeValueIfAny("gus", "gusdma", "0");
						configuration_.setValueIfAnyOr("speaker", "pcspeaker", "impulse", "off", "true");
						// tandy: staging has superset
						configuration_.switchSetting("sdl", "sensitivity", "mouse", "mouse_sensitivity");
						configuration_.setValueIfAny("joystick", "joysticktype", "disabled", "none");
						break;
					case x_20241001:
						// output: x has a superset
						// scaler: x has a superset
						// aspect: x has a superset
						// machine: x has a superset
						// core: x has a superset
						configuration_.setValueIfAny("cpu", "cputype", "386_prefetch", "386_slow");
						configuration_.setValueIfAny("cpu", "cputype", "486_prefetch", "486_slow");
						configuration_.setValueIfAny("cpu", "cputype", "pentium", "pentium_slow");
						// ems: x has a superset
						// mididevice: x has a superset
						// sbtype: x has a superset
						configuration_.removeValueIfAny("sblaster", "sbbase", "300");
						// sbirq: x has a superset
						if (configuration_.hasValue("sblaster", "oplmode")) {
							if (configuration_.getValue("sblaster", "oplmode").equals("cms")) {
								configuration_.removeValue("sblaster", "oplmode");
								configuration_.setValue("sblaster", "sbtype", "sb1");
							}
						}
						// sbdma: x has a superset
						// oplemu: x has a superset
						// sbhdma: x has a superset
						// pcrate: x has a superset
						// usescancodes: x has a superset
						break;
					default: 
						throw new RuntimeException("Cannot switch from 073 to unknown generation");
				}
				break;
			case svn_r4483:
				switch (dst) {
					case svn_r4483:
						break; // nothing to do
					case official_073:
						configuration_.removeValueIfAny("sblaster", "oplmode", "opl3gold");
						configuration_.removeValueIfAny("sblaster", "oplemu", "mame");
						break;
					case daum_20150125:
						configuration_.setValueIfAny("cpu", "cputype", "386_prefetch", "386_slow");
						configuration_.setValueIfAny("cpu", "cputype", "486", "486_slow");
						configuration_.setValueIfAny("cpu", "cputype", "pentium", "pentium_slow");
						configuration_.removeValueIfAny("sblaster", "oplmode", "opl3gold");
						configuration_.removeValueIfAny("sblaster", "oplemu", "mame");
						break;
					case ece_r4482:
						// glshader: ece has the same options
						// mididevice: ece has superset
						// oplmode: ece has the same options
						// oplemu: ece has superset
						break;
					case staging_0820:
						configuration_.switchSetting("dosbox", "captures", "capture", "capture_dir");
						priorityToStaging(configuration_);
						configuration_.removeValueIfAny("sdl", "output", "ddraw", "overlay", "surface", "openglnb");
						configuration_.removeValueIfAny("sdl", "windowresolution", "original", "desktop");
						configuration_.setValueIfAnyOr("sdl", "aspect", "on", "off", "true");
						configuration_.removeValueIfAny("render", "glshader", "advinterp2x", "advinterp3x", "advmame2x", "advmame3x", "rgb2x", "rgb3x", "scan2x", "scan3x", "tv2x", "tv3x");
						configuration_.removeValueIfAny("dosbox", "machine", "vgaonly");
						cputypeToStaging(configuration_);
						cyclesToStaging(configuration_);
						mixerToStaging(configuration_);
						configuration_.removeValueIfAny("midi", "mididevice", "default", "alsa", "oss", "coreaudio", "coremidi");
						// sbtype: staging has superset
						if (configuration_.hasValue("sblaster", "oplmode")) {
							if (configuration_.getValue("sblaster", "oplmode").equals("cms")) {
								configuration_.removeValue("sblaster", "oplmode");
								configuration_.setValue("sblaster", "sbtype", "sb1");
							}
						}
						configuration_.removeValueIfAny("sblaster", "oplemu", "mame");
						configuration_.removeValueIfAny("gus", "gusbase", "280", "2a0", "2c0", "2e0", "300");
						configuration_.removeValueIfAny("gus", "gusirq", "9", "10");
						configuration_.removeValueIfAny("gus", "gusdma", "0");
						configuration_.setValueIfAnyOr("speaker", "pcspeaker", "impulse", "off", "true");
						// tandy: staging has superset
						configuration_.switchSetting("sdl", "sensitivity", "mouse", "mouse_sensitivity");
						configuration_.setValueIfAny("joystick", "joysticktype", "disabled", "none");
						break;
					case x_20241001:
						// output: x has a superset
						// scaler: x has a superset
						// aspect: x has a superset
						// glshader: x has a superset
						// machine: x has a superset
						// core: x has a superset
						configuration_.setValueIfAny("cpu", "cputype", "386_prefetch", "386_slow");
						configuration_.setValueIfAny("cpu", "cputype", "486_prefetch", "486_slow");
						configuration_.setValueIfAny("cpu", "cputype", "pentium", "pentium_slow");
						// ems: x has a superset
						// mididevice: x has a superset
						// sbtype: x has a superset
						configuration_.removeValueIfAny("sblaster", "sbbase", "300");
						// sbirq: x has a superset
						if (configuration_.hasValue("sblaster", "oplmode")) {
							if (configuration_.getValue("sblaster", "oplmode").equals("cms")) {
								configuration_.removeValue("sblaster", "oplmode");
								configuration_.setValue("sblaster", "sbtype", "sb1");
							}
						}
						// sbdma: x has a superset
						// oplemu: x has a superset
						// sbhdma: x has a superset
						// pcrate: x has a superset
						// usescancodes: x has a superset
						break;
					default: 
						throw new RuntimeException("Cannot switch from SVN to unknown generation");
				}
				break;
			case daum_20150125:
				switch (dst) {
					case official_073:
						configuration_.removeValueIfAny("sdl", "output", "openglhq", "direct3d");
						configuration_.setValueIfAny("render", "scaler", "normal3x", "normal4x", "normal5x");
						configuration_.removeValueIfAny("render", "scaler", "hardware_none", "hardware2x", "hardware3x", "hardware4x", "hardware5x", "xbrz");
						configuration_.removeValueIfAny("dosbox", "machine", "cga_mono", "amstrad");
						configuration_.setValueIfAny("cpu", "cputype", "486_slow", "486");
						configuration_.setValueIfAny("cpu", "cputype", "pentium_slow", "pentium", "pentium_mmx");
						configuration_.removeValueIfAny("midi", "mididevice", "mt32", "synth", "timidity");
						configuration_.removeValueIfAny("sblaster", "sbtype", "sb16vibra");
						configuration_.removeValueIfAny("sblaster", "oplmode", "hardware", "hardwaregb");
						break;
					case svn_r4483:
						configuration_.removeValueIfAny("sdl", "output", "openglhq", "direct3d");
						configuration_.setValueIfAny("render", "scaler", "normal3x", "normal4x", "normal5x");
						configuration_.removeValueIfAny("render", "scaler", "hardware_none", "hardware2x", "hardware3x", "hardware4x", "hardware5x", "xbrz");
						configuration_.removeValueIfAny("dosbox", "machine", "cga_mono", "amstrad");
						configuration_.setValueIfAny("cpu", "cputype", "486_slow", "486");
						configuration_.setValueIfAny("cpu", "cputype", "pentium_slow", "pentium", "pentium_mmx");
						configuration_.removeValueIfAny("midi", "mididevice", "mt32", "synth", "timidity");
						configuration_.removeValueIfAny("sblaster", "sbtype", "sb16vibra");
						configuration_.removeValueIfAny("sblaster", "oplmode", "hardware", "hardwaregb");
						break;
					case ece_r4482:
						configuration_.removeValueIfAny("sdl", "output", "openglhq", "direct3d");
						configuration_.setValueIfAny("render", "scaler", "normal3x", "normal4x", "normal5x");
						configuration_.removeValueIfAny("render", "scaler", "hardware_none", "hardware2x", "hardware3x", "hardware4x", "hardware5x", "xbrz");
						configuration_.removeValueIfAny("dosbox", "machine", "cga_mono", "amstrad");
						configuration_.setValueIfAny("cpu", "cputype", "486_slow", "486");
						configuration_.setValueIfAny("cpu", "cputype", "pentium_slow", "pentium", "pentium_mmx");
						configuration_.removeValueIfAny("midi", "mididevice", "synth", "timidity");
						configuration_.removeValueIfAny("sblaster", "sbtype", "sb16vibra");
						configuration_.removeValueIfAny("sblaster", "oplmode", "hardware", "hardwaregb");
						break;
					case staging_0820:
						configuration_.switchSetting("dosbox", "captures", "capture", "capture_dir");
						priorityToStaging(configuration_);
						configuration_.switchSetting("dos", "automount", "dosbox", "automount");
						configuration_.removeValueIfAny("sdl", "output", "ddraw", "overlay", "openglnb", "openglhq", "surface", "direct3d");
						configuration_.removeValueIfAny("sdl", "windowresolution", "original", "desktop");
						configuration_.setValueIfAny("render", "scaler", "normal3x", "normal4x", "normal5x");
						configuration_.removeValueIfAny("render", "scaler", "hardware_none", "hardware2x", "hardware3x", "hardware4x", "hardware5x", "xbrz");
						configuration_.setValueIfAnyOr("sdl", "aspect", "on", "off", "true");
						configuration_.switchSetting("vsync", "vsyncmode", "sdl", "vsync");
						configuration_.removeValueIfAny("dosbox", "machine", "vgaonly", "amstrad");
						cyclesToStaging(configuration_);
						mixerToStaging(configuration_);
						configuration_.removeValueIfAny("midi", "mididevice", "alsa", "default", "coreaudio", "coremidi", "oss", "synth", "timidity");
						configuration_.removeValueIfAny("sblaster", "sbtype", "sb16vibra");
						configuration_.removeValueIfAny("sblaster", "oplmode", "hardware", "hardwaregb");
						configuration_.removeValueIfAny("gus", "gusbase", "280", "2a0", "2c0", "2e0", "300");
						configuration_.removeValueIfAny("gus", "gusirq", "9", "10");
						configuration_.removeValueIfAny("gus", "gusdma", "0");
						configuration_.setValueIfAnyOr("speaker", "pcspeaker", "impulse", "off", "true");
						configuration_.switchSetting("innova", "sidbase", "innovation", "sidport");
						configuration_.switchSetting("sdl", "sensitivity", "mouse", "mouse_sensitivity");
						configuration_.setValueIfAny("joystick", "joysticktype", "disabled", "none");
						configuration_.switchSetting("ne2000", "ne2000", "ethernet", "ne2000");
						configuration_.switchSetting("ne2000", "nicbase", "ethernet", "nicbase");
						configuration_.switchSetting("ne2000", "nicirq", "ethernet", "nicirq");
						configuration_.switchSetting("ne2000", "macaddr", "ethernet", "macaddr");
						break;
					case x_20241001:
						configuration_.switchSetting("pci", "voodoo", "voodoo", "voodoo_card");
						configuration_.switchSetting("glide", "glide", "voodoo", "glide");
						configuration_.switchSetting("glide", "lfb", "voodoo", "lfb");
						configuration_.switchSetting("glide", "splash", "voodoo", "splash");
						configuration_.removeValueIfAny("sblaster", "sbbase", "300");
						if (configuration_.hasValue("sblaster", "oplmode")) {
							if (configuration_.getValue("sblaster", "oplmode").equals("cms")) {
								configuration_.removeValue("sblaster", "oplmode");
								configuration_.setValue("sblaster", "sbtype", "sb1");
							}
						}
						break;
					default:
						throw new RuntimeException("Cannot switch from DAUM to unknown generation");
				}
				break;
			case ece_r4482:
				switch (dst) {
					case ece_r4482:
						break; // nothing to do
					case official_073:
						configuration_.removeValueIfAny("midi", "mididevice", "fluidsynth", "mt32");
						configuration_.removeValueIfAny("sblaster", "oplmode", "opl3gold");
						configuration_.removeValueIfAny("sblaster", "oplemu", "mame", "nuked");
						break;
					case svn_r4483:
						// glshader: svn has the same options
						configuration_.removeValueIfAny("midi", "mididevice", "fluidsynth", "mt32");
						// oplmode: svn has the same options
						configuration_.removeValueIfAny("sblaster", "oplemu", "nuked");
						break;
					case daum_20150125:
						configuration_.setValueIfAny("cpu", "cputype", "386_prefetch", "386_slow");
						configuration_.setValueIfAny("cpu", "cputype", "486", "486_slow");
						configuration_.setValueIfAny("cpu", "cputype", "pentium", "pentium_slow");
						configuration_.removeValueIfAny("midi", "mididevice", "fluidsynth");
						configuration_.removeValueIfAny("sblaster", "oplmode", "opl3gold");
						configuration_.removeValueIfAny("sblaster", "oplemu", "mame", "nuked");
						break;
					case staging_0820:
						configuration_.switchSetting("dosbox", "captures", "capture", "capture_dir");
						priorityToStaging(configuration_);
						configuration_.removeValueIfAny("sdl", "output", "ddraw", "overlay", "surface", "openglnb");
						configuration_.removeValueIfAny("sdl", "windowresolution", "original", "desktop");
						configuration_.setValueIfAnyOr("sdl", "aspect", "on", "off", "true");
						configuration_.removeValueIfAny("render", "glshader", "advinterp2x", "advinterp3x", "advmame2x", "advmame3x", "rgb2x", "rgb3x", "scan2x", "scan3x", "tv2x", "tv3x");
						configuration_.removeValueIfAny("dosbox", "machine", "vgaonly");
						cputypeToStaging(configuration_);
						cyclesToStaging(configuration_);
						mixerToStaging(configuration_);
						configuration_.removeValueIfAny("midi", "mididevice", "default", "alsa", "oss", "coreaudio", "coremidi");
						configuration_.switchSetting("midi", "fluid.soundfont", "fluidsynth", "soundfont");
						configuration_.switchSetting("midi", "mt32.romdir", "mt32", "romdir");
						// sbtype: staging has superset
						if (configuration_.hasValue("sblaster", "oplmode")) {
							if (configuration_.getValue("sblaster", "oplmode").equals("cms")) {
								configuration_.removeValue("sblaster", "oplmode");
								configuration_.setValue("sblaster", "sbtype", "sb1");
							}
						}
						configuration_.removeValueIfAny("sblaster", "oplemu", "mame", "nuked");
						configuration_.removeValueIfAny("gus", "gusbase", "280", "2a0", "2c0", "2e0", "300");
						configuration_.removeValueIfAny("gus", "gusirq", "9", "10");
						configuration_.removeValueIfAny("gus", "gusdma", "0");
						configuration_.setValueIfAnyOr("speaker", "pcspeaker", "impulse", "off", "true");
						// tandy: staging has superset
						configuration_.switchSetting("sdl", "sensitivity", "mouse", "mouse_sensitivity");
						configuration_.setValueIfAny("joystick", "joysticktype", "disabled", "none");
						break;
					case x_20241001:
						// output: x has a superset
						// scaler: x has a superset
						// aspect: x has a superset
						// glshader: x has a superset
						// machine: x has a superset
						// core: x has a superset
						configuration_.setValueIfAny("cpu", "cputype", "386_prefetch", "386_slow");
						configuration_.setValueIfAny("cpu", "cputype", "486_prefetch", "486_slow");
						configuration_.setValueIfAny("cpu", "cputype", "pentium", "pentium_slow");
						// ems: x has a superset
						configuration_.switchSetting("pci", "voodoo", "voodoo", "voodoo_card");
						configuration_.switchSetting("glide", "glide", "voodoo", "glide");
						configuration_.switchSetting("glide", "lfb", "voodoo", "lfb");
						configuration_.switchSetting("glide", "splash", "voodoo", "splash");
						// mididevice: x has a superset
						// fluidsynthdriver: x has the same options
						// sbtype: x has a superset
						configuration_.removeValueIfAny("sblaster", "sbbase", "300");
						// sbirq: x has a superset
						if (configuration_.hasValue("sblaster", "oplmode")) {
							if (configuration_.getValue("sblaster", "oplmode").equals("cms")) {
								configuration_.removeValue("sblaster", "oplmode");
								configuration_.setValue("sblaster", "sbtype", "sb1");
							}
						}
						// sbdma: x has a superset
						// oplemu: x has a superset
						// sbhdma: x has a superset
						// pcrate: x has a superset
						// usescancodes: x has a superset
						break;
					default: 
						throw new RuntimeException("Cannot switch from ECE to unknown generation");
				}
				break;
			case staging_0820:
				switch (dst) {
					case staging_0820:
						break; // nothing to do
					case official_073:
						configuration_.switchSetting("capture", "capture_dir", "dosbox", "captures");
						priorityToOfficial(configuration_);
						configuration_.removeValueIfAny("sdl", "output", "texture", "texturenb");
						configuration_.removeValueIfAny("sdl", "windowresolution", "default", "small", "s", "medium", "m", "large", "l");
						configuration_.setValueIfAnyOr("render", "aspect", "true", "false", "auto", "on", "stretch");
						cputypeToOfficial(configuration_);
						cyclesToOfficial(configuration_);
						configuration_.setValueIfAny("dosbox", "machine", "cga", "cga_mono");
						mixerToOfficial(configuration_);
						configuration_.removeValueIfAny("midi", "mididevice", "auto", "fluidsynth", "mt32");
						configuration_.removeValueIfAny("sblaster", "sbtype", "ess");
						configuration_.removeValueIfAny("sblaster", "oplmode", "opl3gold", "esfm");
						if (configuration_.hasValue("sblaster", "cms")) {
							if (configuration_.getValue("sblaster", "cms").equals("on")) {
								configuration_.setValue("sblaster", "oplmode", "cms");
							}
						}
						configuration_.removeValueIfAny("gus", "gusbase", "210", "230", "250");
						configuration_.removeValueIfAny("gus", "gusirq", "2", "15");
						configuration_.setValueIfAnyOr("speaker", "pcspeaker", "true", "false", "discrete", "impulse");
						configuration_.removeValueIfAny("speaker", "tandy", "psg");
						configuration_.switchSetting("mouse", "mouse_sensitivity", "sdl", "sensitivity");
						configuration_.setValueIfAny("joystick", "joysticktype", "none", "hidden", "disabled");
						break;
					case svn_r4483:
						configuration_.switchSetting("capture", "capture_dir", "dosbox", "captures");
						priorityToOfficial(configuration_);
						configuration_.removeValueIfAny("sdl", "output", "texture", "texturenb");
						configuration_.removeValueIfAny("sdl", "windowresolution", "default", "small", "s", "medium", "m", "large", "l");
						configuration_.setValueIfAnyOr("render", "aspect", "true", "false", "auto", "on", "stretch");
						configuration_.removeValueIfAny("render", "glshader", "crt-auto", "crt-auto-machine", "crt-auto-arcade", "crt-auto-arcade-sharp");
						cputypeToOfficial(configuration_);
						cyclesToOfficial(configuration_);
						configuration_.setValueIfAny("dosbox", "machine", "cga", "cga_mono");
						mixerToOfficial(configuration_);
						configuration_.removeValueIfAny("midi", "mididevice", "auto", "fluidsynth", "mt32");
						configuration_.removeValueIfAny("sblaster", "sbtype", "ess");
						configuration_.removeValueIfAny("sblaster", "oplmode", "esfm");
						if (configuration_.hasValue("sblaster", "cms")) {
							if (configuration_.getValue("sblaster", "cms").equals("on")) {
								configuration_.setValue("sblaster", "oplmode", "cms");
							}
						}
						// oplemu: svn has a superset
						configuration_.removeValueIfAny("gus", "gusbase", "210", "230", "250");
						configuration_.removeValueIfAny("gus", "gusirq", "2", "15");
						configuration_.setValueIfAnyOr("speaker", "pcspeaker", "true", "false", "discrete", "impulse");
						configuration_.removeValueIfAny("speaker", "tandy", "psg");
						configuration_.switchSetting("mouse", "mouse_sensitivity", "sdl", "sensitivity");
						configuration_.setValueIfAny("joystick", "joysticktype", "none", "hidden", "disabled");
						break;
					case daum_20150125:
						configuration_.switchSetting("capture", "capture_dir", "dosbox", "captures");
						priorityToOfficial(configuration_);
						configuration_.switchSetting("dosbox", "automount", "dos", "automount");
						configuration_.removeValueIfAny("sdl", "output", "texture", "texturenb");
						configuration_.removeValueIfAny("sdl", "windowresolution", "default", "small", "s", "medium", "m", "large", "l");
						configuration_.setValueIfAnyOr("render", "aspect", "true", "false", "auto", "on", "stretch");
						configuration_.setValueIfAnyOr("sdl", "vsync", "on", "off", "on", "auto");
						configuration_.switchSetting("sdl", "vsync", "vsync", "vsyncmode");
						configuration_.setValueIfAny("cpu", "cputype", "386", "386_fast");
						cyclesToOfficial(configuration_);
						mixerToOfficial(configuration_);
						configuration_.removeValueIfAny("midi", "mididevice", "fluidsynth");
						configuration_.removeValueIfAny("sblaster", "sbtype", "ess");
						configuration_.removeValueIfAny("sblaster", "oplmode", "opl3gold", "esfm");
						if (configuration_.hasValue("sblaster", "cms")) {
							if (configuration_.getValue("sblaster", "cms").equals("on")) {
								configuration_.setValue("sblaster", "oplmode", "cms");
							}
						}
						configuration_.removeValueIfAny("gus", "gusbase", "210", "230", "250");
						configuration_.removeValueIfAny("gus", "gusirq", "2", "15");
						configuration_.setValueIfAnyOr("speaker", "pcspeaker", "true", "false", "discrete", "impulse");
						configuration_.removeValueIfAny("speaker", "tandy", "psg");
						configuration_.setValueIfAnyOr("speaker", "ps1audio", "on", "off", "true");
						configuration_.switchSetting("innovation", "sidport", "innova", "sidbase");
						configuration_.switchSetting("mouse", "mouse_sensitivity", "sdl", "sensitivity");
						configuration_.setValueIfAny("joystick", "joysticktype", "none", "hidden", "disabled");
						configuration_.switchSetting("ethernet", "ne2000", "ne2000", "ne2000");
						configuration_.switchSetting("ethernet", "nicbase", "ne2000", "nicbase");
						configuration_.switchSetting("ethernet", "nicirq", "ne2000", "nicirq");
						configuration_.switchSetting("ethernet", "macaddr", "ne2000", "macaddr");
						break;
					case ece_r4482:
						configuration_.switchSetting("capture", "capture_dir", "dosbox", "captures");
						priorityToOfficial(configuration_);
						configuration_.removeValueIfAny("sdl", "output", "texture", "texturenb");
						configuration_.removeValueIfAny("sdl", "windowresolution", "default", "small", "s", "medium", "m", "large", "l");
						configuration_.setValueIfAnyOr("render", "aspect", "true", "false", "auto", "on", "stretch");
						configuration_.removeValueIfAny("render", "glshader", "crt-auto", "crt-auto-machine", "crt-auto-arcade", "crt-auto-arcade-sharp");
						cputypeToOfficial(configuration_);
						cyclesToOfficial(configuration_);
						configuration_.setValueIfAny("dosbox", "machine", "cga", "cga_mono");
						mixerToOfficial(configuration_);
						configuration_.removeValueIfAny("midi", "mididevice", "auto");
						configuration_.switchSetting("fluidsynth", "soundfont", "midi", "fluid.soundfont");
						configuration_.switchSetting("mt32", "romdir", "midi", "mt32.romdir");
						configuration_.removeValueIfAny("sblaster", "sbtype", "ess");
						configuration_.removeValueIfAny("sblaster", "oplmode", "esfm");
						if (configuration_.hasValue("sblaster", "cms")) {
							if (configuration_.getValue("sblaster", "cms").equals("on")) {
								configuration_.setValue("sblaster", "oplmode", "cms");
							}
						}
						configuration_.removeValueIfAny("gus", "gusbase", "210", "230", "250");
						configuration_.removeValueIfAny("gus", "gusirq", "2", "15");
						configuration_.setValueIfAnyOr("speaker", "pcspeaker", "true", "false", "discrete", "impulse");
						configuration_.removeValueIfAny("speaker", "tandy", "psg");
						configuration_.switchSetting("mouse", "mouse_sensitivity", "sdl", "sensitivity");
						configuration_.setValueIfAny("joystick", "joysticktype", "none", "hidden", "disabled");
						break;
					case x_20241001:
						configuration_.switchSetting("capture", "capture_dir", "dosbox", "captures");
						priorityToOfficial(configuration_);
						configuration_.switchSetting("dosbox", "shell_config_shortcuts", "dos", "shell configuration as commands");
						configuration_.switchSetting("dosbox", "automount", "dos", "automount");
						configuration_.removeValueIfAny("sdl", "output", "texture", "texturenb");
						configuration_.removeValueIfAny("sdl", "windowresolution", "default", "small", "s", "medium", "m", "large", "l");
						// scaler: x has a superset
						configuration_.setValueIfAnyOr("render", "aspect", "true", "false", "auto", "on", "stretch");
						configuration_.removeValueIfAny("render", "glshader", "crt-auto", "crt-auto-machine", "crt-auto-arcade", "crt-auto-arcade-sharp");
						configuration_.setValueIfAnyOr("sdl", "vsync", "on", "off", "on", "auto");
						configuration_.switchSetting("sdl", "vsync", "vsync", "vsyncmode");
						configuration_.setValueIfAny("render", "monochrome_palette", "gray", "paperwhite");
						configuration_.switchSetting("render", "monochrome_palette", "render", "monochrome_pal");
						// dosversion: x has a superset
						// machine: x has a superset
						// core: x has a superset
						configuration_.setValueIfAny("cpu", "cputype", "386", "386_fast");
						cyclesToOfficial(configuration_);
						// ems: x has a superset
						mixerToOfficial(configuration_);
						configuration_.removeValueIfAny("midi", "mididevice", "auto");
						configuration_.switchSetting("fluidsynth", "soundfont", "midi", "fluid.soundfont");
						configuration_.setValueIfAny("mt32", "model", "cm32l", "cm32l_102", "cm32l_100", "cm32ln_100");
						configuration_.setValueIfAny("mt32", "model", "mt32", "mt32_old", "mt32_107", "mt32_106", "mt32_105", "mt32_104", "mt32_bluer", "mt32_new", "mt32_207", "mt32_206", "mt32_204", "mt32_203");
						configuration_.switchSetting("mt32", "model", "midi", "mt32.model");
						configuration_.switchSetting("mt32", "romdir", "midi", "mt32.romdir");
						configuration_.setValueIfAny("sblaster", "sbtype", "ess1688", "ess");
						configuration_.removeValueIfAny("sblaster", "sbbase", "300");
						// sbirq: x has a superset
						// sbdma: x has a superset
						// oplemu: x has a superset
						// sbhdma: x has a superset
						// oplmode: x has a superset
						// sbcms: x has the same options
						configuration_.removeValueIfAny("gus", "gusbase", "210", "230", "250");
						configuration_.removeValueIfAny("gus", "gusirq", "2", "15");
						configuration_.setValueIfAnyOr("speaker", "pcspeaker", "true", "false", "discrete", "impulse");
						// pcrate: x has a superset
						configuration_.removeValueIfAny("speaker", "tandy", "psg");
						configuration_.setValueIfAnyOr("speaker", "ps1audio", "on", "off", "true");
						// innovaport: x has a superset
						// usescancodes: x has a superset
						configuration_.switchSetting("innovation", "sidport", "innova", "sidbase");
						configuration_.switchSetting("mouse", "mouse_sensitivity", "sdl", "sensitivity");
						configuration_.setValueIfAny("joystick", "joysticktype", "none", "hidden", "disabled");
						configuration_.switchSetting("ethernet", "ne2000", "ne2000", "ne2000");
						configuration_.switchSetting("ethernet", "nicbase", "ne2000", "nicbase");
						configuration_.switchSetting("ethernet", "nicirq", "ne2000", "nicirq");
						configuration_.switchSetting("ethernet", "macaddr", "ne2000", "macaddr");
						break;
					default:
						throw new RuntimeException("Cannot switch from Staging to unknown generation");
				}
				break;
			case x_20241001:
				switch (dst) {
					case x_20241001:
						break; // nothing to do
					case official_073:
						configuration_.removeValueIfAny("sdl", "output", "default", "ttf", "openglhq", "openglpp", "direct3d");
						configuration_.setValueIfAny("render", "scaler", "normal3x", "normal4x", "normal5x");
						configuration_.removeValueIfAny("render", "scaler", "gray", "gray2x", "hardware_none", "hardware2x", "hardware3x", "hardware4x", "hardware5x", "xbrz", "xbrz_bilinear");
						configuration_.setValueIfAnyOr("render", "aspect", "true", "false", "true", "1", "yes", "nearest", "bilinear");
						configuration_.setValueIfAny("dosbox", "machine", "hercules", "mda", "hercules_plus", "hercules_incolor", "hercules_color");
						configuration_.setValueIfAny("dosbox", "machine", "cga", "cga_mono", "cga_rgb", "cga_composite", "cga_composite2");
						configuration_.setValueIfAny("dosbox", "machine", "pcjr", "pcjr_composite", "pcjr_composite2");
						configuration_.setValueIfAny("dosbox", "machine", "vesa_oldvbe", "vesa_oldvbe10");
						configuration_.removeValueIfAny("dosbox", "machine", "amstrad", "jega", "ega200", "mcga", "svga_s386c928", "svga_s3vision864", "svga_s3vision868", "svga_s3vision964", "svga_s3vision968", "svga_s3trio32", "svga_s3trio64", "svga_s3trio64v+", "svga_s3virge", "svga_s3virgevx", "pc98", "pc9801", "pc9821", "svga_ati_egavgawonder", "svga_ati_vgawonder", "svga_ati_vgawonderplus", "svga_ati_vgawonderxl", "svga_ati_vgawonderxl24", "svga_ati_mach8", "svga_ati_mach32", "svga_ati_mach64", "fm_towns");
						configuration_.setValueIfAny("cpu", "core", "dynamic", "dynamic_x86", "dynamic_nodhfpu", "dynamic_rec");
						configuration_.setValueIfAny("cpu", "cputype", "486_slow", "486old", "486old_prefetch", "486", "486_prefetch");
						configuration_.removeValueIfAny("cpu", "cputype", "8086", "8086_prefetch", "80186", "80186_prefetch", "286", "286_prefetch", "pentium pentium_mmx", "ppro_slow", "pentium_ii", "pentium_iii", "experimental");
						configuration_.setValueIfAny("dos", "ems", "true", "1");
						configuration_.setValueIfAny("dos", "ems", "false", "0");
						configuration_.removeValueIfAny("midi", "mididevice", "mt32", "fluidsynth", "synth", "timidity");
						configuration_.removeValueIfAny("sblaster", "sbtype", "sb16vibra", "ess688", "ess1688", "reveal_sc400");
						configuration_.removeValueIfAny("sblaster", "sbbase", "d2", "d4", "d6", "d8", "da", "dc", "de");
						configuration_.removeValueIfAny("sblaster", "irq", "-1", "0");
						configuration_.setValueIfAny("sblaster", "oplmode", "opl3", "opl3gold");
						configuration_.removeValueIfAny("sblaster", "oplmode", "hardware", "hardwaregb", "esfm");
						if (configuration_.hasValue("sblaster", "cms")) {
							if (configuration_.getValue("sblaster", "cms").equals("on")) {
								configuration_.setValue("sblaster", "oplmode", "cms");
							}
						}
						configuration_.removeValueIfAny("sblaster", "dma", "-1", "0");
						configuration_.removeValueIfAny("sblaster", "oplemu", "mame", "nuked", "opl2board", "opl3duoboard", "retrowave_opl3", "esfmu");
						configuration_.removeValueIfAny("sblaster", "hdma", "-1", "0");
						configuration_.setValueIfAny("speaker", "pcrate", "49716", "65536");
						configuration_.setValueIfAnyOr("sdl", "usescancodes", "true", "false", "true", "1", "auto");
						break;
					case svn_r4483:
						configuration_.removeValueIfAny("sdl", "output", "default", "ttf", "openglhq", "openglpp", "direct3d");
						configuration_.setValueIfAny("render", "scaler", "normal3x", "normal4x", "normal5x");
						configuration_.removeValueIfAny("render", "scaler", "gray", "gray2x", "hardware_none", "hardware2x", "hardware3x", "hardware4x", "hardware5x", "xbrz", "xbrz_bilinear");
						configuration_.setValueIfAnyOr("render", "aspect", "true", "false", "true", "1", "yes", "nearest", "bilinear");
						configuration_.removeValueIfAny("render", "glshader", "default");
						configuration_.setValueIfAny("dosbox", "machine", "hercules", "mda", "hercules_plus", "hercules_incolor", "hercules_color");
						configuration_.setValueIfAny("dosbox", "machine", "cga", "cga_mono", "cga_rgb", "cga_composite", "cga_composite2");
						configuration_.setValueIfAny("dosbox", "machine", "pcjr", "pcjr_composite", "pcjr_composite2");
						configuration_.setValueIfAny("dosbox", "machine", "vesa_oldvbe", "vesa_oldvbe10");
						configuration_.removeValueIfAny("dosbox", "machine", "amstrad", "jega", "ega200", "mcga", "svga_s386c928", "svga_s3vision864", "svga_s3vision868", "svga_s3vision964", "svga_s3vision968", "svga_s3trio32", "svga_s3trio64", "svga_s3trio64v+", "svga_s3virge", "svga_s3virgevx", "pc98", "pc9801", "pc9821", "svga_ati_egavgawonder", "svga_ati_vgawonder", "svga_ati_vgawonderplus", "svga_ati_vgawonderxl", "svga_ati_vgawonderxl24", "svga_ati_mach8", "svga_ati_mach32", "svga_ati_mach64", "fm_towns");
						configuration_.setValueIfAny("cpu", "core", "dynamic", "dynamic_x86", "dynamic_nodhfpu", "dynamic_rec");
						configuration_.setValueIfAny("cpu", "cputype", "486_slow", "486old", "486old_prefetch", "486", "486_prefetch");
						configuration_.removeValueIfAny("cpu", "cputype", "8086", "8086_prefetch", "80186", "80186_prefetch", "286", "286_prefetch", "pentium pentium_mmx", "ppro_slow", "pentium_ii", "pentium_iii", "experimental");
						configuration_.setValueIfAny("dos", "ems", "true", "1");
						configuration_.setValueIfAny("dos", "ems", "false", "0");
						configuration_.removeValueIfAny("midi", "mididevice", "mt32", "fluidsynth", "synth", "timidity");
						configuration_.removeValueIfAny("sblaster", "sbtype", "sb16vibra", "ess688", "ess1688", "reveal_sc400");
						configuration_.removeValueIfAny("sblaster", "sbbase", "d2", "d4", "d6", "d8", "da", "dc", "de");
						configuration_.removeValueIfAny("sblaster", "irq", "-1", "0");
						configuration_.removeValueIfAny("sblaster", "oplmode", "hardware", "hardwaregb", "esfm");
						if (configuration_.hasValue("sblaster", "cms")) {
							if (configuration_.getValue("sblaster", "cms").equals("on")) {
								configuration_.setValue("sblaster", "oplmode", "cms");
							}
						}
						configuration_.removeValueIfAny("sblaster", "dma", "-1", "0");
						configuration_.removeValueIfAny("sblaster", "oplemu", "nuked", "opl2board", "opl3duoboard", "retrowave_opl3", "esfmu");
						configuration_.removeValueIfAny("sblaster", "hdma", "-1", "0");
						configuration_.setValueIfAny("speaker", "pcrate", "49716", "65536");
						configuration_.setValueIfAnyOr("sdl", "usescancodes", "true", "false", "true", "1", "auto");
						break;
					case daum_20150125:
						configuration_.removeValueIfAny("sdl", "output", "default", "ttf", "openglhq", "openglpp");
						configuration_.setValueIfAny("render", "scaler", "xbrz", "xbrz_bilinear");
						configuration_.removeValueIfAny("render", "scaler", "gray", "gray2x");
						configuration_.setValueIfAnyOr("render", "aspect", "true", "false", "true", "1", "yes", "nearest", "bilinear");
						configuration_.setValueIfAny("dosbox", "machine", "hercules", "mda", "hercules_plus", "hercules_incolor", "hercules_color");
						configuration_.setValueIfAny("dosbox", "machine", "cga", "cga_rgb", "cga_composite", "cga_composite2");
						configuration_.setValueIfAny("dosbox", "machine", "pcjr", "pcjr_composite", "pcjr_composite2");
						configuration_.setValueIfAny("dosbox", "machine", "vesa_oldvbe", "vesa_oldvbe10");
						configuration_.removeValueIfAny("dosbox", "machine", "jega", "mcga", "svga_s386c928", "svga_s3vision864", "svga_s3vision868", "svga_s3vision964", "svga_s3vision968", "svga_s3trio32", "svga_s3trio64", "svga_s3trio64v+", "svga_s3virge", "svga_s3virgevx", "pc98", "pc9801", "pc9821", "svga_ati_egavgawonder", "svga_ati_vgawonder", "svga_ati_vgawonderplus", "svga_ati_vgawonderxl", "svga_ati_vgawonderxl24", "svga_ati_mach8", "svga_ati_mach32", "svga_ati_mach64", "fm_towns");
						configuration_.setValueIfAny("cpu", "core", "dynamic", "dynamic_x86", "dynamic_nodhfpu", "dynamic_rec");
						configuration_.setValueIfAny("cpu", "cputype", "486", "486old", "486old_prefetch", "486_prefetch");
						configuration_.removeValueIfAny("cpu", "cputype", "8086", "8086_prefetch", "80186", "80186_prefetch", "286", "286_prefetch", "ppro_slow", "pentium_ii", "pentium_iii", "experimental");
						configuration_.setValueIfAny("dos", "ems", "true", "1");
						configuration_.setValueIfAny("dos", "ems", "false", "0");
						configuration_.switchSetting("voodoo", "voodoo_card", "pci", "voodoo");
						configuration_.switchSetting("voodoo", "glide", "glide", "glide");
						configuration_.switchSetting("voodoo", "lfb", "glide", "lfb");
						configuration_.switchSetting("voodoo", "splash", "glide", "splash");
						configuration_.removeValueIfAny("midi", "mididevice", "fluidsynth");
						configuration_.removeValueIfAny("sblaster", "sbtype", "ess688", "ess1688", "reveal_sc400");
						configuration_.removeValueIfAny("sblaster", "sbbase", "d2", "d4", "d6", "d8", "da", "dc", "de");
						configuration_.removeValueIfAny("sblaster", "irq", "-1", "0");
						configuration_.removeValueIfAny("sblaster", "oplmode", "opl3gold", "esfm");
						if (configuration_.hasValue("sblaster", "cms")) {
							if (configuration_.getValue("sblaster", "cms").equals("on")) {
								configuration_.setValue("sblaster", "oplmode", "cms");
							}
						}
						configuration_.removeValueIfAny("sblaster", "dma", "-1", "0");
						configuration_.removeValueIfAny("sblaster", "oplemu", "mame", "nuked", "opl2board", "opl3duoboard", "retrowave_opl3", "esfmu");
						configuration_.removeValueIfAny("sblaster", "hdma", "-1", "0");
						configuration_.setValueIfAny("speaker", "pcrate", "49716", "65536");
						configuration_.setValueIfAnyOr("speaker", "ps1audio", "true", "false", "on");
						configuration_.setValueIfAnyOr("sdl", "usescancodes", "true", "false", "true", "1", "auto");
						break;
					case ece_r4482:
						configuration_.removeValueIfAny("sdl", "output", "default", "ttf", "openglhq", "openglpp", "direct3d");
						configuration_.setValueIfAny("render", "scaler", "normal3x", "normal4x", "normal5x");
						configuration_.removeValueIfAny("render", "scaler", "gray", "gray2x", "hardware_none", "hardware2x", "hardware3x", "hardware4x", "hardware5x", "xbrz", "xbrz_bilinear");
						configuration_.setValueIfAnyOr("render", "aspect", "true", "false", "true", "1", "yes", "nearest", "bilinear");
						configuration_.removeValueIfAny("render", "glshader", "default");
						configuration_.setValueIfAny("dosbox", "machine", "hercules", "mda", "hercules_plus", "hercules_incolor", "hercules_color");
						configuration_.setValueIfAny("dosbox", "machine", "cga", "cga_mono", "cga_rgb", "cga_composite", "cga_composite2");
						configuration_.setValueIfAny("dosbox", "machine", "pcjr", "pcjr_composite", "pcjr_composite2");
						configuration_.setValueIfAny("dosbox", "machine", "vesa_oldvbe", "vesa_oldvbe10");
						configuration_.removeValueIfAny("dosbox", "machine", "amstrad", "jega", "mcga", "svga_s386c928", "svga_s3vision864", "svga_s3vision868", "svga_s3vision964", "svga_s3vision968", "svga_s3trio32", "svga_s3trio64", "svga_s3trio64v+", "svga_s3virge", "svga_s3virgevx", "pc98", "pc9801", "pc9821", "svga_ati_egavgawonder", "svga_ati_vgawonder", "svga_ati_vgawonderplus", "svga_ati_vgawonderxl", "svga_ati_vgawonderxl24", "svga_ati_mach8", "svga_ati_mach32", "svga_ati_mach64", "fm_towns");
						configuration_.setValueIfAny("cpu", "core", "dynamic", "dynamic_x86", "dynamic_nodhfpu", "dynamic_rec");
						configuration_.setValueIfAny("cpu", "cputype", "486_slow", "486old", "486old_prefetch", "486", "486_prefetch");
						configuration_.removeValueIfAny("cpu", "cputype", "8086", "8086_prefetch", "80186", "80186_prefetch", "286", "286_prefetch", "pentium pentium_mmx", "ppro_slow", "pentium_ii", "pentium_iii", "experimental");
						configuration_.setValueIfAny("dos", "ems", "true", "1");
						configuration_.setValueIfAny("dos", "ems", "false", "0");
						configuration_.switchSetting("voodoo", "voodoo_card", "pci", "voodoo");
						configuration_.switchSetting("voodoo", "glide", "glide", "glide");
						configuration_.switchSetting("voodoo", "lfb", "glide", "lfb");
						configuration_.switchSetting("voodoo", "splash", "glide", "splash");
						configuration_.removeValueIfAny("midi", "mididevice", "synth", "timidity");
						// fluidsynthdriver: ece has the same options
						configuration_.removeValueIfAny("sblaster", "sbtype", "sb16vibra", "ess688", "ess1688", "reveal_sc400");
						configuration_.removeValueIfAny("sblaster", "sbbase", "d2", "d4", "d6", "d8", "da", "dc", "de");
						configuration_.removeValueIfAny("sblaster", "irq", "-1", "0");
						configuration_.removeValueIfAny("sblaster", "oplmode", "hardware", "hardwaregb", "esfm");
						if (configuration_.hasValue("sblaster", "cms")) {
							if (configuration_.getValue("sblaster", "cms").equals("on")) {
								configuration_.setValue("sblaster", "oplmode", "cms");
							}
						}
						configuration_.removeValueIfAny("sblaster", "dma", "-1", "0");
						configuration_.removeValueIfAny("sblaster", "oplemu", "opl2board", "opl3duoboard", "retrowave_opl3", "esfmu");
						configuration_.removeValueIfAny("sblaster", "hdma", "-1", "0");
						configuration_.setValueIfAny("speaker", "pcrate", "49716", "65536");
						configuration_.setValueIfAnyOr("sdl", "usescancodes", "true", "false", "true", "1", "auto");
						break;
					case staging_0820:
						configuration_.switchSetting("dosbox", "captures", "capture", "capture_dir");
						priorityToStaging(configuration_);
						configuration_.switchSetting("dos", "shell configuration as commands", "dosbox", "shell_config_shortcuts");
						configuration_.switchSetting("dos", "automount", "dosbox", "automount");
						configuration_.removeValueIfAny("sdl", "output", "default", "surface", "overlay", "ttf", "openglhq", "openglnb", "openglpp", "ddraw", "direct3d");
						configuration_.removeValueIfAny("sdl", "windowresolution", "original", "desktop");
						configuration_.setValueIfAny("render", "scaler", "normal3x", "normal4x", "normal5x");
						configuration_.removeValueIfAny("render", "scaler", "gray", "gray2x", "hardware_none", "hardware2x", "hardware3x", "hardware4x", "hardware5x", "xbrz", "xbrz_bilinear");
						configuration_.setValueIfAnyOr("render", "aspect", "on", "off", "true", "1", "yes", "nearest", "bilinear");
						configuration_.switchSetting("vsync", "vsyncmode", "sdl", "vsync");
						configuration_.switchSetting("render", "monochrome_pal", "render", "monochrome_palette");
						configuration_.removeValueIfAny("render", "glshader", "default", "advinterp2x", "advinterp3x", "advmame2x", "advmame3x", "rgb2x", "rgb3x", "scan2x", "scan3x", "tv2x", "tv3x");
						configuration_.setValueIfAnyOr("sdl", "vsync", "on", "off", "on", "force", "host");
						configuration_.setValueIfAny("dos", "ver", "7.1", "7.0");
						configuration_.removeValueIfAny("dos", "ver", "auto");
						configuration_.setValueIfAny("dosbox", "machine", "hercules", "mda", "hercules_plus", "hercules_incolor", "hercules_color");
						configuration_.setValueIfAny("dosbox", "machine", "cga", "cga_rgb", "cga_composite", "cga_composite2");
						configuration_.setValueIfAny("dosbox", "machine", "pcjr", "pcjr_composite", "pcjr_composite2");
						configuration_.setValueIfAny("dosbox", "machine", "vesa_oldvbe", "vesa_oldvbe10");
						configuration_.removeValueIfAny("dosbox", "machine", "vgaonly", "amstrad", "jega", "mcga", "svga_s386c928", "svga_s3vision864", "svga_s3vision868", "svga_s3vision964", "svga_s3vision968", "svga_s3trio32", "svga_s3trio64", "svga_s3trio64v+", "svga_s3virge", "svga_s3virgevx", "pc98", "pc9801", "pc9821", "svga_ati_egavgawonder", "svga_ati_vgawonder", "svga_ati_vgawonderplus", "svga_ati_vgawonderxl", "svga_ati_vgawonderxl24", "svga_ati_mach8", "svga_ati_mach32", "svga_ati_mach64", "fm_towns");
						configuration_.setValueIfAny("cpu", "core", "dynamic", "dynamic_x86", "dynamic_nodhfpu", "dynamic_rec");
						configuration_.setValueIfAny("cpu", "cputype", "486", "486old", "486old_prefetch", "486_prefetch");
						configuration_.removeValueIfAny("cpu", "cputype", "8086", "8086_prefetch", "80186", "80186_prefetch", "286", "286_prefetch", "ppro_slow", "pentium_ii", "pentium_iii", "experimental");
						cyclesToStaging(configuration_);
						configuration_.setValueIfAny("dos", "ems", "true", "1");
						configuration_.setValueIfAny("dos", "ems", "false", "0");
						configuration_.setValueIfAny("render", "monochrome_pal", "paperwhite", "gray");
						mixerToStaging(configuration_);
						configuration_.removeValueIfAny("midi", "mididevice", "alsa", "default", "coreaudio", "coremidi", "oss", "synth", "timidity");
						configuration_.switchSetting("midi", "fluid.soundfont", "fluidsynth", "soundfont");
						configuration_.switchSetting("midi", "mt32.model", "mt32", "model");
						configuration_.switchSetting("midi", "mt32.romdir", "mt32", "romdir");
						configuration_.setValueIfAny("sblaster", "sbtype", "ess", "ess688", "ess1688");
						configuration_.removeValueIfAny("sblaster", "sbtype", "sb16vibra", "reveal_sc400");
						configuration_.removeValueIfAny("sblaster", "sbbase", "d2", "d4", "d6", "d8", "da", "dc", "de");
						configuration_.removeValueIfAny("sblaster", "irq", "-1", "0");
						configuration_.removeValueIfAny("sblaster", "oplmode", "hardware", "hardwaregb");
						configuration_.removeValueIfAny("sblaster", "dma", "-1", "0");
						configuration_.removeValueIfAny("sblaster", "oplemu", "mame", "nuked", "opl2board", "opl3duoboard", "retrowave_opl3", "esfmu");
						configuration_.removeValueIfAny("sblaster", "hdma", "-1", "0");
						configuration_.removeValueIfAny("gus", "gusbase", "280", "2a0", "2c0", "2e0", "300");
						configuration_.removeValueIfAny("gus", "gusirq", "9", "10");
						configuration_.removeValueIfAny("gus", "gusdma", "0");
						configuration_.setValueIfAnyOr("speaker", "pcspeaker", "impulse", "off", "true");
						configuration_.setValueIfAny("speaker", "pcrate", "49716", "65536");
						// tandy: staging has superset
						configuration_.setValueIfAnyOr("speaker", "ps1audio", "true", "false", "on");
						configuration_.removeValueIfAny("innova", "sidbase", "220", "2e0", "300");
						configuration_.switchSetting("innova", "sidbase", "innovation", "sidport");
						configuration_.setValueIfAnyOr("sdl", "usescancodes", "true", "false", "true", "1", "auto");
						configuration_.switchSetting("sdl", "sensitivity", "mouse", "mouse_sensitivity");
						configuration_.setValueIfAny("joystick", "joysticktype", "disabled", "none");
						configuration_.switchSetting("ne2000", "ne2000", "ethernet", "ne2000");
						configuration_.switchSetting("ne2000", "nicbase", "ethernet", "nicbase");
						configuration_.switchSetting("ne2000", "nicirq", "ethernet", "nicirq");
						configuration_.switchSetting("ne2000", "macaddr", "ethernet", "macaddr");
						break;
					default: 
						throw new RuntimeException("Cannot switch from DOSBox-X to unknown generation");
				}
				break;
			default:
				throw new RuntimeException("Cannot switch generation, invalid src gen");
		}
	}
}
