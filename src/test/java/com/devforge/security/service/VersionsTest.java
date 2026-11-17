package com.devforge.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionsTest {

    private static void assertOrder(String older, String newer) {
        assertTrue(Versions.compare(older, newer) < 0, older + " < " + newer);
        assertTrue(Versions.compare(newer, older) > 0, newer + " > " + older);
    }

    @Test
    void numericSegmentsCompareNumerically() {
        assertOrder("1.2.3", "1.2.10");
        assertOrder("1.9", "1.10");
        assertOrder("2.0.0", "10.0.0");
        assertOrder("1.0", "1.0.1");
    }

    @Test
    void trailingZerosAndCaseAreEqual() {
        assertEquals(0, Versions.compare("1.0", "1.0.0"));
        assertEquals(0, Versions.compare("1.0.0-FINAL", "1.0.0"));
        assertEquals(0, Versions.compare("v2.1.0", "2.1.0"));
    }

    @Test
    void preReleasesSortBeforeTheRelease() {
        assertOrder("1.0.0-alpha", "1.0.0-beta");
        assertOrder("1.0.0-beta", "1.0.0-rc1");
        assertOrder("1.0.0-rc1", "1.0.0");
        assertOrder("1.0.0-SNAPSHOT", "1.0.0");
        assertOrder("2.0-beta9", "2.15.0");
    }

    @Test
    void postReleasesSortAfterTheRelease() {
        assertOrder("1.0.0", "1.0.0-sp1");
    }

    @Test
    void isNewerOnlyForStrictlyHigherVersions() {
        assertTrue(Versions.isNewer("2.14.0", "2.17.1"));
        assertFalse(Versions.isNewer("2.17.1", "2.17.1"));
        assertFalse(Versions.isNewer("3.0", "2.9"));
    }

    @Test
    void hugeNumbersDoNotOverflow() {
        assertOrder("1.99999999999999999999", "1.100000000000000000000");
    }
}
