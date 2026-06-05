#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export MEDIA_SFU_ENABLE_IOS_NATIVE_BRIDGE_PACKAGE="${MEDIA_SFU_ENABLE_IOS_NATIVE_BRIDGE_PACKAGE:-1}"
# CRITICAL: keep the physical probe on the same real native bridge path as the simulator.
# If either flag is dropped here, device runs can silently fall back to placeholder mode and
# drift from simulator behavior, which hides real mediasoup regressions.
export MEDIA_SFU_ENABLE_REAL_LIBMEDIASOUPCLIENT_BINDING="${MEDIA_SFU_ENABLE_REAL_LIBMEDIASOUPCLIENT_BINDING:-1}"
export MEDIASFU_REQUIRE_REAL_NATIVE_BRIDGE="${MEDIASFU_REQUIRE_REAL_NATIVE_BRIDGE:-1}"

DEVICE_ID="${MEDIA_SFU_IOS_DEVICE_ID:-}"      # Required: export MEDIA_SFU_IOS_DEVICE_ID=<your device UDID>
DEVICECTL_ID="${MEDIA_SFU_IOS_DEVICECTL_ID:-$DEVICE_ID}"
APP_BUNDLE_ID="${MEDIA_SFU_IOS_BUNDLE_ID:-com.mediasfu.MediaSFUSampleApp}"
XCTRUNNER_BUNDLE_ID="${MEDIA_SFU_IOS_XCTRUNNER_BUNDLE_ID:-com.mediasfu.MediaSFUSampleAppUITests.xctrunner}"
SCHEME="${MEDIA_SFU_IOS_SCHEME:-MediaSFUSampleApp}"
CONFIGURATION="${MEDIA_SFU_IOS_CONFIGURATION:-Debug}"
DEVELOPMENT_TEAM="${MEDIA_SFU_IOS_DEVELOPMENT_TEAM:-}"  # Required: export MEDIA_SFU_IOS_DEVELOPMENT_TEAM=<your Apple Developer Team ID>
DERIVED_DATA_PATH="${MEDIA_SFU_IOS_DERIVED_DATA_PATH:-/tmp/mediasfu-ios-physical-media-probe}"
SHARED_DIR="${MEDIA_SFU_SHARED_DIR:-$ROOT_DIR/../shared}"
GRADLEW_BIN="${MEDIA_SFU_GRADLEW_BIN:-$ROOT_DIR/../gradlew}"
DEVICE_ARCHS="${MEDIASFU_KOTLIN_DEVICE_ARCHS:-arm64}"
ROOM_DURATION_MINUTES="${MEDIASFU_ROOM_DURATION_MINUTES:-60}"
API_USERNAME="${MEDIASFU_API_USERNAME:-}"  # Required: export MEDIASFU_API_USERNAME before running
ROOMS_ENDPOINT="${MEDIASFU_CLOUD_ROOMS_ENDPOINT:-https://mediasfu.com/v1/rooms}" # ENDPOINT_TOGGLE
CREDS_FILE="${MEDIASFU_CREDS_FILE:-/tmp/mediasfu_creds.txt}"
EVENT_TYPE="${MEDIASFU_EVENT_TYPE:-webinar}"
DEFAULT_IOS_ISLEVEL="0"
# Probe joiners should default to the plain participant role.
# Only the event creator / host should be islevel=2. Promote to islevel=1 explicitly
# only for a dedicated co-host/moderator test; otherwise React/Flutter host placement
# and viewer layout comparisons drift.
ROOM_CREATOR_ISLEVEL="${MEDIASFU_ROOM_CREATOR_ISLEVEL:-2}"
IOS_USER_ISLEVEL="${MEDIASFU_IOS_ISLEVEL:-$DEFAULT_IOS_ISLEVEL}"
# Give the joined browser a short settle window before automation starts producing media.
# This avoids null/undefined transport timing races that show up when production starts too early.
CHROME_JOIN_SETTLE_SECONDS="${MEDIASFU_CHROME_JOIN_SETTLE_SECONDS:-2}"
CHROME_PATH="${MEDIASFU_CHROME_PATH:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
CHROME_DEBUG_PORT="${MEDIASFU_CHROME_DEBUG_PORT:-$((9300 + RANDOM % 500))}"
RUN_MARKER_PREFLIGHT="${MEDIASFU_RUN_MARKER_PREFLIGHT:-1}"
RUN_ID="${MEDIASFU_PROBE_RUN_ID:-iosphys$(date +%Y%m%d%H%M%S)}"
SCREENSHARE_SOURCE_MODE="${MEDIASFU_SCREENSHARE_SOURCE:-ios}"
RUN_STATE_DIR="${MEDIASFU_RUN_STATE_DIR:-/tmp}"
LOCK_DIR="${MEDIASFU_PROBE_LOCK_DIR:-$RUN_STATE_DIR/mediasfu-ios-physical-probe.lock}"
RUN_ID_TOKEN="$(printf '%s' "$RUN_ID" | tr -cd '[:alnum:]')"
RUN_ID_SUFFIX="${RUN_ID_TOKEN: -4}"
if [[ -z "$RUN_ID_SUFFIX" ]]; then
  RUN_ID_SUFFIX="$(date +%H%M)"
fi
WEB_USER="${MEDIASFU_WEB_USER_NAME:-web${RUN_ID_SUFFIX}}"
IOS_USER="${MEDIASFU_USER_NAME:-ios${RUN_ID_SUFFIX}}"
CREATE_RESPONSE="$RUN_STATE_DIR/mediasfu_create_${RUN_ID}.json"
JOIN_RESPONSE="$RUN_STATE_DIR/mediasfu_join_probe_${RUN_ID}.json"
ENV_FILE="$RUN_STATE_DIR/mediasfu_ui_test_env_${RUN_ID}.txt"
CURRENT_ENV_FILE="$RUN_STATE_DIR/mediasfu_ui_test_env.txt"
CHROME_PROFILE="$RUN_STATE_DIR/mediasfu-chrome-${RUN_ID}"
LOG_PREFIX="$RUN_STATE_DIR/mediasfu_ios_physical_${RUN_ID}"
CAPTURE_HELPER="$ROOT_DIR/scripts/capture_chrome_room_evidence.js"
BROWSER_CAPTURE_SOURCE_TITLE="${MEDIASFU_BROWSER_CAPTURE_SOURCE_TITLE:-MediaSFU Probe Capture Source}"
BROWSER_CAPTURE_SOURCE_HTML="$RUN_STATE_DIR/mediasfu_browser_capture_source_${RUN_ID}.html"
PREUNLOCK_LOGIN_KEYCHAIN="${MEDIASFU_PREUNLOCK_LOGIN_KEYCHAIN:-0}"
LOGIN_KEYCHAIN_PATH="${MEDIASFU_LOGIN_KEYCHAIN_PATH:-$HOME/Library/Keychains/login.keychain-db}"
BROWSER_SCREENSHARE_TIMING="${MEDIASFU_BROWSER_SCREENSHARE_TIMING:-postjoin,prejoin}"
FAIL_ON_OPTIONAL_PROBE_FAILURE="${MEDIASFU_FAIL_ON_OPTIONAL_PROBE_FAILURE:-0}"
USE_INSTALLED_APP="${MEDIASFU_USE_INSTALLED_APP:-0}"
INSTALLED_APP_PROBE_TIMEOUT_SECONDS="${MEDIASFU_INSTALLED_APP_PROBE_TIMEOUT_SECONDS:-90}"
PRESYNC_SHARED_FRAMEWORK="${MEDIASFU_PRESYNC_SHARED_FRAMEWORK:-1}"

