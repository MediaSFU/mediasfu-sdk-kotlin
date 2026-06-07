#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KOTLIN_REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
WORKSPACE_ROOT="$(cd "$KOTLIN_REPO/.." && pwd)"
APPLE_REPO="${MEDIA_SFU_APPLE_SDK_REPO:-$WORKSPACE_ROOT/mediasfu-apple-sdk}"
APPLE_SCRIPT="$APPLE_REPO/scripts/prepare_kmp_binary_dist.sh"

if [ ! -d "$APPLE_REPO" ]; then
  echo "Apple repo not found: $APPLE_REPO" >&2
  exit 1
fi

if [ ! -x "$APPLE_SCRIPT" ]; then
  echo "Apple sync script not executable: $APPLE_SCRIPT" >&2
  echo "Run: chmod +x \"$APPLE_SCRIPT\"" >&2
  exit 1
fi

echo "Syncing Apple-ready KMP artifact into $APPLE_REPO"
MEDIA_SFU_KOTLIN_REPO="$KOTLIN_REPO" "$APPLE_SCRIPT"
