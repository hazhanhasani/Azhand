# Azhand Release Bot v0.3.3

## چیزی که عوض شد

این نسخه بعد از هر ZIP:

1. ZIP را از Telegram می‌گیرد.
2. SHA-256 را محاسبه می‌کند.
3. ZIP را موقت در KV ذخیره می‌کند.
4. GitHub Actions را Trigger می‌کند.
5. Workflow ZIP را دانلود و Verify می‌کند.
6. محتویات نسخه را روی `main` Sync و Commit می‌کند.
7. `worker.js` را پیدا می‌کند.
8. از طریق Endpoint امن ربات، محتوای Worker را روی Cloudflare Deploy می‌کند.
9. D1/KV/Secrets و سایر تنظیمات Worker دست‌نخورده می‌مانند.

## Setup خودکار GitHub Workflow

صفحه Setup خودش فایل زیر را در GitHub می‌سازد یا آپدیت می‌کند:

`.github/workflows/azhand-release.yml`

GitHub Fine-grained PAT باید این مجوزها را روی Repository آژند داشته باشد:

- Contents: Read and write
- Workflows: Read and write

## Cloudflare Token

Cloudflare Token باید:

- Account -> Workers Scripts -> Edit

داشته باشد.

برخلاف نسخه قبلی، این Token به‌صورت Secret در خود Worker ذخیره می‌شود؛
چون برای Auto Deploy نسخه‌های بعدی لازم است.

## Bindings

یک بار از Cloudflare Dashboard متصل کن:

- `DB` -> D1
- `RELEASE_FILES` -> KV

## Worker source detection

Workflow به ترتیب دنبال این فایل‌ها می‌گردد:

- `worker.js`
- `worker/worker.js`
- `backend/worker.js`
- `apps/worker/worker.js`

اولین مورد موجود به Cloudflare Deploy می‌شود.

## نکته مهم

اگر Worker آژند همان Release Bot است، باید `worker.js` همین Bot در ZIP نسخه‌های بعدی حفظ شود.
اگر بعداً Backend برنامه را به Worker جدا منتقل کنیم، برای آن یک Target جدا تعریف می‌کنیم.


## Fix v0.3.3

- Fixes GitHub HTTP 422:
  `No more than 10 properties are allowed`
- `repository_dispatch.client_payload` now contains one top-level key:
  `release`
- Workflow reads values from:
  `github.event.client_payload.release.*`
- Version detection also works directly from filenames like:
  `Azhand-Release-Bot-v0.3.3.zip`
- Bot/dashboard always show the actual build version.
- Cloudflare Worker auto-deploy remains enabled through the protected deploy gateway.


## Fix v0.3.3 — Setup page

- Setup form no longer performs a normal HTML submit.
- The page never reloads while saving.
- Setup runs as a background job using `ctx.waitUntil`.
- Progress is stored in KV and shown live:
  GitHub → Telegram → Cloudflare.
- GitHub Workflow errors are shown with their exact API message.
- Cloudflare secrets are saved LAST to avoid interrupting GitHub/Telegram setup.
- If updating Worker secrets switches the Worker version mid-request, the UI keeps polling instead of refreshing.
- Non-sensitive fields (Account ID, Worker name, GitHub owner/repo, Admin ID) are remembered locally in the browser.


## Fix v0.3.3 — Setup Admin Key recovery

- `current_setup_key` comparison now trims accidental whitespace.
- The current key field is no longer a hard lockout.
- If the old Setup Admin Key does not match, a valid Cloudflare API Token
  can authorize recovery/reset of the Setup Admin Key.
- Cloudflare token validity is checked using Cloudflare's token verification API.
- The page reports the exact recovery error instead of only saying
  "کلید فعلی Setup اشتباه است".
- This recovery path does not weaken the security model: the Cloudflare token
  already has permission to modify the Worker itself.
