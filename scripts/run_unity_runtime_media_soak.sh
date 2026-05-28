#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
runtime_runner="$script_dir/run_unity_runtime_media_validation.sh"

runs=3
logs_dir=""
unity_app=""
project_path=""
base_url=""
local_link=""
timeout_seconds="${MEDIASFU_UNITY_RUNTIME_TIMEOUT_SECONDS:-300}"
fail_fast=false
dry_run=false

usage() {
    cat <<'EOF'
Usage: run_unity_runtime_media_soak.sh [options]

Runs the checked-in Unity runtime media validator multiple times and prints a
compact summary for each pass. Per-run Unity and wrapper logs are preserved in a
single logs directory for later inspection.

Required environment:
  MEDIASFU_UNITY_API_USERNAME   MediaSFU cloud API username
  MEDIASFU_UNITY_API_KEY        MediaSFU cloud API key

Options:
  --runs N                     Number of validation runs. Default: 3
  --logs-dir PATH              Directory to store per-run logs. Default: mktemp
  --unity-app PATH             Override Unity.app path.
  --project-path PATH          Override validation project path.
  --base-url URL               Override cloud base URL.
  --local-link URL             Override local link / room server URL.
    --timeout-seconds N          Override runtime timeout passed to each run. Default: MEDIASFU_UNITY_RUNTIME_TIMEOUT_SECONDS or 300
  --fail-fast                  Stop after the first failed run.
  --dry-run                    Print the resolved per-run command without running it.
  --help                       Show this help text.
EOF
}

runtime_runner_args=()

build_runtime_runner_args() {
    local log_file="$1"

    runtime_runner_args=()

    if [[ -n "$unity_app" ]]; then
        runtime_runner_args+=(--unity-app "$unity_app")
    fi

    if [[ -n "$project_path" ]]; then
        runtime_runner_args+=(--project-path "$project_path")
    fi

    if [[ -n "$base_url" ]]; then
        runtime_runner_args+=(--base-url "$base_url")
    fi

    if [[ -n "$local_link" ]]; then
        runtime_runner_args+=(--local-link "$local_link")
    fi

    if [[ -n "$timeout_seconds" ]]; then
        runtime_runner_args+=(--timeout-seconds "$timeout_seconds")
    fi

    runtime_runner_args+=(--log-file "$log_file")
}

extract_room_name() {
    local log_file="$1"
    if [[ ! -f "$log_file" ]]; then
        return 0
    fi

    grep -m1 'MediaSFU Unity runtime media validation passed with room ' "$log_file" | sed -E 's/.*room ([^,]+).*/\1/' || true
}

extract_consume_target() {
    local log_file="$1"
    if [[ ! -f "$log_file" ]]; then
        return 0
    fi

    grep -m1 'MediaSfu receive consume target candidates=' "$log_file" | sed -E 's/.*candidates=([^ ]+).*/\1/' || true
}

extract_failure_reason() {
    local log_file="$1"
    local wrapper_log_file="$2"

    if [[ -f "$log_file" ]]; then
        grep -E 'validation failed|Timed out waiting|joinConRoom failed|joinConsumeRoom failed|Exception|error=[^[:space:]]' "$log_file" | tail -n 1 | tr -s ' ' || true
    fi

    if [[ -f "$wrapper_log_file" ]]; then
        grep -E 'error:|validation failed|Timed out waiting|joinConRoom failed|joinConsumeRoom failed|Exception|error=[^[:space:]]' "$wrapper_log_file" | tail -n 1 | tr -s ' ' || true
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --runs)
            shift
            [[ $# -gt 0 ]] || { echo "error: --runs requires a value" >&2; exit 1; }
            runs="$1"
            ;;
        --logs-dir)
            shift
            [[ $# -gt 0 ]] || { echo "error: --logs-dir requires a value" >&2; exit 1; }
            logs_dir="$1"
            ;;
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
        --fail-fast)
            fail_fast=true
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

if ! [[ "$runs" =~ ^[0-9]+$ ]] || [[ "$runs" -le 0 ]]; then
    echo "error: runs must be a positive integer" >&2
    exit 1
fi

if [[ -n "$timeout_seconds" ]] && ( ! [[ "$timeout_seconds" =~ ^[0-9]+$ ]] || [[ "$timeout_seconds" -le 0 ]] ); then
    echo "error: timeout seconds must be a positive integer" >&2
    exit 1
fi

if [[ ! -f "$runtime_runner" ]]; then
    echo "error: runtime media runner not found at $runtime_runner" >&2
    exit 1
fi

if [[ -z "$logs_dir" ]]; then
    logs_dir="$(mktemp -d /tmp/mediasfu-unity-runtime-soak.XXXXXX)"
else
    mkdir -p "$logs_dir"
fi

echo "runs=$runs"
echo "logs_dir=$logs_dir"
echo "fail_fast=$fail_fast"

pass_count=0
fail_count=0

for (( run_index = 1; run_index <= runs; run_index++ )); do
    log_file="$logs_dir/run-${run_index}.log"
    wrapper_log_file="$logs_dir/run-${run_index}.wrapper.log"

    echo "RUN $run_index log=$log_file wrapper_log=$wrapper_log_file"

    build_runtime_runner_args "$log_file"
    command=(bash "$runtime_runner" "${runtime_runner_args[@]}")

    if [[ "$dry_run" == true ]]; then
        printf 'RUN %s dry_run command=' "$run_index"
        printf '%q ' "${command[@]}"
        printf '\n'
        continue
    fi

    if "${command[@]}" >"$wrapper_log_file" 2>&1; then
        wrapper_result=pass
    else
        wrapper_result=fail
    fi

    room_name="$(extract_room_name "$log_file")"
    consume_target="$(extract_consume_target "$log_file")"
    remote_frame_copied=no
    final_marker=no

    if [[ -f "$log_file" ]] && grep -q 'Runtime media validation copied remote video frame' "$log_file"; then
        remote_frame_copied=yes
    fi

    if [[ -f "$log_file" ]] && grep -q 'MediaSFU Unity runtime media validation passed\.' "$log_file"; then
        final_marker=yes
    fi

    run_result=pass
    if [[ "$remote_frame_copied" != yes || "$final_marker" != yes ]]; then
        run_result=fail
    fi

    if [[ "$run_result" == pass ]]; then
        pass_count=$((pass_count + 1))
        failure_reason="<none>"
    else
        fail_count=$((fail_count + 1))
        failure_reason="$(extract_failure_reason "$log_file" "$wrapper_log_file")"
        if [[ -z "$failure_reason" ]]; then
            failure_reason="<none>"
        fi
    fi

    echo "RUN $run_index result=$run_result wrapper=$wrapper_result room=${room_name:-<none>} consume=${consume_target:-<none>} frame=$remote_frame_copied final=$final_marker reason=$failure_reason"

    if [[ "$run_result" == fail && "$fail_fast" == true ]]; then
        break
    fi
done

echo "SUMMARY pass=$pass_count fail=$fail_count logs_dir=$logs_dir"

if [[ "$dry_run" == true ]]; then
    exit 0
fi

if [[ "$fail_count" -gt 0 ]]; then
    exit 1
fi