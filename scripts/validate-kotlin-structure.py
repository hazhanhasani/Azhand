#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def validate(path: Path):
    text = path.read_text(encoding="utf-8")
    stack = []
    state = "code"
    quote = None
    line = 1
    i = 0

    while i < len(text):
        ch = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ""

        if ch == "\n":
            line += 1

        if state == "line_comment":
            if ch == "\n":
                state = "code"
            i += 1
            continue

        if state == "block_comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 2
                continue
            i += 1
            continue

        if state == "string":
            if ch == "\\":
                i += 2
                continue
            if ch == quote:
                state = "code"
                quote = None
            i += 1
            continue

        if ch == "/" and nxt == "/":
            state = "line_comment"
            i += 2
            continue

        if ch == "/" and nxt == "*":
            state = "block_comment"
            i += 2
            continue

        if ch in ('"', "'"):
            state = "string"
            quote = ch
            i += 1
            continue

        if ch == "{":
            stack.append(line)
        elif ch == "}":
            if not stack:
                raise SystemExit(
                    f"{path}: extra closing brace at line {line}"
                )
            stack.pop()

        i += 1

    if stack:
        raise SystemExit(
            f"{path}: unclosed opening brace(s): {stack}"
        )

for module in ("app", "adminapp"):
    files = list((ROOT / module / "src").rglob("*.kt"))
    if not files:
        raise SystemExit(f"No Kotlin files found in {module}")
    for path in files:
        validate(path)
        print(f"[OK] {path.relative_to(ROOT)}")

print("Kotlin structure validation passed.")
