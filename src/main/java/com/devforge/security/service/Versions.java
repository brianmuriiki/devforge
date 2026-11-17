package com.devforge.security.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tolerant version comparison for Maven, npm and PyPI style versions. Numbers compare numerically;
 * pre-release words (alpha &lt; beta &lt; milestone &lt; rc &lt; snapshot) sort before the plain release.
 * It is used only for "is a newer version available" hints, never for vulnerability matching.
 */
public final class Versions {

    private static final Pattern TOKEN = Pattern.compile("\\d+|[A-Za-z]+");
    private static final int RELEASE = 6;

    private Versions() {}

    public static int compare(String a, String b) {
        List<String> x = tokens(a);
        List<String> y = tokens(b);
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            String p = i < x.size() ? x.get(i) : null;
            String q = i < y.size() ? y.get(i) : null;
            int c;
            if (p == null) {
                c = -absent(q);
            } else if (q == null) {
                c = absent(p);
            } else {
                c = compareTokens(p, q);
            }
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    /** True if {@code latest} is strictly newer than {@code current}. */
    public static boolean isNewer(String current, String latest) {
        return compare(current, latest) < 0;
    }

    /** Sign of (token vs. a missing token): positive means the token makes its version newer. */
    private static int absent(String token) {
        if (Character.isDigit(token.charAt(0))) {
            return new BigInteger(token).signum() == 0 ? 0 : 1;
        }
        return Integer.compare(RELEASE, rank(token)) * -1;
    }

    private static int compareTokens(String p, String q) {
        boolean pn = Character.isDigit(p.charAt(0));
        boolean qn = Character.isDigit(q.charAt(0));
        if (pn && qn) {
            return new BigInteger(p).compareTo(new BigInteger(q));
        }
        if (pn) {
            return 1;
        }
        if (qn) {
            return -1;
        }
        int c = Integer.compare(rank(p), rank(q));
        return c != 0 ? c : p.compareTo(q);
    }

    private static List<String> tokens(String version) {
        String v = version.strip().toLowerCase(Locale.ROOT).replaceFirst("^v(?=\\d)", "");
        List<String> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(v);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    private static int rank(String qualifier) {
        return switch (qualifier) {
            case "dev" -> 0;
            case "alpha", "a" -> 1;
            case "beta", "b" -> 2;
            case "milestone", "m" -> 3;
            case "rc", "cr", "pre", "preview" -> 4;
            case "snapshot" -> 5;
            case "final", "ga", "release" -> RELEASE;
            case "sp", "post", "patch" -> 7;
            default -> 3;
        };
    }
}
