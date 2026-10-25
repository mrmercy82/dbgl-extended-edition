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
package org.dbgl.gui.dialog;

import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.Table_;
import org.dbgl.model.entity.LogEntry;
import org.dbgl.model.entity.LogEntry.EntityType;
import org.dbgl.model.repository.LoggingRepository;
import org.dbgl.service.ImageService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


public class LogDialog extends SizeControlledButtonDialog<Object> {

	private List<LogEntry> logEntries;
	private String orderByClause_;
	private LogEntry clickedEntry;
	private Integer clickedColumn;

	public LogDialog(Shell parent) {
		super(parent, "log");
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.log.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new FillLayout());

		Table table = Table_.on(contents_).header().build();

		Map<String, String> filterClauses = new LinkedHashMap<>();
		orderByClause_ = " ORDER BY ID";

		String[] titles = {text_.get("dialog.log.columns.time"), text_.get("dialog.log.columns.event"), text_.get("dialog.log.columns.entitytype"), text_.get("dialog.log.columns.entitytitle"),
				text_.get("dialog.log.columns.entityid")};

		for (int i = 0; i < titles.length; i++) {
			int columnId = i;
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText(titles[i]);
			column.addListener(SWT.Selection, e -> {
				TableColumn sortColumn = (TableColumn)e.widget;
				int sortDirection = SWT.UP;
				if (sortColumn == table.getSortColumn() && table.getSortDirection() == SWT.UP)
					sortDirection = SWT.DOWN;
				String direction = sortDirection == SWT.DOWN ? " DESC": StringUtils.EMPTY;
				switch (columnId) {
					case 0:
						orderByClause_ = " ORDER BY ID" + direction;
						break;
					case 1:
						orderByClause_ = " ORDER BY EVENT" + direction;
						break;
					case 2:
						orderByClause_ = " ORDER BY ENTITY_TYPE" + direction;
						break;
					case 3:
						orderByClause_ = " ORDER BY ENTITY_TITLE" + direction;
						break;
					case 4:
						orderByClause_ = " ORDER BY ENTITY_ID" + direction;
						break;
					default:
				}
				repopulateEntries(table, sortColumn, sortDirection, filterClauses);
			});
		}

		Menu menu = new Menu(shell_, SWT.POP_UP);
		menu.addListener(SWT.Show, event -> {
			MenuItem[] menuItems = menu.getItems();
			for (MenuItem menuItem: menuItems)
				menuItem.dispose();

			if ((clickedEntry != null) && (clickedColumn != null)) {
				MenuItem mi = new MenuItem(menu, SWT.PUSH);
				switch (clickedColumn) {
					case 0:
						mi.setText(titles[clickedColumn] + ": " + text_.toString(clickedEntry.getTime()));
						break;
					case 1:
						mi.setText(titles[clickedColumn] + ": " + getEventName(clickedEntry.getEvent()));
						break;
					case 2:
						mi.setText(titles[clickedColumn] + ": " + getEntityTypeName(clickedEntry.getEntityType()));
						break;
					case 3:
						mi.setText(titles[clickedColumn] + ": " + clickedEntry.getEntityTitle());
						break;
					case 4:
						mi.setText(titles[clickedColumn] + ": " + clickedEntry.getEntityId());
						break;
					default:
						break;
				}
				mi.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evnt) {
						MenuItem item = (MenuItem)evnt.widget;
						switch (clickedColumn) {
							case 0:
								Calendar cal = Calendar.getInstance();
								cal.setTime(clickedEntry.getTime());
								filterClauses.put("YEAR(TIME)=" + cal.get(Calendar.YEAR) + " AND MONTH(TIME)=" + (cal.get(Calendar.MONTH) + 1) + " AND DAY(TIME)=" + cal.get(Calendar.DAY_OF_MONTH),
									item.getText());
								break;
							case 1:
								filterClauses.put("EVENT=" + clickedEntry.getEvent().ordinal(), item.getText());
								break;
							case 2:
								filterClauses.put("ENTITY_TYPE=" + clickedEntry.getEntityType().ordinal(), item.getText());
								break;
							case 3:
								filterClauses.put("ENTITY_TITLE='" + clickedEntry.getEntityTitle() + "'", item.getText());
								break;
							case 4:
								filterClauses.put("ENTITY_ID=" + clickedEntry.getEntityId(), item.getText());
								break;
							default:
								break;
						}
						repopulateEntries(table, table.getSortColumn(), table.getSortDirection(), filterClauses);
					}
				});

