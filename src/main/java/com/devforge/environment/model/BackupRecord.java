package com.devforge.environment.model;

import java.time.Instant;

/** A backup registered in the database. {@code profileId} and {@code profileName} are null if the profile was deleted. */
public record BackupRecord(
        long id, Long profileId, String profileName, String name, String archivePath, String manifestPath,
        String checksum, Instant createdAt, boolean verified, Instant verifiedAt, long sizeBytes, int fileCount,
        String appVersion, boolean includesSensitive, boolean encrypted) {}
