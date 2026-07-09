#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

mkdir -p config data/default logs runtime/multipart-tmp

JAR_NAME="$(ls file-box-*.jar 2>/dev/null | head -n 1)"
LOG_FILE="logs/out.log"

if [ -z "$JAR_NAME" ]; then
    echo "Error: no file-box-*.jar found in $SCRIPT_DIR"
    exit 1
fi

JAVA_OPTS="${JAVA_OPTS:--Xmx384m -Xms128m}"

nohup java $JAVA_OPTS -jar "$JAR_NAME" "$@" > "$LOG_FILE" 2>&1 &
PID=$!

echo "File Box started."
echo "Jar: $JAR_NAME"
echo "Log: $LOG_FILE"
echo "PID: $PID"
