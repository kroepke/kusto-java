# oracle — .NET reference runner (test tooling only)

The oracle compiles upstream `Kusto.Language` at the pinned submodule commit and records what it
does, so the Java port can be compared against it. Nothing here ships; `mvn verify` never needs
.NET. Normative output format: `porting/golden-format.md`. JSON Schema:
`porting/golden-format.schema.json`.

## Layout

| Path | What |
|---|---|
| `kusto-oracle-gen/` | Console app compiling the ten `Kusto.Language.Generators/*.cs` files directly. Writes the nine T4 outputs. |
| `generated/` | Its output (gitignored): `GeneratedSyntaxNodes.cs` and `{Engine,DataManager,ClusterManager,AriaBridge}{Commands,CommandGrammar}.cs`. |
| `check-generated.sh` | Byte comparison of `generated/GeneratedSyntaxNodes.cs` with `porting/reference/GeneratedSyntaxNodes.cs`. |
| `kusto-oracle/` | Console app: all of `upstream/.../Kusto.Language/**/*.cs` (Editor included; only `**/CodeGen/**`, `obj/`, `bin/` excluded) + `generated/*.cs` + `src/*.cs`. `KUSTO_BUILD` defined, `InvariantGlobalization`, Release. |
| `run.sh` | Driver. Exports `TZ=UTC`, `DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1`. Works from any cwd. |
| `validate-golden.py` | Validates golden files against the JSON Schema and the spec's key order (needs `jsonschema`). |
| `sample/` | 10-line sample corpus, `readme-v1` schema; `sample/out/` is scratch output (gitignored). |

Prerequisite: .NET 10 SDK (tested with 10.0.401, runtime 10.0.12).

## Generation: what worked

Compiling the generator sources as a plain C# project works. They are valid C# under `#if !T4`,
so no T4 engine (`dotnet-t4`) is needed. `kusto-oracle-gen` makes the same calls as the `.tt`
templates:

- `SyntaxNodeGenerator.Generate(SyntaxNodeInfos.All, SyntaxNodeInfos.KnownTypes)` → `GeneratedSyntaxNodes.cs`
- `new CommandGenerator().GenerateSymbols("<X>Commands", typeof(<X>CommandInfos))` → `<X>Commands.cs`
- `new CommandGenerator().GenerateParser("<X>CommandGrammar", typeof(<X>CommandInfos))` → `<X>CommandGrammar.cs`

Output is UTF-8 without BOM, `\n` line endings (the generator uses `Environment.NewLine`).
`check-generated.sh` strips a BOM, the leading `// ` lines (T4 include residue in the reference) and
trailing newlines from both sides, then compares bytes. Result: **identical (717,236 bytes)**.
Command infos are read by reflection (`Type.GetFields`). Checked: the `Command("<name>", …)` order in
all four `*CommandGrammar.cs` outputs equals the declaration order of the (uncommented)
`CommandInfo` fields in `*CommandInfos.cs` (Engine: 629 of 642; the other 13 are commented out).

## Commands

```
bash oracle/run.sh generate                    # gen + check-generated.sh
bash oracle/run.sh build                       # dotnet build -c Release (runs generate if needed)
bash oracle/run.sh dump <corpus.jsonl> <out.jsonl.gz> [schemasDir]
bash oracle/run.sh facts                       # -> kusto-language-conformance/src/test/resources/dotnet-facts.json
bash oracle/run.sh globals                     # -> kusto-language-conformance/src/test/resources/globals.jsonl.gz
bash oracle/run.sh probe                       # T0 questions, JSON on stdout
bash oracle/run.sh census <corpus.jsonl> [schemasDir]
bash oracle/run.sh regenerate                  # generate, build, facts, dump every corpus/*.jsonl to goldens/
bash oracle/run.sh sources-sha256              # the header hash, for inspection
```

`KUSTO_ORACLE_SERVER_KIND` (default `Engine`) is passed as `--server-kind` to `dump`/`census`;
it is applied with `GlobalState.WithServerKind` when it differs and recorded in the header.

