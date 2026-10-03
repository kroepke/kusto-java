# Upstream diff tooling (T1)

`upstream_diff.py <old> <new> [--json PATH] [--md PATH]` classifies every file changed between
two commits of `upstream/kusto-query-language` against `porting/manifest.json`. Markdown goes to
stdout unless `--md` is given. Run from the repo root; stdlib only. Spec: PLAN.md 7, bump
procedure: PORTING.md 11.

Exit codes: `0` nothing to triage; `2` at least one `triage` row.

| Upstream change | Manifest status | Class |
|---|---|---|
| A / M | pending, ported, partial | patch |
| A / M | generated, generated+handwritten | regenerate |
| A / M | buildTime scope, ported (generator sources) | regenerate — patch the Java mirror first (PORTING.md §11 step 4) |
| A / M | stubbed | re-check stub |
| A / M | excluded | re-check exclusion |
| A | not in manifest | triage (names a covering scope.json rule if any) |
| D | any | remove (Java paths listed when the manifest still has the entry) |
| R / C | - | D of old path plus A of new path |

Catalog files (`Functions.cs`, `Functions.Convert.cs`, `Aggregates.cs`, `Operators.cs`,
`PlugIns.cs`) also report changed `data` lines (inside `new FunctionSymbol(` and sibling blocks)
versus `logic` lines, and `dataOnly`. Every row reports `bridgeTouched` when a change falls inside
an `#if BRIDGE` region. A UTF-8 BOM is stripped before comparing.

Caveats: the manifest describes the current pin. Classifying an older or reversed range shows
files by their current status (e.g. a file added in the range is `patch` if the manifest has it),
and deleted files usually have no manifest entry. `triage` therefore appears mainly for ranges
ending at a pin newer than the manifest.

`dry-runs/` holds sample reports. Header and `syncedAt` rewriting is `porting/tools/sync_headers.py`.