CHROME_PID=""
EVIDENCE_PID=""
EVIDENCE_LABEL=""
EVIDENCE_JSON=""
EVIDENCE_PNG=""
EVIDENCE_LOG=""
BROWSER_SHARE_TRIGGER_PID=""
BROWSER_SHARE_TRIGGER_LOG=""
OPTIONAL_PROBE_FAILURES=0
CURRENT_EXPECTED_SUBSTRING=""
APP_CONSOLE_PID=""
APP_CONSOLE_LOG=""

cleanup() {
  set +e

  if [[ -n "$BROWSER_SHARE_TRIGGER_PID" ]]; then
    kill "$BROWSER_SHARE_TRIGGER_PID" >/dev/null 2>&1 || true
    wait "$BROWSER_SHARE_TRIGGER_PID" >/dev/null 2>&1 || true
    BROWSER_SHARE_TRIGGER_PID=""
  fi

  if [[ -n "$APP_CONSOLE_PID" ]]; then
    kill "$APP_CONSOLE_PID" >/dev/null 2>&1 || true
    wait "$APP_CONSOLE_PID" >/dev/null 2>&1 || true
    APP_CONSOLE_PID=""
  fi

  if [[ -n "$EVIDENCE_PID" ]]; then
    kill "$EVIDENCE_PID" >/dev/null 2>&1 || true
    wait "$EVIDENCE_PID" >/dev/null 2>&1 || true
    EVIDENCE_PID=""
  fi

  if [[ -n "$CHROME_PID" ]]; then
    kill "$CHROME_PID" >/dev/null 2>&1 || true
    wait "$CHROME_PID" >/dev/null 2>&1 || true
    CHROME_PID=""
  fi

  if [[ -n "${CHROME_PROFILE:-}" ]]; then
    pkill -TERM -f "$CHROME_PROFILE" >/dev/null 2>&1 || true
    rm -rf "$CHROME_PROFILE" >/dev/null 2>&1 || true
  fi

  rm -f "$BROWSER_CAPTURE_SOURCE_HTML" >/dev/null 2>&1 || true
  rm -rf "$LOCK_DIR" >/dev/null 2>&1 || true
}

trap cleanup EXIT INT TERM

if [[ -f "$CREDS_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$CREDS_FILE"
  set +a
fi

API_KEY="${MEDIASFU_API_KEY:-}"
if [[ -z "$API_KEY" ]]; then
  echo "MEDIASFU_API_KEY is required, either exported or available in $CREDS_FILE." >&2
  exit 2
fi

for required in curl jq node xcodebuild xcrun; do
  if ! command -v "$required" >/dev/null 2>&1; then
    echo "Missing required command: $required" >&2
    exit 2
  fi
done

if [[ ! -x "$CHROME_PATH" ]]; then
  echo "Chrome executable not found at: $CHROME_PATH" >&2
  exit 2
fi

if [[ ! -f "$CAPTURE_HELPER" ]]; then
  echo "Capture helper script not found at: $CAPTURE_HELPER" >&2
  exit 2
fi

case "$SCREENSHARE_SOURCE_MODE" in
  ios|browser)
    ;;
  *)
    echo "Unsupported MEDIASFU_SCREENSHARE_SOURCE: $SCREENSHARE_SOURCE_MODE (expected 'ios' or 'browser')." >&2
    exit 2
    ;;
esac

umask 077

acquire_probe_lock() {
  if mkdir "$LOCK_DIR" >/dev/null 2>&1; then
    printf '%s\n' "$$" > "$LOCK_DIR/pid"
    return
  fi

  local existing_pid=""
  if [[ -f "$LOCK_DIR/pid" ]]; then
    existing_pid="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
  fi

  if [[ -n "$existing_pid" ]] && kill -0 "$existing_pid" >/dev/null 2>&1; then
    echo "Another physical probe is already running (pid=$existing_pid). Stop it before launching a new one." >&2
    exit 2
  fi

  rm -rf "$LOCK_DIR"
  mkdir "$LOCK_DIR"
  printf '%s\n' "$$" > "$LOCK_DIR/pid"
}

acquire_probe_lock

maybe_unlock_login_keychain() {
  if [[ "$PREUNLOCK_LOGIN_KEYCHAIN" != "1" ]]; then
    return
  fi

  if [[ ! -f "$LOGIN_KEYCHAIN_PATH" ]]; then
    echo "Skipping login keychain pre-unlock; keychain not found at $LOGIN_KEYCHAIN_PATH."
    return
  fi

  if [[ ! -t 0 || ! -t 1 ]]; then
    echo "Skipping login keychain pre-unlock because no interactive terminal is attached."
    return
  fi

  echo "== Pre-unlocking login keychain for xcodebuild code signing =="
  security unlock-keychain "$LOGIN_KEYCHAIN_PATH" < /dev/tty > /dev/tty 2>&1 || true
  security set-keychain-settings -t 7200 "$LOGIN_KEYCHAIN_PATH" 2>/dev/null || true
}

maybe_unlock_login_keychain

