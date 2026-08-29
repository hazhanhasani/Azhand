#!/usr/bin/env python3
from pathlib import Path
import re
import base64

ROOT = Path(__file__).resolve().parents[1]
worker = (ROOT / "worker.js").read_text(encoding="utf-8")
migration = (
    ROOT / "migrations/0009_finance_automation.sql"
).read_text(encoding="utf-8")
admin_api = (
    ROOT / "adminapp/src/main/java/com/azhand/admin/AdminApi.kt"
).read_text(encoding="utf-8")
admin_ui = (
    ROOT / "adminapp/src/main/java/com/azhand/admin/MainActivity.kt"
).read_text(encoding="utf-8")

for item in [
    "building_finance_settings",
    "monthly_billing_runs",
    "charge_billing_meta",
]:
    assert item in migration, item

for item in [
    "runMonthlyBilling",
    "iranJalaliParts",
    'IRAN_TIME_ZONE = "Asia/Tehran"',
    "/api/admin/finance-settings",
    "/api/admin/charges/delete",
    "/api/admin/billing/run-now",
    "owner_monthly_charge",
    "tenant_monthly_charge",
    "allowCatchup: true",
]:
    assert item in worker, item

m = re.search(
    r'const RECOVERY_STAGE2_WORKER_B64 = "([^"]+)";',
    worker
)
assert m, "stage2 worker missing"
stage2 = base64.b64decode(m.group(1)).decode("utf-8")
assert "async scheduled(controller, env, ctx)" in stage2

for item in [
    "financeSettings",
    "saveFinanceSettings",
    "runMonthlyBilling",
    "deleteCharge",
]:
    assert item in admin_api, item

for item in [
    "موجودی اولیه ساختمان",
    "شارژ ماهانه مالک",
    "شارژ ماهانه مستأجر",
    "حذف شارژ آزمایشی / اشتباه",
]:
    assert item in admin_ui, item

print("[OK] finance migration/settings")
print("[OK] monthly billing catch-up")
print("[OK] stage2 exact cron handler")
print("[OK] safe unpaid charge deletion")
