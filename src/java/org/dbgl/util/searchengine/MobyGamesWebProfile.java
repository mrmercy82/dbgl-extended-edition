/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-07. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 */
package org.dbgl.util.searchengine;

import java.io.IOException;

import org.dbgl.model.WebProfile;
import org.eclipse.swt.graphics.ImageData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class MobyGamesWebProfile extends WebProfile {

	@Override
	public ImageData getWebImage(int i) throws IOException {
		if (webImages_[i].getData() == null) {
			Document imgDoc = Jsoup.connect(webImages_[i].getUrl()).userAgent(MobyGamesSearchEngine.USER_AGENT).timeout(15000).get();
			webImages_[i].setUrl(imgDoc.select("meta[property=og:image]").attr("content"));
		}
		return super.getWebImage(i);
	}
}
