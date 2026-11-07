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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.filechooser.FileSystemView;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.model.FileLocation;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.fat.BlockDevice;
import org.dbgl.util.fat.FatFileSystem;
import org.dbgl.util.fat.FatLfnDirectory;
import org.dbgl.util.fat.FatLfnDirectoryEntry;
import org.dbgl.util.fat.FileDisk;
import org.dbgl.util.fat.Pc98HdiImage;
import org.dbgl.util.iso.ISO9660FileEntry;
import org.dbgl.util.iso.ISO9660FileSystem;


public class FilesUtils {

	private static final String[] CDIMAGES = {".iso", ".cue", ".bin"};
	private static final String[] EXECUTABLES = {".exe", ".com", ".bat"};
	private static final String[] EXECUTABLES_UPPERCASE = {"EXE", "COM", "BAT"};
	private static final String[] ARCHIVES = {".zip", ".7z"};
	// .hdi/.nhd: PC-98 hard disk images, .fdi/.d88: PC-98 floppy disk images
	private static final String[] FATIMAGES = {".ima", ".img", ".hdi", ".nhd", ".fdi", ".d88"};
	private static final String[] PC98_HD_IMAGES = {".hdi", ".nhd"};
	private static final String[] PC98_IMAGES = {".hdi", ".nhd", ".fdi", ".d88"};
	private static final String[] BOOTERIMAGES = {".cp2", ".dcf", ".img", ".jrc", ".td0", ".fdd", ".fdi", ".d88", ".hdi", ".nhd", ".hdm"};
	private static final String[] PICTURES = {".png", ".gif", ".jpg", ".tif", ".tiff", ".ico", ".bmp"};

	public static final String NATIVE_EXE_FILTER = SystemUtils.IS_WINDOWS ? "*.exe;*.EXE": SystemUtils.IS_OSX ? "*.app": "*";
	public static final String GLSHADER_FILTER = "*.glsl;*.GLSL";
	public static final String CNF_FILTER = "*.conf;*.CONF";
	public static final String EXE_FILTER = "*.com;*.COM;*.exe;*.EXE;*.bat;*.BAT";
	public static final String ARC_FILTER = "*.zip;*.ZIP;*.7z;*.7Z";
	public static final String DBGLZIP_FILTER = "*.dbgl.zip;*.DBGL.ZIP";
	public static final String BTR_FILTER = "*.cp2;*.CP2;*.dcf;*.DCF;*.img;*.IMG;*.jrc;*.JRC;*.td0;*.TD0;*.fdd;*.FDD;*.fdi;*.FDI;*.d88;*.D88;*.hdi;*.HDI;*.nhd;*.NHD;*.hdm;*.HDM";
	public static final String HDI_FILTER = "*.hdi;*.HDI;*.nhd;*.NHD";
	public static final String CDI_FILTER = "*.iso;*.ISO;*.cue;*.CUE";
	public static final String FATI_FILTER = "*.ima;*.IMA;*.img;*.IMG;*.fdi;*.FDI;*.d88;*.D88";
	public static final String ALL_FILTER = "*";

	public static final String XML_EXT = ".xml";
	public static final String CONF_EXT = ".conf";
	public static final String MAPPER_EXT = ".map";
	public static final String GAMEPACKARCHIVE_EXT = ".dbgl.zip";

	private static final String INVALID_FILENAME_CHARS_REGEXP = "[^a-zA-Z_0-9()]";

	private FilesUtils() {
	}

	public static final class FileComparator implements Comparator<File> {
		@Override
		public int compare(File file1, File file2) {
			return new FilenameComparator().compare(file1.getPath(), file2.getPath());
		}
	}

	public static final class FilenameComparator implements Comparator<String> {
		@Override
		public int compare(String string1, String string2) {
			int count1 = StringUtils.countMatches(string1, File.separator);
			int count2 = StringUtils.countMatches(string2, File.separator);
			return (count1 == count2) ? string1.compareTo(string2): count1 - count2;
		}
	}

