# بلوپال در آژند v0.8.0

## معماری امنیتی

- Base API: `https://blupal.net/api`
- `X-API-Key` فقط از Cloudflare Worker ارسال می‌شود.
- کلید با نام `BLUPAL_API_KEY` به‌صورت Worker Secret ذخیره می‌شود.
- هیچ کلید بلوپال داخل APK ساکن، APK مدیریت یا PWA قرار نمی‌گیرد.

## جریان پرداخت

1. ساکن یک شارژ و مبلغ تومان را انتخاب می‌کند.
2. Worker مبلغ را به ریال تبدیل می‌کند (`تومان × 10`).
3. Worker فاکتور را از `POST /v1/invoices/create` می‌سازد.
4. `invoice_id`, `payment_link`, `final_amount`, `mode`, `card_number` در D1 ذخیره می‌شوند.
5. اپ لینک پرداخت را باز می‌کند و مبلغ دقیق `final_amount` را به ریال نشان می‌دهد.
6. هنگام بازگشت کاربر یا بررسی دستی، Worker از `GET /v1/invoices/{invoice_id}` وضعیت را دوباره از بلوپال می‌گیرد.
7. Webhook اختیاری روی `/api/payments/blupal/webhook` دریافت می‌شود، اما Payload آن به‌تنهایی مورد اعتماد نیست؛ Worker مجدداً همان invoice را server-to-server بررسی می‌کند.
8. پرداخت موفق با unique index و trigger دیتابیس idempotent است و مانده شارژ فقط یک بار کم می‌شود.
9. رسید آژند با قالب `BLU-XXXXXXXX` ثبت می‌شود.

## محدودیت مبلغ

بلوپال مبلغ را به ریال می‌گیرد:
- حداقل: 100,000 ریال = 10,000 تومان
- حداکثر: 500,000,000 ریال = 50,000,000 تومان

`final_amount` می‌تواند مبلغ اصلی به‌علاوه عدد تصادفی سه‌رقمی ریالی باشد؛ بنابراین اپ مبلغ نهایی دقیق را به ریال نشان می‌دهد.

## Sandbox / Live

- `blu_test_...` → Sandbox
- `blu_live_...` → Live

کلید یک‌بار از اپ مدیریت وارد می‌شود و با Cloudflare secrets-bulk روی Worker ذخیره می‌شود.

## Webhook

آدرس:
`https://<worker-host>/api/payments/blupal/webhook`

Webhook برای کارکرد اصلی اجباری نیست، چون Dashboard آخرین invoice در وضعیت PENDING را نیز از API بلوپال Sync می‌کند.
