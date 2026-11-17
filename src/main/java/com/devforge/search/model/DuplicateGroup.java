package com.devforge.search.model;

import java.util.List;

/** Files with identical SHA-256 content. {@code locations} are formatted {@code project:path}. */
public record DuplicateGroup(String sha256, long size, List<String> locations) {}
