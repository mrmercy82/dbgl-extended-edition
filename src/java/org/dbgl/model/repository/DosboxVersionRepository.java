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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.entity.LogEntry;
import org.dbgl.model.factory.DosboxVersionFactory;
import org.dbgl.service.DatabaseService.Transaction;
import org.dbgl.service.TextService;


public class DosboxVersionRepository extends LoggingRepository<DosboxVersion> {

	private static final String CREATE_QRY = "INSERT INTO DOSBOXVERSIONS(TITLE, FAMILY, VERSION, ISDEFAULT, MULTICONF, USINGCURSES, PATH, EXEFILE, PARAMETERS, CONFFILE, DYNAMIC_OPTIONS) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	private static final String READ_QRY = "SELECT ID, TITLE, FAMILY, VERSION, ISDEFAULT, MULTICONF, USINGCURSES, PATH, EXEFILE, PARAMETERS, CONFFILE, STATS_CREATED, STATS_LASTMODIFY, STATS_LASTRUN, STATS_RUNS, DYNAMIC_OPTIONS FROM DOSBOXVERSIONS ORDER BY TITLE";
	private static final String UPD_QRY = "UPDATE DOSBOXVERSIONS SET TITLE = ?, FAMILY = ?, VERSION = ?, ISDEFAULT = ?, MULTICONF = ?, USINGCURSES = ?, PATH = ?, EXEFILE = ?, PARAMETERS = ?, CONFFILE = ?, STATS_LASTMODIFY = CURRENT_TIMESTAMP, DYNAMIC_OPTIONS = ? WHERE ID = ?";
	private static final String UPD_NODEFAULT_QRY = "UPDATE DOSBOXVERSIONS SET ISDEFAULT = FALSE";
	private static final String DEL_QRY = "DELETE FROM DOSBOXVERSIONS WHERE ID = ?";

	private static final String REGISTER_RUN_QRY = "UPDATE DOSBOXVERSIONS SET STATS_LASTRUN = CURRENT_TIMESTAMP, STATS_RUNS = (STATS_RUNS + 1) WHERE ID = ?";

	private static final String USAGE_QRY = "SELECT TITLE FROM GAMES WHERE DBVERSION_ID = ? UNION ALL SELECT TITLE FROM TEMPLATES WHERE DBVERSION_ID = ?";

	public DosboxVersionRepository() {
		super();
	}

	public DosboxVersion add(DosboxVersion dbv) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction(); Statement stmt = transaction.createStatement(); PreparedStatement pstmt = transaction.prepareStatement(CREATE_QRY)) {
			if (dbv.isDefault())
				stmt.executeUpdate(UPD_NODEFAULT_QRY);
			pstmt.setString(1, dbv.getTitle());
			pstmt.setString(2, dbv.getFamily());
			pstmt.setString(3, dbv.getVersion());
			pstmt.setBoolean(4, dbv.isDefault());
			pstmt.setBoolean(5, dbv.isMultiConfig());
			pstmt.setBoolean(6, dbv.isUsingCurses());
			pstmt.setString(7, dbv.getPath().getPath());
			pstmt.setString(8, dbv.getExe().getPath());
			pstmt.setString(9, dbv.getExecutableParameters());
			pstmt.setString(10, dbv.getConfiguration().getFile().getPath());
			pstmt.setObject(11, dbv.getDynamicOptions());
			pstmt.executeUpdate();
			dbv.setId(identity(transaction));
			log(transaction, LogEntry.Event.ADD, LogEntry.EntityType.DOSBOXVERSION, dbv);
			transaction.commit();
			return dbv;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"add dosboxversion"}));
		}
	}

	@SuppressWarnings("unchecked")
	public List<DosboxVersion> listAll() throws SQLException {
		try (Connection con = dbService_.getConnection(); Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(READ_QRY)) {
			List<DosboxVersion> dbversionsList = new ArrayList<>();
			while (rs.next())
				dbversionsList.add(
					DosboxVersionFactory.create(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getBoolean(6), rs.getBoolean(7), (LinkedHashMap<String, String>)rs.getObject(16),
						rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getTimestamp(12), rs.getTimestamp(13), rs.getTimestamp(14), rs.getInt(15)));
			return dbversionsList;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read dosboxversions"}));
		}
	}

	public void update(DosboxVersion dbv) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction(); Statement stmt = transaction.createStatement(); PreparedStatement pstmt = transaction.prepareStatement(UPD_QRY)) {
			if (dbv.isDefault())
				stmt.executeUpdate(UPD_NODEFAULT_QRY);
			pstmt.setString(1, dbv.getTitle());
			pstmt.setString(2, dbv.getFamily());
			pstmt.setString(3, dbv.getVersion());
			pstmt.setBoolean(4, dbv.isDefault());
			pstmt.setBoolean(5, dbv.isMultiConfig());
			pstmt.setBoolean(6, dbv.isUsingCurses());
			pstmt.setString(7, dbv.getPath().getPath());
			pstmt.setString(8, dbv.getExe().getPath());
			pstmt.setString(9, dbv.getExecutableParameters());
			pstmt.setString(10, dbv.getConfiguration().getFile().getPath());
			pstmt.setObject(11, dbv.getDynamicOptions());
			pstmt.setInt(12, dbv.getId());
			pstmt.executeUpdate();
			log(transaction, LogEntry.Event.EDIT, LogEntry.EntityType.DOSBOXVERSION, dbv);
			transaction.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"update dosboxversion"}));
		}
	}

	public void remove(DosboxVersion dbv) throws SQLException {
		try {
			int totalUsageAmount = 0;
			List<String> usages = new ArrayList<>();
			try (Connection con = dbService_.getConnection(); PreparedStatement stmt = con.prepareStatement(USAGE_QRY)) {
				stmt.setInt(1, dbv.getId());
				stmt.setInt(2, dbv.getId());
				try (ResultSet resultset = stmt.executeQuery()) {
					while (resultset.next()) {
						if (totalUsageAmount++ < 10)
							usages.add(resultset.getString(1));
					}
				}
			}
			if (totalUsageAmount > 0)
				throw new SQLException(
						TextService.getInstance().get("general.error.profilesandtemplatesusingdbversion", new Object[] {StringUtils.EMPTY, totalUsageAmount, String.join(", ", usages)}));
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		}

		try (Transaction transaction = dbService_.startTransaction()) {
			actionOnEntity(transaction, DEL_QRY, "remove dosboxversion", dbv);
			log(transaction, LogEntry.Event.REMOVE, LogEntry.EntityType.DOSBOXVERSION, dbv);
			transaction.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		}
	}

	public void registerRun(DosboxVersion dbv) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			actionOnEntity(transaction, REGISTER_RUN_QRY, "register run dosboxversion", dbv);
			log(transaction, LogEntry.Event.RUN, LogEntry.EntityType.DOSBOXVERSION, dbv);
			transaction.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		}
	}

	public static DosboxVersion findBestMatch(List<DosboxVersion> dbversionsList, DosboxVersion db) {
		// Start by looking for a perfect title match
		Optional<DosboxVersion> result = dbversionsList.stream().filter(x -> x.getTitle().equals(db.getTitle())).findFirst();
		if (result.isPresent())
			return result.get();

		// Check if the distance with 'the default' equals 0
		DosboxVersion theDefault = findDefault(dbversionsList);
		if (theDefault != null && theDefault.distance(db) == 0)
			return theDefault;

		// Otherwise, find the DosboxVersion with the lowest distance
		return dbversionsList.stream().min(Comparator.comparing(x -> x.distance(db))).orElse(null);
	}
}
