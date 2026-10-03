#!/usr/bin/env python3
# Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2026 Graylog, Inc. Purpose: W6 helper; assembles Binder.java from per-part fragments written by parallel porting agents.
"""Assemble kusto-language/.../binding/Binder.java from fragments in build/w6/.

Fragment file name: NN_<upstream part>.frag (e.g. 01_Binder_API.frag, 07a_Binder_NodeBinder.frag).
Parts are ordered by NN (which follows the ordinal order of the upstream file names, PORTING.md 2.2);
several fragments may share one part (07a, 07b) and are concatenated under one part marker.

Fragment format:
    // IMPORTS
    import a.b.C;
    ...
    // BODY
    <members at Binder class-body level, indented four spaces>

Output: the five PORTING.md 2.1 header lines (one "Ported from:" per part), package, the sorted
union of imports (java.* first, then everything else, blank line between), the class declaration
and the parts. Usage: python3 porting/tools/assemble_binder.py [--frag-dir build/w6] [--out PATH] [--check]
--check only verifies that every fragment parses (IMPORTS/BODY markers present) and prints the part order.
"""
import argparse
import os
import re
import sys

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT = "kusto-language/src/main/java/org/graylog/kusto/language/binding/Binder.java"
PARTS = [
    "Binder_API.cs", "Binder_AsContextBuilder.cs", "Binder_ContextBuilder.cs", "Binder_FunctionCalls.cs",
    "Binder_Misc.cs", "Binder_Names.cs", "Binder_NodeBinder.cs", "Binder_Operators.cs", "Binder_Projection.cs",
    "Binder_SearchPredicateBinder.cs", "Binder_TablesAndColumns.cs", "Binder_TreeBinder.cs",
]
NAME = re.compile(r"^(\d{2})([a-z]?)_(Binder_[A-Za-z]+)\.frag$")


def upstream_commit():
    import json
    with open(os.path.join(REPO, "porting", "manifest.json"), encoding="utf-8") as f:
        return json.load(f)["upstreamCommit"]


def parse_fragment(path):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    if not text.startswith("// IMPORTS"):
        raise SystemExit("%s: must start with '// IMPORTS'" % path)
    try:
        head, body = text.split("\n// BODY\n", 1)
    except ValueError:
        raise SystemExit("%s: missing '// BODY' line" % path)
    imports = []
    for line in head.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        if not (line.startswith("import ") and line.endswith(";")):
            raise SystemExit("%s: bad import line: %s" % (path, line))
        imports.append(line)
    return imports, body.rstrip("\n") + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--frag-dir", default="build/w6")
    ap.add_argument("--out", default=OUT)
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    frag_dir = os.path.join(REPO, a.frag_dir)
    frags = []
    for name in sorted(os.listdir(frag_dir)):
        m = NAME.match(name)
        if not m:
            continue
        part = m.group(3) + ".cs"
        if part not in PARTS:
            raise SystemExit("%s: unknown part %s" % (name, part))
        frags.append((m.group(1), m.group(2), part, os.path.join(frag_dir, name)))
    parts_seen = []
    for _, _, part, _ in frags:
        if part not in parts_seen:
            parts_seen.append(part)
    if parts_seen != [p for p in PARTS if p in parts_seen]:
        raise SystemExit("fragment NN order disagrees with ordinal part order: %s" % parts_seen)
    missing = [p for p in PARTS if p not in parts_seen]
    imports, bodies = set(), []
    for nn, sub, part, path in frags:
        imp, body = parse_fragment(path)
        imports.update(imp)
        bodies.append((part, sub, body))
    print("parts: %s" % ", ".join(parts_seen))
    if missing:
        print("missing parts: %s" % ", ".join(missing))
    if a.check:
        return 0 if not missing else 1
    if missing:
        raise SystemExit("refusing to assemble with missing parts")
    java = sorted(i for i in imports if i.startswith("import java."))
    other = sorted(i for i in imports if not i.startswith("import java."))
    out = []
    for p in PARTS:
        out.append("// Ported from: src/Kusto.Language/Binder/%s" % p)
    out.append("// Upstream: microsoft/Kusto-Query-Language @ %s" % upstream_commit())
    out.append("// SPDX-License-Identifier: Apache-2.0")
    out.append("// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.")
    out.append('// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".')
    out.append("")
    out.append("package org.graylog.kusto.language.binding;")
    out.append("")
    out.extend(java)
    if java and other:
        out.append("")
    out.extend(other)
    out.append("")
    out.append("@Internal")
    out.append("public final class Binder")
    out.append("{")
    last = None
    for part, sub, body in bodies:
        if part != last:
            if last is not None:
                out.append("")
            out.append("    // ===== upstream part: %s =====" % part)
            last = part
        out.append(body.rstrip("\n"))
    out.append("}")
    with open(os.path.join(REPO, a.out), "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print("wrote %s (%d lines)" % (a.out, len(out)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
