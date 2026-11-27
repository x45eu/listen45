#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 3 && $# != 4 ]]; then
    printf 'Usage: %s <keystore> <key alias> <output.apk> [unsigned.apk]\n' "$0" >&2
    exit 2
fi

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
: "${sdk:?Set ANDROID_HOME to the Android SDK}"
apksigner="$sdk/build-tools/35.0.0/apksigner"
unsigned="${4:-$root/app/build/outputs/apk/release/app-release-unsigned.apk}"
output="$3"

if [[ ! -r "$unsigned" || ! -r "$1" || ! -x "$apksigner" || -e "$output" ]]; then
    printf 'Check the unsigned APK, keystore, SDK Build-Tools 35.0.0 and output path. Choose a new output path.\n' >&2
    exit 1
fi
# apksigner prompts for passwords directly. No secrets are passed on the command line.
"$apksigner" sign --ks "$1" --ks-key-alias "$2" --v4-signing-enabled false --out "$output" "$unsigned"
"$apksigner" verify --verbose --print-certs "$output"
sha256sum "$output"
