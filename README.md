# DevForge

A cross-platform, CLI-only developer productivity and project management tool.
Java 21 · Maven · Picocli · SQLite · modular monolith. Everything runs locally; nothing is uploaded
(the only network use is DepGuard's opt-out advisory and registry lookups, which `--offline` disables).

| Module | What it does | Main commands |
|---|---|---|
| **TaskForge** | Projects, tasks, bugs, progress statistics | `project`, `task`, `bug` |
| **FindX** | Incremental file index, full-text search, duplicate detection, file watching | `search` |
| **DepGuard** | Dependency vulnerabilities, outdated packages, licenses | `security` |
| **DevVault** | Verified, secret-aware backups of dev configuration and safe restore | `env` |
| Integration | One combined view | `report`, `analyze` |

## Important: verification status

The full Maven test suite, including database-backed services and CLI tests, is run locally against its real dependencies.
The CI workflow is configured to repeat it on Linux, macOS, and Windows, then smoke-test the packaged native app image.
Before relying on a source checkout, run `./mvnw clean verify`; see `docs/known-limitations.md` for the remaining product
boundaries.

## Requirements

- JDK 21+ to build. The checked-in Maven Wrapper downloads Maven 3.9.9 automatically. Packaged releases (jpackage)
  bundle their own runtime.

## Build and run

```bash
./mvnw clean verify
java -jar target/devforge-0.1.0-SNAPSHOT.jar --help
scripts/demo.sh        # end-to-end demo with throwaway data (Linux/macOS)
```

## Quick tour

```bash
cd ~/code/my-app
devforge init .                                  # register the project
devforge task add "Build authentication" --priority high --due 2026-12-01
devforge task list
devforge task stats

devforge search index .                          # build the search index
devforge search "authentication"
devforge search "TODO" --extension java
devforge search duplicates

devforge security scan .                         # needs network; use --offline for cached data only
devforge security report

devforge env profile create java-development --include '~/.gitconfig' --include '~/.config/git'
devforge env backup
devforge env verify
devforge env restore --dry-run

devforge analyze .                               # register + index + scan + combined report
devforge report --json
```

Every command supports `--help`, and most support `--json` (stable output with a `schemaVersion`) and `--verbose`.
See `docs/commands.md` for the full reference.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Success |
| 1 | Failure (message on stderr; for `search`, also "no matches") |
| 2 | Invalid command or arguments |
| 3 | Security findings detected (`security scan/audit/report`, `analyze`, `report`) |
| 4 | Completed with warnings: incomplete/unavailable security result, backup with missing paths, restore with skipped conflicts |

When several apply: failure (1) > findings (3) > warnings (4) > success (0).

## Where data lives

| OS | Database | Config |
|---|---|---|
| Linux | `~/.local/share/devforge/devforge.db` | `~/.config/devforge/config.json` |
| macOS | `~/Library/Application Support/DevForge/devforge.db` | same directory |
| Windows | `%LOCALAPPDATA%\DevForge\devforge.db` | `%APPDATA%\DevForge\config.json` |

Set `DEVFORGE_HOME` (absolute path) to relocate everything. Backups default to `<data dir>/backups`. Logs go to the
log directory (`devforge.log`); `DEVFORGE_LOG_LEVEL` or `logLevel` in `config.json` sets the level.

## Safety guarantees

- No command deletes or modifies your source files. `project remove` deletes DevForge's own records only.
- Duplicates are reported, never deleted.
- Destructive commands need `--yes` or an interactive confirmation, and fail safely without a terminal.
- Backups exclude secrets (keys, tokens, `.env`, credential stores) unless explicitly requested with a warning;
  restores never overwrite silently, keep recovery copies, and reject path traversal and symbolic links.
- A dependency scan is never reported "secure" unless every dependency was actually checked.
- Dependency manifests are parsed, never executed (no Maven, npm or pip runs).

## Documentation

`docs/commands.md`, `docs/architecture.md`, `docs/database.md`, `docs/security.md`, `docs/development.md`,
`docs/known-limitations.md`, `docs/releasing.md`, `CHANGELOG.md`.

## Packaging

`scripts/package.sh [app-image|deb|rpm|dmg|pkg]` (Linux/macOS) and `scripts\package.ps1 -Type exe` (Windows) build native
packages with jpackage. Run them on the target OS. GitHub Actions workflows (`.github/workflows`) build, test, and smoke-test
native app images on all three systems. Tagged releases require configured macOS signing/notarization and Windows Authenticode
secrets; release artifacts include an OS-specific SHA-256 manifest.

## License

MIT (see `LICENSE`). The copyright holder is a placeholder; change it to yours.
