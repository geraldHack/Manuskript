#!/usr/bin/env bash
# Manuskript Installer für macOS (Apple Silicon / arm64)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

UPLOAD=1
DEPLOY_HOST="${MANUSKRIPT_DEPLOY_HOST:-spoteroxe.de}"
DEPLOY_PATH="${MANUSKRIPT_DEPLOY_PATH:-/home/gehack/home/downloads}"
STABLE_DMG_NAME="Manuskript-macos-arm64.dmg"
for arg in "$@"; do
    case "$arg" in
        --no-upload) UPLOAD=0 ;;
        --upload) UPLOAD=1 ;;
        -h|--help)
            echo "Usage: $0 [--upload|--no-upload]"
            echo
            echo "  --upload      DMG nach ${DEPLOY_HOST} kopieren (Standard)"
            echo "  --no-upload   nur lokal bauen"
            echo
            echo "Umgebung: MANUSKRIPT_DEPLOY_HOST, MANUSKRIPT_DEPLOY_PATH"
            exit 0
            ;;
        *)
            echo "Unbekanntes Argument: $arg (siehe --help)"
            exit 1
            ;;
    esac
done

echo "========================================"
echo " Manuskript Installer-Paket erstellen"
echo " (macOS Apple Silicon)"
echo "========================================"
echo

# --- Nur Apple Silicon ---
if [[ "$(uname -m)" != "arm64" ]]; then
    echo "FEHLER: Dieses Skript ist nur für Apple Silicon (arm64) gedacht."
    exit 1
fi

# --- Java 21 prüfen ---
if [[ -z "${JAVA_HOME:-}" ]]; then
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
        JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    fi
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/jpackage" ]]; then
    echo "FEHLER: JDK 21 mit jpackage nicht gefunden."
    echo "Bitte Temurin 21 installieren und JAVA_HOME setzen, z. B.:"
    echo "  export JAVA_HOME=\$(/usr/libexec/java_home -v 21)"
    exit 1
fi
echo "[OK] Java 21: ${JAVA_HOME}"

JPACKAGE="${JAVA_HOME}/bin/jpackage"

# --- Konfiguration ---
APP_NAME="Manuskript"
VERSION_FILE="src/main/resources/manuskript.version"
if [[ ! -f "$VERSION_FILE" ]]; then
    echo "FEHLER: Versionsdatei fehlt: ${VERSION_FILE}"
    exit 1
