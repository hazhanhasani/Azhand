#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
worker = (ROOT / "worker.js").read_text(encoding="utf-8")
resident = (
    ROOT / "app/src/main/java/com/azhand/app/MainActivity.kt"
).read_text(encoding="utf-8")
resident_api = (
    ROOT / "app/src/main/java/com/azhand/app/ApiClient.kt"
).read_text(encoding="utf-8")
admin = (
    ROOT / "adminapp/src/main/java/com/azhand/admin/MainActivity.kt"
).read_text(encoding="utf-8")
admin_api = (
    ROOT / "adminapp/src/main/java/com/azhand/admin/AdminApi.kt"
).read_text(encoding="utf-8")

for item in [
    'en-US-u-ca-persian-nu-latn',
    'timeZone: IRAN_TIME_ZONE',
    "iranNowDisplay",
    "displayDateValue",
    "jalaliPeriodKey",
    "jalaliMonthName",
]:
    if item not in worker:
        raise SystemExit(f"Jalali helper missing: {item}")

if 'iran_now: iranNowDisplay()' not in worker:
    raise SystemExit("Resident/Admin Iran clock response missing")

for item in [
    "iranNow",
    "currentChargeDueDate",
]:
    if item not in resident_api:
        raise SystemExit(f"Resident API missing {item}")

if "🕒 ${data?.iranNow}" not in resident:
    raise SystemExit("Resident graphical Iran clock missing")

if "iranNow" not in admin_api:
    raise SystemExit("Admin API Iran clock missing")

if "🕒 $iranNow" not in admin:
    raise SystemExit("Admin Iran clock header missing")

for bad in [
    'Text("تاریخ YYYY-MM-DD")',
    'Text("تاریخ سررسید YYYY-MM-DD")',
]:
    if bad in admin:
        raise SystemExit(f"Gregorian UI label remains: {bad}")

print("[OK] Persian/Jalali calendar formatter")
print("[OK] Asia/Tehran timezone")
print("[OK] Resident Iran clock")
print("[OK] Admin Iran clock")
print("[OK] Jalali date labels")
