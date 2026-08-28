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

echo "Building debug APK..."
gradle :app:assembleDebug --stacktrace

if [ -n "${ANDROID_KEYSTORE_PATH:-}" ] &&
   [ -n "${ANDROID_KEYSTORE_PASSWORD:-}" ] &&
   [ -n "${ANDROID_KEY_ALIAS:-}" ] &&
   [ -n "${ANDROID_KEY_PASSWORD:-}" ] &&
   [ -f "app/${ANDROID_KEYSTORE_PATH}" ]; then
  echo "Stable signing key found; building signed release APK..."
  gradle :app:assembleRelease --stacktrace
else
  echo "Stable signing secrets are not configured; signed release skipped."
fi

echo "Built APK files:"
find app/build/outputs/apk -type f -name "*.apk" -print
