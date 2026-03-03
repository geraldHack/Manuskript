# PowerShell-Skript zum Herunterladen der Quill-Bibliothek
# Fuehren Sie dieses Skript im quill-Verzeichnis aus

Write-Host "Lade Quill-Bibliothek herunter..." -ForegroundColor Green

# Pruefe ob wir im richtigen Verzeichnis sind
if (-not (Test-Path "README.md")) {
    Write-Host "FEHLER: Bitte fuehren Sie dieses Skript im quill-Verzeichnis aus!" -ForegroundColor Red
    Write-Host "Erwarteter Pfad: src\main\resources\quill\" -ForegroundColor Yellow
    exit 1
}

# Quill-Version
$quillVersion = "1.3.6"

# URLs
$quillJsUrl = "https://cdn.quilljs.com/$quillVersion/quill.min.js"
$quillCssUrl = "https://cdn.quilljs.com/$quillVersion/quill.snow.css"

# Alternative URLs (falls CDN nicht erreichbar)
$quillJsAltUrl = "https://unpkg.com/quill@$quillVersion/dist/quill.min.js"
$quillCssAltUrl = "https://unpkg.com/quill@$quillVersion/dist/quill.snow.css"

# Funktion zum Herunterladen mit Fallback
function Download-File {
    param(
        [string]$Url,
        [string]$OutputFile,
        [string]$AltUrl
    )
    
    try {
        Write-Host "Lade $OutputFile von $Url..." -ForegroundColor Cyan
        Invoke-WebRequest -Uri $Url -OutFile $OutputFile -ErrorAction Stop
        Write-Host "[OK] $OutputFile erfolgreich heruntergeladen" -ForegroundColor Green
        
        # Pruefe Dateigroesse
        $fileSize = (Get-Item $OutputFile).Length
        if ($fileSize -lt 1000) {
            $sizeStr = "$fileSize Bytes"
            Write-Host "WARNUNG: Datei scheint zu klein zu sein ($sizeStr)" -ForegroundColor Yellow
        }
        
        return $true
    } catch {
        Write-Host "Fehler beim Laden von $Url" -ForegroundColor Red
        Write-Host "Versuche alternative URL: $AltUrl" -ForegroundColor Yellow
        
        try {
            Invoke-WebRequest -Uri $AltUrl -OutFile $OutputFile -ErrorAction Stop
            Write-Host "[OK] $OutputFile erfolgreich von alternativer URL heruntergeladen" -ForegroundColor Green
            return $true
        } catch {
            Write-Host "FEHLER: Konnte $OutputFile nicht herunterladen" -ForegroundColor Red
            Write-Host $_.Exception.Message -ForegroundColor Red
            return $false
        }
    }
}

# Lade Quill JavaScript
$jsSuccess = Download-File -Url $quillJsUrl -OutputFile "quill.min.js" -AltUrl $quillJsAltUrl

# Lade Quill CSS
$cssSuccess = Download-File -Url $quillCssUrl -OutputFile "quill.snow.css" -AltUrl $quillCssAltUrl

# Zusammenfassung
Write-Host ""
Write-Host "=== Zusammenfassung ===" -ForegroundColor Cyan
if ($jsSuccess) {
    $jsSize = (Get-Item "quill.min.js").Length / 1KB
    $jsSizeStr = [math]::Round($jsSize, 2).ToString() + " KB"
    Write-Host "[OK] quill.min.js ($jsSizeStr)" -ForegroundColor Green
} else {
    Write-Host "[FEHLER] quill.min.js" -ForegroundColor Red
}

if ($cssSuccess) {
    $cssSize = (Get-Item "quill.snow.css").Length / 1KB
    $cssSizeStr = [math]::Round($cssSize, 2).ToString() + " KB"
    Write-Host "[OK] quill.snow.css ($cssSizeStr)" -ForegroundColor Green
} else {
    Write-Host "[FEHLER] quill.snow.css" -ForegroundColor Red
}

if ($jsSuccess -and $cssSuccess) {
    Write-Host ""
    Write-Host "[OK] Alle Dateien erfolgreich heruntergeladen!" -ForegroundColor Green
    Write-Host "Sie koennen jetzt die Anwendung verwenden." -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "[FEHLER] Einige Dateien konnten nicht heruntergeladen werden." -ForegroundColor Red
    Write-Host "Bitte pruefen Sie Ihre Internet-Verbindung oder laden Sie die Dateien manuell herunter." -ForegroundColor Yellow
    exit 1
}
