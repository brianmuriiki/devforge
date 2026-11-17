# Command reference

All commands accept `--help`. Options like `--json` and `--verbose` go **after** the subcommand.
Enum values (`--priority high`, `--status done`) are case-insensitive. `--project` accepts an id or a name.

## Core
```
devforge --help | --version
devforge status [--json]
devforge init [dir] [--name N] [--description D] [--json]
devforge project list | show <id|name> [--json]
devforge project update <id|name> [--name N] [-d D]
devforge project archive|unarchive <id|name>
devforge project remove <id|name> [--yes]      # DevForge records only; files on disk are never touched
```

## TaskForge
```
devforge task add <title> [-p project] [-d text] [--priority low|medium|high|critical] [--due YYYY-MM-DD]
devforge task list [-p project] [--status S] [--priority P] [--sort id|due]
devforge task show <id>
devforge task update <id> [--title T] [-d D] [--priority P] [--status S] [--due DATE | --clear-due]
devforge task complete <id> | reopen <id> | delete <id> [--yes]
devforge task stats [-p project]
devforge bug add <title> [-p project] [-d text] [--severity S] [--steps TEXT]
devforge bug list [-p project] [--status S] [--severity S]
devforge bug show <id>
devforge bug update <id> [--title T] [-d D] [--severity S] [--status S] [--steps TEXT]
```
Without `-p`, `task add` and `bug add` use the registered project containing the current directory.
Task statuses: TODO, IN_PROGRESS, BLOCKED, DONE. Bug statuses: OPEN, IN_PROGRESS, RESOLVED, CLOSED.
Archived projects reject new tasks and bugs until unarchived.

## FindX
```
devforge search <query> [-p project] [-e ext] [--limit N] [--json]
devforge search index [dir] [-p project]        # incremental; indexes the whole registered project containing dir
devforge search rebuild [dir] [-p project]      # discard and rebuild (full reconciliation)
devforge search status [-p project]
devforge search duplicates [-p project]         # report only; never deletes
devforge search watch [dir] [-p project] [--seconds N]
```
Every word of the query must match (case-insensitive, whole tokens); file paths are matched too.
Exit code 1 when nothing matches. To search for a word that equals a subcommand name, put it after `--`:
`devforge search -- index`.

## DepGuard
```
devforge security scan [dir] [-p project] [--offline] [--json]
devforge security audit [-p project] [--json]        # findings from the latest stored scan (no network)
devforge security outdated [-p project] [--json]
devforge security licenses [-p project] [--review-only] [--json]
devforge security report [-p project] [--json]
```
Supported manifests: `pom.xml`, `package.json` (+ `package-lock.json`), `requirements.txt`, `pyproject.toml`.
Scan status per manifest: `COMPLETE`, `COMPLETE_WITH_FINDINGS`, `INCOMPLETE` (unresolved versions or missing data),
`UNAVAILABLE` (no advisory data at all). Exit code 3 = findings, 4 = incomplete/unavailable.

## DevVault
```
devforge env profile list | show <name> | delete <name> [--yes]
devforge env profile create <name> [--include PATH]... [--exclude GLOB]... [-d text] [--destination DIR]
devforge env backup [-p profile] [--include-sensitive] [--dry-run] [--yes]
devforge env backups
devforge env verify [id] [--archive FILE] [--checksum SHA256]
devforge env restore [id] [--archive FILE] [--checksum SHA256] [--target-home DIR]
                     [--dry-run] [--skip-conflicts] [--yes]
```
Paths must be inside your home directory (`~` works). Without `--include`, a profile defaults to common Git, shell and editor
files. To restore on another computer, copy the `.zip` and its `.zip.sha256` file, then run
`devforge env restore --archive backup.zip`.

## Combined
```
devforge report [-p project] [--json]            # from stored data
devforge analyze [dir] [--offline] [--json]      # register if needed, index, scan, then report
```

## Configuration (`config.json`)
```json
{ "logLevel": "INFO",
  "search":   { "maxFileSizeBytes": 1048576, "ignorePaths": ["*.log", "tmp/"] },
  "security": { "advisoryCacheHours": 24, "httpTimeoutSeconds": 10 } }
```
Precedence: `DEVFORGE_LOG_LEVEL` environment variable, then the file, then defaults.
