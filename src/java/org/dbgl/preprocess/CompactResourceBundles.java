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
package org.dbgl.preprocess;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.dbgl.constants.Constants;


public class CompactResourceBundles {

	public static void main(String[] args) throws IOException {
		if (args.length != 1)
			throw new RuntimeException("parameter for i18n directory is missing");

		File dir = new File(args[0]);

		Map<String, String> baseResourceBundle = new TreeMap<>();
		int totalNrOfEntries = readBundle(getResourceBundleFile(dir, ""), baseResourceBundle);

		for (String lang: Constants.SUPPORTED_LANGUAGES) {
			if (lang.equals("en"))
				continue;

			Map<String, String> srcBundle = new LinkedHashMap<>();
			Map<String, String> dstBundle = new TreeMap<>();

			int entries = readBundle(getResourceBundleFile(dir, "_" + lang), srcBundle);
			if (entries != totalNrOfEntries)
				throw new RuntimeException("Invalid number of entries in language resource bundle [" + lang + ", " + entries + " vs. " + totalNrOfEntries + "], run the SyncResourceBundles utility");

			for (Entry<String, String> entry: srcBundle.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				if (!baseResourceBundle.containsKey(key))
					throw new RuntimeException("Invalid entry [" + key + "] in [" + lang + "]");
				else if (!baseResourceBundle.get(key).equals(value))
					dstBundle.put(key, value);
			}

			writeBundle(getResourceBundleFile(dir, "_" + lang), dstBundle);
		}

		writeBundle(getResourceBundleFile(dir, ""), baseResourceBundle);
	}

	private static void writeBundle(File file, Map<String, String> bundle) throws FileNotFoundException, UnsupportedEncodingException {
		try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8.name())) {
			for (Entry<String, String> entry: bundle.entrySet())
				pw.println(entry.getKey() + '=' + entry.getValue());
		}
	}

	private static int readBundle(File file, Map<String, String> bundle) throws IOException {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8.name()))) {
			String line = br.readLine();
			while (line != null) {
				line = line.trim();
				if (!line.isEmpty() && !line.startsWith("#")) {
					int idx = line.indexOf('=');
					bundle.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
				}
				line = br.readLine();
			}
		}
		return bundle.size();
	}

	private static File getResourceBundleFile(File dir, String locale) {
		return new File(dir, "MessagesBundle" + locale + ".properties");
	}
}
