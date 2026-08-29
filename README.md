# Azhand v0.7.0

پایه‌ی یکپارچه اپلیکیشن مدیریت مجتمع آژند.

## Android

- Kotlin
- Jetpack Compose
- Package: `com.azhand.app`
- Version: `0.7.0`
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

`v0.7.0`

بعد از موفقیت:

1. GitHub main باید فایل‌های پروژه را داشته باشد.
2. GitHub Actions باید Android debug APK را Build کند.
3. Worker باید به 0.7.0 آپدیت شود.
4. مسیر `/app` باید PWA آژند را نشان دهد.
5. مسیر `/api/app/health` باید `ok: true` برگرداند.


## Fix v0.7.0 — GitHub workflow permission

v0.4.0 reached the local commit stage, but GitHub rejected the push because
the release tried to create `.github/workflows/android-build.yml`.

v0.7.0 fixes that by:

- excluding `.github/workflows/**` from ZIP synchronization;
- removing the separate `android-build.yml` from the release package;
- letting the Release Bot Setup manage workflow files with the user's
  fine-grained PAT (`Contents: Read/Write` + `Workflows: Read/Write`);
- building the Android debug APK inside `azhand-release.yml`;
- uploading the APK as a GitHub Actions artifact.

After v0.7.0 deploys, open `/setup` and save once to update the GitHub release
workflow. Then send v0.7.0 again to test the integrated Android build.


## v0.7.0 — Self-healing release/build pipeline

From v0.7.0 onward, before every normal ZIP release the Telegram Worker uses
its saved GitHub fine-grained PAT to create/update the single stable release
workflow. Normal ZIPs never modify `.github/workflows/*`.

The stable workflow calls `scripts/build-android.sh`, so future Android build
changes can be shipped inside the ZIP without changing GitHub workflow files.

### One-time bridge from the currently deployed v0.4.1 Worker

The currently active v0.4.1 code cannot use features that only exist in v0.7.0
before v0.7.0 has been deployed.

1. Send `Azhand-v0.7.0.zip` once.
2. After the bot reports that the Worker updated, send `/build`.

`/build` reuses the same v0.7.0 ZIP already stored in KV, updates the GitHub
workflow automatically, and starts Android build. After that, every later ZIP
builds Android automatically on its first upload, with no `/setup` step.


## v0.7.0 — D1-independent build recovery

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

1. Send `Azhand-v0.7.0.zip`.
2. Wait for Worker auto-deploy.
3. Send `/build`.

After v0.7.0 is active, normal future ZIP uploads auto-build on the first try.


## v0.7.0 — lossless workflow embedding

Fixes the GitHub Actions failure:

`SyntaxError: unterminated string literal`

Root cause:
the Cloudflare Worker stored the GitHub workflow inside a JavaScript template
literal. Bash and Python backslashes were modified while JavaScript evaluated
that string.

v0.7.0 stores the complete workflow as Base64 and decodes it at runtime.
The workflow GitHub receives is therefore byte-for-byte the workflow tested
during packaging.

Additional validation before packaging:

- worker.js passes `node --check`;
- release YAML parses successfully;
- the `Validate archive` Python block is compiled successfully;
- ZIP paths containing backslashes are rejected using `chr(92)`, avoiding
  cross-language escaping ambiguity.

### One-time recovery

The currently deployed pre-v0.7.0 Worker will keep writing the broken workflow
before every dispatch. Therefore deploy `worker.js` from v0.7.0 to Cloudflare
once. After that, send `Azhand-v0.7.0.zip` normally. Future workflow updates
are automatic again.


## v0.7.0 — Android JVM target fix

Fixes GitHub Actions failure:

`Inconsistent JVM-target compatibility detected for tasks
'compileDebugJavaWithJavac' (1.8) and 'compileDebugKotlin' (17).`

Changes:

- Java sourceCompatibility = 17
- Java targetCompatibility = 17
- Kotlin JVM toolchain = 17
- Android versionCode = 9
- Android versionName = 0.7.0

The GitHub runner already uses Temurin Java 17, so Java and Kotlin now target
the same JVM level.


## v0.7.0 — In-app Android updater

The Android app now checks the Azhand Worker on launch and shows an update
dialog when a newer signed release exists.

Update metadata:

`GET /api/app/update?current_version_code=<code>`

Latest APK:

`GET /app-update/latest.apk`

GitHub Actions publishes the signed release APK directly into Cloudflare KV.
Users no longer need to visit GitHub to download each update.

The app verifies SHA-256 before opening Android's installer.

### Stable signing (one-time)

Configure these repository secrets exactly once:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keep the signing key permanently. Future releases of `com.azhand.app` must use
the same key.

