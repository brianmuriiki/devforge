package com.devforge.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import picocli.CommandLine.IVersionProvider;

/** Supplies the version from the Maven-filtered devforge.properties resource. */
public class VersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[] {"DevForge " + appVersion()};
    }

    /** The application version, or "unknown" if the resource is unavailable. */
    public static String appVersion() {
        Properties props = new Properties();
        try (InputStream in = VersionProvider.class.getResourceAsStream("/devforge.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            return "unknown";
        }
        return props.getProperty("version", "unknown");
    }
}
