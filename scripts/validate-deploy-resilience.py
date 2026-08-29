#!/usr/bin/env python3
from pathlib import Path
import re
import base64

ROOT = Path(__file__).resolve().parents[1]
worker = (ROOT / "worker.js").read_text(encoding="utf-8")

# Recovery bridge itself intentionally has no scheduled export so it can be
# uploaded through the last-known-good v0.9.0 deploy endpoint.
assert "RECOVERY_STAGE2_WORKER_B64" in worker
assert "promoteRecoveryStage2" in worker
assert "syncRecoveryStage2SourceToGithub" in worker
assert "bootstrap_rebuild" in worker
assert 'compatibility_date: "2026-08-29"' in worker

m = re.search(
    r'const RECOVERY_STAGE2_WORKER_B64 = "([^"]+)";',
    worker
)
assert m, "stage2 source missing"

stage2 = base64.b64decode(m.group(1)).decode("utf-8")
assert "async scheduled(controller, env, ctx)" in stage2
assert "allowCatchup: false" in stage2
assert "ensureWorkerCronSchedule" in stage2

m2 = re.search(
    r'const RELEASE_WORKFLOW_B64 = "([^"]+)";',
    worker
)
assert m2, "release workflow missing"

workflow = base64.b64decode(m2.group(1)).decode("utf-8")
for item in [
    'response_file="/tmp/azhand-worker-deploy.json"',
    "for attempt in 1 2 3",
    "429|500|502|503|504",
]:
    assert item in workflow, item

print("[OK] fetch-only recovery bridge")
print("[OK] stage2 scheduled worker embedded")
print("[OK] stage2 compatibility_date promotion")
print("[OK] hardened workflow embedded")
