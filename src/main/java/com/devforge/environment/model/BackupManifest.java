package com.devforge.environment.model;

import java.time.Instant;
import java.util.List;

/** The inventory stored inside every backup archive as {@code manifest.json}. */
public record BackupManifest(
        int formatVersion, String appVersion, String profile, Instant createdAt, String os, List<ManifestEntry> entries) {

    public static final int CURRENT_FORMAT = 1;

    public long totalBytes() {
        long sum = 0;
        for (ManifestEntry e : entries) {
            sum += e.size();
        }
        return sum;
    }
}
