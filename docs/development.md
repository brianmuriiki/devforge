# Development

```bash
./mvnw clean verify   # compile, run all tests, build the shaded jar
./mvnw test           # tests only
```

## Tests
- `@TempDir` and an injected `Clock` everywhere; tests never touch real user data or the network.
- Engines (scanner, parsers, providers with fake transports, backup/restore) are database-free unit tests.
- Services and CLI tests run against a real temporary SQLite database.
- `ArchitectureTest` (ArchUnit) enforces module boundaries.

## Adding a feature
1. Schema change: new numbered migration + entry in `SchemaManager.DEFAULT_MIGRATIONS`; update expected version in
   `DatabaseManagerTest` and `CliTest`.
2. Put rules in a service, SQL in a repository, filesystem/network logic in a database-free engine, parsing/printing in commands.
3. Register commands in `CommandFactory` (settings like case-insensitive enums must be applied after registration).
4. Add `--json` output with `schemaVersion`, tests at service and CLI level, and ArchUnit rules for new packages.

## Packaging and CI
`scripts/package.sh` / `scripts/package.ps1` (jpackage), `.github/workflows/ci.yml` (build, tests, and native-image smoke
tests on 3 OSes), `.github/workflows/release.yml` (signed packages on `v*` tags). See `releasing.md` for credentials and
verification. Tagged releases deliberately fail rather than publish an unsigned installer.
