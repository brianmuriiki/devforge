package com.devforge.security.parse;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Reads the first declared license name from a published POM. */
public final class PomLicenseReader {

    private PomLicenseReader() {}

    /** @return the first {@code <licenses><license><name>} text, or null if none is declared */
    public static String firstLicense(String pomXml) throws ManifestException {
        Document doc = XmlUtil.parseString(pomXml, "pom");
        Element licenses = XmlUtil.child(doc.getDocumentElement(), "licenses");
        for (Element license : XmlUtil.children(licenses, "license")) {
            String name = XmlUtil.text(XmlUtil.child(license, "name"));
            if (name != null) {
                return name;
            }
        }
        return null;
    }
}
