package com.devforge.security.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LicensePolicyTest {

    @Test
    void permissiveLicensesNeedNoReview() {
        for (String l : new String[] {"MIT", "Apache-2.0", "Apache License, Version 2.0", "BSD-3-Clause", "ISC"}) {
            assertFalse(LicensePolicy.review(l).needsReview(), l);
        }
    }

    @Test
    void copyleftAndRestrictedLicensesNeedReview() {
        for (String l : new String[] {"GPL-3.0", "AGPL-3.0-only", "LGPL-2.1", "MPL-2.0", "EPL-2.0", "SSPL-1.0", "CDDL-1.0"}) {
            assertTrue(LicensePolicy.review(l).needsReview(), l);
        }
    }

    @Test
    void unknownLicensesNeedReview() {
        assertTrue(LicensePolicy.review(null).needsReview());
        assertTrue(LicensePolicy.review("  ").needsReview());
        assertTrue(LicensePolicy.review("UNKNOWN").needsReview());
        assertTrue(LicensePolicy.review("NOASSERTION").needsReview());
    }

    @Test
    void disclaimerStatesItIsNotLegalAdvice() {
        assertTrue(LicensePolicy.DISCLAIMER.contains("not legal advice"));
    }
}
