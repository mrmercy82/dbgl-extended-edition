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

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.model.ArchiveFileLocation;
import org.dbgl.model.FileLocation;
import org.dbgl.model.GamePack;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.service.FileLocationService;
import org.dbgl.util.FilesUtils;
import exodos.ZipReference;


public class GamePackEntry extends Entity implements Comparable<GamePackEntry> {

	private GamePack gamePack_;
	private int importedId_;
	private final Profile profile_;
	private final String fullConfig_, incrConfig_;
	private FileLocation baseDir_;
	private ArchiveFileLocation gameDir_, capturesDir_, mapper_;
	private List<ZipEntry> gameArchiveEntries_, captureArchiveEntries_, mapperArchiveEntries_;

	// Only used for converting
	private final Collection<ZipEntry> captures_;
	private final List<ZipReference> extras_;
	private final File gameZipFile_;
	private final long size_;

	/**
	 * Used for export
	 *
	 * @param id
	 * @param profile
	 */
	public GamePackEntry(int id, Profile profile, GamePack gamePack) {
		super();
		setId(id);
		profile_ = profile;
		gamePack_ = gamePack;

		setBaseDir(".");
		setGameDir(profile.getConfiguration().getAutoexec().getCanonicalGameDir().getPath());

		setCapturesDir(StringUtils.EMPTY);

		File customMapper = profile.getCustomMapperFile();
		if (customMapper != null) {
			setMapper(customMapper.getPath());
		}

		fullConfig_ = null;
		incrConfig_ = null;

		captures_ = null;
		extras_ = null;
		gameZipFile_ = null;
		size_ = 0L;
	}

	/**
	 * Used for import
	 *
	 * @param id
	 * @param profile
	 * @param captures
	 * @param mapper
	 * @param gameDir
	 * @param fullConfig
	 * @param incrConfig
	 */
	public GamePackEntry(int id, Profile profile, GamePack gamePack, String captures, String mapper, String gameDir, String fullConfig, String incrConfig) {
		super();
		setId(id);
		profile_ = profile;
		gamePack_ = gamePack;

		setBaseDir(".");
		if (StringUtils.isNotEmpty(gameDir))
			setGameDir(gameDir);

		if (StringUtils.isNotEmpty(captures))
			setCapturesDir(captures);

		if (StringUtils.isNotEmpty(mapper))
			setMapper(mapper);

		fullConfig_ = fullConfig;
		incrConfig_ = incrConfig;

		captures_ = null;
		extras_ = null;
		gameZipFile_ = null;
		size_ = 0L;
	}

	/**
	 * Used for converting
	 *
	 * @param id
	 * @param profile
	 * @param gameDir
	 * @param captures
	 * @param extras
	 * @param gameZipFile
	 * @param size
	 */
	public GamePackEntry(int id, Profile profile, String gameDir, Collection<ZipEntry> captures, List<ZipReference> extras, File gameZipFile, long size) {
		super();
		setId(id);
		profile_ = profile;

		setBaseDir(".");
		if (StringUtils.isNotEmpty(gameDir))
			setGameDir(gameDir);

		setCapturesDir(StringUtils.EMPTY);

		fullConfig_ = null;
		incrConfig_ = null;

		captures_ = captures;
		extras_ = extras;
		gameZipFile_ = gameZipFile;
		size_ = size;
	}

	public int getImportedId() {
		return importedId_;
	}

	public void setImportedId(int importedId) {
		importedId_ = importedId;
	}

	public Profile getProfile() {
		return profile_;
	}

	public GamePack getGamePack() {
		return gamePack_;
	}

	public String getFullConfig() {
		return fullConfig_;
	}

	public String getIncrConfig() {
		return incrConfig_;
	}

	public File getBaseDir() {
		return baseDir_.getFile();
	}

	public File getCanonicalBaseDir() {
		return baseDir_.getCanonicalFile();
	}

	public void setBaseDir(String baseDir) {
		baseDir_ = new FileLocation(baseDir, FileLocationService.getInstance().dosrootRelative());
	}

	public File getGameDir() {
		return gameDir_.getFile();
	}

	public File getCanonicalGameDir() {
		return gameDir_.getCanonicalFile();
	}

	public String getGameDirAsDosString() {
		return gameDir_.getFileAsDosString();
	}

	public String getArchiveGameDirAsDosString() {
		return gameDir_.getArchiveFileAsDosString();
	}

