#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export MEDIA_SFU_ENABLE_IOS_NATIVE_BRIDGE_PACKAGE="${MEDIA_SFU_ENABLE_IOS_NATIVE_BRIDGE_PACKAGE:-1}"
export MEDIA_SFU_ENABLE_REAL_LIBMEDIASOUPCLIENT_BINDING="${MEDIA_SFU_ENABLE_REAL_LIBMEDIASOUPCLIENT_BINDING:-1}"
export MEDIASFU_REQUIRE_REAL_NATIVE_BRIDGE="${MEDIASFU_REQUIRE_REAL_NATIVE_BRIDGE:-1}"

SCHEME="${MEDIA_SFU_IOS_SCHEME:-MediaSFUSampleApp}"
CONFIGURATION="${MEDIA_SFU_IOS_CONFIGURATION:-Debug}"
BUNDLE_ID="${MEDIA_SFU_IOS_BUNDLE_ID:-com.mediasfu.MediaSFUSampleApp}"
DEVELOPMENT_TEAM="${MEDIA_SFU_IOS_DEVELOPMENT_TEAM:-}"  # Required: export MEDIA_SFU_IOS_DEVELOPMENT_TEAM=<your Apple Developer Team ID>
DEVICE_ID="${MEDIA_SFU_IOS_DEVICE_ID:-}"
DEVICECTL_ID="${MEDIA_SFU_IOS_DEVICECTL_ID:-}"
RUN_UI_TESTS="${MEDIA_SFU_IOS_RUN_UI_TESTS:-0}"
REFRESH_DERIVED_DATA="${MEDIA_SFU_IOS_REFRESH_DERIVED_DATA:-1}"
DERIVED_DATA_PATH="${MEDIA_SFU_IOS_DERIVED_DATA_PATH:-$ROOT_DIR/build}"
COMPILE_ONLY="${MEDIA_SFU_IOS_COMPILE_ONLY:-0}"
SHARED_DIR="${MEDIA_SFU_SHARED_DIR:-$ROOT_DIR/../shared}"
GRADLEW_BIN="${MEDIA_SFU_GRADLEW_BIN:-$ROOT_DIR/../gradlew}"
DEVICE_ARCHS="${MEDIASFU_KOTLIN_DEVICE_ARCHS:-arm64}"
PRESYNC_SHARED_FRAMEWORK="${MEDIASFU_PRESYNC_SHARED_FRAMEWORK:-1}"

presync_shared_framework() {
  if [[ "$PRESYNC_SHARED_FRAMEWORK" != "1" ]]; then
    return
  fi

  if [[ ! -d "$SHARED_DIR" ]]; then
    echo "Shared module directory not found at: $SHARED_DIR" >&2
    exit 2
  fi

  if [[ ! -x "$GRADLEW_BIN" ]]; then
    echo "Gradle wrapper not found at: $GRADLEW_BIN" >&2
    exit 2
  fi

  echo "== Pre-syncing shared device framework =="
  (
    cd "$SHARED_DIR"
    ARCHS="$DEVICE_ARCHS" \
    SDK_NAME="${MEDIASFU_KOTLIN_SDK_NAME:-iphoneos}" \
    PLATFORM_NAME="iphoneos" \
    CONFIGURATION="$CONFIGURATION" \
    "$GRADLEW_BIN" --no-daemon -p "$PWD" :shared:syncFramework \
      -Pkotlin.native.cocoapods.platform=iphoneos \
      -Pkotlin.native.cocoapods.archs="$DEVICE_ARCHS" \
      -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
  )

  export OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED="YES"
}

echo "== Regenerating bridge-enabled Xcode project =="
ruby scripts/generate_xcodeproj.rb

echo "== Installing pods =="
pod install

presync_shared_framework

if [[ "$REFRESH_DERIVED_DATA" == "1" ]]; then
  echo "== Clearing sample DerivedData =="
  rm -rf "$HOME/Library/Developer/Xcode/DerivedData/MediaSFUSampleApp-"*
  rm -rf "$DERIVED_DATA_PATH"
