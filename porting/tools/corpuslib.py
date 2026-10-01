"""Shared helpers for corpus extractors."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORPUS = ROOT / "kusto-language-conformance/src/test/resources/corpus"
SCHEMAS = ROOT / "kusto-language-conformance/src/test/resources/schemas"


def write_jsonl(path, records):
    """records: dicts with id,text,schema,source (+ optional extras after)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        for r in records:
            o = {"id": r["id"], "text": r["text"], "schema": r["schema"], "source": r["source"]}
            for k, v in r.items():
                if k not in o:
                    o[k] = v
            fh.write(json.dumps(o, ensure_ascii=False) + "\n")


def update_provenance(corpus, entry):
    p = CORPUS / "PROVENANCE.json"
    d = json.loads(p.read_text(encoding="utf-8")) if p.exists() else {}
    d[corpus] = entry
    p.write_text(json.dumps(dict(sorted(d.items())), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
