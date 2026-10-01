#!/usr/bin/env python3
"""Convert Azure-Sentinel CustomTables/*.json into schemas/sentinel-v1.json.

Input files are inconsistent: keys may be Name/name/Properties/properties, property keys
Name/name and Type/type, some types are missing or misspelt, some tables repeat columns,
some files are not valid JSON. Policy (all recorded in output "notes"):
- table name = Name/name, else file stem; first table of a given name wins (files sorted);
- duplicate column names within a table: first wins;
- type compared case-insensitively after stripping whitespace/commas; aliases below;
- unknown or missing type -> dynamic;
- unparseable files are skipped.
"""
import json, sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "build/corpora/Azure-Sentinel/.script/tests/KqlvalidationsTests/CustomTables"
OUT = ROOT / "kusto-language-conformance/src/test/resources/schemas/sentinel-v1.json"

TYPES = {
    "string": "string", "int": "int", "int32": "int", "integer": "int", "sbyte": "int",
    "long": "long", "int64": "long", "bigint": "long",
    "real": "real", "double": "real",
    "datetime": "datetime", "date/time": "datetime", "timestamp": "datetime", "timetamp": "datetime",
    "timespan": "timespan", "bool": "bool", "boolean": "bool",
    "dynamic": "dynamic", "object": "dynamic", "guid": "guid", "decimal": "decimal",
}


def ci(d, *names):
    for k, v in d.items():
        if k.lower() in names:
            return v
    return None


def main():
    tables, seen = [], set()
    unknown, aliased = Counter(), Counter()
    skipped, dup_tables, dup_cols, missing_type = [], [], 0, 0
    for f in sorted(SRC.glob("*.json"), key=lambda p: p.name):
        try:
            d = json.loads(f.read_text(encoding="utf-8-sig"))
            assert isinstance(d, dict)
        except Exception:
            skipped.append(f.name)
            continue
        name = ci(d, "name") or f.stem
        props = ci(d, "properties") or []
        if name in seen:
            dup_tables.append(f.name)
            continue
        seen.add(name)
        cols, names = [], set()
        for p in props:
            cn = ci(p, "name")
            if not isinstance(cn, str) or not cn:
                continue
            if cn in names:
                dup_cols += 1
                continue
            names.add(cn)
            raw = ci(p, "type")
            if not isinstance(raw, str):
                t = "dynamic"; missing_type += 1
            else:
                key = raw.strip().strip(",").strip().lower()
                if key in TYPES:
                    t = TYPES[key]
                else:
                    t = "dynamic"; unknown[raw] += 1
            cols.append({"name": cn, "type": t})
        tables.append({"name": name, "columns": cols, "docstring": None})
    notes = {
        "source": "Azure/Azure-Sentinel .script/tests/KqlvalidationsTests/CustomTables (MIT)",
        "unknownTypesMappedToDynamic": dict(sorted(unknown.items())),
        "missingTypeMappedToDynamic": missing_type,
        "duplicateColumnsDropped": dup_cols,
        "duplicateTableFilesSkipped": dup_tables,
        "unparseableFilesSkipped": skipped,
    }
    out = {"id": "sentinel-v1", "cluster": "sentinel", "database": "Sentinel", "tables": tables,
           "functions": [], "externalTables": [], "materializedViews": [], "entityGroups": [],
           "notes": notes}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    print(f"tables={len(tables)} skipped={len(skipped)} dupTables={len(dup_tables)} "
          f"dupCols={dup_cols} missingType={missing_type} unknown={dict(unknown)}")


if __name__ == "__main__":
    main()
