package com.devforge.search.model;

/** @param matchType {@code content} or {@code name} */
public record SearchHit(long projectId, String project, String path, String matchType, String snippet) {}
