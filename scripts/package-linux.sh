#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

INPUT="$ROOT/target/package-input"
OUTPUT="$ROOT/dist/linux"
APP_NAME="ERONIX"
APP_JAR="emergency-room-simulation-1.0.0.jar"
JAVA_HOME_RESOLVED="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"

command -v mvn >/dev/null || { echo "Maven is required." >&2; exit 1; }
command -v jpackage >/dev/null || { echo "JDK 25+ with jpackage is required." >&2; exit 1; }

mvn --no-transfer-progress clean package org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
  -DincludeScope=runtime \
  -DoutputDirectory="$INPUT"

cp "$ROOT/target/$APP_JAR" "$INPUT/$APP_JAR"
mkdir -p "$OUTPUT"
rm -rf "$OUTPUT/$APP_NAME"

if [[ -d "$JAVA_HOME_RESOLVED/jmods" ]]; then
  jpackage \
    --type app-image \
    --name "$APP_NAME" \
    --app-version 1.0.0 \
    --vendor "ERONIX" \
    --module-path "$INPUT" \
    --module hospital.emergency.room.simulation/hospital.ui.HospitalApp \
    --add-modules javafx.controls \
    --java-options "--enable-native-access=javafx.graphics" \
    --icon "$ROOT/src/main/resources/icons/app-icon.png" \
    --dest "$OUTPUT"
else
  jpackage \
    --type app-image \
    --name "$APP_NAME" \
    --app-version 1.0.0 \
    --vendor "ERONIX" \
    --runtime-image "$JAVA_HOME_RESOLVED" \
    --input "$INPUT" \
    --main-jar "$APP_JAR" \
    --main-class hospital.ui.HospitalLauncher \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    --icon "$ROOT/src/main/resources/icons/app-icon.png" \
    --dest "$OUTPUT"
fi

cp "$ROOT/scripts/create-linux-desktop-shortcut.sh" "$OUTPUT/create-desktop-shortcut.sh"
chmod +x "$OUTPUT/create-desktop-shortcut.sh"
if [[ "${ERONIX_SKIP_DESKTOP_SHORTCUT:-0}" != "1" ]]; then
  DESKTOP_DIR="${XDG_DESKTOP_DIR:-$HOME/Desktop}"
  if command -v xdg-user-dir >/dev/null 2>&1; then
    DESKTOP_DIR="$(xdg-user-dir DESKTOP)"
  fi
  if [[ -d "$DESKTOP_DIR" ]]; then
    "$OUTPUT/create-desktop-shortcut.sh"
  else
    echo "No Desktop folder was found; run $OUTPUT/create-desktop-shortcut.sh after configuring one."
  fi
fi

echo "Linux application image created at $OUTPUT/$APP_NAME"
