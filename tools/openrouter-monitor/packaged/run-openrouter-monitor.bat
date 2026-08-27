@echo off
REM Startet den OpenRouter-Monitor.
REM Installierte App: JAR neben diesem Skript + JRE mit JavaFX unter runtime\.
REM Entwicklung: Fallback auf mvn javafx:run.

setlocal EnableExtensions EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"

set "JAR="
if exist "%SCRIPT_DIR%openrouter-monitor.jar" set "JAR=%SCRIPT_DIR%openrouter-monitor.jar"
if not defined JAR if exist "%SCRIPT_DIR%..\target\openrouter-monitor.jar" (
  for %%I in ("%SCRIPT_DIR%..\target\openrouter-monitor.jar") do set "JAR=%%~fI"
)

set "CONFIG_DIR="
set "CUR=%SCRIPT_DIR%"
for /L %%I in (1,1,7) do (
  if not defined CONFIG_DIR (
    for %%P in ("!CUR!..") do set "CUR=%%~fP"
    if exist "!CUR!\config\" set "CONFIG_DIR=!CUR!"
  )
)
if not defined CONFIG_DIR for %%P in ("%SCRIPT_DIR%..") do set "CONFIG_DIR=%%~fP"

set "JAVA="
for %%P in ("%SCRIPT_DIR%..") do set "APP_HOME=%%~fP"
if exist "%APP_HOME%\..\runtime\bin\java.exe" set "JAVA=%APP_HOME%\..\runtime\bin\java.exe"
if not defined JAVA if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA set "JAVA=java"

if defined JAR (
  "%JAVA%" --add-modules javafx.controls -jar "%JAR%" --config-dir="%CONFIG_DIR%" %*
  if not errorlevel 1 goto :eof
)

set "MODULE_DIR="
if exist "%SCRIPT_DIR%..\pom.xml" for %%P in ("%SCRIPT_DIR%..") do set "MODULE_DIR=%%~fP"
if not defined MODULE_DIR if exist "%CONFIG_DIR%\tools\openrouter-monitor\pom.xml" (
  set "MODULE_DIR=%CONFIG_DIR%\tools\openrouter-monitor"
)
if not defined MODULE_DIR (
  echo OpenRouter-Monitor: weder gebuendelte JRE mit JavaFX noch Maven-Projekt gefunden.
  exit /b 1
)

cd /d "%MODULE_DIR%"
call mvn -q javafx:run "-Djavafx.args=--config-dir=%CONFIG_DIR% %*"
exit /b %ERRORLEVEL%
