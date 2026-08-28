@echo off
setlocal enabledelayedexpansion

set "UPLOAD=1"
if /i "%~1"=="--no-upload" set "UPLOAD=0"
if /i "%~1"=="--upload" set "UPLOAD=1"
if /i "%~1"=="-h" goto :help
if /i "%~1"=="--help" goto :help

echo ========================================
echo  Manuskript Installer-Paket erstellen
echo  (Windows x64)
echo ========================================
echo.
if "%UPLOAD%"=="1" (
    echo  Nach dem Build: Upload nach spoteroxe.de
) else (
    echo  Nur lokal bauen (--no-upload)
)
echo.
goto :after_help
:help
echo Usage: create-installer.bat [--upload^|--no-upload]
echo.
echo   --upload      ZIP/EXE nach spoteroxe.de kopieren (Standard)
echo   --no-upload   nur lokal bauen
echo.
echo Umgebung: MANUSKRIPT_DEPLOY_HOST, MANUSKRIPT_DEPLOY_PATH
echo WiX Toolset 3.x wird fuer die Setup-EXE benoetigt.
echo Ohne WiX wird die ZIP hochgeladen.
pause
exit /b 0
:after_help

REM --- Java 21 pruefen / setzen (mit jpackage) ---
cd /d "%~dp0"
set "REQUIRE_JPACKAGE=1"
call "%~dp0find-java21.bat"
if errorlevel 1 (
    pause
    exit /b 1
)
echo [OK] Java 21 + jpackage: %JAVA_HOME%

REM --- Konfiguration ---
set "APP_NAME=Manuskript"
set "VERSION_FILE=src\main\resources\manuskript.version"
if not exist "%VERSION_FILE%" (
    echo FEHLER: Versionsdatei fehlt: %VERSION_FILE%
    pause
    exit /b 1
)
set /p APP_VERSION=<"%VERSION_FILE%"
for /f "tokens=* delims= " %%A in ("%APP_VERSION%") do set "APP_VERSION=%%A"
echo [OK] Deploy-Version: %APP_VERSION%
set "MAIN_CLASS=com.manuskript.Launcher"
set "FAT_JAR=manuskript-standalone.jar"
set "JAVAFX_VERSION=21.0.6"
set "JAVAFX_JMODS_DIR=javafx-jmods-%JAVAFX_VERSION%"
set "JAVAFX_JMODS_URL=https://download2.gluonhq.com/openjfx/%JAVAFX_VERSION%/openjfx-%JAVAFX_VERSION%_windows-x64_bin-jmods.zip"
set "OUTPUT_DIR=installer-output"
set "STAGING_DIR=installer-staging"

REM --- Schritt 1: Fat JAR bauen ---
echo.
echo [1/7] Baue Fat JAR...
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo FEHLER: Maven Build fehlgeschlagen!
    pause
    exit /b 1
)
if not exist "target\%FAT_JAR%" (
    echo FEHLER: %FAT_JAR% nicht in target\ gefunden!
    pause
    exit /b 1
)
echo [OK] %FAT_JAR% erstellt.

echo   Baue OpenRouter-Monitor...
call mvn -f tools\openrouter-monitor\pom.xml package -DskipTests -q
if errorlevel 1 (
    echo FEHLER: OpenRouter-Monitor-Build fehlgeschlagen!
    pause
    exit /b 1
)
if not exist "tools\openrouter-monitor\target\openrouter-monitor.jar" (
    echo FEHLER: tools\openrouter-monitor\target\openrouter-monitor.jar nicht gefunden!
    pause
    exit /b 1
)
echo [OK] OpenRouter-Monitor erstellt.

echo   Baue Mammouth-Monitor...
call mvn -f tools\mammouth-monitor\pom.xml package -DskipTests -q
if errorlevel 1 (
    echo FEHLER: Mammouth-Monitor-Build fehlgeschlagen!
    pause
    exit /b 1
)
if not exist "tools\mammouth-monitor\target\mammouth-monitor.jar" (
    echo FEHLER: tools\mammouth-monitor\target\mammouth-monitor.jar nicht gefunden!
    pause
    exit /b 1
)
echo [OK] Mammouth-Monitor erstellt.

