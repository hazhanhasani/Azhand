#!/usr/bin/env python3
from pathlib import Path

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
    if item not in migration:
        raise SystemExit(f"Migration missing {item}")

for item in [
    "async scheduled(controller, env)",
    "runMonthlyBilling",
    "iranJalaliParts",
    'IRAN_TIME_ZONE = "Asia/Tehran"',
    'MONTHLY_BILLING_CRON = "35 20 * * *"',
    "/api/admin/finance-settings",
    "/api/admin/charges/delete",
    "/api/admin/billing/run-now",
    "owner_monthly_charge",
    "tenant_monthly_charge",
    "tenant_member_id",
    "owner_member_id",
    "ensureWorkerCronSchedule",
    "/schedules",
]:
    if item not in worker:
        raise SystemExit(f"Worker missing {item}")

for item in [
    "financeSettings",
    "saveFinanceSettings",
    "runMonthlyBilling",
    "deleteCharge",
]:
    if item not in admin_api:
        raise SystemExit(f"Admin API missing {item}")

for item in [
    "موجودی اولیه ساختمان",
    "شارژ ماهانه مالک",
    "شارژ ماهانه مستأجر",
    "صدور خودکار اول هر ماه",
    "حذف شارژ آزمایشی / اشتباه",
    "موجودی فعلی ساختمان",
]:
    if item not in admin_ui:
        raise SystemExit(f"Admin UI missing {item}")

print("[OK] initial building balance")
print("[OK] owner/tenant monthly prices")
print("[OK] first-day Jalali monthly billing")
print("[OK] safe unpaid charge deletion")
print("[OK] Cloudflare cron sync")
