package com.devforge.search.service;

import com.devforge.search.SearchException;
import java.util.ArrayList;
import java.util.List;

/** Turns user text into a safe FTS5 query: every word becomes a quoted term, all must match. */
public final class FtsQuery {

    private FtsQuery() {}

    public static String build(String text) {
        List<String> terms = new ArrayList<>();
        if (text != null) {
            for (String word : text.strip().split("\\s+")) {
                if (!word.isEmpty()) {
                    terms.add("\"" + word.replace("\"", "\"\"") + "\"");
                }
            }
        }
        if (terms.isEmpty()) {
            throw new SearchException("Search query must not be empty.");
        }
        return String.join(" ", terms);
    }
}
