#!/bin/bash
#
# build-native.sh — 为 File Box 构建自带 JRE 的原生包(Linux)。
# Build self-contained native packages for File Box (Linux).
#
# 产物 / Artifacts (输出到 target/native/):
#   - FileBox/                    便携 app-image(解压即用,无需预装 Java)— 始终生成
#   - file-box_<ver>-1_amd64.deb  Debian 安装包(需 dpkg-deb)
#   - file-box-<ver>-1.x86_64.rpm RedHat 安装包(需 rpmbuild)
#
# 数据目录 / Data dir:
#   app-image 的 launcher 通过 -Dfilebox.data.dir=$ROOTDIR/../file-box-data 把
#   config/data/logs/runtime 写到 app-image 同级目录(便携、升级不丢数据)。
#   安装包(deb/rpm)的系统级数据目录、服务用户、systemd 单元属于后续集成工作。
#
# 依赖 / Requires: JDK 17+(需 jlink/jpackage/jmods);先 `mvn package` 出 fat jar。
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
  echo "ERROR: jmods 未找到。请设置 JAVA_HOME(指向含 jmods 的 JDK)或 JMODS 环境变量。" >&2
  echo "       (Debian: apt install openjdk-21-jdk-headless)" >&2
  exit 1
fi
echo "jmods:        $JMODS"

# ---- locate the fat jar + version ----
JAR="$(ls target/file-box-*.jar 2>/dev/null | grep -vE 'sources|javadoc' | head -n 1 || true)"
if [ -z "$JAR" ]; then
  echo "ERROR: target/file-box-*.jar 未找到。请先运行 'mvn package'。" >&2
  exit 1
fi
VERSION="$(basename "$JAR" | sed -E 's/^file-box-//; s/\.jar$//')"
echo "version:      $VERSION"
echo "fat jar:      $JAR"

# ---- jlink 精简运行时所需模块 ----
# java.instrument:Tomcat 类转换必需(jdeps 检测不到)。jdk.crypto.ec/cryptoki:TLS 必需。
MODULES="java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,\
java.naming,java.scripting,java.security.jgss,java.sql,java.transaction.xa,java.xml,\
jdk.crypto.cryptoki,jdk.crypto.ec,jdk.unsupported"

NATIVE="target/native"
STAGE="$NATIVE/input"
DATA_DIR_OPT="-Dfilebox.data.dir=\$ROOTDIR/../file-box-data"
JAVA_OPTS="-Xmx384m -Dspring.profiles.active=prod $DATA_DIR_OPT"
# 通用 jpackage 参数 / common args reused across app-image + installers
JP_COMMON=(--name FileBox --input "$STAGE" --main-jar "$(basename "$JAR")"
           --runtime-image "$NATIVE/runtime" --app-version "$VERSION"
           --vendor FileBox --description "File Box — self-hosted file sharing"
           --java-options "$JAVA_OPTS")

echo "==> staging fat jar"
rm -rf "$STAGE"; mkdir -p "$STAGE"
cp "$JAR" "$STAGE/"

echo "==> jlink custom runtime (~minimal JRE)"
rm -rf "$NATIVE/runtime"
# shellcheck disable=SC2086
jlink --module-path "$JMODS" --add-modules "$MODULES" \
  --strip-debug --no-header-files --no-man-pages --compress=2 \
  --output "$NATIVE/runtime" || {
    # JDK 23+ 把 --compress=2 改成了 --compress=zip-9;失败则退回不压缩
    jlink --module-path "$JMODS" --add-modules "$MODULES" \
      --strip-debug --no-header-files --no-man-pages \
      --output "$NATIVE/runtime"
  }
echo "    runtime size: $(du -sh "$NATIVE/runtime" | cut -f1)"

echo "==> jpackage app-image (portable folder — always)"
rm -rf "$NATIVE/FileBox"
jpackage --type app-image --dest "$NATIVE" "${JP_COMMON[@]}"

# ---- 安装包(按可用工具链) / installers, by available toolchain ----
if command -v dpkg-deb >/dev/null 2>&1; then
  echo "==> jpackage .deb"
  jpackage --type deb --dest "$NATIVE" "${JP_COMMON[@]}" --license-file LICENSE
fi
if command -v rpmbuild >/dev/null 2>&1; then
  echo "==> jpackage .rpm"
  jpackage --type rpm --dest "$NATIVE" "${JP_COMMON[@]}" --license-file LICENSE
fi

echo
echo "==> 完成 / done. Artifacts in $NATIVE/:"
( cd "$NATIVE" && ls -1 | grep -vE '^(input|runtime)$' )
echo "app-image size: $(du -sh "$NATIVE/FileBox" | cut -f1)"
