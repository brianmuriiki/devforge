#!/usr/bin/env bash
# Throwaway end-to-end demo of every module. Usage: scripts/demo.sh  (after `mvn clean verify`)
set -euo pipefail
JAR="$(ls "$(cd "$(dirname "$0")/.." && pwd)"/target/devforge-*.jar | grep -v original | head -n1)"
WORK="$(mktemp -d)"
export DEVFORGE_HOME="$WORK/devforge"
HOME_DIR="$WORK/home"; mkdir -p "$HOME_DIR"
DEMO="$WORK/demo-app"; mkdir -p "$DEMO/src"; cd "$DEMO"
echo 'class Auth { void authentication() {} }' > src/Auth.java
echo '{ "dependencies": { "left-pad": "1.3.0" } }' > package.json
echo '[user] name = Demo' > "$HOME_DIR/.gitconfig"
df() { echo; echo "\$ devforge $*"; java -Duser.home="$HOME_DIR" -jar "$JAR" "$@" || echo "(exit code $?)"; }

df init .
df task add "Build authentication" --priority high --due 2026-12-01
df task list
df task complete 1
df task stats
df bug add "Login fails after token expiry" --severity high
df search index .
df search authentication
df search duplicates
df security scan . --offline          # offline + empty cache: honestly reported as UNAVAILABLE, exit 4
df env profile create demo --include '~/.gitconfig'
df env backup
df env verify
df env restore --target-home "$WORK/restored" --yes
df report
echo; echo "Demo data in $WORK (safe to delete)"
