#!/usr/bin/env bash

set -euo pipefail

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
timeout_seconds="${MEDIASFU_UNITY_LIVE_TIMEOUT_SECONDS:-45}"
log_file="${MEDIASFU_UNITY_LOG_FILE:--}"
dry_run=false

usage() {
    cat <<'EOF'
Usage: run_unity_live_validation.sh [options]

Runs the checked-in Unity batchmode live validation project against a MediaSFU
cloud environment.

Required environment:
  MEDIASFU_UNITY_API_USERNAME   MediaSFU cloud API username
  MEDIASFU_UNITY_API_KEY        MediaSFU cloud API key

Options:
  --unity-app PATH             Override Unity.app path.
  --project-path PATH          Override validation project path.
  --base-url URL               Override cloud base URL. Default: https://mediasfu.com
  --timeout-seconds N          Override live validation timeout. Default: 45
  --log-file PATH              Unity -logFile target. Default: -
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

command=(
    "$unity_binary"
    -batchmode
    -nographics
    -quit
    -projectPath "$project_path"
    -executeMethod MediaSfuPackageValidation.Run
    -logFile "$log_file"
)

echo "unity_app=$unity_app"
echo "project_path=$project_path"
echo "base_url=$base_url"
echo "timeout_seconds=$timeout_seconds"
echo "log_file=$log_file"

if [[ "$dry_run" == true ]]; then
    printf 'command='
    printf '%q ' "${command[@]}"
    printf '\n'
    exit 0
fi

export MEDIASFU_UNITY_LIVE_VALIDATION=1
export MEDIASFU_UNITY_BASE_URL="$base_url"
export MEDIASFU_UNITY_LIVE_TIMEOUT_SECONDS="$timeout_seconds"

exec "${command[@]}"