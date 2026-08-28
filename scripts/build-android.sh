#!/usr/bin/env bash
set -euo pipefail

echo "== Azhand Android build =="

echo "Validating embedded Manager/PWA JavaScript..."
python3 scripts/validate-web-assets.py
echo "Validating release-bot D1 schema..."
python3 scripts/validate-release-schema.py
echo "Validating Blupal integration..."
python3 scripts/validate-blupal.py

if [ ! -f "settings.gradle.kts" ] || [ ! -d "app" ] || [ ! -d "adminapp" ]; then
  echo "Android projects not found."
  exit 1
fi

if command -v sdkmanager >/dev/null 2>&1; then
  yes | sdkmanager \
    "platform-tools" \
    "platforms;android-35" \
    "build-tools;35.0.0" >/dev/null || true
fi

echo "Building resident + admin debug APKs..."
gradle :app:assembleDebug :adminapp:assembleDebug --stacktrace

if [ -n "${ANDROID_KEYSTORE_PATH:-}" ] &&
   [ -n "${ANDROID_KEYSTORE_PASSWORD:-}" ] &&
   [ -n "${ANDROID_KEY_ALIAS:-}" ] &&
   [ -n "${ANDROID_KEY_PASSWORD:-}" ] &&
   [ -f "${ANDROID_KEYSTORE_PATH}" ]; then
  echo "Stable signing key found; building signed resident + admin release APKs..."
  gradle :app:assembleRelease :adminapp:assembleRelease --stacktrace
else
  echo "Stable signing secrets are not configured; signed release skipped."
fi

echo "Built APK files:"
find app/build/outputs/apk adminapp/build/outputs/apk \
  -type f -name "*.apk" -print
