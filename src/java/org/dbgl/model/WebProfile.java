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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Comparator;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.service.ImageService;
import org.dbgl.util.searchengine.WebSearchEngine;
import org.eclipse.swt.graphics.ImageData;


public class WebProfile implements Comparable<WebProfile> {

	private String title_, platform_, year_, url_, developerName_, publisherName_, genre_, notes_;
	private int rank_;
	private String coreGameCoverUrl_;
	protected SearchEngineImageInformation[] webImages_;
	private int releaseId_;
	private int gameId_;
	private int platformId_;

	public String getTitle() {
		return title_;
	}

	public void setTitle(String title) {
		title_ = title;
	}

	public String getPlatform() {
		return platform_;
	}

	public void setPlatform(String platform) {
		platform_ = platform;
	}

	public String getYear() {
		return year_;
	}

	public void setYear(String year) {
		year_ = year;
	}

	public String getUrl() {
		return url_;
	}

	public void setUrl(String url) {
		url_ = url;
	}

	public String getScreenshotsUrl() {
		return url_ + "screenshots/";
	}

	public String getCoverArtUrl() {
		return url_ + "covers/";
	}

	public String getCoreGameCoverUrl() {
		return coreGameCoverUrl_;
	}

	public String getCoreGameCoverUrlWithoutPathPrefix() {
		int index = StringUtils.ordinalIndexOf(coreGameCoverUrl_, "/", 3);
		if (index > 0)
			return coreGameCoverUrl_.substring(index + 1);
		return coreGameCoverUrl_;
	}

	public void setCoreGameCoverUrl(String coreGameCoverUrl) {
		coreGameCoverUrl_ = coreGameCoverUrl;
	}

	public ImageData getWebImage(int i) throws IOException {
		if (webImages_[i].getData() == null) {
			HttpURLConnection conn = WebSearchEngine.getHttpURLConnection(webImages_[i].getUrl());
			try (InputStream is = conn.getInputStream()) {
				if (conn.getContentType().equalsIgnoreCase("image/gif")) {
					ImageData imgData = ImageService.getImageDataUsingGifDecoder(is);
					if (imgData != null) {
						webImages_[i].setData(imgData);
					} else {
						HttpURLConnection conn2 = WebSearchEngine.getHttpURLConnection(webImages_[i].getUrl()); // open another connection
						try (InputStream is2 = conn2.getInputStream()) {
							webImages_[i].setData(new ImageData(is2));
						}
					}
				} else if (conn.getContentType().equalsIgnoreCase("image/webp")) {
					var img = ImageIO.read(is);
					webImages_[i].setData(ImageService.convertAWTImageToSWT(img));
				} else {
					webImages_[i].setData(new ImageData(is));
				}
			}
		}
		return webImages_[i].getData();
	}

	public SearchEngineImageInformation[] getWebImages() {
		return webImages_.clone();
	}

	public void setWebImages(SearchEngineImageInformation[] webImages) {
		webImages_ = webImages.clone();
	}

	public String getDeveloperName() {
		return developerName_;
	}

	public void setDeveloperName(String developerName) {
		developerName_ = developerName;
	}

	public String getPublisherName() {
		return publisherName_;
	}

	public void setPublisherName(String publisherName) {
		publisherName_ = publisherName;
	}

	public String getGenre() {
		return genre_;
	}

	public void setGenre(String genre) {
		genre_ = genre;
	}

	public String getNotes() {
		return notes_;
	}

	public void setNotes(String notes) {
		notes_ = notes;
	}

	public int getRank() {
		return rank_;
	}

	public void setRank(int rank) {
		rank_ = rank;
	}

	public int getReleaseId() {
		return releaseId_;
	}

	public void setReleaseId(int releaseId) {
		releaseId_ = releaseId;
	}

	public int getGameId() {
		return gameId_;
	}

	public void setGameId(int gameId) {
		gameId_ = gameId;
	}
	
	public int getPlatformId() {
		return platformId_;
	}
	
	public void setPlatformId(int platformId) {
		platformId_ = platformId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(platform_, title_);
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
		WebProfile otherProfile = (WebProfile)obj;
		return platform_.equalsIgnoreCase(otherProfile.platform_) && title_.equalsIgnoreCase(otherProfile.title_);
	}

	@Override
	public int compareTo(WebProfile otherProfile) {
		if (otherProfile.platform_.equalsIgnoreCase(platform_)) {
			return title_.compareToIgnoreCase(otherProfile.title_);
		}
		return platform_.compareToIgnoreCase(otherProfile.platform_);
	}

	public static final class byTitle implements Comparator<WebProfile> {
		@Override
		public int compare(WebProfile prof1, WebProfile prof2) {
			return prof1.title_.compareToIgnoreCase(prof2.title_);
		}
	}

	public static final class byYear implements Comparator<WebProfile> {
		@Override
		public int compare(WebProfile prof1, WebProfile prof2) {
			return prof1.year_.compareToIgnoreCase(prof2.year_);
		}
	}

	public static final class byPlatform implements Comparator<WebProfile> {
		@Override
		public int compare(WebProfile prof1, WebProfile prof2) {
			return prof1.platform_.compareToIgnoreCase(prof2.platform_);
		}
	}

	@Override
	public String toString() {
		return title_ + '@' + platform_;
	}
}
