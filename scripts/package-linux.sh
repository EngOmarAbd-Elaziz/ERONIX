#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

INPUT="$ROOT/target/package-input"
OUTPUT="$ROOT/dist/linux/packages"
APP_NAME="ERONIX"
APP_VERSION="${ERONIX_VERSION:-1.0.6}"
PACKAGE_TYPE="${ERONIX_PACKAGE_TYPE:-}"
APP_JAR="emergency-room-simulation-$APP_VERSION.jar"
APPIMAGE_TOOL="${APPIMAGETOOL:-}"
APPIMAGE_RUNTIME="${APPIMAGE_RUNTIME_FILE:-}"
JAVA_HOME_RESOLVED="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
RUNTIME_IMAGE="${ERONIX_RUNTIME_IMAGE:-}"
if [[ -z "$RUNTIME_IMAGE" && ! -d "$JAVA_HOME_RESOLVED/jmods" ]]; then
  RUNTIME_IMAGE="$JAVA_HOME_RESOLVED"
fi

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
  appimage)
    [[ -x "$APPIMAGE_TOOL" ]] || {
      echo "Set APPIMAGETOOL to the executable appimagetool binary." >&2
      exit 1
    }
    [[ -f "$APPIMAGE_RUNTIME" ]] || {
      echo "Set APPIMAGE_RUNTIME_FILE to the AppImage type 2 runtime." >&2
      exit 1
    }
    PACKAGE_ARGUMENTS=(--type app-image)
    ;;
  *)
    echo "ERONIX_PACKAGE_TYPE must be deb, rpm, or appimage." >&2
    exit 1
    ;;
esac

BUILD_OUTPUT="$ROOT/target/linux-packages/$PACKAGE_TYPE"

mvn --no-transfer-progress clean package org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
  -Drevision="$APP_VERSION" \
  -DincludeScope=runtime \
  -DoutputDirectory="$INPUT"

cp "$ROOT/target/$APP_JAR" "$INPUT/$APP_JAR"
mkdir -p "$OUTPUT" "$BUILD_OUTPUT"
find "$BUILD_OUTPUT" -maxdepth 1 -type f -delete

if [[ "$PACKAGE_TYPE" == "appimage" ]]; then
  APPIMAGE_STAGE="$ROOT/target/appimage-stage"
  APPIMAGE_DIR="$ROOT/target/ERONIX.AppDir"
  if [[ -n "$RUNTIME_IMAGE" ]]; then
    jpackage \
      "${PACKAGE_ARGUMENTS[@]}" \
      --name "$APP_NAME" \
      --app-version "$APP_VERSION" \
      --vendor "ERONIX" \
      --runtime-image "$RUNTIME_IMAGE" \
      --input "$INPUT" \
      --main-jar "$APP_JAR" \
      --main-class hospital.ui.HospitalLauncher \
      --java-options "--enable-native-access=ALL-UNNAMED" \
      --java-options "-Deronix.version=$APP_VERSION" \
      --icon "$ROOT/src/main/resources/icons/app-icon.png" \
      --dest "$APPIMAGE_STAGE"
  else
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
      --dest "$APPIMAGE_STAGE"
  fi

  mkdir -p "$APPIMAGE_DIR"
  cp -a "$APPIMAGE_STAGE/$APP_NAME/." "$APPIMAGE_DIR/"
  cp "$ROOT/src/main/resources/icons/app-icon.png" "$APPIMAGE_DIR/eronix.png"
  cat > "$APPIMAGE_DIR/ERONIX.desktop" <<'DESKTOP'
[Desktop Entry]
Name=ERONIX
Exec=AppRun
Icon=eronix
Type=Application
Categories=Education;
Terminal=false
DESKTOP
  cat > "$APPIMAGE_DIR/AppRun" <<'APPRUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/bin/ERONIX" "$@"
