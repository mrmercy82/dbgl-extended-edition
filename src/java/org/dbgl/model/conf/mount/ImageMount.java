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
package org.dbgl.model.conf.mount;

import java.io.File;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.FileLocation;
import org.dbgl.service.FileLocationService;
import org.dbgl.util.FilesUtils;


public class ImageMount extends Mount {

	private FileLocation[] imgPath_;
	private String fs_, size_, ide_;
	/** "imgmount", or "mount" as DOSBox Staging 0.83 uses it for images too (IMGMOUNT is only an alias there) */
	private String command_ = "imgmount";

	public String getCommand() {
		return command_;
	}

	public void setCommand(String command) {
		command_ = command;
	}

	public ImageMount() {
		imgPath_ = new FileLocation[0];
		fs_ = StringUtils.EMPTY;
		size_ = StringUtils.EMPTY;
		ide_ = StringUtils.EMPTY;
	}

	public String getFs() {
		return fs_;
	}

	public void setFs(String fs) {
		fs_ = fs;
	}

	public String getSize() {
		return size_;
	}

	public void setSize(String size) {
		size_ = size;
	}
	
	public String getIde() {
		return ide_;
	}

	public void setIde(String ide) {
		ide_ = ide;
	}

	public File[] getImgPaths() {
		return Stream.of(imgPath_).map(FileLocation::getFile).toArray(File[]::new);
	}

	public File[] getCanonicalImgPaths() {
		return Stream.of(imgPath_).map(FileLocation::getCanonicalFile).toArray(File[]::new);
	}

	public String[] getImgPathStrings() {
		return Stream.of(imgPath_).map(x -> x.getFile().getPath()).toArray(String[]::new);
	}

	public void setImgPaths(String[] locations) {
		imgPath_ = new FileLocation[locations.length];
		for (int i = 0; i < locations.length; i++) {
			imgPath_[i] = new FileLocation(locations[i], FileLocationService.getInstance().dosrootRelative());
		}
	}

	public boolean matchesImgPath(String location) {
		if (imgPath_.length == 0)
			return false;
		return imgPath_[0].getFile().getPath().equals(location);
	}

	@Override
	public void setMountAs(String mountAs) {
		super.setMountAs("cdrom".equalsIgnoreCase(mountAs) ? "iso": mountAs);
	}

	@Override
	public File canBeUsedFor(FileLocation hostFile) {
		if (!FilesUtils.areRelated(getCanonicalImgPaths()[0], hostFile.getCanonicalFile()))
			return null;
		return FilesUtils.makeRelativeTo(getCanonicalImgPaths()[0], hostFile.getCanonicalFile());
	}

	@Override
	public void setBaseDir(File baseDir) {
		setImgPaths(Stream.of(getImgPathStrings()).map(x -> FilesUtils.concat(baseDir, x)).toArray(String[]::new));
	}

	@Override
	public void migrate(FileLocation from, FileLocation to) {
		imgPath_ = Stream.of(imgPath_).map(x -> FilesUtils.migrate(x, from, to)).toArray(FileLocation[]::new);
	}

	@Override
	public String getPathString() {
		return StringUtils.join(getImgPathStrings(), ", ");
	}

	@Override
	public String toString(boolean forUI) {
		StringBuilder result = new StringBuilder();
		result.append(command_).append(' ').append(drive_);
		for (String imgPath: getImgPathStrings()) {
			result.append(" \"").append(imgPath).append('"');
		}
		if (StringUtils.isNotBlank(fs_)) {
			result.append(" -fs ").append(fs_);
		}
		if (StringUtils.isNotBlank(size_)) {
			result.append(" -size ").append(size_);
		}
		if (StringUtils.isNotBlank(ide_)) {
			result.append(" -ide ").append(ide_);
		}
		String line = extString(result, forUI);
		// IMGMOUNT calls a CD image "iso", MOUNT (DOSBox Staging 0.83) "cdrom"
		return "mount".equals(command_) ? line.replace(" -t iso", " -t cdrom"): line;
	}
}
