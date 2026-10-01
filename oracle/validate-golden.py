#!/usr/bin/env python3
"""Validates golden .jsonl.gz files against porting/golden-format.schema.json and the spec's key order.
Usage: validate-golden.py <golden.jsonl.gz>...   (needs the `jsonschema` package)"""
import gzip, json, os, sys
import jsonschema

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCHEMA = json.load(open(os.path.join(ROOT, "porting", "golden-format.schema.json")))
ORDER = {
    "header": ["golden", "upstream", "oracle", "generatedAt"],
    "oracle": ["sourcesSha256", "runtime", "configuration", "invariantGlobalization", "tz", "serverKind"],
    "record": ["id", "kind", "tokens", "fidelity", "tree", "syntaxDiagnostics", "semanticDiagnostics", "bind", "resultType", "outcome", "timing"],
    "token": ["kind", "triviaStart", "start", "end", "trivia", "text", "value", "diagnostics"],
    "diagnostic": ["code", "severity", "start", "length", "message"],
    "tree": ["i", "kind", "depth", "parent", "name", "start", "end", "missing"],
    "bind": ["i", "symbolKind", "symbol", "symbolOwner", "type", "signature", "isConstant", "constantValue", "calledBody", "alternates"],
    "fidelity": ["roundTrip", "fullWidth"],
    "outcome": ["parse", "analyze", "tokenValues"],
    "timing": ["parseMs", "analyzeMs"],
}

def order(obj, kind, where):
    if list(obj.keys()) != ORDER[kind]:
        raise ValueError(f"{where}: key order {list(obj.keys())} != {ORDER[kind]}")

def check_record(r, where):
    order(r, "record", where)
    for k in ("fidelity", "outcome", "timing"):
        order(r[k], k, f"{where}.{k}")
    for i, t in enumerate(r["tokens"]):
        order(t, "token", f"{where}.tokens[{i}]")
        for d in t["diagnostics"]:
            order(d, "diagnostic", f"{where}.tokens[{i}].diagnostics")
    for i, t in enumerate(r["tree"]):
        order(t, "tree", f"{where}.tree[{i}]")
        if t["i"] != i:
            raise ValueError(f"{where}.tree[{i}].i = {t['i']}")
    for d in r["syntaxDiagnostics"] + r["semanticDiagnostics"]:
        order(d, "diagnostic", f"{where}.diagnostics")
    for b in r["bind"]:
        order(b, "bind", f"{where}.bind")

def main(paths):
    validator = jsonschema.Draft202012Validator(SCHEMA)
    # Same schema, rooted at one branch, for readable error messages.
    header_v = jsonschema.Draft202012Validator({**SCHEMA, "oneOf": [{"$ref": "#/$defs/header"}]})
    record_v = jsonschema.Draft202012Validator({**SCHEMA, "oneOf": [{"$ref": "#/$defs/record"}]})
    total = 0
    for path in paths:
        with gzip.open(path, "rt", encoding="utf-8") as f:
            for n, line in enumerate(f, 1):
                obj = json.loads(line)
                where = f"{path}:{n}"
                if not validator.is_valid(obj):
                    detail = header_v if "golden" in obj else record_v
                    errors = sorted(detail.iter_errors(obj), key=lambda e: list(e.absolute_path))
                    e = errors[0].context[0] if errors and errors[0].context else (errors[0] if errors else None)
                    msg = f"{e.message[:300]} at {list(e.absolute_path)}" if e else "invalid"
                    raise ValueError(f"{where}: {msg}")
                if n == 1:
                    if "golden" not in obj:
                        raise ValueError(f"{where}: first line is not a header")
                    order(obj, "header", where)
                    order(obj["oracle"], "oracle", where + ".oracle")
                else:
                    if "golden" in obj:
                        raise ValueError(f"{where}: header after line 1")
                    check_record(obj, where)
                    total += 1
    print(f"validate-golden: OK ({total} records)")

if __name__ == "__main__":
    main(sys.argv[1:])