The oracle binary itself: `dotnet oracle/kusto-oracle/bin/Release/net10.0/Kusto.Oracle.dll
{dump|facts|globals|probe|census} ...` with `--exception-map porting/exception-map.json` (found by
walking up from the cwd if omitted). All work runs on one thread with a 256 MB stack.

### dump

Gzip JSONL: header line, then one record per corpus line, same order. Per record the oracle runs
`KustoCode.Parse(text, globals)` and `KustoCode.ParseAndAnalyze(text, globals)` separately, each in
`try/catch (Exception)`. `tokens`, `tree`, `syntaxDiagnostics`, `fidelity` come from the analysed
code, or from the parse-only code when analysis threw. `semanticDiagnostics`, `bind`,
`resultType` are empty/null when analysis threw. Deterministic apart from `timing` and
`generatedAt` (checked by dumping `docs` twice).

### census

JSON: `records`, `commands`, `parseThrows`, `analyzeThrows`, `analyzeSkippedDepth`,
`tokenValueThrows`, `roundTripFalse`, per-exception counts, up to 20 example ids.

### facts

Reads `porting/dotnet-facts-cases.txt` and writes one JSON object: header fields
(`generatedAt`, `runtime`, `runtimeVersion`, `invariantGlobalization`, `tz`, `cases`) and
`facts`, one per line, each `{"api","input","ok","value", extras...}`. Unknown API names or
malformed lines fail the command (exit 1).

### globals

Gzip JSONL dump of the built-in catalogs of `GlobalState.Default`, for field-by-field comparison
with the Java port's catalogs. stdout: one `catalog<TAB>count` line per catalog. Compact JSON,
string escaping as for goldens. Deterministic apart from `generatedAt` (checked: two runs give
identical bytes after line 1).

Line 1 (header): `{"globals":1,"upstream":<submodule sha>,"oracle":{sourcesSha256, runtime,
configuration, invariantGlobalization, tz, serverKind},"generatedAt":...}` (same `oracle` object as
goldens; `serverKind` = `GlobalState.Default.ServerKind`).

Then one record per symbol, catalogs in this order, each in its list's enumeration order.
Every record starts with `catalog`, `index` (position in the list).

| `catalog` | Source |
|---|---|
| `functions` | `GlobalState.Default.Functions` (= `Functions.All`) |
| `aggregates` | `GlobalState.Default.Aggregates` |
| `plugins` | `GlobalState.Default.PlugIns` |
| `operators` | `GlobalState.Default.Operators` |
| `queryOperatorParameters` | `QueryOperatorParameters.AllParameters` |
| `queryOperatorParameterFields` | every other public static field of `QueryOperatorParameters` that is a `QueryOperatorParameter` or a list of them, in declaration order (`MetadataToken`). Extra keys `field` (field name), `isList`; `index` is the position in the list (0 for a single field). An empty list gives one marker record `{catalog, field, isList: true, index: -1}` and nothing else. |
| `scalarTypes` | `ScalarTypes.All` |
| `scalarTypeFields` | every public static `ScalarSymbol` field of `ScalarTypes`, declaration order; extra key `field` |
| `options` | `GlobalState.Default.Options` (= `Options.All`) |

Common symbol keys (functions, operators, scalar types, options), in this order: `kind`
(`Symbol.Kind`), `name`, `alternateName`, `isHidden`, `tabularity` (`Symbol.Tabularity`).

- **Function symbols** (functions, aggregates, plugins): common keys, then `description`,
  `isObsolete`, `alternative` (the obsolete message: name of the replacement), `optimizedAlternative`,
  `isConstantFoldable`, `isView`, `hasCustomAvailability` (`CustomAvailability != null`),
  `resultNameKind`, `resultNamePrefix`, `minArgumentCount`, `maxArgumentCount` (the
  `FunctionSymbol` properties over all signatures), `display` (`SchemaDisplay.GetText(f)`),
  `debugDisplay` (internal `DebugDisplay.GetText(f)`), `signatures`.
- **Operators**: common keys, `operatorKind`, `result` (`DebugDisplay` of `OperatorSymbol.Result`;
  never assigned upstream, so always `null`), `signatures`.
