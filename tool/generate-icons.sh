#!/usr/bin/env bash
set -euo pipefail

ROOT="$(dirname "$0")/.."
SRC_WHITE="$ROOT/resource/icon_white.png"

if [ ! -f "$SRC_WHITE" ]; then echo "Error: Missing $SRC_WHITE" >&2; exit 1; fi

echo "Generating icons ..."

# Desktop — Windows .ico (256x256, white)
mkdir -p "$ROOT/desktopApp/resources/windows"
ffmpeg -y -hide_banner -loglevel error -i "$SRC_WHITE" -s 256x256 "$ROOT/desktopApp/resources/windows/icon.ico"
echo "  desktopApp/resources/windows/icon.ico"

# Desktop — Linux (white)
mkdir -p "$ROOT/desktopApp/resources/linux"
cp "$SRC_WHITE" "$ROOT/desktopApp/resources/linux/icon.png"
echo "  desktopApp/resources/linux/icon.png"

# Desktop — macOS (white, .icns needs macOS iconutil)
mkdir -p "$ROOT/desktopApp/resources/macos"
cp "$SRC_WHITE" "$ROOT/desktopApp/resources/macos/icon.png"
echo "  desktopApp/resources/macos/icon.png (placeholder, use macOS iconutil for .icns)"

# iOS AppIcon (white)
mkdir -p "$ROOT/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
cp "$SRC_WHITE" "$ROOT/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"
echo "  iosApp/.../app-icon-1024.png"

# Compose Resources (white)
mkdir -p "$ROOT/shared/src/commonMain/composeResources/drawable"
cp "$SRC_WHITE" "$ROOT/shared/src/commonMain/composeResources/drawable/ic_launcher.png"
echo "  shared/.../composeResources/drawable/ic_launcher.png"

echo "Done."
