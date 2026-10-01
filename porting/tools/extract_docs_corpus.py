#!/usr/bin/env python3
"""Extract ```kusto / ```kql fenced blocks from dataexplorer-docs into corpus/docs.jsonl.

Also writes corpus/PROVENANCE.json (docs entry) and copies the LICENSE files.
Handles 3+ backtick fences, indented fences, blockquote ('> ') fences, 'Kusto' casing and
'``` kusto'. Leading HTML comment lines (<!-- csl ... -->) inside a block are dropped; all
other text is verbatim (CRLF preserved; such records get "crlf": true).
"""
import datetime, json, re, shutil, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from corpuslib import ROOT, CORPUS, SCHEMAS, write_jsonl, update_provenance

REPO = ROOT / "build/corpora/dataexplorer-docs"
BASE = REPO / "data-explorer/kusto/query"
OPEN = re.compile(r'^((?:[ \t]*>)*[ \t]*)(`{3,})[ \t]*([\w-]*)[ \t]*\r?$')
HTMLC = re.compile(r'^[ \t]*<!--.*?-->[ \t]*\r?\n')
IDENT = re.compile(r'\s*([A-Za-z_][A-Za-z0-9_]*)')


def prefix_strip(line, prefix):
    """Remove blockquote markers / indentation of the opener from a content line."""
    if line.startswith(prefix):
        return line[len(prefix):]
    # Lines with less indentation: strip as much leading whitespace / '>' as the prefix has.
    n = len(prefix)
    i = 0
    while i < len(line) and i < n and line[i] in ' \t>':
        i += 1
    return line[i:]


def blocks(path):
    raw = path.read_bytes().decode("utf-8")
    if raw.startswith("﻿"):
        raw = raw[1:]
    lines = raw.split("\n")
    lines = [l + "\n" for l in lines[:-1]] + ([lines[-1]] if lines[-1] else [])
    i = 0
    while i < len(lines):
        m = OPEN.match(lines[i].rstrip("\n"))
        if not m:
            i += 1
            continue
        prefix, ticks, lang = m.groups()
        lang = lang.lower()
        j, body = i + 1, []
        closer = re.compile(r'^[ \t>]*`{%d,}[ \t]*\r?$' % len(ticks))
        while j < len(lines) and not closer.match(lines[j].rstrip("\n")):
            body.append(prefix_strip(lines[j], prefix))
            j += 1
        if lang in ("kusto", "kql"):
            yield i + 2, "".join(body)
        i = j + 1


def clean(text):
    while True:
        m = HTMLC.match(text)
        if not m:
            break
        text = text[m.end():]
    # drop the terminator of the final line only
    if text.endswith("\r\n"):
        text = text[:-2]
    elif text.endswith("\n"):
        text = text[:-1]
    return text


def main():
    samples = SCHEMAS / "samples-v1.json"
    stables = set()
    if samples.exists():
        stables = {t["name"] for t in json.loads(samples.read_text(encoding="utf-8"))["tables"]}
    cfg = json.loads((ROOT / "porting/corpora.json").read_text(encoding="utf-8"))
    pin = next(r for r in cfg["repos"] if r["name"] == "dataexplorer-docs")
    seen, recs, empty, dups, nfiles = set(), [], 0, 0, 0
    for p in sorted(BASE.rglob("*.md"), key=lambda x: x.as_posix()):
        nfiles += 1
        rel = p.relative_to(REPO).as_posix()
        for line, text in blocks(p):
            text = clean(text)
            if not text.strip():
                empty += 1
                continue
            if text in seen:
                dups += 1
                continue
            seen.add(text)
            m = IDENT.match(text)
            schema = "samples-v1" if (m and m.group(1) in stables) else None
            r = {"id": f"docs/{len(recs)+1:04d}", "text": text, "schema": schema,
                 "source": f"{rel}:{line}"}
            if "\r" in text:
                r["crlf"] = True
            recs.append(r)
    write_jsonl(CORPUS / "docs.jsonl", recs)
    shutil.copyfile(REPO / "LICENSE", CORPUS / "LICENSE-dataexplorer-docs.txt")
    for extra in ("LICENSE-CODE",):
        if (REPO / extra).exists():
            shutil.copyfile(REPO / extra, CORPUS / "LICENSE-CODE-dataexplorer-docs.txt")
    update_provenance("docs", {
        "repo": pin["repo"], "commit": pin["commit"], "license": pin["license"],
        "licenseNote": "Dual-licensed: CC-BY-4.0 for documentation text (LICENSE) and MIT for code "
                       "(LICENSE-CODE). Files copied as found: LICENSE-dataexplorer-docs.txt (LICENSE) "
                       "and LICENSE-CODE-dataexplorer-docs.txt (LICENSE-CODE).",
        "count": len(recs), "markdownFilesScanned": nfiles, "emptyBlocksSkipped": empty,
        "duplicatesSkipped": dups, "extractionScript": "porting/tools/extract_docs_corpus.py",
        "extractedAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")})
    print(f"docs: {len(recs)} records from {nfiles} files; empty={empty} dups={dups}")


if __name__ == "__main__":
    main()
