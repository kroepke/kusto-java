#!/usr/bin/env python3
# Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2026 Graylog, Inc. Purpose: classify an upstream commit range against porting/manifest.json (PLAN.md 7, PORTING.md 11).
"""Classify upstream changes between two commits of the Kusto-Query-Language submodule.

Usage: python3 porting/upstream-diff/upstream_diff.py <old> <new> [--json PATH] [--md PATH]

Runs `git diff --name-status <old> <new>` in upstream/kusto-query-language, maps each path
through porting/manifest.json (by upstreamPath) and classifies it:

  pending/ported/partial        -> patch
  generated/generated+handwritten -> regenerate
  buildTime scope, ported       -> regenerate (generator mirror patched by hand first)
  stubbed                       -> re-check stub
  excluded                      -> re-check exclusion
  not in manifest, status A     -> triage (scope.json rule noted when one already covers it)
  status D                      -> remove (affected Java paths listed)
  renames                       -> D(old path) + A(new path)

Catalog files (Functions.cs, Functions.Convert.cs, Aggregates.cs, Operators.cs, PlugIns.cs) get a
data/logic split of their changed lines: "data" lines belong to `new FunctionSymbol(` /
`new Parameter(` / `new OperatorSymbol(` / `new AggregateSymbol(` / `new PlugIn...(` blocks
(tracked by parenthesis depth, plus chained `.Call()` lines directly after a block); everything
else is "logic". `dataOnly` is true when no logic line changed.

A leading UTF-8 BOM is stripped from both sides; BOM-only changes are not changes. `bridgeTouched`
is true when a changed line lies inside an `#if BRIDGE` region (old or new text).

Exit code: 2 if any path is classified `triage`, else 0. Output is sorted by path. stdlib only.
"""
import argparse
import difflib
import fnmatch
import json
import os
import re
import subprocess
import sys

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SUB = os.path.join(REPO, "upstream", "kusto-query-language")
CATALOG_BASENAMES = {"Functions.cs", "Functions.Convert.cs", "Aggregates.cs", "Operators.cs", "PlugIns.cs"}
DATA_START = re.compile(r"^(?:public\s+static\s+(?:readonly\s+)?\S+\s+\w+\s*=\s*)?new\s+(FunctionSymbol|Parameter|OperatorSymbol|AggregateSymbol|PlugIn\w*)\s*\(")
STATUS_CLASS = {
    "pending": "patch", "ported": "patch", "partial": "patch",
    "generated": "regenerate", "generated+handwritten": "regenerate",
    "stubbed": "re-check stub", "excluded": "re-check exclusion",
}


def git(*args):
    r = subprocess.run(["git", "-C", SUB] + list(args), capture_output=True)
    if r.returncode != 0:
        sys.exit("git %s failed: %s" % (" ".join(args), r.stderr.decode("utf-8", "replace")))
    return r.stdout


def show(rev, path):
    r = subprocess.run(["git", "-C", SUB, "show", "%s:%s" % (rev, path)], capture_output=True)
    if r.returncode != 0:
        return None
    t = r.stdout.decode("utf-8", "replace")
    return t[1:] if t.startswith("﻿") else t


def name_status(old, new):
    out = git("diff", "--name-status", "--no-renames", old, new).decode("utf-8", "replace")
    rows = {}
    for ln in out.splitlines():
        if not ln.strip():
            continue
        parts = ln.split("\t")
        st = parts[0][0]
        if st in "RC":  # defensive: treat as D+A
            rows[parts[1]] = "D"
            rows[parts[2]] = "A"
        else:
            rows[parts[1]] = st
    return rows


def scope_rule(scope, path):
    for kind, items in scope.get("rules", {}).items():
        for it in items:
            if isinstance(it, dict):  # stubs: {upstreamPath, ...}
                it = it.get("upstreamPath") or ""
            if not it:
                continue
            if path == it or (it.endswith("/") and path.startswith(it)) or fnmatch.fnmatch(path, it):
                return "%s: %s" % (kind, it)
    return None


