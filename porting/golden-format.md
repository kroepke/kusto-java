# Golden record format v1

One spec, two implementations: `oracle/kusto-oracle` (C#) and
`kusto-language-conformance` (Java, `GoldenWriter`). Both must produce byte-identical JSON for
the same input, modulo the `timing` object which the comparator ignores. The JSON Schema is
`porting/golden-format.schema.json` (written in T0 from this document; this document wins).

## Files

- Corpus input: `kusto-language-conformance/src/test/resources/corpus/<name>.jsonl`, one
  object per line: `{"id": "<name>/<nnnn>", "text": "...", "schema": "<schema id>|null",
  "source": "<provenance path>"}`.
- Golden output: `kusto-language-conformance/src/test/resources/goldens/<name>.jsonl.gz`.
  Line 1 is a header object; then one record per corpus line, same order.
- Schema input: `kusto-language-conformance/src/test/resources/schemas/<schema id>.json`
  (section "Schema format").
- .NET reference facts: `kusto-language-conformance/src/test/resources/dotnet-facts.json`
  produced by `kusto-oracle dotnet-facts` from `porting/dotnet-facts-cases.txt`.

All committed. `mvn verify` never needs .NET.

## Header line

```json
{"golden": 1, "upstream": "9d95a2d5bb085d151f14e88e07b703755fd914e1",
 "oracle": {"sourcesSha256": "<hex>", "runtime": "net10.0", "configuration": "Release",
            "invariantGlobalization": true, "tz": "UTC", "serverKind": "Engine"},
 "generatedAt": "<ISO-8601>"}
```
`sourcesSha256` is the SHA-256 over the sorted list of `(path, blob)` of every compiled C# file,
including the generated ones and the oracle's own `oracle/kusto-oracle/src/*.cs`. Java compares `upstream` with `manifest.upstreamCommit` and
fails the suite on mismatch.

## Record

Serialisation rules: UTF-8, no pretty printing, keys in the order below, no trailing spaces,
integers as JSON numbers, strings escaped per RFC 8259 with `\uXXXX` for every code unit
below U+0020 and for lone surrogates. Positions are UTF-16 code-unit offsets into `text`.

```json
{"id": "readme/0001", "kind": "Query|Command|Directive",
 "tokens": [ {"kind": "IdentifierToken", "triviaStart": 0, "start": 0, "end": 1, "trivia": "", "text": "T",
              "value": null, "diagnostics": [] } ],
 "fidelity": {"roundTrip": true, "fullWidth": 12},
 "tree": [ {"i": 0, "kind": "QueryBlock", "depth": 0, "parent": -1, "name": "", "start": 0, "end": 12,
            "missing": false} ],
 "syntaxDiagnostics": [ {"code": "KS001", "severity": "Error", "start": 3, "length": 1, "message": "..."} ],
 "semanticDiagnostics": [ ... same shape ... ],
 "bind": [ {"i": 7, "symbolKind": "Column", "symbol": "a", "symbolOwner": "T", "type": "real",
            "signature": null, "isConstant": false, "constantValue": null, "calledBody": null,
            "alternates": 0} ],
 "resultType": "(a: real)",
 "outcome": {"parse": "ok", "analyze": "ok", "tokenValues": "ok"},
 "timing": {"parseMs": 1.2, "analyzeMs": 3.4}}
```

### tokens
From `KustoCode.GetLexicalTokens()` of the **analysed** code (so `AlwaysProduceEndTokens` is on):
`kind` = `SyntaxKind` member name; `triviaStart` = offset of the trivia; `start` = offset of
the text; `end` = `start + text.Length`; `value` = `DotNet.str(token.Value)` for literal
tokens (`SyntaxToken.Value` after `SyntaxToken.From`), else `null`; if computing the value
throws, `value` is `"!<ExceptionTypeName>"` (unmapped .NET name) and `outcome.tokenValues`
becomes `"throw:<Name>"` (mapped). The token `value` is set only for literal tokens
(`SyntaxToken.From(lexicalToken).Value` when `IsLiteral`), else `null`.
`diagnostics` = the token's own diagnostics (same shape as below, positions absolute).

### fidelity
`roundTrip` = `root.ToString(IncludeTrivia.All) == text`; `fullWidth` = `root.FullWidth`.
The oracle asserts both equal `text`/`text.Length` and records the result; a `false` is a
corpus-level failure before any Java work starts (T0 gate).

### tree
Pre-order over `SyntaxElement` (nodes **and tokens**, zero-width tokens included),
`i` = pre-order index, `parent` = index of the parent (-1 for root), `name` =
`parent.GetName(childIndex)` ("" for root), `start`/`end` = `TextStart`/`End`,
`missing` = `IsMissing`. Tokens repeat their kind here so tree and tokens cross-check.
Lists (`SyntaxList`, `SeparatedElement`) are nodes like any other.

### syntaxDiagnostics / semanticDiagnostics
`syntaxDiagnostics` = `KustoCode.GetSyntaxDiagnostics()`; `semanticDiagnostics` =
`code.GetDiagnostics()` minus the syntax ones, preserving order. `severity` is the string
constant from `DiagnosticSeverity`. Positions absolute (`Start`, `Length`). The W4 gate
compares `tokens`, `fidelity`, `tree`, `syntaxDiagnostics`, `outcome.parse`; the W6 gate adds
`semanticDiagnostics`, `bind`, `resultType`, `outcome.analyze`.

### bind
One entry per `SyntaxNode` (pre-order index `i`) where `ReferencedSymbol != null` or the
node is an `Expression` with `ResultType != null`. Fields:
- `symbolKind` = `ReferencedSymbol.Kind` name or `null`; `symbol` = `ReferencedSymbol.Name`;
  `symbolOwner` = the owning table/database/cluster name if `GlobalState.GetTable/GetDatabase`
  knows it, else `null`.
- `type` = `SchemaDisplay.GetText(expression.ResultType)` or `null`.
- `signature` = `SchemaDisplay.GetText` of `ReferencedSignature.Parameters` as a parameter
  list plus `->` and the declared return type text, or `null`.
- `isConstant`, `constantValue` (`DotNet.str`, `null` when not constant; `"!<ExceptionTypeName>"`
  when computing the constant value throws, as for `tokens[].value`).
- `calledBody` = for a function call with an expansion: SHA-256 (first 16 hex chars) of
  `GetCalledFunctionBody().ToString()`, else `null`.
- `alternates` = `Alternates?.Count ?? 0`.

### resultType
`SchemaDisplay.GetText(code.ResultType)` or `null`.

### outcome
Each of `parse`, `analyze`, `tokenValues` is `"ok"` or `"throw:<Name>"` where `<Name>` is the
.NET exception type name mapped through the fixed table in `porting/exception-map.json`
(`InvalidOperationException → IllegalStateException`, …). Java records its own exception
mapped the other way, so both sides print the .NET name. `analyze` is `"skipped:depth"` when
`KustoCode.HasSemantics` is false because of `MaxAnalysisDepth`.

### Command records
Records whose `kind` is `Command` are compared only on `tokens` and `fidelity`. The oracle is
built with upstream's full command grammar; Java has stubs. The report prints the count of
command records excluded from tree/diagnostics/bind comparison.

## Schema format

```json
{"id": "samples-v1", "cluster": "help", "database": "Samples",
 "tables": [{"name": "StormEvents", "columns": [{"name": "StartTime", "type": "datetime"}], "docstring": null}],
 "functions": [{"name": "MyFunc", "parameters": "(a: long)", "body": "{ T | take a }"}],
 "externalTables": [], "materializedViews": [], "entityGroups": []}
```
`type` strings are the KQL scalar type names accepted by `ScalarTypes.GetSymbol`. Both sides
build `GlobalState.Default.WithCluster(new ClusterSymbol(cluster, new DatabaseSymbol(database, …)))
.WithDatabase(database)` from it, in file order. `schema: null` means `GlobalState.Default`.

## Comparator and report

- Comparison is field-wise on the parsed JSON, so key order does not matter for failure
  detection, but writers still follow the order above so raw diffs are readable.
- Each failing record is tagged with a cause: the first differing path (e.g.
  `tree[14].kind`), the deviation id from `kusto-language-conformance/src/test/resources/
  known-differences.json` if one matches (keyed by record id + path pattern + D-row), else
  `unclassified`. Pass rates per layer exclude known differences and print their count.
- Known differences are reviewed like code: each entry has a `reason`, a `dRow` and an
  `expires` upstream commit after which it must be re-justified.

## Rendering decisions (T0)

The oracle's concrete choices for points this document leaves open (string escaping, token
diagnostic positions, `symbolOwner` lookup, `signature` text, empty results on throw, schema
member order) are recorded in `oracle/README.md` and are normative for `GoldenWriter`.
