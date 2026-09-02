#!/usr/bin/env bash
# Optional: lädt gebaute Plugin-JARs plus gleichnamige .txt nach
# https://spoteroxe.de/downloads/plugins/
# Es gibt keinen JSON-Index. Die App listet den Ordner; jede .jar braucht eine .txt.
# Du kannst die Dateien auch von Hand kopieren (target/*.jar + target/*.txt).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

DEPLOY_HOST="${MANUSKRIPT_DEPLOY_HOST:-spoteroxe.de}"
DEPLOY_PATH="${MANUSKRIPT_DEPLOY_PATH:-/home/gehack/home/downloads}"
REMOTE_PLUGINS="${DEPLOY_PATH}/plugins"

pom_version() {
    local pom="$1"
    python3 - "$pom" <<'PY'
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(sys.argv[1]).getroot()
el = root.find("m:version", ns)
if el is None or not (el.text or "").strip():
    raise SystemExit("keine <version> in " + sys.argv[1])
print(el.text.strip())
PY
}

upload_pair() {
    local tool="$1"
    local jar="${ROOT_DIR}/tools/${tool}/target/${tool}.jar"
    local version
    version="$(pom_version "${ROOT_DIR}/tools/${tool}/pom.xml")"
    local remote_jar="${tool}-${version}.jar"
    local notes="${ROOT_DIR}/tools/${tool}/target/${tool}-${version}.txt"
    local latest="${ROOT_DIR}/tools/${tool}/target/${tool}.txt"
    if [[ ! -f "$jar" ]]; then
        echo "FEHLER: ${jar} fehlt. Zuerst: cd tools/${tool} && mvn package"
        exit 1
    fi
    if [[ ! -f "$notes" ]]; then
        echo "FEHLER: ${notes} fehlt. mvn package muss die .txt neben die JAR legen."
        exit 1
    fi
    scp -o BatchMode=yes "$jar" "${DEPLOY_HOST}:${REMOTE_PLUGINS}/${remote_jar}"
    scp -o BatchMode=yes "$notes" "${DEPLOY_HOST}:${REMOTE_PLUGINS}/${tool}-${version}.txt"
    if [[ -f "$latest" ]]; then
        scp -o BatchMode=yes "$latest" "${DEPLOY_HOST}:${REMOTE_PLUGINS}/${tool}.txt"
    fi
    ssh -o BatchMode=yes "$DEPLOY_HOST" \
        "chmod 644 '${REMOTE_PLUGINS}/${remote_jar}' '${REMOTE_PLUGINS}/${tool}-${version}.txt' '${REMOTE_PLUGINS}/${tool}.txt'"
    echo "     ${remote_jar}"
    echo "     ${tool}-${version}.txt"
    echo "     ${tool}.txt"
}

echo
echo "[Upload] Plugins (JAR + TXT) nach ${DEPLOY_HOST}:${REMOTE_PLUGINS}"
ssh -o BatchMode=yes -o ConnectTimeout=15 "$DEPLOY_HOST" "mkdir -p '${REMOTE_PLUGINS}'"

upload_pair openrouter-monitor
upload_pair mammouth-monitor
upload_pair projekt-backup
upload_pair schreib-statistik

echo "[OK] https://spoteroxe.de/downloads/plugins/"
echo "     Verzeichnislisting muss öffentlich sein (Apache Options Indexes)."
