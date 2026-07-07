#!/bin/bash
# Startet den OpenRouter Monitor aus dem Manuskript-Repo-Root.
# Nutzt mvn javafx:run mit JavaFX 21 (kompatibel zu JDK 21).
# Das gebündelte javafx-sdk-26 im Repo erfordert JDK 24+ und wird hier nicht verwendet.

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT/tools/openrouter-monitor"

ARGS=(--config-dir="$ROOT")
if (($# > 0)); then
  ARGS+=("$@")
fi

# javafx.args: Anwendungsargumente an OpenRouterMonitorApp
exec mvn -q javafx:run -Djavafx.args="${ARGS[*]}"
