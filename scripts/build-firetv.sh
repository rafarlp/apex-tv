#!/bin/bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$project_root/.build/android-sdk}"
export ANDROID_USER_HOME="$project_root/.build/android-user"
export GRADLE_USER_HOME="$project_root/.build/gradle-user"
if [[ ! -f "$ANDROID_HOME/platforms/android-36/android.jar" ]]; then
    echo "Android SDK 36 is required. See firetv/README.md." >&2
    exit 1
fi
mkdir -p "$project_root/.build" "$project_root/build"
if [[ ! -f "$project_root/.build/apex-tv.keystore" ]]; then
    keytool -genkeypair -noprompt -keystore "$project_root/.build/apex-tv.keystore" \
        -storepass android -keypass android -alias apextv -keyalg RSA -keysize 3072 \
        -validity 10000 -dname 'CN=Apex TV Local Development'
fi
cd "$project_root/firetv"
./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
cp app/build/outputs/apk/release/app-release.apk "$project_root/build/ApexTV-firetv.apk"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose "$project_root/build/ApexTV-firetv.apk"
shasum -a 256 "$project_root/build/ApexTV-firetv.apk"
echo "Built $project_root/build/ApexTV-firetv.apk"
