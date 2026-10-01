#!/usr/bin/env python3
"""Convert `.show database Samples schema as json` output into schemas/samples-v1.json.

Usage: convert_samples_schema.py INPUT.json [OUTPUT.json]
INPUT may be (a) the schema JSON object itself ({"Databases": {...}}), (b) a JSON string
holding it, or (c) a Kusto v1 REST result ({"Tables":[{"Rows":[[<schema json string>]]}]}).

>>> sample = {"Databases": {"Samples": {"Name": "Samples",
...   "Tables": {"T": {"Name": "T", "DocString": "a table", "OrderedColumns": [
...       {"Name": "a", "Type": "System.Int64", "CslType": "long"},
...       {"Name": "b", "Type": "System.DateTime", "CslType": "datetime"}]}},
...   "ExternalTables": {}, "MaterializedViews": {},
...   "Functions": {"F": {"Name": "F", "Body": "{ T | take n }",
...       "InputParameters": [{"Name": "n", "Type": "System.Int64", "CslType": "long"}]},
...                 "G": {"Name": "G", "Body": "{ T }", "InputParameters": []}}}}}
>>> out = convert(sample)
>>> out["tables"]
[{'name': 'T', 'columns': [{'name': 'a', 'type': 'long'}, {'name': 'b', 'type': 'datetime'}], 'docstring': 'a table'}]
>>> out["functions"]
[{'name': 'F', 'parameters': '(n: long)', 'body': '{ T | take n }'}, {'name': 'G', 'parameters': '()', 'body': '{ T }'}]
>>> out["id"], out["cluster"], out["database"]
('samples-v1', 'help', 'Samples')
"""
import json, sys
from pathlib import Path

DEFAULT_OUT = (Path(__file__).resolve().parents[2]
               / "kusto-language-conformance/src/test/resources/schemas/samples-v1.json")
NET = {"System.String": "string", "System.Int32": "int", "System.Int64": "long", "System.Double": "real",
       "System.DateTime": "datetime", "System.TimeSpan": "timespan", "System.Boolean": "bool",
       "System.Guid": "guid", "System.Data.SqlTypes.SqlDecimal": "decimal", "System.Object": "dynamic",
       "System.SByte": "bool"}


def _unwrap(d):
    if isinstance(d, str):
        d = json.loads(d)
    if isinstance(d, dict) and "Tables" in d and "Databases" not in d:
        d = json.loads(d["Tables"][0]["Rows"][0][0])
    return d


def _type(o):
    return o.get("CslType") or NET.get(o.get("Type"), "dynamic")


def convert(raw, database="Samples", cluster="help", schema_id="samples-v1"):
    db = _unwrap(raw)["Databases"][database]
    tables = [{"name": n, "columns": [{"name": c["Name"], "type": _type(c)}
                                      for c in t.get("OrderedColumns", [])],
               "docstring": t.get("DocString") or None} for n, t in db.get("Tables", {}).items()]
    funcs = [{"name": n, "parameters": "(" + ", ".join(f"{p['Name']}: {_type(p)}"
                                                      for p in f.get("InputParameters", [])) + ")",
              "body": f["Body"]} for n, f in db.get("Functions", {}).items()]
    return {"id": schema_id, "cluster": cluster, "database": database, "tables": tables,
            "functions": funcs, "externalTables": [], "materializedViews": [], "entityGroups": []}


def main(argv):
    if not 2 <= len(argv) <= 3:
        sys.exit(__doc__.split("\n\n")[0])
    raw = json.loads(Path(argv[1]).read_text(encoding="utf-8-sig"))
    out = Path(argv[2]) if len(argv) == 3 else DEFAULT_OUT
    out.write_text(json.dumps(convert(raw), ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print("wrote", out)


if __name__ == "__main__":
    main(sys.argv)
