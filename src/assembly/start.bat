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

if "%JAVA_OPTS%"=="" set "JAVA_OPTS=-Xmx384m -Xms128m"

REM Foreground run: single window, logs go to the console, close this window to stop the app.
java %JAVA_OPTS% -jar "%JAR_NAME%" %*
pause