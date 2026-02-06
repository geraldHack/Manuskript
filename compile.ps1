# Vollständige Kompilierung des Manuskript-Projekts

# ========== Java-Version / JAVA_HOME anpassen ==========
# Wenn du eine andere Java-Installation nutzen willst, setze hier den Pfad (z.B. jdk-22 oder anderes Patch-Release):
$javaFallbackPath = "C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot"
# =======================================================

# Setze JAVA_HOME falls nicht gesetzt oder Pfad existiert nicht
$javaHomeValid = $false
if ($env:JAVA_HOME) {
    if (Test-Path $env:JAVA_HOME) {
        # Prüfe ob bin/java.exe oder bin\java.exe existiert
        $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $javaExe) {
            $javaHomeValid = $true
            Write-Host "JAVA_HOME ist gültig: $env:JAVA_HOME" -ForegroundColor Green
        }
    }
}

if (-not $javaHomeValid) {
    Write-Host "JAVA_HOME ist nicht gesetzt oder ungültig. Suche Java automatisch..." -ForegroundColor Yellow
    $javaPath = Get-Command java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if ($javaPath) {
        $env:JAVA_HOME = (Get-Item $javaPath).Directory.Parent.FullName
        Write-Host "JAVA_HOME automatisch gesetzt auf: $env:JAVA_HOME" -ForegroundColor Green
    } else {
        # Fallback: Konfigurierter Pfad oder Suche nach jdk-* im Adoptium-Ordner
        $found = $false
        if (Test-Path $javaFallbackPath) {
            $env:JAVA_HOME = $javaFallbackPath
            $found = $true
            Write-Host "JAVA_HOME auf Fallback gesetzt: $env:JAVA_HOME" -ForegroundColor Yellow
        }
        if (-not $found -and (Test-Path "C:\Program Files\Eclipse Adoptium")) {
            $jdks = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending
            foreach ($jdk in $jdks) {
                $javaExe = Join-Path $jdk.FullName "bin\java.exe"
                if (Test-Path $javaExe) {
                    $env:JAVA_HOME = $jdk.FullName
                    $found = $true
                    Write-Host "JAVA_HOME auf $($jdk.Name) gesetzt: $env:JAVA_HOME" -ForegroundColor Yellow
                    break
                }
            }
        }
        if (-not $found) {
            Write-Host "FEHLER: Java konnte nicht gefunden werden! Bitte JAVA_HOME setzen oder in compile.ps1 die Variable `$javaFallbackPath anpassen." -ForegroundColor Red
            exit 1
        }
    }
}

# Unterdrücke Warnungen über veraltete sun.misc.Unsafe Methoden (von Maven/Guava)
$env:MAVEN_OPTS = "--add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "VOLLSTÄNDIGE KOMPILIERUNG" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "[1/4] Lösche alte Build-Artefakte..." -ForegroundColor Yellow
mvn clean
if ($LASTEXITCODE -ne 0) {
    Write-Host "FEHLER: Maven clean fehlgeschlagen!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[2/4] Kompiliere Quellcode..." -ForegroundColor Yellow
mvn compile
if ($LASTEXITCODE -ne 0) {
    Write-Host "FEHLER: Kompilierung fehlgeschlagen!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[3/4] Führe Tests aus..." -ForegroundColor Yellow
mvn test
if ($LASTEXITCODE -ne 0) {
    Write-Host "WARNUNG: Tests fehlgeschlagen, aber kompiliere weiter..." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[4/4] Erstelle JAR-Package..." -ForegroundColor Yellow
mvn package
if ($LASTEXITCODE -ne 0) {
    Write-Host "FEHLER: Package-Erstellung fehlgeschlagen!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "KOMPILIERUNG ERFOLGREICH!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Erstellte Dateien:" -ForegroundColor Cyan
Write-Host "  - target/manuskript-standalone.jar" -ForegroundColor White
Write-Host ""
Write-Host "Zum Starten der Anwendung:" -ForegroundColor Cyan
Write-Host "  .\run.ps1" -ForegroundColor White
Write-Host ""
