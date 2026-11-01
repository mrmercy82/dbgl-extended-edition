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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Stream;


/**
 * Decides whether the folder of a DOSBox version may be deleted together with the version, and deletes it. Refused are: a
 * drive or file system root, the user's home folder, the DBGL folder and its data, any folder that contains them, and a
 * folder that another DOSBox version uses.
 */
public final class DosboxFolderRemoval {

	/** why a folder may not be deleted, or OK */
	public enum Verdict {
		OK, MISSING, ROOT, HOME, DBGL, SHARED
	}

	private DosboxFolderRemoval() {
	}

	/**
	 * @param folder      the folder of the DOSBox version to remove
	 * @param protectedDirs DBGL's own folders (program, data, profiles, ...): neither them nor anything containing them is deleted
	 * @param otherFolders the folders of the other DOSBox versions
	 */
	public static Verdict check(File folder, Collection<File> protectedDirs, Collection<File> otherFolders) {
		if ((folder == null) || !folder.isDirectory())
			return Verdict.MISSING;
		Path p = canonical(folder);
		if ((p.getParent() == null) || (p.getNameCount() <= 1))
			return Verdict.ROOT;
		Path home = canonical(new File(System.getProperty("user.home")));
		if (home.startsWith(p))
			return Verdict.HOME;
		for (File d: protectedDirs)
			if ((d != null) && canonical(d).startsWith(p))
				return Verdict.DBGL;
		for (File o: otherFolders) {
			if (o == null)
				continue;
			Path q = canonical(o);
			if (q.startsWith(p) || p.startsWith(q))
				return Verdict.SHARED;
		}
		return Verdict.OK;
	}

	/** @return the number of files in the folder (all levels) */
	public static long countFiles(File folder) {
		try (Stream<Path> files = Files.walk(folder.toPath())) {
			return files.filter(Files::isRegularFile).count();
		} catch (IOException e) {
			return -1;
		}
	}

	/** delete the folder and everything in it */
	public static void delete(File folder) throws IOException {
		try (Stream<Path> files = Files.walk(folder.toPath())) {
			for (Path f: (Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)
				Files.delete(f);
		}
	}

	private static Path canonical(File f) {
		try {
			return f.getCanonicalFile().toPath();
		} catch (IOException e) {
			return f.getAbsoluteFile().toPath().normalize();
		}
	}
}
