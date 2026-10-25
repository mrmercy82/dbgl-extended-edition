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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.IntStream;
import org.dbgl.model.ICanBeDefault;
import org.dbgl.model.entity.IEntity;
import org.dbgl.service.DatabaseService;
import org.dbgl.service.DatabaseService.Transaction;
import org.dbgl.service.TextService;


public abstract class BaseRepository<T extends IEntity> {

	private static final String IDENTITY_QRY = "CALL IDENTITY()";

	protected DatabaseService dbService_;

	protected BaseRepository() {
		dbService_ = DatabaseService.getInstance();
	}

	protected void actionOnEntity(Transaction transaction, String query, String action, T entity) throws SQLException {
		try (PreparedStatement stmt = transaction.prepareStatement(query)) {
			stmt.setInt(1, entity.getId());
			stmt.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {action}));
		}
	}

	protected static int identity(Transaction transaction) throws SQLException {
		try (Statement stmt = transaction.createStatement(); ResultSet resultset = stmt.executeQuery(IDENTITY_QRY)) {
			resultset.next();
			return resultset.getInt(1);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"get identity"}));
		}
	}

	public static <T extends IEntity> T findById(List<T> list, int id) {
		return list.stream().filter(x -> x.getId() == id).findAny().orElse(null);
	}

	public static <T extends IEntity> int findIndexById(List<T> list, int id) {
		return IntStream.range(0, list.size()).filter(i -> list.get(i).getId() == id).findFirst().orElse(-1);
	}

	public static <T extends IEntity> int indexOf(List<T> list, T entity) {
		return findIndexById(list, entity.getId());
	}

	public static <T extends ICanBeDefault> T findDefault(List<T> list) {
		return list.stream().filter(ICanBeDefault::isDefault).findAny().orElse(null);
	}

	public static <T extends ICanBeDefault> int indexOfDefault(List<T> list) {
		return IntStream.range(0, list.size()).filter(i -> list.get(i).isDefault()).findFirst().orElse(-1);
	}
}
