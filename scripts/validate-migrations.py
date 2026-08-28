#!/usr/bin/env python3
from pathlib import Path
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
files = sorted((ROOT / "migrations").glob("*.sql"))

if not files:
    raise SystemExit("No migrations found")

db = sqlite3.connect(":memory:")

for path in files:
    sql = path.read_text(encoding="utf-8")

    # The Worker migration splitter is semicolon-based and does not parse
    # CREATE TRIGGER BEGIN/END bodies. Reject those migrations before CI/deploy.
    if "CREATE TRIGGER" in sql.upper():
        raise SystemExit(
            f"{path.name}: CREATE TRIGGER is not compatible with "
            "the Worker migration splitter"
        )

    try:
        db.executescript(sql)
    except Exception as exc:
        raise SystemExit(
            f"{path.name}: SQLite validation failed: {exc}"
        )

    print(f"[OK] {path.name}")

db.close()
print("Migration compatibility validation passed.")
