#!/usr/bin/env python3
# Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2026 Graylog, Inc. Purpose: rewrite Upstream header commits and status.json syncedAt during an upstream bump (PORTING.md 11 step 5).
"""Sync Java file headers and porting/status.json to a new upstream commit.

Usage: python3 porting/tools/sync_headers.py --to <sha> [--dry-run] PATH...

PATH is a Java file (repo-relative or absolute) or an upstream .cs/.tt path
(src/Kusto.Language/...), which is mapped to its javaPaths through porting/manifest.json.
For every Java file the line `// Upstream: microsoft/Kusto-Query-Language @ <sha>` is rewritten
to the new sha. For every manifest entry owning the file, `syncedAt` is set in porting/status.json,
keyed by upstreamPath (javaPath for synthetic entries). A missing key is created holding only
syncedAt; existing keys, fields and order are preserved, and the file keeps its format
(json indent 1, UTF-8, no trailing newline). --to may be abbreviated; it is resolved to 40 hex
via the upstream submodule. --dry-run prints the changes and writes nothing.

BUMP INTERACTION: manifest.json upstreamCommit is ONE global value and check_headers.py compares
every header against it. sync_headers.py is therefore run per file during a bump (PORTING.md 11
step 5), as each file is patched; headers of untouched files keep the old sha until the end.
The global value is refreshed by `python3 porting/tools/build_manifest.py` afterwards (step 8),
which is also when check_headers.py is expected to pass again. Files that needed no patch must
still be passed here so that every header reaches the new sha.
"""
import argparse
import json
import os
import re
import subprocess
import sys

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SUB = os.path.join(REPO, "upstream", "kusto-query-language")
UPSTREAM_LINE = re.compile(r"^(// Upstream: microsoft/Kusto-Query-Language @ )([0-9a-f]{40})(\r?)$")


def resolve_sha(rev):
    r = subprocess.run(["git", "-C", SUB, "rev-parse", "--verify", rev + "^{commit}"], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("cannot resolve upstream commit %r: %s" % (rev, r.stderr.strip()))
    return r.stdout.strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--to", required=True, metavar="SHA")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("paths", nargs="+", metavar="PATH")
    args = ap.parse_args()
    sha = resolve_sha(args.to)

    manifest = json.load(open(os.path.join(REPO, "porting", "manifest.json"), encoding="utf-8"))
    by_up = {e["upstreamPath"]: e for e in manifest["entries"] if e.get("upstreamPath")}
    by_java = {}
    for e in manifest["entries"]:
        for jp in e["javaPaths"]:
            by_java.setdefault(jp, []).append(e)
    sp = os.path.join(REPO, "porting", "status.json")
    raw = open(sp, encoding="utf-8").read()
    status = json.loads(raw)
    was_sorted = list(status) == sorted(status)

    java_files, errors = [], []
    for p in args.paths:
        rel = os.path.relpath(os.path.abspath(p), REPO).replace(os.sep, "/") if os.path.exists(p) else p
        if rel in by_up:
            if not by_up[rel]["javaPaths"]:
                errors.append("%s: manifest entry has no javaPaths" % rel)
            java_files.extend(by_up[rel]["javaPaths"])
        elif rel in by_java:
            java_files.append(rel)
        else:
            errors.append("%s: not an upstream path or javaPath in manifest" % p)
    java_files = list(dict.fromkeys(java_files))

    by_key = {e["upstreamPath"] or e["javaPaths"][0]: e for e in manifest["entries"]}
    keys = []
    for jp in java_files:
        full = os.path.join(REPO, jp)
        if not os.path.exists(full):
            errors.append("%s: Java file does not exist" % jp)
            continue
        with open(full, encoding="utf-8", newline="") as f:
            text = f.read()
        lines = text.split("\n")
        hits = [i for i, l in enumerate(lines[:30]) if UPSTREAM_LINE.match(l)]
        if hits:
            m = UPSTREAM_LINE.match(lines[hits[0]])
            if m.group(2) == sha:
                print("%s: header already at %s" % (jp, sha[:8]))
            else:
                print("%s: header %s -> %s" % (jp, m.group(2)[:8], sha[:8]))
                lines[hits[0]] = m.group(1) + sha + m.group(3)
                if not args.dry_run:
                    with open(full, "w", encoding="utf-8", newline="") as f:
                        f.write("\n".join(lines))
        else:
            print("%s: no Upstream header line (original/synthetic file?), header untouched" % jp)
        for e in by_java[jp]:
            k = e["upstreamPath"] or jp
            if k not in keys:
                keys.append(k)
    for k in keys:
        old = status.get(k, {}).get("syncedAt") or by_key[k].get("syncedAt")
        print("status.json[%s].syncedAt: %s -> %s" % (k, (old or "(unset)")[:8], sha[:8]))
        status.setdefault(k, {})["syncedAt"] = sha
    if was_sorted:
        status = dict(sorted(status.items()))
    if keys and not args.dry_run:
        with open(sp, "w", encoding="utf-8", newline="") as f:
            f.write(json.dumps(status, indent=1, ensure_ascii=False))
    for e in errors:
        print("error: " + e, file=sys.stderr)
    if args.dry_run:
        print("(dry run: nothing written)")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
