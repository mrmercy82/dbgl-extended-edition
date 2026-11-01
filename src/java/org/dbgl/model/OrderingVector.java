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
package org.dbgl.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class OrderingVector {

	private static class ColumnOrder {

		private final int column_;
		private final boolean ascending_;

		ColumnOrder(int col, boolean asc) {
			column_ = col;
			ascending_ = asc;
		}

		@Override
		public int hashCode() {
			return Objects.hash(column_, ascending_);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}

			return column_ == ((ColumnOrder)obj).column_;
		}

		@Override
		public String toString() {
			return GAME_LIST_ORDER[(column_ == 21) ? 9: column_] + (ascending_ ? " ASC": " DESC"); // order by db id
		}
	}

	private static final int MAX_ORDERING_COLS = 8;
	private static final String[] GAME_LIST_ORDER = {"LOWER(GAM.TITLE)", "GAM.SETUP", "LOWER(DEV.NAME)", "LOWER(PUBL.NAME)", "LOWER(GEN.NAME)", "YR.YEAR", "LOWER(STAT.STAT)", "GAM.FAVORITE", "GAM.ID",
			"GAM.DBVERSION_ID", "LOWER(CUST1.VALUE)", "LOWER(CUST2.VALUE)", "LOWER(CUST3.VALUE)", "LOWER(CUST4.VALUE)", "LOWER(GAM.CUSTOM5)", "LOWER(GAM.CUSTOM6)", "LOWER(GAM.CUSTOM7)",
			"LOWER(GAM.CUSTOM8)", "GAM.CUSTOM9", "GAM.CUSTOM10", "LOWER(GAM.CAPTURES)", "", "GAM.STATS_CREATED", "GAM.STATS_LASTMODIFY", "GAM.STATS_LASTRUN", "GAM.STATS_LASTSETUP", "GAM.STATS_RUNS",
			"GAM.STATS_SETUPS", "LOWER(GAM.CUSTOM11)", "LOWER(GAM.CUSTOM12)", "LOWER(GAM.CUSTOM13)", "LOWER(GAM.CUSTOM14)", "GAM.STATS_PLAYTIME"};

	private final List<ColumnOrder> vector_;

	public OrderingVector(int[] columnArray, boolean[] ascendingArray) {
		vector_ = new ArrayList<>();
		for (int i = 0; i < columnArray.length; i++) {
			vector_.add(new ColumnOrder(columnArray[i], ascendingArray[i]));
		}
	}

	public void addOrdering(int column, boolean ascending) {
		ColumnOrder newOrdering = new ColumnOrder(column, ascending);
		int existingIndex = vector_.indexOf(newOrdering);
		if (existingIndex != -1) {
			vector_.remove(existingIndex);
		}
		vector_.add(0, newOrdering);
		if (vector_.size() > MAX_ORDERING_COLS) {
			vector_.remove(MAX_ORDERING_COLS);
		}
	}

	public int[] getColumns() {
		int[] columnArray = new int[vector_.size()];
		for (int i = 0; i < columnArray.length; i++) {
			columnArray[i] = vector_.get(i).column_;
		}
		return columnArray;
	}

	public boolean[] getAscendings() {
		boolean[] ascendingArray = new boolean[vector_.size()];
		for (int i = 0; i < ascendingArray.length; i++) {
			ascendingArray[i] = vector_.get(i).ascending_;
		}
		return ascendingArray;
	}

	public String toClause() {
		StringBuilder orderingClause = new StringBuilder();
		if (!vector_.isEmpty()) {
			orderingClause.append(" ORDER BY ");
		}
		for (int index = 0; index < vector_.size(); index++) {
			ColumnOrder element = vector_.get(index);
			orderingClause.append(element.toString());
			if (index + 1 < vector_.size()) {
				orderingClause.append(',');
			}
		}
		return orderingClause.toString();
	}
}
