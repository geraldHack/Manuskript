#!/usr/bin/env bash
# Lädt Linux-Pakete (.deb, AppImage, Arch) nach spoteroxe.de.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

DEPLOY_HOST="${MANUSKRIPT_DEPLOY_HOST:-spoteroxe.de}"
DEPLOY_PATH="${MANUSKRIPT_DEPLOY_PATH:-/home/gehack/home/downloads}"
VERSION_FILE="src/main/resources/manuskript.version"
APP_VERSION="$(tr -d '[:space:]' < "$VERSION_FILE")"
OUTPUT_DIR="${ROOT_DIR}/installer-output"
RELEASE_NOTES="${ROOT_DIR}/deploy/spoteroxe/manuskript-release-notes.txt"
JS_FILE="${ROOT_DIR}/deploy/spoteroxe/manuskript-download.js"

human_size_mb() {
    local bytes="$1"
    echo $(( (bytes + 524288) / 1048576 ))
}

file_size_bytes() {
    if stat -f%z "$1" >/dev/null 2>&1; then
        stat -f%z "$1"
    else
        stat -c%s "$1"
    fi
}

write_notes() {
    local version="$1"
    local platform="$2"
    local size_mb="$3"
    local filename="$4"
    local dest="$5"
    {
        printf '%s\n' \
            "Manuskript" \
            "${version}" \
            "" \
            "${platform}" \
            "${size_mb} MB" \
            "${filename}"
        if [[ -f "$RELEASE_NOTES" ]]; then
            cat "$RELEASE_NOTES"
            [[ "$(tail -c1 "$RELEASE_NOTES")" == $'\n' ]] || printf '\n'
        fi
    } > "$dest"
}

upload_one() {
    local file="$1"
    local platform="$2"
    local latest_txt="$3"
    local version_txt="$4"
    if [[ ! -f "$file" ]]; then
        echo "FEHLER: Datei fehlt: ${file}"
        return 1
    fi
    local name size_bytes size_mb notes
    name="$(basename "$file")"
    size_bytes="$(file_size_bytes "$file")"
    size_mb="$(human_size_mb "$size_bytes")"
    notes="$(mktemp -t manuskript-linux)"
    write_notes "$APP_VERSION" "$platform" "$size_mb" "$name" "$notes"

    echo "  scp ${name} (${size_mb} MB, kann dauern) ..."
    scp -o BatchMode=yes "$file" "${DEPLOY_HOST}:${DEPLOY_PATH}/${name}"
    scp -o BatchMode=yes "$notes" "${DEPLOY_HOST}:${DEPLOY_PATH}/${latest_txt}"
    scp -o BatchMode=yes "$notes" "${DEPLOY_HOST}:${DEPLOY_PATH}/${version_txt}"
    rm -f "$notes"

    ssh -o BatchMode=yes "$DEPLOY_HOST" \
        "chmod 644 '${DEPLOY_PATH}/${name}' '${DEPLOY_PATH}/${latest_txt}' '${DEPLOY_PATH}/${version_txt}'"
    echo "[OK] https://spoteroxe.de/downloads/${name}"
    echo "     https://spoteroxe.de/downloads/${latest_txt}"
}

echo
echo "[Upload] Linux ${APP_VERSION} nach ${DEPLOY_HOST}:${DEPLOY_PATH}"
if ! ssh -o BatchMode=yes -o ConnectTimeout=15 "$DEPLOY_HOST" "mkdir -p '${DEPLOY_PATH}'"; then
    echo "FEHLER: SSH zu ${DEPLOY_HOST} fehlgeschlagen."
    exit 1
fi

DEB="${OUTPUT_DIR}/Manuskript-${APP_VERSION}-linux-x64.deb"
APPIMAGE="${OUTPUT_DIR}/Manuskript-${APP_VERSION}-linux-x64.AppImage"
ARCH="${OUTPUT_DIR}/Manuskript-${APP_VERSION}-linux-x64.pkg.tar.zst"

upload_one "$DEB" "Linux (Debian/Ubuntu, x64)" \
    "Manuskript-linux-x64-deb.txt" \
    "Manuskript-${APP_VERSION}-linux-x64-deb.txt"
upload_one "$APPIMAGE" "Linux (AppImage, x64)" \
    "Manuskript-linux-x64-appimage.txt" \
    "Manuskript-${APP_VERSION}-linux-x64-appimage.txt"
upload_one "$ARCH" "Linux (Arch, x64)" \
    "Manuskript-linux-x64-arch.txt" \
    "Manuskript-${APP_VERSION}-linux-x64-arch.txt"

if [[ -f "$JS_FILE" ]]; then
    scp -o BatchMode=yes "$JS_FILE" "${DEPLOY_HOST}:/home/gehack/home/js/manuskript-download.js"
fi
# downloads.html nicht überschreiben – dort stehen auch andere Projekte (DeltaBlade, …).

ssh -o BatchMode=yes "$DEPLOY_HOST" \
    "DEPLOY_PATH='${DEPLOY_PATH}' CURRENT_DEB='$(basename "$DEB")' CURRENT_APPIMAGE='$(basename "$APPIMAGE")' CURRENT_ARCH='$(basename "$ARCH")' bash -s" <<'REMOTE'
set -euo pipefail
cd "$DEPLOY_PATH"
for f in Manuskript-*-linux-x64.deb; do
    [[ -f "$f" ]] || continue
    [[ "$f" == "$CURRENT_DEB" ]] && continue
    echo "  Entferne alte Version: $f"
    rm -f "$f" "${f%.deb}-deb.txt"
done
for f in Manuskript-*-linux-x64.AppImage; do
    [[ -f "$f" ]] || continue
    [[ "$f" == "$CURRENT_APPIMAGE" ]] && continue
    echo "  Entferne alte Version: $f"
    rm -f "$f" "${f%.AppImage}-appimage.txt"
done
for f in Manuskript-*-linux-x64.pkg.tar.zst; do
    [[ -f "$f" ]] || continue
    [[ "$f" == "$CURRENT_ARCH" ]] && continue
    echo "  Entferne alte Version: $f"
    rm -f "$f" "${f%.pkg.tar.zst}-arch.txt"
done
REMOTE

echo "[OK] Linux-Pakete auf https://spoteroxe.de/downloads.html"
