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
import java.util.stream.IntStream;
import org.dbgl.model.entity.ITitledEntity;
import org.dbgl.model.entity.TitledEntity;
import org.dbgl.service.DatabaseService.Transaction;
import org.dbgl.service.TextService;


public class TitledEntityRepository extends BaseRepository<ITitledEntity> {

	private static final String CREATE_DEVELOPER_QRY = "INSERT INTO DEVELOPERS(NAME) VALUES (?)";
	private static final String CREATE_PUBLISHER_QRY = "INSERT INTO PUBLISHERS(NAME) VALUES (?)";
	private static final String CREATE_GENRE_QRY = "INSERT INTO GENRES(NAME) VALUES (?)";
	private static final String CREATE_YEAR_QRY = "INSERT INTO PUBLYEARS(YEAR) VALUES (?)";
	private static final String CREATE_STATUS_QRY = "INSERT INTO STATUS(STAT) VALUES (?)";
	private static final String[] CREATE_CUSTOM_QRY = {"INSERT INTO CUSTOM1(VALUE) VALUES (?)", "INSERT INTO CUSTOM2(VALUE) VALUES (?)", "INSERT INTO CUSTOM3(VALUE) VALUES (?)",
			"INSERT INTO CUSTOM4(VALUE) VALUES (?)"};

	private static final String DEVELOPER_LIST_QRY = "SELECT ID, NAME FROM DEVELOPERS ORDER BY NAME";
	private static final String PUBLISHER_LIST_QRY = "SELECT ID, NAME FROM PUBLISHERS ORDER BY NAME";
	private static final String GENRE_LIST_QRY = "SELECT ID, NAME FROM GENRES ORDER BY NAME";
	private static final String PUBLYEAR_LIST_QRY = "SELECT ID, YEAR FROM PUBLYEARS ORDER BY YEAR";
	private static final String STATUS_LIST_QRY = "SELECT ID, STAT FROM STATUS ORDER BY STAT";
	private static final String[] CUSTOM_LIST_QRY = {"SELECT ID, VALUE FROM CUSTOM1 ORDER BY VALUE", "SELECT ID, VALUE FROM CUSTOM2 ORDER BY VALUE", "SELECT ID, VALUE FROM CUSTOM3 ORDER BY VALUE",
			"SELECT ID, VALUE FROM CUSTOM4 ORDER BY VALUE"};

	private static final String CLEANUP_QRY = "DELETE FROM DEVELOPERS WHERE ID NOT IN (SELECT DISTINCT DEV_ID FROM GAMES);"
			+ "DELETE FROM PUBLISHERS WHERE ID NOT IN (SELECT DISTINCT PUBL_ID FROM GAMES); DELETE FROM GENRES WHERE ID NOT IN (SELECT DISTINCT GENRE_ID FROM GAMES);"
			+ "DELETE FROM PUBLYEARS WHERE ID NOT IN (SELECT DISTINCT YEAR_ID FROM GAMES); DELETE FROM STATUS WHERE ID NOT IN (SELECT DISTINCT STAT_ID FROM GAMES);"
			+ "DELETE FROM CUSTOM1 WHERE ID NOT IN (SELECT DISTINCT CUST1_ID FROM GAMES); DELETE FROM CUSTOM2 WHERE ID NOT IN (SELECT DISTINCT CUST2_ID FROM GAMES);"
			+ "DELETE FROM CUSTOM3 WHERE ID NOT IN (SELECT DISTINCT CUST3_ID FROM GAMES); DELETE FROM CUSTOM4 WHERE ID NOT IN (SELECT DISTINCT CUST4_ID FROM GAMES)";

	public TitledEntityRepository() {
		super();
	}

	public List<ITitledEntity> listDevelopers(Transaction transaction) throws SQLException {
		return listAll(DEVELOPER_LIST_QRY, transaction);
	}

	public List<ITitledEntity> listPublishers(Transaction transaction) throws SQLException {
		return listAll(PUBLISHER_LIST_QRY, transaction);
	}

	public List<ITitledEntity> listGenres(Transaction transaction) throws SQLException {
		return listAll(GENRE_LIST_QRY, transaction);
	}

	public List<ITitledEntity> listYears(Transaction transaction) throws SQLException {
		return listAll(PUBLYEAR_LIST_QRY, transaction);
	}

	public List<ITitledEntity> listStatus(Transaction transaction) throws SQLException {
		return listAll(STATUS_LIST_QRY, transaction);
	}

	public List<ITitledEntity> listCustomValues(int index, Transaction transaction) throws SQLException {
		return listAll(CUSTOM_LIST_QRY[index], transaction);
	}

