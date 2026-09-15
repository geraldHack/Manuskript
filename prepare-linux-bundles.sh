#!/usr/bin/env bash
# Pandoc- und FFmpeg-ZIPs für Linux x86_64 erzeugen.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

BUNDLE_TMP="installer-bundle-tmp"
PANDOC_VERSION="3.6.4"
PANDOC_DOWNLOAD_URL="https://github.com/jgm/pandoc/releases/download/${PANDOC_VERSION}/pandoc-${PANDOC_VERSION}-linux-amd64.tar.gz"
FFMPEG_URL="https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-linux-x64.gz"
FFPROBE_URL="https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffprobe-linux-x64.gz"

download_file() {
    local url="$1"
    local dest="$2"
    echo "  Lade $(basename "$dest") ..."
    curl -fL --retry 3 --retry-delay 2 -o "$dest" "$url"
}

echo "Erstelle pandoc/pandoc-linux.zip ..."
mkdir -p pandoc "${BUNDLE_TMP}/pandoc-linux"
if [[ ! -f "${BUNDLE_TMP}/pandoc-linux/download.tar.gz" ]]; then
    download_file "$PANDOC_DOWNLOAD_URL" "${BUNDLE_TMP}/pandoc-linux/download.tar.gz"
fi
tar --no-same-owner -xzf "${BUNDLE_TMP}/pandoc-linux/download.tar.gz" -C "${BUNDLE_TMP}/pandoc-linux"
binary="$(find "${BUNDLE_TMP}/pandoc-linux" -type f -name pandoc | head -n 1 || true)"
if [[ -z "$binary" || ! -f "$binary" ]]; then
    echo "FEHLER: Pandoc-Binary nicht gefunden."
    exit 1
fi
chmod +x "$binary"
rm -f pandoc/pandoc-linux.zip
(cd "$(dirname "$binary")" && zip -q -j "$ROOT_DIR/pandoc/pandoc-linux.zip" pandoc)
echo "[OK] pandoc/pandoc-linux.zip ($(du -h pandoc/pandoc-linux.zip | cut -f1))"

echo "Erstelle ffmpeg/ffmpeg-linux.zip ..."
mkdir -p ffmpeg "${BUNDLE_TMP}/ffmpeg-linux"
if [[ ! -f ffmpeg/ffmpeg-linux.zip ]]; then
    download_file "$FFMPEG_URL" "${BUNDLE_TMP}/ffmpeg-linux/ffmpeg.gz"
    download_file "$FFPROBE_URL" "${BUNDLE_TMP}/ffmpeg-linux/ffprobe.gz"
    gunzip -f "${BUNDLE_TMP}/ffmpeg-linux/ffmpeg.gz"
    gunzip -f "${BUNDLE_TMP}/ffmpeg-linux/ffprobe.gz"
    chmod +x "${BUNDLE_TMP}/ffmpeg-linux/ffmpeg" "${BUNDLE_TMP}/ffmpeg-linux/ffprobe"
    rm -f ffmpeg/ffmpeg-linux.zip
    (cd "${BUNDLE_TMP}/ffmpeg-linux" && zip -q -j "$ROOT_DIR/ffmpeg/ffmpeg-linux.zip" ffmpeg ffprobe)
    echo "[OK] ffmpeg/ffmpeg-linux.zip ($(du -h ffmpeg/ffmpeg-linux.zip | cut -f1))"
else
    echo "[OK] ffmpeg/ffmpeg-linux.zip bereits vorhanden"
fi

echo
echo "Fertig. Bundles liegen in pandoc/ und ffmpeg/."
