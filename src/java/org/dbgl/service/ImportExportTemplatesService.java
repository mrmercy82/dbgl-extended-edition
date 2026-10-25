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
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.constants.Constants;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.factory.DosboxVersionFactory;
import org.dbgl.model.factory.TemplateFactory;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.TemplateRepository;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


public class ImportExportTemplatesService {

	private static final String TEMPLATES_XML_FORMAT_VERSION = "1.0";
	private static final String EXP_TITLE = "DBGL default templates";
	private static final String EXP_NOTES = StringUtils.EMPTY;
	private static final String EXP_AUTHOR = "rcblanke";

	private ImportExportTemplatesService() {
	}

	public static String doImport(List<Template> templates) throws SQLException, XPathExpressionException, ParseException, SAXException, IOException, ParserConfigurationException {
		StringBuilder warningsLog = new StringBuilder();
		ITextService text = TextService.getInstance();
		List<DosboxVersion> dbversionsList = new DosboxVersionRepository().listAll();

		File defaultXml = FileLocationService.getInstance().getDefaultTemplatesXmlFile();
		if (!FilesUtils.isExistingFile(defaultXml))
			throw new IOException(text.get("general.error.openfile", new Object[] {defaultXml}));

		Document doc = XmlUtils.getDocumentBuilder().parse(defaultXml);
		XPath xPath = XPathFactory.newInstance().newXPath();
		String packageVersion = xPath.evaluate("/document/export/format-version", doc);
		String packageTitle = xPath.evaluate("/document/export/title", doc);
		String packageAuthor = xPath.evaluate("/document/export/author", doc);
		String packageNotes = xPath.evaluate("/document/export/notes", doc);
		String creationApp = xPath.evaluate("/document/export/generator-title", doc);
		String creationAppVersion = xPath.evaluate("/document/export/generator-version", doc);
		Date creationDate = SettingsService.getInstance().dateTimeFormat().parse(xPath.evaluate("/document/export/creationdatetime", doc));

		System.out.println(text.get("dialog.import.importing",
			new Object[] {StringUtils.join(new String[] {packageTitle, packageVersion, packageAuthor, packageNotes, creationApp, creationAppVersion, creationDate.toString()}, ' ')}));

		NodeList templateNodes = (NodeList)xPath.evaluate("/document/template", doc, XPathConstants.NODESET);

		for (int i = 0; i < templateNodes.getLength(); i++) {
			Element templateNode = (Element)templateNodes.item(i);
			String templateTitle = XmlUtils.getTextValue(templateNode, "title");
			String incrConfig = XmlUtils.getTextValue(templateNode, "incremental-configuration");
			Element dosboxNode = XmlUtils.getNode(templateNode, "dosbox");
			String dosboxTitle = XmlUtils.getTextValue(dosboxNode, "title");
			String dosboxFamily = XmlUtils.getTextValue(dosboxNode, "family");
			String dosboxVersion = XmlUtils.getTextValue(dosboxNode, "version");
			DosboxVersion tmp = DosboxVersionFactory.create(dosboxTitle, dosboxFamily, dosboxVersion, true, true, false, null, null, null, null, null);
			DosboxVersion dbTemplate = DosboxVersionRepository.findBestMatch(dbversionsList, tmp);
			Template template = TemplateFactory.create(templateTitle, dbTemplate);
			warningsLog.append(template.loadConfigurationData(text, incrConfig, defaultXml));
			templates.add(new TemplateRepository().add(template));
		}
		return warningsLog.toString();
	}

	public static String export(List<Template> templates) throws IOException, ParserConfigurationException, TransformerException {
		StringBuilder warningsLog = new StringBuilder();
		for (Template template: templates) {
			warningsLog.append(template.resetAndLoadConfiguration());
		}
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = doc.createElement("document");
		Element export = doc.createElement("export");
		XmlUtils.addElement(export, "format-version", TEMPLATES_XML_FORMAT_VERSION);
		XmlUtils.addCDataElement(export, "title", EXP_TITLE);
		XmlUtils.addCDataElement(export, "author", EXP_AUTHOR);
		XmlUtils.addCDataElement(export, "notes", XmlUtils.cleanEolnForXml(EXP_NOTES));
		XmlUtils.addCDataElement(export, "creationdatetime", SettingsService.getInstance().dateTimeFormat().format(new Date()));
		XmlUtils.addCDataElement(export, "generator-title", Constants.PROGRAM_NAME_FULL);
		XmlUtils.addElement(export, "generator-version", Constants.PROGRAM_VERSION);
		root.appendChild(export);
		for (Template template: templates) {
			Element templateElement = doc.createElement("template");
			XmlUtils.addCDataElement(templateElement, "title", template.getTitle());
			XmlUtils.addCDataElement(templateElement, "full-configuration", XmlUtils.cleanEolnForXml(template.getCombinedConfiguration().toString(null)));
			XmlUtils.addCDataElement(templateElement, "incremental-configuration", XmlUtils.cleanEolnForXml(template.getConfigurationString()));
			Element dosbox = doc.createElement("dosbox");
			XmlUtils.addCDataElement(dosbox, "title", template.getDosboxVersion().getTitle());
			XmlUtils.addElement(dosbox, "version", template.getDosboxVersion().getVersion());
			templateElement.appendChild(dosbox);
			root.appendChild(templateElement);
		}
		doc.appendChild(root);
		XmlUtils.saveDocument(doc, FileLocationService.getInstance().getDefaultTemplatesXmlFile(), null);
		return warningsLog.toString();
	}
}
