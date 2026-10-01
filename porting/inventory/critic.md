# Completeness critique: Phase-0 inventory

Upstream pin: `9d95a2d5` (2026-09-29). Paths are relative to `upstream/kusto-query-language/src/` unless prefixed.
Inputs read: all 11 area JSONs, `dependency-cut.json/.md`, `generators.md`, `java21-blockers.md`, `traps.md`, `fidelity-tests-api.md`, `blobs.txt`.
Every claim below was checked by grep, script or git. Network checks hit nuget.org and api.github.com on 2026-10-02.

## TL;DR

- **Coverage is nearly complete.** 280 of 289 `Kusto.Language/` files appear in an area report. The 9 missing ones are build and docs files. Only `Kusto.Language.sln` is on neither the area lists nor the cut.
- **The biggest gap is the oracle.** NuGet 12.4.1 is **not** our pin. It was published 2026-07-21. Ten source commits have landed since. `gql_request` and one `Functions` result-name change are missing from it.
- **Waves cannot be strict dependency layers.** 142 of 174 in-scope units form one strongly connected component, about 67.8k of 69.8k lines. Nobody has said this yet.
- **No report covers licensing.** Upstream is Apache-2.0, has no NOTICE file and has no per-file headers.
- **Three reports contradict one another on core rules.** The rules in question are the generic-arity naming, package-by-namespace versus package-by-folder, and the syntax-node count (223/225/226). Java21-blockers and traps also scanned a different scope from the cut.
- **"One Java file per C# file" fails for 37 in-scope files.** Together they declare about 79 extra top-level types. No report gives a rule for them.
- **Two Java semantic traps are missing.** Null in string interpolation or `Append` prints `""` in .NET and `"null"` in Java. A Java `switch` on a null string throws, where C# falls through to `default`.

---

## 1. Facts still missing or unverified

Ordered by the plan elements the plan must cover.

### 1.1 Upstream inventory

| Gap | Evidence | What the plan author needs |
|---|---|---|
| No usable machine dependency graph. The `dependsOn` formats differ by report. | Entries that are file paths: parser-core 0/93, parser-combinators 0/82, syntax 3/80, utils-diagnostics 13/37, root 19/54. Symbols, binder and editor-kusto are about 100%. | Regenerate one graph with a single script, at file level, using the types each file declares. Don't hand-merge the reports. |
| Nobody did an SCC or layering analysis. | See section 4.1. | Wave plan input. |
| Repo-root artefacts are not in any area report: `LICENSE`, `README.md`, `grammar/`, `.github/`. | `blobs.txt` lists them (318 blobs). No report references `blobs.txt`. | Inputs for the attribution plan and the upstream-diff tool. |
| Source encoding facts. | 272 of 280 `.cs` files start with a UTF-8 BOM. 0 contain CR. The only non-ASCII content past the BOM is in `Kusto.Language/Editor/Kusto/KustoCompleter.cs`. | Diff tool and mirror-comment tooling must strip the BOM. Java output must be BOM-free. |
| `readme.md` is excluded as "Build/packaging/docs, no code" (dependency-cut.json). Yet fidelity-tests-api.md:109 calls it the only runnable-looking example source. | | Classify it as a **test-corpus source**, not just excluded. |

### 1.2 License and attribution: nothing exists

- `LICENSE` is Apache-2.0, "Copyright 2019 Microsoft Corporation" (`LICENSE:190`). No upstream `NOTICE` file exists, so Apache §4(d) adds nothing.
- 0 of 280 `.cs` files carry a copyright header. So each Java file needs its own derived-work notice under Apache §4(b): the upstream path, the commit, and "modified".
- The generator emits `// Copyright (c) Microsoft Corporation. All rights reserved.` into every generated file (`Kusto.Language.Generators/CodeGenerator.cs:50`). A mirrored Java emitter would stamp that line on about 229 Java files. Decide whether to keep it, replace it or add to it.
- Corpus licenses (checked on GitHub):
  - `MicrosoftDocs/dataexplorer-docs`: `LICENSE` is CC-BY-4.0 and `LICENSE-CODE` is MIT. Whether KQL snippets in the docs count as "code" is not settled. Default to CC-BY-4.0 attribution and keep the corpus out of the shipped jar.
  - `Azure/Azure-Sentinel`: MIT. The repo is about 11 GB (`size` 11,580,300 KB), so use a sparse checkout.
  - `grammar/.antlr/*.java` is upstream content too, even though nothing builds it.