presync_shared_framework() {
  if [[ "$PRESYNC_SHARED_FRAMEWORK" != "1" ]]; then
    export OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED="YES"
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

redact_env_file() {
  sed -E 's/(MEDIASFU_API_KEY|MEDIASFU_ADMIN_PASSCODE)=.*/\1=<redacted>/' "$1"
}

record_optional_probe_failure() {
  local label="$1"
  echo "Probe failed: $label" >&2
  if [[ "$FAIL_ON_OPTIONAL_PROBE_FAILURE" == "1" ]]; then
    OPTIONAL_PROBE_FAILURES=$((OPTIONAL_PROBE_FAILURES + 1))
  fi
}

stop_installed_app_console() {
  if [[ -n "$APP_CONSOLE_PID" ]]; then
    kill "$APP_CONSOLE_PID" >/dev/null 2>&1 || true
    wait "$APP_CONSOLE_PID" >/dev/null 2>&1 || true
    APP_CONSOLE_PID=""
  fi
}

create_room() {
  local create_status
  create_status=$(curl -sS -o "$CREATE_RESPONSE" -w "%{http_code}" -X POST "$ROOMS_ENDPOINT" \
    -H "Authorization: Bearer ${API_USERNAME}:${API_KEY}" \
    -H "Content-Type: application/json" \
    --data-raw "{\"action\":\"create\",\"duration\":${ROOM_DURATION_MINUTES},\"capacity\":4,\"userName\":\"${WEB_USER}\",\"roomName\":\"\",\"adminPasscode\":\"\",\"islevel\":\"${ROOM_CREATOR_ISLEVEL}\",\"eventType\":\"${EVENT_TYPE}\"}")

  ROOM_NAME="$(jq -r '.roomName // empty' "$CREATE_RESPONSE")"
  SECRET="$(jq -r '.secret // empty' "$CREATE_RESPONSE")"
  PUBLIC_URL="$(jq -r '.publicURL // empty' "$CREATE_RESPONSE")"

  if [[ "$create_status" != "201" || -z "$ROOM_NAME" || -z "$SECRET" || -z "$PUBLIC_URL" ]]; then
    echo "Room creation failed with HTTP $create_status." >&2
    jq -r '{success, message, error, roomName}' "$CREATE_RESPONSE" >&2
    exit 3
  fi

  echo "Created $ROOM_DURATION_MINUTES-minute room: $ROOM_NAME (runId=$RUN_ID, webUser=$WEB_USER)."
}

verify_join_api() {
  local join_payload join_status join_success
  if [[ "$IOS_USER_ISLEVEL" == "2" ]]; then
    join_payload="{\"action\":\"join\",\"meetingID\":\"${ROOM_NAME}\",\"userName\":\"${IOS_USER}\",\"adminPasscode\":\"${SECRET}\",\"islevel\":\"${IOS_USER_ISLEVEL}\"}"
  else
    join_payload="{\"action\":\"join\",\"meetingID\":\"${ROOM_NAME}\",\"userName\":\"${IOS_USER}\",\"islevel\":\"${IOS_USER_ISLEVEL}\"}"
  fi

  join_status=$(curl -sS -o "$JOIN_RESPONSE" -w "%{http_code}" -X POST "$ROOMS_ENDPOINT" \
    -H "Authorization: Bearer ${API_USERNAME}:${API_KEY}" \
    -H "Content-Type: application/json" \
    --data-raw "$join_payload")
  join_success="$(jq -r '.success // false' "$JOIN_RESPONSE")"

  if [[ "$join_status" != "200" || "$join_success" != "true" ]]; then
    echo "Join preflight failed with HTTP $join_status." >&2
    jq -r '{success, message, error, roomName, meetingID}' "$JOIN_RESPONSE" >&2
    exit 4
  fi

  echo "Join preflight passed for $ROOM_NAME using iOS islevel=$IOS_USER_ISLEVEL."
}

write_env_file() {
  local expected_substring="$1"
  local enable_audio="$2"
  local enable_video="$3"
  local enable_screenshare="${4:-0}"
  local probe_label="${5:-runtime-probe}"
  local probe_return_ui="${MEDIASFU_PROBE_RETURN_UI:-0}"
  local apply_background_value="0"
  local post_match_hold_seconds="${MEDIASFU_PROBE_POST_MATCH_HOLD_SECONDS:-0}"
  if [[ "$probe_label" == "local-av" || "$probe_label" == virtual-background* ]]; then
    apply_background_value="${MEDIASFU_PROBE_APPLY_BACKGROUND:-0}"
    post_match_hold_seconds="${MEDIASFU_PROBE_POST_MATCH_HOLD_SECONDS:-10}"
  fi
  local auto_proceed_value="1"
  if [[ "$probe_return_ui" == "1" ]]; then
    auto_proceed_value="0"
  fi
  CURRENT_EXPECTED_SUBSTRING="$expected_substring"

  cat > "$ENV_FILE" <<EOF_ENV
MEDIASFU_AUTO_LAUNCH=1
MEDIASFU_AUTO_PROCEED=${auto_proceed_value}
MEDIASFU_ACTION=join
MEDIASFU_API_USERNAME=${API_USERNAME}
MEDIASFU_API_KEY=${API_KEY}
MEDIASFU_CLOUD_ROOMS_ENDPOINT=${ROOMS_ENDPOINT}
MEDIASFU_USER_NAME=${IOS_USER}
MEDIASFU_ROOM_NAME=${ROOM_NAME}
MEDIASFU_ADMIN_PASSCODE=${SECRET}
MEDIASFU_ISLEVEL=${IOS_USER_ISLEVEL}
MEDIASFU_CONNECT_MEDIA_SFU=1
MEDIASFU_ENABLE_RUNTIME_PROBES=1
MEDIASFU_PROBE_RETURN_UI=${probe_return_ui}
MEDIASFU_PROBE_APPLY_BACKGROUND=${apply_background_value}
MEDIASFU_PROBE_BACKGROUND_ACTION=${MEDIASFU_PROBE_BACKGROUND_ACTION:-apply}
MEDIASFU_PROBE_BACKGROUND_NAME=${MEDIASFU_PROBE_BACKGROUND_NAME:-Light Blur}
MEDIASFU_CAPTURE_LIVE_PROBE_SCREENSHOTS=1
MEDIASFU_PROBE_LABEL=${probe_label}
MEDIASFU_SCREENSHOT_OUTPUT_DIR=${RUN_STATE_DIR}/ios-ui-${probe_label}
MEDIASFU_PROBE_ENABLE_LOCAL_AUDIO=${enable_audio}
MEDIASFU_PROBE_ENABLE_LOCAL_VIDEO=${enable_video}
MEDIASFU_PROBE_ENABLE_LOCAL_VIDEO_AFTER_BACKGROUND=${MEDIASFU_PROBE_ENABLE_LOCAL_VIDEO_AFTER_BACKGROUND:-0}
MEDIASFU_PROBE_ENABLE_SCREENSHARE=${enable_screenshare}
MEDIASFU_PROBE_MODE=join
MEDIASFU_PROBE_RUN_ID=${RUN_ID}
MEDIASFU_EXPECT_RUNTIME_PROBE_SUBSTRING=${expected_substring}
MEDIASFU_EXPECT_PRE_BACKGROUND_APPLY_SUBSTRING=${MEDIASFU_EXPECT_PRE_BACKGROUND_APPLY_SUBSTRING:-}
MEDIASFU_EXPECT_POST_BACKGROUND_SAVE_SUBSTRING=${MEDIASFU_EXPECT_POST_BACKGROUND_SAVE_SUBSTRING:-}
MEDIASFU_EXPECT_POST_BACKGROUND_VIDEO_SUBSTRING=${MEDIASFU_EXPECT_POST_BACKGROUND_VIDEO_SUBSTRING:-}
MEDIASFU_PROBE_POST_MATCH_HOLD_SECONDS=${post_match_hold_seconds}
EOF_ENV
  cp "$ENV_FILE" "$CURRENT_ENV_FILE"
}

copy_env_to_app_container() {
  xcrun devicectl device copy to \
    --device "$DEVICECTL_ID" \
    --domain-type appDataContainer \
    --domain-identifier "$APP_BUNDLE_ID" \
    --source "$ENV_FILE" \
    --destination Documents/mediasfu_sample_env.txt \
    --timeout 30 >/tmp/mediasfu_app_env_copy_${RUN_ID}.log 2>&1 || {
      echo "Failed to copy env file into installed app container; see /tmp/mediasfu_app_env_copy_${RUN_ID}.log" >&2
      exit 6
    }

  echo "Updated on-device app env: $APP_BUNDLE_ID/Documents/mediasfu_sample_env.txt"
  redact_env_file "$ENV_FILE"
}

ensure_xctrunner_container() {
  if xcrun devicectl device copy to \
    --device "$DEVICECTL_ID" \
    --domain-type appDataContainer \
    --domain-identifier "$XCTRUNNER_BUNDLE_ID" \
    --source "$ENV_FILE" \
    --destination Documents/mediasfu_ui_test_env.txt \
    --timeout 30 >/tmp/mediasfu_xctrunner_env_copy_${RUN_ID}.log 2>&1; then
    return
  fi

  echo "XCTest runner container is not ready; installing it with a bootstrap UI test."
  xcodebuild \
    -workspace MediaSFUSampleApp.xcworkspace \
    -scheme "$SCHEME" \
    -destination "platform=iOS,id=$DEVICE_ID" \
    -configuration "$CONFIGURATION" \
    -derivedDataPath "$DERIVED_DATA_PATH" \
    DEVELOPMENT_TEAM="$DEVELOPMENT_TEAM" \
    CODE_SIGN_STYLE=Automatic \
    -only-testing:MediaSFUSampleAppUITests/MediaSFUSampleAppUITests/testBootstrapFormRendersCoreControls \
    test >/tmp/mediasfu_xctrunner_bootstrap_${RUN_ID}.log 2>&1 || {
      echo "Bootstrap UI test failed; see /tmp/mediasfu_xctrunner_bootstrap_${RUN_ID}.log" >&2
      exit 5
    }

  xcrun devicectl device copy to \
    --device "$DEVICECTL_ID" \
    --domain-type appDataContainer \
    --domain-identifier "$XCTRUNNER_BUNDLE_ID" \
    --source "$ENV_FILE" \
    --destination Documents/mediasfu_ui_test_env.txt \
    --timeout 30 >/tmp/mediasfu_xctrunner_env_copy_${RUN_ID}.log 2>&1
}

copy_env_to_xctrunner() {
  if [[ "$USE_INSTALLED_APP" == "1" ]]; then
    copy_env_to_app_container
    return
  fi

  ensure_xctrunner_container

  local verify_file="$RUN_STATE_DIR/mediasfu_xctrunner_env_verify_${RUN_ID}.txt"
  rm -f "$verify_file"
  xcrun devicectl device copy from \
    --device "$DEVICECTL_ID" \
    --domain-type appDataContainer \
    --domain-identifier "$XCTRUNNER_BUNDLE_ID" \
    --source Documents/mediasfu_ui_test_env.txt \
    --destination "$verify_file" \
    --timeout 30 >/tmp/mediasfu_xctrunner_env_verify_${RUN_ID}.log 2>&1

  if ! cmp -s "$ENV_FILE" "$verify_file"; then
    echo "XCTest runner env verification failed." >&2
    echo "Expected:" >&2
    redact_env_file "$ENV_FILE" >&2
    echo "Actual:" >&2
    redact_env_file "$verify_file" >&2 || true
    exit 6
  fi

  echo "Updated on-device XCTest runner env: $XCTRUNNER_BUNDLE_ID/Documents/mediasfu_ui_test_env.txt"
  redact_env_file "$ENV_FILE"
}

run_installed_app_probe_test() {
  local label="$1"
  local expected_substring="$2"
  local log_file="${LOG_PREFIX}_${label}_installed_app.log"
  local timeout_seconds="$INSTALLED_APP_PROBE_TIMEOUT_SECONDS"

  stop_installed_app_console
  rm -f "$log_file"

  echo "== Launching installed app probe ($label) on physical iPhone =="
  xcrun devicectl device process launch \
    --device "$DEVICECTL_ID" \
    --terminate-existing \
    --console \
    "$APP_BUNDLE_ID" >"$log_file" 2>&1 &
  APP_CONSOLE_PID=$!

  for ((attempt = 0; attempt < timeout_seconds; attempt += 1)); do
    if grep -F "MEDIASFU_RUNTIME_PROBE" "$log_file" | grep -F "$expected_substring" >/dev/null 2>&1; then
      echo "Installed app runtime probe matched '$expected_substring' for label=$label."
      grep -F "MEDIASFU_RUNTIME_PROBE" "$log_file" | tail -n 1
      return 0
    fi

    if ! kill -0 "$APP_CONSOLE_PID" >/dev/null 2>&1; then
      echo "Installed app probe exited before matching '$expected_substring' for label=$label." >&2
      tail -n 120 "$log_file" >&2 || true
      APP_CONSOLE_PID=""
      return 1
    fi

    sleep 1
  done

  echo "Timed out waiting for installed app runtime probe substring '$expected_substring' for label=$label." >&2
  tail -n 160 "$log_file" >&2 || true
  stop_installed_app_console
  return 1
}

launch_chrome() {
  rm -rf "$CHROME_PROFILE"
  "$CHROME_PATH" \
    --remote-debugging-port="$CHROME_DEBUG_PORT" \
    --user-data-dir="$CHROME_PROFILE" \
    --no-first-run \
    --no-default-browser-check \
    --disable-background-networking \
    --disable-component-update \
    --disable-default-apps \
    --disable-extensions \
    --disable-notifications \
    --disable-sync \
    --metrics-recording-only \
    --mute-audio \
    --password-store=basic \
    --use-mock-keychain \
    --use-fake-ui-for-media-stream \
    --use-fake-device-for-media-stream \
    --enable-usermedia-screen-capturing \
    --auto-select-desktop-capture-source="$BROWSER_CAPTURE_SOURCE_TITLE" \
    --autoplay-policy=no-user-gesture-required \
    --window-size=1280,720 \
    --force-device-scale-factor=1 \
    --app="$PUBLIC_URL" >/tmp/mediasfu_chrome_${RUN_ID}.log 2>&1 &
  CHROME_PID=$!
}

prepare_browser_capture_source() {
  cat > "$BROWSER_CAPTURE_SOURCE_HTML" <<EOF_SOURCE
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${BROWSER_CAPTURE_SOURCE_TITLE}</title>
  <style>
    :root {
      color-scheme: light;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }
    body {
      margin: 0;
      min-height: 100vh;
      display: grid;
      place-items: center;
      background:
        radial-gradient(circle at top left, #fff5c4 0%, #fff5c4 18%, transparent 18.5%),
        linear-gradient(135deg, #0b3954 0%, #087e8b 45%, #bfd7ea 100%);
      color: #07263f;
    }
    main {
      width: min(900px, 92vw);
      padding: 48px;
      border-radius: 28px;
      background: rgba(255, 255, 255, 0.92);
      box-shadow: 0 24px 80px rgba(7, 38, 63, 0.28);
      text-align: center;
    }
    h1 {
      margin: 0 0 16px;
      font-size: clamp(36px, 6vw, 64px);
      line-height: 1.05;
    }
    p {
      margin: 0;
      font-size: clamp(18px, 2vw, 26px);
    }
    .run {
      margin-top: 24px;
      font-size: clamp(20px, 2.8vw, 34px);
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: #c81d25;
    }
  </style>
</head>
<body>
  <main>
    <h1>Browser Screen Share Source</h1>
    <p>Dedicated non-recursive capture surface for MediaSFU iOS automation.</p>
    <div class="run">${RUN_ID}</div>
  </main>
</body>
</html>
EOF_SOURCE
}

open_browser_capture_source() {
  prepare_browser_capture_source

  "$CHROME_PATH" \
    --user-data-dir="$CHROME_PROFILE" \
    --app="file://$BROWSER_CAPTURE_SOURCE_HTML" >/tmp/mediasfu_chrome_capture_source_${RUN_ID}.log 2>&1 &
}

close_chrome() {
  if [[ -n "$CHROME_PID" ]]; then
    kill "$CHROME_PID" >/dev/null 2>&1 || true
    wait "$CHROME_PID" >/dev/null 2>&1 || true
    CHROME_PID=""
  fi

  pkill -TERM -f "$CHROME_PROFILE" >/dev/null 2>&1 || true
  rm -rf "$CHROME_PROFILE" >/dev/null 2>&1 || true
}

finish_web_evidence_capture() {
  if [[ -z "$EVIDENCE_PID" ]]; then
    return
  fi

  local exit_code=0
  wait "$EVIDENCE_PID" || exit_code=$?

  if [[ -f "$EVIDENCE_JSON" ]]; then
    echo "Web evidence label=$EVIDENCE_LABEL json=$EVIDENCE_JSON png=$EVIDENCE_PNG exit=$exit_code"
    cat "$EVIDENCE_JSON"
  else
    echo "Web evidence missing for label=$EVIDENCE_LABEL (exit=$exit_code)."
    if [[ -f "$EVIDENCE_LOG" ]]; then
      tail -n 80 "$EVIDENCE_LOG"
    fi
  fi

  EVIDENCE_PID=""
  return "$exit_code"
}

start_web_evidence_capture() {
  local label="$1"
  local min_active_videos="$2"
  local timeout_seconds="$3"

  EVIDENCE_LABEL="$label"
  EVIDENCE_JSON="$RUN_STATE_DIR/mediasfu_web_${label}_${RUN_ID}.json"
  EVIDENCE_PNG="$RUN_STATE_DIR/mediasfu_web_${label}_${RUN_ID}.png"
  EVIDENCE_LOG="$RUN_STATE_DIR/mediasfu_web_${label}_${RUN_ID}.log"

  rm -f "$EVIDENCE_JSON" "$EVIDENCE_PNG" "$EVIDENCE_LOG"

  CHROME_DEBUG_PORT="$CHROME_DEBUG_PORT" \
    ROOM_NAME="$ROOM_NAME" \
    SHOT_PATH="$EVIDENCE_PNG" \
    JSON_PATH="$EVIDENCE_JSON" \
    MIN_ACTIVE_VIDEOS="$min_active_videos" \
    TIMEOUT_SECONDS="$timeout_seconds" \
    AUTO_CLICK=1 \
    DISPLAY_NAME_VALUE="$WEB_USER" \
    REQUIRE_REMOTE_VIDEO="${MEDIASFU_BROWSER_CAPTURE_REQUIRE_REMOTE_VIDEO:-1}" \
    MIN_REMOTE_VIDEOS="${MEDIASFU_BROWSER_CAPTURE_MIN_REMOTE_VIDEOS:-1}" \
    REMOTE_VIDEO_MUST_BE_NONBLANK="${MEDIASFU_BROWSER_CAPTURE_REMOTE_VIDEO_MUST_BE_NONBLANK:-1}" \
    POST_READY_DELAY_MS="${MEDIASFU_BROWSER_CAPTURE_POST_READY_MS:-0}" \
    PRODUCE_START_DELAY_MS="${MEDIASFU_BROWSER_PRODUCE_START_DELAY_MS:-2000}" \
    node "$CAPTURE_HELPER" >"$EVIDENCE_LOG" 2>&1 &
  EVIDENCE_PID=$!
}

start_browser_screenshare() {
  local label="browser_share_source"
  local json_path="$RUN_STATE_DIR/mediasfu_web_${label}_${RUN_ID}.json"
  local png_path="$RUN_STATE_DIR/mediasfu_web_${label}_${RUN_ID}.png"
  local log_path="$RUN_STATE_DIR/mediasfu_web_${label}_${RUN_ID}.log"

  rm -f "$json_path" "$png_path" "$log_path"

  open_browser_capture_source

  if ! CHROME_DEBUG_PORT="$CHROME_DEBUG_PORT" \
    ROOM_NAME="$ROOM_NAME" \
    SHOT_PATH="$png_path" \
    JSON_PATH="$json_path" \
    MIN_ACTIVE_VIDEOS="${MEDIASFU_BROWSER_SHARE_MIN_ACTIVE_VIDEOS:-1}" \
    TIMEOUT_SECONDS="${MEDIASFU_BROWSER_SHARE_TIMEOUT_SECONDS:-60}" \
    AUTO_CLICK=1 \
    CLICK_LABELS="${MEDIASFU_BROWSER_SHARE_TRIGGER_LABELS:-Share Screen,Share}" \
    CLICK_SETTLE_MS="${MEDIASFU_BROWSER_SHARE_CLICK_SETTLE_MS:-4000}" \
    CLICK_REPEAT_MS="${MEDIASFU_BROWSER_SHARE_CLICK_REPEAT_MS:-8000}" \
    WAIT_FOR_TEXT="${MEDIASFU_BROWSER_SHARE_WAIT_FOR_TEXT:-Stop Share}" \
    REQUIRE_PRODUCE_TAGS="${MEDIASFU_BROWSER_SHARE_REQUIRE_PRODUCE_TAGS:-screen}" \
    PRODUCE_START_DELAY_MS="${MEDIASFU_BROWSER_SHARE_PRODUCE_START_DELAY_MS:-${MEDIASFU_BROWSER_PRODUCE_START_DELAY_MS:-2000}}" \
    node "$CAPTURE_HELPER" >"$log_path" 2>&1; then
    echo "Browser screen-share bootstrap failed; see $log_path" >&2
    tail -n 120 "$log_path" >&2 || true
    return 1
  fi

  echo "Browser share evidence json=$json_path png=$png_path"
  cat "$json_path"
}

stop_browser_screenshare() {
  local label="browser_share_stop"
  local json_path="$RUN_STATE_DIR/mediasfu_web_${label}_${RUN_ID}.json"
  local png_path="$RUN_STATE_DIR/mediasfu_web_${label}_${RUN_ID}.png"
  local log_path="$RUN_STATE_DIR/mediasfu_web_${label}_${RUN_ID}.log"

  rm -f "$json_path" "$png_path" "$log_path"

  if ! CHROME_DEBUG_PORT="$CHROME_DEBUG_PORT" \
    ROOM_NAME="$ROOM_NAME" \
    SHOT_PATH="$png_path" \
    JSON_PATH="$json_path" \
    MIN_ACTIVE_VIDEOS="${MEDIASFU_BROWSER_SHARE_STOP_MIN_ACTIVE_VIDEOS:-1}" \
    TIMEOUT_SECONDS="${MEDIASFU_BROWSER_SHARE_STOP_TIMEOUT_SECONDS:-30}" \
    AUTO_CLICK=1 \
    CLICK_LABELS="${MEDIASFU_BROWSER_SHARE_STOP_TRIGGER_LABELS:-Stop Share,Stop sharing}" \
    CLICK_SETTLE_MS="${MEDIASFU_BROWSER_SHARE_STOP_CLICK_SETTLE_MS:-0}" \
    CLICK_REPEAT_MS="${MEDIASFU_BROWSER_SHARE_STOP_CLICK_REPEAT_MS:-3000}" \
    WAIT_FOR_TEXT="${MEDIASFU_BROWSER_SHARE_STOP_WAIT_FOR_TEXT:-Share Screen}" \
    node "$CAPTURE_HELPER" >"$log_path" 2>&1; then
    echo "Browser screen-share stop failed; see $log_path" >&2
    tail -n 120 "$log_path" >&2 || true
    return 1
  fi

  echo "Browser share stop evidence json=$json_path png=$png_path"
  cat "$json_path"
}

schedule_browser_screenshare() {
  local delay_seconds="$1"
  local timing_label="$2"

  BROWSER_SHARE_TRIGGER_LOG="$RUN_STATE_DIR/mediasfu_browser_share_${timing_label}_${RUN_ID}.log"
  rm -f "$BROWSER_SHARE_TRIGGER_LOG"

  (
    if [[ "$delay_seconds" != "0" ]]; then
      sleep "$delay_seconds"
    fi
    start_browser_screenshare
  ) >"$BROWSER_SHARE_TRIGGER_LOG" 2>&1 &
  BROWSER_SHARE_TRIGGER_PID=$!
}

wait_for_scheduled_browser_screenshare() {
  if [[ -z "$BROWSER_SHARE_TRIGGER_PID" ]]; then
    return 0
  fi

  local exit_code=0
  if ! wait "$BROWSER_SHARE_TRIGGER_PID"; then
    exit_code=$?
  fi

  if [[ "$exit_code" != "0" ]]; then
    echo "Scheduled browser screen-share trigger failed; see $BROWSER_SHARE_TRIGGER_LOG" >&2
    tail -n 120 "$BROWSER_SHARE_TRIGGER_LOG" >&2 || true
  fi

  BROWSER_SHARE_TRIGGER_PID=""
  return "$exit_code"
}

verify_chrome_media() {
  local allow_dom_active_tag_fallback="${1:-0}"
  local label="browser_av_source"
  local json_path="$RUN_STATE_DIR/mediasfu_web_${label}_${RUN_ID}.json"
  local png_path="$RUN_STATE_DIR/mediasfu_web_${label}_${RUN_ID}.png"
  local log_path="$RUN_STATE_DIR/mediasfu_web_${label}_${RUN_ID}.log"

  rm -f "$json_path" "$png_path" "$log_path"

  if ! CHROME_DEBUG_PORT="$CHROME_DEBUG_PORT" \
    ROOM_NAME="$ROOM_NAME" \
    SHOT_PATH="$png_path" \
    JSON_PATH="$json_path" \
    MIN_ACTIVE_VIDEOS="${MEDIASFU_BROWSER_AV_MIN_ACTIVE_VIDEOS:-1}" \
    TIMEOUT_SECONDS="${MEDIASFU_BROWSER_AV_TIMEOUT_SECONDS:-90}" \
    AUTO_CLICK=1 \
    DISPLAY_NAME_VALUE="$WEB_USER" \
    REQUIRE_REMOTE_VIDEO=0 \
    REQUIRE_PRODUCE_TAGS="${MEDIASFU_BROWSER_AV_REQUIRE_PRODUCE_TAGS:-audio,video}" \
    ALLOW_DOM_ACTIVE_TAG_FALLBACK="$allow_dom_active_tag_fallback" \
    PRODUCE_START_DELAY_MS="${MEDIASFU_BROWSER_AV_PRODUCE_START_DELAY_MS:-${MEDIASFU_BROWSER_PRODUCE_START_DELAY_MS:-2000}}" \
    PRODUCE_RETRY_AFTER_MS="${MEDIASFU_BROWSER_AV_PRODUCE_RETRY_AFTER_MS:-12000}" \
    DEBUG_FIELDS="${MEDIASFU_BROWSER_AV_DEBUG_FIELDS:-0}" \
    node "$CAPTURE_HELPER" >"$log_path" 2>&1; then
    echo "Chrome fake-media peer did not produce server-visible audio/video; see $log_path" >&2
    tail -n 160 "$log_path" >&2 || true
    return 1
  fi

  echo "Browser AV evidence json=$json_path png=$png_path"
  cat "$json_path"
}

run_probe_test() {
  local label="$1"
  local log_file="${LOG_PREFIX}_${label}.log"
  local test_exit=0

  if [[ "$USE_INSTALLED_APP" == "1" ]]; then
    run_installed_app_probe_test "$label" "$CURRENT_EXPECTED_SUBSTRING"
    return
  fi

  set -o pipefail
  xcodebuild \
    -workspace MediaSFUSampleApp.xcworkspace \
    -scheme "$SCHEME" \
    -destination "platform=iOS,id=$DEVICE_ID" \
    -configuration "$CONFIGURATION" \
    -derivedDataPath "$DERIVED_DATA_PATH" \
    DEVELOPMENT_TEAM="$DEVELOPMENT_TEAM" \
    CODE_SIGN_STYLE=Automatic \
    -only-testing:MediaSFUSampleAppUITests/MediaSFUSampleAppUITests/testRuntimeProbeMatchesExpectedLiveState \
    test 2>&1 | tee "$log_file" | grep -E 'MediaSFU UI Test env|MediaSFU runtime probe matched|Test Case|Timed out waiting|Final value|TEST SUCCEEDED|TEST FAILED|Executed|error:' || test_exit=$?

  copy_probe_screenshots "$label"
  return "$test_exit"
}

copy_probe_screenshots() {
  local label="$1"
  local device_source="Documents/mediasfu_probe_screenshots/${label}-${RUN_ID}"
  local destination="${RUN_STATE_DIR}/ios-ui-${label}"

  rm -rf "$destination"
  if xcrun devicectl device copy from \
    --device "$DEVICECTL_ID" \
    --domain-type appDataContainer \
    --domain-identifier "$XCTRUNNER_BUNDLE_ID" \
    --source "$device_source" \
    --destination "$destination" \
    --timeout 30 >/tmp/mediasfu_probe_screenshot_copy_${RUN_ID}_${label}.log 2>&1; then
    echo "Collected iPhone UI screenshots: ${destination}"
  fi
}

run_browser_screenshare_probe_timing() {
  local timing="$1"
  local screenshare_expect_substring="$2"
  local local_screenshare_min_active="$3"
  local local_screenshare_timeout="$4"
  local probe_label="screenshare_${timing}"
  local probe_exit=0
  local share_exit=0
  local stop_exit=0
  local ios_enable_audio="${MEDIASFU_BROWSER_SHARE_IOS_ENABLE_LOCAL_AUDIO:-0}"
  local ios_enable_video="${MEDIASFU_BROWSER_SHARE_IOS_ENABLE_LOCAL_VIDEO:-0}"

  echo "== Running browser-to-Swift screen-share probe (${timing}) on physical iPhone =="
  write_env_file "$screenshare_expect_substring" "$ios_enable_audio" "$ios_enable_video" 0 "screenshare-setup"
  copy_env_to_xctrunner
  start_web_evidence_capture "$probe_label" "$local_screenshare_min_active" "$local_screenshare_timeout"

  case "$timing" in
    postjoin)
      local postjoin_delay_seconds="${MEDIASFU_BROWSER_SHARE_AFTER_JOIN_DELAY_SECONDS:-8}"
      echo "== Scheduling browser screen-share source ${postjoin_delay_seconds}s after iPhone join starts =="
      schedule_browser_screenshare "$postjoin_delay_seconds" "$timing"
      ;;
    prejoin)
      local prejoin_settle_seconds="${MEDIASFU_BROWSER_SHARE_PHASE_SETTLE_SECONDS:-8}"
      if [[ "$prejoin_settle_seconds" != "0" ]]; then
        echo "== Waiting ${prejoin_settle_seconds}s before browser screen-share start =="
        sleep "$prejoin_settle_seconds"
      fi
      echo "== Starting browser screen-share source before iPhone join =="
      start_browser_screenshare || share_exit=$?
      ;;
    *)
      echo "Unsupported MEDIASFU_BROWSER_SCREENSHARE_TIMING entry: $timing" >&2
      finish_web_evidence_capture
      return 2
      ;;
  esac

  if [[ "$share_exit" == "0" ]]; then
    run_probe_test "$probe_label" || probe_exit=$?
  else
    probe_exit=1
  fi

  if [[ "$timing" == "postjoin" ]]; then
    wait_for_scheduled_browser_screenshare || share_exit=$?
  fi

  finish_web_evidence_capture

  if [[ "$timing" == "postjoin" ]]; then
    stop_browser_screenshare || stop_exit=$?
    if [[ "$stop_exit" == "0" ]]; then
      local reset_settle_seconds="${MEDIASFU_BROWSER_SHARE_RESET_SETTLE_SECONDS:-2}"
      if [[ "$reset_settle_seconds" != "0" ]]; then
        echo "== Waiting ${reset_settle_seconds}s after browser screen-share stop =="
        sleep "$reset_settle_seconds"
      fi
    elif [[ "$share_exit" == "0" ]]; then
      share_exit=$stop_exit
    fi
  fi

  if [[ "$probe_exit" == "0" && "$share_exit" == "0" ]]; then
    echo "Screen-share probe test succeeded for timing=$timing."
    return 0
  fi

  echo "Screen-share probe test failed for timing=$timing."
  if [[ "$probe_exit" != "0" ]]; then
    return "$probe_exit"
  fi
  return "$share_exit"
}

