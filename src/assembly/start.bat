@echo off
setlocal

cd /d "%~dp0"

if not exist config mkdir config
if not exist data mkdir data
if not exist data\default mkdir data\default
if not exist logs mkdir logs
if not exist runtime mkdir runtime
if not exist runtime\multipart-tmp mkdir runtime\multipart-tmp

set "JAR_NAME="
for %%F in (file-box-*.jar) do (
    set "JAR_NAME=%%F"
    goto :found_jar
)

:found_jar
if "%JAR_NAME%"=="" (
    echo Error: no file-box-*.jar found in %cd%
    pause
    exit /b 1
)

set "LOG_FILE=logs\out.log"
if "%JAVA_OPTS%"=="" set "JAVA_OPTS=-Xmx384m -Xms128m"
REM 默认生产 profile:关闭控制台日志(只写 logs\filebox.log),避免 out.log 重复捕获整份日志。
REM Default prod profile: console logging off (file only), so out.log doesn't duplicate the full log.
if "%SPRING_PROFILES_ACTIVE%"=="" set "SPRING_PROFILES_ACTIVE=prod"

start "File Box Application" java %JAVA_OPTS% -jar "%JAR_NAME%" %* ^> "%LOG_FILE%" 2^>^&1

echo File Box started.
echo Jar: %JAR_NAME%
echo Log: %LOG_FILE%
pause
