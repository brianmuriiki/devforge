package com.devforge.security.service;

import java.util.List;
import java.util.Locale;

/**
 * Flags licenses that deserve a human look. This is a screening aid, not legal advice: license
 * obligations depend on how a dependency is used, and a clean result does not guarantee compliance.
 */
public final class LicensePolicy {

    public static final String DISCLAIMER =
            "License information is informational and may be incomplete or inaccurate. "
                    + "It is not legal advice; consult your legal counsel for compliance decisions.";

    public record Review(boolean needsReview, String reason) {}

    private static final List<String> RESTRICTIVE =
            List.of("AGPL", "GPL", "SSPL", "EUPL", "CDDL", "EPL", "MPL", "COMMONS CLAUSE", "CC-BY-NC", "BUSL");

    private LicensePolicy() {}

    public static Review review(String license) {
        if (license == null || license.isBlank()) {
            return new Review(true, "license unknown");
        }
        String upper = license.toUpperCase(Locale.ROOT);
        if (upper.equals("UNKNOWN") || upper.contains("NOASSERTION")) {
            return new Review(true, "license unknown");
        }
        for (String key : RESTRICTIVE) {
            if (upper.contains(key)) {
                return new Review(true, "copyleft or restricted license; check its obligations");
            }
        }
        return new Review(false, "");
    }
}
