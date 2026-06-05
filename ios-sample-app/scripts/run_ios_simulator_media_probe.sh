#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export MEDIA_SFU_ENABLE_IOS_NATIVE_BRIDGE_PACKAGE="${MEDIA_SFU_ENABLE_IOS_NATIVE_BRIDGE_PACKAGE:-1}"
export MEDIA_SFU_ENABLE_REAL_LIBMEDIASOUPCLIENT_BINDING="${MEDIA_SFU_ENABLE_REAL_LIBMEDIASOUPCLIENT_BINDING:-1}"
export MEDIASFU_REQUIRE_REAL_NATIVE_BRIDGE="${MEDIASFU_REQUIRE_REAL_NATIVE_BRIDGE:-1}"

SIMULATOR_ID="${MEDIA_SFU_IOS_SIMULATOR_ID:-29A868A7-9E5B-42A6-B1BF-5FBF5D865143}"
APP_BUNDLE_ID="${MEDIA_SFU_IOS_BUNDLE_ID:-com.mediasfu.MediaSFUSampleApp}"
SCHEME="${MEDIA_SFU_IOS_SCHEME:-MediaSFUSampleApp}"
CONFIGURATION="${MEDIA_SFU_IOS_CONFIGURATION:-Debug}"
DERIVED_DATA_PATH="${MEDIA_SFU_IOS_DERIVED_DATA_PATH:-/tmp/mediasfu-ios-simulator-media-probe}"
SHARED_DIR="${MEDIA_SFU_SHARED_DIR:-$ROOT_DIR/../shared}"
GRADLEW_BIN="${MEDIA_SFU_GRADLEW_BIN:-$ROOT_DIR/../gradlew}"
ROOM_DURATION_MINUTES="${MEDIASFU_ROOM_DURATION_MINUTES:-60}"
API_USERNAME="${MEDIASFU_API_USERNAME:-}"
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
# CRITICAL: keep the simulator probe on the same host/role assumptions as the physical lane.
# If the browser room creator falls back to islevel=0 here, host detection drifts and the
# Swift/KMP UI can appear to misplace media even when the rendering logic is otherwise correct.
# Give the joined browser a short settle window before automation starts producing media.
# This avoids transient null/undefined transport timing races during early production.
CHROME_JOIN_SETTLE_SECONDS="${MEDIASFU_CHROME_JOIN_SETTLE_SECONDS:-2}"
CHROME_PATH="${MEDIASFU_CHROME_PATH:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
CHROME_DEBUG_PORT="${MEDIASFU_CHROME_DEBUG_PORT:-$((9300 + RANDOM % 500))}"
RUN_MARKER_PREFLIGHT="${MEDIASFU_RUN_MARKER_PREFLIGHT:-1}"
RUN_EXISTING_MEDIA_PROBE="${MEDIASFU_RUN_EXISTING_MEDIA_PROBE:-1}"
RUN_AUDIO_PRODUCE_PROBE="${MEDIASFU_RUN_AUDIO_PRODUCE_PROBE:-1}"
RUN_VIDEO_PRODUCE_PROBE="${MEDIASFU_RUN_VIDEO_PRODUCE_PROBE:-1}"
RUN_SCREENSHARE_PROBE="${MEDIASFU_RUN_SCREENSHARE_PROBE:-1}"
SCREENSHARE_SOURCE_MODE="${MEDIASFU_SCREENSHARE_SOURCE:-browser}"
PRESYNC_SHARED_FRAMEWORK="${MEDIASFU_PRESYNC_SHARED_FRAMEWORK:-1}"
RUN_STATE_DIR="${MEDIASFU_RUN_STATE_DIR:-/tmp}"
RUN_ID="${MEDIASFU_PROBE_RUN_ID:-iossim$(date +%Y%m%d%H%M%S)}"
RUN_ID_TOKEN="$(printf '%s' "$RUN_ID" | tr -cd '[:alnum:]')"
RUN_ID_SUFFIX="${RUN_ID_TOKEN: -4}"
if [[ -z "$RUN_ID_SUFFIX" ]]; then
	RUN_ID_SUFFIX="$(date +%H%M)"
