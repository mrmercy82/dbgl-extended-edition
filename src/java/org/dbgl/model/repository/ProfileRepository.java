/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-01. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.NativeCommand;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.entity.LogEntry;
import org.dbgl.model.entity.LogEntry.Event;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.service.DatabaseService.Transaction;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.TextService;
import org.dbgl.util.FilesUtils;


public class ProfileRepository extends LoggingRepository<Profile> {

	private static final String CREATE_QRY = "INSERT INTO GAMES(TITLE, FAVORITE, DEV_ID, PUBL_ID, GENRE_ID, YEAR_ID, STAT_ID, NOTES,"
			+ "CUST1_ID, CUST2_ID, CUST3_ID, CUST4_ID, CUSTOM5, CUSTOM6, CUSTOM7, CUSTOM8, CUSTOM11, CUSTOM12, CUSTOM13, CUSTOM14, CUSTOM9, CUSTOM10,"
			+ "LINK1_TITLE, LINK1, LINK2_TITLE, LINK2, LINK3_TITLE, LINK3, LINK4_TITLE, LINK4, LINK5_TITLE, LINK5, LINK6_TITLE, LINK6, LINK7_TITLE, LINK7, LINK8_TITLE, LINK8,"
			+ "CAPTURES, SETUP, SETUP_PARAMS, ALT1, ALT1_PARAMS, ALT2, ALT2_PARAMS, DBVERSION_ID, CONFFILE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
			+ "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	private static final String CREATE_EXPL_ID_QRY = "INSERT INTO GAMES(TITLE, FAVORITE, DEV_ID, PUBL_ID, GENRE_ID, YEAR_ID, STAT_ID, NOTES,"
			+ "CUST1_ID, CUST2_ID, CUST3_ID, CUST4_ID, CUSTOM5, CUSTOM6, CUSTOM7, CUSTOM8, CUSTOM11, CUSTOM12, CUSTOM13, CUSTOM14, CUSTOM9, CUSTOM10,"
			+ "LINK1_TITLE, LINK1, LINK2_TITLE, LINK2, LINK3_TITLE, LINK3, LINK4_TITLE, LINK4, LINK5_TITLE, LINK5, LINK6_TITLE, LINK6, LINK7_TITLE, LINK7, LINK8_TITLE, LINK8,"
			+ "CAPTURES, SETUP, SETUP_PARAMS, ALT1, ALT1_PARAMS, ALT2, ALT2_PARAMS, DBVERSION_ID, CONFFILE, ID) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
			+ "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	private static final String CREATE_NTVCMD_QRY = "INSERT INTO NATIVECOMMANDS(COMMAND, PARAMETERS, CWD, WAITFOR, ORDERNR, GAME_ID, TEMPLATE_ID) VALUES (?, ?, ?, ?, ?, ?, NULL)";
	private static final String READ_QRY = "SELECT GAM.ID, GAM.TITLE, GAM.FAVORITE, DEV.NAME, PUBL.NAME, GEN.NAME, YR.YEAR, STAT.STAT, GAM.NOTES, "
			+ "CUST1.VALUE, CUST2.VALUE, CUST3.VALUE, CUST4.VALUE, GAM.CUSTOM5, GAM.CUSTOM6, GAM.CUSTOM7, GAM.CUSTOM8, GAM.CUSTOM11, GAM.CUSTOM12, GAM.CUSTOM13, GAM.CUSTOM14, "
			+ "GAM.CUSTOM9, GAM.CUSTOM10, GAM.LINK1_TITLE, GAM.LINK1, GAM.LINK2_TITLE, GAM.LINK2, GAM.LINK3_TITLE, GAM.LINK3, GAM.LINK4_TITLE, GAM.LINK4, "
			+ "GAM.LINK5_TITLE, GAM.LINK5, GAM.LINK6_TITLE, GAM.LINK6, GAM.LINK7_TITLE, GAM.LINK7, GAM.LINK8_TITLE, GAM.LINK8, "
			+ "GAM.CAPTURES, GAM.SETUP, GAM.SETUP_PARAMS, GAM.ALT1, GAM.ALT1_PARAMS, GAM.ALT2, GAM.ALT2_PARAMS, GAM.DBVERSION_ID, GAM.CONFFILE, "
			+ "GAM.STATS_CREATED, GAM.STATS_LASTMODIFY, GAM.STATS_LASTRUN, GAM.STATS_LASTSETUP, GAM.STATS_RUNS, GAM.STATS_SETUPS, GAM.STATS_PLAYTIME "
			+ "FROM GAMES GAM, DEVELOPERS DEV, PUBLISHERS PUBL, GENRES GEN, PUBLYEARS YR, STATUS STAT, CUSTOM1 CUST1, CUSTOM2 CUST2, CUSTOM3 CUST3, CUSTOM4 CUST4 "
			+ "WHERE GAM.DEV_ID=DEV.ID AND GAM.PUBL_ID=PUBL.ID AND GAM.GENRE_ID=GEN.ID AND GAM.YEAR_ID=YR.ID AND GAM.STAT_ID=STAT.ID "
			+ "AND GAM.CUST1_ID=CUST1.ID AND GAM.CUST2_ID=CUST2.ID AND GAM.CUST3_ID=CUST3.ID AND GAM.CUST4_ID=CUST4.ID";
	private static final String READ_BY_ID_QRY = READ_QRY + " AND GAM.ID = ?";
	private static final String READ_INVALID_PROFILES_QRY = READ_QRY + " AND (GAM.CONFFILE IS NULL OR GAM.CAPTURES IS NULL)";
	private static final String READ_NTVCMD_QRY = "SELECT COMMAND, PARAMETERS, CWD, WAITFOR, ORDERNR FROM NATIVECOMMANDS WHERE GAME_ID = ? AND TEMPLATE_ID IS NULL ORDER BY ORDERNR";
	private static final String UPD_QRY = "UPDATE GAMES SET TITLE = ?, FAVORITE = ?, DEV_ID = ?, PUBL_ID = ?, GENRE_ID = ?, YEAR_ID = ?, "
			+ "STAT_ID = ?, NOTES = ?, CUST1_ID = ?, CUST2_ID = ?, CUST3_ID = ?, CUST4_ID = ?, "
			+ "CUSTOM5 = ?, CUSTOM6 = ?, CUSTOM7 = ?, CUSTOM8 = ?, CUSTOM11 = ?, CUSTOM12 = ?, CUSTOM13 = ?, CUSTOM14 = ?, CUSTOM9 = ?, CUSTOM10 = ?, "
			+ "LINK1_TITLE = ?, LINK1 = ?, LINK2_TITLE = ?, LINK2 = ?, LINK3_TITLE = ?, LINK3 = ?, LINK4_TITLE = ?, LINK4 = ?, "
			+ "LINK5_TITLE = ?, LINK5 = ?, LINK6_TITLE = ?, LINK6 = ?, LINK7_TITLE = ?, LINK7 = ?, LINK8_TITLE = ?, LINK8 = ?, "
			+ "CAPTURES = ?, SETUP = ?, SETUP_PARAMS = ?, ALT1 = ?, ALT1_PARAMS = ?, ALT2 = ?, ALT2_PARAMS = ?, DBVERSION_ID = ?, CONFFILE = ?, STATS_LASTMODIFY = CURRENT_TIMESTAMP WHERE ID = ?";
	private static final String DEL_QRY = "DELETE FROM GAMES WHERE ID = ?";
	private static final String DEL_NTVCMD_QRY = "DELETE FROM NATIVECOMMANDS WHERE GAME_ID = ? AND TEMPLATE_ID IS NULL";

	private static final String REGISTER_RUN_QRY = "UPDATE GAMES SET STATS_LASTRUN = CURRENT_TIMESTAMP, STATS_RUNS = (STATS_RUNS + 1) WHERE ID = ?";
	private static final String ADD_PLAYTIME_QRY = "UPDATE GAMES SET STATS_PLAYTIME = (STATS_PLAYTIME + ?) WHERE ID = ?";
	private static final String REGISTER_SETUP_QRY = "UPDATE GAMES SET STATS_LASTSETUP = CURRENT_TIMESTAMP, STATS_SETUPS = (STATS_SETUPS + 1) WHERE ID = ?";

	public ProfileRepository() {
		super();
	}

	public Profile add(Profile profile) throws SQLException {
		return add(profile, false);
	}

	public Profile add(Profile profile, boolean tryToUseExistingConfig) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			if (tryToUseExistingConfig && !FilesUtils.isExistingFile(profile.getConfigurationCanonicalFile()))
				tryToUseExistingConfig = false;
			doAdd(transaction, profile, tryToUseExistingConfig);
			doUpdate(transaction, profile); // set correct captures and configuration file paths into the database
			log(transaction, LogEntry.Event.ADD, LogEntry.EntityType.PROFILE, profile);
			if (!tryToUseExistingConfig)
				profile.saveConfiguration();
			transaction.commit();
			return profile;
		} catch (SQLException | IOException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"add profile"}));
		}
	}

	private static void doAdd(Transaction transaction, Profile profile, boolean tryToUseExistingConfig) throws SQLException {
		try (PreparedStatement pstmt = transaction.prepareStatement(profile.getId() == -1 ? CREATE_QRY: CREATE_EXPL_ID_QRY)) {
			TitledEntityRepository valRepo = new TitledEntityRepository();
			int devId = valRepo.findDeveloper(profile.getDeveloper(), transaction);
			int publId = valRepo.findPublisher(profile.getPublisher(), transaction);
			int genId = valRepo.findGenre(profile.getGenre(), transaction);
			int yrId = valRepo.findYear(profile.getYear(), transaction);
			int statId = valRepo.findStatus(profile.getStatus(), transaction);
			pstmt.setString(1, profile.getTitle());
			pstmt.setBoolean(2, profile.isFavorite());
			pstmt.setInt(3, devId == -1 ? TitledEntityRepository.addDeveloper(transaction, profile.getDeveloper()): devId);
			pstmt.setInt(4, publId == -1 ? TitledEntityRepository.addPublisher(transaction, profile.getPublisher()): publId);
			pstmt.setInt(5, genId == -1 ? TitledEntityRepository.addGenre(transaction, profile.getGenre()): genId);
			pstmt.setInt(6, yrId == -1 ? TitledEntityRepository.addYear(transaction, profile.getYear()): yrId);
			pstmt.setInt(7, statId == -1 ? TitledEntityRepository.addStatus(transaction, profile.getStatus()): statId);
			pstmt.setString(8, profile.getNotes());
			for (int i = 0; i < Profile.NR_OF_CUSTOM_STRING_DROPDOWNS; i++) {
				String value = profile.getCustomStrings()[i];
				int custId = valRepo.findCustomValue(i, value, transaction);
				pstmt.setInt(i + 9, custId == -1 ? TitledEntityRepository.addCustomValue(transaction, i, value): custId);
			}
			for (int i = 0; i < Profile.NR_OF_CUSTOM_STRING_FIELDS; i++)
				pstmt.setString(i + 13, profile.getCustomStrings()[Profile.NR_OF_CUSTOM_STRING_DROPDOWNS + i]);
			for (int i = 0; i < Profile.NR_OF_CUSTOM_INTS; i++)
				pstmt.setInt(i + 21, profile.getCustomInts()[i]);
			for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
				pstmt.setString(i * 2 + 23, profile.getLinks()[i].getTitle());
				pstmt.setString(i * 2 + 24, profile.getLinks()[i].getDestination());
			}
			pstmt.setString(39, null);
			pstmt.setString(40, profile.getSetupString());
			pstmt.setString(41, profile.getSetupParams());
			for (int i = 0; i < Profile.NR_OF_ALT_EXECUTABLES; i++) {
				pstmt.setString(i * 2 + 42, profile.getAltExeStrings()[i]);
				pstmt.setString(i * 2 + 43, profile.getAltExeParams()[i]);
			}
			pstmt.setInt(46, profile.getDosboxVersion().getId());
			pstmt.setString(47, null);

			if (profile.getId() != -1)
				pstmt.setInt(48, profile.getId());

			pstmt.executeUpdate();

			if (profile.getId() == -1)
				profile.setId(identity(transaction));

			addNativeCommands(transaction, profile);

			if (!tryToUseExistingConfig)
				profile.setConfigurationFileLocationByIdentifiers();
			profile.setCapturesById();
			FilesUtils.createDir(profile.getCanonicalCaptures());
			profile.updateMapperFileInConfigByIdentifiers();
		}
	}

	private static void addNativeCommands(Transaction transaction, Profile profile) throws SQLException {
		List<NativeCommand> commands = profile.getNativeCommands();
		for (int i = 0; i < commands.size(); i++) {
			NativeCommand cmd = commands.get(i);
			if (!cmd.isDosboxCommand()) {
				try (PreparedStatement stmt = transaction.prepareStatement(CREATE_NTVCMD_QRY)) {
					stmt.setString(1, cmd.getCommand().getPath());
					stmt.setString(2, cmd.getParameters());
					stmt.setString(3, cmd.getCwd().getPath());
					stmt.setBoolean(4, cmd.isWaitFor());
					stmt.setInt(5, i);
					stmt.setInt(6, profile.getId());
					stmt.executeUpdate();
				} catch (SQLException e) {
					e.printStackTrace();
					throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"add profile native commands"}));
				}
			}
		}
	}

	public List<Profile> list(String orderingClause, String filterClause, List<DosboxVersion> dosboxVersions) throws SQLException {
		StringBuilder qry = new StringBuilder(READ_QRY);
		if (StringUtils.isNotBlank(filterClause))
			qry.append(" AND (").append(filterClause).append(")");
		qry.append(orderingClause);
		try (Connection con = dbService_.getConnection(); Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(qry.toString())) {
			List<Profile> profilesList = new ArrayList<>();
			while (rs.next())
				profilesList.add(toProfile(con, rs, dosboxVersions));
			return profilesList;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read profiles"}));
		}
	}

	private static Profile toProfile(Connection con, ResultSet rs, List<DosboxVersion> dosboxVersions) throws SQLException {
		return ProfileFactory.create(rs.getInt(1), rs.getString(2), rs.getBoolean(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
			new String[] {rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17), rs.getString(18),
					rs.getString(19), rs.getString(20), rs.getString(21)},
			new int[] {rs.getInt(22), rs.getInt(23)},
			new String[] {rs.getString(24), rs.getString(25), rs.getString(26), rs.getString(27), rs.getString(28), rs.getString(29), rs.getString(30), rs.getString(31), rs.getString(32),
					rs.getString(33), rs.getString(34), rs.getString(35), rs.getString(36), rs.getString(37), rs.getString(38), rs.getString(39)},
			rs.getString(40), rs.getString(41), rs.getString(42), rs.getString(43), rs.getString(44), rs.getString(45), rs.getString(46), listNativeCommands(con, rs.getInt(1)), rs.getInt(47),
			rs.getString(48), rs.getTimestamp(49), rs.getTimestamp(50), rs.getTimestamp(51), rs.getTimestamp(52), rs.getInt(53), rs.getInt(54), rs.getInt(55), dosboxVersions);
	}

	private static List<NativeCommand> listNativeCommands(Connection con, int profileId) throws SQLException {
		List<NativeCommand> nativeCommands = new ArrayList<>();
		try (PreparedStatement stmt = con.prepareStatement(READ_NTVCMD_QRY)) {
			stmt.setInt(1, profileId);
			try (ResultSet resultset = stmt.executeQuery()) {
				while (resultset.next())
					nativeCommands.add(new NativeCommand(resultset.getString(1), resultset.getString(2), resultset.getString(3), resultset.getBoolean(4), resultset.getInt(5)));
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read profile native commands"}));
		}
		NativeCommand.insertDosboxCommand(nativeCommands);
		return nativeCommands;
	}

	public List<Profile> listInvalidProfiles(List<DosboxVersion> dosboxVersions) throws SQLException {
		try (Connection con = dbService_.getConnection(); Statement stmt = con.createStatement(); ResultSet resultset = stmt.executeQuery(READ_INVALID_PROFILES_QRY)) {
			List<Profile> profilesList = new ArrayList<>();
			while (resultset.next())
				profilesList.add(toProfile(con, resultset, dosboxVersions));
			return profilesList;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"list invalid profiles"}));
		}
	}

	public void update(Profile profile) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			doUpdate(transaction, profile);
			actionOnEntity(transaction, DEL_NTVCMD_QRY, "remove profile native commands", profile);
			addNativeCommands(transaction, profile);
			log(transaction, LogEntry.Event.EDIT, LogEntry.EntityType.PROFILE, profile);
			byte[] before = org.dbgl.util.ProfileHistory.read(profile);
			profile.saveConfiguration();
			org.dbgl.util.ProfileHistory.recordIfChanged(profile, before); // earlier states can be brought back
			transaction.commit();
		} catch (SQLException | IOException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"update profile"}));
		}
	}

	private static void doUpdate(Transaction transaction, Profile profile) throws SQLException {
		try (PreparedStatement pstmt = transaction.prepareStatement(UPD_QRY)) {
			TitledEntityRepository valRepo = new TitledEntityRepository();
			int devId = valRepo.findDeveloper(profile.getDeveloper(), transaction);
			int publId = valRepo.findPublisher(profile.getPublisher(), transaction);
			int genId = valRepo.findGenre(profile.getGenre(), transaction);
			int yrId = valRepo.findYear(profile.getYear(), transaction);
			int statId = valRepo.findStatus(profile.getStatus(), transaction);
			pstmt.setString(1, profile.getTitle());
			pstmt.setBoolean(2, profile.isFavorite());
			pstmt.setInt(3, devId == -1 ? TitledEntityRepository.addDeveloper(transaction, profile.getDeveloper()): devId);
			pstmt.setInt(4, publId == -1 ? TitledEntityRepository.addPublisher(transaction, profile.getPublisher()): publId);
			pstmt.setInt(5, genId == -1 ? TitledEntityRepository.addGenre(transaction, profile.getGenre()): genId);
			pstmt.setInt(6, yrId == -1 ? TitledEntityRepository.addYear(transaction, profile.getYear()): yrId);
			pstmt.setInt(7, statId == -1 ? TitledEntityRepository.addStatus(transaction, profile.getStatus()): statId);
			pstmt.setString(8, profile.getNotes());
			for (int i = 0; i < Profile.NR_OF_CUSTOM_STRING_DROPDOWNS; i++) {
				String value = profile.getCustomStrings()[i];
				int custId = valRepo.findCustomValue(i, value, transaction);
				pstmt.setInt(i + 9, custId == -1 ? TitledEntityRepository.addCustomValue(transaction, i, value): custId);
			}
			for (int i = 0; i < Profile.NR_OF_CUSTOM_STRING_FIELDS; i++)
				pstmt.setString(i + 13, profile.getCustomStrings()[Profile.NR_OF_CUSTOM_STRING_DROPDOWNS + i]);
			for (int i = 0; i < Profile.NR_OF_CUSTOM_INTS; i++)
				pstmt.setInt(i + 21, profile.getCustomInts()[i]);
			for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
				pstmt.setString(i * 2 + 23, profile.getLinks()[i].getTitle());
				pstmt.setString(i * 2 + 24, profile.getLinks()[i].getDestination());
			}
			pstmt.setString(39, profile.getCapturesString());
			pstmt.setString(40, profile.getSetupString());
			pstmt.setString(41, profile.getSetupParams());
			for (int i = 0; i < Profile.NR_OF_ALT_EXECUTABLES; i++) {
				pstmt.setString(i * 2 + 42, profile.getAltExeStrings()[i]);
				pstmt.setString(i * 2 + 43, profile.getAltExeParams()[i]);
			}
			pstmt.setInt(46, profile.getDosboxVersion().getId());
			pstmt.setString(47, profile.getConfigurationFile().getPath());
			pstmt.setInt(48, profile.getId());
			pstmt.executeUpdate();

			profile.setCapturesInConfig();
			profile.updateMapperFileInConfigByIdentifiers();
		}
	}

	public void remove(Profile profile, boolean removeConfig, boolean removeCustomMapper, boolean removeCaptures) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			actionOnEntity(transaction, DEL_NTVCMD_QRY, "remove profile native commands", profile);
			actionOnEntity(transaction, DEL_QRY, "remove profile", profile);
			log(transaction, LogEntry.Event.REMOVE, LogEntry.EntityType.PROFILE, profile);
			if (removeConfig) {
				FilesUtils.removeFile(profile.getConfigurationCanonicalFile());
				org.dbgl.util.ProfileHistory.deleteAll(profile.getId());
			}
			if (removeCustomMapper) {
				File mapperFile = profile.getCustomMapperFile();
				if (mapperFile != null) {
					FilesUtils.removeFile(mapperFile);
				}
			}
			if (removeCaptures && !FileLocationService.getInstance().isCapturesDir(profile.getCanonicalCaptures())) {
				FilesUtils.removeFilesInDirAndDir(profile.getCanonicalCaptures());
			}
			transaction.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		}
	}

	public Profile duplicate(Profile profile) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			log(transaction, LogEntry.Event.DUPLICATE, LogEntry.EntityType.PROFILE, profile);
			Profile duplicate = ProfileFactory.createCopy(profile);
			duplicate.loadConfigurationData(TextService.getInstance(), profile.getConfigurationString(), profile.getConfigurationCanonicalFile());
			doAdd(transaction, duplicate, false);
			doUpdate(transaction, duplicate); // set correct captures and configuration file paths into the database
			log(transaction, LogEntry.Event.ADD, LogEntry.EntityType.PROFILE, duplicate);
			duplicate.saveConfiguration();
			transaction.commit();
			return duplicate;
		} catch (SQLException | IOException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"duplicate profile"}));
		}
	}

	public Profile registerRun(Profile profile) throws SQLException {
		return register(profile, REGISTER_RUN_QRY, "register run profile", LogEntry.Event.RUN);
	}

	/** add to the time the profile was played, in seconds */
	public void addPlayTime(int profileId, long seconds) throws SQLException {
		try (Connection con = dbService_.getConnection(); PreparedStatement stmt = con.prepareStatement(ADD_PLAYTIME_QRY)) {
			stmt.setLong(1, seconds);
			stmt.setInt(2, profileId);
			stmt.executeUpdate();
		}
	}

	public Profile registerSetup(Profile profile) throws SQLException {
		return register(profile, REGISTER_SETUP_QRY, "register setup profile", LogEntry.Event.SETUP);
	}

	private Profile register(Profile profile, String query, String action, Event event) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			actionOnEntity(transaction, query, action, profile);
			log(transaction, event, LogEntry.EntityType.PROFILE, profile);
			transaction.commit();
			return getById(profile.getId(), Arrays.asList(profile.getDosboxVersion()));
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		}
	}

	private Profile getById(int profileId, List<DosboxVersion> dosboxVersions) throws SQLException {
		try (Connection con = dbService_.getConnection(); PreparedStatement stmt = con.prepareStatement(READ_BY_ID_QRY)) {
			stmt.setInt(1, profileId);
			try (ResultSet resultset = stmt.executeQuery()) {
				if (resultset.next())
					return toProfile(con, resultset, dosboxVersions);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read profile " + profileId}));
		}
		return null; // not found
	}
}
