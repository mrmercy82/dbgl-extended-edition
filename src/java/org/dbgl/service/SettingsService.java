/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-10-31. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.controls.ProfilesList.ProfilesListType;
import org.dbgl.model.DosboxFamily;
import org.dbgl.model.FileLocation;
import org.dbgl.model.conf.Settings;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.SystemUtils;
import org.dbgl.util.searchengine.MetropolisSearchEngine;
import org.dbgl.util.searchengine.MobyGamesSearchEngine;
import org.dbgl.util.searchengine.PouetSearchEngine;
import org.dbgl.util.searchengine.TheGamesDBSearchEngine;
import org.dbgl.util.searchengine.WebSearchEngine;


public class SettingsService {

	public static final List<String> SUPPORTED_DOSBOX_RELEASES = Arrays.asList("0.63", "0.65", "0.70", "0.71", "0.72", "0.73", "0.74", "0.74-2", "0.74-3");
	public static final String LATEST_SUPPORTED_DOSBOX_RELEASE = SUPPORTED_DOSBOX_RELEASES.get(SUPPORTED_DOSBOX_RELEASES.size() - 1);

	public static final DosboxFamily OFFICIAL_DOSBOX_FAM = new DosboxFamily("Official", SUPPORTED_DOSBOX_RELEASES);
	public static final DosboxFamily SVN_DOSBOX_FAM = new DosboxFamily("DOSBox SVN", Arrays.asList("r4483"));
	public static final DosboxFamily DAUM_DOSBOX_FAM = new DosboxFamily("DOSBox Daum", Arrays.asList("2015.01.25"));
	public static final DosboxFamily ECE_DOSBOX_FAM = new DosboxFamily("DOSBox ECE", Arrays.asList("r4482")); 
	public static final DosboxFamily STAGING_DOSBOX_FAM = new DosboxFamily("DOSBox Staging", Arrays.asList("0.81.0", "0.81.1", "0.81.2", "0.82.0", "0.82.1", "0.82.2", "0.83.0"));
	public static final DosboxFamily DOSBOX_X_DOSBOX_FAM = new DosboxFamily("DOSBox-X", Arrays.asList("0.82.20", "2024.03.01", "2024.07.01", "2024.10.01", "2024.12.04", "2025.01.01", "2025.02.01",
		"2025.05.03", "2025.10.07", "2025.12.01", "2026.01.02", "2026.03.29", "2026.05.02", "2026.06.02", "2026.07.02", "2026.08.02", "2026.08.31", "2026.10.01"));
	
	public static final List<DosboxFamily> SUPPORTED_DOSBOX_FAMILIES = List.of(
		OFFICIAL_DOSBOX_FAM, SVN_DOSBOX_FAM, DAUM_DOSBOX_FAM, ECE_DOSBOX_FAM, STAGING_DOSBOX_FAM, DOSBOX_X_DOSBOX_FAM
	);
	
	public static final List<WebSearchEngine> ALL_WEBSEARCH_ENGINES = Arrays.asList(MobyGamesSearchEngine.getInstance(), MetropolisSearchEngine.getInstance(), PouetSearchEngine.getInstance(),
		TheGamesDBSearchEngine.getInstance(), org.dbgl.util.searchengine.IgdbSearchEngine.getInstance(), org.dbgl.util.searchengine.SteamGridDbSearchEngine.getInstance());

	private static final String SETTINGS_CONF = "settings.conf";

	private static final String SAMPLE_RATES = "8000 11025 16000 22050 32000 44100 48000 49716";
	private static final String BASE_ADDRS = "220 240 260 280 2a0 2c0 2e0 300";
	private static final String IRQS = "3 5 7 9 10 11 12";
	private static final String DMAS = "0 1 3 5 6 7";

	private Settings settings_;

	private SettingsService() {
		settings_ = new Settings();
		setDefaultSettings();

		String conf = (SystemUtils.USE_USER_HOME_DIR || !FilesUtils.isWritableDirectory(new File("."))) ? new File(SystemUtils.USER_DATA_DIR, SETTINGS_CONF).getPath(): SETTINGS_CONF;
		settings_.setFileLocation(new FileLocation(conf));

		try {
			String warnings = settings_.load(GenericTextService.getInstance());
			if (StringUtils.isNotEmpty(warnings))
				System.out.println(warnings);
		} catch (IOException e) {
			// if settings could not be read, use only the defaults
		}
	}

	private static class SettingsServiceHolder {
		private static final SettingsService instance_ = new SettingsService();
	}

	public static SettingsService getInstance() {
		return SettingsServiceHolder.instance_;
	}

	public void save() throws IOException {
		settings_.save();
	}

	/** @return the file the settings are kept in */
	public File getSettingsFile() {
		return settings_.getFile();
	}

	public static List<WebSearchEngine> availableWebSearchEngines() {
		return ALL_WEBSEARCH_ENGINES.stream().filter(WebSearchEngine::available).toList();
	}

	public SimpleDateFormat dateTimeFormat() {
		return new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	}
	
	public boolean hasValue(String sectionTitle, String sectionItem) {
		return settings_.hasValue(sectionTitle, sectionItem);
	}

	public String getValue(String sectionTitle, String sectionItem) {
		return settings_.getValue(sectionTitle, sectionItem);
	}

	public void setValue(String sectionTitle, String sectionItem, String value) {
		settings_.setValue(sectionTitle, sectionItem, value);
	}

	public boolean getBooleanValue(String sectionTitle, String sectionItem) {
		return settings_.getBooleanValue(sectionTitle, sectionItem);
	}

	public void setBooleanValue(String sectionTitle, String sectionItem, boolean value) {
		settings_.setValue(sectionTitle, sectionItem, value);
	}

	public int getIntValue(String sectionTitle, String sectionItem) {
		return settings_.getIntValue(sectionTitle, sectionItem);
	}

	public void setIntValue(String sectionTitle, String sectionItem, int value) {
		settings_.setValue(sectionTitle, sectionItem, value);
	}

	public String getMultilineValue(String sectionTitle, String sectionItem, String delimiter) {
		return settings_.getMultilineValue(sectionTitle, sectionItem, delimiter);
	}

	public void setMultilineValue(String sectionTitle, String sectionItem, String values, String delimiter) {
		settings_.setMultilineValue(sectionTitle, sectionItem, values, delimiter);
	}

	public int[] getIntValues(String sectionTitle, String sectionItem) {
		return settings_.getIntValues(sectionTitle, sectionItem);
	}

	public void setIntValues(String sectionTitle, String sectionItem, int[] values) {
		settings_.setValues(sectionTitle, sectionItem, values);
	}

	public boolean[] getBooleanValues(String sectionTitle, String sectionItem) {
		return settings_.getBooleanValues(sectionTitle, sectionItem);
	}

	public void setBooleanValues(String sectionTitle, String sectionItem, boolean[] values) {
		settings_.setValues(sectionTitle, sectionItem, values);
	}

	public String[] getValues(String sectionTitle, String sectionItem) {
		return settings_.getValues(sectionTitle, sectionItem);
	}

	public String[] getProfileSectionItemNames() {
		return settings_.getItemNames("profile");
	}

