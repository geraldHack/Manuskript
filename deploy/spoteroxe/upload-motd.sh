#!/usr/bin/env bash
# Lädt deploy/spoteroxe/manuskript-motd.txt nach
# https://spoteroxe.de/downloads/manuskript-motd.txt
#
# Vor dem Upload: id in der Datei erhöhen (sonst sehen Clients die Nachricht nicht erneut).
# Beispiel: id: 2026-09-22-1
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

DEPLOY_HOST="${MANUSKRIPT_DEPLOY_HOST:-spoteroxe.de}"
DEPLOY_PATH="${MANUSKRIPT_DEPLOY_PATH:-/home/gehack/home/downloads}"
LOCAL_FILE="${ROOT_DIR}/deploy/spoteroxe/manuskript-motd.txt"
REMOTE_FILE="${DEPLOY_PATH}/manuskript-motd.txt"

if [[ ! -f "$LOCAL_FILE" ]]; then
    echo "FEHLER: ${LOCAL_FILE} fehlt."
    exit 1
fi

if ! grep -qE '^id:[[:space:]]*.+' "$LOCAL_FILE"; then
    echo "FEHLER: ${LOCAL_FILE} braucht eine Zeile „id: …“ im Header."
    exit 1
fi

echo
echo "[Upload] MOTD nach ${DEPLOY_HOST}:${REMOTE_FILE}"
ssh -o BatchMode=yes -o ConnectTimeout=15 "$DEPLOY_HOST" "mkdir -p '${DEPLOY_PATH}'"
scp -o BatchMode=yes "$LOCAL_FILE" "${DEPLOY_HOST}:${REMOTE_FILE}"
ssh -o BatchMode=yes "$DEPLOY_HOST" "chmod 644 '${REMOTE_FILE}'"

echo "[OK] https://spoteroxe.de/downloads/manuskript-motd.txt"
ID_LINE="$(grep -E '^id:[[:space:]]*.+' "$LOCAL_FILE" | head -1)"
echo "     ${ID_LINE}"
