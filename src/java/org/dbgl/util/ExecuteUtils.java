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
package org.dbgl.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.filechooser.FileSystemView;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.gui.DosboxStatusWindow;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.FileLocation;
import org.dbgl.model.NativeCommand;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.TemplateProfileBase;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.factory.TemplateFactory;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.eclipse.swt.widgets.Display;


public class ExecuteUtils {

	public enum ProfileRunMode {
		NORMAL, SETUP, ALT1, ALT2, INSTALLER
	}

	private static class ConfigurableExecutor extends Thread {

		private final TemplateProfileBase templateOrProfile_;
		private final DosboxVersion dosboxVersion_;
		private final File conf_;
		private final Display display_;
		private final int playTimeProfileId_;
		private File saveDir_;

		/** alpha 48: DOSBox-X keeps the saved states of this start in this folder (-savedir), null: where it keeps them by itself */
		ConfigurableExecutor saveDir(File saveDir) {
			saveDir_ = saveDir;
			return this;
		}

		private String[] withSaveDir(String[] args) {
			if (saveDir_ == null)
				return args;
			try {
				java.nio.file.Files.createDirectories(saveDir_.toPath());
			} catch (IOException e) {
				return args; // DOSBox-X then uses its own folder
			}
			String[] result = java.util.Arrays.copyOf(args, args.length + 2);
			result[args.length] = "-savedir";
			result[args.length + 1] = saveDir_.getPath();
			return result;
		}

		public ConfigurableExecutor(TemplateProfileBase templateOrProfile, DosboxVersion dosboxVersion, File conf, Display display) {
			this(templateOrProfile, dosboxVersion, conf, display, -1);
		}

		/** @param playTimeProfileId the profile to add the time DOSBox ran to, or -1 */
		public ConfigurableExecutor(TemplateProfileBase templateOrProfile, DosboxVersion dosboxVersion, File conf, Display display, int playTimeProfileId) {
			templateOrProfile_ = templateOrProfile;
			dosboxVersion_ = dosboxVersion;
			conf_ = conf;
			display_ = display;
			playTimeProfileId_ = playTimeProfileId;
		}

		@Override
		public void run() {
			LINK_PROFILE.set(templateOrProfile_ instanceof Profile p ? p.getId(): -1);
			LINK_TITLE.set(templateOrProfile_ instanceof Profile p ? p.getTitle(): "");
			try {
				for (NativeCommand cmd: templateOrProfile_.getNativeCommands()) {
					if (cmd.isDosboxCommand()) {
						long start = System.currentTimeMillis();
						long startNanos = System.nanoTime(); // the duration not changed when the clock is set while playing
						doRunDosbox(dosboxVersion_, withSaveDir(new String[] {"-conf", conf_.getPath()}), false, dosboxVersion_.getCwd(), true);
						registerPlayTime(playTimeProfileId_, start, (System.nanoTime() - startNanos) / 1_000_000L);
					} else {
						execute(cmd.getCanonicalCommandAndParameters(), cmd.getCanonicalCwd(), cmd.isWaitFor());
					}
				}
			} catch (IOException e) {
				if (!display_.isDisposed()) {
					display_.syncExec(() -> Mess_.on(display_.getActiveShell()).exception(e).warning());
				}
			}
		}
	}

	/** the profile DOSBox is started for in this thread, so the status window can link its messages to the profile's settings */
	private static final ThreadLocal<Integer> LINK_PROFILE = ThreadLocal.withInitial(() -> -1);
	/** its title, for the folder of its DOSBox logs (see {@link DosboxLog}) */
	private static final ThreadLocal<String> LINK_TITLE = ThreadLocal.withInitial(() -> "");

	/** told about the play time added to a profile (profile id, total seconds), on the UI thread */
	private static volatile java.util.function.IntConsumer playTimeListener_;

	public static void setPlayTimeListener(java.util.function.IntConsumer listener) {
		playTimeListener_ = listener;
	}

