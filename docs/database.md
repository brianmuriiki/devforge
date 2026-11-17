# Database

SQLite through the Xerial JDBC driver. Every connection sets `busy_timeout=5000`, `foreign_keys=ON` and
`journal_mode=WAL`, and verifies that foreign keys are on. Timestamps are UTC ISO-8601 text.

## Migrations
Numbered SQL files in `src/main/resources/db/migration/`, declared in `SchemaManager.DEFAULT_MIGRATIONS`. Pending migrations
run in one `BEGIN IMMEDIATE` transaction; a failure rolls everything back. A database created by a newer DevForge is refused.
Never edit an applied migration; add a new one and update the expected version in `DatabaseManagerTest` and `CliTest`.
The statement splitter ends statements at lines ending in `;` (no triggers).

## Schema (version 6)

| Migration | Tables |
|---|---|
| V1 core | `projects` (unique name NOCASE, unique root_path, status ACTIVE/ARCHIVED), `activity_logs` |
| V2 TaskForge | `tasks` (CHECKs on enums, date format, DONE ⇔ completed_at), `bugs` |
| V3 FindX | `file_index` (unique project+path, extension, size, SHA-256, content_indexed), FTS5 `file_contents(file_path, content)` keyed by `file_index.id` as rowid |
| V4 DepGuard | `dependency_scans`, `dependencies`, `vulnerabilities`, `advisory_cache`, `package_metadata_cache` |
| V5 DevVault | `vault_profiles`, `vault_profile_paths`, `vault_backups` |
| V6 encrypted backups | Records whether a DevVault archive uses authenticated encryption |

Foreign keys: tasks, bugs, file_index, scans and profile paths `ON DELETE CASCADE` from their parent; `activity_logs.project_id`
and `vault_backups.profile_id` use `SET NULL` (history survives). Removing a project never touches the filesystem.
FTS5 rows are not removed by cascades, so every index run purges orphaned rows.

Scan history is preserved: each scan inserts new rows; "latest" reports use the newest run (latest `scanned_at`, newest scan per manifest).
