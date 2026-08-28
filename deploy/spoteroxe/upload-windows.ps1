# Lädt Windows-Build nach spoteroxe.de und merged downloads/manuskript.json
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

$stable = if ($Kind -eq "zip") { "Manuskript-windows-x64.zip" } else { "Manuskript-windows-x64.exe" }
$currentName = [System.IO.Path]::GetFileName($File)
$item = Get-Item -LiteralPath $File
$sizeBytes = [int64] $item.Length
$sizeMb = [int] [Math]::Round(($sizeBytes + 524288) / 1MB)
$iso = [DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
$remoteFile = "${RemoteDir}/${currentName}"
$jsLocal = Join-Path $PSScriptRoot "manuskript-download.js"
$htmlLocal = Join-Path $PSScriptRoot "downloads.html"

Write-Host ""
Write-Host "[Upload] ${DeployHost}:${RemoteDir}  ($currentName, $sizeMb MB)"

ssh -o BatchMode=yes -o ConnectTimeout=15 $DeployHost "mkdir -p '$RemoteDir'"
if ($LASTEXITCODE -ne 0) { throw "SSH zu $DeployHost fehlgeschlagen." }

Write-Host "  scp $currentName (kann bei großen Dateien dauern) ..."
scp -o BatchMode=yes $File "${DeployHost}:${remoteFile}"
if ($LASTEXITCODE -ne 0) { throw "scp der Windows-Datei fehlgeschlagen." }

$tmpJson = Join-Path $env:TEMP "manuskript-download.json"
$existing = $null
scp -o BatchMode=yes -q "${DeployHost}:${RemoteDir}/manuskript.json" $tmpJson 2>$null
if ((Test-Path $tmpJson) -and ((Get-Item $tmpJson).Length -gt 2)) {
    try { $existing = Get-Content -Raw -LiteralPath $tmpJson | ConvertFrom-Json } catch { $existing = $null }
}

$windows = [ordered]@{
    version    = $Version
    platform   = "Windows (x64)"
    filename   = $currentName
    url        = "downloads/$stable"
    sizeBytes  = $sizeBytes
    sizeLabel  = "$sizeMb MB"
    updated    = $iso
}

$root = [ordered]@{}
$macos = $null
if ($existing -ne $null) {
    if ($existing.macos) { $macos = $existing.macos }
    elseif ($existing.url -and ("$($existing.platform)" -notmatch "Windows")) { $macos = $existing }
}
if ($macos -ne $null) { $root["macos"] = $macos }
$root["windows"] = $windows
$root["updated"] = $iso

$outJson = Join-Path $env:TEMP "manuskript-download-out.json"
($root | ConvertTo-Json -Depth 6) | Set-Content -Encoding utf8 -LiteralPath $outJson

scp -o BatchMode=yes $outJson "${DeployHost}:${RemoteDir}/manuskript.json"
if ($LASTEXITCODE -ne 0) { throw "scp von manuskript.json fehlgeschlagen." }

if (Test-Path $jsLocal) {
    scp -o BatchMode=yes $jsLocal "${DeployHost}:/home/gehack/home/js/manuskript-download.js"
}
if (Test-Path $htmlLocal) {
    scp -o BatchMode=yes $htmlLocal "${DeployHost}:/home/gehack/home/downloads.html"
}

# Heredoc in PowerShell hat CRLF; bash auf dem Server braucht LF.
# Sonst stirbt `set -euo pipefail` und der stabile Link (Download-URL) fehlt.
$pattern = if ($Kind -eq "zip") { "Manuskript-*-windows-x64.zip" } else { "Manuskript-*-windows-x64.exe" }
$remoteCleanup = @"
set -eu
cd '$RemoteDir'
ln -f '$currentName' '$stable'
for f in $pattern; do
    [ -f "`$f" ] || continue
    [ "`$f" = '$currentName' ] && continue
    echo "  Entferne alte Version: `$f"
    rm -f "`$f"
done
"@
$lfScript = ($remoteCleanup -replace "`r`n", "`n") -replace "`r", "`n"
$lfScript | ssh -o BatchMode=yes $DeployHost "bash -s"
if ($LASTEXITCODE -ne 0) {
    throw "Stabiler Download-Link ($stable) konnte nicht gesetzt werden."
}

Remove-Item -ErrorAction SilentlyContinue $tmpJson, $outJson
Write-Host "[OK] Download aktuell: https://spoteroxe.de/downloads/$stable"
Write-Host "     Version $Version, $sizeMb MB"
