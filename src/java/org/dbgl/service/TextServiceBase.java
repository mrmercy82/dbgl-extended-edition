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

import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;
import org.dbgl.util.StringRelatedUtils;


public abstract class TextServiceBase implements ITextService {

	protected Locale locale_;
	protected ResourceBundle mes_;

	protected TextServiceBase(Locale locale) {
		refresh(locale);
	}

	public void refresh(Locale locale) {
		locale_ = locale;
		Control utf8Control = new CustomEncodingResourceControl(StandardCharsets.UTF_8.name());
		try {
			mes_ = ResourceBundle.getBundle("plugins/i18n/MessagesBundle", locale, utf8Control);
		} catch (MissingResourceException me) {
			mes_ = ResourceBundle.getBundle("i18n/MessagesBundle", locale, utf8Control);
		}
	}

	@Override
	public String get(String key) {
		try {
			return mes_.getString(key);
		} catch (MissingResourceException me) {
			return "[" + key + "]";
		}
	}

	@Override
	public String get(String key, String param) {
		return get(key, new Object[] {param});
	}

	@Override
	public String get(String key, Object[] objs) {
		try {
			return new MessageFormat(mes_.getString(key), locale_).format(objs);
		} catch (IllegalArgumentException | MissingResourceException e) {
			return StringRelatedUtils.toString(e) + "[" + get(key) + "]";
		}
	}

	@Override
	public String toString(boolean yesno) {
		return yesno ? get("general.yes"): get("general.no");
	}

	@Override
	public String toString(Date date) {
		if (date == null)
			return "-";
		return DateFormat.getDateInstance(DateFormat.SHORT, locale_).format(date);
	}

	@Override
	public String toString(Date date, int timeStyle) {
		if (date == null)
			return "-";
		return DateFormat.getDateTimeInstance(DateFormat.SHORT, timeStyle, locale_).format(date);
	}
}
