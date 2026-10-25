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
import org.dbgl.constants.Constants;


public class SyncResourceBundles {

	public static void main(String[] args) throws IOException {
		if (args.length != 1)
			throw new RuntimeException("parameter for i18n directory is missing");

		File dir = new File(args[0]);

		Map<String, String> baseResourceBundle = new LinkedHashMap<>();
		readBundle(getResourceBundleFile(dir, ""), baseResourceBundle);

		for (String lang: Constants.SUPPORTED_LANGUAGES) {
			if (lang.equals("en"))
				continue;

			Map<String, String> srcBundle = new LinkedHashMap<>();
			Map<String, String> dstBundle = new LinkedHashMap<>();

			readBundle(getResourceBundleFile(dir, "_" + lang), srcBundle);

			for (Entry<String, String> entry: baseResourceBundle.entrySet()) {
				String key = entry.getKey();
				if (key.startsWith("_")) {
					dstBundle.put(key, entry.getValue());
				} else {
					if (!srcBundle.containsKey(key))
						dstBundle.put(key, entry.getValue());
					else
						dstBundle.put(key, srcBundle.get(key));
				}
			}

			writeBundle(getResourceBundleFile(dir, "_" + lang), dstBundle);
		}
	}

	private static void writeBundle(File file, Map<String, String> bundle) throws FileNotFoundException, UnsupportedEncodingException {
		try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8.name())) {
			for (Entry<String, String> entry: bundle.entrySet()) {
				if (!entry.getKey().startsWith("_"))
					pw.print(entry.getKey() + " = ");
				pw.println(entry.getValue());
			}
		}
	}

	private static void readBundle(File file, Map<String, String> bundle) throws IOException {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8.name()))) {
			Integer nr = 0;
			String line = br.readLine();
			while (line != null) {
				line = line.trim();
				if (!line.isEmpty() && !line.startsWith("#")) {
					int idx = line.indexOf('=');
					if (idx != -1)
						bundle.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
				} else {
					bundle.put('_' + nr.toString(), line);
				}
				line = br.readLine();
				nr++;
			}
		}
	}

	private static File getResourceBundleFile(File dir, String locale) {
		return new File(dir, "MessagesBundle" + locale + ".properties");
	}
}
