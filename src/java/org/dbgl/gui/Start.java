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
package org.dbgl.gui;

import java.util.Locale;


/**
 * alpha 51: what the jar starts with. DBGL needs Java 21 (SWT 4.41 and the Nebula Gallery 1.1 are built for it). This class
 * alone is compiled for Java 8, so an older Java can still run it: it says what is needed and where to get it, instead of
 * DBGL just not appearing. With a Java that is new enough it hands over to the Launcher.
 * <p>
 * Nothing of DBGL and no newer syntax may be used here.
 */
public final class Start {

	public static final int JAVA_NEEDED = 21;
	public static final String JAVA_PAGE = "https://adoptium.net/temurin/releases/?version=21";

	private Start() {
	}

	public static void main(String[] args) throws Throwable {
		int have = feature(System.getProperty("java.specification.version"));
		if (have < JAVA_NEEDED) {
			tell(message(have, System.getProperty("java.home"), Locale.getDefault().getLanguage()));
			System.exit(1);
			return;
		}
		try {
			removeForeignSwt(libFolder(), System.getProperty("os.name"));
		} catch (Throwable t) {
			// as it is: then the SWT of the other system may be found first
		}
		try {
			Class.forName("org.dbgl.gui.Launcher").getMethod("main", String[].class).invoke(null, (Object)args);
		} catch (java.lang.reflect.InvocationTargetException e) {
			throw e.getCause() == null ? e: e.getCause();
		}
	}

	/** the folder "lib" beside dbgl.jar, null when not started from a jar */
	private static java.io.File libFolder() throws java.net.URISyntaxException {
		java.security.CodeSource cs = Start.class.getProtectionDomain().getCodeSource();
		if ((cs == null) || (cs.getLocation() == null))
			return null;
		java.io.File jar = new java.io.File(cs.getLocation().toURI());
		return jar.isFile() ? new java.io.File(jar.getParentFile(), "lib"): null;
	}

	/**
	 * alpha 51: an update package holds SWT for Windows (lib/swtwin64.jar) and for Linux (lib/swtlin64.jar), as one package is
	 * for all. dbgl.jar names both; the one of the other system must not be in the folder, or its classes could be loaded
	 * first. So, before anything of SWT is loaded: the SWT of another system goes, but only if the one of this system is there,
	 * and only these three files of DBGL by their exact names. Nothing else in the folder is touched.
	 *
	 * @return the names of the files deleted
	 */
	public static java.util.List<String> removeForeignSwt(java.io.File lib, String osName) {
		java.util.List<String> removed = new java.util.ArrayList<String>();
		String os = String.valueOf(osName).toLowerCase(Locale.ROOT);
		String own = os.startsWith("windows") ? "swtwin64.jar": os.contains("mac") ? "swtmac64.jar": (os.contains("linux") ? "swtlin64.jar": null);
		if ((lib == null) || (own == null) || !new java.io.File(lib, own).isFile())
			return removed;
		for (String name: new String[] {"swtwin64.jar", "swtlin64.jar", "swtmac64.jar"}) {
			java.io.File f = new java.io.File(lib, name);
			if (!name.equals(own) && f.isFile() && f.delete())
				removed.add(name);
		}
		return removed;
	}

	/** "1.8" is 8, "17" is 17, "21.0.2" is 21; 0 if it cannot be told */
	public static int feature(String version) {
		if (version == null)
			return 0;
		String v = version.trim();
		if (v.startsWith("1."))
			v = v.substring(2);
		int end = 0;
		while ((end < v.length()) && Character.isDigit(v.charAt(end)))
			end++;
		try {
			return end == 0 ? 0: Integer.parseInt(v.substring(0, end));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** German for "de", English for all others */
	public static String message(int have, String javaHome, String language) {
		String found = (have > 0 ? String.valueOf(have): "?") + (javaHome == null ? "": " (" + javaHome + ")");
		if ("de".equals(language))
			return "DBGL Extended Edition benötigt Java " + JAVA_NEEDED + " oder neuer.\nGefunden wurde Java " + found + ".\n\nJava " + JAVA_NEEDED
					+ " gibt es z. B. als Eclipse Temurin:\n" + JAVA_PAGE + "\n\nDanach DBGL erneut starten. Liegt im Ordner von DBGL ein Ordner \"jre\", wird unter Windows das Java darin benutzt.";
		return "DBGL Extended Edition needs Java " + JAVA_NEEDED + " or newer.\nFound: Java " + found + ".\n\nJava " + JAVA_NEEDED + " is available e.g. as Eclipse Temurin:\n" + JAVA_PAGE
				+ "\n\nThen start DBGL again. If there is a folder \"jre\" in the folder of DBGL, Windows uses the Java in it.";
	}

	/** on the console, and in a window (there is no console when started by double click) */
	private static void tell(String message) {
		System.err.println(message);
		String os = String.valueOf(System.getProperty("os.name")).toLowerCase(Locale.ROOT);
		try {
			if (os.contains("mac")) {
				// started with -XstartOnFirstThread: a window of Java itself would hang there
				new ProcessBuilder("osascript", "-e", "display dialog \"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\" with title \"DBGL Extended Edition\" buttons {\"OK\"}").start()
						.waitFor();
			} else {
				javax.swing.JOptionPane.showMessageDialog(null, message, "DBGL Extended Edition", javax.swing.JOptionPane.WARNING_MESSAGE);
			}
		} catch (Throwable t) {
			// no screen: the console has it
		}
	}
}
