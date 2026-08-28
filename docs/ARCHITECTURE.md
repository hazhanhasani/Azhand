# معماری

Telegram Release Bot / Setup
        |
        v
Cloudflare Worker
  |-- /app                 -> PWA
  |-- /api/app/*           -> API
  |-- /telegram/webhook    -> Release Bot
  |-- D1 binding: DB
  |-- KV binding: RELEASE_FILES
        |
        +--> D1: members, units, charges, payments, expenses, announcements
        |
GitHub main
  |-- Android Kotlin/Compose
  |-- GitHub Actions build
  |-- worker.js auto deploy

## امضای Android

`applicationId` ثابت:

`com.azhand.app`

Keystore نباید داخل Repository قرار بگیرد.

GitHub Secrets:

- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

GitHub Variable:

- AZHAND_API_BASE_URL
