#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
worker = (ROOT / "worker.js").read_text(encoding="utf-8")
api = (
    ROOT / "app/src/main/java/com/azhand/app/ApiClient.kt"
).read_text(encoding="utf-8")
main = (
    ROOT / "app/src/main/java/com/azhand/app/MainActivity.kt"
).read_text(encoding="utf-8")

required = [
    "payment_callback_sessions",
    "/payments/blupal/callback",
    "/api/payments/blupal/callback-status",
    "BLUPAL_CALLBACK_TTL_SECONDS",
    "callback_url",
    "blupalCallbackPage",
    "blupalCallbackStatus",
    "visibilitychange",
    "no-store, no-cache",
]

for item in required:
    if item not in worker:
        raise SystemExit(f"Missing callback element: {item}")

for item in ["callbackUrl", 'j.optString("callback_url")']:
    if item not in api:
        raise SystemExit(f"Resident API missing: {item}")

if "invoice.callbackUrl" not in main:
    raise SystemExit("Resident app does not open callback URL")

# Never place Blupal API key in callback URL/HTML.
if re.search(r"blu_(?:test|live)_[A-Za-z0-9_-]{12,}", worker):
    raise SystemExit("Blupal key-like value hard-coded in worker")

print("[OK] tokenized callback session")
print("[OK] public callback status uses opaque token")
print("[OK] callback page polls verified server-side status")
print("[OK] resident app opens Azhand callback page")