- The package name `org.graylog.kusto.language` uses the "Kusto" product name. Get a trademark sign-off, if that is in scope.

### 1.3 Translation rules (PORTING.md inputs that are undecided)

- **Accessor naming for C# properties** (`getX()` versus `x()`). generators.md:208 says it is "decided elsewhere". It is decided nowhere.
- **Package rule.** It can follow the namespace or the folder. Four files differ between the two (see 2.1).
- **Multi-type files.** 37 in-scope files declare more than one top-level type, about 79 extra types (script below). The worst cases:
  - `Syntax/SyntaxNode.cs`: 17 types.
  - `Symbols/ParameterLayouts.cs`: 6.
  - `Symbols/DynamicSymbol.cs`: 5.
  - `Symbols/TableSymbol.cs`: 5.
  - `Diagnostics/Diagnostic.cs`: 4.
  - `KustoCode.cs`: 3. `IncludeFunctionKind` and `ParserKind` are public.

  Java permits one public top-level type per file. The rule must say one of three things: split the file, nest the type (which changes the type's qualified name), or keep the extra type package-private. Only the visitor and generic-arity cases are discussed anywhere.
- **Null semantics.** Two traps are missing (section 4.3).
- A test-hook convention to replace `InternalsVisibleTo` (fidelity C7). Package-private only works if tests share the package.

### 1.4 Manifest

- `dependency-cut.json` has include, exclude and stub lists. It has no per-file **status** field (pending, ported or reviewed), no blob hash and no wave id.
- `blobs.txt` has the hashes but is unreferenced and unexplained. Merge the two into the manifest.
- Its `javaPath` values are wrong in 5 places (section 2.1). A tool would create files that fail to compile.

### 1.5 Oracle (.NET) tool: core facts were missing, now verified

- `dotnet` is not installed. `java` and `mvn` are (SDKMAN).
- **NuGet `Microsoft.Azure.Kusto.Language` 12.4.1 exists.** It was published 2026-07-21T07:16Z and targets net472, netcoreapp2.1, net6.0 and netstandard2.0.
- **It does not match the pin.** `version.txt` became `12.4.1` in `1e079791` (2026-07-19). `git log 1e079791..9d95a2d5` shows 10 commits. In scope they change:
  - `PlugIns.cs`: new hidden `gql_request` plugin, +11 lines.
  - `Functions.cs:3573`: `ResultNameKind.None` became `PrefixOnly` plus `$RowId`.
  - `Parser/KustoFacts.cs:404`: two new names.
  - `Utils/Interlocked.cs`: +56 lines.
  - `EngineCommandInfos.cs` changed too.
- **Decision for the plan.** Choose one:
  - (a) Re-pin the submodule to `1e079791` so NuGet 12.4.1 is an exact oracle.
  - (b) Build the oracle from source at `9d95a2d5`. This needs the T4 output, which generators.md §1.4 proved reproducible.
  - (c) Accept the drift and maintain a known-diff list.
- Under any option, the plan must say how the tool proves which source the oracle binary came from.
- **Runtime choice drives goldens** (traps §0: double formatting, `(long)double` and ICU). Name the runtime and set `InvariantGlobalization` in the oracle project.
- **Golden symbol printer.** `Symbols/SchemaDisplay.cs` (`public static GetText(Symbol)`, :14) is public and ships in NuGet. Use it as the canonical type text on both sides. `DebugDisplay` is internal and debugger-only (`Symbol.cs:9-16`). Symbols have no `ToString` override.
- The oracle must call `KustoCode`, not `KustoCodeService` (skeptic finding, see section 5).

### 1.6 Conformance goldens

- No golden serialization format is defined. It needs at least five parts:
  - tokens: kind, trivia, text;
  - the tree as a kind and child-name preorder;
  - diagnostics: code, severity, start, length, message;
  - `ReferencedSymbol` and `ResultType` per node, through `SchemaDisplay`;
  - an "exception thrown" outcome.
- The record must be identical from C# and Java. Write the spec once and implement it twice.

### 1.7 Corpora

- **"Upstream tests" cannot be a corpus.** There are none, and there never were (fidelity B2: 1,262 commits, no test files). The plan must replace that line item.
- Docs examples query the `help` cluster `Samples` database (StormEvents and others). No report says where its schema comes from. Without it, binding goldens are mostly "unknown table" errors.
- Azure-Sentinel has its own KQL validation harness. It lives at `.script/tests/KqlvalidationsTests/`, with `CustomTables/`, `CustomFunctions/` and `FunctionSchemasLoaders/`. It uses NuGet `Microsoft.Azure.Sentinel.KustoServices` 7.1.0 (vendored `.nupkg`). That gives us table schemas and a ready reference for building `GlobalState` per query. Check its license (MIT, repo-wide) and pin a commit.
- Mutation fuzz needs three things: a seed, a reducer, and a rule for when Java and .NET both throw.

### 1.8 Invariants

- The round-trip tests are specified (fidelity A2/A5).
- The never-throws test has no policy. Upstream itself throws in some places:
  - `Enumerable.Max` on empty input (`Parser/Combinators/PartialParser.cs:309,365,1068`).
  - `char.ConvertFromUtf32` (`Parser/KustoFacts.cs:940`).
  - `TimeSpan.FromX` overflow (`Syntax/SyntaxToken.cs:675-712`).

  These are lazy, but the binder reads `LiteralValue` and `.Value` (for example `Binder/Binder_NodeBinder.cs:183,205,739`). Whether `ParseAndAnalyze` can throw upstream is **unverified**, because nothing has been run.
- The invariant should read "Java throws iff the oracle throws", or "the API boundary never throws". It cannot be both. `StackOverflowError` must be in the policy too (section 4.4).

### 1.9 JMH, example translator, upstream-diff tool

- **JMH.** No report defines the queries, a .NET baseline (BenchmarkDotNet on the oracle) or targets. java21-blockers names hot paths: `SubstringMap`, `StringTable`, `ObjectPool`. It names no workload.
- **Example translator.** Nothing beyond the readme recipe (fidelity C6). The plan needs four decisions: the target (Graylog search? OpenSearch DSL?), the supported KQL subset, the API it consumes (`ReferencedSymbol`, `ResultType`, `GetCalledFunctionBody`, `WalkNodes` with `fnDescend`), and how it handles unbound or open schemas.
- **Upstream-diff tool.** No design exists. Facts it must handle:
  - No tags exist. Commits are mostly "Auto-sync from Azure-Kusto-Service" (10 between `1e079791` and the pin).
  - Generated outputs are not committed (since `cd24dcf3`). So drift in node shape shows up only as changes to `SyntaxNodeInfos.cs`.
  - The BOM.
  - The manifest's blob hashes.
  - The `#if !BRIDGE` regions (generators-and-bridge.json `bridgeRegions`).

---

## 2. Contradictions between reports

### 2.1 Machine-readable cut versus area reports (javaPath)

| Upstream file | Cut javaPath | Area report javaPath | Ground truth |
|---|---|---|---|
| `Parser/ParseOptions.cs` | `parsing/ParseOptions.java` | root (parser-core) | `namespace Kusto.Language` (:3). The package rule decides it. |
| `Parser/KustoFacts.cs` + `KustoFacts_Keywords.cs` | `parsing/KustoFacts.java` | root (parser-core) | `namespace Kusto.Language` (:4) |
| `Binder/FunctionCallExpansion.cs` | `binding/…` | root (binder) | `namespace Kusto.Language` (:3) |
| `Utils/Cancellation.cs` | `utils/Cancellation.java` | `utils/CancellationToken.java` | It declares only `public struct CancellationToken` (:7). The cut is wrong for a public type. |
| `Symbols/CustomAvailabilty.cs` (filename typo) | `CustomAvailabilty.java` | `CustomAvailability.java` | It declares `public delegate bool CustomAvailability` (:6). The cut is wrong. |
| `Syntax/SyntaxNode_Semantics.cs` | `syntax/SyntaxNode_Semantics.java` | `syntax/SyntaxNode.java` | Partials only. Already raised by the skeptic. |
| `Editor/CompletionPriority.cs` | one file | `+ CompletionRank.java` | Two public enums (:8, :27). The area report is right. |
| `Parser/Combinators/ParserVisitors.cs`, `SafeParse.cs`, `SafeScan.cs` | `ParserVisitors.java`, `SafeParse.java`, `SafeScan.java` | (same) | No such type names exist. They declare `ParserVisitor`×3 + `IsParentVisitor`, `SafeParser` + `StackSafeParser`, and `SafeScanner` + `StackSafeScanner`. Both are wrong. |

### 2.2 Status disagreements the skeptic did not list

These come on top of the skeptic's list. My script found 34 area-vs-cut status mismatches in total. Most are only the "partial/generated" vocabulary.

- `TestHelpers.cs`: root.json `ported` to `src/main/java`, but the cut says `exclude`. The cut is right: it is internal and test-only (fidelity B1).
- `Symbols/DebugDisplay.cs`: symbols.json `stubbed`, but the cut says `include`. It is debugger-only (`Symbol.cs:9-16`), so stubbing it or dropping it is fine.
- `Binder/Binder_ContextBuilder.cs`, `GlobalState.cs`, `KustoCode.cs`, `CommandSymbol.cs`, `Utils/Interlocked.cs`, `Parser/QueryGrammar.cs`, `SyntaxParsers.cs`, `KustoFacts.cs`, and 3 combinator files are `partial` in the area reports but plain `include` in the cut. The cut does not record which members are dropped, for example `Interlocked.CacheLineSeparated` (dead, traps §5).

### 2.3 Syntax node count: three different numbers

- syntax.json `notes.generatedFiles`: "~223 node classes".
- dependency-cut.md §0, fidelity A5/C2, java21-blockers §3G and dependency-cut.json: **226**, and "210 Visit methods".
- generators.md: **225**, 16 abstract, 209 visits.
- **Verified: 225.** `grep -c 'new SyntaxNodeInfo'` gives 226, but one hit is the array type at `SyntaxNodeInfos.cs:16`. The historical `GeneratedSyntaxNodes.cs` at `cd24dcf3^` has 229 partial classes, which is 225 + 4 visitors. That gives 225 − 16 = 209 visits.

### 2.4 Generic-arity naming: three incompatible proposals

- java21-blockers §3C: a numeric suffix on the higher arity.
- generators.md §3.4: `SyntaxVisitorT` or `…ResultVisitor`.
- syntax.json `notes.filesNotInArea`: rename the non-generic base and keep one generic class.

Pick one rule for all 11 families.

### 2.5 Command boundary: two designs

- dependency-cut.md §2 keeps `CreateCommandGrammar` and `GetCommands` verbatim, with 8 empty stub classes.
- generators.md §4: "always `return new CommandGrammar(globals)`", "every arm returns the empty list", marked with `PORT-NOTE`.

They also disagree on the marker: `PORT-DEVIATION` versus `PORT-NOTE`. And generators.md:249 says no crash, which the skeptic already flagged.

### 2.6 Scope mismatch: java21-blockers and traps scanned a different file set

- Both scan "excluding `Editor/`, 181 files, 68,474 lines". The cut's runtime set includes 9 Editor files (1,236 lines) and excludes `TestHelpers.cs` and `AssemblyInfo.cs`.
- java21-blockers calls `PartialParser.cs` (:120) and `CommandGrammar` (:43, :228) "out of scope". The cut includes both. So these items are in scope but marked out:
  - the 2 mutable-local lambda captures (`CommandGrammar.cs:32-33`);
  - the ValueTuple sites (`PartialParser.cs:595,609,688`);
  - the implicit conversion (`PartialParser.cs:959`);
  - the 29 LINQ calls;
  - the culture `OrderBy` (`CommandGrammar.cs:174`).
- Traps never scanned the included Editor files. Missed sites:
  - `Editor/ClientDirective.cs:178` `double.TryParse` and `:189` `long.TryParse`, which are culture- and ASCII-sensitive (traps §2a lists only 12 sites).
  - `Editor/CompletionHint.cs:11` `[Flags]`, not among traps' 9 Flags enums. This enum is on the binder path (Binder_Names.cs:851).
  - `Editor/CompletionText.cs:63,76,79`: culture `IndexOf(string)`.
  - `Editor/EditString.cs:293,811`: culture `string.Compare`.

### 2.7 Smaller ones

- traps.md §1c presents `IsWhitespace` and `IsLineBreakStart` "verbatim". They are not verbatim. The source uses `' '`-style escapes (`Parser/TextFacts.cs:12-45`), while traps.md:70-72 holds raw invisible characters and an `...` elision. Do not copy from traps.md.
- symbols.json lists `string.Format` among DebugDisplay's features. traps §2c says "no `string.Format` anywhere". A grep finds none in scope.
- generators-and-bridge.json puts the generator under `kusto-language/src/main/java/.../generator/`. generators.md §3.2 puts it in a separate module. generators.md notes this but does not resolve it.
- dependency-cut.json reports an "~226" count in the `.tt` reason (see 2.3).

---

## 3. Files in no area report (script-verified)

Method: walk `src/` (bin/obj skipped), normalise each JSON `upstreamPath`, diff. Script in the appendix.

- `src/` has 307 files. `src/Kusto.Language/` has 289: 270 `.cs`, 9 `.tt`, 1 `.t4`, 2 `.props`, 1 `.targets`, 1 `.csproj`, 2 `.cmd`, 2 `.md`, 1 `.txt`.
- **`Kusto.Language/` files in no area report: 9.** All are build or docs files. All are on the cut's exclude list.

| File | Bytes |
|---|---|
| `Kusto.Language/Directory.Build.props` | 458 |
| `Kusto.Language/Directory.Build.targets` | 718 |
| `Kusto.Language/Kusto.Language.csproj` | 1,329 |
| `Kusto.Language/Package.props` | 1,900 |
| `Kusto.Language/bridge.net.help.md` | 2,007 |
| `Kusto.Language/build-multiTarget.cmd` | 431 |
| `Kusto.Language/pack-multiTarget.cmd` | 348 |
| `Kusto.Language/readme.md` | 17,066 |
| `Kusto.Language/version.txt` | 6 |

- Outside `Kusto.Language/`: only `Kusto.Language.sln` is in no area report, and it is also missing from the cut. That confirms the skeptic.
- No area path points to a file that does not exist.
- 10 files appear in two area reports, with consistent status:
  - `GeneratedSyntaxNodes.tt` (syntax + generators-and-bridge);
  - the 8 command `.tt` files and `CommandGenerator.t4` (parser-core + generators-and-bridge).
- Repo-root files outside `src/` are in no report: `LICENSE`, `README.md`, `grammar/**`, `.github/**`. `blobs.txt` lists them. They are needed for 1.2 and 1.9.

---

## 4. Risks nobody mentioned

### 4.1 Dependency waves are one giant cycle

- I built a type-reference graph over the cut's runtime `.cs` files, with partial groups merged and generated node names attached to `SyntaxNode`. **142 of 174 units sit in one SCC, about 67,824 lines.** The SCC spans Parser 44, Symbols 42, Binder, Syntax, Utils, Editor and every root catalog.
- The 32 units outside it are leaves: Utils helpers, small enums, `TextFacts`, `ScopeKind` and the optional Parser files.
- This is a heuristic: word-matching on type names over-approximates. These real cycle edges were confirmed:
  - Symbols→Binder: `Symbols/ColumnSymbol.cs:200-208` calls `Binding.Binder.Unify…`.
  - Symbols→Parser: `Symbols/Parameter.cs:257` and `Symbols/TableSymbol.cs:101`.
  - Symbols→KustoCode: `Symbols/Signature.cs:687`.
  - Symbols→GlobalState: `Symbols/Signature.cs:335`.
  - Syntax→Binder: `Syntax/SyntaxNode_Semantics.cs:7,19`.
- **Consequence:** "wave N compiles and is reviewed before wave N+1" is impossible past the leaves. The plan needs one of two approaches:
  - (a) A skeleton-first wave. Generate the declarations for all types (signatures that throw `UnsupportedOperationException`), then fill in bodies by wave.
  - (b) Review gates that do not require a green compile.
- The syntax-node generator must land in wave 0 or 1. Nothing in Syntax, Parser or Binder compiles without it (java21-blockers §3G).

### 4.2 The golden reference for generated code lives only in git history

- generators.md §1.4 relies on `git show cd24dcf3^:…/GeneratedSyntaxNodes.cs`. A shallow submodule clone (`--depth 1`, common in CI) lacks that object.
- Extract the file (and the `cd24dcf3^` command outputs, if ever needed) into `porting/reference/` with its blob hash, or fetch the history explicitly in CI.

### 4.3 Null semantics in strings and switches

- **Interpolation and concatenation.** C# `$"{x}"`, `string + null` and `StringBuilder.Append((string)null)` yield `""`. Java yields `"null"`.
  - In scope: 148 interpolations (87 in `Diagnostics/DiagnosticFacts.cs`, e.g. `$"Missing: {text}"` at :17) and 117 `.Append(` calls.
  - This changes diagnostic messages and result column names whenever a value is null.
  - Neither traps.md nor java21-blockers mentions it.
- **`switch` on a null string.** C# goes to `default`. Java throws `NullPointerException`. There are 220 `switch (` in scope, and string switches occur, e.g. `Syntax/SyntaxToken.cs:648`. `case null:` appears at `Symbols/SchemaDisplay.cs:18` and `DebugDisplay.cs:21` and must become an explicit null check.
- **`==` on strings** becomes `equals` (traps §4), but `a == b` with a null `a` must become `Objects.equals`. Only two area JSONs mention it, and PORTING.md must state it.

### 4.4 Stack depth meets host threads

- java21-blockers §5 and traps §11b recommend 4-64 MB stacks. Graylog will call from request threads with the default 1 MB.
- The public API must decide: hop to a dedicated thread pool inside `KustoCode.parse`, or document `-Xss`. That changes the mirrored `KustoCode` surface or adds a facade.
- A `StackOverflowError` in a request thread breaks the never-throws invariant. .NET cannot catch stack overflow; Java can, and must decide whether to.

### 4.5 Oracle runtime versus corpus semantics

- Docs and Sentinel queries use real time literals, `datetime()`, decimals and doubles in column names (`percentile_x_50`, traps §2c). Corpus goldens will exercise the culture and formatting traps hard.
- If the oracle runs under a different culture or runtime than the policy, every golden is wrong at once. Pin it in the oracle's `runtimeconfig` and record it in each golden file header.

### 4.6 Upstream cadence

- 10 auto-sync commits in about 10 weeks. Catalog files (`Functions.cs`, `PlugIns.cs`, `KustoFacts.cs`) change most.
- The diff tool should classify catalog-data diffs separately from logic diffs. Catalog data maps line for line and could be translated mechanically (java21-blockers §3A notes about 8.1k lines of declarative data).

### 4.7 Binding concurrency for the integration

- `Binder.TryBind` locks the per-`GlobalState` `GlobalBindingCache` (`Binder/Binder_API.cs:168-170`). One shared `GlobalState` in Graylog serialises all analysis.
- The fidelity report states the fact but no one draws the consequence. Either the integration keeps one `GlobalState` per worker or accepts the serialisation. The JMH suite should measure it.

---

## 5. Skeptic findings: status

All are still open in the reports. I re-checked these:

- `generators.md:249` "not a crash": confirmed. It contradicts `dependency-cut.md` §2.
- `Kusto.Language.sln` unclassified: confirmed by script (only file outside the cut).
- `SyntaxNode_Semantics.java` javaPath: confirmed (2.1).
- Area statuses overriding the cut: confirmed. 2.2 adds `TestHelpers`, `DebugDisplay` and the `partial` group.

The others are UnknownCommand false errors, embedded queries being dropped, the KustoCodeService contract, the SyntaxVisitor merge note and the EditString call sites. I did not re-derive them. Nothing in the reports contradicts them.

---

## Appendix: coverage script

```python
import json,os,glob,collections
SRC='/home/kroepke/projects/kusto-java/upstream/kusto-query-language/src'
inv='/home/kroepke/projects/kusto-java/porting/inventory'
norm=lambda p: p[4:] if p.startswith('src/') else p
area=collections.defaultdict(list)
for f in glob.glob(inv+'/*.json'):
    if 'dependency-cut' in f: continue
    for e in json.load(open(f)).get('files',[]):
        area[norm(e['upstreamPath'])].append((os.path.basename(f),e.get('proposedStatus')))
tree=sorted(os.path.relpath(os.path.join(r,fn),SRC)
            for r,ds,fs in os.walk(SRC) for fn in fs)
kl=[t for t in tree if t.startswith('Kusto.Language/')]
print('missing from area reports:',[t for t in kl if t not in area])
cut={}
d=json.load(open(inv+'/dependency-cut.json'))
for k in ['include','exclude','stubs']:
    for e in d[k]: cut.setdefault(norm(e['upstreamPath']),[]).append(k)
covered=lambda t: t in cut or any(t.startswith(k.rstrip('/')+'/') for k in cut)
print('missing from cut:',[t for t in tree if not covered(t)])
```