REM --- Schritt 2: JavaFX jmods herunterladen (falls noetig) ---
echo.
echo [2/7] Pruefe JavaFX jmods...
if not exist "%JAVAFX_JMODS_DIR%" (
    echo JavaFX jmods nicht vorhanden, lade herunter...
    powershell -Command "Invoke-WebRequest -Uri '%JAVAFX_JMODS_URL%' -OutFile 'javafx-jmods.zip'"
    if errorlevel 1 (
        echo FEHLER: Download der JavaFX jmods fehlgeschlagen!
        pause
        exit /b 1
    )
    echo Entpacke JavaFX jmods...
    powershell -Command "Expand-Archive -Path 'javafx-jmods.zip' -DestinationPath '.' -Force"
    del "javafx-jmods.zip"
)
if not exist "%JAVAFX_JMODS_DIR%" (
    echo FEHLER: JavaFX jmods Verzeichnis nicht gefunden nach Download!
    pause
    exit /b 1
)
echo [OK] JavaFX jmods: %JAVAFX_JMODS_DIR%

REM --- Schritt 3: Alte Ausgabe loeschen, Staging vorbereiten ---
echo.
echo [3/7] Bereite Staging vor...
if exist "%OUTPUT_DIR%" rmdir /s /q "%OUTPUT_DIR%"
if exist "%STAGING_DIR%" rmdir /s /q "%STAGING_DIR%"
mkdir "%STAGING_DIR%\app"
copy "target\%FAT_JAR%" "%STAGING_DIR%\app\" >nul
echo [OK] Staging vorbereitet.

REM --- Schritt 4: jpackage ausfuehren ---
echo.
echo [4/7] Erstelle App-Image mit jpackage...

"%JAVA_HOME%\bin\jpackage.exe" ^
    --type app-image ^
    --name "%APP_NAME%" ^
    --app-version "%APP_VERSION%" ^
    --vendor "Manuskript" ^
    --input "%STAGING_DIR%\app" ^
    --main-jar "%FAT_JAR%" ^
    --main-class "%MAIN_CLASS%" ^
    --module-path "%JAVAFX_JMODS_DIR%" ^
    --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.swing,javafx.media,java.base,java.desktop,java.logging,java.naming,java.net.http,java.prefs,java.sql,java.xml,java.xml.crypto,java.management,java.scripting,jdk.unsupported,jdk.crypto.ec,jdk.httpserver,jdk.localedata,jdk.charsets,jdk.zipfs ^
    --jlink-options "--strip-debug --no-man-pages --no-header-files" ^
    --java-options "--add-opens javafx.graphics/javafx.css=ALL-UNNAMED" ^
    --java-options "--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" ^
    --java-options "-Dprism.dirtyopts=false" ^
    --dest "%OUTPUT_DIR%"

if errorlevel 1 (
    echo FEHLER: jpackage fehlgeschlagen!
    echo Pruefe ob alle Abhaengigkeiten vorhanden sind.
    pause
    exit /b 1
)
echo [OK] App-Image erstellt.

REM --- Schritt 5: Ressourcen in App-Image kopieren (unter app\, wie ApplicationPaths) ---
echo.
echo [5/7] Kopiere Ressourcen ins App-Image...

set "APP_IMAGE=%OUTPUT_DIR%\%APP_NAME%"
set "APP_DIR=%APP_IMAGE%\app"
if not exist "%APP_DIR%" (
    echo FEHLER: app\-Verzeichnis fehlt unter %APP_IMAGE%
    pause
    exit /b 1
)

REM Config-Verzeichnis (ohne sessions/)
echo   - config/
xcopy "config\*" "%APP_DIR%\config\" /E /I /Q >nul 2>&1
if exist "%APP_DIR%\config\sessions" rmdir /s /q "%APP_DIR%\config\sessions" >nul 2>&1
REM LanguageTool-Wörterbuch nicht mitshipen (projektspezifisch; App legt leere Datei an)
if exist "%APP_DIR%\config\languagetool-dictionary.txt" del "%APP_DIR%\config\languagetool-dictionary.txt" >nul 2>&1
if exist "%APP_DIR%\config\openrouter-monitor.properties" del "%APP_DIR%\config\openrouter-monitor.properties" >nul 2>&1
if exist "%APP_DIR%\config\mammouth-monitor.properties" del "%APP_DIR%\config\mammouth-monitor.properties" >nul 2>&1
copy /Y "installer-assets\installer-config\launchers.json" "%APP_DIR%\config\launchers.json" >nul

