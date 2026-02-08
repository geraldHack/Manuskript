# ========== Java-Version: Hier wird Java 21 für dieses Projekt verwendet ==========
$javaPreferredPath = "C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot"
# ==================================================================================

# Immer das konfigurierte Java (21) verwenden, wenn der Ordner existiert
$javaExe = Join-Path $javaPreferredPath "bin\java.exe"
if (Test-Path $javaExe) {
    $env:JAVA_HOME = $javaPreferredPath
    Write-Host "Java 21 (Projekt): $env:JAVA_HOME"
} else {
    # Fallback: System-JAVA_HOME oder Suche
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        Write-Host "JAVA_HOME (System): $env:JAVA_HOME"
    } else {
        Write-Host "Java wird gesucht..."
        $javaPath = Get-Command java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
        if ($javaPath) {
            $env:JAVA_HOME = (Get-Item $javaPath).Directory.Parent.FullName
            Write-Host "JAVA_HOME gesetzt: $env:JAVA_HOME"
        } else {
            Write-Host "FEHLER: Java nicht gefunden. Bitte Pfad in run.ps1 (javaPreferredPath) anpassen." -ForegroundColor Red
            exit 1
        }
    }
}
# --add-opens gibt es erst ab Java 9; unter Java 8 führt es zu "Unrecognized option"
$javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
$versionOutput = & $javaExe -version 2>&1 | Out-String
$isJava8 = $versionOutput -match '"1\.8\.'
if ($isJava8) {
    $env:MAVEN_OPTS = ""
    Write-Host "Java 8 erkannt – MAVEN_OPTS ohne --add-opens"
} else {
    $env:MAVEN_OPTS = "--add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED"
}
Write-Host "Stoppe alle Java-Prozesse..."
Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Write-Host "Führe Maven Clean aus und starte Anwendung..."
# javafx:run kompiliert automatisch wenn nötig, daher nur clean + javafx:run
mvn clean
Start-Sleep -Seconds 3
mvn javafx:run
