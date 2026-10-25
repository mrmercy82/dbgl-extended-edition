/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *
 *  Many thanks to Manuel J. Gallego for his work on MobyGames querying
 *  for TincoreADB. This file is based on his code.
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.SearchEngineImageInformation;
import org.dbgl.model.SearchEngineImageInformation.SearchEngineImageType;
import org.dbgl.model.WebProfile;
import org.dbgl.service.ImageService;
import org.dbgl.service.MetropolisDatabaseService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;


public class MetropolisSearchEngine extends WebSearchEngine {

	private static final String READ_QRY = "SELECT Release.id_Moby_Releases, Game.id_Moby_Games, Game.Name_Prefix, Game.Name, "
			+ "MIN(AltTitle.Alternate_Title), Game.Description, Release.Year, Platform.Name, Release.URL " + "FROM tbl_Moby_Games Game, tbl_Moby_Platforms Platform, tbl_Moby_Releases Release "
			+ "LEFT JOIN tbl_Moby_Games_Alternate_Titles AltTitle ON Game.id_Moby_Games = AltTitle.id_Moby_Games " + "WHERE Release.id_Moby_Games = Game.id_Moby_Games "
			+ "AND Release.id_Moby_Platforms = Platform.id_Moby_Platforms "
			+ "AND (UCASE(Game.Name) LIKE ? OR UCASE(CONCAT(Game.Name_Prefix, ' ', Game.Name)) LIKE ? OR UCASE(AltTitle.Alternate_Title) LIKE ? OR UCASE(CONCAT(Game.Name, ' (', AltTitle.Alternate_Title, ')')) LIKE ? OR UCASE(CONCAT(Game.Name_Prefix, ' ', Game.Name, ' (', AltTitle.Alternate_Title, ')')) LIKE ?) "
			+ "GROUP BY Release.id_Moby_Releases, Game.id_Moby_Games, Game.Name_Prefix, Game.Name, Game.Description, Release.Year, Platform.Name, Release.URL "
			+ "ORDER BY UCASE(CONCAT(Game.Name_Prefix, Game.Name));";
	private static final String READ_RELEASE_DETAILS_QRY = "SELECT Publ.Name, Dev.Name, Release.MobyRank " + "FROM tbl_Moby_Releases Release "
			+ "LEFT JOIN tbl_Moby_Companies Publ ON Release.Publisher_id_Moby_Companies = Publ.id_Moby_Companies "
			+ "LEFT JOIN tbl_Moby_Companies Dev ON Release.Developer_id_Moby_Companies = Dev.id_Moby_Companies " + "WHERE Release.id_Moby_Releases = ?";
	private static final String READ_GAME_GENRES_QRY = "SELECT Genre.Name " + "FROM tbl_Moby_Games Game, tbl_Moby_Games_Genres GameGenre, tbl_Moby_Genres Genre "
			+ "WHERE GameGenre.id_Moby_Games = Game.id_Moby_Games " + "AND Genre.id_Moby_Genres = GameGenre.id_Moby_Genres " + "AND Game.id_Moby_Games = ?";
	private static final String LIMIT_CLAUSE = "LIMIT ?";
	private static final String READ_SCREENSHOT_DETAILS_QRY = "SELECT Screenshot.URL, Screenshot.Description " + "FROM tbl_Moby_Releases Release, tbl_Moby_Releases_Screenshots Screenshot "
			+ "WHERE Screenshot.id_Moby_Releases = Release.id_Moby_Releases " + "AND Release.id_Moby_Releases = ? " + LIMIT_CLAUSE;
	private static final String READ_COVERART_DETAILS_BASE_QRY = "SELECT CoverArt.URL, CoverArtType.Name "
			+ "FROM tbl_Moby_Releases Release, tbl_Moby_Releases_Cover_Art CoverArt, tbl_Moby_Cover_Art_Types CoverArtType, tbl_Moby_Releases_Cover_Art_Regions CoverRegion, tbl_Moby_Regions Region "
			+ "WHERE CoverArtType.id_Moby_Cover_Art_Types = CoverArt.id_Moby_Cover_Art_Types " + "AND CoverArt.id_Moby_Releases = Release.id_Moby_Releases "
			+ "AND CoverRegion.id_Moby_Releases_Cover_Art = CoverArt.id_Moby_Releases_Cover_Art " + "AND CoverRegion.id_Moby_Regions = Region.id_Moby_Regions " + "AND Release.id_Moby_Releases = ? ";
	private static final String READ_COVERART_DETAILS_ALL_REGIONS_QRY = READ_COVERART_DETAILS_BASE_QRY + LIMIT_CLAUSE;
	private static final String READ_COVERART_DETAILS_QRY = READ_COVERART_DETAILS_BASE_QRY + "AND Region.Region = ? " + LIMIT_CLAUSE;
	private static final String READ_STAFF_QRY = "SELECT Staff.Name FROM tbl_Moby_Staff Staff, tbl_Moby_Releases_Staff RelStaff "
			+ "WHERE Staff.id_Moby_Staff = RelStaff.id_Moby_Staff AND RelStaff.id_Moby_Releases = ? LIMIT 1";

