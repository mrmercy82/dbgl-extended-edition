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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	private static final String READ_ALL_NTVCMD_QRY = "SELECT GAME_ID, COMMAND, PARAMETERS, CWD, WAITFOR, ORDERNR FROM NATIVECOMMANDS WHERE GAME_ID IS NOT NULL AND TEMPLATE_ID IS NULL ORDER BY GAME_ID, ORDERNR";
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
	private static final String ADD_SESSION_QRY = "INSERT INTO PLAYSESSIONS(GAME_ID, STARTED, SECONDS) VALUES(?, ?, ?)";
	private static final String LIST_SESSIONS_QRY = "SELECT GAME_ID, STARTED, SECONDS FROM PLAYSESSIONS";
	private static final String DEL_SESSIONS_QRY = "DELETE FROM PLAYSESSIONS WHERE GAME_ID = ?";
	private static final String MOVE_SESSIONS_QRY = "UPDATE PLAYSESSIONS SET GAME_ID = ? WHERE GAME_ID = ?";
	/** the play time of one profile added to another, as it is in the database (not as an older copy of the profile says) */
	private static final String MOVE_PLAYTIME_QRY = "UPDATE GAMES SET STATS_PLAYTIME = (STATS_PLAYTIME + (SELECT COALESCE(G.STATS_PLAYTIME, 0) FROM GAMES G WHERE G.ID = ?)) WHERE ID = ?";
	/** longest session counted: a DOSBox left running for weeks, or a clock set forward, must not ruin the play time */
	public static final long MAX_SESSION_SECONDS = 30L * 24 * 60 * 60;
	private static final String REMOVED_SESSIONS_WHERE = " FROM PLAYSESSIONS WHERE GAME_ID NOT IN (SELECT ID FROM GAMES)";
	private static final String REMOVED_LOG_WHERE = " FROM LOG WHERE ENTITY_TYPE = " + LogEntry.EntityType.PROFILE.ordinal() + " AND ENTITY_ID NOT IN (SELECT ID FROM GAMES)";

	/** one time a profile was played: when DOSBox started and how long it ran, in seconds */
	public record PlaySession(int profileId, java.util.Date started, int seconds) {
	}
	// alpha 49: game series, in a table of their own (see DatabaseService)
	private static final String READ_SERIES_QRY = "SELECT GAME_ID, NAME, PART FROM SERIES";
	private static final String DEL_SERIES_QRY = "DELETE FROM SERIES WHERE GAME_ID = ?";
	private static final String ADD_SERIES_QRY = "INSERT INTO SERIES(GAME_ID, NAME, PART) VALUES(?, ?, ?)";
	private static final String LIST_SERIES_NAMES_QRY = "SELECT DISTINCT NAME FROM SERIES ORDER BY LOWER(NAME)";
	private static final String REMOVED_SERIES_WHERE = " FROM SERIES WHERE GAME_ID NOT IN (SELECT ID FROM GAMES)";
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
			pstmt.setString(1, TitledEntityRepository.fit(profile.getTitle())); // alpha 52: a too long text made saving fail
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
				pstmt.setString(i + 13, TitledEntityRepository.fit(profile.getCustomStrings()[Profile.NR_OF_CUSTOM_STRING_DROPDOWNS + i]));
			for (int i = 0; i < Profile.NR_OF_CUSTOM_INTS; i++)
				pstmt.setInt(i + 21, profile.getCustomInts()[i]);
			for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
				pstmt.setString(i * 2 + 23, TitledEntityRepository.fit(profile.getLinks()[i].getTitle()));
				pstmt.setString(i * 2 + 24, profile.getLinks()[i].getDestination());
			}
			pstmt.setString(39, null);
			pstmt.setString(40, TitledEntityRepository.cut(profile.getSetupString()));
			pstmt.setString(41, TitledEntityRepository.cut(profile.getSetupParams()));
			for (int i = 0; i < Profile.NR_OF_ALT_EXECUTABLES; i++) {
				pstmt.setString(i * 2 + 42, TitledEntityRepository.cut(profile.getAltExeStrings()[i]));
				pstmt.setString(i * 2 + 43, TitledEntityRepository.cut(profile.getAltExeParams()[i]));
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
					stmt.setString(1, TitledEntityRepository.cut(cmd.getCommand().getPath())); // alpha 53: as in beta 18 (remax)
					stmt.setString(2, TitledEntityRepository.cut(cmd.getParameters()));
					stmt.setString(3, TitledEntityRepository.cut(cmd.getCwd().getPath()));
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
			// alpha 50: the native commands of all profiles with one query. Before, each profile asked for its own: with
			// thousands of profiles that was most of the time the list took
			Map<Integer, List<NativeCommand>> nativeCommands = allNativeCommands(con);
			List<Profile> profilesList = new ArrayList<>();
			while (rs.next())
				profilesList.add(toProfile(con, rs, dosboxVersions, nativeCommands));
			readSeries(con, profilesList);
			return profilesList;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read profiles"}));
		}
	}

	private static Profile toProfile(Connection con, ResultSet rs, List<DosboxVersion> dosboxVersions) throws SQLException {
		return toProfile(con, rs, dosboxVersions, null);
	}

	/** the native commands of all profiles, per profile in their order; each list is a profile's own */
	private static Map<Integer, List<NativeCommand>> allNativeCommands(Connection con) throws SQLException {
		Map<Integer, List<NativeCommand>> result = new HashMap<>();
		try (Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(READ_ALL_NTVCMD_QRY)) {
			while (rs.next())
				result.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(new NativeCommand(rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getInt(6)));
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read profile native commands"}));
		}
		return result;
	}

	private static List<NativeCommand> nativeCommandsOf(Connection con, int profileId, Map<Integer, List<NativeCommand>> all) throws SQLException {
		if (all == null)
			return listNativeCommands(con, profileId);
		List<NativeCommand> nativeCommands = all.getOrDefault(profileId, new ArrayList<>());
		NativeCommand.insertDosboxCommand(nativeCommands);
		return nativeCommands;
	}

	private static Profile toProfile(Connection con, ResultSet rs, List<DosboxVersion> dosboxVersions, Map<Integer, List<NativeCommand>> nativeCommands) throws SQLException {
		return ProfileFactory.create(rs.getInt(1), rs.getString(2), rs.getBoolean(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
			new String[] {rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17), rs.getString(18),
					rs.getString(19), rs.getString(20), rs.getString(21)},
			new int[] {rs.getInt(22), rs.getInt(23)},
			new String[] {rs.getString(24), rs.getString(25), rs.getString(26), rs.getString(27), rs.getString(28), rs.getString(29), rs.getString(30), rs.getString(31), rs.getString(32),
					rs.getString(33), rs.getString(34), rs.getString(35), rs.getString(36), rs.getString(37), rs.getString(38), rs.getString(39)},
			rs.getString(40), rs.getString(41), rs.getString(42), rs.getString(43), rs.getString(44), rs.getString(45), rs.getString(46), nativeCommandsOf(con, rs.getInt(1), nativeCommands), rs.getInt(47),
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
		// a read-only configuration file: said so, before anything is changed (before alpha 46 the message was a failed query)
		java.io.File conf = profile.getConfigurationCanonicalFile();
		if ((conf != null) && conf.isFile() && !conf.canWrite()) {
			// only what DBGL keeps in its database changed (favourite, title, notes...): that is saved, the file stays as it is
			if (configurationUnchanged(profile)) {
				updateKeepingConfigurationFile(profile);
				return;
			}
			throw new SQLException(TextService.getInstance().get("general.error.confreadonly", new Object[] {conf.getPath()}));
		}
		try (Transaction transaction = dbService_.startTransaction()) {
			String[] versionBefore = previousVersion(transaction, profile); // alpha 53: kept with the state before
			doUpdate(transaction, profile);
			actionOnEntity(transaction, DEL_NTVCMD_QRY, "remove profile native commands", profile);
			addNativeCommands(transaction, profile);
			log(transaction, LogEntry.Event.EDIT, LogEntry.EntityType.PROFILE, profile);
			byte[] before = org.dbgl.util.ProfileHistory.read(profile);
			profile.saveConfiguration();
			org.dbgl.util.ProfileHistory.recordIfChanged(profile, before, versionBefore); // earlier states can be brought back
			transaction.commit();
		} catch (SQLException | IOException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"update profile"}));
		}
	}

	/** the file would be written as DBGL writes it after reading it again: the user changed nothing in it */
	private boolean configurationUnchanged(Profile profile) {
		try {
			List<Profile> again = list(StringUtils.EMPTY, "GAM.ID = " + profile.getId(), new DosboxVersionRepository().listAll());
			if (again.isEmpty())
				return false;
			Profile fresh = again.get(0);
			if (fresh.getDosboxVersion().getId() != profile.getDosboxVersion().getId())
				return false; // switched to another DOSBox version
			fresh.setDosboxVersion(profile.getDosboxVersion()); // with its settings read already
			fresh.resetAndLoadConfiguration();
			return java.util.Arrays.equals(fresh.configurationBytesToSave(), profile.configurationBytesToSave());
		} catch (IOException | SQLException e) {
			return false;
		}
	}

	/** alpha 53: the id and title of the DOSBox version the profile has in the database still (before it is saved) */
	private static String[] previousVersion(Transaction transaction, Profile profile) {
		try (PreparedStatement stmt = transaction.prepareStatement("SELECT D.ID, D.TITLE FROM GAMES G, DOSBOXVERSIONS D WHERE G.DBVERSION_ID = D.ID AND G.ID = ?")) {
			stmt.setInt(1, profile.getId());
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next())
					return new String[] {String.valueOf(rs.getInt(1)), rs.getString(2)};
			}
		} catch (SQLException | RuntimeException e) {
			// not known: kept with the one it has now
		}
		return org.dbgl.util.ProfileHistory.idAndTitle(profile.getDosboxVersion());
	}

	/**
	 * Like {@link #update(Profile)}, but the configuration file is left as it is (e.g. its earlier content just put back by
	 * an undo: saving it would write it again in DBGL's own way, not byte for byte as it was).
	 */
	public void updateKeepingConfigurationFile(Profile profile) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			doUpdate(transaction, profile);
			actionOnEntity(transaction, DEL_NTVCMD_QRY, "remove profile native commands", profile);
			addNativeCommands(transaction, profile);
			log(transaction, LogEntry.Event.EDIT, LogEntry.EntityType.PROFILE, profile);
			transaction.commit();
		} catch (SQLException e) {
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
			pstmt.setString(1, TitledEntityRepository.fit(profile.getTitle())); // alpha 52: a too long text made saving fail
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
				pstmt.setString(i + 13, TitledEntityRepository.fit(profile.getCustomStrings()[Profile.NR_OF_CUSTOM_STRING_DROPDOWNS + i]));
			for (int i = 0; i < Profile.NR_OF_CUSTOM_INTS; i++)
				pstmt.setInt(i + 21, profile.getCustomInts()[i]);
			for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
				pstmt.setString(i * 2 + 23, TitledEntityRepository.fit(profile.getLinks()[i].getTitle()));
				pstmt.setString(i * 2 + 24, profile.getLinks()[i].getDestination());
			}
			pstmt.setString(39, TitledEntityRepository.cut(profile.getCapturesString())); // alpha 53: as in beta 18 (remax)
			pstmt.setString(40, TitledEntityRepository.cut(profile.getSetupString()));
			pstmt.setString(41, TitledEntityRepository.cut(profile.getSetupParams()));
			for (int i = 0; i < Profile.NR_OF_ALT_EXECUTABLES; i++) {
				pstmt.setString(i * 2 + 42, TitledEntityRepository.cut(profile.getAltExeStrings()[i]));
				pstmt.setString(i * 2 + 43, TitledEntityRepository.cut(profile.getAltExeParams()[i]));
			}
			pstmt.setInt(46, profile.getDosboxVersion().getId());
			pstmt.setString(47, TitledEntityRepository.cut(profile.getConfigurationFile().getPath()));
			pstmt.setInt(48, profile.getId());
			pstmt.executeUpdate();

			profile.setCapturesInConfig();
			profile.updateMapperFileInConfigByIdentifiers();
		}
		writeSeries(transaction, profile.getId(), profile.getSeries(), profile.getSeriesPart());
	}

	/** alpha 49: the series of the profiles just read (one query for all) */
	private static void readSeries(Connection con, List<Profile> profiles) throws SQLException {
		java.util.Map<Integer, Object[]> series = new java.util.HashMap<>();
		try (Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(READ_SERIES_QRY)) {
			while (rs.next())
				series.put(rs.getInt(1), new Object[] {rs.getString(2), rs.getInt(3)});
		}
		for (Profile p: profiles) {
			Object[] s = series.get(p.getId());
			p.setSeries(s == null ? "": (String)s[0], s == null ? 0: (Integer)s[1]);
		}
	}

	/** the series of a profile as it is to be: null leaves it as it is (not read), "" takes the profile out of its series */
	private static void writeSeries(Transaction transaction, int profileId, String name, int part) throws SQLException {
		if (name == null)
			return;
		try (PreparedStatement del = transaction.prepareStatement(DEL_SERIES_QRY)) {
			del.setInt(1, profileId);
			del.executeUpdate();
		}
		if (name.isBlank())
			return;
		try (PreparedStatement add = transaction.prepareStatement(ADD_SERIES_QRY)) {
			add.setInt(1, profileId);
			add.setString(2, StringUtils.left(name.strip(), 256));
			add.setInt(3, Math.max(0, part));
			add.executeUpdate();
		}
	}

	/** alpha 49: put a profile into a series (or take it out with ""), nothing else of it is touched (not its configuration file) */
	public void setSeries(Profile profile, String name, int part) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			writeSeries(transaction, profile.getId(), StringUtils.defaultString(name), part);
			transaction.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"update series of profile"}));
		}
		profile.setSeries(StringUtils.defaultString(name), part);
	}

	/** a profile of a series, for the menu of its parts */
	public record SeriesMember(int id, String title, int part) {
	}

	/** @return the profiles of a series, by part and title */
	public List<SeriesMember> listSeriesMembers(String series) throws SQLException {
		List<SeriesMember> members = new ArrayList<>();
		try (Connection con = dbService_.getConnection();
				java.sql.PreparedStatement stmt = con.prepareStatement(
					"SELECT GAM.ID, GAM.TITLE, SER.PART FROM SERIES SER JOIN GAMES GAM ON GAM.ID = SER.GAME_ID WHERE LOWER(SER.NAME) = LOWER(?) ORDER BY CASE WHEN SER.PART = 0 THEN 1 ELSE 0 END, SER.PART, LOWER(GAM.TITLE)")) {
			stmt.setString(1, series);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next())
					members.add(new SeriesMember(rs.getInt(1), rs.getString(2), rs.getInt(3)));
			}
		}
		return members;
	}

	/** @return the names of all series, sorted */
	public List<String> listSeriesNames() throws SQLException {
		List<String> names = new ArrayList<>();
		try (Connection con = dbService_.getConnection(); Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(LIST_SERIES_NAMES_QRY)) {
			while (rs.next())
				names.add(rs.getString(1));
		}
		return names;
	}

	public void remove(Profile profile, boolean removeConfig, boolean removeCustomMapper, boolean removeCaptures) throws SQLException {
		remove(profile, removeConfig, removeCustomMapper, removeCaptures, false);
	}

	/**
	 * alpha 52: with the choice for the backups of its saved games: deleted (at both places), or else kept in savegames\deleted
	 * (copied and checked first; if that fails, nothing of the profile is removed)
	 */
	public void remove(Profile profile, boolean removeConfig, boolean removeCustomMapper, boolean removeCaptures, boolean removeSavegames) throws SQLException {
		if (!removeSavegames) {
			try {
				org.dbgl.util.SavegameBackup.keepOfDeleted(profile);
			} catch (IOException | RuntimeException e) {
				throw new SQLException("The backups of saved games of " + profile.getTitle() + " could not be kept, the profile was not removed: " + e.getMessage(), e);
			}
		}
		try (Transaction transaction = dbService_.startTransaction()) {
			actionOnEntity(transaction, DEL_NTVCMD_QRY, "remove profile native commands", profile);
			actionOnEntity(transaction, DEL_SESSIONS_QRY, "remove profile play sessions", profile);
			actionOnEntity(transaction, DEL_SERIES_QRY, "remove profile series", profile);
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
			if (removeSavegames) { // alpha 52: a choice of its own (before, they went with the screenshots, in whose folder they were)
				File savegames = org.dbgl.util.SavegameBackup.dir(profile);
				if (savegames.isDirectory())
					FilesUtils.removeFilesInDirAndDir(savegames);
				File earlier = org.dbgl.util.SavegameBackup.oldDir(profile);
				if (earlier.isDirectory())
					FilesUtils.removeFilesInDirAndDir(earlier);
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
			// alpha 53: the own lines DBGL keeps as they are ("SET TEMP=...", "win" after "cd \\WINDOWS") kept in the copy as well,
			// as when a profile is read from its file; before, the copy had lost them
			org.dbgl.util.UnknownAutoexecLines.keepOnRead(duplicate.getConfiguration().getAutoexec());
			duplicate.getConfiguration().setCharset(profile.getConfiguration().getCharset()); // the copy in the same charset as the file
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

	/**
	 * Alpha 50: starts of a profile that happened in another installation (on a USB stick made from this one) added to its
	 * number here; the time of its last start becomes the later one of both.
	 */
	public void addRuns(int profileId, int runs, long lastRunMillis) throws SQLException {
		if ((runs <= 0) && (lastRunMillis <= 0))
			return;
		try (Transaction transaction = dbService_.startTransaction()) {
			try (PreparedStatement stmt = transaction.prepareStatement("UPDATE GAMES SET STATS_RUNS = (STATS_RUNS + ?) WHERE ID = ?")) {
				stmt.setInt(1, Math.max(runs, 0));
				stmt.setInt(2, profileId);
				stmt.executeUpdate();
			}
			if (lastRunMillis > 0) {
				try (PreparedStatement stmt = transaction.prepareStatement("UPDATE GAMES SET STATS_LASTRUN = ? WHERE ID = ? AND (STATS_LASTRUN IS NULL OR STATS_LASTRUN < ?)")) {
					java.sql.Timestamp last = new java.sql.Timestamp(lastRunMillis);
					stmt.setTimestamp(1, last);
					stmt.setInt(2, profileId);
					stmt.setTimestamp(3, last);
					stmt.executeUpdate();
				}
			}
			transaction.commit();
		}
	}

	/** add to the time the profile was played, in seconds, and keep this time as a play session (started at 'startedMillis') */
	public void addPlayTime(int profileId, long startedMillis, long seconds) throws SQLException {
		if (seconds <= 0)
			return; // nothing played (or the clock was set back)
		seconds = Math.min(seconds, MAX_SESSION_SECONDS);
		try (Transaction transaction = dbService_.startTransaction()) {
			try (PreparedStatement stmt = transaction.prepareStatement(ADD_PLAYTIME_QRY)) {
				stmt.setLong(1, seconds);
				stmt.setInt(2, profileId);
				stmt.executeUpdate();
			}
			try (PreparedStatement stmt = transaction.prepareStatement(ADD_SESSION_QRY)) {
				stmt.setInt(1, profileId);
				stmt.setTimestamp(2, new java.sql.Timestamp(startedMillis));
				stmt.setLong(3, seconds);
				stmt.executeUpdate();
			}
			transaction.commit();
		}
	}

	/** all play sessions (since 0.101 alpha 44; before, only the total play time per profile was kept) */
	public List<PlaySession> listPlaySessions() throws SQLException {
		List<PlaySession> result = new ArrayList<>();
		try (Connection con = dbService_.getConnection(); Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(LIST_SESSIONS_QRY)) {
			while (rs.next())
				result.add(new PlaySession(rs.getInt(1), new java.util.Date(rs.getTimestamp(2).getTime()), rs.getInt(3)));
		}
		return result;
	}

	/** how often a profile was played (with sessions, since alpha 44), and its last session (null: none) */
	public record SessionSummary(int count, PlaySession last) {
	}

	public SessionSummary sessionSummary(int profileId) throws SQLException {
		try (Connection con = dbService_.getConnection();
				PreparedStatement count = con.prepareStatement("SELECT COUNT(*) FROM PLAYSESSIONS WHERE GAME_ID = ?");
				PreparedStatement last = con.prepareStatement("SELECT STARTED, SECONDS FROM PLAYSESSIONS WHERE GAME_ID = ? ORDER BY STARTED DESC LIMIT 1")) {
			count.setInt(1, profileId);
			last.setInt(1, profileId);
			int n;
			try (ResultSet rs = count.executeQuery()) {
				rs.next();
				n = rs.getInt(1);
			}
			try (ResultSet rs = last.executeQuery()) {
				return new SessionSummary(n, rs.next() ? new PlaySession(profileId, new java.util.Date(rs.getTimestamp(1).getTime()), rs.getInt(2)): null);
			}
		}
	}

	/** what is left of removed profiles: play sessions, and entries in the log */
	public int[] countRemovedProfileData() throws SQLException {
		try (Connection con = dbService_.getConnection(); Statement stmt = con.createStatement()) {
			int[] result = new int[2];
			try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*)" + REMOVED_SESSIONS_WHERE)) {
				rs.next();
				result[0] = rs.getInt(1);
			}
			try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*)" + REMOVED_LOG_WHERE)) {
				rs.next();
				result[1] = rs.getInt(1);
			}
			return result;
		}
	}

	/** remove the play sessions and the log entries of profiles that were removed */
	public void removeRemovedProfileData() throws SQLException {
		try (Transaction transaction = dbService_.startTransaction(); Statement stmt = transaction.createStatement()) {
			stmt.executeUpdate("DELETE" + REMOVED_SESSIONS_WHERE);
			stmt.executeUpdate("DELETE" + REMOVED_SERIES_WHERE); // alpha 49: of profiles removed with an older DBGL
			stmt.executeUpdate("DELETE" + REMOVED_LOG_WHERE);
			transaction.commit();
		}
	}

	/** when duplicates are merged: the play time and the play sessions of 'from' go to 'to' */
	public void movePlayTime(Profile from, Profile to) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			try (PreparedStatement stmt = transaction.prepareStatement(MOVE_PLAYTIME_QRY)) {
				stmt.setInt(1, from.getId());
				stmt.setInt(2, to.getId());
				stmt.executeUpdate();
			}
			try (PreparedStatement stmt = transaction.prepareStatement(MOVE_SESSIONS_QRY)) {
				stmt.setInt(1, to.getId());
				stmt.setInt(2, from.getId());
				stmt.executeUpdate();
			}
			transaction.commit();
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
				if (resultset.next()) {
					Profile profile = toProfile(con, resultset, dosboxVersions);
					readSeries(con, List.of(profile));
					return profile;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read profile " + profileId}));
		}
		return null; // not found
	}
}