fi

echo "== Resolving Swift package dependencies =="
xcodebuild \
  -resolvePackageDependencies \
  -workspace MediaSFUSampleApp.xcworkspace \
  -scheme "$SCHEME" \
  -derivedDataPath "$DERIVED_DATA_PATH"

echo "== Verifying generic iOS device build =="
xcodebuild \
  -workspace MediaSFUSampleApp.xcworkspace \
  -scheme "$SCHEME" \
  -configuration "$CONFIGURATION" \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath "$DERIVED_DATA_PATH" \
  build \
  CODE_SIGNING_ALLOWED=NO

if [[ "$COMPILE_ONLY" == "1" ]]; then
  echo "Compile-only validation completed (generic iOS build passed with CODE_SIGNING_ALLOWED=NO)."
  exit 0
fi

echo "== Checking iOS codesigning prerequisites =="
if ! ./scripts/ios_codesign_doctor.sh; then
  echo "Codesigning preflight failed. Repair the local keychain/certificate setup, then rerun this script." >&2
  exit 4
fi

echo "== Connected device snapshot =="
xcrun xctrace list devices || true
xcrun devicectl list devices || true

if [[ -z "$DEVICE_ID" ]]; then
  DEVICE_ID="$(xcrun xctrace list devices 2>/dev/null | awk '
    /^== Devices ==/ { in_devices = 1; next }
    /^== Devices Offline ==/ { in_devices = 0; next }
    /^== Simulators ==/ { in_devices = 0; next }
    in_devices && /\([0-9A-Fa-f-]+\)$/ && $0 !~ /Mac/ {
      line = $0
      sub(/^.*\(/, "", line)
      sub(/\).*$/, "", line)
      print line
      exit
    }
  ')"
fi

if [[ -z "$DEVICE_ID" ]]; then
  echo "No available physical iPhone was reported by xctrace."
  echo "Unlock the phone, accept Trust This Computer, reconnect it, then rerun this script."
  echo "You can also pass MEDIA_SFU_IOS_DEVICE_ID=<xcodebuild-device-id>."
  exit 2
fi

if [[ -z "$DEVICECTL_ID" ]]; then
  DEVICECTL_ID="$DEVICE_ID"
fi

echo "== Building signed app for device $DEVICE_ID =="
xcodebuild \
  -workspace MediaSFUSampleApp.xcworkspace \
  -scheme "$SCHEME" \
  -configuration "$CONFIGURATION" \
  -sdk iphoneos \
  -destination "id=$DEVICE_ID" \
  -derivedDataPath "$DERIVED_DATA_PATH" \
  build \
  DEVELOPMENT_TEAM="$DEVELOPMENT_TEAM" \
  CODE_SIGN_STYLE=Automatic

APP_PATH="$(find "$DERIVED_DATA_PATH" -path "*Build/Products/$CONFIGURATION-iphoneos/$SCHEME.app" -type d | head -1)"
if [[ -z "$APP_PATH" ]]; then
  echo "Could not find built app bundle under ios-sample-app/build."
  exit 3
fi

echo "== Installing $APP_PATH =="
xcrun devicectl device install app --device "$DEVICECTL_ID" "$APP_PATH"

echo "== Launching $BUNDLE_ID =="
xcrun devicectl device process launch --device "$DEVICECTL_ID" "$BUNDLE_ID"

if [[ "$RUN_UI_TESTS" == "1" ]]; then
  echo "== Running UI tests =="
  xcodebuild \
    -workspace MediaSFUSampleApp.xcworkspace \
    -scheme "$SCHEME" \
    -configuration "$CONFIGURATION" \
    -sdk iphoneos \
    -destination "id=$DEVICE_ID" \
    -derivedDataPath "$DERIVED_DATA_PATH" \
    test \
    DEVELOPMENT_TEAM="$DEVELOPMENT_TEAM" \
    CODE_SIGN_STYLE=Automatic
fi

echo "Physical iPhone validation completed."