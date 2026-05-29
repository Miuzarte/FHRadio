param()

$ErrorActionPreference = "Stop"
$Root = "$PSScriptRoot/.."

$SrcWhite = "$Root/resource/icon_white.png"

if (!(Test-Path $SrcWhite)) { Write-Error "Missing: $SrcWhite"; exit 1 }

Write-Host "Generating icons ..."

# Desktop — Windows .ico (256x256, white)
$dest = "$Root/desktopApp/resources/windows"
New-Item -ItemType Directory -Force -Path $dest | Out-Null
ffmpeg -y -hide_banner -loglevel error -i $SrcWhite -s 256x256 "$dest/icon.ico"
Write-Host "  desktopApp/resources/windows/icon.ico"

# Desktop — Linux (white)
$dest = "$Root/desktopApp/resources/linux"
New-Item -ItemType Directory -Force -Path $dest | Out-Null
Copy-Item $SrcWhite "$dest/icon.png" -Force
Write-Host "  desktopApp/resources/linux/icon.png"

# Desktop — macOS (white, .icns needs macOS iconutil)
$dest = "$Root/desktopApp/resources/macos"
New-Item -ItemType Directory -Force -Path $dest | Out-Null
Copy-Item $SrcWhite "$dest/icon.png" -Force
Write-Host "  desktopApp/resources/macos/icon.png (placeholder, use macOS iconutil for .icns)"

# iOS AppIcon (white)
$dest = "$Root/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
New-Item -ItemType Directory -Force -Path $dest | Out-Null
Copy-Item $SrcWhite "$dest/app-icon-1024.png" -Force
Write-Host "  iosApp/.../app-icon-1024.png"

# Compose Resources (white)
$dest = "$Root/shared/src/commonMain/composeResources/drawable"
New-Item -ItemType Directory -Force -Path $dest | Out-Null
Copy-Item $SrcWhite "$dest/ic_launcher.png" -Force
Write-Host "  shared/.../composeResources/drawable/ic_launcher.png"

Write-Host "Done."
