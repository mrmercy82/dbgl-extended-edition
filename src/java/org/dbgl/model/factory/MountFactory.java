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
package org.dbgl.model.factory;

import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.exception.DrivelettersExhaustedException;
import org.dbgl.exception.InvalidMountstringException;
import org.dbgl.model.FileLocation;
import org.dbgl.model.conf.mount.DirMount;
import org.dbgl.model.conf.mount.ImageMount;
import org.dbgl.model.conf.mount.Mount;
import org.dbgl.model.conf.mount.OverlayMount;
import org.dbgl.model.conf.mount.PhysFsMount;
import org.dbgl.model.helper.DriveLetterHelper;
import org.dbgl.service.FileLocationService;
import org.dbgl.util.FilesUtils;


public class MountFactory {

	private static final Pattern MOUNT_PATRN = Pattern.compile(
		"^(?:mount)(?:(?:\\s+-u\\s+([a-y]))|(?:(?:\\s+([a-y]))(?:\\s+((?:\\S+)|(?:\"[^\"]+\")))(?:(?:(?:\\s+-t\\s+(dir|floppy|cdrom|iso|overlay))|(?:\\s+-label (\\S+))|(?:\\s+-(ioctl(?:_dx|_dio|_mci)?|noioctl|aspi))|(?:\\s+-freesize\\s+(\\d+))|(?:\\s+-usecd\\s+(\\d+))|(?:\\s+-size\\s+(\\d+,\\d+,\\d+,\\d+))){0,6})))\\s*$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern IMGMOUNT_PATRN = Pattern.compile(
		"^(?:imgmount)(?:\\s+([a-y0-3]))((?:\\s+(?:(?:[^\"][^-][^\\s]+)|(?:\"[^\"]+\"))\\s*)+)(?:(?:(?:\\s*-t\\s+(hdd|floppy|cdrom|iso))|(?:\\s*-fs\\s+(fat|iso|none))|(?:\\s*-size\\s+(\\d+,\\d+,\\d+,\\d+))|(?:\\s*-ide\\s+(1m|1s|2m|2s))){0,4})\\s*$",
		Pattern.CASE_INSENSITIVE);

	private MountFactory() {
	}

	/** a path of a "mount" command, until the first option */
	/** the drive, then the paths and options: an option starts with "-" outside quotes ("a - b.iso" is one path) */
	private static final Pattern MOUNT_PATHS_PATRN = Pattern.compile("^\\s*mount\\s+[a-y0-3]\\s+(.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern PATH_TOKEN_PATRN = Pattern.compile("\"([^\"]+)\"|(\\S+)");

	/**
	 * @return true for "mount" with image files only, e.g. mount D "a.iso" "b.iso" -t cdrom: DOSBox Staging 0.83 mounts images
	 *         with MOUNT (IMGMOUNT is an alias there)
	 */
	static boolean isMountOfImages(String mount) {
		Matcher m = MOUNT_PATHS_PATRN.matcher(mount);
		if (!m.matches())
			return false;
		// options only images have ("-t iso", "-fs", "-ide"; "-size" is also there for folders, "-t hdd" in DOSBox-X): an image whatever its name, e.g. GOG's
		// "gog.game"; and a file that is there with "-t cdrom" or "-t floppy" (DOSBox Staging 0.83 takes any file as an image)
		boolean imageOptions = IMAGE_OPTIONS_PATRN.matcher(mount).find();
		boolean driveOptions = DRIVE_TYPE_PATRN.matcher(mount).find();
		Matcher t = PATH_TOKEN_PATRN.matcher(m.group(1));
		int count = 0;
		while (t.find()) {
			if ((t.group(2) != null) && t.group(2).startsWith("-"))
				break; // the options
			String path = t.group(1) != null ? t.group(1): t.group(2);
			File f = new File(FilenameUtils.separatorsToSystem(path));
			if (f.isDirectory())
				return false;
			if (!FilesUtils.isDiskImage(path) && !imageOptions && !(driveOptions && f.isAbsolute() && f.isFile()))
				return false;
			count++;
		}
		return count > 0;
	}

