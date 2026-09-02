# Lädt Windows-Build nach spoteroxe.de.
# Metadaten: Manuskript-windows-x64.txt (wie Plugin-.txt), kein JSON.
# Download-Datei behält die Versionsnummer im Namen.
param(
    [Parameter(Mandatory = $true)]
    [string] $File,
    [Parameter(Mandatory = $true)]
    [string] $Version,
    [string] $DeployHost = $(if ($env:MANUSKRIPT_DEPLOY_HOST) { $env:MANUSKRIPT_DEPLOY_HOST } else { "spoteroxe.de" }),
    [string] $RemoteDir = $(if ($env:MANUSKRIPT_DEPLOY_PATH) { $env:MANUSKRIPT_DEPLOY_PATH } else { "/home/gehack/home/downloads" }),
    [string] $Kind = "exe"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $File)) {
    throw "Datei fehlt: $File"
}

$currentName = [System.IO.Path]::GetFileName($File)
$item = Get-Item -LiteralPath $File
$sizeBytes = [int64] $item.Length
$sizeMb = [int] [Math]::Round(($sizeBytes + 524288) / 1MB)
$remoteFile = "${RemoteDir}/${currentName}"
$latestTxtName = "Manuskript-windows-x64.txt"
$versionTxtName = [System.IO.Path]::GetFileNameWithoutExtension($currentName) + ".txt"
$jsLocal = Join-Path $PSScriptRoot "manuskript-download.js"
$htmlLocal = Join-Path $PSScriptRoot "downloads.html"

$releaseNotesFile = Join-Path $PSScriptRoot "manuskript-release-notes.txt"
$releaseNotes = ""
if (Test-Path -LiteralPath $releaseNotesFile) {
    $releaseNotes = [System.IO.File]::ReadAllText($releaseNotesFile).TrimEnd()
}

$notesBody = @"
Manuskript
$Version

Windows (x64)
$sizeMb MB
$currentName
"@
if ($releaseNotes) {
    $notesBody = $notesBody.TrimEnd() + "`n" + $releaseNotes + "`n"
}
$tmpNotes = Join-Path $env:TEMP $latestTxtName
$lfNotes = (($notesBody -replace "`r`n", "`n") -replace "`r", "`n").TrimEnd() + "`n"
[System.IO.File]::WriteAllText($tmpNotes, $lfNotes, [System.Text.UTF8Encoding]::new($false))

Write-Host ""
Write-Host "[Upload] ${DeployHost}:${RemoteDir}  ($currentName, $sizeMb MB)"

ssh -o BatchMode=yes -o ConnectTimeout=15 $DeployHost "mkdir -p '$RemoteDir'"
if ($LASTEXITCODE -ne 0) { throw "SSH zu $DeployHost fehlgeschlagen." }

Write-Host "  scp $currentName (kann bei großen Dateien dauern) ..."
scp -o BatchMode=yes $File "${DeployHost}:${remoteFile}"
if ($LASTEXITCODE -ne 0) { throw "scp der Windows-Datei fehlgeschlagen." }

scp -o BatchMode=yes $tmpNotes "${DeployHost}:${RemoteDir}/${latestTxtName}"
if ($LASTEXITCODE -ne 0) { throw "scp von $latestTxtName fehlgeschlagen." }
scp -o BatchMode=yes $tmpNotes "${DeployHost}:${RemoteDir}/${versionTxtName}"
if ($LASTEXITCODE -ne 0) { throw "scp von $versionTxtName fehlgeschlagen." }

if (Test-Path $jsLocal) {
    scp -o BatchMode=yes $jsLocal "${DeployHost}:/home/gehack/home/js/manuskript-download.js"
}
if (Test-Path $htmlLocal) {
    scp -o BatchMode=yes $htmlLocal "${DeployHost}:/home/gehack/home/downloads.html"
}

$ext = if ($Kind -eq "zip") { "zip" } else { "exe" }
$pattern = "Manuskript-*-windows-x64.$ext"
$remoteCleanup = @"
set -eu
cd '$RemoteDir'
chmod 644 '$currentName' '$latestTxtName' '$versionTxtName' 2>/dev/null || true
rm -f Manuskript-windows-x64.exe Manuskript-windows-x64.zip manuskript.json
for f in $pattern; do
    [ -f "`$f" ] || continue
    [ "`$f" = '$currentName' ] && continue
    echo "  Entferne alte Version: `$f"
    rm -f "`$f"
    base=`${f%.*}
    rm -f "`${base}.txt"
done
"@
$lfScript = ($remoteCleanup -replace "`r`n", "`n") -replace "`r", "`n"
$lfScript | ssh -o BatchMode=yes $DeployHost "bash -s"
if ($LASTEXITCODE -ne 0) {
    throw "Aufräumen auf dem Server fehlgeschlagen."
}

Remove-Item -ErrorAction SilentlyContinue $tmpNotes
Write-Host "[OK] Download: https://spoteroxe.de/downloads/$currentName"
Write-Host "     Notes:    https://spoteroxe.de/downloads/$latestTxtName"
Write-Host "     Version $Version, $sizeMb MB"
