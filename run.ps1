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
        # Fallback: Versuche den Eclipse Adoptium Pfad
        $fallbackPath = "C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot"
        if (Test-Path $fallbackPath) {
            $env:JAVA_HOME = $fallbackPath
            Write-Host "JAVA_HOME auf Fallback gesetzt: $env:JAVA_HOME"
        } else {
            Write-Host "FEHLER: Java konnte nicht gefunden werden!" -ForegroundColor Red
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
mvn clean javafx:run
