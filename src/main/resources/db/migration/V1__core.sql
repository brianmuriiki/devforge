-- V1: core tables shared by every module.

CREATE TABLE projects (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK (length(trim(name)) > 0),
    root_path   TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL DEFAULT '',
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

CREATE TABLE activity_logs (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER REFERENCES projects (id) ON DELETE SET NULL,
    module     TEXT NOT NULL,
    action     TEXT NOT NULL,
    details    TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_activity_logs_project ON activity_logs (project_id);
CREATE INDEX idx_activity_logs_created ON activity_logs (created_at);
