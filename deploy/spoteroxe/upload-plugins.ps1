# Lädt offizielle Plugin-JARs und manuskript-plugins.json nach spoteroxe.de.
param(
    [string] $DeployHost = $(if ($env:MANUSKRIPT_DEPLOY_HOST) { $env:MANUSKRIPT_DEPLOY_HOST } else { "spoteroxe.de" }),
    [string] $RemoteDir = $(if ($env:MANUSKRIPT_DEPLOY_PATH) { $env:MANUSKRIPT_DEPLOY_PATH } else { "/home/gehack/home/downloads" })
)

$ErrorActionPreference = "Stop"
$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$OrJar = Join-Path $RootDir "tools\openrouter-monitor\target\openrouter-monitor.jar"
$MmJar = Join-Path $RootDir "tools\mammouth-monitor\target\mammouth-monitor.jar"
if (-not (Test-Path -LiteralPath $OrJar)) {
    throw "JAR fehlt: $OrJar — zuerst tools\openrouter-monitor: mvn package"
}
if (-not (Test-Path -LiteralPath $MmJar)) {
    throw "JAR fehlt: $MmJar — zuerst tools\mammouth-monitor: mvn package"
}

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

$OrVersion = Get-PomVersion (Join-Path $RootDir "tools\openrouter-monitor\pom.xml")
$MmVersion = Get-PomVersion (Join-Path $RootDir "tools\mammouth-monitor\pom.xml")
$OrName = "openrouter-monitor-$OrVersion.jar"
$MmName = "mammouth-monitor-$MmVersion.jar"
$OrSha = (Get-FileHash -LiteralPath $OrJar -Algorithm SHA256).Hash.ToLowerInvariant()
$MmSha = (Get-FileHash -LiteralPath $MmJar -Algorithm SHA256).Hash.ToLowerInvariant()
$Iso = [DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
$Base = "https://spoteroxe.de/downloads/plugins"
$RemotePlugins = "$RemoteDir/plugins"

$doc = [ordered]@{
    updated = $Iso
    plugins = @(
        [ordered]@{
            id          = "openrouter-monitor"
            label       = "OpenRouter-Monitor"
            version     = $OrVersion
            description = "Credits und API-Logs für OpenRouter."
            fileName    = "openrouter-monitor.jar"
            jar         = "$Base/$OrName"
            sha256      = $OrSha
            requires    = "2.1.70"
        },
        [ordered]@{
            id          = "mammouth-monitor"
            label       = "Mammouth-Monitor"
            version     = $MmVersion
            description = "Credits und Modellliste für Mammouth."
            fileName    = "mammouth-monitor.jar"
            jar         = "$Base/$MmName"
            sha256      = $MmSha
            requires    = "2.1.70"
        }
    )
}

$jsonPath = Join-Path $env:TEMP "manuskript-plugins.json"
($doc | ConvertTo-Json -Depth 6) | Set-Content -Encoding utf8 -LiteralPath $jsonPath

Write-Host ""
Write-Host "[Upload] Plugin-Katalog nach ${DeployHost}:${RemotePlugins}"
ssh -o BatchMode=yes -o ConnectTimeout=15 $DeployHost "mkdir -p '$RemotePlugins'"
if ($LASTEXITCODE -ne 0) { throw "SSH zu $DeployHost fehlgeschlagen." }

scp -o BatchMode=yes $OrJar "${DeployHost}:${RemotePlugins}/${OrName}"
if ($LASTEXITCODE -ne 0) { throw "scp OpenRouter-JAR fehlgeschlagen." }
scp -o BatchMode=yes $MmJar "${DeployHost}:${RemotePlugins}/${MmName}"
if ($LASTEXITCODE -ne 0) { throw "scp Mammouth-JAR fehlgeschlagen." }
scp -o BatchMode=yes $jsonPath "${DeployHost}:${RemoteDir}/manuskript-plugins.json"
if ($LASTEXITCODE -ne 0) { throw "scp JSON fehlgeschlagen." }
ssh -o BatchMode=yes $DeployHost "chmod 644 '$RemoteDir/manuskript-plugins.json' '$RemotePlugins/$OrName' '$RemotePlugins/$MmName'"
if ($LASTEXITCODE -ne 0) { throw "chmod auf dem Server fehlgeschlagen." }

Remove-Item -ErrorAction SilentlyContinue $jsonPath
Write-Host "[OK] https://spoteroxe.de/downloads/manuskript-plugins.json"
Write-Host "     $OrName  $OrSha"
Write-Host "     $MmName  $MmSha"
