@echo off
REM Startet den OpenRouter Monitor aus dem Manuskript-Repo-Root.
REM Nutzt mvn javafx:run mit JavaFX 21 (kompatibel zu JDK 21).

set ROOT=%~dp0
cd /d "%ROOT%tools\openrouter-monitor"

call mvn -q javafx:run -Djavafx.args="--config-dir=%ROOT% %*"
