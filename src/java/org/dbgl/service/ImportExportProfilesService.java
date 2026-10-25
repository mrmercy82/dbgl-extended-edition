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

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.constants.Constants;
import org.dbgl.model.GamePack;
import org.dbgl.model.NativeCommand;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.entity.GamePackEntry;
import org.dbgl.model.factory.DosboxVersionFactory;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class ImportExportProfilesService {

	public static final String PROFILES_XML_FORMAT_VERSION = "1.4";
	public static final String PROFILES_XML = "profiles.xml";

	private static final ITextService text = TextService.getInstance();
	private static final SettingsService settings = SettingsService.getInstance();

	private ImportExportProfilesService() {
	}

	public static String doImport(Document doc, GamePack packageData) throws XPathExpressionException, ParseException {
		StringBuilder warningsLog = new StringBuilder();

		XPathFactory xfactory = XPathFactory.newInstance();
		XPath xPath = xfactory.newXPath();
		packageData.setVersion(xPath.evaluate("/document/export/format-version", doc));
		packageData.setTitle(xPath.evaluate("/document/export/title", doc));
		packageData.setAuthor(xPath.evaluate("/document/export/author", doc));
		packageData.setNotes(xPath.evaluate("/document/export/notes", doc));
		String[] customFieldTitles = new String[Constants.EDIT_COLUMN_NAMES];
		for (int i = 0; i < Constants.EDIT_COLUMN_NAMES; i++) {
			customFieldTitles[i] = xPath.evaluate("/document/export/custom" + (i + 1), doc);
		}
		packageData.setCustomFieldTitles(customFieldTitles);
		packageData.setCreationApp(xPath.evaluate("/document/export/generator-title", doc));
		packageData.setCreationAppVersion(xPath.evaluate("/document/export/generator-version", doc));
		packageData.setCreationDate(settings.dateTimeFormat().parse(xPath.evaluate("/document/export/creationdatetime", doc)));
		packageData.setCapturesAvailable(Boolean.valueOf(xPath.evaluate("/document/export/captures-available", doc)));
		packageData.setMapperfilesAvailable(!packageData.getVersion().equals("1.0") && Boolean.valueOf(xPath.evaluate("/document/export/keymapperfiles-available", doc)));
		packageData.setNativecommandsAvailable(
			!packageData.getVersion().equals("1.0") && !packageData.getVersion().equals("1.1") && Boolean.valueOf(xPath.evaluate("/document/export/nativecommands-available", doc)));
		packageData.setGamedataAvailable(Boolean.valueOf(xPath.evaluate("/document/export/gamedata-available", doc)));

		Set<DosboxVersion> dbSet = packageData.getDosboxVersions();
		NodeList profNodes = (NodeList)xPath.evaluate("/document/profile", doc, XPathConstants.NODESET);

		for (int i = 0; i < profNodes.getLength(); i++) {
			Element profileNode = (Element)profNodes.item(i);

			int id = Integer.parseInt(XmlUtils.getTextValue(profileNode, "id"));
			String title = XmlUtils.getTextValue(profileNode, "title");

			Element metainfo = XmlUtils.getNode(profileNode, "meta-info");
			String developer = XmlUtils.getTextValue(metainfo, "developer");
			String publisher = XmlUtils.getTextValue(metainfo, "publisher");
			String year = XmlUtils.getTextValue(metainfo, "year");
			String genre = XmlUtils.getTextValue(metainfo, "genre");
			String status = XmlUtils.getTextValue(metainfo, "status");
			String notes = XmlUtils.getTextValue(metainfo, "notes");
			boolean favorite = Boolean.parseBoolean(XmlUtils.getTextValue(metainfo, "favorite"));
			String[] customString;
			if (XmlUtils.getNode(metainfo, "custom11") != null) {
				customString = new String[] {XmlUtils.getTextValue(metainfo, "custom1"), XmlUtils.getTextValue(metainfo, "custom2"), XmlUtils.getTextValue(metainfo, "custom3"),
						XmlUtils.getTextValue(metainfo, "custom4"), XmlUtils.getTextValue(metainfo, "custom5"), XmlUtils.getTextValue(metainfo, "custom6"), XmlUtils.getTextValue(metainfo, "custom7"),
						XmlUtils.getTextValue(metainfo, "custom8"), XmlUtils.getTextValue(metainfo, "custom11"), XmlUtils.getTextValue(metainfo, "custom12"),
						XmlUtils.getTextValue(metainfo, "custom13"), XmlUtils.getTextValue(metainfo, "custom14")};
			} else {
				customString = new String[] {XmlUtils.getTextValue(metainfo, "custom1"), XmlUtils.getTextValue(metainfo, "custom2"), XmlUtils.getTextValue(metainfo, "custom3"),
						XmlUtils.getTextValue(metainfo, "custom4"), XmlUtils.getTextValue(metainfo, "custom5"), XmlUtils.getTextValue(metainfo, "custom6"), XmlUtils.getTextValue(metainfo, "custom7"),
						XmlUtils.getTextValue(metainfo, "custom8"), StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY};
			}
			int[] customInt = new int[] {Integer.valueOf(XmlUtils.getTextValue(metainfo, "custom9")), Integer.valueOf(XmlUtils.getTextValue(metainfo, "custom10"))};
			String[] link = new String[Profile.NR_OF_LINK_DESTINATIONS];
			String[] linkTitle = new String[Profile.NR_OF_LINK_TITLES];
			for (int j = 0; j < Profile.NR_OF_LINK_TITLES; j++) {
				if (packageData.getVersion().equals("1.0") && j >= 4) {
					link[j] = StringUtils.EMPTY;
					linkTitle[j] = StringUtils.EMPTY;
				} else {
					link[j] = XmlUtils.getNestedTextValue(metainfo, "link" + (j + 1), "raw");
					linkTitle[j] = XmlUtils.getNestedTextValue(metainfo, "link" + (j + 1), "title");
				}
			}

			String confFile = XmlUtils.getNestedTextValue(profileNode, "config-file", "raw");
			String captures = XmlUtils.getNestedTextValue(profileNode, "captures", "raw");

			String gameDir = XmlUtils.getNestedTextValue(profileNode, "game-dir", "raw");
			String setup = FilenameUtils.separatorsToSystem(XmlUtils.getTextValue(profileNode, "setup"));
			String setupParams = XmlUtils.getTextValue(profileNode, "setup-parameters");
			String[] altExe = new String[] {StringUtils.EMPTY, StringUtils.EMPTY};
			String[] altExeParams = new String[] {StringUtils.EMPTY, StringUtils.EMPTY};
			String mapperfile = StringUtils.EMPTY;
			if (!packageData.getVersion().equals("1.0")) {
				altExe = new String[] {FilenameUtils.separatorsToSystem(XmlUtils.getTextValue(profileNode, "altexe1")),
						FilenameUtils.separatorsToSystem(XmlUtils.getTextValue(profileNode, "altexe2"))};
				altExeParams = new String[] {XmlUtils.getTextValue(profileNode, "altexe1-parameters"), XmlUtils.getTextValue(profileNode, "altexe2-parameters")};
				Element map = XmlUtils.getNode(profileNode, "keymapper-file");
				if (map != null) {
					mapperfile = XmlUtils.getTextValue(map, "raw");
				}
			}

			String fullConfig = XmlUtils.getTextValue(profileNode, "full-configuration");
			String incrConfig = XmlUtils.getTextValue(profileNode, "incremental-configuration");

			List<NativeCommand> nativeCommands = new ArrayList<>();
			if (packageData.isNativecommandsAvailable()) {
				Element nativecommands = XmlUtils.getNode(profileNode, "native-commands");
				if (nativecommands != null) {
					NodeList cmds = nativecommands.getChildNodes();
					for (int j = 0; j < cmds.getLength(); j++) {
						Node node = cmds.item(i);
						if (node instanceof Element) {
							Element cmd = (Element)cmds.item(i);
							nativeCommands.add(new NativeCommand(XmlUtils.getTextValue(cmd, "command"), XmlUtils.getTextValue(cmd, "parameters"), XmlUtils.getTextValue(cmd, "cwd"),
									Boolean.valueOf(XmlUtils.getTextValue(cmd, "waitfor")), Integer.valueOf(XmlUtils.getTextValue(cmd, "ordernr"))));
						}
					}
				}
			}
			NativeCommand.insertDosboxCommand(nativeCommands);

			Element dosboxNode = XmlUtils.getNode(profileNode, "dosbox");
			DosboxVersion dosboxVersion = DosboxVersionFactory.create(XmlUtils.getTextValue(dosboxNode, "title"), XmlUtils.getTextValue(dosboxNode, "family"), XmlUtils.getTextValue(dosboxNode, "version"), false, true, false, null,
				StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY);

			Optional<Integer> dosboxVersionIndex = dbSet.stream().filter(x -> x.compareTo(dosboxVersion) == 0).findFirst().map(DosboxVersion::getId);
			if (dosboxVersionIndex.isPresent()) {
				dosboxVersion.setId(dosboxVersionIndex.get());
			} else {
				dosboxVersion.setId(dbSet.size());
				dbSet.add(dosboxVersion);
			}

			Profile profile = ProfileFactory.create(title, favorite, developer, publisher, genre, year, status, notes, customString, customInt, link, linkTitle, setup, setupParams, altExe,
				altExeParams, nativeCommands, dosboxVersion, confFile);
			GamePackEntry impProfile = new GamePackEntry(id, profile, packageData, captures, mapperfile, gameDir, fullConfig, incrConfig);

			packageData.getEntries().add(impProfile);
		}

		return warningsLog.toString();
	}

	public static void export(List<Profile> profiles, File xmlFile, File xsltFile, File exportFile) throws IOException, ParserConfigurationException, TransformerException {
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = doc.createElement("document");
		root.appendChild(createExportElement(doc, text.get("exportlist.title"), StringUtils.EMPTY, StringUtils.EMPTY, false, false, false, false));
		for (Profile profile: profiles)
			root.appendChild(createProfileElement(doc, null, profile, false, false));
		doc.appendChild(root);

		if (xmlFile != null) {
			XmlUtils.saveDocument(doc, xmlFile, null);
		}
		XmlUtils.saveDocument(doc, exportFile, xsltFile);
	}

	public static void export(GamePack gamePack, ZipOutputStream zipOutputStream) throws ParserConfigurationException, IOException, TransformerException {
		export(gamePack, gamePack.getEntries(), zipOutputStream);
	}

	public static void export(GamePack gamePack, List<GamePackEntry> gamePackEntries, ZipOutputStream zipOutputStream) throws ParserConfigurationException, IOException, TransformerException {
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = doc.createElement("document");

		root.appendChild(createExportElement(doc, gamePack.getTitle(), gamePack.getAuthor(), gamePack.getNotes(), gamePack.isCapturesAvailable(), gamePack.isMapperfilesAvailable(),
			gamePack.isNativecommandsAvailable(), gamePack.isGamedataAvailable()));
		for (GamePackEntry entry: gamePackEntries) {
			root.appendChild(createProfileElement(doc, entry, entry.getProfile(), gamePack.isMapperfilesAvailable(), gamePack.isNativecommandsAvailable()));
		}
		doc.appendChild(root);

		XmlUtils.saveDocumentToZipOutputStream(doc, new File(PROFILES_XML), zipOutputStream);
	}

	private static Element createExportElement(Document doc, String title, String author, String notes, boolean captures, boolean mapperFiles, boolean nativeCommands, boolean gameData) {
		Element export = doc.createElement("export");
		XmlUtils.addElement(export, "format-version", PROFILES_XML_FORMAT_VERSION);
		XmlUtils.addCDataElement(export, "title", title);
		XmlUtils.addCDataElement(export, "author", author);
		XmlUtils.addCDataElement(export, "notes", XmlUtils.cleanEolnForXml(notes));
		XmlUtils.addCDataElement(export, "creationdatetime", settings.dateTimeFormat().format(new Date()));
		XmlUtils.addCDataElement(export, "generator-title", Constants.PROGRAM_NAME_FULL);
		XmlUtils.addElement(export, "generator-version", Constants.PROGRAM_VERSION);
		XmlUtils.addElement(export, "captures-available", String.valueOf(captures));
		XmlUtils.addElement(export, "keymapperfiles-available", String.valueOf(mapperFiles));
		XmlUtils.addElement(export, "nativecommands-available", String.valueOf(nativeCommands));
		XmlUtils.addElement(export, "gamedata-available", String.valueOf(gameData));
		for (int i = 0; i < Constants.EDIT_COLUMN_NAMES; i++)
			XmlUtils.addCDataElement(export, "custom" + (i + 1), settings.getValue("gui", "custom" + (i + 1)));
		return export;
	}

	private static Element createProfileElement(Document doc, GamePackEntry entry, Profile profile, boolean mapperFiles, boolean nativeCommands) {
		Element profEmt = doc.createElement("profile");
		XmlUtils.addCDataElement(profEmt, "title", profile.getTitle());
		XmlUtils.addElement(profEmt, "id", String.valueOf(entry == null ? profile.getId(): entry.getId()));
		Element captures = doc.createElement("captures");
		if (entry == null) {
			XmlUtils.addElement(captures, "raw", profile.getCapturesString());
			XmlUtils.addElement(captures, "url", profile.getCapturesUrl());
		} else {
			XmlUtils.addElement(captures, "raw", entry.getArchiveCapturesAsDosString());
		}
		profEmt.appendChild(captures);
		Element config = doc.createElement("config-file");
		XmlUtils.addElement(config, "raw", FilenameUtils.separatorsToWindows(profile.getConfigurationFile().getPath()));
		if (entry == null)
			XmlUtils.addElement(config, "url", profile.getConfigurationFileUrl());
		profEmt.appendChild(config);
		if (entry != null) {
			if (mapperFiles && profile.getCustomMapperFile() != null) {
				XmlUtils.addNestedElement(profEmt, "keymapper-file", "raw", entry.getArchiveMapperAsDosString());
			}
			XmlUtils.addNestedElement(profEmt, "game-dir", "raw", entry.getGameDirAsDosString());
		}
		XmlUtils.addElement(profEmt, "setup", FilenameUtils.separatorsToWindows(profile.getSetupString()));
		for (int i = 0; i < Profile.NR_OF_ALT_EXECUTABLES; i++) {
			XmlUtils.addElement(profEmt, "altexe" + (i + 1), FilenameUtils.separatorsToWindows(profile.getAltExeStrings()[i]));
		}
		XmlUtils.addElement(profEmt, "setup-parameters", profile.getSetupParams());
		for (int i = 0; i < Profile.NR_OF_ALT_EXECUTABLES; i++) {
			XmlUtils.addElement(profEmt, "altexe" + (i + 1) + "-parameters", profile.getAltExeParams()[i]);
		}
		Element meta = doc.createElement("meta-info");
		XmlUtils.addCDataElement(meta, "developer", profile.getDeveloper());
		XmlUtils.addCDataElement(meta, "publisher", profile.getPublisher());
		XmlUtils.addCDataElement(meta, "year", profile.getYear());
		XmlUtils.addCDataElement(meta, "genre", profile.getGenre());
		XmlUtils.addCDataElement(meta, "status", profile.getStatus());
		XmlUtils.addElement(meta, "favorite", String.valueOf(profile.isFavorite()));
		XmlUtils.addCDataElement(meta, "notes", XmlUtils.cleanEolnForXml(profile.getNotes()));
		int fields = Profile.NR_OF_CUSTOM_STRING_DROPDOWNS + Profile.NR_OF_CUSTOM_STRING_FIELDS_1;
		for (int i = 0; i < fields; i++) {
			XmlUtils.addCDataElement(meta, "custom" + (i + 1), profile.getCustomStrings()[i]);
		}
		for (int i = 0; i < Profile.NR_OF_CUSTOM_INTS; i++) {
			XmlUtils.addElement(meta, "custom" + (i + 1 + fields), String.valueOf(profile.getCustomInts()[i]));
		}
		for (int i = 0; i < Profile.NR_OF_CUSTOM_STRING_FIELDS_2; i++) {
			XmlUtils.addCDataElement(meta, "custom" + (i + 1 + fields + Profile.NR_OF_CUSTOM_INTS), profile.getCustomStrings()[i + fields]);
		}
		for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
			Element link = doc.createElement("link" + (i + 1));
			XmlUtils.addElement(link, "raw", StringUtils.defaultString(profile.getLinks()[i].getDestination()));
			if (entry == null)
				XmlUtils.addElement(link, "url", StringUtils.defaultString(profile.getLinks()[i].getUrl()));
			XmlUtils.addCDataElement(link, "title", StringUtils.defaultString(profile.getLinks()[i].getTitle()));
			meta.appendChild(link);
		}
		profEmt.appendChild(meta);
		if (entry != null) {
			XmlUtils.addCDataElement(profEmt, "full-configuration", XmlUtils.cleanEolnForXml(profile.getCombinedConfiguration().toString(null)));
			XmlUtils.addCDataElement(profEmt, "incremental-configuration", XmlUtils.cleanEolnForXml(profile.getConfigurationString()));
		}
		Element dosbox = doc.createElement("dosbox");
		XmlUtils.addCDataElement(dosbox, "title", profile.getDosboxVersion().getTitle());
		XmlUtils.addElement(dosbox, "family", profile.getDosboxVersion().getFamily());
		XmlUtils.addElement(dosbox, "version", profile.getDosboxVersion().getVersion());
		profEmt.appendChild(dosbox);
		if (nativeCommands && entry != null && profile.getNativeCommands().size() > 1) {
			Element nativecommands = doc.createElement("native-commands");
			for (NativeCommand cmd: profile.getNativeCommands()) {
				if (!cmd.isDosboxCommand()) {
					Element nativecommand = doc.createElement("native-command");
					XmlUtils.addElement(nativecommand, "command", cmd.getCommand().getPath());
					XmlUtils.addElement(nativecommand, "parameters", cmd.getParameters());
					XmlUtils.addElement(nativecommand, "cwd", cmd.getCwd().getPath());
					XmlUtils.addElement(nativecommand, "waitfor", String.valueOf(cmd.isWaitFor()));
					XmlUtils.addElement(nativecommand, "ordernr", String.valueOf(cmd.getOrderNr()));
					nativecommands.appendChild(nativecommand);
				}
			}
			profEmt.appendChild(nativecommands);
		}
		return profEmt;
	}
}