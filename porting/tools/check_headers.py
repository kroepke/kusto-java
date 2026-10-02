#!/usr/bin/env python3
# Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2026 Graylog, Inc. Purpose: review gate for file headers and "// PORT:" citations (PORTING.md 2.1, 2.7).
"""Check Java file headers and // PORT: markers.

Mirrors SourceRules (a) and (c) in kusto-language-conformance:

(a) every .java file starts with one of the two PORTING.md 2.1 header forms. Ported form: one or
    more "// Ported from: src/<path>.cs" lines (".tt", optionally followed by " (<note>)", for
    generated files), "// Upstream: microsoft/Kusto-Query-Language @ <sha>" with sha equal to
    porting/manifest.json upstreamCommit, the SPDX line, an optional "// Copyright (c) Microsoft
    Corporation..." line, then either the upstream-license and derived-work lines or the
    generated-file license line. Original form: the two "Original to kusto-java" lines.
(c) every "// PORT:" line comment cites a rule (section sign + number, e.g. 3.6) or a D-row (D12).

Usage: python3 porting/tools/check_headers.py [ROOT ...]
Default roots: kusto-language/src/main/java and kusto-language-generator/src/main/java.
Exit status 1 when any violation is found. No third-party dependencies.
"""
import json
import os
import re
import sys

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
DEFAULT_ROOTS = ["kusto-language/src/main/java", "kusto-language-generator/src/main/java"]

ORIGINAL_1 = "// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0"
ORIGINAL_2 = re.compile(r"// Copyright \(c\) \d{4} Graylog, Inc\. Purpose: \S.*\Z")
PORTED_FROM = re.compile(r"// Ported from: src/\S+\.(cs|tt)( \(.+\))?\Z")
UPSTREAM = re.compile(r"// Upstream: microsoft/Kusto-Query-Language @ ([0-9a-f]{40})\Z")
SPDX = "// SPDX-License-Identifier: Apache-2.0"
MS_COPYRIGHT = re.compile(r"// Copyright \(c\) Microsoft Corporation\..*\Z")
UPSTREAM_LICENSE = "// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation."
DERIVED = '// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".'
GENERATED_LICENSE = re.compile(r"// Upstream license: Apache-2\.0\. This file is GENERATED .*\Z")
PORT_MARKER = re.compile(r"//\s*PORT:")
PORT_CITATION = re.compile(r"§\d+(\.\d+)?|\bD\d+\b")


def upstream_commit():
    with open(os.path.join(REPO, "porting", "manifest.json"), encoding="utf-8") as f:
        return json.load(f)["upstreamCommit"]


def line_comments(src):
    """Yield (offset, text) of every // comment, skipping comments and string/char/text-block literals."""
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            end = src.find("\n", i)
            end = n if end < 0 else end
            text = src[i:end].rstrip("\r")
            yield i, text
            i = end
        elif src.startswith("/*", i):
            end = src.find("*/", i + 2)
            i = n if end < 0 else end + 2
        elif src.startswith('"""', i):
            j = i + 3
            while j < n and not src.startswith('"""', j):
                j += 2 if src[j] == "\\" else 1
            i = min(n, j + 3)
        elif c in "\"'":
            j = i + 1
            while j < n and src[j] != c and src[j] != "\n":
                j += 2 if src[j] == "\\" else 1
            i = j + 1
        else:
            i += 1


def check_header(lines, upstream):
    """Return a list of (line, message)."""
    if lines and lines[0] == ORIGINAL_1:
        if len(lines) < 2 or not ORIGINAL_2.match(lines[1]):
            return [(2, "expected '// Copyright (c) <year> Graylog, Inc. Purpose: <one line>'")]
        return []
    i = 0
    while i < len(lines) and PORTED_FROM.match(lines[i]):
        i += 1
    if i == 0:
        return [(1, "first line is neither the 'Original to kusto-java' line nor '// Ported from: src/<path>.cs'")]
    out = []
    m = UPSTREAM.match(lines[i]) if i < len(lines) else None
    if not m:
        return [(i + 1, "expected '// Upstream: microsoft/Kusto-Query-Language @ <40 hex>'")]
    if m.group(1) != upstream:
        out.append((i + 1, "upstream %s != manifest upstreamCommit %s" % (m.group(1), upstream)))
    i += 1
    if i >= len(lines) or lines[i] != SPDX:
        return out + [(i + 1, "expected '%s'" % SPDX)]
    i += 1
    if i < len(lines) and MS_COPYRIGHT.match(lines[i]):
        i += 1
    if i < len(lines) and GENERATED_LICENSE.match(lines[i]):
        return out
    if i >= len(lines) or lines[i] != UPSTREAM_LICENSE:
        return out + [(i + 1, "expected '%s'" % UPSTREAM_LICENSE)]
    i += 1
    if i >= len(lines) or lines[i] != DERIVED:
        out.append((i + 1, "expected '%s'" % DERIVED))
    return out


def check_file(path, rel, upstream):
    with open(path, encoding="utf-8") as f:
        src = f.read()
    lines = src.replace("\r\n", "\n").split("\n")
    out = ["%s:%d: [header] %s" % (rel, ln, msg) for ln, msg in check_header(lines, upstream)]
    for off, text in line_comments(src):
        if PORT_MARKER.match(text) and not PORT_CITATION.search(text):
            out.append("%s:%d: [marker] '// PORT:' cites neither a rule nor a D-row: %s"
                       % (rel, src.count("\n", 0, off) + 1, text.strip()))
    return out


def main(argv):
    roots = argv[1:] or DEFAULT_ROOTS
    upstream = upstream_commit()
    violations, files = [], 0
    for root in roots:
        base = root if os.path.isabs(root) else os.path.join(REPO, root)
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames.sort()
            for name in sorted(filenames):
                if name.endswith(".java"):
                    files += 1
                    path = os.path.join(dirpath, name)
                    violations += check_file(path, os.path.relpath(path, REPO), upstream)
    for v in violations:
        print(v)
    print("check_headers: %d files, %d violations" % (files, len(violations)), file=sys.stderr)
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
