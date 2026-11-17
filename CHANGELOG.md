# Changelog

## 0.1.0 (unreleased)

First feature-complete version of all four modules and the integration layer.

### Core
- Java 21 / Maven / Picocli CLI with a consistent output model: results on stdout, errors on stderr,
  `--json` everywhere with a versioned `schemaVersion`, no stack traces without `--verbose`.
- Per-OS application directories with a `DEVFORGE_HOME` override; `config.json` with validation.
- SQLite with WAL, foreign keys and busy timeout on every connection; versioned, transactional migrations (V1-V5).
- Project registry with canonical paths (symlink-safe duplicate detection).
- Persistent log file; configurable log level.

### TaskForge
- Projects (create/list/show/update/archive/unarchive/remove), tasks, bugs, filters, due-date sorting.
- Progress statistics (`task stats`): totals, completion, overdue, blocked, priority distribution, bug counts.
- Confirmation guard for destructive commands (`--yes`; fails safe without a terminal).

### FindX
- Incremental file index with SQLite FTS5 content search and filename search, extension/project filters, snippets.
- Exclusions: VCS/build/dependency directories, binaries, oversized files, secret files, `.gitignore`, configured patterns.
- SHA-256 duplicate detection (size grouping, then hashing); files are never deleted.
- `WatchService` monitoring with overflow handling and periodic reconciliation; full rebuild.

### DepGuard
- Maven, npm (package.json + package-lock.json) and Python (requirements.txt, pyproject.toml incl. Poetry) manifests.
- OSV.dev advisories (normalized and validated), registry metadata (latest version, license), offline cache.
- Honest status model: COMPLETE / COMPLETE_WITH_FINDINGS / INCOMPLETE / UNAVAILABLE; never "secure" without data.
- Exit codes: 3 = findings, 4 = incomplete or unavailable.

### DevVault
- Backup profiles, secret-aware file selection, ZIP archives with SHA-256 manifest and sidecar checksum.
- Verification without extraction; restore with dry-run, conflict detection, recovery copies, path-traversal
  and symlink protection, per-file hash verification. Restore from archives copied to another computer.

### Integration and release
- `devforge report` and `devforge analyze` combine all modules.
- jpackage scripts for native installers; CI on Linux/Windows/macOS; release workflow.
