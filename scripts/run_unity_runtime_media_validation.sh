#!/usr/bin/env bash

set -euo pipefail
set +m >/dev/null 2>&1 || true

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"

default_unity_app="/Applications/Unity/Hub/Editor/2022.3.62f3/Unity.app"
repo_project_path="$repo_dir/unity-validation/MediaSfuUnityValidation"
legacy_project_path="$repo_dir/../.unity-validation/MediaSfuUnityValidation"

default_project_path="$repo_project_path"
if [[ ! -d "$default_project_path" && -d "$legacy_project_path" ]]; then
    default_project_path="$legacy_project_path"
fi

unity_app="${UNITY_APP_PATH:-$default_unity_app}"
project_path="${MEDIASFU_UNITY_PROJECT_PATH:-$default_project_path}"
base_url="${MEDIASFU_UNITY_BASE_URL:-https://mediasfu.com}"
local_link="${MEDIASFU_UNITY_LOCAL_LINK:-}"
timeout_seconds="${MEDIASFU_UNITY_RUNTIME_TIMEOUT_SECONDS:-300}"
log_file="${MEDIASFU_UNITY_LOG_FILE:--}"
keep_project_copy=false
dry_run=false
unity_pid=""

usage() {
    cat <<'EOF'
Usage: run_unity_runtime_media_validation.sh [options]

Runs the Unity editor-side runtime media validator in batchmode. If the
requested validation project is already open in another Unity instance, this
script clones it to a sibling temporary project so the runtime validator can
run without disturbing the active editor session.

Required environment:
  MEDIASFU_UNITY_API_USERNAME   MediaSFU cloud API username
  MEDIASFU_UNITY_API_KEY        MediaSFU cloud API key

Options:
  --unity-app PATH             Override Unity.app path.
  --project-path PATH          Override validation project path.
  --base-url URL               Override cloud base URL. Default: https://mediasfu.com
  --local-link URL             Override local link / room server URL.
    --timeout-seconds N          Override runtime timeout. Default: 300
  --log-file PATH              Unity -logFile target. Default: -
  --keep-project-copy          Keep the temporary project copy when one is used.
  --dry-run                    Print the resolved command without running it.
  --help                       Show this help text.
EOF
}

require_env() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        echo "error: missing required environment variable $name" >&2
        exit 1
    fi
}

project_is_open() {
    local requested_path="$1"

    ps -axo command= | grep 'Unity.app/Contents/MacOS/Unity' | grep -F -- "-projectPath $requested_path" | grep -v grep >/dev/null 2>&1
}

