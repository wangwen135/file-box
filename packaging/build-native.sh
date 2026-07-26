#!/bin/bash
#
# build-native.sh — 为 File Box 构建自带 JRE 的【解压即用】原生包(Linux)。
# Build a self-contained, extract-and-run native package for File Box (Linux).
#
# 产物 / Artifact (target/native/):
#   FileBox-<ver>-linux.tar.gz   解压得到 FileBox-<ver>-linux/{FileBox/, README.txt}
#
# 运行方式 / Run (解压后):
#   FileBox-<ver>-linux/FileBox/bin/FileBox [--server.port=8888]
#
# 数据目录 / Data dir:
#   launcher 注入 -Dfilebox.data.dir=$ROOTDIR/../file-box-data —— config/data/logs/runtime
#   写到 app-image 同级(file-box-data/),与 FileBox/ 一起放在解压目录里,整文件夹可随身携带;
#   升级时替换 FileBox/、保留 file-box-data/。不要安装包(installer),就要这种便携包。
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
  echo "ERROR: jmods 未找到。设置 JAVA_HOME(含 jmods 的 JDK)或 JMODS 环境变量。" >&2
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
DIST_NAME="FileBox-$VERSION-linux"
DIST_DIR="$NATIVE/$DIST_NAME"          # 解压后的顶层目录 / top-level folder after extract
STAGE="$NATIVE/input"
# 数据目录:app-image($ROOTDIR)内的 file-box-data/(启动器旁边),首启生成,在程序文件夹里
DATA_DIR_OPT='-Dfilebox.data.dir=$ROOTDIR/file-box-data'
JAVA_OPTS="-Xmx384m -Dspring.profiles.active=prod $DATA_DIR_OPT"
JP_COMMON=(--name FileBox --input "$STAGE" --main-jar "$(basename "$JAR")"
           --runtime-image "$NATIVE/runtime" --app-version "$VERSION"
           --vendor FileBox --description "File Box - self-hosted file sharing"
           --java-options "$JAVA_OPTS")

echo "==> staging fat jar"
rm -rf "$STAGE"; mkdir -p "$STAGE"
cp "$JAR" "$STAGE/"

echo "==> jlink custom runtime (~minimal JRE)"
rm -rf "$NATIVE/runtime"
# shellcheck disable=SC2086
jlink --module-path "$JMODS" --add-modules "$MODULES" \
  --strip-debug --no-header-files --no-man-pages --compress=2 \
  --output "$NATIVE/runtime" 2>/dev/null || {
    # JDK 23+ 把 --compress=2 改成了 --compress=zip-9;失败则退回不压缩
    jlink --module-path "$JMODS" --add-modules "$MODULES" \
      --strip-debug --no-header-files --no-man-pages \
      --output "$NATIVE/runtime"
  }
echo "    runtime size: $(du -sh "$NATIVE/runtime" | cut -f1)"

echo "==> jpackage app-image"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"
jpackage --type app-image --dest "$DIST_DIR" "${JP_COMMON[@]}"

echo "==> 拍平:去掉 jpackage 内层 FileBox/,让启动器直接落在分发根目录 / flatten inner layer"
shopt -s dotglob
mv "$DIST_DIR"/FileBox/* "$DIST_DIR"/
rmdir "$DIST_DIR"/FileBox
shopt -u dotglob

echo "==> 写 README"
cat > "$DIST_DIR/README.txt" <<EOF
File Box $VERSION (Linux, 自带运行时 / self-contained — 无需预装 Java)

运行 / Run:
    ./bin/FileBox [--server.port=8888] [--更多 Spring Boot 参数]

首次启动会在本目录下生成 file-box-data/(config/data/logs/runtime),
并把初始 admin 密码打印到 file-box-data/logs/filebox.log。浏览器打开
http://localhost:8888 登录。

整个文件夹(FileBox/ + file-box-data/)可一起拷贝、随身携带。
升级:替换 FileBox/ 子目录,保留 file-box-data/。
EOF

echo "==> 打包 tar.gz"
rm -f "$NATIVE/$DIST_NAME.tar.gz"
tar czf "$NATIVE/$DIST_NAME.tar.gz" -C "$NATIVE" "$DIST_NAME"

echo
echo "==> 完成 / done:"
ls -lh "$NATIVE/$DIST_NAME.tar.gz"
echo "解压后大小 / extracted size: $(du -sh "$DIST_DIR" | cut -f1)"