	private MetropolisSearchEngine() {
	}

	private static class SearchEngineHolder {
		private static WebSearchEngine instance_ = new MetropolisSearchEngine();
	}

	public static WebSearchEngine getInstance() {
		return SearchEngineHolder.instance_;
	}

	@Override
	public String getIcon() {
		return ImageService.IMG_METROPOLIS;
	}

	@Override
	public String getName() {
		return "Metropolis Launcher MobyGames Database";
	}

	@Override
	public String getSimpleName() {
		return "metropolis";
	}

	@Override
	public boolean available() {
		int[] version = MetropolisDatabaseService.getInstance().getVersion();
		boolean available = (version.length == 2 && !(version[0] == 0 && version[1] == 0));
		if (!available) {
			Mess_.on(null).txt(getName() + " not available").display();
		}
		return available;
	}

	@Override
	public List<WebProfile> getEntries(String title, String[] platforms) throws IOException {
		try (Connection con = MetropolisDatabaseService.getInstance().getConnection(); PreparedStatement pstmt = con.prepareStatement(READ_QRY)) {
			List<WebProfile> allEntries = new ArrayList<>();
			title = prepareSearchTerm(title);
			pstmt.setString(1, "%" + title.toUpperCase() + "%");
			pstmt.setString(2, "%" + title.toUpperCase() + "%");
			pstmt.setString(3, "%" + title.toUpperCase() + "%");
			pstmt.setString(4, "%" + title.toUpperCase() + "%");
			pstmt.setString(5, "%" + title.toUpperCase() + "%");
			try (ResultSet resultset = pstmt.executeQuery()) {
				while (resultset.next()) {
					WebProfile webProfile = new WebProfile();
					webProfile.setReleaseId(resultset.getInt(1));
					webProfile.setGameId(resultset.getInt(2));

					String fullTitle = resultset.getString(4);
					if (resultset.getString(3) != null)
						fullTitle = resultset.getString(3) + " " + fullTitle;
					webProfile.setTitle(fullTitle);
					webProfile.setNotes(resultset.getString(6));
					webProfile.setYear(resultset.getString(7));
					webProfile.setPlatform(resultset.getString(8));
					String url = absoluteUrl(MobyGamesSearchEngine.MOBY_GAMES_HOST_NAME, resultset.getString(9));
					webProfile.setUrl(url);
					allEntries.add(webProfile);
				}
			}
			return filterEntries(platforms, allEntries);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new IOException(TextService.getInstance().get("database.error.query", new Object[] {"read mobygameslocal entries"}));
		}
	}