REM Plugin-Katalog (inaktiv). plugins\ bleibt leer, bis der Nutzer im Setup aktiviert.
echo   - plugin-catalog/
mkdir "%APP_DIR%\plugin-catalog" 2>nul
mkdir "%APP_DIR%\plugins" 2>nul
if exist "plugin-catalog" xcopy "plugin-catalog\*" "%APP_DIR%\plugin-catalog\" /E /I /Q /Y >nul 2>&1
if exist "%APP_DIR%\plugin-catalog\.gitkeep" del "%APP_DIR%\plugin-catalog\.gitkeep" >nul 2>&1
if exist "%APP_DIR%\plugin-catalog\README.md" del "%APP_DIR%\plugin-catalog\README.md" >nul 2>&1
del /q "%APP_DIR%\plugin-catalog\*.properties" >nul 2>&1
if not exist "tools\openrouter-monitor\target\openrouter-monitor.jar" (
    echo FEHLER: tools\openrouter-monitor\target\openrouter-monitor.jar fehlt.
    pause
    exit /b 1
)
copy /Y "tools\openrouter-monitor\target\openrouter-monitor.jar" "%APP_DIR%\plugin-catalog\openrouter-monitor.jar" >nul
copy /Y "tools\openrouter-monitor\packaged\run-openrouter-monitor.sh" "%APP_DIR%\plugin-catalog\run-openrouter-monitor.sh" >nul
copy /Y "tools\openrouter-monitor\packaged\run-openrouter-monitor.bat" "%APP_DIR%\plugin-catalog\run-openrouter-monitor.bat" >nul
if not exist "tools\mammouth-monitor\target\mammouth-monitor.jar" (
    echo FEHLER: tools\mammouth-monitor\target\mammouth-monitor.jar fehlt.
    pause
    exit /b 1
)
copy /Y "tools\mammouth-monitor\target\mammouth-monitor.jar" "%APP_DIR%\plugin-catalog\mammouth-monitor.jar" >nul
copy /Y "tools\mammouth-monitor\packaged\run-mammouth-monitor.sh" "%APP_DIR%\plugin-catalog\run-mammouth-monitor.sh" >nul
copy /Y "tools\mammouth-monitor\packaged\run-mammouth-monitor.bat" "%APP_DIR%\plugin-catalog\run-mammouth-monitor.bat" >nul

REM FFmpeg (nur ZIP, wird beim ersten Start automatisch entpackt)
echo   - ffmpeg/
mkdir "%APP_DIR%\ffmpeg" 2>nul
if exist "ffmpeg\ffmpeg.zip" copy "ffmpeg\ffmpeg.zip" "%APP_DIR%\ffmpeg\" >nul

REM Pandoc (ZIP + Templates + Hilfsdateien)
echo   - pandoc/
mkdir "%APP_DIR%\pandoc" 2>nul
if exist "pandoc\pandoc.zip" copy "pandoc\pandoc.zip" "%APP_DIR%\pandoc\" >nul
for %%f in (pandoc\*.docx pandoc\*.txt pandoc\*.lua pandoc\*.css pandoc\*.yaml pandoc\*.tex pandoc\*.html pandoc\*.rtf pandoc\*.md) do (
    copy "%%f" "%APP_DIR%\pandoc\" >nul 2>&1
)

REM Demo-Vorlage (wird beim ersten Start nach Documents\Manuskript kopiert)
if exist "Manuskripte" (
    echo   - Manuskripte/ (Demo-Vorlage fuer Erststart)
    xcopy "Manuskripte\*" "%APP_DIR%\Manuskripte\" /E /I /Q >nul 2>&1
)

REM Language Tool (ca. 386 MB, enthaelt lokalen Grammatik-Server)
if exist "language tool" (
    echo   - language tool/ ^(kann etwas dauern...^)
    xcopy "language tool\*" "%APP_DIR%\language tool\" /E /I /Q >nul 2>&1
)

echo [OK] Ressourcen nach %APP_DIR% kopiert.

REM --- Schritt 6: ZIP + Setup-EXE ---
echo.
echo [6/7] Erstelle ZIP und Setup-EXE...
set "ZIP_NAME=%APP_NAME%-%APP_VERSION%-windows-x64.zip"
if exist "%OUTPUT_DIR%\%ZIP_NAME%" del "%OUTPUT_DIR%\%ZIP_NAME%"
powershell -Command "Compress-Archive -Path '%APP_IMAGE%' -DestinationPath '%OUTPUT_DIR%\%ZIP_NAME%' -Force"
if errorlevel 1 (
    echo WARNUNG: ZIP-Erstellung fehlgeschlagen. App-Image ist trotzdem nutzbar.
) else (
    echo [OK] ZIP erstellt: %OUTPUT_DIR%\%ZIP_NAME%
)

set "EXE_NAME=%APP_NAME%-%APP_VERSION%-windows-x64.exe"
set "WIN_ARTIFACT="
set "WIN_KIND=zip"
if exist "%OUTPUT_DIR%\%ZIP_NAME%" (
    set "WIN_ARTIFACT=%OUTPUT_DIR%\%ZIP_NAME%"
    set "WIN_KIND=zip"
)

