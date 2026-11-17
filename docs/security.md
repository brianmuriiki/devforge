# Security and privacy design

## Data handling
- All operations are local. No source code or file content is uploaded. DepGuard sends only package names and versions
  to osv.dev and the public registries (npm, PyPI, Maven Central); `--offline` disables all network use.
- Secrets are never indexed (FindX), never backed up by default (DevVault), and never written to the database or logs.
- SQL uses prepared statements; the only concatenated fragments are fixed literals.

## Filesystem safety
- Destructive operations require confirmation and never delete project files. Duplicates are never deleted.
- Symbolic links are never followed by FindX, DevVault selection or restore.
- Restore: archive contents are untrusted. Paths are validated (no absolute paths, `..`, backslashes, colons, control
  characters), resolved under the target home, rejected when any parent is a symlink, written via temp files, size-limited,
  hash-verified per file, and existing files are saved to `.devforge-recovery/<timestamp>/` before overwrite.
- Backups are written under a temporary name and moved into place atomically; existing archives are never overwritten.

## Network safety (DepGuard)
- HTTPS only, hard timeouts, bounded response sizes, circuit breaker after repeated failures.
- OSV records are validated and bounded (id pattern, text length, http(s) URLs only, list caps) before storage.
- XML (pom.xml) is parsed with DTDs and external entities disabled.
- Manifests are parsed, never executed. Dependencies are never upgraded or modified.
- Status honesty: INCOMPLETE and UNAVAILABLE scans are never reported as clean and produce exit code 4.

## Known gaps
Secret detection is name-based. Backups are not encrypted (planned: authenticated encryption via an established library,
never a custom algorithm). See `known-limitations.md`.
