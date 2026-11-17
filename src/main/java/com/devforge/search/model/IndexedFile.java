package com.devforge.search.model;

import java.time.Instant;

public record IndexedFile(long id, String path, long size, String hash, boolean contentIndexed, Instant modifiedAt) {}