echo   Erstelle Setup-EXE mit jpackage --type exe ...
echo   (braucht WiX Toolset 3.x; sonst bleibt die ZIP)
if exist "installer-assets\Manuskript.ico" (
    "%JAVA_HOME%\bin\jpackage.exe" --type exe --app-image "%APP_IMAGE%" --name "%APP_NAME%" --app-version "%APP_VERSION%" --vendor "Manuskript" --icon "installer-assets\Manuskript.ico" --win-dir-chooser --win-menu --win-shortcut --win-per-user-install --dest "%OUTPUT_DIR%"
) else (
    "%JAVA_HOME%\bin\jpackage.exe" --type exe --app-image "%APP_IMAGE%" --name "%APP_NAME%" --app-version "%APP_VERSION%" --vendor "Manuskript" --win-dir-chooser --win-menu --win-shortcut --win-per-user-install --dest "%OUTPUT_DIR%"
)
if errorlevel 1 (
    echo WARNUNG: Setup-EXE fehlgeschlagen (WiX 3 installieren: https://wixtoolset.org/docs/wix3/).
    echo          ZIP wird nach spoteroxe.de hochgeladen.
) else (
    if exist "%OUTPUT_DIR%\%APP_NAME%-%APP_VERSION%.exe" (
        move /Y "%OUTPUT_DIR%\%APP_NAME%-%APP_VERSION%.exe" "%OUTPUT_DIR%\%EXE_NAME%" >nul
    ) else if exist "%OUTPUT_DIR%\%APP_NAME%.exe" (
        move /Y "%OUTPUT_DIR%\%APP_NAME%.exe" "%OUTPUT_DIR%\%EXE_NAME%" >nul
    )
    if exist "%OUTPUT_DIR%\%EXE_NAME%" (
        echo [OK] Setup-EXE: %OUTPUT_DIR%\%EXE_NAME%
        set "WIN_ARTIFACT=%OUTPUT_DIR%\%EXE_NAME%"
        set "WIN_KIND=exe"
    )
)

REM --- Staging aufraeumen ---
rmdir /s /q "%STAGING_DIR%" >nul 2>&1

REM --- Schritt 7: Upload ---
echo.
if "%UPLOAD%"=="1" (
    if defined WIN_ARTIFACT if exist "%WIN_ARTIFACT%" (
        echo [7/7] Lade Windows-Paket nach spoteroxe.de ...
        powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy\spoteroxe\upload-windows.ps1" -File "%WIN_ARTIFACT%" -Version "%APP_VERSION%" -Kind "%WIN_KIND%"
        if errorlevel 1 (
            echo WARNUNG: Upload fehlgeschlagen. Lokal: %WIN_ARTIFACT%
        )
    ) else (
        echo [7/7] Kein Windows-Paket zum Hochladen.
    )
) else (
    echo [7/7] Upload uebersprungen (--no-upload).
)

REM Patch-Version fuer den naechsten Deploy hochzaehlen
for /f "tokens=1-3 delims=." %%A in ("%APP_VERSION%") do (
    set "VERSION_MAJOR=%%A"
    set "VERSION_MINOR=%%B"
    set /a VERSION_PATCH=%%C+1
)
set "NEXT_VERSION=!VERSION_MAJOR!.!VERSION_MINOR!.!VERSION_PATCH!"
> "%VERSION_FILE%" echo !NEXT_VERSION!
echo [OK] Naechste Deploy-Version: !NEXT_VERSION!

echo.
echo ========================================
echo  Fertig!
echo ========================================
echo.
echo  Version:    %APP_VERSION%
echo  App-Image:  %APP_IMAGE%\
echo  ZIP:        %OUTPUT_DIR%\%ZIP_NAME%
if exist "%OUTPUT_DIR%\%EXE_NAME%" echo  Setup-EXE:  %OUTPUT_DIR%\%EXE_NAME%
echo.
echo  Starten:    %APP_IMAGE%\%APP_NAME%.exe
echo.
if "%UPLOAD%"=="1" (
    echo  Web:        https://spoteroxe.de/downloads.html
)
echo.
echo  Zur Weitergabe: ZIP oder Setup-EXE enthaelt
echo  alles (JRE, JavaFX, Config, FFmpeg, Pandoc, plugins\Monitor-JARs).
echo  Der Empfaenger muss kein Java installieren!
echo.
echo  FFmpeg und Pandoc werden beim ersten Start
echo  automatisch aus ihren ZIP-Dateien entpackt.
echo.
pause
goto :eof