- **Signature**: `returnKind`, `returnType` (`SchemaDisplay.GetText(DeclaredReturnType)`, `null`
  unless `Declared`), `returnTypeDebug` (`DebugDisplay` of the same; distinguishes
  `dynamic([string])` etc., which `SchemaDisplay` renders as `dynamic`), `defaultReturnType`
  (`DebugDisplay.GetText(GetReturnType(GlobalState.Default))`; `"!<ExceptionType>"` on throw;
  no built-in signature is `Computed`, so this never runs the binder — `Custom` gives
  `": ()"` for tabular (`TableSymbol.Empty` open) and `unknown` for scalar), `hasCustomReturnType`,
  `body` (`Signature.Body`), `declaration` (`Declaration?.ToString()`, i.e. `IncludeTrivia.All`),
  `layout` (`Fixed`/`Repeating`/`RepeatingSkipping`/`BlockRepeating` by reference identity with
  the `ParameterLayouts` fields, else `Custom`), `layoutType` (runtime class name, e.g.
  `NonRepeatingParameterLayout`), `minArgumentCount`, `maxArgumentCount`, `isHidden`,
  `isObsolete`, `alternative`, `hasRepeatableParameters`, `hasOptionalParameters`,
  `hasAggregateParameters`, `tabularity`, `isScalar`, `isTabular`, `allowsNamedArguments`,
  `parameterListDeclaration` (`Parameter.GetParameterListDeclaration`), `parameters`.
- **Parameter**: `name`, `typeKind`, `declaredTypes` (`SchemaDisplay.GetText` each),
  `declaredTypesDebug` (`DebugDisplay` each), `typeText` (`SchemaDisplay.GetParameterTypeText`),
  `argumentKind`, `minOccurring`, `maxOccurring`, `isOptional`, `isRepeatable`, `isCaseSensitive`,
  `values` (`object.ToString()` each — every built-in value is a `String`), `valueTypes` (.NET type
  name of each value), `examples`, `defaultValueIndicator`, `defaultValue`
  (`DefaultValue?.ToString()`), `description`, `tabularity`, `typeDependsOnArguments`,
  `declaration` (`Parameter.GetDeclaration`).
- **Query operator parameters**: `kind`, `name`, `aliases`, `valueKind`, `isRepeatable`,
  `isCaseSensitive`, `values`, `isHidden`, `hasNoEquals`. (`AllParameters` holds the `.Hide()`
  copies, so all are hidden there; the `queryOperatorParameterFields` records show the originals
  and the `.WithValues(...)` variants used by each operator.)
- **Scalar types**: common keys, `display`, `debugDisplay`, `aliases` (`ScalarSymbol.Aliases`),
  `typeMapKeys` (every key of the private `ScalarTypes.s_typeMap` mapping to this instance, read by
  reflection, map enumeration order = insertion order; `[]` for types not in `All`), `isInteger`,
  `isNumeric`, `isInterval`, `isSummable`, `isOrderable`, `isMultiValue`, `widerThan` (names of the
  `ScalarTypes.All` members `o` with `t.IsWiderThan(o)`, in `All` order).
- **Options**: common keys, `description`, `types` (`SchemaDisplay.GetText` each), `examples`.

Enums are written with `ToString()`. `ResultNameKind` has the alias `Default = None`; .NET prints
`None`. Strings that are `null` upstream are JSON `null`; lists are never `null`.

Omitted: `CustomAvailability`, `CustomReturnType` and custom `ParameterLayoutBuilder` delegates
(only presence is recorded); `Signature.Symbol` (back reference); `Symbol.Members`/`IsError`;
`Parameter.DefaultValue` as a tree (only its text); hypothetical-argument
`GetReturnType(globals, argTypes)` (binder territory); the private `FunctionFlags`/`ScalarFlags`
bits (observable through the boolean properties); `GlobalState.Default` cluster/database/
ambient/client symbols (empty or `Unknown` placeholders) and the command symbols (covered by the
generated command sources).

