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

import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.Objects;
import javax.xml.bind.annotation.XmlRootElement;


@XmlRootElement
public class SharedConf extends Entity implements Serializable, Comparable<SharedConf> {

	private static final long serialVersionUID = 5570022733154073208L;

	public enum ConfState { NEW, REJECTED, ACCEPTED, READY, ONLINE };
    
    public final static EnumMap<ConfState, Short> confStates = new EnumMap<ConfState, Short>(ConfState.class);
    static {
    	confStates.put(ConfState.NEW,  		(short) 0);
    	confStates.put(ConfState.REJECTED,	(short) 1);
    	confStates.put(ConfState.ACCEPTED,	(short) 2);
    	confStates.put(ConfState.READY,		(short) 9);
    	confStates.put(ConfState.ONLINE,	(short)10);
    }
    
	private String author_, notes_, gameTitle_, gameVersion_, gameYear_, incrConf_, fullConf_, explanation_, dosboxTitle_, dosboxVersion_;
	private short state_;
	private Date insertDate_;
	private int submissionId_;

	public SharedConf() {
		super();
	}

	private SharedConf(String author, String notes, String gameTitle, String gameVersion, String gameYear, String incrConf, String fullConf, String explanation, String dosboxTitle,
			String dosboxVersion, short state, Date insertDate, int submissionId) {
		this();
		author_ = author;
		notes_ = notes;
		gameTitle_ = gameTitle;
		gameVersion_ = gameVersion;
		gameYear_ = gameYear;
		incrConf_ = incrConf;
		fullConf_ = fullConf;
		explanation_ = explanation;
		dosboxTitle_ = dosboxTitle;
		dosboxVersion_ = dosboxVersion;
		state_ = state;
		insertDate_ = insertDate;
		submissionId_ = submissionId;
	}

	public SharedConf(String author, String notes, String gameTitle, String gameVersion, String gameYear, String incrConf, String fullConf, String explanation, String dosboxTitle,
			String dosboxVersion) {
		this(author, notes, gameTitle, gameVersion, gameYear, incrConf, fullConf, explanation, dosboxTitle, dosboxVersion, confStates.get(ConfState.NEW), new Date(), -1);
	}

	public String getAuthor() {
		return author_;
	}

	public void setAuthor(String author) {
		author_ = author;
	}

	public String getNotes() {
		return notes_;
	}

	public void setNotes(String notes) {
		notes_ = notes;
	}

	public String getGameTitle() {
		return gameTitle_;
	}

	public void setGameTitle(String gameTitle) {
		gameTitle_ = gameTitle;
	}

	public String getGameVersion() {
		return gameVersion_;
	}

	public void setGameVersion(String gameVersion) {
		gameVersion_ = gameVersion;
	}

	public String getGameYear() {
		return gameYear_;
	}

	public void setGameYear(String gameYear) {
		gameYear_ = gameYear;
	}

	public String getIncrConf() {
		return incrConf_;
	}

	public void setIncrConf(String incrConf) {
		incrConf_ = incrConf;
	}

	public String getFullConf() {
		return fullConf_;
	}

	public void setFullConf(String fullConf) {
		fullConf_ = fullConf;
	}

	public String getExplanation() {
		return explanation_;
	}

	public void setExplanation(String explanation) {
		explanation_ = explanation;
	}

	public String getDosboxTitle() {
		return dosboxTitle_;
	}

	public void setDosboxTitle(String dosboxTitle) {
		dosboxTitle_ = dosboxTitle;
	}

	public String getDosboxVersion() {
		return dosboxVersion_;
	}

	public void setDosboxVersion(String dosboxVersion) {
		dosboxVersion_ = dosboxVersion;
	}

	public short getState() {
		return state_;
	}

	public void setState(short state) {
		state_ = state;
	}

	public Date getInsertDate() {
		return insertDate_;
	}

	public void setInsertDate(Date insertDate) {
		insertDate_ = insertDate;
	}

	public int getSubmissionId() {
		return submissionId_;
	}

	public void setSubmissionId(int submissionId) {
		submissionId_ = submissionId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(gameTitle_);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof SharedConf))
			return false;
		return this.compareTo((SharedConf)obj) == 0;
	}

	@Override
	public int compareTo(SharedConf otherConf) {
		return gameTitle_.compareToIgnoreCase(otherConf.gameTitle_);
	}
	
	public static final class byId implements Comparator<SharedConf> {
		@Override
		public int compare(SharedConf conf1, SharedConf conf2) {
		    return Integer.valueOf(conf1.getId()).compareTo(conf2.getId());
		}
	}

	public static final class byTitle implements Comparator<SharedConf> {
		@Override
		public int compare(SharedConf conf1, SharedConf conf2) {
			return conf1.gameTitle_.compareToIgnoreCase(conf2.gameTitle_);
		}
	}

	public static final class byYear implements Comparator<SharedConf> {
		@Override
		public int compare(SharedConf conf1, SharedConf conf2) {
			return conf1.gameYear_.compareToIgnoreCase(conf2.gameYear_);
		}
	}

	public static final class byVersion implements Comparator<SharedConf> {
		@Override
		public int compare(SharedConf conf1, SharedConf conf2) {
			return conf1.gameVersion_.compareToIgnoreCase(conf2.gameVersion_);
		}
	}
	
	public static final class byState implements Comparator<SharedConf> {
		@Override
		public int compare(SharedConf conf1, SharedConf conf2) {
		    return Short.valueOf(conf1.state_).compareTo(conf2.state_);
		}
	}
}
