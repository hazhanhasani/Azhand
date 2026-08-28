#!/usr/bin/env bash
set -euo pipefail

echo "== Azhand Android build =="

if [ ! -f "settings.gradle.kts" ] || [ ! -d "app" ]; then
  echo "Android project not found."
  exit 1
fi

if command -v sdkmanager >/dev/null 2>&1; then
  yes | sdkmanager \
    "platform-tools" \
    "platforms;android-35" \
    "build-tools;35.0.0" >/dev/null || true
fi

gradle :app:assembleDebug --stacktrace

echo "Debug APK:"
find app/build/outputs/apk -type f -name "*.apk" -print