				if (!filterClauses.isEmpty()) {
					@SuppressWarnings("unused")
					MenuItem menuItem = new MenuItem(menu, SWT.SEPARATOR);
				}
			}

			for (Map.Entry<String, String> filterClause: filterClauses.entrySet()) {
				MenuItem ci = new MenuItem(menu, SWT.PUSH);
				ci.setText(filterClause.getValue());
				ci.setImage(ImageService.getResourceImage(display_, ImageService.IMG_DELETE));
				ci.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evnt) {
						filterClauses.remove(filterClause.getKey());
						repopulateEntries(table, table.getSortColumn(), table.getSortDirection(), filterClauses);
					}
				});
			}
		});
		table.setMenu(menu);
		table.addListener(SWT.MouseDown, event -> {
			clickedEntry = null;
			clickedColumn = null;
			Point pt = new Point(event.x, event.y);
			TableItem item = table.getItem(pt);
			if (item == null)
				return;
			for (int i = 0; i < titles.length; i++) {
				if (item.getBounds(i).contains(pt)) {
					clickedEntry = logEntries.get(table.indexOf(item));
					clickedColumn = i;
					break;
				}
			}
		});

		repopulateEntries(table, table.getColumn(0), SWT.UP, filterClauses);
		for (int i = 0; i < titles.length; i++)
			table.getColumn(i).pack();

		createOkButton(closeShellAdapter);

		Button enableButton = Button_.on(otherButtons_).toggle().key(settings_.getBooleanValue("log", "enabled") ? "dialog.log.enabled": "dialog.log.disabled").select(
			settings_.getBooleanValue("log", "enabled")).listen(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					Button button = (Button)event.getSource();
					settings_.setBooleanValue("log", "enabled", button.getSelection());
					button.setText(text_.get(button.getSelection() ? "dialog.log.enabled": "dialog.log.disabled"));
				}
			}).ctrl();

		Button clearButton = Button_.on(otherButtons_).text().key("button.clear").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (Mess_.on(shell_).key("dialog.log.confirm.clear").confirm()) {
					try {
						new LoggingRepository<>().clear();
						table.removeAll();
					} catch (SQLException e) {
						Mess_.on(shell_).exception(e).warning();
					}
				}
			}
		}).ctrl();

		setLayoutDataButtons(okButton_, enableButton, clearButton);
	}

	private void repopulateEntries(Table table, TableColumn sortColumn, int sortDirection, Map<String, String> filterClauses) {
		int selLogEntryId = table.getSelectionIndex() == -1 ? -1: logEntries.get(table.getSelectionIndex()).getId();
		try {
			String whereClause = filterClauses.isEmpty() ? StringUtils.EMPTY: " WHERE " + StringUtils.join(filterClauses.keySet(), " AND ");
			logEntries = new LoggingRepository<>().list(whereClause, orderByClause_);
		} catch (SQLException e) {
			Mess_.on(shell_).exception(e).warning();
			logEntries = new ArrayList<>();
		}
		table.removeAll();
		table.setSortColumn(sortColumn);
		table.setSortDirection(sortDirection);
		table.setItemCount(logEntries.size());
		int idx = logEntries.size() - 1;
		for (int i = 0; i < logEntries.size(); i++) {
			LogEntry entry = logEntries.get(i);
			TableItem item = table.getItem(i);
			item.setText(0, text_.toString(entry.getTime(), DateFormat.MEDIUM));
			item.setText(1, getEventName(entry.getEvent()));
			item.setText(2, getEntityTypeName(entry.getEntityType()));
			item.setText(3, entry.getEntityTitle());
			item.setText(4, String.valueOf(entry.getEntityId()));
			if (selLogEntryId == logEntries.get(i).getId())
				idx = i;
		}
		table.setSelection(idx);
		table.showSelection();
	}

	private String getEventName(LogEntry.Event event) {
		switch (event) {
			case ADD:
				return text_.get("dialog.log.columns.event.add");
			case EDIT:
				return text_.get("dialog.log.columns.event.edit");
			case REMOVE:
				return text_.get("dialog.log.columns.event.remove");
			case DUPLICATE:
				return text_.get("dialog.log.columns.event.duplicate");
			case RUN:
				return text_.get("dialog.log.columns.event.run");
			case SETUP:
				return text_.get("dialog.log.columns.event.setup");
			default:
				return StringUtils.EMPTY;
		}
	}

	private String getEntityTypeName(EntityType type) {
		switch (type) {
			case PROFILE:
				return text_.get("dialog.log.columns.entitytype.profile");
			case DOSBOXVERSION:
				return text_.get("dialog.template.dosboxversion");
			case TEMPLATE:
				return text_.get("dialog.profile.template");
			case FILTER:
				return text_.get("dialog.log.columns.entitytype.filter");
			default:
				return StringUtils.EMPTY;
		}
	}
}
