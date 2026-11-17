# Architecture

One installable Java application (modular monolith). No microservices, web UI or REST API.

```
CLI (Picocli) -> Services -> Repositories -> SQLite          Engines (filesystem/network, no database)
                    |                                          scan, parse, provider, engine packages
              Core: AppContext, ProjectRegistry, AppPaths, Configuration, Transactions
```

`devforge report` / `analyze` live in `com.devforge.integration`, above the modules, and compose their services.

## Packages

| Package | Responsibility |
|---|---|
| `com.devforge.cli` | Command factory, shared options, JSON/table/terminal helpers, confirmation |
| `com.devforge.core` (`.paths .db .project .config .hash .files`) | App context, per-OS paths, SQLite + migrations, project registry, configuration, SHA-256, secret-name rules |
| `com.devforge.tasks` (`model repo service cli`) | TaskForge |
| `com.devforge.search` (`model scan repo service cli`) | FindX. `scan` = filesystem engine (ignore rules, scanner, text reader, watcher) |
| `com.devforge.security` (`model parse provider repo service cli`) | DepGuard. `parse` = manifest parsers; `provider` = OSV and registry clients |
| `com.devforge.environment` (`model engine repo service cli`) | DevVault. `engine` = selector, backup, verifier, restore |
| `com.devforge.integration` | Combined report and analyze |

## Rules (enforced by `ArchitectureTest`)
- Modules never depend on each other or on `integration`; `core` never depends on a module, `cli` or `integration`.
- Models never touch `java.sql`. Model, repository, service and engine code never depends on Picocli or CLI classes.
- No web stack dependencies anywhere.

## Conventions
- Services hold rules and transactions; commands parse input and print. Engines are database-free so they are easy to test.
- Results to stdout, errors to stderr (`Error: ...`), stack traces only with `--verbose`.
- Expected failures extend `DevForgeException` (exit 1). Picocli reports argument errors (exit 2).
- Every `--json` document starts with `"schemaVersion"`.
- User-controlled and network-supplied text is sanitized before terminal output (`TerminalText`).
- Time (`Clock`), home directory and prompting are injected, so tests never touch real user data.
- External data is validated at the boundary (OSV records, manifests inside archives, configuration).
