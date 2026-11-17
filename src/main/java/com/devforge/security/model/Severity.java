package com.devforge.security.model;

import java.util.Locale;

public enum Severity {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** Maps advisory wording (including GitHub's {@code MODERATE}) to a severity. */
    public static Severity parse(String text) {
        if (text == null) {
            return UNKNOWN;
        }
        return switch (text.strip().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> CRITICAL;
            case "HIGH" -> HIGH;
            case "MODERATE", "MEDIUM" -> MEDIUM;
            case "LOW" -> LOW;
            default -> UNKNOWN;
        };
    }
}