	private static final Pattern IMAGE_OPTIONS_PATRN = Pattern.compile("\\s-(?:t\\s+iso\\b|fs\\s|ide\\b)", Pattern.CASE_INSENSITIVE);
	private static final Pattern DRIVE_TYPE_PATRN = Pattern.compile("\\s-t\\s+(?:cdrom|floppy)\\b", Pattern.CASE_INSENSITIVE);

	/**
	 * Read a mount command. Parameters DBGL has no field for are kept as {@link Mount#getExtraOptions()}, and the line is
	 * written again exactly as long as the mount is not changed.
	 */
	public static Mount create(String mount) throws InvalidMountstringException {
		Mount result;
		try {
			result = parse(mount);
		} catch (InvalidMountstringException e) {
			result = parseWithUnknownOptions(mount);
		}
		result.rememberOriginal(mount);
		return result;
	}

	private static final Pattern TOKEN_PATRN = Pattern.compile("\"[^\"]*\"|\\S+");
	private static final java.util.Map<String, Integer> MOUNT_OPTIONS = java.util.Map.of("-u", 1, "-t", 1, "-label", 1, "-freesize", 1, "-usecd", 1, "-size", 1, "-ioctl", 0,
		"-ioctl_dx", 0, "-ioctl_dio", 0, "-ioctl_mci", 0);
	private static final java.util.Map<String, Integer> MOUNT_OPTIONS_2 = java.util.Map.of("-noioctl", 0, "-aspi", 0);
	private static final java.util.Map<String, Integer> IMGMOUNT_OPTIONS = java.util.Map.of("-t", 1, "-fs", 1, "-size", 1, "-ide", 1);

	/**
	 * A mount command with parameters DBGL does not know (e.g. "-ro"): read without them, and keep them. An unknown parameter
	 * takes the next word as its value if that comes after the drive and the path and is no parameter itself. If the value of
	 * -t is unknown too, -t and its value are kept the same way.
	 */
	private static Mount parseWithUnknownOptions(String mount) throws InvalidMountstringException {
		java.util.List<String> tokens = new java.util.ArrayList<>();
		Matcher t = TOKEN_PATRN.matcher(mount.trim());
		while (t.find())
			tokens.add(t.group());
		if (tokens.isEmpty())
			throw new InvalidMountstringException();
		String command = tokens.get(0).toLowerCase();
		boolean img = command.equals("imgmount");
		if (!img && !command.equals("mount"))
			throw new InvalidMountstringException();
		java.util.List<String> known = new java.util.ArrayList<>(java.util.List.of(tokens.get(0)));
		java.util.List<String> extra = new java.util.ArrayList<>();
		int positional = 0;
		for (int i = 1; i < tokens.size(); i++) {
			String tok = tokens.get(i);
			if (!tok.startsWith("-")) {
				known.add(tok);
				positional++;
				continue;
			}
			String name = tok.toLowerCase();
			Integer arity = img ? IMGMOUNT_OPTIONS.get(name): MOUNT_OPTIONS.getOrDefault(name, MOUNT_OPTIONS_2.get(name));
			if (arity != null) {
				known.add(tok);
				for (int k = 0; (k < arity) && (i + 1 < tokens.size()); k++)
					known.add(tokens.get(++i));
			} else {
				extra.add(tok);
				if ((positional >= 2) && (i + 1 < tokens.size()) && !tokens.get(i + 1).startsWith("-"))
					extra.add(tokens.get(++i));
			}
		}
		Mount result;
		try {
			result = parse(String.join(" ", known));
		} catch (InvalidMountstringException e) {
			// maybe a type DBGL does not know, e.g. "-t hdd" for mount: keep it too
			int at = -1;
			for (int i = 1; i < known.size() - 1; i++)
				if (known.get(i).equalsIgnoreCase("-t"))
					at = i;
			if (at < 0)
				throw e;
			extra.add(0, known.get(at + 1));
			extra.add(0, known.get(at));
			known.remove(at + 1);
			known.remove(at);
			result = parse(String.join(" ", known));
		}
		result.setExtraOptions(String.join(" ", extra));
		return result;
	}

