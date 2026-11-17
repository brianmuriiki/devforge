-- V4: DepGuard dependency scans, findings and caches.

CREATE TABLE dependency_scans (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id    INTEGER NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    ecosystem     TEXT NOT NULL CHECK (ecosystem IN ('MAVEN', 'NPM', 'PYPI')),
    manifest_path TEXT NOT NULL,
    scanned_at    TEXT NOT NULL,
    scan_status   TEXT NOT NULL
                  CHECK (scan_status IN ('COMPLETE', 'COMPLETE_WITH_FINDINGS', 'INCOMPLETE', 'UNAVAILABLE')),
    message       TEXT NOT NULL DEFAULT '',
    offline       INTEGER NOT NULL DEFAULT 0 CHECK (offline IN (0, 1))
);

CREATE INDEX idx_dependency_scans_project ON dependency_scans (project_id, manifest_path);

CREATE TABLE dependencies (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_id          INTEGER NOT NULL REFERENCES dependency_scans (id) ON DELETE CASCADE,
    name             TEXT NOT NULL,
    declared_version TEXT NOT NULL DEFAULT '',
    resolved_version TEXT,
    direct           INTEGER NOT NULL DEFAULT 1 CHECK (direct IN (0, 1)),
    scope            TEXT NOT NULL DEFAULT '',
    check_status     TEXT NOT NULL CHECK (check_status IN ('CHECKED', 'UNRESOLVED', 'UNAVAILABLE')),
    latest_version   TEXT,
    license          TEXT,
    metadata_status  TEXT NOT NULL DEFAULT 'UNAVAILABLE' CHECK (metadata_status IN ('OK', 'UNAVAILABLE'))
);

CREATE INDEX idx_dependencies_scan ON dependencies (scan_id);

CREATE TABLE vulnerabilities (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_id           INTEGER NOT NULL REFERENCES dependency_scans (id) ON DELETE CASCADE,
    package_name      TEXT NOT NULL,
    installed_version TEXT NOT NULL,
    affected_range    TEXT NOT NULL DEFAULT '',
    severity          TEXT NOT NULL CHECK (severity IN ('UNKNOWN', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    advisory_id       TEXT NOT NULL,
    fixed_versions    TEXT NOT NULL DEFAULT '',
    description       TEXT NOT NULL DEFAULT '',
    reference_urls    TEXT NOT NULL DEFAULT '',
    source            TEXT NOT NULL,
    from_cache        INTEGER NOT NULL DEFAULT 0 CHECK (from_cache IN (0, 1))
);

CREATE INDEX idx_vulnerabilities_scan ON vulnerabilities (scan_id);

CREATE TABLE advisory_cache (
    ecosystem       TEXT NOT NULL,
    package_name    TEXT NOT NULL,
    version         TEXT NOT NULL,
    advisories_json TEXT NOT NULL,
    fetched_at      TEXT NOT NULL,
    PRIMARY KEY (ecosystem, package_name, version)
);

CREATE TABLE package_metadata_cache (
    ecosystem      TEXT NOT NULL,
    package_name   TEXT NOT NULL,
    version        TEXT NOT NULL,
    latest_version TEXT,
    license        TEXT,
    fetched_at     TEXT NOT NULL,
    PRIMARY KEY (ecosystem, package_name, version)
);
