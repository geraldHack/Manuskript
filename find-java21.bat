@echo off
REM Findet JDK 21 und setzt JAVA_HOME.
REM Aufruf: call find-java21.bat
REM Optional: set REQUIRE_JPACKAGE=1 vor dem Aufruf (fuer create-installer.bat)
REM Exitcode 1 wenn nichts Passendes gefunden.

set "FOUND_JAVA_HOME="

REM 1) Vorhandenes JAVA_HOME, wenn java.exe da und Version 21
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" call :check_and_set "%JAVA_HOME%"

REM 2) Bekannte Installationspfade
if not defined FOUND_JAVA_HOME call :check_and_set "C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot"
if not defined FOUND_JAVA_HOME for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do if not defined FOUND_JAVA_HOME call :check_and_set "%%~fD"
if not defined FOUND_JAVA_HOME for /d %%D in ("C:\Program Files\Microsoft\jdk-21*") do if not defined FOUND_JAVA_HOME call :check_and_set "%%~fD"
if not defined FOUND_JAVA_HOME for /d %%D in ("C:\Program Files\Java\jdk-21*") do if not defined FOUND_JAVA_HOME call :check_and_set "%%~fD"
if not defined FOUND_JAVA_HOME for /d %%D in ("C:\Program Files\Amazon Corretto\jdk21*") do if not defined FOUND_JAVA_HOME call :check_and_set "%%~fD"
if not defined FOUND_JAVA_HOME for /d %%D in ("C:\Program Files\Zulu\zulu-21*") do if not defined FOUND_JAVA_HOME call :check_and_set "%%~fD"
if not defined FOUND_JAVA_HOME for /d %%D in ("C:\Program Files\Temurin\jdk-21*") do if not defined FOUND_JAVA_HOME call :check_and_set "%%~fD"

if not defined FOUND_JAVA_HOME (
    echo FEHLER: Kein JDK 21 gefunden.
    echo Bitte Java 21 installieren ^(z.B. Eclipse Adoptium^) oder JAVA_HOME setzen.
    echo Optional: .\set-java21-env.ps1 einmal ausfuehren.
    exit /b 1
)

set "JAVA_HOME=%FOUND_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [OK] JAVA_HOME=%JAVA_HOME%
exit /b 0

:check_and_set
if defined FOUND_JAVA_HOME goto :eof
if not exist "%~1\bin\java.exe" goto :eof
if defined REQUIRE_JPACKAGE if not exist "%~1\bin\jpackage.exe" goto :eof
set "_VEROUT="
for /f "tokens=* delims=" %%V in ('"%~1\bin\java.exe" -version 2^>^&1') do (
    echo %%V | findstr /i "21\." >nul && set "_VEROUT=21"
)
if not defined _VEROUT goto :eof
set "FOUND_JAVA_HOME=%~1"
goto :eof
