-- V3: FindX file index and full-text search.

CREATE TABLE file_index (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id      INTEGER NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    file_path       TEXT NOT NULL,
    extension       TEXT NOT NULL DEFAULT '',
    file_size       INTEGER NOT NULL CHECK (file_size >= 0),
    content_hash    TEXT,
    content_indexed INTEGER NOT NULL DEFAULT 0 CHECK (content_indexed IN (0, 1)),
    modified_at     TEXT NOT NULL,
    indexed_at      TEXT NOT NULL,
    UNIQUE (project_id, file_path)
);

CREATE INDEX idx_file_index_size ON file_index (file_size);
CREATE INDEX idx_file_index_hash ON file_index (content_hash);
CREATE INDEX idx_file_index_extension ON file_index (extension);

CREATE VIRTUAL TABLE file_contents USING fts5(file_path, content, tokenize = 'unicode61 remove_diacritics 2');