	/** add the time DOSBox ran to the profile; very short runs are failed starts and don't count */
	private static void registerPlayTime(int profileId, long start, long millis) {
		if ((profileId < 0) || (millis < DosboxStatusWindow.QUICK_EXIT_MILLIS))
			return;
		try {
			new org.dbgl.model.repository.ProfileRepository().addPlayTime(profileId, start, millis / 1000);
			java.util.function.IntConsumer listener = playTimeListener_;
			Display display = Display.getDefault();
			if ((listener != null) && !display.isDisposed())
				display.asyncExec(() -> listener.accept(profileId));
		} catch (java.sql.SQLException e) {
			e.printStackTrace();
		}
	}

	private static class StreamGobbler extends Thread {

		private final InputStream istream_;
		private final String logType_;
		private final DosboxStatusWindow statusWindow_;
		private final List<String> collector_;
		private final DosboxLog log_;

		public StreamGobbler(InputStream istream, String logType) {
			this(istream, logType, null, null, null);
		}

		public StreamGobbler(InputStream istream, String logType, DosboxStatusWindow statusWindow, List<String> collector, DosboxLog log) {
			istream_ = istream;
			logType_ = logType;
			statusWindow_ = statusWindow;
			collector_ = collector;
			log_ = log;
		}

		@Override
		public void run() {
			try (BufferedReader bReader = new BufferedReader(new InputStreamReader(istream_))) {
				String line = null;
				while ((line = bReader.readLine()) != null) {
					System.out.println(logType_ + "> " + line);
					if (statusWindow_ != null)
						statusWindow_.append(line);
					if (log_ != null)
						log_.line(line);
					if (collector_ != null) {
						synchronized (collector_) {
							if (collector_.size() < MAX_COLLECTED_LINES)
								collector_.add(line);
						}
					}
				}

			} catch (IOException ioe) {
				ioe.printStackTrace();
			} finally {
				try {
					istream_.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (statusWindow_ != null)
					statusWindow_.streamClosed();
			}
		}
	}

	/**
	 * DOSBox with the profile, straight into its key mapper (alpha 48, for the controller assistant); waits until DOSBox is
	 * closed. DOSBox Staging writes the option with two dashes, the others with one.
	 */
	public static void runMapper(Profile prof) throws IOException {
		DosboxVersion v = prof.getDosboxVersion();
		GenerationAwareConfigurable.Generation gen = GenerationAwareConfigurable.determineGen(v);
		boolean staging = (gen != null) && (GenerationAwareConfigurable.familyIdx(gen) == GenerationAwareConfigurable.familyIdx(GenerationAwareConfigurable.Generation.staging_0830));
		doRunDosbox(v, new String[] {"-conf", prof.getConfigurationCanonicalFile().getPath(), staging ? "--startmapper": "-startmapper"}, false, v.getCwd(), true, false);
	}

	public static void doRunDosbox(DosboxVersion dbversion) throws IOException {
		doRunDosbox(dbversion, new String[] {}, true, dbversion.getCwd(), false);
	}