Counts at upstream `9d95a2d5`: functions 429, aggregates 61, plugins 48, operators 55,
queryOperatorParameters 29, queryOperatorParameterFields 143, scalarTypes 12, scalarTypeFields 34,
options 58.

### probe

Prints answers to: D10 (`.foo` under `WithServerKind("Unknown")` and `Engine`), `.show tables |
where x == 1`, D13 (`SyntaxFacts.TryGetKind("")`), the depth constants (private consts read by
reflection), and nesting at 299/300/301/498–501/1000 for `Parse`, `ParseAndAnalyze` and
`Parse` with `ParserKind.Grammar`. The stack-safe fallback inside `QueryParser` is not directly
observable; the probe reports tree depth, `HasSemantics` and diagnostics instead.

## Rendering decisions (the Java `GoldenWriter` must do the same)

- **String escaping.** `"` → `\"`, `\` → `\\`; every code unit below U+0020 and every lone
  surrogate → `\uXXXX` with **upper-case** hex; no short escapes (`\n` is `\u000A`); everything
  else raw UTF-8, including U+007F, U+2028/2029 and valid surrogate pairs. Corpus strings are
  decoded with a custom unescaper so lone surrogates survive.
- **Numbers.** Integers as JSON numbers; `timing` values rounded to 3 decimals.
- **tokens.** Positions are cumulative over `GetLexicalTokens()` (`LexicalToken` has no
  position): `triviaStart` = running offset, `start` = `triviaStart + Trivia.Length`, `end` =
  `start + Text.Length`. `value`: `SyntaxToken.From(lexicalToken)`; if `IsLiteral`,
  `Value?.ToString()` (invariant), else `null`. Non-literal tokens are `null` even though
  `SyntaxToken.Value` returns their text. A literal whose value is null (`int(null)`) is `null`.
  On exception: `"!<.NET type name>"` (unmapped), and `outcome.tokenValues` = `"throw:<mapped>"`.
- **token diagnostics.** Lexer diagnostics carry no absolute location. Mapping:
  `Absolute` → as is; `Relative` → `(start, end - start)` of the token text; `RelativeEnd` →
  `(end, 0)`. (Upstream's tree-level `SetLocation` additionally moves a zero-width token's
  diagnostic to the next token; not applied here because lexical tokens have no tree.)
- **tree.** Iterative pre-order through `ChildCount`/`GetChild(i)`; `null` children are skipped
  but `name` uses the real child index (`parent.GetName(i)`). `depth` counts from the root (0).
  `kind` = `Kind.ToString()` (`List` for `SyntaxList`, `SeparatedElement` for separated elements).
- **syntaxDiagnostics** = `code.GetSyntaxDiagnostics()` in order (not de-duplicated).
- **semanticDiagnostics** = `code.GetDiagnostics()` minus every element equal (`Diagnostic.Equals`
  or same reference) to some syntax diagnostic, order preserved. Includes `KS245` (depth
  exceeded, no location → `start` 0, `length` 0) when analysis was skipped.
- **bind.** Only when `HasSemantics`. Nodes only (tokens never), pre-order `i` shared with `tree`.
  - `symbolKind` = `ReferencedSymbol.Kind.ToString()`, `symbol` = `.Name`.
  - `symbolOwner`: `ColumnSymbol` → `globals.GetTable(column)?.Name`; `DatabaseSymbol` →
    `globals.GetCluster(database)?.Name`; any other symbol → `globals.GetDatabase(symbol)?.Name`.
    So derived columns (from `project`, `extend`, ...) have `null`; schema columns have their table.
  - `type` = `SchemaDisplay.GetText(expression.ResultType)` (non-`Expression` nodes: `null`).
  - `signature` = `"(" + join(", ", BracketNameIfNecessary(p.Name) + ": " +
    SchemaDisplay.GetParameterTypeText(p)) + ") -> " + R`, where `R` =
    `SchemaDisplay.GetText(DeclaredReturnType)` when `ReturnKind == Declared`, else
    `ReturnKind.ToString()` (e.g. `Computed`, `Widest`, `Parameter0`). Parameter types are thus
    `dynamic` for anything but a single declared type (same rule as `SchemaDisplay.GetText(FunctionSymbol)`).
    Example: `(value: dynamic, roundTo: dynamic) -> Widest`.
  - `isConstant` = `Expression.IsConstant` (false for non-expressions); `constantValue` =
    `ConstantValue?.ToString()` only when `isConstant`, else `null`.
  - `calledBody` = first 16 lower-case hex chars of SHA-256 over the UTF-8 bytes of
    `GetCalledFunctionBody().ToString()` (= `IncludeTrivia.All`), checked on every node.
  - `alternates` = `SyntaxNode.Alternates?.Count ?? 0`.
- **resultType** = `SchemaDisplay.GetText(code.ResultType)` only when `HasSemantics`, else `null`.
- **outcome.** `parse`/`analyze`/`tokenValues` = `"ok"` or `"throw:<Name>"`, `Name` mapped through
  `porting/exception-map.json` (`dotnet` table; unmapped names unchanged). `analyze` =
  `"skipped:depth"` when `ParseAndAnalyze` returned with `HasSemantics == false`.
  `tokenValues` uses `throw:<Name>` like the other two (golden-format.md's tokens section says
  `"throw"`; its outcome section says `"throw:<Name>"` — the outcome section was followed).
- **kind** = `code.Kind`; when both parses threw, `KustoCode.GetKind(text)`. `GetKind` never
  returns `Directive`, so in practice only `Query`/`Command` appear.
- **fidelity** when both parses threw: `{"roundTrip": false, "fullWidth": 0}`, empty `tokens`/`tree`.
- **header.** `configuration` from `#if DEBUG`; `invariantGlobalization` from the runtime switch;
  `tz` = `"UTC"` when the local zone has zero offset and no DST, else its id.

