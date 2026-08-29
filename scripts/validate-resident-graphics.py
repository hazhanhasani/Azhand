#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (
    ROOT / "app/src/main/java/com/azhand/app/MainActivity.kt"
).read_text(encoding="utf-8")

for item in [
    "R.mipmap.ic_launcher",
    "ContentScale.Crop",
    "CircleShape",
    'HOME("خانه", "🏠")',
    'FINANCE("مالی", "💳")',
    'NOTICES("اعلانات", "🔔")',
    "LinearProgressIndicator",
    "طرف حساب:",
    "مانده حساب",
]:
    if item not in main:
        raise SystemExit(
            f"Resident graphical UI missing: {item}"
        )

print("[OK] graphical brand header")
print("[OK] resident launcher icon in screens")
print("[OK] expressive bottom navigation")
print("[OK] financial progress visualization")
print("[OK] payer/date context in charge cards")
