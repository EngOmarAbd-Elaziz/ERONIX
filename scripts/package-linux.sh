#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

INPUT="$ROOT/target/package-input"
OUTPUT="$ROOT/dist/linux/packages"
APP_NAME="ERONIX"
APP_VERSION="${ERONIX_VERSION:-1.0.0}"
PACKAGE_TYPE="${ERONIX_PACKAGE_TYPE:-}"
APP_JAR="emergency-room-simulation-$APP_VERSION.jar"
JAVA_HOME_RESOLVED="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"

command -v mvn >/dev/null || { echo "Maven is required." >&2; exit 1; }
command -v jpackage >/dev/null || { echo "JDK 25+ with jpackage is required." >&2; exit 1; }
if [[ -z "$PACKAGE_TYPE" ]]; then
  if command -v dpkg-deb >/dev/null; then
    PACKAGE_TYPE="deb"
  elif command -v rpmbuild >/dev/null; then
    PACKAGE_TYPE="rpm"
  fi
fi
[[ "$APP_VERSION" =~ ^[0-9]+(\.[0-9]+)*$ ]] || {
  echo "ERONIX_VERSION must be a numeric dotted version." >&2
  exit 1
}
case "$PACKAGE_TYPE" in
  deb)
    command -v fakeroot >/dev/null || { echo "fakeroot is required to build a Debian package." >&2; exit 1; }
    command -v dpkg-deb >/dev/null || { echo "dpkg-deb is required to build a Debian package." >&2; exit 1; }
    PACKAGE_ARGUMENTS=(--type deb --linux-package-name "eronix" --linux-app-category "Utility" --linux-shortcut)
    ;;
  rpm)
    command -v rpmbuild >/dev/null || { echo "rpmbuild is required to build an RPM package." >&2; exit 1; }
    PACKAGE_ARGUMENTS=(--type rpm --linux-package-name "eronix" --linux-app-category "Utility")
    ;;
  *)
    echo "ERONIX_PACKAGE_TYPE must be deb or rpm." >&2
    exit 1
    ;;
esac

mvn --no-transfer-progress clean package org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
  -Drevision="$APP_VERSION" \
  -DincludeScope=runtime \
  -DoutputDirectory="$INPUT"

cp "$ROOT/target/$APP_JAR" "$INPUT/$APP_JAR"
mkdir -p "$OUTPUT"

if [[ -d "$JAVA_HOME_RESOLVED/jmods" ]]; then
  jpackage \
    "${PACKAGE_ARGUMENTS[@]}" \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --vendor "ERONIX" \
    --module-path "$INPUT" \
    --module hospital.emergency.room.simulation/hospital.ui.HospitalApp \
    --add-modules javafx.controls \
    --java-options "--enable-native-access=javafx.graphics" \
    --java-options "-Deronix.version=$APP_VERSION" \
    --icon "$ROOT/src/main/resources/icons/app-icon.png" \
    --dest "$OUTPUT"
else
  jpackage \
    "${PACKAGE_ARGUMENTS[@]}" \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --vendor "ERONIX" \
    --runtime-image "$JAVA_HOME_RESOLVED" \
    --input "$INPUT" \
    --main-jar "$APP_JAR" \
    --main-class hospital.ui.HospitalLauncher \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    --java-options "-Deronix.version=$APP_VERSION" \
    --icon "$ROOT/src/main/resources/icons/app-icon.png" \
    --dest "$OUTPUT"
fi

echo "Linux $PACKAGE_TYPE installer created in $OUTPUT for version $APP_VERSION"
