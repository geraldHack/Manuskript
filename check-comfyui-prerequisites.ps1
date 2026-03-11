#!/usr/bin/env pwsh

# ComfyUI Voraussetzungen-Check (PowerShell)
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "ComfyUI Voraussetzungen-Check" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""

# Python Check
Write-Host "1/4 Python wird ueberprueft..." -ForegroundColor Yellow
$pythonOk = $false
try {
    $pythonVersion = python --version 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "OK Python gefunden: $pythonVersion" -ForegroundColor Green
        
        $versionCheck = python -c "import sys; exit(0 if sys.version_info >= (3, 10) else 1)" 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "OK Python Version OK" -ForegroundColor Green
            $pythonOk = $true
        } else {
            Write-Host "X Python Version zu alt (benoetigt 3.10+)" -ForegroundColor Red
            Write-Host "   Gefunden: $pythonVersion" -ForegroundColor Red
        }
    } else {
        Write-Host "X Python nicht gefunden" -ForegroundColor Red
        Write-Host "   Bitte installieren Sie Python 3.10+ von https://www.python.org/downloads/" -ForegroundColor Red
        Write-Host "   Wichtig: Add Python to PATH aktivieren!" -ForegroundColor Red
    }
} catch {
    Write-Host "X Python nicht gefunden" -ForegroundColor Red
    Write-Host "   Bitte installieren Sie Python 3.10+ von https://www.python.org/downloads/" -ForegroundColor Red
}
Write-Host ""

# Git Check
Write-Host "2/4 Git wird ueberprueft..." -ForegroundColor Yellow
$gitOk = $false
try {
    $gitVersion = git --version 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "OK Git gefunden: $gitVersion" -ForegroundColor Green
        $gitOk = $true
    } else {
        Write-Host "X Git nicht gefunden" -ForegroundColor Red
        Write-Host "   Bitte installieren Sie Git von https://git-scm.com/download/win" -ForegroundColor Red
    }
} catch {
    Write-Host "X Git nicht gefunden" -ForegroundColor Red
    Write-Host "   Bitte installieren Sie Git von https://git-scm.com/download/win" -ForegroundColor Red
}
Write-Host ""

# Pip Check
Write-Host "3/4 Pip wird ueberprueft..." -ForegroundColor Yellow
$pipOk = $false
try {
    $pipVersion = pip --version 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "OK Pip gefunden: $pipVersion" -ForegroundColor Green
        $pipOk = $true
    } else {
        Write-Host "X Pip nicht gefunden" -ForegroundColor Red
        Write-Host "   Pip sollte mit Python installiert werden" -ForegroundColor Red
    }
} catch {
    Write-Host "X Pip nicht gefunden" -ForegroundColor Red
    Write-Host "   Pip sollte mit Python installiert werden" -ForegroundColor Red
}
Write-Host ""

# ComfyUI Check
Write-Host "4/4 ComfyUI wird ueberprueft..." -ForegroundColor Yellow
$comfyuiOk = 0
$comfyuiPath = "$env:USERPROFILE\ComfyUI"
if (Test-Path $comfyuiPath) {
    Write-Host "OK ComfyUI Ordner gefunden: $comfyuiPath" -ForegroundColor Green
    
    $mainPyPath = Join-Path $comfyuiPath "main.py"
    if (Test-Path $mainPyPath) {
        Write-Host "OK ComfyUI Hauptdatei gefunden" -ForegroundColor Green
        
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:8188" -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
            Write-Host "OK ComfyUI laeuft auf http://127.0.0.1:8188" -ForegroundColor Green
            $comfyuiOk = 2
        } catch {
            Write-Host "! ComfyUI installiert aber nicht gestartet" -ForegroundColor Yellow
            Write-Host "   Starten Sie ComfyUI mit: python $comfyuiPath\main.py" -ForegroundColor Yellow
            $comfyuiOk = 1
        }
    } else {
        Write-Host "X ComfyUI Hauptdatei nicht gefunden" -ForegroundColor Red
    }
} else {
    Write-Host "X ComfyUI nicht installiert" -ForegroundColor Red
    Write-Host "   Download: https://download.comfy.org/windows/nsis/x64" -ForegroundColor Red
}
Write-Host ""

# Zusammenfassung
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "ZUSAMMENFASSUNG" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

if ($pythonOk) {
    Write-Host "OK Python: OK" -ForegroundColor Green
} else {
    Write-Host "X Python: FEHLT" -ForegroundColor Red
}

if ($gitOk) {
    Write-Host "OK Git: OK" -ForegroundColor Green
} else {
    Write-Host "X Git: FEHLT" -ForegroundColor Red
}

if ($pipOk) {
    Write-Host "OK Pip: OK" -ForegroundColor Green
} else {
    Write-Host "X Pip: FEHLT" -ForegroundColor Red
}

if ($comfyuiOk -eq 2) {
    Write-Host "OK ComfyUI: LAEUFT" -ForegroundColor Green
} elseif ($comfyuiOk -eq 1) {
    Write-Host "! ComfyUI: INSTALLIERT (nicht gestartet)" -ForegroundColor Yellow
} else {
    Write-Host "X ComfyUI: FEHLT" -ForegroundColor Red
}

Write-Host ""

# Empfehlungen
if (-not $pythonOk) {
    Write-Host "INFO EMPFEHLUNG: Python installieren" -ForegroundColor Magenta
    Write-Host "   https://www.python.org/downloads/" -ForegroundColor Magenta
    Write-Host ""
}

if (-not $gitOk) {
    Write-Host "INFO EMPFEHLUNG: Git installieren" -ForegroundColor Magenta
    Write-Host "   https://git-scm.com/download/win" -ForegroundColor Magenta
    Write-Host ""
}

if ($comfyuiOk -eq 0) {
    Write-Host "INFO EMPFEHLUNG: ComfyUI installieren" -ForegroundColor Magenta
    Write-Host "   https://download.comfy.org/windows/nsis/x64" -ForegroundColor Magenta
    Write-Host ""
}

if ($pythonOk -and $gitOk -and $pipOk -and $comfyuiOk -ge 1) {
    Write-Host "SUCCESS ALLES BEREIT FUER COMFYUI" -ForegroundColor Green
    if ($comfyuiOk -eq 1) {
        Write-Host "   Starten Sie ComfyUI mit: python $comfyuiPath\main.py" -ForegroundColor Green
    }
} else {
    Write-Host "WARNING Es werden noch Voraussetzungen benoetigt" -ForegroundColor Yellow
}

Write-Host "==================================================" -ForegroundColor Cyan
