package com.devforge.search.model;

/** Result of an indexing run. {@code contentIndexed} counts files whose text is searchable now. */
public record IndexSummary(int added, int updated, int removed, int unchanged, int contentIndexed, int unreadable) {}
