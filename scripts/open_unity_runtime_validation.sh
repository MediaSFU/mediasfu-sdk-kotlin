#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"

default_unity_app="/Applications/Unity/Hub/Editor/2022.3.62f3/Unity.app"
default_project_path="$repo_dir/unity-validation/MediaSfuUnityValidation"

unity_app="${UNITY_APP_PATH:-$default_unity_app}"
project_path="${MEDIASFU_UNITY_PROJECT_PATH:-$default_project_path}"
scene_path="${MEDIASFU_UNITY_RUNTIME_SCENE_PATH:-}"
dry_run=false

usage() {
    cat <<'EOF'
Usage: open_unity_runtime_validation.sh [options]

Launches the checked-in Unity runtime validation project directly in the Unity
editor and opens the runtime validation scene automatically.

Options:
  --unity-app PATH             Override Unity.app path.
  --project-path PATH          Override validation project path.
  --scene-path PATH            Override runtime validation scene path.
  --dry-run                    Print the resolved launch command without opening Unity.
  --help                       Show this help text.
EOF
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
        --scene-path)
            shift
            [[ $# -gt 0 ]] || { echo "error: --scene-path requires a value" >&2; exit 1; }
            scene_path="$1"
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

if [[ -z "$scene_path" ]]; then
    scene_path="$project_path/Assets/Scenes/MediaSfuRuntimeValidation.unity"
fi

if [[ ! -d "$unity_app" ]]; then
    echo "error: Unity.app not found at $unity_app" >&2
    exit 1
fi

if [[ ! -d "$project_path" ]]; then
    echo "error: Unity validation project not found at $project_path" >&2
    exit 1
fi

if [[ ! -f "$scene_path" ]]; then
    echo "error: runtime validation scene not found at $scene_path" >&2
    echo "hint: regenerate it with MediaSfuRuntimeValidationSceneBuilder.CreateScene if needed" >&2
    exit 1
fi

command=(
    open
    -n
    -a "$unity_app"
    --args
    -projectPath "$project_path"
    -openfile "$scene_path"
)

echo "unity_app=$unity_app"
echo "project_path=$project_path"
echo "scene_path=$scene_path"

if [[ "$dry_run" == true ]]; then
    printf 'command='
    printf '%q ' "${command[@]}"
    printf '\n'
    exit 0
fi

exec "${command[@]}"