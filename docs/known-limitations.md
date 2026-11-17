# Known limitations

Honest list of what DevForge does not do (yet), and caveats about how it was verified.

## Verification status
- The code was type-checked and its dependency-free logic (parsers, normalization, scanning, backup/restore engine,
  version/license logic, configuration) was executed by the author against stand-ins for Picocli/Jackson/JUnit.
- **Not executed by the author:** anything needing the real Picocli, SQLite JDBC driver, Logback, ArchUnit or JUnit
  (database-backed services and CLI end-to-end tests), and everything on Windows and macOS.
  Run `mvn clean verify` on each OS before relying on it.
- jpackage scripts and release workflow are untested. macOS signing/notarization and Windows code signing are not included.

## FindX
- Full-text search matches whole tokens (letters/digits). `getUserName` is one token, so searching `user` will not find it;
  `user_id` and `user.id` are split. Prefix and substring search are not supported yet.
- Only the project's root `.gitignore` is read; re-including a file inside an ignored directory is not supported.
- `search watch` relies on OS events (polling on macOS, inotify limits on Linux); it reconciles with a full incremental
  index after bursts, on overflow and every five minutes.
- Search results from a project are only as fresh as the last index run.

## DepGuard
- Direct dependencies only; transitive dependencies are not resolved.
- Maven versions inherited from a parent POM or BOM stay "unresolved" (the tool never runs Maven).
- npm: `package-lock.json` only (no yarn/pnpm lockfiles). Python: exact `==` pins only; no Poetry/uv/pip lockfiles; `-r` includes are not followed.
- Severity comes from the advisory's `database_specific.severity`; CVSS vectors are not scored, so severity is often UNKNOWN.
- Version comparison for "outdated" is a tolerant heuristic, never used for vulnerability matching.
- License data is best-effort and not legal advice.

## DevVault
- Only files below the user's home directory are backed up or restored.
- Archives are ZIP, not encrypted (encryption is a planned future feature). Secret detection is name-based and best-effort.
- POSIX permissions are preserved where supported; Windows ACLs are not. Symbolic links are skipped, never archived.
- Limits: 200,000 files and 4 GiB per backup.

## General
- Logging is a plain file without rotation.
- Column alignment in tables uses string length (wide characters may misalign).
- Terminal output sanitizes control characters; JSON output is unmodified data.