	public String[] developers() throws SQLException {
		return readToStrings(DEVELOPER_LIST_QRY);
	}

	public String[] publishers() throws SQLException {
		return readToStrings(PUBLISHER_LIST_QRY);
	}

	public String[] genres() throws SQLException {
		return readToStrings(GENRE_LIST_QRY);
	}

	public String[] years() throws SQLException {
		return readToStrings(PUBLYEAR_LIST_QRY);
	}

	public String[] statuses() throws SQLException {
		return readToStrings(STATUS_LIST_QRY);
	}

	public String[] customValues(int index) throws SQLException {
		return readToStrings(CUSTOM_LIST_QRY[index]);
	}

	public int findDeveloper(String developer, Transaction transaction) throws SQLException {
		return findIdByValue(listDevelopers(transaction), developer);
	}

	public int findPublisher(String publisher, Transaction transaction) throws SQLException {
		return findIdByValue(listPublishers(transaction), publisher);
	}

	public int findGenre(String genre, Transaction transaction) throws SQLException {
		return findIdByValue(listGenres(transaction), genre);
	}

	public int findYear(String year, Transaction transaction) throws SQLException {
		return findIdByValue(listYears(transaction), year);
	}

	public int findStatus(String status, Transaction transaction) throws SQLException {
		return findIdByValue(listStatus(transaction), status);
	}

	public int findCustomValue(int index, String value, Transaction transaction) throws SQLException {
		return findIdByValue(listCustomValues(index, transaction), value);
	}

	public static int addDeveloper(Transaction transaction, String developer) throws SQLException {
		return addValue(transaction, CREATE_DEVELOPER_QRY, developer);
	}

	public static int addPublisher(Transaction transaction, String publisher) throws SQLException {
		return addValue(transaction, CREATE_PUBLISHER_QRY, publisher);
	}

	public static int addGenre(Transaction transaction, String genre) throws SQLException {
		return addValue(transaction, CREATE_GENRE_QRY, genre);
	}

	public static int addYear(Transaction transaction, String year) throws SQLException {
		return addValue(transaction, CREATE_YEAR_QRY, year);
	}

	public static int addStatus(Transaction transaction, String status) throws SQLException {
		return addValue(transaction, CREATE_STATUS_QRY, status);
	}

	public static int addCustomValue(Transaction transaction, int index, String value) throws SQLException {
		return addValue(transaction, CREATE_CUSTOM_QRY[index], value);
	}

	private List<ITitledEntity> listAll(String query, Transaction transaction) throws SQLException {
		try {
			if (transaction == null) {
				try (Connection con = dbService_.getConnection(); Statement stmt = con.createStatement()) {
					return listAllWithStatement(query, stmt);
				}
			}
			try (Statement stmt = transaction.createStatement()) {
				return listAllWithStatement(query, stmt);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read KeyValuePairs"}));
		}
	}

	private String[] readToStrings(String query) throws SQLException {
		try {
			try (Connection con = dbService_.getConnection(); Statement stmt = con.createStatement()) {
				try (ResultSet resultset = stmt.executeQuery(query)) {
					List<String> s = new ArrayList<>();
					while (resultset.next())
						s.add(resultset.getString(2));
					return s.toArray(new String[s.size()]);
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read KeyValuePairs"}));
		}
	}

	private static List<ITitledEntity> listAllWithStatement(String query, Statement stmt) throws SQLException {
		try (ResultSet resultset = stmt.executeQuery(query)) {
			List<ITitledEntity> customList = new ArrayList<>();
			while (resultset.next())
				customList.add(new TitledEntity(resultset.getInt(1), resultset.getString(2)));
			return customList;
		}
	}

	private static int addValue(Transaction transaction, String query, String value) throws SQLException {
		try (PreparedStatement stmt = transaction.prepareStatement(query)) {
			stmt.setString(1, value);
			stmt.executeUpdate();
			return identity(transaction);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"add " + value}));
		}
	}

	public int cleanup() throws SQLException {
		try (Transaction transaction = dbService_.startTransaction(); Statement stmt = transaction.createStatement()) {
			for (String s: CLEANUP_QRY.split(";"))
				stmt.addBatch(s);
			int[] results = stmt.executeBatch();
			transaction.commit();
			return IntStream.of(results).sum();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"cleanup"}));
		}
	}

	private static int findIdByValue(List<ITitledEntity> list, String value) {
		return list.stream().filter(x -> x.getTitle().equals(value)).findFirst().map(ITitledEntity::getId).orElse(-1);
	}
}