run_virtual_background_probe_path() {
  local path="$1"
  local background_name="${MEDIASFU_PROBE_BACKGROUND_NAME:-Light Blur}"
  local background_summary="keepBackground=true;selectedBackground=${background_name};backgroundHasChanged=true"
  local probe_exit=0
  local evidence_exit=0
  local label=""
  local enable_audio="0"
  local enable_video="0"
  local web_label=""

  local MEDIASFU_PROBE_APPLY_BACKGROUND=1
  local MEDIASFU_EXPECT_PRE_BACKGROUND_APPLY_SUBSTRING=""
  local MEDIASFU_EXPECT_POST_BACKGROUND_SAVE_SUBSTRING=""
  local MEDIASFU_EXPECT_POST_BACKGROUND_VIDEO_SUBSTRING=""
  local MEDIASFU_PROBE_ENABLE_LOCAL_VIDEO_AFTER_BACKGROUND=0
  local MEDIASFU_PROBE_BACKGROUND_ACTION="apply"

  case "$path" in
    live)
      label="virtual-background-live"
      web_label="virtual-background-live"
      enable_audio="${MEDIASFU_VB_LIVE_ENABLE_LOCAL_AUDIO:-1}"
      enable_video="1"
      MEDIASFU_PROBE_BACKGROUND_ACTION="apply"
      MEDIASFU_EXPECT_PRE_BACKGROUND_APPLY_SUBSTRING="${MEDIASFU_VB_LIVE_PRE_APPLY_EXPECT_SUBSTRING:-videoProduced=1||backgroundHasChanged=false}"
      MEDIASFU_EXPECT_POST_BACKGROUND_VIDEO_SUBSTRING="${MEDIASFU_VB_LIVE_POST_APPLY_EXPECT_SUBSTRING:-videoProduced=1||${background_summary}}"
      ;;
    saved)
      label="virtual-background-saved"
      web_label="virtual-background-saved"
      enable_audio="${MEDIASFU_VB_SAVED_ENABLE_LOCAL_AUDIO:-0}"
      enable_video="0"
      MEDIASFU_PROBE_BACKGROUND_ACTION="save"
      MEDIASFU_PROBE_ENABLE_LOCAL_VIDEO_AFTER_BACKGROUND=1
      MEDIASFU_EXPECT_POST_BACKGROUND_SAVE_SUBSTRING="${MEDIASFU_VB_SAVED_POST_SAVE_EXPECT_SUBSTRING:-localVideo=false||videoProduced=0||${background_summary}}"
      MEDIASFU_EXPECT_POST_BACKGROUND_VIDEO_SUBSTRING="${MEDIASFU_VB_SAVED_POST_VIDEO_EXPECT_SUBSTRING:-localVideo=true||videoProduced=1||${background_summary}}"
      ;;
    *)
      echo "Unsupported virtual background path: $path" >&2
      return 2
      ;;
  esac

  echo "== Running virtual background ${path} probe on physical iPhone =="
  write_env_file "$background_summary" "$enable_audio" "$enable_video" 0 "$label"
  copy_env_to_xctrunner
  start_web_evidence_capture "$web_label" "${MEDIASFU_VB_MIN_ACTIVE_VIDEOS:-2}" "${MEDIASFU_VB_WEB_TIMEOUT_SECONDS:-75}"
  run_probe_test "$label" || probe_exit=$?
  finish_web_evidence_capture || evidence_exit=$?

  if [[ "$probe_exit" == "0" && "$evidence_exit" == "0" ]]; then
    echo "Virtual background ${path} probe succeeded."
  else
    echo "Virtual background ${path} probe failed." >&2
  fi
  if [[ "$probe_exit" != "0" ]]; then
    return "$probe_exit"
  fi
  return "$evidence_exit"
}

