@echo off
setlocal
cd /d "%~dp0"

call "%~dp0find-java21.bat"
if errorlevel 1 (
    pause
    exit /b 1
)

echo Stoppe Manuskript-bezogene Java-Prozesse ^(nicht alle java.exe^)...
REM Nur Hinweis: globales taskkill aller java.exe entfallen - zu destruktiv.
echo Warte 1 Sekunde...
timeout /t 1 /nobreak >nul
echo Führe Maven Clean aus...
call mvn clean
echo Starte Anwendung...
call mvn javafx:run
set "EXITCODE=%ERRORLEVEL%"
pause
exit /b %EXITCODE%
