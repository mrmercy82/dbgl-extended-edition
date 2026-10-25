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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.entity.GamePackEntry;


public class GamePack {

	private File archive_;
	private String title_, author_, notes_;
	private String version_, creationApp_, creationAppVersion_;
	private Date creationDate_;
	private String[] customFieldTitles_;
	private boolean capturesAvailable_, mapperfilesAvailable_;
	private boolean nativecommandsAvailable_, gamedataAvailable_;
	private List<GamePackEntry> entries_;
	private Set<DosboxVersion> dosboxVersions_;

	public GamePack() {
		entries_ = new ArrayList<>();
		dosboxVersions_ = new LinkedHashSet<>();
	}

	public GamePack(File archive) {
		this();
		archive_ = archive;
	}

	public File getArchive() {
		return archive_;
	}

	public void setArchive(File archive) {
		archive_ = archive;
	}

	public String getVersion() {
		return version_;
	}

	public void setVersion(String version) {
		version_ = version;
	}

	public String getCreationApp() {
		return creationApp_;
	}

	public void setCreationApp(String creationApp) {
		creationApp_ = creationApp;
	}

	public String getCreationAppVersion() {
		return creationAppVersion_;
	}

	public void setCreationAppVersion(String creationAppVersion) {
		creationAppVersion_ = creationAppVersion;
	}

	public Date getCreationDate() {
		return creationDate_;
	}

	public void setCreationDate(Date creationDate) {
		creationDate_ = creationDate;
	}

	public String getTitle() {
		return title_;
	}

	public void setTitle(String title) {
		title_ = title;
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

	public String[] getCustomFieldTitles() {
		return customFieldTitles_;
	}

	public void setCustomFieldTitles(String[] customFieldTitles) {
		customFieldTitles_ = customFieldTitles;
	}

	public boolean isCapturesAvailable() {
		return capturesAvailable_;
	}

	public void setCapturesAvailable(boolean capturesAvailable) {
		capturesAvailable_ = capturesAvailable;
	}

	public boolean isMapperfilesAvailable() {
		return mapperfilesAvailable_;
	}

	public void setMapperfilesAvailable(boolean mapperfilesAvailable) {
		mapperfilesAvailable_ = mapperfilesAvailable;
	}

	public boolean isNativecommandsAvailable() {
		return nativecommandsAvailable_;
	}

	public void setNativecommandsAvailable(boolean nativecommandsAvailable) {
		nativecommandsAvailable_ = nativecommandsAvailable;
	}

	public boolean isGamedataAvailable() {
		return gamedataAvailable_;
	}

	public void setGamedataAvailable(boolean gamedataAvailable) {
		gamedataAvailable_ = gamedataAvailable;
	}

	public List<GamePackEntry> getEntries() {
		return entries_;
	}

	public void setEntries(List<GamePackEntry> entries) {
		entries_ = entries;
	}

	public Set<DosboxVersion> getDosboxVersions() {
		return dosboxVersions_;
	}

	public void setDosboxVersions(Set<DosboxVersion> dosboxVersions) {
		dosboxVersions_ = dosboxVersions;
	}

	public GamePackEntry findEntryByGameDir(String rootFolder) {
		return entries_.stream().filter(x -> x.getGameDir().getPath().equals(rootFolder)).findFirst().orElse(null);
	}
}
