#!/bin/bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_USER_HOME="$project_root/.build/android-user"
adb="${ANDROID_HOME:-$project_root/.build/android-sdk}/platform-tools/adb"
apk="$project_root/build/ApexTV-firetv.apk"
device_address="${1:-}"
if [[ -z "$device_address" ]]; then
    echo 'Usage: bash scripts/install-firetv.sh FIRE_STICK_IP[:5555]' >&2
    exit 1
fi
if [[ ! "$device_address" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}(:[0-9]{1,5})?$ ]]; then
    echo 'Provide the Fire Stick local IPv4 address shown under About → Network.' >&2
    exit 1
fi
[[ "$device_address" == *:* ]] || device_address="$device_address:5555"
if [[ ! -x "$adb" || ! -f "$apk" ]]; then
    echo 'Build the app first with bash scripts/build-firetv.sh.' >&2
    exit 1
fi
mkdir -p "$ANDROID_USER_HOME"
"$adb" connect "$device_address"
if [[ "$("$adb" -s "$device_address" get-state 2>/dev/null)" != device ]]; then
    echo 'Enable ADB debugging and approve this computer on the TV, then run this command again.' >&2
    exit 1
fi
echo 'Target model:'
"$adb" -s "$device_address" shell getprop ro.product.model
echo 'App ABI:'
"$adb" -s "$device_address" shell getprop ro.product.cpu.abi
# This package is separate from Vroom, Race Control, and the official F1 TV app.
# Do not uninstall or clear any existing applications or their account data.
"$adb" -s "$device_address" install -r "$apk"
"$adb" -s "$device_address" shell pm path dev.apextv.firetv
"$adb" -s "$device_address" shell am start -n dev.apextv.firetv/.MainActivity
