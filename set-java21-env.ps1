# ========== JAVA_HOME dauerhaft auf Java 21 setzen ==========
# Einmal ausführen (z. B. in PowerShell): .\set-java21-env.ps1
# Danach in einem NEUEN Terminal ist JAVA_HOME gesetzt (Java 21).
# =============================================================

$candidates = @(
    "C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot",
    "C:\Program Files\Eclipse Adoptium\jdk-21*",
    "C:\Program Files\Microsoft\jdk-21*",
    "C:\Program Files\Java\jdk-21*",
    "C:\Program Files\Amazon Corretto\jdk21*",
    "C:\Program Files\Zulu\zulu-21*"
)

$found = $null
foreach ($base in $candidates) {
    if ($base -match '\*') {
        $dirs = Get-Item $base -ErrorAction SilentlyContinue | Sort-Object Name -Descending
        if ($dirs) {
            $found = $dirs[0].FullName
            break
        }
    } else {
        if (Test-Path $base) {
            $javaExe = Join-Path $base "bin\java.exe"
            if (Test-Path $javaExe) {
                $found = $base
                break
            }
        }
    }
}

if (-not $found) {
    $currentJava = Get-Command java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if ($currentJava) {
        $dir = (Get-Item $currentJava).Directory.Parent.FullName
        $v = & "$dir\bin\java.exe" -version 2>&1 | Out-String
        if ($v -match '"21\.') {
            $found = $dir
        }
    }
}

if (-not $found) {
    Write-Host "Kein JDK 21 gefunden. Bitte Java 21 installieren (z. B. Eclipse Adoptium, Microsoft Build of OpenJDK) und den Pfad in set-java21-env.ps1 ergaenzen." -ForegroundColor Red
    exit 1
}

$javaExe = Join-Path $found "bin\java.exe"
# Version ueber cmd auslesen, damit PowerShell stderr nicht als Fehler anzeigt
$version = cmd /c "`"$javaExe`" -version 2>&1"
Write-Host "Gefunden: $found"
Write-Host $version

# Dauerhaft fuer den aktuellen Benutzer setzen
[Environment]::SetEnvironmentVariable("JAVA_HOME", $found, "User")
# Sofort in dieser Session verwenden, dann funktioniert mvn im gleichen Terminal
$env:JAVA_HOME = $found
Write-Host ""
Write-Host "JAVA_HOME wurde dauerhaft gesetzt: $found" -ForegroundColor Green
Write-Host "In diesem Fenster gilt Java 21 sofort; neue Terminals uebernehmen es automatisch." -ForegroundColor Yellow
