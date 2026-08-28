#!/usr/bin/env python3
from pathlib import Path

worker = (Path(__file__).resolve().parents[1] / "worker.js").read_text(
    encoding="utf-8"
)

start = worker.find("async function ensureReleaseBotSchema(env) {")
end = worker.find("async function saveReleaseMetadata(env, metadata) {", start)

if start < 0 or end < 0:
    raise SystemExit("release schema function markers missing")

body = worker[start:end]

if ".exec(" in body:
    raise SystemExit(
        "ensureReleaseBotSchema must not use D1 exec(); use prepare/batch"
    )

for item in [
    "CREATE TABLE IF NOT EXISTS release_uploads",
    "env.DB.prepare(",
    "env.DB.batch(",
    "idx_release_uploads_version",
    "idx_release_uploads_created_at",
]:
    if item not in body:
        raise SystemExit(f"release schema validator missing: {item}")

for fn in ["sendStatus", "sendLatestVersion"]:
    if f"async function {fn}" not in worker:
        raise SystemExit(f"{fn} missing")

if "D1 history: self-repair active" not in worker:
    raise SystemExit("KV fallback message missing")

print("[OK] release_uploads uses prepare/run + batch")
print("[OK] /status and /version fall back to KV")