echo "== Creating fresh MediaSFU room =="
presync_shared_framework
create_room

echo "== Verifying API joinability =="
verify_join_api

echo "== Launching fake-media Chrome peer =="
launch_chrome
verify_chrome_media 0

if [[ "$CHROME_JOIN_SETTLE_SECONDS" != "0" ]]; then
  echo "== Waiting ${CHROME_JOIN_SETTLE_SECONDS}s for browser join settle =="
  sleep "$CHROME_JOIN_SETTLE_SECONDS"
fi

if [[ "$RUN_MARKER_PREFLIGHT" == "1" ]]; then
  echo "== Running current-marker preflight on physical iPhone =="
  write_env_file "launchMarker=${RUN_ID}" 0 0 0 "marker"
  copy_env_to_xctrunner
  run_probe_test marker
fi

if [[ "${MEDIASFU_RUN_EXISTING_MEDIA_PROBE:-0}" == "1" ]]; then
  existing_audio_expect_substring="${MEDIASFU_EXISTING_AUDIO_EXPECT_SUBSTRING:-audioResumes=1}"
  existing_video_expect_substring="${MEDIASFU_EXISTING_VIDEO_EXPECT_SUBSTRING:-videoMatches=1}"
  existing_probe_enable_local_audio="${MEDIASFU_EXISTING_PROBE_ENABLE_LOCAL_AUDIO:-0}"
  existing_probe_enable_local_video="${MEDIASFU_EXISTING_PROBE_ENABLE_LOCAL_VIDEO:-0}"

  if [[ "${MEDIASFU_REFRESH_BROWSER_BEFORE_EXISTING_PROBES:-1}" == "1" ]]; then
    echo "== Refreshing browser AV evidence before existing-media probes =="
    verify_chrome_media 1
  fi

  echo "== Running late-join existing-audio receive probe on physical iPhone =="
  write_env_file "$existing_audio_expect_substring" "$existing_probe_enable_local_audio" "$existing_probe_enable_local_video" 0 "existing-audio"
  copy_env_to_xctrunner
  if run_probe_test existingaudio; then
    echo "Existing-audio receive probe succeeded."
  else
    record_optional_probe_failure "existing audio receive"
  fi

  echo "== Running late-join existing-video receive probe on physical iPhone =="
  write_env_file "$existing_video_expect_substring" "$existing_probe_enable_local_audio" "$existing_probe_enable_local_video" 0 "existing-video"
  copy_env_to_xctrunner
  if run_probe_test existingvideo; then
    echo "Existing-video receive probe succeeded."
  else
    record_optional_probe_failure "existing video receive"
  fi
