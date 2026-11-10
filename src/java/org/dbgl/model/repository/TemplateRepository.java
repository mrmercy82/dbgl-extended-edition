/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-07. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dbgl.model.NativeCommand;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.entity.LogEntry;
import org.dbgl.model.entity.LogEntry.Event;
import org.dbgl.model.factory.TemplateFactory;
import org.dbgl.service.DatabaseService.Transaction;
import org.dbgl.service.TextService;
import org.dbgl.util.FilesUtils;


public class TemplateRepository extends LoggingRepository<Template> {

	private static final String CREATE_QRY = "INSERT INTO TEMPLATES(TITLE, ISDEFAULT, DBVERSION_ID) VALUES (?, ?, ?)";
	private static final String CREATE_NTVCMD_QRY = "INSERT INTO NATIVECOMMANDS(COMMAND, PARAMETERS, CWD, WAITFOR, ORDERNR, GAME_ID, TEMPLATE_ID) VALUES (?, ?, ?, ?, ?, NULL, ?)";
	private static final String READ_QRY = "SELECT ID, TITLE, ISDEFAULT, DBVERSION_ID, STATS_CREATED, STATS_LASTMODIFY, STATS_LASTRUN, STATS_RUNS FROM TEMPLATES ORDER BY ID";
	private static final String READ_BY_ID_QRY = "SELECT ID, TITLE, ISDEFAULT, DBVERSION_ID, STATS_CREATED, STATS_LASTMODIFY, STATS_LASTRUN, STATS_RUNS FROM TEMPLATES WHERE ID = ?";
	private static final String READ_NTVCMD_QRY = "SELECT COMMAND, PARAMETERS, CWD, WAITFOR, ORDERNR FROM NATIVECOMMANDS WHERE GAME_ID IS NULL AND TEMPLATE_ID = ? ORDER BY ORDERNR";
	private static final String UPD_QRY = "UPDATE TEMPLATES SET TITLE = ?, ISDEFAULT = ?, DBVERSION_ID = ?, STATS_LASTMODIFY = CURRENT_TIMESTAMP WHERE ID = ?";
	private static final String UPD_NODEFAULT_QRY = "UPDATE TEMPLATES SET ISDEFAULT = FALSE";
	private static final String DEL_QRY = "DELETE FROM TEMPLATES WHERE ID = ?";
	private static final String DEL_NTVCMD_QRY = "DELETE FROM NATIVECOMMANDS WHERE GAME_ID IS NULL AND TEMPLATE_ID = ?";

	private static final String REGISTER_RUN_QRY = "UPDATE TEMPLATES SET STATS_LASTRUN = CURRENT_TIMESTAMP, STATS_RUNS = (STATS_RUNS + 1) WHERE ID = ?";

	public TemplateRepository() {
		super();
	}

