/*
 *  Copyright (C) 2026  mrmercy82 (DBGL Extended Edition, written with the help of AI)
 *  Part of a modified version of DBGL, Copyright (C) 2006-2025  Ronald Blankendaal
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
package org.dbgl.util;

import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;


/** alpha 52: the profile list as a table with the columns chosen: CSV (for Excel, LibreOffice) or a web page */
public final class ListTable {

	private ListTable() {
	}

	/** CSV in UTF-8 with its mark (Excel reads it so), separated by ";" where the comma is the decimal sign, else "," */
	public static String csv(Locale locale, List<String> headers, List<List<String>> rows) {
		char sep = java.text.DecimalFormatSymbols.getInstance(locale).getDecimalSeparator() == ',' ? ';': ',';
		StringBuilder sb = new StringBuilder("﻿");
		line(sb, sep, headers);
		for (List<String> r: rows)
			line(sb, sep, r);
		return sb.toString();
	}

	private static void line(StringBuilder sb, char sep, List<String> cells) {
		for (int i = 0; i < cells.size(); i++) {
			if (i > 0)
				sb.append(sep);
			String s = StringUtils.defaultString(cells.get(i));
			sb.append((s.indexOf(sep) >= 0) || s.contains("\"") || s.contains("\n") || s.contains("\r") ? '"' + s.replace("\"", "\"\"") + '"': s);
		}
		sb.append("\r\n");
	}

	/** a web page with the table, light or dark as the system is, also for printing */
	public static String html(String title, String date, List<String> headers, List<List<String>> rows) {
		StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><title>").append(esc(title)).append("</title>\n<style>\n");
		sb.append(":root{color-scheme:light dark}body{font-family:system-ui,Segoe UI,sans-serif;margin:24px;background:#fff;color:#222}\n");
		sb.append("h1{font-size:20px;margin:0 0 4px}p{color:#666;margin:0 0 16px}table{border-collapse:collapse;font-size:13px}\n");
		sb.append("th,td{border:1px solid #ccc;padding:4px 8px;text-align:left;vertical-align:top}th{background:#eee}tr:nth-child(even) td{background:#f7f7f7}\n");
		sb.append("@media (prefers-color-scheme:dark){body{background:#1e1e1e;color:#ddd}p{color:#aaa}th,td{border-color:#444}th{background:#333}tr:nth-child(even) td{background:#262626}}\n");
		sb.append("@media print{body{background:#fff;color:#000}th{background:#eee}tr:nth-child(even) td{background:#f7f7f7}}\n");
		sb.append("</style></head><body>\n<h1>").append(esc(title)).append("</h1>\n<p>").append(esc(date)).append(" &middot; ").append(rows.size()).append("</p>\n<table>\n<tr>");
		for (String h: headers)
			sb.append("<th>").append(esc(h)).append("</th>");
		sb.append("</tr>\n");
		for (List<String> r: rows) {
			sb.append("<tr>");
			for (String c: r)
				sb.append("<td>").append(esc(c)).append("</td>");
			sb.append("</tr>\n");
		}
		sb.append("</table>\n</body></html>\n");
		return sb.toString();
	}

	/** alpha 52: plain text, the columns lined up (a very long value is cut with "..." at 60 characters), lines as on Windows */
	public static String txt(String title, String date, List<String> headers, List<List<String>> rows) {
		int n = headers.size();
		int[] widths = new int[n];
		for (int c = 0; c < n; c++)
			widths[c] = cut(headers.get(c)).length();
		for (List<String> r: rows)
			for (int c = 0; c < n; c++)
				widths[c] = Math.max(widths[c], cut(r.get(c)).length());
		StringBuilder sb = new StringBuilder();
		sb.append(title).append("\r\n").append(date).append(" - ").append(rows.size()).append("\r\n\r\n");
		row(sb, headers, widths);
		List<String> rule = new java.util.ArrayList<>();
		for (int c = 0; c < n; c++)
			rule.add("-".repeat(widths[c]));
		row(sb, rule, widths);
		for (List<String> r: rows)
			row(sb, r, widths);
		return sb.toString();
	}

	private static String cut(String s) {
		String v = StringUtils.defaultString(s).replaceAll("[\\r\\n\\t]+", " ");
		return v.length() > 60 ? v.substring(0, 57) + "...": v;
	}

	private static void row(StringBuilder sb, List<String> cells, int[] widths) {
		StringBuilder line = new StringBuilder();
		for (int c = 0; c < widths.length; c++) {
			if (c > 0)
				line.append("  ");
			line.append(StringUtils.rightPad(cut(cells.get(c)), widths[c]));
		}
		sb.append(StringUtils.stripEnd(line.toString(), " ")).append("\r\n");
	}

	static String esc(String s) {
		return StringUtils.defaultString(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
