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

	public static Mount create(String mount) throws InvalidMountstringException {

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
