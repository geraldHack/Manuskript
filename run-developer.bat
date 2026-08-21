@echo off
setlocal
REM Startet Manuskript mit dem zuletzt kompilierten Code (target/classes).
REM Nutze dieses Skript beim Entwickeln, damit Aenderungen sofort laufen.
REM Voraussetzung: Im Ordner manuskript ausfuehren (dort liegt config/, logs/).

cd /d "%~dp0"

call "%~dp0find-java21.bat"
if errorlevel 1 (
    pause
    exit /b 1
)

echo Kompiliere und starte Manuskript (Entwicklermodus)...
call mvn compile javafx:run
set "EXITCODE=%ERRORLEVEL%"
pause
exit /b %EXITCODE%
