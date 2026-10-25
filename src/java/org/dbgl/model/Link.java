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
package org.dbgl.model;

import java.io.File;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.service.FileLocationService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.StringRelatedUtils;


public class Link {

	private String title_;
	private String destination_;

	public Link(String title, String destination) {
		title_ = title;
		destination_ = StringUtils.isNotBlank(destination) && !destination.contains("://") ? FilenameUtils.separatorsToSystem(destination): destination;
	}

	public String getTitle() {
		return title_;
	}

	public String getDestination() {
		return destination_;
	}

	public String getDisplayTitle() {
		return StringUtils.isNotBlank(title_) ? StringRelatedUtils.toSwtGuiString(title_): StringRelatedUtils.toSwtGuiString(destination_);
	}

	public String getUrl() {
		return StringUtils.isBlank(destination_) || destination_.contains("://") 
				? destination_: FilesUtils.toUrl(new FileLocation(destination_, FileLocationService.getInstance().dataRelative()).getCanonicalFile());
	}

	public String getPathBasedAnchor() {
		return StringUtils.isBlank(destination_) ? null: "<a href=\"" + destination_ + "\">" + getDisplayTitle() + "</a>";
	}
	
	public String getUrlBasedAnchor() {
		return StringUtils.isBlank(destination_) ? null: "<a href=\"" + getUrl() + "\">" + getDisplayTitle() + "</a>";
	}

	public void setBaseDir(File baseDir) {
		if (StringUtils.isNotBlank(destination_) && !destination_.contains("://")) {
			File canonicalFile = new FileLocation(destination_, FileLocationService.getInstance().dataRelative()).getCanonicalFile();
			File relativeToDosrootFile = new FileLocation(canonicalFile.getPath(), FileLocationService.getInstance().dosrootRelative()).getFile();
			destination_ = FilesUtils.concat(baseDir, relativeToDosrootFile);
			if (!baseDir.isAbsolute())
				destination_ = FilesUtils.concat(FileLocationService.DOSROOT_DIR_STRING, destination_);
		}
	}

	public void migrate(FileLocation fromPath, FileLocation toPath) {
		if (StringUtils.isBlank(destination_) || destination_.toLowerCase().startsWith("http://") || destination_.toLowerCase().startsWith("https://"))
			return; // migration not applicable for URLs
		if (destination_.toLowerCase().startsWith("file://"))
			destination_ = destination_.substring(7);
		if (StringUtils.isNotBlank(destination_))
			destination_ = FilesUtils.migrate(new File(destination_), fromPath.getCanonicalFile(), toPath.getCanonicalFile()).getPath();
	}

	@Override
	public String toString() {
		return "title: [" + title_ + "], destination: [" + destination_ + "]";
	}
}
