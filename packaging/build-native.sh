#!/bin/bash
#
# build-native.sh — 为 File Box 构建【自带 JRE 的解压即用】Linux 包(脚本式,非 jpackage)。
# Build a self-contained, extract-and-run Linux package (script-based, NOT jpackage).
#
# 产物 / Artifact (target/native/):
#   FileBox-<ver>-linux-jre.tar.gz
#     解压得 FileBox-<ver>-linux-jre/{file-box-<ver>.jar, jre/, start.sh, manage.sh, data/default/, README.txt}
#
# 运行 / Run: ./start.sh  (后台启动 + 打印 PID + prod profile + logs/out.log;用自带 jre/bin/java)
#
# 为什么 Linux 不用 jpackage:服务端要透明、可改 java 命令、易接 systemd,且复用项目已有的
# start.sh / manage.sh。Windows 仍走 jpackage(桌面双击体验好,见 build-native.ps1)。
# Why not jpackage on Linux: server deployments want transparency (read/tweak the java command,
# easy systemd integration) and reuse of the existing start.sh / manage.sh.
#
# 依赖 / Requires: JDK 17+(需 jlink/jmods);先 `mvn package` 出 fat jar。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

# ---- locate jmods (jlink needs them) ----
JMODS="${JMODS:-}"
if [ -z "$JMODS" ]; then
  if [ -n "${JAVA_HOME:-}" ] && [ -d "$JAVA_HOME/jmods" ]; then
    JMODS="$JAVA_HOME/jmods"
  else
    for cand in /usr/lib/jvm/java-*-openjdk*/jmods /usr/lib/jvm/*/jmods; do
      if [ -d "$cand" ]; then JMODS="$cand"; break; fi
    done
  fi
fi
if [ ! -d "$JMODS" ]; then
  echo "ERROR: jmods 未找到。设置 JAVA_HOME(含 jmods 的 JDK)或 JMODS 环境变量。" >&2
  echo "       (Debian: apt install openjdk-17-jdk-headless)" >&2
  exit 1
fi
echo "jmods:        $JMODS"

# ---- fat jar + version ----
JAR="$(ls target/file-box-*.jar 2>/dev/null | grep -vE 'sources|javadoc' | head -n 1 || true)"
if [ -z "$JAR" ]; then
  echo "ERROR: target/file-box-*.jar 未找到。请先运行 'mvn package'。" >&2
  exit 1
fi
VERSION="$(basename "$JAR" | sed -E 's/^file-box-//; s/\.jar$//')"
echo "version:      $VERSION"

# ---- jlink 精简运行时所需模块 ----
# java.instrument:Tomcat 类转换必需(jdeps 检测不到)。jdk.crypto.ec/cryptoki:TLS 必需。
MODULES="java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,\
java.naming,java.scripting,java.security.jgss,java.sql,java.transaction.xa,java.xml,\
jdk.crypto.cryptoki,jdk.crypto.ec,jdk.unsupported"

NATIVE="target/native"
DIST_NAME="FileBox-$VERSION-linux-jre"
DIST_DIR="$NATIVE/$DIST_NAME"

echo "==> 组装分发目录 / assemble dist folder (复用 release 包的脚本)"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR/data/default"
cp "$JAR" "$DIST_DIR/"
cp src/assembly/start.sh src/assembly/manage.sh "$DIST_DIR/"
chmod +x "$DIST_DIR/start.sh" "$DIST_DIR/manage.sh"
cp "src/assembly/data/default/操作说明.html" "$DIST_DIR/data/default/"

echo "==> jlink 自带 JRE → jre/  (~minimal)"
# jre/ 而非 runtime/:避开应用运行时自己建的 runtime/multipart-tmp(数据目录)
# jre/ not runtime/: avoid clashing with the app's own runtime/multipart-tmp data dir
jlink --module-path "$JMODS" --add-modules "$MODULES" \
  --strip-debug --no-header-files --no-man-pages --compress=2 \
  --output "$DIST_DIR/jre" 2>/dev/null || {
    # JDK 23+ 把 --compress=2 改成了 --compress=zip-9;失败则退回不压缩
    jlink --module-path "$JMODS" --add-modules "$MODULES" \
      --strip-debug --no-header-files --no-man-pages \
      --output "$DIST_DIR/jre"
  }
echo "    jre size: $(du -sh "$DIST_DIR/jre" | cut -f1)"

echo "==> 写 README"
cat > "$DIST_DIR/README.txt" <<EOF
File Box $VERSION (Linux, 自带运行时 / self-contained — 无需预装 Java)

运行 / Run:
    ./start.sh [--server.port=8888] [--更多 Spring Boot 参数]
    (start.sh 后台启动、打印 PID、写 logs/out.log)

管理 / Manage:
    ./manage.sh    (交互菜单:重置 admin 密码等)

停止 / Stop:
    kill <start.sh 打印的 PID>

首次启动会在本目录下生成 config/ data/ logs/ runtime/,
初始 admin 密码见 logs/filebox.log。浏览器打开 http://localhost:8888 登录。

整个文件夹可一起拷贝、随身携带。
升级:替换 file-box-$VERSION.jar 与 jre/,保留 config/ data/ logs/。
EOF

echo "==> 打包 tar.gz"
rm -f "$NATIVE/$DIST_NAME.tar.gz"
tar czf "$NATIVE/$DIST_NAME.tar.gz" -C "$NATIVE" "$DIST_NAME"

echo
echo "==> 完成 / done:"
ls -lh "$NATIVE/$DIST_NAME.tar.gz"
echo "解压后大小 / extracted size: $(du -sh "$DIST_DIR" | cut -f1)"
