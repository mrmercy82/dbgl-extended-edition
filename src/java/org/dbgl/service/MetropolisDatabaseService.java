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
package org.dbgl.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.util.StringRelatedUtils;
import org.eclipse.swt.widgets.Shell;
import org.hsqldb.jdbc.pool.JDBCPooledDataSource;


public class MetropolisDatabaseService {

	private static final String GET_VERSION = "SELECT MAJORVERSION, MINORVERSION FROM VERSION";

	private JDBCPooledDataSource dataSource_;

	private MetropolisDatabaseService() {
		SettingsService settings = SettingsService.getInstance();
		String connString = FileLocationService.getInstance().getCanonicalConnectionString(settings.getValue("mobygames_database", "connectionstring"));
		try {
			// Register the JDBC driver for dBase
			Class.forName("org.hsqldb.jdbcDriver");
			dataSource_ = new JDBCPooledDataSource();
			dataSource_.setDatabase(connString);
			dataSource_.setUser(settings.getValue("mobygames_database", "username"));
			dataSource_.setPassword(StringUtils.defaultString(settings.getValue("mobygames_database", "password")));
		} catch (ClassNotFoundException e) {
			Shell shell = new Shell();
			Mess_.on(shell).key("database.error.registerdriver", StringRelatedUtils.toString(e)).exception(e).fatal();
			throw new RuntimeException();
		}
	}

	public int[] getVersion() {
		try (Connection con = getConnection(); Statement stmt = con.createStatement(); ResultSet resultset = stmt.executeQuery(GET_VERSION)) {
			resultset.next();
			return new int[] {resultset.getInt(1), resultset.getInt(2)}; // major, minor
		} catch (SQLException e) {
			return new int[] {0, 0};
		}
	}

	private static class DatabaseServiceHolder {
		private static final MetropolisDatabaseService instance_ = new MetropolisDatabaseService();
	}

	public static MetropolisDatabaseService getInstance() {
		return DatabaseServiceHolder.instance_;
	}

	public Connection getConnection() throws SQLException {
		return dataSource_.getPooledConnection().getConnection();
	}

	public void shutdown(boolean compact) throws SQLException {
		try (Connection con = getConnection(); Statement stmt = con.createStatement()) {
			stmt.execute("SHUTDOWN" + (compact ? " COMPACT": ""));
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.shutdown"));
		}
	}
}
