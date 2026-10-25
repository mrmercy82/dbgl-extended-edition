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
			Document imgDoc = Jsoup.connect(webImages_[i].getUrl()).userAgent(MobyGamesSearchEngine.USER_AGENT).get();
			webImages_[i].setUrl(imgDoc.select("meta[property=og:image]").attr("content"));
		}
		return super.getWebImage(i);
	}
}
