package com.devforge.search.model;

import java.time.Instant;

/** @param lastIndexedAt null if the project has never been indexed */
public record IndexStatus(long projectId, String project, int files, int contentIndexed, long totalBytes, Instant lastIndexedAt) {}
