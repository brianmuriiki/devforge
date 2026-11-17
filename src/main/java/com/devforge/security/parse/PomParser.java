package com.devforge.security.parse;

import com.devforge.security.model.Dependency;
import com.devforge.security.model.Ecosystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Reads direct dependencies from a Maven {@code pom.xml}. Never runs Maven. {@code ${property}}
 * references are resolved from the POM's own {@code <properties>} and project coordinates; versions
 * inherited from a parent POM or BOM are not resolvable here, so such dependencies stay unresolved.
 */
public final class PomParser {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private PomParser() {}

    public static List<Dependency> parse(Path pom) throws ManifestException {
        Document doc = XmlUtil.parse(pom);
        Element project = doc.getDocumentElement();
        Map<String, String> props = properties(project);

        List<Dependency> result = new ArrayList<>();
        Element dependencies = XmlUtil.child(project, "dependencies");
        for (Element dep : XmlUtil.children(dependencies, "dependency")) {
            String group = resolve(XmlUtil.text(XmlUtil.child(dep, "groupId")), props);
            String artifact = resolve(XmlUtil.text(XmlUtil.child(dep, "artifactId")), props);
            if (artifact == null) {
                continue;
            }
            String rawVersion = XmlUtil.text(XmlUtil.child(dep, "version"));
            String version = resolve(rawVersion, props);
            String scope = XmlUtil.text(XmlUtil.child(dep, "scope"));
            String resolved = version != null && isExact(version) ? version : null;
            result.add(new Dependency(
                    (group == null ? "" : group) + ":" + artifact,
                    rawVersion == null ? "" : rawVersion,
                    resolved,
                    Ecosystem.MAVEN,
                    true,
                    scope == null ? "compile" : scope));
        }
        return result;
    }

    static boolean isExact(String version) {
        return !version.isBlank() && !version.contains("[") && !version.contains("(")
                && !version.contains(",") && !version.contains("${");
    }

    private static Map<String, String> properties(Element project) {
        Map<String, String> props = new HashMap<>();
        Element properties = XmlUtil.child(project, "properties");
        if (properties != null) {
            for (Node n = properties.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e) {
                    String value = XmlUtil.text(e);
                    if (value != null) {
                        props.put(e.getTagName(), value);
                    }
                }
            }
        }
        Element parent = XmlUtil.child(project, "parent");
        String version = firstNonNull(XmlUtil.text(XmlUtil.child(project, "version")),
                XmlUtil.text(XmlUtil.child(parent, "version")));
        String group = firstNonNull(XmlUtil.text(XmlUtil.child(project, "groupId")),
                XmlUtil.text(XmlUtil.child(parent, "groupId")));
        if (version != null) {
            props.put("project.version", version);
            props.put("version", version);
        }
        if (group != null) {
            props.put("project.groupId", group);
            props.put("groupId", group);
        }
        return props;
    }

    /** Substitutes properties (up to a few nested levels); returns null if any placeholder remains. */
    static String resolve(String value, Map<String, String> props) {
        if (value == null) {
            return null;
        }
        String current = value;
        for (int pass = 0; pass < 5 && current.contains("${"); pass++) {
            Matcher m = PLACEHOLDER.matcher(current);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String replacement = props.get(m.group(1));
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement == null ? m.group() : replacement));
            }
            m.appendTail(sb);
            current = sb.toString();
        }
        return current.contains("${") ? null : current;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
