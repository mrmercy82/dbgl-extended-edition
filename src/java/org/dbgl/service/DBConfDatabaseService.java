/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Copyright (C) 2026  mrmercy82 (DBGL Extended Edition, written with the help of AI)
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

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.dbgl.model.entity.SharedConf;
import org.dbgl.util.searchengine.WebSearchEngine;


/**
 * The configurations of DBConf (the shared configurations of DBGL users), kept in a database next to DBGL (db/dbconf.*), as in
 * DBGL 0.100 beta 18 by Ronald Blankendaal (who made the database from share.dbgl.org). Alpha 53: read once and kept in
 * memory (a few hundred configurations), opened read-only, so that DBGL in a folder without write access can read it too; a
 * missing database is no error: DBConf is then not offered.
 */
public final class DBConfDatabaseService {

	public static final String DATA = "dbconf.data";
	public static final String SCRIPT = "dbconf.script";
	public static final String PROPERTIES = "dbconf.properties";

	private static final String QUERY = "SELECT NOTES, GAME_TITLE, GAME_VERSION, GAME_YEAR, INCR_CONFIGURATION, FULL_CONFIGURATION, EXPLANATION, DOSBOX_TITLE, DOSBOX_VERSION FROM CONFS ORDER BY GAME_TITLE, ID";

	private static List<SharedConf> confs_;

	private DBConfDatabaseService() {
	}

	/** the database files without their extension (e.g. "db/dbconf" in DBGL's folder for its data) */
	public static File databaseBase() {
		String conn = SettingsService.getInstance().getValue("dbconf_database", "connectionstring");
		File file = FileLocationService.getFileFromConnectionString(conn);
		return file == null ? null: new org.dbgl.model.FileLocation(file.getPath(), FileLocationService.getInstance().dataRelative()).getCanonicalFile();
	}

	/** true if the database is there */
	public static synchronized boolean available() {
		return !all().isEmpty();
	}

	/** all configurations, sorted by title; empty if the database is missing or cannot be read */
	public static synchronized List<SharedConf> all() {
		if (confs_ == null) {
			File base = databaseBase();
			try {
				confs_ = base == null ? List.of(): load(base);
			} catch (SQLException e) {
				System.err.println("DBConf could not be read: " + e.getMessage());
				confs_ = List.of();
			}
		}
		return confs_;
	}

	/** read the database at base ("…/db/dbconf"), read-only */
	public static List<SharedConf> load(File base) throws SQLException {
		if (!new File(base.getPath() + ".script").isFile())
			return List.of();
		List<SharedConf> result = new ArrayList<>();
		try {
			Class.forName("org.hsqldb.jdbcDriver");
		} catch (ClassNotFoundException e) {
			throw new SQLException(e);
		}
		String url = "jdbc:hsqldb:file:" + base.getPath() + ";ifexists=true;readonly=true;shutdown=true";
		try (Connection con = DriverManager.getConnection(url, "sa", ""); Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(QUERY)) {
			while (rs.next())
				result.add(new SharedConf(null, rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
						rs.getString(9)));
		}
		return Collections.unmodifiableList(result);
	}

	/**
	 * The configurations whose title contains the one given (case does not matter), as share.dbgl.org searched; those with the
	 * same title first. For a list to choose from.
	 */
	public static List<SharedConf> search(String title) {
		return search(all(), title);
	}

	static List<SharedConf> search(List<SharedConf> confs, String title) {
		String t = title == null ? "": title.strip().toLowerCase(Locale.ROOT);
		if (t.isEmpty())
			return List.of();
		String same = WebSearchEngine.comparable(title);
		List<SharedConf> first = new ArrayList<>(), rest = new ArrayList<>();
		for (SharedConf c: confs) {
			String g = c.getGameTitle() == null ? "": c.getGameTitle();
			if (!same.isEmpty() && same.equals(WebSearchEngine.comparable(g)))
				first.add(c);
			else if (g.toLowerCase(Locale.ROOT).contains(t))
				rest.add(c);
		}
		first.addAll(rest);
		return first;
	}

	/**
	 * The configurations for exactly this game: the same title when compared without case, accents and punctuation ("Doom" is
	 * not "Doom II"). For using a configuration without asking.
	 */
	public static List<SharedConf> exact(String title) {
		return exact(all(), title);
	}

	public static List<SharedConf> exact(List<SharedConf> confs, String title) {
		String same = WebSearchEngine.comparable(title);
		if (same.isEmpty())
			return List.of();
		return confs.stream().filter(c -> same.equals(WebSearchEngine.comparable(c.getGameTitle()))).toList();
	}
}
