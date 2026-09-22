#!/usr/bin/env bash
# Lädt die Discharge-Chat-API nach spoteroxe.de/chat/
# Erwartung: Document-Root-Pfad /home/gehack/home/chat → https://spoteroxe.de/chat/
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

DEPLOY_HOST="${MANUSKRIPT_DEPLOY_HOST:-spoteroxe.de}"
DEPLOY_PATH="${MANUSKRIPT_CHAT_DEPLOY_PATH:-/home/gehack/home/chat}"
LOCAL_DIR="${ROOT_DIR}/deploy/spoteroxe/chat"

if [[ ! -f "${LOCAL_DIR}/index.php" || ! -f "${LOCAL_DIR}/lib.php" ]]; then
    echo "FEHLER: ${LOCAL_DIR}/index.php oder lib.php fehlt."
    exit 1
fi

echo
echo "[Upload] Discharge-Chat-API nach ${DEPLOY_HOST}:${DEPLOY_PATH}"
ssh -o BatchMode=yes -o ConnectTimeout=15 "$DEPLOY_HOST" "mkdir -p '${DEPLOY_PATH}/data'"
scp -o BatchMode=yes \
    "${LOCAL_DIR}/index.php" \
    "${LOCAL_DIR}/lib.php" \
    "${LOCAL_DIR}/.htaccess" \
    "${DEPLOY_HOST}:${DEPLOY_PATH}/"
scp -o BatchMode=yes \
    "${LOCAL_DIR}/data/.htaccess" \
    "${DEPLOY_HOST}:${DEPLOY_PATH}/data/"
ssh -o BatchMode=yes "$DEPLOY_HOST" bash -s <<EOF
chmod 755 '${DEPLOY_PATH}'
chmod 644 '${DEPLOY_PATH}/index.php' '${DEPLOY_PATH}/lib.php' '${DEPLOY_PATH}/.htaccess' '${DEPLOY_PATH}/data/.htaccess'
# PHP/Apache muss SQLite anlegen können (Plesk oft anderer User als SSH)
chmod 777 '${DEPLOY_PATH}/data'
if [[ -f '${DEPLOY_PATH}/data/discharge.sqlite' ]]; then
  chmod 666 '${DEPLOY_PATH}/data/discharge.sqlite'
fi
EOF

echo "[OK] https://spoteroxe.de/chat/"
echo "     Testring: curl -s 'https://spoteroxe.de/chat/index.php?op=register' -H 'Content-Type: application/json' -d '{\"displayName\":\"Probe\"}'"
