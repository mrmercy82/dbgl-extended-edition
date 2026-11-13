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
package org.dbgl.gui.dialog;

import java.util.ArrayList;
import java.util.List;

import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.DarkTheme;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;


/**
 * alpha 48: the changelog and "What is new" (help menu) laid out like the keys window: headings, the parts ("Fixed", "New")
 * bold, each entry with a bullet in front and its text wrapped next to it. Before, the plain text file in a box for text.
 */
public class DocumentDialog extends SizeControlledButtonDialog<Object> {

	/** a part of the text: a title (underlined with =), a section (with -), a sub-heading, an entry (- ...) or a paragraph */
	public enum Kind {
		TITLE, SECTION, SUB, BULLET, PARAGRAPH
	}

	public record Block(Kind kind, String text, int level) {
	}

	private final String title_;
	private final String content_;

	public DocumentDialog(Shell parent, String title, String content, String image) {
		super(parent, "documentdialog");
		title_ = title;
		content_ = content;
		windowImage_ = image;
	}

	@Override
	protected String getDialogTitle() {
		return title_;
	}

	/** the text of a document shipped with DBGL in its parts; lines of an entry or paragraph joined */
	public static List<Block> blocks(String text) {
		String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
		List<Block> blocks = new ArrayList<>();
		StringBuilder current = null;
		Kind kind = null;
		int level = 0;
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			String trimmed = line.trim();
			String next = i + 1 < lines.length ? lines[i + 1].trim(): "";
			boolean underline = next.length() >= 3 && (next.matches("=+") || next.matches("-+"));
			boolean bullet = trimmed.startsWith("- ") || trimmed.startsWith("* ");
			boolean indented = !line.isEmpty() && Character.isWhitespace(line.charAt(0));
			if (trimmed.isEmpty() || underline || bullet || (!indented && (kind == Kind.BULLET)) || trimmed.matches("[-=]{3,}")) {
				if (current != null)
					blocks.add(new Block(kind, current.toString(), level));
				current = null;
				kind = null;
			}
			if (trimmed.isEmpty() || trimmed.matches("[-=]{3,}"))
				continue;
			if (underline) {
				blocks.add(new Block(next.startsWith("=") ? Kind.TITLE: Kind.SECTION, trimmed, 0));
				i++;
				continue;
			}
			if (bullet) {
				current = new StringBuilder(trimmed.substring(2).trim());
				kind = Kind.BULLET;
				level = (line.length() - line.stripLeading().length()) >= 4 ? 1: 0;
				continue;
			}
			if (current == null) {
				current = new StringBuilder(trimmed);
				kind = Kind.PARAGRAPH;
				level = 0;
			} else {
				current.append(' ').append(trimmed);
			}
		}
		if (current != null)
			blocks.add(new Block(kind, current.toString(), level));
		// a short line of its own right before entries is the heading of a part ("Fixed", "New", "Improved")
		for (int i = 0; i + 1 < blocks.size(); i++) {
			Block b = blocks.get(i);
			if ((b.kind() == Kind.PARAGRAPH) && (b.text().length() <= 40) && !b.text().endsWith(".") && (blocks.get(i + 1).kind() == Kind.BULLET))
				blocks.set(i, new Block(Kind.SUB, b.text(), 0));
		}
		return blocks;
	}

	private static final java.util.regex.Pattern URL_PATRN = java.util.regex.Pattern.compile("https?://[^\\s<>\"]*[^\\s<>\".,;:!?)\\]]");

	/** a text of an entry or a paragraph; with addresses in it a Link, so that they can be clicked (alpha 48) */
	private org.eclipse.swt.widgets.Control text(Composite page, String text) {
		java.util.regex.Matcher m = URL_PATRN.matcher(text);
		if (!m.find())
			return labelOf(page, text);
		StringBuilder sb = new StringBuilder();
		int last = 0;
		do {
			sb.append(text, last, m.start()).append("<a href=\"").append(m.group()).append("\">").append(m.group()).append("</a>");
			last = m.end();
		} while (m.find());
		sb.append(text.substring(last));
		org.eclipse.swt.widgets.Link link = new org.eclipse.swt.widgets.Link(page, SWT.WRAP);
		link.setText(sb.toString().replace("&", "&&"));
		link.addListener(SWT.Selection, e -> org.dbgl.util.SystemUtils.openForBrowsing(e.text, shell_));
		if (DarkTheme.forced())
			link.setLinkForeground(DarkTheme.linkForeground);
		return link;
	}

	private static Label labelOf(Composite page, String text) {
		Label label = new Label(page, SWT.WRAP);
		label.setText(text.replace("&", "&&"));
		return label;
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(1, false));
		ScrolledComposite scroll = new ScrolledComposite(contents_, SWT.V_SCROLL | SWT.BORDER);
		scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scroll.setExpandHorizontal(true);
		scroll.setExpandVertical(true);
		Composite page = new Composite(scroll, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 12;
		layout.marginHeight = 8;
		layout.horizontalSpacing = 8;
		layout.verticalSpacing = 3;
		page.setLayout(layout);
		boolean dark = DarkTheme.forced();
		Color strong = dark ? new Color(255, 255, 255): new Color(0, 0, 0);
		Color normal = dark ? new Color(200, 200, 200): new Color(70, 70, 70);
		FontData[] boldData = page.getFont().getFontData(), headData = page.getFont().getFontData(), titleData = page.getFont().getFontData();
		for (FontData d: boldData)
			d.setStyle(SWT.BOLD);
		for (FontData d: headData) {
			d.setStyle(SWT.BOLD);
			d.setHeight(d.getHeight() + 2);
		}
		for (FontData d: titleData) {
			d.setStyle(SWT.BOLD);
			d.setHeight(d.getHeight() + 4);
		}
		Font bold = new Font(shell_.getDisplay(), boldData), head = new Font(shell_.getDisplay(), headData), titleFont = new Font(shell_.getDisplay(), titleData);
		shell_.addDisposeListener(e -> {
			bold.dispose();
			head.dispose();
			titleFont.dispose();
		});
		boolean first = true;
		for (Block b: blocks(content_)) {
			switch (b.kind()) {
				case TITLE, SECTION, SUB -> {
					Label l = new Label(page, SWT.WRAP);
					l.setText(b.text().replace("&", "&&"));
					l.setFont(b.kind() == Kind.TITLE ? titleFont: b.kind() == Kind.SECTION ? head: bold);
					l.setData(DarkTheme.KEEP_FOREGROUND, Boolean.TRUE);
					l.setForeground(strong);
					GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
					gd.verticalIndent = first ? 0: (b.kind() == Kind.SUB ? 6: 16);
					gd.horizontalIndent = b.kind() == Kind.SUB ? 8: 0;
					l.setLayoutData(gd);
				}
				case BULLET -> {
					Label dot = new Label(page, SWT.NONE);
					dot.setText("•");
					dot.setFont(bold);
					dot.setData(DarkTheme.KEEP_FOREGROUND, Boolean.TRUE);
					dot.setForeground(strong);
					GridData dd = new GridData(SWT.END, SWT.BEGINNING, false, false);
					dd.horizontalIndent = 8 + 16 * b.level();
					dot.setLayoutData(dd);
					org.eclipse.swt.widgets.Control what = text(page, b.text());
					what.setData(DarkTheme.KEEP_FOREGROUND, Boolean.TRUE);
					what.setForeground(normal);
					GridData wd = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
					wd.widthHint = 200;
					what.setLayoutData(wd);
				}
				default -> {
					org.eclipse.swt.widgets.Control sentence = text(page, b.text());
					sentence.setData(DarkTheme.KEEP_FOREGROUND, Boolean.TRUE);
					sentence.setForeground(normal);
					GridData sd = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
					sd.widthHint = 200;
					sd.verticalIndent = 4;
					sentence.setLayoutData(sd);
				}
			}
			first = false;
		}
		scroll.setContent(page);
		scroll.addListener(SWT.Resize, e -> {
			int width = scroll.getClientArea().width;
			scroll.setMinSize(page.computeSize(width, SWT.DEFAULT));
		});
		scroll.getVerticalBar().setIncrement(24);
		createOkButton(closeShellAdapter);
		okCancelButtons_.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));
	}
}