	private static Mount parse(String mount) throws InvalidMountstringException {

		if (isMountOfImages(mount)) {
			Mount img = parse("imgmount" + mount.trim().substring("mount".length()));
			((ImageMount)img).setCommand("mount"); // written again as it was
			return img;
		}

		Matcher mountMatcher = MOUNT_PATRN.matcher(mount);
		Matcher imgmountMatcher = IMGMOUNT_PATRN.matcher(mount);

		if (mountMatcher.matches()) {

			if (mountMatcher.group(1) != null) {
				// mount -u
				Mount mnt = new Mount();
				mnt.setDrive(Character.toUpperCase(mountMatcher.group(1).charAt(0)));
				mnt.setUnmounted(true);
				return mnt;
			}

			String drive = mountMatcher.group(2);
			String mountLocation = StringUtils.strip(FilenameUtils.separatorsToSystem(mountMatcher.group(3)), "\"");
			String mountAs = mountMatcher.group(4);
			String label = mountMatcher.group(5);
			String lowlevelCD = mountMatcher.group(6);
			String freesize = mountMatcher.group(7);
			String useCD = mountMatcher.group(8);

			if (FilesUtils.isPhysFS(mountLocation)) {
				String path;
				String write;
				int colonIndex1 = mountLocation.indexOf(':');
				if (colonIndex1 == 1) {
					colonIndex1 = mountLocation.indexOf(":", colonIndex1 + 1);
				}
				int colonIndex2 = mountLocation.lastIndexOf(":");
				if (colonIndex1 == colonIndex2) {
					path = mountLocation.substring(0, colonIndex2);
					write = null;
				} else {
					path = mountLocation.substring(colonIndex1 + 1, colonIndex2);
					write = mountLocation.substring(0, colonIndex1);
				}
				return createPhysFsMount(drive, path, write, mountAs, label);
			}

			if ("overlay".equalsIgnoreCase(mountAs))
				return createOverlayMount(drive, mountLocation, mountAs, label, lowlevelCD, freesize, useCD);

			return createDirMount(drive, mountLocation, mountAs, label, lowlevelCD, freesize, useCD);

		} else if (imgmountMatcher.matches()) {

			String drive = imgmountMatcher.group(1);
			String[] paths = StringUtils.stripAll(FilenameUtils.separatorsToSystem(imgmountMatcher.group(2)).trim().split("\\s+(?=([^\"]*\"[^\"]*\")*[^\"]*$)"), "\"");
			String mountAs = imgmountMatcher.group(3);
			String fs = imgmountMatcher.group(4);
			String size = imgmountMatcher.group(5);
			String ide = imgmountMatcher.group(6);
			return createImageMount(drive, paths, mountAs, fs, size, ide);

		} else {

			throw new InvalidMountstringException();

		}
	}

	public static DirMount createDefaultNewMount(char driveletter) {
		DirMount mnt = new DirMount();
		mnt.setDrive(Character.toUpperCase(driveletter));
		mnt.setPath(".");
		if (mnt.getDrive() == 'A' || mnt.getDrive() == 'B')
			mnt.setMountAs("floppy");
		return mnt;
	}

	public static DirMount createDirMount(String drive, String path, String mountAs, String label, String lowlevelCD, String freesize, String useCD) {
		DirMount mnt = new DirMount();
		mnt.setDrive(Character.toUpperCase(drive.charAt(0)));
		mnt.setPath(path);
		mnt.setMountAs(StringUtils.defaultString(mountAs));
		mnt.setLabel(StringUtils.defaultString(label));
		mnt.setLowlevelCD(StringUtils.defaultString(lowlevelCD));
		mnt.setFreesize(StringUtils.defaultString(freesize));
		mnt.setUseCD(StringUtils.defaultString(useCD));
		return mnt;
	}

	public static OverlayMount createOverlayMount(String drive, String path, String mountAs, String label, String lowlevelCD, String freesize, String useCD) {
		OverlayMount mnt = new OverlayMount();
		mnt.setDrive(Character.toUpperCase(drive.charAt(0)));
		mnt.setPath(path);
		mnt.setMountAs(StringUtils.defaultString(mountAs));
		mnt.setLabel(StringUtils.defaultString(label));
		mnt.setLowlevelCD(StringUtils.defaultString(lowlevelCD));
		mnt.setFreesize(StringUtils.defaultString(freesize));
		mnt.setUseCD(StringUtils.defaultString(useCD));
		return mnt;
	}

