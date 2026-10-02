# PLAN.md — Kusto.Language → Java mirror port

Phase 0 deliverable. Nothing has been ported. Waiting for review before any code is written.

Companion documents: `UPSTREAM.md` (pin), `PORTING.md` (rules, normative), `NOTICE`, `LICENSE`,
`porting/golden-format.md` (conformance record spec), `porting/manifest.json` (351 entries),
`porting/scope.json` (classification rules), `porting/status.json` (hand-owned status),
`porting/inventory/` (evidence: 11 area reports, 6 analyses, 1 critique), `porting/reference/`
(historical generated code). This plan was itself reviewed adversarially (five lenses, 47
verified findings) and revised; the review transcript is in the session workflow logs.

---

## 1. Upstream inventory

### 1.1 Projects

| Project | Files | Lines | Role | Port? |
|---|---|---|---|---|
| `Kusto.Language` | 270 .cs, 9 .tt, 1 .t4 | 87,200 | parser, binder, editor services | core yes, editor no |
| `Kusto.Language.Generators` | 10 .cs | 13,300 | T4 include sources: syntax-node generator (3 files) and command-grammar generator (7 files) | syntax generator yes |
| `Kusto.Language.Bridge` | 0 .cs | — | Bridge.NET (C# → JS) packaging | no |
| `grammar/` | 2 .g4 + ANTLR cache | 2,035 | documentation only; nothing references it and it lags the C# parser | no |

License: Apache-2.0, `Copyright 2019 Microsoft Corporation`. No upstream `NOTICE`, no per-file
headers. No git tags; releases are NuGet-only. `version.txt` says 12.4.1 but NuGet 12.4.1 was
cut ten commits before the pin (4 of them touch in-scope source).

### 1.2 Namespaces and sizes (`Kusto.Language` only)

| Directory | Namespace | Files | Lines | Scope |
|---|---|---|---|---|
| root | `Kusto.Language` | 16 | 10,383 | include (minus `TestHelpers`) |
| `Parser/` | `Kusto.Language.Parsing` (3 files declare `Kusto.Language`) | 17 + 9 templates | 17,505 | include; 6 optional; command `.tt` stubbed |
| `Parser/Combinators/` | `Kusto.Language.Parsing` | 37 | 7,488 | include (1 optional) |
| `Syntax/` | `Kusto.Language.Syntax` | 15 + 1 template | 4,988 | include; nodes generated |
| `Binder/` | `Kusto.Language.Binding` (`FunctionCallExpansion.cs` declares `Kusto.Language`) | 24 | 17,063 | include |
| `Symbols/` | `Kusto.Language.Symbols` | 48 | 7,536 | include |
| `Diagnostics/` | `Kusto.Language` | 2 | 1,364 | include |
| `Utils/` | `Kusto.Language.Utils` | 21 | 2,143 | include |
| `Editor/` + `Editor/Kusto/` | `Kusto.Language.Editor` | 89 | 18,738 | 9 types in 8 files include, 1 optional, 1 stub, 79 exclude |

Lines by manifest scope (C#/T4 files): include 69,100; optional 682; build-time 4,919;
stubbed (T4 outputs) 850; excluded 25,238 in 89 .cs files. Ported C# ≈ 69.8k (include +
optional); touched including generator and stubs ≈ 75.6k. Java output: 498 files (229 generated,
26 synthetic, 243 ported). Per-wave sizes in section 6.

### 1.3 Dependency graph

Entry points: `KustoCode.Parse` / `ParseAndAnalyze` (`KustoCode.cs:147,164`) → `TokenParser`
→ `QueryParser` (default) or `QueryGrammar` (combinators; also the stack-safe fallback past
300 nesting levels, `QueryParser.cs:141-199`) → `SyntaxTree` → `Binder.TryBind`.

- **The in-scope code is one strongly connected component** (heuristic type-name graph in
  `critic.md` §4.1: 142 of 174 units, 67.8k of 69.8k lines). Confirmed cycle edges:
  `Symbols → Binder` (`ColumnSymbol.cs:200-208`), `Symbols → Parser` (`Parameter.cs:257`,
  `TableSymbol.cs:101`), `Symbols → KustoCode` (`Signature.cs:687`), `Symbols → GlobalState`
  (`Signature.cs:335`), `Syntax → Binder` (`SyntaxNode_Semantics.cs:19`). Outside the SCC: Utils
  leaves, small enums, `TextFacts`, the optional Parser files. Consequence: waves port whole
  files but may create skeleton types and `PORT-PENDING` members for later waves
  (`PORTING.md` §2.7-2.8); layer gates require none on the gated path. Symbols (W1) and the
  catalogs (W3b) must precede the parser gate because `QueryParser`'s static tables read
  `Functions.All`/`Aggregates.All` (`QueryParser.cs:2696-2700`) and `GlobalState.Default` needs
  every catalog. The binder proper (`Binder/`) is the part held back until after the W4 stop.
- **Generated code is absent from the repo.** 225 syntax node classes + 4 visitors and the
  command grammars are T4 outputs deleted in July 2026 (`cd24dcf3`). The repo as cloned does
  not compile. `porting/reference/GeneratedSyntaxNodes.cs` is the last committed output; it is
  structurally identical to the current generator's output (re-implementation check,
  `generators.md` §1.4); T0 confirms byte-for-byte with the real generator.
