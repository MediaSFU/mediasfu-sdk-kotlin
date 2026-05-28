#!/usr/bin/env bash
set -euo pipefail

DEVELOPMENT_TEAM="${MEDIA_SFU_IOS_DEVELOPMENT_TEAM:-}"  # Required: export MEDIA_SFU_IOS_DEVELOPMENT_TEAM=<your Apple Developer Team ID>
KEYCHAIN_PATH="${MEDIA_SFU_LOGIN_KEYCHAIN_PATH:-}"
APPLY_KEYCHAIN=0
CONFIGURE_ACCESS=0
QUIET=0

usage() {
  cat <<EOF
Usage: ./scripts/ios_codesign_doctor.sh [options]

Options:
  --keychain PATH       Keychain to inspect or repair.
  --apply-keychain      Add the keychain to the user search list and set it as default.
  --configure-access    Unlock the keychain and update the partition list for codesign.
  --quiet               Print only failures and remediation.
  --help                Show this message.

This script checks the macOS codesigning prerequisites used by the iOS sample app:
default keychain, active Apple Development identities, and local provisioning profiles.
EOF
}

normalize_security_path() {
  sed -E 's/^[[:space:]]*//; s/^"//; s/"$//'
}

default_keychain() {
  security default-keychain -d user 2>/dev/null | normalize_security_path || true
}

list_user_keychains() {
  security list-keychains -d user 2>/dev/null | normalize_security_path | sed '/^$/d' || true
}

candidate_keychains() {
  if [[ -n "$KEYCHAIN_PATH" ]]; then
    printf '%s\n' "$KEYCHAIN_PATH"
  fi

  if [[ -f "$HOME/Library/Keychains/login.keychain-db" ]]; then
    printf '%s\n' "$HOME/Library/Keychains/login.keychain-db"
  fi

  find "$HOME/Library/Keychains" -maxdepth 1 -type f \( -name 'login*.keychain' -o -name 'login*.keychain-db' \) -print 2>/dev/null | sort
}

resolve_keychain_path() {
  local candidate
  while IFS= read -r candidate; do
    [[ -n "$candidate" ]] || continue
    if [[ -f "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done < <(candidate_keychains)

  return 1
}

count_valid_identities() {
  local output target="${1:-}"
  if [[ -n "$target" ]]; then
    output="$(security find-identity -v -p codesigning "$target" 2>&1 || true)"
  else
    output="$(security find-identity -v -p codesigning 2>&1 || true)"
  fi

  printf '%s\n' "$output" | awk '/valid identities found/ { print $1; found = 1 } END { if (!found) print 0 }'
}

count_profiles_in_dir() {
  local dir="$1"
  if [[ ! -d "$dir" ]]; then
    printf '0\n'
    return 0
  fi

  find "$dir" -maxdepth 1 -type f -name '*.mobileprovision' -print 2>/dev/null | wc -l | tr -d ' '
}

print_summary() {
  local current_default="$1"
  local active_identities="$2"
  local xcode_profiles="$3"
  local legacy_profiles="$4"
  local chosen_keychain="$5"

  if [[ "$QUIET" == "1" ]]; then
    return
  fi

  echo "Default keychain: ${current_default:-<missing>}"
  echo "User keychain search list:"

  local had_keychain=0 line
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    had_keychain=1
    echo "  - $line"
  done < <(list_user_keychains)

  if [[ "$had_keychain" == "0" ]]; then
    echo "  - <none>"
  fi

  echo "Active code signing identities: $active_identities"
  echo "Provisioning profiles: Xcode user data=$xcode_profiles, legacy MobileDevice=$legacy_profiles"

  if [[ -n "$chosen_keychain" ]]; then
    echo "Preferred repair keychain: $chosen_keychain"
  fi
}

dedupe_keychain_list() {
  local item existing duplicate
  local -a output=()
  for item in "$@"; do
    [[ -n "$item" ]] || continue
    duplicate=0
    for existing in "${output[@]-}"; do
      if [[ "$existing" == "$item" ]]; then
        duplicate=1
        break
      fi
    done
    if [[ "$duplicate" == "0" ]]; then
      output+=("$item")
    fi
  done

  if [[ "${#output[@]}" == "0" ]]; then
    return 0
  fi

  printf '%s\n' "${output[@]}"
}

apply_keychain_settings() {
  local chosen_keychain="$1"
  local -a combined=("$chosen_keychain")
  local -a deduped=()

  if [[ ! -f "$chosen_keychain" ]]; then
    echo "Keychain not found: $chosen_keychain" >&2
    exit 2
  fi

  local line
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    combined+=("$line")
  done < <(list_user_keychains)

  combined+=("/Library/Keychains/System.keychain")

  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    deduped+=("$line")
  done < <(dedupe_keychain_list "${combined[@]}")

  if [[ "${#deduped[@]}" == "0" ]]; then
    echo "No keychains were available to apply to the user search list." >&2
    exit 2
  fi

  security list-keychains -d user -s "${deduped[@]}"
  security default-keychain -d user -s "$chosen_keychain"
}

configure_keychain_access() {
  local chosen_keychain="$1"
  local keychain_password

  if [[ ! -t 0 || ! -t 1 ]]; then
    echo "--configure-access requires an interactive terminal." >&2
    exit 2
  fi

  if [[ ! -f "$chosen_keychain" ]]; then
    echo "Keychain not found: $chosen_keychain" >&2
    exit 2
  fi

  read -r -s -p "Keychain password for $chosen_keychain: " keychain_password < /dev/tty
  echo > /dev/tty

  security unlock-keychain -p "$keychain_password" "$chosen_keychain"
  security set-keychain-settings -lut 7200 "$chosen_keychain"
  security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$keychain_password" "$chosen_keychain"

  unset keychain_password
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --keychain)
      KEYCHAIN_PATH="$2"
      shift 2
      ;;
    --apply-keychain)
      APPLY_KEYCHAIN=1
      shift
      ;;
    --configure-access)
      CONFIGURE_ACCESS=1
      shift
      ;;
    --quiet)
      QUIET=1
      shift
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