The current debug app is `com.azhand.app.debug`; the signed release is
`com.azhand.app`, so both can coexist during the transition.

Android requires a user confirmation for sideloaded APK installation; a normal
app cannot silently replace itself.

### One-time transition

1. Send v0.7.0 so the Worker updates.
2. Add the four signing secrets from the signing bundle.
3. Send `/build` once.
4. Install the signed v0.7.0 from `/app-update/latest.apk`.
5. From v0.7.0 onward, the release app detects updates itself.


## v0.7.0 — automatic Android signing

No GitHub Android signing secrets are required.

On the first v0.7.0 build, GitHub Actions automatically creates the permanent
Azhand JKS signing identity and stores it in Cloudflare KV. Future builds
retrieve and reuse exactly the same signing identity.

The signing identity cannot be overwritten automatically after initialization.

The app update pipeline remains:

signed APK -> Cloudflare KV -> `/api/app/update` -> in-app download/install.

One-time bridge from the older Worker:
send v0.7.0, wait for Worker deploy, then send `/build` once. After that normal
future ZIP releases require no manual signing setup.


## v0.7.0 — release token pipeline hardening

Fixes Run #10:

`DOWNLOAD_TOKEN: unbound variable`

All short-lived release tokens are now read directly from `GITHUB_EVENT_PATH`
inside the exact step that needs them:

- DOWNLOAD_TOKEN
- SIGNING_TOKEN
- UPDATE_TOKEN
- DEPLOY_TOKEN

They are not placed in GitHub Actions step `env` blocks.

Release workflow validation performed before packaging:

- YAML parse
- `bash -n` on every Bash run block
- Python compile on every Python run block
- static token assignment checks
- checks preventing short-lived token expressions from appearing in step env


## v0.7.0 — resident accounts + real D1 data

New:
- `/manage`
- `POST /api/app/auth/login`
- `POST /api/app/auth/logout`
- `GET /api/app/dashboard`
- `POST /api/app/service-requests`
- `GET /api/admin/overview`
- `POST /api/admin/member`
- `POST /api/admin/charge`
- `POST /api/admin/announcement`

No new Cloudflare or GitHub secrets are required.
Existing `SETUP_ADMIN_KEY` protects manager APIs.


## v0.7.0 — updater reliability + automatic D1 migrations

Updater:
- check on app launch;
- re-check whenever the app returns to foreground;
- manual check button in Account;
- visible checking/latest/update/error states;
- no-cache on client and update API;
- version label uses BuildConfig.

D1:
- removes multiline `DB.exec()` usage that caused `incomplete input`;
- bootstraps schema table via `prepare().run()`;
- splits migration SQL by semicolon safely;
- applies each migration through `DB.batch()`;
- records a migration only after it succeeds;
- opening `/manage` automatically upgrades the schema;
- `/api/admin/schema` reports applied migration state;
- future CI runs apply D1 migrations after Worker deployment.

No manual SQL is required.


## v0.7.0 — payments + management operations

New D1 migration:
`0004_management_payments`

Resident:
- submit manual/card-transfer payment for an unpaid charge;
- reference number validation;
- see pending/approved/rejected payment submissions;
- approved submissions reduce charge balance.

Manager `/manage`:
- resident/unit list;
- charge list;
- pending payment review;
- approve/reject payment;
- service-request status management.

New APIs:
- `POST /api/app/payment-submissions`
- `GET /api/admin/data`
- `POST /api/admin/payment/review`
- `POST /api/admin/service-request/status`

The existing automatic D1 migration pipeline applies `0004` during release.


## v0.7.0 — Manager panel JavaScript hotfix

Fixes the manager page staying forever on "در حال بارگذاری...".

Root cause:
the generated HTML used nested single-quoted inline `onclick` handlers.
After embedding/decoding, the escaping produced invalid JavaScript and the
browser stopped parsing the whole manager script before `refreshAll()` ran.

v0.7.0:
- removes fragile inline onclick string generation;
- uses `data-*` actions + one delegated click handler;
- uses template literals for row rendering;
- shows a visible load error if initial refresh fails;
- adds `scripts/validate-web-assets.py`;
- every Android/release build now runs Node syntax validation against the
  embedded Manager JavaScript AND PWA JavaScript before Gradle/build/deploy.

This prevents a successful CI deployment containing syntactically broken
embedded web UI.


## v0.7.0 — Telegram release-history D1 self-repair

Fixes `/status` and `/version` returning:
`D1_ERROR: no such table: release_uploads`

The application D1 migrations were already fixed, but the older release-bot
schema bootstrap still used multiline `DB.exec()`.

