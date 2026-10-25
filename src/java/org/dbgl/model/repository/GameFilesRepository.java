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
package org.dbgl.model.repository;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.bind.DatatypeConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.dbgl.model.WebProfile;
import org.dbgl.service.MetropolisDatabaseService;
import org.dbgl.service.TextService;
import org.dbgl.util.FilesUtils;
import org.hsqldb.jdbc.JDBCArrayBasic;


public class GameFilesRepository {

	private static final String GET_FILENAMES_QRY = "SELECT NAME FROM FILENAMES";
	private static final String GET_FILES_WITH_MD5_QRY = "SELECT FILE_MD5S.ID_FILE_MD5 FROM FILE_MD5S, FILENAMES WHERE FILE_MD5S.ID_FILENAME=FILENAMES.ID_FILENAME "
			+ "AND (FILENAMES.NAME, HEX(FILE_MD5S.MD5)) IN ";
	private static final String GET_GAMES_QRY = "SELECT MD5.ID_MOBY_RELEASES, MD5.ID_FILE_SET, MD5.ID_FILE_TYPE, NAM.NAME, FIL.MD5, GAM.NAME, PUBL.NAME, REL.YEAR, GAM.ID_MOBY_GAMES, GAM.NAME_PREFIX, GAM.DESCRIPTION "
			+ "FROM RELEASE_MD5S AS MD5, TBL_MOBY_RELEASES AS REL, TBL_MOBY_GAMES AS GAM, FILE_MD5S AS FIL, FILENAMES AS NAM "
			+ "LEFT JOIN tbl_Moby_Companies Publ ON REL.Publisher_id_Moby_Companies = PUBL.id_Moby_Companies "
			+ "WHERE MD5.ID_MOBY_RELEASES = REL.ID_MOBY_RELEASES AND FIL.ID_FILE_MD5 = MD5.ID_FILE_MD5  AND NAM.ID_FILENAME = FIL.ID_FILENAME "
			+ "AND REL.ID_MOBY_GAMES = GAM.ID_MOBY_GAMES AND ID_FILE_MD5 IN (UNNEST(?)) " + "ORDER BY MD5.ID_MOBY_RELEASES, MD5.ID_FILE_SET, MD5.ID_FILE_TYPE";
	private static final String GET_RELEASE_FILESET_QRY = "SELECT FILENAMES.NAME FROM RELEASE_MD5S " + "JOIN FILE_MD5S ON FILE_MD5S.ID_FILE_MD5 = RELEASE_MD5S.ID_FILE_MD5 "
			+ "JOIN FILENAMES ON FILENAMES.ID_FILENAME = FILE_MD5S.ID_FILENAME " + "WHERE RELEASE_MD5S.ID_MOBY_RELEASES = ? AND RELEASE_MD5S.ID_FILE_SET = ? "
			+ "ORDER BY RELEASE_MD5S.ID_MOBY_RELEASES, RELEASE_MD5S.ID_FILE_SET, RELEASE_MD5S.ID_FILE_TYPE";

	public enum FileType {
		MAIN, SETUP, EXTRA
	}

	public static class GameData {
		public final List<FileSet> fileSets_;
		public final WebProfile webProfile_;

		public GameData() {
			fileSets_ = new ArrayList<>();
			webProfile_ = new WebProfile();
		}
	}

	public static class FileSet {
		public final Map<String, FileData> set_;

		public FileSet(String filename, FileData fileData) {
			set_ = new LinkedHashMap<>();
			set_.put(filename, fileData);
		}

		public FileSet(Map<String, FileData> set) {
			set_ = set;
		}

		public int getScore() {
			return set_.values().stream().mapToInt(x -> x.score_).sum();
		}
	}

	public static class FileData {
		public final FileType type_;
		public final byte[] md5_;
		private int score_;

		public FileData() {
			type_ = null;
			md5_ = null;
		}

		public FileData(FileType type, byte[] md5) {
			type_ = type;
			md5_ = md5;
		}

		public int getScore() {
			return score_;
		}

		public void resetScore() {
			score_ = 0;
		}

		public void scoreByType() {
			if (type_ == FileType.MAIN)
				score_ = 35;
			else if (type_ == FileType.SETUP)
				score_ = 15;
			else
				score_ = 10;
		}
	}

