package com.devforge.search.model;

import java.util.List;

/** @param unreadable candidate files that could not be hashed (missing or unreadable) */
public record DuplicateReport(List<DuplicateGroup> groups, int unreadable, long wastedBytes) {}
