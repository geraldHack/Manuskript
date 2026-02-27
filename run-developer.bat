@echo off
REM Startet Manuskript mit dem zuletzt kompilierten Code (target/classes).
REM Nutze dieses Skript beim Entwickeln, damit Aenderungen sofort laufen.
REM Voraussetzung: Im Ordner manuskript ausfuehren (dort liegt config/, logs/).

echo Kompiliere und starte Manuskript (Entwicklermodus)...
call mvn compile javafx:run
pause
