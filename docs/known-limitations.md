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
- FindX searches whole tokens first, then safely falls back to substring matching for identifiers and paths. The fallback is
  necessarily slower than FTS on very large indexes.
- FindX reads nested `.gitignore` files and honors re-inclusions. It supports the most common Git patterns but does not
  emulate every Git edge case (for example, escaped leading `!` or `#` patterns).
- `search watch` relies on OS events (polling on macOS, inotify limits on Linux); it reconciles with a full incremental
  index after bursts, on overflow and every five minutes.
- Search results from a project are only as fresh as the last index run.

## DepGuard
- npm `package-lock.json` dependencies are scanned transitively. Maven and Python transitive dependencies still require
  their respective lockfiles or resolvers and are not inferred.
- Maven versions inherited from a parent POM or BOM stay "unresolved" (the tool never runs Maven).
- npm supports `package-lock.json`; yarn and pnpm lockfiles are not yet parsed. Python accepts exact `==` pins; Poetry,
  uv, and pip lockfiles are not yet parsed, and `-r` includes are not yet followed.
- Severity uses the advisory's `database_specific.severity`, with CVSS v3 base-vector scoring as a fallback. Advisories
  without either remain UNKNOWN.
- Version comparison for "outdated" is a tolerant heuristic, never used for vulnerability matching.
- License data is best-effort and not legal advice.

## DevVault
- Only files below the user's home directory are backed up or restored.
- New archives use AES-256-GCM encryption. Passphrase recovery is intentionally impossible, and secret detection remains
  name-based and best-effort.
- POSIX permissions are preserved where supported; Windows ACLs are not. Symbolic links are skipped, never archived.
- Limits: 200,000 files and 4 GiB per backup.

## General
- Logs rotate daily or at 10 MiB, retain 14 days, and are capped at 100 MiB total.
- Terminal tables account for combining and common wide Unicode characters; unusual grapheme clusters can still render
  differently between terminal emulators.
- Terminal output sanitizes control characters; JSON output is unmodified data.