	public Template add(Template template) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			doAdd(transaction, template);
			log(transaction, LogEntry.Event.ADD, LogEntry.EntityType.TEMPLATE, template);
			template.saveConfiguration();
			transaction.commit();
			return template;
		} catch (SQLException | IOException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"add template"}));
		}
	}

	private static void doAdd(Transaction transaction, Template template) throws SQLException {
		try (PreparedStatement pstmt = transaction.prepareStatement(CREATE_QRY)) {
			if (template.isDefault()) {
				try (Statement stmt = transaction.createStatement()) {
					stmt.executeUpdate(UPD_NODEFAULT_QRY);
				}
			}
			pstmt.setString(1, template.getTitle());
			pstmt.setBoolean(2, template.isDefault());
			pstmt.setInt(3, template.getDosboxVersion().getId());
			pstmt.executeUpdate();
			template.setId(identity(transaction));
			addNativeCommands(transaction, template);

			template.setConfigurationFileLocationByIdentifiers();
		}
	}

	private static void addNativeCommands(Transaction transaction, Template template) throws SQLException {
		List<NativeCommand> commands = template.getNativeCommands();
		for (int i = 0; i < commands.size(); i++) {
			NativeCommand cmd = commands.get(i);
			if (!cmd.isDosboxCommand()) {
				try (PreparedStatement stmt = transaction.prepareStatement(CREATE_NTVCMD_QRY)) {
					stmt.setString(1, cmd.getCommand().getPath());
					stmt.setString(2, cmd.getParameters());
					stmt.setString(3, cmd.getCwd().getPath());
					stmt.setBoolean(4, cmd.isWaitFor());
					stmt.setInt(5, i);
					stmt.setInt(6, template.getId());
					stmt.executeUpdate();
				} catch (SQLException e) {
					e.printStackTrace();
					throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"add template native commands"}));
				}
			}
		}
	}

	public List<Template> listAll(List<DosboxVersion> dosboxVersions) throws SQLException {
		try (Connection con = dbService_.getConnection(); Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(READ_QRY);) {
			List<Template> templatesList = new ArrayList<>();
			while (rs.next())
				templatesList.add(toTemplate(con, rs, dosboxVersions));
			return templatesList;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read templates"}));
		}
	}

	private static Template toTemplate(Connection con, ResultSet rs, List<DosboxVersion> dosboxVersions) throws SQLException {
		return TemplateFactory.create(rs.getInt(1), rs.getString(2), rs.getBoolean(3), listNativeCommands(con, rs.getInt(1)), rs.getInt(4), rs.getTimestamp(5), rs.getTimestamp(6), rs.getTimestamp(7),
			rs.getInt(8), dosboxVersions);
	}

	private static List<NativeCommand> listNativeCommands(Connection con, int templateId) throws SQLException {
		List<NativeCommand> nativeCommands = new ArrayList<>();
		try (PreparedStatement stmt = con.prepareStatement(READ_NTVCMD_QRY)) {
			stmt.setInt(1, templateId);
			try (ResultSet resultset = stmt.executeQuery()) {
				while (resultset.next())
					nativeCommands.add(new NativeCommand(resultset.getString(1), resultset.getString(2), resultset.getString(3), resultset.getBoolean(4), resultset.getInt(5)));
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read template native commands"}));
		}
		NativeCommand.insertDosboxCommand(nativeCommands);
		return nativeCommands;
	}

	public void update(Template template) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction(); PreparedStatement pstmt = transaction.prepareStatement(UPD_QRY)) {
			if (template.isDefault()) {
				try (Statement stmt = transaction.createStatement()) {
					stmt.executeUpdate(UPD_NODEFAULT_QRY);
				}
			}
			pstmt.setString(1, template.getTitle());
			pstmt.setBoolean(2, template.isDefault());
			pstmt.setInt(3, template.getDosboxVersion().getId());
			pstmt.setInt(4, template.getId());
			pstmt.executeUpdate();
			actionOnEntity(transaction, DEL_NTVCMD_QRY, "remove template native commands", template);
			addNativeCommands(transaction, template);
			log(transaction, LogEntry.Event.EDIT, LogEntry.EntityType.TEMPLATE, template);
			template.saveConfiguration();
			transaction.commit();
		} catch (SQLException | IOException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"update template"}));
		}
	}

	public void remove(Template template, boolean removeConfig) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			actionOnEntity(transaction, DEL_NTVCMD_QRY, "remove template native commands", template);
			actionOnEntity(transaction, DEL_QRY, "remove template", template);
			log(transaction, LogEntry.Event.REMOVE, LogEntry.EntityType.TEMPLATE, template);
			if (removeConfig) {
				FilesUtils.removeFile(template.getConfigurationCanonicalFile());
			}
			transaction.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		}
	}

	public Template duplicate(Template template) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			log(transaction, LogEntry.Event.DUPLICATE, LogEntry.EntityType.TEMPLATE, template);
			Template duplicate = TemplateFactory.createCopy(template);
			duplicate.loadConfigurationData(TextService.getInstance(), template.getConfigurationString(), template.getConfigurationCanonicalFile());
			duplicate.getConfiguration().setCharset(template.getConfiguration().getCharset()); // the copy in the same charset as the file
			doAdd(transaction, duplicate);
			log(transaction, LogEntry.Event.ADD, LogEntry.EntityType.TEMPLATE, duplicate);
			duplicate.saveConfiguration();
			transaction.commit();
			return duplicate;
		} catch (SQLException | IOException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"duplicate template"}));
		}
	}

	public Template registerRun(Template template) throws SQLException {
		return register(template, REGISTER_RUN_QRY, "register run template", LogEntry.Event.RUN);
	}

	private Template register(Template template, String query, String action, Event event) throws SQLException {
		try (Transaction transaction = dbService_.startTransaction()) {
			actionOnEntity(transaction, query, action, template);
			log(transaction, event, LogEntry.EntityType.TEMPLATE, template);
			transaction.commit();
			return getById(template.getId(), Arrays.asList(template.getDosboxVersion()));
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		}
	}

	private Template getById(int templateId, List<DosboxVersion> dosboxVersions) throws SQLException {
		try (Connection con = dbService_.getConnection(); PreparedStatement stmt = con.prepareStatement(READ_BY_ID_QRY)) {
			stmt.setInt(1, templateId);
			try (ResultSet resultset = stmt.executeQuery()) {
				if (resultset.next())
					return toTemplate(con, resultset, dosboxVersions);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(TextService.getInstance().get("database.error.query", new Object[] {"read template " + templateId}));
		}
		return null; // not found
	}
}
