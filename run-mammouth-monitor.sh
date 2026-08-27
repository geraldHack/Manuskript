#!/bin/bash
# Startet den Mammouth Monitor aus dem Manuskript-Repo-Root.

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT/tools/mammouth-monitor"

ARGS=(--config-dir="$ROOT")
if (($# > 0)); then
  ARGS+=("$@")
fi

exec mvn -q javafx:run -Djavafx.args="${ARGS[*]}"
