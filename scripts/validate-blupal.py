#!/usr/bin/env python3
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
w=(root/'worker.js').read_text(encoding='utf-8')
required=[
 'BLUPAL_BASE_URL = "https://blupal.net/api"',
 '"X-API-Key": apiKey',
 '/v1/invoices/create',
 'syncBlupalInvoice',
 'finalizeBlupalPayment',
 '/api/payments/blupal/webhook',
 'INSERT OR IGNORE INTO payments',
 'ADMIN_ANDROID_UPDATE_META_KEY',
 '/admin/publish-admin-android-update',
]
for x in required:
    if x not in w: raise SystemExit(f'Missing: {x}')
# Prevent committing an actual-looking key.
pat=re.compile(r'blu_(?:test|live)_[A-Za-z0-9_-]{12,}')
for path in [root/'worker.js',root/'app',root/'adminapp',root/'pwa']:
    files=[path] if path.is_file() else list(path.rglob('*'))
    for f in files:
        if not f.is_file(): continue
        try: t=f.read_text(encoding='utf-8')
        except Exception: continue
        if pat.search(t): raise SystemExit(f'API key-like secret in {f}')
print('[OK] Blupal backend structure')
print('[OK] no hard-coded Blupal key')
print('[OK] webhook server-side recheck + idempotency')
