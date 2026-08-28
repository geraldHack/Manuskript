# Erzeugt installer-assets\Manuskript.ico aus der 1024px-PNG (wie build-mac-icon.sh fuer .icns).
# jpackage auf Windows akzeptiert nur .ico, nicht PNG.

$ErrorActionPreference = "Stop"

function Info([string]$Message) {
    [Console]::Error.WriteLine($Message)
}

function Write-UInt16([System.IO.Stream]$Stream, [int]$Value) {
    $Stream.WriteByte($Value -band 0xFF)
    $Stream.WriteByte(($Value -shr 8) -band 0xFF)
}

function Write-UInt32([System.IO.Stream]$Stream, [int]$Value) {
    $Stream.WriteByte($Value -band 0xFF)
    $Stream.WriteByte(($Value -shr 8) -band 0xFF)
    $Stream.WriteByte(($Value -shr 16) -band 0xFF)
    $Stream.WriteByte(($Value -shr 24) -band 0xFF)
}

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$src = Join-Path $root "installer-assets\manuskript-app-icon-1024.png"
$ico = Join-Path $root "installer-assets\Manuskript.ico"

if (-not (Test-Path -LiteralPath $src)) {
    throw "Quell-Icon fehlt: $src"
}

if ((Test-Path -LiteralPath $ico) -and ((Get-Item -LiteralPath $ico).LastWriteTimeUtc -ge (Get-Item -LiteralPath $src).LastWriteTimeUtc)) {
    Info "[OK] Windows-Icon aktuell: $ico"
    exit 0
}

Add-Type -AssemblyName System.Drawing

$srcImage = [System.Drawing.Bitmap]::FromFile((Resolve-Path -LiteralPath $src).Path)
$sizes = @(16, 24, 32, 48, 64, 128, 256)
$frames = New-Object System.Collections.Generic.List[byte[]]
try {
    foreach ($size in $sizes) {
        $bmp = New-Object System.Drawing.Bitmap $size, $size
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        try {
            $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
            $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $g.Clear([System.Drawing.Color]::Transparent)
            $g.DrawImage($srcImage, 0, 0, $size, $size)
        } finally {
            $g.Dispose()
        }
        $ms = New-Object System.IO.MemoryStream
        $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
        $frames.Add($ms.ToArray())
        $ms.Dispose()
        $bmp.Dispose()
    }
} finally {
    $srcImage.Dispose()
}

$fs = [System.IO.File]::Create($ico)
try {
    Write-UInt16 $fs 0
    Write-UInt16 $fs 1
    Write-UInt16 $fs $frames.Count
    $offset = 6 + (16 * $frames.Count)
    for ($i = 0; $i -lt $frames.Count; $i++) {
        $size = $sizes[$i]
        $data = $frames[$i]
        $dim = if ($size -ge 256) { 0 } else { $size }
        $fs.WriteByte($dim)
        $fs.WriteByte($dim)
        $fs.WriteByte(0)
        $fs.WriteByte(0)
        Write-UInt16 $fs 1
        Write-UInt16 $fs 32
        Write-UInt32 $fs $data.Length
        Write-UInt32 $fs $offset
        $offset += $data.Length
    }
    foreach ($data in $frames) {
        $fs.Write($data, 0, $data.Length)
    }
} finally {
    $fs.Dispose()
}

Info "[OK] Windows-Icon erstellt: $ico"
exit 0
