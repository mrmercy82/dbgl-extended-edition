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
package org.dbgl.model.entity;

public abstract class Entity implements IEntity {

	private int id_;

	protected Entity() {
		id_ = -1;
	}

	@Override
	public int getId() {
		return id_;
	}

	@Override
	public void setId(int id) {
		if (id == -1)
			throw new RuntimeException("Should not set ID to -1");
		if (id_ != -1)
			throw new RuntimeException("Should not set ID more than once");
		id_ = id;
	}
}
