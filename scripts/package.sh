#!/usr/bin/env bash
# Builds a native DevForge package with jpackage (bundles a Java runtime; users need no JDK).
# Usage: scripts/package.sh [app-image|deb|rpm|dmg|pkg]    (default: app-image)
# Run on the target OS: jpackage cannot cross-compile. Requires JDK 21+ and Maven.
set -euo pipefail
cd "$(dirname "$0")/.."

TYPE="${1:-app-image}"
FULL_VERSION="$(grep -m1 '<version>' pom.xml | sed -E 's/.*<version>([^<]+)<.*/\1/')"
APP_VERSION="$(echo "$FULL_VERSION" | sed -E 's/-.*//')"   # jpackage needs plain numeric versions
JAR="devforge-${FULL_VERSION}.jar"

mvn -B clean verify
rm -rf dist && mkdir -p dist

# Modules the shaded jar needs at runtime: JDBC (java.sql), XML parsing, HTTPS client + TLS ciphers
# (jdk.crypto.ec), logging/JNDI used by Logback, and sun.misc for the SQLite native loader.
MODULES="java.base,java.sql,java.xml,java.net.http,java.logging,java.naming,java.management,jdk.crypto.ec,jdk.unsupported"

jpackage \
  --type "$TYPE" \
  --name devforge \
  --app-version "$APP_VERSION" \
  --vendor "DevForge" \
  --description "Developer productivity and project management CLI" \
  --input target \
  --main-jar "$JAR" \
  --main-class com.devforge.Main \
  --add-modules "$MODULES" \
  --java-options "-Dfile.encoding=UTF-8" \
  --dest dist

echo "Package written to dist/"
ls -la dist
