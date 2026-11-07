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
package org.dbgl.util.searchengine;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import org.dbgl.service.TextService;
import org.dbgl.util.StringRelatedUtils;
import org.jsoup.HttpStatusException;


/**
 * A failed request to a game database, with a message a user can act on instead of a stack trace: no connection, no answer,
 * access refused (too many requests, protection against automatic access, missing key) or a page that looks different now.
 */
public class SearchEngineException extends IOException {

	private static final long serialVersionUID = 1L;

	public SearchEngineException(String message, Throwable cause) {
		super(message, cause);
	}

	/** @return the cause explained for a user; never null */
	public static SearchEngineException explain(String engineName, Throwable e) {
		if (e instanceof SearchEngineException see)
			return see;
		var text = TextService.getInstance();
		Throwable c = e;
		while ((c.getCause() != null) && !(c instanceof HttpStatusException) && !(c instanceof SocketTimeoutException) && !(c instanceof UnknownHostException))
			c = c.getCause();
		if (c instanceof HttpStatusException h)
			return http(engineName, h.getStatusCode(), e);
		if (c instanceof SocketTimeoutException)
			return new SearchEngineException(text.get("searchengine.error.timeout", new Object[] {engineName}), e);
		if ((c instanceof UnknownHostException) || (c instanceof ConnectException) || (c instanceof NoRouteToHostException) || (c instanceof SSLException))
			return new SearchEngineException(text.get("searchengine.error.offline", new Object[] {engineName}), e);
		if ((e instanceof RuntimeException) || (e instanceof org.json.JSONException))
			return changed(engineName, e);
		return new SearchEngineException(text.get("searchengine.error.other", new Object[] {engineName, StringRelatedUtils.toString(e instanceof Exception ex ? ex: new Exception(e))}), e);
	}

	public static SearchEngineException http(String engineName, int status, Throwable cause) {
		var text = TextService.getInstance();
		if ((status == 401) || (status == 403) || (status == 429) || (status == 503))
			return new SearchEngineException(text.get("searchengine.error.blocked", new Object[] {engineName, String.valueOf(status)}), cause);
		return new SearchEngineException(text.get("searchengine.error.http", new Object[] {engineName, String.valueOf(status)}), cause);
	}

	/** the page or answer does not have what DBGL looks for, e.g. after the site was rebuilt or when a captcha page came back */
	public static SearchEngineException changed(String engineName, Throwable cause) {
		return new SearchEngineException(TextService.getInstance().get("searchengine.error.changed", new Object[] {engineName}), cause);
	}

	/** a key (API key, client ID) the user must enter in the settings is missing or wrong */
	public static SearchEngineException key(String engineName) {
		return new SearchEngineException(TextService.getInstance().get("searchengine.error.key", new Object[] {engineName}), null);
	}
}
