#!/usr/bin/env python3
"""Extract `query` fields of Azure-Sentinel YAML rules into corpus/sentinel.jsonl.

Scope: Detections/, Hunting Queries/, Solutions/**/<Analytic Rules|Analytics Rules|Hunting Queries>/
(directory names matched case-insensitively). PyYAML safe_load; files that fail to load are
skipped and counted. Queries are kept verbatim (including {{...}} placeholders, counted).
Requires PyYAML (pinned in porting/corpora.json).
"""
import datetime, json, sys
from pathlib import Path
import yaml
sys.path.insert(0, str(Path(__file__).parent))
from corpuslib import ROOT, CORPUS, write_jsonl, update_provenance

REPO = ROOT / "build/corpora/Azure-Sentinel"
SOL_DIRS = {"analytic rules", "analytics rules", "hunting queries"}


def files():
    out = []
    for top in ("Detections", "Hunting Queries"):
        out += [p for p in (REPO / top).rglob("*") if p.suffix in (".yaml", ".yml")]
    for p in (REPO / "Solutions").rglob("*"):
        if p.suffix in (".yaml", ".yml") and any(
                part.lower() in SOL_DIRS for part in p.relative_to(REPO / "Solutions").parts[:-1]):
            out.append(p)
    return sorted(set(out), key=lambda p: p.relative_to(REPO).as_posix())


def main():
    cfg = json.loads((ROOT / "porting/corpora.json").read_text(encoding="utf-8"))
    pin = next(r for r in cfg["repos"] if r["name"] == "Azure-Sentinel")
    assert yaml.__version__ == cfg["pyyaml"], f"PyYAML {yaml.__version__} != pinned {cfg['pyyaml']}"
    seen, recs = set(), []
    st = dict(files=0, parseFailures=0, multiDocumentFailures=0, noQueryField=0, queryNotString=0,
              emptyQuery=0, duplicatesSkipped=0, placeholderQueries=0)
    failed = []
    for p in files():
        st["files"] += 1
        rel = p.relative_to(REPO).as_posix()
        try:
            d = yaml.safe_load(p.read_bytes().decode("utf-8-sig"))
        except Exception as e:
            st["parseFailures"] += 1
            if "single document" in str(e) or "expected a single" in str(e):
                st["multiDocumentFailures"] += 1
            failed.append(rel)
            continue
        if not isinstance(d, dict) or "query" not in d:
            st["noQueryField"] += 1
            continue
        q = d["query"]
        if not isinstance(q, str):
            st["queryNotString"] += 1
            continue
        if not q.strip():
            st["emptyQuery"] += 1
            continue
        if q in seen:
            st["duplicatesSkipped"] += 1
            continue
        seen.add(q)
        if "{{" in q:
            st["placeholderQueries"] += 1
        recs.append({"id": f"sentinel/{len(recs)+1:04d}", "text": q, "schema": "sentinel-v1",
                     "source": rel})
    write_jsonl(CORPUS / "sentinel.jsonl", recs)
    update_provenance("sentinel", {
        "repo": pin["repo"], "commit": pin["commit"], "license": pin["license"],
        "count": len(recs), **st, "pyyaml": yaml.__version__,
        "extractionScript": "porting/tools/extract_sentinel_corpus.py",
        "extractedAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")})
    shutil_copy = CORPUS / "LICENSE-Azure-Sentinel.txt"
    shutil_copy.write_bytes((REPO / "LICENSE").read_bytes())
    print(f"sentinel: {len(recs)} records; {st}")
    if failed:
        print("failed:", *failed[:10], sep="\n  ")


if __name__ == "__main__":
    main()
