/*
 *  Copyright (C) 2006-2019 Ronald Blankendaal
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

public class ViewType {

	private final String name_;
	private final String image_;
	private final String displayName_;

	public ViewType(String name, String image, String displayName) {
		name_ = name;
		image_ = image;
		displayName_ = displayName;
	}

	public String getName() {
		return name_;
	}

	public String getImage() {
		return image_;
	}

	public String getDisplayName() {
		return displayName_;
	}
}