### sourcesSha256

`run.sh` hashes the lines `"<path>\t<git blob sha>\n"`, sorted bytewise (`LC_ALL=C sort`), with
SHA-256. Paths are relative to the repository root. Included: every `src/Kusto.Language/**/*.cs`
blob at the submodule `HEAD` except `*/CodeGen/*` (via `git ls-tree`), plus `oracle/generated/*.cs`
and **the oracle's own `oracle/kusto-oracle/src/*.cs`** (via `git hash-object`), because the oracle
code shapes the goldens too. That is exactly the set the build compiles (286 files: 270 upstream, 9 generated, 7 oracle). Untracked
`.cs` files dropped into the upstream tree would be compiled but not hashed.

### Schema files

Format per golden-format.md. Database members are added in a fixed order — tables, functions,
externalTables, materializedViews, entityGroups — each list in file order. Fields:

| List | Fields | Symbol |
|---|---|---|
| `tables` | `name`, `columns[{name,type}]`, `docstring` | `TableSymbol(name, columns, docstring)` |
| `functions` | `name`, `parameters` (`"(a: long)"`; null/empty → `"()"`), `body`, `docstring` | `FunctionSymbol(name, parameters, body, docstring)` |
| `externalTables` | as tables | `ExternalTableSymbol` |
| `materializedViews` | as tables + `query` | `MaterializedViewSymbol(name, columns, query, docstring)` |
| `entityGroups` | `name`, `definition`, `docstring` | `EntityGroupSymbol(name, definition, docstring)` |

Column types go through `ScalarTypes.GetSymbol`; an unknown name fails the dump. `GlobalState`s
are cached per schema id; `schema: null` is `GlobalState.Default`.

### dotnet-facts API semantics

Input: raw text after the TAB, or, when it starts with `[`, a JSON array of arguments (numbers and
booleans are passed as their JSON text). Results use default `ToString()` under invariant culture.

