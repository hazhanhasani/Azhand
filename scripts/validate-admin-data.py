#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
worker = (ROOT / "worker.js").read_text(encoding="utf-8")

start = worker.index("async function adminData(request, env) {")
end = worker.index("\nasync function adminReviewPayment", start)
fn = worker[start:end]

required = [
    'error: "دسترسی مدیریت نامعتبر است."',
    "await ensureAppSchema(env)",
    "financialSummary",
    "financeSettingsRow",
    "mappedAdminCharges",
    "building_balance",
    "iran_now: iranNowDisplay()",
    "allowCatchup: true",
]

for item in required:
    assert item in fn, item

# Regression guard for the malformed v0.9.2 function.
prefix = fn.split("try {", 1)[0]
assert "financeSettingsRow" not in prefix
assert "financialSummary" not in prefix

print("[OK] adminData auth branch")
print("[OK] adminData finance queries")
print("[OK] adminData Jalali mappings")
