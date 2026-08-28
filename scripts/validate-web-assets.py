#!/usr/bin/env python3
from pathlib import Path
import base64
import json
import re
import subprocess
import tempfile
import sys

ROOT = Path(__file__).resolve().parents[1]
worker_path = ROOT / "worker.js"
worker = worker_path.read_text(encoding="utf-8")

def node_check(name: str, code: str) -> None:
    with tempfile.NamedTemporaryFile(
        "w",
        suffix=".js",
        encoding="utf-8",
        delete=False
    ) as f:
        f.write(code)
        path = f.name

    result = subprocess.run(
        ["node", "--check", path],
        capture_output=True,
        text=True
    )

    if result.returncode != 0:
        print(f"[FAIL] {name}", file=sys.stderr)
        print(result.stderr, file=sys.stderr)
        raise SystemExit(1)

    print(f"[OK] {name}")

# Manager HTML is Base64 encoded.
m = re.search(
    r'const AZHAND_MANAGE_HTML_B64 = "([^"]+)";',
    worker
)
if not m:
    raise SystemExit("AZHAND_MANAGE_HTML_B64 not found")

manage_html = base64.b64decode(m.group(1)).decode("utf-8")

scripts = re.findall(
    r"<script(?:\s[^>]*)?>(.*?)</script>",
    manage_html,
    flags=re.S | re.I
)
if not scripts:
    raise SystemExit("No manager JavaScript found")

for i, code in enumerate(scripts, start=1):
    node_check(f"manager script #{i}", code)

# PWA HTML is a JSON JavaScript string literal.
m = re.search(
    r"const AZHAND_PWA_HTML = (.*?);\nconst AZHAND_MANIFEST",
    worker,
    flags=re.S
)
if not m:
    raise SystemExit("AZHAND_PWA_HTML not found")

try:
    pwa_html = json.loads(m.group(1))
except Exception as exc:
    raise SystemExit(f"Cannot decode AZHAND_PWA_HTML: {exc}")

scripts = re.findall(
    r"<script(?:\s[^>]*)?>(.*?)</script>",
    pwa_html,
    flags=re.S | re.I
)
if not scripts:
    raise SystemExit("No PWA JavaScript found")

for i, code in enumerate(scripts, start=1):
    node_check(f"PWA script #{i}", code)

print("Embedded web assets syntax validation passed.")
