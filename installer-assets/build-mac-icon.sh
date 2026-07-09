#!/usr/bin/env bash
# Erzeugt installer-assets/Manuskript.icns aus der 1024px-Quelldatei.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${ROOT_DIR}/installer-assets/manuskript-app-icon-1024.png"
ICONSET="${ROOT_DIR}/installer-assets/Manuskript.iconset"
ICNS="${ROOT_DIR}/installer-assets/Manuskript.icns"

if [[ ! -f "$SRC" ]]; then
    echo "FEHLER: Quelldatei fehlt: $SRC"
    exit 1
fi

if [[ -f "$ICNS" && "$ICNS" -nt "$SRC" ]]; then
    echo "[OK] $ICNS ist aktuell."
    exit 0
fi

echo "Erstelle macOS App-Icon: $ICNS"
rm -rf "$ICONSET"
mkdir -p "$ICONSET"

for size in 16 32 128 256 512; do
    sips -z "$size" "$size" "$SRC" --out "${ICONSET}/icon_${size}x${size}.png" >/dev/null
    sips -z $((size * 2)) $((size * 2)) "$SRC" --out "${ICONSET}/icon_${size}x${size}@2x.png" >/dev/null
done

xattr -cr "$ICONSET" 2>/dev/null || true
iconutil -c icns "$ICONSET" -o "$ICNS"
rm -rf "$ICONSET"
echo "[OK] $ICNS"
