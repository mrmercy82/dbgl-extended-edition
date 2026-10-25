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
import org.dbgl.model.entity.Filter;
import org.dbgl.model.entity.LogEntry;
import org.dbgl.service.DatabaseService.Transaction;
import org.dbgl.service.TextService;


public class FilterRepository extends LoggingRepository<Filter> {

	private static final String CREATE_QRY = "INSERT INTO FILTERS(TITLE, FILTER, CONF_FILTER) VALUES (?, ?, '')";
	private static final String READ_QRY = "SELECT ID, TITLE, FILTER FROM FILTERS";
	private static final String UPD_QRY = "UPDATE FILTERS SET TITLE = ?, FILTER = ? WHERE ID = ?";
	private static final String DEL_QRY = "DELETE FROM FILTERS WHERE ID = ?";

	public FilterRepository() {
		super();
	}

	public Filter add(Filter filter) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction(); PreparedStatement pstmt = transaction.prepareStatement(CREATE_QRY)) {
			pstmt.setString(1, filter.getTitle());
			pstmt.setString(2, filter.getFilter());
			pstmt.executeUpdate();
			filter.setId(identity(transaction));
			log(transaction, LogEntry.Event.ADD, LogEntry.EntityType.FILTER, filter);
			transaction.commit();
			return filter;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"add filter"}));
		}
	}

	public List<Filter> listAll() throws SQLException {
		try (Connection con = dbService_.getConnection(); Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(READ_QRY)) {
			List<Filter> filtersList = new ArrayList<>();
			while (rs.next())
				filtersList.add(new Filter(rs.getInt(1), rs.getString(2), rs.getString(3)));
			return filtersList;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read filters"}));
		}
	}

	public void update(Filter filter) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction(); PreparedStatement pstmt = transaction.prepareStatement(UPD_QRY)) {
			pstmt.setString(1, filter.getTitle());
			pstmt.setString(2, filter.getFilter());
			pstmt.setInt(3, filter.getId());
			pstmt.executeUpdate();
			log(transaction, LogEntry.Event.EDIT, LogEntry.EntityType.FILTER, filter);
			transaction.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"update filter"}));
		}
	}

	public void remove(Filter filter) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			actionOnEntity(transaction, DEL_QRY, "remove filter", filter);
			log(transaction, LogEntry.Event.REMOVE, LogEntry.EntityType.FILTER, filter);
			transaction.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		}
	}
}
