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
package org.dbgl.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


public class XmlUtils {

	private static final Pattern whitespaceSurroundingCDataPattern = Pattern.compile("\\>(\\s*)(\\<\\!\\[CDATA\\[)(.*?)(\\]\\]\\>)(\\s*)\\<", Pattern.DOTALL);

	private XmlUtils() {
	}

	public static DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
		DocumentBuilderFactory df = DocumentBuilderFactory.newInstance();
		df.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
		df.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
		return df.newDocumentBuilder();
	}

	public static Element getNode(Element element, String tagName) {
		if (element == null || tagName == null)
			return null;
		return (Element)element.getElementsByTagName(tagName).item(0);
	}

	public static String getTextValue(Element element, String tagName) {
		Node n = getNode(element, tagName);
		if (n != null) {
			Node child = n.getFirstChild();
			return child == null ? StringUtils.EMPTY: child.getNodeValue();
		}
		return null;
	}

	public static String getNestedTextValue(Element element, String tagName1, String tagName2) {
		return getTextValue(getNode(element, tagName1), tagName2);
	}

	public static Element addElement(Element el, String name, String value) {
		if (el == null || name == null || value == null)
			throw new RuntimeException("null is an invalid node value");

		Element newElement = el.getOwnerDocument().createElement(name);
		newElement.appendChild(el.getOwnerDocument().createTextNode(value));
		el.appendChild(newElement);
		return el;
	}

	public static Element addCDataElement(Element el, String name, String value) {
		if (el == null || name == null || value == null)
			throw new RuntimeException("null is an invalid node value");

		Element newElement = el.getOwnerDocument().createElement(name);
		newElement.appendChild(el.getOwnerDocument().createCDATASection(value));
		el.appendChild(newElement);
		return el;
	}

	public static Element addNestedElement(Element el, String tagName1, String tagName2, String value) {
		if (el == null || tagName1 == null || tagName2 == null || value == null)
			throw new RuntimeException("null is an invalid node value");

		el.appendChild(addElement(el.getOwnerDocument().createElement(tagName1), tagName2, value));
		return el;
	}

	public static void saveDocument(Document doc, File target, File xslt) throws TransformerException, IOException {
		try (FileOutputStream fos = new FileOutputStream(target)) {
			fos.write(docToString(doc, xslt).getBytes(StandardCharsets.UTF_8));
		}
	}

	public static void saveDocumentToZipOutputStream(Document doc, File zipFileEntry, ZipOutputStream zipOutputStream) throws IOException, TransformerException {
		zipOutputStream.putNextEntry(new ZipEntry(FilesUtils.toArchivePath(zipFileEntry, false)));
		zipOutputStream.write(docToString(doc, null).getBytes(StandardCharsets.UTF_8));
		zipOutputStream.closeEntry();
	}

	private static String docToString(Document doc, File xslt) throws TransformerException {
		StringWriter stringWriter = new StringWriter();
		TransformerFactory fact = TransformerFactory.newInstance();
		fact.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
		fact.setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "");
		fact.setAttribute("indent-number", 2);
		Transformer trans = xslt == null ? fact.newTransformer(): fact.newTransformer(new StreamSource(xslt));
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.transform(new DOMSource(doc), new StreamResult(stringWriter));
		return whitespaceSurroundingCDataPattern.matcher(stringWriter.getBuffer()).replaceAll(">$2$3$4<");
	}

	public static String cleanEolnForXml(String s) {
		return s.replace("\r", "");
	}
}
