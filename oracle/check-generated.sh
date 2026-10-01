#!/usr/bin/env bash
# Compares oracle/generated/GeneratedSyntaxNodes.cs with porting/reference/GeneratedSyntaxNodes.cs.
# Normalisation (both sides): strip a UTF-8 BOM, strip leading lines that are exactly "// "
# (the T4 include residue), strip trailing newlines. Then the bytes must be equal.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/.." && pwd)"
generated="${1:-$here/generated/GeneratedSyntaxNodes.cs}"
reference="${2:-$root/porting/reference/GeneratedSyntaxNodes.cs}"
python3 - "$generated" "$reference" <<'PY'
import sys

def norm(path):
    data = open(path, "rb").read()
    if data.startswith(b"\xef\xbb\xbf"):
        data = data[3:]
    while data.startswith(b"// \n") or data.startswith(b"// \r\n"):
        data = data[data.index(b"\n") + 1:]
    return data.rstrip(b"\r\n")

gen, ref = norm(sys.argv[1]), norm(sys.argv[2])
if gen == ref:
    print(f"check-generated: OK ({len(gen)} bytes identical)")
    sys.exit(0)
n = min(len(gen), len(ref))
i = next((k for k in range(n) if gen[k] != ref[k]), n)
line = gen[:i].count(b"\n") + 1
print(f"check-generated: MISMATCH at byte {i} (line {line}); generated {len(gen)} bytes, reference {len(ref)} bytes")
print("generated:", gen[max(0, i - 80):i + 80])
print("reference:", ref[max(0, i - 80):i + 80])
sys.exit(1)
PY
