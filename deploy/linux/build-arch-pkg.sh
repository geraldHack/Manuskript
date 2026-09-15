#!/usr/bin/env bash
# Baut Manuskript-*.pkg.tar.zst aus installer-output/Manuskript (jpackage App-Image).
set -euo pipefail
export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

APP_NAME="Manuskript"
VERSION_FILE="src/main/resources/manuskript.version"
APP_VERSION="$(tr -d '[:space:]' < "$VERSION_FILE")"
APP_IMAGE="${ROOT_DIR}/installer-output/${APP_NAME}"
WORK="${ROOT_DIR}/installer-output/arch-pkg"

if [[ ! -x "${APP_IMAGE}/bin/${APP_NAME}" ]]; then
    echo "FEHLER: App-Image fehlt unter ${APP_IMAGE}"
    exit 1
fi

# Unter Docker/Bind-Mount: in /tmp bauen (chown auf macOS-Volumes schlägt sonst fehl).
if [[ "$(id -u)" -eq 0 ]]; then
    WORK="/tmp/manuskript-arch-pkg"
fi
rm -rf "$WORK"
mkdir -p "$WORK"
cp -R --no-preserve=ownership "$APP_IMAGE" "$WORK/app-image"
cp -f deploy/linux/PKGBUILD "$WORK/PKGBUILD"
cp -f deploy/linux/manuskript.desktop "$WORK/manuskript.desktop"
if [[ -f installer-assets/manuskript-app-icon-512.png ]]; then
    cp -f installer-assets/manuskript-app-icon-512.png "$WORK/manuskript.png"
elif [[ -f installer-assets/manuskript-app-icon-1024.png ]]; then
    cp -f installer-assets/manuskript-app-icon-1024.png "$WORK/manuskript.png"
fi
sed -i "s/^pkgver=.*/pkgver=${APP_VERSION}/" "$WORK/PKGBUILD"

if [[ "$(id -u)" -eq 0 ]]; then
    if ! id builder >/dev/null 2>&1; then
        useradd -m builder
    fi
    chown -R builder:builder "$WORK"
    # --nodeps: Runtime-Deps (gtk3 …) gelten fürs Zielsystem, nicht für den Build-Container.
    su builder -c "cd '$WORK' && makepkg -f --noconfirm --nodeps"
else
    (cd "$WORK" && makepkg -f --noconfirm --nodeps)
fi

shopt -s nullglob
dest="${ROOT_DIR}/installer-output/Manuskript-${APP_VERSION}-linux-x64.pkg.tar.zst"
for pkg in "$WORK"/*.pkg.tar.zst; do
    cp -f --no-preserve=ownership "$pkg" "$dest"
    rm -f "$pkg"
    echo "[OK] Arch-Paket: installer-output/Manuskript-${APP_VERSION}-linux-x64.pkg.tar.zst"
done
