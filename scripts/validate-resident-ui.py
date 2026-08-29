#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (
    ROOT / "app/src/main/java/com/azhand/app/MainActivity.kt"
).read_text(encoding="utf-8")
api = (
    ROOT / "app/src/main/java/com/azhand/app/ApiClient.kt"
).read_text(encoding="utf-8")

required_ui = [
    'private enum class AppTab(val label: String, val emoji: String)',
    'ScreenContainer',
    'SectionTitle',
    'MiniStat',
    'NoticeCard',
    'ProfileLine',
    'updateStatus',
    'updateChecking',
    'requestUpdateCheck(force = true)',
    'LifecycleEventObserver',
    'بررسی بروزرسانی',
    'PaymentSubmissionDialog',
    'PaymentSubmissionCard',
    'onSubmitPayment',
    'ثبت واریز دستی',
    'واریزهای دستی ثبت‌شده',
    'onCreateOnlinePayment',
    'onCheckOnlinePayment',
    'OnlinePaymentCard',
    'پرداخت آنلاین با بلوپال',
    'CreateRequestDialog',
    'NoticesScreen',
    'onMarkNotificationRead',
    'AccountScreen',
    'خروج از حساب',
]

for item in required_ui:
    if item not in main:
        raise SystemExit(f"Resident UI regression: missing {item}")

for item in [
    'submitPayment',
    'createBlupalInvoice',
    'checkBlupalInvoice',
    'markNotificationRead',
]:
    if item not in api:
        raise SystemExit(f"Resident API regression: missing {item}")

if 'icon = { Text("●") }' in main:
    raise SystemExit("Resident UI regression: generic dot icons returned")

print("[OK] v0.7 visual/component baseline preserved")
print("[OK] updater UX preserved")
print("[OK] manual payment preserved")
print("[OK] services/notifications/account preserved")
print("[OK] Blupal merged without replacing old resident UI")
