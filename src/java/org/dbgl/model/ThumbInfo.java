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
import org.dbgl.service.FileLocationService;
import org.dbgl.util.FilesUtils;


public class ThumbInfo {

	private final FileLocation capturesDir_;

	private File[] files_;
	private String mainThumb_;
	private boolean updated_;

	public ThumbInfo(String captures) {
		capturesDir_ = new FileLocation(captures, FileLocationService.getInstance().dataRelative());
	}

	private void prepareInfo() {
		if (files_ == null) {
			files_ = FilesUtils.listPictureFiles(capturesDir_.getCanonicalFile().listFiles());
			if (files_.length > 0)
				mainThumb_ = files_[0].getPath();
			updated_ = true;
		}
	}

	public String getMainThumb() {
		updated_ = false;
		return mainThumb_;
	}

	public File[] getAllThumbs() {
		prepareInfo();
		return files_;
	}

	public void resetCachedInfo() {
		files_ = null;
		mainThumb_ = null;
	}

	public boolean hasUpdatedMainThumb() {
		prepareInfo();
		return updated_;
	}
}
