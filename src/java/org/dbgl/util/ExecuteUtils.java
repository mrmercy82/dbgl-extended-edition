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

		public ConfigurableExecutor(TemplateProfileBase templateOrProfile, DosboxVersion dosboxVersion, File conf, Display display) {
			templateOrProfile_ = templateOrProfile;
			dosboxVersion_ = dosboxVersion;
			conf_ = conf;
			display_ = display;
		}

		@Override
		public void run() {
			try {
				for (NativeCommand cmd: templateOrProfile_.getNativeCommands()) {
					if (cmd.isDosboxCommand()) {
						doRunDosbox(dosboxVersion_, new String[] {"-conf", conf_.getPath()}, false, dosboxVersion_.getCwd(), true);
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

	private static class StreamGobbler extends Thread {

		private final InputStream istream_;
		private final String logType_;
		private final DosboxStatusWindow statusWindow_;
		private final List<String> collector_;

		public StreamGobbler(InputStream istream, String logType) {
			this(istream, logType, null, null);
		}

		public StreamGobbler(InputStream istream, String logType, DosboxStatusWindow statusWindow, List<String> collector) {
			istream_ = istream;
			logType_ = logType;
			statusWindow_ = statusWindow;
			collector_ = collector;
		}

		@Override
		public void run() {
			try (BufferedReader bReader = new BufferedReader(new InputStreamReader(istream_))) {
				String line = null;
				while ((line = bReader.readLine()) != null) {
					System.out.println(logType_ + "> " + line);
					if (statusWindow_ != null)
						statusWindow_.append(line);
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

	public static void doRunDosbox(DosboxVersion dbversion) throws IOException {
		doRunDosbox(dbversion, new String[] {}, true, dbversion.getCwd(), false);
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
		preparedTemplate.getConfiguration().setFileLocation(FileLocationService.getInstance().getTmpConfLocation());
		preparedTemplate.saveConfiguration(true);
		new ConfigurableExecutor(preparedTemplate, preparedTemplate.getDosboxVersion(), preparedTemplate.getConfigurationCanonicalFile(), display).start();
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
		}
		boolean pc98Changed = prof.ensurePc98MachineForHdi(); // DOSBox-X crashes when booting an .HDI in IBM PC mode
		if ((mode == ProfileRunMode.NORMAL) && !prepareOnly && (pc98Changed || prof.getConfiguration().getAutoexec().hasLongNamesWithSpaces())) {
			// written by an older DBGL version with names like "cd \M&M 3" and "MM 3.EXE", which DOS can't use; write it again
			// with the short names DOSBox gives them (M&M3~1, MM3~1.EXE)
			prof.saveConfiguration(false);
		}

		if (mode != ProfileRunMode.NORMAL || prepareOnly) {
			preparedProfile = ProfileFactory.createCopy(prof);
			preparedProfile.loadConfigurationData(TextService.getInstance(), prof.getConfigurationString(),
				mode == ProfileRunMode.INSTALLER ? new File(StringUtils.EMPTY): prof.getConfigurationCanonicalFile());
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
			preparedProfile.saveConfiguration(prepareOnly);
		}

		if (mode == ProfileRunMode.INSTALLER)
			doRunDosbox(dosboxVersion, new String[] {"-conf", preparedProfile.getConfigurationCanonicalFile().getPath()}, false, dosboxVersion.getCwd(), true);
		else
			new ConfigurableExecutor(preparedProfile, dosboxVersion, preparedProfile.getConfigurationCanonicalFile(), display).start();
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
			execute(commandItems, cwd, waitFor, allowStatusWindow && dbglShowsStatusWindow(dbversion), allowStatusWindow && !dbversion.isUsingCurses());
		} catch (IOException e) {
			e.printStackTrace();
			throw new IOException(TextService.getInstance().get("general.error.startdosbox", new Object[] {StringUtils.join(commandItems, ' ')}));
		}
	}

	private static final int MAX_COLLECTED_LINES = 5000;

	private static void execute(List<String> execCommands, File cwd, boolean waitFor) throws IOException {
		execute(execCommands, cwd, waitFor, false, false);
	}

	/**
	 * @param showStatusWindow show DBGL's status window with the output of DOSBox
	 * @param detectFailedStart if DOSBox ends within moments without a status window, show its output afterwards, as starting
	 *                          the game most likely failed and DOSBox closed before its message could be read
	 */
	private static void execute(List<String> execCommands, File cwd, boolean waitFor, boolean showStatusWindow, boolean detectFailedStart) throws IOException {
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
		List<String> collected = (statusWindow == null) && detectFailedStart && SettingsService.getInstance().getBooleanValue("dosbox", "detectfailedstart") ? new ArrayList<>(): null;
		if (statusWindow != null)
			proc.onExit().thenAccept(p -> statusWindow.processEnded(p.exitValue()));
		try {
			StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), "DOSBox stderr", statusWindow, collected);
			StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "DOSBox stdout", statusWindow, collected);
			outputGobbler.start();
			errorGobbler.start();
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
					DosboxStatusWindow.showAfterQuickExit(StringUtils.join(execCommands, ' '), lines, p.exitValue(), elapsed);
				});
			}
			if (waitFor)
				proc.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