def strip_code(line, in_block):
    """Remove comments and string/char literals from one line; returns (code, in_block_comment)."""
    out, i, n = [], 0, len(line)
    while i < n:
        if in_block:
            j = line.find("*/", i)
            if j < 0:
                return "".join(out), True
            i, in_block = j + 2, False
        elif line.startswith("//", i):
            break
        elif line.startswith("/*", i):
            in_block = True
            i += 2
        elif line[i] == '"' or (line.startswith('@"', i)) or (line.startswith('$"', i)):
            verbatim = line[i] == "@" or line.startswith('$@"', i)
            i += 1 if line[i] == '"' else 2
            while i < n:
                if line[i] == "\\" and not verbatim:
                    i += 2
                    continue
                if line[i] == '"':
                    if verbatim and i + 1 < n and line[i + 1] == '"':
                        i += 2
                        continue
                    break
                i += 1
            i += 1
        elif line[i] == "'":
            j = i + 1
            while j < n and line[j] != "'":
                j += 2 if line[j] == "\\" else 1
            i = j + 1
        else:
            out.append(line[i])
            i += 1
    return "".join(out), in_block


def data_marks(lines):
    """Per-line bool: line is part of a catalog data block."""
    marks, depth, in_block, prev_data = [], 0, False, False
    for idx, raw in enumerate(lines):
        code, in_block = strip_code(raw, in_block)
        s = code.strip()
        is_data = False
        nxt = lines[idx + 1].strip() if idx + 1 < len(lines) else ""
        if depth == 0 and s.endswith("=") and DATA_START.match(nxt):
            is_data = True  # "public static readonly X Y =" heading a data block
        elif depth > 0:
            is_data = True
        elif DATA_START.match(s):
            is_data = True
        elif prev_data and s.startswith("."):
            is_data = True
        if is_data:
            depth = max(0, depth + code.count("(") - code.count(")"))
        marks.append(is_data)
        if s:
            prev_data = is_data
    return marks


def bridge_marks(lines):
    """Per-line bool: line inside an `#if BRIDGE` region (stops at #else/#elif/#endif)."""
    marks, stack = [], []  # stack of bool: is this level a BRIDGE-positive branch
    for raw in lines:
        s = raw.strip()
        if s.startswith("#if"):
            stack.append(bool(re.search(r"(?<![!\w])BRIDGE\b", s)))
        elif s.startswith("#elif") or s.startswith("#else"):
            if stack:
                stack[-1] = False
        elif s.startswith("#endif"):
            if stack:
                stack.pop()
        marks.append(any(stack))
    return marks