- **Editor dependencies are nine small types** (1,180 lines) plus a read-only slice of
  `EditString` (stubbed). `CompletionHint` is semantic (`Binder_Names.cs:851`).
- **Commands** are reachable only for text whose first token is `.` (section 3).
- **Static initialisation is a DAG** with one lazily broken cycle and one partial-class order
  hazard (`Functions.Convert.cs` before `Functions.cs`).
- A machine-readable file-level graph is not kept; `porting/tools/depgraph.py` is optional
  follow-up work (listed in W7).

### 1.4 Tests upstream

None. The private test project `Kusto.Language.UT` never existed in this repo's history.
`TestHelpers.cs` exposes binder-cache hooks for it. `readme.md` has API examples that double
as smoke tests. The spec's "port upstream tests" item is replaced by the corpus plan (5.3).

### 1.5 Code generators

| Generator | Input | Output | Decision |
|---|---|---|---|
| `SyntaxNodeGenerator` + `CodeGenerator` | `SyntaxNodeInfos.cs` (225 nodes, 687 properties) | all concrete syntax nodes, `SyntaxVisitor` ×4 | **port to Java** (`kusto-language-generator`), output checked in with a drift test (`PORTING.md` §4) |
| `CommandGenerator` + `Grammar.cs` | `*CommandInfos.cs` (642 engine commands) | `EngineCommandGrammar` (8.5k lines) and symbol tables | **not ported**; 8 stub classes |
| `Functions.Convert.cs`, `Options.cs` | external tools, not in repo | checked in | port as hand-written |

---

## 2. License and attribution

- Root `LICENSE` (Apache-2.0 text, port copyright plus Microsoft portions) and `NOTICE`
  (derived-work statement, corpus licenses): written. The `kusto-language` jar includes both as
  `META-INF/LICENSE` and `META-INF/NOTICE` (Maven resource in T0).
- Every Java file: header with upstream path, synced commit, SPDX line, upstream license,
  modification marker (`PORTING.md` §2.1); a second header form for original files.
  Generated files add the `Copyright (c) Microsoft Corporation` line the upstream generator emits.
- Corpora: Microsoft Learn docs are CC-BY-4.0 (prose) / MIT (code samples); Azure-Sentinel is
  MIT. Both ship only in the conformance module's test resources with their license files and
  `PROVENANCE.json` (source repo, commit, path, extraction script). Nothing from them enters
  the `kusto-language` jar.
- Package `org.graylog.kusto` uses the product name "Kusto". Flagged for trademark review.

---

## 3. Include / exclude / stubs

Authoritative lists: `porting/scope.json` → `porting/manifest.json`. Narrative and evidence:
`porting/inventory/dependency-cut.md`. Summary in `PORTING.md` §8.

| Class | Files | Lines | Rule |
|---|---|---|---|
| include | 180 | 69,100 | reachable from the entry points, or data they need |
| optional | 8 | 682 | no path from entry points; ported in W7 |
| build-time | 4 | 4,919 | generator sources + template |
| stub | 9 | 850 (T4 outputs) + `EditString` | 8 command outputs, `EditString` |
| exclude | 124 (89 .cs) | 25,238 | Editor services, command generator, Bridge, build/docs |
| synthetic | 26 Java files | — | `utils.dotnet`, `LexicalTokenParsers`, `P` |

