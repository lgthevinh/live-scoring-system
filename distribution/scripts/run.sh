#!/usr/bin/env bash

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_OPTS=${JAVA_OPTS:-""}
# You can pass system properties here if needed, e.g.
# JAVA_OPTS="$JAVA_OPTS -Dmatchmaker.bin=bin/MatchMaker.exe -Dmatchmaker.out=data/match_schedule.txt"

# Prefer the bundled JRE (Linux: jre/bin, macOS: jre/*/Contents/Home/bin), then JAVA_HOME, then PATH
JAVA_BIN=java
for candidate in "$DIR"/jre/bin/java "$DIR"/jre/*/Contents/Home/bin/java "${JAVA_HOME:-}/bin/java"; do
  if [[ -x "$candidate" ]]; then JAVA_BIN="$candidate"; break; fi
done

exec "$JAVA_BIN" $JAVA_OPTS -jar "$DIR/app/scoring-system.jar"
