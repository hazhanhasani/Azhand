#!/usr/bin/env bash
set -euo pipefail

echo "== Azhand Android build =="

echo "Validating embedded Manager/PWA JavaScript..."
python3 scripts/validate-web-assets.py
echo "Validating release-bot D1 schema..."
python3 scripts/validate-release-schema.py
echo "Validating Blupal integration..."
python3 scripts/validate-blupal.py
echo "Validating Kotlin structure..."
python3 scripts/validate-kotlin-structure.py
echo "Validating resident UI regression baseline..."
python3 scripts/validate-resident-ui.py
echo "Validating Admin app design/icon..."
python3 scripts/validate-admin-ui.py
echo "Validating Admin operational parity..."
python3 scripts/validate-admin-operations.py
echo "Validating Admin dashboard data..."
python3 scripts/validate-admin-data.py
echo "Validating Jalali/Iran time..."
python3 scripts/validate-jalali-time.py
echo "Validating finance automation..."
python3 scripts/validate-finance-automation.py
echo "Validating deploy resilience..."
python3 scripts/validate-deploy-resilience.py
echo "Validating resident graphical UI..."
python3 scripts/validate-resident-graphics.py
echo "Validating Blupal callback flow..."
python3 scripts/validate-blupal-callback.py
echo "Validating D1 migration compatibility..."
python3 scripts/validate-migrations.py

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

normalized_keystore="${ANDROID_KEYSTORE_PATH:-}"

if [ -n "$normalized_keystore" ] &&
   [ ! -f "$normalized_keystore" ] &&
   [ -f "app/$normalized_keystore" ]; then
  normalized_keystore="app/$normalized_keystore"
fi

if [ -n "$normalized_keystore" ] &&
   [ -n "${ANDROID_KEYSTORE_PASSWORD:-}" ] &&
   [ -n "${ANDROID_KEY_ALIAS:-}" ] &&
   [ -n "${ANDROID_KEY_PASSWORD:-}" ] &&
   [ -f "$normalized_keystore" ]; then
  export ANDROID_KEYSTORE_PATH="$normalized_keystore"
  echo "Stable signing key found at $ANDROID_KEYSTORE_PATH; building signed resident + admin release APKs..."
  gradle :app:assembleRelease :adminapp:assembleRelease --stacktrace
else
  echo "Stable signing key could not be resolved; signed release skipped."
  echo "Requested path: ${ANDROID_KEYSTORE_PATH:-<empty>}"
  ls -l app/azhand-release.jks 2>/dev/null || true
fi

echo "Built APK files:"
find app/build/outputs/apk adminapp/build/outputs/apk \
  -type f -name "*.apk" -print
