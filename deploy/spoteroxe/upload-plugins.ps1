# Optional: lädt gebaute Plugin-JARs plus gleichnamige .txt nach
# https://spoteroxe.de/downloads/plugins/
# Kein JSON-Index. Die Dateien kannst du auch von Hand kopieren.
param(
    [string] $DeployHost = $(if ($env:MANUSKRIPT_DEPLOY_HOST) { $env:MANUSKRIPT_DEPLOY_HOST } else { "spoteroxe.de" }),
    [string] $RemoteDir = $(if ($env:MANUSKRIPT_DEPLOY_PATH) { $env:MANUSKRIPT_DEPLOY_PATH } else { "/home/gehack/home/downloads" })
)

$ErrorActionPreference = "Stop"
$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$RemotePlugins = "$RemoteDir/plugins"

function Get-PomVersion([string] $PomPath) {
    [xml] $pom = Get-Content -LiteralPath $PomPath
    $ns = New-Object System.Xml.XmlNamespaceManager($pom.NameTable)
    $ns.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
    $ver = $pom.SelectSingleNode("/m:project/m:version", $ns)
    if (-not $ver -or [string]::IsNullOrWhiteSpace($ver.InnerText)) {
        throw "keine <version> in $PomPath"
    }
    return $ver.InnerText.Trim()
}

function Upload-PluginPair([string] $Tool) {
    $jar = Join-Path $RootDir "tools\$Tool\target\$Tool.jar"
    $version = Get-PomVersion (Join-Path $RootDir "tools\$Tool\pom.xml")
    $remoteJar = "$Tool-$version.jar"
    $notes = Join-Path $RootDir "tools\$Tool\target\$Tool-$version.txt"
    $latest = Join-Path $RootDir "tools\$Tool\target\$Tool.txt"
    if (-not (Test-Path -LiteralPath $jar)) {
        throw "JAR fehlt: $jar — zuerst tools\$Tool : mvn package"
    }
    if (-not (Test-Path -LiteralPath $notes)) {
        throw "TXT fehlt: $notes — mvn package muss die .txt neben die JAR legen."
    }
    scp -o BatchMode=yes $jar "${DeployHost}:${RemotePlugins}/${remoteJar}"
    if ($LASTEXITCODE -ne 0) { throw "scp $remoteJar fehlgeschlagen." }
    scp -o BatchMode=yes $notes "${DeployHost}:${RemotePlugins}/$Tool-$version.txt"
    if ($LASTEXITCODE -ne 0) { throw "scp $Tool-$version.txt fehlgeschlagen." }
    if (Test-Path -LiteralPath $latest) {
        scp -o BatchMode=yes $latest "${DeployHost}:${RemotePlugins}/$Tool.txt"
        if ($LASTEXITCODE -ne 0) { throw "scp $Tool.txt fehlgeschlagen." }
    }
    ssh -o BatchMode=yes $DeployHost "chmod 644 '$RemotePlugins/$remoteJar' '$RemotePlugins/$Tool-$version.txt' '$RemotePlugins/$Tool.txt'"
    if ($LASTEXITCODE -ne 0) { throw "chmod auf dem Server fehlgeschlagen." }
    Write-Host "     $remoteJar"
    Write-Host "     $Tool-$version.txt"
    Write-Host "     $Tool.txt"
}

Write-Host ""
Write-Host "[Upload] Plugins (JAR + TXT) nach ${DeployHost}:${RemotePlugins}"
ssh -o BatchMode=yes -o ConnectTimeout=15 $DeployHost "mkdir -p '$RemotePlugins'"
if ($LASTEXITCODE -ne 0) { throw "SSH zu $DeployHost fehlgeschlagen." }

Upload-PluginPair openrouter-monitor
Upload-PluginPair mammouth-monitor
Upload-PluginPair projekt-backup
Upload-PluginPair schreib-statistik

Write-Host "[OK] https://spoteroxe.de/downloads/plugins/"
Write-Host "     Verzeichnislisting muss öffentlich sein (Apache Options Indexes)."
