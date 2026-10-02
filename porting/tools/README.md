# Corpus tools

Pins live in `porting/corpora.json` (repo commits, PyYAML version, readme path).
Outputs go to `kusto-language-conformance/src/test/resources/{corpus,schemas}/`.
Checkouts go to gitignored `build/corpora/`.

## Setup

```
python3 -m venv build/venv        # any location works
build/venv/bin/pip install PyYAML==6.0.3   # must match "pyyaml" in corpora.json
```

Only `extract_sentinel_corpus.py` needs PyYAML. It asserts the pinned version.

## Run order

```
python3 porting/tools/fetch_corpora.py            # sparse shallow checkouts (idempotent; Azure-Sentinel is ~5 GB)
python3 porting/tools/extract_readme_corpus.py    # corpus/readme.jsonl (needs schemas/readme-v1.json)
python3 porting/tools/extract_docs_corpus.py      # corpus/docs.jsonl, LICENSE copies, PROVENANCE.json
python3 porting/tools/convert_sentinel_schema.py  # schemas/sentinel-v1.json
build/venv/bin/python porting/tools/extract_sentinel_corpus.py   # corpus/sentinel.jsonl
python3 porting/tools/corpus_stats.py
```

`samples-v1.json` is a one-time human capture: see `samples_schema.md`.
Re-run `extract_docs_corpus.py` after adding it.

## Notes

- Output is deterministic (sorted paths) apart from `extractedAt` in `PROVENANCE.json`.
- To bump a pin, edit `commit` in `corpora.json`, re-run everything, review the diff.
- `readme-v1.json` is hand-transcribed; the readme extractor does not generate it.

## Generator tools (W0)

- `convert_syntax_node_infos.py`: mechanical C# → Java conversion of `SyntaxNodeInfos.cs` into
  `kusto-language-generator/.../SyntaxNodeInfos.java` (`--check` verifies it is up to date).
- `check_generated_structure.py`: diffs node/visitor structure between
  `porting/reference/GeneratedSyntaxNodes.cs` and the generated Java (W0 gate).
- `check_headers.py`, `check_pending.py`: review-gate scripts (headers/markers, PORT-PENDING list).
