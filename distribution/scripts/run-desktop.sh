#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# Prefer the bundled JRE (Linux: jre/bin, macOS: jre/*/Contents/Home/bin), then JAVA_HOME, then PATH
JAVA_BIN=java
for candidate in "$DIR"/jre/bin/java "$DIR"/jre/*/Contents/Home/bin/java "${JAVA_HOME:-}/bin/java"; do
  if [[ -x "$candidate" ]]; then JAVA_BIN="$candidate"; break; fi
done

echo "Using JAVA: $JAVA_BIN"
exec "$JAVA_BIN" -cp "app/scoring-system.jar" org.thingai.app.desktop.DesktopLauncher