	public static class GameDirEntry implements Comparable<GameDirEntry> {
		public final File dir_;
		private Optional<File> main_;
		private Optional<File> setup_;
		public final List<File> executables_;
		public final WebProfile webProfile_;
		public final int score_;
		public final String explanation_;

		public GameDirEntry(File dir, Optional<File> main, Optional<File> setup, List<File> executables, WebProfile webProfile, int score, String explanation) {
			super();
			dir_ = dir;
			main_ = main;
			setup_ = setup;
			executables_ = executables;
			webProfile_ = webProfile;
			score_ = score;
			explanation_ = explanation;
		}

		public Optional<File> getOptMain() {
			return main_;
		}

		public File getMain() {
			return main_.get();
		}

		public void setOptMain(Optional<File> main) {
			main_ = main;
		}

		public Optional<File> getOptSetup() {
			return setup_;
		}

		public File getSetup() {
			return setup_.get();
		}

		public void setOptSetup(Optional<File> setup) {
			setup_ = setup;
		}

		@Override
		public int hashCode() {
			return Objects.hash(main_, webProfile_.getTitle());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof GameDirEntry))
				return false;
			return this.compareTo((GameDirEntry)obj) == 0;
		}

		@Override
		public int compareTo(GameDirEntry comp) {
			if (main_.isPresent() && !comp.main_.isPresent())
				return -1;
			if (!main_.isPresent() && comp.main_.isPresent())
				return 1;
			return webProfile_.getTitle().compareTo(comp.webProfile_.getTitle());
		}

		public static final class byDir implements Comparator<GameDirEntry> {
			@Override
			public int compare(GameDirEntry prof1, GameDirEntry prof2) {
				return new FilesUtils.FileComparator().compare(prof1.dir_, prof2.dir_);
			}
		}

		public static final class byTitle implements Comparator<GameDirEntry> {
			@Override
			public int compare(GameDirEntry prof1, GameDirEntry prof2) {
				return new WebProfile.byTitle().compare(prof1.webProfile_, prof2.webProfile_);
			}
		}

		public static final class byMain implements Comparator<GameDirEntry> {
			@Override
			public int compare(GameDirEntry prof1, GameDirEntry prof2) {
				if (!prof1.main_.isPresent() && !prof2.main_.isPresent())
					return new byDir().compare(prof1, prof2);
				else if (!prof1.main_.isPresent())
					return 1;
				else if (!prof2.main_.isPresent())
					return -1;
				return new FilesUtils.FilenameComparator().compare(prof1.main_.get().getName(), prof2.main_.get().getName());
			}
		}

		public static final class bySetup implements Comparator<GameDirEntry> {
			@Override
			public int compare(GameDirEntry prof1, GameDirEntry prof2) {
				if (!prof1.setup_.isPresent() && !prof2.setup_.isPresent())
					return new byDir().compare(prof1, prof2);
				else if (!prof1.setup_.isPresent())
					return 1;
				else if (!prof2.setup_.isPresent())
					return -1;
				return new FilesUtils.FilenameComparator().compare(prof1.setup_.get().getName(), prof2.setup_.get().getName());
			}
		}

		public static final class byPublisher implements Comparator<GameDirEntry> {
			@Override
			public int compare(GameDirEntry prof1, GameDirEntry prof2) {
				if (StringUtils.isAllBlank(prof1.webProfile_.getPublisherName(), prof2.webProfile_.getPublisherName()))
					return 0;
				else if (StringUtils.isBlank(prof1.webProfile_.getPublisherName()))
					return 1;
				else if (StringUtils.isBlank(prof2.webProfile_.getPublisherName()))
					return -1;
				return prof1.webProfile_.getPublisherName().compareToIgnoreCase(prof2.webProfile_.getPublisherName());
			}
		}

		public static final class byYear implements Comparator<GameDirEntry> {
			@Override
			public int compare(GameDirEntry prof1, GameDirEntry prof2) {
				if (StringUtils.isAllBlank(prof1.webProfile_.getYear(), prof2.webProfile_.getYear()))
					return 0;
				else if (StringUtils.isBlank(prof1.webProfile_.getYear()))
					return 1;
				else if (StringUtils.isBlank(prof2.webProfile_.getYear()))
					return -1;
				return new WebProfile.byYear().compare(prof1.webProfile_, prof2.webProfile_);
			}
		}

		public static final class byScore implements Comparator<GameDirEntry> {
			@Override
			public int compare(GameDirEntry prof1, GameDirEntry prof2) {
				return Integer.compare(prof1.score_, prof2.score_);
			}
		}
	}

	MetropolisDatabaseService metropolisService_;

	public GameFilesRepository() {
		metropolisService_ = MetropolisDatabaseService.getInstance();
	}

	public List<String> listAllFilenames() throws SQLException {
		try (Connection con = metropolisService_.getConnection(); Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(GET_FILENAMES_QRY)) {
			List<String> filenames = new ArrayList<>();
			while (rs.next())
				filenames.add(rs.getString(1));
			return filenames;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read filenames"}));
		}
	}

	public Map<Integer, GameData> getGamesWithMd5Ids(List<ImmutablePair<File, byte[]>> fileMd5s) throws SQLException {
		LinkedHashMap<Integer, GameData> result = new LinkedHashMap<>();
		if (fileMd5s.isEmpty())
			return result;

		String inClause = "(" + fileMd5s.stream().map(x -> "('" + x.getKey().getName() + "','" + DatatypeConverter.printHexBinary(x.getValue()).toLowerCase() + "')").collect(Collectors.joining(","))
				+ ")";
		List<Integer> fileWithMd5Ids = new ArrayList<>();

		try (Connection con = metropolisService_.getConnection()) {
			try (Statement stmt = con.createStatement()) {
				try (ResultSet resultset = stmt.executeQuery(GET_FILES_WITH_MD5_QRY + inClause)) {
					while (resultset.next())
						fileWithMd5Ids.add(resultset.getInt(1));
				}
			}

			try (PreparedStatement pstmt = con.prepareStatement(GET_GAMES_QRY)) {
				JDBCArrayBasic array = new JDBCArrayBasic(fileWithMd5Ids.toArray(), org.hsqldb.types.Type.SQL_INTEGER);
				pstmt.setArray(1, array);
				try (ResultSet resultset = pstmt.executeQuery()) {
					int lastMobyReleaseId = -1;
					int lastSet = -1;
					while (resultset.next()) {
						int mobyReleaseId = resultset.getInt(1);
						int fileSet = resultset.getInt(2);
						String filename = resultset.getString(4);
						FileData fileData = new FileData(FileType.values()[resultset.getInt(3)], resultset.getBytes(5));

						if ((mobyReleaseId != lastMobyReleaseId || fileSet != lastSet) && lastMobyReleaseId != -1 && lastSet != -1) {
							// complete fileset
							GameData gameData2 = result.get(lastMobyReleaseId);
							Map<String, FileData> fileData2 = gameData2.fileSets_.get(gameData2.fileSets_.size() - 1).set_;

							try (PreparedStatement pstmt2 = con.prepareStatement(GET_RELEASE_FILESET_QRY)) {
								pstmt2.setInt(1, lastMobyReleaseId);
								pstmt2.setInt(2, lastSet);
								try (ResultSet resultset2 = pstmt2.executeQuery()) {
									while (resultset2.next()) {
										String filename2 = resultset2.getString(1);
										if (!fileData2.containsKey(filename2)) {
											fileData2.put(filename2, new FileData());
										}
									}
								}
							}
						}

						if (result.containsKey(mobyReleaseId)) {
							GameData gameData = result.get(mobyReleaseId);
							if (fileSet == lastSet) {
								gameData.fileSets_.get(gameData.fileSets_.size() - 1).set_.put(filename, fileData);
							} else {
								gameData.fileSets_.add(new FileSet(filename, fileData));
							}
						} else {
							GameData gameData = new GameData();
							gameData.webProfile_.setReleaseId(mobyReleaseId);
							gameData.webProfile_.setGameId(resultset.getInt(9));
							gameData.webProfile_.setTitle(resultset.getString(10) != null ? resultset.getString(10) + " " + resultset.getString(6): resultset.getString(6));
							gameData.webProfile_.setPublisherName(resultset.getString(7));
							gameData.webProfile_.setYear(resultset.getString(8));
							gameData.webProfile_.setNotes(resultset.getString(11));
							gameData.fileSets_.add(new FileSet(filename, fileData));
							result.put(mobyReleaseId, gameData);
						}

						lastMobyReleaseId = mobyReleaseId;
						lastSet = fileSet;
					}
				}
			}
		}
		return result;
	}
}