Three adversarial reviewers attacked the cut. It survived reachability and completeness; one
refuted the **behaviour** of the command boundary (false errors on `.cmd | where …`, embedded
queries dropped). Decision (`PORTING.md` §8): mirror upstream's `UnknownCommand` path as-is;
command records are compared on tokens and fidelity only, with their count reported. Whether
the zero-parser `CommandGrammar` throws is unverified and is settled by the oracle in T0.

---

## 4. Translation rules

Settled in `PORTING.md` §2-§7 with before/after examples. The decisions that shape the work:

| Topic | Decision |
|---|---|
| Java version | 21. Zero sites need Java 25 flexible constructors. |
| Names | `foo()` accessors; methods lowerCamel; `hashCode/equals/toString` protocol names; `nameof` keeps C# spelling |
| Files / partials / arity / erasure | one file per top-level type; partials merged in ordinal file order; `Foo`/`Foo2` arity suffix; explicit rename table for erasure clashes |
| `internal` | `public @Internal` |
| structs | records for plain immutable structs; final classes with upstream `equals/hashCode` for custom-equality structs; custom null-carrying `Optional` |
| ref/out | `Out<T>`, `IntRef` |
| LINQ | loops on hot paths, eager `Linq` helper elsewhere; no streams |
| yield | eager lists (4 cold methods) |
| collections | `LinkedHashMap`/`LinkedHashSet` always; never `EnumMap` where enumerated; `ReadOnlyList<T>` for upstream list types; upstream shapes for `SubstringMap`/`TextKeyedDictionary` |
| enums | identical order; alias members as static constants; `default(enum)` fields initialised explicitly |
| culture | invariant; `utils.dotnet` reproduces .NET TryParse/ToString; oracle Release, invariant, UTC |
| string compare | ordinal; `OrdinalIgnoreCase` = upper-case per char; `(start,length)` → `start + length` |
| 64 KB | `QueryGrammar.Initialize` split along regions and sub-regions; all cross-boundary locals become fields |
| threading | `Interlocked` ported over `VarHandle`s; `synchronized`; `get`+`putIfAbsent`; one shared forward-depth counter |
| stack | constants unchanged; 4 MB thread documented; harness uses 16 MB |
| markers | `// PORT: §rule` at every non-transliterated site; `// PORT: D<n>` for site deviations; `PORT-PENDING`/`PORT-SKELETON` for cross-wave stubs |

---

## 5. Test tooling

### 5.1 Oracle (`oracle/kusto-oracle`, .NET 10, test tooling only)

Layout: `oracle/kusto-oracle/Kusto.Oracle.csproj` (console, `net10.0`, `-c Release`,
`InvariantGlobalization=true`, `LangVersion=latest`, `AllowUnsafeBlocks=true`, `Nullable`
disabled), compiling `../../upstream/kusto-query-language/src/Kusto.Language/**/*.cs` (minus
`CodeGen/**`; `Editor/**` is kept, the oracle is the full upstream library) plus
`oracle/generated/*.cs` (gitignored). `oracle/kusto-oracle-gen/` is a second console project
with explicit `<Compile Include>` of the ten `Kusto.Language.Generators/*.cs` files; it calls
`SyntaxNodeGenerator.Generate(SyntaxNodeInfos.All, SyntaxNodeInfos.KnownTypes)` and
`new CommandGenerator().GenerateParser/GenerateSymbols(name, typeof(XCommandInfos))` for the
four server kinds, and writes the nine files. `oracle/run.sh {generate|build|dump|facts|regenerate}`
exports `TZ=UTC` and `DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1`. The oracle embeds the
upstream commit and the SHA-256 of all compiled sources in the golden header
(`porting/golden-format.md`).

T0 acceptance for the oracle:
- `run.sh generate` output for syntax nodes equals `porting/reference/GeneratedSyntaxNodes.cs`
  byte-for-byte after stripping BOM, the leading `// ` lines and the trailing newline; command
  outputs are generated in `*CommandInfos.cs` declaration order.
- `run.sh dump corpus/readme.jsonl` produces records with `fidelity.roundTrip == true` for all.
- `run.sh facts` writes `dotnet-facts.json` for every line of `porting/dotnet-facts-cases.txt`.
- `run.sh dump` on `.foo` with `WithServerKind("Unknown")` records whether it throws (settles D10).
- `run.sh dump` over all corpora records whether `ParseAndAnalyze` ever throws (settles the
  analysis never-throws invariant, `PORTING.md` §6).

