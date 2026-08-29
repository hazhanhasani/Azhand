#!/usr/bin/env python3
from pathlib import Path
import re,base64
ROOT=Path(__file__).resolve().parents[1]
w=(ROOT/'worker.js').read_text(encoding='utf-8')
for x in ['async scheduled(controller, env)','compatibility_date: "2026-08-29"','body: JSON.stringify([','cloudflare_errors']:
    assert x in w, x
m=re.search(r'const RELEASE_WORKFLOW_B64 = "([^"]+)";',w); f=base64.b64decode(m.group(1)).decode()
for x in ['response_file="/tmp/azhand-worker-deploy.json"','for attempt in 1 2 3','429|500|502|503|504']:
    assert x in f, x
print('[OK] deploy diagnostics/retry and Cloudflare Cron payload')
