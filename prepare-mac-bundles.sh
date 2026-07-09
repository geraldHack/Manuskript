#!/usr/bin/env bash
# Pandoc- und FFmpeg-ZIPs für macOS (arm64) erzeugen – ohne vollen Installer-Build.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

if [[ "$(uname -m)" != "arm64" ]]; then
    echo "FEHLER: Nur Apple Silicon (arm64) unterstützt."
    exit 1
fi

BUNDLE_TMP="installer-bundle-tmp"
PANDOC_VERSION="3.6.4"
PANDOC_DOWNLOAD_URL="https://github.com/jgm/pandoc/releases/download/${PANDOC_VERSION}/pandoc-${PANDOC_VERSION}-arm64-macOS.zip"
FFMPEG_URL="https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-darwin-arm64.gz"
FFPROBE_URL="https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffprobe-darwin-arm64.gz"

download_file() {
    local url="$1"
    local dest="$2"
    echo "  Lade $(basename "$dest") ..."
    curl -fL --retry 3 --retry-delay 2 -o "$dest" "$url"
}

echo "Erstelle pandoc/pandoc-mac.zip ..."
mkdir -p pandoc "${BUNDLE_TMP}/pandoc-src"
if [[ ! -f "${BUNDLE_TMP}/pandoc-src/download.zip" ]]; then
    download_file "$PANDOC_DOWNLOAD_URL" "${BUNDLE_TMP}/pandoc-src/download.zip"
fi
unzip -q -o "${BUNDLE_TMP}/pandoc-src/download.zip" -d "${BUNDLE_TMP}/pandoc-src"
binary="$(find "${BUNDLE_TMP}/pandoc-src" -type f -name pandoc | head -n 1 || true)"
if [[ -z "$binary" || ! -f "$binary" ]]; then
    echo "FEHLER: Pandoc-Binary nicht gefunden."
    exit 1
fi
chmod +x "$binary"
rm -f pandoc/pandoc-mac.zip
(cd "$(dirname "$binary")" && zip -q -j "$ROOT_DIR/pandoc/pandoc-mac.zip" pandoc)
echo "[OK] pandoc/pandoc-mac.zip ($(du -h pandoc/pandoc-mac.zip | cut -f1))"

echo "Erstelle ffmpeg/ffmpeg-mac.zip ..."
mkdir -p ffmpeg "${BUNDLE_TMP}/ffmpeg-src"
if [[ ! -f ffmpeg/ffmpeg-mac.zip ]]; then
    download_file "$FFMPEG_URL" "${BUNDLE_TMP}/ffmpeg-src/ffmpeg.gz"
    download_file "$FFPROBE_URL" "${BUNDLE_TMP}/ffmpeg-src/ffprobe.gz"
    gunzip -f "${BUNDLE_TMP}/ffmpeg-src/ffmpeg.gz"
    gunzip -f "${BUNDLE_TMP}/ffmpeg-src/ffprobe.gz"
    chmod +x "${BUNDLE_TMP}/ffmpeg-src/ffmpeg" "${BUNDLE_TMP}/ffmpeg-src/ffprobe"
    rm -f ffmpeg/ffmpeg-mac.zip
    (cd "${BUNDLE_TMP}/ffmpeg-src" && zip -q -j "$ROOT_DIR/ffmpeg/ffmpeg-mac.zip" ffmpeg ffprobe)
    echo "[OK] ffmpeg/ffmpeg-mac.zip ($(du -h ffmpeg/ffmpeg-mac.zip | cut -f1))"
else
    echo "[OK] ffmpeg/ffmpeg-mac.zip bereits vorhanden"
fi

echo
echo "Fertig. Bundles liegen in pandoc/ und ffmpeg/."
