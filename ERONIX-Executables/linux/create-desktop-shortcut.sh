#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_LAUNCHER="$SCRIPT_DIR/ERONIX/bin/ERONIX"
APP_ICON="$SCRIPT_DIR/ERONIX/lib/ERONIX.png"
DESKTOP_DIR=""

if command -v xdg-user-dir >/dev/null 2>&1; then
  DESKTOP_DIR="$(xdg-user-dir DESKTOP)"
fi
if [[ -z "$DESKTOP_DIR" ]]; then
  DESKTOP_DIR="$HOME/Desktop"
fi

if [[ ! -x "$APP_LAUNCHER" ]]; then
  echo "ERONIX launcher was not found at: $APP_LAUNCHER" >&2
  exit 1
fi
if [[ ! -d "$DESKTOP_DIR" ]]; then
  echo "Desktop folder was not found: $DESKTOP_DIR" >&2
  echo "Create the folder or configure your desktop location, then run this script again." >&2
  exit 1
fi

SHORTCUT="$DESKTOP_DIR/ERONIX.desktop"
cat > "$SHORTCUT" <<EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=ERONIX
Comment=Emergency Room Simulation
Exec="$APP_LAUNCHER"
Icon=$APP_ICON
Terminal=false
Categories=Education;Science;
StartupWMClass=ERONIX
EOF
chmod +x "$SHORTCUT"
echo "ERONIX desktop shortcut created at: $SHORTCUT"
echo "If your desktop environment asks, choose Allow Launching for the shortcut."
