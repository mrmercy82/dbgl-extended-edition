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
import org.apache.commons.lang3.StringUtils;
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

	/** alpha 53: how many entries the last cleanup merged into others of the same name */
	private int lastMerged_;

	public TitledEntityRepository() {
		super();
	}

	public int lastMerged() {
		return lastMerged_;
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
			stmt.setString(1, fit(tidy(value)));
			stmt.executeUpdate();
			return identity(transaction);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"add " + value}));
		}
	}

	public int cleanup() throws SQLException {
		try (Transaction transaction = dbService_.startTransaction(); Statement stmt = transaction.createStatement()) {
			lastMerged_ = mergeSame(transaction); // alpha 53: entries of the same name first, the ones left over are unused then
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
		String fitting = fit(tidy(value)); // alpha 52: as it was stored
		int exact = list.stream().filter(x -> x.getTitle().equals(fitting)).findFirst().map(ITitledEntity::getId).orElse(-1);
		if ((exact != -1) || (fitting == null))
			return exact;
		// alpha 53: the same name with other spaces or case ("Accolade, Inc." with a no-break space, "Role-playing (RPG)"): the entry there is
		return list.stream().filter(x -> same(x.getTitle(), fitting)).findFirst().map(ITitledEntity::getId).orElse(-1);
	}

	/**
	 * alpha 53: a name as it is kept: no-break and other special spaces (MobyGames gives "Accolade, Inc." with one) as normal ones,
	 * several in a row as one, none at the start or end. Before, such a name made a second entry next to "Accolade, Inc."
	 */
	public static String tidy(String value) {
		if (value == null)
			return null;
		return value.replaceAll("[\\s\\u00a0\\u2007\\u202f\\u2009\\u200a]+", " ").strip();
	}

	/** alpha 53: two names are the same entry: equal apart from spaces and case */
	public static boolean same(String a, String b) {
		return (a != null) && (b != null) && tidy(a).equalsIgnoreCase(tidy(b));
	}

	private static final String[][] MERGED = {{"DEVELOPERS", "NAME", "DEV_ID"}, {"PUBLISHERS", "NAME", "PUBL_ID"}, {"GENRES", "NAME", "GENRE_ID"}, {"STATUS", "STAT", "STAT_ID"},
		{"CUSTOM1", "VALUE", "CUST1_ID"}, {"CUSTOM2", "VALUE", "CUST2_ID"}, {"CUSTOM3", "VALUE", "CUST3_ID"}, {"CUSTOM4", "VALUE", "CUST4_ID"}};

	/**
	 * alpha 53: entries of the same name apart from spaces and case (e.g. "Accolade, Inc." with a normal and with a no-break space) become one:
	 * the profiles get the one most of them have (with equal counts the first), its name is tidied; the others are then unused
	 * and go with the cleanup. The number of entries merged into others.
	 */
	static int mergeSame(Transaction transaction) throws SQLException {
		int merged = 0;
		try (Statement stmt = transaction.createStatement()) {
			for (String[] t: MERGED) {
				List<int[]> uses = new ArrayList<>(); // id, count
				List<String> names = new ArrayList<>();
				try (ResultSet rs = stmt.executeQuery("SELECT E.ID, E." + t[1] + ", (SELECT COUNT(*) FROM GAMES G WHERE G." + t[2] + " = E.ID) FROM " + t[0] + " E ORDER BY E.ID")) {
					while (rs.next()) {
						uses.add(new int[] {rs.getInt(1), rs.getInt(3)});
						names.add(rs.getString(2));
					}
				}
				java.util.Map<String, Integer> keep = new java.util.LinkedHashMap<>(); // tidied lower name -> index in uses
				for (int i = 0; i < uses.size(); i++) {
					String key = tidy(names.get(i)).toLowerCase(java.util.Locale.ROOT);
					Integer k = keep.get(key);
					if ((k == null) || (uses.get(i)[1] > uses.get(k)[1]))
						keep.put(key, i);
				}
				for (int i = 0; i < uses.size(); i++) {
					int k = keep.get(tidy(names.get(i)).toLowerCase(java.util.Locale.ROOT));
					if (k == i) {
						if (!names.get(i).equals(tidy(names.get(i))) && !tidy(names.get(i)).isEmpty())
							try (PreparedStatement ps = transaction.prepareStatement("UPDATE " + t[0] + " SET " + t[1] + " = ? WHERE ID = ?")) {
								ps.setString(1, tidy(names.get(i)));
								ps.setInt(2, uses.get(i)[0]);
								ps.executeUpdate();
							}
						continue;
					}
					try (PreparedStatement ps = transaction.prepareStatement("UPDATE GAMES SET " + t[2] + " = ? WHERE " + t[2] + " = ?")) {
						ps.setInt(1, uses.get(k)[0]);
						ps.setInt(2, uses.get(i)[0]);
						ps.executeUpdate();
					}
					merged++;
				}
			}
		}
		return merged;
	}

	/** alpha 53: an entry of a list with the number of profiles that have it */
	public record Entry(int id, String name, int uses) {
	}

	/** alpha 53: entries of one list (DEVELOPERS, PUBLISHERS or GENRES) whose names are similar, the one most used first */
	public record Similar(String table, List<Entry> entries) {
	}

	/** words that only tell the kind of company, left out when comparing names */
	private static final java.util.Set<String> COMPANY_WORDS = java.util.Set.of("inc", "incorporated", "ltd", "limited", "llc", "gmbh", "corp", "corporation", "co", "kg", "ag", "sa",
		"plc", "bv", "nv", "srl", "spa", "the", "and");
	/** names of genres from MobyGames, IGDB, RAWG and others that mean the same */
	private static final java.util.Map<String, String> GENRE_WORDS = java.util.Map.of("strategy/tactics", "strategy", "strategy / tactics", "strategy", "dlc / add-on", "add-on",
		"dlc", "add-on", "role-playing (rpg)", "rpg", "role-playing", "rpg", "role playing", "rpg", "racing / driving", "racing", "driving", "racing", "puzzle elements", "puzzle");

	/**
	 * alpha 53: what two similar names have in common: for companies without the kind of company ("Inc.", "Ltd.", "GmbH",
	 * "The"), spaces, hyphens and punctuation ("Sierra On-Line, Inc." and "Sierra Online"); for genres also with the words of
	 * other sources taken as the same ("Strategy/Tactics" and "Strategy"); the order of several names does not count
	 */
	public static String similarKey(String table, String name) {
		java.util.TreeSet<String> parts = new java.util.TreeSet<>();
		for (String part: tidy(StringUtils.defaultString(name)).toLowerCase(java.util.Locale.ROOT).split(",")) {
			String p = part.strip();
			if (table.equals("GENRES"))
				p = GENRE_WORDS.getOrDefault(p, p);
			StringBuilder sb = new StringBuilder();
			for (String w: p.replace("&", " and ").split("[^\\p{L}\\p{N}]+"))
				if (!w.isEmpty() && (table.equals("GENRES") || !COMPANY_WORDS.contains(w)))
					sb.append(w);
			if (sb.length() > 0)
				parts.add(sb.toString());
		}
		return String.join("|", parts);
	}

	private static final String[][] SIMILAR = {{"DEVELOPERS", "NAME", "DEV_ID"}, {"PUBLISHERS", "NAME", "PUBL_ID"}, {"GENRES", "NAME", "GENRE_ID"}};

	/** alpha 53: groups of similar entries in the lists of developers, publishers and genres (not the same ones, see mergeSame) */
	public List<Similar> similar() throws SQLException {
		List<Similar> result = new ArrayList<>();
		try (Transaction transaction = dbService_.startTransaction(); Statement stmt = transaction.createStatement()) {
			for (String[] t: SIMILAR) {
				java.util.Map<String, List<Entry>> byKey = new java.util.LinkedHashMap<>();
				try (ResultSet rs = stmt.executeQuery("SELECT E.ID, E." + t[1] + ", (SELECT COUNT(*) FROM GAMES G WHERE G." + t[2] + " = E.ID) FROM " + t[0] + " E ORDER BY E." + t[1])) {
					while (rs.next()) {
						String key = similarKey(t[0], rs.getString(2));
						if (!key.isEmpty() && (rs.getInt(3) > 0))
							byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(new Entry(rs.getInt(1), rs.getString(2), rs.getInt(3)));
					}
				}
				for (List<Entry> l: byKey.values())
					if ((l.size() > 1) && (l.stream().map(e -> tidy(e.name()).toLowerCase(java.util.Locale.ROOT)).distinct().count() > 1)) {
						l.sort((a, c) -> Integer.compare(c.uses(), a.uses()));
						result.add(new Similar(t[0], l));
					}
			}
			transaction.commit();
		}
		return result;
	}

	/** alpha 53: the profiles of the other entries get the entry kept; the others are unused then (gone with the cleanup) */
	public void mergeInto(String table, int keep, List<Integer> others) throws SQLException {
		String column = java.util.Arrays.stream(SIMILAR).filter(t -> t[0].equals(table)).findFirst().orElseThrow()[2];
		try (Transaction transaction = dbService_.startTransaction(); PreparedStatement ps = transaction.prepareStatement("UPDATE GAMES SET " + column + " = ? WHERE " + column + " = ?")) {
			for (int other: others)
				if (other != keep) {
					ps.setInt(1, keep);
					ps.setInt(2, other);
					ps.addBatch();
				}
			ps.executeBatch();
			transaction.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"merge " + table}));
		}
	}

	/** the most a text column holds (VARCHAR(256)) */
	public static final int MAX_LENGTH = 256;

	/**
	 * alpha 52: a text that fits into its column: a longer one (e.g. the list of publishers of Doom from MobyGames) made saving the
	 * whole profile fail ("Database query 'update profile' failed!", reported on Vogons). It is cut after the last whole name
	 * that fits and ends with "...", so that it can be seen that something is missing.
	 */
	public static String fit(String value) {
		if ((value == null) || (value.length() <= MAX_LENGTH))
			return value;
		String cut = value.substring(0, MAX_LENGTH - 3);
		int at = Math.max(cut.lastIndexOf(", "), cut.lastIndexOf(" / "));
		if (at > MAX_LENGTH / 2)
			cut = cut.substring(0, at);
		return cut.stripTrailing() + "...";
	}

	/**
	 * alpha 53, as in DBGL 0.100 beta 18 by Ronald Blankendaal ("remax"): a path or parameters that fit into their column, cut
	 * at its width (no "...": it would not be a path either way); before, a longer one made saving fail
	 */
	public static String cut(String value) {
		return org.apache.commons.lang3.StringUtils.left(value, MAX_LENGTH);
	}
}
