package com.devforge.search.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.devforge.search.SearchException;
import org.junit.jupiter.api.Test;

class FtsQueryTest {

    @Test
    void everyWordBecomesAQuotedTerm() {
        assertEquals("\"database\" \"connection\"", FtsQuery.build("  database   connection "));
        assertEquals("\"JWT\"", FtsQuery.build("JWT"));
    }

    @Test
    void embeddedQuotesAreDoubledSoOperatorsCannotBeInjected() {
        assertEquals("\"a\"\"b\"", FtsQuery.build("a\"b"));
        assertEquals("\"NOT\" \"x\" \"OR\" \"y*\"", FtsQuery.build("NOT x OR y*"));
    }

    @Test
    void blankQueryIsRejected() {
        assertThrows(SearchException.class, () -> FtsQuery.build("   "));
        assertThrows(SearchException.class, () -> FtsQuery.build(null));
    }
}
