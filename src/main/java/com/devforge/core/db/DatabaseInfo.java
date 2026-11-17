package com.devforge.core.db;

import java.nio.file.Path;

/** Snapshot of the database's runtime state, used by diagnostics such as {@code devforge status}. */
public record DatabaseInfo(
        Path path,
        String sqliteVersion,
        String journalMode,
        boolean foreignKeysEnabled,
        int schemaVersion) {}