fi
APP_VERSION="$(tr -d '[:space:]' < "$VERSION_FILE")"
if [[ ! "$APP_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "FEHLER: Ungültige Version in ${VERSION_FILE}: '${APP_VERSION}'"
    exit 1
fi
echo "[OK] Deploy-Version: ${APP_VERSION}"
MAIN_CLASS="com.manuskript.Launcher"
FAT_JAR="manuskript-standalone.jar"
JAVAFX_VERSION="21.0.6"
JAVAFX_MODULE_PATH="javafx-modpath-${JAVAFX_VERSION}"
JAVAFX_PLATFORM="mac-aarch64"
MAVEN_REPO="https://repo1.maven.org/maven2/org/openjfx"
JAVAFX_MODULES=(javafx-base javafx-graphics javafx-controls javafx-fxml javafx-web javafx-swing javafx-media)
OUTPUT_DIR="installer-output"
STAGING_DIR="installer-staging"
ICON_SOURCE="installer-assets/manuskript-app-icon-1024.png"
ICON_ICNS="installer-assets/Manuskript.icns"

download_file() {
    local url="$1"
    local dest="$2"
    echo "  Lade $(basename "$dest") ..."
    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 --retry-delay 2 -o "$dest" "$url"
    else
        echo "FEHLER: curl nicht gefunden."
        exit 1
    fi
}

ensure_javafx_module_path() {
    echo
    echo "[2/8] Prüfe JavaFX-Module (Maven Central)..."
    mkdir -p "$JAVAFX_MODULE_PATH"

    local missing=0
    for module in "${JAVAFX_MODULES[@]}"; do
        local jar="${JAVAFX_MODULE_PATH}/${module}-${JAVAFX_VERSION}-${JAVAFX_PLATFORM}.jar"
        if [[ ! -f "$jar" ]]; then
            missing=1
            local url="${MAVEN_REPO}/${module}/${JAVAFX_VERSION}/${module}-${JAVAFX_VERSION}-${JAVAFX_PLATFORM}.jar"
            download_file "$url" "$jar"
        fi
    done

    if [[ "$missing" -eq 0 ]]; then
        echo "[OK] JavaFX-Module: ${JAVAFX_MODULE_PATH} (bereits vorhanden)"
    else
        echo "[OK] JavaFX-Module: ${JAVAFX_MODULE_PATH} (von Maven Central geladen)"
    fi
}

ensure_mac_icon() {
    if [[ ! -f "$ICON_SOURCE" ]]; then
        echo "WARNUNG: App-Icon-Quelle fehlt (${ICON_SOURCE}) – jpackage nutzt Standard-Icon."
        return 1
    fi
    if [[ ! -f "$ICON_ICNS" || "$ICON_SOURCE" -nt "$ICON_ICNS" ]]; then
        echo "  Erstelle App-Icon (.icns) ..."
        "$ROOT_DIR/installer-assets/build-mac-icon.sh"
    else
        echo "[OK] App-Icon: ${ICON_ICNS}"
    fi
}

copy_bundled_resources() {
    local app_dir="$1"
    mkdir -p "$app_dir"

    echo "  - config/"
    mkdir -p "${app_dir}/config"
    if command -v rsync >/dev/null 2>&1; then
        rsync -a \
            --exclude 'sessions/' \
            --exclude 'parameters.properties' \
            --exclude 'parameters.properties.backup' \
            --exclude 'tts-voices.json' \
            --exclude 'tts-recent-descriptions.json' \
            --exclude 'languagetool-dictionary.txt' \
            config/ "${app_dir}/config/"
    else
        cp -R config/. "${app_dir}/config/"
        rm -rf "${app_dir}/config/sessions"
        rm -f "${app_dir}/config/parameters.properties" \
              "${app_dir}/config/parameters.properties.backup" \
              "${app_dir}/config/tts-voices.json" \
              "${app_dir}/config/tts-recent-descriptions.json" \
              "${app_dir}/config/languagetool-dictionary.txt"
    fi
    cp -f installer-assets/installer-config/parameters.properties "${app_dir}/config/parameters.properties"

    echo "  - ffmpeg/"
    mkdir -p "${app_dir}/ffmpeg"
    cp -f ffmpeg/ffmpeg-mac.zip "${app_dir}/ffmpeg/"

    echo "  - pandoc/"
    mkdir -p "${app_dir}/pandoc"
    cp -f pandoc/pandoc-mac.zip "${app_dir}/pandoc/"
    for f in pandoc/*.docx pandoc/*.txt pandoc/*.lua pandoc/*.css pandoc/*.yaml pandoc/*.tex pandoc/*.html pandoc/*.rtf pandoc/*.md; do
        [[ -f "$f" ]] || continue
        cp -f "$f" "${app_dir}/pandoc/"
    done

    if [[ -d Manuskripte ]]; then
        echo "  - Manuskripte/ (Demo-Vorlage; wird beim ersten Start nach ~/Documents/Manuskript kopiert)"
        mkdir -p "${app_dir}/Manuskripte"
        if command -v rsync >/dev/null 2>&1; then
            rsync -a \
                --exclude '.DS_Store' \
                Manuskripte/ "${app_dir}/Manuskripte/"
        else
            cp -R Manuskripte/. "${app_dir}/Manuskripte/"
            find "${app_dir}/Manuskripte" -name '.DS_Store' -delete 2>/dev/null || true
        fi
    else
        echo "  WARNUNG: Ordner Manuskripte/ nicht gefunden – kein Demo-Projekt im Paket."
    fi

    if [[ -d "language tool" ]]; then
        echo "  - language tool/ (kann etwas dauern...)"
        mkdir -p "${app_dir}/language tool"
        if command -v rsync >/dev/null 2>&1; then
            rsync -a "language tool/" "${app_dir}/language tool/"
        else
            cp -R "language tool/." "${app_dir}/language tool/"
        fi
    else
        echo "  WARNUNG: Ordner 'language tool/' nicht gefunden – Rechtschreib-Server fehlt im Paket."
    fi
}

human_size_mb() {
    local bytes="$1"
    echo $(( (bytes + 524288) / 1048576 ))
}

upload_dmg_to_spoteroxe() {
    local dmg_path="$1"
    if [[ ! -f "$dmg_path" ]]; then
        echo "WARNUNG: Keine DMG zum Hochladen: ${dmg_path}"
        return 1
    fi

    echo
    echo "[8/8] Lade DMG nach ${DEPLOY_HOST}:${DEPLOY_PATH} ..."
    if ! ssh -o BatchMode=yes -o ConnectTimeout=15 "$DEPLOY_HOST" "mkdir -p '${DEPLOY_PATH}'"; then
        echo "WARNUNG: SSH zu ${DEPLOY_HOST} fehlgeschlagen. Lokal: ${dmg_path}"
        return 1
    fi

    echo "  scp ${DMG_NAME} (kann bei ~500 MB dauern) ..."
    if ! scp -o BatchMode=yes "$dmg_path" "${DEPLOY_HOST}:${DEPLOY_PATH}/${DMG_NAME}"; then
        echo "WARNUNG: scp der DMG fehlgeschlagen."
        return 1
    fi

    local size_bytes size_mb json_file js_file
    size_bytes="$(stat -f%z "$dmg_path")"
    size_mb="$(human_size_mb "$size_bytes")"
    json_file="$(mktemp -t manuskript-download)"
    cat > "$json_file" <<EOF
{
  "version": "${APP_VERSION}",
  "platform": "macOS (Apple Silicon / arm64)",
  "filename": "${DMG_NAME}",
  "url": "downloads/${STABLE_DMG_NAME}",
  "sizeBytes": ${size_bytes},
  "sizeLabel": "${size_mb} MB",
  "updated": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

    js_file="${ROOT_DIR}/deploy/spoteroxe/manuskript-download.js"
    if ! scp -o BatchMode=yes "$json_file" "${DEPLOY_HOST}:${DEPLOY_PATH}/manuskript.json"; then
        rm -f "$json_file"
        echo "WARNUNG: scp von manuskript.json fehlgeschlagen."
        return 1
    fi
    rm -f "$json_file"

    if [[ -f "$js_file" ]]; then
        scp -o BatchMode=yes "$js_file" "${DEPLOY_HOST}:/home/gehack/home/js/manuskript-download.js" || \
            echo "WARNUNG: scp von manuskript-download.js fehlgeschlagen."
    fi

    ssh -o BatchMode=yes "$DEPLOY_HOST" \
        "DEPLOY_PATH='${DEPLOY_PATH}' CURRENT='${DMG_NAME}' STABLE='${STABLE_DMG_NAME}' bash -s" <<'REMOTE'
set -euo pipefail
cd "$DEPLOY_PATH"
ln -f "$CURRENT" "$STABLE"
for f in Manuskript-*-macos-arm64.dmg; do
    [[ -f "$f" ]] || continue
    [[ "$f" == "$CURRENT" ]] && continue
    echo "  Entferne alte Version: $f"
    rm -f "$f"
done
REMOTE

    echo "[OK] Download aktuell: https://spoteroxe.de/downloads/${STABLE_DMG_NAME}"
    echo "     Version ${APP_VERSION}, ${size_mb} MB"
}

# --- Schritt 1: Fat JAR bauen ---
echo
echo "[1/8] Baue Fat JAR..."
mvn clean package -DskipTests -q
if [[ ! -f "target/${FAT_JAR}" ]]; then
    echo "FEHLER: ${FAT_JAR} nicht in target/ gefunden!"
    exit 1
fi
echo "[OK] ${FAT_JAR} erstellt."

ensure_javafx_module_path

# --- Schritt 3: Pandoc/FFmpeg ZIPs ---
echo
echo "[3/8] Prüfe Pandoc- und FFmpeg-Bundles..."
if [[ ! -f pandoc/pandoc-mac.zip || ! -f ffmpeg/ffmpeg-mac.zip ]]; then
    echo "  Fehlende Bundles – rufe prepare-mac-bundles.sh auf ..."
    "$ROOT_DIR/prepare-mac-bundles.sh"
else
    echo "[OK] pandoc/pandoc-mac.zip vorhanden"
    echo "[OK] ffmpeg/ffmpeg-mac.zip vorhanden"
fi

# --- Schritt 4: Staging ---
echo
echo "[4/8] Bereite Staging vor..."
rm -rf "$STAGING_DIR"
mkdir -p "${STAGING_DIR}/app"
cp "target/${FAT_JAR}" "${STAGING_DIR}/app/"
echo "[OK] Staging vorbereitet."

# --- Schritt 5: jpackage App-Image ---
echo
echo "[5/8] Erstelle App-Image mit jpackage..."
ensure_mac_icon || true
rm -rf "${OUTPUT_DIR}/${APP_NAME}.app"
mkdir -p "$OUTPUT_DIR"

JPACKAGE_APP_IMAGE_ARGS=(
    --type app-image
    --name "$APP_NAME"
    --app-version "$APP_VERSION"
    --vendor "Manuskript"
    --input "${STAGING_DIR}/app"
    --main-jar "$FAT_JAR"
    --main-class "$MAIN_CLASS"
)
if [[ -f "$ICON_ICNS" ]]; then
    JPACKAGE_APP_IMAGE_ARGS+=(--icon "$ICON_ICNS")
fi
JPACKAGE_APP_IMAGE_ARGS+=(
    --module-path "$JAVAFX_MODULE_PATH"
    --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.swing,javafx.media,java.base,java.desktop,java.logging,java.naming,java.net.http,java.prefs,java.sql,java.xml,java.xml.crypto,java.management,java.scripting,jdk.unsupported,jdk.crypto.ec,jdk.httpserver,jdk.localedata,jdk.charsets,jdk.zipfs
    --jlink-options "--strip-debug --no-man-pages --no-header-files"
    --java-options "--add-opens=javafx.graphics/javafx.css=ALL-UNNAMED"
    --java-options "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"
    --java-options "-Dprism.dirtyopts=false"
    --dest "$OUTPUT_DIR"
)

"$JPACKAGE" "${JPACKAGE_APP_IMAGE_ARGS[@]}"

APP_BUNDLE="${OUTPUT_DIR}/${APP_NAME}.app"
APP_CONTENTS="${APP_BUNDLE}/Contents/app"
if [[ ! -d "$APP_CONTENTS" ]]; then
    echo "FEHLER: App-Bundle nicht gefunden unter ${APP_CONTENTS}"
    exit 1
fi
echo "[OK] App-Image erstellt."

# --- Schritt 6: Ressourcen kopieren ---
echo
echo "[6/8] Kopiere Ressourcen ins App-Bundle..."
copy_bundled_resources "$APP_CONTENTS"
echo "[OK] Ressourcen kopiert."

# --- Schritt 7: DMG + ZIP ---
echo
echo "[7/8] Erstelle DMG und ZIP..."
echo "  (DMG und ZIP sind groß – kann mehrere Minuten dauern, bitte warten)"
DMG_NAME="${APP_NAME}-${APP_VERSION}-macos-arm64.dmg"
ZIP_NAME="${APP_NAME}-${APP_VERSION}-macos-arm64.zip"
rm -f "${OUTPUT_DIR}/${DMG_NAME}" "${OUTPUT_DIR}/${ZIP_NAME}"

echo "  Erstelle DMG ..."
"$JPACKAGE" \
    --type dmg \
    --app-image "$APP_BUNDLE" \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --vendor "Manuskript" \
    --dest "$OUTPUT_DIR"

if [[ -f "${OUTPUT_DIR}/${APP_NAME}-${APP_VERSION}.dmg" ]]; then
    mv "${OUTPUT_DIR}/${APP_NAME}-${APP_VERSION}.dmg" "${OUTPUT_DIR}/${DMG_NAME}"
elif [[ -f "${OUTPUT_DIR}/${APP_NAME}.dmg" ]]; then
    mv "${OUTPUT_DIR}/${APP_NAME}.dmg" "${OUTPUT_DIR}/${DMG_NAME}"
fi

if [[ -f "${OUTPUT_DIR}/${DMG_NAME}" ]]; then
    echo "[OK] DMG erstellt: ${OUTPUT_DIR}/${DMG_NAME}"
else
    echo "WARNUNG: DMG-Erstellung fehlgeschlagen. App-Bundle ist trotzdem nutzbar."
fi

echo "  Erstelle ZIP ..."
if ditto -c -k --sequesterRsrc --keepParent "$APP_BUNDLE" "${OUTPUT_DIR}/${ZIP_NAME}" 2>/dev/null; then
    echo "[OK] ZIP erstellt: ${OUTPUT_DIR}/${ZIP_NAME}"
else
    (cd "$OUTPUT_DIR" && zip -qr "$ZIP_NAME" "${APP_NAME}.app") || \
        echo "WARNUNG: ZIP-Erstellung fehlgeschlagen."
fi

rm -rf "$STAGING_DIR"

if [[ "$UPLOAD" -eq 1 ]]; then
    upload_dmg_to_spoteroxe "${OUTPUT_DIR}/${DMG_NAME}" || true
else
    echo
    echo "[8/8] Upload übersprungen (--no-upload)."
fi

# Patch-Version für den nächsten Deploy hochzählen (dieses Build bleibt bei APP_VERSION)
IFS='.' read -r VERSION_MAJOR VERSION_MINOR VERSION_PATCH <<< "$APP_VERSION"
NEXT_VERSION="${VERSION_MAJOR}.${VERSION_MINOR}.$((VERSION_PATCH + 1))"
printf '%s\n' "$NEXT_VERSION" > "$VERSION_FILE"
echo "[OK] Nächste Deploy-Version: ${NEXT_VERSION}"

echo
echo "========================================"
echo " Fertig!"
echo "========================================"
echo
echo " Version: ${APP_VERSION}"
echo " App:  ${APP_BUNDLE}"
echo " DMG:  ${OUTPUT_DIR}/${DMG_NAME}"
echo " ZIP:  ${OUTPUT_DIR}/${ZIP_NAME}"
if [[ "$UPLOAD" -eq 1 ]]; then
    echo " Web:  https://spoteroxe.de/downloads.html"
fi
echo
echo " Starten: open \"${APP_BUNDLE}\""
echo
echo " Zur Weitergabe: DMG oder ZIP enthält alles"
echo " (JRE, JavaFX, Config, FFmpeg, Pandoc, LanguageTool)."
echo " Der Empfänger braucht kein Java."
echo
echo " Hinweis: Ohne Code-Signing zeigt macOS ggf. eine"
echo " Sicherheitswarnung. Dann: Rechtsklick → Öffnen"
echo " oder: xattr -cr \"${APP_BUNDLE}\""
echo
