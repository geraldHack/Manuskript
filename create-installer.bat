@echo off
setlocal enabledelayedexpansion

echo ========================================
echo  Manuskript Installer-Paket erstellen
echo ========================================
echo.

REM --- Java 21 pruefen / setzen ---
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot"
if not exist "%JAVA_HOME%\bin\jpackage.exe" (
    echo FEHLER: JDK 21 nicht gefunden unter %JAVA_HOME%
    echo Bitte JAVA_HOME anpassen oder JDK 21 installieren.
    pause
    exit /b 1
)
echo [OK] Java 21: %JAVA_HOME%

REM --- Konfiguration ---
set "APP_NAME=Manuskript"
set "APP_VERSION=1.0.0"
set "MAIN_CLASS=com.manuskript.Launcher"
set "FAT_JAR=manuskript-standalone.jar"
set "JAVAFX_VERSION=21.0.6"
set "JAVAFX_JMODS_DIR=javafx-jmods-%JAVAFX_VERSION%"
set "JAVAFX_JMODS_URL=https://download2.gluonhq.com/openjfx/%JAVAFX_VERSION%/openjfx-%JAVAFX_VERSION%_windows-x64_bin-jmods.zip"
set "OUTPUT_DIR=installer-output"
set "STAGING_DIR=installer-staging"

REM --- Schritt 1: Fat JAR bauen ---
echo.
echo [1/6] Baue Fat JAR...
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

REM --- Schritt 2: JavaFX jmods herunterladen (falls noetig) ---
echo.
echo [2/6] Pruefe JavaFX jmods...
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
echo [3/6] Bereite Staging vor...
if exist "%OUTPUT_DIR%" rmdir /s /q "%OUTPUT_DIR%"
if exist "%STAGING_DIR%" rmdir /s /q "%STAGING_DIR%"
mkdir "%STAGING_DIR%\app"
copy "target\%FAT_JAR%" "%STAGING_DIR%\app\" >nul
echo [OK] Staging vorbereitet.

REM --- Schritt 4: jpackage ausfuehren ---
echo.
echo [4/6] Erstelle App-Image mit jpackage...

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

REM --- Schritt 5: Ressourcen in App-Image kopieren ---
echo.
echo [5/6] Kopiere Ressourcen ins App-Image...

set "APP_IMAGE=%OUTPUT_DIR%\%APP_NAME%"

REM Config-Verzeichnis (ohne sessions/)
echo   - config/
xcopy "config\*" "%APP_IMAGE%\config\" /E /I /Q >nul 2>&1
if exist "%APP_IMAGE%\config\sessions" rmdir /s /q "%APP_IMAGE%\config\sessions" >nul 2>&1
REM LanguageTool-Wörterbuch nicht mitshipen (projektspezifisch; App legt leere Datei an)
if exist "%APP_IMAGE%\config\languagetool-dictionary.txt" del "%APP_IMAGE%\config\languagetool-dictionary.txt" >nul 2>&1

REM FFmpeg (nur ZIP, wird beim ersten Start automatisch entpackt)
echo   - ffmpeg/
mkdir "%APP_IMAGE%\ffmpeg"
if exist "ffmpeg\ffmpeg.zip" copy "ffmpeg\ffmpeg.zip" "%APP_IMAGE%\ffmpeg\" >nul

REM Pandoc (ZIP + Templates + Hilfsdateien)
echo   - pandoc/
mkdir "%APP_IMAGE%\pandoc"
if exist "pandoc\pandoc.zip" copy "pandoc\pandoc.zip" "%APP_IMAGE%\pandoc\" >nul
for %%f in (pandoc\*.docx pandoc\*.txt pandoc\*.lua pandoc\*.css pandoc\*.yaml pandoc\*.tex pandoc\*.html pandoc\*.rtf pandoc\*.md) do (
    copy "%%f" "%APP_IMAGE%\pandoc\" >nul 2>&1
)

REM Demo-Manuskripte
if exist "Manuskripte" (
    echo   - Manuskripte/
    xcopy "Manuskripte\*" "%APP_IMAGE%\Manuskripte\" /E /I /Q >nul 2>&1
)

REM Language Tool (ca. 386 MB, enthaelt lokalen Grammatik-Server)
if exist "language tool" (
    echo   - language tool/ ^(kann etwas dauern...^)
    xcopy "language tool\*" "%APP_IMAGE%\language tool\" /E /I /Q >nul 2>&1
)

echo [OK] Ressourcen kopiert.

REM --- Schritt 6: ZIP erstellen ---
echo.
echo [6/6] Erstelle ZIP-Archiv...
set "ZIP_NAME=%APP_NAME%-%APP_VERSION%-windows.zip"
if exist "%OUTPUT_DIR%\%ZIP_NAME%" del "%OUTPUT_DIR%\%ZIP_NAME%"
powershell -Command "Compress-Archive -Path '%APP_IMAGE%' -DestinationPath '%OUTPUT_DIR%\%ZIP_NAME%' -Force"
if errorlevel 1 (
    echo WARNUNG: ZIP-Erstellung fehlgeschlagen. App-Image ist trotzdem nutzbar.
) else (
    echo [OK] ZIP erstellt: %OUTPUT_DIR%\%ZIP_NAME%
)

REM --- Staging aufraeumen ---
rmdir /s /q "%STAGING_DIR%" >nul 2>&1

echo.
echo ========================================
echo  Fertig!
echo ========================================
echo.
echo  App-Image:  %APP_IMAGE%\
echo  ZIP:        %OUTPUT_DIR%\%ZIP_NAME%
echo.
echo  Starten:    %APP_IMAGE%\%APP_NAME%.exe
echo.
echo  Zur Weitergabe: Die ZIP-Datei enthaelt
echo  alles (JRE, JavaFX, Config, FFmpeg, Pandoc).
echo  Der Empfaenger muss kein Java installieren!
echo.
echo  FFmpeg und Pandoc werden beim ersten Start
echo  automatisch aus ihren ZIP-Dateien entpackt.
echo.
pause
