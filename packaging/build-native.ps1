#
# build-native.ps1 — 为 File Box 构建自带 JRE 的【解压即用】原生包(Windows)。
# Build a self-contained, extract-and-run native package for File Box (Windows).
#
# 产物 / Artifact (target/native/):
#   FileBox-<ver>-windows.zip   解压得到 FileBox-<ver>-windows/{FileBox/, README.txt}
#
# 运行方式 / Run (解压后):
#   双击 / double-click  FileBox-<ver>-windows\FileBox\FileBox.exe
#
# 数据目录 / Data dir:
#   launcher 注入 -Dfilebox.data.dir=$ROOTDIR/../file-box-data —— config/data/logs/runtime
#   写到 app-image 同级(file-box-data\),与 FileBox\ 一起放在解压目录里,整文件夹可随身携带;
#   升级时替换 FileBox\、保留 file-box-data\。
#
# 依赖 / Requires: JDK 17+(需 jlink/jpackage/jmods);先 `mvn package` 出 fat jar。
# 注意 / Note: jpackage 不能交叉编译,Windows 包必须在 Windows 上构建。本脚本未在 Linux 上验证。
$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = (Resolve-Path "$ScriptDir\..").Path
Set-Location $Root

# ---- 定位 jmods (jlink 需要它) ----
$Jmods = $env:JMODS
if (-not $Jmods) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\jmods")) {
        $Jmods = Join-Path $env:JAVA_HOME 'jmods'
    } else {
        # 常见安装位置 / common install locations
        $cands = @(
            "$env:JAVA_HOME\jmods",
            "C:\Program Files\Java\*\jmods",
            "C:\Program Files\Eclipse Adoptium\*\jmods",
            "C:\Program Files\Microsoft\jdk-*\jmods"
        ) | Where-Object { $_ }
        $found = Get-ChildItem $cands -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $Jmods = $found.FullName }
    }
}
if (-not $Jmods -or -not (Test-Path $Jmods)) {
    Write-Error "jmods 未找到。设置 JAVA_HOME(含 jmods 的 JDK)或 JMODS 环境变量。"
    exit 1
}
Write-Host "jmods:        $Jmods"

# ---- 定位 fat jar + 版本 ----
$Jar = Get-ChildItem "target\file-box-*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
if (-not $Jar) {
    Write-Error "target\file-box-*.jar 未找到。请先运行 'mvn package'。"
    exit 1
}
$Version = $Jar.BaseName -replace '^file-box-', ''
Write-Host "version:      $Version"
Write-Host "fat jar:      $($Jar.FullName)"

# ---- jlink 精简运行时所需模块 ----
# java.instrument:Tomcat 类转换必需(jdeps 检测不到)。jdk.crypto.ec/cryptoki:TLS 必需。
$Modules = "java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.scripting,java.security.jgss,java.sql,java.transaction.xa,java.xml,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.unsupported"

$Native     = "target\native"
$DistName   = "FileBox-$Version-windows"
$DistDir    = Join-Path $Native $DistName
$Stage      = Join-Path $Native 'input'
$RuntimeDir = Join-Path $Native 'runtime'

Write-Host "==> staging fat jar"
if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
New-Item -ItemType Directory -Force -Path $Stage | Out-Null
Copy-Item $Jar.FullName $Stage

Write-Host "==> jlink custom runtime (~minimal JRE)"
if (Test-Path $RuntimeDir) { Remove-Item -Recurse -Force $RuntimeDir }
# 单引号里的 $ROOTDIR 保持字面,作为 launcher 运行时展开的占位符
# single-quoted so $ROOTDIR stays literal for the launcher to expand at runtime
jlink --module-path $Jmods --add-modules $Modules `
    --strip-debug --no-header-files --no-man-pages --compress=2 `
    --output $RuntimeDir

Write-Host "==> jpackage app-image (放到分发目录里 / place into distributable folder)"
if (Test-Path $DistDir) { Remove-Item -Recurse -Force $DistDir }
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null
# 用多个 --java-options(每个一个值),规避 PowerShell 把含空格/含$ 的单个串拆错的问题
jpackage --type app-image `
    --name FileBox `
    --input $Stage `
    --main-jar $Jar.Name `
    --runtime-image $RuntimeDir `
    --app-version $Version `
    --vendor FileBox `
    --description "File Box — self-hosted file sharing" `
    --dest $DistDir `
    --java-options '-Xmx384m' `
    --java-options '-Dspring.profiles.active=prod' `
    --java-options '-Dfilebox.data.dir=$ROOTDIR/../file-box-data'

Write-Host "==> 写 README"
$readme = @"
File Box $Version (Windows, 自带运行时 / self-contained — 无需预装 Java)

运行 / Run:
    双击 FileBox\FileBox.exe
    (命令行:FileBox\FileBox.exe [--server.port=8888] [--更多 Spring Boot 参数])

首次启动会在本目录下生成 file-box-data (config / data / logs / runtime),
并把初始 admin 密码打印到 file-box-data\logs\filebox.log。浏览器打开
http://localhost:8888 登录。

整个文件夹(FileBox\ + file-box-data\)可一起拷贝、随身携带。
升级:替换 FileBox\ 子目录,保留 file-box-data\。
"@
Set-Content -Encoding UTF8 (Join-Path $DistDir 'README.txt') $readme

Write-Host "==> 打包 zip"
$Zip = Join-Path $Native "$DistName.zip"
if (Test-Path $Zip) { Remove-Item -Force $Zip }
# 从 native 目录压缩 DistName 目录本身(让 zip 顶层就是 FileBox-<ver>-windows/)
Push-Location $Native
Compress-Archive -Path $DistName -DestinationPath $Zip -Force
Pop-Location

Write-Host ""
Write-Host "==> 完成 / done:"
Get-Item $Zip | Format-List Name, Length
Write-Host ("解压后大小 / extracted size: " + ((Get-ChildItem $DistDir -Recurse | Measure-Object Length -Sum).Sum / 1MB).ToString('F1') + ' MB')
