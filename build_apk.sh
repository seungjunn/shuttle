#!/usr/bin/env bash
# APK 빌드: web/ 를 앱 내장 자산으로 복사한 뒤 서명된 release APK 를 assets/shuttle.apk 로 만든다.
# 요구: JDK 17, Android SDK platform 34 + build-tools 34.0.0, Gradle 8.7
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
GRADLE="${GRADLE:-$HOME/gradle/gradle-8.7/bin/gradle}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"

rm -rf "$HERE/android/app/src/main/assets/web"
mkdir -p "$HERE/android/app/src/main/assets/web"
cp "$HERE"/web/* "$HERE/android/app/src/main/assets/web/"
[ -f "$HERE/android/local.properties" ] || echo "sdk.dir=$ANDROID_HOME" > "$HERE/android/local.properties"

cd "$HERE/android"
"$GRADLE" --no-daemon -q assembleRelease
mkdir -p "$HERE/assets"
cp app/build/outputs/apk/release/app-release.apk "$HERE/assets/shuttle.apk"
echo "APK: $HERE/assets/shuttle.apk ($(du -h "$HERE/assets/shuttle.apk" | cut -f1))"