CHOSEN_KEYCHAIN="$(resolve_keychain_path || true)"

if [[ "$APPLY_KEYCHAIN" == "1" ]]; then
  if [[ -z "$CHOSEN_KEYCHAIN" ]]; then
    echo "No usable keychain file was found to apply." >&2
    exit 2
  fi
  apply_keychain_settings "$CHOSEN_KEYCHAIN"
fi

if [[ "$CONFIGURE_ACCESS" == "1" ]]; then
  if [[ -z "$CHOSEN_KEYCHAIN" ]]; then
    echo "No usable keychain file was found to configure." >&2
    exit 2
  fi
  configure_keychain_access "$CHOSEN_KEYCHAIN"
fi

CURRENT_DEFAULT="$(default_keychain)"
ACTIVE_IDENTITIES="$(count_valid_identities)"
XCODE_PROFILE_COUNT="$(count_profiles_in_dir "$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles")"
LEGACY_PROFILE_COUNT="$(count_profiles_in_dir "$HOME/Library/MobileDevice/Provisioning Profiles")"

print_summary "$CURRENT_DEFAULT" "$ACTIVE_IDENTITIES" "$XCODE_PROFILE_COUNT" "$LEGACY_PROFILE_COUNT" "$CHOSEN_KEYCHAIN"

failure=0

if [[ -z "$CURRENT_DEFAULT" ]]; then
  echo "No default user keychain is configured for codesigning." >&2
  failure=1
fi

if [[ "$ACTIVE_IDENTITIES" == "0" ]]; then
  echo "No valid Apple Development codesigning identity is visible in the active keychains." >&2
  failure=1
fi

if [[ "$XCODE_PROFILE_COUNT" == "0" && "$LEGACY_PROFILE_COUNT" == "0" ]]; then
  echo "No local provisioning profiles were found under Xcode user data or MobileDevice." >&2
  failure=1
fi

if [[ "$failure" == "0" ]]; then
  if [[ "$QUIET" != "1" ]]; then
    echo "iOS codesigning prerequisites look healthy."
  fi
  exit 0
fi

echo >&2
echo "Recommended remediation:" >&2

if [[ -n "$CHOSEN_KEYCHAIN" ]]; then
  echo "  1. Restore the user keychain into the search list and set it as default:" >&2
  echo "     ./scripts/ios_codesign_doctor.sh --apply-keychain --keychain \"$CHOSEN_KEYCHAIN\"" >&2
  echo "  2. If the certificate exists in that keychain, unlock it and grant codesign access:" >&2
  echo "     ./scripts/ios_codesign_doctor.sh --configure-access --keychain \"$CHOSEN_KEYCHAIN\"" >&2
fi

echo "  3. If identities are still missing, import the Apple Development certificate/private key (.p12) for team $DEVELOPMENT_TEAM into the chosen login keychain." >&2
echo "  4. Open Xcode > Settings > Accounts and refresh the team so Xcode can recreate/download provisioning assets if needed." >&2

exit 1