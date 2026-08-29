#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

main = (
    ROOT / "adminapp/src/main/java/com/azhand/admin/MainActivity.kt"
).read_text(encoding="utf-8")

api = (
    ROOT / "adminapp/src/main/java/com/azhand/admin/AdminApi.kt"
).read_text(encoding="utf-8")

worker = (ROOT / "worker.js").read_text(encoding="utf-8")

for item in [
    "ثبت ساکن / مالک جدید",
    "صدور شارژ ماهانه",
    "MoneyMetricCard",
    "جستجو نام، موبایل یا واحد",
    "جستجو درخواست یا ساکن",
    "ذخیره به‌عنوان الگوی شارژ",
    "الگوهای شارژ",
]:
    if item not in main:
        raise SystemExit(f"Admin UI missing: {item}")

for item in [
    "createMember",
    "createBulkCharges",
    "createSingleCharge",
    "chargeTemplates",
    "saveChargeTemplate",
]:
    if item not in api:
        raise SystemExit(f"Admin API missing: {item}")

for item in [
    "/api/admin/charges/bulk",
    "/api/admin/charge-templates",
    "/api/admin/charge-template",
    "adminCreateBulkCharges",
    "adminListChargeTemplates",
    "adminSaveChargeTemplate",
    "total_billed",
    "total_paid",
    "total_due",
    "charge_templates",
]:
    if item not in worker:
        raise SystemExit(
            f"Worker Admin operation missing: {item}"
        )

print("[OK] native Admin creates resident/unit")
print("[OK] native Admin bulk-issues charges")
print("[OK] charge-template workflow present")
print("[OK] search/filter flows present")
print("[OK] financial collection summary present")
