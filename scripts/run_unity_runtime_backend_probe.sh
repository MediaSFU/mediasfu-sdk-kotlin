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
log_file="${MEDIASFU_UNITY_LOG_FILE:--}"
keep_project_copy=false
dry_run=false

usage() {
    cat <<'EOF'
Usage: run_unity_runtime_backend_probe.sh [options]

Runs the Unity editor-side runtime backend probe in batchmode without requiring
cloud credentials. If the requested validation project is already open in
another Unity instance, this script clones it to a sibling temporary project so
the probe can still run without disturbing the active editor session.

Options:
  --unity-app PATH             Override Unity.app path.
  --project-path PATH          Override validation project path.
  --log-file PATH              Unity -logFile target. Default: -
  --keep-project-copy          Keep the temporary project copy when one is used.
  --dry-run                    Print the resolved command without running it.
  --help                       Show this help text.
EOF
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
    clone_path="$(mktemp -d "$clone_parent/$(basename "$source_project")-batchprobe.XXXXXX")"
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

resolved_project_path="$project_path"
temporary_project_path=""
if project_is_open "$project_path"; then
    resolved_project_path="$(clone_project_if_needed "$project_path")"
    temporary_project_path="$resolved_project_path"
fi

cleanup() {
    if [[ -n "$temporary_project_path" && "$keep_project_copy" != true ]]; then
        rm -rf "$temporary_project_path"
    fi
}

trap cleanup EXIT

command=(
    "$unity_binary"
    -batchmode
    -nographics
    -quit
    -projectPath "$resolved_project_path"
    -executeMethod MediaSfuRuntimeMediaValidation.RunBackendProbe
    -logFile "$log_file"
)

echo "unity_app=$unity_app"
echo "project_path=$project_path"
echo "resolved_project_path=$resolved_project_path"
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

exec "${command[@]}"