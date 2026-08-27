@echo off
REM Startet den Mammouth Monitor aus dem Manuskript-Repo-Root.

set ROOT=%~dp0
cd /d "%ROOT%tools\mammouth-monitor"

call mvn -q javafx:run -Djavafx.args="--config-dir=%ROOT% %*"
