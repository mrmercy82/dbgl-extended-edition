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

import java.util.Date;


public class LogEntry extends Entity {

	public enum Event {
		ADD, EDIT, REMOVE, DUPLICATE, RUN, SETUP
	}
	public enum EntityType {
		PROFILE, DOSBOXVERSION, TEMPLATE, FILTER
	}

	private final Date time_;
	private final Event event_;
	private final EntityType entityType_;
	private final int entityId_;
	private final String entityTitle_;

	public LogEntry(int id, Date time, byte event, byte entityType, int entityId, String entityTitle) {
		super();
		setId(id);
		time_ = time;
		event_ = Event.values()[event];
		entityType_ = EntityType.values()[entityType];
		entityId_ = entityId;
		entityTitle_ = entityTitle;
	}

	public Date getTime() {
		return time_;
	}

	public Event getEvent() {
		return event_;
	}

	public EntityType getEntityType() {
		return entityType_;
	}

	public int getEntityId() {
		return entityId_;
	}

	public String getEntityTitle() {
		return entityTitle_;
	}
}