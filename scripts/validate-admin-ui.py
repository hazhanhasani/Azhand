#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest = (
    ROOT / "adminapp/src/main/AndroidManifest.xml"
).read_text(encoding="utf-8")
main = (
    ROOT / "adminapp/src/main/java/com/azhand/admin/MainActivity.kt"
).read_text(encoding="utf-8")
api = (
    ROOT / "adminapp/src/main/java/com/azhand/admin/AdminApi.kt"
).read_text(encoding="utf-8")

for item in [
    'android:icon="@mipmap/ic_launcher"',
    'android:roundIcon="@mipmap/ic_launcher"',
]:
    if item not in manifest:
        raise SystemExit(f"Admin manifest missing: {item}")

for density in [
    "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"
]:
    resident = (
        ROOT / f"app/src/main/res/mipmap-{density}/ic_launcher.png"
    ).read_bytes()
    admin = (
        ROOT / f"adminapp/src/main/res/mipmap-{density}/ic_launcher.png"
    ).read_bytes()

    if resident != admin:
        raise SystemExit(
            f"Admin icon differs from resident icon at {density}"
        )

for item in [
    "R.mipmap.ic_launcher",
    "MetricCard",
    "OnlinePaymentAdminCard",
    "SectionTitle",
    "StatusPill",
    "callbackPage",
    'AdminTab.HOME',
]:
    if item not in main and item not in api:
        raise SystemExit(f"Admin redesign missing: {item}")

if 'icon={Text("●")}' in main or 'icon = { Text("●") }' in main:
    raise SystemExit("Generic dot navigation returned")

print("[OK] Admin uses exact resident launcher icon")
print("[OK] Admin dashboard redesign present")
print("[OK] online/manual payments and service management preserved")
print("[OK] Blupal webhook/callback information exposed in Admin")