| API | Meaning |
|---|---|
| `char.Is{WhiteSpace,Digit,Letter,LetterOrDigit,Upper,Lower}` | `table` → `[[start,end],...]` inclusive ranges over U+0000–U+FFFF; a single char → boolean |
| `char.To{Upper,Lower}Invariant` | `table` → `[[cp,mapped],...]` where they differ |
| `{Int32,Int64,Double,Decimal,TimeSpan,DateTime,Guid,Boolean}.TryParse` | `X.TryParse(string, out X)`; `ok` = return value, `value` = out value |
| `Double.ToString`, `Single.ToString` | parse with `Float \| AllowThousands` invariant, then `ToString()`; extra `bits` |
| `Decimal.ToString` | parse with `Number \| AllowExponent` invariant |
| `TimeSpan.ToString` / `DateTime.ToString` | integer input = ticks (`DateTime` kind Unspecified), else `TimeSpan.Parse(s, Invariant)` / `DateTime.Parse(s)` |
| `Guid.ToString` | `Guid.Parse(s).ToString()` |
| `TimeSpan.From{Seconds,Minutes,Hours,Days,Milliseconds}` | the `double` overloads (what netstandard2.1 upstream binds to) |
| `TimeSpan.FromTicks` | `long` input |
| `Convert.ToInt64Cast` | C# `(long)double`: saturating on .NET 9+ (±∞/out of range clamp to `long.Min/MaxValue`, NaN → 0) |
| `String.Compare` | `[a,b]`, `[a,ia,b,ib,len]`, `[a,ia,b,ib,len,ignoreCase]` (culture overloads = ordinal under invariant); extra `sign` |
| `String.CompareOrdinalIgnoreCase` | `string.Compare(a, b, OrdinalIgnoreCase)`; extra `sign` |
| `String.EqualsOrdinalIgnoreCase` | `string.Equals(a, b, OrdinalIgnoreCase)` |
| `String.{ToUpperInvariant,ToLowerInvariant,Trim,IsNullOrWhiteSpace}` | as named |
| `String.Split` | `[s, "c"]` → JSON array of parts |
| `Convert.ChangeType` | `[value, target, source]`: parse `value` as `source` (default `String`), then `Convert.ChangeType(obj, typeof(target))`; extras `type`, `bits` for doubles |
| `SyntaxFacts.TryGetKind` | library call; `value` = kind name |

Extras: `ticks` (number) for TimeSpan and DateTime results, `kind` for DateTime, `bits` (decimal
string of `BitConverter.DoubleToInt64Bits`/`SingleToInt32Bits`) for floating results. An exception
gives `ok: false`, `value: "!<.NET exception type name>"` (unmapped).

## T0 findings (2026-10-02, upstream 9d95a2d5)

- **D10 confirmed.** `KustoCode.Parse(".foo", GlobalState.Default.WithServerKind("Unknown"))` and
  `ParseAndAnalyze` throw `InvalidOperationException` ("Sequence contains no elements") from
  `PartialParser.PathFinder.FindBestPath` (`PartialParser.cs:365`). With `Engine` both succeed.
- **D13.** `SyntaxFacts.TryGetKind("")` returns `true` with kind `None`.
- Constants: `QueryParser.MaxDepth` 300, `ForwardParser.MaxCallDepth` 30,
  `Properties.MaxAnalysisDepth` 500 (`GlobalStateProperty` default).
- Nesting `print (((…1…)))` at 299/300/301/1000: neither `Parse` nor `ParseAndAnalyze` throws on a
  256 MB stack; from 498 levels the tree depth exceeds 500 and analysis is skipped (`KS245`).
- Corpora present at the time (readme 7, docs 1,537, sentinel 4,722 records): 0 `Parse` throws,
  0 `ParseAndAnalyze` throws, 0 token-value throws, 0 round-trip failures. Schema-valid.

## Not verified / caveats

- Token `value` uses `SyntaxToken.From(lexicalToken)`, not the tree's tokens. They agree for
  literals as far as the corpora show, but no pairing check is done.
- The `DateTime.TryParse 12:00` fact depends on the run date (time-only input defaults to today).
- `tz` relies on `TZ=UTC` from `run.sh`; running the dll directly uses the machine zone.
- The 256 MB stack has not been stress-tested beyond 1,000 nesting levels.
- gzip bytes are not reproducible run to run (`generatedAt`, timing); compare parsed JSON.
