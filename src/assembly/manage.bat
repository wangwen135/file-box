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

:menu
echo.
echo File Box Management
echo 1. Reset admin password
echo 2. Show config file path
echo 0. Exit
set /p choice=Select:

if "%choice%"=="1" (
    java -jar "%JAR_NAME%" --filebox.maintenance=reset-admin-password %*
    goto menu
)
if "%choice%"=="2" (
    echo %cd%\config\filebox.yml
    goto menu
)
if "%choice%"=="0" exit /b 0

echo Invalid selection.
goto menu
