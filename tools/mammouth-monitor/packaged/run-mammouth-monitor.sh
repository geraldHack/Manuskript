#!/bin/bash
# Startet den Mammouth-Monitor.
# Installierte App: JAR neben diesem Skript + JRE mit JavaFX unter runtime/.
# Entwicklung: Fallback auf mvn javafx:run.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

find_jar() {
  if [[ -f "$SCRIPT_DIR/mammouth-monitor.jar" ]]; then
    echo "$SCRIPT_DIR/mammouth-monitor.jar"
    return
  fi
  if [[ -f "$SCRIPT_DIR/../target/mammouth-monitor.jar" ]]; then
    echo "$(cd "$SCRIPT_DIR/../target" && pwd)/mammouth-monitor.jar"
    return
  fi
  echo ""
}

find_config_dir() {
  local d="$SCRIPT_DIR"
  local i
  for i in 1 2 3 4 5 6 7; do
    d="$(cd "$d/.." && pwd)"
    if [[ -d "$d/config" ]]; then
      echo "$d"
      return
    fi
  done
  echo "$(cd "$SCRIPT_DIR/.." && pwd)"
}

find_java() {
  local app_home config_dir mac_java win_java
  config_dir="$(find_config_dir)"
  app_home="$(cd "$SCRIPT_DIR/.." && pwd)"
  mac_java="$(cd "$app_home/.." && pwd)/runtime/Contents/Home/bin/java"
  win_java="$(cd "$app_home/.." && pwd)/runtime/bin/java"
  if [[ -x "$mac_java" ]]; then
    echo "$mac_java"
    return
  fi
  if [[ -x "$win_java" ]]; then
    echo "$win_java"
    return
  fi
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    echo "${JAVA_HOME}/bin/java"
    return
  fi
  if command -v java >/dev/null 2>&1; then
    command -v java
    return
  fi
  echo ""
}

java_has_javafx() {
  local java_bin="$1"
  "$java_bin" --add-modules javafx.controls -version >/dev/null 2>&1
}

run_with_maven() {
  local config_dir="$1"
  local module_dir
  if [[ -f "$SCRIPT_DIR/../pom.xml" ]]; then
    module_dir="$(cd "$SCRIPT_DIR/.." && pwd)"
  elif [[ -f "$config_dir/tools/mammouth-monitor/pom.xml" ]]; then
    module_dir="$config_dir/tools/mammouth-monitor"
  else
    echo "Mammouth-Monitor: weder gebündelte JRE mit JavaFX noch Maven-Projekt gefunden." >&2
    exit 1
  fi
  local args=(--config-dir="$config_dir")
  if (($# > 1)); then
    shift
    args+=("$@")
  fi
  cd "$module_dir"
  exec mvn -q javafx:run -Djavafx.args="${args[*]}"
}

JAR="$(find_jar)"
CONFIG_DIR="$(find_config_dir)"
JAVA="$(find_java)"

if [[ -n "$JAR" && -n "$JAVA" ]] && java_has_javafx "$JAVA"; then
  exec "$JAVA" --add-modules javafx.controls -jar "$JAR" --config-dir="$CONFIG_DIR" "$@"
fi

run_with_maven "$CONFIG_DIR" "$@"
