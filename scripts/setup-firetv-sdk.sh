#!/bin/bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_root"
export ANDROID_USER_HOME="$project_root/.build/android-user"
sdk_path="$project_root/.build/android-sdk"
archive_path="$project_root/.build/android-downloads/cmdline-tools.zip"
mkdir -p .build/android-downloads "$sdk_path/cmdline-tools"
if [[ ! -x "$sdk_path/cmdline-tools/latest/bin/sdkmanager" ]]; then
    curl -fL 'https://dl.google.com/android/repository/commandlinetools-mac_arm64-15859902_latest.zip' -o "$archive_path"
    printf '%s  %s\n' '835b62a26162b229b441d1f6d4680383815a270809eb33522c0d480fa5002c4e' "$archive_path" | shasum -a 256 -c -
    unzip -q "$archive_path" -d "$sdk_path/cmdline-tools"
    mv "$sdk_path/cmdline-tools/cmdline-tools" "$sdk_path/cmdline-tools/latest"
fi
"$sdk_path/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$sdk_path" --licenses
"$sdk_path/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$sdk_path" \
    'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
