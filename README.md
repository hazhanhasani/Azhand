# Azhand v0.4.5

پایه‌ی یکپارچه اپلیکیشن مدیریت مجتمع آژند.

## Android

- Kotlin
- Jetpack Compose
- Package: `com.azhand.app`
- Version: `0.4.5`
- Debug APK از GitHub Actions ساخته می‌شود.
- Release فقط وقتی Keystore ثابت در GitHub Secrets ثبت شده باشد ساخته می‌شود.

## PWA

بعد از Deploy Worker:

`https://YOUR-WORKER.workers.dev/app`

Manifest:

`/manifest.webmanifest`

Service Worker:

`/sw.js`

## App API

Health:

`GET /api/app/health`

Demo dashboard:

`GET /api/app/dashboard-demo`

## D1

Schema اپ هنگام اولین درخواست API به صورت idempotent ایجاد می‌شود.
فایل مرجع SQL نیز در:

`migrations/0002_app_core.sql`

قرار دارد.

## Cloudflare Bindings

- `DB` -> D1
- `RELEASE_FILES` -> KV

## Release Bot

نسخه Release Bot داخل همین `worker.js` حفظ شده؛ بنابراین وقتی ZIP پروژه را با ربات Sync می‌کنی، خود ربات حذف نمی‌شود.

## اولین تست

این ZIP را با Caption زیر برای ربات بفرست:

`v0.4.5`

بعد از موفقیت:

1. GitHub main باید فایل‌های پروژه را داشته باشد.
2. GitHub Actions باید Android debug APK را Build کند.
3. Worker باید به 0.4.5 آپدیت شود.
4. مسیر `/app` باید PWA آژند را نشان دهد.
5. مسیر `/api/app/health` باید `ok: true` برگرداند.


## Fix v0.4.5 — GitHub workflow permission

v0.4.0 reached the local commit stage, but GitHub rejected the push because
the release tried to create `.github/workflows/android-build.yml`.

v0.4.5 fixes that by:

- excluding `.github/workflows/**` from ZIP synchronization;
- removing the separate `android-build.yml` from the release package;
- letting the Release Bot Setup manage workflow files with the user's
  fine-grained PAT (`Contents: Read/Write` + `Workflows: Read/Write`);
- building the Android debug APK inside `azhand-release.yml`;
- uploading the APK as a GitHub Actions artifact.

After v0.4.5 deploys, open `/setup` and save once to update the GitHub release
workflow. Then send v0.4.5 again to test the integrated Android build.


## v0.4.5 — Self-healing release/build pipeline

From v0.4.5 onward, before every normal ZIP release the Telegram Worker uses
its saved GitHub fine-grained PAT to create/update the single stable release
workflow. Normal ZIPs never modify `.github/workflows/*`.

The stable workflow calls `scripts/build-android.sh`, so future Android build
changes can be shipped inside the ZIP without changing GitHub workflow files.

### One-time bridge from the currently deployed v0.4.1 Worker

The currently active v0.4.1 code cannot use features that only exist in v0.4.5
before v0.4.5 has been deployed.

1. Send `Azhand-v0.4.5.zip` once.
2. After the bot reports that the Worker updated, send `/build`.

`/build` reuses the same v0.4.5 ZIP already stored in KV, updates the GitHub
workflow automatically, and starts Android build. After that, every later ZIP
builds Android automatically on its first upload, with no `/setup` step.


## v0.4.5 — D1-independent build recovery

Fixes:

`D1_ERROR: no such table: release_uploads`

Changes:

- The latest release metadata is now stored in KV under `release-meta:latest`.
- `/build` uses KV as its primary source and no longer depends on D1.
- `release_uploads` is created automatically and idempotently when D1 exists.
- D1 is audit/history only; D1 errors no longer block normal release/build flow.
- `/status` and `/version` also work when D1 is missing or not initialized.
- Existing installations can fall back to D1 once, then migrate latest metadata
  into KV automatically.

For the currently deployed v0.4.2 bridge:

1. Send `Azhand-v0.4.5.zip`.
2. Wait for Worker auto-deploy.
3. Send `/build`.

After v0.4.5 is active, normal future ZIP uploads auto-build on the first try.


## v0.4.5 — lossless workflow embedding

Fixes the GitHub Actions failure:

`SyntaxError: unterminated string literal`

Root cause:
the Cloudflare Worker stored the GitHub workflow inside a JavaScript template
literal. Bash and Python backslashes were modified while JavaScript evaluated
that string.

v0.4.5 stores the complete workflow as Base64 and decodes it at runtime.
The workflow GitHub receives is therefore byte-for-byte the workflow tested
during packaging.

Additional validation before packaging:

- worker.js passes `node --check`;
- release YAML parses successfully;
- the `Validate archive` Python block is compiled successfully;
- ZIP paths containing backslashes are rejected using `chr(92)`, avoiding
  cross-language escaping ambiguity.

### One-time recovery

The currently deployed pre-v0.4.5 Worker will keep writing the broken workflow
before every dispatch. Therefore deploy `worker.js` from v0.4.5 to Cloudflare
once. After that, send `Azhand-v0.4.5.zip` normally. Future workflow updates
are automatic again.


## v0.4.5 — Android JVM target fix

Fixes GitHub Actions failure:

`Inconsistent JVM-target compatibility detected for tasks
'compileDebugJavaWithJavac' (1.8) and 'compileDebugKotlin' (17).`

Changes:

- Java sourceCompatibility = 17
- Java targetCompatibility = 17
- Kotlin JVM toolchain = 17
- Android versionCode = 9
- Android versionName = 0.4.5

The GitHub runner already uses Temurin Java 17, so Java and Kotlin now target
the same JVM level.
