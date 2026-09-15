#!/usr/bin/env bash
# Manuskript-Pakete für Linux x86_64: jpackage-App-Image, .deb, AppImage, Arch .pkg.tar.zst
# Auf Linux nativ oder via Docker (linux/amd64) vom Mac.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

INSIDE_CONTAINER=0
USE_DOCKER=1
DO_DEB=1
DO_APPIMAGE=1
DO_ARCH=1

for arg in "$@"; do
    case "$arg" in
        --inside-container) INSIDE_CONTAINER=1 ;;
        --no-docker) USE_DOCKER=0 ;;
        --deb-only) DO_APPIMAGE=0; DO_ARCH=0 ;;
        --appimage-only) DO_DEB=0; DO_ARCH=0 ;;
        --arch-only) DO_DEB=0; DO_APPIMAGE=0 ;;
        --skip-arch) DO_ARCH=0 ;;
        -h|--help)
            echo "Usage: $0 [--no-docker] [--deb-only|--appimage-only|--arch-only|--skip-arch]"
            echo
            echo "  Baut Linux-x86_64-Pakete (.deb, AppImage, Arch .pkg.tar.zst)."
            echo "  Auf macOS/Windows: automatisch Docker linux/amd64 (ubuntu + archlinux)."
            echo
            echo "JavaFX 21 nutzt auf Wayland-Desktops XWayland. GDK_BACKEND wird nicht gesetzt."
            exit 0
            ;;
        *)
            echo "Unbekanntes Argument: $arg (siehe --help)"
            exit 1
            ;;
    esac
done

APP_NAME="Manuskript"
VERSION_FILE="src/main/resources/manuskript.version"
MAIN_CLASS="com.manuskript.Launcher"
FAT_JAR="manuskript-standalone.jar"
JAVAFX_VERSION="21.0.6"
JAVAFX_JMODS_DIR="javafx-jmods-${JAVAFX_VERSION}-linux-x64"
JAVAFX_JMODS_URL="https://download2.gluonhq.com/openjfx/${JAVAFX_VERSION}/openjfx-${JAVAFX_VERSION}_linux-x64_bin-jmods.zip"
OUTPUT_DIR="installer-output"
STAGING_DIR="installer-staging"
ICON_PNG="installer-assets/manuskript-app-icon-1024.png"
# linuxdeploy/appimagetool: max. 512×512 (1024×1024 wird abgelehnt)
ICON_PNG_APPIMAGE="installer-assets/manuskript-app-icon-512.png"

if [[ ! -f "$VERSION_FILE" ]]; then
    echo "FEHLER: Versionsdatei fehlt: ${VERSION_FILE}"
    exit 1