Fallback if compiling the generator sources fails: `dotnet tool install dotnet-t4` and run the
`.tt` files with `-I` include paths. Last resort: `porting/reference/GeneratedSyntaxNodes.cs`
plus hand-applied `EngineCommandInfos.cs` diffs.

### 5.2 Golden format

`porting/golden-format.md` (v1): per record `tokens` (with values and per-token diagnostics),
`fidelity`, `tree` (pre-order, parent index, child names), `syntaxDiagnostics`,
`semanticDiagnostics`, `bind` (symbol, owner, type, signature, constant, called-body hash,
alternates), `resultType`, `outcome` (`parse`/`analyze`/`tokenValues`, exception names via
`porting/exception-map.json`), `timing` (ignored). Header line records upstream commit and
oracle build. Schema input format defined there too. Goldens live at
`kusto-language-conformance/src/test/resources/goldens/<corpus>.jsonl.gz`, committed;
`mvn verify` needs no .NET. A JSON Schema file is produced in T0 from the spec.

### 5.3 Corpora (`kusto-language-conformance/src/test/resources/corpus/`)

| Corpus | Source | Script → output | Schema |
|---|---|---|---|
| `readme` | upstream `src/Kusto.Language/readme.md` code fences | `porting/tools/extract_readme_corpus.py` → `readme.jsonl` | `readme-v1.json` (the readme's `db`/`Shapes` tables, hand-transcribed) |
| `docs` | KQL fences (` ```kusto `) in `MicrosoftDocs/dataexplorer-docs` | sparse checkout of `data-explorer/kusto/query/` into gitignored `build/corpora/`, pinned in `porting/corpora.json`; `extract_docs_corpus.py` → `docs.jsonl` + `PROVENANCE.json` + license copies | `samples-v1.json`: the `help/Samples` database schema, captured once by a human running `.show database Samples schema as json` against `https://help.kusto.windows.net` (command in the script's docstring); records without a matching table fall back to `null` schema and are parse-only for bind layers |
| `sentinel` | `query:` fields of YAML rules under `Detections/`, `Hunting Queries/`, `Solutions/**/Analytic Rules/` in `Azure/Azure-Sentinel` | sparse checkout pinned in `porting/corpora.json`; `extract_sentinel_corpus.py` (PyYAML, pinned) → `sentinel.jsonl` | `sentinel-v1.json` converted from Sentinel's `.script/tests/KqlvalidationsTests/CustomTables/*.json` (MIT) by `convert_sentinel_schema.py` |
| `traps` | one hand-written case per `PORTING.md` §5 policy and per fidelity risk (`fidelity-tests-api.md` A5: comments without newline, `\r`-only, BOM, lone surrogate, NUL, unterminated literals, 299/301/1000-deep nesting, …) | `traps.jsonl`, authored in T0b | `null` or `readme-v1` |
| `fuzz` | mutations of the above: token deletion, insertion, swap, truncation at each token boundary; seeded, deterministic | `Mutator.java` generates at test time from `fuzz-seeds.txt`; failing mutants are delta-reduced by `Reducer.java` and promoted into `traps.jsonl` | parent's schema |

Fuzz policy: locally, fuzz checks the invariants only (never throws, round-trip, bounded
time). Nightly CI (with .NET) dumps the same mutants through the oracle and compares; a record
where both sides throw the same mapped exception type matches.

Corpus pins: `MicrosoftDocs/dataexplorer-docs` and `Azure/Azure-Sentinel` main-branch heads at
T0 unless commits are named (section 9).

Golden size (T0b decision): a full sentinel dump is 37 MB gzipped, so committed goldens for
large corpora are a deterministic sample (every k-th record; sizes in `porting/corpora.json`
`goldenSample`, sentinel = 1200 → 1181 records, 9.7 MB). The corpus files stay complete;
`run.sh regenerate --all` (nightly) dumps everything and the harness compares whatever records
the golden holds.

### 5.4 Conformance harness (`kusto-language-conformance`)

- `GoldenWriter` (Java side of `golden-format.md`), `GoldenComparator` (field-wise, cause
  tagging via `known-differences.json`), `ConformanceReport` (per corpus × layer pass counts
  and rates excluding known differences; failures grouped by cause tag, then by first differing
  path, then by diagnostic code; unified diff of the first 20 lines per failing record; JSON
  report for CI trend tracking).
- `known-differences.json`: `{id pattern, path pattern, dRow, reason, expires: <upstream sha>}`;
  reviewed like code.
- `conformance-baseline.json`: per corpus × layer expected pass counts; a wave gate fails on
  any regression against it for layers already gated ("green" = no regression and no new
  unclassified failure).
- Invariant tests (`InvariantsTest`): `root.toString().equals(text)` and
  `root.fullWidth() == text.length()` for every record; token starts monotonic;
  `getTokenAt` round-trips; `parse` never throws on a 16 MB-stack thread; parse ≤ 2 s, inputs
  over 200 ms reported as pathological; static-init order test; depth tests at 299/301,
  30/31, 499/501 under `-Xint`.
- `TrapsTest`: one test per `PORTING.md` §5 policy against `dotnet-facts.json`.
- `SourceRulesTest`: greps `kusto-language` sources for banned APIs (`java.util.stream`,
  `HashMap`, `HashSet`, `EnumMap`, `CASE_INSENSITIVE_ORDER`, `equalsIgnoreCase`, `trim()`,
  `strip()`, `isBlank()`, `String.split`, `String.join`, `Double.parseDouble`, `Long.parseLong`,
  `UUID.fromString`, `assert `, `substring(` with a non-additive second argument) outside
  `utils.dotnet`, and checks headers and markers (`check_headers.py`, `check_pending.py`).
- JMH (`kusto-language-benchmarks`): parse and parse+bind on three fixed workloads (short
  filter, 2 KB Sentinel rule, 40 KB synthetic union) plus a shared-`GlobalState` contention
  case; informational. A BenchmarkDotNet baseline on the oracle is optional.

### 5.5 API proof

`ExampleTranslatorTest`: a KQL → normalised-KQL printer using `SyntaxVisitor1<String>`,
`ReferencedSymbol`, `ResultType`, `getCalledFunctionBody()` and `GlobalState.withDatabase(…)`.
Assertions: (1) in whitespace-only mode the output equals the input modulo trivia; (2) in
annotated mode (table-qualified columns, type comments) the output re-parses with no
diagnostics and binds to the same result types as the input. Not a real translator.

---

## 6. Work breakdown: waves

Model tiers (per `~/.claude/CLAUDE.md`): **strong** = this Fable session directly, or Opus
subagents; Fable subagents only with explicit approval per task. **mechanical** = Sonnet
(Haiku for pure data mirrors). Every delegated change is reviewed against `PORTING.md` by the
strong tier before merge. One commit per wave or sub-wave; message names the upstream commit
and the manifest entries covered. After each wave: set `status` in `porting/status.json`, run
`build_manifest.py`, commit.

| Wave | Content (`manifest.wave`) | C# lines | Java files | Tier | Gate (all must pass) |
|---|---|---|---|---|---|
| **T0** | Scaffold: parent POM `org.graylog.kusto:kusto-parent:0.1.0-SNAPSHOT` (Java 21, pinned plugin versions, enforcer `bannedDependencies` on `kusto-language`, `META-INF/LICENSE+NOTICE`), four modules, `.github/workflows/ci.yml` (Java only) and `nightly-oracle.yml`; `utils.dotnet` package (26 synthetic files) with unit tests; `porting/dotnet-facts-cases.txt`; `porting/exception-map.json`; `golden-format.schema.json`. Oracle: `kusto-oracle-gen`, `kusto-oracle`, `run.sh`. Corpora: `porting/corpora.json` pins, extraction scripts, schemas, `PROVENANCE.json`, license copies. | — | 26 | strong (oracle, dotnet compat, golden writer spec); mechanical (POMs, scripts) | oracle acceptance list in 5.1; `mvn verify` green with no .NET; `mvn dependency:list -pl kusto-language` shows no runtime deps |
| **T0b** | Conformance harness: `GoldenWriter`, `GoldenComparator`, `ConformanceReport`, `InvariantsTest`, `TrapsTest`, `SourceRulesTest`, `known-differences.json` (empty), `conformance-baseline.json`; `traps.jsonl` authored; goldens generated and committed for `readme`, `docs`, `sentinel`, `traps`; `Mutator`/`Reducer`. | — | ~15 | strong (comparator, traps); mechanical (report, scripts) | goldens committed with header; `fidelity.roundTrip` true for 100% of records (else a corpus bug is filed before W2); harness runs against an empty port and reports 0% per layer without crashing |
| **W0** | `kusto-language-generator` (3 generator files, data mirror, `LexicalTokenParsers` emitter), 229 generated node/visitor files, `Utils/`, `Diagnostics/`, hand-written `Syntax/` (incl. `SyntaxFacts`), 8 Editor files + `EditString` stub; skeletons for W1/W2/W4/W6 types referenced | 15,451 | 320 | strong: generator emitter, `SyntaxElement`/`SyntaxToken`/`SyntaxFacts`; mechanical: `SyntaxNodeInfos` data, Utils, Diagnostics, Editor enums | compiles with listed skeletons; `GeneratedSyntaxNodesUpToDateTest` green; `check_generated_structure.py` clean; `check_pending.py` lists only W1/W2/W4/W6 skeletons |
| **W1** | `Symbols/` (48), `Options`, `Properties`, `ServerKinds`, `KustoDialect`, `P` builder | 7,910 | 72 | mechanical with strong review; `Signature`, `TableSymbol`, `ScalarTypes`, `ParameterLayouts` strong | compiles; `PORT-PENDING` only at Binder/Parser/KustoCode edges (listed in the commit); `ScalarTypes`/`TableSymbol.From` unit tests from readme examples |
| **W2** | Lexer: `TextFacts`, `TokenParser`, `LexicalToken`, `ParseOptions`, `KustoFacts` (+Keywords) | 3,818 | 5 | strong | **tokens + fidelity layers 100%** on readme, docs, sentinel, traps (excluding known differences, fuzz nightly only); `TrapsTest` green; no `PORT-PENDING` reachable from `TokenParser.parseTokens` |
| **W3a** | `Parser/Combinators/` (36 files), `SyntaxParsers`, `LexicalTokenParsers` facade generated | 8,900 | 51 | strong (Opus): generics, pools, safe parsers | compiles; combinator behaviour tests (`Rule`, `First`, `Best`, `Forward` depth fallback, `SafeParser` ≡ direct on nested input) |
| **W3b** | Catalogs: `Functions`(+Convert), `Aggregates`, `Operators`, `PlugIns`, `FunctionHelpers`, `FunctionBodyFacts`, `QueryOperatorParameters` | 7,400 | 8 | mechanical (Haiku/Sonnet) + strong review of helpers | catalog name/signature/parameter equality against `run.sh dump --globals` (an oracle dump of `GlobalState.Default`, commands excluded); both-order static-init test |
| **W4** | `QueryParser`, `QueryGrammar` (split, `javap` size check), `CommandGrammar`, `PredefinedRuleParsers`, `KustoCode`, `GlobalState`, `KustoCache`, 8 command stubs | 14,267 | 20 | strong | **tree + syntaxDiagnostics + outcome.parse layers** green against baseline on all corpora (target 100% excluding known differences); `InvariantsTest` green; `ParserKind.Grammar ≡ Default` on the corpus; no `PORT-PENDING` reachable from `KustoCode.parse`; **STOP AND REPORT** |
| **T1** | Upstream tracking: `porting/upstream-diff/upstream_diff.py`, `sync_headers.py`; dry runs over `1e079791..9d95a2d5` (10 real commits: catalog, facts, Interlocked, excluded command infos) and over `6c977323^..cd24dcf3` (SyntaxNodeInfos change and generated-file deletions) so every classification path is exercised | — | — | strong | both dry-run reports reviewed; every changed file classified correctly |
| **W6** | `Binder/` (24 files → 13 Java), binder edges pending from W1 filled, `SyntaxNode_Semantics` regions | 17,083 | 13 | strong | **semanticDiagnostics + bind + resultType + outcome.analyze layers** green against baseline; zero `PORT-PENDING`/skeletons in scope; `GetSymbolsInScope`/`GetColumnsInScope` spot checks |
| **W7** | Optional files (8), example translator, JMH, `depgraph.py` (optional), final manifest statuses, deviation register audit | 682 | 8 | mechanical + strong review | definition of done |

Wave numbering matches `manifest.wave` (5 unused; W3a/W3b share 3).

Review gates applied to every delegated commit before merge: header present and commit
matches `status.json`; member order equals upstream (script lists member names from both
files); every `// PORT:` cites a rule or a D-row; `SourceRulesTest` green; no regression in
`conformance-baseline.json` for gated layers.

---

## 7. Upstream tracking tooling (T1)

`porting/upstream-diff/upstream_diff.py <old> <new>`:
1. `git diff --name-status old new` in the submodule.
2. Map each path through `manifest.json`; classify: `pending/ported/partial → patch`,
   `generated`/`generated+handwritten → regenerate`, `stubbed → re-check stub`,
   `excluded → re-check exclusion`, `new → triage`, `deleted → remove`, `synthetic` untouched.
3. For catalog files, split the diff into data lines (`new FunctionSymbol(…)` blocks) and logic
   lines; data diffs map line-for-line to Java.
4. Strip the UTF-8 BOM before diffing; report `#if BRIDGE` regions touched.
5. Output Markdown + JSON; exit non-zero if triage is needed.

Bump procedure: `PORTING.md` §11.

---

## 8. Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | Oracle generation path fails on some T4-specific construct | T0 acceptance diff against the reference file; fallback `dotnet-t4`; last resort the reference file plus hand-applied diffs |
| R2 | Giant SCC: waves cannot be verified by compile alone | skeleton/pending discipline (`PORTING.md` §2.8), `check_pending.py`, conformance per layer |
| R3 | `QueryGrammar.Initialize` split changes evaluation order of `Forward` parsers or still exceeds 64 KB | split only at region/sub-region boundaries; cross-boundary locals computed by script; `javap` size check; `ParserKind.Grammar ≡ Default` test |
| R4 | Culture/formatting drift between oracle host and Java | invariant policy pinned in the oracle project and `run.sh`; `dotnet-facts` tests; golden header records the runtime |
| R5 | Stack overflow on default 1 MB threads in Graylog | documented 4 MB requirement; depth tests record real `-Xss`; a non-mirrored facade may add a dedicated thread later |
| R6 | `Binder.TryBind` serialises all analysis on one `GlobalState` | documented; JMH contention case; integration may keep one `GlobalState` per worker |
| R7 | `docs` corpus binding mostly "unknown table" without the Samples schema | human-captured `samples-v1.json`; parse-only fallback per record |
| R8 | Upstream cadence (8 auto-syncs in the last 10 commits, catalog-heavy) | upstream-diff classifies data vs logic; catalogs mirror line-for-line |
| R9 | Unicode table mismatch (Java 21 Unicode 15.0 vs .NET 10) | D21; `dotnet-facts` emits category sets; traps corpus covers post-15.0 characters as known differences |
| R10 | `EditString` stub hides a behavioural dependence | 12 call sites verified; directive test (`#database "c" "db"`) in traps |
| R11 | Golden format implemented differently in C# and Java | one spec file, JSON Schema validation on both outputs, `readme` corpus cross-check in T0b before any port work |
| R12 | Known-differences file silently grows into a blanket waiver | each entry needs a D-row and an `expires` commit; the report prints the waived count per layer |

---

## 9. Decisions needing your confirmation

1. **Command text**: mirror the `UnknownCommand` path with its false diagnostics and compare
   command records on tokens/fidelity only (chosen), or add a non-mirrored open-table scope.
2. **Oracle runtime**: .NET 10 LTS from source at the pin (chosen) rather than NuGet 12.4.1.
3. **Naming**: `foo()` property accessors and lowerCamel methods (a systematic rename, chosen)
   rather than keeping upstream `Foo`/`DoThing` spelling.
4. **Arity naming**: numeric suffix (`SyntaxList1<T>`) rather than a per-family hand pick.
5. **Generated Java checked in** with a drift test (chosen) rather than generated at build time.
6. **Model tiers**: Opus is the "strongest" subagent tier by default; Fable subagents only when
   you approve per task.
7. **Corpus pins**: main-branch heads of `MicrosoftDocs/dataexplorer-docs` and
   `Azure/Azure-Sentinel` at T0 unless you name commits.
8. **Names that differ from the spec**: `oracle/kusto-oracle` (a directory, not a Maven module)
   and `porting/upstream-diff/upstream_diff.py`.
9. **Optional files** ported in W7 (chosen) rather than excluded.

## 10. Definition of done (restated against this plan)

- W4 gate: tokens, fidelity, tree, syntax diagnostics and parse outcome conform on all corpora
  against the baseline; invariants hold; report delivered before W6 starts.
- W6 gate: semantic diagnostics, bind, result type and analysis outcome conform, reported per
  corpus with failure groups and the waived count.
- Manifest: no `pending`/`partial` entries in scope; every `// PORT:` cites a rule or D-row;
  every D-row has at least one site or a "rule-level" note.
- `upstream_diff.py` dry runs over both ranges reviewed.
- JMH numbers and the example translator test in the repo.