	public static PhysFsMount createPhysFsMount(String drive, String path, String write, String mountAs, String label) {
		PhysFsMount mnt = new PhysFsMount();
		mnt.setDrive(Character.toUpperCase(drive.charAt(0)));
		mnt.setPath(path);
		mnt.setWrite(write);
		mnt.setMountAs(StringUtils.defaultString(mountAs));
		mnt.setLabel(StringUtils.defaultString(label));
		return mnt;
	}

	public static ImageMount createImageMount(String drive, String[] paths, String mountAs, String fs, String size, String ide) {
		ImageMount mnt = new ImageMount();
		mnt.setDrive(Character.toUpperCase(drive.charAt(0)));
		mnt.setImgPaths(paths);
		mnt.setMountAs(StringUtils.defaultString(mountAs));
		mnt.setFs(StringUtils.defaultString(fs));
		mnt.setSize(StringUtils.defaultString(size));
		mnt.setIde(StringUtils.defaultString(ide));
		return mnt;
	}

	public static Mount createUnmount(Mount mount) {
		return createUnmount(mount.getDrive());
	}

	public static Mount createUnmount(char drive) {
		Mount mnt = new Mount();
		mnt.setDrive(drive);
		mnt.setUnmounted(true);
		return mnt;
	}

	public static Mount createCopy(Mount mount) throws InvalidMountstringException {
		return create(mount.toString());
	}

	public static Mount create(boolean booter, String hostFile, Set<Character> usedDriveLetters) throws DrivelettersExhaustedException, InvalidMountstringException {
		if (booter && FilesUtils.isPc98HdImage(hostFile)) // self-booting PC-98 hard disk image: attach as first hard disk, 'boot -l c'
			return create("imgmount 2 \"" + hostFile + "\" -t hdd");
		char driveLetter = DriveLetterHelper.getFirstAvailable(booter, usedDriveLetters);
		if (FilesUtils.physFsIndex(hostFile) != -1) {
			return create("mount " + driveLetter + " \"" + hostFile.substring(0, FilesUtils.physFsIndex(hostFile)) + ":\\\"");
		} else if (FilesUtils.cdImageIndex(hostFile) != -1) {
			return create("imgmount " + driveLetter + " \"" + hostFile.substring(0, FilesUtils.cdImageIndex(hostFile)) + "\" -t cdrom");
		} else if (FilesUtils.fatImageIndex(hostFile) != -1) {
			String image = hostFile.substring(0, FilesUtils.fatImageIndex(hostFile));
			if (FilesUtils.isPc98HdImage(image)) // PC-98 hard disk image (DOSBox-X with machine=pc98 reads the geometry from the header)
				return create("imgmount " + driveLetter + " \"" + image + "\" -t hdd");
			driveLetter = DriveLetterHelper.getFirstAvailable(true, usedDriveLetters);
			return create("imgmount " + driveLetter + " \"" + image + "\" -t floppy");
		} else {
			File file = new FileLocation(hostFile, FileLocationService.getInstance().dosrootRelative()).getFile();
			File dir = file.getParentFile();
			if (dir == null)
				dir = new File(".");
			if (FilesUtils.isStoredOnFloppyDrive(file))
				return create("mount " + driveLetter + " \"" + dir + "\" -t floppy");
			else if (FilesUtils.isStoredOnCDRomDrive(file))
				return create("mount " + driveLetter + " \"" + dir + "\" -t cdrom");
			else
				return create("mount " + driveLetter + " \"" + dir + "\"");
		}
	}

	public static Mount create(boolean booter, String hostFile, Set<Character> usedDriveLetters, File[] overrideFilesToMount) throws DrivelettersExhaustedException, InvalidMountstringException {
		Mount mount = create(booter, hostFile, usedDriveLetters);
		if (mount instanceof ImageMount) {
			((ImageMount)mount).setImgPaths(FilesUtils.listFilePaths(Arrays.asList(overrideFilesToMount)));
		}
		return mount;
	}
}