	public static boolean isExistingFile(File file) {
		return file != null && file.isFile() && file.exists();
	}

	public static boolean isReadableFile(File file) {
		return file != null && file.isFile() && file.canRead();
	}

	public static boolean isExistingDirectory(File dir) {
		return dir != null && dir.isDirectory() && dir.exists();
	}

	public static boolean isWritableDirectory(File dir) {
		try {
			Files.delete(Files.createTempFile(dir.toPath(), "chkperm", null));
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static boolean isStoredOnFloppyDrive(File file) {
		if (SystemUtils.IS_OSX)
			return false; // FileSystemView doesn't work on OSX and leads to freeze on application exit

		FileSystemView fsv = FileSystemView.getFileSystemView();
		for (File f: File.listRoots()) {
			if (areRelated(f, file))
				return fsv.isFloppyDrive(f);
		}
		return false;
	}

	public static boolean isStoredOnCDRomDrive(File file) {
		if (SystemUtils.IS_OSX)
			return false; // FileSystemView doesn't work on OSX and leads to freeze on application exit

		FileSystemView fsv = FileSystemView.getFileSystemView();
		for (File f: File.listRoots()) {
			if (areRelated(f, file))
				return fsv.isDrive(f) && fsv.getSystemTypeDescription(f).toUpperCase().contains("CD");
		}
		return false;
	}

	public static boolean isZipFile(String filename) {
		return filename.toLowerCase().endsWith(ARCHIVES[0]);
	}

	public static boolean isCueSheet(String filename) {
		return filename.toLowerCase().endsWith(CDIMAGES[1]);
	}

	public static boolean isConfFile(String filename) {
		return filename.toLowerCase().endsWith(CONF_EXT);
	}

	public static boolean isGamePackArchiveFile(String filename) {
		return filename.toLowerCase().endsWith(GAMEPACKARCHIVE_EXT);
	}

	public static boolean isExecutable(String filename) {
		return Stream.of(EXECUTABLES).anyMatch(x -> filename.toLowerCase().endsWith(x));
	}

	public static boolean isArchive(String filename) {
		return Stream.of(ARCHIVES).anyMatch(x -> filename.toLowerCase().endsWith(x));
	}

	/** @return true for a file DOSBox can mount as a CD, floppy or hard disk image */
	public static boolean isDiskImage(String filename) {
		String f = filename.toLowerCase();
		return Stream.of(CDIMAGES).anyMatch(f::endsWith) || Stream.of(FATIMAGES).anyMatch(f::endsWith) || Stream.of(BOOTERIMAGES).anyMatch(f::endsWith) || f.endsWith(".vhd")
				|| f.endsWith(".chd") || f.endsWith(".ccd") || f.endsWith(".mdf");
	}

	public static boolean isFatImage(String filename) {
		return Stream.of(FATIMAGES).anyMatch(x -> filename.toLowerCase().endsWith(x));
	}

	/** PC-98 hard disk image (.hdi, .nhd), see {@link Pc98HdiImage} */
	public static boolean isPc98HdImage(String filename) {
		return Stream.of(PC98_HD_IMAGES).anyMatch(x -> filename.toLowerCase().endsWith(x));
	}

	/** PC-98 hard or floppy disk image (.hdi, .nhd, .fdi, .d88), DOSBox-X needs machine=pc98 for these */
	public static boolean isPc98Image(String filename) {
		return Stream.of(PC98_IMAGES).anyMatch(x -> filename.toLowerCase().endsWith(x));
	}

	public static boolean isBooterImage(String filename) {
		return Stream.of(BOOTERIMAGES).anyMatch(x -> filename.toLowerCase().endsWith(x));
	}

	public static boolean isCdImageFile(String filename) {
		return Stream.of(CDIMAGES).anyMatch(x -> filename.toLowerCase().endsWith(x));
	}

	public static boolean isPhysFS(String filename) {
		return Stream.of(ARCHIVES).anyMatch(x -> filename.toLowerCase().endsWith(x + ':' + File.separatorChar));
	}

	public static int fatImageIndex(String filename) {
		return Stream.of(FATIMAGES).filter(x -> filename.toLowerCase().contains(x + File.separatorChar)).mapToInt(
			x -> filename.toLowerCase().indexOf(x + File.separatorChar) + x.length()).findFirst().orElse(-1);
	}

	public static int cdImageIndex(String filename) {
		return Stream.of(CDIMAGES).filter(x -> filename.toLowerCase().contains(x + File.separatorChar)).mapToInt(
			x -> filename.toLowerCase().indexOf(x + File.separatorChar) + x.length()).findFirst().orElse(-1);
	}

	public static int physFsIndex(String filename) {
		return Stream.of(ARCHIVES).filter(x -> filename.toLowerCase().contains(x + File.separatorChar)).mapToInt(
			x -> filename.toLowerCase().indexOf(x + File.separatorChar) + x.length()).findFirst().orElse(-1);
	}

	public static String[] dosExecutables() {
		return Arrays.copyOf(EXECUTABLES, EXECUTABLES.length);
	}

	public static String[] listFileNames(List<File> files) {
		return files.stream().map(File::getName).toArray(String[]::new);
	}

	public static String[] listFileNamesWithoutExtension(List<File> files) {
		return files.stream().map(x -> FilenameUtils.removeExtension(x.getName())).toArray(String[]::new);
	}

	public static String[] listFileNames(FileLocation[] fileLocations) {
		return (fileLocations == null) ? null: Stream.of(fileLocations).map(x -> x != null ? x.getFile().getName(): null).toArray(String[]::new);
	}

	public static String[] listFilePaths(List<File> files) {
		return files.stream().map(File::getPath).toArray(String[]::new);
	}

	public static String[] listFilePaths(FileLocation[] fileLocations) {
		return (fileLocations == null) ? null: Stream.of(fileLocations).map(x -> x != null ? x.getFile().getPath(): StringUtils.EMPTY).toArray(String[]::new);
	}

	public static File[] listPictureFiles(File[] files) {
		return (files == null) ? new File[] {}: Stream.of(files).filter(f -> Stream.of(PICTURES).anyMatch(x -> f.getName().toLowerCase().endsWith(x))).sorted().toArray(File[]::new);
	}

	public static List<File> listExecutablesInDirRecursive(File dir) {
		List<File> executables = new ArrayList<>(FileUtils.listFiles(dir, EXECUTABLES_UPPERCASE, true));
		Collections.sort(executables, new FileComparator());
		return executables;
	}

	public static String[] listExecutablesInZipOrIsoOrFat(String archive) throws IOException {
		List<String> result = new ArrayList<>();
		File arcFile = new File(archive);

		if (archive.toLowerCase().endsWith(ARCHIVES[0])) { // zip
			try (ZipFile zfile = new ZipFile(arcFile)) {
				for (Enumeration<? extends ZipEntry> entries = zfile.entries(); entries.hasMoreElements();) {
					ZipEntry entry = entries.nextElement();
					String name = entry.getName();
					if (!entry.isDirectory() && isExecutable(name))
						result.add(FilenameUtils.separatorsToSystem(name));
				}
			}
		} else if (isCdImageFile(archive)) {
			try (ISO9660FileSystem iso = new ISO9660FileSystem(new File(archive))) {
				for (ISO9660FileEntry entry: iso) {
					String name = entry.getPath();
					if (!entry.isDirectory() && isExecutable(name))
						result.add(FilenameUtils.separatorsToSystem(name));
				}
			}
		} else if (isFatImage(archive)) {
			BlockDevice device = isPc98Image(archive) ? Pc98HdiImage.open(new File(archive)): new FileDisk(new File(archive));
			result.addAll(listFatExecutables(new FatFileSystem(device).getRoot(), StringUtils.EMPTY));
			device.close();
		}

		Collections.sort(result, new FilenameComparator());
		return result.toArray(new String[result.size()]);
	}

	private static List<String> listFatExecutables(FatLfnDirectory dir, String dirPath) throws IOException {
		List<String> result = new ArrayList<>();
		for (Iterator<FatLfnDirectoryEntry> entries = dir.iterator(); entries.hasNext();) {
			FatLfnDirectoryEntry entry = entries.next();
			String name = entry.getShortNameAsString();
			if (name.length() > 8)
				name = name.substring(0, 8).trim() + '.' + name.substring(8).trim();
			if (entry.isDirectory() && !name.equals(".") && !name.equals(".."))
				result.addAll(listFatExecutables(entry.getDirectory(), dirPath + name + File.separatorChar));
			else if (entry.isFile() && isExecutable(name))
				result.add(dirPath + name);
		}
		return result;
	}

	public static File[] listFileSequence(File file) {
		List<File> result = new ArrayList<>();
		result.add(file);

		int i = 1;
		String name = FilenameUtils.removeExtension(file.getName());
		String ext = FilenameUtils.getExtension(file.getName());

		if (name.endsWith(String.valueOf(i))) {
			File dir = file.getParentFile();
			if (dir != null) {
				File[] files = dir.listFiles(File::isFile);
				if (files != null) {
					String[] fileNames = listFileNames(Arrays.asList(files));
					int index;
					do {
						i++;
						String nextFileName = StringUtils.chop(name) + String.valueOf(i) + FilenameUtils.EXTENSION_SEPARATOR + ext;
						index = ArrayUtils.indexOf(fileNames, nextFileName);
						if (index >= 0)
							result.add(files[index]);
					} while (index >= 0);
				}
			}
		}

		return result.toArray(new File[result.size()]);
	}

	public static void createDir(File dir) {
		if (!dir.exists() && !dir.mkdirs())
			System.err.println(TextService.getInstance().get("general.error.createdir", new Object[] {dir}));
	}

	public static void createDirIfNonExisting(File dir) {
		if (!dir.isDirectory()) {
			System.out.println(TextService.getInstance().get("general.notice.createdir", new Object[] {dir.getPath()}));
			if (!dir.mkdirs())
				System.err.println(TextService.getInstance().get("general.error.createdir", new Object[] {dir.getPath()}));
		}
	}

	public static void copyDirIfDestinationDoesNotExist(File src, File dst) {
		try {
			if (dst != null && !isExistingDirectory(dst) && isExistingDirectory(src))
				FileUtils.copyDirectoryToDirectory(src, dst.getParentFile());
		} catch (IOException e) {
			System.err.println(TextService.getInstance().get("general.error.copydirtodir", new String[] {src.getPath(), dst.getPath()}));
		}
	}

	public static void copyFileIfDestinationDoesNotExist(File srcFile, File dstDir) {
		try {
			if (!isExistingFile(new File(dstDir, srcFile.getName())) && isExistingFile(srcFile))
				FileUtils.copyFileToDirectory(srcFile, dstDir);
		} catch (IOException e) {
			System.err.println(TextService.getInstance().get("general.error.copyfile", new String[] {srcFile.getPath(), dstDir.getPath()}));
		}
	}

	public static void setLastModified(File file, long time) {
		if (time < 0 || !file.setLastModified(time))
			System.err.println(TextService.getInstance().get("general.error.setlastmodifiedfile", new Object[] {file.getPath()}));
	}

	public static void setReadOnly(File file) {
		if (!file.setReadOnly())
			System.err.println(TextService.getInstance().get("general.error.setreadonlyfile", new Object[] {file.getPath()}));
	}

	public static void removeFile(File file) {
		try {
			if (file.isFile())
				Files.delete(file.toPath());
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println(TextService.getInstance().get("general.error.deletefile", new Object[] {file.getPath()}));
		}
	}

	public static void removeFilesInDirAndDir(File dir) {
		File[] files = dir.listFiles();
		if (files != null) {
			for (File file: files) {
				if (file.isDirectory()) {
					System.err.println(TextService.getInstance().get("general.error.dirtobedeletedcontainsdir", new Object[] {dir.getPath()}));
					return;
				}
			}
			for (File file: files)
				removeFile(file);
		}
		try {
			if (dir.isDirectory())
				Files.delete(dir.toPath());
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println(TextService.getInstance().get("general.error.deletedir", new Object[] {dir.getPath()}));
		}
	}

	public static String toUrl(File file) {
		if (file == null) 
			return null;
		try {
			return file.toPath().toUri().normalize().toString();
		} catch (Exception e) {
			try {
				String result = file.toURI().toURL().toString();
				if (result.startsWith("file:/") && !result.startsWith("file://"))
					result = result.replace("file:/", "file:///");
				return result;				
			} catch (MalformedURLException me) {
				return file.toString();
			}
		}
	}

	public static String toSafeFilename(String filename) {
		return filename.replaceAll(INVALID_FILENAME_CHARS_REGEXP, StringUtils.EMPTY);
	}

	public static String toSafeFilenameForWebImages(String filename) {
		return toSafeFilename(filename.replace(" ", "_"));
	}

	public static String toArchivePath(File archiveEntry, boolean isDirectory) {
		return isDirectory ? Strings.CS.appendIfMissing(FilenameUtils.separatorsToUnix(archiveEntry.getPath()), "/")
				: Strings.CS.removeEnd(FilenameUtils.separatorsToUnix(archiveEntry.getPath()), "/");
	}

	public static String determineFullArchiveName(String archive, String fileEntry) {
		if (isArchive(archive) || isCdImageFile(archive) || isFatImage(archive))
			return archive + File.separatorChar + fileEntry;
		return null;
	}

	/**
	 * @return true if DOS would find the program: the file itself, or, as DOS does for a program called without or with
	 *         another of these extensions, the same name with .com, .exe or .bat (e.g. "ran2.exe" missing, "ran2.bat"
	 *         there); upper and lower case do not matter, as in DOS
	 */
	public static boolean existsAsDosProgram(File file) {
		if (file.exists())
			return true;
		File dir = file.getAbsoluteFile().getParentFile();
		String[] names = dir == null ? null: dir.list();
		if (names == null)
			return false;
		String name = file.getName();
		int dot = name.lastIndexOf('.');
		String ext = dot < 0 ? "": name.substring(dot).toLowerCase();
		boolean program = ext.isEmpty() || Stream.of(EXECUTABLES).anyMatch(ext::equals);
		String base = dot < 0 ? name: name.substring(0, dot);
		for (String n: names) {
			if (n.equalsIgnoreCase(name))
				return true;
			if (program) {
				int d = n.lastIndexOf('.');
				if ((d > 0) && n.substring(0, d).equalsIgnoreCase(base) && Stream.of(EXECUTABLES).anyMatch(n.substring(d).toLowerCase()::equals))
					return true;
			}
		}
		return false;
	}

	private static final java.util.regex.Pattern SERIES_NUMBER = java.util.regex.Pattern.compile("^(.*?)(\\d{1,2})(\\D*)$");

	/**
	 * @return the images of the same series in the same folder, e.g. for "GAME_CD1.ISO" also "GAME_CD2.ISO" and "GAME_CD3.ISO",
	 *         sorted by their number (the image given included); just the image given when there is no series
	 */
	public static List<File> findImageSeries(File image) {
		File dir = image.getAbsoluteFile().getParentFile();
		java.util.regex.Matcher m = SERIES_NUMBER.matcher(image.getName());
		String[] names = dir == null ? null: dir.list();
		if (!m.matches() || (names == null))
			return List.of(image);
		String prefix = m.group(1), suffix = m.group(3);
		java.util.TreeMap<Integer, File> series = new java.util.TreeMap<>();
		for (String n: names) {
			java.util.regex.Matcher s = SERIES_NUMBER.matcher(n);
			if (s.matches() && s.group(1).equalsIgnoreCase(prefix) && s.group(3).equalsIgnoreCase(suffix) && new File(dir, n).isFile())
				series.putIfAbsent(Integer.parseInt(s.group(2)), new File(image.getParentFile(), n));
		}
		return series.size() > 1 ? new ArrayList<>(series.values()): List.of(image);
	}

	public static File determineMainFile(File file) {
		String f = file.getPath();
		int isoIdx = cdImageIndex(f);
		int pfsIdx = physFsIndex(f);
		int fatIdx = fatImageIndex(f);
		if (isoIdx != -1) {
			return new File(f.substring(0, isoIdx));
		} else if (pfsIdx != -1) {
			return new File(f.substring(0, pfsIdx));
		} else if (fatIdx != -1) {
			return new File(f.substring(0, fatIdx));
		}
		return file;
	}

	public static FileLocation concat(File basePath, FileLocation fullFilenameToAdd) {
		return new FileLocation(concat(basePath, fullFilenameToAdd.getFile()), fullFilenameToAdd.getCanonicalizer());
	}

	public static String concat(File basePath, File fullFilenameToAdd) {
		return concat(basePath.getPath(), fullFilenameToAdd.getPath());
	}

	public static String concat(File basePath, String fullFilenameToAdd) {
		return concat(basePath.getPath(), fullFilenameToAdd);
	}

	public static String concat(String basePath, String fullFilenameToAdd) {
		if (basePath.equals("."))
			return fullFilenameToAdd;
		return FilenameUtils.concat(basePath, fullFilenameToAdd);
	}

	public static String relativize(File base, File target) {
		Path pathBase = base.toPath().normalize();
		Path pathAbsolute = target.toPath().normalize();
		return pathBase.relativize(pathAbsolute).toString();
	}

	public static File makeRelativeTo(File base, File file) {
		if (!file.isAbsolute()) {
			return file;
		}
		if (file.equals(base)) {
			return new File(".");
		}
		File remainder = new File(file.getName());
		File parent = file.getParentFile();
		while (parent != null) {
			if (parent.equals(base) && parent.getPath().equals(base.getPath())) {
				return remainder;
			}
			remainder = new File(parent.getName(), remainder.getPath());
			parent = parent.getParentFile();
		}
		
		if (SettingsService.getInstance().getBooleanValue("directory", "parentscanberelative")) {
			try {
				return base.toPath().normalize().relativize(file.toPath().normalize()).toFile();
			} catch (IllegalArgumentException e) {
				// this path cannot be relativized
			}
		}
		
		return file;
	}

	public static boolean areRelated(File parent, File child) {
		//System.out.print("areRelated: " + parent + ", " + child);
		File remainder = child.getParentFile();
		while (remainder != null) {
			if (parent.equals(remainder) && parent.getPath().equals(remainder.getPath())) {
				//System.out.println(" true");
				return true;
			}
			remainder = remainder.getParentFile();
		}
		//System.out.println(" false");
		return false;
	}

	public static File migrate(File path, File from, File to) {
		return new File(concat(to, makeRelativeTo(from, path)));
	}

	public static FileLocation migrate(FileLocation path, FileLocation from, FileLocation to) {
		return new FileLocation(concat(to.getCanonicalFile(), makeRelativeTo(from.getCanonicalFile(), path.getCanonicalFile())), path.getCanonicalizer());
	}
	
	public static File removeFirstFolder(File path) {
		if (path == null)
			return null;
		
		String end = path.getName();
		while ((path = path.getParentFile()) != null) {
			if (path.getParentFile() != null)
				end = new File(path.getName(), end).getPath();
		}
		return new File(end);
	}

	public static byte[] md5(File file) throws IOException, NoSuchAlgorithmException {
		MessageDigest digester = MessageDigest.getInstance("MD5");
		try (FileInputStream fis = new FileInputStream(file)) {
			byte[] data = new byte[1024];
			int nread = 0;
			while ((nread = fis.read(data)) != -1) {
				digester.update(data, 0, nread);
			}
			return digester.digest();
		}
	}
}