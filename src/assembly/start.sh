#!/bin/bash

# 获取脚本所在目录的绝对路径
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 进入该目录
cd "$SCRIPT_DIR" || exit
echo "当前目录已切换至: $(pwd)"

# Linux启动脚本
JAR_NAME="file-box-0.1.2.jar"
LOG_FILE="out.log"

# 检查jar文件是否存在
if [ ! -f "$JAR_NAME" ]; then
    echo "错误：找不到$JAR_NAME文件"
    exit 1
fi

# JVM 内存限制（防止多服务同跑时 OOM）
JAVA_OPTS="-Xmx384m -Xms128m"

# 启动应用
nohup java $JAVA_OPTS -jar "$JAR_NAME" > "$LOG_FILE" 2>&1 &

echo "应用已启动，日志输出到$LOG_FILE"
echo "进程ID：$!"