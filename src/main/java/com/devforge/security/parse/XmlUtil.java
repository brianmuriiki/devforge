package com.devforge.security.parse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Hardened XML parsing (no DTDs, no external entities) plus small DOM helpers. */
final class XmlUtil {

    static final long MAX_BYTES = 5L * 1024 * 1024;

    private XmlUtil() {}

    static Document parse(Path file) throws ManifestException {
        try {
            if (Files.size(file) > MAX_BYTES) {
                throw new ManifestException(file.getFileName() + " is too large to parse.");
            }
            try (InputStream in = Files.newInputStream(file)) {
                return parse(in, file.getFileName().toString());
            }
        } catch (IOException e) {
            throw new ManifestException("Could not read " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    static Document parseString(String xml, String label) throws ManifestException {
        return parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), label);
    }

    private static Document parse(InputStream in, String label) throws ManifestException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(in);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new ManifestException("Could not parse " + label + " as XML.", e);
        }
    }

    /** First direct child element with the given tag, or null. */
    static Element child(Element parent, String tag) {
        if (parent == null) {
            return null;
        }
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) {
                return e;
            }
        }
        return null;
    }

    static java.util.List<Element> children(Element parent, String tag) {
        java.util.List<Element> out = new java.util.ArrayList<>();
        if (parent != null) {
            for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e && e.getTagName().equals(tag)) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    static String text(Element e) {
        if (e == null) {
            return null;
        }
        String t = e.getTextContent();
        return t == null || t.isBlank() ? null : t.strip();
    }
}