fi

echo "== Running video production probe on physical iPhone =="
local_video_expect_substring="${MEDIASFU_LOCAL_VIDEO_EXPECT_SUBSTRING:-audioProduced=1;videoProduced=1}"
video_probe_exit=0
video_evidence_exit=0
write_env_file "$local_video_expect_substring" 1 1 0 "local-av"
copy_env_to_xctrunner
start_web_evidence_capture "video" 2 45
run_probe_test video || video_probe_exit=$?
finish_web_evidence_capture || video_evidence_exit=$?
if [[ "$video_probe_exit" != "0" ]]; then
  exit "$video_probe_exit"
fi
if [[ "$video_evidence_exit" != "0" ]]; then
  exit "$video_evidence_exit"
fi

if [[ "${MEDIASFU_RUN_VIRTUAL_BACKGROUND_PROBE:-0}" == "1" ]]; then
  IFS=',' read -r -a vb_paths <<< "${MEDIASFU_VIRTUAL_BACKGROUND_PATHS:-live,saved}"
  for raw_path in "${vb_paths[@]}"; do
    vb_path="$(printf '%s' "$raw_path" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
    if [[ -z "$vb_path" ]]; then
      continue
    fi
    run_virtual_background_probe_path "$vb_path"
  done
fi

if [[ "${MEDIASFU_RUN_SCREENSHARE_PROBE:-0}" == "1" ]]; then
  local_screenshare_timeout="${MEDIASFU_SCREENSHARE_WEB_TIMEOUT_SECONDS:-60}"
  case "$SCREENSHARE_SOURCE_MODE" in
    ios)
      local_screenshare_min_active="${MEDIASFU_SCREENSHARE_MIN_ACTIVE_VIDEOS:-2}"
      screenshare_expect_substring="${MEDIASFU_SCREENSHARE_EXPECT_SUBSTRING:-localScreenShare=true}"

      echo "== Running iPhone-local screen-share probe on physical iPhone =="
      write_env_file "$screenshare_expect_substring" 1 1 1 "screenshare-live"
      copy_env_to_xctrunner
      start_web_evidence_capture "screenshare" "$local_screenshare_min_active" "$local_screenshare_timeout"
      if run_probe_test screenshare; then
        echo "Screen-share probe test succeeded."
      else
        record_optional_probe_failure "iPhone-local screen share"
      fi
      finish_web_evidence_capture
      ;;
    browser)
      local_screenshare_min_active="${MEDIASFU_SCREENSHARE_MIN_ACTIVE_VIDEOS:-2}"
      screenshare_expect_substring="${MEDIASFU_SCREENSHARE_EXPECT_SUBSTRING:-remoteScreenShare=true}"
      IFS=',' read -r -a browser_screenshare_timings <<< "$BROWSER_SCREENSHARE_TIMING"
      for raw_timing in "${browser_screenshare_timings[@]}"; do
        timing="$(printf '%s' "$raw_timing" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
        if [[ -z "$timing" ]]; then
          continue
        fi

        if [[ "$timing" == "both" ]]; then
          for expanded_timing in postjoin prejoin; do
            if run_browser_screenshare_probe_timing "$expanded_timing" "$screenshare_expect_substring" "$local_screenshare_min_active" "$local_screenshare_timeout"; then
              :
            else
              record_optional_probe_failure "browser screen share timing=$expanded_timing"
            fi
          done
          continue
        fi

        if run_browser_screenshare_probe_timing "$timing" "$screenshare_expect_substring" "$local_screenshare_min_active" "$local_screenshare_timeout"; then
          :
        else
          record_optional_probe_failure "browser screen share timing=$timing"
        fi
      done
      ;;
  esac
fi

if [[ "$OPTIONAL_PROBE_FAILURES" != "0" ]]; then
  echo "Physical iPhone media probe failed with $OPTIONAL_PROBE_FAILURES failed optional probe(s)." >&2
  exit 8
fi

echo "Physical iPhone media probe completed for room=$ROOM_NAME runId=$RUN_ID."
close_chrome
