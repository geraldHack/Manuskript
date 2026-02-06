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
            Write-Host "JAVA_HOME ist gültig: $env:JAVA_HOME"
        }
    }
}

if (-not $javaHomeValid) {
    Write-Host "JAVA_HOME ist nicht gesetzt oder ungültig. Suche Java automatisch..."
    $javaPath = Get-Command java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if ($javaPath) {
        $env:JAVA_HOME = (Get-Item $javaPath).Directory.Parent.FullName
        Write-Host "JAVA_HOME automatisch gesetzt auf: $env:JAVA_HOME"
    } else {
        # Fallback: Konfigurierter Pfad oder Suche nach jdk-21* / jdk-22* im Adoptium-Ordner
        $found = $false
        if (Test-Path $javaFallbackPath) {
            $env:JAVA_HOME = $javaFallbackPath
            $found = $true
            Write-Host "JAVA_HOME auf Fallback gesetzt: $env:JAVA_HOME"
        }
        if (-not $found -and (Test-Path "C:\Program Files\Eclipse Adoptium")) {
            $jdks = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending
            foreach ($jdk in $jdks) {
                $javaExe = Join-Path $jdk.FullName "bin\java.exe"
                if (Test-Path $javaExe) {
                    $env:JAVA_HOME = $jdk.FullName
                    $found = $true
                    Write-Host "JAVA_HOME auf $($jdk.Name) gesetzt: $env:JAVA_HOME"
                    break
                }
            }
        }
        if (-not $found) {
            Write-Host "FEHLER: Java konnte nicht gefunden werden! Bitte JAVA_HOME setzen oder in run.ps1 die Variable `$javaFallbackPath anpassen." -ForegroundColor Red
            exit 1
        }
    }
}
# Unterdrücke Warnungen über veraltete sun.misc.Unsafe Methoden (von Maven/Guava)
$env:MAVEN_OPTS = "--add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED"
Write-Host "Stoppe alle Java-Prozesse..."
Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Write-Host "Führe Maven Clean aus und starte Anwendung..."
# javafx:run kompiliert automatisch wenn nötig, daher nur clean + javafx:run
mvn clean
Start-Sleep -Seconds 3
mvn javafx:run
