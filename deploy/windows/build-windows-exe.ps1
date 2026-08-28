param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$AppImage,
    [Parameter(Mandatory = $true)][string]$AppName,
    [Parameter(Mandatory = $true)][string]$AppVersion,
    [Parameter(Mandatory = $true)][string]$Dest,
    [string]$Icon = ""
)

$ErrorActionPreference = "Stop"
$jpackage = Join-Path $JavaHome "bin\jpackage.exe"
if (-not (Test-Path -LiteralPath $jpackage)) {
    [Console]::Error.WriteLine("jpackage nicht gefunden: $jpackage")
    exit 1
}

$jpkgArgs = @(
    "--type", "exe",
    "--app-image", $AppImage,
    "--name", $AppName,
    "--app-version", $AppVersion,
    "--vendor", "Manuskript",
    "--win-dir-chooser",
    "--win-menu",
    "--win-shortcut",
    "--win-per-user-install",
    "--dest", $Dest
)
if ($Icon -and (Test-Path -LiteralPath $Icon)) {
    $jpkgArgs += @("--icon", (Resolve-Path -LiteralPath $Icon).Path)
    Write-Host "  Icon: $Icon"
} else {
    Write-Host "  WARNUNG: Kein .ico gefunden - Setup-EXE bekommt das Java-Standard-Icon."
}

Write-Host ("  {0} {1}" -f $jpackage, ($jpkgArgs -join " "))
& $jpackage @jpkgArgs
exit $LASTEXITCODE
