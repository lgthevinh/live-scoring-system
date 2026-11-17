#!/usr/bin/env bash

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_OPTS=${JAVA_OPTS:-""}
# You can pass system properties here if needed, e.g.
# JAVA_OPTS="$JAVA_OPTS -Dmatchmaker.bin=bin/MatchMaker.exe -Dmatchmaker.out=data/match_schedule.txt"
exec java $JAVA_OPTS -jar "$DIR/app/scoring-system.jar"