package com.devforge.security.model;

import java.time.Instant;

/** One stored scan of one manifest. */
public record ScanRecord(
        long id, long projectId, Ecosystem ecosystem, String manifestPath, Instant scannedAt,
        ScanStatus status, String message, boolean offline) {}