	/**
	 * Let DOSBox write its configuration file again, e.g. after the executable was replaced by a newer version, so that new
	 * settings are known. The current file is kept as .bak, and put back if DOSBox writes none.
	 */
	public static void recreateDosboxConf(DosboxVersion dbversion) throws IOException {
		File configFile = dbversion.getConfigurationCanonicalFile();
		File backup = new File(configFile.getPath() + ".bak");
		boolean existed = FilesUtils.isReadableFile(configFile);
		if (existed) {
			java.nio.file.Files.copy(configFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			java.nio.file.Files.delete(configFile.toPath());
		}
		doCreateDosboxConf(dbversion);
		if (existed && !FilesUtils.isReadableFile(configFile))
			java.nio.file.Files.copy(backup.toPath(), configFile.toPath()); // DOSBox did not write it, keep the old one
		else if (existed)
			ConfMerge.keepPrevious(backup, configFile); // the [autoexec] (e.g. mount lines) and the user's values stay
	}

	public static void doCreateDosboxConf(DosboxVersion dbversion) throws IOException {
		String quote = SystemUtils.IS_WINDOWS ? "'": "\"";
		if ((dbversion.getVersionAsInt() <= 650))
			quote = StringUtils.EMPTY;

		String[] cmds = switch (GenerationAwareConfigurable.determineGen(dbversion)) {
			case daum_20150125 -> new String[] {"-c", "config -writeconf " + quote +
				(SystemUtils.IS_LINUX ? dbversion.getConfigurationFile().getName(): dbversion.getConfigurationCanonicalFile()) + quote, "-c", "exit"};
			// 0.83 dropped the early-exit protection of 0.82.1/0.82.2, so the plain 'exit' works again
			case staging_0810, staging_0830 -> new String[] {"-noprimaryconf", "-c", "config -writeconf " + quote + dbversion.getConfigurationCanonicalFile() + quote, "-c", "exit"};
			case staging_0820 -> 
				dbversion.getVersion().equals("0.82.0")
					? new String[] {"-noprimaryconf", "-c", "config -writeconf " + quote + dbversion.getConfigurationCanonicalFile() + quote, "-c", "exit"}
					: new String[] {"-noprimaryconf", "-c", "config -writeconf " + quote + dbversion.getConfigurationCanonicalFile() + quote, "-c", "exit", "exit.exe"};
			case x_08220 -> new String[] {"-c", "config -all -writeconf " + quote + dbversion.getConfigurationCanonicalFile() + quote, "-c", "exit"};
			// -all: without it DOSBox-X only writes the common settings, and DBGL would not know the others
			case x_20240301, x_20241001 -> new String[] {"-nopromptfolder", "-c", "config -writeconf -all " + dbversion.getConfigurationCanonicalFile(), "-c", "exit"};
			default -> new String[] {"-c", "config -writeconf " + quote + dbversion.getConfigurationCanonicalFile() + quote, "-c", "exit"};
		};

		doRunDosbox(dbversion, cmds, false, dbversion.getCwd(), true, false); // no status window when just creating the config
	}

	public static void doRunTemplate(Template template, Display display) throws IOException {
		Template preparedTemplate = TemplateFactory.createCopy(template);
		preparedTemplate.loadConfigurationData(TextService.getInstance(), template.getConfigurationString(), template.getConfigurationCanonicalFile());
		preparedTemplate.getConfiguration().setCharset(template.getConfiguration().getCharset());
		preparedTemplate.getConfiguration().setFileLocation(FileLocationService.getInstance().getTmpConfLocation());
		preparedTemplate.saveConfiguration(true);
		new ConfigurableExecutor(preparedTemplate, preparedTemplate.getDosboxVersion(), preparedTemplate.getConfigurationCanonicalFile(), display).start();
	}

	/**
	 * Start a profile or template once as it is in its dialog right now, e.g. to try a shader, without saving anything: it
	 * runs from a temporary copy of its configuration, and a profile run is not counted.
	 */
	public static void doTryRun(TemplateProfileBase configurable, Display display) throws IOException {
		if (configurable instanceof Template template) {
			doRunTemplate(template, display);
			return;
		}
		Profile prof = (Profile)configurable;
		Profile tryProfile = ProfileFactory.createCopy(prof);
		tryProfile.loadConfigurationData(TextService.getInstance(), prof.getConfigurationString(), prof.getConfigurationCanonicalFile());
		tryProfile.getConfiguration().setCharset(prof.getConfiguration().getCharset());
		tryProfile.getConfiguration().setFileLocation(FileLocationService.getInstance().getTmpConfLocation());
		tryProfile.saveConfiguration(false);
		new ConfigurableExecutor(tryProfile, prof.getDosboxVersion(), tryProfile.getConfigurationCanonicalFile(), display, -1).start();
	}

	/**
	 * Names in the [autoexec] DOS can't use, which DOSBox gets as short names from a temporary copy. Not when the long names
	 * work as they are (DOSBox-X with long file names on), and not when a "-pr" mount takes its path from the folder of the
	 * configuration file and the copy is in another folder (the game would not be found at all).
	 */
	static boolean needsShortNamesForStart(Profile prof) {
		if (!prof.getConfiguration().getAutoexec().hasLongNamesWithSpaces())
			return false;
		GenerationAwareConfigurable.Generation gen = prof.getGeneration();
		boolean dosboxX = (gen != null) && (GenerationAwareConfigurable.familyIdx(gen) == GenerationAwareConfigurable.familyIdx(GenerationAwareConfigurable.Generation.x_20241001));
		if (dosboxX && (prof.getDosboxVersion() != null) && dosboxXLongNames(prof.getCombinedConfigurationForReading()))
			return false;
		if (ProfileChecks.hasPrMount(prof)) {
			try {
				File own = prof.getConfigurationCanonicalFile().getParentFile();
				File tmp = FileLocationService.getInstance().getTmpConfLocation().getCanonicalFile().getParentFile();
				return own != null && own.getCanonicalFile().equals(tmp.getCanonicalFile());
			} catch (IOException e) {
				return false;
			}
		}
		return true;
	}

	/** DOSBox-X: [dos] lfn=true or autostart, or auto with a DOS version of 7 or newer */
	static boolean dosboxXLongNames(org.dbgl.model.conf.Configuration combined) {
		String lfn = StringUtils.defaultString(combined.getValue("dos", "lfn")).trim().toLowerCase();
		if (lfn.equals("true") || lfn.equals("autostart") || lfn.equals("1") || lfn.equals("on"))
			return true;
		if (lfn.equals("auto")) {
			String ver = StringUtils.defaultString(combined.getValue("dos", "ver")).trim();
			return ver.matches("([7-9]|[1-9][0-9]+)(\\..*)?");
		}
		return false;
	}

	public static void doRunProfile(ProfileRunMode mode, Profile prof, boolean prepareOnly, Display display) throws IOException {
		doRunProfile(mode, prof, prof.getDosboxVersion(), prepareOnly, display);
	}

	public static void doRunProfile(ProfileRunMode mode, Profile prof, DosboxVersion dosboxVersion, boolean prepareOnly, Display display) throws IOException {
		Profile preparedProfile = prof;

		if (!prepareOnly && (mode != ProfileRunMode.INSTALLER) && (display != null) && !display.isDisposed()) {
			// say which folder or image is missing, instead of DOSBox failing with a message that is hard to relate to it
			List<String> missing = prof.listMissingPaths();
			if (!missing.isEmpty() && !Mess_.on(display.getActiveShell()).key("general.confirm.missingpaths", new Object[] {String.join("\n", missing)}).confirm())
				return;
			// a PC-98 image (.hdi etc.) only works with DOSBox-X
			if (prof.needsDosboxXForPc98() && !Mess_.on(display.getActiveShell()).key("dialog.profile.pc98needsdosboxx", new Object[] {prof.getDosboxVersion().getTitle()}).confirm())
				return;
			if (SettingsService.getInstance().getBooleanValue("dosbox", "checkbeforestart")) {
				// values the DOSBox version does not know are ignored by DOSBox, often with a message that is easy to miss
				List<String[]> invalid = prof.listInvalidValues();
				if (!invalid.isEmpty()) {
					StringBuilder list = new StringBuilder();
					invalid.forEach(v -> list.append('[').append(v[0]).append("] ").append(v[1]).append('=').append(v[2]).append('\n'));
					if (!Mess_.on(display.getActiveShell()).key("general.confirm.invalidvalues", new Object[] {list.toString().trim()}).confirm())
						return;
				}
			}
		}
		// alpha 48 (wish 11): the saved games into a backup first, if switched on for this profile (page "Savegames")
		if (mode == ProfileRunMode.NORMAL && !prepareOnly)
			SavegameBackup.beforeStart(prof);
		boolean pc98Changed = prof.ensurePc98MachineForHdi(); // DOSBox-X crashes when booting an .HDI in IBM PC mode
		// "mount D game.cue" works in Staging 0.83+ only; DOSBox-X quits with "PHYSFS couldn't mount ... unsupported"
		boolean mountsChanged = prof.adaptImageMountsToDosbox();
		// names DOS can't use: written by an older DBGL version ("cd \M&M 3", "MM 3.EXE") or too long ("cd \DEATHGATE"); DOSBox
		// gets them with the short names it gives them (M&M3~1, MM3~1.EXE, DEATHG~1). Since alpha 46 only in a temporary copy for
		// this start: the file of the profile is not changed without the user knowing it ("Check profiles" offers it)
		boolean runFromCopy = (mode == ProfileRunMode.NORMAL) && !prepareOnly && (pc98Changed || mountsChanged || needsShortNamesForStart(prof));

		if (mode != ProfileRunMode.NORMAL || prepareOnly || runFromCopy) {
			preparedProfile = ProfileFactory.createCopy(prof);
			preparedProfile.loadConfigurationData(TextService.getInstance(), prof.getConfigurationString(),
				mode == ProfileRunMode.INSTALLER ? new File(StringUtils.EMPTY): prof.getConfigurationCanonicalFile());
			preparedProfile.getConfiguration().setCharset(prof.getConfiguration().getCharset()); // the same charset as the file
			switch (mode) {
				case SETUP:
					preparedProfile.setAutoexecSettings(preparedProfile.getSetupString(), preparedProfile.getSetupParams());
					break;
				case ALT1:
					preparedProfile.setAutoexecSettings(preparedProfile.getAltExeStrings()[0], preparedProfile.getAltExeParams()[0]);
					break;
				case ALT2:
					preparedProfile.setAutoexecSettings(preparedProfile.getAltExeStrings()[1], preparedProfile.getAltExeParams()[1]);
					break;
				case INSTALLER:
					preparedProfile.setAutoexecSettings(true, true);
					break;
				default:
			}
			preparedProfile.getConfiguration().setFileLocation(FileLocationService.getInstance().getTmpConfLocation());
			// DOSBox finds a relative mapper file next to the file it reads: the copy may be in another folder (alpha 48)
			File mapper = prof.getCustomMapperFile();
			if ((mapper != null) && !new File(preparedProfile.getConfiguration().getValue("sdl", "mapperfile")).isAbsolute()
					&& !mapper.getParentFile().equals(preparedProfile.getConfigurationCanonicalFile().getParentFile()))
				preparedProfile.getConfiguration().setValue("sdl", "mapperfile", mapper.getPath());
			preparedProfile.saveConfiguration(prepareOnly);
		}

		if (mode == ProfileRunMode.INSTALLER)
			doRunDosbox(dosboxVersion, new String[] {"-conf", preparedProfile.getConfigurationCanonicalFile().getPath()}, false, dosboxVersion.getCwd(), true);
		else {
			ConfigurableExecutor executor = new ConfigurableExecutor(preparedProfile, dosboxVersion, preparedProfile.getConfigurationCanonicalFile(), display,
					(!prepareOnly && (mode != ProfileRunMode.SETUP)) ? prof.getId(): -1).saveDir(usesOwnSavestates(dosboxVersion) ? savestatesDir(prof): null);
			lastStarted_ = executor;
			executor.start();
		}
	}

	private static volatile Thread lastStarted_;

	/** alpha 48: the thread of the DOSBox started last; it ends with DOSBox (after the play time is written) */
	public static Thread lastStarted() {
		return lastStarted_;
	}

	/** alpha 48: the folder of the saved states of DOSBox-X for this game: "savestates" in its folder of screenshots */
	public static File savestatesDir(Profile profile) {
		return new File(profile.getCanonicalCaptures(), "savestates");
	}

	/** alpha 48: DOSBox-X, and each game is to have its own saved states (Settings, on by default) */
	public static boolean usesOwnSavestates(DosboxVersion dosboxVersion) {
		return (dosboxVersion != null) && StringUtils.defaultString(dosboxVersion.getFamily()).toLowerCase(java.util.Locale.ROOT).contains("dosbox-x")
				&& SettingsService.getInstance().getBooleanValue("dosbox", "ownsavestates");
	}

	public static void createShortcut(Profile profile) throws IOException {
		DosboxVersion dbversion = profile.getDosboxVersion();
		String strictFilename = profile.getTitle().replaceAll("[\\/:*?\"<>|]", " ").trim();
		StringBuilder params = new StringBuilder(128);
		if (dbversion.isMultiConfig()) {
			params.append("-conf \"\"").append(dbversion.getConfigurationCanonicalFile()).append("\"\" ");
		}
		params.append("-conf \"\"").append(profile.getConfigurationCanonicalFile()).append("\"\"");
		if (SettingsService.getInstance().getBooleanValue("dosbox", "hideconsole")) {
			params.append(" -noconsole");
		}

		if (SystemUtils.IS_WINDOWS) {

			File desktopDir = FileSystemView.getFileSystemView().getHomeDirectory();
			File lnkFile = new File(desktopDir, strictFilename + ".lnk");
			File vbsFile = new FileLocation("shortcut.vbs", FileLocationService.getInstance().dataRelative()).getCanonicalFile();
			try (BufferedWriter vbsWriter = new BufferedWriter(new FileWriter(vbsFile))) {
				vbsWriter.write("Set oWS = WScript.CreateObject(\"WScript.Shell\")" + SystemUtils.EOLN);
				vbsWriter.write("Set oLink = oWS.CreateShortcut(\"" + lnkFile.getCanonicalPath() + "\")" + SystemUtils.EOLN);
				vbsWriter.write("oLink.TargetPath = \"" + dbversion.getCanonicalExecutable() + "\"" + SystemUtils.EOLN);
				vbsWriter.write("oLink.Arguments = \"" + params.toString() + "\"" + SystemUtils.EOLN);
				vbsWriter.write("oLink.Description = \"" + TextService.getInstance().get("general.shortcut.title", new Object[] {strictFilename}) + "\"" + SystemUtils.EOLN);
				vbsWriter.write("oLink.WorkingDirectory = \"" + FileLocationService.getInstance().getDosroot() + "\"" + SystemUtils.EOLN);
				vbsWriter.write("oLink.Save" + SystemUtils.EOLN);
			}
			Process proc = Runtime.getRuntime().exec(new String[] {"CSCRIPT", vbsFile.getPath()}, null, vbsFile.getParentFile());
			try (InputStream err = proc.getErrorStream(); InputStream out = proc.getInputStream()) {
				StreamGobbler errorGobbler = new StreamGobbler(err, "CSCRIPT stderr");
				StreamGobbler outputGobbler = new StreamGobbler(out, "CSCRIPT stdout");
				outputGobbler.start();
				errorGobbler.start();
				proc.waitFor();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			FilesUtils.removeFile(vbsFile);

		} else if (SystemUtils.IS_LINUX) {

			File desktopDir = determineLinuxDesktopDir();
			if (desktopDir != null) {
				File desktopFile = new File(desktopDir, strictFilename + ".desktop");
				try (BufferedWriter desktopWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(desktopFile), StandardCharsets.UTF_8))) {
					desktopWriter.write("[Desktop Entry]" + SystemUtils.EOLN);
					desktopWriter.write("Version=1.0" + SystemUtils.EOLN);
					desktopWriter.write("Type=Application" + SystemUtils.EOLN);
					desktopWriter.write("Name=" + strictFilename + SystemUtils.EOLN);
					desktopWriter.write("Comment=" + TextService.getInstance().get("general.shortcut.title", new Object[] {strictFilename}) + SystemUtils.EOLN);
					desktopWriter.write("Icon=" + new File(dbversion.getExecutable().getParent(), "dosbox.ico").getPath() + SystemUtils.EOLN);
					desktopWriter.write("TryExec=" + dbversion.getExecutable().getPath() + SystemUtils.EOLN);
					desktopWriter.write("Exec=" + dbversion.getExecutable().getPath() + " " + Strings.CS.replace(params.toString(), "\"\"", "\"") + SystemUtils.EOLN);
					desktopWriter.write("Path=" + FileLocationService.getInstance().getDosroot() + SystemUtils.EOLN);
				}
				if (!desktopFile.setExecutable(true)) {
					throw new IOException(TextService.getInstance().get("general.error.savefile", new Object[] {desktopDir.getPath()}));
				}
			}

		}
	}