clone_project_if_needed() {
    local source_project="$1"
    local clone_parent
    local clone_path

    if ! project_is_open "$source_project"; then
        printf '%s\n' "$source_project"
        return 0
    fi

    clone_parent="$(dirname "$source_project")"
    clone_path="$(mktemp -d "$clone_parent/$(basename "$source_project")-batchruntime.XXXXXX")"
    rm -rf "$clone_path"
    rsync -a --delete --exclude Temp/UnityLockfile "$source_project/" "$clone_path/"
    printf '%s\n' "$clone_path"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --unity-app)
            shift
            [[ $# -gt 0 ]] || { echo "error: --unity-app requires a value" >&2; exit 1; }
            unity_app="$1"
            ;;
        --project-path)
            shift
            [[ $# -gt 0 ]] || { echo "error: --project-path requires a value" >&2; exit 1; }
            project_path="$1"
            ;;
        --base-url)
            shift
            [[ $# -gt 0 ]] || { echo "error: --base-url requires a value" >&2; exit 1; }
            base_url="$1"
            ;;
        --local-link)
            shift
            [[ $# -gt 0 ]] || { echo "error: --local-link requires a value" >&2; exit 1; }
            local_link="$1"
            ;;
        --timeout-seconds)
            shift
            [[ $# -gt 0 ]] || { echo "error: --timeout-seconds requires a value" >&2; exit 1; }
            timeout_seconds="$1"
            ;;
        --log-file)
            shift
            [[ $# -gt 0 ]] || { echo "error: --log-file requires a value" >&2; exit 1; }
            log_file="$1"
            ;;
        --keep-project-copy)
            keep_project_copy=true
            ;;
        --dry-run)
            dry_run=true
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            echo "error: unknown option '$1'" >&2
            usage >&2
            exit 1
            ;;
    esac
    shift
done

require_env "MEDIASFU_UNITY_API_USERNAME"
require_env "MEDIASFU_UNITY_API_KEY"

if [[ ! -d "$unity_app" ]]; then
    echo "error: Unity.app not found at $unity_app" >&2
    exit 1
fi

unity_binary="$unity_app/Contents/MacOS/Unity"
if [[ ! -x "$unity_binary" ]]; then
    echo "error: Unity binary is not executable at $unity_binary" >&2
    exit 1
fi

if [[ ! -d "$project_path" ]]; then
    echo "error: Unity validation project not found at $project_path" >&2
    exit 1
fi

if ! [[ "$timeout_seconds" =~ ^[0-9]+$ ]] || [[ "$timeout_seconds" -le 0 ]]; then
    echo "error: timeout seconds must be a positive integer" >&2
    exit 1
fi

resolved_project_path="$project_path"
temporary_project_path=""
if project_is_open "$project_path"; then
    resolved_project_path="$(clone_project_if_needed "$project_path")"
    temporary_project_path="$resolved_project_path"
fi

cleanup() {
    if [[ -n "${unity_pid:-}" ]] && kill -0 "$unity_pid" >/dev/null 2>&1; then
        kill "$unity_pid" >/dev/null 2>&1 || true
        wait "$unity_pid" >/dev/null 2>&1 || true
    fi

    if [[ -n "$temporary_project_path" && "$keep_project_copy" != true ]]; then
        rm -rf "$temporary_project_path"
    fi
}

stop_unity_process() {
    local pid="$1"
    local attempt
    local process_state

    disown "$pid" >/dev/null 2>&1 || true
    kill "$pid" >/dev/null 2>&1 || true

    for attempt in {1..15}; do
        process_state="$(ps -o stat= -p "$pid" 2>/dev/null | tr -d '[:space:]')"
        if [[ -z "$process_state" || "$process_state" == Z* ]]; then
            return 0
        fi
        sleep 1
    done

    kill -9 "$pid" >/dev/null 2>&1 || true
    return 0
}

wait_for_unity_process_exit() {
    local pid="$1"
    local attempt
    local process_state

    for attempt in {1..10}; do
        process_state="$(ps -o stat= -p "$pid" 2>/dev/null | tr -d '[:space:]')"
        if [[ -z "$process_state" || "$process_state" == Z* ]]; then
            wait "$pid" >/dev/null 2>&1 || true
            return 0
        fi
        sleep 1
    done

    return 1
}

run_unity_command() {
    local completion_marker="MediaSFU Unity runtime media validation passed."
    local failure_marker="MediaSFU Unity runtime media validation failed."
    local rc

    if [[ "$log_file" == "-" ]]; then
        "${command[@]}"
        return $?
    fi

    "${command[@]}" >/dev/null 2>&1 &
    unity_pid="$!"

    while kill -0 "$unity_pid" >/dev/null 2>&1; do
        if [[ -f "$log_file" ]]; then
            if grep -qF "$completion_marker" "$log_file"; then
                if wait_for_unity_process_exit "$unity_pid"; then
                    echo "note=validation pass marker observed; Unity batchmode exited"
                else
                    echo "note=validation pass marker observed; stopping lingering Unity batchmode process"
                    stop_unity_process "$unity_pid"
                fi
                unity_pid=""
                return 0
            fi

            if grep -qF "$failure_marker" "$log_file"; then
                if wait_for_unity_process_exit "$unity_pid"; then
                    echo "note=validation failure marker observed; Unity batchmode exited" >&2
                else
                    echo "note=validation failure marker observed; stopping lingering Unity batchmode process" >&2
                    stop_unity_process "$unity_pid"
                fi
                unity_pid=""
                return 1
            fi
        fi

        sleep 2
    done

    wait "$unity_pid"
    rc=$?
    unity_pid=""
    return "$rc"
}

trap cleanup EXIT

command=(
    "$unity_binary"
    -batchmode
    -nographics
    -quit
    -projectPath "$resolved_project_path"
    -executeMethod MediaSfuRuntimeMediaValidation.Run
    -logFile "$log_file"
)

echo "unity_app=$unity_app"
echo "project_path=$project_path"
echo "resolved_project_path=$resolved_project_path"
echo "base_url=$base_url"
echo "local_link=$local_link"
echo "timeout_seconds=$timeout_seconds"
echo "log_file=$log_file"
if [[ -n "$temporary_project_path" ]]; then
    echo "note=project was already open; using temporary clone"
fi

if [[ "$dry_run" == true ]]; then
    printf 'command='
    printf '%q ' "${command[@]}"
    printf '\n'
    exit 0
fi

export MEDIASFU_UNITY_BASE_URL="$base_url"
export MEDIASFU_UNITY_LOCAL_LINK="$local_link"
export MEDIASFU_UNITY_RUNTIME_TIMEOUT_SECONDS="$timeout_seconds"

run_unity_command