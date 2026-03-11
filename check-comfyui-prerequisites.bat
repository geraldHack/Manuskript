@echo off
setlocal enabledelayedexpansion

echo ==================================================
echo ComfyUI Voraussetzungen-Check
echo ==================================================
echo.

:: Initialize variables
set PYTHON_OK=0
set GIT_OK=0
set PIP_OK=0
set COMFYUI_OK=0

:: Python Check
echo [1/4] Python wird ueberprueft...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [X] Python nicht gefunden
    echo    Bitte installieren Sie Python 3.10+ von https://www.python.org/downloads/
    echo    Wichtig: "Add Python to PATH" aktivieren!
) else (
    for /f "tokens=2" %%i in ('python --version 2^>^&1') do set PYTHON_VERSION=%%i
    echo [OK] Python gefunden: %PYTHON_VERSION%
    
    :: Check Python Version
    python -c "import sys; exit(0 if sys.version_info >= (3, 10) else 1)" >nul 2>&1
    if %errorlevel% neq 0 (
        echo [X] Python Version zu alt (benoetigt 3.10+)
        echo    Gefunden: %PYTHON_VERSION%
    ) else (
        echo [OK] Python Version OK
        set PYTHON_OK=1
    )
)
echo.

:: Git Check
echo [2/4] Git wird ueberprueft...
git --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [X] Git nicht gefunden
    echo    Bitte installieren Sie Git von https://git-scm.com/download/win
) else (
    for /f "tokens=3" %%i in ('git --version 2^>^&1') do set GIT_VERSION=%%i
    echo [OK] Git gefunden: %GIT_VERSION%
    set GIT_OK=1
)
echo.

:: Pip Check
echo [3/4] Pip wird ueberprueft...
pip --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [X] Pip nicht gefunden
    echo    Pip sollte mit Python installiert werden
) else (
    for /f "tokens=2" %%i in ('pip --version 2^>^&1') do set PIP_VERSION=%%i
    echo [OK] Pip gefunden: %PIP_VERSION%
    set PIP_OK=1
)
echo.

:: ComfyUI Check
echo [4/4] ComfyUI wird ueberprueft...
if exist "%USERPROFILE%\ComfyUI" (
    echo [OK] ComfyUI Ordner gefunden: %USERPROFILE%\ComfyUI
    
    if exist "%USERPROFILE%\ComfyUI\main.py" (
        echo [OK] ComfyUI Hauptdatei gefunden
        
        :: Check if ComfyUI is running (simple HTTP check)
        powershell -Command "try { Invoke-WebRequest -Uri 'http://127.0.0.1:8188' -TimeoutSec 2 -UseBasicParsing | Out-Null; exit 0 } catch { exit 1 }" >nul 2>&1
        if %errorlevel% equ 0 (
            echo [OK] ComfyUI laeuft auf http://127.0.0.1:8188
            set COMFYUI_OK=2
        ) else (
            echo [!] ComfyUI installiert aber nicht gestartet
            echo    Starten Sie ComfyUI mit: python "%USERPROFILE%\ComfyUI\main.py"
            set COMFYUI_OK=1
        )
    ) else (
        echo [X] ComfyUI Hauptdatei nicht gefunden
    )
) else (
    echo [X] ComfyUI nicht installiert
    echo    Download: https://download.comfy.org/windows/nsis/x64
)
echo.

:: Zusammenfassung
echo ==================================================
echo ZUSAMMENFASSUNG
echo ==================================================
if %PYTHON_OK% equ 1 (
    echo [OK] Python: OK
) else (
    echo [X] Python: FEHLT
)

if %GIT_OK% equ 1 (
    echo [OK] Git: OK
) else (
    echo [X] Git: FEHLT
)

if %PIP_OK% equ 1 (
    echo [OK] Pip: OK
) else (
    echo [X] Pip: FEHLT
)

if %COMFYUI_OK% equ 2 (
    echo [OK] ComfyUI: LAEUFT
) else if %COMFYUI_OK% equ 1 (
    echo [!] ComfyUI: INSTALLIERT (nicht gestartet)
) else (
    echo [X] ComfyUI: FEHLT
)

echo.

:: Empfehlungen
if %PYTHON_OK% equ 0 (
    echo [INFO] EMPFEHLUNG: Python installieren
    echo    https://www.python.org/downloads/
    echo.
)

if %GIT_OK% equ 0 (
    echo [INFO] EMPFEHLUNG: Git installieren
    echo    https://git-scm.com/download/win
    echo.
)

if %COMFYUI_OK% equ 0 (
    echo [INFO] EMPFEHLUNG: ComfyUI installieren
    echo    https://download.comfy.org/windows/nsis/x64
    echo.
)

if %PYTHON_OK% equ 1 if %GIT_OK% equ 1 if %PIP_OK% equ 1 if %COMFYUI_OK% geq 1 (
    echo [SUCCESS] ALLES BEREIT FUER COMFYUI!
    if %COMFYUI_OK% equ 1 (
        echo    Starten Sie ComfyUI mit: python "%USERPROFILE%\ComfyUI\main.py"
    )
) else (
    echo [WARNING] Es werden noch Voraussetzungen benoetigt
)

echo ==================================================
pause
