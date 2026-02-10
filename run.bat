@echo off
REM Java 21 fuer diese Sitzung verwenden (anpassen an deinen Installationspfad)
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot"
if exist "%JAVA_HOME%\bin\java.exe" (
    echo Java 21: %JAVA_HOME%
) else (
    if not defined JAVA_HOME echo Warnung: JAVA_HOME nicht gesetzt. Fuehre einmal set-java21-env.ps1 aus.
)
echo Stoppe alle Java-Prozesse...
taskkill /f /im java.exe >nul 2>&1
echo Warte 2 Sekunden...
timeout /t 2 /nobreak >nul
echo Führe Maven Clean aus...
call mvn clean
echo Starte Anwendung...
call mvn javafx:run
