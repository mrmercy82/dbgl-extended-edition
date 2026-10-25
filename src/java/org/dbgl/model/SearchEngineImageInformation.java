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

import org.eclipse.swt.graphics.ImageData;


public class SearchEngineImageInformation {

	public enum SearchEngineImageType {
		COVER_ART, SCREENSHOT
	}

	private final SearchEngineImageType type_;
	private final String description_;

	private String url_;
	private ImageData data_;

	public SearchEngineImageInformation(SearchEngineImageType type, String url, String description) {
		type_ = type;
		url_ = url;
		description_ = description;
	}

	public SearchEngineImageType getType() {
		return type_;
	}

	public String getUrl() {
		return url_;
	}
	
	public void setUrl(String url) {
		url_ = url;
	}

	public String getDescription() {
		return description_;
	}

	public ImageData getData() {
		return data_;
	}

	public void setData(ImageData data) {
		data_ = data;
	}
}