fi
WEB_USER="${MEDIASFU_WEB_USER_NAME:-web${RUN_ID_SUFFIX}}"
IOS_USER="${MEDIASFU_USER_NAME:-ios${RUN_ID_SUFFIX}}"
PREFLIGHT_IOS_USER="${MEDIASFU_IOS_PREFLIGHT_USER:-$IOS_USER}"
if [[ "$PREFLIGHT_IOS_USER" == "$IOS_USER" ]]; then
	if [[ ${#IOS_USER} -ge 10 ]]; then
		PREFLIGHT_IOS_USER="${IOS_USER:0:9}p"
	else
		PREFLIGHT_IOS_USER="${IOS_USER}p"
	fi
fi
CREATE_RESPONSE="$RUN_STATE_DIR/mediasfu_create_${RUN_ID}.json"
JOIN_RESPONSE="$RUN_STATE_DIR/mediasfu_join_probe_${RUN_ID}.json"
ENV_FILE="$RUN_STATE_DIR/mediasfu_ui_test_env_${RUN_ID}.txt"
CURRENT_ENV_FILE="$RUN_STATE_DIR/mediasfu_ui_test_env.txt"
CHROME_PROFILE="$RUN_STATE_DIR/mediasfu-chrome-${RUN_ID}"
LOG_PREFIX="$RUN_STATE_DIR/mediasfu_ios_simulator_${RUN_ID}"
CAPTURE_HELPER="$ROOT_DIR/scripts/capture_chrome_room_evidence.js"
BROWSER_CAPTURE_SOURCE_TITLE="${MEDIASFU_BROWSER_CAPTURE_SOURCE_TITLE:-MediaSFU Probe Capture Source}"
BROWSER_CAPTURE_SOURCE_HTML="$RUN_STATE_DIR/mediasfu_browser_capture_source_${RUN_ID}.html"
BROWSER_SCREENSHARE_TIMING="${MEDIASFU_BROWSER_SCREENSHARE_TIMING:-postjoin,prejoin}"
FAIL_ON_OPTIONAL_PROBE_FAILURE="${MEDIASFU_FAIL_ON_OPTIONAL_PROBE_FAILURE:-0}"

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

cleanup() {
	set +e

	if [[ -n "$BROWSER_SHARE_TRIGGER_PID" ]]; then
		kill "$BROWSER_SHARE_TRIGGER_PID" >/dev/null 2>&1 || true
		wait "$BROWSER_SHARE_TRIGGER_PID" >/dev/null 2>&1 || true
		BROWSER_SHARE_TRIGGER_PID=""
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
}

trap cleanup EXIT INT TERM

if [[ -f "$CREDS_FILE" ]]; then
	set -a
	# shellcheck disable=SC1090
	source "$CREDS_FILE"
	set +a
fi

API_USERNAME="${MEDIASFU_API_USERNAME:-}"
API_KEY="${MEDIASFU_API_KEY:-}"
if [[ -z "$API_USERNAME" ]]; then
	echo "MEDIASFU_API_USERNAME is required, either exported or available in $CREDS_FILE." >&2
	exit 2
fi
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

if [[ "$SCREENSHARE_SOURCE_MODE" != "browser" ]]; then
	echo "Simulator probe only supports MEDIASFU_SCREENSHARE_SOURCE=browser." >&2
	exit 2
fi

umask 077

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

boot_simulator() {
	xcrun simctl boot "$SIMULATOR_ID" >/dev/null 2>&1 || true
	open -a Simulator --args -CurrentDeviceUDID "$SIMULATOR_ID" >/dev/null 2>&1 || true
	xcrun simctl bootstatus "$SIMULATOR_ID" -b
	if ! xcrun simctl getenv "$SIMULATOR_ID" SIMULATOR_UDID >/dev/null 2>&1; then
		echo "Simulator did not become ready: $SIMULATOR_ID" >&2
		exit 3
	fi
	xcrun simctl privacy "$SIMULATOR_ID" grant microphone "$APP_BUNDLE_ID" >/dev/null 2>&1 || true
	xcrun simctl privacy "$SIMULATOR_ID" grant camera "$APP_BUNDLE_ID" >/dev/null 2>&1 || true
	printf 'Booted simulator: %s\n' "$SIMULATOR_ID"
}

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

	echo "== Pre-syncing shared simulator framework =="
	(
		cd "$SHARED_DIR"
		ARCHS="${MEDIASFU_KOTLIN_SIMULATOR_ARCHS:-arm64}" \
		SDK_NAME="${MEDIASFU_KOTLIN_SDK_NAME:-iphonesimulator}" \
		PLATFORM_NAME="iphonesimulator" \
		CONFIGURATION="$CONFIGURATION" \
		"$GRADLEW_BIN" --no-daemon -p "$PWD" :shared:syncFramework \
			-Pkotlin.native.cocoapods.platform=iphonesimulator \
			-Pkotlin.native.cocoapods.archs="${MEDIASFU_KOTLIN_SIMULATOR_ARCHS:-arm64}" \
			-Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
	)

	# Xcode can stall in the CocoaPods script phase while re-invoking the same sync task.
	export OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED="YES"
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
		exit 4
	fi

	echo "Created $ROOM_DURATION_MINUTES-minute room: $ROOM_NAME (runId=$RUN_ID, webUser=$WEB_USER)."
}

verify_join_api() {
	local join_payload join_status join_success
	if [[ "$IOS_USER_ISLEVEL" == "2" ]]; then
		join_payload="{\"action\":\"join\",\"meetingID\":\"${ROOM_NAME}\",\"userName\":\"${PREFLIGHT_IOS_USER}\",\"adminPasscode\":\"${SECRET}\",\"islevel\":\"${IOS_USER_ISLEVEL}\"}"
	else
		join_payload="{\"action\":\"join\",\"meetingID\":\"${ROOM_NAME}\",\"userName\":\"${PREFLIGHT_IOS_USER}\",\"islevel\":\"${IOS_USER_ISLEVEL}\"}"
	fi
	join_status=$(curl -sS -o "$JOIN_RESPONSE" -w "%{http_code}" -X POST "$ROOMS_ENDPOINT" \
		-H "Authorization: Bearer ${API_USERNAME}:${API_KEY}" \
		-H "Content-Type: application/json" \
		--data-raw "$join_payload")
	join_success="$(jq -r '.success // false' "$JOIN_RESPONSE")"

	if [[ "$join_status" != "200" || "$join_success" != "true" ]]; then
		echo "Join preflight failed with HTTP $join_status." >&2
		jq -r '{success, message, error, roomName, meetingID}' "$JOIN_RESPONSE" >&2
		exit 5
	fi

	echo "Join preflight passed for $ROOM_NAME using preflight user=$PREFLIGHT_IOS_USER islevel=$IOS_USER_ISLEVEL."
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
MEDIASFU_CAPTURE_SCREENSHOTS=1
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
	echo "Updated simulator UI test env: $CURRENT_ENV_FILE"
	redact_env_file "$ENV_FILE"
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

	# Give Chrome time to register a debuggable tab, especially after simulator/device resets.
	local launch_delay_seconds="${MEDIASFU_CHROME_LAUNCH_DELAY_SECONDS:-6}"
	if [[ "$launch_delay_seconds" != "0" ]]; then
		sleep "$launch_delay_seconds"
	fi
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
    <p>Dedicated non-recursive capture surface for MediaSFU iOS simulator automation.</p>
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
	local xcode_status=0

	xcodebuild \
		-workspace MediaSFUSampleApp.xcworkspace \
		-scheme "$SCHEME" \
		-destination "platform=iOS Simulator,id=$SIMULATOR_ID" \
		-configuration "$CONFIGURATION" \
		-derivedDataPath "$DERIVED_DATA_PATH" \
		-only-testing:MediaSFUSampleAppUITests/MediaSFUSampleAppUITests/testRuntimeProbeMatchesExpectedLiveState \
		test \
		CODE_SIGNING_ALLOWED=NO \
		2>&1 | tee "$log_file"
	xcode_status=${PIPESTATUS[0]}
	grep -E 'MediaSFU UI Test env|MediaSFU runtime probe matched|Test Case|Timed out waiting|Final value|TEST SUCCEEDED|TEST FAILED|Executed|error:' "$log_file" || true
	return "$xcode_status"
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

	echo "== Running browser-to-simulator screen-share probe (${timing}) =="
	write_env_file "$screenshare_expect_substring" 0 0 0 "screenshare-setup"
	start_web_evidence_capture "$probe_label" "$local_screenshare_min_active" "$local_screenshare_timeout"

	case "$timing" in
		postjoin)
			local postjoin_delay_seconds="${MEDIASFU_BROWSER_SHARE_AFTER_JOIN_DELAY_SECONDS:-8}"
			echo "== Scheduling browser screen-share source ${postjoin_delay_seconds}s after simulator join starts =="
			schedule_browser_screenshare "$postjoin_delay_seconds" "$timing"
			;;
		prejoin)
			local prejoin_settle_seconds="${MEDIASFU_BROWSER_SHARE_PHASE_SETTLE_SECONDS:-8}"
			if [[ "$prejoin_settle_seconds" != "0" ]]; then
				echo "== Waiting ${prejoin_settle_seconds}s before browser screen-share start =="
				sleep "$prejoin_settle_seconds"
			fi
			echo "== Starting browser screen-share source before simulator join =="
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

	echo "== Running virtual background ${path} probe on iOS simulator =="
	write_env_file "$background_summary" "$enable_audio" "$enable_video" 0 "$label"
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

echo "== Booting iOS simulator =="
boot_simulator

presync_shared_framework

echo "== Creating fresh MediaSFU room =="
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
	echo "== Running current-marker preflight on iOS simulator =="
	write_env_file "launchMarker=${RUN_ID}" 0 0 0 "marker"
	run_probe_test marker
fi

if [[ "$RUN_EXISTING_MEDIA_PROBE" == "1" ]]; then
	existing_audio_expect_substring="${MEDIASFU_EXISTING_AUDIO_EXPECT_SUBSTRING:-audioResumes=1}"
	existing_video_expect_substring="${MEDIASFU_EXISTING_VIDEO_EXPECT_SUBSTRING:-videoMatches=1}"
	existing_probe_enable_local_audio="${MEDIASFU_EXISTING_PROBE_ENABLE_LOCAL_AUDIO:-0}"
	existing_probe_enable_local_video="${MEDIASFU_EXISTING_PROBE_ENABLE_LOCAL_VIDEO:-0}"

	echo "== Running late-join existing-audio receive probe on iOS simulator =="
	write_env_file "$existing_audio_expect_substring" "$existing_probe_enable_local_audio" "$existing_probe_enable_local_video" 0 "existing-audio"
	if run_probe_test existingaudio; then
		echo "Existing-audio receive probe succeeded."
	else
		record_optional_probe_failure "existing audio receive"
	fi

	echo "== Running late-join existing-video receive probe on iOS simulator =="
	write_env_file "$existing_video_expect_substring" "$existing_probe_enable_local_audio" "$existing_probe_enable_local_video" 0 "existing-video"
	if run_probe_test existingvideo; then
		echo "Existing-video receive probe succeeded."
	else
		record_optional_probe_failure "existing video receive"
	fi
fi

if [[ "$RUN_AUDIO_PRODUCE_PROBE" == "1" ]]; then
	audio_expect_substring="${MEDIASFU_SIMULATOR_AUDIO_EXPECT_SUBSTRING:-audioProduced=1}"
	echo "== Running audio production probe on iOS simulator =="
	write_env_file "$audio_expect_substring" 1 0 0 "local-audio"
	run_probe_test audio
fi

if [[ "$RUN_VIDEO_PRODUCE_PROBE" == "1" ]]; then
	local_video_expect_substring="${MEDIASFU_LOCAL_VIDEO_EXPECT_SUBSTRING:-audioProduced=1;videoProduced=1}"
	video_probe_exit=0
	video_evidence_exit=0
	echo "== Running video production probe on iOS simulator =="
	write_env_file "$local_video_expect_substring" 1 1 0 "local-av"
	start_web_evidence_capture "video" "${MEDIASFU_VIDEO_MIN_ACTIVE_VIDEOS:-2}" "${MEDIASFU_VIDEO_WEB_TIMEOUT_SECONDS:-60}"
	run_probe_test video || video_probe_exit=$?
	finish_web_evidence_capture || video_evidence_exit=$?
	if [[ "$video_probe_exit" != "0" ]]; then
		exit "$video_probe_exit"
	fi
	if [[ "$video_evidence_exit" != "0" ]]; then
		exit "$video_evidence_exit"
	fi
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

if [[ "$RUN_SCREENSHARE_PROBE" == "1" ]]; then
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
				if run_browser_screenshare_probe_timing "$expanded_timing" "$screenshare_expect_substring" "$local_screenshare_min_active" "${MEDIASFU_SCREENSHARE_WEB_TIMEOUT_SECONDS:-60}"; then
					:
				else
					record_optional_probe_failure "browser screen share timing=$expanded_timing"
				fi
			done
			continue
		fi

		if run_browser_screenshare_probe_timing "$timing" "$screenshare_expect_substring" "$local_screenshare_min_active" "${MEDIASFU_SCREENSHARE_WEB_TIMEOUT_SECONDS:-60}"; then
			:
		else
			record_optional_probe_failure "browser screen share timing=$timing"
		fi
	done
fi

if [[ "$OPTIONAL_PROBE_FAILURES" != "0" ]]; then
	echo "iOS simulator media probe completed with $OPTIONAL_PROBE_FAILURES optional probe failure(s)." >&2
	exit 8
fi

echo "iOS simulator media probe completed for room=$ROOM_NAME runId=$RUN_ID."
echo "Simulator coverage is valid for browser-hosted consume flows and audio production; local camera video and iOS-local ReplayKit screenshare still require physical hardware."
