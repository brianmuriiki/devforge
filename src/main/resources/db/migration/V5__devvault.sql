-- V5: DevVault backup profiles and registered backups.

CREATE TABLE vault_profiles (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK (length(trim(name)) > 0),
    description TEXT NOT NULL DEFAULT '',
    destination TEXT,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

CREATE TABLE vault_profile_paths (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    profile_id INTEGER NOT NULL REFERENCES vault_profiles (id) ON DELETE CASCADE,
    kind       TEXT NOT NULL CHECK (kind IN ('INCLUDE', 'EXCLUDE')),
    pattern    TEXT NOT NULL CHECK (length(trim(pattern)) > 0),
    UNIQUE (profile_id, kind, pattern)
);

CREATE TABLE vault_backups (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    profile_id         INTEGER REFERENCES vault_profiles (id) ON DELETE SET NULL,
    name               TEXT NOT NULL,
    archive_path       TEXT NOT NULL,
    manifest_path      TEXT NOT NULL,
    checksum           TEXT NOT NULL,
    created_at         TEXT NOT NULL,
    verified           INTEGER NOT NULL DEFAULT 0 CHECK (verified IN (0, 1)),
    verified_at        TEXT,
    size_bytes         INTEGER NOT NULL CHECK (size_bytes >= 0),
    file_count         INTEGER NOT NULL CHECK (file_count >= 0),
    app_version        TEXT NOT NULL,
    includes_sensitive INTEGER NOT NULL DEFAULT 0 CHECK (includes_sensitive IN (0, 1))
);

CREATE INDEX idx_vault_backups_profile ON vault_backups (profile_id);
CREATE INDEX idx_vault_backups_created ON vault_backups (created_at);