def analyze(path, old, new, status, catalog):
    a = (show(old, path) or "").split("\n") if status != "A" else []
    b = (show(new, path) or "").split("\n") if status != "D" else []
    ab, bb = bridge_marks(a), bridge_marks(b)
    res = {"added": 0, "removed": 0, "bridgeTouched": False}
    if catalog:
        am, bm = data_marks(a), data_marks(b)
        res.update({"dataLines": 0, "logicLines": 0})
    sm = difflib.SequenceMatcher(None, a, b, autojunk=False)
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            continue
        for i in range(i1, i2):
            res["removed"] += 1
            if catalog and not a[i].strip():
                res["removed"] -= 1
                continue
            res["bridgeTouched"] |= ab[i]
            if catalog:
                res["dataLines" if am[i] else "logicLines"] += 1
        for j in range(j1, j2):
            res["added"] += 1
            if catalog and not b[j].strip():
                res["added"] -= 1
                continue
            res["bridgeTouched"] |= bb[j]
            if catalog:
                res["dataLines" if bm[j] else "logicLines"] += 1
        # a pure insertion/deletion adjacent to a bridge region boundary is still caught by line marks
    if catalog:
        res["dataOnly"] = res["logicLines"] == 0 and (res["dataLines"] > 0)
    res["changed"] = res["added"] + res["removed"] > 0
    return res


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("old")
    ap.add_argument("new")
    ap.add_argument("--json")
    ap.add_argument("--md")
    args = ap.parse_args()

    manifest = json.load(open(os.path.join(REPO, "porting", "manifest.json"), encoding="utf-8"))
    scope = json.load(open(os.path.join(REPO, "porting", "scope.json"), encoding="utf-8"))
    by_path = {e["upstreamPath"]: e for e in manifest["entries"] if e.get("upstreamPath")}
    old = git("rev-parse", args.old).decode().strip()
    new = git("rev-parse", args.new).decode().strip()

    rows = []
    for path, st in sorted(name_status(old, new).items()):
        e = by_path.get(path)
        row = {"path": path, "git": st, "manifestStatus": e["status"] if e else None,
               "wave": e.get("wave") if e else None, "javaPaths": list(e["javaPaths"]) if e else []}
        if st == "D":
            row["class"] = "remove"
            row["note"] = ("manifest status " + e["status"]) if e else "deleted upstream; no manifest entry at current pin"
        elif e is None:
            row["class"] = "triage"
            rule = scope_rule(scope, path)
            row["note"] = ("covered by scope rule " + rule) if rule else "no scope rule"
        elif e.get("scope") == "buildTime" and e["status"] == "ported":
            row["class"] = "regenerate"
            row["note"] = "generator source: patch the Java mirror, then regenerate (PORTING.md 11 step 4)"
        else:
            row["class"] = STATUS_CLASS.get(e["status"], "triage")
            if e["status"] not in STATUS_CLASS:
                row["note"] = "unknown manifest status " + e["status"]
        catalog = os.path.basename(path) in CATALOG_BASENAMES
        if st in "AMD":
            d = analyze(path, old, new, st, catalog)
            row["lines"] = {k: d[k] for k in ("added", "removed")}
            row["bridgeTouched"] = d["bridgeTouched"]
            if not d["changed"] and st == "M":
                row["note"] = (row.get("note", "") + " BOM/whitespace-only change").strip()
            if catalog:
                row["catalog"] = {"dataLines": d["dataLines"], "logicLines": d["logicLines"], "dataOnly": d["dataOnly"]}
        rows.append(row)

    summary = {}
    for r in rows:
        summary[r["class"]] = summary.get(r["class"], 0) + 1
    report = {"old": old, "new": new, "summary": dict(sorted(summary.items())), "files": rows}

    md = ["# Upstream diff %s..%s" % (old[:8], new[:8]), "",
          "Summary: " + (", ".join("%s %d" % kv for kv in sorted(summary.items())) or "no changes"), "",
          "| Path | Git | Manifest | Class | +/- | Bridge | Catalog data/logic | Notes |",
          "|---|---|---|---|---|---|---|---|"]
    for r in rows:
        ln = r.get("lines", {})
        c = r.get("catalog")
        cat = "%d/%d%s" % (c["dataLines"], c["logicLines"], " dataOnly" if c["dataOnly"] else "") if c else ""
        notes = r.get("note", "")
        if r["class"] == "remove" and r["javaPaths"]:
            notes = (notes + "; " if notes else "") + "Java: " + ", ".join(os.path.basename(p) for p in r["javaPaths"])
        md.append("| %s | %s | %s | %s | +%s/-%s | %s | %s | %s |" % (
            r["path"], r["git"], r["manifestStatus"] or "-", r["class"], ln.get("added", 0), ln.get("removed", 0),
            "yes" if r.get("bridgeTouched") else "", cat, notes))
    text = "\n".join(md) + "\n"
    if args.md:
        os.makedirs(os.path.dirname(os.path.abspath(args.md)), exist_ok=True)
        open(args.md, "w", encoding="utf-8").write(text)
    else:
        sys.stdout.write(text)
    if args.json:
        os.makedirs(os.path.dirname(os.path.abspath(args.json)), exist_ok=True)
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=1, ensure_ascii=False)
            f.write("\n")
    sys.exit(2 if summary.get("triage") else 0)


if __name__ == "__main__":
    main()