	private static File determineLinuxDesktopDir() {
		try {
			Process process = Runtime.getRuntime().exec(new String[] {"xdg-user-dir", "DESKTOP"});
			try (InputStream is = process.getInputStream()) {
				process.waitFor();
				String xdgDesktopDir = IOUtils.toString(is, StandardCharsets.UTF_8);
				if (xdgDesktopDir != null) {
					xdgDesktopDir = xdgDesktopDir.replace(SystemUtils.EOLN, "");
					if (!xdgDesktopDir.isEmpty()) {
						File desktopDirFile = new File(xdgDesktopDir);
						if (desktopDirFile.exists() && desktopDirFile.isDirectory())
							return desktopDirFile;
					}
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			e.printStackTrace();
		}

		File defaultDesktopDir = new File(SystemUtils.USER_HOME, "Desktop");
		if (defaultDesktopDir.exists() && defaultDesktopDir.isDirectory())
			return defaultDesktopDir;

		return null;
	}

	private static void doRunDosbox(DosboxVersion dbversion, String[] parameters, boolean forceDBConf, File cwd, boolean waitFor) throws IOException {
		doRunDosbox(dbversion, parameters, forceDBConf, cwd, waitFor, true);
	}

	/**
	 * DOSBox Staging 0.83 no longer opens a status window (console) on Windows by itself, and DOSBox-X never did (it is a
	 * Windows GUI program that only opens a console with -console), so DBGL shows the DOSBox output itself unless the 'hide
	 * status window' option is set. DOSBox, SVN, ECE, Daum and DOSBox Staging up to 0.82 open their own console. The system
	 * property dbgl.statuswindow=true enables this on other platforms as well.
	 */
	static boolean dbglShowsStatusWindow(DosboxVersion dbversion) {
		if (SettingsService.getInstance().getBooleanValue("dosbox", "hideconsole") || dbversion.isUsingCurses() || !(SystemUtils.IS_WINDOWS || Boolean.getBoolean("dbgl.statuswindow")))
			return false;
		GenerationAwareConfigurable.Generation gen = GenerationAwareConfigurable.determineGen(dbversion);
		if ((gen == GenerationAwareConfigurable.Generation.staging_0830)
				|| ((gen != null) && (GenerationAwareConfigurable.familyIdx(gen) == GenerationAwareConfigurable.familyIdx(GenerationAwareConfigurable.Generation.x_20241001))))
			return true;
		if ((gen != null) && (GenerationAwareConfigurable.familyIdx(gen) == GenerationAwareConfigurable.familyIdx(GenerationAwareConfigurable.Generation.staging_0830))) {
			// The DOSBox version in DBGL may still say 0.82 or older while the executable was updated to 0.83 or newer, then
			// nothing at all would be shown. Ask the executable itself (the result is cached per executable file).
			DosboxVersionDetector.Result detected = DosboxVersionDetector.detectCached(dbversion.getCanonicalExecutable(), dbversion.getCwd());
			boolean newer = (detected != null) && SettingsService.STAGING_DOSBOX_FAM.title_.equals(detected.family_) && DosboxVersionDetector.isStaging083OrNewer(detected.version_);
			if (newer)
				System.out.println("DOSBox version '" + dbversion.getTitle() + "' is set to " + dbversion.getVersion() + ", but the executable is " + detected + "; showing the status window");
			return newer;
		}
		return false;
	}

	private static void doRunDosbox(DosboxVersion dbversion, String[] parameters, boolean forceDBConf, File cwd, boolean waitFor, boolean allowStatusWindow) throws IOException {
		List<String> commandItems = new ArrayList<>();
		if (dbversion.isUsingCurses()) {
			if (SystemUtils.IS_WINDOWS) {
				commandItems.add("rundll32");
				commandItems.add("SHELL32.DLL,ShellExec_RunDLL");
			} else if (SystemUtils.IS_LINUX) {
				commandItems.add("xterm");
				commandItems.add("-e");
			}
		}
		commandItems.add(dbversion.getCanonicalExecutable().getPath());
		if ((dbversion.isMultiConfig() && FilesUtils.isReadableFile(dbversion.getConfiguration().getFile())) || forceDBConf) {
			// selected default dosbox config file
			commandItems.add("-conf");
			commandItems.add(dbversion.getConfigurationCanonicalFile().getPath());
		}
		commandItems.addAll(Arrays.asList(parameters));
		if (dbversion.getExecutableParameters().length() > 0) {
			Collections.addAll(commandItems, StringUtils.split(dbversion.getExecutableParameters(), ' '));
		}
		if (SettingsService.getInstance().getBooleanValue("dosbox", "hideconsole"))
			commandItems.add("-noconsole");
		if (SystemUtils.IS_OSX && dbversion.isUsingCurses()) {
			String command = StringUtils.join(commandItems, ' ');
			commandItems.clear();
			commandItems.add("osascript");
			commandItems.add("-e");
			commandItems.add("tell application \"Terminal\" to do script \"cd '" + cwd + "'; " + command + "; exit;\"");
		}

		try {
			execute(commandItems, cwd, waitFor, allowStatusWindow && dbglShowsStatusWindow(dbversion), allowStatusWindow && !dbversion.isUsingCurses(), true);
		} catch (IOException e) {
			e.printStackTrace();
			throw new IOException(TextService.getInstance().get("general.error.startdosbox", new Object[] {StringUtils.join(commandItems, ' ')}));
		}
	}

	private static final int MAX_COLLECTED_LINES = 5000;

	private static void execute(List<String> execCommands, File cwd, boolean waitFor) throws IOException {
		execute(execCommands, cwd, waitFor, false, false, false);
	}

	/**
	 * @param showStatusWindow show DBGL's status window with the output of DOSBox
	 * @param detectFailedStart if DOSBox ends within moments without a status window, show its output afterwards, as starting
	 *                          the game most likely failed and DOSBox closed before its message could be read
	 */
	private static void execute(List<String> execCommands, File cwd, boolean waitFor, boolean showStatusWindow, boolean detectFailedStart, boolean dosbox) throws IOException {
		System.out.print(StringUtils.join(execCommands, ' '));
		ProcessBuilder pb = new ProcessBuilder(execCommands.toArray(new String[execCommands.size()]));
		pb.directory(cwd);
		Map<String, String> environment = pb.environment();
		Map<String, String> env = SettingsService.getInstance().getBooleanValue("environment", "use")
				? StringRelatedUtils.stringArrayToMap(SettingsService.getInstance().getValues("environment", "value"))
				: null;
		if (env != null) {
			environment.putAll(env);
			System.out.print(env);
		}
		System.out.println();
		long startMillis = System.currentTimeMillis();
		Process proc = pb.start();
		DosboxStatusWindow statusWindow = showStatusWindow ? DosboxStatusWindow.open(StringUtils.join(execCommands, ' ')): null;
		int linkProfile = LINK_PROFILE.get();
		if (statusWindow != null)
			statusWindow.setProfileId(linkProfile);
		List<String> collected = (statusWindow == null) && detectFailedStart && SettingsService.getInstance().getBooleanValue("dosbox", "detectfailedstart") ? new ArrayList<>(): null;
		if (statusWindow != null)
			proc.onExit().thenAccept(p -> statusWindow.processEnded(p.exitValue()));
		try {
			// the output of DOSBox into a log file, for the profiles that want that
			DosboxLog log = dosbox ? DosboxLog.open(linkProfile, LINK_TITLE.get(), StringUtils.join(execCommands, ' ')): null;
			StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), "DOSBox stderr", statusWindow, collected, log);
			StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "DOSBox stdout", statusWindow, collected, log);
			outputGobbler.start();
			errorGobbler.start();
			if (log != null)
				proc.onExit().thenAccept(p -> {
					try {
						outputGobbler.join(3000);
						errorGobbler.join(3000);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					log.close(p.exitValue());
				});
			if (collected != null) {
				proc.onExit().thenAccept(p -> {
					long elapsed = System.currentTimeMillis() - startMillis;
					if (elapsed >= DosboxStatusWindow.QUICK_EXIT_MILLIS)
						return;
					try {
						outputGobbler.join(2000);
						errorGobbler.join(2000);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					List<String> lines;
					synchronized (collected) {
						lines = new ArrayList<>(collected);
					}
					DosboxStatusWindow.showAfterQuickExit(StringUtils.join(execCommands, ' '), lines, p.exitValue(), elapsed, linkProfile);
				});
			}
			if (waitFor)
				proc.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