v0.7.0:
- creates `release_uploads` using `prepare().run()`;
- creates indexes with `DB.batch()`;
- release history never blocks ZIP build/deploy;
- `/status` and `/version` fall back to KV metadata if D1 history is empty;
- later releases automatically repopulate D1 history;
- CI now rejects any future return of `DB.exec()` inside
  `ensureReleaseBotSchema()`.

No manual D1 SQL is required.


## v0.7.0 — notifications, receipts, expenses, access-code reset

New D1 migration:
`0005_notifications_receipts`

Resident:
- personal in-app notifications;
- unread notification counter;
- mark notifications as read;
- payment approval/rejection notifications;
- service-request status notifications;
- permanent receipt number for approved manual payments;
- receipt number shown in Android and PWA.

Manager:
- create and view building expenses;
- issue a new resident access code;
- access-code reset revokes existing sessions for that member;
- payment and service actions automatically create resident notifications.

New APIs:
- `POST /api/app/notifications/read`
- `POST /api/admin/expense`
- `POST /api/admin/member/access-code`

No external push provider or payment gateway is required for this release.


## v0.8.4 — Admin compile hotfix

GitHub Actions Run #19 failed at `Build Android`.

Exact compiler failure:
- missing closing brace in `adminapp/MainActivity.kt` Login composable;
- all `AdminApp/Home/Payments/Requests/Members/More` unresolved errors were
  cascading parser errors from that missing brace.

v0.8.4:
- rewrites Admin Login as normal multi-line Kotlin;
- adds `validate-kotlin-structure.py` before Gradle;
- Resident and Admin app are both versionCode 20;
- after first successful deploy, Worker self-syncs the new GitHub workflow;
- a one-time KV bootstrap automatically triggers one fresh build with new
  workflow tokens, so the native Admin APK pipeline activates without a
  manual `/setup` or second ZIP upload.


## v0.8.4 — D1 migration and signing transition hotfix

GitHub Actions Run #20:
- Resident + Admin debug builds succeeded.
- Upload artifact succeeded.
- Worker deploy succeeded.
- D1 migration returned HTTP 500.
- Signed release was skipped because the previous GitHub workflow exported
  `ANDROID_KEYSTORE_PATH=azhand-release.jks`, while the restored file was
  `app/azhand-release.jks`.

D1 root cause:
`0006_blupal_admin_app.sql` used a SQLite `CREATE TRIGGER ... BEGIN ...; END;`.
The current Worker migration engine splits versioned migration SQL on
semicolons, so trigger-body semicolons were split into invalid statements.

Fixes:
- remove the trigger from migration 0006;
- scope the unique payment reference index to `gateway='blupal'`;
- recompute charge paid_amount from the paid ledger after verified Blupal
  payment, which is retry-safe and idempotent;
- build script resolves both legacy and current keystore paths;
- add a migration compatibility test that rejects CREATE TRIGGER before build;
- new workflow prints the D1 endpoint response body when migration fails.


## v0.8.4 — Resident UI regression recovery

The resident Android UI was unintentionally simplified in v0.8.0 while
Blupal and the native Admin app were introduced.

v0.8.4 uses the complete v0.7.0 resident UI as the visual/functional baseline
and merges all newer backend capabilities on top of it.

Restored:
- full ScreenContainer-based design;
- original tab identity instead of generic dot icons;
- Home dashboard cards and MiniStat blocks;
- full manual-payment submission and history UI;
- service request dialog/history;
- personal notifications and announcements;
- Account profile UI;
- manual updater status, retry button, foreground re-check;
- original reusable NoticeCard/ProfileLine/SectionTitle components.

Preserved/merged:
- Blupal online payment creation;
- exact Rial final amount;
- payment-link launch;
- pending payment status re-check;
- Blupal receipts;
- Admin Android app;
- D1 migrations;
- current Worker and release pipeline.

New CI guard:
`scripts/validate-resident-ui.py`

The release now fails if core resident UI/functionality is accidentally
removed again.


## v0.8.4 — Admin redesign + shared icon + Blupal callback UX

Admin Android:
- exact same launcher icon bytes as the resident app at all densities;
- polished Azhand Navy/Gold login and dashboard;
- app logo shown on login and header;
- emoji-based navigation instead of generic dots;
- overview metrics, online Blupal payments, manual payments, charges,
  service requests and residents;
- Blupal setup card shows both Webhook and Azhand callback page.

Blupal callback:
- new migration `0007_blupal_callback.sql`;
- short-lived opaque callback session, raw token never stored in D1;
- new `/payments/blupal/callback?token=...` branded page;
- new `/api/payments/blupal/callback-status` public safe status endpoint;
- callback page polls status and re-verifies PENDING invoices server-to-server;
- resident Android and PWA open the Azhand callback page first;
- no API key or resident bearer token is exposed in the callback URL.