fi
APP_VERSION="$(tr -d '[:space:]' < "$VERSION_FILE")"
if [[ ! "$APP_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "FEHLER: Ungültige Version in ${VERSION_FILE}: '${APP_VERSION}'"
    exit 1
fi

build_arch_in_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        echo "WARNUNG: Docker fehlt – Arch-Paket übersprungen."
        return 0
    fi
    local app_image="${OUTPUT_DIR}/${APP_NAME}"
    if [[ ! -x "${app_image}/bin/${APP_NAME}" ]]; then
        echo "WARNUNG: App-Image fehlt, Arch-Paket übersprungen."
        return 0
    fi
    echo
    echo "Baue Arch-Paket in Docker (archlinux)..."
    docker run --rm --platform linux/amd64 \
        -e LANG=C.UTF-8 \
        -e LC_ALL=C.UTF-8 \
        -v "$ROOT_DIR:/work" \
        -w /work \
        archlinux:latest \
        bash -lc 'pacman -Syu --noconfirm --needed base-devel && bash /work/deploy/linux/build-arch-pkg.sh'
}

ensure_linux_host_or_docker() {
    if [[ "$(uname -s)" == "Linux" ]]; then
        return 0
    fi
    if [[ "$USE_DOCKER" -eq 0 ]]; then
        echo "FEHLER: Linux-Pakete müssen auf Linux gebaut werden. Docker erlauben oder auf Linux ausführen."
        exit 1
    fi
    if ! command -v docker >/dev/null 2>&1; then
        echo "FEHLER: Docker nicht gefunden. Bitte Docker installieren oder das Skript auf Linux ausführen."
        exit 1
    fi
    echo "========================================"
    echo " Baue Linux-Pakete in Docker (linux/amd64)"
    echo "========================================"
    if [[ "$DO_DEB" -eq 1 || "$DO_APPIMAGE" -eq 1 ]]; then
        docker run --rm --platform linux/amd64 \
            -e APPIMAGE_EXTRACT_AND_RUN=1 \
            -e JAVA_TOOL_OPTIONS="-Xint -XX:UseAVX=0" \
            -e SKIP_MAVEN="${SKIP_MAVEN:-0}" \
            -e SKIP_JPACKAGE="${SKIP_JPACKAGE:-0}" \
            -v "$ROOT_DIR:/work" \
            -w /work \
            ubuntu:24.04 \
            bash /work/create-installer-linux.sh --inside-container --skip-arch
    fi

    if [[ "$DO_ARCH" -eq 1 ]]; then
        build_arch_in_docker
    fi
    echo
    echo "Fertig. Artefakte unter ${OUTPUT_DIR}/"
    ls -lh "${OUTPUT_DIR}"/*.deb "${OUTPUT_DIR}"/*.AppImage "${OUTPUT_DIR}"/*.pkg.tar.zst 2>/dev/null || true
    exit 0
}

install_ubuntu_build_deps() {
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq --no-install-recommends \
        openjdk-21-jdk maven curl unzip zip tar file \
        fakeroot binutils desktop-file-utils \
        libgtk-3-0 libglib2.0-0 libx11-6 libxtst6 libgl1 \
        ca-certificates
}

ensure_java_jpackage() {
    if [[ -z "${JAVA_HOME:-}" ]]; then
        if [[ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/jpackage ]]; then
            export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
        elif [[ -x /usr/lib/jvm/java-21-openjdk/bin/jpackage ]]; then
            export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
        fi
    fi
    if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/jpackage" ]]; then
        echo "FEHLER: JDK 21 mit jpackage nicht gefunden. JAVA_HOME setzen."
        exit 1
    fi
    echo "[OK] Java 21 + jpackage: ${JAVA_HOME}"
}

download_file() {
    local url="$1"
    local dest="$2"
    echo "  Lade $(basename "$dest") ..."
    curl -fL --retry 3 --retry-delay 2 -o "$dest" "$url"
}

ensure_javafx_jmods() {
    echo
    echo "[2/8] Prüfe JavaFX-jmods (linux-x64)..."
    mkdir -p "$JAVAFX_JMODS_DIR"
    if ls "${JAVAFX_JMODS_DIR}"/*.jmod >/dev/null 2>&1; then
        echo "[OK] JavaFX-jmods: ${JAVAFX_JMODS_DIR}"
        return
    fi
    local zip="${JAVAFX_JMODS_DIR}.zip"
    download_file "$JAVAFX_JMODS_URL" "$zip"
    unzip -q -o "$zip" -d "${JAVAFX_JMODS_DIR}-unpack"
    local found
    found="$(find "${JAVAFX_JMODS_DIR}-unpack" -name '*.jmod' -print -quit)"
    if [[ -z "$found" ]]; then
        echo "FEHLER: Keine .jmod in ${zip}"
        exit 1
    fi
    cp "$(dirname "$found")"/*.jmod "${JAVAFX_JMODS_DIR}/"
    rm -rf "${JAVAFX_JMODS_DIR}-unpack" "$zip"
    echo "[OK] JavaFX-jmods geladen."
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
            --exclude 'launchers.json' \
            --exclude 'openrouter-monitor.properties' \
            --exclude 'mammouth-monitor.properties' \
            --exclude 'projekt-backup.json' \
            config/ "${app_dir}/config/"
    else
        cp -R config/. "${app_dir}/config/"
        rm -rf "${app_dir}/config/sessions"
        rm -f "${app_dir}/config/parameters.properties" \
              "${app_dir}/config/parameters.properties.backup" \
              "${app_dir}/config/tts-voices.json" \
              "${app_dir}/config/tts-recent-descriptions.json" \
              "${app_dir}/config/languagetool-dictionary.txt" \
              "${app_dir}/config/launchers.json" \
              "${app_dir}/config/openrouter-monitor.properties" \
              "${app_dir}/config/mammouth-monitor.properties" \
              "${app_dir}/config/projekt-backup.json"
    fi
    cp -f installer-assets/installer-config/parameters.properties "${app_dir}/config/parameters.properties"
    cp -f installer-assets/installer-config/launchers.json "${app_dir}/config/launchers.json"

    mkdir -p "${app_dir}/plugin-catalog" "${app_dir}/plugins"
    if [[ -d plugin-catalog ]]; then
        cp -R plugin-catalog/. "${app_dir}/plugin-catalog/" || true
        rm -f "${app_dir}/plugin-catalog/.gitkeep" "${app_dir}/plugin-catalog/README.md"
        rm -f "${app_dir}/plugin-catalog/"*.properties
    fi
    cp -f tools/openrouter-monitor/target/openrouter-monitor.jar "${app_dir}/plugin-catalog/openrouter-monitor.jar"
    cp -f tools/openrouter-monitor/packaged/run-openrouter-monitor.sh "${app_dir}/plugin-catalog/run-openrouter-monitor.sh"
    chmod +x "${app_dir}/plugin-catalog/run-openrouter-monitor.sh"
    cp -f tools/mammouth-monitor/target/mammouth-monitor.jar "${app_dir}/plugin-catalog/mammouth-monitor.jar"
    cp -f tools/mammouth-monitor/packaged/run-mammouth-monitor.sh "${app_dir}/plugin-catalog/run-mammouth-monitor.sh"
    chmod +x "${app_dir}/plugin-catalog/run-mammouth-monitor.sh"
    cp -f tools/projekt-backup/target/projekt-backup.jar "${app_dir}/plugin-catalog/projekt-backup.jar"

    echo "  - ffmpeg/"
    mkdir -p "${app_dir}/ffmpeg"
    if [[ -f ffmpeg/ffmpeg-linux.zip ]]; then
        cp -f ffmpeg/ffmpeg-linux.zip "${app_dir}/ffmpeg/"
    else
        echo "  WARNUNG: ffmpeg/ffmpeg-linux.zip fehlt – FFmpeg nur aus PATH."
    fi

    echo "  - pandoc/"
    mkdir -p "${app_dir}/pandoc"
    if [[ -f pandoc/pandoc-linux.zip ]]; then
        cp -f pandoc/pandoc-linux.zip "${app_dir}/pandoc/"
    else
        echo "  WARNUNG: pandoc/pandoc-linux.zip fehlt – Pandoc nur aus PATH."
    fi
    for f in pandoc/*.docx pandoc/*.txt pandoc/*.lua pandoc/*.css pandoc/*.yaml pandoc/*.tex pandoc/*.html pandoc/*.rtf pandoc/*.md; do
        [[ -f "$f" ]] || continue
        cp -f "$f" "${app_dir}/pandoc/"
    done

    if [[ -d Manuskripte ]]; then
        echo "  - Manuskripte/"
        mkdir -p "${app_dir}/Manuskripte"
        cp -R Manuskripte/. "${app_dir}/Manuskripte/"
    fi

    if [[ -d "language tool" ]]; then
        echo "  - language tool/"
        mkdir -p "${app_dir}/language tool"
        cp -R "language tool/." "${app_dir}/language tool/"
    fi
}

appimage_icon_source() {
    if [[ -f "$ICON_PNG_APPIMAGE" ]]; then
        echo "$ICON_PNG_APPIMAGE"
        return
    fi
    echo "$ICON_PNG"
}

build_appimage() {
    local app_image="$1"
    echo
    echo "[7/8] Erstelle AppImage..."
    local tools_dir="${OUTPUT_DIR}/linux-tools"
    mkdir -p "$tools_dir"
    # jpackage bündelt Runtime + Libs bereits – appimagetool reicht (kein linuxdeploy).
    local appimagetool="${tools_dir}/appimagetool-x86_64.AppImage"
    if [[ ! -x "$appimagetool" ]]; then
        download_file \
            "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage" \
            "$appimagetool"
        chmod +x "$appimagetool"
    fi
    local appdir="${OUTPUT_DIR}/Manuskript.AppDir"
    rm -rf "$appdir"
    mkdir -p "$appdir/usr/share/icons/hicolor/512x512/apps"
    cp -R --no-preserve=ownership "$app_image" "${appdir}/Manuskript"
    cp -f deploy/linux/AppRun "$appdir/AppRun"
    chmod +x "$appdir/AppRun"
    cp -f deploy/linux/manuskript.desktop "$appdir/manuskript.desktop"
    sed -i 's|^Exec=.*|Exec=AppRun|' "$appdir/manuskript.desktop"
    local icon_src
    icon_src="$(appimage_icon_source)"
    if [[ -f "$icon_src" ]]; then
        cp -f "$icon_src" "$appdir/manuskript.png"
        cp -f "$icon_src" "$appdir/.DirIcon"
        cp -f "$icon_src" "$appdir/usr/share/icons/hicolor/512x512/apps/manuskript.png"
    fi
    export APPIMAGE_EXTRACT_AND_RUN=1 ARCH=x86_64
    local out_name="Manuskript-${APP_VERSION}-linux-x64.AppImage"
    if (
        cd "$OUTPUT_DIR"
        APPIMAGE_EXTRACT_AND_RUN=1 ARCH=x86_64 \
            "./linux-tools/appimagetool-x86_64.AppImage" \
            --no-appstream \
            "Manuskript.AppDir" \
            "$out_name"
    ); then
        echo "[OK] AppImage: ${OUTPUT_DIR}/${out_name}"
        return 0
    fi
    echo "  appimagetool fehlgeschlagen – fallback AppRun-Tarball."
    (cd "$appdir" && tar -czf "../Manuskript-${APP_VERSION}-linux-x64.AppDir.tar.gz" .)
}

build_arch_pkg_local() {
    echo
    echo "[8/8] Erstelle Arch-Paket..."
    if command -v makepkg >/dev/null 2>&1 && [[ "$(id -u)" -ne 0 ]]; then
        bash deploy/linux/build-arch-pkg.sh
        return
    fi
    if command -v docker >/dev/null 2>&1; then
        build_arch_in_docker
        return
    fi
    echo "WARNUNG: makepkg/Docker fehlt – Arch-Paket übersprungen."
}

# --- Einstieg ---
if [[ "$INSIDE_CONTAINER" -eq 0 ]]; then
    ensure_linux_host_or_docker
fi

echo "========================================"
echo " Manuskript Linux-Pakete (x86_64)"
echo "========================================"
echo " Version: ${APP_VERSION}"
echo

if [[ "$INSIDE_CONTAINER" -eq 1 ]]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--XX:UseAVX=0}"
    install_ubuntu_build_deps
fi

ensure_java_jpackage
JPACKAGE="${JAVA_HOME}/bin/jpackage"

echo
echo "[1/8] Baue Fat JAR und Plugins..."
if [[ "${SKIP_MAVEN:-0}" == "1" && -f "target/${FAT_JAR}" ]]; then
    echo "[OK] Überspringe Maven (SKIP_MAVEN=1), nutze vorhandene JARs."
else
    mvn clean package -DskipTests -q
    if [[ ! -f "target/${FAT_JAR}" ]]; then
        echo "FEHLER: ${FAT_JAR} nicht in target/ gefunden!"
        exit 1
    fi
    mvn -f tools/openrouter-monitor/pom.xml package -DskipTests -q
    mvn -f tools/mammouth-monitor/pom.xml package -DskipTests -q
    mvn -f tools/projekt-backup/pom.xml package -DskipTests -q
    echo "[OK] JARs erstellt."
fi

ensure_javafx_jmods

echo
echo "[3/8] Prüfe Pandoc-/FFmpeg-Bundles..."
if [[ ! -f pandoc/pandoc-linux.zip || ! -f ffmpeg/ffmpeg-linux.zip ]]; then
    if [[ -x "$ROOT_DIR/prepare-linux-bundles.sh" ]]; then
        "$ROOT_DIR/prepare-linux-bundles.sh" || echo "WARNUNG: Linux-Bundles konnten nicht erzeugt werden."
    fi
fi

APP_IMAGE="${OUTPUT_DIR}/${APP_NAME}"
APP_DIR="${APP_IMAGE}/lib/app"

if [[ "${SKIP_JPACKAGE:-0}" == "1" && -x "${APP_IMAGE}/bin/${APP_NAME}" && -d "$APP_DIR" ]]; then
    echo
    echo "[4/8] Staging übersprungen (SKIP_JPACKAGE=1)"
    echo "[5/8] jpackage App-Image vorhanden: ${APP_IMAGE}"
    echo "[6/8] Ergänze fehlende Linux-Bundles in lib/app..."
    mkdir -p "${APP_DIR}/ffmpeg" "${APP_DIR}/pandoc"
    if [[ -f ffmpeg/ffmpeg-linux.zip ]]; then
        cp -f ffmpeg/ffmpeg-linux.zip "${APP_DIR}/ffmpeg/"
    fi
    if [[ -f pandoc/pandoc-linux.zip ]]; then
        cp -f pandoc/pandoc-linux.zip "${APP_DIR}/pandoc/"
    fi
    echo "[OK] lib/app aktualisiert."
else
    echo
    echo "[4/8] Staging..."
    rm -rf "$STAGING_DIR"
    mkdir -p "${STAGING_DIR}/app"
    cp "target/${FAT_JAR}" "${STAGING_DIR}/app/"

    echo
    echo "[5/8] jpackage App-Image..."
    rm -rf "${OUTPUT_DIR}/${APP_NAME}"
    mkdir -p "$OUTPUT_DIR"
    JPACKAGE_ARGS=(
        --type app-image
        --name "$APP_NAME"
        --app-version "$APP_VERSION"
        --vendor "Manuskript"
        --input "${STAGING_DIR}/app"
        --main-jar "$FAT_JAR"
        --main-class "$MAIN_CLASS"
        --module-path "$JAVAFX_JMODS_DIR"
        --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.swing,javafx.media,java.base,java.desktop,java.logging,java.naming,java.net.http,java.prefs,java.sql,java.xml,java.xml.crypto,java.management,java.scripting,jdk.unsupported,jdk.crypto.ec,jdk.httpserver,jdk.localedata,jdk.charsets,jdk.zipfs
        --jlink-options "--strip-debug --no-man-pages --no-header-files"
        --java-options "--add-opens=javafx.graphics/javafx.css=ALL-UNNAMED"
        --java-options "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"
        --java-options "-Dprism.dirtyopts=false"
        --dest "$OUTPUT_DIR"
    )
    if [[ -f "$ICON_PNG" ]]; then
        JPACKAGE_ARGS+=(--icon "$ICON_PNG")
    fi
    "$JPACKAGE" "${JPACKAGE_ARGS[@]}"

    if [[ ! -d "$APP_DIR" ]]; then
        echo "FEHLER: Linux-App-Home fehlt: ${APP_DIR}"
        exit 1
    fi
    echo "[OK] App-Image: ${APP_IMAGE}"

    echo
    echo "[6/8] Kopiere Ressourcen nach lib/app..."
    copy_bundled_resources "$APP_DIR"
    echo "[OK] Ressourcen kopiert."
fi

if [[ "$DO_DEB" -eq 1 ]]; then
    existing_deb="${OUTPUT_DIR}/Manuskript-${APP_VERSION}-linux-x64.deb"
    if [[ "${SKIP_JPACKAGE:-0}" == "1" && -f "$existing_deb" ]]; then
        echo "[OK] Deb vorhanden: ${existing_deb}"
    else
        echo
        echo "Erstelle .deb..."
        rm -f "${OUTPUT_DIR}/${APP_NAME}"*.deb
        DEB_ARGS=(
            --type deb
            --app-image "$APP_IMAGE"
            --name "$APP_NAME"
            --app-version "$APP_VERSION"
            --vendor "Manuskript"
            --linux-shortcut
            --linux-menu-group Office
            --linux-app-category Office
            --linux-deb-maintainer "Manuskript"
            --linux-package-deps "libgtk-3-0, libglib2.0-0, libx11-6, libxtst6, libgl1"
            --dest "$OUTPUT_DIR"
        )
        if "$JPACKAGE" "${DEB_ARGS[@]}"; then
            produced="$(ls -1 "${OUTPUT_DIR}"/Manuskript*.deb 2>/dev/null | tail -n 1 || true)"
            if [[ -n "$produced" ]]; then
                mv -f "$produced" "${OUTPUT_DIR}/Manuskript-${APP_VERSION}-linux-x64.deb"
                echo "[OK] Deb: ${OUTPUT_DIR}/Manuskript-${APP_VERSION}-linux-x64.deb"
            fi
        else
            echo "WARNUNG: .deb fehlgeschlagen (fakeroot/binutils?)."
        fi
    fi
fi

if [[ "$DO_APPIMAGE" -eq 1 ]]; then
    build_appimage "$APP_IMAGE"
fi

if [[ "$DO_ARCH" -eq 1 ]]; then
    build_arch_pkg_local
fi

rm -rf "$STAGING_DIR"

echo
echo "========================================"
echo " Fertig!"
echo "========================================"
echo " Version:   ${APP_VERSION}"
echo " App-Image: ${APP_IMAGE}/"
echo " Starten:   ${APP_IMAGE}/bin/${APP_NAME}"
echo
echo " JavaFX 21 auf Wayland: XWayland (kein GDK_BACKEND=wayland)."
echo