	private void setDefaultSettings() {
		settings_.setValue("gui", "width", "904");
		settings_.setValue("gui", "height", "475");
		settings_.setValue("gui", "x", "10");
		settings_.setValue("gui", "y", "10");
		settings_.setValue("gui", "profileinfo_delay", "100");
		settings_.setValue("gui", "profiledialog_width", "768");
		settings_.setValue("gui", "profiledialog_height", "588");
		settings_.setValue("gui", "profileloaderdialog_width", "375");
		settings_.setValue("gui", "profileloaderdialog_height", "300");
		settings_.setValue("gui", "profiledeletedialog_width", "550");
		settings_.setValue("gui", "profiledeletedialog_height", "224");
		settings_.setValue("gui", "multiprofiledialog_width", "600");
		settings_.setValue("gui", "multiprofiledialog_height", "375");
		settings_.setValue("gui", "addgamewizard_width", "500");
		settings_.setValue("gui", "addgamewizard_height", "375");
		settings_.setValue("gui", "dosboxdialog_width", "600");
		settings_.setValue("gui", "replacevaluesdialog_width", "820");
		settings_.setValue("gui", "replacevaluesdialog_height", "640");
		settings_.setValue("gui", "extrasdialog_width", "920");
		settings_.setValue("gui", "extrasdialog_height", "600");
		settings_.setValue("gui", "switchdosboxdialog_width", "760");
		settings_.setValue("gui", "switchdosboxdialog_height", "680");
		settings_.setValue("gui", "unknownlinesdialog_width", "760");
		settings_.setValue("gui", "unknownlinesdialog_height", "420");
		settings_.setValue("gui", "checkprofilesdialog_width", "760");
		settings_.setValue("gui", "checkprofilesdialog_height", "560");
		settings_.setValue("gui", "differencesdialog_width", "600");
		settings_.setValue("gui", "differencesdialog_height", "460");
		settings_.setValue("gui", "textinputdialog_width", "480");
		settings_.setValue("gui", "preseteditdialog_width", "560");
		settings_.setValue("gui", "choicedialog_width", "620");
		settings_.setValue("gui", "choicedialog_height", "340");
		settings_.setValue("gui", "preseteditdialog_height", "460");
		settings_.setValue("gui", "textinputdialog_height", "200");
		settings_.setValue("gui", "profilehistorydialog_width", "800");
		settings_.setValue("gui", "profilehistorydialog_height", "500");
		settings_.setValue("gui", "dosboxdialog_height", "400");
		settings_.setValue("gui", "templatedialog_width", "768");
		settings_.setValue("gui", "templatedialog_height", "588");
		settings_.setValue("gui", "mountdialog_width", "640");
		settings_.setValue("gui", "mountdialog_height", "600");
		settings_.setValue("gui", "imgsizedialog_width", "330");
		settings_.setValue("gui", "imgsizedialog_height", "260");
		settings_.setValue("gui", "archivebrowser_width", "400");
		settings_.setValue("gui", "mobygamesbrowser_width", "650");
		settings_.setValue("gui", "mobygamesbrowser_height", "375");
		settings_.setValue("gui", "mixerdialog_width", "950");
		settings_.setValue("gui", "mixerdialog_height", "450");
		settings_.setValue("gui", "archivebrowser_height", "375");
		settings_.setValue("gui", "dfendimportdialog_width", "600");
		settings_.setValue("gui", "dfendimportdialog_height", "375");
		settings_.setValue("gui", "exportlistdialog_width", "550");
		settings_.setValue("gui", "exportlistdialog_height", "190");
		settings_.setValue("gui", "nativecommanddialog_width", "520");
		settings_.setValue("gui", "nativecommanddialog_height", "225");
		settings_.setValue("gui", "export_width", "550");
		settings_.setValue("gui", "export_height", "500");
		settings_.setValue("gui", "import_width", "654");
		settings_.setValue("gui", "import_height", "500");
		settings_.setValue("gui", "migratedialog_width", "600");
		settings_.setValue("gui", "migratedialog_height", "375");
		settings_.setValue("gui", "settingsdialog_width", "715");
		settings_.setValue("gui", "settingsdialog_height", "560");
		settings_.setValue("gui", "shareconfdialog_width", "560");
		settings_.setValue("gui", "shareconfdialog_height", "540");
		settings_.setValue("gui", "sharedconfbrowser_width", "860");
		settings_.setValue("gui", "sharedconfbrowser_height", "540");
		settings_.setValue("gui", "importdirdialog_width", "1680");
		settings_.setValue("gui", "importdirdialog_height", "800");
		settings_.setValue("gui", "importdir_column_1width", "70");
		settings_.setValue("gui", "importdir_column_2width", "400");
		settings_.setValue("gui", "importdir_column_3width", "260");
		settings_.setValue("gui", "importdir_column_4width", "165");
		settings_.setValue("gui", "importdir_column_5width", "165");
		settings_.setValue("gui", "importdir_column_6width", "240");
		settings_.setValue("gui", "importdir_column_7width", "60");
		settings_.setValue("gui", "importdir_column_8width", "105");

		settings_.setValue("gui", "log_width", "860");
		settings_.setValue("gui", "log_height", "540");
		settings_.setValue("gui", "filterdialog_width", "725");
		settings_.setValue("gui", "filterdialog_height", "540");
		settings_.setValue("gui", "filtertab", "0");
		settings_.setValue("gui", "maximized", false);
		settings_.setValue("gui", "column1width", "150");
		settings_.setValue("gui", "column2width", "48");
		settings_.setValue("gui", "column3width", "100");
		settings_.setValue("gui", "column4width", "100");
		settings_.setValue("gui", "column5width", "70");
		settings_.setValue("gui", "column6width", "40");
		settings_.setValue("gui", "column7width", "60");
		settings_.setValue("gui", "column8width", "60");
		settings_.setValue("gui", "column9width", "38");
		settings_.setValue("gui", "column10width", "40");
		settings_.setValue("gui", "column11width", "70");
		settings_.setValue("gui", "column12width", "70");
		settings_.setValue("gui", "column13width", "70");
		settings_.setValue("gui", "column14width", "70");
		settings_.setValue("gui", "column15width", "70");
		settings_.setValue("gui", "column16width", "70");
		settings_.setValue("gui", "column17width", "70");
		settings_.setValue("gui", "column18width", "70");
		settings_.setValue("gui", "column19width", "44");
		settings_.setValue("gui", "column20width", "44");
		settings_.setValue("gui", "column21width", "82");
		settings_.setValue("gui", "column22width", "70");
		settings_.setValue("gui", "column23width", "150");
		settings_.setValue("gui", "column24width", "150");
		settings_.setValue("gui", "column25width", "150");
		settings_.setValue("gui", "column26width", "150");
		settings_.setValue("gui", "column27width", "70");
		settings_.setValue("gui", "column28width", "70");
		settings_.setValue("gui", "column29width", "70");
		settings_.setValue("gui", "column30width", "70");
		settings_.setValue("gui", "column31width", "70");
		settings_.setValue("gui", "column32width", "70");
		settings_.setValue("gui", "column33width", "70");
		settings_.setValue("gui", "column1visible", true);
		settings_.setValue("gui", "column2visible", true);
		settings_.setValue("gui", "column3visible", true);
		settings_.setValue("gui", "column4visible", true);
		settings_.setValue("gui", "column5visible", true);
		settings_.setValue("gui", "column6visible", true);
		settings_.setValue("gui", "column7visible", true);
		settings_.setValue("gui", "column8visible", true);
		settings_.setValue("gui", "column9visible", true);
		settings_.setValue("gui", "column10visible", false);
		settings_.setValue("gui", "column11visible", false);
		settings_.setValue("gui", "column12visible", false);
		settings_.setValue("gui", "column13visible", false);
		settings_.setValue("gui", "column14visible", false);
		settings_.setValue("gui", "column15visible", false);
		settings_.setValue("gui", "column16visible", false);
		settings_.setValue("gui", "column17visible", false);
		settings_.setValue("gui", "column18visible", false);
		settings_.setValue("gui", "column19visible", false);
		settings_.setValue("gui", "column20visible", false);
		settings_.setValue("gui", "column21visible", false);
		settings_.setValue("gui", "column22visible", false);
		settings_.setValue("gui", "column23visible", false);
		settings_.setValue("gui", "column24visible", false);
		settings_.setValue("gui", "column25visible", false);
		settings_.setValue("gui", "column26visible", false);
		settings_.setValue("gui", "column27visible", false);
		settings_.setValue("gui", "column28visible", false);
		settings_.setValue("gui", "column29visible", false);
		settings_.setValue("gui", "column30visible", false);
		settings_.setValue("gui", "column31visible", false);
		settings_.setValue("gui", "column32visible", false);
		settings_.setValue("gui", "column33visible", false);
		settings_.setValue("gui", "column2_1width", "300");
		settings_.setValue("gui", "column2_2width", "250");
		settings_.setValue("gui", "column2_3width", "250");
		settings_.setValue("gui", "column2_4width", "150");
		settings_.setValue("gui", "column2_5width", "68");
		settings_.setValue("gui", "column2_6width", "46");
		settings_.setValue("gui", "column2_7width", "150");
		settings_.setValue("gui", "column2_8width", "150");
		settings_.setValue("gui", "column2_9width", "150");
		settings_.setValue("gui", "column2_10width", "112");
		settings_.setValue("gui", "column3_1width", "500");
		settings_.setValue("gui", "column3_2width", "50");
		settings_.setValue("gui", "column3_3width", "68");
		settings_.setValue("gui", "column3_4width", "150");
		settings_.setValue("gui", "column3_5width", "150");
		settings_.setValue("gui", "column3_6width", "150");
		settings_.setValue("gui", "column3_7width", "112");
		settings_.setValue("gui", "sortcolumn", "0 8");
		settings_.setValue("gui", "sortascending", "true true");
		settings_.setValue("gui", "columnorder", "0 1 2 3 4 5 6 7 8");
		settings_.setValue("gui", "sashweights", "777 222");
		settings_.setValue("gui", "screenshotsheight", "100");
		settings_.setValue("gui", "screenshotscolumnheight", "50");
		settings_.setValue("gui", "screenshotscolumnstretch", false);
		settings_.setValue("gui", "screenshotscolumnkeepaspectratio", false);
		settings_.setValue("gui", "screenshotsvisible", true);
		settings_.setValue("gui", "screenshotsmaxzoompercentage", "300");
		settings_.setValue("gui", "screenshotsmaxwidth", "1920");
		settings_.setValue("gui", "screenshotsmaxheight", "1440");
		settings_.setValue("gui", "autosortonupdate", false);
		settings_.setValue("gui", "screenshotsfilename", true);
		settings_.setValue("gui", "buttondisplay", 0);
		settings_.setValue("gui", "custom1", "Custom1");
		settings_.setValue("gui", "custom2", "Custom2");
		settings_.setValue("gui", "custom3", "Custom3");
		settings_.setValue("gui", "custom4", "Custom4");
		settings_.setValue("gui", "custom5", "Custom5");
		settings_.setValue("gui", "custom6", "Custom6");
		settings_.setValue("gui", "custom7", "Custom7");
		settings_.setValue("gui", "custom8", "Custom8");
		settings_.setValue("gui", "custom9", "Custom9");
		settings_.setValue("gui", "custom10", "Custom10");
		settings_.setValue("gui", "custom11", "Custom11");
		settings_.setValue("gui", "custom12", "Custom12");
		settings_.setValue("gui", "custom13", "Custom13");
		settings_.setValue("gui", "custom14", "Custom14");
		settings_.setValue("gui", "searchengine", "mobygames");

		settings_.setValue("gui", "notesfont", "Courier 10 0"); // 0 = SWT.NORMAL
		settings_.setValue("gui", "notesvisible", true);
		settings_.setValue("gui", "theme", 0);
		settings_.setValue("gui", "linking", 0);

		settings_.setValue("gui", "viewstyle", ProfilesListType.TABLE.toString().toLowerCase());
		settings_.setValue("gui", "small_tile_width", 100);
		settings_.setValue("gui", "small_tile_height", 82);
		settings_.setValue("gui", "medium_tile_width", 132);
		settings_.setValue("gui", "medium_tile_height", 102);
		settings_.setValue("gui", "large_tile_width", 164);
		settings_.setValue("gui", "large_tile_height", 122);
		settings_.setValue("gui", "small_box_width", 75);
		settings_.setValue("gui", "small_box_height", 100);
		settings_.setValue("gui", "medium_box_width", 120);
		settings_.setValue("gui", "medium_box_height", 150);
		settings_.setValue("gui", "large_box_width", 150);
		settings_.setValue("gui", "large_box_height", 200);
		settings_.setValue("gui", "tile_title_trunc_pos", "end");

		settings_.setValue("gui", "gallerybackgroundcolor", "-1");

		settings_.setValue("profiledefaults", "confpath", 0);
		settings_.setValue("profiledefaults", "conffile", 0);
		settings_.setValue("profiledefaults", "capturespath", 1);

		settings_.setValue("dosbox", "hideconsole", false);
		settings_.setValue("gui", "hideunsupportedsettings", false);
		settings_.setValue("gui", "statuswindowkeepopen", false);
		settings_.setValue("gui", "xbuildnoticeshown", "");
		settings_.setValue("dosbox", "detectfailedstart", true);
		settings_.setValue("dosbox", "checkversionsatstartup", true);
		settings_.setValue("dosbox", "checkforupdates", true);
		settings_.setValue("dosbox", "checkbeforestart", false);
		settings_.setValue("gui", "profilewarnings", true);
		settings_.setValue("gui", "longtooltips", true);
		settings_.setValue("edition", "checkforupdates", false);
		settings_.setValue("edition", "prereleases", true);
		settings_.setValue("edition", "updatechecked", "");
		settings_.setValue("edition", "updatenoticeshown", "");
		settings_.setValue("autoexec", "unknownlines", "ask"); // the user decides, until set otherwise
		settings_.setValue("autoexec", "nomarkers", false);
		settings_.setValue("profiles", "allsettings", false); // see FullConfProfiles: only on request, it has drawbacks
		settings_.setValue("profiles", "findextras", false); // see ExtrasFinder: link manuals etc. when a profile is added
		settings_.setValue("gui", "viewsize_tiles", 1); // the size the Tiles button brings back (0 small, 1 medium, 2 large)
		settings_.setValue("gui", "viewsize_boxes", 1);
		settings_.setValue("gui", "viewhintshown", false);
		settings_.setValue("gui", "extrasexpand", "changed");
		settings_.setValue("gui", "extrasexpandonextraspages", false);
		settings_.setValue("gui", "extrascountchanges", true);
		settings_.setValue("backup", "auto", "never");
		settings_.setValue("backup", "keep", 3);
		settings_.setValue("backup", "last", "");
		settings_.setValue("backup", "dir", "");
		settings_.setValue("gui", "updatenoticeshown", "");
		settings_.setValue("gui", "updatechecked", "");
		settings_.setValue("gui", "statuswindow_width", "800");
		settings_.setValue("gui", "statuswindow_height", "440");
		settings_.setValue("gui", "statuswindow_maximized", false);

		settings_.setValue("communication", "port_enabled", SystemUtils.IS_WINDOWS);
		settings_.setValue("communication", "port", "4740");

		settings_.setValue("database", "connectionstring", "jdbc:hsqldb:file:./db/database");
		settings_.setValue("database", "username", "sa");
		settings_.setValue("database", "pasword", "");

		settings_.setValue("mobygames_database", "connectionstring", "jdbc:hsqldb:file:./db/mobygames;shutdown=true");
		settings_.setValue("mobygames_database", "username", "sa");
		settings_.setValue("mobygames_database", "pasword", "");

		settings_.setValue("directory", "data", ".");
		settings_.setValue("directory", "dosbox", ".");
		settings_.setValue("directory", "tmpinstall", "TMP_INST");
		settings_.setValue("directory", "orgimages", "ORGIMAGE");
		settings_.setValue("directory", "parentscanberelative", false);

		settings_.setValue("locale", "language", "en");
		settings_.setValue("locale", "country", "");
		settings_.setValue("locale", "variant", "");

		settings_.setValue("log", "enabled", true);
		
		settings_.setValue("websearchengine", "search_term_filter", "freeware (freeware) shareware (shareware)");

		settings_.setValue("mobygames", "platform_filter", "dos pc<space>booter");
		settings_.setValue("mobygames", "platform_filter_ids", "2 4");
		settings_.setValue("mobygames", "set_title", true);
		settings_.setValue("mobygames", "set_developer", true);
		settings_.setValue("mobygames", "set_publisher", true);
		settings_.setValue("mobygames", "set_year", true);
		settings_.setValue("mobygames", "set_genre", true);
		settings_.setValue("mobygames", "set_link", true);
		settings_.setValue("mobygames", "set_description", true);
		settings_.setValue("mobygames", "set_rank", true);
		settings_.setValue("mobygames", "choose_coverart", false);
		settings_.setValue("mobygames", "choose_screenshot", false);
		settings_.setValue("mobygames", "force_all_regions_coverart", false);
		settings_.setValue("mobygames", "multi_max_coverart", 0);
		settings_.setValue("mobygames", "multi_max_screenshot", 0);
		settings_.setValue("mobygames", "image_width", 128);
		settings_.setValue("mobygames", "image_height", 80);
		settings_.setValue("mobygames", "image_columns", 2);
		settings_.setValue("mobygames", "api_key", "moby_cLvVy0ShCSgvuv2175i6KhOO6VU");

		settings_.setValue("metropolis", "platform_filter", "dos pc<space>booter");
		settings_.setValue("metropolis", "region", "United States");
		settings_.setValue("metropolis", "set_title", true);
		settings_.setValue("metropolis", "set_developer", true);
		settings_.setValue("metropolis", "set_publisher", true);
		settings_.setValue("metropolis", "set_year", true);
		settings_.setValue("metropolis", "set_genre", true);
		settings_.setValue("metropolis", "set_link", true);
		settings_.setValue("metropolis", "set_description", true);
		settings_.setValue("metropolis", "set_rank", true);
		settings_.setValue("metropolis", "choose_coverart", false);
		settings_.setValue("metropolis", "choose_screenshot", false);
		settings_.setValue("metropolis", "force_all_regions_coverart", false);
		settings_.setValue("metropolis", "multi_max_coverart", 0);
		settings_.setValue("metropolis", "multi_max_screenshot", 0);
		settings_.setValue("metropolis", "image_width", 128);
		settings_.setValue("metropolis", "image_height", 80);
		settings_.setValue("metropolis", "image_columns", 2);

		settings_.setValue("pouet", "platform_filter", "ms-dos ms-dos/gus");
		settings_.setValue("pouet", "set_title", true);
		settings_.setValue("pouet", "set_developer", true);
		settings_.setValue("pouet", "set_year", true);
		settings_.setValue("pouet", "set_genre", true);
		settings_.setValue("pouet", "set_link", true);
		settings_.setValue("pouet", "set_rank", true);
		settings_.setValue("pouet", "choose_coverart", false);
		settings_.setValue("pouet", "choose_screenshot", false);
		settings_.setValue("pouet", "multi_max_coverart", 0);
		settings_.setValue("pouet", "multi_max_screenshot", 0);

		settings_.setValue("thegamesdb", "platform_filter", "pc");
		settings_.setValue("thegamesdb", "set_title", true);
		settings_.setValue("thegamesdb", "set_developer", true);
		settings_.setValue("thegamesdb", "set_publisher", true);
		settings_.setValue("thegamesdb", "set_year", true);
		settings_.setValue("thegamesdb", "set_genre", true);
		settings_.setValue("thegamesdb", "set_link", true);
		settings_.setValue("thegamesdb", "set_description", true);
		settings_.setValue("thegamesdb", "choose_coverart", false);
		settings_.setValue("thegamesdb", "choose_screenshot", false);
		settings_.setValue("thegamesdb", "multi_max_coverart", 0);
		settings_.setValue("thegamesdb", "multi_max_screenshot", 0);
		settings_.setValue("thegamesdb", "image_filter", "");
		settings_.setValue("thegamesdb", "image_base", "original");
		settings_.setValue("thegamesdb", "api_key", "8364ae0f983f8484afbb70a4d1030292137deaa4131c7765c9b8c0fdfdda1cf7");
		// IGDB and SteamGridDB: each user enters own keys (free), see the settings
		for (String engine: new String[] {"igdb", "steamgriddb"}) {
			boolean igdb = engine.equals("igdb");
			settings_.setValue(engine, "platform_filter", igdb ? "PC DOS": "");
			settings_.setValue(engine, "set_title", true);
			settings_.setValue(engine, "set_developer", igdb);
			settings_.setValue(engine, "set_publisher", igdb);
			settings_.setValue(engine, "set_year", true);
			settings_.setValue(engine, "set_genre", igdb);
			settings_.setValue(engine, "set_link", true);
			settings_.setValue(engine, "set_description", igdb);
			settings_.setValue(engine, "set_rank", false);
			settings_.setValue(engine, "choose_coverart", true);
			settings_.setValue(engine, "choose_screenshot", igdb);
			settings_.setValue(engine, "multi_max_coverart", igdb ? 1: 0);
			settings_.setValue(engine, "multi_max_screenshot", 0);
		}
		settings_.setValue("igdb", "client_id", "");
		settings_.setValue("igdb", "client_secret", "");
		settings_.setValue("steamgriddb", "api_key", "");
		settings_.setValue("mobygames", "api_key", ""); // the official API: MobyGames blocks loading its web pages

		settings_.setValue("environment", "use", false);
		settings_.setValue("environment", "value", "");

		settings_.setValue("profile", "automountall_x20240301", "true false 1 0 quiet");
		settings_.setValue("profile", "freesizecap_x20240301", "true false fixed relative cap 2 1 0");
		settings_.setValue("profile", "priority_active", "lowest lower normal higher highest");
		settings_.setValue("profile", "priority_active_staging0810", "auto lowest lower normal higher highest");
		settings_.setValue("profile", "priority_inactive", "lowest lower normal higher highest pause");
		settings_.setValue("profile", "priority_inactive_staging0810", "auto lowest lower normal higher highest");
		settings_.setValue("profile", "languages", "de en es fr it nl pl ru");
		settings_.setValue("profile", "bannercolortheme_x20240301", "default black red green yellow blue magenta cyan white");
		settings_.setValue("profile", "startupverbosity_staging0810", "auto high low quiet");
		settings_.setValue("profile", "quitwarning_x20240301", "true false 1 0 auto autofile");
		settings_.setValue("profile", "configurationtooltheme_x20241001", "Windows<space>Default Arizona Black<space>Leather<space>Jacket Bordeaux Cinnamon Designer Emerald<space>City Fluorescent HotDog<space>Stand LCD<space>Default<space>Screen<space>Settings LCD<space>Reversed<space>-<space>Dark LCD<space>Reversed<space>-<space>Light Mahogany Monochrome Ocean Pastel Patchwork Plasma<space>Power<space>Saver Rugby The<space>Blues Tweed Valentine Wingtips");
		settings_.setValue("profile", "resolveconfigpath_x20240301", "true false dosvar tilde 1 0");
		settings_.setValue("profile", "hostkey_x20240301", "ctrlalt ctrlshift altshift mapper");
		settings_.setValue("profile", "workingdiroption_x20240301", "autoprompt config custom default force noprompt program prompt userconfig");
		settings_.setValue("profile", "autoexecsections_staging0810", "join overwrite");
		settings_.setValue("profile", "imgcaptureformat_staging0810", "upscaled rendered raw");
		settings_.setValue("profile", "debuggerrun_x20240301", "debugger normal watch");
		settings_.setValue("profile", "clipboardbutton_x20240301", "none middle right arrows");
		settings_.setValue("profile", "clipboardmod_x20240301", "none ctrl lctrl rctrl alt lalt ralt shift lshift rshift ctrlalt ctrlshift altshift lctrlalt lctrlshift laltshift rctrlalt rctrlshift raltshift");
		settings_.setValue("profile", "clipboardbios_x20240301", "true false 1 0 default");
		settings_.setValue("profile", "output", "ddraw overlay opengl openglnb surface");
		settings_.setValue("profile", "output_staging0810", "texture texturenb opengl openglnb");
		settings_.setValue("profile", "output_staging0820", "texture texturenb opengl");
		settings_.setValue("profile", "output_x08220", "ddraw overlay opengl openglnb openglhq surface direct3d"); // also used by daum
		settings_.setValue("profile", "output_x20240301", "default surface overlay ttf opengl openglnb openglhq openglpp ddraw direct3d");
		settings_.setValue("profile", "windowresolution", "original desktop 320x200 640x480 800x600 1024x768 1280x768 1280x960 1280x1024");
		settings_.setValue("profile", "windowresolution_staging0810", "default small s medium m large l 320x200 640x480 800x600 1024x768 1280x768 1280x960 1280x1024");
		settings_.setValue("profile", "fullresolution", "original desktop 0x0 320x200 640x480 800x600 1024x768 1280x768 1280x960 1280x1024");
		settings_.setValue("profile", "fullscreenmode_staging0830", "standard forced-borderless");
		settings_.setValue("profile", "scaler", "none normal2x normal3x advmame2x advmame3x advinterp2x advinterp3x hq2x hq3x 2xsai super2xsai supereagle tv2x tv3x rgb2x rgb3x scan2x scan3x");
		settings_.setValue("profile", "scaler_daum20150125", "none normal2x normal3x normal4x normal5x advmame2x advmame3x advinterp2x advinterp3x hq2x hq3x 2xsai super2xsai supereagle tv2x tv3x rgb2x rgb3x scan2x scan3x hardware_none hardware2x hardware3x hardware4x hardware5x xbrz");
		settings_.setValue("profile", "scaler_x20240301", "none normal2x normal3x normal4x normal5x advmame2x advmame3x advinterp2x advinterp3x hq2x hq3x 2xsai super2xsai supereagle tv2x tv3x rgb2x rgb3x scan2x scan3x gray gray2x hardware_none hardware2x hardware3x hardware4x hardware5x xbrz xbrz_bilinear");
		settings_.setValue("profile", "aspect", "true false");
		settings_.setValue("profile", "aspect_staging0810", "auto on square-pixels off stretch");
		settings_.setValue("profile", "aspect_x20240301", "false true 0 1 yes no nearest bilinear");
		settings_.setValue("profile", "frameskip", "0 1 2 3 4 5 6 7 8 9 10");
		settings_.setValue("profile", "glshader_svn4483", "none advinterp2x advinterp3x advmame2x advmame3x rgb2x rgb3x scan2x scan3x tv2x tv3x sharp");
		settings_.setValue("profile", "glshader_ecer4482", "none advinterp2x advinterp3x advmame2x advmame3x rgb2x rgb3x scan2x scan3x tv2x tv3x sharp");
		settings_.setValue("profile", "glshader_staging0810", "none crt-auto crt-auto-machine crt-auto-arcade crt-auto-arcade-sharp sharp");
		settings_.setValue("profile", "glshader_staging0830", "crt-auto crt-auto-machine crt-auto-arcade crt-auto-arcade-sharp sharp bilinear nearest");
		settings_.setValue("profile", "glshader_x20240301", "none default advinterp2x advinterp3x advmame2x advmame3x rgb2x rgb3x scan2x scan3x tv2x tv3x sharp");
		settings_.setValue("profile", "texturerenderer_staging0810", "auto direct3d direct3d11 direct3d12 opengl opengles2 software");
		settings_.setValue("profile", "dpiaware_x20240301", "true false 1 0 auto");
		settings_.setValue("profile", "windowposition_x20240301", ", -");
		settings_.setValue("profile", "windowposition_staging0810", "auto 0,0");
		settings_.setValue("profile", "aspectratio_x20240301", "-1:-1 0:0 4:3 16:9");
		settings_.setValue("profile", "integerscaling_staging0810", "auto vertical horizontal off");
		settings_.setValue("profile", "vsyncmode_staging0810", "auto on adaptive off yield");
		settings_.setValue("profile", "vsyncmode_staging0830", "off on fullscreen-only");
		settings_.setValue("profile", "vsyncmode_x20240301", "on off force host");
		settings_.setValue("profile", "hostrefreshrate_staging0810", "auto sdi vrr");
		settings_.setValue("profile", "framepresentationmode_staging0810", "auto cfr vfr");
		settings_.setValue("profile", "framepresentationmode_staging0830", "auto dos-rate host-rate");
		settings_.setValue("profile", "crtcolorprofile_staging0830", "auto none ebu p22 smpte-c philips trinitron");
		settings_.setValue("profile", "deinterlacing_staging0830", "off on light medium strong full");
		settings_.setValue("profile", "dedithering_staging0830", "off on");
		settings_.setValue("profile", "blacklevel_staging0830", "auto 0 10 20 30 40 50");
		settings_.setValue("profile", "colortemperature_staging0830", "auto 5000 5500 6500 7500 9300");
		settings_.setValue("profile", "colorspace_staging0830", "srgb display-p3 dci-p3 dci-p3-d65 modern-p3 adobe-rgb rec-2020");
		settings_.setValue("profile", "diskspeed_staging0830", "maximum fast medium slow");
		settings_.setValue("profile", "screensaver_staging0810", "auto allow block");
		settings_.setValue("profile", "ttfblinkc_x20240301", "true false 0");
		settings_.setValue("profile", "ttfwp_x20240301", "FE WP WS XY");
		settings_.setValue("profile", "ttfoutputswitch_x20240301", "auto surface opengl openglnb openglhq openglpp direct3d");
		settings_.setValue("profile", "dosversion_staging0810", "3.3 5.0 6.22 7.1");
		settings_.setValue("profile", "dosversion_x20240301", "auto 3.3 5.0 6.22 7.0 7.1");
		settings_.setValue("profile", "dosclipboarddevice_x20240301", "read write full true false disabled");
		settings_.setValue("profile", "fat32setversion_x20240301", "ask auto manual");
		settings_.setValue("profile", "doslocaleperiod_staging0810", "historic modern");
		settings_.setValue("profile", "doslocaleperiod_staging0830", "native historic modern");
		settings_.setValue("profile", "dosexpandshellvar_staging0810", "auto true false");
		settings_.setValue("profile", "dosexpandshellvar_staging0830", "auto on off");
		settings_.setValue("profile", "filelocking_staging0830", "auto on off");
		settings_.setValue("profile", "booleans", "true false");
		settings_.setValue("profile", "longfilenames_x20240301", "true false 1 0 auto autostart");
		settings_.setValue("profile", "doslogconsole_x20240301", "true false 1 0 quiet");
		settings_.setValue("profile", "autofixwarning_x20240301", "true false 1 0 both a20fix loadfix none");
		settings_.setValue("profile", "configsysshell_x20240301", "COMMAND.COM 4DOS.COM");
		settings_.setValue("profile", "configsyslastdrive_x20240301", "a b c d e f g h i j k l m n o p q r s t u v w x y z");
		settings_.setValue("profile", "configsysnumlock_x20240301", "off on");
		settings_.setValue("profile", "dosv_x20240301", "off jp ko chs cht cn tw");
		settings_.setValue("profile", "showdbcsnodosv_x20240301", "true false 1 0 auto");
		settings_.setValue("profile", "fepcontrol_x20240301", "ias mskanji both");
		settings_.setValue("profile", "vtext1_x20240301", "xga xga24 sxga sxga24 svga");
		settings_.setValue("profile", "vtext2_x20240301", "xga xga24 sxga sxga24 svga");
		settings_.setValue("profile", "dosvj3100_x20240301", "off on auto manual 0 1 2");
		settings_.setValue("profile", "dosvj3100type_x20240301", "default gt sgt gx gl sl sgx ss gs sx sxb sxw sxp ez zs zx");
		settings_.setValue("profile", "machine", "cga hercules pcjr tandy vga");
		settings_.setValue("profile", "machine073", "cga hercules pcjr tandy ega vgaonly svga_s3 svga_et3000 svga_et4000 svga_paradise vesa_nolfb vesa_oldvbe");
		settings_.setValue("profile", "machine_daum20150125", "hercules cga_mono cga pcjr tandy ega vgaonly svga_s3 svga_et3000 svga_et4000 svga_paradise vesa_nolfb vesa_oldvbe amstrad");
		settings_.setValue("profile", "machine_staging0810", "hercules cga_mono cga pcjr tandy ega svga_s3 svga_et3000 svga_et4000 svga_paradise vesa_nolfb vesa_oldvbe");
		settings_.setValue("profile", "machine_x08220",    "mda cga cga_mono cga_rgb cga_composite cga_composite2 hercules tandy pcjr amstrad ega mcga vgaonly svga_s3 svga_et3000 svga_et4000 svga_paradise vesa_nolfb vesa_oldvbe pc98 pc9801 pc9821 fm_towns");
		settings_.setValue("profile", "machine_x20240301", "mda cga cga_mono cga_rgb cga_composite cga_composite2 hercules hercules_plus hercules_incolor hercules_color tandy pcjr pcjr_composite pcjr_composite2 amstrad ega ega200 jega mcga vgaonly svga_s3 svga_s386c928 svga_s3vision864 svga_s3vision868 svga_s3vision964 svga_s3vision968 svga_s3trio32 svga_s3trio64 svga_s3trio64v+ svga_s3virge svga_s3virgevx svga_et3000 svga_et4000 svga_paradise vesa_nolfb vesa_oldvbe vesa_oldvbe10 pc98 pc9801 pc9821 svga_ati_egavgawonder svga_ati_vgawonder svga_ati_vgawonderplus svga_ati_vgawonderxl svga_ati_vgawonderxl24 svga_ati_mach8 svga_ati_mach32 svga_ati_mach64 fm_towns");
		settings_.setValue("profile", "pc98fmboard_x20240301", "auto off false board14 board26k board86 board86c");
		settings_.setValue("profile", "pc98forceibmkeyboard_x20240301", "true false 1 0 auto");
		settings_.setValue("profile", "pc98forcejiskeyboard_x20240301", "true false 1 0 auto");
		settings_.setValue("profile", "core", "dynamic full normal simple auto");
		settings_.setValue("profile", "core_x20240301", "dynamic dynamic_x86 dynamic_nodhfpu dynamic_rec full normal simple auto");
		settings_.setValue("profile", "cputype", "auto 386 386_slow 486_slow pentium_slow 386_prefetch");
		settings_.setValue("profile", "cputype_daum20150125", "auto 386 486 pentium pentium_mmx 386_prefetch");
		settings_.setValue("profile", "cputype_staging0820", "auto 386 386_fast 386_prefetch 486 pentium pentium_mmx");
		settings_.setValue("profile", "cputype_x20240301", "auto 8086 8086_prefetch 80186 80186_prefetch 286 286_prefetch 386 386_prefetch 486old 486old_prefetch 486 486_prefetch pentium pentium_mmx ppro_slow pentium_ii pentium_iii experimental");
		settings_.setValue("profile", "cycles",
				"350 500 750 1000 2000 3000 4000 5000 7500 10000 12500 15000 17500 20000 25000 30000 32500 35000 40000 45000 50000 55000 60000 auto max<space>50% max<space>80% max<space>90% max");
		settings_.setValue("profile", "cpucycles_staging0820", "auto max 300 700 1500 3000 6000 8000 12000 25000 50000 100000 200000");
		settings_.setValue("profile", "cpucyclesprotected_staging0820", "auto max 60000");
		settings_.setValue("profile", "cycles_up", "10 20 50 100 500 1000 2000 5000 10000");
		settings_.setValue("profile", "cycles_down", "10 20 50 100 500 1000 2000 5000 10000");
		settings_.setValue("profile", "fpuemulation_x20240301", "true false 1 0 auto 8087 287 387");
		settings_.setValue("profile", "memsize", "0 1 2 4 8 16 32 63");
		settings_.setValue("profile", "ems", "false emsboard emm386 true");
		settings_.setValue("profile", "ems_x20240301", "false emsboard emm386 true 1 0");
		settings_.setValue("profile", "ems_staging0830", "on off emsboard emm386");
		settings_.setValue("profile", "loadfix_value", "1 63 64 127");
		settings_.setValue("profile", "shellhigh_x20240301", "true false 1 0 auto");
		settings_.setValue("profile", "a20_x20240301", "fast mask off off_fake on on_fake");
		settings_.setValue("profile", "mcbfaults_staging0810", "repair report allow deny");
		settings_.setValue("profile", "pcjrmemorylayout_staging0811", "expanded standard");
		settings_.setValue("profile", "fdc1mode_x20240301", "ps2 ps2_model30 at xt");
		settings_.setValue("profile", "vmemsizemb_x20240301", "-1 0 1 2 4 8");
		settings_.setValue("profile", "scanlineondemand_x20240301", "true false 1 0 auto");
		settings_.setValue("profile", "vmemsize_staging0810", "auto 1 2 4 8 256 512 1024 2048 4096 8192");
		settings_.setValue("profile", "vmemdelay_staging0811", "off on");
		settings_.setValue("profile", "dosrate_staging0810", "default host");
		settings_.setValue("profile", "monochromepalette_staging0810", "amber green white paperwhite");
		settings_.setValue("profile", "monochromepalette_x20240301", "amber green white gray");
		settings_.setValue("profile", "cgacolors_staging0810", "default tandy-warm agi-amiga-v1 agi-amiga-v2 agi-amiga-v3 agi-amigaish scumm-amiga colodore colodore-sat dga16");
		settings_.setValue("profile", "vesamodes_staging0810", "compatible all halfline");
		settings_.setValue("profile", "reelmagic_staging0810", "off cardonly on");
		settings_.setValue("profile", "composite_staging0810", "auto on off");
		settings_.setValue("profile", "compositeera_staging0810", "auto old new");
		settings_.setValue("profile", "voodoo_ecer4482", "false software opengl auto");
		settings_.setValue("profile", "voodoo_x20240301", "false software opengl auto");
		settings_.setValue("profile", "voodoomem_ecer4482", "standard max");
		settings_.setValue("profile", "voodoomemsize_staging0810", "4 12");
		settings_.setValue("profile", "lfbglide_ecer4482", "full full_noaux read read_noaux write write_noaux none");
		settings_.setValue("profile", "lfbglide_x20240301", "full full_noaux read read_noaux write write_noaux none");
		settings_.setValue("profile", "voodoothreads_staging0820", "auto 1 2 4 6 8 16");
		settings_.setValue("profile", "rate", SAMPLE_RATES);
		settings_.setValue("profile", "blocksize", "256 512 1024 2048 4096 8192");
		settings_.setValue("profile", "prebuffer", "10 20 25");
		settings_.setValue("profile", "audiocrossfeed_staging0810", "off on light normal strong");
		settings_.setValue("profile", "audioreverb_staging0810", "off on tiny small medium large huge");
		settings_.setValue("profile", "audiochorus_staging0810", "off on light normal strong");
		settings_.setValue("profile", "disknoise_staging0830", "off seek-only on");
		settings_.setValue("profile", "mpu401", "none intelligent uart");
		settings_.setValue("profile", "mididevice", "alsa default coreaudio coremidi none oss win32");
		settings_.setValue("profile", "mididevice_daum20150125", "alsa default coreaudio coremidi none oss win32 mt32 synth timidity");
		settings_.setValue("profile", "mididevice_ecer4482", "alsa default coreaudio coremidi none oss win32 mt32 fluidsynth");
		settings_.setValue("profile", "mididevice_staging0810", "auto win32 fluidsynth mt32 none");
		settings_.setValue("profile", "mididevice_staging0830", "port coreaudio fluidsynth mt32 soundcanvas none");
		settings_.setValue("profile", "soundcanvasmodel_staging0830", "auto sc55 sc55_121 sc55_120 sc55_110 sc55_100 sc55_200 sc55mk2 sc55mk2_100 sc55mk2_101");
		settings_.setValue("profile", "mididevice_x20240301", "alsa default coreaudio coremidi none oss win32 mt32 fluidsynth synth timidity");
		settings_.setValue("profile", "midisamplerate_x20240301", SAMPLE_RATES);
		settings_.setValue("profile", "mpubase_x20240301", "0 300 310 320 330 332 334 336 340 360 c0d0 c8d0 d0d0 d8d0 e0d0 e8d0 f0d0 f8d0 80d2 80d4 80d6 80d8 80da 80dc 80de");
		settings_.setValue("profile", "mpuirq_x20240301", "-1 0 3 5 7 9 10 11 12");
		settings_.setValue("profile", "fluidsynthdriver_ecer4482", "pulseaudio alsa oss coreaudio dsound portaudio sndman jack file default");
		settings_.setValue("profile", "fluidsynthdriver_x20240301", "pulseaudio alsa oss coreaudio dsound portaudio sndman jack file default");
		settings_.setValue("profile", "fluidsynthsamplerate_ecer4482", SAMPLE_RATES);
		settings_.setValue("profile", "fluidsynthchorus_staging0810", "auto on off");
		settings_.setValue("profile", "fluidsynthreverb_staging0810", "auto on off");
		settings_.setValue("profile", "mt32model_staging0810", "auto cm32l cm32l_102 cm32l_100 cm32ln_100 mt32 mt32_old mt32_107 mt32_106 mt32_105 mt32_104 mt32_bluer mt32_new mt32_207 mt32_206 mt32_204 mt32_203");
		settings_.setValue("profile", "mt32model_x20240301", "auto cm32l mt32");
		settings_.setValue("profile", "mt32dac_ecer4482", "0 1 2 3");
		settings_.setValue("profile", "mt32rate_ecer4482", SAMPLE_RATES);
		settings_.setValue("profile", "mt32analog_ecer4482", "0 1 2 3");
		settings_.setValue("profile", "mt32srcquality_ecer4482", "0 1 2 3");
		settings_.setValue("profile", "mt32reverbmode_ecer4482", "0 1 2 3 auto");
		settings_.setValue("profile", "mt32reverbtime_ecer4482", "0 1 2 3 4 5 6 7");
		settings_.setValue("profile", "mt32reverblevel_ecer4482", "0 1 2 3 4 5 6 7");
		settings_.setValue("profile", "sbtype", "none gb sb1 sb2 sbpro1 sbpro2 sb16");
		settings_.setValue("profile", "sbtype_daum20150125", "none gb sb1 sb2 sbpro1 sbpro2 sb16 sb16vibra");
		settings_.setValue("profile", "sbtype_staging0820", "none gb sb1 sb2 sbpro1 sbpro2 sb16 ess");
		settings_.setValue("profile", "sbtype_x20240301", "none gb sb1 sb2 sbpro1 sbpro2 sb16 sb16vibra ess688 ess1688 reveal_sc400");
		settings_.setValue("profile", "sbbase", BASE_ADDRS);
		settings_.setValue("profile", "sbbase_x20240301", "220 240 260 280 2a0 2c0 2e0 d2 d4 d6 d8 da dc de");
		settings_.setValue("profile", "oplrate", SAMPLE_RATES);
		settings_.setValue("profile", "irq", IRQS);
		settings_.setValue("profile", "sbirq_x20240301", "-1 0 3 5 7 9 10 11 12");
		settings_.setValue("profile", "oplmode", "auto cms opl2 dualopl2 opl3 none");
		settings_.setValue("profile", "oplmode_svn4483", "auto cms opl2 dualopl2 opl3 opl3gold none");
		settings_.setValue("profile", "oplmode_daum20150125", "auto cms opl2 dualopl2 opl3 none hardware hardwaregb");
		settings_.setValue("profile", "oplmode_ecer4482", "auto cms opl2 dualopl2 opl3 opl3gold none");
		settings_.setValue("profile", "oplmode_staging0810", "auto cms opl2 dualopl2 opl3 opl3gold none");
		settings_.setValue("profile", "oplmode_staging0820", "auto opl2 dualopl2 opl3 opl3gold esfm none");
		settings_.setValue("profile", "oplmode_x20240301", "auto cms opl2 dualopl2 opl3 opl3gold none hardware hardwaregb esfm");
		settings_.setValue("profile", "oplmode_x20241001", "auto opl2 dualopl2 opl3 opl3gold none hardware hardwaregb esfm");
		settings_.setValue("profile", "dma", DMAS);
		settings_.setValue("profile", "sbdma_x20240301", "-1 0 1 3 5 6 7");
		settings_.setValue("profile", "oplemu", "default compat fast");
		settings_.setValue("profile", "oplemu_svn4483", "default compat fast mame");
		settings_.setValue("profile", "oplemu_ecer4482", "default compat fast mame nuked");
		settings_.setValue("profile", "oplemu_x20240301", "default compat fast mame nuked opl2board opl3duoboard retrowave_opl3 esfmu");
		settings_.setValue("profile", "hdma", DMAS);
		settings_.setValue("profile", "sbhdma_x20240301", "-1 0 1 3 5 6 7");
		settings_.setValue("profile", "sboplfadeout_staging0810", "on off");
		settings_.setValue("profile", "sbfilter_staging0810", "auto sb1 sb2 sbpro1 sbpro2 sb16 modern off");
		settings_.setValue("profile", "sboplfilter_staging0810", "auto sb1 sb2 sbpro1 sbpro2 sb16 off");
		settings_.setValue("profile", "sbcms_staging0820", "on off auto");
		settings_.setValue("profile", "sbcms_x20241001", "on off auto");
		settings_.setValue("profile", "sbcmsfilter_staging0810", "on off");
		settings_.setValue("profile", "sbretrowavebus_x20240301", "serial spi");
		settings_.setValue("profile", "sbrecordingsource_x20240301", "silence hiss 1khz<space>tone");
		settings_.setValue("profile", "sbhardwarebase_x20240301", "210 220 230 240 250 260 280");
		settings_.setValue("profile", "gusrate", SAMPLE_RATES);
		settings_.setValue("profile", "gusbase", BASE_ADDRS);
		settings_.setValue("profile", "gusbase_staging0820", "210 220 230 240 250 260");
		settings_.setValue("profile", "irq1", IRQS);
		settings_.setValue("profile", "irq1_staging0820", "2 3 5 7 11 12 15");
		settings_.setValue("profile", "irq2", IRQS);
		settings_.setValue("profile", "dma1", DMAS);
		settings_.setValue("profile", "dma1_staging0820", "1 3 5 6 7");
		settings_.setValue("profile", "dma2", DMAS);
		settings_.setValue("profile", "gustype_x20240301", "classic classic37 max interwave");
		settings_.setValue("profile", "gusmemsize_x20240301", "-1 512 1024 2048 4096");
		settings_.setValue("profile", "gusglobalregisterreadalias_x20240301", "true false 1 0 auto");
		settings_.setValue("profile", "pcspeaker", "true false");
		settings_.setValue("profile", "pcspeaker_staging0810", "discrete impulse none off");
		settings_.setValue("profile", "pcrate", SAMPLE_RATES);
		settings_.setValue("profile", "pcrate_x20240301", "8000 11025 16000 22050 32000 44100 48000 49716 65536");
		settings_.setValue("profile", "pcspeakerfilter_staging0810", "on off");
		settings_.setValue("profile", "tandy", "auto off on");
		settings_.setValue("profile", "tandy_staging0810", "auto off on psg");
		settings_.setValue("profile", "tandyrate", SAMPLE_RATES);
		settings_.setValue("profile", "tandyfilter_staging0810", "on off");
		settings_.setValue("profile", "tandydacfilter_staging0810", "on off");
		settings_.setValue("profile", "tandyfadeout_staging0810", "on off");
		settings_.setValue("profile", "ps1_staging0810", "true false");
		settings_.setValue("profile", "ps1_x20240301", "on off");
		settings_.setValue("profile", "ps1rate_x20240301", SAMPLE_RATES);
		settings_.setValue("profile", "ps1filter_staging0810", "on off");
		settings_.setValue("profile", "ps1dacfilter_staging0810", "on off");
		settings_.setValue("profile", "lptdac_staging0810", "none disney covox ston1 off");
		settings_.setValue("profile", "lptdacfilter_staging0810", "on off");
		settings_.setValue("profile", "imfcbase_staging0810", "2a20 2a30");
		settings_.setValue("profile", "imfcirq_staging0810", "2 3 4 5 6 7");
		settings_.setValue("profile", "imfcfilter_staging0810", "on off");
		settings_.setValue("profile", "innovamodel_staging0810", "auto 6581 8580 none");
		settings_.setValue("profile", "innovarate_x20240301", SAMPLE_RATES);
		settings_.setValue("profile", "innovaport_staging0810", "240 260 280 2a0 2c0");
		settings_.setValue("profile", "innovaport_x20240301", "220 240 260 280 2a0 2c0 2e0 300");
		settings_.setValue("profile", "innovaclock_staging0810", "default c64ntsc c64pal hardsid");
		settings_.setValue("profile", "innovafilter_staging0810", "off");
		settings_.setValue("profile", "usescancodes", "true false");
		settings_.setValue("profile", "usescancodes_x20240301", "true false 1 0 auto");
		settings_.setValue("profile", "keyboardlayout",
				"auto none ba234 be120 bg241 bg442 bl463 br274 br275 by463 ca58 ca445 cf58 cf445 cf501 cz243 de129 de453 dk159 dv103 ee454 el220 el319 el459 es172 es173 et454 fi153 fo fr120 fr189 "
						+ "gk220 gk319 gk459 gr129 gr453 hr234 hu208 is161 is197 is458 it141 it142 la171 lt210 lt211 lt212 lt221 lt456 lh103 mk449 ml47 mt47 nl143 no155 ph pl214 pl457 po163 rh103 "
						+ "ro333 ro446 ru441 ru443 sd150 sf150 sg150 si234 sk245 sp172 sp173 sq448 sq452 sr118 sr450 su153 sv153 tm tr179 tr440 ua465 uk166 uk168 ur465 us103 ux103 yc118 yc450 yu234");
		settings_.setValue("profile", "controllertype_x20240301", "auto at xt pcjr pc98");
		settings_.setValue("profile", "mappersendkey_x20240301", "winlogo winmenu alttab ctrlesc ctrlbreak ctrlaltdel");
		settings_.setValue("profile", "ime_x20240301", "true false 1 0 auto");
		settings_.setValue("profile", "sensitivity", "10 20 30 40 50 60 70 80 90 100 100,-50 125 150 175 200 250 300 350 400 450 500 550 600 700 800 900 1000");
		settings_.setValue("profile", "mouseemu_x20240301", "integration locked always never");
		settings_.setValue("profile", "mousecapture_staging0810", "seamless onclick onstart nomouse");
		settings_.setValue("profile", "middleunlock_x20240301", "none manual auto both");
		settings_.setValue("profile", "autolockfeedback_x20240301", "none beep flash");
		settings_.setValue("profile", "mousecommodel_staging0810", "2button 3button wheel msm 2button+msm 3button+msm wheel+msm");
		settings_.setValue("profile", "mouseps2model_staging0810", "standard intellimouse explorer none");
		settings_.setValue("profile", "builtindosmousedriver_staging0830", "on no-tsr off");
		settings_.setValue("profile", "builtindosmousedrivermodel_staging0830", "2button 3button wheel");
		settings_.setValue("profile", "builtindosmousedrivermovethreshold_staging0830", "1 2 3 4 5 6 7 8 9");
		settings_.setValue("profile", "builtindosmousedriveroptions_staging0830", "immediate modern no-granularity");
		settings_.setValue("profile", "auxdevice_x20240301", "none 2button 3button intellimouse intellimouse45");
		settings_.setValue("profile", "joysticktype", "auto none 2axis 4axis 4axis_2 ch fcs");
		settings_.setValue("profile", "joysticktype_staging0810", "auto hidden disabled 2axis 4axis 4axis_2 ch fcs");
		settings_.setValue("profile", "printoutput_x20240301", "png ps bmp printer");
		settings_.setValue("profile", "printdbcs_x20240301", "true false 1 0 auto");
		settings_.setValue("profile", "ne2000backend_x20240301", "auto none nothing pcap slirp");
		settings_.setValue("profile", "ne2000base_staging0810", "200 220 240 260 280 2c0 300 320 340 360");
		settings_.setValue("profile", "ne2000irq_staging0810", "3 4 5 9 10 11 12 14 15");
		
		settings_.setValue("profile", "mount_type", "cdrom dir floppy overlay");
		settings_.setValue("profile", "imgmount_type", "iso floppy hdd");
		settings_.setValue("profile", "imgmount_fs", "iso fat none");
		settings_.setValue("profile", "zipmount_type", "cdrom dir floppy");
		settings_.setValue("profile", "freesize", "1 10 100 200 500 1000");
		settings_.setValue("profile", "lowlevelcd_type", "aspi ioctl ioctl_dx ioctl_dio ioctl_mci noioctl");
		settings_.setValue("profile", "uniquemapperfile", "%1$d.map");
		
		settings_.setValue("addgamewizard", "requiresinstallation", false);
		settings_.setValue("addgamewizard", "consultsearchengine", true);
		settings_.setValue("addgamewizard", "consultdbconfws", true);
		settings_.setValue("addgamewizard", "useuniquemapperfile", false);

		settings_.setValue("exportwizard", "gamedata", false);

		settings_.setValue("confsharing", "endpoint", "https://share.dbgl.org/DBConfWS/apiv1/");
	}
}
