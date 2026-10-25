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
import java.util.List;
import org.dbgl.model.entity.IEntity;
import org.dbgl.model.entity.ITitledEntity;
import org.dbgl.model.entity.LogEntry;
import org.dbgl.service.DatabaseService.Transaction;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;


public class LoggingRepository<T extends IEntity> extends BaseRepository<T> {

	private static final String CREATE_QRY = "INSERT INTO LOG(EVENT, ENTITY_TYPE, ENTITY_ID, ENTITY_TITLE) VALUES(?, ?, ?, ?)";
	private static final String READ_QRY = "SELECT ID, TIME, EVENT, ENTITY_TYPE, ENTITY_ID, ENTITY_TITLE FROM LOG";
	private static final String DEL_QRY = "DELETE FROM LOG";

	protected static void log(Transaction transaction, LogEntry.Event event, LogEntry.EntityType entityType, ITitledEntity titledEntity) throws SQLException {
		if (SettingsService.getInstance().getBooleanValue("log", "enabled")) {
			try (PreparedStatement pstmt = transaction.prepareStatement(CREATE_QRY)) {
				pstmt.setByte(1, (byte)event.ordinal());
				pstmt.setByte(2, (byte)entityType.ordinal());
				pstmt.setInt(3, titledEntity.getId());
				pstmt.setString(4, titledEntity.getTitle());
				pstmt.executeUpdate();
			} catch (SQLException e) {
				e.printStackTrace();
				throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"add log entry"}));
			}
		}
	}

	public List<LogEntry> list(String whereClause, String orderByClause) throws SQLException {
		try (Connection con = dbService_.getConnection(); Statement stmt = con.createStatement(); ResultSet resultset = stmt.executeQuery(READ_QRY + whereClause + orderByClause)) {
			List<LogEntry> logEntriesList = new ArrayList<>();
			while (resultset.next())
				logEntriesList.add(new LogEntry(resultset.getInt(1), resultset.getTimestamp(2), resultset.getByte(3), resultset.getByte(4), resultset.getInt(5), resultset.getString(6)));
			return logEntriesList;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read log entries"}));
		}
	}

	public void clear() throws SQLException {
		try (Transaction transaction = dbService_.startTransaction(); Statement stmt = transaction.createStatement()) {
			stmt.executeUpdate(DEL_QRY);
			transaction.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"clear log"}));
		}
	}
}
