-- V2: TaskForge tables.

CREATE TABLE tasks (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id   INTEGER NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    title        TEXT NOT NULL CHECK (length(trim(title)) > 0),
    description  TEXT NOT NULL DEFAULT '',
    status       TEXT NOT NULL DEFAULT 'TODO'
                 CHECK (status IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE')),
    priority     TEXT NOT NULL DEFAULT 'MEDIUM'
                 CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    due_date     TEXT
                 CHECK (due_date IS NULL
                        OR due_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
    created_at   TEXT NOT NULL,
    updated_at   TEXT NOT NULL,
    completed_at TEXT,
    CHECK ((status = 'DONE') = (completed_at IS NOT NULL))
);

CREATE INDEX idx_tasks_project_status ON tasks (project_id, status);
CREATE INDEX idx_tasks_due_date ON tasks (due_date);

CREATE TABLE bugs (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id         INTEGER NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    title              TEXT NOT NULL CHECK (length(trim(title)) > 0),
    description        TEXT NOT NULL DEFAULT '',
    severity           TEXT NOT NULL DEFAULT 'MEDIUM'
                       CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status             TEXT NOT NULL DEFAULT 'OPEN'
                       CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    reproduction_steps TEXT NOT NULL DEFAULT '',
    created_at         TEXT NOT NULL,
    updated_at         TEXT NOT NULL
);

CREATE INDEX idx_bugs_project_status ON bugs (project_id, status);
