#!/usr/bin/env bash
# Startet Manuskript mit dem zuletzt kompilierten Code (target/classes).
# Arbeitsverzeichnis = Projektwurzel (config/, logs/).
# Kein festes DISPLAY=:1 – Wayland-Session bleibt unangetastet.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

if ! command -v mvn >/dev/null 2>&1; then
    echo "FEHLER: Maven (mvn) nicht gefunden."
    exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
        JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
        export JAVA_HOME
    fi
fi

echo "Kompiliere und starte Manuskript (Entwicklermodus)..."
exec mvn compile javafx:run
