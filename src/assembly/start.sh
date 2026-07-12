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

# 默认生产 profile:关闭控制台日志(只写 logs/filebox.log),避免 out.log 重复捕获整份日志。
# Default prod profile: console logging off (file only), so out.log doesn't duplicate the full log.
# 如需控制台输出(调试):SPRING_PROFILES_ACTIVE=dev ./start.sh
# For console output (debugging): SPRING_PROFILES_ACTIVE=dev ./start.sh
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

nohup java $JAVA_OPTS -jar "$JAR_NAME" "$@" > "$LOG_FILE" 2>&1 &
PID=$!

echo "File Box started."
echo "Jar: $JAR_NAME"
echo "Log: $LOG_FILE"
echo "PID: $PID"
