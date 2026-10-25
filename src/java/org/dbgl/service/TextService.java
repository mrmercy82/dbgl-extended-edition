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

import java.util.Locale;


public class TextService extends TextServiceBase {

	protected TextService(Locale locale) {
		super(locale);
	}

	private static class TextServiceHolder {
		private static final TextService instance_ = new TextService(new Locale(SettingsService.getInstance().getValue("locale", "language"),
				SettingsService.getInstance().getValue("locale", "country"), SettingsService.getInstance().getValue("locale", "variant")));

		private static void refresh() {
			instance_.refresh(new Locale(SettingsService.getInstance().getValue("locale", "language"), SettingsService.getInstance().getValue("locale", "country"),
					SettingsService.getInstance().getValue("locale", "variant")));
		}
	}

	public static ITextService getInstance() {
		return TextServiceHolder.instance_;
	}

	public void refresh() {
		TextServiceHolder.refresh();
	}

}