APPRUN
  chmod +x "$APPIMAGE_DIR/AppRun"
  [[ -x "$APPIMAGE_DIR/lib/runtime/bin/java" ]] || {
    echo "The AppImage staging directory does not contain its Java runtime." >&2
    exit 1
  }
  grep -Fxq "app.classpath=\$APPDIR/$APP_JAR" "$APPIMAGE_DIR/lib/app/ERONIX.cfg" || {
    echo "The AppImage staging directory does not contain the expected $APP_JAR." >&2
    exit 1
  }
  grep -Fxq "java-options=-Djpackage.app-version=$APP_VERSION" "$APPIMAGE_DIR/lib/app/ERONIX.cfg" || {
    echo "The AppImage staging directory does not report version $APP_VERSION." >&2
    exit 1
  }
  ARCH=x86_64 VERSION="$APP_VERSION" APPIMAGE_EXTRACT_AND_RUN=1 \
    "$APPIMAGE_TOOL" --runtime-file "$APPIMAGE_RUNTIME" \
    "$APPIMAGE_DIR" "$BUILD_OUTPUT/ERONIX-linux-x86_64.AppImage"
  [[ -s "$BUILD_OUTPUT/ERONIX-linux-x86_64.AppImage" ]] || {
    echo "The AppImage build did not produce an output file." >&2
    exit 1
  }
  find "$OUTPUT" -maxdepth 1 -type f -name 'ERONIX-linux-x86_64.AppImage' -delete
  cp "$BUILD_OUTPUT/ERONIX-linux-x86_64.AppImage" "$OUTPUT/ERONIX-linux-x86_64.AppImage"
  echo "Linux AppImage created at $OUTPUT/ERONIX-linux-x86_64.AppImage"
  exit 0
fi

if [[ -n "$RUNTIME_IMAGE" ]]; then
  jpackage \
    "${PACKAGE_ARGUMENTS[@]}" \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --vendor "ERONIX" \
    --runtime-image "$RUNTIME_IMAGE" \
    --input "$INPUT" \
    --main-jar "$APP_JAR" \
    --main-class hospital.ui.HospitalLauncher \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    --java-options "-Deronix.version=$APP_VERSION" \
    --icon "$ROOT/src/main/resources/icons/app-icon.png" \
    --dest "$BUILD_OUTPUT"
elif [[ -d "$JAVA_HOME_RESOLVED/jmods" ]]; then
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
    --dest "$BUILD_OUTPUT"
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
    --dest "$BUILD_OUTPUT"
fi

case "$PACKAGE_TYPE" in
  deb)
    mapfile -t BUILT_PACKAGES < <(find "$BUILD_OUTPUT" -maxdepth 1 -type f -name 'eronix_*.deb' -print)
    [[ ${#BUILT_PACKAGES[@]} -eq 1 ]] || {
      echo "Expected exactly one Debian package in $BUILD_OUTPUT." >&2
      exit 1
    }
    dpkg-deb --contents "${BUILT_PACKAGES[0]}" > "$BUILD_OUTPUT/package-files.txt"
    grep -q '/lib/runtime/bin/java$' "$BUILD_OUTPUT/package-files.txt" || {
      echo "The Debian package does not contain its Java runtime." >&2
      exit 1
    }
    [[ "$(dpkg-deb -f "${BUILT_PACKAGES[0]}" Version)" == "$APP_VERSION" ]] || {
      echo "The Debian package version does not match $APP_VERSION." >&2
      exit 1
    }
    find "$OUTPUT" -maxdepth 1 -type f -name 'eronix_*.deb' -delete
    cp "${BUILT_PACKAGES[0]}" "$OUTPUT/"
    ;;
  rpm)
    mapfile -t BUILT_PACKAGES < <(find "$BUILD_OUTPUT" -maxdepth 1 -type f -name 'eronix-*.rpm' -print)
    [[ ${#BUILT_PACKAGES[@]} -eq 1 ]] || {
      echo "Expected exactly one RPM package in $BUILD_OUTPUT." >&2
      exit 1
    }
    rpm -qpl "${BUILT_PACKAGES[0]}" > "$BUILD_OUTPUT/package-files.txt"
    grep -q '/lib/runtime/bin/java$' "$BUILD_OUTPUT/package-files.txt" || {
      echo "The RPM package does not contain its Java runtime." >&2
      exit 1
    }
    [[ "$(rpm -qp --qf '%{VERSION}' "${BUILT_PACKAGES[0]}")" == "$APP_VERSION" ]] || {
      echo "The RPM package version does not match $APP_VERSION." >&2
      exit 1
    }
    find "$OUTPUT" -maxdepth 1 -type f -name 'eronix-*.rpm' -delete
    cp "${BUILT_PACKAGES[0]}" "$OUTPUT/"
    ;;
esac

echo "Linux $PACKAGE_TYPE installer created in $OUTPUT for version $APP_VERSION"
