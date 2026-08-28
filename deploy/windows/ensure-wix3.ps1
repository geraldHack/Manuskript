# Findet WiX Toolset 3.x (candle.exe / light.exe) oder laedt portable 3.14-Binaries.
# Schreibt nur den bin-Pfad nach stdout (fuer create-installer.bat).
# JDK 21 jpackage braucht WiX 3; WiX 4/5 haben keine candle/light-EXEs.

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Info([string]$Message) {
    [Console]::Error.WriteLine($Message)
}

function HasWix3Tools([string]$Dir) {
    if ([string]::IsNullOrWhiteSpace($Dir)) { return $false }
    return (Test-Path -LiteralPath (Join-Path $Dir "candle.exe")) -and
           (Test-Path -LiteralPath (Join-Path $Dir "light.exe"))
}

$candidates = New-Object System.Collections.Generic.List[string]

$candle = Get-Command "candle.exe" -ErrorAction SilentlyContinue
if ($candle) {
    $candidates.Add((Split-Path -Parent $candle.Source))
}

$programFilesX86 = ${env:ProgramFiles(x86)}
@(
    (Join-Path $programFilesX86 "WiX Toolset v3.14\bin"),
    (Join-Path $programFilesX86 "WiX Toolset v3.11\bin"),
    (Join-Path $env:ProgramFiles "WiX Toolset v3.14\bin"),
    (Join-Path $env:LOCALAPPDATA "Manuskript\wix3")
) | ForEach-Object {
    if ($_ -and -not $candidates.Contains($_)) { $candidates.Add($_) }
}

foreach ($dir in $candidates) {
    if (HasWix3Tools $dir) {
        Info "[OK] WiX 3 gefunden: $dir"
        Write-Output $dir
        exit 0
    }
}

$dest = Join-Path $env:LOCALAPPDATA "Manuskript\wix3"
$url = "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"
Info "WiX tools nicht gefunden. Lade portable WiX 3.14 nach $dest ..."

New-Item -ItemType Directory -Force -Path $dest | Out-Null
$zip = Join-Path $env:TEMP "wix314-binaries.zip"
Invoke-WebRequest -Uri $url -OutFile $zip
Expand-Archive -Path $zip -DestinationPath $dest -Force
Remove-Item $zip -ErrorAction SilentlyContinue

if (-not (HasWix3Tools $dest)) {
    Info "FEHLER: WiX-Download unvollstaendig (candle.exe/light.exe fehlen)."
    exit 1
}

Info "[OK] WiX 3.14 bereit: $dest"
Write-Output $dest
exit 0