	@Override
	public WebProfile getEntryDetailedInformation(WebProfile entry) throws IOException {
		try (Connection con = MetropolisDatabaseService.getInstance().getConnection()) {
			try (PreparedStatement pstmt = con.prepareStatement(READ_RELEASE_DETAILS_QRY)) {
				pstmt.setInt(1, entry.getReleaseId());
				try (ResultSet resultset = pstmt.executeQuery()) {
					while (resultset.next()) {
						entry.setPublisherName(resultset.getString(1) != null ? resultset.getString(1): StringUtils.EMPTY);
						entry.setDeveloperName(resultset.getString(2) != null ? resultset.getString(2): StringUtils.EMPTY);
						entry.setRank(resultset.getInt(3));
					}
				}
			}

			if (StringUtils.isBlank(entry.getDeveloperName())) {
				try (PreparedStatement pstmt = con.prepareStatement(READ_STAFF_QRY)) {
					pstmt.setInt(1, entry.getReleaseId());
					try (ResultSet resultset = pstmt.executeQuery()) {
						if (resultset.next())
							entry.setDeveloperName(resultset.getString(1));
					}
				}
			}

			try (PreparedStatement pstmt = con.prepareStatement(READ_GAME_GENRES_QRY)) {
				pstmt.setInt(1, entry.getGameId());
				try (ResultSet resultset = pstmt.executeQuery()) {
					List<String> genres = new ArrayList<>();
					while (resultset.next()) {
						genres.add(resultset.getString(1));
					}
					entry.setGenre(StringUtils.join(genres, ", "));
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new IOException(TextService.getInstance().get("database.error.query", new Object[] {"read mobygameslocal game details"}));
		}

		return entry;
	}

	@Override
	public SearchEngineImageInformation[] getEntryImages(WebProfile entry, int coverArtMax, int screenshotsMax, boolean forceAllRegionsCoverArt) throws IOException {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		if (coverArtMax > 0) {
			result.addAll(getEntryCoverArtInformation(entry, coverArtMax, forceAllRegionsCoverArt, SettingsService.getInstance().getValue(getSimpleName(), "region")));
		}
		if (screenshotsMax > 0) {
			result.addAll(getEntryScreenshotInformation(entry, screenshotsMax));
		}
		entry.setWebImages(result.toArray(new SearchEngineImageInformation[0]));
		return entry.getWebImages();
	}

	private static Collection<? extends SearchEngineImageInformation> getEntryCoverArtInformation(WebProfile entry, int coverArtMax, boolean forceAllRegionsCoverArt, String region)
			throws IOException {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		try (Connection con = MetropolisDatabaseService.getInstance().getConnection();
				PreparedStatement pstmt = con.prepareStatement(forceAllRegionsCoverArt ? READ_COVERART_DETAILS_ALL_REGIONS_QRY: READ_COVERART_DETAILS_QRY)) {
			pstmt.setInt(1, entry.getReleaseId());
			if (!forceAllRegionsCoverArt)
				pstmt.setString(2, region);
			pstmt.setInt(forceAllRegionsCoverArt ? 2: 3, coverArtMax);
			try (ResultSet resultset = pstmt.executeQuery()) {
				while (resultset.next()) {
					result.add(new SearchEngineImageInformation(SearchEngineImageType.COVER_ART,
							HTTPS_PROTOCOL + MobyGamesSearchEngine.MOBY_GAMES_HOST_NAME + "/images/covers/l/" + resultset.getString(1), resultset.getString(2)));
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new IOException(TextService.getInstance().get("database.error.query", new Object[] {"read mobygameslocal covertart details"}));
		}
		return result;
	}

	private static Collection<? extends SearchEngineImageInformation> getEntryScreenshotInformation(WebProfile entry, int screenshotsMax) throws IOException {
		List<SearchEngineImageInformation> result = new ArrayList<>();
		try (Connection con = MetropolisDatabaseService.getInstance().getConnection(); PreparedStatement pstmt = con.prepareStatement(READ_SCREENSHOT_DETAILS_QRY)) {
			pstmt.setInt(1, entry.getReleaseId());
			pstmt.setInt(2, screenshotsMax);
			try (ResultSet resultset = pstmt.executeQuery()) {
				while (resultset.next()) {
					result.add(new SearchEngineImageInformation(SearchEngineImageType.SCREENSHOT,
							HTTPS_PROTOCOL + MobyGamesSearchEngine.MOBY_GAMES_HOST_NAME + "/images/shots/l/" + resultset.getString(1), resultset.getString(2)));
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new IOException(TextService.getInstance().get("database.error.query", new Object[] {"read mobygameslocal screenshot details"}));
		}
		return result;
	}
}
