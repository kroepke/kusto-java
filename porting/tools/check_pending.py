#!/usr/bin/env python3
# Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2026 Graylog, Inc. Purpose: lists PORT-PENDING / PORT-SKELETON markers per wave (PORTING.md 2.7).
"""List "// PORT-PENDING: W<n>" and "// PORT-SKELETON: W<n>" markers per wave.

Mirrors SourceRules (d) in kusto-language-conformance. A marker line comment without a wave is a
violation. With --allowed-waves W1,W2,... any marker of another wave is a violation too (wave
gates, e.g. W0: "lists only W1/W2/W4/W6 skeletons"). --quiet prints only the per-wave counts.

Usage: python3 porting/tools/check_pending.py [--allowed-waves W1,W2] [--quiet] [ROOT ...]
Default roots: kusto-language/src/main/java and kusto-language-generator/src/main/java.
Exit status 1 on violations. No third-party dependencies.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_headers import DEFAULT_ROOTS, REPO, line_comments  # noqa: E402

WAVE_MARKER = re.compile(r"//\s*PORT-(PENDING|SKELETON)\b:?\s*(W\d+[a-z]?\b)?")


def wave_key(w):
    m = re.match(r"W(\d+)([a-z]?)", w)
    return (int(m.group(1)), m.group(2)) if m else (1 << 30, w)


def main(argv):
    args = argv[1:]
    allowed, quiet, roots = None, False, []
    while args:
        a = args.pop(0)
        if a == "--allowed-waves":
            allowed = {w.strip() for w in args.pop(0).split(",") if w.strip()}
        elif a.startswith("--allowed-waves="):
            allowed = {w.strip() for w in a.split("=", 1)[1].split(",") if w.strip()}
        elif a == "--quiet":
            quiet = True
        else:
            roots.append(a)
    roots = roots or DEFAULT_ROOTS
    by_wave, violations = {}, []
    for root in roots:
        base = root if os.path.isabs(root) else os.path.join(REPO, root)
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames.sort()
            for name in sorted(filenames):
                if not name.endswith(".java"):
                    continue
                path = os.path.join(dirpath, name)
                rel = os.path.relpath(path, REPO)
                with open(path, encoding="utf-8") as f:
                    src = f.read()
                for off, text in line_comments(src):
                    m = WAVE_MARKER.match(text)
                    if not m:
                        continue
                    line = src.count("\n", 0, off) + 1
                    kind = "PORT-" + m.group(1)
                    if m.group(2) is None:
                        violations.append("%s:%d: %s without a wave (W<n>): %s" % (rel, line, kind, text.strip()))
                        continue
                    wave = m.group(2)
                    by_wave.setdefault(wave, []).append((kind, rel, line))
                    if allowed is not None and wave not in allowed:
                        violations.append("%s:%d: %s %s not in allowed waves %s"
                                          % (rel, line, kind, wave, ",".join(sorted(allowed, key=wave_key))))
    for wave in sorted(by_wave, key=wave_key):
        items = by_wave[wave]
        pending = sum(1 for k, _, _ in items if k == "PORT-PENDING")
        print("%s: %d markers (%d PORT-PENDING, %d PORT-SKELETON)" % (wave, len(items), pending, len(items) - pending))
        if not quiet:
            for kind, rel, line in items:
                print("  %s %s:%d" % (kind, rel, line))
    if not by_wave:
        print("no PORT-PENDING / PORT-SKELETON markers")
    for v in violations:
        print(v)
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
