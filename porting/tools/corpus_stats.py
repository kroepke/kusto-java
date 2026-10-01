#!/usr/bin/env python3
"""Print per-corpus statistics for kusto-language-conformance/.../corpus/*.jsonl."""
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from corpuslib import CORPUS


def main():
    cols = ["corpus", "records", "bytes", "maxLen", "commands", "withCR", "nonASCII", "withSchema"]
    rows = []
    for p in sorted(CORPUS.glob("*.jsonl")):
        n = b = mx = cmd = cr = na = sc = 0
        for line in p.read_text(encoding="utf-8").splitlines():
            r = json.loads(line)
            t = r["text"]
            n += 1
            b += len(t.encode("utf-8"))
            mx = max(mx, len(t))
            cmd += t.lstrip().startswith(".")
            cr += "\r" in t
            na += any(ord(c) > 127 for c in t)
            sc += r["schema"] is not None
        rows.append([p.stem, n, b, mx, cmd, cr, na, sc])
    w = [max(len(str(x)) for x in [c] + [r[i] for r in rows]) for i, c in enumerate(cols)]
    for r in [cols] + rows:
        print("  ".join(str(x).rjust(w[i]) for i, x in enumerate(r)))


if __name__ == "__main__":
    main()
