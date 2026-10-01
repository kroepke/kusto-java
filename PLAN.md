# PLAN.md — Kusto.Language → Java mirror port

Phase 0 deliverable. Nothing has been ported. Waiting for review before any code is written.

Companion documents: `UPSTREAM.md` (pin), `PORTING.md` (rules, normative), `NOTICE`,
`porting/manifest.json` (325 entries), `porting/scope.json` (classification rules),
`porting/inventory/` (evidence: 11 area reports, 6 analyses, 1 critique).

---

## 1. Upstream inventory

### 1.1 Projects

| Project | Files | Lines | Role | Port? |
|---|---|---|---|---|
| `Kusto.Language` | 270 .cs, 9 .tt, 1 .t4 | 87,200 | parser, binder, editor services | core yes, editor no |
| `Kusto.Language.Generators` | 10 .cs | 13,300 | T4 include sources: syntax-node generator (3 files) and command-grammar generator (7 files) | syntax generator yes |
| `Kusto.Language.Bridge` | 0 .cs | — | Bridge.NET (C# → JS) packaging | no |
| `grammar/` | 2 .g4 + ANTLR cache | 2,035 | documentation only; nothing references it and it lags the C# parser | no |

License: Apache-2.0, `Copyright 2019 Microsoft Corporation`. No upstream `NOTICE`. No per-file
headers. No git tags; releases are NuGet-only and NuGet 12.4.1 is ten commits behind the pin.

### 1.2 Namespaces and sizes (`Kusto.Language` only)

| Directory | Namespace | Files | Lines | Scope |
|---|---|---|---|---|
| root | `Kusto.Language` | 16 | 10,383 | include (minus `TestHelpers`) |
| `Parser/` | `Kusto.Language.Parsing` (4 files declare `Kusto.Language`) | 17 + 9 templates | 17,505 | include; 7 optional; command `.tt` stubbed |
| `Parser/Combinators/` | `Kusto.Language.Parsing` | 37 | 7,488 | include (1 optional) |
| `Syntax/` | `Kusto.Language.Syntax` | 15 + 1 template | 4,988 | include; nodes generated |
| `Binder/` | `Kusto.Language.Binding` | 24 | 17,063 | include |
| `Symbols/` | `Kusto.Language.Symbols` | 48 | 7,536 | include |
| `Diagnostics/` | `Kusto.Language` | 2 | 1,364 | include |
| `Utils/` | `Kusto.Language.Utils` | 21 | 2,143 | include |
| `Editor/` + `Editor/Kusto/` | `Kusto.Language.Editor` | 89 | 18,738 | 9 files include, 1 stub, 79 exclude |

In-scope total: 75,551 C# lines → 473 Java files (229 generated). Per-wave sizes in section 6.

### 1.3 Dependency graph

Entry points: `KustoCode.Parse` / `ParseAndAnalyze` (`KustoCode.cs:147,164`) → `TokenParser`
→ `QueryParser` (default) or `QueryGrammar` (combinators; also the stack-safe fallback past
300 nesting levels, `QueryParser.cs:141-199`) → `SyntaxTree` → `Binder.TryBind`.

Key facts the plan is built on:

- **The in-scope code is one strongly connected component.** 142 of 174 compilation units
  (67.8k of 69.8k lines) reference each other cyclically: `Symbols → Binder`
  (`ColumnSymbol.cs:200`), `Symbols → Parser` (`Parameter.cs:257`), `Symbols → KustoCode`
  (`Signature.cs:687`), `Syntax → Binder` (`SyntaxNode_Semantics.cs:7`). Only Utils leaves,
  small enums and `TextFacts` sit outside it. Consequence: waves cannot be "compile-clean
  layers". Each wave ports whole files but may leave `// PORT-PENDING` stubs for members whose
  callee arrives later; the layer gates require zero pending stubs on their path.
- **Generated code is absent from the repo.** Syntax nodes (225 classes + 4 visitors) and the
  command grammars/symbol tables are T4 outputs deleted from git in July 2026. The repo as
  cloned does not compile. The last checked-in output is byte-identical to what the current
  generator produces (verified by re-implementation, `generators.md` §1.4).
- **Editor dependencies are nine small types** (`CodeKinds`, `CompletionHint`, `CompletionKind`,
  `CompletionPriority`/`Rank`, `CompletionItem`, `CompletionText`, `ClientDirective`,
  `ClientDirectiveArgument`), 1,236 lines, plus a read-only slice of `EditString` (stubbed).
  `CompletionHint` is semantic: the binder reads it to decide tabular context.
- **Commands** are reachable only for text whose first token is `.`. They can be stubbed
  (section 4).
- **Static initialisation is a DAG** with one lazily broken cycle (`KustoFacts` ⇄
  `QueryOperatorParameters`) and one partial-class order hazard (`Functions.Convert.cs` must
  precede `Functions.cs`).

### 1.4 Tests upstream

None. The private test project `Kusto.Language.UT` never existed in this repo's 1,262-commit
history. `TestHelpers.cs` exposes binder-cache hooks for it. `readme.md` has API examples that
double as smoke tests. The spec's "port upstream tests" line item is replaced by the corpus
plan (section 5.3).

### 1.5 Code generators

| Generator | Input | Output | Decision |
|---|---|---|---|
| `SyntaxNodeGenerator` + `CodeGenerator` | `SyntaxNodeInfos.cs` (225 nodes, 687 properties) | all concrete syntax nodes, `SyntaxVisitor` ×4 | **port to Java** (`kusto-language-generator`), output checked in with a drift test |
| `CommandGenerator` + `Grammar.cs` | `*CommandInfos.cs` (642 engine commands) | `EngineCommandGrammar` (8.5k lines) and symbol tables | **not ported**; 8 stub classes |
| `Functions.Convert.cs`, `Options.cs` | external tools, not in repo | checked in | port as hand-written |

---

## 2. License and attribution

- `NOTICE` (written): derived-work statement, Microsoft copyright, corpus licenses.
- Every Java file: 4-line header with upstream path, commit, license, modification marker
  (`PORTING.md` §2.1). Generated files add the `Copyright (c) Microsoft Corporation` line the
  upstream generator emits.
- Corpora: Microsoft Learn docs are CC-BY-4.0 (prose) / MIT (code samples); Azure-Sentinel is
  MIT. Both corpora ship only in the conformance module's test resources with their license
  files and a provenance manifest (source repo, commit, path, extraction script). Nothing from
  them enters the `kusto-language` jar.
- Package `org.graylog.kusto` uses the product name "Kusto". Flagged for trademark review; not a
  blocker.

---

## 3. Include / exclude / stubs

Authoritative lists: `porting/scope.json` → `porting/manifest.json`.
Narrative and evidence: `porting/inventory/dependency-cut.md`. Summary in `PORTING.md` §8.

| Class | Files | Lines | Rule |
|---|---|---|---|
| include | 180 | 74,869 | reachable from the entry points, or data they need |
| optional | 8 | 682 | no path from entry points; ported last if cheap |
| build-time | 4 | 4,037 | generator sources + template |
| stub | 9 | — | 8 command outputs, `EditString` |
| exclude | 124 | ~36k | Editor services, command generator, Bridge, build/docs |

Three adversarial reviewers attacked the cut (`critic.md` §5, workflow skeptics). It survived
reachability and completeness; one reviewer refuted the **behaviour** of the command boundary:
`.cmd | where …` yields false name-resolution errors and queries embedded in commands are
dropped silently. Decision (PORTING.md §8): mirror upstream's `UnknownCommand` path as-is and
pin those diagnostics in goldens. Command text is not a Graylog use case. Revisit if that changes.

---

## 4. Translation rules

Settled in `PORTING.md` §3-§7 with before/after examples. The decisions that shape the work:

| Topic | Decision |
|---|---|
| Java version | 21. Zero sites need Java 25 flexible constructors (42 computed `base(...)` chains, all inline-legal). |
| Properties | `foo()` accessors |
| Multi-type files / partials / arity | one file per top-level type; partials merged in ordinal file order; `Foo`/`Foo2` arity suffix |
| `internal` | `public @Internal` |
| structs | records (immutable) / final classes (mutable); custom null-carrying `Optional` |
| ref/out | `Out<T>`, `IntRef` |
| LINQ | loops on hot paths, eager `Linq` helper elsewhere; no streams |
| yield | eager lists (4 cold methods) |
| collections | `LinkedHashMap`/`LinkedHashSet` always; never `EnumMap` where enumerated |
| culture | invariant; `utils.dotnet` reproduces .NET TryParse/ToString; oracle runs invariant + UTC |
| string compare | ordinal; `OrdinalIgnoreCase` = upper-case per char |
| 64 KB | `QueryGrammar.Initialize` split along regions; forward locals → fields |
| threading | `VarHandle` CAS, `synchronized`, `get`+`putIfAbsent`, per-type `ThreadLocal` depth |
| stack | constants unchanged; 4 MB thread documented; harness uses 16 MB |

---

## 5. Test tooling

### 5.1 Oracle (`oracle/Kusto.Oracle`, .NET 10, test tooling only)

Build: a `.csproj` that compiles `upstream/.../src/Kusto.Language/**/*.cs` (minus `CodeGen`
and Bridge) plus the generated outputs. Generation step: a tiny C# runner project compiles
`Kusto.Language.Generators/*.cs` as a library (they are plain C# under `#if !T4`) and calls
`SyntaxNodeGenerator.Generate(...)` and `CommandGenerator.GenerateParser/GenerateSymbols(...)`
to write the nine `.cs` files into `oracle/generated/`. Fallback if that fails: `dotnet-t4`.
Settings: `InvariantGlobalization=true`, `TZ=UTC`, `net10.0`. The oracle embeds the upstream
commit and the SHA-256 of every compiled source into its output header, so a golden file proves
which sources produced it.

Modes:
- `oracle dump <corpus.jsonl>` → one JSON line per query (format below).
- `oracle dotnet-facts` → reference values for the `utils.dotnet` unit tests (TryParse edge
  cases, double/decimal/TimeSpan/DateTime formatting, Trim set, OrdinalIgnoreCase pairs).
- `oracle schema <file>` → builds `GlobalState` from a schema description shared with Java.

### 5.2 Golden format (v1, spec written once, implemented twice)

```json
{"id": "docs/ago-0001", "text": "...", "schema": "samples-v1", "upstream": "9d95a2d5", "oracle": "<sha256 of sources>",
 "tokens": [{"kind": "IdentifierToken", "trivia": "", "text": "T", "start": 0}],
 "tree": [{"kind": "QueryBlock", "name": "", "start": 0, "end": 12, "depth": 0}],
 "diagnostics": [{"code": "KS001", "severity": "Error", "start": 3, "length": 1, "message": "..."}],
 "bind": [{"start": 0, "end": 1, "kind": "NameReference", "symbolKind": "Table", "symbol": "T", "type": "(a: real, b: real)"}],
 "resultType": "(a: real)", "outcome": "ok|throw:<type>", "parseMs": 1.2}
```
- `tree` is a pre-order list with depth; `name` is the child name from `GetName(i)`.
- `bind` covers every `SyntaxNode` with a `ReferencedSymbol` or an `Expression.ResultType`;
  types are printed with upstream's public `SchemaDisplay.GetText` on both sides.
- `outcome` records exceptions from lazy value conversion so "throws iff oracle throws" is testable.
- Goldens are stored gzip-compressed per corpus file; a header line records the oracle build.

### 5.3 Corpora (all under `kusto-language-conformance/src/test/resources/corpus/`)

| Corpus | Source | Provenance | Schema |
|---|---|---|---|
| `readme` | upstream `src/Kusto.Language/readme.md` examples | submodule | the readme's `db`/`Shapes` tables |
| `docs` | KQL examples from `MicrosoftDocs/dataexplorer-docs` (code fences tagged `kusto`) | sparse checkout at a pinned commit; extraction script `porting/tools/extract_docs_corpus.py`; CC-BY-4.0/MIT notice | `help/Samples` schema captured once from the public cluster via `.show database schema` into `samples-v1.json` (checked in) |
| `sentinel` | `Azure/Azure-Sentinel` detections and hunting queries (YAML `query:` fields) | sparse checkout of `Detections/`, `Hunting Queries/`, `Solutions/**/Analytic Rules/` at a pinned commit; MIT | table schemas from Sentinel's own `.script/tests/KqlvalidationsTests/CustomTables/` (MIT) |
| `traps` | hand-written cases for every `PORTING.md` §5 policy and the fidelity risk list (`fidelity-tests-api.md` A5) | ours | none |
| `fuzz` | mutations of the above: token deletion, insertion, swap, truncation at every token boundary, plus 299/301/1000-deep nesting | generated deterministically (seeded) by `Mutator.java`; never checked in beyond the seed list | same as parent |

Fuzz policy: the Java side must not throw; the oracle is run on the same mutants in CI
(nightly, .NET available) and any mismatch is a conformance failure like any other.

### 5.4 Invariants beyond oracle equality

- `root.toString().equals(text)` and `root.fullWidth() == text.length()` for every input
  (upstream is full-fidelity: leading-only trivia, trailing trivia on the end token,
  `fidelity-tests-api.md` A1-A4).
- Token starts monotonic; `GetTokenAt(p)` round-trips.
- No `Throwable` from `parse`/`parseAndAnalyze` on a 4 MB stack thread; parse time ≤ 2 s per
  input, inputs over 200 ms are reported as pathological (not a failure).
- Static-init order test: load catalogs in both orders.

### 5.5 Reporting

`mvn verify -pl kusto-language-conformance` prints per layer (tokens, tree, diagnostics, bind)
pass counts and rates per corpus, failures grouped by first differing field and by diagnostic
code, with a unified diff of the first 20 lines of each failing record. A JSON report is written
for CI trend tracking. JMH (`kusto-language-benchmarks`): parse, parse+bind, on three fixed
workloads (short filter, 2 KB Sentinel rule, 40 KB synthetic union) plus a shared-`GlobalState`
contention case; informational.

### 5.6 API proof

`ExampleTranslatorTest`: a KQL → normalised-KQL pretty-printer using `SyntaxVisitor1<String>`,
`ReferencedSymbol`, `ResultType` and `GlobalState.withDatabase(...)`. It resolves column
references to their declaring table, prints types as comments, and must reproduce the input
modulo whitespace. Not a real translator.

---

## 6. Work breakdown: waves

Model tiers (per `~/.claude/CLAUDE.md`): **strong** = this Fable session directly, or Opus
subagents; Fable subagents only with explicit approval per task. **mechanical** = Sonnet
(Haiku for pure data mirrors). Every delegated change is reviewed against `PORTING.md` by the
strong tier before merge. One commit per wave (or per sub-wave), message naming the upstream
commit and manifest entries.

| Wave | Content (manifest wave field) | C# lines | Java files | Tier | Gate |
|---|---|---|---|---|---|
| **T0** | Repo scaffold: parent POM, 4 modules, `utils.dotnet` package with unit tests, `@Internal`, CI skeleton. Oracle project: generator runner, build, `dotnet-facts`. Corpus extraction scripts + pinned sparse checkouts + schema captures. | — | ~25 | strong (oracle, dotnet compat), mechanical (POMs, scripts) | oracle builds and dumps the readme corpus; `dotnet-facts` tests green |
| **W0** | `kusto-language-generator` (port of 3 generator files, data mirror), generated 229 node/visitor files, `Utils/`, `Diagnostics/`, hand-written `Syntax/`, 9 Editor types + `EditString` stub | 15,451 | 297 | strong: generator emitter, `SyntaxElement`/`SyntaxToken`; mechanical: `SyntaxNodeInfos` data, Utils, Diagnostics, Editor enums | compiles; drift test green; generated structure equals `porting/reference/GeneratedSyntaxNodes.cs` (class order, child names, optional sets, hints) |
| **W1** | `Symbols/` (48), `Options`, `Properties`, `ServerKinds`, `KustoDialect` | 7,910 | 71 | mechanical with strong review; `Signature`, `TableSymbol`, `ScalarTypes` strong | compiles; `PORT-PENDING` only at Binder/Parser/KustoCode edges (listed) |
| **W2** | Lexer: `TextFacts`, `TokenParser`, `LexicalToken`, `ParseOptions`, `KustoFacts` (+Keywords), `SyntaxFacts` | 3,818 | 5 (+1) | strong | **token conformance 100%** on all corpora; traps tests green |
| **W3a** | `Parser/Combinators/` (37), `SyntaxParsers`, `LexicalTokenParsers` facade | 8,900 | 51 | strong (Opus): generics, pools, safe parsers | compiles; combinator unit tests ported from behaviour (`Rule`, `First`, `Best`, `Forward` depth fallback) |
| **W3b** | Catalogs: `Functions`(+Convert), `Aggregates`, `Operators`, `PlugIns`, `FunctionHelpers`, `FunctionBodyFacts`, `QueryOperatorParameters` | 7,400 | 8 | mechanical (Haiku/Sonnet) + strong review of helpers | catalog count/name/signature equality against an oracle dump of `GlobalState.Default` |
| **W4** | `QueryParser`, `QueryGrammar` (split), `CommandGrammar`, `PredefinedRuleParsers`, `KustoCode`, `GlobalState`, `KustoCache`, 8 command stubs | 14,267 | 20 | strong | **tree + diagnostics conformance** on all corpora; round-trip and never-throws invariants; `ParserKind.Grammar` ≡ default on the corpus; **STOP AND REPORT** |
| **T1** | Upstream tracking: `porting/tools/upstream_diff.py`, bump procedure dry-run against `1e079791..9d95a2d5` (the 10 commits since NuGet 12.4.1) | — | — | strong | dry-run output reviewed; every changed file classified correctly |
| **W6** | `Binder/` (24 files → 13 Java), binder edges pending from W1 filled | 17,083 | 13 | strong | **bind conformance**; zero `PORT-PENDING` in scope; `GetSymbolsInScope`/`GetColumnsInScope` spot checks |
| **W7** | Optional files, example translator, JMH, final manifest statuses, deviation register audit | 682 | 8 | mechanical + strong review | definition of done |

Wave numbering matches `manifest.wave` (5 is unused; W3a/W3b share wave 3).

Review gates, applied to every delegated commit before merge: header present; member order
equals upstream (checked by a script that lists member names from both files); every `// PORT:`
has a register row; no `java.util.stream` in `kusto-language`; no `HashMap`/`HashSet`;
no `String.CASE_INSENSITIVE_ORDER`/`equalsIgnoreCase`/`trim()`/`Double.parseDouble` outside
`utils.dotnet` (enforced by a conformance test that greps the sources).

---

## 7. Upstream tracking tooling (built in T1, alongside the syntax layer)

`porting/tools/upstream_diff.py <old> <new>`:
1. `git diff --name-status old new` in the submodule.
2. Map each path through `manifest.json`; classify: `pending/ported → patch`, `generated →
   regenerate`, `stubbed → re-check stub`, `excluded → re-check exclusion`, `new → triage`,
   `deleted → remove`.
3. For catalog files, split the diff into data lines (`new FunctionSymbol(…)` blocks) and logic
   lines; data diffs map line-for-line to Java.
4. Strip the UTF-8 BOM before diffing; report `#if BRIDGE` regions touched.
5. Output Markdown + JSON; exit non-zero if triage is needed.

Dry run target: `1e079791..9d95a2d5` (real, recent, includes a `PlugIns.cs` catalog addition,
a `Functions.cs` result-name change, `KustoFacts.cs` names, `Interlocked.cs` growth and an
excluded `EngineCommandInfos.cs` change).

---

## 8. Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | Oracle generation path (compiling generator sources as a library) fails on some T4-specific construct | fallback `dotnet-t4`; last resort: the historical generated file from git plus hand-applied `EngineCommandInfos` diffs |
| R2 | Giant SCC: waves cannot be verified by compile alone | `PORT-PENDING` discipline, gate scripts, conformance per layer |
| R3 | `QueryGrammar.Initialize` split changes evaluation order of `Forward` parsers | split only at region boundaries; `ParserKind.Grammar ≡ Default` conformance test |
| R4 | Culture/formatting drift between oracle host and Java | invariant policy pinned in the oracle project; `dotnet-facts` tests; golden header records the runtime |
| R5 | Stack overflow on default 1 MB threads in Graylog | documented 4 MB requirement; depth tests; a non-mirrored facade may add a dedicated thread later |
| R6 | `Binder.TryBind` serialises all analysis on one `GlobalState` | documented; JMH contention case; integration may keep one `GlobalState` per worker |
| R7 | `docs` corpus binding mostly "unknown table" without the Samples schema | capture `help/Samples` schema once; mark docs records as parse-only when schema is absent |
| R8 | Upstream cadence (10 auto-syncs in 10 weeks, catalog-heavy) | upstream-diff classifies data vs logic; catalogs mirror line-for-line |
| R9 | Unicode table version mismatch (Java 21 = 15.0) | listed deviation D21; traps corpus includes characters assigned after 15.0 and expects known differences |
| R10 | Hidden behavioural dependence on `EditString` internals in `ClientDirective` | stub surface verified against all 12 call sites by the skeptic pass; test 3 in `dependency-cut.md` |

---

## 9. Decisions needing your confirmation

1. **Command text**: mirror the `UnknownCommand` path with its false diagnostics (chosen), or
   add a non-mirrored open-table scope to suppress them.
2. **Oracle runtime**: .NET 10 LTS from source at the pin (chosen) rather than NuGet 12.4.1.
3. **Property accessors**: `foo()` (chosen) rather than `getFoo()`.
4. **Arity naming**: numeric suffix (`SyntaxList1<T>`) rather than a per-family hand pick.
5. **Generated Java checked in** with a drift test (chosen) rather than generated at build time.
6. **Model tiers**: Opus is the "strongest" subagent tier by default; Fable subagents only when
   you approve per task.
7. **Corpus pins**: I will pin `MicrosoftDocs/dataexplorer-docs` and `Azure/Azure-Sentinel` to
   their main-branch heads at T0 unless you name commits.

## 10. Definition of done (restated against this plan)

- W4 gate: tokens, trees and diagnostics conform on all corpora; round-trip and never-throws
  invariants hold; report delivered before W6 starts.
- W6 gate: bind conformance reported per corpus with failure groups.
- Manifest: no `pending` entries in scope; every `// PORT:` has a register row.
- `upstream_diff.py` dry run over `1e079791..9d95a2d5` reviewed.
- JMH numbers and the example translator test in the repo.