	public File getArchiveGameDir() {
		return gameDir_.getArchiveFile();
	}

	public void setGameDir(String gameDir) {
		gameDir_ = new ArchiveFileLocation(gameDir, FileLocationService.getInstance().dosrootRelative(),
				f -> new File(FileLocationService.DOSROOT_DIR_STRING, new File(String.valueOf(getId()), f.getPath()).getPath()));
	}

	public File getCapturesDir() {
		return capturesDir_.getFile();
	}

	public File getCanonicalCapturesDir() {
		return capturesDir_.getCanonicalFile();
	}

	public String getArchiveCapturesAsDosString() {
		return capturesDir_.getArchiveFileAsDosString();
	}

	public File getArchiveCapturesDir() {
		return capturesDir_.getArchiveFile();
	}

	public void setCapturesDir(String capturesDir) {
		capturesDir_ = new ArchiveFileLocation(capturesDir, FileLocationService.getInstance().dataRelative(), f -> new File(FileLocationService.CAPTURES_DIR_STRING, String.valueOf(getId())));
	}

	public boolean hasMapper() {
		return mapper_ != null;
	}

	public File getMapper() {
		return mapper_.getFile();
	}

	public File getCanonicalMapper() {
		return mapper_.getCanonicalFile();
	}

	public String getArchiveMapperAsDosString() {
		return mapper_.getArchiveFileAsDosString();
	}

	public File getArchiveMapper() {
		return mapper_.getArchiveFile();
	}

	public File getNewMapper() {
		return new File(Strings.CS.replace(profile_.getConfigurationCanonicalFile().getPath(), FilesUtils.CONF_EXT, FilesUtils.MAPPER_EXT));
	}

	public void setMapper(String mapper) {
		mapper_ = new ArchiveFileLocation(mapper, FileLocationService.getInstance().dosrootRelative(), f -> new File(FileLocationService.MAPPER_DIR_STRING, getId() + FilesUtils.MAPPER_EXT));
	}

	public Collection<ZipEntry> getCaptures() {
		return captures_;
	}

	public List<ZipReference> getExtras() {
		return extras_;
	}

	public File getGameZipFile() {
		return gameZipFile_;
	}

	public long getSize() {
		return size_;
	}

	public File getCanonicalFullDir() {
		return new File(baseDir_.getCanonicalFile(), gameDir_.getFile().getPath());
	}

	public List<ZipEntry> getGameArchiveEntries() {
		return gameArchiveEntries_;
	}

	public void setGameArchiveEntries(List<ZipEntry> gameArchiveEntries) {
		gameArchiveEntries_ = gameArchiveEntries;
	}

	public List<ZipEntry> getCaptureArchiveEntries() {
		return captureArchiveEntries_;
	}

	public void setCaptureArchiveEntries(List<ZipEntry> captureArchiveEntries) {
		captureArchiveEntries_ = captureArchiveEntries;
	}

	public List<ZipEntry> getMapperArchiveEntries() {
		return mapperArchiveEntries_;
	}

	public void setMapperArchiveEntries(List<ZipEntry> mapperArchiveEntries) {
		mapperArchiveEntries_ = mapperArchiveEntries;
	}

	public void stripGameTitleFromBaseDir() {
		String gameTitle = FilesUtils.toSafeFilename(getProfile().getTitle());
		if (getBaseDir().getName().equals(gameTitle))
			setBaseDir(getBaseDir().getParent());
	}

	public void appendGameTitleToBaseDir() {
		String gameTitle = FilesUtils.toSafeFilename(getProfile().getTitle());
		if (!getBaseDir().getName().equals(gameTitle))
			setBaseDir(new File(getBaseDir(), gameTitle).getPath());
	}

	@Override
	public int hashCode() {
		return Objects.hash(profile_.getTitle());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof GamePackEntry))
			return false;
		return this.compareTo((GamePackEntry)obj) == 0;
	}

	@Override
	public int compareTo(GamePackEntry comp) {
		return FilesUtils.toSafeFilename(profile_.getTitle()).compareToIgnoreCase(FilesUtils.toSafeFilename(comp.profile_.getTitle()));
	}

	public int getIndexFirstEmptyLink() {
		return IntStream.range(0, profile_.getLinks().length).filter(x -> StringUtils.isBlank(profile_.getLinkDestination(x))).findFirst().orElse(-1);
	}
}
