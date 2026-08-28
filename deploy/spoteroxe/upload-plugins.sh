#!/usr/bin/env bash
# Lädt offizielle Plugin-JARs und manuskript-plugins.json nach spoteroxe.de.
# Keine öffentliche HTML-Seite — die App liest nur das JSON.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

DEPLOY_HOST="${MANUSKRIPT_DEPLOY_HOST:-spoteroxe.de}"
DEPLOY_PATH="${MANUSKRIPT_DEPLOY_PATH:-/home/gehack/home/downloads}"
REMOTE_PLUGINS="${DEPLOY_PATH}/plugins"
PUBLIC_BASE="https://spoteroxe.de/downloads/plugins"

hash_file() {
    local file="$1"
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$file" | awk '{print $1}'
    else
        sha256sum "$file" | awk '{print $1}'
    fi
}

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

OR_JAR="${ROOT_DIR}/tools/openrouter-monitor/target/openrouter-monitor.jar"
MM_JAR="${ROOT_DIR}/tools/mammouth-monitor/target/mammouth-monitor.jar"
if [[ ! -f "$OR_JAR" ]]; then
    echo "FEHLER: ${OR_JAR} fehlt. Zuerst: cd tools/openrouter-monitor && mvn package"
    exit 1
fi
if [[ ! -f "$MM_JAR" ]]; then
    echo "FEHLER: ${MM_JAR} fehlt. Zuerst: cd tools/mammouth-monitor && mvn package"
    exit 1
fi

OR_VERSION="$(pom_version "${ROOT_DIR}/tools/openrouter-monitor/pom.xml")"
MM_VERSION="$(pom_version "${ROOT_DIR}/tools/mammouth-monitor/pom.xml")"
OR_REMOTE_NAME="openrouter-monitor-${OR_VERSION}.jar"
MM_REMOTE_NAME="mammouth-monitor-${MM_VERSION}.jar"
OR_SHA="$(hash_file "$OR_JAR")"
MM_SHA="$(hash_file "$MM_JAR")"
UPDATED="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

JSON_FILE="$(mktemp -t manuskript-plugins)"
python3 - "$JSON_FILE" "$UPDATED" \
    "$OR_VERSION" "$OR_REMOTE_NAME" "$OR_SHA" \
    "$MM_VERSION" "$MM_REMOTE_NAME" "$MM_SHA" \
    "$PUBLIC_BASE" <<'PY'
import json, sys
out, updated, or_ver, or_name, or_sha, mm_ver, mm_name, mm_sha, base = sys.argv[1:]
doc = {
    "updated": updated,
    "plugins": [
        {
            "id": "openrouter-monitor",
            "label": "OpenRouter-Monitor",
            "version": or_ver,
            "description": "Credits und API-Logs für OpenRouter.",
            "fileName": "openrouter-monitor.jar",
            "jar": f"{base}/{or_name}",
            "sha256": or_sha,
            "requires": "2.1.70",
        },
        {
            "id": "mammouth-monitor",
            "label": "Mammouth-Monitor",
            "version": mm_ver,
            "description": "Credits und Modellliste für Mammouth.",
            "fileName": "mammouth-monitor.jar",
            "jar": f"{base}/{mm_name}",
            "sha256": mm_sha,
            "requires": "2.1.70",
        },
    ],
}
with open(out, "w", encoding="utf-8") as f:
    json.dump(doc, f, indent=2, ensure_ascii=False)
    f.write("\n")
PY

echo
echo "[Upload] Plugin-Katalog nach ${DEPLOY_HOST}:${REMOTE_PLUGINS}"
ssh -o BatchMode=yes -o ConnectTimeout=15 "$DEPLOY_HOST" "mkdir -p '${REMOTE_PLUGINS}'"

scp -o BatchMode=yes "$OR_JAR" "${DEPLOY_HOST}:${REMOTE_PLUGINS}/${OR_REMOTE_NAME}"
scp -o BatchMode=yes "$MM_JAR" "${DEPLOY_HOST}:${REMOTE_PLUGINS}/${MM_REMOTE_NAME}"
scp -o BatchMode=yes "$JSON_FILE" "${DEPLOY_HOST}:${DEPLOY_PATH}/manuskript-plugins.json"
ssh -o BatchMode=yes "$DEPLOY_HOST" "chmod 644 '${DEPLOY_PATH}/manuskript-plugins.json' '${REMOTE_PLUGINS}/${OR_REMOTE_NAME}' '${REMOTE_PLUGINS}/${MM_REMOTE_NAME}'"
rm -f "$JSON_FILE"

echo "[OK] https://spoteroxe.de/downloads/manuskript-plugins.json"
echo "     ${OR_REMOTE_NAME}  ${OR_SHA}"
echo "     ${MM_REMOTE_NAME}  ${MM_SHA}"
